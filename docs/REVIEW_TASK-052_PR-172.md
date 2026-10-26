# Nachprüfung TASK-052 / PR #172

Stand: 26. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261026-review-052-01`, Branch `claude/project-thread-6mx99o`, eigener Arbeitsbereich. Auftrag: Holgers Zustimmung vom 26. Oktober 2026 zur getrennten Nachprüfung von TASK-052 (DEC-040). Die Sitzung, die TASK-052 umgesetzt hat (`claude-20261026-configfix-01`), ist eine andere als diese.

Geprüft wurde die Behebung der Befunde F1 bis F5 aus dem [Prüfbericht zu TASK-044](REVIEW_TASK-044_PR-151.md) gegen den Umfang und die Abnahmekriterien von TASK-052 in TODO.md, DEC-081 und Holgers Entscheidungen zu F1 und F3. Ergebnis: **F1 bis F5 sind behoben. Zwei neue Befunde P3, kein P1 oder P2.** Alle vorhandenen Tests sind grün; zwölf zusätzliche Prüftests bestätigen die Behebung, einer belegt den neuen Befund N1.

## Grundlage und Abgrenzung

- Ergebnis: [PR #172](https://github.com/1uedev/task-manager/pull/172), auf main als `2616bb5a74f9dfaa944e49233d075f489a7d7ebf`. Prüfbasis ist genau dieser Stand.
- Maßstab: AGENTS.md, TODO.md (Eintrag TASK-052), DEC-081, der Bericht zu TASK-044, [PROJECT_CONFIGURATION.md](PROJECT_CONFIGURATION.md) und die Verträge `tickets` und `projects`.
- Holgers Entscheidungen: F1 lehnt einen Übergang, der ein für die anfragende Person nicht les- oder schreibbares Feld verlangt, immer mit 409 `ticket.required_field_inaccessible` ab. F3 behält beim Typwechsel den Status, wenn der neue Typ ihn kennt, sonst kommt das Ticket in den ersten Status des neuen Typs, mit Eintrag in der Historie und ohne frei gewählten Zielstatus.
- Die Hinweise ohne Befund aus dem Bericht zu TASK-044 und die offenen Entscheidungen aus DEC-073 sind nicht Teil von TASK-052 und werden hier nicht erneut gezählt.
- Diese Prüfung ändert keinen Produktcode und erklärt keine Abnahme.

## Befunde aus TASK-044

| Befund | Ergebnis | Nachweis |
|---|---|---|
| F1 · P2 verborgenes Pflichtfeld beim Übergang | **Behoben.** Vor jeder Wertprüfung verlangt der Server Lese- und Schreibregel für jedes aktive Pflichtfeld des Übergangs, sonst 409 ohne Feldnamen und ohne `violations`. Gilt für feste und eigene Felder und auch beim Wechsel von Typ und Status zusammen unter dem Ablauf des neuen Typs. Ein inaktives Pflichtfeld verlangt nichts und löst daher auch keinen Konflikt aus. | R2: leer und gefüllt je 409, gleicher Rumpf. N2: dasselbe beim gleichzeitigen Typwechsel. N3: `DENY-READ` auf `description`. N7: inaktives Feld, Wechsel 200. Regression `aStepThatRequiresAFieldTheCallerCannotSeeIsRefusedWithoutTellingWhetherItIsFilled`. |
| F2 · P3 Ganzzahl −2^63 | **Behoben.** Grenze ohne `Math.abs` auf beiden Seiten. | R1: ±2^53, −2^63 und 2^63−1 je 400, auch bei der Änderung. Grenzen in `ProjectConfigurationTest`. |
| F3 · P3 Typwechsel bei getrennten Abläufen | **Behoben wie entschieden.** Nur Typ: Status bleibt, wenn der neue Ablauf ihn kennt, sonst erster Status des neuen Typs. Das verlangt die Schreibregel für `status`. Typ und Status zusammen bleiben ein Übergang im neuen Ablauf, sodass kein Pflichtfeld umgangen wird. Historie und Suche folgen. | R3: 200 in `b1`, Historie mit `status` und `type`, Typ mit Status `b1` 409. N6: Suche nach `b1` findet das Ticket. Siehe aber **N1**. |
| F4 · P3 Zuordnung je Typ | **Im Server behoben.** `<typ>:<status>` mit Vorrang vor dem Status allein; der Typ ist der bisherige, auch wenn er selbst per `typeMapping` wegfällt. Statusschlüssel können keinen Doppelpunkt enthalten, die Schreibweise ist also eindeutig. | R4: `x` allein 409, `a:x` und `b:x` 200. N5: `c:x` für einen weggefallenen Typ 200. Zur Oberfläche siehe **N2**. |
| F5 · P3 unbekanntes Feld 403 | **Behoben.** 400 `customFields.unknown` bei Anlage und Änderung. Die Prüfung liegt hinter der Zugangs- und Bearbeitungsprüfung, verrät also Außenstehenden nichts; Mitglieder sehen die Feldschlüssel ohnehin in der Konfiguration. | R5. N4: ohne Zugang 404, nur Leserecht 403, für bekannte und unbekannte Schlüssel gleich. |

E6 aus dem Bericht zu TASK-044 bleibt grün (R6: 53 Tickets vorher gewechselt und verschoben, 7 danach abgewiesen, kein Ticket in einem unbekannten Status).

## Neue Befunde

### N1 · P3: Ein ausdrücklich mitgegebener bisheriger Status wird beim Typwechsel still ersetzt

**Fundstelle:** [TicketService.java:311–322](https://github.com/1uedev/task-manager/blob/2616bb5a74f9dfaa944e49233d075f489a7d7ebf/backend/src/main/java/taskmanager/tickets/internal/TicketService.java#L311).

`statusChanged` ist nur wahr, wenn der mitgegebene Status vom bisherigen abweicht. Schickt ein Client beim Typwechsel den bisherigen Status ausdrücklich mit, den der neue Ablauf nicht kennt, greift deshalb die Regel für „nur Typ“, und das Ticket landet im ersten Status des neuen Typs. Der Vertrag sagt für diesen Fall: „Wird der Status mitgegeben, muss er im Ablauf des neuen Typs vorkommen (`status.unavailable`)“.

**Nachweis (N1):** Ticket vom Typ `a` in `a1`, `PATCH {"type":"b","status":"a1"}` ergibt 200 mit Status `b1` statt 400 `status.unavailable`.

**Auswirkung:** Gering. Kein Pflichtfeld wird umgangen, die Historie zeigt den Wechsel, und die eigene Oberfläche schickt nur geänderte Felder. Ein API Client, der das ganze Ticket zurückschreibt, bekommt aber einen anderen Status als den, den er verlangt hat, ohne Hinweis.

**Vorschlag:** Den Fall „Status mitgegeben“ an `changes.containsKey("status")` festmachen und dann `status.unavailable` melden, oder den Vertrag so fassen, dass ein unveränderter Status wie ein fehlender behandelt wird. Beides ist klein; Regression mit N1.

### N2 · P3: Der Zuordnungsdialog bietet für einen weggefallenen Typ keine eigene Zeile

**Fundstelle:** [configuration.ts:113–122](https://github.com/1uedev/task-manager/blob/2616bb5a74f9dfaa944e49233d075f489a7d7ebf/frontend/src/app/configuration.ts#L113) (`mappingRows`).

`mappingRows` zählt nur Typen, die es in der neuen Version noch gibt. Verwenden ein bleibender Typ `a` und ein wegfallender Typ `c` (per `typeMapping` auf `b`) denselben wegfallenden Status `x`, entsteht eine einzige Zeile `x` mit den Zielen von `a`. Der Server prüft die Tickets von `c` aber gegen den Ablauf von `b`, und die Veröffentlichung scheitert mit 409 `status.mapping_invalid:x`. Die Zielauswahl berücksichtigt auch sonst nicht, auf welchen Typ ein wegfallender Typ abgebildet wird.

**Nachweis (N5):** `mappingRows(['x'], [{a,x},{c,x}], neu)` liefert nur `{"key":"x","targets":["a1"]}`. Über die API gelingt die Veröffentlichung mit `{"x":"a1","c:x":"b1"}`, mit dem Angebot des Dialogs allein nicht.

**Auswirkung:** Kein Datenverlust, die Veröffentlichung bleibt sicher abgewiesen. Der Fall verlangt das gleichzeitige Entfernen eines Typs und eines gemeinsam genutzten Status bei getrennten Abläufen und ist damit selten; die Verwaltung kann den Typ und den Status in zwei Schritten entfernen.

**Vorschlag:** Wegfallende Typen in `mappingRows` mitzählen und ihre Ziele aus dem Ablauf des gewählten `typeMapping` Ziels ableiten, oder die Grenze im Dialog nennen.

## Hinweise ohne Befund

- **Typwechsel ohne Schreibregel für `status`** antwortet weiter mit 400 `type.status_unavailable`. Das ist dokumentiert und gibt nichts preis, weil die Person den Status lesen kann oder ihn in der Antwort ohnehin nicht sieht. Die Ticketseite zeigt dafür nur die allgemeine Meldung zu ungültigen Eingaben.
- **Die Ticketseite kündigt den Statussprung nicht an.** Wer nur den Typ ändert, sieht den neuen Status erst nach dem Speichern. Das entspricht der Entscheidung zu F3; eine kurze Vorschau wäre eine spätere Verbesserung.
- **`status.mapping_invalid:<status>`** nennt bei der Zuordnung je Typ nicht, für welchen Typ das Ziel nicht passt. Mit der Nutzungsübersicht ist das ableitbar.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Backend `verify` auf unverändertem Produkt- und Testcode | Selbst ausgeführt: **308 Tests, 0 Fehler, 0 übersprungen**, mit `-Dtaskmanager.test.server-version=16.15`. Java 21 mit `-Djava.version=21`, eigene PostgreSQL 16.15 mit synthetischen Daten; kein Nachweis für Java 25 und PostgreSQL 18.6, maßgeblich bleibt CI. |
| Zwölf zusätzliche Prüftests | Selbst ausgeführt in einer temporären Testklasse: elf grün, N1 schlägt wie beschrieben fehl. Quelle und Ergebnisse unter [review-evidence/task-052](review-evidence/task-052/README.md); die Klasse ist danach aus dem Testbaum entfernt. |
| Frontend | Selbst ausgeführt: `npm ci --ignore-scripts`, `npm run verify` mit 76 Tests, Typprüfung und Build; `npm run test:browser` mit 102 bestandenen Tests, keine übersprungen. Node 22. Ein temporärer Test für `mappingRows` (N2) ist danach entfernt. |
| Durchsicht | Diff von PR #172 in `TicketService`, `ProjectService`, `ProjectConfiguration`, Verträgen, Dokumentation, `configuration.ts`, Konfigurationseditor, Ticketseite und Sprachdateien. |
| Repository | Validator, Aufgabenübersicht, Python Regressionstests und `git diff --check` für diesen PR. |

Nicht geprüft: Lebenszyklus mit Docker und der Workflow `Image`, Lasttests über R6 hinaus.

## Übergabe

Dieser PR veröffentlicht nur Bericht und Prüftests. TODO.md und DONE.md bleiben unverändert; TASK-044 und TASK-052 bleiben BEREIT ZUR PRÜFUNG. Aus technischer Sicht steht einer gemeinsamen fachlichen Abnahme beider Aufgaben nichts entgegen. N1 und N2 sind klein und können als eigene Folgeaufgabe behoben oder bewusst in Kauf genommen werden; das entscheidet Holger. Eine Korrektur braucht nach AGENTS.md eine eigene Reservierung.
