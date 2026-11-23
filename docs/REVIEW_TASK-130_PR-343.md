# Prüfbericht zu TASK-130 / PR #343

**Empfehlung: nachbessern.** Beim Verwerfen eines Skillentwurfs kann ein bereits ungültiger Probelauf wieder mit Inhalt ausgeliefert werden. Ein zulässiger rein numerischer API-Schlüssel kann über die Tokenzählung im Laufprotokoll erscheinen. Beide Befunde sind vor der Abnahme zu beheben.

Erstprüfung am 18. November 2026, Gegenprüfung am 23. November 2026. Repository 1uedev/task-manager, Branch claude/project-thread-av1na6, Ergebnis-Head d93ce025b4899b7f47a397dbb5020e72eb9785e8, PR-Basis 1c995d1e05d4280b184f31643830e3976b7c4086. Die ausgewerteten CI-Logs nennen den Checkout 7239548f97666a1661f3caa28e8c542aedf28c5b ausdrücklich als Merge dieses Heads in diese Basis. Die nachstehenden Zeilen beziehen sich auf den Ergebnis-Head. In der Gegenprüfung stimmten die Blob-Hashes aller 22 geänderten Dateien mit dem PR-Diff überein.

Maßstab: AGENTS.md, docs/DEVELOPMENT_REVIEW.md, [Vorgängerbericht](REVIEW_TASK-129_PR-341.md), TASK-130 mit Regeln 1 bis 9, Abnahmekriterien und Übergabe, TASK-125/TASK-129, DEC-160 sowie der Block „Nachbesserung (TASK-130, DEC-160)“ in backend/README.md. Geprüft wurden der PR-Diff und die betroffenen Aufruf-, Speicher-, Lese- und Testwege. Produktcode und vorhandene Tests blieben unverändert. Zusätzliche Prüfprogramme und Varianten mit entfernten Korrekturen lagen außerhalb des geprüften Quellbaums. Es gab keine Aufrufe gegen echte KI-Anbieter.

## Befunde vor der Abnahme

### F1 – Mittel – Verwerfen eines Entwurfs macht einen alten Probelauf erneut gültig

- **Datei, Zeile:** backend/src/main/java/taskmanager/ai/internal/SkillService.java, Zeile 121 (discardDraft) und Zeile 173 (forTrial); backend/src/main/java/taskmanager/ai/internal/RunService.java, Zeile 320.
- **Szenario:** Ohne eigenen Entwurf einen Probelauf mit dem eingebauten Skill erfolgreich abschließen. Dieselbe Lauf-ID lesen: Inhalt vorhanden. Anschließend einen Entwurf speichern: Die alte Lauf-ID liefert korrekt content: null und invalid: ai.configuration_changed. Diesen Entwurf mit seinem aktuellen If-Match verwerfen und dieselbe alte Lauf-ID nochmals lesen.
- **Beobachtete Folge:** Der alte Inhalt erscheint wieder; invalid ist erneut null. forTrial fällt auf den geltenden Skill zurück. Dessen Auswahlzähler wurde weder beim Anlegen noch beim Verwerfen des Entwurfs erhöht. Damit kehrt für den Probelauf der vollständige frühere Stempel zurück. Der Zähler für Aktivierungen selbst fällt nicht zurück; die Lücke betrifft die zusätzliche Auswahl des Entwurfs durch Probeläufe.
- **Gegenprobe:** Ein zusätzlicher Integrationstest gegen den unveränderten Produktcode, echtes PostgreSQL 18.6 und den lokalen Ollama-Testserver reproduzierte alle drei Lesestände an derselben Lauf-ID. Die letzte Prüfung auf weiterhin fehlenden Inhalt scheiterte mit einer erfolgreichen Serverantwort einschließlich des alten Vorschlags. Dafür wurde kein neuer Probelauf gestartet. Der vorhandene Test returningToTheBuiltInSkillNeverMakesAnOldSuggestionValidAgain prüft Aktivieren/Restore eines fachlichen Laufs und erfasst diesen Weg nicht.
- **Bezug:** Der Aktivierungsfall des bisherigen F3 ist behoben. Das zugrunde liegende Wiedererscheinen nach einer Konfigurationsänderung bleibt beim ausdrücklich mitzuprüfenden Verwerfen offen. Es betrifft administrative Probeläufe; ein Wiedererscheinen fachlicher Läufe durch bloße Entwurfsbearbeitung wurde hier nicht festgestellt.
- **Vorschlag:** Auch die für Probeläufe geltende Auswahl mit einer monotonen Revision versehen. Wechsel zum Entwurf und Rückkehr nach Verwerfen müssen unterscheidbar bleiben. Das kann eine eigene Probelaufrevision sein, damit bloße Entwurfsarbeit keine fachlichen Vorschläge unnötig entwertet. Den beschriebenen Ablauf mit derselben alten Lauf-ID dauerhaft als Integrationstest aufnehmen.

### F2 – Niedrig – Numerische API-Schlüssel können in den Laufnachweis gelangen

- **Dateien und Zeilen:** backend/src/main/java/taskmanager/ai/internal/ConfigService.java (494), AiAdapters.java (103, 132, 169), RunService.java (393–402, 451, 463), AiStore.java (295) und backend/src/main/java/taskmanager/ai/web/AiController.java (73).
- **Szenario:** Die Schlüsselprüfung erlaubt rein numerische Schlüssel. Gibt ein Anbieter einen solchen Schlüssel als Tokenzahl zurück, übernimmt der Adapter diese Zahl in den Laufnachweis. Das passiert auch, wenn derselbe Schlüssel im Vorschlag erkannt wird und der Lauf mit ai.invalid_output endet. Die Abfrage des eigenen Laufs gibt die gespeicherte Tokenzahl aus.
- **Gegenprobe:** Der echte OpenAI-kompatible Adapter verarbeitete eine synthetische Antwort aus einem HTTP-Testdoppel. Der echte Ausführer verwarf den Vorschlag und übergab die numerische Wiederholung des Schlüssels als outputTokens an die nachgebildete Speicherschnittstelle. Der SQL- und Controllerpfad für diesen Wert wurde im Quellcode geprüft.
- **Vorschlag:** Bei einem erkannten Schlüsseltreffer die vom Anbieter gemeldeten Zähler verwerfen. Auch bei übrigen Abschlüssen Tokenzahlen vor dem Speichern mit dem verwendeten Schlüssel abgleichen. Die Gegenprobe sollte einen zulässigen numerischen Schlüssel sowie unverfängliche Tokenzahlen abdecken.

## Hinweise für später

### H1 – Die Prüfung im Ausführer braucht einen eigenen dauerhaften Regressionstest

- **Datei, Zeile:** backend/src/test/java/taskmanager/ai/AiApiTest.java, Zeile 1481; backend/src/test/java/taskmanager/ai/internal/AiInternalsTest.java, Zeile 40; backend/src/main/java/taskmanager/ai/internal/RunService.java, Zeile 385.
- **Szenario und Folge:** Der Integrationstest hält das Lesen des Auswahlzählers. Der JDBC-Treiber reagiert dort bereits auf die Unterbrechung. Der Unit Test prüft dagegen den Einstieg in AiHttp. Zusammen isolieren diese beiden Tests nicht die zusätzliche Prüfung unmittelbar vor adapters.complete. Das ist in der Übergabe zutreffend offengelegt.
- **Gegenprobe:** Die unabhängige Prüfprobe hielt gezielt ConfigService.key an, brach den Lauf ab und gab die Schlüsselphase mit erhaltenem Interrupt frei. Der unveränderte Ausführer rief den nachgebauten Adapter nicht auf. Nach Entfernen ausschließlich seiner Abbruchprüfung schlug diese Probe wegen des Adapteraufrufs fehl. Der vorhandene Integrationstest mit Auswahlsperre blieb bei derselben Entfernung erfolgreich.
- **Vorschlag:** Die isolierte Schlüsselphasenprobe in die reguläre Testsuite übernehmen, getrennt vom HTTP-Test. Für die aktuelle Codebewertung ist die Wirkung unabhängig nachgewiesen; die bestehende Suite schützt diesen Teil der Korrektur allein noch nicht.

### H2 – Zwischen HTTP-Einstiegsprüfung und Dispatch bleibt ein kooperatives Abbruchfenster

- **Datei, Zeile:** backend/src/main/java/taskmanager/ai/internal/AiHttp.java, Zeilen 144, 148 und 151.
- **Szenario und Folge:** Der Interrupt wird vor client(provider) geprüft. Trifft er erst während des anschließenden Holens oder Erzeugens des Clients ein, folgt ohne weitere Prüfung sendAsync. Gerade der erste Aufbau kann zusätzlich den TLS-Vertrauenskontext erzeugen. Die Korrektur verhindert zuverlässig den schon beim Eintritt gesetzten Interrupt; sie garantiert keine atomare Grenze bis zum ersten gesendeten Byte.
- **Gegenprobe und Grenze:** In einer getrennten Probe wurde während des Clientzugriffs der Interrupt gesetzt. Der nachgebaute HttpClient erhielt danach einmal sendAsync mit gesetztem Interrupt; anschließend endete der Aufruf mit ai.cancelled. Das belegt den Dispatch-Pfad, keinen tatsächlichen Versand von Netzwerkbytes. Es wurde kein echter Anbieter angesprochen.
- **Vorschlag:** Nach der Clientbeschaffung unmittelbar vor sendAsync nochmals prüfen und die kooperative Grenze ausdrücklich beibehalten. Eine absolute Zusage „kein Byte nach gleichzeitigem Abbruch“ folgt auch daraus nicht. Dieser später eintreffende Abbruch ist vom ursprünglichen F4 mit bereits gesetztem Interrupt vor dem Aufruf zu unterscheiden.

### H3 – Der Browser-Testersatz meldet einen gesperrten Zugang bei den Prüfrouten als erfolgreich

- **Dateien und Zeilen:** frontend/tests/browser/backend.mjs (3587, 3716, 3744), frontend/tests/browser/ai.test.mjs (184) und backend/src/main/java/taskmanager/ai/internal/ConfigService.java (152–158, 239–246).
- **Szenario und Folge:** Die Testdaten setzen addressRefused am Zugang. Der Status zeigt ai.address_not_allowed. Beim anschließenden Prüfen von Zugang oder Modell meldet der Testersatz ohne separat hinterlegte Antwort trotzdem ok: true. Der Server meldet in beiden Fällen ok: false, code: ai.address_not_allowed und models: null. Die vorhandene Browserprobe deckt nur die Statusanzeige ab.
- **Gegenprobe:** Den unveränderten Testersatz über seinen registrierten Request-Handler mit synthetischem Zugang und Modell aufgerufen. Der Status meldete die Sperre. Beide Prüfrouten lieferten HTTP 200 mit ok: true.
- **Vorschlag:** Dieselbe hinterlegte Adresssperre auch für beide Prüfrouten verwenden und deren Fehlermeldungen im Browser prüfen.

## Abgleich mit TASK-129 und den besonderen Prüffragen

Die Kennungen in dieser Tabelle gehören zum Vorgängerbericht; F1, F2 und H1 bis H3 oben gehören zu diesem Bericht.

| Vorgänger | Ergebnis |
|---|---|
| F1 – heutige Adressregeln | Behoben. ConfigService.addressAllowed verlangt eine bereits kanonische gespeicherte Adresse, verhindert bekannte externe Standardhosts mit lokalem Datenweg und prüft die HTTP-Freigabe. key prüft dies vor der Entschlüsselung, auch ohne gespeicherten Schlüssel; availability sperrt den Start. Zugangsprüfung und Modellprüfung benutzen diesen Schlüsselpfad, dessen Adapteraufruf die Modellliste abruft. Fachlicher Lauf und Probelauf passieren Verfügbarkeit und Schlüsselpfad. Damit sind auch Zeilen mit einem durch V0068 zunächst passend gesetzten Zielhash erfasst. Unzulässige Bestandszeilen und gültige Kontrollen wurden geprüft; kein verbleibender Umgehungsweg wurde gefunden. |
| F2 – Schlüssel in endgültigen Werten | Für den Inhalt des Vorschlags behoben. Nach der Formatprüfung untersucht AiFormats.mentions die dekodierten und bereinigten Zeichenketten, Feldnamen und beliebig verschachtelten Objekt-/Arrayelemente. JSON-Escapes und durch Bereinigung zusammengefügte Schlüssel führen zu ai.invalid_output; es wird kein Vorschlag gespeichert. Zusätzliche Feldnamen scheitern außerdem am strikten Formatschema. Die Anwendung speichert keine Anbietertexte zum Fehler. Anbieterbestimmte numerische Felder bleiben vom Schutz ausgenommen, siehe neuer Befund F2. |
| F3 – monotone Auswahl | Aktivieren, das dabei erfolgende Archivieren der bisherigen aktiven Version und Restore sind unter der Skillsperre serialisiert. V0069 und das atomare UPSERT erhöhen den Zähler; dieselbe frühere Auswahlrevision kehrt dabei nicht zurück. Alte Stempel ohne @ passen auch bei Zähler null nicht mehr und werden als geändert behandelt. Verworfene Entwurfsnummern bleiben belegt. Für Probeläufe besteht jedoch der neue Befund F1 beim Wechsel zum Entwurf und zurück. |
| F4 – bekannter Abbruch vor Aufruf | Die beiden neuen Prüfungen wirken an ihren jeweiligen Eintrittspunkten. Die unabhängige Schlüsselphasenprobe belegt die Prüfung im Ausführer zusätzlich zum HTTP-Test. Benutzerabbruch unterbricht den Future; der Konfigurationsabbruch setzt außerdem seinen Grund. Beide erreichen die gemeinsame Abbruchbehandlung. Als dauerhafter Testnachweis reichen Integrationstest und HTTP-Unit-Test für die Ausführerprüfung allein nicht aus: neuer Hinweis H1. Zur Grenze bis zum tatsächlichen Dispatch siehe H2. |
| F5 – Codeblock | Behoben. Nach Entfernen des einen äußeren Blocks entscheidet der strikte Parser über den gesamten Inhalt. Backticks innerhalb gültiger JSON-Strings bleiben erhalten. Fremdtext davor/dahinter, ein zweiter äußerer Block, ein zweites JSON-Objekt und doppelte Schlüssel werden verweigert. Positive und negative Gegenproben waren erfolgreich; die alte pauschale Backtick-Suche lässt den positiven Fall wieder scheitern. |
| F6 – verworfene Versionen | Die offengelegte Abweichung ist technisch konsistent und vertretbar: AiStore.skillVersions schließt discarded aus; Vertrag und erzeugte Typen beschreiben genau diese Auslassung und erlauben weiterhin nur draft, active, archived. Die Seite benötigt deshalb keine vierte sichtbare Statusvariante. Beide Sprachen enthalten vorsorglich die Übersetzung. Der Testersatz listet den aktuellen Entwurf und entfernt ihn nach Verwerfen; die Browserfolge wurde auf Deutsch und Englisch geprüft. Die internen Zeilen bleiben zur Sicherung der Nummern erhalten. Der ursprüngliche Wortlaut von Regel 6 wird damit bewusst ersetzt; Holgers fachliche Abnahme wird durch diese technische Bewertung nicht vorweggenommen. |
| F7 – getrenntes Fachrecht | Behoben. Guard.readable ist unabhängig von Guard.valid; der zusätzliche Lesefall belässt Quelle und ai.use gültig und erhält ohne Fachrecht keinen Inhalt. Das entspricht dem delegierten Lesevertrag des Produktcodes. Der Testcontroller bildet die noch ausstehenden Fachmodule nicht vollständig ab; deren tatsächliche Rechteprüfung bleibt TASK-126/TASK-127. |
| H1 – IPv6 | Behoben. Kein DNS-Aufruf: Gruppen werden numerisch gelesen, kleingeschrieben, führende Nullen entfernt und die längste Nullengruppe komprimiert; IPv4-Endungen werden zu zwei Gruppen. AiProperties verwendet dieselbe Form für die Hostfreigabe. 3.012 unabhängig mit Python ipaddress vorbereitete Schreibweisen einschließlich Großschreibung, ausgeschriebener Gruppen, Nullengruppen und IPv4-Endung ergaben dieselbe Ziel- und Freigabeform. Ungültige Literale werden verweigert. |
| H2 – äußere Transaktion | Behoben. Der Test schreibt zuerst einen äußeren Marker und löst dann einen berechtigten, aber nicht zugeordneten Fachstart aus. Der Marker wird zurückgerollt; der inhaltsfreie refused-Datensatz bleibt durch REQUIRES_NEW bestehen. Wird nur diese Transaktion auf REQUIRED geändert, verschwindet der Nachweis mit dem Rollback und die Abfrage des Tests findet keine Zeile mehr. |

Der Routenabgleich von AiContractTest ist erfolgreich. Die ergänzten Fehlercodes und die Beschreibung der Versionsliste stimmen mit den geprüften Serverantworten und erzeugten Typen überein. Die Browserproben prüfen den Testersatz, die Backendproben den Server; erst beide gemeinsam stützen den Abgleich. Die Abweichung der beiden Prüfrouten ist unter H3 dokumentiert.

## Ausgeführte Prüfungen

### Erstprüfung am 18. November 2026

| Prüfung | Ergebnis |
|---|---|
| python3 scripts/validate_repository.py | Erfolgreich; unter Windows mit dem verfügbaren Python-Interpreter ausgeführt. |
| python3 scripts/task_board.py --check | Erfolgreich, Übersicht aktuell. |
| python3 -m unittest discover -s tests -v | 50 Tests, keine Fehler; ein ausdrücklich nur unter Unix ausführbarer Zeitzonenfall unter Windows übersprungen. |
| Backend: AiApiTest, AiInternalsTest, AiRecordsPageTest, AiContractTest, ModuleStructureTest, DatabaseFoundationTest, ApplicationLifecycleTest | Lokal erfolgreich: 46 Tests, keine Fehler, nichts übersprungen. Java 25.0.4.1, PostgreSQL 18.6 als eigener nativer Testserver über den dokumentierten JDBC-Override. Enthält Migration und Neustart mit 66 Migrationen bis V0069. |
| frontend/npm run verify | Erfolgreich: Erzeugung der Typen ohne verbleibenden Diff, Typecheck, 102 Tests und Produktionsbuild. Vorhandene Bundler-Hinweise sind keine Testfehler. |
| Browser: node --test tests/browser/ai.test.mjs | Alle neun Tests erfolgreich, nichts übersprungen; lokales Chrome, deutsche und englische Abläufe. |
| backend/scripts/lifecycle-check.sh | Lokal nicht ausgeführt: kein laufender Docker-Daemon und keine nutzbare Linux-Shell für diesen Lauf. Der konkrete Shell-Lifecycle-Nachweis aus der CI wurde anhand der Joblogs geprüft und ist erfolgreich. Der lokale ApplicationLifecycleTest ist davon getrennt ebenfalls grün. |
| git diff --check, Abgleich der geprüften Quellen | Erfolgreich; vorhandene Quellen unverändert. Die Varianten wurden separat kompiliert und über ihren Klassenpfad geladen. |

Der zunächst versuchte Backendlauf mit Testcontainers war wegen des fehlenden Docker-Daemons nicht ausführbar. Der abschließende native PostgreSQL-Lauf ist ein eigenständiger erfolgreicher Nachweis, kein Nachweis für das gepinnte Containerimage. Dafür liegen die geprüften CI-Ergebnisse vor:

- Backend einschließlich Lifecycle: 730 Tests, null Fehler, null übersprungene Tests; Lifecycle check passed, 66 Migrationen bis V0069, Start/Stop/Neustart der gepackten Anwendung.
- Frontend: verify mit 102 Tests und 319 erfolgreichen Browsertests, nichts übersprungen.
- Repositoryprüfungen: erfolgreich. Die lokalen Ergebnisse oben wurden unabhängig davon erhoben.

### Gegenprüfung am 23. November 2026

| Prüfung | Ergebnis |
|---|---|
| Repository-Validierung, Aufgabenübersicht und git diff --check | Erfolgreich. |
| Python-Tests | 50 Fälle im UTF-8-Modus. Neun Fehler und eine fehlgeschlagene Assertion in den lokalen HTTP-Gegenproben (WinError 10013), ein Unix-spezifischer Fall übersprungen. |
| Backend-Auswahl mit isoliertem PostgreSQL 18.6 | Der Testserver startete. Der lokale Zugriff darauf sowie Java-Loopback-Verbindungen wurden verweigert. 10 Testfälle bestanden, 37 endeten beim Aufbau der Testumgebung oder infolge des fehlgeschlagenen Aufbaus mit Fehler. |
| npm run verify | Erfolgreich mit 102 Tests, Typprüfung und Produktionsbuild. |
| node --test tests/browser/ai.test.mjs | Alle neun Fälle endeten beim Laden von localhost mit ERR_NETWORK_ACCESS_DENIED. Die zusätzliche direkte Gegenprobe am Testersatz brauchte keine Netzwerkverbindung. |
| backend/scripts/lifecycle-check.sh | Docker-Daemon weiterhin nicht erreichbar. Für den Shell-Lifecycle gilt der CI-Nachweis der Erstprüfung. |

Alle sieben unabhängigen Java-Prüfgruppen ohne Netzwerk liefen erneut erfolgreich. In acht separat kompilierten Varianten scheiterte jeweils die zugehörige Gegenprobe nach Entfernen der Korrektur. Für F2 und H3 kamen neue Gegenproben mit synthetischen Daten hinzu. Die PostgreSQL-Gegenprobe aus F1 ist mit dem Ergebnis und dem Zeitpunkt ihrer Erstprüfung dokumentiert.

## Gegenproben durch Entfernen der Korrektur

Die Entfernung erfolgte ausschließlich in getrennten Prüfkopien. Ein Scheitern zählt hier nur, wenn es die beabsichtigte Verhaltenserwartung betrifft; anfängliche Probleme des lokalen Prüfaufbaus wurden vorher behoben.

| Entfernte Korrektur | Beobachtung |
|---|---|
| Adressprüfung in key, getrennt davon in availability | Beide unabhängigen Negativproben scheitern jeweils; der unerlaubte Zugang wird nicht mehr rechtzeitig verweigert. Die Adapter waren nachgebaut, keine fremden Ziele wurden aufgerufen. |
| Prüfung der dekodierten/bereinigten Ausgabe in RunService | Die Schlüsselgegenprobe scheitert. |
| Auswahlzähler im Skill-Stempel | Sowohl die unabhängige Leseprobe als auch der neue ursprüngliche API-Test mit derselben Lauf-ID scheitern nach Restore. |
| Abbruchprüfung im Ausführer | Die isolierte Schlüsselphasenprobe scheitert; der bestehende API-Test mit Auswahlsperre bleibt erfolgreich. |
| Interruptprüfung am HTTP-Einstieg | Die unabhängige Dispatch-Gegenprobe scheitert: sendAsync wird trotz vorher gesetzten Interrupts aufgerufen. |
| Weglassen der alten pauschalen Backtick-Suche rückgängig gemacht | Die gültige Antwort mit Backticks in einer JSON-Zeichenkette wird wieder verweigert; Positivprobe scheitert. |
| Ausschluss von discarded aus der Versionsliste | aDraftRevisionIsNeverUsedTwice scheitert am ausgegebenen, laut Vertrag unzulässigen Zustand. |
| readable aus dem Testguard entfernt | contentNeedsTheGuardOfItsUseCase scheitert gezielt am neuen Fachrechtefall. |
| IPv6-Kanonisierung wieder durch bloße Kleinschreibung ersetzt | Der Vergleich gleichwertiger IPv6-Schreibweisen scheitert. |
| REQUIRES_NEW des Ablehnungsnachweises durch REQUIRED ersetzt | Der äußere Rollback entfernt den Nachweis; der neue API-Test scheitert mit leerem Abfrageergebnis. |

Zusätzlich scheitert die neue Entwurfs-/Verwerfungsprobe bereits mit sämtlichen Korrekturen des PRs am oben beschriebenen F1. Die grünen bestehenden Tests widerlegen diesen zusätzlichen Fehlerfall deshalb nicht.

## Empfehlung

Nachbessern. F1 mit derselben alten Probelauf-ID absichern und F2 für die im Laufnachweis gespeicherten Tokenzahlen schließen. Die Auslassung verworfener Entwurfsstände aus der Versionsliste ist als dokumentierte Abweichung zu Regel 6 technisch vertretbar. Die fachliche Abnahme dieser Abweichung liegt bei Holger.
