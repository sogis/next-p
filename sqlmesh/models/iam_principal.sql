MODEL (
  name iam.principal,
  description 'Returns the users and groups normalized as principals. The query returns empty lists if no roles / no groups where assigned.',
  kind FULL,
);

SELECT
  id,
  COALESCE(roles_, CAST([] AS TEXT[])) AS roles,
  COALESCE(groups_, CAST([] AS TEXT[])) AS groups,
  is_user
FROM iam.principal_raw
