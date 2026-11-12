# Unabhängiger Prüfbericht zu TASK-108 / PR #309

Prüfstand: 12. November 2026. PR #309, Head 469d20acacf0776e6bd402bbeaf117a04e3ad11e auf claude/project-thread-hulabb, Basis cafb85630d5ba1250f2eac07e009e815054a2105. Der PR war bei der Prüfung offen. Zehn geänderte Dateien, ohne Migration oder Vertragsänderung.

Maßstab: [TASK-108](../TODO.md#task-108-befunde-der-codex-prüfung-von-task-107-beheben-a14), DEC-138 in [DECISIONS.md](../DECISIONS.md), [Prüfbericht zu TASK-107](REVIEW_TASK-107_PR-307.md), [AGENTS.md](../AGENTS.md) und „Wiki in der Suche“ im [Backend-README](../backend/README.md).

## Befunde

### F1 · Mittel · Nach dem Verschieben eines veröffentlichten Treffers öffnet die Sprachwahl wieder Entwürfe

Eine Chrome-Probe öffnete die deutsche Veröffentlichung aus der Wiki-Suche. Deutsch und Englisch waren veröffentlicht und hatten jeweils einen neueren Entwurf. Nach „Verschieben“ lieferte der Testersatz 200. Unmittelbar danach zeigten Titel und Inhalt den deutschen Entwurf. Anschließend öffnete ein Klick auf Englisch den englischen Entwurf. In keiner Sprache wurde „Entwurf zeigen“ gewählt.

`changed` setzt nach einem erfolgreichen Verschieben `publishedFirst` auf false. Dadurch gilt der Entwurfszweig unabhängig von `draftLanguage`. Die Sprachleiste löscht `draftLanguage`, stellt den Vorrang der Veröffentlichung aber nicht wieder her. Damit fällt dieselbe veröffentlichte Suchroute nach der Verwaltungsaktion wieder in das Verhalten zurück, das DEC-138 für die Sprachwahl korrigiert. Der Aufruf von `changed` sollte den Einstieg aus der veröffentlichten Suche erhalten oder eine Rückkehr zur Veröffentlichung bei der nächsten Sprachwahl erzwingen. [WikiPage.tsx, changed (line 753)](../frontend/src/pages/WikiPage.tsx#L753) · [Standwahl (line 793)](../frontend/src/pages/WikiPage.tsx#L793) · [Sprachleiste (line 822)](../frontend/src/pages/WikiPage.tsx#L822)

### F2 · Mittel · Ein erlaubter Wiki-Text kann den Heap bei der Markierung erschöpfen

Eine Java-21-Probe markierte das gewöhnliche Wort `wort` in einem Text mit 960.005 UTF-8-Bytes, der danach wiederholt U+FDFA mit Leerzeichen enthielt. U+FDFA wird bei NFKD zu 18 Codepoints. Der Text passt samt einfacher Dokumentstruktur unter die Wiki-Grenze von 1 MiB. Bei `-Xmx256m` endete der Markierungspfad des PR mit `OutOfMemoryError: Java heap space` in `Nfkc.origins`. Dieselbe Probe mit TASK-107 und demselben Heap lieferte [0,4) nach rund 1,7 s. Das ist ein Vergleich des Markierers ohne Datenbank, kein Ausfallnachweis für eine konkrete Produktionsinstallation.

`origins` legt für jedes zerlegte Zeichen ein eigenes `int[]` an und klont es beim Übertragen in die Ergebnisliste. Beide Listen liegen dabei gleichzeitig vor. Der Markierer verarbeitet den ganzen Treffertext, bevor der Ausschnitt begrenzt wird. Die Verfügbarkeit der Suche hängt so bei zulässigem Text und begrenztem Heap vom Ausmaß der Kompatibilitätszerlegung ab. Eine kompaktere Darstellung der Codepoints und Originalbereiche würde den doppelten Objektbestand vermeiden. [Nfkc.java, Ursprünge (line 64)](../backend/src/main/java/taskmanager/search/internal/Nfkc.java#L64) · [SearchSnippet.java, Ausschnitt (line 32)](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L32) · [WikiDocument.java (line 29)](../backend/src/main/java/taskmanager/wiki/internal/WikiDocument.java#L29)

### F3 · Niedrig · Der Testersatz bricht die Suche bei langen gültigen Texten ab

Zwei direkte Aufrufe von `wikiSearchCall` reproduzierten `RangeError: Maximum call stack size exceeded`. Ein Text enthielt `wort` und danach ein 150.000 Zeichen langes Wort. Der andere enthielt A, 150.000-mal U+0334 und ein abschließendes U+0301. Beide Texte passen in ein Wiki-Dokument. Bei langen Wörtern übergeben `Math.min(...)` und `Math.max(...)` einen Funktionsparameter pro UTF-16-Einheit. Bei langen Kombinationen macht `ordered.push(...run.sort(...))` dasselbe pro Zeichen der sortierten Folge. Die Produktionsimplementierung in Java hat dieses Aufruflimit nicht. Eine Schleife für die Übernahme der sortierten Zeichen und das laufende Minimum und Maximum würde die Aufrufgrenze vermeiden. [Testersatz, nfkcOrigins und searchWordPlaces (line 2523)](../frontend/tests/browser/backend.mjs#L2523)

### F4 · Niedrig · Die dokumentierte Sigma-Grenze erfasst Unterschiede innerhalb der BMP nicht

Doppelpunkt und Mittelpunkt liefern jetzt wie Java 21 Schluss-Sigma. Auch die geprüften Verbindungs-, Zahlen-, Format- und Kombinationsfälle mit älteren BMP-Zeichen stimmten überein. Für `AΣ᫏A` erzeugte Java 21 dagegen `aς᫏a`, der Testersatz `aσ᫏a`. Der direkte Suchaufruf im Testersatz ergab null Treffer für `aς` und einen für `aσ`. U+1ACF liegt in der BMP. Java 21 behandelt das Zeichen als unzugewiesen, während Node 24.22.0 mit Unicode 17.0 es als kombinierendes Zeichen behandelt. Das gilt unabhängig von der beschriebenen Einschränkung für ergänzende Codepoints.

Im Vergleich von 166.334 BMP-Kontexten aus Einzelzeichen- und Mehrzeichenproben traten 88 Unterschiede auf. Unter 4.554 Kontexten mit ergänzenden Zeichen traten 1.336 Unterschiede auf. Die Stichprobe enthält gezielt schwierige Fälle und dient als Differenzprobe. Die Abweichung innerhalb der BMP sollte für die eingesetzten Laufzeiten dokumentiert oder über dieselben Zeicheneigenschaften abgesichert werden. [Testersatz, javaLower (line 2448)](../frontend/tests/browser/backend.mjs#L2448)

## Hinweise

- H1 · NFKC, Blockierung und Index. In 22.143 zusätzlichen Differenzfällen stimmten `Nfkc.origins`, `Nfkc.of` und der Java-Normalizer überein. U+034F mit Klasse 0 sowie ein gleichklassiges, nicht zusammengesetztes Zeichen blockierten die folgenden Kombinationen. Ein zuvor aufgenommenes Zeichen mit niedrigerer Klasse erlaubte die spätere Zusammensetzung. Hangul L+V+T ergab U+AC01. Ein dazwischenliegendes U+034F oder ein noch stehendes Kombinationszeichen verhinderte die Kombination. क plus Nukta und U+0344 bestätigten die Ausschlüsse. Die Indexfunktion `Nfkc.of` und die Aufbereitung in `SearchText` sind gegenüber TASK-107 unverändert. [Nfkc.java](../backend/src/main/java/taskmanager/search/internal/Nfkc.java)
- H2 · Abweichende Normalform verwirft die Markierungen. `origins` vergleicht die zusammengesetzte Form mit `Nfkc.of` und liefert bei Abweichung null. `SearchSnippet.Normal.of` liefert auch dann null, wenn die Länge der Gesamtkleinschreibung von der pro Codepoint ermittelten Zahl der Positionen abweicht. Beide Wege ergeben in `words` eine leere Liste. Die bekannte Längenerweiterung bei İ bleibt korrekt markierbar, weil sie in der Positionsliste mitgeführt wird. In den Differenzproben war kein natürlicher Auslöser für die Abbruchzweige enthalten. [Nfkc.java (line 116)](../backend/src/main/java/taskmanager/search/internal/Nfkc.java#L116) · [SearchSnippet.java (line 145)](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L145)
- H3 · Laufzeit bei später Zusammensetzung. Die Präfix-Halbierung ist entfernt. Bei rund 480.000 UTF-16-Einheiten dauerte ein Java-21-Aufruf der späten Zusammensetzung rund 165 ms. Eine gleich lange, gemischte Folge mehrerer Kombinationsklassen dauerte rund 540 ms. Bei rund 240.000 Einheiten waren es rund 79 bzw. 331 ms. Das sind Einzelmessungen des Markierers ohne Datenbank. Die Zusammensetzung selbst läuft einmal über die Zeichen, die kanonische Ordnung nutzt weiterhin stabile Vergleichssortierung. Die Messwerte stützen die Verbesserung der geprüften Folgen. [Nfkc.java (line 84)](../backend/src/main/java/taskmanager/search/internal/Nfkc.java#L84)

## Abgleich mit den Abnahmekriterien

| Kriterium | Ergebnis |
| --- | --- |
| 1 · Sprachwahl | Die drei neuen Browserfälle für dieselbe zurückgezogene Sprache, eine Sprache mit nur einem Entwurf und den Wechsel nach „Entwurf zeigen“ bestanden. Beide Leserfälle sowie die allgemeine 404-Anzeige nach einem Sprachwechsel bestanden in gezielten Proben. Der Verwaltungsweg aus F1 bleibt offen. |
| 2 · Späte Zusammensetzung | Die frühere Präfix-Halbierung ist beseitigt. Die unabhängige Zeitprobe bestätigt die Verbesserung. Die Speicherregression steht unter F2. |
| 3 · Originalbereiche je Wort | Java und Testersatz liefern die erwarteten Bereiche für ½ᅡ ([0,1) und [0,2)), กำ ([0,1) und [1,2)) sowie das Hangul-Wort nach einem ergänzenden Zeichen ([3,4)). Die Langtextgrenzen des Testersatzes stehen unter F3. |
| 4 · Sigma und H2 | Doppelpunkt, Mittelpunkt und die weiteren neuen Fälle sind abgesichert. Die H2-Abbruchzweige sind korrekt angeschlossen. Die dokumentierte Grenze des Testersatzes ist gemäß F4 zu eng. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| PR-Diff und Prüfkopie | Zehn PR-Dateien geprüft. Separate Kopie auf Basis der TASK-107-Prüfkopie und aller seitdem geänderten Dateien des PR-Heads erstellt. Git-Blob-Prüfsummen der übernommenen Dateien abgeglichen. `git diff --no-index --check` für die zehn Dateien ohne Befund. |
| Backend | `mvn -Djava.version=21 verify` gegen PostgreSQL 16.15 bestanden. 637 Tests, 0 Fehler und 0 übersprungen, einschließlich JAR-Build. Der lokale Wiederholungslauf nutzte einen vorgeladenen Mockito-Agent, nachdem der Standardstart des Testprozesses bei AppearanceApiTest stehen blieb. Der PR nennt 638 Tests, der lokale Maven-Bericht weist 637 aus. Zusätzlich liefen 40 gezielte Tests in WikiSearchApiTest, RequirementSearchApiTest und SearchSnippetTest gegen PostgreSQL 16.15 erfolgreich. |
| Java-Differenz- und Lastproben | 22.143 NFKC-Differenzfälle sowie gezielte Blockierungs-, Hangul-, Ausschluss-, Positions-, Laufzeit- und Heap-Vergleiche mit Java 21 ausgeführt. |
| Frontend | `npm run verify` bestanden. Typgenerierung, TypeScript-Prüfung, 102 Tests und Vite-Produktionsbuild erfolgreich. |
| Browser, regulärer Lauf | `npm run test:browser` mit installiertem Windows-Chrome als Ersatz für /opt/pw-browsers/chromium: 291 von 291 Tests bestanden. |
| Gezielte Browser- und Testersatzproben | Sechs Leser- und Sprachwechselfälle aus dem Projekt und eine zusätzliche Leser-/404-Probe mit dem gebauten Frontend ausgeführt. Acht direkte Testersatzproben, darunter beide RangeError-Fälle. Der anfängliche Verschiebeversuch im direkt ausgelieferten Aufbau scheiterte am CSRF-Cookie des Testaufbaus. Die Wiederholung mit dem regulären lokalen Browser-Server bestätigte F1 bei erfolgreicher Mutation. |
| Repository | `validate_repository.py`, `task_board.py --check` und `python -m unittest discover -s tests -v` bestanden. 42 Python-Tests, davon einer unter Windows übersprungen. |
| GitHub Actions am PR-Head | Backend, Frontend, Repository checks, Image und Technical qualification erfolgreich. |

## Empfehlung

Nachbessern. Den veröffentlichten Einstieg auch nach Verwaltungsaktionen respektieren und den Speicherbedarf der Originalzuordnung bei großen Kompatibilitätszerlegungen begrenzen. Anschließend die beiden Grenzen des Testersatzes absichern.
