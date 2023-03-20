# Comparsion of the the free datasources Changeset DB, OSM STATS API and OHSOME NOW

## Changeset Counts

### Stats
|hashtag             |timerange                   |CHDB |OSTMSTATS|OHSOME|
|--------------------|----------------------------|-----|---------|------|
|missingmaps         |['2017-01-01', '2017-12-31']|1121267|1089181  |1112610|
|bloomberg           |['2017-01-01', '2017-12-31']|1429 |1419     |1419  |
|hotosm-project-     |['2017-01-01', '2017-12-31']|1610104|1580420  |1597126|
|missingmaps         |['2022-01-01', '2023-02-07']|293747|291969   |292483|
|bloomberg           |['2022-01-01', '2023-02-07']|8091 |7935     |8055  |
|visa                |['2022-01-01', '2023-02-07']|1821 |1230     |1820  |
|hotosm-project-14154|['2022-01-01', '2023-02-07']|108  |108      |108   |
|hotosm-project-     |['2022-01-01', '2023-02-07']|1361236|1354439  |1352537|

Thh Results show that both, the OSM STATS API and OHSOME NOW are not containing all changesets. While some hashtags, e.g. the bloomberg
or the hotosm-project-14154 show results with no diffrence, the counts for the OSM STATS API tend to be the lowest, while the changestDB constantly the highest count.

### Potential sources for diffrences

#### Parsing of hashtags

The following code snippet is, used to extract the hashtags used to extract the hashtags, stored in the OSM STATS API. 

```javascript
const HASHTAG_REGEX = /(#[^\u2000-\u206F\u2E00-\u2E7F\s\\'!"#$%()*,./:;<=>?@[\]^`{|}~]+)/g;

const extractHashtags = tags => {
  tags.comment = htmlEntities.decode(tags.comment);
  return ((tags.comment || "").match(HASHTAG_REGEX) || []).map(x =>
    x.slice(1).toLowerCase());
};

```

Yet a changesets tags element, can contain a seperate hashtags, attribute, which is generated in most cases by the editor.
Incase the user decides to assing, tha hashtags independently from the comment attribute, the consequence will be that the OSM STATS API will miss these.

In case for the missingmaps hashtag, this e.g. accounts for roughly half of the missing changesets.

#### Applied Filters
The ChangesetDB does not apply any kind of filter and therefore includes all changesets into the DB.

The OSM STATS API applies the following conditions on changesets, before processing and including a contribution to the database.

```javascript
const isInteresting = feature =>
  ["node", "way"].includes(feature.properties.type) &&
  (isBuilding(feature) ||
    isPOI(feature) ||
    isRoad(feature) ||
    isWaterway(feature));
```

The OHSOME NOW Database, also includes only contributions to nodes, or ways.
Both, has as consequence, that changesets, containing only relations are currently not included in the counts.

## UNIQUE USERS

The counts for individual users diverge less, incomparison to the OSM STATS 

| hashtag               |timerange                   |CHDB |OSTMSTATS|OHSOME|
|-----------------------|----------------------------|-----|---------|------|
| missingmaps           |['2017-01-01', '2017-12-31']|28446|28240    |28395 |
| bloomberg             |['2017-01-01', '2017-12-31']|137  |137      |137   |
| hotosm-project-       |['2017-01-01', '2017-12-31']|42901|42550    |42818 |
| missingmaps           |['2022-01-01', '2023-02-07']|11793|11636    |11774 |
| bloomberg             |['2022-01-01', '2023-02-07']|572  |550      |572   |
| visa                  |['2022-01-01', '2023-02-07']|175  |122      |175   |
| hotosm-project-14154  |['2022-01-01', '2023-02-07']|15   |15       |15    |
| hotosm-project-       |['2022-01-01', '2023-02-07']|48149|47970    |48045 |

## Contributions payload

As the Statistics above show, some changesets are missing, to account for this, the following statistics have been calculated only for changesets, which are present in both the OSM STATS API and OHSOME NOW.
### queries (differentiation betweent creation and modification)

The following SQL QUERY is used to classify the contributions. road_length is the length of a contribution, after the contribution, road_legth_delta is the diffrence betweent the length before and after 
```SQL 
SUM(CASE WHEN (road_length == 0.0 and road_length_delta < 0) THEN ABS(road_length_delta) ELSE 0 END) as deletionsRoad_length,
SUM(CASE WHEN (road_length > 0.0 and road_length_delta == road_length) THEN ABS(road_length_delta) ELSE 0 END) as creationsRoad_length,
SUM(CASE WHEN (road_length > 0.0 and road_length_delta != road_length) THEN ABS(road_length_delta) ELSE 0 END) as modificationsRoad_length
```

The OSM STATS API uses the version of an OSM object to differentiate between a creation and modification.

```javascript
module.exports.isModified = ({ properties: { version } }) =>
  Number(version) > 1;

module.exports.isNew = ({ properties: { version } }) => Number(version) === 1;

\\calculation of the diffrences
module.exports = (prev, next) =>
  isWay(prev) && isWay(next) && isRoad(prev) && isRoad(next)
    ? Math.abs(
        length(prev, { units: "kilometers" }) -
          length(next, { units: "kilometers" })
      )
    : 0;
```
Since the OSM STATS API uses the absolute value of the diffrence between modifications to calulate the diffrence between two versions, the same is applied to the road_legth_delta. 
### building counts

|hashtag             |timerange                   |creationsBuildingsOSMSTATS|creationsBuildingsOHSOME|creationBuildingsAbsDiffrence|creationBuildingsRelDiffrence|moodificationsBuildingsOSMSTATS|modificationsBuildingsOHSOME|modificationsBuildingsAbsDiffrence|modificationsBuildingsRelDiffrence|GAP (OSMSTATS - OHSOME)|GAP with DELETIONS |
|--------------------|----------------------------|--------------------------|------------------------|-----------------------------|-----------------------------|-------------------------------|----------------------------|----------------------------------|----------------------------------|-----------------------|-------------------|
|missingmaps         |['2017-01-01', '2017-12-31']|15562631.0                |15651562                |-88931.0                     |0.9943180750905245           |3145157.0                      |2760304                     |384853.0                          |1.1394241358922785                |295922.0               |-432340.0          |
|bloomberg           |['2017-01-01', '2017-12-31']|8958.0                    |9079                    |-121.0                       |0.9866725410287477           |683.0                          |537                         |146.0                             |1.271880819366853                 |25.0                   |-74.0              |
|hotosm-project-     |['2017-01-01', '2017-12-31']|23162134.0                |25234051                |-2071917.0                   |0.9178920182098388           |4369281.0                      |4143332                     |225949.0                          |1.0545331631643324                |-1845968.0             |-2892567.0         |
|missingmaps         |['2022-01-01', '2023-02-07']|4768705.0                 |4989039                 |-220334.0                    |0.9558363845221495           |364686.0                       |1938736                     |-1574050.0                        |0.18810503338257503               |-1794384.0             |-2457604.0         |
|bloomberg           |['2022-01-01', '2023-02-07']|63940.0                   |64201                   |-261.0                       |0.9959346427625738           |3570.0                         |17018                       |-13448.0                          |0.20977788224233165               |-13709.0               |-16414.0           |
|visa                |['2022-01-01', '2023-02-07']|6074.0                    |6461                    |-387.0                       |0.940102151369757            |270.0                          |468                         |-198.0                            |0.5769230769230769                |-585.0                 |-708.0             |
|hotosm-project-14154|['2022-01-01', '2023-02-07']|1460.0                    |1460                    |0.0                          |1.0                          |19.0                           |519                         |-500.0                            |0.036608863198458574              |-500.0                 |-528.0             |
|hotosm-project-     |['2022-01-01', '2023-02-07']|20747261.0                |20859534                |-112273.0                    |0.9946176649967349           |1749869.0                      |7446845                     |-5696976.0                        |0.23498125716326848               |-5809249.0             |-7899290.0         |

### road count

|hashtag             |timerange                   |creationsroadsOSMSTATS|creationsroadsOHSOME|creationroadsAbsDiffrence|creationroadsRelDiffrence|moodificationsroadsOSMSTATS|modificationsroadsOHSOME|modificationsroadsAbsDiffrence|modificationsroadsRelDiffrence|GAP (OSMSTATS - OHSOME)|GAP with DELETIONS |
|--------------------|----------------------------|----------------------|--------------------|-------------------------|-------------------------|---------------------------|------------------------|------------------------------|------------------------------|-----------------------|-------------------|
|missingmaps         |['2017-01-01', '2017-12-31']|816890.0              |825439              |-8549.0                  |0.9896430868907333       |896427.0                   |744845                  |151582.0                      |1.2035081124260751            |143033.0               |54990.0            |
|bloomberg           |['2017-01-01', '2017-12-31']|394.0                 |399                 |-5.0                     |0.9874686716791979       |123.0                      |113                     |10.0                          |1.0884955752212389            |5.0                    |-3.0               |
|hotosm-project-     |['2017-01-01', '2017-12-31']|1458623.0             |1688745             |-230122.0                |0.8637319429517186       |1767305.0                  |1651445                 |115860.0                      |1.070156741520305             |-114262.0              |-275888.0          |
|missingmaps         |['2022-01-01', '2023-02-07']|108869.0              |120244              |-11375.0                 |0.9054006852732777       |177086.0                   |221771                  |-44685.0                      |0.7985083712478187            |-56060.0               |-76615.0           |
|bloomberg           |['2022-01-01', '2023-02-07']|387.0                 |404                 |-17.0                    |0.9579207920792079       |1249.0                     |1488                    |-239.0                        |0.8393817204301075            |-256.0                 |-266.0             |
|visa                |['2022-01-01', '2023-02-07']|0.0                   |0                   |0.0                      |                         |210.0                      |247                     |-37.0                         |0.8502024291497976            |-37.0                  |-39.0              |
|hotosm-project-14154|['2022-01-01', '2023-02-07']|2.0                   |2                   |0.0                      |1.0                      |10.0                       |11                      |-1.0                          |0.9090909090909091            |-1.0                   |-2.0               |
|hotosm-project-     |['2022-01-01', '2023-02-07']|639144.0              |643439              |-4295.0                  |0.9933249305683989       |959000.0                   |1152522                 |-193522.0                     |0.8320882377950269            |-197817.0              |-285669.0          |


### road length in km

|hashtag             |timerange                   |creationsroadsOSMSTATS|creationsroadsOHSOME|creationroadsAbsDiffrence|creationroadsRelDiffrence|moodificationsroadsOSMSTATS|modificationsroadsOHSOME|modificationsroadsAbsDiffrence|modificationsroadsRelDiffrence|GAP (OSMSTATS - OHSOME)|GAP with DELETIONS |
|--------------------|----------------------------|----------------------|--------------------|-------------------------|-------------------------|---------------------------|------------------------|------------------------------|------------------------------|-----------------------|-------------------|
|missingmaps         |['2017-01-01', '2017-12-31']|275551.8863480143     |282821936.0681011   |-282546384.1817531       |0.0009742946045092618    |73497.89872126092          |71246995.13481209       |-71173497.23609082            |0.0010315929616707305         |-353719881.41784394    |-428148070.3287996 |
|bloomberg           |['2017-01-01', '2017-12-31']|187.09946201460002    |188242.50887900242  |-188055.40941698782      |0.0009939277962708352    |4.0651599361               |3771.8589387388         |-3767.7937788027              |0.0010777603304166171         |-191823.20319579053    |-194403.85548472544|
|hotosm-project-14154|['2017-01-01', '2017-12-31']|0.0                   |0.0                 |0.0                      |                         |0.0                        |0.0                     |0.0                           |                              |0.0                    |0.0                |
|missingmaps         |['2022-01-01', '2023-02-07']|41817.6561017575      |46055170.98083393   |-46013353.32473218       |0.0009079904647224111    |22475.6886304773           |15921490.687654737      |-15899014.99902426            |0.0014116573046708861         |-61912368.32375644     |-69692681.98716146 |
|bloomberg           |['2022-01-01', '2023-02-07']|168.1880024334        |176878.80983390403  |-176710.62183147063      |0.0009508657514788513    |47.545764717               |44923.026833021606      |-44875.48106830461            |0.0010583829289537207         |-221586.10289977526    |-224097.29302623385|
|visa                |['2022-01-01', '2023-02-07']|0.0                   |0.0                 |0.0                      |                         |0.3901119625               |322.2086515041          |-321.8185395416               |0.0012107432891045012         |-321.8185395416        |-389.03415312469997|
|hotosm-project-14154|['2022-01-01', '2023-02-07']|1.7549987690000002    |1752.860055534      |-1751.105056765          |0.001001220127904249     |0.6617686260000001         |597.1592089214          |-596.4974402954               |0.0011081946256766245         |-2347.6024970604       |-2410.8139564353   |
|hotosm-project-     |['2022-01-01', '2023-02-07']|238937.37335489513    |244536036.8907879   |-244297099.51743302      |0.0009771049551343094    |101521.44869769977         |70975174.13852933       |-70873652.68983163            |0.0014303797057200625         |-315170752.20726466    |-347683912.61424816|

