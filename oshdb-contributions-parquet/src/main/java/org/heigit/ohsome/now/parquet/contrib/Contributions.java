package org.heigit.ohsome.now.parquet.contrib;

import static com.google.common.collect.Iterators.peekingIterator;
import com.google.common.collect.Maps;
import com.google.common.collect.PeekingIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import org.heigit.ohsome.oshdb.osh.OSHEntity;
import org.heigit.ohsome.oshdb.osh.OSHNode;
import org.heigit.ohsome.oshdb.osm.OSMEntity;
import org.heigit.ohsome.oshdb.osm.OSMMember;
import org.heigit.ohsome.oshdb.osm.OSMNode;
import org.heigit.ohsome.oshdb.osm.OSMRelation;
import org.heigit.ohsome.oshdb.osm.OSMType;
import org.heigit.ohsome.oshdb.osm.OSMWay;

public abstract class Contributions implements PeekingIterator<Contribution> {
  private static final Contributions EMPTY_CONTRIBUTIONS = new NullContributions();

  public static Contributions of(OSHEntity osh) {
    if (osh == null) {
      return null;
    }
    switch (osh.getType()) {
      case NODE:
        return new Nodes((OSHNode) osh);
      case WAY:
      case RELATION:
        return new WayRels(osh);
      default:
        throw new NoSuchElementException();
    }
  }

  public abstract OSMType getType();

  public abstract long getId();

  private Contribution next;

  @Override
  public boolean hasNext() {
    return next != null || (next = computeNext()) != null;
  }

  @Override
  public Contribution peek() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return next;
  }

  @Override
  public Contribution next() {
    var contrib = peek();
    next = null;
    if (hasNext()) {
      contrib.setBefore(next);
    }
    return contrib;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("remove");
  }

  public long peekTimestamp() {
    return peek().getTimestamp();
  }

  public long peekChangeset() {
    return peek().getChangeset();
  }


  protected abstract Contribution computeNext();

  private static class Nodes extends Contributions {
    private final long id;
    private final PeekingIterator<OSMNode> majorVersions;

    private Nodes(OSHNode osh) {
      this.id = osh.getId();
      this.majorVersions = peekingIterator(osh.getVersions().iterator());
    }

    @Override
    public OSMType getType() {
      return OSMType.NODE;
    }

    @Override
    public long getId() {
      return id;
    }

    @Override
    public Contribution computeNext() {
      if (!majorVersions.hasNext()) {
        return null;
      }
      var major = majorVersions.next();
      return new Contribution(major);
    }
  }

  private static class WayRels extends Contributions {
    protected static final Comparator<Contributions> QUEUE_ORDER = Comparator
        .comparingLong(Contributions::peekTimestamp)
        .thenComparingLong(Contributions::peekChangeset)
        .reversed();

    protected final Map<OSMType, Map<Long, OSHEntity>> oshMembers = new EnumMap<>(OSMType.class);
    protected final Map<OSMType, Map<Long, Contributions>> memberContribs =
        new EnumMap<>(OSMType.class);
    protected final OSMType type;
    protected final long id;
    protected final PeekingIterator<OSMEntity> majorVersions;
    protected final PriorityQueue<Contributions> queue;
    protected OSMEntity major;
    protected long timestamp = Long.MAX_VALUE;
    protected long changeset = -1;
    protected Map<OSMType, Set<Long>> active = Collections.emptyMap();

    protected WayRels(OSHEntity osh) {
      this.type = osh.getType();
      this.id = osh.getId();
      this.majorVersions = peekingIterator(osh.getVersions().iterator());

      initMembers(OSMType.NODE, osh.getNodes());
      initMembers(OSMType.WAY, osh.getWays());

      var membersSize = oshMembers.values().stream()
          .mapToInt(Map::size)
          .sum();
      queue = new PriorityQueue<>(Math.max(1, membersSize), QUEUE_ORDER);
      nextMajor();
    }

    @Override
    public OSMType getType() {
      return type;
    }

    @Override
    public long getId() {
      return id;
    }

    protected OSMMember[] members(OSMEntity osm) {
      if (type == OSMType.WAY) {
        return ((OSMWay) osm).getMembers();
      }
      return ((OSMRelation) osm).getMembers();
    }

    private void initMembers(OSMType type, List<? extends OSHEntity> list) {
      if (list.isEmpty()) {
        return;
      }
      var entities = Maps.<Long, OSHEntity>newHashMapWithExpectedSize(list.size());
      var contribs = Maps.<Long, Contributions>newLinkedHashMapWithExpectedSize(list.size());
      list.forEach(n -> entities.put(n.getId(), n));
      oshMembers.put(OSMType.NODE, entities);
      memberContribs.put(OSMType.WAY, contribs);
    }

    private void nextMajor() {
      major = majorVersions.hasNext() ? majorVersions.next() : null;
      if (major == null || !major.isVisible()) {
        return; // nothing to init when major is a deletion
      }

      var newActive = new EnumMap<OSMType, Set<Long>>(OSMType.class);
      Arrays.stream(members(major))
          .filter(m -> newActive
              .computeIfAbsent(m.getType(), x -> new HashSet<>())
              .add(m.getId()))
          .filter(m -> !active
              .getOrDefault(m.getType(), Collections.emptySet())
              .contains(m.getId())) // was no active before
          .map(m -> memberContribs
              .computeIfAbsent(m.getType(), x -> new HashMap<>())
              .computeIfAbsent(m.getId(), x -> memberContribs(m)))
          .filter(Objects::nonNull)
          .filter(Contributions::hasNext)
          .forEach(queue::add);

      queue.removeIf(contribs -> !newActive
          .getOrDefault(contribs.getType(), Collections.emptySet())
          .contains(contribs.getId()));
      active = newActive;
    }

    private Contributions memberContribs(OSMMember member) {
      var osh = oshMembers
          .getOrDefault(member.getType(), Collections.emptyMap())
          .get(member.getId());
      return Contributions.of(osh);
    }

    @Override
    protected Contribution computeNext() {
      if (major == null) {
        return null;
      }

      if (!major.isVisible()) {
        var contrib = contribution(Collections.emptyList());
        nextMajor();
        return contrib;
      }

      advanceQueue();
      var members = new ArrayList<Contribution>(members(major).length);
      for (var mem : members(major)) {
        var contribs = memberContribs
            .getOrDefault(mem.getType(), Collections.emptyMap())
            .getOrDefault(mem.getId(), EMPTY_CONTRIBUTIONS);
        if (contribs.hasNext()) {
          members.add(contribs.peek());
        } else {
          members.add(null);
        }

      }

      if (!queue.isEmpty() && queue.peek().peek().getTimestamp() >= major.getEpochSecond()
          && queue.peek().peek().getChangeset() != major.getChangesetId()) {
        // minor version;
        var minorContribs = queue.poll();  // remove from queue
        var minorContrib = minorContribs.next();
        if (minorContribs.hasNext()) {
          queue.add(minorContribs);
        }
        return contribution(minorContrib, members);
      }
      var timestamp =
          !queue.isEmpty() ? Math.max(queue.peek().peek().getTimestamp(), major.getEpochSecond())
              : major.getEpochSecond();
      var contrib = contribution(timestamp, major.getChangesetId(), major.getUserId(), members);
      nextMajor();
      return contrib;
    }

    protected void advanceQueue() {
      while (!queue.isEmpty() && (queue.peek().peek().getTimestamp() > timestamp
          || queue.peek().peek().getChangeset() == changeset)) {
        var contribs = queue.poll();
        while (contribs.hasNext() && (contribs.peek().getTimestamp() > timestamp
            || contribs.peek().getChangeset() == changeset)) {
          contribs.next(); // skip
        }
        if (contribs.hasNext()) {
          queue.add(contribs);
        }
      }
    }

    private Contribution contribution(List<Contribution> members) {
      return contribution(major.getEpochSecond(), major.getChangesetId(), major.getUserId(),
          members);
    }

    private Contribution contribution(Contribution minor, List<Contribution> members) {
      return contribution(minor.getTimestamp(), minor.getChangeset(), minor.getUser(), members);
    }

    private Contribution contribution(long timestamp, long changeset, int user,
        List<Contribution> members) {
      this.timestamp = timestamp;
      this.changeset = changeset;
      return new Contribution(timestamp, changeset, user, major, members);
    }
  }

  private static class NullContributions extends Contributions {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Contribution peek() {
      return null;
    }

    @Override
    public Contribution next() {
      return null;
    }

    @Override
    public OSMType getType() {
      return null;
    }

    @Override
    public long getId() {
      return -1;
    }

    @Override
    protected Contribution computeNext() {
      return null;
    }
  }
}

