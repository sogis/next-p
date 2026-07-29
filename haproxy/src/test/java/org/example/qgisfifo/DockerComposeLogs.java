package org.example.qgisfifo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Collects raw Compose logs without filtering or altering their contents. */
public final class DockerComposeLogs {
    private static final List<String> QGIS_SERVICES = List.of("qgis-1", "qgis-2", "qgis-3", "qgis-4");

    private DockerComposeLogs() {
    }

    public static void collectQgisLogs(Path destination, Duration timeout) {
        List<String> command = new ArrayList<>();
        String composeCommand = System.getenv("FIFO_DOCKER_COMPOSE_COMMAND");
        if (composeCommand == null || composeCommand.trim().isEmpty()) {
            command.add("docker");
            command.add("compose");
        } else {
            command.addAll(Arrays.asList(composeCommand.trim().split("\\s+")));
        }
        String composeFile = System.getenv("COMPOSE_FILE");
        if (composeFile != null && !composeFile.trim().isEmpty()) {
            command.add("--file");
            command.add(composeFile);
        }
        command.add("logs");
        command.add("--no-color");
        command.addAll(QGIS_SERVICES);

        try {
            Files.createDirectories(destination.getParent());
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(destination.toFile())
                .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("docker compose logs timed out after " + timeout
                    + "; raw log: " + destination.toAbsolutePath());
            }
            if (process.exitValue() != 0) {
                throw new AssertionError("docker compose logs exited with " + process.exitValue()
                    + "; raw log: " + destination.toAbsolutePath());
            }
        } catch (IOException error) {
            throw new AssertionError("could not start docker compose logs; raw log: "
                + destination.toAbsolutePath(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while collecting Compose logs; raw log: "
                + destination.toAbsolutePath(), error);
        }
    }
}
