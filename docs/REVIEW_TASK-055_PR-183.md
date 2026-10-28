# Unabhängige Prüfung TASK-055 / PR #183

Stand: 28. Oktober 2026. Prüfer: Codex, Sitzung `codex-20261028-task055-review-01`, unabhängig von der Umsetzungssitzung `claude-20261028-jobsfix-01` (DEC-040). Prüfbasis: frisch geklontes main `1666f4c817fcc9fde57cc7cdce343399fa79270f`, Merge von [PR #183](https://github.com/1uedev/task-manager/pull/183); Claim [PR #182](https://github.com/1uedev/task-manager/pull/182), DEC-084. Eigener Branch `review/task-055-pr-183-codex`.

**Ergebnis: F3 und F4 sind behoben. F1 und F2 sind im normalen Ablauf korrigiert, aber wegen verbleibender Fehlerpfade noch nicht vollständig behoben.** R1 betrifft den zugesagten Prozessabbruch, R2 und R3 die Abgrenzung und Lebensdauer der Stopprückgabe. Keine fachliche Abnahme; TODO.md und Produktcode bleiben unverändert.

## Maßstab und Umfang

Gelesen wurden AGENTS.md, README.md, TODO.md (TASK-054/055), DONE.md, docs/AI_WORKFLOW.md, DEC-040/083/084, die einschlägigen Anforderungen und backend/README.md (Hintergrundaufträge), der [Vorbericht](REVIEW_TASK-054_PR-179.md) und seine [Experimente](review-evidence/task-054/README.md). Geprüft wurden alle 14 geänderten Dateien von PR #183 sowie die angrenzenden Aufrufwege und die Jobs-Oberfläche.

Der Diff bleibt innerhalb F1 bis F4 einschließlich Tests, Vertrag, generierter Typen und Dokumentation des Auftrags. Keine Migration, kein neuer Endpunkt, keine Umsetzung der ausgeschlossenen Hinweise H1 bis H8. Die Änderungen an AGENTS.md, CLAUDE.md, DECISIONS.md und TODO.md dokumentieren diesen Umfang. Der zusätzliche boolesche Wert in `JobExecution` verändert den internen Java-Konstruktor; alle Aufrufer im Repository wurden angepasst.

## Abgleich F1 bis F4

| Befund | Bewertung | Begründung |
|---|---|---|
| F1: Error beendet Ausführerfaden | **Nicht vollständig behoben** | Nichtfatale Errors des Bearbeiters werden vermerkt, der Faden läuft weiter. `VirtualMachineError` außer `StackOverflowError` führt nach erfolgreichem Vermerk zum Prozessausstieg. Schlägt der Vermerk fehl, kann der fatale Error jedoch verschwinden (R1). |
| F2: Stopp verbraucht letzten Versuch | **Nicht vollständig behoben** | Der normale unterbrechbare Bearbeiter wird sofort mit dem vorherigen Zähler zurückgegeben, auch beim letzten Versuch. Die Besitzerbedingung ist korrekt. Falsche Klassifikation und verfrühtes Löschen der Markierung bleiben offen (R2/R3). |
| F3: Wiederholungsnachweis nach erneutem Einstellen | **Behoben** | `claim` liest den bisherigen Start unter derselben Zeilensperre, setzt erst danach den neuen Start; `requeue` behält den alten Wert. Erster Lauf falsch, weiterer Versuch, Stoppwiederholung und Verwaltungswiederholung wahr. |
| F4: Docker stoppt vor Ende der Gnadenfrist | **Behoben, mit Betriebsgrenzen** | `compose.yaml:44` und `:72` setzen 30 Sekunden für app und worker. Das übersteigt 15 + 3 Sekunden. Es ist keine absolute Obergrenze für alle Shutdown-Arbeiten oder blockierte Datenbankaufrufe. |

## Neue Befunde

### R1 · P2 · Ein fehlgeschlagener Ergebnisvermerk unterdrückt den fatalen Prozessausstieg

**Ort:** `backend/src/main/java/taskmanager/jobs/internal/JobExecutor.java:312` bis `:319` (insbesondere der Vermerk vor der Fatalitätsprüfung).

Der Bearbeiter wirft beispielsweise `OutOfMemoryError`. `record` versucht den Datenbankvermerk dreimal. Scheitern diese Schreibversuche mit einer `RuntimeException`, überspringt der Kontrollfluss die Prüfung in Zeile 313. Der äußere Catch protokolliert lediglich den fehlenden Ergebnisvermerk. Der fatale Error erreicht `work()` nicht; der Prozess und seine Ausführer laufen weiter, obwohl DEC-084 gerade dafür den Neustart verlangt. Der Auftrag bleibt zunächst `running`, seine Reservierung entscheidet später. Das ist bei Ressourcenmangel oder einer gleichzeitigen Datenbankstörung ein relevanter Fehlerpfad.

**Nachstellung:** `databaseFailureSuppressesFatalExit` im beigefügten Prüfexperiment: echter Ausführer und PostgreSQL, Bearbeiter wirft synthetischen OOM, ausschließlich `JobRepository.fail` wird mit einem Spy auf einen dauerhaften Schreibfehler gesetzt. Nach den Schreibversuchen ist die Zahl der Aufrufe des ersetzbaren Ausgangs null; ein anschließend eingestellter Auftrag wird erledigt.

**Vorschlag:** Die Entscheidung über den ursprünglichen fatalen Error unabhängig vom Erfolg des Ergebnisvermerks in einem `finally` oder gleichwertigen äußeren Kontrollfluss durchsetzen. Den Vermerk begrenzt bestmöglich versuchen; ein fehlender Vermerk darf den notwendigen Prozessabbruch nicht verhindern. Test für OOM plus fehlschlagenden Vermerk ergänzen.

### R2 · P3 · Die Stoppmarkierung überstimmt auch einen endgültigen Fachfehler

**Ort:** `backend/src/main/java/taskmanager/jobs/internal/JobExecutor.java:329` bis `:340`.

`record` prüft nur, ob der Faden vom Stopp markiert wurde und kein Zeitlimit ausgelöst hat. Die Art des Fehlers wird erst anschließend ausgewertet. Deshalb wird auch ein ausdrücklich endgültiger `JobFailure.permanent` zu `job.interrupted`, sofort zurückgestellt und nicht gezählt. Ein Fachfehler während der Abbruchbehandlung oder ein mit der Stoppunterbrechung konkurrierender Fehler verliert damit seinen Code und die Endgültigkeit. Bei wiederholt passenden Neustarts kann derselbe Auftrag unbegrenzt wieder anlaufen; ohne weitere Stopps ergibt sich daraus keine autonome Endlosschleife.

**Nachstellung:** `permanentFailureDuringStopIsIncorrectlyRefunded`: ein Bearbeiter wartet; beim Unterbrechen meldet er ausdrücklich `JobFailure.permanent("review.rejected", ...)`. Nach `stop()` steht der Auftrag trotz `max_attempts=1` auf `queued`, `attempts=0`, `error_code=job.interrupted`.

**Vorschlag:** Abbruchursache pro laufendem Versuch explizit führen und den Vertrag für weitergereichte/umhüllte Unterbrechungen festlegen. Echte endgültige Fachfehler und fatale VM-Fehler dürfen nicht allein wegen einer Fadenmarkierung in eine kostenlose Wiederholung umgedeutet werden. Test mit konkurrierendem Fachfehler ergänzen.

### R3 · P3 · Nach drei Sekunden geht der Nachweis einer tatsächlichen Stoppunterbrechung verloren

**Ort:** `backend/src/main/java/taskmanager/jobs/internal/JobExecutor.java:213`, zusammen mit `:329`.

`stop()` löscht nach dem zweiten Warten sämtliche Markierungen, auch wenn ein unterbrochener Bearbeiter noch lebt und seine Aufräumarbeiten fortsetzt. Meldet er danach seine tatsächliche `InterruptedException`, behandelt `record` sie als normalen Fehler. Beim letzten Versuch wird der Auftrag endgültig `failed` mit `job.failed`, obwohl der Stopp die Unterbrechung verursacht hat. Ein solcher Ablauf ist möglich, solange weitere Shutdown-Arbeiten den Prozess noch am Leben halten, und direkt bei einem Stopp des Ausführers im laufenden Prozess.

**Nachstellung:** `interruptedHandlerEndingAfterStopLosesItsRefund`: der Bearbeiter fängt die Stoppunterbrechung, wartet für seine Aufräumphase auf eine Testschranke und wirft dieselbe Ausnahme erst nach Rückkehr von `stop()` weiter. Ergebnis: `failed`, `attempts=1`, `job.failed` statt `queued`, `attempts=0`, `job.interrupted`.

**Vorschlag:** Die Markierung am Versuch bis zu dessen tatsächlichem Ende halten und dort entfernen. Für noch laufende Bearbeiter beim endgültigen Prozessende die Grenzen des kooperativen Stopps ausdrücklich dokumentieren; keine vorzeitige Rückgabe mit paralleler Fachwirkung zulassen.

## Weitere Prüfungsergebnisse und Grenzen

- **Abholen/Nebenläufigkeit:** Die CTE in `JobRepository.java:69` bis `:76` sperrt die ausgewählte Jobzeile mit `FOR UPDATE SKIP LOCKED`. Das Update und der aus dem alten `started_at` abgeleitete Wert gehören zu derselben Anweisung und Transaktion. Ein zweiter Ausführer überspringt die gesperrte Zeile. Der vorhandene Test mit zwei Ausführern und 150 Aufträgen prüft Einmaligkeit und Überschneidungen. Die Nutzung entspricht dem von PostgreSQL beschriebenen Warteschlangenfall ([SELECT, Sperrklausel](https://www.postgresql.org/docs/18/sql-select.html#SQL-FOR-UPDATE-SHARE)). Das beweist keine Exactly-once-Fachwirkung nach Reservierungsverlust; H3 des Vorberichts bleibt bestehen.
- **Rückgabe:** `giveBack` verlangt `state='running'` und den Besitzer, dekrementiert genau einmal und setzt `run_at` auf jetzt. Fremder Besitzer und zweite Rückgabe ändern nichts. `releaseExpired` bleibt unverändert und verbraucht bei einem Absturz weiterhin den Versuch. Regelmäßige Stopps vor dem Ende eines langen Bearbeiters können gewollt beliebig viele kostenlose Wiederholungen erzeugen; das folgt aus Holgers Entscheidung, nicht aus einem Zählerunterlauf.
- **API/Oberfläche:** `startedAt` bedeutet Beginn des letzten Laufs und bleibt bei erneutem Einstellen erhalten; der nächste Claim überschreibt ihn. Vertrag und generierte Typen sagen dies ausdrücklich. Die Browserattrappe behält ihn ebenfalls. `JobsPage` zeigt Erstellungszeit, Fälligkeit und Abschluss, verwendet `startedAt` aber nicht; daraus entsteht keine falsche Anzeige eines gerade laufenden Auftrags. Ein Claim vor einem Absturz kann bereits `repeated=true` für den nächsten Lauf begründen, obwohl der Handler noch nicht erreicht wurde. Diese konservative Aussage ist für den Wiederholungsnachweis sinnvoll.
- **Fatalitätsgrenze:** Stacküberlauf nach Abwickeln des Stacks weiterzubehandeln und sonstige `VirtualMachineError` zum Anlass eines Neustarts zu nehmen, ist für diesen Auftrag plausibel. „Harmless“ im Kommentar ist zu absolut: Teilwirkungen bleiben möglich und brauchen weiter den Nachweis über die Job-ID. Ein synthetisch geworfener OOM beweist keine Funktionsfähigkeit bei tatsächlich erschöpftem Heap oder fehlenden nativen Fäden. Logging, UUID-Erzeugung, JDBC und das Anlegen des Exit-Fadens brauchen selbst Ressourcen.
- **Exit/Shutdown:** `exit` startet bereits einen separaten, nicht als Daemon laufenden Faden. Der Ausführer kann zurückkehren, während der Shutdown Hook ihn in `stop()` abwartet. Der typische Selbstwartezyklus durch `System.exit` direkt aus dem Ausführer besteht hier nicht. Andere hängende Hooks oder unbeschränkt blockierende Datenbankaufrufe können einen Exit trotzdem verzögern; Java wartet auf die Hooks ([Java 25 Runtime](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Runtime.html#exit(int))).
- **Unterbrechung während des Vermerks:** `Thread.interrupted()` in Zeile 304 entfernt nur eine bereits zugestellte Unterbrechung. Der Stopp kann danach in `succeed`/`fail`/`giveBack` unterbrechen, weil er alle lebenden Fäden markiert, ohne die Handlerphase mit dem Ergebnisvermerk zu synchronisieren. Bei einem dadurch abgebrochenen Schreibversuch bleibt die Reservierung übrig und kann letztlich einen Versuch verbrauchen. Der Watchdog besitzt eine solche Abstimmung (`done`), der Stopp nicht. Dies ist eine verbleibende Grenze des Stopppfads, keine durch die CTE verursachte Regression.
- **Dokumentation:** DEMO.md verspricht zu pauschal, laufende Aufträge warteten nach dem Stopp wieder. Innerhalb der ersten 15 Sekunden erfolgreich endende Aufträge sind erledigt; echte Fehlschläge zählen, unkooperative Bearbeiter und fehlgeschlagene Vermerke können bis zum Reservierungsablauf laufen. Die konkrete Frist ist korrekt. H6 des Vorberichts (README behauptet zusätzlich einen aktiven Web-Ausführer, Compose schaltet ihn ab) bleibt ausdrücklich außerhalb TASK-055.

## Abnahmekriterien und tatsächlich gelaufene Prüfungen

Tatsächlich gelaufen: vollständiges Backend `mvn verify` mit JDK 25.0.4.1 gegen native PostgreSQL 18.6: **349 Tests grün**, davon 340 vorhandene Tests und neun Review-Experimente; keine Fehler, keine Skips, Paketbau erfolgreich. Frontend `npm run verify`: **77 Tests grün**, Typprüfung, Generierung und Build erfolgreich; Browsertests: **114/114 grün**. Repositoryvalidierung und Aufgabenübersicht grün; Python-Suite 38 Tests, ein erwartetes Windows-Skip (Unix-Zeitzonenwechsel); `git diff --check` sauber. Die Probe `stopCanInterruptTheOutcomeWrite` bestätigt mit einem künstlich unterbrechbaren Ergebnisvermerk den oben erläuterten Stopprandfall: Auftrag bleibt `running` mit einem verbrauchten Versuch. Die abschließenden Messergebnisse und Reproduktionsbefehle stehen unter [review-evidence/task-055](review-evidence/task-055/README.md).

| Kriterium TASK-055 | Abgleich |
|---|---|
| E1 umgekehrt: nach Error läuft weiterer Auftrag | Vorhandener neuer Test `anErrorOfAHandlerIsRecordedAndTheWorkerThreadGoesOn`; zusätzlich direkte Wiederholung des alten E1 mit umgekehrter Erwartung. Fataler Sonderpfad siehe R1. |
| E2 umgekehrt: letzter Versuch nach Stopp wartend, Zähler unverändert | Neuer Test `aStopDuringTheLastAttemptGivesTheJobBackWithoutUsingTheAttempt`; zusätzlich altes E2 mit umgekehrter Erwartung. Normalfall erfüllt, Randfälle R2/R3. |
| E3 umgekehrt: Verwaltungswiederholung sieht `repeated=true` | Neuer Test `aJobPutInTheQueueAgainByTheAdministrationKnowsThatItRanBefore`; zusätzlich altes E3 mit umgekehrter Erwartung. Erfüllt. |
| Stoppfrist über Gnadenfrist | 30 > 15 + 3 für beide Dienste; erfüllt. |
| Alle bestehenden Tests bleiben grün | Siehe ausgeführte Backend-, Frontend- und Browsertests im Nachweis. Die fünf neuen Tests prüfen zusätzlich Fatalitätsklassifikation und ersten/zweiten Lauf, decken R1 bis R3 aber nicht ab. |
| Imageprüfung bleibt grün | GitHub-Check `image` von PR #183 erfolgreich; Protokoll einschließlich `tools/demo/image-check.sh`, Workerstart und Neustart gelesen. [CI-Lauf](https://github.com/1uedev/task-manager/actions/runs/40643708546/job/122667521910). Kein lokaler Imagebau; Docker-Daemon war nicht verfügbar. Der CI-Neustart ist kein Test eines über 15 Sekunden laufenden Bearbeiters. |
| Getrennte KI-Prüfung, fachliche Abnahme durch Holger | Diese Prüfung erfüllt den technischen Prüfauftrag; keine Abnahme von TASK-054 oder TASK-055. |

**Empfehlung:** R1 vor einem fachlichen Verbraucher beheben, R2 und R3 mit einer eindeutigen Abbruch- und Ergebnisphase im selben kleinen Folgeauftrag korrigieren. F3 und die Konfigurationskorrektur F4 können als technisch behoben gelten. Der Bericht meldet keine Aufgabe als abgenommen.
