with
    temp_users_changesets_by_day AS (
        SELECT
            user_id
            ,min(toStartOfDay(fromUnixTimestamp((changeset_timestamp/1000)::integer))) as first_day
            ,max(toStartOfDay(fromUnixTimestamp((changeset_timestamp/1000)::integer))) as last_day
            ,count(distinct toStartOfDay(fromUnixTimestamp((changeset_timestamp/1000)::integer))) as active_days
        FROM __stats_all_summed_unnested_lowercase
        where hashtag = '#bloomberg'
        GROUP BY user_id
    ),
    temp_users_all as (
        SELECT
            count(*) as users_all
        FROM temp_users_changesets_by_day
    )
SELECT
    active_days
    ,SUM(count(user_id)) OVER (ORDER BY active_days DESC) as users_active
    ,round(
        SUM(count(user_id)) OVER (ORDER BY active_days DESC)
        /
        max(t2.users_all), 3) as survival_rate
FROM temp_users_changesets_by_day as t1, temp_users_all as t2
GROUP BY active_days
ORDER BY active_days;