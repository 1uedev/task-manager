# Unabhängige Prüfung von TASK-084 / PR #249

**Prüfsitzung:** `codex-20261103-portal-review-06`, unabhängige zweite KI nach DEC-040, 3. November 2026.

**Stand:** aktuelles `main`, Commit `2e714da64d313918368d2b35c306a3c5015a1552`, Ergebnis [PR #249](https://github.com/1uedev/task-manager/pull/249). Der PR ist gemergt. Verglichen wurde seine Änderung gegen den unmittelbaren Vorgänger `010ad078df203de08d09ae5bbcea3b7e4fec6591`. TASK-082 mit V0049/V0050 und TASK-085 sind bereits Bestandteil dieser Basis.

**Empfehlung: abnehmen.** F1 sowie H1 und H3 des Vorberichts sind im beauftragten Umfang behoben. Kein neuer Produktbefund. Backend und serielle Browser-Gesamtprüfung bestanden; die zwei Zeitüberschreitungen des ersten parallelen Browserlaufs sind unten dokumentiert.

Maßstab: `AGENTS.md`, TASK-084 in `TODO.md`, DEC-114, [der eigene Vorbericht zu TASK-083](REVIEW_TASK-083_PR-245.md), Abschnitt „Befund der Prüfung von TASK-083 (TASK-084)“ in `backend/README.md` und Holgers Auftrag „Ja, so beheben“: F1 mit H1 und H3 beheben, sonst nichts. Die konservative Grenze von UTC plus zwölf Stunden ist ausdrücklich beauftragt.

Im eigenen Prüfklon wurde ausschließlich dieser Bericht ergänzt. Produktcode, Tests, Verträge, Migrationen und Aufgabenstände blieben unverändert. Zusätzliche Prüfhilfen und eine isolierte Testkopie liegen außerhalb des Repositorys; alle Daten sind synthetisch. Es wurde nichts auf GitHub veröffentlicht. Die fachliche Abnahme bleibt bei Holger.

## Befunde

**Keine neuen Produktbefunde im beauftragten Umfang gefunden.** Die gezielten Gegenproben zu F1 bestanden. Die abschließende Empfehlung und die vollständigen Testergebnisse stehen unten.

### F1 aus dem Vorbericht – behoben: konservative Grenze ohne aktuelle Sitzungszeitzone

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0051__helpdesk_reply_audience_boundary.sql:17` und `:30`; bisherige Grenze in `V0047__helpdesk_reply_audience_safe.sql:19`.

V0051 liest `installed_on` ausdrücklich mit `AT TIME ZONE 'UTC'` und addiert `interval '12 hours'`. Ein Wechsel der aktuellen Sitzungszeitzone verändert diesen Ausdruck nicht. Unter der dokumentierten Voraussetzung einer damals richtigen Datenbankuhr ist die Grenze in allen beauftragten realen Zeitzonen sicher:

- Sei `I` der tatsächliche Installationszeitpunkt und `o` der damalige UTC-Versatz in Stunden. Der gespeicherte Ortszeitwert entspricht beim Lesen als UTC dem Zeitpunkt `I + o`.
- Die neue Grenze ist daher `B = I + o + 12 Stunden`.
- Für `−12 ≤ o ≤ +14` gilt `0 ≤ B − I ≤ 26 Stunden`. Die Grenze liegt niemals vor der tatsächlichen Installation. Gebrochene Stundenversätze und der zum Installationszeitpunkt geltende Sommerzeitversatz ändern diese Rechnung nicht.
- V0051 rechnet alle Antworten mit `created_at < B` neu. Genau an der Grenze und danach bleiben gespeicherte Werte erhalten. Die Abgrenzung unmittelbar vor, genau auf und unmittelbar nach der Grenze wurde zusätzlich mit Mikrosekundenabstand geprüft.

Auch ein bereits ausgeführter falscher V0047-Lauf verhindert die Reparatur nicht: Seine frühere Grenze war `D = gespeicherte Ortszeit als UTC − Versatz der bei V0047 verwendeten Zeitzone`. Solange auch dieser Versatz im beauftragten Bereich liegt, gilt `D ≤ B`. Ein von V0047 möglicherweise übersehener Altwert liegt somit weiterhin im Neuberechnungsbereich von V0051; ein durch eine zu späte alte Grenze veränderter Wert wird ebenfalls erfasst. V0051 hängt nicht vom bisherigen `customer_id` ab, sondern ermittelt die Zielgruppe erneut aus der Kontaktgeschichte.

Die Historienregel selbst entspricht V0047: genau eine belastbar gleichbleibende Organisation aus Kontaktanlage und -änderungen bis einschließlich Antwortzeitpunkt, sonst null. Fehlende oder widersprüchliche Zielgruppen werden nicht durch den heutigen Kontakt ersetzt. Die Abfrage umfasst die betroffenen Antworten unabhängig davon, ob ihr bisheriger Wert null oder gesetzt ist. Der bestehende Portalfilter (`backend/src/main/java/taskmanager/helpdesk/internal/PortalConversation.java:252`) zeigt eine gesendete Antwort nur bei passender Organisation; eine leere Zielgruppe bleibt damit verborgen.

**Auswirkung:** Der im Vorbericht nachgewiesene Weg, auf dem eine fremde Organisation einen falschen Altwert aus V0043 über die verschobene Grenze behalten konnte, ist in den geprüften Fällen geschlossen. **Vorschlag:** Die neue Korrekturmigration und die unveränderte V0047 beibehalten; keine weitere Produktänderung aus diesem Befund erforderlich.

## Gezielte Gegenproben

### Echte Flyway-Zeitzonen und bereits angewendete V0047

Die unabhängige Nachstellung verwendet die echte Flyway-Ausführung der Testhilfe, eigene PostgreSQL-16-Datenbanken und modellierte Tabellenhistorien. Für jedes Paar wurde zunächst V0043 in der ersten Zone ausgeführt, danach V0047 bis V0050 in der zweiten Zone vollständig angewendet und erst in einem weiteren Lauf V0051 ausgeführt. Damit wurde ausdrücklich auch eine Installation geprüft, auf der V0047 bereits abgeschlossen war.

| Zeitzone bei V0043 → bei der Korrektur | Neue Grenze nach dem echten Installationszeitpunkt | Falscher Altwert nach V0047 noch vorhanden? | Ergebnis nach V0051 |
|---|---:|---|---|
| UTC → Europe/Berlin | 12 Stunden | Ja | Zielgruppe leer; sichere Altantwort wiederhergestellt |
| Europe/Berlin → UTC | 13 Stunden am Prüftag | Nein | Ergebnis korrekt; Werte ab der neuen Grenze erhalten |
| Etc/GMT+12 → Pacific/Kiritimati | 0 Stunden | Ja | Zielgruppe leer; sichere Altantwort wiederhergestellt |
| Pacific/Kiritimati → Etc/GMT+12 | 26 Stunden | Nein | Ergebnis korrekt; Werte ab der neuen Grenze erhalten |

Zusätzlich wurden in jedem Paar Antworten eine Mikrosekunde vor der Grenze, genau auf ihr und eine Mikrosekunde danach sowie ein danach gespeicherter null-Wert geprüft. Vor der Grenze wurde eine wegen früherem Kontaktumzug unklare Antwort ausgeblendet; auf und nach der Grenze blieb die gespeicherte Organisation unverändert, ebenso das danach gespeicherte null. Der Fall eine Minute nach V0043 wurde nur dann ausgeblendet, wenn er tatsächlich noch vor der jeweiligen konservativen Grenze lag. Die übrigen Zielgruppenprüfungen des vorhandenen Szenarios bestanden ebenfalls. Alle vier Datenbanken endeten bei 48 erfolgreichen Migrationen.

**Zusätzliche Breitenprüfung:** Eine reine SQL-Abfrage berechnete die Formel für alle 598 am Prüftag in dieser PostgreSQL-16-Installation bekannten Zeitzonennamen. Ergebnis: kleinster Abstand 0 Stunden, größter Abstand 26 Stunden, **keine Grenze vor dem tatsächlichen Referenzzeitpunkt**. Diese Prüfung ergänzt die allgemeine Rechnung; sie ersetzt sie nicht für andere Daten oder historische Zeitzonenstände.

### H1 aus dem Vorbericht – behoben: wirksame Zeitzonenmatrix

**Datei/Zeile:** `backend/src/test/java/taskmanager/PortalAudienceMigrationTest.java:90`, `:91`, `:107` und `:325`.

Der neue Test enthält die vier verlangten Paare einschließlich beider Richtungen. Er bildet eine falsche alte Antwort nach einem Kontaktumzug, eine sichere Altantwort sowie deutlich nach der maximalen Grenze liegende gespeicherte Antworten ab. `initSql("set time zone ...")` wird auf den echten Flyway-Verbindungen ausgeführt.

Die Wirkung auf `installed_on` wurde zusätzlich nachgemessen: Der unveränderte Wert wurde als `LocalDateTime` gelesen und mit der jeweils angeforderten Installationszone in einen Zeitpunkt umgerechnet. Dieser lag in allen vier Fällen im gemessenen Zeitbereich des tatsächlichen V0043-Aufrufs, mit einer Toleranz von einer Sekunde. Die gespeicherten Ortszeitwerte unterschieden sich entsprechend den angeforderten Zonen, einschließlich UTC−12 und UTC+14. Die Testhilfe verändert somit tatsächlich die für die Grenze relevante Flyway-Historie.

**Gegenprobe ohne V0051:** Eine außerhalb des Repositorys angelegte Kopie derselben Testklasse begrenzte ausschließlich den sonst vollständigen Folgemigrationslauf auf V0050. Die Szenarien und Zusicherungen des neuen Zeitzonentests blieben unverändert. Der Test scheiterte erwartungsgemäß beim Paar UTC → Europe/Berlin: „an old answer after the move is unclear“, erwartet null, tatsächlich noch die falsche Organisation. Es handelte sich um einen fachlichen Assertionsfehler, keinen Start-, Konfigurations- oder Prüfsummenfehler. Die eigene Prüfhilfe wertet diesen erwarteten Fehlschlag als erfolgreichen Nachweis der Testwirksamkeit.

**Auswirkung:** Der Test erkennt den ursprünglichen Fehler und ist nicht nur wegen einer wirkungslosen Zeitzonenangabe grün. **Vorschlag:** Die vier Paare und die vorhandenen fachlichen Upgradefälle beibehalten. Die zusätzlichen Mikrosekundenprüfungen sind unabhängige Gegenproben, keine Änderung an der Testsuite.

### H3 aus dem Vorbericht – behoben: modellierte Historie ausdrücklich benannt

**Datei/Zeile:** `backend/src/test/java/taskmanager/PortalAudienceMigrationTest.java:22` und `:23`; `backend/README.md:296`.

Der Test beschreibt nun ausdrücklich synthetische Enddaten und sagt, dass keine Transaktion gehalten wird. Eine „gehaltene“ Änderung bedeutet dort einen protokollierten Änderungszeitpunkt 70 Sekunden vor der Antwort. Das README trennt diesen Migrationsnachweis von der im vorherigen Codex-Bericht separat über HTTP nachgestellten echten gehaltenen Änderung.

**Auswirkung:** Der Migrationstest wird für den hier beauftragten Nachweis zutreffend eingeordnet; sein grüner Ausgang wird nicht mehr als eigener 70-Sekunden-Nebenläufigkeitsversuch dargestellt. **Vorschlag:** Diese Trennung beibehalten. Ein weiterer langsamer Lauf mit echter gehaltener Kontaktänderung war für die reine Zeitgrenzenkorrektur nicht erforderlich und wurde hier nicht erneut durchgeführt.

## Bewusst in Kauf genommene Ausblendung

**Datei/Zeile:** `backend/README.md:294` und `:298`; `TODO.md:448`; Kommentar in `V0051__helpdesk_reply_audience_boundary.sql:13`.

Die Korrektur kann auch korrekt von der Anwendung gespeicherte Antworten aus der Zeit zwischen `I` und `B` neu berechnen. Hatte der Kontakt davor eine andere Organisation, bleibt die Zielgruppe dann leer. Die Länge dieses Bereichs ist **nicht pauschal zwölf Stunden**, sondern `o + 12`: beispielsweise 0 Stunden bei UTC−12, 12 Stunden bei UTC und bis 26 Stunden bei UTC+14. Das README benennt die bis zu 26 Stunden bei östlichen Zonen und die Voraussetzung einer damals richtigen Datenbankuhr. Die Nachstellung bestätigte den Preis und den Erhalt späterer Werte. Das entspricht Holgers ausdrücklich gewählter konservativer Regel und ist kein neuer Befund.

Die frühere Dokumentation zu TASK-083 bleibt als Beschreibung des damaligen Standes erhalten; der unmittelbar davor eingefügte TASK-084-Abschnitt erklärt die neue Grenze und ersetzt für den aktuellen Stand die alte Zeitzonenvoraussetzung.

## Mergefolge, Migrationsnachweise und Umfang

**Datei/Zeile:** `backend/scripts/lifecycle-check.sh:89` und `:91`; `backend/src/test/java/taskmanager/DatabaseFoundationTest.java:36`; `backend/src/test/java/taskmanager/ApplicationLifecycleTest.java:34`.

- PR #247 mit V0049 und V0050 kam am 3. November 2026 vor PR #249 auf main. Zwischen beiden wurde TASK-085 ohne weitere Migration integriert. V0051 folgt somit auf die beiden erforderlichen Migrationen.
- Die erwartete Versionsliste im Skript und in `DatabaseFoundationTest` endet mit V0049, V0050, V0051. Skript und Anwendungstest erwarten 48 Migrationen. Die bereits vorhandenen älteren Nummernlücken ändern diese Anzahl nicht.
- V0047 ist seit TASK-083 unverändert. TASK-084 ändert genau zehn Dateien: eine neue Migration, den Migrationstest, die drei Migrationsnachweise sowie Auftrag-, Entscheidungs- und Ergebnisdokumentation. Kein zusätzlicher Laufzeitpfad, Endpunkt, Vertrag oder Oberflächenumfang wurde eingeführt.

## Ausgeführte Prüfungen und Grenzen

Verwendet wurden Windows, Python 3.12, Node 24.22.0/npm 11.19.1, JDK 25.0.4.1 entsprechend dem Projekt sowie eine eigene lokale PostgreSQL-16.15-Instanz. Unter Windows erfolgten die beauftragten Python-Aufrufe mit dem verfügbaren Python und der Maven-Aufruf über `mvnw.cmd`. Abhängigkeiten kamen aus lokalen Caches.

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | Bestanden, einschließlich abschließender Wiederholung mit Bericht. |
| `python3 scripts/task_board.py --check` | Bestanden, einschließlich abschließender Wiederholung. |
| `python3 -m unittest discover -s tests -v` | 38 Tests, keine Fehler; einer unter Windows übersprungen, da er das Umschalten der Unix-Zeitzone benötigt. |
| Backend `./mvnw verify` mit PostgreSQL 16 | **BUILD SUCCESS, 516 Tests**, 0 Fehler, 0 übersprungen, 13 min 9 s. Darunter die vier Tests von `PortalAudienceMigrationTest`, Datenbankfundament und Anwendungslebenszyklus. |
| Frontend `npm ci --offline --no-audit --no-fund` | Erfolgreich, 58 Pakete installiert. |
| Frontend `npm run verify` | Erfolgreich: Typgenerierung, Typprüfung, 79 Tests und Produktionsbuild. Bestehender Hinweis auf ein JavaScript-Paket über 500 kB. |
| Frontend `npm run test:browser` | Paralleler Lauf: 179/181 bestanden; Zeitüberschreitungen in `tests/browser/tags.test.mjs:60` und `:79`. Vollständige Wiederholung mit `node --test --test-concurrency=1 tests/browser/*.test.mjs`: **181/181 bestanden**, keine Skips. Die Wiederholung änderte keinen Test- oder Produktcode. |
| Unabhängige Migrationsgegenproben | Alle vier Zeitzonenpaare einschließlich bereits abgeschlossener V0047/V0050 und Mikrosekundengrenzen bestanden; 48 Migrationen je Datenbank. |
| Neuer Zeitzonentest ohne V0051 | Erwarteter fachlicher Fehlschlag nachgewiesen. |
| Grenzformel über alle aktuellen Zeitzonennamen | 598 Zonen; Abstand 0 bis 26 Stunden, kein zu früher Wert. |
| Abschließende Dateiprüfung | `git diff --check` und gesonderte Leerraumprüfung der neuen Datei bestanden. Einzige neue Datei im Prüfklon ist dieser Bericht. main bei abschließender Aktualitätsprüfung unverändert. |

Nicht ausgeführt: `backend/scripts/lifecycle-check.sh` als eigener Shellprozess, Docker-/Vorführimage, der in CI referenzierte PostgreSQL-Container und Browserprüfungen gegen ein echtes Backend. Die Shell-Listen wurden gelesen und mit den erfolgreichen Datenbank-/Anwendungslebenszyklustests abgeglichen. Die Zeitzonenprüfungen verwendeten echte Flyway-Läufe auf PostgreSQL 16, aber modellierte Fachhistorien. Geänderte Systemuhren, historische Zeitzonen außerhalb des ausdrücklich beauftragten Bereichs und während eines Upgrades parallel weiterlaufende alte Anwendungsversionen wurden nicht untersucht. Die eigene PostgreSQL-Testinstanz wurde nach Abschluss beendet. Die lokalen Prüfhilfen und Protokolle liegen außerhalb des Repositorys.

## Empfehlung

**TASK-084 abnehmen.** Die Zeitgrenze ist für die beauftragten Versätze konservativ, repariert auch bereits von V0047 übersehene Altwerte und bewahrt Werte ab der neuen Grenze. Die wirksame Zeitzonenmatrix, der nachgewiesene Fehlschlag ohne V0051 und die präzisierte Testbeschreibung erfüllen H1 und H3. Der dokumentierte Preis von bis zu 26 Stunden ausgeblendeter Antworten entspricht Holgers Entscheidung. Keine weitere Änderung aus dieser Prüfung erforderlich; die fachliche Abnahme trägt Holger ein.
