# Prüfnachweise TASK-026 / PR #109 und #110

Sitzung `codex-20261020-projects-admin-review-01`, 20. Oktober 2026. Geprüfter Stand: `cf89e43292ba6d7415aa507edc2d4ab8186a2401`. Bewertung und Fundstellen: [Prüfbericht](../../REVIEW_TASK-026_PR-109.md).

## Inhalt und Wiederholung

`project-review-probes.patch` ergänzt nur die vorhandene `ProjectsApiTest`. `results.txt` trennt eigene Ausführung von gelesener CI und enthält ausgewählte Ergebniszeilen ohne Kennwörter, Sitzungstoken oder private Rechnerpfade. Prozesskennungen gehören zur flüchtigen synthetischen Prüfdatenbank.

In einem eigenen Wegwerfklon des geprüften Commits mit Java 25.0.4.1+1 und PostgreSQL 18.6:

```sh
git apply --unidiff-zero docs/review-evidence/task-026/project-review-probes.patch
cd backend
./mvnw "-Dtaskmanager.test.jdbc-url=$REVIEW_JDBC_URL" \
  "-Dtaskmanager.test.username=$REVIEW_DB_USER" "-Dtaskmanager.test.password=$REVIEW_DB_PASSWORD" \
  '-Dtest=ProjectsApiTest#review*' test
```

Die Variablen enthalten ausschließlich die eigene synthetische Testverbindung. Der Testdatenbank-Benutzer benötigt Rechte zum Erstellen der isolierten Testdatenbanken. Erwartung auf dem geprüften Stand: **8 Tests, 6 erfolgreich, 2 fehlgeschlagene Assertions**, keine Ausführungsfehler oder übersprungenen Tests:

- `reviewNullOrMissingCategoryMustBeValidation400`: erwartet zweimal 400, erhält zweimal 500 (F1/P2).
- `reviewBusyForeignProjectMustStayIndistinguishableFromUnknown`: erwartet 404 für die gleichzeitig gesperrte fremde Kennung, erhält 500 (F2/P3).

Die tatsächliche eigene Ausführung erfolgte zusammen mit den [vier TASK-041-Proben](../task-041/README.md): insgesamt 12 Tests, genau diese zwei Fehlschläge. Das sind absichtlich auf das gewünschte Vertragsverhalten gerichtete Regressionen; die erwartete Assertion-Niederlage belegt den Fehler im geprüften Produktstand.

## Aussagekraft der Sperrproben

Die Hilfen ermitteln die PostgreSQL-Prozesskennung der haltenden Transaktion und verlangen sie in `pg_blocking_pids` des wartenden Aufrufs. Reine Threadüberlappung oder bloßes Schlafen genügt nicht. Die Wartefristprüfung bei F2 wartet bewusst auf den wirklichen Sperrtimeout. Die meisten Gegenarme sind echte HTTP-Anfragen; der Arm „bereits erlaubtes Lesen vor Entfernen“ ist ein Dienstaufruf mit echtem Identitätskontext in einer gehaltenen Testtransaktion gegen einen HTTP-Aufruf.

Entzugsfixtures verwenden SQL mit derselben Projekt-/Revisionssperre und Versionserhöhung. Sie prüfen den Adapter und den wartenden Schreibweg, behaupten aber keinen Test einer noch nicht vorhandenen Rechteverwaltungs-HTTP-API. Der gemischte Mitgliedskonto-/Passwortresetversuch benutzt echte HTTP-Wege und unveränderte Produktionssperren.

Nach dem Versuch wurde die erweiterte Testdatei bytegleich wiederhergestellt. Zusammen mit den unveränderten Verwaltungs-/Wiring-Tests wurde sie erneut mit `clean test` ausgeführt: **23 Tests erfolgreich**. `git apply --check --unidiff-zero` bestätigt die Anwendbarkeit des Nachweispatches auf die wiederhergestellten Quellen. Kein Produktcode wurde für die Versuche geändert; der Patch bleibt Dokumentationsbeleg und wird nicht als regulärer grüner Test eingecheckt.
