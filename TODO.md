# Zentrale offene Aufgaben

Stand 18. Oktober 2026 · Planung, Repository-Infrastruktur und Frontend-Grundfläche (TASK-023)

Diese Datei enthält den gemeinsamen Arbeitsstand aller beauftragten KI-Sitzungen. Status und Zuständigkeit werden gemäß [AGENTS.md](AGENTS.md) koordiniert. Eine Aufgabe kann mehrere Anforderungen konkretisieren. Ein offener Entwurf gilt nicht als fachlich abgenommen.

Repository: [1uedev/task-manager](https://github.com/1uedev/task-manager). Maßgeblich ist diese Datei auf main. TASK-013 richtet die Arbeitsumgebung ein. Die übergreifende Koordination liegt bei Holger oder einer ausdrücklich von ihm benannten Sitzung. Aufgaben werden über einen reservierenden Pull Request vergeben; siehe docs/AI_WORKFLOW.md. GitHub Issues sind Diskussions- und Einstiegspunkte, keine zweite Aufgabenquelle.

Am 28. Oktober 2026 hat Holger alle bis dahin zur Prüfung stehenden Aufgaben abgenommen, darunter TASK-001 bis TASK-021; sie stehen in [DONE.md](DONE.md). Neue Aufgaben brauchen weiterhin einen eigenen Auftrag und eine Reservierung.

<!-- TASK-BOARD:START -->
## Aufgabenübersicht

Automatisch aus den Aufgabeneinträgen weiter unten erzeugt. Maßgeblich ist TODO.md auf main; eine lokale Kopie kann veraltet sein. Aktualisieren: `python3 scripts/task_board.py --write`.

| Kategorie | Anzahl | Bedeutung |
|---|---:|---|
| Frei und bearbeitbar (OFFEN) | 0 | Zur Reservierung verfügbar; Auftrag und Abhängigkeiten im Detail prüfen. |
| Durch eine KI reserviert (IN ARBEIT) | 1 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 0 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 7 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-109](#task-109-befunde-der-codex-prüfung-von-task-108-beheben-a14) | Befunde der Codex Prüfung von TASK-108 beheben (A14) | claude-20261112-search-06 | 2026-11-12 |

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-102](#task-102-wiki-in-der-gemeinsamen-suche-a14-teil-1) | Wiki in der gemeinsamen Suche (A14 Teil 1) | claude-20261111-search-01 | 2026-11-11 |
| [TASK-103](#task-103-anforderungen-in-der-gemeinsamen-suche-a14-teil-2) | Anforderungen in der gemeinsamen Suche (A14 Teil 2) | claude-20261111-search-01 | 2026-11-11 |
| [TASK-104](#task-104-gemeinsame-suchübersicht-schnellsuche-und-messung-a14-teil-3) | Gemeinsame Suchübersicht, Schnellsuche und Messung (A14 Teil 3) | claude-20261111-search-01 | 2026-11-11 |
| [TASK-105](#task-105-befunde-der-codex-nachprüfung-von-task-102-beheben-a14) | Befunde der Codex Nachprüfung von TASK-102 beheben (A14) | claude-20261111-search-02 | 2026-11-11 |
| [TASK-106](#task-106-befunde-der-codex-prüfung-von-task-105-beheben-a14) | Befunde der Codex Prüfung von TASK-105 beheben (A14) | claude-20261112-search-03 | 2026-11-12 |
| [TASK-107](#task-107-befunde-der-codex-prüfung-von-task-106-beheben-a14) | Befunde der Codex Prüfung von TASK-106 beheben (A14) | claude-20261112-search-04 | 2026-11-12 |
| [TASK-108](#task-108-befunde-der-codex-prüfung-von-task-107-beheben-a14) | Befunde der Codex Prüfung von TASK-107 beheben (A14) | claude-20261112-search-05 | 2026-11-12 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-102 Wiki in der gemeinsamen Suche (A14 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261111-search-01 · **Aktualisiert:** 2026-11-11

**Bezug:** REQ-SRCH-001, REQ-DOC-004; DEC-047, DEC-050; docs/TAGS_SEARCH.md (Suchumfang, Prüffälle T09, T11, T13, T16). **Abhängigkeiten:** TASK-029, TASK-093 und TASK-094, geliefert. **Arbeitsstand:** Holger hat am 11. November 2026 im Projektgespräch mit „ja“ den Beginn von Paket A14 beauftragt und am selben Tag die 23 Vorschläge mit „passt“ übernommen (Regeln unten und bei TASK-103 und TASK-104). Geplanter Branch claude/project-thread-hulabb, Entscheidung DEC-132, Migrationen ab V0062.

**Umfang:** Wikiseiten in der Suchprojektion, synchron in der Transaktion jeder Änderung, Veröffentlichung und Archivierung, der Endpunkt `/search/wiki` mit Rechten, Filtern, Ausschnitt und Seiten, der Neuaufbau mit `rebuild-search`, der Bereich „Wiki“ auf der Suchseite, mit Migration, Vertrag und Tests. Nicht Teil: Anforderungen (TASK-103), Übersicht „Alle“, Schnellsuche und Messung (TASK-104), Dateiinhalte und Dateinamen, Portal, Wortstämme, Synonyme, Tippfehlertoleranz, Phrasensuche, gespeicherte Suchen, KI, zusätzlicher Suchdienst.

**Fachliche Regeln** (von Holger am 11. November 2026 bestätigt):
1. Gefunden werden Titel und Text jeder Sprachfassung. Der Server gewinnt den Text aus dem geprüften Dokument: Absätze, Überschriften, Listen, Tabellen, Zitate, Codeblöcke und sichtbare Linktexte; keine Bilder, Linkadressen oder internen Kennungen.
2. Standardmäßig wird nur die veröffentlichte Revision jeder Sprachfassung durchsucht, auch für Personen mit `wiki.edit`. Mit `wiki.edit` oder `wiki.publish` lässt sich „Entwürfe durchsuchen“ wählen; dann wird stattdessen der aktuelle Entwurf durchsucht und der Treffer als Entwurf gekennzeichnet. Nie gemischt.
3. Beide Sprachfassungen werden durchsucht, ein Treffer je Seite: Treffen beide, zeigt der Treffer die Fassung in der Oberflächensprache, sonst die getroffene, jeweils mit Sprachkennzeichen. Filter „Sprache“ mit Deutsch, Englisch oder beide.
4. Archivierte Seiten werden nicht gefunden; mit „Archivierte einbeziehen“ (nur mit `wiki.publish`, wie das Wiki selbst archivierte Seiten zeigt; von Holger am 11. November 2026 so entschieden, statt zuvor `wiki.edit`) erscheinen sie gekennzeichnet.
5. Suchen verlangt Mitgliedschaft und eine der drei Wikiaktionen `wiki.read`, `wiki.edit` oder `wiki.publish` (wie das Wiki selbst veröffentlichte Inhalte zeigt), Entwürfe `wiki.edit` oder `wiki.publish`. Das Archiv verlangt gesondert `wiki.publish` im jeweiligen Projekt. Ohne Recht weder Treffer noch Zähler noch Vorschau. Portalkonten erhalten keine Wikisuche (403).
6. Ein Treffer zeigt Titel, Projekt, Pfad der Elternseiten (nicht lesbare als „Seite nicht verfügbar“), Sprache, Stand, Zeitpunkt der Veröffentlichung und einen Ausschnitt als Klartext mit den Positionen der gefundenen Wörter, nie als HTML.
7. Textregeln wie TASK-029: keine Wortstämme, jedes Wort als Präfix, alle Wörter verlangt, keine Suchsyntax, dieselben Grenzen und 503 `search.busy`.
8. Die Projektion wird in derselben Transaktion geschrieben; Rechte gelten bei jeder Suche neu. `rebuild-search` baut auch das Wiki neu auf, die Migration löscht den Aufbauvermerk.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Ein Wort, das nur im Entwurf einer veröffentlichten Seite steht, findet ein Leser nicht; mit „Entwürfe durchsuchen“ und `wiki.edit` oder `wiki.publish` erscheint die Seite als Entwurf (T11).
2. Ein Wort der englischen Fassung findet die Seite mit Sprachkennzeichen; der Sprachfilter grenzt ein.
3. Ohne Mitgliedschaft oder ohne jede der drei Wikiaktionen keine Treffer und keine Zähler; Entwürfe nur mit `wiki.edit` oder `wiki.publish`; archivierte Seiten gesondert nur mit dem Filter und `wiki.publish`.
4. Nach Veröffentlichen ist der neue Stand in der folgenden Suche sichtbar (T13); `rebuild-search` liefert dieselben Treffer.
5. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für den Bereich Wiki.

**Übergabe:** Ergebnis in PR #299 (Branch claude/project-thread-hulabb), Migration V0062, Vertrag `contracts/search.openapi.yaml` 0.3.1, Beschreibung in backend/README.md (Abschnitt „Wiki in der Suche“), DEC-132 mit Nachtrag. Die Codex Prüfung (docs/REVIEW_TASK-102_PR-299.md, nachbessern) ist in #299 selbst behoben: F1 von Holger entschieden (Archiv nur mit `wiki.publish`, Regeln 4 und 5 und Kriterium 3 angepasst), F2 Pfad mit jeder Ebene, „Seite nicht verfügbar“ und dem gesuchten Stand, F3 Markierung nach der Regel des Index, F4 Zähler, Treffer und Ausschnitt aus einer Anweisung, F5 Trefferlink mit Sprachfassung und Stand, dazu H1 bis H6 soweit in TASK-102 (H2 Messung in TASK-104). Nachweise: `WikiSearchApiTest` (12, darunter Pfad, jede Änderung, gleichzeitiges Veröffentlichen, Rangfolge, Seitenende, zerlegte Akzente, Wortgrenze), `SearchSnippetTest` (6), `WikiPlainTextTest`, `SearchContractTest`, Routentest, Browsertests in `wiki.test.mjs`, vollständige Läufe.

**Nächster Schritt:** Nachprüfung durch Codex oder eine getrennte Claude Sitzung, danach Abnahme durch Holger; TASK-103 beginnt, sobald das Ergebnis auf main ist.

## TASK-103 Anforderungen in der gemeinsamen Suche (A14 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261111-search-01 · **Aktualisiert:** 2026-11-11

**Bezug:** REQ-SRCH-001, REQ-REQ-001, REQ-ACL-004; DEC-050; docs/TAGS_SEARCH.md (Suchumfang, Prüffälle T09, T13, T20). **Abhängigkeiten:** TASK-102 auf main; TASK-096 bis TASK-098, geliefert. **Arbeitsstand:** Mit TASK-102 von Holger am 11. November 2026 beauftragt und mit den Vorschlägen bestätigt; begonnen, nachdem das Ergebnis von TASK-102 mit PR #299 auf main war. Branch claude/project-thread-hulabb, Entscheidung DEC-133, Migration V0063. Ergebnis im Ergebnis PR: Projektion `search_requirement` mit aktuellem und zuletzt freigegebenem Stand, Endpunkt `GET /api/v1/search/requirements`, Bereich „Anforderungen“ der Suchseite, Auslegungen der Sitzung in DEC-133.

**Umfang:** Anforderungen in der Suchprojektion mit Feldrechten, der Endpunkt `/search/requirements` mit Filtern, Ausschnitt und Seiten, der Neuaufbau, der Bereich „Anforderungen“ auf der Suchseite, mit Migration, Vertrag und Tests. Nicht Teil: ältere freigegebene Revisionen, Tags an Anforderungen, Verknüpfungen als Filter.

**Fachliche Regeln** (von Holger am 11. November 2026 bestätigt):
1. Gefunden werden die Kennung `ANF-n` (mit Projektschlüssel, etwa `HELP ANF-17`, oder `ANF-17` mit gewähltem Projekt), Titel, Beschreibung, Text der Abnahmekriterien und eigene Felder nach der Regel der Tickets (Textfelder im Volltext, Auswahl, Zahl, Datum und Person als Filter).
2. Standardmäßig wird die aktuelle Revision durchsucht; „Stand: zuletzt freigegeben“ sucht stattdessen in der jeweils letzten freigegebenen Revision. Ältere freigegebene Revisionen werden nicht durchsucht. Keine Mischung von Freigabe und neuem Entwurf.
3. Suchen verlangt Mitgliedschaft und `requirement.read`. Wörter treffen nur lesbare Felder, ein Filter auf ein nicht lesbares Feld trifft im Projekt nichts; Rang, Anzahl, Seite und Ausschnitt entstehen nur aus lesbaren Feldern. Die Kennung findet, wer die Anforderung lesen darf.
4. Filter: Projekt, Status, Priorität, verantwortliche Person und Freigabestand („mindestens einmal freigegeben“). Verworfene Anforderungen sind ausgeblendet und über den Statusfilter erreichbar.
5. Ein Treffer zeigt Kennung, Titel, Projekt, Status, Revision mit Freigabestand, Priorität und einen Ausschnitt wie im Wiki, bei einem Treffer im Kriterium mit dessen Kennung.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Ein Wort, das nur in Revision 2 im Entwurf steht, findet die Anforderung in der Standardsuche, nicht aber mit „zuletzt freigegeben“.
2. Ein Wort in einem Feld ohne Leserecht erzeugt weder Treffer noch Rang noch Ausschnitt (T09).
3. `HELP ANF-1` findet die Anforderung für Leser direkt, für andere nichts.
4. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für den Bereich Anforderungen.

**Nächster Schritt:** Prüfung durch Codex oder eine getrennte Claude Sitzung, danach Abnahme durch Holger; TASK-104 beginnt, sobald das Ergebnis auf main ist.

## TASK-105 Befunde der Codex Nachprüfung von TASK-102 beheben (A14)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261111-search-02 · **Aktualisiert:** 2026-11-11

**Bezug:** REQ-SRCH-001, REQ-DOC-004, REQ-ACL-004; DEC-132, DEC-133; docs/REVIEW_TASK-102_PR-299_NACHPRUEFUNG.md. **Abhängigkeiten:** TASK-102 (PR #299) und TASK-103 (PR #300) geliefert. **Arbeitsstand:** Holger hat am 11. November 2026 den Bericht der Codex Nachprüfung von TASK-102 (Empfehlung nachbessern) in den Thread gegeben und am selben Tag auf der Entscheidungskarte „Alles beheben als TASK-105“ gewählt. Branch claude/project-thread-hulabb, Entscheidung DEC-135, keine Migration geplant.

**Umfang:** Befunde R1 bis R4 und Hinweise N1 bis N4 aus docs/REVIEW_TASK-102_PR-299_NACHPRUEFUNG.md:
1. R1: Ein Treffer im veröffentlichten Stand zeigt Titel und Inhalt der veröffentlichten Revision; ist sie inzwischen zurückgezogen, wird das kenntlich gemacht und der Entwurf erst über eine ausdrückliche Aktion gezeigt.
2. R2: Der Pfad eines Treffers geht über archivierte Elternseiten bis zur Wurzel weiter; archivierte Ebenen erscheinen als „Seite nicht verfügbar“.
3. R3: Markierungen in Titel und Ausschnitt folgen derselben Normalisierung und Wortzerlegung wie der Index, auch für Kompatibilitätszeichen am Wortanfang; gilt für Wiki und Anforderungen.
4. R4: Zähler und Seite der Wikisuche kommen auch hinter der letzten Seite aus einer Anweisung (für Anforderungen schon mit PR #300 behoben).
5. N1: Der Testersatz folgt Rangfolge, Längengrenzen und Indexgrenze des Servers.
6. N2: Browsertest für den Wechsel in ein Projekt mit geringeren Rechten.
7. N3: Vertrag und Kommentare beschreiben den Pfad im Suchstand mit festen Kombinationen von `available`, `id` und `title`; Tests der vier 400 Gründe und einer Antwort mit Platzhalter.
8. N4: TODO nennt für veröffentlichte Inhalte eine der drei Wikiaktionen, für Entwürfe `wiki.edit` oder `wiki.publish`, das Archivrecht getrennt.

Nicht Teil: neue Regeln, TASK-104.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. R1 mit Browsertest: unterschiedliche Titel von Entwurf und Veröffentlichung, dazu eine zwischen Suche und Klick zurückgezogene Veröffentlichung.
2. R2 mit API Test: Kind archiviert, dann Elternseite archiviert, Pfad bis zur Wurzel in beiden Ständen.
3. R3 mit Test für `Ⓐlpha` im Titel und einem Treffer außerhalb des Anfangsausschnitts.
4. R4 mit Test eines Publikationswechsels am Seitenende; N1 bis N3 mit Tests, bestehende Tests bleiben grün.

**Übergabe:** Ergebnis in PR #302 (Branch claude/project-thread-hulabb), ohne Migration, Vertrag `contracts/search.openapi.yaml` 0.4.1. R1: Titel und Text einer Wikiseite kommen aus demselben Stand; eine seit der Suche zurückgezogene Veröffentlichung zeigt „Nicht mehr veröffentlicht“ und den Entwurf erst nach „Entwurf zeigen“ (zwei neue Browsertests, der bestehende mit unterschiedlichen Titeln). R2: `WikiService.searchPath` liest die Elternbeziehung archivierter Seiten und geht bis zur Wurzel (`anArchivedParentKeepsThePathUpToTheRoot`, Testersatz gleich). R3: `SearchSnippet.marks` normalisiert Zeichen für Zeichen samt kombinierenden Zeichen und bildet die Wörter der Normalform auf die Stellen im Originaltext ab (Unit Tests mit Ⓐ, ℡, ½, İ und einer Prüfung, dass jedes Indexwort markiert wird; API Tests für Wiki und Anforderungen). R4: Zählzeile und optionale Trefferzeilen in einer Anweisung (`pastTheLastPageTheTotalComesFromTheSameSnapshot` mit wiederholtem Veröffentlichen und Zurückziehen). N1: Testersatz mit Rang vor Titel, Längen in Codepoints nach NFKC und Indexgrenze, dieselben vier Fälle direkt am Testersatz und am Server. N2: Browsertest mit Wechsel in ein reines Leseprojekt. N3: `PathEntry` mit zwei festen Formen, Pfadbeschreibung, Kommentar von `WikiPages.paths`, Test der vier 400 Gründe und der Formen einer echten Antwort. N4: Regeln 2 und 5 und Kriterien 1 und 3 von TASK-102 nennen die Alternativen, das Archivrecht gesondert. Beschreibung in backend/README.md, Abschnitt „Wiki in der Suche“.

**Nächster Schritt:** Prüfung durch Codex (Auftrag liegt im Projektordner a14), danach Holgers Abnahme; TASK-104 folgt.

## TASK-104 Gemeinsame Suchübersicht, Schnellsuche und Messung (A14 Teil 3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261111-search-01 · **Aktualisiert:** 2026-11-11

**Bezug:** REQ-SRCH-001, REQ-PERF-003; DEC-047, DEC-050; OPEN-014; ACC-009; docs/TAGS_SEARCH.md (Messziele, Prüffälle T15, T20), docs/OPERATIONS.md, docs/DEMO.md. **Abhängigkeiten:** TASK-103 auf main; TASK-031. **Arbeitsstand:** Mit TASK-102 von Holger am 11. November 2026 beauftragt und mit den Vorschlägen bestätigt; nach TASK-103 (#300) und TASK-105 (#302) auf main umgesetzt. Geplanter Branch claude/project-thread-hulabb, Entscheidung DEC-134.

**Umfang:** Suchseite mit den Reitern „Alle“, „Tickets“, „Projekte“, „Wiki“ und „Anforderungen“, der Endpunkt `/search/all`, die Schnellsuche, der erweiterte Referenzbestand, die Messung mit neuen Messklassen und einer Browsermessung, die Festlegung der Suchziele, mit Vertrag, Tests und Messbericht. Nicht Teil: zusätzlicher Suchdienst, Nachweis auf der Referenzhardware (A18).

**Fachliche Regeln** (von Holger am 11. November 2026 bestätigt):
1. „Alle“ zeigt je Bereich die ersten fünf Treffer mit Anzahl und „Alle anzeigen“; die Reiter zeigen Filter und Seiten ihres Bereichs. Keine gemeinsame Rangliste über die Bereiche; Bereiche ohne Recht erscheinen nicht.
2. Ein Suchfeld oben in der Anwendung, erreichbar mit „/“. Enter öffnet „Alle“. Eine exakt passende Kennung (`HELP-12`, `HELP ANF-17`, Projektschlüssel) öffnet das lesbare Objekt direkt, sonst normale Suche ohne Hinweis auf Existenz.
3. Je Bereich ein eigener Endpunkt, dazu `/search/all` mit fünf Treffern und Anzahl je Bereich in einer Anfrage; Grenzen wie TASK-029.
4. Der Referenzbestand erhält zusätzlich 2.000 Wikiseiten (etwa ein Drittel mit englischer Fassung, ein Teil mit offenem Entwurf, einige archiviert) und 10.000 Anforderungen mit je ein bis acht Kriterien, ein Teil freigegeben und mit neuer Revision, über die öffentliche API.
5. Neue Messklassen: Wiki ein Wort, Wiki Entwürfe, Anforderung ein Wort, Anforderung Kennung, Anforderung mit Filtern, Übersicht „Alle“, Schnellsuche mit Kennung; bestehende Klassen bleiben, Last wie in TASK-031 mit Wikianteil.
6. Suchziele für V1 (Teil von OPEN-014), serverseitig p95 unter der Referenzlast: Kennung und Vorschläge höchstens 200 ms, reguläre Suche erste Seite höchstens 500 ms, komplexe Suche und Übersicht „Alle“ höchstens 1 s. Messrechner und Grenzen werden dokumentiert; der Nachweis auf 4 vCPU/16 GB bleibt Teil von A18.
7. Browsermessung vom Auslösen bis zur sichtbaren ersten Seite, 200 Wiederholungen je Klasse im lokalen Netz, Ziel p95 höchstens 1 s.
8. Eine Zielverfehlung wird offen dokumentiert und in A14 mit Indizes und Abfrageplänen nachgebessert; ein zusätzlicher Suchdienst nur mit eigener Entscheidung.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Vier Begriffe in je einem Ticket, Projekt, veröffentlichten Wiki und einer aktuellen Anforderung erscheinen in „Alle“ (T20); Bereiche ohne Recht fehlen.
2. Die Schnellsuche öffnet `HELP-1` und `HELP ANF-1` direkt, eine nicht lesbare Kennung führt zur leeren Suche.
3. Messbericht in docs/DEMO.md und docs/measurements mit allen Klassen gegen die Ziele, ohne Mittelwerte als Ersatz (T15).
4. Vertrag gegen Routen, Backend Tests, Browser Tests für Übersicht und Schnellsuche.

**Übergabe:** Ergebnis im PR zu Branch claude/project-thread-hulabb, ohne Migration, Vertrag `contracts/search.openapi.yaml` 0.5.0, Beschreibung in backend/README.md (Abschnitt „Übersicht „Alle“ und Schnellsuche“), DEC-134 mit den Auslegungen (1) bis (6). Nachweise: Kriterium 1 und 2 in `SearchOverviewApiTest` (7 Fälle: vier Bereiche mit einem Wort und ohne Entwurf, Bereiche ohne Recht, fünf Treffer mit Anzahl, Direkttreffer für Ticket, Anforderung und Projekt, nicht lesbare Kennung wie unbekannte, Projektschlüssel vorn, 400 und 401) und `search-overview.test.mjs` (6 Fälle: „Alle“ mit vier Bereichen, fehlende Bereiche, „Alle anzeigen“, „/“, Direktöffnen, nicht lesbare Kennung führt zu „Alle“). Kriterium 3 in docs/DEMO.md (Abschnitt „Messung mit Wiki, Anforderungen und Übersicht“) und docs/measurements/task-104: Servermessung 5.415 Aktionen ohne Fehler, alle 22 Klassen im Ziel nach Regel 6, „Alle“ p95 269 ms; Browsermessung je 200 Werte, alle p95 unter 1 s, „Alle“ 320 ms; jeweils p50, p95, p99 statt Mittelwerten. Abweichung: Messung mit dem Jar (Java 21, PostgreSQL 16) auf der Maschine statt im Image, weil Docker Hub drosselte. Kriterium 4: `SearchContractTest` mit fünf Routen, voller Backendlauf 629 Tests, Frontend `npm run verify` und 282 Browsertests grün.

**Nächster Schritt:** Prüfung durch Codex (Auftrag im Projektordner a14), danach Holgers Abnahme von TASK-102 bis TASK-105.

## TASK-106 Befunde der Codex Prüfung von TASK-105 beheben (A14)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261112-search-03 · **Aktualisiert:** 2026-11-12

**Bezug:** REQ-SRCH-001, REQ-DOC-004, REQ-ACL-004; DEC-132, DEC-135; docs/REVIEW_TASK-105_PR-302.md. **Abhängigkeiten:** TASK-105 (PR #302) geliefert. **Arbeitsstand:** Holger hat am 12. November 2026 den Bericht der Codex Prüfung von TASK-105 in den Thread gegeben und auf der Entscheidungskarte „Alles beheben als TASK-106“ gewählt. Branch claude/project-thread-hulabb, Entscheidung DEC-136, keine Migration geplant.

**Umfang:** Befunde F1 bis F3 und Hinweis H2 aus docs/REVIEW_TASK-105_PR-302.md:
1. F1: Wer eine veröffentlichte Wikiseite aus der Suche öffnet, deren gefundene Fassung inzwischen zurückgezogen ist, sieht auch mit reinem Leserecht einen Hinweis statt eines Fehlers oder einer stillen Ersatzfassung; eine andere veröffentlichte Sprachfassung öffnet sich nur über eine ausdrückliche Aktion.
2. F2: Markierungen in Titel und Ausschnitt folgen der Normalisierung des Index auch für Zeichenfolgen, die NFKC zusammensetzt (Hangul Kompatibilitätsjamo), und für die Kleinschreibung im Wortzusammenhang (Schluss Sigma); gilt für Wiki und Anforderungen.
3. F3: Der Testersatz behandelt einen Suchtext aus U+FEFF wie der Server.
4. H2: Der Test des Archivpfads nutzt je Stufe einen eigenen Titel der Wurzel.

Nicht Teil: neue Regeln, H1 des Berichts.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 mit Browsertests für ein reines Lesekonto: nur die gefundene Fassung zurückgezogen, und zusätzlich eine andere veröffentlichte Sprachfassung vorhanden.
2. F2 mit Unit Tests (jedes Indexwort wird markiert, auch für ㄱㅏ und İ ΟΣ) und einem API Test bei Anforderungen.
3. F3 mit Test am Testersatz; H2 im bestehenden API Test; bestehende Tests bleiben grün.

**Übergabe:** Ergebnis in PR #305 (Branch claude/project-thread-hulabb), ohne Migration und ohne Vertragsänderung. F1: `WikiPage` zeigt bei einem veröffentlichten Treffer, dessen gefundene Fassung nicht mehr veröffentlicht oder nicht mehr lesbar ist, „Nicht mehr veröffentlicht“ mit dem Hinweis, dass die gefundene Fassung nicht mehr zur Verfügung steht, auch bei 404 und auch statt einer stillen Ersatzfassung; „Entwurf zeigen“ nur in derselben Sprache, eine andere veröffentlichte Sprachfassung über „Fassung … zeigen“ (zwei Browsertests mit reinem Lesekonto). F2: `SearchSnippet` verbindet Zeichen, die NFKC zusammensetzt, zu einem Stück und bildet die Kleinschreibung über die ganze Normalform wie `SearchText.terms` (Unit Tests mit `ㄱㅏ` und `İ ΟΣ` in der Prüfung, dass jedes Indexwort markiert wird, API Test `composedJamoChooseTheirCriterionAndAFinalSigmaIsMarked`); der Testersatz markiert gleich. F3: Der Testersatz entfernt Leerraum wie Javas `strip()`, U+FEFF bleibt Inhalt (Test direkt am Testersatz). H2: `anArchivedParentKeepsThePathUpToTheRoot` mit Wurzeltitel „Wurzel“ veröffentlicht und „Wurzel Entwurf“ im Entwurf. Läufe: Backend `mvn verify` 631 Tests grün (Java 21, PostgreSQL 16), `npm run verify` grün, Browsertests 285 grün. Beschreibung in backend/README.md, Abschnitt „Wiki in der Suche“.

**Nächster Schritt:** Prüfung durch Codex (Auftrag liegt im Projektordner a14), danach Holgers Abnahme von TASK-102 bis TASK-106.

## TASK-107 Befunde der Codex Prüfung von TASK-106 beheben (A14)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261112-search-04 · **Aktualisiert:** 2026-11-12

**Bezug:** REQ-SRCH-001, REQ-DOC-004, REQ-ACL-004; DEC-132, DEC-136; docs/REVIEW_TASK-106_PR-305.md. **Abhängigkeiten:** TASK-106 (PR #305) geliefert. **Arbeitsstand:** Holger hat am 12. November 2026 den Bericht der Codex Prüfung von TASK-106 (Empfehlung nachbessern) in den Thread gegeben und auf der Entscheidungskarte „Alles beheben als TASK-107“ gewählt. Branch claude/project-thread-hulabb, Entscheidung DEC-137, keine Migration geplant.

**Umfang:** Befunde F1 bis F4 und Hinweis H1 aus docs/REVIEW_TASK-106_PR-305.md:
1. F1: Nach einem veröffentlichten Suchtreffer öffnet die Wahl einer Sprache in der Sprachleiste deren veröffentlichte Fassung, auch mit `wiki.edit` oder `wiki.publish`; den Entwurf zeigt nur die ausdrückliche Aktion.
2. F2: Die Markierung normalisiert keine wachsenden Teilstücke mehr; ihre Laufzeit wächst linear mit der Textlänge, auch bei langen Folgen kombinierender Zeichen.
3. F3: Wörter und Kleinschreibung der Markierung folgen genau der Normalform des Index, und eine Markierung umfasst nur die Originalzeichen, aus denen ihr Wort entsteht.
4. F4: Der Testersatz schreibt das Sigma wie Java klein, sodass Treffer und Markierungen auch bei `AΣ1A` gleich sind.
5. H1: Die Statuszeile einer Ersatzfassung erscheint erst mit ihr und nennt die Sprache.

Nicht Teil: neue Regeln.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 mit Browsertest für ein Konto mit Entwurfsrecht und unterschiedlichen englischen Titeln von Entwurf und Veröffentlichung.
2. F2 mit einem Test, der einen langen Text aus U+FF9E und U+0334 in kurzer Zeit markiert.
3. F3 mit Unit Tests für `カ\u3099\u3099\uFF9E\u0334\uFF9E\u0334 end` (Markierung nur `カ\u3099`) und einem Vergleich der Normalform mit `SearchText.terms` über viele zufällige Texte.
4. F4 mit Fällen am Testersatz (`AΣ1A`, `AΣ-A`, `AΣ_A`); H1 im Browsertest; bestehende Tests bleiben grün.

**Übergabe:** Ergebnis in PR #307 (Branch claude/project-thread-hulabb), ohne Migration und ohne Vertragsänderung; der Index ändert sich nicht. F1: `WikiPage` merkt sich die Sprache des gefundenen Treffers getrennt vom Vorrang der Veröffentlichung; die Wahl einer Sprache in der Sprachleiste oder „Fassung … zeigen“ zeigt deren veröffentlichte Fassung, den Entwurf nur „Entwurf zeigen“ (Browsertest mit Entwurfsrecht und unterschiedlichen englischen Titeln). F2: neue Klasse `Nfkc` mit demselben Ergebnis wie `java.text.Normalizer`, die lange Folgen kombinierender Zeichen selbst stabil nach Kombinationsklasse vorordnet; genutzt von `SearchText` (Index und Suchtext), `SearchService` (Kennungen) und `SearchSnippet`; 100.000 solcher Zeichen in rund 0,14 s (`aLongRunOfReorderedMarksIsMarkedQuickly`, `theLinearNormalFormIsNfkc` mit 3.000 Zufallstexten gegen den Normalizer). F3: `SearchSnippet` zerlegt an den Grenzen der Normalisierung und sucht den kürzesten Anfang eines Abschnitts mit allen seinen Buchstaben per Halbierung (`aMarkCoversOnlyTheCharactersOfItsWord`, `theWordsOfTheMarksAreThoseOfTheIndexForRandomTexts` mit 3.000 Zufallstexten gegen `SearchText.terms`); Testersatz gleich, ein Abgleich von 800 Zufallstexten mit Java ergab nur Abweichungen beim Sigma zwischen Buchstaben anderer Schriften. F4: Testersatz mit der Kleinschreibung des Sigma nach Javas Wortgrenzen für gängige Fälle (`AΣ1A`, `AΣ-A`, `AΣ_A`, `AΣ A`, `A1Σ`), Grenze im Test und in backend/README.md benannt. H1: keine Statuszeile neben „Nicht mehr veröffentlicht“, eine Ersatzfassung nennt ihre Sprache. Läufe: Backend `mvn verify` 636 Tests grün (Java 21, PostgreSQL 16), `npm run verify` grün, Browsertests 287 grün.

**Nächster Schritt:** Prüfung durch Codex (Auftrag liegt im Projektordner a14), danach Holgers Abnahme von TASK-102 bis TASK-107.

## TASK-108 Befunde der Codex Prüfung von TASK-107 beheben (A14)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261112-search-05 · **Aktualisiert:** 2026-11-12

**Bezug:** REQ-SRCH-001, REQ-DOC-004, REQ-ACL-004; DEC-132, DEC-137; docs/REVIEW_TASK-107_PR-307.md. **Abhängigkeiten:** TASK-107 (PR #307) geliefert. **Arbeitsstand:** Holger hat am 12. November 2026 den Bericht der Codex Prüfung von TASK-107 (Empfehlung nachbessern) in den Thread gegeben und auf der Entscheidungskarte „Alles beheben als TASK-108“ gewählt. Branch claude/project-thread-hulabb, Entscheidung DEC-138, keine Migration geplant.

**Umfang:** Befunde F1 bis F4 und Hinweis H2 aus docs/REVIEW_TASK-107_PR-307.md:
1. F1: Nach einem veröffentlichten Suchtreffer ist jede Wahl einer Sprache, auch die erneute Wahl der gefundenen Sprache und eine Sprache nur mit Entwurf, eine neue Entscheidung für deren veröffentlichte Fassung. Fehlt sie, erscheint ein Hinweis mit eigener Aktion für den Entwurf. Eine gewählte Entwurfsansicht gilt nur für ihre Sprache.
2. F2: Die Markierung braucht auch bei später Zusammensetzung (etwa A, viele U+0334, dann U+0301) linearen Aufwand.
3. F3: Ergibt ein Normalisierungsabschnitt mehrere Indexwörter (etwa `½ᅡ`, `กำ`), umfasst jede Markierung nur die Originalzeichen ihres Worts, mit richtigen UTF-16 Positionen auch nach ergänzenden Zeichen; Testersatz gleich.
4. F4: Der Testersatz schreibt das Sigma an Doppelpunkt und Mittelpunkt (`AΣ:A`, `AΣ·A`) wie Java klein.
5. H2: Weicht die verkettete Abschnittsnormalform von NFKC des ganzen Texts ab, entstehen keine Markierungen aus einer anderen Form als der des Index.

Nicht Teil: neue Regeln.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 mit Browsertests für beide im Bericht genannten Wege.
2. F2 mit einem Laufzeittest für späte Zusammensetzung.
3. F3 mit Unit Tests für die vier Fälle des Berichts und einem Zufallstest, der den genauen Originalbereich jedes Worts prüft; Testersatz mit `½ᅡ`.
4. F4 mit Fällen am Testersatz; H2 mit Test oder begründeter Beschreibung; bestehende Tests bleiben grün.

**Übergabe:** Ergebnis im Ergebnis PR nach #308 (Branch claude/project-thread-hulabb), ohne Migration und ohne Vertragsänderung; der Index ändert sich nicht. F1: `WikiPage` merkt sich die Sprache eines ausdrücklich gewählten Entwurfs; jede Wahl in der Sprachleiste vergisst sie und zeigt nach einem veröffentlichten Treffer die veröffentlichte Fassung, ohne Veröffentlichung „Nicht veröffentlicht“ mit „Entwurf zeigen“ (drei Browsertests: erneute Wahl der gefundenen Sprache, Sprache nur mit Entwurf, Entwurf Deutsch dann Englisch). F2, F3, H2: neue Methode `Nfkc.origins` bildet NFKC des ganzen Texts mit den Originalzeichen jedes Zeichens (Zerlegung je Zeichen, stabile Ordnung nach Kombinationsklasse, kanonische Zusammensetzung über den Normalizer), `SearchSnippet` markiert jedes Wort vom ersten bis zum letzten eigenen Originalzeichen und markiert nichts, wenn die Form vom Index abweicht; die Abschnitte und die Halbierung entfallen (Unit Tests `severalWordsOfOneCharacterAndItsMarksKeepTheirOwnPlaces` mit den vier Fällen des Berichts, `thePlacesComeFromTheNormalFormOfTheIndex` mit 5.000 Zufallstexten und genauer Prüfung jedes Wortbereichs, späte Zusammensetzung nach 480.000 Zeichen im Laufzeittest). Testersatz gleich. F4: Testersatz bildet die Wortgrenzen des JDK für das Sigma nach, abgeglichen mit Java 21 für jedes BMP Zeichen neben einem Sigma und 200.000 Zufallstexte ohne Abweichung; Fälle `AΣ:A`, `AΣ·A`, `AΣ"A`, `A1Σ,1`, `AΣ1,1A` im Test; Grenze bei Zeichen außerhalb der BMP benannt. Läufe: Backend `mvn verify` 638 Tests grün (Java 21, PostgreSQL 16), `npm run verify` grün, Browsertests 291 grün. Beschreibung in backend/README.md, Abschnitt „Wiki in der Suche“.

**Nächster Schritt:** Prüfung durch Codex (Auftrag liegt im Projektordner a14), danach Holgers Abnahme von TASK-102 bis TASK-108.

## TASK-109 Befunde der Codex Prüfung von TASK-108 beheben (A14)

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261112-search-06 · **Aktualisiert:** 2026-11-12

**Bezug:** REQ-SRCH-001, REQ-DOC-004, REQ-ACL-004; DEC-132, DEC-138; docs/REVIEW_TASK-108_PR-309.md. **Abhängigkeiten:** TASK-108 (PR #309) geliefert. **Arbeitsstand:** Holger hat am 12. November 2026 den Bericht der Codex Prüfung von TASK-108 (Empfehlung nachbessern) in den Thread gegeben und auf der Entscheidungskarte „F1 bis F4 beheben, dann Abnahme“ gewählt. Branch claude/project-thread-hulabb, Entscheidung DEC-139, keine Migration geplant.

**Umfang:** Befunde F1 bis F4 aus docs/REVIEW_TASK-108_PR-309.md:
1. F1: Nach einem veröffentlichten Suchtreffer bleibt der Vorrang der Veröffentlichung auch nach Verwaltungsaktionen wie dem Verschieben erhalten; Entwürfe erscheinen in keiner Sprache ohne „Entwurf zeigen“.
2. F2: Die Zuordnung der Originalzeichen braucht auch bei großen Kompatibilitätszerlegungen (etwa viele U+FDFA) begrenzten Speicher ohne Objekt je Zeichen.
3. F3: Der Testersatz verarbeitet ein Wort mit 150.000 Zeichen und A mit 150.000 U+0334 und U+0301 ohne `RangeError`.
4. F4: Die Grenze des Testersatzes bei Zeichen, die Java 21 und Node in unterschiedlichen Unicode Versionen kennen (etwa U+1ACF), ist benannt oder über dieselben Zeicheneigenschaften abgesichert.

Nicht Teil: neue Regeln.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. F1 mit einem Browsertest: Verschieben nach einem veröffentlichten Treffer, danach zeigt weder die aktuelle noch eine andere Sprache einen Entwurf ohne „Entwurf zeigen“.
2. F2 mit einem Test für einen großen Text mit U+FDFA und einer Beschreibung des Speicherbedarfs.
3. F3 mit Fällen am Testersatz für beide Texte des Berichts.
4. F4 mit dem Fall `AΣ\u1ACFA` als benannte oder behobene Grenze; bestehende Tests bleiben grün.

**Nächster Schritt:** Nach dem Merge dieser Reservierung umsetzen und Ergebnis PR mit Nachweisen öffnen; danach Holgers Abnahme von TASK-102 bis TASK-109.
