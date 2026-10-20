# Nachweise zur unabhängigen Prüfung von TASK-028

Sitzung `claude-20261020-tags-review-01`, ausgeführt am 20. Oktober 2026 auf main `6ba41750facf6e204b72911fc360edc57f9c3870` (PR #119 gemergt). Einordnung und Schwere stehen im [Prüfbericht](../../REVIEW_TASK-028_PR-119.md).

- [TagsReviewProbeTest.java.txt](TagsReviewProbeTest.java.txt): eigene Testklasse. Der Vorbau (Anmeldung, Projekte, Rechtevergabe direkt in den Tabellen) ist unverändert aus `TagsApiTest` übernommen; darunter folgen zwölf Proben mit dem Präfix `review`. Vier davon schlagen auf dem geprüften Stand erwartungsgemäß fehl (F1, F2, zweimal F3), acht bestehen und belegen oder dokumentieren Verhalten.
- [results.txt](results.txt): gekürzte, selbst ausgeführte Ergebnisse. Keine vollständigen Protokolle, Passwörter, Sitzungscookies oder Maschinenpfade.

## Reproduktion nur in einem eigenen Testarbeitsbereich

Eine leere synthetische PostgreSQL Datenbank bereitstellen; der Testbenutzer muss weitere flüchtige Datenbanken anlegen dürfen.

```sh
cp docs/review-evidence/task-028/TagsReviewProbeTest.java.txt \
  backend/src/test/java/taskmanager/tags/TagsReviewProbeTest.java
cd backend
./mvnw \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:5432/review \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  -Dtest=TagsReviewProbeTest test
```

Auf dem geprüften Stand endet der Lauf mit vier Assertion Fehlschlägen. Die Sperrhalter werden auch im Fehlerfall freigegeben. Die Testdatei danach wieder entfernen; sie ist keine Produktkorrektur.
