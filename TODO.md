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
| Durch eine KI reserviert (IN ARBEIT) | 1 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 5 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 6 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-135](#task-135-befunde-f1-bis-f11-und-hinweise-h1-bis-h3-der-codex-prüfung-von-task-131-beheben) | Befunde F1 bis F11 und Hinweise H1 bis H3 der Codex Prüfung von TASK-131 beheben | claude-20261123-opsfix-01 | 2026-11-23 |

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-126](#task-126-ki-ticketzusammenfassung-und-antwortentwurf-a16-teil-2) | KI Ticketzusammenfassung und Antwortentwurf (A16 Teil 2) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-127](#task-127-ki-anforderungsprüfung-und-wiki-überarbeitung-a16-teil-3) | KI Anforderungsprüfung und Wiki Überarbeitung (A16 Teil 3) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-128](#task-128-ki-prüfsatz-demo-lauf-gegen-echte-anbieter-und-betriebsdokumentation-a16-teil-4) | KI Prüfsatz, Demo, Lauf gegen echte Anbieter und Betriebsdokumentation (A16 Teil 4) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-133](#task-133-betriebszustand-und-überwachung-a18-teil-3) | Betriebszustand und Überwachung (A18 Teil 3) | claude-20261123-ops-01 | 2026-11-23 |
| [TASK-134](#task-134-referenzmessung-und-v1-freigabeprüfung-a18-teil-4) | Referenzmessung und V1 Freigabeprüfung (A18 Teil 4) | claude-20261123-ops-01 | 2026-11-23 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-113](#task-113-git-demodaten-nachweis-gegen-echte-anbieter-und-betriebsdokumentation-a15-teil-3) | Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3) | claude-20261113-git-01 | 2026-11-17 |
| [TASK-125](#task-125-ki-grundlage-mit-anbietern-modellen-skills-grenzen-und-nachweis-a16-teil-1) | KI Grundlage mit Anbietern, Modellen, Skills, Grenzen und Nachweis (A16 Teil 1) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-129](#task-129-befunde-f1-bis-f12-und-hinweise-h1-bis-h4-der-codex-prüfung-von-task-125-beheben) | Befunde F1 bis F12 und Hinweise H1 bis H4 der Codex Prüfung von TASK-125 beheben | claude-20261117-aifix-01 | 2026-11-17 |
| [TASK-130](#task-130-befunde-f1-bis-f7-und-hinweise-h1-und-h2-der-codex-prüfung-von-task-129-beheben) | Befunde F1 bis F7 und Hinweise H1 und H2 der Codex Prüfung von TASK-129 beheben | claude-20261118-aifix-02 | 2026-11-18 |
| [TASK-131](#task-131-sicherung-und-wiederherstellung-mit-praktischem-nachweis-a18-teil-1) | Sicherung und Wiederherstellung mit praktischem Nachweis (A18 Teil 1) | claude-20261123-ops-01 | 2026-11-23 |
| [TASK-132](#task-132-installation-version-und-update-für-den-betrieb-a18-teil-2) | Installation, Version und Update für den Betrieb (A18 Teil 2) | claude-20261123-ops-01 | 2026-11-23 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-113 Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261113-git-01 · **Aktualisiert:** 2026-11-17

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003; DEC-036; OPEN-010; ACC-006; docs/GIT_MIGRATION.md, docs/DEMO.md, README.md. **Abhängigkeiten:** TASK-112 auf main (PR #317); TASK-031. **Arbeitsstand:** Mit TASK-111 von Holger am 13. November 2026 beauftragt und mit den Vorschlägen bestätigt; nach dem Ergebnis von TASK-112 auf main (PR #317) am 14. November 2026 begonnen. Branch claude/project-thread-6p8449, Entscheidung DEC-143, keine Migration.

**Umfang:** Lauf gegen ein echtes GitLab 16 im Container, Anleitung zur Registrierung einer GitHub App und einer GitLab.com Anwendung, Demodaten im Vorführimage, Betriebsdokumentation und Fortschreibung von OPEN-010. Nicht Teil: dauerhafte Zugangsdaten im Repository, GitHub Enterprise Server.

**Fachliche Regeln** (von Holger am 13. November 2026 bestätigt):
1. Automatische Tests laufen gegen nachgebaute GitHub und GitLab Server ohne Abrufe ins Internet; dazu ein eigener Lauf gegen ein echtes GitLab 16 im Container für Version und eigene CA.
2. Für die Abnahme wird eine GitHub App und eine GitLab.com Anwendung für das Vorführimage eingerichtet; die Sitzung liefert dafür eine Schritt für Schritt Anleitung. Ohne Registrierung zeigt die Demo Verwaltung und „Git Konto verbinden“.
3. Das Vorführimage enthält vorbereitete Instanzen für GitHub.com und GitLab.com ohne Geheimnis und Beispielverknüpfungen auf öffentliche Repositories, die ohne Verbindung als „nicht verfügbar“ erscheinen.
4. README und docs/GIT_MIGRATION.md beschreiben Registrierung, nötige Rechte, Rückleitungsadresse, eigene CA, Proxy und das Verhalten bei Tokenentzug; OPEN-010 wird mit dem Ergebnis als entschieden geführt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Der Lauf gegen GitLab 16 im Container verknüpft und zeigt Repository, Commit und Merge Request, auch mit eigener CA.
2. Mit registrierten Anwendungen gelingt Verbinden, Verknüpfen und Anzeigen gegen GitHub.com und GitLab.com (ACC-006 Teil Git).
3. Das Vorführimage startet mit den Demodaten; ohne Verbindung erscheinen die Beispiele als „nicht verfügbar“.
4. Anleitung und Betriebsdokumentation liegen vor; OPEN-010 ist fortgeschrieben.

**Übergabe (2026-11-14, claude-20261113-git-01):** Ergebnis auf dem Branch claude/project-thread-6p8449 im Ergebnis PR, ohne Änderung am Backend und ohne Migration. Zu den Kriterien: 1 `tools/git/gitlab-check.sh` mit `tools/git/compose.gitlab.yaml`, `tools/git/gitlab-setup.rb` und `tools/git/gitlab_check.py`, Workflow `.github/workflows/gitlab.yml` („GitLab 16“): GitLab CE 16.11.10 mit HTTPS und eigener CA, Prüfen ohne CA `git.tls_failed` und mit CA erfolgreich, Verbinden mit Anmeldung bei GitLab und PKCE, Freigabe aus einer Untergruppe, Repository, Commit über kurzen SHA und Merge Request am Ticket, Merge Request über die Freigabe an einer Anforderung, Duplikat und fremder Rechner abgewiesen, „merged“ nach dem Zusammenführen, neuer Pfad nach dem Umbenennen (G05), nach dem Löschen der Anwendung nur `git.connection_renew` ohne Angaben; lokal mit Docker Compose bestanden, Ergebnis in gitlab-run.json. 2 Offen für Holger: GitHub App und GitLab.com Anwendung nach docs/GIT_MIGRATION.md, Abschnitt Einrichtung und Betrieb, registrieren und Kennung und Geheimnis in den Demoinstanzen eintragen. 3 `demo seed --profile demo` legt die Instanzen GitHub.com und GitLab.com ohne Geheimnis an und gibt den Demogruppen das Feld `git`; `tools/demo/git-examples.sql` speichert Freigaben und Beispielverknüpfungen an WEB-1 und ANF-1 mit Historie; `demo verify` prüft im Imagecheck Instanzen, `git.provider_incomplete`, die Beispiele als nicht verfügbar und nichts für carla; compose.yaml setzt `TASK_MANAGER_PUBLIC_URL`. 4 docs/GIT_MIGRATION.md (Einrichtung und Betrieb: Voraussetzungen, Rückleitungsadresse, GitHub App, GitLab.com, eigenes GitLab mit CA, Proxy, Tokenentzug, Vorführung, GitLab Lauf), README.md, docs/DEMO.md, backend/README.md, .env.example; OPEN-010 in DECISIONS.md und docs/ARCHITECTURE_READINESS.md entschieden. Präzisierungen in DEC-143. Gelaufen: GitLab Lauf und Imagecheck mit Docker Compose gegen ein lokal aus demselben Stand gebautes Image (`GITLAB_CHECK_SKIP_BUILD=1`, `IMAGE_CHECK_SKIP_BUILD=1`, weil der Docker Build hier keinen Zugang zu den Paketquellen hat), Repository Prüfungen; der Docker Build selbst läuft nur in CI.

**Nächster Schritt:** Holger registriert die GitHub App und die GitLab.com Anwendung nach docs/GIT_MIGRATION.md, Abschnitt Einrichtung und Betrieb, und trägt Kennung und Geheimnis in den Demoinstanzen ein; danach Nachweis von Abnahmekriterium 2 und Holgers Abnahme von TASK-113. Die Codex Prüfung (docs/REVIEW_TASK-113_PR-318.md) ist mit TASK-115 und den Folgeaufgaben bis TASK-124 behoben; TASK-111, TASK-112 und TASK-114 bis TASK-124 hat Holger am 17. November 2026 abgenommen (DONE.md), TASK-113 bleibt bis zu diesem Nachweis offen.

## TASK-125 KI Grundlage mit Anbietern, Modellen, Skills, Grenzen und Nachweis (A16 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261117-ai-01 · **Aktualisiert:** 2026-11-17

**Bezug:** REQ-AI-002, REQ-AI-003, REQ-AI-006, REQ-AI-007, REQ-AI-008, REQ-PERF-004, REQ-SEC-001; DEC-034, DEC-035; OPEN-003, OPEN-019; docs/AI_USE_CASES.md (Administrative Skills, Offene Anbieterarchitektur, Datenfreigabe, Kosten und Ausfall). **Abhängigkeiten:** TASK-040, TASK-054 (wiederkehrende Aufträge), TASK-057 (verschlüsselte Ablage von Zugangsdaten), geliefert. **Arbeitsstand:** Holger hat am 17. November 2026 im Projektgespräch mit „Mache weiter“ den Beginn von Paket A16 beauftragt und am selben Tag die 22 Vorschläge mit „Passt so“ übernommen (/mnt/project-files/a16/fragen-a16.md; Regeln unten und bei TASK-126 bis TASK-128). Geplanter Branch claude/project-thread-av1na6, Entscheidung DEC-156, Migrationen ab V0067.

**Umfang:** Neues Modul `ai` mit Anbieterzugängen und Modellen, Adaptern für OpenAI, Anthropic und Ollama über HTTP, Skills mit Versionen und eingebauten Skills, der Zuordnung je Anwendungsfall, dem Unternehmensrecht `ai.manage`, der Projektaktion `ai.use`, Grenzen für Laufzeit, Gleichzeitigkeit und Tageslimits, der befristeten Ablage von Vorschlägen mit Löschauftrag, dem Nachweis ohne Inhalt, Migration, Vertrag, Verwaltungsoberfläche und Tests gegen nachgebaute Anbieter. Nicht Teil: die vier Funktionen selbst (TASK-126, TASK-127), Prüfsatz, Demo und echter Nachweis (TASK-128), Portal, Zuordnung je Projekt, Ersatzmodelle, Geldbudget.

**Fachliche Regeln** (von Holger am 17. November 2026 bestätigt):
1. Paket A16 liefert die vier bestätigten Funktionen, jede nur auf ausdrücklichen Klick, nie automatisch beim Öffnen. Nicht dabei: Portal und externe Personen, weitere KI Funktionen, Übersetzung, Chat oder freie Fragen, Suche mit KI, Vektordatenbank, Anhänge, Bilder, Git Inhalte und Links als Kontext, automatische Folgeaktionen, automatischer Anbieterwechsel, eingebauter Modellserver im Image.
2. V1 liefert alle drei Adapter: OpenAI, Anthropic und Ollama, jeweils direkt über die HTTP Schnittstelle ohne Hersteller SDK. Bei OpenAI ist die Adresse änderbar (Standard api.openai.com) für OpenAI kompatible Server, Anthropic hat eine feste Adresse, Ollama eine frei eingetragene.
3. Neues Unternehmensrecht `ai.manage`. Die Verwaltung legt Anbieterzugänge an (Art, Name, Adresse, API Schlüssel verschlüsselt wie die Postfachpasswörter und nie wieder ausgegeben, optional eigene CA) und darunter Modelle (Modellkennung, Kontextgrenze, maximale Ausgabe) und trägt je Zugang den Datenweg ein: „lokal im eigenen Netz“ oder „externer Dienst“. „Verbindung prüfen“ schickt ausdrücklich eine kleine Testanfrage; beim Speichern wird nichts aufgerufen.
4. Je Anwendungsfall genau ein Modell und ein aktiver Skill für das ganze Unternehmen. Fehlt die Zuordnung oder ist sie deaktiviert, ist die Funktion mit Erklärung ausgegraut. Kein Ersatzmodell und kein automatischer Wechsel bei Fehlern.
5. Ausgehende Verbindungen nur zu eingetragenen Adressen, keine Weiterleitungen, TLS immer geprüft (eigene CA möglich), Proxy über die Java Einstellungen; interne Adressen für Ollama erlaubt.
6. Skills sind Markdown Anweisungen je Anwendungsfall und Sprache (Deutsch, Englisch) mit Name, Status (Entwurf, aktiv, archiviert) und Version. Eine aktive Version ist unveränderlich; Bearbeiten erzeugt einen neuen Entwurf, der ausdrücklich aktiviert wird. Je Fall und Sprache gibt es einen eingebauten Skill, den die Verwaltung ersetzen und wiederherstellen kann.
7. Im Skill Editor kann die Verwaltung einen Entwurf mit eingegebenem Beispieltext ausdrücklich gegen das zugeordnete Modell testen; das zählt gegen die Grenzen und steht im Nachweis.
8. Rechte, Kontext, Ausgabeformat und erlaubte Aktionen legt die Anwendung fest, nicht der Skill. Der Skill wird in eine feste Rahmenanweisung eingebettet; Inhalte aus Tickets, Mails und Dokumenten werden als markierte Daten übergeben und nie als Anweisung behandelt.
9. Neue Projektaktion `ai.use`, ohne die keine KI Funktion im Projekt startet; bestehende Gruppen erhalten sie nicht von selbst. Dazu kommen die fachlichen Rechte des jeweiligen Falls. Portalkonten werden immer abgewiesen, auch über die API. Der Kontext wird mit denselben Lesewegen wie die Oberfläche zusammengestellt; ein KI Recht erweitert kein Fachrecht. Höchstens 60.000 Zeichen Kontext je Anfrage; ist er länger, werden die neuesten Einträge genommen und die Ansicht nennt die Kürzung.
10. Höchstens 90 Sekunden je Aufruf, höchstens zwei gleichzeitige Aufrufe je Installation und bis zu vier wartende; mehr ergibt „KI gerade ausgelastet“. Die Person kann abbrechen. Keine automatische Wiederholung. Ein KI Ausfall stört keine andere Funktion.
11. Die Installation nutzt eigene Schlüssel des Betreibers, der die Kosten trägt. Einstellbare Tageslimits statt Geldbudget: Standard 50 Aufrufe je Person und 500 je Installation, dazu die Ausgabegrenze je Modell. Gemeldete Token werden im Nachweis gezählt und in der Verwaltung je Tag und Fall angezeigt.
12. Prompts und Antworten kommen nicht ins Protokoll. Ein Vorschlag wird höchstens 24 Stunden nur für die erzeugende Person gespeichert und danach durch einen wiederkehrenden Auftrag gelöscht. Ändert sich eine Quelle oder ein Recht, ist er ungültig und wird nicht mehr angezeigt.
13. Jede Ausführung schreibt einen Nachweis ohne Inhalt: Person, Fall, Objekt und Version, Skill Version, Anbieter, Modell, Datenweg, Dauer, Token, Ergebnis (erfolgreich, abgelehnt, Fehler mit Art), lesbar mit `ai.manage`, aufbewahrt wie die übrigen Audits.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Mit `ai.manage` lassen sich Zugänge, Modelle, Skills und Zuordnungen anlegen, ändern und deaktivieren; der Schlüssel erscheint in keiner Antwort und keinem Log; ohne das Recht wird alles abgewiesen (A07).
2. Alle drei Adapter liefern gegen nachgebaute Anbieter ein Ergebnis und unterscheiden Erreichbarkeit, Anmeldung, Ratenlimit, ungeeignetes Modell, zu großen Kontext, ungültige Ausgabe, Abbruch und Zeitüberschreitung; kein Wechsel zu einem anderen Anbieter (A12).
3. Grenzen für Laufzeit, Gleichzeitigkeit und Tageslimits greifen; ein deaktivierter Zugang oder Skill stoppt die weitere Auslieferung (A13, A14).
4. Nachweis ohne Inhalt, Löschung der Vorschläge nach 24 Stunden, Portalkonten abgewiesen (A06).
5. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für die Verwaltung.

**Übergabe:** Ergebnis im Ergebnis PR auf dem Branch claude/project-thread-av1na6 mit DEC-156. Modul `taskmanager.ai` mit Migration V0067, Vertrag contracts/ai.openapi.yaml (26 Routen, `AiContractTest`), Seite „KI“ für `ai.manage`, Port `taskmanager.ai.AiRuns` für TASK-126 und TASK-127. Nachweise: `AiApiTest` gegen einen nachgebauten Anbieter, der wie OpenAI, Anthropic und Ollama antwortet (Kriterium 1 bis 4), volle Backend Suite mit PostgreSQL 16, `npm run verify` und alle Browsertests einschließlich `ai`. Abweichungen zur Prüfung: „Verbindung prüfen“ liest die Modellliste und erzeugt keinen Text (keine Kosten); die Plätze für laufende und wartende Ausführungen gelten je Prozess; ein Abbruch durch Neustart endet nach höchstens 14 Minuten als `ai.interrupted`; `ai.use` lässt sich mangels Anwendungsfall bisher nur über einen Testcontroller im Backend Test prüfen. Beschreibung und Grenzen in backend/README.md, Abschnitt „KI Grundlage“.

**Nächster Schritt:** Die Codex Prüfung (docs/REVIEW_TASK-125_PR-339.md) empfiehlt nachbessern; F1 bis F12 und H1 bis H4 behebt TASK-129 (DEC-158). Danach Holgers Abnahme von TASK-125 und TASK-129 und der Beginn von TASK-126.

## TASK-129 Befunde F1 bis F12 und Hinweise H1 bis H4 der Codex Prüfung von TASK-125 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261117-aifix-01 · **Aktualisiert:** 2026-11-17

**Bezug:** REQ-AI-002, REQ-AI-003, REQ-AI-006, REQ-AI-007, REQ-AI-008, REQ-SEC-001; DEC-156, DEC-158; docs/REVIEW_TASK-125_PR-339.md; docs/AI_USE_CASES.md (Prüffälle A06, A07, A12 bis A14). **Abhängigkeiten:** TASK-125 auf main (PR #339). **Arbeitsstand:** Holger wählt am 17. November 2026 zur Codex Prüfung von TASK-125 (Empfehlung nachbessern) „Alles beheben als TASK-129“. Geplanter Branch claude/project-thread-av1na6, Entscheidung DEC-158, Migration V0068.

**Umfang:** Behebung der Befunde F1 bis F12 und der Hinweise H1 bis H4 aus docs/REVIEW_TASK-125_PR-339.md im Modul `ai`, seinem Vertrag, der Seite „KI“, dem Testersatz der Browsertests und der Dokumentation, mit Migration V0068. Nicht Teil: die vier Anwendungsfälle (TASK-126, TASK-127), Prüfsatz, Demo und Lauf gegen echte Anbieter (TASK-128), weitere Anbieter oder Funktionen.

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt):
1. F1: Ein gespeicherter Schlüssel ist an die kanonische Adresse und die CA seines Zugangs gebunden. Ändert sich eines davon, wird der bisherige Schlüssel verworfen, es sei denn, im selben Schreibvorgang wird ein neuer eingegeben; ein Schlüssel geht nie an ein anderes Ziel als das, für das er gespeichert wurde. Gegenprobe mit zwei nachgebauten Anbietern: Der neue Empfänger erhält das alte Geheimnis nie.
2. F2: Ein Schlüssel besteht aus 1 bis 500 sichtbaren ASCII Zeichen und wird sonst vor dem Speichern mit 400 abgewiesen. Fehler beim Aufbau einer Anfrage werden ohne ihren Text gemeldet und protokolliert. Eine erfolgreiche Antwort, die den verwendeten Schlüssel wörtlich enthält, wird verworfen und als ungültige Ausgabe nachgewiesen. Tests prüfen API Antwort, Log und Vorschlag mit einem synthetischen Geheimnis.
3. F3: Adressen werden vor Vergleich und Speicherung kanonisiert (Schema und Host klein und in ASCII, ohne Punkt am Ende, ohne Standardport, ohne Anmeldedaten, Abfrage und Fragment). Die Hosts von OpenAI und Anthropic gelten immer als externer Datenweg. Unverschlüsseltes http ist nur für Hosts erlaubt, die der Betreiber in der Einstellung `TASK_MANAGER_AI_PLAIN_HTTP_HOSTS` ausdrücklich freigibt, und nur mit dem Datenweg „lokal“; die allgemeine http Freigabe gibt es nur in Tests. Freigabe und Beschriftung des Datenwegs werden gemeinsam getestet.
4. F4: Eine Ausführung hält beim Start die Versionen von Zuordnung, Zugang, Modell und angewandtem Skill fest. Vor dem Versand, beim Speichern des Ergebnisses und beim Lesen wird verglichen; bei einer Abweichung endet eine wartende oder laufende Ausführung mit `ai.configuration_changed`, eine laufende Anfrage wird abgebrochen, und ein gespeicherter Vorschlag gilt als ungültig. Auch Aus und wieder Einschalten einer Zuordnung erhöht ihre Version. Gegenproben für dieselbe Modell ID mit neuer Version, einen Skillwechsel und Aus und Einschalten.
5. F5: Kontodeaktivierung und Speichern eines Vorschlags sind für dieselbe Person geordnet: Die Deaktivierung beendet offene Ausführungen der Person, und ein später eintreffendes Ergebnis wird für ein inaktives Konto nicht gespeichert. Gegenprobe mit gehaltener Anbieterantwort und anschließender Reaktivierung.
6. F6: Eine gemeinsame Frist gilt für Verbindung, Antwortkopf und den ganzen begrenzten Antwortkörper. Bei Ablauf werden Anfrage und Lesen des Körpers abgebrochen, das Ergebnis ist `ai.timeout`, und der Platz wird frei. Gegenprobe mit sofortigem Kopf und offenem Körper.
7. F7: Platz und Ausführung werden gemeinsam verwaltet. Ein Abbruch vor dem Start entfernt die Ausführung aus der Warteschlange und gibt den Platz genau einmal frei; eine laufende Ausführung gibt ihren Platz erst nach dem tatsächlichen Ende frei. Gegenprobe: wiederholter Abbruch wartender Ausführungen, danach lassen sich wieder alle Plätze belegen.
8. F8: Eine fachliche Ausführung liefert Inhalt nur, wenn für ihren Anwendungsfall ein zuständiger Prüfer registriert ist; ohne ihn wird kein Inhalt ausgeliefert. Probeläufe ohne Fachkontext werden getrennt behandelt. Der Testcontroller erhält einen veränderlichen Prüfwert; Quellenänderung und Entzug eines Fachrechts werden getrennt geprüft.
9. F9: Die Ausgabe ist genau ein JSON Objekt ohne doppelte Schlüssel, nur von Leerraum oder einem einzelnen Codeblock umgeben, mit den Pflichtfeldern und zulässigen Werten des Anwendungsfalls, festgelegt in Code und Vertrag. Eine unbekannte Quelle bleibt ein eigener Fehler.
10. F10: Die erste Zuordnung eines Anwendungsfalls wird vor dem Lesen der Version serialisiert. Gegenprobe mit zwei Transaktionen auf denselben Ausgangsstand: genau eine erfolgreich, die andere 412.
11. F11: Ein Skillentwurf hat eine eindeutige, nie wiederverwendete Revision, auch nach einem Verwerfen; ETag und Nachweis des Probelaufs stützen sich darauf. Gegenprobe mit fester Uhr: zwei Änderungen in derselben Millisekunde und Aktivieren über ein altes Kennzeichen ergibt 412.
12. F12: Der Start einer berechtigten Person, der fachlich abgelehnt wird (keine oder ausgeschaltete Zuordnung, Tagesgrenze, Auslastung), wird ohne Inhalt als `refused` mit Grund nachgewiesen, auch nach dem Rollback, und zählt nicht gegen die Tagesgrenze.
13. H1: Übergabe und README beschreiben das Ende unterbrochener Ausführungen genau: fällig nach 14 Minuten, beendet beim nächsten Lauf von `ai.cleanup` im 15 Minuten Takt, also nach bis zu etwa 29 Minuten und später bei stehendem Worker. Die Plätze gelten je Prozess, und Regel 10 von TASK-125 wird in DEC-158 entsprechend eindeutig formuliert.
14. H2: Die Protokollseite mit `limit=200` liefert bei mehr Einträgen ihren Folgelink; Vertragstest mit genau 201 Einträgen.
15. H3: Der Testersatz antwortet wie der Server (kein Feld `sample`, `content: null` mit Grund bei ungültigem Vorschlag, laufender Probelauf); die Seite „KI“ zeigt einen ungültig gewordenen Vorschlag und den Abbruch eines laufenden Probelaufs, mit Browsertests.
16. H4: Ein lokaler HTTPS Test unterscheidet richtige CA, fehlende CA und falschen Hostnamen; ein echtes Portalkonto wird gegen Status, Lauf und Verwaltung mit 403 geprüft.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F12 und H1 bis H4 sind nach den Regeln behoben, mit den Gegenproben.
2. Migration V0068, Vertrag, Backend Tests, `npm run verify` und alle Browsertests sind grün.
3. Dokumentation und DEC-158 entsprechen den Regeln.

**Übergabe:** Umgesetzt auf claude/project-thread-av1na6 mit Migration V0068 (Schlüsselziel, Entwurfsrevision, Versionsstempel, abgelehnte Starts). F1 bis F12 und H1 bis H4 sind nach den Regeln 1 bis 16 behoben; die Gegenproben stehen in `AiApiTest` (unter anderem zwei nachgebaute Anbieter für die Schlüsselbindung, gehaltene Antwort mit Deaktivierung und Reaktivierung, Körper mit offenem Ende, wiederholter Abbruch wartender Ausführungen, gehaltene Beratungssperre der ersten Zuordnung, lokaler HTTPS Server mit eigener CA, echtes Portalkonto), `AiInternalsTest` (Kanonisierung, Schlüsselformat, Anfragefehler ohne Text) und `AiRecordsPageTest` (201 Einträge, Folgelink bei `limit=200`) sowie in den Browsertests `ai` (laufender Probelauf abbrechen, ungültiger Vorschlag, verworfener Schlüssel nach Adresswechsel, abgelehnter Start im Protokoll). Vertrag, backend/README.md (Abschnitt KI Grundlage mit H1) und DEC-158 beschreiben das Verhalten. Abweichung zur Prüfung: H1 bleibt bei Plätzen je Prozess, wie in DEC-158 festgehalten; über mehrere Webserver gilt die Zahl je Prozess.

**Nächster Schritt:** Prüfung des Ergebnis PR (Codex), danach Holgers Abnahme von TASK-125 und TASK-129 und der Beginn von TASK-126.

## TASK-130 Befunde F1 bis F7 und Hinweise H1 und H2 der Codex Prüfung von TASK-129 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261118-aifix-02 · **Aktualisiert:** 2026-11-18

**Bezug:** REQ-AI-002, REQ-AI-003, REQ-AI-006, REQ-AI-007, REQ-AI-008, REQ-SEC-001; DEC-156, DEC-158, DEC-160; docs/REVIEW_TASK-129_PR-341.md; docs/AI_USE_CASES.md (Prüffälle A06, A07, A12 bis A14). **Abhängigkeiten:** TASK-129 auf main (PR #341). **Arbeitsstand:** Holger wählt am 17. November 2026 zur Codex Prüfung von TASK-129 (Empfehlung nachbessern) „Alles beheben als TASK-130“. Geplanter Branch claude/project-thread-av1na6, Entscheidung DEC-160, Migration V0069.

**Umfang:** Behebung der Befunde F1 bis F7 und der Hinweise H1 und H2 aus docs/REVIEW_TASK-129_PR-341.md im Modul `ai`, seinem Vertrag, der Seite „KI“, dem Testersatz der Browsertests und der Dokumentation. Nicht Teil: die vier Anwendungsfälle (TASK-126, TASK-127), Prüfsatz, Demo und Lauf gegen echte Anbieter (TASK-128), weitere Anbieter oder Funktionen.

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt; die Befunde dieses Berichts heißen hier F1 bis F7 und H1, H2):
1. F1: Jeder bestehende Zugang wird vor jedem Aufruf nach den heutigen Adress- und Datenwegregeln geprüft (kanonische Adresse, unverschlüsseltes http nur für freigegebene Hosts mit dem Datenweg „lokal“, die Hosts von OpenAI und Anthropic nie „lokal“). Ein Zugang, der heute nicht mehr angelegt werden dürfte, gilt als nicht verfügbar; sein Schlüssel wird weder geöffnet noch versandt, auch nicht für die Modellliste und Prüfungen. Gegenprobe mit einer vor V0068 angelegten HTTP Zeile bei leerer Betreiberfreigabe und mit einem als „lokal“ gespeicherten Standardziel.
2. F2: Vor dem Speichern werden auch die dekodierten und bereinigten Textwerte der endgültigen Ausgabe auf den verwendeten Schlüssel geprüft; ein Treffer ergibt `ai.invalid_output` ohne Antwortinhalt in Fehler und Nachweis. Gegenproben mit JSON Escapes und einer erst durch die Bereinigung zusammengesetzten Wiederholung.
3. F3: Die Auswahl des geltenden Skills eines Anwendungsfalls und einer Sprache trägt eine monotone Revision, die in den Stempel eingeht und beim Aktivieren, Archivieren und Wiederherstellen nie zurückkehrt (Migration V0069). Dieselbe alte Lauf ID ist vor der Aktivierung gültig, nach der Aktivierung und nach der Wiederherstellung des eingebauten Skills ungültig.
4. F4: Ein bereits bekannter Abbruch oder Interrupt wird vor dem Adapteraufruf und vor dem Start des HTTP Aufrufs beachtet; Benutzer und Konfigurationsabbruch nutzen denselben kontrollierten Übergang. Gegenprobe mit gehaltener Schlüsselphase: danach hat kein Anbieteraufruf begonnen.
5. F5: Nach dem Entfernen genau eines äußeren Codeblocks wird dessen Inhalt nur mit dem strikten JSON Parser geprüft; Backticks innerhalb von JSON Zeichenketten bleiben erhalten, weitere äußere Blöcke scheitern weiter. Beide Fälle gemeinsam getestet.
6. F6: Der Status `discarded` einer Skillversion ist in Vertrag, erzeugten Typen, Texten (Deutsch und Englisch), Seite und Testersatz durchgängig festgelegt; der Ablauf Speichern, Verwerfen, Versionsliste ist mit einer Serverantwort dieses Zustands im Browsertest geprüft.
7. F7: Der Testguard erhält einen getrennten, veränderlichen Fachrechtewert; der Entzug des Fachrechts bei gültiger Quelle und bestehendem `ai.use` macht einen vorhandenen Vorschlag ohne Inhalt, getrennt vom Quellenfall.
8. H1: IPv6 Literale werden einheitlich dargestellt (Adresse und Freigabeeintrag in derselben Form, ohne DNS Auflösung); `https://[::1]` und `https://[0:0:0:0:0:0:0:1]` sind dieselbe kanonische Adresse.
9. H2: Ein Fachmodulaufruf aus einer bereits laufenden Transaktion, die nach der Ablehnung zurückgerollt wird, lässt den inhaltsfreien Ablehnungsnachweis bestehen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F7, H1 und H2 sind nach den Regeln behoben, mit den Gegenproben.
2. Migration V0069, Vertrag, Backend Tests, `npm run verify` und alle Browsertests sind grün.
3. Dokumentation und DEC-160 entsprechen den Regeln.

**Übergabe:** Umgesetzt auf claude/project-thread-av1na6 mit Migration V0069 (`ai_skill_selection`). F1 bis F7, H1 und H2 sind nach den Regeln 1 bis 9 behoben. Gegenproben: `AiApiTest` (Zugang mit heute unerlaubter Adresse vor jedem Aufruf, Schlüssel nur in dekodierten Werten, Rückkehr zum mitgelieferten Skill macht alte Vorschläge ungültig, Abbruch vor dem Aufruf bei gehaltener Auswahlsperre, Ablehnung unter zurückgerollter äußerer Transaktion, gleiche IPv6 Adressen, getrennter Fachrechtewert des Prüfers), `AiInternalsTest` (IPv6 Form und Freigabeeinträge, Schlüssel in jedem dekodierten Wert, Backticks im Codeblock, ein bereits unterbrochener Faden sendet keine Anfrage an einen zählenden Server) und der Browsertest `ai` (Verwerfen und Versionsliste, Adressgrund, je Deutsch und Englisch). Abweichung zu Regel 6: Der Server liefert verworfene Entwurfsstände nie in der Versionsliste; statt sie als Zustand zu zeigen, sind Vertrag und Typ jetzt so beschrieben, der Text `ai.skillState.discarded` steht vorsorglich in beiden Sprachen, und der Testersatz listet den Entwurf wie der Server mit dem Zustand `draft`. Hinweis zu Regel 4: Der Test mit der gehaltenen Auswahlsperre belegt den Ablauf, aber nicht allein die Prüfung im Ausführer (der Treiber bricht auch ohne sie ab); die Prüfung vor dem HTTP Aufruf belegt der Unit Test, der ohne sie fehlschlägt. Bestehende Läufe mit einem Stempel aus der Zeit vor V0069 gelten als geändert. Ausgeführt (lokal, Java 21, PostgreSQL 16): alle 730 Backend Tests, `lifecycle-check.sh` mit 66 angewandten Migrationen bis V0069, `npm run verify`, alle 319 Browsertests, `validate_repository.py`, `task_board.py --check`, `unittest`, `git diff --check`.

**Nächster Schritt:** Prüfung des Ergebnis PR (Codex), danach Holgers Abnahme von TASK-125, TASK-129 und TASK-130 und der Beginn von TASK-126.

## TASK-126 KI Ticketzusammenfassung und Antwortentwurf (A16 Teil 2)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261117-ai-01 · **Aktualisiert:** 2026-11-17

**Bezug:** REQ-AI-001, REQ-AI-004, REQ-AI-005, REQ-AI-006, REQ-HD-001; DEC-034, DEC-035; ACC-010; docs/AI_USE_CASES.md (AI-UC-01, AI-UC-02, Prüffälle A01 bis A03, A08 bis A11). **Abhängigkeiten:** TASK-125 auf main; TASK-058, TASK-076, geliefert. **Arbeitsstand:** Mit TASK-125 von Holger am 17. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-125 auf main ist. Geplanter Branch claude/project-thread-av1na6, Entscheidung DEC-157.

**Umfang:** Die Funktionen Ticketzusammenfassung und Antwortentwurf mit Kontext, Validierung, Vorschau, Vertrag, Oberfläche und Tests. Nicht Teil: Speichern der Zusammenfassung am Ticket, Wikiseiten als Quelle, automatischer Versand.

**Fachliche Regeln** (von Holger am 17. November 2026 bestätigt):
1. Ticketzusammenfassung: Kontext sind Titel, Beschreibung, Typ, Status, Priorität, lesbare eigene Felder, Kundenmails, gesendete Antworten und das Portalgespräch, soweit lesbar. Ergebnis: Sachstand, offene Punkte und Quellen als Verweise auf die Einträge. Nur Vorschau mit „Kopieren“; am Ticket wird nichts gespeichert. Voraussetzung: `ai.use` und Leserecht am Ticket.
2. Antwortentwurf: „Entwurf mit KI“ im Dialog „Antwort an Kunden“ und bei der Portalantwort. Kontext ist nur, was die Kundschaft schon sehen darf: Titel, Kundenmails, gesendete Antworten, Portalgespräch, dazu ein kurzes Ziel der Person. Keine internen Felder, keine Wikiseiten. Der Entwurf landet bearbeitbar im Textfeld; Versand nur über das bestehende Prüfen und Bestätigen. Sprache wie die des Kontakts. Voraussetzung: `ai.use` und `helpdesk.reply` beziehungsweise `portal.answer`.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Die Zusammenfassung trennt belegten Stand von offenen Punkten und verweist nur auf übergebene Einträge; unbekannte Quellen werden abgelehnt (A01, A03, A15).
2. Der Kontext des Antwortentwurfs enthält keine internen Felder und keine für die Person verbotenen Felder (A02); Anweisungen in einer Kundenmail ändern nichts (A08).
3. Ein Entwurf wird nur nach Prüfen und Bestätigen versendet; geänderter Text braucht eine neue Prüfung, doppelte Bestätigung erzeugt höchstens einen Versand (A10, A11).
4. Vertrag gegen Routen, Backend Tests, Browser Tests für Ticket, Antwortdialog und Portalantwort.

**Nächster Schritt:** Nach dem Ergebnis von TASK-125 auf main die Blockade aufheben und umsetzen.

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

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261123-opsfix-01 · **Aktualisiert:** 2026-11-23

**Bezug:** REQ-OPS-004, REQ-OPS-006, REQ-DATA-003, REQ-SEC-001; ACC-008; DEC-161, DEC-165; docs/REVIEW_TASK-131_PR-345.md; docs/OPERATIONS.md (Prüffälle O06 bis O10). **Abhängigkeiten:** TASK-131 auf main (PR #345). **Arbeitsstand:** Holger wählt am 23. November 2026 zur Codex Prüfung von TASK-131 (Empfehlung nachbessern) „Alles beheben als TASK-135“. Geplanter Branch claude/project-thread-elfpvq, Entscheidung DEC-165, Migration V0071 nur falls nötig. Die Umsetzung beginnt nach dem Ergebnis-PR von TASK-132, weil beide den Sicherungsdienst und die Anleitungen ändern.

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

**Nächster Schritt:** Nach dem Merge der Reservierung und dem Ergebnis-PR von TASK-132 umsetzen.
