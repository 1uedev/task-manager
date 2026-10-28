# Nachweise zur Prüfung TASK-055

Prüfbasis main `1666f4c817fcc9fde57cc7cdce343399fa79270f`, 28. Oktober 2026, Sitzung `codex-20261028-task055-review-01`. Einordnung im [Bericht](../../REVIEW_TASK-055_PR-183.md).

## Umgebung und Ausführung

Windows, JDK 25.0.4.1, Maven 3.9.16, echte isolierte PostgreSQL 18.6 (native Installation, kein gepinnter Testcontainer), Node 24.22.0, npm 11.19.1, installierter Chrome über Playwright. Ausschließlich synthetische Daten; kein Produktcode geändert.

- [JobReview055Test.java.txt](JobReview055Test.java.txt): eigenständige Kopie des vorhandenen Testgerüsts mit sechs Prüfungen für R1, R2, R3, Besitzerbedingung, echten Prozessausstieg und Stopp während des Ergebnisvermerks. Die drei Befundtests erwarten ausdrücklich das beobachtete fehlerhafte Verhalten; grün bedeutet hier Reproduktion, nicht Fehlerfreiheit.
- [JobReviewExperimentTest.java.txt](JobReviewExperimentTest.java.txt): E1 bis E3 aus TASK-054, nur mit umgekehrten Ergebnisassertionen (sowie Zählerprüfung für den letzten Versuch). Alte Methodennamen und Kommentare sind zur Nachverfolgbarkeit beibehalten; maßgeblich sind die geänderten Assertions.
- [results.txt](results.txt): ausgewählte tatsächliche Ergebnisse, Maschinenkennung und Zufallsreferenzen entfernt.

Die Dateien wurden nur vorübergehend unter `backend/src/test/java/taskmanager/jobs/internal/` eingefügt und nach der Prüfung entfernt. Reproduktion in einem eigenen Checkout:

```sh
cp docs/review-evidence/task-055/JobReview055Test.java.txt backend/src/test/java/taskmanager/jobs/internal/JobReview055Test.java
cp docs/review-evidence/task-055/JobReviewExperimentTest.java.txt backend/src/test/java/taskmanager/jobs/internal/JobReviewExperimentTest.java
cd backend
./mvnw -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:55455/postgres \
  -Dtaskmanager.test.username=reviewer -Dtaskmanager.test.password=synthetic-review-only \
  -Dtaskmanager.test.server-version=18.6 \
  -Dtest=JobReview055Test,JobReviewExperimentTest test
```

Unter Windows wurde Maven über `mvn.cmd` ausgeführt. Der Kindprozesstest verwendet `java.exe`; unter Linux den Namen auf `java` ändern. Der Kindprozess benutzt dieselbe bereits migrierte Testdatenbank, Benutzer `reviewer`, leeres Kennwort bei ausschließlich lokalem `trust` in dieser isolierten Testinstanz; für eine kennwortgeschützte Testinstanz den synthetischen Testwert auch dort setzen. Der Test startet den realen `JobExecutor` mit JDBC und einem eigenen Shutdown Hook, der `stop()` aufruft. Er prüft die Selbstwartefrage und Exitcode 70, nicht den gesamten Spring- oder Docker-Lebenszyklus und keine echte Speichererschöpfung.

## Prüfprotokoll

Die endgültigen Zahlen stehen in `results.txt`. Frontend: `npm ci`, `npm run verify` (77 Tests, Typprüfung, Generierung, Produktionsbuild), `npm run test:browser` (114 Tests). Backend: vollständige bestehende Suite gegen PostgreSQL sowie die ergänzten Experimente. Der bestehende Test `twoExecutorsRunEveryJobExactlyOnce` prüft 150 Jobs mit zwei Ausführern und mehreren Fäden, keine doppelten Ausführungen/Überschneidungen.

Repository: `scripts/validate_repository.py`, `scripts/task_board.py --check`, `python -m unittest discover -s tests -v` (38 Tests, ein erwartetes Unix-Zeitzonen-Skip unter Windows), `git diff --check`.

Erste Anläufe scheiterten an lokalen Ausführungsrechten bzw. Socketzugriff; sie wurden mit geeigneten Rechten wiederholt. Ein Backendanlauf verwendete ein leeres Testkennwort, das die verpflichtende Anwendungskonfiguration verweigerte; danach wurde ein synthetischer Wert verwendet. Ein eigener Versuch scheiterte zunächst am Spy auf einem Spring-Proxy, anschließend wurde ausschließlich für die Fehlerinduktion ein unverpacktes Repository verwendet. Diese Anläufe sind keine Produktbefunde und werden nicht als grüne Prüfungen gezählt.

Die Imageprüfung wurde nicht lokal ausgeführt (kein laufender Docker-Daemon). Gelesener externer Nachweis: [Image-Job zu PR #183](https://github.com/1uedev/task-manager/actions/runs/40643708546/job/122667521910), erfolgreich am 28. Oktober 2026, PR-Head `fb2bcfa321623bd4f96f49b72e8082306132b62b`, getestet als PR-Mergeref `7630332ee8830a92361fefe3fcf3a319c8771af3`. Das Protokoll belegt `tools/demo/image-check.sh`, getrennten Worker, Neustart und `image check passed`. Kein eigener SIGTERM-Test unter Docker mit lang laufendem Handler, kein eigener Lauf von `backend/scripts/lifecycle-check.sh`.
