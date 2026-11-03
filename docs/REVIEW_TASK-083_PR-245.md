# Unabhängige Prüfung TASK-083 · PR #245

**Prüfsitzung:** `codex-20261103-portal-review-05`, unabhängige zweite KI nach DEC-040. Prüfdatum: 3. November 2026.

**Stand:** Abschließend `main`, Commit **`bfff1bcff853f0bacb5fbf9a82adfc3c5d6de2ee`**, der während der Prüfung gemergte [PR #245](https://github.com/1uedev/task-manager/pull/245). Geprüft wurden zunächst der Branch `claude/project-thread-houava` mit Commit `1c4f42f90a519e7066daca343b2e8bf849debbef` und anschließend dessen integrierter Stand **`af32620a7bcce957040c5b769c3c70ce3d8b193a`**. Dessen vollständiger Dateibaum ist mit dem abschließenden main-Stand identisch; die abschließenden Prüfungen gelten damit auch für diesen main-Stand. Enthalten ist TASK-081, [PR #243](https://github.com/1uedev/task-manager/pull/243), Commit `db2b62de8bc24f449901a61292431fcef8b37f00`, mit V0046. V0047, V0048 und der Migrationstest wurden bei dessen Integration nicht geändert.

**Empfehlung: nachbessern.** Die beauftragte Historienregel funktioniert in den geprüften Fällen mit unveränderter Sitzungszeitzone. Ein Befund P1 bleibt an der Grenze, die alte Antworten von seit V0043 gespeicherten Antworten unterscheiden soll: Bei geänderter Sitzungszeitzone können alte falsche Zielgruppen erhalten bleiben und neue richtige Zielgruppen verloren gehen.

Maßstab: AGENTS.md, TASK-083 in TODO.md, DEC-113, [die vorherige Prüfung](REVIEW_TASK-080_PR-241.md), der TASK-083-Abschnitt in backend/README.md und Holgers Auftrag „Ja, so beheben“. Es gilt die bewusst konservative Regel ohne Zeitfenster; nachgewiesen von der Anwendung seit V0043 gespeicherte Werte sollen erhalten bleiben.

Im separaten Prüfcheckout wurde ausschließlich dieser Bericht ergänzt. Produktcode, Tests, Verträge, Migrationen und Aufgabenstatus wurden nicht geändert. Zusätzliche Prüfprogramme und die isolierte Kopie der V0046 für den Reihenfolgeversuch lagen außerhalb des Checkouts. Verwendet wurden eigene PostgreSQL-16-Datenbanken und synthetische Daten; keine echten Mails oder Kundendaten. Die fachliche Abnahme bleibt bei Holger.

## Befunde

### F1 · P1 · Die ungesicherte Zeitzone der Installationsgrenze kann falsche Altwerte von der Korrektur ausschließen

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0047__helpdesk_reply_audience_safe.sql:19` und `:32`; dokumentierte Voraussetzung `backend/README.md:294`. Die Verbindungskonfiguration in `backend/src/main/resources/application.yaml:13` erzwingt nur das Sperrzeitlimit, keine unveränderliche Zeitzone dieser historischen Grenze.

V0047 interpretiert `flyway_schema_history.installed_on` von V0043 mit der **jetzigen** Sitzungszeitzone und korrigiert nur Antworten mit `created_at < installed`. Der gespeicherte Installationswert enthält keine Zeitzone. Wurde er unter einer anderen Sitzungszeitzone geschrieben, verschiebt die erneute Interpretation die Grenze. Ein alter Datensatz kann dann fälschlich als von der Anwendung nach V0043 geschrieben gelten. Gerade sein unzuverlässiger Wert aus V0043 bleibt dadurch unverändert.

**Zusätzliche Nachstellungen mit echter Flyway-Ausführung und PostgreSQL 16:** Die Zeitzonenmatrix verwendet synthetische, über JDBC vorbereitete Zeilen vor und nach V0043. Die nachträglichen Zeilen bilden die unveränderte Speicherung der Anwendung ab. Der echte API-Versuch mit gehaltener Kontaktänderung ist davon getrennt und unten beschrieben.

| Zeitzone bei V0043 → bei V0047 | Ergebnis |
|---|---|
| UTC → UTC | Unklare Altantwort wird leer; sichere Antwort an neuen Kontakt wird wiederhergestellt; neue von der Anwendung gespeicherte Kundenkennung und gespeichertes null bleiben erhalten. |
| UTC → Europe/Berlin | Eine vor V0043 vorhandene, unklare Antwort bleibt fälschlich Südtechnik zugeordnet. Auch eine von V0043 ausgeblendete sichere Antwort bleibt leer, statt wiederhergestellt zu werden. |
| Europe/Berlin → UTC | Eine erst nach V0043 korrekt mit Südtechnik gespeicherte Antwort wird unnötig neu berechnet und erhält null, weil der Kontakt früher eine andere Organisation hatte. |

Für den zweiten Fall wurde V0043 am 3. November mit dem lokalen Installationswert `07:33:10.640952` unter UTC angewendet. Bei der Korrektur unter Europe/Berlin wird derselbe Wert als `06:33:10.640952Z` verstanden. Die ungefähr zehn Minuten vor dem tatsächlichen Upgrade erzeugte Altantwort liegt nun hinter dieser falsch verschobenen Grenze und wird nicht korrigiert. Die Kontaktgeschichte enthält beide Organisationen; nach der beauftragten Regel müsste ihre Zielgruppe leer werden. Stattdessen bleibt der von V0043 falsch ermittelte neue Kunde bestehen.

Die Nachstellung wurde auf dem integrierten PR-Stand wiederholt. Mit entsprechender aktueller Ticketkundenzuordnung erfüllt die erhaltene falsche Antwort weiterhin die vom Portal verwendete Organisationsbedingung. Dies wurde zusätzlich per SQL geprüft; ein weiterer HTTP-Download beziehungsweise Gesprächsabruf war nicht Bestandteil dieses Zeitzonenversuchs. Der Laufzeitfilter vergleicht genau die gespeicherte Kundenkennung (`backend/src/main/java/taskmanager/helpdesk/internal/PortalConversation.java:252`).

**Auswirkung:** Ein Wechsel der Sitzungszeitzone zwischen den Upgrades kann historische Korrespondenz für eine fremde Organisation zugänglich lassen oder korrekt gespeicherte Antworten verbergen. Der Text in backend/README.md benennt diese Voraussetzung bereits. Sie wird aber weder anhand einer gespeicherten Ursprungszeitzone geprüft noch bei Unsicherheit abgewiesen. Der erfolgreiche Fall mit unveränderter UTC ist daher kein allgemeiner Nachweis für die sichere Trennung der Bestandsdaten.

**Vorschlag:** Die ursprüngliche Installationsgrenze eindeutig als Zeitpunkt bestimmen, statt die aktuelle Sitzungszeitzone als historische Information zu verwenden. Für bereits angewendete V0043 braucht es eine verlässlich bekannte ursprüngliche Zone beziehungsweise eine explizit gesicherte UTC-Grenze. Kann sie nicht belastbar festgestellt werden, den unsicheren Upgradefall abweisen oder einen konservativen Korrekturweg vorsehen, statt mögliche Altwerte ungeprüft zu behalten. Ein bloßes Festlegen auf UTC bei V0047 repariert nicht jede bereits unter anderer Zone ausgeführte V0043. Beide Richtungen des Zeitzonenwechsels als Regressionen aufnehmen. Bereits angewendete Migrationen nicht still durch andere Prüfsummen ersetzen.

## Hinweise

### H1 · Die drei Upgradepfade sind abgedeckt, die Zeitzonenvoraussetzung noch nicht

**Datei/Zeile:** `backend/src/test/java/taskmanager/PortalAudienceMigrationTest.java:26`, `:35`, `:49`, `:68` und `:69`.

Die Tests prüfen das Upgrade ab V0040, die Korrektur nach V0042 und die Korrektur nach V0045. Frischer Kontakt, reine Namensänderung, frühere Organisationswechsel, die von V0043 falsch ausgeblendete TASK-079-Antwort und nach V0043 gespeicherte Werte einschließlich null sind enthalten. Die letzten beiden Zeilen tragen gezielt Zeitpunkte nach der Migration und werden auf Erhalt geprüft.

**Auswirkung:** Der beauftragte H1-Zusatz ist als Migrationsmatrix vorhanden. Die grünen Tests setzen aber dieselbe Zeitzone für beide Installationen voraus und erfassen F1 dieses Berichts nicht. **Vorschlag:** Die Grenzprüfung um explizite unterschiedliche Sitzungszeitzonen und den erwarteten sicheren Abbruch beziehungsweise die korrekte Behandlung ergänzen. Die vorhandenen fachlichen Szenarien beibehalten.

### H2 · Die Reihenfolge mit V0046 ist inzwischen richtig integriert; ein ausgerollter Zwischenstand wäre gesondert zu behandeln

**Datei/Zeile:** `backend/scripts/lifecycle-check.sh:89` und `:92`; `backend/src/test/java/taskmanager/DatabaseFoundationTest.java:36`; `backend/src/test/java/taskmanager/ApplicationLifecycleTest.java:34`; Standhinweis `TODO.md:391`.

Zu Beginn der Prüfung fehlte V0046 im Branch; inzwischen wurde PR #243 zuerst auf main gemergt und dann in PR #245 übernommen. Der aktuelle Stand enthält **45 versionierte Migrationen**, darunter V0046, V0047 und V0048 in dieser Reihenfolge. Die drei erwarteten Versionslisten beziehungsweise Zahlen wurden auf 45 angeglichen. Die Nummernlücke allein verhindert einen ersten Lauf des alten Zwischenstands nicht: Ohne bekannte V0046 konnte Flyway bis V0048 migrieren.

**Zusätzlicher Reihenfolgeversuch:** Erst den früheren Katalog ohne V0046 bis V0048 angewendet, danach die echte V0046 aus PR #243 ergänzt. Der normale Flyway-Lauf scheitert dann mit `Detected resolved migration not applied to database: 0046`. Der umgekehrte Weg – V0045, dann V0046/V0047/V0048 – besteht mit 45 Migrationen und der erwarteten neuen Spalte `appearance_setting.name`.

**Auswirkung:** Die aktuelle Mergefolge löst das Problem für Installationen, die diesen integrierten Stand in normaler Reihenfolge erhalten. Für eine bereits auf V0047/V0048 gebrachte Installation wird eine nachgereichte niedrigere Nummer nicht automatisch korrekt nachgezogen. Ein solches produktives Ausrollen wurde hier nicht festgestellt. **Vorschlag:** Nur den integrierten Stand ausliefern und vor einem Upgrade den angewendeten Versionsstand prüfen; gegebenenfalls einen gezielten Korrekturpfad für eine schon entstandene Lücke festlegen. Die Validierung nicht pauschal abschalten. Aus der früher fehlenden Nummer folgt kein zusätzlicher Codebefund am jetzt integrierten Stand.

### H3 · „Gehaltene Änderung“ ist im Migrationstest eine modellierte Historie

**Datei/Zeile:** `backend/src/test/java/taskmanager/PortalAudienceMigrationTest.java:128`, `:129`, `:148` und `:149`; Beschreibung `backend/README.md:292`.

Der Migrationstest schreibt synthetische Enddaten mit 70 Sekunden Abstand. Er hält selbst keine Transaktion und führt keinen konkurrierenden Verwaltungs- oder Antwortaufruf aus. Das ist eine sinnvolle deterministische Vorbereitung für den SQL-Migrationsfall, prüft aber nicht die behauptete Reihenfolge des laufenden Anwendungscodes.

**Auswirkung:** Der Migrationsnachweis ist enger als eine echte Nebenläufigkeitsprüfung. **Vorschlag:** Die Testbeschreibung entsprechend präzisieren und die Aussage zur Sperrreihenfolge separat belegen. In dieser unabhängigen Prüfung wurde die echte 70-Sekunden-Kontaktänderung zusätzlich über HTTP nachgestellt; die Korrektur bestand. Dieser Hinweis ist kein weiterer nachgewiesener Produktfehler und fordert keine langsame Wartezeit in jedem normalen Migrationslauf.

## Positive Prüfung der beauftragten Regeln

### V0047: Kontaktgeschichte ohne Zeitfenster

Die neue Abfrage betrachtet sämtliche protokollierten Kontaktanlagen und -änderungen bis einschließlich `reply.created_at` (`V0047:23` bis `:27`). Sie nimmt eine Organisation nur bei genau einem unterschiedlichen Organisationswert. Mehrere Organisationen, fehlende Historie oder eine unvollständige Zielgruppe werden nicht durch den heutigen Kontakt ersetzt. Wiederholte Namens-/Adressänderungen mit derselben Organisation ändern die Zahl der unterschiedlichen Zielgruppen nicht. Auch die bloße Kontaktanlage neben der Antwort erzeugt keine Unsicherheit mehr.

Die Begründung trägt für den relevanten Anwendungspfad bei vollständiger, unveränderter Historie und fortschreitender Anwendungszeit: `DirectoryService.updateContact` sperrt zuerst die Kontaktzeile (`:141`) und liest danach die Zeit (`:160`), bevor es die Änderung und das Audit schreibt. `ReplyService.create` liest den Kontakt (`:119`) vor seiner eigenen Zeit (`:141`). Eine neue Kontaktversion, die der Antwort schon sichtbar gewesen sein konnte, hat daher einen erfassten früheren Zeitpunkt. Eine bereits geschriebene, noch nicht bestätigte Kontaktänderung kann ebenfalls einen früheren Zeitpunkt tragen; durch die Einbeziehung aller bisherigen Organisationswerte führt das nun zu null statt zur neueren Organisation. Eine erst nach dem Kontaktlesen beginnende Änderung konnte die gelesene Version nicht beeinflussen; ihre Nichtberücksichtigung ist dann unschädlich. Gleichzeitige Zeitwerte werden durch `<=` konservativ mitgezählt.

**Echte Gegenprüfung des bisherigen F1/F2-Falls:** Auf V0042 einen Kontaktwechsel durch einen temporären Trigger in der eigenen Testdatenbank 70 Sekunden gehalten. Nach 67 Sekunden regulär eine Antwort bestätigen lassen; Abstand ihrer Zeit zum Änderungszeitpunkt **67,097129 Sekunden**, gespeicherte Organisation zunächst Nordwerk. Nach den neuen Migrationen bleibt die durch den früheren Wechsel unklare Antwort leer. Eine sichere frühere Antwort, die V0043 zwischenzeitlich ausgeblendet hatte, wird wieder Nordwerk zugeordnet. Die Fachaufrufe endeten erfolgreich. Der alte Fensterfehler ist in diesem Ablauf geschlossen.

Die zusätzlichen Prüfungen bei gleicher UTC bestätigten außerdem: Eine kurz zuvor angelegte, nie verschobene Kontaktorganisation wird wiederhergestellt; neue seit V0043 gespeicherte Organisationen und neue gespeicherte null-Werte bleiben unverändert. Diese Aussagen stehen unter der in F1 untersuchten Installationsgrenze.

### V0048: alte Umschaltungen und unveränderte sichere Quellen

V0048 wählt die jüngste relevante Handlung je Datei anhand der Auditreihenfolge. Es rechnet nur kundensichtbare Dateien neu, deren letzte relevante Handlung ein `attachment.visibility = customer` ohne spätere benannte Zielgruppe ist (`:18`, `:27`, `:32`). Für diese Dateien gilt die neue Ein-Organisations-Regel über alle vorherigen Kundenänderungen des Tickets; fehlende oder wechselnde Kunden ergeben keine Zielgruppe.

Im ursprünglichen Umschaltpfad vor TASK-079 wurde vor dem Zeitpunkt keine Kundenzeile gesperrt; ein damals laufender Kundenwechsel konnte deshalb noch unsichtbar sein. Kundenänderungen des Tickets nehmen die Kundensperre und lesen ihre Zeit vor dem Schreiben und dem Commit (`TicketCustomerService.java:135`, `:165`). Die neue Regel zählt solche schon geschriebenen möglichen Wechsel mit, statt ihre Sichtbarkeit aus einem Zeitabstand zu schätzen. Das Zeitfenster ist vollständig entfernt. Für die alte Dateiregel wurde die materialisierte Historie einschließlich länger laufender Änderung auf Migrationsebene geprüft; ein laufender alter Dateiserver wurde nicht verwendet.

Benannte Freigaben mit `attachment.audience` und nie umgeschaltete Dateien werden von V0048 nicht neu berechnet. Deren bestehende Quellen sind die ausdrücklich gespeicherte Organisation, die feste Organisation des hochladenden Portalkontos beziehungsweise die Kundenkennung der Mail. Die vorhandenen Migrationstests bestätigen ihren Erhalt. Interne Dateien werden nicht veröffentlicht. Bei einer unklaren Altumschaltung bleibt die Organisation leer; der bestehende ausdrückliche Freigabeweg kann sie später an den aktuellen Kunden binden. An diesem Freigabeweg wurde in TASK-083 nichts geändert.

Ein weiterer Weg zu einer fremden Zielgruppe wurde bei diesen Dateiwegen im untersuchten Modell nicht gefunden. Für Mailantworten ist der in F1 nachgewiesene übersprungene Altwert dagegen ein verbleibender Weg. Supportbeiträge werden wie beauftragt weiterhin nach V0043 behandelt und sind kein neuer Änderungsteil von TASK-083.

### Umfang und übrige Änderungen

Der Differenzumfang von TASK-083 gegen das nun integrierte main umfasst weiterhin zwölf Dateien: die zwei neuen Migrationen, der Migrationstest, die drei Migrationsnachweise, der ausdrücklich beauftragte Kommentar zu `position` sowie die zugehörigen Auftrags- und Ergebnisdokumente. Es wurde keine zusätzliche Portal-, Rechte-, Such- oder Oberflächenfunktion eingeführt. Frühere Migrationen bleiben unverändert. Die A17-Funktionen aus PR #243 sind bereits Teil von main; sie wurden für die Integration mitgeprüft, hier aber nicht als eigenständige fachliche Abnahme von TASK-081 bewertet. Der frühere Hinweis zu historischen Lesemarken bleibt wie beauftragt bestehen.

## Ausgeführte Prüfungen und Grenzen

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | Bestanden, vor und nach Aktualisierung des Branchs; unter Windows mit dem verfügbaren Python 3.12 als `python`. |
| `python3 scripts/task_board.py --check` | Bestanden, Aufgabenübersicht aktuell; ebenfalls `python`. |
| `python3 -m unittest discover -s tests -v` | Beide Stände bestanden 38 Tests mit einem ausdrücklich Unix-spezifischen Zeitzonen-Skip unter Windows. Eigener temporärer Ordner und lokale Testverbindungen. |
| Backend `./mvnw verify` | Erster Stand `1c4f42f`: BUILD SUCCESS, 504 Tests ohne Fehler/Skips, 15 min 23 s. Integrierter Stand `af32620`: **BUILD SUCCESS, 509 Tests**, keine Fehler oder Skips, 12 min 37 s. Windows-Aufruf über `mvnw.cmd`, Java 25.0.4.1, Maven 3.9.16, eigene PostgreSQL 16.15; für den integrierten Lauf eine weitere leere Datenbank. |
| Frontend `npm ci` | Erfolgreich, 58 Pakete aus vorhandenem Cache; Node 24.22.0 / npm 11.19.1. Die Abhängigkeitsdateien änderten sich bei der späteren Integration nicht. |
| Frontend `npm run verify` | Erster Stand: erfolgreich, einschließlich 77 Tests. Integrierter Stand: ebenfalls erfolgreich, einschließlich 79 Tests, Generierung, Typprüfung und Produktionsbuild. Nur die bekannte Warnung zur Paketgröße; generierte Dateien unverändert. |
| Frontend `npm run test:browser` | Erster Stand: 169/170, eine sofortige Zeilenzählung im Portallistentest scheiterte (`portal.test.mjs:112`, 0 statt 1). Wiederholung derselben Dateien mit `node --test --test-concurrency=1 tests/browser/*.test.mjs`: 170/170 erfolgreich. Der Test wartet zuvor auf die Überschrift, nicht auf die geladene Tabelle. Integrierter Stand: reguläres `npm run test:browser` vollständig grün, **176/176**, 35 Gruppen, keine Skips. Kein Produkt-/Testcode für die Wiederholungen geändert. |
| Zusätzliche Nachstellungen | Echte 70 Sekunden gehaltene Kontaktänderung mit regulärer API-Bestätigung und anschließender Migration: Korrektur bestanden. Gleiche und unterschiedliche Sitzungszeitzonen: F1 reproduziert, einschließlich Rückwärtsrichtung und Prüfung der Portal-Zielgruppenbedingung. Reihenfolgen V0046 vor beziehungsweise nach V0047/V0048 mit echter V0046: erwarteter Erfolg beziehungsweise Validierungsfehler. |
| Abschließende Dateiprüfung | Repositoryvalidierung und Aufgabenübersicht mit Bericht erneut erfolgreich geprüft; `git diff --check` und die gesonderte Leerraumprüfung der neuen Datei ohne Beanstandung. Keine verfolgte Datei verändert; einzige neue Datei im Prüfcheckout ist dieser Bericht. |

Nicht ausgeführt: Linux-Skript `backend/scripts/lifecycle-check.sh`, Docker-/Vorführimage mit vollständiger Datenbefüllung, der festgelegte PostgreSQL-18.6-Container, vollständiger Browserdurchlauf gegen das echte Backend, Lastmessung, vollständige Abbruch-/Sperrmatrix und Zeitsprünge der beteiligten Systemuhren. Das Lifecycle-Skript wurde auf Versionsliste und Migrationszahl geprüft; das ersetzt seine Ausführung nicht. Der echte gehaltene API-Versuch betrifft die Kontaktänderung, die Zeitzonen- und Reihenfolgeversuche betreffen direkt die Migrationsausführung.

## Empfehlung

**TASK-083 nachbessern, vorerst nicht abnehmen.** Die konservative Historienregel ohne Zeitfenster und die sichere Wiederherstellung bei unveränderter Zeitzone sind positiv geprüft. Vor der Abnahme muss die V0043-Installationsgrenze auch dann sicher behandelt werden, wenn ihre ursprüngliche Zeitzone nicht zuverlässig der jetzigen entspricht; unsichere Altwerte dürfen dadurch nicht ungeprüft stehen bleiben. Die inzwischen richtige Mergefolge mit V0046 beibehalten. Danach den gezielten Grenzfall und den endgültigen integrierten Stand erneut prüfen.
