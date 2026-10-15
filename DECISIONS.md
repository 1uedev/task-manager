# Entscheidungen und offene Fragen

Stand 15. Oktober 2026 · Bestätigte Produktentscheidungen und offene Detailplanung

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
| DEC-008 | Interne Anmeldung und LDAP in V1, spätere Anmeldeverfahren erweiterbar; ursprünglich 2FA sehr zeitnah mit offenem Release. Die Release-Einordnung wurde am 13. Oktober 2026 durch DEC-029 konkretisiert. | REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003, REQ-AUTH-004 |
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
| DEC-027 | Holger legt am 13. Oktober 2026 die Standardsicht externer Portalnutzer auf alle Anfragen ihrer Kundenorganisation fest. Die bestehenden Projekt- und Funktionsrechte bleiben Voraussetzung; zusätzliche Rechte können Administratoren gezielt vergeben. Damit ist die bisher offene Standardsicht in OPEN-005 geklärt. | REQ-ACL-002, REQ-PORTAL-001 |
| DEC-028 | Holger bestätigt am 13. Oktober 2026: Wenn anwendbare Gruppenrechte ein Feld freigeben und ausdrücklich verbieten, gewinnt das ausdrückliche Verbot. Dies klärt den Feldkonflikt in OPEN-005; eine allgemeine Verbotslogik für alle Aktionen ist damit nicht entschieden. | REQ-ACL-001, REQ-ACL-003 |
| DEC-029 | Holger legt am 13. Oktober 2026 fest: 2FA folgt zeitnah nach Version 1, nicht in V1. Diese Konkretisierung ersetzt die offene Release-Zuordnung in DEC-008; Verfahren und Wiederherstellung bleiben offen. | REQ-AUTH-004 |
| DEC-030 | Holger bestätigt am 13. Oktober 2026: Eine inhaltliche Änderung einer freigegebenen Anforderung erzeugt eine neue Revision zur erneuten Freigabe; die bisherige Freigabe bleibt historisch erhalten. Dadurch ist dieser Teil von OPEN-021 geklärt, ohne das spätere formale Änderungsmanagement vorwegzunehmen. | REQ-REQ-002, REQ-REQ-004 |
| DEC-031 | Holger bestätigt am 13. Oktober 2026: E-Mails unbekannter Absender kommen in eine Prüfwarteschlange; Support prüft und ordnet den Kunden zu. Die Wahl klärt diesen Teil von OPEN-009 und gibt keine automatische Portalberechtigung. | REQ-HD-001, REQ-HD-002, REQ-PORTAL-003 |
| DEC-032 | Holger bestätigt am 13. Oktober 2026: Während auf den Kunden gewartet wird, pausiert die Lösungsfrist; eine noch offene Erstreaktionsfrist läuft während der Geschäftszeiten weiter. Dies klärt die Kundenpause in OPEN-008; konkrete Budgets und übrige Berechnungsregeln bleiben offen. | REQ-HD-003, REQ-HD-004 |

DEC-027 bis DEC-029 beruhen auf Holgers ausdrücklicher Auswahl der zuvor offenen Produktvarianten. Sie schaffen eine eindeutige Basis für Portalfilter, Feldkonflikte und Release-Planung. REQUIREMENTS.md und ROADMAP.md sind entsprechend nachgeführt; der [Rechteentwurf](docs/PERMISSIONS.md) konkretisiert sie mit weiterhin prüfpflichtigen Umsetzungsvorschlägen.

DEC-030 ergänzt diese Entscheidungen nach Holgers ausdrücklicher Auswahl zur Behandlung geänderter Anforderungen. Die Regel hält die Freigabe an den tatsächlich geprüften Inhalt gebunden. REQUIREMENTS.md und ROADMAP.md sind angepasst; Beispiele und weitere Vorschläge stehen im [Fachmodellentwurf](docs/DOMAIN_MODEL.md).

DEC-031 und DEC-032 beruhen ebenfalls auf Holgers ausdrücklicher Auswahl am 13. Oktober. Sie verhindern eine ungeprüfte automatische Kundenzuordnung und unterscheiden Kundenwartezeit von noch ausstehender Erstreaktion. Der [Helpdesk- und SLA-Entwurf](docs/HELPDESK_SLA.md) beschreibt die Folgen und trennt bestätigte Regeln von weiteren Vorschlägen.

## Ergänzung vom 15. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-033 | Holger bestätigt am 15. Oktober 2026: Wiki-Änderungen werden zunächst als Entwurf gespeichert und erst anschließend ausdrücklich veröffentlicht. Speicherung allein verändert die Leserfassung nicht. Dies klärt den Veröffentlichungsablauf in OPEN-011. | REQ-DOC-001, REQ-DOC-002, REQ-DOC-003 |
| DEC-034 | Holger legt am 15. Oktober 2026 Ticketzusammenfassung, Antwortentwurf, Anforderungsprüfung und Wiki-Überarbeitung als KI-Lieferumfang der ersten verkaufbaren Version fest. Die vier Fälle verwenden die bereits bestätigten administrierbaren Skills und Modellzuordnungen. Weitere KI-Funktionen und die Lieferreihenfolge der drei Anbieter bleiben offen. | REQ-PROD-002, REQ-AI-001, REQ-AI-002, REQ-AI-003 |
| DEC-035 | Holger legt am 15. Oktober 2026 fest: In V1 dürfen nur interne berechtigte Benutzer die KI-Funktionen nutzen. Die direkte Nutzung durch externe Kunden im Portal folgt später. Von internem Support ausdrücklich bestätigte Kundenantworten bleiben möglich. | REQ-AI-001, REQ-AI-004, REQ-ACL-002 |

DEC-033 trennt redaktionelle Arbeit von der sichtbaren Leserfassung. Der [Wiki-, Sprach- und Brandingentwurf](docs/WIKI_I18N_BRANDING.md) beschreibt Revisionen, optionale Übersetzungen, Konflikte und Vorlagen. Die weiteren Detailvorschläge sind dadurch nicht pauschal freigegeben.

DEC-034 und DEC-035 klären den ersten fachlichen KI-Umfang und dessen Nutzerkreis. Der [KI-Entwurf](docs/AI_USE_CASES.md) beschreibt die vier Abläufe sowie weiterhin vorgeschlagene Datenwege, Bestätigung, Skills und Betriebsgrenzen. Anforderungen und Roadmap sind nachgeführt.

## Offene Entscheidungen

Die nachstehenden Punkte müssen nicht alle beantwortet sein, um die erste Spezifikation zu prüfen. Jeder Punkt nennt, bis wann er für verlässliche Umsetzung oder Auslieferung geklärt sein muss. Vorschläge sind keine vorweggenommene Entscheidung.

### OPEN-001 Backend und Entwicklungswerkzeuge

**Status:** offen · **Fällig:** vor Implementierung · **Bezug:** REQ-ARCH-001, REQ-DEV-005

Zu entscheiden sind Go oder Java, Framework, Sprachversionen, Frontend mit oder ohne TypeScript sowie Build- und Testkonventionen. Bisheriger Vorschlag: Java mit Spring Boot und React mit TypeScript. Die Begründung muss Domänenkomplexität, Betrieb, verfügbare Kenntnisse, Prüfbarkeit und laufende Kosten gegeneinander abwägen. Eine vermeintliche KI-Präferenz genügt nicht.

Der [Architekturentwurf vom 13. Oktober 2026](docs/ARCHITECTURE.md) liefert den Go/Java-Vergleich mit Primärquellen und empfiehlt Java/Spring Boot sowie React/TypeScript. Die Auswahl ist weiterhin offen; es wurden keine Laufzeitmessungen oder Implementierungen vorgenommen.

### OPEN-002 Modulstruktur und spätere Mandanten

**Status:** offen · **Fällig:** vor Implementierung · **Bezug:** REQ-ORG-002, REQ-ARCH-002

Zu entscheiden sind fachliche Modulgrenzen, Hintergrundverarbeitung und Vorbereitung der Unternehmensgrenzen. Ausgangsvorschlag ist ein modularer Monolith. Die konkrete spätere Isolation über eigene Datenbanken, Schemas oder gemeinsam genutzte Tabellen bleibt offen. Für V1 soll daraus kein unnötiger Mehrmandantenbetrieb entstehen.

Der [Architekturentwurf](docs/ARCHITECTURE.md) beschreibt Datenhoheit je Modul, gerichtete Abhängigkeiten, einen serverseitigen Unternehmenskontext sowie Datenbankadapter. PostgreSQL bleibt allein unterstützt; weitere Datenbanken und mehrere Mandanten werden nicht implementiert.

### OPEN-003 Erster KI Lieferumfang

**Status:** teilweise entschieden · **Fällig:** Anbieterreihenfolge vor verbindlicher V1-Planung · **Bezug:** REQ-AI-001 bis REQ-AI-009

DEC-034 bestätigt Ticketzusammenfassung, Antwortentwurf, Anforderungsprüfung und Wiki-Überarbeitung für V1. DEC-035 begrenzt V1 auf interne berechtigte Benutzer; direkte Portalnutzung folgt später. Offen bleiben die Lieferreihenfolge von OpenAI, Anthropic/Claude und Ollama sowie mögliche spätere automatische Vorschlagsgenerierung. Der [KI-Entwurf](docs/AI_USE_CASES.md) empfiehlt zunächst ausdrückliche Benutzerstarts und beschreibt die vier Abläufe. Mindestens eine geeignete Anbindung muss die V1-Funktionen tragen; eine gleichzeitige Auslieferung aller drei Anbieter ist noch nicht beschlossen. Automatische Änderungen bleiben ausgeschlossen.

### OPEN-004 Zwei Faktor Authentifizierung

**Status:** teilweise entschieden · **Fällig:** Architektur vor Implementierung, Detailverfahren vor 2FA-Release · **Bezug:** REQ-AUTH-004

DEC-029 legt die Auslieferung zeitnah nach V1 fest. Offen bleiben konkreter Termin, Verfahren, verpflichtende Nutzergruppen, Wiederherstellung und LDAP-Zusammenspiel. Die V1-Architektur bereitet die Ergänzung vor; die frühere Empfehlung eines Termins vor regulärem Kundeneinsatz wird nicht als Anforderung weitergeführt.

### OPEN-005 Zusammenspiel der Berechtigungen

**Status:** teilweise entschieden · **Fällig:** vor Datenmodell und Rechteimplementierung · **Bezug:** REQ-ACL-001 bis REQ-ACL-005

DEC-027 bestätigt die Organisationssicht innerhalb freigegebener Projekte; DEC-028 bestätigt den Vorrang ausdrücklicher Feldverbote. Offen bleiben das Zusammenspiel allgemeiner Aktionsrechte, globale und delegierte Administrationsrechte, zusätzliche externe Sichtbereiche, verborgene Pflichtfelder und organisationsübergreifende Kontakte. Der [Rechteentwurf](docs/PERMISSIONS.md) enthält eine Beispielmatrix und positive sowie negative Prüffälle. Er ist noch nicht fachlich abgenommen.

### OPEN-006 Konten und LDAP Lebenszyklus

**Status:** offen · **Fällig:** vor Anmeldung und Benutzerverwaltung · **Bezug:** REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003

Zu klären sind lokale Einladungen, Kontowiederherstellung, Kontoanlage aus LDAP, stabile Identitätszuordnung, gegebenenfalls LDAP-Gruppenzuordnung, Deaktivierung und Ausfallverhalten. Ein geschützter lokaler Wiederherstellungsweg für Administratoren ist zu bewerten. Eine konkrete LDAP-Servervariante ist noch nicht benannt.

### OPEN-007 Projektkonfiguration und Automationen

**Status:** offen · **Fällig:** vor Workflow- und Feldmodell · **Bezug:** REQ-ORG-003, REQ-ORG-004, REQ-TKT-001

Zu definieren sind Feldtypen, Validierungen, Formularregeln, Übergänge, Projektvorlagen, erlaubte Automationen und der Umgang mit vorhandenen Daten nach Regeländerungen. Vorschlag: fachlich begrenzte Regeln mit dokumentierten Aktionen; beliebiger Kundencode ist kein bestätigter Bedarf.

Der [Fachmodellentwurf](docs/DOMAIN_MODEL.md) beschreibt zwei unterschiedliche Projektabläufe, Feld- und Übergangsregeln, versionierte Konfigurationsänderungen, Archivierung und begrenzte Automationen. Diese Detailregeln sind zur Prüfung gestellt und noch nicht als Gesamtpaket bestätigt.

### OPEN-008 SLA Berechnungsregeln

**Status:** teilweise entschieden · **Fällig:** vor Helpdesk-Datenmodell · **Bezug:** REQ-HD-003, REQ-HD-004, REQ-HD-005

DEC-032 legt die Kundenpause fest: Lösungsfrist pausiert, offene Erstreaktionsfrist läuft im Geschäftszeitkalender weiter. Der [SLA-Entwurf](docs/HELPDESK_SLA.md) enthält 14 berechenbare Fälle einschließlich Wochenenden, Kalendertagen ohne Betrieb, Zeitumstellung, Pausen, Wiedereröffnung und Prioritätswechsel. Zu entscheiden bleiben Budgets, verlässlicher Startmesspunkt, qualifizierte Antwort, Regelzuordnung und Eskalationsstufen. Die übrigen Fristregeln sind Vorschläge, keine bereits abgenommenen Vertragsbedingungen.

### OPEN-009 Postfächer und Portalzugang

**Status:** teilweise entschieden · **Fällig:** vor Helpdesk- und Portalimplementierung · **Bezug:** REQ-HD-001, REQ-HD-002, REQ-PORTAL-001, REQ-PORTAL-003

DEC-031 legt die Prüfwarteschlange mit Kundenzuordnung durch Support für unbekannte Absender fest. Der [Helpdesk-Entwurf](docs/HELPDESK_SLA.md) beschreibt Mail- und Portalabläufe, Wiederholungen und unklare SMTP-Annahme. Die vorgeschlagene Abnahme von REQ-TKT-005 wird entsprechend auf interne Versandaufträge und kontrollierte Behandlung unklarer Zustellung präzisiert; eine uneingeschränkte einmalige externe Zustellung wird nicht zugesagt. Offen bleiben Anzahl und Zuordnung der Postfächer, Mailanbieter, Authentifizierungs- und Vertrauensprüfung, Einladungen, Selbstregistrierung, anonyme Webanfragen, CC-Empfänger, Weiterleitungen und Anhänge. IMAP/SMTP bleiben bestätigt; die erlaubten Anmeldeverfahren des vorgesehenen Anbieters müssen praktisch geprüft werden.

### OPEN-010 Varianten der Git Dienste

**Status:** offen · **Fällig:** vor Git-Integration · **Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003

GitHub und GitLab sind bestätigt. Offen sind SaaS beziehungsweise selbst betriebene Varianten, Authentifizierungsweg, unterstützte Objektarten, automatische Zuordnung, Aktualisierung und Sichtbarkeit importierter Metadaten. Es werden keine Git-Schreibfunktionen für V1 vorausgesetzt.

### OPEN-011 Wiki Dateien und Suche

**Status:** teilweise entschieden · **Fällig:** vor Speicher- und Editorwahl · **Bezug:** REQ-DOC-001 bis REQ-DOC-004, REQ-DATA-003, REQ-TKT-004

DEC-033 legt Entwurf und ausdrückliche Veröffentlichung fest. Der [Wiki-Entwurf](docs/WIKI_I18N_BRANDING.md) liefert konkrete Abläufe und Prüffälle für Sprachfassungen und Konflikte. Weiter zu entscheiden sind Editorumfang, gespeichertes Dokumentformat, Import/Export, Konfliktverfahren, Dateispeicher, Versionsumfang und Suchstrategie. Anhänge benötigen ein reales Größenprofil, damit die 1-TB-Planung bewertet werden kann. Eine zusätzliche Suchdatenbank wird nicht automatisch vorausgesetzt.

### OPEN-012 Betriebsmodell und Verfügbarkeit

**Status:** offen · **Fällig:** vor Installationsarchitektur · **Bezug:** REQ-OPS-001 bis REQ-OPS-006

Benötigt werden Zielplattformen und CPU-Architekturen, Container-Betriebsumgebung, TLS-Zuständigkeit, Proxy und Internetziele, Updateverfahren, Wartungsfenster, Fernzugriffsweg, Sicherungsziel, tolerierter Datenverlust und Wiederanlaufzeit. Ein Internetbedarf begründet keine unbestimmte Pflicht zur Datenübermittlung oder Telemetrie.

### OPEN-013 API Vertrag

**Status:** offen · **Fällig:** vor erster Fachimplementierung · **Bezug:** REQ-API-001, REQ-API-002

Zu bestimmen sind API-Stil, Dokumentationsformat, Versionierung, Authentifizierung, Fehlerformat, Seitennavigation, Wiederholbarkeit von Schreibaufrufen und spätere Ereignisschnittstellen. API- und UI-Rechte müssen auf denselben fachlichen Regeln beruhen.

Der [API-Grundvertrag im Architekturentwurf](docs/ARCHITECTURE.md) schlägt HTTP/JSON, OpenAPI, versionierte Ressourcen, Konflikterkennung, eingeschränkte API-Zugänge und wiederholbare Schreiboperationen vor. Tokenform, Laufzeiten, Wiederholungsfenster und Übergangsfristen sind noch festzulegen.

### OPEN-014 Messbare Leistungsziele

**Status:** offen · **Fällig:** vor Architekturfreigabe und Lasttestplanung · **Bezug:** REQ-PROD-004, REQ-PERF-001 bis REQ-PERF-004

Zu entscheiden sind Zielzeiten und Referenzhardware, akzeptable Verzögerungen für Mail- und SLA-Verarbeitung sowie Daten- und Anhangsprofil. Für einen Vergleich mit Redmine oder Easy8 sind konkrete Version, Konfiguration und Testdaten nötig. Keine RAM-Obergrenze bedeutet nicht, dass Ressourcenverbrauch unbewertet bleibt.

### OPEN-015 Sprachen Vorlagen und Bedienung

**Status:** offen · **Fällig:** vor UI- und Vorlagensystem · **Bezug:** REQ-I18N-001 bis REQ-I18N-003, REQ-BRAND-001, REQ-BRAND-002, REQ-UX-001

Zu bestimmen sind Standardsprache, Rückfallregeln, Übersetzungsprozess für Kundenkonfiguration, Zeitzonenverhalten, Vorlagenbearbeitung, erlaubte Designanpassungen, Browser und mobile Mindestansichten. Ein verbindliches Barrierefreiheitsziel bleibt zu entscheiden. Der [Sprach- und Brandingentwurf](docs/WIKI_I18N_BRANDING.md) enthält Rückfallregeln, getrennte Formateinstellungen, Vorlagen und synthetische Beispiele zur Prüfung.

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

Zu entscheiden sind erlaubte Datenklassen pro Anbieter, Herkunft der API-Zugangsdaten, Kostenträger, Nutzungsgrenzen, Zeitlimits, Inhaltsprotokollierung, Löschung und zulässige Ersatzmodelle. Eine gemeinsame Provider-Schnittstelle macht Modelle nicht automatisch austauschbar. Der [KI-Entwurf](docs/AI_USE_CASES.md) liefert Vorschläge für Kontextbegrenzung, versionierte Skills, Bestätigung, Budgetreservierung und Ausfallverhalten. Zahlenwerte, Datenklassen und Aufbewahrung sind noch nicht fachlich freigegeben.

### OPEN-020 Produkt und kommerzieller Rahmen

**Status:** offen · **Fällig:** vor verbindlichem Produktplan · **Bezug:** REQ-PROD-001, REQ-PROD-002, REQ-OPS-003

Produktname, Lizenzmodell, Preislogik, Pilotkunde, Budget, Zeitrahmen und Supportumfang sind offen. Diese Angaben verhindern den ersten Anforderungskatalog nicht. Sie sind für eine belastbare Release- und Wirtschaftlichkeitsplanung erforderlich. Eine günstigere Alternative ist ein Ziel, kein bereits kalkulierter Preisvorteil.

### OPEN-021 Detailumfang der Fachmodule und späteren Funktionen

**Status:** teilweise entschieden · **Fällig:** V1-Details vor deren Umsetzung; vollständiges Funktionsinventar vor Implementierungsstart · **Bezug:** REQ-PROD-003, REQ-TIME-001 bis REQ-TIME-003, REQ-REQ-001 bis REQ-REQ-004, REQ-EXT-001

DEC-030 bestätigt neue Revision und erneute Freigabe nach inhaltlicher Änderung einer freigegebenen Anforderung. Offen bleiben die genaue Abgrenzung inhaltlicher Änderungen, Standardfelder, Ticketbeziehungen, Zeitkorrekturen und Exportumfang; der [Fachmodellentwurf](docs/DOMAIN_MODEL.md) liefert prüfbare Vorschläge. Alle als Vorschlag bezeichneten V1-Ergänzungen und Kandidaten späterer Module müssen priorisiert werden. Die Freigabe des einfachen Anforderungsmoduls bedeutet keine Freigabe eines beliebig komplexen Genehmigungsworkflows.

## Entscheidungsverfahren

Neue Entscheidungen bekommen eine neue DEC-ID mit Datum, Verantwortlichem, Begründung und betroffenen REQ-IDs. Bereits bestätigte Entscheidungen werden nicht still geändert. Eine Ablösung nennt Vorgänger und Nachfolger sowie ihre Folgen für Anforderungen, Roadmap und offene Aufgaben. Technische Recherche wird bei der jeweiligen Entscheidung mit Quelle und Abrufdatum dokumentiert; diese erste Fassung enthält Gesprächsentscheidungen und Entwurfsvorschläge, keine neuen Produkt- oder Preisrecherchen.
