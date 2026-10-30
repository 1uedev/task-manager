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
| [TASK-065](#task-065-befunde-f1-und-f2-der-codex-nachprüfung-von-task-054-beheben) | Befunde F1 und F2 der Codex Nachprüfung von TASK-054 beheben | claude-20261030-jobsfix-03 | 2026-10-30 |

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

## TASK-065 Befunde F1 und F2 der Codex Nachprüfung von TASK-054 beheben

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261030-jobsfix-03 · **Aktualisiert:** 2026-10-30

**Bezug:** REQ-OPS-006 (Hintergrundaufträge), REQ-HD-005 (keine Mehrfachaktion nach Neustart oder Wiederholung). **Abhängigkeiten:** TASK-054 (abgenommen, PR #179 und #180), Prüfbericht [docs/REVIEW_TASK-054_PR-179.md](docs/REVIEW_TASK-054_PR-179.md) (Codex Nachprüfung auf main 3f38643, PR #212). **Arbeitsstand:** Holger hat am 30. Oktober 2026 die Behebung beider Befunde beauftragt; Arbeit beginnt nach dem Merge dieser Reservierung. Branch claude/project-thread-lxrdya; Ergebnis mit DEC-095, keine Migration geplant.

**Umfang:**

1. F1: Jede Übernahme eines Auftrags erhält eine eigene, nicht wiederverwendbare Reservierungskennung. Verlängerung, Erfolg, Fehlschlag und Rückgabe gelten nur für diese Kennung, sodass ein verspäteter alter Versuch desselben Ausführers einen neueren Versuch weder abschließen noch zurückstellen kann. Der Ausführer führt seine laufenden Versuche je Reservierung, damit das Ende eines alten Versuchs die Verlängerung des neuen nicht entfernt.
2. F2: Die Jobübersicht verwirft verspätete Listenantworten einer älteren Laderunde nach einem Filterwechsel.

Nicht Teil: die Hinweise H1 bis H4 der Nachprüfung, außer einer knappen Korrektur der Aussage zu gleichzeitiger Ausführung, soweit F1 sie berührt.

**Abnahmekriterien:** Regressionstests mit echter Datenbank für beide Reihenfolgen verspäteter Ergebnisse (alter Erfolg vor neuem Fehler und alter Fehler vor neuem Erfolg) sowie für einen alten Versuch nach erneutem Einstellen durch die Verwaltung; ein Browsertest mit umgekehrter Antwortreihenfolge beim Filterwechsel; alle Backend Tests einschließlich der Helpdesk Tests, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt ab.

**Nächster Schritt:** Merge dieser Reservierung, danach Umsetzung.
