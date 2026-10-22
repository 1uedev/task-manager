# Prüfprotokoll TASK-030 / PR #141

22. Oktober 2026, Sitzung `codex-20261022-task030-followup-01`. Ausgeführt auf main `a8489bcd07570e679a2ba77870ad002a4a53f1c3`, Dateibaum identisch mit PR-Kopf `aa0202f98e40191237c061c7cf3274730c36604f`. Unveränderte Produktquellen und vorhandene Tests. Eigene flüchtige PostgreSQL-18.6-Instanz auf Loopback, leere synthetische Datenbank, Java 25.0.4.1+1, Node 24.22.0/npm 11.19.1 und Chromium über die installierte Chrome-Laufzeit.

## Backend und vorhandene Frontendprüfungen

In `backend/`, mit den dokumentierten Parametern für eine externe Testdatenbank:

```text
mvnw.cmd -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:61545/task030_followup_20261022 -Dtaskmanager.test.username=reviewer -Dtaskmanager.test.password=synthetic-review-only -Dtest=PermissionsApiTest package
```

Surefire: `taskmanager.PermissionsApiTest`, tests=7, failures=0, errors=0, skipped=0. Darunter `aRightsLockNotGrantedInTimeAnswersRetryableBusy`. Maven BUILD SUCCESS, 44,895 Sekunden, Ende 2026-10-22T15:38:33+02:00. Das ist eine eigene Ausführung der unveränderten sieben Tests, kein eigener vollständiger Backendlauf.

In `frontend/`: `npm ci`, `npm run verify`, `npm run test:browser`. Installation, Vertragstypen, Typprüfung und Produktionsbuild erfolgreich; 68/68 Unit-Tests; 67/67 Chromium-Tests in zehn Suites, 48,552 Sekunden. Die letzte Suite arbeitet gegen die vorhandene API-Nachbildung, nicht gegen PostgreSQL.

## Eigene echte Gegenproben

[replay.mjs](replay.mjs) ist ein eigener Prüfhelfer unter den Berichtsnachweisen. Er ändert keinen Produktcode. Voraussetzungen: ausschließlich eine leere lokale synthetische Datenbank, das unveränderte gebaute JAR, der Produktionsbuild unter `frontend/dist`, installierte Frontendabhängigkeiten und ein Chromium-Browser. Das JAR mit Pflichtkonfiguration und HTTP-Port 61546 zunächst als `bootstrap-admin reviewadmin` ausführen; die einmalige Ausgabe nur in einer privaten Laufzeitdatei außerhalb des Berichts sichern. Danach dasselbe JAR als lokalen Server starten.

Beispielaufruf aus der Repositorywurzel:

```text
node docs/review-evidence/task-030-pr-141/replay.mjs PRIVATE_BOOTSTRAP_LOG 61546 61547
```

Für die Sperrprobe müssen `PSQL`, `PGHOST`, `PGPORT`, `PGUSER` und `PGDATABASE` auf genau die eigene flüchtige Datenbank zeigen. `TASKMANAGER_BROWSER` kann einen expliziten Chromium-Pfad setzen. `REVIEW_RESUME=1` erlaubt die Fortsetzung auf einem zuvor von diesem Skript erzeugten Bestand nach bereits abgeschlossenem Erstwechsel; bei einer frischen Ausführung weglassen. Nicht gegen einen bestehenden Produktivbestand ausführen.

Der Helfer legt über die echte öffentliche API künstliche Konten, eine zweite Unternehmensverwaltung, eine Bearbeitungsgruppe, Projekt und Ticket an. Ausgegebene Einmalpasswörter und Cookies bleiben im Speicher beziehungsweise in privaten Bootstrapdateien. Das im Skript verwendete ausdrücklich synthetische Prüfpasswort ist kein Produktstandard. Der lokale Transportserver liefert den unveränderten Produktionsbuild aus und leitet API-Anfragen samt Cookie-/CSRF-Verhalten an das echte JAR weiter. Bei F1/F2 hält der Browser nur die Zustellung fertig empfangener echter Antworten an; Status und Antwortinhalt bleiben unverändert.

Endgültiger Lauf: **11 PASS, null FAIL, keine unerwarteten Seitenfehler**, [vollständige bereinigte Ergebnisse](replay-results.json). Er prüft unter anderem die unveränderte Ticketversion beim Feldentzug, das Fehlen alter Historienwerte während der ausstehenden Antwort, die im Server noch nicht gespeicherte spätere Eingabe, Rückfrage und zweiten Speichervorgang, sechs DE-/EN-Formularfälle mit `beforeunload`, die reale Abmelde-/CSRF-/Anmeldesequenz, verschwundene Verwaltungslinks sowie den realen Rechtesperrhalter und Rollback mit später erfolgreicher Neuanlage.

Die ersten Prüfroutinen hatten unpassend exakte Pflichtfeldselektoren, eine zu frühe Hashprüfung und beim Wiederholen einen unverändert gewordenen Titel. Diese Probleme des eigenen Helfers wurden behoben; Produktcode blieb unverändert. Der endgültige Lauf enthält jeweils eine tatsächliche neue Titeländerung und wartet auf die bestätigten Antworten. Keine Aussage, die Nachbesserung wurde selbst durch vorübergehendes Zurückändern der Produktquellen rot geprüft.

Die drei gespeicherten Bilder wurden nach dem endgültigen Lauf geöffnet und angesehen. Ihre Übertragung wurde nicht abgeschlossen; sie bleiben lokal gesichert und sind nicht Bestandteil dieses PRs:

- `01-history-after-field-withdrawal.jpg`: Beschreibung verborgen, Historie ohne alte Beschreibungswerte, Version 3 unverändert.
- `02-later-input-still-unsaved.jpg`: Erste Speicherung erfolgreich, spätere Beschreibung bleibt im Feld und ist erneut speicherbar; zu diesem Zeitpunkt noch nicht im Backend gespeichert.
- `03-navigation-after-rights-withdrawal.jpg`: Titel nur lesend, keine Konten-/Gruppenlinks; frische Sitzung hat keine Verwaltungsrechte.

Browser, lokaler Transportserver, eigene Anwendung und Datenbank wurden nach Abschluss der Prüfungen beendet. Private Rohlogs und Bootstrapausgabe werden nicht veröffentlicht.

## Repositorypflichtprüfungen und H1

```text
python scripts/validate_repository.py
python scripts/task_board.py --check
python -m unittest discover -s tests -v
git diff --check
```

Validator, Aufgabenübersicht und Whitespaceprüfung erfolgreich. Python mit UTF-8 und eigenem TEMP/TMP: `Ran 38 tests in 31.466s`, `FAILED (errors=1, skipped=1)`. 36 Tests bestanden. Einzige Auslassung: `SessionTimeTests.test_local_time_zone_plays_no_role`, mangels `time.tzset` unter Windows.

Einziger Fehler: `LostAnswerTests.test_change_that_could_not_connect_stays_a_plain_error`. Der Test erwartet `ConnectionRefusedError`; `http.client.HTTPConnection.connect` / `socket.connect` liefert am geschlossenen lokalen Port nach zwei Sekunden `TimeoutError: timed out`. Isoliert unverändert wiederholt:

```text
python -m unittest discover -s tests -p test_demo_tool.py -k test_change_that_could_not_connect_stays_a_plain_error -v
Ran 1 test in 3.013s
FAILED (errors=1)
TimeoutError: timed out
```

Die Datei wurde in PR #141 nicht verändert. Diese Beobachtung wird als H1/P3 getrennt von den sechs Produktbefunden ausgewiesen; keine Teständerung oder versteckte Auslassung zur Herstellung eines grünen Ergebnisses.

Zusätzlich nur gelesen: [Repository-CI 39780621683](https://github.com/1uedev/task-manager/actions/runs/39780621683), Job 119830628229, exakter PR-Kopf. Relevante Zeilen: `test_change_that_could_not_connect_stays_a_plain_error ... ok`, `Ran 38 tests in 6.763s`, `OK`. Fremder Ubuntu-Lauf, keine eigene Ausführung. Andere CI-Läufe wurden nur als erfolgreiches Statusmetadatum gelesen und nicht als eigene Tests ausgegeben.
