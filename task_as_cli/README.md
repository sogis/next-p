# Gradle lifecycle-task demonstration

This self-contained project shows how to keep a Gradle command-line interface
focused on **lifecycle tasks**, while treating the lower-level action tasks as
implementation details.

## Public lifecycle tasks

Use these tasks to run a deployment workflow:

| Task | Environment | Purpose |
| --- | --- | --- |
| `deployDevelopment` | development | Runs the deployment lifecycle for development. |
| `deployStaging` | staging | Runs the deployment lifecycle for staging. |
| `deployProduction` | production | Runs the deployment lifecycle for production. |

They are all placed in Gradle's `deployment` task group and include a
user-facing description. Inspect the supported parameters with:

```sh
./gradlew help --task deployDevelopment
```

| Parameter | Default | Meaning |
| --- | --- | --- |
| `--deployment-version <label>` | `current` | The version label reported by the simulated deployment. |
| `--simulate` | disabled | Report the chosen deployment instead of reporting it as completed. |

Examples:

```sh
./gradlew deployDevelopment
./gradlew deployStaging --deployment-version 1.2.3 --simulate
./gradlew deployProduction --deployment-version 1.2.3
```

## Internal actions

Each lifecycle task depends on the internal `prepareDeployment`,
`uploadDeployment`, and `verifyDeployment` actions. These tasks are grouped as
`internal deployment`, have only brief implementation descriptions, and expose
no command-line options. They model the ordered work behind the public
lifecycle contract; callers should invoke a `deploy...` task instead.

The default task report intentionally lists only the public lifecycle tasks:

```sh
./gradlew tasks
```

The actions only write log messages, so this project is safe to run as a
configuration demonstration.
