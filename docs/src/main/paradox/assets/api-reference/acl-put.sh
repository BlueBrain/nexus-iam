curl -XPUT "https://nexus.example.com/v0/acls/myorg" \
  -d '{"acl":[{"permissions":["read","write"],"identity":{"type":"GroupRef","origin":"https://example.com/realm","group":"students"}}]}'