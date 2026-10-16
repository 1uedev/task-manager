# Roadmap für das Projektmanagementprodukt

Stand 16. Oktober 2026 · Vorläufige Reihenfolge ohne Kalenderzusage

Diese Roadmap ordnet die Anforderungen aus [REQUIREMENTS.md](REQUIREMENTS.md). Sie unterscheidet den bestätigten V1-Umfang von Vorschlägen und späteren Funktionen. Die nötige Entwicklungskapazität, das Budget und ein Pilottermin sind noch offen. Das [Funktionsinventar](docs/FUNCTION_INVENTORY.md) enthält den Kern, priorisierte V1-Ergänzungen und spätere Bereiche mit Grenzen und Abnahmebeispielen. Seine fachliche Prüfung ist vor Implementierungsstart erforderlich.

## Phase 0 Anforderungen und Architektur

**Aktueller Stand:** Fachentwürfe, vollständige Bereichsinventur und Review-Vorschlag liegen zur Prüfung vor; die [Entscheidungsübersicht](docs/ARCHITECTURE_READINESS.md) bündelt die Freigabepunkte. GitHub-Arbeitsumgebung ist eingerichtet. Architekturentscheidung, fachliche Gesamtabnahme und Produktimplementierung sind noch offen.

**Ergebnis:** abgestimmte Anforderungen, fachliche Abnahmeszenarien, priorisiertes Funktionsinventar, Modul- und Datenkonzept, Rechtekonzept, Betriebsrahmen und Arbeitsverfahren für mehrere KIs.

**Vor dem Übergang:** kritische Entscheidungen aus DECISIONS.md sind getroffen oder mit klarer Zuständigkeit und spätestem Termin versehen. Backend, Modulstruktur, API-Grundvertrag, Berechtigungsmodell, relevante Daten- und Betriebsgrenzen sind entschieden. Eine gesonderte Beauftragung erlaubt die Implementierung.

**Bezug:** REQ-PROD-003, REQ-ARCH-001, REQ-ARCH-002, REQ-ORG-002, REQ-DEV-001 bis REQ-DEV-005.

## Version 1 Erste verkaufbare Version

Der folgende Umfang ist bestätigt. Die Reihenfolge innerhalb der Entwicklung wird nach der Architekturentscheidung festgelegt.

| Bereich | Bestätigter Umfang | Wichtige Abhängigkeiten |
|---|---|---|
| Installation und Daten | Docker, PostgreSQL, einzelne Unternehmensinstallation, spätere Erweiterbarkeit; Wiederherstellungsziel höchstens 1 Stunde Datenverlust und 4 Stunden Wiederanlauf | Betriebs-/Datenkonzept und praktischer Wiederherstellungsnachweis; DEC-038 |
| Identität und Rechte | Internes Konto, LDAP, Gruppen, Projektberechtigungen, verborgene Felder | Identitätsmodell und Konfliktregeln |
| Projekte und Tickets | Konfigurierbare Projekte, Tickettypen, Workflows, Felder, Formulare und Automationen | Rechte, API und Fachmodell |
| Zeiterfassung | Erfassung im Projekt- und Ticketzusammenhang | Benutzer, Tickets und Zeitregeln |
| Helpdesk | IMAP/SMTP, Prüfwarteschlange für unbekannte Absender, SLA-Fristen, Geschäftszeiten und Eskalationen; Kundenpause hält die Lösungsfrist an, offene Erstreaktion läuft weiter | Tickets, Hintergrundjobs und konkrete SLA-Regeln; DEC-031, DEC-032 |
| Kundenportal | Berechtigte externe Teilnahme; standardmäßig Anfragen der eigenen Kundenorganisation in freigegebenen Projekten | Identität, Rechte und Helpdesk; DEC-027 |
| Wiki | Moderne Textbearbeitung, Entwurf mit ausdrücklicher Veröffentlichung, Historie und Konfliktschutz, optionale Sprachfassungen | Rechte, Dateispeicher und Editorentscheidung; DEC-033 |
| Anforderungen | Kriterien, einfache Freigabe, Historie und Verknüpfungen; inhaltliche Änderungen freigegebener Anforderungen erhalten eine neue Revision zur erneuten Freigabe | Tickets, Wiki und Git; DEC-030 |
| Git | GitHub.com, GitLab.com und selbst betriebenes GitLab für Zuordnung und Anzeige | Sichere Anbieteranbindung, Quellversionen und Sichtbarkeitsregeln; DEC-036 |
| Sprachen und Marke | Deutsch und Englisch, zusätzliche Sprachen vorbereiten, Logo und Farben | UI, Portal, Anmeldung und Vorlagen |
| API | Dokumentierte Kernabläufe von Anfang an | Gemeinsame Fachlogik und Rechte |
| KI | Ticketzusammenfassung, Antwortentwurf, Anforderungsprüfung und Wiki-Überarbeitung für interne berechtigte Benutzer; Skills und Modellzuordnung durch Administratoren | Kontextrechte, Bestätigung und mindestens eine qualifizierte Anbieteranbindung; DEC-034, DEC-035 |

**Noch für V1 zu entscheiden:** Lieferreihenfolge der drei KI-Anbieter, Datenfreigaben und Betriebsgrenzen sowie alle in REQUIREMENTS.md als V markierten Ergänzungen. Die vier KI-Anwendungsfälle und der interne Nutzerkreis sind durch DEC-034 und DEC-035 bestätigt; die gleichzeitige Lieferung aller drei Anbieter ist noch offen. 2FA folgt gemäß DEC-029 zeitnah nach V1; V1 bereitet die spätere Ergänzung in der Anmeldearchitektur vor. Für Feldrechte gilt bereits der bestätigte Vorrang ausdrücklicher Verbote aus DEC-028.

**Wiederherstellung:** DEC-038 legt Planungsziele fest, keine gemessene Verfügbarkeit. Sicherungsziel, Ersatzkapazität und erreichbares Betriebspersonal müssen zum zugesagten Servicezeitraum passen.

**Vorgeschlagene Auslieferungsbedingungen:** fachlich geprüfte Kernabläufe, nachgewiesene Rechteisolation, reproduzierbare Installation, Update und Wiederherstellung sowie definierte und gemessene Leistungsziele. Die genaue Freigaberegel wird unter OPEN-017 beschlossen.

## Zeitnahe Erweiterungen nach Version 1

| Thema | Status | Ergebnis und Abnahmebeispiel |
|---|---|---|
| Migration aus Bestandssystemen | Zuerst Redmine bestätigt; Version und Detailumfang offen; DEC-037 | Ein ausgewählter Import wird im Probelauf geprüft; übertragene, ausgelassene und fehlerhafte Inhalte sind nachvollziehbar. REQ-MIG-001, REQ-MIG-002 |
| Zwei-Faktor-Authentifizierung | Zeitnah nach V1 bestätigt, konkretes Datum und Verfahren offen; DEC-029 | Geschützte Anmeldung mit dokumentiertem Wiederherstellungsverfahren und LDAP-Zusammenspiel. REQ-AUTH-004 |
| Weitere KI-Anwendungsfälle | Bedarf zur Bewertung bestätigt, Reihenfolge offen | Jeder ausgelieferte Anwendungsfall besitzt passende Skills, Rechteprüfung und Bestätigung; keine automatische Freigabe durch die Roadmap. REQ-AI-001 bis REQ-AI-009 |

Redmine als erste Importquelle ist bestätigt; Easy Redmine/Easy8 und Jira werden anschließend geprüft. Die Reihenfolge gegenüber anderen Erweiterungen ist vorläufig. Ein Importumfang mit komplexer Rechte- und Historienübernahme benötigt eine eigene Aufwandsbewertung.

## Bestätigte spätere Entwicklungsrichtungen

| Thema | Zweck und Grenze | Abhängigkeit und Abnahmebeispiel |
|---|---|---|
| Mehrmandantenfähigkeit | Mehrere Unternehmen innerhalb eines Betriebsmodells | Unternehmensgrenzen müssen auch in Suche, Dateien, Jobs und KI nachgewiesen werden; eine getrennte Nutzersicht allein genügt nicht. REQ-ORG-002 |
| Weitere Anmeldeanbieter | Kundenanforderungen an Anmeldung ergänzen | Anbieterwahl offen; ein neues Verfahren nutzt dieselbe interne Identität und Rechtebasis. REQ-AUTH-003 |
| KI für externe Portalnutzer | Direkte KI-Unterstützung für berechtigte Kunden nach V1 | Eigene Daten- und Empfängergrenzen, Missbrauchs-/Kostenkontrolle und Abnahme; DEC-035, REQ-AI-001, REQ-AI-005 |
| GitHub Enterprise Server | Selbst betriebenes GitHub ergänzen | Eigene Versions-, Netzwerk- und Rechteprüfung; DEC-036, REQ-GIT-001 |
| Weitere Git-Funktionen | Über Anzeige und Zuordnung hinausgehen | Einzelaktionen offen; eine später erlaubte Schreibaktion besitzt eigene Rechte und gegebenenfalls KI-Bestätigung. REQ-GIT-003 |
| Vorlagenbasierte PDF-Ausgabe | Gebrandete Dokumente erzeugen | Ein freigegebener Dokumenttyp lässt sich in der gewünschten Sprache mit geprüftem Layout erzeugen. REQ-BRAND-002 |
| Formale Anforderungsstände | Freigegebene Mengen und Änderungen verbindlich nachvollziehen | Ein eingefrorener Stand bleibt unverändert; spätere Änderungen besitzen einen Genehmigungsweg. REQ-REQ-004 |
| Weitere Datenbanken | Option für MariaDB, SQL Server oder Oracle | Bisher nur Vorbereitung beauftragt; jede tatsächliche Unterstützung braucht gesonderte Entscheidung, Migration und Prüfnachweise. REQ-DATA-002 |

## Vorgeschlagene Reihenfolge weiterer Module

Die Bereiche und Mindestumfänge stehen im [Funktionsinventar](docs/FUNCTION_INVENTORY.md). Alle folgenden Prioritäten sind Vorschläge unter OPEN-021, ohne Kalenderzusage und zusätzlich zu den bestätigten frühen Erweiterungen 2FA und Redmine-Import.

| Reihenfolge | Schwerpunkt | Voraussetzung |
|---|---|---|
| A | Kanban/Sprints, Gantt, einfache Dashboards sowie Ticketvorlagen/Checklisten | Stabile Tickets, Rechte, Kalender und definierte Kennzahlen |
| B | Ressourcen, Kosten, Zeitfreigaben, Tests, Portfolios, Risiken, Assets, Servicekontingente und externe Ereignisse | Bedarf aus Pilotbetrieb, Datenverträge und eigene Abnahme je Modul |
| C | Freie Ideenstruktur, erweitertes CRM, Dokumentverwaltung und native/offline mobile Nutzung | Nachgewiesener Nutzen gegenüber zusätzlichen Integrationen und Wartung |

Formale Anforderungsstände und PDF-Ausgabe sind bereits als spätere Richtungen bestätigt; ihre Einordnung zusammen mit B bleibt vorgeschlagen. Weitere Anmeldeanbieter, Git-Ausbau, Portal-KI und Mehrmandantenbetrieb erhalten ihre Reihenfolge nach konkretem Bedarf. Weitere Datenbanken sind nur architektonisch vorbereitet; eine tatsächliche Unterstützung bleibt gesondert zu beauftragen.

Die als V gekennzeichneten V1-Ergänzungen werden im Inventar zuerst nach Daten-/Rechteintegrität, verlässlicher KI und Entwicklungsgrundlage, danach nach Alltagsabläufen und eng begrenztem Komfort priorisiert. Es wird keine bestätigte V1-Funktion verschoben.

## Roadmap Änderungen

Eine Änderung nennt betroffene REQ-IDs, Begründung, Auswirkungen und Entscheider. Ein Verschieben bestätigter V1-Funktionen ist eine fachliche Entscheidung und darf nicht allein zur Vereinfachung der Umsetzung erfolgen. Erledigte Planungsaufgaben werden nicht als fertig entwickelte Produktfunktionen dargestellt.
