# Roadmap für das Projektmanagementprodukt

Stand 13. Oktober 2026 · Vorläufige Reihenfolge ohne Kalenderzusage

Diese Roadmap ordnet die Anforderungen aus [REQUIREMENTS.md](REQUIREMENTS.md). Sie unterscheidet den bestätigten V1-Umfang von Vorschlägen und späteren Funktionen. Die nötige Entwicklungskapazität, das Budget und ein Pilottermin sind noch offen. Die vollständige Funktionsinventur ist vor Implementierungsstart fertigzustellen; diese erste Roadmap ersetzt sie nicht.

## Phase 0 Anforderungen und Architektur

**Aktueller Stand:** erste Dokumentfassung zur Prüfung vorhanden; GitHub-Arbeitsumgebung beauftragt, Produktimplementierung nicht autorisiert.

**Ergebnis:** abgestimmte Anforderungen, fachliche Abnahmeszenarien, priorisiertes Funktionsinventar, Modul- und Datenkonzept, Rechtekonzept, Betriebsrahmen und Arbeitsverfahren für mehrere KIs.

**Vor dem Übergang:** kritische Entscheidungen aus DECISIONS.md sind getroffen oder mit klarer Zuständigkeit und spätestem Termin versehen. Backend, Modulstruktur, API-Grundvertrag, Berechtigungsmodell, relevante Daten- und Betriebsgrenzen sind entschieden. Eine gesonderte Beauftragung erlaubt die Implementierung.

**Bezug:** REQ-PROD-003, REQ-ARCH-001, REQ-ARCH-002, REQ-ORG-002, REQ-DEV-001 bis REQ-DEV-005.

## Version 1 Erste verkaufbare Version

Der folgende Umfang ist bestätigt. Die Reihenfolge innerhalb der Entwicklung wird nach der Architekturentscheidung festgelegt.

| Bereich | Bestätigter Umfang | Wichtige Abhängigkeiten |
|---|---|---|
| Installation und Daten | Docker, PostgreSQL, einzelne Unternehmensinstallation, spätere Erweiterbarkeit | Betriebs- und Datenkonzept |
| Identität und Rechte | Internes Konto, LDAP, Gruppen, Projektberechtigungen, verborgene Felder | Identitätsmodell und Konfliktregeln |
| Projekte und Tickets | Konfigurierbare Projekte, Tickettypen, Workflows, Felder, Formulare und Automationen | Rechte, API und Fachmodell |
| Zeiterfassung | Erfassung im Projekt- und Ticketzusammenhang | Benutzer, Tickets und Zeitregeln |
| Helpdesk | IMAP/SMTP, SLA-Fristen, Geschäftszeiten und Eskalationen | Tickets, Hintergrundjobs und konkrete SLA-Regeln |
| Kundenportal | Berechtigte externe Teilnahme und Bearbeitung freigegebener Anfragen | Identität, Rechte und Helpdesk |
| Wiki | Moderne Textbearbeitung, Historie und Konfliktschutz, optionale Sprachfassungen | Rechte, Dateispeicher und Editorentscheidung |
| Anforderungen | Kriterien, einfache Freigabe, Historie und Verknüpfungen | Tickets, Wiki und Git |
| Git | GitHub und GitLab für Zuordnung und Anzeige | Sichere Anbieteranbindung und Sichtbarkeitsregeln |
| Sprachen und Marke | Deutsch und Englisch, zusätzliche Sprachen vorbereiten, Logo und Farben | UI, Portal, Anmeldung und Vorlagen |
| API | Dokumentierte Kernabläufe von Anfang an | Gemeinsame Fachlogik und Rechte |

**Noch für V1 zu entscheiden:** konkreter KI-Lieferumfang, 2FA-Termin und alle in REQUIREMENTS.md als V markierten Ergänzungen. Diese Einordnung darf die bestätigten KI-Anbieter und die zeitnahe 2FA-Anforderung nicht aus der Planung entfernen.

**Vorgeschlagene Auslieferungsbedingungen:** fachlich geprüfte Kernabläufe, nachgewiesene Rechteisolation, reproduzierbare Installation, Update und Wiederherstellung sowie definierte und gemessene Leistungsziele. Die genaue Freigaberegel wird unter OPEN-017 beschlossen.

## Zeitnahe Erweiterungen nach Version 1

| Thema | Status | Ergebnis und Abnahmebeispiel |
|---|---|---|
| Migration aus Bestandssystemen | Bedarf bestätigt, Quellsysteme und Umfang offen | Ein ausgewählter Import wird im Probelauf geprüft; übertragene, ausgelassene und fehlerhafte Inhalte sind nachvollziehbar. REQ-MIG-001, REQ-MIG-002 |
| Zwei-Faktor-Authentifizierung | Bedarf bestätigt, falls nicht bereits in V1 | Geschützte Anmeldung mit dokumentiertem Wiederherstellungsverfahren und LDAP-Zusammenspiel. REQ-AUTH-004 |
| Weitere KI-Anwendungsfälle | Bedarf zur Bewertung bestätigt, Reihenfolge offen | Jeder ausgelieferte Anwendungsfall besitzt passende Skills, Rechteprüfung und Bestätigung; keine automatische Freigabe durch die Roadmap. REQ-AI-001 bis REQ-AI-009 |

Diese Reihenfolge ist vorläufig. Ein Importumfang mit komplexer Rechte- und Historienübernahme benötigt eine eigene Aufwandsbewertung.

## Bestätigte spätere Entwicklungsrichtungen

| Thema | Zweck und Grenze | Abhängigkeit und Abnahmebeispiel |
|---|---|---|
| Mehrmandantenfähigkeit | Mehrere Unternehmen innerhalb eines Betriebsmodells | Unternehmensgrenzen müssen auch in Suche, Dateien, Jobs und KI nachgewiesen werden; eine getrennte Nutzersicht allein genügt nicht. REQ-ORG-002 |
| Weitere Anmeldeanbieter | Kundenanforderungen an Anmeldung ergänzen | Anbieterwahl offen; ein neues Verfahren nutzt dieselbe interne Identität und Rechtebasis. REQ-AUTH-003 |
| Weitere Git-Funktionen | Über Anzeige und Zuordnung hinausgehen | Einzelaktionen offen; eine später erlaubte Schreibaktion besitzt eigene Rechte und gegebenenfalls KI-Bestätigung. REQ-GIT-003 |
| Vorlagenbasierte PDF-Ausgabe | Gebrandete Dokumente erzeugen | Ein freigegebener Dokumenttyp lässt sich in der gewünschten Sprache mit geprüftem Layout erzeugen. REQ-BRAND-002 |
| Formale Anforderungsstände | Freigegebene Mengen und Änderungen verbindlich nachvollziehen | Ein eingefrorener Stand bleibt unverändert; spätere Änderungen besitzen einen Genehmigungsweg. REQ-REQ-004 |
| Weitere Datenbanken | Option für MariaDB, SQL Server oder Oracle | Bisher nur Vorbereitung beauftragt; jede tatsächliche Unterstützung braucht gesonderte Entscheidung, Migration und Prüfnachweise. REQ-DATA-002 |

## Kandidaten für das vollständige Funktionsinventar

Die folgenden Bereiche sind zu prüfen, aber noch keine bestätigten Lieferzusagen. Ihre Beschreibung erfüllt die erste Einordnung; die vollständige Spezifikation bleibt eine offene Planungsaufgabe.

| Kandidat | Zweck und Abgrenzung | Abhängigkeiten | Beispiel für spätere fachliche Abnahme |
|---|---|---|---|
| Projektplanung und Gantt | Termine, Meilensteine und Abhängigkeiten planen | Ticketbeziehungen, Kalender und Rechte | Eine Terminänderung macht ihre Auswirkungen sichtbar; Folgetermine werden nach definierten Regeln behandelt |
| Agile Ansichten | Kanban beziehungsweise Sprintplanung für vorhandene Arbeit | Statusmodell, Filter und Teams | Ein Vorgang wechselt im Board nur entlang erlaubter Übergänge |
| Ressourcen und Kapazitäten | Auslastung und Verfügbarkeit planen | Benutzer, Kalender, Projekte und Zeitdaten | Eine Überplanung ist mit nachvollziehbaren Eingabedaten erkennbar |
| Budgets und Kosten | Plan und Ist wirtschaftlich vergleichen | Zeiten, Berechtigungen und Preisregeln | Ein Bericht lässt sich auf Buchungen und festgelegte Sätze zurückführen |
| Berichte und Dashboards | Projekt- und Servicezustand beurteilen | Rechte, Kennzahlendefinitionen und Historie | Zwei Ansichten desselben Zeitraums verwenden dieselbe Kennzahlendefinition |
| Testmanagement | Prüffälle, Ausführungen und Ergebnisse mit Anforderungen verbinden | Anforderungen, Tickets und Versionen | Eine nicht bestandene Prüfung verweist auf Anforderung und Fehlerbehebung |
| Erweiterte Zeitwirtschaft | Timer, Freigaben und Abrechnung | Basis-Zeiterfassung und Kalender | Eine freigegebene Abrechnungsperiode behandelt spätere Korrekturen nachvollziehbar |
| Erweiterte Kundenverwaltung | Organisationen, Kontakte und Servicevereinbarungen verwalten | Portal, Rechte und Helpdesk | Ein Kontakt erhält nur die für seine Kundenorganisation freigegebenen Vorgänge |
| Weitere Integrationen und Ereignisse | Nachbarsysteme zuverlässig anbinden | API, Identität und Hintergrundverarbeitung | Ein wiederholt zugestelltes Ereignis erzeugt keine doppelte Fachaktion |

**KI-Bewertung:** Für jeden freigegebenen Kandidaten wird REQ-AI-001 konkretisiert. KI kann Vorschläge und Erklärungen unterstützen; verbindliche Berechnungen, Rechte und Freigaben bleiben explizite Fachlogik.

## Roadmap Änderungen

Eine Änderung nennt betroffene REQ-IDs, Begründung, Auswirkungen und Entscheider. Ein Verschieben bestätigter V1-Funktionen ist eine fachliche Entscheidung und darf nicht allein zur Vereinfachung der Umsetzung erfolgen. Erledigte Planungsaufgaben werden nicht als fertig entwickelte Produktfunktionen dargestellt.
