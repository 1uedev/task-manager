# Fachmodell und Projektkonfiguration

Stand 15. Oktober 2026 · TASK-005 · Entwurf zur Prüfung durch Holger

Dieses Dokument konkretisiert REQ-ORG-003, REQ-TKT-001 bis REQ-TKT-005, REQ-TIME-001, REQ-TIME-002 und REQ-REQ-001 bis REQ-REQ-003 aus den [zentralen Anforderungen](../REQUIREMENTS.md). Es beschreibt fachliches Verhalten, kein Datenbankschema und keinen Anwendungscode. Die [Architektur](ARCHITECTURE.md) und das [Rechtekonzept](PERMISSIONS.md) bleiben Entwürfe.

Bestätigt sind konfigurierbare Projekte, Tickets, Zeiterfassung, Anforderungen mit einfacher Freigabe sowie deren Verbindung mit Dokumentation und Git. Zusätzlich hat Holger am 13. Oktober festgelegt: Nach inhaltlicher Änderung einer freigegebenen Anforderung entsteht eine neue Revision zur erneuten Freigabe; die bisherige Freigabe bleibt historisch erhalten (DEC-030). Alle weiteren Detailregeln unten sind Vorschläge. Gruppennamen und Beispielstatus sind keine fest eingebauten Rollen oder unveränderlichen Workflows.

## Fachliche Objekte und ihre Beziehungen

| Objekt | Verantwortung und Mindestinhalt | Wichtige Grenze |
|---|---|---|
| Projekt | Kennung, übersetzbarer Name, Unternehmenskontext, Mitglieder, Konfiguration, Aktiv-/Archivzustand | Ein Projekt kann mehrere Kundenorganisationen betreuen; das Portal zeigt nur erlaubte Anfragen. |
| Ticket | Projekt, Typ, Titel, Beschreibung, Status, Priorität, Verantwortlicher, Ersteller, Zeitpunkte, Änderungsnummer | Ein bearbeitbarer Vorgang; weder eine Zeitbuchung noch eine formale Anforderung. |
| Kundenbezug | Kundenorganisation und anfragender Kontakt einer Anfrage | Bestätigte Organisationssicht innerhalb freigegebener Projekte; Absender- und Organisationszuordnung serverseitig prüfen. |
| Beitrag | Text, Autor, Zeitpunkt, Zielgruppe und zugehörige Dateien | Interne Notiz und Kundenantwort sind verschiedene Aktionen. |
| Felddefinition / Feldwert | Stabile Feldkennung, Datentyp, übersetzbare Bezeichnung, Prüf- und Sichtregeln | Umbenennen ändert keine Kennung; ein ausdrücklich verbotenes Feld bleibt verborgen. |
| Beziehung | Quelle, Ziel und Art, zum Beispiel Unteraufgabe oder blockiert | Beide Seiten behalten ihre eigenen Zugriffsrechte. |
| Zeitbuchung | Projekt, optional Ticket, Person, Leistungsdatum, Dauer, Tätigkeit, Änderungsverlauf | Tatsächliche erfasste Zeit, keine automatisch aus Ticketstatus abgeleitete Dauer. |
| Anforderung | Stabile Kennung, Projekt, fachliche Revisionen, Verantwortlicher, Priorität, Kriterien, Freigaben | Ein erledigtes Umsetzungsticket ist keine fachliche Freigabe der Anforderung. |
| Konfigurationsversion | Typen, Felder, Formulare, Übergänge und erlaubte Automationsregeln | Fachliche Regeln werden versioniert; aktuelle Berechtigungen gelten unabhängig davon. |

Kennungen bleiben bei Umbenennung erhalten. Zeitpunkte werden eindeutig gespeichert; Leistungsdatum und Dauer einer Zeitbuchung sind eigene fachliche Angaben. Sprachabhängige Namen verändern weder Beziehungen noch Regelkennungen. Es gibt keine automatische Übernahme von Rechten über einen Link oder eine Unteraufgabe.

## Ticketfelder als Ausgangsvorschlag

| Feld / Gruppe | Vorschlag für den Kern | Änderung und Sichtbarkeit |
|---|---|---|
| Kennung, Ersteller, erstellt/geändert | Systemgeführt; Projektkennung und sichtbare Ticketnummer getrennt von interner Identität | Nicht frei umschreibbar; auch erratene Nummern unterliegen Rechten. |
| Projekt und Typ | Bei Anlage erforderlich; späterer Wechsel als eigene Aktion | Nur mit Rechten an Quelle und Ziel sowie gültiger Feld-/Statuszuordnung. |
| Titel und Beschreibung | Titel erforderlich, Beschreibung je Formular oder Übergang | Portalformulare können eigene Pflichtregeln haben; keine vertraulichen Vorgabewerte ausgeben. |
| Status | Startstatus je Typ; weitere Änderungen nur durch gültige Übergänge | Kein freies Setzen eines beliebigen Status per API. |
| Priorität | Administrierbare Liste mit stabilen Kennungen und Standardwert | Kein fest verdrahteter Zusammenhang zwischen Anzeigename und SLA. |
| Verantwortlicher | Optional bei Anlage; für ausgewählte Übergänge erforderlich | Nur geeignete aktive Projektmitglieder auswählbar. |
| Kundenorganisation / Kontakt | Für Kundenanfragen erforderlich, für interne Aufgaben optional | Externe können nicht durch Eingabe einer Kennung zu einem anderen Kunden wechseln. |
| Termine, Tags, Version/Meilenstein | Kandidaten zur Priorisierung unter OPEN-021 | Kein automatischer V1-Lieferumfang durch diese Tabelle. |
| Eigene Felder | Vorschlag: Kurztext, Langtext, Ganzzahl, Dezimalzahl, Datum, Wahr/Falsch, Ein-/Mehrfachauswahl, Benutzerreferenz | Datentyp, zulässige Werte, Längen und Pflichtbedingungen je Projekt festlegen. |

Freie Berechnungsformeln, kundeneigener Servercode und beliebige verschachtelte Datentypen werden durch die konfigurierbaren Felder nicht vorausgesetzt. Ein Zahlenfeld kann im Beispiel die geschätzte Restdauer enthalten; daraus entsteht keine bestätigte Ressourcenplanung.

## Zwei Projekte mit unterschiedlichen Abläufen

### Beispiel A: Kundenservice

Das Projekt „Service“ betreut Nordwerk und Südtechnik. Tickettypen sind „Störung“ und „Frage“. Lea und Paul gehören zu Nordwerk. Sie sehen standardmäßig auch die Anfragen des jeweils anderen, aber keine Südtechnik-Anfragen oder Projekte ohne eigene Zuordnung.

| Übergang | Wer im Beispiel? | Bedingung / Ergebnis |
|---|---|---|
| Anlage → Neu | Berechtigte Externe oder Support | Titel und Kundenbezug gültig; Eingangskanal und Eingangszeit bleiben nachvollziehbar. |
| Neu → In Bearbeitung | Support | Zuständiger Bearbeiter und Priorität vorhanden. |
| In Bearbeitung → Wartet auf Kunde | Support | Konkrete Rückfrage und Begründung der Wartephase erfassen; Behandlung der Fristen in TASK-006. |
| Wartet auf Kunde → In Bearbeitung | Berechtigte Kundenantwort oder Support | Wartephase endet nachvollziehbar. Automatische Zuordnung einer Antwort benötigt dieselben Rechte. |
| In Bearbeitung → Gelöst | Support | Kundenlesbare Lösungsbeschreibung vorhanden; zugehöriger Versand kann separat noch ausstehen. |
| Gelöst → In Bearbeitung | Support nach berechtigter Rückmeldung | Wiedereröffnung als neues Ereignis, frühere Lösung bleibt sichtbar. |
| Gelöst → Geschlossen | Support | Explizite Abschlussaktion; in diesem Beispiel kein ungefragter zeitgesteuerter Abschluss. |
| Offen/aktiv → Abgebrochen | Gesondert berechtigter Support | Begründung erforderlich; keine erfolgreiche Lösung vortäuschen. |

Der fachliche Zustand und der Versandzustand werden getrennt angezeigt. Ein „gelöstes“ Ticket kann eine fehlgeschlagene Lösungsnachricht haben. SLA-Regeln legen gesondert fest, wann eine Lösung als mitgeteilt zählt. Externe dürfen im Vorschlag anlegen und antworten, aber weder interne Felder ändern noch eigenmächtig den Supportabschluss setzen. Zusätzliche Kundenaktionen bleiben administrierbar.

### Beispiel B: Softwareentwicklung

Das interne Projekt „Produktentwicklung“ verwendet die Typen „Fehler“, „Aufgabe“ und „Verbesserung“. Sein Ablauf lautet Eingang → Geplant → In Entwicklung → In Prüfung → Erledigt. „Wartet auf Kunde“ ist hier kein notwendiger Status.

| Übergang | Bedingung im Beispiel |
|---|---|
| Eingang → Geplant | Priorität und prüfbare Beschreibung; bei Fehlern erwartetes und beobachtetes Verhalten. |
| Geplant → In Entwicklung | Verantwortlicher vorhanden; bei verknüpfter Anforderung deren relevante Revision anzeigen. |
| In Entwicklung → In Prüfung | Prüfanleitung und Umsetzungsnachweis; ein Git-Link ist möglich, aber kein pauschaler Ersatz für die Beschreibung. |
| In Prüfung → Erledigt | Berechtigter Prüfer dokumentiert Ergebnis. Ein zweiter Mensch ist hier eine mögliche Projektregel, keine allgemeine Produktvorgabe. |
| In Prüfung → In Entwicklung | Begründete Rückgabe mit erhaltenem Prüfverlauf. |
| Erledigt → In Entwicklung | Berechtigte Wiedereröffnung mit Grund; keine Löschung des bisherigen Abschlusses. |

„In Prüfung“ gehört fachlich zu aktiver Arbeit; „Erledigt“ zu erledigten Vorgängen. Konfigurierbare Anzeigenamen erhalten eine eindeutige fachliche Kategorie, damit Berichte und später SLA-Regeln nicht Zeichenketten wie „Closed“ auswerten. Welche Kategorien V1 genau anbietet, muss mit TASK-006 abgestimmt werden. Nicht jedes Projekt erhält allein wegen dieser Vorbereitung ein SLA.

## Konfiguration ändern, ohne Bestandsdaten zu beschädigen

Vorschlag: Administratoren bearbeiten einen Entwurf, prüfen Auswirkungen und veröffentlichen eine neue Konfigurationsversion. Neue Tickets verwenden sie. Vorhandene Tickets wechseln erst über eine ausdrücklich geprüfte Zuordnung auf die neue Version. Eine Rechteänderung wirkt dagegen auf aktuelle Zugriffe sofort nach dem Rechtevertrag; alte Konfigurationsstände konservieren keine entzogenen Rechte.

| Änderung | Vorgeschlagene Behandlung |
|---|---|
| Feld oder Status umbenennen / übersetzen | Kennung erhalten; historische Darstellung bleibt nachvollziehbar. |
| Neue Auswahloption | Neue stabile Optionskennung. Entfernte Optionen für neue Eingaben deaktivieren, vorhandene Werte erhalten. |
| Feldtyp wechseln | Keine stille Konvertierung. Neue Definition oder kontrollierter Migrationsplan mit Fehlervorschau; ungültige Bestandswerte verhindern die Freigabe der betreffenden Migration. |
| Neues Pflichtfeld | Geltung bei Anlage beziehungsweise bestimmten Übergängen ausdrücklich wählen. Bestandslücken anzeigen, bevor Tickets umgestellt werden. |
| Status entfernen | Zuerst Zielstatus und erlaubte Übergänge für betroffene Tickets festlegen. Kein Löschen noch verwendeter Kennungen. |
| Neues ausdrückliches Feldverbot | Auf aktuellen Zugriff einschließlich Historie, Suche und KI anwenden. Keine automatische Rechteerweiterung zur Reparatur eines Formulars. |
| Projektvorlage ändern | Nur künftige Übernahmen; bestehende Projekte ändern sich nicht ungefragt. Vorlagen/Archivierung sind bisher V1-Vorschläge. |

**Verborgene Pflichtfelder:** Beispiel „Interne Ursache“ ist extern verboten, aber beim internen Lösen einer Störung erforderlich. Die Kundenanlage bleibt möglich, weil das Feld erst beim internen Übergang geprüft wird. Ein bereits bei Kundenanlage benötigtes verborgenes Feld benötigt einen fachlich gültigen internen Standard oder eine andere zulässige Regel. Fehlt beides, wird die betroffene Formularkonfiguration nicht freigegeben. Nicht vorab erkennbare Kombinationen aus Gruppen und Verboten werden zur Laufzeit ohne Teiländerung abgewiesen; eine verständliche Meldung nennt den Konfigurationskonflikt, nicht den geheimen Wert.

**Automationen:** Vorschlag V1 ist ein begrenztes Regelmodell aus Ereignis, Bedingung und erlaubter Aktion, etwa Zuweisung an eine Supportgruppe oder eine regelbasierte Benachrichtigung. Kein beliebiger Kundenprogrammcode. Regeln besitzen eine ausführende Identität mit begrenzten Rechten, Änderungsprotokoll, Wiederholungsschutz und eine begrenzte Kette weiterer Ereignisse. Eine Regel darf weder Feldverbote noch Übergangsbedingungen übergehen. KI-Vorschläge benötigen unabhängig davon ausdrückliche Bestätigung; eine Automation darf diese nicht ersetzen.

## Beziehungen, Verschieben und Archivieren

Unteraufgabe, „blockiert“, „verwandt mit“ und „Duplikat von“ sind unterschiedliche Beziehungstypen. Vorschlag: höchstens ein übergeordnetes Ticket, keine Zyklen in Unteraufgaben oder Blockaden. „Verwandt“ ist symmetrisch. Beziehungen zwischen Projekten benötigen Zugriff auf beide Ziele und eine erlaubte Projektregel; verborgene Ziele liefern keinen Titel und keine indirekte Suchauskunft.

Ein Verschieben zwischen Projekten prüft beide Berechtigungen, Typ-/Feld-/Statuszuordnung, Kundenbezug und sensible Beiträge. Es darf zuvor interne Inhalte nicht allein durch einen neuen Projektkontext extern sichtbar machen. Vorschlag V1: Verschieben mit vorhandenen Zeitbuchungen zunächst ablehnen, solange keine gesonderte Umbuchungsregel beschlossen ist. So verändern sich Projektzeiten nicht still rückwirkend. Die spätere Freigabe dieses Sonderfalls gehört zu OPEN-021.

Archivierung bedeutet im Vorschlag lesbar gemäß Rechten, aber keine neuen fachlichen Änderungen oder Zeitbuchungen. Offene Tickets, laufende SLA-Fristen und ausstehende Aufträge müssen vor Archivierung geklärt sein. E-Mails an ein archiviertes Projekt bleiben als unbearbeitete Eingänge sichtbar; sie erzeugen keine versteckten Änderungen. Wiederöffnen reaktiviert das Projekt, aber nicht automatisch alte Automations- oder Versandaufträge. Löschen und Aufbewahrung sind eigene Regeln aus TASK-010.

## Zeitbuchungen und Korrekturen

Vorschlag für V1: Leistungsdatum, positive Dauer in vollen Minuten, Tätigkeitsbeschreibung, Projekt und optional Ticket. Bei Ticketbezug müssen Projekt und Berechtigung übereinstimmen. Der Benutzer erfasst Zeit für sich; fremde Zeiten und Korrekturen benötigen eigene Rechte. Timer, Abrechnung, Stundensätze und formale Periodenfreigaben bleiben spätere Kandidaten aus REQ-TIME-003.

Eigene Buchungen in aktiven Projekten sind mit passender Berechtigung korrigierbar. Eine Korrektur ersetzt den wirksamen Wert und bewahrt vorherigen Wert, Bearbeiter, Zeitpunkt und Grund. Ein Storno setzt eine Buchung fachlich außer Wirkung, ohne ihre Historie zu löschen. Summen verwenden ausschließlich die aktuell wirksamen Werte, niemals zusätzlich die historischen Revisionen. Ein vorgeschlagener CSV-Export nennt Zeitraum, Filter und Einheit und enthält nur zugängliche Daten; sein tatsächlicher V1-Umfang bleibt unter OPEN-021 zu entscheiden.

Auf geschlossene Tickets dürfen berechtigte Personen im Vorschlag nachträglich buchen, wenn das Projekt aktiv ist; dies öffnet weder Ticket noch SLA erneut. Auf archivierte Projekte wird nicht gebucht. Zukünftige Leistungsdaten werden im vorgeschlagenen Ist-Zeit-Modell abgewiesen. Eine später eingeführte Planung ist ein anderer Datentyp. Dieselbe Dauer bleibt nach Wechsel der Benutzerzeitzone unverändert.

**Rechenbeispiel:** Nina bucht 90 Minuten, Omar 30 Minuten. Summe: 120 Minuten. Nina korrigiert auf 75 Minuten; neue Summe: 105 Minuten, nicht 195. Omar storniert seine Buchung; verbleiben 75 Minuten. Anzeige 1,25 Stunden beziehungsweise „1 Std. 15 Min.“ meint dieselbe Dauer. Fehlen Leserechte auf Omars Buchung, darf deren Wert auch nicht über eine verborgene Differenz oder eine ungefilterte Gesamtsumme offengelegt werden.

## Anforderungsrevisionen und Freigabe

Eine Anforderung besitzt eine dauerhafte Kennung und getrennte fachliche Revisionen. Die technische Änderungsnummer schützt paralleles Speichern, die fachliche Revision bezeichnet den Inhalt, auf den eine Freigabe verweist. Eine einzelne freigegebene Revision ist noch kein formaler eingefrorener Stand mehrerer Anforderungen; REQ-REQ-004 bleibt eine spätere Erweiterung.

**Bestätigte Regel DEC-030:** Wird freigegebener Inhalt geändert, entsteht eine neue Revision zur erneuten Freigabe. Die bisherige Fassung samt freigebender Person und Zeitpunkt bleibt erhalten. Ein neues Git-Commit oder ein erledigtes Umsetzungsticket übernimmt keine Freigabe für den geänderten Inhalt.

Vorgeschlagener Ablauf: Entwurf → Zur Prüfung → Freigegeben; eine Rückgabe führt mit Begründung zum Entwurf. Die erste Bearbeitung eines freigegebenen Inhalts erzeugt einen neuen Entwurf. Während dessen Bearbeitung bleibt die vorige Freigabe als „Revision 1 freigegeben; Revision 2 in Bearbeitung“ erkennbar. Die neue Revision darf nicht als freigegeben erscheinen. Mehrere Korrekturen am noch nicht freigegebenen Entwurf führen zu Änderungshistorie, nicht zwingend zu immer neuen fachlichen Revisionen.

Als inhaltlich gelten im Vorschlag Titel, Beschreibung, Abnahmekriterien, Priorität, fachlicher Geltungsbereich und eigene Fachfelder. Eine reine Zuweisung des Bearbeiters oder Ergänzung eines nachvollziehbaren Umsetzungslinks kann ohne neue Fachrevision erfolgen, wird aber protokolliert. Diese eng begrenzte Ausnahme muss vor Umsetzung bestätigt werden. Eine Freigabe referenziert stets die tatsächlich geprüfte Revision; zwischenzeitliche Änderungen verhindern eine Bestätigung des alten Bildschirmstands.

**Beispiel:** Anforderung ANF-17, Revision 1, verlangt die Anzeige des Ticketstatus für berechtigte Kunden. Sie wird freigegeben und mit DEV-42 verknüpft. Später soll zusätzlich die letzte Kundenantwort angezeigt werden. Es entsteht Revision 2 als Entwurf. DEV-42 bleibt zunächst auf Revision 1 bezogen; ein Hinweis macht die neuere Revision sichtbar. Erst eine ausdrückliche Zuordnung verbindet die Umsetzung mit Revision 2. Die neue Fassung benötigt eine eigene Freigabe. Ein Freigabevergleich zeigt nur Felder, für die der Prüfer Leserechte besitzt; ohne die für die Freigabe notwendigen Inhalte kann er diese Revision nicht freigeben.

## KI-Nutzen innerhalb dieser Abläufe

| Bereich | Vorschlag zur Unterstützung | Grenze |
|---|---|---|
| Ticketanlage | Beschreibung gliedern, fehlende Angaben nennen, Typ und Priorität vorschlagen | Kein automatisches Setzen, keine Erweiterung der Kundensicht. |
| Projektkonfiguration | Auswirkungen einer Regeländerung erklären und problematische Beispiele benennen | Verbindliche Gültigkeitsprüfung bleibt deterministisch; Anwendung nur durch berechtigte Administration nach Bestätigung. |
| Zeitbuchung | Tätigkeitsbeschreibung verbessern, vorhandene Buchungen zusammenfassen | Keine erfundenen Dauern, kein Zugriff auf verborgene Zeiten, Änderung nur nach Bestätigung. |
| Anforderungen | Widersprüche und fehlende Kriterien finden, Unterschiede zwischen Revisionen erklären | Keine fachliche Freigabe durch KI; geänderte Inhalte folgen DEC-030. |
| Beziehungen | Mögliche Duplikate und passende Dokumentation vorschlagen | Nur sichtbare Inhalte; Verknüpfen benötigt Bestätigung und normale Fachrechte. |

Diese Bewertung ergänzt REQ-AI-001. DEC-034 bestätigt inzwischen vier V1-Anwendungsfälle; der [KI-Entwurf](AI_USE_CASES.md) grenzt sie ab. Weitere hier genannte KI-Tätigkeiten und die Anbieterreihenfolge sind damit nicht automatisch freigegeben.

## Fachliche Prüffälle

Alle Fälle sind geplante Produktprüfungen mit synthetischen Daten, keine bereits bestandenen Anwendungstests.

| Fall | Aktion | Erwartetes Ergebnis / Bezug |
|---|---|---|
| F-01 | Nordwerk-Kontakt eröffnet Anfrage und zweiter Nordwerk-Kontakt öffnet sie im freigegebenen Serviceprojekt. | Organisationssicht erfüllt; Südtechnik und interne Notizen bleiben verborgen. REQ-TKT-001, REQ-ACL-002 |
| F-02 | Externer setzt „Gelöst“ oder ein verbotenes internes Feld per API. | Ohne passendes Recht keine Teiländerung. REQ-ORG-003, REQ-ACL-003 |
| F-03 | Service verlangt „Interne Ursache“ erst beim Lösen. | Kundenanlage ohne dieses Feld möglich; internes Lösen ohne Ursache abgewiesen. REQ-ORG-003 |
| F-04 | Entwicklungsprojekt wechselt ohne Prüfanleitung in „In Prüfung“. | Übergang abgewiesen, bisheriger Stand erhalten. REQ-TKT-001 |
| F-05 | Administrator entfernt einen noch verwendeten Status. | Ohne geprüfte Zuordnung keine Migration der betroffenen Tickets. REQ-ORG-003 |
| F-06 | Auswahloption wird deaktiviert und später übersetzt. | Bestandswert bleibt nachvollziehbar, für neue Eingaben nicht mehr wählbar. REQ-ORG-003 |
| F-07 | Zwei Bearbeiter speichern denselben Ticketstand. | Zweiter veralteter Schreibversuch erzeugt Konflikt statt Überschreibung. REQ-TKT-005 |
| F-08 | Ein Regelereignis wird nach Neustart erneut bearbeitet. | Keine doppelte Fachwirkung und keine unbegrenzte Regelkette. REQ-ORG-003, REQ-TKT-005 |
| F-09 | A wird Unteraufgabe von B, B danach Unteraufgabe von A. | Zweite Aktion wegen Zyklus abgewiesen. REQ-TKT-002 |
| F-10 | Ticket mit Zeitbuchungen soll in anderes Projekt wechseln. | Im vorgeschlagenen V1-Umfang abgewiesen; keine stille Umbuchung. REQ-TIME-001 |
| F-11 | 90 + 30 Minuten, dann Korrektur 90 → 75, danach Storno von 30. | Summen 120 → 105 → 75 Minuten, vollständige Änderungshistorie. REQ-TIME-001, REQ-TIME-002 |
| F-12 | Nachbuchung auf geschlossenes Ticket in aktivem Projekt. | Mit Rechten erlaubt; Ticket und SLA bleiben geschlossen. Im archivierten Projekt abgewiesen. REQ-TIME-001 |
| F-13 | Kriterien einer freigegebenen Anforderung ändern sich. | Neue ungeprüfte Revision; alte Freigabe bleibt erhalten. REQ-REQ-002, DEC-030 |
| F-14 | DEV-42 ist erledigt, ANF-17 Revision 2 noch im Entwurf. | Keine automatische fachliche Freigabe oder Erfüllungszusage. REQ-REQ-003 |
| F-15 | Freigabe bei zwischenzeitlicher Änderung oder ohne Sicht auf notwendige Inhalte. | Keine Freigabe des ungesehenen Stands. REQ-REQ-002, REQ-ACL-003 |
| F-16 | Archivierung trotz offener SLA und ausstehendem Versand. | Zuerst nachvollziehbare Klärung erforderlich; keine still gestoppten Verpflichtungen. REQ-ORG-004 |

## Offene Punkte und Übergabe

OPEN-007 erhält hier konkrete Vorschläge, aber keine pauschale Freigabe. Feldtypen, Regelaktionen, Konfigurationsmigration, Kategorien und Umgang mit Verschieben müssen vor Implementierung entschieden werden. OPEN-021 ist durch DEC-030 bei Änderungen freigegebener Anforderungen teilweise geklärt; Zeitregeln, zusätzliche Standardfelder und Exportumfang bleiben offen.

TASK-006 konkretisiert Rückfragen, Nachrichtenwege und Servicefristen. TASK-007 entscheidet Editor und Vorlagen, TASK-009 Git-Objektarten und spätere Migration, TASK-010 Aufbewahrung und Betriebsgrenzen. Technische Prüfung und fachliche Abnahme sind noch ausstehend. Der Entwurf wählt keine Backend-Sprache und erteilt keinen Implementierungsauftrag.
