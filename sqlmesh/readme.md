# SqlMesh POC

## Fragestellungen:

* Koennen isolierte Unit-Tests geschrieben werden, mit welche Cornercases fuer eine komplizierte SQL-Query abgedeckt werden?
  * Ist das "einfach genug", so dass sich der Aufwand lohnt?
    * Res: Ja, der Aufwand lohnt sich, insbesondere in Kombination mit KI-Agents, welche sowohl das Schreiben des SQL wie auch das Schreiben der Tests übernehmen können.
    * Bezüglich Json zu beachten:
      * Die Datentests müssen immer als YAML geschrieben werden. Im Moment kennt sqlmesh den Json-Datentyp bezüglich der Tests nicht, er vergleicht Strings. Die Tests können dadurch "wackelig" werden. Ein Test mit erwartetem Resultat 1. und Resultset-Spalteninhalt 2. schlägt fehl, obwohl es dasselbe Json ist:
        1. `'{"roles":["beta_role" ]}'`
        1. `'{"roles":["beta_role"]}'`
  * Wird damit sqlmesh als produktive Abhaengigkeit eingefuehrt, oder kann dies vermieden werden?
    * Res: Kann Verhindert werden 
      * mittels Befehl "render" und Ablage des generierten Sql in eine Datei.
      * oder bei duckdb noch einfacher: Modelle sind als Views in der duckdb abgebildet. Damit keine produktive Abhängigkeit auf sqlmesh
* Wie weit kann ein AI-Agent wie codex bei der Erstellung helfen?
  * Für diese Tätigkeiten:
    * Erstellung der Sql-Queries?
    * Erstellung des Konfigurations-Geruests fuer sqlmesh?
    * Erstellung der Unit-Tests?
  * Res: Ich war erstaunt wie gut! Zu beachten:
    * Bezüglich der YAML-Konfiguration manchmal falsche Auskunft der KI
    * SQL und insbesondere Unit-Tests: KI "schreibt zuviel am falschen Ort" - Ist im Betrieb (Ohne KI) und das Verständnis nicht ideal. Darum: Schrittweise erarbeiten.

## Weitere Restriktionen: 

* Ein erwarteter Fehler kann nicht direkt getestet werden im Sinne von Java `assertThrows(...)`. Falls der Fehlerfall wichtig ist, muss er in eine normales Resultset übersetzt werden. Beispiel: Eine State-Spalte mit -1 bei Fehler.
* Die in agents.md beschriebene "Task Isolation Rule" wird von codex weitestgehend ignoriert - muss anders gelöst werden (Betrifft rein codex, und nicht sqlmesh).

# Häufig genutzte sqlmesh Befehle (sqlmesh test, ...)

* **test:** Lässt alle Daten-Tests laufen
* **evaluate:** [modelname]: Gibt das Resultset eines Models (und all seiner upstream Models) auf der Kommandozeile aus. Scheint nicht so gut mit Listen umgehen zu können.
* **plan:** Erstellt glaubs die Temp-Tabellen oder Views auf der entsprechenden Umgebung. Ob Tabellen oder Views kann konfiguriert werden. In diesem Beispiel werden Views generiert.

## Hilfe auf der Kommandozeile

* `sqlmesh --help`: Übersicht aller Befehle
* `sqlmesh [subcommand] --help`: Hilfe zu den Optionen für den entsprechenden Subcommand. Beispiel: `sqlmesh test --help`

# Dieses Beispiel auf anderer Linux-Machine aufsetzen

* .venv/ erstellen mit allen für sqlmesh notwendigen Python-Libs (Siehe sqlmesh doc)
* .temp/ Ordner erstellen - In diesem wird die duckdb durch sqlmesh erstellt



