# Zentrale abgeschlossene Aufgaben

Stand 18. Oktober 2026

Diese Datei enthält dauerhaft nachvollziehbare Ergebnisse abgeschlossener und gemäß ihren Kriterien geprüfter Aufgaben. Der Status offener Arbeit steht in [TODO.md](TODO.md). Fachliche Abnahme ist von technischer Eigenprüfung zu unterscheiden.

## Bisheriger Stand

Bis zum 18. Oktober 2026 lagen keine nach dem hier festgelegten Verfahren abgeschlossenen Aufgaben vor; TASK-034 und TASK-035 sind die ersten Einträge unten. Die erste Dokumentfassung ist erstellt und unter TASK-001 zur Prüfung eingetragen. Die fachliche Abnahme durch Holger Gerlach steht aus. TASK-013 dokumentiert die veröffentlichte und technisch geprüfte GitHub-Arbeitsumgebung und bleibt ebenfalls zur Prüfung in TODO.md. Die Produktimplementierung hat nicht begonnen.

## Angaben für spätere Einträge

Jeder Eintrag enthält:

- ursprüngliche TASK-ID und Titel;
- betroffene REQ-IDs;
- ursprüngliche Abnahmekriterien und tatsächlich erreichtes Ergebnis;
- verantwortliche KI-Sitzung beziehungsweise Bearbeiter;
- Abschlussdatum und betroffene Dateien;
- tatsächlich ausgeführte Prüfungen mit Ergebnis und Grenzen;
- Reviewer und Art der Prüfung, getrennt von einer Eigenprüfung;
- erforderliche fachliche Abnahme mit Person, Datum und Bezug zur Bestätigung;
- Commit- oder PR-Verweis, sofern vorhanden; andernfalls ausdrücklich kein Git-Verweis;
- verbleibende Einschränkungen und gegebenenfalls verknüpfte Folgeaufgaben.

## Historie erhalten

Abgeschlossene Einträge bleiben bestehen. Spätere Fehler oder Erweiterungen erzeugen neue, verknüpfte Aufgaben in TODO.md. Sachliche Berichtigungen werden als datierter Nachtrag kenntlich gemacht. Eine erledigte Spezifikationsaufgabe wird nicht als erledigte Produktfunktion bezeichnet.

## TASK-034 Claude-Gegenprüfung von TASK-021 und TASK-033 durchführen

**Bezug:** REQ-ARCH-001, REQ-API-001, REQ-DATA-001, REQ-DEV-002, REQ-DEV-003, REQ-DEV-005. **Verantwortlich:** claude-20261018-review-01. **Abgeschlossen:** 2026-10-18.

**Abnahmekriterien:** Prüfbericht nennt geprüfte Commits, ausgeführte und nicht ausgeführte Prüfungen, Befunde mit Datei- und Zeilenbezug sowie verbleibende Grenzen. Bestätigte Entscheidungen, Vorschläge und geprüfte Ergebnisse bleiben getrennt. TASK-021 und TASK-033 behalten Status und Zuständigkeit; Übergaben werden nur als Verweis auf den Bericht ergänzt. Holger entscheidet über Annahme der Befunde.

**Ergebnis:** [Prüfbericht](docs/REVIEW_TASK-021_TASK-033.md) mit geprüften Commits, Prüfumgebungen und Befunden. TASK-033: keine blockierenden Befunde, T1 (niedrig) und T2 (Hinweis). TASK-021: keine blockierenden Befunde, Q1 (mittel), Q2 bis Q4 (niedrig), Q5 bis Q7 (Hinweise). Versionen, Prüfsummen und Testzahlen aus TASK-021 unabhängig bestätigt. TASK-021 und TASK-033 erhielten nur Verweise; Status und Zuständigkeit unverändert. Holger entschied, Q1 bis Q4 sofort zu beheben (TASK-035). T1, T2 und Q5 bis Q7 bleiben offen. Betroffene Dateien: docs/REVIEW_TASK-021_TASK-033.md, TODO.md.

**Prüfungen:** Repository-Validator, Übersichtsprüfung, 25 Regressionstests und git diff --check erfolgreich. Gegenprüfung lokal mit Java 21 statt 25 und Node 22 statt 24; PostgreSQL-Tests nur über das CI-Protokoll, da lokal kein Docker. Repository- und Qualifikationsläufe des Ergebnis-PR #60 erfolgreich.

**Review:** Der Bericht ist selbst die nach DEC-040 vorgesehene KI-Gegenprüfung von TASK-021 und TASK-033. Eine weitere unabhängige Prüfung des Berichts fand nicht statt.

**Abnahme:** Holger, 2026-10-18, im Projektgespräch erklärte Annahme von TASK-034; bestätigt durch Merge dieses Abschluss-PR.

**Referenz:** Reservierung #59 (c341fd4), Ergebnis #60 (39491aa).

## TASK-035 Befunde Q1 bis Q4 der Claude-Gegenprüfung im Qualifikationsversuch beheben

**Bezug:** REQ-ARCH-002, REQ-DATA-001, REQ-DEV-005. **Verantwortlich:** claude-20261018-qualfix-01. **Abgeschlossen:** 2026-10-18.

**Abnahmekriterien:** Der neue Negativtest schlägt nachweislich fehl, wenn die Transaktionspflicht entfernt wird, und besteht mit ihr. Die Modulith-Negativtests prüfen die erwartete Verstoßart. `mvnw.cmd` wird mit CRLF ausgecheckt und ist inhaltlich gleich mit dem Wrapper-Archiv 3.3.4. Keine Deprecation-Warnung mehr aus TransportFixtureTest. Der vollständige Qualifikationslauf mit PostgreSQL ist grün. Getrennte KI prüft die Änderung.

**Ergebnis:** Q1 bis Q4 behoben, beschrieben im Nachtrag aus TASK-035 in [TECHNICAL_BASELINE.md](docs/TECHNICAL_BASELINE.md). Neuer Test für die Transaktionspflicht, Verstoßarten in den Modulith-Negativtests, `*.cmd text eol=crlf` in `.gitattributes`, `asString()` statt `asText()`. Java jetzt 9 Tests. Keine Versions-, Vertrags- oder Produktänderung. Betroffene Dateien: .gitattributes, drei Testklassen unter qualification/backend/src/test, qualification/README.md, docs/TECHNICAL_BASELINE.md, docs/REVIEW_TASK-021_TASK-033.md, TODO.md.

**Prüfungen:** Gegenprobe ohne Transaktionspflicht: genau der neue Test scheitert ([Lauf 39116009163](https://github.com/1uedev/task-manager/actions/runs/39116009163)). Mit Transaktionspflicht 9/9 Java-Tests mit PostgreSQL 18.6, keine Deprecation-Meldung ([Lauf 39116142898](https://github.com/1uedev/task-manager/actions/runs/39116142898)). `mvnw.cmd` nach frischem Auschecken mit CRLF und bytegleich mit dem Wrapper-Archiv. Repository-Validator, Übersichtsprüfung, 25 Regressionstests und git diff --check erfolgreich.

**Review:** Die im Kriterium vorgesehene getrennte KI-Prüfung der Änderung fand nicht statt. Holger hat TASK-035 ohne diese Prüfung angenommen; die Nachweise oben sind Eigenprüfung der umsetzenden Sitzung.

**Abnahme:** Holger, 2026-10-18, im Projektgespräch erklärte Annahme von TASK-035; bestätigt durch Merge dieses Abschluss-PR.

**Referenz:** Reservierung #61 (05fc08a), Ergebnis #62 (03350ba).
