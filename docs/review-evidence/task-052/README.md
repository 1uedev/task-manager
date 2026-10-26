# Nachweise zur Nachprüfung von TASK-052

Sitzung `claude-20261026-review-052-01`, ausgeführt am 26. Oktober 2026 auf main `2616bb5a74f9dfaa944e49233d075f489a7d7ebf` (PR #172). Einordnung und Schwere stehen im [Prüfbericht](../../REVIEW_TASK-052_PR-172.md).

- [ReviewTask052ExperimentTest.java.txt](ReviewTask052ExperimentTest.java.txt): Rahmen aus den [Nachweisen zu TASK-044](../task-044/README.md) mit zwölf Prüftests. R1 bis R6 wiederholen E1 bis E6 gegen den behobenen Code, N1 bis N7 prüfen die Ränder der Behebung. N1 erwartet das Verhalten aus dem Vertrag und schlägt auf dem geprüften Stand fehl (Befund N1); alle anderen sind grün.
- [results.txt](results.txt): gekürzte, selbst ausgeführte Ergebnisse. Keine Passwörter, Sitzungscookies oder Maschinenpfade.

## Reproduktion

Wie bei TASK-044: eine leere synthetische PostgreSQL Testdatenbank bereitstellen, die Datei nach `backend/src/test/java/taskmanager/projects/ReviewTask052ExperimentTest.java` kopieren und mit `-Dtest=ReviewTask052ExperimentTest` sowie den Parametern `-Dtaskmanager.test.jdbc-url`, `-Dtaskmanager.test.username`, `-Dtaskmanager.test.password` und gegebenenfalls `-Dtaskmanager.test.server-version` ausführen. Erwartet: 12 Tests, ein Fehlschlag (N1). Die Datei danach wieder entfernen.
