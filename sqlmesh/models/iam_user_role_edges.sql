MODEL (
  name iam.user_role_edges,
  kind FULL,
);

WITH RECURSIVE principal_group AS (
  SELECT
    id,
    grp,
    CAST([grp] AS TEXT[]) AS path
  FROM iam.principal
  CROSS JOIN UNNEST(groups) AS group_ref(grp)
), principal_direct_group AS (
  SELECT
    id,
    grp,
    path
  FROM principal_group

  UNION ALL

  SELECT
    principal_direct_group.id,
    principal_group.grp,
    list_append(principal_direct_group.path, principal_group.grp) AS path
  FROM principal_direct_group
  JOIN principal_group
    ON principal_group.id = principal_direct_group.grp
  WHERE array_length(principal_direct_group.path) < 11
), user_principal_ref AS (
  SELECT
    id AS user_id,
    id AS ref_id,
    FALSE AS cycle_overflow,
    CAST([] AS TEXT[]) AS group_chain
  FROM iam.principal
  WHERE is_user

  UNION

  SELECT
    principal_direct_group.id AS user_id,
    principal_direct_group.grp AS ref_id,
    array_length(principal_direct_group.path) > 10 AS cycle_overflow,
    CAST(principal_direct_group.path AS TEXT[]) AS group_chain
  FROM principal_direct_group
  JOIN iam.principal AS principal
    ON principal.id = principal_direct_group.id
  WHERE principal.is_user
), principal_role AS (
  SELECT
    id,
    role
  FROM iam.principal
  CROSS JOIN UNNEST(roles) AS role_ref(role)
)
SELECT
  user_principal_ref.user_id,
  principal_role.role AS role_id,
  user_principal_ref.cycle_overflow,
  user_principal_ref.group_chain
FROM user_principal_ref
JOIN principal_role
  ON principal_role.id = user_principal_ref.ref_id
