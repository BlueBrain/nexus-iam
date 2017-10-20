# Service Internals

Connection to third party OIDC providers is handled by an internal relay service, so that the IAM
service can expose unified endpoints to clients.

## Configuration

Several environment variables must be defined to setup connectivity to the OIDC provider.

| Name | Description |
| --- |---|
| OIDC_DISCOVERY_URI | The OIDC server discovery URI used for discovering its endpoints |
| OIDC_CLIENT_ID | The platform client id used for authenticating to the server |
| OIDC_CLIENT_SECRET | The platform client secret used for authenticating to the server |
| TOKEN_URI | The provider *token* endpoint to  |
| OIDC_ISSUER | The issuer origin aka the *realm* |
| AUTHORIZE_ENDPOINT | The exposed OAuth 2.0 *authorize* endpoint |
| TOKEN_ENDPOINT | The exposed *token* endpoint to get the forwarded access token |
| USERINFO_ENDPOINT | The exposed *userinfo* endpoint to get the forwarded object associated to valid credentials |
