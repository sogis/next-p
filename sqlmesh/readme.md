# SqlMesh POC

Fragestellungen:

* Koennen isolierte Unit-Tests geschrieben werden, mit welche Cornercases fuer eine komplizierte SQL-Query abgedeckt werden?
  * Ist das "einfach genug", so dass sich der Aufwand lohnt?
    * Res: Bis jetzt ja - noch json gedöns prüfen
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
    * SQL und insbesondere Unit-Tests: KI "schreibt zuviel am falschen Ort" - Ist im Betrieb (Ohne KI), für das Verständnis nicht ideal. Darum: Schrittweise erarbeiten.
