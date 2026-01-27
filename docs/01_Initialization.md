# System initialization
1. Change the `spring.cloud.config.server.native.search-locations=${CONFIG_SERVER_CONFIGURATIONS_PATH}
` with the absolute path of the config folder
2. For using `SonarQube`, replace the `${SONAR_QUBE_TOKEN}` with your token and run `run-sonar.sh` for full analysis