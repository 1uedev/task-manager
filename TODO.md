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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 9 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

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
| [TASK-111](#task-111-git-anbieter-kontoverbindung-und-repository-freigabe-a15-teil-1) | Git Anbieter, Kontoverbindung und Repository Freigabe (A15 Teil 1) | claude-20261113-git-01 | 2026-11-13 |
| [TASK-112](#task-112-git-verknüpfung-und-anzeige-an-tickets-und-anforderungen-a15-teil-2) | Git Verknüpfung und Anzeige an Tickets und Anforderungen (A15 Teil 2) | claude-20261113-git-01 | 2026-11-13 |
| [TASK-113](#task-113-git-demodaten-nachweis-gegen-echte-anbieter-und-betriebsdokumentation-a15-teil-3) | Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3) | claude-20261113-git-01 | 2026-11-14 |
| [TASK-114](#task-114-befunde-f1-bis-f9-und-hinweis-h3-der-codex-prüfung-von-task-112-beheben) | Befunde F1 bis F9 und Hinweis H3 der Codex Prüfung von TASK-112 beheben | claude-20261114-gitfix-01 | 2026-11-14 |
| [TASK-115](#task-115-befunde-f1-bis-f6-und-hinweise-h1-bis-h3-der-codex-prüfung-von-task-113-beheben) | Befunde F1 bis F6 und Hinweise H1 bis H3 der Codex Prüfung von TASK-113 beheben | claude-20261114-gitfix-01 | 2026-11-14 |
| [TASK-116](#task-116-befunde-f1-bis-f6-und-hinweise-h1-bis-h3-der-codex-nachprüfung-von-task-114-und-task-115-beheben) | Befunde F1 bis F6 und Hinweise H1 bis H3 der Codex Nachprüfung von TASK-114 und TASK-115 beheben | claude-20261114-gitfix-02 | 2026-11-14 |
| [TASK-117](#task-117-befunde-f1-bis-f4-und-hinweise-h1-und-h2-der-codex-nachprüfung-von-task-116-beheben) | Befunde F1 bis F4 und Hinweise H1 und H2 der Codex Nachprüfung von TASK-116 beheben | claude-20261114-gitfix-03 | 2026-11-15 |
| [TASK-118](#task-118-befunde-f1-bis-f5-und-hinweise-h1-und-h2-der-codex-nachprüfung-von-task-117-beheben) | Befunde F1 bis F5 und Hinweise H1 und H2 der Codex Nachprüfung von TASK-117 beheben | claude-20261115-gitfix-04 | 2026-11-15 |
| [TASK-119](#task-119-befunde-f1-bis-f3-und-hinweis-h1-der-codex-nachprüfung-von-task-118-beheben) | Befunde F1 bis F3 und Hinweis H1 der Codex Nachprüfung von TASK-118 beheben | claude-20261115-gitfix-05 | 2026-11-15 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-111 Git Anbieter, Kontoverbindung und Repository Freigabe (A15 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261113-git-01 · **Aktualisiert:** 2026-11-13

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003, REQ-SEC-001; DEC-036; OPEN-010; docs/GIT_MIGRATION.md (Verbindung und Berechtigungen, Aktualisierung, Prüffälle G05 bis G07). **Abhängigkeiten:** TASK-026, TASK-040, TASK-057 (verschlüsselte Ablage von Zugangsdaten), geliefert. **Arbeitsstand:** Holger hat am 13. November 2026 im Projektgespräch mit „lass uns mit A15 weitermachen“ den Beginn von Paket A15 beauftragt und am selben Tag die 22 Vorschläge mit „passt so“ übernommen (Regeln unten und bei TASK-112 und TASK-113). Geplanter Branch claude/project-thread-6p8449, Entscheidung DEC-141, Migrationen ab V0064.

**Umfang:** Neues Modul `git` mit Anbieterinstanzen, dem Unternehmensrecht `git.manage`, der Verbindung des eigenen Git Kontos über OAuth, der verschlüsselten Ablage und Erneuerung der Token, der Freigabe von Repositories je Projekt, den Regeln für ausgehende Verbindungen, Migration, Vertrag, Oberfläche und Tests gegen nachgebaute Anbieter. Nicht Teil: Verknüpfungen und Anzeige an Tickets und Anforderungen (TASK-112), Demodaten und Nachweis gegen echte Anbieter (TASK-113), persönliche Zugriffstoken, Dienstkonten, Webhooks, Schreibaktionen in Git, GitHub Enterprise Server.

**Fachliche Regeln** (von Holger am 13. November 2026 bestätigt):
1. Paket A15 verknüpft Repositories (GitLab Projekte), Commits und Pull beziehungsweise Merge Requests mit Tickets und Anforderungen und zeigt sie mit aktuellen Angaben an. Nicht dabei: Schreibaktionen in Git, Klonen, Codebrowser, Diffs, Branchlisten, CI Ergebnisse, Releases, Webhooks, automatische Erkennung von Ticketnummern, Statusänderung durch Git, Git in Suche, Export, Portal, Kundenmails und KI, GitHub Enterprise Server.
2. Die Verwaltung legt Anbieterinstanzen an: Art (GitHub.com, GitLab.com, GitLab eigenes), Name, Basisadresse (bei GitHub.com und GitLab.com fest), Kennung und Geheimnis der registrierten Anwendung, optional ein eigenes CA Zertifikat. Neues Unternehmensrecht `git.manage`. Das Geheimnis wird verschlüsselt wie die Postfachpasswörter gespeichert und nie wieder ausgegeben.
3. Jede Person verbindet ihr eigenes Git Konto über OAuth im Browser: bei GitHub über eine GitHub App mit nur lesenden Rechten und deren Benutzertoken (die App muss in den Organisationen installiert sein, deren private Repositories verknüpft werden), bei GitLab über eine OAuth Anwendung mit `read_api`. Keine persönlichen Zugriffstoken in V1. Die Rückleitungsadresse ist die Adresse der Anwendung.
4. Jede Anzeige und Prüfung läuft mit dem Token der betrachtenden Person; kein Dienstkonto und kein Installationstoken als Rückfall. Ohne Verbindung erscheint „Git Konto verbinden“. Gleiche Mailadresse verbindet nichts automatisch.
5. Token und Erneuerungstoken liegen verschlüsselt je Person und Instanz; abgelaufene werden erneuert, sonst gilt „Verbindung erneuern“. Die Person kann trennen; Deaktivieren des Kontos oder Entfernen der Instanz löscht die Token sofort.
6. Wer `project.manage` hat, gibt im Projekt einzelne Repositories einer Instanz frei (Auswahl aus den Repositories der eigenen Verbindung, gespeichert mit der stabilen Kennung des Anbieters). Verknüpft wird nur in freigegebenen Repositories. Umbenennen ändert nichts, ein Umzug auf eine andere Instanz braucht eine neue Freigabe.
7. Abgerufen wird nur bei eingetragenen Instanzen, nie bei einer Adresse aus einer Eingabe. Weiterleitungen auf andere Rechner werden nicht verfolgt, TLS wird immer geprüft (eigene CA möglich), 5 Sekunden je Abruf. Interne Adressen sind erlaubt, die Verwaltung entscheidet mit dem Eintrag.
8. Unterstützt sind GitHub.com und GitLab.com im aktuellen Stand und eigenes GitLab ab Version 16.0 (REST API v4); ältere Versionen werden beim Anlegen abgewiesen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Mit `git.manage` lassen sich Instanzen anlegen, ändern und entfernen; das Geheimnis erscheint in keiner Antwort und keinem Log.
2. Eine Person verbindet ihr Konto über den OAuth Ablauf gegen einen nachgebauten Anbieter, erneuert es nach Ablauf und trennt es; Deaktivieren des Kontos löscht die Token.
3. Eine Projektverwaltung gibt ein Repository frei, das ihre Verbindung sieht; ein nicht sichtbares wird abgewiesen.
4. Eine nicht eingetragene Adresse, eine Weiterleitung auf einen anderen Rechner und ein ungültiges Zertifikat führen zu keinem Abruf beziehungsweise einem Fehler (G07); ein GitLab unter 16.0 wird abgewiesen.
5. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für Verwaltung, Kontoverbindung und Freigabe.

**Übergabe (2026-11-13, claude-20261113-git-01):** Ergebnis auf dem Branch claude/project-thread-6p8449 im Ergebnis PR. Modul `git` mit Migration V0064, Vertrag contracts/git.openapi.yaml (14 Routen), Seite „Git“ mit eigenen Verbindungen und Anbieterverwaltung, Projektreiter „Git“ für Freigaben; Beschreibung in backend/README.md, Abschnitt Git Anbindung. Zu den Kriterien: 1 `GitApiTest.administrationKeepsTheSecretWriteOnlyAndChecksVersions` (Geheimnis in keiner Antwort, keinem Audit und keiner Logausgabe); 2 `connectingUsesPkceAStateOnceAndStoresTokensEncrypted`, `anEndingTokenIsRenewedAndARefusedRenewalAsksToReconnect`, `deactivationAndRemovalEndTheTokensAtOnce`; 3 `releasingNeedsARepositoryTheOwnConnectionMayRead`, `everyReadUsesTheTokenOfTheViewer`; 4 Weiterleitung 502 `git.redirect_refused`, `certificatesAreAlwaysCheckedAndAnOwnCaCanBeTrusted`, `aGitlabOfItsOwnNeedsVersion16`, keine Adresse aus Eingaben (Abrufe nur an die Adresse der Instanz, Repository Adresse gebaut); 5 `GitContractTest`, Browser Tests tests/browser/git.test.mjs. Präzisierungen in DEC-141: Regel 8 prüft die Version bei „Prüfen“, wenn das GitLab sie ohne Token nennt, und immer beim Verbinden; Regel 6 erlaubt auch `projects.manage`. Gelaufen: volle Backend Suite gegen PostgreSQL 16 mit Java 21, `npm run verify`, alle Browser Tests, Repository Prüfungen; der Lifecycle Check mit Docker nur in CI.

**Nächster Schritt:** Unabhängige Prüfung (Codex Prüfauftrag liegt bei), danach Holgers Abnahme; TASK-112 beginnt, sobald dieses Ergebnis auf main ist.

## TASK-112 Git Verknüpfung und Anzeige an Tickets und Anforderungen (A15 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261113-git-01 · **Aktualisiert:** 2026-11-13

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-REQ-003, REQ-ACL-004; DEC-036; OPEN-010; ACC-006; docs/GIT_MIGRATION.md (Git-Umfang, Prüffälle G01 bis G04, G06, G08). **Abhängigkeiten:** TASK-111 auf main; TASK-027, TASK-096, TASK-097, geliefert. **Arbeitsstand:** Mit TASK-111 von Holger am 13. November 2026 beauftragt und mit den Vorschlägen bestätigt; nach dem Ergebnis von TASK-111 auf main (PR #316) am selben Tag begonnen. Branch claude/project-thread-6p8449, Entscheidung DEC-142, Migration V0065.

**Umfang:** Git Verknüpfungen an Tickets und Anforderungen mit den Feldern `git` beider Ressourcentypen, die Live Anzeige mit Aktualisieren, beide Historien, Migration, Vertrag, Oberfläche und Tests. Nicht Teil: Bezug auf Revisionen, automatische Erkennung, Git in Suche, Export, Portal und KI.

**Fachliche Regeln** (von Holger am 13. November 2026 bestätigt):
1. Verknüpft wird über die eingefügte Adresse eines Repositorys, Commits, Pull Requests oder Merge Requests oder über Auswahl von Repository und Nummer beziehungsweise Commit. Der Server erkennt die Instanz, prüft mit dem Token der Person Existenz und Lesbarkeit und speichert nur stabile Kennungen (Instanz, Repository Kennung, Objektart, voller Commit SHA oder Nummer). Dieselbe Verknüpfung entsteht nur einmal.
2. Neues Ticketfeld `git` im Feldrechtemodell und neues Anforderungsfeld `git` nach den Feldregeln des Ressourcentyps `requirement`; bestehende Gruppen erhalten die Regel nicht von selbst. Verknüpfen braucht das Schreibrecht des Felds, eine Verbindung mit Zugriff auf das Objekt und ein freigegebenes Repository; Lösen nur das Schreibrecht.
3. Angaben werden beim Öffnen live mit dem Token der betrachtenden Person geladen, die Fachseite lädt unabhängig. Repository: Name, Pfad, Link. Commit: Kurz SHA, erste Zeile der Nachricht, Autor, Zeitpunkt, Link. Pull/Merge Request: Nummer, Titel, Zustand (offen, Entwurf, zusammengeführt, geschlossen), Autor, Link. „Aktualisieren“ lädt neu.
4. Ohne Verbindung, ohne Zugriff, bei unbekanntem Objekt oder Anbieterfehler erscheint nur „Git Objekt nicht verfügbar“ mit der Objektart, ohne Pfad, Titel, Nachricht, Link oder Nummer und ohne Unterschied zwischen „gibt es nicht“ und „darfst du nicht“. Die Verknüpfung wird nie von selbst gelöscht.
5. Zwischenspeicher nur im Arbeitsspeicher je Person, Instanz und Objekt für 60 Sekunden, nie für eine andere Person; „Aktualisieren“ umgeht ihn, Trennen leert ihn. Angaben aus Git werden nicht in der Datenbank gespeichert.
6. Höchstens 50 Git Verknüpfungen je Ticket und je Anforderung, höchstens 4 gleichzeitige Abrufe je Anfrage, beim Ratenlimit „vorübergehend nicht verfügbar“ ohne Wiederholungsschleife.
7. Anlegen und Lösen erscheinen in Ticket und Anforderungshistorie mit Objektart und interner Kennung, sichtbar mit der Leseregel des Felds `git`; Pfad und Titel nur über die Live Anzeige. Eine Verknüpfung ändert die Version des Tickets oder der Anforderung nicht.
8. Die Verknüpfung einer Anforderung gilt für die Anforderung, nicht für eine Revision. Ein zusammengeführter Merge Request gibt nichts frei und ändert keinen Status.
9. Git Angaben kommen nicht in Suche, CSV Export, Portal, Kundenmails und KI Kontexte; Portalkonten verbinden kein Git Konto.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Ein Commit, ein Pull Request und ein Merge Request lassen sich an Ticket und Anforderung verknüpfen; erneute Eingabe erzeugt kein Duplikat (G01).
2. Eine Person ohne Zugriff beim Anbieter sieht nur „Git Objekt nicht verfügbar“, auch in der Historie (G02); nach Entzug liefert der nächste Abruf nichts mehr (G03).
3. Ein zusammengeführter Merge Request zeigt den neuen Zustand, Ticket und Anforderung bleiben unverändert (G04); Ratenlimit und Ausfall lassen die Seite bedienbar (G06).
4. Suche, Export und Portal liefern keine Git Angaben (G08).
5. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für Ticket und Anforderung.

**Übergabe (2026-11-13, claude-20261113-git-01):** Ergebnis auf dem Branch claude/project-thread-6p8449 im Ergebnis PR. Migration V0065 mit der Tabelle `git_link` (nur Instanz, Repository Kennung, Objektart, voller SHA oder Nummer) und den Ereignissen `git_linked` und `git_unlinked` der Anforderungshistorie; Feld `git` der Ressourcentypen `ticket` und `requirement` im Rechtekatalog; acht Routen in contracts/git.openapi.yaml (jetzt 22), Ergänzungen in tickets, requirements und permissions; Bereich „Git“ am Ticket und an der Anforderung mit Adresse oder Auswahl aus freigegebenen Repositories, „Aktualisieren“ und Lösen; Beschreibung in backend/README.md, Abschnitt Git Anbindung. Zu den Kriterien: 1 `GitApiTest.commitsPullAndMergeRequestsAreLinkedOnceAtTicketAndRequirement` (kurzer und voller SHA ergeben dieselbe Verknüpfung, Version von Ticket und Anforderung unverändert, beide Historien); 2 `withoutAccessOnlyTheKindIsShownAndAWithdrawnAccessEndsAtTheNextRead` (ohne Verbindung und ohne Zugriff nur die Art, Historie nur mit Art und Kennung, nach Entzug liefert „Aktualisieren“ nichts mehr, Lösen ohne Verbindung); 3 `aMergedRequestShowsItsStateAndARateLimitKeepsThePageUsable` (Zustand zusammengeführt, Anforderung bleibt freigegeben, Ratenlimit mit genau einem Abruf je Objekt); 4 `atMostFiftyLinksAndNothingOfGitInSearch` (Grenze 50, höchstens vier gleichzeitige Abrufe, keine Git Angaben in Ticket, Suche und Anforderungssuche; Export und Portal lesen das Feld nicht); 5 `GitContractTest`, `DatabaseFoundationTest`, Browser Tests tests/browser/gitLinks.test.mjs. Präzisierungen in DEC-142: mehrere Instanzen mit derselben Adresse, Gesamtzeit von 20 Sekunden je Anfrage, Gründe der Nichtverfügbarkeit, Zwischenspeicher auch für „nicht verfügbar“. Gelaufen: volle Backend Suite gegen PostgreSQL 16 mit Java 21 (654 Tests), `npm run verify`, alle Browser Tests (307), Repository Prüfungen; der Lifecycle Check mit Docker nur in CI.

**Nächster Schritt:** Die Codex Prüfung (docs/REVIEW_TASK-112_PR-317.md) empfiehlt nachbessern; F1 bis F9 und H3 behebt TASK-114 (DEC-144), danach Holgers Abnahme.

## TASK-113 Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261113-git-01 · **Aktualisiert:** 2026-11-14

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

**Nächster Schritt:** Die Codex Prüfung (docs/REVIEW_TASK-113_PR-318.md) empfiehlt nachbessern; F1 bis F6 und H1 bis H3 behebt TASK-115 (DEC-145). Danach Registrierung der Anwendungen für Abnahmekriterium 2 und Holgers Abnahme von TASK-111 bis TASK-113.

## TASK-114 Befunde F1 bis F9 und Hinweis H3 der Codex Prüfung von TASK-112 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261114-gitfix-01 · **Aktualisiert:** 2026-11-14

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003, REQ-SEC-001; DEC-036, DEC-142, DEC-144; docs/REVIEW_TASK-112_PR-317.md; docs/GIT_MIGRATION.md (Prüffälle G02 und G03). **Abhängigkeiten:** TASK-112 auf main (PR #317); TASK-113 auf main (PR #318). **Arbeitsstand:** Holger wählt am 14. November 2026 zur Codex Prüfung von TASK-112 (Empfehlung nachbessern) „F1 bis F9 und H3 als TASK-114“. Umgesetzt auf claude/project-thread-6p8449 mit Migration V0066 (Historie nur mit Art), Entscheidung DEC-144; Ergebnis PR zusammen mit TASK-115.

**Umfang:** Behebung der Befunde F1 bis F9 und des Hinweises H3 aus docs/REVIEW_TASK-112_PR-317.md mit Vertrag, Oberfläche und Tests. Nicht Teil: Hinweise H1, H2, H4 und H5 (später), weitere Git Funktionen.

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt):
1. F1: Positive Daten aus dem Cache erscheinen nur, wenn der aktuelle Zugriff der Person auf das Repository mit ihrer eigenen Verbindung nachgewiesen ist; ein normaler Abruf von Liste und Auswahl zeigt einen Rechteentzug beim Anbieter sofort.
2. F2: Cacheeinträge sind an eine Generation der Verbindung und an den Stand der Instanz gebunden; eine nachlaufende Antwort einer alten Verbindung wird nicht mehr gespeichert und nicht ausgegeben.
3. F3 und F7: Eine eingefügte Adresse wird je Instanz mit ihrer eigenen Auslegung zur Repository Kennung aufgelöst, dann wird die konkrete Projektfreigabe geprüft und erst danach das Objekt abgefragt; ein nicht freigegebenes Repository verrät nichts über das angefragte Objekt.
4. F4: Die Adresse einer Instanz mit Freigaben oder Verknüpfungen kann nicht mehr geändert werden; für einen neuen Server wird eine neue Instanz angelegt, alte Verknüpfungen bleiben als „nicht verfügbar“ erhalten. Vor dem Speichern wird der für den Abruf genutzte Stand der Instanz mit dem gesperrten verglichen.
5. F5: Alle Git Wege an Anforderungen und Tickets verlangen zuerst das Leserecht am Objekt (`requirement.read` beziehungsweise das Lesen des Tickets) und antworten sonst 404; Schreiben verlangt zusätzlich das Bearbeitungsrecht und die Schreibregel für `git`.
6. F6: Die Auswahl zeigt nur Repositories, die die Person lesen kann. Ein nicht verfügbarer Eintrag zeigt nur die Art und für Schreibberechtigte einen undurchsichtigen Löseschlüssel, ohne Link ID, Instanz, Zeit und Urheber. Die Historie nennt nur die Art ohne Link ID.
7. F8: Ein 403 von GitHub mit `Retry-After` oder dem Merkmal des sekundären Limits gilt als `git.rate_limited` und wird nicht gespeichert.
8. F9: Die Anlage sperrt die konkrete Freigabezeile bis zum Commit; Rücknahme und Anlage sind in beiden Reihenfolgen getestet.
9. H3: Der Testersatz der Browsertests antwortet bei Adressauswahl, Verbindungszustand `renew` und Ratenlimit wie der Server.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F9 sind behoben und je mit einem Regressionstest belegt, G02 und G03 auch am normalen Abruf.
2. Vertrag `contracts/git.openapi.yaml`, Oberfläche und README entsprechen den Regeln 4 bis 6.
3. Der Testersatz entspricht Regel 9; Backend, Frontend und Browsertests sind grün.

**Übergabe:** Backend (`ConnectionService`, `GitCache`, `GitHttp`, `GitStore`, `LinkService`, `ProviderService`, `ReleaseService`, `GitLinkController`), Migration V0066, Vertrag `contracts/git.openapi.yaml` (Pfad `{key}`, `key` statt `id`, Auswahl ohne `available`, Codes `git.provider_in_use` und `git.provider_changed`) sowie die Historientexte in `tickets.openapi.yaml` und `requirements.openapi.yaml`, Oberfläche `GitLinks.tsx`, Testersatz `frontend/tests/browser/backend.mjs`, README Abschnitt „Korrekturen (TASK-114)“. Regressionstests: `GitApiTest` (unter anderem `aNewConnectionNeverSeesWhatTheFormerOneKept`, `theReleaseIsCheckedBeforeTheObjectIsFetched`, `overlappingInstancesKeepTheirOwnReading`, `anInstanceInUseKeepsItsAddress`, `everyGitRouteNeedsTheReadActionFirst`, `theChoiceHoldsOnlyReadableRepositories`, `theSecondaryRateLimitOfGitHubIsARateLimitAndNotKept`, `aWithdrawalAndALinkWaitForEachOther` in beiden Reihenfolgen, G03 am normalen Abruf in `withoutAccessOnlyTheKindIsShownAndAWithdrawnAccessEndsAtTheNextRead`), `GitCacheTest`, `GitHistoryMigrationTest` und der Browsertest „the address, the choice and the removal key follow the server“. Präzisierungen: der Löseschlüssel ist ein HMAC über Person und Verknüpfung mit einem Schlüssel je Serverprozess, also nach einem Neustart neu; jede Verknüpfung zählt in der Liste als ein Eintrag, auch ein Platzhalter; die Auswahl lässt nicht lesbare Repositories ganz weg; F5 gilt auch für Tickets (`ticket.read` vor jedem Git Weg). Grenzen: H1, H2, H4 und H5 bleiben für später.

**Nächster Schritt:** Die Codex Nachprüfung (docs/REVIEW_TASK-114-115_PR-320.md) empfiehlt nachbessern; F1 bis F6 und H1 bis H3 behebt TASK-116 (DEC-146). Danach Holgers Abnahme von TASK-111 bis TASK-116.

## TASK-115 Befunde F1 bis F6 und Hinweise H1 bis H3 der Codex Prüfung von TASK-113 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261114-gitfix-01 · **Aktualisiert:** 2026-11-14

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003, REQ-SEC-001; DEC-143, DEC-145; docs/REVIEW_TASK-113_PR-318.md; docs/GIT_MIGRATION.md, docs/DEMO.md. **Abhängigkeiten:** TASK-113 auf main (PR #318); F6 zusammen mit TASK-114. **Arbeitsstand:** Holger wählt am 14. November 2026 zur Codex Prüfung von TASK-113 (Empfehlung nachbessern) „F1 bis F6 und H1 bis H3 als TASK-115“. Umgesetzt auf claude/project-thread-6p8449 gemeinsam mit TASK-114, Entscheidung DEC-145, keine Migration.

**Umfang:** Behebung der Befunde F1 bis F6 und der Hinweise H1 bis H3 aus docs/REVIEW_TASK-113_PR-318.md in Lauf, Werkzeugen, Demodaten, Workflow und Dokumentation. Nicht Teil: Änderungen am Backend außerhalb von TASK-114, Registrierung bei GitHub.com und GitLab.com (Abnahmekriterium 2 von TASK-113).

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt):
1. F1 und H1: Der GitLab Lauf prüft vor und nach dem Tokenentzug dieselben Link IDs, Arten und Anzahlen an Ticket und Anforderung, das vollständige Rückleitungsziel mit Challenge, exakte Linkanzahlen und unveränderte Ticketversion und Anforderungsrevision beim Zusammenführen.
2. F2: Fehlermeldungen des Laufs nennen nur Schritt, HTTP Status und Fehlercode; Zugangsdaten gehen nicht über die Argumentliste; der Workflow lädt nur Ergebnisdatei und geprüfte Diagnosen hoch.
3. F3: Compose Projekt und Ergebnisordner sind je Lauf eindeutig, das Aufräumen greift ab dem ersten temporären Ordner und ein fehlgeschlagener Abbau wird gemeldet.
4. F4: `git-examples.sql` prüft Anbieterart, Unternehmen, Demoakteur und Freigabegrenze und schreibt die Auditereignisse der Freigaben wie die API.
5. F5: `demo verify` prüft beide Instanzen, WEB-1 und ANF-1 mit Anzahl, Arten, leeren Git Daten und Historie sowie Carla und eine Person ohne Git Feldrecht.
6. F6 und H3: Die Betriebsanleitung trennt Tokenablauf, Widerruf, Verlust des Zugriffs und vorübergehende Fehler, beschreibt den Serverwechsel als neue Instanz und nennt genau die abgedeckten Prüffälle; die Vorführung nennt den Schlüssel vor dem Verbinden und den aktuellen Weg bei GitLab.com.
7. H2: Der Workflow „GitLab 16“ startet auch bei Änderungen am Git Vertrag, an V0064 und V0065 und an der Sitzungslogik.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F6 und H1 bis H3 sind behoben; der GitLab Lauf und der Imagecheck sind grün.
2. Ein Lauf mit erzwungenem Fehler zeigt keine Geheimnisse in Ausgabe und Artefakt.
3. Dokumentation und DEC-143 entsprechen den Regeln 4 und 6.

**Übergabe:** `tools/git/gitlab_check.py` (nummerierte Schritte, Fehler nur mit Schritt, Status und festem Code, vollständige Anmeldeadresse mit Rückleitung, Zustand und Challenge, genau drei Verknüpfungen am Ticket, beide Historien, Version, Revision und Status beim Zusammenführen, Tokenentzug an denselben Löseschlüsseln, Arten und Anzahlen von Ticket und Anforderung, `GITLAB_CHECK_FAIL_AT`), `tools/git/gitlab-check.sh` (Compose Projekt und Ergebnisordner je Lauf, Aufräumen ab dem ersten temporären Ordner, gemeldeter Abbaufehler, Zugangsdaten über die Standardeingabe), neues `tools/git/redact.py` mit `tests/test_gitlab_evidence.py`, Workflow `.github/workflows/gitlab.yml` (nur `gitlab-run.json` und `diagnostics.txt`, Eingabe `fail_at`, weitere Pfade), `tools/demo/git-examples.sql` (Demobestand, Grenze 50, Audit, Historie mit Art), `tools/demo/demo.py` (`demo verify` für beide Instanzen über die Verwaltung, WEB-1 und ANF-1, Historien, Carla, David mit der neuen Gruppe „Anforderungen lesen“), docs/GIT_MIGRATION.md, docs/DEMO.md und DEC-143. Das SQL ist lokal gegen PostgreSQL 16 mit synthetischem Bestand geprüft: Einspielung, zweiter Lauf, 50 vorhandene Freigaben, geänderte Instanz, anderes Projekt und fehlende ANF-1 brechen ohne Änderung ab. Grenzen: Ein Lauf mit erzwungenem Fehler gegen ein echtes GitLab steht aus (Workflow „GitLab 16“ mit `fail_at`); Abnahmekriterium 2 von TASK-113 (Registrierung bei GitHub.com und GitLab.com) bleibt Holgers Schritt.

**Nächster Schritt:** Die Codex Nachprüfung (docs/REVIEW_TASK-114-115_PR-320.md) empfiehlt nachbessern; F1 bis F6 und H1 bis H3 behebt TASK-116 (DEC-146). Danach Holgers Abnahme von TASK-111 bis TASK-116.

## TASK-116 Befunde F1 bis F6 und Hinweise H1 bis H3 der Codex Nachprüfung von TASK-114 und TASK-115 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261114-gitfix-02 · **Aktualisiert:** 2026-11-14

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003, REQ-SEC-001; DEC-144, DEC-145, DEC-146; docs/REVIEW_TASK-114-115_PR-320.md; docs/GIT_MIGRATION.md. **Abhängigkeiten:** TASK-114 und TASK-115 auf main (PR #320). **Arbeitsstand:** Holger wählt am 14. November 2026 zur Codex Nachprüfung von TASK-114 und TASK-115 (Empfehlung nachbessern) „F1 bis F6 und H1 bis H3 als TASK-116“. Umgesetzt auf claude/project-thread-6p8449, Entscheidung DEC-146, ohne Migration (F2 vergleicht die vorhandene Version der Instanz; V0067 bleibt frei).

**Umfang:** Behebung der Befunde F1 bis F6 und der Hinweise H1 bis H3 aus docs/REVIEW_TASK-114-115_PR-320.md im Git Modul, im Browser Testersatz, im GitLab Lauf, im Demo SQL, im Workflow und in der Dokumentation. Nicht Teil: weitere Git Funktionen, Registrierung bei GitHub.com und GitLab.com (Abnahmekriterium 2 von TASK-113).

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt):
1. F1: Die Generation einer Verbindung stammt aus genau der Verbindungszeile, deren Token ein Abruf verwendet. Der Repository Nachweis innerhalb einer Anfrage gilt nur für dieselbe Generation. Vor dem Schreiben in den Zwischenspeicher, vor der Ausgabe und vor dem Wechsel auf „erneuern“ wird geprüft, ob die Generation noch gilt; sonst liefert der Abruf nichts und verändert die neue Verbindung nicht.
2. F2: Freigabe und Verknüpfung vergleichen in der abschließenden Transaktion den Stand der Instanz, mit dem der Anbieter befragt wurde, mit dem gesperrten Stand (Version der Instanz); bei Abweichung 409 `git.provider_changed` ohne Änderung in der Datenbank.
3. F3: Die Betriebsanleitung trennt den sofort wirksamen Entzug des Repositoryzugriffs vom Entzug eines einzelnen Objekts, der spätestens nach Ablauf des Zwischenspeichers (60 Sekunden) wirkt; ein Test zeigt den Wechsel der Objektantwort auf 403 und 404.
4. F4: Der GitLab Lauf gibt nur bekannte Fehlercodes und eng geprüfte Ergebnisfelder aus; die hochgeladenen Diagnosen entstehen aus festen Feldern (Containerzustand, HTTP Status, Schritt) statt aus Rohprotokollen; die Prüfung vor dem Hochladen erkennt auch JSON Felder, Cookies und kodierte Werte.
5. F5: `git-examples.sql` bindet die Beispiele an den Demobestand (Ersteller der Projekte, des Tickets und der Anforderung, Demogruppen und unveränderter Stand der Instanzen) und bricht sonst ohne Änderung ab.
6. F6: Der Lauf legt auch unter einem vorgegebenen Basisordner ein eigenes Unterverzeichnis atomar an; der Workflow lädt nur aus dem Ordner hoch, den der geprüfte Lauf ausdrücklich freigibt.
7. H1 bis H3: Server und Testersatz laufen über dieselben Adressvektoren einschließlich verschachtelter Instanzen, Grenzwerten und Wechsel der Client ID; der Lauf prüft Zielorigin und Fragment der Rückleitung und die Löseschlüssel bis zum Widerruf; der Vertrag nennt die Antwort auf syntaktisch falsche Eingaben, und der Sperrtest ordnet die wartenden Sitzungen über `pg_blocking_pids` zu.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F6 und H1 bis H3 sind behoben, mit Regressionstests für F1 und F2 bei blockierter Anbieterantwort.
2. Backend, Frontend, Browsertests, GitLab Lauf (auch mit erzwungenem Fehler) und Imagecheck sind grün.
3. Dokumentation und DEC-146 entsprechen den Regeln.

**Übergabe:** F1 `ConnectionService` liest Token und Generation aus derselben Zeile, `callWith` prüft sie nach dem Abruf, `GitCache.read` speichert und liefert nur bei gültiger Generation, `renewLater` markiert nur dieselbe Generation; Tests `aRunningReadOfAFormerConnectionShowsAndChangesNothing` und `anAnswerWhoseGenerationEndedDuringTheCallIsNeitherKeptNorHandedOn` mit gehaltener Anbieterantwort. F2 Freigabe und Verknüpfung vergleichen Version der Instanz und Generation in der schreibenden Transaktion (Test `aReleaseOrLinkReadWithAFormerStateOfTheInstanceIsRefused`). F3 Test `anObjectRefusedBelowAReadableRepositoryEndsWithTheCache` (403 und 404), docs/GIT_MIGRATION.md trennt Repository und Objektentzug. F4 und F6 `tools/git/redact.py` mit Diagnosen aus `docker compose ps` und strenger Prüfung von gitlab-run.json, bekannte Codes in `gitlab_check.py`, laufeigener Ordner per `mktemp -d` und Upload nur aus `steps.check.outputs.evidence`; `tests/test_gitlab_evidence.py`. F5 `tools/demo/git-examples.sql` gegen einen echten Demobestand geprüft (gültiger Lauf, sechs veränderte Bestände brechen ab, zwei gleichzeitige Läufe ergeben einen Erfolg). H1 `contracts/cases/git-addresses.json` mit `GitAddressTest` und Browsertest, Testersatz beendet Verbindungen bei neuer Adresse oder Client ID. H2 exakte Rückleitung und Löseschlüssel bis zum Widerruf im GitLab Lauf. H3 Vertrag nennt 400 `request.invalid` und 404 `git.link_not_found` vor der Leseprüfung, Sperrtest über `pg_blocking_pids`. Lokal: Backend, Frontend und alle Browsertests grün; GitLab Lauf und Imagecheck nur in CI.

**Nächster Schritt:** Die Codex Nachprüfung (docs/REVIEW_TASK-116_PR-322.md) empfiehlt nachbessern; F1 bis F4 und H1, H2 behebt TASK-117 (DEC-147). Danach Holgers Abnahme von TASK-111 bis TASK-117.

## TASK-117 Befunde F1 bis F4 und Hinweise H1 und H2 der Codex Nachprüfung von TASK-116 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261114-gitfix-03 · **Aktualisiert:** 2026-11-15

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003, REQ-SEC-001; DEC-144 bis DEC-147; docs/REVIEW_TASK-116_PR-322.md; docs/GIT_MIGRATION.md. **Abhängigkeiten:** TASK-116 auf main (PR #322). **Arbeitsstand:** Holger wählt am 14. November 2026 zur Codex Nachprüfung von TASK-116 (Empfehlung nachbessern) „F1 bis F4, H1, H2 als TASK-117“. Umgesetzt auf claude/project-thread-6p8449, Entscheidung DEC-147, ohne Migration (V0067 bleibt frei).

**Umfang:** Behebung der Befunde F1 bis F4 und der Hinweise H1 und H2 aus docs/REVIEW_TASK-116_PR-322.md im Git Modul, im GitLab Lauf, im Demo SQL und in der Dokumentation. Nicht Teil: weitere Git Funktionen, Registrierung bei GitHub.com und GitLab.com (Abnahmekriterium 2 von TASK-113).

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt):
1. F1: Die Generation enthält auch den Stand des Tokens. Ein Abruf erneuert ein Token nur, wenn die gesperrte Verbindungszeile noch genau den Stand hat, mit dem er begonnen hat; sonst endet er ohne Erneuerung, ohne Zustandsänderung und ohne Ausgabe. Eine Erneuerung durch den Abruf selbst gibt ihren neuen Stand zurück; Zwischenspeicher, Repository Nachweis, Ausgabe und die Markierung „erneuern“ hängen am tatsächlich verwendeten Stand.
2. F2: Verknüpfen und Freigeben sperren die geprüfte Verbindungszeile in der schreibenden Transaktion bis zum Commit und vergleichen ihren Stand; Trennen, neues Verbinden und Erneuern warten darauf. Ist das Trennen zuerst festgeschrieben, antworten beide mit 409 `git.provider_changed` ohne Verknüpfung, Freigabe, Historie oder Audit.
3. F3: Die Prüfung vor dem Upload erlaubt genau die Schritttexte und Fehlercodes des Laufs, Diagnosen nur für die Dienste des Laufs mit ihren Zustandswerten, und sucht Geheimnisse auch im decodierten JSON.
4. F4: `git-examples.sql` prüft auch Mitgliedschaften und Git Feldregeln der Demogruppen sowie die aktuelle Revision der gewählten Anforderung und hält die geprüften Stände bis zum Schreiben stabil; sonst bricht es ohne Änderung ab.
5. H1: Die Betriebsanleitung beschreibt die Felder der Diagnose und den Umfang des erzwungenen Fehlers genau.
6. H2: Ein Test mit gesteuerter Uhr zeigt nach Ablauf der 60 Sekunden beim normalen Abruf die Platzhalter für entzogenen Commit und Änderung bei weiter aktiver Verbindung.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F4 und H1, H2 sind behoben, mit Regressionstests für eine blockierte alte 401 bei gelingender und abgelehnter Erneuerung (Token, Zustand, Zahl der Erneuerungen) und für beide Reihenfolgen von Trennen und Schreiben.
2. Backend, Frontend, Browsertests, GitLab Lauf (auch mit erzwungenem Fehler) und Imagecheck sind grün.
3. Dokumentation und DEC-147 entsprechen den Regeln.

**Übergabe:** F1 `ConnectionService.generation` enthält den Stand des Tokens (Hash der verschlüsselten Form); `renew` vergleicht unter der Zeilensperre den erwarteten Stand, bevor das Erneuerungstoken gelesen oder der Zustand geändert wird, verwendet ein inzwischen erneuertes Token derselben Verbindung ohne Erneuerung und gibt nach eigener Erneuerung die neue Generation zurück; Test `aRunningReadOfAFormerConnectionShowsAndChangesNothing` mit gelingender und abgelehnter Erneuerung (Zahl der Erneuerungen, gespeichertes Token, Zustand). F2 `ConnectionService.holdCurrent` sperrt die geprüfte Verbindungszeile in Verknüpfen und Freigeben bis zum Commit; Test `disconnectingAndWritingALinkOrReleaseWaitForEachOther` mit beiden Reihenfolgen über gehaltene Sperren und `pg_blocking_pids`. Beide neuen Tests scheitern gegen den Code von TASK-116. F3 `tools/git/evidence.json` mit Schritten, Texten, Codes und Diensten, aus der `gitlab_check.py` schreibt und gegen die `redact.py --check` prüft, Geheimnissuche auch in decodierten JSON Zeichenketten; `tests/test_gitlab_evidence.py` mit unbekanntem Code, freiem Detail, Unicode Escape und fremdem Dienst. F4 `tools/demo/git-examples.sql` prüft Mitgliedschaften der Demogruppen, alle Regeln des Felds `git` und den aktuellen Stand von ANF-1 und hält die gelesenen Tabellen bis zum Commit gesperrt; gegen einen echten Demobestand geprüft (gültiger Lauf, sechs veränderte Bestände brechen ohne Änderung ab, zwei gleichzeitige Läufe ergeben einen Erfolg, ein gleichzeitiges Ändern der Mitgliedschaften wartet). H1 docs/GIT_MIGRATION.md beschreibt Felder der Belege und den Umfang des Fehlerlaufs. H2 der Objekttest stellt die Uhr der Anwendung um 61 Sekunden vor.

**Nächster Schritt:** Die Codex Nachprüfung (docs/REVIEW_TASK-117_PR-323.md) empfiehlt nachbessern; F1 bis F5 und H1, H2 behebt TASK-118 (DEC-148). Danach Holgers Abnahme von TASK-111 bis TASK-118.

## TASK-118 Befunde F1 bis F5 und Hinweise H1 und H2 der Codex Nachprüfung von TASK-117 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261115-gitfix-04 · **Aktualisiert:** 2026-11-15

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003, REQ-SEC-001; DEC-144 bis DEC-148; docs/REVIEW_TASK-117_PR-323.md; docs/GIT_MIGRATION.md. **Abhängigkeiten:** TASK-117 auf main (PR #323). **Arbeitsstand:** Holger wählt am 15. November 2026 zur Codex Nachprüfung von TASK-117 (Empfehlung nachbessern) „F1 bis F5, H1, H2 als TASK-118“. Umgesetzt auf claude/project-thread-6p8449, Entscheidung DEC-148, ohne Migration (V0067 bleibt frei).

**Umfang:** Behebung der Befunde F1 bis F5 und der Hinweise H1 und H2 aus docs/REVIEW_TASK-117_PR-323.md im Git Modul, im GitLab Lauf, im Demo SQL und in der Dokumentation. Nicht Teil: weitere Git Funktionen, Registrierung bei GitHub.com und GitLab.com (Abnahmekriterium 2 von TASK-113).

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt):
1. F1: Jeder Eintrag einer Verknüpfungsliste, der Auswahl freigegebener Repositories und der Freigabeliste behält bis zur Ausgabe die Generation, mit der er gelesen wurde. Unmittelbar vor der Ausgabe prüft die Anwendung alle Generationen gemeinsam; ein Eintrag einer nicht mehr gültigen Generation wird zum Platzhalter oder fällt aus der Auswahl.
2. F2: Trifft eine verspätete Ablehnung auf einen neueren Tokenstand derselben Verbindung, endet ein Fehler beim Öffnen dieses Tokens ohne Schreibzugriff; markiert wird nur ein Stand, mit dem der Abruf tatsächlich begonnen hat.
3. F3: Die Prüfung vor dem Upload lehnt doppelte JSON Schlüssel auf jeder Ebene ab und verwendet den einmal streng decodierten Wert für Schema und Geheimnissuche.
4. F4: `git-examples.sql` prüft auch die beim Seed erreichte Version der Beispielanforderung, sodass eine Bearbeitung innerhalb derselben Revision zum Abbruch ohne Änderung führt.
5. F5: Das Demo SQL nimmt zuerst eine eigene Sperre für parallele Läufe und sperrt die übrigen Tabellen in der Reihenfolge der normalen Schreibwege, sodass Demo SQL und Instanzänderung in beiden Startreihenfolgen terminieren.
6. H1: Tests belegen das wartende Trennen während einer aufgehaltenen Freigabe nach `holdCurrent` sowie den erfolgreichen Refresh durch einen zweiten Abruf mit Wiederverwendung des neuen Tokens durch den ersten und genau einem Refresh.
7. H2: Die Betriebsanleitung trennt die Suche nach den sieben bekannten Umgebungswerten von den Belegen des OAuth Ablaufs.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F5 und H1, H2 sind behoben, mit Regressionstests für einen abgeschlossenen und einen gehaltenen Abruf bei Neuverbindung und Erneuerung, für den Öffnungsfehler, für doppelte Schlüssel und für beide Startreihenfolgen von Demo SQL und Instanzänderung.
2. Backend, Frontend, Browsertests, GitLab Lauf (auch mit erzwungenem Fehler) und Imagecheck sind grün.
3. Dokumentation und DEC-148 entsprechen den Regeln.

**Übergabe:** F1 `ConnectionService.keepCurrent` prüft die Verbindungen aller Einträge einer Antwort gemeinsam unmittelbar vor der Ausgabe; `LinkService.list`, `LinkService.choices` und `ReleaseService.list` führen je Eintrag die Generation des Lesens mit, veraltete Einträge werden zum Platzhalter `git.unavailable` oder fallen aus der Auswahl, ein von einem parallelen Abruf erneuertes Token derselben Verbindung behält den Eintrag (Test `aFinishedReadOfAReplacedConnectionIsNotPartOfTheAnswer` für Verknüpfungen, Auswahl und Freigabeliste; scheitert gegen TASK-117). F2 der Zweig für einen neueren Tokenstand endet bei einem Öffnungsfehler ohne Schreibzugriff (Test `aLateRefusalNeverMarksANewerTokenItCannotOpen`; scheitert gegen TASK-117). F3 `redact.py` decodiert einmal streng, lehnt doppelte Schlüssel, NaN und Infinity ab und prüft Form und Geheimnisse an diesem Wert; Negativtests über `--check`. F4 `git-examples.sql` prüft die Version 4 der Beispielanforderung; ein Speichern innerhalb der Revision 2 bricht ohne Änderung ab (gegen eine Kopie des echten Demobestands geprüft). F5 eigene Sperre `pg_advisory_xact_lock` vor allen Tabellensperren, Audit zuletzt; mit zwei Sitzungen geprüft: alte Reihenfolge verklemmt sich mit einer Instanzänderung, neue endet in beiden Startreihenfolgen (Änderung zuerst: Skript bricht an der geänderten Instanz ab; Skript zuerst: Änderung wartet und gelingt), zwei Läufe ergeben einen Erfolg. H1 Tests `aTokenRenewedByAParallelReadIsUsedOnceRenewed` (genau eine Erneuerung, erneuertes Token im wartenden Abruf) und die aufgehaltene Freigabe nach `holdCurrent` mit wartendem Trennen in `disconnectingAndWritingALinkOrReleaseWaitForEachOther`. H2 docs/GIT_MIGRATION.md trennt die Suche nach den sieben Umgebungswerten von den Belegen des OAuth Ablaufs. Lokal: Git Backend Tests (29 API, Cache, Adresse, Vertrag) und Python Tests grün; GitLab Lauf und Imagecheck nur in CI.

**Nächster Schritt:** Die Codex Nachprüfung (docs/REVIEW_TASK-118_PR-325.md) empfiehlt nachbessern; F1 bis F3 und H1 behebt TASK-119 (DEC-149). Danach Holgers Abnahme von TASK-111 bis TASK-119.

## TASK-119 Befunde F1 bis F3 und Hinweis H1 der Codex Nachprüfung von TASK-118 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261115-gitfix-05 · **Aktualisiert:** 2026-11-15

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003, REQ-SEC-001; DEC-144 bis DEC-149; docs/REVIEW_TASK-118_PR-325.md; docs/GIT_MIGRATION.md. **Abhängigkeiten:** TASK-118 auf main (PR #325). **Arbeitsstand:** Holger wählt am 15. November 2026 zur Codex Nachprüfung von TASK-118 (Empfehlung nachbessern) „F1 bis F3, H1 als TASK-119“. Umgesetzt auf claude/project-thread-6p8449, Entscheidung DEC-149, ohne Migration (V0067 bleibt frei).

**Umfang:** Behebung der Befunde F1 bis F3 und des Hinweises H1 aus docs/REVIEW_TASK-118_PR-325.md im Git Modul, im Demo SQL, in der Evidenzprüfung des GitLab Laufs und in der Dokumentation. Nicht Teil: weitere Git Funktionen, Registrierung bei GitHub.com und GitLab.com (Abnahmekriterium 2 von TASK-113).

**Fachliche Regeln** (Vorschlag mit der Wahl bestätigt):
1. F1: Die abschließende Prüfung einer Verknüpfungsliste, der Auswahl freigegebener Repositories und der Freigabeliste liest alle beteiligten Instanzen mit den aktiven Verbindungen der Person in einer einzigen Abfrage, also in einem gemeinsamen Datenbankstand, und prüft dort Mandant, Stand der Instanz und Identität der aktiven Verbindung. Ein von einem parallelen Abruf erneuertes Token derselben weiterhin aktiven Verbindung behält den Eintrag.
2. F2: Das Demo SQL sperrt nach seiner eigenen Sperre zuerst die beiden Demo Instanzzeilen mit FOR UPDATE und erst danach die Tabellen. Instanzänderung, Freigeben und Verknüpfen derselben Instanz warten damit auf das Skript oder das Skript auf sie, in keiner Startreihenfolge entsteht eine Verklemmung.
3. F3: Die Evidenzprüfung lehnt jede nicht endliche Zahl ab, auch eine, die erst durch Überlauf wie `1e999` entsteht.
4. H1: Ein deterministischer Test hält fest, dass ein vollständig gelesener Eintrag die abschließende Prüfung passiert, wenn ein zweiter Eintrag derselben Antwort das Token erneuert, und dass Trennen oder Wechsel auf „erneuern“ an derselben Stelle die Daten entfernen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F3 und H1 sind behoben, mit Regressionstests für einen Verbindungswechsel zwischen den Anbieterabrufen und der abschließenden Prüfung bei zwei Instanzen, für den Überlauf über `--check` und mit Sitzungsproben des Demo SQL gegen Instanzänderung und Freigabe vor und nach dem ersten UPDATE in beiden Startreihenfolgen.
2. Backend, Frontend, Browsertests, GitLab Lauf (auch mit erzwungenem Fehler) und Imagecheck sind grün.
3. Dokumentation und DEC-149 entsprechen den Regeln.

**Übergabe:** F1 `ConnectionService.keepCurrent` liest alle beteiligten Instanzen mit den Verbindungen der Person in einer einzigen Abfrage (`GitStore.providersWithConnection`) und prüft dort Unternehmen, Stand der Instanz und aktive Verbindung; der Tokenstand bleibt bei derselben aktiven Verbindung ausgenommen (Test `theFinalCheckOfTwoInstancesDropsEveryEndedConnection`: Commit von GitHub gelesen, Merge Request von GitLab.com gehalten, beide Instanzen nacheinander getrennt, die Antwort zeigt von keiner Daten). Der Abstand zwischen zwei Abfragen der Fassung von TASK-118 lässt sich über die Schnittstelle nicht gezielt herbeiführen, dieser Test besteht daher auch gegen TASK-118; die einzige Abfrage beseitigt den Abstand. F2 `git-examples.sql` sperrt nach seiner eigenen Sperre zuerst alle Instanzzeilen in der Reihenfolge der Kennungen mit FOR UPDATE, erst danach die Tabellen. Sitzungsproben auf einer Kopie des Demobestands (PostgreSQL 16) mit gehaltener Zeilensperre vor dem ersten UPDATE oder INSERT: Instanzänderung, Freigabe und Verknüpfung jeweils in beiden Startreihenfolgen enden ohne Verklemmung (Instanzänderung zuerst: Skript bricht an der geänderten Instanz ab; Verknüpfung zuerst: Skript bricht an der vorhandenen Verknüpfung ab; Freigabe in APP zuerst: Skript gelingt; Skript zuerst: die anderen warten und gelingen), zwei Läufe ergeben genau einen Erfolg. Die Fassung von TASK-118 verklemmte sich in denselben Proben mit Instanzänderung und Freigabe (`deadlock detected`). F3 `redact.py` lehnt beim Decodieren jede nicht endliche Zahl ab (`parse_float`), auch `1e999` und `-1e999`; `tests/test_gitlab_evidence.py` prüft über `--check` NaN, Infinity, -Infinity, 1e999 und -1e999 (der Überlauf scheitert gegen TASK-118). H1 Test `aFullyReadEntryPassesTheFinalCheckOnlyWhileItsConnectionLasts` wartet, bis der Commit vollständig gelesen und nur der Pull Request gehalten ist, und zeigt an genau dieser Stelle: Erneuerung durch den zweiten Eintrag mit genau einem Refresh lässt beide Einträge sichtbar, Wechsel auf „erneuern“ und Trennen entfernen auch die Daten des fertigen Eintrags. Dokumentation in docs/GIT_MIGRATION.md, docs/DEMO.md und backend/README.md („Korrekturen (TASK-119)“). Lokal geprüft: `GitApiTest` (31), `GitCacheTest`, `GitAddressTest`, `GitContractTest`, `python3 -m unittest discover -s tests`, `validate_repository.py`, `task_board.py --check`, `git diff --check`; Frontend unverändert.

**Nächster Schritt:** Unabhängige Prüfung von TASK-119, danach Holgers Abnahme von TASK-111 bis TASK-119.
