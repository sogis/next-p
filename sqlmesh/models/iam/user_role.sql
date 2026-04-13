MODEL (
  name iam_user_role,
  kind FULL,
  audits (
    not_null(columns=(user_id, role_id)),
    unique_values(columns=(user_id, role_id))
  )
);

WITH RECURSIVE principal_group AS (
  SELECT
    id,
    grp
  FROM iam_principal
  CROSS JOIN UNNEST(groups) AS group_ref(grp)
), principal_direct_group AS (
  SELECT
    id,
    grp
  FROM principal_group

  UNION

  SELECT
    principal_direct_group.id,
    principal_group.grp
  FROM principal_direct_group
  JOIN principal_group
    ON principal_group.id = principal_direct_group.grp
), user_group_and_self_ref AS (
  SELECT
    id,
    id AS ref_id
  FROM iam_principal
  WHERE is_user

  UNION

  SELECT
    principal_direct_group.id,
    principal_direct_group.grp AS ref_id
  FROM principal_direct_group
  JOIN iam_principal AS principal
    ON principal.id = principal_direct_group.id
  WHERE principal.is_user
), principal_role AS (
  SELECT
    id,
    role
  FROM iam_principal
  CROSS JOIN UNNEST(roles) AS role_ref(role)
)
SELECT DISTINCT
  user_group_and_self_ref.id AS user_id,
  principal_role.role AS role_id
FROM user_group_and_self_ref
JOIN principal_role
  ON principal_role.id = user_group_and_self_ref.ref_id
