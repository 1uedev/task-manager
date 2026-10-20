# Unabhängige Prüfung TASK-028 / PR #119

Stand: 20. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261020-tags-review-01`, Branch `review/task-028-pr-119-claude`, eigener Arbeitsbereich. Diese Sitzung war an der Umsetzung von TASK-028 nicht beteiligt. Holger hat auf die Frage „Wer prüft den Tag Kern TASK-028 unabhängig?“ die Prüfung durch Claude gewählt; sie ersetzt die nach DEC-040 vorgesehene Prüfung durch eine getrennte KI, wie bei TASK-027 (PR #115).

**Ergebnis:** Projektgrenzen, Rechteentzug, Feldregel `tags`, Archivieren und die kombinierte Anlage mit Zuordnung halten in allen geprüften Fällen. Kein Projektkatalog und kein Tagname wurde Außenstehenden offengelegt, keine Zuordnung nach einem zuvor verbindlich gewordenen Entzug gespeichert. Es gibt **keinen Befund P1 oder P2** und **drei Befunde P3**: Namen mit anderen Unicode Leerzeichen gelten als verschieden (F1), `STRASSE` und `Straße` gelten als verschieden (F2), ein gesperrter fremder Tag antwortet nach 5 Sekunden mit 503 statt sofort mit 404 (F3). Dazu kommen **fünf Hinweise**, davon eine fachliche Frage an Holger (H1: Tagänderungen erscheinen nicht in der Tickethistorie).

## Grundlage und Abgrenzung

- Geprüft: [PR #119](https://github.com/1uedev/task-manager/pull/119), auf main als `6ba41750facf6e204b72911fc360edc57f9c3870`.
- Maßstab: AGENTS.md, CLAUDE.md, TODO.md (Umfang und Abnahmekriterien von TASK-028), DEC-040, DEC-065, DEC-067 mit Nachtrag 9, DEC-068, REQ-TAG-001, REQ-ACL-003, REQ-ACL-004, REQ-TKT-005, docs/TAGS_SEARCH.md (Entdeckungsgrenze, Sichtbarkeit, Testfälle T01, T02, T08, T14), backend/README.md Abschnitt „Tags (TASK-028)“, `contracts/tags.openapi.yaml`.
- Gelesen: `tags/internal/TagService.java`, `TagRepository.java`, `TagName.java`, `Tag.java`, `tags/web/TagController.java`, `tags/package-info.java`, V0013, `tickets/TicketAccess.java`, `JdbcTicketAccess.java`, `projects/ProjectAccess.java`, `JdbcProjectAccess.java`, `permissions/internal/Policy.java`, `JdbcPermissions.java` (Sperren, `inspect`, `require`), `AdministrationRight.java`, `TagsApiTest.java`, `TagsContractTest.java`.
- Prüfschwerpunkte laut Auftrag: Projektgrenzen, Rechteentzug, Feldregel `tags` gegenüber verborgenen Feldern, Konflikte und Nebenläufigkeit, Historie und Audit, Archivieren. Laut TODO.md zusätzlich Entdeckungsgrenzen und Modultransaktionen.
- Nicht als Befund gezählt: die Grenzen aus DEC-068 (8) und backend/README.md (keine Farben, kein Zusammenführen, keine Massenänderung, keine Suche und Filter, keine Oberfläche, keine Portaltags, keine Obergrenze je Objekt, Prüfung zum Sperrzeitpunkt statt beim Commit) sowie die Entscheidung H1 aus DEC-067 Nachtrag 9.

## Befunde

### F1 — P3: Andere Unicode Leerzeichen im Namen werden weder zusammengefasst noch abgelehnt

**Fundstelle:** `backend/src/main/java/taskmanager/tags/internal/TagName.java:21` (gleich in `:38` für die Suche). `replaceAll("\\s+", " ")` erfasst in Java ohne `UNICODE_CHARACTER_CLASS` nur ASCII Leerraum. Die Zeichenprüfung in `:25` bis `:28` lehnt Steuer und Formatzeichen ab, aber nicht die Kategorien Leerzeichen (Zs), Zeilentrenner (Zl) und Absatztrenner (Zp). NFKC wandelt nur einen Teil davon (etwa U+00A0, U+3000) in ein normales Leerzeichen.

**Nachweis:** `reviewInnerUnicodeSpacesMustCountAsOneSpace`: Nach „Nord Süd …“ (201) werden „Nord Süd …“ (OGHAM SPACE MARK) und „Nord Süd …“ (LINE SEPARATOR) im selben Katalog jeweils mit **201** angelegt, erwartet war 409 oder 400.

**Auswirkung:** Im Katalog stehen zwei oder drei Tags, die gleich oder fast gleich aussehen, aber verschiedene Kennungen haben. Das unterläuft die zugesagte Eindeutigkeit je Katalog (DEC-068 (4), TAGS_SEARCH.md T01) und erschwert später Filter und Zusammenführen. Ein Zeilentrenner im Namen kann außerdem Listen und Exporte zerreißen. Kein Rechteproblem.

**Vorschlag:** Mit `Pattern.UNICODE_CHARACTER_CLASS` (oder `\\p{Z}` zusammen mit `\\s`) zusammenfassen und Zl sowie Zp ablehnen; dieselbe Regel in `searchKey`. Regression mit U+1680, U+2028 und U+2029.

### F2 — P3: Groß und Kleinschreibung wird nur über `toLowerCase` verglichen, `STRASSE` und `Straße` sind zwei Namen

**Fundstelle:** `TagName.java:44` (`cleaned.toLowerCase(Locale.ROOT)`), genutzt vom eindeutigen Index `tag_name_per_catalog` in `V0013__tags_baseline.sql:20`.

**Nachweis:** `reviewUpperCaseSharpSMustBeTheSameName`: „Straße S36“ wird angelegt (201), danach „STRASSE S36“, die Großschreibung desselben Namens, ebenfalls mit **201** statt `409 tag.name_taken`.

**Auswirkung:** DEC-068 (4) und backend/README.md versprechen Eindeutigkeit „ohne Unterschied der Groß und Kleinschreibung“. Für deutschsprachige Kataloge ist ß der häufigste Fall, in dem das nicht gilt. Ähnliche Grenzfälle gibt es bei anderen Schriften. Kein Rechteproblem.

**Vorschlag:** Für den Vergleichsschlüssel eine vollständige Faltung verwenden, etwa `toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)` nach NFKC oder die Unicode Faltung von ICU, und bestehende Schlüssel in einer Migration neu berechnen (vorhandene Dubletten melden statt still zusammenlegen). Alternativ die Grenze in DEC-068 und README ausdrücklich nennen.

### F3 — P3: Fremde Tags werden erst gesperrt und dann ausgefiltert; ein gesperrter fremder Tag antwortet mit 503 statt 404

**Fundstellen:** `TagService.java:266` bis `:268` (Zuordnung per `tagId`: `tags.lock(..., false)` sperrt die Zeile geteilt, erst danach prüft `filter`, ob der Tag zum Unternehmenskatalog oder zum eigenen Projekt gehört) und `TagService.java:222` bis `:223` (Ändern über einen Katalog: `tags.lock(..., true)` sperrt exklusiv, erst danach der Vergleich mit dem Katalog). Die Abfrage in `TagRepository.java:53` filtert nur nach Unternehmen und Kennung.

**Nachweis:**
- `reviewLockedForeignTagMustAnswerLikeAnUnknownOne`: Mitglied von Projekt A ordnet einem Ticket in A die Kennung eines Tags aus dem Katalog des fremden Projekts B zu. Ohne Sperre sofort `404 tag.not_found`. Hält eine andere Transaktion den Tag `FOR UPDATE` (wie es eine laufende Umbenennung oder Archivierung in B tut), antwortet derselbe Aufruf nach **5048 ms mit 503 `tag.busy`**.
- `reviewChangeThroughAForeignCatalogMustNotWaitForTheTag`: Mitglied mit `tags.manage` für Projekt A ändert per `PATCH /projects/A/catalog/tags/{id}` einen Tag des Unternehmenskatalogs, den gerade eine Zuordnung geteilt hält. Antwort nach **5034 ms 503 `tag.busy`** statt sofort `404 tag.not_found`.

**Auswirkung:** (1) Die Antwort unterscheidet einen vorhandenen fremden Tag von einer unbekannten Kennung, entgegen TAGS_SEARCH.md T14 („unzulässige IDs ohne Existenzdetails abgewiesen“). Das ist dieselbe Klasse wie Befund F2 der Prüfung von TASK-026 (gesperrtes fremdes Projekt), der mit PR #114 behoben wurde. Die Ausnutzung ist schwach: Die Kennung ist eine zufällige UUID und der Zeitpunkt muss eine laufende Änderung treffen. (2) Im zweiten Weg setzt eine Person mit `tags.manage` nur für ihr Projekt eine exklusive Sperre auf einen Unternehmenstag oder auf den Tag eines fremden Projekts, bis ihre Anfrage endet. In dieser Zeit warten Zuordnungen dieses Tags in allen Projekten. Weder Werte noch Namen werden offengelegt.

**Vorschlag:** Den Katalog in die sperrende Abfrage aufnehmen, damit nur passende Zeilen gesperrt werden: für die Zuordnung `and (project_id is null or project_id = ?)` (beziehungsweise nur `project_id is null` für Projekte), für Änderungen `and project_id is not distinct from ?`. Regressionen nach dem Muster der beiden Proben.

## Hinweise

### H1 — fachliche Frage: Tagänderungen erscheinen nicht in der Tickethistorie

`reviewTagChangesAreNotInTheTicketHistory` (bestanden, dokumentiert das Verhalten): Nach dem Zuordnen eines Tags zeigt `GET …/tickets/{n}/history` nur die Anlage; die Zuordnung steht allein in `tag_audit_event`, für das es bis TASK-030 keine Leseschnittstelle gibt. REQ-TKT-005 verlangt, dass „fachlich relevante Änderungen“ mit Urheber und Zeitpunkt historisiert werden, und DEC-068 nennt `tags` ein Feld des Tickets. Ob Tagänderungen dazu gehören, ist eine fachliche Entscheidung. Vorschlag: Zuordnen und Entfernen in der Tickethistorie zeigen, sichtbar nur mit der Leseregel für `tags` (wie die übrigen Felder), als eigener kleiner Folgeauftrag. Alternativ genügt das Audit und wird mit TASK-030 lesbar; dann die Grenze in DEC-068 benennen. Gleiches gilt sinngemäß für Tags an Projekten.

### H2: `ticket.edit` ohne Leseaktion ordnet Projekttags per Kennung zu, nicht per Namen

`reviewEditWithoutReadAssignsProjectTagsByIdentifier` (bestanden): Ein Mitglied mit `ticket.edit` und den Feldregeln für `tags`, aber ohne `ticket.read`, sieht den Projektkatalog nicht (404) und darf per Namen nicht zuordnen (403), per bekannter Kennung aber schon (204; unbekannte Kennung 404). Das entspricht dem Wortlaut von DEC-068 (2), das nur die Abfrage über einen Namen an eine Leseaktion bindet. Es erlaubt aber, für eine bekannte Kennung zu erfahren, ob sie zum Projektkatalog gehört. Vorschlag: so lassen und in DEC-068 benennen, oder die Zuordnung eines Projekttags ebenfalls an eine Leseaktion des Katalogs binden.

### H3: Ein archivierter Tag hält seinen Namen besetzt

`reviewArchivedNameBlocksANewTag` (bestanden): Nach dem Archivieren von „Alt …“ antwortet eine Neuanlage mit `409 tag.name_taken`, eine Zuordnung per Namen mit `409 tag.archived`. Das passt zu „Archivieren statt Löschen“ und zu stabilen Kennungen, steht aber nicht ausdrücklich in README oder Vertrag. Vorschlag: einen Satz ergänzen, dass Wiederherstellen der Weg ist.

### H4: Audit der Anlage nennt das Projekt als Ziel vom Typ `tag`

`TagService.java:207` und `:294`, ebenso `:252` und `:255`: Bei Anlage, Umbenennung, Archivierung und Wiederherstellung steht `target_type = 'tag'`, `target_id` ist aber die Projektkennung des Katalogs (beim Unternehmenskatalog `null`). Die Probe zur Historie bestätigt das. Bei Zuordnungen bezeichnet dasselbe Spaltenpaar das Ticket oder Projekt. Vorschlag: `target_type` für Katalogereignisse `catalog` oder `project` nennen oder eine eigene Spalte für den Katalog vorsehen, bevor TASK-030 das Audit lesbar macht.

### H5: Katalogabfragen nutzen den eindeutigen Index nicht

`TagRepository.java:39`, `:59` und `:64` vergleichen mit `project_id is not distinct from ?`. PostgreSQL kann diesen Vergleich mit einem Parameter nicht als Indexbedingung nutzen; im generischen Plan wird `tag_catalog` nur über `company_id` und `normalized` gelesen und `project_id` gefiltert, `tag_name_per_catalog` (über `coalesce`) gar nicht. Bei den vorgeschlagenen 5.000 Katalogeinträgen ist das unkritisch. Für die Messziele von TASK-029 (Vorschläge p95 200 ms) wäre `coalesce(project_id, …) = coalesce(?, …)` oder zwei Abfragen mit `is null` und `= ?` die bessere Form. Die Teilwortsuche ohne Trigrammindex ist in README (6) schon benannt.

## Weitere Prüfergebnisse

| Bereich | Ergebnis und Nachweis |
|---|---|
| Projektgrenzen und Entdeckung | Projektkatalog nur für Mitglieder mit einer der fünf Leseaktionen, sonst 404 wie ein unbekanntes Projekt; Unternehmenskatalog zeigt nie Projekttags; `GET /tags/{id}` und `GET /projects/A/catalog/tags/{id}` lehnen Tags anderer Kataloge mit 404 ab; Projekte nehmen nur Unternehmenstags. Anlage und Abfrage per Namen im Projektkatalog setzen das Lesen des ganzen Katalogs voraus, wie TAGS_SEARCH.md es für die Dublettenprüfung verlangt. Gleiche Namen in verschiedenen Katalogen lösen keinen Konflikt aus. Vorhandene Tests und Code gelesen; einzige Lücke F3. |
| Rechteentzug | `reviewAssignmentWaitsForConcurrentRevocationAndIsRefused` (bestanden): Eine Transaktion hält `permissions_revision` exklusiv und entfernt die Gruppenmitgliedschaft; die Zuordnung wartet nachweislich dort und antwortet danach 404, keine Zuordnung gespeichert. `reviewAssignmentWaitsForConcurrentRemovalFromTheProject` (bestanden): Entfernen aus dem Projekt unter Sperre der Projektzeile; eine Zuordnung mit Anlage über den Namen wartet, antwortet 404 und hinterlässt keinen Tag. |
| Feldregel `tags` | Lesen braucht `ticket.read` und die Leseregel, Ändern `ticket.edit` und die Schreibregel; Schreiben setzt Lesen voraus (`Policy.decide`). `reviewDenyOnTheTagsFieldWins` (bestanden): Ein Verbot `DENY-READ:tags` aus einer zweiten Gruppe gewinnt für Lesen, Zuordnen und Entfernen (403). Eine Zuordnung ändert weder Version noch `updatedAt` des Tickets, verborgene Tagänderungen erzeugen also auch keine Metadatenspur im Sinne von DEC-067 Nachtrag 9. Die Felder der Tickets bleiben unberührt; das Tagmodul liest über `TicketAccess` nur die Kennung. |
| Atomarität der kombinierten Anlage | Ticket und Rechte werden vor der Anlage geprüft, die Anlage liegt in derselben Transaktion wie die Zuordnung. `reviewCombinedCreationIsAtomic` (bestanden) und die Mitgliedschaftsprobe: kein verwaister Tag bei fehlendem Recht, unbekanntem Ticket oder gleichzeitigem Entzug. |
| Konflikte und Nebenläufigkeit | Gleichzeitige Anlage desselben Namens über den eindeutigen Index und `on conflict do nothing` (vorhandener Test: genau ein 201). Gleichzeitige Umbenennung auf denselben Namen endet mit `409` über `DuplicateKeyException`. Eine wartende Abfrage per Namen sieht nach einer Umbenennung die neue Fassung (Neubewertung durch PostgreSQL) und legt dann einen neuen Tag an; eine noch nicht bestätigte Anlage oder Umbenennung auf denselben Namen lässt das Einfügen warten und den Tag danach finden. Tagversion als ETag mit 428 und 412. Zuordnen und Entfernen sind mengenbasiert und wiederholbar, kein stiller Listenverlust (T07). |
| Sperrreihenfolge | Rechte (geteilt, `lock_timeout` 5 s), Identität, Projektzeile (geteilt über den Projektadapter, Nichtmitglieder ohne Warten), Tagzeile. Die Unternehmenspfade sperren kein Projekt. Kein Weg sperrt erst einen Tag und dann ein Projekt oder Rechte; eine Verklemmung wurde im Code nicht gefunden. Zeitüberschreitung, Verklemmung und Serialisierungsfehler werden 503 `tag.busy` mit `Retry-After`. |
| Archivieren | Vorhandener Test: Archivieren wartet auf eine Zuordnung, die den Tag geteilt hält; eine wartende Zuordnung sieht danach den archivierten Tag (409). Archivierte Tags bleiben zugeordnet, lassen sich entfernen und wiederherstellen. Siehe H3. |
| Modultransaktionen | `TicketAccess.find` läuft mit `MANDATORY` in der Transaktion des Tagmoduls; `ProjectAccess.resource` sperrt in derselben Transaktion; keine fremden Tabellen gelesen, keine Fremdschlüssel in andere Module. Spring Modulith Prüfung im Build grün. |
| Eingaben | NUL und andere Steuerzeichen im Namen 400, unbekannte Felder 400 (auch bei der Anlage), falsche Parameterarten 400, NUL und 5000 Zeichen in `q` oder `after` 200 (`reviewBadParametersAreRefusedCleanly`). Kein 500 gefunden. Siehe F1 und F2 für den Namensvergleich. |
| Vertrag | Routen und Antwortcodes stimmen mit `tags.openapi.yaml` überein (`TagsContractTest` und Stichproben). |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Backend `clean verify` auf unverändertem main `6ba4175` | Selbst ausgeführt: **179 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen**. Temurin 25.0.1+8, Maven 3.9.11 (der Wrapper konnte seine Verteilung in dieser Umgebung nicht laden), lokale PostgreSQL 16 mit synthetischen Daten über `-Dtaskmanager.test.*`. Kein Nachweis für die festgelegte Version 18.6. |
| Zwölf eigene Proben auf main | Selbst ausgeführt: **12 Tests, 8 bestanden, 4 erwartete Fehlschläge** (F1, F2, zweimal F3). Quelle und Ergebnisse: [review-evidence/task-028](review-evidence/task-028/README.md). |
| Abfrageplan | Selbst ausgeführt auf PostgreSQL 16 (H5). |
| CI des Ergebnisses | Gelesen, nicht selbst ausgeführt: backend, frontend, qualification und repository-checks von PR #119 erfolgreich ([Backendlauf 39443250337](https://github.com/1uedev/task-manager/actions/runs/39443250337)). |
| Wiederherstellung | Die Probendatei wurde nach dem Lauf aus `backend/src/test` entfernt; dieser PR ändert keinen Produkt oder Testcode. |
| Repository | Selbst ausgeführt: Repository Validator und Aufgabenübersicht erfolgreich, Python Regressionstests grün, `git diff --cached --check` ohne Befund. |

Nicht geprüft: Lasttest und Messziele, vollständige Penetrationsprüfung, Lifecycle Skript lokal, Frontend (TASK-028 ändert nur einen generierten Typ), Portalkonten und mehrere Unternehmen (noch nicht vorhanden; die Projektpfade rufen dafür nicht ausdrücklich `internal` auf und `projects.key` filtert nicht nach Unternehmen, was mit einer Firma je Installation folgenlos ist).

## Übergabe

Dieser PR veröffentlicht nur den Bericht und die Prüfnachweise. TODO.md und DONE.md bleiben unverändert, es wird keine Aufgabe reserviert und keine Abnahme erklärt. F1 bis F3 sowie H2 bis H5 können von der umsetzenden Sitzung als Folgearbeit behoben oder dokumentiert werden; H1 braucht eine fachliche Entscheidung. Die fachliche Abnahme bleibt Holger vorbehalten.
