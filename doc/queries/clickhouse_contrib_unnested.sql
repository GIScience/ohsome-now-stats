SELECT

    count(distinct changeset_id) as changesets,
    count(distinct user_id) as users,
    sum(road_length) as roads,
    count(building_area) as buildings,
    count(*) as edits,
    FROM_UNIXTIME(intDiv(max(changeset_timestamp), 1000)) as latest

FROM "__stats_all_unnested"
WHERE
    hashtag = '#hotosm-project-14226';

--     hashtag = '#hotosm';
--     hashtag = '#missingmaps';

