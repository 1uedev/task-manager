# Prüfung TASK-089 / PR #263

**Empfehlung: nachbessern.** Die ursprünglichen S1-Abläufe mit gleicher Version und mit Lesefehler sind korrigiert. S2 und die konkreten Logo-Typprüfungen aus H2 sind behoben; H1 hat jetzt einen echten HTTP-/PostgreSQL-Test mit zwei parallel gestarteten Clientaufträgen. Die neue Ladeaktion schützt jedoch weder vor Antworten einer bereits verlassenen Seite (T1) noch vor überlappenden Vorgängen über Logo-Drag-and-drop (T2). Beide Fälle können den gemeinsamen Auftritt auf eine ältere Version zurücksetzen, obwohl der Server bereits einen neueren Stand gespeichert hat.

## Prüfstand und Umfang

- Codex, Sitzung `codex-20261106-task089-review-01`, 6. November 2026; unabhängige technische Prüfung nach DEC-040.
- Repository `1uedev/task-manager`; geprüfter Kopf des Branches `claude/project-thread-tzp0cw`: `d7041c9fc87bccde5d14031ce6eeed2e0375d4df`, Basis `bff7a72914468075f993688c991f60785806847c`.
- PR #263 war zu Beginn offen und als Entwurf markiert. Während der Prüfung wurde er gemergt; aktuelles main ist `6f14fd784810aa1998b877c465c04bf198560e01`. Der geprüfte Dateistand entspricht dem Ergebnis auf main. Ein Merge ist keine fachliche Abnahme.
- Grundlagen: AGENTS.md, README.md, TODO.md/TASK-089, DONE.md, docs/AI_WORKFLOW.md, relevante Anforderungen und Roadmap, DEC-119 sowie docs/REVIEW_TASK-087_PR-259_2.md mit S1, S2, H1 und H2.
- Geprüft: die beauftragten Frontend-, Übersetzungs-, Testersatz- und API-Teständerungen sowie ergänzend `BrandProvider`, API-Client, Seitenwechsel im `App`-Baum, `ProblemView`, Controller, Service und Logo-Erkennung.
- Diese Sitzung ändert ausschließlich diesen Bericht. Keine Korrektur der Anwendung, keine Änderung des Aufgabenstatus und keine fachliche Abnahme.

## Befunde

### T1 [P2] – Die Ladeantwort einer verlassenen Seite kann einen inzwischen gespeicherten Stand überschreiben

**Stellen am geprüften Kopf:** `frontend/src/pages/AppearancePage.tsx:95–105,125–136`; ergänzend `frontend/src/brand/BrandProvider.tsx:118–120` und `frontend/src/app/App.tsx:307`.

`loadCurrent` wartet ohne Abbruchsignal oder Prüfung der noch aktiven Seiteninstanz auf `readAppearance(api)`. Auch nach dem Aushängen von `AppearancePage` läuft `take(part, outcome.data)` weiter. Dessen `accept(data)` wirkt auf den weiterhin vorhandenen `BrandProvider`; er nimmt die Antwort ohne Prüfung entgegen. Dass React die lokalen Zustandsänderungen der alten Seite ignoriert, verhindert diese globale Änderung nicht.

**Reproduktion mit den unveränderten React-Komponenten, echtem API-Client und kontrollierter Antwortreihenfolge:**

1. Die Seite kennt Version 3. Eine andere Änderung hat auf dem Server Version 9 mit Name „Alter Servername“ erzeugt. Das Speichern des eigenen Namens ergibt 412.
2. „Aktuellen Stand laden“ starten. Die Antwort enthält Version 9; ihre Auslieferung zurückhalten.
3. Auf eine andere Seite wechseln und das Erscheinungsbild wieder öffnen. Der Provider kennt weiterhin Version 3. Erneut einen Namen speichern, den erwarteten Konflikt über eine zweite, sofort beantwortete Ladeaktion auflösen und anschließend „Neu gespeichert“ erfolgreich als Version 10 speichern.
4. Jetzt die erste Antwort mit Version 9 ausliefern.

**Beobachtet:** Der Server behält „Neu gespeichert“/Version 10. Der Provider fällt auf „Alter Servername“/ETag `"9"` zurück; auch das Namensfeld der neuen Seiteninstanz zeigt wieder „Alter Servername“. Die globale Darstellung und die Version für die nächste Änderung sind veraltet. Die Übernahme ist damit weiterhin möglich, nachdem der ursprüngliche Seitenkontext längst beendet wurde.

**Korrektur:** Den Lesevorgang an die Lebensdauer der Seite binden und beim Verlassen abbrechen beziehungsweise seine Antwort verwerfen. Vor jeder Übernahme einschließlich `accept` prüfen, ob der Vorgang noch gültig ist. Eine Regression muss die alte Antwort erst nach erfolgreichem Laden und Speichern in der neuen Seiteninstanz freigeben und prüfen, dass Name, Logo, Farbe und ETag den neueren Stand behalten.

### T2 [P2] – Logo-Drag-and-drop umgeht die Sperre während des Ladens und ermöglicht überholte Übernahmen

**Stellen am geprüften Kopf:** `frontend/src/pages/AppearancePage.tsx:108–114,125–131,139–155,257–275`.

Die Speicherschaltflächen und „Datei auswählen“ beachten `busy`. `onDrop`, `takeFile` und der Aufruf von `run` prüfen es dagegen nicht. Während `loadCurrent` auf seine Antwort wartet, startet ein fallengelassenes Logo deshalb einen weiteren Schreibvorgang. Dessen Abschluss setzt das gemeinsame `busy` auf `false`, obwohl der erste Lesevorgang noch läuft. Seine Meldung kann einen zweiten Lesevorgang anbieten; eine laufende Vorgangskennung oder andere Absicherung gegen die spätere erste Antwort gibt es nicht.

**Reproduktion in derselben Komponentenprobe, ohne Seitenwechsel:**

1. Wie bei T1 nach einem echten Versionskonflikt die Ladeantwort mit Version 9 und ohne Logo zurückhalten; die Seite kennt noch Version 3.
2. Während des Lesens ein gültiges PNG auf die Logo-Fläche ziehen. Trotz `busy` wird ein PUT mit ETag `"3"` gesendet. Es endet mit 412; die Ladeaktion ist wieder verfügbar, während der erste GET noch offen ist.
3. Diese zweite Ladeaktion sofort mit Version 9 beantworten. Das PNG erneut ablegen; der Upload speichert erfolgreich Version 10 mit Logo.
4. Die erste GET-Antwort mit Version 9 und ohne Logo freigeben.

**Beobachtet:** Der Server enthält das Logo in Version 10. Im Provider steht wieder ETag `"9"` und `logo: null`; die gerade erfolgreich gespeicherte Logodarstellung verschwindet aus der Oberfläche. Damit reicht die Sperre der Schaltflächen nicht aus, um die neue Ladeaktion gegenüber anderen Teilen zu schützen.

**Korrektur:** Alle Einstiegspunkte einschließlich Drop und Dateiauswahl an derselben Sperre beteiligen. Überholte Antworten dürfen keinen neueren Vorgang überschreiben. Ergänzend testen: Logo-Drop während des Konfliktlesens sendet keine zweite Änderung, und eine verspätete ältere Antwort setzt weder gespeicherte Darstellung noch ETag zurück. T1 und T2 brauchen beide Belege: Eine reine Unmount-Absicherung schließt T2 nicht.

## Antworten auf die Prüffragen

### 1. Eigene Antwort, gleiche Version, Fehler und weitere Aktionen

Für einen einzelnen Vorgang auf derselben Seite ist die Korrektur gelungen. `take` verwendet direkt `outcome.data`; es hängt kein Effekt mehr von einer ETag-Änderung ab. Die deutschen und englischen Texte für `appearance.reloaded` sind vorhanden. Die erfolgreiche Übernahme bekommt eine Rückmeldung und den vorgesehenen Fokus.

| Ablauf | Ergebnis der Prüfung |
| --- | --- |
| Erfolgreiches Konfliktlesen mit gleicher ETag | Der gescheiterte Teil wird ausdrücklich übernommen; ein ungespeicherter anderer Teil bleibt. In der Komponentenprobe: Name zurück auf „Nordlicht“, ungespeicherte Farbe bleibt `#7C3AED`. |
| Danach einen anderen Teil speichern | Der inzwischen neu bearbeitete Name bleibt beim Farbspeichern erhalten. Kein alter Übernahmeauftrag wird abgearbeitet. |
| Lesefehler | Alle bearbeiteten Werte bleiben; die Providerverbindung bleibt benutzbar. `notice.loading` bietet „Erneut versuchen“ für denselben Zielteil. |
| Nach Lesefehler direkt bearbeiten und anderen Teil speichern, ohne vorher zu wiederholen | Zusätzlich zur gelieferten Regression geprüft: Der neue Namensentwurf bleibt beim Farbspeichern erhalten. Es existiert kein nachwirkender Übernahmeauftrag. |
| Wiederholen nach Lesefehler | Der Zielteil bleibt an die neue konkrete Leseantwort gebunden; andere Entwürfe bleiben. Die gelieferte Browserregression belegt diesen Weg in der CI. |
| Gewöhnlicher Doppelklick auf die Ladeaktion | Der erste Klick löscht die Meldung und damit die Schaltfläche. Nach dem React-Commit gibt es kein zweites Klickziel; normale Speicherschaltflächen sind gesperrt. Dieses DOM-Verhalten wurde geprüft, kein nativer Browserdoppelklick in dieser Sitzung. Es ist keine allgemeine Sperre für andere Einstiegspunkte, siehe T2. |
| Seitenwechsel während des Lesens | Nicht abgesichert, siehe T1. |
| Andere Teiländerung während eines noch laufenden Lesens | Normale Schaltflächen sind gesperrt, Logo-Drop umgeht dies, siehe T2. |

Der vorhandene Teilumfang bleibt passend: `name` übernimmt den Namen, `color` Farbe und Hex-Eingabe, `logo` den gespeicherten Auftritt über den Provider und `all` absichtlich alle Teile. Andere bearbeitete Name-/Farbentwürfe schützt weiterhin der jeweilige Bearbeitungsmarker. Ein administratives Logo wird direkt hochgeladen; es gibt dort keinen separat wartenden Logoentwurf.

### 2. JSON-Typen und Logotypen des Testersatzes

**Die konkret beanstandeten Fälle sind korrigiert.** 19 zusätzliche direkte Requests durch den tatsächlich installierten Route-Handler von `backend.mjs` lieferten reguläre Antworten, ohne die früheren `TypeError`-Abbrüche.

| Eingabe | Testersatz | Serverbeleg |
| --- | --- | --- |
| `{name:{}}`, `{name:["A"]}`, `{accentColor:["#f97316"]}`, JSON-Körper `[]` | 400 `request.invalid` | Neuer API-Test `wrongJsonTypesAreRefusedLikeTheTestStandIn`, in der Backend-CI erfolgreich. |
| Körper `null` | 400 `appearance.change_empty` | Derselbe API-Test. |
| `{accentColor:42}` | 400 `appearance.color_invalid` | Derselbe API-Test. |
| `{name:42}` | 200, Name `"42"` | Derselbe API-Test prüft Status und gespeicherten Antwortwert. |
| Wahrheitswerte in Name/Farbe | Name als `"true"`/`"false"`, Farbe 400 `appearance.color_invalid` | Im Testersatz direkt geprüft; der neue API-Test enthält hierfür noch keine eigene Serverassertion, siehe Hinweis 2. |
| Leere/zu große Logodaten, auch mit alter numerischer ETag | 400 `appearance.logo_empty` / 413 `request.too_large` vor dem Versionskonflikt | Entspricht der Reihenfolge in `AppearanceService.replaceLogo`. |
| PNG als `image/jpeg`, unbekannte Bildsignatur | 415 `appearance.logo_type_invalid`, vor dem Versionsvergleich | Entspricht Service und `Logo.detectType`; die bestehenden echten Logo-API-Tests sind Teil der grünen Backend-CI. |
| Wiederholt identische PNG-Bytes mit richtigem Typ / danach falschem Typ | Unveränderte Version / 415 statt unverändertem Erfolg | Gleichheit umfasst jetzt Typ und Bytes; ein abweichender deklarierter Typ wird vorher abgelehnt. |

Die Erkennung von PNG, JPEG und WebP sowie das Normalisieren des deklarierten Typs entsprechen den betreffenden Serverregeln. Der Testersatz dekodiert Bilder weiterhin nicht und bildet `appearance.logo_unreadable` nicht nach; das ist in der Übergabe ausdrücklich als Grenze dokumentiert. Deshalb ist dies kein Nachweis vollständiger Gleichheit sämtlicher Bild-, HTTP- oder JSON-Bindungsregeln.

### 3. Gleichzeitige unveränderte API-Anfragen und veraltete Version

**Als Integrationstest mit parallel gestarteten HTTP-Aufträgen tragfähig, mit der unten genannten Grenze.** `concurrentRequestsWithoutAChangeLeaveVersionAndAuditAndAStaleOneIsRefused` verwendet zwei getrennte angemeldete Clients, zwei Executor-Threads und eine gemeinsame Start-Latch. Es ist kein bloß serieller Test mehr.

- Je zwei Standard-Resets und Entfernungen ohne Logo erhalten 200 und jeweils ETag `"1"`.
- Ein echter Logo-Upload erzeugt Version 2. Zwei weitere identische Uploads erhalten beide 200 und ETag `"2"`.
- Ein identischer Logo-Upload mit ETag `"1"` und eine inhaltlich unveränderte Namensanfrage mit dieser alten Version erhalten 412 `appearance.version_conflict`.
- Die abschließende Datenbankabfrage erwartet genau `appearance.logo_replaced` als einziges Auditereignis und Version 2. Damit sind zusätzliche Änderungen durch die unveränderten Anfragen ausgeschlossen.

Die Produktionslogik passt dazu: `locked(expectedVersion)` sperrt die Zeile per `FOR UPDATE` und prüft ihre Version vor dem Inhaltsvergleich; unveränderte Rückgaben liegen vor UPDATE und Audit. Das Backend-CI-Protokoll belegt den erfolgreichen Lauf aller 25 `AppearanceApiTest`-Tests gegen PostgreSQL 18.6. Eine deterministisch erzwungene überlappende Sperrwartephase belegt die Start-Latch allein nicht (Hinweis 1).

## Hinweise ohne zusätzlichen Abnahmeblocker

1. **Nebenläufigkeitsbeleg präzise benennen:** Der Helfer öffnet die Latch, ohne vorher auf die Bereitschaft beider Threads zu warten. Der Scheduler kann die Aufträge zeitlich versetzt bis zum Server bringen. Für einen deterministischen Nachweis der Sperrkonkurrenz eine Bereitschaftsbarriere und eine kontrollierte Datenbanksperre mit Beobachtung der wartenden Verbindung ergänzen. Die Formulierung „at the same moment“ im Helferkommentar ist stärker als der gegenwärtige Nachweis. Dies entwertet weder die zwei unabhängigen Clientaufträge noch ihre Status-/ETag-/Auditassertionen.
2. **Wahrheitswerte und Logo-Prüfreihenfolge dauerhaft absichern:** Die gelieferten JSON-API-/Browsertests prüfen die Zahl 42, aber keine Wahrheitswerte. Die gelieferten Logo-Browsertests verwenden eine aktuelle ETag; die Priorität des Typfehlers gegenüber einer veralteten, gültig geformten ETag folgt dort noch nicht aus einer Assertion. Die zusätzlichen direkten Proben dieser Sitzung bestätigen das Verhalten des Testersatzes. Passende dauerhafte API-/Browserfälle würden die weitergehenden Aussagen besser absichern.

## Tatsächlich ausgeführte Prüfungen und Grenzen

**Eigene Ausführung:**

- Vollständiger separater Git-Clone, Prüfung der Differenz von #263, erneuter Abruf von main und des PR-Status; geprüfter Branchkopf blieb unverändert.
- `npm ci --ignore-scripts --no-audit --no-fund` und `npm run verify`: erfolgreich; Clientgenerierung, TypeScript, 83 Tests und Produktionsbuild. Lokal Node 24.22.1/npm 11.9.0 statt der exakten Projektvorgaben 24.22.0/11.19.1; der Build meldet die bereits bestehende Warnung zur Bundlegröße.
- React-/DOM-Proben mit JSDOM, unveränderten Komponenten und API-Client: gleiche ETag mit späterem Farbspeichern; Lesefehler mit anschließendem Farbspeichern ohne Wiederholen; Entfernen der Ladeschaltfläche und Sperren der normalen Speicherschaltflächen; kontrolliert verspätete Antworten für T1 und T2. T1 und T2 wurden mit echten Versionskonflikten 3/9 und nachfolgendem Erfolg in Version 10 reproduziert. Die Serverantworten dieser Proben sind synthetisch; dies ist keine vollständige Browser- oder Backendintegration.
- 19 direkte Requests durch den echten installierten Browser-Testersatz-Route-Handler: JSON-Typfehler, `null`, Zahl/Wahrheitswerte, Logo-Leere/Größe/Typ, alte Version, MIME-Parameter und unveränderter Wiederholungsupload erfolgreich gegen die beschriebenen Erwartungen geprüft.
- `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `python3 -m unittest discover -s tests -v`: erfolgreich, 38 Python-Tests. `git diff --check`: erfolgreich.
- `npm run test:browser` mit installiertem Chromium versucht, aber vor der fachlichen Testausführung gescheitert: Chromium kann in dieser Laufzeit seinen Prozess-Socket nicht anlegen (`Operation not permitted`). Kein eigener erfolgreicher Browserlauf. Kein eigener Maven-/PostgreSQL-Lauf.

**Zusätzlich eingesehene GitHub-CI, ausdrücklich keine eigene Testausführung:**

- [Backend-Run 42201439180](https://github.com/1uedev/task-manager/actions/runs/42201439180): 542 Tests, keine Fehler oder übersprungenen Tests; darunter 25 `AppearanceApiTest`-Tests. Protokoll bestätigt JDK 25.0.4.1 und PostgreSQL 18.6.
- [Frontend-Run 42201439144](https://github.com/1uedev/task-manager/actions/runs/42201439144): 83 Tests und 205 Browsertests erfolgreich, keine Fehler.
- Beide CI-Protokolle nennen den Prüf-Merge `72cffb46aec896f49f09a70c216e9c444e18aa9c` aus Kopf `d7041c9` und Basis `bff7a72`. Sie belegen die gelieferten Regressionen, enthalten aber keine Tests für T1 und T2.

## Empfehlung an Holger

**Nachbessern: T1 und T2 schließen und ihre verspäteten Antworten gezielt nachprüfen.** S2 sowie die ursprünglichen Hinweise H1 und H2 sind im beschriebenen Umfang bearbeitet. Die ursprüngliche S1-Übernahme bei gleicher Version und nach Lesefehler funktioniert; die zusätzlich angefragten Grenzen bei Seitenwechsel und überlappenden Teilaktionen bleiben offen.

Über die gemeinsame fachliche Abnahme von TASK-081, TASK-087 und TASK-089 entscheidet weiterhin Holger. Diese Sitzung nimmt keine dieser Aufgaben ab.

## Nachtrag vom 8. November 2026

**Empfehlung unverändert: nachbessern. T1 und T2 sind jetzt zusätzlich im echten Chrome reproduziert.** Sitzung `codex-20261108-task089-review-01` hat den vorhandenen Bericht aus main übernommen und um eigene Browser- und Datenbankausführung ergänzt. Die Ergebnisse vom 6. November oben bleiben als damaliger Prüfstand erhalten.

### Stand und Abgrenzung

- Geprüfter PR-Kopf weiterhin `d7041c9fc87bccde5d14031ce6eeed2e0375d4df`; main bei dieser Nachprüfung: `6719883` (Berichts-PR #264). Der vollständige Tree-Vergleich unterscheidet nur die inzwischen hinzugekommene Berichtsdatei; der Anwendungscode und die gelieferten Tests sind identisch.
- PR #263 ist gemergt; TASK-081, TASK-087 und TASK-089 stehen weiterhin auf BEREIT ZUR PRÜFUNG. Diese Sitzung ändert weder Produktcode noch Aufgabenstatus oder Abnahme.
- Lokale Werkzeuge: Node 24.22.0, npm 11.19.1, Chrome 156.0.8078.80, JDK 25.0.4.1, Maven 3.9.16, PostgreSQL 16.15 in einer eigenen temporären Testinstanz. Der lokale Datenbanklauf ersetzt keinen Nachweis für die festgelegte PostgreSQL-Version 18.6.

### Zusätzliche Browsernachweise

Der unveränderte Produktionsbuild wurde mit dem vorhandenen Browser-Testersatz geprüft. Für T1 und T2 wird ausschließlich die erste konkrete GET-Antwort zurückgehalten; weitere Anfragen laufen durch den regulären Testersatz. Nach Freigabe der alten Antwort wird zusätzlich eine neue Änderung abgesendet, um die tatsächlich verwendete ETag am HTTP-Request nachzuweisen.

| Probe | Eigenes Ergebnis |
| --- | --- |
| Nativer Maus-Doppelklick auf „Aktuellen Stand laden“ | Genau ein GET. Die Aktion verschwindet nach dem ersten Klick; die normalen Speicherschaltflächen sind während des Lesens gesperrt. Die Antwort wird übernommen. |
| T1: Seite verlassen, erneut öffnen, aktuell laden und Namen als Version 10 speichern; danach alte Antwort mit Version 9 freigeben | Fehler bestätigt: Namensfeld wieder „Alter Servername“, Server weiterhin „Neu gespeichert“ in Version 10. Der nächste PUT trägt `If-Match: "9"` und endet mit 412. |
| T2: Logo-Drop während des zurückgehaltenen Lesens, zweiten Konflikt auflösen und Logo als Version 10 speichern; danach alte Antwort mit Version 9 freigeben | Fehler bestätigt: Der erste Drop sendet trotz laufenden Lesens einen PUT mit `If-Match: "3"`. Nach dem später erfolgreichen Upload verschwindet das Logo durch die alte GET-Antwort wieder aus der Oberfläche. Der Server behält das Logo in Version 10; der nächste PUT trägt erneut `If-Match: "9"` und endet mit 412. |
| Nach Lesefehler Namen erneut bearbeiten und Farbe speichern, ohne vorher „Erneut versuchen“ zu wählen | Neuer Namensentwurf bleibt erhalten. Kein nachwirkender Übernahmeauftrag. Für den erfolgreichen Folgeschritt setzt die synthetische Probe die Serverversion auf die bekannte Version zurück. |
| Gleiche Version nach Farbkonflikt | Serverfarbe wird übernommen, Namensentwurf bleibt. |
| Gleiche Version nach Logokonflikt | Namensentwurf und ungespeicherte Farbe bleiben. |
| Gleiche Version nach Konflikt beim vollständigen Zurücksetzen | Servername und Serverfarbe werden übernommen, entsprechend `part: all`. |

Alle sieben Ablaufproben sind erfolgreich ausgeführt. Bei T1 und T2 bedeutet dies ausdrücklich **erfolgreiche Reproduktion des Fehlers**, keine Erfüllung des Sollverhaltens. Die gelieferten Browserregressionen ergänzen den Namensfall mit gleicher Version und den erfolgreichen Wiederholungsweg nach Lesefehler.

Die ausführbare Browserprobe (`review-evidence/task-089/browser-probe.mjs`) und ihre Ausgabe (`review-evidence/task-089/browser-probe.txt`) liegen dem Bericht bei. Ausführung nach Installation und Produktionsbuild des Frontends vom Repository-Wurzelverzeichnis aus:

```text
node docs/review-evidence/task-089/browser-probe.mjs
```

### Eigene Testläufe und Einordnung

| Prüfung | Ergebnis |
| --- | --- |
| `npm run verify` | Erfolgreich: Clientgenerierung, Typprüfung, 83 Tests und Produktionsbuild. Bestehende Warnung zur Bundlegröße. |
| `npm run test:browser` | 204 von 205 bestanden. Alle Auftrittstests einschließlich der neuen S1-/S2-/H2-Fälle bestanden. Ein Timeout im Tag-Test, siehe unten. |
| Isolierte Wiederholung des Tag-Tests | 1 von 1 bestanden. |
| Sieben zusätzliche Browserproben | Alle erwarteten Beobachtungen bestätigt, einschließlich T1 und T2. |
| Maven `-Dtest=AppearanceApiTest test` gegen PostgreSQL 16.15 | 25 Tests, 0 Fehler, 0 übersprungen. Einschließlich `wrongJsonTypesAreRefusedLikeTheTestStandIn` und `concurrentRequestsWithoutAChangeLeaveVersionAndAuditAndAStaleOneIsRefused`. |
| Repositoryvalidator und `task_board.py --check` | Erfolgreich. |
| Python-Unittests | 38 Tests, 37 bestanden; 1 vorgesehene Überspringung unter Windows, weil der betreffende Test das Umschalten der Unix-Zeitzone benötigt. |
| `git diff --check` | Erfolgreich. |

Der erste API-Versuch scheiterte beim Start des Anwendungskontexts, weil diese Sitzung das erforderliche synthetische Datenbankpasswort leer gesetzt hatte. Nach Korrektur der lokalen Testkonfiguration lief dieselbe unveränderte API-Testklasse vollständig grün. Das war kein Produktbefund. Eine neue vollständige Backend-Suite wurde in dieser Sitzung nicht ausgeführt.

Der Browser-Gesamtlauf scheiterte ausschließlich an `tags.test.mjs:124`, „the dark display keeps the tag readable with its color mark“: Der Tag „Dringend“ wurde innerhalb von 30 Sekunden nicht sichtbar. Die isolierte Wiederholung bestand. Der Test legt den Tag erst nach dem Öffnen der Seite an; seine Reihenfolge gegenüber dem ersten Laden ist deshalb ein plausibler Ansatzpunkt für die Untersuchung. Dieser Test ist gegenüber dem PR-Stand unverändert. Kein Nachweis einer durch TASK-089 verursachten Regression, aber auch **kein vollständig grüner eigener Gesamtlauf**.

S2 und die konkreten H2-Typfälle sind nun sowohl durch die eigenen Browser- als auch die eigenen Serverläufe gestützt. Die bisherigen Grenzen bleiben: keine vollständige Bilddekodierung im Testersatz, keine dauerhafte Wahrheitswert-Assertion im neuen API-Test und keine deterministisch erzwungene Sperrwartephase im Nebenläufigkeitstest. H1 ist als Test zweier parallel gestarteter HTTP-Aufträge mit Status-, ETag- und Datenbankassertionen tragfähig; die Start-Latch beweist weiterhin keinen bestimmten Überlappungszeitpunkt in PostgreSQL.

**Entscheidungsvorlage:** T1 und T2 beheben und die beiden verspäteten Antworten anschließend gegen das korrigierte Sollverhalten prüfen. Erst danach die gemeinsame Abnahme von TASK-081, TASK-087 und TASK-089 durch Holger entscheiden.

> Hinweis zur Ablage (claude-20261102-branding-01, 8. November 2026): Holger hat diesen Bericht ohne die genannten Dateien (Links deshalb als Pfad gesetzt) unter `docs/review-evidence/task-089/` übergeben. Sie liegen dem Repository daher nicht bei; die Reproduktionen von T1 und T2 werden in TASK-090 als dauerhafte Browsertests nachgebildet.
