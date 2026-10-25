# Unabhängige Prüfung TASK-044 / PR #151

Stand: 25. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261025-review-044-01`, Branch `claude/project-thread-kqr6e8`, eigener Arbeitsbereich. Auftrag: Holgers „Claude prüft“ für TASK-044 als Ersatz für eine Prüfung durch Codex (DEC-040). Die Sitzung, die TASK-044 umgesetzt hat, ist eine andere als diese.

Geprüft wurde die Projektkonfiguration mit Tickettypen, Abläufen je Tickettyp, Pflichtfeldern je Übergang und eigenen Feldern nach DEC-073 gegen die Abnahmekriterien aus TODO.md. Ergebnis: **ein Befund P2 zu verborgenen Feldern und vier Befunde P3**. Die vorhandenen Tests sind grün. Fünf zusätzliche Prüftests gegen den unveränderten Code belegen die Befunde; ein sechster belegt als Gegenprobe, dass die Veröffentlichung mit Zuordnung auch bei gleichzeitigen Ticketänderungen kein Ticket in einem unbekannten Status zurücklässt.

## Grundlage und Abgrenzung

- Ergebnis: [PR #151](https://github.com/1uedev/task-manager/pull/151), auf main als `8b7928d7510caa4f70e1902e7a6546e28b882503`.
- Prüfbasis: main `37580d001210d21f1fa30f5726f02ac6227a10ba` (nach TASK-051). Spätere PRs haben TicketService, TicketController und JdbcTicketAccess für Anhänge erweitert; die Fundstellen unten beziehen sich auf diese Prüfbasis.
- Maßstab: AGENTS.md, TODO.md (Eintrag TASK-044 mit Abnahmekriterien), DEC-073, [PROJECT_CONFIGURATION.md](PROJECT_CONFIGURATION.md), die Beispiele A und B sowie die Prüffälle F-03 bis F-06 aus [DOMAIN_MODEL.md](DOMAIN_MODEL.md), REQ-ORG-003, REQ-ORG-004, REQ-TKT-001, REQ-ACL-003 und die Verträge `projects`, `tickets`, `search` und `permissions`.
- Die mit DEC-073 offen gelassenen Entscheidungen (Löschen nie verwendeter Felder, nachträgliches Erzwingen von Pflichtfeldern, Direktveröffentlichung neben dem Entwurf) und die bewusst ausgeschlossenen Teile (Formulare, Portal, Automationen, Vorlagen, Mehrfachauswahl, Dezimalzahl, Verschieben, Archivierung) werden nicht als Befunde gezählt.
- Diese Prüfung ändert keinen Produktcode und erklärt keine Abnahme.

## Abnahmekriterien

| Kriterium aus TODO.md | Ergebnis |
|---|---|
| Zwei Projekte nach Beispiel A und B einrichtbar und bedienbar | Erfüllt im Rahmen von TASK-044. `twoProjectsRunTheirOwnTypesWorkflowsAndFieldsSideBySide` richtet beide Projekte über die echte API ein und bedient sie. Teile der Beispiele, die Portal, Kundenorganisationen oder Versandzustand voraussetzen, sind nicht Teil der Aufgabe. |
| F-03 bis F-06 als Regression | Erfüllt. Die Zuordnung in PROJECT_CONFIGURATION.md stimmt mit den Tests überein; alle laufen grün. |
| Nicht erlaubter Übergang und fehlendes Pflichtfeld lassen das Ticket unverändert | Erfüllt. Alle Prüfungen laufen vor dem ersten Schreibzugriff; der Test prüft Status und Version nach der Ablehnung. |
| Verborgene eigene Felder bleiben in API, Historie und Suche verborgen | Für Werte erfüllt, aber mit einer Lücke beim Übergang: siehe **F1**. Antwort, Historie und Suche (Treffer, Rang, Anzahl, Filter) filtern nach der Leseregel; das deckt `customFieldRulesHideValuesInAnswersHistoryAndSearchLikeStandardFields` ab. |
| Bestandstickets bleiben nach jeder Veröffentlichung gültig | Erfüllt. Ein Status oder Typ, den Tickets noch verwenden, geht nur mit Zuordnung; ohne sie 409 und keine Änderung. Auch unter gleichzeitigen Ticketänderungen bestätigt (Gegenprobe E6). Die Grenzen der Zuordnung stehen in **F4**. |

## Befunde

### F1 · P2: Ein Übergang mit verborgenem Pflichtfeld verrät, ob das Feld gefüllt ist

**Fundstellen:** [TicketService.java:319–331](https://github.com/1uedev/task-manager/blob/37580d001210d21f1fa30f5726f02ac6227a10ba/backend/src/main/java/taskmanager/tickets/internal/TicketService.java#L319) und `hasValue` in Zeile 429–438.

Verlangt ein Übergang ein Feld, prüft der Server nur, ob es nach der Änderung einen Wert hat. Ob die anfragende Person dieses Feld lesen oder schreiben darf, spielt keine Rolle. Eine Person mit dem Recht auf den Statuswechsel, aber ohne Leseregel für das verlangte Feld, bekommt deshalb zwei unterschiedliche Antworten: 400 mit `custom.interne_ursache.required_for_transition`, wenn das verborgene Feld leer ist, und 200 mit vollzogenem Wechsel, wenn es gefüllt ist. Für `description` und `assignee` gilt dasselbe.

**Nachweis (E2):** Projekt nach Beispiel A. Das Kundenkonto hat alle festen Felder und `custom.prioritaet`, aber keine Regel für `custom.interne_ursache`. Der Support setzt beide Tickets auf „In Bearbeitung“ und trägt nur bei Ticket 2 eine interne Ursache ein. Der Wechsel nach „Gelöst“ durch das Kundenkonto endet bei Ticket 1 mit 400 und dem Namen des verborgenen Feldes, bei Ticket 2 mit 200.

**Auswirkung:** Der Wert selbst wird nicht preisgegeben, aber für jedes verborgene Pflichtfeld eines Übergangs lässt sich erfahren, ob es gefüllt ist. Zusätzlich kann die Person einen Schritt vollziehen, dessen Bedingung sie weder sehen noch erfüllen kann. DOMAIN_MODEL.md verlangt für verborgene Pflichtfelder eine Meldung, die „den Konfigurationskonflikt, nicht den geheimen Wert“ nennt. Die Anlage löst das bereits so: dort antwortet der Server mit 409 `ticket.required_field_inaccessible`, wenn ein Pflichtfeld für die anfragende Person nicht schreibbar ist, unabhängig vom Wert.

**Vorschlag:** Beim Übergang dieselbe Regel wie bei der Anlage anwenden: Verlangt der Übergang ein Feld, das die anfragende Person nicht lesen oder nicht schreiben darf, antwortet der Server vor jeder Wertprüfung mit 409 `ticket.required_field_inaccessible`. Ob der Schritt dann für diese Gruppe überhaupt möglich sein soll, ist eine fachliche Frage an Holger. Den Fall als Regression aufnehmen.

### F2 · P3: Ganzzahlfelder nehmen −2^63 an

**Fundstelle:** [ProjectConfiguration.java:416](https://github.com/1uedev/task-manager/blob/37580d001210d21f1fa30f5726f02ac6227a10ba/backend/src/main/java/taskmanager/projects/ProjectConfiguration.java#L416).

Die Grenze ±(2^53 − 1) wird mit `Math.abs(value) > INTEGER_MAX` geprüft. Für `Long.MIN_VALUE` liefert `Math.abs` wieder `Long.MIN_VALUE`, also eine negative Zahl, und die Prüfung schlägt nicht an.

**Nachweis (E1):** `"restdauer": 9007199254740992` wird mit 400 abgewiesen, `"restdauer": -9223372036854775808` mit 201 gespeichert und genau so zurückgegeben.

**Auswirkung:** Der gespeicherte Wert verletzt die dokumentierte Grenze. Ein JavaScript Client liest ihn nur gerundet; die Oberfläche zeigt einen anderen Wert, und ein erneutes Speichern aus der Oberfläche würde an der Prüfung im Browser scheitern. Keine Rechtefrage.

**Vorschlag:** Die Grenze ohne `Math.abs` prüfen (`value > INTEGER_MAX || value < -INTEGER_MAX`) und den Randwert in `ProjectConfigurationTest` aufnehmen.

### F3 · P3: Zwischen Typen mit getrennten Abläufen ist kein Typwechsel möglich

**Fundstelle:** [TicketService.java:315–321](https://github.com/1uedev/task-manager/blob/37580d001210d21f1fa30f5726f02ac6227a10ba/backend/src/main/java/taskmanager/tickets/internal/TicketService.java#L315).

Ein Wechsel nur des Typs verlangt, dass der aktuelle Status im Ablauf des neuen Typs vorkommt. Ein Wechsel von Typ und Status zusammen verlangt einen Übergang vom alten Status im Ablauf des neuen Typs. Haben zwei Typen eigene Abläufe ohne gemeinsamen Status, ist beides unmöglich.

**Nachweis (E3):** Typ `a` mit Ablauf `a1 → a2`, Typ `b` mit Ablauf `b1 → b2`. Ein Ticket vom Typ `a` in `a1`: nur Typ ändern gibt 400 `type.status_unavailable`, Typ und Status `b1` zusammen gibt 409 `ticket.transition_not_allowed`.

**Auswirkung:** Ein falsch eingeordnetes Ticket lässt sich nicht umstellen, nur neu anlegen. DEC-073 (2) beschreibt die erste Hälfte der Regel ausdrücklich; die Folge für getrennte Abläufe nennt es nicht. DOMAIN_MODEL.md sieht den späteren Typwechsel „mit gültiger Feld-/Statuszuordnung“ vor.

**Vorschlag:** Holger entscheiden lassen, ob ein Typwechsel mit gleichzeitiger Wahl eines Status aus dem neuen Ablauf ohne Übergang erlaubt sein soll (etwa auf dessen ersten Status oder auf einen gültigen Status nach Wahl). Bis dahin die Grenze in PROJECT_CONFIGURATION.md nennen.

### F4 · P3: Die Statuszuordnung gilt je Status, nicht je Typ und Status

**Fundstelle:** [ProjectService.java:294–305](https://github.com/1uedev/task-manager/blob/37580d001210d21f1fa30f5726f02ac6227a10ba/backend/src/main/java/taskmanager/projects/internal/ProjectService.java#L294).

`statusMapping` ordnet jedem alten Status genau ein Ziel zu. Der Zielstatus muss aber im Ablauf des jeweiligen Typs liegen. Verwenden zwei Typen mit eigenen Abläufen denselben Status und führen ihre neuen Abläufe zu verschiedenen Zielen, gibt es keine Zuordnung, mit der die Veröffentlichung gelingt.

**Nachweis (E4):** Typen `a` und `b` mit eigenen Abläufen, beide beginnen in `x`. Der Entwurf entfernt `x`; `a` kennt danach nur `a1`, `b` nur `b1`. Je ein Ticket beider Typen steht in `x`. Sowohl `x → a1` als auch `x → b1` enden mit 409 `status.mapping_invalid:x`.

**Auswirkung:** Kein Datenverlust; die Veröffentlichung bleibt sicher abgewiesen. Die Verwaltung muss die betroffenen Tickets aber vorher von Hand verschieben, was bei vielen Tickets mühsam ist und in der Oberfläche nicht erklärt wird.

**Vorschlag:** Die Zuordnung je Paar aus Typ und Status zulassen (zum Beispiel `statusMapping` mit Schlüssel `<typ>:<status>` neben dem bisherigen Schlüssel) oder die Grenze in Vertrag, Dokumentation und Zuordnungsdialog ausdrücklich nennen.

### F5 · P3: Ein unbekanntes eigenes Feld antwortet mit 403 statt 400

**Fundstellen:** [TicketService.java:191–198](https://github.com/1uedev/task-manager/blob/37580d001210d21f1fa30f5726f02ac6227a10ba/backend/src/main/java/taskmanager/tickets/internal/TicketService.java#L191) (Anlage) und Zeile 256–258 (Änderung).

Der Server prüft die Schreibregeln für alle genannten Felder, bevor er nachsieht, ob die Konfiguration den Schlüssel kennt. Für einen unbekannten Schlüssel gibt es keine Regel, also 403 `auth.forbidden`. Die spätere Prüfung mit `customFields.unknown`, die der Vertrag nennt, wird für einen gültig geschriebenen Schlüssel nie erreicht.

**Nachweis (E5):** Anlage mit `"customFields": {"gibt_es_nicht": "x"}` durch eine Person mit vollen Rechten: 403 statt 400 `customFields.unknown`.

**Auswirkung:** Ein Client mit veralteter Konfiguration bekommt eine irreführende Meldung über fehlende Rechte. Kein Rechteproblem, denn das Ergebnis bleibt eine Ablehnung.

**Vorschlag:** Unbekannte Schlüssel nach dem Sperren des Projekts und vor `require` abweisen, oder Vertrag und Dokumentation an das tatsächliche Verhalten anpassen.

## Hinweise ohne Befund

- **Typen ohne eigenen Ablauf kennen alle Status.** `statusKeysOf` liefert für sie die ganze Statusliste, also auch Status, die nur im Ablauf eines anderen Typs vorkommen. Eine Zuordnung oder eine Veröffentlichung kann ein Ticket so in einen Status bringen, aus dem sein Ablauf keinen Übergang kennt. Das ist mit DEC-073 vereinbar, sollte aber in PROJECT_CONFIGURATION.md stehen.
- **Abweichung vom Vorschlag in DOMAIN_MODEL.md.** Dort wechseln Bestandstickets erst über eine geprüfte Zuordnung auf eine neue Version, und vor einem neuen Pflichtfeld werden Bestandslücken angezeigt. TASK-044 hat bewusst die sofortige Geltung gewählt und die Bestandslücken zu Holgers offenen Entscheidungen gelegt. Kein Befund, aber bei der fachlichen Abnahme zu bestätigen.
- **Mengenangaben in der Veröffentlichung.** `GET /configuration/usage` und die 409 Antwort nennen Zahlen je Typ und Status. Wer nur `project.manage` ohne Leseregel für `status` oder `type` hat, erfährt so Mengen über Felder, die er im Ticket nicht sieht. Für die Zuordnung ist das nötig; ob es so bleiben soll, ist eine fachliche Frage.
- **Suchereignis bei der Zuordnung.** `TicketUsageAdapter.migrate` liest die Konfiguration, bevor die neue Version geschrieben ist, und gibt dem Suchereignis die alte Version mit. Das ist heute harmlos, weil neue Felder vor ihrer Veröffentlichung keine Werte haben können. Bei einer späteren Erweiterung (etwa Umwandlung von Werten) sollte die neue Version übergeben werden.

## Weitere Prüfergebnisse

| Bereich | Ergebnis der Durchsicht |
|---|---|
| Sperren und Last | Ticketänderungen halten die Projektzeile `FOR SHARE` ab der ersten Rechteprüfung bis zum Commit; Entwurf, Veröffentlichung und Zuordnung halten sie `FOR UPDATE`. Damit sehen Prüfung und Schreiben immer dieselbe Konfiguration. Gegenprobe E6: 60 Tickets, 61 gleichzeitige Anfragen (60 Statuswechsel in den zu entfernenden Status und die Veröffentlichung). Im ersten Lauf liefen 48 Wechsel vorher und wurden mit Historie verschoben, 12 kamen danach und wurden mit 400 abgewiesen; im zweiten Lauf wurden 55 verschoben. In beiden Läufen steht danach kein Ticket in einem unbekannten Status. |
| Entwurf | Revision im ETag, 412 bei veralteter Revision, 409 bei veralteter Basisversion, ein Entwurf je Projekt (Primärschlüssel in V0018). Lesen und Schreiben verlangen `project.manage`. |
| Eigene Felder nie entfernen | `incompatibilities` vergleicht mit der veröffentlichten Vorgängerversion. Da jede Version gegen ihre Vorgängerin geprüft wird, gilt die Regel über alle Versionen. Inaktive Felder und Optionen behalten ihre Werte; erneutes Schreiben desselben Werts bleibt erlaubt (F-06). |
| Rechte | Eigene Felder laufen als `custom.<schlüssel>` durch denselben Policy-Pfad wie feste Felder (ohne ALLOW verborgen, DENY gewinnt). Regeln für eigene Felder verlangen ein sichtbares Projekt; die 404 Prüfung läuft vor der Feldprüfung. |
| Historie und Suche | Historie und Suche fragen die Leseregeln der eigenen Felder nach dem Sperren des Projekts ab. Suchwörter, Rang, Filter und `customFields` im Treffer nutzen nur lesbare Felder; je Projekt getrennt. V0020 leert `search_state`, der Start baut die Projektion neu. |
| Ältere Konfigurationsdokumente | Fehlende Teile lesen sich als leere Listen; `theRegisteredConfigurationOfAnOldProjectReadsAsOneWithoutNewFeatures` belegt das mit einem gespeicherten Dokument aus TASK-026. |
| Modulgrenzen | Das Projektmodul erreicht Tickets nur über den Port `TicketUsage`; `ModuleStructureTest` ist grün. |
| Oberfläche | Der Editor sperrt das Entfernen veröffentlichter Felder und Optionen; der Zuordnungsdialog arbeitet je Status (passend zu F4). Die Ticketseite zeigt nur Felder, die der Server liefert, und bietet nur aktive, schreibbare Felder zur Eingabe an. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Backend `verify` auf unverändertem Produkt- und Testcode | Selbst ausgeführt: **303 Tests, 0 Fehler, 0 übersprungen**, bis auf `DatabaseFoundationTest.runsAgainstThePinnedPostgresqlVersion`, der erwartungsgemäß an der lokalen Version 16.15 statt 18.6 scheitert; mit `-Dtaskmanager.test.server-version=16.15` erneut ausgeführt grün. Einzelheiten in [results.txt](review-evidence/task-044/results.txt). Java 21 mit `-Djava.version=21`, eigene PostgreSQL 16.15 mit synthetischen Daten; kein Nachweis für Java 25 und PostgreSQL 18.6. |
| `ProjectConfigurationApiTest` und `ProjectConfigurationTest` einzeln | Selbst ausgeführt: 11 und 13 Tests, alle grün. |
| Sechs zusätzliche Prüftests | Selbst ausgeführt in einer temporären Testklasse: E1 bis E5 schlagen wie beschrieben fehl und belegen F1 bis F5, E6 ist grün. Quelle unter [review-evidence/task-044](review-evidence/task-044/README.md); die Klasse ist danach aus dem Testbaum entfernt. |
| Frontend | Selbst ausgeführt: `npm ci --ignore-scripts`, `npm run verify` mit 75 Tests, Typprüfung und Build; `npm run test:browser` mit 102 bestandenen Tests, keine übersprungen. Node 22. Die Browsertests nutzen eine nachgebildete API. |
| Repository | Validator, Aufgabenübersicht, Python Regressionstests und `git diff --check` für diesen PR. |

Nicht geprüft: Lebenszyklus mit Docker und der Workflow `Image`, ein Lasttest über die Gegenprobe hinaus, Portal und Formulare (nicht Teil der Aufgabe).

## Übergabe

Dieser PR veröffentlicht nur Bericht und Prüftests. TODO.md und DONE.md bleiben unverändert; TASK-044 bleibt BEREIT ZUR PRÜFUNG. Holger entscheidet über die fachliche Abnahme, darüber, ob F1 bis F5 vorher behoben werden, und über die Fragen in F1 (Übergang mit verborgenem Pflichtfeld) und F3 (Typwechsel). Eine Korrektur braucht nach AGENTS.md eine eigene Reservierung.
