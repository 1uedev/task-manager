# Unabhängiger Prüfbericht zu TASK-105 / PR #302

Prüfstand: 11. November 2026, PR-Head 8514289541cf699e23025cf290a12ca8354f7375, Merge-Commit 2f1a4a69330653dd4e6f6e613a344b33c4e2207e. Geprüft wurden die 17 geänderten Dateien gegen AGENTS.md, TASK-105 und TASK-102 in TODO.md, DEC-135, die Nachprüfung zu PR #299 und die Backend-Dokumentation. Der Diff enthält keine Migration und keine Änderungen an TASK-104.

Quelle: Codex Bericht, von Holger am 12. November 2026 im Projektgespräch als Text übergeben.

## Befunde

### F1 · Mittel · Reine Lesekonten sehen nach dem Zurückziehen den vorgesehenen Hinweis nicht

Ein Mitglied mit ausschließlich wiki.read findet eine veröffentlichte deutsche Fassung. Wird diese vor dem Klick zurückgezogen, antwortet der Seitenabruf mit 404, falls keine andere Fassung veröffentlicht ist. Ist Englisch noch veröffentlicht, liefert der Server die englische Fassung als Sprachfallback. In diesem Fall sieht WikiPage.tsx eine vorhandene Veröffentlichung und wählt published. In beiden Fällen fehlt für die ursprünglich gefundene Fassung „Nicht mehr veröffentlicht“.

Die Leseansicht bezieht Überschrift und Haupttext aus demselben Stand. Für Personen mit Entwurfsrecht greift der neue gone-Zweig im geprüften Fall, und der Haupttext des Entwurfs erscheint nach „Entwurf zeigen“. Der neue Rückzugstest verwendet ein Konto mit Lese-, Bearbeitungs- und Veröffentlichungsrecht. Eine zusätzliche Probe am Testersatz bestätigte für ein reines Lesekonto beide API-Ausgänge: 404 bei einsprachiger Seite, 200 mit language=en und fallback=true bei weiterhin veröffentlichter englischer Fassung.

Korrekturvorschlag: Beim Einstieg aus einem veröffentlichten Suchtreffer die angefragte Sprachfassung ausdrücklich berücksichtigen. Den Hinweis auf die nicht mehr verfügbare Fassung auch im Leseweg abdecken und dabei die bestehenden Zugriffsgrenzen wahren. Beide Fälle mit einem reinen Lesekonto testen. (WikiPage.tsx, WikiService.java, Rückzugstest)

### F2 · Mittel · Die Markierung weicht bei Hangul und griechischem Sigma vom Index ab

SearchText.terms normalisiert den ganzen Text mit NFKC und setzt ihn als Ganzes in Kleinschreibung um. SearchSnippet normalisiert einzelne Gruppen. Wenn die Kleinschreibung den Text verlängert, ersetzt es außerdem die Kleinschreibung des Gesamttexts durch die Kleinschreibung einzelner Codepoints. Daraus ergeben sich zwei unabhängige Abweichungen.

| Original | Suche | Abweichung |
|---|---|---|
| ㄱㅏ (U+3131, U+314F) | 가 | NFKC setzt die beiden Kompatibilitäts-Jamo im Gesamttext zu 가 zusammen. Die getrennte Normalisierung ergibt 가, sodass der Präfixvergleich keine Markierung findet. |
| İ ΟΣ | ος mit Schluss-Sigma ς | Die Kleinschreibung des Gesamttexts ergibt i̇ ος. Die Erweiterung von İ löst den Rückfall aus. Dessen zeichenweise Kleinschreibung erzeugt οσ mit gewöhnlichem Sigma σ. |

Am Testersatz lieferte die Suche in beiden Fällen je einen Treffer. Die Hangul-Fundstelle blieb im Ausschnitt und die Sigma-Fundstelle im Titel unmarkiert. Der Java-Code hat dieselben beiden Abweichungen. Die Anforderungssuche verwendet dieselbe Markierung auch bei der Wahl des Ausschnittfelds. Eine fehlende Markierung kann dort zusätzlich dazu führen, dass das passende Kriterium oder Textfeld bei der Ausschnittwahl übergangen wird. Der vorhandene Ⓐlpha-Fall deckt beide Ursachen nicht ab.

Korrekturvorschlag: Die Wörter aus derselben Normalform wie der Index ableiten und die Zuordnung zu den UTF-16-Positionen des Originals erhalten. Beide Gegenbeispiele in Java absichern und mindestens eines über die Anforderungssuche bis zum Ausschnitt prüfen. (SearchText.java, SearchSnippet.java, Anforderungssuche)

### F3 · Niedrig · Der Testersatz behandelt eine Eingabe mit U+FEFF als leere Suche

Bei q=%EF%BB%BF entfernt JavaScripts .trim() das einzige Zeichen. Der Testersatz behandelt die Eingabe dadurch als leere Suche und listet sichtbare Seiten. In der Probe lieferte er einen Treffer. Java behandelt U+FEFF mit isBlank() und strip() anders. SearchText.parse behält eine angegebene Suche ohne Suchwort, und SearchService.wiki antwortet mit total: 0 und leeren items. Die Abweichung betrifft die Treffermenge und besteht unabhängig von der dokumentierten Annäherung an ts_rank. (backend.mjs, SearchText.java, SearchService.java)

## Hinweise

- H1: Der Paralleltest zum Seitenende prüft die richtige Invariante. Bei limit=1, offset=1 muss genau dann eine Trefferzeile vorliegen, wenn total=2. Der Test wiederholt Veröffentlichungen und Rückzüge, erzwingt aber weder beide beobachteten Zustände noch den Wechsel zwischen zwei hypothetischen SQL-Anweisungen. Die gemeinsame SQL-Anweisung belegt die Korrektur direkt. (WikiSearchApiTest.java)
- H2: Der neue Archivpfadtest prüft beide Suchstände mit demselben Wurzeltitel. Er sichert den Weg über die archivierte Elternseite und den Platzhalter ab. Die Auswahl des jeweiligen Stands ergibt sich aus shownRow; der neue Fall allein würde eine Verwechslung der beiden Wurzeltitel nicht erkennen. (WikiService.java, WikiSearchApiTest.java)

## Abgleich mit TASK-105 und DEC-135

| Punkt | Ergebnis |
|---|---|
| R1 Anzeige des Suchstands | Überschrift und Haupttext werden gemeinsam gewählt. Die beiden neuen Browsertests decken unterschiedliche Titel und den Rückzug bei einem Konto mit Entwurfsrecht ab. Der Rückzug für reine Lesekonten bleibt gemäß F1 offen. |
| R2 Archivpfad | Der Server geht über archivierte Eltern bis zur Wurzel weiter. Archivierte Ebenen bleiben Platzhalter ohne Kennung und Titel. Die Nachschlageabfrage ist auf Unternehmen und Projekt begrenzt, und die Menge besuchter Eltern stoppt Zyklen. Der API-Test archiviert zuerst das Kind, dann die Elternseite und prüft den Pfad im veröffentlichten Stand und im Entwurfsstand. |
| R3 Markierungen | Die neuen Tests decken Ⓐlpha im Titel und hinter dem Anfangsausschnitt sowie Markierungen in Anforderungen ab. Die geforderte Übereinstimmung mit dem Index bleibt gemäß F2 offen. |
| R4 Zähler am Seitenende | count(*) über die bereits je Seite ausgewählten Treffer liefert auch bei leerer Ergebnisseite eine Zählzeile. LEFT JOIN LATERAL hängt die Treffer optional an. Innere und äußere Sortierung verwenden Rang, kleingeschriebenen Titel, Originaltitel und Seiten-ID. Der Code liest den Zähler auch bei fehlender Trefferzeile. Die Ticketsuche behält ihre separate Zählung. |
| N1 Testersatz | Die vier Vergleichsfälle sind am Server und am Testersatz vorhanden. Sie prüfen Rang vor Titel, Archivpfad, Kompatibilitätszeichen und die Längen- und Indexgrenzen. Alle vier liefen in der direkten Probe am Testersatz erfolgreich. ts_rank bleibt eine dokumentierte Annäherung. F2 und F3 zeigen Abweichungen außerhalb der vier Fälle. |
| N2 Projektwechsel | Der Browsertest aktiviert beide Filter im Veröffentlichungsprojekt, wechselt ins Leseprojekt, prüft die ausgeblendeten Schalter und die gesendeten Parameter und kontrolliert nach dem Rückwechsel, dass beide Filter zurückgesetzt sind. |
| N3 Vertrag | OpenAPI 0.4.1 und die generierten Typen bilden die zwei festen Formen von PathEntry ab. Der Vertrag und WikiPages.paths beschreiben den Pfad im Suchstand. Der API-Test prüft eine echte 200-Antwort mit Platzhalter sowie die vier dokumentierten 400-Gründe language.invalid, preferred.invalid, drafts.invalid und archived.invalid. |
| N4 Rechte in TASK-102 | Regeln 2 und 5 sowie Kriterien 1 und 3 stimmen mit Server und Backend-README überein. Veröffentlichte Inhalte setzen eine der drei Wikiaktionen im Mitgliedsprojekt voraus, Entwürfe wiki.edit oder wiki.publish, archivierte Treffer den Archivfilter und wiki.publish im jeweiligen Projekt. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Quellcode- und Diffprüfung | Alle 17 geänderten Dateien sowie die relevanten Aufrufpfade und Projektvorgaben am PR-Head geprüft. Der heruntergeladene Diff enthielt keine nachgestellten Leerzeichen in hinzugefügten Zeilen und keine hinzugefügten Konfliktmarkierungen. |
| Gezielte JavaScript-Proben | Die vier vorhandenen Vergleichsfälle direkt aus wiki.test.mjs gegen backend.mjs ausgeführt, mit Ersatz für die im Prüfkontext fehlenden Node-Hilfsfunktionen. Alle vier bestanden. Zusätzliche Proben bestätigten beide Lesewege nach Rückzug, die fehlenden Unicode-Markierungen und das Verhalten bei U+FEFF am Testersatz. Die entsprechenden Java-Pfade wurden statisch geprüft. |
| GitHub Actions am PR-Head | Backend, Frontend einschließlich Browsertests, Repository Checks, Image und Technical Qualification erfolgreich. Der Backend-Workflow verwendet Java 25 und PostgreSQL 18.6. |
| Angeforderte lokale Läufe | Wegen der ausgefallenen Ausführungsumgebung offen: mvn -Djava.version=21 verify gegen PostgreSQL 16, npm run verify, der Browserlauf mit TASKMANAGER_BROWSER=/opt/pw-browsers/chromium sowie die drei Repository-Prüfungen und git diff --check als lokale Kommandos. |

## Abnahmeempfehlung

F1 und F2 vor der Abnahme beheben und F3 beim Abgleich des Testersatzes korrigieren. Für die Nachprüfung sind die zusätzlichen Leser- und Unicode-Fälle sowie die noch offenen lokalen Prüfläufe maßgeblich.
