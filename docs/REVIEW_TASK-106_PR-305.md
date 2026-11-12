# Unabhängiger Prüfbericht zu TASK-106 / PR #305

Prüfstand: 12. November 2026. PR #305, PR-Head 3427f969dedf5d8acefe265e1a19a786f1254a89, Merge auf main als 544906a08cd18ae4fb63be39d0815bc407b6af23. Die Blobs aller elf geänderten Dateien sind an beiden Ständen identisch.

Quelle: Codex Bericht, von Holger am 12. November 2026 im Projektgespräch als Text übergeben.

Maßstab: [TASK-106](../TODO.md#task-106-befunde-der-codex-prüfung-von-task-105-beheben-a14), DEC-136 in [DECISIONS.md](../DECISIONS.md), [Prüfbericht zu TASK-105](REVIEW_TASK-105_PR-302.md), [AGENTS.md](../AGENTS.md) und die [Backend-Dokumentation](../backend/README.md). Der Diff bleibt bei den beauftragten Such- und Wiki-Pfaden und deren Tests. Er enthält keine Migration und keine Vertragsänderung.

## Befunde

### F1 · Mittel · Die Sprachleiste kann nach dem Rückzug den Entwurf der anderen Sprache öffnen

Für ein reines Lesekonto sind beide gemeldeten Fälle abgedeckt. Ein 404 zeigt „Nicht mehr veröffentlicht“. Eine englische Ersatzfassung bleibt im Haupttext verdeckt, bis die Person „Fassung Englisch zeigen“ wählt. Der Server und der Testersatz geben einem reinen Lesekonto nur veröffentlichte Sprachfassungen. Bei ausschließlich englischer Veröffentlichung blendet die Sprachleiste den einzigen Eintrag aus. Der ausdrückliche Wechsel erfolgt über die Schaltfläche im Hinweis.

Bei einem Konto mit wiki.edit oder wiki.publish ist der Ablauf anders. Ausgangslage ist ein veröffentlichter deutscher Treffer, dessen Veröffentlichung zurückgezogen wird. Die englische Fassung hat eine Veröffentlichung und einen davon abweichenden Entwurf. Der Server liefert zunächst die angefragte deutsche Fassung samt Entwurf und listet Englisch in der Sprachleiste. Klickt die Person dort auf „Englisch“, setzt onChoose publishedFirst auf false. Sobald die englische Antwort vorliegt, gewinnt wegen draft.changed der Entwurfszweig. Titel und Haupttext sind dann aus dem englischen Entwurf, obwohl die Person in der Leiste nur die Sprache gewählt hat. Eine Chrome-Probe mit dem unveränderten Produktionsbuild und dem Testersatz bestätigte diesen Ablauf. Die beiden neuen Browsertests verwenden reine Lesekonten und bestanden separat mit demselben Aufbau.

Die Auswahl einer veröffentlichten Sprache nach einem veröffentlichten Suchtreffer sollte den veröffentlichten Stand öffnen. Für den Entwurf kann die bestehende ausdrückliche Aktion verwendet werden. Ein Browsertest mit unterschiedlichen englischen Entwurfs- und Veröffentlichungstiteln würde die beiden Aktionen unterscheiden. [WikiPage.tsx, Sprachleiste und Standwahl (line 779)](../frontend/src/pages/WikiPage.tsx#L779) · [WikiService.java, Wahl der Sprachfassung (line 238)](../backend/src/main/java/taskmanager/wiki/internal/WikiService.java#L238)

### F2 · Hoch · Die Markierung normalisiert wachsende Teilstücke wiederholt und verzögert die Suche

Normal.of normalisiert bei jeder neuen Teilgruppe erneut den Abschnitt vom Beginn des letzten Teilstücks bis zum aktuellen Ende. Wenn NFKC die Zeichen bei jedem Schritt umordnet, wächst dieses Teilstück fortlaufend. Für einen synthetischen Text mit einem gewöhnlichen Suchwort und einer Folge aus U+FF9E und U+0334 maß die Java-21-Probe für einen Aufruf von SearchSnippet.marks rund 0,52 s bei 2.011, 3,35 s bei 4.011 und 27,89 s bei 8.011 UTF-16-Einheiten. Ein gleich langer ASCII-Text dauerte nach dem ersten Durchlauf rund 7–10 ms. Die Messwerte stammen von einem lokalen Probelauf ohne Datenbank und sind keine Ende-zu-Ende-Messung.

Die Wikisuche berechnet den Ausschnitt synchron über den vollständigen Treffertext. Der Begrenzung auf 240 UTF-16-Einheiten geht die Markierung des gesamten Texts voraus. Das Wiki-Dokumentlimit von 1 MiB liegt weit oberhalb des gemessenen Bereichs. Auch die Anforderungssuche nutzt den Markierer bei der Wahl des Ausschnittfelds. Die wiederholte Normalisierung wachsender Präfixe sollte aus dem Markierungspfad entfernt und die Laufzeit mit längeren Texten geprüft werden. [SearchSnippet.java (line 114)](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L114) · [SearchService.java (line 548)](../backend/src/main/java/taskmanager/search/internal/SearchService.java#L548) · [WikiDocument.java (line 29)](../backend/src/main/java/taskmanager/wiki/internal/WikiDocument.java#L29)

### F3 · Mittel · NFKC-Teilstücke können die Fundstelle über ihre Ursprungszeichen hinaus verlängern

Im Original `ガ゙ﾞ̴ﾞ̴ end` ergibt sich für das Indexwort ガ in Java und im Testersatz der Bereich [0,7). Die letzten beiden U+FF9E bleiben nach NFKC eigenständige kombinierende Zeichen. Auch die beiden U+0334 bleiben eigenständig. Weil die Umordnung das Teilstück wachsen lässt, landen sie trotzdem in der Wortmarkierung. Der Bereich wird mit jeder zusätzlichen Gruppe länger, obwohl das Indexwort dasselbe bleibt.

Die Java-Probe zeigt zudem, dass die verketteten Stücke allgemein von der NFKC des Gesamttexts abweichen können. Bei `=ﾞﾞ̸` liefert die Gesamtnormalisierung U+2260 U+3099 U+3099, die Markierernormalform U+003D U+0338 U+3099 U+3099. Dieses Beispiel erzeugt in beiden Fällen keine Indexwörter. Es belegt, dass der paarweise Vergleich die bereits abgeschlossene Grenze nicht nachträglich korrigiert. Die Worterkennung sollte die tatsächliche NFKC- und Kleinschreibungsfolge des Index verwenden. Die Positionszuordnung sollte die vom jeweiligen Wort stammenden Originalzeichen erfassen. [SearchSnippet.java (line 122)](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L122) · [SearchText.java (line 59)](../backend/src/main/java/taskmanager/search/internal/SearchText.java#L59)

### F4 · Niedrig · Der Testersatz findet bei weiterem Sigma-Kontext andere Treffer als Java

Für den Titel `AΣ1A` liefert Java 21 bei SearchText.terms das Wort `aσ1a` mit gewöhnlichem Sigma. JavaScript erzeugt mit `.toLowerCase()` das Wort `aς1a` mit Schluss-Sigma. Im direkten Test von wikiSearchCall liefert die Suche nach `aσ1a` daher null Treffer und die Suche nach `aς1a` einen Treffer. Auch `AΣ-A` und `AΣ_A` zeigen die Abweichung in der Kleinschreibung. Im Server stimmen Indexwort und Markierer für diese Fälle überein. Die Abweichung liegt im JavaScript-Testersatz und betrifft bereits die Treffermenge, also auch die möglichen Markierungen. Der neue Fall `İ ΟΣ` ist für beide Laufzeiten grün. Ein zusätzlicher Vergleichsfall mit abweichendem Wortkontext würde die Grenze des Ersatzes sichtbar machen. [backend.mjs, Suchwörter (line 2448)](../frontend/tests/browser/backend.mjs#L2448) · [SearchText.java (line 59)](../backend/src/main/java/taskmanager/search/internal/SearchText.java#L59)

## Hinweise

- H1 · Status der Ersatzfassung. Bei `stage === 'gone'` und englischem Fallback zeigt der Hauptbereich bereits den Hinweis zum deutschen Suchtreffer. Die allgemeine Statuszeile nennt zugleich Revision, Person und Zeitpunkt der englischen Veröffentlichung, ohne die Sprache dazuzuschreiben. Der Lesefall wäre eindeutiger, wenn die Statuszeile erst beim Öffnen der Ersatzfassung erschiene oder diese ausdrücklich benennen würde. [WikiPage.tsx (line 820)](../frontend/src/pages/WikiPage.tsx#L820)
- H2 · Archivpfadtest erfüllt. anArchivedParentKeepsThePathUpToTheRoot veröffentlicht „Wurzel“ und speichert danach „Wurzel Entwurf“. Die getrennten Suchabfragen verlangen den jeweils passenden Titel und dieselbe Wurzel-ID. Beide verlangen den Platzhalter für den archivierten Elternknoten. Damit erkennt der Test eine Verwechslung der beiden Wurzelstände. [WikiSearchApiTest.java (line 552)](../backend/src/test/java/taskmanager/search/WikiSearchApiTest.java#L552)

## Abgleich mit den Abnahmekriterien

| Kriterium | Ergebnis |
|---|---|
| 1 · Leserpfade nach Rückzug | Beide neuen Browsertests vorhanden. Direkte Proben am Testersatz bestätigen 404 sowie 200 mit englischem Fallback und draft: null. Der Sprachwechsel mit Entwurfsrecht bleibt gemäß F1 offen. |
| 2 · Index und Markierungen | Die Java-Proben bestätigen Kompatibilitäts-Jamo, moderne Jamo, Halbbreiten-Jamo, Schluss-Sigma nach İ und UTF-16-Positionen bei einem ergänzenden Codepoint. Der neue Anforderungs-API-Test prüft die Wahl des zweiten Kriteriums und den markierten Titel. Die ursprünglichen Beispiele sind korrigiert. Laufzeit und weitere Originalpositionen bleiben gemäß F2 und F3 offen. |
| 3 · Testersatz, Archivpfad und bestehende Prüfungen | U+FEFF liefert am Testersatz wie im Java-Pfad eine angegebene Suche ohne Wörter und null Treffer. javaStrip übernimmt die Java-Leerraumgrenzen. H2 ist erfüllt. Die weitere Abweichung der Sigma-Regeln im Testersatz ist F4. Der Status der lokalen Läufe steht unten. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| PR-Diff und Prüfkopie | Elf Änderungen geprüft. Lokale Prüfkopie aus einem vorhandenen Git-Archiv und den geänderten Dateien des Merge-Stands erstellt. Git-Blob-Prüfsummen abgeglichen. `git diff --no-index --check` für alle elf Dateien ohne Befund. |
| Backend, Java 21 und PostgreSQL 16.15 | `mvn -Djava.version=21 verify` mit lokalem PostgreSQL 16.15 gestartet. 272 Haupt- und 82 Testquellen kompiliert. PostgreSQL war betriebsbereit, aber Java und psql durften lokal keine TCP-Verbindung zur Datenbank aufbauen (Permission denied: getsockopt bzw. WinError 10013). Der vollständige lokale Lauf wurde nach den Verbindungsfehlern beendet. |
| Gezielte Java- und JavaScript-Proben | Alle neun JUnit-Tests in SearchSnippetTest mit Java 21 bestanden. Zusätzliche Java-21-Proben für Normalformen, Positionen und Laufzeit ausgeführt. Acht direkte Tests bzw. Diagnoseproben am unveränderten Testersatz ausgeführt, darunter die fünf Wiki-Vergleichstests aus dem Projekt. |
| Frontend | `npm run verify` lokal bestanden. Typgenerierung, TypeScript-Prüfung, 102 Tests und Vite-Produktionsbuild erfolgreich. |
| Browser | `npm run test:browser` mit installiertem Windows-Chrome als Ersatz für /opt/pw-browsers/chromium ausgeführt. Von 285 Tests bestanden zehn. 275 scheiterten im regulären Lauf bei der gesperrten Navigation zum lokalen Testserver (ERR_NETWORK_ACCESS_DENIED). Separat lieferte Playwright den unveränderten Build und den Testersatz direkt aus. Beide neuen Leserfälle bestanden. Eine dritte Browserprobe bestätigte F1 und zeigte stage: draft, Titel „English draft“ und den englischen Entwurfstext nach einem Klick auf „Englisch“. |
| Repository | `validate_repository.py` und `task_board.py --check` bestanden. `unittest discover -s tests -v`: 42 Tests, 1 übersprungen, 9 Fehler und 1 fehlgeschlagene Folgeassertion in den Loopback-Tests des Demo-Werkzeugs. Die lokalen TCP-Verbindungen wurden mit WinError 10013 abgewiesen. |
| GitHub Actions am PR-Head | Backend, Frontend, Repository Checks, Image und Technical Qualification erfolgreich. |

## Empfehlung

Nachbessern. Vor der Abnahme die veröffentlichte Ansicht bei der Sprachwahl beibehalten und die Normalisierung so ändern, dass Markierung und Index dieselbe Wortfolge verwenden, die Originalpositionen stimmen und die Laufzeit bei langen Texten begrenzt bleibt. Den zusätzlichen Sigma-Fall im Testersatz absichern.
