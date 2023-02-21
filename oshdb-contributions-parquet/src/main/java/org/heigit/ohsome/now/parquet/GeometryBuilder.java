package org.heigit.ohsome.now.parquet;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import org.heigit.ohsome.now.parquet.contrib.Contribution;
import org.heigit.ohsome.oshdb.osm.OSMEntity;
import org.heigit.ohsome.oshdb.osm.OSMNode;
import org.heigit.ohsome.oshdb.osm.OSMWay;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

public class GeometryBuilder implements Serializable {

  private static final GeometryFactory geomFactory = new GeometryFactory();
  public static final double OSM_COORDINATE_SCALE = 1E7;

  public Geometry getGeometry(Contribution contrib) {
    return getGeometry(contrib, false);
  }

  public Geometry getGeometry(Contribution contrib, boolean area) {
    var entity = contrib.getEntity();
    switch (contrib.getType()) {
      case NODE:
        return getGeometry((OSMNode) entity);
      case WAY:
        return getGeometry((OSMWay) entity, contrib.getMembers(), area);
      case RELATION:
      default:
        throw new UnsupportedOperationException("Relation Geometry are not supported yet");
    }
  }

  public Geometry getGeometry(OSMNode osm) {
    return osm.isVisible() ? geomFactory.createPoint(coord(osm)) : geomFactory.createPoint();
  }

  public Geometry getGeometry(OSMWay osm, List<Contribution> memberContribs, boolean area) {
    if (!osm.isVisible()) {
      return geomFactory.createLineString();
    }
    var coords = coords(memberContribs);

    if (area) {
      if (coords.length >= 4 && isClosed(coords)) {
        return geomFactory.createPolygon(coords);
      }
      // fallback and/or log?
    }
    if (coords.length >= 2) {
      return geomFactory.createLineString(coords);
    }
    if (coords.length == 1) {
      return geomFactory.createPoint(coords[0]);
    }
    return geomFactory.createLineString();
  }

  private Coordinate coord(OSMNode osm) {
    var longitude = osm.getLon() / OSM_COORDINATE_SCALE;
    var latitude = osm.getLat() / OSM_COORDINATE_SCALE;
    return new Coordinate(longitude, latitude);
  }

  private Coordinate[] coords(List<Contribution> wayMembers) {
    return wayMembers.stream()
        .filter(Objects::nonNull)
        .map(Contribution::getEntity)
        .map(OSMNode.class::cast)
        .filter(OSMEntity::isVisible)
        .map(this::coord)
        .toArray(Coordinate[]::new);
  }

  private boolean isClosed(Coordinate[] coords) {
    return coords[0].equals2D(coords[coords.length - 1]);
  }
}

