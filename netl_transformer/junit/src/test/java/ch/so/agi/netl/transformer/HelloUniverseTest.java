package ch.so.agi.netl.transformer;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static ch.so.agi.netl.transformer.TransformerTestSupport.row;
import static org.assertj.core.api.Assertions.assertThat;

class HelloUniverseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void runsHelloWorldThroughGradleWhenTheFullChainIsRequested() {
        TransformerTestSupport support = testSupport();
        TransformerTestSupport.Database database = support.createDatabase(temporaryDirectory);

        BuildResult result = support.runFullChain(database, helloUniverse());

        assertThat(result.task(":helloWorld").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertHelloUniverse(support, database);
    }

    private static void assertHelloUniverse(
        TransformerTestSupport support,
        TransformerTestSupport.Database database
    ) {
        support.assertRowsUnordered(
            database,
            "SELECT greeting FROM hello_universe",
            List.of(row("greeting", "hello universe"))
        );
        support.assertQueryEmpty(database, "SELECT * FROM hello_universe WHERE greeting IS NULL");
    }

    private static TransformerTestSupport testSupport() {
        return new TransformerTestSupport(Paths.get("transformers").toAbsolutePath());
    }

    private static TransformerTestSupport.TransformerBuild helloUniverse() {
        return TransformerTestSupport.TransformerBuild.of(
            "helloUniverse",
            "helloUniverse/transformer.gradle",
            "helloWorld/transformer.gradle"
        );
    }
}
