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
| Blockiert (BLOCKIERT) | 1 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 2 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

Derzeit keine Aufgaben in dieser Kategorie.

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-113](#task-113-git-demodaten-nachweis-gegen-echte-anbieter-und-betriebsdokumentation-a15-teil-3) | Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3) | claude-20261113-git-01 | 2026-11-13 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-111](#task-111-git-anbieter-kontoverbindung-und-repository-freigabe-a15-teil-1) | Git Anbieter, Kontoverbindung und Repository Freigabe (A15 Teil 1) | claude-20261113-git-01 | 2026-11-13 |
| [TASK-112](#task-112-git-verknüpfung-und-anzeige-an-tickets-und-anforderungen-a15-teil-2) | Git Verknüpfung und Anzeige an Tickets und Anforderungen (A15 Teil 2) | claude-20261113-git-01 | 2026-11-13 |

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

**Nächster Schritt:** Unabhängige Prüfung (Codex Prüfauftrag liegt bei), danach Holgers Abnahme; TASK-113 beginnt, sobald dieses Ergebnis auf main ist.

## TASK-113 Git Demodaten, Nachweis gegen echte Anbieter und Betriebsdokumentation (A15 Teil 3)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261113-git-01 · **Aktualisiert:** 2026-11-13

**Bezug:** REQ-GIT-001, REQ-GIT-002, REQ-GIT-003; DEC-036; OPEN-010; ACC-006; docs/GIT_MIGRATION.md, docs/DEMO.md, README.md. **Abhängigkeiten:** TASK-112 auf main; TASK-031. **Arbeitsstand:** Mit TASK-111 von Holger am 13. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-112 auf main ist. Geplanter Branch claude/project-thread-6p8449, Entscheidung DEC-143.

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

**Nächster Schritt:** Nach dem Ergebnis von TASK-112 auf main die Blockade aufheben und umsetzen.
