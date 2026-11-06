# Prüfnachweise TASK-027 / PR #112

Sitzung `claude-20261020-tickets-review-01`, 20. Oktober 2026. Geprüfter Stand: main `9a6a20048189e89cb5d23afdafef0fd0a2826305`. Bewertung und Fundstellen: [Prüfbericht](../../REVIEW_TASK-027_PR-112.md).

`ticket-review-probes.patch` ergänzt nur die vorhandene `TicketsApiTest` um zehn Methoden mit dem Präfix `review`. Er wird nicht als regulärer Test eingecheckt, weil vier Proben absichtlich das gewünschte Vertragsverhalten verlangen und auf dem geprüften Stand fehlschlagen.

## Wiederholung

In einem eigenen Klon des geprüften Commits mit Java 25 und einer leeren synthetischen PostgreSQL:

```sh
git apply --unidiff-zero docs/review-evidence/task-027/ticket-review-probes.patch
cd backend
./mvnw "-Dtaskmanager.test.jdbc-url=$REVIEW_JDBC_URL" \
  "-Dtaskmanager.test.username=$REVIEW_DB_USER" "-Dtaskmanager.test.password=$REVIEW_DB_PASSWORD" \
  '-Dtest=TicketsApiTest#review*' test
```

Erwartung auf main `9a6a200`: **10 Tests, 6 bestanden, 4 Fehlschläge**, keine Ausführungsfehler.

| Probe | Ergebnis auf main | Befund |
|---|---|---|
| `reviewCreateWithoutReadActionMustCreateTheTicket` | erwartet 201, erhalten 404, 0 Tickets | F1 |
| `reviewEditWithoutReadActionMustChangeTheTicket` | erwartet 200, erhalten 404, Titel unverändert | F1 |
| `reviewNulCharacterInTextMustBeValidation400` | erwartet zweimal 400, erhalten zweimal 500 | F2 |
| `reviewCreationWithAnUnknownFieldMustBeRefused` | erwartet 400, erhalten 201 mit Status `new` | F3 |
| `reviewLockedForeignProjectMustAnswerLikeAnUnknownOne` | erwartet 404, erhalten 503 `ticket.busy`; auf #114 bestanden | bekannt aus TASK-026, durch #114 behoben |
| `reviewHiddenChangeShowsInVersionAndUpdatedBy` | bestanden, dokumentiert das Verhalten | H1 |
| `reviewChangeWaitsForConcurrentRightsRevocationAndIsRefused` | bestanden | kein Befund |
| `reviewAssignmentWaitsForConcurrentDeactivationOfTheAssignee` | bestanden | kein Befund |
| `reviewTwoChangesWithTheSameVersionYieldOneConflict` | bestanden | kein Befund |
| `reviewNumbersAndAssigneesStayInsideTheirProject` | bestanden | kein Befund |

Die tatsächliche Ausführung erfolgte in zwei Läufen: neun Proben zusammen, `reviewCreationWithAnUnknownFieldMustBeRefused` danach einzeln. Auf dem Kopf von #114 (`1149476`) liefen die neun Proben ohne F3: 6 bestanden, 3 Fehlschläge (F1 zweimal, F2).

## Aussagekraft der Sperrproben

Die Sperrproben halten die Zeile in einer eigenen Testtransaktion und verlangen mit `pg_blocking_pids`, dass der echte HTTP Aufruf an genau dieser Abfrage wartet; bloße zeitliche Überlappung genügt nicht. Entzug und Deaktivierung werden mit synthetischem SQL nachgebildet, weil es noch keine Rechteendpunkte gibt (TASK-030); sie verwenden dieselben Zeilen wie die echten Wege, ersetzen aber keinen Test aller Aufrufketten. Kein Produktcode wurde für die Proben geändert.
