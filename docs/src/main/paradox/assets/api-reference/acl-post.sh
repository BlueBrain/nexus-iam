curl -XPOST "https://nexus.example.com/v0/acls/myorg/mydom/myschema" \
  -d '{"permissions":["read"],"identity":{"type":"Anonymous"}}'