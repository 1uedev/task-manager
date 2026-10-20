# Unabhängige Prüfung TASK-027 / PR #112

Stand: 20. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261020-tickets-review-01`, Branch `review/task-027-pr-112-claude`, eigener Arbeitsbereich. Diese Sitzung war an der Umsetzung von TASK-027 nicht beteiligt. Holger hat diese Prüfung durch Claude gewählt, weil Codex wegen eines Nutzungslimits nicht verfügbar ist; sie ersetzt die nach DEC-040 vorgesehene Prüfung durch eine getrennte KI. Ob Codex später zusätzlich prüft, bleibt offen.

**Ergebnis:** Projektgrenzen, Sperren bei gleichzeitigem Entzug, Versionskonflikte und die Historie halten in allen geprüften Fällen. Kein verborgener Feldwert wurde offengelegt, kein Zugriff nach einem zuvor verbindlich gewordenen Entzug erlaubt. Es gibt **einen Befund P2** (die Rechte `ticket.create` oder `ticket.edit` ohne `ticket.read` sind nicht nutzbar und enden als 404 mit Rückabwicklung) und **zwei Befunde P3** (NUL Zeichen in Texten ergibt 500; unbekannte Felder bei der Anlage werden still ignoriert) sowie **einen Hinweis** zu Metadaten verborgener Änderungen. Der bekannte Befund F2 aus TASK-026 wirkt auf main auch im Ticketweg und ist mit PR #114 behoben.

## Grundlage und Abgrenzung

- Geprüft: [PR #112](https://github.com/1uedev/task-manager/pull/112), auf main als `9a6a20048189e89cb5d23afdafef0fd0a2826305`. Zum Abgleich der bekannten Befunde zusätzlich der Kopf von [PR #114](https://github.com/1uedev/task-manager/pull/114), `11494764d2fbbc1208f7a33fda4fde197a024015` (noch nicht gemergt).
- Maßstab: AGENTS.md, CLAUDE.md, TODO.md (Kriterien von TASK-027), DEC-040, DEC-065, DEC-067, REQ-TKT-001, REQ-TKT-005, REQ-ACL-003, REQ-API-001, backend/README.md Abschnitt „Tickets (TASK-027)“, backend/PERMISSIONS.md, `contracts/tickets.openapi.yaml`.
- Gelesen: `tickets/internal/TicketService.java`, `TicketRepository.java`, `Ticket.java`, `tickets/web/TicketController.java`, V0012, `projects/ProjectAccess.java`, `JdbcProjectAccess.java`, `permissions/internal/JdbcPermissions.java`, `Policy.java`, `IdentityPermissionsAdapter.java`, die Sperrpfade von Deaktivierung und Passwortreset in `AccountService.java` sowie die vorhandenen Tests.
- Prüfschwerpunkte laut Auftrag: Projektgrenzen, verborgene Felder (auch in der Historie), Sperren bei gleichzeitigem Rechteentzug, Historie.
- Bereits bekannt und nicht erneut gezählt: die beiden Befunde der Codex Prüfung von TASK-026 (fehlende Statuskategorie ergibt 500; gesperrtes fremdes Projekt verrät seine Existenz). Ihr Stand im Ticketweg steht unten unter „Bekannte Befunde aus TASK-026“.
- Die in DEC-067 genannten Grenzen (Status, den eine neuere Konfiguration nicht kennt; Zuständigkeit nach Entfernen des Mitglieds; keine Löschung; Prüfung zum Sperrzeitpunkt statt beim Commit) werden nicht als Befunde gezählt.

## Befunde

### F1 — P2: `ticket.create` oder `ticket.edit` ohne `ticket.read` endet als 404 und wird zurückgerollt

**Fundstellen:** `backend/src/main/java/taskmanager/tickets/internal/TicketService.java:166` (Anlage), `:229` und `:235` (Änderung), jeweils über `readable(...)` in Zeile 288.

DEC-067 (1) und backend/README.md sagen: Nur **ohne eine der drei** Aktionen antwortet das Projekt wie ein unbekanntes mit 404. `create` und `update` lassen entsprechend ein Konto mit nur `ticket.create` beziehungsweise nur `ticket.edit` bis zum Schreiben durch (Zeilen 118 und 178). Zum Aufbau der Antwort ruft der Dienst danach aber `readable(...)` auf, und diese Methode wirft ohne `ticket.read` die Ausnahme `project.not_found`. Weil das innerhalb der Transaktion geschieht, wird das schon geschriebene Ticket beziehungsweise die Änderung samt Historie und Nummer zurückgerollt.

**Nachweis:** Eigene HTTP Proben gegen die unveränderten Dienste auf PostgreSQL:
- `reviewCreateWithoutReadActionMustCreateTheTicket`: Mitglied mit `ticket.create` und allen Feldregeln, ohne `ticket.read`. Erwartet 201, erhalten **404 `project.not_found`**, danach 0 Tickets im Projekt.
- `reviewEditWithoutReadActionMustChangeTheTicket`: Mitglied mit `ticket.edit` und allen Feldregeln, ohne `ticket.read`. Erwartet 200, erhalten **404 `project.not_found`**, Titel unverändert.

**Auswirkung:** Eine nach Vertrag gültige Rechtevergabe, etwa „darf melden, aber nicht lesen“ für Meldende, ist nicht nutzbar, und die Antwort widerspricht dem Vertrag: Dasselbe Projekt gilt in derselben Anfrage zuerst als vorhanden und dann als unbekannt. Es wird nichts offengelegt und nichts gespeichert; ein Sicherheitsproblem ist das nicht.

**Vorschlag:** Für die Antwort die Leseentscheidung als Momentaufnahme verwenden, ohne bei fehlendem `ticket.read` zu werfen (dann eine Antwort ohne lesbare Felder, aber mit Kennung, Nummer und Version), oder die Kombination ausdrücklich ausschließen und in DEC-067 und Vertrag nachführen. In beiden Fällen Regressionen für „nur Anlage“ und „nur Änderung“ ergänzen.

### F2 — P3: NUL Zeichen in Titel oder Beschreibung führt zu HTTP 500

**Fundstellen:** `TicketService.java:240` (`title`) und `:249` (`description`); PostgreSQL lehnt `\u0000` in `text` mit SQLSTATE 22021 ab.

**Nachweis:** `reviewNulCharacterInTextMustBeValidation400`: Anlage mit Titel `"a\u0000b"` und Anlage mit Beschreibung `"x\u0000y"` antworten beide **500 `server.internal_error`** statt 400. Das Protokoll zeigt `invalid byte sequence for encoding "UTF8": 0x00`. Kein Stacktrace in der Antwort, kein Ticket gespeichert. Für `PATCH` gilt nach dem Code derselbe Weg.

**Auswirkung:** Eine normale fehlerhafte Eingabe wird zum Serverfehler, wie F1 der Prüfung von TASK-026. Vermutlich betrifft das auch andere Textfelder (Projektname, Beschreibung); das wurde hier nicht geprüft.

**Vorschlag:** Steuerzeichen, mindestens `\u0000`, als `title.invalid` beziehungsweise `description.invalid` ablehnen; sinnvoll wäre eine gemeinsame Prüfung für alle Textfelder.

### F3 — P3: Unbekannte Felder bei der Anlage werden still ignoriert

**Fundstelle:** `tickets/web/TicketController.java:81` (`CreateRequest`); der Vertrag erklärt `CreateRequest` mit `additionalProperties: false`, die Änderung prüft unbekannte Felder dagegen ausdrücklich (`fields.unknown`).

**Nachweis:** `reviewCreationWithAnUnknownFieldMustBeRefused`: Anlage mit `"status": "done"` und `"priority": "hoch"` antwortet **201**, das Ticket steht im Anfangsstatus `new`, beide Angaben sind verworfen.

**Auswirkung:** Ein Client, der bei der Anlage einen Status oder ein noch nicht vorhandenes Feld mitgibt, glaubt es gespeichert. Keine Rechteumgehung: `status` wird nicht übernommen.

**Vorschlag:** Wie bei `PATCH` den Rumpf als JSON lesen und unbekannte Schlüssel mit `400 request.invalid` und `fields.unknown` ablehnen.

### Hinweis H1: Version und `updatedBy` zeigen Änderungen verborgener Felder

`reviewHiddenChangeShowsInVersionAndUpdatedBy` (bestanden, dokumentiert das Verhalten): Ändert jemand nur die für eine andere Person verborgene Beschreibung, sieht diese Person danach ETag `"2"`, neues `updatedAt` und `updatedBy`, während ihre Historie nur Version 1 enthält. Der Wert selbst bleibt verborgen. REQ-ACL-003 verbietet, ein verborgenes Feld „indirekt“ offenzulegen; ob schon die Tatsache, Zeitpunkt und Urheber einer Änderung darunter fallen, ist eine fachliche Frage. Die Version wird für `If-Match` gebraucht, eine eigene sichtbare Version wäre ein größerer Umbau. Vorschlag: die Grenze in DEC-067 und backend/README.md benennen oder Holger entscheiden lassen. Kein Befund.

## Bekannte Befunde aus TASK-026

| Befund | Stand im Ticketweg auf main | Stand mit PR #114 |
|---|---|---|
| Fehlende Statuskategorie ergibt 500 | Betrifft nur die Projektkonfiguration; ein Ticket kann keine Kategorie setzen. | Behoben in `ProjectConfiguration.violations`, im Code bestätigt. |
| Gesperrtes fremdes Projekt verrät seine Existenz | `reviewLockedForeignProjectMustAnswerLikeAnUnknownOne`: Außenstehender mit unternehmensweiten Ticketrechten, Projektzeile durch eine andere Transaktion gesperrt. Auf main **503 `ticket.busy`** nach 5 Sekunden statt sofort 404. | Dieselbe Probe auf dem Kopf von #114 **bestanden**: sofort 404 `project.not_found`. Die Vorprüfung der Mitgliedschaft vor der Sperre in `JdbcProjectAccess` gilt auch für Tickets. |

Damit sind beide Punkte durch #114 erledigt, sobald #114 auf main ist.

## Weitere Prüfergebnisse

| Bereich | Ergebnis und Nachweis |
|---|---|
| Projektgrenzen | Alle Ticketabfragen filtern nach Unternehmen und Projekt. `reviewNumbersAndAssigneesStayInsideTheirProject` (bestanden): Ein Konto mit unternehmensweiten Ticketrechten und Mitgliedschaft in A und B erhält für eine nur in A vorhandene Nummer in B 404 `ticket.not_found` bei Lesen, Ändern und Historie; ein Mitglied nur von B kann in A nicht zuständig werden (`assignee.not_member`). Die vorhandenen Tests decken Außenstehende, fremde Projekte, unbekannte Kennungen und `projects.manage` ohne Ticketzugriff ab. |
| Verborgene Felder | Schreiben setzt in `Policy` Lesen voraus; `require` prüft genau die mitgegebenen Felder vor dem Lesen des Tickets. Antwort, Liste und Historie filtern nach der Leseentscheidung derselben Transaktion; Versionen mit nur verborgenen Änderungen fehlen in der Historie. Fehlercodes (`description.required`, `assignee.not_member`, `ticket.transition_not_allowed`) entstehen nur für Felder, die die Person schreiben und damit lesen darf. Keine Wertoffenlegung gefunden; siehe H1 zu Metadaten. |
| Gleichzeitiger Rechteentzug | `reviewChangeWaitsForConcurrentRightsRevocationAndIsRefused` (bestanden): Eine Transaktion hält die Revisionszeile `FOR UPDATE` und entfernt die Gruppenmitgliedschaft. Die Änderung wartet nachweislich an `permissions_revision` (`pg_blocking_pids`), wird nach dem Commit verweigert, Titel und Historie bleiben unverändert. Der vorhandene Test belegt dasselbe für das Entfernen der Projektmitgliedschaft. |
| Gleichzeitige Deaktivierung der Zuständigkeit | `reviewAssignmentWaitsForConcurrentDeactivationOfTheAssignee` (bestanden): Das Konto der neuen Zuständigkeit ist gesperrt und wird deaktiviert; die Zuweisung wartet an `identity_account` und antwortet danach 400 `assignee.not_member`, keine Zuständigkeit gespeichert. Die echte Deaktivierung nimmt vorher die Revisionszeile exklusiv und wartet daher schon dort auf die laufende Ticketänderung; eine Verklemmung entsteht in dieser Reihenfolge nicht. |
| Versionskonflikte | `reviewTwoChangesWithTheSameVersionYieldOneConflict` (bestanden): fünf Runden mit je zwei gleichzeitigen Änderungen zweier Personen mit demselben ETag ergeben jeweils genau 200 und 412, am Ende Version 6 und genau fünf Historienzeilen. Kein stiller Datenverlust. |
| Historie | Je geändertem Feld eine Zeile mit Version, Zeitpunkt, Konto, altem und neuem Wert, Anlage als Version 1, in derselben Transaktion wie die Änderung. Keine Änderung ohne neuen Wert erzeugt eine Version. Die Historie zeigt alte Werte allen, die das Feld heute lesen dürfen; das entspricht dem Vertrag. |
| Sperrreihenfolge | Rechte (geteilt), Identität, Projekt (geteilt), Ticket oder Zähler (exklusiv), Konto der Zuständigkeit (geteilt). Deaktivierung und Passwortreset nehmen die Revisionszeile vor jeder Kontosperre; eine Verklemmung mit Ticketänderungen wurde im Code nicht gefunden. Sperrzeitüberschreitung, Verklemmung und Serialisierungsfehler werden zu 503 `ticket.busy`; der vorhandene Test belegt die Sperrzeitüberschreitung. |
| Migration und Vertrag | V0012 legt drei Tabellen im Ticketmodul an, ohne Fremdschlüssel in andere Module; Längenprüfungen passen zum Dienst. Abweichung vom Vertrag nur in F3. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Backend `clean verify` auf unverändertem main `9a6a200` | Selbst ausgeführt: **165 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen**, Build erfolgreich. Temurin 25.0.1+8, Maven Wrapper 3.9.16, lokale PostgreSQL 16.15 mit synthetischen Daten über `-Dtaskmanager.test.*`. Kein Nachweis für die festgelegte Version 18.6. |
| Zehn eigene Proben auf main | Selbst ausgeführt: **10 Tests, 6 bestanden, 4 erwartete Fehlschläge** (F1 zweimal, F2, bekannter Befund F2 aus TASK-026), dazu F3 als eigener Lauf, keine Ausführungsfehler. Quellpatch: [review-evidence/task-027](review-evidence/task-027/README.md). |
| Dieselben Proben auf dem Kopf von #114 | Selbst ausgeführt (ohne F3): **9 Tests, 6 bestanden, 3 Fehlschläge** (F1 zweimal, F2); die Probe zum gesperrten fremden Projekt besteht dort. |
| CI des Ergebnisses | Gelesen, nicht selbst ausgeführt: [Backendlauf 39414584871](https://github.com/1uedev/task-manager/actions/runs/39414584871) auf `9a6a200` erfolgreich. |
| Wiederherstellung | Testdatei nach den Proben auf den Stand von main zurückgesetzt, `git apply --check --unidiff-zero` bestätigt den Nachweispatch; dieser PR ändert keinen Produkt- oder Testcode. |
| Repository | Selbst ausgeführt: Repository-Validator und Aufgabenübersicht erfolgreich, Python-Regressionstests grün, `git diff --cached --check` ohne Befund. |

Nicht geprüft: Lasttest, vollständige Penetrationsprüfung, Lifecycle Skript lokal, Frontend (TASK-027 ändert keins), Portal und externe Organisationen (noch nicht vorhanden).

## Übergabe

Dieser PR veröffentlicht nur den Bericht und die Prüfnachweise. TODO.md und DONE.md bleiben unverändert, es wird keine Aufgabe reserviert und keine Abnahme erklärt. F1 bis F3 können als Folgearbeit bewertet und von der umsetzenden Sitzung behoben werden; H1 braucht eine fachliche Entscheidung. Die fachliche Abnahme bleibt Holger vorbehalten.
