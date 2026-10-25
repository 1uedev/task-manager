# Zentrale offene Aufgaben

Stand 18. Oktober 2026 · Planung, Repository-Infrastruktur und Frontend-Grundfläche (TASK-023)

Diese Datei enthält den gemeinsamen Arbeitsstand aller beauftragten KI-Sitzungen. Status und Zuständigkeit werden gemäß [AGENTS.md](AGENTS.md) koordiniert. Eine Aufgabe kann mehrere Anforderungen konkretisieren. Ein offener Entwurf gilt nicht als fachlich abgenommen.

Repository: [1uedev/task-manager](https://github.com/1uedev/task-manager). Maßgeblich ist diese Datei auf main. TASK-013 richtet die Arbeitsumgebung ein. Die übergreifende Koordination liegt bei Holger oder einer ausdrücklich von ihm benannten Sitzung. Aufgaben werden über einen reservierenden Pull Request vergeben; siehe docs/AI_WORKFLOW.md. GitHub Issues sind Diskussions- und Einstiegspunkte, keine zweite Aufgabenquelle.

Planungsentwürfe aus TASK-002 bis TASK-011 dürfen auf der vorhandenen Fassung von TASK-001 vorbereitet werden. Die noch offene fachliche Abnahme von TASK-001 verhindert solche Entwürfe nicht. Abhängige Festlegungen und die Produktimplementierung werden dadurch nicht vorweggenommen.

<!-- TASK-BOARD:START -->
## Aufgabenübersicht

Automatisch aus den Aufgabeneinträgen weiter unten erzeugt. Maßgeblich ist TODO.md auf main; eine lokale Kopie kann veraltet sein. Aktualisieren: `python3 scripts/task_board.py --write`.

| Kategorie | Anzahl | Bedeutung |
|---|---:|---|
| Frei und bearbeitbar (OFFEN) | 0 | Zur Reservierung verfügbar; Auftrag und Abhängigkeiten im Detail prüfen. |
| Durch eine KI reserviert (IN ARBEIT) | 1 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 0 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 29 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-050](#task-050-befund-r1-der-nachprüfung-von-task-049-beheben) | Befund R1 der Nachprüfung von TASK-049 beheben | claude-20261025-reviewfix-03 | 2026-10-25 |

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-001](#task-001-erste-zentrale-spezifikation-erstellen-und-prüfen) | Erste zentrale Spezifikation erstellen und prüfen | Codex-Hauptsitzung-2026-10-12 | 2026-10-12 |
| [TASK-002](#task-002-umfang-und-spätere-funktionen-vervollständigen) | Umfang und spätere Funktionen vervollständigen | codex-20261016-inventory-01 | 2026-10-16 |
| [TASK-003](#task-003-architekturentscheidungen-vorbereiten) | Architekturentscheidungen vorbereiten | codex-20261013-architecture-01 | 2026-10-13 |
| [TASK-004](#task-004-identität-und-berechtigungen-spezifizieren) | Identität und Berechtigungen spezifizieren | codex-20261013-rights-fallback-01 | 2026-10-13 |
| [TASK-005](#task-005-fachmodell-und-projektkonfiguration-konkretisieren) | Fachmodell und Projektkonfiguration konkretisieren | codex-20261013-domain-01 | 2026-10-13 |
| [TASK-006](#task-006-helpdesk-portal-und-sla-regeln-ausarbeiten) | Helpdesk Portal und SLA Regeln ausarbeiten | codex-20261013-helpdesk-01 | 2026-10-13 |
| [TASK-007](#task-007-wiki-sprachen-und-unternehmensauftritt-spezifizieren) | Wiki Sprachen und Unternehmensauftritt spezifizieren | codex-20261015-wiki-01 | 2026-10-15 |
| [TASK-008](#task-008-ki-umfang-und-freigabeablauf-festlegen) | KI Umfang und Freigabeablauf festlegen | codex-20261015-ai-01 | 2026-10-15 |
| [TASK-009](#task-009-git-und-spätere-migration-eingrenzen) | Git und spätere Migration eingrenzen | codex-20261015-git-01 | 2026-10-15 |
| [TASK-010](#task-010-betrieb-sicherheit-und-leistung-planen) | Betrieb Sicherheit und Leistung planen | codex-20261015-ops-01 | 2026-10-15 |
| [TASK-011](#task-011-gemeinsamen-entwicklungs-und-review-ablauf-beschließen) | Gemeinsamen Entwicklungs und Review Ablauf beschließen | codex-20261016-review-01 | 2026-10-16 |
| [TASK-012](#task-012-planungsstand-zur-architekturfreigabe-zusammenführen) | Planungsstand zur Architekturfreigabe zusammenführen | codex-20261016-readiness-01 | 2026-10-16 |
| [TASK-013](#task-013-github-arbeitsumgebung-einrichten) | GitHub Arbeitsumgebung einrichten | Codex-Hauptsitzung-2026-10-13 | 2026-10-13 |
| [TASK-014](#task-014-regressionstest-von-freien-aufgaben-entkoppeln) | Regressionstest von freien Aufgaben entkoppeln | codex-20261016-contract-fix-01 | 2026-10-16 |
| [TASK-015](#task-015-bestätigte-backend--review--und-prioritätsentscheidungen-eintragen) | Bestätigte Backend-, Review- und Prioritätsentscheidungen eintragen | codex-20261016-decisions-01 | 2026-10-16 |
| [TASK-016](#task-016-frontend--modul--und-api-verträge-konkretisieren) | Frontend-, Modul- und API-Verträge konkretisieren | codex-20261016-contracts-01 | 2026-10-16 |
| [TASK-017](#task-017-bestätigte-frontend--modul--und-api-auswahl-eintragen) | Bestätigte Frontend-, Modul- und API-Auswahl eintragen | codex-20261016-architecture-decisions-01 | 2026-10-16 |
| [TASK-018](#task-018-sitzungsfristen-v1-tags-und-suche-spezifizieren) | Sitzungsfristen, V1-Tags und Suche spezifizieren | codex-20261017-tags-search-01 | 2026-10-17 |
| [TASK-019](#task-019-bestätigte-sitzungs--portal-tag--und-suchauswahl-eintragen) | Bestätigte Sitzungs-, Portal-Tag- und Suchauswahl eintragen | codex-20261017-v1-decisions-01 | 2026-10-17 |
| [TASK-020](#task-020-entscheidungsvorlage-und-aufgabenplan-für-den-entwicklungsstart) | Entscheidungsvorlage und Aufgabenplan für den Entwicklungsstart | codex-20261017-start-plan-01 | 2026-10-17 |
| [TASK-021](#task-021-werkzeugbasis-und-gemeinsame-verträge-qualifizieren) | Werkzeugbasis und gemeinsame Verträge qualifizieren | codex-20261017-qualification-01 | 2026-10-17 |
| [TASK-032](#task-032-bestätigten-startumfang-sitzungsstandard-und-kontoanlage-eintragen) | Bestätigten Startumfang, Sitzungsstandard und Kontoanlage eintragen | codex-20261017-start-decisions-01 | 2026-10-17 |
| [TASK-033](#task-033-bearbeitbare-und-reservierte-aufgaben-übersichtlich-anzeigen) | Bearbeitbare und reservierte Aufgaben übersichtlich anzeigen | codex-20261018-task-board-01 | 2026-10-18 |
| [TASK-044](#task-044-projektkonfiguration-mit-tickettypen-abläufen-und-eigenen-feldern) | Projektkonfiguration mit Tickettypen, Abläufen und eigenen Feldern | claude-20261023-projectconfig-02 | 2026-10-23 |
| [TASK-045](#task-045-kontoverwaltung-ergänzen-rest-von-paket-a4) | Kontoverwaltung ergänzen (Rest von Paket A4) | claude-20261023-accounts-01 | 2026-10-23 |
| [TASK-046](#task-046-ldap-anmeldung-paket-a5) | LDAP Anmeldung (Paket A5) | claude-20261023-ldap-01 | 2026-10-23 |
| [TASK-047](#task-047-interne-verwaltung-bleibt-erhalten-und-umstellung-von-verzeichniskonten-nachtrag-zu-dec-075) | Interne Verwaltung bleibt erhalten und Umstellung von Verzeichniskonten (Nachtrag zu DEC-075) | claude-20261023-localadmin-01 | 2026-10-23 |
| [TASK-048](#task-048-befunde-f1-bis-f4-der-prüfung-von-task-046-beheben) | Befunde F1 bis F4 der Prüfung von TASK-046 beheben | claude-20261024-reviewfix-01 | 2026-10-24 |
| [TASK-049](#task-049-befunde-der-nachprüfung-von-task-048-und-der-prüfung-von-task-047-beheben) | Befunde der Nachprüfung von TASK-048 und der Prüfung von TASK-047 beheben | claude-20261024-reviewfix-02 | 2026-10-24 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## TASK-001 Erste zentrale Spezifikation erstellen und prüfen

**GitHub:** [Issue #1](https://github.com/1uedev/task-manager/issues/1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** Codex-Hauptsitzung-2026-10-12 · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-PROD-003, REQ-DEV-001, REQ-DEV-002, REQ-DEV-004. **Abhängigkeiten:** keine. **Arbeitsstand:** Dokumentfassung im Repository; ursprüngliche Erstellung am 2026-10-12.

**Umfang:** Gesprächsentscheidungen in Anforderungen, Abnahmekriterien, offene Fragen, Roadmap und gemeinsame Arbeitsregeln überführen.

**Abnahmekriterien:** Bestätigte Vorgaben und Vorschläge sind getrennt. IDs und Verweise sind konsistent. TODO und DONE unterscheiden Entwurf und Abnahme. Es wurde keine Anwendung implementiert. Holger hat die erste Dokumentfassung fachlich geprüft beziehungsweise Änderungswünsche benannt.

**Ergebnis:** README.md, REQUIREMENTS.md, DECISIONS.md, ROADMAP.md, AGENTS.md, CLAUDE.md, TODO.md und DONE.md liegen als erste Fassung vor. Fachliche Abnahme offen.

**Eigenprüfung am 2026-10-12:** 86 eindeutige Anforderungs-IDs mit Status und Abnahmekriterien, 24 Entscheidungs-IDs, 21 offene Fragen und 12 Aufgaben geprüft. Anforderungs-, Aufgaben- und Fragenverweise sowie lokale Dokumentlinks sind auf gültige Ziele geprüft; Pflichtangaben der Aufgaben sind vorhanden. Der Ordner enthält ausschließlich die acht Markdown-Dokumente. Diese Strukturprüfung ist kein unabhängiges Review und kein Nachweis implementierter Produktfunktionen.

**Nächster Schritt:** Holgers fachliche Rückmeldung integrieren. Vorschläge bleiben bis zur Entscheidung als solche markiert.

## TASK-002 Umfang und spätere Funktionen vervollständigen

**GitHub:** [Issue #2](https://github.com/1uedev/task-manager/issues/2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261016-inventory-01 · **Aktualisiert:** 2026-10-16

**Bezug:** REQ-PROD-002, REQ-PROD-003, REQ-EXT-001, REQ-TIME-003. **Abhängigkeiten:** Entwurf TASK-001 vorhanden; Priorisierung nach fachlicher Rückmeldung. **Arbeitsstand:** task/task-002-codex-inventory; Funktionsinventar und priorisierte Roadmap als Entwurf vorhanden.

**Umfang:** V1-Vorschläge priorisieren, vollständiges Funktionsinventar der angestrebten Vergleichbarkeit erstellen und spätere Module abgrenzen. OPEN-020 und OPEN-021 einbeziehen.

**Abnahmekriterien:** Jeder Bereich hat Zweck, Mindestumfang, Abhängigkeit, Release-Status und Abnahmebeispiel. Bestätigte V1-Funktionen werden nicht ohne Entscheidung verschoben. Termine und Preise werden ohne Budget- und Kapazitätsbasis nicht zugesagt.

**Ergebnis:** [Funktionsinventar](docs/FUNCTION_INVENTORY.md) mit V1-Kern, Priorisierung aller V-Ergänzungen, späteren Funktionsbereichen, Mindestumfang, Abhängigkeiten, Abnahmebeispielen und KI-Nutzenbewertung. Roadmap verknüpft und konsolidiert. Bestätigte V1-Funktionen und frühe Ergänzungen bleiben erhalten; keine Preis-, Termin- oder Gleichheitszusage.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 86 REQ-IDs sind über Inventar und Bereichsverweise zugeordnet; V-Anforderungen auf Priorisierung geprüft. Herstellerseiten zur Bereichsabdeckung gelesen; eigene Mindestumfänge von Herstellerangaben getrennt. Keine Anwendungstests und keine unabhängige fachliche oder technische Abnahme.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-041 bestätigt die Priorität von Kanban, Gantt und einfachen Dashboards; die frühen Ergänzungen 2FA und Redmine-Import bleiben eingeplant. Übrige Prioritäten und Mindestumfänge bleiben Vorschläge.

**Nächster Schritt:** Holger prüft V1-Ergänzungen, Detailumfänge und verbleibende Reihenfolge unter OPEN-021. OPEN-020 benötigt Pilot, Budget, Lizenz- und Supportmodell. TASK-012 führt Entscheidungen zur Architekturfreigabe zusammen; Programmierung bleibt gesondert zu beauftragen.

## TASK-003 Architekturentscheidungen vorbereiten

**GitHub:** [Issue #3](https://github.com/1uedev/task-manager/issues/3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261013-architecture-01 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-ORG-002, REQ-DATA-001, REQ-DATA-002, REQ-API-001. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004 und TASK-005. **Arbeitsstand:** task/task-003-codex-architecture, separates Arbeitsverzeichnis; Architekturentwurf vorhanden.

**Umfang:** OPEN-001, OPEN-002 und OPEN-013 bearbeiten. Go und Java bewerten, Modulgrenzen, Unternehmenskontext und API-Grundvertrag entwerfen.

**Abnahmekriterien:** Ein begründeter Entscheidungsvorschlag benennt Betrieb, Prüfbarkeit, Aufwand und Grenzen. PostgreSQL bleibt alleiniger V1-Datenbankumfang. Kein Technologie-Prototyp oder Anwendungscode ohne zusätzliche Beauftragung.

**Ergebnis:** [Architekturentwurf](docs/ARCHITECTURE.md) mit Go/Java-Vergleich, begründeter Empfehlung, Modul- und Datenhoheit, späteren Unternehmensgrenzen, API-Grundvertrag, Hintergrundaufträgen und Docker-Betriebsbild. PostgreSQL bleibt die einzige unterstützte Datenbank. Vorschläge für Leistung und Hardware sind keine Messergebnisse. Die Backendwahl war im ursprünglichen Entwurf offen; der nachstehende Nachtrag dokumentiert die bestätigte Auswahl. Detailverträge bleiben offen.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Rechte- und Architekturentwurf auf konsistente Organisationsgrenzen, Feldrechte, Zugriffskontext und spätere 2FA geprüft. Quellen, Aufgabenverweise und Trennung von Vorschlag und Entscheidung geprüft. Keine Produkt-, Last- oder Integrationstests; keine unabhängige technische Prüfung.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-039 bestätigt Java mit Spring Boot; Versionen, TypeScript und Modulstruktur bleiben offen. DEC-040 legt zunächst gegenseitige KI-Reviews fest; zusätzliche menschliche Prüfung bleibt offen.

**Nachtrag aus TASK-017 am 2026-10-16:** DEC-042 bis DEC-044 bestätigen React/TypeScript, modularen Monolithen und HTTP/JSON im REST-Stil mit OpenAPI. Die frühere offene Grundauswahl ist damit entschieden; konkrete Modul- und API-Verträge bleiben prüfpflichtig.

**Nächster Schritt:** Holger prüft verbleibende Werkzeugdetails unter OPEN-001, konkrete Modulverträge unter OPEN-002 und API-Details unter OPEN-013. Der Vertragsentwurf aus TASK-016 ist separat KI-geprüft; weitere Zuordnung unter OPEN-017 organisieren. Produktimplementierung bleibt gesondert zu beauftragen.

## TASK-004 Identität und Berechtigungen spezifizieren

**GitHub:** [Issue #4](https://github.com/1uedev/task-manager/issues/4)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261013-rights-fallback-01 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-ACL-001 bis REQ-ACL-005, REQ-AUTH-001 bis REQ-AUTH-004, REQ-PORTAL-003. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-004-claude-rights, separates Arbeitsverzeichnis; koordinierte Übergabe an Codex am 2026-10-13.

**Umfang:** OPEN-004, OPEN-005 und OPEN-006 beantworten. Rechtekonflikte, externe Sicht, Feldrechte, LDAP-Lebenszyklus und 2FA-Termin festlegen.

**Abnahmekriterien:** Eine Rechte-Matrix und positive sowie negative Beispiele decken API, Suche, Exporte, E-Mail, Dateien und KI ab. Authentifizierung und fachliche Berechtigung bleiben getrennt. Fachliche Entscheidungen sind von technischen Vorschlägen unterscheidbar.

**Übergabe:** Die reservierte Claude-Sitzung konnte wegen eines ungültigen OAuth-Zugangs keinen Entwurf erzeugen. Die koordinierende Sitzung weist die Entwurfserstellung ausdrücklich Codex zu. Kein Claude-Review wird behauptet; ein späterer Einsatz erfordert erneute Anmeldung.

**Ergebnis:** [Rechteentwurf](docs/PERMISSIONS.md) mit Gruppenmatrix, Auswertungsregeln, Datenwegen, LDAP-Lebenszyklus und 16 fachlichen Prüfszenarien. Holgers drei Entscheidungen sind als DEC-027 bis DEC-029 in Anforderungen, Entscheidungen und Roadmap übernommen. Die weiteren Umsetzungsvorschläge bleiben offen.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Dokumente auf Widersprüche zur Organisationssicht, Feldverbotsregel und 2FA-Einordnung geprüft. Keine Produkt- oder Sicherheitstests ausgeführt, da keine Anwendung implementiert ist. Keine unabhängige Prüfung und kein Claude-Review.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-040 bestätigt gegenseitige KI-Reviews auch für Rechte und Anmeldung. Zusätzliche menschliche Prüfung bleibt offen; die fachliche Gesamtprüfung ist damit nicht erfolgt.

**Nächster Schritt:** Holger prüft Matrix und Szenarien. Allgemeine Aktionsrechte, delegierte Administration, LDAP-Lebenszyklus und das spätere 2FA-Verfahren bleiben unter OPEN-004 bis OPEN-006 offen. Konkrete KI-Review-Zuordnung unter OPEN-017 bestimmen; Umsetzung benötigt weiterhin gesonderten Auftrag.

## TASK-005 Fachmodell und Projektkonfiguration konkretisieren

**GitHub:** [Issue #5](https://github.com/1uedev/task-manager/issues/5)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261013-domain-01 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-ORG-003, REQ-TKT-001 bis REQ-TKT-005, REQ-TIME-001, REQ-TIME-002, REQ-REQ-001 bis REQ-REQ-003. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004. **Arbeitsstand:** task/task-005-codex-domain; Fachmodellentwurf vorhanden.

**Umfang:** OPEN-007 und OPEN-021 bearbeiten. Standardfelder, Beziehungen, Status, Regeln, Zeitkorrekturen und Anforderungsfreigaben beschreiben.

**Abnahmekriterien:** Änderungen bestehender Projektregeln sind durchgespielt. Verborgene Pflichtfelder, Archivierung und Änderungen nach Freigabe haben eindeutige Regeln. API und Oberfläche verwenden dieselbe Fachlogik.

**Ergebnis:** [Fachmodellentwurf](docs/DOMAIN_MODEL.md) mit Service- und Entwicklungsprojekt, Standardfeldern, Konfigurationsänderungen, verborgenen Pflichtfeldern, Beziehungen, Archivierung, Zeitkorrekturen und 16 fachlichen Prüffällen. Holgers Freigaberegel für neue Anforderungsrevisionen ist als DEC-030 in Anforderungen und Roadmap übernommen. Weitere Detailregeln bleiben Vorschläge.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Objekt- und Rechteabgrenzung, Zeitkorrekturbeispiel und Trennung von neuer Revision und späterer Baseline geprüft. Keine Anwendungstests und kein unabhängiges technisches Review.

**Nächster Schritt:** Holger prüft die Beispiele und offenen Vorschläge aus OPEN-007 und OPEN-021. TASK-006 konkretisiert zugehörige Nachrichten- und SLA-Regeln. Technische Umsetzung bleibt gesondert zu beauftragen.

## TASK-006 Helpdesk Portal und SLA Regeln ausarbeiten

**GitHub:** [Issue #6](https://github.com/1uedev/task-manager/issues/6)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261013-helpdesk-01 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-HD-001 bis REQ-HD-005, REQ-PORTAL-001 bis REQ-PORTAL-003; Präzisierung des Abnahmevorschlags in REQ-TKT-005. **Abhängigkeiten:** TASK-001; Rechte aus TASK-004. **Arbeitsstand:** task/task-006-codex-helpdesk; Helpdesk- und SLA-Entwurf vorhanden.

**Umfang:** OPEN-008 und OPEN-009 bearbeiten. Nachrichtenwege, Postfächer, Kundenidentität und Servicekalender festlegen.

**Abnahmekriterien:** Wochenenden, Feiertage, Zeitumstellung, Pausen, Wiedereröffnung und Prioritätswechsel sind anhand konkreter Zeitpunkte berechenbar. Doppelte Mails und Eskalationsversuche führen nicht zu unkontrollierten Doppelaktionen.

**Ergebnis:** [Helpdesk- und SLA-Entwurf](docs/HELPDESK_SLA.md) mit Mail-/Portalabläufen, Versandzuständen, Regel- und Kalenderzuordnung, 14 berechenbaren SLA-Fällen und zwölf weiteren Fachprüffällen. DEC-031 und DEC-032 dokumentieren Holgers Auswahl zur Prüfwarteschlange und Kundenpause. Anforderungen und Roadmap sind nachgeführt; andere Details bleiben Vorschläge.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. 17 Termin-/Offsetberechnungen mit separater Python-datetime/zoneinfo-Hilfsrechnung geprüft. Abgleich mit Rechte- und Fachmodellentwurf durchgeführt. Keine implementierte SLA-Engine, keine Mailintegration und keine Anwendungstests; kein unabhängiges Review.

**Nächster Schritt:** Holger prüft Fristwerte, Antwortdefinition, Startmesspunkt, Wiedereröffnung und Prioritätswechsel aus OPEN-008 sowie Mailanbieter und Zugangsregeln aus OPEN-009. TASK-010 ergänzt Betriebs- und Aufbewahrungsgrenzen. Umsetzung bleibt gesondert zu beauftragen.

## TASK-007 Wiki Sprachen und Unternehmensauftritt spezifizieren

**GitHub:** [Issue #7](https://github.com/1uedev/task-manager/issues/7)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261015-wiki-01 · **Aktualisiert:** 2026-10-15

**Bezug:** REQ-DOC-001 bis REQ-DOC-004, REQ-I18N-001 bis REQ-I18N-003, REQ-BRAND-001, REQ-BRAND-002, REQ-UX-001. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-007-codex-wiki, separates Arbeitsverzeichnis; Wiki-, Sprach- und Brandingentwurf vorhanden.

**Umfang:** OPEN-011 und OPEN-015 bearbeiten. Editor, Dokumentformat, Sprachfassungen, Konfliktbehandlung und Vorlagen entwerfen.

**Abnahmekriterien:** Einsprachige Seiten sind ohne Übersetzungszwang nutzbar. Wiederholte Bearbeitung erhält Inhalte. Sprache, Zeitzone und Formatierung sind abgegrenzt. Anwendung, Portal, Anmeldung und E-Mail sind beim Branding berücksichtigt.

**Ergebnis:** [Wiki-, Sprach- und Brandingentwurf](docs/WIKI_I18N_BRANDING.md) mit bestätigter Entwurfs-/Veröffentlichungstrennung (DEC-033), Sprachfassungen, Konfliktablauf, zwei Editor-Kandidaten, synthetischer Beispielseite, DE/EN-Mailvorlage und 16 fachlichen Prüffällen. Anforderungen, Roadmap und offene Entscheidungen sind nachgeführt. Die weiteren Detailregeln bleiben Vorschläge.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Veröffentlichung, Rechte, Sprachrückfall und Vorlagen mit den bestehenden Entwürfen abgeglichen; Editor-Grundlagen anhand zweier Primärquellen geprüft. Keine Editorimplementierung, Browser-, Mailclient- oder Anwendungstests und kein unabhängiges Review.

**Nächster Schritt:** Holger prüft Beispiele und Detailvorschläge aus OPEN-011 und OPEN-015. Editor und Formate bleiben offen; TASK-010 konkretisiert Speicher- und Sicherungsgrenzen. Umsetzung bleibt gesondert zu beauftragen.

## TASK-008 KI Umfang und Freigabeablauf festlegen

**GitHub:** [Issue #8](https://github.com/1uedev/task-manager/issues/8)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261015-ai-01 · **Aktualisiert:** 2026-10-15

**Bezug:** REQ-AI-001 bis REQ-AI-009, REQ-PERF-004; bestätigte V1-Ergänzung in REQ-PROD-002. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004. **Arbeitsstand:** task/task-008-codex-ai, separates Arbeitsverzeichnis; KI-Anwendungsfallentwurf vorhanden.

**Umfang:** OPEN-003 und OPEN-019 entscheiden. Skills, Provider-Konfiguration, erste Anwendungsfälle und Datenwege konkretisieren.

**Abnahmekriterien:** Alle Fachbereiche besitzen eine Nutzenbewertung. Nur Administratoren konfigurieren Modelle und Skills. Jeder ändernde Anwendungsfall besitzt eine sichtbare Bestätigung, Konfliktbehandlung und erneute Rechteprüfung. Lokaler Betrieb wechselt nicht still in die Cloud.

**Ergebnis:** [KI-Anwendungsfallentwurf](docs/AI_USE_CASES.md) mit vier bestätigten V1-Funktionen für interne berechtigte Benutzer (DEC-034, DEC-035), Kontext- und Empfängergrenzen, versionierten Markdown-Skills, Providervertrag, Bestätigung, Kosten-/Ausfallvorschlägen und 16 fachlichen Prüffällen. Weitere Fachbereiche erhalten eine Nutzenbewertung. Anforderungen und Roadmap sind nachgeführt.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Datenwege, Bestätigung, Anforderungsrevision, Wiki-Veröffentlichung und SMTP-Grenzen mit den bestehenden Entwürfen abgeglichen. Keine Modellaufrufe, Integrationstests, Qualitätsmessungen oder Produktimplementierung; kein unabhängiges technisches Review.

**Nächster Schritt:** Holger prüft die Detailvorschläge. OPEN-003 entscheidet die Anbieterreihenfolge, OPEN-019 Datenfreigaben, Kostenträger und Limits; TASK-010 ergänzt Betriebs- und Löschgrenzen. Externe KI-Nutzung bleibt nach V1. Technische Umsetzung benötigt weiterhin einen gesonderten Auftrag.

## TASK-009 Git und spätere Migration eingrenzen

**GitHub:** [Issue #9](https://github.com/1uedev/task-manager/issues/9)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261015-git-01 · **Aktualisiert:** 2026-10-15

**Bezug:** REQ-GIT-001 bis REQ-GIT-003, REQ-MIG-001, REQ-MIG-002, REQ-API-002. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-009-codex-git, separates Arbeitsverzeichnis; Git- und Migrationsentwurf vorhanden.

**Umfang:** OPEN-010 und den Migrationsteil von OPEN-016 bearbeiten. Git-Varianten und Objektarten wählen, Importquellen priorisieren.

**Abnahmekriterien:** Zuordnung und Anzeige sind klar von späteren Git-Schreibaktionen getrennt. Quellenzugriff, Metadatenrechte und Aktualisierung sind definiert. Die erste Version verspricht keinen Import; die anschließende Migration besitzt einen begrenzten, überprüfbaren Umfang.

**Ergebnis:** [Git- und Migrationsentwurf](docs/GIT_MIGRATION.md) mit drei vorgeschlagenen Objektarten, Benutzer-/Quellrechten, Aktualisierung und Fehlerfällen sowie begrenztem Redmine-Import, Zuordnung, Probelauf und Umschaltung. Acht Git- und acht Migrationsprüffälle sind beschrieben. DEC-036 und DEC-037 dokumentieren Holgers Auswahl; Anforderungen und Roadmap sind nachgeführt.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Rechte, Wiki-Veröffentlichung und KI-Umfang mit bestehenden Entwürfen abgeglichen; technische Grundlagen anhand offizieller GitHub-, GitLab- und Redmine-Dokumentation geprüft. Keine Anbieteranbindung, Extraktion, Migration oder Anwendungstests ausgeführt; kein unabhängiges Review.

**Nächster Schritt:** Holger prüft Objekt- und Importumfang. OPEN-010 entscheidet Authentifizierung und unterstützte Versionen; OPEN-016 Mapping, Quellversion und Aufbewahrung. TASK-010 ergänzt Netzwerk, Dateien und Sicherungen. Produktimplementierung bleibt gesondert zu beauftragen.

## TASK-010 Betrieb Sicherheit und Leistung planen

**GitHub:** [Issue #10](https://github.com/1uedev/task-manager/issues/10)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261015-ops-01 · **Aktualisiert:** 2026-10-15

**Bezug:** REQ-OPS-001 bis REQ-OPS-006, REQ-SEC-001, REQ-PERF-001 bis REQ-PERF-004, REQ-DATA-003, REQ-DATA-004. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-003. **Arbeitsstand:** task/task-010-codex-ops, separates Arbeitsverzeichnis; Betriebs- und Lastentwurf vorhanden.

**Umfang:** OPEN-012, OPEN-014 und Aufbewahrung aus OPEN-016 bearbeiten. Docker-Betrieb, Persistenz, Internetbedarf, Sicherung, Updates, Risiken und Lastprofil beschreiben.

**Abnahmekriterien:** Ressourcenannahmen sind nachvollziehbar, ohne feste RAM-Grenze. Die SSD-Planung berücksichtigt Anhänge. Wiederanlauf, zulässiger Datenverlust und Leistungsziele sind vor Tests festgelegt. Vorschläge werden nicht als gemessene Systemanforderungen ausgegeben.

**Ergebnis:** [Betriebsentwurf](docs/OPERATIONS.md) mit Docker-Referenzinstallation, Persistenz, Netzwegen, gemeinsamen Sicherungspunkten, Wiederanlauf, Updates, Aufbewahrung, Fünfjahres-Speicherrechnung, Lastprofil und 16 vorgeschlagenen Prüffällen. DEC-038 dokumentiert Holgers RPO-/RTO-Ziele; Anforderungen und Roadmap sind nachgeführt. Hardware, Leistungswerte und Detailverfahren bleiben Vorschläge.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Speicher-, Durchsatz- und Lastrechnungen separat mit Python geprüft; Docker-/PostgreSQL-Grundlagen anhand offizieller Dokumentation geprüft. Abgleich mit Mailversand, Wiki, Git, KI und Rechteentzug durchgeführt. Keine Containerinstallation, Sicherung, Wiederherstellung, Last- oder Sicherheitstests an einer Anwendung; kein unabhängiges technisches Review.

**Nächster Schritt:** Holger prüft Referenzplattform, Servicezeit, Betreiberpflichten, Datenmengen und vorgeschlagene Fristen. OPEN-012, OPEN-014 und OPEN-016 bleiben für diese Details offen. OPEN-017 klärt konkrete KI-Review-Zuordnung und Nachweise gemäß DEC-040; TASK-012 führt spätere Freigaben zusammen. Umsetzung bleibt gesondert zu beauftragen.

## TASK-011 Gemeinsamen Entwicklungs und Review Ablauf beschließen

**GitHub:** [Issue #11](https://github.com/1uedev/task-manager/issues/11)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261016-review-01 · **Aktualisiert:** 2026-10-16

**Bezug:** REQ-DEV-001 bis REQ-DEV-005. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-011-codex-review; Entwicklungs- und Review-Vorschlag vorhanden.

**Umfang:** OPEN-017 und OPEN-018 entscheiden. Koordination, getrennte Arbeitsstände, Aufgabenreservierung, PRs, technisches Review und fachliche Abnahme definieren.

**Abnahmekriterien:** Zwei gedachte gleichzeitige KI-Sitzungen können Aufgaben eindeutig übernehmen und übergeben. Eine veraltete TODO-Kopie überschreibt keinen aktuellen Stand. Fachliche Freigabe und technische Prüfung haben benannte Verantwortlichkeiten. Die technische GitHub-Einrichtung erfolgt separat in TASK-013. Weitergehende Review-Regeln werden hier geprüft.

**Ergebnis:** [Entwicklungs- und Review-Entwurf](docs/DEVELOPMENT_REVIEW.md) mit Rollen, risikogerechten Nachweisen, Commitbezug, acht Koordinations-/Unterbrechungsszenarien und klarer Trennung von Integration, technischer Prüfung und Fachabnahme. Bestehende AGENTS-Regeln bleiben gültig; zusätzliche Freigabeschwellen bleiben vorgeschlagen; den bestätigten anfänglichen Review-Weg ergänzt der nachstehende Nachtrag.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Acht Ablaufszenarien gedanklich geprüft. Zwei Fälle zusätzlich in isolierten temporären Git-Repositories ausgeführt: konkurrierende Eigentümer derselben Aufgabe erzeugten einen Textkonflikt; unabhängige Aufgaben ließen sich unter Erhalt beider Eigentümer zusammenführen. Das beweist keine atomare Sperre, verhindert kein bewusstes Zurückkopieren veralteter Inhalte und ersetzt weder reale parallele KI-Tests noch unabhängiges technisches Review.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-040 bestätigt zunächst gegenseitige KI-Reviews; menschliche technische Prüfung bleibt ausdrücklich offen. Die Antwort nimmt weder alle Review-Details noch den Gesamtentwurf ab.

**Nächster Schritt:** Holger prüft die vorgeschlagenen Nachweise und Schwellen unter OPEN-017 sowie übrige Ablaufdetails unter OPEN-018; konkrete prüfende KI-Sitzungen sind zuzuordnen. TASK-012 bündelt die Freigabepunkte. Keine Produktimplementierung und keine Abnahme im Namen Holgers.

## TASK-012 Planungsstand zur Architekturfreigabe zusammenführen

**GitHub:** [Issue #12](https://github.com/1uedev/task-manager/issues/12)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261016-readiness-01 · **Aktualisiert:** 2026-10-16

**Bezug:** REQ-PROD-003, REQ-DEV-004, REQ-DEV-005. **Abhängigkeiten:** TASK-002 bis TASK-011 im für V1 und Architektur erforderlichen Umfang. **Arbeitsstand:** task/task-012-codex-readiness; zentrale Entscheidungsvorlage vorhanden, Architekturfreigabe weiterhin offen.

**Umfang:** Entscheidungen, Anforderungskatalog, Abnahmeszenarien und Roadmap konsistent zusammenführen.

**Abnahmekriterien:** Kritische Architekturfragen sind entschieden. Restfragen haben Zuständigkeit und Fälligkeit. Der fachlich freigegebene Umfang ist sichtbar; keine Implementierung wird automatisch ausgelöst.

**Ergebnis:** [Entscheidungsübersicht zur Architekturfreigabe](docs/ARCHITECTURE_READINESS.md) mit bestätigtem Kern, allen 21 offenen Entscheidungsbereichen, Zuständigkeiten/Fälligkeiten, Prüfungspaketen und Nachweisübersicht. Sie verknüpft sämtliche Fachentwürfe. Kritische Entscheidungen und fachliche Abnahme stehen weiterhin aus; die Abnahmekriterien dieser Aufgabe sind daher noch nicht vollständig erfüllt. Keine Programmierung ausgelöst.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf Regressionstests und git diff --check erfolgreich. Alle 21 OPEN-IDs in der Entscheidungstabelle erfasst; 86 Anforderungen im Inventar zugeordnet, 31 V-Anforderungen dort priorisiert. Bestätigte DEC-001 bis DEC-038 bleiben unverändert. Nur vorhandene Dokument-, Rechen- und Git-Beispielnachweise angeführt; keine Produktmessung oder unabhängige Abnahme behauptet.

**Nachtrag aus TASK-015 am 2026-10-16:** Die drei Antworten sind in der Entscheidungsvorlage und den Fachentwürfen nachgeführt. Weitere Architekturentscheidungen und die fachliche Gesamtabnahme stehen weiterhin aus.

**Nachtrag aus TASK-017 am 2026-10-16:** DEC-042 bis DEC-044 bestätigen die weitere Grundauswahl. REQ-ARCH-002 wechselt von V zu B; der aktuelle Katalog enthält dadurch 56 B- und 30 V-Anforderungen. Die oben dokumentierte frühere Eigenprüfung mit 31 V bleibt als historischer Prüfstand erhalten.

**Nächster Schritt:** Grundrichtung nach DEC-039 bis DEC-044 ist dokumentiert. Verbleibende technische Verträge, Versionen sowie fällige Rechte-/Betriebs-/KI-Details paketweise prüfen. Erst nach den erforderlichen Entscheidungen einen gesonderten Implementierungsauftrag abgrenzen.

## TASK-013 GitHub Arbeitsumgebung einrichten

**GitHub:** [Issue #13](https://github.com/1uedev/task-manager/issues/13)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** Codex-Hauptsitzung-2026-10-13 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-DEV-001, REQ-DEV-002, REQ-DEV-003, REQ-DEV-005. **Abhängigkeiten:** vorhandene Spezifikation und DEC-025. **Arbeitsstand:** Bootstrap-Commit 9b791e0 auf main; Verknüpfungen und Nachweise im Branch setup/task-013-task-links.

**Umfang:** Das vom Auftraggeber angelegte Repository mit den zentralen Dokumenten, gemeinsamen KI-Regeln, Aufgaben-Issues, PR-Vorlage, Strukturprüfung und Schutz des Integrationsbranches einrichten. Die aktuelle Beauftragung umfasst die technische Einrichtung; die fachliche Abnahme der Produktspezifikation bleibt offen.

**Abnahmekriterien:** Dokumente sind auf GitHub erreichbar. Jede bestehende Aufgabe besitzt einen verknüpften Issue-Einstieg. Die lokale Strukturprüfung und GitHub CI laufen erfolgreich. main verlangt Pull Requests und den aktuellen erfolgreichen Prüfstatus; gewaltsames Überschreiben und Löschen sind gesperrt. Eine neue KI-Sitzung findet Start-, Reservierungs- und Übergabeanleitung. Einschränkungen der Review-Regeln sind dokumentiert.

**Ergebnis:** Dokumente, gemeinsame Regeln, Vorlagen und Prüfskripte sind veröffentlicht. Alle 13 Aufgaben haben Issues. main verlangt Pull Requests, einen aktuellen erfolgreichen repository-checks-Lauf von GitHub Actions und aufgelöste Review-Gespräche; Force-Push und Löschen sind auch für Administratoren gesperrt.

**Prüfungen:** Lokale Strukturprüfung und elf Regressionstests bestanden. [Erster GitHub-CI-Lauf](https://github.com/1uedev/task-manager/actions/runs/38366179158) erfolgreich. Repository-Einstellungen und Branchschutz über die GitHub-API zurückgelesen. Kein unabhängiges Review und keine fachliche Produktfreigabe behauptet.

**Referenz:** [Integrierter Ergebnis-PR #14](https://github.com/1uedev/task-manager/pull/14), einschließlich erfolgreicher PR-Prüfung.

**Nächster Schritt:** Holger kann die eingerichtete Arbeitsweise fachlich bestätigen und die Koordination weiterer Aufgaben benennen; bis dahin bleibt die Aufgabe zur Prüfung eingetragen. Die Produktimplementierung wurde nicht gestartet und kein kostenpflichtiger KI-Dienst aktiviert.

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## TASK-014 Regressionstest von freien Aufgaben entkoppeln

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261016-contract-fix-01 · **Aktualisiert:** 2026-10-16

**Bezug:** REQ-DEV-002, REQ-DEV-003, REQ-DEV-005. **Abhängigkeiten:** TASK-013; Fehler durch Reservierung von TASK-012 sichtbar. **Arbeitsstand:** task/task-014-codex-contract-fix; korrigierter Regressionstest vorhanden.

**Umfang:** Vorhandenen Test test_active_task_requires_owner unabhängig vom aktuellen Aufgabenbestand machen. Produktcode und Validatorregeln bleiben außerhalb des Umfangs.

**Abnahmekriterien:** Test prüft eine aktive Aufgabe ohne Besitzer auch dann, wenn keine freie Aufgabe mehr existiert. Alle vorhandenen Tests bestehen; der negative Nachweis bleibt wirksam. Keine Pflichtprüfung wird deaktiviert.

**Ergebnis:** Der Test verwendet eine eigene synthetische Aufgabe und prüft sowohl IN ARBEIT als auch BEREIT ZUR PRÜFUNG auf fehlenden Besitzer. Ein gültiger Besitzer beseitigt den Fehler. Der produktive Validator und seine Regeln bleiben unverändert.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf Regressionstests und git diff --check erfolgreich. Zusätzlich eine temporäre Repository-Kopie ohne irgendeine OFFEN-Aufgabe validiert und alle elf Tests dort erfolgreich ausgeführt. Der vorherige Fehlschlag der TASK-012-Reservierung ist damit lokal reproduzierbar behoben; deren GitHub-Prüfung muss nach Integration erneut laufen.

**Nachtrag am 2026-10-16:** Fix über PR #39 integriert; Reservierung PR #37 auf aktuellen main aktualisiert, alle elf Tests und GitHub-Pflichtprüfung erfolgreich, anschließend regulär integriert. Keine Schutzregel umgangen.

**Nächster Schritt:** Technisches Ergebnis und Prüfnachweise durch Holger prüfen lassen; bis zur Abnahme bleibt die Aufgabe hier nachvollziehbar. Keine Produktimplementierung.

## TASK-015 Bestätigte Backend-, Review- und Prioritätsentscheidungen eintragen

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261016-decisions-01 · **Aktualisiert:** 2026-10-16

**Bezug:** REQ-ARCH-001, REQ-DEV-004, REQ-DEV-005, REQ-PROD-003, REQ-EXT-001. **Abhängigkeiten:** TASK-002, TASK-003, TASK-011, TASK-012 als Entwürfe und Holgers ausdrückliche Auswahl vom 16. Oktober 2026. **Arbeitsstand:** task/task-015-codex-decisions; bestätigte Auswahl und betroffene Planungsdokumente nachgeführt.

**Umfang:** Drei bestätigte Auswahlentscheidungen mit neuen DEC-IDs festhalten und betroffene Anforderungen, Architektur, Review-Regeln, Roadmap und KI-Einstiegsdateien konsistent nachführen. Keine weiteren Detailvorschläge als bestätigt behandeln.

**Abnahmekriterien:** Java/Spring Boot ist als Backend gesetzt; zunächst gegenseitige KI-Reviews bei weiterhin offener menschlicher Prüfung; Kanban, Gantt und einfache Dashboards nach den frühen Ergänzungen 2FA/Redmine-Import priorisiert. Versionen, TypeScript, Modulstruktur und weitere Details bleiben offen. Keine Programmierung oder Gesamtfreigabe abgeleitet.

**Ergebnis:** DEC-039 bis DEC-041 halten die ausdrücklichen Antworten fest. Zentrale Spezifikation, offene Entscheidungen, Architektur, Review-Dokumente, Funktionsinventar, Roadmap, Einstiegsregeln und betroffene Übergaben sind konsistent nachgeführt. TypeScript, Versionen, Modulstruktur, zusätzliche Sprintfunktionen und übrige Detailvorschläge bleiben offen. Keine Anwendung implementiert.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Entscheidungseinträge, Anforderungs-/Fragenverweise und verbliebene Formulierungen zur Backendwahl, menschlichen Prüfung und Roadmap-Priorität auf Widersprüche geprüft. Keine zusätzliche KI-Gegenprüfung oder menschliche technische Prüfung als erfolgt behauptet; reine Dokumentnachführung, keine Anwendungstests.

**Nachtrag aus TASK-017 am 2026-10-16:** Die damals noch offene Auswahl von TypeScript, modularem Monolithen und HTTP/JSON/OpenAPI ist inzwischen durch DEC-042 bis DEC-044 bestätigt. Die früheren Aussagen oben beschreiben den Stand bei Bearbeitung von TASK-015.

**Nächster Schritt:** Holger prüft die korrekte Übernahme; restliche technische Detailverträge unter OPEN-001, OPEN-002 und OPEN-013 sowie Detailverfahren unter OPEN-017/OPEN-018 prüfen. Separate Implementierungsfreigabe bleibt erforderlich.

## TASK-016 Frontend-, Modul- und API-Verträge konkretisieren

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261016-contracts-01 · **Aktualisiert:** 2026-10-16

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-API-001, REQ-API-002, REQ-DEV-004. **Abhängigkeiten:** TASK-003, TASK-004, TASK-005, TASK-008 und TASK-012 als Entwürfe; DEC-039 und DEC-040. **Arbeitsstand:** task/task-016-codex-contracts; Vertragsentwurf im separaten Arbeitsverzeichnis erstellt.

**Umfang:** Prüffähige Vorschläge zu TypeScript, Datenhoheit und Modulabhängigkeiten sowie API-Verträgen mit konkreten Abläufen und Fehlerfällen ausarbeiten. Offene Grundentscheidungen unter OPEN-001, OPEN-002 und OPEN-013 gezielt vorlegen; nur ausdrückliche Antworten als bestätigt eintragen. Keine Produktimplementierung.

**Abnahmekriterien:** Vorschläge enthalten Verantwortlichkeiten, Vertragsänderungen bei paralleler KI-Arbeit, Rechte- und Versionsprüfung, Wiederholbarkeit sowie prüfbare positive und negative Beispiele. Bestehende Fachentscheidungen bleiben erhalten. Ausgeführte Dokumentprüfungen und KI-Gegenprüfung werden von künftigen Anwendungstests getrennt. Offene Details und nicht bestätigte Entscheidungen bleiben sichtbar.

**Ergebnis:** [TECHNICAL_CONTRACTS.md](docs/TECHNICAL_CONTRACTS.md) konkretisiert TypeScript-Datenverträge, Modulordnung, Transaktionen, API-Abläufe, Feldrechte, Konflikte und Wiederholung mit 16 vorgeschlagenen Prüffällen. Entscheidungsübersicht, Architektur und Einstieg verweisen darauf. Die drei Grundentscheidungen wurden Holger vorgelegt; ohne Antwort bleiben sie offen. Keine neuen Entscheidungen, Anwendungsdateien oder zusätzlichen Pflichtdienste angelegt.

**KI-Gegenprüfung am 2026-10-16:** Eine getrennte Codex-Prüfsitzung contracts_review las den Entwurf in Commit 5bba988c15a918363069fab309887c9d75f16c0d gegen die bestehenden Fachverträge. Zwei P2-Befunde: Freigabe benötigte zusätzlich die technische Inhaltsversion; fachliche Einmaligkeit einer KI-Aktionskennung musste vom HTTP-Wiederholungsfenster getrennt werden. Korrektur in 484d85e74cdc5044079c78000346bda10fc77556, einschließlich C14/C15. Dieselbe separate Prüfsitzung bestätigte in gezielter Nachprüfung beide dokumentarischen Korrekturen ohne weiteren konkreten Widerspruch. Kein Claude-Review, Anwendungstest oder Sicherheitsnachweis behauptet.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Technische Primärquellen zu TypeScript, Spring Modulith, OpenAPI und HTTP geprüft. Die 16 Produktfälle sind erst beschrieben, nicht ausgeführt. Holgers fachliche Abnahme bleibt ausstehend.

**Nachtrag aus TASK-017 am 2026-10-16:** Holger hat die drei zuvor vorgelegten Grundentscheidungen bestätigt (DEC-042 bis DEC-044). Der Ergebnis- und Reviewbericht oben bleibt als historischer Stand erhalten. Detailregeln und die 16 Prüffälle sind damit nicht pauschal abgenommen.

**Nächster Schritt:** Verbleibende Detailverträge unter OPEN-001, OPEN-002 und OPEN-013 einschließlich Authentifizierung, Versionen, Wiederholungsfenster und Listengrenzen gezielt prüfen. Eine gesonderte Implementierungsfreigabe bleibt erforderlich.

## TASK-017 Bestätigte Frontend-, Modul- und API-Auswahl eintragen

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261016-architecture-decisions-01 · **Aktualisiert:** 2026-10-16

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-API-001. **Abhängigkeiten:** TASK-003, TASK-012, TASK-016 und Holgers ausdrückliche Auswahl vom 16. Oktober 2026. **Arbeitsstand:** task/task-017-codex-architecture-decisions; Entscheidungen im eigenen Arbeitsverzeichnis nachgeführt.

**Umfang:** TypeScript, modularen Monolithen und HTTP/JSON im REST-Stil mit OpenAPI als bestätigte Grundentscheidungen erfassen; Anforderungen, Entwürfe, offene Fragen und KI-Einstiegsregeln konsistent nachführen.

**Abnahmekriterien:** Drei neue DEC-IDs erfassen ausschließlich die ausgewählten Grundentscheidungen. Sprach-/Werkzeugversionen, konkrete Modul-/Transaktionsverträge, Authentifizierung, Laufzeiten und weitere Detailregeln bleiben offen. Keine Gesamtfreigabe, Anwendung oder Implementierungsbeauftragung ableiten. Historische Prüfstände erhalten.

**Ergebnis:** DEC-042 bis DEC-044 bestätigen React/TypeScript, modularen Monolithen und HTTP/JSON im REST-Stil mit OpenAPI. Anforderungen sind auf Version 0.11 angehoben; REQ-ARCH-002 ist im Kern B. Offene Fragen, technische Entwürfe, Funktionsinventar, Roadmap, KI-Einstieg und frühere Übergaben sind nachgeführt. Details bleiben Vorschläge; keine Produktimplementierung oder Gesamtfreigabe abgeleitet.

**KI-Gegenprüfung am 2026-10-16:** Eine getrennte Codex-Sitzung architecture_decisions_review prüfte alle zehn geänderten Dokumente in 119d20622483b534f1d9a2a9dead5ab122814a08 gegenüber 3e42d6d2c81254271620b2baaeea562d512cafc7 auf korrekte Übernahme, Überbestätigung und Widersprüche. Keine konkreten Befunde; offene Details und historische Nachträge waren konsistent. Dieser nachträgliche Prüfbericht ist Eigenprotokoll der koordinierenden Sitzung und nicht Teil des genannten Review-Commits. Kein Claude-Review, Produkt- oder Sicherheitsnachweis behauptet.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Gezielter Vergleich: alle 86 REQ-IDs erhalten, nur REQ-ARCH-002 wechselt V zu B; 56 B und 30 V. Alle früheren DEC-Einträge unverändert, genau DEC-042 bis DEC-044 ergänzt; 21 OPEN-IDs und DONE.md unverändert. Keine Anwendungstests.

**Nächster Schritt:** Holger prüft die korrekte Übernahme. Danach verbleibende technische Detailverträge und Werkzeugversionen sowie die fälligen Rechte-/Betriebs-/KI-Fragen konkret entscheiden. Zusätzliche fachliche Abnahmen und ein gesonderter Implementierungsauftrag bleiben erforderlich.

## TASK-018 Sitzungsfristen, V1-Tags und Suche spezifizieren

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261017-tags-search-01 · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-AUTH-001, REQ-AUTH-005, REQ-ORG-003, REQ-TKT-004, REQ-TAG-001, REQ-SRCH-001, REQ-PERF-002, REQ-PERF-003, REQ-ARCH-002, REQ-API-001. **Abhängigkeiten:** TASK-003, TASK-004, TASK-005, TASK-010, TASK-016 und Holgers Ergänzungen vom 17. Oktober 2026. **Arbeitsstand:** task/task-018-codex-tags-search; Entwurf im separaten Arbeitsverzeichnis nach Reservierung #47.

**Umfang:** Konfigurierbare automatische Abmeldung und umfangreiches Tag-System für Tickets/Tasks und Projekte sowie performante Suche für V1 in die Anforderungen aufnehmen. Sitzungssemantik, Tag-Verwaltung und Suchumfang gezielt klären. Direkte Modulaufrufe, REST und gRPC für den bestätigten modularen Monolithen einordnen. Keine Produktimplementierung.

**Abnahmekriterien:** Bestätigte Kernvorgaben erhalten stabile REQ-/DEC-IDs; Detailvorschläge und offene Antworten bleiben getrennt. Tags, Suche und Sitzungsablauf berücksichtigen API, Portal, Rechte, Konflikte und messbare spätere Prüffälle. Keine unbelegte Leistungszusage oder Jira-Funktionsgleichheit. Der interne Kommunikationsvorschlag widerspricht nicht DEC-043/DEC-044.

**Ergebnis:** REQ-AUTH-005, REQ-TAG-001 und REQ-SRCH-001 sowie DEC-045 bis DEC-047 erfassen die neuen V1-Kernvorgaben. Anforderungen Version 0.12, Roadmap, Funktionsinventar, Architektur-/Betriebsentwurf und KI-Einstieg sind nachgeführt. PERMISSIONS.md beschreibt konfigurierbare Sitzungsfristen mit acht späteren Prüffällen; TAGS_SEARCH.md beschreibt Katalogpflege, Massenaktionen, Sichtgrenzen, PostgreSQL-Suchvorschlag, Lastprofil und 17 spätere Prüffälle. Direkte Java-Modulaufrufe im selben Prozess sind empfohlen, REST bleibt an Frontend-/Integrationsgrenzen bestätigt; gRPC ist keine neue Pflicht. Detailwerte und zusätzliche Suchbereiche bleiben Vorschläge.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle 14 geänderten Dateien in c556ee17db8d5d55fb4080ac428ae108ad427f29 gegenüber c8b7d188562cddab0f202a603f2677d2004cd19a. Zwei Befunde: Namenskonflikte könnten verborgene Tags verraten; der ältere Rechtealgorithmus verlangte auch für angenommene Hintergrundaufträge eine aktive Browsersitzung. Korrektur 422025389caaa5183f1757277325a3ce49534998 bindet Namenseindeutigkeit an die Entdeckungsgrenze und trennt interaktive, Integrations- und Auftragskontexte. Die gezielte Nachprüfung beider Korrekturen fand keine weiteren konkreten Widersprüche. Dieser nachträgliche Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review oder vollständiger Produkt-/Sicherheitsnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Gezielter Vergleich: 89 Anforderungen, davon 59 B und 30 V; alle früheren 86 REQ-IDs und deren Status erhalten. Genau drei neue DEC-IDs, alle 44 früheren DEC-Zeilen unverändert; 21 OPEN-IDs und DONE.md unverändert. Keine Anwendungs-, Last- oder Sicherheitstests; die 25 beschriebenen neuen Prüffälle sind nicht ausgeführt.

**Nachtrag aus TASK-019 am 2026-10-17:** Die drei Antworten sind durch DEC-048 bis DEC-050 erfasst: ausschließlich feste Dauer seit Anmeldung, Tag-Erstellung auch durch berechtigte Portalbenutzer und gemeinsame Suche einschließlich Wiki/Anforderungen; Dateiinhalte später. Die oben dokumentierten Prüfstände bleiben historisch erhalten; der frühere Zwei-Fristen-Vorschlag ist ersetzt.

**Nächster Schritt:** Standard-/Grenzwerte, konkrete Katalog-/Vergaberegeln, Suchdetails und Messziele prüfen. Diese Teilentscheidungen nehmen weder sämtliche Detailvorschläge noch die Architektur insgesamt ab. Produktimplementierung bleibt gesondert zu beauftragen.

## TASK-019 Bestätigte Sitzungs-, Portal-Tag- und Suchauswahl eintragen

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261017-v1-decisions-01 · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-AUTH-005, REQ-TAG-001, REQ-SRCH-001, REQ-DOC-004, REQ-PERF-003. **Abhängigkeiten:** TASK-004, TASK-007, TASK-010, TASK-012, TASK-018 und Holgers ausdrückliche Auswahl vom 17. Oktober 2026. **Arbeitsstand:** task/task-019-codex-v1-decisions; Übernahme im separaten Arbeitsverzeichnis nach Reservierung #49.

**Umfang:** Ausschließlich feste Sitzungsdauer seit Anmeldung, Tag-Erstellung auch durch berechtigte externe Portalbenutzer und gemeinsame V1-Suche über Tickets/Tasks, Projekte, Wiki und Anforderungen bestätigen; Dateiinhalte später. Anforderungen, Entwürfe, Roadmap und offene Fragen konsistent nachführen.

**Abnahmekriterien:** Genau diese Auswahl als drei neue Entscheidungen erfassen; frühere Entscheidungen und Prüfberichte erhalten. Keine Inaktivitätsfrist in den V1-Vertrag übernehmen. Externe Tag-Erstellung erweitert nicht automatisch Katalogverwaltung, Fachrechte oder Sichtbarkeit. Dateiinhalte bleiben außerhalb V1; Dateinamen-/Metadatenumfang und übrige Detailvorschläge sind nicht pauschal freigegeben. Keine Umsetzung oder fachliche Gesamtabnahme ableiten.

**Ergebnis:** DEC-048 bis DEC-050 erfassen ausschließlich die drei bestätigten Antworten. REQUIREMENTS.md steht auf Version 0.13. PERMISSIONS.md verwendet feste Dauer seit Anmeldung ohne Inaktivitätsfrist; TAGS_SEARCH.md berücksichtigt berechtigte externe Ersteller und gemeinsame Suche einschließlich Wiki/Anforderungen bei späteren Dateiinhalten. Sitzungsfälle angepasst, drei weitere Tag-/Suchfälle beschrieben. Inventar, Roadmap, Messprofil, Wiki-Vertrag, offene Fragen und KI-Einstieg sind konsistent nachgeführt; übrige Detailvorschläge bleiben offen.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle 13 geänderten Planungsdateien in 6d27a003774a93fadc7a48af52b31e132b0ff1ff gegenüber 1d3f7722d19a7c4dce44e04b915284083a5cf574, einschließlich umgebender Sitzungs-, Katalog-, Rechte- und Suchregeln. Keine konkreten Befunde: Auswahl korrekt übertragen, keine weitere Inaktivitätsfrist, keine automatische Rechteausweitung durch externe Tag-Erstellung, Wiki/Anforderungen in Suchumfang und Messprofil enthalten. Dieser anschließend ergänzte Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review oder Produktnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 89 REQ-IDs samt Status erhalten (59 B/30 V), genau drei DEC-IDs ergänzt; alle 47 vorherigen DEC-Zeilen unverändert. 21 OPEN-IDs und DONE.md unverändert. Acht Sitzungs- und 20 Tag-/Suchfälle sind beschrieben, nicht als Anwendungstests ausgeführt; keine Leistungs- oder Sicherheitsmessungen.

**Nächster Schritt:** Holger prüft die korrekte Übernahme. Danach verbleibende Fristwerte/Änderungsregeln, konkrete Katalog-/Vergaberegeln, Suchfelder/-filter, Dateinamen/Metadaten und Messziele entscheiden. Die drei beantworteten Auswahlfragen werden nicht erneut geöffnet; weitere Abnahmen und ein gesonderter Implementierungsauftrag bleiben ausstehend.

## TASK-020 Entscheidungsvorlage und Aufgabenplan für den Entwicklungsstart

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261017-start-plan-01 · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-PROD-002, REQ-ARCH-001, REQ-ARCH-002, REQ-API-001, REQ-DATA-001, REQ-AUTH-001, REQ-AUTH-005, REQ-ACL-001, REQ-ACL-003, REQ-TKT-001, REQ-TAG-001, REQ-SRCH-001, REQ-I18N-001, REQ-DEV-001 bis REQ-DEV-005. **Abhängigkeiten:** TASK-003, TASK-004, TASK-005, TASK-010, TASK-012, TASK-016, TASK-018 und TASK-019. **Arbeitsstand:** task/task-020-codex-start-plan; eigenes Arbeitsverzeichnis nach integrierter Reservierung #51.

**Umfang:** Bestehende Entwürfe zu einer kompakten Entscheidungsvorlage mit technischen Empfehlungen, offenen Fachfragen, erstem durchgängigen Entwicklungsablauf und kleinen KI-Arbeitspaketen mit Schnittstellen, Abhängigkeiten und Prüfungen zusammenführen. Werkzeugempfehlungen aus Primärquellen prüfen. Künftige Aufgaben ausschließlich zentral in TODO.md vormerken.

**Abnahmekriterien:** Erster Abschnitt ist vom vollständigen V1-Umfang getrennt; keine bestätigte V1-Funktion wird gestrichen. Bestätigte Vorgaben, Empfehlungen und unbeantwortete Fragen sind unterscheidbar. Künftige Aufgaben sind bis zum gesonderten Implementierungsauftrag gesperrt, ohne vorgetäuschte Besitzer oder fertiggestellte Funktionen. Kritische Übergaben und Review-Nachweise sind benannt. Kein Anwendungscode, Gerüst, Datenbank oder Container wird erstellt.

**Ergebnis:** [DEVELOPMENT_START.md](docs/DEVELOPMENT_START.md) bündelt einen ersten internen Ablauf, technische Werkzeug-/Vertragsempfehlungen, drei offene Fachfragen, Abhängigkeiten und zehn geplante Nachweise. TASK-021 bis TASK-031 sind zentral als BLOCKIERT und unbesetzt vorgemerkt, mit Arbeitsgrenzen, Übergaben und Review-Kriterien. Einstieg, offene Entscheidungen, Roadmap und KI-Regeln verweisen darauf. Keine bestätigte V1-Funktion verschoben, keine neue Entscheidung vorweggenommen, keine Anwendung erstellt.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle acht geänderten Planungsdateien in 9cf25809fee3869c7f4a7c605ce8834b86d117d5 gegenüber 40e3d8f1eddce9ccf1b94a3bcee36098074daa82 einschließlich Folgeaufgaben und umgebender Fachverträge. Keine konkreten Befunde: V1-Umfang erhalten, Aufgabenabhängigkeiten zyklenfrei, Konten-/SMTP-/Bootstrap-Fragen sowie feste Frist, Rechte, Transaktionen und Suchgeheimhaltung konsistent. Dieser anschließend ergänzte Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review, Anwendungstest oder Sicherheitsnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 89 Anforderungen samt Status unverändert (59 B/30 V), alle 50 bisherigen DEC-Zeilen unverändert; 21 OPEN-IDs erhalten. TASK-001 bis TASK-019 und DONE.md unverändert; elf Folgeaufgaben blockiert/unbesetzt, ausschließlich acht Markdown-Dateien geändert. Werkzeugempfehlungen anhand offizieller Primärquellen eingeordnet, nicht praktisch qualifiziert. Die zehn Start-Szenarien sind nur geplant.

**Nachtrag aus TASK-032 am 2026-10-17:** Die drei damaligen Auswahlfragen sind durch DEC-051 bis DEC-053 beantwortet: erster interner Ablauf, LDAP/Portal anschließend innerhalb V1; 24 Stunden als Standard neuer Installationen; administrative lokale Kontoanlage mit Einmalpasswort und Pflichtwechsel. Die oben berichteten Ergebnisse/Prüfungen bleiben historisch erhalten. Technische Detailfreigaben und Implementierungsauftrag sind weiterhin offen.

**Nächster Schritt:** Holger prüft verbleibende technische Empfehlungen und Details. Ein nächster begrenzter Implementierungsauftrag könnte ausdrücklich TASK-021 zur Werkzeug-/Vertragsqualifikation beauftragen; weitere Aufgaben benötigen ihren freigegebenen Umfang und akzeptierte Voraussetzungen. Fachliche Abnahme des Gesamtplans bleibt ausstehend.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-021 Werkzeugbasis und gemeinsame Verträge qualifizieren

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261017-qualification-01 · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-API-001, REQ-DATA-001, REQ-DEV-005. **Abhängigkeiten:** TASK-020 sowie ausdrücklich beauftragter Umfang und ausgewählte Kandidaten P1 bis P4. **Arbeitsstand:** task/task-021-codex-qualification, separates Arbeitsverzeichnis nach Reservierung #55. Holgers Fortsetzungsauftrag vom 17. Oktober 2026 beauftragt den angekündigten begrenzten Werkzeug-/Vertragsnachweis. P1 bis P4 werden dafür als zu prüfende Kandidaten verwendet, nicht als pauschal abgenommene Produktarchitektur; keine Fachimplementierung aus TASK-022 bis TASK-031.

**Umfang:** Exakte Werkzeug-/Bibliotheksversionen, Komponentenlizenzen, Modulordnung, Datenhoheit, Identitäts-/Rechtekontext und API-/Fehler-/Versionsvertrag für den ersten Abschnitt festlegen. Nach gesonderter Beauftragung einen begrenzten Build-/Generator-/Transaktionsnachweis erstellen; keine Fachmodule vorwegnehmen.

**Schnittstellen und Arbeitsbereich:** [TECHNICAL_BASELINE.md](docs/TECHNICAL_BASELINE.md), synthetische Quellen unter `qualification/`, Vertragsbeispiel unter `contracts/` und eigener CI-Workflow. Produktpfade `backend/` und `frontend/` existieren noch nicht. Die Übergabe liefert die qualifizierten Werkzeugversionen und gemeinsamen Vertragsregeln für TASK-022 und TASK-023; fachliche Typen und produktive API bleiben in deren Folgeaufgaben zu konkretisieren.

**Abnahmekriterien:** Java/Boot/PostgreSQL und React/TypeScript/Buildwerkzeuge gemeinsam qualifiziert; Generator verarbeitet Erfolgs-, Fehler- und Konfliktantworten. Modulprüfung, JPA-/JDBC-Transaktion und Rückabwicklung nachgewiesen. Lizenz-/Wartungsinformationen und verbleibende Lücken dokumentiert; keine ungeprüfte Alternativdatenbankzusage. Getrennte KI prüft Verträge und Nachweise.

**Ergebnis:** Der [Qualifikationsbericht](docs/TECHNICAL_BASELINE.md) hält exakte Versionen, gemeinsame Modul-/Daten-/API-Regeln, Herkunft, direkte Lizenzangaben, Wartungsgrenzen und Fehlerkorrekturen fest. Der [vollständige CI-Lauf 39063473112](https://github.com/1uedev/task-manager/actions/runs/39063473112) auf `79ad5fe18dfbbffeabbd46cd4643cf9bf01459c1` besteht: acht Java-Tests mit echtem PostgreSQL, sechs Clienttests mit den Java-JSON-Fixtures, Typgenerierung ohne Drift, strenge Typprüfung und React-Build. TypeScript 5.9.3 ersetzt im Versuch den mit dem Generator inkompatiblen 6.0.3-Kandidaten; keine erzwungene Peer-Auflösung. Kein Produkt, keine Anmeldung, Fachrechte, Suchmessung oder Restore-Prüfung erstellt.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte `ac27dc2d30434f49f130afca55867fffeb8a1a5d` gegen `970242b8083a5b64de54a7facc922f4d9301a6a9` sowie gezielt die Änderungen bis `94fa49c4f82a6905c902930072e0426cea825b05` und `79ad5fe18dfbbffeabbd46cd4643cf9bf01459c1`. Ein kleiner YAML-Beschreibungsfehler wurde korrigiert und nachgeprüft; danach keine offenen Befunde. Das letzte Nachreview las die heruntergeladenen Berichte und das CI-Protokoll und bestätigte die genannten Testzahlen. Keine eigenen Java-/Docker-Läufe des Reviewers, kein Claude-Review. Die abschließend ergänzte Ergebnisdokumentation und dieser Bericht sind Eigenprotokoll der koordinierenden Sitzung, keine zusätzliche unabhängige Freigabe.

**Eigenprüfung am 2026-10-17:** Repository-Validator, zwölf Regressionstests und git diff --check erfolgreich. Lokale Typgenerierung, Typprüfung und React-Build erfolgreich; Java-/PostgreSQL-/Client-Gesamtlauf auf dem flüchtigen GitHub-Runner. Frühere fehlgeschlagene Versuche sind im Bericht verlinkt. Die 89 Anforderungen mit Status (60 B/29 V), 53 historischen DEC-Zeilen, TASK-022 bis TASK-031 und DONE.md bleiben unverändert. DEC-054 ergänzt ausschließlich den begrenzten Auftrag. Die technische Prüfung ist keine fachliche Abnahme.

**Nachtrag aus TASK-034 am 2026-10-18:** Die Claude-Sitzung claude-20261018-review-01 hat das Ergebnis gegengeprüft; siehe [Prüfbericht](docs/REVIEW_TASK-021_TASK-033.md). Keine blockierenden Befunde; ein mittlerer Befund (fehlender Negativtest für die Transaktionspflicht) und mehrere niedrige Befunde und Hinweise zur Übergabe an TASK-022/TASK-023. Status, Zuständigkeit und Ergebnis bleiben unverändert; keine fachliche Abnahme.

**Nächster Schritt:** Holger prüft die qualifizierte Werkzeugbasis und die gemeinsamen Vertragsregeln. Danach können TASK-022 (Backend-/PostgreSQL-Grundlage) und TASK-023 (React-Grundfläche) mit benannten Grenzen beauftragt werden; benötigte Browser-/Betriebsdetails sind vorher festzuhalten. Sie bleiben bis dahin BLOCKIERT und unbesetzt. TASK-021 bleibt zur Prüfung in TODO.md.

## TASK-032 Bestätigten Startumfang, Sitzungsstandard und Kontoanlage eintragen

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261017-start-decisions-01 · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-PROD-001, REQ-PROD-002, REQ-AUTH-001, REQ-AUTH-005, REQ-PORTAL-003, REQ-DEV-004. **Abhängigkeiten:** TASK-004, TASK-006, TASK-012, TASK-020 und Holgers ausdrückliche Auswahl vom 17. Oktober 2026. **Arbeitsstand:** task/task-032-codex-start-decisions; separates Arbeitsverzeichnis nach integrierter Reservierung #53.

**Umfang:** Ersten internen Ablauf mit Anmeldung, Projekten, Tickets, Tags und Suche bei LDAP/Portal anschließend innerhalb V1, 24 Stunden feste Standardsitzungsdauer und administrative Kontoanlage mit Einmalpasswort und Pflichtwechsel bei erster Anmeldung als bestätigte Auswahl erfassen. Betroffene Anforderungen, Entwürfe, offene Fragen und gesperrte Folgeaufgaben konsistent nachführen.

**Abnahmekriterien:** Genau die drei Antworten als neue Entscheidungen aufnehmen; keine technische Umsetzung, Gesamtfreigabe oder weitere Detailentscheidung ableiten. Einmalpasswort und erzwungener Wechsel gelten auch für lokale Portalzugänge; sichere Ausgestaltung und Wiederherstellung bleiben ausdrücklich zur Konkretisierung. Frühere Entscheidungseinträge und Prüfberichte erhalten. TASK-021 bis TASK-031 bleiben blockiert/unbesetzt. Keine Anwendung erstellen.

**Ergebnis:** DEC-051 bis DEC-053 erfassen den ersten internen Abschnitt, 24 Stunden als Standardsitzungsdauer neuer Installationen und administrative lokale Kontoanlage mit Einmalpasswort/Pflichtwechsel. Anforderungen Version 0.14, Startvorlage, Rechte-/Portal-/API-Entwurf, Roadmap, Inventar und Einstieg sind konsistent nachgeführt. REQ-PORTAL-003 besitzt nun bestätigten Zugangskern; technische Detailverfahren bleiben offen. TASK-024/TASK-030 sind angepasst, alle elf Implementierungsaufgaben weiterhin BLOCKIERT/unbesetzt. Neun Sitzungs- und vier Kontoanlagefälle sind im Rechteentwurf beschrieben, nicht ausgeführt.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle 13 geänderten Dokumentdateien in 5245104b5fd519220ce96907d4d56b28f7c1b540 gegenüber 6a9c6fd12091ffbcb48326c09a990aae87d182c9. Keine konkreten Befunde: Auswahl korrekt, Sitzungsstandard von Einmalpasswortgültigkeit und API-Wiederholungsfenster getrennt, Pflichtwechsel für UI/API einschließlich lokaler Portalzugänge berücksichtigt. LDAP, Rechte und 2FA unverändert; sichere Detailverfahren offen. Historische Berichte erhalten. Dieser nachträglich ergänzte Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review, Anwendungstest oder Sicherheitsnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 89 REQ-IDs erhalten, nur REQ-PORTAL-003 wechselt V zu B (60 B/29 V). Genau drei DEC-IDs ergänzt; alle 50 früheren DEC-Zeilen unverändert. 21 OPEN-IDs, TASK-001 bis TASK-019 und DONE.md unverändert; TASK-020 hat einen datierten Nachtrag, TASK-024/TASK-030 die nötige Folgeplanung. Keine Anwendung, Last- oder Sicherheitstests erstellt oder ausgeführt.

**Nächster Schritt:** Holger prüft die korrekte Übernahme. Der nächste begrenzte Entwicklungsauftrag kann TASK-021 zur Werkzeug-/Vertragsqualifikation betreffen; technische Kandidaten, verbliebene Details und der Implementierungsauftrag bleiben gesondert zu entscheiden. Die drei beantworteten Auswahlfragen werden nicht erneut geöffnet.

## TASK-033 Bearbeitbare und reservierte Aufgaben übersichtlich anzeigen

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261018-task-board-01 · **Aktualisiert:** 2026-10-18

**Bezug:** REQ-DEV-001, REQ-DEV-002, REQ-DEV-003, REQ-DEV-005. **Abhängigkeiten:** vorhandene zentrale Aufgabenverwaltung aus TASK-013; ausdrücklicher Auftrag vom 18. Oktober 2026 zur Übersicht bearbeitbarer und durch KI reservierter Aufgaben. **Arbeitsstand:** task/task-033-codex-overview, separates Arbeitsverzeichnis nach integrierter Reservierung #57.

**Umfang:** Kompakte, aus den Aufgabeneinträgen erzeugte Übersicht in TODO.md für bearbeitbare, reservierte, blockierte und zur Prüfung bereite Aufgaben. Zuständigkeit und letzter Aktualisierungsstand sichtbar machen, Aktualisierung und Konsistenzprüfung automatisieren und den Einstieg für neue KI-Sitzungen erklären. Bestehende Produktaufgaben nicht eigenmächtig freigeben.

**Abnahmekriterien:** Jede offene Aufgabe erscheint genau in der passenden Statusgruppe. Reservierte Aufgaben nennen die zuständige Sitzung. Freie und blockierte Arbeit ist eindeutig getrennt; offene Aufgaben mit zugeordnetem Besitzer werden nicht als frei angeboten. Die Übersicht ist eine abgeleitete Ansicht derselben TODO-Datei, keine zweite Statusquelle. Veraltete Übersichten werden von CI erkannt. Bestehende Aufgaben, Entscheidungen und Abnahmen bleiben erhalten. Getrennte KI prüft Verfahren und Nachweise.

**Ergebnis:** Die Aufgabenübersicht oben in TODO.md zeigt alle Einträge nach Verfügbarkeit, Reservierung, Blockade und Prüfbereitschaft mit Aufgabenlink, zuständiger Sitzung und Aktualisierungsdatum. scripts/task_board.py erzeugt sie aus derselben Datei; der verpflichtende repository-checks-Job weist eine veraltete Ansicht zurück. Gemeinsame Arbeitsregeln und Einstieg für Codex/Claude erklären Aktualisierung und Reservierung. Keine automatische Vergabe, Freigabe oder zeitliche Aufhebung einer Reservierung.

**KI-Gegenprüfung am 2026-10-18:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte zunächst 11fb6d57c295e045f0bb87d2979c2fb22f4bc1b1 gegenüber dem integrierten Claim #57. Die Befunde zu unvollständigen Aufgabenüberschriften und einer vollständig abgearbeiteten TODO-Liste wurden in 37556037ae57ec0539ce47342da26f9c6bf3f8ab korrigiert. Die anschließende Prüfung erkannte noch eingerückte Überschriften; cdbe689fcc3fa891bea9fa34be81e30968c1f101 behebt diesen Fall. Die erneute gezielte Prüfung bestätigte die Korrektur ohne weitere konkrete Befunde. Der Reviewer prüfte Fälle im Speicher sowie Validator, Übersichtsprüfung und Diff; die vollständige Testsuite wurde von der koordinierenden Sitzung ausgeführt. Dieser nachträglich ergänzte Bericht ist Eigenprotokoll, kein Claude-Review oder Nachweis einer fachlichen Abnahme.

**Eigenprüfung am 2026-10-18:** Repository-Validator, Übersichtsprüfung, alle 25 Regressionstests und git diff --check erfolgreich. Tests prüfen unter anderem eindeutige Zuordnung, inkonsistente Besitzer, beschädigte Marker, leere Listen, Erhalt der Aufgaben und schreibfreie Zurückweisung fehlerhafter Überschriften. TASK-001 bis TASK-032 sowie Anforderungen, Entscheidungen, Roadmap und DONE.md bleiben unverändert. Für cdbe689 bestehen auch die GitHub-Läufe [Repository checks](https://github.com/1uedev/task-manager/actions/runs/39112206093) und [Technical qualification](https://github.com/1uedev/task-manager/actions/runs/39112206092); der Ergebnis-PR #58 prüft die abschließende Übergabefassung erneut.

**Nachtrag aus TASK-034 am 2026-10-18:** Die Claude-Sitzung claude-20261018-review-01 hat das Ergebnis gegengeprüft; siehe [Prüfbericht](docs/REVIEW_TASK-021_TASK-033.md). Keine blockierenden Befunde; ein niedriger Befund zu nicht geprüften Ankerlinks und ein Hinweis zum Whitespace-Schritt der CI. Status, Zuständigkeit und Ergebnis bleiben unverändert; keine fachliche Abnahme.

**Nächster Schritt:** Holger prüft die Übersicht. Weitere KI-Sitzungen lesen den aktuellen Stand auf main und reservieren ausschließlich freie, beauftragte Aufgaben über das dokumentierte Verfahren. TASK-022 bis TASK-031 bleiben BLOCKIERT; fachliche Abnahmen und weitere Aufträge werden gesondert eingetragen.

## TASK-044 Projektkonfiguration mit Tickettypen, Abläufen und eigenen Feldern

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261023-projectconfig-02 · **Aktualisiert:** 2026-10-23

**Bezug:** REQ-ORG-003, REQ-ORG-004, REQ-TKT-001, REQ-ACL-003, REQ-API-001, REQ-UX-001. **Abhängigkeiten:** TASK-026, TASK-027, TASK-029, TASK-030 und TASK-043 (alle abgenommen). **Arbeitsstand:** Paket A2 der mit DEC-072 festgehaltenen Reihenfolge; Holger hat am 23. Oktober 2026 im Projektgespräch mit „mache weiter“ den Start freigegeben. Umgesetzt mit DEC-073 und den Migrationen V0018 bis V0020 im Ergebnis PR dieser Sitzung; Beschreibung in [docs/PROJECT_CONFIGURATION.md](docs/PROJECT_CONFIGURATION.md). Übernahme: Die Reservierung (Claim PR #150) lautete auf claude-20261023-projectconfig-01 und den Zweig claude/project-thread-r4dri5. Dieser Zweig bestand nicht, und im Konto lief keine weitere Sitzung zu diesem Repository; auf Holgers erneutes „mache weiter mit dem Projekt“ hat claude-20261023-projectconfig-02 die Aufgabe auf dem zugewiesenen Zweig claude/upbeat-goldberg-mwa4c2 fortgeführt. Holger bestätigt die Übernahme mit dem Merge oder widerspricht im Review.

**Umfang:** Erster Teil von A2, Detailregeln nach den Vorschlägen aus docs/DOMAIN_MODEL.md zu OPEN-007. (1) Tickettypen je Projekt. (2) Status und erlaubte Übergänge je Tickettyp statt des festen Ablaufs Neu, In Arbeit, Erledigt. (3) Eigene Felder je Projekt mit den Typen Kurztext, Langtext, Ganzzahl, Datum, Wahr/Falsch, Einfachauswahl und Benutzerreferenz; Auswahloptionen lassen sich deaktivieren, ohne Bestandswerte zu verlieren. (4) Pflichtfelder bei Anlage und je Übergang. (5) Konfiguration als Entwurf bearbeiten und als neue Version veröffentlichen; verträgliche Änderungen gelten sofort für alle Tickets des Projekts, ein noch verwendeter Status lässt sich nur mit Zuordnung entfernen, ein Feldtyp wird nicht still umgewandelt. (6) Eigene Felder in Ticketrechten, Historie und Suche wie die Standardfelder. (7) Oberfläche zum Bearbeiten der Projektkonfiguration und für eigene Felder im Ticket. Vorgesehene Standardwahl, mit dem Ergebnis zur Prüfung: Bestandstickets wechseln bei Veröffentlichung sofort auf die neue Version, keine Bindung einzelner Tickets an alte Versionen. Nicht Teil: Formulare und Portalformulare, Automationen (setzen Hintergrundaufträge aus A8 voraus), Projektvorlagen, Mehrfachauswahl und Dezimalzahl, Verschieben zwischen Projekten, Archivierung.

**Schnittstellen und Arbeitsbereich:** Projektmodul (Konfiguration), Ticketkern (Typ, Status, eigene Feldwerte, Übergangsprüfung), Feldrechte im Berechtigungsmodul, Suchprojektion, Verträge `contracts/projects.openapi.yaml`, `contracts/tickets.openapi.yaml` und betroffene Verträge, Oberfläche für Projektkonfiguration und Ticket, Tests.

**Abnahmekriterien:** Zwei Projekte mit unterschiedlichen Typen, Abläufen und Feldern nach den Beispielen A und B aus docs/DOMAIN_MODEL.md sind einrichtbar und bedienbar; die Prüffälle F-03 bis F-06 sind als Regression abgedeckt; ein nicht erlaubter Übergang und ein fehlendes Pflichtfeld lassen das Ticket unverändert; verborgene eigene Felder bleiben in API, Historie und Suche verborgen; Bestandstickets bleiben nach jeder Veröffentlichung gültig. Getrennte KI prüft Konfigurationsänderungen, Rechte und Bestandsdaten; Holger nimmt fachlich ab.

**Nächster Schritt:** Getrennte KI prüft Konfigurationsänderungen, Rechte und Bestandsdaten (DEC-040), insbesondere die Veröffentlichung mit Zuordnung unter Last, die Rechte auf eigene Felder in Historie und Suche und die Lesbarkeit älterer Konfigurationsdokumente. Holger nimmt fachlich ab und entscheidet die drei offenen Punkte aus DEC-073 (Löschen nie verwendeter Felder, nachträgliches Erzwingen von Pflichtfeldern bei Bestandstickets, Direktveröffentlichung neben dem Entwurf).

**Ergebnis und Prüfungen:** Lokal grün: 235 Backendtests gegen PostgreSQL 16 (darunter 11 Integrationstests `ProjectConfigurationApiTest` und 6 neue Prüfungen der Konfigurationsregeln), Frontend `npm run verify`, 78 Browsertests (3 neue). Die Fälle F-03 bis F-06 sind als Regression abgedeckt (Tabelle in docs/PROJECT_CONFIGURATION.md). Nicht lokal gelaufen: der Lebenszyklus gegen PostgreSQL 18.6 und der Workflow `Image` (kein Docker in dieser Umgebung); das übernimmt die CI.

## TASK-045 Kontoverwaltung ergänzen (Rest von Paket A4)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261023-accounts-01 · **Aktualisiert:** 2026-10-23

**Bezug:** REQ-AUTH-001, REQ-PORTAL-003, REQ-API-001, REQ-UX-001. **Abhängigkeiten:** TASK-024, TASK-030 und TASK-040 (alle abgenommen). **Arbeitsstand:** Paket A4 der mit DEC-072 festgehaltenen Reihenfolge; Holger hat am 23. Oktober 2026 im Projektgespräch mit „ja mache weiter“ den Start freigegeben und auf die Rückfrage „Kleine Lücken in A4 schließen“ gewählt. Der Kern des Pakets ist bereits mit DEC-058 umgesetzt (Einmalpasswort 72 Stunden gültig und einmalig, einmalige Anzeige bei der Übergabe, Wiederherstellung durch Zurücksetzen durch eine Administration, Notweg im Container); Selbstbedienung per Mail bleibt nach DEC-058 ausgeschlossen. Branch claude/upbeat-goldberg-mwa4c2 nach dem Merge dieser Reservierung; Ergebnis mit DEC-074, voraussichtlich ohne neue Migration oder mit V0021.

**Umfang:** (1) Die Kontenliste zeigt den Zustand des Einmalpassworts: offen mit Ablaufzeit, abgelaufen, oder nicht mehr gültig; die Ablaufzeit kommt als `oneTimePasswordExpiresAt` am Konto, ohne das Passwort preiszugeben. (2) Der Anzeigename eines Kontos lässt sich ändern (Recht `accounts.manage`, Protokoll). (3) Lesezugriff auf das Kontenprotokoll (`identity_audit_event`) seitenweise für `accounts.manage`, mit Anzeige in der Kontenverwaltung. (4) Eine Wartezeit nach Fehlversuchen lässt sich für ein Konto aufheben (Recht `accounts.manage`, Protokoll). Nicht Teil: Selbstbedienung per Mail, Benutzernamen ändern oder löschen, LDAP, Portalkonten, zweiter Faktor, Konten löschen.

**Schnittstellen und Arbeitsbereich:** Identitätsmodul (Konto, Protokoll, Wartezeit), `contracts/identity.openapi.yaml`, Kontenverwaltung der Oberfläche, Tests.

**Abnahmekriterien:** Der Zustand des Einmalpassworts ist in API und Oberfläche richtig für offen, abgelaufen und verbraucht und verrät nie das Passwort; Anzeigename und Aufheben der Wartezeit verlangen `accounts.manage`, wirken sofort und stehen im Protokoll; das Protokoll lässt sich über Seitengrenzen lesen und enthält keine Geheimnisse; bestehende Anmelde-, Pflichtwechsel- und Zurücksetzenfälle bleiben grün. Getrennte KI prüft Anmeldung und Protokoll; Holger nimmt fachlich ab.

**Nächster Schritt:** Getrennte KI prüft Anmeldung und Protokoll (DEC-040): Rechte und Serialisierung der drei neuen Endpunkte, dass kein Geheimnis in Liste und Protokoll gelangt, und die Wirkung von `clear-login-delay` auf die Zähler der Quelladressen. Holger nimmt fachlich ab.

**Ergebnis und Prüfungen:** DEC-074, Vertrag identity.openapi.yaml, Oberfläche der Kontenverwaltung. Lokal grün: Backendtests gegen PostgreSQL 16 (vier neue Integrationstests in `IdentityApiTest`), Frontend `npm run verify`, 83 Browsertests (5 neue in `accounts.test.mjs`). Nicht lokal gelaufen: PostgreSQL 18.6 und der Workflow `Image`; das übernimmt die CI.

## TASK-046 LDAP Anmeldung (Paket A5)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261023-ldap-01 · **Aktualisiert:** 2026-10-23

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-API-001, REQ-UX-001. **Abhängigkeiten:** TASK-024, TASK-030, TASK-040 und TASK-045 (alle abgenommen oder gemergt). **Arbeitsstand:** Paket A5 der mit DEC-072 festgehaltenen Reihenfolge; Holger hat am 23. Oktober 2026 im Projektgespräch mit „ja mache weiter“ die Reihenfolge fortgesetzt und auf Rückfragen zu OPEN-006 gewählt: allgemeines LDAPv3, Anlage des Kontos beim ersten Login, bei Ausfall scheitert die Anmeldung und laufende Sitzungen laufen bis zu ihrem festen Ende weiter. Branch claude/upbeat-goldberg-mwa4c2 nach dem Merge dieser Reservierung; Ergebnis mit DEC-075, Migration ab V0021.

**Umfang:** (1) Einstellbare Anbindung eines LDAPv3 Verzeichnisses (Adresse, Verschlüsselung `none`, `starttls` oder `ldaps`, Suchbasis, Suchfilter, Attribute für Kennung, Anzeigename), verwaltet mit dem Recht `accounts.manage` und protokolliert; das Passwort des Suchkontos kommt aus der Umgebung des Betriebs, nicht aus der Datenbank. (2) Verbindungsprüfung mit benannten Gründen ohne Geheimnisse. (3) Anmeldung: Gibt es ein lokales Konto dieses Namens, entscheidet allein das lokale Passwort; sonst sucht der Server den Eintrag, prüft das Passwort durch Bindung als diese Person und ordnet über eine unveränderliche Kennung des Verzeichnisses zu, nie über den Namen. Beim ersten erfolgreichen Login entsteht das Konto ohne Rechte und ohne lokales Passwort. (4) Ein LDAP Konto kann in der Anwendung deaktiviert werden; Passwort zurücksetzen und Passwortwechsel gibt es dafür nicht. (5) Fällt das Verzeichnis aus, scheitert die Anmeldung der LDAP Konten mit 503 `auth.directory_unavailable`; lokale Konten, offene Sitzungen und der Notweg im Container bleiben unberührt. (6) Oberfläche für Anbindung und Prüfung in der Kontenverwaltung. Nicht Teil: Gruppenzuordnung aus dem Verzeichnis, Synchronisation oder Massenimport, Passwortverwaltung im Verzeichnis, Zwischenspeicher für Passwörter, Portalkonten, zweiter Faktor, mehrere Verzeichnisse.

**Schnittstellen und Arbeitsbereich:** Identitätsmodul (Anmeldung, Konto, Anbindung), Migration, `contracts/identity.openapi.yaml`, Kontenverwaltung der Oberfläche, Tests mit einem im Test gestarteten Verzeichnis (UnboundID im Testumfang), Dokumentation.

**Abnahmekriterien:** Eine konfigurierte Anbindung lässt sich prüfen; ein LDAP Konto entsteht beim ersten Login, wird danach über die Kennung wiedergefunden und hat keine Rechte; eine gleichnamige lokale Identität übernimmt nie ein LDAP Konto und umgekehrt; Eingaben mit Sonderzeichen können weder den Suchfilter noch den Namen einschleusen; bei Ausfall scheitert nur die LDAP Anmeldung mit 503, ohne lokale Konten zu berühren; kein Passwort und kein Bindungsgeheimnis steht in Antworten, Protokoll oder Datenbank; bestehende Anmelde-, Pflichtwechsel- und Zurücksetzenfälle bleiben grün. Getrennte KI prüft Anmeldung und Zuordnung; Holger nimmt fachlich ab.

**Nächster Schritt:** Getrennte KI prüft Anmeldung und Zuordnung (DEC-040): die Reihenfolge lokale Prüfung vor Verzeichnis, die Zuordnung über die Kennung und nie über den Namen (Umbenennen, Namensübernahme, gleichzeitige Erstanmeldung), das Maskieren des Namens im Suchfilter, dass nie ein leeres Passwort gebunden wird, die Prüfung des Zertifikats gegen den Namen bei LDAPS und StartTLS, dass weder ein Passwort noch das Geheimnis des Suchkontos in Antwort, Protokoll oder Datenbank gelangt, und dass ein Ausfall nur die Verzeichnisanmeldung trifft. Holger nimmt fachlich ab und entscheidet die offenen Punkte aus DEC-075 (Notanmeldung für Verzeichniskonten bei dauerhaftem Ausfall, Zertifikatswechsel ohne Neustart).

**Ergebnis und Prüfungen:** DEC-075, Migration V0021 (`identity_account.auth_kind` und `external_id`, Tabelle `identity_ldap_setting`), Vertrag identity.openapi.yaml (`/settings/ldap`, `/settings/ldap/test`, `authKind`, 503), Verzeichnisclient mit dem JNDI Treiber der Java Laufzeit, Oberfläche in der Kontenverwaltung. Lokal grün: Backendtests gegen PostgreSQL 16, 259 Tests ohne Ausfall, darunter `LdapLoginTest` (16 Fälle mit einem im Test gestarteten Verzeichnis) und `LdapTlsTest` (LDAPS und StartTLS mit selbst signiertem Zertifikat, Zertifikat für anderen Namen wird abgewiesen); `lifecycle-check.sh` gegen eine leere Datenbank mit 20 Migrationen; Frontend `npm run verify` und 90 Browsertests. Nicht geprüft: PostgreSQL 18.6 (läuft in der CI), ein echtes Verzeichnis (OpenLDAP, Active Directory), Verhalten bei sehr großen Verzeichnissen und die Wirkung der Wartezeit bei Massenanmeldungen am Verzeichnis.

## TASK-047 Interne Verwaltung bleibt erhalten und Umstellung von Verzeichniskonten (Nachtrag zu DEC-075)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261023-localadmin-01 · **Aktualisiert:** 2026-10-23

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-AUTH-005, REQ-API-001, REQ-UX-001. **Abhängigkeiten:** TASK-046 (gemergt, Prüfung und Abnahme offen). **Arbeitsstand:** Holger hat am 23. Oktober 2026 auf die offene Frage aus DEC-075 entschieden: Es muss immer ein internes Konto mit Verwaltungsrechten vorhanden sein, das bei einem Ausfall des Verzeichnisses helfen kann, indem es lokale Konten einrichtet oder Verzeichniskonten auf lokal umstellt. Ein Zertifikatswechsel der LDAP Anbindung ohne Neustart muss derzeit nicht sein und kommt ins Backlog. Branch claude/upbeat-goldberg-mwa4c2 nach dem Merge dieser Reservierung; Ergebnis mit DEC-076.

**Umfang:** (1) Unverzichtbare lokale Verwaltung: Es gibt jederzeit mindestens ein aktives lokales Konto, das die Rechte `accounts.manage` und `permissions.manage` hält. Jede Änderung, die das aufheben würde, wird in ihrer Transaktion mit 409 abgewiesen: Deaktivieren des Kontos, Umstellen auf das Verzeichnis, Entzug des Rechts oder der Gruppenmitgliedschaft, Ändern oder Löschen der Gruppe. Ob und wie die bestehende Sperre für die letzte Verwaltung (TASK-039) dafür erweitert wird, entscheidet die Umsetzung nach Lesen des Adapters. (2) Umstellen: Eine Verwaltung mit `accounts.manage` stellt ein Verzeichniskonto auf lokal um. Das Konto behält Name, Rechte und Verlauf, löst sich von der Kennung des Verzeichnisses, erhält ein Einmalpasswort mit erzwungenem Wechsel, alle Sitzungen enden, der Vorgang steht im Protokoll. Danach entscheidet allein das lokale Passwort (Notweg im Container, Zurücksetzen und Wartezeit gelten wie bei jedem lokalen Konto). (3) Oberfläche in der Kontenverwaltung, Vertrag, Tests. (4) Backlog: Zertifikatswechsel der LDAP Anbindung ohne Neustart, mit Eintrag in ROADMAP.md. Nicht Teil: Umstellen eines lokalen Kontos auf das Verzeichnis, automatisches Umstellen bei Ausfall, Gruppenzuordnung aus dem Verzeichnis, zweiter Faktor.

**Schnittstellen und Arbeitsbereich:** Identitätsmodul (Konto, Umstellung), Adapter zwischen Identität und Berechtigungen und die Sperre der letzten Verwaltung, gegebenenfalls die Rechteänderungen im Berechtigungsmodul, `contracts/identity.openapi.yaml`, Kontenverwaltung der Oberfläche, Tests, Dokumentation.

**Abnahmekriterien:** Das letzte aktive lokale Konto mit den beiden Verwaltungsrechten lässt sich weder deaktivieren noch durch eine Rechteänderung entkleiden, auch nicht durch gleichzeitige Änderungen, solange Verzeichniskonten dieselben Rechte halten; ein Verzeichniskonto lässt sich umstellen und meldet sich danach nur noch mit dem neuen Einmalpasswort an, behält seine Rechte und ist im Protokoll vermerkt; bei ausgefallenem Verzeichnis kann die lokale Verwaltung Konten einrichten und umstellen; bestehende Anmelde-, Sperr- und Rechtefälle bleiben grün. Getrennte KI prüft Sperre und Umstellung; Holger nimmt fachlich ab.

**Nächster Schritt:** Getrennte KI prüft Sperre und Umstellung (DEC-040): dass die Sperre der lokalen Verwaltung in allen Wegen greift, die Mitgliedschaft, Recht oder Konto ändern (Deaktivieren, `setMember`, `setAction`), auch bei gleichzeitigen Änderungen und mit der Sperrreihenfolge Rechte, Identität; dass Verzeichniskonten nie als lokale Verwaltung zählen; dass die Umstellung Sitzungen beendet, die Verbindung zum Eintrag löst, die Rechte behält und kein Geheimnis preisgibt; und ob die relative Sperre (greift nur, wenn es vorher eine lokale Verwaltung gab) richtig gewählt ist. Holger nimmt fachlich ab.

**Ergebnis und Prüfungen:** DEC-076, `IdentityVerification.accountIsActiveLocal` und `IdentityAccess.userActiveLocal`, `LastLocalAdministration` (409 `permissions.last_local_administrator`), `POST /accounts/{id}/convert-to-local`, Schaltfläche mit Bestätigung in der Kontenverwaltung, Verträge identity und permissions, Backlog Zertifikatswechsel in ROADMAP.md; keine Migration. Lokal grün: Backendtests gegen PostgreSQL 16, 262 Tests ohne Ausfall (drei neue Fälle in `LdapLoginTest`, ein angepasster Fall in `IdentityPermissionsAdapterTest`), Frontend `npm run verify`, 92 Browsertests (zwei neue in `ldap.test.mjs`). Nicht geprüft: PostgreSQL 18.6 (läuft in der CI), ein echtes Verzeichnis, gleichzeitige Umstellung und Deaktivierung unter Last.

## TASK-048 Befunde F1 bis F4 der Prüfung von TASK-046 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261024-reviewfix-01 · **Aktualisiert:** 2026-10-24

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-API-001. **Abhängigkeiten:** TASK-046 (gemergt, Prüfung durchgeführt, Abnahme offen) und TASK-047 (gemergt). **Arbeitsstand:** Die getrennte Prüfung `codex-20261023-task046-review-01` ([Bericht](https://github.com/1uedev/task-manager/blob/review/task-046-pr-155/docs/REVIEW_TASK-046_PR-155.md) auf dem Branch review/task-046-pr-155) empfiehlt „nicht annehmbar im geprüften Stand“ wegen der Befunde F1 bis F4. Die Behebung ist eigener Umfang und braucht diese Reservierung; Vorbild ist TASK-041 nach der Prüfung von TASK-040. Branch claude/upbeat-goldberg-mwa4c2 nach dem Merge dieser Reservierung; Ergebnis mit DEC-077.

**Umfang:** (F1, hoch) Binäre Kennungsattribute wie `objectGUID` fallen heute durch die Textdekodierung von JNDI auf dieselbe Kennung; verschiedene Personen erreichen dasselbe Konto samt Rechten. Das Kennungsattribut wird immer als binär angefordert; druckbarer Text bleibt unverändert (bestehende Zuordnungen über `entryUUID` gelten weiter), alles andere wird eindeutig als Hex mit Präfix gespeichert, Werte mit Ersatzzeichen werden abgewiesen. Eine Migration löst bereits beschädigte Zuordnungen vom Verzeichnis, deaktiviert diese Konten, beendet ihre Sitzungen und protokolliert das. (F2, mittel) Auch beim Wiederfinden über die Kennung wird abgewiesen (401), wenn der Anmeldename einem anderen Konto gehört. (F3, niedrig) Fehlender, leerer oder null Wert für die Verschlüsselung ergibt 400 mit `ldap.security.invalid` statt 500. (F4, niedrig) Ein abgelehntes StartTLS wird als `directory.tls_failed` gemeldet statt als `directory.search_failed`. Dazu Regressionstests für alle vier Befunde. Nicht Teil: die Qualifikation mit einem echten Verzeichnisprodukt, Zertifikatswechsel ohne Neustart, Änderungen an TASK-047, die weiteren Hinweise des Berichts ohne Befund.

**Schnittstellen und Arbeitsbereich:** Identitätsmodul (Verzeichnisclient, Zuordnung, Einstellungsprüfung), eine Migration, Tests, Dokumentation und DEC-077; keine Änderung der Verträge außer der Beschreibung der Gründe.

**Abnahmekriterien:** Zwei Personen mit verschiedenen binären Kennungen und verschiedenen Rechten erreichen je ihr eigenes Konto; bestehende Zuordnungen über Textkennungen bleiben gültig; ein bekannter Eintrag mit dem Namen eines anderen Kontos wird abgewiesen, die Umbenennung desselben Eintrags funktioniert weiter; die drei leeren Varianten der Verschlüsselung ergeben 400; StartTLS ohne Unterstützung der Gegenstelle ergibt `directory.tls_failed`; beschädigte Alt-Zuordnungen werden von der Migration abgesichert; bestehende Anmelde-, Umstell- und Sperrfälle bleiben grün. Getrennte KI prüft die Behebung gezielt nach; Holger nimmt fachlich ab.

**Nächster Schritt:** Getrennte KI prüft die Behebung gezielt nach (DEC-040), besonders F1 mit zwei binären Kennungen und getrennten Rechten sowie die Migration V0022 und ihre Wirkung auf Konten mit Altschlüssel; danach die Qualifikation mit dem vorgesehenen Verzeichnisprodukt (eigene Aufgabe, braucht Holgers Auftrag). Holger nimmt TASK-046 und TASK-048 fachlich ab.

**Ergebnis und Prüfungen:** DEC-077, Zuordnungsschlüssel in `DirectoryClient.identifier`, Prüfung des Namens in `DirectoryAccounts.assign`, `LdapSettingsService.validate`, Migration V0022. Die vier neuen Regressionstests in `LdapLoginTest` scheitern gegen den alten Code mit genau den berichteten Symptomen (die zweite Person meldet sich als `bin-a` an, 200 statt 401, 500 statt 400, `search_failed` statt `tls_failed`). Neu außerdem `DirectoryIdentifierTest` und ein Test der Migration auf eingefügten Alt-Konten. Lokal grün: Backendtests gegen PostgreSQL 16, 272 Tests ohne Ausfall; `lifecycle-check.sh` gegen eine leere Datenbank mit 21 Migrationen; Frontend `npm run verify` (75 Tests), keine Änderung der Oberfläche. Nicht geprüft: PostgreSQL 18.6 (läuft in der CI), ein echtes Verzeichnisprodukt.

## TASK-049 Befunde der Nachprüfung von TASK-048 und der Prüfung von TASK-047 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261024-reviewfix-02 · **Aktualisiert:** 2026-10-24

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-API-001. **Abhängigkeiten:** TASK-046 und TASK-048 (gemergt, Abnahme offen). **Arbeitsstand:** Auch die getrennte Prüfung von TASK-047 (Sitzung `codex-20261024-task047-review-01`, [Bericht](https://github.com/1uedev/task-manager/blob/review/task-047/docs/REVIEW_TASK-047.md) auf dem Branch review/task-047) empfiehlt „nicht annehmbar im geprüften Stand“ wegen der Befunde F1 (hoch) und F2 (mittel); beide werden mit dieser Reservierung behoben, damit die Zuordnung im Verzeichnis nur einmal geändert wird. Die getrennte Nachprüfung `codex-20261024-task048-047-review-01` ([Bericht](https://github.com/1uedev/task-manager/blob/review/task-048-nachpruefung/docs/REVIEW_TASK-048_NACHPRUEFUNG.md) auf dem Branch review/task-048-nachpruefung) bestätigt F2 bis F4 als behoben, F1 nur als teilweise behoben und empfiehlt „nicht annehmbar im geprüften Stand“ wegen N1 (hoch) und N2 (niedrig). Die Behebung ist eigener Umfang und braucht diese Reservierung. Branch claude/upbeat-goldberg-mwa4c2 nach dem Merge dieser Reservierung; Ergebnis mit DEC-078.

**Umfang:** (N1, hoch) Ein vor der Behebung gespeicherter Schlüssel eines Verzeichnisses (Text nach alter Regel, getrimmt, auch mit dem Anfang `hex:`) kann mit dem neuen Schlüssel eines anderen Eintrags übereinstimmen; dann erreicht dieser Eintrag das Konto samt Rechten der anderen Person. Kein Altschlüssel darf künftig allein über seinen Text einem neuen Eintrag zugeordnet werden. Vorschlag der Sitzung: Die Form des Schlüssels wird versioniert (neue Spalte, Eindeutigkeit je Form); ein Altkonto wird beim ersten Wiederfinden nur dann auf die neue Form umgestellt, wenn der alte Schlüssel des Eintrags passt und der Anmeldename dem Konto gehört; sonst wird es nie zugeordnet. Eine strengere Alternative, alle vor der Behebung angelegten Verzeichniskonten vom Verzeichnis zu lösen wie die Migration V0022 es für beschädigte Schlüssel tut, entscheidet Holger vor dem Merge dieser Reservierung oder mit dem Ergebnis. (N2, niedrig) Sind Kennungs und Namensattribut dasselbe Attribut (nur anders geschrieben), geht der Anzeigename verloren, weil Rohbytes verworfen werden; der Name wird aus den Bytes als Text gelesen oder die Kombination wird ausdrücklich begrenzt. Dazu Regressionstests für beide Gegenbeispiele des Berichts zu TASK-048 (Altschlüssel `hex:80` und `trim-id` mit späterer Anmeldung beider Einträge), für N2, für beide Zeitpläne der Umstellung gegen eine laufende Anmeldung und für die Selbstumstellung in der Oberfläche, eine Migration und DEC-078. (F1 der Prüfung von TASK-047, hoch) Eine bereits laufende Anmeldung über das Verzeichnis überlebt die Umstellung auf lokal: Nach der Kontosperre wird auch das inzwischen lokale Konto akzeptiert, und die Sitzung entsteht, ohne dass das lokale Passwort geprüft wurde, oder das neue Einmalpasswort wird verbraucht. Nach der Kontosperre werden Kontotyp und Kennung gegen die authentifizierte Verzeichnisidentität geprüft; stimmen sie nicht mehr, wird die Anmeldung mit 401 verweigert, ohne ein Einmalpasswort zu verbrauchen. Der Sitzungsbeginn prüft zusätzlich, dass der Weg der Anmeldung zum Kontotyp passt. (F2 der Prüfung von TASK-047, mittel) Bei der Umstellung des eigenen Kontos beendet der Server die Sitzung und die Oberfläche verliert das Einmalpasswort, weil sie danach neu lädt. Die Selbstumstellung bleibt erlaubt; die Oberfläche lädt dann nichts mehr nach, zeigt das Einmalpasswort samt Hinweis auf die beendete Sitzung und führt auf Wunsch zur Anmeldung. Nicht Teil: die Qualifikation mit einem echten Verzeichnisprodukt, Zertifikatswechsel ohne Neustart, die Prüfung von TASK-047.

**Schnittstellen und Arbeitsbereich:** Identitätsmodul (Verzeichnisclient, Zuordnung), eine Migration, Tests, Dokumentation und DEC-078; keine Änderung der Verträge.

**Abnahmekriterien:** Die beiden Gegenbeispiele aus dem Bericht (Altschlüssel `hex:80` und `trim-id` auf einer befüllten V0022-Datenbank, danach Anmeldung eines anderen Eintrags mit kollidierendem Wert) führen nicht mehr zum Konto der anderen Person und ohne Rechte zu einem eigenen Konto oder zur Abweisung; das Altkonto selbst bleibt über sein Verzeichnis erreichbar oder ist sichtbar gesperrt, je nach der gewählten Lösung; der Anzeigename kommt auch bei gleichem Kennungs und Namensattribut an; eine laufende Verzeichnisanmeldung, die von einer Umstellung überholt wird, ergibt 401 ohne Sitzung und ohne verbrauchtes Einmalpasswort; bei der Selbstumstellung bleibt das Einmalpasswort sichtbar; bestehende Anmelde-, Umstell- und Sperrfälle bleiben grün. Getrennte KI prüft die Behebung gezielt nach; Holger nimmt fachlich ab.

**Nächster Schritt:** Getrennte KI prüft die Behebung gezielt nach (DEC-040): die Migration V0023 und ihre Wirkung auf Konten mit Altschlüsseln, die Zuordnung nach der Behebung mit beiden Gegenbeispielen des Berichts zu TASK-048, beide Zeitpläne der Umstellung gegen eine laufende Verzeichnisanmeldung, die Selbstumstellung in der Oberfläche und die Betriebshinweise zur relativen Sperre. Danach die Qualifikation mit dem vorgesehenen Verzeichnisprodukt (eigene Aufgabe, braucht Holgers Auftrag). Holger nimmt TASK-046 bis TASK-049 fachlich ab.

**Ergebnis und Prüfungen:** DEC-078, Migration V0023 (löst alle beim Einspielen vorhandenen Verzeichniskonten vom Verzeichnis, deaktiviert sie und beendet ihre Sitzungen), Prüfung von Kontotyp und Kennung nach der Kontosperre in `DirectoryAccounts.assign` und `SessionService.startSession`, Anzeigename aus Rohbytes in `DirectoryClient`, Selbstumstellung in `AccountsPage`. Die neuen Tests scheitern gegen den alten Code mit den berichteten Symptomen (die überholte Verzeichnisanmeldung ergibt 200 mit lokalem Konto und verbrauchtem Einmalpasswort, der Anzeigename ist null, die Seite lädt nach der Selbstumstellung nach und verliert das Passwort). Lokal grün: Backendtests gegen PostgreSQL 16, 275 Tests ohne Ausfall; `lifecycle-check.sh` gegen eine leere Datenbank mit 22 Migrationen; Frontend `npm run verify` (75 Tests) und 93 Browsertests. Nicht geprüft: PostgreSQL 18.6 (läuft in der CI), ein echtes Verzeichnisprodukt.

## TASK-050 Befund R1 der Nachprüfung von TASK-049 beheben

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261025-reviewfix-03 · **Aktualisiert:** 2026-10-25

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-UX-001. **Abhängigkeiten:** TASK-047 und TASK-049 (gemergt, Abnahme offen). **Arbeitsstand:** Die getrennte Nachprüfung `codex-20261025-task049-review-01` ([Bericht](https://github.com/1uedev/task-manager/blob/review/task-049/docs/REVIEW_TASK-049.md) auf dem Branch review/task-049) bestätigt die Behebung von N1, N2 und F1, bestätigt F2 aber nur teilweise und empfiehlt „annehmbar nach Behebung von R1“. Die Behebung ist eigener Umfang und braucht diese Reservierung. Branch claude/upbeat-goldberg-mwa4c2 nach dem Merge dieser Reservierung; Ergebnis mit DEC-079.

**Umfang:** (R1, mittel) Nach der Selbstumstellung hält nur die Kontenseite das Einmalpasswort. Eine bereits laufende Anfrage, die danach 401 erhält, oder eine weiterhin angebotene Kontenaktion führt über die allgemeine Behandlung der beendeten Sitzung zur Anmeldeseite und entfernt die Anzeige, bevor die Person das Passwort übernommen hat. Der Abschluss der Selbstumstellung wird deshalb ein eigener Zustand der Oberfläche oberhalb der Seiten: Das Einmalpasswort wird nur im Speicher gehalten und auf einer eigenen Seite gezeigt, die keine geschützten Aktionen mehr anbietet; ein späterer 401 entfernt sie nicht; nur die Schaltfläche „Zur Anmeldung“ beendet den Zustand. Außerdem die zweite Reihenfolge der Umstellung gegen eine laufende Verzeichnisanmeldung (Umstellung und lokaler Pflichtwechsel sind schon abgeschlossen, bevor die Anmeldung die Kontosperre erreicht) als dauerhafte Regression, die der Bericht als fehlend nennt. Dazu Browsertests für beide Gegenbeispiele des Berichts (verspätete Leseanfrage und weiterhin angebotene Kontenaktion). Nicht Teil: die Qualifikation mit einem echten Verzeichnisprodukt, Zertifikatswechsel ohne Neustart, Änderungen an der serverseitigen Umstellung.

**Schnittstellen und Arbeitsbereich:** Oberfläche (Sitzungskontext, Kontenverwaltung, Kataloge), Browsertests mit Testattrappe, ein Backendtest in LdapLoginTest, Dokumentation und DEC-079; keine Migration, keine Änderung der Verträge.

**Abnahmekriterien:** Nach erfolgreicher Selbstumstellung bleibt das Einmalpasswort sichtbar, auch wenn danach eine geschützte Anfrage 401 erhält oder eine Kontenaktion ausgelöst wird; die Seite bietet dann keine geschützten Aktionen mehr an; „Zur Anmeldung“ führt zur Anmeldeseite und entfernt das Passwort aus dem Speicher; die Umstellung eines fremden Kontos und das normale Ende einer Sitzung verhalten sich unverändert; die zweite Reihenfolge der Umstellung gegen die laufende Verzeichnisanmeldung ergibt 401 ohne Sitzung. Getrennte KI prüft die Behebung gezielt nach; Holger nimmt fachlich ab.

**Nächster Schritt:** Reservierung integrieren; danach umsetzen und mit DEC-079 als Ergebnis PR übergeben.
