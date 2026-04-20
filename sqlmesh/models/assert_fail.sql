MODEL (
  name assert_fail,
  description 'Helper model. Used only to verify the assert macro via test_assert_fail.yaml',
  kind FULL,
);

SELECT
  1 AS id,
  @ASSERT(1 = 0, '1 = 0 must fail') AS assertion_result
