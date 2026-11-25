# Zentrale offene Aufgaben

Stand 18. Oktober 2026 · Planung, Repository-Infrastruktur und Frontend-Grundfläche (TASK-023)

Diese Datei enthält den gemeinsamen Arbeitsstand aller beauftragten KI-Sitzungen. Status und Zuständigkeit werden gemäß [AGENTS.md](AGENTS.md) koordiniert. Eine Aufgabe kann mehrere Anforderungen konkretisieren. Ein offener Entwurf gilt nicht als fachlich abgenommen.

Repository: [1uedev/task-manager](https://github.com/1uedev/task-manager). Maßgeblich ist diese Datei auf main. TASK-013 richtet die Arbeitsumgebung ein. Die übergreifende Koordination liegt bei Holger oder einer ausdrücklich von ihm benannten Sitzung. Aufgaben werden über einen reservierenden Pull Request vergeben; siehe docs/AI_WORKFLOW.md. GitHub Issues sind Diskussions- und Einstiegspunkte, keine zweite Aufgabenquelle.

Am 28. Oktober 2026 hat Holger alle bis dahin zur Prüfung stehenden Aufgaben abgenommen, darunter TASK-001 bis TASK-021; sie stehen in [DONE.md](DONE.md). Neue Aufgaben brauchen weiterhin einen eigenen Auftrag und eine Reservierung.

<!-- TASK-BOARD:START -->
## Aufgabenübersicht

Automatisch aus den Aufgabeneinträgen weiter unten erzeugt. Maßgeblich ist TODO.md auf main; eine lokale Kopie kann veraltet sein. Aktualisieren: `python3 scripts/task_board.py --write`.

| Kategorie | Anzahl | Bedeutung |
|---|---:|---|
| Frei und bearbeitbar (OFFEN) | 0 | Zur Reservierung verfügbar; Auftrag und Abhängigkeiten im Detail prüfen. |
| Durch eine KI reserviert (IN ARBEIT) | 0 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 4 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 6 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

Derzeit keine Aufgaben in dieser Kategorie.

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-127](#task-127-ki-anforderungsprüfung-und-wiki-überarbeitung-a16-teil-3) | KI Anforderungsprüfung und Wiki Überarbeitung (A16 Teil 3) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-128](#task-128-ki-prüfsatz-demo-lauf-gegen-echte-anbieter-und-betriebsdokumentation-a16-teil-4) | KI Prüfsatz, Demo, Lauf gegen echte Anbieter und Betriebsdokumentation (A16 Teil 4) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-133](#task-133-betriebszustand-und-überwachung-a18-teil-3) | Betriebszustand und Überwachung (A18 Teil 3) | claude-20261123-ops-01 | 2026-11-23 |
| [TASK-134](#task-134-referenzmessung-und-v1-freigabeprüfung-a18-teil-4) | Referenzmessung und V1 Freigabeprüfung (A18 Teil 4) | claude-20261123-ops-01 | 2026-11-23 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-126](#task-126-ki-ticketzusammenfassung-und-antwortentwurf-a16-teil-2) | KI Ticketzusammenfassung und Antwortentwurf (A16 Teil 2) | claude-20261117-ai-01 | 2026-11-25 |
| [TASK-131](#task-131-sicherung-und-wiederherstellung-mit-praktischem-nachweis-a18-teil-1) | Sicherung und Wiederherstellung mit praktischem Nachweis (A18 Teil 1) | claude-20261123-ops-01 | 2026-11-23 |
| [TASK-132](#task-132-installation-version-und-update-für-den-betrieb-a18-teil-2) | Installation, Version und Update für den Betrieb (A18 Teil 2) | claude-20261123-ops-01 | 2026-11-23 |
| [TASK-135](#task-135-befunde-f1-bis-f11-und-hinweise-h1-bis-h3-der-codex-prüfung-von-task-131-beheben) | Befunde F1 bis F11 und Hinweise H1 bis H3 der Codex Prüfung von TASK-131 beheben | claude-20261123-opsfix-01 | 2026-11-23 |
| [TASK-136](#task-136-befunde-f1-bis-f12-und-hinweise-h1-bis-h4-der-codex-prüfung-von-task-132-beheben) | Befunde F1 bis F12 und Hinweise H1 bis H4 der Codex Prüfung von TASK-132 beheben | claude-20261123-opsfix-02 | 2026-11-23 |
| [TASK-137](#task-137-befunde-n1-bis-n12-und-hinweise-h1-bis-h5-der-codex-prüfung-von-task-136-beheben) | Befunde N1 bis N12 und Hinweise H1 bis H5 der Codex Prüfung von TASK-136 beheben | claude-20261123-opsfix-03 | 2026-11-23 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-126 KI Ticketzusammenfassung und Antwortentwurf (A16 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261117-ai-01 · **Aktualisiert:** 2026-11-25

**Bezug:** REQ-AI-001, REQ-AI-004, REQ-AI-005, REQ-AI-006, REQ-HD-001; DEC-034, DEC-035; ACC-010; docs/AI_USE_CASES.md (AI-UC-01, AI-UC-02, Prüffälle A01 bis A03, A08 bis A11). **Abhängigkeiten:** TASK-125 auf main; TASK-058, TASK-076, geliefert. **Arbeitsstand:** Mit TASK-125 von Holger am 17. November 2026 beauftragt und mit den Vorschlägen bestätigt. Nach der Abnahme von TASK-125, TASK-129, TASK-130 und TASK-138 (PR #354) gibt Holger am 25. November 2026 mit „ja“ den Beginn frei. Geplanter Branch claude/project-thread-av1na6, Entscheidung DEC-157, Migration V0074 nur falls nötig.

**Umfang:** Die Funktionen Ticketzusammenfassung und Antwortentwurf mit Kontext, Validierung, Vorschau, Vertrag, Oberfläche und Tests. Nicht Teil: Speichern der Zusammenfassung am Ticket, Wikiseiten als Quelle, automatischer Versand.

**Fachliche Regeln** (von Holger am 17. November 2026 bestätigt):
1. Ticketzusammenfassung: Kontext sind Titel, Beschreibung, Typ, Status, Priorität, lesbare eigene Felder, Kundenmails, gesendete Antworten und das Portalgespräch, soweit lesbar. Ergebnis: Sachstand, offene Punkte und Quellen als Verweise auf die Einträge. Nur Vorschau mit „Kopieren“; am Ticket wird nichts gespeichert. Voraussetzung: `ai.use` und Leserecht am Ticket.
2. Antwortentwurf: „Entwurf mit KI“ im Dialog „Antwort an Kunden“ und bei der Portalantwort. Kontext ist nur, was die Kundschaft schon sehen darf: Titel, Kundenmails, gesendete Antworten, Portalgespräch, dazu ein kurzes Ziel der Person. Keine internen Felder, keine Wikiseiten. Der Entwurf landet bearbeitbar im Textfeld; Versand nur über das bestehende Prüfen und Bestätigen. Sprache wie die des Kontakts. Voraussetzung: `ai.use` und `helpdesk.reply` beziehungsweise `portal.answer`.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Die Zusammenfassung trennt belegten Stand von offenen Punkten und verweist nur auf übergebene Einträge; unbekannte Quellen werden abgelehnt (A01, A03, A15).
2. Der Kontext des Antwortentwurfs enthält keine internen Felder und keine für die Person verbotenen Felder (A02); Anweisungen in einer Kundenmail ändern nichts (A08).
3. Ein Entwurf wird nur nach Prüfen und Bestätigen versendet; geänderter Text braucht eine neue Prüfung, doppelte Bestätigung erzeugt höchstens einen Versand (A10, A11).
4. Vertrag gegen Routen, Backend Tests, Browser Tests für Ticket, Antwortdialog und Portalantwort.

**Übergabe (2026-11-25, claude-20261117-ai-01):** Ergebnis auf dem Branch claude/project-thread-av1na6 im Ergebnis PR, ohne Migration. Neues Modul `taskmanager.assist` mit den Routen `GET /api/v1/projects/{projectId}/tickets/{number}/ai`, `POST …/ai/summary` und `POST …/ai/reply-draft` (Vertrag contracts/assist.openapi.yaml), gelesen nur über die neuen Ports `tickets.TicketReader`, `sla.TicketPriorities` und `helpdesk.TicketConversation` mit den Regeln der Oberfläche; Prüfer je Anwendungsfall und Objektart, die den Kontext beim Lesen mit den heutigen Rechten neu bauen und über einen Fingerabdruck vergleichen (`ai.source_changed`). Zu den Kriterien: 1 `AssistApiTest`: Quelle S1 mit den lesbaren Feldern als Pflicht, danach Mails, gesendete Antworten und Portalbeiträge; Sachstand und offene Punkte mit Verweisen; eine Antwort mit unbekannter Quelle wird verworfen (A15); nicht lesbare Felder und Mails fehlen; geänderte Quelle oder verlorenes Recht machen den Vorschlag ungültig. 2 Der Entwurf bekommt nur Titel und Einträge des aktuellen Kunden, nie Beschreibung, eigene Felder oder Priorität; der Text einer Mail wird nur als markierte Quelle übergeben (A08), Englisch beim englischen Kontakt, Rechte einer Antwort und Kanal geprüft, Portalkonten 403. 3 Browsertest `ai-assist`: Entwurf im Textfeld, Enter im Ziel prüft und sendet nicht, nach „Zurück“ und Ändern gibt es kein „Jetzt senden“ ohne neue Prüfung, gesendet wird genau der geprüfte Text, zwei Klicks auf „Jetzt senden“ und auf „Im Portal veröffentlichen“ ergeben je eine Anfrage; eigener Text bleibt bis „Text ersetzen“, Rückfragen und Hinweise nur für die Person. Auf dem Server ist jede Bestätigung weiterhin ein eigener Versand; die Sperre gegen den Doppelklick liegt in der Oberfläche, der unsichere SMTP Fall folgt TASK-058. 4 `AssistContractTest` (drei Routen), `AssistApiTest`, Browsertests für Ticket, Antwortdialog und Portalantwort. Auslegung: Regel 2 nennt für die Portalantwort `portal.answer`; das ist seit DEC-106 die Aktion der Portalkonten, der Support antwortet im Portal mit `helpdesk.reply`, und so verlangt es auch der Entwurf (README des Backends, Abschnitt „KI am Ticket“). Gelaufen: Backend Tests der Module ai, assist, helpdesk, Portalgespräch, sla und tickets sowie ModuleStructureTest gegen lokales PostgreSQL 16, `npm run verify`, Browsertests ai-assist, ai, helpdesk, portal, app und sla, Repository Prüfungen. Nicht gelaufen: echte Anbieter (TASK-128).

**Nächster Schritt:** Codex Prüfung des Ergebnis PR nach dem Prüfauftrag, danach Holgers Abnahme; TASK-127 bleibt bis zum Ergebnis von TASK-126 auf main blockiert.

## TASK-127 KI Anforderungsprüfung und Wiki Überarbeitung (A16 Teil 3)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261117-ai-01 · **Aktualisiert:** 2026-11-17

**Bezug:** REQ-AI-001, REQ-AI-004, REQ-AI-005, REQ-AI-006, REQ-REQ-001, REQ-REQ-002; DEC-030, DEC-033, DEC-034; ACC-010; docs/AI_USE_CASES.md (AI-UC-03, AI-UC-04, Prüffälle A04, A05, A09, A15). **Abhängigkeiten:** TASK-126 auf main; TASK-093, TASK-094, TASK-096, TASK-098, geliefert. **Arbeitsstand:** Mit TASK-125 von Holger am 17. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-126 auf main ist. Geplanter Branch claude/project-thread-av1na6, Entscheidung DEC-158.

**Umfang:** Die Funktionen Anforderungsprüfung und Wiki Überarbeitung mit Kontext, Validierung, Vergleich, Übernahme in den Editor, Vertrag, Oberfläche und Tests. Nicht Teil: Freigabe oder Statuswechsel durch KI, Veröffentlichung durch KI, Übersetzung, Bearbeitung mehrerer Seiten oder Anforderungen auf einmal.

**Fachliche Regeln** (von Holger am 17. November 2026 bestätigt):
1. Anforderungsprüfung: prüft die angezeigte Revision (Titel, Beschreibung, Abnahmekriterien, lesbare eigene Felder) auf Unklarheiten, Widersprüche, fehlende oder nicht messbare Kriterien. Ergebnis: Befundliste mit Textstelle, Begründung und optionalem Änderungsvorschlag. „Übernehmen“ schreibt den Vorschlag nur in den Editor; erst normales Speichern mit If-Match ändert die Anforderung und folgt den Revisionsregeln. Die KI gibt nie frei und ändert keinen Status. Voraussetzung: `ai.use` und `requirement.read`, Übernahme mit `requirement.edit`.
2. Wiki Überarbeitung: Die Person wählt im Entwurf einen Abschnitt (Überschrift bis zur nächsten) oder die ganze Fassung und ein Ziel („kürzer“, „klarer gliedern“, „sachlicher“ oder eigener Text). Der Server übergibt den Text vereinfacht; Bilder und Seitenlinks werden zu Platzhaltern, die im Ergebnis wieder vorkommen müssen, sonst wird es abgelehnt. Anzeige als Vergleich alt und neu; „Übernehmen“ setzt den Text in den Editor, Speichern des Entwurfs wie bisher, Veröffentlichen bleibt eigene Aktion. Voraussetzung: `ai.use` und `wiki.edit`.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Eine Prüfung liefert Befunde mit Textstelle; übernommene Änderungen an einer freigegebenen Anforderung erzeugen beim Speichern eine neue Revision zur erneuten Freigabe (A04).
2. Eine Wiki Überarbeitung zeigt den Vergleich, die Übernahme ändert nur den Entwurf, Bilder und Links bleiben erhalten oder das Ergebnis wird abgelehnt (A05).
3. Ändert sich die Quelle oder das Recht vor der Übernahme, ist der Vorschlag ungültig (A09); eine Ausgabe mit Skript wird nicht ausgeführt (A15).
4. Vertrag gegen Routen, Backend Tests, Browser Tests für Anforderung und Wiki.

**Nächster Schritt:** Nach dem Ergebnis von TASK-126 auf main die Blockade aufheben und umsetzen.

## TASK-128 KI Prüfsatz, Demo, Lauf gegen echte Anbieter und Betriebsdokumentation (A16 Teil 4)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261117-ai-01 · **Aktualisiert:** 2026-11-17

**Bezug:** REQ-AI-007, REQ-AI-008, REQ-AI-009, REQ-PERF-004; DEC-034, DEC-035; OPEN-003, OPEN-019; ACC-010; docs/AI_USE_CASES.md (Prüffälle und Qualitätsnachweis), docs/DEMO.md, README.md. **Abhängigkeiten:** TASK-127 auf main; TASK-031. **Arbeitsstand:** Mit TASK-125 von Holger am 17. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-127 auf main ist. Geplanter Branch claude/project-thread-av1na6, Entscheidung DEC-159.

**Umfang:** Synthetischer Prüfsatz mit Auswertung, Lauf gegen ein lokales Ollama im Container, Demodaten und Compose Profil `ki` im Vorführimage, Betriebsdokumentation und Fortschreibung von OPEN-003 und OPEN-019. Nicht Teil: dauerhafte Zugangsdaten im Repository, Modell im Anwendungsimage.

**Fachliche Regeln** (von Holger am 17. November 2026 bestätigt):
1. Automatische Tests laufen gegen nachgebaute Anbieterserver ohne Abrufe ins Internet und ohne Kosten mit den Prüffällen A01 bis A16. Dazu ein synthetischer Prüfsatz mit zehn Fällen je Funktion und Sprache, den ein eigener Lauf gegen ein eingetragenes Modell ausführt und auswertet; Zielwert 80 Prozent brauchbar, alle Sicherheitsfälle bestanden.
2. Für die Abnahme läuft der Prüfsatz gegen ein lokales Ollama im Container ohne Kosten; gegen OpenAI oder Anthropic nur, wenn Holger einen Schlüssel einträgt.
3. Das Vorführimage bringt einen Ollama Zugang ohne Modell, die eingebauten Skills, die Zuordnungen und `ai.use` für die Demogruppen mit. Ein optionales Compose Profil `ki` startet Ollama mit einem kleinen Modell (einige GB Download beim ersten Start).
4. README und docs/AI_USE_CASES.md beschreiben Einrichtung, Datenwege, Grenzen und Speicherung; OPEN-003 und OPEN-019 werden mit dem Ergebnis als entschieden geführt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Der Prüfsatz läuft gegen Ollama im Container mit Auswertung; alle Sicherheitsfälle bestehen (ACC-010).
2. Das Vorführimage startet mit den Demodaten; mit dem Profil `ki` funktionieren die vier Funktionen ohne Internetanbieter.
3. Dokumentation liegt vor; OPEN-003 und OPEN-019 sind fortgeschrieben.

**Nächster Schritt:** Nach dem Ergebnis von TASK-127 auf main die Blockade aufheben und umsetzen.

## TASK-131 Sicherung und Wiederherstellung mit praktischem Nachweis (A18 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261123-ops-01 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-OPS-004, REQ-OPS-006, REQ-DATA-003, REQ-SEC-001; DEC-038; OPEN-012; ACC-008; docs/OPERATIONS.md (Sicherungsziel und vollständige Wiederherstellung, Prüffälle O06 bis O10). **Abhängigkeiten:** TASK-031 (Vorführimage), TASK-051 (Dateispeicher), TASK-054 (Hintergrundaufträge), TASK-058 (Mailantworten mit Zustand unsicher), TASK-125 (KI Vorschläge), geliefert. **Arbeitsstand:** Holger hat am 23. November 2026 im Projektgespräch mit „ja dann mache weiter“ den Beginn von Paket A18 beauftragt und am selben Tag die 24 Vorschläge mit „ok“ übernommen (/mnt/project-files/a18/fragen-a18.md; Regeln unten und bei TASK-132 bis TASK-134). Geplanter Branch claude/project-thread-elfpvq, Entscheidung DEC-161, Migrationen ab V0070.

**Umfang:** Dienst `backup` mit `pg_basebackup`, fortlaufender Archivierung der Schreibprotokolle und restic, gemeinsame Sicherungspunkte von Datenbank und Dateispeicher, Rücksicherung genau bis zum Zeitpunkt eines Punkts, der Befehl `after-restore`, die Prüfung der Dateiinhalte, die Anleitung und ein Workflow, der den vollständigen Ausfall durchspielt und Datenverlust und Wiederanlaufzeit misst. Nicht Teil: Betriebsvorlage mit TLS und Updates (TASK-132), Betriebsübersicht (TASK-133), Messung und Freigabe (TASK-134), Hochverfügbarkeit, automatischer Abgleich von Rechten nach dem Sicherungspunkt, Aufbewahrungsregeln mit Löschung von Fachdaten.

**Fachliche Regeln** (von Holger am 23. November 2026 bestätigt):
1. Paket A18 liefert Sicherung und Wiederherstellung mit praktischem Nachweis, Installation und Update für den Betrieb, Betriebszustand für das Betriebspersonal und die V1 Freigabeprüfung. Nicht dabei: Hochverfügbarkeit, Kubernetes, ARM64, unterbrechungsfreie Updates, Fernwartungswerkzeug, Telemetrie, Lizenzprüfung, Virenscanner, Aufbewahrungsregeln mit Löschung (OPEN-016) und Benachrichtigungen (REQ-TKT-005).
2. Referenzplattform ist Linux x86 64 mit Ubuntu 24.04 LTS, Docker Engine und Docker Compose in der bei der Freigabe gewarteten Version und PostgreSQL 18 als Container aus der Lieferung. Eine eigene PostgreSQL 18 Instanz des Kunden ist erlaubt, wenn die Sicherung nach demselben Verfahren läuft. Anderes ist bis zu einer eigenen Prüfung nicht unterstützt; die Versionsmatrix steht in der Betriebsanleitung.
3. Der Betreiber (Kunde oder ausdrücklich beauftragter Anbieter) verantwortet Host, Netz, TLS Zertifikat, Sicherungsziel, Schlüssel und Erreichbarkeit. Keine Bereitschaft rund um die Uhr; die 4 Stunden gelten ab Feststellung des Ausfalls innerhalb der vereinbarten Servicezeit. Die Anleitung enthält eine Vorlage für Betreiber, Servicezeit, Sicherungsziel und Ansprechpartner je Installation.
4. Die Anleitung listet jede ausgehende Verbindung mit Zweck (Mailserver, LDAP, Git, KI, Sicherungsziel, Image Registry). Keine Telemetrie, keine Verbindung zum Hersteller; ohne Internet bleiben Tickets, Wiki, Anforderungen und Zeiten nutzbar.
5. Werkzeug sind die Mittel von PostgreSQL (`pg_basebackup`, Archivierung der Schreibprotokolle) mit restic für das Sicherungsziel, in einem eigenen Dienst `backup`, nicht im Anwendungsprozess.
6. Ein Sicherungspunkt ist genau ein restic Stand mit Schreibprotokollen bis zu einem festgehaltenen Zeitpunkt T und dem nach T gelesenen Dateispeicher; die Datenbank wird genau bis T wiederhergestellt. Ein Punkt gilt erst als vollständig, wenn restic ihn auf dem Ziel bestätigt hat.
7. Wechsel der Schreibprotokolle spätestens alle 5 Minuten, ein Sicherungspunkt alle 15 Minuten, eine Basissicherung täglich. Warnung ab 30 Minuten ohne neuen vollständigen Punkt, kritisch ab 45 Minuten.
8. Unterstützte Ziele sind ein S3 kompatibler Objektspeicher und SFTP auf einem anderen Rechner, jeweils mit eigenem Zugang, der nach Möglichkeit nicht löschen darf. Ein Verzeichnis auf demselben Rechner ist nur für Tests erlaubt und wird als „kein Schutz bei Hostverlust“ gekennzeichnet. Aufbewahrung 14 Tage vollständig wiederherstellbar.
9. `TASK_MANAGER_SECRET_KEY`, das restic Passwort und die Zugangsdaten des Ziels liegen nicht im Sicherungsstand, sondern getrennt beim Betreiber; die Anleitung verlangt beim Einrichten eine Prüfung, dass sie dort vorhanden sind. Ohne Schlüssel müssen Postfachpasswörter, Git Tokens und KI Schlüssel neu eingetragen werden.
10. Der Befehl `after-restore` läuft vor der Freigabe einmal: er beendet alle Sitzungen, setzt Mailantworten, die noch nicht als gesendet galten, auf „unsicher“ und hält sie an, verwirft KI Vorschläge, prüft zu jedem Anhang und Wikibild Inhalt und Prüfsumme, schreibt einen Bericht mit den Kennungen ohne Inhalt, listet alle aktiven Konten mit Verwaltungsrechten und hält im Audit fest, wer ihn wann zu welchem Sicherungspunkt ausgeführt hat. Webprozess und Ausführer starten erst danach. Der IMAP Abruf holt erneut ab, was nach T kam.
11. Kein automatischer Abgleich von Rechten und Löschungen nach T: der Betreiber führt bekannte Kontosperren und Rechteentzüge vor der Freigabe erneut aus. Lokale Passwörter bleiben auf dem Stand von T.
12. Ein eigener Workflow „Wiederherstellung“ (manuell und wöchentlich) baut die Umgebung mit Sicherung auf, erzeugt Demodaten und Last, sichert laufend, zerstört Datenbank und Dateien vollständig, stellt auf einer frischen Umgebung wieder her, führt `after-restore` aus und prüft Kernabläufe. Er misst den Datenverlust und die Zeit vom Ausfall bis zur geprüften Nutzung und prüft O06 (fehlende Datei, kein vollständiger Punkt) und O08 (beschädigter Stand oder fehlender Schlüssel, Rückfall auf den älteren Punkt).

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Der Dienst `backup` legt alle 15 Minuten einen vollständigen Punkt auf dem Ziel an; eine fehlende Datei oder ein gescheiterter Upload ergibt keinen vollständigen Punkt (O06).
2. Die Rücksicherung auf einer frischen Umgebung stellt Datenbank bis T und passende Dateien wieder her; der Workflow weist Datenverlust unter 60 Minuten und Wiederanlauf unter 240 Minuten für den dokumentierten Bestand aus (O07).
3. Ein beschädigter Punkt oder fehlender Schlüssel führt zum Rückfall auf den älteren Punkt mit Meldung, nie zu einem falschen Erfolg (O08).
4. `after-restore` hält offene Mailantworten an, beendet Sitzungen, verwirft KI Vorschläge und meldet fehlende Inhalte (O09, O10); Tests dafür.
5. Anleitung mit Einrichtung, Schlüsselablage, Zielen, Rücksicherung und Vorlage für Zuständigkeiten.

**Ergebnis** (claude-20261123-ops-01, 23. November 2026): Umgesetzt auf dem Branch claude/project-thread-elfpvq.

- Migration V0070: Sicht `operations_backup_content` (Inhalte mit Größe und Prüfsumme), Tabelle `operations_restore_pending` (Vormerkung der Rücksicherung) und `operations_audit_event` für den Betrieb.
- Modul `operations` mit dem Befehl `after-restore <Name>`: beendet alle Sitzungen, hält offene Mailantworten als `uncertain` mit dem Code `helpdesk.restored` an, verwirft KI Vorschläge, prüft jeden Inhalt des Dateispeichers über Größe und SHA-256, listet die Konten mit Rechten für das ganze Unternehmen, entfernt die Vormerkung und schreibt den Auditeintrag `restore.released`. Rückgabewerte 0, 3 (Inhalte fehlen oder sind beschädigt), 2 (abgelehnt), 1 (Fehler). Webserver und Ausführer verweigern den Start, solange die Vormerkung besteht.
- Neue Zugänge der Module: `IdentityRestore`, `HelpdeskRestore`, `AiRestore`, `FilesRestore` und `CompanyAdministration.companyRightHolders`.
- Dienst `deploy/backup` (restic 0.18.1 im PostgreSQL 18.6 Image) mit `backup.sh` (Basissicherung, Protokollwechsel, Prüfung der Inhalte vor dem Punkt, Beschreibung, Aufbewahrung) und `restore.sh` (`points`, `restore`, `mark`) mit Rückfall auf den älteren Punkt und Rücksicherung auf die Schreibposition des Punkts.
- Probe `tools/restore/restore-check.sh` mit eigenem Compose und Schreiber, Workflow `.github/workflows/restore.yml` (bei Änderungen, wöchentlich, manuell).
- Anleitung `docs/BACKUP_RESTORE.md` (Einrichtung, Ziele, Schlüsselablage, neunschrittiger Wiederanlauf, Probe, Zuständigkeitsvorlage, ausgehende Verbindungen, Grenzen), Hinweise in docs/OPERATIONS.md und backend/README.md, Entscheidung DEC-161.

**Geprüft:** `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `python3 -m unittest discover -s tests` (50 Tests), Backend `./mvnw test` mit Java 21 lokal (CI prüft Java 25), `backend/scripts/lifecycle-check.sh` einschließlich der neuen Phase `after-restore`, Frontend `npm run verify`. Die Probe lief vollständig auf diesem Rechner (Linux x86 64, 4 vCPU): Punkt mit fehlendem Inhalt abgelehnt (O06), beschädigter neuester Punkt übersprungen und der ältere genommen (O08), `after-restore` hat 7 Sitzungen beendet und die Rechteliste ausgegeben (O09, O10), Datenverlust 11 s, wieder nutzbar nach 105 s (Ziele 3600 s und 14400 s). Bericht und Protokolle: /mnt/project-files/a18/probe-2026-11-23/.

**Abweichungen:** Die gemessenen Werte gelten für diesen Rechner und einen kleinen Bestand; der Nachweis auf der Referenzumgebung mit vollem Daten- und Dateibestand bleibt nach DEC-038 Teil von TASK-134. Der Dateispeicher gehört nach der Rücksicherung dem Konto des Sicherungsdienstes und wird in Schritt 4 der Anleitung an das Konto der Anwendung übergeben.

**Nächster Schritt:** Holger nimmt ab (Ergebnis-PR und Codex Prüfauftrag /mnt/project-files/a18/codex-pruefauftrag-task-131.md); danach TASK-132 in derselben Sitzung, sobald das Ergebnis auf main ist.

## TASK-132 Installation, Version und Update für den Betrieb (A18 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261123-ops-01 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-OPS-001, REQ-OPS-002, REQ-OPS-003, REQ-OPS-005, REQ-SEC-001; OPEN-012; ACC-008; docs/OPERATIONS.md (Referenzinstallation, Updates und Rückkehr, Prüffälle O01, O11). **Abhängigkeiten:** TASK-131 auf main. **Arbeitsstand:** Mit TASK-131 von Holger am 23. November 2026 beauftragt und mit den Vorschlägen bestätigt; nach dem Ergebnis von TASK-131 auf main (PR #345) umgesetzt. Branch claude/project-thread-elfpvq, Entscheidung DEC-162.

**Umfang:** Betriebsvorlage `deploy/compose.yaml` mit TLS Einstieg, Versionen und Veröffentlichung des Images auf ein Tag von Holger, der Befehl `migrate` mit Startprüfung der Schemaversion, der Updatepfad mit Workflow und die Installations und Updateanleitung. Nicht Teil: unterbrechungsfreie Updates, automatische Updates, andere Plattformen, Fernwartungswerkzeug.

**Fachliche Regeln** (von Holger am 23. November 2026 bestätigt):
1. Eigene Datei `deploy/compose.yaml` neben der Vorführung mit Caddy als TLS Einstieg (eigenes Zertifikat oder ACME, wählbar), Webprozess, Ausführer, PostgreSQL und Sicherung, Grenzen für Speicher und CPU, ohne Docker Socket und ohne privilegierte Container. Ein vorhandener Proxy des Kunden ist erlaubt, die Anleitung nennt die nötigen Kopfzeilen. Die Vorführung bleibt unverändert.
2. Versionen nach dem Schema 1.0.0. Eine Version entsteht nur, wenn Holger ein Tag setzt; dann baut ein Workflow das Image, veröffentlicht es in `ghcr.io/1uedev/task-manager` und legt Digest, Komponentenverzeichnis (SBOM) und den Bericht der Schwachstellenprüfung zur Version. Die Betriebsvorlage bezieht das Image nur über den Digest. Ohne Tag wird nichts veröffentlicht.
3. Genau ein Schemaänderungslauf je Update über den Befehl `migrate`. Webprozess und Ausführer ändern das Schema in der Betriebsvorlage nicht selbst und starten nicht, wenn Migrationen fehlen oder die Datenbank neuer ist als das Image. Die Vorführung behält das heutige Verhalten.
4. Update: Sicherungspunkt abwarten, Anwendung anhalten, `migrate`, starten, Kernabläufe prüfen. Rückkehr nur durch Rücksicherung des Punkts von vor dem Update, nie durch ein älteres Image. Ein Workflow „Update“ prüft bei jeder Änderung an Migrationen den Weg vom Stand der letzten Version (bis zur ersten Version von einem festen früheren Stand auf main) mit Demodaten auf den neuen Stand mit Datenerhalt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Eine Installation nach Anleitung mit `deploy/compose.yaml` startet mit TLS; ein Austausch der Container behält Daten und Konfiguration, das Image enthält keine Geheimnisse (O01).
2. Ein Start mit fehlender Migration oder neuerer Datenbank wird mit klarer Meldung abgewiesen; `migrate` läuft genau einmal (O11).
3. Der Workflow „Update“ besteht mit Datenerhalt; der Veröffentlichungsworkflow erzeugt Digest, SBOM und Schwachstellenbericht und veröffentlicht nur auf ein Tag.
4. Installations und Updateanleitung mit Versionsmatrix und Liste der ausgehenden Verbindungen.

**Ergebnis (Übergabe):**
- Befehl `migrate` (`MigrateCommandRunner`) und Startprüfung `SchemaGuard` mit `TASK_MANAGER_SCHEMA_MODE`: `migrate` (Vorgabe, Vorführung unverändert) oder `verify` (Betriebsvorlage). `verify` startet nicht bei fehlenden Migrationen, bei einer Datenbank, die neuer ist als das Image, oder nach einer gescheiterten Migration, jeweils mit klarer Meldung; `migrate` lehnt eine neuere Datenbank mit Rückgabewert 2 ab und läuft dank der Sperre von Flyway genau einmal.
- Betriebsvorlage `deploy/compose.yaml` mit Caddy 2.10.2 (ACME oder eigenes Zertifikat), Webprozess, Ausführer, PostgreSQL 18.6, Sicherungsdienst und Dienst `migrate`, Grenzen für Speicher und CPU, schreibgeschützte Dateisysteme, ohne Capabilities, Docker Socket und privilegierte Container; dazu `Caddyfile`, `db-init.sh` (Rolle des Sicherungsdienstes) und `.env.example`. Das Sicherungsimage enthält jetzt den ssh Client für SFTP.
- Versionsangaben im Image (`TASK_MANAGER_VERSION`, OCI Labels), Workflow `release.yml` nur auf ein Tag `vMAJOR.MINOR.PATCH` auf main (beide Images, SPDX Komponentenverzeichnis, Schwachstellenbericht, Digests, Vorlage mit Prüfsummen), Workflow `update.yml` mit der Prüfung `tools/update/update-check.sh` vom festen Stand 7731cd0 beziehungsweise der letzten Version.
- Anleitung `docs/INSTALL.md` (Versionsmatrix, Installation, eigener Proxy, Startprüfung, Update, Rückkehr, ausgehende Verbindungen, Grenzen), Entscheidung DEC-162, Hinweise in README.md, backend/README.md, docs/OPERATIONS.md und docs/BACKUP_RESTORE.md.

**Geprüft:** `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `python3 -m unittest discover -s tests`, Backend `./mvnw test` mit Java 21 lokal (CI prüft Java 25) einschließlich der sechs Tests von `MigrateCommandTest`, `backend/scripts/lifecycle-check.sh` mit der neuen Phase `schema` und einem Start im Modus `verify`. Die Updateprüfung lief vollständig auf diesem Rechner: V0069 nach V0070 mit Demodaten hinter TLS mit eigener CA, Start vor `migrate` abgewiesen, zweiter `migrate` ohne Änderung, Datenfingerabdruck gleich (4 Projekte, 43 Tickets, 16 Tags, 7 Konten), Kernabläufe über TLS, Austausch der Container, Sicherungspunkt mit eigener Rolle, keine Geheimnisse im Image, keine privilegierten Container. Protokolle: /mnt/project-files/a18/update-2026-11-23/.

**Abweichungen:** Der Veröffentlichungsworkflow wurde nicht ausgeführt, weil er nur auf ein Tag von Holger läuft; das Komponentenverzeichnis wurde lokal mit derselben syft Version aus dem Image erzeugt, der Schwachstellenbericht nicht. Das Sicherungsimage mit ssh Client ließ sich lokal nicht bauen (Paketquellen gesperrt); den Bau prüft die CI im Workflow „Update“. Befund F8 der Prüfung von TASK-131 ist damit erst teilweise behoben; der SFTP Nachweis gehört zur Behebung dieser Befunde.

**Nächster Schritt:** Holger nimmt ab (Ergebnis-PR und Codex Prüfauftrag /mnt/project-files/a18/codex-pruefauftrag-task-132.md); danach TASK-133 in derselben Sitzung, sobald das Ergebnis auf main ist.

## TASK-133 Betriebszustand und Überwachung (A18 Teil 3)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261123-ops-01 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-OPS-006, REQ-SEC-001; OPEN-012; docs/OPERATIONS.md (Betriebsverantwortung, Ressourcen und Speicherrechnung, Prüffälle O03, O04, O12, O16). **Abhängigkeiten:** TASK-132 auf main. **Arbeitsstand:** Mit TASK-131 von Holger am 23. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-132 auf main ist. Geplanter Branch claude/project-thread-elfpvq, Entscheidung DEC-163.

**Umfang:** Unternehmensrecht `operations.read`, Seite „Betrieb“, Endpunkt für externe Überwachung mit eigenem Schlüssel, Befehle `backup-status` und `diagnostics`, die Speichergrenze für Uploads, Migration, Vertrag, Oberfläche und Tests. Nicht Teil: Alarmversand durch das Produkt, Telemetrie, Fernwartungszugang.

**Fachliche Regeln** (von Holger am 23. November 2026 bestätigt):
1. Neues Unternehmensrecht `operations.read` und eine Seite „Betrieb“ mit Version und Schema, Erreichbarkeit der Datenbank, freiem Platz des Dateispeichers mit den Stufen 70 und 85 Prozent, Rückstand und gescheiterten Hintergrundaufträgen, gescheiterten und unsicheren Mailantworten, Postfächern mit letztem erfolgreichen Abruf und dem Alter des letzten vollständigen Sicherungspunkts. Ein Ausfall von KI oder Git macht die Anwendung nicht unbereit.
2. Endpunkt `GET /api/v1/operations/status` mit eigenem Überwachungsschlüssel, nicht an ein Konto gebunden, nur lesend und ohne Fachinhalte. Der Befehl `backup-status` prüft direkt am Sicherungsziel das Alter des letzten Punkts und bemerkt so auch einen ausgefallenen Host. Ab 90 Prozent belegtem Dateispeicher werden neue Uploads mit 507 abgelehnt.
3. Der Befehl `diagnostics` schreibt ein Paket mit Version, Konfiguration ohne Geheimnisse, Zustand und den letzten Fehlerkennungen, ohne Tickettexte, Mails oder Schlüssel.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Die Seite „Betrieb“ zeigt alle Angaben nur mit `operations.read`; ohne das Recht wird abgewiesen.
2. Der Überwachungsendpunkt antwortet nur mit gültigem Schlüssel und ohne Fachinhalte; `backup-status` meldet Warnung und kritisch nach den Stufen aus TASK-131.
3. Uploads ab 90 Prozent werden mit 507 abgelehnt, ohne falschen Erfolg (O12); das Diagnosepaket enthält keine Geheimnisse und Fachinhalte (O16).
4. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für die Seite.

**Nächster Schritt:** Nach dem Ergebnis von TASK-132 auf main die Blockade aufheben und umsetzen.

## TASK-134 Referenzmessung und V1 Freigabeprüfung (A18 Teil 4)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261123-ops-01 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-PERF-001 bis REQ-PERF-004, REQ-OPS-004, REQ-DEV-004, REQ-DEV-005; DEC-038, DEC-040, DEC-047; OPEN-014, OPEN-017; docs/OPERATIONS.md (Lastprofil und Messziele, Prüffälle O14, O15), docs/TAGS_SEARCH.md (T15), F8 der Prüfung von TASK-104. **Abhängigkeiten:** TASK-133 auf main. **Arbeitsstand:** Mit TASK-131 von Holger am 23. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-133 auf main ist. Geplanter Branch claude/project-thread-elfpvq, Entscheidung DEC-164.

**Umfang:** Messung des vollständigen T15 Profils und des Browserlaufs über eine nachgebildete LAN Strecke auf einem Rechner mit 4 vCPU und 16 GB, der Wiederherstellungsnachweis mit vollem Ticketbestand, die Freigaberegel docs/RELEASE.md und der Bericht docs/RELEASE_V1.md. Nicht Teil: das Schließen der im Bericht gefundenen Lücken, der Nachweis mit vollem Dateibestand auf Kundenhardware, eine zusätzliche menschliche technische Prüfung.

**Fachliche Regeln** (von Holger am 23. November 2026 bestätigt):
1. Gemessen wird auf den Standardrechnern von GitHub Actions für öffentliche Repositories (4 vCPU, 16 GB, SSD): das vollständige T15 Profil mit 15 Sitzungen inklusive Portalkonten und Hintergrundlast (Mailabruf, SLA, Sicherung) über 30 Minuten nach 10 Minuten Aufwärmen, dazu der Browserlauf über eine mit `tc` nachgebildete LAN Strecke mit fester Verzögerung und Bandbreite, dokumentiert als Nachbildung. Damit ist F8 der Prüfung von TASK-104 erledigt.
2. Wiederherstellung und Messung laufen mit vollem Ticketbestand (90.000) und verkleinertem Dateibestand; der Bericht rechnet aus dem gemessenen Durchsatz die Zeit für den vollen Dateibestand hoch. Der Nachweis mit vollem Daten und Dateibestand auf echter Hardware bleibt nach DEC-038 Pflicht vor der ersten Kundenzusage und steht als Bedingung in der Freigaberegel.
3. docs/RELEASE.md: V1 ist freigabefähig, wenn alle V1 Anforderungen umgesetzt oder als bewusste Abweichung von Holger bestätigt sind, alle Aufgaben abgenommen in DONE.md stehen, keine offenen Befunde aus Prüfungen ohne Holgers Entscheidung bleiben, CI auf dem Freigabestand grün ist, Wiederherstellung, Update und Messung auf genau diesem Stand bestanden haben, die Schwachstellenprüfung keine offenen kritischen oder hohen Funde zeigt und Holger fachlich abnimmt. Eine zusätzliche menschliche technische Prüfung ist keine Bedingung, steht aber als bekanntes Risiko im Bericht.
4. docs/RELEASE_V1.md führt jede V1 Anforderung mit Stand, Nachweis und Lücke. Der Bericht schließt keine Lücke selbst; Holger entscheidet je Punkt über Nachholen oder Abweichung.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Der Messbericht weist je Klasse p50, p95, p99 und Ziel für das vollständige T15 Profil und den Browserlauf mit nachgebildeter LAN Strecke aus (O14, O15).
2. Der Wiederherstellungsnachweis mit vollem Ticketbestand und hochgerechneter Dateizeit liegt vor.
3. docs/RELEASE.md und docs/RELEASE_V1.md liegen vor; jede V1 Anforderung hat Stand, Nachweis und Lücke.

**Nächster Schritt:** Nach dem Ergebnis von TASK-133 auf main die Blockade aufheben und umsetzen.

## TASK-135 Befunde F1 bis F11 und Hinweise H1 bis H3 der Codex Prüfung von TASK-131 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261123-opsfix-01 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-OPS-004, REQ-OPS-006, REQ-DATA-003, REQ-SEC-001; ACC-008; DEC-161, DEC-165; docs/REVIEW_TASK-131_PR-345.md; docs/OPERATIONS.md (Prüffälle O06 bis O10). **Abhängigkeiten:** TASK-131 auf main (PR #345). **Arbeitsstand:** Holger wählt am 23. November 2026 zur Codex Prüfung von TASK-131 (Empfehlung nachbessern) „Alles beheben als TASK-135“. Geplanter Branch claude/project-thread-elfpvq, Entscheidung DEC-165, Migration V0071 nur falls nötig. Die Umsetzung beginnt nach dem Ergebnis-PR von TASK-132, weil beide den Sicherungsdienst und die Anleitungen ändern. Umgesetzt auf demselben Branch nach dem Merge von TASK-132 (PR #347), mit Migration V0071 (`files_purge_hold`). Übergabe: alle Befunde und Hinweise nach den Regeln behoben; die Probe läuft jetzt gegen ein synthetisches SFTP Ziel und deckte drei weitere Lücken auf, die mit behoben sind (Lesbarkeit hochgeladener Inhalte für den Sicherungsdienst, verwaiste restic Sperre nach einem abgebrochenen Lauf, Wartungsdienst `restore-place` für Schritt 4 der Anleitung, siehe DEC-165). Lokaler Lauf der Probe am 23. November 2026: bestanden, Datenverlust 77 s (einschließlich der drei absichtlich beschädigten neuesten Punkte), Wiederanlauf 128 s; Nachweise in /mnt/project-files/a18/probe-135-2026-11-23/. Grenzen: der KI Lauf mit Vorschlag entsteht in der Probe per SQL, weil kein Anbieter erreichbar ist; die verlangsamte Archivkopie ist nur durch den Aufbau des Archivkommandos abgedeckt, nicht durch einen eigenen Lauf.

**Umfang:** Behebung der Befunde F1 bis F11 und der Hinweise H1 bis H3 aus docs/REVIEW_TASK-131_PR-345.md im Sicherungsdienst `deploy/backup`, im Modul `operations` und seinen Zugängen, in der Probe `tools/restore`, im Workflow „Restore“ und in docs/BACKUP_RESTORE.md. Nicht Teil: Betriebsübersicht (TASK-133), Messung mit vollem Bestand und Freigabe (TASK-134), Hochverfügbarkeit, weitere Sicherungsziele.

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt; die Befunde dieses Berichts heißen hier F1 bis F11 und H1 bis H3):
1. F1: Jeder fehlerkritische Schritt von `backup.sh` wird ausdrücklich ausgewertet, auch im Dauermodus; ein Fehler beendet den Lauf ohne Meldung „complete“. Ein restic Stand gilt erst als Punkt, wenn restic mit 0 endet und die Beschreibung geprüft ist; nur solche Stände bietet `restore.sh points` an. Gegenproben im Dauermodus: Fehler der Basissicherung, SQL Fehler, Uploadfehler, restic Status 3.
2. F2: Die Grenze eines Punkts ist ein benannter Wiederherstellungspunkt (`pg_create_restore_point`); T ist dessen Zeit, die Rücksicherung zielt auf diesen Namen, und vor dem Punkt ist das Segment mit diesem Datensatz nachweislich archiviert. Gegenproben mit echtem PostgreSQL 18: Commit kurz vor und nach der Grenze, eine vorher begonnene und danach bestätigte Transaktion, ein weiterer Punkt ohne neue Schreibvorgänge.
3. F3: Das Archivkommando schreibt unter einem temporären Namen, schließt dauerhaft ab und benennt atomar um; ein liegengebliebener Rest blockiert die Wiederholung nicht. Vor dem Punkt wird die Kette der Segmente von der Basis bis zur Grenze geprüft. Gegenprobe mit abgebrochener Kopie.
4. F4: Die Inhaltsmenge wird zur Grenze festgehalten, und die Bereinigung der Anhänge löscht keinen Inhalt, solange eine Sicherung läuft (gemeinsame Sperre oder Vormerkung in der Datenbank). Gegenprobe mit einer Bereinigung nach T und anschließender Rücksicherung.
5. F5: Ein Punkt gilt bei der Rücksicherung erst als gelungen, wenn eine isolierte PostgreSQL Instanz das Ziel erreicht hat und die genannten Inhalte nach Kennung, Größe und Prüfsumme vorliegen; sonst wird der nächstältere versucht. Dateisystemfehler werden ausdrücklich geprüft. Gegenproben: fehlende mittlere WAL Datei, beschädigte Basis, ersetzte Datei bei gleicher Anzahl, Fehler beim Verschieben.
6. F6: Prozessart und Anwendungstyp sind aneinander gebunden; nur die Befehle ohne Webserver lassen die Startsperre aus, eine unzulässige Kombination wird abgelehnt. Tests für Webstart und Ausführer mit gesetztem Parameter und einen Lesefehler der Vormerkung.
7. F7: `after-restore` führt seine Änderungen, den Audit und das Entfernen der Vormerkung in einer Transaktion unter exklusiver Sperre der Vormerkung aus; ein zweiter gleichzeitiger Aufruf wartet oder wird abgewiesen. Tests mit Fehler zwischen den Schritten, Abbruch und zwei gleichzeitigen Aufrufen.
8. F8: Das Sicherungsimage enthält den ssh Client; die Anleitung beschreibt Schlüssel und `known_hosts` für das Dienstkonto; die Probe sichert und spielt zurück über eine synthetische SFTP Gegenstelle.
9. F9: Die Anleitung nennt Speicherbereiche, Besitzer, Entrypoints, Mounts und Zielverbindungen vollständig; jeder abgedruckte Befehl wird in einer frischen Umgebung genau so ausgeführt.
10. F10: Die Probe erzeugt vor dem Punkt Anhänge und Wikibilder mit bekannten Prüfsummen, Mailantworten in `pending` und `sending` mit Aufträgen, interne und Portalsitzungen und einen KI Vorschlag; nach der Rücksicherung startet ein Ausführer mit synthetischer Mailgegenstelle, und es wird nichts erneut gesendet. Geprüft werden Anmeldung, Projekt, Ticketänderung, Suche und Download sowie die Schreibvorgänge vor und nach T.
11. F11: Der Schreiber wird vor dem Ablesen angehalten, Zeit und Nummer stammen aus derselben letzten Zeile, die Wiederanlaufzeit endet nach den Kernprüfungen, negative Werte und Lücken werden abgewiesen.
12. H1 bis H3: Die Pfadfilter des Workflows nennen alle beteiligten Dateien; Einmal- und Demopasswort laufen nicht über Prozessargumente; die lokale WAL Ablage wird nach der ältesten aufbewahrten Basis sicher bereinigt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F11 und H1 bis H3 sind nach den Regeln behoben, mit den Gegenproben.
2. Backend Tests, Lebenszyklusprüfung und der Workflow „Restore“ mit den neuen Fehlerfällen sind grün.
3. docs/BACKUP_RESTORE.md, TODO.md und DEC-165 entsprechen dem Code.

**Nächster Schritt:** Codex prüft (Prüfauftrag /mnt/project-files/a18/codex-pruefauftrag-task-135.md), Holger nimmt TASK-131 und TASK-135 zusammen ab; danach TASK-133.

## TASK-136 Befunde F1 bis F12 und Hinweise H1 bis H4 der Codex Prüfung von TASK-132 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261123-opsfix-02 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-OPS-004, REQ-OPS-005, REQ-OPS-006, REQ-SEC-001; ACC-008; DEC-162, DEC-166; docs/REVIEW_TASK-132_PR-347.md; docs/OPERATIONS.md (Prüffälle O01 und O11). **Abhängigkeiten:** TASK-132 auf main (PR #347), TASK-135 auf main (PR #348). **Arbeitsstand:** Holger wählt am 23. November 2026 zur Codex Prüfung von TASK-132 (Empfehlung nachbessern) „Alles beheben als TASK-136“. Geplanter Branch claude/project-thread-elfpvq, Entscheidung DEC-166, Migration V0072 nur falls nötig. F4 (Lesbarkeit hochgeladener Inhalte für den Sicherungsdienst) und der SFTP Nachweis aus H4 sind bereits mit TASK-135 (PR #348) umgesetzt und werden hier nur gegengeprüft.

**Umfang:** Behebung der Befunde F1 bis F12 und der Hinweise H1 bis H4 aus docs/REVIEW_TASK-132_PR-347.md in der Schemaprüfung und den Einstiegen des Backends, der Betriebsvorlage `deploy/`, dem Sicherungsdienst, den Workflows „Release“ und „Update“, der Probe `tools/update` und in docs/INSTALL.md, docs/BACKUP_RESTORE.md und docs/OPERATIONS.md. Nicht Teil: Betriebsübersicht (TASK-133), Messung und Freigabe (TASK-134), weitere Proxys, Hochverfügbarkeit, eine tatsächliche Veröffentlichung.

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt; die Befunde dieses Berichts heißen hier F1 bis F12 und H1 bis H4):
1. F1: Nur der vom Einstieg gewählte Containerbefehl `migrate` darf Migrationen ausführen; keine Spring Eigenschaft und kein Schema Modus erteilt Webprozess, Ausführer oder anderen Befehlen diese Freigabe. Die Vorführung behält ihr Startverhalten über einen ausdrücklich eigenen Weg, den die Betriebsvorlage ablehnt. Tests für Webprozess, Ausführer und `bootstrap-admin` mit gesetzter Eigenschaft.
2. F2: Im Betriebsmodus ist die Schemaprüfung eine zwingende Startvoraussetzung unabhängig von der Flyway Autokonfiguration; abgeschaltetes Flyway und Einstellungen, die Satz, Ziel, Historientabelle oder Baseline verändern, werden mit klarer Meldung abgelehnt. Tests mit abweichenden Einstellungen und einer tatsächlich fehlerhaften Historie.
3. F3: Die Vorlage trennt Rollen für Verwaltung (nur Einrichtung), Migration (Eigentümer des Schemas) und Laufzeit (nur Datenrechte an Anwendungsobjekten, keine DDL, keine Rollenverwaltung); Webprozess und Ausführer laufen mit der Laufzeitrolle, `migrate` mit der Migrationsrolle. Die Abfragerechte der Sicherung beschränken sich auf ihren Bedarf. Ein Test zeigt die laufende Identität und eine abgelehnte Schemaänderung mit dem Laufzeitkonto; bestehende Installationen erhalten einen beschriebenen Übergang.
4. F4: mit TASK-135 umgesetzt; gegengeprüft mit einem über die Anwendung geschriebenen Anhang und Wikibild in einem Sicherungspunkt der Update Probe.
5. F5: Ein manueller Punkt wird über den laufenden Sicherungsdienst angefordert oder nur bei angehaltenem Dienst gestartet; die lokale Basis und Punkterstellung ist exklusiv serialisiert. Gegenprobe mit gleichzeitig fälligem Dauerlauf und anschließend verwendbarem Rückkehrpunkt.
6. F6: Das Access Log von Caddy enthält keine Query; Gegenprobe mit synthetischem OAuth Rückruf.
7. F7: Die Veröffentlichung läuft in einer geschützten GitHub Umgebung, die Holgers Freigabe verlangt; Release Tags sind durch eine Repository Regel geschützt. Holger richtet beides in GitHub ein, die Anleitung beschreibt es; der Prüfschritt des Tags wird für erlaubte und abgelehnte Fälle ohne Veröffentlichung nachgewiesen.
8. F8: Pull, Start und Update der Betriebsvorlage lassen nur vollständige Digestreferenzen der erwarteten Registry zu und lehnen Tags ab; CI nutzt einen ausdrücklich getrennten Testweg.
9. F9: Die Update Basis ist die neueste tatsächlich veröffentlichte Version mit ihren veröffentlichten Referenzen, nie der geprüfte Zielstand; vor der ersten Veröffentlichung bleibt der festgelegte Stand. Identität und Herkunft von Basis und Ziel stehen im Nachweis.
10. F10: Die Update Probe vergleicht ein inhaltliches Inventar der gesäten Fachobjekte (unter anderem Beschreibungen, Mitgliedschaften, Wiki, Anforderungen, Dateibytes), weist einen Upload mit Download und Prüfsumme vor und nach dem Containerwechsel nach, prüft die Kernabläufe der Anleitung und nennt die geprüfte Datenmenge.
11. F11: Der Rückkehrweg ist für genau die ausgelieferte Vorlage beschrieben, die Vormerkung der Rücksicherung ist mit den Rechten der Vorlage möglich, und die Probe führt ein synthetisch gescheitertes Update mit Wiederanlauf am vorherigen Punkt aus.
12. F12: Für einen eigenen Proxy gibt es einen vollständigen Betriebsweg ohne Caddy mit auf den Proxy begrenzter Verbindung; das Release Paket enthält die verlinkten Anleitungen oder verweist auf die Quellen des Releases; die nötigen Registries sind genannt.
13. H1 bis H4: Ein ungültiger Modus nennt die zulässigen Werte ohne Rohwert, frühere und aktuelle Migrationsfehler sind in Anleitung und Tests unterschieden; Passwörter der Einrichtung und der Probe laufen nicht über Prozessargumente, und temporäre Dateien liegen im aufgeräumten Verzeichnis; die Pfadfilter des Workflows nennen die beteiligten Start und Seed Dateien, und jeder Lauf hat einen eigenen Compose Projektnamen; der Paketstand des ssh Clients wird über das SBOM erfasst.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F12 und H1 bis H4 sind nach den Regeln behoben, mit den Gegenproben; F7 ist in GitHub eingerichtet oder als offener Schritt für Holger benannt.
2. Backend Tests, Lebenszyklusprüfung und die Workflows „Update“ und „Restore“ sind grün.
3. docs/INSTALL.md, docs/BACKUP_RESTORE.md, TODO.md und DEC-166 entsprechen dem Code.

**Übergabe:** Umgesetzt auf claude/project-thread-elfpvq mit Migration V0072. F1: der Einstieg registriert den Befehl, `MigrateCommandRunner` und `RestoreCommandRunner` hängen an `@ConditionalOnContainerCommand`, keine Eigenschaft gibt das Recht (`MigrateCommandTest.noSettingLetsAWebProcessAWorkerOrAnotherCommandMigrate`). F2 und H1: `SchemaGuard` lehnt für `migrate` und `verify` abgeschaltetes Flyway und jede Flyway Einstellung außer den vier festen ab und verweigert einen Start ohne Prüfung; unbekannter Modus ohne Rohwert, Fehler „in this run“ und „in an earlier run“ getrennt (vier neue Tests). F3: `deploy/db-roles.sql` mit `DatabaseRolesTest` (neue Installation, Übergang einer älteren zweimal, Vorführung ohne Rollen; Laufzeitrolle ohne DDL, Sicherungsrolle nur mit ihrem Anteil), V0072. F4: Punkt der Updateprüfung mit drei Anhängen und Wikibild (4 Inhalte). F5: Sperre im Zustandsvolume für jeden Punkt, Gegenprobe in der Updateprüfung mit laufendem Punkt des Dienstes und Rückkehr auf genau diesen Punkt. F6: Filter in `Caddyfile`, `tools/caddy/check-log.sh` grün und gegen das alte Format rot. F7: `environment: release`, `tools/release/check-tag.sh` mit `tests/test_release_tools.py`; **offen für Holger:** Umgebung `release` und Tag Regelsatz nach docs/INSTALL.md, „Veröffentlichen einer Version“. F8: Dienst `images` mit `check-images.sh`, Testweg `tools/update/compose.test.yaml`, Tag in der Updateprüfung abgelehnt. F9: `tools/update/choose_base.py` mit `tests/test_update_base.py`, Weg über Release Paket und Digests im Workflow; bis zum ersten Release fester Stand. F10: 27 Tabellen und Inhalte des Dateispeichers, Hoch und Herunterladen vor und nach dem Austausch, Kernabläufe über TLS. F11: Abschnitt „Rückkehr“ und Lauf nach synthetisch gescheitertem Update. F12: `compose.proxy.yaml`, Paket nach `tools/release/bundle.py` mit Verweisprüfung, Registries genannt. H2: Passwörter nur über Umgebung, Dateien unter dem Arbeitsverzeichnis. H3: Pfadfilter, Projektname je Lauf, Aufräumfehler protokolliert, Schritt für abgebrochene Läufe. H4: Release nennt die Version von `openssh-client` aus dem SBOM. Nachweise: Repository Prüfungen und 63 Python Tests grün, Backend `./mvnw verify` mit Java 21, Lebenszyklusprüfung, lokaler Lauf von `tools/update/update-check.sh` von V0069 auf V0072 (461 Zeilen in 27 Tabellen und 3 Dateien gleich, Rückweg grün). Grenzen: der Weg über ein veröffentlichtes Release läuft erst mit dem ersten Release; die Probe der Rücksicherung behält für ihre eigenen Prozesse das Verwaltungskonto.

**Nächster Schritt:** Codex Prüfung des Ergebnis PR, danach Abnahme von TASK-132 und TASK-136 durch Holger; Holger richtet die Umgebung `release` und den Tag Regelsatz ein.

## TASK-137 Befunde N1 bis N12 und Hinweise H1 bis H5 der Codex Prüfung von TASK-136 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261123-opsfix-03 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-OPS-004, REQ-OPS-005, REQ-OPS-006, REQ-SEC-001; ACC-008; DEC-162, DEC-166, DEC-167; docs/REVIEW_TASK-136_PR-350.md; docs/OPERATIONS.md (Prüffälle O01 und O11). **Abhängigkeiten:** TASK-136 auf main (PR #350). **Arbeitsstand:** Holger hält am 23. November 2026 zur Codex Prüfung von TASK-136 (Empfehlung nachbessern) die Nachbesserung für beauftragt („Dachte das ist schon in arbeit“) zur vorgeschlagenen Wahl „Alles beheben als TASK-137“. Geplanter Branch claude/project-thread-elfpvq, Entscheidung DEC-167, Migration V0073 nur falls nötig. H2 und H4 des Berichts verlangen keine Änderung.

**Umfang:** Behebung der Befunde N1 bis N12 und der Hinweise H1, H3 und H5 aus docs/REVIEW_TASK-136_PR-350.md in der Schemaprüfung und den Einstiegen des Backends, den Datenbankrollen, der Betriebsvorlage `deploy/`, dem Sicherungsdienst, der Caddy Vorlage, den Workflows „Release“, „Update“ und „Restore“, den Proben unter `tools/` und in docs/INSTALL.md, docs/BACKUP_RESTORE.md und docs/OPERATIONS.md. Nicht Teil: Betriebsübersicht (TASK-133), Messung und Freigabe (TASK-134), weitere Proxys, Hochverfügbarkeit, eine tatsächliche Veröffentlichung.

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt; die Befunde dieses Berichts heißen hier N1 bis N12 und H1 bis H5):
1. N1: Der Schema Modus wird vom Einstieg festgelegt; Betriebs Webprozess, Ausführer und Verwaltungsbefehle prüfen das Schema auch bei überschriebener Einstellung und lehnen eine neuere Historie ab, nur die Vorführung behält ihren eigenen Startweg. Tests mit gesetztem Modus `migrate` aus einer höher priorisierten Quelle.
2. N2: Die Schemaprüfung vergleicht die gebundene Flyway Konfiguration typgerecht, den Klassenpfad mit genauer Schreibweise, und lehnt einen leeren oder unbekannten Migrationssatz ab. Negativtests für Umgebung, Profil, Listenbindung, Pfadschreibweise und ausgeschlossene Flyway Autokonfiguration.
3. N3: Die Laufzeitrolle hat keine Schreibrechte an `flyway_schema_history` und nur die nötigen Rechte an `files_purge_hold` und den Sperrfunktionen; Eigentum an Schema `public` und Erweiterung `pg_trgm` ist für Neuinstallation und Übergang festgelegt und beschrieben. `reset-admin-password`, `rebuild-search` und die Auftragsverarbeitung laufen im Test unter der Laufzeitrolle; der Übergang wird an einem Stand vor V0072 mit gebundenen und freien Folgen, Partition, Typen und Erweiterung geprüft.
4. N4: Punkt- und Basiskennungen sind innerhalb des Zustandsvolumes eindeutig, auch bei zwei Punkten in derselben Sekunde; Anleitungen und Probe nutzen denselben manuellen Aufruf nach der Regel aus TASK-136.
5. N5: Access- und Fehlerlog von Caddy enthalten weder Query noch Referrer, Cookies oder querytragende Antwortheader; die Probe prüft Erfolg, 502, HTTP Weiterleitung und Weiterleitung der Anwendung über stdout und stderr zusammen.
6. N6: Die Anleitung legt fest, dass Tag und Freigabe nur Holger vorbehalten sind, ohne administrativen Bypass; die Prüfung des Tags läuft vor der Freigabe in einem Job mit Leserechten, und der Release Job verwendet genau den geprüften Stand.
7. N7: Jeder unterstützte Betriebsbefehl mit Pull, Start, Update und Rücksicherung, auch mit `--no-deps`, läuft nur nach der gemeinsamen Digestprüfung; Negativtests für Pull, Up und den Weg der Rücksicherung mit lokal vorhandenen Tags.
8. N8: Der feste Stand dient nur, solange kein stabiles Release veröffentlicht ist; sonst ist ein fehlender Vorgänger ein eigener Fehlerzustand. Die Anmeldung an ghcr.io hält bis zum letzten Pull, die Prüfsummenzeilen beider Downloads werden verlangt, der Nachweis nennt Tag, Commit und Digests ohne Runner Pfad.
9. N9: Das Inventar der Update Probe umfasst alle vom Seed angelegten Fachobjekte, mit einer Gegenprobe über einen absichtlichen Verlust; Suche und Ausführer werden über einen erwarteten Treffer und eine Auftragswirkung geprüft, SQL- und Dateifehler brechen ab.
10. N10: Die Probe nimmt einen Punkt vor einem Versionswechsel, lässt die Ziel Migration tatsächlich scheitern und kehrt mit Vorlage und Images der Vorgängerversion zurück; die Anleitung wartet auf die Datenbank, prüft Punkt, Projekt und Volumes vor dem Löschen, behandelt Rückgabewert 3 und den Rechteabgleich vor dem Start, und der Fehlertext des Migrationsbefehls verweist auf den beschriebenen Rückweg.
11. N11: Für einen entfernten Proxy beschreibt die Anleitung die Begrenzung der Quelle über ein eigenes Netz oder die Firewall mit einem Zugriffstest vom Proxy und von einem anderen Host; die Probe der Rücksicherung ist aus dem Paket ausführbar oder verweist auf den Quellstand des Tags, geprüft in einem frischen Verzeichnis.
12. N12: Passwörter und Suchmuster der Proben laufen über Umgebung, stdin oder geschützte Dateien, nie über Prozessargumente; die Reichweite der Imageprüfung ist genannt, und die Einrichtung wird einmal mit einem synthetischen Rollenfehler ohne Passwort im Protokoll geprüft.
13. H1, H3, H5: Ein früherer Fehler einer dem Image unbekannten Migration wird ausdrücklich benannt und im Update Test erwartet; die Abschlussaktion entfernt auch die Netze eines Laufs, und lokale Läufe haben eigene Hostports; Ausschnitt und Liste der getrennt aufzubewahrenden Kennwörter in docs/BACKUP_RESTORE.md entsprechen der Vorlage.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. N1 bis N12 und H1, H3, H5 sind nach den Regeln behoben, mit den Gegenproben; die Einrichtung in GitHub bleibt als offener Schritt für Holger benannt.
2. Backend Tests, Lebenszyklusprüfung und die Workflows „Update“ und „Restore“ sind grün.
3. docs/INSTALL.md, docs/BACKUP_RESTORE.md, TODO.md und DEC-167 entsprechen dem Code.

**Übergabe:** Umgesetzt auf claude/project-thread-elfpvq mit Migration V0073. N1: Die Vorlage startet jeden Prozess über `java -jar app.jar operations` (`OperationsStart`); so gestartet prüft `SchemaGuard` immer und lehnt jeden Modus außer leer und `verify` ab, nur `migrate` migriert (`MigrateCommandTest.aProcessOfTheOperationsTemplateNeverMigratesWhateverTheModeSays`, Profil und Umgebung). N2: typgerechter Vergleich der wirksamen Flyway Konfiguration mit exakter Ablage, leerer Satz abgelehnt (`SchemaGuardSettingsTest`, `aProfileAnExcludedFlywayAndAnotherSpellingAreRefused`). N3: V0073 nimmt der Laufzeitrolle Schreibrechte an `flyway_schema_history` und `files_purge_hold` und die Sperrfunktionen; `db-roles.sql` legt Schema an `taskmanager_migrate` und `pg_trgm` an das Verwaltungskonto, Übergang mit Folgen, Partition, Typen, Sichten und Funktion zweimal geprüft; `reset-admin-password`, `rebuild-search` und ein Ausführer laufen im Test als Laufzeitrolle (`DatabaseRolesTest`). N4: eindeutige Punkt- und Basisnamen (`tests/test_backup_names.py`). N5: Caddy ohne Query, Referrer, Cookies und querytragendes `Location` in Erfolg, Weiterleitung der Anwendung, HTTPS Weiterleitung und 502 (`tools/caddy/check-log.sh` grün, gegen TASK-136 rot). N6: Job `check` mit Leserechten beim Push, Job `release` baut genau den geprüften Commit; Anleitung ohne Bypass, nur Holgers Konto. N7: Vorlage kennt nur `…@${TASK_MANAGER_IMAGE_DIGEST}`; Updateprüfung zeigt mit lokal getaggten Images, dass pull, up --no-deps und run --no-deps von migrate und backup abgelehnt werden. N8: fester Stand nur ohne stabiles Release, sonst Rückgabewert 3; `tools/update/release-files.sh` verlangt je eine Prüfsummenzeile; kein zweiter Pull; Bericht mit Tag, Commit und Digests (`tests/test_update_base.py`, `tests/test_update_release_files.py`). N9: Inventar aller 51 vom Seed gefüllten Tabellen mit benannter Ausschluss- und Pflichtliste, Gegenprobe mit gelöschter Mailvorlage, Suche nach Projekt, Nummer und Titel, aktiver Ausführer mit erfolgreichem wiederkehrendem Auftrag, Abbruch bei Lesefehlern. N10: Punkt vor dem Wechsel, synthetische nächste Version mit bestätigter V0074 und wirklich scheiternder V0075, Rückkehr mit aufbewahrtem Paket und `.env`, Prüfung von Projekt und Volumes, Rückgabewert 3; Anleitung „Rückkehr“ in neun Schritten, `up -d --wait db`, Fehlertext mit Verweis auf den Rückweg. N11: entfernter Proxy über eigenes Netz oder `DOCKER-USER` mit Zugriffstest von zwei Rechnern; `tools/release/check-package.sh` im Workflow und in `tests/test_release_tools.py`; die Restore Probe verweist auf den Quellstand des Tags. N12: Suchmuster aus geschützter Datei, synthetischer Rollenfehler bei `log_statement=all` ohne Passwort in Ausgabe und Datenbanklog, Reichweite der Imageprüfung benannt, restore-check ohne Passwörter in Argumenten. H1: frühere Fehler einer unbekannten Migration benannt (`aFailedMigrationUnknownToTheImageIsNamedAsSuch`); in der Updateprüfung lehnt die vorherige Version die neuere Datenbank mit „newer than this image“ ab, weil PostgreSQL die gescheiterte Migration zurückrollt. H3: Netze und Testimages in der Abschlussaktion, Hostports je Lauf. H5: Ausschnitt und Passwortliste in docs/BACKUP_RESTORE.md. Nachweise: Repository Prüfungen, 77 Python Tests, Backend Tests der geänderten Klassen mit Java 21 und PostgreSQL 16, lokaler Lauf von `tools/update/update-check.sh` von V0069 auf V0073 grün (911 Zeilen in 51 Tabellen und 3 Dateien gleich, Rückkehr auf den Punkt mit Vorgängerpaket). **Offen für Holger:** Umgebung `release` und Tag Regelsatz nach docs/INSTALL.md, „Veröffentlichen einer Version“. Grenzen: das Holen privater Images mit Anmeldung läuft erst ab dem ersten Release mit; bis dahin belegt ein synthetisches Paar die Rückkehr.

**Nächster Schritt:** Codex Prüfung des Ergebnis PR, danach Abnahme von TASK-132, TASK-136 und TASK-137 durch Holger.
