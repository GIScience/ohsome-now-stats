

## setup

### import conda env

```shell
  conda env create -f env.yml 
  conda activate ohsomeNOW-dashboard
```

### configer database connection
please configure the .env file with valid connection to a running clickhouse db

```yaml 
hostOHSOME="host"
userOHSOME=user
passwordOHSOME=password
portOHSOME=port
```

### run the application

To run the application, simply execute the app.py.
The dashboard will be available under http://127.0.0.1:8050
as long the app is running.

