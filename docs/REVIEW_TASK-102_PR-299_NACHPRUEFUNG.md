# Unabhängige Nachprüfung von TASK-102 / PR #299

**Empfehlung · Nachbessern.** Die Archivberechtigung entspricht Holgers Entscheidung. Offen bleiben die Anzeige des gefundenen Stands, der vollständige Pfad über archivierte Eltern, die Markierung bestimmter normalisierter Wörter und der gemeinsame Datenstand beim Blättern hinter das Seitenende.

**Prüfstand** · 11. November 2026, Sitzung `codex-20261111-search-wiki-02`, [PR #299](https://github.com/1uedev/task-manager/pull/299), Branch `claude/project-thread-hulabb`. Geprüft wurde [6058eb3](https://github.com/1uedev/task-manager/commit/6058eb340a7294697e3247e2d3024b23e9cb4688), direkter Nachfolger von `8f81284e72fff490339aab057a474ce14e6f1021`. Grundlage waren [AGENTS.md](../AGENTS.md), der [Erstbericht](REVIEW_TASK-102_PR-299.md), [TASK-102](../TODO.md#task-102-wiki-in-der-gemeinsamen-suche-a14-teil-1), DEC-132 mit Nachtrag, die Backend-Beschreibung und der Vertrags-, Server- und Frontend-Code dieses Commits. Die Zeilennummern beziehen sich auf diesen Stand.

## Neue Befunde

### R1 · Der veröffentlichte Treffer öffnet den Entwurfstitel

- **Datei und Zeile** · [WikiPage.tsx:768](../frontend/src/pages/WikiPage.tsx#L768), [WikiPage.tsx:851](../frontend/src/pages/WikiPage.tsx#L851), [wiki.test.mjs:799](../frontend/tests/browser/wiki.test.mjs#L799).
- **Auswirkung** · Bei `…/published` bestimmt `publishedFirst` den angezeigten Text. Die Überschrift verwendet bei Entwurfsrecht weiterhin den Entwurfstitel. Eine lokale Browserprobe mit Testersatz zeigte beim Klick auf „Alte Ueberschrift“ den Titel „Neuer Entwurfstitel“ über dem Text „Alte Fassung“. Ein nur im veröffentlichten Titel vorkommender Suchbegriff fehlt dadurch auf der geöffneten Seite. Wenn die Veröffentlichung inzwischen zurückgezogen wurde und die Person den Entwurf lesen darf, zeigt dieselbe Route unmittelbar den Entwurf. Der vorhandene Browsertest verwendet für beide Stände „Guide“.
- **Vorschlag** · Titel und Inhalt aus demselben gewählten Stand anzeigen. Fehlt die Veröffentlichung, den Zustand kenntlich machen und den Entwurf erst über eine ausdrückliche Aktion zeigen. Den Browsertest mit unterschiedlichen Titeln und einer zwischen Suche und Klick zurückgezogenen Veröffentlichung ergänzen.

### R2 · Ein archivierter Elternknoten kürzt den restlichen Pfad ab

- **Datei und Zeile** · [WikiService.java:631](../backend/src/main/java/taskmanager/wiki/internal/WikiService.java#L631), [WikiService.java:649](../backend/src/main/java/taskmanager/wiki/internal/WikiService.java#L649), [WikiRepository.java:124](../backend/src/main/java/taskmanager/wiki/internal/WikiRepository.java#L124), [backend.mjs:2512](../frontend/tests/browser/backend.mjs#L2512).
- **Auswirkung** · Der Fehler ist über die reguläre Archivierung erreichbar. Unter einer aktiven veröffentlichten Wurzel liegen eine Elternseite und ihr Kind. Wer das Kind zuerst archiviert und danach die Elternseite, erhält die Elternbeziehung des archivierten Kindes. Die Archivierung hängt nur aktive Kinder um. Bei einem Archivtreffer für das Kind fehlen die archivierte Elternseite in der Pfadabfrage und damit alle darüber liegenden Ebenen. Das Ergebnis enthält nur „Seite nicht verfügbar“, obwohl die Wurzel lesbar ist. Eine HTTP-Probe am lokalen Server mit PostgreSQL 16.15 und dieselbe Folge am Browser-Testersatz lieferten für beide Suchstände genau einen Platzhalter. Der vorhandene Pfadtest deckt unveröffentlichte aktive Eltern ab.
- **Vorschlag** · Beim Traversieren auch die Elternbeziehung archivierter Knoten lesen, diese Knoten als Platzhalter ohne Kennung und Titel ausgeben und bis zur Wurzel weitergehen. Den Archivierungsablauf im API-Test für veröffentlichte Treffer und Entwurfstreffer abdecken.

### R3 · Bestimmte NFKC-Treffer bleiben ohne Markierung

- **Datei und Zeile** · [SearchSnippet.java:20](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L20), [SearchSnippet.java:70](../backend/src/main/java/taskmanager/search/internal/SearchSnippet.java#L70), [SearchText.java:58](../backend/src/main/java/taskmanager/search/internal/SearchText.java#L58).
- **Auswirkung** · `SearchText.terms` normalisiert den gesamten Text vor der Wortzerlegung. `SearchSnippet` wählt zuerst Abschnitte aus Buchstaben, Zahlen und kombinierenden Zeichen. Kompatibilitätssymbole der Unicode-Kategorie `So` können beim Normalisieren Buchstaben ergeben und gehen bei der Vorauswahl verloren. Eine lokale Java-Probe mit den beiden Klassen des Commits bestätigte `Ⓐlpha → alpha` und `℡ecall → telecall` mit leeren Markierungslisten. Die HTTP-Probe mit `Ⓐlpha` im Titel und nach 40 Wiederholungen von „Vorwort“ im Text lieferte für `alpha` einen Treffer ohne Titelmarkierung und einen Ausschnitt am Textanfang ohne das gesuchte Wort. Der ursprünglich gemeldete zerlegte Akzent wird jetzt markiert.
- **Vorschlag** · Dieselbe Normalisierung und Wortzerlegung wie im Index verwenden und die gefundenen Bereiche auf die UTF-16-Positionen im Originaltext abbilden. Einen Test für ein Kompatibilitätssymbol am Wortanfang und einen Treffer außerhalb des Anfangsausschnitts ergänzen.

### R4 · Bei leerer Ergebnisseite kann der Zähler aus einem späteren Datenstand stammen

- **Datei und Zeile** · [SearchService.java:527](../backend/src/main/java/taskmanager/search/internal/SearchService.java#L527), [SearchService.java:539](../backend/src/main/java/taskmanager/search/internal/SearchService.java#L539), [WikiSearchApiTest.java:649](../backend/src/test/java/taskmanager/search/WikiSearchApiTest.java#L649), [backend/README.md:642](../backend/README.md#L642).
- **Auswirkung** · Bei einer leeren Seite mit positivem Offset folgt auf die Fensterabfrage eine separate Zählung. Unter `READ COMMITTED` kann eine Veröffentlichung dazwischen sichtbar werden. Bei 20 Treffern und `offset=20` kann die erste Anweisung keine Zeile liefern, die zweite nach einer Veröffentlichung aber `total=21`. Die Antwort meldet dann 21 Treffer und eine leere zweite Seite. Eine kontrollierte SQL-Probe gegen die echte Projektion bestätigte den Effekt mit `limit=1` und `offset=1`: keine Zeile bei der Fensterabfrage, danach Veröffentlichung per API, anschließend Zähler 2 in derselben Lesetransaktion. Für nicht leere Seiten sind Titel, Revision, Text und Zähler seit der Korrektur in derselben Anweisung. Der Konkurrenztest variiert die Revision eines durchgehend passenden Treffers, der Test hinter dem Seitenende arbeitet mit unveränderter Treffermenge.
- **Vorschlag** · Zähler und Seitenauswahl auch bei leerer Seite aus einer SQL-Anweisung liefern, etwa mit einer Zählzeile und optionalen Trefferzeilen. Den Fall eines Publikationswechsels am Seitenende gezielt prüfen.

## Hinweise

### N1 · Browser-Testersatz und Server können unterschiedliche Treffer liefern

- **Datei und Zeile** · [backend.mjs:2531](../frontend/tests/browser/backend.mjs#L2531), [backend.mjs:2559](../frontend/tests/browser/backend.mjs#L2559), [backend.mjs:2571](../frontend/tests/browser/backend.mjs#L2571), [SearchService.java:511](../backend/src/main/java/taskmanager/search/internal/SearchService.java#L511), [SearchText.java:29](../backend/src/main/java/taskmanager/search/internal/SearchText.java#L29), [SearchText.java:77](../backend/src/main/java/taskmanager/search/internal/SearchText.java#L77).
- **Auswirkung** · Wortgrenzen, reine Satzzeichen, die Ausschnittwahl und die Grundregeln zu Rechten sind angenähert. Der Testersatz sortiert Treffer weiterhin alphabetisch, zählt für seine Längenprüfung UTF-16-Einheiten vor der NFKC-Normalisierung und durchsucht den gesamten Text. Der Server verwendet Rang vor Titel, zählt Codepoints nach der Normalisierung und begrenzt die indizierten Suchwörter je Text. Die Browserprüfungen mit dem Testersatz können damit bei Treffermenge, Reihenfolge und Seitenwechsel vom realen Server abweichen. Die Suche nach `verteilta verteiltb` ergab in der lokalen HTTP-Probe zuerst „Titel verteilta“, dann „Nur Text“. Dieselben Seiten und dieselbe Anfrage am Browser-Testersatz ergaben die umgekehrte Reihenfolge.
- **Vorschlag** · Dieselben Antwortfälle am Server und am Testersatz prüfen, insbesondere verteilte Wörter mit anderer Titelreihenfolge, ein Suchwort hinter der Indexgrenze und die Längengrenzen bei NFKC und Nicht-BMP-Zeichen.

### N2 · Der Browsertest für Projektfilter wechselt das Projekt nicht

- **Datei und Zeile** · [wiki.test.mjs:821](../frontend/tests/browser/wiki.test.mjs#L821), [WikiSearch.tsx:62](../frontend/src/pages/WikiSearch.tsx#L62), [WikiSearch.tsx:100](../frontend/src/pages/WikiSearch.tsx#L100).
- **Auswirkung** · Der Test bestätigt den fehlenden Archivfilter ohne `wiki.publish` sowie Wiederholen und Fokus auf Seite 2. Das Ausblenden und Zurücksetzen zuvor aktivierter Filter beim Wechsel in ein Projekt mit geringeren Rechten bleibt im Browser ungeprüft. Die Implementierung richtet die Filter am gewählten Projekt aus.
- **Vorschlag** · Ein Projekt mit Veröffentlichungsrecht und ein reines Leseprojekt vorbereiten. Beide Filter zunächst aktivieren, zum Leseprojekt wechseln und nach der Suche die sichtbaren Filter sowie die gesendeten Parameter prüfen.

### N3 · Vertrag und Testabdeckung für Pfad und Fehlergründe präzisieren

- **Datei und Zeile** · [search.openapi.yaml:229](../contracts/search.openapi.yaml#L229), [search.openapi.yaml:310](../contracts/search.openapi.yaml#L310), [search.openapi.yaml:339](../contracts/search.openapi.yaml#L339), [WikiPages.java:37](../backend/src/main/java/taskmanager/wiki/WikiPages.java#L37), [SearchContractTest.java:66](../backend/src/test/java/taskmanager/search/SearchContractTest.java#L66), [WikiSearchApiTest.java:212](../backend/src/test/java/taskmanager/search/WikiSearchApiTest.java#L212).
- **Auswirkung** · Vertrag 0.3.1 nennt die vier Wiki-Fehlergründe. Die Beschreibung von `WikiHit.path` verweist noch auf die Seitenansicht, deren Brotkrumen anderen Regeln folgen. Der letzte Satz im Kommentar von `WikiPages.paths` spricht noch vom Weglassen unsichtbarer Seiten. Das Schema erlaubt unabhängig von `available` auch eine Kennung und einen Titel. Der Routentest prüft die Zuordnung von Route und Vertrag. Die vorhandene Hilfsmethode `assertInvalid` bleibt ungenutzt, sodass sie keinen der dokumentierten Wiki-Fehlergründe absichert.
- **Vorschlag** · Den Pfad als Pfad im Suchstand beschreiben, die Kombinationen für `available` und die beiden Werte festlegen und eine echte 200-Antwort mit Platzhalter sowie die vier 400-Fehlergründe prüfen.

### N4 · TODO und API formulieren die Rechte für unveröffentlichte und veröffentlichte Inhalte unterschiedlich

- **Datei und Zeile** · [TODO.md:67](../TODO.md#L67), [TODO.md:70](../TODO.md#L70), [TODO.md:78](../TODO.md#L78), [SearchService.java:464](../backend/src/main/java/taskmanager/search/internal/SearchService.java#L464), [backend/README.md:642](../backend/README.md#L642).
- **Auswirkung** · Holgers Archiventscheidung ist überall umgesetzt. Regel 5 und Kriterium 3 nennen daneben `wiki.read` so, als wäre es zwingend. Im Server und in der Backend-Beschreibung genügt wie im Wiki eine der drei Wiki-Aktionen für veröffentlichte Inhalte. Für Entwürfe reicht auch `wiki.publish`, wie in DEC-132 beschrieben. Das lässt bei der Abnahme unterschiedliche Erwartungen an Mitglieder mit ausschließlich Bearbeitungs- oder Veröffentlichungsrecht zu.
- **Vorschlag** · Die Alternativen in Regel 2, Regel 5 und Kriterium 3 ausdrücklich benennen und das Archivrecht separat festhalten.

## Abgleich mit dem Erstbericht

| Punkt | Ergebnis |
| --- | --- |
| F1 · Archiv | TODO-Regeln 4 und 5, Kriterium 3, DEC-132, OpenAPI 0.3.1, Server und Oberfläche verlangen `wiki.publish` für archivierte Treffer. Der Server bindet das Recht an das jeweilige Mitgliedsprojekt und schließt archivierte Seiten ohne den Filter aus. `wiki.edit` allein erhält ohne Veröffentlichungsrecht in allen Mitgliedsprojekten 403 bei gesetztem Archivfilter. Die Oberfläche richtet den Schalter am gewählten Projekt aus. |
| F2 · Pfad | `WikiPages.paths` erhält den Suchstand. `WikiService.searchPath` liefert veröffentlichte Titel bei veröffentlichter Suche und Entwurfstitel bei Entwurfssuche. Aktive Eltern ohne sichtbare Fassung bleiben als `available: false` mit `id: null` und `title: null` erhalten. Die Oberfläche lokalisiert den Platzhalter. Archivierte Eltern siehe R2. |
| F3 · Markierungen | Das Wortmuster enthält kombinierende Zeichen. Die Positionen werden vor dem Ausschneiden bestimmt, am Ausschnitt begrenzt und in dessen UTF-16-Koordinaten umgerechnet. Kompatibilitätssymbole bleiben offen (R3). |
| F4 · Zähler und Revision | Eine Fensterabfrage liefert bei nicht leeren Seiten Zähler, Zeile, Revision und Text. Der nachgelagerte Textabruf entfällt. `aHitNeverMixesRevisionsWhileThePageIsPublished` prüft Zähler, Titel und Text während 24 weiterer Veröffentlichungen. Der Sonderfall hinter dem Seitenende bleibt offen (R4). |
| F5 · Trefferlink | Die Route `#/wiki/{Projekt}/{Seite}/{de\|en}[/published]` übergibt Sprache und Stand. Routentest und Browserprüfung decken den Link, den veröffentlichten Text und „Entwurf zeigen“ ab. Die Anzeige von Titel und Stand bleibt offen (R1). |
| H1 · Textgewinnung | Ausgelassene interne Linktexte erhalten eine Wortgrenze, mit API-Test. Die Grenze von 200.000 UTF-8-Bytes für normalisierte Suchwörter, der Schnitt nach einem ganzen Wort und der vollständige Klartext für Ausschnitte sind beschrieben. |
| H2 · Rang und Messung | Die Suche verlangt weiter jedes Wort. Die Rangformel bewertet passende Wörter je Feld und gewichtet den Titel doppelt. Der API-Test prüft einen über Titel und Text verteilten Treffer. Die Leistungsmessung bleibt bei TASK-104. |
| H3 · Testersatz | Rechte, Wortzerlegung, Satzzeichen und Ausschnitte wurden nachgezogen. Verbleibende Abweichungen siehe N1. |
| H4 · Oberfläche | Filter je gewähltem Projekt, Wiederholen mit dem zuletzt angefragten Offset, dauerhaft eingehängte Live-Region, Fokus auf dem Ergebnistitel nach dem Blättern und `lang` am Treffertitel sind umgesetzt. Testlücke beim Projektwechsel siehe N2. |
| H5 · Vertrag | Version 0.3.1 und die vier Wiki-Fehlergründe sind vorhanden. Siehe N3 zur Pfadbeschreibung und Vertragsprüfung. |
| H6 · Projektion | Der API-Test prüft Zurückziehen, Übernehmen, Archivieren und Wiederherstellen über die Suche. Die Backend-Beschreibung begründet, weshalb Verschieben und Bildupload die Projektion unverändert lassen. Der bestehende Neuaufbautest vergleicht einen Treffer vor und nach dem Neuaufbau. Ein gleichzeitiger Wiki-Schreibvorgang beim Neuaufbau und der Start mit Wiki-Bestandsdaten aus V0061 bleiben ohne eigenen Test. |
| Umfang | Drei Commits und 39 Dateien gegen die PR-Basis `890a7c9`, davon 22 Dateien im Korrekturcommit. Alle geprüften Änderungen gehören zur Wiki-Suche, ihrer Integration, den zugehörigen Tests oder zur Dokumentation von TASK-102. |

## Ausgeführte Prüfungen

| Prüfung | Lokales Ergebnis auf `6058eb3` |
| --- | --- |
| `python3 scripts/validate_repository.py` | Erfolgreich mit dem vorhandenen Python 3.12.14. |
| `python3 scripts/task_board.py --check` | Erfolgreich. |
| `python3 -m unittest discover -s tests -v` | 42 Tests, davon 41 bestanden und 1 übersprungen, weil der Zeitzonenwechsel im Test Unix voraussetzt. |
| `./mvnw verify` mit PostgreSQL 16 | Erfolgreich mit dem Windows-Wrapper `mvnw.cmd`, Java 25 und PostgreSQL 16.15. Alle 608 Tests bestanden, einschließlich aller 12 Tests in `WikiSearchApiTest`. Das JAR wurde gebaut. Der erste Anlauf scheiterte beim Start der Anwendung wegen des fehlenden Testdatenbank-Passwortparameters. Der vollständige Wiederholungslauf mit gesetztem Parameter war erfolgreich. |
| `npm ci` | Erfolgreich, 111 Pakete installiert. |
| `npm run verify` | Erfolgreich, Typgenerierung, Typprüfung, 101 Unit-Tests und Produktions-Build. |
| `npm run test:browser` | Erfolgreich, 267 Tests in 39 Suiten mit dem Browser-Testersatz. |
| Gezielte Nachprüfung | Lokale Browserprobe mit unterschiedlichen Entwurfs- und Veröffentlichungstiteln (R1), HTTP-Proben mit echtem Backend und PostgreSQL 16.15 für Archivpfad, Unicode und Rangfolge (R2, R3, N1), zusätzlich Java-Probe für NFKC und Vergleich mit dem Browser-Testersatz. Eine kontrollierte SQL-Probe bestätigte den Datenstandswechsel zwischen Seitenauswahl und Zählung (R4). |
| `git diff --check` | Erfolgreich gegen den Erstprüfstand und die PR-Basis. |

Für denselben Commit meldet GitHub Actions erfolgreiche Läufe für Repository Checks, Backend, Frontend, Image und Technical Qualification. Der Backend-Workflow verwendet die festgelegte PostgreSQL-Version 18.6. [Repository Checks](https://github.com/1uedev/task-manager/actions/runs/43032143537) · [Backend](https://github.com/1uedev/task-manager/actions/runs/43032143522) · [Frontend](https://github.com/1uedev/task-manager/actions/runs/43032143657) · [Image](https://github.com/1uedev/task-manager/actions/runs/43032143462) · [Technical Qualification](https://github.com/1uedev/task-manager/actions/runs/43032143576)
