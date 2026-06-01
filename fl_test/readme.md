# Abnahme der QGIS Server Erweiterungen

Der QGIS Server wurde von opengis erweitert, um:

* Facadelayer im Kern zu unterstützen (opaque layer genannt).
* In der Featureinfo-Antwort für jedes Feature die Kennung der "Datenebene" sowie für Features aus einem opaque layer den Namen des Opaque Layer zurückzugeben.

## Test-Setup

Installation von QGIS Desktop und Server (nightly) auf WSL zur Konfiguration der Projekte und dem Ausführen des Development Server (qgis_mapserver).

Testdaten: MOpublic-Daten der Stadt Olten, als geopackage "2581.ch.so.agi.av.mopublic.gpkg" im versteckten Verzeichnis ".data" abzulegen (Nicht eingecheckt).

Starten des Dev QGIS Server:

        qgis_mapserver -p /home/bjsvwjek/code/next-p/fl_test/vermess.qgs


## Testfälle Facadelayer

|Nr.|Beschr.|Details Resultat|OK?|
|---|---|---|---|
|1.1|GetCapa: Kinder kommen nicht vor||Ja|
|1.2|GetCapa: Publizierte Legend-URL produziert korrektes Resultat||Ja|
|2.3|GetMap: In Massstab sichtbare Kinder werden bei Request auf Parent gerendert||Ja|
|2.4|GetMap: Request auf Child schlägt fehl|<ServiceException code="LayerNotDefined">The layer 'einzelobjekt_punkt' does not exist.</...|Ja|
|2.3|GetMap: Request auf im Layerbaum vorhandenen Layer mit gleicher Kennung wie ein Child funktioniert||Ja|
|3.1|GetFeatureinfo: Gibt alle angeklickten Objekte der Kindebenen zurück||Ja|
|3.2|GetFeatureinfo: Gibt die Kennung des Kindlayers zurück|Attribut "featureType"|Ja|
|3.3|GetFeatureinfo: Gibt die Kennung des Opaque-Layers zurück|Attribut "qgis:requestedWmsName"|Ja|
|3.4|GetFeatureinfo: Request auf Child schlägt fehl|<ServiceException code="LayerNotDefined">The layer 'einzelobjekt_punkt' does not exist.</...|Ja|
|4|GetLegend: Rendert die Kind-Ebenen des Opaque-Layers||Ja|
|5.1|Dokumentation Opaque-Layer||Nein|
|5.2|Dokumentation "featureType"||Nein|
|5.2|Dokumentation "qgis:requestedWmsName"||Nein|

### Bemerkungen

"qgis:requestedWmsName" wird nur bei info_format "application/json" zurückgegeben, sofern ein Opaque-Layer angefordert wurde.

Opaque layer scheint als Attribut "wms-group-request-mode" im qgs codiert zu werden.

```
<layer-tree-group checked="Qt::Checked" expanded="1" groupLayer="" name="vermess" wms-group-request-mode="Opaque">...</layer-tree-group>
```

Wert für "normalen" Group-Layer: "Normal"