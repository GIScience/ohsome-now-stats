SELECT

    count(distinct changeset_id) as changesets,
    count(distinct users) as users,

    sum(roads) as roads,
    sum(edits) as edits,

    max(latest) as latest

FROM "stats_all_summed_unnested"
WHERE
        hashtag = '#hotosm';

-- #hotosm-project-14226
-- #hotosm