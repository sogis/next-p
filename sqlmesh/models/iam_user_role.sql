MODEL (
  name iam.user_role,
  kind FULL,
);

SELECT
  user_id,
  array_agg(DISTINCT role_id ORDER BY role_id) AS roles
FROM 
  iam.user_role_edges
GROUP BY
  user_id
