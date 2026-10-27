# Nachweise zur Prüfung von TASK-054

Sitzung `claude-20261027-review-054-01`, ausgeführt am 27. Oktober 2026 auf main `23adfe3`. Einordnung und Schwere stehen im [Prüfbericht](../../REVIEW_TASK-054_PR-179.md).

- [JobReviewExperimentTest.java.txt](JobReviewExperimentTest.java.txt): Kopie des Rahmens von `JobExecutorTest` (echte Ausführer, PostgreSQL) mit einem Ausführerfaden, drei zusätzlichen Bearbeitern und drei Experimenten statt der ursprünglichen Testmethoden. Die Experimente sind so geschrieben, dass sie das beobachtete Verhalten des geprüften Stands festhalten; sie sind grün, solange die Befunde bestehen.
  - E1 (Befund F1): ein `StackOverflowError` im Bearbeiter beendet den einzigen Faden; ein danach eingestellter Auftrag bleibt `queued`, der Ausführer gilt weiter als gesehen.
  - E2 (Befund F2): `stop()` während des einzigen Versuchs; der Auftrag endet `failed` mit `job.interrupted`.
  - E3 (Befund F3): nach `requeue` (wie `JobAdministration.retry`) sieht der nächste Lauf `attempt=1` und `repeated()=false`.
- [results.txt](results.txt): gekürzte, selbst ausgeführte Ausgaben.

## Reproduktion in einem eigenen Arbeitsbereich

```sh
cp docs/review-evidence/task-054/JobReviewExperimentTest.java.txt \
  backend/src/test/java/taskmanager/jobs/internal/JobReviewExperimentTest.java
cd backend
./mvnw -Djava.version=21 \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/review \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  -Dtaskmanager.test.server-version=16.15 \
  -Dtest=JobReviewExperimentTest test
```

Danach die Kopie wieder entfernen; sie ist kein Teil der Testsuite.
