# Unabhängiges Review TASK-067 / PR #221

**Empfehlung: nachbessern.** Der Zustand „neu“, die Versionierung und die normale Rechteprüfung funktionieren in den geprüften Abläufen. Offen bleiben eine fehlende Ticketprüfung beim Markieren nach Projektwechsel, eine verschwindende Konfliktmeldung mit Fokusverlust sowie ein unsichtbarer Fehler beim Laden der Liste.

## Prüfstand

- Sitzung: `codex-20261104-newanswers-review-01`, unabhängige zweite KI: Codex, 4. November 2026.
- Geprüft: aktuelles `main`, **`b4004be8e95383903ddb7b6783bdb91a4e0a8c1f`**, [PR #221](https://github.com/1uedev/task-manager/pull/221), am 4. November gemergt. Eigener Arbeitsbereich und Branch `review/codex-20261104-newanswers-review-01`.
- Gelesen: AGENTS.md, TASK-067 in TODO.md, DEC-097, backend/README.md „Neue Kundenantworten (TASK-067)“, H7 in docs/REVIEW_TASK-058_PR-202.md, ergänzend Arbeitsablauf, Anforderungen, Vertrag, Implementierung und Tests.
- Keine Änderung am Anwendungs- oder Testcode, Vertrag, Migrationsskript oder Aufgabenstatus im Repository. Zusätzliche Prüfprogramme, Logs und Bilder wurden außerhalb des Repository-Arbeitsbereichs erstellt. Alle Daten, Konten, Mails und Datenbanken waren synthetisch.
- Schweregrade: P1 dringend, P2 regulär zu beheben, P3 gering. Die nummerierten Befunde sind mit ihrem Schweregrad gekennzeichnet; es wurde kein P1-Befund festgestellt. Eine technische Empfehlung ersetzt Holgers fachliche Abnahme nicht.

## Befunde

### F1 [P2] – Markieren prüft das aktuelle Ticketprojekt nicht

**Datei/Zeile:** `backend/src/main/java/taskmanager/helpdesk/internal/AnswerService.java:66–79`, insbesondere `:68`; zum Vergleich Liste `:54–56` und Anhängen `ReviewService.java:142–146`.

`seen` prüft die Rechte ausschließlich für `unlocked.projectId()`, also die Projektkennung der Mail. Nach dem Sperren der Mail fehlen eine Auflösung des aktuellen Tickets und der Vergleich zwischen Ticketprojekt und Mailprojekt. Die Liste führt genau diesen Vergleich bereits durch und blendet eine solche Mail aus.

**Reproduktion:** In einer isolierten Testdatenbank einen gültigen neuen Antwortzustand an Ticket T in Projekt A vorbereiten, einer Person die Antwortrechte nur in A geben und anschließend `ticket.project_id` von T auf ein anderes Projekt B setzen. Die Person bleibt ohne Mitgliedschaft in B. Die echte HTTP-Schnittstelle liefert danach bei `GET /api/v1/helpdesk/answers` eine leere Liste, bei `POST /api/v1/helpdesk/answers/{mailId}/seen` mit aktueller Version aber **200**. `seen_at`, `seen_by`, Version und Audit werden für das nun fremde Ticket geändert.

**Auswirkung:** Wer die Mailkennung und Version aus dem alten Projekt kennt, kann den Hinweis für ein inzwischen nicht mehr lesbares Ticket für das ganze Team beseitigen. Der erfolgreiche Aufruf bestätigt außerdem die Existenz der Mail, statt wie ein unbekanntes Objekt mit 404 zu antworten. Betreff oder Absender werden dabei nicht zusätzlich zurückgegeben.

**Grenze des Nachweises:** Der Projektwechsel wurde gezielt auf Datenbankebene simuliert, wie für diesen Prüffall erforderlich. Es wurde kein öffentlich erreichbarer Ticketverschiebe-Endpunkt in diesem Stand gefunden. Dies ist eine fehlende Absicherung gegen veraltete Zuordnungen, kein Nachweis eines heute über die Oberfläche auslösbaren Ticketwechsels.

**Vorschlag:** Nach dem Sperren die aktuelle Ticketzuordnung über den Ticketport prüfen und nur bei passendem Unternehmen/Projekt sowie aktuellen Leserechten markieren; sonst dieselbe 404-Antwort wie bei unbekannter Mail. Die Prüfung gegen gleichzeitigen Wechsel absichern und dabei die bestehenden Sperrfolgen von Anhängen und Ticketänderung berücksichtigen. Einen Test mit veralteter Mailprojekt-Zuordnung ergänzen. Nicht lediglich die Liste korrigieren: Diese verweigert den Zugriff bereits.

### F2 [P2] – Im Ticket verschwindet die Konfliktmeldung; der Tastaturfokus geht verloren

**Datei/Zeile:** `frontend/src/pages/TicketHelpdesk.tsx:193–204,225–246`; `frontend/src/pages/InboxPage.tsx:281–288,295–304`; vorhandener Konflikttest nur für die Liste: `frontend/tests/browser/helpdesk.test.mjs:546–559`.

`AnswerSeen` speichert die Fehlermeldung lokal und ruft anschließend immer `onChanged()` auf. Nach dem Neuladen ist eine inzwischen von jemand anderem gesehene Mail nicht mehr `newAnswer`. Dadurch wird `AnswerSeen` einschließlich seiner Meldung entfernt. Die aktive Schaltfläche verschwindet ebenfalls; ein anschließendes Fokusziel fehlt. Auch beim erfolgreichen Markieren in der Liste wird die fokussierte Zeile entfernt.

**Reproduktion:** Ticket mit neuer Antwort öffnen. Im Testserver die Antwort als durch eine zweite Person gesehen setzen und ihre Version erhöhen. Die zunächst angezeigte Schaltfläche „Gesehen“ per Enter aktivieren. Der Aufruf ergibt den Versionskonflikt; nach dem Neuladen existiert der Text „Diese Antwort hat inzwischen jemand anderes bearbeitet. Die Liste ist neu geladen.“ **nicht mehr im DOM**. `document.activeElement` ist `BODY`. Dasselbe Fokusziel wurde nach erfolgreichem Markieren der letzten Antwort in der Liste gemessen.

**Auswirkung:** Im Ticket fehlt die angeforderte Erklärung des Konflikts. Tastaturnutzer verlieren nach der Aktion ihre sichtbare Position; beim Verschwinden der letzten Listenzeile fehlt zudem eine Bestätigung des Erfolgs. Die vorhandenen Tests prüfen den Konflikt in der Prüfwarteschlange, nicht im Ticket und nicht die Fokusführung.

**Vorschlag:** Meldung und Fokussteuerung in einem übergeordneten, nach dem Neuladen bestehen bleibenden Bereich halten. Nach Markieren den nächsten Eintrag beziehungsweise eine fokussierbare Statusmeldung oder Überschrift fokussieren. Den Ticketkonflikt sowie erfolgreiche Aktionen in Ticket und Liste mit Tastatur und `document.activeElement` prüfen.

### F3 [P2] – Ein Fehler beim Abruf neuer Antworten sieht wie eine leere Liste aus

**Datei/Zeile:** `frontend/src/pages/InboxPage.tsx:272–290`, insbesondere `:289`.

`NewAnswers` gibt für jeden Zustand außer `ok` lediglich `null` zurück. Damit verschwinden bei 401/403/503 oder einem Netzfehler Überschrift, Liste und jede Fehlermeldung. Die übrige Prüfwarteschlange kann weiterhin normal geladen sein.

**Reproduktion:** Nur `GET /api/v1/helpdesk/answers` mit HTTP 503 `helpdesk.busy` beantworten, während Sitzung und übrige Endpunkte funktionieren. Die Browserprobe bestätigt: kein Abschnitt `new-answers`, keine Status- oder Fehlermeldung. Das Ergebnis ist für die Person nicht von „keine neue Kundenantwort vorhanden“ unterscheidbar.

**Auswirkung:** Gerade die durch H7 beauftragte Sichtbarkeit kann unbemerkt ausfallen. Wer die Prüfwarteschlange öffnet, erhält keinen Hinweis auf die unvollständige Sicht und keinen gezielten Wiederholungsweg.

**Vorschlag:** Fehlerzustand ausdrücklich anzeigen, einschließlich verständlicher Meldung und erneuter Abrufmöglichkeit. Erfolgreich leer, noch ladend und fehlgeschlagen getrennt behandeln. Einen Browserfall mit unabhängig fehlschlagendem Antwortabruf ergänzen.

## Hinweise

### H1 – Der Browser-Testersatz bildet relevante Servergrenzen nicht ab

**Datei/Zeile:** `frontend/tests/browser/backend.mjs:838–854`; Serververgleich: `AnswerService.java:27,54–56` und `AnswerController.java:49–51`.

**Beobachtung/Auswirkung:** Der Testersatz begrenzt die Liste nicht auf 100, prüft die aktuelle Ticketprojekt-Zuordnung nicht und sortiert bei gleichem `joinedAt` nicht zusätzlich nach Kennung. Sein Markierweg prüft Existenz/Rechte vor `If-Match`; der echte Controller prüft den Versionskopf zuvor. So kann etwa eine unbekannte Mail ohne Kopf im Test 404, am echten Server aber 428 ergeben. Die normalen Zustände, Versionserhöhung sowie 409/412 werden passend nachgebildet; die fehlenden Grenzen können aber Fehlannahmen in zukünftigen Tests verdecken. Der Testersatz enthält kein Audit.

**Vorschlag:** Grenze, Reihenfolge und Ticketzuordnung an den echten Vertrag angleichen und Grenzfälle ausdrücklich testen. Die Korrektur aus F1 auch im Testersatz berücksichtigen. Audit und Sperren weiterhin am echten Backend prüfen.

### H2 – Ohne aktuellen Ticketkontakt fehlt „Gesehen“ im Ticket trotz vorhandener Markierrechte

**Datei/Zeile:** `backend/src/main/java/taskmanager/helpdesk/internal/TicketCustomerService.java:90–94`; `frontend/src/pages/TicketHelpdesk.tsx:204`; `ReplyService.java:365–376`.

**Beobachtung/Auswirkung:** `replyAllowed` verlangt zusätzlich einen tatsächlich vorhandenen Kontakt. Das ist für das Verfassen einer Mail sinnvoll, der Markierendpunkt verlangt hingegen nur die Antwortrechte und Leseregeln. Wird der Ticketkontakt entfernt, bleibt die neue Mail hervorgehoben, die Schaltfläche im Ticket fehlt jedoch. Dies wurde im Browser mit vorhandenem `newAnswer` und null Kontakt reproduziert. In der Liste kann dieselbe Person weiterhin markieren.

**Vorschlag:** Die Fähigkeit zum Markieren von der Fähigkeit zum Verfassen einer Mail an den aktuellen Kontakt unterscheiden. Im Ticket dieselbe Rechteentscheidung wie beim Markierendpunkt anbieten. Alternativ diese zusätzliche fachliche Einschränkung ausdrücklich entscheiden und dokumentieren.

### H3 – Vertrag und Übergabetexte sind an einzelnen Stellen unvollständig oder veraltet

**Datei/Zeile:** `contracts/helpdesk.openapi.yaml:450–495`; `backend/src/main/java/taskmanager/helpdesk/web/Requests.java:79–88`; `TODO.md:64,77`; `DECISIONS.md:188`; `backend/README.md:484–494`.

**Beobachtung/Auswirkung:** Beide neuen Routen verwenden die gemeinsame Behandlung für Sperrprobleme und können damit 503 `helpdesk.busy` mit `Retry-After` liefern; die Operationen dokumentieren 503 nicht. Der Routenabgleich prüft diese Antwortsemantik nicht. TODO nennt im Arbeitsstand noch die voraussichtliche V0029 und in den Nachweisen 28 Routen; aktuell sind es V0052 und 36 Routen. DEC-097 nennt bei den alten unmarkierten Mails noch „vor V0029“, obwohl der tatsächliche Stichtag V0052 ist.

Die Beschränkung auf **Mails** folgt DEC-097 und README korrekt. Portalbeiträge werden nicht als `helpdesk_message` angelegt und zählen hier deshalb nicht. Diese Grenze ist im PR ausdrücklich genannt, im README aber nur aus „Eine Mail“ abzuleiten.

**Vorschlag:** 503 und Retry-After bei den neuen Operationen ergänzen, Übergabezahlen und Migrationsstichtag korrigieren und die Portalausnahme im README ausdrücklich nennen.

### H4 – Kein eigener Upgrade- oder gehaltener Sperrtest für TASK-067 im Repository

**Datei/Zeile:** `backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java:1228–1294`; `backend/src/main/resources/db/migration/V0052__helpdesk_new_answers.sql:4–10`.

**Beobachtung/Auswirkung:** Der neue API-Test prüft viele Rechte und Zustände, behandelt zwei Personen aber nacheinander. Es gibt keinen spezifischen Upgrade-Test mit vor V0052 angehängten Mails und keinen gehaltenen Sperrtest für „seen gegen attach“ oder Mitgliedschaftsentzug. Die unabhängigen Zusatzproben dieses Reviews decken tatsächliches gleichzeitiges Markieren und ein Rennen mit Anhängen ab; sie ersetzen keine dauerhafte Regression im Repository.

**Vorschlag:** Für die Nachbesserung gezielte Regressionen hinzufügen: alter Datenbestand bleibt unmarkiert, zwei Markierungen erzeugen genau ein Audit, Anhängen/Markieren erzeugt keinen inkonsistenten Zustand, Projektzuordnung und Mitgliedschaft werden unter gehaltenen Änderungen korrekt geprüft.

## Ergebnisse der geforderten Prüfpunkte

1. **Zustand „neu“ und Migration:** `repository.joined` hat genau einen Produktionsaufrufer, `TicketOpener.java:109`. Abruf (`MailIntake.java:267`) und manuelles Anhängen (`ReviewService.java:164`) benutzen beide diesen Weg. `processed` und `joined` liegen in derselben Transaktion; die Version wird bereits durch `processed` erhöht. `open` setzt `joined_at` nicht. V0052 fügt nullable Spalten ohne Backfill hinzu; bestehende Mails bleiben daher mit null `joined_at` unmarkiert. Eine tatsächlich per Mail eröffnete Ticketmail hatte auch in der Zusatzprobe null für alle drei Spalten. Die Check-Bedingung erzwingt genau die behauptete Paarigkeit: Zeitpunkt und Person sind gemeinsam gesetzt oder gemeinsam null; ein gesetzter Gesehen-Zeitpunkt verlangt `joined_at`. Drei direkte ungültige Updates wurden jeweils abgelehnt. Die Bedingung prüft weder zeitliche Reihenfolge noch Ticketzuordnung oder Mailstatus; das ist keine weitergehende Datenbankgarantie.
2. **Rechte und Informationsumfang:** Die gemeinsame Prüfung in `ReplyService.requireReply:365–376` verlangt Mitgliedschaft, `ticket.read`, Leseregeln für `mail`/`contact` sowie `helpdesk.reply`; die Liste verwendet dieselben Entscheidungen über `mayReply`. Ohne Antwortrecht bleibt die Liste leer, Markieren ergibt 403; ohne Ticketzugriff ergibt es 404. Mailtext wird in der Liste nicht ausgegeben, Betreff und Absender gehören zur lesbaren Mail. Nach regulärem Mitgliedschaftsentzug lieferte die echte Zusatzprobe eine leere Liste und 404 beim Markieren. Der Ticketansicht werden Mail und neues Kennzeichen nur mit der Leseregel für `mail` geliefert. Die Abweichung nach Ticketprojektwechsel ist F1.
3. **Nebenläufigkeit und Version:** Die Mailzeile wird vor Versions- und Neuprüfung mit `FOR UPDATE` gesperrt. Zwei echte gleichzeitige Aufrufe mit identischem `If-Match` ergaben **200 und 412**, genau eine Versionserhöhung und einen `answer.seen`-Eintrag. Mit aktueller Version nach bereits erfolgter Markierung ergab sich 409, ohne Kopf 428. Im gleichzeitigen Anhängen/Markieren ergab sich **attach 200 / seen 412**; die Mail blieb korrekt neu, mit gesetztem `joined_at` und null `seen_at`. Bei Markieren vor dem Anhängen ist nach dem Code 409 vorgesehen. Letztere Reihenfolge wurde nicht mit einer eigenen gehaltenen Sperre erzwungen.
4. **Sperrfolgen:** `seen` nimmt Rechte-/Projektsperren und danach die Mailzeile. Es sperrt weder Ticketkunde noch Ticket noch ausgehende Antwort. Der manuelle Anhangspfad nimmt Rechte/Projekt, Mail, Ticketkunden-Advisory-Lock und Ticketsperre; der automatische Abruf fügt seine neue Mail zunächst selbst ein und nimmt dann Ticketkunde/Kontakt/Ticket. Aus dem neuen Markierpfad wurde kein zusätzlicher zyklischer Sperrpfad festgestellt; im ausgeführten Rennen trat keine Verklemmung auf. Dies ist kein formaler Nachweis aller möglichen Parallelabläufe. Bei F1 die gemeinsame Reihenfolge beibehalten; eine zusätzliche Ticketsperre nicht ungeprüft vor die Mail ziehen. Sperrfehler werden gemeinsam in 503 mit Wiederholhinweis übersetzt.
5. **Zusammenspiel mit TASK-068 und Portal:** `TicketOpener.java:107–110` schreibt Verarbeitung und `joined_at` vor `CustomerAnswered`. Der SLA-Empfänger `TicketSla.java:171–176` läuft synchron als `@EventListener` mit zwingend vorhandener Transaktion. Damit sieht er den bereits zugeordneten Zustand; ein Fehler rollt auch das Anhängen und den neuen Hinweis zurück. Das Ereignis verwendet weiterhin die Empfangszeit. Es wird weder ein Statuswechsel noch eine Zuständigkeitsänderung eingeführt. Portalantworten bleiben korrekt außerhalb dieser Mailfunktion; ihr vorhandenes SLA-Ereignis ist davon unabhängig. Dokumentationshinweis H3.
6. **Audit und Protokoll:** `AnswerService.java:76–79` und `HelpdeskRepository.java:658–663` schreiben `answer.seen` mit Mailkennung als Ziel, Ticketkennung im JSON-Detail, handelnder Person und Zeitpunkt in derselben Transaktion. Die Zusatzprobe bestätigte einen einzigen Eintrag bei zwei konkurrierenden Personen. Kein Betreff, Absender, Mailtext, Passwort oder Mailservertext wird von diesem neuen Audit-/Markiercode protokolliert. Die IDs sind die notwendige Objektzuordnung, keine zusätzlichen Mailinhalte.
7. **Oberfläche, Vertrag und Umfang:** Beide Sprachdateien enthalten die neuen Texte. Links, Details und Schaltflächen sind per Tastatur erreichbar; neue Mails sind aufgeklappt und haben zusätzlich zur Farbe eine Textmarkierung. Vier Zusatzproben mit 320 px (Deutsch/Englisch × hell/dunkel) ergaben jeweils 320 px Dokumentbreite und 22 geprüfte sichtbare Texte ohne Kontrastfehler; zwei der erzeugten Screenshots wurden zusätzlich visuell betrachtet. Konflikt/Fokus und fehlender Ladefehler sind F2/F3. Die generierten Typen entsprechen dem Vertrag; `npm run verify` erzeugte keine Änderungen. `HelpdeskContractTest` vergleicht 36 Routen. V0052 ist in beiden Java-Migrationsprüfungen und `lifecycle-check.sh` konsistent enthalten: 49 Migrationen bis V0052 mit den bestehenden Nummernlücken. Keine Ausweitung auf Benachrichtigungen, Wiederöffnen, neuen SLA-Funktionsumfang oder „gesehen je Person“ gefunden.

## Ausgeführte Prüfungen

Windows-Umgebung: Python 3.12 mit `PYTHONUTF8=1`, Node 24.22.0 / npm 11.19.1, Java 25.0.4.1+1, eigene PostgreSQL **16.15**. Für `python3` wurde der vorhandene Python-Interpreter verwendet, für `./mvnw` der Windows-Wrapper `mvnw.cmd`. Die Datenbankparameter wurden über die dokumentierten `taskmanager.test.*`-Eigenschaften gesetzt. Die Zusatzproben verwenden denselben Anwendungsstand und frische synthetische Datenbanken.

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Erfolgreich; nach Berichtserstellung erneut geprüft. |
| `python3 scripts/task_board.py --check` | Erfolgreich, Übersicht aktuell. |
| `python3 -m unittest discover -s tests -v` | 38 Tests: 37 erfolgreich, ein nur unter Unix ausführbarer Zeitzonentest unter Windows übersprungen. |
| Backend `./mvnw verify`, PostgreSQL 16.15 | **BUILD SUCCESS: 517 Tests, 0 Fehlschläge, 0 Fehler, 0 übersprungen**, Laufzeit 14:37 Minuten. Darunter HelpdeskApiTest 44, HelpdeskContractTest 1, ApplicationLifecycleTest 3 und DatabaseFoundationTest 4, jeweils erfolgreich. |
| Frontend `npm ci` | Erfolgreich. |
| Frontend `npm run verify` | Erfolgreich: Generierung, Typecheck, **79/79 Tests**, Produktionsbuild. Vite meldete einen großen Bundle-Chunk. |
| Frontend `npm run test:browser` | Erfolgreich: **183/183 Tests**, keine übersprungenen Tests. |
| Zusätzliche echte API-/Datenbankproben | Erfolgreich ausgeführt: paralleles Markieren, Audit, 409/428, simuliertes Verschieben, Mitgliedschaftsentzug, Anhängen gegen Markieren und drei ungültige Zustandskombinationen. Ergebnisse oben; F1 reproduziert. |
| Zusätzliche Browserproben | Erfolgreich ausgeführt: Fokus nach Markieren, Ticketkonflikt, isolierter HTTP-503-Abruf, Ticket ohne Kontakt sowie vier Sprach-/Darstellungsvarianten bei 320 px. F2/F3 und H2 reproduziert. |
| `git diff --check` und Unverändertheit | Sauber; ausschließlich dieser Bericht kommt im Repository hinzu. |

**Nicht ausgeführt:** PostgreSQL-18.6-Testcontainer und das separate Unix-Prozessskript `lifecycle-check.sh` mit seinem SIGTERM-Ablauf. Dessen Migrationszahlen wurden statisch geprüft; die Java-Lebenszyklusprüfung lief gegen PostgreSQL 16 erfolgreich. Kein zusätzlicher vollständiger Live-Browserlauf gegen das echte Backend; die regulären Browserprüfungen verwenden wie vorgesehen den Testersatz. Kein spezifischer historischer Upgrade-Test mit Datenbestand vor V0052; Grenzen und Vorschlag in H4.

## Abnahmeempfehlung

**Nachbessern vor der Abnahme.** F1, F2 und F3 beheben und mit gezielten Gegenprüfungen absichern. Die grünen bestehenden Tests bestätigen die vorgesehenen Normalabläufe, decken die reproduzierten Fehler aber nicht ab. Die Markierung bleibt erwartungsgemäß teamweit; ein automatisches Wiederöffnen oder eine Benachrichtigung ist für diese Abnahme nicht nachzufordern.
