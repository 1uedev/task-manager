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
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 0 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-058](#task-058-helpdesk-antworten-über-smtp-paket-a9-teil-2) | Helpdesk Antworten über SMTP (Paket A9, Teil 2) | claude-20261028-helpdesk-01 | 2026-10-29 |

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

Derzeit keine Aufgaben in dieser Kategorie.

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-058 Helpdesk Antworten über SMTP (Paket A9, Teil 2)

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261028-helpdesk-01 · **Aktualisiert:** 2026-10-29

**Bezug:** REQ-HD-001 (Versand, erkennbare ausstehende Sendungen), REQ-HD-002 (Antworten zuordnen, Wiederholungen, Schleifen), REQ-HD-005, REQ-TKT-005 (interne Versandaufträge, unklare Zustellung). **Abhängigkeiten:** TASK-057 mit TASK-059 bis TASK-061, abgenommen (DEC-090). **Arbeitsstand:** Zweiter Teil von Paket A9 mit derselben Zustimmung vom 28. Oktober 2026; der Blocker ist mit Holgers Abnahme von TASK-057, TASK-059, TASK-060 und TASK-061 am 29. Oktober 2026 aufgehoben (DEC-090). Branch wird beim Start festgelegt; Ergebnis mit eigener DEC und Migration, Nummern beim Start auf main prüfen.

**Umfang:** Vorschlag: Kundenantwort am Ticket an den Kontakt über das SMTP des Postfachs, sichtbar getrennt von internen Arbeiten, mit Prüfung der aktuellen Rechte vor dem Versand; Versand als Auftrag im Modul `jobs` mit den Zuständen aus docs/HELPDESK_SLA.md (ausstehend, vom Server angenommen, sicher gescheitert, Versandstatus unklar ohne blinde Wiederholung); eigene Message-ID und Antwortkennung, damit Antworten des Kunden über `In-Reply-To` und `References` zusammen mit der Absenderprüfung aus TASK-057 dem bestehenden Ticket zugeordnet werden, sonst Prüfwarteschlange; keine automatische Antwort auf automatische Nachrichten; ausstehende und gescheiterte Sendungen für zuständige Personen sichtbar. Nicht Teil: Vorlagen mit Branding (A17), Benachrichtigungen, SLA, KI Entwürfe, Portal.

**Abnahmekriterien:** Werden mit dem Start nach Vorlage des Ergebnisses von TASK-057 festgelegt; mindestens: genau ein interner Versandauftrag je bestätigter Antwort auch nach Neustart, unklarer Versandstatus ohne automatische Wiederholung, Antwort des Kunden landet im richtigen Ticket nur bei bestätigtem Absender, keine Mailschleife mit einem Autoresponder.

**Nächster Schritt:** Abnahmekriterien gegen den abgenommenen Stand von TASK-057 schärfen, DEC und Migrationsnummer auf main prüfen und mit der Umsetzung beginnen. Die offenen Punkte aus DEC-090 (Lesemarkierung eines überholten Abrufs, H5 und H6 der Prüfung von TASK-057) gehören nicht dazu, solange Holger sie nicht beauftragt.
