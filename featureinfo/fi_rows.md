# Konfigurations-Json der Featureinfo Ausgabe

## "Zeilenorientierte" Konfiguration und Rendering

Neu ist das Rendering der Featureinfoausgabe "zeilenorientiert" mit unterschiedlichen Zeilentypen aufgebaut.
Die Zeilentypen Rendern rendern sich alle in die immer gleich strukturierte tabellarische Ausgabe.

## Darstellungstypen

Visualisierung der verschiedenen Darstellungstypen, jeweils
durch eine Divider getrennt:

```text
+----------------------+--------------------------------+
| Alias                | Value                          |
+----------------------+--------------------------------+
| _________________ DIVIDER ___________________________ |
+----------------------+--------------------------------+
| Group Alias          | Alias       | Value            |
|                      | Alias       | Value            |
|                      | Alias       | Value            |
+----------------------+--------------------------------+
| _________________ DIVIDER ___________________________ |
+----------------------+--------------------------------+
| Alias                | Value                          |
+----------------------+--------------------------------+
| Alias                | Value                          |
+----------------------+--------------------------------+
| Alias                | Value                          |
+----------------------+--------------------------------+
| _________________ DIVIDER ___________________________ |
+----------------------+--------------------------------+
| Group Alias          | Value                          |
|                      | Value                          |
|                      | Value                          |
+----------------------+--------------------------------+
```

Namen der Darstellungstypen in der Visualisierung:
* Singlerow
* Divider
* Rowgroup
* Divider
* PromotedGroup
* Divider
* ValueGroup

Alle Darstellungstypen mit "group" im Namen stellen, in verschiedener Form, die
Werte eines Json-Listenattributs dar.

Beispiel für ein Json-Listenattribut:

|Spaltenname|Spaltenwert|
|---|---|
|dokumente|[<br>{"rrb":"2022/12","datum":"2022-12-15"},<br>{"rrb":"2019/05","datum":"2019-05-30"}<br>]|

## Json Konfiguration

Hauptunterscheidungsmerkmal der Konfiguration ist, ob die Darstellung eines Listenattributs oder eines "normalen" (sprich: Nicht listenbasiertes) Attributs konfiguriert wird.

Untergeordnetes Unterscheidungsmerkmal ist, ob der Themenintegrator das Jinja-Template
selbst entwirft, oder ob dieses aufgrund der in der Konfiguration enthaltenen Informationen automatisch abgeleitet wird.

### Konfiguration für normale Attribute

#### Tablecell

Konfiguriert die Darstellung einer einzelnen Spalte mit "normalen" Inhalt. Dies ist die häufigste und einfachste Konfigurationsart.

```json
{
    "type":"tablecell",
    "column_rev":"hoehe",
    "alias":"Höhe [m]"
}
```

#### Tablerow

Konfiguriert die Ausgabe basierend auf einer oder mehrerer "normaler" Spalten der gleichen Zeile mittels Template.

```json
{
    "type":"tablerow",
    "alias_tpl":"{{ 'Adresse' }}",
    "value_tpl":"{{ strasse ~ ' ' ~ hausnummer ~ ', ' ~ plz ~ ' ' ~ ort  }}",
    "column_revs": ["plz","ort","strasse","hausnummer"]
}
```

Mit dem Attribut `column_revs` wird in der deployment pipeline sichergestellt, dass die referenzierten Attribute in der Datenquelle auch effektiv existieren.

Bei einer Schemaänderung mit Spaltenumbenennung von "hausnummer" auf "hnr" bricht die Pipeline ab, da "hausnummer" in der Quelle nach der Spaltenumbenennung nicht mehr vorhanden ist.

### Konfiguration für ein listenbasiertes Attribut

Allen Konfigurationsarten für ein listenbasiertes Attribut ist gemein, dass eine der drei folgenden Darstellungen gewählt werden kann:

* Rowgroup
* PromotedGroup
* ValueGroup

Die entsprechende Darstellung wird jeweils ausgelöst, in dem die optionalen Eigenschaften _alias und _groupalias gesetzt oder weggelassen werden. 

#### Listcell

Simple Konfiguration, mit welcher ausgewählte Json-Attribute dargestellt werden.

```json
{
    "type":"listcell",
    "column_rev":"kostentraeger",
    "json_attributes":[
        {
            "value_rev":"sap_nr",
            "_alias":"SAP Nummer",
        },
        {
            "value_rev":"gde",
            "_alias":"Gemeindename",
        },
        {
            "value_rev":"signed",
            "_alias":"Vertragsabschluss",
            "_format_as":"date"
        },
    ],
    "group_alias":"Kostenträger"    
}
```

Bemerkungen:
* Beim Weglassen von `_alias` wird die Simplelist als ValueGroup ausgegeben, sonst als Rowgroup.
* Bei date und wenigen weiteren Datentypen kann nicht aus dem reinen Json der Datentyp und die korrekte Darstellung ermittelt werden. In diesen Situationen wird mittels `_format_as` erreicht, das die Ausgabe richtig erfolgt.

#### Listrow

Erlaubt ähnlich wie Tablerow, in der Ausgabe mittels Jinja Template mehrere Attribute
derselben Json-Row zu neuen "virtuellen" Attributen zu kombinieren, und diese auszugeben.

```json
{
    "type":"listrow",
    "column_rev":"adressen",
    "json_attributes":[
        {
            "value_tpl":"{{ strasse ~ ' ' ~ hausnummer }}",
            "_alias_tpl":"{{ 'Strasse / Nr' }}",
        },
        {
            "value_tpl":"{{ plz ~ ' ' ~ ort }}",
            "_alias_tpl":"{{ 'PLZ / Ort' }}",
        }
    ],
    "_group_alias":"Adressen"    
}
```

### Divider

Mit dem folgenden Json wird ein Divider konfiguriert:

```json
{
    "type":"divider"
}
```

## Fragen

* Wie gestalten, damit keine Umformatierungen während copy/paste zwischen Templatedesign-Webanwendung und den Konfigurations-Jsons notwendig sind?
* Die manuelle Nachführung von column_revs ist fehleranfällig. Gibt es in der Webanwendung Möglichkeiten, die im template referenzierten Spalten automatisch zu identifizieren?
* Wie erfogt das Templatedesign bei Facadelayern?
* Vertiefung bezüglich der notwendigen Kennung der entstehenden virtuellen Attribute:
    * Wo genau sind diese zwingend?
    * Kann dafür einfach die "Ausgabeposition" verwendet werden?