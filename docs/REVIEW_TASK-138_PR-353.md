# Prüfbericht zu TASK-138 / PR #353

**Empfehlung: abnehmen.** F1, F2 und H1 bis H3 der Prüfung von TASK-130 sind nach den Regeln 1 bis 5 von TASK-138 behoben. Die zugehörigen Gegenproben scheitern jeweils gezielt, wenn die Korrektur in einer separaten Prüfkopie entfernt wird. Im beauftragten Umfang wurde kein neuer abnahmeverhindernder Befund festgestellt.

Unabhängige technische Nachprüfung durch Codex, Sitzung codex-20261123-task138-review, am 23. November 2026. Repository 1uedev/task-manager, PR #353, Branch claude/project-thread-av1na6, geprüfter Head f411ef26f4e8440b38b34c53446e74166926ea71, Basis 3fbbba2bb91586b8f88f090b423f5b23e609a8cd. Der Head wurde vor Abschluss nochmals abgeglichen. Die Zeilenangaben beziehen sich auf diesen Head.

Maßstab: AGENTS.md, docs/DEVELOPMENT_REVIEW.md, [Prüfung von TASK-130](REVIEW_TASK-130_PR-343.md), TASK-138 einschließlich Regeln, Abnahmekriterien und Übergabe, DEC-168 und der Block „Nachbesserung (TASK-138, DEC-168)“ in backend/README.md. Geprüft wurden alle 13 geänderten Dateien sowie die betroffenen Aufruf-, Speicher-, Lese- und Vertragswege. Keine Migration gehört zum Diff.

Produktcode und vorhandene Tests wurden nicht verändert. Zusatztests und Varianten mit entfernten Korrekturen wurden außerhalb des geprüften Quellbaums separat kompiliert beziehungsweise ausgeführt. Alle Anbieterantworten waren synthetisch; es gab keine Aufrufe gegen echte KI-Anbieter.

## Befunde vor der Abnahme

Keine neuen Befunde F1, F2 … im geprüften Umfang. Die beiden Befunde des Vorgängerberichts sind geschlossen; die Begründung und Gegenproben folgen unten.

## Hinweise für später

Keine neuen technischen Hinweise H1, H2 … im geprüften Umfang. Die drei Hinweise des Vorgängerberichts sind geschlossen.

Die bereits dokumentierte fachliche Entscheidung zur Auslassung verworfener Entwurfsstände aus der Versionsliste bleibt bei Holger. Die kooperative Abbruchgrenze bleibt ausdrücklich bestehen. Beides wird durch diese technische Empfehlung nicht in eine bereits erfolgte Fachabnahme umgedeutet.

## Nachprüfung der fünf Regeln

### Regel 1 / bisheriger F1: Alte Probeläufe kehren nicht zurück

Orte: backend/src/main/java/taskmanager/ai/internal/SkillService.java, Zeilen 42–62 und 178–192; AiStore.java, Zeilen 130–169 und 182–201; RunService.java, Zeilen 291–325 und 531; backend/src/test/java/taskmanager/ai/AiApiTest.java, Zeile 1491.

Der fachliche Stempel bleibt label@selection. Nur forTrial ergänzt die höchste vergebene Entwurfsnummer des konkreten Unternehmens, Anwendungsfalls und der Sprache. Die Abfrage schließt verworfene und archivierte Stände ein. Verwerfen löscht die Zeile nicht. Das nächste Anlegen verwendet eine größere Nummer; eine weitere Speicherung desselben Entwurfs erhöht dessen Revision und damit sein draft-n.revision-Label. Aktivieren und Wiederherstellen erhöhen zusätzlich den Auswahlzähler. Die Schreibwege sind durch dieselbe Skillsperre serialisiert.

Der dauerhafte Test liest dieselbe alte Lauf-ID vor dem Entwurf, mit Entwurf und nach dessen Verwerfen. Der Inhalt bleibt nach dem Verwerfen gesperrt. Entfernt man ausschließlich den #drafts-Zusatz aus stampLabel, scheitert genau die letzte Erwartung: Der alte Inhalt erscheint wieder. Damit schützt der Test den ursprünglichen Fehlerfall und nicht lediglich einen erfolgreichen neuen Probelauf.

Die zusätzliche Integrationstestfolge reviewRepeatedTransitionsAndSeparateSlots bestand gegen unveränderten Produktcode und echtes PostgreSQL:

* Einen fachlichen Lauf und einen Probelauf abschließen. Speichern, Aktivieren und Wiederherstellen in ticket_summary/en sowie reply_draft/de lassen den ursprünglichen Lauf für ticket_summary/de gültig.
* Im ursprünglichen Slot dreimal einen Entwurf anlegen, einen eigenen Probelauf daraus abschließen, den Entwurf erneut speichern und verwerfen. Alle bereits entwerteten Lauf-IDs bleiben bei jedem Folgeschritt ohne Inhalt und melden ai.configuration_changed.
* Jede neue Entwurfsnummer ist größer als die vorige. Beim Bearbeiten bleibt die Nummer gleich und die Revision steigt. Verworfene Zeilen bleiben tatsächlich in der Datenbank erhalten.
* Nach jedem Verwerfen funktioniert ein neu gestarteter Probelauf mit dem dann geltenden Skill. Das belegt, dass keine pauschale Sperre aller Probeläufe den Fehler verdeckt.
* Zwei weitere Entwürfe aktivieren, dabei den vorherigen aktiven Stand archivieren, anschließend zum mitgelieferten Skill zurückkehren. Auch die zwischenzeitlich mit aktiven Skills abgeschlossenen Probeläufe werden nach ihrem Auswahlwechsel nicht wieder gültig.
* Bloßes Anlegen, Bearbeiten und Verwerfen im selben Slot lässt den fachlichen Vorschlag gültig. Erst Aktivieren entwertet ihn; Wiederherstellen macht auch diesen alten fachlichen Vorschlag nicht erneut gültig.

Alte Probestempel ohne den neuen Zusatz passen nach dem Update nicht mehr. Das ist die dokumentierte Behandlung als geänderte Konfiguration, keine Migration. Ein weiterer Weg zum Wiedererscheinen nach einer abgeschlossenen Zustandsänderung wurde nicht gefunden.

### Regel 2 / bisheriger F2: Tokenzahlen tragen den Schlüssel nicht weiter

Orte: RunService.java, Zeilen 389–410 und 433–496; AiFormats.java, Zeile 34; AiAdapters.java, Zeilen 38, 103, 132, 169 und 183–206; AiController.java, Zeilen 66–74; AiApiTest.java, Zeile 1525; AiInternalsTest.java, Zeile 199.

Jede erfolgreich vom Adapter gelieferte Completion durchläuft counted, bevor sie irgendeinen Abschlussweg erreicht. Die Dezimaldarstellung beider Zähler wird mit dem tatsächlich verwendeten Schlüssel verglichen; Treffer werden zu null. Das gilt auch für Formatfehler sowie für die Abschlusswege „Konfiguration geändert“ und „Abbruch angefordert“ in deliver. Fehler, die bereits der Adapter meldet, übergeben keine Completion und damit keine Tokenzahlen.

Ein Treffer im Rohtext oder in den dekodierten und bereinigten Inhalten erzeugt KeyRepeated. Dieser Untertyp von AiFormats.Invalid wird vor dem allgemeinen Formatfehler behandelt. Der Lauf endet mit ai.invalid_output und ohne beide Zähler, auch wenn nur einer davon auffällig war. Der Laufnachweis selbst bleibt bestehen. Invalid wurde ausschließlich für diesen Untertyp geöffnet; seine Fehlercodebehandlung bleibt erhalten.

Der dauerhafte API-Test prüft einen erlaubten numerischen Schlüssel, den Fehlerabschluss mit Texttreffer, einen erfolgreichen Abschluss mit auffälliger Tokenzahl, unverfängliche Zahlen, die eigene Laufantwort, die administrative Laufübersicht und die gespeicherten Datenbankwerte. Beide Bestandteile der Korrektur wurden einzeln entfernt:

* Ohne Aufruf von counted scheitert der erfolgreiche Abschluss an der gespeicherten Schlüsselzahl.
* Wenn der KeyRepeated-Zweig wieder die Completion an finish übergibt, scheitert der Fehlerabschluss am erhalten gebliebenen unverfänglichen Eingabezähler. Der Test prüft damit auch die Regel, bei einem Texttreffer sämtliche gemeldeten Zähler zu verwerfen.

Zusätzlich bestand reviewMoreNumericAndErrorPaths: Schlüssel als Teil einer größeren Zahl, negative Zahl mit Schlüssel, unverfänglicher Gegenwert, allgemeiner JSON-Formatfehler, erst nach JSON-Dekodierung sichtbarer Schlüssel, neun HTTP-Fehlerstatus und abgeschnittene Ausgabe. Erfolgreiche unverfängliche Antworten speichern weiterhin die gemeldeten Werte. Der vorhandene Unit-Test deckt außerdem fehlende Zahlen, fehlenden Schlüssel, nichtnumerischen Schlüssel und einen Teiltreffer innerhalb einer Zahl ab.

Weitere Werte im Laufnachweis: Der Quellcodeabgleich ergab keinen weiteren direkt übernommenen Anbieterzähler. Numerische Anbieterwerte außerhalb des 32-Bit-Ganzzahlbereichs und nichtganzzahlige Werte werden bereits durch AiAdapters.integer nicht übernommen. Anbieter-Fehlertexte und deren eigene Codes werden auf feste interne Fehlercodes abgebildet. Anfangs- und Endzeit kommen aus der Anwendungsuhr; die geloggte Laufdauer wird aus System.nanoTime berechnet. Vom Anbieter gemeldete Dauerwerte werden nicht gespeichert. Die Aussage betrifft diese direkten Datenwege und die vorgeschriebene Prüfung der Dezimaldarstellung, nicht eine Erkennung beliebiger Kodierungen oder zufälliger Übereinstimmungen in unabhängig erzeugten Metadaten.

### Regel 3 / bisheriger H1: Der Test isoliert die Prüfung im Ausführer

Orte: backend/src/test/java/taskmanager/ai/internal/HeldKeyPhase.java, Zeilen 18–105; AiApiTest.java, Zeilen 67, 180 und 1556–1577; RunService.java, Zeilen 384–390.

Die Testkonfiguration hält nach super.key und vor Rückkehr zum Ausführer an. Ein Interrupt beendet diese Wartephase nicht vorzeitig; beim Freigeben wird er wieder gesetzt. Der zusätzliche Adapter zählt schon den Eintritt in complete, bevor die HTTP-Schicht einen Aufruf verhindern könnte.

Die entscheidende Mutation entfernt nur task.abort != null || Thread.currentThread().isInterrupted() im Ausführer. Der originale neue Test scheitert dann mit erwartet 0 Adapteraufrufe, tatsächlich 1. Die HTTP-Abbruchprüfungen bleiben dabei aktiv. Der Test hängt damit weder von einer JDBC-Abbruchreaktion noch von einem tatsächlich gesendeten Netzwerkpaket ab und isoliert den bisher fehlenden Schutz.

HeldKeyPhase ist eine ausdrücklich nur in AiApiTest importierte @TestConfiguration. In diesem Kontext werden ConfigService und AiAdapters als primäre, delegierende Varianten verwendet; andere Testkontexte importieren sie nicht. Außerhalb der aktivierten Haltephase läuft der echte Schlüsselpfad weiter. @BeforeEach deaktiviert eine verbliebene Haltephase. Es wurden keine doppelten Ereignishandler durch diese Konfiguration gefunden.

Zusätzlich führte reviewHeldKeyPhaseIsReusableAndPassesOrdinaryWork zweimal innerhalb desselben Spring-Kontexts die Abbruchprobe aus, setzte den Zustand zurück und ließ jeweils anschließend einen gewöhnlichen Probelauf erfolgreich abschließen. Auch die vollständige lokale AiApiTest-Klasse mit 29 Tests bestand. Eine Beeinflussung der übrigen Tests wurde damit nicht festgestellt. Der statische Testzustand ist dabei im bestehenden sequenziellen Testbetrieb bewertet; daraus folgt keine Zusage für eine künftig parallelisierte Ausführung derselben Testklasse.

### Regel 4 / bisheriger H2: Erneute Prüfung unmittelbar vor Dispatch

Orte: backend/src/main/java/taskmanager/ai/internal/AiHttp.java, Zeilen 144–157 und 257; AiInternalsTest.java, Zeilen 76–123; backend/README.md, Zeile 722.

Zwischen der erneuten Interruptprüfung nach client(provider) und sendAsync liegt kein weiterer blockierender oder konfigurierender Aufruf. Die ursprüngliche Eingangsprüfung bleibt erhalten. Der Clientzugriff wurde für die gezielte Testableitung paketweit sichtbar gemacht.

Der neue Test lässt einen normalen Aufruf zu, setzt anschließend während des Clientzugriffs den Interrupt und zählt die Übergaben an den HTTP-Client. Nur die zweite Prüfung entfernt: Der Test scheitert mit erwartet insgesamt 1 Übergabe, tatsächlich 2. Das beweist den Dispatch-Pfad auch dann, wenn ein sofortiger Abbruch verhindert, dass das zweite Paket beim lokalen Testserver ankommt.

Die Grenze bleibt kooperativ. Ein Interrupt nach der letzten Prüfung kann mit der Übergabe konkurrieren; eine atomare Garantie bis zum ersten gesendeten Byte besteht nicht. README und Übergabe beschreiben diese Einschränkung ausreichend klar. Die Prüfung liegt im vorhandenen Ablauf so nah wie möglich vor der Übergabe.

### Regel 5 / bisheriger H3: Beide Prüfrouten berücksichtigen die Adresssperre

Orte: frontend/tests/browser/backend.mjs, Zeilen 3717–3719 und 3746–3748; frontend/tests/browser/ai.test.mjs, Zeilen 184–210; serverseitiger Vergleich: ConfigService.java, Zeilen 145–161 und 229–249.

Zugangsprüfung und Modellprüfung liefern bei gesetzter Adresssperre HTTP 200 mit ok: false, code: ai.address_not_allowed und models: null. Die Modellprüfung liest die Sperre des zugehörigen Zugangs. Die Sperre hat Vorrang vor einer gesondert hinterlegten Prüfantwort. Das entspricht dem Serverpfad, der die Adresse beim Schlüsselzugriff vor dem Adapter prüft.

Die unabhängige direkte Probe über den registrierten Request-Handler bestand mit 72 gesperrten Prüfantworten: drei Anbieterarten, aktivierter/deaktivierter Zugang und Modell, vorhandener/fehlender Installationsschlüssel sowie fehlende, erfolgreiche oder fehlerhafte hinterlegte Prüfantwort, jeweils beide Routen. Zwei ungesperrte Positivkontrollen blieben erfolgreich. Der tatsächliche Browsertest bestätigt die Statusanzeige und beide Fehlermeldungen auf Deutsch und Englisch.

Beide Routenkorrekturen wurden getrennt entfernt. Jeweils meldet dann nur die mutierte Route fälschlich Erfolg. Der originale Browsertest scheitert gezielt beim Warten auf „Prüfung von Ollama im Haus fehlgeschlagen …“ beziehungsweise „Prüfung von Llama fehlgeschlagen …“. Die Browser-Mutationsproben verwenden unveränderte Anwendungsausgaben und jeweils einen separaten Testersatz.

## Vertrag, Routen und Antworten

AiContractTest.everyMappedRouteIsDocumentedAndEveryDocumentedRouteExists bestand: alle 26 dokumentierten Routen stimmen mit den Spring-Routen überein. Dieser Test prüft Routengleichheit; die Antwortinhalte wurden zusätzlich über API-Tests und Quellcodeabgleich beurteilt.

contracts/ai.openapi.yaml, Zeilen 726–731, lässt beide Zähler weiterhin als Ganzzahl oder null zu und beschreibt die neue Auslassung bei Schlüsselwiederholung. frontend/src/api/generated/ai.d.ts, Zeilen 514–517, stimmt damit überein. Die erneute Generierung hinterließ keinen Quelldiff. Zähler im Laufnachweis, Prüfergebnis mit models: null, fester Fehlercode sowie verborgenes Vorschlagsfeld stimmen mit den geprüften Antworten überein. Der zusätzliche interne Entwurfszähler wird nicht versehentlich als neues öffentliches Feld ausgeliefert; AiController verwendet weiterhin seine expliziten Antworttypen.

## Ausgeführte Prüfungen

Unabhängig lokal auf dem PR-Head.

*Anmerkung beim Ablegen:* Der Bericht kam als Nachricht im Projektchat und endet dort nach dem Satz oben; die Liste der einzelnen ausgeführten Prüfungen ist in der Nachricht abgeschnitten. Alle Aussagen zu ausgeführten Tests, die oben in den einzelnen Abschnitten stehen, sind unverändert übernommen.
