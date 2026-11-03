# Testing SQL transformers in netl

This folder contains the study and decision for testing the SQL transformers in
netl, and for fitting those tests into the Gradle-based data build.

The overall build is a Gradle multi-project build. One project assembles
metadata from JSON files, PostgreSQL catalogues, and LDAP into a metadata
schema conforming to
[SO_AGI_Metadata_20260825](https://github.com/sogis/schema-jobs/blob/jek_metamodels/topics/tmp_metamodels/SO_AGI_Metadata_20260825.ili).
Several component projects then generate component-specific metadata subsets in
their required formats; those projects can run in parallel. The atomic build
steps are primarily tasks from the `ch.so.agi.gretl` Gradle plugin, notably
`SqlExecutor` and `Db2Db`.

A transformer is a short, ordered chain of SQL statements. It transforms one
or more input relations into a result set, table, or view in DuckDB. In
production, inputs may originate from JSON, PostgreSQL, or LDAP. This study is
deliberately narrower: by the time a transformer is tested, every input is
available as JSON or as a DuckDB table. Connecting to PostgreSQL and LDAP is
out of scope.

## Requirements

The chosen approach must:

* test a transformer's expected rows and values;
* test its output shape and database-level rules, such as column names, types,
  nullability, and applicable constraints;
* run with fixture JSON and DuckDB only;
* support a chain of SQL queries as naturally as it supports a single query;
* keep production execution simple with Gradle and Gretl;
* make transformers and their tests easy to author and maintain, including for
  AI-assisted development; and
* avoid introducing a large framework solely to test a small SQL
  transformation layer.

## Variants considered

### 1. Gradle TestKit/DuckDB/JUnit test harness

Gradle/Gretl production tasks with a small Gradle TestKit/DuckDB/JUnit test
harness

Transformers are stored as ordinary, ordered SQL files. Production Gradle tasks
run those files through Gretl. Tests create a temporary file-backed DuckDB
database, load small JSON fixtures, then use Gradle TestKit to run the same
public production task. JUnit connects to that database through JDBC for its
assertions.

Expected-result tests compare the resulting rows with compact expected tables
or queries. Shape and rule tests inspect the DuckDB catalog and run assertion
queries that must return no violating rows. A thin shared test helper owns only
database setup, Gradle invocation, and readable assertion failures.

This option adds Gradle TestKit, the DuckDB JDBC driver, and standard test
dependencies. It retains the existing Gradle/Gretl production model and runs
the exact production task graph. The small amount of test helper code is
explicit and can be tailored to the project, without introducing a new
modelling language or build lifecycle.

### 2. Gretl/Gradle tests only

Tests could be implemented entirely as Gradle tasks and Gretl SQL execution
steps, with SQL queries that fail when their result contains discrepancies.

This maximises alignment with production execution and introduces no separate
test library. However, test fixtures, result comparisons, expected failures,
and diagnostic output would need to be encoded repeatedly in Gradle and SQL.
It is less pleasant for focused transformer tests and makes a failed assertion
harder to understand. It is a reasonable execution mechanism, but not a
complete testing experience on its own.

### 3. dbt with the DuckDB adapter

dbt supplies model dependencies, generic tests, and a familiar SQL-first test
experience. It is well suited to a project whose primary build and model graph
are already managed by dbt.

Here it would create a second project model, dependency graph, configuration
format, and execution lifecycle beside Gradle/Gretl. Adopting it just for the
small transformer layer adds more concepts than it removes, while production
orchestration would still remain in Gradle. It is therefore disproportionate
to the scope of this study.

### 4. A standalone SQL test framework or custom command-line runner

Tools such as SQLLogicTest-style runners can compare query results compactly,
and a custom command-line test runner can do the same with very few runtime
dependencies.

These alternatives handle row comparisons well, but need additional custom
conventions for JSON fixture loading, chained transforms, catalog assertions,
and Gradle reporting. They also give up the established JUnit reporting and
IDE support. The resulting bespoke test language or runner has little benefit
over a small JUnit helper.

## Decision

Use a **Gradle TestKit/DuckDB/JUnit test harness**.

Transformers remain plain SQL files, grouped and executed in their defined
order. Their production Gradle tasks continue to use Gretl. Tests use isolated
file-backed DuckDB databases, load representative JSON fixtures, invoke the
same public Gradle task graph through TestKit, and assert both result contents
and output metadata/rules. For isolated transformer tests, TestKit excludes
public upstream tasks and fixtures supply their output relations. The test
harness should remain deliberately narrow: it is not a new transformation
framework, nor does it duplicate Gretl orchestration.

This is the practical sweet spot. It preserves a single production build model,
offers direct and readable tests for the two required test classes, and adds
only familiar, small dependencies. A subfolder may contain a prototype of the
test helper and representative tests to validate the conventions before they
are adopted by the main build.

## Further reading

See `Konzept_Next_V1.0.pdf` for the broader context of this study, in
particular the chapters describing netl.
