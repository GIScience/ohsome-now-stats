select
    toYear(
        fromUnixTimestamp((changeset_timestamp/1000)::integer)
    ) as year
    ,count(*)
    ,sum(building_area_delta)
from stats_2
where has(country_iso_a3, 'USA')
group by year;

select
    toYear(fromUnixTimestamp((changeset_timestamp/1000)::integer)) as year
    ,count(*)
from stats_2
where hashtag = '#MissingMaps' and building_area_delta is not null
group by year;


select
    toYear(fromUnixTimestamp((changeset_timestamp/1000)::integer)) as year
    ,count(*) / (1000*1000)
    ,count(distinct osm_id) / (1000*1000)
from stats_2
where hashtag = '#MissingMaps' or hashtag = '#missingmaps'
group by year;

select
    toYear(fromUnixTimestamp((changeset_timestamp/1000)::integer)) as year
    ,count(*) / (1000*1000)
    ,count(distinct osm_id) / (1000*1000)
    ,count(distinct user_id)
from stats_2
where hashtag = '#uniHD'
group by year;


select
    toStartOfDay(fromUnixTimestamp((changeset_timestamp/1000)::integer)) as day
    ,count(*) / (1000*1000)
    ,count(distinct contrib_id_text) / (1000*1000)
    ,count(distinct osm_id) / (1000*1000)
    ,count(distinct user_id)
from stats_2
where
    (hashtag = '#MissingMaps' or hashtag = '#missingmaps' or hashtag like 'hotosm-project-%')
    and
    toYear(fromUnixTimestamp((changeset_timestamp/1000)::integer)) >= 2022
    and
    has(country_iso_a3, 'UGA')
group by day;
