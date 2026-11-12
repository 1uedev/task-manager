# Unabhängige Prüfung TASK-031 / PR #134

**Ergebnis:** Auf dem geprüften Stand ist TASK-031 **technisch noch nicht abnahmefähig**. Zwei Befunde P2 betreffen die Aussagekraft der komplexen Suchmessung und eine automatische Wiederholung nicht idempotenter Anlagen. Zwei Befunde P3 betreffen die Sitzungsfristprüfung außerhalb von UTC und die Abgrenzung der Schreibmessung. Kein P1-Befund. Imagekonfiguration, Geheimnisübergabe, Nicht-Root-Benutzer, CSP und grundlegende Persistenz sind im geprüften Umfang plausibel beziehungsweise durch die unten getrennt ausgewiesenen Tests belegt. Keine fachliche Abnahme.

## Grundlage und Umfang

- Getrennte Codex-Sitzung `codex-20261022-task031-review-01`, 22. Oktober 2026; eigener Klon, Branch `review/task-031-pr-134`. Nur Bericht und eigene bereinigte Nachweise geändert; Produktcode, vorhandene Tests, TODO.md und DONE.md unverändert.
- [PR #134](https://github.com/1uedev/task-manager/pull/134), Kopf `6203269f9738aa312d9bbded7cfd4b57cf0c627e`, integriert als main `28becad967722994423db0bf5ab49ebdeb3a9c6f`. Die eigenen ausführenden Prüfungen beziehen sich auf diesen Integrationsstand. Der gelesene CI-Lauf gehört genau zum genannten PR-Kopf.
- Maßstab: [TASK-031](../TODO.md#task-031-docker-vorführung-und-nachweise-des-ersten-abschnitts-liefern), DEC-040/DEC-071, [OPERATIONS.md](OPERATIONS.md#lastprofil-und-vorgeschlagene-messziele), [TAGS_SEARCH.md](TAGS_SEARCH.md#messbare-zielvorschläge) und die zehn Nachweispunkte in [DEVELOPMENT_START.md](DEVELOPMENT_START.md#nachweise-für-diesen-abschnitt). TASK-031 steht beim Autor `claude-20261021-demo-01` BEREIT ZUR PRÜFUNG; Voraussetzung TASK-030 wartet ebenfalls auf Prüfung/Abnahme. Das Review übernimmt keine Implementierungsreservierung und akzeptiert diese Abhängigkeit nicht stillschweigend.
- Gelesen: AGENTS.md, CLAUDE.md, README.md, TODO.md, DONE.md, docs/AI_WORKFLOW.md, relevante Anforderungen/Entscheidungen/Roadmap, docs/TECHNICAL_BASELINE.md sowie sämtliche im Auftrag genannten Dateien und die zugehörigen Kopfzeilentests/Messdateien. Bezug: REQ-OPS-001/005/006, REQ-PERF-001/003, REQ-DEV-004. Numerische Leistungsziele bleiben vorgeschlagene, für diesen Versuch ausgewählte Vergleichswerte; kein neues Produktversprechen.

## Befunde nach Schwere

### F1 — P2: Die komplexe Suchklasse erzeugt überwiegend zwingend leere Kombinationen

**Fundstellen:** `tools/demo/demo.py:603–608`, `:652–658`; Vergleich `backend/src/main/java/taskmanager/search/internal/SearchService.java:180–183` und Projektkataloggrenzen des Tagvertrags. Aussage in `docs/DEMO.md:114`, Ergebnistabelle und „Alle Ziele eingehalten“ bei den Laststufen.

`prepare_user` sammelt Tagkennungen aus allen Mitgliedsprojekten und dem Unternehmenskatalog. `run_action` zieht unabhängig davon drei Suchprojekte und ein bis zehn Tags aus dieser gesamten Sammlung. Die Tags werden serverseitig mit **UND** verknüpft. Tags verschiedener Projektkataloge können nicht am selben Ticket vorkommen; ein Projekttag außerhalb der ausgewählten Suchprojekte kann ebenfalls keinen Treffer liefern. Das ist ein wesentlich anderer Lastfall als eine komplexe Suche mit tatsächlich zurückzugebenden, berechtigten Treffern.

**Eigene Gegenproben:**

1. Den unveränderten Generator mit der von `prepare_user` für `ref001` erzeugten Standardform ausführen: fünf Mitgliedsprojekte, je 50 zuerst geladene Projekttags und 50 Unternehmenstags. Bei festem Zufallsstart sind **8.914 von 10.000** erzeugten komplexen Abfragen allein wegen der Katalog-/Projektkombination zwingend leer; **8.331** enthalten Tags verschiedener Projektkataloge. Das ist eine Konstruktionsprüfung ohne Laufzeitmessung des Produkts.
2. Den originalen Referenzgenerator über die echte API auf einem ausdrücklich verkleinerten Bestand ausführen: vier Projekte, 200 anfängliche Tickets, zwölf zusätzliche Konten und 80 Tags. **91 von 100** anschließend durch das unveränderte `run_action` erzeugten komplexen API-Suchen haben null Treffer, alle antworten 200. Eine gezielt aus einem vorhandenen Ticket gebildete Kombination mit Wort, Projekt, Status, Typ und tatsächlich zugeordnetem Tag liefert dagegen Treffer.

**Auswirkung:** Eine erfolgreiche HTTP-Antwort wird als erfolgreicher Messwert gezählt, ohne Treffermenge oder Ergebnisfall festzuhalten. Die ausgewiesenen p95-Werte belegen daher die Laufzeit der erzeugten Abfragen, aber keine ausreichend geprüfte komplexe Suche mit Treffern und Antwortaufbereitung. Die gesonderte Messklasse „ohne Treffer“ ersetzt die fehlende Abgrenzung nicht. Aus den veröffentlichten Aggregaten lässt sich die Treffermischung des ursprünglichen 90.000-Ticket-Laufs nicht nachträglich bestimmen; die hier gemessenen Anteile werden diesem historischen Lauf nicht unterstellt. Eine tatsächliche Überschreitung des Zeitvergleichswerts wurde damit nicht bewiesen.

**Vorschlag:** Komplexe positive Fälle gezielt aus zulässigen, gemeinsam zugeordneten Tags vorhandener Tickets bilden, einschließlich ausreichend breiter Trefferfälle und erlaubter Projekt-/Feldfilter. Leere/selektive Fälle gesondert behalten. Erwartete Treffereigenschaften vor der Messung prüfen und je Klasse Treffermengen beziehungsweise Fallkennungen zusammen mit Laufzeit/Ergebnis erfassen. Betroffene Messklasse erneut messen; bis dahin die entsprechende Zielerfüllung in DEMO.md begrenzen.

**Nachweise:** [Generatorprobe](review-evidence/task-031/measurement_probes.py), [Ergebnisse](review-evidence/task-031/probe-results.txt), [echte HTTP-Proben](review-evidence/task-031/http_probes.py), [HTTP-Ergebnisse](review-evidence/task-031/http-results.txt).

### F2 — P2: Automatische Wiederholung eines POST kann eine Anlage doppelt ausführen

**Fundstelle:** `tools/demo/demo.py:96–109`; betroffen sind unter anderem `Builder.ticket` und die Anlagen von Gruppen, Konten und Tags.

`Client.call` wiederholt bei `RemoteDisconnected`, Verbindungsreset und ähnlichen Ausnahmen jeden Aufruf einmal mit neuer Verbindung. Die Ausnahme beweist jedoch nicht, dass der Server die Anfrage noch nicht verarbeitet hat. Anlagen haben keinen hier mitgeführten, serverseitig abgesicherten Einmaligkeitsnachweis.

**Eigene Versuche:** Ein lokaler HTTP-Gegenversuch akzeptiert eine Anlage und bricht erst vor Zustellung der Erfolgsantwort die Verbindung ab. Ein einziger `Client.call("POST", …)` führt zu zwei angenommenen Anlagen und meldet schließlich Erfolg. Zusätzlich wurde derselbe Fall mit einem eigenen Transporthelfer am **echten Backend** nachgestellt: Die erste vollständige 201-Antwort wird nach ihrem Empfang verworfen; der Originalclient wiederholt. Der Server bestätigt zweimal 201, und die anschließende öffentliche Ticketliste enthält **zwei verbindlich angelegte Tickets mit dem eindeutigen synthetischen Prüftitel**.

**Auswirkung:** Ein vorübergehender Transportfehler kann den Referenzbestand oder die Demo unbemerkt verdoppeln beziehungsweise bei eindeutig benannten Objekten eine bereits erfolgreiche Anlage nachträglich als Fehler erscheinen lassen. Der Schutz vor einer zweiten kompletten Befüllung verhindert diese Wiederholung innerhalb eines Aufrufs nicht. Keine solche Störung wird für die veröffentlichten Messläufe behauptet.

**Vorschlag:** Automatisch nur Aufrufe wiederholen, deren Wiederholung sicher ist. Bei nicht idempotenten Anlagen einen unklaren Ausgang sichtbar machen und den tatsächlichen Zustand gezielt abgleichen; alternativ einen abgestimmten serverseitigen Einmaligkeitsvertrag nutzen. Kein zusätzlicher API-Sonderweg. Regression „Commit erfolgreich, Antwort verloren“ erhalten.

**Nachweis:** Beide [Prüfhelfer](review-evidence/task-031/README.md) mit [echtem HTTP-Ergebnis](review-evidence/task-031/http-results.txt). Produktcode und Originalwerkzeug wurden für diese Proben nicht verändert.

### F3 — P3: Die Fristprüfung rechnet UTC-Zeitstempel als lokale Zeit

**Fundstelle:** `tools/demo/demo.py:511`.

`expiresAt` ist ein UTC-Zeitpunkt. `time.strptime` und `time.mktime` interpretieren ihn hier als lokale Zeit; für den Gegenwert wird dagegen `time.gmtime()` mit dessen Sommerzeitkennzeichnung durch `mktime` geschickt. Die Umrechnungen sind nicht gleichwertig.

**Eigene echte Ausführung:** Nach frischer Befüllung meldet das unveränderte `verify` unter mitteleuropäischer Sommerzeit **23,00 Stunden** und scheitert an genau dieser Prüfung. Die übrigen elf Prüfungen bestehen. Derselbe echte Sitzungswert ergibt bei UTC-bewusster Rechnung **24,00 Stunden**. Eine deterministische Gegenprobe mit genau 24 Stunden zwischen zwei UTC-Zeitpunkten reproduziert 23 Stunden. Im UTC-Container fällt der Fehler nicht auf; der gelesene CI-Lauf besteht dort.

**Auswirkung/Vorschlag:** Die außerhalb des Containers ebenfalls angebotene Python-Ausführung kann eine korrekte Sitzung fälschlich beanstanden. UTC-Zeitpunkte mit Zeitzone direkt von `datetime.now(timezone.utc)` abziehen oder Epochenwerte verwenden. Sommerzeit- und Zeitzonenfälle prüfen. Das ist ein Fehler im Prüfwerkzeug; ein falsches Sitzungsende des Servers wurde nicht festgestellt.

**Nachweis:** [Original-verify-Ergebnis](review-evidence/task-031/verify-results.txt), [UTC-Vergleich am echten Server](review-evidence/task-031/http-results.txt), [deterministische Rechnung](review-evidence/task-031/probe-results.txt).

### F4 — P3: Schreibmessung und Vergleichsziel sind nicht sauber vom beschriebenen Profil abgegrenzt

**Fundstellen:** `tools/demo/demo.py:576`, `:625–632`, `:666`; `docs/DEMO.md:113`, `:126`; Vergleich `docs/OPERATIONS.md:164`.

Die Fachaktion „Ticketänderung“ führt GET und PATCH aus. Der erfasste Messwert enthält ausschließlich die Laufzeit des PATCH. Eine kontrollierte Gegenprobe mit GET = 900 ms und PATCH = 10 ms ergibt **10 ms** als Messwert. Das ist als eigene PATCH-Metrik zulässig, muss aber vom gesamten beschriebenen Lese-/Änderungsablauf unterschieden werden.

Zudem setzt `TARGETS_MS` für diese Klasse `None`, und DEMO.md nennt „kein Ziel“. OPERATIONS.md enthält bereits den vorgeschlagenen Vergleichswert von **1 s für reguläre Speicherung**, den das Dokument als Grundlage seiner Auswahl nennt. Es fehlt eine explizite Abgrenzung dieser Ausnahme. Die Zahlen zeigen hier keine nachgewiesene Zielüberschreitung; betroffen sind Messbedeutung und Vollständigkeit der Bewertung.

**Vorschlag:** Die Spalte als PATCH-/Speicherzeit kennzeichnen und den ausgewählten Speichervergleichswert ausweisen oder dessen Ausschluss ausdrücklich begründen. Für eine Aussage zur gesamten Fachaktion zusätzlich deren vollständige Dauer erfassen. Die vorgeschlagenen Werte dadurch nicht zu zugesagten Produktgrenzen erklären.

## Übrige Prüfschwerpunkte

| Schwerpunkt | Ergebnis und Nachweisgrenze |
|---|---|
| Geheimnisse / Standardkennwörter | Keine echten Zugangsdaten oder Standardpasswörter in den geprüften neuen Quellen gefunden. `.env.example` enthält leere Werte; Compose verlangt beide Passwörter ohne Rückfallwert. Eigener `compose config`-Versuch ohne Werte scheitert, mit synthetischen Werten gelingt die Auflösung. Keine versionierte `.env`, Schlüsseldatei oder Sitzungsdatei gefunden; ergänzender Musterscan für private Schlüssel und verbreitete Tokenformate ohne Treffer. Das ist keine vollständige Geheimniserkennung über die gesamte Git-Historie. |
| Imageinhalt und Nicht-Root | Finalstufe übernimmt nur das gebaute JAR, root-eigen/0444, danach `USER 10001:10001`. Keine Passwort-ENV-/ARG-/COPY-Übergabe im Dockerfile. `.dockerignore` begrenzt den Kontext auf benötigte Quellen, schließt lokale Umgebungsdateien und Builds aus. Compose überschreibt den Anwendungsbenutzer nicht. Eigenes `docker inspect`, Schichtscan und `id` im Container waren mangels startfähiger lokaler Docker Engine nicht möglich; die Aussage beruht insoweit auf Quellen und dem gelesenen Buildnachweis. |
| CSP / Cache | Der Filter steht vor dem Authentifizierungsfilter und setzt CSP, `nosniff`, `no-referrer`, COOP. Keine Inline-/Eval-Freigabe für Skripte. Dynamisch gesetzte Unternehmensvariablen werden nicht als untrusted Skript eingebunden. Assets bekommen lange unveränderliche Cachefrist, HTML `no-cache`; der Identitätsfilter erhält API-`no-store` auch bei Ablehnungen. Drei neue echte HTTP-Tests unter Java 25/PostgreSQL 18.6 selbst erfolgreich ausgeführt. CI prüft außerdem das tatsächlich gepackte HTML/JS. Kein eigener neuer Browserlauf gegen das Image. |
| Compose / Persistenz | Eigene Konfigurationsauflösung bestätigt App-Bindung an 127.0.0.1, keinen veröffentlichten Datenbankport und das benannte Volume am PostgreSQL-18-Pfad `/var/lib/postgresql`. Der gelesene CI-Lauf entfernt die Container ohne das Volume, startet neu und bestätigt dieselbe gültige Sitzung mit gleichem Endzeitpunkt. Zusätzlich eigener nativer Backendneustart mit unveränderter Datenbank und erfolgreichem Original-`verify --resume`. Diese Zusatzprobe ist kein eigener Docker-Volume-Test. |
| Nur öffentliche API | Das Werkzeug verwendet `/api/v1` über HTTP, dieselben Sitzungs-/CSRF-Verfahren und Fachrechte; keine SQL-Verbindung, fremden Tabellen oder internen Befüllports. Lokale Dateioperationen betreffen Messausgabe, den ausdrücklich gewählten Sitzungsnachweis und Maschinenmetadaten aus `/proc`. Originale Demo- und verkleinerte Referenzbefüllung selbst gegen die API ausgeführt. Die zweite Demobefüllung wird vor neuer Anlage wegen vorhandener Daten abgelehnt. F2 betrifft die unsichere Wiederholung dieses öffentlichen API-Wegs. |
| Daten-/Lastprofil | Der Referenzgenerator bildet vorhandene Objekte mit Projektmitgliedschaften und drei verschiedenen Feldsichten ab; Standard: 20 Projekte, 90.000 Tickets, 5.000 Tags, ungefähr fünf Zuordnungen je Ticket und zehn je Projekt. Er erzeugt 100 zusätzliche Konten plus Bootstrap-Konto; die Ergebnisdatei weist entsprechend 101 Konten aus. Die Formulierung „100 Konten“ in DEMO.md sollte diese Zählweise nennen. Such-/Lese-/Schreibanteile skalieren die fehlenden Fachaktionen und verteilen den Schreibanteil der Leser ausdrücklich um. Denkzeit, Aufwärmen, drei Laststufen und Einschränkungen sind beschrieben. Keine eigene Wiederholung des 90.000-Ticket-/30-Minuten-Laufs. |
| p50/p95 / Fehler | Nearest-rank-Berechnung selbst mit festem Wertevektor geprüft. Alle drei veröffentlichten JSON-Aggregate haben konsistente Klassen-/Ergebnissummen, Perzentilreihenfolge, Fehlerrate, Durchsatz und Zielkennzeichnung. Originale Einzelbeobachtungen sind nicht enthalten; deren p50/p95 konnten daher nicht unabhängig nachgerechnet werden. Der Ausdruck „Rohdaten“ in DEMO.md:144 sollte „aggregierte Ergebnisse“ heißen oder um anonymisierte Einzelmessungen ergänzt werden. F1 begrenzt die inhaltliche Aussage trotz rechnerisch konsistenter Zahlen. |
| Aussagen in DEMO.md | Grenzen zu TLS, Sicherung/Restore, Hochverfügbarkeit, vollständigem V1-Suchumfang, Browserzeit, Hardwareübertragbarkeit und Wettbewerbsvergleich sind ausdrücklich benannt. Der Überlastlauf nennt seine Zielverfehlungen. Zwei Stunden und Hintergrundlast werden nicht als ausgeführt ausgegeben. Die positive Bewertung komplexer Trefferfälle und die Schreibbewertung müssen gemäß F1/F4 begrenzt werden. Die zehn Startpunkte verweisen überwiegend auf andere Tasks; diese Verweise sind kein neues Review/keine Abnahme von TASK-030. |

## Tatsächlich ausgeführte Prüfungen

- Eigenes `-Dtest=WebInterfaceHeadersTest package` mit Java 25.0.4.1+1, Maven 3.9.16 und PostgreSQL 18.6: **3 Tests, 0 Fehler**, Paketierung erfolgreich. Keine erneute vollständige Backend-/Frontend-Suite behauptet.
- Originales `seed --profile demo`: **4 zusätzliche Konten, 4 Projekte, 43 Tickets**. Originales `verify --save`: **11/12 erfolgreich**, Fristprüfung scheitert wegen F3. Nach eigenem Backendneustart originales `verify --resume`: **3/3 erfolgreich**. Eine frühere Zweitbefüllungsprobe fiel mit dem Neustart zusammen und traf kurz keinen Server; nach abgeschlossenem Start wiederholt und die beabsichtigte Datenbestandsablehnung eindeutig bestätigt.
- Originales `seed --profile reference` mit bewusst kleiner Konfiguration: **12 zusätzliche Konten, 4 Projekte, 200 Tickets, 80 Tags, 984 Ticket-Tagzuordnungen**. Funktionale Gegenprobe, keine Ersatzmessung für den großen Referenzbestand.
- Originales `measure` kurz auf diesem kleinen Bestand: drei Benutzer, zwei Sekunden Aufwärmen, zehn Sekunden Messung, **265 Werte in zwölf Klassen, keine technischen Fehler**. Nur Ausführungs-/Exportprüfung; daraus keine Leistungsfreigabe abgeleitet.
- Eigene deterministische Generator-, Rechen- und Wiederholungsproben sowie 100 echte komplexe Suchabfragen und der echte doppelte POST-Fall; Dateien unter [review-evidence/task-031](review-evidence/task-031/README.md).
- `docker compose config` ohne/mit synthetischen Pflichtwerten selbst geprüft. Eigener Containerbuild und eigener Lauf von `tools/demo/image-check.sh` **nicht ausgeführt**: der lokale Docker-Client meldet „Docker Desktop is unable to start“. Keine Engine-/Betriebssystemeinstellung verändert.
- Stattdessen für den exakten PR-Kopf den erfolgreichen [Image-CI-Lauf 39666854242](https://github.com/1uedev/task-manager/actions/runs/39666854242) samt Jobprotokoll gelesen: vollständiger mehrstufiger Build, Start ohne Einstellungen verweigert, HTML-/Assetkopfzeilen, 43 Demotickets, zwölf Prüfungen, Neustart mit gültiger Sitzung und gleichem Endzeitpunkt. [Auszug](review-evidence/task-031/ci-excerpt.txt). Das ist gelesene fremde Ausführung, keine eigene.
- Repository-Validator, `task_board.py --check`, **25 Python-Regressionstests**, `git diff --check` erfolgreich; vor Veröffentlichung für den fertigen Bericht nochmals geprüft.

## Technische Übergabe

F1 und F2 vor technischer Freigabe beheben und gezielt nachprüfen. F3/F4 korrigieren beziehungsweise ihre Grenzen ausdrücklich mit der Koordination festhalten. Die gut abgegrenzte lokale Docker-Vorführung wird dadurch nicht zur verkaufbaren V1 oder zum Betriebs-/Restore-Nachweis. TODO.md und DONE.md bleiben unverändert; Holgers fachliche Abnahme bleibt offen.
