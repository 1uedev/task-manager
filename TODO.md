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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 5 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

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
| [TASK-072](#task-072-tag-katalogpflege-und-zusammenführen-a3-teil-1) | Tag Katalogpflege und Zusammenführen (A3 Teil 1) | claude-20261101-tags-01 | 2026-11-01 |
| [TASK-073](#task-073-tag-massenänderung-und-filter-a3-teil-2) | Tag Massenänderung und Filter (A3 Teil 2) | claude-20261101-tags-01 | 2026-11-01 |
| [TASK-081](#task-081-auftritt-für-anmeldung-und-portal-a17-teil-1) | Auftritt für Anmeldung und Portal (A17 Teil 1) | claude-20261102-branding-01 | 2026-11-02 |
| [TASK-082](#task-082-mailvorlage-für-antworten-an-kunden-a17-teil-2) | Mailvorlage für Antworten an Kunden (A17 Teil 2) | claude-20261102-branding-01 | 2026-11-03 |
| [TASK-085](#task-085-befunde-der-prüfung-von-task-082-beheben-a17) | Befunde der Prüfung von TASK-082 beheben (A17) | claude-20261102-branding-01 | 2026-11-03 |

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

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme durch Holger; danach TASK-082 in derselben Sitzung, sobald das Ergebnis auf main ist.

## TASK-082 Mailvorlage für Antworten an Kunden (A17 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-branding-01 · **Aktualisiert:** 2026-11-03

**Bezug:** REQ-BRAND-001, REQ-BRAND-002, REQ-I18N-001, REQ-HD-001; OPEN-015; DEC-091; docs/WIKI_I18N_BRANDING.md (Logo, Farben und Vorlagen, Prüffälle W15 und W16). **Abhängigkeiten:** TASK-081 auf main, TASK-058 geliefert. **Arbeitsstand:** Mit TASK-081 von Holger am 2. November 2026 beauftragt und mit den Vorschlägen bestätigt; begonnen am 3. November 2026, nachdem das Ergebnis von TASK-081 auf main war (PR #243). Branch claude/project-thread-tzp0cw, Entscheidung DEC-112, Migration V0049 (V0047 und V0048 belegt TASK-083).

**Umfang:** Vorlage „Antwort an Kunden“ als Rahmen um den Antworttext in Deutsch und Englisch, Platzhalterkatalog, Adresse der Anwendung, Sprache am Kontakt, Versand als HTML und Klartext mit eingebettetem Logo, eingebaute Rückfallvorlage, Festhalten der fertigen Mail bei der Bestätigung, Vorschau, Demodaten, mit Migration, Vertrag, Oberfläche und Tests. Nicht Teil: PDFs, weitere Mailarten wie Benachrichtigungen, Vorlagen je Projekt oder Postfach, freies HTML, CSS oder Skripte, Änderung des Betreffs durch die Vorlage.

**Fachliche Regeln** (von Holger am 2. November 2026 bestätigt):
1. Die Vorlage „Antwort an Kunden“ umfasst Kopf mit Logo und Name, Anrede, den Antworttext des Supports, Signatur und Fußzeile. Der Betreff bleibt im Antwortformular.
2. Bearbeitbar sind Anrede, Signatur und Fußzeile je Sprache als einfacher Text mit Platzhaltern und Zeilenumbrüchen, je Teil höchstens 2 000 Zeichen. Das HTML entsteht aus einem festen Layout.
3. Platzhalter sind nur `{{company.name}}`, `{{contact.name}}`, `{{ticket.number}}`, `{{ticket.title}}`, `{{sender.name}}`, `{{mailbox.address}}` und `{{portal.url}}`. Ein unbekannter Platzhalter wird beim Speichern mit 400 und benanntem Platzhalter abgelehnt. Werte werden für HTML maskiert.
4. Ein optionales Feld „Adresse der Anwendung“ (nur https) liefert `{{portal.url}}`; ohne Adresse entfällt der Link.
5. Kontakte haben die Sprache Deutsch oder Englisch, Vorgabe Deutsch, änderbar in der Kundenpflege. Die Mail folgt der Sprache des Kontakts, sonst Deutsch, unabhängig von der Oberflächensprache der antwortenden Person.
6. Jede Antwort geht als HTML und Klartext derselben Nachricht. Das Logo ist eingebettet, nicht nachgeladen; ohne Logo steht dort der Name. Der Antworttext wird maskiert, Zeilenumbrüche bleiben erhalten.
7. Eine eingebaute Vorlage in Deutsch und Englisch gilt, solange nichts gespeichert ist; „Zurücksetzen“ kehrt zu ihr zurück. Gespeicherte Vorlagen sind beim Speichern geprüft.
8. Die Prüfung vor dem Senden zeigt die fertige Mail; beim Bestätigen wird sie mit der dann gültigen Vorlage festgehalten. Spätere Änderungen von Vorlage oder Auftritt ändern wartende oder erneut versuchte Sendungen nicht.
9. Die Vorlage pflegt, wer `appearance.manage` hat; die Vorschau nutzt nur synthetische Daten. Version mit If-Match und Audit wie beim Auftritt.
10. Das Vorführimage erhält eine Beispielsignatur, der Kontakt von Südtechnik spricht Englisch.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Eine Antwort an einen deutschen und einen englischen Kontakt geht als HTML und Klartext mit Logo, Name und gespeicherter Vorlage in der jeweiligen Sprache; ohne gespeicherte Vorlage gilt die Rückfallvorlage.
2. Unbekannte Platzhalter werden abgelehnt (W15); Werte mit HTML Zeichen erscheinen maskiert; die Vorschau zeigt keine echten Daten.
3. Eine nach der Bestätigung geänderte Vorlage ändert eine wartende oder erneut versuchte Sendung nicht.
4. Migration, Vertrag gegen Routen, Backend Tests mit dem SMTP Testserver, Browser Tests für Vorlagenpflege, Kontaktsprache und Vorschau.

**Übergabe zur Prüfung (2026-11-03):**
1. Ergebnis: V0049 mit `appearance_mail_template`, V0050 mit der Sprache am Kontakt, `helpdesk_reply_mail` und `helpdesk_reply_image`. `GET`, `PUT`, `DELETE /api/v1/appearance/mail-template` und `POST /api/v1/appearance/mail-template/preview` mit `appearance.manage`, Version und Audit; Prüfung von Länge, Steuerzeichen, Platzhaltern (400 `mail_template.placeholder_unknown` mit Sprache, Teil und dem Geschriebenen) und Adresse. Port `MailTemplates` im Erscheinungsbild, Port `AccountNames` in der Identität, `TicketAccess.title`. `POST /api/v1/projects/{id}/tickets/{n}/helpdesk/reply/preview` zeigt die fertige Mail vor der Bestätigung; die Bestätigung hält Klartext, HTML und Logo fest; `SmtpMailSender` sendet `multipart/alternative` mit dem Logo als `cid:logo`. Oberfläche: Abschnitt „Mailvorlage“ auf der Seite „Erscheinungsbild“ mit Sprachwahl, drei Feldern mit Zeichenzähler, Adresse, Platzhalterliste, Vorschau mit synthetischen Daten im ungespeicherten Namen und Farbton, Speichern und Rückkehr zur eingebauten Vorlage mit Rückfrage; Sprache in der Kontaktpflege; die Prüfung einer Antwort zeigt die fertige Mail mit Sprache und Klartext. Demo: Signatur je Sprache, Kontakt von Südtechnik auf Englisch.
2. Nachweise (lokal, JDK 21, PostgreSQL 16): `./mvnw verify` 515 Tests ohne Fehler, darunter neu in `AppearanceApiTest` vier Tests zur Vorlage (eingebaute Vorlage, Speichern, Zurücksetzen, Ablehnungen, Vorschau, Recht) und in `HelpdeskApiTest` zwei Tests (Antwort als HTML und Klartext mit Logo über den SMTP Testserver, festgehaltene Mail nach Vorlagenänderung und beim erneuten Senden, englischer Kontakt, Sprache am Kontakt). Frontend `npm run verify` mit 79 Tests, `npm run test:browser` 179 von 179. Repositoryprüfungen grün. Nicht lokal: `lifecycle-check.sh` mit Docker (läuft in CI).
3. Grenzen: Das HTML der Mail erscheint in der Anwendung nicht selbst, weil die Inhaltsrichtlinie Stile im Element verbietet; die Vorschau zeigt dieselben gefüllten Teile als Text und den Klartext. Eine zwischen Prüfung und Bestätigung geänderte Vorlage gilt mit der Bestätigung. Antworten vor V0050 gehen weiter als reiner Text. Ohne Leserecht auf den Titel bleibt `{{ticket.title}}` leer.

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme durch Holger.

## TASK-085 Befunde der Prüfung von TASK-082 beheben (A17)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-branding-01 · **Aktualisiert:** 2026-11-03

**Bezug:** REQ-BRAND-002, REQ-HD-001, REQ-UX-001; DEC-112; docs/REVIEW_TASK-082_PR-247.md. **Abhängigkeiten:** TASK-082 geliefert (PR #247). **Arbeitsstand:** Holger hat am 3. November 2026 den Codex Prüfbericht zu TASK-082 (Empfehlung „nachbessern“) in das Projektgespräch gestellt; die Behebung wurde dort angekündigt, PR #247 wurde vorher gemergt. Reservierung mit PR #248 gemergt. Ergebnis auf Branch claude/project-thread-tzp0cw mit DEC-115, ohne Migration.

**Umfang:** Befunde F1 und F2 und Hinweise H1 bis H3 aus docs/REVIEW_TASK-082_PR-247.md:
1. F1: die Bestätigung einer Antwort sendet genau die geprüfte Fassung (Postfach, Empfänger, Betreff, Text); eine Prüfung, die nach einer Änderung, einem Abbruch oder einer neuen Prüfung antwortet, wird verworfen.
2. F2: das Schema der Adresse der Anwendung gilt in jeder Schreibweise und wird kleingeschrieben gespeichert, damit Vorschau, Speichern und die Prüfung von V0049 übereinstimmen; V0049 bleibt unverändert.
3. H1: der Fokus geht nach dem Speichern der Vorlage auf die Bestätigung, beim Prüfen einer Antwort auf die fertige Mail und beim Zurückgehen auf „Antwort prüfen“.
4. H2: die Vorlagenvorschau lehnt eine fehlende oder andere Sprache als `de` oder `en` mit 400 `mail_template.languages_missing` ab, im Vertrag und im Testersatz ebenso.
5. H3: der Browsertest des Portalkopfs aus TASK-081 wartet auf den geladenen Auftritt statt den Standard zu lesen.
6. Nachtrag aus dem Faden zu A11: die auf main wackelnden Farbtests der Seite „Erscheinungsbild“ warten auf den geladenen Stand; dabei gefunden, dass eine halb eingetippte Farbe vom spät geladenen gespeicherten Auftritt überschrieben wurde, das ist mitbehoben.

Nicht Teil: weitere Funktionen der Mailvorlage.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 ist mit einem Browsertest mit gehaltener Prüfung und zwischenzeitlicher Eingabe belegt, F2 und H2 mit Backendtests, H1 mit Fokusprüfungen im Browser.
2. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-03):**
1. Ergebnis: `TicketHelpdesk.tsx` hält den geprüften Entwurf fest, die Bestätigung sendet genau ihn, jede Änderung, jeder Abbruch und jede neue Prüfung zählt eine Runde hoch und eine ältere Antwort der Prüfung wird verworfen; der Fokus geht auf die fertige Mail und beim Zurückgehen auf „Antwort prüfen“. `MailTemplateService.address` liefert das Schema kleingeschrieben, Pfad und Abfrage bleiben; `preview` lehnt eine fehlende oder fremde Sprache mit 400 `mail_template.languages_missing` ab (Vertrag appearance ergänzt, Testersatz ebenso). `MailTemplateSection.tsx` setzt den Fokus nach dem Speichern auf die Bestätigung. `AppearancePage.tsx` zählt jede Eingabe im Farbwert als Änderung. Browsertests für Portalkopf und Farbwert warten auf den geladenen Auftritt.
2. Nachweise (lokal, JDK 21, PostgreSQL 16): `./mvnw verify` 515 Tests ohne Fehler, `AppearanceApiTest` prüft `HTTPS://` und `Https://` in der Vorschau und `HTTPS://` beim Speichern und die abgelehnte Sprache. Frontend `npm run verify` 79 Tests, `npm run test:browser` 181 von 181; neu sind der Test mit gehaltener Prüfung und zwischenzeitlicher Eingabe samt Fokus, der Fokus nach dem Speichern der Vorlage und die vor dem gespeicherten Auftritt eingetippte Farbe (ohne die Behebung rot). In weiteren Läufen fiel einmal ein Test der Hintergrundaufträge aus, unabhängig von dieser Änderung. Repositoryprüfungen grün.
3. Grenzen: V0049 bleibt unverändert; eine schon gespeicherte Adresse ist immer kleingeschrieben, weil die Datenbank anderes nie angenommen hat.

4. Nachprüfung (2026-11-03): docs/REVIEW_TASK-085_PR-250.md von Codex empfiehlt „abnehmen“. F1, F2, H1 bis H3 und der Nachtrag sind wirksam; N1 bis N3 betreffen nur Testpflege (Login Test, Verzögerungstests, Tag Testvorbereitung) und begründen keine Überarbeitung. Die Beschreibung des Backendnachweises in Punkt 2 ist nach N2 enger gefasst.

**Nächster Schritt:** Abnahme durch Holger; N1 bis N3 als eigene Testpflege bei Gelegenheit.
