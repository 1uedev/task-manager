# Prüfbericht TASK-093 / PR #278

Datum: 9. November 2026. Unabhängige zweite KI: Codex, Sitzung `codex-20261109-wiki-review-01` (DEC-040).

**Empfehlung: nachbessern.** Fünf Befunde bleiben offen: Eingaben gehen während eines Speicherns verloren; der Blockvergleich übersieht inhaltliche Änderungen; der Editor meldet unmittelbar nach dem Öffnen ungespeicherte Änderungen; die Leseansicht verändert zulässige Listennummerierungen; die Bildrechte gehen über die bestätigte Regel 14 hinaus. Grüne CI ersetzt diese fachlichen Reproduktionen nicht.

## Gegenstand und Grundlage

Geprüft wurde der weiterhin offene Entwurfs-PR [#278](https://github.com/1uedev/task-manager/pull/278), Branch `claude/project-thread-ipd3h4`, zuletzt Commit `aa412f1a0f2f915c295d1e91fe43edbc768a0d20`, gegen `main` auf `e223ce21232e0f003f320f9ef57746da6de74f01`.

Gelesene Maßstäbe: `AGENTS.md`, die 15 fachlichen Regeln und Abnahmekriterien von TASK-093 in `TODO.md`, DEC-123 in `DECISIONS.md`, `contracts/wiki.openapi.yaml`, `backend/README.md`, Abschnitt „Wiki (TASK-093)“. Die insgesamt 21 bestätigten Vorschläge verteilen sich auf TASK-093 und TASK-094; geprüft wurde ausschließlich TASK-093.

Die Prüfung begann auf `5b4446b`. Währenddessen kamen `947e860` (Upload-Größentest) und `aa412f1` (erwartete Tabellenliste der Startprüfung) hinzu. Beide Änderungen wurden geprüft und in den Prüfstand übernommen. Produktcode, Frontend und Vertrag blieben dabei identisch; die unabhängigen Reproduktionen gelten deshalb auch für den abschließend bewerteten Commit. Auf diesem Stand wurden die betroffenen Backendtests erneut ausgeführt.

## Befunde – vor Abnahme zu beheben

### F1 – P2: Eine erfolgreiche Speicherung überschreibt später eingegebenen Text

**Ort:** `frontend/src/pages/WikiPage.tsx:241`, `:261`, `:273`, `:328`; ergänzend `frontend/src/pages/WikiEditor.tsx:55`.

Der Editor bleibt während der Speicheranfrage beschreibbar. `save` übermittelt einen Schnappschuss des Dokuments; die spätere Erfolgsantwort ersetzt den aktuellen Editorinhalt bedingungslos durch diesen älteren Schnappschuss und setzt ihn als gespeicherte Grundlage. Gesperrte Schaltflächen verhindern das weitere Tippen im Inhaltsfeld nicht.

**Reproduktion:** Entwurf öffnen, „Start gesendet“ eingeben, die Antwort auf `PUT .../draft` im Browsertest anhalten und speichern. Während der ausstehenden Anfrage ` SPAETER` ergänzen; der Text ist zunächst sichtbar. Erfolgsantwort für Revision 2 freigeben. Anschließend steht nur noch „Start gesendet“ im Editor, `data-dirty="false"`; die Ergänzung ist weder gespeichert noch weiter vorhanden. Wiederholt mit dem echten Editor und verzögerter Testserverantwort bestätigt.

**Auswirkung:** Stiller Verlust eigener Arbeit bei gewöhnlicher Netzverzögerung; anschließend schützt auch die Warnung beim Verlassen diese Eingabe nicht mehr. Das widerspricht dem vorgesehenen Schutz eigener Eingaben und dem verlässlichen Speicherzustand aus Regeln 5 und 6.

**Vorschlag:** Während der Anfrage auch den Editor sperren, oder Änderungen seit dem abgeschickten Dokument erkennen und erhalten. Im zweiten Fall nur die bestätigte Grundlage/Revision fortschreiben und spätere Änderungen weiterhin als ungespeichert behandeln. Einen verzögerten erfolgreichen Speichervorgang mit weiterer Eingabe als Regression aufnehmen.

### F2 – P2: Der Blockvergleich erklärt geänderte Links und Bilder für unverändert

**Ort:** `frontend/src/pages/wikiDiff.ts:24`, `:40`, `:44`; Verwendung im Konfliktvergleich `frontend/src/pages/WikiPage.tsx:341` und in der Historie.

Ein Block wird ausschließlich durch Typ und extrahierten Text verglichen. Linkziele, Textmarkierungen und fast alle Attribute verschwinden dabei; bei Bildern bleibt nur der Alternativtext. Damit werden fachlich verschiedene Dokumente als gleich behandelt.

**Reproduktion:** Zwei Revisionen mit dem gleichen Text „Anleitung“, aber Linkziel `https://one.example` beziehungsweise `https://two.example`, in der Historie vergleichen. Die Oberfläche zeigt „Keine Unterschiede.“. Auch der direkte Aufruf des tatsächlichen Vergleichsmoduls liefert `differs=false` und nur `same`. Zwei Bilder mit gleichem Alternativtext und unterschiedlichem `src` liefern ebenfalls `differs=false`.

**Auswirkung:** Vor einer Übernahme beziehungsweise Konfliktentscheidung bleiben geänderte Verweisziele und ausgetauschte Bilder unbemerkt. Ebenso betroffen sind etwa geänderte Hervorhebungen oder Tabellenattribute. Das ist ein unvollständiger Blockvergleich nach Regeln 6 und 8, unabhängig von der vereinbarten Beschränkung auf Blöcke statt Wörter.

**Vorschlag:** Die vollständige normalisierte Blockstruktur einschließlich `marks`, Attributen und Kindknoten zur Gleichheitsprüfung verwenden. Geänderte Ziele/Bildkennungen beziehungsweise andere Attribute auch sichtbar erläutern; nur einen Block mit unverändertem Klartext einzufärben reicht zur Beurteilung nicht. Linkziel, Bildaustausch und reine Formatänderung als Tests ergänzen.

### F3 – P3: Ein unverändert geöffneter Entwurf gilt bereits als ungespeichert

**Ort:** `frontend/src/pages/WikiPage.tsx:242`, `:244`, `:254`.

Die erste Berechnung von `dirty` vergleicht den Inhalt mit einer zunächst leeren Referenz. Der Effekt setzt anschließend `baseline.current`, löst aber weder eine Neuberechnung noch einen Zustandswechsel aus; die Referenz ist keine Abhängigkeit des gespeicherten Vergleichsergebnisses.

**Reproduktion:** Eine bestehende Seite bearbeiten, ohne Titel oder Inhalt anzufassen. Der Speicherzustand lautet „Ungespeicherte Änderungen“, Speichern ist aktiv und Schließen verlangt eine Bestätigung. `data-dirty` bleibt `true`. Im gesonderten Browsertest sowie in beiden 320-px-Prüfungen mit DE und EN bestätigt.

**Auswirkung:** Falsche Warnungen und irreführender Speicherzustand nach Regel 5; eine unveränderte Seite kann unnötig eine weitere Revision erhalten.

**Vorschlag:** Die normalisierte Ausgangsfassung zusammen mit dem Editor reaktiv initialisieren und die Berechnung davon abhängig machen. Öffnen ohne Eingabe muss sauber sein; die erste tatsächliche Änderung muss den Zustand umschalten.

### F4 – P3: Die zweite HTML-Schranke verändert zulässige geordnete Listen

**Ort:** `frontend/src/pages/WikiContent.tsx:16`, `:41`; Gegenstück `backend/src/main/java/taskmanager/wiki/internal/WikiDocument.java:194`, `:394`.

Der Server erlaubt `orderedList.attrs.start` von 1 bis 1.000.000 und rendert den Startwert. Der React-Konverter übernimmt hingegen nur ein- bis vierstellige Zahlen. Ab 10.000 entfällt das Attribut und der Browser beginnt bei 1.

**Reproduktion:** Eine erlaubte Liste mit `start: 10000` speichern/veröffentlichen beziehungsweise die entsprechende Serverantwort `<ol start="10000"><li><p>Fortsetzung</p></li></ol>` anzeigen. Im unabhängigen Browsertest mit dieser vertragsgemäßen Antwort hat das tatsächlich gerenderte Element `ol.start === 1`; erwartet ist 10.000. Serverprüfung und Serverrenderer wurden dazu am Code abgeglichen.

**Auswirkung:** Leser sehen eine andere Nummerierung als im gespeicherten Dokument. Die zusätzliche Sicherheitsprüfung darf zulässigen Inhalt nicht verfälschen.

**Vorschlag:** Für `start`, `colspan` und `rowspan` jeweils die zum Serverschema passenden Grenzen verwenden. Startwerte 9.999, 10.000 und 1.000.000 über beide Darstellungen prüfen.

### F5 – P2: `wiki.publish` allein erlaubt entgegen Regel 14 den Abruf von Entwurfsbildern

**Ort:** `backend/src/main/java/taskmanager/wiki/internal/WikiService.java:75`, `:235`; Abweichung zwischen `TODO.md:78` und `backend/README.md:518`, ergänzend `contracts/wiki.openapi.yaml:13`.

Die bestätigte Regel 14 lautet ausdrücklich „Entwurfsbilder nur mit `wiki.edit`“. `Access.drafts()` gibt jedoch `edit || publish` zurück und wird auch für den Bildabruf verwendet. README und Vertrag beschreiben die erweiterte Befugnis bereits; eine entsprechende Änderung der bestätigten Regel ist in DEC-123 nicht festgehalten.

**Reproduktion gegen den echten Server:** Eine Seite veröffentlichen, danach ein weiteres Bild hochladen, das in keiner veröffentlichten Revision vorkommt. Ein internes Projektmitglied ausschließlich mit `wiki.publish`, ohne `wiki.edit`, erhält für dessen Bildadresse HTTP 200. Dasselbe Konto erhält auch Entwurf und Historie. Ein Konto ausschließlich mit `wiki.read` erhält für dieses Bild korrekt 404.

**Auswirkung:** Die implementierte Rechtevergabe legt unveröffentlichte Bilder einem zusätzlichen Berechtigtenkreis offen. Dies ist kein nachgewiesenes Leck an reine Leser; es ist eine konkrete Abweichung vom bestätigten Rechteumfang.

**Vorschlag:** Für Entwurfsbilder das bestätigte `wiki.edit` verlangen und die Veröffentlichungsvorschau bei fehlendem Recht entsprechend behandeln. Falls Veröffentlicher bewusst auch ohne Bearbeitungsrecht alle Entwurfsinhalte sehen sollen, muss Holger diese Erweiterung bestätigen und die maßgeblichen Regeln müssen mit Vertrag, Dokumentation und Tests übereinstimmen. Der vorliegende PR allein ersetzt diese fachliche Entscheidung nicht.

## Hinweise

### H1 – Linkprüfung lässt einzelne Steuerzeichen im URL-Inhalt zu

**Ort:** `backend/src/main/java/taskmanager/wiki/internal/WikiDocument.java:38`, `:326`; zweite Schranke `frontend/src/pages/WikiContent.tsx:12`, `:38`.

**Reproduktion:** Je eine Seite mit Link `https://example.test/a<Zeichen>b` anlegen, wobei `<Zeichen>` U+0000, U+0001, U+0008, U+001B oder U+007F ist. Alle fünf Anfragen antworten 201. Der auf Ablehnung ausgelegte unabhängige Test schlägt deshalb fehl. Das Java-Muster `[^\s<>"]` deckt diese Zeichen nicht ab; `strip()` beseitigt innenliegende Zeichen ebenfalls nicht.

**Auswirkung:** Nicht sauber definierte URLs werden gespeichert und können beim Parsen verändert werden. Eine ausführbare Skript-URL oder XSS wurde damit nicht nachgewiesen. `javascript:`, `data:`, protokollrelative Ziele sowie die separat geprüften Tabulator-/Zeilenumbruchvarianten wurden abgewiesen.

**Vorschlag:** Steuerzeichen vor jeder Normalisierung ausdrücklich zurückweisen und die Linkprüfung auf beiden Seiten mit gemeinsamen Negativfällen absichern. Ein Schema-Präfix allein ist keine vollständige URL-Prüfung.

### H2 – Der Browser-Testersatz bildet wichtige Serverregeln nicht ab

**Ort:** `frontend/tests/browser/backend.mjs:1618`, `:1658`, `:1746`, Bild-Upload im anschließenden Wiki-Routenblock.

**Reproduktion und Abgleich:** Der Mock liefert ein Bild allein wegen der Sichtbarkeit seiner Seite aus; die notwendige Zugehörigkeit zur veröffentlichten Revision fehlt. Ein Leser einer veröffentlichten Seite kann dadurch im Mock auch deren unveröffentlichtes Bild laden, während der echte Server im unabhängigen Test korrekt 404 liefert. `wikiAnswer` setzt auch für reine Leser den Entwurfs-ETag, den der echte Controller weglässt. Der Upload akzeptiert im Mock nur PNG; der Server akzeptiert auch JPEG und WebP. Beim Rendern entfallen unter anderem der Startwert geordneter Listen und Tabellen-Spannweiten.

**Auswirkung:** Browserprüfungen können Rechte- und Darstellungsfehler übersehen oder ein vom Server abweichendes Verhalten bestätigen. Dies ist insbesondere bei F4 und beim Schutz von Entwurfsbildern relevant.

**Vorschlag:** Die genannten Fälle als gemeinsame Vertragsbeispiele gegen echten Server und Mock prüfen: unveröffentlichtes Bild 404, Leserantwort ohne Entwurfs-ETag, drei Bildtypen sowie erhaltene erlaubte Attribute. Die Tests für Größen sollten ebenfalls Bytes und dieselbe Grenze wie der Server verwenden.

## Weitere Prüfergebnisse

- **Rechte und Sichtbarkeit:** Außer F5 kein Zugriff reiner Leser auf Entwurfsinhalt, Entwurfstitel, Historie, unveröffentlichte Bilder oder archivierte Seiten festgestellt. Baum und Pfad verwenden die veröffentlichte Fassung; nicht sichtbare Eltern werden übersprungen. Nicht lesbare/archivierte Wiki-Verweise werden ohne verborgenen Titel als „Seite nicht verfügbar“ ausgegeben. Portalkonten und Nichtmitglieder werden ausgeschlossen. Die vorhandenen API-Tests dieser Fälle liefen im abschließenden gezielten Lauf erfolgreich.
- **Prüfung bei Änderungen:** Erstellen, Speichern, Veröffentlichen, Zurücknehmen, Übernehmen, Verschieben, Archivieren, Wiederherstellen und Upload führen über `Permissions.require`. Ein zusätzlicher Test hielt den echten Bildspeicher während eines Uploads an: Der parallel gestartete Rechteentzug wartete nachweislich an der Rechte-Sperre bis zum Upload-Commit. Danach waren Seite und Bild für das Konto nicht mehr abrufbar. Der Bild-GET ist ein Lesevorgang; er prüft aktuelle Rechte über `inspect` und die veröffentlichte Bildzuordnung, er ruft selbst kein `require` auf. Der spätere HTTP-Stream ist nicht als bis zum letzten Byte gegen Rechteentzug gesperrt nachgewiesen.
- **Revisionen und Konkurrenz:** Varianten werden mit `FOR UPDATE` gesperrt; If-Match fehlt mit 428 beziehungsweise ist veraltet mit 412. Revisionen werden eingefügt und nicht überschrieben. Veröffentlichen prüft zusätzlich die ausdrücklich ausgewählte Revision. Beim unabhängigen Rennen Speichern gegen Veröffentlichen waren die Antworten 200 für Speichern und 412 für Veröffentlichen; die ungesehene Revision wurde nicht veröffentlicht. Die vorhandenen Fälle mit sechs konkurrierenden Speicherungen und Übernahme einer alten Revision bestanden.
- **Baum und Sperrreihenfolge:** Baumänderungen nehmen die Projekt-Advisory-Sperre vor den Seitensperren. Speichern/Veröffentlichen nehmen anschließend keine rückwärts gerichtete Baumsperre. In der untersuchten Reihenfolge kein Sperrkreis festgestellt. Zwei zusätzliche Rennen – A unter B gegen B unter A sowie zwei einzeln zulässige Verschiebungen mit zusammen acht Ebenen – ergaben jeweils einmal 200 und einmal 400, ohne hängende Anfrage. Das ist eine gezielte Prüfung, kein Beweis für jede denkbare Ablaufreihenfolge.
- **Dokumentschutz:** Feste Knoten-/Attributlisten, maskierte Texte und Attribute sowie Bildadressen nur aus der eigenen Seite; kein freies HTML. Zusätzlich geprüft: Maskierung von `<`, `>`, `&`, Anführungszeichen und Apostroph; Überschreitung von 1 MiB, 51 Bilder und 35 verschachtelte Zitate werden abgewiesen. Titelgrenze 1–200 und Strukturprüfungen wurden am Code und an vorhandenen Tests abgeglichen. Ausnahme der Linkprüfung siehe H1.
- **Zweite HTML-Schranke und Einfügen:** Im echten React-Konverter wurden gezielt Skripte, Ereignisattribute, Iframes, externe Bilder sowie `javascript:`, `data:` und `//`-Links eingespeist. Keine aktiven Skripte/Frames/Bilder, keine übernommenen Ereignisattribute und keine fremden Netzabrufe beobachtet. Auch das Einfügen fremder Bilder löste keine fremden Abrufe aus. Die Schranke erfüllt damit die untersuchten Schutzfälle; F4 betrifft ihre Wiedergabetreue.
- **Bilder und Bereinigung:** PNG-, JPEG- und WebP-Signaturen akzeptiert, leerer Inhalt 400, SVG 415, genau 5 MiB akzeptiert. Eine tatsächliche Übertragung von 5 MiB plus einem Byte ergab mit Node Fetch eine vollständig lesbare 413-Antwort `image.too_large`. Der veröffentlichte Abruf lieferte die erwarteten Bytes mit `X-Content-Type-Options: nosniff` und `Content-Security-Policy: default-src 'none'; sandbox`. Die Erkennung anhand der Signatur ist die dokumentierte Prüfung, keine vollständige Bilddekodierung. `AttachmentRepository.known` berücksichtigt `file_image`; ein eigener kompletter Bereinigungsdurchlauf mit Wikibildern wurde nicht ausgeführt. Nie gespeicherte Uploads bleiben entsprechend der offengelegten Einschränkung zunächst erhalten.
- **Bedienung:** Vorhandener Konfliktablauf erhält die eigene Eingabe und sperrt weiteres Speichern bis zur Wahl; die Warnung beim Verlassen und der Veröffentlichungsdialog mit Revision, Sprache und Leserkreis wurden geprüft. Bei 320 px in DE und EN kein horizontaler Überlauf, benannte Bedienelemente und passende Inhaltsbeschriftung beobachtet; Tastaturfokus in Werkzeugleiste und Veröffentlichungsdialog geprüft. Screenshots wurden angesehen. Dies ist keine vollständige Barrierefreiheitsprüfung; Speicherzustand und Vergleich bleiben wegen F1–F3 mangelhaft.
- **Migrationen und Vertrag:** `V0055__files_images.sql` und `V0056__wiki_pages.sql` folgen der Namenskonvention. Die lokalen Datenbanktests legten die 53 tatsächlich vorhandenen Migrationen erfolgreich an. Schlüssel, Fremdschlüssel und CHECKs geprüft; Projektgleichheit, Zyklen und Baumtiefe werden im Service abgesichert. Die Revisionszeiger in `wiki_variant` haben selbst keinen Fremdschlüssel auf `wiki_revision`; deren Konsistenz wird hier durch die Anwendung getragen. Keine damit erreichbare API-Verletzung festgestellt. Migrationslisten/-zahlen in `DatabaseFoundationTest`, `ApplicationLifecycleTest` und `lifecycle-check.sh` passen am abschließenden Stand. `aa412f1` ergänzt die zunächst fehlenden Tabellen im Shell-Lifecyclecheck. Der erfolgreiche `WikiContractTest` gleicht 15 Routen/Methoden ab; er beweist keine vollständige Übereinstimmung aller Antwort- und Rechtefälle, siehe H2.
- **Umfang und Pakete:** Keine Umsetzung der ausgeschlossenen Sprachfassungen, Suche, Portal-Wiki, KI, Import/Export, Kommentare oder endgültigen Löschung festgestellt. 53 gegenüber der Basis neue beziehungsweise geänderte npm-Paketinstallationen überprüft, darunter 30 `@tiptap`-Pakete. Alle deklarieren MIT; die vorhandenen Lizenzdateien enthalten jeweils die MIT-Rechteeinräumung. Keine kostenpflichtige Tiptap-Erweiterung oder Tiptap-Cloudanbindung festgestellt.

## Ausgeführte Prüfungen und Grenzen

Lokale Umgebung: Windows, Java 25.0.4.1, Maven 3.9.16, Node 24.22.0, eigene PostgreSQL-16.15-Testinstanz. Die verfügbaren Python-/Maven-Programme wurden direkt aufgerufen; die Befehlsnamen `python3` und die Unix-Datei `./mvnw` waren hier nicht die Startprogramme. Maven lief mit dem Repository-POM und demselben Ziel `verify`.

| Prüfung | Tatsächlich ausgeführtes Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Mit verfügbarem Python ausgeführt: bestanden. |
| `python3 scripts/task_board.py --check` | Mit verfügbarem Python ausgeführt: bestanden. |
| `python3 -m unittest discover -s tests -v` | 38 Tests insgesamt, 37 bestanden, ein vorgesehener Windows-Zeitzonen-Skip. |
| Backend vollständiges `verify`, PostgreSQL 16.15, `5b4446b` | 560 Tests: 558 bestanden, ein Fehlerfall bei `WikiApiTest.foreignContentIsRefused` (Java-HTTP-Client beim gleichzeitig gesendeten zu großen Upload), ein fehlgeschlagener LDAP-Test (`wrongDirectoryPasswordsAreSlowedDownLikeLocalOnes`, 503 statt 401). Gesamtaufruf nicht erfolgreich. |
| Isolierte Wiederholung dieser beiden Backendfälle auf `5b4446b` | LDAP bestanden; Upload-Clientfehler erneut. Der separate Fetch-Test mit tatsächlichem Upload lieferte korrekt 413. |
| Backend `verify` auf `aa412f1`, Auswahl `WikiApiTest,WikiContractTest,LdapLoginTest#wrongDirectoryPasswordsAreSlowedDownLikeLocalOnes` | Alle zehn Tests bestanden; Verpackung der Anwendung erfolgreich. Der neue Uploadtest aus `947e860` prüft die Ablehnung anhand der angekündigten Länge ohne Senden des Körpers. |
| `npm ci` | Erfolgreich; 112 Pakete geprüft, keine gemeldeten Schwachstellen. |
| `npm run verify` | Bestanden: 88 Unit Tests, Typ-/Vertragsprüfung und Build. Vorhandene Bundlerhinweise zu Modul-Direktiven und Paketgröße, kein Buildfehler. |
| `npm run test:browser -- --test-concurrency=1` | Gesamte Suite ausgeführt: 219 Tests, 217 bestanden, zwei fehlgeschlagen. Alle sieben Wiki-Tests bestanden. |
| Isolierte Wiederholung der beiden Browserfehler | Beide bestanden: Browser-Sprache nach Login-Neuladen (`app.test.mjs:49`) und Lesbarkeit des Tags in dunkler Darstellung (`tags.test.mjs:124`). Der volle Erstlauf bleibt als fehlgeschlagen dokumentiert; die isolierten Wiederholungen sind kein zweiter vollständiger grüner Lauf. |
| Eigene API-/Datenbankprüfungen außerhalb des Produktbaums | Acht unterschiedliche Fälle. Sieben positiv abgeschlossen; der Negativtest für die fünf Steuerzeichen schlägt reproduzierbar fehl (H1). Der zunächst zusätzlich auftretende Upload-Clientfehler wurde getrennt mit Fetch geklärt. Rechteabweichung F5 wurde dabei als tatsächliches Verhalten beobachtet. |
| Eigene Browserprüfungen mit echtem gebautem Frontend | Sieben Fälle: drei bestanden (Einfügen/keine fremden Abrufe, mobile Bedienung DE/EN, zweite HTML-Schranke), vier schlagen entsprechend F1–F4 fehl. |
| Statische Prüfung, Lizenzabgleich und `git diff --check` | Ausgeführt. Änderungen dieses Reviews beschränken sich auf diesen Bericht. |

**CI am abschließend geprüften Commit:** Alle fünf Workflows erfolgreich: [Backend](https://github.com/1uedev/task-manager/actions/runs/42634764201), [Frontend](https://github.com/1uedev/task-manager/actions/runs/42634764232), [Repository checks](https://github.com/1uedev/task-manager/actions/runs/42634764306), [Technical qualification](https://github.com/1uedev/task-manager/actions/runs/42634764216), [Image](https://github.com/1uedev/task-manager/actions/runs/42634764202). Beim Backend wurden auch die Schritte „No skipped tests“ und „Start, stop and restart the packaged application“ als erfolgreich verifiziert. Die konfigurierte CI-Datenbank ist PostgreSQL 18.6; die eigenen lokalen Datenbankprüfungen verwendeten ausdrücklich PostgreSQL 16.15.

**Nicht lokal erneut vollständig ausgeführt:** Nach den beiden reinen Test-/Prüfskriptänderungen kein zweiter kompletter Backendlauf mit allen 560 Tests; stattdessen die betroffene Auswahl auf dem endgültigen Stand. Der Unix-Lifecyclecheck wurde lokal nicht gestartet; sein endgültiger Stand wurde gelesen und sein erfolgreicher CI-Schritt geprüft. Keine vollständige lokale Docker-Image-/Neustartprüfung, kein vollständiger eigener Barrierefreiheitstest, kein eigener kompletter Bereinigungsdurchlauf. Die Browserprüfungen verwenden den Testserver; die unabhängigen API- und Sperrprüfungen liefen gegen den echten Backendserver und PostgreSQL.

Die lokalen Rohprotokolle und synthetischen Prüfprogramme liegen im benachbarten Arbeitsverzeichnis `review-tools/wiki093-review-01` (unter anderem `backend-verify.log`, `backend-current-targeted-verify.log`, `browser-all.log`, `browser-failed-isolated.log`, `browser-probes-complete.log`, `api-probes.log`, `api-images-final.log`, `api-upload-lock.log`, `licenses.json`). Sie wurden nicht dem Produktcode hinzugefügt. `TODO.md`, `DONE.md` und Produktcode wurden durch diesen Review nicht geändert. Die fachliche Abnahme bleibt bei Holger.
