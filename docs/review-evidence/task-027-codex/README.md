# Prüfnachweise TASK-027 / PR #112

Unabhängige Sitzung `codex-20261020-tickets-review-01`, 20. Oktober 2026. Geprüft: `9a6a20048189e89cb5d23afdafef0fd0a2826305`. Bewertung: [Prüfbericht](../../REVIEW_TASK-027_PR-112_CODEX.md).

Veröffentlicht als zusätzliche historische Evidenz am 21. Oktober 2026: F1 wurde inzwischen durch PR #114 korrigiert, TASK-027 durch Holger abgenommen (#118). Diese Proben sind für den ursprünglichen Commit bestimmt und behaupten keinen erneuten Lauf des heutigen main. Sie ersetzen nicht den getrennten Claude-Bericht aus PR #115.

`ticket-review-probes.patch` ergänzt ausschließlich die vorhandene `TicketsApiTest`. Produktcode wurde zu keinem Zeitpunkt verändert. `results.txt` enthält ausgewählte eigene Laufzeilen und getrennt gelesene CI-Evidenz ohne Kennwörter, Sitzungstoken oder private Rechnerpfade. PostgreSQL-PIDs sind nur flüchtige Kennungen der eigenen synthetischen Testinstanz.

## Wiederholung

Eigenen Wegwerfklon des geprüften Commits mit Java 25.0.4.1+1 und PostgreSQL 18.6 verwenden. Die Variablen unten stehen ausschließlich für die eigene synthetische Prüfverbindung. Der Testdatenbankbenutzer muss neue isolierte Testdatenbanken erstellen dürfen. Auch beim lokalen Entwicklerweg benötigt die Anwendung ein nicht leeres synthetisches Prüfkennwort.

```sh
git apply --unidiff-zero docs/review-evidence/task-027-codex/ticket-review-probes.patch
cd backend
./mvnw "-Dtaskmanager.test.jdbc-url=$REVIEW_JDBC_URL" \
  "-Dtaskmanager.test.username=$REVIEW_DB_USER" "-Dtaskmanager.test.password=$REVIEW_DB_PASSWORD" \
  '-Dtest=TicketsApiTest#review*' test
```

Erwartet auf dem geprüften Stand: **11 Tests, 10 erfolgreich, 1 fehlgeschlagene Assertion**, keine Ausführungsfehler oder übersprungenen Tests. `reviewCreateAndEditAreIndependentOfTheReadAction` erwartet 201/200, erhält ohne `ticket.read` zweimal 404 und mit ausschließlich zusätzlich vergebenem `ticket.read` anschließend 201/200. Diese absichtlich auf das beschriebene Vertragsverhalten gerichtete Regression belegt F1. H-C1 ist ein statischer Vergleich des dokumentierten und tatsächlichen Anlagepfads; kein zusätzlicher Laufversuch wird dafür behauptet.

## Aussagekraft und Eingriffe der Fixtures

- Sperrproben prüfen die konkrete Halter-PID in `pg_blocking_pids` des wartenden Vorgangs; keine reine Threadüberlappung.
- Entzug von Aktion, Feldrecht, Projektmitgliedschaft oder Sitzung und die Konfigurationsänderung werden über synthetisches SQL unter den jeweiligen Revisions-/Projekt-/Sitzungssperren hergestellt. Es gibt noch keine Rechteverwaltungs-HTTP-API.
- Der Gegensinn „bereits erlaubte Änderung vor Entfernung“ verwendet den echten Ticketdienst mit vollständigem aktuellen Identitätskontext in einer gehaltenen Transaktion gegen einen echten HTTP-Aufruf zur Mitgliederentfernung.
- Der Deadlockgegner nimmt absichtlich Ticket vor Rechtezeile, um die PostgreSQL-Fehlerabbildung der echten HTTP-Anfrage zu prüfen. Er ist kein behaupteter regulärer Produktpfad. Der Serverlog bestätigt den Zyklus und den Abbruch des HTTP-Prozesses.
- Ein nur im synthetischen Testschema angelegter Trigger lässt das Historieschreiben gezielt scheitern. Der Versuch prüft dadurch Rückabwicklung von Ticket, Zähler und Historie; Trigger/Funktion werden danach entfernt.

Nach dem Lauf wurde die erweiterte Testdatei bytegleich aus der Sicherung wiederhergestellt. Danach `clean test` der Originalklassen `TicketsApiTest,TicketsContractTest`; das Ergebnis steht in `results.txt`. Der Patch wurde mit `git apply --check --unidiff-zero` gegen die wiederhergestellten Quellen geprüft. Er bleibt Dokumentationsnachweis und wird nicht in die reguläre grüne Testsuite übernommen.
