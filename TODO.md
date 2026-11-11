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
| Durch eine KI reserviert (IN ARBEIT) | 0 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 2 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 1 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

Derzeit keine Aufgaben in dieser Kategorie.

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-103](#task-103-anforderungen-in-der-gemeinsamen-suche-a14-teil-2) | Anforderungen in der gemeinsamen Suche (A14 Teil 2) | claude-20261111-search-01 | 2026-11-11 |
| [TASK-104](#task-104-gemeinsame-suchübersicht-schnellsuche-und-messung-a14-teil-3) | Gemeinsame Suchübersicht, Schnellsuche und Messung (A14 Teil 3) | claude-20261111-search-01 | 2026-11-11 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-102](#task-102-wiki-in-der-gemeinsamen-suche-a14-teil-1) | Wiki in der gemeinsamen Suche (A14 Teil 1) | claude-20261111-search-01 | 2026-11-11 |

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
2. Standardmäßig wird nur die veröffentlichte Revision jeder Sprachfassung durchsucht, auch für Personen mit `wiki.edit`. Mit `wiki.edit` lässt sich „Entwürfe durchsuchen“ wählen; dann wird stattdessen der aktuelle Entwurf durchsucht und der Treffer als Entwurf gekennzeichnet. Nie gemischt.
3. Beide Sprachfassungen werden durchsucht, ein Treffer je Seite: Treffen beide, zeigt der Treffer die Fassung in der Oberflächensprache, sonst die getroffene, jeweils mit Sprachkennzeichen. Filter „Sprache“ mit Deutsch, Englisch oder beide.
4. Archivierte Seiten werden nicht gefunden; mit „Archivierte einbeziehen“ (nur mit `wiki.edit`) erscheinen sie gekennzeichnet. Umgesetzt mit `wiki.publish`, weil das Wiki archivierte Seiten nur damit zeigt (DEC-132 Auslegung 1).
5. Suchen verlangt Mitgliedschaft und `wiki.read`, Entwürfe und Archiv `wiki.edit`. Ohne Recht weder Treffer noch Zähler noch Vorschau. Portalkonten erhalten keine Wikisuche (403).
6. Ein Treffer zeigt Titel, Projekt, Pfad der Elternseiten (nicht lesbare als „Seite nicht verfügbar“), Sprache, Stand, Zeitpunkt der Veröffentlichung und einen Ausschnitt als Klartext mit den Positionen der gefundenen Wörter, nie als HTML.
7. Textregeln wie TASK-029: keine Wortstämme, jedes Wort als Präfix, alle Wörter verlangt, keine Suchsyntax, dieselben Grenzen und 503 `search.busy`.
8. Die Projektion wird in derselben Transaktion geschrieben; Rechte gelten bei jeder Suche neu. `rebuild-search` baut auch das Wiki neu auf, die Migration löscht den Aufbauvermerk.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Ein Wort, das nur im Entwurf einer veröffentlichten Seite steht, findet ein Leser nicht; mit „Entwürfe durchsuchen“ und `wiki.edit` erscheint die Seite als Entwurf (T11).
2. Ein Wort der englischen Fassung findet die Seite mit Sprachkennzeichen; der Sprachfilter grenzt ein.
3. Ohne Mitgliedschaft oder `wiki.read` keine Treffer und keine Zähler; archivierte Seiten nur mit dem Filter und `wiki.edit`.
4. Nach Veröffentlichen ist der neue Stand in der folgenden Suche sichtbar (T13); `rebuild-search` liefert dieselben Treffer.
5. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für den Bereich Wiki.

**Übergabe:** Ergebnis auf dem Branch claude/project-thread-hulabb (Ergebnis-PR). Umgesetzt wie oben mit Migration V0062, Vertrag `contracts/search.openapi.yaml` 0.3.0, Beschreibung in backend/README.md (Abschnitt „Wiki in der Suche“) und DEC-132 mit sechs Auslegungen zur Prüfung, darunter das Archiv nur mit `wiki.publish`. Nachweise: `WikiSearchApiTest` (Entwürfe nur auf Wunsch, Sprachfassungen als ein Treffer, Rechte für Treffer, Zähler und Archiv, sichtbarer Text, Pfad und Neuaufbau, Liste ohne Suchtext), `SearchSnippetTest`, `WikiPlainTextTest`, `SearchContractTest`, vollständiger Backendlauf, Browsertest des Bereichs Wiki in `wiki.test.mjs`, vollständige Browsertests.

**Nächster Schritt:** Prüfung durch Codex oder eine getrennte Claude Sitzung, danach Abnahme durch Holger; TASK-103 beginnt, sobald das Ergebnis auf main ist.

## TASK-103 Anforderungen in der gemeinsamen Suche (A14 Teil 2)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261111-search-01 · **Aktualisiert:** 2026-11-11

**Bezug:** REQ-SRCH-001, REQ-REQ-001, REQ-ACL-004; DEC-050; docs/TAGS_SEARCH.md (Suchumfang, Prüffälle T09, T13, T20). **Abhängigkeiten:** TASK-102 auf main; TASK-096 bis TASK-098, geliefert. **Arbeitsstand:** Mit TASK-102 von Holger am 11. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-102 auf main ist. Geplanter Branch claude/project-thread-hulabb, Entscheidung DEC-133, eigene Migration nach denen von TASK-102.

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

**Nächster Schritt:** Nach dem Ergebnis von TASK-102 auf main die Blockade aufheben und umsetzen.

## TASK-104 Gemeinsame Suchübersicht, Schnellsuche und Messung (A14 Teil 3)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261111-search-01 · **Aktualisiert:** 2026-11-11

**Bezug:** REQ-SRCH-001, REQ-PERF-003; DEC-047, DEC-050; OPEN-014; ACC-009; docs/TAGS_SEARCH.md (Messziele, Prüffälle T15, T20), docs/OPERATIONS.md, docs/DEMO.md. **Abhängigkeiten:** TASK-103 auf main; TASK-031. **Arbeitsstand:** Mit TASK-102 von Holger am 11. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-103 auf main ist. Geplanter Branch claude/project-thread-hulabb, Entscheidung DEC-134.

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

**Nächster Schritt:** Nach dem Ergebnis von TASK-103 auf main die Blockade aufheben und umsetzen.
