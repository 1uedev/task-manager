# Nachprüfung TASK-071 / PR #225

1. November 2026 · Codex · Sitzung `codex-20261101-task071-review-01`.

**Ergebnis: F1 bis F3 der Prüfung von TASK-070 sind im geprüften Umfang behoben. Kein neuer Fehler R1 bis Rn nachgewiesen.** Die Nachtragung nach Überschreiten einer Schwelle entspricht DEC-101. **19 gezielte Backendtests einschließlich zweier eigener Zusatzfälle sowie acht SLA-Browsertests bestehen.** Vollständige Produkt-Testläufe wurden in dieser Nachprüfung nicht wiederholt.

## Grundlage und Umfang

Geprüft wurde [PR #225](https://github.com/1uedev/task-manager/pull/225), Branch `claude/project-thread-2gq3w2`, fester Head **`8602afe00b05354a5787c27ac7c99b08a6259fe4`**. Der PR war bei Prüfstart offen. Maßstab: TASK-071, DEC-101 und [der vorherige Prüfbericht](REVIEW_TASK-070_PR-223.md), insbesondere F1–F3 und H1–H5. Holgers Entscheidung zu H5 „Ja, Stufe nachtragen“ wurde als bestätigte Regel übernommen.

Gelesen wurden die im Prüfauftrag genannten Methoden und Ereignisse in TicketSla, Escalations, SlaRepository.escalationsFor, Stage.violations, EscalationsTest, der TASK-071-Abschnitt von SlaApiTest sowie Listenattrappe und letzter SLA-Browsertest. Für die Frage nach einem umgehenden Schreibpfad wurden ergänzend die unmittelbar aufgerufenen Methoden `SlaCycle.customerAnswered/statusChanged/replySent/priorityChanged`, `SlaClock.resume/meet/rebudget/pause` und einzelne Testhilfen betrachtet.

Die Ergebnis-Prüfkopie enthält ausschließlich diesen Bericht. Eigene API-Tests wurden in einer getrennten Experimentkopie ergänzt. Kein Produktcode, Aufgabenstatus oder Abnahmevermerk wurde geändert.

## Bewertung der Korrekturen

| Alter Punkt | Ergebnis und Beleg |
|---|---|
| **F1: falscher Schwellenzeitpunkt nach einer Pause** | **Behoben für neue Änderungen.** `change` ruft `record` mit dem alten Zyklus auf, bevor `updateCycle` den neuen Stand schreibt. Der ursprüngliche Rechenfall 09:00–11:00 bewahrt so 10:36 beziehungsweise 11:00 als Schwellenzeitpunkte. Die neue API-Regression prüft außerdem den gespeicherten Wert nach Pause und Fortsetzen. |
| **F2: lesbare Stufe hinter mehr als 200 nicht lesbaren Kandidaten** | **Behoben.** Kandidaten werden mit Fortsetzung gelesen, nicht lesbare Projekte auf weiteren Seiten ausgeschlossen. Erst die hinzugefügten sichtbaren Einträge zählen zur Ergebnisgrenze 200. Der neue API-Fall findet die ältere lesbare Stufe hinter 201 nicht lesbaren Kandidaten. |
| **F3: clock null erzeugt 500** | **Behoben.** Die Nullprüfung steht vor `List.of(...).contains`. Der API-Test verwendet gültige übrige Regeldaten und erhält 400 mit `escalations.invalid`. |
| **H1: abweichende Attrappe** | Die Liste wird jetzt nach Gruppen-/Leserechteprüfung auf 200 gekürzt. Der neue Browserfall prüft 200 aus 201 Einträgen und die Ablehnung der leeren Uhr. |
| **H2: Budgetwechsel und konkurrierende Änderung** | Neue Tests prüfen kleineres/größeres Budget und eine Statusänderung mit drei parallelen Watches. Zusätzlich wurde eine Verkleinerung mit anschließender Vergrößerung ohne Watch dazwischen nachgestellt: Die zwischenzeitlich fällige Stufe bleibt erhalten. |
| **H5: nach der Schwelle, vor dem Watch beendete Uhr** | **Umgesetzt.** Pause, Antwort, Kundenentfernung und Neubudgetierung sichern die bis dahin erreichten Stufen vor dem Schreiben. Die echte API-Regression für die Antwort und der zusätzliche Fall zur Kundenentfernung bestehen. Die Auswahl der höchsten fälligen Stufe mit gespeicherten übersprungenen Positionen bleibt erhalten. |

**Dateien/Zeilen:** `TicketSla.java:278–312,328–374`; `Escalations.java:26–47`; `SlaRepository.java:293–306`; `Rule.java:53–57`; `SlaApiTest.java:791–923`; `frontend/tests/browser/backend.mjs:307–323`, `sla.test.mjs:217–238`.

## Änderungswege und Sperren

| Weg | Sicherung vor dem Schreiben |
|---|---|
| Statuswechsel mit Änderung der SLA-Statusart | `TicketSla.on(TicketChanged)`, Zeilen 120–125: `change` vor Änderung des Zyklus |
| Kunde entfernt | `on(TicketCustomerChanged)`, Zeilen 143–147: `change` vor Abbruch |
| Antwort versandt | `on(ReplySent)`, Zeilen 165–168: `change` am Zeitstempel der Antwort |
| Priorität geändert | `setPriority`, Zeilen 394–402: Ticketsperre, dann SLA-Sperre und `change` vor Neubudgetierung |
| Kundenantwort | `on(CustomerAnswered)`, Zeilen 173–176: weiterhin direkter `updateCycle`-Aufruf, hier aber **kein Umgehen der benötigten Sicherung** |

Der letzte Weg wurde ausdrücklich verfolgt: `SlaCycle.customerAnswered` (Zeilen 84–88) verändert nur eine **pausierte** Lösungsuhr durch Fortsetzen. Eine bereits laufende Lösungsuhr bleibt unverändert; auch die Erstreaktionsuhr bleibt erhalten. Dieser Pfad hält keine laufende Uhr an, beendet sie nicht und budgetiert sie nicht neu.

`record` nimmt selbst keine Ticketzeilensperre und ruft keinen Ticketänderungsport auf. Die Prioritätsänderung hält weiterhin Ticket vor `sla_ticket`; der Watch nimmt nur die bekannte SLA-Zeile. Die neue Speicherung fügt im gelesenen Ablauf keine umgekehrte Reihenfolge hinzu. Die äußeren Publisher aller Modulereignisse wurden nicht vollständig neu auditiert; daraus wird keine allgemeine Deadlock-Freigabe abgeleitet.

## Kleinere Budgets und Zeitpunkte

Bei einer Verkleinerung hält `change` zunächst die Stufen des alten Budgets fest. Die neue Uhr trägt den Zeitpunkt der Budgetänderung als Checkpoint. Eine erst dadurch fällige Stufe erhält über `Escalations.due` diesen Zeitpunkt. Ein vor dem Checkpoint datiertes Ereignis wird auf den letzten Stand begrenzt; die Uhr läuft nicht rückwärts.

Die neu fällige Stufe muss dabei nicht schon im Prioritäts-PUT geschrieben werden: Sie wird beim nächsten Watch oder vor der nächsten entsprechenden Änderung gespeichert. **Zusatzfall E1** prüft genau die Lücke dazwischen:

1. 20 Minuten Verbrauch bei normalem Budget 60 Minuten, Stufe 80 % noch nicht erreicht.
2. Wechsel auf kritisch mit 15 Minuten Budget; noch kein Watch.
3. Nach einer unterscheidbaren Sekunde Wechsel zurück auf normal.
4. Die Stufe ist gespeichert, ihr Schwellenzeitpunkt entspricht exakt dem ersten Budgetwechsel. Ein späterer Watch verändert sie nicht.

Damit wird für diese schnelle Änderungsfolge weder die Stufe verloren noch der spätere Zeitpunkt der Vergrößerung übernommen.

## Fortschritt und Vollständigkeit der Liste

Die Reihenfolge ist `reached_at DESC, id ASC`. Der Cursor verwendet entsprechend ältere Zeitpunkte oder bei gleichem Zeitpunkt eine größere ID. Jede nicht leere Seite verschiebt den Cursor streng nach hinten. Bei unverändertem endlichem Bestand endet die Schleife, sobald 200 sichtbare Einträge oder das Kandidatenende erreicht sind.

Unlesbare Projekte auszuschließen kann in diesem Modell keine lesbare Stufe desselben Abrufs verwerfen: Die Prüfung und der Cache sind projektbezogen. Ein Kandidat ohne vorhandenen Ticketzustand verbraucht keine sichtbare Ergebnisposition; der Cursor schreitet trotzdem fort. Der alte Verlust durch die erste rohe 200er-Grenze ist nicht mehr vorhanden.

## Hinweise

### H1 – Listenabruf ist keine eingefrorene Momentaufnahme

**Dateien/Zeilen:** `SlaRepository.java:298–306`, `TicketSla.java:293–312`.

**Fall:** Während der zweiten Seite entsteht eine neue Eskalation mit einem Zeitpunkt vor der bereits gelesenen Cursorposition in der Sortierreihenfolge. Sie erscheint gegebenenfalls erst nach erneutem Aktualisieren. Auch gleichzeitig geänderte Leserechte bilden keinen über den ganzen Abruf garantierten Snapshot. Das ist kein Wiederauftreten von F2, sollte aber nicht als absolute Vollständigkeit bei gleichzeitigem Schreiben beschrieben werden. Die neue Implementierung wurde bei stabilem Bestand und anhand des vorhandenen 201er-Falls bewertet.

### H2 – Der Nebenläufigkeitstest erzwingt nicht beide Commit-Reihenfolgen

**Datei/Zeilen:** `SlaApiTest.java:863–880`.

**Fall:** Statuswechsel und drei Watches starten gemeinsam, der Scheduler entscheidet aber die tatsächliche Reihenfolge. Der Test belegt das richtige Endergebnis dieses Laufs und die Einmaligkeit, nicht getrennt beide gezielt gesteuerten Reihenfolgen. Der Statusaufruf selbst wird durch die Testhilfe `status` (Zeilen 294–299) auf HTTP 200 geprüft; fehlgeschlagene Änderungen werden also nicht still akzeptiert. Eine spätere Härtung mit gezielten Barrieren wäre sinnvoll.

### H3 – Historische Zyklen bleiben ausdrücklich begrenzt repariert

**Datei/Zeilen:** `Escalations.java:44–46`; bekannte Grenze in DEC-101/TASK-071.

**Fall:** Eine alte Uhr wurde noch vor dieser Korrektur nach Schwellenübertritt pausiert, ohne eine Stufe zu speichern. Nach ihrem Fortsetzen fehlt die ursprüngliche Zwischenhistorie; sie kann weiterhin dessen Zeitpunkt erhalten. Die Korrektur verhindert den Fehler für neue Änderungen, rekonstruiert aber keine fehlende Vergangenheit. Das ist die ausdrücklich dokumentierte Grenze, kein neuer Befund.

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| EscalationsTest unverändert | **8/8 erfolgreich** |
| Neun gezielt ausgewählte Eskalationsfälle aus SlaApiTest, einschließlich aller fünf TASK-071-Fälle und vier TASK-070-Regressionen | **9/9 erfolgreich** |
| Zusätzlicher API-Fall E1 `reviewTwoPriorityChangesWithoutWatchKeepNewlyDueStage` | erfolgreich; exakter Zeitpunkt des kleineren Budgets erhalten |
| Zusätzlicher API-Fall E2 `reviewCustomerRemovalAfterThresholdKeepsStage` | erfolgreich; Stufe mit ursprünglichem Zeitpunkt im abgebrochenen Zyklus erhalten |
| Frontend-Abhängigkeiten aus lokalem Cache und Vite-Build | erfolgreich; Warnung zur Bundlegröße über 500 kB |
| Ausschließlich `frontend/tests/browser/sla.test.mjs`, Chrome über Playwright | **8/8 erfolgreich**, einschließlich neuem 200er-/Null-Uhr-Test |
| Repositoryvertrag, Aufgabenübersicht, Python-Tests und Diff-/Statuskontrolle | erfolgreich; **38 Python-Tests, 1 Unix-spezifischer Skip**; ausschließlich Berichtsdatei im Ergebnisverzeichnis neu |

Backendumgebung: JDK 25.0.4.1, Maven 3.9.16, lokales PostgreSQL 18.6; kein gepinnter Container. Frontendumgebung: Node 24.22.0. Die zusätzlichen API-Tests liefen nur in der Experimentkopie gegen unveränderten Produktcode.

Nicht ausgeführt: vollständige Backend-/Frontend-Suites, alle Browsertests, Docker-/Lifecycleprüfung, Upgrade eines befüllten Bestands und vollständiger Audit der äußeren Ereignispublisher. Die in TODO.md genannten vollständigen Prüfergebnisse der Umsetzung werden hier nicht als eigene Läufe bestätigt.

## Empfehlung

**TASK-071 ist im geprüften Nachprüfumfang technisch zur Abnahme zu empfehlen.** Die früheren F1–F3 sowie die beauftragten H1/H2/H5 sind nachvollziehbar korrigiert und durch gezielte Tests gestützt. Kein neuer R-Befund wurde nachgewiesen. Für die gemeinsame fachliche Abnahme bleiben die dokumentierten Grenzen und die eigenständige Prüfung von TASK-068 maßgeblich. Dieser Bericht erklärt keine Aufgabe für abgenommen und ändert keinen Status.
