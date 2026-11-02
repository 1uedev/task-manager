# Unabhängige Prüfung TASK-079 · PR #239

**Prüfsitzung:** `codex-20261102-portal-review-03`, unabhängige zweite KI nach DEC-040.

**Stand:** 2. November 2026, `main`, Commit `a5b03ed88595cd635f4de5af4e68eb299c51d4d8`, [PR #239](https://github.com/1uedev/task-manager/pull/239).

**Empfehlung: nachbessern.** Ein Befund P1 und zwei Befunde P2 bleiben offen. F4 und F5 sowie die beauftragten Korrekturen zu H1 bis H3 sind umgesetzt; bei der Testattrappe bleibt eine zusätzliche Abweichung innerhalb der Zielgruppenprüfung.

Maßstab sind AGENTS.md, TASK-079 in TODO.md, DEC-109, [die vorherige Prüfung](REVIEW_TASK-076-078_A11.md) und der TASK-079-Abschnitt in backend/README.md. Insbesondere gilt Holgers Entscheidung **„Nur neue Einträge“** auch für die Bewertung der Rückfüllung: Der aktuelle Ticketkunde allein ist kein Nachweis, dass alte Inhalte für ihn entstanden sind. Die in der Ergebnisdokumentation genannten Grenzen sind keine zusätzliche Freigabe Holgers für abweichendes Verhalten.

Geprüft wurden alle 48 geänderten Dateien und die betroffenen Lese-, Schreib-, Rechte- und Migrationspfade. Im separaten Prüfcheckout wurde ausschließlich dieser Bericht ergänzt. Produktcode, Tests, Verträge und Aufgabenstatus wurden nicht geändert. Zusätzliche Nachstellungen lagen außerhalb des Checkouts und verwendeten ausschließlich synthetische Daten und eigene Testdatenbanken. Die fachliche Abnahme bleibt bei Holger.

## Befunde

### F1 · P1 · Die Rückfüllung überträgt historische Inhalte ohne Freigabe an den neuen Kunden

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0041__helpdesk_portal_audience.sql:12`, `:15`; `backend/src/main/resources/db/migration/V0042__files_portal_audience.sql:14`. Der Migrationstest schreibt dieses Verhalten in `backend/src/test/java/taskmanager/PortalAudienceMigrationTest.java:68` und `:73` sogar als Erwartung fest. Die bekannte Grenze steht in `backend/README.md:293`.

V0041 bindet sämtliche vorhandenen Supportbeiträge an den **heutigen** Ticketkunden. V0042 macht dasselbe mit kundensichtbaren Dateien, deren Zielgruppe weder über ein Portalkonto noch über eine Mail ermittelt wurde. Ein vor dem Upgrade erfolgter Kundenwechsel macht dadurch alte Supportbeiträge und intern für den früheren Kunden freigegebene Dateien für die neue Organisation sichtbar, ohne erneutes PATCH und ohne `attachment.audience`-Freigabe.

Auch die Rückfüllung der Mailantworten ist nicht durchgehend historisch: V0041 verwendet die heutige Organisation des Kontakts. Kontakte können ihre Organisation über `DirectoryService.updateContact` ändern (`backend/src/main/java/taskmanager/helpdesk/internal/DirectoryService.java:139`). Wurde der Kontakt vor dem Upgrade verschoben, wird seine alte Mailantwort der neuen Organisation zugerechnet.

**Nachstellung:** Eine eigene Datenbank bis V0040 migriert. Während Nordwerk Ticketkunde ist, einen Supportbeitrag, eine kundensichtbare interne Datei und eine gesendete Antwort an einen Nordwerk-Kontakt vorbereitet. Anschließend Ticket und Kontakt auf Südtechnik geändert, ohne die Datei erneut freizugeben. Erst danach V0041 und V0042 angewendet. Ergebnis: `organization_id` des alten Beitrags und der alten Datei sowie `customer_id` der alten Mailantwort enthalten sämtlich Südtechnik. Die neuen Portalfilter lassen genau diese Zielgruppe passieren. Der Mailfall mit Kontaktwechsel ist zusätzlich zum bloßen Ticketwechsel zu beachten.

**Auswirkung:** Historische Korrespondenz und Dateien können beim Upgrade an einen anderen Kunden gelangen. Der Kern von F1 bleibt für Bestandsdaten offen. Die dauerhafte Speicherung einer Zielgruppe für künftig erzeugte Daten ist dagegen richtig umgesetzt.

**Vorschlag:** Zielgruppen nur aus belastbaren historischen Quellen rekonstruieren, etwa den zeitlich passenden Kunden- und Kontaktänderungen beziehungsweise Freigaben. Wo die frühere Zielgruppe nicht eindeutig feststeht, konservativ `null` verwenden; eine Datei dann erst durch ausdrückliche Freigabe an den aktuellen Kunden binden. Bereits migrierte Installationen brauchen einen passenden Korrekturpfad, ohne angewendete Migrationsprüfsummen still zu ändern. Den Migrationstest um vor dem Upgrade erfolgte Ticket- und Kontaktwechsel ergänzen und seine Erwartungen korrigieren.

### F2 · P2 · Eine später sichtbare Antwort mit früherem Zeitstempel gilt weiterhin als gelesen

**Datei/Zeile:** `backend/src/main/java/taskmanager/helpdesk/internal/PortalConversation.java:306`, `:263`, `:112`; `backend/src/main/java/taskmanager/helpdesk/internal/PortalRepository.java:121`; `frontend/src/pages/PortalPage.tsx:372`. Dokumentierte Grenze: `backend/README.md:293`.

`until` begrenzt die Lesemarke korrekt auf den neuesten angezeigten Zeitpunkt. Dieser Zeitpunkt beschreibt aber nicht, welche Transaktionen beim Lesen bereits sichtbar waren. Der Supportbeitrag erhält seinen Zeitpunkt vor dem Insert und dem Commit. Während er noch läuft, kann ein später datierter Kundenbeitrag abgeschlossen und angezeigt werden. Dessen Zeitpunkt quittiert dann auch die noch unsichtbare Supportantwort.

**Nachstellung am echten Backend über HTTP:** Einen Supportaufruf nach Vergabe seines Zeitpunkts vor dem Insert gezielt angehalten; dafür nur in der synthetischen Datenbank einen temporären Trigger mit Beratungssperre verwendet. Währenddessen regulär einen Kundenbeitrag veröffentlicht, das Gespräch gelesen und dessen neuesten Zeitpunkt mit `/seen` quittiert. Dann den Supportaufruf freigegeben. Alle Fachaufrufe wurden erfolgreich abgeschlossen. Vorher zeigte das Gespräch einen Eintrag, danach zwei. Trotzdem meldete `/activity` `unread: false`: `lastSupportAt = 18:39:02.988981Z`, `seenAt = 18:39:03.068021Z`. Die Supportantwort fehlte im quittierten Gespräch.

**Auswirkung:** Der Kunde kann eine tatsächlich noch nicht angezeigte Supportantwort ohne Neu-Markierung übersehen. Der einfache bisherige Fehlerfall – eine erst nach dem Lesen begonnene Antwort vor `/seen` – ist behoben und getestet; der Fall einer bereits laufenden, erst später sichtbaren Antwort bleibt offen.

**Vorschlag:** Den Lesestand an den tatsächlich gelieferten Supporteinträgen oder einem konsistenten, transaktional serialisierten Gesprächsstand festmachen. Ein vor dem Commit vergebener Zeit- oder Sequenzhöchstwert allein reicht nicht. Den beschriebenen Überholfall als dauerhaften Test aufnehmen und Vertrag, Client und Dokumentation gemeinsam auf die garantierte Semantik ausrichten.

### F3 · P2 · Die Generation wechselt erst mit der neuen Antwort, nicht schon mit dem Filter

**Datei/Zeile:** `frontend/src/pages/PortalPage.tsx:158`, `:163`, `:182`; dazu `frontend/src/app/hooks.ts:21`. Der neue Browsertest wartet auf die bereits geladene neue erste Seite (`frontend/tests/browser/portal.test.mjs:415`).

Die Generation hängt von `tickets` ab. `useLoad` behält einen erfolgreichen Zustand während des nächsten Ladens unverändert bei. Nach einem Filterwechsel bleibt deshalb zunächst die alte Generation gültig. Eine alte Folgeseite, die vor der neuen ersten Seite eintrifft, besteht den Vergleich und wird angehängt. Die Korrektur greift erst, sobald die neue erste Seite oder ihr Fehler den Zustand ändert.

**Nachstellung im gebauten Frontend mit API-Attrappe:** 51 eigene offene Anfragen bereitgestellt, die Folgeseite zurückgehalten und den Filter auf „Erledigt“ gewechselt. Auch die neue erste Seite gezielt zurückgehalten. Dann nur die alte Folgeseite freigegeben. Unter `state=done` wuchs die Tabelle von 50 auf **51** Zeilen; die neu angehängte Zeile war `PLAT-1`, Status „Neu“. Erst nach Freigabe der neuen ersten Seite sank die Liste auf null Zeilen.

**Auswirkung:** Während einer langsamen neuen Abfrage erscheint weiterhin eine verspätete Seite des alten Filters. Das geforderte Verwerfen unmittelbar nach dem Filterwechsel ist noch nicht erfüllt. Ein Zugriff über die Organisationsgrenze wurde dabei nicht festgestellt.

**Vorschlag:** Abfragegeneration und Folgeseitenzustand bereits beim Start einer neuen Filter-/Projekt-/Neuladefolge wechseln; Ergebnisse, Fehler und Ladezustände der alten Folge daran prüfen. Im Regressionstest beide Antworten halten und die alte Folgeseite vor der neuen ersten Seite freigeben. Den alten Listenstand während des Ladens gegebenenfalls ausdrücklich als solchen kennzeichnen oder ausblenden.

## Hinweise und Prüfung der übrigen Korrekturen

### H1 · Die Ausnahme zur Zahl verborgener Tags ist dokumentiert

**Datei/Zeile:** `backend/README.md:278`; `DECISIONS.md:197`; Kapazitätsprüfung `backend/src/main/java/taskmanager/tags/internal/PortalTags.java:127`.

Die 30er-Grenze zählt weiterhin alle Zuordnungen. Der Text benennt ausdrücklich, dass dadurch verborgene Kapazität erkennbar wird, während Namen und Kennungen intern bleiben. **Beauftragte Dokumentationskorrektur erfüllt.** Auswirkung bleibt die bereits bestätigte Ausnahme; Vorschlag: bei der fachlichen Abnahme ausdrücklich mitführen, ohne die Zählregel eigenmächtig zu ändern.

### H2 · Gehaltene Tagänderungen werden jetzt tatsächlich geprüft

**Datei/Zeile:** `backend/src/test/java/taskmanager/PortalTagsApiTest.java:596`, `:644`; `backend/src/main/java/taskmanager/tags/internal/PortalTags.java:148`.

Die Tests halten eine geänderte Tagzeile und prüfen über `pg_stat_activity`/`pg_blocking_pids`, dass der Portalaufruf wirklich wartet. Abgedeckt sind Wechsel auf intern beim Setzen und Entfernen, Archivieren beim Setzen sowie Zusammenführen beim Setzen und Entfernen; der Zieltag bleibt erhalten. Entfernen eines zusammengeführten Tags liefert nun 404. **Beauftragte Korrektur erfüllt.** Die Halter bilden die Datenänderung direkt mit SQL nach, nicht den vollständigen Verwaltungsaufruf. Vorschlag: diese Aussagegrenze beibehalten; daraus folgt kein zusätzlicher Produktauftrag.

### H3 · Kapazitätsfehler und Vorschlagslimit der Attrappe sind korrigiert; Mailzielgruppen weichen noch ab

**Datei/Zeile:** `frontend/tests/browser/backend.mjs:426`, `:207`, `:402`; verbleibende Abweichung `:501`, `:1452`, `:1567`; Vergleich `backend/src/main/java/taskmanager/helpdesk/internal/ReplyService.java:148` und `PortalConversation.java:237`.

Die Attrappe prüft die Kapazität vor dem Anlegen und berücksichtigt `limit`. Der neue Browsertest bestätigt, dass eine abgelehnte Anlage keinen Tag hinterlässt. **Die ursprünglich beauftragte H3-Korrektur ist erfüllt.**

Für F1 ermittelt dieselbe Attrappe die Zielgruppe gesendeter Antworten jedoch weiterhin beim Lesen aus dem aktuellen Kontakt. Weder der Bestätigungsweg noch `addReply` hält dessen damaligen Kunden standardmäßig fest. Das echte Backend speichert dafür jetzt `Reply.customerId`. Nach einem Kontaktwechsel können Browserprüfungen deshalb einen anderen Gesprächsinhalt erhalten als die Anwendung. Auswirkung: Die Testattrappe belegt die neue unveränderliche Mailzielgruppe nicht zuverlässig. Vorschlag: Kundenkennung bei Erzeugung der Antwort festhalten und beim Lesen verwenden; Kontaktwechsel gezielt abdecken. Dieser Hinweis betrifft die Prüfgenauigkeit, keinen zusätzlich nachgewiesenen Laufzeitfehler im neuen Reply-Schreibpfad.

### F4 und F5 · Die beauftragten Korrekturen bestehen

| Prüfung | Ergebnis und Beleg |
|---|---|
| F4 Katalogcursor | `TagRepository.java:69` ordnet und begrenzt nach `(normalized, id)`. `TagController`, `TagService`, beide Katalogrouten in `contracts/tags.openapi.yaml`, generierte Typen, `product.ts:414`, `TagParts.tsx:249` und die Attrappe mit `afterId` passen dazu. Backendtest mit Seitengröße 2 innerhalb gleicher Namen und Browsertest mit 52 gleichnamigen Tags bestehen. Ohne `afterId` bleibt das dokumentierte Verhalten nach allen Tags dieses Namens erhalten. |
| F5 Tagfilter | `frontend/src/pages/PortalPage.tsx:120` durchsucht die Portalvorschläge; `chosen` hält die gewählte Option bei anderer Suche sichtbar. Der Browsertest erreicht Tag 21 und behält ihn nach der Suche nach „Wartung“. |

### Organisationsgrenze, Freigabe, Rechte, Verträge und Umfang

- **Laufzeit nach Migration:** Gespräch filtert Kundenmails, gesendete Antworten und Beiträge nach gespeicherter Organisation (`PortalConversation.java:221`, `:225`, `:237`); auch Beitragsdateien werden nachgeprüft (`:230`). Aktivität filtert alle drei Quellen (`PortalRepository.java:118`). Portalliste und Einzelticket begrenzen auf den aktuellen Organisationskunden (`tickets/internal/TicketService.java:143`, `:188`). Anhangliste, Download und Einbindung einer Datei in einen neuen Beitrag prüfen die gespeicherte Zielgruppe (`files/internal/AttachmentService.java:332`, `:339`; `PortalConversation.java:201`). Alte und neue Organisation können dadurch im regulären neuen Schreibpfad nicht wechselseitig auf neue/fremde Inhalte zugreifen. F1 betrifft hier die falschen rückgefüllten Zielgruppen, keinen fehlenden dieser Filter.
- **Ausdrückliche Dateifreigabe:** PATCH braucht `ticket.edit` und die Schreibregel für `attachments`. `setVisibility` ermittelt den Kunden über `lockCustomer` vor der Ticketsperre (`AttachmentService.java:306`), sperrt danach den Anhang und schreibt `attachment.audience` beim Binden. Die interne Liste ermittelt `customerCurrent` gegen den aktuellen Kunden; Oberfläche und API bieten die erneute Freigabe. Eine unveränderte Zielgruppe erzeugt kein zusätzliches Audit.
- **Zusätzlich geprüfte Gleichzeitigkeit in beiden Richtungen:** Wartet PATCH auf einen gehaltenen Kundenwechsel, bindet es nach dessen Commit an den neuen Kunden, antwortet mit `customerCurrent: true`, schreibt dessen Audit genau einmal; neuer Download 200, alter 404. Hält PATCH bereits die Kundenzeile und wartet am Anhang, wartet ein gleichzeitig gestarteter Kundenwechsel auf PATCH. Danach bleibt die Datei beim zum Freigabezeitpunkt aktuellen Kunden; der inzwischen neue Kunde erhält 404. Beide Fachaufrufe antworten 200. Kein nachgestelltes Umhängen auf eine ungeprüfte Zielgruppe und keine Verklemmung in diesen beiden Fällen.
- **Lesemarke:** Fehlender Körper beziehungsweise fehlendes `until` wird mit 400 `request.invalid`/`until.required` abgewiesen. Zukunft wird auf jetzt begrenzt, ältere Marken senken den Stand nicht. Controller, Helpdeskvertrag, generierte Typen und Aufrufer passen dazu. F2 betrifft die nicht garantierte Sichtbarkeit trotz korrektem Zeitvergleich.
- **Rechte und Suche:** Die Änderungen führen keine alternative Berechtigung ein. Portalzugriffe prüfen aktuelle Organisation, Projektmitgliedschaft, Aktion und benötigte Felder; die Dateifreigabe nutzt die vorhandenen internen Schreibrechte. Die allgemeine Suche verweigert Portalkonten weiterhin Zugriff (`search/internal/SearchService.java:458`). Bestehende Rechte-, Vertrags- und Portaltests gehören zum vollständigen Backendlauf. Keine zusätzliche Rechteausweitung im Diff festgestellt.
- **Verträge:** Helpdesk (`until`), Files (`customerCurrent`, erneute Freigabe) und Tags (`afterId`) sind strukturell mit den geänderten Routen und Datentypen abgestimmt. Die uneingeschränkten fachlichen Zusagen zur historischen Zielgruppe beziehungsweise ungelesenen Antworten sind wegen F1/F2 noch nicht vollständig wahr.
- **Migrationen:** Tatsächlich 39 versionierte Migrationen, trotz höchster Kennung V0042. `lifecycle-check.sh:89`/`:92`, `DatabaseFoundationTest.java:36` und `ApplicationLifecycleTest.java:34` sind entsprechend angepasst; die Versionsliste enthält V0041 und V0042.
- **Umfang:** Keine sachfremde Funktion unter den 48 geänderten Dateien festgestellt. Die kleine Änderung beim Entfernen zusammengeführter Tags gehört zu H2. Der ursprüngliche Skalierungshinweis H4 bleibt wie beauftragt offen und wurde nicht zum neuen Implementierungsauftrag gemacht.

## Ausgeführte Prüfungen und Grenzen

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | Bestanden; unter Windows mit dem verfügbaren Python 3.12 als `python` ausgeführt. |
| `python3 scripts/task_board.py --check` | Bestanden, Übersicht aktuell; ebenfalls `python`. |
| `python3 -m unittest discover -s tests -v` | 38 Tests, erfolgreich; 1 ausdrücklich Unix-spezifischer Zeitzonentest unter Windows übersprungen. Der eingeschränkte Erstversuch scheiterte an lokalen Verbindungen/Dateirechten; Wiederholung mit eigenem temporären Ordner erfolgreich. |
| Backend `./mvnw verify` | **BUILD SUCCESS**, 501 Tests, keine Fehler und keine übersprungenen Tests, 9 min 22 s. Unter Windows über `mvnw.cmd`, Java 25.0.4.1, Maven 3.9.16, eigene PostgreSQL 16.15, vorhandener Abhängigkeitscache. Ein erster Lauf mit versehentlich leerer Pflichtangabe für das synthetische Datenbankpasswort scheiterte beim Anwendungsstart; der vollständige Wiederholungslauf mit korrigierter Angabe bestand. |
| Frontend `npm ci` | Bestanden, 58 Pakete, Node 24.22.0 / npm 11.19.1, vorhandener Cache. |
| Frontend `npm run verify` | Bestanden: Generierung, Typprüfung, 77 Tests und Produktionsbuild. Nur die bekannte Buildwarnung zur Paketgröße. Generierte Dateien stimmen weiterhin mit Git überein. |
| Frontend `npm run test:browser` | Alle 167 Tests in 32 Gruppen bestanden, keine übersprungenen Tests; lokaler Chrome, Produktionsbuild und API-Attrappe. Auch alle sechs neuen TASK-079-Browsertests bestanden. |
| Zusätzliche Migrations-/API-Nachstellungen | F1 und F2 reproduziert. Die Dateifreigabe bei Kundenwechsel in beiden Sperrreihenfolgen erfolgreich geprüft. PostgreSQL 16.15; echte Anwendung und HTTP, bei Migration direkt vorbereitete synthetische Bestandsdaten. Temporäre Datenbanksperren/Trigger dienten ausschließlich der kontrollierten Unterbrechung. |
| Zusätzliche Browsernachstellung | F3 mit gehaltener alter Folgeseite und gleichzeitig gehaltener neuer erster Seite reproduziert. |
| `git diff --check` und abschließender Dateistatus | Bestanden, zusätzlich auch die neue Berichtsdatei gesondert auf Leerraumfehler geprüft. Keine verfolgte Datei verändert; einzige neue Datei im Checkout ist dieser Bericht. Repositoryvalidierung und Aufgabenübersicht auch mit dem neuen Bericht erneut erfolgreich geprüft. |

Nicht ausgeführt: Linux-Skript `backend/scripts/lifecycle-check.sh`, Docker-/Vorführimage samt Datenbefüllung, der in CI festgelegte PostgreSQL-18.6-Container, Browserdurchlauf mit echtem Backend, Lastmessung und vollständige Sperr-/Abbruchmatrix. Das Lifecycle-Skript wurde auf Versionsliste und Migrationszahl gelesen; das ersetzt seine Ausführung nicht.

## Empfehlung

**TASK-079 nachbessern, vorerst nicht abnehmen.** F1 muss die historische Zielgruppe auch beim Upgrade bewahren beziehungsweise unklare Altbestände zurückhalten. F2 muss später sichtbar werdende, nicht angezeigte Antworten neu lassen. F3 muss alte Folgeseiten schon ab dem Filterwechsel verwerfen. F4/F5 und die ursprünglichen H1–H3-Korrekturen können als positiv geprüft gelten; die zusätzliche Abweichung der Mailantworten in der Attrappe sollte mitgezogen werden. Anschließend die drei gezielten Gegenfälle dauerhaft aufnehmen und erneut unabhängig prüfen lassen.
