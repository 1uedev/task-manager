# Unabhängige Prüfung TASK-080 · PR #241

**Prüfsitzung:** `codex-20261102-portal-review-04`, unabhängige zweite KI nach DEC-040.

**Prüfdatum:** 3. November 2026. Geprüft auf aktuellem `main`, Commit `e450c1429f944a582ffcffa7b9f64d23d2e3fdda`. Der Produktcode entspricht [PR #241](https://github.com/1uedev/task-manager/pull/241), Mergecommit `42b1de4c3eeb127403908e52663e9b4bb439781c`; danach hat PR #242 ausschließlich TODO.md für A17 geändert.

**Empfehlung: nachbessern.** Ein Befund P1 und ein Befund P2 zur historischen Zielgruppe bleiben offen. Die Positionslösung, die Korrektur der Portalliste und der beauftragte Zusatz zur Testattrappe sind positiv geprüft.

Maßstab: AGENTS.md, TASK-080 in TODO.md, DEC-110, [die vorherige Prüfung](REVIEW_TASK-079_PR-239.md), der TASK-080-Abschnitt in backend/README.md sowie REQ-ACL-002 und die Portalvorgaben. Es gilt Holgers Entscheidung **„Beheben, unklare Altdaten verbergen“**. Ein geschätztes Zeitfenster ersetzt keinen belastbaren Nachweis der damaligen Zielgruppe.

Im separaten Prüfcheckout wurde ausschließlich dieser Bericht ergänzt. Produktcode, Tests, Verträge, Migrationen und Aufgabenstatus wurden nicht geändert. Zusätzliche Prüfprogramme lagen außerhalb des Checkouts und verwendeten eigene PostgreSQL-16-Datenbanken mit synthetischen Daten. Die fachliche Abnahme bleibt bei Holger.

| Prüfpunkt aus TASK-080 | Ergebnis |
|---|---|
| Ursprünglicher F1: historische Zielgruppen | Noch nicht vollständig behoben; siehe die Befunde F1 und F2 dieses Berichts. |
| Ursprünglicher F2: Lesestand nach Position | Positiv geprüft, einschließlich früher datierter, später sichtbarer Antwort und gemischter Schreiber. |
| Ursprünglicher F3: verspätete Folgeseite | Positiv geprüft, auch solange die neue erste Seite noch lädt. |
| Zusatz zu H3: Mailkunde und Positionen der Attrappe | Umgesetzt und durch Browserprüfungen belegt. |

## Befunde

### F1 · P1 · Das Ein-Minuten-Fenster beweist die historische Zielgruppe nicht

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0043__helpdesk_portal_audience_history.sql:27`, insbesondere `:31` und `:32`; entsprechender Schluss bei Dateien in `backend/src/main/resources/db/migration/V0044__files_portal_audience_history.sql:22` bis `:28`. Quellen der Zeitpunkte: `backend/src/main/java/taskmanager/helpdesk/internal/DirectoryService.java:139`, `:160`; ungesperrtes Lesen des Kontakts: `backend/src/main/java/taskmanager/helpdesk/internal/ReplyService.java:265`.

V0043 hält einen Kontaktwechsel nur dann für zeitlich unklar, wenn sein Auditzeitpunkt höchstens eine Minute vom Erzeugungszeitpunkt der Antwort entfernt liegt. Außerhalb dieses Fensters verwendet sie die letzte davor protokollierte Organisation. Der Auditzeitpunkt entsteht jedoch **vor dem Commit** der Kontaktänderung. Eine länger laufende Änderung kann beim Lesen des Kontakts noch unsichtbar sein, obwohl ihr protokollierter Zeitpunkt bereits mehr als eine Minute zurückliegt. V0044 macht beim früher nicht gemeinsam gesperrten Umschalten einer Datei denselben zeitlichen Schluss.

Die Anwendung begrenzt das Warten auf eine Datenbanksperre auf fünf Sekunden (`backend/src/main/resources/application.yaml:13`). Das begrenzt nicht die gesamte Transaktion, die Zeit bis zu ihrem Commit oder eine Ausführungsunterbrechung. Für eine maximale Minute zwischen den relevanten Ereignissen wurde keine durchgesetzte Grenze gefunden.

**Nachstellung mit echter Anwendung und HTTP:**

1. Eigene Datenbank bis V0042 gestartet; Kontakt und Ticket gehören zu Nordwerk. Mailantworten werden durch die Anwendung bereits mit ihrer festen Kundenkennung gespeichert.
2. Den Wechsel des Kontakts auf Südtechnik nach Vergabe des Änderungszeitpunkts durch einen ausschließlich in der Testdatenbank ergänzten Trigger für 70 Sekunden angehalten. Der Trigger wartet mit `pg_sleep`; es wurde kein Sperrzeitlimit abgeschaltet. Die alte Kontaktversion bleibt währenddessen für andere Leser sichtbar.
3. Nach 67 Sekunden regulär eine Mailantwort bestätigen lassen. Sie übernimmt korrekt den noch sichtbaren Nordwerk-Kontakt. Kontaktänderung und Bestätigung schließen beide erfolgreich ab. Der Abstand zwischen Auditzeitpunkt der Änderung und `reply.created_at` beträgt **67,165024 Sekunden**. Vor dem Upgrade enthält die Antwort korrekt Nordwerk als `customer_id`.
4. Die Antwort ausschließlich als synthetische Vorbereitung als gesendet markiert; es wurde keine echte Mail verschickt. V0043 bis V0045 angewendet. Danach enthält dieselbe Antwort **Südtechnik** als Kundenkennung.
5. Ticket über die API auf Südtechnik gewechselt und das Gespräch mit deren Portalkonto gelesen: HTTP 200, die alte Nordwerk-Antwort mit dem Testinhalt „Private North answer“ wird angezeigt.

Damit ist nicht nur ein theoretisch längerer Ablauf, sondern eine tatsächliche falsche Bindung beim Upgrade mit anschließendem Portalzugriff nachgewiesen. Für V0044 wurde zusätzlich ein synthetischer historischer Dateifall mit 120 Sekunden Abstand vorbereitet: Eine Umschaltung konnte vor dem Commit des früher datierten Kundenwechsels erfolgt sein. Die Migration bindet die Datei trotzdem an dessen neue Organisation, statt den nicht entscheidbaren Fall leer zu lassen. Dieser Dateifall wurde auf Migrationsebene geprüft, nicht mit einem laufenden alten Dateiserver.

**Auswirkung:** Eine Organisation kann nach dem Upgrade fremde historische Mailinhalte erhalten; der entsprechende Rekonstruktionsschluss für alte Dateiumschaltungen ist ebenfalls nicht belastbar. „Keine Änderung innerhalb einer Minute“ bedeutet nicht „keine konkurrierende Änderung“.

**Vorschlag:** Das feste Zeitfenster nicht als Sicherheitsnachweis verwenden. Für Vorgänge ohne gemeinsame Serialisierung nur Quellen auswerten, die die damalige Zielgruppe selbst oder eine verlässlich geordnete Veröffentlichung nachweisen. Lässt sich die tatsächliche Reihenfolge aus den vorhandenen Daten nicht entscheiden, Zielgruppe leer lassen. Sichere seit TASK-079 gespeicherte Bindungen anhand belastbarer Herkunft bewahren, ohne die falsche Rückfüllung von V0041/V0042 pauschal zu übernehmen. Die Nachstellung außerhalb des Fensters dauerhaft testen; eine größere willkürliche Zeitspanne schließt den Fehler nicht.

### F2 · P2 · V0043 löscht auch eine sichere neue Mailzielgruppe ohne jeden Organisationswechsel

Dieser Befund gehört ebenfalls zum ursprünglichen F1. Er betrifft **nicht** die neue Lesemarke nach Position.

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0043__helpdesk_portal_audience_history.sql:27` bis `:36`, besonders die Einbeziehung von `contact.created` in `:30`; korrekte Speicherung seit TASK-079 in `backend/src/main/java/taskmanager/helpdesk/internal/ReplyService.java:148`.

Das bedingungslose UPDATE berechnet jede vorhandene Mailantwort neu. Bereits die Anlage des Kontakts innerhalb der letzten Minute führt zu `customer_id = null`, auch wenn der Kontakt nie einer anderen Organisation angehörte. Gleiches gilt für Änderungen von Name oder Adressen ohne Organisationswechsel. Der Test auf Nähe eines beliebigen Auditereignisses unterscheidet diese sicheren Fälle nicht von einer möglichen Umbindung.

**Zusätzliche Nachstellung über die API auf V0042:** Einen Kontakt bei Nordwerk angelegt und 0,237904 Sekunden danach eine Antwort bestätigt. Kein Kontaktwechsel und keine Kontaktänderung, weder vorher noch danach; die Antwort speichert korrekt Nordwerk. Nach V0043 ist ihre Kundenkennung leer, obwohl das einzige Kontaktaudit die Anlage bei Nordwerk und der heutige Kontakt ebenfalls Nordwerk nennen.

**Auswirkung:** Beim Upgrade verschwinden korrekt geschriebene Antworten aus dem Portal beziehungsweise erreichen es bei späterem Versand nicht mehr. Beiträge und Mailantworten haben keinen nachträglichen Freigabeweg. Der Auftrag, bereits korrekt geschriebene Einträge nicht zu verfälschen, ist damit nicht erfüllt. Dieser Fall ist keine unklare historische Organisationszuordnung, die nach Holgers Entscheidung verborgen werden soll.

**Vorschlag:** Sichere Organisationshistorie von tatsächlichen Organisationswechseln unterscheiden. Eine Kontaktanlage oder eine reine Namens-/Adressänderung erzeugt für sich keine zweite mögliche Organisation. Beweisbar korrekt seit TASK-079 gespeicherte Bindungen erhalten; bei der Korrektur bereits angewendeter Migrationen einen neuen Korrekturpfad vorsehen, statt bestehende Prüfsummen zu ändern. Insbesondere „Kontakt neu angelegt, Antwort unmittelbar danach, kein Wechsel“ und „Änderung ohne Organisationswechsel“ als Regressionen aufnehmen.

## Hinweise

### H1 · Der Migrationstest prüft noch keine nach V0042 regulär geschriebenen Antworten

**Datei/Zeile:** `backend/src/test/java/taskmanager/PortalAudienceMigrationTest.java:34`, `:37`, `:38`, `:43`.

Der Test für bereits migrierte Installationen legt sämtliche Szenariozeilen vor V0041/V0042 an. Danach prüft er deren falsche erste Rückfüllung und die erneute Migration. Er erzeugt keine Antwort mit der neuen Anwendung zwischen V0042 und V0043. Die historischen Vorher-/Nachher-Fälle bestehen, belegen aber nicht den Erhalt neuer, korrekt eingefrorener Zielgruppen.

**Auswirkung:** Die grünen Migrationstests decken F1/F2 dieses Berichts nicht ab. **Vorschlag:** Den Versionsübergang auch mit regulär nach V0042 erzeugten Daten und mit gehaltenen beziehungsweise über eine Minute hinaus laufenden Vorgängen prüfen. Die eigenen zusätzlichen Versuche haben genau diese Lücke genutzt.

### H2 · Alte Lesestände werden bewusst mit ihrer bisherigen zeitlichen Aussage übernommen

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0045__helpdesk_portal_position.sql:17` bis `:26`; dokumentiert in `backend/README.md:291`.

Die Rückfüllung ordnet Supportbeiträge und gesendete Antworten je Ticket nach `(Zeitpunkt, Art, Kennung)` und setzt den Zähler auf die höchste Position. Ein alter Lesestand erhält die höchste Position mit Eintragszeitpunkt bis `seen_at`. Das ist intern konsistent und erhält die frühere zeitliche Semantik. Es kann aber eine schon vor diesem Upgrade fälschlich als gelesen behandelte Antwort nicht nachträglich als ungelesen erkennen; die damalige Sichtbarkeit wurde nicht gespeichert.

**Auswirkung:** Die Positionslösung verhindert den nachgestellten Fehler künftig, repariert aber keine historisch verlorene Neu-Markierung. **Vorschlag:** Diese dokumentierte Grenze bei der Abnahme mitführen und nicht als vollständige Bereinigung alter Lesestände darstellen. Daraus wird hier kein zusätzlicher Auftrag zum Zurücksetzen aller Lesemarken abgeleitet.

## Positive Prüfung der übrigen Änderungen

### Belastbare Teile der historischen Rückfüllung

- **Supportbeiträge:** Die Annahme der gemeinsamen Sperre ist im geprüften Schreibpfad richtig. `PortalConversation.answer` nimmt `lockTicketCustomer` vor dem Lesen der Zeit (`PortalConversation.java:320`, `:325`); `TicketCustomerService.set` nimmt dieselbe transaktionale Beratungssperre (`TicketCustomerService.java:135`) und liest die Zeit erst nach der Ticketsperre. V0043 kann deshalb bei fortschreitender Anwendungszeit die geordnete Kundenhistorie verwenden. Fehlende Vorgeschichte und ein exakt gleicher Zeitpunkt bleiben verborgen. Die anfängliche Kundenzuordnung eines neuen Tickets entsteht in dessen Erzeugungstransaktion.
- **Benannte Dateifreigabe:** `attachment.audience` enthält die ausdrücklich gebundene Organisation und wird in V0044 gegenüber älteren Quellen bevorzugt. Die Anhangsperre ordnet die Auditeinträge derselben Datei; die Auswahl nach Auditkennung entspricht hier der Reihenfolge der Handlungen. Bei Dateien ohne bewusste spätere Umschaltung sind die feste Organisation des Portalkontos und die Kundenkennung der zugehörigen Mail die vorgesehenen Quellen. Interne Dateien bleiben intern. Die bestandenen Migrationsfälle erfassen diese Wege.
- **Upgradepfad:** V0041 und V0042 sind unverändert; V0043/V0044 berichtigen auch bereits angewendete erste Rückfüllungen. Die Strategie, alle Zeilen neu zu berechnen, ist jedoch wegen F1/F2 nicht in allen Fällen korrekt. Die neuen Filter des Portals können eine falsch rekonstruierte Zielgruppe nicht erkennen.

### Lesestand, Positionen und Sperren

Die neue Lösung ist im geprüften Umfang korrekt:

- `HelpdeskRepository.nextPortalPosition` (`:421`) verwendet ein transaktionales `INSERT ... ON CONFLICT ... UPDATE ... RETURNING`. Die eindeutige Zählerzeile je Ticket bleibt bis zum Commit gesperrt. Ein weiterer Schreiber kann die nächste Position erst nach Ende dieser Transaktion vergeben. Abgebrochene Änderungen am Zähler werden mit zurückgerollt. Ein gewöhnlicher Leser sieht bei READ COMMITTED nur den veröffentlichten Zählerstand, ohne auf eine noch laufende Zähleränderung zu warten.
- Alle drei Veröffentlichungswege verwenden diesen Zähler in der Transaktion der Antwort: Portalantwort (`PortalConversation.java:327`), erfolgreicher Sendeauftrag (`ReplySender.java:204`) und Vermerken als gesendet (`ReplyService.java:196`). Die bestehenden SMTP-Tests prüfen beide Mailwege; 503 Backendtests einschließlich des gehaltenen Support-Inserts bestehen.
- Das Gespräch liest den Zähler **vor** den Einträgen (`PortalConversation.java:233`). Eine später gelesene Antwort mit höherer Position wird gegebenenfalls bereits angezeigt, erhält in diesem Ergebnis aber keine quittierbare Position (`:280`). Sie bleibt damit vorsichtshalber neu. Das entspricht dem beschriebenen Vertrag; die Aussage „nur Positionen bis dahin“ wird auf die ausgegebenen Positionswerte angewendet, nicht als Ausschluss jedes später sichtbar gewordenen Textes.
- `seen` begrenzt die übermittelte Position auf den veröffentlichten Zählerstand (`:275`). `PortalRepository.seen` erhöht den Stand nur mit `greatest`; Aktivität vergleicht die höchste Position der **eigenen Organisation** mit dem quittierten Stand. Fehlende oder negative Positionen ergeben 400 `position.invalid`; `until` genügt nicht mehr.
- **Zusätzliche Gegenprüfung der Lesegrenze:** Nach dem Lesen von Zähler 1 die Gesprächsabfrage an der Beitragstabelle angehalten. Währenddessen eine Mailantwort regulär als gesendet vermerkt, Position 2. Die fortgesetzte Abfrage liefert ihren Text mit `position: null`, die alte Antwort mit Position 1. Quittieren von 1 lässt die neue Mailantwort ungelesen.
- **Zusätzliche Gegenprüfung gemischter Schreiber:** Portalantwort nach Vergabe ihrer Position vor dem Insert gehalten; eine gleichzeitig als gesendet vermerkte Mail wartet nachweislich an der Zählerzeile. Der laufende Leser sieht noch Zähler 2; nach Freigabe erhalten Portal- und Mailantwort die Positionen 3 und 4. Beide schließen erfolgreich ab, der Zähler endet bei 4.
- Die neuen Schreibwege ordnen den Zähler vor dem nachgelagerten SLA-Ereignis ein. Portalantworten halten vorher die Kunden- und Ticketsperren; Mailabschlüsse die Antwortzeile. Im untersuchten Aufrufgraphen und den beiden gezielten Gegenläufen wurde keine neue umgekehrte Abhängigkeit festgestellt. Das ist keine vollständige Prüfung jeder Abbruch-/Sperrkombination.

### Portalliste, Attrappe, Vertrag, Rechte und Umfang

- **F3:** `frontend/src/pages/PortalPage.tsx:160` bildet den Abfrageschlüssel aus Projekt und allen Filtern; bei dessen Wechsel wird die Generation bereits beim Rendern ungültig. `restart` wird auch vor erneutem Laden aufgerufen (`:174`). Ergebnisse und Fehler alter Folgeseiten bestehen den Generationsvergleich nicht mehr. Der gezielt nachgezogene Browsertest hält alte Folgeseite und neue erste Seite gleichzeitig; die alte Seite wird auch bei noch laufender neuer erster Seite nicht angehängt. Die bisherige erste Seite bleibt währenddessen wie im Test vorgesehen sichtbar; das ist kein erneutes Auftreten des beauftragten Folgeseitenfehlers.
- **H3-Zusatz:** Die Attrappe hält `customerId` bei Bestätigung und in `addReply` fest (`frontend/tests/browser/backend.mjs:1467`, `:1585`), verwendet diese beim Portalabruf und zählt Positionen beim Veröffentlichen. Der Browserfall mit Kontaktwechsel besteht. Eine synchrone Speicherattrappe belegt keine Datenbank-Commitreihenfolge; diese wurde deshalb separat am Backend geprüft.
- **Vertrag:** `contracts/helpdesk.openapi.yaml:799` verlangt die nichtnegative Ganzzahl `position`; `PortalEntry.position` ist optional/nullable (`:912`). Controller, generierte Typen, Aufruf und Oberfläche sind abgestimmt. Die Oberfläche quittiert die höchste gezeigte Position (`PortalPage.tsx:384`) und nimmt einen fehlgeschlagenen Quittierungsversuch zurück. Lediglich der Kommentar in `frontend/src/api/product.ts:289` nennt noch `until`; der ausführbare Aufruf unmittelbar darunter sendet korrekt `position`.
- **Rechte:** Die neuen Positionsabfragen laufen nach der bestehenden Portalprüfung. Organisations-, Projekt-, Aktions- und Feldrechte wurden nicht durch einen zweiten Zugang ersetzt. Aktivität und Verlauf filtern weiter auf die gespeicherte Organisation. Die interne und die Portaloberfläche verwenden die vorhandenen Rechteinformationen; eine Ausweitung der Portalrechte oder der Suche wurde nicht eingeführt. F1 betrifft falsche Datenbindung nach Migration, keinen fehlenden Laufzeitfilter.
- **Migrationszahlen:** 42 versionierte Migrationen bis V0045. Versionsliste und Zahl in `backend/scripts/lifecycle-check.sh:89`/`:92`, `DatabaseFoundationTest.java:36` und `ApplicationLifecycleTest.java:34` passen dazu. Auch `helpdesk_portal_position` wurde in die erwarteten Tabellenlisten aufgenommen. Die temporäre Nummerierungstabelle aus V0045 bleibt nach der Migration nicht bestehen.
- **Umfang:** Die 26 geänderten Dateien von PR #241 gehören zur beauftragten Korrektur und ihren Verträgen, Tests und Nachweisen. Keine sachfremde Produktfunktion festgestellt. A17 aus der nachfolgenden Reservierung wurde nicht geprüft. Der ursprüngliche Skalierungshinweis H4 bleibt wie beauftragt offen.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | Bestanden; unter Windows mit dem verfügbaren Python 3.12 als `python` ausgeführt. |
| `python3 scripts/task_board.py --check` | Bestanden, Aufgabenübersicht aktuell; ebenfalls `python`. |
| `python3 -m unittest discover -s tests -v` | 38 Tests, erfolgreich; ein ausdrücklich Unix-spezifischer Zeitzonentest unter Windows übersprungen. Eigener temporärer Ordner und lokale Testverbindungen. |
| Backend `./mvnw verify` | **BUILD SUCCESS**, 503 Tests, keine Fehler und keine übersprungenen Tests, 12 min 22 s. Windows-Aufruf über `mvnw.cmd`, Java 25.0.4.1, Maven 3.9.16, eigene leere PostgreSQL 16.15, vorhandener Abhängigkeitscache. |
| Frontend `npm ci` | Erfolgreich nach Wiederholung mit den erforderlichen Dateirechten; der erste eingeschränkte Versuch wurde vom Betriebssystem abgewiesen. Node 24.22.0 / npm 11.19.1, vorhandener Cache. |
| Frontend `npm run verify` | Bestanden: Vertragstypen neu erzeugt, Typprüfung, 77 Tests und Produktionsbuild. Nur die bekannte Warnung zur Paketgröße. Generierte Dateien gegenüber Git unverändert. |
| Frontend `npm run test:browser` | Erster vollständiger Lauf: 170 Fachtests bestanden, zusätzlich Fehler der LDAP-Testdatei durch asynchrone Aktivität nach Ende des `before`-Hooks (`browserContext.cookies` nach geschlossenem Browser). Deshalb kein vollständig grüner Erstlauf. Vollständige Wiederholung derselben Dateien mit `node --test --test-concurrency=1 tests/browser/*.test.mjs`: **170/170 erfolgreich**, 33 Gruppen, keine übersprungenen Tests. Kein Produkt- oder Testcode dafür geändert. |
| Eigene Migrations-/API-Prüfungen | F1 über echte gehaltene Kontaktänderung, API-Bestätigung, Upgrade und anschließenden fremden Portalzugriff reproduziert; unsichere Dateirückfüllung separat mit synthetischer Historie geprüft. F2 zusätzlich mit frisch angelegtem, nie verschobenem Kontakt geprüft. Lesegrenze und gemeinsame Positionsvergabe über echte API-Aufrufe mit gehaltenen Datenbankschritten erfolgreich geprüft. |
| Abschließende Repositoryprüfung | Repositoryvalidierung und Aufgabenübersicht auch mit Bericht erfolgreich geprüft. `git diff --check` sowie die gesonderte Leerraumprüfung der neuen Datei bestehen. Keine verfolgte Datei verändert; einzige neue Datei im Checkout ist dieser Bericht. |

Nicht ausgeführt: Linux-Skript `backend/scripts/lifecycle-check.sh`, Docker-/Vorführimage mit vollständiger Datenbefüllung, der in CI festgelegte PostgreSQL-18.6-Container, Browserdurchlauf mit dem echten Backend statt Attrappe, Lastmessung und vollständige Sperr-/Abbruchmatrix. Das Lifecycle-Skript wurde auf Versionsliste, Zahl und erwartete Tabellen geprüft; das ersetzt seine Ausführung nicht. Es wurden keine echten Mails verschickt und keine realen Kundendaten verwendet.

## Empfehlung

**TASK-080 nachbessern, vorerst nicht abnehmen.** Die historische Zielgruppe darf weder aus einem unbelegten Zeitfenster abgeleitet noch in eindeutig sicheren Fällen gelöscht werden. F1 und F2 dieses Berichts mit dauerhaften Regressionen korrigieren und danach erneut unabhängig prüfen. Die Positionslösung, die Folgeseitenkorrektur und der H3-Zusatz können als positiv geprüft gelten; die vollständigen vorhandenen Prüfketten sind mit der genannten Einschränkung des ersten Browserlaufs erfolgreich abgeschlossen.
