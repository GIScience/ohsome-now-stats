# OhsomeStats

## Download
- ask the team at HeiGIT if you need access to the parquet file.

## Directory Structure
the parquet files (44GB in total) are group by type and year.
```
ohsome-stats-parquet
├── type=node
│   ├── year=2005
│   │   ├── contribs-40abae66-9607-4276-8d89-ab3f9ca96f91.parquet
│   │   ├── contribs-c71f1f6d-8373-4122-bc30-ee2016f74547.parquet
│   │   └── contribs-fa45c1b5-7371-4041-9e5d-0057ca6a8c66.parquet
    ...
│   └── year=2023
│       ├── contribs-40abae66-9607-4276-8d89-ab3f9ca96f91.parquet
│       ├── contribs-c71f1f6d-8373-4122-bc30-ee2016f74547.parquet
│       └── contribs-fa45c1b5-7371-4041-9e5d-0057ca6a8c66.parquet
└── type=way
    ├── year=2005
    │   ├── contribs-40abae66-9607-4276-8d89-ab3f9ca96f91.parquet
    │   ├── contribs-c71f1f6d-8373-4122-bc30-ee2016f74547.parquet
    │   └── contribs-fa45c1b5-7371-4041-9e5d-0057ca6a8c66.parquet
    ...
    └── year=2023
        ├── contribs-40abae66-9607-4276-8d89-ab3f9ca96f91.parquet
        ├── contribs-c71f1f6d-8373-4122-bc30-ee2016f74547.parquet
        └── contribs-fa45c1b5-7371-4041-9e5d-0057ca6a8c66.parquet
```

## SQL example:
```
select 
    'https://osm.org/' || type || '/' || osm_id as osm, 
    'https://osm.org/changeset/' || changeset_id as changeset, 
    epoch_ms(changeset_timestamp) as timestamp, 
    hashtags, building_area as area, 
    building_area_delta as delta, 
    country_iso_a3 as country 
from read_parquet('ohsome-stats-parquet/*/*/*', HIVE_PARTITIONING=true)
where list_contains(hashtags, '#hotosm') and building_area is not null limit 3;
```
### Result
```
│              osm               │              changeset              │      timestamp      │                                                                         hashtags                                                                         │       area        │       delta        │  country  │
│            varchar             │               varchar               │      timestamp      │                                                                        varchar[]                                                                         │      double       │       double       │ varchar[] │
├────────────────────────────────┼─────────────────────────────────────┼─────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────┼────────────────────┼───────────┤
│ https://osm.org/way/1041344349 │ https://osm.org/changeset/118618903 │ 2022-03-18 06:30:45 │ [#Bangladesh, #Map4resilience, #OSMBDfacilitativeteam, #WM4Mar, #boil, #hot, #hotosm, #hotosm-project-11687, #mapbd, #missingmaps, #osmbd, #tech4social] │ 74.42127179780857 │  74.42127179780857 │ [UGA]     │
│ https://osm.org/way/441255796  │ https://osm.org/changeset/116365179 │ 2022-01-20 03:08:32 │ [#Bangladesh, #Map4resilience, #boil, #buildings_&_roads_mapping, #hot, #hotosm, #hotosm-project-11687, #mapbd, #missingmaps, #osmbd, #tech4social]      │ 301.1526051851149 │                0.0 │ [BGD]     │
│ https://osm.org/way/441256492  │ https://osm.org/changeset/116365179 │ 2022-01-20 03:08:32 │ [#Bangladesh, #Map4resilience, #boil, #buildings_&_roads_mapping, #hot, #hotosm, #hotosm-project-11687, #mapbd, #missingmaps, #osmbd, #tech4social]      │ 180.5776469988827 │ 0.8253627420864689 │ [BGD]     |
```


