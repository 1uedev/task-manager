# Nachweise zur Prüfung von TASK-044

Sitzung `claude-20261025-review-044-01`, ausgeführt am 25. Oktober 2026. Grundlage ist main `37580d001210d21f1fa30f5726f02ac6227a10ba`; der Produktcode von TASK-044 stammt aus PR #151 (`8b7928d`). Einordnung und Schwere stehen im [Prüfbericht](../../REVIEW_TASK-044_PR-151.md).

- [ReviewTask044ExperimentTest.java.txt](ReviewTask044ExperimentTest.java.txt): Kopie des Rahmens von `ProjectConfigurationApiTest` (echte HTTP Schnittstelle, echte Module, PostgreSQL) mit sechs Prüftests statt der ursprünglichen Testmethoden. E1 bis E5 erwarten das richtige Verhalten und schlagen auf dem geprüften Stand fehl (Befunde F2, F1, F3, F4, F5). E6 ist die Gegenprobe zur Veröffentlichung unter gleichzeitigen Ticketänderungen und ist grün.
- [results.txt](results.txt): gekürzte, selbst ausgeführte Ergebnisse. Keine Passwörter, Sitzungscookies oder Maschinenpfade.

## Reproduktion in einem eigenen Arbeitsbereich

Eine leere synthetische PostgreSQL Testdatenbank bereitstellen; der Testbenutzer muss weitere Datenbanken anlegen dürfen.

```sh
cp docs/review-evidence/task-044/ReviewTask044ExperimentTest.java.txt \
  backend/src/test/java/taskmanager/projects/ReviewTask044ExperimentTest.java
cd backend
./mvnw \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/review \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  -Dtest=ReviewTask044ExperimentTest test
```

Erwartet auf dem geprüften Stand: 6 Tests, 5 Fehlschläge (E1 bis E5), E6 grün. Bei einer anderen PostgreSQL Version als 18.6 zusätzlich `-Dtaskmanager.test.server-version=<version>` setzen. Die Datei danach wieder entfernen; sie ist keine Produktkorrektur.
