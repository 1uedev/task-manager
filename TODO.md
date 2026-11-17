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
| Blockiert (BLOCKIERT) | 0 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 1 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

Derzeit keine Aufgaben in dieser Kategorie.

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-113](#task-113-git-demodaten-nachweis-gegen-echte-anbieter-und-betriebsdokumentation-a15-teil-3) | Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3) | claude-20261113-git-01 | 2026-11-17 |

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
