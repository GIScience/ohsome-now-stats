package org.heigit.ohsome.now.parquet;

import static com.google.common.collect.Streams.stream;
import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static org.heigit.ohsome.now.parquet.AvroUtil.parseSchema;
import static org.heigit.ohsome.now.parquet.Countries.indexCountries;
import static org.heigit.ohsome.oshdb.util.geometry.Geo.areaOf;
import static org.heigit.ohsome.oshdb.util.geometry.Geo.lengthOf;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Flux.fromStream;
import static reactor.core.publisher.Mono.fromCallable;
import static reactor.core.scheduler.Schedulers.boundedElastic;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import javax.cache.Cache.Entry;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.apache.avro.Schema;
import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.parquet.hadoop.ParquetWriter;
import org.heigit.ohsome.now.parquet.avro.AvroChangeset;
import org.heigit.ohsome.now.parquet.avro.AvroContribId;
import org.heigit.ohsome.now.parquet.avro.AvroContribType;
import org.heigit.ohsome.now.parquet.avro.AvroHOTStats;
import org.heigit.ohsome.now.parquet.contrib.Contribution;
import org.heigit.ohsome.now.parquet.contrib.Contributions;
import org.heigit.ohsome.now.parquet.util.SysoutProgressConsumer;
import org.heigit.ohsome.oshdb.OSHDBTag;
import org.heigit.ohsome.oshdb.grid.GridOSHEntity;
import org.heigit.ohsome.oshdb.osm.OSMNode;
import org.heigit.ohsome.oshdb.osm.OSMType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.index.strtree.STRtree;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class HOTStatsJob implements IgniteCallable<Long> {

  private static final Path OHSOME_NOW_STATS = Path.of("/data/ignite/ohsome-now-stats");
  private static final int KEY_BUILDING = 0;
  private static final int KEY_HIGHWAY = 2;
  private static final OSHDBTag TAG_TREE = new OSHDBTag(11, 0);


  @IgniteInstanceResource
  private final transient Ignite ignite;

  private final GeometryBuilder geometryBuilder = new GeometryBuilder();

  private final OSMType type;
  private Schema schema;
  private STRtree countriesIndex;
  private Object localId;
  private ChangesetDb changesetDb;


  public HOTStatsJob(Ignite ignite, OSMType type) {
    this.ignite = ignite;
    this.type = type;
  }

  @Override
  public Long call() throws Exception {
    var cluster = ignite.cluster();
    var localNode = cluster.localNode();
    var hostName = localNode.hostNames().stream().filter(hn -> hn.startsWith("heigit"))
        .findAny().orElse("n/a");

    localId = ignite.cluster().localNode().consistentId();
    schema = parseSchema(Files.readString(OHSOME_NOW_STATS.resolve("hotstats.avsc")));
    countriesIndex = indexCountries(OHSOME_NOW_STATS.resolve("countries_level2.csv"));

    var cache = ignite.<Long, GridOSHEntity>cache("oshdb_grid_" + type);
    var size = cache.localSize();
    try (var changesetDb = ChangesetDb.openChangesetDb("129.206.5.164", "changesets",
        "HOTStats " + hostName);
        var progress = progressBar(size)) {
      this.changesetDb = changesetDb;
      return fromIterable(cache.localEntries())
          .map(Entry::getValue)
          .flatMap(grid -> Mono.just(grid)
              .flatMapMany(this::process)
              .doOnComplete(progress::step)
              .subscribeOn(boundedElastic()), 4)
          .groupBy(Map.Entry::getKey)
          .flatMap(group -> Flux.using(openWriter(group.key()),
              writer -> group.map(Map.Entry::getValue)
                  .concatMap(stats -> fromCallable(() -> write(writer, stats))),
              this::closeAndSwallowIOExceptions))
          .count()
          .block();
    }
  }

  private void closeAndSwallowIOExceptions(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Callable<ParquetWriter<AvroHOTStats>> openWriter(PartitionKey partition) {
    return () -> AvroUtil.openWriter(schema, OHSOME_NOW_STATS.resolve("parquet"), partition);
  }

  private static boolean write(ParquetWriter<AvroHOTStats> writer, AvroHOTStats stats)
      throws IOException {
    writer.write(stats);
    return true;
  }

  private ProgressBar progressBar(int size) {
    return new ProgressBarBuilder().setTaskName("grids "+ type)
        .setUpdateIntervalMillis(10_000)
        .setInitialMax(size)
        .setConsumer(new SysoutProgressConsumer(60))
        .build();
  }

  private Flux<Map.Entry<PartitionKey, AvroHOTStats>> process(GridOSHEntity grid) {
    var changesets = changesetDb.changesets(grid);
    //Map<Long, AvroChangeset> changesets = Map.of();
    return Flux.fromIterable(grid.getEntities())
        .map(Contributions::of)
        .flatMap(contribs -> fromStream(stream(contribs)
            .map(contrib -> contribToStats(contrib, changesets.get(contrib.getChangeset())))
        ))
        .map(this::partitionBy);
  }

  private Map.Entry<PartitionKey, AvroHOTStats> partitionBy(AvroHOTStats stats) {
    var timestamp = Instant.ofEpochMilli(stats.getChangesetTimestamp()).atZone(UTC);
    var year = timestamp.getYear();
    var partition = new HOTStatsPartition(type, year, localId);
    return Map.entry(partition, stats);
  }

  private AvroHOTStats contribToStats(Contribution contrib, AvroChangeset changeset) {
    var stats = new AvroHOTStats();
    stats.setContribId(contribId(contrib));
    stats.setOsmId(contrib.getId());
    stats.setChangesetId(contrib.getChangeset());
    stats.setChangesetTimestamp(changeset != null && changeset.getClosedAt() != null ?
        changeset.getClosedAt() : contrib.getTimestamp() * 1000L);
    stats.setHashtags(changeset != null ?
        changeset.getHashtags() : emptyList());
    stats.setUserId(contrib.getUser());

    var before = contrib.before().orElse(null);
    building(stats, contrib, before);
    road(stats, contrib, before);
    types(stats, contrib, before);

    var geometry = contrib.data("geometry", this::getGeometry);
    var countries = contrib.data("countries", c -> countries(geometry));
    stats.setCountryIsoA3(countries);
    return stats;
  }

  private AvroContribType contribType(Contribution contrib, Contribution before) {
    var entity = contrib.getEntity();
    if (!entity.isVisible()) {
      return AvroContribType.DELETED;
    }
    if (before == null || !before.getEntity().isVisible()) {
      return AvroContribType.CREATED;
    }

    var tagChange = !entity.getTags().equals(before.getEntity().getTags());
    var geomChange = geomChange(contrib, before);
    if (tagChange) {
      return geomChange ? AvroContribType.TAG_GEOMETRY : AvroContribType.TAG;
    }
    return geomChange ? AvroContribType.GEOMETRY : AvroContribType.NONE;
  }

  private boolean geomChange(Contribution contrib, Contribution before) {
    if (contrib.getType() == OSMType.NODE) {
      var node = (OSMNode) contrib.getEntity();
      var nodeBefore = (OSMNode) before.getEntity();
      return node.getLon() != nodeBefore.getLon() || node.getLat() != nodeBefore.getLat();
    }
    return true;
    //throw new UnsupportedOperationException("geomChange for non Nodes not supported yet");
  }

  private Geometry getGeometry(Contribution contrib) {
    return getGeometry(contrib, false);
  }

  private Geometry getGeometry(Contribution contrib, boolean area) {
    if (contrib == null) {
      return null;
    }
    var geom = geometryBuilder.getGeometry(contrib, area);
    if (!geom.isValid()) {
      geom = geom.buffer(0);
    }
    return geom;
  }

  private void building(AvroHOTStats stats, Contribution contrib, Contribution before) {
    if (isBuilding(contrib)) {
      var geometry = contrib.data("geometry", c -> getGeometry(c, true));
      var area = contrib.data("buildingArea", x -> areaOf(geometry));
      stats.setBuildingArea(area);
      if (isBuilding(before)) {
        var geomBefore = before.data("geometry", c -> getGeometry(c, true));
        var areaBefore = before.data("buildingArea", x -> areaOf(geomBefore));
        var delta = area - areaBefore;
        stats.setBuildingAreaDelta(delta);
      } else {
        stats.setBuildingAreaDelta(area);
      }
    } else if (isBuilding(before)) {
      var geomBefore = before.data("geometry", c -> getGeometry(c, true));
      var areaBefore = before.data("buildingArea", x -> areaOf(geomBefore));
      stats.setBuildingArea(0.0);
      stats.setBuildingAreaDelta(-areaBefore);
    }
  }

  private boolean isBuilding(Contribution contrib) {
    return contrib != null && contrib.getEntity().getTags().stream()
        .anyMatch(tag -> tag.getKey() == KEY_BUILDING);
  }

  private void road(AvroHOTStats stats, Contribution contrib, Contribution before) {
    if (isRoad(contrib)) {
      var geometry = contrib.data("geometry", c -> getGeometry(c, true));
      var length = contrib.data("roadLength", x -> lengthOf(geometry));
      stats.setRoadLength(length);
      if (isRoad(before)) {
        var geomBefore = before.data("geometry", c -> getGeometry(c, true));
        var lengthBefore = before.data("buildingArea", x -> lengthOf(geomBefore));
        var delta = length - lengthBefore;
        stats.setRoadLengthDelta(delta);
      } else {
        stats.setRoadLengthDelta(length);
      }
    } else if (isRoad(before)) {
      var geomBefore = before.data("geometry", c -> getGeometry(c, true));
      var lengthBefore = before.data("buildingArea", x -> lengthOf(geomBefore));
      stats.setRoadLength(0.0);
      stats.setRoadLengthDelta(-lengthBefore);
    }
  }

  private boolean isRoad(Contribution contrib) {
    return contrib != null && contrib.getEntity().getTags().stream()
        .anyMatch(tag -> tag.getKey() == KEY_HIGHWAY);
  }

  private void tree(AvroHOTStats stats, Contribution contrib, Contribution before) {
    stats.setTree(isTree(contrib) ? (isTree(before) ? 0 : 1) : (isTree(before)? -1 : null));
  }

  public boolean isTree(Contribution contrib) {
    return contrib != null && contrib.getEntity().getTags().hasTag(TAG_TREE);
  }

  private void types(AvroHOTStats stats, Contribution contrib, Contribution before) {
    stats.setContribType(contribType(contrib, before));
  }

  private List<CharSequence> countries(Geometry geometry) {
    if (geometry == null) {
      return emptyList();
    }
    var result = new ArrayList<CharSequence>();
    countriesIndex.query(geometry.getEnvelopeInternal(), obj -> {
      var candidate = (PreparedGeometry) obj;
      if (candidate.intersects(geometry)) {
        result.add((String) candidate.getGeometry().getUserData());
      }
    });
    return result;
  }

  private AvroContribId contribId(Contribution contrib) {
    var bb = ByteBuffer.allocate(25)
        .put((byte) contrib.getType().intValue())
        .putLong(contrib.getId())
        .putLong(contrib.getTimestamp())
        .putLong(contrib.getChangeset());
    return new AvroContribId(bb.array());
  }

  private double absDelta(Double value) {
    return value != null ? Math.abs(value) : 0.0;
  }

  private boolean geometryEquals(Geometry geometry, Geometry geometryBefore) {
    if (geometry == null) {
      return geometryBefore == null;
    }
    return geometry.equals(geometryBefore);
  }

  private static class HOTStatsPartition implements PartitionKey {
    private final OSMType type;
    private final int year;
    private final Object uuid;

    public HOTStatsPartition(OSMType type, int year, Object uuid) {
      this.type = type;
      this.year = year;
      this.uuid = uuid;
    }

    @Override
    public Path toPath(Path root) {
      return root.resolve(format("type=%s", type))
          .resolve(format("year=%04d", year))
          .resolve(format("contribs-%s.parquet", uuid));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof HOTStatsPartition)) {
        return false;
      }
      HOTStatsPartition that = (HOTStatsPartition) o;
      return year == that.year && type == that.type && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, year, uuid);
    }
  }
}
