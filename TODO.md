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
| Blockiert (BLOCKIERT) | 1 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 1 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-067](#task-067-neue-kundenantworten-an-bestehenden-tickets-sichtbar-machen) | Neue Kundenantworten an bestehenden Tickets sichtbar machen | claude-20261030-newanswers-01 | 2026-10-30 |

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-073](#task-073-tag-massenänderung-und-filter-a3-teil-2) | Tag Massenänderung und Filter (A3 Teil 2) | claude-20261101-tags-01 | 2026-11-01 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-072](#task-072-tag-katalogpflege-und-zusammenführen-a3-teil-1) | Tag Katalogpflege und Zusammenführen (A3 Teil 1) | claude-20261101-tags-01 | 2026-11-01 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-067 Neue Kundenantworten an bestehenden Tickets sichtbar machen

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261030-newanswers-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-HD-002 (Antworten zuordnen), REQ-TKT-005 (Änderungen). **Abhängigkeiten:** TASK-058 und TASK-062 bis TASK-064, abgenommen (PR #211); Hinweis H7 aus [docs/REVIEW_TASK-058_PR-202.md](docs/REVIEW_TASK-058_PR-202.md). **Arbeitsstand:** Holger hat am 30. Oktober 2026 der Empfehlung zu H7 zugestimmt und im Projektgespräch „nach H7 bitte A10 und A6 parallel“ die Umsetzung beauftragt. Arbeit beginnt nach dem Merge dieser Reservierung. Branch claude/project-thread-fde105; Ergebnis mit eigener DEC-ID und voraussichtlich Migration V0029.

**Ausgangslage:** Hängt der Abruf eine Antwort des Kunden an ein bestehendes Ticket an (`TicketOpener.append`, auch über `POST /api/v1/helpdesk/inbox/{id}/attach`), entsteht nur ein Eintrag der Historie im Feld `mail`. Status und Zuständigkeit bleiben, auch bei einem erledigten Ticket; es gibt keine Benachrichtigung. Die Antwort sieht nur, wer das Ticket öffnet.

**Umfang:** Eine an ein bestehendes Ticket angehängte Mail gilt als „neu“, bis eine Person sie als gesehen markiert. Das Helpdesk zeigt die neuen Kundenantworten der Projekte, in denen die Person `helpdesk.reply` hat, als eigene Liste neben den offenen Antworten; das Ticket zeigt einen Hinweis mit der Möglichkeit, die Antwort als gesehen zu markieren. Nicht Teil: automatisches Wiederöffnen erledigter Tickets (später als Einstellung je Projekt), Benachrichtigungen per Mail oder in der Anwendung (eigenes Paket zu REQ-TKT-005), SLA.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Jede durch den Abruf oder durch `…/attach` an ein bestehendes Ticket angehängte Mail ist neu; Mails, die ein Ticket eröffnen, sind es nicht.
2. `GET` einer Liste der neuen Kundenantworten nennt nur Projekte, in denen die Person Mitglied ist und `helpdesk.reply` sowie die Leseregeln für `mail` und `contact` hat; ohne `ticket.read` keine Spur des Tickets.
3. Als gesehen markieren verlangt dieselben Rechte, ist versioniert (If-Match) und steht mit Person und Zeitpunkt im Audit; danach verschwindet die Mail für alle aus der Liste.
4. Die Ticketansicht zeigt neue Kundenantworten hervorgehoben, nur mit der Leseregel für `mail`.
5. Migration, Vertrag gegen Routen, Backend Tests für Rechte und Zustände, Browser Tests für Liste, Hinweis und Markieren.

**Nächster Schritt:** Nach dem Merge dieser Reservierung umsetzen, Ergebnis-PR mit Nachweisen.

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

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261101-tags-01 · **Aktualisiert:** 2026-11-01

**Bezug:** REQ-TAG-001, REQ-SRCH-001, REQ-TKT-004; DEC-046, DEC-069; docs/TAGS_SEARCH.md (T05, T06, T07, T14). **Abhängigkeiten:** TASK-072 geliefert. **Arbeitsstand:** Mit TASK-072 von Holger am 1. November 2026 beauftragt und mit den Vorschlägen 13 bis 16 bestätigt. Blockiert, bis das Ergebnis von TASK-072 auf main ist; dann ohne neue Reservierung weiter in derselben Sitzung, Ergebnis mit DEC-103.

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

**Nächster Schritt:** Nach dem Ergebnis von TASK-072 umsetzen.
