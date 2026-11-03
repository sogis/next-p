package ch.so.agi.netl.transformer;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.fail;

/** Discovers isolated transformer data tests from src/test/resources/data-tests. */
class FixtureDataTests {
    private static final String DATA_TEST_ROOT = "data-tests";

    @TempDir
    Path temporaryDirectory;

    @TestFactory
    Stream<DynamicTest> fixtureScenarios() {
        try {
            List<Scenario> scenarios = discoverScenarios(resourceDirectory());
            String transformerFilter = System.getProperty("netl.fixture.transformer");
            if (transformerFilter != null && !transformerFilter.isBlank()) {
                scenarios = scenarios.stream()
                    .filter(scenario -> scenario.transformer.equals(transformerFilter))
                    .collect(Collectors.toList());
            }
            if (scenarios.isEmpty()) {
                return Stream.of(failingTest("fixture discovery", "No fixture scenarios found below " + DATA_TEST_ROOT
                    + (transformerFilter == null || transformerFilter.isBlank() ? "" : " for transformer " + transformerFilter)));
            }
            return scenarios.stream().map(this::dynamicTest);
        } catch (Exception exception) {
            return Stream.of(failingTest("fixture discovery", exception.getMessage(), exception));
        }
    }

    private DynamicTest dynamicTest(Scenario scenario) {
        if (scenario.error != null) {
            return failingTest(scenario.displayName(), scenario.error);
        }
        return DynamicTest.dynamicTest(scenario.displayName(), () -> runScenario(scenario));
    }

    private void runScenario(Scenario scenario) {
        TransformerTestSupport support = new TransformerTestSupport(Paths.get("transformers").toAbsolutePath());
        TransformerTestSupport.Database database = support.createDatabase(temporaryDirectory);
        for (Path fixture : scenario.inputs) {
            support.loadJsonFixture(database, relationName(fixture), fixture);
        }
        support.runIsolated(database, scenario.transformer);
        for (Path fixture : scenario.outputs) {
            support.assertJsonFixtureMatchesRelation(database, relationName(fixture), fixture);
        }
    }

    private static List<Scenario> discoverScenarios(Path root) throws Exception {
        List<Path> transformerDirectories = childDirectories(root);
        List<Scenario> scenarios = new ArrayList<>();
        for (Path transformerDirectory : transformerDirectories) {
            String transformer = transformerDirectory.getFileName().toString();
            for (Path scenarioDirectory : childDirectories(transformerDirectory)) {
                scenarios.add(discoverScenario(transformer, scenarioDirectory));
            }
        }
        return scenarios;
    }

    private static Scenario discoverScenario(String transformer, Path directory) {
        String scenarioName = directory.getFileName().toString();
        try {
            Path input = directory.resolve("input");
            Path output = directory.resolve("output");
            if (!Files.isDirectory(input) || !Files.isDirectory(output)) {
                return Scenario.invalid(transformer, scenarioName,
                    "Fixture scenario " + directory + " must contain input/ and output/ directories");
            }
            List<Path> inputs = jsonFiles(input);
            List<Path> outputs = jsonFiles(output);
            if (outputs.isEmpty()) {
                return Scenario.invalid(transformer, scenarioName,
                    "Fixture scenario " + directory + " must contain at least one output JSON file");
            }
            return Scenario.valid(transformer, scenarioName, inputs, outputs);
        } catch (Exception exception) {
            return Scenario.invalid(transformer, scenarioName, exception.getMessage());
        }
    }

    private static Path resourceDirectory() throws Exception {
        URL resource = FixtureDataTests.class.getClassLoader().getResource(DATA_TEST_ROOT);
        if (resource == null) {
            throw new IllegalStateException("Classpath resource directory does not exist: " + DATA_TEST_ROOT);
        }
        if (!"file".equals(resource.getProtocol())) {
            throw new IllegalStateException("Fixture discovery requires a filesystem resource directory: " + resource);
        }
        return Paths.get(resource.toURI());
    }

    private static List<Path> childDirectories(Path directory) throws Exception {
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).collect(Collectors.toList());
        }
    }

    private static List<Path> jsonFiles(Path directory) throws Exception {
        try (Stream<Path> children = Files.list(directory)) {
            List<Path> entries = children.sorted(Comparator.comparing(Path::toString)).collect(Collectors.toList());
            List<Path> fixtures = new ArrayList<>();
            for (Path entry : entries) {
                if (entry.getFileName().toString().equals(".gitkeep")) {
                    continue;
                }
                if (!Files.isRegularFile(entry) || !entry.getFileName().toString().endsWith(".json")
                    || entry.getFileName().toString().equals(".json")) {
                    throw new IllegalArgumentException("Fixture directory " + directory
                        + " may contain only non-empty .json files; found " + entry.getFileName());
                }
                fixtures.add(entry);
            }
            return fixtures;
        }
    }

    private static String relationName(Path fixture) {
        String filename = fixture.getFileName().toString();
        return filename.substring(0, filename.length() - ".json".length());
    }

    private static DynamicTest failingTest(String name, String message) {
        return failingTest(name, message, null);
    }

    private static DynamicTest failingTest(String name, String message, Exception cause) {
        return DynamicTest.dynamicTest(name, () -> {
            if (cause == null) {
                fail(message);
            }
            throw cause;
        });
    }

    private static final class Scenario {
        private final String transformer;
        private final String name;
        private final List<Path> inputs;
        private final List<Path> outputs;
        private final String error;

        private Scenario(String transformer, String name, List<Path> inputs, List<Path> outputs, String error) {
            this.transformer = transformer;
            this.name = name;
            this.inputs = inputs;
            this.outputs = outputs;
            this.error = error;
        }

        static Scenario valid(String transformer, String name, List<Path> inputs, List<Path> outputs) {
            return new Scenario(transformer, name, List.copyOf(inputs), List.copyOf(outputs), null);
        }

        static Scenario invalid(String transformer, String name, String error) {
            return new Scenario(transformer, name, List.of(), List.of(), error);
        }

        String displayName() {
            return transformer + "/" + name;
        }
    }
}
