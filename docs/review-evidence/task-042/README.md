# Nachweise zur unabhängigen Prüfung von TASK-042

Sitzung `claude-20261020-taghistory-review-01`, ausgeführt am 20. Oktober 2026 auf main `b85abdddce048fb7a76a44222e61a969cc6417e6` (PR #123 gemergt). Einordnung und Schwere stehen im [Prüfbericht](../../REVIEW_TASK-042_PR-123.md).

- [TagHistoryReviewProbeTest.java.txt](TagHistoryReviewProbeTest.java.txt): eigene Testklasse. Der Vorbau (Anmeldung, Projekte, Rechtevergabe direkt in den Tabellen, Sperrhalter) ist unverändert aus `TagsApiTest` übernommen; darunter folgen sieben Proben mit dem Präfix `review`. Eine davon schlägt auf dem geprüften Stand erwartungsgemäß fehl (F1), sechs bestehen und belegen Verhalten.
- [results.txt](results.txt): gekürzte, selbst ausgeführte Ergebnisse. Keine vollständigen Protokolle, Passwörter, Sitzungscookies oder Maschinenpfade.

## Reproduktion nur in einem eigenen Testarbeitsbereich

Eine leere synthetische PostgreSQL Datenbank bereitstellen; der Testbenutzer muss weitere flüchtige Datenbanken anlegen dürfen.

```sh
cp docs/review-evidence/task-042/TagHistoryReviewProbeTest.java.txt \
  backend/src/test/java/taskmanager/tags/TagHistoryReviewProbeTest.java
cd backend
./mvnw \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/review \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  -Dtest=TagHistoryReviewProbeTest test
```

Auf dem geprüften Stand endet der Lauf mit einem Assertion Fehlschlag. Die Sperrhalter werden auch im Fehlerfall freigegeben. Die Testdatei danach wieder entfernen; sie ist keine Produktkorrektur.
