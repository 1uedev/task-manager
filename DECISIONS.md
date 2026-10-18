# Entscheidungen und offene Fragen

Stand 17. Oktober 2026 · Bestätigte Produktentscheidungen und offene Detailplanung

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
| DEC-010 | React vorgesehen; Backend ursprünglich Go oder Java offen. Am 16. Oktober 2026 durch DEC-039 auf Java mit Spring Boot konkretisiert. | REQ-ARCH-001 |
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
| DEC-036 | Holger bestätigt am 15. Oktober 2026 für V1 GitHub.com, GitLab.com und selbst betriebenes GitLab. GitHub Enterprise Server wird später ergänzt. Objektumfang, Authentifizierung und unterstützte Quellversionen bleiben gesondert zu bestimmen. | REQ-GIT-001, REQ-GIT-002, REQ-GIT-003 |
| DEC-037 | Holger wählt am 15. Oktober 2026 Redmine als erste Datenimportquelle zeitnah nach V1. Easy Redmine/Easy8 und Jira werden anschließend geprüft. Diese Priorisierung ist keine Zusage vollständiger Übernahme beliebiger Versionen, Plugins oder Historien. | REQ-MIG-001, REQ-MIG-002 |
| DEC-038 | Holger wählt am 15. Oktober 2026 für die Planung bei vollständigem Serverausfall höchstens eine Stunde Datenverlust (RPO) und vier Stunden bis zum Wiederbetrieb (RTO). Die Ziele sind vor Kundenzusage mit vollständigem Daten-/Dateibestand nachzuweisen; sie sind keine bereits erreichten Messwerte oder zugesagte Bereitschaftsleistung. | REQ-OPS-004, REQ-PERF-002, REQ-PERF-003 |

DEC-033 trennt redaktionelle Arbeit von der sichtbaren Leserfassung. Der [Wiki-, Sprach- und Brandingentwurf](docs/WIKI_I18N_BRANDING.md) beschreibt Revisionen, optionale Übersetzungen, Konflikte und Vorlagen. Die weiteren Detailvorschläge sind dadurch nicht pauschal freigegeben.

DEC-034 und DEC-035 klären den ersten fachlichen KI-Umfang und dessen Nutzerkreis. Der [KI-Entwurf](docs/AI_USE_CASES.md) beschreibt die vier Abläufe sowie weiterhin vorgeschlagene Datenwege, Bestätigung, Skills und Betriebsgrenzen. Anforderungen und Roadmap sind nachgeführt.

DEC-036 und DEC-037 konkretisieren Betriebsvarianten der Git-Anbindung und die erste spätere Importquelle. Der [Git- und Migrationsentwurf](docs/GIT_MIGRATION.md) beschreibt weiterhin vorgeschlagene Berechtigungen, Datenumfang, Probelauf und Umschaltung.

DEC-038 konkretisiert das Sicherungsziel. Der [Betriebsentwurf](docs/OPERATIONS.md) liefert Verfahren, Voraussetzungen, Ressourcenrechnung und Messfälle als Vorschläge. Servicezeit, Betreiberpflichten und technische Umsetzung bleiben zu entscheiden.

## Ergänzung vom 16. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-039 | Holger bestätigt am 16. Oktober 2026 Java mit Spring Boot als Backend auf Basis der vorgelegten Auswahl. Dies konkretisiert DEC-010 und den Backend-/Frameworkteil von OPEN-001. React bleibt gesetzt; TypeScript, Sprach-/Framework-Versionen, Build-Konventionen und Modulstruktur sind damit nicht bestätigt. | REQ-ARCH-001 |
| DEC-040 | Holger legt am 16. Oktober 2026 zunächst gegenseitige KI-Reviews für technische Änderungen fest, auch für kritische Bereiche wie Anmeldung, Berechtigungen, Datenmigration und Wiederherstellung. Eine zusätzliche menschliche technische Prüfung bleibt ausdrücklich offen. Holger übernimmt weiterhin die fachliche Abnahme. Die Auswahl bestätigt den anfänglichen Review-Weg, nicht sämtliche vorgeschlagenen Freigabeschwellen und keine bereits erfolgte Prüfung. | REQ-DEV-004, REQ-DEV-005 |
| DEC-041 | Holger priorisiert am 16. Oktober 2026 Kanban, Gantt und einfache Dashboards als erste weitere Fachfunktionen nach V1. Die bereits vorgesehenen frühen Ergänzungen 2FA und Redmine-Import bleiben eingeplant. Diese Auswahl klärt die Richtung unter OPEN-021, bestätigt aber keine Termine, zusätzlichen Sprintfunktionen, umfassenden Berichtswerkzeuge oder alle vorgeschlagenen Mindestumfänge. | REQ-PROD-003, REQ-EXT-001 |

Die drei ausdrücklichen Antworten konkretisieren die Planung, ohne eine Gesamtfreigabe der Architektur oder einen Implementierungsauftrag zu erteilen. Die bisher offene Technologieauswahl wird nachvollziehbar abgeschlossen; der anfängliche Review-Weg und die nächste fachliche Priorität sind festgelegt. Weitere Details bleiben als Vorschläge gekennzeichnet.

## Weitere Architekturauswahl vom 16. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-042 | Holger bestätigt TypeScript für das React-Frontend. Damit ist die Sprachauswahl unter OPEN-001 konkretisiert. Strenge Compileroptionen, Generator, Komponentenbibliotheken, Versionen sowie Build-/Testkonventionen bleiben gesonderte Detailvorschläge. | REQ-ARCH-001 |
| DEC-043 | Holger bestätigt einen modularen Monolithen: eine gemeinsame Anwendung mit klar getrennten Fachmodulen. Webserver und Hintergrundverarbeitung können aus demselben Docker-Image laufen. Konkrete Modulverträge, Transaktions-/Auftragstechnik, zusätzliche Dienste und spätere Mandantenisolation bleiben unter OPEN-002 zu prüfen. Die Auswahl verpflichtet nicht auf eine bestimmte Prozessaufteilung. | REQ-ARCH-002, REQ-ORG-002 |
| DEC-044 | Holger bestätigt eine HTTP/JSON-Schnittstelle im REST-Stil mit OpenAPI-Beschreibung für Oberfläche und Integrationen. Damit sind API-Stil und Beschreibungsformat unter OPEN-013 entschieden. OpenAPI-Version, Generator, Ressourcenpfade, Authentifizierung, Fehler-/Versionsregeln, Seitengrößen und Wiederholungsfristen sind dadurch nicht pauschal angenommen. | REQ-API-001 |

Diese drei Antworten bestätigen die in TASK-016 vorgelegten Grundentscheidungen. Die vorherigen Entscheidungseinträge bleiben als historische Belege erhalten; die aktuelle Einordnung richtet sich zusätzlich nach DEC-042 bis DEC-044. Die Detailentwürfe und deren Abnahmeszenarien bleiben prüfpflichtig. Eine Gesamtfreigabe der Spezifikation oder ein Auftrag zur Produktimplementierung ist damit nicht verbunden.

## Ergänzungen vom 17. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-045 | Holger fordert eine durch den Kunden einstellbare automatische Benutzerabmeldung nach einer konfigurierten Stundenzahl. Diese Anforderung wird in V1 aufgenommen. Ob seit Anmeldung, seit letzter Aktivität oder nach beiden Grenzen gemessen wird sowie Fristwerte und Regeländerungen bleiben zur Klärung offen. | REQ-AUTH-005 |
| DEC-046 | Holger bestätigt ein umfangreiches Tag-System für Tickets/Tasks und Projekte bereits ab V1, mit Jira als funktionaler Orientierung. Konkrete Verwaltungs-/Rechteregeln und Einzelmerkmale werden als prüfbarer Entwurf spezifiziert; daraus folgt keine vollständige Jira-Funktionsgleichheit. | REQ-TAG-001 |
| DEC-047 | Holger verlangt eine sehr performante Suche als V1-Kern, im Zusammenhang mit Tickets/Tasks, Projekten und Tags. Zusätzliche durchsuchbare Bereiche und verbindliche Antwortzeiten unter definierter Last sind noch festzulegen. Ein Suchdienst, ein Protokollwechsel oder bereits erreichte Messwerte sind damit nicht beschlossen. | REQ-SRCH-001, REQ-PERF-003 |

Die Frage nach REST oder gRPC wird im [Architekturentwurf](docs/ARCHITECTURE.md) beantwortet: direkte Java-Modulschnittstellen innerhalb eines Prozesses sind die Empfehlung, REST bleibt gemäß DEC-044 für Frontend und Integrationen gesetzt. gRPC wird für einen späteren konkreten Dienstbedarf bewertet. Diese Empfehlung ist keine zusätzliche bestätigte Technologieentscheidung und löst keine Implementierung aus.

## Bestätigte Auswahl zu Sitzungen, Tags und Suche vom 17. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-048 | Holger wählt für die konfigurierbare automatische Abmeldung ausschließlich eine feste Zeit seit Anmeldung. Damit ist die Fristart aus DEC-045 entschieden; der vorherige Vorschlag mit zusätzlicher Inaktivitätsfrist entfällt für V1. Aktivität verlängert die feste Frist nicht. Konkrete Dauer, Grenzen und Umgang mit Regeländerungen bleiben offen; Kontosperre, Widerruf und manuelle Abmeldung sind unabhängige Vorgänge. | REQ-AUTH-005 |
| DEC-049 | Auch berechtigte externe Portalbenutzer dürfen in V1 neue Tags anlegen. Die Auswahl erweitert den Nutzerkreis aus DEC-046; sie erteilt weder allen Portalbenutzern automatisch das Recht noch Katalogverwaltung oder Zugriff auf fremde Projekte/Kunden. Konkrete Katalog-, Sicht- und Vergaberegeln bleiben prüfpflichtig. | REQ-TAG-001, REQ-ACL-001, REQ-ACL-002 |
| DEC-050 | Die gemeinsame V1-Suche umfasst Tickets/Tasks, Projekte, Wiki und Anforderungen. Suche in Dateiinhalten folgt später. Dies konkretisiert den Bereich aus DEC-047; Dateinamen-/Metadatensuche, konkrete Felder, Filter, Suchtechnik und Antwortzeitwerte sind nicht pauschal bestätigt. | REQ-SRCH-001, REQ-DOC-004, REQ-PERF-003 |

Die drei Antworten konkretisieren den Entwurf aus TASK-018. Frühere Entscheidungseinträge bleiben historisch erhalten; die aktuelle Einordnung berücksichtigt DEC-048 bis DEC-050. Weitere Details, die Gesamtarchitektur und eine Produktimplementierung werden dadurch nicht freigegeben.

## Bestätigter Einstieg und lokale Kontoanlage vom 17. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-051 | Holger wählt als ersten vorführbaren Entwicklungsabschnitt den internen Ablauf mit Anmeldung, Projekten, Tickets, Tags und Suche. LDAP und Kundenportal folgen anschließend innerhalb V1. Alle übrigen bestätigten V1-Funktionen bleiben erhalten. Die Auswahl bestätigt den Abschnittsumfang, nicht sämtliche technischen Vorschläge, Detailabnahmen oder einen Implementierungsauftrag. | REQ-PROD-001, REQ-PROD-002 |
| DEC-052 | Bei einer neuen Installation beträgt die feste Sitzungsdauer standardmäßig 24 Stunden seit erfolgreicher Anmeldung. Administratoren können den Stundenwert ändern. Die ausschließlich feste Frist ohne Inaktivitätsabmeldung aus DEC-048 bleibt bestehen. Grenzen, Deaktivierbarkeit und Wirkung von Regeländerungen auf laufende Sitzungen bleiben offen; bestehende Kundenkonfigurationen werden durch diese Standardwahl nicht pauschal überschrieben. | REQ-AUTH-005 |
| DEC-053 | Lokale Benutzerkonten und lokale Portalzugänge werden in V1 durch Administratoren mit Einmalpasswort angelegt. Bei erster Anmeldung muss der Benutzer sein Passwort ändern. Dies ersetzt den vorgeschlagenen Einladungslink als gewählten Kontoanlageweg; es ist keine zusätzliche 2FA-Anforderung und legt keine LDAP-Passwortverwaltung fest. Gültigkeit, Übergabe, Wiederherstellung und technischer Zustandsübergang bleiben auszugestalten; Kontoanlage ersetzt keine Gruppen-/Projektfreigabe. | REQ-AUTH-001, REQ-PORTAL-003 |

Die Antworten konkretisieren TASK-020. Werkzeugversionen, weitere Detailverträge, fachliche Gesamtabnahme und gesonderter Entwicklungsauftrag bleiben offen. TASK-021 bis TASK-031 bleiben blockiert und unbesetzt.

## Begrenzter technischer Folgeauftrag vom 17. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-054 | Holger beauftragt mit der Fortsetzung nach TASK-032 den angekündigten nächsten Schritt TASK-021: Werkzeugversionen und gemeinsame Schnittstellen in einem isolierten technischen Versuch qualifizieren. Synthetische Build-/Testquellen, öffentliche Abhängigkeiten und flüchtige Testdatenbanken gehören zu diesem Nachweis. Die bisherigen P1–P4-Empfehlungen sind Kandidaten; Abweichungen werden begründet und geprüft. Daraus folgen weder eine pauschale fachliche Abnahme aller Details noch Produktimplementierung aus TASK-022 bis TASK-031, produktive Konten oder Auslieferung. | REQ-ARCH-001, REQ-ARCH-002, REQ-API-001, REQ-DATA-001, REQ-DEV-005 |

Der [Qualifikationsbericht](docs/TECHNICAL_BASELINE.md) trennt technische Versuchsauswahl und tatsächliche Nachweise von der ausstehenden Produktfreigabe. Frühere Entscheidungseinträge bleiben als damaliger Stand erhalten.

## Implementierungsauftrag vom 18. Oktober 2026

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-055 | Holger wählt am 18. Oktober 2026 im Projektgespräch auf einer Entscheidungskarte TASK-022 als nächsten Schritt, mit der Folge „TASK-021 gilt als angenommen, das Backend mit PostgreSQL startet, zuerst per Claim PR“. Damit sind die Ergebnisse von TASK-021 für diesen Folgeschritt angenommen und die Backend- und PostgreSQL-Grundlage unter `backend/` beauftragt: Build, leere Modulgrenzen, Konfiguration, gemeinsame Fehlerbehandlung, Datenbankanbindung, Migrationsweg und Backend-CI. Fachfunktionen, Frontend, Docker-Image, Auslieferung, produktive Konten sowie TASK-023 bis TASK-031 sind damit nicht beauftragt. Die förmliche Abnahme von TASK-021 und dessen Übernahme nach DONE.md bleiben gesondert. | REQ-ARCH-001, REQ-ARCH-002, REQ-DATA-001, REQ-OPS-005, REQ-DEV-005 |

Modulordnung, Migrationskonvention und erste Fehlercodes in [backend/README.md](backend/README.md) sind technische Umsetzungsvorschläge dieses Auftrags und werden mit dessen Ergebnis geprüft.

| ID | Entscheidung | Bezug |
|---|---|---|
| DEC-056 | Holger wählt am 18. Oktober 2026 im Projektgespräch auf einer Entscheidungskarte TASK-023 als nächste Aufgabe. Die Karte nannte als Grundlage Deutsch und Englisch sowie aktuelle Chromium-basierte Browser. Damit ist die React-Grundfläche unter `frontend/` beauftragt: Oberflächenrahmen mit Navigation, Sprachumschaltung Deutsch und Englisch, Unternehmensauftritt aus einer Einstellung, aus dem Vertrag erzeugter API Client, Zustände für Laden, leere Daten, Fehler, fehlende Rechte und Konflikte sowie Frontend-CI. Fachfunktionen, Anmeldung, Docker-Image, Auslieferung sowie TASK-024 bis TASK-031 sind damit nicht beauftragt. Der verbindliche Barrierefreiheitsstandard und die mobilen Mindestansichten (REQ-UX-001) bleiben offen. Die förmliche Abnahme von TASK-021 und dessen Übernahme nach DONE.md bleiben gesondert. | REQ-ARCH-001, REQ-API-001, REQ-I18N-001, REQ-I18N-002, REQ-BRAND-001, REQ-UX-001 |

Sprachpaketformat, Rückfallregel, Fehlerabbildung und Brandingprüfung in [frontend/README.md](frontend/README.md) sind technische Umsetzungsvorschläge dieses Auftrags und werden mit dessen Ergebnis geprüft.

## Offene Entscheidungen

Die [Entscheidungsübersicht zur Architekturfreigabe](docs/ARCHITECTURE_READINESS.md) bündelt diese 21 Fragen mit Zuständigkeiten, Fälligkeiten und vorgeschlagenen Prüfungspaketen. Sie ersetzt keine Entscheidung und gibt die Architektur noch nicht frei.

Die [Startvorlage aus TASK-020](docs/DEVELOPMENT_START.md) bündelt konkrete Empfehlungen für einen begrenzten ersten Auftrag. Ihre drei Auswahlfragen sind durch DEC-051 bis DEC-053 beantwortet. DEC-054 beauftragte TASK-021 zur begrenzten technischen Qualifikation der Werkzeugkandidaten, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023; TASK-024 bis TASK-031 bleiben gesperrt. Die Teilantworten und der technische Nachweis schließen die nachstehenden umfassenderen OPEN-Fragen nicht vollständig.

Die nachstehenden Punkte müssen nicht alle beantwortet sein, um die erste Spezifikation zu prüfen. Jeder Punkt nennt, bis wann er für verlässliche Umsetzung oder Auslieferung geklärt sein muss. Vorschläge sind keine vorweggenommene Entscheidung.

### OPEN-001 Backend und Entwicklungswerkzeuge

**Status:** teilweise entschieden · **Fällig:** Restdetails vor Implementierung · **Bezug:** REQ-ARCH-001, REQ-DEV-005

DEC-039 bestätigt Java mit Spring Boot, DEC-042 TypeScript für React. Offen bleiben Sprach- und Framework-Versionen sowie Build- und Testkonventionen. Die Backendentscheidung beruht auf dem vorgelegten Go/Java-Vergleich; eine vermeintliche KI-Präferenz wird nicht als Begründung verwendet.

Der [Architekturentwurf](docs/ARCHITECTURE.md) erhält den ursprünglichen Go/Java-Vergleich als Entscheidungsbegründung und weist die bestätigte Auswahl separat aus. Es wurden keine Laufzeitmessungen oder Implementierungen vorgenommen.

TASK-016 beschreibt [TypeScript und den gemeinsamen Datenvertrag](docs/TECHNICAL_CONTRACTS.md). Die TypeScript-Auswahl ist bestätigt; strenge Typprüfung und aus OpenAPI abgeleitete Transporttypen bleiben Empfehlungen. Werkzeuge und Versionen sind nicht vorweggenommen.

### OPEN-002 Modulstruktur und spätere Mandanten

**Status:** teilweise entschieden · **Fällig:** Restdetails vor Implementierung · **Bezug:** REQ-ORG-002, REQ-ARCH-002

DEC-043 bestätigt den modularen Monolithen mit klar getrennten Fachmodulen. Offen bleiben konkrete Modulverträge, Hintergrundverarbeitung und Ausgestaltung der Unternehmensgrenzen. Die konkrete spätere Isolation über eigene Datenbanken, Schemas oder gemeinsam genutzte Tabellen bleibt offen. Für V1 soll daraus kein unnötiger Mehrmandantenbetrieb entstehen.

Der [Architekturentwurf](docs/ARCHITECTURE.md) beschreibt Datenhoheit je Modul, gerichtete Abhängigkeiten, einen serverseitigen Unternehmenskontext sowie Datenbankadapter. PostgreSQL bleibt allein unterstützt; weitere Datenbanken und mehrere Mandanten werden nicht implementiert.

Die [Modulverträge aus TASK-016](docs/TECHNICAL_CONTRACTS.md) konkretisieren Abhängigkeitsordnung, zusammengesetzte Ansichten, Dateizugriff und gemeinsame Transaktionsgrenzen einschließlich konkurrierendem Rechteentzug. Diese Detailregeln bleiben Vorschläge innerhalb der bestätigten Architekturform.

### OPEN-003 Erster KI Lieferumfang

**Status:** teilweise entschieden · **Fällig:** Anbieterreihenfolge vor verbindlicher V1-Planung · **Bezug:** REQ-AI-001 bis REQ-AI-009

DEC-034 bestätigt Ticketzusammenfassung, Antwortentwurf, Anforderungsprüfung und Wiki-Überarbeitung für V1. DEC-035 begrenzt V1 auf interne berechtigte Benutzer; direkte Portalnutzung folgt später. Offen bleiben die Lieferreihenfolge von OpenAI, Anthropic/Claude und Ollama sowie mögliche spätere automatische Vorschlagsgenerierung. Der [KI-Entwurf](docs/AI_USE_CASES.md) empfiehlt zunächst ausdrückliche Benutzerstarts und beschreibt die vier Abläufe. Mindestens eine geeignete Anbindung muss die V1-Funktionen tragen; eine gleichzeitige Auslieferung aller drei Anbieter ist noch nicht beschlossen. Automatische Änderungen bleiben ausgeschlossen.

### OPEN-004 Zwei Faktor Authentifizierung

**Status:** teilweise entschieden · **Fällig:** Architektur vor Implementierung, Detailverfahren vor 2FA-Release · **Bezug:** REQ-AUTH-004

DEC-029 legt die Auslieferung zeitnah nach V1 fest. Offen bleiben konkreter Termin, Verfahren, verpflichtende Nutzergruppen, Wiederherstellung und LDAP-Zusammenspiel. Die V1-Architektur bereitet die Ergänzung vor; die frühere Empfehlung eines Termins vor regulärem Kundeneinsatz wird nicht als Anforderung weitergeführt.

### OPEN-005 Zusammenspiel der Berechtigungen

**Status:** teilweise entschieden · **Fällig:** vor Datenmodell und Rechteimplementierung · **Bezug:** REQ-ACL-001 bis REQ-ACL-005

DEC-027 bestätigt die Organisationssicht innerhalb freigegebener Projekte; DEC-028 bestätigt den Vorrang ausdrücklicher Feldverbote. Offen bleiben das Zusammenspiel allgemeiner Aktionsrechte, globale und delegierte Administrationsrechte, zusätzliche externe Sichtbereiche, verborgene Pflichtfelder und organisationsübergreifende Kontakte. Der [Rechteentwurf](docs/PERMISSIONS.md) enthält eine Beispielmatrix und positive sowie negative Prüffälle. Er ist noch nicht fachlich abgenommen.

DEC-049 bestätigt Tag-Erstellung auch durch berechtigte externe Portalbenutzer. Gesonderte Erstell-, Zuordnungs- und Verwaltungsrechte sowie Sichtbarkeitsgrenzen werden im [Tag-Entwurf](docs/TAGS_SEARCH.md) vorgeschlagen; die konkrete Rechtevergabe ist damit nicht pauschal beschlossen.

### OPEN-006 Konten und LDAP Lebenszyklus

**Status:** teilweise entschieden · **Fällig:** Restdetails vor Anmeldung und Benutzerverwaltung · **Bezug:** REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003, REQ-AUTH-005

DEC-053 bestätigt administrative Anlage lokaler Konten und Portalzugänge mit Einmalpasswort und erzwungenem Passwortwechsel bei erster Anmeldung. Zu klären sind dessen Gültigkeit, sichere Übergabe und Zustandswechsel, Kontowiederherstellung, Kontoanlage aus LDAP, stabile Identitätszuordnung, gegebenenfalls LDAP-Gruppenzuordnung, Deaktivierung und Ausfallverhalten. Ein geschützter lokaler Wiederherstellungsweg für Administratoren ist zu bewerten. Eine konkrete LDAP-Servervariante ist noch nicht benannt.

DEC-045 bestätigt die Einstellbarkeit; DEC-048 legt ausschließlich feste Laufzeit seit Anmeldung fest. Die [Sitzungsregeln](docs/PERMISSIONS.md) sind darauf angepasst und enthalten keine zusätzliche Inaktivitätsfrist für V1. DEC-052 bestätigt 24 Stunden als Voreinstellung neuer Installationen. Grenzwerte, Deaktivierbarkeit, Wirkung von Regeländerungen und UX-Details bleiben Vorschläge. Fristart und Standardwert sind nicht erneut offen.

### OPEN-007 Projektkonfiguration und Automationen

**Status:** offen · **Fällig:** vor Workflow- und Feldmodell · **Bezug:** REQ-ORG-003, REQ-ORG-004, REQ-TKT-001

Zu definieren sind Feldtypen, Validierungen, Formularregeln, Übergänge, Projektvorlagen, erlaubte Automationen und der Umgang mit vorhandenen Daten nach Regeländerungen. Vorschlag: fachlich begrenzte Regeln mit dokumentierten Aktionen; beliebiger Kundencode ist kein bestätigter Bedarf.

Der [Fachmodellentwurf](docs/DOMAIN_MODEL.md) beschreibt zwei unterschiedliche Projektabläufe, Feld- und Übergangsregeln, versionierte Konfigurationsänderungen, Archivierung und begrenzte Automationen. Diese Detailregeln sind zur Prüfung gestellt und noch nicht als Gesamtpaket bestätigt.

### OPEN-008 SLA Berechnungsregeln

**Status:** teilweise entschieden · **Fällig:** vor Helpdesk-Datenmodell · **Bezug:** REQ-HD-003, REQ-HD-004, REQ-HD-005

DEC-032 legt die Kundenpause fest: Lösungsfrist pausiert, offene Erstreaktionsfrist läuft im Geschäftszeitkalender weiter. Der [SLA-Entwurf](docs/HELPDESK_SLA.md) enthält 14 berechenbare Fälle einschließlich Wochenenden, Kalendertagen ohne Betrieb, Zeitumstellung, Pausen, Wiedereröffnung und Prioritätswechsel. Zu entscheiden bleiben Budgets, verlässlicher Startmesspunkt, qualifizierte Antwort, Regelzuordnung und Eskalationsstufen. Die übrigen Fristregeln sind Vorschläge, keine bereits abgenommenen Vertragsbedingungen.

### OPEN-009 Postfächer und Portalzugang

**Status:** teilweise entschieden · **Fällig:** vor Helpdesk- und Portalimplementierung · **Bezug:** REQ-HD-001, REQ-HD-002, REQ-PORTAL-001, REQ-PORTAL-003

DEC-031 legt die Prüfwarteschlange mit Kundenzuordnung durch Support für unbekannte Absender fest. Der [Helpdesk-Entwurf](docs/HELPDESK_SLA.md) beschreibt Mail- und Portalabläufe, Wiederholungen und unklare SMTP-Annahme. Die vorgeschlagene Abnahme von REQ-TKT-005 wird entsprechend auf interne Versandaufträge und kontrollierte Behandlung unklarer Zustellung präzisiert; eine uneingeschränkte einmalige externe Zustellung wird nicht zugesagt. Offen bleiben Anzahl und Zuordnung der Postfächer, Mailanbieter, Authentifizierungs- und Vertrauensprüfung, sichere Übergabe/Verfall von Einmalpasswörtern und Wiederherstellung, anonyme Webanfragen, CC-Empfänger, Weiterleitungen und Anhänge. Der lokale Portalzugang wird nach DEC-053 administrativ mit Einmalpasswort angelegt; offene Selbstregistrierung gehört nicht zum ausgewählten Kontoanlageweg. IMAP/SMTP bleiben bestätigt; die erlaubten Anmeldeverfahren des vorgesehenen Anbieters müssen praktisch geprüft werden.

### OPEN-010 Varianten der Git Dienste

**Status:** teilweise entschieden · **Fällig:** vor Git-Integration · **Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003

DEC-036 bestätigt GitHub.com, GitLab.com und selbst betriebenes GitLab für V1 sowie GitHub Enterprise Server für später. Offen sind Authentifizierungsweg, unterstützte Versionen und Objektarten, automatische Zuordnung, Aktualisierung und Sichtbarkeit importierter Metadaten. Der [Git-Entwurf](docs/GIT_MIGRATION.md) enthält lesende Benutzeranbindung, drei Objektarten und aktuelle Quellprüfung als Vorschläge. Es werden keine Git-Schreibfunktionen für V1 vorausgesetzt.

### OPEN-011 Wiki Dateien und Suche

**Status:** teilweise entschieden · **Fällig:** vor Speicher- und Editorwahl · **Bezug:** REQ-DOC-001 bis REQ-DOC-004, REQ-DATA-003, REQ-TKT-004

DEC-033 legt Entwurf und ausdrückliche Veröffentlichung fest. Der [Wiki-Entwurf](docs/WIKI_I18N_BRANDING.md) liefert konkrete Abläufe und Prüffälle für Sprachfassungen und Konflikte. Weiter zu entscheiden sind Editorumfang, gespeichertes Dokumentformat, Import/Export, Konfliktverfahren, Dateispeicher, Versionsumfang und Suchstrategie. Anhänge benötigen ein reales Größenprofil, damit die 1-TB-Planung bewertet werden kann. Eine zusätzliche Suchdatenbank wird nicht automatisch vorausgesetzt.

Der [Tag- und Suchentwurf aus TASK-018, konkretisiert durch TASK-019](docs/TAGS_SEARCH.md) beschreibt die bestätigte gemeinsame Suche über Tickets/Tasks, Projekte, Wiki und Anforderungen (DEC-050). Dateiinhalte sind für später eingeordnet. Dateinamen/Metadaten, genaue Felder, Revisions-/Sprachfilter und Suchstrategie bleiben offen. PostgreSQL-Volltextindizes und getrennte sichtbare Textsegmente sind Vorschläge; ein zusätzlicher Suchdienst wird erst bei nachgewiesenem Bedarf bewertet.

### OPEN-012 Betriebsmodell und Verfügbarkeit

**Status:** teilweise entschieden · **Fällig:** vor Installationsarchitektur · **Bezug:** REQ-OPS-001 bis REQ-OPS-006

DEC-038 legt für die Planung höchstens eine Stunde Datenverlust und vier Stunden bis zum Wiederbetrieb nach vollständigem Serverausfall fest. Der [Betriebsentwurf](docs/OPERATIONS.md) beschreibt eine Referenzinstallation, gemeinsame Sicherungspunkte und kontrollierte Wiederaufnahme. Offen bleiben Plattform-/Versionsmatrix, TLS, Proxy und Internetziele, Offline-Verhalten, Sicherungswerkzeug/-ziel, Servicezeit, Ersatzkapazität, Zuständigkeiten, Update- und Fernwartungsverfahren. Ein Internetbedarf begründet keine unbestimmte Pflicht zur Datenübermittlung oder Telemetrie. Die Wiederherstellungsziele sind noch nicht praktisch nachgewiesen.

### OPEN-013 API Vertrag

**Status:** teilweise entschieden · **Fällig:** Restdetails vor erster Fachimplementierung · **Bezug:** REQ-API-001, REQ-API-002

DEC-044 bestätigt HTTP/JSON im REST-Stil mit OpenAPI-Beschreibung. Zu bestimmen bleiben die konkrete Vertrags-/Werkzeugversion, Versionierung, Authentifizierung, Fehlerformat, Seitennavigation, Wiederholbarkeit von Schreibaufrufen und spätere Ereignisschnittstellen. API- und UI-Rechte müssen auf denselben fachlichen Regeln beruhen.

Der [API-Grundvertrag im Architekturentwurf](docs/ARCHITECTURE.md) konkretisiert die bestätigte API-Richtung mit vorgeschlagenen versionierten Ressourcen, Konflikterkennung, eingeschränkten API-Zugängen und wiederholbaren Schreiboperationen. Tokenform, Laufzeiten, Wiederholungsfenster und Übergangsfristen sind noch festzulegen.

Der [API-Entwurf aus TASK-016](docs/TECHNICAL_CONTRACTS.md) liefert konkrete Operationen, Fehlerfälle und Wiederholungsregeln. Opake API-Zugänge, 24 Stunden für abgeschlossene Wiederholungsnachweise und Listenseiten mit 50/maximal 100 Einträgen sind prüfpflichtige Vorschläge. Die Auswahl nach DEC-044 bestätigt diese Details nicht. Anmeldung, Aufbewahrung und Transaktionsverfahren sind mit OPEN-006 und OPEN-014 abzustimmen.

### OPEN-014 Messbare Leistungsziele

**Status:** offen · **Fällig:** vor Architekturfreigabe und Lasttestplanung · **Bezug:** REQ-PROD-004, REQ-PERF-001 bis REQ-PERF-004

Zu entscheiden sind Zielzeiten und Referenzhardware, akzeptable Verzögerungen für Mail- und SLA-Verarbeitung sowie Daten- und Anhangsprofil. Für einen Vergleich mit Redmine oder Easy8 sind konkrete Version, Konfiguration und Testdaten nötig. Keine RAM-Obergrenze bedeutet nicht, dass Ressourcenverbrauch unbewertet bleibt. Der [Betriebsentwurf](docs/OPERATIONS.md) schlägt 4 vCPU/16 GB als Messreferenz, ein Fünfjahresprofil, p95-Ziele und Störungstests vor. Nur die RPO-/RTO-Ziele aus DEC-038 sind bestätigt; übrige Werte bleiben Vorschläge.

DEC-047 bestätigt die sehr performante Suche als Produktziel. Der [Tag- und Suchentwurf](docs/TAGS_SEARCH.md) konkretisiert Suchfälle, Tag-Mengen, Berechtigungsfilter und vorgeschlagene p95-Ziele. Diese verschärfen die Ausgangsplanung für einfache Suche, ersetzen aber keine gemessene Qualifikation oder Freigabe der Zahlenwerte.

### OPEN-015 Sprachen Vorlagen und Bedienung

**Status:** offen · **Fällig:** vor UI- und Vorlagensystem · **Bezug:** REQ-I18N-001 bis REQ-I18N-003, REQ-BRAND-001, REQ-BRAND-002, REQ-UX-001

Zu bestimmen sind Standardsprache, Rückfallregeln, Übersetzungsprozess für Kundenkonfiguration, Zeitzonenverhalten, Vorlagenbearbeitung, erlaubte Designanpassungen, Browser und mobile Mindestansichten. Ein verbindliches Barrierefreiheitsziel bleibt zu entscheiden. Der [Sprach- und Brandingentwurf](docs/WIKI_I18N_BRANDING.md) enthält Rückfallregeln, getrennte Formateinstellungen, Vorlagen und synthetische Beispiele zur Prüfung.

### OPEN-016 Datenaufbewahrung und Migration

**Status:** teilweise entschieden · **Fällig:** Aufbewahrung vor Betriebskonzept; Importumfang vor Migrationsrelease · **Bezug:** REQ-DATA-004, REQ-MIG-001, REQ-MIG-002

DEC-037 priorisiert Redmine als erste Quelle nach V1; Easy Redmine/Easy8 und Jira werden anschließend geprüft. Der [Migrationsentwurf](docs/GIT_MIGRATION.md) liefert Datenarten, Probelauf, Rechteabbildung und Ergebnisabgleich als Vorschlag. Offen bleiben Quellversionen, endgültiger Datenumfang, Lösch- und Aufbewahrungsregeln. Vollständige Historie, Plugin-Inhalte und Rechteabbildung sind keine ungeprüfte Importzusage. Der [Betriebsentwurf](docs/OPERATIONS.md) ergänzt vorgeschlagene Fristen, Löschwege und den Umgang mit historischen Sicherungen; keine dieser Fristen gilt bereits als beschlossen.

### OPEN-017 Technisches Review und Freigaben

**Status:** teilweise entschieden · **Fällig:** Detailverfahren vor Implementierung; menschliche Prüfung ausdrücklich offen · **Bezug:** REQ-SEC-001, REQ-DEV-004, REQ-DEV-005

DEC-040 bestätigt zunächst gegenseitige KI-Reviews, auch für kritische Änderungen. Holger übernimmt weiterhin die fachliche Abnahme. Eine zusätzliche menschliche technische Prüfung bleibt ausdrücklich offen und ist durch diese Auswahl keine vorausgesetzte Freigabestufe. Zu klären bleiben konkrete Zuordnung der prüfenden Sitzungen, Nachweise, Freigabeschwellen und Behandlung offener Befunde. Der [Review-Entwurf](docs/DEVELOPMENT_REVIEW.md) trennt den bestätigten Review-Weg von diesen Detailvorschlägen.

### OPEN-018 Koordination der KI Arbeit

**Status:** offen · **Fällig:** vor paralleler Entwicklung · **Bezug:** REQ-DEV-001, REQ-DEV-002, REQ-DEV-003

Repository: 1uedev/task-manager. Integrationsbranch: main. Der technische Einrichtungsauftrag ist DEC-025. Das in docs/AI_WORKFLOW.md beschriebene Verfahren verwendet reservierende Pull Requests, bevor eine KI eine Aufgabe bearbeitet, sowie getrennte Arbeitsstände. TODO.md auf main bleibt maßgeblich. Das Verfahren ist organisatorisch; eine atomare Task-Sperre und die Identifikation einzelner KI-Sitzungen über GitHub-Benutzerkonten werden nicht behauptet. Koordination: Holger beziehungsweise eine von ihm ausdrücklich benannte Sitzung. Die weitergehenden fachlichen und technischen Freigaberegeln bleiben unter OPEN-017 zu entscheiden. Der [Review-Entwurf](docs/DEVELOPMENT_REVIEW.md) ergänzt Parallel-, Konflikt- und Abbruchszenarien; gegenseitige KI-Reviews sind durch DEC-040 bestätigt, seine übrigen erweiterten Regeln sind noch nicht beschlossen.

### OPEN-019 KI Betrieb und Nutzungsgrenzen

**Status:** offen · **Fällig:** vor erster KI-Auslieferung · **Bezug:** REQ-AI-003, REQ-AI-005, REQ-AI-007, REQ-AI-008

Zu entscheiden sind erlaubte Datenklassen pro Anbieter, Herkunft der API-Zugangsdaten, Kostenträger, Nutzungsgrenzen, Zeitlimits, Inhaltsprotokollierung, Löschung und zulässige Ersatzmodelle. Eine gemeinsame Provider-Schnittstelle macht Modelle nicht automatisch austauschbar. Der [KI-Entwurf](docs/AI_USE_CASES.md) liefert Vorschläge für Kontextbegrenzung, versionierte Skills, Bestätigung, Budgetreservierung und Ausfallverhalten. Zahlenwerte, Datenklassen und Aufbewahrung sind noch nicht fachlich freigegeben.

### OPEN-020 Produkt und kommerzieller Rahmen

**Status:** offen · **Fällig:** vor verbindlichem Produktplan · **Bezug:** REQ-PROD-001, REQ-PROD-002, REQ-OPS-003

Produktname, Lizenzmodell, Preislogik, Pilotkunde, Budget, Zeitrahmen und Supportumfang sind offen. Diese Angaben verhindern den ersten Anforderungskatalog nicht. Sie sind für eine belastbare Release- und Wirtschaftlichkeitsplanung erforderlich. Eine günstigere Alternative ist ein Ziel, kein bereits kalkulierter Preisvorteil. Das [Funktionsinventar](docs/FUNCTION_INVENTORY.md) benennt die wesentlichen Kostentreiber; es enthält keine neue Preis- oder Terminzusage.

### OPEN-021 Detailumfang der Fachmodule und späteren Funktionen

**Status:** teilweise entschieden · **Fällig:** V1-Details vor deren Umsetzung; vollständiges Funktionsinventar vor Implementierungsstart · **Bezug:** REQ-PROD-003, REQ-TIME-001 bis REQ-TIME-003, REQ-REQ-001 bis REQ-REQ-004, REQ-EXT-001

DEC-030 bestätigt neue Revision und erneute Freigabe nach inhaltlicher Änderung einer freigegebenen Anforderung. Offen bleiben die genaue Abgrenzung inhaltlicher Änderungen, Standardfelder, Ticketbeziehungen, Zeitkorrekturen und Exportumfang; der [Fachmodellentwurf](docs/DOMAIN_MODEL.md) liefert prüfbare Vorschläge. Alle als Vorschlag bezeichneten V1-Ergänzungen und Kandidaten späterer Module müssen priorisiert werden. Die Freigabe des einfachen Anforderungsmoduls bedeutet keine Freigabe eines beliebig komplexen Genehmigungsworkflows. Das [Funktionsinventar vom 17. Oktober 2026](docs/FUNCTION_INVENTORY.md) liegt mit Kernumfang, priorisierten V1-Vorschlägen und späteren Modulen vor. DEC-041 bestätigt Kanban, Gantt und einfache Dashboards als erste weitere Fachpriorität nach V1; 2FA und Redmine-Import bleiben früh eingeplant. Die übrigen Prioritäten und Detailumfänge sind nicht bestätigt.

DEC-046 bestätigt umfangreiche Tags für Tickets/Tasks und Projekte bereits ab V1, DEC-049 die Erstellung auch durch berechtigte externe Portalbenutzer. Die frühere Einordnung von Tags als bloßem Standardfeld-Kandidaten ist damit überholt. Katalogbereiche, konkrete Vergabe der Erstellrechte, Massenänderungen, Pflege und Grenzen bleiben im [Tag- und Suchentwurf](docs/TAGS_SEARCH.md) Vorschläge; mit OPEN-005 und OPEN-014 abstimmen.

## Entscheidungsverfahren

Neue Entscheidungen bekommen eine neue DEC-ID mit Datum, Verantwortlichem, Begründung und betroffenen REQ-IDs. Bereits bestätigte Entscheidungen werden nicht still geändert. Eine Ablösung nennt Vorgänger und Nachfolger sowie ihre Folgen für Anforderungen, Roadmap und offene Aufgaben. Technische Recherche wird bei der jeweiligen Entscheidung mit Quelle und Abrufdatum dokumentiert; diese erste Fassung enthält Gesprächsentscheidungen und Entwurfsvorschläge, keine neuen Produkt- oder Preisrecherchen.
