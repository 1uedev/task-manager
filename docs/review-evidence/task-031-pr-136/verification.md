# Prüfprotokoll TASK-031 / PR #136

Sitzung `codex-20261022-task031-followup-01`, 22. Oktober 2026. Eigener Klon auf main `124d2d81425ce4012b257049fc5c932e84ad2b44`; Dateibaum identisch mit PR-Kopf `5ce767cd0503a5a81b5468a6911fc722d5b7edc6`. Nur synthetische Daten. Keine Änderungen an Produktcode, vorhandenen Tests oder Aufgabenbüchern.

## Eigene Ausführungen

### Repository und Python

Aus dem Repositorywurzelverzeichnis, Python mit UTF-8-Ausgabe und eigenem temporärem Verzeichnis:

```text
python scripts/validate_repository.py
python scripts/task_board.py --check
python -m unittest discover -s tests -v
git diff --check
```

Validator, Aufgabenübersicht und Whitespaceprüfung erfolgreich. Python-Suite: 35 Tests, null Fehler, eine Auslassung. Ausgelassen: `SessionTimeTests.test_local_time_zone_plays_no_role`, weil Windows `time.tzset` nicht anbietet. Neun der zehn neuen Werkzeugtests laufen lokal; insbesondere alle fünf `LostAnswerTests`, zwei UTC-/Fristtests und beide `ComplexSearchTests` bestehen. Die Schleife für vier Zeitzonen besteht ergänzend im gelesenen Ubuntu-CI-Log, nicht im eigenen Windows-Lauf.

### Suchmodul auf PostgreSQL 18.6

Windows, Java 25.0.4.1+1, eigener nativer PostgreSQL-18.6-Testserver auf Loopback, eigenständige leere Testdatenbank. `show server_version` ergibt `18.6`. Maven verwendet einen separaten lokalen Cache. Aus `backend/`:

```text
mvnw.cmd -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:61535/task031_followup_20261022 -Dtaskmanager.test.username=reviewer -Dtaskmanager.test.password=synthetic-review-only -Dtest=SearchApiTest package
```

Ergebnis aus Surefire / Maven:

```text
taskmanager.search.SearchApiTest: tests=13 errors=0 skipped=0 failures=0
aRebuildCopesWithMoreTicketsThanOneStatementTakesParameters: passed, 24.926 s
aRebuildRestoresTheProjectionFromTheOwningModules: passed, 0.797 s
aTagRenameAndARebuildWaitForEachOtherInsteadOfDeadlocking: passed, 0.965 s
BUILD SUCCESS
Total time: 01:38 min
Finished at: 2026-10-22T10:29:14+02:00
```

Die restlichen zehn Suchtests bestehen ebenfalls. Der Mengentest legt 70.000 Tickets in einer Transaktion an, baut neu auf, prüft genau 70.000 Projektionszeilen und rollt zurück. Keine Aussage, dass dieser Test allein Taginhalte oder den vollständigen 90.000-Ticket-Neustart prüft. Kein eigener roter Lauf mit rückgängig gemachtem Produktfix. Die eigene Datenbankinstanz wurde nach Abschluss geordnet beendet.

### Eigene Gegenproben

```text
python docs/review-evidence/task-031-pr-136/followup_probes.py
```

[Ausführbares Skript](followup_probes.py), [vollständige kurze Ausgabe](followup-probes.txt). Alle Assertions erfolgreich. Der lokale HTTP-Server dient ausschließlich der synthetischen Transportprobe; sein Speicherzähler ist keine echte Produktdatenbank. Er beweist, wie oft das Werkzeug eine Anfrage sendet, nachdem ein Server die simulierte Änderung bereits angenommen hat. `measurement classification` wird aus der geworfenen Ausnahmeklasse und dem unveränderten Fehlerzweig von `measure` abgeleitet; kein vollständiger Lastlauf mit injizierten Fehlern.

Weitere Assertions: 10.000 passende Kombinationen des unveränderten positiven Generators, JSON/Markdown-Tabelle exakt übereinstimmend, Profil-/Treffer-/Aktionssummen korrekt, Zielwerte konsistent, getrennte Speicherung/Gesamtaktion mit kontrollierter Uhr und UTC-Rechnung über beide Sommerzeitwechsel. Historische Quantile werden mangels Einzelmesswerten nicht rekonstruiert.

## Gelesene CI-Nachweise, keine eigene Ausführung

Die folgenden Logs gehören zum exakten Kopf von PR #136. Nur die relevanten Zeilen sind hier wiedergegeben; erfolgreiche CI ist keine fachliche Abnahme und kein Nachweis menschlicher Zustimmung.

| Lauf | Job | Gelesenes Ergebnis |
|---|---:|---|
| [Repository 39741505344](https://github.com/1uedev/task-manager/actions/runs/39741505344) | 119699558259 | 35 Python-Tests, OK; `test_local_time_zone_plays_no_role ... ok` am 2026-10-22T07:35:14Z |
| [Backend 39741505255](https://github.com/1uedev/task-manager/actions/runs/39741505255) | 119699557741 | 207 Tests, Failures 0, Errors 0, Skipped 0; SearchApiTest 13; BUILD SUCCESS; Lifecycle check passed |
| [Image 39741505359](https://github.com/1uedev/task-manager/actions/runs/39741505359) | 119699558906 | Imagebau erfolgreich; `session ends after the default of 24 hours (24.00 h left)`; `session of anna is still valid after the restart`; `image check passed` |

Im Backend-Log nennen Flyway und die Prüfung von `show server_version` den Server 18.6; 16.15 ist nur die Version des psql-Clients. Auch der eigene Suchlauf verwendet 18.6. Kein eigener erneuter Dockerlauf, kein vollständiger neuer Backend-/Frontendlauf und keine Wiederholung der historischen Referenzmessung. Der Containeraufbau ist gegenüber PR #134 unverändert.
