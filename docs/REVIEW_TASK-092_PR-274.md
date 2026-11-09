# Technische Nachprüfung TASK-092 / PR #274

**Ergebnis: Noch nicht zur Abnahme empfohlen. Ein Pflichtbefund R1 (P2) bleibt offen.** Die ursprünglichen Mengenfehler sind bei unverändertem Datenbestand behoben. F2 und die Oberflächenkorrektur F3 bestehen die Nachprüfung; H1 und die beauftragten Regressionen aus H2 sind umgesetzt. Bei einer gleichzeitig gespeicherten Buchung widersprechen sich jedoch Gesamt- und Tageswerte derselben Antwort. An der Exportgrenze kann derselbe Fehler eine gekürzte CSV mit HTTP 200 statt einer vollständigen Auswahl oder HTTP 400 erzeugen.

## Auftrag und Prüfstand

- Unabhängige prüfende Sitzung: `codex-20261109-task092-review-01`, 9. November 2026; getrennte Arbeitskopie und getrennt von Autor `claude-20261108-timefix-01` (DEC-040).
- Repository `1uedev/task-manager`, [PR #274](https://github.com/1uedev/task-manager/pull/274), Branch `claude/project-thread-thp5tg`.
- Geprüfter Commit: `e9092ab6dd2caf047b05b1435da29a72198e06ae`; Vergleichsbasis/Elterncommit: `13ee71cea077cacc7a820012757939e9f5bd4936`. Der PR-Endstand wurde zum Abschluss erneut abgefragt und war unverändert.
- Aktueller `main` wurde zusätzlich gelesen: `2101fd70045a230634119f69f874fbdbba92d904`. Bewertet wurde der PR-Endstand, kein selbst erzeugter Integrationsmerge.
- Maßstab: `AGENTS.md`, TASK-092 und TASK-069 einschließlich des Nachtrags vom 8. November 2026 in `TODO.md`, DEC-040, DEC-099, DEC-122, REQ-TIME-001/002 und die Abgrenzung REQ-TIME-003, `contracts/time.openapi.yaml` sowie [der ursprüngliche Prüfbericht](REVIEW_TASK-069_PR-256.md).
- Umfang: die 17 geänderten Dateien des PR, insbesondere Aggregation und Filter, Korrektur und Maskierung, HTTP-Antworten und CSV, „Meine Zeiten“, Vertrag, Dokumentation und neue API-/Browsertests. Die vollständigen bestehenden Testbestände wurden zusätzlich ausgeführt; daraus folgt kein vollständiges Codeprüfurteil über sämtliche anderen Module.
- Die ausdrückliche Reviewzuweisung ist kein Implementierungsauftrag und keine fachliche Abnahme. Im geprüften Checkout wurde ausschließlich dieser Bericht angelegt. Produktcode, `TODO.md` und `DONE.md` blieben unverändert. Zusätzliche Proben und Fehlermutationen liefen in separaten, entbehrlichen Testkopien.

## Offener Pflichtbefund

### R1 · P2 · Getrennte Datenstände verfälschen Tageswerte und umgehen die Exportablehnung

**Ort:** `backend/src/main/java/taskmanager/time/internal/TimeService.java:242` (Anzahl, Gesamt-, Personen- und Ticketsummen), `:256` (Tageswerte), `:261` (Kürzungsentscheidung), `:265` (Zeilenabfrage), `:274` (Abschneiden der Zeilen). SQL-Abfragen: `TimeRepository.java:124`, `:136` und `:111`. Der Export prüft ausschließlich das so entstandene `truncated`: `backend/src/main/java/taskmanager/time/web/TimeController.java:190`. Sichtbare Folge: `frontend/src/pages/TimePage.tsx:391` und `:399`.

**Ursache:** `listing()` berechnet diese Bestandteile nacheinander mit getrennten SQL-Anweisungen. Die Transaktion verwendet die voreingestellte Isolation READ COMMITTED. Jede Anweisung kann deshalb eine inzwischen bestätigte Buchung zusätzlich sehen. `truncated` wird nur aus dem zuvor ermittelten `count` berechnet; die spätere Zeilenmenge wird ohne erneute Grenzentscheidung auf `limit` abgeschnitten. Auch die Projekte werden nacheinander abgefragt.

**Reproduktion A – widersprüchliche Summen:**

1. Für eine berechtigte Person 501 wirksame Buchungen zu je einer Minute an einem Leistungsdatum anlegen.
2. `GET /api/v1/time/entries?project=…` starten. Nach Rückkehr von `sums()`, unmittelbar vor Ausführung von `days()`, den Anfragefaden anhalten.
3. In einer anderen Datenbanktransaktion eine weitere passende Buchung zu einer Minute speichern und bestätigen; danach die Anfrage fortsetzen.
4. Tatsächliche Antwort: `count=501`, `totalMinutes=501`, Personen- und Ticketsumme jeweils 501, aber `perDate=[{date: …, minutes: 502}]`.

Die unabhängige Gleichheitsprüfung `totalMinutes == sum(perDate.minutes)` scheitert mit **erwartet 501, tatsächlich 502**. „Meine Zeiten“ übernimmt diese Werte nun korrekt vom Server und zeigt damit eine Wochensumme von 501 Minuten neben einer Tagessumme von 502 Minuten. Der Fehler liegt im gemeinsamen Serverergebnis, nicht in einer erneuten Summierung durch die Oberfläche.

**Reproduktion B – Export mit 50.001 Treffern wird still gekürzt:**

1. In einem Projekt 50.000 wirksame Buchungen zu einer Minute am 1. September 2026 anlegen.
2. `GET /api/v1/time/export?project=…` starten und unmittelbar vor der Zeilenabfrage `entries()` anhalten; Anzahl und Aggregate sind bereits gelesen.
3. Eine weitere passende Buchung am 2. September in einer anderen Transaktion bestätigen; Anfrage fortsetzen.
4. Die echte Zeilenabfrage sieht jetzt 50.001 Treffer. Tatsächliche Antwort: **HTTP 200**, 50.000 CSV-Datenzeilen, darunter die gerade hinzugefügte Buchung vom 2. September. Ein anderer Treffer wird abgeschnitten. Die Quelle enthält nachweislich 50.001 wirksame Buchungen.

Damit ist die CSV weder die vollständige alte Auswahl noch die vollständige neue Auswahl. Ein kohärenter älterer Datenstand dürfte die ursprünglichen 50.000 Buchungen vollständig liefern; ein Datenstand, der die neue Buchung berücksichtigt, muss wegen 50.001 Treffern mit **400 `time.export_too_large`** abgelehnt werden. Die bestehende Exportablehnung greift hier nicht, weil `truncated` aus der früheren Anzahl 50.000 stammt.

**Nachweis:** Zwei zusätzliche HTTP-/PostgreSQL-Tests `Time092ConsistencyProbe` hielten jeweils nur die betreffende Repositorymethode mit einem Mockito-Spy und zwei Latches an. Danach wurde die unveränderte echte SQL-Methode fortgesetzt. Weder Rückgabewerte noch Summen wurden simuliert. Die zusätzliche Buchung wurde während der angehaltenen Anfrage in einer anderen Transaktion bestätigt. Beide Prüfungen scheiterten deterministisch an den fachlichen Erwartungen, ohne Ausführungsfehler; der Wiederholungslauf bestätigte beide Fälle. Ein dritter Lauf erzeugte die Zwischenbuchungen ausdrücklich über reguläre `POST /time/projects/{projectId}/entries` mit allen echten Rechteprüfungen (jeweils 201) und bestätigte dieselben beiden Fehler. Direkte SQL-Einfügungen waren in diesem Lauf nur für den Ausgangsbestand nötig.

**Bewertung:** Die PR-Beschreibung nennt READ COMMITTED und mögliche Unterschiede zwischen Summen und Zeilen bereits als Risiko. Das beschreibt die Ursache zutreffend, behebt aber weder die widersprüchlichen Aggregate noch die still gekürzte erfolgreiche CSV. Diese weitere Abweichung gehört nicht zu den drei von Holger in DEC-122 bestätigten Abweichungen. R1 betrifft die genaue Summenbildung und die Exportgrenze aus TASK-092/F1; der erste Fall wirkt auch auf F3.

**Erforderliche Korrektur:** Alle Aggregate und ausgegebenen Zeilen einer Antwort müssen einen konsistenten Datenstand über den gesamten berechtigten Projektumfang verwenden. Dafür einen gemeinsamen Datenbanksnapshot oder eine entsprechend gemeinsame Abfrage herstellen; dabei die bestehenden Rechte-, Sperr- und Wiederholungsregeln erhalten. Für den Export außerdem eine tatsächlich gelesene Überschreitung vor jeder Kürzung ablehnen, statt ausschließlich auf die vorab gelesene Anzahl zu vertrauen. Beide angehaltenen Abläufe dauerhaft als Regression aufnehmen. Keine Korrektur in diesem Review ausgeführt.

## Nachprüfung der ursprünglichen Befunde

### F1 – Mengengrenzen bei unverändertem Datenbestand behoben; R1 bleibt

**Orte:** `TimeRepository.java:124–142`, `TimeService.java:198–282`, `TimeController.java:183–201`; neuer Test `backend/src/test/java/taskmanager/time/TimeApiTest.java:489`.

`sums()` aggregiert ohne Mengenlimit nach Person und Ticket; `days()` aggregiert ohne Mengenlimit nach Leistungsdatum. Dieselbe Filterfunktion berücksichtigt Firma, Projekt, Person, Ticket, beide Datumsgrenzen und Stornos. Die Serviceschicht führt die erlaubten Projekte zusammen. Die begrenzte Einzelauflistung entsteht anschließend getrennt. Stornos zählen nur bei gewünschter Auflistung in `count`, in keiner Minutensumme.

Ausgeführt und erfolgreich:

| Bestand/Fall | Beobachtetes und geprüftes Ergebnis |
|---|---|
| 500 wirksame Buchungen | 500 Zeilen, `count=500`, 500 Minuten, `truncated=false`. |
| 501 wirksame Buchungen | 500 Zeilen, `count=501`, 501 Minuten auch in Personen-, Ticket- und Tagesgruppe, `truncated=true`. |
| 50.000 wirksame Buchungen | 50.000 Minuten; Export 200 mit genau 50.000 Datenzeilen. |
| 50.001 wirksame Buchungen | 500 Listenzeilen, `count=50001`, 50.001 Minuten einschließlich der Personensumme; Export 400 `time.export_too_large`. |
| Zweites Projekt, zehn wirksame und 600 neuere stornierte Buchungen | Projektübergreifend 50.011 wirksame Buchungen/Minuten; im zweiten Projekt mit `cancelled=true` 610 Treffer, zehn Minuten und 500 stornierte Einzelzeilen. Ohne Stornofilter zehn Treffer; Export zehn Datenzeilen. |

Zusätzlich verwendete die unabhängige Probe zwei Projekte, zwei Personen, zwei tatsächliche Tickets, unzugeordnete Buchungen, drei Leistungsdaten und unterschiedliche Dauern:

| Gruppe | Synthetischer Bestand | Erwartete und bestätigte Minuten |
|---|---|---:|
| Projekt A, Person 1, Ticket 1, 1. September | 30.000 × 2 Minuten | 60.000 |
| Projekt A, Person 2, Ticket 2, 2. September | 20.001 × 3 Minuten | 60.003 |
| Projekt B, Person 1, ohne Ticket, 3. September | 7 × 5 Minuten | 35 |
| Projekt B, Person 2, 1. Oktober | 600 Stornos × 1.440 Minuten | 0 |

Bestätigt: `count=50608` mit aufgelisteten Stornos, sonst 50.008; `totalMinutes=120038`; Personenwerte 60.035 und 60.003; Ticketwerte 60.000, 60.003 und 35; Tageswerte ebenfalls 60.000, 60.003 und 35, kein Stornotag. Personen-, Ticket- und Datumsfilter bestanden. Nach Entzug von `ticket.read` blieben zwei Projektgruppen ohne sichtbare Ticketreferenz. Der projektübergreifende Export wurde mit 400 abgelehnt. Die SQL-Grenzkorrektur besteht damit auch bei ungleich verteilten Werten; die noch offene Einschränkung ist R1.

### F2 – Verborgene Zuordnung bleibt; keine Ticketkennung offengelegt

**Orte:** `TimeService.java:329–353`, `:498–508`; öffentliche Antworttypen `TimeController.java:65–89`; Formular `frontend/src/pages/TimePage.tsx:89–142`, `:212–218`; neuer API-Test `TimeApiTest.java:546`, Browsertest `frontend/tests/browser/time.test.mjs:208`.

Die Korrektur behält bei fehlender oder explizit leerer `ticketNumber` den vorhandenen verborgenen Verweis. Das gilt sowohl für die eigene Korrektur mit `time.book` als auch für die fremde Korrektur mit `time.manage` und Grund. Die API-Probe entzog und erteilte `ticket.read` erneut: Nach 30 → 40 → 45 Minuten stand dasselbe Ticket an der aktuellen Buchung und an jeder bisherigen Revision. Erst die Korrektur mit `removeHiddenTicket: true` entfernte die aktuelle Zuordnung; ältere Revisionen behielten sie.

Die zusätzliche Probe prüfte getrennt `ticketNumber:null` und ein vollständig weggelassenes Ticketfeld. Der gespeicherte Ticket-UUID blieb gleich. Liste, Detail, Korrekturantwort, Revisionen und CSV enthielten weder den synthetischen geheimen Titel noch den Ticket-UUID. `ticket` war an Buchung und Revisionen `null`; `ticketHidden` war nur ein Wahrheitswert. Die öffentlichen Antworttypen reichen die interne `ticketId` nicht durch; auch die CSV verwendet ausschließlich die bereits maskierte Ticketreferenz. Filter nach verborgenem und nach nicht vorhandenem Ticket ergaben gleichermaßen 404. Es wurde kein Offenlegen von Nummer, Titel oder Ticketkennung gefunden. Das ausdrücklich vereinbarte Existenzsignal durch `ticketHidden` ist dabei beabsichtigt.

Der deutsche Browsertest bestätigte Hinweis, fehlenden Ticketlink, Erhalt bei einer Dauerkorrektur und die bewusste Entfernung über das neue Kontrollfeld. Eine ausdrücklich eingegebene, lesbare andere Ticketnummer kann die Zuordnung weiterhin ersetzen; die Erhaltungsregel betrifft die Korrektur **ohne** Ticketnummer.

### F3 – Eigene Serversummen und Kürzungshinweis umgesetzt

**Orte:** `TimeService.java:205–234`, `TimeController.java:146–152`, `frontend/src/pages/TimePage.tsx:373–399`; API-Test `TimeApiTest.java:597`, Browsertest `frontend/tests/browser/time.test.mjs:239`.

„Meine Zeiten“ sendet `mine=true`, übernimmt die Woche aus `totalMinutes` und die Tage aus `perDate`. Ein Konto mit `time.read` wird serverseitig auf die eigene Person beschränkt; `mine=true` mit einer anderen `person` ergibt 400 mit `person.with_mine` in `violations`.

Die API-Prüfung bestätigte 501 eigene Wochenminuten, verteilt auf zwei Tage mit 300 und 201 Minuten. 40 Minuten einer anderen Person, sieben eigene Stornos und fünf eigene Buchungen außerhalb der Woche gingen nicht ein. Die Liste war auf 500 eigene Buchungen gekürzt. Im deutschen Browser standen 501 Minuten beziehungsweise **8 Std. 21 Min.**, der vollständige Tageswert und der Hinweis: „Es werden 500 von 501 Einträgen gezeigt. Die Summen umfassen alle Einträge der Woche.“ Die Oberfläche berechnet diese Summen nicht mehr aus der Liste. Bei gleichzeitigen Änderungen können die gelieferten Serversummen allerdings noch auseinanderlaufen: R1.

## H1 und H2

### H1 – Die drei bestätigten Abweichungen sind wirksam konsistent dokumentiert

| Bestätigte Regel | Abgleich |
|---|---|
| Veraltete Version 412 `time.version_conflict`; stornierte Buchung mit aktueller Version 409 `time.entry_cancelled` | Nachtrag `TODO.md:131`, DEC-122 `DECISIONS.md:212`, Vertrag `contracts/time.openapi.yaml:19–27` und die Korrektur-/Stornorouten, `backend/README.md:514`. Bestehende API-Konflikttests bestanden. |
| „Heute“ bis zur Unternehmenszeitzone als UTC-Datum plus ein Tag | Derselbe Nachtrag, DEC-122, Vertrag `:24–27`, `backend/README.md:512` und `:520`; Implementierung `TimeService.java:395`. |
| Verborgenes Ticket in „ohne sichtbares Ticket“ | Derselbe Nachtrag, DEC-122, Vertrag `:27` und `:110`, `backend/README.md:516`; deutsche und englische Beschriftung `frontend/src/i18n/de.ts:1295` und `en.ts:1295`, Ausgabe `TimePage.tsx:283`. |

Die ursprünglichen Regeln 3 und 11 sowie DEC-099 bewahren die historische Formulierung; der ausdrücklich datierte Nachtrag und DEC-122 ändern sie eindeutig. Diese historischen Angaben wurden nicht fälschlich als weitere offene fachliche Entscheidung bewertet. Eine Unternehmenszeitzone ist weiterhin nicht implementiert und gehört nicht zu TASK-092.

### H2 – Regressionen reagieren auf die behobenen Fehler

Die neuen Tests wurden am PR-Endstand erfolgreich ausgeführt. Zusätzlich wurden in einer isolierten Kopie gezielt die früheren Fehlerbedingungen wiederhergestellt, während die neuen Antwortfelder und Tests erhalten blieben. Das vermeidet einen bloßen Kompilier- oder Schemafehler als vermeintlichen Verhaltensnachweis.

| Kontrolliert wiederhergestellter Fehler | Ergebnis des unveränderten neuen Tests |
|---|---|
| Datenbankaggregation wieder auf die ersten 50.000 passenden Zeilen begrenzt | `countAndSumsCoverEveryEntryBeyondTheListAndTheExportLimit`: Fehler **erwartet 50001, erhalten 50000**. Diese Mutation bildet die alte Begrenzung nach; sie ist kein vollständiger Rückbau des alten Service. |
| Erhaltungszweig für das verborgene Ticket deaktiviert | `aCorrectionKeepsAHiddenTicketUnlessItsRemovalIsAsked`: Fehler **`ticketHidden` erwartet true, erhalten false** nach der Korrektur ohne Nummer. |
| Personenbeschränkung von `mine` entfernt | `mineGivesTheOwnEntriesAndDaysEvenToAReader`: Fehler **erwartet 501, erhalten 541**. |
| Bestehender Portal-/Projektzugriffsschutz unverändert | `otherProjectsAndPortalAccountsAddNothing` bleibt erfolgreich; dieser Negativtest soll die bereits vorhandene Isolation erhalten. |

Die beiden neuen deutschen Browsertests wurden außerdem gegen das tatsächliche Frontend vor PR #274 (`13ee71c`) ausgeführt, mit den neuen Tests und der neuen Backendattrappe: Beide scheiterten an den erwarteten Oberflächenbedingungen, dem Hinweis auf das verborgene Ticket beziehungsweise dem Wochenwert 501. Ein gesonderter Rückbau allein der Wochen-/Tagesberechnung auf die 500 gelieferten Einzelzeilen ließ den neuen Wochentest ebenfalls scheitern. Am unveränderten PR-Endstand bestehen beide Tests.

Die ursprüngliche Kritik an fehlenden Mengen-, Erhaltungs- und eigenen Wochenregressionen ist damit beantwortet. Die neuen Tests decken die zeitliche Lücke zwischen den Abfragen noch nicht ab; dafür dienen die beiden zusätzlichen Reproduktionen aus R1. Es wurde kein neuer vollständiger Nachweis aller Rechteentzugs-/Commitreihenfolgen behauptet.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| `python scripts/validate_repository.py` | Erfolgreich, auch mit dem fertigen Bericht. |
| `python scripts/task_board.py --check` | Erfolgreich; Aufgabenübersicht unverändert aktuell. |
| `python -m unittest discover -s tests -v` | 38 Tests, erfolgreich; ein auf Windows vorgesehener Zeitzonenwechseltest übersprungen. |
| `git diff --check` | Erfolgreich; zusätzlich wurde der neue Bericht auf Leerraumfehler geprüft. |
| Backend `mvn -o -Dtest=TimeApiTest,TimeContractTest test` mit expliziter lokaler Datenbank | 12 Tests, keine Fehler oder Fehlschläge. |
| Vollständiger bestehender Backend-Testbestand `mvn -o test` mit expliziter lokaler Datenbank | 549 Tests erfolgreich, keine Fehler, Fehlschläge oder übersprungenen Tests. |
| Unabhängige zusätzliche API-Proben `Time092ReviewProbe` | Zwei Tests erfolgreich: gemischte Mengen-/Filter-/Gruppenprobe und Maskierung/Erhalt/Entfernung. |
| Zusätzliche Gleichzeitigkeitstests `Time092ConsistencyProbe` | Je zwei fachliche Fehlschläge, keine Ausführungsfehler; bestätigt im Wiederholungslauf und zusätzlich mit regulären API-Zwischenbuchungen. Grundlage von R1. |
| Vier neue API-Tests mit den beschriebenen Fehlermutationen | Drei erwartete fachliche Fehlschläge, ein erfolgreicher Portal-/Projektnegativtest, keine Ausführungsfehler. |
| Frontend `npm ci --offline`, danach `npm run verify` | Installation, Generierung, Typprüfung, 84 Tests und Build erfolgreich. Allgemeiner Hinweis zur Bündelgröße beim Build. |
| Zeit-Browsertests `node --test tests/browser/time.test.mjs` | Sieben Tests erfolgreich. |
| Vollständige Browsertests `node --test --test-concurrency=1 tests/browser/*.test.mjs` | 212 Tests erfolgreich, keine Fehlschläge oder übersprungenen Tests. |
| Neue Browsertests auf dem Frontend vor der Korrektur | Zwei erwartete Fehlschläge; gesonderte Zeilensummenmutation ebenfalls erkannt. |

**CI separat abgefragt:** Für den geprüften Commit waren [Backend](https://github.com/1uedev/task-manager/actions/runs/42579590048), [Frontend](https://github.com/1uedev/task-manager/actions/runs/42579590087), [Repositoryprüfungen](https://github.com/1uedev/task-manager/actions/runs/42579590122), [technische Qualifikation](https://github.com/1uedev/task-manager/actions/runs/42579590079) und [Image](https://github.com/1uedev/task-manager/actions/runs/42579590061) erfolgreich. Beim Backend wurden auch die erfolgreichen Schritte für echte PostgreSQL-Integration, keine übersprungenen Tests und Start/Stopp/Neustart des Pakets abgefragt. Diese CI-Ergebnisse ersetzen die neuen Reproduktionen nicht.

**Lokale Umgebung und Grenzen:** Windows, Java 25.0.4.1, Maven 3.9.16, Node 24.22.0, Chrome und eine eigens angelegte PostgreSQL-16.15-Testinstanz mit ausschließlich synthetischen Daten. Die lokalen Datenbankläufe sind kein Nachweis für den gepinnten PostgreSQL-18.6-Container; dessen Einsatz ist im abgefragten erfolgreichen CI-Workflow konfiguriert. Initiale Versuche mit unpassenden lokalen Cache-/Startparametern beziehungsweise eingeschränktem Temporär-/Netzwerkzugriff wurden nach Korrektur der Prüfumgebung erfolgreich wiederholt. Kein eigener Docker-, Paket-Lifecycle-, Last- oder Wiederherstellungslauf. Keine API-Antwortsimulation in den Mengen- und Gleichzeitigkeitstests; Browsertests verwenden die vorhandene Backendattrappe. Die Testkopien mit Fehlermutationen sind nicht der geprüfte Produktstand.

## Nächster Schritt

R1 beheben und beide kontrollierten Zwischenbuchungen als Regression nachprüfen. Danach technische Nachprüfung und Holgers fachliche Abnahme von TASK-069/TASK-092. Bis dahin keine Abnahmeempfehlung; Aufgabenstatus und Produktcode wurden durch dieses Review nicht geändert.
