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
| Blockiert (BLOCKIERT) | 3 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 3 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

Derzeit keine Aufgaben in dieser Kategorie.

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-126](#task-126-ki-ticketzusammenfassung-und-antwortentwurf-a16-teil-2) | KI Ticketzusammenfassung und Antwortentwurf (A16 Teil 2) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-127](#task-127-ki-anforderungsprüfung-und-wiki-überarbeitung-a16-teil-3) | KI Anforderungsprüfung und Wiki Überarbeitung (A16 Teil 3) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-128](#task-128-ki-prüfsatz-demo-lauf-gegen-echte-anbieter-und-betriebsdokumentation-a16-teil-4) | KI Prüfsatz, Demo, Lauf gegen echte Anbieter und Betriebsdokumentation (A16 Teil 4) | claude-20261117-ai-01 | 2026-11-17 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-113](#task-113-git-demodaten-nachweis-gegen-echte-anbieter-und-betriebsdokumentation-a15-teil-3) | Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3) | claude-20261113-git-01 | 2026-11-17 |
| [TASK-125](#task-125-ki-grundlage-mit-anbietern-modellen-skills-grenzen-und-nachweis-a16-teil-1) | KI Grundlage mit Anbietern, Modellen, Skills, Grenzen und Nachweis (A16 Teil 1) | claude-20261117-ai-01 | 2026-11-17 |
| [TASK-129](#task-129-befunde-f1-bis-f12-und-hinweise-h1-bis-h4-der-codex-prüfung-von-task-125-beheben) | Befunde F1 bis F12 und Hinweise H1 bis H4 der Codex Prüfung von TASK-125 beheben | claude-20261117-aifix-01 | 2026-11-17 |

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
