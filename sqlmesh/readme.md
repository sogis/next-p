# SqlMesh POC

Fragestellungen:

* Koennen isolierte Unit-Tests geschrieben werden, mit welche Cornercases fuer eine komplizierte SQL-Query abgedeckt werden?
  * Ist das "einfach genug", so dass sich der Aufwand lohnt?
  * Wird damit sqlmesh als produktive Abhaengigkeit eingefuehrt, oder kann dies vermieden werden?
    * Kann Verhindert werden mittels Befehl render und Ablage des generierten Sql in eine Datei.
* Wie weit kann ein AI-Agent wie codex bei der Erstellung helfen?
  * Erstellung der Sql-Queries?
  * Erstellung des Konfigurations-Geruests fuer sqlmesh?
  * Erstellung der Unit-Tests?

## Hello World

This repo now contains a minimal DuckDB-backed SQLMesh model in `models/hello_world.sql`.
You can run it locally from the command line with:

```bash
# Install dependencies once
.venv/bin/pip install -r requirements.txt

# Run the model for a single day
.venv/bin/sqlmesh evaluate hello_world --start 2026-04-06 --end 2026-04-06
```
