import os

import clickhouse_connect
import dash_bootstrap_components as dbc
import geopandas as gpd
import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
from dash import Dash, dcc, html, Input, Output, dash_table, State
from dotenv import load_dotenv
from plotly.subplots import make_subplots

external_stylesheets = [dbc.themes.SANDSTONE]

app = Dash(__name__, external_stylesheets=external_stylesheets)

load_dotenv("dev.env")
clicks = 0
try:
    con = clickhouse_connect.get_client(
        user=os.getenv("userOHSOME"),
        host=os.getenv("hostOHSOME"),
        port=os.getenv("portOHSOME"))
except:
    pass

df = pd.read_feather("data/df_missingmaps.feather")
df_user = pd.read_feather("data/df_missingmaps_user.feather")
df_boundaries = gpd.read_file("data/admin_bound.geojson")

df["Year"] = df.date.dt.year
df["month"] = df.date.dt.day
df.date = pd.to_datetime(df.date)
df = df.set_index(pd.DatetimeIndex(df["date"]))

df_map = pd.read_feather("data/df_missingmaps_2017-01-01 00:00:00_2017-12-01 00:00:00_map.feather")

cols = ["changesets", "users", "building_edits", "road_edits"]

header = html.Div([
    html.Div([
        html.H1(children='OhsomeNOW Dashboard',
                style={'textAlign': 'center'}
                )],
        className='row',

        style={'padding-top': '1%'}
    ),
    html.Br(),
    html.Div([
        dbc.Row([
            dbc.Col([dcc.Input(id="inputHashtag", type="text", placeholder="e.g #MissingMaps", debounce=True,
                               style={"height": '43 px'})]),
            dbc.Col([
                dcc.DatePickerRange(
                    start_date=df['date'].min().to_pydatetime(),
                    end_date=df['date'].max().to_pydatetime(),
                    display_format='D MMM YYYY HH',
                    id='timepicker', style={'height': '43 px'})]),
            dbc.Col([
                dcc.Dropdown(['auto', 'monthly', 'daily', 'hourly', "minutely"], 'monthly', id='interval',
                             style={"border": "None", 'height': '43 px'}),
            ], style={"height": '43 px', "justify": "center"}),
            html.Button('Apply', id='apply-button', n_clicks=0),
        ], style={'display': 'flex', "border": "True", "justify": "center"}),
    ])])

tab_1 = html.Div([
    dcc.Graph(id="temporalEvolution", ),
])
tab_2 = html.Div([
    dcc.Graph(id="temporalEvolution2", ),
    dcc.Dropdown(
        cols, ["changesets"],
        id="temporalValues",
        multi=True, style={'display': 'flex'}),
    dcc.Checklist(
        ["normalize"],
        [],
        style={'display': 'flex'},
        id="normalize"
    )
])

app.layout = html.Div([
    header,
    html.Br(),
    dbc.Card(
        dbc.CardBody([
            dbc.Row([
                dbc.Col([
                    dash_table.DataTable([{col: df[col].sum() for col in cols}], [{"name": i, "id": i} for i in cols],
                                         id="baseTable")
                ], width=12)
            ], align='center'),
            html.Br(),
            dbc.Row([
                dbc.Col([
                    dcc.Tabs(id="tabs-example-graph", value='tab-1-example-graph', children=[
                        dcc.Tab(label='Overview', value='tab-1-example-graph', children=[tab_1]),
                        dcc.Tab(label='compare', value='tab-2-example-graph', children=[tab_2]),
                    ])], width=12)
            ], align='center'),
            dbc.Row([
                dbc.Col(
                    [
                        dcc.Graph(id="userSurvival")
                    ], width=6
                ),
                dbc.Col(
                    [
                        dcc.Graph(id="map"),
                        dcc.Dropdown(cols, 'changesets', id='mapKey', style={"border": "None"})
                    ], width=6
                )
            ], align='center')
        ])
    ),
])


def getSelectInterval(start_date: str, end_date: str, interval: str):
    select = """
        toYear(
            fromUnixTimestamp((changeset_timestamp / 1000)::integer)
        ) as year,
        toMonth(
            fromUnixTimestamp((changeset_timestamp / 1000)::integer)
        ) as month,
        toDayOfMonth(
            fromUnixTimestamp((changeset_timestamp / 1000)::integer)
        ) as day,
        toHour(
            fromUnixTimestamp((changeset_timestamp / 1000)::integer)
        ) as hour,
        toMinute(
            fromUnixTimestamp((changeset_timestamp / 1000)::integer)
        ) as minute
        """
    group = "GROUP BY year,month,day,hour,minute"
    if interval == "yearly":
        fun = lambda x: str(int(x.year)) + "-1" + "-1"
        n_1 = 1
        n_2 = 1
    if interval == "monthly" or interval == "auto":
        fun = lambda x: str(int(x.year)) + "-" + str(int(x.month)) + "-1"
        n_1 = 2
        n_2 = 2

    if interval == "daily":
        fun = lambda x: str(int(x.year)) + "-" + str(int(x.month)) + "-" + str(int(x.day))
        n_1 = 3
        n_2 = 3

    if interval == "hourly":
        n_1 = 4
        n_2 = 4
        fun = lambda x: str(int(x.year)) + "-" + str(int(x.month)) + "-" + str(int(x.day)) + " " + str(
            int(x.hour)) + ":00"

    if interval == "minutely":
        n_1 = 5
        n_2 = 5
        fun = lambda x: str(int(x.year)) + "-" + str(int(x.month)) + "-" + str(int(x.day)) + " " + str(
            int(x.hour)) + ":" + str(int(x.minute))
    return ",".join(select.split(",")[:n_1]) + ",", ",".join(group.split(",")[:n_2]), fun


@app.callback(
    Output("baseTable", "data"),
    Output("temporalEvolution", "figure"),
    Output("userSurvival", "figure"),
    Output("map", "figure"),
    Input("apply-button", "n_clicks"),
    Input("mapKey", "value"),
    State("timepicker", "start_date"),
    State("timepicker", "end_date"),
    State("interval", "value"),
    State("inputHashtag", "value"),
)
def updateDFs(n_clicks: int, key_map: str, start_date: str, end_date: str, interval: str, hashtag: str):
    global df
    global df_user
    global cols
    global clicks
    global df_map
    global df_boundaries
    try:
        global con
    except:
        print("error")
        pass
    print(start_date, end_date)
    print(hashtag)
    print(n_clicks, clicks)
    print(df.changesets.sum())
    if clicks != n_clicks:
        clicks += 1
        hashtag = hashtag.replace("#", "")
        hashtag = hashtag.replace("*", "%")
        hashtag = hashtag.lower()
        start_date = start_date.replace("T", " ")
        end_date = end_date.replace("T", " ")
        select, groupby, fun = getSelectInterval(start_date, end_date, interval)

        try:
            df = pd.read_feather(f"data/df_{hashtag}_{start_date}_{end_date}_{interval}.feather")
            df.date = pd.to_datetime(df.date)
            df = df.set_index(pd.DatetimeIndex(df["date"]))
            df = df.sort_index()
        except:
            sql = f"""
                SELECT
                {select}
                count(DISTINCT user_id) as users,
                count(DISTINCT changeset_id) as changesets,
                SUM(CASE WHEN (building_area > 0.0 ) THEN 1 ELSE 0 END) as building_edits,
                SUM(CASE WHEN (road_length > 0.0) THEN 1 ELSE 0 END) as road_edits
                FROM __stats_all_unnested
                WHERE lower(hashtag) LIKE lower('#{hashtag}')
                AND (FROM_UNIXTIME((changeset_timestamp/1000)::integer) BETWEEN  '{start_date}' and '{end_date}')
                {groupby}
            """
            df = con.query_df(sql)
            df["date"] = df.apply(
                fun, axis=1
            )
            df.date = pd.to_datetime(df.date)
            df.to_feather(f"data/df_{hashtag}_{start_date}_{end_date}_{interval}.feather")
            df = df.set_index(pd.DatetimeIndex(df["date"])).sort_index().drop(["year", "month"], axis=1)
        try:
            df_user = pd.read_feather(f"data/df_{hashtag}_{start_date}_{end_date}_{interval}_user.feather")
        except:
            sql = f"""
            SELECT user_id,
            count(DISTINCT changeset_id) as changesets,
            min(FROM_UNIXTIME((changeset_timestamp/1000)::integer)) as oldest,
            max(FROM_UNIXTIME((changeset_timestamp/1000)::integer)) as latest
            FROM __stats_all_unnested
            WHERE lower(hashtag) ILIKE lower('#{hashtag}')
            GROUP BY user_id
            """
            df_user = con.query_df(sql)
            df_user["delta"] = df_user.latest - df_user.oldest
            df_user.delta = df_user.delta.dt.days
            df_user.to_feather(f"data/df_{hashtag}_{start_date}_{end_date}_{interval}_user.feather")
        try:
            df_map = pd.read_feather(f"data/df_{hashtag}_{start_date}_{end_date}_map.feather")
        except:
            sql = f"""
                SELECT
                country_iso_a3[1] as a3,
                count(DISTINCT user_id) as users,
                count(DISTINCT changeset_id) as changesets,
                SUM(CASE WHEN (building_area > 0.0 ) THEN 1 ELSE 0 END) as building_edits,
                SUM(CASE WHEN (road_length > 0.0) THEN 1 ELSE 0 END) as road_edits
                FROM __stats_all_unnested
                WHERE lower(hashtag) LIKE lower('#{hashtag}')
                AND (FROM_UNIXTIME((changeset_timestamp/1000)::integer) BETWEEN '{start_date}' and '{end_date}')
                GROUP BY a3
            """
            df_map = con.query_df(sql)
            df_map.to_feather(f"data/df_{hashtag}_{start_date}_{end_date}_map.feather")

    # fig_table = dash_table.DataTable(pd.DataFrame([{col: df[col].sum() for col in cols}], [{"name": i, "id": i} for i in cols])
    print(df.changesets.sum())
    fig_table = [{col: df[col].sum() for col in cols}]
    fig_timeseries = make_subplots(rows=2, cols=2, subplot_titles=(cols))
    for i, col in zip([(0, 0), (0, 1), (1, 0), (1, 1)], cols):
        fig_timeseries.add_trace(
            go.Scatter(x=df.date, y=df[col], name=col),
            row=i[0] + 1, col=i[1] + 1)
        fig_timeseries.update_yaxes(title_text="number of " + col, row=i[0] + 1, col=i[1] + 1)
        fig_timeseries.update_xaxes(title_text="date", row=i[0] + 1, col=i[1] + 1)
    fig_user = px.histogram(df_user, x="delta", nbins=21, title="User survival rate in days")
    fig_user.update_xaxes(title_text="number of days")
    fig_user.update_yaxes(title_text="number of users")
    gdf_merged = df_boundaries.merge(df_map, right_on="a3", left_on="SOV_A3").set_index("SOV_A3")
    gdf_merged[key_map] = gdf_merged[key_map].astype("double")
    fig_map = px.choropleth(gdf_merged,
                            geojson=gdf_merged.geometry,
                            locations=gdf_merged.index,
                            color=key_map,
                            projection="mollweide", title="Map")

    return fig_table, fig_timeseries, fig_user, fig_map


@app.callback(
    Output("temporalEvolution2", "figure"),
    Input("temporalValues", "value"),
    Input("normalize", "value"), )
def displayTemporal(values: [str], normalize: [str]):
    df_cp = df.copy()
    if "normalize" in normalize:
        for col in cols:
            df_cp[col] = df_cp[col] / df_cp[col].max()
    fig = px.line(df_cp, x='date', y=values)
    print(df.changesets.sum())
    return fig


app.run_server(debug=True)
