# Prüfung TASK-070 / PR #223 – SLA-Eskalationen

31. Oktober 2026 · Codex, Sitzung `codex-20261031-task070-review-01` · unabhängige Prüfung nach DEC-040.

**Ergebnis: Noch nicht zur Abnahme empfohlen. Zwei P2-Befunde und ein P3-Befund.** Nach einer Pause kann der gespeicherte Schwellenzeitpunkt falsch sein (F1); die Liste kann trotz vorhandener lesbarer Eskalationen leer bleiben (F2). Eine Stufe mit `clock: null` führt zu HTTP 500 (F3). Die gezielt ausgeführten zehn Backend- und sieben Browsertests bestehen, decken diese Fälle aber nicht ab.

## Prüfstand und getrennte Durchgänge

Geprüft wurde der feste Head von [PR #223](https://github.com/1uedev/task-manager/pull/223), Branch `claude/project-thread-2gq3w2`, Commit **`7b152c0feea895740b84fb687b910e3625645778`**. Der PR war bei Prüfstart bereits gemergt; Merge-Commit `f68438fbd250b5b462d558b3f9468c82a78174a7`. Maßstab sind TASK-070 mit Regeln 1–4 und den vorgeschlagenen Abnahmekriterien, DEC-100 sowie der Abschnitt „Eskalationen (TASK-070)“ im Backend-README.

**Durchgang 1** behandelte ausschließlich die benannten Backenddateien/-abschnitte, GroupDirectory, V0032, EscalationsTest und den Eskalationsabschnitt von SlaApiTest. Die Ergebnisse wurden festgehalten, bevor **Durchgang 2** mit Vertrag, den benannten Oberflächenstellen und Browserdateien begann. In Teil 2 wurden keine weiteren Backendpfade untersucht. Beide Durchgänge verwenden denselben Commit.

Die Prüfkopie enthält nur diesen neuen Bericht. Eigene Backend-Beobachtungstests wurden in einer getrennten Experimentkopie ergänzt; das Browserskript liegt außerhalb der Prüfkopie. Kein Produktcode und kein Aufgabenstatus wurden geändert.

## Teil 1 – Überwachung, Sperren und Rechte

### F1 · P2 · Eine vor der Pause überschrittene Schwelle erhält nach dem Fortsetzen dessen Zeitpunkt

**Datei/Zeile:** `backend/src/main/java/taskmanager/sla/internal/Escalations.java:42`.

Wenn die Schwelle bereits im gespeicherten Verbrauch enthalten ist, übernimmt `due` den aktuellen `checkpoint` als `thresholdAt`. Dieser bezeichnet nach einer Pause das Fortsetzen, nicht den früheren tatsächlichen Schwellenübertritt. Der Unterschied wird anschließend dauerhaft gespeichert und als fachlicher Zeitpunkt angezeigt.

**Nachvollziehbarer Fall / Zusatztest E1:**

1. Geschäftszeit Montag 09:00–17:00, Beginn 19. Oktober 2026 um 09:00 Europe/Berlin; Budget 120 Minuten.
2. 80 % sind um **10:36**, 100 % um **11:00** erreicht. Die Überwachung hat noch nichts gespeichert.
3. Pause um 11:00, Fortsetzen um 13:00, Überwachung um 13:01.
4. Ergebnis: 80 % werden übersprungen, 100 % erreicht – diese Auswahl ist richtig. **Beide `thresholdAt`-Werte lauten jedoch 13:00.**

Der Beobachtungstest `reviewThresholdAlreadyPassedBeforePauseGetsResumeTime` verwendet die echten SlaClock-/Escalations-Operationen und bestätigt diese Werte. Er erwartet den Fehler ausdrücklich; er ist kein bestandener Abnahmetest. Der vorhandene Pausentest pausiert dagegen schon vor der Schwelle und trifft den Fehler nicht.

**Vorschlag:** Bereits überschrittene Schwellen mit ihrem tatsächlichen Zeitpunkt erhalten beziehungsweise aus einer ausreichenden Zeit-/Pausenhistorie rekonstruieren. Den aktuellen Checkpoint nicht pauschal als früheren Übertritt ausgeben. Dabei den anderen Fall ausdrücklich unterscheiden: Erst ein neues, kleineres Prioritätsbudget macht eine Stufe fällig; dann ist der Zeitpunkt der Budgetänderung relevant. Beide Varianten als Regression festhalten.

### F2 · P2 · Die Begrenzung vor der Rechteprüfung kann lesbare Eskalationen vollständig ausblenden

**Dateien/Zeilen:** `backend/src/main/java/taskmanager/sla/internal/TicketSla.java:278–299`; `SlaRepository.java:289–294`.

Die Datenbank liefert zunächst höchstens 200 Stufen der eigenen Gruppen. Erst danach prüft der Dienst `ticket.read` und die Leseregeln von `customer`/`priority`. Nicht lesbare Kandidaten verbrauchen damit die gesamte Auswahlgrenze.

**Nachvollziehbarer Fall:** Eine Person ist in der Empfängergruppe für zwei Projekte. Die neuesten 200 Stufen gehören zu einem Projekt, dessen Tickets sie nicht lesen darf. Eine ältere Stufe gehört zu einem lesbaren Ticket ihres anderen Projekts. Die Datenbank wählt nur die 200 nicht lesbaren Kandidaten; der Dienst entfernt alle. Die Seite meldet eine leere Liste, obwohl eine für diese Person sichtbare Eskalation existiert. Aktualisieren hilft nicht; eine Fortsetzung bietet der Endpunkt nicht.

Dies ist aus Auswahl und anschließender Filterung abgeleitet, **nicht mit 201 realen Backenddatensätzen nachgestellt**. Es ist keine Offenlegung fremder Inhalte, sondern eine unvollständige Arbeitsliste.

**Vorschlag:** Kandidaten seitenweise lesen und Rechte prüfen, bis höchstens 200 tatsächlich sichtbare Einträge oder das Listenende erreicht sind. Die Grenze muss sich auf das sichtbare Ergebnis beziehen. Einen Test mit mehr als 200 neueren nicht lesbaren und einer älteren lesbaren Stufe ergänzen.

### F3 · P3 · Eine leere Stufenuhr verursacht einen Serverfehler

**Datei/Zeilen:** `backend/src/main/java/taskmanager/sla/internal/Rule.java:53–55`; Aufruf in `SlaAdministration.java:243–249`.

`List.of(...).contains(stage.clock())` wirft bei `null` eine NullPointerException. Die Prüfung erreicht deshalb die vorgesehene Verletzung `escalations.invalid` nicht.

**Nachstellung E2/E3:** Als berechtigte Administration eine Regel mit `escalations: [{clock: null, percent: 80, groupId: <gültige Gruppe>}]` anlegen. Der direkte Prüfungstest bestätigt die Exception; die echte HTTP-API antwortet **500** statt einer verständlichen 400-Ablehnung. Beide Beobachtungen wurden gegen unveränderten Produktcode ausgeführt.

**Vorschlag:** `clock == null` vor der Aufzählungsprüfung abweisen; fehlende und ausdrücklich leere Uhr als API-Regression ergänzen.

### Bewertung von Überwachung und Sichtbarkeit

- **Keine doppelte Stufe im geprüften Ablauf:** `watchTicket` sperrt die bekannte `sla_ticket`-Zeile pro Ticket/Transaktion und liest die gespeicherten Positionen danach. V0032 erzwingt zusätzlich `unique(cycle_id, kind, stage)`; der Insert benutzt `on conflict do nothing`. Der vorhandene Test mit vier gleichzeitigen Überwachungen besteht.
- **Keine sichtbare umgekehrte Ticketsperre:** Der Watch nimmt `sla_ticket`, sperrt danach aber keine Ticketzeile und ruft keinen Ticketänderungsport auf. Die neue Eskalationstabelle hat nur den Fremdschlüssel zum Zyklus, keinen neuen zur Ticketzeile. Aus diesem Pfad ergibt sich kein Zyklus „sla_ticket → Ticket“ gegen „Ticket → sla_ticket“. Die Aufrufer für Status-, Antwort- und Prioritätsänderungen liegen außerhalb der vorgegebenen Ausschnitte; deren vollständige Sperrfolge wird hier nicht neu freigegeben.
- **H-11 im Standardfall richtig:** Nur die höchsten noch fälligen Prozentwerte werden erreicht, auch mehrere Empfängergruppen desselben Prozentwerts. Niedrigere fällige Positionen werden mit `skipped` gespeichert; sie kommen später nicht wieder. Regelversion und Positionskennung bleiben am Zyklus gebunden.
- **Leserechte werden beim Abruf geprüft:** Unternehmens- und Gruppenfilter stehen in der Auswahl, `ticket.read` sowie beide Feldrechte im Dienst. Die CycleView-Zusammenstellung entfernt übersprungene und fremde Gruppenstufen. Im gelesenen Code wurde kein Hinweis auf die Offenlegung solcher Stufen am Ticket gefunden. F2 betrifft die Vollständigkeit, nicht eine Lockerung dieser Rechte.

## Teil 2 – Vertrag, Oberfläche und Testattrappe

Die gelesenen Formen von Stage, ReachedStage, EscalationItem und Options passen grundsätzlich zu den in Teil 1 ermittelten Daten. `cycleOutcome: null` bei offenem Zyklus ist im Vertrag erlaubt; Gruppenoptionen bestehen aus `id/name`. Erreichte Stufen enthalten keine `skipped`-Markierung für die Oberfläche. Der neue Navigationseintrag ist eine normale Hauptseite ohne zusätzliches `sla.manage`, passend zum Endpunkt für jede vollständige Anmeldung.

### H1 – Die Attrappe verdeckt die Listenbegrenzung und den ungültigen Uhrwert

**Datei/Zeilen:** `frontend/tests/browser/backend.mjs:307–322`, `:852–863`; Vertrag `contracts/sla.openapi.yaml:215–233`.

Die Attrappe prüft Leserechte vor dem Sammeln und begrenzt die Liste überhaupt nicht. **Zusatzfall E5 im echten Frontend-Build:** 201 erreichte Stufen in den eigenen Gruppen werden als **201 Listeneinträge** angezeigt. Das entspricht weder der beschriebenen 200er-Grenze noch dem fehlerhaften echten Ablauf aus F2.

Eine Stufe mit leerer Uhr beantwortet `checkedRule` verständlich mit 400; der echte Server liefert derzeit 500 (F3). Die bestehenden Browserfälle verwenden kleine Listen und einen Prozentwert 0, keine leere Uhr. Sie bestehen deshalb, können diese Abweichungen aber nicht feststellen.

**Vorschlag/Fall für die Nachprüfung:** Server und Attrappe bei 201 Einträgen und bei `clock: null` gegenüberstellen. Nach der Produktkorrektur die Attrappe an den verbindlichen Vertrag anpassen und diese Grenzen ausdrücklich testen.

### H2 – Prioritätswechsel und konkurrierende Fachänderungen sind nicht ausreichend belegt

**Dateien/Zeilen:** `backend/src/test/java/taskmanager/sla/internal/EscalationsTest.java:39–54,70–79`; `backend/src/test/java/taskmanager/sla/SlaApiTest.java:713–739`; `frontend/tests/browser/backend.mjs:908–916` und `sla.test.mjs:108–134`.

Der als S-13 bezeichnete Eskalationstest prüft 80 % und die Fälligkeit bei unverändertem Budget. Die vier parallelen API-Läufe sind reine Watch-Läufe; kein Statuswechsel, keine Antwort und kein Prioritätswechsel werden dabei kontrolliert dazwischengeschoben.

In der Browserattrappe ändert ein Prioritäts-PUT nur den Prioritätswert und die Historie; die hinterlegten Zyklusbudgets und Zeitpunkte bleiben gleich. Der Browsertest bestätigt Auswahl und Historieneintrag, keine neue Eskalationsschwelle.

**Konkreter fehlender Fall:** Vor einer noch nicht erreichten 80-%-Stufe das Budget verkleinern, sodass der bereits verbrauchte Anteil die neue Stufe überschreitet; alternativ vergrößern, während ein Watch wartet. Jeweils beide Commit-Reihenfolgen mit Erwartung für `thresholdAt`, erreichte/übersprungene Stufen und bereits gespeicherte Positionen prüfen. Der Watch benutzt zwar das aktuelle Clock-Budget, eine korrekte vollständige Neuberechnung durch alle Änderungswege ist im vorgegebenen Umfang nicht bewiesen.

### H3 – Eine in den Optionen fehlende Gruppe ist verständlich reparierbar

**Datei/Zeilen:** `frontend/src/pages/SlaPage.tsx:387–391,419–426`; Zusatzfall E4.

Eine bestehende Regel behält ihre Stufen. Fehlt deren Gruppe in den Optionen, zeigt die Auswahl **„Gelöschte Gruppe“**. Beim Speichern erscheint im getesteten Frontend die Ablehnung **„Eine Stufe nennt eine Gruppe, die es nicht gibt.“** Die Stufe kann entfernt und die Regel anschließend gespeichert werden; alternativ ist eine andere vorhandene Gruppe wählbar.

Nachgestellt wurde eine fehlende Optionsgruppe in der Attrappe, **kein Löschvorgang im Berechtigungsmodul**. Die belegte Grenze entspricht der Dokumentation: Eine verwaiste Stufe erreicht niemanden und verhindert unverändertes Speichern, bis sie repariert ist. Der Vorschlag 80/100 % für die erste Gruppe wird nur beim Neuanlegen erzeugt, nicht beim Bearbeiten bestehender Regeln.

### H4 – Verzögerung und Prüfgrenzen präzise benennen

**Datei/Zeilen:** `backend/src/main/java/taskmanager/sla/internal/SlaWatch.java:42–62`; `SlaRepository.java:267–268`.

„Jede Minute“ bezeichnet das Intervall, keine harte Zusage von höchstens einer Minute Verzögerung. Beispiel: Eine offene Transaktion hält die erste betroffene `sla_ticket`-Zeile; der synchrone Watch wartet und erreicht später folgende Tickets erst danach. Das beweist keinen Deadlock, begrenzt aber die Betriebszusage. Bei großen Listen kommt die begrenzte Laufzeit hinzu.

Ein vollständiger Routenvergleich, vollständige Backend-/Frontend-Suites und eine Produktprüfung aller Status-/Antwort-/Prioritätsänderungspfade lagen außerhalb der zwei Dateidurchgänge. Der Bericht ersetzt diese Nachweise nicht.

### H5 – Eine vor dem Watch erfüllte Uhr holt eine bereits überschrittene Stufe nicht nach

**Dateien/Zeilen:** `backend/src/main/java/taskmanager/sla/internal/Escalations.java:26–28`; `SlaRepository.java:258–261`.

**Fall:** Eine 80-%-Schwelle liegt um 09:48. Die Antwort erfüllt die Uhr um 09:49, bevor der Watch sie verarbeitet. Danach wird die Uhr nicht mehr ausgewählt beziehungsweise `due` liefert wegen des Zustands keine Stufe. Die frühere Überschreitung bleibt somit ohne gespeicherte Eskalation. Bei konkurrierenden Änderungen entscheidet die Reihenfolge der Transaktionen mit, ob eine Stufe noch festgehalten wird.

Das entspricht der beschriebenen technischen Beschränkung auf laufende Uhren, ist aber durch das Abnahmekriterium „Pause/Erfüllung **vor** der Schwelle“ nicht abschließend fachlich geklärt. Vor Abnahme ausdrücklich bestätigen, ob eine nach der Schwelle, aber vor dem nächsten Watch beendete Uhr eine Stufe im Verlauf behalten soll. Die Sperre verhindert Duplikate, garantiert allein jedoch keine lückenlose Erfassung jedes historischen Übertritts. Dieser Fall wurde statisch abgeleitet und nicht durch einen gleichzeitigen echten Antwortversand nachgestellt.

## Tatsächlich ausgeführte Prüfungen

| Durchgang | Prüfung | Ergebnis |
|---|---|---|
| 1 | EscalationsTest unverändert | **6/6 erfolgreich** |
| 1 | Ausschließlich die vier TASK-070-Eskalationsfälle aus SlaApiTest, PostgreSQL 18.6 lokal, JDK 25.0.4.1 | **4/4 erfolgreich**, einschließlich paralleler Watches und Erstaufbau mit Migration V0032 |
| 1 | E1: `reviewThresholdAlreadyPassedBeforePauseGetsResumeTime` | Falscher Schwellenzeitpunkt aus F1 reproduziert |
| 1 | E2/E3: `reviewNullClockThrowsInsteadOfReturningViolation`, `reviewNullStageClockProduces500` | Exception und HTTP 500 aus F3 reproduziert |
| 2 | Frontend-Abhängigkeiten aus lokalem Cache, Vite-Produktionsbuild | erfolgreich; Hinweis auf Bundlegröße über 500 kB |
| 2 | Ausschließlich `frontend/tests/browser/sla.test.mjs`, Chrome über Playwright | **7/7 erfolgreich**, darunter drei neue TASK-070-Fälle |
| 2 | E4: fehlende Empfängergruppe im Regelformular | Platzhalter, verständliche Ablehnung und erfolgreiche Entfernung geprüft |
| 2 | E5: 201 Stufen in der Browserattrappe | 201 sichtbare Einträge, Abweichung bestätigt |
| Abschluss | Repositoryvertrag, Aufgabenübersicht, Python-Repositorytests und Diff-/Statusprüfung | Vertrag/Übersicht erfolgreich; **38 Python-Tests erfolgreich, 1 Unix-spezifischer Skip**; nur Bericht geändert |

Die drei zusätzlichen Backendtests erwarten teilweise ausdrücklich den Fehler. „Beobachtung erfolgreich“ bedeutet hier Reproduzierbarkeit, nicht erfüllte Abnahme. F2 wurde statisch abgeleitet. Keine vollständigen Produkt-Testläufe, keine aktuellen CI-Läufe, keine Docker-/Lifecycleprüfung, keine Upgradeprobe eines befüllten Bestands und kein echter Ausführer- oder Netzwerkausfall. H-11 wurde mit gewähltem Überwachungszeitpunkt statt einem gestoppten Prozess geprüft. Die Übernacht-/Pausenfälle der vorhandenen Tests bestehen; daraus folgt kein allgemeiner Nachweis der Zeitrechnung außerhalb der benannten Dateien.

## Abgleich und Empfehlung

- **Kriterium 1:** Standard-S-13, H-11 und parallele Watches bestehen. F1 und die Lücken aus H2 verhindern einen vollständigen Nachweis insbesondere für Pausen nach Schwellenübertritt und Prioritätswechsel.
- **Kriterium 2:** Die Filter verhindern im geprüften Umfang fremde Sichtbarkeit, werden beim Lesen angewandt und schützen auch die Ticketansicht. Die sichtbare Liste bleibt wegen F2 unvollständig.
- **Kriterium 3:** Die vorhandenen Tests belegen keine neue Stufe bei vorzeitig pausierter/erfüllter Uhr und den Erhalt einer bereits gespeicherten Stufe nach Abbruch des Zyklus. Der spätere Schwellenzeitpunkt nach Fortsetzen ist mit F1 falsch.
- **Kriterium 4:** Gezielte Backend-/Browsertests und Migration in frischer Datenbank erfolgreich. Vollständige Suites und Routenvergleich nicht wiederholt; F3 sowie die Abweichungen der Attrappe bleiben offen.

**F1 und F2 vor der Abnahme korrigieren, F3 im selben kleinen Korrekturumfang beheben.** Danach die konkret genannten Randfälle und die konkurrierenden Änderungsreihenfolgen gezielt nachprüfen. Die fachliche Grenze aus H5 ausdrücklich bestätigen und Produktvertrag, Server und Browserattrappe auf dieselben Grenzen bringen. Der Bericht nimmt weder TASK-070 noch dessen Voraussetzung TASK-068 ab und verändert keinen Aufgabenstatus.
