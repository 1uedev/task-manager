# Unabhängige Prüfung TASK-026 / PR #109 und #110

**Ergebnis:** TASK-026 ist auf dem geprüften Stand technisch noch nicht abnahmefähig. Zwei eigene HTTP-Prüfversuche reproduzieren offene Befunde: **F1/P2**, ungültige Statuskategorie verursacht 500 statt 400; **F2/P3**, ein gesperrtes fremdes Projekt ist über seine Fehlerantwort von einer unbekannten Kennung unterscheidbar. Eine Rechteumgehung oder ein erfolgreicher Zugriff nach einem zuvor verbindlich gewordenen Entzug wurde in den geprüften Fällen nicht festgestellt.

## Grundlage

- Prüfer: Codex, getrennt vom Autor Claude; Sitzung `codex-20261020-projects-admin-review-01`, 20. Oktober 2026; eigener Klon und Branch `review/task-041-task-026-codex`.
- Geprüftes aktuelles main: [`cf89e43292ba6d7415aa507edc2d4ab8186a2401`](https://github.com/1uedev/task-manager/commit/cf89e43292ba6d7415aa507edc2d4ab8186a2401).
- Veröffentlichungsbasis nach erneutem Abgleich: `b1c88eebf36ed1f66ec685375734ec9109495100` (#111, Reservierung TASK-027). Der einzige zwischenzeitliche Unterschied ist TODO.md; Produkt- und Testquellen sind identisch zum ausgeführten Prüfstand. Der neuere Aufgabenstand wird unverändert übernommen.
- [PR #109](https://github.com/1uedev/task-manager/pull/109): Kopf `3676397b1930433a399e5883e02a977a0d3ddba5`, Merge `20596fe`; [Korrektur #110](https://github.com/1uedev/task-manager/pull/110): Kopf `7d47dd6cab4b9e433c4fe206c7b1fcd4b0427915`. Zwischen dem Kopf von #110 und dem geprüften main gibt es keinen Inhaltsunterschied.
- Gelesen: AGENTS.md, README.md, TODO.md mit Kriterien von TASK-026, DONE.md, docs/AI_WORKFLOW.md, einschlägige Anforderungen/Architekturverträge, docs/TECHNICAL_BASELINE.md, DEC-040/DEC-065 und der Abschnitt „Projekte (TASK-026)“ in backend/README.md. CLAUDE.md verweist auf AGENTS.md.
- Bezug: REQ-ORG-001, REQ-ORG-003, REQ-ACL-001, REQ-API-001. Die Voraussetzungen TASK-024/025/039/040 sind laut main abgenommen und integriert. Keine Aufgabenübernahme, Statusänderung oder fachliche Abnahme; TODO.md und DONE.md bleiben unverändert.

## Offene Befunde

### F1 — P2: Fehlende oder nullwertige Statuskategorie führt zu HTTP 500

**Fundstelle:** `backend/src/main/java/taskmanager/projects/ProjectConfiguration.java:95`, aufgerufen durch `internal/ProjectService.changeConfiguration` ab Zeile 141. `CATEGORIES` ist ein unveränderliches `Set.of(...)`; dessen `contains(null)` wirft eine NullPointerException, statt einen Validierungsgrund zu liefern.

**Reproduzierter Ablauf:**

1. Mit einem berechtigten Unternehmensverwalter ein neues Projekt anlegen; Projekt- und Konfigurationsversion sind 1.
2. Die ansonsten gültige Anfangskonfiguration nehmen und in der ersten Statusdefinition entweder `"category":"open"` durch `"category":null` ersetzen oder die Eigenschaft entfernen.
3. Jeweils `PUT /api/v1/projects/{id}/configuration` mit `If-Match: "1"` senden.
4. Beide Varianten antworten **500** statt der erwarteten **400**. Die Antwort bleibt allgemein (`server.internal_error`); kein Stacktrace und keine NullPointerException gelangen in den HTTP-Inhalt. Projektversion und Anzahl der Konfigurationsversionen bleiben jeweils 1.

**Auswirkung:** Eine normale fehlerhafte Eingabe durch berechtigte Konfigurationspflege wird zum internen Fehler. Der OpenAPI-Vertrag verlangt die Kategorie als Zeichenkette aus dem Enum; der Dienst verspricht für ungültige Konfiguration 400 mit Validierungsgründen. Keine Teiländerung oder Rechteausweitung beobachtet.

**Vorschlag:** Kategorie vor dem Set-Zugriff ausdrücklich auf null prüfen oder bereits am DTO validieren; beide Eingaben als `400 request.invalid`, etwa mit `statuses.category_invalid`, behandeln. Regression für fehlende und explizit nullwertige Kategorie ergänzen und unveränderte Projekt-, Konfigurations- und Auditdaten prüfen. Die eigene Probe ist `reviewNullOrMissingCategoryMustBeValidation400` im [Prüfpatch](review-evidence/task-026/project-review-probes.patch).

### F2 — P3: Fremdes Projekt verrät unter Zeilensperre einen Existenzunterschied

**Fundstellen:** `backend/src/main/java/taskmanager/projects/internal/ProjectService.java:211–228`, besonders die Prüfungen ab Zeile 218; `internal/JdbcProjectAccess.java:28–30`; `internal/ProjectRepository.java:35–38`. Der Ressourcenlieferant sperrt zuerst die Projektzeile und prüft erst danach die Mitgliedschaft. `permissions/internal/JdbcPermissions.java:222` setzt die Wartefrist auf 5 Sekunden; `inspect` behandelt eine Rechteverweigerung, aber keinen Datenbank-Sperrtimeout als unsichtbares Projekt.

**Reproduzierter Ablauf:**

1. Ein berechtigter Benutzer legt ein Projekt an. Ein anderer angemeldeter Benutzer hat weder Mitgliedschaft noch Projektrecht noch `projects.manage`.
2. `GET /api/v1/projects/{zufällige-unbekannte-UUID}` antwortet unmittelbar **404 `project.not_found`**.
3. Eine synthetische Transaktion hält die existierende fremde Projektzeile `FOR UPDATE`. Derselbe Außenstehende ruft deren Kennung ab.
4. `pg_blocking_pids` belegt die Wartebeziehung des echten HTTP-Aufrufs zu genau dieser Transaktion. Die Sperre bleibt länger als die tatsächliche Wartefrist bestehen. Nach dem Timeout lautet die Antwort **500 `server.internal_error`** statt 404.

**Auswirkung und Einstufung:** Die Existenz einer bekannten oder geratenen Projektkennung ist während eines konkurrierenden Schreibens am Antwortverhalten unterscheidbar. Es werden weder Projektname noch Konfiguration oder Mitglieder ausgegeben; der Zugriff wird nicht erlaubt. Wegen UUID-Voraussetzung und notwendiger konkurrierender Sperre ist dies ein begrenzter Informationskanal, **P3**. Die dokumentierte Schutzregel verlangt fremd und unbekannt gleichermaßen 404. Das ist sicherheitsrelevant und kein gewöhnlicher Hinweis auf die bekannten Funktionsgrenzen.

**Vorschlag:** Vor dem Warten auf die verborgene Projektzeile eine vorläufige negative Mitgliedschafts-/Rechteentscheidung ermöglichen oder den Unsichtbarkeitsfehler anderweitig gleichförmig halten. Positive Entscheidungen müssen weiterhin unter den verbindlichen Rechte-, Identitäts- und Projektsperren erneut geprüft werden. Die Sperre einfach zu entfernen würde den Entzugsschutz beschädigen. Regression für unbekannte und gleichzeitig gesperrte fremde Kennung ergänzen. Eigene Probe: `reviewBusyForeignProjectMustStayIndistinguishableFromUnknown` im [Prüfpatch](review-evidence/task-026/project-review-probes.patch).

## Projektgrenzen und Delegation

Im ungesperrten Zustand ergaben eigene HTTP-Gegenproben für ein fremdes Projekt sowie eine unbekannte Kennung jeweils 404: Projektdetail, Mitglieder, vorhandene und nicht vorhandene Konfigurationsversion sowie Umbenennen. Die Liste enthält für gewöhnliche Benutzer nur Mitgliedschaften mit `project.read`. Ein Recht auf Projekt A erlaubt Projekt B auch dann nicht, wenn das Konto Mitglied beider Projekte ist. Unternehmensweit vergebene Projektaktionen verlangen weiterhin die Mitgliedschaft; die Policy prüft beides. Ein Konto ohne Projektrecht gewinnt durch bloße Aufnahme als Mitglied noch keinen Zugriff.

Ein delegierter Verantwortlicher mit `project.manage` **darf nach DEC-065 Mitglieder aufnehmen und entfernen**. Im eigenen Versuch konnte er ein bekanntes aktives Konto aufnehmen; Anzahl der Rechteaktionen und Revisionsstand im Berechtigungsmodul blieben unverändert. Die Aufnahme kann vorhandene, passend zugeordnete Projektaktionen des neuen Mitglieds wirksam machen. Das ist eine ausdrücklich delegierte Möglichkeit zur Freigabe des eigenen Projekts, keine Befugnis zur Vergabe von Gruppenrechten. Ein nur für A berechtigtes aufgenommenes Mitglied konnte A, aber nicht B lesen. Der Verantwortliche konnte kein neues Unternehmensprojekt anlegen und kein fremdes Projekt verwalten.

Diese Delegation ist im vereinbarten Umfang vertretbar, sofern `project.manage` als Recht zur Mitgliederpflege und damit zur Projektfreigabe vergeben wird. Eine spätere Aufteilung in Konfigurations- und Mitgliedschaftsrechte wäre eine neue Produktentscheidung, kein hier festgestellter Fehler.

Der Unternehmensweg über `projects.manage` ist getrennt: Er erlaubt projektübergreifend Metadaten, Konfiguration und Mitgliedschaftspflege ohne eigene Mitgliedschaft. Der Projektweg prüft dagegen Mitgliedschaft und Projektaktionen. Das Unternehmensrecht gewährt keine zukünftigen Ticketrechte; Folgemodule müssen ihren eigenen Objektkontext verbindlich prüfen. Die dokumentierte Liste verlangt `project.read`; ein lediglich verwaltbares Projekt kann deshalb im Direktzugriff erreichbar sein und in dieser Liste fehlen. Das entspricht dem beschriebenen Vertrag und wird nicht als neuer Befund geführt.

## Rechteentzug, Mitgliedschaft und Sperrbeziehungen

Der Schreibweg benutzt **Rechte → Identität des Anfragenden → Projekt → gegebenenfalls neues Mitgliedskonto**. Der Projektadapter liefert seinen Objektkontext unter der bis zum Commit gehaltenen Projektzeilensperre. Entfernen einer Mitgliedschaft und Projektänderungen nehmen dieselbe Zeile exklusiv. Die Rechteentziehung wird durch die gemeinsame Revisionszeile vor den Identitäts-/Projektsperren geordnet.

Eigene Versuche mit tatsächlichen PostgreSQL-Wartebeziehungen:

| Versuch | Nachweis und Ergebnis |
| --- | --- |
| Mitgliedschaftsentzug vor wartendem Schreiben | Entzugstransaktion hält Projektzeile; HTTP-Schreiben wartet auf genau deren PID. Nach Entfernen und Commit: 404, Projektname unverändert; frische Mitglieder-/Konfigurationsabfragen 404, Liste leer. |
| Rechteentzug vor wartendem Schreiben | Entzug hält Revisionszeile; wartendes HTTP-Schreiben wird nach Commit verweigert (404 für den veralteten Kontext). Frische Anmeldung: Lesen weiter 200, Schreiben 403; keine Namensänderung. |
| Bereits erlaubtes Lesen vor Mitgliedschaftsentzug | `ProjectService.get` mit echtem aktiven Identitäts-/Sitzungs-/Rechtekontext hält die Projektzeile in einer Testtransaktion. Echtes HTTP-Entfernen wartet auf genau deren PID. Nach Lesercommit: Entfernen 200, späterer Zugriff des entfernten Mitglieds 404. Dieser Arm ist Dienstaufruf gegen HTTP, nicht zweimal HTTP. |
| Neues Mitgliedskonto gegen gegenseitige Kontoverwaltung | Kontosperre für B wird gehalten. HTTP-Aufnahme von B durch Projektverantwortlichen A erreicht diese Sperre, nachdem sie die Projektzeile hält. Gleichzeitiges HTTP-Zurücksetzen von A durch B wartet ebenfalls auf die Kontosperre. Die exakten PIDs werden geprüft. Nach Freigabe: beide 200, Mitgliedschaft gesetzt und A zum Passwortwechsel verpflichtet, kein Deadlock. |
| Zwei Änderungen mit gleichem ETag | Zwei Unternehmensverwalter schreiben mit `If-Match: "1"`: genau 200 und 412, Projektversion 2, genau ein Änderungs-Auditeintrag. |

Die Entzugsfixtures ändern Rechte/Mitgliedschaft direkt über synthetisches SQL, weil es keine Rechteverwaltungs-HTTP-API gibt. Sie bilden dabei dieselben Sperren, Revisionen und Projektversionen ab; sie ersetzen keinen Test sämtlicher denkbarer Port-Aufrufketten. Die Ergänzungen zeigen echte blockierende Datenbankbeziehungen, nicht nur zeitlich überlappende Threads. Keine Produktmethode oder Sperre wurde für diese Versuche ersetzt. Details und Quellpatch: [Prüfnachweise](review-evidence/task-026/README.md).

„Sofort“ bedeutet hier: Ein Entzug, der seine Sperre zuerst erworben hat und verbindlich wird, wird vom wartenden Aufruf gesehen. Eine bereits erlaubte laufende Transaktion beendet zuerst ihre Arbeit und blockiert den Entzug bis dahin. Eine zusätzliche Prüfung beim Commit ist laut DEC-065 nicht zugesagt.

## Konfiguration, Migration, Vertrag und Startprüfung

Konfigurationsstände werden unveränderlich versioniert; neue Konfiguration, Projektversionswechsel und Audit liegen in derselben Transaktion. Das ETag bezeichnet die gemeinsame Projektversion für Metadaten, Konfiguration und Mitglieder. Konflikte benötigen ein aktuelles `If-Match`; die eigenen Konkurrenzproben und die unveränderten API-/Vertragstests bestehen. Konfigurationsprüfung ist bis auf F1 konsistent mit der minimalen Feld-/Workflowkonfiguration. Bestehende Tests prüfen auch ungültige Konfigurationen, Anfangskonfiguration und historische Versionen; daraus folgt keine Vollständigkeit aller fehlerhaften JSON-Varianten.

V0011 legt `project`, `project_configuration`, `project_member` und `project_audit_event` an. Die überprüften Modulzugriffe verwenden die veröffentlichten Identitäts-/Rechteports; das Projektmodul besitzt seine eigenen Tabellen. `contracts/projects.openapi.yaml` beschreibt die Projekt-, Versions-, Fehler- und Konfliktantworten; F1 verletzt seine Eingabevalidierung und F2 die dokumentierte Unsichtbarkeit unter Konkurrenz.

Die Korrektur #110 passt `backend/scripts/lifecycle-check.sh` an **zehn** erfolgreiche Migrationen, V0011 und die vier Projekttabellen an. Der Bootstrap erwartet jetzt fünf Verwaltungsrechte einschließlich `projects.manage`; es werden keine Projekte angelegt. Der CI-Lauf auf dem exakten Kopf von #110 bestätigt PostgreSQL **18.6** sowie Start, Stopp und Neustart auf leerer Datenbank. Ich habe das Shellskript unter Windows gelesen und die CI-Protokolle geprüft, aber keinen eigenen Linux-Lifecycle-Lauf ausgeführt.

## Tatsächlich ausgeführte Prüfungen

- Eigener unveränderter Backendlauf `clean verify`, Java **25.0.4.1+1**, echte lokale PostgreSQL **18.6**: **154 Tests, 0 Fehler, 0 übersprungen**.
- Eigene temporäre Projektproben: **8 Tests, 6 erfolgreich, 2 Assertions fehlgeschlagen**, keine Ausführungsfehler oder übersprungenen Tests. Die beiden Fehlschläge sind F1 und F2; sie sind keine Fehler beim Aufbau des Versuchs. Gemeinsam mit TASK-041: 12 Tests, 2 fehlgeschlagen.
- Teständerungen danach bytegleich zurückgesetzt, zusätzliche Testklasse entfernt. Originalklassen `AdministrationRightsApiTest`, `AdministrationRightsWiringTest`, `ProjectsApiTest` erneut mit `clean test`: **23 Tests erfolgreich**. Kein Produktcode für die Proben geändert.
- Gelesene Backend-CI: [#110, Lauf 39402996996](https://github.com/1uedev/task-manager/actions/runs/39402996996), **154 Tests erfolgreich**, PostgreSQL 18.6, `Lifecycle check passed`. Kein eigener Nachbau dieses Runner-Laufs.
- Wiederholbare Prüfpatches und ausgewählte Laufzeilen: [review-evidence/task-026](review-evidence/task-026/README.md). Private Rechnerpfade, Kennwörter und Sitzungstoken sind nicht enthalten.
- Abschlussprüfung der Dokumentation: Repository-Validator und Aufgabenübersicht erfolgreich, **25 Python-Regressionstests erfolgreich**, `git diff --cached --check` ohne Befund. Die eigene synthetische PostgreSQL-Instanz wurde kontrolliert gestoppt.

## Bekannte Grenzen und Übergabe

Die in DEC-065 und backend/README.md festgehaltenen Grenzen sind für diesen ersten internen Abschnitt vertretbar: keine Portalkonten/Organisationen, Tickets oder Oberfläche, keine Löschung/Archivierung, Mitglieder nur als Kennungen, noch keine Prüfung bestehender Tickets bei Konfigurationswechsel und keine erneute Identitätsprüfung beim Commit. Das Neuerstellen älterer Entwicklungsdatenbanken für `projects.manage` ist eine Entwicklungsregel, keine qualifizierte Migration produktiver Bestandsdaten. Keine dieser Grenzen wird als neu entdeckter Befund ausgegeben oder zur Kundenbetriebsfreigabe umgedeutet.

**Technische Schlussfolgerung:** Erst F1 und F2 beheben und die jeweiligen HTTP-Regressionen sowie die Entzugs-/Sperrtests erneut ausführen; dann ist eine erneute technische Bewertung möglich. TASK-026 ist derzeit technisch nicht abnahmefähig. Die fachliche Abnahme bleibt Holger vorbehalten. Dieser Bericht verändert weder den Aufgabenstatus noch DONE.md.
