# SqlMesh POC

Fragestellungen:

* Koennen isolierte Unit-Tests geschrieben werden, mit welche Cornercases fuer eine komplizierte SQL-Query abgedeckt werden?
  * Ist das "einfach genug", so dass sich der Aufwand lohnt?
  * Wird damit sqlmesh als produktive Abhaengigkeit eingefuehrt, oder kann dies vermieden werden?
* Wie weit kann ein AI-Agent wie codex bei der Erstellung helfen?
  * Erstellung der Sql-Queries?
  * Erstellung des Konfigurations-Geruests fuer sqlmesh?
  * Erstellung der Unit-Tests?

## Hello World

This repo now contains a minimal DuckDB-backed SQLMesh model in `models/hello_world.sql`.
You can verify it locally with:

```bash
.venv/bin/sqlmesh evaluate hello_world --start 2026-04-06 --end 2026-04-06
```
