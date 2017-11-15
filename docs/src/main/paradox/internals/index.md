# Service Internals

Connection to third party OIDC providers is handled by an integration layer, so that the IAM
service can expose unified endpoints to clients.

## Configuration

Several environment variables must be defined to setup connectivity to the OIDC provider.


### Environment variables on Integration Layer

| Name | Description |
| --- |---|
| OIDC_DISCOVERY_URI | The OIDC server discovery URI used for discovering its endpoints |
| OIDC_CLIENT_ID | The platform client id used for authenticating to the server |
| OIDC_CLIENT_SECRET | The platform client secret used for authenticating to the server |
| REALM | The OIDC realm. It has to match the realm on the IAM Environment variables for this OIDC Provider  |
| TOKEN_URI | The token endpoint of IAM (`http(s)://nexus.example.com/v0/oauth2/token`) |

### Environment variables on IAM

**Note:** `N` is a value from `1` to `4`

| Name | Description |
| --- |---|
| DEFAULT_REALM | The default realm used when requesting the [authorization endpoint](../api-reference/auth.html#authorization)  |
| OIDC_`N`_REALM | The realm for the OIDC Provider N |
| OIDC_`N`_ISSUER | The issuer origin of the OIDC Provider N. This value has to match the `issuer` field on the issued JWT |
| OIDC_`N`_CERT | The endpoint where to retrieve the [Json Web Keys](https://tools.ietf.org/html/rfc7517) in order to validate the JWT signature for the OIDC Provider N |
| OIDC_`N`_AUTHORIZE | The exposed OAuth 2.0 *authorize* endpoint for the OIDC Provider N |
| OIDC_`N`_TOKEN | The provider *token* endpoint to get the forwarded access token for the OIDC Provider N  |
| OIDC_`N`_USERINFO | The exposed *userinfo* endpoint to get the forwarded object associated to valid credentials for the OIDC Provider N |



## Adding an Integration Layer

In order to add a new integration layer, you should perform the following steps:
 
 1. Copy the content on `modules/oidc/bbp` into `modules/oidc/your-oidc`.
 2. Add the following lines in `build.sbt`
 ```
 lazy val yourOidc = project
      .in(file("modules/oidc/your-oidc"))
      .dependsOn(oidcCore)
      .enablePlugins(ServicePackagingPlugin)
      .settings(
        common,
        monitoringSettings,
        name := "iam-your-oidc",
        moduleName := "iam-your-oidc",
        packageName in Docker := "iam-your-oidc",
        description := "Description for your OIDC",
        libraryDependencies ++= Seq(akkaHttp,
                                    circeCore,
                                    circeParser,
                                    circeGenericExtras,
                                    commonsService,
                                    journal,
                                    scalaTest % Test)
      )
```
3. Modify the following line in `buid.sbt` `.aggregate(docs, core, service, oidcCore, oidcBbp, oidcHbp)` to `.aggregate(docs, core, service, oidcCore, oidcBbp, oidcHbp, yourOidc)`
4. Modify `modules/your-oidc/bbp/src/main/scala/ch/epfl/bluebrain/nexus/iam/your-oidc/Main.scala` providing your implementation of `Decoder[UserInfo]`. You can see examples of `Decoder[UserInfo]` in `modules/core/src/main/scala/ch/epfl/bluebrain/nexus/iam/core/acls/UserInfoDecoder.scala`