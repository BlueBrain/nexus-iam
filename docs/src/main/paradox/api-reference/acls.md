# Access control lists

Access to any resource in the system is protected by ACLs.  Assuming a nexus deployment at
`http(s)://nexus.example.com` and resource address of `/v0/{address}` the following operations should apply
to all resources for authenticated users:

## Authentication and authorization

Access to the ACL on a particular resource is protected by the ACL itself.

This implies that the caller (i.e. the client sending an HTTP request to perform any of the following actions)
must have the `own` permission for administrative tasks (*create*, *add*, *subtract*, *clear*, *fetch all*) and
the `read` permission to fetch its own permissions.

See @ref:[Authentication](auth.md) to learn about the authentication mechanism.

## Bootstrapping

By default, the IAM service gives `read` and `own` rights to the LDAP groups defined as administrators in the
system internal configuration settings.

This is done by setting permissions to the top-level, root resource path `/` for these groups.  The root ACL
can be subsequently modified by users belonging to administrator groups.

**Note**: Be careful when modifying top-level permissions, as it can results in locking everyone out, or
on the contrary, giving full ownership to everyone, including anonymous users.  No checks about the sanity of
such operations are performed by the service.

## Actions

### Fetch own permissions on a resource

```
GET /v0/acls/{address}
```

**Note**: Permissions are transitively inherited from the resource ACL and all its ancestors.
This call computes the effective ACL for the caller on this resource.

#### Status Codes

- **200 OK**: the ACL is computed and the associated caller permissions are returned successfully
- **403 Forbidden**: the caller doesn't have `read` rights on this resource

## Administrative actions

### Fetch permissions for all users on a resource

```
GET /v0/acls/{address}?all=true
```

**Note**: This action *does not* show inherited permissions.

#### Status Codes

- **200 OK**: the entire ACL is fetched is returned successfully
- **403 Forbidden**: the caller doesn't have `own` rights on this resource

### Create a new ACL

```
PUT /v0/acls/{address}
{...}
```

Payload
:   @@snip [acls.json](../assets/api-reference/acls.json)

#### Status Codes

- **201 Created**: the resource ACL was created successfully
- **400 Bad Request**: the request payload is not valid
- **403 Forbidden**: the caller doesn't have `own` rights on this resource
- **409 Conflict**: the resource ACL already exists

### Add permissions to a resource ACL

```
POST /v0/acls/{address}
{...}
```

Payload
:   @@snip [acl.json](../assets/api-reference/acl.json)

#### Status Codes

- **200 OK**: the resource ACL was updated successfully
- **400 Bad Request**: the request payload is not valid
- **403 Forbidden**: the caller doesn't have `own` rights on this resource

### Clear ACL on a resource

```
DELETE /v0/acls/{address}
```

#### Status Codes

- **200 OK**: the resource ACL was cleared successfully
- **403 Forbidden**: the caller doesn't have `own` rights on this resource
- **404 Not Found**: the resource ACL was not found, i.e. it is already empty

## Permission format

The `permissions` object in the payload (in both request and response body) is a simple
JSON array of arbitrary string literals. Internally, the IAM services
recognizes `read`, `write` and `own`. Third party services can store and
make use of custom permissions.

## Identity format

The `identity` object in the payload (in both request and response body) is qualified by
a mandatory `type` field that needs to match one of the literals described below. Additionally,
any authenticated identity has an `origin` which is the URI of the provider realm. Groups
have a `group` identifier, and users a `name`.

| Type | Additional fields | Description |
| --- | --- | --- |
| Anonymous | *None* | Represents unauthenticated users |
| AuthenticatedRef | `origin` | Represents any user authenticated by a provider of a specific *origin* |
| GroupRef | `origin`, `group` | Represents users belonging to a specific *group* within the *origin* realm |
| UserRef | `origin`, `name` | Represents a single user identified by *name* within the *origin* realm |

## Error Signaling

The services makes use of the HTTP Status Codes to report the outcome of each API call.  The status codes are
complemented by a consistent response data model for reporting client and system level failures.

Format
:   @@snip [error.json](../assets/api-reference/error.json)

Example
:   @@snip [error-example.json](../assets/api-reference/error-example.json)

While the format only specifies `code` and `message` fields, additional fields may be presented for additional
information in certain scenarios.