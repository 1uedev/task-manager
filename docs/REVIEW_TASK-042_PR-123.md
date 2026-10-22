# Unabhängige Prüfung TASK-042 / PR #123

Stand: 20. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261020-taghistory-review-01`, eigener Arbeitsbereich. Diese Sitzung war an der Umsetzung von TASK-042 und TASK-028 nicht beteiligt. Holger hat auf die Frage, wer TASK-042 unabhängig prüft, „Claude prüft“ gewählt; die Prüfung ersetzt die nach DEC-040 vorgesehene Prüfung durch eine getrennte KI, wie bei TASK-027 (PR #115) und TASK-028 (PR #120).

**Ergebnis:** Sichtbarkeit und Transaktion halten in allen geprüften Fällen. Tageinträge sieht nur, wer `ticket.read` und die Leseregel für `tags` hat; ein Verbot gewinnt, ein Entzug wirkt sofort, Projektgrenzen bleiben dicht, und ein gescheiterter Historieneintrag nimmt die Zuordnung mit zurück. Unter gleichzeitigen Feld und Tagänderungen trat keine Verklemmung auf, die Versionen der Historie laufen nie zurück. Es gibt **keinen Befund P1 oder P2** und **einen Befund P3**: Eine Zuordnung, die auf eine laufende Feldänderung wartet, trägt einen Zeitpunkt vor dieser Änderung, obwohl sie danach in der Historie steht (F1). Dazu kommen **drei Hinweise** ohne Handlungsdruck.

## Grundlage und Abgrenzung

- Geprüft: [PR #123](https://github.com/1uedev/task-manager/pull/123), auf main als `b85abdddce048fb7a76a44222e61a969cc6417e6`.
- Maßstab: AGENTS.md, CLAUDE.md, TODO.md (Umfang und Abnahmekriterien von TASK-042), DEC-040, DEC-067 mit Nachtrag 9 (H1: Version und „zuletzt geändert von“ bleiben sichtbar, der Inhalt verborgener Felder nicht), DEC-068 mit Nachtrag (10), REQ-TKT-005, REQ-TAG-001, REQ-ACL-003, backend/README.md (Tickets und Tags), `contracts/tickets.openapi.yaml`.
- Gelesen: `tickets/TicketAccess.java`, `tickets/internal/JdbcTicketAccess.java`, `TicketRepository.java` (`lockShared`, `change`, `changes`), `TicketService.java` (`history`, `update`, Lesepfad), `tags/internal/TagService.java` (`assignTicket`, `unassignTicket`, `resolve`, `changeableTicket`), `TagRepository.java`, `tags/web/TagController.java` (`retrying`), V0012 und V0013, der neue Test in `TagsApiTest.java`.
- Prüfschwerpunkte laut Auftrag: Sichtbarkeit (Feldregel `tags`, Projektgrenzen, Rechteentzug), Sperrreihenfolge und Transaktion, Zusammenspiel mit H1.
- Nicht als Befund gezählt: die Grenzen aus DEC-068 Nachtrag (10) und backend/README.md (keine Migration, Projekttags in keiner Historie, keine Oberfläche, keine Suche, Prüfung zum Sperrzeitpunkt) sowie die Entscheidung H1 aus DEC-067 Nachtrag 9.

## Befunde

### F1 (P3): Eine wartende Zuordnung trägt einen früheren Zeitpunkt als die Feldänderung, nach der sie steht

**Fundstelle:** `backend/src/main/java/taskmanager/tags/internal/TagService.java:189` und `:200` nehmen den Zeitpunkt, bevor `tickets.recordTagChange` in `JdbcTicketAccess.java:32` die Ticketzeile geteilt sperrt. Hält eine Feldänderung die Zeile gerade `FOR UPDATE`, wartet die Zuordnung dort bis zu 5 Sekunden (`lock_timeout`). Die Feldänderung nimmt ihren Zeitpunkt erst nach ihrer Sperre (`TicketService.java:240`). Die Historie ordnet nach `ticket_change.id`, also nach der Reihenfolge der Sperren.

**Nachweis:** `reviewAnAssignmentThatWaitsForAFieldChangeTakesTheNewVersionAndALaterTime`: Eine Transaktion hält die Ticketzeile, eine Zuordnung wartet nachweislich an `select version from ticket … for share`, danach wird die Feldänderung mit Version 2 bestätigt. Die Historie lautet richtig „1, 2 title, 2 +Tag“, der Tageintrag trägt aber **20:12:08.786** und die Feldänderung davor **20:12:09.296**. Erwartet war ein Zeitpunkt nicht vor dem vorangehenden Eintrag.

**Auswirkung:** Version und Reihenfolge stimmen, nur der Zeitpunkt läuft zurück, um höchstens die Wartezeit. Wer die Historie nach Zeit sortiert oder anzeigt, sieht die Zuordnung vor der Änderung, deren Version sie trägt. Dasselbe frühere Datum steht in `tag_ticket.assigned_at` und im Tagaudit. Kein Rechteproblem, keine Offenlegung.

**Vorschlag:** Die Ticketzeile sperren, bevor der Zeitpunkt genommen wird, etwa mit einer eigenen Portmethode, die die Zeile geteilt sperrt und die Version liefert, aufgerufen nach der Tagzeile und vor `tags.assignTicket` beziehungsweise `tags.unassignTicket`. Die Reihenfolge Rechte, Identität, Projekt, Tag, Ticket aus DEC-068 Nachtrag (10) bleibt dabei erhalten. Regression nach dem Muster der Probe.

## Hinweise

### H1: Die Historie nennt nur die Kennung des Tags

Ein Tageintrag trägt in `to` oder `from` die Kennung, nicht den Namen. Der Umfang in TODO.md verlangt „Zeitpunkt, Konto und Tag“; das ist mit der Kennung erfüllt, und wer die Einträge sehen darf, kann den Namen auch auflösen (`ticket.read` gehört zu den Leseaktionen des Projektkatalogs, den Unternehmenskatalog liest jede interne Anmeldung). Ein umbenannter Tag erscheint aber mit seinem heutigen Namen, ein archivierter nur mit `includeArchived`. Für die Oberfläche (TASK-030) sollte das bewusst entschieden werden; eine Momentaufnahme des Namens wäre eine kleine Ergänzung im Port.

### H2: Einträge sind nicht mehr eindeutig je Version

Bisher bildete jede Version genau einen Eintrag. Jetzt können mehrere Einträge dieselbe Version tragen, und eine Version kann für einen Leser nur aus Tageinträgen bestehen. Vertrag und README sagen das ausdrücklich, und die Probe `reviewSeveralTagChangesShareOneVersionAndArchivedTagsAreRecordedOnRemoval` bestätigt es (drei Zuordnungen und eine Entfernung, alle mit Version 1). Ein Client darf Einträge also nicht über die Version schlüsseln; die Kurzbeschreibung „Je Version …“ am Anfang der Vertragsbeschreibung könnte das klarer sagen.

### H3: Der Port schreibt ohne eigene Prüfung

`TicketAccess.recordTagChange` ist öffentlich, verlangt eine laufende Transaktion und prüft weder Unternehmen noch Projekt des Tickets noch die Tagkennung. Das ist für einen internen Port im modularen Monolithen vertretbar, weil das Tagmodul vorher Rechte, Ticket und Tag prüft. Ein späterer zweiter Aufrufer müsste dieselben Prüfungen selbst leisten; ein Satz im Javadoc würde das festhalten.

## Weitere Prüfergebnisse

| Bereich | Ergebnis und Nachweis |
|---|---|
| Feldregel `tags` | `history` fragt die Entscheidung für die fünf Felder und `tags` ab und filtert jeden Eintrag nach seinem Feld. Ohne Leseregel für `tags` fehlen die Einträge ganz (vorhandener Test). `reviewRevokedOrDeniedTagReadRuleHidesTheEntriesAtOnce` (bestanden): Ein Verbot `DENY-READ:tags` aus einer unternehmensweiten Gruppe gewinnt gegen die Erlaubnis im Projekt; nach dem Entfernen der Gruppe mit der Leseregel bleibt beim nächsten Abruf nur die Anlage stehen. |
| Projektgrenzen | `reviewProjectBoundariesAndMissingReadActionHideTheHistory` (bestanden): Ein Mitglied eines anderen Projekts mit allen Ticketrechten dort und ein Nichtmitglied mit unternehmensweiten Ticketrechten bekommen `404 project.not_found`. Wer nur `ticket.edit` mit den Feldregeln hat, ordnet zu, die Zuordnung steht in der Historie, die Historie selbst bleibt ihm aber verborgen (404), wie bei den übrigen Feldern. Tags fremder Projektkataloge können ohnehin nicht zugeordnet werden (TASK-028, F3 behoben), daher erscheinen ihre Kennungen in keiner fremden Historie. |
| Zusammenspiel mit H1 | `reviewTagChangesLeaveVersionAndLastChangedByOfTheTicketAsTheyAre` (bestanden): Nach einer Zuordnung durch eine andere Person bleiben `ETag`, `updatedBy` und `updatedAt` des Tickets unverändert; ein Leser ohne Leseregel für `tags` sieht weiterhin nur die Anlage. Eine Tagänderung hinterlässt also für ihn keine Metadatenspur, was strenger ist als H1 verlangt und dazu passt. Wer die Leseregel für `tags` hat, aber nicht für alle fünf Felder, sieht die Version einer verborgenen Feldänderung über den Tageintrag; das deckt H1 ausdrücklich. |
| Wiederholung und Entfernen | Wiederholtes Zuordnen und das Entfernen eines nicht zugeordneten Tags schreiben nichts (vorhandener Test und Probe mit zufälliger Kennung). Das Entfernen eines archivierten Tags wird eingetragen. Gleichzeitiges Zuordnen desselben Tags: das zweite Einfügen wartet an `on conflict do nothing` und schreibt danach nichts. |
| Transaktion | `recordTagChange` läuft mit `MANDATORY` in der Transaktion des Tagmoduls. `reviewAFailedHistoryWriteRollsTheAssignmentBack` (bestanden): Hält eine andere Transaktion die Ticketzeile länger als 5 Sekunden, antwortet die Zuordnung `503 tag.busy` mit `Retry-After: 1`; weder `tag_ticket` noch das Audit noch die Historie enthalten danach etwas. |
| Sperrreihenfolge | Zuordnen: Rechte, Identität, Projektzeile geteilt, Tagzeile geteilt, Einfügen in `tag_ticket` (keine Fremdschlüssel auf `ticket`, also keine Sperre dort), zuletzt Ticketzeile geteilt. Entfernen: wie oben ohne Tagzeile. Feldänderung: Rechte, Identität, Projektzeile geteilt, Ticketzeile exklusiv, Konto der Zuständigkeit. Kein Weg sperrt erst das Ticket und dann einen Tag oder eine Zuordnung, daher kein Zyklus. `reviewConcurrentFieldAndTagChangesNeitherDeadlockNorMixUpTheHistory` (bestanden): acht Threads mit je drei Zuordnungen und Entfernungen, acht Threads mit je drei Titeländerungen auf demselben Ticket; alle Antworten 200 oder 204, **0 Verklemmungen** laut `pg_stat_database`, genau 48 Tageinträge, Versionen in der Historie nie rückläufig, letzte Version gleich dem `ETag`. |
| Version der Einträge | Unter `READ COMMITTED` liest `select version … for share` nach dem Warten die neue Version (Probe zu F1: Eintrag mit Version 2). Eine Feldänderung kann sich nicht zwischen Sperre und Eintrag schieben, weil `FOR UPDATE` auf die geteilte Sperre wartet. |
| Lesepfad | Die Historie prüft wie die übrigen Lesepfade von TASK-027 mit `inspect` ohne Sperre; ein gleichzeitig verbindlich werdender Entzug kann eine einzelne laufende Antwort noch zulassen. Das ist unverändertes Verhalten aus TASK-027 und kein neuer Befund. |
| Vertrag | `contracts/tickets.openapi.yaml` nennt `tags` im Feldaufzählungstyp und beschreibt Kennung, Version und Sichtbarkeit; Routen und Codes unverändert. Das Frontend nutzt die Historie noch nicht. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Backend `clean verify` auf unverändertem main `b85abdd` | Selbst ausgeführt: **182 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen**. Temurin 25.0.1+8, Maven 3.9.11 (der Wrapper konnte seine Verteilung in dieser Umgebung nicht laden), lokale PostgreSQL 16 mit synthetischen Daten über `-Dtaskmanager.test.*`. Kein Nachweis für die festgelegte Version 18.6. |
| Sieben eigene Proben auf main | Selbst ausgeführt: **7 Tests, 6 bestanden, 1 erwarteter Fehlschlag** (F1). Quelle und Ergebnisse: [review-evidence/task-042](review-evidence/task-042/README.md). |
| CI des Ergebnisses | Gelesen, nicht selbst ausgeführt: backend, frontend, qualification und repository-checks von PR #123 erfolgreich ([Backendlauf](https://github.com/1uedev/task-manager/actions/runs/39473931209/job/118804875039)). |
| Wiederherstellung | Die Probendatei wurde nach dem Lauf aus `backend/src/test` entfernt; dieser PR ändert keinen Produkt oder Testcode. |
| Repository | Selbst ausgeführt: Repository Validator und Aufgabenübersicht erfolgreich, Python Regressionstests grün, `git diff --cached --check` ohne Befund. |

Nicht geprüft: Lasttest und Messziele, vollständige Penetrationsprüfung, Lifecycle Skript lokal, Frontend (TASK-042 ändert keinen Frontendcode), Portalkonten und mehrere Unternehmen (noch nicht vorhanden).

## Übergabe

Dieser PR veröffentlicht nur den Bericht und die Prüfnachweise. TODO.md und DONE.md bleiben unverändert, es wird keine Aufgabe reserviert und keine Abnahme erklärt. F1 und die Hinweise H1 bis H3 können auf Holgers Auftrag als kleine Folgearbeit behoben oder dokumentiert werden; keiner davon steht einer Abnahme von TASK-042 entgegen. Die fachliche Abnahme von TASK-028 und TASK-042 bleibt Holger vorbehalten.
