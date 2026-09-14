# Dry-run: Von Objektkatalog zu Ebenen V 1.1, von V 1.1 zu V 2.0

Es wird ein relativ umfassendes Szenario als Beispiel durchgegangen, um möglichst viele Konzeptteile auf diesem Weg beleuchten zu können.

Es soll eine Punktebene "Bauplanung- und Koordination" aufgeschaltet werden. Die Erfassung erfolgt via Web GIS Client.

# Grundlagen und Einordnung

Der Dry-run ist die Überleitung des Konzeptes des ganzen Programms zu den Konzepten und Spezifikationen der einzelnen Umsetzungsprojekte.

Da es beim Dry-run um die Arbeit des Themenintegrators geht, sind im Ablauf die folgenden Tools erwähnt. Deren Spezifikation erfolgt 
im Umsetzungsprojekt "Tools für die Themenintegratoren".

Übersicht der Tools / Befehle:

|Name|Beschreibung|
|---|---|
|ti|ti steht für das Kommandozeilentool "Theme Integrator". Umfasst die Befehle zur Unterstützung der lokalen Konfigurationsarbeiten. Name im Programm-Konzept: NETL|
|ti copy|Kopiert die angegebenen Schemen von den produktiven Edit- und Pub- Datenbanken in die lokale Umgebung.|
|ti refresh|Validiert die Konfiguration des Kellers und der Applikatioen. Erstellt die Komponenten-Konfigurationen für die Applikationen (WGC, Datenbezug, API's). Aktualisiert die lokal laufenden Komponenten mittels entsprechendem "docker compose" Befehl.|
|ti translateForm|Formt die Erfassungsformulardefinition von qml nach json um. Nutzt dazu Basisbibliotheken von sourcepole.|
|(ti merge)|Überschreibt für die zutreffenden Themen gemäss review_config.json die Themenkonfiguration mit dem auf S3 vorliegenden Stand. Kommt nur zwecks debugging lokal zum Einsatz.|
|Abhängigkeiten|Webapplikation mit analoger Funktion wie "Abhängigkeiten zeigen" in SIMI.|
|Berechtigungen|Webapplikation mit welcher die resultierenden Berechtigungen für Benutzer, Schemen und Ebenen angezeigt werden können.|
|Aufräumarbeiten|Zeigt eine Liste der anstehenden Aufräumarbeiten an. Beispielsweise bezüglich verwaister Schemen.|

# Konfigurationsschritte

## Schritte bis V 1.0

### A) "Keller"-Konfiguration

* Branch avt_baukoord_data_jek erstellen
* Themenordner avt/baukoord ab Data-Template erstellen
* Modelle erstellen
* Abgeleitete Modelle mittels ilishaper erstellen (Spalten-Abstreifung)
* Schemajobs erstellen
* Schemas lokal anlegen
* Gretljob(s) erstellen
* Gretljobs lokal erstellen
* Berechtigung konfigurieren
* "ti refresh" ausführen
    * Validiert die Konfiguration des Kellers (Da erst Keller vorhanden ist)
        * Json-Referenzen untereinander konsistent
        * Schemen liegen lokal vor
* Berechtigungen über log (einfache Fälle) und oder über Tool "Berechtigungen zeigen" verifizieren.
* Schemen mittels Schemajobs auf Prod anlegen
* Merge avt_baukoord_data_jek in main


### B) Produkt-Konfiguration

* Branch avt_baukoord_apps_jek erstellen
* avt/baukoord/apps ab Apps-Template erstellen
* datamart.json erstellen
* tables.json, layers.json und die *.qml erstellen
    * editForm.qml mittels "ti translateForm" in editForm.json wandeln
* "ti refresh" ausführen
* Lokal verifizieren
* Merge avt_baukoord_apps_jek in main

### C) Freigabe durch Fachamt einholen

* Fachamt verifiziert die publizierten Datensätze und Ebenen
* Themenbereitstellung freigeben durch Anpassung von review_config.json

## Schritte bis V 1.1

### Szenario 

Zwei Monate nach der Freigabe steht im Fachamt ein Pressetermin vor der Tür. Der zuständige Abteilungsleiter schaut sich die Ebenen erstmalig an und verlangt umfangreiche Änderungen. Die Datenmodelle sind zum Glück nicht betroffen.

### D) Produkt-Rekonfiguration

* Branch avt_baukoord_apps_jek erstellen (Ist neuer Branch, aber mit gleichem Namen wie der Branch unter B))
* Falls die DB-Schemen lokal nicht mehr vorhanden sind: Diese mittels "ti copy" von der Prod auf die lokale Umgebung kopieren.
* Mittels tool "Abhängigkeiten zeigen" einen Überblick verschaffen. Insbesondere, falls bestehende Identifier gelöscht oder umbenannt werden.
* **Konfiguration von review_config.json**
    * Produktions-Konfiguration der Applikationen wird bzgl. Thema avt/baukoord auf Stand V 1.0 eingefrohren
    * Review-Konfiguration folgt dem Inhalt des Themen-Repo
* Konfiguration anpassen (tables.json, layers.json, *.qml, ...)
* "ti refresh" ausführen
    * Validiert die Konfiguration des Kellers und der Applikatioen
    * Erstellt die Komponenten-Konfigurationen für die Applikationen (WGC, Datenbezug, API's)
    * Aktualisiert die lokal laufenden Komponenten mittels entsprechendem "docker compose" Befehl
* Lokal verifizieren
* Merge avt_baukoord_apps_jek in main

### E) Freigabe durch Fachamt einholen

* Fachamt verifiziert die auf Review publizierten Datensätze und Ebenen
* Themenbereitstellung freigeben durch Anpassung von review_config.json
    * Prod folgt nun wieder dem Inhalt des Themen-Repo

## Schritte bis V 2.0

### Szenario

Nach hoffentlich mehr als zwei Jahren Betrieb ändern die Anforderungen im grösserem Stil, so dass die Datenmodelle strukturell angepasst werden müssen. Es ist ein eher herausfordernder Fall für die Datenmigration, da auch die Edit-Umgebung betroffen ist, und nicht mehr als ein Tag "Edit-Stop" machbar ist.

### F) Migrationsplan erstellen

In avt/baukoord/migrations/20280505_edit_u_pub/readme.md den Plan erstellen:
* Branch avt_baukoord_migplan_jek erstellen
* Neue Modellversionen entwickeln und ablegen.
* Wird mittels xtf oder sql migriert?
* Was geht in welcher Reihenfolge life?
* Wer muss informiert werden über Edit-Unterbrüche, pausierte Pub-Jobs, ...
* Branch avt_baukoord_migplan_jek mergen


### G) Migration erstellen

* Branch avt_baukoord_mig_jek erstellen
* Schemajobs aktualisieren
    * Ref auf neue Modelle
    * Schemaversion inkrementieren
* Schemajobs ausführen
    * Lokal
    * auf Prod
* Mittels "ti copy" die produktiven Schemen als "Migrationsquellen" lokal nehmen
* Migrationsjob 
    * lokal erstellen und testen (normaler GRETL-Job)
    * auf Produktion durchführen
* Gretljobs
    * lokal anpassen und testen
    * auf Produktion durchführen
* Branch avt_baukoord_mig_jek bleibt bis Ende J) bestehen

### H) Produkt-Rekonfiguration

Analog zu D). Mit der folgenden wichtigen Ergänzung:
* Es werden auch die schemas.json mutiert (Inkrementierung der Schemaversion)
    * Dadurch zeigt "Preview" auf die neuen Schemas 

### I) Freigabe durch Fachamt einholen

Analog zu E)

### J) Migration abschliessen

* Migrationsjob erneut durchführen
* Branch avt_baukoord_mig_jek mergen. 
    * Damit laufen die Gretljobs zwischen den neuen Schemen. 
    * Die alten Schemen werden nun weder von Gretljobs, noch von Applikationen genutzt.
* Alte Schemen auf prod löschen
    * Mittels "Abhängigkeiten zeigen" verifizieren, dass keine Applikationen mehr auf das Schema zeigen
    * Mittels Schemajob löschen

# Vorbereitungsaufgabe

Versucht zu beantworten, was die Umgebungen nach jedem der Schritte C) bis J) bezüglich dem Thema "Baukoordination" umfassen:
* Umgebungen:
    * Lokal (Lokale Entwicklungsumgebung)
    * Review
    * Produktion
* Mit der Umgebung verknüpftes Schema? \[schemaname\]_v1 oder \[schemaname\]_v2?

Beispielsweise tabellarisch in dieser Form (Weitere Zeilen werde ich am Termin mit eurer Unterstützung ausfüllen).

|Nach?|Lokal|L Schema|Review|R Schema|Produktion|P Schema|Bemerkungen|
|---|---|---|---|---|---|---|---|
|A)|-|-|-|-|-|-|V1-Schemen Lokal und auf Prod angelegt|
|B)|App V1|V1|App V1|V1|-|-|-|




