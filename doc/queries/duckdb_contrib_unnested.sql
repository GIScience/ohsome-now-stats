SELECT

    count(distinct changeset_id) as changesets,
    count(distinct user_id) as users,

    sum(road_length) as roads,
    count(*) as edits,

    max(changeset_timestamp) as latest

FROM "stats_indexhashtag"
WHERE
        hashtag = '#MissingMaps';
