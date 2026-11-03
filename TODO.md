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
| Durch eine KI reserviert (IN ARBEIT) | 2 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 0 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 11 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-067](#task-067-neue-kundenantworten-an-bestehenden-tickets-sichtbar-machen) | Neue Kundenantworten an bestehenden Tickets sichtbar machen | claude-20261030-newanswers-01 | 2026-10-30 |
| [TASK-084](#task-084-befund-der-prüfung-von-task-083-beheben-a11) | Befund der Prüfung von TASK-083 beheben (A11) | claude-20261103-portal-05 | 2026-11-03 |

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-072](#task-072-tag-katalogpflege-und-zusammenführen-a3-teil-1) | Tag Katalogpflege und Zusammenführen (A3 Teil 1) | claude-20261101-tags-01 | 2026-11-01 |
| [TASK-073](#task-073-tag-massenänderung-und-filter-a3-teil-2) | Tag Massenänderung und Filter (A3 Teil 2) | claude-20261101-tags-01 | 2026-11-01 |
| [TASK-075](#task-075-portalkonten-sicht-und-rechte-a11-teil-1) | Portalkonten, Sicht und Rechte (A11 Teil 1) | claude-20261102-portal-01 | 2026-11-02 |
| [TASK-076](#task-076-portalgespräch-anhänge-und-portalseiten-a11-teil-2) | Portalgespräch, Anhänge und Portalseiten (A11 Teil 2) | claude-20261102-portal-01 | 2026-11-02 |
| [TASK-077](#task-077-tags-im-portal-a11-teil-3) | Tags im Portal (A11 Teil 3) | claude-20261102-portal-01 | 2026-11-02 |
| [TASK-078](#task-078-befunde-der-prüfung-von-task-075-beheben-a11) | Befunde der Prüfung von TASK-075 beheben (A11) | claude-20261102-portal-01 | 2026-11-02 |
| [TASK-079](#task-079-befunde-der-prüfung-von-task-076-bis-task-078-beheben-a11) | Befunde der Prüfung von TASK-076 bis TASK-078 beheben (A11) | claude-20261102-portal-02 | 2026-11-02 |
| [TASK-080](#task-080-befunde-der-prüfung-von-task-079-beheben-a11) | Befunde der Prüfung von TASK-079 beheben (A11) | claude-20261102-portal-03 | 2026-11-02 |
| [TASK-081](#task-081-auftritt-für-anmeldung-und-portal-a17-teil-1) | Auftritt für Anmeldung und Portal (A17 Teil 1) | claude-20261102-branding-01 | 2026-11-02 |
| [TASK-082](#task-082-mailvorlage-für-antworten-an-kunden-a17-teil-2) | Mailvorlage für Antworten an Kunden (A17 Teil 2) | claude-20261102-branding-01 | 2026-11-03 |
| [TASK-083](#task-083-befunde-der-prüfung-von-task-080-beheben-a11) | Befunde der Prüfung von TASK-080 beheben (A11) | claude-20261103-portal-04 | 2026-11-03 |

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

## TASK-075 Portalkonten, Sicht und Rechte (A11 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-portal-01 · **Aktualisiert:** 2026-11-02

**Bezug:** REQ-PORTAL-001, REQ-PORTAL-003, REQ-ACL-002, REQ-AUTH-001, REQ-API-001; DEC-027, DEC-031, DEC-053; docs/PERMISSIONS.md (Organisationssicht, Fälle 1 bis 8), docs/HELPDESK_SLA.md (Kundenportal). **Abhängigkeiten:** TASK-024, TASK-025, TASK-026, TASK-027, TASK-045, TASK-057, abgenommen. **Arbeitsstand:** Holger hat am 2. November 2026 im Projektgespräch mit „ja“ den Beginn von Paket A11 nach A3 bestätigt und am selben Tag die 20 Vorschläge mit „passt so“ übernommen (Punkte unten). Reservierung mit PR #232 gemergt. Ergebnis auf Branch claude/project-thread-houava mit DEC-105 (DEC-104 bleibt für die Korrekturen aus A3 frei) und den Migrationen V0034 bis V0036; Beschreibung in backend/README.md (Abschnitt TASK-075).

**Umfang:** Portalkonten mit fester Kundenorganisation, Projektfreigabe, Standardgruppe „Portal“, Organisationssicht im Rechtekern für Tickets und ihre Daten, Lesen der Anfragen über die API und die Kontenverwaltung dafür. Nicht Teil: Gespräch und Portalseiten (TASK-076), Tags im Portal (TASK-077), Wissen im Portal (A12), Selbstregistrierung, anonyme Webformulare, Passwort vergessen per Mail, zweiter Faktor, KI im Portal (DEC-035), Mailbenachrichtigungen, eigene Portalformulare, Sicht über die eigene Organisation hinaus, eigenes Portalbranding (A17).

**Fachliche Regeln** (von Holger am 2. November 2026 bestätigt):
1. Ein Portalkonto ist ein lokales Konto der Art „Portal“ mit genau einer Kundenorganisation, angelegt mit `accounts.manage` mit Einmalpasswort und Pflichtwechsel; Gültigkeit und Erneuerung des Einmalpassworts, Sperre nach Fehlversuchen und Sitzungsdauer wie bei internen Konten.
2. Die Organisation eines Portalkontos ist unveränderlich; ein Portalkonto wird nie zu einem internen Konto oder Verzeichniskonto und umgekehrt.
3. Ein Portalkonto kann mit einem Kontakt derselben Organisation verbunden werden; dann setzt eine Portalanfrage diesen Kontakt, ohne Kontakt bleibt das Feld leer.
4. Portalkonten erhalten nie Unternehmensrechte; der Server lehnt solche Zuweisungen auch über die API ab. Portalkonten erscheinen nicht in der Auswahl für Bearbeiter.
5. Wählbar sind nur Projekte, die der Organisation zugeordnet sind; das Konto wird dort Mitglied der Gruppe „Portal“.
6. Beim ersten Portalkonto entsteht die Unternehmensgruppe „Portal“: Anfragen lesen, anlegen und beantworten; Titel, Beschreibung, Typ, Status, Priorität, Tags und Anhänge lesen; Titel, Beschreibung und Anhänge beim Anlegen schreiben. Kunde, Kontakt, Bearbeiter, eigene Felder und Fristen bleiben unsichtbar, bis die Verwaltung sie gezielt freigibt. Weitere Gruppen für Portalkonten sind möglich.
7. Ein Portalkonto sieht alle Anfragen seiner Organisation in seinen Projekten (DEC-027), auch die von Kollegen und per Mail eingegangene; andere Anfragen antworten mit 404 wie eine unbekannte Kennung, auch bei Historie, Anhängen und Suche.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Fälle 1 bis 8 aus docs/PERMISSIONS.md als automatische Tests, soweit ihre Daten in V1 bestehen; ein Portalkonto vor dem Pflichtwechsel hat keinen Fachzugriff.
2. Unternehmensrechte, fremde Organisation oder nicht zugeordnete Projekte werden für Portalkonten abgelehnt, auch bei gleichzeitiger Änderung.
3. Entzug der Projektfreigabe oder Deaktivierung wirkt sofort auf Lesen, Suche und Anhänge.
4. Migration, Vertrag gegen Routen, Backend Tests für Rechte und Nebenläufigkeit, Browser Tests für die Kontenverwaltung.

**Übergabe zur Prüfung (2026-11-02):**
1. Ergebnis: Portalkonten über `/api/v1/portal-accounts` mit fester Organisation, optionalem Kontakt und den freigegebenen Projekten; Gruppe „Portal“ mit Zweck `portal` und den Standardregeln; Ablehnung von Unternehmensrechten im Rechtekern (409 `permissions.portal_company_right`); Organisationssicht über `/api/v1/portal/projects` und `/api/v1/portal/projects/{projectId}/tickets`; interne Pfade, Historie, Anhänge, Suche und Kontoverzeichnis bleiben verschlossen; Bearbeiter nur interne Konten. Oberfläche: Abschnitt „Portalkonten“ in der Kontenverwaltung und eine Lesesicht „Ihre Anfragen“ für Portalkonten ohne interne Navigation.
2. Nachweise: `PortalAccountsApiTest` (Fälle 1 bis 7 aus docs/PERMISSIONS.md, Pflichtwechsel, Gruppe und Unternehmensrechte, Prüfung von Organisation, Kontakt und Projekten, Entzug und Deaktivierung), `PortalContractTest` und die angepassten Vertragstests, voller Backendlauf mit PostgreSQL 16, `npm run verify` und alle Browsertests einschließlich `portal.test.mjs`.
3. Grenzen: Anlegen und Beantworten im Portal, Portalseiten mit Statusnamen und kundensichtbare Anhänge folgen mit TASK-076, Tags mit TASK-077; die Aktion zum Beantworten kommt mit TASK-076 in die Gruppe „Portal“. Ein Kontakt, der später die Organisation wechselt, und ein im Helpdesk wieder entzogenes Projekt bleiben am Konto, bis die Verwaltung es ändert. Ein eigener Test gleichzeitiger Änderungen fehlt; die Sperrreihenfolge ist in backend/README.md beschrieben. Fall 8 (Antworten des Supports) gehört zu TASK-076.

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme durch Holger; danach TASK-076 in derselben Sitzung.

## TASK-076 Portalgespräch, Anhänge und Portalseiten (A11 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-portal-01 · **Aktualisiert:** 2026-11-02

**Bezug:** REQ-PORTAL-001, REQ-HD-002, REQ-HD-003, REQ-TKT-004, REQ-ACL-003; DEC-027, DEC-035, DEC-091, DEC-098; docs/HELPDESK_SLA.md, docs/PERMISSIONS.md (Fall 8). **Abhängigkeiten:** TASK-075 geliefert. **Arbeitsstand:** Mit TASK-075 von Holger am 2. November 2026 beauftragt und mit den Vorschlägen 10 bis 16 und 20 bestätigt. Nach dem Ergebnis von TASK-075 auf main (PR #233) ohne neue Reservierung in derselben Sitzung begonnen. Ergebnis auf Branch claude/project-thread-houava mit DEC-106 und den Migrationen V0037 bis V0039; Beschreibung in backend/README.md (Abschnitt TASK-076).

**Umfang:** Anlegen und Beantworten von Anfragen im Portal, Portalantworten des Supports, Kundenverlauf, kundensichtbare Anhänge, Portalseiten und Demodaten. Nicht Teil: interne Notizen und Kommentare, Statusänderung durch den Kunden, Mailbenachrichtigungen, Tags im Portal (TASK-077).

**Fachliche Regeln** (von Holger am 2. November 2026 bestätigt):
1. Anlegen mit Titel, Beschreibung, Typ und Anhängen; Kunde und Kontakt setzt der Server aus dem Konto. Die Projektverwaltung markiert in der Projektkonfiguration die im Portal angebotenen Tickettypen; ohne markierten Typ keine Anlage.
2. Kundenverlauf je Anfrage aus Beschreibung, eingegangenen Mails der Organisation, gesendeten Mailantworten, Portalbeiträgen des Kunden und Portalantworten des Supports; interne Historie, Prüfwarteschlange, fehlgeschlagene oder unklare Mails und übrige interne Daten bleiben unsichtbar.
3. Ein Portalbeitrag des Kunden ist Text bis 100 000 Zeichen mit optionalen Anhängen und zählt wie eine Kundenantwort per Mail, auch für die Servicefristen.
4. Der Support wählt beim Antworten „Portal“ oder „Mail“; eine Portalantwort braucht `helpdesk.reply`, erscheint sofort und zählt als Erstreaktion; eine Mailantwort erscheint nach erfolgreichem Versand ebenfalls im Portal.
5. Der Kunde sieht den Status mit seinem Namen aus dem Ablauf, ändert ihn aber nicht.
6. Portalstartseite mit Nummer, Titel, Status und letzter Änderung, Filter nach Projekt, Status und offen oder erledigt, Textsuche über lesbare Felder, Markierung neuer Supportantworten seit dem letzten Öffnen.
7. Jeder Anhang ist „intern“ oder „Kunde“; Portalanhänge immer „Kunde“, interne zunächst „intern“ und mit Schreibrecht auf Anhänge umschaltbar; ein interner Anhang antwortet im Portal mit 404. Grenzen für Größe und Dateiarten wie bisher.
8. Das Vorführimage erhält Nordwerk und Südtechnik mit je einem Portalkonto.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Der Verlauf zeigt nur die genannten Teile; ein interner Anhang ist über seinen direkten Link nicht erreichbar, ein kundensichtbarer derselben Anfrage schon (Fall 8).
2. Portalbeitrag und Portalantwort wirken auf Kundenantworten und Servicefristen wie die Mailwege.
3. Migration, Vertrag gegen Routen, Backend Tests für Rechte, Zustände und Nebenläufigkeit, Browser Tests für Liste, Anlage, Verlauf, Antworten und Anhänge in Portal und interner Ansicht.

**Übergabe zur Prüfung (2026-11-02):**
1. Ergebnis: Tickettypen mit der Markierung `portal` in der Projektkonfiguration (Kästchen im Editor); Anlage im Portal mit Kunde und Kontakt aus dem Konto; Gespräch je Anfrage aus Mails der Organisation, gesendeten Mailantworten und Beiträgen; Beiträge des Kunden mit eigenen Dateien und der neuen Aktion `portal.answer` in der Gruppe „Portal“ (`CustomerAnswered`); „Im Portal antworten“ am Ticket mit den Rechten einer Mailantwort (`ReplySent`, Erstreaktion); Sichtbarkeit `internal` oder `customer` je Anhang mit Umschalter in der Ticketansicht und Portalanhängen, ein interner antwortet im Portal mit 404; Portalliste mit Filtern nach Projekt, Status, offen oder erledigt und Text, Statusnamen und Markierung neuer Antworten; Demodaten mit Nordwerk und Südtechnik und den Konten `lea.nordwerk` und `tom.suedtechnik`.
2. Nachweise: `PortalConversationApiTest` (Anlage nur mit angebotenem Typ, Verlauf ohne Internes, Fall 8 mit internem und kundensichtbarem Anhang, Umschalten, eigene Dateien je Beitrag, Filter, Markierung, gleichzeitige Beiträge mit derselben Datei, Ereignisse für die Servicefristen), angepasste Vertragstests, voller Backendlauf mit PostgreSQL 16, `npm run verify` und alle Browsertests einschließlich `portal.test.mjs` (Anlage mit Datei, Beitrag, Portalantwort und Markierung, Freigabe eines Anhangs, Filter).
3. Grenzen: Ein Beitrag ändert die Version des Tickets nicht; die Portalliste zeigt als letzte Änderung den jüngeren Wert aus Ticket und Gespräch. Anhänge aus der Zeit vor TASK-076, auch aus Mails, bleiben intern, bis jemand sie umschaltet. Die Wirkung auf die Fristen ist über dieselben Ereignisse wie bei Mails nachgewiesen, nicht mit einer eigenen Fristregel im Test. Die Portalliste filtert in der Anwendung; für sehr viele Anfragen einer Organisation ist das nicht gemessen.

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme durch Holger; danach TASK-077 in derselben Sitzung.

## TASK-077 Tags im Portal (A11 Teil 3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-portal-01 · **Aktualisiert:** 2026-11-02

**Bezug:** REQ-TAG-001, REQ-PORTAL-001, REQ-ACL-002; DEC-049, DEC-068, DEC-102; docs/TAGS_SEARCH.md (T04, T10, T18, T19). **Abhängigkeiten:** TASK-076 geliefert. **Arbeitsstand:** Mit TASK-075 von Holger am 2. November 2026 beauftragt und mit den Vorschlägen 17 bis 19 bestätigt. Nach dem Ergebnis von TASK-076 auf main (PR #234) und der Behebung aus TASK-078 (PR #236) ohne neue Reservierung in derselben Sitzung begonnen. Ergebnis auf Branch claude/project-thread-houava mit DEC-108 und der Migration V0040; Beschreibung in backend/README.md (Abschnitt TASK-077).

**Umfang:** Sichtbarkeit von Tags, kundensichtbare Tags im Portal, Anlage und Zuordnung durch berechtigte Portalnutzer. Nicht Teil: Katalogpflege im Portal, Massenänderung im Portal.

**Fachliche Regeln** (von Holger am 2. November 2026 bestätigt):
1. Tags sind „intern“ (Vorgabe) oder „Kunde“, gesetzt mit `tags.manage`; das Portal zeigt nur kundensichtbare Tags, auch in Vorschlägen, Filtern und Zählern, ohne Hinweis auf interne.
2. Anlage im Portal nur mit `tag.create` in einer Gruppe des Portalkontos, nicht in der Standardgruppe; der Tag kommt in den Projektkatalog, ist an die eigene Organisation gebunden und nur für diese und intern sichtbar; intern mit `tags.manage` für alle Kunden freigebbar, umbenennbar und zusammenführbar.
3. Portalnutzer setzen und entfernen kundensichtbare Tags an Anfragen ihrer Organisation, wenn ihre Gruppe das Feld Tags schreiben darf (Standardgruppe nicht); interne Tags derselben Anfrage bleiben unberührt.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. T04, T10, T18 und T19 als automatische Tests.
2. Zuordnung durch Portalnutzer lässt interne Tags unverändert, auch bei gleichzeitiger interner Änderung.
3. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für Anzeige, Anlage und Zuordnung im Portal und die Sichtbarkeit in der Tagpflege.

**Übergabe zur Prüfung (2026-11-02):**
1. Ergebnis: Sichtbarkeit `internal` oder `customer` je Tag mit `tags.manage`, Bindung eines im Portal angelegten Tags an die Organisation und Freigabe für alle Kunden (`forAllCustomers`), Namen je Katalog und Zielgruppe; Portalpfade `/api/v1/portal/projects/{projectId}/tags` und `.../tickets/{number}/tags` zum Lesen, Setzen, Anlegen und Entfernen sichtbarer Tags; Tagfilter `tag` der Portalliste über den Port `TicketTagFilter`. Oberfläche: Tags an der Anfrage im Portal mit Vorschlägen, Anlage und Entfernen bei Schreibrecht, Tagfilter auf der Startseite, Sichtbarkeit und Freigabe in der Tagpflege.
2. Nachweise: `PortalTagsApiTest` mit T04 (interne Tags weder an der Anfrage noch in Vorschlägen), T10 (Filter nur eigene Anfragen, interner oder unbekannter Tag findet nichts), T18 (Anlage und Zuordnung, gebunden an Nordwerk, für Südtechnik unsichtbar, Freigabe mit Namenskonflikt), T19 (ohne `tag.create`, ohne Schreibregel, nach Entzug der Mitgliedschaft und mit manipulierter Organisation nichts angelegt oder geändert) und Abnahmekriterium 2 (interne Tags bleiben, auch bei gleichzeitigen Zuordnungen von Portal und Support); `TagsContractTest` mit 26 Routen; voller Backendlauf mit PostgreSQL 16; `npm run verify`; alle Browsertests einschließlich `portal.test.mjs` mit drei neuen Fällen.
3. Grenzen: Die Obergrenze von 30 Tags zählt auch interne Tags der Anfrage. Der Tagfilter der Startseite erscheint nur bei einem gewählten oder einzigen Projekt. Katalogpflege und Massenänderung im Portal fehlen wie vereinbart; die Suche bleibt für Portalkonten verschlossen.

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme durch Holger.

## TASK-078 Befunde der Prüfung von TASK-075 beheben (A11)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-portal-01 · **Aktualisiert:** 2026-11-02

**Bezug:** REQ-PORTAL-001, REQ-PORTAL-003, REQ-ACL-002, REQ-AUTH-001; DEC-027, DEC-105, DEC-106; docs/REVIEW_TASK-075_PR-233.md. **Abhängigkeiten:** TASK-075 und TASK-076 geliefert (PR #233, PR #234). **Arbeitsstand:** Holger hat am 2. November 2026 im Projektgespräch per Auswahl „Beheben, Entzug wirkt sofort“ beauftragt, die Befunde der Codex Prüfung von TASK-075 zu beheben, und festgelegt, dass ein im Helpdesk entzogenes Projekt den Portalzugang sofort beendet. Reservierung mit PR #235 gemergt. Ergebnis auf Branch claude/project-thread-houava mit DEC-107, ohne Migration; Beschreibung in backend/README.md (Abschnitt TASK-078). TASK-077 erhält danach die nächste freie Entscheidungsnummer.

**Umfang:** Befunde F1 bis F6 und Hinweise H1 bis H3 aus docs/REVIEW_TASK-075_PR-233.md sowie Hinweis H5 nach Holgers Wahl:
1. F1: Detailabfrage einer fremden Anfrage antwortet auch bei gesperrter Kundenzuordnung wie eine unbekannte Nummer.
2. F2: einheitliche Sperrreihenfolge der Projekte zwischen Portal Projektliste und Kontenverwaltung, Test mit Kennungen auf beiden Seiten des Vorzeichenbits.
3. F3: die Portal Projektliste prüft Konto und Sitzung in ihrer Transaktion erneut.
4. F4: fehlendes oder auf null gesetztes Feld `projectIds` beim Ändern eines Portalkontos antwortet mit 400; eine ausdrücklich leere Liste entfernt weiter alle Projekte.
5. F5: Portalkonten erscheinen nicht in den Auswahlen für Bearbeiter und eigene Personenfelder.
6. F6: die Portalliste erreicht alle Anfragen über weitere Seiten.
7. H1 doppelte Nummer in der Portalansicht, H2 Antwort 503 im Vertrag der Portal Projektliste, H3 verständliche Gründe im Formular der Portalkonten.
8. H5: wird einer Organisation im Helpdesk ein Projekt entzogen, verlieren ihre Portalkonten den Zugang zu diesem Projekt sofort.

Nicht Teil: Tags im Portal (TASK-077) und weitere Funktionen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Jeder Befund und Hinweis aus dem Umfang ist behoben und mit einem automatischen Test belegt, soweit testbar.
2. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-02):**
1. Ergebnis: F1 sperrende Abfrage nur für die eigene Organisation, eine gehaltene fremde Zeile antwortet sofort 404; F2 und F3 neue Prüfung `Permissions.current` mit geteilter Rechtesperre und sperrender Kontoprüfung vor den Projektsperren der Portal Projektliste, Projektreihenfolge der Datenbank in Kontenverwaltung und Helpdesk; F4 400 `projectIds.required`; F5 Portalkonten nicht in „Zuständig“ und Personenfeldern; F6 und H1 weitere Seiten der Portalliste und Schlüssel einmal; H2 503 im Vertrag; H3 einzelne Gründe im Formular; H5 entzieht der Helpdesk einer Organisation ein Projekt, verlieren ihre Portalkonten dort in derselben Transaktion die Mitgliedschaft.
2. Nachweise: fünf neue Tests in `PortalAccountsApiTest` (gehaltene fremde Zeile, Sortierung gegen PostgreSQL, Deaktivierung während wartender Projektliste, fehlendes, null, leeres und 100 oder 101 Projekte, Entzug im Helpdesk); die Tests zu F1 und F3 schlagen mit dem alten Verhalten fehl. Zwei neue Browsertests in `portal.test.mjs` (51 Anfragen mit zweiter Seite und einfachem Schlüssel, keine Portalkonten in Bearbeiter und Personenfeld) und ein geschärfter (Grund bei verschobenem Kontakt). Voller Backendlauf, `npm run verify` und alle Browsertests.
3. Grenzen: Die Portal Projektliste antwortet bei abgelaufener Sitzung innerhalb des Aufrufs mit 403 `auth.forbidden`. Fall 4 bis 6 aus docs/PERMISSIONS.md als Portal Schreibfälle und der direkte Download eines internen Anhangs wurden mit TASK-076 ergänzt (Fall 8), nicht hier. Ein Kontakt, der die Organisation wechselt, bleibt wie bisher am Konto.

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme durch Holger; danach TASK-077 in derselben Sitzung.

## TASK-079 Befunde der Prüfung von TASK-076 bis TASK-078 beheben (A11)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-portal-02 · **Aktualisiert:** 2026-11-02

**Bezug:** REQ-PORTAL-001, REQ-PORTAL-003, REQ-TAG-001, REQ-ACL-002; DEC-049, DEC-106, DEC-107, DEC-108; docs/REVIEW_TASK-076-078_A11.md. **Abhängigkeiten:** TASK-076, TASK-077 und TASK-078 geliefert (PR #234, PR #237, PR #236). **Arbeitsstand:** Holger hat am 2. November 2026 im Projektgespräch beauftragt, die Befunde der Codex Prüfung von TASK-076 bis TASK-078 als eine Aufgabe zu beheben, und zu F1 per Auswahl „Nur neue Einträge“ festgelegt: Beiträge, Antworten und Dateien bleiben der Organisation zugeordnet, für die sie geschrieben wurden; nach einem Kundenwechsel sieht die neue Organisation nur, was ab dem Wechsel entsteht. Reservierung mit PR #238 gemergt. Ergebnis auf Branch claude/project-thread-houava mit DEC-109 und den Migrationen V0041 und V0042; Beschreibung in backend/README.md (Abschnitt TASK-079).

**Umfang:** Befunde F1 bis F5 und Hinweise H1 bis H3 aus docs/REVIEW_TASK-076-078_A11.md:
1. F1: nach einem Kundenwechsel sieht die neue Organisation im Portal keine Beiträge, Antworten des Supports, gesendeten Antworten und Kundendateien, die für die bisherige Organisation entstanden sind; was ab dem Wechsel entsteht, sieht sie.
2. F2: die Gelesen-Markierung quittiert nur die Antworten, die die Anzeige tatsächlich geladen hat.
3. F3: eine verspätete Folgeseite der Portalliste wird nach einem Filterwechsel verworfen.
4. F4: die Katalogliste der Tags erreicht auch gleichnamige Tags verschiedener Organisationen vollständig.
5. F5: der Tagfilter der Portalliste erreicht alle sichtbaren Tags, nicht nur die ersten 20.
6. H1: die bestätigte Grenze von 30 Tags je Anfrage zählt verborgene Tags mit; das wird dokumentiert.
7. H2: Tests mit gehaltenen Sperren für Sichtbarkeitswechsel, Archivieren und Zusammenführen während einer Portalzuordnung.
8. H3: der Testersatz des Browsers behält nach einer abgelehnten Anlage keinen Tag und prüft die Grenze wie der Server.

Nicht Teil: H4 (Seitengröße begrenzt die Arbeit am Backend noch nicht, dokumentiert) und weitere Funktionen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Jeder Befund und Hinweis aus dem Umfang ist behoben und mit einem automatischen Test belegt, soweit testbar.
2. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-02):**
1. Ergebnis: F1 Beiträge, gesendete Mailantworten und kundensichtbare Anhänge tragen die Organisation, für die sie geschrieben wurden (V0041 und V0042 mit Übernahme der Bestandsdaten); Gespräch, Aktivität und Portalanhänge zeigen nur diese, eine Datei eines früheren Kunden gibt der Support durch erneutes Umschalten auf „Kunde“ frei, die interne Liste zeigt das mit `customerCurrent`. F2 `PUT .../seen` mit `until`, dem neuesten gezeigten Eintrag. F3 Generation je erster Seite der Portalliste. F4 Katalogcursor `after` und `afterId`. F5 Suchfeld im Tagfilter der Portalliste. H1 in backend/README.md unter TASK-077 dokumentiert. H2 Tests mit gehaltenen Änderungen der Tagzeile, Entfernen eines zusammengeführten Tags antwortet 404. H3 Testersatz prüft die Obergrenze vor dem Anlegen und beachtet `limit`.
2. Nachweise: `PortalConversationApiTest` (Kundenwechsel mit allen Eintragsarten und ausdrücklicher Freigabe, Antwort zwischen Lesen und Quittieren), `PortalAudienceMigrationTest` (Bestandsdaten), `PortalTagsApiTest` (gehaltene Änderungen, Seitengrenze in gleichen Namen), sechs neue Browsertests in `portal.test.mjs`. Voller Backendlauf, `npm run verify` und alle Browsertests.
3. Grenzen: Bestandsantworten des Supports im Portal zählen zum heutigen Kunden des Tickets; Beiträge und Mailantworten lassen sich nicht für eine andere Organisation freigeben; der Lesestand vergleicht Zeitpunkte; H4 bleibt offen.

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme von TASK-075 bis TASK-079 durch Holger.

## TASK-080 Befunde der Prüfung von TASK-079 beheben (A11)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261102-portal-03 · **Aktualisiert:** 2026-11-02

**Bezug:** REQ-PORTAL-001, REQ-PORTAL-003, REQ-ACL-002; DEC-106, DEC-109; docs/REVIEW_TASK-079_PR-239.md. **Abhängigkeiten:** TASK-079 geliefert (PR #239). **Arbeitsstand:** Holger hat am 2. November 2026 im Projektgespräch per Auswahl „Beheben, unklare Altdaten verbergen“ beauftragt, die Befunde F1 bis F3 der Codex Prüfung von TASK-079 als eine Aufgabe zu beheben: Wo die frühere Organisation alter Beiträge, Antworten oder Dateien nicht sicher feststeht, bleiben sie im Portal verborgen, bis jemand sie ausdrücklich freigibt. Geplanter Branch claude/project-thread-houava, Entscheidung DEC-110.

**Umfang:** Befunde F1 bis F3 und der Zusatz zu H3 aus docs/REVIEW_TASK-079_PR-239.md:
1. F1: die Zielgruppe vorhandener Beiträge des Supports, gesendeter Antworten und kundensichtbarer Dateien wird nur aus belastbaren historischen Quellen bestimmt; wo das nicht sicher geht, bleibt sie leer und der Eintrag im Portal verborgen. Bereits mit V0041 und V0042 migrierte Installationen werden über eine neue Migration berichtigt, ohne angewendete Migrationen zu ändern. Der Migrationstest deckt Ticket und Kontaktwechsel vor dem Upgrade ab.
2. F2: eine Antwort des Supports, die beim Lesen noch nicht sichtbar war, bleibt nach dem Quittieren neu, auch wenn ihr Zeitpunkt vor dem quittierten liegt.
3. F3: eine verspätete Folgeseite der Portalliste wird schon ab dem Filterwechsel verworfen, auch während die neue erste Seite noch lädt.
4. H3 Zusatz: der Testersatz des Browsers hält den Kunden einer gesendeten Antwort bei ihrer Erzeugung fest.

Nicht Teil: weitere Funktionen.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Jeder Befund aus dem Umfang ist behoben und mit einem automatischen Test belegt, soweit testbar.
2. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-02):**
1. Ergebnis: F1 V0043 und V0044 berechnen die Zielgruppe vorhandener Portalantworten, Mailantworten und kundensichtbarer Anhänge nur aus der Historie (Kundenänderungen in `ticket_change`, Änderungen des Kontakts im Prüfprotokoll, `attachment.audience` und `attachment.visibility`); ohne sichere Quelle, bei gleichem Zeitpunkt oder bei einer Änderung innerhalb einer Minute um nicht gesperrte Vorgänge bleibt sie leer. Jede Zeile wird neu berechnet, auch nach V0041 und V0042. F2 Positionen der Antworten des Supports aus `helpdesk_portal_position` (V0045), gesperrt bis zum Commit; das Gespräch zeigt `position`, `PUT .../seen` verlangt `position`, neu ist eine Anfrage mit höherer Position als vermerkt. F3 Generation der Portalliste wechselt schon mit Projekt, Filter und erneutem Laden. H3 Zusatz: Testersatz hält den Kunden einer Mailantwort und vergibt Positionen.
2. Nachweise: `PortalAudienceMigrationTest` (Upgrade von V0040 und Korrektur nach V0042 mit Ticket und Kontaktwechsel, Positionen), `PortalConversationApiTest` (per Trigger gehaltene Portalantwort mit früherer Zeit bleibt neu, Quittieren nach Position), `HelpdeskApiTest` (Position beim Senden und Vermerken), drei neue Browsertests in `portal.test.mjs`, der F3 Test schlägt auf dem Stand von TASK-079 fehl. Voller Backendlauf, `npm run verify` und alle Browsertests.
3. Grenzen: Bestandsdaten ohne sichere Quelle bleiben verborgen, nur Dateien lassen sich freigeben; das Fenster von einer Minute ist eine gewählte Sicherheitsspanne; `until` entfällt zugunsten von `position`; H4 bleibt offen.

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme von TASK-075 bis TASK-080 durch Holger.

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

## TASK-083 Befunde der Prüfung von TASK-080 beheben (A11)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261103-portal-04 · **Aktualisiert:** 2026-11-03

**Bezug:** REQ-PORTAL-001, REQ-PORTAL-003, REQ-ACL-002; DEC-106, DEC-109, DEC-110; docs/REVIEW_TASK-080_PR-241.md. **Abhängigkeiten:** TASK-080 geliefert (PR #241). **Arbeitsstand:** Holger hat am 3. November 2026 im Projektgespräch per Auswahl „Ja, so beheben“ beauftragt, die Befunde F1 und F2 der Codex Prüfung von TASK-080 ohne Zeitfenster zu beheben. Geplanter Branch claude/project-thread-houava, Entscheidung DEC-113, neue Migrationen mit den nächsten freien Nummern auf main zum Zeitpunkt der Umsetzung (TASK-081 belegt V0046).

**Umfang:** Befunde F1 und F2 und Hinweis H1 aus docs/REVIEW_TASK-080_PR-241.md:
1. F1: eine alte Mailantwort behält ihre Organisation nur, wenn der Kontakt vor ihrem Zeitpunkt nie die Organisation gewechselt hat; eine alte Freigabe einer Datei für Kunden behält ihre Organisation nur, wenn das Ticket vor der Freigabe nie den Kunden gewechselt hat. Sonst bleibt die Zielgruppe leer und der Eintrag verborgen. Das Fenster von einer Minute entfällt.
2. F2: Änderungen von Name oder Adresse und das Anlegen des Kontakts machen eine Antwort nicht mehr unsicher; Zielgruppen, die die Anwendung seit TASK-079 selbst gespeichert hat, bleiben unverändert oder werden wiederhergestellt.
3. H1: der Migrationstest deckt eine länger als eine Minute gehaltene Änderung, einen frisch angelegten Kontakt, eine reine Namensänderung und Zeilen nach V0042 ab.
4. Der veraltete Kommentar zu `until` in `frontend/src/product.ts` wird berichtigt.

Nicht Teil: weitere Funktionen, Beiträge des Supports (Regel aus V0043 bleibt), H2 (Lesestand alter Zeitpunkte bleibt dokumentierte Grenze).

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Jeder Befund aus dem Umfang ist behoben und mit einem automatischen Test belegt.
2. Bestehende Backend und Browser Tests bleiben grün.

**Übergabe zur Prüfung (2026-11-03):**
1. Ergebnis: V0047 gibt alten Mailantworten nur dann eine Organisation, wenn jede Anlage und Änderung des Kontakts bis zum Zeitpunkt der Antwort dieselbe Organisation nennt; V0048 gibt Anhängen, deren jüngste Handlung ein Umschalten auf „Kunde“ vor TASK-079 ist, nur dann einen Kunden, wenn jede Kundenänderung des Tickets bis dahin denselben nennt. Sonst bleibt die Zielgruppe leer; kein Zeitfenster mehr (F1). Name, Adressen und Anlage des Kontakts zählen nicht; Mailantworten nach der Installation von V0043 behalten den Wert der Anwendung, ältere werden nach der Regel neu berechnet (F2). Kommentar zu `position` in `frontend/src/api/product.ts` berichtigt.
2. Nachweise: `PortalAudienceMigrationTest` mit drei Fällen (Upgrade von V0040, Korrektur nach V0042, Korrektur nach V0045 mit Zeilen der Anwendung), darin 70 Sekunden gehaltene Änderung von Kontakt und Kunde, kurz vorher angelegter Kontakt, reine Namensänderung; auf dem Stand von V0045 belegt der Test die alten Fehler. Voller Backendlauf, `npm run verify`.
3. Grenzen: Mailantworten an einen Kontakt, der vorher die Organisation gewechselt hat, bleiben verborgen; die Grenze aus `flyway_schema_history` setzt dieselbe Zeitzone der Datenbanksitzung wie bei V0043 voraus; H2 bleibt. V0046 gehört TASK-081 (PR #243, schon auf main).

**Nächster Schritt:** Prüfung des Ergebnis PR durch eine zweite KI und Abnahme von TASK-075 bis TASK-080 und TASK-083 durch Holger.

## TASK-084 Befund der Prüfung von TASK-083 beheben (A11)

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261103-portal-05 · **Aktualisiert:** 2026-11-03

**Bezug:** REQ-PORTAL-001, REQ-PORTAL-003, REQ-ACL-002; DEC-110, DEC-113; docs/REVIEW_TASK-083_PR-245.md. **Abhängigkeiten:** TASK-083 geliefert (PR #245). **Arbeitsstand:** Holger hat am 3. November 2026 im Projektgespräch per Auswahl „Ja, so beheben“ beauftragt, den Befund F1 der Codex Prüfung von TASK-083 mit einer von der Zeitzone unabhängigen, vorsichtigen Grenze zu beheben. Geplanter Branch claude/project-thread-houava, Entscheidung DEC-114, Migration V0051 (V0049 und V0050 gehören TASK-082, dessen Ergebnis daher zuerst auf main kommt).

**Umfang:** Befund F1 und Hinweise H1 und H3 aus docs/REVIEW_TASK-083_PR-245.md:
1. F1: eine neue Migration bestimmt die Grenze zwischen alten Mailantworten und den von der Anwendung nach V0043 gespeicherten ohne Annahme über die Zeitzone der Datenbanksitzung: der späteste mögliche Zeitpunkt, 12 Stunden nach dem gespeicherten Installationswert von V0043 in UTC gelesen. Alle Antworten davor werden nach der Regel aus TASK-083 neu berechnet; V0047 bleibt unverändert.
2. H1: der Migrationstest deckt beide Richtungen eines Zeitzonenwechsels zwischen V0043 und der neuen Migration ab.
3. H3: Testbeschreibung und README sagen genau, dass der Migrationstest eine modellierte Historie prüft.

Nicht Teil: weitere Funktionen, H2 (Reihenfolge mit V0046 ist integriert).

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Der Befund ist behoben und mit automatischen Tests für beide Zeitzonenrichtungen belegt.
2. Bestehende Backend und Browser Tests bleiben grün.

**Nächster Schritt:** Reservierung mergen, danach Umsetzung auf dem Branch.
