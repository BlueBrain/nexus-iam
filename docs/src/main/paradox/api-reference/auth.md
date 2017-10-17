# Authentication

The IAM service delegates authentication to third party OpenID Connect providers.

It follows the OAuth 2.0 protocol and uses [Json Web Tokens](https://jwt.io) as access tokens.

See [OpenID Connect explained](https://connect2id.com/learn/openid-connect).

### Authorization

```
GET /v0/oauth2/authorize?redirect={callback}
```
... where `{callback}` is an optional callback URI.

Forwards authorization request to the OIDC provider endpoint (typically, a login prompt).
Upon success, the client is redirected to the *token* endpoint, or to the callback URI if provided.

#### Status Codes

- **200 OK**: the authorization is successful
- **401 Unauthorized**: the authorization attempt was rejected

### Token

```
GET /v0/oauth2/authorize?state={state}&code={code}
```
... where `{state}` is the client internal state and `{code}` the code received from the provider.

Gets the OAuth 2.0 access token from the provider.

#### Status Codes

- **200 OK**: the request was successfully verified and the access token is returned
- **401 Unauthorized**: the request was rejected

### User info

```
GET /v0/oauth2/userinfo

Authorization: Bearer {access_token}
```
... where `{access_token}` is the access token that was received from the provider, which must be passed in an `Authorization` header.

Gets the *user info* associated to these credentials from the provider.

#### Status Codes

- **200 OK**: the access token is valid and the corresponding *user info* is returned
- **401 Unauthorized**: the access token is invalid or missing