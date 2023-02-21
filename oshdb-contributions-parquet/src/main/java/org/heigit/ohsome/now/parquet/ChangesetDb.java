package org.heigit.ohsome.now.parquet;

import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.heigit.ohsome.now.parquet.avro.AvroChangeset;
import org.heigit.ohsome.now.parquet.contrib.Contribution;
import org.heigit.ohsome.now.parquet.contrib.Contributions;
import org.heigit.ohsome.oshdb.grid.GridOSHEntity;
import org.heigit.ohsome.oshdb.util.exceptions.OSHDBException;
import org.heigit.ohsome.oshdb.util.tagtranslator.ClosableSqlArray;

import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;

public class ChangesetDb implements AutoCloseable {

    public static final Pattern HASHTAG_HOTOSM = compile("#HOTOSM", CASE_INSENSITIVE);
    public static final Pattern HASHTAG_PATTERN = compile("#[^\\u2000-\\u206F\\u2E00-\\u2E7F\\s\\\\'!\"#$%()*,./:;<=>?@\\[\\]^`{|}~]+");

    private final HikariDataSource dataSource;

    public static ChangesetDb openChangesetDb(String host, String database, String appName) {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + "/" + database);
        config.setUsername("readonly");
        config.setPassword("ohsome");
        config.addDataSourceProperty("ApplicationName", appName);
        return new ChangesetDb(new HikariDataSource(config));
    }

    public Map<Long, AvroChangeset> changesets(GridOSHEntity grid) {
        var changesetsIds = new TreeSet<Long>();
        Streams.stream(grid.getEntities())
            .map(Contributions::of)
            .flatMap(Streams::stream)
            .map(Contribution::getChangeset)
            .forEach(changesetsIds::add);
        return changesets(changesetsIds);
    }

    private ChangesetDb(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    public Map<Long, AvroChangeset> changesets(Set<Long> ids) {
        try (var conn = dataSource.getConnection();
             var pstmt = conn.prepareStatement("select id, created_at, closed_at, tags from osm_changeset where id = any(?)");
             var array = ClosableSqlArray.createArray(conn, "int", ids)) {
            pstmt.setArray(1, array.get());
            var map = Maps.<Long, AvroChangeset>newHashMapWithExpectedSize(ids.size());
            try (var rst = pstmt.executeQuery()) {
                var changesetBuilder = AvroChangeset.newBuilder();
                while (rst.next()) {
                    var id = rst.getLong(1);
                    changesetBuilder.setId(id);
                    changesetBuilder.setCreatedAt(timestampToLong(rst.getTimestamp(2)));
                    changesetBuilder.setClosedAt(timestampToLong(rst.getTimestamp(3)));
                    @SuppressWarnings("unchecked")
                    var tags = (Map<String, String>) rst.getObject(4);
                    var hashTags = hashTags(tags);
                    changesetBuilder.setHashtags(hashTags);
                    changesetBuilder.setTags(Map.copyOf(tags));
                    changesetBuilder.setHot(
                            hashTags.stream().map(HASHTAG_HOTOSM::matcher).anyMatch(Matcher::find));
                    map.put(id, changesetBuilder.build());
                }
                return map;
            }
        } catch (Exception e) {
            throw new OSHDBException(e);
        }
    }

    private Long timestampToLong(Timestamp ts) {
        return ts != null ? ts.getTime() : null;
    }

    private List<CharSequence> hashTags(Map<String, String> tags) {
        // """source""=>""Bing"",
        // ""comment""=>""#hotosm-project-12009 #MissingMaps_DRK #redcross #missingmaps"",
        // ""hashtags""=>""#hotosm-project-12009;#MissingMaps_DRK;#redcross;#missingmaps"", ""created_by""=>""JOSM/1.5 (18583 en)"""
        var hashtags = new TreeSet<String>();
        for (var comment : List.of(tags.getOrDefault("hashtags",""), tags.getOrDefault("comment",""))) {
            var matcher = HASHTAG_PATTERN.matcher(comment);
            while (matcher.find()) {
                hashtags.add(matcher.group());
            }
        }
        return new ArrayList<>(hashtags);
    }
}
