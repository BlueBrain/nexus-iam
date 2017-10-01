# Access control lists

Access to any resource in the system is protected by an ACL.  Assuming a nexus deployment at
`http(s)://nexus.example.com` and resource address of `/v0/{address}` the following operations should apply
to all resources for authenticated users:

### Fetch own permissions on a resource

```
GET /v0/acls/{address}
```

#### Status Codes

- **200 OK**: the resource is found and the associated caller permissions are returned successfully
- **404 Not Found**: the resource was not found

### Fetch all permissions on a resource

```
GET /v0/acls/{address}?all=true
```

#### Status Codes

- **200 OK**: the resource is found and its ACL is returned successfully
- **404 Not Found**: the resource was not found

### Create a new ACL


```
PUT /v0/acls/{address}
{...}
```

#### Status Codes

- **201 Created**: the resource ACL was created successfully
- **409 Conflict**: the resource ACL already exists

### Add permissions to a resource ACL

```
POST /v0/acls/{address}
{...}
```

#### Status Codes

- **200 OK**: the resource ACL was updated successfully
- **400 Bad Request**: the request payload is not valid

### Clear ACL on a resource

```
DELETE /v0/acls/{address}
```

#### Status Codes

- **200 OK**: the resource was created successfully
- **404 Not Found**: the resource was not found
- **409 Conflict**: the resource ACL is already empty

## Error Signaling

The services makes use of the HTTP Status Codes to report the outcome of each API call.  The status codes are
complemented by a consistent response data model for reporting client and system level failures.

Format
:   @@snip [error.json](../assets/api-reference/error.json)

Example
:   @@snip [error-example.json](../assets/api-reference/error-example.json)

While the format only specifies `code` and `message` fields, additional fields may be presented for additional
information in certain scenarios.