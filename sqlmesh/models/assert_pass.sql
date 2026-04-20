MODEL (
  name assert_pass,
  description 'Helper model. Used only to verify the assert macro via test_assert_pass.yaml',
  kind FULL,
);

SELECT
  1 AS id,
  @ASSERT(1 = 1, '1 = 1 must pass') AS assertion_result
