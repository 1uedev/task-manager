# Entscheidungen und offene Fragen

Stand 13. Oktober 2026 · Erste Erfassung und Erweiterung um die GitHub-Arbeitsumgebung

Diese Datei dokumentiert bestätigte Entscheidungen und noch offene Auswahlfragen. Sie ergänzt die zentrale Spezifikation in [REQUIREMENTS.md](REQUIREMENTS.md). Die ursprünglichen Einzeltermine früherer Gesprächsentscheidungen werden nicht nachträglich erfunden; der genannte Stand ist das Erfassungsdatum. Umsetzung und fachliche Abnahme sind daraus nicht abzuleiten.

## Bestätigte Entscheidungen

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-001 | Eigenständiges Produkt mit Easy8 beziehungsweise Easy Redmine als Funktionsreferenz; zuerst Kern, dann schrittweise Erweiterungen | REQ-PROD-001, REQ-PROD-003 |
| DEC-002 | DACH-KMU mit 10 bis 100 Nutzern, Schwerpunkt IT, Software und technische Projektteams | REQ-PROD-001, REQ-PERF-001 |
| DEC-003 | V1 umfasst Tickets, Zeiterfassung, E-Mail-Helpdesk, Kundenportal, Dokumentation, Anforderungen und Git-Verknüpfung | REQ-PROD-002 |
| DEC-004 | Docker-Auslieferung, zunächst überwiegend beim Kunden; Wartung durch Anbieter oder Kunde | REQ-OPS-001, REQ-OPS-002 |
| DEC-005 | Internetzugang darf Voraussetzung sein; konkrete Zwecke und Ausfallregeln bleiben offen | REQ-OPS-003 |
| DEC-006 | Eine Installation pro Unternehmen, spätere Mandantenfähigkeit architektonisch vorbereiten | REQ-ORG-001, REQ-ORG-002 |
| DEC-007 | Kundenadministratoren verwalten Gruppen, Rechte und projektspezifische Konfiguration; einzelne Felder sind ausblendbar | REQ-ORG-003, REQ-ACL-001, REQ-ACL-003 |
| DEC-008 | Interne Anmeldung und LDAP in V1, spätere Anmeldeverfahren erweiterbar; 2FA sehr zeitnah, konkreter Release offen | REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003, REQ-AUTH-004 |
| DEC-009 | PostgreSQL ist gesetzt; MariaDB, SQL Server und Oracle nur architektonisch vorbereiten | REQ-DATA-001, REQ-DATA-002 |
| DEC-010 | React vorgesehen; Backend Go oder Java noch nicht entschieden | REQ-ARCH-001 |
| DEC-011 | Deutsch und Englisch von Anfang an, weitere Sprachen einfach ergänzbar; Wiki-Sprachfassungen freiwillig | REQ-I18N-001, REQ-I18N-002, REQ-DOC-003 |
| DEC-012 | Logo und Farben für Anwendung, Portal, Anmeldung und E-Mails; später PDFs auf Vorlagenbasis | REQ-BRAND-001, REQ-BRAND-002 |
| DEC-013 | IMAP/SMTP reichen für V1; SLA-Fristen, Geschäftszeiten und Eskalationen sind wichtig und Teil von V1 | REQ-HD-001, REQ-HD-003, REQ-HD-004, REQ-HD-005 |
| DEC-014 | GitHub und GitLab zunächst für Zuordnung und Anzeige; spätere Erweiterbarkeit erforderlich | REQ-GIT-001, REQ-GIT-002, REQ-GIT-003 |
| DEC-015 | Keine Datenmigration in V1; zeitnah danach vorsehen | REQ-MIG-001 |
| DEC-016 | Keine gemeinsame Echtzeitbearbeitung im Wiki erforderlich | REQ-DOC-002 |
| DEC-017 | Anforderungen in V1 mit Kriterien, Beziehungen, einfachem Freigabestatus und Historie; formale eingefrorene Stände und Änderungsanträge später | REQ-REQ-001, REQ-REQ-002, REQ-REQ-003, REQ-REQ-004 |
| DEC-018 | KI-Nutzen je Anwendungsfall betrachten; Administratoren hinterlegen Markdown-Anweisungen und wählen Anbieter und Modelle | REQ-AI-001, REQ-AI-002, REQ-AI-003 |
| DEC-019 | KI-Anbieter OpenAI, Anthropic beziehungsweise Claude und Ollama; Änderungen und Versand nur nach ausdrücklicher Bestätigung | REQ-AI-003, REQ-AI-004 |
| DEC-020 | 500 bis 1.500 neue Tickets monatlich, ungefähr 15 gleichzeitig aktive Nutzer | REQ-PERF-001 |
| DEC-021 | Keine feste RAM-Grenze; die frühere Annahme maximal 16 GB wurde ausdrücklich korrigiert. Bis zu 1 TB SSD bleibt bisheriger Planungsrahmen | REQ-PERF-002 |
| DEC-022 | Entwicklung überwiegend durch Codex und Claude Code, teilweise gleichzeitig; Repository auf GitHub, fachliche Abnahme durch Holger Gerlach | REQ-DEV-001, REQ-DEV-004 |
| DEC-023 | Zentrale TODO.md und DONE.md ermöglichen die Nachverfolgung über KI-Sitzungen hinweg | REQ-DEV-002 |
| DEC-024 | Aktueller Auftrag ist Planung und Anforderungserstellung; noch keine Implementierung | REQ-PROD-003, REQ-DEV-004 |

## Ergänzung vom 13. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-025 | Der Auftraggeber hat https://github.com/1uedev/task-manager angelegt und die Einrichtung als gemeinsame Arbeitsumgebung beauftragt. Repository-Regeln, Aufgaben, Vorlagen und Strukturprüfungen dürfen eingerichtet werden. DEC-024 wird um diese Infrastrukturarbeit erweitert; eine Implementierung des Produkts ist weiterhin nicht freigegeben. | REQ-DEV-001, REQ-DEV-002, REQ-DEV-003 |
| DEC-026 | Am 13. Oktober 2026 hat der Auftraggeber die öffentliche Veröffentlichung der vorbereiteten Anforderungen, Roadmap, Entscheidungen, Aufgabenlisten und KI-Arbeitsregeln im öffentlichen Repository 1uedev/task-manager ausdrücklich bestätigt. | REQ-DEV-001, REQ-DEV-002 |

## Offene Entscheidungen

Die nachstehenden Punkte müssen nicht alle beantwortet sein, um die erste Spezifikation zu prüfen. Jeder Punkt nennt, bis wann er für verlässliche Umsetzung oder Auslieferung geklärt sein muss. Vorschläge sind keine vorweggenommene Entscheidung.

### OPEN-001 Backend und Entwicklungswerkzeuge

**Status:** offen · **Fällig:** vor Implementierung · **Bezug:** REQ-ARCH-001, REQ-DEV-005

Zu entscheiden sind Go oder Java, Framework, Sprachversionen, Frontend mit oder ohne TypeScript sowie Build- und Testkonventionen. Bisheriger Vorschlag: Java mit Spring Boot und React mit TypeScript. Die Begründung muss Domänenkomplexität, Betrieb, verfügbare Kenntnisse, Prüfbarkeit und laufende Kosten gegeneinander abwägen. Eine vermeintliche KI-Präferenz genügt nicht.

### OPEN-002 Modulstruktur und spätere Mandanten

**Status:** offen · **Fällig:** vor Implementierung · **Bezug:** REQ-ORG-002, REQ-ARCH-002

Zu entscheiden sind fachliche Modulgrenzen, Hintergrundverarbeitung und Vorbereitung der Unternehmensgrenzen. Ausgangsvorschlag ist ein modularer Monolith. Die konkrete spätere Isolation über eigene Datenbanken, Schemas oder gemeinsam genutzte Tabellen bleibt offen. Für V1 soll daraus kein unnötiger Mehrmandantenbetrieb entstehen.

### OPEN-003 Erster KI Lieferumfang

**Status:** offen · **Fällig:** vor verbindlicher V1-Planung · **Bezug:** REQ-AI-001 bis REQ-AI-009

Welche Anwendungsfälle werden bereits in V1 ausgeliefert, und wann stehen alle drei bestätigten Anbieter zur Verfügung? Vorschlag zur Priorisierung: Ticketzusammenfassung, Antwortentwurf, Anforderungsprüfung und Wiki-Überarbeitung. Die Verfügbarkeit für externe Portalnutzer und die Möglichkeit automatischer Vorschlagsgenerierung sind gesondert zu entscheiden. Automatische Änderungen bleiben ausgeschlossen.

### OPEN-004 Zwei Faktor Authentifizierung

**Status:** offen · **Fällig:** vor verbindlicher V1-Planung · **Bezug:** REQ-AUTH-004

Zu entscheiden sind Liefertermin, Verfahren, verpflichtende Nutzergruppen und Wiederherstellung. Empfehlung: vor regulärem Kundeneinsatz, besonders für administrative Konten. Die bisherige Nutzerzusage lautet sehr zeitnah, nicht ausdrücklich V1.

### OPEN-005 Zusammenspiel der Berechtigungen

**Status:** offen · **Fällig:** vor Datenmodell und Rechteimplementierung · **Bezug:** REQ-ACL-001 bis REQ-ACL-005

Wie wirken mehrere Gruppen zusammen, und gibt es ausdrückliche Verbote? Welche Administrationsrechte sind global oder projektbezogen? Sehen externe Nutzer nur eigene, organisationsbezogene oder projektweit freigegebene Tickets? Wie werden verborgene Pflichtfelder und organisationsübergreifende Kontakte behandelt? Ausgangspunkt für die Spezifikation ist fehlender Zugriff ohne erteilte Berechtigung.

### OPEN-006 Konten und LDAP Lebenszyklus

**Status:** offen · **Fällig:** vor Anmeldung und Benutzerverwaltung · **Bezug:** REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003

Zu klären sind lokale Einladungen, Kontowiederherstellung, Kontoanlage aus LDAP, stabile Identitätszuordnung, gegebenenfalls LDAP-Gruppenzuordnung, Deaktivierung und Ausfallverhalten. Ein geschützter lokaler Wiederherstellungsweg für Administratoren ist zu bewerten. Eine konkrete LDAP-Servervariante ist noch nicht benannt.

### OPEN-007 Projektkonfiguration und Automationen

**Status:** offen · **Fällig:** vor Workflow- und Feldmodell · **Bezug:** REQ-ORG-003, REQ-ORG-004, REQ-TKT-001

Zu definieren sind Feldtypen, Validierungen, Formularregeln, Übergänge, Projektvorlagen, erlaubte Automationen und der Umgang mit vorhandenen Daten nach Regeländerungen. Vorschlag: fachlich begrenzte Regeln mit dokumentierten Aktionen; beliebiger Kundencode ist kein bestätigter Bedarf.

### OPEN-008 SLA Berechnungsregeln

**Status:** offen · **Fällig:** vor Helpdesk-Datenmodell · **Bezug:** REQ-HD-003, REQ-HD-004, REQ-HD-005

Benötigt werden konkrete Beispiele für erste Antwort und Lösung, Servicekalender, Feiertage, Zeitzonen, Pausen, Wiedereröffnung, Prioritätsänderung und Eskalationsstufen. Zu entscheiden ist, ob Regeln nach Kunde, Projekt, Vertrag oder Tickettyp zugeordnet werden. Fristverhalten muss vor Implementierung anhand von Beispielen berechenbar sein.

### OPEN-009 Postfächer und Portalzugang

**Status:** offen · **Fällig:** vor Helpdesk- und Portalimplementierung · **Bezug:** REQ-HD-001, REQ-HD-002, REQ-PORTAL-001, REQ-PORTAL-003

Zu entscheiden sind Anzahl und Zuordnung der Postfächer, Umgang mit unbekannten Absendern, Einladungen, Selbstregistrierung, öffentliche Anfragen, CC-Empfänger, Weiterleitungen und Anhänge. IMAP/SMTP sind bestätigt; die erlaubten Authentifizierungsverfahren des vorgesehenen Mailanbieters müssen praktisch geprüft werden.

### OPEN-010 Varianten der Git Dienste

**Status:** offen · **Fällig:** vor Git-Integration · **Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003

GitHub und GitLab sind bestätigt. Offen sind SaaS beziehungsweise selbst betriebene Varianten, Authentifizierungsweg, unterstützte Objektarten, automatische Zuordnung, Aktualisierung und Sichtbarkeit importierter Metadaten. Es werden keine Git-Schreibfunktionen für V1 vorausgesetzt.

### OPEN-011 Wiki Dateien und Suche

**Status:** offen · **Fällig:** vor Speicher- und Editorwahl · **Bezug:** REQ-DOC-001 bis REQ-DOC-004, REQ-DATA-003, REQ-TKT-004

Zu entscheiden sind Editorumfang, gespeichertes Dokumentformat, Import/Export, Konfliktverfahren, Dateispeicher, Versionsumfang und Suchstrategie. Anhänge benötigen ein reales Größenprofil, damit die 1-TB-Planung bewertet werden kann. Eine zusätzliche Suchdatenbank wird nicht automatisch vorausgesetzt.

### OPEN-012 Betriebsmodell und Verfügbarkeit

**Status:** offen · **Fällig:** vor Installationsarchitektur · **Bezug:** REQ-OPS-001 bis REQ-OPS-006

Benötigt werden Zielplattformen und CPU-Architekturen, Container-Betriebsumgebung, TLS-Zuständigkeit, Proxy und Internetziele, Updateverfahren, Wartungsfenster, Fernzugriffsweg, Sicherungsziel, tolerierter Datenverlust und Wiederanlaufzeit. Ein Internetbedarf begründet keine unbestimmte Pflicht zur Datenübermittlung oder Telemetrie.

### OPEN-013 API Vertrag

**Status:** offen · **Fällig:** vor erster Fachimplementierung · **Bezug:** REQ-API-001, REQ-API-002

Zu bestimmen sind API-Stil, Dokumentationsformat, Versionierung, Authentifizierung, Fehlerformat, Seitennavigation, Wiederholbarkeit von Schreibaufrufen und spätere Ereignisschnittstellen. API- und UI-Rechte müssen auf denselben fachlichen Regeln beruhen.

### OPEN-014 Messbare Leistungsziele

**Status:** offen · **Fällig:** vor Architekturfreigabe und Lasttestplanung · **Bezug:** REQ-PROD-004, REQ-PERF-001 bis REQ-PERF-004

Zu entscheiden sind Zielzeiten und Referenzhardware, akzeptable Verzögerungen für Mail- und SLA-Verarbeitung sowie Daten- und Anhangsprofil. Für einen Vergleich mit Redmine oder Easy8 sind konkrete Version, Konfiguration und Testdaten nötig. Keine RAM-Obergrenze bedeutet nicht, dass Ressourcenverbrauch unbewertet bleibt.

### OPEN-015 Sprachen Vorlagen und Bedienung

**Status:** offen · **Fällig:** vor UI- und Vorlagensystem · **Bezug:** REQ-I18N-001 bis REQ-I18N-003, REQ-BRAND-001, REQ-BRAND-002, REQ-UX-001

Zu bestimmen sind Standardsprache, Rückfallregeln, Übersetzungsprozess für Kundenkonfiguration, Zeitzonenverhalten, Vorlagenbearbeitung, erlaubte Designanpassungen, Browser und mobile Mindestansichten. Ein verbindliches Barrierefreiheitsziel bleibt zu entscheiden.

### OPEN-016 Datenaufbewahrung und Migration

**Status:** offen · **Fällig:** Aufbewahrung vor Betriebskonzept; Importumfang vor Migrationsrelease · **Bezug:** REQ-DATA-004, REQ-MIG-001, REQ-MIG-002

Zu bestimmen sind Lösch- und Aufbewahrungsregeln sowie Reihenfolge, Versionen und Datenarten der Importquellen. Redmine, Easy8 beziehungsweise Easy Redmine und Jira müssen je Quelle bewertet werden. Vollständige Historie, Rechteabbildung und Anhänge sind keine ungeprüfte Importzusage.

### OPEN-017 Technisches Review und Freigaben

**Status:** offen · **Fällig:** vor Implementierung · **Bezug:** REQ-SEC-001, REQ-DEV-004, REQ-DEV-005

Holger übernimmt die fachliche Abnahme. Offen ist, wer sicherheitskritische Architektur, Rechte, Anmeldung, Datenmigration und Wiederherstellung technisch prüft und welche Nachweise dafür nötig sind. Fachliche Abnahme und technischer Review sollen sich ergänzen.

### OPEN-018 Koordination der KI Arbeit

**Status:** offen · **Fällig:** vor paralleler Entwicklung · **Bezug:** REQ-DEV-001, REQ-DEV-002, REQ-DEV-003

Repository: 1uedev/task-manager. Integrationsbranch: main. Der technische Einrichtungsauftrag ist DEC-025. Das in docs/AI_WORKFLOW.md beschriebene Verfahren verwendet reservierende Pull Requests, bevor eine KI eine Aufgabe bearbeitet, sowie getrennte Arbeitsstände. TODO.md auf main bleibt maßgeblich. Das Verfahren ist organisatorisch; eine atomare Task-Sperre und die Identifikation einzelner KI-Sitzungen über GitHub-Benutzerkonten werden nicht behauptet. Koordination: Holger beziehungsweise eine von ihm ausdrücklich benannte Sitzung. Die weitergehenden fachlichen und technischen Freigaberegeln bleiben unter OPEN-017 zu entscheiden.

### OPEN-019 KI Betrieb und Nutzungsgrenzen

**Status:** offen · **Fällig:** vor erster KI-Auslieferung · **Bezug:** REQ-AI-003, REQ-AI-005, REQ-AI-007, REQ-AI-008

Zu entscheiden sind erlaubte Datenklassen pro Anbieter, Herkunft der API-Zugangsdaten, Kostenträger, Nutzungsgrenzen, Zeitlimits, Inhaltsprotokollierung, Löschung und zulässige Ersatzmodelle. Eine gemeinsame Provider-Schnittstelle macht Modelle nicht automatisch austauschbar.

### OPEN-020 Produkt und kommerzieller Rahmen

**Status:** offen · **Fällig:** vor verbindlichem Produktplan · **Bezug:** REQ-PROD-001, REQ-PROD-002, REQ-OPS-003

Produktname, Lizenzmodell, Preislogik, Pilotkunde, Budget, Zeitrahmen und Supportumfang sind offen. Diese Angaben verhindern den ersten Anforderungskatalog nicht. Sie sind für eine belastbare Release- und Wirtschaftlichkeitsplanung erforderlich. Eine günstigere Alternative ist ein Ziel, kein bereits kalkulierter Preisvorteil.

### OPEN-021 Detailumfang der Fachmodule und späteren Funktionen

**Status:** offen · **Fällig:** V1-Details vor deren Umsetzung; vollständiges Funktionsinventar vor Implementierungsstart · **Bezug:** REQ-PROD-003, REQ-TIME-001 bis REQ-TIME-003, REQ-REQ-001 bis REQ-REQ-004, REQ-EXT-001

Zu konkretisieren sind Standardfelder, Ticketbeziehungen, Zeitkorrekturen, Exportumfang und Verhalten nach Änderung einer freigegebenen Anforderung. Alle als Vorschlag bezeichneten V1-Ergänzungen und Kandidaten späterer Module müssen priorisiert werden. Die Freigabe des einfachen Anforderungsmoduls bedeutet keine Freigabe eines beliebig komplexen Genehmigungsworkflows.

## Entscheidungsverfahren

Neue Entscheidungen bekommen eine neue DEC-ID mit Datum, Verantwortlichem, Begründung und betroffenen REQ-IDs. Bereits bestätigte Entscheidungen werden nicht still geändert. Eine Ablösung nennt Vorgänger und Nachfolger sowie ihre Folgen für Anforderungen, Roadmap und offene Aufgaben. Technische Recherche wird bei der jeweiligen Entscheidung mit Quelle und Abrufdatum dokumentiert; diese erste Fassung enthält Gesprächsentscheidungen und Entwurfsvorschläge, keine neuen Produkt- oder Preisrecherchen.
