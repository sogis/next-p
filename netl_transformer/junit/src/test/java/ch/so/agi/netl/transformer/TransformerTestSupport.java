package ch.so.agi.netl.transformer;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Test-only bridge between JUnit assertions and the production Gradle
 * transformer build.  It deliberately invokes public Gradle tasks instead of
 * executing transformer SQL directly.
 */
public final class TransformerTestSupport {
    private final Path transformersDirectory;

    public TransformerTestSupport(Path transformersDirectory) {
        this.transformersDirectory = requireDirectory(transformersDirectory, "transformersDirectory");
    }

    /** Creates an empty, file-backed DuckDB database below {@code directory}. */
    public Database createDatabase(Path directory) {
        try {
            Files.createDirectories(directory);
            Path databaseFile = Files.createTempFile(directory, "transformer-test-", ".duckdb");
            Files.deleteIfExists(databaseFile);
            Database database = new Database(databaseFile);
            try (Connection ignored = database.openConnection()) {
                // Opening the connection creates the DuckDB file.
            }
            return database;
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Could not create a DuckDB test database in " + directory, exception);
        }
    }

    /**
     * Loads a JSON fixture into a stable relation, for example
     * {@code input.schemas}.  Parent schemas are created when necessary.
     */
    public void loadJsonFixture(Database database, String relation, Path fixture) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(fixture, "fixture");
        if (!Files.isRegularFile(fixture)) {
            throw new IllegalArgumentException("JSON fixture does not exist: " + fixture);
        }

        String quotedRelation = quoteQualifiedIdentifier(relation);
        String parentSchema = parentSchema(relation);
        String fixturePath = fixture.toAbsolutePath().toString().replace("'", "''");
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement()) {
            if (parentSchema != null) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(parentSchema));
            }
            statement.execute("CREATE OR REPLACE TABLE " + quotedRelation
                + " AS SELECT * FROM read_json_auto('" + fixturePath + "')");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load fixture " + fixture + " into " + relation, exception);
        }
    }

    /** Loads a classpath JSON resource, including resources packaged in a JAR. */
    public void loadJsonResource(Database database, String relation, String resourceName) {
        URL resource = TransformerTestSupport.class.getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new IllegalArgumentException("Classpath JSON resource does not exist: " + resourceName);
        }
        try (InputStream input = resource.openStream()) {
            Path fixture = Files.createTempFile("transformer-fixture-", ".json");
            try {
                Files.copy(input, fixture, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                loadJsonFixture(database, relation, fixture);
            } finally {
                Files.deleteIfExists(fixture);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read classpath JSON resource " + resourceName, exception);
        }
    }

    /** Runs the target with the selected real upstream transformer definitions. */
    public BuildResult runFullChain(Database database, TransformerBuild transformer) {
        return run(database, transformer, transformer.definitionPaths, List.of());
    }

    /**
     * Runs a public transformer while excluding public upstream tasks.  Tests
     * using this method must fixture-load the relations produced by those
     * upstream tasks before invoking it.
     */
    public BuildResult runIsolated(Database database, TransformerBuild transformer, String... publicUpstreamTasks) {
        return run(database, transformer, List.of(transformer.definitionPath), List.of(publicUpstreamTasks));
    }

    /**
     * Runs a conventionally named transformer in isolation.  Every sibling
     * transformer definition is made available to the temporary Gradle host,
     * while every other public transformer task is excluded.  This lets data
     * fixtures avoid maintaining a second dependency declaration.
     */
    public BuildResult runIsolated(Database database, String publicTask) {
        validatePublicTask(publicTask);
        List<String> definitions = discoverTransformerDefinitionPaths();
        String definitionPath = publicTask + "/transformer.gradle";
        if (!definitions.contains(definitionPath)) {
            throw new IllegalArgumentException("No transformer definition exists for public task " + publicTask);
        }
        List<String> otherTasks = definitions.stream()
            .map(path -> path.substring(0, path.indexOf('/')))
            .filter(task -> !task.equals(publicTask))
            .collect(Collectors.toList());
        TransformerBuild transformer = TransformerBuild.of(publicTask, definitionPath);
        return run(database, transformer, List.of(transformer.definitionPath), otherTasks);
    }

    /**
     * Reads expected rows from JSON and compares them with the named relation.
     * Expected keys define the projection, so implementation-only columns do
     * not make a data test fail.
     */
    public void assertJsonFixtureMatchesRelation(Database database, String relation, Path expectedFixture) {
        List<Map<String, Object>> expected = readJsonFixtureRows(database, expectedFixture);
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("Expected-output JSON must contain at least one row: " + expectedFixture);
        }
        List<String> columns = new ArrayList<>(expected.get(0).keySet());
        String projection = columns.stream()
            .map(TransformerTestSupport::quoteIdentifier)
            .collect(Collectors.joining(", "));
        assertRowsUnordered(database, "SELECT " + projection + " FROM " + quoteQualifiedIdentifier(relation), expected);
    }

    private List<Map<String, Object>> readJsonFixtureRows(Database database, Path fixture) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(fixture, "fixture");
        if (!Files.isRegularFile(fixture)) {
            throw new IllegalArgumentException("JSON fixture does not exist: " + fixture);
        }
        String temporaryRelation = "__netl_expected_" + UUID.randomUUID().toString().replace('-', '_');
        String fixturePath = fixture.toAbsolutePath().toString().replace("'", "''");
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + quoteIdentifier(temporaryRelation)
                + " AS SELECT * FROM read_json_auto('" + fixturePath + "')");
            return queryRows(connection, "SELECT * FROM " + quoteIdentifier(temporaryRelation)).rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read expected JSON fixture " + fixture, exception);
        } finally {
            try (Connection connection = database.openConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + quoteIdentifier(temporaryRelation));
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not clean up expected JSON fixture " + fixture, exception);
            }
        }
    }

    private List<String> discoverTransformerDefinitionPaths() {
        try (Stream<Path> paths = Files.list(transformersDirectory)) {
            return paths
                .filter(Files::isDirectory)
                .map(directory -> directory.resolve("transformer.gradle"))
                .filter(Files::isRegularFile)
                .map(transformersDirectory::relativize)
                .map(Path::toString)
                .map(path -> path.replace('\\', '/'))
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not discover transformer definitions in " + transformersDirectory, exception);
        }
    }

    private BuildResult run(
        Database database,
        TransformerBuild transformer,
        List<String> definitionPaths,
        List<String> excludedPublicTasks
    ) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(transformer, "transformer");
        Path testHost = createTestHost(definitionPaths, excludedPublicTasks);
        List<String> arguments = new ArrayList<>();
        arguments.add("-PduckdbUrl=" + database.jdbcUrl());
        arguments.add("-PtransformersDir=" + transformersDirectory);
        arguments.add("--stacktrace");
        for (String excludedTask : excludedPublicTasks) {
            validatePublicTask(excludedTask);
            arguments.add("-x");
            arguments.add(excludedTask);
        }
        arguments.add(transformer.publicTask);
        return GradleRunner.create()
            .withProjectDir(testHost.toFile())
            .withArguments(arguments)
            .build();
    }

    private Path createTestHost(List<String> definitionPaths, List<String> stubTaskNames) {
        try {
            Path host = Files.createTempDirectory("transformer-test-host-");
            Files.writeString(host.resolve("settings.gradle"), "rootProject.name = 'transformer-test-host'\n", StandardCharsets.UTF_8);
            StringBuilder build = new StringBuilder();
            build.append(GRETL_BOOTSTRAP);
            build.append("apply from: '").append(quoteGroovy(transformersDirectory.resolve("transformer-host.gradle"))).append("'\n");
            for (String definitionPath : definitionPaths) {
                Path definition = transformersDirectory.resolve(definitionPath).normalize();
                if (!definition.startsWith(transformersDirectory) || !Files.isRegularFile(definition)) {
                    throw new IllegalArgumentException("Transformer definition does not exist: " + definitionPath);
                }
                build.append("apply from: '").append(quoteGroovy(definition)).append("'\n");
            }
            for (String task : stubTaskNames) {
                validatePublicTask(task);
                build.append("tasks.register('").append(quoteGroovy(task)).append("') { group = 'transformer-test-stubs' }\n");
            }
            Files.writeString(host.resolve("build.gradle"), build.toString(), StandardCharsets.UTF_8);
            return host;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create temporary transformer test host", exception);
        }
    }

    private static final String GRETL_BOOTSTRAP = String.join("\n",
        "buildscript {",
        "    repositories {",
        "        maven { url 'https://repository.jboss.org/nexus/content/groups/public/' }",
        "        maven { url 'https://plugins.gradle.org/m2/' }",
        "        maven { url 'https://jars.interlis.ch' }",
        "        maven { url 'https://repo.osgeo.org/repository/release/' }",
        "        mavenCentral()",
        "    }",
        "    dependencies {",
        "        classpath('ch.so.agi.gretl:ch.so.agi.gretl.gradle.plugin:3.2.863') {",
        "            exclude group: 'javax.media', module: 'jai_core'",
        "        }",
        "        classpath 'javax.media:jai-core:1.1.3'",
        "    }",
        "}",
        "apply plugin: 'ch.so.agi.gretl'",
        ""
    );

    /** Compares projected query rows as a multiset; duplicate rows remain significant. */
    public void assertRowsUnordered(Database database, String query, List<? extends Map<String, ?>> expectedRows) {
        List<Map<String, Object>> expected = copyExpectedRows(expectedRows);
        QueryResult result = executeQuery(database, query);
        Set<String> expectedColumns = expected.isEmpty()
            ? new LinkedHashSet<>()
            : expected.get(0).keySet();
        assertThat(result.columns)
            .as("query <%s> must select every expected column", query)
            .containsAll(expectedColumns);
        assertThat(result.rows)
            .as("query <%s>\nexpected rows: %s\nactual rows: %s", query, expected, result.rows)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    /** Fails when the validation query returns even one row. */
    public void assertQueryEmpty(Database database, String query) {
        List<Map<String, Object>> actual = queryRows(database, query);
        assertThat(actual)
            .as("expected validation query to return no rows: <%s>", query)
            .isEmpty();
    }

    /** Reads query rows with their selected column labels, for ordinary schema assertions. */
    public List<Map<String, Object>> queryRows(Database database, String query) {
        return executeQuery(database, query).rows;
    }

    private QueryResult executeQuery(Database database, String query) {
        Objects.requireNonNull(database, "database");
        try (Connection connection = database.openConnection()) {
            return queryRows(connection, query);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not execute query: " + query, exception);
        }
    }

    private QueryResult queryRows(Connection connection, String query) {
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                columns.add(metadata.getColumnLabel(column));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    row.put(metadata.getColumnLabel(column), resultSet.getObject(column));
                }
                rows.add(row);
            }
            return new QueryResult(columns, rows);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not execute query: " + query, exception);
        }
    }

    /** Convenience builder for concise expected rows. */
    public static Map<String, Object> row(Object... columnsAndValues) {
        if (columnsAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("row requires alternating column names and values");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < columnsAndValues.length; index += 2) {
            Object column = columnsAndValues[index];
            if (!(column instanceof String) || ((String) column).isEmpty()) {
                throw new IllegalArgumentException("row column names must be non-empty strings");
            }
            if (row.put((String) column, columnsAndValues[index + 1]) != null) {
                throw new IllegalArgumentException("row contains duplicate column " + column);
            }
        }
        return row;
    }

    private static List<Map<String, Object>> copyExpectedRows(List<? extends Map<String, ?>> expectedRows) {
        Objects.requireNonNull(expectedRows, "expectedRows");
        List<Map<String, Object>> expected = new ArrayList<>();
        Set<String> columns = null;
        for (Map<String, ?> expectedRow : expectedRows) {
            if (expectedRow == null) {
                fail("Expected rows must not contain null");
            }
            Map<String, Object> copy = new LinkedHashMap<>(expectedRow);
            for (String column : copy.keySet()) {
                if (column == null || column.isEmpty()) {
                    fail("Expected row columns must be non-empty strings");
                }
            }
            if (columns == null) {
                columns = new LinkedHashSet<>(copy.keySet());
            } else if (!columns.equals(copy.keySet())) {
                fail("Expected rows must project the same columns; found %s and %s", columns, copy.keySet());
            }
            expected.add(copy);
        }
        return expected;
    }

    private static Path requireDirectory(Path directory, String name) {
        Objects.requireNonNull(directory, name);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException(name + " is not a directory: " + directory);
        }
        return directory.toAbsolutePath();
    }

    private static void validatePublicTask(String task) {
        if (task == null || task.trim().isEmpty() || task.substring(task.lastIndexOf(':') + 1).startsWith("_")) {
            throw new IllegalArgumentException("A non-private public Gradle task is required: " + task);
        }
    }

    private static String quoteGroovy(Path path) {
        return quoteGroovy(path.toAbsolutePath().toString());
    }

    private static String quoteGroovy(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String parentSchema(String relation) {
        int separator = relation.lastIndexOf('.');
        return separator < 0 ? null : relation.substring(0, separator);
    }

    private static String quoteQualifiedIdentifier(String identifier) {
        String[] parts = identifier.split("\\.", -1);
        if (parts.length == 0) {
            throw new IllegalArgumentException("A relation name is required");
        }
        List<String> quoted = new ArrayList<>();
        for (String part : parts) {
            quoted.add(quoteIdentifier(part));
        }
        return String.join(".", quoted);
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier parts must not be empty");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public static final class Database {
        private final Path file;

        private Database(Path file) {
            this.file = file.toAbsolutePath();
        }

        public Path file() {
            return file;
        }

        public String jdbcUrl() {
            return "jdbc:duckdb:" + file;
        }

        public Connection openConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl());
        }
    }

    /** Definition files required to assemble a temporary host for one public transformer. */
    public static final class TransformerBuild {
        private final String publicTask;
        private final String definitionPath;
        private final List<String> definitionPaths;

        private TransformerBuild(String publicTask, String definitionPath, List<String> upstreamDefinitionPaths) {
            validatePublicTask(publicTask);
            this.publicTask = publicTask;
            this.definitionPath = requireDefinitionPath(definitionPath);
            List<String> paths = new ArrayList<>();
            for (String upstreamDefinitionPath : upstreamDefinitionPaths) {
                String path = requireDefinitionPath(upstreamDefinitionPath);
                if (!path.equals(this.definitionPath)) {
                    paths.add(path);
                }
            }
            paths.add(this.definitionPath);
            this.definitionPaths = List.copyOf(paths);
        }

        public static TransformerBuild of(String publicTask, String definitionPath, String... upstreamDefinitionPaths) {
            return new TransformerBuild(publicTask, definitionPath, List.of(upstreamDefinitionPaths));
        }

        private static String requireDefinitionPath(String definitionPath) {
            if (definitionPath == null || definitionPath.isBlank() || Path.of(definitionPath).isAbsolute()) {
                throw new IllegalArgumentException("A relative transformer definition path is required");
            }
            return definitionPath;
        }
    }

    private static final class QueryResult {
        private final List<String> columns;
        private final List<Map<String, Object>> rows;

        private QueryResult(List<String> columns, List<Map<String, Object>> rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }
}
