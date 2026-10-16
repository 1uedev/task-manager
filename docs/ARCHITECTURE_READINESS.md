# Entscheidungsübersicht für die Architekturfreigabe

Stand 16. Oktober 2026 · TASK-012 · Vorlage zur Prüfung, Architektur noch nicht freigegeben

Für die geplanten Fachbereiche liegen jetzt Entwürfe vor. Der nächste Schritt ist die gezielte Entscheidung über ihre offenen Grundlagen. Zusätzliche Dokumente allein lösen diese Entscheidungen nicht. Die Anwendung wurde nicht implementiert; dieses Paket beauftragt keine Programmierung.

Die zentrale Spezifikation enthält 86 Anforderungen. **B** bestätigt deren Kern, nicht automatisch alle vorgeschlagenen Abnahmedetails. **V** bleibt ein Vorschlag. Die unter DEC-001 bis DEC-041 erfassten Vorgaben gelten weiterhin; die offene Auswahl unter OPEN-001 bis OPEN-021 wird durch diese Übersicht nicht geschlossen. Aufgaben und Abnahmen werden ausschließlich in [TODO.md](../TODO.md) und [DONE.md](../DONE.md) geführt.

## Bestätigte Auswahl und nächster Prüfbedarf

Holger hat am 16. Oktober 2026 drei Entscheidungen getroffen:

1. **Backend: Java mit Spring Boot (DEC-039).** React und PostgreSQL bleiben gesetzt. TypeScript, Versionen und die übrigen Architekturdetails sind nicht automatisch bestätigt.
2. **Technische Prüfung: zunächst gegenseitige KI-Reviews (DEC-040).** Zusätzliche menschliche technische Prüfung bleibt ausdrücklich offen. Holger übernimmt weiterhin die fachliche Abnahme; konkrete Prüfbelege und Freigabeschwellen sind noch zu bestimmen.
3. **Ausbau: Kanban, Gantt und einfache Dashboards (DEC-041).** Diese bilden die nächste Fachpriorität nach V1; die frühen Ergänzungen 2FA und Redmine-Import bleiben eingeplant. Detailumfänge, Termine und zusätzliche Sprintfunktionen sind nicht bestätigt.

Als Nächstes sind TypeScript, Modulstruktur und API-Grundvertrag sowie die fälligen Rechte-/Datenregeln, das Betriebsprofil und die KI-Anbieter zu prüfen. Die drei Entscheidungen geben weder sämtliche Detailentwürfe noch eine Produktimplementierung frei.

## Bestätigter Umfang, der erhalten bleibt

| Gebiet | Bereits bestätigte Richtung | Noch zu prüfende Ausgestaltung |
|---|---|---|
| Produkt und Betrieb | Eigenständige Codebasis; Docker beim Kunden, eine Unternehmensinstallation; React, PostgreSQL und Java/Spring Boot; Internetbedarf zulässig | Versionen, genaue Laufzeit-/Installationsplattform, Kosten, Wartung und Verhalten bei Netzstörungen |
| V1-Fachmodule | Tickets, Zeiten, IMAP/SMTP-Helpdesk mit SLA/Geschäftszeiten/Eskalation, Portal, Wiki, Anforderungen und Git-Verknüpfung | Felder, Übergänge, Mailzuordnung, berechnete Fristen und weitere V1-Vorschläge |
| Identität und Rechte | Lokales Konto, LDAP, eigene Gruppen, Projekt- und Feldrechte; Feldverbot gewinnt; Portal zeigt eigene Organisationsanfragen innerhalb freigegebener Projekte | Allgemeine Aktionskonflikte, delegierte Administration, LDAP-Lebenszyklus und zusätzliche Sichtbereiche |
| Wissen und Anforderungen | Wiki-Entwurf mit ausdrücklicher Veröffentlichung, optionale Sprachfassungen; neue Revision und erneute Freigabe nach inhaltlicher Änderung freigegebener Anforderungen | Editorformat, Such-/Dateiverfahren und genaue Änderungs-/Freigaberegeln |
| Sprache und Marke | DE/EN für Anwendung, Portal, Anmeldung und E-Mail; weitere Sprachen vorbereiten; Logo, Farben und Vorlagen | Rückfallsprachen, Übersetzungsprozess, Browser-/Mobilprofil und erlaubte Designanpassung |
| KI | Vier interne V1-Fälle; Admin-Markdown-Skills und Admin-Modellzuordnung; Änderungen/Versand nur nach Bestätigung; OpenAI, Claude und Ollama vorgesehen | Anbieterreihenfolge, Datenklassen, Kostenlimits und technische Bestätigungs-/Fehlerregeln |
| Git und Erweiterungen | GitHub.com, GitLab.com und selbst betriebenes GitLab in V1 zur Anzeige/Zuordnung; 2FA und Redmine-Import früh danach | Authentifizierung, Objekt-/Importumfang und Versionen; spätere Ausbaureihenfolge |
| Mengen und Wiederherstellung | 500–1.500 neue Tickets monatlich, etwa 15 aktive Nutzer; keine feste RAM-Grenze; 1 TB als Live-Planungsrahmen; RPO höchstens 1 Stunde, RTO höchstens 4 Stunden | Reale Anhangsmengen, Messprofil, Servicezeit, Ersatzkapazität und praktische Nachweise |
| Entwicklung und Review | Zunächst gegenseitige KI-Reviews, Holgers Fachabnahme getrennt | Konkrete Prüfzuordnung, Nachweise und Freigabeschwellen; menschliche Prüfung weiterhin offen |
| Zukunft | Kanban, Gantt und einfache Dashboards priorisiert; Mehrmandantenfähigkeit, weitere Anmeldung und Git-Funktionen vorbereiten; weitere Datenbanken zunächst nur architektonisch; PDFs und formale Anforderungsstände später | Konkrete spätere Releases und deren gesonderte technische Qualifikation |

Die genaue Nachverfolgung bleibt in [REQUIREMENTS.md](../REQUIREMENTS.md) und [DECISIONS.md](../DECISIONS.md). Das [Funktionsinventar](FUNCTION_INVENTORY.md) ordnet alle Anforderungen und die zusätzlichen Kandidaten ein. Kein bestätigter V1-Bereich wurde für einen kleineren ersten Entwicklungsdurchlauf gestrichen.

## Offene Entscheidungen mit Zuständigkeit und Fälligkeit

**Entscheider ist Holger.** Technische Vorschläge werden zunächst durch gegenseitige KI-Reviews geprüft (DEC-040). „Technik offen“ in der Tabelle bedeutet, dass die konkreten KI-Sitzungen für Vorbereitung und Gegenprüfung noch zuzuordnen sind; es ist keine Voraussetzung für einen menschlichen Reviewer. Zusätzliche menschliche Prüfung bleibt offen. Betreiber und Pilotkunde sind ebenfalls noch zu bestimmen. KI-Sitzungen nehmen Holgers Freigabe nicht vorweg.

Die Fälligkeiten entsprechen DECISIONS.md. Wo eine Frage sowohl Architektur als auch spätere Details umfasst, ist der frühe Teil ausdrücklich genannt. Ein kalendarisches Datum ohne vereinbarten Start wäre erfunden; die relative Fälligkeit ist hier die Grenze für den jeweiligen Arbeitsschritt. Der Status wird weiterhin in DECISIONS.md gepflegt; die Tabelle ist ein Leseweg durch diese Entscheidungen.

| Frage | Noch nötiges Ergebnis / Ausgangsvorschlag | Zuständigkeit neben Holger | Spätester Entscheidungszeitpunkt |
|---|---|---|---|
| OPEN-001 Backend und Werkzeuge | Java/Spring Boot bestätigt; TypeScript, unterstützte Versionen sowie Build-/Testkonventionen noch entscheiden | Technik offen | Vor Implementierung |
| OPEN-002 Modulstruktur | Modularer Monolith, Datenhoheit, Unternehmenskontext und Jobs; spätere Mandantenisolation darf als ausdrücklich vertagte Variante dokumentiert bleiben | Technik offen | Vor Implementierung; Isolation vor Mehrmandantenausbau |
| OPEN-003 KI-Anbieter | Lieferreihenfolge der drei Anbieter; mindestens eine geeignete qualifizierte Anbindung trägt alle vier V1-Fälle | Technik und Pilot offen | Vor verbindlicher V1-Planung |
| OPEN-004 2FA | Erweiterungspunkt jetzt; Faktor, Pflichtgruppen, Recovery und LDAP-Zusammenspiel später | Technik offen | Architektur vor Implementierung; Verfahren vor 2FA-Release |
| OPEN-005 Rechte | Aktionsrechte, Admin-Grenzen, verborgene Pflichtfelder und Sonderfälle der Organisationssicht; bestätigte Feldverbote erhalten | Technik offen | Vor Datenmodell und Rechteimplementierung |
| OPEN-006 Identitäten | Konto-/LDAP-Zuordnung, Deaktivierung, Einladungen, Recovery und Ausfallfall | Technik, LDAP-Betreiber offen | Vor Anmeldung und Benutzerverwaltung |
| OPEN-007 Projektregeln | Feldtypen, Versionierung, sichere Änderungen vorhandener Konfiguration und erlaubte Automationen | Fachentscheidung Holger, Technik offen | Vor Workflow- und Feldmodell |
| OPEN-008 SLA | Start, Antwortdefinition, Budgets, Kalender, Wiedereröffnung und Eskalationen anhand berechneter Fälle bestätigen | Holger, später Serviceverantwortlicher offen | Vor Helpdesk-Datenmodell |
| OPEN-009 Mail und Portal | Postfach-/Projektzuordnung, Mailanbieter, sichere Absender-/Threadbehandlung, CC, Einladungen und Anhangsgrenzen | Technik und Mailbetreiber offen | Vor Helpdesk- und Portalimplementierung |
| OPEN-010 Git | Lesende Benutzeranbindung, zulässige Objekte, Versionsmatrix und aktuelle Quellenprüfung | Technik offen | Vor Git-Integration |
| OPEN-011 Wiki, Dateien, Suche | Editor-/Dokumentformat, Versionsumfang, Konfliktverfahren, Dateispeicher und Suchstrategie | Technik offen | Vor Speicher- und Editorwahl |
| OPEN-012 Betrieb | Referenzplattform, TLS/Proxy/Internetziele, Update, Sicherungsziel, Servicezeit, Ersatzkapazität und Zuständigkeiten | Betreiber und Technik offen | Vor Installationsarchitektur; praktische Nachweise vor entsprechender Auslieferung |
| OPEN-013 API | HTTP/JSON und OpenAPI prüfen; Versionierung, Authentifizierung, Fehler, Pagination und Wiederholbarkeit konkret beschließen | Technik offen | Vor erster Fachimplementierung |
| OPEN-014 Leistung | Referenzhardware, Mengen, Anhangsprofil und p95-Ziele; Vergleichsprodukt/Version und Bedienaufgaben benennen | Technik und Pilot offen | Vor Architekturfreigabe und Lasttestplanung |
| OPEN-015 Oberfläche und Sprache | Rückfall-/Formatregeln, Vorlagen und Brandinggrenzen, Browser/mobile Ansichten, Zugänglichkeitsziel | Holger; Technik für Umsetzbarkeit | Vor UI- und Vorlagensystem |
| OPEN-016 Lebenszyklus und Import | Aufbewahrung/Löschen jetzt; Redmine-Version, Mapping und Detailumfang vor Import | Betreiber und Technik offen | Aufbewahrung vor Betriebskonzept; Importdetails vor Migrationsrelease |
| OPEN-017 Technische Prüfung | Gegenseitige KI-Reviews bestätigt; konkrete Zuordnung, Nachweise und Schwellen offen; zusätzliche menschliche Prüfung offen | Koordination ordnet KI-Reviews zu, Holger entscheidet Detailverfahren | Detailverfahren vor Implementierung; menschliche Prüfung ausdrücklich offen |
| OPEN-018 Zusammenarbeit | Erweitertes Review-/Übergabeverfahren entscheiden; bestehende Claims und getrennte Worktrees weiter nutzen | Holger / ausdrücklich benannte Koordination | Vor paralleler Entwicklung |
| OPEN-019 KI-Daten und Kosten | Anbieterbezogene Datenfreigaben, Schlüssel-/Kostenträger, Limits, Logs, Löschung und Ersatzmodelle | Betreiber und Technik offen | Vor erster KI-Auslieferung; Daten-/Schlüsselgrenzen schon vor deren Implementierung festlegen |
| OPEN-020 Produkt und Wirtschaft | Name, Lizenz, Pilot, Budget, Termin- und Supportmodell; Preisvorteil erst kalkulieren | Holger | Vor verbindlichem Produktplan; Lizenzfrage vor entsprechender Code-/Komponentennutzung entscheiden |
| OPEN-021 Fachdetails und Roadmap | Kanban/Gantt/einfache Dashboards priorisiert; Inventar/V1-Ergänzungen, Detailumfänge und übrige Prioritäten prüfen | Holger | Inventar vor Implementierungsstart; V1-Details vor jeweiliger Umsetzung |

## Empfohlene Entscheidungspakete

**Grundlage:** OPEN-001, OPEN-002, OPEN-013, OPEN-017 und OPEN-018 zusammen prüfen. Java/Spring Boot ist bestätigt. React/TypeScript, modularer Monolith, PostgreSQL-Jobs und HTTP/JSON/OpenAPI bilden die noch vorgeschlagene Ergänzung. Die Zustimmung zur Sprache allein gibt weder alle Komponenten noch deren Versionen frei. Ergebnis ist eine nachvollziehbare Auswahl mit Review-Verantwortung und begrenztem erstem Entwicklungsauftrag.

**Rechte und Fachmodell:** OPEN-005 bis OPEN-009 mit OPEN-011 und den relevanten Teilen von OPEN-021 prüfen. Entscheidend sind Organisations-/Projektgrenzen, Identität, Konfigurationsversionen, Nachrichtenempfänger, Kalender und Revisionen. Diese Verträge beeinflussen mehrere Module und dürfen nicht widersprüchlich von verschiedenen KIs implementiert werden. Eine Entscheidung nennt die konkret angenommenen beziehungsweise geänderten Beispiele.

**Betrieb, Daten und Messung:** OPEN-012, OPEN-014, OPEN-016 und OPEN-019 zusammenführen. Die 4-vCPU-/16-GB-Referenz sowie vorgeschlagene Antwortzeiten im [Betriebsentwurf](OPERATIONS.md) bleiben Vorschläge; 16 GB ist keine Obergrenze. Datenbank, Dateien und Schlüssel benötigen einen gemeinsamen Wiederherstellungsstand. Ein RTO von vier Stunden braucht erreichbares Personal und passende Ersatz-/Transferkapazität. Die bestätigten Ziele ersetzen diesen Nachweis nicht.

**Lieferumfang:** OPEN-003, OPEN-010, OPEN-015, OPEN-020 und OPEN-021 für die verbindliche V1-Planung konkretisieren; spätere Teile von OPEN-004 und OPEN-016 rechtzeitig vor den frühen Erweiterungen entscheiden. Die Freigabe eines V1-Entwurfs soll genau benennen, welche V-Anforderungen aufgenommen, verändert oder abgelehnt werden. Eine Ablehnung darf einen bestätigten Kernablauf nicht unbemerkt unmöglich machen.

## Nachweise und Abnahmeweg

| Prüfgegenstand | Vorhandene Unterlage | Was noch fehlt |
|---|---|---|
| Funktionsumfang und Prioritäten | [Inventar](FUNCTION_INVENTORY.md), [Roadmap](../ROADMAP.md) | Holgers Bewertung der V-Ergänzungen, Detailumfänge und übrigen Ausbaureihenfolge |
| Architektur, Zugriff und Fachobjekte | [Architektur](ARCHITECTURE.md), [Rechte](PERMISSIONS.md), [Fachmodell](DOMAIN_MODEL.md) | Weitere Entscheidungen und gegenseitige KI-Prüfung; keine Implementierungsnachweise vorhanden |
| Mail und Fristen | [Helpdesk/SLA](HELPDESK_SLA.md) mit berechneten Beispielen | Fachliche Bestätigung der Detailregeln; tatsächliche Mail-/Kalenderintegration später testen |
| Wissen, Sprache und Marke | [Wiki/I18n/Branding](WIKI_I18N_BRANDING.md) | Editor-/Vorlagenentscheidung und später sichtbare Bedienprüfung |
| KI | [KI-Anwendungsfälle](AI_USE_CASES.md) | Anbieterfreigabe, Daten-/Kostenregeln, spätere Qualitäts-/Negativtests |
| Git und Import | [Git/Migration](GIT_MIGRATION.md) | Versions-/Zugangsentscheidungen, später Integration und Importprobelauf |
| Betrieb und Leistung | [Betrieb](OPERATIONS.md) mit nachvollziehbaren Planrechnungen | Freigegebenes Messprofil, Betreiber und praktische Update-/Restore-/Lastnachweise |
| KI-Zusammenarbeit | [Arbeitsablauf](AI_WORKFLOW.md), [Review-Vorschlag](DEVELOPMENT_REVIEW.md) | Konkrete KI-Review-Zuordnung und zusätzliche Detailregeln; menschliche Prüfung weiterhin offen |

Die zehn übergreifenden Szenarien ACC-001 bis ACC-010 in REQUIREMENTS.md bleiben der fachliche rote Faden. Spezifische Rechte-, SLA-, Wiki-, KI-, Git- und Betriebsfälle ergänzen sie. Ein späteres Ergebnis zeigt Holger nachvollziehbar, welcher Ablauf funktioniert, welche Fehlerfälle geprüft wurden und welche Grenzen bleiben. Es gibt noch keine ausgeführten Anwendungstests, Lastmessungen oder Wiederherstellungsübungen.

## Wann aus Planung ein Entwicklungsauftrag werden kann

Vor einem Implementierungsstart müssen die fälligen Grundlagen entschieden, die fachlich angenommenen Anforderungen/Beispiele kenntlich gemacht und die Prüfverantwortung geklärt sein. Verbleibende spätere Fragen brauchen einen benannten Entscheider und dürfen dem ersten Auftrag nicht widersprechen. Eine separate ausdrückliche Beauftragung legt fest, welcher Teil umgesetzt werden darf. Architekturfreigabe, Produktimplementierung und Kundenauslieferung sind getrennte Entscheidungen.

**Vorschlag für die erste spätere Lieferung:** Ein berechtigter interner Benutzer meldet sich an, wählt ein Projekt, erstellt ein Ticket und findet es wieder; ein unberechtigter Benutzer wird über UI und API abgewiesen. Deutsch/Englisch, Konflikterkennung und ein reproduzierbarer Docker-Start werden dabei früh überprüfbar. Das wäre ein interner Entwicklungsstand, keine verkaufbare V1. Danach folgen die übrigen bestätigten Fachmodule in prüfbaren Schritten. Hier werden weder Anwendungscode noch neue Implementierungsaufgaben angelegt.

Nach jeder ausdrücklichen Entscheidung wird eine datierte DEC-ID ergänzt und die betroffenen Anforderungen und Entwürfe werden konsistent aktualisiert. Nur tatsächlich angenommene Vorschläge wechseln ihren Status. Ein zusammengeführter PR oder erfolgreiche CI bewirkt diesen Wechsel nicht. TASK-012 bleibt zur Prüfung, solange seine Kriterien „kritische Architekturfragen entschieden“ und „fachlich freigegebener Umfang sichtbar“ nicht durch konkrete Abnahme erfüllt sind.
