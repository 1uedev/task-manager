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
| Blockiert (BLOCKIERT) | 3 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 26 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-028](#task-028-tag-kern-für-tickets-und-projekte-implementieren) | Tag-Kern für Tickets und Projekte implementieren | claude-20261020-tags-01 | 2026-10-20 |

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-029](#task-029-berechtigte-suche-für-tickets-und-projekte-implementieren) | Berechtigte Suche für Tickets und Projekte implementieren | — | 2026-10-17 |
| [TASK-030](#task-030-durchgängige-oberfläche-und-minimale-administration-integrieren) | Durchgängige Oberfläche und minimale Administration integrieren | — | 2026-10-17 |
| [TASK-031](#task-031-docker-vorführung-und-nachweise-des-ersten-abschnitts-liefern) | Docker-Vorführung und Nachweise des ersten Abschnitts liefern | — | 2026-10-17 |

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
| [TASK-026](#task-026-projekte-und-minimale-konfiguration-implementieren) | Projekte und minimale Konfiguration implementieren | claude-20261020-projects-01 | 2026-10-20 |
| [TASK-027](#task-027-ticketkern-mit-historie-und-konflikterkennung-implementieren) | Ticketkern mit Historie und Konflikterkennung implementieren | claude-20261020-tickets-01 | 2026-10-20 |
| [TASK-032](#task-032-bestätigten-startumfang-sitzungsstandard-und-kontoanlage-eintragen) | Bestätigten Startumfang, Sitzungsstandard und Kontoanlage eintragen | codex-20261017-start-decisions-01 | 2026-10-17 |
| [TASK-033](#task-033-bearbeitbare-und-reservierte-aufgaben-übersichtlich-anzeigen) | Bearbeitbare und reservierte Aufgaben übersichtlich anzeigen | codex-20261018-task-board-01 | 2026-10-18 |
| [TASK-041](#task-041-befunde-f1-und-f2-der-nachprüfung-von-task-040-beheben) | Befunde F1 und F2 der Nachprüfung von TASK-040 beheben | claude-20261020-adminfix-01 | 2026-10-20 |

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

## TASK-026 Projekte und minimale Konfiguration implementieren

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261020-projects-01 · **Aktualisiert:** 2026-10-20

**Bezug:** REQ-ORG-001, REQ-ORG-003, REQ-ACL-001, REQ-API-001. **Abhängigkeiten:** TASK-024, TASK-025, TASK-039 und TASK-040 sind abgenommen und integriert. Holger hat am 20. Oktober 2026 im Projektgespräch die Frage, ob TASK-026 mit der minimalen Projektkonfiguration aus docs/DEVELOPMENT_START.md beansprucht werden soll, mit „ja“ beantwortet. Das ist der Auftrag für den unten beschriebenen Umfang und bestätigt diese minimale Konfiguration für den ersten Abschnitt; TASK-027 bis TASK-031 bleiben blockiert und werden hier nicht freigegeben. Codex prüft das Ergebnis danach unabhängig nach DEC-040. **Arbeitsstand:** Branch claude/project-thread-r4dri5 nach dem Merge dieses Claim PRs; dieselbe Sitzung liefert zuerst die kleinere TASK-041 und setzt den Branch danach für TASK-026 neu von main auf. Ergebnis mit DEC-065, der auch den Satz zum Umfang in AGENTS.md, CLAUDE.md und README.md nachführt. Migrationen ab der nächsten freien Nummer V0011.

**Umfang:** Projekte, Mitgliedschaften sowie minimale pro Projekt gepflegte Typ-/Status-/Feldkonfiguration mit Verwaltungs-API umsetzen. Bestätigte minimale Konfiguration: Ticketfelder Titel, Beschreibung, Typ, Status und Zuständigkeit; Ausgangsablauf „Neu → In Arbeit → Erledigt“ wird je Projekt als Konfiguration angelegt und ist dort änderbar, keine globale unveränderliche Workflow-Schablone. Projektbezogene Rechte über den Adapter zwischen Projektmodul und Berechtigungsmodul (Objektkontext Projekt); keine zweite Benutzer- oder Gruppenverwaltung. Zwei unterschiedliche synthetische Projekte für Tests vorbereiten. Nicht Teil: Tickets (TASK-027), Tags, Suche, Oberfläche für Projekte (TASK-030), HTTP Endpunkte zur Gruppen- und Rechtepflege, Formulare und Automationen, Prüfung beim Commit statt beim Sperren, P2 bis P6, F1 bis F3 aus PR #96.

**Schnittstellen und Arbeitsbereich:** Geplante Zuständigkeit: Projektmodul und dessen Migrationen, eigener OpenAPI Vertrag unter contracts/, Verdrahtung im Anwendungseinstieg, Tests. Mitgliedschaft und Regeln werden über vereinbarte Rechteverträge verbunden. Liefert Projekt-/Konfigurationsversion und Objektkontext an Tickets.

**Abnahmekriterien:** Berechtigter Zugriff und Administration funktionieren; fremde Projekte bleiben über Listen und IDs geschützt. Konfigurationsversionen und ungültige Änderungen werden nach Fachvertrag behandelt. Getrennte KI prüft Datenhoheit, Projektgrenzen und administrativen Rechteentzug; ihre Prüfung ist angeboten, aber nicht Voraussetzung der Abnahme durch Holger.

**Ergebnis:** Projektmodul nach DEC-065. Migration V0011 (Projekt, Konfigurationsversionen, Mitglieder, Audit), Unternehmensrecht `projects.manage` beim ersten Start, Projektaktionen `project.read` und `project.manage` mit Mitgliedschaft, Projektadapter `ProjectAccess` mit Zeilensperre der Projekte, Endpunkte unter `/api/v1/projects` mit Vertrag `contracts/projects.openapi.yaml`, Ausgangskonfiguration Neu, In Arbeit, Erledigt mit den fünf festen Feldern, Konfigurationsänderung als neue Version, ETag und `If-Match` auf die Projektversion. Beschreibung und Grenzen in backend/README.md, Abschnitt „Projekte (TASK-026)“.

**Eigenprüfung:** Backend `verify` gegen lokale PostgreSQL 16.15 mit 154 Tests ohne Fehler, darunter `ProjectConfigurationTest`, `ProjectsApiTest` (Rechte, Sichtbarkeit fremder Projekte über Liste und Kennung, Delegation per Projektrecht, Versionen, Konfiguration, Mitglieder, Wartesperre bei Entfernung) und `ProjectsContractTest`. Gegenprobe: ohne Zeilensperre im Projektadapter scheitert die Sperrprüfung. Maßgeblich ist CI auf PostgreSQL 18.6. Eine unabhängige Prüfung nach DEC-040 steht aus.

**Korrektur nach unabhängiger Prüfung:** Codex hat in docs/REVIEW_TASK-026_PR-109.md (PR #113) zwei Befunde gemeldet, beide behoben. F1 (P2): Eine Statuskategorie `null` oder ohne Angabe ergab 500; sie antwortet jetzt `400 request.invalid` mit `statuses.category_invalid`. F2 (P3): Ein fremdes Projekt, dessen Zeile eine gleichzeitige Änderung hielt, wartete auf die Sperre und antwortete nach der Wartefrist mit 500 statt 404; der Projektadapter weist ein Konto ohne Mitgliedschaft jetzt vor der Zeilensperre ab, ohne zu warten, und prüft die Mitgliedschaft unter der Sperre erneut. Das gilt auch für die Ticketendpunkte aus TASK-027, die vorher 503 geantwortet hätten. Regressionen in `ProjectsApiTest` und `TicketsApiTest` schlagen ohne die Korrektur fehl; Backend `verify` lokal mit 168 Tests ohne Fehler.

**Nächster Schritt:** Codex prüft die Korrektur nach, sobald es wieder verfügbar ist; Holger nimmt fachlich ab; danach Übertrag nach DONE.md.

## TASK-027 Ticketkern mit Historie und Konflikterkennung implementieren

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261020-tickets-01 · **Aktualisiert:** 2026-10-20

**Bezug:** REQ-TKT-001, REQ-TKT-005, REQ-ACL-003, REQ-API-001. **Abhängigkeiten:** TASK-026 (auf main, BEREIT ZUR PRÜFUNG) sowie angenommener Ticket-/Konfigurationsvertrag. Holger hat am 20. Oktober 2026 im Projektgespräch mit „kannst du mit task 27 schon mal anfangen“ den Ticketkern beauftragt, parallel zur laufenden unabhängigen Prüfung von TASK-026 und TASK-041; Befunde daraus fließen nach. TASK-028 bis TASK-031 bleiben blockiert und werden hier nicht freigegeben. Codex prüft das Ergebnis danach unabhängig nach DEC-040. **Arbeitsstand:** Branch claude/project-thread-r4dri5 nach dem Merge dieses Claim PRs. Ergebnis mit DEC-067, der auch den Satz zum Umfang in AGENTS.md, CLAUDE.md und README.md nachführt. Migrationen ab der nächsten freien Nummer V0012.

**Umfang:** Tickets mit Titel, Beschreibung, Typ, Status und Zuständigkeit erstellen, lesen und ändern. Projektkonfiguration, Feldrechte, Änderungshistorie und Versionskonflikte durchsetzen. Tickets gehören zu genau einem Projekt, tragen eine Nummer je Projekt und die Konfigurationsversion des Projekts, mit der sie zuletzt geprüft wurden; Typ, Status, Übergänge und Pflichtfelder kommen aus der Projektkonfiguration aus TASK-026, die Zuständigkeit ist ein Konto, das Mitglied des Projekts ist. Rechte über Ticketaktionen des Berechtigungsmoduls im Projektkontext. Tags und Suche folgen über vereinbarte Verträge. Nicht Teil: Kommentare, Anhänge, Beziehungen, Benachrichtigungen, Oberfläche, Tags (TASK-028), Suche (TASK-029), Prüfung beim Commit statt beim Sperren. Von den offenen Punkten der Gegenprüfung von TASK-025 berührt nur P4 (Sperrzeitüberschreitung und Verklemmung als Ausnahme) den Ticketkern; die Ticketendpunkte beantworten diese Fälle als wiederholbaren Fehler statt mit 500, P2, P3, P5 und P6 bleiben unberührt.

**Schnittstellen und Arbeitsbereich:** Geplante Zuständigkeit: Ticketmodul, API-Operationen und eigene Migrationen. Öffentliche Kontext-/Änderungs- und Projektionsverträge für TASK-028/TASK-029; keine direkten Abfragen anderer Modultabellen.

**Abnahmekriterien:** Erfolgs- und Negativfälle, Feldfilterung, verbotene Übergänge, veraltete Versionen und vollständige Rückabwicklung geprüft. Aktuelle Rechte gelten auch bei konkurrierendem Entzug bis zur Schreibgrenze. Getrennte KI prüft Transaktion, Historie und API-Konfliktantworten.

**Ergebnis:** Ticketmodul nach DEC-067. Migration V0012 (Tickets, Nummernzähler je Projekt, Feldhistorie), Aktionen `ticket.read`, `ticket.create` und `ticket.edit` mit Mitgliedschaft im Projektkontext, Feldregeln des Ressourcentyps `ticket` für Lesen und Schreiben, Typ, Status, Übergänge und Pflichtfelder aus der aktuellen Projektkonfiguration, Zuständigkeit nur für aktive Mitglieder, Ticketversion als ETag mit `If-Match`, Historie je Feldänderung, 503 `ticket.busy` bei Sperrproblemen (P4). Endpunkte unter `/api/v1/projects/{projectId}/tickets` mit Vertrag `contracts/tickets.openapi.yaml`; der Projektport `ProjectAccess` liefert zusätzlich Mitgliedschaft und Projektschlüssel. Beschreibung und Grenzen in backend/README.md, Abschnitt „Tickets (TASK-027)“.

**Eigenprüfung:** Backend `verify` gegen lokale PostgreSQL 16.15 mit 165 Tests ohne Fehler, darunter `TicketsApiTest` (Anlage und Nummern, gleichzeitige Anlage, Sichtbarkeit und Projektgrenzen, verborgene Felder in Antwort und Historie, Konfiguration und Übergänge, geänderte Konfiguration und Pflichtfelder, Versionen, Anmeldung mit offenem Passwortwechsel, Warten bei gleichzeitigem Entfernen des Mitglieds, 503 bei gehaltener Ticketsperre) und `TicketsContractTest`; Lifecycle-Prüfung lokal bestanden. Maßgeblich ist CI auf PostgreSQL 18.6. Eine unabhängige Prüfung nach DEC-040 steht aus.

**Korrektur nach unabhängiger Prüfung:** Die Prüfung durch eine getrennte Claude Sitzung (docs/REVIEW_TASK-027_PR-112.md, PR #115) meldet drei Befunde, alle behoben. F1 (P2): Wer nur `ticket.create` oder nur `ticket.edit` ohne `ticket.read` hatte, erhielt 404 und die Anlage oder Änderung wurde zurückgerollt; jetzt gelingt sie, und die Antwort zeigt das Ticket ohne Feldwerte. F2 (P3): Ein NUL Zeichen in Titel oder Beschreibung ergab 500; jetzt `title.invalid` beziehungsweise `description.invalid`. F3 (P3): Unbekannte Felder bei der Anlage wurden still ignoriert; jetzt `400 request.invalid` mit `fields.unknown` wie bei der Änderung. Der Befund F2 der Prüfung von TASK-026 wirkte auch hier (503 statt 404 bei gesperrtem fremdem Projekt) und ist mit derselben Korrektur behoben. Regressionen in `TicketsApiTest` schlagen ohne die Korrektur fehl; Backend `verify` lokal mit 170 Tests ohne Fehler. Zum Hinweis H1 (Version und letzte Änderung zeigen, dass sich ein verborgenes Feld geändert hat) hat Holger am 20. Oktober 2026 „Ja, so lassen“ gewählt; festgehalten als Nachtrag 9 zu DEC-067, Code unverändert.

**Nächster Schritt:** Holger nimmt fachlich ab; danach Übertrag nach DONE.md.

## TASK-028 Tag-Kern für Tickets und Projekte implementieren

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261020-tags-01 · **Aktualisiert:** 2026-10-20

**Bezug:** REQ-TAG-001, REQ-ACL-004, REQ-TKT-004. **Abhängigkeiten:** TASK-027 (auf main, BEREIT ZUR PRÜFUNG) und TASK-025. Holger hat am 20. Oktober 2026 im Projektgespräch mit „kannst du weitermachen“ das nächste Paket beauftragt und auf die Frage „Welchen Tag Katalog soll der Tag Kern (TASK-028) zuerst bekommen?“ die Variante „Beides“ gewählt: ein Unternehmenskatalog für gemeinsame Begriffe und Tags an Projekten, dazu je Projekt ein eigener Katalog, nur für Mitglieder sichtbar. Vorgeschlagene Vergaberegeln aus derselben Frage: eigenes Recht zum Anlegen neuer Tags, Zuordnen zu einem Ticket braucht das Änderungsrecht am Ticket, Zuordnen zu einem Projekt das Projektverwaltungsrecht, Umbenennen und Archivieren ein eigenes Unternehmensrecht, Namen ohne Groß und Kleinschreibung eindeutig im jeweiligen Katalog, Portalnutzer später mit dem Portal. TASK-029 bis TASK-031 bleiben blockiert und werden hier nicht freigegeben. Eine getrennte KI prüft das Ergebnis danach nach DEC-040. **Arbeitsstand:** Branch claude/project-thread-r4dri5 nach dem Merge dieses Claim PRs. Ergebnis mit DEC-068, der auch den Satz zum Umfang in AGENTS.md, CLAUDE.md und README.md nachführt. Migrationen ab der nächsten freien Nummer V0013.

**Umfang:** Katalog, Erstell-/Zuordnungsrechte, Tag-Erstellung und Zuordnung zu Tickets sowie Projekten für den internen Ablauf umsetzen. Vertrag für weitere V1-Verwaltungsfunktionen und berechtigte externe Ersteller erhalten.

**Schnittstellen und Arbeitsbereich:** Geplante Zuständigkeit: Tag-Modul, Tag-Zuordnungen und eigene Migrationen. Objektkontext kommt über öffentliche Projekt-/Ticketverträge. Katalog-/Objektsicht und Eindeutigkeit folgen TAGS_SEARCH.md nach Auswahl; keine Rechteausweitung durch Tag-Erstellung.

**Abnahmekriterien:** Mehrfachzuordnung, unberechtigtes Erstellen/Zuordnen und versteckte Katalognamen geprüft; kombinierte Erstellung/Zuordnung bleibt bei Fehlern atomar. Getrennte KI prüft Entdeckungsgrenzen und Modultransaktionen. Das Ergebnis erklärt den noch offenen umfangreichen V1-Tag-Anteil.

**Nächster Schritt:** Reservierung integrieren; danach umsetzen und als Ergebnis PR mit DEC-068 übergeben.

## TASK-029 Berechtigte Suche für Tickets und Projekte implementieren

**Status:** BLOCKIERT · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-SRCH-001, REQ-TKT-004, REQ-TAG-001, REQ-PERF-003. **Abhängigkeiten:** TASK-028; Projektions-/Sichtbarkeitsvertrag und Messziele für den Abschnitt entschieden. **Arbeitsstand:** nur vorgemerkt; kein Branch reserviert, keine Anwendung erstellt.

**Umfang:** Text-/Projekt-/Status-/Tag-Suche für Tickets und Projekte, PostgreSQL-Indizes, Rechtefilterung und konsistente Aktualisierung implementieren. Erweiterungsverträge für Wiki und Anforderungen erhalten; diese bleiben notwendige V1-Folgearbeit.

**Schnittstellen und Arbeitsbereich:** Geplante Zuständigkeit: Suchmodul mit eigenen Projektionstabellen und Indizes. Quellen liefern berechtigungstrennbare Segmente über Verträge; Such-SQL liest keine fremden internen Tabellen. Rechteprüfung vor Ranking, Zählung und Seitenauswahl.

**Abnahmekriterien:** Verborgene Inhalte beeinflussen weder Treffer noch Rang/Anzahl/Vorschläge. Neuanlage, Änderung, Rechteentzug, Rückabwicklung und Projektionserneuerung geprüft; veraltete Projektionen geben nichts unzulässig frei. Getrennte KI prüft insbesondere Negativfälle und Datenhoheit. Messung erst in TASK-031.

**Nächster Schritt:** Suchfelder/-filter und messbaren Abschnittsumfang annehmen; nach Beauftragung reservieren.

## TASK-030 Durchgängige Oberfläche und minimale Administration integrieren

**Status:** BLOCKIERT · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-PROD-002, REQ-TKT-001, REQ-TAG-001, REQ-SRCH-001, REQ-I18N-001, REQ-UX-001. **Abhängigkeiten:** TASK-023 und TASK-029 einschließlich der angenommenen vorgelagerten API-Verträge. **Arbeitsstand:** nur vorgemerkt; kein Branch reserviert, keine Anwendung erstellt.

**Umfang:** Administrative lokale Kontoanlage, Anmeldung mit Pflichtwechsel, Projekte, Ticketanlage/-bearbeitung, Tags und Suche durchgängig verbinden. Minimale Gruppen-/Mitgliedschafts-/Rechte-/Projektkonfiguration für die Vorführung bedienbar machen. Fachregeln bleiben serverseitig.

**Schnittstellen und Arbeitsbereich:** Geplante Zuständigkeit: Frontend-Fachansichten und Browserprüfungen. Backendänderungsbedarf als koordinierte Vertragskorrektur übergeben; keine unabhängigen API-Sonderwege. Keine echten Kundendaten in Beispielen.

**Abnahmekriterien:** Administrative Kontoanlage und verpflichtender Erstwechsel sind bedienbar; direkter Fachzugriff vor dem Wechsel wird auch serverseitig abgewiesen. Zwei unterschiedlich berechtigte Benutzer durchlaufen den Ablauf in DE/EN. Ablaufende, verlorene Rechte, Konflikte und ungespeicherte Eingaben entsprechen dem ausgewählten UX-Vertrag. Direkte API-Negativfälle bleiben geschützt. Getrennte KI prüft Ablauf und sichtbare Grenzen; echte Browserprüfung dokumentiert.

**Nächster Schritt:** Passende Backend-/UI-Ergebnisse und Demoablauf annehmen; nach Beauftragung reservieren.

## TASK-031 Docker-Vorführung und Nachweise des ersten Abschnitts liefern

**Status:** BLOCKIERT · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-17

**Bezug:** REQ-OPS-001, REQ-OPS-005, REQ-OPS-006, REQ-PERF-001, REQ-PERF-003, REQ-DEV-004. **Abhängigkeiten:** TASK-030 und angenommene Backendgrundlage; Betriebs-/Messprofil und Implementierungsumfang entschieden. **Arbeitsstand:** nur vorgemerkt; kein Branch reserviert, keine Anwendung erstellt.

**Umfang:** Reproduzierbares Anwendungsimage mit Oberfläche, PostgreSQL-Anbindung, Startanleitung und synthetischen Demodaten liefern. Durchgängige Funktions-/Rechte-/Konfliktnachweise und begrenzte Suchmessung des tatsächlich vorhandenen Umfangs durchführen.

**Schnittstellen und Arbeitsbereich:** Geplante Zuständigkeit: Container-/Buildintegration, Beispielkonfiguration ohne Geheimnisse, Demonstrations- und Messprotokoll. Runtime-/Secrets-/TLS-/Persistenzgrenzen aus Betriebsvertrag übernehmen. Gemeinsame CI-Änderungen koordiniert integrieren.

**Abnahmekriterien:** Frischer Start und Neustart erhalten Daten und feste Sitzungsfristen; keine Standardkennwörter im Image. Reproduzierbare Nachweise zu den zehn Start-Szenarien und dokumentierte 15-Nutzer-Suchmessung, mit gewähltem Profil und verbleibenden Grenzen. Getrennte KI prüft Ergebnis; Holger nimmt fachlich ab. Keine verkaufbare V1, RPO/RTO-Erfüllung oder Überlegenheit gegenüber Wettbewerbern ohne eigenen Nachweis behaupten.

**Nächster Schritt:** Betriebs-/Messprofil und Vorführung freigeben; nach Beauftragung reservieren. Spätere V1-Aufgaben erst anschließend mit unverändertem bestätigtem Umfang konkretisieren.

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

## TASK-041 Befunde F1 und F2 der Nachprüfung von TASK-040 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261020-adminfix-01 · **Aktualisiert:** 2026-10-20

**Bezug:** REQ-ACL-001, REQ-ACL-003, REQ-AUTH-001, REQ-SEC-001. **Abhängigkeiten:** TASK-040 ist abgenommen und integriert; die unabhängige Codex Nachprüfung [docs/REVIEW_TASK-040_PR-101.md](docs/REVIEW_TASK-040_PR-101.md) (PR #103) liegt auf main. Holger hat am 20. Oktober 2026 im Projektgespräch auf den Hinweis, dass die beiden Befunde einen eigenen Auftrag brauchen, mit „ja mache dafür einen eigenen auftrag“ geantwortet. Das ist der Auftrag für den unten beschriebenen Umfang; TASK-027 bis TASK-031 bleiben blockiert. Codex prüft das Ergebnis danach unabhängig nach DEC-040. **Arbeitsstand:** Branch claude/project-thread-r4dri5, Ergebnis PR mit DEC-066.

**Umfang:** (F1, P2) Gleichzeitige Verwaltungsaufrufe zweier Berechtigter, die jeweils das Konto des anderen verwalten (Passwort zurücksetzen, deaktivieren, reaktivieren), dürfen nicht mit einem Deadlock und HTTP 500 enden. Eine gemeinsame Sperrreihenfolge oder vorgeschaltete Serialisierung für Abläufe festlegen, die neben dem Anfragenden weitere Identitäten sperren; Sitzungsdauer und Anmelde- sowie Entzugswege dabei berücksichtigen. Die Rechteprüfung bleibt in der Transaktion der Änderung wirksam. (F2, P3) Alle Verbraucher des Verwaltungsports (`AdministrationRequests`, `SessionController`, `AppearanceController`, `AppearanceService`) zählen die Kandidaten streng wie `AccountService` und `JdbcPermissions`, sodass ein zusätzlicher Primary Adapter nicht still gewinnt; bei Mehrdeutigkeit verweigern und keine Verwaltungsrechte in der Sitzungsantwort melden. Nicht Teil: Prüfung beim Commit statt beim Sperren, Endpunkte zur Gruppen- und Rechtepflege, P2 bis P6 aus TASK-025, F1 bis F3 aus PR #96.

**Schnittstellen und Arbeitsbereich:** Identitätsmodul (Kontoverwaltung, sperrende Identitätsprüfung, Verwaltungsanfragen, Sitzungsantwort), Erscheinungsbild (Controller, Dienst), gegebenenfalls Anwendungseinstieg `taskmanager`, Tests, backend/PERMISSIONS.md oder backend/README.md, DECISIONS.md. Keine Migration erwartet; falls doch, nächste freie Nummer nach den von TASK-026 belegten.

**Abnahmekriterien:** Der Kreuzfall aus F1 läuft als Regressionstest mit echter PostgreSQL Wartebeziehung: beide Aufrufe enden ohne 500 und ohne Deadlock, das Ergebnis ist fachlich eindeutig. Bestehende Konkurrenz-, Entzugs- und Schutztests bleiben grün. Für F2 Tests mit keinem, genau einem, mehreren ohne und mehreren mit Primary Adapter an jedem Verbraucher; nur genau ein Adapter erlaubt. Alle Backendtests und CI auf PostgreSQL 18.6 grün. Getrennte KI prüft das Ergebnis; ihre Prüfung ist angeboten, aber nicht Voraussetzung der Abnahme durch Holger.

**Ergebnis:** DEC-066. (F1) Deaktivieren, Reaktivieren, Passwortzurücksetzen und die Änderung der Sitzungsdauer nehmen vor der Rechteprüfung eine gemeinsame transaktionsgebundene Advisory Sperre (`AccountRepository.serializeAdministration`); die Sitzungsdauer direkt nach ihrer Einstellungszeile, das Deaktivieren vor der Schranke der letzten Verwaltung, in der die bisherige Deaktivierungssperre aufgeht. Zwei Berechtigte, die gleichzeitig das Konto des anderen verwalten, laufen dadurch nacheinander: der erste gewinnt, der zweite sieht dessen Ergebnis (zum Beispiel 403, weil seine eigene Anmeldung durch das Zurücksetzen endete) statt Deadlock und 500. Endpunkte für Reaktivieren, Zurücksetzen und Sitzungsdauer prüfen vorher eine Momentaufnahme des Rechts, damit Anfragen ohne Recht die Serialisierung nicht belegen. (F2) `AdministrationRights.unique` zählt die Kandidaten; alle fünf Verwendungen in `AdministrationRequests`, `SessionController`, `AppearanceController` und `AppearanceService` nutzen es statt `getIfUnique`. Kein Vertrag, keine Migration, keine Oberflächenänderung.

**Eigenprüfung am 2026-10-20:** Backend `verify` mit Temurin 25.0.4.1+1 gegen eine lokale PostgreSQL 16.15 (Docker war in dieser Umgebung nicht verfügbar, daher Entwicklerweg über `-Dtaskmanager.test.jdbc-url`; maßgeblich ist der CI-Lauf auf 18.6): 138 Tests, 0 Fehler, 0 übersprungen. Neu: drei Konkurrenztests in `AdministrationRightsApiTest` (gegenseitiges Zurücksetzen; Deaktivieren und Reaktivieren gegen Zurücksetzen; Sitzungsdauer gegen Zurücksetzen des Anfragenden), die die Serialisierung festhalten, belegen, dass beide Anfragen dort ohne Identitätssperre warten, und danach eindeutige Ergebnisse ohne 500 prüfen; fünf Tests in `AdministrationRightsWiringTest` (kein, ein, zwei ohne und zwei mit Primary Adapter sowie Quelltextprüfung, dass kein Verbraucher `getIfUnique` für den Port nutzt). Gegenprobe: Mit wirkungsloser Serialisierung und mit `getIfUnique` in `unique` scheitern genau diese 5 neuen Prüfungen (3 + 2), danach Originale wiederhergestellt. Repository Validator, Übersichtsprüfung, Python Tests und git diff --check erfolgreich. Der ursprüngliche Prüfversuch von Codex wurde nicht unverändert wiederholt: Er erwartet beide Anfragen wartend an der Sitzungssperre, was die Serialisierung gerade verhindert.

**Nächster Schritt:** Codex prüft unabhängig nach DEC-040; Holger nimmt fachlich ab.
