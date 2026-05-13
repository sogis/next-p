# NETL Volumes

Prototyp zur Definition der besten Ordnerstruktur für NETL. Ziele:

* Einfaches Ausführen von Gradle-Befehlen via TTL Terminal
* Nachvollziehbare Ordnerstruktur aus Sicht innerhalb Container und von Aussen (Dateisystem)
* Temporäre Dateien landen alle im entsprechenden Unterordner des Root-Gradle Projekts
  * Dieses ist von Aussen gut sichtbar.