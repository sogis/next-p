CREATE OR REPLACE TABLE hello_world AS
SELECT
    'hello ' || subject AS greeting,
    'created by helloWorld' AS implementation_detail
FROM
    _hello_world_input
;
