# Nachprüfung von TASK-100 / PR #293

**Empfehlung: abnehmen.** F1 bis F4 aus docs/REVIEW_TASK-098_PR-291.md sind wirksam behoben. Es gibt keinen neuen Befund, der die Abnahme verhindert. Drei Hinweise (N1 bis N3) betreffen Anzeige und Testabdeckung; N1 lohnt eine kleine Folgeaufgabe. Die fachliche Abnahme bleibt Holger vorbehalten.

* **Sitzung:** claude-20261111-requirements-review-01 (unabhängige Claude Prüfung statt Codex, auf Holgers Wunsch vom 11. November 2026).
* **Gegenstand:** [PR #293](https://github.com/1uedev/task-manager/pull/293), gemergt auf `main` als `cb1d4634cc63aded3bc90824827bd37f4737869b`, verglichen mit dem Elterncommit `8bb860e`.
* **Grundlage:** AGENTS.md, TASK-100 in TODO.md, DEC-130 in DECISIONS.md, docs/REVIEW_TASK-098_PR-291.md, Abschnitt „Nachbesserung (TASK-100, DEC-130)“ in backend/README.md, Prüfauftrag a13/codex-nachpruefung-task-100.md.
* **Änderungen dieser Prüfung:** nur dieser Bericht. Eine eigene Browserprobe lag während der Prüfung als Datei im Testordner und wurde danach gelöscht; kein Produktcode, Testcode, Vertrag oder Aufgabenstatus wurde geändert.

## Befunde

Keine.

## Bewertung von F1 bis F4

### F1: Definitionen eigener Felder in der Projektansicht

**Behoben.** `ProjectService.view` (ProjectService.java:469 bis 471) und `ProjectService.configuration` (ProjectService.java:331 bis 335) geben die Konfiguration ohne `requirementFields` aus, wenn `manageable` falsch ist (`ProjectConfiguration.withoutRequirementFields`, ProjectConfiguration.java:274). `manageable` ist dieselbe Prüfung wie für den Reiter Einstellungen: `projects.manage` im Unternehmen oder `project.manage` im Projekt; in der Projektliste wird sie je Projekt bestimmt (ProjectService.java:94).

Weitere Antwortwege habe ich durchgesehen:

| Weg | Ergebnis |
|---|---|
| `GET /projects` (Liste) | geht über `view`, also gefiltert. |
| `GET /projects/{id}` und `GET /projects/{id}/configurations/{version}` | gefiltert, Regressionstest `theProjectViewNamesCustomFieldsOfRequirementsOnlyToItsAdministration` mit demselben eingeschränkten Mitglied wie in der Codex Probe (Anforderung, Detail, gespeicherte Version) sowie Unternehmens und Projektverwaltung. |
| `PUT /projects/{id}/configuration`, Entwurf lesen, speichern, veröffentlichen, `usage` | verlangen `access(actor, id, true)`, also Verwaltung; die volle Konfiguration ist dort richtig. |
| Portal `GET /portal/projects` | gibt nur Tickettypen und Status aus (PortalProjectController.java:33). |
| Ticket, Suche, SLA, Rechteverwaltung | geben keine Definitionen der Anforderungsfelder aus; `PermissionsController.customFieldIds` nutzt sie nur zur Prüfung von Feldregeln. |

Der Testersatz (`projectView` in backend.mjs) filtert gleich. Die Oberfläche liest `requirementFields` nur im Konfigurationseditor, der nur bei `manageable` erscheint; die Anforderungsseite nutzt die gefilterten `customFields` der Anforderungen.

### F2: Personenauswahl für eigene Personenfelder

**Behoben.** `RequirementService.owners` öffnet die Liste bei `requirement.edit` und der Schreibregel für `owner` oder für mindestens ein aktives eigenes Feld vom Typ `user` (`choosesPeople`, RequirementService.java:329 bis 332). Das Recht auf `owner` wird dadurch nicht erweitert: Die Schreibprüfung von PATCH bleibt feldweise, der Backendtest `aWritablePersonFieldOpensTheChoiceOfPersonsButNotTheOwner` bestätigt 200 für das Personenfeld und 403 für `ownerId`. Ein nur deaktiviertes Personenfeld öffnet die Liste nicht (403), passend dazu, dass es keinen neuen Wert annimmt. Oberfläche (RequirementsPage.tsx:409) und Testersatz (backend.mjs:2243) folgen derselben Regel. Der Ladefehler erscheint mit Text in Deutsch und Englisch und „Erneut versuchen“; der Browsertest prüft Fehler, Wiederholung, Auswahl, Speichern und das unveränderte `owner`. Der Vertrag requirements.openapi.yaml 0.3.1 beschreibt die erweiterte Regel und das 403.

Zum deaktivierten Personenfeld siehe N1.

### F3: Speichergrenze der eigenen Werte

**Behoben.** V0061 setzt die Grenze der Spalte `requirement_revision.custom` auf 4 MiB (4.194.304 Bytes). Nachgerechnet für den schlimmsten Fall: höchstens 30 Felder (`requirementFields.too_many`, deaktivierte Felder zählen mit, weil sie nicht entfernt werden können), je höchstens 20.000 Codepunkte. Die Werte werden mit Jackson als JSON Objekt von Texten gespeichert (RequirementRepository.java:120 bis 124). Die längste gespeicherte Form eines Codepunkts sind die 6 Bytes eines als `\uXXXX` geschriebenen Steuerzeichens; erlaubt ist jedes Steuerzeichen außer NUL. Zeichen außerhalb der Basisebene brauchen 4 Bytes, Anführungszeichen, Backslash und Schrägstrich 2 Bytes. Damit höchstens 30 × 20.000 × 6 = 3.600.000 Bytes plus rund 1.500 Bytes für Schlüssel und Satzzeichen, also unter 4 MiB. Der Unittest `RequirementCustomSizeTest` prüft genau diesen Fall mit 40 Zeichen langen Schlüsseln.

Die Prüfung `customFits` läuft vor dem Schreiben über die zusammengeführten Werte (alter Bestand und Änderungen) mit derselben Serialisierung, die das Repository schreibt (RequirementService.java:715), und antwortet mit 400 `customFields.too_large`; die Oberfläche übersetzt den Code in Deutsch und Englisch. Die Codex Probe ist im Backendtest `theLargestPermittedValuesFitAndASmallChangeOnTopWorks` nachgebildet und übertroffen: 30 Langtexte mit je 20.000 Zeichen, abwechselnd Eurozeichen (3 Bytes) und U+0001 (6 Bytes), einzeln gespeichert, danach ein kleines PATCH auf den großen Bestand; die Spalte liegt dabei deutlich über der alten Grenze von 1 MiB. Eine Größengrenze für den Anfragekörper gibt es nicht, auch ein PATCH aller 30 Werte zugleich wird angenommen.

Die Migrationslisten sind vollständig nachgeführt: lifecycle-check.sh (Liste bis 0061, „Successfully applied 58 migrations“), DatabaseFoundationTest (bis „0061“) und ApplicationLifecycleTest (58). Der Backendlauf meldet „Successfully validated 58 migrations“.

### F4: Testersatz wie der Server

**Behoben für die geprüften Fälle.** `requirementCanonical` und `requirementChanges` (backend.mjs ab 2015) übernehmen Kalenderprüfung, Ganzzahlgrenze 2^53 − 1, das Entfernen von Leerzeichen wie Java `strip`, Codepunktzählung, NUL, Schlüsselmuster, höchstens 30 Einträge, `null` als ungültige Feldkarte, UUID in Kleinbuchstaben und aktive interne Mitglieder. Die gespeicherten Werte sind kanonisch. Alle vier Abweichungen aus dem Codex Bericht (`customFields: null`, `2026-02-30`, `9007199254740992`, `"  hello  "`) stehen als Fälle in contracts/cases/requirement-custom-values.json und laufen auf beiden Seiten. RequirementApiTest (`theSharedCaseMatrixOfCustomValuesHolds`) und requirements.test.mjs lesen dieselbe Datei, durchlaufen alle 34 Fälle und vergleichen Status, Verletzungscode und gelesene Werte. Beide Tests sind grün.

Zur Reichweite der Matrix siehe N2.

## Hinweise

### N1: Ein gespeicherter Wert eines Personenfelds erscheint im Editor als „Niemand“, wenn die Person nicht in der Auswahl steht

**Datei/Zeile:** frontend/src/pages/CustomFieldInput.tsx:41 bis 43; frontend/src/pages/RequirementsPage.tsx:409 und :583.

**Nachweis:** Browserprobe gegen den Testersatz. (a) Deaktiviertes Personenfeld mit gespeichertem Wert „Ben Leser“, Anna darf nur dieses Feld und den Titel schreiben: Die Liste wird richtig nicht geladen, die Auswahl zeigt nur „Niemand“ und steht auf „Niemand“. (b) Aktives Personenfeld, Ben ist inzwischen kein Mitglied mehr: Die Auswahl zeigt „Niemand, Anna Admin, Nora Neu“ und steht auf „Niemand“. Für `owner` behebt RequirementsPage.tsx:545 denselben Fall bereits, indem die gespeicherte Person zusätzlich angeboten wird (`ownerChoices`); die Personenfelder bekommen nur `ownerOptions`.

**Auswirkung:** Kein Datenverlust, denn unveränderte Felder werden nicht gesendet und der Server behält den Wert. Die Anzeige im Editor ist aber falsch, und wer das Feld bewusst leeren will, kann „Niemand“ nicht wählen, weil es schon gewählt scheint. Teilweise schon in TASK-098 angelegt; durch F2 jetzt im Fall (a) sichtbar, weil die Liste dort zu Recht nicht mehr geladen wird.

**Vorschlag:** In `CustomFieldInput` für `user` die gespeicherte Person wie bei der Einfachauswahl mit deaktivierter Option zusätzlich anbieten, gegebenenfalls als „(nicht mehr wählbar)“ gekennzeichnet. Gilt ebenso für Tickets, die dieselbe Komponente nutzen. Als kleine Folgeaufgabe, kein Abnahmehindernis für TASK-100.

### N2: Die Fallmatrix deckt nur neue Werte an einer frischen Anforderung ab

**Datei/Zeile:** contracts/cases/requirement-custom-values.json; frontend/tests/browser/backend.mjs:2024.

Jeder Fall startet mit einer leeren Anforderung. Nicht in der Matrix sind daher: der neue Wert eines deaktivierten Felds (`.inactive`), der beibehaltene Wert eines inzwischen deaktivierten Felds oder einer deaktivierten Option, ein kanonisch unveränderter Wert (keine Änderung, keine neue Revision), ein deaktiviertes Konto oder Portalkonto als Person sowie `customFields.too_large`. Die Logik dafür ist auf beiden Seiten vorhanden und teils durch ältere Tests abgedeckt; der Codex Bericht nannte diese Fälle aber ausdrücklich. Kleine verbliebene Abweichungen, die über die Oberfläche nicht erreichbar sind: `1.0` oder `1e3` für eine Ganzzahl nimmt der Testersatz an (JavaScript unterscheidet sie nach `JSON.parse` nicht von `1`), der Server lehnt sie ab; verkürzte UUID Schreibweisen wie `1-1-1-1-1` beantwortet der Server mit `not_member`, der Testersatz mit `invalid`; vorzeichenbehaftete Jahre wie `+10000-01-01` nimmt nur der Server an; `customFields.too_large` prüft der Testersatz nicht (mit gültigen Konfigurationen nicht erreichbar).

**Vorschlag:** Die Matrix bei Gelegenheit um einen Vorzustand je Fall erweitern (gespeicherter Wert, Feld oder Option danach deaktiviert) und um ein deaktiviertes Konto. Kein Abnahmehindernis.

### N3: 400 `customFields.too_large` ist nur als Unittest belegt

Mit gültigen Konfigurationen ist die Grenze nach der Rechnung oben nicht erreichbar; die Prüfung ist eine Schutzschicht vor dem Check der Datenbank. Ein HTTP Test für den Code fehlt deshalb zwangsläufig, `RequirementCustomSizeTest.aLargerStoredFormIsRecognized` prüft nur `customFits`. Ein Test, der die Antwort 400 über eine verkleinerte Grenze oder direkt am Service prüft, wäre möglich, ist aber keine Voraussetzung.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | OK |
| `python3 scripts/task_board.py --check` | OK, Übersicht aktuell |
| `python3 -m unittest discover -s tests` | 42 Tests, OK |
| Backend `./mvnw verify` (`-Djava.version=21`, JDK 21, lokale PostgreSQL 16.15 über `taskmanager.test.*`) | BUILD SUCCESS, 589 Tests, 0 Fehler, 0 übersprungen; darunter RequirementApiTest (21) mit den vier neuen Tests, RequirementCustomSizeTest (2), DatabaseFoundationTest und ApplicationLifecycleTest |
| Frontend `npm ci` | OK |
| Frontend `npm run verify` | OK: Generierung, Typprüfung, 100 Unittests, Build (nur bekannte Hinweise zu Moduldirektiven und Bundlegröße) |
| Frontend `npm run test:browser` (`TASKMANAGER_BROWSER=/opt/pw-browsers/chromium`) | 261 Tests in 39 Suiten, alle bestanden |
| Eigene Browserprobe zu N1 | zwei Fälle wie oben beschrieben; Probedatei danach gelöscht |
| `backend/scripts/lifecycle-check.sh`, gepinnter PostgreSQL 18.6 Container, Vorführimage | nicht ausgeführt: keine erreichbare Docker Engine. Liste und Zähler im Skript wurden im Quelltext geprüft. Der PostgreSQL 16 Lauf ist kein Nachweis für die gepinnte Version. |
| `git diff --check` | OK |

## Ergebnis

F1 bis F4 sind wirksam und mit Tests auf Server und Testersatz behoben. Neue Fehler habe ich nicht gefunden. **Empfehlung: abnehmen**, N1 als kleine Folgeaufgabe vormerken, N2 und N3 bei Gelegenheit.
