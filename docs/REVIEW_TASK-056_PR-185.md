# Nachprüfung TASK-056 / PR #185

Stand: 28. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261028-review-056-01`, Branch `claude/project-thread-it6vqg`, eigener Arbeitsbereich. Auftrag: Holgers Wunsch vom 28. Oktober 2026 nach einer getrennten Nachprüfung (DEC-040). Die Sitzung, die TASK-056 umgesetzt hat (`claude-20261028-jobsfix-02`), ist eine andere als diese.

Geprüft wurde die Behebung der Befunde R1 bis R3 aus der [Nachprüfung von TASK-055 durch Codex](REVIEW_TASK-055_PR-183.md) gegen den Umfang und die Abnahmekriterien von TASK-056 in TODO.md und DEC-085. Ergebnis: **R1, R2 und R3 sind behoben. Kein neuer Befund P1 bis P3, drei Hinweise.** Die ganze Backend Suite ist grün, die drei neuen Tests scheitern auf dem Stand vor TASK-056 und sind danach grün, sechs eigene Proben bestätigen die Abgrenzung.

## Grundlage und Abgrenzung

- Ergebnis: [PR #185](https://github.com/1uedev/task-manager/pull/185) auf main als `796acf1`. Prüfbasis ist `796acf1`, Vergleichsstand vor TASK-056 ist `cd25ff8`.
- Maßstab: AGENTS.md, TODO.md (TASK-056), DEC-083 bis DEC-085, backend/README.md (Hintergrundaufträge), docs/DEMO.md, die Berichte zu [TASK-054](REVIEW_TASK-054_PR-179.md) und [TASK-055](REVIEW_TASK-055_PR-183.md).
- Der Diff bleibt im Umfang: `JobExecutor`, `JobExecutorTest`, backend/README.md, docs/DEMO.md sowie die Verwaltungseinträge in AGENTS.md, CLAUDE.md, DECISIONS.md und TODO.md. Keine Migration, kein Vertrag, keine Oberfläche, keine Änderung an `JobRepository`.
- Die übrigen Hinweise der Berichte zu TASK-054 und TASK-055 (H1 bis H8) gehören nicht zu TASK-056 und werden hier nicht erneut gezählt.
- Diese Prüfung ändert keinen Produktcode, keinen Aufgabenstatus und erklärt keine Abnahme.

## Was gelaufen ist

| Prüfung | Ergebnis |
|---|---|
| Backend `./mvnw verify` mit Java 21 (`-Djava.version=21`) gegen natives PostgreSQL 16.15 | grün, 44 Testklassen, 343 Tests, keine Fehler, nichts übersprungen, Paketbau erfolgreich |
| `JobExecutorTest` mit den eigenen Proben ([probes.patch](review-evidence/task-056/probes.patch)) auf `796acf1` | grün, 29 Tests |
| Dieselbe Klasse gegen `JobExecutor` vom Stand `cd25ff8` (nur um das Testfeld `stopGrace` ergänzt) | 4 Fehlschläge: die drei neuen Tests zu R1, R2, R3 und die Probe P3; dazu scheitert P6 bei getrenntem Lauf der Proben |
| CI von PR #185 | qualification, frontend, backend, image, repository-checks grün |
| `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check` | grün |

Ausgaben in [review-evidence/task-056/results.txt](review-evidence/task-056/results.txt). Nicht gelaufen: Java 25 und PostgreSQL 18.6 (prüft CI), Frontend und Browsertests (keine Änderung daran), Imageprüfung und ein echter `docker compose stop` mit einem länger als 15 Sekunden laufenden Bearbeiter.

## Befunde aus der Nachprüfung von TASK-055

| Befund | Ergebnis | Nachweis |
|---|---|---|
| R1 (P2): ein fehlgeschlagener Ergebnisvermerk unterdrückt den fatalen Prozessausstieg | **Behoben** | `JobExecutor.execute` (Zweig `fatal(error)`) versucht den Vermerk in einem eigenen `try`, fängt dessen `RuntimeException` und wirft den ursprünglichen Error danach in jedem Fall weiter; `work` ruft den Ausgang. Wirft der Vermerk selbst einen Error, erreicht auch dieser `work` und beendet den Prozess (Probe P5). Test `aFatalErrorEndsTheProcessEvenWhenItsOutcomeCannotBeWritten`: drei gescheiterte `fail`, Ausgang aufgerufen, Auftrag bleibt `running` für die Reservierung. Scheitert auf `cd25ff8`. |
| R2 (P3): die Stoppmarkierung überstimmt einen endgültigen Fachfehler | **Behoben** | `record` gibt nur zurück, wenn `stopped && !timedOut && !kept`, mit `kept` für jeden Error und `JobFailure.permanent`. Test `aPermanentFailureReportedAtTheStopKeepsItsCodeAndEndsTheJob`: `failed`, ein Versuch, `test.refused_on_stop`. Proben P1 und P2: `JobFailure.retryable` und eine umhüllte Unterbrechung werden wie in DEC-085 beschrieben ohne Zählung zurückgegeben. P3: ein nicht fataler Error zählt und beendet den Prozess nicht. |
| R3 (P3): der Nachweis der Stoppunterbrechung geht nach drei Sekunden verloren | **Behoben** | Die Menge `interruptedByStop`, die `stop()` am Ende leerte, ist ersetzt durch `attempts` (Faden auf `Watchdog`). `Watchdog.stop` setzt `stopped` und unterbricht nur, solange der Versuch nicht `done` ist; `execute` entfernt den Eintrag erst im `finally`. Test `anAttemptThatEndsAtTheStopInterruptOnlyAfterTheStopReturnedIsGivenBackWithoutCounting`: nach Rückkehr von `stop()` noch `running`, danach `queued`, 0 Versuche, `job.interrupted`. Scheitert auf `cd25ff8`. backend/README.md beschreibt den kooperativen Stopp, das harte Beenden durch die Umgebung und die mögliche Übernahme nach Ablauf der Reservierung; docs/DEMO.md trennt die 15 Sekunden des Ausführers von den 30 Sekunden von Docker. |

Die Abnahmekriterien von TASK-056 sind damit erfüllt: die drei Experimente des Berichts laufen mit umgekehrter Erwartung als Regression grün, alle bestehenden Tests bleiben grün.

## Weitere Beobachtungen

- **Vermerk nicht mehr unterbrochen.** Der Bericht zu TASK-055 nannte als Grenze, dass der Stopp den Ergebnisvermerk unterbrechen kann (Probe `stopCanInterruptTheOutcomeWrite`). Das war ausdrücklich nicht Teil von TASK-056, ist aber als Nebenwirkung behoben: `stop()` unterbricht nur noch über den `Watchdog`, und der lehnt nach `done()` unter derselben Sperre ab. Probe P6 hält den Schreibversuch während Stopp und Nachfrist fest; auf `796acf1` gibt es genau einen Aufruf von `succeed`, auf `cd25ff8` zwei (der erste wurde unterbrochen, die Wiederholung gelang). Ein Faden, der auf Arbeit wartet oder einen Auftrag reserviert, wird ebenfalls nicht mehr unterbrochen; `pause()` wird über den Monitor geweckt.
- **Zeitlimit und Stopp zugleich:** Feuert das Zeitlimit, gilt weiter `job.timeout` mit Zählung, auch wenn der Stopp ebenfalls markiert hat. Das entspricht dem bisherigen Verhalten und DEC-085.
- **Gleichzeitigkeit:** `Watchdog` hält `finished`, `fired` und `stopped` unter seinem Monitor; `attempts` ist eine `ConcurrentHashMap`, `put` geschieht vor dem Planen des Zeitlimits, `remove` im äußersten `finally`. Ein Stopp, der vor dem `put` eines eben reservierten Auftrags durch die Liste geht, unterbricht diesen Versuch nicht; er läuft dann bis zum Ende oder bis zum harten Beenden. Das Zeitfenster ist sehr klein und fällt unter die in README.md beschriebene Grenze des kooperativen Stopps.

## Hinweise

**H1 · Der bestmögliche Vermerk nach einem fatalen Error ist nicht zeitlich begrenzt.** `retrying` versucht dreimal mit 200, 400 und 600 Millisekunden Pause. Jeder Versuch wartet aber bis zur Zeitgrenze von Hikari auf eine Verbindung (Standard 30 Sekunden, in application.yaml nicht gesetzt), und ein hängender Datenbankaufruf hat keine Socket Zeitgrenze. Bei erschöpftem Pool kann der Ausstieg so etwa 90 Sekunden später kommen, bei einer hängenden Verbindung gar nicht; in dieser Zeit reservieren die übrigen Fäden weiter Aufträge. Das ist die schon im Bericht zu TASK-055 genannte allgemeine Grenze unbeschränkt blockierender Datenbankaufrufe und kein Rückfall. Wer es enger will, gibt dem fatalen Pfad einen einzigen Schreibversuch oder eine eigene Frist.

**H2 · Ein nicht fataler Error beim Stopp zählt jetzt.** DEC-085 behält jeden Error mit seinem Code, nicht nur die fatalen. Ein `StackOverflowError` oder `AssertionError`, der während der Stoppunterbrechung entsteht, verbraucht also den Versuch (Probe P3), wo TASK-055 ihn noch zurückgab. Das ist gewollt und dokumentiert; es sei nur genannt, weil es über den Wortlaut des Befunds R2 („fataler Error“) hinausgeht.

**H3 · README.md nennt weiter einen Ausführer im Webserver der Vorführumgebung.** Der letzte Satz des Absatzes „Prozesse“ sagt, die Vorführumgebung starte den Webserver mit Ausführer, compose.yaml setzt dort aber `TASK_MANAGER_JOBS_WORKER: "false"`. Das ist H6 des Berichts zu TASK-054 und bleibt außerhalb von TASK-056.

## Empfehlung

R1 bis R3 können als technisch behoben gelten. Zusammen mit den Berichten zu TASK-054 und TASK-055 bleiben nur Hinweise offen, kein Befund. Aus technischer Sicht steht der fachlichen Abnahme von TASK-054 bis TASK-056 durch Holger nichts entgegen. Diese Prüfung meldet keine Aufgabe als abgenommen.
