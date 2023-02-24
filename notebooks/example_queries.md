# Example Queries (EQs)
This section describes example queries using "pseudo"-code.
You can't use these queries directly, but should read them as a description of what should be analyzed.

### Example Queries:
* [EQ1 Number of OSM Contributors](eq1-number-of-osm-contributors)
* [EQ2 Number of OSM Changesets](#eq2-number-of-osm-changesets)
* [EQ3 Total Map Edits](#eq3-total-map-edits)
* [EQ4 Number of Added Features (Feature Creation)](#eq4-number-of-added-features--feature-creation-)
* [EQ5 Added km Feature Length ](#eq5-added-km-feature-length)

### Definitions
* [Hashtags](#hashtags)
* [Topics](#topics)
* [Time Range and Time Interval](#time-range-and-time-interval)
* [Datasets](#datasets)

## Example Queries:
### EQ1 Number of OSM Contributors
```sql
select
count(distinct osm_user_id) as n_contributors
from contributions
where
hashtag = '#your_hashtag';
```

This is the number of distinct OSM users that contributed at least one changeset to a particular hashtag since the start of collecting data.
This is the very first number displayed on the company leaderboards as `contributors`.

![image](https://user-images.githubusercontent.com/7045979/220880337-2cde7063-e9b8-42b7-9064-1e23e89de813.png)

### EQ2 Number of OSM Changesets
```sql
select
count(distinct changeset_id) as n_changesets
from contributions
where
hashtag = '#your_hashtag';
```

This is the number of distinct OSM changesets contributed to a particular hashtag since the start of collecting data.
This number is of less important for partners or reporting at mapathons and often this causes confusion.

However, internally this number will be helpful to compare different processing approaches.

### EQ3 Total Map Edits
```sql
select
count(*) as n_total_map_edits
from contributions
where
hashtag = '#your_hashtag';
```

This is the sum of the [primary map features](#primary-map-features) edited in OSM contributed to a particular hashtag since the start of collecting data.
This should consider only edits for OSM elements with the tag keys defined in the OSM Wiki as [Map Features](https://wiki.openstreetmap.org/wiki/Map_features).
In general, we want to avoid "exponentially exploding" statistics for counting total map edits, such as considering each edit to nodes without any tags.

This is the second number displayed on the company leaderboards as `Total Edits`.
(It is not clear what `Total Edits` exactly means for the osmstats-api.)

![image](https://user-images.githubusercontent.com/7045979/220887029-738048ec-0256-460e-9001-6369460f4d22.png)

Examples for map edit:
* Create a building in OSM with 4 nodes (without tags) and 1 way with the tag `building=yes`. This change should be counted as 1 map edit.
* Create a new road in OSM and tags it with `highway=primary`. This change should be counted as 1 map edit.
* Modify the tag of an existing road in OSM from `highway=primary` to `highway=secondary`. This change should be counted as 1 map edit.
* Modify the geometry of an existing building in OSM  and moves 2 nodes. This change should be counted as 1 map edit. (This is a minor edit for the building from OpenStreetMap History Database (OSHDB) perspective.)
* Split a road into two parts. This results in a modification for the existing road part, and a creation for the other road part. This change should be counted as 2 map edits.
* Simplify the geometry of an existing building by removing 5 out of 9 nodes for this way. This change should be counted as 1 map edit. (This will also result in a new version for the OSM way as the list of node members changed.)
* Remove the tag `building=yes` from an existing way. The way remains with other tags (or maybe even no tags at all). This change should be counted as 1 map edit.
* Add the tag `highway=unclassified` to an existing way without any tags. This change should be counted as 1 map edit.
* Add the tag `short_name=HeiGIT` to an existing node tagged as `office=research`. This change should be counted as 1 map edit.
* Delete a building (way and all nodes). This change should be counted as 1 map edit.
* Delete a road (delete the way, but not all nodes). This change should be counted as 1 map edit.

Examples for **NO** map edit:
* Create, move or delete a node, way or relation in OSM which has no tags. This change should NOT be counted as map edit.
* Create a node with the tag `source=survey`, but which has no other tag defined. This change should NOT be counted as map edit.

### EQ4 Number of Added Features (Feature Creation)
```sql
select
count(*) as n_buildings_added_to_osm
from contributions
where
hashtag = '#your_hashtag' AND
topic_name = 'buildings' AND
contribution_type = 'created';
```

This query should be available for all topics.
This is the number of OSM features newly tagged as buildings/roads/any-other-topic-name according to the [definitions above](#tag-filter-definitions-for-topics).
The number of features added considers a) newly created OSM features and also b) specific tag changes to existing OSM features.

When considering buildings, this should be the third number on the company leaderboards, which is currently displayed as `Building Edits`.
(It is not clear what `Building Edits` exactly means for the osmstats-api.)

![image](https://user-images.githubusercontent.com/7045979/220890270-ddc9415f-a1a7-4fad-900d-10c88fd3597b.png)

examples for feature added:
* Create 1 new way (and 4 new nodes without tags) tagged as `building=house`. This change should be counted as 1 building added to OSM.
* Modify an existing way tagged as `amenity=school` by adding the tag `buildng=yes`. This change should be counted as 1 building added to OSM.

examples for **NO** feature added:
* Create 1 new way (and 4 new nodes) tagged as `building=no`. This change should NOT be counted as building added to OSM according to the filter definition for buildings because the value `no` is not in the list of considered tag values.

### EQ5 Added km Feature Length 
```sql
select
sum(delta_length_km) as road_length_added_to_osm
from contributions
where
hashtag = '#your_hashtag AND
topic_name = 'roads' AND
contribution_type IN ('created', 'geometry-change', 'tag-and-geometry-change');
```

This query should be available for all topics for which the unit `length` is specified.
This is length of OSM features which are newly tagged as /roads/any-other-topic-name according to the [definitions above](#tag-filter-definitions-for-topics) and also considers the change in length when the geometry of existing /roads/any-other-topic-name is modified.
Changes that only modify tags, but not the geometry of a feature, are not considered.
The change in length resulting from deletions is not considered.

### EQ6 Combined Number of OSM Contributors
EQ6.1 AND Combined Number of OSM Contributors
```sql
select
count(distinct osm_user_id) as n_contributors
from contributions
where
hashtag = '#your_hashtag1' AND hashtag = '#your_hashtag2';
```

EQ6.2 OR Combined Number of OSM Contributors
```sql
select
count(distinct osm_user_id) as n_contributors
from contributions
where
hashtag = '#your_hashtag1' OR hashtag = '#your_hashtag2';
```

## Queries with time range filter and no group by
### EQ7
```sql
select
count(*) as n_buildings_added_to_osm
from contributions
where
hashtag = '#your_hashtag' AND
building_area IS NOT NULL AND
contribution_type = 'created' AND
changeset_timestamp > '2014-01-01' AND
changeset_timestamp <= '2023-01-01';
```

## Queries with time range filter and daily or monthly stats values

### EQ8
```sql
select
date_trunc('month', changeset_timestamp) as month
,count(distinct osm_user_id) as n_contributors
from contributions
where
hashtag = ANY ('missingmaps', 'hotosm-project-*') AND
changeset_timestamp > '2014-10-01' AND
changeset_timestamp <= '2023-01-01'
group by month
order by month;
```

### EQ9
```sql
select
date_trunc('day', changeset_timestamp) as day
,count(distinct osm_user_id) as n_contributors
from contributions
where
country_iso_a3 = ANY ('DEU', 'FRA') AND
changeset_timestamp > '2022-06-01' AND
changeset_timestamp <= '2023-01-01'
group by day
order by day;
```

# Definitions
## Hashtags
Hashtags should be case-insensitive. This means that when you are adding `#missingmaps` or `#MissingMaps` or `#missingMaps` to a OSM changeset comment this will all be counted towards lower case `missingmaps`. This is currently the case for the processing of the osmstats-api.

The following hashtags are considered for testing and validating the example queries:
* `#missingmaps`
* `#MissingMaps` (should tive the same results as `missingmaps`) 
* `#bloomberg`
* `#visa`
* `#hotosm-project-14154`
* `#hotosm-project-*` (uses `*` as a wildcard to get all HOT Tasking Manager projects)

## Topics
Topics are described by pre-defined filters which consider OSM Type, Geometry Type, tag keys and tag values. For each topic one or more units are assigned, which describe the kind of summary statistics that will be provided.

### Filter Definitions for Topics

### Primary Map Features
| Property      | Description                                                                                                                                                                                                                                                                  | 
|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Topic Name    | Primary Map Features                                                                                                                                                                                                                                                         |
| OSM Type      | node, way, relation                                                                                                                                                                                                                                                          |
| Geometry Type | Point, Linestring, Polygon, MultiPolygon, GeometryCollection                                                                                                                                                                                                                 |
| OSM Tag Key   | aerialway, aeroway, amenity, barrier, boundary, building, craft, emergency, geological, healthcare, highway, historic, landuse, leisure, man_made, military, natural, office, place, power, public_transport, railway, route, shop, sport, telecom, tourism, water, waterway |
| OSM Tag Value | * (any value is considered)                                                                                                                                                                                                                                                  |
| Unit          | count                                                                                                                                                                                                                                                                        |
When referring to the total number of map edits, we will play this filter definition. The "primary map features" are described in the [OSM Wiki](https://wiki.openstreetmap.org/wiki/Map_features#Highway) as an attempt to foster coherence at the key-level and provide a list of tags grouped by topic. The Map Features list currently contains 29 keys or object types.

### Buildings
| Property      | Description                                                                            | 
|---------------|----------------------------------------------------------------------------------------|
| Topic Name    | Buildings                                                                              |
| OSM Type      | way, relation                                                                          |
| Geometry Type | Polygon, MultiPolygon                                                                  |
| OSM Tag Key   | building                                                                               |
| OSM Tag Value | yes, house, residential, detached, detached, garage, apartments, shed, hut, industrial |
| Unit          | count, area [sqkm]                                                                     |

These tag values account for more than 95% of all OSM features tagged with the building key according to [taginfo](https://taginfo.openstreetmap.org/keys/building#values).

### Roads
| Property       | Description                                                                                                                                      | 
|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| Topic Name     | Roads                                                                                                                                            |
| OSM Type       | way, relation                                                                                                                                    |
| Geometry Type  | Linestring                                                                                                                                       |
| OSM Tag Keys   | highway                                                                                                                                          |
| OSM Tag Values | motorway, trunk, motorway_link, trunk_link, primary, primary_link, secondary, secondary_link, tertiary, tertiary_link, unclassified, residential |
| Unit           | count, length [km]                                                                                                                               |

These are the principal tag values for the road network and the link roads as defined in the [OSM Wiki](https://wiki.openstreetmap.org/wiki/Map_features#Highway). Special road types (e.g. tracks, paths or footways) are not considered in this topic.


## Time Range and Time Interval
* osm-stats API contains changesets from `20XX-xx-xx`
* changeset DB contains changesets from the beginning of OSM

## Datasets
comparison of different approaches:
* osmstats-api
* OSM changeset DB (+ OSHDB)
* parquet file with OSHDB contributions
