# Zentrale abgeschlossene Aufgaben

Stand 18. Oktober 2026

Diese Datei enthält dauerhaft nachvollziehbare Ergebnisse abgeschlossener und gemäß ihren Kriterien geprüfter Aufgaben. Der Status offener Arbeit steht in [TODO.md](TODO.md). Fachliche Abnahme ist von technischer Eigenprüfung zu unterscheiden.

## Bisheriger Stand

Bis zum 18. Oktober 2026 lagen keine nach dem hier festgelegten Verfahren abgeschlossenen Aufgaben vor; TASK-034 und TASK-035 waren die ersten abgeschlossenen Einträge. TASK-022 ist als erste Produktimplementierung (Backendgrundlage ohne Fachfunktion) abgeschlossen, TASK-023 als Frontend-Grundfläche ohne Fachfunktion, TASK-024 als Identitätsmodul (lokale Anmeldung und feste Sitzungsfristen, ohne Anmeldeseite), TASK-025 als Berechtigungsmodul (Gruppen-, Projekt- und Feldrechte ohne HTTP und ohne echten Identitätsadapter) mit der Nachbesserung TASK-038, der echte Adapter zwischen Identität und Berechtigungen als TASK-039, das Projektmodul als TASK-026, der Ticketkern als TASK-027, die Serialisierung der Verwaltung als TASK-041, der Tag-Kern als TASK-028 und die Tagänderungen in der Tickethistorie als TASK-042; TASK-037 schließt eine Restlücke der Sitzungsfristen aus TASK-024. Nachtrag vom 28. Oktober 2026: Holger hat alle bis dahin zur Prüfung stehenden Aufgaben abgenommen, also die Planungs- und Einrichtungsaufgaben TASK-001 bis TASK-021, TASK-032 und TASK-033 sowie TASK-044 bis TASK-056. Die Planungsaufgaben stehen in Nummernfolge vor TASK-034, TASK-044 bis TASK-056 vor TASK-043. Nachtrag vom 29. Oktober 2026: Holger hat den Helpdesk Eingang TASK-057 mit den Korrekturen TASK-059 bis TASK-061 abgenommen (DEC-090); diese Einträge stehen vor TASK-056. Nachtrag vom 30. Oktober 2026: Holger hat die Antworten über SMTP TASK-058 mit den Korrekturen TASK-062 bis TASK-064 abgenommen; diese Einträge stehen vor TASK-061. Nachtrag vom 1. November 2026: Holger hat die Servicefristen TASK-068, die Eskalationen TASK-070 und deren Korrektur TASK-071 abgenommen; diese Einträge stehen vor TASK-066. Die Abnahme der Planungsaufgaben macht als Vorschlag gekennzeichnete Anforderungen und offene Fragen nicht zu Entscheidungen; maßgeblich bleiben REQUIREMENTS.md und DECISIONS.md.

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

## TASK-001 Erste zentrale Spezifikation erstellen und prüfen

**GitHub:** [Issue #1](https://github.com/1uedev/task-manager/issues/1)

**Bezug:** REQ-PROD-003, REQ-DEV-001, REQ-DEV-002, REQ-DEV-004. **Abhängigkeiten:** keine. **Arbeitsstand:** Dokumentfassung im Repository; ursprüngliche Erstellung am 2026-10-12. **Verantwortlich:** Codex-Hauptsitzung-2026-10-12. **Abgeschlossen:** 2026-10-28.

**Umfang:** Gesprächsentscheidungen in Anforderungen, Abnahmekriterien, offene Fragen, Roadmap und gemeinsame Arbeitsregeln überführen.

**Abnahmekriterien:** Bestätigte Vorgaben und Vorschläge sind getrennt. IDs und Verweise sind konsistent. TODO und DONE unterscheiden Entwurf und Abnahme. Es wurde keine Anwendung implementiert. Holger hat die erste Dokumentfassung fachlich geprüft beziehungsweise Änderungswünsche benannt.

**Ergebnis:** README.md, REQUIREMENTS.md, DECISIONS.md, ROADMAP.md, AGENTS.md, CLAUDE.md, TODO.md und DONE.md liegen als erste Fassung vor. Fachliche Abnahme offen.

**Eigenprüfung am 2026-10-12:** 86 eindeutige Anforderungs-IDs mit Status und Abnahmekriterien, 24 Entscheidungs-IDs, 21 offene Fragen und 12 Aufgaben geprüft. Anforderungs-, Aufgaben- und Fragenverweise sowie lokale Dokumentlinks sind auf gültige Ziele geprüft; Pflichtangaben der Aufgaben sind vorhanden. Der Ordner enthält ausschließlich die acht Markdown-Dokumente. Diese Strukturprüfung ist kein unabhängiges Review und kein Nachweis implementierter Produktfunktionen.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-12“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #1](https://github.com/1uedev/task-manager/issues/1); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-002 Umfang und spätere Funktionen vervollständigen

**GitHub:** [Issue #2](https://github.com/1uedev/task-manager/issues/2)

**Bezug:** REQ-PROD-002, REQ-PROD-003, REQ-EXT-001, REQ-TIME-003. **Abhängigkeiten:** Entwurf TASK-001 vorhanden; Priorisierung nach fachlicher Rückmeldung. **Arbeitsstand:** task/task-002-codex-inventory; Funktionsinventar und priorisierte Roadmap als Entwurf vorhanden. **Verantwortlich:** codex-20261016-inventory-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** V1-Vorschläge priorisieren, vollständiges Funktionsinventar der angestrebten Vergleichbarkeit erstellen und spätere Module abgrenzen. OPEN-020 und OPEN-021 einbeziehen.

**Abnahmekriterien:** Jeder Bereich hat Zweck, Mindestumfang, Abhängigkeit, Release-Status und Abnahmebeispiel. Bestätigte V1-Funktionen werden nicht ohne Entscheidung verschoben. Termine und Preise werden ohne Budget- und Kapazitätsbasis nicht zugesagt.

**Ergebnis:** [Funktionsinventar](docs/FUNCTION_INVENTORY.md) mit V1-Kern, Priorisierung aller V-Ergänzungen, späteren Funktionsbereichen, Mindestumfang, Abhängigkeiten, Abnahmebeispielen und KI-Nutzenbewertung. Roadmap verknüpft und konsolidiert. Bestätigte V1-Funktionen und frühe Ergänzungen bleiben erhalten; keine Preis-, Termin- oder Gleichheitszusage.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 86 REQ-IDs sind über Inventar und Bereichsverweise zugeordnet; V-Anforderungen auf Priorisierung geprüft. Herstellerseiten zur Bereichsabdeckung gelesen; eigene Mindestumfänge von Herstellerangaben getrennt. Keine Anwendungstests und keine unabhängige fachliche oder technische Abnahme.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-041 bestätigt die Priorität von Kanban, Gantt und einfachen Dashboards; die frühen Ergänzungen 2FA und Redmine-Import bleiben eingeplant. Übrige Prioritäten und Mindestumfänge bleiben Vorschläge.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-16“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #2](https://github.com/1uedev/task-manager/issues/2); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-003 Architekturentscheidungen vorbereiten

**GitHub:** [Issue #3](https://github.com/1uedev/task-manager/issues/3)

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-ORG-002, REQ-DATA-001, REQ-DATA-002, REQ-API-001. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004 und TASK-005. **Arbeitsstand:** task/task-003-codex-architecture, separates Arbeitsverzeichnis; Architekturentwurf vorhanden. **Verantwortlich:** codex-20261013-architecture-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-001, OPEN-002 und OPEN-013 bearbeiten. Go und Java bewerten, Modulgrenzen, Unternehmenskontext und API-Grundvertrag entwerfen.

**Abnahmekriterien:** Ein begründeter Entscheidungsvorschlag benennt Betrieb, Prüfbarkeit, Aufwand und Grenzen. PostgreSQL bleibt alleiniger V1-Datenbankumfang. Kein Technologie-Prototyp oder Anwendungscode ohne zusätzliche Beauftragung.

**Ergebnis:** [Architekturentwurf](docs/ARCHITECTURE.md) mit Go/Java-Vergleich, begründeter Empfehlung, Modul- und Datenhoheit, späteren Unternehmensgrenzen, API-Grundvertrag, Hintergrundaufträgen und Docker-Betriebsbild. PostgreSQL bleibt die einzige unterstützte Datenbank. Vorschläge für Leistung und Hardware sind keine Messergebnisse. Die Backendwahl war im ursprünglichen Entwurf offen; der nachstehende Nachtrag dokumentiert die bestätigte Auswahl. Detailverträge bleiben offen.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Rechte- und Architekturentwurf auf konsistente Organisationsgrenzen, Feldrechte, Zugriffskontext und spätere 2FA geprüft. Quellen, Aufgabenverweise und Trennung von Vorschlag und Entscheidung geprüft. Keine Produkt-, Last- oder Integrationstests; keine unabhängige technische Prüfung.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-039 bestätigt Java mit Spring Boot; Versionen, TypeScript und Modulstruktur bleiben offen. DEC-040 legt zunächst gegenseitige KI-Reviews fest; zusätzliche menschliche Prüfung bleibt offen.

**Nachtrag aus TASK-017 am 2026-10-16:** DEC-042 bis DEC-044 bestätigen React/TypeScript, modularen Monolithen und HTTP/JSON im REST-Stil mit OpenAPI. Die frühere offene Grundauswahl ist damit entschieden; konkrete Modul- und API-Verträge bleiben prüfpflichtig.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-13“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #3](https://github.com/1uedev/task-manager/issues/3); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-004 Identität und Berechtigungen spezifizieren

**GitHub:** [Issue #4](https://github.com/1uedev/task-manager/issues/4)

**Bezug:** REQ-ACL-001 bis REQ-ACL-005, REQ-AUTH-001 bis REQ-AUTH-004, REQ-PORTAL-003. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-004-claude-rights, separates Arbeitsverzeichnis; koordinierte Übergabe an Codex am 2026-10-13. **Verantwortlich:** codex-20261013-rights-fallback-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-004, OPEN-005 und OPEN-006 beantworten. Rechtekonflikte, externe Sicht, Feldrechte, LDAP-Lebenszyklus und 2FA-Termin festlegen.

**Abnahmekriterien:** Eine Rechte-Matrix und positive sowie negative Beispiele decken API, Suche, Exporte, E-Mail, Dateien und KI ab. Authentifizierung und fachliche Berechtigung bleiben getrennt. Fachliche Entscheidungen sind von technischen Vorschlägen unterscheidbar.

**Übergabe:** Die reservierte Claude-Sitzung konnte wegen eines ungültigen OAuth-Zugangs keinen Entwurf erzeugen. Die koordinierende Sitzung weist die Entwurfserstellung ausdrücklich Codex zu. Kein Claude-Review wird behauptet; ein späterer Einsatz erfordert erneute Anmeldung.

**Ergebnis:** [Rechteentwurf](docs/PERMISSIONS.md) mit Gruppenmatrix, Auswertungsregeln, Datenwegen, LDAP-Lebenszyklus und 16 fachlichen Prüfszenarien. Holgers drei Entscheidungen sind als DEC-027 bis DEC-029 in Anforderungen, Entscheidungen und Roadmap übernommen. Die weiteren Umsetzungsvorschläge bleiben offen.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Dokumente auf Widersprüche zur Organisationssicht, Feldverbotsregel und 2FA-Einordnung geprüft. Keine Produkt- oder Sicherheitstests ausgeführt, da keine Anwendung implementiert ist. Keine unabhängige Prüfung und kein Claude-Review.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-040 bestätigt gegenseitige KI-Reviews auch für Rechte und Anmeldung. Zusätzliche menschliche Prüfung bleibt offen; die fachliche Gesamtprüfung ist damit nicht erfolgt.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-13“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #4](https://github.com/1uedev/task-manager/issues/4); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-005 Fachmodell und Projektkonfiguration konkretisieren

**GitHub:** [Issue #5](https://github.com/1uedev/task-manager/issues/5)

**Bezug:** REQ-ORG-003, REQ-TKT-001 bis REQ-TKT-005, REQ-TIME-001, REQ-TIME-002, REQ-REQ-001 bis REQ-REQ-003. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004. **Arbeitsstand:** task/task-005-codex-domain; Fachmodellentwurf vorhanden. **Verantwortlich:** codex-20261013-domain-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-007 und OPEN-021 bearbeiten. Standardfelder, Beziehungen, Status, Regeln, Zeitkorrekturen und Anforderungsfreigaben beschreiben.

**Abnahmekriterien:** Änderungen bestehender Projektregeln sind durchgespielt. Verborgene Pflichtfelder, Archivierung und Änderungen nach Freigabe haben eindeutige Regeln. API und Oberfläche verwenden dieselbe Fachlogik.

**Ergebnis:** [Fachmodellentwurf](docs/DOMAIN_MODEL.md) mit Service- und Entwicklungsprojekt, Standardfeldern, Konfigurationsänderungen, verborgenen Pflichtfeldern, Beziehungen, Archivierung, Zeitkorrekturen und 16 fachlichen Prüffällen. Holgers Freigaberegel für neue Anforderungsrevisionen ist als DEC-030 in Anforderungen und Roadmap übernommen. Weitere Detailregeln bleiben Vorschläge.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Objekt- und Rechteabgrenzung, Zeitkorrekturbeispiel und Trennung von neuer Revision und späterer Baseline geprüft. Keine Anwendungstests und kein unabhängiges technisches Review.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-13“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #5](https://github.com/1uedev/task-manager/issues/5); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-006 Helpdesk Portal und SLA Regeln ausarbeiten

**GitHub:** [Issue #6](https://github.com/1uedev/task-manager/issues/6)

**Bezug:** REQ-HD-001 bis REQ-HD-005, REQ-PORTAL-001 bis REQ-PORTAL-003; Präzisierung des Abnahmevorschlags in REQ-TKT-005. **Abhängigkeiten:** TASK-001; Rechte aus TASK-004. **Arbeitsstand:** task/task-006-codex-helpdesk; Helpdesk- und SLA-Entwurf vorhanden. **Verantwortlich:** codex-20261013-helpdesk-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-008 und OPEN-009 bearbeiten. Nachrichtenwege, Postfächer, Kundenidentität und Servicekalender festlegen.

**Abnahmekriterien:** Wochenenden, Feiertage, Zeitumstellung, Pausen, Wiedereröffnung und Prioritätswechsel sind anhand konkreter Zeitpunkte berechenbar. Doppelte Mails und Eskalationsversuche führen nicht zu unkontrollierten Doppelaktionen.

**Ergebnis:** [Helpdesk- und SLA-Entwurf](docs/HELPDESK_SLA.md) mit Mail-/Portalabläufen, Versandzuständen, Regel- und Kalenderzuordnung, 14 berechenbaren SLA-Fällen und zwölf weiteren Fachprüffällen. DEC-031 und DEC-032 dokumentieren Holgers Auswahl zur Prüfwarteschlange und Kundenpause. Anforderungen und Roadmap sind nachgeführt; andere Details bleiben Vorschläge.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. 17 Termin-/Offsetberechnungen mit separater Python-datetime/zoneinfo-Hilfsrechnung geprüft. Abgleich mit Rechte- und Fachmodellentwurf durchgeführt. Keine implementierte SLA-Engine, keine Mailintegration und keine Anwendungstests; kein unabhängiges Review.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-13“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #6](https://github.com/1uedev/task-manager/issues/6); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-007 Wiki Sprachen und Unternehmensauftritt spezifizieren

**GitHub:** [Issue #7](https://github.com/1uedev/task-manager/issues/7)

**Bezug:** REQ-DOC-001 bis REQ-DOC-004, REQ-I18N-001 bis REQ-I18N-003, REQ-BRAND-001, REQ-BRAND-002, REQ-UX-001. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-007-codex-wiki, separates Arbeitsverzeichnis; Wiki-, Sprach- und Brandingentwurf vorhanden. **Verantwortlich:** codex-20261015-wiki-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-011 und OPEN-015 bearbeiten. Editor, Dokumentformat, Sprachfassungen, Konfliktbehandlung und Vorlagen entwerfen.

**Abnahmekriterien:** Einsprachige Seiten sind ohne Übersetzungszwang nutzbar. Wiederholte Bearbeitung erhält Inhalte. Sprache, Zeitzone und Formatierung sind abgegrenzt. Anwendung, Portal, Anmeldung und E-Mail sind beim Branding berücksichtigt.

**Ergebnis:** [Wiki-, Sprach- und Brandingentwurf](docs/WIKI_I18N_BRANDING.md) mit bestätigter Entwurfs-/Veröffentlichungstrennung (DEC-033), Sprachfassungen, Konfliktablauf, zwei Editor-Kandidaten, synthetischer Beispielseite, DE/EN-Mailvorlage und 16 fachlichen Prüffällen. Anforderungen, Roadmap und offene Entscheidungen sind nachgeführt. Die weiteren Detailregeln bleiben Vorschläge.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Veröffentlichung, Rechte, Sprachrückfall und Vorlagen mit den bestehenden Entwürfen abgeglichen; Editor-Grundlagen anhand zweier Primärquellen geprüft. Keine Editorimplementierung, Browser-, Mailclient- oder Anwendungstests und kein unabhängiges Review.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-15“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #7](https://github.com/1uedev/task-manager/issues/7); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-008 KI Umfang und Freigabeablauf festlegen

**GitHub:** [Issue #8](https://github.com/1uedev/task-manager/issues/8)

**Bezug:** REQ-AI-001 bis REQ-AI-009, REQ-PERF-004; bestätigte V1-Ergänzung in REQ-PROD-002. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004. **Arbeitsstand:** task/task-008-codex-ai, separates Arbeitsverzeichnis; KI-Anwendungsfallentwurf vorhanden. **Verantwortlich:** codex-20261015-ai-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-003 und OPEN-019 entscheiden. Skills, Provider-Konfiguration, erste Anwendungsfälle und Datenwege konkretisieren.

**Abnahmekriterien:** Alle Fachbereiche besitzen eine Nutzenbewertung. Nur Administratoren konfigurieren Modelle und Skills. Jeder ändernde Anwendungsfall besitzt eine sichtbare Bestätigung, Konfliktbehandlung und erneute Rechteprüfung. Lokaler Betrieb wechselt nicht still in die Cloud.

**Ergebnis:** [KI-Anwendungsfallentwurf](docs/AI_USE_CASES.md) mit vier bestätigten V1-Funktionen für interne berechtigte Benutzer (DEC-034, DEC-035), Kontext- und Empfängergrenzen, versionierten Markdown-Skills, Providervertrag, Bestätigung, Kosten-/Ausfallvorschlägen und 16 fachlichen Prüffällen. Weitere Fachbereiche erhalten eine Nutzenbewertung. Anforderungen und Roadmap sind nachgeführt.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Datenwege, Bestätigung, Anforderungsrevision, Wiki-Veröffentlichung und SMTP-Grenzen mit den bestehenden Entwürfen abgeglichen. Keine Modellaufrufe, Integrationstests, Qualitätsmessungen oder Produktimplementierung; kein unabhängiges technisches Review.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-15“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #8](https://github.com/1uedev/task-manager/issues/8); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-009 Git und spätere Migration eingrenzen

**GitHub:** [Issue #9](https://github.com/1uedev/task-manager/issues/9)

**Bezug:** REQ-GIT-001 bis REQ-GIT-003, REQ-MIG-001, REQ-MIG-002, REQ-API-002. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-009-codex-git, separates Arbeitsverzeichnis; Git- und Migrationsentwurf vorhanden. **Verantwortlich:** codex-20261015-git-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-010 und den Migrationsteil von OPEN-016 bearbeiten. Git-Varianten und Objektarten wählen, Importquellen priorisieren.

**Abnahmekriterien:** Zuordnung und Anzeige sind klar von späteren Git-Schreibaktionen getrennt. Quellenzugriff, Metadatenrechte und Aktualisierung sind definiert. Die erste Version verspricht keinen Import; die anschließende Migration besitzt einen begrenzten, überprüfbaren Umfang.

**Ergebnis:** [Git- und Migrationsentwurf](docs/GIT_MIGRATION.md) mit drei vorgeschlagenen Objektarten, Benutzer-/Quellrechten, Aktualisierung und Fehlerfällen sowie begrenztem Redmine-Import, Zuordnung, Probelauf und Umschaltung. Acht Git- und acht Migrationsprüffälle sind beschrieben. DEC-036 und DEC-037 dokumentieren Holgers Auswahl; Anforderungen und Roadmap sind nachgeführt.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Rechte, Wiki-Veröffentlichung und KI-Umfang mit bestehenden Entwürfen abgeglichen; technische Grundlagen anhand offizieller GitHub-, GitLab- und Redmine-Dokumentation geprüft. Keine Anbieteranbindung, Extraktion, Migration oder Anwendungstests ausgeführt; kein unabhängiges Review.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-15“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #9](https://github.com/1uedev/task-manager/issues/9); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-010 Betrieb Sicherheit und Leistung planen

**GitHub:** [Issue #10](https://github.com/1uedev/task-manager/issues/10)

**Bezug:** REQ-OPS-001 bis REQ-OPS-006, REQ-SEC-001, REQ-PERF-001 bis REQ-PERF-004, REQ-DATA-003, REQ-DATA-004. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-003. **Arbeitsstand:** task/task-010-codex-ops, separates Arbeitsverzeichnis; Betriebs- und Lastentwurf vorhanden. **Verantwortlich:** codex-20261015-ops-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-012, OPEN-014 und Aufbewahrung aus OPEN-016 bearbeiten. Docker-Betrieb, Persistenz, Internetbedarf, Sicherung, Updates, Risiken und Lastprofil beschreiben.

**Abnahmekriterien:** Ressourcenannahmen sind nachvollziehbar, ohne feste RAM-Grenze. Die SSD-Planung berücksichtigt Anhänge. Wiederanlauf, zulässiger Datenverlust und Leistungsziele sind vor Tests festgelegt. Vorschläge werden nicht als gemessene Systemanforderungen ausgegeben.

**Ergebnis:** [Betriebsentwurf](docs/OPERATIONS.md) mit Docker-Referenzinstallation, Persistenz, Netzwegen, gemeinsamen Sicherungspunkten, Wiederanlauf, Updates, Aufbewahrung, Fünfjahres-Speicherrechnung, Lastprofil und 16 vorgeschlagenen Prüffällen. DEC-038 dokumentiert Holgers RPO-/RTO-Ziele; Anforderungen und Roadmap sind nachgeführt. Hardware, Leistungswerte und Detailverfahren bleiben Vorschläge.

**Eigenprüfung am 2026-10-15:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Speicher-, Durchsatz- und Lastrechnungen separat mit Python geprüft; Docker-/PostgreSQL-Grundlagen anhand offizieller Dokumentation geprüft. Abgleich mit Mailversand, Wiki, Git, KI und Rechteentzug durchgeführt. Keine Containerinstallation, Sicherung, Wiederherstellung, Last- oder Sicherheitstests an einer Anwendung; kein unabhängiges technisches Review.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-15“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #10](https://github.com/1uedev/task-manager/issues/10); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-011 Gemeinsamen Entwicklungs und Review Ablauf beschließen

**GitHub:** [Issue #11](https://github.com/1uedev/task-manager/issues/11)

**Bezug:** REQ-DEV-001 bis REQ-DEV-005. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-011-codex-review; Entwicklungs- und Review-Vorschlag vorhanden. **Verantwortlich:** codex-20261016-review-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** OPEN-017 und OPEN-018 entscheiden. Koordination, getrennte Arbeitsstände, Aufgabenreservierung, PRs, technisches Review und fachliche Abnahme definieren.

**Abnahmekriterien:** Zwei gedachte gleichzeitige KI-Sitzungen können Aufgaben eindeutig übernehmen und übergeben. Eine veraltete TODO-Kopie überschreibt keinen aktuellen Stand. Fachliche Freigabe und technische Prüfung haben benannte Verantwortlichkeiten. Die technische GitHub-Einrichtung erfolgt separat in TASK-013. Weitergehende Review-Regeln werden hier geprüft.

**Ergebnis:** [Entwicklungs- und Review-Entwurf](docs/DEVELOPMENT_REVIEW.md) mit Rollen, risikogerechten Nachweisen, Commitbezug, acht Koordinations-/Unterbrechungsszenarien und klarer Trennung von Integration, technischer Prüfung und Fachabnahme. Bestehende AGENTS-Regeln bleiben gültig; zusätzliche Freigabeschwellen bleiben vorgeschlagen; den bestätigten anfänglichen Review-Weg ergänzt der nachstehende Nachtrag.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Acht Ablaufszenarien gedanklich geprüft. Zwei Fälle zusätzlich in isolierten temporären Git-Repositories ausgeführt: konkurrierende Eigentümer derselben Aufgabe erzeugten einen Textkonflikt; unabhängige Aufgaben ließen sich unter Erhalt beider Eigentümer zusammenführen. Das beweist keine atomare Sperre, verhindert kein bewusstes Zurückkopieren veralteter Inhalte und ersetzt weder reale parallele KI-Tests noch unabhängiges technisches Review.

**Nachtrag aus TASK-015 am 2026-10-16:** DEC-040 bestätigt zunächst gegenseitige KI-Reviews; menschliche technische Prüfung bleibt ausdrücklich offen. Die Antwort nimmt weder alle Review-Details noch den Gesamtentwurf ab.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-16“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #11](https://github.com/1uedev/task-manager/issues/11); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-012 Planungsstand zur Architekturfreigabe zusammenführen

**GitHub:** [Issue #12](https://github.com/1uedev/task-manager/issues/12)

**Bezug:** REQ-PROD-003, REQ-DEV-004, REQ-DEV-005. **Abhängigkeiten:** TASK-002 bis TASK-011 im für V1 und Architektur erforderlichen Umfang. **Arbeitsstand:** task/task-012-codex-readiness; zentrale Entscheidungsvorlage vorhanden, Architekturfreigabe weiterhin offen. **Verantwortlich:** codex-20261016-readiness-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Entscheidungen, Anforderungskatalog, Abnahmeszenarien und Roadmap konsistent zusammenführen.

**Abnahmekriterien:** Kritische Architekturfragen sind entschieden. Restfragen haben Zuständigkeit und Fälligkeit. Der fachlich freigegebene Umfang ist sichtbar; keine Implementierung wird automatisch ausgelöst.

**Ergebnis:** [Entscheidungsübersicht zur Architekturfreigabe](docs/ARCHITECTURE_READINESS.md) mit bestätigtem Kern, allen 21 offenen Entscheidungsbereichen, Zuständigkeiten/Fälligkeiten, Prüfungspaketen und Nachweisübersicht. Sie verknüpft sämtliche Fachentwürfe. Kritische Entscheidungen und fachliche Abnahme stehen weiterhin aus; die Abnahmekriterien dieser Aufgabe sind daher noch nicht vollständig erfüllt. Keine Programmierung ausgelöst.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf Regressionstests und git diff --check erfolgreich. Alle 21 OPEN-IDs in der Entscheidungstabelle erfasst; 86 Anforderungen im Inventar zugeordnet, 31 V-Anforderungen dort priorisiert. Bestätigte DEC-001 bis DEC-038 bleiben unverändert. Nur vorhandene Dokument-, Rechen- und Git-Beispielnachweise angeführt; keine Produktmessung oder unabhängige Abnahme behauptet.

**Nachtrag aus TASK-015 am 2026-10-16:** Die drei Antworten sind in der Entscheidungsvorlage und den Fachentwürfen nachgeführt. Weitere Architekturentscheidungen und die fachliche Gesamtabnahme stehen weiterhin aus.

**Nachtrag aus TASK-017 am 2026-10-16:** DEC-042 bis DEC-044 bestätigen die weitere Grundauswahl. REQ-ARCH-002 wechselt von V zu B; der aktuelle Katalog enthält dadurch 56 B- und 30 V-Anforderungen. Die oben dokumentierte frühere Eigenprüfung mit 31 V bleibt als historischer Prüfstand erhalten.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-16“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Issue #12](https://github.com/1uedev/task-manager/issues/12); kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-013 GitHub Arbeitsumgebung einrichten

**GitHub:** [Issue #13](https://github.com/1uedev/task-manager/issues/13)

**Bezug:** REQ-DEV-001, REQ-DEV-002, REQ-DEV-003, REQ-DEV-005. **Abhängigkeiten:** vorhandene Spezifikation und DEC-025. **Arbeitsstand:** Bootstrap-Commit 9b791e0 auf main; Verknüpfungen und Nachweise im Branch setup/task-013-task-links. **Verantwortlich:** Codex-Hauptsitzung-2026-10-13. **Abgeschlossen:** 2026-10-28.

**Umfang:** Das vom Auftraggeber angelegte Repository mit den zentralen Dokumenten, gemeinsamen KI-Regeln, Aufgaben-Issues, PR-Vorlage, Strukturprüfung und Schutz des Integrationsbranches einrichten. Die aktuelle Beauftragung umfasst die technische Einrichtung; die fachliche Abnahme der Produktspezifikation bleibt offen.

**Abnahmekriterien:** Dokumente sind auf GitHub erreichbar. Jede bestehende Aufgabe besitzt einen verknüpften Issue-Einstieg. Die lokale Strukturprüfung und GitHub CI laufen erfolgreich. main verlangt Pull Requests und den aktuellen erfolgreichen Prüfstatus; gewaltsames Überschreiben und Löschen sind gesperrt. Eine neue KI-Sitzung findet Start-, Reservierungs- und Übergabeanleitung. Einschränkungen der Review-Regeln sind dokumentiert.

**Ergebnis:** Dokumente, gemeinsame Regeln, Vorlagen und Prüfskripte sind veröffentlicht. Alle 13 Aufgaben haben Issues. main verlangt Pull Requests, einen aktuellen erfolgreichen repository-checks-Lauf von GitHub Actions und aufgelöste Review-Gespräche; Force-Push und Löschen sind auch für Administratoren gesperrt.

**Prüfungen:** Lokale Strukturprüfung und elf Regressionstests bestanden. [Erster GitHub-CI-Lauf](https://github.com/1uedev/task-manager/actions/runs/38366179158) erfolgreich. Repository-Einstellungen und Branchschutz über die GitHub-API zurückgelesen. Kein unabhängiges Review und keine fachliche Produktfreigabe behauptet.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** [Integrierter Ergebnis-PR #14](https://github.com/1uedev/task-manager/pull/14), einschließlich erfolgreicher PR-Prüfung. Abschluss PR #187.

## TASK-014 Regressionstest von freien Aufgaben entkoppeln

**Bezug:** REQ-DEV-002, REQ-DEV-003, REQ-DEV-005. **Abhängigkeiten:** TASK-013; Fehler durch Reservierung von TASK-012 sichtbar. **Arbeitsstand:** task/task-014-codex-contract-fix; korrigierter Regressionstest vorhanden. **Verantwortlich:** codex-20261016-contract-fix-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Vorhandenen Test test_active_task_requires_owner unabhängig vom aktuellen Aufgabenbestand machen. Produktcode und Validatorregeln bleiben außerhalb des Umfangs.

**Abnahmekriterien:** Test prüft eine aktive Aufgabe ohne Besitzer auch dann, wenn keine freie Aufgabe mehr existiert. Alle vorhandenen Tests bestehen; der negative Nachweis bleibt wirksam. Keine Pflichtprüfung wird deaktiviert.

**Ergebnis:** Der Test verwendet eine eigene synthetische Aufgabe und prüft sowohl IN ARBEIT als auch BEREIT ZUR PRÜFUNG auf fehlenden Besitzer. Ein gültiger Besitzer beseitigt den Fehler. Der produktive Validator und seine Regeln bleiben unverändert.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf Regressionstests und git diff --check erfolgreich. Zusätzlich eine temporäre Repository-Kopie ohne irgendeine OFFEN-Aufgabe validiert und alle elf Tests dort erfolgreich ausgeführt. Der vorherige Fehlschlag der TASK-012-Reservierung ist damit lokal reproduzierbar behoben; deren GitHub-Prüfung muss nach Integration erneut laufen.

**Nachtrag am 2026-10-16:** Fix über PR #39 integriert; Reservierung PR #37 auf aktuellen main aktualisiert, alle elf Tests und GitHub-Pflichtprüfung erfolgreich, anschließend regulär integriert. Keine Schutzregel umgangen.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-16“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-015 Bestätigte Backend-, Review- und Prioritätsentscheidungen eintragen

**Bezug:** REQ-ARCH-001, REQ-DEV-004, REQ-DEV-005, REQ-PROD-003, REQ-EXT-001. **Abhängigkeiten:** TASK-002, TASK-003, TASK-011, TASK-012 als Entwürfe und Holgers ausdrückliche Auswahl vom 16. Oktober 2026. **Arbeitsstand:** task/task-015-codex-decisions; bestätigte Auswahl und betroffene Planungsdokumente nachgeführt. **Verantwortlich:** codex-20261016-decisions-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Drei bestätigte Auswahlentscheidungen mit neuen DEC-IDs festhalten und betroffene Anforderungen, Architektur, Review-Regeln, Roadmap und KI-Einstiegsdateien konsistent nachführen. Keine weiteren Detailvorschläge als bestätigt behandeln.

**Abnahmekriterien:** Java/Spring Boot ist als Backend gesetzt; zunächst gegenseitige KI-Reviews bei weiterhin offener menschlicher Prüfung; Kanban, Gantt und einfache Dashboards nach den frühen Ergänzungen 2FA/Redmine-Import priorisiert. Versionen, TypeScript, Modulstruktur und weitere Details bleiben offen. Keine Programmierung oder Gesamtfreigabe abgeleitet.

**Ergebnis:** DEC-039 bis DEC-041 halten die ausdrücklichen Antworten fest. Zentrale Spezifikation, offene Entscheidungen, Architektur, Review-Dokumente, Funktionsinventar, Roadmap, Einstiegsregeln und betroffene Übergaben sind konsistent nachgeführt. TypeScript, Versionen, Modulstruktur, zusätzliche Sprintfunktionen und übrige Detailvorschläge bleiben offen. Keine Anwendung implementiert.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Entscheidungseinträge, Anforderungs-/Fragenverweise und verbliebene Formulierungen zur Backendwahl, menschlichen Prüfung und Roadmap-Priorität auf Widersprüche geprüft. Keine zusätzliche KI-Gegenprüfung oder menschliche technische Prüfung als erfolgt behauptet; reine Dokumentnachführung, keine Anwendungstests.

**Nachtrag aus TASK-017 am 2026-10-16:** Die damals noch offene Auswahl von TypeScript, modularem Monolithen und HTTP/JSON/OpenAPI ist inzwischen durch DEC-042 bis DEC-044 bestätigt. Die früheren Aussagen oben beschreiben den Stand bei Bearbeitung von TASK-015.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-16“ festgehalten.

**Review:** Keine getrennte KI-Prüfung vermerkt; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-016 Frontend-, Modul- und API-Verträge konkretisieren

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-API-001, REQ-API-002, REQ-DEV-004. **Abhängigkeiten:** TASK-003, TASK-004, TASK-005, TASK-008 und TASK-012 als Entwürfe; DEC-039 und DEC-040. **Arbeitsstand:** task/task-016-codex-contracts; Vertragsentwurf im separaten Arbeitsverzeichnis erstellt. **Verantwortlich:** codex-20261016-contracts-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Prüffähige Vorschläge zu TypeScript, Datenhoheit und Modulabhängigkeiten sowie API-Verträgen mit konkreten Abläufen und Fehlerfällen ausarbeiten. Offene Grundentscheidungen unter OPEN-001, OPEN-002 und OPEN-013 gezielt vorlegen; nur ausdrückliche Antworten als bestätigt eintragen. Keine Produktimplementierung.

**Abnahmekriterien:** Vorschläge enthalten Verantwortlichkeiten, Vertragsänderungen bei paralleler KI-Arbeit, Rechte- und Versionsprüfung, Wiederholbarkeit sowie prüfbare positive und negative Beispiele. Bestehende Fachentscheidungen bleiben erhalten. Ausgeführte Dokumentprüfungen und KI-Gegenprüfung werden von künftigen Anwendungstests getrennt. Offene Details und nicht bestätigte Entscheidungen bleiben sichtbar.

**Ergebnis:** [TECHNICAL_CONTRACTS.md](docs/TECHNICAL_CONTRACTS.md) konkretisiert TypeScript-Datenverträge, Modulordnung, Transaktionen, API-Abläufe, Feldrechte, Konflikte und Wiederholung mit 16 vorgeschlagenen Prüffällen. Entscheidungsübersicht, Architektur und Einstieg verweisen darauf. Die drei Grundentscheidungen wurden Holger vorgelegt; ohne Antwort bleiben sie offen. Keine neuen Entscheidungen, Anwendungsdateien oder zusätzlichen Pflichtdienste angelegt.

**KI-Gegenprüfung am 2026-10-16:** Eine getrennte Codex-Prüfsitzung contracts_review las den Entwurf in Commit 5bba988c15a918363069fab309887c9d75f16c0d gegen die bestehenden Fachverträge. Zwei P2-Befunde: Freigabe benötigte zusätzlich die technische Inhaltsversion; fachliche Einmaligkeit einer KI-Aktionskennung musste vom HTTP-Wiederholungsfenster getrennt werden. Korrektur in 484d85e74cdc5044079c78000346bda10fc77556, einschließlich C14/C15. Dieselbe separate Prüfsitzung bestätigte in gezielter Nachprüfung beide dokumentarischen Korrekturen ohne weiteren konkreten Widerspruch. Kein Claude-Review, Anwendungstest oder Sicherheitsnachweis behauptet.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Technische Primärquellen zu TypeScript, Spring Modulith, OpenAPI und HTTP geprüft. Die 16 Produktfälle sind erst beschrieben, nicht ausgeführt. Holgers fachliche Abnahme bleibt ausstehend.

**Nachtrag aus TASK-017 am 2026-10-16:** Holger hat die drei zuvor vorgelegten Grundentscheidungen bestätigt (DEC-042 bis DEC-044). Der Ergebnis- und Reviewbericht oben bleibt als historischer Stand erhalten. Detailregeln und die 16 Prüffälle sind damit nicht pauschal abgenommen.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-16“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in dem Absatz „KI-Gegenprüfung am 2026-10-16“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-017 Bestätigte Frontend-, Modul- und API-Auswahl eintragen

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-API-001. **Abhängigkeiten:** TASK-003, TASK-012, TASK-016 und Holgers ausdrückliche Auswahl vom 16. Oktober 2026. **Arbeitsstand:** task/task-017-codex-architecture-decisions; Entscheidungen im eigenen Arbeitsverzeichnis nachgeführt. **Verantwortlich:** codex-20261016-architecture-decisions-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** TypeScript, modularen Monolithen und HTTP/JSON im REST-Stil mit OpenAPI als bestätigte Grundentscheidungen erfassen; Anforderungen, Entwürfe, offene Fragen und KI-Einstiegsregeln konsistent nachführen.

**Abnahmekriterien:** Drei neue DEC-IDs erfassen ausschließlich die ausgewählten Grundentscheidungen. Sprach-/Werkzeugversionen, konkrete Modul-/Transaktionsverträge, Authentifizierung, Laufzeiten und weitere Detailregeln bleiben offen. Keine Gesamtfreigabe, Anwendung oder Implementierungsbeauftragung ableiten. Historische Prüfstände erhalten.

**Ergebnis:** DEC-042 bis DEC-044 bestätigen React/TypeScript, modularen Monolithen und HTTP/JSON im REST-Stil mit OpenAPI. Anforderungen sind auf Version 0.11 angehoben; REQ-ARCH-002 ist im Kern B. Offene Fragen, technische Entwürfe, Funktionsinventar, Roadmap, KI-Einstieg und frühere Übergaben sind nachgeführt. Details bleiben Vorschläge; keine Produktimplementierung oder Gesamtfreigabe abgeleitet.

**KI-Gegenprüfung am 2026-10-16:** Eine getrennte Codex-Sitzung architecture_decisions_review prüfte alle zehn geänderten Dokumente in 119d20622483b534f1d9a2a9dead5ab122814a08 gegenüber 3e42d6d2c81254271620b2baaeea562d512cafc7 auf korrekte Übernahme, Überbestätigung und Widersprüche. Keine konkreten Befunde; offene Details und historische Nachträge waren konsistent. Dieser nachträgliche Prüfbericht ist Eigenprotokoll der koordinierenden Sitzung und nicht Teil des genannten Review-Commits. Kein Claude-Review, Produkt- oder Sicherheitsnachweis behauptet.

**Eigenprüfung am 2026-10-16:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Gezielter Vergleich: alle 86 REQ-IDs erhalten, nur REQ-ARCH-002 wechselt V zu B; 56 B und 30 V. Alle früheren DEC-Einträge unverändert, genau DEC-042 bis DEC-044 ergänzt; 21 OPEN-IDs und DONE.md unverändert. Keine Anwendungstests.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-16“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in dem Absatz „KI-Gegenprüfung am 2026-10-16“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-018 Sitzungsfristen, V1-Tags und Suche spezifizieren

**Bezug:** REQ-AUTH-001, REQ-AUTH-005, REQ-ORG-003, REQ-TKT-004, REQ-TAG-001, REQ-SRCH-001, REQ-PERF-002, REQ-PERF-003, REQ-ARCH-002, REQ-API-001. **Abhängigkeiten:** TASK-003, TASK-004, TASK-005, TASK-010, TASK-016 und Holgers Ergänzungen vom 17. Oktober 2026. **Arbeitsstand:** task/task-018-codex-tags-search; Entwurf im separaten Arbeitsverzeichnis nach Reservierung #47. **Verantwortlich:** codex-20261017-tags-search-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Konfigurierbare automatische Abmeldung und umfangreiches Tag-System für Tickets/Tasks und Projekte sowie performante Suche für V1 in die Anforderungen aufnehmen. Sitzungssemantik, Tag-Verwaltung und Suchumfang gezielt klären. Direkte Modulaufrufe, REST und gRPC für den bestätigten modularen Monolithen einordnen. Keine Produktimplementierung.

**Abnahmekriterien:** Bestätigte Kernvorgaben erhalten stabile REQ-/DEC-IDs; Detailvorschläge und offene Antworten bleiben getrennt. Tags, Suche und Sitzungsablauf berücksichtigen API, Portal, Rechte, Konflikte und messbare spätere Prüffälle. Keine unbelegte Leistungszusage oder Jira-Funktionsgleichheit. Der interne Kommunikationsvorschlag widerspricht nicht DEC-043/DEC-044.

**Ergebnis:** REQ-AUTH-005, REQ-TAG-001 und REQ-SRCH-001 sowie DEC-045 bis DEC-047 erfassen die neuen V1-Kernvorgaben. Anforderungen Version 0.12, Roadmap, Funktionsinventar, Architektur-/Betriebsentwurf und KI-Einstieg sind nachgeführt. PERMISSIONS.md beschreibt konfigurierbare Sitzungsfristen mit acht späteren Prüffällen; TAGS_SEARCH.md beschreibt Katalogpflege, Massenaktionen, Sichtgrenzen, PostgreSQL-Suchvorschlag, Lastprofil und 17 spätere Prüffälle. Direkte Java-Modulaufrufe im selben Prozess sind empfohlen, REST bleibt an Frontend-/Integrationsgrenzen bestätigt; gRPC ist keine neue Pflicht. Detailwerte und zusätzliche Suchbereiche bleiben Vorschläge.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle 14 geänderten Dateien in c556ee17db8d5d55fb4080ac428ae108ad427f29 gegenüber c8b7d188562cddab0f202a603f2677d2004cd19a. Zwei Befunde: Namenskonflikte könnten verborgene Tags verraten; der ältere Rechtealgorithmus verlangte auch für angenommene Hintergrundaufträge eine aktive Browsersitzung. Korrektur 422025389caaa5183f1757277325a3ce49534998 bindet Namenseindeutigkeit an die Entdeckungsgrenze und trennt interaktive, Integrations- und Auftragskontexte. Die gezielte Nachprüfung beider Korrekturen fand keine weiteren konkreten Widersprüche. Dieser nachträgliche Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review oder vollständiger Produkt-/Sicherheitsnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Gezielter Vergleich: 89 Anforderungen, davon 59 B und 30 V; alle früheren 86 REQ-IDs und deren Status erhalten. Genau drei neue DEC-IDs, alle 44 früheren DEC-Zeilen unverändert; 21 OPEN-IDs und DONE.md unverändert. Keine Anwendungs-, Last- oder Sicherheitstests; die 25 beschriebenen neuen Prüffälle sind nicht ausgeführt.

**Nachtrag aus TASK-019 am 2026-10-17:** Die drei Antworten sind durch DEC-048 bis DEC-050 erfasst: ausschließlich feste Dauer seit Anmeldung, Tag-Erstellung auch durch berechtigte Portalbenutzer und gemeinsame Suche einschließlich Wiki/Anforderungen; Dateiinhalte später. Die oben dokumentierten Prüfstände bleiben historisch erhalten; der frühere Zwei-Fristen-Vorschlag ist ersetzt.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-17“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in dem Absatz „KI-Gegenprüfung am 2026-10-17“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-019 Bestätigte Sitzungs-, Portal-Tag- und Suchauswahl eintragen

**Bezug:** REQ-AUTH-005, REQ-TAG-001, REQ-SRCH-001, REQ-DOC-004, REQ-PERF-003. **Abhängigkeiten:** TASK-004, TASK-007, TASK-010, TASK-012, TASK-018 und Holgers ausdrückliche Auswahl vom 17. Oktober 2026. **Arbeitsstand:** task/task-019-codex-v1-decisions; Übernahme im separaten Arbeitsverzeichnis nach Reservierung #49. **Verantwortlich:** codex-20261017-v1-decisions-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Ausschließlich feste Sitzungsdauer seit Anmeldung, Tag-Erstellung auch durch berechtigte externe Portalbenutzer und gemeinsame V1-Suche über Tickets/Tasks, Projekte, Wiki und Anforderungen bestätigen; Dateiinhalte später. Anforderungen, Entwürfe, Roadmap und offene Fragen konsistent nachführen.

**Abnahmekriterien:** Genau diese Auswahl als drei neue Entscheidungen erfassen; frühere Entscheidungen und Prüfberichte erhalten. Keine Inaktivitätsfrist in den V1-Vertrag übernehmen. Externe Tag-Erstellung erweitert nicht automatisch Katalogverwaltung, Fachrechte oder Sichtbarkeit. Dateiinhalte bleiben außerhalb V1; Dateinamen-/Metadatenumfang und übrige Detailvorschläge sind nicht pauschal freigegeben. Keine Umsetzung oder fachliche Gesamtabnahme ableiten.

**Ergebnis:** DEC-048 bis DEC-050 erfassen ausschließlich die drei bestätigten Antworten. REQUIREMENTS.md steht auf Version 0.13. PERMISSIONS.md verwendet feste Dauer seit Anmeldung ohne Inaktivitätsfrist; TAGS_SEARCH.md berücksichtigt berechtigte externe Ersteller und gemeinsame Suche einschließlich Wiki/Anforderungen bei späteren Dateiinhalten. Sitzungsfälle angepasst, drei weitere Tag-/Suchfälle beschrieben. Inventar, Roadmap, Messprofil, Wiki-Vertrag, offene Fragen und KI-Einstieg sind konsistent nachgeführt; übrige Detailvorschläge bleiben offen.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle 13 geänderten Planungsdateien in 6d27a003774a93fadc7a48af52b31e132b0ff1ff gegenüber 1d3f7722d19a7c4dce44e04b915284083a5cf574, einschließlich umgebender Sitzungs-, Katalog-, Rechte- und Suchregeln. Keine konkreten Befunde: Auswahl korrekt übertragen, keine weitere Inaktivitätsfrist, keine automatische Rechteausweitung durch externe Tag-Erstellung, Wiki/Anforderungen in Suchumfang und Messprofil enthalten. Dieser anschließend ergänzte Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review oder Produktnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 89 REQ-IDs samt Status erhalten (59 B/30 V), genau drei DEC-IDs ergänzt; alle 47 vorherigen DEC-Zeilen unverändert. 21 OPEN-IDs und DONE.md unverändert. Acht Sitzungs- und 20 Tag-/Suchfälle sind beschrieben, nicht als Anwendungstests ausgeführt; keine Leistungs- oder Sicherheitsmessungen.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-17“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in dem Absatz „KI-Gegenprüfung am 2026-10-17“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-020 Entscheidungsvorlage und Aufgabenplan für den Entwicklungsstart

**Bezug:** REQ-PROD-002, REQ-ARCH-001, REQ-ARCH-002, REQ-API-001, REQ-DATA-001, REQ-AUTH-001, REQ-AUTH-005, REQ-ACL-001, REQ-ACL-003, REQ-TKT-001, REQ-TAG-001, REQ-SRCH-001, REQ-I18N-001, REQ-DEV-001 bis REQ-DEV-005. **Abhängigkeiten:** TASK-003, TASK-004, TASK-005, TASK-010, TASK-012, TASK-016, TASK-018 und TASK-019. **Arbeitsstand:** task/task-020-codex-start-plan; eigenes Arbeitsverzeichnis nach integrierter Reservierung #51. **Verantwortlich:** codex-20261017-start-plan-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Bestehende Entwürfe zu einer kompakten Entscheidungsvorlage mit technischen Empfehlungen, offenen Fachfragen, erstem durchgängigen Entwicklungsablauf und kleinen KI-Arbeitspaketen mit Schnittstellen, Abhängigkeiten und Prüfungen zusammenführen. Werkzeugempfehlungen aus Primärquellen prüfen. Künftige Aufgaben ausschließlich zentral in TODO.md vormerken.

**Abnahmekriterien:** Erster Abschnitt ist vom vollständigen V1-Umfang getrennt; keine bestätigte V1-Funktion wird gestrichen. Bestätigte Vorgaben, Empfehlungen und unbeantwortete Fragen sind unterscheidbar. Künftige Aufgaben sind bis zum gesonderten Implementierungsauftrag gesperrt, ohne vorgetäuschte Besitzer oder fertiggestellte Funktionen. Kritische Übergaben und Review-Nachweise sind benannt. Kein Anwendungscode, Gerüst, Datenbank oder Container wird erstellt.

**Ergebnis:** [DEVELOPMENT_START.md](docs/DEVELOPMENT_START.md) bündelt einen ersten internen Ablauf, technische Werkzeug-/Vertragsempfehlungen, drei offene Fachfragen, Abhängigkeiten und zehn geplante Nachweise. TASK-021 bis TASK-031 sind zentral als BLOCKIERT und unbesetzt vorgemerkt, mit Arbeitsgrenzen, Übergaben und Review-Kriterien. Einstieg, offene Entscheidungen, Roadmap und KI-Regeln verweisen darauf. Keine bestätigte V1-Funktion verschoben, keine neue Entscheidung vorweggenommen, keine Anwendung erstellt.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle acht geänderten Planungsdateien in 9cf25809fee3869c7f4a7c605ce8834b86d117d5 gegenüber 40e3d8f1eddce9ccf1b94a3bcee36098074daa82 einschließlich Folgeaufgaben und umgebender Fachverträge. Keine konkreten Befunde: V1-Umfang erhalten, Aufgabenabhängigkeiten zyklenfrei, Konten-/SMTP-/Bootstrap-Fragen sowie feste Frist, Rechte, Transaktionen und Suchgeheimhaltung konsistent. Dieser anschließend ergänzte Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review, Anwendungstest oder Sicherheitsnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 89 Anforderungen samt Status unverändert (59 B/30 V), alle 50 bisherigen DEC-Zeilen unverändert; 21 OPEN-IDs erhalten. TASK-001 bis TASK-019 und DONE.md unverändert; elf Folgeaufgaben blockiert/unbesetzt, ausschließlich acht Markdown-Dateien geändert. Werkzeugempfehlungen anhand offizieller Primärquellen eingeordnet, nicht praktisch qualifiziert. Die zehn Start-Szenarien sind nur geplant.

**Nachtrag aus TASK-032 am 2026-10-17:** Die drei damaligen Auswahlfragen sind durch DEC-051 bis DEC-053 beantwortet: erster interner Ablauf, LDAP/Portal anschließend innerhalb V1; 24 Stunden als Standard neuer Installationen; administrative lokale Kontoanlage mit Einmalpasswort und Pflichtwechsel. Die oben berichteten Ergebnisse/Prüfungen bleiben historisch erhalten. Technische Detailfreigaben und Implementierungsauftrag sind weiterhin offen.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-17“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in dem Absatz „KI-Gegenprüfung am 2026-10-17“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-021 Werkzeugbasis und gemeinsame Verträge qualifizieren

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-API-001, REQ-DATA-001, REQ-DEV-005. **Abhängigkeiten:** TASK-020 sowie ausdrücklich beauftragter Umfang und ausgewählte Kandidaten P1 bis P4. **Arbeitsstand:** task/task-021-codex-qualification, separates Arbeitsverzeichnis nach Reservierung #55. Holgers Fortsetzungsauftrag vom 17. Oktober 2026 beauftragt den angekündigten begrenzten Werkzeug-/Vertragsnachweis. P1 bis P4 werden dafür als zu prüfende Kandidaten verwendet, nicht als pauschal abgenommene Produktarchitektur; keine Fachimplementierung aus TASK-022 bis TASK-031. **Verantwortlich:** codex-20261017-qualification-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Exakte Werkzeug-/Bibliotheksversionen, Komponentenlizenzen, Modulordnung, Datenhoheit, Identitäts-/Rechtekontext und API-/Fehler-/Versionsvertrag für den ersten Abschnitt festlegen. Nach gesonderter Beauftragung einen begrenzten Build-/Generator-/Transaktionsnachweis erstellen; keine Fachmodule vorwegnehmen.

**Schnittstellen und Arbeitsbereich:** [TECHNICAL_BASELINE.md](docs/TECHNICAL_BASELINE.md), synthetische Quellen unter `qualification/`, Vertragsbeispiel unter `contracts/` und eigener CI-Workflow. Produktpfade `backend/` und `frontend/` existieren noch nicht. Die Übergabe liefert die qualifizierten Werkzeugversionen und gemeinsamen Vertragsregeln für TASK-022 und TASK-023; fachliche Typen und produktive API bleiben in deren Folgeaufgaben zu konkretisieren.

**Abnahmekriterien:** Java/Boot/PostgreSQL und React/TypeScript/Buildwerkzeuge gemeinsam qualifiziert; Generator verarbeitet Erfolgs-, Fehler- und Konfliktantworten. Modulprüfung, JPA-/JDBC-Transaktion und Rückabwicklung nachgewiesen. Lizenz-/Wartungsinformationen und verbleibende Lücken dokumentiert; keine ungeprüfte Alternativdatenbankzusage. Getrennte KI prüft Verträge und Nachweise.

**Ergebnis:** Der [Qualifikationsbericht](docs/TECHNICAL_BASELINE.md) hält exakte Versionen, gemeinsame Modul-/Daten-/API-Regeln, Herkunft, direkte Lizenzangaben, Wartungsgrenzen und Fehlerkorrekturen fest. Der [vollständige CI-Lauf 39063473112](https://github.com/1uedev/task-manager/actions/runs/39063473112) auf `79ad5fe18dfbbffeabbd46cd4643cf9bf01459c1` besteht: acht Java-Tests mit echtem PostgreSQL, sechs Clienttests mit den Java-JSON-Fixtures, Typgenerierung ohne Drift, strenge Typprüfung und React-Build. TypeScript 5.9.3 ersetzt im Versuch den mit dem Generator inkompatiblen 6.0.3-Kandidaten; keine erzwungene Peer-Auflösung. Kein Produkt, keine Anmeldung, Fachrechte, Suchmessung oder Restore-Prüfung erstellt.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte `ac27dc2d30434f49f130afca55867fffeb8a1a5d` gegen `970242b8083a5b64de54a7facc922f4d9301a6a9` sowie gezielt die Änderungen bis `94fa49c4f82a6905c902930072e0426cea825b05` und `79ad5fe18dfbbffeabbd46cd4643cf9bf01459c1`. Ein kleiner YAML-Beschreibungsfehler wurde korrigiert und nachgeprüft; danach keine offenen Befunde. Das letzte Nachreview las die heruntergeladenen Berichte und das CI-Protokoll und bestätigte die genannten Testzahlen. Keine eigenen Java-/Docker-Läufe des Reviewers, kein Claude-Review. Die abschließend ergänzte Ergebnisdokumentation und dieser Bericht sind Eigenprotokoll der koordinierenden Sitzung, keine zusätzliche unabhängige Freigabe.

**Eigenprüfung am 2026-10-17:** Repository-Validator, zwölf Regressionstests und git diff --check erfolgreich. Lokale Typgenerierung, Typprüfung und React-Build erfolgreich; Java-/PostgreSQL-/Client-Gesamtlauf auf dem flüchtigen GitHub-Runner. Frühere fehlgeschlagene Versuche sind im Bericht verlinkt. Die 89 Anforderungen mit Status (60 B/29 V), 53 historischen DEC-Zeilen, TASK-022 bis TASK-031 und DONE.md bleiben unverändert. DEC-054 ergänzt ausschließlich den begrenzten Auftrag. Die technische Prüfung ist keine fachliche Abnahme.

**Nachtrag aus TASK-034 am 2026-10-18:** Die Claude-Sitzung claude-20261018-review-01 hat das Ergebnis gegengeprüft; siehe [Prüfbericht](docs/REVIEW_TASK-021_TASK-033.md). Keine blockierenden Befunde; ein mittlerer Befund (fehlender Negativtest für die Transaktionspflicht) und mehrere niedrige Befunde und Hinweise zur Übergabe an TASK-022/TASK-023. Status, Zuständigkeit und Ergebnis bleiben unverändert; keine fachliche Abnahme.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-17“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in den Absätzen „KI-Gegenprüfung am 2026-10-17“, „Nachtrag aus TASK-034 am 2026-10-18“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-032 Bestätigten Startumfang, Sitzungsstandard und Kontoanlage eintragen

**Bezug:** REQ-PROD-001, REQ-PROD-002, REQ-AUTH-001, REQ-AUTH-005, REQ-PORTAL-003, REQ-DEV-004. **Abhängigkeiten:** TASK-004, TASK-006, TASK-012, TASK-020 und Holgers ausdrückliche Auswahl vom 17. Oktober 2026. **Arbeitsstand:** task/task-032-codex-start-decisions; separates Arbeitsverzeichnis nach integrierter Reservierung #53. **Verantwortlich:** codex-20261017-start-decisions-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Ersten internen Ablauf mit Anmeldung, Projekten, Tickets, Tags und Suche bei LDAP/Portal anschließend innerhalb V1, 24 Stunden feste Standardsitzungsdauer und administrative Kontoanlage mit Einmalpasswort und Pflichtwechsel bei erster Anmeldung als bestätigte Auswahl erfassen. Betroffene Anforderungen, Entwürfe, offene Fragen und gesperrte Folgeaufgaben konsistent nachführen.

**Abnahmekriterien:** Genau die drei Antworten als neue Entscheidungen aufnehmen; keine technische Umsetzung, Gesamtfreigabe oder weitere Detailentscheidung ableiten. Einmalpasswort und erzwungener Wechsel gelten auch für lokale Portalzugänge; sichere Ausgestaltung und Wiederherstellung bleiben ausdrücklich zur Konkretisierung. Frühere Entscheidungseinträge und Prüfberichte erhalten. TASK-021 bis TASK-031 bleiben blockiert/unbesetzt. Keine Anwendung erstellen.

**Ergebnis:** DEC-051 bis DEC-053 erfassen den ersten internen Abschnitt, 24 Stunden als Standardsitzungsdauer neuer Installationen und administrative lokale Kontoanlage mit Einmalpasswort/Pflichtwechsel. Anforderungen Version 0.14, Startvorlage, Rechte-/Portal-/API-Entwurf, Roadmap, Inventar und Einstieg sind konsistent nachgeführt. REQ-PORTAL-003 besitzt nun bestätigten Zugangskern; technische Detailverfahren bleiben offen. TASK-024/TASK-030 sind angepasst, alle elf Implementierungsaufgaben weiterhin BLOCKIERT/unbesetzt. Neun Sitzungs- und vier Kontoanlagefälle sind im Rechteentwurf beschrieben, nicht ausgeführt.

**KI-Gegenprüfung am 2026-10-17:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte alle 13 geänderten Dokumentdateien in 5245104b5fd519220ce96907d4d56b28f7c1b540 gegenüber 6a9c6fd12091ffbcb48326c09a990aae87d182c9. Keine konkreten Befunde: Auswahl korrekt, Sitzungsstandard von Einmalpasswortgültigkeit und API-Wiederholungsfenster getrennt, Pflichtwechsel für UI/API einschließlich lokaler Portalzugänge berücksichtigt. LDAP, Rechte und 2FA unverändert; sichere Detailverfahren offen. Historische Berichte erhalten. Dieser nachträglich ergänzte Bericht ist Eigenprotokoll der koordinierenden Sitzung; kein Claude-Review, Anwendungstest oder Sicherheitsnachweis.

**Eigenprüfung am 2026-10-17:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Alle 89 REQ-IDs erhalten, nur REQ-PORTAL-003 wechselt V zu B (60 B/29 V). Genau drei DEC-IDs ergänzt; alle 50 früheren DEC-Zeilen unverändert. 21 OPEN-IDs, TASK-001 bis TASK-019 und DONE.md unverändert; TASK-020 hat einen datierten Nachtrag, TASK-024/TASK-030 die nötige Folgeplanung. Keine Anwendung, Last- oder Sicherheitstests erstellt oder ausgeführt.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-17“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in dem Absatz „KI-Gegenprüfung am 2026-10-17“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

## TASK-033 Bearbeitbare und reservierte Aufgaben übersichtlich anzeigen

**Bezug:** REQ-DEV-001, REQ-DEV-002, REQ-DEV-003, REQ-DEV-005. **Abhängigkeiten:** vorhandene zentrale Aufgabenverwaltung aus TASK-013; ausdrücklicher Auftrag vom 18. Oktober 2026 zur Übersicht bearbeitbarer und durch KI reservierter Aufgaben. **Arbeitsstand:** task/task-033-codex-overview, separates Arbeitsverzeichnis nach integrierter Reservierung #57. **Verantwortlich:** codex-20261018-task-board-01. **Abgeschlossen:** 2026-10-28.

**Umfang:** Kompakte, aus den Aufgabeneinträgen erzeugte Übersicht in TODO.md für bearbeitbare, reservierte, blockierte und zur Prüfung bereite Aufgaben. Zuständigkeit und letzter Aktualisierungsstand sichtbar machen, Aktualisierung und Konsistenzprüfung automatisieren und den Einstieg für neue KI-Sitzungen erklären. Bestehende Produktaufgaben nicht eigenmächtig freigeben.

**Abnahmekriterien:** Jede offene Aufgabe erscheint genau in der passenden Statusgruppe. Reservierte Aufgaben nennen die zuständige Sitzung. Freie und blockierte Arbeit ist eindeutig getrennt; offene Aufgaben mit zugeordnetem Besitzer werden nicht als frei angeboten. Die Übersicht ist eine abgeleitete Ansicht derselben TODO-Datei, keine zweite Statusquelle. Veraltete Übersichten werden von CI erkannt. Bestehende Aufgaben, Entscheidungen und Abnahmen bleiben erhalten. Getrennte KI prüft Verfahren und Nachweise.

**Ergebnis:** Die Aufgabenübersicht oben in TODO.md zeigt alle Einträge nach Verfügbarkeit, Reservierung, Blockade und Prüfbereitschaft mit Aufgabenlink, zuständiger Sitzung und Aktualisierungsdatum. scripts/task_board.py erzeugt sie aus derselben Datei; der verpflichtende repository-checks-Job weist eine veraltete Ansicht zurück. Gemeinsame Arbeitsregeln und Einstieg für Codex/Claude erklären Aktualisierung und Reservierung. Keine automatische Vergabe, Freigabe oder zeitliche Aufhebung einer Reservierung.

**KI-Gegenprüfung am 2026-10-18:** Die getrennte Codex-Sitzung sessions_tags_search_review prüfte zunächst 11fb6d57c295e045f0bb87d2979c2fb22f4bc1b1 gegenüber dem integrierten Claim #57. Die Befunde zu unvollständigen Aufgabenüberschriften und einer vollständig abgearbeiteten TODO-Liste wurden in 37556037ae57ec0539ce47342da26f9c6bf3f8ab korrigiert. Die anschließende Prüfung erkannte noch eingerückte Überschriften; cdbe689fcc3fa891bea9fa34be81e30968c1f101 behebt diesen Fall. Die erneute gezielte Prüfung bestätigte die Korrektur ohne weitere konkrete Befunde. Der Reviewer prüfte Fälle im Speicher sowie Validator, Übersichtsprüfung und Diff; die vollständige Testsuite wurde von der koordinierenden Sitzung ausgeführt. Dieser nachträglich ergänzte Bericht ist Eigenprotokoll, kein Claude-Review oder Nachweis einer fachlichen Abnahme.

**Eigenprüfung am 2026-10-18:** Repository-Validator, Übersichtsprüfung, alle 25 Regressionstests und git diff --check erfolgreich. Tests prüfen unter anderem eindeutige Zuordnung, inkonsistente Besitzer, beschädigte Marker, leere Listen, Erhalt der Aufgaben und schreibfreie Zurückweisung fehlerhafter Überschriften. TASK-001 bis TASK-032 sowie Anforderungen, Entscheidungen, Roadmap und DONE.md bleiben unverändert. Für cdbe689 bestehen auch die GitHub-Läufe [Repository checks](https://github.com/1uedev/task-manager/actions/runs/39112206093) und [Technical qualification](https://github.com/1uedev/task-manager/actions/runs/39112206092); der Ergebnis-PR #58 prüft die abschließende Übergabefassung erneut.

**Nachtrag aus TASK-034 am 2026-10-18:** Die Claude-Sitzung claude-20261018-review-01 hat das Ergebnis gegengeprüft; siehe [Prüfbericht](docs/REVIEW_TASK-021_TASK-033.md). Keine blockierenden Befunde; ein niedriger Befund zu nicht geprüften Ankerlinks und ein Hinweis zum Whitespace-Schritt der CI. Status, Zuständigkeit und Ergebnis bleiben unverändert; keine fachliche Abnahme.

**Prüfungen:** Wie in dem Absatz „Eigenprüfung am 2026-10-18“ festgehalten.

**Review:** Getrennte KI-Prüfung wie in den Absätzen „KI-Gegenprüfung am 2026-10-18“, „Nachtrag aus TASK-034 am 2026-10-18“ festgehalten.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch: „ja dsa gilt auch für die anderen offenen tasks“ und auf der Rückfrage zu TASK-001 bis TASK-033 die Wahl „Ja, alles in PR #187“; bestätigt durch Merge dieses Abschluss PR. Die Abnahme gilt dem Arbeitsstand dieser Aufgabe. Als Vorschlag (V) gekennzeichnete Anforderungen, offene Fragen und nicht bestätigte technische Vorschläge bleiben, was sie sind; DECISIONS.md und REQUIREMENTS.md bleiben maßgeblich.

**Referenz:** kein einzelner Commit Verweis vermerkt, das Ergebnis liegt auf main; Abschluss PR #187.

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

## TASK-024 Lokale Anmeldung und feste Sitzungsfristen implementieren

**Bezug:** REQ-AUTH-001, REQ-AUTH-003, REQ-AUTH-005, REQ-SEC-001. **Verantwortlich:** claude-20261018-identity-01. **Abgeschlossen:** 2026-10-18.

**Abnahmekriterien:** Neue Installation mit 24 Stunden voreingestellt, andere gültige Stundenwerte administrativ änderbar. Vor Pflichtwechsel kein regulärer Fachzugriff über UI/API oder Ausgabe von Integrationszugängen; verbrauchtes Einmalpasswort nicht wiederverwendbar. Kein zusätzliches Inaktivitätslimit, keine Fristverlängerung durch Aktivität, Rotation oder Neustart; Ablauf und Deaktivierung serverseitig wirksam. Cookie-/CSRF- und negative Anmeldefälle geprüft. Keine produktiven Standardpasswörter. Getrennte KI prüft Sicherheitsgrenzen; Umfang des Kontenwegs bleibt in Ergebnis und Vorführung sichtbar.

**Ergebnis:** Das Identitätsmodul im [Backend](backend/README.md#identität-task-024) setzt die zehn Beschlüsse um. Migration `V0003__identity_accounts.sql` (V0002 gehört der Berechtigung aus TASK-025) mit fünf eigenen Tabellen: Konten, Sitzungen, Einstellung, Fehlversuchzähler, Prüfprotokoll; kein Konto und kein Standardpasswort in der Migration. Containerbefehle `bootstrap-admin NAME` (nur ohne vorhandene Konten) und `reset-admin-password NAME` (Notweg, protokolliert). Lokale Konten mit Einmalpasswort (20 Zeichen, 72 Stunden, genau eine erfolgreiche Anmeldung, nur als Argon2id Hash gespeichert, einmalig in der Antwort), Anmeldung, Abmeldung, Deaktivierung und Reaktivierung (letzter aktiver Administrator geschützt), Zurücksetzen durch Administratoren. Eingeschränkte Sitzung bis zum Pflichtwechsel (nur Passwortwechsel und Abmeldung, höchstens 30 Minuten), danach neue Sitzungskennung mit unverändertem Ablauf; alle anderen Sitzungen des Kontos enden. Feste Sitzungsdauer, 24 Stunden voreingestellt, 1 bis 720 Stunden einstellbar, nicht abschaltbar, ohne Verlängerung durch Aktivität, Rotation oder Neustart; Verkürzung wirkt bei der nächsten Anfrage, Verlängerung nur auf neue Anmeldungen. Serverseitige Sitzungen mit Token Hash, Cookies `__Host-tm-session` (HttpOnly) und `__Host-tm-csrf` (Secure, SameSite=Lax), CSRF per Doppelcookie mit Sitzungsbindung und Herkunftsprüfung, Standardverweigerung für jeden Pfad unter `/api`, `Cache-Control: no-store`. Wartezeit nach fünf Fehlversuchen je Name und je Adresse ohne Kontosperre; gleiche Antwort für bekannte, unbekannte und deaktivierte Konten. Produkt API Vertrag [identity.openapi.yaml](contracts/identity.openapi.yaml) mit einem Test gegen Abweichungen von den Routen. Zugriffskontext für Fachmodule über `taskmanager.identity.AccessContexts`. Mit dem Ergebnis eingetragen: DEC-058. Stand nach der Nachbesserung zur Sicherheitsprüfung: Eine Verkürzung der Sitzungsdauer wird beim Ändern in alle offenen Sitzungen geschrieben und nie zurückgenommen; ein Einmalpasswort wird mit der ersten erfolgreichen Anmeldung verbraucht; der Filter unter `/api` entscheidet auf dem von Spring MVC zugeordneten Pfad; die Adressschwelle liegt bei fünf Fehlversuchen; vier Migrationen (V0001 bis V0004).

**Prüfungen:** Erste Lieferung (PR #75): Lokal mit OpenJDK 21 (nur Entwicklerhilfe, CI nutzt Temurin 25) gegen PostgreSQL 16: 59 Tests ohne Fehler und ohne übersprungene Tests, darunter 29 HTTP Integrationstests (Standardverweigerung, CSRF, Cookie Attribute, Pflichtwechsel, verbrauchtes und abgelaufenes Einmalpasswort, gleichzeitige Wechsel, feste Frist über 23 Stunden Aktivität und Ablauf, Verkürzung und Verlängerung, Deaktivierung, Zurücksetzen, Wartezeiten, Protokoll ohne Geheimnisse, Prüfprotokoll), 5 Tests der Containerbefehle gegen frische Datenbanken, Regel und Tokentests sowie die Vertragsprüfung. Das gepackte JAR bestand `lifecycle-check.sh` einschließlich `bootstrap-admin` und der Ablehnung eines zweiten Aufrufs. Vor dem Ergebnis gefundene und behobene Fehler: Zeitstempel wurden von PostgreSQL auf Mikrosekunden gerundet und konnten Wartezeiten um eine Sekunde verlängern (jetzt abgeschnitten), der Problemschreiber warf bei fehlendem `type` einen Nullzeiger. Repository Prüfungen (`validate_repository.py`, `task_board.py --check`, `unittest` mit 25 Tests, `git diff --check`) bestanden. Nicht lokal ausgeführt: Java 25, PostgreSQL 18.6 und Docker. [CI-Lauf 39146584613](https://github.com/1uedev/task-manager/actions/runs/39146584613) auf dem mit main (TASK-025) zusammengeführten Stand `8b000f1`: Temurin 25.0.4.1 und PostgreSQL 18.6, 73 Tests ohne Fehler und ohne übersprungene Tests (14 davon aus TASK-025), danach das gepackte JAR gegen eine leere Datenbank: Abweisung ohne Einstellungen, erster Start mit genau den drei Migrationen V0001 bis V0003, Neustart ohne Änderung, `bootstrap-admin` mit einmaliger Ausgabe, Ablehnung des zweiten Aufrufs und Start mit vorhandenem Konto. Frontend, Repository und Qualifikation derselben Prüfung ebenfalls erfolgreich. Nach der Nachbesserung (PR #79): lokal mit OpenJDK 21 gegen PostgreSQL 16 83 Tests ohne Fehler und ohne übersprungene Tests (73 bisherige und 10 neue Regressionstests in `IdentityHardeningTest`); auf dem Stand vor der Korrektur scheitern 9 der 10 neuen Tests, einer ist die positive Kontrolle des Pflichtwechsels. `backend/scripts/lifecycle-check.sh` am gepackten JAR lokal bestanden. [CI-Lauf 39153055006](https://github.com/1uedev/task-manager/actions/runs/39153055006) auf `eaa9c35` mit Temurin 25 und PostgreSQL 18.6: 83 Tests ohne Fehler und ohne übersprungene Tests, vier Migrationen angewendet, Lifecycle-Prüfung einschließlich `bootstrap-admin` und Neustart bestanden; Auf dem Merge Commit `5381db3` in main liefen Backend ([Lauf 39155818999](https://github.com/1uedev/task-manager/actions/runs/39155818999)), Repository checks, Frontend und Technical qualification ebenfalls erfolgreich durch. Nicht ausgeführt: Browser-CSRF-Angriff, Last- und Abhängigkeitsschwachstellenscan, Windows.

**Review:** Getrennte KI-Sicherheitsprüfung nach DEC-040 durch die Codex-Sitzung codex-20261018-identity-security-01 auf PR #75 ([Bericht](docs/REVIEW_TASK-024_PR-75.md), PR #77, mit reproduzierbaren Gegenproben unter `docs/review-evidence`): fünf Befunde, zwei hohe und drei mittlere. Alle fünf waren im Code nachvollziehbar und wurden mit Holgers Entscheidung "Alle beheben" über Claim PR #78 und Ergebnis PR #79 behoben. Alle fünf Befunde waren im Code nachvollziehbar und sind behoben; die Gegenproben aus `docs/review-evidence` wurden als negative Regressionstests in `IdentityHardeningTest` umgekehrt (10 Tests). Gegenprobe der Tests: Auf dem Stand vor der Korrektur scheitern 9 davon, mit der Korrektur laufen alle. R1 (hoch): `login` sperrt die Kontozeile vor dem Anlegen der Sitzung, liest sie neu und startet nur, wenn Hash und Aktivstatus noch dem geprüften Zustand entsprechen, sonst `auth.invalid_credentials` und Fehlzähler; zusätzlich gilt eine volle Sitzung bei einem Konto mit offenem Pflichtwechsel nicht mehr als lebendig. Der Test hält den Login deterministisch am Zeilenlock (`pg_blocking_pids`). R2 (hoch): Eine Verkürzung wird beim Ändern in alle offenen Sitzungen geschrieben und nie zurückgenommen; die Dauer wird beim Anlegen einer Sitzung mit gemeinsamer Sperre gelesen, damit ein gleichzeitiges Ändern sie ebenfalls erfasst. R3 (mittel): Neue Migration `V0004__identity_one_time_consumption.sql` (Spalte `one_time_used_at`); die erste erfolgreiche Anmeldung verbraucht das Einmalpasswort in derselben Transaktion, jede weitere (auch parallel und nach Ablauf der eingeschränkten Sitzung) liefert `auth.one_time_password_used`, bis ein Administrator ein neues ausgibt; der Pflichtwechsel in der verbrauchenden Sitzung funktioniert weiter. R4 (mittel): Der Filter entscheidet auf dem von Spring MVC zugeordneten Pfad (Pfadparameter entfernt, Kodierung aufgelöst) statt auf `getRequestURI()`; Tests mit neun Schreibweisen für einen Testhandler sowie für Login ohne CSRF und mit fremdem Origin. R5 (mittel): Standard der Adressschwelle fünf statt 20 nach DEC-058 und Holgers Entscheidung "Alle beheben"; hinter gemeinsamem Proxy oder NAT teilen sich Nutzer eine Adresse, die Schwelle bleibt einstellbar. Angepasst: Vertrag (neuer Fehlercode), README, Foundation-, Lebenszyklus- und Lifecycle-Skript auf vier Migrationen. Lokal (OpenJDK 21, PostgreSQL 16): 83 Tests ohne Fehler und ohne übersprungene Tests, gepacktes JAR mit `lifecycle-check.sh` bestanden. CI-Nachweis im Ergebnis PR. Nicht erneut geprüft: Java 25, PostgreSQL 18.6 und Browser-CSRF lokal. Eine Nachprüfung durch Codex am Korrekturcommit steht aus. Die Korrekturen wurden **nicht** erneut durch Codex geprüft; ihre Nachweise sind die Eigenprüfung der umsetzenden Sitzung und die CI.

**Abnahme:** Holger, 2026-10-18, im Projektgespräch erklärte Annahme von TASK-024 („Ist ohne Nachprüfung abgenommen“) nach Bericht über die Korrekturen in PR #79, ausdrücklich ohne erneute Codex Prüfung; bestätigt durch Merge dieses Abschluss-PR.

**Offen und Folgeaufgaben:** (1) Der Anschluss an das Berechtigungsmodul fehlt weiter: Der Port `IdentityAccess` aus TASK-025 erwartet Mandant, Organisation, Rechteversion und einen Adapter außerhalb des Identitätsmoduls, der Konto und Sitzung unter einer bis zum Commit gehaltenen Sperre prüft; das Identitätsmodul führt diese Daten und Sperren auf seinen Entzugswegen (Deaktivieren, Zurücksetzen, Abmelden, Passwortwechsel) nicht. Das Feld `administrator` ist nur eine vorläufige technische Kennzeichnung. Für diesen Adapter ist keine Sitzung zugewiesen und er wird nicht ohne Auftrag gebaut. (2) Nicht Teil dieser Aufgabe: Anmeldeseite (TASK-030), LDAP, Portalkonten, zweiter Faktor. (3) Hinter einem gemeinsamen Proxy oder NAT teilen sich Nutzer eine Quelladresse; die Schwelle ist einstellbar (`taskmanager.identity.source-failure-threshold`). Kleine Passwortliste und Aufbewahrung des Prüfprotokolls (OPEN-014) sind in `backend/README.md` als Grenzen vermerkt. (4) `docs/review-evidence/IdentitySecurityProbeTest.java` beschreibt den fehlerhaften Zustand vor der Korrektur und ist durch `IdentityHardeningTest` ersetzt. (5) TASK-026 bis TASK-031 bleiben blockiert; TASK-025 liegt bei Codex und ist nicht abgenommen.

**Referenz:** Reservierung #73 (8a39c05), Ergebnis #75 (508f0df), Sicherheitsprüfung #77 (7a1bd34), Reservierung der Nachbesserung #78 (9d65106), Nachbesserung #79 (5381db3).

## TASK-025 Gruppen-, Projekt- und Feldrechte implementieren

**Bezug:** REQ-ACL-001, REQ-ACL-003, REQ-ACL-004, REQ-ACL-005. **Verantwortlich:** codex-20261018-permissions-01 (Umsetzung), claude-20261018-permissions-01 (Nachbesserung P1 und P7 nach Übergabe im Claim PR #81). **Abgeschlossen:** 2026-10-19.

**Abnahmekriterien:** Positive, negative und Mehrgruppenfälle einschließlich Feldverbot, Rechteentzug und konkurrierender Änderung geprüft. Fehlender Kontext verweigert Zugriff; Testdoubles werden im integrierten Betrieb ersetzt. Getrennte KI prüft Rechtealgorithmus, Auditgrenzen und konkurrierenden Entzug.

**Ergebnis:** Das Berechtigungsmodul im [Backend](backend/PERMISSIONS.md) (PR #74) verwaltet Gruppen und Mitgliedschaften, Unternehmens- und Projektaktionen und getrennte Lese- und Schreibrechte je Feld. Aktionsfreigaben mehrerer Gruppen werden vereinigt, ohne Freigabe gibt es keinen Zugriff, ausdrückliche Feldverbote gewinnen, Schreiben verlangt zusätzlich Lesen. Eine Systemverwaltung hat keinen impliziten Zugriff auf Fachinhalte, eine Projektdelegation ist begrenzt. Rechteänderung, Revision und unternehmensgetrenntes Audit mit eigenem Leserecht laufen atomar in der Transaktion des Aufrufers unter einer Revisionssperre; alte Aufträge nach einem Entzug werden verweigert. Migration `V0002__permissions_baseline.sql` ohne Konten, Startdaten oder Defaultrechte. Ohne genau einen Identitätsadapter, bei fehlendem oder altem Kontext, Pflichtwechsel oder fremdem Mandanten wird verweigert; synthetische Adapter gibt es nur im Test. Keine HTTP Endpunkte und keine fremden Tabellenzugriffe. DEC-057 führt den Auftrag nach. Nachbesserung nach dem Gegenreview (DEC-059, PR #82): (P1) Änderungen, nach denen keine Gruppe mit `permissions.manage` auf Unternehmensebene mehr ein Mitglied hat, werden verweigert und zurückgerollt; (P7) eine Projektdelegation ändert nur Verbote, die sie selbst gesetzt hat, dafür Migration `V0005__permissions_rule_origin.sql` (`set_by`). Die Folgeaufgabe TASK-038 (DEC-062, PR #90) hat danach die beiden Befunde R1 und K1 der Nachprüfung behoben (siehe dort).

**Prüfungen:** Erste Lieferung (PR #74): Lokal sieben datenbankfreie Backendtests; [Backend-CI 39135636061](https://github.com/1uedev/task-manager/actions/runs/39135636061) auf `19a6755` mit PostgreSQL 18.6: 28 Tests ohne Fehler und ohne übersprungene Tests, darunter 10 Berechtigungsintegrationstests mit Konkurrenzfällen in beiden Reihenfolgen und echter Sperrwartebeziehung (`pg_blocking_pids`), gepacktes JAR mit Start, Stopp und Neustart. Nachbesserung (PR #82): lokal 87 Tests mit OpenJDK 21 und PostgreSQL 16, [Backend-CI 39164298025](https://github.com/1uedev/task-manager/actions/runs/39164298025) auf `c27d5a6` mit Temurin 25 und PostgreSQL 18.6: 87 Tests, Lifecycle-Prüfung bestanden. Grenze: Der Test des gleichzeitigen Entzugs bestand schon vor der Nachbesserung, er belegt nur die Serialisierung. Alle Lieferungen liefen nur gegen synthetische Adapter.

**Review:** (1) [Bericht](docs/REVIEW_TASK-025.md) des getrennten Codex Prüfers `permissions_review` zur ersten Lieferung (statisch, mittlerer Befund R1 zur Sperrpflicht der Adapter adressiert, keine offenen Befunde, ohne eigene Testläufe). (2) Gegenreview durch Claude nach DEC-040 ([Bericht](docs/REVIEW_TASK-025_PR-74.md), PR #76): Holger entschied P1 und P7 mit „Beide wie vorgeschlagen umsetzen“, P2 bis P6 sind nicht entschieden. (3) Gezielte Nachprüfung der Nachbesserung durch codex-20261019-permissions-followup-01 ([Bericht](https://github.com/1uedev/task-manager/blob/review/task-025-pr82-codex/docs/REVIEW_TASK-025_PR-82.md), [PR #87](https://github.com/1uedev/task-manager/pull/87), mit eigener Ausführung von 87 Tests und Gegenproben): R1 (deaktiviertes Konto zählt als verbleibende Administration) und K1 (Herkunft nach Rollenwechsel), beide über TASK-038 behoben.

**Abnahme:** Holger, 2026-10-19, im Projektgespräch („task 25 und 38 sind abgenommen“), gemeinsam mit TASK-038; bestätigt durch Merge dieses Abschluss PR. Eine erneute gezielte Nachprüfung der Korrekturen aus TASK-038 (PR #90) durch eine getrennte KI hat nicht stattgefunden und wurde nicht angefordert.

**Offen und Folgeaufgaben:** (1) Der echte Identitätsadapter, der Konto und Sitzung unter einer bis zum Commit gehaltenen Sperre prüft und Mandant, Organisation und Rechteversion führt, fehlt weiter und ist keiner Sitzung zugewiesen; er muss auch die Schranke für die letzte Administration auf dem Entzugsweg des Identitätsmoduls selbst durchsetzen. Ebenso fehlen Projekt- und Objektadapter (TASK-026). (2) P2 bis P6 des Gegenreviews sind weder entschieden noch umgesetzt; ob sie mit der Abnahme zurückgestellt sind, hat Holger nicht ausdrücklich gesagt. (3) Das Audit ist nur mit den letzten 100 Einträgen lesbar, Seitenweise Lesen gehört zu TASK-030, die Aufbewahrung zu OPEN-014. Projektverwalter können `groups()` nicht aufrufen, die Delegation braucht in TASK-030 einen eingeschränkten Lesepfad. (4) TASK-026 bis TASK-031 bleiben blockiert.

**Referenz:** Reservierung #69 (0619b65), Ergebnis #74 (d60c554), Gegenreview #76 (54245fd), Reservierung der Nachbesserung #81 (370a69c), Nachbesserung #82 (dbc188b), Nachprüfbericht #87 (offen), Folgeaufgabe TASK-038 mit Reservierung #89 (d837463) und Ergebnis #90 (5dbe32e).

## TASK-071 Befunde der Codex Prüfung von TASK-070 beheben

**Bezug:** REQ-HD-005, ACC-002. **Verantwortlich:** claude-20261030-sla-01. **Abgeschlossen:** 2026-11-01.

**Abnahmekriterien:** Regressionstests für den Fall aus F1 (Pause nach der Schwelle), für H5 (Erfüllung nach der Schwelle vor der Prüfung) und für den kleineren Budgetwechsel; ein API Test mit mehr als 200 neueren nicht lesbaren und einer älteren lesbaren Stufe; API Test für `clock: null` mit 400; Browsertests gegen die angepasste Attrappe; volle Backend- und Frontend-Suites grün.

**Ergebnis:** DEC-101, ohne Migration. Holger hat am 31. Oktober 2026 zu H5 „Ja, Stufe nachtragen“ gewählt. `TicketSla.change` hält vor jedem Schreiben eines geänderten Zyklus (Statuswechsel, Kunde entfernt, Antwort gesendet, Priorität) die bis dahin erreichten Stufen unter derselben Sperre fest; die Überwachung nutzt dieselbe Methode `record`. `Escalations.due` nimmt einen Zeitpunkt vor dem letzten Stand als diesen Stand, so dass eine erst durch ein kleineres Budget fällige Stufe den Zeitpunkt der Budgetänderung erhält. Die Liste liest Kandidaten seitenweise (`SlaRepository.escalationsFor` mit Fortsetzung und ausgeschlossenen Projekten) bis 200 sichtbare Einträge. `Rule.Stage.violations` prüft eine fehlende Uhr zuerst. Testattrappe mit der Grenze von 200.

**Prüfungen:** Backend vollständig gegen PostgreSQL 16 lokal grün (55 Testklassen, 460 Tests), darunter `EscalationsTest` mit 8 Tests und 5 neue Tests in `SlaApiTest`. Frontend `tsc`, 77 Einheitentests und 139 Browsertests grün. CI auf PR #225 grün. Nicht lokal geprüft: Lifecycle Skript und Abbild ohne Docker, beides in der CI.

**Review:** Getrennte Nachprüfung durch Codex, Sitzung codex-20261101-task071-review-01, am festen Head 8602afe ([Bericht](docs/REVIEW_TASK-071_PR-225.md), PR #226): F1 bis F3 sowie H1, H2 und H5 behoben, kein neuer Befund, Empfehlung zur Abnahme. Codex hat 8 Tests aus `EscalationsTest`, 9 ausgewählte Fälle aus `SlaApiTest`, zwei eigene Zusatzfälle (zwei Prioritätswechsel ohne Überwachung dazwischen, Kunde nach der Schwelle entfernt) und die 8 SLA Browsertests ausgeführt, nicht die vollständigen Suites.

**Abnahme:** Holger, 2026-11-01, im Projektgespräch mit „gleich abnehmen“ auf die Frage, ob TASK-068, TASK-070 und TASK-071 ohne eigene Prüfung von TASK-068 abgenommen werden; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben** (nicht beauftragt): Aus der Nachprüfung H1 (die Liste ist bei gleichzeitigem Schreiben keine eingefrorene Momentaufnahme), H2 (der Nebenläufigkeitstest erzwingt nicht beide Reihenfolgen, gezielte Barrieren wären eine spätere Härtung), H3 (Zyklen, deren Stufen vor dieser Änderung nicht beim Anhalten gespeichert wurden, erhalten nach dem Fortsetzen den Zeitpunkt des Fortsetzens).

**Referenz:** Prüfbericht und Reservierung #224 (6ccb7eb), Ergebnis #225 (23afc8b), Nachprüfbericht #226 (a692820); verknüpft mit TASK-070 und DEC-101.

## TASK-070 SLA Eskalationen (A10 Teil 2)

**Bezug:** REQ-HD-005 (Eskalationen), ACC-002; OPEN-008. **Verantwortlich:** claude-20261030-sla-01. **Abgeschlossen:** 2026-11-01.

**Abnahmekriterien:** S-13 und H-11 als Tests, mehrere Ausführer erzeugen keine doppelte Stufe; die Liste zeigt nur Tickets, die die Person lesen darf, und nur Stufen ihrer Gruppen, Rechte werden beim Lesen geprüft; eine Pause oder Erfüllung vor der Schwelle erzeugt keine Stufe, eine bereits entstandene bleibt im Verlauf; Migration, Vertrag gegen Routen, Backend und Browser Tests.

**Fachliche Regeln** (von Holger am 30. Oktober 2026 mit den Vorschlägen 17 bis 20 zu OPEN-008 bestätigt): Stufen je Regel und Uhr mit Prozentwert und Empfängergruppe, Vorschlag 80 % und 100 % ohne feste Produktvorgabe; Hinweis am Ticket und Liste „SLA Eskalationen“ für Mitglieder der Gruppe mit Leserecht, keine Mail; jede Stufe je Ticket, Zyklus, Uhr und Stufe höchstens einmal, nach einem Ausfall nur die höchste fällige mit Protokoll der übersprungenen (H-11), Überwachung `sla.watch` jede Minute; keine automatische Zuweisung und kein Statuswechsel.

**Ergebnis:** DEC-100, Migration V0032. Stufen je Regelversion, wiederkehrende Aufgabe `sla.watch`, Tabelle `sla_escalation` mit einer Zeile je Zyklus, Uhr und Stufe, Port `GroupDirectory` des Berechtigungsmoduls, Liste `GET /api/v1/sla/escalations`, erreichte Stufen in der Ticketansicht; Vertrag [contracts/sla.openapi.yaml](contracts/sla.openapi.yaml), Beschreibung in [backend/README.md](backend/README.md#eskalationen-task-070). Oberfläche mit Stufen im Regelformular, Seite „SLA Eskalationen“ und Hinweis am Ticket; Demodaten mit Stufen je Uhr. Die Befunde der Prüfung sind mit TASK-071 behoben.

**Prüfungen:** Backend vollständig gegen PostgreSQL 16 lokal grün (55 Testklassen, 453 Tests), darunter `EscalationsTest` mit 6 Tests, vier neue Tests in `SlaApiTest` und `SlaContractTest` mit 12 Routen. Frontend `tsc`, 77 Einheitentests und 138 Browsertests grün. CI auf PR #223 grün, Lifecycle Skript und Abbild nur in der CI.

**Review:** Getrennte Prüfung durch Codex, Sitzung codex-20261031-task070-review-01 ([Bericht](docs/REVIEW_TASK-070_PR-223.md), PR #224): F1 und F2 (P2) und F3 (P3) sowie Hinweise H1 bis H5; F1 bis F3, H1, H2 und H5 mit TASK-071 behoben und nachgeprüft.

**Abnahme:** Holger, 2026-11-01, im Projektgespräch mit „gleich abnehmen“, zusammen mit TASK-068 und TASK-071; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben** (nicht beauftragt): H3 der Prüfung (eine Stufe mit gelöschter Gruppe erreicht niemanden und muss vor dem Speichern repariert werden) und H4 („jede Minute“ ist das Intervall, keine harte Zusage der Verzögerung). Mail und Benachrichtigungen für Eskalationen gehören zu einem späteren Paket.

**Referenz:** Reservierung #220 (f55568c), Ergebnis #223 (f68438f), Prüfbericht #224 (6ccb7eb); verknüpft mit TASK-068, TASK-071 und DEC-100.

## TASK-068 SLA Fristen und Geschäftszeiten (A10 Teil 1)

**Bezug:** REQ-HD-003 (SLA Fristen), REQ-HD-004 (Geschäftszeiten), ACC-002; DEC-032; OPEN-008. **Verantwortlich:** claude-20261030-sla-01. **Abgeschlossen:** 2026-11-01.

**Abnahmekriterien:** Rechenbeispiele S-01 bis S-14 und H-09, H-10, H-12 aus docs/HELPDESK_SLA.md als Backend Tests, dazu Grenzauflösung bei Zeitumstellung; Kalender und Regeln versioniert, nur mit `sla.manage` änderbar, mit Audit, mehrdeutige Zuordnung mit 409 abgelehnt; Uhren in der Transaktion der auslösenden Änderung fortgeschrieben ohne doppelten oder verlorenen Zyklus; Fristen am Ticket nur mit Leserecht und ohne verborgene Felder; Migration, Vertrag, Backend Tests für Rechte, Zustände und Nebenläufigkeit, Browser Tests, Demodaten mit einem Kalender und einer Regel.

**Fachliche Regeln** (von Holger am 30. Oktober 2026 bestätigt, Klärung von OPEN-008):
1. Neues Ticketfeld `priority` mit den Werten Niedrig, Normal, Hoch, Kritisch, Vorgabe Normal, mit Feldregeln und Historie.
2. Budgets ohne Produktvorgabe: Erstreaktion und Lösung in Geschäftsminuten je Regel und Priorität; 120 und 480 Minuten nur in den Demodaten.
3. Zuordnung der Regel in der Reihenfolge Projekt mit Kunde und Tickettyp, Projekt mit Kunde, Standard des Projekts; zwei gleich passende Regeln werden beim Speichern abgelehnt; ohne Regel zeigt das Ticket „Keine SLA zugeordnet“.
4. Fristen nur für Tickets mit gesetztem Feld `customer`; ein später gesetzter Kunde startet mit dem Zeitpunkt aus Regel 7.
5. Neues Unternehmensrecht `sla.manage` für Kalender, Regeln und deren Zuordnung; Lesen der Fristen folgt der Leseregel des Tickets.
6. Kalender mit IANA Zeitzone, je Wochentag einem oder mehreren offenen Zeiträumen und datierten Ausnahmen; Zeitumstellung wie in docs/HELPDESK_SLA.md (nicht existierende Uhrzeit auf die nächste gültige, doppelte Startzeit auf das frühere, doppelte Endzeit auf das spätere Vorkommen). Ein laufender Zyklus behält die Fassung von Regel und Kalender, mit der er begann.
7. Start ist der erste dauerhafte Eingang: bei einer Mail der Zeitpunkt der Speicherung durch den Abruf, auch bei späterer Übernahme aus der Prüfwarteschlange (S-12); sonst die Anlage des Tickets. Der Date Header zählt nicht.
8. Qualifizierte Erstreaktion ist eine Antwort über SMTP im Zustand „gesendet“, zum Zeitpunkt der Annahme durch den Mailserver.
9. Jeder Status eines Ablaufs bekommt in der Projektkonfiguration eine SLA Art: offen (Vorgabe), wartet auf Kunde, gelöst, verworfen.
10. Kundenpause (DEC-032) beginnt mit dem Wechsel in „wartet auf Kunde“ und endet mit einer dem Ticket zugeordneten Kundenantwort oder dem nächsten Statuswechsel; eine Antwort des Supports beendet sie nicht.
11. Lösung ist erfüllt, wenn das Ticket in „gelöst“ steht und im Zyklus mindestens eine Antwort gesendet wurde, zum späteren der beiden Zeitpunkte (H-09); „verworfen“ beendet den Zyklus als Abbruch.
12. Rückkehr aus „gelöst“ in einen offenen Status beginnt einen neuen Zyklus mit neuem Budget (S-10); eine Kundenmail allein öffnet nicht wieder.
13. Prioritätswechsel: neues Budget minus verbrauchte Zeit, bei negativem Rest ab diesem Moment verletzt (S-09); erfüllte Uhren bleiben erfüllt.
14. Das Ticket zeigt je Uhr Zustand (läuft, pausiert, erfüllt, verletzt, abgebrochen), Fälligkeit mit Zeitzone, verbleibende Geschäftszeit, Regel und Kalender; während einer Pause nur das Restbudget.

**Ergebnis:** DEC-098, Migration V0030. Modul `sla` mit Kalendern, Regeln, Priorität und Zyklen mit je einer Uhr für Erstreaktion und Lösung, Vertrag [contracts/sla.openapi.yaml](contracts/sla.openapi.yaml), Beschreibung in [backend/README.md](backend/README.md#servicefristen-task-068). Der Helpdesk meldet `TicketCustomerChanged`, `ReplySent` und `CustomerAnswered` und stellt `CustomerDirectory` bereit; Tickets haben das Feld `priority`, die Projektkonfiguration je Status die SLA Art. Oberfläche: Seite „Servicefristen“, Abschnitt „Priorität und Fristen“ am Ticket, SLA Art in den Projekteinstellungen. Demodaten mit Kalender „Bürozeiten“ und Regel „Helpdesk Standard“.

**Prüfungen:** Backend vollständig gegen PostgreSQL 16 lokal grün (54 Testklassen, 443 Tests), darunter `SlaCalculationTest` mit 24 Tests, `SlaApiTest` mit 10 Tests, `SlaContractTest` mit 11 Routen und ein Test über den echten Mailweg in `HelpdeskApiTest`. Frontend `tsc`, 77 Einheitentests und 135 Browsertests grün. CI auf PR #222 grün, Bildprüfung mit Demodaten, Lifecycle Skript und Abbild nur in der CI.

**Review:** Keine eigene getrennte Prüfung. Die Codex Prüfung von TASK-070 und die Nachprüfung von TASK-071 haben die Fristuhren nur dort gelesen, wo die Eskalationen sie nutzen; die Nachprüfung weist ausdrücklich darauf hin, dass für TASK-068 keine eigene Prüfung vorliegt. Holger hat auf diese Frage hin ohne sie abgenommen.

**Abnahme:** Holger, 2026-11-01, im Projektgespräch mit „gleich abnehmen“, zusammen mit TASK-070 und TASK-071; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben** (nicht beauftragt): Eine geänderte SLA Art wirkt erst beim nächsten Statuswechsel eines Tickets; eine später angelegte Regel gilt nicht für Tickets, deren Kunde schon gesetzt war; ein Kundenwechsel von A nach B behält den laufenden Zyklus; laufende Zyklen werden nicht neu bewertet. Eine getrennte technische Prüfung von TASK-068 kann später nachgeholt werden.

**Referenz:** Reservierung #220 (f55568c), Ergebnis #222 (9f0abba); verknüpft mit TASK-070 und DEC-098.

## TASK-066 Befund F1 und Hinweis H2 der Codex Prüfung von TASK-065 beheben

**Bezug:** REQ-OPS-006 (Hintergrundaufträge). **Verantwortlich:** claude-20261030-jobsfix-04. **Abgeschlossen:** 2026-10-30.

**Abnahmekriterien:** Die neuen Browsertests sind mit dem Stand nach TASK-065 rot, sofern sie eine Lücke des Produkts zeigen, und mit der Korrektur grün; alle Frontend und Browsertests grün. Holger nimmt TASK-065 und TASK-066 zusammen ab.

**Ergebnis:** DEC-096. `JobsPage` zählt je Listenabruf und je Abruf der Übersicht eine eigene Nummer (`listRequest`, `summaryRequest`) und übernimmt nur die Antwort des zuletzt gestarteten Abrufs; das ersetzt die Laderunden aus TASK-065. `jobs.test.mjs` hat eine gemeinsame Hilfe `hold`, die passende Listenabrufe zurückhält und beim Freigeben bis zum Eintreffen der Antwort wartet, und zwei neue Tests (verspätete ältere Seite nach Filterwechsel, verspätetes erstes „Aktualisieren“).

**Prüfungen:** Frontend `npm run verify` mit 77 Tests und `npm run test:browser` mit 131 Tests grün; `jobs.test.mjs` dreimal hintereinander grün. Mit der Seite vor TASK-065 (main 72920f1) sind alle drei Tests der verspäteten Antworten rot; mit der Seite nach TASK-065 sind sie grün, weil die Laderunden diese Fälle schon abdeckten. Die Absicherung überlappender Abrufe derselben Runde (F1) ist über die Oberfläche nicht erreichbar, weil „Ältere Aufträge laden“ während eines Abrufs gesperrt ist, und hat deshalb keinen eigenen roten Browsertest. Backend unverändert, nicht erneut ausgeführt.

**Review:** Keine weitere getrennte Prüfung; Holger hat auf die Empfehlung hin ohne sie abgenommen, weil TASK-066 nur die Jobübersicht und ihre Tests ändert und die CI grün ist.

**Abnahme:** Holger, 2026-10-30, im Projektgespräch mit „Dann sind abgenommen“ auf die Empfehlung, TASK-065 und TASK-066 jetzt zusammen abzunehmen; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben** (gemeinsam für TASK-065 und TASK-066, nicht beauftragt):
1. Aus der Codex Nachprüfung von TASK-054 ([Bericht](docs/REVIEW_TASK-054_PR-179.md)): H1 (nach Verlust der Reservierung kann ein alter Versuch neben einem neuen weiterlaufen; Bearbeiter halten ihre Wirkung unter der Auftragskennung fest), H2 (Prozessuhren, Lebensdauer der Dedup-Schlüssel, Wiederholung alter wiederkehrender Aufgaben), H3 (Nutzdaten in `job.payload` liegen in der Verantwortung des Aufrufers), H4 (README Satz zum Ausführer im Webprozess und Kommentar in V0025).
2. Aus der Codex Prüfung von TASK-065: H1 (eine alte Antwort, die vor dem Start des neuen Abrufs ankommt, steht bis zu dessen Antwort kurz da).

**Referenz:** Reservierung mit Prüfbericht #215 (375c142), Ergebnis #216 (3c3ed3f); verknüpft mit TASK-065 und DEC-096.

## TASK-065 Befunde F1 und F2 der Codex Nachprüfung von TASK-054 beheben

**Bezug:** REQ-OPS-006 (Hintergrundaufträge), REQ-HD-005 (keine Mehrfachaktion nach Neustart oder Wiederholung). **Verantwortlich:** claude-20261030-jobsfix-03. **Abgeschlossen:** 2026-10-30.

**Abnahmekriterien:** Regressionstests mit echter Datenbank für beide Reihenfolgen verspäteter Ergebnisse (alter Erfolg vor neuem Fehler und alter Fehler vor neuem Erfolg) sowie für einen alten Versuch nach erneutem Einstellen durch die Verwaltung; ein Browsertest mit umgekehrter Antwortreihenfolge beim Filterwechsel; alle Backend Tests einschließlich der Helpdesk Tests, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt ab.

**Ergebnis:** DEC-095. F1: `JobRepository.claim` vergibt je Übernahme eine eigene Kennung in `lease_owner` (Ausführer, `#`, Zufallskennung); `renew`, `succeed`, `fail` und `giveBack` prüfen diese Kennung, `JobExecutor` führt seine laufenden Versuche je Kennung. F2: `JobsPage` zählt Laderunden und verwirft Antworten älterer Runden für Liste und Übersicht. README des Backends beschreibt die Grenze gleichzeitiger Versuche nach Verlust der Reservierung genauer.

**Prüfungen:** Backend `./mvnw verify` gegen lokales PostgreSQL 16 (JDK 21) mit 407 Tests grün, darunter drei neue Tests in `JobExecutorTest` (alter Erfolg vor neuem endgültigen Fehler mit Verlängerung des neuen Versuchs über die Reservierungsdauer hinaus, alter Fehler vor neuem Erfolg, alter Versuch nach erneutem Einstellen durch die Verwaltung) und alle 40 Helpdesk API Tests. Mit dem alten Verhalten (eine Kennung je Ausführer) sind alle drei neuen Tests rot. Frontend `npm run verify` mit 77 Tests und 129 Browsertests grün; der neue Browsertest hält die Antwort für „Gescheitert“ zurück und ist mit der alten Seite rot. Nicht lokal ausgeführt: PostgreSQL 18.6 im Container und `backend/scripts/lifecycle-check.sh` (keine Migration), beides läuft in CI.

**Review:** Getrennte Prüfung durch Codex ([Bericht](docs/REVIEW_TASK-065_PR-214.md), PR #215): Kern von F1 und F2 plausibel behoben, kein P1 oder P2, F1 P3 (Seitenabrufe derselben Laderunde nicht einzeln versioniert) und H2 (fehlende Browsertests) durch TASK-066 behoben, H1 bleibt Hinweis. Codex hat keine Tests ausgeführt.

**Abnahme:** Holger, 2026-10-30, im Projektgespräch mit „Dann sind abgenommen“ auf die Empfehlung, TASK-065 und TASK-066 jetzt zusammen abzunehmen; bestätigt durch Merge dieses Abschluss PR.

**Referenz:** Reservierung #213 (ca618a1), Ergebnis #214 (7dd7c84); verknüpft mit TASK-054, TASK-066 und DEC-095.

## TASK-064 Befund F1 und Hinweis H2 der Codex Prüfung von TASK-063 beheben

**Bezug:** REQ-HD-001 (Antworten an Kunden), REQ-TKT-005 (unklare Zustellung). **Verantwortlich:** claude-20261030-replyfix-03. **Abgeschlossen:** 2026-10-30.

**Abnahmekriterien:** Ein Dekodierungs Roundtrip Test für normale, lange, internationale und wie kodierte Wörter aussehende Betreffe; der Abbruchtest erzwingt seine Reihenfolge selbst; alle Backend, Frontend und Browsertests grün. Holger nimmt TASK-058, TASK-062, TASK-063 und TASK-064 zusammen ab.

**Ergebnis:** DEC-094, keine Migration. `SmtpMailSender.subject` sendet einen Betreff mit `=?` immer als kodierte Wörter, sodass er beim Empfänger unverändert ankommt. `ScriptedSmtp` liest im Modus `DROP_IN_DATA` die Mail bis zur Abschlusszeile, verwirft sie und beendet die Verbindung ohne Antwort; der Absender hat damit immer die ganze Mail geschrieben, unabhängig von seinen Puffern. Neue Tests `ScriptedSmtpTest` treiben den Testserver zeilenweise. Die Zeitreserve einer SMTP Antwort in `HelpdeskApiTest` steigt von 20 auf 60 Sekunden; die übrigen Reserven (Verbindungsaufbau 5 Sekunden gegen die 6 Sekunden von `SLOW_ACCEPT`, 30 Sekunden für das Ende eines freigegebenen Auftrags) bleiben, weil sie dort Absicht oder unkritisch sind. Beschreibung in backend/README.md und docs/HELPDESK_SLA.md.

**Prüfungen:** Backend `./mvnw verify` gegen PostgreSQL 16 mit 404 Tests ohne Fehler, darunter der neue Einheitentest `everySubjectArrivesAsItWasTyped` (acht Betreffe, Rückdekodierung über die Bibliothek und über die gesendete Kopfzeile) und drei neue Tests in `ScriptedSmtpTest` (Verbindungsende vor der Abschlusszeile, verworfene Mail ohne Antwort, vollständige Mail). Gegen den Stand vor der Korrektur ist der Betrefftest rot (`=?UTF-8?B?SGFsbG8=?=` kam als `Hallo` an). Frontend und Browsertests nicht ausgeführt, weil sich dort nichts ändert; sie laufen in CI. Nicht lokal ausgeführt: `backend/scripts/lifecycle-check.sh` mit Docker (keine neue Migration), läuft in CI.

**Review:** Getrennte Prüfung durch Codex ([Bericht](docs/REVIEW_TASK-064_PR-210.md), abgelegt mit diesem Abschluss PR): F1 und H2 behoben, kein neuer P1 oder P2 Befund, F1 P3 und H1 zur Testhärtung; Empfehlung gemeinsame Abnahme. Codex hat keine Tests ausgeführt.

**Abnahme:** Holger, 2026-10-30, im Projektgespräch mit der Wahl „Ja, jetzt abnehmen“ auf die Frage „Nimmst du TASK-058, 062, 063 und 064 jetzt zusammen ab?“ nach der Codex Prüfung von TASK-064; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben** (gemeinsam für TASK-058 und TASK-062 bis TASK-064, nicht beauftragt):
1. Aus der Claude Prüfung von TASK-058: H1 (eigene Antwort nur an der Message-ID erkannt), H2 (keine Wiederholungssicherheit der Bestätigung), H3 (Reihenfolge des Verlaufs nach Kopfdatum), H4 (kurzes Fenster für vorübergehende Fehler), H5 (Systemübergänge auf Personen gebucht), H6 (Testlücken), H7 (angehängte Kundenantworten fallen ohne Benachrichtigung nicht auf; braucht eine fachliche Entscheidung), H8 (Beschreibung der Sperrreihenfolge).
2. Aus der Codex Prüfung von TASK-058: H1 (Gruppenrechte bei der Bestätigung), H2 (Auftragsschlüssel sichert keine wiederholte HTTP Bestätigung), H3 (Übersicht kann offene Antworten ausblenden), H4 (Konfliktregel mehrerer Referenzen, Vertragsgrenzen), H5 (weitere Regressionen).
3. Aus der Codex Prüfung von TASK-062: H2 (bewusst enge Adressregel, auch Kleinschreibung des lokalen Teils), H3, H5 und die nicht garantierte Gesamtlaufzeit eines Auftrags aus C-F4.
4. Aus der Codex Prüfung von TASK-063: H1 (keine allgemeine Freigabe aller Sperrreihenfolgen) und H3 (Klärung über mehrere Transaktionen, neue Einträge hinter einer passierten Kennung erst im nächsten Lauf; `ScriptedSmtp.close` prüft das Threadende nicht).
5. Aus der Codex Prüfung von TASK-064: F1 P3 (Socket Selbsttests in `ScriptedSmtpTest` ohne Lesezeitlimit) und H1 (Offenprüfung vor der Abschlusszeile zu schwach); reine Testhärtung.
6. Ein Abbruch innerhalb der Maildaten bleibt für den Absender unklar (DEC-093).

**Referenz:** Reservierung #209 (a628d4f), Ergebnis #210 (a5e60ed); verknüpft mit TASK-063 und DEC-094.

## TASK-063 Befunde der Codex Prüfung von TASK-062 beheben

**Bezug:** REQ-HD-001 (erkennbare ausstehende Sendungen), REQ-HD-002 (Zuordnung von Antworten), REQ-TKT-005 (unklare Zustellung). **Verantwortlich:** claude-20261030-replyfix-02. **Abgeschlossen:** 2026-10-30.

**Abnahmekriterien:** Regressionstests für eine Wiederholung in der Jobverwaltung gleichzeitig mit der Klärung, lange Betreffe und Namen, beide Reihenfolgen der Kundengrenze, einen Abbruch im Inhalt der Mail, ein ungültiges `by` und mehr als 200 aktive Antworten vor einer verwaisten; alle Backend, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-058, TASK-062 und TASK-063 zusammen ab.

**Ergebnis:** DEC-093, keine Migration. `Jobs.active` liest den Auftrag mit geteilter Zeilensperre (`JobRepository.shareState`), Wiederholung und Abbruch der Jobverwaltung warten darauf; `SmtpMailSender` faltet den Betreff, zerlegt lange Folgen in kodierte Wörter, kürzt Namen auf 200 Zeichen, lehnt Steuerzeichen im Betreff ab und prüft jede Kopfzeile auf 998 Zeichen; ein ungültiges `by` endet als `helpdesk.job_invalid`; `ReplyWatch` geht seitenweise durch alle wartenden Antworten; `ScriptedSmtp` zählt nur vollständige Mails als übergeben (neuer Modus `DROP_IN_DATA`, Liste `broken`), schließt offene Verbindungen und wartet auf seinen Thread. Neuer Fehlercode `helpdesk.job_invalid` in Vertrag und Oberfläche. Beschreibung in backend/README.md und docs/HELPDESK_SLA.md.

**Prüfungen:** Backend `./mvnw verify` gegen PostgreSQL 16 mit 400 Tests, darunter vier neue API Tests (Wiederholung der Jobverwaltung gleichzeitig mit der Klärung in beiden Reihenfolgen, ungültiges `by`, 250 Antworten mit wartendem Auftrag vor einer verwaisten, Abbruch innerhalb der Maildaten), der neu geschriebene Test der Kundengrenze mit echtem Anhängen vor dem Kundenwechsel und zwei neue Einheitentests (lange Betreffe und Namen, Betreff mit Zeilenumbruch). Gegen den Stand vor der Korrektur (main ae1bd96) sind fünf der neuen Tests rot (Wiederholung, ungültiges `by`, viele Antworten, lange Kopfzeilen, Zeilenumbruch im Betreff); der Test der Kundengrenze und der Abbruch in den Daten sind dort grün, weil sie Testlücken schließen, keinen Fehler des Produkts. Frontend `npm run verify` mit 77 Tests und 128 Browser Tests grün. Nicht lokal ausgeführt: `backend/scripts/lifecycle-check.sh` mit Docker (keine neue Migration), läuft in CI.

**Review:** Getrennte Prüfung durch Codex ([Bericht](docs/REVIEW_TASK-063_PR-208.md), PR #209): F1 bis F4, H1 und H6 behoben, H4 verbessert, neuer F1 P3 (Betreff wie kodiertes Wort) durch TASK-064 behoben, Hinweise H1 bis H3.

**Abnahme:** Holger, 2026-10-30, im Projektgespräch mit der Wahl „Ja, jetzt abnehmen“ auf die Frage „Nimmst du TASK-058, 062, 063 und 064 jetzt zusammen ab?“ nach der Codex Prüfung von TASK-064; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** siehe TASK-064.

**Referenz:** Reservierung #207 (ae1bd96), Ergebnis #208 (86c0faa), Prüfbericht #209 (a628d4f); verknüpft mit TASK-062, TASK-064 und DEC-093.

## TASK-062 Befunde der Prüfungen von TASK-058 beheben

**Bezug:** REQ-HD-001 (erkennbare ausstehende Sendungen), REQ-HD-002 (Zuordnung von Antworten), REQ-TKT-005 (unklare Zustellung). **Verantwortlich:** claude-20261030-replyfix-01. **Abgeschlossen:** 2026-10-30.

**Abnahmekriterien:** Regressionstests für die drei Fälle verwaister Antworten mit echter Lease Freigabe, für beide Ergebnisreihenfolgen eines alten Versuchs nach einer neuen Runde, für das erneute Senden nach Austritt des Urhebers, für Adressen mit Kommentar und Zeilenumbruch, Gruppen und Steuerzeichen bei Postfach und Kontakt, für die Zeilenlänge mit 20 langen Message-IDs, für die Einordnung vor und nach dem Datenende sowie eine späte Annahme innerhalb des neuen Zeitlimits und für einen Kundenwechsel zwischen Vergleich und Anhängen in beiden Commit Reihenfolgen; die neuen Tests sind vor der Korrektur rot und danach grün; alle Backend, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-058 und TASK-062 zusammen ab.

**Ergebnis:** DEC-092, keine Migration. Neue wiederkehrende Aufgabe `helpdesk.replies` (`ReplyWatch`) und Klärung bei jeder Aktion einer Person mit dem Port `Jobs.active`; Ausgang eines Versuchs nur für den eigenen Auftrag unter der Sperre der Antwort; Auftrag mit Nutzlast `by` der bestätigenden Person; strenge einfache Adressen in `DirectoryService.normalizedAddress`, Namen ohne Steuerzeichen, geprüfte und gefaltete Message-IDs; `SmtpMailSender` unterscheidet die Phasen über das Datenende und nutzt `taskmanager.helpdesk.smtp-timeout` (Standard zehn Minuten), Zeitlimit des Auftrags fünf Minuten darüber; Sperre je Ticket für Kundenvergleich, Kundenwechsel und Nehmen aus der Prüfwarteschlange. Neue Fehlercodes `helpdesk.job_ended` und `helpdesk.send_broken` in Vertrag und Oberfläche. Beschreibung in backend/README.md und docs/HELPDESK_SLA.md.

**Prüfungen:** Backend `./mvnw verify` gegen PostgreSQL 16 mit 394 Tests, darunter neun neue API Tests in `HelpdeskApiTest` (verwaiste Antworten mit echter Lease Freigabe und Abbruch in der Jobverwaltung, Klärung bei Aktion vor der Aufgabe, beide Reihenfolgen alter Versuche, Austritt der verfassenden und der bestätigenden Person, Adressen bei Kontakt und Postfach, früher gespeicherte Adresse, Abbruch vor dem Datenende, späte Annahme nach sechs Sekunden bei fünf Sekunden Verbindungszeitlimit, Kundenwechsel in beiden Sperrreihenfolgen) und fünf Einheitentests in `SmtpMailSenderTest` (Adressen, Gruppen, Namen mit Zeilenumbruch, ungültige Message-IDs, 20 Referenzen mit je etwa 250 Zeichen). Gegen den Stand vor der Korrektur (main 42f2183) sind 13 der 14 neuen Tests rot; grün blieb dort nur die Ablehnung zusammengesetzter Adressen über die API, die schon der alte Parser abwies. Zusätzlich gezielt geprüft: ohne die Auftragsprüfung beim Ausgang wird der Reihenfolgetest rot. Frontend `npm run verify` mit 77 Tests und 128 Browser Tests grün (ein erster paralleler Lauf hatte einen Ausreißer im Designtest „a typed color is checked before it is shown“ unter Last, der Wiederholungslauf war vollständig grün). Nicht lokal ausgeführt: `backend/scripts/lifecycle-check.sh` mit Docker, ohne neue Migration unverändert, läuft in CI.

**Review:** Getrennte Prüfung durch Codex ([Bericht](docs/REVIEW_TASK-062_PR-206.md), PR #207): Korrekturen nachvollziehbar, F1 P2 (Klärung gegen Wiederholung der Jobverwaltung) und F2 bis F4 P3, durch TASK-063 behoben.

**Abnahme:** Holger, 2026-10-30, im Projektgespräch mit der Wahl „Ja, jetzt abnehmen“ auf die Frage „Nimmst du TASK-058, 062, 063 und 064 jetzt zusammen ab?“ nach der Codex Prüfung von TASK-064; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** siehe TASK-064.

**Referenz:** Reservierung #204 (42f2183) mit Nachtrag #205 (2751a28), Ergebnis #206 (e975fcb), Prüfbericht #207 (ae1bd96); verknüpft mit TASK-058, TASK-063 und DEC-092.

## TASK-058 Helpdesk Antworten über SMTP (Paket A9, Teil 2)

**Bezug:** REQ-HD-001 (Versand, erkennbare ausstehende Sendungen), REQ-HD-002 (Antworten zuordnen, Wiederholungen, Schleifen), REQ-HD-005, REQ-TKT-005 (interne Versandaufträge, unklare Zustellung). **Verantwortlich:** claude-20261028-helpdesk-01. **Abgeschlossen:** 2026-10-30.

**Abnahmekriterien:** Vorschlag der Sitzung, zur Prüfung:
1. Nur mit `helpdesk.reply`, `ticket.read` und den Leseregeln für `mail` und `contact`; Empfänger nur Adressen des Kontakts des Tickets, Absender nur sendende Postfächer des Projekts; ohne Kontakt 409. Die Oberfläche zeigt Empfänger und Absender vor der Bestätigung.
2. Je bestätigter Antwort genau ein Versandauftrag in derselben Transaktion wie Antwort, Historie und Audit, auch nach Neustart oder mit zwei Ausführern.
3. Vor dem Versand prüft der Auftrag Projekt, aktiven Urheber mit Mitgliedschaft, Postfach und Empfänger; eine geänderte Lage endet als nicht gesendet ohne Versand.
4. Vorübergehende Fehler vor der Übergabe werden wiederholt, endgültige Ablehnungen enden als nicht gesendet; eine fehlende Antwort des Servers nach der Übergabe und ein Versuch, der die Antwort noch im Versand findet, ergeben Versand unklar ohne automatische Wiederholung.
5. Erneut senden nur für nicht gesendete Antworten oder für unklare mit bestätigtem Duplikatrisiko; eine unklare lässt sich als angekommen festhalten.
6. Die Antwort des Kunden mit `In-Reply-To` oder `References` auf eine Antwort landet ohne neues Ticket im Ticket der Antwort nur bei bestätigtem Absender des Kunden des Tickets, sonst in der Prüfwarteschlange mit Verweis und Übernahme dorthin.
7. Keine Antwort auf automatische Mails; eine zurückkommende eigene Antwort wird als automatisch vermerkt. Keine Mailschleife mit einem Autoresponder.
8. Nicht angenommene Antworten sind für Personen mit `helpdesk.reply` im Projekt sichtbar; Protokolle enthalten keinen Text des Mailservers.

**Ergebnis:** Modul `helpdesk` mit `ReplyService`, `ReplySender`, `SmtpMailSender`, Migration V0028, Vertrag `contracts/helpdesk.openapi.yaml` (sechs neue Routen), Oberfläche am Ticket und in der Prüfwarteschlange, Beschreibung in backend/README.md und docs/HELPDESK_SLA.md, Entscheidung DEC-091.

**Prüfungen:** Backend `./mvnw verify` gegen PostgreSQL 16 mit 380 Tests, darunter sechs neue Tests der Antworten gegen GreenMail und `ScriptedSmtp` (Abbruch nach DATA, Ablehnung 550 und 451); Frontend `npm run verify` und 128 Browser Tests, davon vier neue. Nicht lokal ausgeführt: `backend/scripts/lifecycle-check.sh` mit Docker; die Migrationsliste ist angepasst, der Lauf erfolgt in CI.

**Review:** Zwei getrennte Prüfungen: Claude ([Bericht](docs/REVIEW_TASK-058_PR-202.md), PR #203, F1 P2, F2 bis F4 P3, Hinweise H1 bis H8) und Codex ([Bericht](docs/REVIEW_TASK-058_PR-202_CODEX.md), PR #205, F1 bis F4 P2, Hinweise H1 bis H5); alle Befunde durch TASK-062 bis TASK-064 behoben. Die geschärften Abnahmekriterien oben hat die Sitzung vorgeschlagen; Holger hat sie mit der Abnahme angenommen.

**Abnahme:** Holger, 2026-10-30, im Projektgespräch mit der Wahl „Ja, jetzt abnehmen“ auf die Frage „Nimmst du TASK-058, 062, 063 und 064 jetzt zusammen ab?“ nach der Codex Prüfung von TASK-064; bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** siehe TASK-064.

**Referenz:** Freigabe mit DEC-090 (#201, abbac70), Ergebnis #202 (ab9a6ee), Prüfberichte #203 (5ace9ab) und #205 (2751a28); verknüpft mit TASK-057, TASK-062 und DEC-091.

## TASK-061 Befunde S1 und S2 der Nachprüfung von TASK-060 beheben

**Bezug:** REQ-HD-002 (Duplikate, Zuordnung), REQ-ORG-002. **Verantwortlich:** claude-20261029-helpdeskfix-03. **Abgeschlossen:** 2026-10-29.

**Abnahmekriterien:** Regressionstest E1 des Prüfberichts: dieselbe Mail in alter und neuer Generation ergibt genau ein Ticket, auch in umgekehrter Reihenfolge; Regressionen für verschachtelte Gruppe, fehlendes Semikolon und nicht geschlossenen Anzeigenamen in To und Cc führen in die Prüfwarteschlange, auch über `headerOnly`; die neuen Tests sind vor der Korrektur rot und danach grün; alle Backend, Frontend und Browsertests grün, der Sitzungstest mehrfach hintereinander. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-057, TASK-059, TASK-060 und TASK-061 zusammen ab.

**Ergebnis:** DEC-089, präzisiert durch DEC-090; keine Migration, keine Vertragsänderung (PR #199):
- S1: `HelpdeskRepository.lockCurrent` sperrt die Postfachzeile und prüft Quelle und UIDVALIDITY in der Transaktion des Eintrags. Ab dem Commit eines Neustarts des Ordners oder einer neuen Quelle schreibt ein überholter Abruf weder Eingang noch Ticket noch Position. Berichtigung durch DEC-090: Die ursprünglich zugesagte ausbleibende Lesemarkierung gilt nicht; eine schon gespeicherte Mail kann der überholte Abruf noch auf dem Server als gelesen markieren (T1).
- S2: Empfängerzeilen und Gruppen werden streng gelesen; verschachtelte, nicht abgeschlossene Gruppen und offene Anzeigenamen führen zu `recipients_incomplete`, auch über `headerOnly`.
- H1: Der Testadapter `ScriptedMailbox` liest übergroße Mails über `ParsedMail.headerOnly`.
- H2: Der Browsertest zum Sitzungsende wartet, bis die Seite keine Anfragen mehr offen hat; der Fall „a right withdrawn in another browser…“ wartet auf das Verschwinden des Titelfelds.
- H3: verbindlicher Zeitpunkt in DEC-089, backend/README.md und dem Nachtrag in docs/HELPDESK_SLA.md.

**Prüfungen:** CI grün auf PR #199. Lokal (Java 21, PostgreSQL 16): 374 Backendtests, Frontend `npm run verify`, alle 124 Browsertests, Sitzungsende-Test achtmal hintereinander grün; die Regressionen zu S1 und S2 waren ohne die Korrektur rot. Die Nachprüfung lief die Backendsuite selbst: 374 Tests mit einem Fehler in `LdapLoginTest` (siehe Offen), isoliert grün. Nicht lokal: Java 25, PostgreSQL 18 und Image (CI).

**Review:** Getrennte Nachprüfung durch Codex ([Bericht](docs/REVIEW_TASK-061_PR-199.md), PR #200): S1 und S2 behoben, Befund T1 (P3, Lesemarkierung nach neuer Quelle), Hinweise H1 bis H3.

**Abnahme:** Holger, 2026-10-29, im Projektgespräch mit der Wahl „Abnehmen, H2 und H4 klären“ und danach „Beides so lassen“ (DEC-090); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:**
1. T1: Ein überholter Abruf kann eine schon gespeicherte Mail noch als gelesen markieren; als schwächere Zusage in DEC-090 festgehalten, Korrektur nicht beauftragt.
2. `LdapLoginTest.java:515` (vier gleichzeitige Erstanmeldungen) war im Gesamtlauf der Nachprüfung einmal rot; nicht untersucht.

**Referenz:** Reservierung #198 (4b720c6), Ergebnis #199 (c49a53f), Prüfbericht #200 (80144bf); verknüpft mit TASK-060, DEC-089 und DEC-090.

## TASK-060 Befunde R1 bis R3 der Nachprüfung von TASK-059 beheben

**Bezug:** REQ-HD-002 (Duplikate, Zuordnung), REQ-ORG-002, Feldrechte nach DEC-067. **Verantwortlich:** claude-20261028-helpdeskfix-02. **Abgeschlossen:** 2026-10-29.

**Abnahmekriterien:** Für jeden Befund ein Regressionstest nach der Nachstellung im Prüfbericht (E6, E1, E2), der vor der Korrektur rot und danach grün ist; nach überlappenden Abrufen über einen Wechsel der UIDVALIDITY wird die neue Mail übernommen; ein fremder Kundenkontakt in einer Gruppe in To oder CC führt in die Prüfwarteschlange, ebenso insgesamt 1001 Empfänger bei beliebiger Aufteilung auf To und CC, 1000 dagegen nicht; alle Backend, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-088, keine Migration und keine Vertragsänderung (PR #196):
- R1: Die Quelle ist die Abrufgeneration und wächst auch mit jedem neuen UIDVALIDITY; Beginn und Neustart eines Ordners gelten nur gegen den gelesenen Stand, die Position rückt nur für Quelle und UIDVALIDITY des Abrufs vor.
- R2: Adressgruppen werden in ihre Adressen aufgelöst, eine unlesbare Gruppe macht die Empfänger unvollständig.
- R3: Eine Grenze von 1000 Empfängern für To und Cc zusammen, auch beim Lesen nur des Kopfs.
- Testmodell der Browsertests: Kontakte in der Kundenauswahl nur mit Lese und Schreibregel für `contact`.
- Neuer Testadapter `ScriptedMailbox` für feste Abläufe des Abrufs.

**Prüfungen:** CI grün auf PR #196. Lokal (Java 21, PostgreSQL 16): 370 Backendtests, Frontend `npm run verify`, alle 124 Browsertests; die vier neuen Fälle in `HelpdeskApiTest` und der Browserfall „follows the field rules“ waren vor der Korrektur rot. Nicht lokal: Java 25, PostgreSQL 18 und Image (CI).

**Review:** Getrennte Nachprüfung durch Codex ([Bericht](docs/REVIEW_TASK-060_PR-196.md), PR #197): R1 bis R3 behoben, neue Befunde S1 (zweites Ticket aus überholtem Abruf) und S2 (nachsichtiges Lesen von Gruppen), beide über TASK-061 behoben; Hinweise H1 bis H3 ebenfalls in TASK-061.

**Abnahme:** Holger, 2026-10-29, gemeinsam mit TASK-057, TASK-059 und TASK-061 (DEC-090); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Keine eigenen; siehe TASK-061.

**Referenz:** Reservierung #195 (463c3e4), Ergebnis #196 (c22d6f7), Prüfbericht #197 (e4c7bef); verknüpft mit TASK-059, TASK-061 und DEC-088.

## TASK-059 Befunde F1 bis F5 der Prüfung von TASK-057 beheben

**Bezug:** REQ-HD-001 (geschützte Zugangsdaten), REQ-HD-002 (Duplikate, Zuordnung), REQ-ORG-002, Feldrechte nach DEC-067. **Verantwortlich:** claude-20261028-helpdeskfix-01. **Abgeschlossen:** 2026-10-29.

**Abnahmekriterien:** Für jeden Befund ein Regressionstest nach der Nachstellung im Prüfbericht (E2, E3, E4, E6, E7), der vor der Korrektur rot und danach grün ist; kein Passwort im Protokoll bei einem Server, der die Anmeldung spiegelt; keine doppelten Tickets bei mehr wieder aufgetauchten Nachrichten als dem Paketlimit; keine übergangene Nachricht nach Wechsel von Konto oder Ordner; keine Kontaktdaten ohne Leserecht auf `contact`; ein fremder Kunde an beliebiger Empfängerposition führt in die Prüfwarteschlange; alle Backend, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-057 und TASK-059 zusammen ab.

**Ergebnis:** DEC-087 mit Korrekturmigration V0027 (PR #193):
- F1: Protokoll nur mit Postfach, Code und Klassen der Ursachen, nie mit dem Text des Mailservers.
- F2: Abgleichgrenze `compare_until` am Postfach, Abgleich und Eintrag unter Sperre des Postfachs, gültig über alle Pakete, Fehler und Neustarts.
- F3: Quelle `source` am Postfach und in der Herkunft jeder Mail, Positionen nur für die aktuelle Quelle.
- F4: Kundenauswahl nach den Lese und Schreibregeln für `customer` und `contact`.
- F5: Alle Empfänger bis 1000 werden geprüft, sonst Grund `recipients_incomplete`.
- H1: Vertrauensvoraussetzung an den eigenen Empfangsserver dokumentiert.
- H3: stabile Browsertests des Helpdesks; Backendtests mit höchstens acht offenen Spring Kontexten (`src/test/resources/spring.properties`).

**Prüfungen:** CI grün auf PR #193. Lokal (Java 21, PostgreSQL 16.15): `./mvnw clean verify` mit 366 Tests, `scripts/lifecycle-check.sh`, Frontend `npm run verify`, alle 124 Browsertests, `helpdesk.test.mjs` dreimal hintereinander; die fünf neuen Fälle waren vor der Korrektur rot. Nicht lokal: Java 25, PostgreSQL 18 und Image (CI).

**Review:** Getrennte Nachprüfung durch Codex ([Bericht](docs/REVIEW_TASK-059_PR-193.md), PR #194): F1 bis F5 behoben, neue Befunde R1 bis R3, über TASK-060 behoben.

**Abnahme:** Holger, 2026-10-29, gemeinsam mit TASK-057, TASK-060 und TASK-061 (DEC-090); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Keine eigenen; siehe TASK-057 und TASK-061.

**Referenz:** Reservierung #192 (d65f05d), Ergebnis #193 (059de83), Prüfbericht #194 (6872405); verknüpft mit TASK-057, TASK-060 und DEC-087.

## TASK-057 Helpdesk Eingang über IMAP mit Prüfwarteschlange (Paket A9, Teil 1)

**Bezug:** REQ-HD-001 (Eingang, geschützte Zugangsdaten, erkennbare Verbindungsfehler), REQ-HD-002 (Duplikate, unbekannte Absender nach DEC-031, automatische Antworten und Schleifen beim Eingang), REQ-ORG-002 (Unternehmensgrenze), REQ-TKT-003 (nur Mailanhänge über das Modul `files`). **Verantwortlich:** claude-20261028-helpdesk-01. **Abgeschlossen:** 2026-10-29.

**Abnahmekriterien:** Eine Nachricht eines bestätigten bekannten Kontakts einer freigegebenen Organisation erzeugt genau ein Ticket mit Kunde, Kontakt, Text und Anhängen, auch nach erneutem Abruf, Neustart oder zwei gleichzeitigen Ausführern; ein unbekannter, nicht bestätigter oder mehrdeutiger Absender landet mit Grund in der Prüfwarteschlange und erzeugt kein Ticket; eine gefälschte From Adresse ohne `dmarc=pass` des eigenen Empfangsservers führt nie direkt zum Ticket; eine automatische Antwort erzeugt kein Ticket; ein Wechsel von UIDVALIDITY erzeugt keine Duplikate; die Prüfwarteschlange ist ohne `helpdesk.review` im Zielprojekt nicht sichtbar (403), eine doppelte Bearbeitung legt nichts doppelt an; Kennwörter erscheinen weder in Antworten noch im Protokoll noch unverschlüsselt in der Datenbank; ein falsches Kennwort oder ein nicht erreichbarer Server ist in der Postfachliste sichtbar; ohne Leserecht auf `mail`, `customer` oder `contact` erscheinen diese Felder weder in Ticket noch Historie noch Suche. Tests gegen GreenMail. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-086 mit Migration V0026 und Vertrag contracts/helpdesk.openapi.yaml (PR #189), korrigiert durch TASK-059 bis TASK-061:
- Modul `helpdesk` mit Kundenorganisationen, Kontakten und IMAP Postfächern; Kennwörter mit AES-GCM und `TASK_MANAGER_SECRET_KEY` verschlüsselt.
- Abruf als wiederkehrende Aufgabe im Modul `jobs`, Herkunft über Postfach, Quelle, UIDVALIDITY und UID; Mails bleiben auf dem Server.
- Absenderprüfung über `Authentication-Results` des eigenen Empfangsservers mit `dmarc=pass`; alles andere mit Grund in die Prüfwarteschlange, automatische Mails ohne Ticket.
- Prüfwarteschlange mit der Projektaktion `helpdesk.review`, Verwaltung mit dem Unternehmensrecht `helpdesk.manage`.
- Ticketfelder `customer`, `contact` und `mail` mit Feldrechten und Historie, Anhänge über den Port `AttachmentIntake` des Moduls `files`.
- Oberfläche Helpdesk, Prüfwarteschlange und Bereich „Kunde und Mails“ am Ticket, Katalog de und en.
- Abweichung, durch DEC-090 entschieden: Ohne `helpdesk.review` ist die Liste leer und eine einzelne Mail antwortet mit 404 statt 403 (H2). Betreff und Text stehen als Titel und Beschreibung im Ticket und folgen deren Feldrechten, nicht dem Feld `mail` (H4).

**Prüfungen:** CI grün auf PR #189 (Backend, Frontend, Image, Qualifikation, Repository). Lokal: `HelpdeskApiTest` gegen GreenMail und PostgreSQL, `SenderCheckTest`, `ParsedMailTest`, `SecretBoxTest`, `HelpdeskContractTest`, alle Backendtests mit `./mvnw verify` (Java 21, PostgreSQL 16), Frontend `npm run verify` und alle Browsertests einschließlich `helpdesk.test.mjs`. Nicht lokal: Java 25, PostgreSQL 18 und Image (CI); kein Test gegen einen produktiven Mailserver.

**Review:** Getrennte Prüfung durch Codex ([Bericht](docs/REVIEW_TASK-057_PR-189.md), PR #191): Befunde F1 bis F5 (alle P2), über TASK-059 behoben; Hinweise H1 und H3 in TASK-059, H2 und H4 durch Holger in DEC-090 entschieden, H5 und H6 offen. Nachprüfungen siehe TASK-059 bis TASK-061.

**Abnahme:** Holger, 2026-10-29, im Projektgespräch mit der Wahl „Abnehmen, H2 und H4 klären“ und „Beides so lassen“, gemeinsam mit TASK-059, TASK-060 und TASK-061 (DEC-090); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:**
1. H5: Größen und Zeitgrenzen ohne Last und Ressourcentest; eine als klein gemeldete Mail wird vollständig in den Speicher gelesen.
2. H6: ein Abrufauftrag arbeitet alle Postfächer nacheinander ab statt einer je Postfach; die Message-ID dient nur nach einem Wechsel von UIDVALIDITY dem Abgleich; ein Postfach ohne Passwort lässt sich auch ohne Schlüssel anlegen.
3. T1 und der LDAP-Test, siehe TASK-061.
4. TASK-058 (Antworten über SMTP) ist mit dieser Abnahme nicht mehr blockiert.

**Referenz:** Reservierung #188 (a4c2e0f), Ergebnis #189 (1d80f18), Prüfbericht #191 (71c0cbb); verknüpft mit TASK-058 bis TASK-061, DEC-086 und DEC-090.

## TASK-056 Befunde R1 bis R3 der Nachprüfung von TASK-055 beheben

**Bezug:** REQ-OPS-006, REQ-ACL-004 (nur der Teil Hintergrundprozess), REQ-HD-005 (nur die Grundlage: keine Mehrfachaktion nach Neustart oder Wiederholung). **Verantwortlich:** claude-20261028-jobsfix-02. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Die Experimente des Berichts zu TASK-055 laufen mit umgekehrter Erwartung als Regression grün: nach einem fatalen Error mit dauerhaft scheiterndem Vermerk wird der Ausgang aufgerufen (R1); ein beim Stopp gemeldeter endgültiger Fachfehler endet als `failed` mit seinem Code (R2); ein Bearbeiter, der seine Stoppunterbrechung erst nach der Rückkehr von `stop()` weitergibt, wird ohne Zählung zurückgegeben (R3); alle bestehenden Tests bleiben grün. Getrennte KI prüft nach (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-085, keine Migration (PR #185):
- R1: `JobExecutor.execute` versucht den Vermerk eines fatalen Errors bestmöglich und wirft den Error danach in jedem Fall weiter, so dass der Prozess endet.
- R2: Ein vom Stopp unterbrochener Versuch wird nur zurückgegeben, wenn weder ein Error noch `JobFailure.permanent` gemeldet wurde.
- R3: Jeder Versuch führt seinen `Watchdog` mit den Merkmalen Zeitlimit und Stopp; die Markierung gilt bis zum tatsächlichen Ende des Versuchs. Die Wartezeit des Stopps ist einstellbar (`stopGrace`, sonst 15 Sekunden).
- backend/README.md beschreibt die Grenzen des kooperativen Stopps, docs/DEMO.md trennt die 15 Sekunden des Ausführers von den 30 Sekunden von Docker.

**Prüfungen:** CI grün auf PR #185. Lokal (Java 21, PostgreSQL 16): ganze Backend Suite mit 343 Tests ohne Fehler; die drei neuen Regressionen in `JobExecutorTest` scheitern gegen den Stand vor TASK-056. Nicht lokal: Frontend, Browsertests, Imageprüfung und ein echter `docker compose stop`; Imageprüfung, PostgreSQL 18.6 und Java 25 prüft CI.

**Review:** Getrennte Nachprüfung durch Claude (claude-20261028-review-056-01, Bericht [docs/REVIEW_TASK-056_PR-185.md](docs/REVIEW_TASK-056_PR-185.md) in PR #186): R1 bis R3 behoben, kein neuer Befund, Hinweise H1 bis H3.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („dann sind die abgenommen“ nach dem Merge von PR #186); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:**
1. H1: Der bestmögliche Vermerk nach einem fatalen Error ist zeitlich nicht begrenzt; bei erschöpftem Pool oder hängender Verbindung kommt der Ausstieg spät oder gar nicht.
2. H2: Ein nicht fataler Error beim Stopp verbraucht jetzt den Versuch; so in DEC-085 gewollt.
3. H3: backend/README.md nennt weiter einen Ausführer im Webserver der Vorführumgebung (H6 aus TASK-054).
4. Ein Browsertest der Anhänge (`attachments.test.mjs`, Zählung der Liste) war in CI einmal unzuverlässig.

**Referenz:** Reservierung in PR #184 (cd25ff8), Ergebnis #185 (796acf1), Prüfbericht #186 (bf432fa); verknüpft mit TASK-054, TASK-055 und DEC-085.

## TASK-055 Befunde F1 bis F4 der Prüfung von TASK-054 beheben

**Bezug:** REQ-OPS-006, REQ-ACL-004 (nur der Teil Hintergrundprozess), REQ-HD-005 (nur die Grundlage: keine Mehrfachaktion nach Neustart oder Wiederholung). **Verantwortlich:** claude-20261028-jobsfix-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Die Experimente E1 bis E3 aus docs/review-evidence/task-054 laufen mit umgekehrter Erwartung als Regression grün: nach einem Error läuft ein weiterer Auftrag, ein Stopp im letzten Versuch lässt den Auftrag wartend mit unverändertem Versuchszähler, nach dem erneuten Einstellen sieht der Bearbeiter `repeated() = true`; compose.yaml setzt eine Stoppfrist über der Gnadenfrist; alle bestehenden Tests und die Imageprüfung bleiben grün. Getrennte KI prüft nach (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-084, keine Migration (PR #183), nach Holgers Zustimmung zur Empfehlung des Berichts:
- F1: Ein Error eines Bearbeiters wird vermerkt und beendet keinen Ausführerfaden mehr; nur `VirtualMachineError` außer `StackOverflowError` beendet nach dem Vermerk den Prozess mit Code 70.
- F2: Ein durch den Stopp unterbrochener Versuch zählt nicht (`JobRepository.giveBack`, `job.interrupted`, sofort fällig, auch nach dem letzten Versuch). Ein Absturz ohne geordneten Stopp verbraucht weiter einen Versuch.
- F3: `JobExecution.repeated` ergibt sich aus `attempt > 1` oder einem früheren Start; `started_at` bleibt beim erneuten Einstellen erhalten.
- F4: `stop_grace_period: 30s` für `app` und `worker` in compose.yaml.
- Vertrag `contracts/jobs.openapi.yaml`, backend/README.md, docs/DEMO.md und die Testattrappe der Browsertests angeglichen.

**Prüfungen:** CI grün auf PR #183. Lokal (Java 21, PostgreSQL 16): ganze Backend Suite mit 340 Tests ohne Fehler, `npm run verify` und alle 114 Browsertests. Nicht lokal: Imageprüfung und echter `docker compose stop`; Imageprüfung, PostgreSQL 18.6 und Java 25 prüft CI.

**Review:** Unabhängige Nachprüfung durch Codex (codex-20261028-task055-review-01, Bericht [docs/REVIEW_TASK-055_PR-183.md](docs/REVIEW_TASK-055_PR-183.md) in PR #184): F1 bis F4 im Kern behoben; neue Befunde R1 (P2) sowie R2 und R3 (P3), behoben mit TASK-056.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („dann sind die abgenommen“ nach dem Merge von PR #186); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Keine eigenen; die Befunde R1 bis R3 sind mit TASK-056 erledigt, die übrigen Grenzen stehen bei TASK-054 und TASK-056.

**Referenz:** Reservierung #182 (2862665), Ergebnis #183 (1666f4c), Prüfbericht #184 (cd25ff8); verknüpft mit TASK-054, TASK-056 und DEC-084.

## TASK-054 Dauerhafte Hintergrundaufträge (Paket A8)

**Bezug:** REQ-OPS-006, REQ-ACL-004 (nur der Teil Hintergrundprozess), REQ-ORG-002 (Unternehmensgrenze der Aufträge), REQ-HD-005 (nur die Grundlage: keine Mehrfachaktion nach Neustart oder Wiederholung). **Verantwortlich:** claude-20261027-jobs-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Ein in einer zurückgerollten Transaktion eingestellter Auftrag existiert nicht; ein bestätigter Auftrag wird genau einmal erfolgreich ausgeführt, auch mit zwei gleichzeitig laufenden Ausführern; ein Abbruch des Ausführers während der Ausführung führt nach Ablauf der Reservierung zu genau einer weiteren Ausführung, die den Wiederholungsnachweis sieht; ein immer scheiternder Auftrag erreicht nach der Höchstzahl den Zustand „endgültig gescheitert“, wird in Übersicht und Liste mit Fehlerkennung ohne Geheimnisse gezeigt und lässt sich mit `jobs.manage` erneut einstellen; ohne `jobs.manage` gibt es weder Übersicht noch Liste noch Aktion (403); eine wiederkehrende Aufgabe läuft mit zwei Prozessen nur einmal je Fälligkeit; der Aufräumlauf der Anhänge verhält sich wie vorher und seine Tests bleiben grün; Neustart von Webprozess und Ausführer verliert keinen bestätigten Auftrag. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-083 mit Migration V0025 (PR #179, Bestätigung der Rückstauanzeige in PR #180):
- Modul `jobs` mit dauerhaften Aufträgen in PostgreSQL, eingestellt über den Port `Jobs` in der Transaktion der Fachänderung, optionaler Eindeutigkeitsschlüssel je Unternehmen und Art.
- Ausführer mit `FOR UPDATE SKIP LOCKED`, befristeter Reservierung, Zeitlimit, Wiederholung mit wachsendem Abstand (5 Versuche ab 30 Sekunden, verdoppelt bis 1 Stunde) und Zustand „endgültig gescheitert“; die Auftragskennung dient als Wiederholungsnachweis.
- Wiederkehrende Aufgaben mit festem Abstand, einmal je Fälligkeit; der Aufräumlauf der Anhänge läuft darüber (`FilesCleanupTask`).
- Getrennter Prozess `worker` aus demselben Image; die Vorführumgebung startet ihn als eigenen Dienst mit eigener Speichergrenze.
- Unternehmensrecht `jobs.manage` mit Übersicht, Liste, erneutem Einstellen und Abbrechen im Audit, Seite Hintergrundaufträge in der Administration, Katalog de und en. Der Rückstau steht nach Holgers Bestätigung in der Übersicht und als Warnung im Protokoll, nicht in der Bereitschaftsanzeige.
- Aufbewahrung 7 Tage für erledigte, 30 Tage für gescheiterte und abgebrochene Aufträge, konfigurierbar.

**Prüfungen:** CI grün auf PR #179 und #180, darunter der Workflow `Image`. Lokal (Java 21, PostgreSQL 16): alle 44 Testklassen des Backends ohne Fehler, die Lebenszyklusprüfung mit dem Prozess `worker`, `npm run verify` und alle 114 Browsertests. Nicht lokal: die Imageprüfung (Docker Hub verweigerte die Basisimages); sie lief in CI.

**Review:** Getrennte Prüfung durch Claude statt Codex auf Holgers Wunsch (claude-20261027-review-054-01, Bericht [docs/REVIEW_TASK-054_PR-179.md](docs/REVIEW_TASK-054_PR-179.md) in PR #181): F1 (P2), F2 bis F4 (P3), behoben mit TASK-055 und nachgeprüft über TASK-056; Hinweise H1 bis H8.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („dann sind die abgenommen“ nach dem Merge von PR #186); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Die Hinweise H1 bis H8 des Berichts sind nicht beauftragt, unter anderem:
1. H1: Das Zeitlimit wirkt nur kooperativ; ein Bearbeiter, der die Unterbrechung nicht beachtet, verlängert seine Reservierung unbegrenzt.
2. H2: Reservierung und Ablauf nutzen die Uhren der Prozesse statt der Datenbank.
3. H3: Eine verlorene Reservierung wird vom Ausführer nicht bemerkt.
4. H4: Das erneute Einstellen einer wiederkehrenden Aufgabe prüft nicht auf einen laufenden Lauf derselben Art.
5. H5: Beim Herunterfahren kann Spring Bearbeiter vor dem Stopp des Ausführers zerstören.
6. H6: backend/README.md und der Kommentar in V0025 weichen von compose.yaml und dem Code ab.
7. H7: Der Eindeutigkeitsschlüssel bindet auch fertige Aufträge bis zur Aufbewahrung; vor dem ersten Mailverbraucher in der Javadoc festhalten.
8. H8: Ohne laufenden Ausführer findet kein Aufräumlauf statt; das gehört als Warnung in die Betriebsbeschreibung.

**Referenz:** Reservierung #178 (c3d66cd), Ergebnis #179 (dc99440) und #180 (23adfe3), Prüfbericht #181 (700d9d3); Korrekturen TASK-055 und TASK-056; verknüpft mit TASK-051 und DEC-083.

## TASK-053 Befunde der Prüfung von TASK-051 und der Nachprüfung von TASK-052 beheben

**Bezug:** REQ-TKT-003 (nur der Teil Dateien), REQ-DATA-003, REQ-SEC-001, REQ-ORG-003, REQ-TKT-001, REQ-API-001. **Verantwortlich:** claude-20261026-attachfix-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Die Rechte für Anhänge gelten in Oberfläche, API und Historie gleich, belegt mit beiden Fällen aus F1 im Browsertest; die Sicherungshinweise beantworten die Punkte aus F2; der Nachweis zu N1 aus docs/review-evidence/task-052 läuft als Regression grün; der Fall aus N2 lässt sich allein über den Dialog veröffentlichen; alle bestehenden Tests bleiben grün. Getrennte KI prüft nach (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-082, keine Migration (PR #175, Warnhinweis für Makrodokumente als Nachtrag in PR #176):
- F1: `writableFields` nennt `tags` und `attachments`; die Oberfläche bietet Hochladen und Entfernen genau nach der Schreibregel an.
- F2: Vollständige Sicherungshinweise in backend/README.md und docs/DEMO.md.
- F3 bis F8: Zusammenfassung mehrerer Uploads, erklärter Download Fehler mit Frage per HEAD, erweiterte Sperrliste, Protokoll der Speicherfehler getrennt vom Abbruch (400 `attachment.incomplete`), Grenze vom Server (`X-Attachment-Max-Bytes`), Angleichung von TASK-051 an DEC-080.
- N1: Ein ausdrücklich mitgegebener unbekannter Status beim Typwechsel ergibt 400 `status.unavailable`. N2: Eigene Zuordnungszeilen für einen wegfallenden Typ.
- Makrodokumente bleiben nach Holgers Entscheidung vom 27. Oktober 2026 erlaubt und tragen in der Liste einen Warnhinweis.

**Prüfungen:** CI grün auf PR #175 und #176. Lokal (Java 21, PostgreSQL 16.15): 311 Backendtests ohne Ausfall, `npm run verify` (77 Tests) und 106 Browsertests; der Browsertest zu N2 scheitert gegen den Dialog vor TASK-053. Nicht lokal: PostgreSQL 18.6, Java 25 und der Docker Lauf (CI); die Sicherungsbefehle in docs/DEMO.md wurden nicht ausgeführt.

**Review:** Getrennte Nachprüfung durch Claude (claude-20261027-review-053-01, Bericht [docs/REVIEW_TASK-053_PR-175.md](docs/REVIEW_TASK-053_PR-175.md) in PR #177): F1 bis F8, N1 und N2 behoben; R1 (P3) und Hinweise H1 bis H5.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:**
1. R1: Die Frage mit HEAD liest auf dem Server den ganzen Inhalt; nicht beauftragt.
2. H1: `tags` in `writableFields` prüft nur der Browsertest gegen die Attrappe.
3. H2: Weitere seltene ausführbare Endungen werden noch angenommen.
4. H3 bis H5: Makrohinweis hängt an der Endung, Ortsangabe in docs/DEMO.md, doppelter Download bei Doppelklick.

**Referenz:** Reservierung #174 (ee4f03e), Ergebnis #175 (2bc252a) und #176 (cf08a67), Prüfbericht #177 (9d75046); verknüpft mit TASK-051, TASK-052 und DEC-082.

## TASK-052 Befunde F1 bis F5 der Prüfung von TASK-044 beheben

**Bezug:** REQ-ORG-003, REQ-TKT-001, REQ-ACL-003, REQ-API-001. **Verantwortlich:** claude-20261026-configfix-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Die Prüftests E1 bis E5 aus docs/review-evidence/task-044 laufen in angepasster Form als Regression grün; E6 bleibt grün; bestehende Tests zu F-03 bis F-06 bleiben grün; F1 verrät in keiner Antwort, ob ein verborgenes Feld gefüllt ist; ein Typwechsel umgeht kein Pflichtfeld eines Übergangs. Getrennte KI prüft nach (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-081, keine Migration (PR #172):
- F1: Verlangt ein Übergang ein Feld, das die Person nicht lesen oder schreiben darf, antwortet der Server immer mit 409 `ticket.required_field_inaccessible`.
- F2: Ganzzahlfelder weisen Werte unter −(2^53 − 1) ab.
- F3: Ein Typwechsel behält den Status, wenn der neue Typ ihn kennt, sonst kommt das Ticket in den ersten Status des neuen Typs.
- F4: Statuszuordnung je Paar aus Typ und Status, auch im Dialog der Oberfläche.
- F5: Ein unbekanntes eigenes Feld ergibt 400 `customFields.unknown`.

**Prüfungen:** CI grün auf PR #172. Lokal (Java 21, PostgreSQL 16): 308 Backendtests ohne Ausfall, `npm run verify` (76 Tests), 102 Browsertests. E1 bis E5 als Regressionen in `ProjectConfigurationApiTest`. Nicht lokal: PostgreSQL 18.6, Java 25 und der Docker Lauf (CI).

**Review:** Getrennte Nachprüfung durch Claude (claude-20261026-review-052-01, Bericht [docs/REVIEW_TASK-052_PR-172.md](docs/REVIEW_TASK-052_PR-172.md) in PR #173): F1 bis F5 behoben; N1 und N2 (P3), behoben mit TASK-053.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Keine eigenen; N1 und N2 sind mit TASK-053 erledigt.

**Referenz:** Reservierung in PR #169 (6424595), Ergebnis #172 (2616bb5), Prüfbericht #173 (c7a5fbb); verknüpft mit TASK-044, TASK-053 und DEC-081.

## TASK-051 Dateien und Anhänge an Tickets (Paket A7)

**Bezug:** REQ-TKT-003 (nur der Teil Dateien), REQ-DATA-003, REQ-SEC-001. **Verantwortlich:** claude-20261025-files-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Ein berechtigtes Mitglied lädt eine Datei bis zur Grenze hoch, sieht sie in der Liste und lädt exakt dieselben Bytes herunter; zu große, gesperrte und leere Dateien werden mit stabilem Fehlercode abgelehnt, ohne dass Reste im Speicher oder in der Datenbank bleiben; ein Neustart oder Austausch des Anwendungscontainers mit demselben Volume behält alle Anhänge; ein Abbruch zwischen Ablegen und Zuordnung hinterlässt nur eine Datei, die der Aufräumlauf entfernt; wer das Ticket oder das Feld nicht lesen darf, erhält auch über den direkten Link 404 oder 403 nach der Rechtesemantik des Ticketkerns, ebenso nach Entzug des Rechts; Entfernen und Hinzufügen stehen in der Historie, sichtbar nur mit Leserecht am Feld; Dateiname und Inhalt können weder Pfade verlassen noch beim Download Skripte ausführen lassen; die Rechte gelten in Oberfläche, API und Historie gleich. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** DEC-080 mit Migration V0024 (PR #168):
- Modul `files` mit Speicherschnittstelle und Dateisystemadapter auf einem dauerhaften Verzeichnis, Upload mit Staging und atomarem Ablegen, Regeln für Name, Größe, Endung und Programmanfang, Aufräumlauf mit Quarantäne.
- Ticketfeld `attachments` mit getrennten Lese und Schreibregeln, Einträge in der Historie, Auslieferung nur als Download.
- Oberfläche im Ticket mit Liste, Auswahl und Ablegen, Herunterladen und Entfernen mit Rückfrage; Volume `files-data` im Vorführimage.
- Holger hat am 26. Oktober 2026 die Vorschläge angenommen (Nachtrag zu DEC-080, PR #170): 25 MB je Datei, kein Virenscanner in V1, nur interne Anhänge bis zum Helpdesk, Entfernen mit Frist von 7 Tagen.

**Prüfungen:** CI grün auf PR #168. Lokal: 303 Backendtests gegen PostgreSQL 16, `lifecycle-check.sh` mit 23 Migrationen, `npm run verify` (75 Tests), 102 Browsertests und der Demoablauf mit Neustart und unverändertem Anhang. Nicht lokal: PostgreSQL 18.6 und `image-check.sh` (CI), andere Dateisysteme, sehr große oder viele gleichzeitige Uploads, ein Virenscanner.

**Review:** Getrennte Prüfung durch Claude statt Codex (Bericht [docs/REVIEW_TASK-051_PR-168.md](docs/REVIEW_TASK-051_PR-168.md) in PR #171): F1 und F2 (P2), F3 bis F8 (P3), behoben mit TASK-053; Hinweise H1 bis H8.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Die Hinweise H1 bis H8 sind nicht beauftragt, unter anderem der Fokus nach dem Entfernen (H1), kein Abbruch eines laufenden Uploads (H2), Fehler im Stil einer Information (H3), kein `fsync` des Zielverzeichnisses (H6) und der Rückstrich als Pfadtrenner (H7). Der Nachweis einer konsistenten Sicherung und Wiederherstellung gehört zu A18, ein Virenscanner bleibt bei OPEN-014.

**Referenz:** Reservierung #167 (6eb4db0), Ergebnis #168 (37580d0), Entscheidung #170 (f829fe5), Prüfbericht #171 (01a5df4); Korrekturen TASK-053; verknüpft mit DEC-080.

## TASK-050 Befund R1 der Nachprüfung von TASK-049 beheben

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-UX-001. **Verantwortlich:** claude-20261025-reviewfix-03. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Nach erfolgreicher Selbstumstellung bleibt das Einmalpasswort sichtbar, auch wenn danach eine geschützte Anfrage 401 erhält oder eine Kontenaktion ausgelöst wird; die Seite bietet dann keine geschützten Aktionen mehr an; „Zur Anmeldung“ führt zur Anmeldeseite und entfernt das Passwort aus dem Speicher; die Umstellung eines fremden Kontos und das normale Ende einer Sitzung verhalten sich unverändert; die zweite Reihenfolge der Umstellung gegen die laufende Verzeichnisanmeldung ergibt 401 ohne Sitzung. Getrennte KI prüft die Behebung gezielt nach; Holger nimmt fachlich ab.

**Ergebnis:** DEC-079, keine Migration und keine Vertragsänderung (PR #166): eine eigene Seite oberhalb aller Seiten hält das Einmalpasswort nach der Selbstumstellung (`Handover` in `session.tsx`), bietet keine geschützten Aktionen an und entfernt das Passwort bei „Zur Anmeldung“; Regression für die zweite Reihenfolge der Umstellung mit dem Testersatz `PausableAccountRepository`.

**Prüfungen:** CI grün auf PR #166. Lokal: 276 Backendtests gegen PostgreSQL 16, `npm run verify` (75 Tests) und 95 Browsertests; die drei neuen Browsertests scheitern gegen die Seiten vor TASK-050. Nicht lokal: PostgreSQL 18.6 (CI), ein echtes Verzeichnisprodukt, ein durchgängiger Browserlauf am echten Backend.

**Review:** Keine eigene Nachprüfung von TASK-050; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Die Qualifikation mit dem vorgesehenen Verzeichnisprodukt bleibt eine eigene Aufgabe und braucht Holgers Auftrag.

**Referenz:** Reservierung #165 (0947847), Ergebnis #166 (6820bd4); Prüfbericht zu TASK-049 auf dem Branch review/task-049; verknüpft mit TASK-047, TASK-049 und DEC-079.

## TASK-049 Befunde der Nachprüfung von TASK-048 und der Prüfung von TASK-047 beheben

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-API-001. **Verantwortlich:** claude-20261024-reviewfix-02. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Die beiden Gegenbeispiele aus dem Bericht (Altschlüssel `hex:80` und `trim-id` auf einer befüllten V0022-Datenbank, danach Anmeldung eines anderen Eintrags mit kollidierendem Wert) führen nicht mehr zum Konto der anderen Person und ohne Rechte zu einem eigenen Konto oder zur Abweisung; das Altkonto selbst bleibt über sein Verzeichnis erreichbar oder ist sichtbar gesperrt, je nach der gewählten Lösung; der Anzeigename kommt auch bei gleichem Kennungs und Namensattribut an; eine laufende Verzeichnisanmeldung, die von einer Umstellung überholt wird, ergibt 401 ohne Sitzung und ohne verbrauchtes Einmalpasswort; bei der Selbstumstellung bleibt das Einmalpasswort sichtbar; bestehende Anmelde-, Umstell- und Sperrfälle bleiben grün. Getrennte KI prüft die Behebung gezielt nach; Holger nimmt fachlich ab.

**Ergebnis:** DEC-078 mit Migration V0023 (PR #164): Die Migration löst alle vorhandenen Verzeichniskonten vom Verzeichnis, deaktiviert sie und beendet ihre Sitzungen (N1); Prüfung von Kontotyp und Kennung nach der Kontosperre bei der Verzeichnisanmeldung (F1 aus TASK-047); Anzeigename aus den Rohbytes (N2); Selbstumstellung behält das Einmalpasswort (F2 aus TASK-047, vollständig erst mit TASK-050).

**Prüfungen:** CI grün auf PR #164. Lokal: 275 Backendtests gegen PostgreSQL 16, `lifecycle-check.sh` mit 22 Migrationen, `npm run verify` (75 Tests) und 93 Browsertests; die neuen Tests scheitern gegen den alten Code mit den berichteten Symptomen. Nicht lokal: PostgreSQL 18.6 (CI), ein echtes Verzeichnisprodukt.

**Review:** Getrennte Nachprüfung durch Codex (codex-20261025-task049-review-01, [Bericht](https://github.com/1uedev/task-manager/blob/review/task-049/docs/REVIEW_TASK-049.md) auf dem Branch review/task-049): N1, N2 und F1 behoben, F2 teilweise; R1 (P2), behoben mit TASK-050. Empfehlung „annehmbar nach Behebung von R1“.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Verzeichniskonten, die vor V0023 bestanden, sind deaktiviert und müssen von der Verwaltung wieder freigegeben werden. Die Qualifikation mit einem echten Verzeichnisprodukt steht aus.

**Referenz:** Reservierung #162 (bc22999), Ergebnis #164 (ad44fdb); verknüpft mit TASK-046 bis TASK-048, TASK-050 und DEC-078.

## TASK-048 Befunde F1 bis F4 der Prüfung von TASK-046 beheben

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-API-001. **Verantwortlich:** claude-20261024-reviewfix-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Zwei Personen mit verschiedenen binären Kennungen und verschiedenen Rechten erreichen je ihr eigenes Konto; bestehende Zuordnungen über Textkennungen bleiben gültig; ein bekannter Eintrag mit dem Namen eines anderen Kontos wird abgewiesen, die Umbenennung desselben Eintrags funktioniert weiter; die drei leeren Varianten der Verschlüsselung ergeben 400; StartTLS ohne Unterstützung der Gegenstelle ergibt `directory.tls_failed`; beschädigte Alt-Zuordnungen werden von der Migration abgesichert; bestehende Anmelde-, Umstell- und Sperrfälle bleiben grün. Getrennte KI prüft die Behebung gezielt nach; Holger nimmt fachlich ab.

**Ergebnis:** DEC-077 mit Migration V0022 (PR #160): verlustfreier Zuordnungsschlüssel binärer Kennungen (F1), Abweisung eines bekannten Eintrags mit dem Namen eines anderen Kontos (F2), 400 bei unvollständiger Verschlüsselung (F3), `directory.tls_failed` bei abgelehntem StartTLS (F4).

**Prüfungen:** CI grün auf PR #160. Lokal: 272 Backendtests gegen PostgreSQL 16, `lifecycle-check.sh` mit 21 Migrationen, `npm run verify`; die vier Regressionen scheitern gegen den alten Code. Nicht lokal: PostgreSQL 18.6 (CI), ein echtes Verzeichnisprodukt.

**Review:** Getrennte Nachprüfung durch Codex (codex-20261024-task048-047-review-01, [Bericht](https://github.com/1uedev/task-manager/blob/review/task-048-nachpruefung/docs/REVIEW_TASK-048_NACHPRUEFUNG.md), PR #161): F2 bis F4 behoben, F1 teilweise; N1 (hoch) und N2 (niedrig), behoben mit TASK-049.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Keine eigenen; N1 und N2 sind mit TASK-049 erledigt.

**Referenz:** Reservierung #159 (f858f56), Ergebnis #160 (1274c31), Prüfbericht PR #161; verknüpft mit TASK-046, TASK-049 und DEC-077.

## TASK-047 Interne Verwaltung bleibt erhalten und Umstellung von Verzeichniskonten (Nachtrag zu DEC-075)

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-AUTH-005, REQ-API-001, REQ-UX-001. **Verantwortlich:** claude-20261023-localadmin-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Das letzte aktive lokale Konto mit den beiden Verwaltungsrechten lässt sich weder deaktivieren noch durch eine Rechteänderung entkleiden, auch nicht durch gleichzeitige Änderungen, solange Verzeichniskonten dieselben Rechte halten; ein Verzeichniskonto lässt sich umstellen und meldet sich danach nur noch mit dem neuen Einmalpasswort an, behält seine Rechte und ist im Protokoll vermerkt; bei ausgefallenem Verzeichnis kann die lokale Verwaltung Konten einrichten und umstellen; bestehende Anmelde-, Sperr- und Rechtefälle bleiben grün. Getrennte KI prüft Sperre und Umstellung; Holger nimmt fachlich ab.

**Ergebnis:** DEC-076, keine Migration (PR #157): Sperre der letzten lokalen Verwaltung mit `permissions.manage` und `accounts.manage` (409 `permissions.last_local_administrator`), `POST /accounts/{id}/convert-to-local` mit neuem Einmalpasswort, Schaltfläche mit Bestätigung in der Kontenverwaltung, Backlog Eintrag für den Zertifikatswechsel ohne Neustart in ROADMAP.md.

**Prüfungen:** CI grün auf PR #157. Lokal: 262 Backendtests gegen PostgreSQL 16, `npm run verify`, 92 Browsertests. Nicht lokal: PostgreSQL 18.6 (CI), ein echtes Verzeichnis, gleichzeitige Umstellung und Deaktivierung unter Last.

**Review:** Getrennte Prüfung durch Codex (codex-20261024-task047-review-01, [Bericht](https://github.com/1uedev/task-manager/blob/review/task-047/docs/REVIEW_TASK-047.md), PR #163): die relative Sperre hält; F1 (hoch) und F2 (mittel), behoben mit TASK-049 und TASK-050.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Die Sperre ist relativ: Sie greift nur, wenn es vorher eine lokale Verwaltung gab. Eine Umstellung lokaler Konten ins Verzeichnis und eine automatische Umstellung sind nicht Teil.

**Referenz:** Reservierung #156 (434592f), Ergebnis #157 (a1426fb), Prüfbericht PR #163; verknüpft mit TASK-046, TASK-049, TASK-050 und DEC-076.

## TASK-046 LDAP Anmeldung (Paket A5)

**Bezug:** REQ-AUTH-002, REQ-AUTH-003, REQ-API-001, REQ-UX-001. **Verantwortlich:** claude-20261023-ldap-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Eine konfigurierte Anbindung lässt sich prüfen; ein LDAP Konto entsteht beim ersten Login, wird danach über die Kennung wiedergefunden und hat keine Rechte; eine gleichnamige lokale Identität übernimmt nie ein LDAP Konto und umgekehrt; Eingaben mit Sonderzeichen können weder den Suchfilter noch den Namen einschleusen; bei Ausfall scheitert nur die LDAP Anmeldung mit 503, ohne lokale Konten zu berühren; kein Passwort und kein Bindungsgeheimnis steht in Antworten, Protokoll oder Datenbank; bestehende Anmelde-, Pflichtwechsel- und Zurücksetzenfälle bleiben grün. Getrennte KI prüft Anmeldung und Zuordnung; Holger nimmt fachlich ab.

**Ergebnis:** DEC-075 mit Migration V0021 (PR #155): eine LDAPv3 Anbindung mit Prüfung, Anmeldung durch Bindung als Person, Zuordnung über die unveränderliche Kennung, Anlage ohne Rechte beim ersten Login, 503 bei Ausfall, Oberfläche in der Kontenverwaltung.

**Prüfungen:** CI grün auf PR #155. Lokal: 259 Backendtests gegen PostgreSQL 16 mit `LdapLoginTest` und `LdapTlsTest` gegen ein im Test gestartetes Verzeichnis, `lifecycle-check.sh`, `npm run verify` und 90 Browsertests. Nicht lokal: PostgreSQL 18.6 (CI), ein echtes Verzeichnisprodukt, sehr große Verzeichnisse.

**Review:** Getrennte Prüfung durch Codex (codex-20261023-task046-review-01, [Bericht](https://github.com/1uedev/task-manager/blob/review/task-046-pr-155/docs/REVIEW_TASK-046_PR-155.md), PR #158): F1 (hoch), F2 (mittel), F3 und F4 (niedrig), behoben mit TASK-048 und nachgeprüft über TASK-049.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:**
1. Die Qualifikation mit einem echten Verzeichnisprodukt (OpenLDAP, Active Directory) steht aus und braucht Holgers Auftrag.
2. Zertifikatswechsel ohne Neustart ist im Backlog (DEC-076).
3. Gruppenabbildung, Abgleich, Passwortverwaltung im Verzeichnis und ein zweites Verzeichnis sind nicht Teil.

**Referenz:** Reservierung #154 (a022836), Ergebnis #155 (6e40ebf), Prüfbericht PR #158; Korrekturen TASK-047 bis TASK-050; verknüpft mit DEC-075.

## TASK-045 Kontoverwaltung ergänzen (Rest von Paket A4)

**Bezug:** REQ-AUTH-001, REQ-PORTAL-003, REQ-API-001, REQ-UX-001. **Verantwortlich:** claude-20261023-accounts-01. **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Der Zustand des Einmalpassworts ist in API und Oberfläche richtig für offen, abgelaufen und verbraucht und verrät nie das Passwort; Anzeigename und Aufheben der Wartezeit verlangen `accounts.manage`, wirken sofort und stehen im Protokoll; das Protokoll lässt sich über Seitengrenzen lesen und enthält keine Geheimnisse; bestehende Anmelde-, Pflichtwechsel- und Zurücksetzenfälle bleiben grün. Getrennte KI prüft Anmeldung und Protokoll; Holger nimmt fachlich ab.

**Ergebnis:** DEC-074 (PR #153): Zustand des Einmalpassworts in der Kontenliste, Ändern des Anzeigenamens, Lesen des Protokolls der Kontenverwaltung und Aufheben der Wartezeit nach Fehlanmeldungen, jeweils mit Oberfläche und Vertrag identity.openapi.yaml.

**Prüfungen:** CI grün auf PR #153. Lokal: Backendtests gegen PostgreSQL 16 mit vier neuen Integrationstests in `IdentityApiTest`, `npm run verify`, 83 Browsertests. Nicht lokal: PostgreSQL 18.6 und der Workflow `Image` (CI).

**Review:** Keine eigene getrennte Prüfung von TASK-045; Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Selbstbedienung per Mail, Portalkonten und zweiter Faktor sind nicht Teil (DEC-058).

**Referenz:** Reservierung #152 (4615e60), Ergebnis #153 (f4f62b7); verknüpft mit TASK-024, TASK-040 und DEC-074.

## TASK-044 Projektkonfiguration mit Tickettypen, Abläufen und eigenen Feldern

**Bezug:** REQ-ORG-003, REQ-ORG-004, REQ-TKT-001, REQ-ACL-003, REQ-API-001, REQ-UX-001. **Verantwortlich:** claude-20261023-projectconfig-02 (Übernahme der Reservierung von claude-20261023-projectconfig-01). **Abgeschlossen:** 2026-10-28.

**Abnahmekriterien:** Zwei Projekte mit unterschiedlichen Typen, Abläufen und Feldern nach den Beispielen A und B aus docs/DOMAIN_MODEL.md sind einrichtbar und bedienbar; die Prüffälle F-03 bis F-06 sind als Regression abgedeckt; ein nicht erlaubter Übergang und ein fehlendes Pflichtfeld lassen das Ticket unverändert; verborgene eigene Felder bleiben in API, Historie und Suche verborgen; Bestandstickets bleiben nach jeder Veröffentlichung gültig. Getrennte KI prüft Konfigurationsänderungen, Rechte und Bestandsdaten; Holger nimmt fachlich ab.

**Ergebnis:** DEC-073 mit den Migrationen V0018 bis V0020 (PR #151), beschrieben in [docs/PROJECT_CONFIGURATION.md](docs/PROJECT_CONFIGURATION.md): Tickettypen, Abläufe je Typ, Pflichtfelder je Übergang, sieben Arten eigener Felder, Entwurf und Veröffentlichung mit Zuordnung von Bestandstickets, eigene Felder in Rechten, Historie und Suche, Oberfläche dazu.

**Prüfungen:** CI grün auf PR #151. Lokal: 235 Backendtests gegen PostgreSQL 16, `npm run verify`, 78 Browsertests; F-03 bis F-06 als Regression. Nicht lokal: PostgreSQL 18.6 und der Workflow `Image` (CI).

**Review:** Getrennte Prüfung durch Claude statt Codex (claude-20261025-review-044-01, Bericht [docs/REVIEW_TASK-044_PR-151.md](docs/REVIEW_TASK-044_PR-151.md) in PR #169): F1 (P2), F2 bis F5 (P3), behoben mit TASK-052 und TASK-053. Die Veröffentlichung mit Zuordnung unter Last blieb ohne Befund.

**Abnahme:** Holger, 2026-10-28, im Projektgespräch („ja dsa gilt auch für die anderen offenen tasks“ im Anschluss an die Abnahme von TASK-054 bis TASK-056); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:**
1. Die drei offenen Punkte aus DEC-073 sind nicht entschieden: Löschen nie verwendeter Felder, nachträgliches Erzwingen von Pflichtfeldern bei Bestandstickets, Direktveröffentlichung neben dem Entwurf.
2. Filter auf eigene Felder in der Suchoberfläche, Mehrfachauswahl, Dezimalzahl, Formulare, Automationen, Projektvorlagen, Verschieben zwischen Projekten und Archivierung sind nicht Teil.

**Referenz:** Reservierung #150 (47eae91), Ergebnis #151 (8b7928d), Prüfbericht #169 (6424595); Korrekturen TASK-052 und TASK-053; verknüpft mit DEC-073.

## TASK-043 Rechtekern härten

**Bezug:** REQ-ACL-001, REQ-ACL-002, REQ-ACL-003, REQ-ACL-004, REQ-ACL-005, REQ-API-001, REQ-UX-001. **Verantwortlich:** claude-20261022-hardening-01. **Abgeschlossen:** 2026-10-23.

**Abnahmekriterien:** Jeder umgesetzte Punkt hat eine Regression, die ohne Korrektur fehlschlägt; ein zwischen Sperre und Commit entzogenes Recht verhindert die Änderung; Projektverwaltungen sehen nur die Gruppen, die sie delegieren dürfen; das Protokoll ist über 100 Einträge hinaus erreichbar. Getrennte KI prüft Rechtegrenzen und Nebenläufigkeit; Holger nimmt fachlich ab.

**Ergebnis:** DEC-072. Umfang nach Holgers Wahl „Alle fünf“ (PR #146):
- P2: Das Rechteprotokoll blättert mit `before` über die Rechteversion; jeder Eintrag nennt den ersetzten Zustand (Migration V0016). Die Gruppenverwaltung zeigt das Protokoll seitenweise.
- P3: Externe Konten verwalten nie Rechte.
- P4: Ein doppelter Gruppenname, ohne Rücksicht auf Groß- und Kleinschreibung, antwortet mit 409 `permissions.group_name_taken`. Sperrfehler sind als wiederholbar dokumentiert.
- P5: Die Sperrwartezeit von fünf Sekunden gilt nur für die Rechteprüfung.
- P6: Wirkungslose Änderungen schreiben weder eine neue Rechteversion noch einen Protokolleintrag.
- Prüfung beim Commit: `require` vergleicht das Sitzungsende unmittelbar vor dem Commit erneut mit der Uhr.
- Lesepfad `GET /api/v1/permissions/projects/{projectId}/groups` mit dem Reiter Rechte im Projekt.
- Restpunkte F1 bis F3 aus TASK-036 und F1 aus TASK-042.

Nachbesserung nach unabhängiger Prüfung (PR #148):
- F1: Ein animiertes WebP braucht den Animationskopf und eigene Bilddaten je Bild, sonst 415 `appearance.logo_unreadable`.
- F2: Die Rechteendpunkte mit Projektbezug beantworten ein verborgenes Projekt wie ein fehlendes mit 404 `project.not_found`.
- F3: Mit Logo bleibt der Markenname für Bildschirmleser erhalten.
- F4: `previousRecorded` (Migration V0017, Vertrag 0.3.0) unterscheidet „vorher nichts“ von „nicht erfasst“.

**Prüfungen:** CI mit Backend, Frontend, Qualifikation, Repository Prüfungen und Workflow `Image` grün auf PR #146 und #148; die Lebenszyklusprüfung wurde auf V0016 und V0017 nachgeführt. Zuletzt lokal grün: 218 Backendtests gegen PostgreSQL 16, Frontend `npm run verify`, 75 Browsertests und die Lebenszyklusprüfung gegen eine leere Datenbank. Jede neue Regression wurde gegen einen Stand ohne ihre Korrektur ausgeführt und schlägt dort fehl. Ausnahme ist die Backendregression zu F4: Sie setzt die neue Spalte voraus; ihre Browserregression ohne Korrektur ist rot.

**Review:** Unabhängige Prüfung durch Codex (codex-20261022-task043-review-01, Bericht `docs/REVIEW_TASK-043_PR-146.md` in PR #147): F1 (P2), F2 bis F4 (P3). Ohne Befund blieben Rechtegrenzen der Delegation, externe Konten, doppelte Namen, Sperrwartezeit, Prüfung beim Commit, Tagzeitpunkt, wirkungslose Änderungen und Protokollblättern. Eine Nachprüfung der Korrekturen hat Holger ausdrücklich nicht verlangt.

**Abnahme:** Holger, 2026-10-23, im Projektgespräch („keine nachprüfung und ist abgenommen“ nach dem Merge von PR #148); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:**
1. P7, also ob eine Projektverwaltung die Projektregeln jeder Gruppe ändern darf, bleibt Holgers Entscheidung.
2. Die Messung der Zeilensperre bei `inspect` und die unternehmensweite Ungültigkeit aller Kontexte aus P6 stehen aus.
3. Aufbewahrung und Schutz des Protokolls bleiben bei OPEN-014.
4. WebP wird nur auf seinen Aufbau geprüft, nicht vollständig dekodiert.
5. Es gab keinen manuellen Test mit einem Bildschirmleser.
6. Einträge zwischen V0016 und V0017 ohne erfassten Vorzustand gelten vorsichtig als nicht erfasst.

**Referenz:** Reservierung #145 (d04926b), Ergebnis #146 (a210423), Prüfbericht #147, Nachbesserung #148 (bc15ea8); verknüpft mit TASK-025, TASK-030, TASK-036, TASK-042 und DEC-072.

## TASK-030 Durchgängige Oberfläche und minimale Administration integrieren

**Bezug:** REQ-PROD-002, REQ-TKT-001, REQ-TAG-001, REQ-SRCH-001, REQ-I18N-001, REQ-UX-001, REQ-ACL-001, REQ-ACL-003. **Verantwortlich:** claude-20261021-ui-01. **Abgeschlossen:** 2026-10-22.

**Abnahmekriterien:** Administrative Kontoanlage und verpflichtender Erstwechsel sind bedienbar; direkter Fachzugriff vor dem Wechsel wird auch serverseitig abgewiesen. Zwei unterschiedlich berechtigte Benutzer durchlaufen den Ablauf in DE/EN. Ablaufende, verlorene Rechte, Konflikte und ungespeicherte Eingaben entsprechen dem ausgewählten UX-Vertrag. Direkte API-Negativfälle bleiben geschützt. Getrennte KI prüft Ablauf und sichtbare Grenzen; echte Browserprüfung dokumentiert.

**Ergebnis:** DEC-070. Teil 1 im Backend (PR #132): Gruppen, Mitglieder, Aktionen je Unternehmen oder Projekt und Feldregeln werden über `/api/v1/permissions` mit dem Recht `permissions.manage` verwaltet, das Protokoll mit `permissions.audit.read`; Schutz der letzten Verwaltung (409 `permissions.last_administrator`), Kontoverzeichnis `GET /api/v1/directory/accounts`, Verträge `contracts/permissions.openapi.yaml` und Ergänzungen in `contracts/identity.openapi.yaml`, keine Migration. Teil 2 in der Oberfläche (PR #133, DEC-070 Nachtrag 8 bis 13): Anmeldung mit Pflichtwechsel des Einmalpassworts, Konten mit Einmalpasswort, Projekte mit Mitgliedern, Tags und Einstellungen, Tickets mit Feldrechten, Historie und Konfliktbehandlung, Suche, Gruppen und Rechte sowie Erscheinungsbild, in Deutsch und Englisch; Schutz ungespeicherter Eingaben und Rückkehr zur Anmeldung bei Sitzungsende. Nachbesserung nach unabhängiger Prüfung (PR #141, DEC-070 Nachtrag 14): Historie und Tags laden nach einer Ablehnung beim Speichern neu (F1), Eingaben während einer Speicherung bleiben erhalten (F2), Anlegeformulare fragen vor dem Verlassen (F3), nach der Abmeldung wird das CSRF Cookie neu ausgestellt (F4), Sperrfehler der Rechteverwaltung antworten mit 503 `permissions.busy` (F5), jede 403 Antwort liest die Rechte der Sitzung neu (F6). Der Test des Demowerkzeugs zu Verbindungsfehlern läuft seit PR #143 systemunabhängig (H1 der Nachprüfung).

**Prüfungen:** Eigenprüfung: Unit Tests, 60 Browsertests gegen ein vertragstreues Testbackend und eine echte Browserprüfung gegen Backend und PostgreSQL mit zwei unterschiedlich berechtigten Konten ([Protokoll und Screenshots](docs/screenshots/task-030/README.md)). Nach der Nachbesserung lokal 208 Backendtests gegen PostgreSQL 16, 68 Unit Tests und 67 Browsertests, davon sieben neue Regressionen, die ohne Korrektur rot waren; Backendtest `aRightsLockNotGrantedInTimeAnswersRetryableBusy` mit echter Sperre. CI mit Backend, Frontend, Qualifikation, Repository Prüfungen und Workflow `Image` grün auf PR #132, #133, #141 und #143.

**Review:** Unabhängige Prüfung durch Codex (Bericht `docs/REVIEW_TASK-030_PR-133.md` in PR #140): F1 bis F5 (P2) und F6 (P3), Rechteendpunkte, Verzeichnis, letzte Verwaltung, verborgene Felder und Versionskonflikt ohne Befund. Nachprüfung durch Codex (codex-20261022-task030-followup-01, Bericht `docs/REVIEW_TASK-030_PR-141.md` in PR #142): F1 bis F6 im nachgespielten Umfang mit elf Gegenproben am echten Backend behoben, technisch abnahmefähig, kein neuer P1 oder P2; der Hinweis H1 (P3, Demotest unter Windows) ist mit PR #143 behoben, ohne weitere Nachprüfung.

**Abnahme:** Holger, 2026-10-22, im Projektgespräch („030 ist abgenommen“ nach dem Merge von PR #142 und #143); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Projektkonfiguration nur lesend; Gruppen lassen sich nicht umbenennen oder löschen; kein Bildschirm für das Rechteprotokoll. (2) Die Ticketanlage wird angeboten, weil die Projektantwort keine Projektaktionen nennt; der Server lehnt ohne Recht ab. (3) Suche ohne zusätzliche Fachfilter in der Oberfläche; Board bleibt Vorschau mit Beispieldaten. (4) Jede 403 Antwort löst eine zusätzliche Sitzungsabfrage aus, auch erwartete. (5) Kein Kanban, Portal, LDAP, Wiki und keine Anforderungen.

**Referenz:** Reservierung #131, Teil 1 #132 (4cbb0c9), Teil 2 #133 (58ffc87), Prüfbericht #140, Nachbesserung #141 (a8489bc), Nachprüfung #142, Korrektur H1 #143 (97633a9); verknüpft mit TASK-029, TASK-031, DEC-070.

## TASK-031 Docker-Vorführung und Nachweise des ersten Abschnitts liefern

**Bezug:** REQ-OPS-001, REQ-OPS-005, REQ-OPS-006, REQ-PERF-001, REQ-PERF-003, REQ-DEV-004. **Verantwortlich:** claude-20261021-demo-01. **Abgeschlossen:** 2026-10-22.

**Abnahmekriterien:** Frischer Start und Neustart erhalten Daten und feste Sitzungsfristen; keine Standardkennwörter im Image. Reproduzierbare Nachweise zu den zehn Start-Szenarien und dokumentierte 15-Nutzer-Suchmessung, mit gewähltem Profil und verbleibenden Grenzen. Getrennte KI prüft Ergebnis; Holger nimmt fachlich ab. Keine verkaufbare V1, RPO/RTO-Erfüllung oder Überlegenheit gegenüber Wettbewerbern ohne eigenen Nachweis behaupten.

**Ergebnis:** DEC-071. `Dockerfile` baut ein Image mit Backend und Oberfläche, die der Server unter `/` vom selben Ursprung wie die API mit Content Security Policy und Cacheregeln ausliefert (`WebInterfaceHeadersFilter`). `compose.yaml` startet es mit PostgreSQL 18.6 im benannten Volume; Pflichtwerte ohne Standard aus `.env` (Vorlage `.env.example`), Ersteinrichtung mit `bootstrap-admin`. `tools/demo/demo.py` legt über die öffentliche API Demodaten (vier Konten mit unterschiedlichen Rechten, vier Projekte, 43 Tickets) oder den Referenzbestand an, prüft Rechte, Konflikt, Suche und Sitzung (`verify`) und misst mit gleichzeitigen Sitzungen (`measure`). `tools/demo/image-check.sh` prüft das Image als Workflow `Image` in CI. [DEMO.md](docs/DEMO.md) beschreibt Start, Demodaten, den Ablauf zur fachlichen Abnahme, Nachweise zu den zehn Punkten aus DEVELOPMENT_START.md und die Messung. Messung mit 15 Sitzungen auf 90.000 Tickets, 5.000 Tags und rund 450.000 Zuordnungen: alle vorgeschlagenen p95 Ziele eingehalten, keine Fehler; Werte, Laststufen und Grenzen in DEMO.md. Nicht enthalten: TLS, Sicherung, Hochverfügbarkeit, Veröffentlichung, Zweistundenlauf, Browserzeit der Suche, Kommentare, Wiki und Anforderungen im Bestand. Nachbesserung nach unabhängiger Prüfung (PR #136 und #138, Nachtrag zu DEC-071): komplexe Suche in eine Klasse mit Treffern aus sichtbaren Tickets und eine selektive Klasse geteilt, Trefferzahl je Suche erfasst (F1); eine Änderung wird nach verlorener, abgeschnittener oder verspäteter Antwort nicht erneut gesendet, sondern als unklarer Ausgang gemeldet (F2, N1); Sitzungsfrist in UTC (F3); Ticketänderung als Speichern mit Vergleichswert 1 s und als ganze Aktion gemessen (F4). Nebenbefund behoben: Der Neuaufbau der Suchprojektion scheiterte ab 65.535 Tickets, der Server startete auf dem Referenzbestand nicht neu. Nachmessung am 2026-10-22 mit 15 Sitzungen auf 90.000 Tickets: 5.385 Aktionen, keine Fehler, alle vorgeschlagenen p95 Ziele eingehalten, komplexe Suche mit Treffern in 113 von 113 Fällen mit Treffer bei p95 43 ms, Speichern p95 16,7 ms.

**Prüfungen:** Eigenprüfung am 2026-10-21: `image-check.sh` lokal grün, Browserlauf gegen das Image ohne CSP Verstoß, `WebInterfaceHeadersTest`. Nach den Korrekturen: 13 Werkzeugtests unter `tests/test_demo_tool.py`, Backendtest mit 70.000 Tickets (ohne Korrektur rot), alle 207 Backendtests lokal, `image-check.sh` lokal. CI mit Backend, Frontend, Qualifikation, Repository Prüfungen und Workflow `Image` grün auf PR #134, #136 und #138.

**Review:** Unabhängige Prüfung durch Codex (codex-20261022-task031-review-01, Bericht `docs/REVIEW_TASK-031_PR-134.md` in PR #135): F1 und F2 (P2), F3 und F4 (P3). Nachprüfung durch Codex (codex-20261022-task031-followup-01, Bericht `docs/REVIEW_TASK-031_PR-136.md` in PR #137): im geprüften Umfang technisch abnahmefähig, F1 bis F4 und Nebenbefund geschlossen, kein P1 oder P2; der verbleibende P3 Befund N1 ist mit PR #138 behoben, ohne weitere Nachprüfung.

**Abnahme:** Holger, 2026-10-22, im Projektgespräch („ist abgenommen“ nach dem Merge von PR #138); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Kein TLS; die Anmeldung funktioniert nur im Browser auf derselben Maschine über localhost. (2) Keine Sicherung und Wiederherstellung, keine Hochverfügbarkeit, kein veröffentlichtes Image. (3) Kein zweistündiger Dauerlauf, keine Hintergrundlast, keine Browserzeit der Suche gemessen; Kommentare, Wiki und Anforderungen fehlen im Bestand. (4) Die komplexe Suche mit Treffern bleibt sehr selektiv (Median 1 Treffer); große Treffermengen mit vielen Filtern sind ungemessen. (5) Der erste Start nach `bootstrap-admin` baut die Suchprojektion einmal neu auf. (6) Messwerte gelten für die dokumentierte Maschine, keine Zusage für die Referenzhardware.

**Referenz:** Reservierung #131, Ergebnis #134 (28becad), Prüfbericht #135, Nachbesserung #136 (124d2d8), Nachprüfung #137, Korrektur N1 #138 (02a96a0); verknüpft mit TASK-029, TASK-030 und DEC-071.

## TASK-029 Berechtigte Suche für Tickets und Projekte implementieren

**Bezug:** REQ-SRCH-001, REQ-TKT-004, REQ-TAG-001, REQ-PERF-003. **Verantwortlich:** claude-20261021-search-01. **Abgeschlossen:** 2026-10-21.

**Abnahmekriterien:** Verborgene Inhalte beeinflussen weder Treffer noch Rang/Anzahl/Vorschläge. Neuanlage, Änderung, Rechteentzug, Rückabwicklung und Projektionserneuerung geprüft; veraltete Projektionen geben nichts unzulässig frei. Getrennte KI prüft insbesondere Negativfälle und Datenhoheit. Messung erst in TASK-031.

**Ergebnis:** Suchmodul nach DEC-069 mit eigener Projektion von Projekten, Tickets, Tags und Zuordnungen (Migrationen V0014 und V0015). Projekte, Tickets und Tags veröffentlichen jede Änderung als Ereignis in ihrer Transaktion; die Projektion folgt synchron und rollt mit zurück. `GET /api/v1/search/tickets` findet Tickets nach Kennung, Titel, Beschreibung und Tagnamen und filtert nach Projekt, Status, Typ, Zuständigkeit und Tags; `GET /api/v1/search/projects` findet Projekte nach Schlüssel, Name, Beschreibung und Tags des Unternehmenskatalogs. Rechte und lesbare Felder werden vor Vergleich, Rang und Zählung geprüft. PostgreSQL Volltext `simple` mit Präfixsuche und GIN Index je Feld, Suchwörter nach derselben Regel wie der Suchtext. Containerbefehl `rebuild-search` und einmaliger Aufbau beim ersten Start. Vertrag `contracts/search.openapi.yaml`, backend/README.md Abschnitt „Suche (TASK-029)“. Korrektur nach unabhängiger Prüfung (PR #130, Nachtrag (8) zu DEC-069, auf Holgers Wahl „Alle drei“): F1 gleiche Zerlegung von Anfrage und Index, F2 gemeinsame Sperrordnung für Neuaufbau und Projektion, F3 indexfähiger Vergleich je Feld.

**Prüfungen:** Backend `verify` gegen lokale PostgreSQL 16 mit 196 Tests ohne Fehler nach PR #130, darunter `SearchApiTest` (12 Fälle, davon drei Regressionstests zu F1 bis F3), `SearchContractTest` und `AdminCommandTest` für `rebuild-search`; Lifecycle Prüfung mit dem gepackten Programm einschließlich `rebuild-search` lokal bestanden. CI auf PostgreSQL 18.6 grün auf PR #128 und PR #130 sowie auf main nach dem Merge von PR #128.

**Review:** Unabhängige Prüfung durch Codex (codex-20261021-search-review-01, Bericht `docs/REVIEW_TASK-029_PR-128.md` in PR #129): Projekt und Feldgrenzen, Rang und Seitenstabilität bei verborgenen Änderungen, wartender Rechte, Feld und Mitgliedschaftsentzug sowie Rückabwicklung von Quelle, Historie und Projektion halten; keine Rechteumgehung. Befunde F1 (P2, unterschiedliche Textaufbereitung), F2 (P2, Verklemmung zwischen Neuaufbau und Tagumbenennung) und F3 (P3, Feldindizes für den Mehrfeldvergleich ungenutzt) sind mit PR #130 behoben, jeweils mit Regressionstest. Eine Nachprüfung der Korrekturen durch eine getrennte KI hat nicht stattgefunden und war nicht Voraussetzung der Abnahme.

**Abnahme:** Holger, 2026-10-21, im Projektgespräch („Ich nehme ab“ nach dem Merge von PR #130); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Wiki, Anforderungen, Kommentare, Anhänge und Dateinamen sind noch nicht durchsuchbar, ebenso fehlen Vorschläge und Autovervollständigung; das bleibt bestätigter V1 Umfang. (2) Portalkonten suchen nicht, bis das Portal entschieden ist. (3) Antwortzeiten sind nicht gemessen (TASK-031); Zählung aller erlaubten Treffer und Rechteprüfung je Mitgliedsprojekt können bei großen Datenmengen teuer werden. (4) Die Rechteprüfung gilt zum Sperrzeitpunkt der Suche. (5) Nach V0015 findet die Textsuche bis zum Ende des Aufbaus beim nächsten Start nichts. (6) Eine Suchoberfläche folgt mit TASK-030.

**Referenz:** Reservierung #127, Ergebnis #128 (90f43be), Prüfbericht #129 (2da06c1), Korrektur #130 (f662b6a); verknüpft mit TASK-026, TASK-027, TASK-028, TASK-042 und DEC-069.

## TASK-042 Tagänderungen in der Tickethistorie zeigen

**Bezug:** REQ-TKT-005, REQ-TAG-001, REQ-ACL-003. **Verantwortlich:** claude-20261020-taghistory-01. **Abgeschlossen:** 2026-10-21.

**Abnahmekriterien:** Zuordnen und Entfernen stehen in der Historie, mehrfaches Zuordnen erzeugt keinen doppelten Eintrag; ohne Leserecht auf `tags` fehlen die Einträge ganz; die Ticketversion bleibt unverändert, wie in DEC-068 festgelegt. Getrennte KI prüft Sichtbarkeit und Transaktion.

**Ergebnis:** Nachtrag (10) zu DEC-068, auf Holgers Wahl „In Tickethistorie“ zum Hinweis H1 der Prüfung von TASK-028. Jedes tatsächliche Zuordnen oder Entfernen eines Tags an einem Ticket schreibt in derselben Transaktion über den neuen Port `TicketAccess.recordTagChange` einen eigenen Eintrag in die Historie des Ticketmoduls: Feld `tags`, Kennung des Tags in `to` oder `from`, Zeitpunkt, Konto und die Version, die das Ticket dabei hat. Die Ticketversion ändert sich nicht; mehrere Einträge können dieselbe Version tragen. Wiederholtes Zuordnen und das Entfernen eines nicht zugeordneten Tags schreiben nichts. Sichtbar nur mit `ticket.read` und der Leseregel für `tags`. Der Port sperrt die Ticketzeile geteilt als letzte Sperre nach der Tagzeile. Keine Migration. Vertrag `contracts/tickets.openapi.yaml` (Feld `tags`), backend/README.md Abschnitte „Tickets“ und „Tags“.

**Prüfungen:** Backend `verify` gegen lokale PostgreSQL 16.15 mit 182 Tests ohne Fehler, darunter `assignmentsAndRemovalsAppearInTheTicketHistoryOnlyForReadersOfTheTags` und `TicketsContractTest`; Repository Validator, Übersichtsprüfung, Python Tests und git diff --check erfolgreich. CI auf PostgreSQL 18.6 grün auf PR #123 und auf main nach dem Merge (b85abdd). Lifecycle Prüfung nicht ausgeführt, da keine Migration.

**Review:** Unabhängige Prüfung durch eine getrennte Claude Sitzung (claude-20261020-taghistory-review-01), von Holger gewählt (Bericht `docs/REVIEW_TASK-042_PR-123.md` in PR #124): Sichtbarkeit, Projektgrenzen, Rechteentzug, Rückabwicklung und Sperrreihenfolge halten, unter gleichzeitigen Feld und Tagänderungen keine Verklemmung; `clean verify` mit 182 Tests und sieben eigene Proben. Kein Befund P1 oder P2. Befund F1 (P3): Eine Zuordnung, die auf eine laufende Feldänderung wartet, trägt einen Zeitpunkt vor dieser Änderung, obwohl sie danach in der Historie steht; Version und Reihenfolge stimmen. Holger hat die Behebung nicht beauftragt. Hinweise H1 bis H3 (nur Kennung statt Name, mehrere Einträge je Version, Port ohne eigene Prüfung) ohne Handlungsdruck.

**Abnahme:** Holger, 2026-10-21, im Projektgespräch („abgenommen 029“ auf die Frage nach der Abnahme von TASK-028 und TASK-042); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) F1 der Prüfung bleibt bestehen: der Zeitpunkt einer wartenden Zuordnung kann bis zur Sperrwartezeit vor der vorangehenden Feldänderung liegen. (2) Der Eintrag nennt die Kennung, nicht den Namen des Tags; für die Oberfläche (TASK-030) zu entscheiden. (3) Zuordnungen an Projekten erscheinen in keiner Historie. (4) Ein späterer zweiter Aufrufer des Ports muss Rechte, Ticket und Tag selbst prüfen.

**Referenz:** Reservierung #122, Ergebnis #123 (b85abdd), Prüfbericht #124; verknüpft mit TASK-028, TASK-027 und DEC-068.

## TASK-028 Tag-Kern für Tickets und Projekte implementieren

**Bezug:** REQ-TAG-001, REQ-ACL-004, REQ-TKT-004. **Verantwortlich:** claude-20261020-tags-01. **Abgeschlossen:** 2026-10-21.

**Abnahmekriterien:** Mehrfachzuordnung, unberechtigtes Erstellen/Zuordnen und versteckte Katalognamen geprüft; kombinierte Erstellung/Zuordnung bleibt bei Fehlern atomar. Getrennte KI prüft Entdeckungsgrenzen und Modultransaktionen. Das Ergebnis erklärt den noch offenen umfangreichen V1-Tag-Anteil.

**Ergebnis:** Tagmodul nach DEC-068, auf Holgers Wahl „Beides“: ein Unternehmenskatalog für jede interne Anmeldung und je Projekt ein eigener Katalog nur für Mitglieder mit einer Projekt oder Ticketleseaktion. Migration V0013 (Tags, Ticket- und Projektzuordnungen, Audit), Aktion `tag.create`, Unternehmensrecht `tags.manage` bei der Ersteinrichtung, Anlegen, Umbenennen, Archivieren und Wiederherstellen mit Tagversion als ETag, Zuordnung zu Tickets über das Feld `tags` des Ressourcentyps `ticket` und zu Projekten mit den Projektrechten, Zuordnung über Kennung oder Namen mit Anlage in derselben Transaktion, Namen nach NFKC eindeutig je Katalog ohne Unterschied der Groß und Kleinschreibung, 503 `tag.busy` bei Sperrproblemen, Port `TicketAccess` im Ticketmodul. Vertrag `contracts/tags.openapi.yaml`, Identitätsvertrag mit `tags.manage`, backend/README.md Abschnitt „Tags (TASK-028)“ mit dem offenen V1 Anteil. Korrektur nach unabhängiger Prüfung (PR #121, Nachtrag (9) zu DEC-068, auf Holgers Auftrag „ja beheben“): F1 innerer Unicode Leerraum zählt als ein Leerzeichen, F2 der Vergleichsschlüssel faltet über Groß und danach Kleinschreibung (`STRASSE` gleich `Straße`), F3 Tags fremder Kataloge werden weder gesperrt noch abgewartet und antworten sofort 404; dazu H3 bis H5. H1 (Tags in der Tickethistorie) hat Holger mit „In Tickethistorie“ entschieden und als TASK-042 umsetzen lassen.

**Prüfungen:** Backend `verify` gegen lokale PostgreSQL 16.15 mit 181 Tests ohne Fehler nach PR #121, darunter `TagsApiTest` (Kataloge und Rechte, Namensprüfung, gleichzeitige Anlage, verborgene Projektkataloge, Ticket und Projektzuordnung mit Feldregeln, Unicode und Faltung, Archivieren gegen wartende Zuordnung, gesperrter fremder Tag ohne Warten) und `TagsContractTest`; Lifecycle Prüfung lokal bestanden. CI auf PostgreSQL 18.6 grün auf PR #119, PR #121 und auf main nach beiden Merges.

**Review:** Unabhängige Prüfung durch eine getrennte Claude Sitzung (claude-20261020-tags-review-01), von Holger gewählt (Bericht `docs/REVIEW_TASK-028_PR-119.md` in PR #120): Projektgrenzen, Rechteentzug, Feldregel `tags`, Archivieren und kombinierte Anlage mit Zuordnung halten; kein Befund P1 oder P2, drei Befunde P3 (F1 bis F3) und fünf Hinweise. F1 bis F3 sind mit PR #121 behoben, jeweils mit Regressionstest; H2 bleibt so und ist dokumentiert, H1 folgt als TASK-042. Eine Nachprüfung der Korrekturen durch eine getrennte KI hat nicht stattgefunden und war nicht Voraussetzung der Abnahme.

**Abnahme:** Holger, 2026-10-21, im Projektgespräch („abgenommen 029“ auf die Frage nach der Abnahme von TASK-028 und TASK-042); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Beschreibung und Farbe, Zusammenführen, Massenänderung, Filter, Autovervollständigung über Objekte, Suche (TASK-029), Oberfläche (TASK-030) und Portaltags (DEC-049) fehlen. (2) Projekte nehmen nur Tags des Unternehmenskatalogs. (3) `tag.create` und die Feldregeln für `tags` lassen sich bis TASK-030 nur direkt in den Tabellen vergeben. (4) Keine Obergrenze je Objekt, kein Trigrammindex für die Katalogsuche. (5) Wer nur `ticket.edit` mit den Feldregeln hat, ordnet Projekttags nur über eine bekannte Kennung zu (H2). (6) Entwicklungsdatenbanken aus dem Stand vor TASK-028 oder mit Tags aus PR #119 werden neu angelegt. (7) Prüfung zum Sperrzeitpunkt, nicht beim Commit.

**Referenz:** Reservierung #117, Ergebnis #119 (6ba4175), Prüfbericht #120, Korrektur #121 (d4eaf40), Folgeaufgabe TASK-042 (#122, #123); verknüpft mit TASK-027 und DEC-068.

## TASK-027 Ticketkern mit Historie und Konflikterkennung implementieren

**Bezug:** REQ-TKT-001, REQ-TKT-005, REQ-ACL-003, REQ-API-001. **Verantwortlich:** claude-20261020-tickets-01. **Abgeschlossen:** 2026-10-20.

**Abnahmekriterien:** Erfolgs- und Negativfälle, Feldfilterung, verbotene Übergänge, veraltete Versionen und vollständige Rückabwicklung geprüft. Aktuelle Rechte gelten auch bei konkurrierendem Entzug bis zur Schreibgrenze. Getrennte KI prüft Transaktion, Historie und API-Konfliktantworten.

**Ergebnis:** Ticketmodul nach DEC-067. Migration V0012 (Tickets, Nummernzähler je Projekt, Feldhistorie), Aktionen `ticket.read`, `ticket.create` und `ticket.edit` mit Mitgliedschaft im Projektkontext, Feldregeln des Ressourcentyps `ticket` für Lesen und Schreiben, Typ, Status, Übergänge und Pflichtfelder aus der aktuellen Projektkonfiguration, Zuständigkeit nur für aktive Mitglieder, Ticketversion als ETag mit `If-Match`, Historie je Feldänderung, 503 `ticket.busy` bei Sperrproblemen (P4). Endpunkte unter `/api/v1/projects/{projectId}/tickets` mit Vertrag `contracts/tickets.openapi.yaml`; der Projektport `ProjectAccess` liefert zusätzlich Mitgliedschaft und Projektschlüssel. Beschreibung und Grenzen in backend/README.md, Abschnitt „Tickets (TASK-027)“. Korrektur nach unabhängiger Prüfung: Die Prüfung durch eine getrennte Claude Sitzung (docs/REVIEW_TASK-027_PR-112.md, PR #115) meldet drei Befunde, alle behoben. F1 (P2): Wer nur `ticket.create` oder nur `ticket.edit` ohne `ticket.read` hatte, erhielt 404 und die Anlage oder Änderung wurde zurückgerollt; jetzt gelingt sie, und die Antwort zeigt das Ticket ohne Feldwerte. F2 (P3): Ein NUL Zeichen in Titel oder Beschreibung ergab 500; jetzt `title.invalid` beziehungsweise `description.invalid`. F3 (P3): Unbekannte Felder bei der Anlage wurden still ignoriert; jetzt `400 request.invalid` mit `fields.unknown` wie bei der Änderung. Der Befund F2 der Prüfung von TASK-026 wirkte auch hier (503 statt 404 bei gesperrtem fremdem Projekt) und ist mit derselben Korrektur behoben. Regressionen in `TicketsApiTest` schlagen ohne die Korrektur fehl; Backend `verify` lokal mit 170 Tests ohne Fehler. Zum Hinweis H1 (Version und letzte Änderung zeigen, dass sich ein verborgenes Feld geändert hat) hat Holger am 20. Oktober 2026 „Ja, so lassen“ gewählt; festgehalten als Nachtrag 9 zu DEC-067, Code unverändert.

**Prüfungen:** Backend `verify` gegen lokale PostgreSQL 16.15 mit 165 Tests ohne Fehler, darunter `TicketsApiTest` (Anlage und Nummern, gleichzeitige Anlage, Sichtbarkeit und Projektgrenzen, verborgene Felder in Antwort und Historie, Konfiguration und Übergänge, geänderte Konfiguration und Pflichtfelder, Versionen, Anmeldung mit offenem Passwortwechsel, Warten bei gleichzeitigem Entfernen des Mitglieds, 503 bei gehaltener Ticketsperre) und `TicketsContractTest`; Lifecycle-Prüfung lokal bestanden. Maßgeblich ist CI auf PostgreSQL 18.6. Eine unabhängige Prüfung nach DEC-040 steht aus. Eigenprüfung siehe Ergebnis und Korrektur. CI auf PostgreSQL 18.6 grün auf dem Ergebnis PR #112, auf PR #114 und auf main nach beiden Merges (Backend, Frontend, Qualifikation, Repository Prüfungen).

**Review:** Unabhängige Prüfung durch eine getrennte Claude Sitzung (claude-20261020-tickets-review-01), von Holger gewählt, weil Codex wegen eines Nutzungslimits nicht verfügbar war (Bericht `docs/REVIEW_TASK-027_PR-112.md` in PR #115). Befunde F1 (P2), F2 und F3 (P3) sind mit PR #114 behoben, jeweils mit Regressionstest, der auf dem alten Code scheitert; Hinweis H1 von Holger entschieden (Nachtrag 9 zu DEC-067, PR #116). Eine Nachprüfung der Korrekturen durch eine getrennte KI hat nicht stattgefunden und war nicht Voraussetzung der Abnahme.

**Abnahme:** Holger, 2026-10-20, im Projektgespräch („die offenen tasks 41, 26, 27 sind abgenommen“); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Rechte für Tickets lassen sich bis TASK-030 nur direkt in den Tabellen vergeben. (2) Ein Status, den eine neuere Konfiguration nicht mehr kennt, bleibt am Ticket und lässt sich nur über einen Übergang der aktuellen Fassung verlassen. (3) Eine Zuständigkeit bleibt nach Austritt aus dem Projekt bestehen. (4) Keine Löschung, keine Kommentare, Anhänge, Beziehungen, Benachrichtigungen, Oberfläche. (5) Version, `updatedAt` und `updatedBy` zeigen Änderungen verborgener Felder (so entschieden, DEC-067 Nachtrag 9). (6) Prüfung zum Sperrzeitpunkt, nicht beim Commit. Tags folgen mit TASK-028, Suche mit TASK-029.

**Referenz:** Reservierung #111, Ergebnis #112, Korrektur #114, Entscheidung H1 #116, Prüfbericht #115; verknüpft mit TASK-026 und DEC-067.

## TASK-026 Projekte und minimale Konfiguration implementieren

**Bezug:** REQ-ORG-001, REQ-ORG-003, REQ-ACL-001, REQ-API-001. **Verantwortlich:** claude-20261020-projects-01. **Abgeschlossen:** 2026-10-20.

**Abnahmekriterien:** Berechtigter Zugriff und Administration funktionieren; fremde Projekte bleiben über Listen und IDs geschützt. Konfigurationsversionen und ungültige Änderungen werden nach Fachvertrag behandelt. Getrennte KI prüft Datenhoheit, Projektgrenzen und administrativen Rechteentzug; ihre Prüfung ist angeboten, aber nicht Voraussetzung der Abnahme durch Holger.

**Ergebnis:** Projektmodul nach DEC-065. Migration V0011 (Projekt, Konfigurationsversionen, Mitglieder, Audit), Unternehmensrecht `projects.manage` beim ersten Start, Projektaktionen `project.read` und `project.manage` mit Mitgliedschaft, Projektadapter `ProjectAccess` mit Zeilensperre der Projekte, Endpunkte unter `/api/v1/projects` mit Vertrag `contracts/projects.openapi.yaml`, Ausgangskonfiguration Neu, In Arbeit, Erledigt mit den fünf festen Feldern, Konfigurationsänderung als neue Version, ETag und `If-Match` auf die Projektversion. Beschreibung und Grenzen in backend/README.md, Abschnitt „Projekte (TASK-026)“. Korrektur nach unabhängiger Prüfung: Codex hat in docs/REVIEW_TASK-026_PR-109.md (PR #113) zwei Befunde gemeldet, beide behoben. F1 (P2): Eine Statuskategorie `null` oder ohne Angabe ergab 500; sie antwortet jetzt `400 request.invalid` mit `statuses.category_invalid`. F2 (P3): Ein fremdes Projekt, dessen Zeile eine gleichzeitige Änderung hielt, wartete auf die Sperre und antwortete nach der Wartefrist mit 500 statt 404; der Projektadapter weist ein Konto ohne Mitgliedschaft jetzt vor der Zeilensperre ab, ohne zu warten, und prüft die Mitgliedschaft unter der Sperre erneut. Das gilt auch für die Ticketendpunkte aus TASK-027, die vorher 503 geantwortet hätten. Regressionen in `ProjectsApiTest` und `TicketsApiTest` schlagen ohne die Korrektur fehl; Backend `verify` lokal mit 168 Tests ohne Fehler.

**Prüfungen:** Backend `verify` gegen lokale PostgreSQL 16.15 mit 154 Tests ohne Fehler, darunter `ProjectConfigurationTest`, `ProjectsApiTest` (Rechte, Sichtbarkeit fremder Projekte über Liste und Kennung, Delegation per Projektrecht, Versionen, Konfiguration, Mitglieder, Wartesperre bei Entfernung) und `ProjectsContractTest`. Gegenprobe: ohne Zeilensperre im Projektadapter scheitert die Sperrprüfung. Maßgeblich ist CI auf PostgreSQL 18.6. Eine unabhängige Prüfung nach DEC-040 steht aus. Eigenprüfung siehe Ergebnis und Korrektur. CI auf PostgreSQL 18.6 grün auf PR #110 (Lifecycle Korrektur nach dem Merge von #109), auf PR #114 und auf main danach.

**Review:** Unabhängige Prüfung durch Codex (codex-20261020-projects-admin-review-01), von Holger angestoßen (Bericht `docs/REVIEW_TASK-026_PR-109.md` in PR #113). Befunde F1 (P2) und F2 (P3) sind mit PR #114 behoben, jeweils mit Regressionstest, der auf dem alten Code scheitert. Projektgrenzen, Delegation und Rechteentzug sind im Bericht mit echten Sperrwartebeziehungen positiv belegt. Eine Nachprüfung der Korrekturen durch eine getrennte KI hat nicht stattgefunden und war nicht Voraussetzung der Abnahme.

**Abnahme:** Holger, 2026-10-20, im Projektgespräch („die offenen tasks 41, 26, 27 sind abgenommen“); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Die Mitgliederliste nennt nur Kontokennungen. (2) Portalkonten erhalten keinen Projektzugriff. (3) Konfigurationsänderungen prüfen keine Nutzung durch Tickets; Tickets tragen dafür ihre Konfigurationsversion (TASK-027). (4) Kein Löschen oder Archivieren. (5) Ein berechtigtes Mitglied erhält bei einer Sperrzeitüberschreitung an Projektendpunkten 500. (6) Prüfung zum Sperrzeitpunkt, nicht beim Commit. Weiterhin nicht Teil: Projektoberfläche und Rechteendpunkte (TASK-030).

**Referenz:** Reservierung #104, Ergebnis #109, Lifecycle Korrektur #110, Korrektur der Prüfbefunde #114, Prüfbericht #113; verknüpft mit TASK-027 und DEC-065.

## TASK-041 Befunde F1 und F2 der Nachprüfung von TASK-040 beheben

**Bezug:** REQ-ACL-001, REQ-ACL-003, REQ-AUTH-001, REQ-SEC-001. **Verantwortlich:** claude-20261020-adminfix-01. **Abgeschlossen:** 2026-10-20.

**Abnahmekriterien:** Der Kreuzfall aus F1 läuft als Regressionstest mit echter PostgreSQL Wartebeziehung: beide Aufrufe enden ohne 500 und ohne Deadlock, das Ergebnis ist fachlich eindeutig. Bestehende Konkurrenz-, Entzugs- und Schutztests bleiben grün. Für F2 Tests mit keinem, genau einem, mehreren ohne und mehreren mit Primary Adapter an jedem Verbraucher; nur genau ein Adapter erlaubt. Alle Backendtests und CI auf PostgreSQL 18.6 grün. Getrennte KI prüft das Ergebnis; ihre Prüfung ist angeboten, aber nicht Voraussetzung der Abnahme durch Holger.

**Ergebnis:** DEC-066. (F1) Deaktivieren, Reaktivieren, Passwortzurücksetzen und die Änderung der Sitzungsdauer nehmen vor der Rechteprüfung eine gemeinsame transaktionsgebundene Advisory Sperre (`AccountRepository.serializeAdministration`); die Sitzungsdauer direkt nach ihrer Einstellungszeile, das Deaktivieren vor der Schranke der letzten Verwaltung, in der die bisherige Deaktivierungssperre aufgeht. Zwei Berechtigte, die gleichzeitig das Konto des anderen verwalten, laufen dadurch nacheinander: der erste gewinnt, der zweite sieht dessen Ergebnis (zum Beispiel 403, weil seine eigene Anmeldung durch das Zurücksetzen endete) statt Deadlock und 500. Endpunkte für Reaktivieren, Zurücksetzen und Sitzungsdauer prüfen vorher eine Momentaufnahme des Rechts, damit Anfragen ohne Recht die Serialisierung nicht belegen. (F2) `AdministrationRights.unique` zählt die Kandidaten; alle fünf Verwendungen in `AdministrationRequests`, `SessionController`, `AppearanceController` und `AppearanceService` nutzen es statt `getIfUnique`. Kein Vertrag, keine Migration, keine Oberflächenänderung.

**Prüfungen:** Backend `verify` mit Temurin 25.0.4.1+1 gegen eine lokale PostgreSQL 16.15 (Docker war in dieser Umgebung nicht verfügbar, daher Entwicklerweg über `-Dtaskmanager.test.jdbc-url`; maßgeblich ist der CI-Lauf auf 18.6): 138 Tests, 0 Fehler, 0 übersprungen. Neu: drei Konkurrenztests in `AdministrationRightsApiTest` (gegenseitiges Zurücksetzen; Deaktivieren und Reaktivieren gegen Zurücksetzen; Sitzungsdauer gegen Zurücksetzen des Anfragenden), die die Serialisierung festhalten, belegen, dass beide Anfragen dort ohne Identitätssperre warten, und danach eindeutige Ergebnisse ohne 500 prüfen; fünf Tests in `AdministrationRightsWiringTest` (kein, ein, zwei ohne und zwei mit Primary Adapter sowie Quelltextprüfung, dass kein Verbraucher `getIfUnique` für den Port nutzt). Gegenprobe: Mit wirkungsloser Serialisierung und mit `getIfUnique` in `unique` scheitern genau diese 5 neuen Prüfungen (3 + 2), danach Originale wiederhergestellt. Repository Validator, Übersichtsprüfung, Python Tests und git diff --check erfolgreich. Der ursprüngliche Prüfversuch von Codex wurde nicht unverändert wiederholt: Er erwartet beide Anfragen wartend an der Sitzungssperre, was die Serialisierung gerade verhindert. Eigenprüfung siehe oben. CI auf PostgreSQL 18.6 grün auf dem Ergebnis PR #108 ([Backend 39385815671](https://github.com/1uedev/task-manager/actions/runs/39385815671)); Codex hat zusätzlich `clean verify` mit 154 Tests auf PostgreSQL 18.6 ausgeführt.

**Review:** Unabhängige Nachprüfung durch Codex (codex-20261020-projects-admin-review-01), von Holger angestoßen (Bericht `docs/REVIEW_TASK-041_PR-108.md` in PR #113): technisch abnahmefähig, kein neuer Befund; F1 und F2 aus der Prüfung von TASK-040 behoben, die zusätzliche globale Wartebeziehung der Sitzungsdauer ist zyklusfrei.

**Abnahme:** Holger, 2026-10-20, im Projektgespräch („die offenen tasks 41, 26, 27 sind abgenommen“); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Selbstverwaltung gegen nicht serialisierte Wege wie Passwortwechsel bleibt wie in DEC-064 beschrieben. (2) Der Notbefehl `reset-admin-password` bleibt außerhalb der Serialisierung. (3) Eine wartende Änderung der Sitzungsdauer kann Anmeldungen kurz aufhalten. (4) Prüfung zum Sperrzeitpunkt, nicht beim Commit. Nicht Teil: Rechteendpunkte, P2 bis P6 aus TASK-025, F1 bis F3 aus PR #96.

**Referenz:** Reservierung #106, Ergebnis #108, Prüfbericht #113; verknüpft mit TASK-040 und DEC-066.

## TASK-040 Verwaltungsrechte statt Administratorkennzeichnung einführen

**Bezug:** REQ-ACL-001, REQ-ACL-003, REQ-AUTH-001, REQ-AUTH-003, REQ-BRAND-001, REQ-SEC-001. **Verantwortlich:** claude-20261019-rights-01. **Abgeschlossen:** 2026-10-19.

**Abnahmekriterien:** Jeder bisher durch die Kennzeichnung geschützte Endpunkt (Konten, Sitzungsdauer, Akzentfarbe, Logo) verlangt das jeweilige Recht: Ein Konto mit Kennzeichen, aber ohne Recht erhält 403; ein Konto mit Recht, aber ohne Kennzeichen, ist erfolgreich. Ohne Adapter, mit veraltetem Kontext, nach Entzug des Rechts oder der Mitgliedschaft, bei deaktiviertem Konto und bei abgelaufener Sitzung wird verweigert, mit Konkurrenztest auf echter PostgreSQL Sperrwartebeziehung für Rechteentzug gegen eine laufende Änderung. Das Audit der Änderungen bleibt vollständig; der Entzug eines der Rechte ändert die Rechteversion. `bootstrap-admin` legt Gruppe, Mitgliedschaft und alle Rechte atomar an. Der Notfallzugang und der Schutz der letzten Unternehmensverwaltung bleiben durch Tests belegt. Die Oberfläche blendet Verwaltung ohne Recht aus und zeigt sie mit Recht; Frontendtests und Browsertests angepasst und grün. Alle bestehenden Backendtests, Lifecycle-Prüfung und CI auf PostgreSQL 18.6 grün. Getrennte KI prüft das Ergebnis; ihre Prüfung ist angeboten, aber nicht Voraussetzung der Abnahme durch Holger.

**Ergebnis:** Before: Konten verwalten, die Sitzungsdauer ändern und Akzentfarbe und Logo ändern durfte jedes Konto mit der Kennzeichnung `administrator`. After: Diese drei Bereiche verlangen die Unternehmensrechte `accounts.manage`, `session_duration.manage` und `appearance.manage` des Berechtigungsmoduls (DEC-064). Die Module Identität und Erscheinungsbild prüfen sie über den Port `AdministrationRights` aus dem Plattformmodul, den der Anwendungseinstieg mit `Permissions.require` in der Transaktion der Änderung bedient; ohne Recht, mit veraltetem Kontext, deaktiviertem Konto oder abgelaufener Sitzung folgt `403 auth.forbidden`. Das Lesen des Erscheinungsbilds bleibt für jede vollständige Anmeldung offen. `GET /api/v1/session` liefert die gehaltenen Rechte als Liste `rights`; die Oberfläche nutzt sie nur zur Anzeige. `bootstrap-admin` ordnet der Gruppe „Unternehmensverwaltung“ neben `permissions.manage` die drei Rechte zu (sechs Versionen und Auditeinträge, atomar mit dem ersten Konto). Migration V0010 entfernt die Spalte `identity_account.administrator`; die Vertragsfelder entfallen, ein mitgesendetes `administrator` gewährt nichts. Der Notbefehl `reset-admin-password` und der Schutz der letzten Unternehmensverwaltung funktionieren weiter (Port `EmergencyResetPolicy` und Schranke aus DEC-063). Sperrreihenfolge: Beim Deaktivieren steht die Rechteprüfung nach der Schranke, bei der Sitzungsdauer wird zuerst die Einstellungszeile gesperrt; ohne diese Vorsperre ergibt der Test einen echten Deadlock mit Antwort 500 (Mutationsnachweis, Einzelheiten in DEC-064 und backend/README.md, Abschnitt „Verwaltungsrechte“).

**Prüfungen:** Backend `clean verify` mit 130 Tests grün (lokal Java 21 und PostgreSQL 16), darunter 7 neue API-Tests in `AdministrationRightsApiTest`: Rechtematrix je Endpunkt (Kennzeichen ohne Recht 403, Recht ohne Kennzeichen erfolgreich), Rechteliste der Sitzung, Anmeldung ohne Rechte, sofortiger Rechteentzug, Entzug während eine Änderung auf die Rechtesperre wartet (echte PostgreSQL Sperrwartebeziehung) und der deterministische Deadlocktest für die Sitzungsdauer. `lifecycle-check.sh` grün mit neun Migrationen und den vier Verwaltungsrechten nach dem ersten Start. Frontend `npm run verify` grün, Browserprüfungen 45 von 45 grün (lokal Node 22 und Chromium). Repository-Validator, Übersichtsprüfung, Python-Regressionstests und `git diff --check` erfolgreich. Maßgeblich ist CI mit PostgreSQL 18.6. CI auf dem Ergebnis PR #101 grün (Backend mit PostgreSQL 18.6, Frontend, Qualifikation und Repository Prüfungen).

**Review:** Eine getrennte KI-Prüfung des Ergebnisses (Rechteprüfung, Sperrreihenfolge, Notweg) hat nicht stattgefunden. Holger hat ohne sie abgenommen. Sie kann nachgeholt werden. Die Nachprüfung von TASK-039 durch Codex (F1 und F2 behoben) betrifft diese Aufgabe nicht.

**Abnahme:** Holger, 2026-10-19, im Projektgespräch („task 40 ist abgenommen“); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** (1) Ohne HTTP Endpunkte für Rechte (TASK-030) kann kein weiteres Konto Verwaltungsrechte erhalten; bisher setzte die Kontoanlage dafür die Kennzeichnung, dieser Weg entfällt. (2) Die Seite Erscheinungsbild bleibt für jede vollständige Anmeldung als Vorschau sichtbar, nur das Speichern verlangt das Recht; Seiten für Konten und Sitzungsdauer gibt es in der Oberfläche noch nicht. (3) Eine nach TASK-039 gestartete Entwicklungsdatenbank hat die Rechte nicht und wird neu angelegt; eine Datenmigration entfällt, weil sie Auditeinträge mit erfundenem Akteur bräuchte. (4) Die Prüfung gilt zum Sperrzeitpunkt, eine Abschlussprüfung beim Commit gibt es nicht. (5) Die Befunde F1 bis F3 aus PR #96 und P2 bis P6 sind nicht enthalten; der Appearance Controller kann mit deren Korrekturen zusammentreffen. Weiterhin nicht Teil: HTTP Endpunkte zum Verwalten von Gruppen und Rechten, Anmeldeseite (TASK-030), LDAP, Portalkonten, Projekt- und Objektadapter (TASK-026); TASK-026 bis TASK-031 bleiben blockiert.

**Referenz:** Reservierung #99 (68878ab), Ergebnis #101 (ef7dbbc); verknüpft mit TASK-036, TASK-039 und DEC-064.

## TASK-039 Adapter zwischen Identität und Berechtigungen implementieren

**Bezug:** REQ-ACL-001, REQ-ACL-003, REQ-AUTH-001, REQ-AUTH-003, REQ-ORG-001, REQ-SEC-001. **Verantwortlich:** claude-20261019-integration-01. **Abgeschlossen:** 2026-10-19.

**Abnahmekriterien:** Konkurrenztests mit echter PostgreSQL Sperrwartebeziehung in beiden Reihenfolgen: Rechteänderung oder Fachauftrag gegen Abmelden, Deaktivieren, Zurücksetzen, Passwortwechsel und Ablauf der Sitzung; nach dem Commit eines Entzugs greift kein alter Kontext mehr. Die letzte aktive Unternehmensverwaltung lässt sich über die Identität nicht deaktivieren, auch nicht gleichzeitig mit `setMember` oder `setAction`, die Schranke zählt nur aktive interne Konten. `bootstrap-admin` legt Unternehmen, Rechteversion, Gruppe und Mitgliedschaft atomar an und lehnt einen zweiten Aufruf ab. Ohne Adapter, bei mehr als einem Adapter, ohne Unternehmenskennung oder mit veraltetem Kontext wird verweigert; im Produktstart ist keine Testfixture registriert. Ein Test mit dem echten Adapter weist nach, dass ein deaktiviertes Konto und eine abgelaufene Sitzung keine Berechtigung mehr erhalten. Alle bestehenden Backendtests, Lifecycle-Prüfung und CI auf PostgreSQL 18.6 grün. Getrennte KI prüft den Adapter, die Sperrreihenfolge und die Schranke nach dem Ergebnis; ihre Prüfung ist angeboten, aber nicht Voraussetzung der Abnahme durch Holger.

**Ergebnis:** Alle fünf Punkte des Umfangs sind umgesetzt (DEC-063, Beschreibung in [backend/README.md](backend/README.md), Abschnitt „Adapter zwischen Identität und Berechtigungen“). (1) Neue Tabelle `platform_company` (Migration V0009, höchstens eine Zeile) und `CompanyContext` im Plattformmodul; die Firma wird nur von `bootstrap-admin` angelegt. (2) `IdentityVerification` im Identitätsmodul mit sperrenden Prüfungen (`FOR SHARE`, erst Konto, dann Sitzung, bis zum Commit des Aufrufers); `userExists` und `userActive` nutzen dieselbe Kontosperre. (3) Im Paket `taskmanager`: `IdentityPermissionsAdapter` als einzige `IdentityAccess` Implementierung, `RequestPermissionContexts` hinter dem neuen Port `PermissionContexts` und `AccountLifecyclePorts` für die beiden neuen Ports des Identitätsmoduls (`AccountDeactivationGuard`, `FirstAdministratorHandler`). (4) Deaktivieren fragt vor allen Identitätssperren die Schranke; `CompanyAdministration.remainsWithout` nimmt die exklusive Revisionssperre und verlangt ein anderes aktives Konto mit `permissions.manage`; Antwort `409 account.last_administrator` (Vertrag und erzeugte Typen im Text angepasst). (5) `bootstrap-admin` legt Firma, Rechteversion, die Gruppe „Unternehmensverwaltung“ mit `permissions.manage` und die Mitgliedschaft atomar an; kein Fachrecht, kein Auditrecht. Der Port `IdentityAccess` blieb unverändert; neu im Berechtigungsmodul sind nur die Ports `CompanyAdministration` und `PermissionContexts`. Das Rechtemodul selbst wurde nur um deren Umsetzung in `JdbcPermissions` erweitert (keine Regeländerung).

**Prüfungen:** Backend `mvn verify` mit 123 Tests grün (vorher 102; neu `IdentityPermissionsAdapterTest` mit 18, `PermissionContextsProbeTest` mit 1 und `RequestPermissionContextsTest` mit 2 Tests). Konkurrenztests mit echter PostgreSQL Wartebeziehung (`pg_stat_activity`, `pg_blocking_pids`) in beiden Reihenfolgen für Abmelden, Deaktivieren, Zurücksetzen, Passwortwechsel und Verkürzung der Sitzungsdauer gegen eine laufende Rechteprüfung, für Rechteänderung gegen laufende Prüfung und für Deaktivieren gegen `setMember` und gegen `setAction` der letzten Verwaltung. Ablauf der Sitzung nach 23 und 25 Stunden. Zwei Gegenproben, danach zurückgenommen: ohne `FOR SHARE` auf der Sitzung scheitern 2 dieser Tests, ohne `FOR UPDATE` der Schranke 4. Ein Test mit dem echten Adapter zeigt, dass ein deaktiviertes Konto, eine zurückgesetzte oder abgemeldete Sitzung und eine abgelaufene Sitzung keine Berechtigung mehr erhalten; weitere Tests prüfen fremde Firma, fremde Sitzung, Pflichtwechsel, externen und Organisationskontext, veraltete Rechteversion, fehlende Firma, genau einen Adapter im Produktstart und einen scheiternden Bootstrap ohne Konto, Firma und Rechte. Der HTTP Test belegt, dass der Kontext einer echten Anfrage aus Sitzung, Firma und Rechteversion entsteht und Angaben des Clients ignoriert. `AdminCommandTest` und `lifecycle-check.sh` (jetzt mit Firma und Gruppe nach `bootstrap-admin`) grün. Die Testverbindungsgruppe ist über `src/test/resources/application.properties` klein gehalten, sonst reichen 100 Verbindungen für die vielen Spring Kontexte nicht. Lokal mit Java 25.0.4 und PostgreSQL 16, nicht mit dem festgelegten PostgreSQL 18.6. CI auf PostgreSQL 18.6 grün für den Stand vor den Korrekturen der Codex Prüfung (Commit 18d6774, PR #97; die CI der Korrekturen steht am PR): [backend](https://github.com/1uedev/task-manager/actions/runs/39254743160), [repository-checks](https://github.com/1uedev/task-manager/actions/runs/39254742994), [frontend](https://github.com/1uedev/task-manager/actions/runs/39254742997), [qualification](https://github.com/1uedev/task-manager/actions/runs/39254743082). Repository-Validator, Übersichtsprüfung, Python-Regressionstests und `git diff --check` erfolgreich. Nach den Korrekturen der Codex Prüfung (Commit eadfb11): Backend `mvn verify` mit 123 Tests und `lifecycle-check.sh` lokal grün, [Backend-CI 39259170108](https://github.com/1uedev/task-manager/actions/runs/39259170108) und die drei weiteren Prüfungen des Commits grün (PostgreSQL 18.6).

**Review:** Unabhängige Gegenprüfung durch codex-20261019-adapter-review-01, von Holger angestoßen ([Bericht](docs/REVIEW_TASK-039_PR-97.md), PR #98). Befunde F1 (mittel, Uhr vor dem Sperrwarten gelesen) und F2 (niedrig, Vertrag des Kontextports) sind behoben, F1 mit einem Regressionstest, der auf dem alten Code scheitert. Sperrreihenfolge, Schranke und atomarer Start sind im Bericht positiv belegt. Eine Prüfung der Korrekturen selbst durch eine getrennte KI hat nicht stattgefunden und war nicht Voraussetzung der Abnahme.

**Abnahme:** Holger, 2026-10-19, im Projektgespräch („ist abgenommen“), nach der Codex Prüfung und der Meldung der Korrekturen samt der festgehaltenen Zeitsemantik (eine Prüfung gilt für den Zeitpunkt ihrer Sperren; eine Abschlussprüfung beim Commit hat er nicht beauftragt).

**Offen und Folgeaufgaben:** Die Kennzeichnung `administrator` (Konten, Sitzungsdauer, Erscheinungsbild) ist noch nicht durch Rechte abgelöst; das braucht einen neuen Auftrag, ebenso eine Abschlussprüfung beim Commit, falls sie gewünscht wird. Projekt- und Objektadapter (TASK-026), HTTP Endpunkte für Rechte, Anmeldeseite (TASK-030), LDAP, Portalkonten und P2 bis P6 bleiben unberührt; TASK-026 bis TASK-031 bleiben blockiert. (1) Eine Entwicklungsdatenbank mit Konten, aber ohne Firma wird nicht nachgerüstet und muss neu angelegt werden. (2) Das Ablaufen einer Sitzung ist eine Zeitbedingung ohne Sperre und wird nicht beim Commit erneut geprüft. (3) Jede Deaktivierung nimmt kurz die exklusive Revisionssperre der Firma. (4) Kein HTTP Endpunkt für Rechte, keine Anmeldeseite, kein LDAP, keine Portalkonten, kein zweiter Faktor, keine Dienstkontexte, keine Projekt- und Objektadapter (TASK-026), P2 bis P6 unberührt. (5) Die Kennzeichnung `administrator` gilt weiter für Kontenverwaltung, Sitzungsdauer und Erscheinungsbild. Der Wartehelfer der Tests bindet die Blockerliste nicht an die haltende Verbindung (Hinweis der Codex Prüfung); bei einer späteren Parallelisierung der Tests wäre das nachzuziehen.

**Referenz:** Reservierung #94 (b57a63f), Ergebnis #97 (Kopf eadfb11 und der Abschlusscommit dieses PR), Codex Bericht #98; verknüpft mit TASK-024, TASK-025 und TASK-038.

## TASK-038 Rechtemodul: aktive Administration und Herkunft von Feldverboten nachbessern

**Bezug:** REQ-ACL-001, REQ-ACL-003, REQ-ACL-004, REQ-ACL-005. **Verantwortlich:** claude-20261019-permissions-01. **Abgeschlossen:** 2026-10-19.

**Abnahmekriterien:** Je ein ablehnender Regressionstest für Entzug über `setMember` und über `setAction`, wenn das einzige weitere Mitglied deaktiviert ist (Aktivität des Testadapters, nicht bloße Existenz), plus ein Fall, in dem ein aktives Mitglied verbleibt. Für K1 ein Test des Rollenwechsels aus dem Bericht (Firmenverbot bleibt für die ehemalige Verwaltung geschützt) und ein Test, dass eigene Delegationsverbote weiter änderbar sind. Die neuen Tests scheitern auf dem Stand vor der Korrektur. Migrationstest vom Stand V0005 mit vorhandener Regel (Herkunft bleibt geschützt). Bestehende Backendtests, Lifecycle-Prüfung und CI auf PostgreSQL 18.6 grün. Eine gezielte Nachprüfung durch eine getrennte KI am Korrekturcommit wird angeboten, ist aber nicht Voraussetzung der Abnahme von TASK-025 durch Holger.

**Ergebnis:** Holger wählte am 2026-10-19 auf einer Entscheidungskarte „R1 und K1 streng“ (DEC-062). (R1) Der Port `IdentityAccess` hat den neuen Befehl `userActive(companyId, userId)` (Standard: verweigert); die Schranke für die letzte Unternehmensverwaltung zählt nur noch aktive Konten, `userExists` bleibt für das Hinzufügen von Mitgliedern. Das berichtigt das schwächere „bekannt“ aus DEC-059. (K1) Migration `V0007__permissions_rule_origin_role.sql` ergänzt `permissions_field.set_by_company`; eine Projektdelegation ändert nur Verbote, die sie selbst als Delegation gesetzt hat, Firmenverbote und alte Regeln ohne Angabe bleiben auch nach einem Rollenwechsel des Urhebers geschützt. Neue Tests in `PermissionsIntegrationTest` zu deaktiviertem Konto (beide Entzugswege mit Rollback von Revision und Audit), Rollenverlust bei erhaltener Delegation und Upgrade von V0005. V0006 ist für TASK-036 (PR #85) vorgesehen, die Testlisten enthalten daher V0007 neben V0005. Dokumentation in `backend/PERMISSIONS.md` und `backend/README.md`.

**Prüfungen:** Lokal mit OpenJDK 21 und PostgreSQL 16: `./mvnw clean verify` mit 92 Tests (bisher 89) ohne Fehler, `backend/scripts/lifecycle-check.sh` bestanden, Repository Prüfungen und `git diff --check` bestanden. Auf dem Stand vor der Korrektur scheitert der R1 Test an der erwarteten Ablehnung und der K1 Test an der fehlenden Herkunftsangabe; die Verhaltensprobe zu K1 liefert der Bericht aus PR #87. [Backend-CI 39241837469](https://github.com/1uedev/task-manager/actions/runs/39241837469) auf `9d2ac54` (PR #90) mit Temurin 25 und PostgreSQL 18.6 sowie Repository, Frontend und Qualifikation desselben Stands grün. Grenze: `userActive` ist nur gegen die Testfixture belegt.

**Review:** Die Korrektur beruht auf der gezielten Nachprüfung durch codex-20261019-permissions-followup-01 ([Bericht](https://github.com/1uedev/task-manager/blob/review/task-025-pr82-codex/docs/REVIEW_TASK-025_PR-82.md), [PR #87](https://github.com/1uedev/task-manager/pull/87)). Eine Prüfung der Korrekturen selbst durch eine getrennte KI hat nicht stattgefunden und war nicht Voraussetzung der Abnahme.

**Abnahme:** Holger, 2026-10-19, im Projektgespräch („task 25 und 38 sind abgenommen“); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Der echte Identitätsadapter bleibt offen und unzugewiesen; er muss `userActive` unter Sperre und die Schranke für die letzte Administration auf seinem Entzugsweg liefern. P2 bis P6 aus dem Gegenreview zu TASK-025 sind weiter unentschieden. Bei einem Merge von PR #85 (V0006) sind die Migrationslisten und DECISIONS.md anzugleichen.

**Referenz:** Reservierung #89 (d837463), Ergebnis #90 (5dbe32e); verknüpft mit TASK-025.

## TASK-037 Sitzungsfrist bei gleichzeitigem Passwortwechsel absichern

**Bezug:** REQ-AUTH-001, REQ-AUTH-003, REQ-SEC-001. **Verantwortlich:** claude-20261019-identity-01. **Abgeschlossen:** 2026-10-19.

**Abnahmekriterien:** Regressionstest mit echter PostgreSQL Sperrwartebeziehung (beide Reihenfolgen von Passwortwechsel und Fristverkürzung): Nach Verkürzung auf eine Stunde und anschließender Verlängerung auf 24 Stunden bleibt das Token der Ersatzsitzung nach Ablauf der einen Stunde ungültig. Der Test scheitert auf dem Stand vor der Korrektur. Bestehende 87 Backendtests, Lifecycle-Prüfung und CI auf PostgreSQL 18.6 grün. N2 als Grenze dokumentiert. Eine gezielte Nachprüfung durch eine getrennte KI am Korrekturcommit wird angeboten, ist aber nicht Voraussetzung der Abnahme durch Holger.

**Ergebnis:** Folgeaufgabe zu TASK-024 nach der gezielten Nachprüfung der Korrekturen R1 bis R5 ([Bericht](https://github.com/1uedev/task-manager/blob/review/task-024-fixes-79-codex/docs/REVIEW_TASK-024_PR-79.md), [PR #83](https://github.com/1uedev/task-manager/pull/83)), die zwei Restlücken fand. Holger wählte am 2026-10-19 auf einer Entscheidungskarte „Nur N1“ (DEC-061). `SessionService.replacePassword` nimmt als erste Anweisung die Einstellung der Sitzungsdauer gemeinsam gesperrt (`hoursForShare`, wie schon die Anmeldung) und begrenzt das Ablaufdatum der Ersatzsitzung auf Beginn plus aktuelle Dauer; es gibt nur diese beiden Wege, die eine Sitzung anlegen. Eine gleichzeitige Verkürzung wartet dadurch auf den laufenden Wechsel und kürzt auch die Ersatzsitzung, oder sie ist schon festgeschrieben und der Wechsel liest den verkürzten Wert. Zwei neue Regressionstests in `IdentityHardeningTest` (`n1A`, `n1B`) erzwingen beide Reihenfolgen mit echter Sperrwartebeziehung (`pg_stat_activity`, `pg_blocking_pids`). N2 (ein vor V0004 verbrauchtes Einmalpasswort wird nach dem Update einer Datenbank, die zwischen V0003 und V0004 bestand, wieder gültig) wird bewusst nicht behoben, sondern in `backend/README.md` als Grenze für Altdatenbanken dokumentiert; keine neue Migration, keine echte Installation betroffen.

**Prüfungen:** Lokal mit OpenJDK 21 und PostgreSQL 16: `./mvnw clean verify` mit 89 Tests (bisher 87) ohne Fehler, `backend/scripts/lifecycle-check.sh` bestanden, Repository Prüfungen und `git diff --check` bestanden. Beide neuen Tests scheitern auf dem Stand vor der Korrektur an der Wartebehauptung. [Backend-CI 39231163886](https://github.com/1uedev/task-manager/actions/runs/39231163886) auf `5ef853c` (PR #88) mit Temurin 25 und PostgreSQL 18.6 sowie Repository, Frontend und Qualifikation desselben Stands grün.

**Review:** Unabhängige gezielte Nachprüfung nach DEC-040 durch codex-20261019-session-review-01 ([Bericht](docs/REVIEW_TASK-037_PR-88.md), PR #91, Nachweise unter `docs/review-evidence`): keine neuen Befunde, N1 für die geprüften Abläufe behoben. Eigene Läufe: 89 Tests bestanden; eine Sperrmutation (in `replacePassword` wieder `hours()` statt `hoursForShare()`) ließ beide N1 Tests gezielt an ihrer Wartebehauptung scheitern, der frische Rücklauf bestand. Grenzen des Berichts: kein eigener Docker- oder Lifecycle-Lauf, keine fachliche Abnahme, nicht der gesamte Sicherheitsumfang des Identitätsmoduls.

**Abnahme:** Holger, 2026-10-19, im Projektgespräch („task 37 ist abgenommen“); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** N2 bleibt als dokumentierte Einschränkung bestehen (Holgers Entscheidung in DEC-061). Der Anschluss des Identitätsmoduls an das Berechtigungsmodul (Identitätsadapter) bleibt offen und unzugewiesen, ebenso Anmeldeseite (TASK-030), LDAP, Portalkonten und zweiter Faktor.

**Referenz:** Reservierung #86 (2443582), Ergebnis #88 (421d4b8), unabhängige Nachprüfung #91 (24b9422), Prüfbericht zu TASK-024 #83 (offen); verknüpft mit TASK-024.

## TASK-036 Moderne Oberfläche mit Admin-Akzentfarbe und Drag and Drop umsetzen

**Bezug:** REQ-PROD-004, REQ-BRAND-001, REQ-UX-001, REQ-I18N-001. **Verantwortlich:** claude-20261019-design-01. **Abgeschlossen:** 2026-10-19.

**Abnahmekriterien:** Neue Gestaltung in Deutsch und Englisch, hell und dunkel sowie bei 320 Pixel Breite ohne horizontales Scrollen der Seite. Text auf der Akzentfarbe erreicht mindestens Kontrast 4,5, Rahmen und Fokus 3. Akzentfarbe und Logo sind nur mit dem passenden Recht änderbar; serverseitig abgelehnte Änderungen ohne Recht sind per Test belegt, ungültige Werte werden abgewiesen. Jede Drag-and-Drop-Aktion ist auch per Tastatur möglich und wird angesagt. Die Board-Vorschau ist als Beispieldaten gekennzeichnet und speichert nichts. Unit-, Backend- und Browserprüfungen grün; neue Screenshots im Ergebnis-PR. Getrennte KI prüft Barrierefreiheit und Rechtegrenzen.

**Ergebnis:** Neues Modul `appearance` im Backend mit Migration V0008 (eine Einstellungszeile mit Standard `#f97316`, Audittabelle), Vertrag [appearance.openapi.yaml](contracts/appearance.openapi.yaml) und Endpunkten zum Lesen und Ändern von Akzentfarbe und Logo mit Versionsprüfung (`If-Match`, 428 und 412), CSRF und Audit; Logos nur als PNG, JPEG oder WebP bis 512 KiB, am Inhalt erkannt, mit `nosniff` und Sandbox ausgeliefert. Frontend mit Seitenleiste, Hell, Dunkel oder System, Orange als Standard mit automatisch lesbarer Schrift, Seite Erscheinungsbild mit Vorlagen, freier Farbe, Live-Vorschau, Logoablage per Drag and Drop und Speichern über die API, verschiebbare Navigation und Startkarten (Reihenfolge je Browser gespeichert) und Board-Vorschau mit Beispielkarten. Jede Verschiebung geht mit Alt und Pfeiltasten, behält den Fokus und wird angesagt. DEC-060 ist eingetragen. Abweichung vom Umfang: Ändern dürfen vorläufig Konten mit der technischen Administratorkennzeichnung aus TASK-024, nicht ein eigenes Recht des Rechtemoduls. Der Identitätsadapter, der Konten an das Rechtemodul aus TASK-025 anbindet, existiert noch nicht; ein Recht „Unternehmensauftritt verwalten“ wäre ohne ihn nicht prüfbar. Das Rechtemodul wurde nicht verändert. Mit dem Adapter wird die Prüfung im Controller auf das neue Recht umgestellt. Holger hat diese Zwischenlösung am 19. Oktober 2026 im Projektgespräch mit „ja das passt“ bestätigt. Die Migration heißt V0008 statt der ursprünglich vorgesehenen V0006, weil V0007 aus TASK-038 vorher auf main kam und Flyway ohne `outOfOrder` keine niedrigere Version nach einer höheren anwendet; V0006 bleibt unbenutzt. Screenshots in [docs/screenshots/task-036](docs/screenshots/task-036/README.md).

**Prüfungen:** Backend `mvn verify` mit 102 Tests grün (nach Einbindung von main mit PR #82, #88, #90 und #92), darunter 9 API-Tests des Erscheinungsbilds (ohne Anmeldung 401, ohne Administratorkennzeichnung 403 bei allen Änderungen, ungültige Farben 400, fehlende oder veraltete Version 428 und 412, gefälschtes CSRF-Token 403, Logotyp am Inhalt geprüft 415, Größe 413, Auslieferungsköpfe, Audit) und der Vertragsabgleich; `lifecycle-check.sh` grün mit sieben Migrationen (V0005 aus PR #82, V0007 aus PR #90, V0008 dieses PRs; V0006 bleibt frei). Lokal mit Java 25.0.4 und PostgreSQL 16, nicht mit dem festgelegten PostgreSQL 18.6; maßgeblich ist CI. Frontend `npm run verify` mit 71 Unit-Tests und 45 Browserprüfungen grün (lokal Node 22 und Chromium statt Node 24 und Chrome), darunter Kontrast jedes sichtbaren Textes auf allen fünf Seiten in Hell und Dunkel, 320 Pixel ohne waagrechtes Scrollen auf allen Seiten, Tastaturverschiebung mit Ansage und Fokus, Speichern mit `If-Match` und CSRF, Konfliktanzeige, Logoablage und Ablehnung von SVG. Zusätzlich ein lokaler Durchlauf mit dem gebauten Backend und PostgreSQL hinter `vite preview`: Administrator speichert Farbe und Logo, ein zweites Konto sieht beides, sein direkter Schreibversuch wird vom Server mit 403 `auth.forbidden` abgewiesen, das Audit enthält drei Einträge. Die Screenshots im PR stammen aus diesem Durchlauf. Repository-Validator, Übersichtsprüfung, Python-Regressionstests und `git diff --check` erfolgreich. CI auf `74eebea` (PR #85) grün: [Backend 39249407530](https://github.com/1uedev/task-manager/actions/runs/39249407530) mit Temurin 25 und PostgreSQL 18.6, [Frontend 39249407758](https://github.com/1uedev/task-manager/actions/runs/39249407758), Qualifikation und Repository Prüfungen desselben Stands.

**Review:** Eine getrennte KI-Prüfung von Barrierefreiheit und Rechtegrenzen, wie im Abnahmekriterium vorgesehen, hat nicht stattgefunden. Holger hat ohne sie abgenommen.

**Abnahme:** Holger, 2026-10-19, im Projektgespräch („Task 36 ist abgenommen“); bestätigt durch Merge dieses Abschluss PR.

**Offen und Folgeaufgaben:** Keine Anmeldeseite (TASK-030); ohne Anmeldung zeigt die Oberfläche das Standardorange und eine Vorschau. Keine Leseschnittstelle für das Audit. Kein Screenreader- und kein Windows-Test. Der Identitätsadapter aus TASK-039 ist inzwischen abgeschlossen, lässt die Administratorkennzeichnung für das Erscheinungsbild aber bewusst bestehen; die Umstellung auf ein eigenes Recht „Unternehmensauftritt verwalten“ braucht einen neuen Auftrag. Die getrennte KI-Prüfung kann nachgeholt werden.

**Referenz:** Reservierung #84 (e18ceaa), Ergebnis #85 (078e102); verknüpft mit TASK-023 und DEC-060.
