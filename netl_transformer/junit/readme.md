# POC: DuckDB/JUnit transformer tests

See [the parent study](../readme.md). This POC validates the selected variant:
production execution remains Gradle/Gretl, while JUnit uses DuckDB directly to
test the same SQL files.

## Decision

Use **Gradle tasks and their `dependsOn` relationships** as the authoritative
DAG. A named public `SqlExecutor` task represents one transformer and executes
its final SQL step. It owns its private preparation and public upstream
dependencies. There is no second DAG format, test DSL, or data-build framework.

## Running

Run all transformer tests from `junit/`:

```bash
./gradlew test
```

Run the tests for one transformer:

```bash
./gradlew test --tests ch.so.agi.netl.transformer.HelloUniverseTest
```

Run the aggregate production build, which applies every transformer definition:

```bash
./gradlew -p transformers helloUniverse
./gradlew -p transformers helloUniverse -PduckdbUrl=jdbc:duckdb:/path/to/netl.duckdb
```

```groovy
def sqlExecutor = project.plugins.getPlugin('ch.so.agi.gretl').class.classLoader
    .loadClass('ch.so.agi.gretl.tasks.SqlExecutor')
def helloWorldDir = new File(transformersDir, 'helloWorld')

def helloWorldCreateInput = tasks.register('_helloWorldCreateInput', sqlExecutor) {
    sqlFiles = files(new File(helloWorldDir, 'create-input.sql'))
}
def helloWorld = tasks.register('helloWorld', sqlExecutor) {
    group = 'transformers'
    sqlFiles = files(new File(helloWorldDir, 'result.sql'))
    dependsOn helloWorldCreateInput
}
def helloUniverseDir = new File(transformersDir, 'helloUniverse')
tasks.register('helloUniverse', sqlExecutor) {
    group = 'transformers'
    sqlFiles = files(new File(helloUniverseDir, 'result.sql'))
    dependsOn 'helloWorld'
}
```

The `helloWorld` task is the transformer's public final SQL task and its public
entry point. Its preparation step is a separate internal Gradle task. Private
task dependencies are the single definition of local execution order.
Filenames are descriptive and do not encode ordering.

Another transformer may depend only on a public final task such as
`helloWorld`, never on its internal tasks. `helloUniverse` declares that
dependency in its own definition, so it owns its production prerequisites while
knowing nothing about `helloWorld`'s implementation. Gradle has no private-task
visibility, so task names beginning with `_` identify private tasks; a later
configuration-time validation will reject a public dependency on one. Private
steps may prepare only local data; they must not read upstream transformer
output. The downstream public final SQL task consumes such output, after Gradle
has run the upstream public task. Production execution and Gradle's task graph
remain the single source of truth.

JUnit uses Gradle TestKit's `GradleRunner` to create a temporary host build for
the transformer under test. That host applies the same shared runtime and the
selected `transformer.gradle`; a full-chain scenario additionally applies its
real upstream transformer definitions. A test invokes a public transformer task
with a temporary, file-backed DuckDB URL, then opens that same database through
JDBC for its assertions. Production and tests therefore use the same Gretl task
definitions without making the aggregate production build the test subject.

An isolated test discovers the requested transformer's direct public upstream
tasks and excludes them with Gradle's `-x` option. It fixture-loads the stable
relations those tasks would have produced. A full-chain test omits `-x`,
letting Gradle execute the complete public dependency graph.

## File layout

```text
junit/
  build.gradle
  settings.gradle
  gradlew
  gradle/wrapper/
  transformers/
    build.gradle                 # production aggregate host
    transformer-host.gradle      # shared host configuration
    transformer-runtime.gradle   # shared transformer-definition contract
    settings.gradle
    helloWorld/
      transformer.gradle
      create-input.sql
      result.sql
    helloUniverse/
      transformer.gradle
      result.sql
  src/test/java/.../
    TransformerTestSupport.java
    FixtureDataTests.java
    HelloUniverseTest.java
  src/test/resources/data-tests/
    helloWorld/expected-greeting/
      input/
      output/hello_world.json
    helloUniverse/expected-greeting/
      input/hello_world.json
      output/hello_universe.json
```

`transformer-host.gradle` supplies `transformersDir` as the directory
containing all transformer directories, and applies `transformer-runtime.gradle`
once. Each transformer script resolves its SQL files below that path. TestKit's
temporary host passes the repository transformer directory through
`-PtransformersDir`; it does not copy or redefine production transformer tasks.
Tests use classpath resources for JSON fixtures.

## Gradle script roles

The POC has three kinds of Gradle script:

1. A transformer-definition script, one per transformer, for example
   `transformers/helloWorld/transformer.gradle`. Its directory name matches the
   public transformer task name, and it is stored with that
   transformer's SQL files, declares the transformer's private
   `SqlExecutor` step tasks, its public final `SqlExecutor` task, and the SQL
   paths used by production tasks. It declares dependencies on other
   transformers only by their public task names and contains no JUnit settings.
2. The production aggregate host, `junit/transformers/build.gradle`. It puts
   Gretl on its root build classpath, applies the shared host once, and applies
   all transformer definitions. A generated TestKit host does the same bootstrap
   but applies only the transformer definition(s) required by its scenario.
3. The test build script, `junit/build.gradle`. It applies the Java plugin and
   adds Gradle TestKit, DuckDB JDBC, JUnit, and AssertJ as test-only
   dependencies. JUnit uses TestKit to invoke the transformer-runner's public
   tasks.

The Gretl runner uses no JUnit, assertion-library, or test-fixture dependency;
those dependencies are confined to the Java test build.

The transformer-runner accepts `-PduckdbUrl`, `-PduckdbUser`,
`-PduckdbPassword`, and `-PinputDir`. `duckdbUrl` defaults to the file-backed
database `junit/transformers/build/netl-transformer.duckdb`; the hello-world
sample creates local input itself, so `inputDir` is reserved for the later
external-input preparation task.

Transformer-definition scripts define production behavior once. They never
apply a host or runtime themselves. Tests exercise the selected scripts through
TestKit, so they never duplicate task definitions or transformer dependencies.

## Database and input configuration

Tests use a fresh, file-backed DuckDB database. Before invoking a production
task through TestKit, each test loads small JSON fixtures into the transformer's
external input relations. For example, a test may load `schemas.json` into
`input.schemas`. Tests must not use the production database or production input
files.

Transformer SQL reads stable DuckDB table or view names, such as
`input.schemas` and `staging.users`; it never embeds input JSON paths. The
test setup phase creates those relations from its fixture files. This makes
each scenario a concise choice of fixture data and keeps path handling outside
SQL.

An isolated transformer test loads both external source relations and fixture
relations representing each public upstream transformer output. It invokes the
requested public task with `-x` for every direct public upstream task discovered
from its Gradle dependencies; private `_...` steps still run. This keeps a
unit-level transformer test small and focused.

A full-chain test omits `-x`, so Gradle runs upstream public tasks first in the
same DuckDB database. Such a test does not fixture-load relations produced by
those upstream transformers. The complete isolated-test sequence is therefore:

1. create a fresh file-backed DuckDB database;
2. load fixture JSON into the external input relations;
3. load fixture relations for public upstream transformer outputs;
4. invoke the requested public transformer through TestKit and its private SQL
   steps; and
5. assert result rows, shape, and rules.

Production tasks receive their database URL and input locations through Gradle
properties, for example `-PduckdbUrl=jdbc:duckdb:/path/to/netl.duckdb` and
`-PinputDir=/path/to/input`. The production Gradle tasks pass the database
values to Gretl's `SqlExecutor`. A later preparation task will load real inputs
into stable relation names; the current hello-world POC creates its local input
relation itself. The test helper never receives production settings. This is
configuration, not dependency injection of a database object, and keeps the
test and deployment concerns separate.

The SQL files must be valid both in Gretl production tasks and through DuckDB
JDBC. A transformer may finish with a `SELECT` that returns a query result. If
its result has to be consumed by a later production task, the SQL should create
a named table or view; a transient JDBC result cannot be a DAG dependency in a
separate Gretl task.

## Tests

Expected-result data tests are discovered by one JUnit `@TestFactory`; no
transformer-specific Java test method is required.  Each directory below
`src/test/resources/data-tests` is one isolated scenario:

```text
data-tests/<public-transformer-task>/<scenario>/
  input/
    <relation>.json
  output/
    <relation>.json
```

The filename without `.json` is the DuckDB relation name, including dots for
qualified relations (for example `input.schemas.json` maps to
`input.schemas`). Every input relation is loaded before the public transformer
task runs. The fixture runner provides stubs for every other conventionally
named public transformer task and excludes them, so upstream output fixtures
belong in `input/` and no second dependency declaration is needed.

Each output JSON must contain at least one object. Its object keys are the
selected output projection and its rows are compared unordered, with duplicate
rows significant. Extra actual columns are therefore allowed. Empty-result,
schema, rule, and full-chain tests remain ordinary JUnit tests. An empty
`input/` directory may be retained in Git with `.gitkeep`.

Malformed scenario directories produce a named failing dynamic test; valid
scenarios are reported as `<transformer>/<scenario>` in JUnit results.

Run every scenario with `./gradlew test`. To run the data scenarios for one
transformer, pass its public task name, for example:

```bash
./gradlew test -Dnetl.fixture.transformer=helloUniverse \
  --tests ch.so.agi.netl.transformer.FixtureDataTests
```

Add the DuckDB JDBC driver and an assertion library such as AssertJ only to
the test configuration. They are not production dependencies.

The test support should expose only three small operations:

1. create a temporary DuckDB database and load fixture JSON;
2. run a named public transformer through Gradle TestKit, either with public
   upstream tasks excluded or with its complete dependency closure;
3. query its result and compare selected columns, or assert that a validation
   query returns no rows.

For expected rows, use a helper that takes an explicit projection and compares
rows without regard to order. The projection deliberately ignores additional
actual columns:

```java
assertRowsUnordered(
    database,
    "select greeting from hello_world",
    List.of(row("greeting", "hello world"))
);
```

The helper should reject a missing expected column and print the selected
actual and expected rows on failure. It should not silently ignore duplicate
rows: comparison is multiset-based, so duplicates remain significant.

Schema and rule tests remain ordinary assertions. Query `information_schema`
or DuckDB catalog tables for names, types, and nullability; express
data-quality rules as anti-queries and assert that they return no rows.

## POC examples to implement

The POC should contain two transformers:

* `helloWorld`, comprising two SQL steps, which emits a `greeting` column
  containing `hello world`;
* `helloUniverse`, which depends on `helloWorld` and emits `hello universe`.

Each example should have one expected-result test. Add a small, otherwise
irrelevant column to one actual result to demonstrate that expected-row
assertions compare only the stated projection. `helloUniverse` needs an
isolated test with a fixture for `helloWorld` output and a full-chain test that
executes `helloWorld` through Gradle TestKit.

## Why this is the simplest fit

The Gradle task graph is native, familiar, and lets a transformer use as many
SQL files or production tasks as necessary. TestKit runs the production task
graph unchanged, while JUnit inspects the resulting DuckDB database through
JDBC. Plain SQL is shared unchanged between Gretl and tests. This avoids a
second execution model, while JUnit plus DuckDB JDBC and an assertion library
provide familiar reporting and concise assertions.

## Implementation plan

1. Create `transformers/helloWorld/transformer.gradle` and
   `transformers/helloUniverse/transformer.gradle` beside their SQL files.
   Define their private `SqlExecutor` tasks and one public final `SqlExecutor`
   task each.
2. Declare public upstream dependencies in the downstream transformer script,
   so `helloUniverse` depends on `helloWorld`.
3. Add the three sample SQL files. Keep filenames descriptive; private Gradle
   task dependencies, not filenames, define their execution order.
4. Configure `junit/transformers/build.gradle` as the transformer runner:
   apply the transformer scripts, accept the DuckDB/input Gradle properties,
   prepare stable input relations, and invoke public transformer tasks.
5. Configure `junit/build.gradle` independently with the Java plugin plus
   Gradle TestKit, DuckDB JDBC, JUnit 5, and AssertJ in test scope only.
6. Implement `TransformerTestSupport`: create file-backed DuckDB databases,
   load fixture JSON into external input relations, invoke public production
   tasks with TestKit, and provide unordered projected-row and empty-result
   assertions.
7. Add TestKit helpers for full-chain execution and isolated execution, where
   the latter discovers direct public upstream tasks and uses `-x` to exclude
   them.
8. Add `HelloWorldTest` and `HelloUniverseTest`. Test `helloUniverse` in
   isolation with a fixture for `helloWorld` output; add a full-chain test that
   proves the upstream transformer can instead be executed by Gradle.
9. Run the JUnit suite and then invoke the public production transformer tasks
   against a file-backed DuckDB database to verify that Gretl and test
   execution use the same SQL successfully.


## Weiterfahren

Was davon ist notwendig? (transformer.gradle):

      // The host project must provide dbUri, dbUser, and dbPwd before applying this
      // script. Paths are relative to that host project's directory.
      def sqlExecutor = project.plugins.getPlugin('ch.so.agi.gretl').class.classLoader
         .loadClass('ch.so.agi.gretl.tasks.SqlExecutor')
      def transformerDir = new File(transformersDir, 'helloWorld')
      def helloWorldCreateInput = tasks.register('_helloWorldCreateInput', sqlExecutor) {
         description = 'Creates the local input required by the helloWorld transformer.'
         database = [dbUri, dbUser, dbPwd]
         sqlFiles = files(new File(transformerDir, 'create-input.sql'))
      }

Ideen: 
* apply from auch im testkontext verwenden
   * apply from header sowohl beim testen wie für das prod skript -> eine quelle für den header
   * transformer dependencies: für das testen einfacher loswerden durch beispielsweise gruppieren in "trafodep" oder ähnlich
   * Umformen der Tests, damit diese rein aus den Testfixtures folgen, ohne die Notwendigkeit von jeweiligen @test methoden

Dokumentieren:
* Ausführen des Tests eines transformators
* Ausführen des ganzen builds


