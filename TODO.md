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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 3 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

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
| [TASK-067](#task-067-neue-kundenantworten-an-bestehenden-tickets-sichtbar-machen) | Neue Kundenantworten an bestehenden Tickets sichtbar machen | claude-20261030-newanswers-01 | 2026-10-30 |
| [TASK-088](#task-088-befunde-der-prüfung-von-task-067-beheben) | Befunde der Prüfung von TASK-067 beheben | claude-20261104-newanswersfix-01 | 2026-11-04 |
| [TASK-091](#task-091-befunde-der-nachprüfung-von-task-088-beheben) | Befunde der Nachprüfung von TASK-088 beheben | claude-20261108-newanswersfix-02 | 2026-11-08 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-067 Neue Kundenantworten an bestehenden Tickets sichtbar machen

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261030-newanswers-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-HD-002 (Antworten zuordnen), REQ-TKT-005 (Änderungen). **Abhängigkeiten:** TASK-058 und TASK-062 bis TASK-064, abgenommen (PR #211); Hinweis H7 aus [docs/REVIEW_TASK-058_PR-202.md](docs/REVIEW_TASK-058_PR-202.md). **Arbeitsstand:** Holger hat am 30. Oktober 2026 der Empfehlung zu H7 zugestimmt und im Projektgespräch „nach H7 bitte A10 und A6 parallel“ die Umsetzung beauftragt. Arbeit beginnt nach dem Merge dieser Reservierung. Branch claude/project-thread-fde105; Ergebnis mit eigener DEC-ID und Migration V0052 (geplant als V0029, berichtigt mit TASK-088).

**Ausgangslage:** Hängt der Abruf eine Antwort des Kunden an ein bestehendes Ticket an (`TicketOpener.append`, auch über `POST /api/v1/helpdesk/inbox/{id}/attach`), entsteht nur ein Eintrag der Historie im Feld `mail`. Status und Zuständigkeit bleiben, auch bei einem erledigten Ticket; es gibt keine Benachrichtigung. Die Antwort sieht nur, wer das Ticket öffnet.

**Umfang:** Eine an ein bestehendes Ticket angehängte Mail gilt als „neu“, bis eine Person sie als gesehen markiert. Das Helpdesk zeigt die neuen Kundenantworten der Projekte, in denen die Person `helpdesk.reply` hat, als eigene Liste neben den offenen Antworten; das Ticket zeigt einen Hinweis mit der Möglichkeit, die Antwort als gesehen zu markieren. Nicht Teil: automatisches Wiederöffnen erledigter Tickets (später als Einstellung je Projekt), Benachrichtigungen per Mail oder in der Anwendung (eigenes Paket zu REQ-TKT-005), SLA.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Jede durch den Abruf oder durch `…/attach` an ein bestehendes Ticket angehängte Mail ist neu; Mails, die ein Ticket eröffnen, sind es nicht.
2. `GET` einer Liste der neuen Kundenantworten nennt nur Projekte, in denen die Person Mitglied ist und `helpdesk.reply` sowie die Leseregeln für `mail` und `contact` hat; ohne `ticket.read` keine Spur des Tickets.
3. Als gesehen markieren verlangt dieselben Rechte, ist versioniert (If-Match) und steht mit Person und Zeitpunkt im Audit; danach verschwindet die Mail für alle aus der Liste.
4. Die Ticketansicht zeigt neue Kundenantworten hervorgehoben, nur mit der Leseregel für `mail`.
5. Migration, Vertrag gegen Routen, Backend Tests für Rechte und Zustände, Browser Tests für Liste, Hinweis und Markieren.

**Ergebnis (zur Prüfung):** Umgesetzt nach DEC-097 auf Branch claude/project-thread-fde105, Ergebnis-PR nach der Reservierung PR #218. Migration V0052, ursprünglich V0029, beim Einmischen von main umbenannt (`joined_at`, `seen_at`, `seen_by` an `helpdesk_message`); `TicketOpener.append` vermerkt das Anhängen für Abruf und `…/attach`. Neue Endpunkte `GET /api/v1/helpdesk/answers` und `POST /api/v1/helpdesk/answers/{id}/seen` mit If-Match, Rechten wie beim Versand und Audit `answer.seen`; die Mails im Ticket tragen `newAnswer` und `version`. Oberfläche: Liste „Neue Kundenantworten“ in der Prüfwarteschlange, hervorgehobene Antwort mit „Gesehen“ im Ticket. Nachweise: `HelpdeskApiTest.anAnswerThatJoinsATicketIsNewUntilSomeoneMarksItAsSeen` (Liste, Ticketansicht, 403 für Leser und Mitglied ohne Leseregel, 404 für Fremde, 428, 412, 409, Audit, Anhängen über die Prüfwarteschlange), Vertrag gegen Routen (36, nach dem Einmischen von main; berichtigt mit TASK-088), zwei Browsertests. Beschreibung in backend/README.md, Abschnitt „Neue Kundenantworten (TASK-067)“.

**Nächster Schritt:** Gegenprüfung des Ergebnis-PR und Abnahme durch Holger.

## TASK-088 Befunde der Prüfung von TASK-067 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261104-newanswersfix-01 · **Aktualisiert:** 2026-11-04

**Bezug:** REQ-HD-002, REQ-TKT-005; DEC-097; docs/REVIEW_TASK-067_PR-221.md. **Abhängigkeiten:** TASK-067 geliefert (PR #221). **Arbeitsstand:** Holger hat am 4. November 2026 den Codex Prüfbericht zu TASK-067 (Empfehlung „nachbessern“) in das Projektgespräch gestellt; Umfang nach seiner Wahl auf der Entscheidungskarte vom selben Tag. Branch claude/project-thread-fde105; Ergebnis mit DEC-118, ohne Migration.

**Umfang:** Befunde F1 bis F3 und Hinweise H1 bis H4 aus docs/REVIEW_TASK-067_PR-221.md:
1. F1: Als gesehen markieren prüft nach dem Sperren der Mail das aktuelle Ticket und sein Projekt; passt es nicht oder fehlt das Leserecht dort, antwortet der Server wie bei einer unbekannten Mail mit 404, ohne Änderung und Audit.
2. F2: Die Meldung bei einem Konflikt bleibt im Ticket sichtbar; nach dem Markieren liegt der Fokus in Ticket und Liste auf einem sinnvollen Ziel und der Erfolg wird bestätigt.
3. F3: Ein Fehler beim Abruf der neuen Antworten wird in der Prüfwarteschlange angezeigt und lässt sich wiederholen; leer, ladend und fehlgeschlagen sind unterscheidbar.
4. H1: Der Testersatz der Browsertests bildet Grenze 100, Reihenfolge, Ticketzuordnung und die Reihenfolge der Prüfungen (428 vor 404) wie der Server ab.
5. H2: „Gesehen“ steht im Ticket allen offen, die markieren dürfen, auch ohne aktuellen Kontakt.
6. H3: Der Vertrag nennt 503 mit Retry-After bei beiden Routen; TODO, DEC-097 und README nennen V0052, 36 Routen und die Grenze zu Portalbeiträgen.
7. H4: Regressionen für alten Datenbestand ohne Markierung, gleichzeitiges Markieren mit genau einem Audit, Anhängen gegen Markieren und den Projektwechsel aus F1.

Nicht Teil: Wiederöffnen, Benachrichtigungen, „gesehen“ je Person.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 und H4 mit API Tests gegen die echte Datenbank; F2 und F3 mit Browsertests einschließlich `document.activeElement`; H2 mit einem Browsertest ohne Kontakt.
2. Bestehende Backend und Browser Tests bleiben grün.

**Ergebnis (zur Prüfung):** Umgesetzt nach DEC-118 auf Branch claude/project-thread-fde105, ohne Migration. (1) F1: `AnswerService.seen` prüft nach der Sperre der Mail, dass das Ticket noch im Unternehmen und im Projekt der Mail liegt, einmal vor und einmal unter der geteilten Sperre des Tickets; sonst 404 `helpdesk.message_not_found`. Eine Mail ohne Ticket endet wie bisher mit 412 oder 409. (2) F2: Meldung und Fokus liegen in Ticket und Liste an der Überschrift über den Einträgen, mit Erfolgsmeldung „Die Antwort ist als gesehen markiert.“ und eigenem Konflikttext im Ticket. (3) F3: Ein Ladefehler der Liste zeigt Überschrift, Fehler und „Erneut versuchen“. (4) H2: `answerSeenAllowed` in der Ticketansicht, Vertrag und generierte Typen. (5) H1: Testersatz mit 428 zuerst, Ticket im Projekt der Mail, Grenze 100 vor dem Ticketfilter und Kennung als zweiter Sortierschlüssel. (6) H3: 503 mit Retry-After bei beiden Routen, `helpdesk.answer_not_new` in der Codeliste, V0052 und 36 Routen in TASK-067 und DEC-097, Portalgrenze im README. (7) H4 und F1: `HelpdeskApiTest.anAnswerWhoseTicketLeftTheProjectIsUnknown` (schlägt ohne die Behebung mit 200 statt 404 fehl), `twoPeopleWhoMarkAnAnswerAtOnceLeaveOneMarking`, `anAnswerCanBeMarkedOnlyAfterItJoinedItsTicket`, `anAnswerFromBeforeTheMigrationIsNeverNew`, `anAnswerCanBeMarkedAtATicketWithoutContact`; Browsertests für Fokus und Meldung in Liste und Ticket, Konflikt im Ticket, Ladefehler mit Wiederholen und Ticket ohne Kontakt.

**Nächster Schritt:** Prüfung durch eine zweite KI (Codex oder Claude), danach Abnahme von TASK-067 und TASK-088 durch Holger.

## TASK-091 Befunde der Nachprüfung von TASK-088 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261108-newanswersfix-02 · **Aktualisiert:** 2026-11-08

**Bezug:** REQ-HD-002, REQ-TKT-005; DEC-097, DEC-118; docs/REVIEW_TASK-088_PR-261.md. **Abhängigkeiten:** TASK-088 geliefert (PR #261). **Arbeitsstand:** Holger hat am 8. November 2026 den Codex Bericht der Nachprüfung von TASK-088 (Empfehlung „nachbessern“) in das Projektgespräch gestellt; Umfang nach seiner Wahl auf der Entscheidungskarte vom selben Tag. Branch claude/project-thread-fde105; Ergebnis mit eigener DEC-ID, ohne Migration.

**Umfang:** Befunde R1 und R2 aus docs/REVIEW_TASK-088_PR-261.md:
1. R1: Die Liste „Neue Kundenantworten“ zeigt beim Laden Überschrift und übersetzte Ladeanzeige; ladend, leer und fehlgeschlagen sind sichtbar unterscheidbar. Ein Browsertest hält den Abruf zurück und prüft erst ladend, dann leer und gefüllt.
2. R2: Dauerhafte Regressionstests: eine Antwort, die vor V0052 an ihrem Ticket hängt, bleibt nach der Migration unmarkiert; Anhängen und Markieren in beiden Reihenfolgen unter gehaltener Sperre; der Projektwechsel zwischen erster Prüfung und Ticketsperre; gleichzeitiges Markieren mit 200 und 412, genau einer Versionserhöhung und genau einem passenden Audit, jeweils mit nachgewiesenen Wartezuständen.

Nicht Teil: die wechselnden Zeitüberschreitungen der Tag Browsertests (Hinweis H1, gesondert), Wiederöffnen, Benachrichtigungen, „gesehen“ je Person.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. R1 mit Browsertest in Deutsch; R2 mit API und Migrationstests gegen die echte Datenbank, die ohne die jeweilige Prüfung im Code fehlschlagen würden.
2. Bestehende Backend und Browser Tests bleiben grün.

**Ergebnis (zur Prüfung):** Umgesetzt nach DEC-121 auf Branch claude/project-thread-fde105, ohne Migration. (1) R1: `NewAnswers` in frontend/src/pages/InboxPage.tsx zeigt beim Laden Überschrift und Ladeanzeige mit `aria-busy`; Browsertests halten den Abruf im Testersatz zurück (`state.hold`) und prüfen ladend, dann leer (Abschnitt verschwindet) und gefüllt. (2) R2: `NewAnswersMigrationTest` migriert bis V0051, legt eine eröffnende Mail, eine später angehängte Antwort und eine wartende Mail an und prüft nach dem Upgrade, dass keine neu ist; ein Nachfüllen von `joined_at` in V0052 lässt ihn fehlschlagen. In `HelpdeskApiTest`: `twoPeopleWhoMarkAnAnswerAtOnceLeaveOneMarking` hält die Mail, wartet auf zwei wartende Markierungen und verlangt 200 und 412, eine Versionserhöhung und ein Audit des Gewinners; `attachingAndMarkingThatWaitForTheSameMailEndInEitherOrder` prüft beide Reihenfolgen (Anhängen zuerst: 200 und 412; Markieren zuerst: 409 und 200, danach neu); `aTicketThatLeavesTheProjectWhileTheMarkingWaitsMakesTheAnswerUnknown` hält den Projektwechsel offen, bis das Markieren auf die Ticketsperre wartet, und verlangt 404 ohne Änderung und Audit; ohne die zweite Prüfung unter der Sperre schlägt er mit 200 fehl.

**Prüfung:** Claude Prüfung statt Codex (Sitzung claude-20261108-newanswersfix-review-01) in docs/REVIEW_TASK-091_PR-269.md: R1 und R2 wirksam behoben, drei Gegenproben zu R2 schlagen ohne die Absicherung fehl. Befund B1 [P3]: der Browsertest aus TASK-088 in frontend/tests/browser/helpdesk.test.mjs:547 zählt die Liste, während sie noch lädt, und ist seit TASK-091 unzuverlässig; Hinweise H1 bis H4 ohne Abnahmehindernis. Empfehlung: B1 mit einer Zeile im Test beheben, danach TASK-067, TASK-088 und TASK-091 ohne weitere Nachprüfung abnehmen.

Nach Holgers „B1 noch beheben“ vom 9. November 2026 im selben PR #272 behoben (helpdesk.test.mjs:547 wartet auf das Ausblenden der Liste; Browsertests 210 von 210 grün).

**Nächster Schritt:** Abnahme von TASK-067, TASK-088 und TASK-091 durch Holger.
