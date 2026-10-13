# Zentrale offene Aufgaben

Stand 13. Oktober 2026 · Planung und Repository-Infrastruktur ohne Produktimplementierung

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

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-PROD-002, REQ-PROD-003, REQ-EXT-001, REQ-TIME-003. **Abhängigkeiten:** Entwurf TASK-001 vorhanden; Priorisierung nach fachlicher Rückmeldung. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** V1-Vorschläge priorisieren, vollständiges Funktionsinventar der angestrebten Vergleichbarkeit erstellen und spätere Module abgrenzen. OPEN-020 und OPEN-021 einbeziehen.

**Abnahmekriterien:** Jeder Bereich hat Zweck, Mindestumfang, Abhängigkeit, Release-Status und Abnahmebeispiel. Bestätigte V1-Funktionen werden nicht ohne Entscheidung verschoben. Termine und Preise werden ohne Budget- und Kapazitätsbasis nicht zugesagt.

**Nächster Schritt:** Vorhandene Vergleichsanalyse bei Bedarf gezielt heranziehen und eine prüfbare Lückenliste erstellen.

## TASK-003 Architekturentscheidungen vorbereiten

**GitHub:** [Issue #3](https://github.com/1uedev/task-manager/issues/3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261013-architecture-01 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-ARCH-001, REQ-ARCH-002, REQ-ORG-002, REQ-DATA-001, REQ-DATA-002, REQ-API-001. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004 und TASK-005. **Arbeitsstand:** task/task-003-codex-architecture, separates Arbeitsverzeichnis; Architekturentwurf vorhanden.

**Umfang:** OPEN-001, OPEN-002 und OPEN-013 bearbeiten. Go und Java bewerten, Modulgrenzen, Unternehmenskontext und API-Grundvertrag entwerfen.

**Abnahmekriterien:** Ein begründeter Entscheidungsvorschlag benennt Betrieb, Prüfbarkeit, Aufwand und Grenzen. PostgreSQL bleibt alleiniger V1-Datenbankumfang. Kein Technologie-Prototyp oder Anwendungscode ohne zusätzliche Beauftragung.

**Ergebnis:** [Architekturentwurf](docs/ARCHITECTURE.md) mit Go/Java-Vergleich, begründeter Empfehlung, Modul- und Datenhoheit, späteren Unternehmensgrenzen, API-Grundvertrag, Hintergrundaufträgen und Docker-Betriebsbild. PostgreSQL bleibt die einzige unterstützte Datenbank. Vorschläge für Leistung und Hardware sind keine Messergebnisse. Backend und Detailverträge sind noch nicht freigegeben.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Rechte- und Architekturentwurf auf konsistente Organisationsgrenzen, Feldrechte, Zugriffskontext und spätere 2FA geprüft. Quellen, Aufgabenverweise und Trennung von Vorschlag und Entscheidung geprüft. Keine Produkt-, Last- oder Integrationstests; keine unabhängige technische Prüfung.

**Nächster Schritt:** Holger bewertet OPEN-001, OPEN-002 und den vorgeschlagenen API-Grundvertrag unter OPEN-013. TASK-005, TASK-006 und TASK-010 konkretisieren Fachmodell, SLA und Betrieb; TASK-002 vervollständigt das Funktionsinventar. Technisches Review unter OPEN-017 organisieren. Produktimplementierung bleibt gesondert zu beauftragen.

## TASK-004 Identität und Berechtigungen spezifizieren

**GitHub:** [Issue #4](https://github.com/1uedev/task-manager/issues/4)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** codex-20261013-rights-fallback-01 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-ACL-001 bis REQ-ACL-005, REQ-AUTH-001 bis REQ-AUTH-004, REQ-PORTAL-003. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** task/task-004-claude-rights, separates Arbeitsverzeichnis; koordinierte Übergabe an Codex am 2026-10-13.

**Umfang:** OPEN-004, OPEN-005 und OPEN-006 beantworten. Rechtekonflikte, externe Sicht, Feldrechte, LDAP-Lebenszyklus und 2FA-Termin festlegen.

**Abnahmekriterien:** Eine Rechte-Matrix und positive sowie negative Beispiele decken API, Suche, Exporte, E-Mail, Dateien und KI ab. Authentifizierung und fachliche Berechtigung bleiben getrennt. Fachliche Entscheidungen sind von technischen Vorschlägen unterscheidbar.

**Übergabe:** Die reservierte Claude-Sitzung konnte wegen eines ungültigen OAuth-Zugangs keinen Entwurf erzeugen. Die koordinierende Sitzung weist die Entwurfserstellung ausdrücklich Codex zu. Kein Claude-Review wird behauptet; ein späterer Einsatz erfordert erneute Anmeldung.

**Ergebnis:** [Rechteentwurf](docs/PERMISSIONS.md) mit Gruppenmatrix, Auswertungsregeln, Datenwegen, LDAP-Lebenszyklus und 16 fachlichen Prüfszenarien. Holgers drei Entscheidungen sind als DEC-027 bis DEC-029 in Anforderungen, Entscheidungen und Roadmap übernommen. Die weiteren Umsetzungsvorschläge bleiben offen.

**Eigenprüfung am 2026-10-13:** Repository-Validator, elf vorhandene Regressionstests und git diff --check erfolgreich. Dokumente auf Widersprüche zur Organisationssicht, Feldverbotsregel und 2FA-Einordnung geprüft. Keine Produkt- oder Sicherheitstests ausgeführt, da keine Anwendung implementiert ist. Keine unabhängige Prüfung und kein Claude-Review.

**Nächster Schritt:** Holger prüft Matrix und Szenarien. Allgemeine Aktionsrechte, delegierte Administration, LDAP-Lebenszyklus und das spätere 2FA-Verfahren bleiben unter OPEN-004 bis OPEN-006 offen. Technischen Reviewer unter OPEN-017 bestimmen; Umsetzung benötigt weiterhin gesonderten Auftrag.

## TASK-005 Fachmodell und Projektkonfiguration konkretisieren

**GitHub:** [Issue #5](https://github.com/1uedev/task-manager/issues/5)

**Status:** IN ARBEIT · **Verantwortlich:** codex-20261013-domain-01 · **Aktualisiert:** 2026-10-13

**Bezug:** REQ-ORG-003, REQ-TKT-001 bis REQ-TKT-005, REQ-TIME-001, REQ-TIME-002, REQ-REQ-001 bis REQ-REQ-003. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004. **Arbeitsstand:** geplant: task/task-005-codex-domain, separates Arbeitsverzeichnis.

**Umfang:** OPEN-007 und OPEN-021 bearbeiten. Standardfelder, Beziehungen, Status, Regeln, Zeitkorrekturen und Anforderungsfreigaben beschreiben.

**Abnahmekriterien:** Änderungen bestehender Projektregeln sind durchgespielt. Verborgene Pflichtfelder, Archivierung und Änderungen nach Freigabe haben eindeutige Regeln. API und Oberfläche verwenden dieselbe Fachlogik.

**Nächster Schritt:** Nach Integration der Reservierung zwei exemplarische Projekte, Feldregeln, Zeitkorrekturen und Anforderungsrevisionen als prüfbaren Entwurf erstellen. Fortsetzung der Planung durch die koordinierende Codex-Sitzung auf Holgers Auftrag; keine Produktimplementierung.

## TASK-006 Helpdesk Portal und SLA Regeln ausarbeiten

**GitHub:** [Issue #6](https://github.com/1uedev/task-manager/issues/6)

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-HD-001 bis REQ-HD-005, REQ-PORTAL-001 bis REQ-PORTAL-003. **Abhängigkeiten:** TASK-001; Rechte aus TASK-004. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** OPEN-008 und OPEN-009 bearbeiten. Nachrichtenwege, Postfächer, Kundenidentität und Servicekalender festlegen.

**Abnahmekriterien:** Wochenenden, Feiertage, Zeitumstellung, Pausen, Wiedereröffnung und Prioritätswechsel sind anhand konkreter Zeitpunkte berechenbar. Doppelte Mails und Eskalationsversuche führen nicht zu unkontrollierten Doppelaktionen.

**Nächster Schritt:** Fachliche SLA-Beispiele vorschlagen und gezielt fehlende Regeln klären.

## TASK-007 Wiki Sprachen und Unternehmensauftritt spezifizieren

**GitHub:** [Issue #7](https://github.com/1uedev/task-manager/issues/7)

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-DOC-001 bis REQ-DOC-004, REQ-I18N-001 bis REQ-I18N-003, REQ-BRAND-001, REQ-BRAND-002, REQ-UX-001. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** OPEN-011 und OPEN-015 bearbeiten. Editor, Dokumentformat, Sprachfassungen, Konfliktbehandlung und Vorlagen entwerfen.

**Abnahmekriterien:** Einsprachige Seiten sind ohne Übersetzungszwang nutzbar. Wiederholte Bearbeitung erhält Inhalte. Sprache, Zeitzone und Formatierung sind abgegrenzt. Anwendung, Portal, Anmeldung und E-Mail sind beim Branding berücksichtigt.

**Nächster Schritt:** Repräsentative Wiki-Seite und E-Mail-Vorlage als fachliche Beispiele beschreiben.

## TASK-008 KI Umfang und Freigabeablauf festlegen

**GitHub:** [Issue #8](https://github.com/1uedev/task-manager/issues/8)

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-AI-001 bis REQ-AI-009, REQ-PERF-004. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-004. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** OPEN-003 und OPEN-019 entscheiden. Skills, Provider-Konfiguration, erste Anwendungsfälle und Datenwege konkretisieren.

**Abnahmekriterien:** Alle Fachbereiche besitzen eine Nutzenbewertung. Nur Administratoren konfigurieren Modelle und Skills. Jeder ändernde Anwendungsfall besitzt eine sichtbare Bestätigung, Konfliktbehandlung und erneute Rechteprüfung. Lokaler Betrieb wechselt nicht still in die Cloud.

**Nächster Schritt:** Vier priorisierbare Anwendungsfälle mit Ein- und Ausgaben sowie Prüffällen formulieren.

## TASK-009 Git und spätere Migration eingrenzen

**GitHub:** [Issue #9](https://github.com/1uedev/task-manager/issues/9)

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-GIT-001 bis REQ-GIT-003, REQ-MIG-001, REQ-MIG-002, REQ-API-002. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** OPEN-010 und den Migrationsteil von OPEN-016 bearbeiten. Git-Varianten und Objektarten wählen, Importquellen priorisieren.

**Abnahmekriterien:** Zuordnung und Anzeige sind klar von späteren Git-Schreibaktionen getrennt. Quellenzugriff, Metadatenrechte und Aktualisierung sind definiert. Die erste Version verspricht keinen Import; die anschließende Migration besitzt einen begrenzten, überprüfbaren Umfang.

**Nächster Schritt:** Gewünschte Git-Betriebsvarianten und erste Importquelle ermitteln.

## TASK-010 Betrieb Sicherheit und Leistung planen

**GitHub:** [Issue #10](https://github.com/1uedev/task-manager/issues/10)

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-OPS-001 bis REQ-OPS-006, REQ-SEC-001, REQ-PERF-001 bis REQ-PERF-004, REQ-DATA-003, REQ-DATA-004. **Abhängigkeiten:** TASK-001; Abstimmung mit TASK-003. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** OPEN-012, OPEN-014 und Aufbewahrung aus OPEN-016 bearbeiten. Docker-Betrieb, Persistenz, Internetbedarf, Sicherung, Updates, Risiken und Lastprofil beschreiben.

**Abnahmekriterien:** Ressourcenannahmen sind nachvollziehbar, ohne feste RAM-Grenze. Die SSD-Planung berücksichtigt Anhänge. Wiederanlauf, zulässiger Datenverlust und Leistungsziele sind vor Tests festgelegt. Vorschläge werden nicht als gemessene Systemanforderungen ausgegeben.

**Nächster Schritt:** Betriebs- und Lastprofil mit klar markierten Annahmen entwerfen.

## TASK-011 Gemeinsamen Entwicklungs und Review Ablauf beschließen

**GitHub:** [Issue #11](https://github.com/1uedev/task-manager/issues/11)

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-DEV-001 bis REQ-DEV-005. **Abhängigkeiten:** TASK-001. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** OPEN-017 und OPEN-018 entscheiden. Koordination, getrennte Arbeitsstände, Aufgabenreservierung, PRs, technisches Review und fachliche Abnahme definieren.

**Abnahmekriterien:** Zwei gedachte gleichzeitige KI-Sitzungen können Aufgaben eindeutig übernehmen und übergeben. Eine veraltete TODO-Kopie überschreibt keinen aktuellen Stand. Fachliche Freigabe und technische Prüfung haben benannte Verantwortlichkeiten. Die technische GitHub-Einrichtung erfolgt separat in TASK-013. Weitergehende Review-Regeln werden hier geprüft.

**Nächster Schritt:** Den organisatorischen Vorschlag aus AGENTS.md mit einem Parallel- und Abbruchszenario prüfen.

## TASK-012 Planungsstand zur Architekturfreigabe zusammenführen

**GitHub:** [Issue #12](https://github.com/1uedev/task-manager/issues/12)

**Status:** OFFEN · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-12

**Bezug:** REQ-PROD-003, REQ-DEV-004, REQ-DEV-005. **Abhängigkeiten:** TASK-002 bis TASK-011 im für V1 und Architektur erforderlichen Umfang. **Arbeitsstand:** noch nicht begonnen.

**Umfang:** Entscheidungen, Anforderungskatalog, Abnahmeszenarien und Roadmap konsistent zusammenführen.

**Abnahmekriterien:** Kritische Architekturfragen sind entschieden. Restfragen haben Zuständigkeit und Fälligkeit. Der fachlich freigegebene Umfang ist sichtbar; keine Implementierung wird automatisch ausgelöst.

**Nächster Schritt:** Nach den vorausgehenden Aufgaben verbleibende Entscheidungs- und Freigabepunkte bündeln.

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
