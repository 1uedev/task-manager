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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 8 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

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
| [TASK-081](#task-081-auftritt-für-anmeldung-und-portal-a17-teil-1) | Auftritt für Anmeldung und Portal (A17 Teil 1) | claude-20261102-branding-01 | 2026-11-02 |
| [TASK-087](#task-087-befunde-der-prüfung-von-task-081-beheben-a17) | Befunde der Prüfung von TASK-081 beheben (A17) | claude-20261102-branding-01 | 2026-11-04 |
| [TASK-088](#task-088-befunde-der-prüfung-von-task-067-beheben) | Befunde der Prüfung von TASK-067 beheben | claude-20261104-newanswersfix-01 | 2026-11-04 |
| [TASK-089](#task-089-befunde-der-zweiten-nachprüfung-von-task-087-beheben-a17) | Befunde der zweiten Nachprüfung von TASK-087 beheben (A17) | claude-20261102-branding-01 | 2026-11-06 |
| [TASK-090](#task-090-befunde-der-prüfung-von-task-089-beheben-a17) | Befunde der Prüfung von TASK-089 beheben (A17) | claude-20261102-branding-01 | 2026-11-08 |
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

**Nachbesserung nach der Codex Nachprüfung (docs/REVIEW_TASK-087_PR-259.md, 4. November 2026, Empfehlung „nachbessern“) im selben PR #259:**
1. R1: „Aktuellen Stand laden“ nach einem Versionskonflikt übernimmt den gespeicherten Wert nur für den Teil, dessen Speichern scheiterte; die ungespeicherte Eingabe der anderen Teile bleibt. Browsertest „after a conflict on the name the current version is loaded and the unsaved color stays“, der ohne die Korrektur scheitert.
2. R2: Zurücksetzen im Standardzustand, Logo entfernen ohne Logo und dasselbe Logo noch einmal lassen Version und Audit stehen wie ein `PUT` ohne Änderung. API Test `resetRemoveAndTheSameLogoAgainChangeNothingWhenNothingChanges`, der ohne die Korrektur scheitert.
3. H1: Der Testersatz behandelt `null` wie einen nicht gesendeten Teil, antwortet auf eine Anfrage ohne Teile mit 400 `appearance.change_empty` und prüft die Werte vor der Version wie der Server. H2: `MailTemplatePreviewRequest.name` beschreibt die Normalisierung statt einer Länge am Rohwert (Vertrag appearance 0.3.2).

**Nächster Schritt:** Die zweite Codex Nachprüfung (docs/REVIEW_TASK-087_PR-259_2.md, 6. November 2026) empfiehlt „nachbessern“; die Befunde behebt TASK-089. Abnahme durch Holger danach zusammen mit TASK-081 und TASK-089.

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

**Nächster Schritt:** Kurze Nachprüfung durch eine zweite KI, danach Abnahme von TASK-067, TASK-088 und TASK-091 durch Holger.

## TASK-089 Befunde der zweiten Nachprüfung von TASK-087 beheben (A17)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-branding-01 · **Aktualisiert:** 2026-11-06

**Bezug:** REQ-BRAND-001, REQ-UX-001; DEC-117; docs/REVIEW_TASK-087_PR-259_2.md. **Abhängigkeiten:** TASK-087 geliefert (PR #259). **Arbeitsstand:** Holger hat am 6. November 2026 den Codex Bericht der zweiten Nachprüfung von TASK-087 (Empfehlung „nachbessern“) in den Thread gestellt und am selben Tag auf der Entscheidungskarte „S1, S2, H1 und H2 beheben“ gewählt. Branch claude/project-thread-tzp0cw; ohne Migration.

**Umfang:** Befunde S1 und S2 und Hinweise H1 und H2 aus docs/REVIEW_TASK-087_PR-259_2.md:
1. S1: „Aktuellen Stand laden“ nach einem Versionskonflikt ist an genau diesen Ladevorgang gebunden. Auch eine Antwort mit gleicher Version schließt ihn ab; scheitert das Laden, bleiben die Eingaben und eine wiederholbare Fehlermeldung, und kein offener Auftrag wirkt auf eine spätere Ladung oder Teiländerung.
2. S2: Der Testersatz antwortet auf falsche JSON Typen bei Name und Farbe mit einer regulären Fehlerantwort wie der Server.
3. H1: Ein API Test mit gleichzeitigen unveränderten Anfragen zum Zurücksetzen, Entfernen und gleichen Logo sowie mit veralteter Version.
4. H2: Der Testersatz prüft beim Logo den angegebenen Typ wie der Server, bevor er Inhalte vergleicht.

Nicht Teil: weitere Änderungen am Auftritt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. S1 mit Browsertests für gleiche Version, Ladefehler und eine danach folgende Bearbeitung eines anderen Teils; S2 und H2 mit Browsertests oder direkten Aufrufen des Testersatzes; H1 mit einem API Test gegen die echte Datenbank.
2. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-06):**
1. Ergebnis: DEC-119, ohne Migration. S1: `AppearancePage.tsx` liest nach „Aktuellen Stand laden“ den Auftritt selbst (`loadCurrent`) und übernimmt genau diese Antwort nur für den gescheiterten Teil (`take`), auch bei gleicher Version, mit der Meldung „Der aktuelle Stand ist geladen. Ungespeicherte Eingaben anderer Teile bleiben erhalten.“; ein Lesefehler zeigt „Erneut versuchen“, alle Eingaben bleiben, kein Auftrag wirkt später nach. Der Provider wird dafür nicht mehr neu geladen. S2: Testersatz für Objekte, Listen, `null`, Zahlen und Wahrheitswerte wie der Server, festgehalten in `AppearanceApiTest.wrongJsonTypesAreRefusedLikeTheTestStandIn`. H1: `concurrentRequestsWithoutAChangeLeaveVersionAndAuditAndAStaleOneIsRefused`. H2: Testersatz prüft Leere, Größe, erkannten und angegebenen Typ des Logos vor der Version.
2. Nachweise (lokal, JDK 21, PostgreSQL 16): die beiden neuen Browsertests zu S1 scheitern mit der Seite von main und laufen mit der Korrektur; die Ergebnisse der Gesamtläufe stehen im Ergebnis PR.
3. Grenzen: Der Testersatz prüft das Logo nicht durch Dekodieren (`appearance.logo_unreadable` bleibt dem Server vorbehalten).

**Nächster Schritt:** Die Codex Prüfung (docs/REVIEW_TASK-089_PR-263.md, 6. und 8. November 2026) empfiehlt „nachbessern“; T1, T2 und die Hinweise 1 und 2 behebt TASK-090. Abnahme durch Holger danach zusammen mit TASK-081, TASK-087 und TASK-090.

## TASK-090 Befunde der Prüfung von TASK-089 beheben (A17)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-branding-01 · **Aktualisiert:** 2026-11-08

**Bezug:** REQ-BRAND-001, REQ-UX-001; DEC-119, DEC-120; docs/REVIEW_TASK-089_PR-263.md, docs/REVIEW_TASK-090_PR-268.md. **Abhängigkeiten:** TASK-089 geliefert (PR #263). **Arbeitsstand:** Holger hat am 8. November 2026 den Codex Bericht zu TASK-089 (Empfehlung „nachbessern“) in den Thread gestellt und am selben Tag auf der Entscheidungskarte „T1, T2 und beide Hinweise beheben“ gewählt. Branch claude/project-thread-tzp0cw; ohne Migration.

**Umfang:** Befunde T1 und T2 und die Hinweise 1 und 2 aus docs/REVIEW_TASK-089_PR-263.md:
1. T1: Das Laden nach einem Versionskonflikt ist an die Lebensdauer der Seite gebunden. Eine Antwort, die nach dem Verlassen der Seite eintrifft, übernimmt nichts, auch nicht in den gemeinsamen Auftritt.
2. T2: Logo Ablage und Dateiauswahl beachten dieselbe Sperre wie die Schaltflächen, und eine überholte Antwort überschreibt keinen neueren Stand.
3. Hinweis 1: Der Test gleichzeitiger unveränderter Anfragen wartet auf die Bereitschaft beider Aufträge und belegt das Warten auf die Sperre der Zeile; der Kommentar beschreibt genau das.
4. Hinweis 2: Dauerhafte Tests für Wahrheitswerte in Name und Farbe am Server und für den Vorrang des Logotypfehlers vor einer veralteten Version im Testersatz.

Nicht Teil: weitere Änderungen am Auftritt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. T1 und T2 mit Browsertests, die die alte Antwort erst nach einem neueren gespeicherten Stand freigeben und prüfen, dass Name, Logo, Farbe und Version den neueren Stand behalten; dazu ein Test, dass eine Logo Ablage während des Ladens keine Änderung sendet.
2. Hinweise 1 und 2 mit API und Browsertests.
3. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-08):**
1. Ergebnis: DEC-120, ohne Migration. T1: `AppearancePage.tsx` übernimmt nach dem Verlassen der Seite keine Antwort mehr (`alive`), und `BrandProvider.accept` verwirft eine Antwort mit älterer Version als der bekannten. T2: Alle Schritte der Seite, auch Logo Ablage und Dateiauswahl, laufen über dieselbe Sperre (`working`, `begin`, `end`); eine Ablage während eines Vorgangs sendet nichts. Hinweis 1: `AppearanceApiTest.together` hält die Zeile gesperrt, wartet auf die Bereitschaft beider Aufträge und gibt die Sperre erst frei, wenn `pg_stat_activity` beide Anfragen wartend zeigt. Hinweis 2: Wahrheitswerte bei Name und Farbe und der Vorrang von 415 vor 412 beim Logo in `wrongJsonTypesAreRefusedLikeTheTestStandIn` und im Browsertest des Testersatzes.
2. Nachweise (lokal, JDK 21, PostgreSQL 16): die Browsertests „a late answer of a page that was left takes nothing and keeps the newer saved version“ und „no logo can be dropped while the current version is loading and the old answer takes nothing back“ scheitern mit der Seite von main und laufen mit der Korrektur; die Ergebnisse der Gesamtläufe stehen im Ergebnis PR.
3. Grenzen: Ein Lesevorgang wird beim Verlassen der Seite nicht abgebrochen; seine Antwort ändert die Seite nicht mehr, eine gleiche oder neuere Version erreicht aber den gemeinsamen Auftritt. Eine Ablage während eines Vorgangs wird ohne eigene Meldung übergangen; die Seite zeigt den laufenden Vorgang durch gesperrte Schaltflächen.
4. Nachtrag nach der Claude Prüfung (docs/REVIEW_TASK-090_PR-268.md, Holgers Wahl „B1 beheben lassen“ vom 8. November 2026): B1: Ein erfolgreiches Speichern oder Laden, dessen Antwort nach dem Verlassen der Seite eintrifft, geht weiter an `accept` (`keep`), nur die Zustände der Seite bleiben unberührt; Browsertest „a save whose answer arrives after leaving the page is kept for the next visit“ scheitert ohne die Behebung. Hinweis 1: `newerOrSame` und `etagVersion` liegen in `brand.ts` mit eigenem Test in tests/brand.test.ts. Hinweis 2: Das eigene Laden des Providers nutzt denselben Vergleich. Hinweis 3: `together` nennt die Grenze durch `lock_timeout` von 5 Sekunden, gibt die Sperre vor dem Pool frei und öffnet den Start auch nach einem Fehlschlag. Dazu `{"name":false}` im Testersatz.

**Nächster Schritt:** Prüfung durch eine zweite KI, danach Abnahme von TASK-081, TASK-087, TASK-089 und TASK-090 durch Holger.
