# Nachprüfung von TASK-085 / PR #250

**Empfehlung: abnehmen.** Die beauftragten Produktkorrekturen zu F1, F2, H1 und H2 sowie zum verspätet geladenen Farbwert sind wirksam. Die in H3 beanstandeten Auftrittstests bestehen jetzt. Es wurde keine neue abnahmehindernde Produktabweichung gefunden.

**Die Browser-Gesamtsuite ist dennoch nicht zuverlässig parallel grün:** Der erste parallele und der serielle Lauf bestanden mit jeweils 181/181 Tests; die parallele Wiederholung bestand mit 180/181. Verbleibend war ein unveränderter Login-Test, der den Ladebildschirm zu früh auswertet (N1). Das ist von den behobenen Auftrittstests zu unterscheiden. Die Hinweise unten betreffen die Testzuverlässigkeit; sie begründen nach dieser Prüfung keine erneute Überarbeitung der beauftragten Produktkorrekturen. Die fachliche Abnahme bleibt bei Holger.

## Prüfstand und Umfang

- Sitzung: `codex-20261103-mailtemplate-review-02`, unabhängige zweite KI, 3. November 2026.
- Repository: https://github.com/1uedev/task-manager; Ergebnis-PR: https://github.com/1uedev/task-manager/pull/250.
- PR #250 ist gemergt. Geprüft und getestet wurde deshalb das beim Abruf aktuelle `main`, Commit `536aecb01ec614b362e1398580245d51204fc2bb`.
- TASK-085: ursprünglicher Branch `claude/project-thread-tzp0cw`, Commit `68771bf42f76d7f70d8ab40a7081821a3cf1a6d8`, Basis `86df764725e2158e772b51aa98076699295f455d`, Merge `010ad078df203de08d09ae5bbcea3b7e4fec6591`.
- Gelesen: `AGENTS.md`, `README.md`, `TODO.md` mit TASK-082 und TASK-085, `DONE.md`, `docs/AI_WORKFLOW.md`, relevante Anforderungen und Planung, DEC-112 und DEC-115 sowie `docs/REVIEW_TASK-082_PR-247.md`.
- Der Diff von TASK-085 umfasst 17 Dateien. Frontend, Verträge und Java-Produktcode stimmen zwischen dem PR-Ergebnis und dem geprüften main überein. Die inzwischen auf main vorhandene V0051 und der zusätzliche Backendtest gehören zu TASK-084, nicht zu TASK-085.
- Eigener Prüfklon. Ausschließlich dieser Bericht wurde im Repository ergänzt. Anwendungscode, Tests, Verträge, Migrationen und Aufgabenstände wurden nicht geändert; kein Commit, Push oder GitHub-Kommentar.

Alle folgenden Zeilennummern beziehen sich auf den genannten main-Stand.

## Befunde aus der Erstprüfung

### F1: Geprüften Entwurf bestätigen – behoben

**Datei/Zeile:** `frontend/src/pages/TicketHelpdesk.tsx:289`, `:329`, `:343`, `:347`, `:353`, `:384` und `:396`.

Die erfolgreiche Prüfung hält Postfach, Empfänger, getrimmten Betreff und Antworttext zusammen mit der gerenderten Vorschau fest. Die Bestätigung erhält ausschließlich `checked.draft`; sie liest diese vier Werte nicht erneut aus dem Formularzustand. Die Anzeige von Empfänger, Absender und Betreff verwendet ebenfalls den festgehaltenen Entwurf. Im Eingabezustand gibt es keine Schaltfläche „Jetzt senden“.

Jede Eingabe in einem der vier Felder, jeder Abbruch und jede neue Prüfung erhöht die Prüfrunde. Nach dem Warten wird die Runde vor der Übernahme sowohl eines Erfolgs als auch eines Fehlers verglichen. Abbrechen setzt außerdem den Beschäftigtzustand zurück. Beim Zurückgehen wird die geprüfte Fassung entfernt; vor dem Senden ist eine neue Prüfung erforderlich. Ein Ticketwechsel erzeugt über den Schlüssel in `frontend/src/app/App.tsx:293` eine neue Ticketansicht.

**Unabhängige Gegenprobe gegen den Produktionsbuild:** Die Vorschauanfrage wurde angehalten; der Test wartete auf ihr tatsächliches Ende und anschließend auf die Darstellung, nicht auf eine pauschale 300-ms-Frist.

| Ablauf | Beobachtung |
|---|---|
| Prüfung von Text A halten, währenddessen Text B eingeben | Alte Prüfung verworfen, keine Sendebestätigung angeboten. Erst nach neuer Prüfung wurde B bestätigt. |
| Während der gehaltenen Prüfung jeweils Betreff, Postfach oder Empfänger ändern | In allen drei Fällen alte Prüfung verworfen. Der bestätigte Request entsprach vollständig dem Request der neuen Prüfung. |
| Während der Prüfung abbrechen; alte Antwort noch bei geschlossenem Formular freigeben | Keine Prüfung und keine Sendung; auch nach anschließendem Öffnen keine alte Bestätigung. |
| Abbrechen, erneut öffnen, dann die alte Antwort freigeben | Alte Prüfung verworfen; keine Sendung. |
| Neue Prüfung von B zuerst beantworten, danach alten Erfolg von A freigeben | B blieb angezeigt und wurde bestätigt. |
| Neue Prüfung von B zuerst beantworten, danach alten Fehler freigeben | B und der Fokus blieben erhalten; B wurde bestätigt. |
| Per Tastatur zurückgehen, Text ändern, erneut prüfen und bestätigen | Neue Prüfung erforderlich; nur die erneut geprüfte Fassung wurde bestätigt. |

**Auswirkung:** Die in F1 reproduzierte Bestätigung einer ungeprüften Formularfassung ist in diesen Abläufen geschlossen. Kein weiterer Weg über die reguläre Oberfläche gefunden.

**Vorschlag:** Produktkorrektur übernehmen; die zusätzlichen Abbruch- und Reihenfolgefälle bei Gelegenheit in die dauerhaften Regressionstests aufnehmen (N2).

Die nach DEC-112 ausdrücklich erlaubte Auswertung der beim Bestätigen gültigen Vorlage bleibt bestehen. „Genau geprüft“ bezieht sich hier auf die vier Formularwerte; es bedeutet keine unveränderliche Momentaufnahme einer zwischenzeitlich administrativ geänderten Vorlage.

### F2: HTTPS-Schreibweisen – behoben

**Datei/Zeile:** `backend/src/main/java/taskmanager/appearance/internal/MailTemplateService.java:232`, insbesondere `:242` und `:247`; bestehende Datenbankregel `backend/src/main/resources/db/migration/V0049__appearance_mail_template.sql:7`.

Nach unveränderter URI-Prüfung wird ausschließlich das Schema durch `https` ersetzt. Der verbleibende Adressteil wird nicht neu aufgebaut oder kleingeschrieben.

**Nachweis über die echte HTTP-API und PostgreSQL 16.15:** `https://`, `HTTPS://`, `Https://` und `hTtPs://` erhielten jeweils 200 in Vorschau und Speicherung. Datenbank und Antwort behielten beispielsweise `/Portal/AbC?Q=AbC&x=%2F#Part` unverändert. Auch die Großschreibung im Host blieb erhalten.

15 unerlaubte Adressfälle wurden jeweils in Vorschau und Speicherung mit 400 `mail_template.address_invalid` abgelehnt: unter anderem HTTP, schemalose Adressen, JavaScript, Data-URLs, fehlender Host, nicht hierarchische HTTPS-Formen, Anmeldedaten, maskierte Anmeldedaten, Rückstrich, CR/LF, Leerzeichen und Nicht-ASCII-Zeichen. Abgewiesene Aufrufe änderten die Vorlagenversion nicht. Keine neue Umgehung der HTTPS-Beschränkung gefunden.

**Auswirkung:** Die bisherige Kombination „Vorschau 200, Speichern 500“ ist für die geprüften Schreibweisen beseitigt, ohne Pfad oder Abfrage zu verändern.

**Vorschlag:** Korrektur übernehmen. V0049 unverändert lassen; eine Migration ist für diese Behebung nicht erforderlich.

## Hinweise aus der Erstprüfung und Nachtrag

| Punkt | Datei und Zeile | Ergebnis, Auswirkung und Vorschlag |
|---|---|---|
| H1: Fokus nach Vorlagenspeicherung | `frontend/src/pages/MailTemplateSection.tsx:55`, `:66`, `:72` | Behoben. Die Statusmeldung ist programmatisch fokussierbar; der Effekt setzt den Fokus dorthin. Zwei aufeinanderfolgende Änderungen und Speicherungen mit Tab/Enter geprüft, beide Male Fokus auf `mail-template-saved`. Bedienort bleibt erhalten; übernehmen. |
| H1: Prüfung öffnen und zurückgehen | `frontend/src/pages/TicketHelpdesk.tsx:301`, `:383`, `:390`, `:397` | Behoben. Tab vom Antworttext, Enter auf „Antwort prüfen“: Fokus im mit „Prüfung der Antwort vor dem Senden“ beschrifteten Bereich. Tab/Enter auf „Zurück zum Text“: Fokus auf „Antwort prüfen“. Mit Umschalt+Tab zurück in den Text, neue Fassung und Bestätigung per Tastatur geprüft. Übernehmen. |
| H2: fehlende/fremde Sprache | `backend/src/main/java/taskmanager/appearance/internal/MailTemplateService.java:154`; `contracts/appearance.openapi.yaml:238`, `:382`; `frontend/tests/browser/backend.mjs:418` | Behoben. Echte API: fehlend, `null`, leer, `fr`, `DE` und `en-US` ergeben 400 `mail_template.languages_missing`; `de` und `en` ergeben 200 in der jeweiligen Sprache. Testersatz zusätzlich mit fehlend, `null`, leer, `fr`, `DE`, `de` und `en` geprüft, gleiches Verhalten. Vertrag und generierte Typen stimmen überein. Der interne Rückfall in `reply()` bleibt davon getrennt. Übernehmen. |
| H3: Portalauftritt und geladene Farbe | `frontend/tests/browser/portal.test.mjs:153`; `frontend/tests/browser/design.test.mjs:274` | Behoben. Der Portaltest wartet auf den erwarteten Namen und Fenstertitel; der Farbtest auf die gespeicherte Vorschaufarbe und die Speicherschaltfläche. Diese Prüfungen bestehen in beiden parallelen Läufen und seriell. Die Gesamtstabilität bleibt durch N1 eingeschränkt. |
| Nachtrag: unvollständigen Farbwert erhalten | `frontend/src/pages/AppearancePage.tsx:47`, `:187`, `:191` | Behoben. Jede Änderung des Textfelds setzt den Änderungsmarker vor der Gültigkeitsprüfung. Unabhängig mit gehaltener Auftrittsantwort und den Eingaben `#12`, leer, `#ab` sowie `#123456` geprüft: Eingabe bleibt nach vollständig eingetroffener Antwort erhalten, bei Buchstaben in der vorgesehenen Großschreibung. Übernehmen. |
| Auftragsgrenze | Diff `86df764..68771bf` | Nur die genannten Produktkorrekturen, zugehörige Verträge/Typen, Beschriftung, Tests und Auftragsdokumentation. Keine neue Funktion, keine neue oder geänderte Migration in TASK-085. Kein zusätzlicher Vorschlag zum Produktumfang. |

Die Tastaturprüfung erfolgte mit Browser-Tastaturereignissen und Kontrolle von `document.activeElement`; eine Prüfung mit einem realen Screenreader wurde nicht durchgeführt.

## Verbleibende Hinweise

### N1 – P3: Browser-Gesamtlauf weiterhin zeitabhängig

**Datei/Zeile:** `frontend/tests/browser/app.test.mjs:51`, ergänzend `:29` und `:57`; `frontend/tests/browser/support.mjs:58`; Ladeüberschrift in `frontend/src/app/App.tsx:127`.

**Nachweis:** Erster paralleler Lauf 181/181, seriell 181/181, zweite parallele Ausführung 180/181. Ausschließlich „the login page follows the browser language and the choice is remembered“ scheiterte in der Wiederholung: tatsächlich `Loading application`, erwartet `Sign in`, an Zeile 51. Die anschließende isolierte Wiederholung dieses Tests bestand.

Der Helfer wartet lediglich auf `main h1`. Diese Bedingung erfüllt bereits der Ladebildschirm. Für die anonyme Sitzung greift die zusätzliche Wartebedingung auf die angemeldete Oberfläche nicht. Der Test kann daher vor dem Loginbildschirm fortfahren. Die genannten Dateien sind im TASK-085-Diff unverändert.

**Auswirkung:** Kein belastbarer Nachweis einer stabil grünen parallelen Gesamtsuite. Der beobachtete Fehler belegt keine Regression der Mailvorlage oder des Auftritts.

**Vorschlag:** Im Login-Test auf die konkrete Loginüberschrift beziehungsweise das Loginformular warten, nach Sprachwechsel und Neuladen ebenfalls auf den erwarteten Zustand. Fenstertitel als eigenen asynchronen Zustand abwarten. Als separate Testpflege nachziehen; keine Produktfunktion ändern.

### N2 – P3: Neue Verzögerungstests könnten präziser absichern

**Datei/Zeile:** `frontend/tests/browser/helpdesk.test.mjs:425`; `frontend/tests/browser/design.test.mjs:263`; ergänzend `backend/src/test/java/taskmanager/appearance/AppearanceApiTest.java:609` und `:615`.

**Nachweis:** Nach Freigabe der gehaltenen Anfrage warten die beiden neuen Browsertests pauschal 300 ms. Das beweist nicht, dass die Antwort bereits verarbeitet wurde. Der neue Backendtest prüft beide Schreibweisen in der Vorschau, speichert aber nur `HTTPS://`; die Übergabe in TODO beschreibt auch `Https://` als durch diesen Test beim Speichern abgedeckt. Die unabhängige API-Gegenprobe hat beide Speicherfälle erfolgreich geprüft.

**Auswirkung:** Die Produktkorrekturen sind unabhängig nachgewiesen; die dauerhaften Tests sichern langsame Antwortverarbeitung und die beschriebene Variantenbreite noch nicht vollständig ab.

**Vorschlag:** Antwortabschluss und anschließende Verarbeitung gezielt synchronisieren. Abbruch, erneutes Öffnen, überholenden Erfolg/Fehler und die übrigen Entwurfsfelder als dauerhafte Regressionen ergänzen. Beide Schreibweisen auch durch den Speicherschritt des Backendtests führen oder die Nachweisbeschreibung enger formulieren.

### N3 – P3: Bereits vorhandene Tag-Testvorbereitung hängt vom Ladezeitpunkt ab

**Datei/Zeile:** `frontend/tests/browser/tags.test.mjs:29`, `:32`, `:35`, `:96` und `:100`; gleiches Muster etwa `:78` und `:125`.

**Nachweis:** Der Helfer öffnet die Tagseite vor dem Aufruf des Testkörpers. Erst dort werden die benötigten Tags in den Testersatz eingefügt. Unabhängig nachgestellt: leeren Katalog vollständig laden lassen, anschließend „Rechnung“ in den Testersatz einfügen. Die Seite bleibt leer und bietet „Rechnung zusammenführen“ nicht an. Das erklärt einen möglichen Zeitablauf hinter der Tag-Zeitüberschreitung aus der Erstprüfung. In den drei aktuellen Gesamtläufen bestanden die Tagtests.

**Auswirkung:** Auch bei aktuell grünen Tagtests bleibt eine bestehende Ursache für sporadische Fehler vorhanden. Kein Produktfehler und keine Änderung durch TASK-085 nachgewiesen.

**Vorschlag:** Tags und Zuordnungen im vorhandenen `prepare`-Schritt vor der Navigation anlegen; die Seite erst danach öffnen. Separat pflegen, ohne die Tagfunktion im Rahmen dieser Nachprüfung zu ändern.

## Ausgeführte Prüfungen

Umgebung: Windows, Python 3.12.14, Node 24.22.0/npm 11.19.1, JDK 25.0.4.1 gemäß `backend/pom.xml`, lokale PostgreSQL-16.15-Testinstanz. Die Windows-Entsprechungen des verfügbaren Python und `mvnw.cmd` wurden verwendet; vorhandene lokale Abhängigkeitscaches.

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | Erfolgreich. |
| `python3 scripts/task_board.py --check` | Erfolgreich. |
| `python3 -m unittest discover -s tests -v` | 38 Tests, keine Fehler; einer unter Windows wegen Unix-Zeitzonenumschaltung übersprungen. |
| Backend `./mvnw verify` | `BUILD SUCCESS`: 516 Tests, 0 Fehler, 0 übersprungen; PostgreSQL 16.15 über die vorgesehenen lokalen Testparameter, Maven offline. Der gegenüber PR #250 zusätzliche Test stammt aus TASK-084. |
| Frontend `npm ci --offline --no-audit --no-fund` | Erfolgreich; 58 Pakete installiert. |
| Frontend `npm run verify` | Erfolgreich: Typgenerierung, Typprüfung, 79 Tests, Produktionsbuild. Bestehender Hinweis auf ein JavaScript-Paket über 500 kB. |
| Frontend `npm run test:browser`, parallel | Erster Lauf: 181/181 erfolgreich. |
| `node --test --test-concurrency=1 tests/browser/*.test.mjs` | 181/181 erfolgreich. |
| Frontend `npm run test:browser`, parallele Wiederholung | 180/181 erfolgreich; Login-Ladezustand, siehe N1. |
| Isolierte Wiederholung des Login-Sprachtests | 1/1 erfolgreich. |
| Unabhängige Browser-Gegenproben | Alle 14 Fallgruppen erfolgreich: vier Eingabefelder, zwei Abbruch-/Öffnungsabläufe, zwei Antwortreihenfolgen, Tastaturprüfung der Antwort, zweimaliges Vorlagenspeichern, vier Farbwerte. |
| Unabhängige HTTP-/Datenbank-Gegenprobe | HTTPS-Varianten, unveränderte Pfad-/Abfrage-/Fragmentwerte, 15 unerlaubte Adressen, fehlende/fremde und gültige Sprachen erfolgreich geprüft; Ablehnungen und Vorschauen schrieben nichts. |
| Testersatz-Gegenprobe | Sprachvertrag und beide HTTPS-Speicherfälle bestanden; vorhandene Tag-Testabhängigkeit gezielt nachgestellt. |
| Git-Abgleich | Keine Produktänderungen durch die Prüfung. TASK-085 enthält keine Migrationsänderung. Frontend, Java-Produktcode und Verträge entsprechen dem PR-Ergebnis. |

Nach Ergänzung dieses Berichts wurden Repositoryvalidierung, Aufgabenübersicht und Leerraumprüfung erfolgreich wiederholt.

Nicht zusätzlich ausgeführt: Docker-/Compose-Aufbau, `backend/scripts/lifecycle-check.sh` als Shellprozess, externer SMTP-Dienst oder kompletter Demoaufbau. Die Backend-Gesamtprüfung enthält die vorhandenen Datenbank-, Anwendungslebenszyklus-, Vertrags- und SMTP-Testserverprüfungen. Der Lauf ist ein Nachweis für PostgreSQL 16.15, nicht für das im Repository separat gepinnte Containerimage. Kein vollständiger Browserlauf gegen einen echten Backendserver: die Browserprüfungen verwenden den vorhandenen Testersatz; die API-Gegenprobe lief gegen die echte Anwendung und Datenbank.

Die vollständigen Protokolle dieser Ausführung liegen außerhalb des Prüfklons unter `../review-tools/mailtemplate02-main-*`. Die verwendeten unabhängigen Prüfhilfen sind `../review-tools/MailTemplate085Probe.java`, `../review-tools/mailtemplate085-browser-probe.mjs` und `../review-tools/mailtemplate085-contract-probe.mjs`. Sie sind keine vorgeschlagenen Produktänderungen.
