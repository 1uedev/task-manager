# Nachweise zur Nachprüfung von TASK-056

Sitzung `claude-20261028-review-056-01`, ausgeführt am 28. Oktober 2026 auf main `796acf1`. Einordnung steht im [Prüfbericht](../../REVIEW_TASK-056_PR-185.md).

- [probes.patch](probes.patch): sechs zusätzliche Proben in `JobExecutorTest` (echter Ausführer, PostgreSQL), mit fünf kleinen Bearbeitern. Sie halten das erwartete Verhalten des geprüften Stands fest und sind dort grün.
  - P1 `probeRetryableFailureAtStopIsGivenBack`: ein beim Stopp gemeldetes `JobFailure.retryable` wird ohne Zählung zurückgegeben (`job.interrupted`).
  - P2 `probeWrappedInterruptAtStopIsGivenBack`: eine in eine `IllegalStateException` gehüllte Unterbrechung ebenso.
  - P3 `probeNonFatalErrorAtStopCountsAndDoesNotEndTheProcess`: ein nicht fataler Error (`AssertionError`) beim Stopp zählt (`failed`, `job.failed`), der Prozess wird nicht beendet.
  - P4 `probeSwallowedInterruptAtStopEndsAsSuccess`: ein Bearbeiter, der die Unterbrechung schluckt und normal zurückkehrt, endet als `succeeded`.
  - P5 `probeFatalErrorWhoseRecordThrowsAnErrorStillEndsTheProcess`: wirft der Vermerk eines fatalen Errors selbst einen Error, wird der Ausgang trotzdem aufgerufen.
  - P6 `probeStopDoesNotInterruptTheOutcomeWrite`: ein Stopp, während der Erfolg geschrieben wird, unterbricht diesen Schreibversuch nicht (genau ein Aufruf von `succeed`).
- [results.txt](results.txt): selbst ausgeführte Ausgaben, auch gegen den Stand vor TASK-056.

## Reproduktion

```sh
git apply docs/review-evidence/task-056/probes.patch
cd backend
./mvnw -Djava.version=21 \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/review \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  -Dtaskmanager.test.server-version=16.15 \
  -Dtest=JobExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Für den Gegenlauf `backend/src/main/java/taskmanager/jobs/internal/JobExecutor.java` aus `cd25ff8` holen, `STOP_GRACE.toNanos()` durch `stopGrace.toNanos()` ersetzen und das Feld `Duration stopGrace = STOP_GRACE;` ergänzen. Danach den Patch wieder entfernen; er gehört nicht in die Testsuite.
