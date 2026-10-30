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
| Durch eine KI reserviert (IN ARBEIT) | 3 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 1 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 0 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-067](#task-067-neue-kundenantworten-an-bestehenden-tickets-sichtbar-machen) | Neue Kundenantworten an bestehenden Tickets sichtbar machen | claude-20261030-newanswers-01 | 2026-10-30 |
| [TASK-068](#task-068-sla-fristen-und-geschäftszeiten-a10-teil-1) | SLA Fristen und Geschäftszeiten (A10 Teil 1) | claude-20261030-sla-01 | 2026-10-30 |
| [TASK-069](#task-069-zeiterfassung-im-projekt-und-ticketzusammenhang-paket-a6) | Zeiterfassung im Projekt und Ticketzusammenhang (Paket A6) | claude-20261030-time-01 | 2026-10-30 |

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-070](#task-070-sla-eskalationen-a10-teil-2) | SLA Eskalationen (A10 Teil 2) | claude-20261030-sla-01 | 2026-10-30 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

Derzeit keine Aufgaben in dieser Kategorie.

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

## TASK-068 SLA Fristen und Geschäftszeiten (A10 Teil 1)

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261030-sla-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-HD-003 (SLA Fristen), REQ-HD-004 (Geschäftszeiten), ACC-002; DEC-032; OPEN-008. **Abhängigkeiten:** TASK-054 bis TASK-056 (Hintergrundaufträge) und TASK-057 bis TASK-064 (Helpdesk über Mail), abgenommen; TASK-044 (Projektkonfiguration), abgenommen. **Arbeitsstand:** Holger hat am 30. Oktober 2026 im Projektgespräch „nach H7 bitte A10 und A6 parallel“ das Paket A10 beauftragt und am selben Tag die 20 Vorschläge zu OPEN-008 mit „Ja, so übernehmen“ bestätigt (Punkte unten). Arbeit beginnt nach dem Merge dieser Reservierung. Branch claude/project-thread-2gq3w2; Ergebnis mit DEC-098 und der beim Ergebnis nächsten freien Migrationsnummer auf main.

**Umfang:** Kalender, SLA Regeln, Priorität und Fristuhren je Ticket mit Anzeige am Ticket und ihrer Oberfläche. Nicht Teil: Eskalationen (TASK-070), Mail an interne Empfänger, Benachrichtigungen, Portal, Auswertungen und Berichte über Fristen, Filter nach Fristzustand in der Suche, Neubewertung laufender Zyklen, Feiertagsimport, Verträge je Kunde über die Zuordnung in Regel 3 hinaus.

**Fachliche Regeln** (von Holger am 30. Oktober 2026 bestätigt, Klärung von OPEN-008):
1. Neues Ticketfeld `priority` mit den Werten Niedrig, Normal, Hoch, Kritisch, Vorgabe Normal, mit Feldregeln und Historie.
2. Budgets ohne Produktvorgabe: Erstreaktion und Lösung in Geschäftsminuten je Regel und Priorität; 120 und 480 Minuten nur in den Demodaten.
3. Zuordnung der Regel in der Reihenfolge Projekt mit Kunde und Tickettyp, Projekt mit Kunde, Standard des Projekts; zwei gleich passende Regeln werden beim Speichern abgelehnt; ohne Regel zeigt das Ticket „Keine SLA zugeordnet“.
4. Fristen nur für Tickets mit gesetztem Feld `customer`; ein später gesetzter Kunde startet mit dem Zeitpunkt aus Regel 7.
5. Neues Unternehmensrecht `sla.manage` für Kalender, Regeln und deren Zuordnung; Lesen der Fristen folgt der Leseregel des Tickets.
6. Kalender mit IANA Zeitzone, je Wochentag einem oder mehreren offenen Zeiträumen und datierten Ausnahmen; Zeitumstellung wie in docs/HELPDESK_SLA.md (nicht existierende Uhrzeit auf die nächste gültige, doppelte Startzeit auf das frühere, doppelte Endzeit auf das spätere Vorkommen). Ein laufender Zyklus behält die Fassung von Regel und Kalender, mit der er begann.
7. Start ist der erste dauerhafte Eingang: bei einer Mail der Zeitpunkt der Speicherung durch den Abruf, auch bei späterer Übernahme aus der Prüfwarteschlange (S-12); sonst die Anlage des Tickets. Der Date Header zählt nicht.
8. Qualifizierte Erstreaktion ist eine Antwort über SMTP im Zustand „gesendet“, zum Zeitpunkt der Annahme durch den Mailserver.
9. Jeder Status eines Ablaufs bekommt in der Projektkonfiguration eine SLA Art: offen (Vorgabe), wartet auf Kunde, gelöst, verworfen.
10. Kundenpause (DEC-032) beginnt mit dem Wechsel in „wartet auf Kunde“ und endet mit einer dem Ticket zugeordneten Kundenantwort oder dem nächsten Statuswechsel; eine Antwort des Supports beendet sie nicht.
11. Lösung ist erfüllt, wenn das Ticket in „gelöst“ steht und im Zyklus mindestens eine Antwort gesendet wurde, zum späteren der beiden Zeitpunkte (H-09); „verworfen“ beendet den Zyklus als Abbruch.
12. Rückkehr aus „gelöst“ in einen offenen Status beginnt einen neuen Zyklus mit neuem Budget (S-10); eine Kundenmail allein öffnet nicht wieder.
13. Prioritätswechsel: neues Budget minus verbrauchte Zeit, bei negativem Rest ab diesem Moment verletzt (S-09); erfüllte Uhren bleiben erfüllt.
14. Das Ticket zeigt je Uhr Zustand (läuft, pausiert, erfüllt, verletzt, abgebrochen), Fälligkeit mit Zeitzone, verbleibende Geschäftszeit, Regel und Kalender; während einer Pause nur das Restbudget.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Die Rechenbeispiele S-01 bis S-14 und H-09, H-10, H-12 aus docs/HELPDESK_SLA.md sind als Backend Tests der Berechnung umgesetzt, dazu Tests der Grenzauflösung bei Zeitumstellung.
2. Kalender und Regeln sind versioniert, nur mit `sla.manage` änderbar, mit Audit; mehrdeutige Zuordnung wird mit 409 abgelehnt.
3. Uhren werden in der Transaktion der auslösenden Änderung (Ticket, Status, Priorität, Kunde, Antwort gesendet, Kundenantwort) fortgeschrieben; nebenläufige Änderungen erzeugen keinen doppelten oder verlorenen Zyklus.
4. Die Fristen am Ticket erscheinen nur mit Leserecht auf das Ticket und verraten keine verborgenen Felder.
5. Migration, Vertrag gegen Routen, Backend Tests für Rechte, Zustände und Nebenläufigkeit, Browser Tests für Kalender, Regeln und die Anzeige am Ticket; Demodaten mit einem Kalender und einer Regel.

**Nächster Schritt:** Nach dem Merge dieser Reservierung umsetzen, Ergebnis-PR mit Nachweisen.

## TASK-069 Zeiterfassung im Projekt und Ticketzusammenhang (Paket A6)

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261030-time-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-TIME-001, REQ-TIME-002; Abgrenzung REQ-TIME-003; OPEN-021 (Zeitkorrekturen, Exportumfang). **Abhängigkeiten:** Projekte (TASK-026), Ticketkern (TASK-027), Projektkonfiguration (TASK-044), Oberfläche (TASK-030), alle abgenommen. **Arbeitsstand:** Holger hat am 30. Oktober 2026 im Projektgespräch „nach H7 bitte A10 und A6 parallel“ die Umsetzung von Paket A6 beauftragt und am selben Tag die 15 Vorschläge zu den Zeitregeln mit „passt so“ bestätigt. Arbeit beginnt nach dem Merge dieser Reservierung. Branch claude/project-thread-thp5tg; Ergebnis mit eigener DEC-ID (voraussichtlich DEC-099, DEC-098 ist für Paket A10 vorgesehen) und einer Migration mit der beim Ergebnis nächsten freien Nummer auf main.

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

**Nächster Schritt:** Nach dem Merge dieser Reservierung umsetzen, Ergebnis-PR mit Nachweisen.

## TASK-070 SLA Eskalationen (A10 Teil 2)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261030-sla-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-HD-005 (Eskalationen), ACC-002; OPEN-008. **Abhängigkeiten:** TASK-068 geliefert. **Arbeitsstand:** Mit TASK-068 von Holger am 30. Oktober 2026 beauftragt und mit den Vorschlägen 17 bis 20 bestätigt. Blockiert, bis das Ergebnis von TASK-068 auf main ist; dann ohne neue Reservierung weiter in derselben Sitzung, Ergebnis mit eigener DEC-ID.

**Umfang:** Eskalationsstufen, ihre Überwachung und die Liste „SLA Eskalationen“ mit Oberfläche. Nicht Teil: Mail, Benachrichtigungen, automatische Eingriffe.

**Fachliche Regeln** (von Holger am 30. Oktober 2026 bestätigt):
1. Je Regel und Uhr eine Liste von Stufen mit Prozentwert und Empfängergruppe aus der Rechteverwaltung; Vorschlag beim Anlegen 80 % Warnung und 100 % Fälligkeit, ohne feste Produktvorgabe.
2. Eine Eskalation erscheint als Hinweis am Ticket und in einer Liste „SLA Eskalationen“ für Mitglieder der Empfängergruppe, die das Ticket lesen dürfen; keine Mail, weil Konten keine Mailadresse haben und Benachrichtigungen ein eigenes späteres Paket sind.
3. Eine Stufe entsteht je Ticket, Zyklus, Uhr und Stufe höchstens einmal; nach einem Ausfall wird nur die höchste fällige Stufe nachgeholt, übersprungene werden protokolliert (H-11). Überwachung als wiederkehrende Aufgabe `sla.watch` jede Minute.
4. Keine automatische Zuweisung und kein Statuswechsel durch Eskalationen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. S-13 und H-11 als Tests; mehrere Ausführer erzeugen keine doppelte Stufe.
2. Die Liste zeigt nur Tickets, die die Person lesen darf, und nur Stufen ihrer Gruppen; Rechte werden beim Lesen geprüft, nicht beim Entstehen.
3. Eine Pause oder Erfüllung vor der Schwelle erzeugt keine Stufe; eine bereits entstandene bleibt im Verlauf.
4. Migration, Vertrag gegen Routen, Backend und Browser Tests.

**Nächster Schritt:** Nach dem Ergebnis von TASK-068 umsetzen.
