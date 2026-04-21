MODEL (
  name iam.principal_raw,
  description 'Returns the users and groups normalized as principals.',
  kind FULL,
);

SELECT
  id,
  roles_,
  groups_,
  TRUE AS is_user
FROM 
  read_json_auto('data/users.json')

UNION ALL

SELECT
  id,
  roles_,
  groups_,
  FALSE AS is_user
FROM 
  read_json_auto('data/groups.json')
