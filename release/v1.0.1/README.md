# v1.0.1 Release

1. Go to keycloak, go to real settings, Action dropdown, Partial import and upload the json from `keycloak` folder and select skip or overwrite for duplicates and click import. This creates the `notification-service` client and its service account user with the `view-users` role, which is used for getting emails for recepients from user-service.