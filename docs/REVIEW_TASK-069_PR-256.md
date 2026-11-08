# Technische Prüfung TASK-069 / PR #256

**Ergebnis:** Drei Pflichtbefunde F1–F3 offen. Der geprüfte Stand ist technisch noch nicht zur Abnahme empfohlen. Die drei angekündigten Abweichungen bleiben Holgers fachlicher Entscheidung vorbehalten (H1).

## Auftrag und Stand

- Prüfende Sitzung: `codex-20261108-time-review-01`, 8. November 2026; getrennt von Autor `claude-20261030-time-01`, eigene Arbeitskopie.
- Repository: `1uedev/task-manager`, [PR #256](https://github.com/1uedev/task-manager/pull/256), damaliger Branch `claude/project-thread-thp5tg`.
- Geprüfter PR-Endstand: `1125238d451a0b1cca12be48cefc048562d59735`; Vergleichsbasis: `faf73a16555ded0a412abf300b45451a2812c348`.
- Der PR wurde am 4. November 2026 bereits zusammengeführt, Mergecommit `2222f687a98146b2b0c528b869b6b4dd16e7a811`. Der Quellbranch ist inzwischen gelöscht; die Prüfung erfolgte am gesicherten Commit. Aktueller `main` wurde zur Einordnung gelesen, nicht als Ersatz für den Prüfstand verwendet.
- Maßstab: `AGENTS.md`, `docs/DEVELOPMENT_REVIEW.md`, die 15 bestätigten Regeln und vier Abnahmekriterien von TASK-069 in `TODO.md`, DEC-099 sowie REQ-TIME-001/002 und die Abgrenzung REQ-TIME-003. Die ausdrückliche Reviewzuweisung erteilt keinen Implementierungsauftrag und ändert keine Aufgabenreservierung.
- Dies ist eine getrennte technische KI-Prüfung nach DEC-040. Die weitergehenden Freigabeschwellen aus `DEVELOPMENT_REVIEW.md` sind weiterhin Vorschläge. Holgers Fachabnahme wird nicht vorweggenommen; TODO/DONE und Produktcode wurden nicht geändert.

**Prüfumfang:** ausschließlich V0053, alle sieben Dateien des Moduls `time`, `ProjectAccess.java`, `JdbcProjectAccess.java` mit Schwerpunkt `members`, die drei neuen Aktionen des `PermissionsController`, `time.openapi.yaml`, `TimeApiTest.java`, `TimeContractTest.java`, `TimePage.tsx`, `duration.ts`, der Zeitabschnitt von `product.ts` und `time.test.mjs`.

**Pfadkorrektur:** Der im Auftrag genannte Pfad `backend/src/main/java/taskmanager/permissions/web/PermissionsController.java` existiert an diesem Commit nicht. Die drei Aktionen stehen in `backend/src/main/java/taskmanager/PermissionsController.java:71`; ausschließlich diese Ergänzung wurde geprüft. Nicht genannte Integrationsdateien, insbesondere `TicketPage.tsx`, die Browser-Backendattrappe und die Verdrahtung anderer Module, sind nicht Gegenstand eines Codeprüfurteils. Öffentliche Hilfstypen und Teststartkonfiguration wurden nur zur Ausführung der Gegenproben herangezogen.

## Pflichtbefunde

### F1 · P2 · Die Exportgrenze kürzt auch die als vollständig zugesagten Summen

**Ort:** `backend/src/main/java/taskmanager/time/internal/TimeService.java:209–215`, Summenbildung `:221–239`. Gegenmaßstab: `contracts/time.openapi.yaml:103–105`, `:359`, DEC-099 und Abnahmekriterium 1.

`listing()` lädt höchstens 50.001 Zeilen je Projekt und kürzt die zusammengeführte Ergebnismenge vor der Aggregation auf 50.000. `totalMinutes`, `perPerson`, `perTicket` und `count` entstehen anschließend nur aus diesem Ausschnitt. Die Grenze von 50.000 ist laut DEC-099 eine Exportgrenze; bei der Liste dürfen nur die zurückgegebenen Einzelbuchungen auf 500 begrenzt werden. Die Summen sollen alle passenden wirksamen Buchungen umfassen.

**Reproduktion mit echter API/PostgreSQL:** 50.001 sichtbare wirksame Buchungen zu jeweils einer Minute in einem Projekt anlegen. `GET /api/v1/time/entries?project=…` liefert 500 Zeilen, `truncated=true`, aber `count=50000` und `totalMinutes=50000`. Erwartet sind 50.001 Buchungen und 50.001 Minuten. Die unabhängige Sollwertprüfung schlug entsprechend fehl. Mit `cancelled=true` können außerdem zahlreiche neuere Stornos ältere wirksame Buchungen aus der Summenberechnung verdrängen.

**Auswirkung:** Projekt-, Personen- und Ticketsummen sowie die Anzahl werden bei größeren Beständen zu klein ausgewiesen. Ein allgemeiner Kürzungshinweis erklärt die Abweichung nicht, weil Vertrag und DEC ausdrücklich vollständige Summen zusagen.

**Erforderliche Korrektur:** Aggregation und Anzahl über den vollständigen berechtigten Filterbestand berechnen; die Begrenzung der ausgegebenen Liste und die Ablehnung zu großer Exporte davon trennen. Regression für 500, 501, 50.000 und 50.001 Zeilen, mehrere Projekte und eingeschlossene Stornos ergänzen.

### F2 · P2 · Eine Korrektur entfernt die verborgene Ticketzuordnung ohne bewusste Auswahl

**Ort:** `frontend/src/pages/TimePage.tsx:202–206`, Übergabe `:105–107`; serverseitige Übernahme in `backend/src/main/java/taskmanager/time/internal/TimeService.java:290–304`.

Der Server maskiert ein nicht lesbares Ticket korrekt als `ticket=null`. Das Korrekturformular übernimmt diesen Wert aber als tatsächlich leeres Ticketfeld (`entry.ticket?.number ?? null`). Beim Speichern einer bloßen Dauerkorrektur sendet es `ticketNumber:null`; `ticketOf()` und `correct()` behandeln dies als Entfernung der Zuordnung.

**Reproduktion mit echter API/PostgreSQL und dem Payload des Formulars:** Eigene 30 Minuten auf ein Ticket buchen. Nur `ticket.read` entziehen, `time.book` behalten. Die Buchung bleibt korrekt lesbar, ihr Ticket ist verborgen. Dauer auf 40 Minuten ändern und den vom Formular erzeugten leeren Ticketwert mitsenden. Antwort: 200. Nach Wiedererteilung von `ticket.read` bleibt das aktuelle Ticket `null`; der ursprüngliche Verweis ist nur noch in der Historie vorhanden. Die unabhängige Erhaltungsprüfung schlug fehl.

**Auswirkung:** Eine berechtigte Korrektur von Datum, Dauer oder Beschreibung ändert zusätzlich unbemerkt den fachlichen Zusammenhang. Die Zeit verschwindet aus der bisherigen Ticketansicht und Ticketsumme und zählt stattdessen unter „ohne Ticket“. Das betrifft eigene Buchungen ebenso wie Korrekturen mit `time.manage` ohne Ticketleserecht.

**Erforderliche Korrektur:** Einen unveränderten, nicht sichtbaren Ticketverweis von einer bewusst gewählten Entfernung unterscheiden. Formular und Vertrag müssen diese Unterscheidung ermöglichen; serverseitig darf ein maskierter Leerwert die Zuordnung nicht still entfernen. Dafür kein verborgenes Ticket offenlegen. Regression mit Entzug und Wiedererteilung von `ticket.read` ergänzen.

### F3 · P2 · „Meine Zeiten“ berechnet Wochen- und Tagessummen aus höchstens 500 Zeilen

**Ort:** `frontend/src/pages/TimePage.tsx:379–387`, fehlende Behandlung der Kürzung im Wochenbereich `:377–403`.

`MyWeek` summiert `list.entries` erneut, statt die vollständigen eigenen Aggregate zu verwenden. Bereits bei 501 eigenen Wochenbuchungen liefert die API nur 500 Einzelzeilen, aber weiterhin die korrekte Gesamtsumme. Der Wochenbereich ignoriert diese Summe und auch `truncated`.

**Reproduktion im Browser:** Die Wochenabfrage mit einer vertragskonformen Antwort aus 500 sichtbaren eigenen Zeilen zu je einer Minute, `count=501`, `totalMinutes=501` und `truncated=true` beantworten. Die Oberfläche zeigt 500 Minuten beziehungsweise „8 Std. 20 Min. (8,33 h)“ statt 501 Minuten. Der Browser-Sollwerttest schlug fehl; der sichtbare Bereich wurde zusätzlich als Screenshot kontrolliert. Tageswerte werden ebenfalls nur aus den angezeigten Zeilen berechnet.

**Auswirkung:** Eine vollständige Wochen- und Tagesauswertung wird vorgetäuscht; bei derselben Datenmenge liefert die allgemeine Auswertung bis zur Grenze aus F1 bereits andere Summen. Regel 14 und die Gleichheit von API und Oberfläche aus Abnahmekriterium 1 sind verletzt.

**Erforderliche Korrektur:** Die vollständige eigene Wochensumme verwenden und vollständige Tagesaggregate bereitstellen beziehungsweise abrufen. Dabei den Personenfilter sicher setzen, damit ein Konto mit `time.read` keine fremden Werte in „Meine Zeiten“ einrechnet. Eine gekürzte Einzelauflistung sichtbar kennzeichnen; Regression mit mehr als 500 eigenen Wochenbuchungen ergänzen.

## Hinweise

### H1 · Die drei offengelegten Abweichungen sind technisch einzuordnen, fachlich noch zu entscheiden

| Abweichung und Ort | Technische Bewertung | Offene Entscheidung |
|---|---|---|
| 412 statt 409; `TimeService.java:335–339`, `TimeRequests.java:41–52`, `contracts/time.openapi.yaml:18–20` | Vertrag, Backend und Oberfläche verwenden konsistent 412 für eine nicht passende Version aus `If-Match`; fehlende Vorbedingung ergibt 428, eine bereits stornierte Buchung mit aktueller Version 409. Die ausgeführten Konflikt- und Stornofälle entsprechen diesem Vertrag. | Die bestätigte Regel 11 nennt ausdrücklich 409. Die Begründung mit den Tickets ersetzt keine Zustimmung zur Änderung dieser Regel. Empfehlung: 412 als abgestimmte Vertragsabweichung ausdrücklich bestätigen und den Regeltext nachführen. |
| UTC als „heute“; `TimeService.java:349–354`, `contracts/time.openapi.yaml:23–24`; Browserdatum `TimePage.tsx:27–35` | UTC plus ein Tag ist umgesetzt und getestet. Es entspricht nicht immer dem Datum einer Unternehmenszeitzone plus einem Tag. Beispiel: 7. November 2026, 23:30 UTC ist in Berlin bereits der 8. November. Die bestätigte Regel würde dann den 9. November zulassen, der Code nur den 8. November. Datum und Dauer gespeicherter Buchungen werden dadurch nicht nachträglich umgerechnet. | UTC als vorläufige Regel ausdrücklich bestätigen oder die Unternehmenszeitzone umsetzen. Der Puffer beseitigt den Unterschied der beiden Regeln nicht. Grenztests an lokaler Mitternacht fehlen. |
| Verborgenes Ticket in „ohne Ticket“; `TimeService.java:231–234`, `:452–457`, Beschriftung `TimePage.tsx:269–272` | Die Zusammenfassung schützt Nummer, Titel und UUID des verborgenen Tickets. Liste, Detailhistorie und CSV geben die maskierten Ticketkennungen nicht roh aus. Die Zeitsumme bleibt grundsätzlich erhalten; das Verhalten ist im Vertrag beschrieben. Davon getrennt besteht F2 bei Korrekturen. | Die Gruppe bezeichnet technisch „ohne sichtbares Ticket“ und enthält auch tatsächlich verknüpfte Buchungen. Diese fachliche Bedeutung und möglichst die präzisere Beschriftung bestätigen; nicht als Zahl ausschließlich unzugeordneter Buchungen auslegen. |

Keine dieser Entscheidungen wird durch dieses Review als von Holger getroffen vermerkt.

### H2 · Die vorhandenen Tests belegen weniger als die Übergabe zur Commitprüfung erwarten lässt

**Ort:** `backend/src/test/java/taskmanager/time/TimeApiTest.java:417–437`, insbesondere `:434–437`; Grenz- und Exportfälle `:440–461`; `frontend/tests/browser/time.test.mjs:117–150`.

`changesNeedTheCurrentVersionAndTheCurrentRight()` entzieht das Recht vor Beginn des nächsten HTTP-Aufrufs. Das ist ein sinnvoller Test, belegt aber keinen Rechteentzug während einer bereits gestarteten Änderung. Auch die Mengenränder 500/501 und 50.000/50.001, eine Dauerkorrektur mit verborgenem Ticket und ein direkt geprüftes CSV-Ergebnis im Browser fehlen im gelieferten Zeit-Testbestand. Der Browser-Exportfall prüft nur den Link; die API prüft den Dateiinhalt. Der Vertragstest gleicht Methoden und Routen ab, keine Antwortschemas oder Berechtigungen.

Die zusätzliche Reviewprobe hat einen HTTP-Korrekturaufruf tatsächlich hinter einer laufenden Entzugstransaktion blockiert und danach fortgesetzt: 404, unverändert 30 Minuten, Version 1, eine Revision. Dieser Ablauf ist korrekt. Die unabhängigen Mengen- und Korrekturproben reproduzieren dagegen F1–F3. Empfohlen ist die dauerhafte Aufnahme dieser Regressionen einschließlich expliziter Portal- und projektübergreifender Negativfälle. Die zusätzliche Konkurrenzprobe deckt die Reihenfolge „Entzug gewinnt“ ab; sie ist kein vollständiger Nachweis aller Commit- und Sperrreihenfolgen.

## Abgleich mit den 15 bestätigten Regeln

| Regel | Prüfungsergebnis im beauftragten Umfang |
|---|---|
| 1 · Pflichtwerte und optionales Ticket desselben Projekts | Migration und Service validieren Datum, 1–1440 Minuten und nicht leere Beschreibung bis 1000 Zeichen. Tickets werden über Projekt und Nummer aufgelöst. Korrekturen eines maskierten Verweises verletzen den beabsichtigten Zusammenhang: F2. |
| 2 · Volle Minuten, Eingabe und Anzeige | Dauer wird ganzzahlig gespeichert, die vier bestätigten Schreibweisen sind im Parser umgesetzt. Typprüfung und vorhandene Frontendtests erfolgreich; CSV-Rundung mit unabhängigen Beispielen geprüft. |
| 3 · Datum, Zukunftspuffer, rückwirkend | UTC plus ein Tag statt Unternehmenszeitzone: H1. Der API-Test prüft morgen, übermorgen und ein zurückliegendes Datum. |
| 4 · Geschlossene Tickets | Im geprüften Buchungspfad keine Ablehnung anhand des Ticketstatus und keine Änderung des Tickets. Kein zusätzlicher ausgeführter Abschlussstatus-Test; Archivierung bleibt ausdrücklich außerhalb des Pakets. |
| 5 · Drei Zeitaktionen, eigene/fremde Buchungen | Aktionen im Katalog vorhanden; `time.manage` wird durch `readsAll()` wie `time.read` behandelt. Ohne beide werden Listen, Personenfilter, Summen und Exporte auf eigene Buchungen beschränkt. API-Negativproben erfolgreich. |
| 6 · Ticketleserecht allein, externe Kunden | `ticket.read` allein öffnet keine Zeiten; API- und Browserfall erfolgreich. Externe Aufrufer erhalten im Service keinen Zeitbereich. Ein echter Portal-HTTP-Test wurde in dieser Prüfung nicht ergänzt. |
| 7 · Für andere nur aktive Mitglieder | Service prüft Mitgliedschaft und `accountIsActiveInternal`; Projektport `members` fügt keine eigene Umgehung hinzu. Der vorhandene Test weist Außenstehende ab. Kein zusätzlicher Nebenläufigkeitstest zur Deaktivierung der Zielperson. |
| 8 · Korrektur, Historie, Grund | Alte und neue Werte bleiben als Revisionen mit Bearbeiter und Zeitpunkt erhalten. Fremde Korrektur/Storno verlangen einen Grund. Versionierung korrekt; F2 betrifft die unbeabsichtigte zusätzliche Ticketänderung. |
| 9 · Storno | Keine Lösch- oder Rücknahmefunktion im Modul; Stornos bleiben im Detail und mit Listenfilter lesbar, zählen nicht zu Summen und Export. Rechnung und erneutes Storno getestet. F1 betrifft die Vorbegrenzung bei großer Trefferzahl. |
| 10 · Keine Korrekturfrist | Keine Altersgrenze im Korrekturpfad gefunden. |
| 11 · Versionskonflikt | Optimistische Version nach Zeilensperre; veraltete Änderung überschreibt nichts. Statusabweichung 412: H1. |
| 12 · Filter, Summen, CSV, mehrere Projekte | Filter und Rechtebegrenzung im gemeinsamen Lesepfad; CSV-Metadaten und Einheit vorhanden. Grenzfehler F1. Einzelprojekt-Filter, Personenfilter und Exportgrenzen ausgeführt; projektübergreifende Berechtigungsabgrenzung zusätzlich statisch geprüft. |
| 13 · Ticketansicht | Der geprüfte `TicketTime`-Baustein verwendet dieselbe gefilterte API; ohne Zeitaktion kein Bereich. Buchen, Korrektur, Storno und Konflikt im Browser erfolgreich. Seine Einbindung in `TicketPage.tsx` war nicht Teil des Auftrags. |
| 14 · Meine Zeiten | Wochenwechsel und normale Buchungen erfolgreich; größere Wochenbestände falsch summiert: F3. |
| 15 · Paketgrenzen | Im geprüften Modul keine Timer, Freigaben, Abrechnung, Suche oder KI. Eigene Revisionshistorie; kein Schreiben in die Tickethistorie im Zeitpfad. |

**Abnahmekriterien:** Kriterium 1 ist wegen F1–F3 und der noch offenen Regelabweichungen nicht erfüllt; das Rechenbeispiel 120 → 105 → 75 Minuten besteht. Zu Kriterium 2 wurde in den geprüften Lesewegen kein unberechtigter Fremdzugriff gefunden; F2 verändert jedoch den Zusammenhang bei einer erlaubten Korrektur. Kriterium 3 besteht für den zusätzlich ausgeführten konkurrierenden Entzug mit unverändertem Buchungs- und Revisionsstand; die genannte Begrenzung der geprüften Sperrreihenfolgen gilt. Zu Kriterium 4 sind Migration, acht Vertrags-/API-Tests und fünf Zeit-Browsertests erfolgreich ausgeführt; die fehlenden Regressionen stehen in H2.

## Ausgeführte Prüfungen und Nachweise

Die zusätzlichen Gegenproben wurden vorübergehend ausgeführt und danach aus dem Quellbaum entfernt; Produktdateien blieben unverändert. Mengenbestände wurden synthetisch per SQL erzeugt. Die Browserfälle verwenden die vorhandene Backendattrappe; der zusätzliche Wochenfall ersetzt gezielt eine Antwort durch die beschriebene vertragskonforme Grenzmenge. API-Gegenproben liefen gegen die echte Anwendung und eine isolierte PostgreSQL-Datenbank.

| Prüfung | Ergebnis |
|---|---|
| `python scripts/validate_repository.py` | Erfolgreich. |
| `python scripts/task_board.py --check` | Erfolgreich; Übersicht unverändert aktuell. |
| `python -m unittest discover -s tests -v` | 38 Tests erfolgreich abgeschlossen, davon ein auf Windows übersprungener Zeitzonenwechseltest; 0 Fehler, 0 Fehlschläge. |
| `git diff --check` | Erfolgreich; abschließende Prüfung einschließlich Bericht. |
| Backend: `mvn -o -Dtest=TimeApiTest,TimeContractTest test` mit expliziter lokaler Testdatenbank | 8 Tests, 0 Fehler, 0 Fehlschläge; 7 API-Fälle und ein Routenvertragstest. Migration bis V0053 erfolgreich. |
| Frontend: `npm ci --offline`, danach `npm run verify` | Installation, Vertragstypen, Typprüfung, 82 Tests und Build erfolgreich; Build meldet die allgemeine Warnung zur Bündelgröße. |
| Browser: `node --test tests/browser/time.test.mjs` | 5 Tests erfolgreich. |
| Zusätzliche API-Gegenproben `Time069ReviewProbe` | 5 Tests: 3 erfolgreich, 2 erwartungswidrig fehlgeschlagen und als F1/F2 dokumentiert; keine Ausführungsfehler. Erfolgreich: Fremdzugriffsschutz, konkurrierender Rechteentzug, CSV-Schutz und Rundung. |
| Zusätzlicher Browser-Grenztest | Erwartungswidrig fehlgeschlagen: 500 statt 501 Minuten, F3. Wiederholt bestätigt; sichtbaren Browserbereich kontrolliert. |

**Mengengrenzen konkret:** Bei 500 Treffern 500 Zeilen und keine Kürzung; bei 501 Treffern 500 Zeilen, vollständige 501 Minuten und Kürzungsmarker; bei 50.000 Treffern Export 200 mit 50.000 Datenzeilen; bei 50.001 Treffern Export 400 `time.export_too_large`. Die Liste unterschlägt bei 50.001 Treffern eine Minute und eine Buchung (F1). Der Export liefert oberhalb seiner Grenze keine unvollständige CSV.

**CSV konkret:** Alle fünf von DEC-099 geforderten Anfangszeichen `=`, `+`, `-`, `@` und Tab werden durch ein Hochkomma geschützt. Anführungszeichen werden verdoppelt, Semikolon bleibt innerhalb der zitierten Zelle, Zeilenumbrüche werden neutralisiert. Unabhängige Rundungswerte: 1 Minute → `0,02`, 30 → `0,50`, 59 → `0,98`, 75 → `1,25`. Der echte Exporttest bestätigt BOM, Zeitraum, Filter, Einheit, Rundung der Gesamtsumme und Ausschluss von Stornos. Kein zusätzlicher Importversuch in Excel oder LibreOffice.

**Umgebung und Grenzen:** Windows, Java 25.0.4.1, Maven 3.9.16, Node 24.22.0, Chrome und PostgreSQL 16.15 mit expliziter Testkonfiguration. Dies ist kein Nachweis für den gepinnten PostgreSQL-18.6-Container. Initiale Startversuche scheiterten an lokalem Netzwerkzugriff beziehungsweise einer unvollständigen synthetischen Datenbankkonfiguration; die oben genannten erfolgreichen Wiederholungen liefen mit vervollständigter Konfiguration. Kein Docker-Lifecycle-, vollständiger Backendgesamt-, Last- oder Wiederherstellungslauf; kein allgemeines Codeprüfurteil über Dateien außerhalb des Auftrags.

## Nächster Schritt

F1–F3 in einem gesondert beauftragten Korrekturschritt beheben und die betroffenen Fälle erneut prüfen. Holger entscheidet die drei Abweichungen aus H1 ausdrücklich. TASK-069 bleibt bis zur fachlichen Abnahme im bisherigen Status; der erfolgte Merge ersetzt diese Abnahme nicht.
