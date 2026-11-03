# `user_role` transformer

The `user_role` transformer generates the `user_role` mapping table.  It
contains one distinct record for every direct or indirect association of a user
to a role.

## Inputs

The caller supplies these DuckDB relations:

- `input.users`: user principals with `id`, optional `groups_`, and optional
  `roles_` string-array columns.
- `input.groups`: group principals with the same columns. A group's `groups_`
  values identify its parent groups.

## Output

`user_role` has two columns:

- `user_id`: the user principal identifier.
- `role_id`: a role assigned directly to that user or to any reachable group.

The traversal starts with the user, follows group membership through any number
of levels, and includes roles on every reached principal. It does not revisit a
principal already on the same path, so cyclic group definitions terminate.
Different paths that reach the same user-role association produce one output
record.

Run it with:

```bash
./gradlew -p transformers user_role -PduckdbUrl=jdbc:duckdb:/path/to/netl.duckdb
```
