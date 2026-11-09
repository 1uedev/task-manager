# Prüfbericht TASK-094 / PR #281

Datum: 9. November 2026. Unabhängige zweite KI: Codex, Sitzung `codex-20261109-wiki-review-02` (DEC-040).

**Empfehlung: nachbessern.** Vier Befunde betreffen den Schutz unveröffentlichter Sprachfassungen, die Sprachwahl und den Ausgangsvergleich. F1 bis F4 sollten vor der Abnahme behoben werden. Die fachliche Abnahme bleibt bei Holger.

## Gegenstand und Maßstab

Geprüft wurde der offene Entwurfs-PR [#281](https://github.com/1uedev/task-manager/pull/281), Branch `claude/project-thread-ipd3h4`, Commit `4f1f7bfdbfe59b1bd9b9b11be118dadbd80785c0`, gegen aktuelles `main` auf `6bf37ed6e991bd11958c392827812abab1285a44`. Eine erneute Abfrage gegen Ende der Prüfung bestätigte denselben offenen PR und denselben Prüfstand. Der Vergleich umfasst 26 Dateien. Produktcode, Vertrag, Tests und Aufgabenstatus wurden nicht geändert; hinzugefügt wird ausschließlich dieser Bericht.

Zuerst gelesen wurden `AGENTS.md`, TASK-094 mit seinen vier Regeln und Regel 3 von TASK-093 in `TODO.md`, DEC-123 und DEC-124 in `DECISIONS.md`, `contracts/wiki.openapi.yaml` Version 0.2.0 und `backend/README.md`, Abschnitt „Sprachfassungen (TASK-094)“. Ergänzend: Sitzungsregeln, relevante Anforderungen, Wiki-Dokumentation, Implementierung, Tests und Demowerkzeug.

Die ausdrücklich vorgegebene Erweiterung aus TASK-093 gilt: **Auch `wiki.publish` ohne `wiki.edit` darf Entwürfe, Historie, Entwurfsbilder und den Ausgangsvergleich lesen.** Das wird hier nicht als Abweichung beanstandet. Abgleichen und Speichern verlangen weiterhin `wiki.edit`.

## Befunde – vor Abnahme zu beheben

### F1 – P2: Leser erfahren von einer nie veröffentlichten Ausgangsfassung

**Datei und Zeile:** `backend/src/main/java/taskmanager/wiki/internal/WikiService.java:612`, `:618`, `:638`; ebenso im Testersatz `frontend/tests/browser/backend.mjs:1679`, `:1698`. Der Vertrag verlangt die offengelegte Ausgangssprache in `contracts/wiki.openapi.yaml:618`.

`languages` blendet unveröffentlichte Fassungen korrekt aus. Die übrigen Metadaten tun das nicht vollständig: `sourceLanguage` nennt Lesern immer die tatsächliche Ausgangssprache, und für die gezeigte Übersetzung entsteht auch dann ein `translation`-Objekt, wenn die Ausgangsfassung nie veröffentlicht wurde.

**Reproduktion:** Seite auf Englisch als Entwurf anlegen, Deutsch hinzufügen und nur Deutsch veröffentlichen. Diese Folge ist erlaubt: `addLanguage` übernimmt ohne Ausgangsveröffentlichung deren Entwurf. Ein reiner Leser fordert `language=de` an und erhält unter anderem:

```json
{
  "language": "de",
  "sourceLanguage": "en",
  "fallback": false,
  "languages": [{"language": "de", "published": true, "changed": false}],
  "translation": {"sourceRevision": null, "sourcePublishedRevision": null, "outdated": false},
  "draft": null
}
```

Diese Projektion wurde durch Aufruf der unveränderten echten `WikiService.view`-Methode mit einer Speicherattrappe und `Access(true, false, false)` reproduziert. Es war kein HTTP-/Datenbanktest. Der Leser kann daraus die Existenz einer englischen Ausgangsfassung ableiten, obwohl Englisch nie veröffentlicht war und im sichtbaren Sprachenbestand fehlt. Titel und Inhalt dieses Entwurfs werden dabei nicht ausgegeben.

**Auswirkung:** Verstoß gegen TASK-094, Regel 2: Auch das Bestehen eines Entwurfs soll Lesern nicht verraten werden. Die Einschränkung der Liste `languages` reicht dafür nicht. Der Vertrag beschreibt hier dieselbe Offenlegung wie die Implementierung und ist deshalb ebenfalls anzupassen.

**Vorschlag:** Die gesamte Leserprojektion anhand veröffentlichter Fassungen bilden. Eine unveröffentlichte Ausgangssprache und die nur intern bekannte Übersetzungsbeziehung nicht offenlegen, beispielsweise mit einem für Leser entsprechend nullable `sourceLanguage` und ohne `translation`, solange keine Ausgangsfassung veröffentlicht ist. Vertrag, generierte Typen und Testersatz gemeinsam anpassen. Die Kombination „Ausgangsfassung nie veröffentlicht, andere Fassung veröffentlicht“ für beide Ausgangssprachen sowie nach Zurücknahme dauerhaft testen.

### F2 – P2: Ein Wechsel der Oberflächensprache aktualisiert die geöffnete Seite nicht

**Datei und Zeile:** `frontend/src/pages/WikiPage.tsx:711`, `:712`; gegenüber dem sprachabhängigen Baum in `:846` und der stabilen Seiteninstanz in `:894`.

`wanted` übernimmt die Oberflächensprache nur beim ersten Mount in `useState`. Ändert sich danach die globale Sprache, bleibt `wanted` unverändert; der Seitenabruf hängt nicht von `language` ab. Der Baum wechselt dagegen seine Sprache.

**Reproduktion:** Als Leser eine beidseitig veröffentlichte Seite mit englischer Oberfläche öffnen, ohne die Sprachleiste zu benutzen. Die Oberfläche auf Deutsch umstellen und auf derselben Seite bleiben. Eine Komponentenprüfung mit der unveränderten `PageView` und dem echten `useLoad` bestätigte: einzige Abrufsprache weiterhin `en`, Titel weiterhin „English title“, `lang="en"`, `fallback=false`, obwohl Deutsch veröffentlicht ist. API, Kontext und äußere Darstellungsbausteine waren Attrappen; ein Browser wurde dafür nicht benutzt. Im Anwendungsbaum gibt es keinen sprachabhängigen Schlüssel, der die Instanz bei diesem Wechsel neu anlegt.

**Auswirkung:** Der Standard folgt nach dem Sprachwechsel nicht mehr der aktuellen Oberflächensprache aus Regel 2. Baum und Seiteninhalt können unterschiedliche Fassungen zeigen, ohne Rückfallhinweis und ohne eine bewusste Fassungswahl der Person.

**Vorschlag:** Standard und ausdrücklich gewählte Fassung getrennt speichern, etwa eine optionale manuelle Wahl mit Ableitung der Standardsprache aus dem aktuellen Sprachkontext. Den Fall ohne manuelle Wahl bei einem Oberflächenwechsel neu laden. Dabei ungespeicherte Eingaben im Editor schützen. Beide Wechselrichtungen als Regression aufnehmen.

### F3 – P2: Der Ausgangsvergleich unterschlägt Änderungen am Titel

**Datei und Zeile:** `frontend/src/pages/WikiPage.tsx:675`, `:678`, `:688`; die Ausgabe „Keine Unterschiede.“ stammt aus `:179` beziehungsweise `frontend/src/i18n/de.ts:1386`.

Die Vergleichsantworten enthalten jeweils `title` und `content`. `SourceChange.compare` verwendet ausschließlich `content`; die alten und neuen Ausgangstitel werden weder verglichen noch angezeigt.

**Reproduktion:** Ausgangsrevision 1 mit Titel „Alter Titel“, danach Revision 2 mit Titel „Neuer Titel“ und identischem Dokument veröffentlichen. In der Übersetzung „Änderungen der Ausgangsfassung zeigen“ auslösen. Die echte `SourceChange`-Komponente wurde mit zwei solchen Revisionsantworten ausgeführt: Ergebnis `wiki.diff.none`, also „Keine Unterschiede.“; keiner der beiden Titel erscheint im Ergebnis.

**Auswirkung:** Eine fachlich relevante Änderung der Ausgangsfassung bleibt unsichtbar. Bearbeitende können nicht beurteilen, ob sie auch den eigenen Übersetzungstitel anpassen müssen. Das betrifft ebenfalls Personen mit ausschließlich `wiki.publish`.

**Vorschlag:** Den Titel zusätzlich zum Blockvergleich ausdrücklich als Vorher/Nachher anzeigen. Nur bei gleichem Titel und gleichem Dokument „Keine Unterschiede.“ melden. Reine Titeländerungen in Deutsch und Englisch testen.

### F4 – P2: Ein vorhandener Vergleich bleibt trotz neuer Ausgangsrevision stehen

**Datei und Zeile:** `frontend/src/pages/WikiPage.tsx:668`, `:670`, `:678`, `:684`, `:688`; aktualisierte Seitenantworten werden in `:720` übernommen.

Die Vergleichseinträge leben unabhängig vom Revisionspaar im lokalen Zustand. Bei einer Änderung von `sourceRevision` oder `sourcePublishedRevision` werden sie weder verworfen noch neu geladen. Die Beschreibung verwendet jedoch sofort die neuen Nummern. Nach dem ersten Vergleich verschwindet außerdem dessen Schaltfläche.

**Reproduktion:** Vergleich der Ausgangsrevisionen 1 und 2 öffnen. In einer anderen Sitzung Ausgangsrevision 3 veröffentlichen. Anschließend beispielsweise einen bereits vorhandenen, noch nicht veröffentlichten Übersetzungsentwurf veröffentlichen, der weiterhin auf Ausgangsrevision 1 beruht. Die Erfolgsantwort aktualisiert `sourcePublishedRevision` auf 3, während dieselbe `SourceChange`-Instanz bestehen bleibt. Die Komponentenprüfung bestätigte den Zustandsfehler direkt durch Aktualisieren der Seiten-Props: Beschreibung nun `to=3`, Dokumentvergleich weiterhin mit Inhalt aus Revision 2, geladene Revisionsnummern ausschließlich `[1, 2]`.

**Auswirkung:** Der sichtbare Vergleich behauptet, die aktuelle Ausgangsfassung abzudecken, lässt deren jüngste Änderungen aber aus. Das kann zu einem falschen Abgleich der Übersetzung führen.

**Vorschlag:** Den Vergleich an Seitenkennung, Ausgangssprache und beide Revisionsnummern binden. Bei Änderung zurücksetzen oder neu laden; verspätete Antworten eines alten Revisionspaars verwerfen. Den beschriebenen Wechsel nach einer zweiten Veröffentlichung als Regression aufnehmen.

## Hinweise

### H1 – Testersatz weicht bei Fehlern und archivierten Fassungen vom Server ab

**Datei und Zeile:** `frontend/tests/browser/backend.mjs:1838`, `:1840`, `:1886`; Gegenstellen `backend/src/main/java/taskmanager/wiki/internal/WikiService.java:367`, `:370`, `:773`.

Der Server prüft bei `addLanguage` die fehlende oder ungültige Sprache vor der Sichtbarkeit und dem Änderungsrecht. Der Testersatz prüft zuerst Recht und Archivzustand: Ein Leser einer veröffentlichten Seite bekommt mit leerem Sprachkörper im Server 400, im Ersatz 403. Außerdem erlaubt der Ersatz einem Veröffentlichenden das Zurücknehmen einer archivierten Fassung, weil vor der Änderung in `:1891` die Archivprüfung fehlt; der Server verweigert dies mit 409 `wiki.page_archived`.

**Auswirkung:** Browserprüfungen können andere Antworten und Zustandsübergänge als der echte Server absichern. Die Archivabweichung betrifft beide Sprachfassungen. Dieser Hinweis beruht auf dem Vergleich der Kontrollflüsse, nicht auf einem ausgeführten HTTP-Test.

**Vorschlag:** Reihenfolge der Prüfungen und Archivschutz angleichen. Leerer Sprachkörper mit Leserecht sowie Zurücknehmen einer archivierten DE-/EN-Fassung als kleine Paritätstests aufnehmen.

### H2 – Fokus und Sprachattribute der ergänzten Ansichten absichern

**Datei und Zeile:** `frontend/src/pages/WikiPage.tsx:637`, `:653`, `:687`, `:688`, ergänzend `:532`.

Nach dem Anlegen verschwindet die betätigte Schaltfläche „Fassung … hinzufügen“; nach dem Laden des Ausgangsvergleichs verschwindet dessen Schaltfläche. Ein gezieltes Fokussieren des Ergebnisses beziehungsweise der neuen Sprachwahl ist nicht vorgesehen. Der Ausgangsvergleich enthält außerdem Texte der Ausgangssprache, erhält aber kein `lang={page.sourceLanguage}`; auch die Revisionsansicht in `:532` hat keinen Fassungs-Sprachcontainer. Titel, Baumlinks, Hauptinhalt und Editorinhalt tragen die Sprache dagegen bereits.

**Auswirkung:** Der Tastaturfokus kann nach dem Entfernen der Schaltfläche verloren gehen; anderssprachige Vergleichs- und Revisionstexte können mit der falschen Aussprache gelesen werden. Die konkrete Fokuswirkung und die 320-px-Darstellung konnten hier nicht im Browser geprüft werden.

**Vorschlag:** Ergebnis oder ausgewählte Sprachschaltfläche gezielt fokussieren und nur die eigentlichen fremdsprachigen Inhalte passend mit `lang` umgeben. Fokusfolge, sichtbaren Fokus, Umbruch und horizontalen Überlauf in beiden Oberflächensprachen bei 320 px prüfen.

### H3 – Der Rückfalltext behauptet zu viel über die Verfügbarkeit

**Datei und Zeile:** `frontend/src/i18n/de.ts:1437`, `frontend/src/i18n/en.ts:1437`; Verwendung `frontend/src/pages/WikiPage.tsx:760`.

„Diese Seite gibt es nicht auf …“ beziehungsweise „This page is not available in …“ erscheint auch dann, wenn die gewünschte Fassung vorhanden ist, aber nur als Entwurf oder nach Zurücknahme. Für Bearbeitende kann die vorhandene Fassung gleichzeitig in der Sprachleiste stehen.

**Auswirkung:** Der Hinweis erklärt den Rückfall ungenau und kann der sichtbaren Sprachleiste widersprechen.

**Vorschlag:** Neutral formulieren, etwa „Angezeigt wird die veröffentlichte Fassung auf …“. Bei Lesern aus dem Hinweis weiterhin keine Aussage über vorhandene Entwürfe ableiten lassen.

### H4 – Sprachübergreifende Sperr- und Grenzfälle fehlen als dauerhafte Nachweise

**Datei und Zeile:** `backend/src/test/java/taskmanager/wiki/WikiApiTest.java:469`, `:805`; `frontend/tests/browser/wiki.test.mjs:483`; `tools/demo/demo.py:463`.

Der bestehende Paralleltest betrifft sechs Speicherungen einer einzigen Ausgangsfassung. Der neue Sprachtest ist eine sequenzielle Folge auf einer deutschen Ausgangsseite. Es fehlen insbesondere zwei gleichzeitige `addLanguage`-Aufrufe, konkurrierendes Speichern/Veröffentlichen je Sprache, Übernahme alter Übersetzungsrevisionen mit deren `sourceRevision`, eine zunächst englische Ausgangsseite sowie ein Übersetzungsvergleich mit ausschließlich `wiki.publish`. Auch ein Sprachabgleich bis zur noch unveröffentlichten Ausgangsrevision wird nicht als eigener Grenzfall geprüft. Die Demoseiten sind nur mit einem Ersatzclient getestet.

**Auswirkung:** Gerade die durch TASK-094 hinzugefügten Kombinationen werden von den vorhandenen grünen Tests nicht vollständig erfasst. Die hiesige fehlende Datenbankausführung lässt diese Nachweislücke offen.

**Vorschlag:** Diese Fälle gegen PostgreSQL aufnehmen, Sperrwartepositionen bei Nebenläufigkeit nachweisbar herstellen und das unveränderte Demowerkzeug gegen ein frisch gestartetes echtes Backend ausführen. Keine Speicherattrappe als Beleg für SQL-Sperren oder Serverintegration verwenden.

## Ergebnis zu den beauftragten Prüfbereichen

Die folgenden Aussagen bezeichnen Codeprüfung, sofern kein gesonderter Ausführungsnachweis genannt ist. Sie ersetzen keinen erfolgreichen Datenbank-/Browserlauf.

| Bereich | Ergebnis |
|---|---|
| `shown`, `order`, Rückfall und Kennzeichen | Reihenfolge gewünschte veröffentlichte Sprache, veröffentlichte Ausgangssprache, andere veröffentlichte Fassung korrekt; kein Entwurf als Leser-Rückfall. `fallback` vergleicht gezeigte und angeforderte Sprache. Ohne Parameter gilt die Ausgangssprache. Diese Projektionen wurden zusätzlich am echten Service mit Speicherattrappe ausgeführt. |
| `view`, `languages`, `translation` | Entwurfsinhalt, Entwurfstitel, `changed`, Revisionsnummern der Übersetzungsgrundlage und Speicherperson/-zeit werden für Leser gefiltert. **Die Existenz der unveröffentlichten Ausgangsfassung bleibt über die Metadaten erkennbar: F1.** Leser-`outdated` beruht auf der veröffentlichten Übersetzungsrevision, Bearbeitenden-`outdated` auf dem Entwurf; beide Projektionen separat ausgeführt. |
| Baum, Pfad, `visibleParent` | Auswahl der Titel aus der jeweils gezeigten Veröffentlichung; unveröffentlichte Eltern werden für Leser übersprungen. Kein zusätzlicher Zugriff auf Entwurfstitel im untersuchten Kontrollfluss gefunden. |
| `visiblePage`, Verweise und Fehler | Für Leser genügt eine beliebige veröffentlichte Fassung; keine Veröffentlichung ergibt `wiki.page_not_found`. Historie und Revisionslesen prüfen das Entwurfsrecht vor der angeforderten Fassung. Änderungen prüfen das Änderungsrecht vor dem Variantenbestand. Dadurch kein zusätzliches Existenzsignal durch `wiki.language_not_found` gegenüber Lesern gefunden. Interne Verweise auf ganz unsichtbare/archivierte Seiten verlieren Titel und Linktext. |
| Bilder und ETag | `imagePublished` verbindet Bildreferenzen mit der veröffentlichten Revision derselben Sprache und erlaubt dadurch Bilder jeder veröffentlichten Fassung. Nur Entwurfsbilder bleiben reinen Lesern verborgen; Archiv nur mit `wiki.publish`. Seiten-ETag wird ausschließlich gesetzt, wenn `page.draft()` vorhanden ist. Keine weitere Entwurfs-Offenlegung gefunden. |
| Zurücknahme und Archiv | Zurücknahme ändert nur die angeforderte Fassung. Eine zurückgenommene Ausgangsfassung verbirgt eine andere Veröffentlichung nicht; dieser Rückfall wurde zusätzlich ausgeführt. Archivierung betrifft die logische Seite mit beiden Fassungen und hebt Kinder an; Wiederherstellung behält Fassungen und Historie. Abweichung im Testersatz: H1. |
| `addLanguage`, `sourceRevision`, alte Revisionen | 400 bei fehlender/ungültiger Sprache, 403 ohne Bearbeitungsrecht bei gültigem Änderungsaufruf, 409 bei vorhandener/Ausgangssprache. Grundlage ist die veröffentlichte Ausgangsrevision, sonst der Ausgangsentwurf. Beim Speichern sind gesetzte Quellen an der Ausgangsfassung ungültig; bei Übersetzungen bleibt die bisherige Quelle erhalten oder wird auf 1 bis Ausgangsentwurf gesetzt. Übernahme kopiert die alte Revision einschließlich ihrer Quelle und schreibt nur die gewählte Sprache als neue Revision fort. |
| Nebenläufigkeit | `changeable` nimmt nach Rechte-/Projektsperren zunächst `wiki_page FOR UPDATE`, danach die jeweilige `wiki_variant FOR UPDATE`. Dadurch sind auch zwei Anlagen derselben zweiten Fassung serialisiert; die zweite prüft den Bestand nach der Seitensperre und sollte 409 erhalten. Speichern/Veröffentlichen prüfen If-Match an der gewählten Variante. Auch Änderungen verschiedener Sprachen sind momentan durch die gemeinsame Seitensperre serialisiert; deren Revisionszähler bleiben getrennt. Baumänderungen nehmen davor die Projekt-Baumsperre, normale Fassungsänderungen fordern sie danach nicht an. Im untersuchten Pfad keine neue umgekehrte Sperrfolge gefunden. **Kein ausgeführter Nachweis an PostgreSQL.** |
| Migration und Migrationszahlen | V0057 ergänzt die nullable, positive `source_revision` und erweitert die Ereignisprüfung um `translated`. Sprachzugehörigkeit und obere Quellgrenze prüft der Service; die Migration verspricht dafür keinen zusätzlichen Fremdschlüssel. Insgesamt 54 Migrationsdateien; `lifecycle-check.sh`, `DatabaseFoundationTest` und `ApplicationLifecycleTest` erwarten konsistent 54 beziehungsweise die Liste bis V0057. Keine Änderung alter Migrationen im PR. |
| Vertrag und Antworten | Version 0.2.0, 16 Methoden/Pfade einschließlich `POST /languages`; Parameter, Antwortfelder und Varianten-ETag im Controller nachvollzogen. Der Vertrag übernimmt die fachliche Metadatenlücke aus F1. `WikiContractTest` prüft nur Methoden/Pfade, nicht Antwortschemata; sein Lauf war datenbankbedingt nicht erfolgreich. |
| Oberfläche und Rechte | Sprachleiste, Hinweis, Abgleich im `EditorPanel`, Fassungsparameter und Veröffentlichungssprache vorhanden. Abgleich setzt beim nächsten Speichern die angebotene veröffentlichte Ausgangsrevision; ohne Häkchen bleibt die Quelle erhalten. `wiki.publish` allein erreicht Hinweis/Vergleich und Historie, erhält aber keinen Editor. Offene Funktionsbefunde: F2 bis F4; Barrierefreiheit und Texte: H2/H3. DE-/EN-Schlüssel vorhanden, Typprüfung erfolgreich. |
| Demo und Umfang | `seed_demo` ruft `seed_wiki` für HELP auf. „Helpdesk Handbuch“ und darunter „Serviceanfrage ergänzen“ werden deutsch veröffentlicht; nur das Kind erhält einen englischen Entwurf und dessen eigenen Titel. Das erfüllt Regel 4. Projektleitung/Verwaltung erhalten alle drei Wikirechte, Entwicklung Lesen/Bearbeiten, Lesergruppe Lesen; Projektmitgliedschaft bleibt erforderlich, Portalgruppen erhalten keine neuen Wikirechte. Keine Erweiterung um weitere Sprachen, automatische/KI-Übersetzung, Suche oder Portal gefunden. **Nicht gegen einen echten Server ausgeführt.** |

## Tatsächlich ausgeführte Prüfungen

Die Werkzeuge wurden lokal im getrennten Arbeitsbereich ausgeführt. Laufzeiten: Python 3.12.14, Node 24.22.1, npm 11.19.1, Maven 3.9.16, Temurin 25.0.5. Node weicht um eine Patchversion von der im Repository verlangten 24.22.0 ab; `npm ci` meldete dies als Engine-Warnung. Java liegt ebenfalls über der dokumentierten Patchversion. Keine dieser Versionen wurde im Repository geändert.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | Erfolgreich: Repository-Vertrag OK. |
| `python3 scripts/task_board.py --check` | Erfolgreich: Aufgabenübersicht aktuell. |
| `python3 -m unittest discover -s tests -v` | Erfolgreich: 40 Tests, einschließlich der beiden neuen Demoprüfungen. |
| `git diff --check` | Erfolgreich; Produktcheckout nach den Prüfungen unverändert. |
| Frontend `npm ci` | Erfolgreich, 112 Pakete installiert; keine gemeldeten Sicherheitslücken. |
| Frontend `npm run verify` | Erfolgreich: Vertragsgenerierung, Typecheck, 90 Unit-Tests und Produktionsbuild. Nicht blockierende Warnung zur Größe des JavaScript-Bundles. Generierte Vertragsdateien bleiben unverändert. |
| Backend `./mvnw --batch-mode --no-transfer-progress … verify` | **Nicht erfolgreich, Exit 1.** Kompilierung erfolgreich. Surefire: 571 Tests, 113 erfolgreich, 458 Fehler, 0 Assertion-Fehlschläge, 0 übersprungene Tests. Alle 458 Fehler gehören zum fehlenden Datenbankzugang beziehungsweise dessen Initialisierungs-/Kontextfolgefehlern. Verwendet wurden die dokumentierten externen DB-Overrides für einen vorgesehenen PostgreSQL-16.10-Testserver auf Loopback. |
| PostgreSQL 16 | Portable 16.10-Binärdateien bereitgestellt; `initdb` scheitert mit `could not create shared memory segment: Operation not permitted` (`shmget`). Auch mit mmap-Einstellungen kein startbarer Cluster. Deshalb **kein Backend-Integrationserfolg gegen PostgreSQL 16**, keine erfolgreiche Migration oder Sperrprüfung. Docker steht nicht zur Verfügung. |
| Frontend `npm run test:browser` | **Nicht erfolgreich, Exit 1.** Chrome endet beim Start mit SIGABRT; 226 Tests werden vom Testläufer abgebrochen, 0 bestehen, 0 werden regulär übersprungen. Dies ist kein Lauf mit 226 fachlichen Fehlschlägen und kein erfolgreicher Browsernachweis. |
| Ergänzende Komponenten-/Serviceprüfungen | Erfolgreich: echte Serviceprojektion mit Speicherattrappe für normalen Rückfall, Entwurfsfilterung, getrenntes `outdated`, zurückgenommene Ausgangsfassung und F1; echte React-Komponenten mit Kontext-/API-Attrappen für F2, F3 und F4. Temporäre Prüfprogramme außerhalb des Repositorys; keine neuen oder geänderten Produkttests. |
| Sichtprüfung/Fokus/320 px | **Nicht ausgeführt.** Die zusätzliche Browseröffnung des lokalen Prüfservers wurde von der automatischen Browser-Freigabe abgelehnt: keine Freigabe für den lokalen Ursprung. Kein Umgehungsversuch; keine Behauptung einer visuellen Abnahme. |
| Demoskript gegen echten Server und Docker-Lifecycle | **Nicht ausgeführt:** kein laufender PostgreSQL-/Backendserver beziehungsweise kein Docker. Quellprüfung und erfolgreiche Python-Tests ersetzen diese beiden Prüfungen nicht. |

Die im PR beschriebenen erfolgreichen Eigenprüfungen des Autors wurden nicht als eigene Ausführung übernommen. Neben den Korrekturen an F1 bis F4 bleiben in dieser unabhängigen Prüfung ein erfolgreicher Datenbanklauf, Browserlauf und der echte Demo-/Lifecycle-Nachweis offen.
