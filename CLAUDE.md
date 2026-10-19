@AGENTS.md

# Hinweise für Claude Code

Die gemeinsamen Regeln und Einstiegsdateien stehen in AGENTS.md. Wenn der verwendete Client den Import nicht automatisch auflöst, AGENTS.md ausdrücklich lesen. Diese Datei enthält keine zweite Fassung der Anforderungen oder Aufgaben.

Der schnelle Einstieg ist die erzeugte Aufgabenübersicht oben in TODO.md auf aktuellem main. Nur freigegebene OFFEN-Aufgaben ohne Besitzer kommen für eine Reservierung infrage. Nach Änderung eines Aufgabeneintrags die Übersicht mit `python3 scripts/task_board.py --write` nachführen; das Reservierungsverfahren steht in AGENTS.md.

Aktueller Umfang ist Planung und Repository-Infrastruktur; Produktimplementierung nur für die Backendgrundlage aus TASK-022 (DEC-055), die React-Grundfläche aus TASK-023 (DEC-056) und das Berechtigungsmodul aus TASK-025 (DEC-057, Claim-PR #69) sowie das Identitätsmodul aus TASK-024 ohne Anmeldeseite (DEC-058, Claim-PR #73, claude-20261018-identity-01) und die moderne Oberfläche mit Admin-Akzentfarbe, Logo und Drag and Drop aus TASK-036 (DEC-060, Claim-PR #84, claude-20261019-design-01) sowie den Adapter zwischen Identität und Berechtigungen aus TASK-039 (DEC-063, Claim-PR #94, claude-20261019-integration-01). Vor Arbeitsbeginn TODO.md auf Zuständigkeiten prüfen. Bei gleichzeitiger Arbeit mit Codex das dort und in AGENTS.md beschriebene Koordinationsverfahren beachten.
