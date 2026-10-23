# Ausführungsnachweis TASK-043 / PR #146

Sitzung `codex-20261022-task043-review-01`, eigener Klon, 22.–23. Oktober 2026. Produktstand `a210423b82fcc6c76dacbb0be67ff7f77e4e98f3`, Produktbaum wie PR-Head `5621dc45cb642278a16e1163459e29073d57958b`. Nur Bericht und Textbelege werden veröffentlicht; lokale Pfade, OTP, Cookie-/Sitzungswerte und private Fixture-Datei bleiben außerhalb des Repositorys.

## Vorgeschriebene Läufe

Die drei Produktläufe erfolgten nacheinander, keine Browserdateien parallel. Repräsentative Aufrufe ohne lokale Pfade:

```text
# backend/; eigener leerer Testserver, kein Produktserver
./mvnw --batch-mode --no-transfer-progress verify \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:61555/task043_review_20261022 \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  -Dtaskmanager.test.server-version=18.6

# frontend/; erst nach abgeschlossenem Backendlauf
npm ci
npm run verify

# frontend/; erst danach, explizit sequentiell
node --test --test-concurrency=1 tests/browser/*.test.mjs
```

Unter Windows wurde der Maven-Wrapper über den vorhandenen Java-Laufzeitweg ausgeführt. Maven 3.9.16, Java 25.0.4.1+1, PostgreSQL 18.6, Node 24.22.0, npm 11.19.1, installierter Chrome / Playwright-Core 1.64.1. Native PostgreSQL-Verwendung über die in backend/README.md dokumentierten Override-Parameter; kein Nachweis des CI-Testcontainers oder des Demoimages.

Auszüge der selbst erzeugten Ergebniszusammenfassungen:

```text
Backend:
Tests run: 217, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 04:26 min
Finished at: 2026-10-22T22:03:05+02:00

Frontend verify:
tests 68; pass 68; fail 0; skipped 0
vite v8.3.4: 49 modules transformed; build successful

Browser, test-concurrency=1:
tests 75; suites 11; pass 75; fail 0; cancelled 0; skipped 0
duration_ms 67670.7971

Repository-Python-Tests:
Ran 38 tests in 28.126s
OK (skipped=1)
```

Der einzige Python-Skip ist `SessionTimeTests.test_local_time_zone_plays_no_role`, weil Windows kein `time.tzset` bereitstellt; 37 Tests ausgeführt. Vollständige lokale Prozesslogs wurden wegen lokaler Pfade und möglicher synthetischer Sitzungsdaten nicht veröffentlicht; dies sind ehrlich gekennzeichnete Ergebniszusammenfassungen, keine behaupteten CI-Artefakte.

## Eigene Zusatzproben wiederholen

Nur auf einer leeren, wegwerfbaren Testinstallation mit synthetischen Daten ausführen. Die Quellen schreiben absichtlich Testdaten. Passwörter in den Quellen sind ausdrücklich synthetische Testwerte und keine Produktvorgaben.

### Kern / Spring / PostgreSQL

1. Leere eigene Datenbank für die Kernprobe bereitstellen, `TASK_MANAGER_DB_URL`, `TASK_MANAGER_DB_USERNAME`, `TASK_MANAGER_DB_PASSWORD` setzen.
2. Im Backend mit dem Maven-Ziel `dependency:build-classpath` den Laufzeit-Classpath bereitstellen. Aus dem Repository-Stamm `java --class-path "<backend/target/classes und Abhängigkeiten>" docs/review-evidence/task-043-pr-146/CoreProbes.java` ausführen.
3. Die Quelle startet die unveränderten Spring-Produktbeans und Hikari-Konfiguration. **Nur für diesen isolierten Test** werden der Identitätsport durch eine synthetische gültige Identität und die Uhr durch eine steuerbare Uhr ersetzt. Dadurch können externe Kontexte und der exakte Ablauf beim Commit unabhängig geprüft werden. Das ist kein Portal- oder produktiver HTTP-Identitätsnachweis.
4. Ausgabezeilen mit Präfix `REVIEW` stehen in [core-results.jsonl](core-results.jsonl): Timeout 5s und Wiederherstellung von drei Werten, externe Verwaltung verweigert, wirkungslose Änderungen/Urheberwechsel, Ablauf und Deadlinebereinigung nach Transaktionsende.

### Echtes Backend / HTTP

1. Zweite leere Datenbank für die Anwendung bereitstellen. Die unveränderte gebaute Backend-JAR auf Loopback-Port 61556 starten. Einmal `bootstrap-admin` mit dem synthetischen Namen `reviewadmin` verwenden; die Einmalkennwort-Ausgabe nur in einer privaten lokalen Datei speichern. Keine solche Datei committen.
2. `PSQL` auf den PostgreSQL-Client und `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE` auf **diese eigene Datenbank** setzen. Pillow mit WebP-Unterstützung wird für die unabhängigen Bildproben benötigt.
3. `python docs/review-evidence/task-043-pr-146/http_probes.py <private Bootstrap-Ausgabe> <private Fixture-Datei>` ausführen. Bei bereits durchgeführtem initialem Passwortwechsel kann `REVIEW_RESUME=1` verwendet werden; ein vollständig frischer Lauf ist vorzuziehen.
4. Die Quelle erzeugt Konten, Gruppen, zwei Projekte und einen Testticketkontext. Reguläre Änderungen erfolgen über HTTP. **Ausdrückliche Datenbank-Fixtures:** Die projektbezogene Verwaltungsbefugnis des Delegierten wird für den Test direkt vorbereitet, da die Unternehmens-HTTP-Aktionsauswahl diese Befugnis nicht anbietet; außerdem setzt die Ablaufprobe das Ende einer eigenen synthetischen Sitzung und hält eine Ticketzeile in einer zweiten Verbindung gesperrt. SQL dient zusätzlich der Versions-/Auditkontrolle. Diese Fixtures sind keine behaupteten extern erreichbaren Schreibwege.
5. [http-results.json](http-results.json) enthält die beobachteten Ergebnisse. Zwei spätere, gesonderte HTTP-Kontrollen wurden ergänzt: der normale Projektaufruf mit demselben unberechtigten Konto (404) und ein doppelter Gruppenname (409). Beide Aufrufe sind inzwischen auch in der Probe enthalten. Die gespeicherte Anzahl 174 gehört zum ursprünglichen Paginglauf, nicht zu einem erwarteten Festwert eines beliebigen Wiederholungslaufs.
6. Für F1 genügen die in der JSON-Datei enthaltenen vollständigen Hexdateien: als `image/webp` mit aktuellem ETag auf den Logoendpunkt hochladen. Erwartet wäre 415; beobachtet wurde zweimal 200 mit Versionszuwachs.

### Echter Produktionsbuild / Browser

Nach der HTTP-Probe `node docs/review-evidence/task-043-pr-146/ui_probes.mjs <private Fixture-Datei>` ausführen. Vorher Frontend-Abhängigkeiten und `frontend/dist` aus `npm run verify` bereitstellen. Die Probe verwendet einen lokalen statischen Server auf Port 61557 mit unveränderter API-Durchleitung an 61556. Sie benutzt installierten Chrome und die wirklichen Konten/Cookies; kein Mock-Backend.

[ui-results.json](ui-results.json) dokumentiert Projekt-Rechteänderungen (zwei echte 204), Audit-Seiten 50/100/150, Farbeingabe, Accessibility-Snapshots mit/ohne Logo bei 320 Pixeln und die Browser-Dekodierung beider kaputter WebP-Dateien. Keine Screenshotdateien oder binären Uploads nötig. Der durchleitende Testserver belegt keine separaten Produkt-CSP-/Hosting-Eigenschaften.

### Älterer Protokolleintrag

Erst nach den vorigen Proben auf derselben wegwerfbaren Fixture-Datenbank:

```text
psql -X -At -v ON_ERROR_STOP=1 -f docs/review-evidence/task-043-pr-146/legacy_fixture.sql
# REVIEW_LEGACY_ONLY=1 in der Umgebung setzen
node docs/review-evidence/task-043-pr-146/ui_probes.mjs <private Fixture-Datei>
```

[legacy-fixture.json](legacy-fixture.json) hält den zuvor bekannten Vorgänger DENY und die bewusst nachgebildete fehlende Erfassung NULL fest. Der Eintrag wird nur zur sicheren Wiedererkennung auf den synthetischen Zeitpunkt 1. Januar 2020 gesetzt. [legacy-ui-results.json](legacy-ui-results.json) enthält die reale Darstellung „Nichts“. Dies ist ein gezielter Darstellungsversuch für ein durch V0016 zulässiges Altformat, **kein tatsächlich durchgeführtes Upgrade einer alten Installation** und kein Vorwurf manipulierbarer Audit-API.

Die endgültigen Hilfsquellen spiegeln erfolgreiche Proben wider. Anfangs korrigierte Java-Typfehler und Browser-Warte-/Fixtureprobleme betrafen ausschließlich diese Review-Hilfen, nicht das Produkt. Nach Abschluss wurden die eigenen Anwendung-/Datenbankprozesse gestoppt.

## Abschließende Repositoryprüfungen

- `python scripts/validate_repository.py`: **Repository contract: OK** (IDs, links, task metadata and completion evidence).
- `python scripts/task_board.py --check`: **Aufgabenübersicht: aktuell**.
- `python -m unittest discover -s tests -v`: **38 Tests, OK, 1 Windows-bedingter Skip**, siehe oben.
- `git diff --check` und abschließend `git diff --cached --check`: ohne Fehler.
- Alle Änderungen liegen unter dem neuen Bericht und `docs/review-evidence/task-043-pr-146/`. Kein Produktcode, keine Migration, kein Vertrag, kein TODO-/DONE-Eintrag geändert.
