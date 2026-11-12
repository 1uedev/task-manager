# Unabhängiger Prüfbericht zu TASK-107 / PR #307

Prüfstand: 12. November 2026. PR #307, PR-Head 48c029d6f5f85e5da005755ca209393b0e82c268, Merge auf main als 460ef96276ccfab74d8414138caec0ea6c96e39f. Zwölf geänderte Dateien. Keine Migration oder Vertragsänderung.

Maßstab: [TASK-107](../TODO.md#task-107-befunde-der-codex-prüfung-von-task-106-beheben-a14), DEC-137 in [DECISIONS.md](../DECISIONS.md), [Prüfbericht zu TASK-106](REVIEW_TASK-106_PR-305.md), [AGENTS.md](../AGENTS.md) und der Abschnitt „Wiki in der Suche“ im [Backend-README](../backend/README.md).

## Befunde

### F1 · Mittel · Die Sprachleiste öffnet in mehreren Fällen einen Entwurf direkt

Für den beauftragten Hauptfall funktioniert die Änderung. Nach dem Rückzug der gefundenen deutschen Veröffentlichung öffnet die Wahl der weiterhin veröffentlichten englischen Fassung deren veröffentlichten Titel und Text, auch wenn ein neuerer englischer Entwurf vorliegt. „Entwurf zeigen“ öffnet anschließend den englischen Entwurf. Der geladene Zustand `found` bindet die spezielle 404-Anzeige an die ursprünglich gefundene Sprache.

Die Sprachleiste setzt `found` bei jeder Wahl zurück und behält den Wert von `publishedFirst`. Zwei Wege führen dadurch vom veröffentlichten Suchtreffer in einen Entwurf:

| Ausgangslage und Aktion | Ergebnis |
| --- | --- |
| Deutsche Veröffentlichung zurückgezogen. Im Hinweis „Nicht mehr veröffentlicht“ erneut Deutsch in der Leiste wählen oder in eine nur als Entwurf vorhandene englische Fassung wechseln. | Mit `found === undefined` und `page.published === null` fällt die Standwahl auf `usual` zurück. Titel und Text stammen sofort aus dem gewählten Entwurf. |
| Veröffentlichten deutschen Treffer öffnen, „Entwurf zeigen“ für Deutsch wählen, dann auf das veröffentlichte Englisch mit neuerem Entwurf wechseln. | `publishedFirst` bleibt false. Die Wahl von Englisch übernimmt die frühere Entwurfsentscheidung und zeigt den englischen Entwurf. |

Drei zusätzliche Chrome-Proben mit dem unveränderten Frontend-Build bestätigten die beschriebenen Entwurfsansichten. Eine vierte Probe lieferte nach der Sprachwahl einen 404 für die gewählte Fassung. Sie bestätigte die allgemeine Nicht-gefunden-Meldung und das Ende der speziellen Rückzugsanzeige.

Die Standwahl nach einem veröffentlichten Treffer sollte eine Sprachwahl als neue Entscheidung über die Veröffentlichung behandeln. Fehlt die Veröffentlichung, kann die Seite einen Hinweis mit einer eigenen Aktion für den Entwurf zeigen. Die bereits bestätigte Entwurfsansicht sollte auf die Sprache begrenzt sein, für die sie gewählt wurde. [WikiPage.tsx, Standwahl (line 786)](../frontend/src/pages/WikiPage.tsx#L786) · [WikiPage.tsx, Sprachleiste (line 813)](../frontend/src/pages/WikiPage.tsx#L813)

### F2 · Mittel · Späte Zusammensetzung erfordert weiterhin mehr als linearen Aufwand

Der Langtext aus dem Vorgängerbericht läuft erheblich schneller. Die Teilstücke wachsen bei der Erkennung der Abschnittsgrenzen nicht mehr schrittweise. Für einen Buchstaben, der erst am Ende eines langen Abschnitts seine endgültige Form bekommt, untersucht `wordEnd` allerdings wiederholt fast den gesamten Abschnitt. Bei A, gefolgt von vielen U+0334 und abschließend U+0301, ergibt erst der vollständige Abschnitt das Indexwort á. Jeder Halbierungsschritt normalisiert erneut einen Präfix ab der Abschnittsgrenze. Das sind in diesem Fall logarithmisch viele Präfixe linearer Länge und damit mindestens Ω(n log n) Arbeit über diese Präfixe. Die vorherige Normalisierung des gesamten Texts kommt hinzu.

| Original in UTF-16-Einheiten | Messung für späte Zusammensetzung |
| --- | --- |
| 40.002 | 65 ms |
| 80.002 | 116 ms |
| 160.002 | 461 ms |
| 480.002 | 1.807 ms |

Die ersten drei Werte sind Mittelwerte aus je drei Aufrufen der Java-21-Probe. Der letzte Wert stammt aus einer separaten Einzelmessung. Gemischte Abschnitte mit normalen Wortgrenzen dauerten bei rund 160.000 Einheiten etwa 105 ms, der früh zusammengesetzte Vergleichsfall etwa 112 ms. Die Zeitangaben messen den Markierer ohne Datenbank. Die Aufwandsaussage folgt aus den wiederholten Präfixlängen, unabhängig von Messschwankungen. Der Laufzeittest im PR deckt eine frühe Zusammensetzung und eine Folge ohne eigenes Indexwort ab. Ein zusätzlicher Fall mit spätem Kompositionszeichen deckt den teureren Suchpfad ab. [SearchSnippet.java, wordEnd (line 172)](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L172) · [SearchSnippetTest.java](../backend/src/test/java/taskmanager/search/internal/SearchSnippetTest.java)

### F3 · Mittel · Mehrere Wörter eines Normalisierungsabschnitts erhalten denselben Originalbereich

`Normal.of` ordnet allen Buchstaben und Ziffern eines Abschnitts denselben Bereich zu. `wordEnd` sucht den kürzesten Präfix für die Buchstaben und Ziffern des gesamten Abschnitts. Ein Abschnitt kann mehrere Indexwörter enthalten. Java 21 lieferte diese Ergebnisse für einzelne Suchwörter:

| Original | Suche | Tatsächlich | Erwarteter UTF-16-Bereich |
| --- | --- | --- | --- |
| ½ᅡ (U+00BD U+1161) | 1 | [0,2) | [0,1). Die Ziffer stammt aus ½. |
| กำ (U+0E01 U+0E33) | ก | [0,2) | [0,1). U+0E33 ergibt das separate Indexwort า nach einem kombinierenden Zeichen. |
| กำ | า | [0,2) | [1,2). Das Wort stammt aus U+0E33. |
| U+10400 U+0334 U+1161 | ᅡ | [0,4) | [3,4). Das ergänzende erste Zeichen belegt zwei UTF-16-Einheiten. |

Bei ½ allein markieren beide Ziffern korrekt dasselbe Originalzeichen. Bei ½ᅡ gilt das nur für die Ziffern. Das Jamo gehört zum zweiten Indexwort. Auch der Testersatz liefert für ½ᅡ bei Suche nach 1 den zu breiten Bereich. Die Wortfolge war in diesen Proben korrekt. Die Positionszuordnung braucht innerhalb eines Abschnitts die Ursprünge des einzelnen Worts. Der bestehende Zufallstest prüft, dass der normalisierte Ausschnitt das Wort enthält. Ein zu breiter Ausschnitt erfüllt diese Prüfung ebenfalls. [SearchSnippet.java, Normal.of (line 121)](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L121) · [Testersatz (line 2500)](../frontend/tests/browser/backend.mjs#L2500)

### F4 · Niedrig · Bei Doppelpunkt und Mittelpunkt weicht die Sigma-Regel des Testersatzes von Java ab

Die beauftragten Fälle AΣ1A, AΣ-A und AΣ_A sind korrigiert. Für AΣ:A und AΣ·A erzeugt Java 21 das Indexwort aς mit Schluss-Sigma. `javaMidCharacter` behandelt Doppelpunkt und Mittelpunkt im Testersatz als Verbindungszeichen und erzeugt aσ. Der direkte Test von `wikiSearchCall` mit AΣ:A ergab null Treffer für aς und einen für aσ. Das ist derselbe Buchstabenkontext wie in den zugesagten Fällen mit Bindestrich und Unterstrich. Der Unterschied liegt im Satzzeichen. Die dokumentierte Grenze bei Buchstaben anderer Schriften erklärt diese Fälle daher nicht. Die beiden Trennzeichen sollten im Vergleich mit Java abgesichert werden. [Testersatz, javaLower (line 2448)](../frontend/tests/browser/backend.mjs#L2448) · [SearchText.java (line 57)](../backend/src/main/java/taskmanager/search/internal/SearchText.java#L57)

## Hinweise

- H1 · Statuszeile erfüllt. Bei gone fehlt die Statuszeile. Nach „Fassung Englisch zeigen“ sieht das Lesekonto die englische Veröffentlichung und deren Status mit Sprache. Bei einer gezielten Wahl von Englisch ohne serverseitigen Fallback erscheint der normale Veröffentlichungsstatus. [WikiPage.tsx (line 822)](../frontend/src/pages/WikiPage.tsx#L822)
- H2 · Normalform und defensive Abweichung. In den Differenzproben entsprach die verkettete Abschnittsnormalform der NFKC des ganzen Texts. Der defensive Zweig in `Normal.of` würde bei einer Abweichung dennoch die Abschnittsnormalform für die Worterkennung verwenden. Das kann die dokumentierte Gleichheit mit dem Index in diesem Zweig nicht garantieren. Für Java 21 wurde kein auslösender Fall gefunden. [SearchSnippet.java (line 148)](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L148)

## Abgleich mit TASK-107 und DEC-137

| Abnahmekriterium | Ergebnis |
| --- | --- |
| 1 · Sprachwechsel bei Entwurfsrecht | Der neue Browsertest deckt die englische Veröffentlichung mit neuerem Entwurf ab. Die weiteren Wege in F1 bleiben offen. |
| 2 · Laufzeit bei langen Folgen | Der neue Test sichert den ursprünglichen U+FF9E/U+0334-Fall. Späte Zusammensetzung verletzt die geforderte lineare Laufzeit gemäß F2. |
| 3 · Gleichheit mit dem Index und Markierungsgrenzen | Die Katakana-Regression besteht. Die Differenzproben zeigen keine Änderung der Indexnormalform. Die Originalbereiche bei mehreren Wörtern eines Abschnitts bleiben gemäß F3 offen. |
| 4 · Testersatz und Status | Die genannten Sigma-Fälle und H1 sind abgedeckt. Die Abweichungen an Doppelpunkt und Mittelpunkt bleiben gemäß F4 offen. |

## NFKC und Index

Die Java-21-Proben verglichen `Nfkc.of` und den erzwungenen Sortierpfad `Nfkc.ordered` mit `Normalizer` für 5.000 gemischte und 2.000 längere synthetische Texte. Enthalten waren kombinierende Klassen in wechselnder Reihenfolge, U+034F und andere kombinierende Zeichen mit Klasse 0, NFKD-Zerlegungen wie U+0F73, Hangul und ergänzende Codepoints. Die Proben verglichen außerdem die kleingeschriebene Gesamtform mit der für Markierungen verwendeten Form. Alle Vergleiche waren gleich.

Auf bereits NFKD-zerlegten Codepoints erkennt die erste NFD-Vertauschung positive Klassen unterhalb der Klasse von U+0301 (230). Die zweite erkennt alle Klassen oberhalb der Klasse von U+0334 (1). Zusammen decken die beiden Vergleiche alle positiven Klassen ab. Stabile Sortierung erhält die Reihenfolge gleicher Klassen. Ein Codepoint mit Klasse 0 beendet den Lauf auch dann, wenn sein Unicode-Typ ein kombinierendes Zeichen ist. Die Untersuchung definierter Java-21-Codepoints fand vier Nicht-Mark-Zeichen mit einem führenden Mark in NFKD. U+FF9E und U+FF9F zählt der Schnellpfad mit. U+0E33 und U+0EB3 zerfallen jeweils in ein führendes Zeichen der Klasse 0 und einen folgenden Starter, sodass sie keine lange Folge positiver Kombinationsklassen verbergen.

`SearchText` verwendet denselben nachgelagerten Ablauf für Kleinschreibung, Worttrennung und Indexgrenze. Auch die beiden Aufrufe für Kennungen in `SearchService` wechseln nur die NFKC-Funktion. Die geprüften Ergebnisse ergeben keinen Anlass für einen Index-Neuaufbau. [Nfkc.java](../backend/src/main/java/taskmanager/search/internal/Nfkc.java) · [SearchText.java](../backend/src/main/java/taskmanager/search/internal/SearchText.java)

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| PR-Diff und Prüfkopie | Alle zwölf PR-Dateien geprüft und ihre Git-Blob-Prüfsummen zwischen PR-Head und Merge abgeglichen. Die Prüfkopie beruht auf der TASK-106-Prüfkopie und den seitdem geänderten Dateien des Merge-Stands. `git diff --no-index --check` für die zwölf PR-Dateien ohne Befund. |
| Backend | `mvn -Djava.version=21 verify` mit PostgreSQL 16.15 gestartet. Haupt- und Testquellen kompiliert. PostgreSQL war betriebsbereit. Java und psql durften lokal keine TCP-Verbindung zur Datenbank aufbauen (Permission denied: getsockopt bzw. WinError 10013). Der vollständige Lauf wurde nach den Verbindungsfehlern beendet. Alle 13 Tests in SearchSnippetTest bestanden in einem separaten Java-21-Lauf. |
| Java-Differenz- und Laufzeitproben | 7.000 Vergleiche gegen den Normalizer, Untersuchung der NFKD-Anfänge definierter Codepoints und Prüfungen für Katakana, Jamo, Thai, Brüche, Sigma und ergänzende Codepoints. Laufzeitproben mit frühen, späten und gemischten Kompositionen. |
| Frontend | `npm run verify` lokal bestanden. Typgenerierung, TypeScript-Prüfung, 102 Tests und Vite-Produktionsbuild erfolgreich. |
| Browser, regulärer Lauf | `npm run test:browser` mit Windows-Chrome als Ersatz für /opt/pw-browsers/chromium ausgeführt. Von 287 Tests bestanden elf. Die übrigen 276 scheiterten bei der Navigation zum lokalen Testserver mit ERR_NETWORK_ACCESS_DENIED. |
| Chrome ohne TCP und Testersatz | 14 gezielte Tests und Diagnoseproben erfolgreich ausgeführt. Playwright lieferte den unveränderten Build und die Antworten des Testersatzes direkt aus. Sieben Browserfälle deckten Leser, Statuszeile, Sprachwahl, Entwurfswahl und 404 ab. Die Gegenproben bestätigen die beobachteten Abweichungen F1, F3 und F4. |
| Repository | `validate_repository.py` und `task_board.py --check` bestanden. `unittest discover -s tests -v`: 42 Tests, 1 übersprungen, 9 Fehler und 1 fehlgeschlagene Folgeassertion in Loopback-Tests des Demo-Werkzeugs. Die lokalen TCP-Verbindungen wurden mit WinError 10013 abgewiesen. |
| GitHub Actions am PR-Head | Backend, Frontend, Repository Checks, Image und Technical Qualification erfolgreich. |

## Empfehlung

Nachbessern. Die Sprachwahl aus einem veröffentlichten Treffer vom Entwurfsstand lösen. Die Markierung je Indexwort an dessen Originalzeichen binden und den wiederholten Präfixaufwand bei später Zusammensetzung beseitigen. Die bestätigte Sigma-Abweichung im Testersatz absichern.
