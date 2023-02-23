# Example Queries (EQs)
This file describes example queries using "pseudo"-code. You can't use these queries directly, but should read them as a description of what should be analyzed.

## General
comparison of different approaches:
* osmstats-api
* OSM changeset DB (+ OSHDB)
* parquet file with OSHDB contributions

## Hashtags
Hashtags should be case insensitive. This means that when you are adding `#missingmaps` or `#MissingMaps` or `#missingMaps` this will all be counted towards lower case `missingmaps`. This is currently the case for the processing of the osmstats-api.

The following hashtags are considered for testing and validating the example queries:
* `#missingmaps`
* `#MissingMaps` (should tive the same results as `missingmaps`) 
* `#bloomberg`
* `#visa`
* `#hotosm-project-14154`
* `#hotosm-project-*` (uses `*` as a wildcard to get all HOT Tasking Manager projects)

## Time Range:
* osm-stats API contains changesets from `20XX-xx-xx`
* changeset DB contains changesets from the beginning of OSM


### EQ1 Number of OSM Contributors
This is the number of distinct OSM users that contributed at least one changeset to a particular hashtag since the start of collecting data. This is the very first number displayed on the company leaderboards as `contributors`.

![image](https://user-images.githubusercontent.com/7045979/220880337-2cde7063-e9b8-42b7-9064-1e23e89de813.png)

```sql
select
count(distinct osm_user_id) as n_contributors
from contributions
where
hashtag = '#your_hashtag';
```

### EQ2 Number of OSM Changesets
This is the number of distinct OSM changesets contributed to a particular hashtag since the start of collecting data. This number is of less important for partners or reporting at mapathons and often this causes confusion.

However, internally this number will be helpful to compare different processing approaches.

```sql
select
count(distinct changeset_id) as n_changesets
from contributions
where
hashtag = '#your_hashtag';
```

### EQ3 Total Map Edits
This is the sum of the total features edited in OSM contributed to a particular hashtag since the start of collecting data. This should consider only edits for OSM features with tags. In general we want to avoid "exponentially exploding" statistics for counting total map edits. This is the second number displayed on the company leaderboards as `Total Edits`.

![image](https://user-images.githubusercontent.com/7045979/220887029-738048ec-0256-460e-9001-6369460f4d22.png)

For example:

creation and modification:
* a user creates a squared building in OSM with with 4 nodes and 1 way with the tag `building=yes`. This change should be counted as 1 map edit.
* a user creates a new road in OSM and tags it with `highway=primary`. This change should be counted as 1 map edit.
* a user modifies the tag of an existing road in OSM from `highway=primary` to `highway=secondary`. This change should be counted as 1 map edit.
* a user modifies the geometry of an existing building in OSM  and moves 2 nodes and resquares the building. This change should be counted as 1 map edit. This is a minor edit for the building from OpenStreetMap History Database (OSHDB) perspective.
* a users splits a road into two parts. This change should be counted as 2 map edits.
* a users simplifies the geometry of an existing building by removing 5 out of 9 nodes for this way. This change should be counted as 1 map edit. (This will also result in a new version for the OSM way as the list of node members changed)

deletion:
* a user deletes a building (way and all nodes). This change should be counted as 1 map edit.
* a user deletes a highway (deletes the way, but not all nodes). This change should be counted as 1 map edit.

```sql
select
count(*) as n_total_map_edits
from contributions
where
hashtag = '#your_hashtag';
```

### EQ4
We consider a "building" as (Multi)Polygon mapped as OSM way or OSM relation tagged with the key `building` and any of the following values (which account for more than 95% of all OSM features tagged with the building key according to [taginfo](https://taginfo.openstreetmap.org/keys/building#values):
* yes
* house
* residential
* detached
* garage
* apartments
* shed
* hut
* industrial

#### EQ4.1 Buildings Added to OSM
This is the number of OSM features tagged for the first as a building according to the definion above.
This should be the third number on the company leaderboards, which is currently displayed as `Building Edits` (and is not clear what this means for osmstats-api). ![image](https://user-images.githubusercontent.com/7045979/220890270-ddc9415f-a1a7-4fad-900d-10c88fd3597b.png)

examples:
* a user creates 1 new way (and 4 new nodes) and tags the way with `building=house`. This change should be counted as 1 building added to OSM.
* a user modifies an existing way tagged as `amenity=school` and adds the tag `buildng=yes`. This change should be counted as 1 building added to OSM.
* a user modifieds an existing way tagged as `building=yes` and changes the tag into `building=residential`. This change should NOT be counted as 1 building added to OSM.

```sql
select
count(*) as n_buildings_added_to_osm
from contributions
where
hashtag = '#your_hashtag' AND
building_area IS NOT NULL AND
contribution_type = 'created';
```

#### EQ4.2 Buildings Modified in OSM
* tbd


#### EQ4.3 Buildings Deleted in OSM
* tbd

### EQ5 Road Length Added to OSM
```sql
select
sum(road_length) as road_length_added_to_osm
from contributions
where
hashtag = '#your_hashtag AND
contribution_type IN ('created', 'geom', 'tag-geom');
```

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
