# Prüfnachweise TASK-041 / PR #108

Sitzung `codex-20261020-projects-admin-review-01`, 20. Oktober 2026. Geprüfter Stand: `cf89e43292ba6d7415aa507edc2d4ab8186a2401`. Einordnung und Grenzen: [Prüfbericht](../../REVIEW_TASK-041_PR-108.md).

## Inhalt

- `admin-review-probes.patch`: zusätzliche Tests und Hilfen für die vorhandene `AdministrationRightsApiTest`; keine Produktänderungen.
- `AdministrationConsumerReviewTest.java.txt`: zusätzliche isolierte Testklasse für vier Bean-Konfigurationen an fünf tatsächlichen Verbraucherhilfen.
- `results.txt`: ausgewählte eigene Laufzeilen und eindeutig getrennte gelesene CI-Belege. Die Prozesskennungen sind flüchtige Kennungen der synthetischen Prüfdatenbank, keine dauerhaften Systemkennungen.

## Wiederholen

In einem eigenen Wegwerfklon des geprüften Commits mit Java 25.0.4.1+1 und PostgreSQL 18.6 ausführen. Nur synthetische Konten/Daten verwenden. Der externe Testdatenbank-Benutzer benötigt die Rechte zum Erstellen der isolierten Testdatenbanken; `TestDatabase` legt sie an. Die folgenden Umgebungsvariablen bezeichnen ausschließlich die eigene lokale Prüfverbindung, keine produktiven Zugangsdaten.

```sh
git apply --unidiff-zero docs/review-evidence/task-041/admin-review-probes.patch
cp docs/review-evidence/task-041/AdministrationConsumerReviewTest.java.txt backend/src/test/java/taskmanager/identity/web/AdministrationConsumerReviewTest.java
cd backend
./mvnw "-Dtaskmanager.test.jdbc-url=$REVIEW_JDBC_URL" \
  "-Dtaskmanager.test.username=$REVIEW_DB_USER" "-Dtaskmanager.test.password=$REVIEW_DB_PASSWORD" \
  '-Dtest=AdministrationRightsApiTest#review*,AdministrationConsumerReviewTest' test
```

Erwartung auf dem geprüften Stand: **4 Tests erfolgreich**. Die eigene tatsächliche Ausführung erfolgte zusammen mit den acht Projektproben, insgesamt 12 Tests mit zwei erwarteten Fehler-Assertions ausschließlich bei TASK-026. Die Matrix enthält 20 einzelne Verhaltensprüfungen innerhalb eines JUnit-Tests.

Für die gemeinsame Wiederholung zusätzlich den [Projektpatch](../task-026/project-review-probes.patch) anwenden und den Filter `AdministrationRightsApiTest#review*,ProjectsApiTest#review*,AdministrationConsumerReviewTest` verwenden.

Die Proben halten produktive Advisory-/Zeilensperren in synthetischen Transaktionen und prüfen `pg_blocking_pids` gegen den exakten Halter. Rechteentzüge werden im Fixture über SQL mit Revisionssperre/-wechsel aufgebaut. Sie ersetzen keine Rechteverwaltungs-HTTP-API. Keine Produktmethode wurde ausgewechselt.

Nach dem Versuch die beiden erweiterten Testdateien aus dem geprüften Commit wiederherstellen und nur die zusätzlich kopierte Klasse entfernen. Vor Auslieferung wurden die Originaldateien bytegleich geprüft und die drei Originalklassen mit `clean test` erneut ausgeführt: **23 erfolgreich**. Die Patchdateien wurden auf die wiederhergestellten Quellen mit `git apply --check --unidiff-zero` geprüft. Die Testpatches gehören als Nachweis in die Dokumentation, nicht in die reguläre Testsuite.
