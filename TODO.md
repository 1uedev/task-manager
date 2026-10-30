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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 1 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-062](#task-062-befunde-der-prüfungen-von-task-058-beheben) | Befunde der Prüfungen von TASK-058 beheben | claude-20261030-replyfix-01 | 2026-10-30 |

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-058](#task-058-helpdesk-antworten-über-smtp-paket-a9-teil-2) | Helpdesk Antworten über SMTP (Paket A9, Teil 2) | claude-20261028-helpdesk-01 | 2026-10-29 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-058 Helpdesk Antworten über SMTP (Paket A9, Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261028-helpdesk-01 · **Aktualisiert:** 2026-10-29

**Bezug:** REQ-HD-001 (Versand, erkennbare ausstehende Sendungen), REQ-HD-002 (Antworten zuordnen, Wiederholungen, Schleifen), REQ-HD-005, REQ-TKT-005 (interne Versandaufträge, unklare Zustellung). **Abhängigkeiten:** TASK-057 mit TASK-059 bis TASK-061, abgenommen (DEC-090). **Arbeitsstand:** Branch `claude/project-thread-b4u17z`, Ergebnis-PR mit DEC-091 und Migration V0028. Holger hat am 29. Oktober 2026 „Ja, direkt umsetzen“ gewählt; die Abnahmekriterien unten hat die Sitzung mit dem Ergebnis geschärft und legt sie zur Prüfung vor.

**Umfang:** Kundenantwort am Ticket an eine Adresse des Kontakts über das SMTP eines Postfachs des Projekts, mit der neuen Projektaktion `helpdesk.reply`, Prüfschritt vor der Bestätigung und Versand als Auftrag `helpdesk.reply` im Modul `jobs`; Zustände ausstehend, vom Mailserver angenommen, nicht gesendet und Versand unklar mit erneutem Senden oder Festhalten durch eine Person; eigene Message-ID, `In-Reply-To` und `References`; Zuordnung der Antworten des Kunden zum Ticket nur bei bestätigtem Absender des Kunden des Tickets, sonst Prüfwarteschlange mit Verweis auf das Ticket; Übersicht der nicht angenommenen Antworten. Nicht Teil: Entwürfe, weitere Empfänger, Anhänge, Vorlagen mit Branding (A17), Benachrichtigungen, SLA, KI Entwürfe, Portal, Zustellberichte, Ablage im Gesendet Ordner.

**Abnahmekriterien:** Vorschlag der Sitzung, zur Prüfung:
1. Nur mit `helpdesk.reply`, `ticket.read` und den Leseregeln für `mail` und `contact`; Empfänger nur Adressen des Kontakts des Tickets, Absender nur sendende Postfächer des Projekts; ohne Kontakt 409. Die Oberfläche zeigt Empfänger und Absender vor der Bestätigung.
2. Je bestätigter Antwort genau ein Versandauftrag in derselben Transaktion wie Antwort, Historie und Audit, auch nach Neustart oder mit zwei Ausführern.
3. Vor dem Versand prüft der Auftrag Projekt, aktiven Urheber mit Mitgliedschaft, Postfach und Empfänger; eine geänderte Lage endet als nicht gesendet ohne Versand.
4. Vorübergehende Fehler vor der Übergabe werden wiederholt, endgültige Ablehnungen enden als nicht gesendet; eine fehlende Antwort des Servers nach der Übergabe und ein Versuch, der die Antwort noch im Versand findet, ergeben Versand unklar ohne automatische Wiederholung.
5. Erneut senden nur für nicht gesendete Antworten oder für unklare mit bestätigtem Duplikatrisiko; eine unklare lässt sich als angekommen festhalten.
6. Die Antwort des Kunden mit `In-Reply-To` oder `References` auf eine Antwort landet ohne neues Ticket im Ticket der Antwort nur bei bestätigtem Absender des Kunden des Tickets, sonst in der Prüfwarteschlange mit Verweis und Übernahme dorthin.
7. Keine Antwort auf automatische Mails; eine zurückkommende eigene Antwort wird als automatisch vermerkt. Keine Mailschleife mit einem Autoresponder.
8. Nicht angenommene Antworten sind für Personen mit `helpdesk.reply` im Projekt sichtbar; Protokolle enthalten keinen Text des Mailservers.

**Ergebnis:** Modul `helpdesk` mit `ReplyService`, `ReplySender`, `SmtpMailSender`, Migration V0028, Vertrag `contracts/helpdesk.openapi.yaml` (sechs neue Routen), Oberfläche am Ticket und in der Prüfwarteschlange, Beschreibung in backend/README.md und docs/HELPDESK_SLA.md, Entscheidung DEC-091.

**Prüfungen:** Backend `./mvnw verify` gegen PostgreSQL 16 mit 380 Tests, darunter sechs neue Tests der Antworten gegen GreenMail und `ScriptedSmtp` (Abbruch nach DATA, Ablehnung 550 und 451); Frontend `npm run verify` und 128 Browser Tests, davon vier neue. Nicht lokal ausgeführt: `backend/scripts/lifecycle-check.sh` mit Docker; die Migrationsliste ist angepasst, der Lauf erfolgt in CI.

**Nächster Schritt:** Getrennte Prüfung durch eine andere KI (Codex), danach Holgers Abnahme.

## TASK-062 Befunde der Prüfungen von TASK-058 beheben

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261030-replyfix-01 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-HD-001 (erkennbare ausstehende Sendungen), REQ-HD-002 (Zuordnung von Antworten), REQ-TKT-005 (unklare Zustellung). **Abhängigkeiten:** TASK-058 (Ergebnis PR #202), Prüfberichte docs/REVIEW_TASK-058_PR-202.md (Claude, PR #203) und docs/REVIEW_TASK-058_PR-202_CODEX.md (Codex, abgelegt mit dem Nachtrag zur Reservierung PR #204). **Arbeitsstand:** Holgers Wahl „F1 bis F4 beheben“ vom 30. Oktober 2026 zur Claude Prüfung; der danach übergebene Codex Bericht empfiehlt seine Befunde F1 bis F4 ebenfalls vor der Abnahme und überschneidet sich mit der Claude Prüfung, daher deckt TASK-062 mit diesem Nachtrag beide ab. Branch claude/project-thread-b4u17z nach dem Merge dieser Reservierung; Ergebnis mit DEC-092, Migration V0029 nur falls nötig.

**Umfang:** Befunde beider Berichte (C für Claude, X für Codex):

1. Verwaiste Antworten (C-F1, X-F2): Eine Antwort, deren Versandauftrag ohne vermerkten Ausgang endet (letzter Versuch verloren, anderer Fehler im letzten Versuch, Abbruch in der Jobverwaltung), bleibt nicht dauerhaft ausstehend; sie wird unklar oder nicht gesendet und lässt sich im Helpdesk erneut senden oder festhalten.
2. Versandrunden (X-F1): Ein Ergebnis eines älteren Versuchs oder einer älteren Runde ändert eine neuere Runde nie; die Prüfung der Runde ist atomar mit dem Zustandswechsel.
3. Person der Bestätigung (C-F2, Teil von X-H1): Der Auftrag prüft die Person, die den Versand zuletzt bestätigt hat, nicht den ursprünglichen Urheber.
4. Adressen und Kopfzeilen (X-F3, C-F3): Absender und Empfänger sind genau eine einfache Adresse ohne Kommentar, Gruppe oder Steuerzeichen, kanonisch gespeichert und verwendet; Message-ID aus geprüfter Domain; `In-Reply-To` und `References` nur mit Message-IDs aus druckbarem ASCII und gefaltet unter der Zeilengrenze von RFC 5322; ein lokaler Fehler vor der Übergabe gilt als sicher nicht gesendet.
5. Zeitlimits und Phasen (C-F4): Eigenes, längeres Zeitlimit für die Antwort des Servers auf das Datenende; ein Abbruch vor dem Datenende gilt als sicher nicht gesendet.
6. Kundengrenze beim Anhängen (X-F4): Kundenvergleich und Anhängen geschehen unter einer mit dem Kundenwechsel gemeinsamen Sperre; bei Abweichung bleibt die Mail mit Verweis in der Prüfwarteschlange.

Nicht Teil: die übrigen Hinweise beider Berichte (C-H1 bis C-H8, X-H1 bis X-H5), insbesondere Wiederholungsschlüssel der Bestätigung, vollständige Übersicht, Konfliktregel mehrerer Referenzen und Benachrichtigung bei Kundenantworten; sie bleiben als offene Punkte für Holgers Abnahme.

**Abnahmekriterien:** Regressionstests für die drei Fälle verwaister Antworten mit echter Lease Freigabe, für beide Ergebnisreihenfolgen eines alten Versuchs nach einer neuen Runde, für das erneute Senden nach Austritt des Urhebers, für Adressen mit Kommentar und Zeilenumbruch, Gruppen und Steuerzeichen bei Postfach und Kontakt, für die Zeilenlänge mit 20 langen Message-IDs, für die Einordnung vor und nach dem Datenende sowie eine späte Annahme innerhalb des neuen Zeitlimits und für einen Kundenwechsel zwischen Vergleich und Anhängen in beiden Commit Reihenfolgen; die neuen Tests sind vor der Korrektur rot und danach grün; alle Backend, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-058 und TASK-062 zusammen ab.

**Nächster Schritt:** Nach dem Merge dieser Reservierung umsetzen.
