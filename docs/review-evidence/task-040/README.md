# Nachweise zur unabhängigen Nachprüfung von TASK-040

Sitzung `codex-20261019-admin-rights-review-01`, ausgeführt am 20. Oktober 2026. Grundlage ist main `78a858c6e9d4692e5daf5348764c0be94b6fb071`; Produktcode wie im finalen PR #101. Einordnung und Schwere stehen im [Prüfbericht](../../REVIEW_TASK-040_PR-101.md).

- [mutual-reset-probe.patch](mutual-reset-probe.patch): zusätzliche Methode in AdministrationRightsApiTest, zwei echte HTTP-Aufrufe mit den produktiven Autorisierungswegen. Die Testtransaktion verzögert beide Sitzungsprüfungen; `pg_blocking_pids` muss genau deren PID als Halter für zwei verschiedene HTTP-PIDs nennen. Nach Freigabe wird das korrekte Ergebnis ohne HTTP 500 erwartet und auf dem geprüften Stand verfehlt.
- [AdministrationRightsWiringReviewTest.java.txt](AdministrationRightsWiringReviewTest.java.txt): isolierter BeanFactory-/Verbrauchertest für zwei Adapter mit Primary. Erwartet Ablehnung wegen Mehrdeutigkeit; diese bleibt auf dem geprüften Stand aus. Kein HTTP-BYPASS-Versuch in der Standardverdrahtung.
- [results.txt](results.txt): gekürzte selbst ausgeführte Ergebnisse und gesondert markierte gelesene CI-Nachweise. Keine vollständigen lokalen Logs, Passwörter, Sitzungscookies oder privaten Maschinenpfade veröffentlicht.

## Reproduktion ausschließlich in einem separaten Testarbeitsbereich

Eine leere synthetische PostgreSQL-18.6-Testdatenbank bereitstellen; der Testbenutzer muss weitere flüchtige Datenbanken anlegen dürfen. Zunächst den unveränderten Backendlauf durchführen, dann den Patch anwenden und die Textdatei als `backend/src/test/java/taskmanager/identity/web/AdministrationRightsWiringReviewTest.java` kopieren.

```sh
git apply docs/review-evidence/task-040/mutual-reset-probe.patch
cp docs/review-evidence/task-040/AdministrationRightsWiringReviewTest.java.txt \
  backend/src/test/java/taskmanager/identity/web/AdministrationRightsWiringReviewTest.java
cd backend
./mvnw \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/review \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  '-Dtest=AdministrationRightsApiTest#reviewMutualPasswordResetsDoNotReturnServerError,AdministrationRightsWiringReviewTest' \
  test
```

Verbindung und synthetische Zugangsdaten an die eigene Testdatenbank anpassen. Auf dem geprüften Stand endet der Aufruf mit zwei Assertion-Fehlschlägen. Der Halter wird auch im Fehlerfall freigegeben. Die Produktimplementierung bleibt unverändert. Die temporären Testdateien anschließend zurücksetzen beziehungsweise entfernen und die betroffenen Originaltests mit `clean test` neu kompilieren. Den Probe-Patch nicht als Produktkorrektur übernehmen.
