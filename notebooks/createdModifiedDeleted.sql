SELECT hashtag, SUM(road_length_delta),SUM(abs(road_length_delta)), road_edit
FROM stats
WHERE hashtag IN ('factset22', 'visamappers', 'salesforce', 'blackrock20', 'aig', 'headingtonschooloxford', 'jlr', 'ftmaps', 'flexvolunteering', 'ourimpact', 'atlassian', 'acthotpilot21', 'awsmapathon', 'hpegives', 'cisco', 'msgivesback', 'hpinspiresgiving', 'accenture', 'aviva', 'msfglobalmapathon2019', 'jpmc', 'amm', 'dhl', 'bloomberg', 'hoganlovells', 'msft', 'missingmaps')
GROUP BY hashtag, road_edit
ORDER BY hashtag, road_edit;