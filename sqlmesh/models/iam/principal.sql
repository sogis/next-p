MODEL (
  name iam_principal,
  kind FULL,
  audits (
    not_null(columns=(id, is_user)),
    unique_values(columns=(id, is_user))
  )
);

WITH principal AS (
  SELECT
    id,
    roles,
    groups,
    TRUE AS is_user
  FROM iam_users

  UNION ALL

  SELECT
    id,
    roles,
    groups,
    FALSE AS is_user
  FROM iam_groups
)
SELECT
  id,
  roles,
  groups,
  is_user
FROM principal
