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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 3 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-067](#task-067-neue-kundenantworten-an-bestehenden-tickets-sichtbar-machen) | Neue Kundenantworten an bestehenden Tickets sichtbar machen | claude-20261030-newanswers-01 | 2026-10-30 |

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-068](#task-068-sla-fristen-und-geschäftszeiten-a10-teil-1) | SLA Fristen und Geschäftszeiten (A10 Teil 1) | claude-20261030-sla-01 | 2026-10-30 |
| [TASK-070](#task-070-sla-eskalationen-a10-teil-2) | SLA Eskalationen (A10 Teil 2) | claude-20261030-sla-01 | 2026-10-31 |
| [TASK-071](#task-071-befunde-der-codex-prüfung-von-task-070-beheben) | Befunde der Codex Prüfung von TASK-070 beheben | claude-20261030-sla-01 | 2026-11-01 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-071 Befunde der Codex Prüfung von TASK-070 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261030-sla-01 · **Aktualisiert:** 2026-11-01

**Bezug:** REQ-HD-005, ACC-002. **Abhängigkeiten:** TASK-070 geliefert (PR #223 auf main); Bericht [docs/REVIEW_TASK-070_PR-223.md](docs/REVIEW_TASK-070_PR-223.md). **Arbeitsstand:** Holger hat am 31. Oktober 2026 den Bericht übergeben und zu H5 „Ja, Stufe nachtragen“ gewählt. Reservierung PR #224 am 1. November 2026 gemergt; Ergebnis auf Branch claude/project-thread-2gq3w2 mit DEC-101, ohne Migration.

**Umfang:** Nur die Befunde F1 bis F3 und die Hinweise H1, H2 und H5 des Berichts; sonst nichts.
1. H5 und F1: Bevor eine laufende Uhr pausiert, erfüllt, abgebrochen oder neu budgetiert wird, hält die auslösende Änderung in ihrer Transaktion die bis dahin erreichten Stufen fest, mit dem tatsächlichen Zeitpunkt der Schwelle. Eine nach der Schwelle, aber vor der nächsten Prüfung beendete Uhr behält ihre Stufe im Verlauf; nach dem Fortsetzen erscheint kein falscher Zeitpunkt. Macht erst ein kleineres neues Budget eine Stufe fällig, gilt der Zeitpunkt der Budgetänderung.
2. F2: Die Liste „SLA Eskalationen“ liest Kandidaten seitenweise und prüft die Rechte, bis 200 sichtbare Einträge oder das Ende erreicht sind.
3. F3: Eine Stufe ohne Uhr wird mit 400 `escalations.invalid` abgelehnt.
4. H1: Die Testattrappe begrenzt die Liste wie der Server und lehnt eine leere Uhr gleich ab.
5. H2: Tests für einen Prioritätswechsel vor und nach einer Schwelle und für eine Statusänderung, die mit der Überwachung zusammenfällt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Regressionstests für den Fall aus F1 (Pause nach der Schwelle), für H5 (Erfüllung nach der Schwelle vor der Prüfung) und für den kleineren Budgetwechsel.
2. Ein API Test mit mehr als 200 neueren nicht lesbaren und einer älteren lesbaren Stufe.
3. API Test für `clock: null` mit 400.
4. Browsertests gegen die angepasste Attrappe; volle Backend- und Frontend-Suites grün.

**Ergebnis:** DEC-101. `TicketSla.change` hält vor jedem Schreiben eines geänderten Zyklus (Statuswechsel, Kunde entfernt, Antwort gesendet, Priorität) die bis dahin erreichten Stufen unter derselben Sperre fest; die Überwachung nutzt dieselbe Methode `record`. `Escalations.due` nimmt einen Zeitpunkt vor dem letzten Stand als diesen Stand. Die Liste liest Kandidaten seitenweise (`SlaRepository.escalationsFor` mit Fortsetzung und ausgeschlossenen Projekten) bis 200 sichtbare Einträge. `Rule.Stage.violations` prüft eine fehlende Uhr zuerst. Testattrappe mit der Grenze von 200; Beschreibung in [backend/README.md](backend/README.md#eskalationen-task-070).

**Prüfungen:** Backend vollständig gegen PostgreSQL 16 lokal grün (55 Testklassen, 460 Tests), darunter `EscalationsTest` mit 8 Tests (neu: Zeitpunkte beim Speichern vor der Pause, kleineres Budget und früher Zeitpunkt) und 5 neue Tests in `SlaApiTest` (Pause nach der Schwelle mit erhaltenem Zeitpunkt, Antwort nach der Schwelle vor der Prüfung, kleineres und größeres Budget, Statuswechsel gleichzeitig mit drei Überwachungen, 201 nicht lesbare neuere Stufen vor einer lesbaren und Stufe ohne Uhr mit 400). Frontend `tsc`, 77 Einheitentests und 139 Browsertests grün, in `sla.test.mjs` neu die Grenze von 200 und die Ablehnung der leeren Uhr. Nicht lokal geprüft: Lifecycle Skript und Abbild ohne Docker.

**Bekannte Grenzen:** H3 und H4 bleiben Hinweise. Zyklen, deren Stufen vor dieser Änderung nicht beim Anhalten gespeichert wurden, erhalten nach dem Fortsetzen weiter den Zeitpunkt des Fortsetzens.

**Nachprüfung:** Codex, Sitzung codex-20261101-task071-review-01, am festen Head 8602afe, Bericht [docs/REVIEW_TASK-071_PR-225.md](docs/REVIEW_TASK-071_PR-225.md): F1 bis F3 sowie H1, H2 und H5 behoben, kein neuer Befund, Empfehlung zur Abnahme; Hinweise H1 bis H3 ohne Handlungsbedarf für diese Aufgabe.

**Nächster Schritt:** Abnahme durch Holger zusammen mit TASK-068 und TASK-070.

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

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261030-sla-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-HD-003 (SLA Fristen), REQ-HD-004 (Geschäftszeiten), ACC-002; DEC-032; OPEN-008. **Abhängigkeiten:** TASK-054 bis TASK-056 (Hintergrundaufträge) und TASK-057 bis TASK-064 (Helpdesk über Mail), abgenommen; TASK-044 (Projektkonfiguration), abgenommen. **Arbeitsstand:** Holger hat am 30. Oktober 2026 im Projektgespräch „nach H7 bitte A10 und A6 parallel“ das Paket A10 beauftragt und am selben Tag die 20 Vorschläge zu OPEN-008 mit „Ja, so übernehmen“ bestätigt (Punkte unten). Reservierung PR #220 gemergt, Ergebnis auf Branch claude/project-thread-2gq3w2 mit DEC-098 und Migration V0030 (V0029 belegt TASK-067, V0031 ist mit der Zeiterfassung A6 abgestimmt).

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

**Ergebnis:** DEC-098. Neues Modul `sla` mit Kalendern, Regeln, Priorität und Zyklen mit je einer Uhr für Erstreaktion und Lösung, Migration V0030, Vertrag [contracts/sla.openapi.yaml](contracts/sla.openapi.yaml) und Beschreibung in [backend/README.md](backend/README.md#servicefristen-task-068). Der Helpdesk meldet über neue öffentliche Ereignisse Kunde gesetzt oder entfernt (`TicketCustomerChanged`), Antwort gesendet (`ReplySent`) und Kundenantwort (`CustomerAnswered`) und stellt `CustomerDirectory` bereit; Tickets bekommen das getrennt geführte Feld `priority`, die Projektkonfiguration je Status die SLA Art. Oberfläche: Seite „Servicefristen“ für `sla.manage` mit Kalendern und Regeln, Abschnitt „Priorität und Fristen“ am Ticket, SLA Art je Status in den Projekteinstellungen, Bezeichnungen in Gruppen und Historie. Demodaten mit Kalender „Bürozeiten“, Regel „Helpdesk Standard“ (120 und 480 Minuten) und drei Tickets mit Kunde in HELP.

**Prüfungen:** Backend vollständig gegen PostgreSQL 16 lokal grün (54 Testklassen, 443 Tests), darunter `SlaCalculationTest` (24 Tests: S-01 bis S-14, H-09, H-10, H-12, Zeitumstellung, Überlappungen, Prüfungen der Eingaben), `SlaApiTest` (10 Tests: Rechte, Versionen, gleichzeitige Regeln, genaueste Regel, Start bei Anlage, Pause und Kundenantwort, Lösung und neuer Zyklus, Abbruch, Entfernen des Kunden, Prioritätswechsel mit Historie, Feldregeln, gleichzeitige Änderungen), `SlaContractTest` (11 Routen) und ein Test über den echten Mailweg in `HelpdeskApiTest` (Start beim Speichern der Mail, Erstreaktion erst mit gesendeter Antwort). Frontend `tsc`, 77 Einheitentests und 135 Browsertests grün, davon 4 neue in `sla.test.mjs`. Die Bildprüfung mit den Demodaten läuft erst in der CI. Nicht geprüft: Lifecycle Skript und Abbild lokal ohne Docker.

**Bekannte Grenzen:** Eine geänderte SLA Art wirkt beim nächsten Statuswechsel eines Tickets; eine später angelegte Regel gilt nicht für Tickets, deren Kunde schon gesetzt war; ein Kundenwechsel von A nach B behält den laufenden Zyklus; laufende Zyklen werden nicht neu bewertet.

**Nächster Schritt:** Technische Prüfung (Codex oder eine getrennte Claude Prüfung) und Abnahme durch Holger; danach TASK-070.

## TASK-070 SLA Eskalationen (A10 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261030-sla-01 · **Aktualisiert:** 2026-10-31

**Bezug:** REQ-HD-005 (Eskalationen), ACC-002; OPEN-008. **Abhängigkeiten:** TASK-068 geliefert (PR #222 auf main, Prüfung und Abnahme offen). **Arbeitsstand:** Mit TASK-068 von Holger am 30. Oktober 2026 beauftragt und mit den Vorschlägen 17 bis 20 bestätigt. Nach dem Merge von PR #222 am 31. Oktober 2026 in derselben Sitzung ohne neue Reservierung umgesetzt, Branch claude/project-thread-2gq3w2 mit DEC-100 und Migration V0032.

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

**Ergebnis:** DEC-100. Stufen je Regelversion (`escalations` mit Uhr, Prozent und Gruppe), wiederkehrende Aufgabe `sla.watch` jede Minute, Tabelle `sla_escalation` mit einer Zeile je Zyklus, Uhr und Stufe, übersprungene Stufen als eigene Zeilen mit Protokoll, Port `GroupDirectory` des Berechtigungsmoduls, Liste `GET /api/v1/sla/escalations` und erreichte Stufen in den Zyklen der Ticketansicht; Migration V0032, Vertrag [contracts/sla.openapi.yaml](contracts/sla.openapi.yaml), Beschreibung in [backend/README.md](backend/README.md#eskalationen-task-070). Oberfläche: Stufen im Regelformular mit dem Vorschlag 80 und 100 Prozent je Uhr für die erste Gruppe, neue Seite „SLA Eskalationen“ in der Hauptnavigation, Hinweis im Abschnitt „Priorität und Fristen“ am Ticket. Demodaten: Regel „Helpdesk Standard“ mit 80 % für Projektleitung und 100 % für Verwaltung Demo je Uhr.

**Prüfungen:** Backend vollständig gegen PostgreSQL 16 lokal grün (55 Testklassen, 453 Tests), darunter `EscalationsTest` (6 Tests: S-13, H-11, Schwelle über Nacht und nach einer Pause, pausierte und erfüllte Uhr, gleicher Anteil für zwei Gruppen und Rundung, Prüfung der Stufen) und vier neue Tests in `SlaApiTest` (Prüfung und Versionen der Stufen, Überwachung einmal je Stufe mit Rechten beim Lesen und ohne Eingriff am Ticket, vier gleichzeitige Läufe nach einem Ausfall mit nur der höchsten Stufe, Pause und Erfüllung vor der Schwelle und erhaltene Stufe nach Abbruch), `SlaContractTest` mit 12 Routen. Frontend `tsc`, 77 Einheitentests und 138 Browsertests grün, davon 3 neue in `sla.test.mjs`. Nicht geprüft: Lifecycle Skript und Abbild lokal ohne Docker, die laufen in der CI.

**Bekannte Grenzen:** Keine Mail und keine Benachrichtigung, die Liste lädt beim Öffnen und mit „Aktualisieren“; bis zu einer Minute Verzug; eine gelöschte Gruppe bleibt in den Stufen stehen und erreicht niemanden; nach einem Prioritätswechsel gelten die Schwellen des neuen Budgets, gespeicherte Stufen bleiben.

**Nächster Schritt:** Technische Prüfung (Codex oder eine getrennte Claude Prüfung) und Abnahme durch Holger, zusammen mit oder nach TASK-068.
