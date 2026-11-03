CREATE OR REPLACE TABLE user_role AS
WITH RECURSIVE
user_principal_references(user_id, principal_id, path) AS (
    SELECT
        id AS user_id,
        id AS principal_id,
        [id] AS path
    FROM
        _user_role_principal
    WHERE
        is_user

    UNION ALL

    SELECT
        reference.user_id,
        membership.group_id AS principal_id,
        list_append(reference.path, membership.group_id) AS path
    FROM
        user_principal_references AS reference
    JOIN
        _user_role_principal AS principal
        ON principal.id = reference.principal_id
    CROSS JOIN
        UNNEST(COALESCE(principal.group_ids, []::VARCHAR[])) AS membership(group_id)
    JOIN
        _user_role_principal AS group_principal
        ON group_principal.id = membership.group_id
    WHERE
        NOT list_contains(reference.path, membership.group_id)
),
principal_roles AS (
    SELECT
        principal.id AS principal_id,
        role.role_id
    FROM
        _user_role_principal AS principal
    CROSS JOIN
        UNNEST(COALESCE(principal.role_ids, []::VARCHAR[])) AS role(role_id)
)
SELECT DISTINCT
    reference.user_id,
    role.role_id
FROM
    user_principal_references AS reference
JOIN
    principal_roles AS role
    ON role.principal_id = reference.principal_id
;
