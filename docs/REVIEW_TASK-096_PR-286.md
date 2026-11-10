# Prüfbericht TASK-096 / PR #286

Datum: 10. November 2026. Unabhängige zweite KI: Codex, Sitzung `codex-20261110-requirements-review-01` (DEC-040).

**Empfehlung: nachbessern.** Sieben reproduzierte Oberflächenbefunde bleiben offen. Besonders relevant sind der Verlust fremder Kriterienänderungen nach einer Konfliktentscheidung, ein unvollständiger Freigabedialog, nicht beurteilbare Linkänderungen im Vergleich und die durch den neuen Navigationseintrag unerreichbare Darstellungsauswahl. Der vollständige Backendlauf besteht; ein Umgehen der geprüften Lese-/Schreibrechte oder Überschreiben einer freigegebenen Revision wurde nicht festgestellt. Die fachliche Abnahme und die Bestätigung der Auslegungen in DEC-126 bleiben bei Holger.

## Prüfstand und Maßstab

Geprüft wurde der weiterhin offene Entwurfs-PR [#286](https://github.com/1uedev/task-manager/pull/286), Branch `claude/project-thread-dyiimb`, Commit `0aaed21dfbc76615187f422a2cd09c79566de2ec`, gegen `main` auf `3f186a7599a6adf1000d975229ba19c64cf5fba3`. Die erneute GitHub-Abfrage vor Abschluss bestätigte denselben Prüfstand. Umfang: 41 geänderte Dateien.

Gelesen wurden zuerst `AGENTS.md`, TASK-096 mit den 15 fachlichen Regeln in `TODO.md`, DEC-126 mit den Auslegungen a bis e, `contracts/requirements.openapi.yaml` 0.1.0, `contracts/permissions.openapi.yaml` 0.4.0 und `backend/README.md`, Abschnitt „Anforderungen (TASK-096)“. Ergänzend: Sitzungsregeln, relevante Anforderungen, Implementierung, Migration, Tests und betroffene gemeinsame Komponenten.

Die Auslegungen a bis e wurden technisch mitgeprüft, durch diesen Bericht aber nicht fachlich bestätigt. Insbesondere b weicht ausdrücklich von Regel 5 ab: Wiederherstellen führt bei einer bereits freigegebenen laufenden Revision nach „Freigegeben“. Das schützt hier deren Unveränderlichkeit. a lässt Speichern während der Prüfung zu, c macht Kennung/Status/Revision/Freigabe allein mit Leserecht sichtbar, d verlangt Leserecht zusätzlich zu Bearbeiten/Freigeben. Diese Abläufe sind entsprechend umgesetzt. Die Konfliktübernahme nach e bleibt wegen F1 unvollständig.

## Befunde – vor Abnahme zu beheben

### F1 – P2: „Meine Eingaben behalten“ überschreibt unberührte Kriterien

**Ort:** `frontend/src/pages/RequirementsPage.tsx:381`, `:438`, `:443`.

Die Übernahme unterscheidet nur, ob das gesamte Kriterienfeld geändert wurde. Sobald die Person ein einzelnes Kriterium bearbeitet, behält sie die vollständige alte Liste; fremde Änderungen an anderen Kriterien und hinzugefügte Kriterien werden nicht übernommen.

**Reproduktion:** Ausgangsliste K1 „K1 alt“, K2 „K2 alt“. Anna ändert ausschließlich K1 auf „K1 MEINE EINGABE“. Ben ändert inzwischen K2 auf „K2 VON BEN“ und fügt K3 „K3 VON BEN“ hinzu. Annas Speichern erhält korrekt 412; Eingabe und Vergleich bleiben vorhanden. Nach „Meine Eingaben behalten“ und erneutem Speichern enthält der gespeicherte Stand jedoch nur K1 „K1 MEINE EINGABE“ und K2 „K2 alt“. K3 ist entfernt. Mit echtem Produktionsbuild und Testserver wiederholt bestätigt, einschließlich der abschließenden gespeicherten Daten.

**Auswirkung:** Fremde Arbeit an selbst nicht geänderten Teilen geht verloren. Das widerspricht dem Prüfauftrag und dem vorgesehenen Verhalten aus Auslegung e. Der vorhandene Test deckt nur „eigener Titel, fremdes Kriterium“ ab und entdeckt die Lücke innerhalb der Kriterienliste nicht.

**Vorschlag:** Die eigenen Änderungen anhand der festen Kriterienkennungen gegen die ursprüngliche Grundlage ermitteln. Unberührte Kriterien und fremde Ergänzungen aus dem neueren Stand erhalten; Überschneidungen bei Text, Entfernung und Reihenfolge ausdrücklich behandeln. Die Konstellation mit unabhängig bearbeiteten K1/K2 und neuem K3 dauerhaft testen.

### F2 – P2: Der Freigabedialog zeigt nicht alle Inhaltsfelder

**Ort:** `frontend/src/pages/RequirementsPage.tsx:241`, `:569`, `:579`.

Der Dialog zeigt Revision, Bearbeitende, Zeit, Beschreibung und Kriterien. `ContentView` stellt Titel und Priorität nicht dar; der Dialog ergänzt diese beiden Inhaltsfelder ebenfalls nicht. Sie stehen nur außerhalb des Dialogs in der allgemeinen Seitenansicht.

**Reproduktion:** Anforderung „PRUEFTITEL“ mit Priorität „Kritisch“ zur Prüfung geben und den Freigabedialog öffnen. Beide Werte fehlen im Text und Inhalt des Dialogelements. Browserprüfung und Screenshot bestätigen dies. Nach einem 412 wird zwar der neuere Stand geladen, diese Inhaltsfelder fehlen im Dialog aber weiterhin.

**Auswirkung:** Die zugesagte vollständige Darstellung der freizugebenden Revision aus Regel 8 ist nicht erfüllt. Gerade bei schmaler Darstellung müssen Freigebende den Dialog verlassen beziehungsweise weit nach oben zu anderen Seitenteilen zurückgehen, um alle Inhalte zu beurteilen. Die serverseitige Freigabeprüfung selbst bleibt korrekt.

**Vorschlag:** Titel und Priorität derselben `requirement`-Antwort ausdrücklich in den Dialog aufnehmen. Alle vier Inhaltsfelder einschließlich Titel-/Prioritätsänderung nach einem 412 als Dialoginhalt prüfen.

### F3 – P2: Geänderte Linkziele sind im Beschreibungsvergleich nicht erkennbar

**Ort:** `frontend/src/pages/RequirementsPage.tsx:669`, `:834`, `:839`.

Der gemeinsame Blockvergleich erkennt unterschiedliche Attribute. Die Anforderungen-Ansicht gibt anschließend aber nur den Klartext des Blocks aus; Ziele und weitere Strukturmerkmale werden verworfen. Die zusätzliche Erläuterung des Wiki-Vergleichs wird hier nicht verwendet.

**Reproduktion:** Freigegebene Revision 1 mit Linktext „Anleitung“ nach `https://old.example`, Revision 2 mit gleichem Text nach `https://new.example`. In der Historie 1 und 2 vergleichen. Die Ausgabe lautet lediglich „Geändert“, gestrichen „Anleitung“, eingefügt „Anleitung“. Keines der beiden Ziele erscheint. Im Browser bestätigt; derselbe Darsteller wird auch für den Konfliktvergleich verwendet.

**Auswirkung:** Die Person erkennt zwar, dass etwas anders ist, kann die fachliche Änderung aber nicht beurteilen. Entsprechend bleiben reine Format-/Attributänderungen ohne verständliche Erläuterung. Das erfüllt den vorgesehenen Beschreibungsvergleich nach Blöcken nur teilweise.

**Vorschlag:** Die Vergleichsdarstellung einschließlich Linkreferenzen und Hinweisen auf reine Formatänderungen gemeinsam mit dem Wiki verwenden oder gleichwertig ergänzen. Reine Linkzieländerungen ohne Textänderung in Historie und Konflikt testen.

### F4 – P2: Der zusätzliche Navigationseintrag macht die Darstellungsauswahl unerreichbar

**Ort:** `frontend/src/app/App.tsx:47`; betroffene gemeinsame Darstellung `frontend/src/styles.css:59`.

Mit dem neunten Haupteintrag „Anforderungen“ passt der Inhalt der feststehenden Seitenleiste bei 1100 × 800 px nicht mehr vollständig in deren Höhe. Für diesen Überlauf ist kein geeigneter eigener Scrollbereich vorgesehen.

**Reproduktion:** Als Anna die Startseite bei 1100 × 800 px öffnen und „Hell“ in der Darstellungsauswahl anklicken. Im vollständigen Browserlauf und isoliert scheitert derselbe vorhandene Test in `design.test.mjs:63`; das HTML-Element fängt die Klicks ab. Die zusätzliche gezielte Prüfung misst die Schaltfläche nach dem Scrollversuch bei y=799,47 mit 32 px Höhe in einem 800 px hohen Fenster. Sie bleibt nicht anklickbar. Wird ausschließlich der neue Navigationseintrag im Prüf-DOM ausgeblendet, liegt sie bei y=755,08 und der normale Klick wechselt erfolgreich auf „light“. Kein erzwungener Klick wurde verwendet.

**Auswirkung:** Eine bestehende Anwendungsfunktion ist mit dem zusätzlichen Eintrag nicht mehr regulär bedienbar. Dieser Fehler des Gesamtlaufs ist damit ein bestätigter Produktbefund und keine als flüchtig eingeordnete Teststörung.

**Vorschlag:** Die Seitenleiste für längere Navigation und begrenzte Fensterhöhen auslegen, etwa mit einem korrekt begrenzten Scrollbereich. Footer, Sprach- und Darstellungsauswahl müssen erreichbar bleiben. Den vorhandenen Test erhalten und die neue Eintragszahl bei üblichen Desktophöhen absichern.

### F5 – P3: Verschieben per Pfeilschaltfläche bleibt ohne Ansage

**Ort:** `frontend/src/pages/RequirementsPage.tsx:294`, `:296`, `:316`.

Alt mit Pfeiltaste und Ziehen verwenden `useReorder` mit Ansage. Die sichtbaren Pfeilschaltflächen verwenden dagegen die separate Funktion `swap`, die nur die Liste setzt und die Live-Region nicht aktualisiert.

**Reproduktion:** K2 über „K2 nach oben“ an Stelle 1 verschieben. Die Reihenfolge ist korrekt `[2,1]`, die zugehörige `aria-live="polite"`-Region bleibt aber leer. Im Browser bestätigt. Alt+Pfeil funktioniert demgegenüber mit Ansage in DE und EN.

**Auswirkung:** Für einen ausdrücklich angebotenen Bedienweg fehlt die verlangte Rückmeldung über die neue Position.

**Vorschlag:** Alle Verschiebewege über dieselbe Funktion mit Positionsansage führen und Pfeilschaltflächen ebenfalls mit einer Prüfung der Live-Region absichern.

### F6 – P3: Das Anlegeformular schützt ungespeicherte Eingaben nicht

**Ort:** `frontend/src/pages/RequirementsPage.tsx:89`; vorhandener Schutz ausschließlich im Bearbeiter bei `:386`.

**Reproduktion:** In der Projektliste im Anlegeformular „Noch nicht angelegt“ als Titel eingeben, danach über „Start“ navigieren. Es erscheint keine Bestätigung; die Route wechselt auf `#/start` und die Eingabe ist verloren. Im Browser bestätigt. Der bereits vorhandene Schutz des Bearbeiters hilft vor dem Anlegen nicht.

**Auswirkung:** Die im Auftrag verlangte Warnung beim Verlassen mit ungespeicherten Eingaben ist für diesen Inhaltsfluss unvollständig.

**Vorschlag:** Den Navigations-/Tabschutz auch für ausgefüllte, noch nicht abgeschickte Anlegefelder verwenden und nach erfolgreichem Anlegen sauber aufheben. Navigation und Tab-Schließen separat testen.

### F7 – P3: Die echte Kriterienhistorie zeigt Kennungen mit doppeltem K

**Ort:** `backend/src/main/java/taskmanager/requirements/internal/RequirementService.java:526`; `frontend/src/pages/RequirementsPage.tsx:703`, `:704`; abweichender Mock `frontend/tests/browser/backend.mjs:1963`.

Der Server schreibt Reihenfolgen als `K1,K3` beziehungsweise `K3,K1`. Die Oberfläche setzt beim Aufteilen nochmals `K` davor. Der Mock liefert ausschließlich Zahlen und verdeckt dadurch den Fehler in den vorhandenen Browsertests.

**Reproduktion:** Kriterien umsortieren und Historie öffnen. Der echte Serverwert ist durch `criteriaKeepTheirNumbersAndOrder` im erfolgreichen API-Lauf belegt. Mit genau diesem Antwortformat im Produktionsfrontend lautet die Ausgabe etwa „Reihenfolge der Kriterien geändert von KK1, KK2, KK3 zu KK2, KK1, KK3“. Der zusätzliche Browsertest reproduziert dies.

**Auswirkung:** Die Historie benennt andere als die tatsächlichen festen Kennungen und wirkt widersprüchlich zur Kriterienliste.

**Vorschlag:** Ein gemeinsames Werteformat für Server, Oberfläche und Testersatz festlegen und genau dieses über beide Seiten prüfen. Das Präfix genau einmal hinzufügen.

## Hinweise

### H1 – CRLF-Zeilenumbrüche werden vor der vorgesehenen Normalisierung abgewiesen

**Ort:** `backend/src/main/java/taskmanager/requirements/internal/RequirementService.java:641`, `:645`; Vertrag `contracts/requirements.openapi.yaml:44`.

**Reproduktion:** Ein Kriterium mit `Zeile1\nZeile2` anlegen: 201. Dasselbe mit Windows-Zeilenumbruch `Zeile1\r\nZeile2`: 400 `criteria.text_invalid`. Der zusätzliche API-Test gegen den echten Server bestätigt die Abweichung. Das anschließende Ersetzen von CRLF durch LF kommt zu spät, da `\r` bereits als unzulässiges Steuerzeichen gewertet wurde.

**Auswirkung:** Die dokumentierte Zulässigkeit von Zeilenumbrüchen ist für API-Clients und Windows-Eingaben unvollständig umgesetzt. Der normale HTML-Textarea-Fluss nutzt LF.

**Vorschlag:** Zeilenumbrüche vor Längen-/Steuerzeichenprüfung normalisieren und LF/CRLF gemeinsam testen; andere Steuerzeichen weiterhin ablehnen.

### H2 – Der Testersatz lässt serverseitig verbotene Inhalte und Kriterien zu

**Ort:** `frontend/tests/browser/backend.mjs:1814`, `:1830`, `:1936`; zusätzlich F7.

**Reproduktion:** Direkte Aufrufe von `requirementCall` akzeptieren mit 200 erstens zwei Kriterien mit derselben bestehenden ID, zweitens eine Beschreibung mit Linkziel `javascript:alert(1)`, drittens eine unbekannte verantwortliche UUID. Der echte Server weist diese Fälle mit 400 ab; dies ist durch die ausgeführten API-Tests einschließlich der zusätzlichen Linkprüfung belegt. Beim Beschreibungsverbot verwendet der Mock zudem `content.*` statt der vom Service gelieferten `description.*`-Verletzungscodes. Das unterschiedliche Historienformat ist unter F7 ausgeführt.

**Auswirkung:** Browserprüfungen können Zustände erzeugen oder akzeptieren, die im Produkt nicht zulässig sind. Die neun grünen Anforderungen-Browsertests belegen daher keine vollständige Serverparität. Die gefährliche URL wurde am echten Server abgewiesen; hier liegt kein nachgewiesener XSS-Durchlass des Produkts vor.

**Vorschlag:** Gemeinsame Vertragsbeispiele für Kriterienidentität, neue/ungültige Verantwortliche, Dokumentelemente/Links und Fehlercodes gegen echten Server und Mock ausführen. Serverwerte wie die Kriterienreihenfolge unverändert übernehmen.

### H3 – `editors` ist nicht nach den lesbaren Änderungsfeldern eingeschränkt

**Ort:** `backend/src/main/java/taskmanager/requirements/internal/RequirementService.java:538`, `:540`; Vertrag `contracts/requirements.openapi.yaml`, Schema `Requirement.editors`.

**Reproduktion:** Eine zweite Person ändert ausschließlich die Beschreibung. Ein Leser ohne Leseregel für Beschreibung erhält deren Wert und Änderungseintrag korrekt nicht, aber die Person bleibt in `editors`. Gegen den echten Server beobachtet. Die Menge wird aus allen Inhaltsereignissen der laufenden Revision gebildet.

**Auswirkung:** Es wird kein versteckter Feldwert übermittelt, wohl aber zusätzliche Bearbeitungshistorie zu verborgenen Feldern. Der Vertrag beschreibt die Menge derzeit uneingeschränkt; die Reichweite der Regel 12 sollte hier ausdrücklich geklärt werden, statt die Antwort als vollständig nach Feldereignissen gefiltert zu bezeichnen. Allgemeine Revisions-/Änderungsmetadaten sind davon zu unterscheiden.

**Vorschlag:** Für eingeschränkte Leser nur Bearbeitende lesbarer Inhaltsänderungen ableiten oder die beabsichtigte Ausnahme ausdrücklich bestätigen und dokumentieren. Für Freigebende mit allen Inhaltsleserechten bleibt die vollständige Menge erhalten.

## Weitere Ergebnisse zu den Prüfschwerpunkten

- **Rechte:** Ohne `requirement.read` weder Anforderung noch Kennung/Projekt im Anforderungen-Katalog; Bearbeiten und Freigeben allein reichen nicht. Portalkonten mit allen drei zugewiesenen Aktionen erhalten dennoch keinen Anforderungen-Katalog, kein Detail und keine Liste. Neue Verantwortliche werden auf aktive interne Projektmitglieder geprüft; Portalkonto und Nichtmitglied als neue Verantwortliche abgewiesen. Die Feldwerte in Detail, Liste, Historie und Revisionen werden entsprechend ihrer Leseregeln gefiltert; Filter über verborgene Felder erhalten 400. Sonderfall Bearbeitungsmetadaten siehe H3. Die UI zeigt die vom Server maskierten Werte auch in Vergleich und Dialog entsprechend eingeschränkt.
- **Änderungsprüfung bis Commit:** Alle Änderungen einschließlich Zur Prüfung, Zurückgeben, Verwerfen und Wiederherstellen führen über `Permissions.require`. Auch die vorgelagerten `inspect`-Prüfungen halten innerhalb derselben Service-Transaktion die geteilte Rechte-Revisionssperre. Eine zusätzliche kontrollierte Prüfung hielt die Anforderungszeile an: Die Änderung wartete dort, ein parallel gestarteter Rechteentzug wartete anschließend an `permissions_revision`. Erst nach dem Änderungscommit lief der Entzug durch; der folgende Abruf ergab 404. Keine Lücke zwischen vorgelagerter Leseprüfung und Änderungsprüfung festgestellt.
- **Revisionen:** Die erste tatsächliche Änderung an Titel/Beschreibung/Kriterien/Priorität nach Freigabe öffnet n+1; eine Änderung nur der verantwortlichen Person nicht. Weitere Änderungen innerhalb des Entwurfs bleiben in derselben Revision. Ein unveränderter Aufruf erhöht weder Version noch Historie. Die freigegebene Revisionsantwort blieb beim zusätzlichen Test nach Verantwortlichenwechsel, Verwerfen/Wiederherstellen und neuer Revision bytegleich, abgesehen vom erwarteten Kennzeichen `current`. Erneutes Zur-Prüfung-Geben/Freigeben derselben wiederhergestellten Freigabe wird verweigert. Das entspricht Auslegung b, deren fachliche Bestätigung aussteht.
- **Freigabe und Konkurrenz:** Fehlendes If-Match 428, veraltete Version/abweichende aktuelle Revision 412; ohne Leseregel eines Inhaltsfelds 403. Beim zusätzlichen Rennen Speichern gegen Freigeben auf derselben Version erfolgte einmal 200 und einmal 412. Zwei nachweislich an der gehaltenen `requirement`-Zeile wartende Kriterienänderungen ergaben ebenfalls 200/412. Der erfolgreiche neue Eintrag erhielt K2, ein anschließender gültiger Versuch K3. Zwei gleichzeitig an `requirement_counter` wartende Anlagen erhielten ANF-2 und ANF-3. Kein doppelter Zähler oder Wiederverwenden einer Kriteriennummer festgestellt.
- **Beschreibung:** `wiki.RichText` verwendet die Serverprüfung und maskierte HTML-Ausgabe von `WikiDocument`; Bilder und Wikiverweise werden zusätzlich verworfen. Ausgeführt: Bild/Wikiverweis, `javascript:`, `data:`, protokollrelative URL und Steuerzeichen im Link abgelehnt; `<`, `>`, `&`, Anführungszeichen und Apostroph korrekt maskiert. Kein fremdes Bildwerkzeug in der Anforderungen-Werkzeugleiste. Die gemeinsamen Dokumentgrenzen wurden am übernommenen Prüfer und vorhandenen Wiki-/Anforderungstests abgeglichen.
- **Rechteverwaltung:** Katalogreihenfolge `ticket`, dann `requirement`; unbekannte Felder und Zuordnung zum falschen Typ werden abgewiesen. Eine sichtbare Projektverwaltung mit projektbezogenem `permissions.manage`, `project.read` und entsprechenden Feldrechten erhält alle fünf delegierbaren Anforderungsfelder. `project.manage` allein ist nach dem bestehenden Rechtevertrag keine Rechteverwaltung. Gruppenoberfläche sendet den richtigen Ressourcentyp; `ProjectRights.tsx` trennt die Typen bei Angebot und vorhandener Regel. Neue Feldtabellen sind DE/EN beschriftet.
- **Oberfläche:** Der vorhandene Test für eine späte Antwort eines früheren Listenfilters besteht. Im normalen Konfliktfall bleibt eigene Eingabe erhalten und Speichern ist bis zur Wahl gesperrt; F1 betrifft die anschließende Kriterienübernahme. Der Freigabedialog verwendet exakt angezeigte Revision/Version und lädt nach 412 neu. Die vorhandenen Bearbeiter-/Navigationsschutzfälle bestehen, die Anlage bleibt ungeschützt (F6). Bei 320 px in DE/EN kein horizontaler Dokumentüberlauf, benannte Schaltflächen und Editor, sichtbarer Tastaturfokus und erfolgreiche Alt-Pfeil-Ansage beobachtet; Screenshots angesehen. F4 betrifft zusätzlich Desktophöhe, F5 die Pfeilschaltflächen. Kein vollständiger Barrierefreiheitstest behauptet.
- **Migration/Vertrag:** V0058 folgt der Namenskonvention; positive Nummern/Versionen, Status-/Prioritätswerte, Eindeutigkeit je Projekt, Freigabepaar sowie modulinterne Fremdschlüssel geprüft. Beziehungen zu anderen Modulen und Revisionszeiger werden durch Service/Ports getragen, nicht durch zusätzliche modulübergreifende Fremdschlüssel. Alle 55 Migrationen bis V0058 liefen gegen PostgreSQL 16. Zahlen/Tabellenlisten in `lifecycle-check.sh`, `DatabaseFoundationTest` und `ApplicationLifecycleTest` passen am Prüfstand. `RequirementsContractTest` bestand mit 13 Routen; dieser Routenabgleich ist keine vollständige Antwortschema-/Mockprüfung.
- **Umfang:** Keine Umsetzung von TASK-097/098, eigenen Feldern, Demodaten, Suche, Git, KI, Portalzugriff oder Löschen festgestellt. Keine neuen npm-Abhängigkeiten; die Paketänderung ergänzt die Vertragsgenerierung. Die Auslegungen a bis e sind in DEC-126/TODO/PR weiterhin als zur Bestätigung stehend kenntlich und werden nicht durch diese technische Prüfung beschlossen.

## Tatsächlich ausgeführte Prüfungen

Umgebung: Windows, Python 3.12.14, Java 25.0.4.1, Maven 3.9.16, Node 24.22.0/npm 11.19.1, eigene PostgreSQL-16.15-Instanz. Die verfügbaren Python-/Maven-Programme wurden direkt verwendet, mit denselben Skripten beziehungsweise Repository-POM und Ziel `verify`; `python3` und die Unix-Datei `./mvnw` waren hier nicht die tatsächlich gestarteten Programme. Ausschließlich synthetische Testdaten.

| Prüfung | Ergebnis |
| --- | --- |
| `scripts/validate_repository.py` | Bestanden. |
| `scripts/task_board.py --check` | Bestanden, Aufgabenübersicht aktuell. |
| `python -m unittest discover -s tests -v` | 40 Tests: 39 bestanden, ein vorgesehener Windows-Skip zur Zeitzonenumstellung. |
| Backend vollständiges `verify`, PostgreSQL 16.15 | **577 Tests bestanden, 0 Fehler, 0 Fehlschläge, 0 Skips; Build/Verpackung erfolgreich.** Darin elf `RequirementApiTest`-Fälle und der Vertrags-/Routenabgleich. |
| `npm ci` | Erfolgreich; keine gemeldeten Schwachstellen. |
| `npm run verify` | Erfolgreich: Generierung, Typprüfung, **91 Unit-Tests**, Produktionsbuild. Nicht blockierende Bundlerhinweise zu Tiptap-Moduldirektiven und Paketgröße. |
| `npm run test:browser -- --test-concurrency=1` | **242 Tests, 240 bestanden, zwei fehlgeschlagen**, keine Skips. Alle neun Anforderungen-Fälle bestanden. Fehler: Darstellungsauswahl `design.test.mjs:63` und Tag-Bearbeitung `tags.test.mjs:56`. |
| Isolierte Wiederholung dieser zwei Browserfälle | Tag-Test bestanden. Darstellungstest erneut fehlgeschlagen; anschließend als Produktregression F4 bestätigt. Der vollständige Browserlauf bleibt fehlgeschlagen. |
| Eigene API-/Datenbankfälle außerhalb des Produktcheckouts | Sieben unterschiedliche Fälle: sechs bestanden; der CRLF-Grenzfall schlägt reproduzierbar fehl (H1). Enthalten: kontrollierte Zeilen-/Zählersperren, Freigaberennen/Unveränderlichkeit, Rechteentzug, Feldmaskierung, Projektdelegation, Portalrechte/-Verantwortliche und Dokumentprüfung. Der Projektdelegations-Prüfaufbau wurde an die im Vertrag festgelegte Rolle und Projektsichtbarkeit angepasst; die endgültige Prüfung besteht. |
| Eigene Browserfälle außerhalb des Produktcheckouts | Acht unterschiedliche Fälle: sieben reproduzierbare Fehlschläge entsprechend F1–F7; ein erfolgreicher DE-/EN-Bedienungs-/320-px-Fall. Die sechs Fälle F1/F2/F3/F5/F6/F7 liefen im abschließenden Siebenerlauf zusammen; die Desktopnavigation wurde anschließend gesondert geprüft. Die Historienprüfung verwendet die vom echten Server nachgewiesenen K-Präfixe. |
| Direkte Mock-Gegenproben | Ausgeführt: doppelte Kriteriennummer, gefährliches Linkziel und unbekannte verantwortliche Person werden im Mock mit 200 akzeptiert (H2). |
| `git diff --check`, Stand-/Änderungskontrolle | Erfolgreich. Produktcode, generierte Dateien, TODO.md und DONE.md unverändert; ausschließlich dieser Bericht wird hinzugefügt. |

Die abgefragte CI des Prüfcommits war grün: [Backend](https://github.com/1uedev/task-manager/actions/runs/42835178535), [Frontend](https://github.com/1uedev/task-manager/actions/runs/42835178521), [Repository checks](https://github.com/1uedev/task-manager/actions/runs/42835178368), [Technical qualification](https://github.com/1uedev/task-manager/actions/runs/42835178283), [Image](https://github.com/1uedev/task-manager/actions/runs/42835178342). Diese abgefragten Ergebnisse ersetzen die benannten eigenen Reproduktionen nicht.

**Nicht lokal ausgeführt:** Docker-/Compose-Lifecyclecheck, eigenständiger Aufruf des Unix-Lifecycle-Skripts und vollständiger Barrierefreiheitstest. Die vorhandenen Datenbank-/Anwendungstests liefen im vollständigen Backendlauf. Kein zweiter kompletter Browserlauf nach den isolierten Wiederholungen. Browsernachweise verwenden den echten Produktionsbuild mit dem Testserver; API-/Sperrnachweise verwenden den echten Backendserver und PostgreSQL.

Rohprotokolle, Screenshots und synthetische Zusatzprüfungen liegen außerhalb des Produktcheckouts im benachbarten Prüfbereich `review-tools/requirements096-review-01`, insbesondere `repository.log`, `backend-verify.log`, `frontend-verify.log`, `browser-all.log`, `browser-failed-isolated.log`, `browser-probes-complete.log`, `browser-navigation.log`, `api-probes.log`, `api-delegation-portal.log` und `mock-probes.log`. Keine Zusatzprogramme wurden in den Produktcode aufgenommen.
