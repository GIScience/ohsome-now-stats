# Example Queries

## queries without temporal aspects in the filter and no group by

EQ1
```sql
select
count(*) as n_buildings_added_to_osm
from contributions
where
hashtag = ANY ('missingmaps') AND
building_area IS NOT NULL AND
contribution_type = 'created';
```

EQ2
```sql
select
sum(road_length) as road_length_added_to_osm
from contributions
where
hashtag = ANY ('missingmaps') AND
contribution_type IN ('created', 'geom', 'tag-geom');
```

EQ3
```sql
select
count(distinct osm_user_id) as n_contributors
from contributions
-- this should be 'missingmaps' OR 'hotosm-project-*'
where
hashtag = ANY ('missingmaps', 'hotosm-project-*');
```

EQ4
```sql
select
count(distinct changeset_id) as n_changesets
from contributions
where
hashtag = ANY ('missingmaps', 'hotosm-project-*');
```

EQ5
```sql
select
count(*) as n_total_map_edits
from contributions
-- this should be 'DEU' AND 'FRA'
where
country_iso_a3 = ANY ('DEU') AND
country_iso_a3 = ANY ('FRA')
```

## queries with time range filter and no group by:

EQ6
```sql
select
count(*) as n_buildings_added_to_osm
from contributions
where
hashtag = ANY ('missingmaps') AND
building_area IS NOT NULL AND
contribution_type = 'created' AND
changeset_timestamp > '2014-01-01' AND
changeset_timestamp <= '2023-01-01';
```

### queries with time range filter and daily or monthly stats values

EQ7
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

EQ8
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