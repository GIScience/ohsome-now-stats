SELECT

    count(distinct changeset_id) as changesets,
    count(distinct user_id) as users,

    sum(road_length) as roads,
    sum(buildings) as buildings,
    sum(edits) as edits,

    FROM_UNIXTIME(intDiv(max(changeset_timestamp), 1000)) as latest

FROM "__stats_all_summed_unnested"
WHERE
        hashtag = '#hotosm-project-14226';

--     hashtag = '#hotosm';
--     hashtag = '#missingmaps';

