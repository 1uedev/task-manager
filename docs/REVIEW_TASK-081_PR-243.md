# Unabhängiges Review TASK-081 / PR #243

**Empfehlung: nachbessern.** Die öffentliche Schnittstelle und der Schreibschutz sind in den geprüften Fällen korrekt begrenzt. Es bestehen jedoch vier Befunde zur Namensvalidierung, zum Verlust ungespeicherter Eingaben, zur Fokusführung und zur sichtbaren Darstellung des Unternehmensnamens. Eine fachliche Abnahme durch Holger wird mit diesem Bericht nicht vorweggenommen.

## Grundlage und Prüfstand

- Sitzung: `codex-20261102-branding-review-01`, unabhängige zweite KI: Codex.
- Prüfung begonnen am 3. November, nach Sitzungsunterbrechung am **4. November 2026** abgeschlossen.
- [PR #243](https://github.com/1uedev/task-manager/pull/243) wurde am 3. November gemergt; ursprünglicher Ergebnisstand `079322b50bce71624d04206e336358144a0c4178`, Merge `db2b62de8bc24f449901a61292431fcef8b37f00`.
- Gemäß Auftrag wurde aktuelles `main` geprüft: **`4307176f58e034fb2719d0c5fc5d87d0c3930ded`**, erneut mit `origin/main` abgeglichen. Eigener Arbeitsbereich und Branch `review/codex-20261102-branding-review-01`.
- Gelesen: AGENTS.md, README.md, TODO.md mit TASK-081, DONE.md, docs/AI_WORKFLOW.md, relevante Anforderungen und Entscheidungen einschließlich DEC-111, contracts/appearance.openapi.yaml und backend/README.md, Abschnitt „Erscheinungsbild (TASK-036, TASK-081)“.
- Der ursprüngliche PR-Diff wurde zusätzlich geprüft. Auf aktuellem main vorhandene Mailvorlagen aus TASK-082 und Korrekturen aus TASK-085 sind separate, inzwischen abgenommene Arbeiten und kein Umfangsverstoß von TASK-081. Die Befunde unten betreffen bereits den TASK-081-Auftritt. Zeilenangaben beziehen sich auf den genannten main-Commit.
- Kein Anwendungscode, Testcode, Vertrag, Aufgabenstatus oder Migrationsskript wurde im Repository geändert. Zusätzliche Prüfprogramme und Logs liegen außerhalb des Repository-Arbeitsbereichs; sie verwenden ausschließlich synthetische Daten und eigene PostgreSQL-Testdatenbanken. Dieser Bericht enthält die relevanten Reproduktionen und Ergebnisse.

## Befunde

### F1 – Backend akzeptiert Namen, die anschließend den gesamten Auftritt im Frontend unlesbar machen (P2)

**Datei/Zeile:** `backend/src/main/java/taskmanager/appearance/internal/AppearanceService.java:103–107`; `frontend/src/api/client.ts:218–222,246–250,259–262`; `frontend/src/brand/BrandProvider.tsx:62–76`; `backend/src/main/resources/db/migration/V0046__appearance_name.sql:5`.

`validName` kürzt mit Java `String.strip()`, der Client fordert dagegen Gleichheit mit JavaScript `trim()`. Diese Funktionen haben unterschiedliche Mengen von Leerraumzeichen. Die API und die Datenbank akzeptieren insbesondere U+00A0, U+2007 und U+202F am Rand. `isBrandName` verwirft dieselben gespeicherten Werte. Auch ein einzelnes U+00A0 wird als Name gespeichert.

**Reproduktion:** Mit vollständiger Anmeldung, `appearance.manage`, gültigem CSRF und aktuellem `If-Match` ein `PUT /api/v1/appearance` mit `{"name":"\u00a0Acme\u00a0"}` senden. Ergebnis der echten API-Probe: HTTP 200; gespeicherte Codepunkte unverändert `U+00A0 U+0041 U+0063 U+006D U+0065 U+00A0`. U+2007 und U+202F ergeben dasselbe Verhalten. `parseAppearance` und `parsePublicAppearance` liefern dafür `null`.

**Auswirkung:** Die Anmeldeseite fällt auf „Task Manager“ und die Standardfarbe zurück. Nach vollständiger Anmeldung meldet die Seite „Der Server ist nicht erreichbar“, obwohl der Server HTTP 200 liefert. Der BrandProvider erreicht den Verwaltungszustand nicht mehr; „Alles zurücksetzen“ wird nicht angeboten. Die Reparatur muss über einen direkten API-Aufruf erfolgen. Dieser Ablauf wurde zusätzlich im Browser mit den entsprechenden API-Antworten reproduziert.

**Vorschlag:** Eine explizite gemeinsame Normalisierungsregel für Backend und Frontend festlegen, insbesondere geschützte Leerzeichen berücksichtigen und nach Normalisierung auf einen nichtleeren Namen prüfen. Vorhandene betroffene Werte mit einer neuen Migration korrigieren. Dieselben Grenzfälle durch API, Datenbank und Antwortparser prüfen. V0046 als bereits ausgelieferte Migration nicht nachträglich ändern.

Weitere Grenzbeobachtung: Beide Validatoren akzeptieren einen isolierten UTF-16-Surrogaten U+D800. Die API-Probe speicherte dafür U+003F (`?`). Ungültige Unicode-Sequenzen sollten mit 400 abgelehnt werden, statt den Namen stillschweigend zu verändern. Reguläre ergänzende Zeichen funktionieren: 80 Raketenzeichen wurden unverändert gespeichert und vom Client angenommen.

### F2 – Speichern eines Teils verwirft die ungespeicherte Auswahl anderer Teile (P2)

**Datei/Zeile:** `frontend/src/pages/AppearancePage.tsx:80–93`, Aufrufer insbesondere `:152–153`, `:206–207` und `:103–106`.

Die gemeinsame Funktion `run` setzt nach jedem erfolgreichen Schreibvorgang sowohl Name als auch Farbe aus der Serverantwort neu und löscht beide Bearbeitungsmarker. Ein Aufruf, der nur die Farbe speichert, enthält den ungespeicherten Namen nicht. Trotzdem überschreibt seine Antwort das Namensfeld. Entsprechend verwirft „Namen speichern“ eine ungespeicherte Farbe; auch Logoaktionen verwenden denselben Ablauf.

**Reproduktion:** Gespeicherter Name „Nordlicht“; Namensfeld auf „DraftName“ ändern, Grün wählen, „Für alle speichern“ im Farbbereich anklicken. Die Farbe wird gespeichert, das Namensfeld springt ohne Rückfrage auf „Nordlicht“ zurück. Browserprobe bestätigt den Verlust.

**Auswirkung:** Eingaben verschwinden bei einer unabhängigen Speicheraktion. Die Vorschau fällt ebenfalls auf den alten Wert zurück. Das widerspricht den getrennten Speicherschaltflächen und der Aussage, dass nicht gesendete Teile unverändert bleiben.

**Vorschlag:** Nach einer Teiländerung nur deren lokalen Bearbeitungszustand zurücksetzen, andere ungespeicherte Werte behalten und lediglich die gemeinsame Serverversion aktualisieren. Beim ausdrücklich bestätigten Gesamtreset dürfen alle lokalen Werte zurückgesetzt werden. Gegenproben für Name → Farbe speichern, Farbe → Namen speichern und Name/Farbe → Logoaktion ergänzen.

### F3 – Tastaturfokus geht nach Speichern und nach Abbrechen der Reset-Rückfrage verloren (P2)

**Datei/Zeile:** `frontend/src/pages/AppearancePage.tsx:80–93,152–153,253–260,267–269`.

Beim Speichern wird die aktive Schaltfläche deaktiviert; ein Fokusziel nach Abschluss fehlt. Beim Abbrechen der Rückfrage wird die aktive Schaltfläche entfernt. Die Erfolgsmeldung wird zwar angekündigt, erhält aber keinen Fokus.

**Reproduktion:** „Namen speichern“ fokussieren und mit Enter aktivieren. Nach der Erfolgsmeldung ist `document.activeElement.tagName === 'BODY'`. Dasselbe wurde nach dem Speichern der Farbe und nach Enter auf „Abbrechen“ beim Gesamtreset gemessen. Positiv: Beim Öffnen der Reset-Rückfrage blieb der Fokus auf „Ja, alles zurücksetzen“; der folgende Tab erreichte „Abbrechen“.

**Auswirkung:** Tastaturnutzer verlieren nach Abschluss oder Abbruch ihre sichtbare Position im Formular. Die vorhandenen Browserprüfungen bestätigen den Inhalt der Aktionen, prüfen hier aber keine anschließende Fokusführung.

**Vorschlag:** Nach erfolgreichem Speichern eine fokussierbare Statusmeldung oder ein sinnvolles Feld fokussieren; nach Abbrechen den Auslöser „Alles zurücksetzen“ fokussieren. Fokus nach erfolgreichem Gesamtreset ebenfalls gezielt setzen. Diese Abläufe mit Tastatur und `document.activeElement` prüfen.

### F4 – Der Unternehmensname wird mit Logo sowie auf schmalen Ansichten visuell ausgeblendet (P2, fachliche Abweichung)

**Datei/Zeile:** `frontend/src/styles.css:381–382,525–530`; `frontend/src/app/App.tsx:78–89`; ergänzend `frontend/tests/browser/design.test.mjs:144–155` und die Übergabegrenze in `TODO.md:161`.

Bei vorhandenem Logo macht die CSS-Regel den Namen zu einem abgeschnittenen 1×1-Pixel-Element. Unter 30 rem wird der Name auch ohne Logo ausgeblendet. Der neue `BrandMark` verwendet diese Regeln in Anwendung, Anmeldung, Passwortwechsel und Portal weiter.

**Reproduktion:** Anmeldeseite mit Name „Nordlicht“ und Logo, Ansichtsbreite 1100 px: Name im DOM vorhanden, aber Breite/Höhe 1 px und `clip: rect(0px, 0px, 0px, 0px)`. Die visuelle Prüfung bestätigt den fehlenden Namen. Bei 320 px greift die zweite Regel auch ohne Logo. Der vorhandene Test liest lediglich `innerText()`; das belegt keine sichtbare Darstellung.

**Auswirkung:** Der Auftrag, Name, Farbe und Logo bereits vor der Anmeldung und im Portal zu zeigen, ist für sehende Nutzer in diesen Fällen nicht erfüllt. Screenreadertext und Fenstertitel ersetzen den sichtbaren Namen nicht. In den Vorschauen steht der Name dagegen neben dem Logo, sodass Vorschau und tatsächlicher Kopf abweichen.

**Einordnung:** Die ausblendenden CSS-Regeln stammen aus früheren Aufgaben und wurden in der Übergabe von TASK-081 als Einschränkung erwähnt. Dies ist keine neu eingeführte CSS-Regression; die alten Regeln wurden jedoch nicht an die neue fachliche Namensanforderung angepasst. DEC-111 und der aktuelle Prüfauftrag nennen keine entsprechende Ausnahme.

**Vorschlag:** Den Namen sichtbar neben oder unter dem Logo anordnen; bei 320 px einen eigenen umbruchfähigen Bereich vorsehen. Sichtbarkeit und tatsächliche Ausdehnung mit Logo und ohne Logo prüfen. Sollte Holger die Einschränkung fachlich akzeptieren, diese Ausnahme ausdrücklich als Entscheidung dokumentieren und Vorschauen daran angleichen.

## Hinweise

### H1 – Die Datenbankprüfung sichert die Zeichenverbote der API nicht ab

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0046__appearance_name.sql:3–5`; `backend/src/main/java/taskmanager/appearance/internal/AppearanceService.java:103–107`.

**Nachweis/Auswirkung:** V0046 prüft nur 1 bis 80 Zeichen und `btrim(name)` ohne zusätzliche Zeichenmenge. Direkte Testdatenbank-Updates mit `A\nB`, `A\u200bB` und `A\u2028B` wurden angenommen; die API verweigert diese Werte mit 400. Die Anwendung schützt ihren Schreibpfad, aber Datenimporte, Wartung oder spätere Schreibpfade haben diese Absicherung nicht. U+00A0 am Rand wird auch von der Datenbank nicht entfernt oder beanstandet, siehe F1.

**Vorschlag:** Die gemeinsame Regel aus F1 auch als Datenbankinvariante absichern oder die bewusst schwächere Datenbankprüfung ausdrücklich dokumentieren. Für eine Verschärfung eine neue Migration einschließlich Bereinigung vorhandener Werte verwenden.

### H2 – Audit protokolliert auch unveränderte, lediglich mitgesendete Teile als Änderungen

**Datei/Zeile:** `backend/src/main/java/taskmanager/appearance/internal/AppearanceService.java:68–76`; `backend/README.md:156`.

**Nachweis/Auswirkung:** Nach Speicherung von „Probe“ und `#2563eb` erzeugt ein identisches PUT erneut `appearance.name_changed` mit `Probe -> Probe` und `appearance.accent_changed` mit `#2563eb -> #2563eb`. Auch die Version steigt. Rechte, Transaktion und Versionsschutz bleiben korrekt; das Audit unterscheidet aber eine tatsächliche Änderung nicht von einer Wiederholung. Die Beschreibung spricht von „jedem geänderten Teil“.

**Vorschlag:** Vorher-/Nachherwerte vergleichen und nur tatsächlich geänderte Teile als solche protokollieren; alternativ die Semantik ausdrücklich als Speicherereignis beschreiben. Das Farbverhalten existierte bereits vor TASK-081.

### H3 – Routenabgleich ist grün, HEAD und Eingabenormalisierung sind im Vertrag nur unvollständig beschrieben

**Datei/Zeile:** `contracts/appearance.openapi.yaml:82–108,296–302`; `backend/src/test/java/taskmanager/appearance/AppearanceContractTest.java:40–43,72–74`.

**Nachweis/Auswirkung:** Die beiden öffentlichen HEAD-Operationen funktionieren, stehen aber nicht als Operationen im Vertrag. Der Routenvergleich berücksichtigt HEAD nicht. Ferner setzt das Eingabeschema `maxLength: 80` vor einer Normalisierung, während der Server zuerst kürzt: Ein 80-Zeichen-Name mit zusätzlichen Rand-Leerzeichen ist nach Serverregel gültig, nach Schema zu lang. Die Verbote von Steuer-, Format- und Zeilentrennzeichen sind im Schema ebenfalls nicht abgebildet.

**Vorschlag:** Die öffentliche HEAD-Unterstützung dokumentieren und gesondert testen. Eingabeschema und Beschreibung an die gemeinsame Normalisierungsregel aus F1 anpassen. Der erfolgreiche Test ist ein Beleg für die expliziten Controller-Routen, nicht für vollständige semantische Gleichheit aller Anfragen.

### H4 – Der reguläre Browser-Gesamtlauf ist wegen zweier Tagtests nicht grün

**Datei/Zeile:** `frontend/tests/browser/tags.test.mjs:56–60,123–127`.

**Nachweis/Auswirkung:** Der beauftragte parallele Lauf endete mit 179 erfolgreichen und zwei fehlgeschlagenen Tests. Beide Timeouts betreffen nachträglich angelegte Tags: „Kunde Nord bearbeiten“ und „Dringend“ fehlen. Die Testdaten werden erst nach der ersten Navigation angelegt; damit kann das Laden des Katalogs den Datenaufbau überholen. Die Namens-, Anmelde-, Portal- und Erscheinungsbildtests waren in diesem Lauf erfolgreich. Daraus folgt kein belegter Produktfehler von TASK-081, aber auch kein grüner Gesamt-Browsernachweis.

**Vorschlag:** Diese Tests separat stabilisieren: Tags vor der Navigation anlegen oder nach dem Anlegen ausdrücklich neu laden. Ergebnis des seriellen Vergleichslaufs siehe Testtabelle; ein grüner Wiederholungslauf hebt den ursprünglichen Fehlschlag nicht auf.

## Ergebnisse zu den sechs Prüfpunkten

1. **Öffentlicher Lesepfad:** `AuthenticationFilter.java:86–100` verwendet die Spring-Pfadsegmente mit Dekodierung und Entfernung von Matrixparametern und erlaubt ausschließlich die beiden kanonischen Pfade für GET/HEAD. 16 Pfade × 8 Methoden wurden über den echten HTTP-Server geprüft. Kodiertes `/%61pi/`, `%70ublic` und `;x` erreichen ausschließlich dieselbe öffentliche Antwort. Doppelter Slash und abschließender Slash ergaben 404; kodierter Slash 400. `public/../logo` und die kodierte Punktvariante ergaben 401, ebenso `public/extra`, `publicity`, private Einstellung, privates Logo und Mailvorlage. POST/PUT/PATCH/DELETE/OPTIONS eröffneten keinen Zugriff; TRACE wurde mit 405 abgewiesen. Erfolgreiche HEAD-Antworten hatten keinen Body. Kein zusätzlicher Lese- oder Schreibzugriff gefunden.
2. **Informationsumfang und Sitzungen:** `AppearanceController.java:86–110` liefert öffentlich nur `name`, `accentColor`, `logoUrl`, ohne ETag, Version, Zeitpunkt oder Person. Logoantworten enthalten Bilddaten und die dokumentierten Schutzheader; kein alternativer Unternehmenskontext ist wählbar. `Cache-Control: no-store` wird im Filter gesetzt. Die vorhandenen erfolgreichen API-Tests decken ungültiges/abgelaufenes Sitzungscookie und ausstehenden Passwortwechsel ab. Die öffentliche Freigabe erfolgt vor der entsprechenden Ablehnung; private Pfade bleiben geschützt. Die Unicode-Abweichungen sind F1/H1.
3. **PUT/DELETE:** Teilupdates für Farbe, Name und beide zusammen, 400 `appearance.change_empty`, Validierungsfehler, Audit pro gesendetem Teil sowie atomarer Gesamtreset sind implementiert und durch API-Tests geprüft. `AppearanceController.java:80–83,146–175` und `AppearanceService.java:82–88,145–164` sichern Anmeldung, `appearance.manage`, CSRF, `If-Match`, Zeilensperre und Version ab. Fehlende Version ergibt 428, veraltete 412, fehlendes Recht 403. Gesamtreset stellt Name/Farbe/Logo gemeinsam zurück, erhöht die Version einmal und schreibt `appearance.reset`. Die Auditpräzision ist H2.
4. **Oberfläche:** `BrandProvider.tsx:62–89` liest zuerst den Sitzungszustand, nur ohne vollständige Anmeldung öffentlich und sonst die vollständige Einstellung. Fehlende/ungültige Antworten führen zum Standard. AbortController verwirft überholte Abrufe. `app/session.tsx:65–71` lädt beim Wechsel in/aus vollständiger Anmeldung erneut. Login, Pflichtwechsel und Abmelden sind durch Browserfälle abgedeckt; das Portal verwendet denselben Auftritt mit übersetztem Zusatz „Kundenportal“/„Customer portal“. Name wird in JSX-Text und `document.title` ausgegeben. Eine zusätzliche Probe mit `<img src=x onerror=alert(1)>` erzeugte Text und kein eingefügtes Bild. Kein `dangerouslySetInnerHTML` oder `innerHTML` im Frontend gefunden. Die fünf TASK-081-Vorschauen verwenden feste synthetische Inhalte, keine Tickets oder Kontakte. F1–F4 bleiben offen.
5. **Migrationen und Vertrag:** V0046 ist im Migrationsverzeichnis sowie in beiden Java-Prüfungen und dem Shellskript berücksichtigt. Ursprünglicher PR: 43 Migrationen bis V0046; aktuelles main: 48 bis V0051, mit den bestehenden Nummernlücken. `DatabaseFoundationTest.java:36`, `ApplicationLifecycleTest.java:34` und `lifecycle-check.sh:89–91` sind konsistent. Der Vertrag entspricht den expliziten Routen: ursprünglich acht, heute zwölf mit den separat beauftragten Mailvorlagenrouten. HEAD-/Normalisierungsgrenzen siehe H3.
6. **Umfang und Barrierefreiheit:** Der ursprüngliche PR umfasst Name, öffentlichen Auftritt, Gesamtreset, Vorschauen, Migration, Vertrag und Tests; keine zusätzliche konfigurierbare Farbe, SVG-Freigabe, persistierte Entwurfsverwaltung oder eigener Portalauftritt. Die ungespeicherte lokale Vorschau ist beauftragt und kein zusätzlicher Entwurfsworkflow. Felder sind beschriftet, Fehler über `aria-invalid`/Beschreibungen zugeordnet; Farbwahl hat Textnamen, Upload eine Tastaturalternative. Eigene 320-px-Proben in Hell und Dunkel mit einem 80-Zeichen-Namen: Dokumentbreite jeweils 320/320 px, nach Ende der CSS-Übergänge jeweils 99 gemessene Texte ohne Kontrastverletzung. Drei Screenshots wurden visuell geprüft. Diese Stichprobe ist keine vollständige Barrierefreiheitszertifizierung; Fokus und sichtbarer Name sind F3/F4.

## Ausgeführte Prüfungen und Grenzen

Die Befehle wurden im separaten Arbeitsbereich ausgeführt. Unter Windows kam für `python3` der vorhandene Python-3.12-Interpreter und für `./mvnw` der mitgelieferte Windows-Wrapper `mvnw.cmd` zum Einsatz. Node 24.22.0 / npm 11.19.1, Java 25.0.4.1+1 und echte PostgreSQL 16.15. Weder eine Produktionsdatenbank noch echte Konten wurden verwendet.

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Erfolgreich; nach dem Bericht erneut geprüft. |
| `python3 scripts/task_board.py --check` | Erfolgreich, Übersicht aktuell. |
| `python3 -m unittest discover -s tests -v` | 38 Tests, 37 erfolgreich, ein Unix-spezifischer Zeitzonentest unter Windows übersprungen. Erfolgreicher Lauf mit `PYTHONUTF8=1`; vorher acht Windows-Kodierungsfehler ohne diese Einstellung. |
| Backend `./mvnw verify` mit PostgreSQL 16 | Erfolgreich: **516 Tests, 0 Fehlschläge, 0 Fehler, 0 übersprungen**, `BUILD SUCCESS`; Laufzeit 12:17 Minuten. Darunter AppearanceApiTest 19, AppearanceContractTest 1, DatabaseFoundationTest 4 und ApplicationLifecycleTest 3, jeweils erfolgreich. |
| Frontend `npm ci` | Erfolgreich. npm meldete drei moderate Abhängigkeitswarnungen; keine Abhängigkeiten geändert. |
| Frontend `npm run verify` | Erfolgreich: Generierung, Typecheck, 79/79 Tests und Produktionsbuild. Vite meldete einen großen Bundle-Chunk. |
| Frontend `npm run test:browser` | Ausgeführt: 181 Tests, 179 erfolgreich, zwei Tagtest-Timeouts, keine übersprungenen Tests; H4. |
| Browser-Gesamtlauf seriell (`node --test --test-concurrency=1 tests/browser/*.test.mjs`) | Erfolgreich: **181/181 Tests**, keine übersprungenen Tests. Die beiden Tagtest-Fehlschläge traten hier nicht auf; zusammen mit dem Testaufbau spricht dies für ein Ablaufproblem. H4 bleibt als Hinweis zur Teststabilität bestehen. |
| Zusätzliche API-/Datenbankproben | Erfolgreich ausgeführt: 128 Pfad-/Methodenkombinationen, Unicode-Grenzfälle, Datenbankinvarianten und Audit bei identischem PUT; Ergebnisse oben. |
| Zusätzliche Browserproben | Erfolgreich ausgeführt: Parserabweichung mit Fallback, Verlust der Eingabe, Fokus, Logo-/Namensdarstellung, 320 px hell/dunkel, Kontrast und Textausgabe statt HTML; Ergebnisse oben. |
| `git diff --check` / Unverändertheit | Sauber; vor Berichtserstellung keine Repositoryänderungen, nachher ausschließlich dieser Bericht. |

**Nicht ausgeführt:** Das separate Unix-Prozessskript `backend/scripts/lifecycle-check.sh` mit SIGTERM und der CI-Testcontainer PostgreSQL 18.6. Die Migrationszahlen wurden statisch geprüft; der Maven-Lauf enthält den Java-Lebenszyklustest mit frischer PostgreSQL-16-Datenbank. Der ursprüngliche PR-Commit wurde nicht separat vollständig gebaut; alle hier ausgewiesenen Läufe gelten dem oben genannten aktuellen main-Stand.

**Umgebungsprobleme, nicht als Produktfehler gewertet:** Der erste Backendversuch verwendete ein leeres Testpasswort und scheiterte an der vorgeschriebenen Konfiguration. Ein weiterer Versuch aus dem Sandbox konnte die lokale Testdatenbank nicht erreichen und wurde abgebrochen; der abschließende Lauf nutzt ein synthetisches nichtleeres Testpasswort außerhalb des Sandboxes. Auch der erste Browserlauf aus dem Sandbox wurde abgebrochen. Die Python-Tests benötigten Zugriff auf temporäre Dateien außerhalb des Sandboxes und UTF-8 als Standardkodierung. Keine dieser Anlaufstörungen wird als erfolgreicher Testnachweis gezählt.

## Abnahmeempfehlung

**TASK-081 nachbessern, noch nicht abnehmen.** F1 und F2 beeinträchtigen die verlässliche Pflege des Auftritts; F3 betrifft die beauftragte Tastaturbedienung. F4 muss umgesetzt oder von Holger ausdrücklich als fachliche Ausnahme entschieden werden. Danach gezielte Gegenprüfungen und die erforderlichen Gesamtläufe wiederholen. Die fehlende öffentliche Schreibfreigabe und der Versions-/Rechteschutz sind positiv geprüft; eine Sicherheitsöffnung des neuen öffentlichen Pfads wurde nicht festgestellt.
