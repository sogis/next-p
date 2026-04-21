MODEL (
  name iam.user_roles_json,
  kind FULL,
);

SELECT
  user_id,
  json_object('roles', roles) AS roles_json
FROM iam.user_roles
