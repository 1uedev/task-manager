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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 7 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

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
| [TASK-069](#task-069-zeiterfassung-im-projekt-und-ticketzusammenhang-paket-a6) | Zeiterfassung im Projekt und Ticketzusammenhang (Paket A6) | claude-20261030-time-01 | 2026-11-04 |
| [TASK-072](#task-072-tag-katalogpflege-und-zusammenführen-a3-teil-1) | Tag Katalogpflege und Zusammenführen (A3 Teil 1) | claude-20261101-tags-01 | 2026-11-01 |
| [TASK-073](#task-073-tag-massenänderung-und-filter-a3-teil-2) | Tag Massenänderung und Filter (A3 Teil 2) | claude-20261101-tags-01 | 2026-11-01 |
| [TASK-081](#task-081-auftritt-für-anmeldung-und-portal-a17-teil-1) | Auftritt für Anmeldung und Portal (A17 Teil 1) | claude-20261102-branding-01 | 2026-11-02 |
| [TASK-086](#task-086-befunde-beider-prüfungen-von-task-072-und-task-073-beheben-a3) | Befunde beider Prüfungen von TASK-072 und TASK-073 beheben (A3) | claude-20261101-tags-01 | 2026-11-04 |
| [TASK-087](#task-087-befunde-der-prüfung-von-task-081-beheben-a17) | Befunde der Prüfung von TASK-081 beheben (A17) | claude-20261102-branding-01 | 2026-11-04 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-067 Neue Kundenantworten an bestehenden Tickets sichtbar machen

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261030-newanswers-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-HD-002 (Antworten zuordnen), REQ-TKT-005 (Änderungen). **Abhängigkeiten:** TASK-058 und TASK-062 bis TASK-064, abgenommen (PR #211); Hinweis H7 aus [docs/REVIEW_TASK-058_PR-202.md](docs/REVIEW_TASK-058_PR-202.md). **Arbeitsstand:** Holger hat am 30. Oktober 2026 der Empfehlung zu H7 zugestimmt und im Projektgespräch „nach H7 bitte A10 und A6 parallel“ die Umsetzung beauftragt. Arbeit beginnt nach dem Merge dieser Reservierung. Branch claude/project-thread-fde105; Ergebnis mit eigener DEC-ID und voraussichtlich Migration V0029.

**Ausgangslage:** Hängt der Abruf eine Antwort des Kunden an ein bestehendes Ticket an (`TicketOpener.append`, auch über `POST /api/v1/helpdesk/inbox/{id}/attach`), entsteht nur ein Eintrag der Historie im Feld `mail`. Status und Zuständigkeit bleiben, auch bei einem erledigten Ticket; es gibt keine Benachrichtigung. Die Antwort sieht nur, wer das Ticket öffnet.

**Umfang:** Eine an ein bestehendes Ticket angehängte Mail gilt als „neu“, bis eine Person sie als gesehen markiert. Das Helpdesk zeigt die neuen Kundenantworten der Projekte, in denen die Person `helpdesk.reply` hat, als eigene Liste neben den offenen Antworten; das Ticket zeigt einen Hinweis mit der Möglichkeit, die Antwort als gesehen zu markieren. Nicht Teil: automatisches Wiederöffnen erledigter Tickets (später als Einstellung je Projekt), Benachrichtigungen per Mail oder in der Anwendung (eigenes Paket zu REQ-TKT-005), SLA.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Jede durch den Abruf oder durch `…/attach` an ein bestehendes Ticket angehängte Mail ist neu; Mails, die ein Ticket eröffnen, sind es nicht.
2. `GET` einer Liste der neuen Kundenantworten nennt nur Projekte, in denen die Person Mitglied ist und `helpdesk.reply` sowie die Leseregeln für `mail` und `contact` hat; ohne `ticket.read` keine Spur des Tickets.
3. Als gesehen markieren verlangt dieselben Rechte, ist versioniert (If-Match) und steht mit Person und Zeitpunkt im Audit; danach verschwindet die Mail für alle aus der Liste.
4. Die Ticketansicht zeigt neue Kundenantworten hervorgehoben, nur mit der Leseregel für `mail`.
5. Migration, Vertrag gegen Routen, Backend Tests für Rechte und Zustände, Browser Tests für Liste, Hinweis und Markieren.

**Ergebnis (zur Prüfung):** Umgesetzt nach DEC-097 auf Branch claude/project-thread-fde105, Ergebnis-PR nach der Reservierung PR #218. Migration V0052, ursprünglich V0029, beim Einmischen von main umbenannt (`joined_at`, `seen_at`, `seen_by` an `helpdesk_message`); `TicketOpener.append` vermerkt das Anhängen für Abruf und `…/attach`. Neue Endpunkte `GET /api/v1/helpdesk/answers` und `POST /api/v1/helpdesk/answers/{id}/seen` mit If-Match, Rechten wie beim Versand und Audit `answer.seen`; die Mails im Ticket tragen `newAnswer` und `version`. Oberfläche: Liste „Neue Kundenantworten“ in der Prüfwarteschlange, hervorgehobene Antwort mit „Gesehen“ im Ticket. Nachweise: `HelpdeskApiTest.anAnswerThatJoinsATicketIsNewUntilSomeoneMarksItAsSeen` (Liste, Ticketansicht, 403 für Leser und Mitglied ohne Leseregel, 404 für Fremde, 428, 412, 409, Audit, Anhängen über die Prüfwarteschlange), Vertrag gegen Routen (28), zwei Browsertests. Beschreibung in backend/README.md, Abschnitt „Neue Kundenantworten (TASK-067)“.

**Nächster Schritt:** Gegenprüfung des Ergebnis-PR und Abnahme durch Holger.

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

## TASK-072 Tag Katalogpflege und Zusammenführen (A3 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261101-tags-01 · **Aktualisiert:** 2026-11-01

**Bezug:** REQ-TAG-001, REQ-ACL-004, REQ-API-001; DEC-046, DEC-049, DEC-068; docs/TAGS_SEARCH.md (T02, T03, T08). **Abhängigkeiten:** TASK-028, TASK-042, TASK-029 und TASK-030, abgenommen. **Arbeitsstand:** Holger hat am 1. November 2026 im Projektgespräch mit „ja“ den Beginn von Paket A3 nach A10 bestätigt und am selben Tag die 20 Vorschläge mit „passt so“ übernommen (Punkte unten). Reservierung mit PR #228 auf main. Ergebnis auf Branch claude/project-thread-trk1ni mit DEC-102 und Migration V0033; Beschreibung in backend/README.md (Abschnitt „Tag Katalogpflege und Zusammenführen“), Vertrag contracts/tags.openapi.yaml 0.2.0.

**Umfang:** Katalogpflege in der Oberfläche, Beschreibung und Farbe, Zusammenführen, Verwaltungsseite, Nutzungszahlen, Autovervollständigung und Obergrenze je Objekt. Nicht Teil: Massenänderung und Filter (TASK-073), Portaltags (A11), gespeicherte Filter, Hierarchien, Aliase oder Synonyme, endgültiges Löschen, Zusammenführen zwischen Katalogen.

**Fachliche Regeln** (von Holger am 1. November 2026 bestätigt):
1. Beschreibung optional, reiner Text, höchstens 500 Zeichen, sichtbar für alle, die den Tag sehen.
2. Farbe aus einer festen Palette von 12 Farben oder keine, in hell und dunkel lesbar; gespeichert wird der Name der Palettenfarbe. Der Name bleibt immer als Text lesbar.
3. Beschreibung, Farbe, Umbenennen, Archivieren, Wiederherstellen und Zusammenführen brauchen `tags.manage`, für den Unternehmenskatalog unternehmensweit, für einen Projektkatalog im Projekt; `project.manage` allein reicht nicht. Kein neues Recht.
4. Menüpunkt „Tags“ für Personen mit `tags.manage` im Unternehmen mit Unternehmenskatalog, Suche, Filter „archiviert“, Bearbeiten und Zusammenführen; der Projektkatalog wird im Reiter „Tags“ des Projekts gepflegt, Bedienelemente nur mit `tags.manage` im Projekt.
5. Nutzungszahl je Tag für Tickets und Projekte, gezählt nur über Objekte, die die Person lesen darf.
6. Zusammenführen nur im selben Katalog. Quelle und Ziel wählen, Vorschau, Bestätigung; Zuordnungen der Quelle gehen auf das Ziel über, doppelte entfallen. Die Quelle verschwindet aus Katalog und Vorschlägen und gibt ihren Namen frei; ihre Kennung bleibt als Verweis auf das Ziel. Archiviertes Ziel nicht erlaubt, archivierte Quelle schon.
7. Die Vorschau nennt die Zahl der betroffenen lesbaren Tickets und Projekte und weist ohne Zahl darauf hin, dass weitere Zuordnungen an nicht lesbaren Tickets mit umgestellt werden.
8. Kein neuer Eintrag in der Tickethistorie je Ticket, Ticketversion unverändert; alte Einträge mit der Quelle zeigen deren letzten Namen mit „zusammengeführt in“ Ziel. Das Tagaudit hält Quelle, Ziel, Person und Zeitpunkt fest; die Suche wird in derselben Transaktion nachgeführt.
9. Zusammenführen in einer Transaktion bis 20.000 Zuordnungen; darüber verständliche Ablehnung.
10. Archivierte Tags werden nicht vorgeschlagen und nicht neu zugeordnet, bleiben am Ticket sichtbar und filterbar.
11. Autovervollständigung aus Unternehmenskatalog und Projektkatalog, Treffer am Wortanfang zuerst, auch Teilwörter, höchstens 20 Vorschläge mit Farbe und Katalog, Trigrammindex; mit Erstellrecht „neuen Tag anlegen“.
12. Höchstens 30 Tags je Ticket und je Projekt; bestehende Zuordnungen über der Grenze bleiben, neue werden abgelehnt.
13. Projekte nehmen weiter nur Tags des Unternehmenskatalogs.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Beschreibung, Farbe und Pflege nur mit `tags.manage` im passenden Bereich, mit Version (If-Match) und Audit; ungültige Farbe oder zu lange Beschreibung 400.
2. Zusammenführen dedupliziert (T03), verschiebt keine Sichtbarkeit, hält historische Verweise auflösbar, führt die Suche nach und ist gegen gleichzeitige Zuordnung und gleichzeitiges Zusammenführen sicher.
3. Vorschau und Nutzungszahlen verraten keine nicht lesbaren Tickets.
4. Vorschläge zeigen keine archivierten, zusammengeführten oder verborgenen Tags; Obergrenze greift bei Ticket und Projekt.
5. Migration, Vertrag gegen Routen, Backend Tests für Rechte, Zustände und Nebenläufigkeit, Browser Tests für Verwaltungsseite, Projektreiter, Zusammenführen und Vorschläge.

**Übergabe:** Backend mit Migration V0033 (Beschreibung, Farbe, Verweis der Zusammenführung, Trigrammindex), Zusammenführen mit Vorschau, Vorschlägen, Namensabfrage für die Historie, Nutzungszahlen und Obergrenze; Oberfläche mit Seite „Tags“, Projektreiter und Auswahl mit Vorschlägen an Ticket und Projekt. Nachweise im Ergebnis PR. Grenzen: Zusammenführen nur im selben Katalog, über 20.000 Zuordnungen abgelehnt, Prüfung zum Sperrzeitpunkt.

**Nächster Schritt:** Unabhängige Prüfung und Abnahme durch Holger; danach TASK-073.

## TASK-073 Tag Massenänderung und Filter (A3 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261101-tags-01 · **Aktualisiert:** 2026-11-01

**Bezug:** REQ-TAG-001, REQ-SRCH-001, REQ-TKT-004; DEC-046, DEC-069; docs/TAGS_SEARCH.md (T05, T06, T07, T14). **Abhängigkeiten:** TASK-072 geliefert. **Arbeitsstand:** Mit TASK-072 von Holger am 1. November 2026 beauftragt und mit den Vorschlägen 13 bis 16 bestätigt. Das Ergebnis von TASK-072 ist mit PR #229 auf main; danach ohne neue Reservierung in derselben Sitzung umgesetzt, Ergebnis mit DEC-103 ohne Migration auf Branch claude/project-thread-trk1ni; Beschreibung in backend/README.md (Abschnitt „Tag Massenänderung und Filter“), Verträge tags 0.3.0 und search 0.2.0.

**Umfang:** Massenänderung an Tickets und Tagfilter in Ticketliste und Suche mit Oberfläche. Nicht Teil: Massenänderung an Projekten, Anlage neuer Tags in der Massenänderung, gespeicherte Filter, Hintergrundaufträge.

**Fachliche Regeln** (von Holger am 1. November 2026 bestätigt):
1. In Ticketliste und Suchergebnis bis 500 Tickets auswählbar, auch über Projekte; „Tags hinzufügen“ oder „Tags entfernen“ mit 1 bis 10 vorhandenen Tags, kein Ersetzen der ganzen Liste.
2. Jedes Ticket wird für sich mit aktuellen Rechten geprüft und geändert (`ticket.edit` und Schreibregel für `tags`); ein Projekttag passt nur zu Tickets seines Projekts, andere werden als „nicht passend“ übersprungen; keine Gesamttransaktion.
3. Ergebnis mit Zahlen für geändert, schon so, nicht passend, abgelehnt; einzelne Tickets nur mit Schlüssel, wenn noch lesbar; Wiederholung ohne doppelte Zuordnung; Anfrage direkt, nicht als Hintergrundauftrag.
4. Filter „alle gewählten Tags“, „mindestens einer“, „keiner der gewählten“ und „ohne sichtbare Tags“, bis 10 Tags je Filter, kombinierbar; nur Sichtbares zählt, ohne Leseregel für `tags` kein Tagfilter.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Massenänderung nach Rechteentzug an einem Teil der Auswahl (T06) und mit gleichzeitiger Einzeländerung (T07) ohne stillen Verlust und ohne fremde Namen im Ergebnis.
2. Filter liefern dieselben Treffer in Ticketliste und Suche; verborgene Tags beeinflussen weder Treffer noch Zähler (T05, T14).
3. Vertrag gegen Routen, Backend Tests für Rechte, Grenzen und Teilergebnisse, Browser Tests für Auswahl, Vorschau, Ergebnis und Filter.

**Übergabe:** `POST /api/v1/tags/bulk` mit Prüfung der Tags vorab und je Ticket in eigener Transaktion, Ergebnis mit vier Zahlen und Schlüsseln nur lesbarer Tickets; `tagMatch` mit `all`, `any`, `none` und `untagged` in Ticket und Projektsuche; Oberfläche mit Tagfilter, Auswahl, Prüfung vor dem Ausführen und Ergebnis in Suche und Ticketliste, die mit Tagfilter die Suche nutzt. Nachweise im Ergebnis PR. Grenzen: kein Hintergrundauftrag, eine abgebrochene Anfrage lässt bis dahin geänderte Tickets geändert; Prüfung zum Sperrzeitpunkt.

**Nächster Schritt:** Unabhängige Prüfung von TASK-072 und TASK-073 und Abnahme durch Holger.

## TASK-086 Befunde beider Prüfungen von TASK-072 und TASK-073 beheben (A3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261101-tags-01 · **Aktualisiert:** 2026-11-04

**Bezug:** REQ-TAG-001, REQ-SRCH-001, REQ-TKT-004; DEC-102, DEC-103; [Codex Bericht](docs/REVIEW_TASK-072-073_PR-229-230.md) (PR #254), [Claude Bericht](docs/REVIEW_TASK-072-073_PR-229-230_claude.md) (PR #231). **Abhängigkeiten:** TASK-072 und TASK-073 geliefert. **Arbeitsstand:** Von Holger am 4. November 2026 mit „Ja, alles in TASK-086“ beauftragt; Branch claude/project-thread-trk1ni, Ergebnis mit DEC-116 ohne Migration; Beschreibung in backend/README.md (Abschnitt „Befunde beider Prüfungen von TASK-072 und TASK-073“), Vertrag tags 0.5.0.

**Umfang:** Nur die Befunde und Hinweise beider Prüfungen:
1. Codex F1: Umhängen früherer Zusammenführungsverweise in derselben Sperrfolge wie Quelle und Ziel, ohne Verklemmung mit einer gleichzeitigen alten Zusammenführung.
2. Codex F2 und Claude F1: Massenentfernen sperrt und prüft die Tags wie das Hinzufügen; ein zwischenzeitlich zusammengeführter Tag lehnt das Ticket mit Grund ab, archivierte Tags bleiben entfernbar.
3. Claude F2: Ein Fehler beim Abschluss eines Tickets oder beim Lesen seines Schlüssels beendet die Massenänderung nicht ohne Ergebnis, das Ticket zählt als abgelehnt.
4. Codex F3: Archivierte Tags sind im Tagfilter und beim Entfernen auswählbar, neue Zuordnungen schließen sie weiter aus.
5. Codex H1: Suche und Ticketliste übernehmen nur die Antwort der zuletzt gestarteten Anfrage.
6. Kleinere Hinweise beider Prüfungen: Text des Grundes für zusammengeführte Tags, Testattrappe mit den Ablehnungen des Servers, Namensauflösung der Historie in Paketen, stabiler zeitabhängiger Browsertest und die übrigen Hinweise H1 bis H5 der Claude Prüfung, soweit sie Code oder Tests betreffen.

Nicht Teil: neue Funktionen, Massenänderung an Projekten, gespeicherte Filter.

**Abnahmekriterien:** Vorschlag, zur Prüfung: F1 bis F3 der Codex Prüfung und F1, F2 der Claude Prüfung jeweils mit einem Regressionstest belegt (Backend für Sperrfolge, Entfernen nach Zusammenführen und Fehler beim Abschluss, Browser für archivierte Tags im Filter und die verspätete Suchantwort); bestehende Backend und Browser Tests bleiben grün.

**Übergabe:** Beratungssperre je Katalog vor dem Zusammenführen; Massenentfernen mit geteilten Tagsperren und `tag.merged` für inzwischen zusammengeführte Tags; Ablehnung beim Commit und Fehler beim Schlüssel als abgelehntes Ticket im Ergebnis; Vorschläge mit `archived=true` für Tagfilter und Entfernen; Suche mit Nummer je Anfrage und gemerkter abgeschickter Suche; eigener Grund „zusammengeführt“, Hinweis an der Grenze von 1000 Treffern, Historie in Paketen zu 100; Testattrappe mit den Ablehnungen des Servers, stabile Katalogtests. Je Befund ein Regressionstest, die neuen Backend und Browsertests scheitern ohne die Korrektur. Nachweise im Ergebnis PR. Grenzen: jede Ablehnung beim Commit heißt `auth.forbidden`; die Installationsanleitung für `pg_trgm` steht aus.

**Nächster Schritt:** Prüfung durch eine zweite KI und Abnahme von TASK-072, TASK-073 und TASK-086 durch Holger.

## TASK-081 Auftritt für Anmeldung und Portal (A17 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-branding-01 · **Aktualisiert:** 2026-11-02

**Bezug:** REQ-BRAND-001, REQ-UX-001, REQ-PORTAL-001; OPEN-015; DEC-060, DEC-064; docs/WIKI_I18N_BRANDING.md (Logo, Farben und Vorlagen, Prüffall W14). **Abhängigkeiten:** TASK-036, TASK-040, TASK-075, TASK-076, geliefert. **Arbeitsstand:** Holger hat am 2. November 2026 im Projektgespräch mit „ja“ den Beginn von Paket A17 nach A11 bestätigt und am selben Tag die 20 Vorschläge mit „passt“ übernommen (Regeln unten und bei TASK-082). Reservierung mit PR #242 gemergt. Ergebnis auf Branch claude/project-thread-tzp0cw mit DEC-111 und der Migration V0046; Beschreibung in backend/README.md (Abschnitt „Erscheinungsbild“), Vertrag appearance 0.2.0.

**Umfang:** Einstellbarer Name im Auftritt, öffentlicher Lesepfad für Name, Akzentfarbe und Logo vor der Anmeldung, Auftritt auf Anmeldeseite, Pflichtwechsel des Passworts, Seite nach dem Abmelden und im Portal, „Alles zurücksetzen“, Vorschau aller Bereiche auf der Seite „Erscheinungsbild“, mit Migration, Vertrag, Oberfläche und Tests. Nicht Teil: Mailvorlagen (TASK-082), PDFs, weitere Farben, SVG Logos, Entwurf und Freigabe des Auftritts, eigener Auftritt nur für das Portal, Mandanten.

**Fachliche Regeln** (von Holger am 2. November 2026 bestätigt):
1. Der Auftritt erhält einen Namen mit 1 bis 80 Zeichen, Vorgabe „Task Manager“. Er erscheint im Seitenkopf, im Fenstertitel, auf der Anmeldeseite, im Portal und in Mails.
2. Ein öffentlicher Lesepfad liefert ohne Anmeldung nur Name, Akzentfarbe und Logo, ohne Version, Zeitpunkt oder bearbeitende Person. Er gilt für die Installation und wird nicht über Eingaben des Besuchers gewählt. Fällt er aus, zeigt die Seite den Standard.
3. Anmeldung, Pflichtwechsel des Passworts und die Seite nach dem Abmelden zeigen Logo, Name und Akzentfarbe; Hell und Dunkel bleiben wählbar.
4. Das Portal nutzt denselben Auftritt wie die Anwendung, im Kopf mit dem Zusatz „Kundenportal“.
5. Es bleibt bei Akzentfarbe und Logo; die Schrift auf Akzentflächen wird automatisch schwarz oder weiß gewählt. Fehler, Status und Fokus hängen nie allein an der Akzentfarbe.
6. Logo als PNG, JPEG oder WebP bis 512 KB, keine SVG Dateien; dasselbe Logo dient Anwendung, Anmeldung, Portal und Mails.
7. Änderungen wirken sofort, ohne Entwurf; Version mit If-Match und Audit bleiben. „Alles zurücksetzen“ setzt Name, Farbe und Logo in einem Schritt auf den Standard.
8. Die Seite „Erscheinungsbild“ zeigt die ungespeicherte Wahl in Anwendung, Anmeldeseite, Portal und einer Beispielmail in Deutsch und Englisch mit synthetischen Daten. Die Beispielmail zeigt bis TASK-082 den Kopf mit Logo und Name.
9. Das Recht bleibt `appearance.manage`.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Eine Änderung von Name, Farbe und Logo wirkt auf Anwendung, Anmeldeseite und Portal und lässt sich zurücksetzen (W14).
2. Der öffentliche Lesepfad gibt nur Name, Farbe und Logo preis; ungültige Namen werden mit 400 abgelehnt; Änderungen ohne `appearance.manage` oder mit veralteter Version werden abgelehnt.
3. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für Anmeldeseite, Portal und Vorschau in Hell und Dunkel.

**Übergabe zur Prüfung (2026-11-02):**
1. Ergebnis: Spalte `name` mit V0046; `PUT /api/v1/appearance` nimmt Farbe, Name oder beides (400 `appearance.name_invalid`, 400 `appearance.change_empty`), `DELETE /api/v1/appearance` setzt alles zurück (Audit `appearance.reset`); `GET /api/v1/appearance/public` und `/public/logo` ohne Sitzung mit nur Name, Farbe und Logo, im Filter genau für `GET` und `HEAD` freigegeben. Oberfläche: Anmeldeseite, Passwortwechsel und Seite nach dem Abmelden mit dem öffentlichen Auftritt, Portal mit Zusatz „Kundenportal“, Seite „Erscheinungsbild“ mit Name, „Alles zurücksetzen“ mit Rückfrage und Vorschau von Anwendung, Anmeldeseite, Portal und Beispielmail auf Deutsch und Englisch.
2. Nachweise: `AppearanceApiTest` (Name, Kürzen, Grenzen in Codepunkten, Steuer- und Richtungszeichen, Audit je Teil, Zurücksetzen mit Version, öffentlicher Lesepfad ohne Cookie, mit abgelaufenem Cookie und bei ausstehendem Passwortwechsel, nichts sonst geöffnet), `AppearanceContractTest` mit acht Routen, `DatabaseFoundationTest`, `ApplicationLifecycleTest` und `lifecycle-check.sh` mit V0046, voller Backendlauf mit PostgreSQL 16, `npm run verify` und alle Browsertests einschließlich neuer Fälle in `design.test.mjs` und `portal.test.mjs` sowie 320 px und Kontrast hell und dunkel.
3. Grenzen: Die Beispielmail zeigt bis TASK-082 nur Kopf, Beispieltext und Name; echte Mails bleiben bis dahin reiner Text. Mit Logo bleibt der Name im Kopf wie bisher nur für Screenreader sichtbar. Ein geändertes Logo erscheint auf geöffneten Anmeldeseiten erst beim nächsten Laden.

**Nächster Schritt:** Die Codex Prüfung (docs/REVIEW_TASK-081_PR-243.md, 4. November 2026) empfiehlt „nachbessern“; die Befunde behebt TASK-087. Abnahme durch Holger danach zusammen mit TASK-087. TASK-082 und TASK-085 sind am 3. November 2026 abgenommen (DONE.md).

## TASK-087 Befunde der Prüfung von TASK-081 beheben (A17)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-branding-01 · **Aktualisiert:** 2026-11-04

**Bezug:** REQ-BRAND-001, REQ-UX-001, REQ-PORTAL-001; DEC-111; docs/REVIEW_TASK-081_PR-243.md. **Abhängigkeiten:** TASK-081 geliefert (PR #243). **Arbeitsstand:** Holger hat am 4. November 2026 den Codex Prüfbericht zu TASK-081 (Empfehlung „nachbessern“) in das Projektgespräch gestellt; Umfang nach seiner Wahl „F1 bis F4 und H1 bis H3 beheben“ vom selben Tag. Branch claude/project-thread-tzp0cw, Umsetzung voraussichtlich mit DEC-117 und der Migration V0054 (V0053 ist für TASK-069 vorgemerkt; vor dem Ergebnis die nächste freie Nummer prüfen).

**Umfang:** Befunde F1 bis F4 und Hinweise H1 bis H3 aus docs/REVIEW_TASK-081_PR-243.md:
1. F1: eine gemeinsame Regel für den Namen in Backend und Oberfläche, die auch geschützte Leerzeichen am Rand entfernt und ungültige Unicode Folgen mit 400 ablehnt; vorhandene betroffene Namen korrigiert eine neue Migration, V0046 bleibt unverändert.
2. F2: das Speichern von Name, Farbe oder Logo verwirft keine ungespeicherte Eingabe eines anderen Teils; nur „Alles zurücksetzen“ setzt alles zurück.
3. F3: der Fokus geht nach dem Speichern auf die Bestätigung und nach dem Abbrechen der Rückfrage zurück auf „Alles zurücksetzen“.
4. F4: der Name ist im Kopf von Anwendung, Anmeldeseite, Pflichtwechsel und Portal neben dem Logo und auf schmalen Ansichten sichtbar, wie in der Vorschau.
5. H1: die Datenbank prüft dieselben Zeichenverbote wie die Anwendung (in derselben Migration).
6. H2: das Audit nennt nur Teile, die sich wirklich geändert haben.
7. H3: der Vertrag beschreibt die öffentlichen HEAD Aufrufe und die Normalisierung des Namens.

Nicht Teil: weitere Funktionen des Auftritts; die Stabilisierung der Tagtests (H4) gehört zur Testpflege.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 und H1 mit API und Migrationstests für U+00A0, U+2007, U+202F und einen einzelnen Surrogaten; F2, F3 und F4 mit Browsertests (verlorene Eingabe, Fokus über `document.activeElement`, sichtbare Ausdehnung des Namens mit und ohne Logo bei 1100 und 320 px); H2 mit einem API Test für ein identisches PUT.
2. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-04):**
1. Ergebnis: DEC-117, Migration V0054 (nach V0053 von TASK-069). Die Regel des Namens steht ausgeschrieben gleich in `AppearanceService` (`EDGE`, `FORBIDDEN`), in V0054 als Datenbankprüfung, in `frontend/src/api/client.ts` (`normalizeBrandName`, `isBrandName`) und im Testersatz: Leerzeichen jeder Art an den Rändern fallen weg, Steuer-, Trenn- und Formatzeichen (Unicode 15.1, Cf) und halbe Surrogatpaare werden mit 400 `appearance.name_invalid` abgelehnt. V0054 kürzt gespeicherte Namen an den Rändern und setzt einen danach ungültigen auf „Task Manager“. Ein `PUT` ohne Änderung lässt Version und Audit stehen; das Audit nennt nur geänderte Teile. `AppearancePage.tsx` übernimmt nach dem Speichern nur den Wert des gespeicherten Teils, setzt den Fokus auf die Rückmeldung und nach „Abbrechen“ auf „Alles zurücksetzen“. `styles.css` blendet den Namen neben dem Logo und bis 30 rem nicht mehr aus; dort erhält der Kopf für ihn eine eigene Zeile. Vertrag appearance 0.3.1 mit `HEAD` der öffentlichen Pfade und der Normalisierung des Namens; `AppearanceContractTest` prüft HEAD genau für diese beiden Pfade. backend/README.md ergänzt.
2. Nachweise (lokal, JDK 21, PostgreSQL 16): `./mvnw clean verify` 522 Tests, davon einer rot (`JobExecutorTest.anExpiredLeaseOnTheLastAttemptFailsTheJob`, ohne Bezug, in der Wiederholung der Klasse 26 von 26 grün). Neu: in `AppearanceApiTest` geschützte Leerzeichen, ungültige Folgen und Surrogate über die API, die Datenbankprüfung und das unveränderte `PUT`; `AppearanceNameMigrationTest` mit sieben gespeicherten Namen von V0052 auf V0054. Frontend `npm run verify` 80 Tests, `npm run test:browser` 191 Tests, davon 190 grün; rot war nur `ldap.test.mjs` wegen einer Routenantwort nach dem Schließen des Browsers, ohne Bezug, einzeln zweimal 12 von 12 grün. Neu im Browser: erhaltene Eingaben bei Farbe, Name und Logo, Ränder mit geschützten Leerzeichen, Fokus nach Speichern, Abbrechen und Zurücksetzen per Tastatur, sichtbarer Name bei 1100 und 320 px mit und ohne Logo vor und nach der Anmeldung; ohne die Behebung waren fünf dieser Tests rot. Repositoryprüfungen grün. Nicht lokal: `lifecycle-check.sh` mit Docker (läuft in CI).
3. Grenzen: Die Liste der Formatzeichen folgt Unicode 15.1 und wird bei neuen Unicode Versionen nicht von selbst länger. Emoji Folgen mit Verbindungszeichen (U+200D) und Flaggen aus Tag Zeichen bleiben wie bisher abgelehnt. V0054 schreibt für eine Bereinigung keinen Auditeintrag.

**Nächster Schritt:** Nachprüfung durch eine zweite KI, danach Abnahme von TASK-081 und TASK-087 durch Holger.
