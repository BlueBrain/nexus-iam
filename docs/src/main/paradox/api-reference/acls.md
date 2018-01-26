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
| ---       | --- | --- | --- |
| GET       | /v0/acls/{address}?self=[true\|**false**]&parents=[true\|**false**] | *N/A* | [Full ACL](../assets/api-reference/full-acls-self.json) |
| PATCH     | /v0/acls/{address} | [Patch permissions](../assets/api-reference/acl-patch.json) | [Identity permissions](../assets/api-reference/acl-patch-response.json) |
| PUT       | /v0/acls/{address} | [ACL](../assets/api-reference/acls.json) | *N/A* |
| DELETE    | /v0/acls/{address} | *N/A* | *N/A* |

## Actions

All `GET` operations specify an `{address}` which can be a regular path (`a/b/c`) or can be a filter path, expressed as (`a/*/c/*`). 
The character `*` is reserved to indicate that this segment of the path can be matched by any word. As an example, `a/*/c/*` will be matched by `a/b/c/d` and also by `a/something/c/else`. The response for this example will only contain paths with length 4.

### Fetch own permissions on a resource

```
GET /v0/acls/{address}?self=true
```
This call will return the ACLs for the caller's identities on the provided `{address}`. 

Request
:   @@snip [acl-get-self.sh](../assets/api-reference/acl-get-self.sh)

Response
:   @@snip [full-acls-self.json](../assets/api-reference/full-acls-self.json)


#### Status Codes

- **200 OK**: the ACLs for the provided `{address}` with the associated caller are returned successfully


### Fetch own permissions on a resource and its parents

This call will return the ACLs for the caller's identities on the provided `{address}` and its parents. For example, being `/a/b/c` the address, it will return
the self ACLs for `/`, `/a`, `/a/b` and `/a/b/c`.

```
GET /v0/acls/{address}?self=true&parents=true
```

Request
:   @@snip [acl-get-self-parents.sh](../assets/api-reference/acl-get-self-parents.sh)

Response
:   @@snip [full-acls-self-parents.json](../assets/api-reference/full-acls-self-parents.json)


#### Status Codes

- **200 OK**: the ACLs for the provided `{address}` (including parents) with the associated caller are returned successfully


### Fetch all permissions on a resource and its parents

This call will return all the ACLs the caller has access to on the provided `{address}` and its parents. For example, being `/a/b/c` the address, it will return
all the ACLs for `/`, `/a`, `/a/b` and `/a/b/c`.

```
GET /v0/acls/{address}?parents=true
```

Request
:   @@snip [acl-get-parents.sh](../assets/api-reference/acl-get-parents.sh)

Response
:   @@snip [full-acls-parents.json](../assets/api-reference/full-acls-parents.json)


#### Status Codes

- **200 OK**: the ACLs for the provided `{address}` (including parents) are returned successfully


### Fetch all permissions on a resource

This call will return all the ACLs the caller has access to on the provided `{address}`. 

```
GET /v0/acls/{address}
```

Request
:   @@snip [acl-get.sh](../assets/api-reference/acl-get.sh)

Response
:   @@snip [full-acls.json](../assets/api-reference/full-acls.json)


#### Status Codes

- **200 OK**: the ACLs for the provided `{address}` (including parents) are returned successfully


### Append new ACL
Appends certain `permissions` to some `identities` on the provided `address`
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

- **200 OK**: the resource ACL was added successfully
- **400 Bad Request**: the request payload is not valid
- **403 Forbidden**: the caller doesn't have `own` rights on this resource
- **409 Conflict**: the resource ACL already exists

### Subtract ACL
Subtract certain `permissions` from an `identity` on the provided `address`

```
PATCH /v0/acls/{address}
{...}
```

#### Example
The initial conditions of this example are as follows: the permissions `own` and `read` for the identity group `students` from the realm `realm` are set on the path `myorg`

Request
:   @@snip [acl-patch.sh](../assets/api-reference/acl-patch.sh)

Payload
:   @@snip [acl-patch.json](../assets/api-reference/acl-patch.json)

Response
:   @@snip [acl-patch-response.json](../assets/api-reference/acl-patch-response.json)

#### Status Codes

- **200 OK**: the resource ACL was subtracted successfully
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

### Permissions

The `permissions` object in the payload (in both request and response body) is a simple
JSON array of arbitrary string literals. Internally, the IAM services
recognizes `read`, `write` and `own`.

Third party services can store and make use of custom permissions, for instance we could imagine adding
a `publish` permission to allow users of [Nexus KnowledgeGraph](https://bbp-nexus.epfl.ch/dev/docs/kg/index.html)
to publish new instances of existing schemas hosted by the platform, without giving them explicit
`write` rights. This has no incidence on the IAM service internals, but is fully supported.

### Identity

The `identity` object's request payload is qualified by the mandatory `@type` field, while the response is qualified by `@id` (unique identifier of the identity)  and `@type`. Additionally, `UserRef` and `GroupRef` have a `realm` field which matches their provider. Groups
have a `group` identifier, and users a `sub`. Those are the available `@type`s (this is the response's payload since it contains `@id`):

UserRef
:   @@snip [userref.json](../assets/api-reference/userref.json)

GroupRef
:   @@snip [groupref.json](../assets/api-reference/groupref.json)

AuthenticatedRef
:   @@snip [authref.json](../assets/api-reference/authref.json)

Anonymous
:   @@snip [anonref.json](../assets/api-reference/anonref.json)


## Error Signaling

The services makes use of the HTTP Status Codes to report the outcome of each API call.  The status codes are
complemented by a consistent response data model for reporting client and system level failures.

Format
:   @@snip [error.json](../assets/api-reference/error.json)


While the format only specifies `code` and `message` fields, additional fields may be presented for additional
information in certain scenarios.