# Zentrale abgeschlossene Aufgaben

Stand 18. Oktober 2026

Diese Datei enthält dauerhaft nachvollziehbare Ergebnisse abgeschlossener und gemäß ihren Kriterien geprüfter Aufgaben. Der Status offener Arbeit steht in [TODO.md](TODO.md). Fachliche Abnahme ist von technischer Eigenprüfung zu unterscheiden.

## Bisheriger Stand

Bis zum 18. Oktober 2026 lagen keine nach dem hier festgelegten Verfahren abgeschlossenen Aufgaben vor; TASK-034 und TASK-035 sind die ersten Einträge unten. TASK-022 ist als erste Produktimplementierung (Backendgrundlage ohne Fachfunktion) abgeschlossen, TASK-023 als Frontend-Grundfläche ohne Fachfunktion. Die erste Dokumentfassung ist erstellt und unter TASK-001 zur Prüfung eingetragen. Die fachliche Abnahme durch Holger Gerlach steht aus. TASK-013 dokumentiert die veröffentlichte und technisch geprüfte GitHub-Arbeitsumgebung und bleibt ebenfalls zur Prüfung in TODO.md. Fachfunktionen sind noch nicht implementiert.

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

## TASK-022 Backend und PostgreSQL-Grundlage aufbauen

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-DATA-001, REQ-OPS-005, REQ-DEV-005. **Verantwortlich:** claude-20261018-backend-01. **Abgeschlossen:** 2026-10-18.

**Abnahmekriterien:** Sauberer Build, Start/Stopp, leere Datenbankmigration und Wiederanlauf reproduzierbar; fehlende notwendige Konfiguration scheitert nachvollziehbar. Echtes PostgreSQL in Integrationstests, keine Produktions-Schemaautomatik. Getrennte KI prüft insbesondere Migrations- und Modulgrenzen.

**Ergebnis:** [Backendgrundlage](backend/README.md) unter `backend/` mit den Versionen aus TASK-021 ohne Abweichung: Spring Boot 4.1.1, Spring Modulith 2.1.1, Flyway, PostgreSQL 18.6 mit festem Digest, Temurin 25.0.4.1+1, Maven-Wrapper 3.3.4. Sieben leere Modulgrenzen mit erlaubten Abhängigkeiten nach TECHNICAL_CONTRACTS.md. Pflichtkonfiguration `TASK_MANAGER_DB_URL`, `TASK_MANAGER_DB_USER`, `TASK_MANAGER_DB_PASSWORD`; fehlt eine Angabe, bricht der Start vor Webserver und Datenbankverbindung ab und nennt alle fehlenden Variablen. Alle Fehler als Problem Details mit stabilem `code`, serverseitiger `correlationId` und `instance` als `urn:uuid:`, ohne Anfragepfad, Query, `detail`, Stacktrace oder Ausnahmemeldung, auch außerhalb der Controller. Flyway als einziger Schemaweg (`ddl-auto: validate`, `clean` abgeschaltet), Migrationskonvention `V<NNNN>__<modul>_<beschreibung>.sql` mit leerer Startmigration. Nur `/actuator/health` mit Liveness/Readiness ohne Details. Workflow [backend.yml](.github/workflows/backend.yml). Auftrag als DEC-055 eingetragen. Keine Fachfunktion, keine Anmeldung, kein Frontend, kein Docker-Image. Betroffene Dateien: `backend/`, `.github/workflows/backend.yml`, DECISIONS.md, AGENTS.md, CLAUDE.md, README.md, docs/AI_WORKFLOW.md, TODO.md.

**Prüfungen:** [CI-Lauf 39121322406](https://github.com/1uedev/task-manager/actions/runs/39121322406) auf `62a9d4c`: Temurin 25.0.4.1, 14/14 Tests ohne übersprungene Tests gegen PostgreSQL 18.6; danach gepacktes JAR gegen leere Datenbank: Abweisung ohne Einstellungen ohne Datenbankänderung, erster Start mit genau einer Migration, Stopp per SIGTERM mit geordnetem Herunterfahren (Exitcode 143), Neustart mit „No migration necessary“ und unveränderter Historie, ohne verwaiste Prozesse. Repository-, Qualifikations- und Backendläufe auf dem letzten Stand `8635a54` erfolgreich. Gegenproben: eingefügter Zugriff `identity` → `tickets` von der Modulprüfung abgewiesen; alte Fehlerbehandlung lässt vier der fünf Problemtests scheitern; nicht erreichbare Datenbank bricht das Lebenszyklusskript nach 8 s mit Phasenangabe ab. Repository-Validator, Übersichtsprüfung, 25 Regressionstests und `git diff --check` erfolgreich. Lokal nur OpenJDK 21 und PostgreSQL 16 ohne Docker (Entwicklerhilfe); ein hängender Stopp wurde nicht simuliert.

**Review:** Getrennte KI-Gegenprüfung nach DEC-040 durch die Codex-Sitzung codex-20261018-backend-review-01 auf `bc48d12` ([Prüfbericht in PR #66](https://github.com/1uedev/task-manager/blob/d5e8f3dec4c334d38a379791324904069eb8ad88/docs/REVIEW_TASK-022_PR-65.md)): keine Befunde an Startmigration und Moduldeklarationen, R1 (mittel, `instance` spiegelte den Anfragepfad) und R2 (niedrig, Lebenszyklusskript ohne Zeitlimits und Bereinigung). Beide in PR #65 behoben. Die Korrekturen wurden nicht erneut durch Codex geprüft; ihre Nachweise oben sind Eigenprüfung der umsetzenden Sitzung.

**Abnahme:** Holger, 2026-10-18, im Projektgespräch erklärte Annahme von TASK-022 („task22 passt“) nach Merge von PR #65; bestätigt durch Merge dieses Abschluss-PR.

**Offen und Folgeaufgaben:** Q5 (Problem-Schema im Produktvertrag für Standard- und Erweiterungsfelder öffnen) und Q6 (wer flusht) aus der Gegenprüfung von TASK-021 bleiben für TASK-023 beziehungsweise die ersten Fachmodule offen. Fachcodes, `type`-URIs, Absicherung der Endpunkte (TASK-024), Zusammensetzung von Tickets, Tags und Suche (TASK-027 bis TASK-029) sowie Image und Betrieb (TASK-031) sind nicht Teil dieses Ergebnisses. TASK-023 bis TASK-031 bleiben blockiert.

**Referenz:** Reservierung #64 (26ac52d), Ergebnis #65 (4be10f7).

## TASK-023 React-Grundfläche mit Sprachen und API-Anbindung aufbauen

**Bezug:** REQ-ARCH-001, REQ-API-001, REQ-I18N-001, REQ-I18N-002, REQ-BRAND-001, REQ-UX-001. **Verantwortlich:** claude-20261018-frontend-01. **Abgeschlossen:** 2026-10-18.

**Abnahmekriterien:** Reproduzierbarer Build mit strenger Typprüfung, Sprachumschaltung und Tastaturbedienung geprüft; keine hart verdrahteten Fachtexte oder Sitzungstokens im Lokalspeicher. Testantworten ausdrücklich synthetisch, keine behauptete Backendfunktion. Getrennte KI prüft Client-/Fehlervertrag und UI-Zustände.

**Ergebnis:** Die [Frontend-Grundfläche](frontend/README.md) unter `frontend/` verwendet die Versionen aus TASK-021 ohne Abweichung: Node 24.22.0, npm 11.19.1, React 19.3.1, TypeScript 5.9.3, Vite 8.3.4, openapi-typescript 7.13.0 und openapi-fetch 0.17.0; hinzu kommen nur die Prüfwerkzeuge `playwright-core` 1.64.1 und `@types/node`. Laufzeitabhängigkeiten sind React, React DOM und openapi-fetch. Streng gesetzte Compileroptionen (`strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `erasableSyntaxOnly`). Oberflächenrahmen mit Sprunglink, Landmarken, Navigation und Seitenwahl über den URL-Anker. Sprachumschaltung Deutsch und Englisch mit Katalogen, Rückfallregel (gewählte Sprache, Englisch, Schlüssel), Mehrzahlformen, Sprachwahl aus gespeicherter Wahl, Browser und Deutsch, sowie einer weiteren Sprache nur über den Katalog (Test mit Polnisch). Datum, Zeit und Zahlen nach Sprache bei unverändertem Austauschwert; Dezimaleingaben nach Schreibweise der Sprache. Unternehmensauftritt aus einer geprüften Einstellung (Farben, Logo, Kontrast), zurücksetzbar. API Client aus dem synthetischen Vertrag erzeugt, mit Laufzeitprüfung der Antworten, getrennter `ETag` und Fachversion. Gemeinsame Zustände für Laden, leere Daten, Erfolg, fehlende Rechte, nicht gefunden, Konflikt (412), fehlende Vorbedingung (428), nicht erreichbar, Serverfehler und Netzwerkfehler; Fehlertexte stammen aus Status und stabilem Code, nie vom Server, mit Referenzkennung für den Support. Beispielformular mit Fehlerzusammenfassung und Fehlerkorrektur. Browserspeicher nur für die Sprachwahl, keine Sitzungstokens. Neuer Workflow [frontend.yml](.github/workflows/frontend.yml). Keine Fachfunktion, keine Anmeldung, keine Backendanbindung, kein Docker-Image. Alle Daten sind synthetisch und als solche gekennzeichnet.

**Prüfungen:** Lokal mit dem offiziellen Node 24.22.0 (SHA-256 geprüft) und npm 11.19.1: `npm ci --ignore-scripts`, Typerzeugung ohne Abweichung, strenge Typprüfung, 53 Unit-Tests ohne Auslassung (Sprachkataloge, Rückfall, Mehrzahl, Formate, Auftritt, Fehlerabbildung, Client gegen synthetische Antworten, Formularregeln, statische Regeln für Text, Speicher und Netzzugriff), Build. 19 Browserprüfungen mit Chromium 141 und Tastatur, Deutsch und Englisch: Sprachumschaltung und Wiederherstellung nach Neuladen, Sprunglink, Navigation, Fokus auf die neue Überschrift, sichtbarer Fokus, jeder Zustand einschließlich Laden und veralteter Antwort, Wiederherstellungsaktionen, Formular mit Fehlerzusammenfassung und Sprungverweisen, Auftritt anwenden und zurücksetzen, keine horizontale Verschiebung bei 320 Pixel, keine Konsolenwarnungen oder Fehler in allen Abläufen. Gegenproben: ohne Fokus auf die Überschrift und ohne `aria-describedby` scheitern genau die zwei zugehörigen Browserprüfungen; ein fest eingebauter Text in einer Komponente wird von der statischen Prüfung abgewiesen. Repository-Validator, Übersichtsprüfung, 25 Regressionstests und `git diff --check` erfolgreich. [CI-Lauf 39129923486](https://github.com/1uedev/task-manager/actions/runs/39129923486) auf `287f53b` mit Node 24.22.0, npm 11.19.1 und Google Chrome 155 auf dem Runner: Typerzeugung ohne Abweichung, 53 Unit-Tests und 19 Browserprüfungen ohne übersprungene Tests, Build erfolgreich; Repository-, Backend- und Qualifikationsläufe desselben Stands ebenfalls grün. Nach der Nachbesserung (siehe Review) lokal: Typprüfung, 59 Unit-Tests und Build sowie 21 Browserprüfungen (Chromium 141) ohne Auslassung, auch aus einem Verzeichnis mit Leerzeichen und Umlaut; Gegenprobe mit absichtlich zu dunkler Hinweisfarbe lässt genau die Browserprüfung zum dunklen Auftritt scheitern; [CI auf PR #72](https://github.com/1uedev/task-manager/pull/72) mit allen vier Läufen (Repository, Frontend, Backend, Qualifikation) grün. Windows wurde nicht ausgeführt.

**Review:** Getrennte KI-Gegenprüfung nach DEC-040 durch die Codex-Sitzung codex-20261018-frontend-review-01 auf PR #70 ([Prüfbericht](https://github.com/1uedev/task-manager/blob/6ab54470e0d622273f24a6eb8f674dd8d409ca09/docs/REVIEW_TASK-023_PR-70.md)): keine blockierenden Befunde, drei mittlere (F1 Dezimaleingabe, F2 Logo-Adresse, F3 Kontraste) und ein niedriger Befund (F4 Testpfade unter Windows). (F1) `parseDecimal` akzeptiert nur noch die Trennzeichen der gewählten Sprache und Gruppen aus genau drei Ziffern; `1 5` ist in Deutsch, Englisch und Französisch ein Fehler, auch mit geschütztem und schmalem geschütztem Leerzeichen. Sprachen mit Leerzeichen als Gruppentrenner (Französisch) bleiben mit vollständigen Gruppen gültig. (F2) Das Logo wird gegen eine feste Basis geparst; Rückwärtsschrägstriche, Steuer- und Leerzeichen werden abgewiesen, eine lokale Adresse muss auf der Basisherkunft bleiben, gespeichert wird der normalisierte Wert. (F3) Die Texte für Hinweise, Rahmen, Fehler, Fokus und Verweise werden aus Hintergrund, Text und Primärfarbe abgeleitet und im Zweifel auf die Textfarbe zurückgesetzt, die ohnehin Kontrast 4,5 erreicht. Zusätzlich gibt es einen dunklen Beispielauftritt (Codex Kombination) und eine Browserprüfung dafür. (F4) Dateipfade der Tests werden mit `fileURLToPath` gebildet, der Testserver prüft, dass der aufgelöste Pfad in `dist` bleibt. Der Prüfweg lief lokal von einem Verzeichnis mit Leerzeichen und Umlaut. Unter Windows wurde **nicht** gelaufen; die Korrektur ist dort ungeprüft. Lokal nach der Nachbesserung: Typprüfung, 59 Unit-Tests und Build sowie 21 Browserprüfungen (Chromium 141) ohne Auslassung; Gegenprobe mit einer absichtlich zu dunklen Hinweisfarbe lässt genau die Browserprüfung zum dunklen Auftritt scheitern. Die Korrekturen wurden nicht erneut durch Codex geprüft; ihre Nachweise sind Eigenprüfung der umsetzenden Sitzung, die Korrektur zu F4 ist unter Windows ungeprüft.

**Abnahme:** Holger, 2026-10-18, im Projektgespräch erklärte Annahme von TASK-023 („ich nehme task 023 so ab“) nach Bericht über die Nachbesserung in PR #72, ausdrücklich ohne erneute Codex Prüfung; bestätigt durch Merge dieses Abschluss-PR.

**Offen und Folgeaufgaben:** (1) Das Schema `Problem` im synthetischen Vertrag kennt `correlationId` und `instance` nicht und ist geschlossen; der Produktvertrag muss beides aufnehmen und für Erweiterungsfelder öffnen (Q5). (2) Noch nicht festgelegt: verbindlicher Barrierefreiheitsstandard, unterstützte Browser außerhalb von Chromium, mobile Mindestansichten (REQ-UX-001). Es gab weder eine automatische Barrierefreiheitsprüfung noch eine Prüfung mit Screenreader. (3) Seitenwahl, Übersetzungs- und Komponentenbibliothek bleiben eine spätere Entscheidung. (4) Verwaltung des Unternehmensauftritts, Benutzereinstellung für Zeitzone, Anmeldung mit Cookie und CSRF sowie Übersetzung konfigurierbarer Feldbezeichnungen folgen mit den Fachaufgaben (TASK-024 und später). Die Prüfbericht-Datei `docs/REVIEW_TASK-023_PR-70.md` liegt nur auf dem Codex Branch (Commit 6ab5447), nicht auf main. TASK-024 und TASK-026 bis TASK-031 bleiben blockiert; TASK-025 liegt bei Codex.

**Referenz:** Reservierung #68 (71291c9), Ergebnis #70 (fad1648), Nachbesserung #72.
