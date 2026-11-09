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
| Blockiert (BLOCKIERT) | 0 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 4 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-092](#task-092-befunde-der-codex-prüfung-von-task-069-beheben-a6) | Befunde der Codex Prüfung von TASK-069 beheben (A6) | claude-20261108-timefix-01 | 2026-11-08 |

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-067](#task-067-neue-kundenantworten-an-bestehenden-tickets-sichtbar-machen) | Neue Kundenantworten an bestehenden Tickets sichtbar machen | claude-20261030-newanswers-01 | 2026-10-30 |
| [TASK-069](#task-069-zeiterfassung-im-projekt-und-ticketzusammenhang-paket-a6) | Zeiterfassung im Projekt und Ticketzusammenhang (Paket A6) | claude-20261030-time-01 | 2026-11-04 |
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

## TASK-092 Befunde der Codex Prüfung von TASK-069 beheben (A6)

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261108-timefix-01 · **Aktualisiert:** 2026-11-08

**Bezug:** REQ-TIME-001, REQ-TIME-002; DEC-099; docs/REVIEW_TASK-069_PR-256.md. **Abhängigkeiten:** TASK-069 geliefert (PR #256). **Arbeitsstand:** Holger hat am 8. November 2026 den Codex Bericht zu TASK-069 (Empfehlung „noch nicht abnehmen“) in den Thread gestellt und am selben Tag auf der Entscheidungskarte „Beheben, Abweichungen bestätigen“ gewählt. Damit sind die drei Abweichungen aus Hinweis H1 bestätigt: 412 bei veralteter Version (409 bleibt der stornierten Buchung), „heute“ als Datum in UTC ohne Unternehmenszeitzone, und die Zeit an einem nicht lesbaren Ticket zählt unter „ohne sichtbares Ticket“. Branch claude/project-thread-thp5tg; Ergebnis mit eigener DEC-ID, Migration nur falls nötig.

**Umfang:** Befunde F1 bis F3 und Hinweise H1 und H2 aus docs/REVIEW_TASK-069_PR-256.md:
1. F1: Anzahl, Gesamtsumme und Summen je Person und je Ticket der Auswertung gelten für alle erlaubten Treffer des Filters, unabhängig von der Listengrenze 500 und der Exportgrenze 50 000; der Export lehnt über 50 000 Zeilen weiter ab.
2. F2: Eine Korrektur entfernt ein Ticket, das die korrigierende Person nicht lesen darf, nie stillschweigend; ein unverändertes verborgenes Ticket bleibt erhalten, ohne dass Nummer oder Titel sichtbar werden.
3. F3: „Meine Zeiten“ zeigt Wochen und Tagessummen aus Serversummen über alle eigenen Buchungen der Woche und weist auf eine gekürzte Liste hin.
4. H1: Die bestätigten Abweichungen werden in Regeltext, DEC, README und Vertrag nachgeführt.
5. H2: Regressionstests für 500, 501, 50 000 und 50 001 Treffer, mehrere Projekte und Stornos, Entzug und Wiedergabe von `ticket.read` bei einer Korrektur, mehr als 500 eigene Buchungen und Negativfälle für Portal und fremde Projekte.

Nicht Teil: Timer, Freigaben, Abrechnung, Unternehmenszeitzone, Suche über Zeitbuchungen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 bis F3 mit API Tests gegen die echte Datenbank und Browsertests in Deutsch, die ohne die jeweilige Behebung fehlschlagen würden.
2. Bestehende Backend und Browser Tests bleiben grün.

**Nächster Schritt:** Reservierung mergen, danach Umsetzung und Ergebnis PR.

## TASK-069 Zeiterfassung im Projekt und Ticketzusammenhang (Paket A6)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261030-time-01 · **Aktualisiert:** 2026-11-04

**Bezug:** REQ-TIME-001, REQ-TIME-002; Abgrenzung REQ-TIME-003; OPEN-021 (Zeitkorrekturen, Exportumfang). **Abhängigkeiten:** Projekte (TASK-026), Ticketkern (TASK-027), Projektkonfiguration (TASK-044), Oberfläche (TASK-030), alle abgenommen. **Arbeitsstand:** Holger hat am 30. Oktober 2026 im Projektgespräch „nach H7 bitte A10 und A6 parallel“ die Umsetzung von Paket A6 beauftragt und am selben Tag die 15 Vorschläge zu den Zeitregeln mit „passt so“ bestätigt. Reservierung mit PR #219 auf main. Ergebnis auf Branch claude/project-thread-thp5tg mit DEC-099 und Migration V0053 (nach V0052 aus TASK-067); Beschreibung in backend/README.md (Abschnitt „Zeiterfassung“), Vertrag contracts/time.openapi.yaml. Abweichungen zur Prüfung: Regel 11 antwortet wie bei Tickets mit 412 statt 409 (409 bleibt der stornierten Buchung), Regel 3 rechnet mangels Unternehmenszeitzone mit dem Datum in UTC.

**Umfang:** Modul Zeiterfassung mit Buchungen, Korrektur und Storno mit Historie, drei Projektaktionen, Auswertung mit CSV Export, API Vertrag, Migration und Oberfläche (Ticketansicht, „Meine Zeiten“, Auswertung) nach den folgenden Regeln.

**Bestätigte Zeitregeln (Holger, 30. Oktober 2026):**
1. Eine Buchung hat Projekt, optional ein Ticket desselben Projekts, Person, Leistungsdatum, Dauer und Tätigkeitsbeschreibung (Pflicht, höchstens 1000 Zeichen). Buchungen nur auf das Projekt sind erlaubt.
2. Dauer in vollen Minuten, 1 bis 1440 je Buchung; Eingabe als „1:15“, „75m“, „1,25 h“ oder „1h 15m“, Anzeige als Stunden und Minuten. Zeitzonen ändern weder Datum noch Dauer.
3. Kein Leistungsdatum in der Zukunft (heutiges Datum der Unternehmenszeitzone plus ein Tag Puffer); rückwirkend ohne Grenze, da V1 keine Periodensperre hat.
4. Auf geschlossene Tickets darf gebucht werden, das Ticket bleibt geschlossen. Die Regel für archivierte Projekte folgt mit der Archivierung.
5. Neue Projektaktionen: `time.book` (eigene Zeit buchen, eigene Buchungen lesen, korrigieren und stornieren), `time.read` (Buchungen aller im Projekt lesen, einschließlich Summen und Export), `time.manage` (für andere buchen, fremde Buchungen korrigieren und stornieren, enthält `time.read`). Ohne `time.read` enthalten Summen nur eigene Buchungen.
6. Ticketleserecht allein zeigt keine Zeiten; externe Kunden sehen keine Zeiten.
7. Mit `time.manage` wird nur für aktive Mitglieder des Projekts gebucht.
8. Eine Korrektur ändert Datum, Dauer, Beschreibung oder Ticket; der alte Wert bleibt mit Bearbeiter, Zeitpunkt und Grund in der Historie der Buchung. Grund optional bei eigenen, Pflicht bei fremden Buchungen.
9. Storno statt Löschen: stornierte Buchungen zählen in keiner Summe, bleiben mit Historie lesbar und lassen sich nicht zurücknehmen.
10. Keine zeitliche Grenze für eigene Korrekturen in V1.
11. Korrekturen sind versioniert; eine veraltete Version ergibt 409.
12. Auswertung: Liste mit Filtern nach Projekt, Person, Ticket und Zeitraum, Summen je Person und je Ticket, CSV Export mit Zeitraum, Filtern und Einheit (Minuten und Stunden mit zwei Nachkommastellen); projektübergreifend nur über lesbare Projekte.
13. Die Ticketansicht zeigt Summe und Liste der für den Leser sichtbaren Buchungen und ein Formular zum Buchen.
14. Seite „Meine Zeiten“ mit Wochenansicht der eigenen Buchungen über alle Projekte.
15. Nicht enthalten: Timer, Freigaben, abrechenbare Zeiten, Stundensätze, Abrechnung, Kontingente, Planung, Verschieben von Tickets, Suche über Zeiten, KI. Buchungen erscheinen in ihrer eigenen Historie, nicht in der Tickethistorie.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Die Regeln 1 bis 14 gelten in API und Oberfläche gleich; Summen entsprechen genau den wirksamen, für den Aufrufer lesbaren Buchungen (Rechenbeispiel aus docs/DOMAIN_MODEL.md: 90 + 30, Korrektur auf 75, Storno von 30 ergibt 120, 105, 75 Minuten).
2. Ohne passende Aktion verrät weder Liste, Summe, Export noch Ticketansicht eine fremde Buchung; ein Ticket, das die Person nicht lesen darf, bleibt unsichtbar.
3. Rechteentzug zwischen Lesen und Speichern verhindert die Änderung (Prüfung beim Commit wie in TASK-043).
4. Migration, Vertrag gegen Routen, Backend Tests für Rechte, Regeln, Korrektur, Storno, Konflikt und Export, Browser Tests für Buchen am Ticket, Korrigieren, Stornieren, „Meine Zeiten“ und Auswertung.

**Übergabe:** Modul `time` mit Migration V0053, drei Projektaktionen, Buchen, Korrigieren und Stornieren mit eigener Historie, Auswertung mit Summen je Person und je Ticket, CSV Export, Bereich „Zeiten“ am Ticket und Seite „Zeiten“ mit „Meine Zeiten“ und Auswertung. Nachweise im Ergebnis PR: TimeApiTest (Rechenbeispiel 120, 105, 75 Minuten, Rechte, Prüfung beim Commit, Konflikt, Export), TimeContractTest, Browsertests time.test.mjs.

**Nächster Schritt:** Getrennte Prüfung (Codex oder Claude) und fachliche Abnahme durch Holger, danach Übertrag nach DONE.md.

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
