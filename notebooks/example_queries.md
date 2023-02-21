# Example Queries
This file describes example queries using "pseudo"-code. You can't use these queries directly, but should read them as a description of what should be analyzed.

## General
comparison of different approaches:
* osmstats-api
* OSM changeset DB (+ OSHDB)
* parquet file with OSHDB contributions

hashtags to consider:
* `#missingmaps`
* `#bloomberg`
* `#visa`
* `#hotosm-project-*`
* `#hotosm-project-14154`

time range:
* this might depend on the service/api used

### EQ1 Number of OSM Contributors
```sql
select
count(distinct osm_user_id) as n_contributors
from contributions
where
hashtag = '#your_hashtag';
```

### EQ2 Number of OSM Changesets
```sql
select
count(distinct changeset_id) as n_changesets
from contributions
where
hashtag = '#your_hashtag';
```

### EQ3 Total Map Edits
```sql
select
count(*) as n_total_map_edits
from contributions
where
hashtag = '#your_hashtag';
```

### EQ4 Buildings added to OSM
```sql
select
count(*) as n_buildings_added_to_osm
from contributions
where
hashtag = '#your_hashtag' AND
building_area IS NOT NULL AND
contribution_type = 'created';
```

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
