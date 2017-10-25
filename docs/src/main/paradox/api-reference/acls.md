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

## Overview

Here is a summary of ACL actions with relevant samples for the corresponding request payload and response body
when present. See [Actions](#actions) and [Data formats](#data-formats) for detailed usage.

| HTTP Verb | Request path | Request payload | Response body |
| --- | --- | --- | --- |
| GET | /v0/acls/{address} | *N/A* | [Own ACL](../assets/api-reference/own-acls.json) |
| GET | /v0/acls/{address}?all=true | *N/A* | [Full ACL](../assets/api-reference/acls.json) |
| PUT | /v0/acls/{address} | [Full ACL](../assets/api-reference/acls.json) | *N/A* |
| POST | /v0/acls/{address} | [Additional permissions](../assets/api-reference/acl.json) | [Resulting permissions](../assets/api-reference/acl.json) |
| DELETE | /v0/acls/{address} | *N/A* | *N/A* |

## Actions

### Fetch own permissions on a resource

```
GET /v0/acls/{address}
```

Request
:   @@snip [acl-get.sh](../assets/api-reference/acl-get.sh)

Response
:   @@snip [own-acls.json](../assets/api-reference/own-acls.json)

**Note**: Permissions are transitively inherited from the resource ACL and all its ancestors.
This call computes the effective ACL for the caller on this resource.

#### Status Codes

- **200 OK**: the ACL is computed and the associated caller permissions are returned successfully
- **403 Forbidden**: the caller doesn't have any access rights (either `read`, `write` or `own`)
                     on this resource ACL

## Administrative actions

### Fetch permissions for all users on a resource

```
GET /v0/acls/{address}?all=true
```

#### Example

Request
:   @@snip [acl-get-all.sh](../assets/api-reference/acl-get-all.sh)

Response
:   @@snip [acls.json](../assets/api-reference/acls.json)

**Note**: This action *does not* show inherited permissions.

#### Status Codes

- **200 OK**: the entire ACL is fetched is returned successfully
- **403 Forbidden**: the caller doesn't have `own` rights on this resource

### Create a new ACL

```
PUT /v0/acls/{address}
{...}
```

#### Example

Request
:   @@snip [acl-put.sh](../assets/api-reference/acl-put.sh)

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

#### Example

Request
:   @@snip [acl-post.sh](../assets/api-reference/acl-post.sh)

Payload
:   @@snip [acl.json](../assets/api-reference/acl.json)

Response
:   @@snip [acl.json](../assets/api-reference/acl.json)

**Note**: Request and response body follow the same format.  The response shows the resulting permissions.

#### Status Codes

- **200 OK**: the resource ACL was updated successfully
- **400 Bad Request**: the request payload is not valid
- **403 Forbidden**: the caller doesn't have `own` rights on this resource

### Clear ACL on a resource

```
DELETE /v0/acls/{address}
```

#### Example

Request
:   @@snip [acl-del.sh](../assets/api-reference/acl-del.sh)

#### Status Codes

- **200 OK**: the resource ACL was cleared successfully
- **403 Forbidden**: the caller doesn't have `own` rights on this resource
- **404 Not Found**: the resource ACL was not found, i.e. it is already empty

## Data formats

### Full ACL

The `acl` object in the payload is an array of `identity` and `permissions` pairs.

### ACL patch

To add additional permissions to an existing ACL, the payload consists in a pair of `identity`
and `permissions`.

### Permissions

The `permissions` object in the payload (in both request and response body) is a simple
JSON array of arbitrary string literals. Internally, the IAM services
recognizes `read`, `write` and `own`.

Third party services can store and make use of custom permissions, for instance we could imagine adding
a `publish` permission to allow users of [Nexus KnowledgeGraph](https://bbp-nexus.epfl.ch/dev/docs/kg/index.html)
to publish new instances of existing schemas hosted by the platform, without giving them explicit
`write` rights. This has no incidence on the IAM service internals, but is fully supported.

### Identity

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