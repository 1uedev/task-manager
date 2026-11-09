# Nachprüfung R1 von TASK-092 / PR #274

**Ergebnis: R1 ist im geprüften Umfang behoben. Keine offenen Pflichtbefunde.** Die wiederholten Gleichzeitigkeitstests liefern konsistente Summen und vollständige Ergebnisse beziehungsweise eine ausdrücklich gekürzte Liste. Der Export antwortet bei 50.001 Treffern mit 400 oder liefert einen vollständigen älteren Datenstand mit 50.000 Treffern. Eine erfolgreiche, still gekürzte CSV wurde nicht mehr reproduziert. Ein nicht blockierender Leistungshinweis H1 bleibt.

## Auftrag und Stand

- Unabhängige Sitzung `codex-20261109-task092-r1-review-01`, 9. November 2026, eigene Arbeitskopie; getrennt vom Autor `claude-20261108-timefix-01` (DEC-040).
- Repository `1uedev/task-manager`, [PR #274](https://github.com/1uedev/task-manager/pull/274); geprüfter Stand **`74e3cc1c561f50ecc753d6714c44b3e1dbcd8fee`**, R1-Korrektur **`514cfe7`**. Funktionaler Vergleich mit dem zuvor beanstandeten Stand `e9092ab`.
- Der PR war bei Beginn dieser Nachprüfung bereits zusammengeführt: Mergecommit `aa1030c82564834277249cfdf473e551bb3c4ec4`. Der Branch `claude/project-thread-thp5tg` war gelöscht; der gewünschte Prüfstand wurde über `refs/pull/274/head` geladen. Geprüft wurde der angegebene Commit, nicht ersatzweise der Mergecommit.
- Gelesen: `AGENTS.md` (gegenüber der vorherigen Prüfung unverändert), TASK-092 in `TODO.md`, DEC-122 einschließlich des Nachtrags vom 9. November 2026 und [der R1-Befund](REVIEW_TASK-092_PR-274.md). Ergänzend wurden die betroffenen Vertrags- und README-Passagen abgeglichen.
- Umfang ausschließlich R1: Datenstand der Zeitabfragen, Exportgrenze, Erhalt der bisherigen Filter-/Summenwerte sowie Abfrageplan und Laufzeit. Keine erneute allgemeine Prüfung anderer Funktionen und keine fachliche Abnahme an Holgers Stelle.
- Im geprüften Checkout wurde nur dieser Bericht angelegt. Produktcode, Aufgabenlisten und der vorherige Bericht blieben unverändert. Zusätzliche Testquellen, Messbestände und Pläne entstanden außerhalb dieses Checkouts in einer separaten Testkopie.

## Prüfung der Korrektur

**Orte:** `backend/src/main/java/taskmanager/time/internal/TimeRepository.java:122–181`, `backend/src/main/java/taskmanager/time/internal/TimeService.java:236–277`; Exportablehnung weiterhin `backend/src/main/java/taskmanager/time/web/TimeController.java:190`.

`listing()` stellt zunächst den berechtigten Projektumfang samt projektabhängigem Personen- und Ticketfilter zusammen. Danach erfolgt genau ein Aufruf von `snapshot()`. Die materialisierte CTE `m` liest die passenden Buchungen aller dieser Projekte; die drei `UNION ALL`-Zweige liefern Projekt-/Person-/Ticketaggregate, Tagesaggregate und begrenzte Einzelzeilen aus dieser gemeinsamen Menge. Es gibt keine getrennten Zeitabfragen je Projekt oder je Summenart mehr.

Für Listen werden höchstens 501 Einzelzeilen gelesen und anschließend höchstens 500 ausgegeben. Für Exporte wird der Einzelzeilenzweig nur bei `count < 50001` freigegeben. Zusätzlich berücksichtigt `truncated = count > limit || found.size() > limit` auch eine tatsächlich gelesene Überschreitung. Die Exportantwort prüft dieses Flag vor der CSV-Erzeugung.

Die Isolation bleibt READ COMMITTED. Das passt zur unveränderten Prüfung in `backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java:442–444`, die andere Isolationsstufen ablehnt. Die Behebung verändert diese Rechtesicherung nicht.

## Wiederholte Reproduktionen

### 1. Buchung während einer Listenabfrage

Die früheren Anhaltepunkte zwischen `sums()`, `days()` und `entries()` existieren nicht mehr. Deshalb wurden zwei zeitliche Lagen geprüft:

1. **Nach Rechteprüfung, vor der SQL-Anweisung:** Der neue Test `TimeApiTest.countSumsDaysAndRowsOfAListFitEachOther` hält über `HeldTimeRepository` an, bestätigt eine weitere Buchung und setzt dann die Abfrage fort. Ergebnis: vier Buchungen, Gesamt-, Personen-, Tages- und Zeilenminuten jeweils **187**, keine Kürzung.
2. **Während der bereits laufenden SQL-Anweisung:** Eine unabhängige Gegenprobe ergänzte testseitig eine Datenbankschranke: CTE `review_gate` mit `pg_advisory_xact_lock`, die vor dem Lesen von `m` wartet. Über `pg_stat_activity` wurde der tatsächliche Wartezustand der SQL-Anweisung festgestellt. Anschließend wurde über den regulären Buchungsendpunkt eine weitere Minute gespeichert (**201**) und die Schranke gelöst. Es wurden keine Ergebniswerte simuliert; die Produktionsdateien wurden nicht geändert.

Bei **499 Ausgangsbuchungen** enthielt die bereits gestartete Antwort unverändert **499 Buchungen und 499 Minuten**: `totalMinutes`, Summe `perDate`, Summe `perPerson`, Summe `perTicket` und Summe der Einzelzeilen waren gleich. Die nächste normale Abfrage enthielt **500** Buchungen/Minuten. Pro Antwort wurde genau eine Snapshot-Anweisung beobachtet.

Die zusätzliche Wiederholung mit **501 Ausgangsbuchungen** ergab konsistent `count=501`, `totalMinutes=501` und 501 Tagesminuten; die nächste Abfrage enthielt 502 Minuten. Die ausgelieferte Liste hatte jeweils höchstens 500 Zeilen und `truncated=true`.

**Einordnung der Zeilensumme:** Bei einer vollständigen, nicht stornierten Liste gilt Gesamt = Tagesminuten = Zeilenminuten. Bei einer ausdrücklich gekürzten Liste gelten die Aggregate weiterhin für alle Treffer; ihre Summe ist dann größer als die Summe der ausgelieferten 500 Zeilen. Auch aufgelistete Stornos dürfen nicht zu wirksamen Zeilenminuten addiert werden. Diese vereinbarten Unterschiede sind keine Inkonsistenz aus R1.

### 2. Export an der Grenze 50.000 + 1

| Zeitpunkt der bestätigten zusätzlichen Buchung | Beobachtetes Ergebnis |
|---|---|
| Vor Beginn der Snapshot-Anweisung, während der Aufruf bereits nach der Rechteprüfung angehalten ist | Neuer Test `anExportIsRefusedWhenABookingMakesItTooLargeWhileItRuns`: **400 `time.export_too_large`** bei 50.001 Treffern. |
| Während der bereits gestarteten SQL-Anweisung, kontrolliert über dieselbe Datenbankschranke | **200 mit genau den vollständigen 50.000 ursprünglichen Datenzeilen**, CSV-Summe 50.000 Minuten. Die nachgebuchte Zeile auf einem anderen Datum und mit eindeutigem Beschreibungstext fehlte vollständig. Der Buchungsaufruf selbst war mit 201 erfolgreich. |
| Unmittelbar folgende normale Exportanfrage | **400 `time.export_too_large`**. |

Damit bestehen beide zulässigen Ergebnisse: vollständiger alter Snapshot oder Ablehnung des größeren neuen Snapshots. Das frühere Ergebnis „200 mit einer neuen Zeile und einer verdrängten alten Zeile“ trat nicht mehr auf.

Die zusätzlichen Schranken verändern ausschließlich die Testanweisung um den Wartepunkt. Die unveränderte Produktionsanweisung wurde zusätzlich durch die gelieferten API-Tests, die normalen Folgeaufrufe und sämtliche nachstehenden Plan-/Laufzeitmessungen ausgeführt.

## Unveränderter Bestand und Filter

Die vollständigen vorhandenen Zeit-API-Tests bestanden, darunter die Mengenränder 500/501 und 50.000/50.001, mehrere Projekte, eingeschlossene Stornos, `mine` auch mit `time.read`, verborgene Tickets einschließlich Korrektur/Entfernung sowie Portal- und Fremdprojektabgrenzung.

Zusätzlich wurde dieselbe unabhängige Mischprobe wie im vorherigen Review erneut ausgeführt:

| Synthetischer Bestand | Bestätigte Minuten |
|---|---:|
| Projekt A, Person 1, Ticket 1: 30.000 × 2 Minuten am 1. September | 60.000 |
| Projekt A, Person 2, Ticket 2: 20.001 × 3 Minuten am 2. September | 60.003 |
| Projekt B, Person 1, ohne Ticket: 7 × 5 Minuten am 3. September | 35 |
| Projekt B, Person 2: 600 Stornos × 1.440 Minuten am 1. Oktober | 0 |

Unverändert bestätigt: `count=50608` mit Stornos, sonst 50.008; `totalMinutes=120038`; Personenwerte 60.035/60.003, Ticketwerte 60.000/60.003/35 und entsprechende Tageswerte ohne Stornotag. Personen-, Ticket- und Datumsfilter bestanden. Nach Entzug von `ticket.read` enthielten die beiden Projektgruppen keine sichtbare Ticketreferenz; Gesamtwerte blieben erhalten. Die zusätzliche Maskierungsprobe bestätigte weiterhin Erhalt bei leerem oder weggelassenem Ticketfeld, bewusste Entfernung und fehlende Offenlegung von Titel/UUID in Liste, Detail, Revisionen und CSV.

Der vorhandene `mine`-Test bestätigte weiterhin **501 eigene Wochenminuten**, Tageswerte **300 und 201**, Ausschluss von fremden Minuten, Stornos und Buchungen außerhalb der Woche sowie 400 `person.with_mine` bei einer anderen Person.

## Abfrageplan und Laufzeit

Gemessen wurde die **unveränderte, vom Repository erzeugte SQL-Anweisung** mit gebundenen Parametern. Je Fall: isolierte Datenbank mit der echten V0053-Tabellen-/Indexdefinition, `ANALYZE`, ein Warmlauf und fünf serielle Messläufe. Zusätzlich `EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)`. Die Planabfrage ist eine eigene, zusätzliche Ausführung.

Gemeinsame Bedingungen: PostgreSQL **16.15**, READ COMMITTED, `work_mem=4MB`, JIT eingeschaltet; die gemessenen Pläne enthielten keine JIT-Kompilierung. Jeweils **insgesamt** 50.000 Buchungen, gleichmäßig auf 1, 100 oder 1.000 Projekte verteilt, 30 Leistungsdaten, eine Person, ohne Ticket, Minuten 1–8, Beschreibungen mit 80 Zeichen. Ohne einzelnen Projektfilter enthält die Anweisung alle genannten Projektscopes. Die SQL-Liste liest bis zu 501 Einzelzeilen; der Export bei genau 50.000 Treffern alle 50.000.

| Fall | PostgreSQL-Planung ms | PostgreSQL-Ausführung ms | Repository-Median ms, fünf Läufe | Repository-Spanne ms |
|---|---:|---:|---:|---:|
| 1 Projekt, Liste | 5,6 | 161,5 | 225,0 | 216,8–234,3 |
| 1 Projekt, Export | 8,5 | 272,5 | 657,6 | 609,2–743,2 |
| 100 Projekte, Liste | 4,8 | 179,9 | 220,7 | 212,8–259,3 |
| 100 Projekte, Export | 5,7 | 341,8 | 661,5 | 652,1–748,6 |
| 1.000 Projekte, Liste | 10,9 | 467,7 | 536,2 | 501,7–650,7 |
| 1.000 Projekte, Export | 14,9 | 634,9 | 1.438,8 | 1.394,0–1.724,0 |
| 100 Projekte, Liste, **1.000 Zeichen Beschreibung** | 4,7 | 427,2 | 439,3 | 404,3–502,0 |
| 100 Projekte, **50.001 Treffer**, Exportablehnung | 4,2 | 132,2 | 178,2 | 171,3–234,9 |

Repository-Zeiten enthalten JDBC, Übertragung und Aufbau der Java-Ergebnisse. Rechteprüfung, HTTP-/JSON- beziehungsweise CSV-Erzeugung sind darin nicht enthalten. Die Vorbereitung der vielen Testprojekte ist ebenfalls nicht Teil dieser Messwerte.

**Planbewertung:**

- `time_entry` wird für `m` einmal gelesen: sequenzieller Scan, bei den projektübergreifenden Fällen teilweise als paralleler Scan mit `Gather`. Bei einer Auswahl aller 50.000 Tabellenzeilen ist dieser Plan plausibel; daraus folgt kein fehlender Index.
- Die materialisierte Menge wird anschließend für Projekt-/Person-/Ticketaggregate, Tageswerte und Einzelzeilen verwendet. Die Liste sortiert die Einzelzeilen per `top-N heapsort`; der Export mit 50.000 Zeilen verwendet bei 4 MB Arbeitsspeicher `external merge` auf temporären Dateien.
- Beim übergroßen Export enthält der Zeilenzweig **null Einzelbuchungen**; der darunterliegende CTE-Scan des Zeilenzweigs hat null Ausführungen. Anzahl und Aggregate werden weiterhin berechnet. Die Basisdaten werden dafür vollständig gelesen und materialisiert: siehe H1.

**Zusätzliche echte API-Messung ohne Projektfilter:** Für einen berechtigten internen Leser wurden 100 tatsächliche Projekte mit je 500 Buchungen angelegt. `GET /api/v1/time/entries` lieferte pro Antwort 50.000 Treffer/Minuten, vollständige Tageswerte, 100 Projektgruppen und 500 Zeilen mit Kürzungsflag. Es wurde jeweils genau eine Snapshot-Anweisung beobachtet. Nach einem Warmlauf dauerten drei vollständige API-Aufrufe **935,3 / 967,3 / 1.172,2 ms**. Diese Zeiten schließen die Rechteprüfungen und die Antwortverarbeitung ein. Ein vollständiger HTTP-Aufruf über 1.000 Projekte wurde nicht gemessen.

Die Werte sind lokale Vergleichsmessungen auf synthetischen Daten, kein Nachweis für PostgreSQL 18.6 oder für parallele Produktionslast. Bestände mit sehr vielen unterschiedlichen Personen-/Ticketgruppen wurden in der Laufzeitmessung nicht abgebildet. Ein bestätigter Laufzeitgrenzwert für diese Abfrage ist im Prüfmaßstab nicht festgelegt.

## Befunde

**Pflicht:** Keine. R1 kann technisch geschlossen werden.

### H1 · Nicht blockierend · Vollständige Materialisierung verursacht temporäre Dateizugriffe

**Ort:** `TimeRepository.java:161–169`, insbesondere `select` aller Buchungsspalten einschließlich `description` in `m`.

Schon die Listenmessung mit 50.000 Buchungen und 80 Zeichen Beschreibung meldete **1.429 geschriebene und 2.858 gelesene temporäre Blöcke**. Bei 1.000 Zeichen waren es **7.044 geschriebene und 14.088 gelesene Blöcke**, entsprechend rund 55 MiB Schreiben und 110 MiB Lesen bei 8-KiB-Blöcken. Der Listenmedian über 100 Projekte stieg dabei von 221 auf 439 ms. Die kleinen Hashaggregate beziehungsweise der begrenzte Zeilensortierer waren nicht die Ursache dieser vollständigen Materialisierung.

Auch bei 50.001 Treffern, deren Export abgelehnt wird, wird die breite Grundmenge für die Aggregate materialisiert. Die Einzelzeilen werden dann nicht an Java zurückgegeben; ein Verzicht auf das Lesen der Basisdaten folgt daraus nicht.

**Empfehlung:** Bei einer späteren Leistungsoptimierung eine schmalere gemeinsame CTE für Kennungen, Gruppierung, Minuten und Sortierung untersuchen und die Beschreibungen nur für die ausgewählten Einzelzeilen **innerhalb derselben SQL-Anweisung** ergänzen. Den nun geschlossenen Snapshotfehler dabei nicht wieder öffnen. Die gemessenen Kosten ergeben hier keinen zusätzlichen Pflichtbefund, sollten aber vor einer Leistungszusage auf der gepinnten Version und mit repräsentativer Datenverteilung nachgemessen werden.

## Gelaufene Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Repositoryvalidator und Aufgabenübersicht | Erfolgreich, einschließlich fertigem Bericht; Aufgabenlisten unverändert. |
| `python -m unittest discover -s tests -v` | 38 Tests erfolgreich, ein unter Windows vorgesehener Zeitzonenwechseltest übersprungen. |
| `git diff --check` und zusätzliche Leerraumprüfung des neuen Berichts | Erfolgreich. |
| `mvn -o -Dtest=TimeApiTest,TimeContractTest test` mit expliziter lokaler Datenbank | **14 Tests erfolgreich**, keine Fehler, Fehlschläge oder übersprungenen Tests. Beide neuen R1-Tests enthalten. |
| Unabhängiger `Time092R1ReviewProbe` in separater Testkopie | **Sieben Tests erfolgreich**, keine Fehler/Fehlschläge: drei SQL-Gleichzeitigkeitsfälle, zwei unveränderte Misch-/Maskierungsproben, Plan-/Laufzeitserie und echte API über 100 Projekte. |
| Plan-/Laufzeitserie | Acht Fälle, je ein Warmlauf, fünf Messläufe und ein tatsächlicher `EXPLAIN ANALYZE`; Werte und Planmerkmale oben. |

**CI für exakt `74e3cc1` separat abgefragt:** [Backend](https://github.com/1uedev/task-manager/actions/runs/42607686118), [Frontend](https://github.com/1uedev/task-manager/actions/runs/42607686126), [Repositoryprüfungen](https://github.com/1uedev/task-manager/actions/runs/42607686129), [technische Qualifikation](https://github.com/1uedev/task-manager/actions/runs/42607686209) und [Image](https://github.com/1uedev/task-manager/actions/runs/42607686186) erfolgreich. Diese Ergebnisse sind ergänzende CI-Evidenz, keine eigenen lokalen Ausführungen dieser vollständigen Workflows.

**Umgebung und Grenzen:** Windows, Java 25.0.4.1, Maven 3.9.16 und eine neue, isolierte PostgreSQL-16.15-Testinstanz mit synthetischen Daten. Docker war lokal nicht verfügbar. Kein erneuter vollständiger Backend-, Frontend- oder Browserlauf, kein Docker-/Paket-Lifecycle-, Last- oder Wiederherstellungstest. Die Nachprüfung ist gezielt auf R1 begrenzt; die fachliche Abnahme von TASK-069/TASK-092 bleibt bei Holger.
