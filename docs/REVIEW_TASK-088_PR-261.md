# Unabhängige Nachprüfung TASK-088 / PR #261

**Empfehlung: TASK-067 und TASK-088 nachbessern, noch nicht abnehmen.** Die Ticketprüfung und die Meldungen samt Fokusführung sind wirksam korrigiert. Ein Teil von F3 bleibt offen: Eine noch ladende Antwortliste sieht weiterhin exakt wie eine erfolgreich leere Liste aus (R1). Die neuen Tests sichern außerdem die in H4 beschriebenen Upgrade- und Sperrfälle nur teilweise dauerhaft ab (R2).

## Prüfstand

- Unabhängige zweite KI: Codex; Sitzung codex-20261104-newanswersfix-review-01; Prüfung am **8. November 2026**.
- [PR #261](https://github.com/1uedev/task-manager/pull/261), Branch claude/project-thread-fde105, Head 96a3e9257d7e14c17ca638747f2bee82e56aa044. Lokal geprüft: dessen Merge auf main, **1a75e5d0dc4a7e155c56b7df6ef5f29188c35a87** vom 4. November. Dieser Stand ist Vorfahr des beim Abruf aktuellen main; spätere Aufgaben wurden nicht eingemischt.
- Zuerst gelesen: AGENTS.md, TASK-067 und TASK-088 in TODO.md, DEC-097 und DEC-118, backend/README.md „Neue Kundenantworten (TASK-067)“ und docs/REVIEW_TASK-067_PR-221.md. Ergänzend README, DONE, Arbeitsablauf, relevante Anforderungen/Roadmap, Vertrag, Implementierung, Aufrufer und Tests.
- Eigener Checkout. Keine Änderung an Anwendungs- oder Testcode, Vertrag, Migrationen oder Aufgabenstatus. Im Checkout kommt ausschließlich dieser Bericht hinzu. Zusatzprogramme und Logs liegen außerhalb des Checkouts; alle Daten und Konten sind synthetisch.
- P1 = dringend, P2 = regulär zu beheben, P3 = gering. Kein P1-Befund. Die technische Empfehlung ersetzt Holgers fachliche Abnahme nicht.

## Befunde

### R1 [P2] – F3: ladend und erfolgreich leer bleiben ununterscheidbar

**Datei/Zeile:** frontend/src/pages/InboxPage.tsx:293 und :303; Anforderung TODO.md:177; Gegenprüfung frontend/tests/browser/helpdesk.test.mjs:597–603.

NewAnswers liefert sowohl für answers.state === 'loading' als auch für eine erfolgreich leere Liste ohne vorherige Meldung null. Die übrige Prüfwarteschlange kann währenddessen bereits vollständig geladen sein. Überschrift und Ladeanzeige für den Antwortabruf fehlen.

**Reproduktion:** Nur GET /api/v1/helpdesk/answers im Browser zurückhalten, alle anderen Anfragen normal beantworten. Nach dem Laden der übrigen Seite den sichtbaren Haupttext und die Zahl der Abschnitte mit data-testid="new-answers" erfassen. Danach den Abruf mit 200 [] abschließen und erneut erfassen. Ergebnis in **Deutsch und Englisch**: jeweils **0 Abschnitte**, der gesamte sichtbare Haupttext ist vor und nach der Antwort **identisch**. Die Seite zeigt bereits „Keine Mail wartet auf Prüfung.“ beziehungsweise „No mail waits for review.“, ohne den noch unvollständigen Antwortabruf kenntlich zu machen.

**Auswirkung:** Die Person erkennt nicht, ob keine neuen Kundenantworten vorliegen oder deren Abruf noch aussteht. Die ausdrücklich beauftragte Unterscheidung zwischen leer, ladend und fehlgeschlagen ist nur für den Fehlerzustand umgesetzt. Der neue Browsertest prüft Fehler und Wiederholung, hält aber keinen Abruf im Ladezustand fest.

**Vorschlag:** Beim Erstladen den Abschnitt mit Überschrift und übersetzter Ladeanzeige zeigen; erfolgreich leer muss davon unterscheidbar sein. Einen Browserfall mit zurückgehaltener Antwort ergänzen, der zuerst ladend und danach leer beziehungsweise gefüllt prüft. Den funktionierenden Fehlerbereich beibehalten.

### R2 [P3] – H4: dauerhafte Upgrade- und Sperrregressionen bleiben unvollständig

**Datei/Zeile:** backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java:1327–1347,1374–1390,1398–1419,1427–1441; Übergabe TODO.md:189.

Die neuen Tests prüfen reale API-Antworten und Datenbankzustände, aber nicht alle angemahnten Abläufe:

- **anAnswerFromBeforeTheMigrationIsNeverNew** erzeugt die Antwort nach der bereits ausgeführten Migration und setzt danach joined_at = null. Er prüft Nullzustand und Check-Bedingung, aber keinen Altbestand beim Upgrade über V0052. Ein künftig fälschlich ergänztes Backfill würde der Test nicht erkennen, weil er das Ergebnis selbst wieder auf null setzt.
- **anAnswerCanBeMarkedOnlyAfterItJoinedItsTicket** führt Markieren, Anhängen und erneutes Markieren vollständig nacheinander aus. Er prüft korrekt 409/412/200, aber keinen Ablauf unter gehaltenen Sperren. Eine falsche Sperrfolge oder fehlende Serialisierung kann dabei unentdeckt bleiben.
- **twoPeopleWhoMarkAnAnswerAtOnceLeaveOneMarking** startet zwei Futures ohne Barriere. Er kann auch bei vollständig nacheinander ausgeführten Requests bestehen. Beim identischen ursprünglichen If-Match erlaubt er 409 oder 412; mit korrekter Versionierung muss der Verlierer hier 412 liefern. Genau eine Versionserhöhung wird nicht geprüft.
- **anAnswerWhoseTicketLeftTheProjectIsUnknown** sichert den bereits abgeschlossenen Projektwechsel gut ab, einschließlich unveränderter Version und fehlender Markierung/Audit. Den Wechsel zwischen erster Prüfung und Ticketsperre erzwingt er nicht. Das Entfernen der zweiten inPlace-Prüfung würde dieser Test allein nicht entdecken.

**Auswirkung:** Nützliche Zustandsregressionen ersetzen die in H4 angemahnten Upgrade- und gehaltenen Sperrtests nicht vollständig. Dies ist eine Lücke der dauerhaften Absicherung, kein nachgewiesener weiterer Fehler des aktuellen Markiercodes. Die unabhängigen Zusatzproben dieses Reviews waren erfolgreich, gehören aber nicht zur CI.

**Vorschlag:** Altbestand vor V0052 anlegen und danach migrieren. Beide Reihenfolgen Anhängen/Markieren und den Wechsel während der Ticketsperre mit überprüften Wartebarrieren dauerhaft testen. Beim parallelen Markieren 200/412, eine Versionserhöhung und genau ein zum erfolgreichen Aufruf passendes Audit prüfen. Die bereits in derselben Klasse verwendeten Hilfen mit pg_blocking_pids eignen sich dafür.

## Hinweise

### H1 – Vollständiger Browserlauf nicht durchgehend grün

**Datei/Zeile:** frontend/tests/browser/tags.test.mjs:60 und :128; diese Datei wurde durch PR #261 nicht geändert.

**Beobachtung:** Der vollständige Lauf hatte **193/194** erfolgreiche Tests; ein Tagfall wartete vergeblich auf „Kunde Nord bearbeiten“. Beim Wiederholen der Tagdatei bestand dieser Fall, dafür scheiterte die dunkle Darstellung beim Warten auf „Dringend“ (**7/8**). Anschließend liefen genau diese beiden Fälle gezielt gemeinsam **2/2** grün. Die Helpdeskdatei einschließlich der neuen Fälle war im vollständigen Lauf **21/21** grün.

**Auswirkung:** Ein vollständig grüner Gesamtlauf lässt sich für diese Sitzung nicht behaupten. Die wechselnden Timeouts belegen noch keine durch TASK-088 verursachte Regression; ihre Ursache wurde nicht abschließend geklärt.

**Vorschlag:** Bei der Übergabe berücksichtigen und bei erneutem Auftreten getrennt im Tagbereich eingrenzen. Daraus folgt keine Erweiterung des Reparaturauftrags von TASK-088.

## Ergebnisse zu den Prüfpunkten

### F1 – Ticketzuordnung und Sperrfolge

**Wirksam behoben.** AnswerService.java:69–83,97–100 prüft nach der Mailzeilensperre Unternehmen und Projekt des Tickets, nimmt dessen geteilte Sperre und prüft dieselbe Zuordnung erneut. Erst danach folgen Version, Neuzustand und Schreiben samt Audit (:84–93). JdbcTicketAccess.java:125–126 verwendet TicketRepository.java:85–86, also SELECT … FOR SHARE. Bei READ COMMITTED liest die zweite Auflösung die nach dem Warten gültige Zuordnung; die gehaltene Ticketsperre verhindert anschließend einen weiteren Wechsel bis zum Commit.

Zusätzliche echte API-/Datenbankproben bestätigten:

| Gehaltener oder gezielter Ablauf | Ergebnis |
| --- | --- |
| Noch nicht bestätigter Projektwechsel hält die Ticketzeile; Markieren sieht zuerst das alte Projekt und wartet nachweislich auf die Ticketsperre; danach wird der Wechsel bestätigt | **404 helpdesk.message_not_found**, seen_at unverändert null, Version unverändert, kein answer.seen-Audit |
| Abweichende Unternehmenskennung des Tickets bei gleichem Mailprojekt | **404**, unveränderte Version, kein Markieraudit |
| Zwei Markierungen warten auf dieselbe Mail; beide Wartezustände über die Blockierungskette nachgewiesen | **200/412**, genau eine Versionserhöhung und ein answer.seen |
| Anhängen wartet zuerst auf die gehaltene Mail, Markieren dahinter | **attach 200 / seen 412**; joined_at gesetzt, seen_at/seen_by null |
| Markieren wartet zuerst auf die gehaltene Mail, Anhängen dahinter | **seen 409 / attach 200**; danach ebenfalls korrekt neu |
| Mail ohne Ticket, aktuelle beziehungsweise veraltete Version | **409 / 412** |
| Projektzeile bis zum Sperrtimeout gehalten, beide neuen Routen aufgerufen | jeweils **503 helpdesk.busy mit Retry-After: 1** |

Die Zuordnungswechsel wurden gezielt auf Datenbankebene simuliert; ein öffentlicher Ticketverschiebe-Endpunkt wird damit nicht behauptet.

**Sperrfolge:** Rechte einschließlich Identitätsprüfung, Projektzeile, Mail, Ticket FOR SHARE. ReviewService.java:142–164 und TicketOpener.java:93–109 nehmen die Mail vor Ticketkundensperre und Ticketsperre. MailIntake.java:246–267 fügt die Mail selbst ein, bevor es Ticketkunde/Kontakt/Ticket nimmt; andere Transaktionen können diese noch nicht bestätigte neue Mail nicht zum Markieren sperren. ReplyService.java:148–159 und PortalConversation.java:197,320–328 halten geteilte Ticketsperren und nehmen keine exklusive Sperre einer vorhandenen Eingangsmail. Der Markierpfad nimmt nach dem Ticket keine weiteren Projekt-, Kunden-, Kontakt- oder Antwortsperren. Es wurde kein zusätzlicher zyklischer Sperrpfad zu Abruf, Anhängen, Antworten oder Portal gefunden. In den gehaltenen Rennen trat keine Verklemmung auf. Dies ist kein formaler Nachweis sämtlicher möglichen Parallelabläufe.

### F2 – Meldung, Fokus und Sprachen

**Behoben in den beauftragten Erfolgs- und Konfliktabläufen.** InboxPage.tsx:283–307 und TicketHelpdesk.tsx:178–215,234–255 halten Meldung und Überschrift außerhalb der verschwindenden Schaltfläche. useLoad behält beim Aktualisieren einen vorhandenen erfolgreichen Wert (frontend/src/app/hooks.ts:19); dadurch bleibt der übergeordnete Bereich erhalten.

Acht zusätzliche Browserfälle (Deutsch/Englisch × Liste/Ticket × Erfolg/Versionskonflikt) wurden per Enter ausgelöst und nach dem folgenden Datenabruf geprüft. Alle behielten die passende Meldung. document.activeElement war H2 „Neue Kundenantworten“/„New customer answers“ beziehungsweise H3 „E-Mail Verlauf“/„Email history“. Der Ticketkonflikt nennt das neu geladene Ticket. Die Proben liefen zugleich ohne aktuellen Ticketkontakt. „Neuladen“ bezeichnet hier den von der Aktion ausgelösten Datenabruf; eine Speicherung der Meldung über einen Browserneustart ist nicht vereinbart.

### F3 – Ladefehler und Wiederholung

**Teilweise behoben; R1 bleibt.** InboxPage.tsx:295–300 zeigt bei Ladefehlern Überschrift, Fehler und „Erneut versuchen“/„Try again“. Gezielte 503-Browserproben bestätigten Fehlertext und erfolgreiche Wiederholung in beiden Sprachen. Ladend und erfolgreich leer bleiben identisch unsichtbar.

### H1 – Testersatz

**Die angefragten Abweichungen sind korrigiert.** frontend/tests/browser/backend.mjs:860–889 prüft If-Match vor der Objektsuche, sortiert nach joinedAt und Kennung absteigend, begrenzt vor dem Ticketfilter auf 100 und prüft die Ticketzuordnung auch beim Markieren. Das entspricht HelpdeskRepository.java:405–411, AnswerService.java:49–57 und dem Controller.

Eine zusätzliche direkte Probe des unveränderten Testersatzes bestätigte 428 vor 404, die Kennungssortierung beidseits des höchsten UUID-Bits und die Projektprüfung. Bei 100 neueren Mails mit fremdem Ticketprojekt plus einer älteren gültigen Mail blieb das Ergebnis wie beim Server leer: Die Grenze greift vor dem Ticketfilter. Ohne Ticket ergab sich 409 beziehungsweise 412. Audit und Sperren wurden am echten Backend geprüft.

### H2 – answerSeenAllowed ohne Kontakt

**Behoben.** TicketCustomerService.java:64–65,92–95 liefert answerSeenAllowed aus derselben Rechteentscheidung wie beim Antworten, ohne einen Kontakt zu verlangen. TicketCustomerController.java:72–81, contracts/helpdesk.openapi.yaml:1296–1329 und frontend/src/api/generated/helpdesk.d.ts:989 reichen das Pflichtfeld durch; TicketHelpdesk.tsx:209 verwendet es. Der echte API-Test prüft auch den Leser ohne Antwortrecht. Regulärer Browserfall und Zusatzfälle ohne Kontakt waren erfolgreich.

### H3 – Vertrag und Texte

**Behoben.** Beide Routen dokumentieren 503 (contracts/helpdesk.openapi.yaml:468,495); die gemeinsame Problemantwort beschreibt Retry-After nur für 503 und nennt helpdesk.answer_not_new (:953–957). Die echten Timeoutproben bestätigten Status und Header; Requests.java:80 legt dafür helpdesk.busy fest. Nach npm run verify sind die generierten Typen unverändert.

TASK-067 nennt nun V0052 und 36 Routen (TODO.md:63,76); DEC-097 nennt für den Altbestand V0052 (DECISIONS.md:188). Der Helpdesk-Vertragstest zählt tatsächlich 36 Routen. Der README-Abschnitt nennt Portalbeiträge ausdrücklich außerhalb dieser Mailfunktion.

### H4 – Aussagekraft der neuen Tests

Die Tests für abgeschlossenen Projektwechsel und Ticket ohne Kontakt prüfen die behaupteten Zustände nachvollziehbar. Auch Nullzustand des Altbestands, ein Audit bei zwei Futures und sequenzielle 409/412/200-Abläufe werden geprüft. **Ein tatsächliches Upgrade und deterministisch gehaltene Rennen sind damit weiterhin nicht dauerhaft abgesichert; siehe R2.** Erfolgreiche Zusatzproben ersetzen diese Repositoryregressionen nicht.

### Auftragsgrenze

Der Vergleich des Merge-Commits mit seinem ersten Elternstand umfasst 17 Dateien und ist dem Auftrag zuzuordnen: Markierprüfung, Ansichtsfähigkeit, Oberfläche/Texte, Vertrag/Typen, Testersatz/Tests und zugehörige Dokumentation. **Keine Migration wurde hinzugefügt oder geändert.** Keine Erweiterung um Wiederöffnen, Benachrichtigungen, SLA oder „gesehen je Person“ gefunden.

## Ausgeführte Prüfungen

Windows, Python 3.12 mit PYTHONUTF8=1, Node 24.22.0 / npm 11.19.1, Java 25.0.4.1+1, eigener PostgreSQL **16.15**. Für python3 wurde der vorhandene echte Python-Interpreter verwendet, für ./mvnw der Windows-Wrapper mvnw.cmd. Die dokumentierten taskmanager.test.*-Parameter richteten den Backendlauf auf den isolierten Testserver.

| Prüfung | Ergebnis |
| --- | --- |
| python3 scripts/validate_repository.py | Erfolgreich, auch nach Berichtserstellung. |
| python3 scripts/task_board.py --check | Erfolgreich, Übersicht aktuell. |
| python3 -m unittest discover -s tests -v | **38 Tests**, 37 erfolgreich, 1 Unix-Zeitzonentest unter Windows übersprungen. |
| Backend ./mvnw verify, PostgreSQL 16.15 | **BUILD SUCCESS: 534 Tests, 0 Fehlschläge, 0 Fehler, 0 übersprungen**, 10:47 Minuten. Darunter HelpdeskApiTest 49, Vertragstest 1, ApplicationLifecycleTest 3 und DatabaseFoundationTest 4, jeweils erfolgreich. |
| Frontend npm ci | Erfolgreich. |
| Frontend npm run verify | Erfolgreich: Generierung, Typprüfung, **82/82 Tests**, Produktionsbuild. Bestehende Vite-Warnung zum großen Bundle. |
| Frontend npm run test:browser | **193/194 erfolgreich**, ein Tag-Timeout; Helpdesk **21/21**. Wiederholungen: Hinweis H1. |
| Zusätzliche echte API-/Datenbankproben | Erfolgreich: Wechsel zwischen Prüfung und Sperre, Unternehmensabweichung, gehaltene Markierungen, beide Reihenfolgen Anhängen/Markieren, 409/412 ohne Ticket, 503 mit Header an beiden Routen. |
| Zusätzliche Browserproben | Acht Erfolgs-/Konfliktfälle samt Tastaturfokus nach Datenabruf; Fehler/Wiederholung DE/EN; gehaltene Liste. **R1 in beiden Sprachen reproduziert.** |
| Zusätzliche Probe des Testersatzes | Erfolgreich: 428/404, Grenze vor Filter, Kennungssortierung, Ticketprojekt. |
| git diff --check und Unverändertheit | Sauber; nur dieser Bericht ist neu, alle zuvor vorhandenen Dateien unverändert. |

Erste Versuche stießen auf lokale Zugriffsgrenzen für Netzwerk/temporäre Dateien beziehungsweise unvollständige lokale Java-/Datenbankeinstellungen. Repositorytests und Paketinstallation wurden mit geeignetem Testverzeichnis beziehungsweise Zugriff erfolgreich wiederholt. Ein erster vollständiger Backendlauf mit leerem Datenbankpasswort endete mit **534 Tests, 416 Startfehlern** wegen der vorgeschriebenen nichtleeren Einstellung; der oben genannte abschließende Lauf verwendet einen synthetischen Wert. Diese Startfehler sind kein Befund im Produktcode.

**Nicht ausgeführt:** PostgreSQL-18.6-Testcontainer, das separate Unix-Skript lifecycle-check.sh mit SIGTERM, ein vollständiger Browserlauf gegen das echte Backend sowie ein historischer Upgrade-Lauf mit einer vor V0052 vorhandenen Antwortmail. Normale Browserprüfungen verwenden den vorgesehenen Testersatz; echte Rechte, Audit und Sperren wurden durch Backendtests und Zusatzproben geprüft. Die Java-Lebenszyklusprüfung lief im vollständigen Backendlauf.

## Abnahmeempfehlung

**TASK-067 und TASK-088 nachbessern.** Für den Abschluss die Unterscheidung von ladend und leer aus R1 ergänzen und die verbleibende dauerhafte Absicherung aus R2 vervollständigen. F1, F2, der Fehler-/Wiederholungsweg von F3 und die konkreten Korrekturen H1 bis H3 sind wirksam. Die Tag-Timeouts bleiben als gesonderte Testeinschränkung dokumentiert. Aufgabenstatus und fachliche Abnahme bleiben unverändert bei Holger.

## Ergänzende Verifikation am 8. November 2026

Der inzwischen vorliegende Bericht wurde nochmals vollständig mit dem unveränderten Merge-Stand und eigenen Wiederholungen abgeglichen. Empfehlung und Befunde **R1 [P2] / R2 [P3] bleiben bestehen**. Der beim Einstieg bereits laufende vollständige Backend-Verify-Prozess wurde bis zu seinem erfolgreichen Abschluss beobachtet; dessen Prozessparameter, 534 Testresultate und BUILD SUCCESS wurden geprüft. Er wurde nicht nochmals vollständig gestartet.

Die anschließenden Wiederholungen ergaben:

- Repositoryvalidator und Aufgabenübersicht erfolgreich; Python erneut **38 Tests, 37 erfolgreich, 1 plattformbedingt übersprungen**.
- npm ci und npm run verify erneut erfolgreich, **82/82 Tests**.
- Vollständiger Browserlauf erneut **193/194** erfolgreich: derselbe unveränderte Tagtest in tags.test.mjs:60 mit Timeout auf „Kunde Nord bearbeiten“; direkt anschließende isolierte Wiederholung **1/1 erfolgreich**. Dies ändert die Einschränkung aus H1 nicht.
- Die externen API-/Sperrproben wurden gegen eine neu angelegte eigene PostgreSQL-16.15-Instanz erneut erfolgreich ausgeführt: **404 ohne Markierung/Audit** beim Wechsel zwischen Prüfung und Sperre sowie bei abweichendem Unternehmen; **200/412 mit genau einer Versionserhöhung und einem Audit** beim gehaltenen Doppelmarkieren; **attach 200 / seen 412** beziehungsweise **seen 409 / attach 200** in beiden erzwungenen Reihenfolgen; danach weiterhin neue Antwort. Ohne Ticket **409/412**; beide Routen bei Sperrtimeout **503 mit Retry-After: 1**.
- Die acht Browserfälle für Erfolg/Konflikt in Liste/Ticket in Deutsch/Englisch bestätigten erneut Meldung und Fokus nach abgeschlossenem Datenabruf, auch ohne Kontakt. Fehler und Wiederholen funktionieren in beiden Sprachen. Der zurückgehaltene Listenabruf reproduziert R1 erneut: **0 Abschnitte beim Laden und bei leerem Ergebnis, identischer sichtbarer Haupttext**.
- Die direkte Probe des Testersatzes bestätigte erneut 428 vor 404, Grenze 100 vor dem Ticketfilter, Kennung als zweites Sortiermerkmal, Ticketprojekt und 409/412 ohne Ticket.

Ein erster Wiederholungsversuch der API-Probe traf auf die inzwischen beendete vorherige Prüfdatenbank. Die eigene Instanz beseitigte diese Umgebungsabhängigkeit. Eine erste Browser-Zusatzprobe wurde beendet, weil ihr Context-Routenhandler vom Page-Routenhandler des Testersatzes überlagert wurde; gewertet ist ausschließlich die erfolgreiche Wiederholung mit der bereits vorhandenen korrigierten Page-Route. Beides sind Grenzen der Prüfwerkzeuge, keine zusätzlichen Produktbefunde. Es wurden keine Anwendungs- oder Repositorytests verändert.
