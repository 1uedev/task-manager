# Unabhängige Nachprüfung TASK-031 / PR #136

**Ergebnis:** Die Nachbesserung ist **im geprüften Umfang technisch abnahmefähig**. Die ursprünglichen Befunde F1 und F2 (P2) sowie F3 und F4 (P3) aus [PR #135](https://github.com/1uedev/task-manager/pull/135) sind für den jetzt beschriebenen Messumfang behoben. Auch der Parameterüberlauf beim Neuaufbau ist behoben. Kein P1- oder P2-Befund. Ein verbleibender P3-Befund N1 betrifft die Kennzeichnung bestimmter verlorener Schreibantworten; dabei wird keine Änderung wiederholt. Keine fachliche Abnahme und keine erneute Gesamtprüfung der unveränderten Image-/Frontendfunktionen oder der Voraussetzung TASK-030.

## Prüfstand und Maßstab

- Getrennte Codex-Sitzung `codex-20261022-task031-followup-01`, 22. Oktober 2026, eigener Klon und Branch `review/task-031-pr-136`. Nur dieser Bericht und eigene Nachweise unter `docs/review-evidence/task-031-pr-136/`; Produktcode, vorhandene Tests, TODO.md und DONE.md unverändert. Die ausdrücklich beauftragte Nachprüfung übernimmt keine Implementierungsreservierung.
- [PR #136](https://github.com/1uedev/task-manager/pull/136), Branch `claude/project-thread-r4dri5`, Kopf `5ce767cd0503a5a81b5468a6911fc722d5b7edc6`, Basis `28becad967722994423db0bf5ab49ebdeb3a9c6f`. Beim Prüfstart bereits integriert als main `124d2d81425ce4012b257049fc5c932e84ad2b44`. Eigene Ausführungen auf diesem main-Stand; sein Dateibaum ist mit dem PR-Kopf identisch. Main vor der Veröffentlichung erneut abgeglichen.
- Ausgangspunkt: [ursprünglicher Prüfbericht am festgehaltenen Stand von PR #135](https://github.com/1uedev/task-manager/blob/ba11a9036fead4b567b7da4a9f85fc4005d042d0/docs/REVIEW_TASK-031_PR-134.md). Dieser Bericht liegt noch auf dem eigenen Reviewbranch; er wurde aus Git gelesen und nicht stillschweigend nach main übernommen.
- Gelesen: AGENTS.md, CLAUDE.md, README.md, TODO.md mit TASK-031, DONE.md, docs/AI_WORKFLOW.md, docs/TECHNICAL_BASELINE.md und relevante Anforderungen/Entscheidungen einschließlich DEC-040/DEC-071 samt Nachtrag, Betriebs-/Suchprofil und die Änderungen aus PR #136. Bezug: REQ-OPS-001/005/006, REQ-PERF-001/003, REQ-DEV-004. [OPERATIONS.md](OPERATIONS.md#lastprofil-und-vorgeschlagene-messziele) und [TAGS_SEARCH.md](TAGS_SEARCH.md#messbare-zielvorschläge) enthalten weiterhin vorgeschlagene Vergleichswerte, keine bestätigten Produktgarantien.
- TASK-031 steht BEREIT ZUR PRÜFUNG bei `claude-20261021-demo-01`; TASK-030 und die angenommene Backendgrundlage bleiben Voraussetzungen. Die Eigenprüfungsangaben des Autors sind von den folgenden eigenen Ausführungen und den lediglich gelesenen CI-Nachweisen getrennt.

## Verbleibender Befund nach Schwere

### N1 — P3: Abgeschnittene Schreibantworten und Zeitüberschreitungen werden nicht als unklarer Ausgang ausgewiesen

**Fundstellen:** `tools/demo/demo.py:124–135` (`Client.call`), `:838–843` (Messfehler), `:966–968` (CLI-Fehlerbehandlung); allgemeine Zusage in `docs/DEMO.md:115`. Tests `tests/test_demo_tool.py:65–98` erfassen den Abbruch ohne Antwort, nicht diese beiden Varianten.

**Reproduzierbarer Ablauf:** Der eigene lokale HTTP-Testserver liest einen POST vollständig und erhöht einen synthetischen Speicherzähler. Danach sendet er entweder `201` mit angekündigten 100 Bytes, aber nur einem Fragment des JSON-Inhalts, oder hält jede Antwort länger als das konfigurierte Zeitlimit zurück. Das unveränderte `Client.call` wirft dann `IncompleteRead` beziehungsweise `TimeoutError` statt `UnclearOutcome`. In beiden Fällen erreicht **genau ein** POST den Server und der Speicherzähler beträgt eins. Der unveränderte Messpfad ordnet diese Ausnahmen dem gewöhnlichen `error`-Zweig zu; außerhalb von `measure` fehlt die für `UnclearOutcome` vorgesehene klare CLI-Meldung. Die Fehlerquote enthält den Fall weiterhin, aber `unclear` kann trotz bereits gespeicherter Änderung null bleiben. Nachweis: [Skript](review-evidence/task-031-pr-136/followup_probes.py), [Ausgabe](review-evidence/task-031-pr-136/followup-probes.txt).

**Auswirkung:** Kein erneutes Senden und kein Wiederaufleben des ursprünglichen P2-Duplikationsfehlers. Die dokumentierte Diagnose ist jedoch unvollständig; ein Anwender kann nach einem gewöhnlichen Fehler irrtümlich von einer nicht gespeicherten Änderung ausgehen. Die veröffentlichte Messung wird dadurch nicht nachträglich als fehlerhaft bewiesen.

**Vorschlag:** Verbindungs-/Antwortfehler nach Beginn einer Änderung einschließlich unvollständigem Antwortkörper und Lesezeitüberschreitung konservativ als `UnclearOutcome` behandeln und die Verbindung schließen. Auch diese Fälle gegen einen lokalen Server testen: einmalige Änderung, kein erneuter Schreibversuch, klare Meldung und Zählung als unklar. Fehler vor dem Senden dürfen gesondert behandelt werden, sofern der Ausgang sicher feststeht. Kein pauschales Wiederholen von Änderungen einführen.

## Rückprüfung der ursprünglichen Befunde

### F1 — P2 behoben für die eingegrenzte komplexe Trefferklasse

**Fundstellen:** `tools/demo/demo.py:665–713`, `:760–773`, `:882–923`; `tests/test_demo_tool.py:148–177`; `docs/DEMO.md:112–151` und [Nachmessung JSON](measurements/task-031/measurement-20261022-073217.json) / [Markdown](measurements/task-031/measurement-20261022-073217.md).

`prepare_user` gewinnt die neuen Fälle aus tatsächlich sichtbaren Suchtreffern mit lesbarem Titel, Status, Typ und Tags. `complex_case` übernimmt das zugehörige Projekt, ein Titelwort, den Status und Typ sowie ausschließlich Tags dieses einen Tickets. Weitere Projekte/Status-/Typwerte erweitern die Auswahl; sie schließen das Ausgangsticket nicht aus. Damit sind insbesondere die früheren zwingend leeren Mischungen fremder Projektkataloge im positiven Generator beseitigt. Die frühere Zufallskombination bleibt zutreffend als eigene selektive Klasse erhalten. Eigene Konstruktionsprobe: **10.000 von 10.000** Kombinationen passen zu ihrem synthetischen Ausgangsticket. Der vorhandene Regressionstest mit 500 Kombinationen besteht ebenfalls.

Erfolgreiche Suchaktionen übernehmen `total` aus der API-Antwort. Die Auswertung trennt Anteil mit/ohne Treffer, Median und Maximum je Klasse; sie zählt alle erlaubten Treffer und nicht nur die Seite mit maximal 20 Einträgen. Eigener Abgleich der veröffentlichten Aggregate:

| Klasse | Stichproben | Mit / ohne Treffer | Treffer Median / Maximum | p50 / p95 / p99 ms |
|---|---:|---:|---:|---:|
| Komplexe Suche mit Treffern | 113 | 113 / 0 | 1 / 25 | 22,9 / 43,0 / 53,5 |
| Komplexe Suche selektiv | 37 | 3 / 34 | 0 / 10 | 23,5 / 55,3 / 61,1 |

JSON und erzeugte Markdown-Tabelle stimmen exakt überein; auch die entsprechenden DEMO-Zeilen stimmen. Bestands-/Profilangaben sind konsistent: 20 Projekte, 90.000 Tickets, **101 Konten einschließlich Administrator**, 15 Sitzungen, 600 Sekunden Aufwärmen, 1.800 Sekunden Messung, mittlere Denkzeit fünf Sekunden. Die Klassen ergeben ohne die zweite Zeitreihe „Ticketänderung gesamt“ genau **5.385 Aktionen**, ohne Abweisungen, Fehler oder unklare Ausgänge. Vergleichswerte und `met`-Angaben passen zum Werkzeug.

**Grenze:** Das ist eine Prüfung der Konstruktion und der abgelegten Aggregate, keine eigene Wiederholung des 30-Minuten-Laufs. Einzelmesswerte liegen nicht vor; die historischen Quantile und Trefferereignisse sind daraus nicht unabhängig nachberechenbar. 113 beziehungsweise 37 Stichproben tragen insbesondere keine allgemeine p99-Zusage. Die positive Klasse ist stark selektiv; große Treffermengen mit vielen gleichzeitig gesetzten Filtern bleiben ungemessen. Größere Trefferzahlen der Wortsuche oder einfacher Filter ersetzen diesen Nachweis nicht. DEMO.md benennt die Grenze und kennzeichnet die alten 15-/30-Sitzungs- und Stressläufe als vor der Methodenkorrektur entstanden. Auch das mögliche Veralten eines vorbereiteten Titelworts durch eine gleichzeitige Titeländerung ist offengelegt. Diese bekannten Grenzen sind kein neuer Befund; die Schlussfolgerung gilt für die dokumentierten Klassen, nicht für den gesamten V1-Suchumfang.

### F2 — P2 behoben: keine Wiederholung von Änderungen

**Fundstellen:** `tools/demo/demo.py:60–64`, `:117–135`; `tests/test_demo_tool.py:65–98`.

Nur `GET` und `HEAD` gelangen nach den abgefangenen Verbindungsabbrüchen in einen zweiten Versuch. Für POST/PATCH/PUT/DELETE wird die Verbindung geschlossen und `UnclearOutcome` geworfen. Auch andere Transportausnahmen fallen aus der Schleife heraus und wiederholen keine Änderung; ihre unvollständige Kennzeichnung ist N1. Vor Änderungen wird eine länger als fünf Sekunden ruhende Verbindung vorsorglich erneuert.

Die unveränderten lokalen Servertests bestehen: angenommener POST mit verlorener Antwort genau einmal; sämtliche vier Änderungsmethoden genau einmal; GET einmal wiederholt und nach zweitem Verlust beendet; ruhende Verbindung erneuert. Eigene zusätzliche Abbruch-/Teilantwort-/Zeitlimitproben bestätigen jeweils genau einen POST. Die Tests verwenden einen synthetischen Speicherzähler, keine echte Fachtransaktion; sie belegen das clientseitige Sendeverhalten. HEAD ist durch denselben Codepfad erfasst, wurde aber nicht separat dynamisch geprüft.

### F3 — P3 behoben: UTC für die feste Sitzungsfrist

**Fundstellen:** `tools/demo/demo.py:512–537`, `:567`; `tests/test_demo_tool.py:101–132`.

Die API-Zeit wird mit Zeitzone gelesen, auf UTC normalisiert und gegen eine UTC-Zeit gerechnet. Zeitangaben ohne Zone werden abgewiesen; Sekundenbruchteile bis Nanosekunden werden auf die Mikrosekundenpräzision von Python gebracht. Eigene Tests für API-Zeitformate sowie Frühjahrs-/Herbstwechsel bestehen und ergeben jeweils 24 Stunden. Der Test für vier lokale Zeitzonen wird auf Windows mangels `time.tzset` ausdrücklich übersprungen; im gelesenen Ubuntu-CI-Protokoll desselben PR-Kopfs besteht er. Image-CI zeigt ebenfalls 24,00 Stunden und eine nach Neustart weiterhin gültige Sitzung.

### F4 — P3 behoben: Speicherung und ganze Aktion getrennt

**Fundstellen:** `tools/demo/demo.py:639–643`, `:732–743`, `:857–868`; `docs/DEMO.md:113–139`.

PATCH liefert „Ticketänderung Speichern“ mit dem vorgeschlagenen Vergleichswert von 1.000 ms. Zusätzlich wird die gesamte Aktion aus vorherigem GET, lokaler Vorbereitung und PATCH als „Ticketänderung gesamt“ ohne eigenes Ziel ausgewiesen. Eigene kontrollierte Zeitprobe: GET 900 ms und PATCH 10 ms liefern 10 ms Speicherung sowie 910 ms Gesamtzeit. Die Gesamtzeitreihe wird nicht nochmals als Aktion oder Fehler gezählt. Die Nachmessung enthält jeweils 651 Werte, p95 **16,7 ms** für Speichern und **27,3 ms** gesamt; die Gesamtzeile hat weder Ziel noch Erfüllungsbehauptung.

## Nebenbefund: Neuaufbau oberhalb der Parametergrenze

**Fundstellen:** `backend/src/main/java/taskmanager/search/internal/SearchRepository.java:125–127`, `:153–157`; `SearchProjection.java:75–109`; `backend/src/test/java/taskmanager/search/SearchApiTest.java:551–595`.

`refreshCompany` materialisiert keine Liste sämtlicher Ticket-/Projektkennungen mehr als SQL-Parameter. Je Tabelle wird ein korreliertes UPDATE mit genau einem Unternehmensparameter ausgeführt. Die bestehende Tagtextbildung, Sortierung und Leertextbehandlung bleiben erhalten, der Unternehmensfilter begrenzt die aktualisierten Zeilen. Der Aufrufer hält weiterhin in einer Transaktion die exklusiven Projektionssperren mit `search_state` zuerst. Die Änderung führt keinen neuen Sperrweg ein.

**Eigenständig ausgeführt:** Der unveränderte `SearchApiTest` besteht vollständig auf **PostgreSQL 18.6 und Java 25**, **13 Tests, null Fehler, null Auslassungen**. Darunter besteht `aRebuildCopesWithMoreTicketsThanOneStatementTakesParameters` mit **70.000 Tickets** und abschließender Prüfung aller 70.000 Projektionszeilen; Testdauer 24,926 Sekunden. Er überschreitet die frühere 65.535-Parametergrenze tatsächlich. Der vorhandene Wiederaufbautest prüft zusätzlich reparierten Titel und Tag-Suchbarkeit; der Sperrtest für Tagumbenennung gegen Neuaufbau besteht ebenfalls.

**Grenze des Nachweises:** Der neue Mengentest hat keine Tagzuordnungen; er prüft die Mengengrenze, nicht allein die korrekte Tagaggregation. Diese wird durch Codevergleich und den ergänzenden vorhandenen Wiederaufbautest gestützt. Er ist kein eigener Neustart des vollständigen 90.000-Ticket-Bestands und keine Skalierungsmessung großer Tagfächer. Die vom Autor genannte rote Ausführung ohne Korrektur wurde nicht selbst wiederholt; Produktcode blieb unverändert.

## Tatsächlich ausgeführte Prüfungen und gelesene Nachweise

Eigene Ausführungen mit Ergebnissen und reproduzierbaren Aufrufen stehen im [Prüfprotokoll](review-evidence/task-031-pr-136/verification.md):

- Repository-Validator, generierte Aufgabenübersicht, `git diff --check`.
- Alle Python-Regressionstests: **35 Tests, null Fehler, eine plattformbedingte Auslassung**. Von den zehn neuen Werkzeugtests wurden neun lokal ausgeführt.
- Vollständiger `SearchApiTest`: **13 Tests, null Fehler/Auslassungen**, Paketbau erfolgreich, PostgreSQL 18.6.
- Eigene synthetische Gegenproben für Antwortverlust, Teilantwort, Zeitlimit, 10.000 passende Suchkombinationen, Zeitmessung, UTC und Aggregate; [Ausgabe](review-evidence/task-031-pr-136/followup-probes.txt).

Zusätzlich nur gelesen, **nicht als eigene Ausführung gewertet**, jeweils für den exakten PR-Kopf: [Repository-CI](https://github.com/1uedev/task-manager/actions/runs/39741505344) mit allen 35 Python-Tests ohne Auslassung; [Backend-CI](https://github.com/1uedev/task-manager/actions/runs/39741505255) mit 207 Tests ohne Auslassung und erfolgreichem Lifecycle-Check auf PostgreSQL 18.6; [Image-CI](https://github.com/1uedev/task-manager/actions/runs/39741505359) mit erfolgreichem Build, Demo-/Fristprüfung und Neustart. Die ebenfalls protokollierte Version 16.15 betrifft nur das psql-Clientwerkzeug; Server/Flyway und Lifecycle-Versionstest nennen 18.6.

Nicht selbst erneut ausgeführt: vollständige Backend-/Frontendtests, Containerbau/-neustart und die historische Lastmessung. Docker Desktop war in der vorherigen Prüfung auf dieser Maschine nicht startfähig; für diese eng begrenzte Nachprüfung wurden der unveränderte Containeraufbau und dessen CI-Nachweise gelesen. Kein neuer vollständiger Geheimnis-, CSP- oder Nicht-Root-Audit.

## Technische Übergabe

F1 bis F4 und der konkrete Neuaufbaufehler sind im geprüften Umfang geschlossen. N1 ist eine nicht blockierende Diagnose-/Dokumentationslücke der Schwere P3 und sollte mit zwei zusätzlichen Transporttests nachgebessert werden. Die technische Nachbesserung ist damit abnahmefähig; eine weitergehende Leistungszusage folgt daraus nicht. Die fachliche Abnahme von TASK-031 und die Bewertung ihrer Voraussetzung TASK-030 verbleiben bei Holger beziehungsweise der Koordination. TODO.md und DONE.md wurden nicht geändert.
