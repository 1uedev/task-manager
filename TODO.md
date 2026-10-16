# Zentrale offene Aufgaben

Stand 16. Oktober 2026 · Planung und Repository-Infrastruktur ohne Produktimplementierung

Diese Datei enthält den gemeinsamen Arbeitsstand aller beauftragten KI-Sitzungen. Status und Zuständigkeit werden gemäß [AGENTS.md](AGENTS.md) koordiniert. Eine Aufgabe kann mehrere Anforderungen konkretisieren. Ein offener Entwurf gilt nicht als fachlich abgenommen.

Repository: [1uedev/task-manager](https://github.com/1uedev/task-manager). Maßgeblich ist diese Datei auf main. TASK-013 richtet die Arbeitsumgebung ein. Die übergreifende Koordination liegt bei Holger oder einer ausdrücklich von ihm benannten Sitzung. Aufgaben werden über einen reservierenden Pull Request vergeben; siehe docs/AI_WORKFLOW.md. GitHub Issues sind Diskussions- und Einstiegspunkte, keine zweite Aufgabenquelle.

Planungsentwürfe aus TASK-002 bis TASK-011 dürfen auf der vorhandenen Fassung von TASK-001 vorbereitet werden. Die noch offene fachliche Abnahme von TASK-001 verhindert solche Entwürfe nicht. Abhängige Festlegungen und die Produktimplementierung werden dadurch nicht vorweggenommen.

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

**Nächster Schritt:** Holger bewertet restliche Werkzeugdetails unter OPEN-001, Modulstruktur unter OPEN-002 und API-Grundvertrag unter OPEN-013. Fachentwürfe und Funktionsinventar liegen vor. Gegenseitige KI-Prüfung mit konkreter Zuordnung unter OPEN-017 organisieren. Produktimplementierung bleibt gesondert zu beauftragen.

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

**Nächster Schritt:** Backend, anfänglicher Review-Weg und nächste Fachpriorität sind durch DEC-039 bis DEC-041 festgelegt. Als Nächstes TypeScript, Modulstruktur, API sowie die fälligen Rechte-/Betriebs-/KI-Details paketweise prüfen. Erst nach den erforderlichen Entscheidungen einen gesonderten Implementierungsauftrag abgrenzen.

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

**Nächster Schritt:** Holger prüft die korrekte Übernahme; weitere Architekturentscheidungen unter OPEN-001, OPEN-002 und OPEN-013 sowie Detailverfahren unter OPEN-017/OPEN-018 vorbereiten. Separate Implementierungsfreigabe bleibt erforderlich.
