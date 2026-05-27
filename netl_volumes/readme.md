# NETL Volumes

Prototyp zur Definition der besten Ordnerstruktur für NETL. Ziele:

* Einfaches Ausführen von Gradle-Befehlen via TTL Terminal
* Nachvollziehbare Ordnerstruktur aus Sicht innerhalb Container und von Aussen (Dateisystem)
* Temporäre Dateien landen alle im entsprechenden Unterordner des Root-Gradle Projekts
  * Dieses ist von Aussen gut sichtbar.

## Verzeichnisse

Übersicht der Verzeichnisse auf Host und innerhalb Docker, sowie deren Bedeutung

|Host|Docker|Bedeutung|
|---|---|---|
|netl_build|netl/netl_build|Ist als Gradle layout.buildDirectory konfiguriert. Für Themenintegratoren zwecks Transparenz einsehbar|
|netl_run|-|Teil der lokalen Entwicklungsumgebung, die lokal auf jeder Maschine vorhanden sein muss.|
|netl_source|netl|Nur für diesen Prototyp relevant. Wird dann in das Netl-Image reingebrannt.|
|repo|netl/repo|Konfigurations-Monorepo. Muss auf allen clients vom docker compose gleich referenziert werden können.|

## Verwendung

In Verzeichnis netl_run:

Docker container hochfahren:

    ./run.sh

Auf Bash in netl verbinden:

    ./shell.sh

Innerhalb der Shell den entsprechenden Gradle-Befehl ausführen. Beispiel:

    gradle copy


## Todo's

* netl_source in das Image brennen
* netl_run: Weg definieren, wie der Inhalt von netl_run bereitgestellt / aktualisiert werden kann






