package org.heigit.ohsome.now.parquet.contrib;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.heigit.ohsome.oshdb.osm.OSMEntity;
import org.heigit.ohsome.oshdb.osm.OSMNode;
import org.heigit.ohsome.oshdb.osm.OSMType;

public class Contribution {
  private final long timestamp;
  private final long changeset;
  private final int user;
  private final OSMEntity entity;
  private final List<Contribution> members;
  private final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();

  private Contribution before;

  public Contribution(OSMNode osm) {
    this(osm.getEpochSecond(), osm.getChangesetId(), osm.getUserId(), osm);
  }

  public Contribution(long timestamp, long changeset, int user, OSMEntity entity) {
    this(timestamp, changeset, user, entity, Collections.emptyList());
  }

  public Contribution(long timestamp, long changeset, int user, OSMEntity entity,
      List<Contribution> members) {
    this.timestamp = timestamp;
    this.changeset = changeset;
    this.user = user;
    this.entity = entity;
    this.members = members;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getChangeset() {
    return changeset;
  }

  public int getUser() {
    return user;
  }

  public long getId() {
    return entity.getId();
  }

  public OSMType getType() {
    return entity.getType();
  }

  public OSMEntity  getEntity() {
    return entity;
  }

  public List<Contribution> getMembers() {
    return members;
  }

  public Optional<Contribution> before() {
    return Optional.ofNullable(before);
  }

  public Map<String, Object> data() {
    return data;
  }

  @SuppressWarnings("unchecked")
  public <T> T data(String key, Function<Contribution, T> fnt) {
     return (T) data.computeIfAbsent(key, x -> fnt.apply(this)) ;
  }

  public void setBefore(Contribution before) {
    this.before = before;
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeset, timestamp, getType(), getId());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Contribution)) {
      return false;
    }
    Contribution other = (Contribution) obj;
    return changeset == other.changeset
        && timestamp == other.timestamp
        && getType() == other.getType()
        && getId() == other.getId();
  }
}

