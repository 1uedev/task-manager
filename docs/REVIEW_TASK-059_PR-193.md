# Nachprüfung TASK-059 / PR #193

Ergebnis: **Die ursprünglichen Nachstellungen F1 bis F5 werden durch die Korrekturen abgefangen. Die Prüfung ist dennoch nicht ohne Befund: zwei P2 und ein P3.** F1, F3 und F4 sind im geprüften Umfang behoben; H1 ist dokumentiert, H3 besteht die ausgeführten Tests. Der dauerhafte Abgleich aus F2 funktioniert über Pakete und einen simulierten Fehler hinweg. Bei überlappenden Abrufen verschiedener UIDVALIDITY-Werte kann aber weiterhin die Position einer neuen Gültigkeit mit einem alten Wert überschrieben werden (R1). Die Empfängerprüfung aus F5 übersieht Kontakte innerhalb einer Adressgruppe (R2); ihre Grenze gilt außerdem getrennt für To und CC statt für die ganze Mail (R3).

Stand: 28. Oktober 2026. Prüfer: Codex, Sitzung `codex-20261028-task059-review-01`, getrennte technische Nachprüfung nach DEC-040. Die Korrekturen stammen aus der Claude-Sitzung `claude-20261028-helpdeskfix-01`; diese Prüfung hat daran nicht mitgearbeitet. Sie ändert weder Produktcode noch Aufgabenstatus und erklärt keine Abnahme.

## Grundlage und Abgrenzung

- Ergebnis [PR #193](https://github.com/1uedev/task-manager/pull/193), feste Prüfbasis **`059de83526a04a43e462ab7897f9164d4b3d838a`** auf main.
- Maßstab: [Prüfbericht TASK-057](REVIEW_TASK-057_PR-189.md), AGENTS.md, TODO.md (TASK-059 mit Umfang, Abnahmekriterien und Übergabe), DEC-087, backend/README.md (Helpdesk Eingang), Nachtrag TASK-059 in docs/HELPDESK_SLA.md und `contracts/helpdesk.openapi.yaml`.
- Durchgesehen wurden insbesondere `MailIntake`, `HelpdeskRepository`, `MailboxService`, `MailClient.kinds`, `ParsedMail`, `TicketCustomerService`, V0027, die fünf neuen API-Testfälle, die Testkonfiguration und die geänderten Browsertests. Der Ergebnisdiff bleibt bei den beauftragten Korrekturen und ihren Vertrags-, Migrations-, Test- und Dokumentationsfolgen; keine zusätzliche Produktfunktion festgestellt.
- Alle Daten und Mailserverantworten waren synthetisch. Zusätzliche Java-Tests wurden nur vorübergehend ergänzt und danach entfernt. Nachstellcode ohne Zugangsdaten und bereinigte Resultate stehen unter [review-evidence/task-059](review-evidence/task-059/README.md). Die Experimente E1–E9 dieses Berichts sind neu nummeriert; Verweise auf die frühere Prüfung sind ausdrücklich als solche bezeichnet.

## Was gelaufen ist

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` über den verfügbaren Python-Interpreter | erfolgreich |
| `python3 scripts/task_board.py --check` über den verfügbaren Python-Interpreter | erfolgreich; Aufgabenübersicht unverändert |
| `python3 -m unittest discover -s tests -v` | 38 Tests, keine Fehler, ein plattformbedingter Skip: Zeitzonenwechsel nur unter Unix |
| Backend-Ziel `verify`, Maven aus der Wrapper-Installation, JDK 25.0.4.1, PostgreSQL 18.6 lokal | **366 Tests, 0 Fehler, 0 übersprungen, BUILD SUCCESS**; darunter 14 `HelpdeskApiTest` einschließlich der fünf neuen Fälle |
| Frontend `npm ci` und `npm run verify`, Node 24.22.0 | erfolgreich: Vertragsgenerierung, Typprüfung, **77 Tests** und Build |
| Frontend `npm run test:browser`, installiertes Chrome über Playwright | **124/124 erfolgreich** im vollständigen Lauf |
| Helpdesk-Browsertests anschließend dreimal nacheinander | jeweils **10/10 erfolgreich** |
| Zusätzliche Experimente E1–E9 gegen PostgreSQL | abschließend **9/9 erfolgreich**; drei Assertions bestätigen Fehlerbilder, die übrigen bestätigen Kontrollfälle |
| Belegter Datenbankstand V0026 mit vorhandener Mail → V0027 | E7: vorhandene Herkunft/Position erhalten, Quelle 1 ergänzt, gleiche UID in Quelle 2 erlaubt, Doppelherkunft in Quelle 1 abgewiesen |
| Spring-Testkonfiguration und Verbindungen | `spring.test.context.cache.maxSize=8` in Quelle und Test-Klassenpfad vorhanden; Hikari schließt im Gesamtlauf ältere Pools. Eine Stichprobe während des Laufs zeigte 15 Client-Verbindungen einschließlich Kontrollabfrage; kein Nachweis einer absoluten Spitzenzahl. |
| `git diff --check`, erneute Repository- und Aufgabenübersichtsprüfung nach dem Bericht | erfolgreich; Auslieferung nur als Dokumentation/Prüfbelege |

Die zusätzliche Suite enthält Beobachtungstests: Grün bei E1, E2 und E6 bedeutet, dass der jeweils beschriebene Fehler auf der Prüfbasis reproduziert wurde. Der erste Versuch von E1 über GreenMail endete vor der fachlichen Verarbeitung mit `helpdesk.protocol_error`; die endgültige Nachstellung übergibt dieselbe Gruppen-Kopfzeile deshalb direkt über den MailClient-Testadapter an den echten Parser und die echte Datenbankverarbeitung. Dieser Unterschied ist in den Belegen ausgewiesen.

Nicht gelaufen: Docker-Image-Build, Compose-Betrieb, `lifecycle-check.sh` als Shell-Lauf, JDK 21, Linux-Chromium und ein produktiver IMAP-/DMARC-Mailweg. Die Sitzung lief unter Windows; Maven wurde direkt aus der Wrapper-Installation gestartet. Die Browser verwenden das vorhandene Backend-Testmodell. Der Ausfall-/Wiederanlauftest erzeugt neue `MailIntake`-Objekte bei erhaltener Datenbank, keinen echten JVM-Absturz. Die alten roten Regressionstests vor PR #193 wurden nicht erneut ausgeführt; die im Übergabetext genannten früheren Läufe werden nicht als eigene Prüfergebnisse ausgegeben.

## Abgleich F1 bis F5, H1 und H3

| Gegenstand | Ergebnis der Nachprüfung |
|---|---|
| **F1 – Geheimnisse im Fehlerprotokoll** | **Behoben im geprüften Fehlerpfad.** Die HTTP-Verbindungsprüfung und der Abruf protokollieren Fehlercode und Klassen der Ursachen, keine Servertexte/Throwable-Anhänge. Der neue API-Test mit zurückgespiegelter Anmeldung besteht. E8 prüft zusätzlich den allgemeinen RuntimeException-Pfad mit verschachteltem Fehlertext: nur Klassennamen, `helpdesk.intake_failed` gespeichert. `MailClient.kinds` begrenzt auch die Traversierung. |
| **F2 – Abgleich über mehrere Pakete** | **Ursprünglicher Befund behoben, Nebenläufigkeitsrest R1.** `compare_until` bleibt in PostgreSQL erhalten. Der neue API-Test verarbeitet sechs wieder aufgetauchte Nachrichten bei Paketgröße fünf ohne Duplikat. E4 ergänzt einen Fehler nach dem ersten Paket und neue Ausführerobjekte; E3 zwei gleichzeitige Abgleiche mit gleicher Message-ID. Beides ohne doppelte Tickets. Die Speicherung der Position schützt jedoch nicht gegen einen verspäteten Abruf einer anderen UIDVALIDITY. |
| **F3 – konkrete Quelle in der Herkunft** | **Behoben in den nachgestellten Fällen.** V0027 erhält vorhandene Daten (E7). Der API-Regressionstest trennt gleiche UIDVALIDITY/UID nach Kontowechsel über `source`. In E5 erfolgt der Kontowechsel während eines angehaltenen alten Abrufs; der alte Eingang trägt Quelle 1, der neue Quelle 2, und die neue Position bleibt unverändert. Ein Ordnerwechsel benutzt denselben geprüften Generationspfad, wurde aber nicht zusätzlich mit zwei realen Serverordnern ausgeführt. |
| **F4 – Auswahl nach Feldregeln** | **Behoben.** Kunden benötigen Lesen und Schreiben auf `customer`; Kontakte zusätzlich beides auf `contact`. Die neuen Tests prüfen fehlendes Lesen auf customer, fehlende Kontaktrechte, Lesen ohne Schreiben sowie vollständige Rechte. E9 ergänzt Schreiben ohne Lesen auf contact: keine Kontaktdaten. Der Filter liegt im Backend und im Vertrag, nicht nur in der Oberfläche. |
| **F5 – alle Empfänger prüfen** | **Teilweise behoben.** Der frühere Fall „fremder Kunde an CC-Position 51“ wird erkannt, und 1001 CC-Adressen führen zu `recipients_incomplete`. Die Prüfung verwendet nun eine mengenbasierte Datenbankabfrage. Adressgruppen und die gemeinsame Grenze bleiben unvollständig: R2/R3. |
| **H1 – Vertrauensvoraussetzung** | **Dokumentiert.** README und SLA-Nachtrag verlangen das Entfernen eingeschleuster eigener Authentication-Results-Header, passende authserv-id und eine Testmail vor Inbetriebnahme. Die Unterscheidung zwischen bestätigter Domain und individueller Person steht dabei. Kein tatsächlicher Empfangsserver wurde hier freigegeben oder getestet. |
| **H3 – stabile Tests** | **Für die ausgeführten Läufe bestätigt.** Browsertests warten auf das Entfernen einer bearbeiteten Mail beziehungsweise auf die neu geladene Kontaktoption. Der vollständige Lauf und drei Wiederholungen der Helpdesk-Tests bestehen. Der Spring-Cache ist auf acht Kontexte begrenzt; das ist eine Cachegrenze, keine Obergrenze aller vorübergehend manuell erzeugten Kontexte. Hikari gibt zusätzliche ungenutzte Verbindungen nach konfigurierten zehn Sekunden zurück. |

## Befunde

### R1 · P2 · Ein alter Abruf kann die Position einer neuen UIDVALIDITY über noch ungelesene Mails hinausschieben

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/HelpdeskRepository.java:254`, `:256`, `:266`; `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:110`, `:188`.

`position` prüft die Quellgeneration, schreibt die übergebene UIDVALIDITY aber ungeprüft zurück und verbindet den vorhandenen Zähler mit `greatest(last_uid, ?)`. Bei einem UIDVALIDITY-Wechsel bleibt `source` gleich. Ein verspäteter alter Abruf darf deshalb seinen alten Wert nach dem bereits erfolgten Wechsel speichern. Ein weiterer neuer Abruf kann danach die neue UIDVALIDITY zurückschreiben, behält aber die zu hohe alte Position. Die zusätzliche Sperre beim Message-ID-Abgleich schützt diese getrennten Positionsupdates nicht. Auch `restartFolder` prüft nur `source`, nicht den erwarteten vorherigen Gültigkeitsstand.

**Nachstellung E6:** Ein deterministischer MailClient-Adapter hält bereits gelesene Nachrichten an den benötigten Übergängen zurück; Parser, Transaktionen und PostgreSQL sind echt. Alle Abrufe betreffen dieselbe Quelle:

1. Abruf A liest UID 100 unter UIDVALIDITY 100 und wartet vor Rückgabe der Nachricht an die Verarbeitung.
2. Nach dem modellierten Gültigkeitswechsel verarbeitet B unter UIDVALIDITY 200 die neue UID 1. Der gespeicherte Zähler ist 1, die Abgleichgrenze 2.
3. Abruf C übernimmt diesen neuen Stand, liest UID 2 und wartet ebenfalls.
4. A wird freigegeben: Er schreibt die alte Gültigkeit und Position 100 zurück. Anschließend wird C freigegeben: Er setzt die Gültigkeit wieder auf 200; `greatest` lässt die Position bei 100.
5. Die neue Mail UID 3 wird hinzugefügt. Der nächste Abruf startet oberhalb 100 und übernimmt sie nicht.

**Beobachtet:** Gültigkeit 200, Position 100 nach Verarbeitung von UID 2; UID 3 bleibt ohne Eingang/Ticket und ungelesen auf dem modellierten Server. Die Abgleichgrenze ist bereits gelöscht. Die Nachricht ist nicht physisch gelöscht, wird aber bis zu einer Korrektur beziehungsweise einem weiteren Gültigkeitswechsel übergangen.

**Vorschlag:** Cursor und Abgleichzustand an Quelle **und** UIDVALIDITY beziehungsweise eine gemeinsame Abrufgeneration binden. Den Wechsel atomar gegen den erwarteten bisherigen Stand vornehmen; gewöhnliche Positionsupdates dürfen die Gültigkeit nicht wechseln und müssen einen veralteten Abruf verwerfen. Den Zustand bei konkurrierenden Übergängen unter konsistenter Sperre neu lesen. Die Nachstellung als Regressionstest mit Erwartung „UID 3 wird übernommen“ ergänzen.

**Einordnung:** Nachgewiesen ist ein Fehler bei überlappenden Abrufen, kein erzeugter Fehler des Jobs-Leasingmechanismus. Die reguläre einmalige Planung reduziert solche Überlappungen; ein realer Serverwechsel mit gleichzeitigem Lease-Verlust wurde nicht ausgelöst. Der Prüfauftrag verlangt ausdrücklich Nebenläufigkeit. Der Befund wird nicht als erstmals durch PR #193 entstandener Fehler behauptet.

### R2 · P2 · Ein fremder Kundenkontakt innerhalb einer Empfängergruppe wird weiterhin automatisch zugeordnet

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/ParsedMail.java:251`, `:256`; `backend/src/main/java/taskmanager/helpdesk/internal/HelpdeskRepository.java:113`.

Mailadressen dürfen in To/CC als benannte Gruppe vorkommen, etwa `Support-Team: kontakt@anderer-kunde.example;`. Das ist gültige Syntax nach [RFC 5322, Abschnitt 3.4](https://www.rfc-editor.org/rfc/rfc5322.html#section-3.4). `InternetAddress.parseHeader` liefert dafür ein Gruppenobjekt. Der Code übernimmt dessen gesamten `getAddress()`-Wert als eine Adresse, statt die enthaltenen Postfachadressen auszulesen. `recipientsComplete` bleibt wahr. Die Datenbankabfrage vergleicht anschließend den gesamten Gruppentext mit einzelnen Kontaktadressen und findet den fremden Kunden nicht.

**Nachstellung E1:** Bekannter bestätigter Absender eines freigegebenen Kunden, in CC eine Gruppe mit genau einem Kontakt eines anderen Kunden. Der echte Parser liefert eine Liste mit dem vollständigen, kleingeschriebenen Gruppentext und meldet Vollständigkeit. Übergabe über den Testadapter an `MailIntake` ergibt **ein automatisches Ticket**, keinen Eintrag mit `foreign_customer` oder `recipients_incomplete`.

**Vorschlag:** Gruppen erkennen und ihre enthaltenen Adressen vor Normalisierung, Begrenzung und Kundenprüfung auswerten. Kann eine Gruppe nicht vollständig gelesen werden, muss sie zur Prüfwarteschlange führen. Regressionen für Gruppen in To und CC, leere Gruppen und eine Gruppe mit vielen Mitgliedern ergänzen. Die Grenze muss die enthaltenen Adressen zählen.

### R3 · P3 · Die Grenze von 1000 Empfängern gilt zweimal statt einmal pro Nachricht

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/ParsedMail.java:60`, `:64`, `:69`, `:255`.

To und CC werden unabhängig eingelesen, jeweils mit einer eigenen Liste und Grenze von 1000. Bei der Zusammenführung werden nur die beiden Vollständigkeitsmerkmale verknüpft. So kann eine Mail insgesamt bis zu 2000 einzelne Empfänger tragen und trotzdem als vollständig gelten. Das widerspricht DEC-087 sowie der README/SLA-Aussage, dass mehr als 1000 Empfänger zur Prüfung führen. Anders als R2 lässt diese Variante bei gewöhnlichen Einzeladressen keine Adressen aus der Kundenprüfung weg; deshalb P3.

**Nachstellung E2:** Eine To-Adresse und 1000 weitere, unterschiedliche CC-Adressen in korrekt gefalteten Kopfzeilen, bestätigter bekannter Absender. Ergebnis: **automatisches Ticket statt `recipients_incomplete`**. Der neue vorhandene Test verwendet 1001 Adressen allein in CC und deckt diese Aufteilung nicht ab.

**Vorschlag:** Ein gemeinsames Budget für alle relevanten Empfänger über To und CC verwenden, einschließlich aufgelöster Gruppen. Die kombinierte Grenze auch in `headerOnly` anwenden. Tests für insgesamt 1000 und 1001 Adressen mit verschiedenen Aufteilungen ergänzen.

## Hinweise und Grenzen

1. **Sperre und Verklemmungen:** E3 lässt zwei getrennte Ausführer denselben Abgleich konkurrierend betreten; E5 ändert die Einstellungen während eines laufenden Abrufs. Beide beenden sich ohne Deadlock. Die neue Sperre hält die Entscheidung über Message-ID und Eintrag zusammen, aber nicht den ganzen IMAP-Abruf. `lockMailboxForFetch` ist weiterhin nicht der aufgerufene Weg. Eine umfassende Freiheit von Verklemmungen in allen Modulkombinationen ist damit nicht bewiesen; R1 entsteht ohne Deadlock.
2. **Grenzen des Wiederanlaufnachweises:** Die fünf neuen API-Testfälle sind auf dieser Basis grün und entsprechen den alten Nachstellungen. Der F2-Test verwendet aber keinen echten Prozessneustart. E4 ergänzt Fehler und neue Ausführerobjekte bei erhaltenem Datenbankzustand. Das belegt die Persistenz der Grenze, ersetzt keinen Absturztest des gesamten JVM-/Jobs-/IMAP-Verbunds.
3. **Browser-Testmodell:** `frontend/tests/browser/backend.mjs` liefert für `/helpdesk/options` weiterhin Kontakte allein nach dem Schreibrecht auf customer und bildet die verschärften Backendregeln nicht vollständig nach. Die echten API-Tests bestätigen F4; die grünen Browserläufe allein wären dafür kein ausreichender Nachweis. Das Testmodell sollte künftig an den Vertrag angepasst werden.
4. **Nicht erneut entschieden:** H2 und H4 der Erstprüfung bleiben wie beauftragt Holgers Abnahme vorbehalten. H5/H6 und TASK-058 sind nicht Inhalt der Korrekturprüfung. Es wurden keine Antworten versandt, keine produktiven Mailkonten verbunden und keine echten Kundendaten verwendet.
