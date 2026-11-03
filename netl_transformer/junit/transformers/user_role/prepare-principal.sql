CREATE OR REPLACE TABLE _user_role_principal AS
SELECT
    id,
    roles_::VARCHAR[] AS role_ids,
    groups_::VARCHAR[] AS group_ids,
    TRUE AS is_user
FROM
    input.users

UNION ALL

SELECT
    id,
    roles_::VARCHAR[] AS role_ids,
    groups_::VARCHAR[] AS group_ids,
    FALSE AS is_user
FROM
    input.groups
;
