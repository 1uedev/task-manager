# Prüfbericht TASK-097 – PR #290

Sitzung: codex-20261110-requirements-links-01
Repository: 1uedev/task-manager
Stand: 10. November 2026
Status: Teilprüfung; keine vollständige technische Abnahme möglich.

## Geprüfte Grundlagen

Über den GitHub-Connector gelesen: AGENTS.md, TODO.md (TASK-097 mit Regeln 1–5 und Abnahmekriterien 1–4), DECISIONS.md (DEC-127, Auslegungen a–f) sowie backend/README.md (Abschnitt „Verknüpfungen von Anforderungen (TASK-097)“). Diese Quellen beschreiben Sollverhalten und Implementierungsanspruch; die Behauptungen des README gelten nicht als Nachweis der Implementierung.

## Befunde

### F1 – Widerspruch zwischen fachlicher Regel und Abnahmekriterium (Entscheidung erforderlich)

• Datei/Zeile: TODO.md, Abschnitt TASK-097, fachliche Regel 4 und Abnahmekriterium 3; DECISIONS.md, DEC-127(c). Exakte Zeilennummern wurden bei der Connector-Ausgabe nicht übermittelt.
• Sachverhalt: Regel 4 verlangt bei fehlendem wiki.read die sichtbare Ersatzanzeige „Seite nicht verfügbar“ ohne Titel. Abnahmekriterium 3 verlangt dagegen, dass eine Verknüpfung ohne Leserecht auf die Wikiseite nicht erscheint und nicht gezählt wird. DEC-127(c) priorisiert Regel 4.
• Auswirkung: Ohne ausdrückliche Bestätigung von DEC-127(c) ist die fachliche Abnahme unklar; außerdem kann bereits die Existenz einer Seite offengelegt werden.
• Vorschlag: Fachlich entscheiden und Abnahmekriterium 3 mit Regel 4 harmonisieren. Wenn die Ersatzanzeige gewünscht ist, präzisieren, dass keine Seitentitel und keine vertraulichen Metadaten offengelegt werden, und das Verhalten mit Negativtests absichern.

## Hinweise

### H1 – Revisions- und Konkurrenztests ausstehend

• Datei/Zeile: backend/README.md, Abschnitt TASK-097; konkrete Implementierungszeilen nicht geprüft.
• Auswirkung: Die dokumentierte Sperrfolge und die 412-Antwort beim Wechsel auf eine zwischenzeitlich überholte laufende Revision sind noch nicht durch Code- oder Laufzeittests verifiziert.
• Vorschlag: RequirementLinks und Integrationstests auf Lock-Reihenfolge, gleichzeitiges Speichern, Doppellink, Switch/Unlink und unveränderte Versionsnummern prüfen.

### H2 – Rechte- und Sichtbarkeitsprüfung ausstehend

• Datei/Zeile: backend/README.md, Abschnitt TASK-097; konkrete Implementierungszeilen nicht geprüft.
• Auswirkung: Keine belastbare Aussage zur Durchsetzung von requirement.edit, ticket.read, requirement.read, wiki.read und zur Filterung in beiden Historien.
• Vorschlag: Negative API-Tests mit gezielt entzogenen Rechten für Links, Zählungen, Ticket-Historie (TicketRequirementReaders) und Wiki-Seitentitel ergänzen bzw. ausführen.

### H3 – Migration, Verträge und Frontend nicht verifiziert

• Datei/Zeile: Laut Auftrag V0059, contracts/requirements.openapi.yaml, contracts/tickets.openapi.yaml, frontend/src/pages/RequirementLinksPanel.tsx, TicketRequirements.tsx, RequirementsPage.tsx, TicketPage.tsx, frontend/tests/browser/backend.mjs, frontend/tests/browser/requirementLinks.test.mjs; Implementierungszeilen nicht geprüft.
• Auswirkung: Weder Datenbankmigration und Lifecycle noch API-Vertragstreue, Fokusführung, Ansagen, verspätete Antworten oder englische Übersetzungen sind abgenommen.
• Vorschlag: Vollständigen Diff von PR #290 und die genannten Dateien mit Zeilennummern prüfen; Tests mit PostgreSQL 16 ausführen.

## Testprotokoll

|Kommando                                     |Ergebnis                                          |
|---------------------------------------------|--------------------------------------------------|
|`python3 scripts/validate_repository.py`     |Nicht ausgeführt: Repository lokal nicht verfügbar|
|`python3 scripts/task_board.py --check`      |Nicht ausgeführt: Repository lokal nicht verfügbar|
|`python3 -m unittest discover -s tests -v`   |Nicht ausgeführt: Repository lokal nicht verfügbar|
|`cd backend && ./mvnw verify` (PostgreSQL 16)|Nicht ausgeführt: Repository lokal nicht verfügbar|
|`cd frontend && npm ci`                      |Nicht ausgeführt: Repository lokal nicht verfügbar|
|`cd frontend && npm run verify`              |Nicht ausgeführt: Repository lokal nicht verfügbar|
|`cd frontend && npm run test:browser`        |Nicht ausgeführt: Repository lokal nicht verfügbar|

Zugriff: git clone --depth 1 https://github.com/1uedev/task-manager.git wurde versucht und scheiterte mit Could not resolve host: github.com. Einzelne Dokumente waren über den GitHub-Connector abrufbar. Eine vollständige lokale Prüfung konnte nicht erfolgen.

## Umfang und Änderungen

Keine Produktivdatei verändert. Dieser Bericht ist eine lokale Datei und wurde nicht in das Repository committed. Der Umfang des PR-Diffs wurde nicht geprüft; deshalb ist „nichts außerhalb TASK-097“ nicht bestätigt.

## Empfehlung

Nachbessern bzw. Abnahme zurückstellen. Zuerst die fachliche Diskrepanz in F1 entscheiden und dokumentieren; anschließend die vollständige Implementierungsprüfung und Testläufe nachholen. Diese Empfehlung ist keine Feststellung eines nachgewiesenen Implementierungsfehlers. Eine technische Abnahme wäre auf Grundlage der vorliegenden Evidenz nicht seriös.
