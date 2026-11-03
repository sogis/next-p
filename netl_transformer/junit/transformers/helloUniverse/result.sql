CREATE OR REPLACE TABLE hello_universe AS
SELECT
    replace(greeting, 'world', 'universe') AS greeting,
    greeting AS source_greeting
FROM
    hello_world
;
