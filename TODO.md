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
| Blockiert (BLOCKIERT) | 1 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 0 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-093](#task-093-wikiseiten-mit-entwurf-veröffentlichung-und-historie-a12-teil-1) | Wikiseiten mit Entwurf, Veröffentlichung und Historie (A12 Teil 1) | claude-20261109-wiki-01 | 2026-11-09 |

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-094](#task-094-sprachfassungen-im-wiki-a12-teil-2) | Sprachfassungen im Wiki (A12 Teil 2) | claude-20261109-wiki-01 | 2026-11-09 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

Derzeit keine Aufgaben in dieser Kategorie.

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-093 Wikiseiten mit Entwurf, Veröffentlichung und Historie (A12 Teil 1)

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261109-wiki-01 · **Aktualisiert:** 2026-11-09

**Bezug:** REQ-DOC-001, REQ-DOC-002, REQ-DOC-004; OPEN-011; DEC-033; docs/WIKI_I18N_BRANDING.md (Prüffälle W01 bis W06, W09 bis W11). **Abhängigkeiten:** TASK-026, TASK-051, TASK-043, geliefert. **Arbeitsstand:** Holger hat am 9. November 2026 im Projektgespräch mit „ja“ den Beginn von Paket A12 bestätigt und am selben Tag die 21 Vorschläge mit „Passt so“ übernommen (Regeln unten und bei TASK-094). Geplanter Branch claude/project-thread-ipd3h4, Entscheidung DEC-123, Migrationen ab V0055.

**Umfang:** Wiki je Projekt mit Seitenbaum, gemeinsamer Entwurf, ausdrückliche Veröffentlichung, Zurücknehmen, Archivieren und Wiederherstellen, Revisionen mit Historie und Vergleich, Konfliktschutz über die Version, visueller Editor mit festem Elementumfang, Bilder in Seiten, Verweise zwischen Wikiseiten, die Projektaktionen `wiki.read`, `wiki.edit` und `wiki.publish`, mit Migration, Vertrag, Oberfläche und Tests. Nicht Teil: Sprachfassungen (TASK-094), Suche (A14), Wiki im Kundenportal, KI Überarbeitung (A16), Import und Export, Beziehungen zu Tickets und Anforderungen, Echtzeitbearbeitung, Kommentare, endgültiges Löschen, Verschieben in ein anderes Projekt, Unternehmenswiki.

**Fachliche Regeln** (von Holger am 9. November 2026 bestätigt):
1. Jedes Projekt hat ein Wiki; ein eigenes Unternehmenswiki gibt es nicht.
2. Eine Seite hat höchstens eine Elternseite im selben Projekt, höchstens 6 Ebenen tief, Geschwister alphabetisch nach Titel. Seiten haben eine feste Kennung; Umbenennen und Verschieben im Baum ändern keinen Verweis.
3. `wiki.read` liest veröffentlichte Seiten; `wiki.edit` liest Entwürfe und Historie, legt Seiten an, bearbeitet und verschiebt; `wiki.publish` veröffentlicht, nimmt zurück, archiviert und stellt wieder her. Kein Vier Augen Verfahren. Portalkonten erhalten keine Wikirechte.
4. Titel mit 1 bis 200 Zeichen, nicht eindeutig erzwungen.
5. Je Seite und Sprache gibt es einen gemeinsamen Entwurf. „Entwurf speichern“ erzeugt eine neue Revision; Revisionen werden nie überschrieben. Kein automatisches Speichern; Speicherzustand sichtbar, Warnung beim Verlassen mit ungespeicherten Änderungen.
6. Speichern und Veröffentlichen senden die bekannte Version mit If-Match; ist der Entwurf neuer, lehnt der Server mit 412 ab. Die eigene Eingabe bleibt erhalten, daneben erscheint der neuere Entwurf mit markierten Unterschieden. Keine Sperren, keine automatische Zusammenführung.
7. Der Veröffentlichungsdialog zeigt Revision, Sprache und Leserkreis und veröffentlicht genau diese Revision. Leser sehen bis dahin die bisherige Veröffentlichung. „Veröffentlichung zurücknehmen“ entfernt die Leserfassung und behält die Historie.
8. Die Historie listet alle Revisionen mit Person, Zeit und Kennzeichen „veröffentlicht“. Eine alte Revision lässt sich ansehen, mit einer anderen nach Blöcken vergleichen und als neuer Entwurf übernehmen, der erneut veröffentlicht werden muss.
9. Eine archivierte Seite verschwindet für Leser und aus dem Baum, bleibt mit Historie für `wiki.publish` sichtbar und lässt sich wiederherstellen; Unterseiten wandern zur nächsthöheren Ebene.
10. Anlegen, Speichern, Veröffentlichen, Zurücknehmen, Archivieren und Wiederherstellen stehen mit Person und Zeit in der Seitenhistorie.
11. Editor Tiptap 3 nur mit freien Paketen unter MIT Lizenz, ohne kostenpflichtige Erweiterungen und Clouddienst. Gespeichert wird das strukturierte JSON mit Schemaversion; der Server prüft erlaubte Elemente, Größen und Verweise selbst und erzeugt daraus die Leseansicht.
12. Elemente: Absätze, Überschriften in drei Stufen, fett, kursiv, Code im Text, geordnete und ungeordnete Listen, Tabellen mit Kopfzeile, Links, Bilder mit Alternativtext, Zitate, Codeblöcke, Trennlinien. Tastaturbedienung mit sichtbarem Fokus. Kein freies HTML, keine Skripte, keine Fremdinhalte.
13. Links nur https, http und mailto sowie Verweise auf Wikiseiten desselben Projekts; ein Verweis auf eine archivierte oder nicht lesbare Seite zeigt „Seite nicht verfügbar“ ohne Titel.
14. Bilder als PNG, JPEG oder WebP bis 5 MB, keine SVG Dateien, im vorhandenen Dateispeicher, ausgeliefert mit dem Leserecht der Seite; Entwurfsbilder nur mit `wiki.edit`. Externe Bilder werden nie nachgeladen.
15. Beim Einfügen bleiben erlaubte Elemente erhalten, andere werden zu Text, mit Hinweis auf Entferntes. Eine Revision höchstens 1 MB und 50 Bilder; der Server nennt die Grenze mit 400.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Ein gespeicherter Entwurf ändert die Leserfassung nicht; nur eine Veröffentlichung mit aktueller Version macht ihn lesbar (W03, W04, W06).
2. Zwei Bearbeitungen auf gleicher Grundlage: die zweite erhält 412, nichts wird überschrieben, die Eingabe bleibt erhalten (W05).
3. Die Beispielseite bleibt nach dreimaligem Bearbeiten strukturell erhalten (W02); Skripte, freies HTML und externe Bilder werden abgewiesen oder entfernt (W10).
4. Ohne `wiki.read` keine Seite, kein Bild, kein Verweistitel; ohne `wiki.edit` kein Entwurf und keine Historie (W09); Umbenennen und Verschieben erhalten Verweise (W11).
5. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für Baum, Editor, Konflikt, Veröffentlichung und Historie.

**Nächster Schritt:** Reservierung mergen, danach Umsetzung auf dem Branch; anschließend TASK-094 in derselben Sitzung, sobald das Ergebnis auf main ist.

## TASK-094 Sprachfassungen im Wiki (A12 Teil 2)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261109-wiki-01 · **Aktualisiert:** 2026-11-09

**Bezug:** REQ-DOC-003, REQ-I18N-001; OPEN-011; DEC-033; docs/WIKI_I18N_BRANDING.md (Optionale Übersetzungen, Prüffälle W01, W07, W08, W12). **Abhängigkeiten:** TASK-093 auf main. **Arbeitsstand:** Mit TASK-093 von Holger am 9. November 2026 beauftragt und mit den Vorschlägen bestätigt; blockiert, bis das Ergebnis von TASK-093 auf main ist. Geplanter Branch claude/project-thread-ipd3h4, Entscheidung DEC-124, eigene Migration nach denen von TASK-093.

**Umfang:** Sprachfassungen Deutsch und Englisch je Seite mit eigenem Titel, Entwurf, Revisionen und Veröffentlichung, Rückfallregel für Leser, Übersetzungshinweis mit Vergleich, Demodaten, mit Migration, Vertrag, Oberfläche und Tests. Nicht Teil: weitere Sprachen, automatische oder KI Übersetzung, Suche.

**Fachliche Regeln** (von Holger am 9. November 2026 bestätigt):
1. Sprachen sind Deutsch und Englisch. Die erste Fassung ist die Ausgangssprache; eine weitere Fassung ist freiwillig und hat eigenen Titel, Entwurf, Revisionen und Veröffentlichung. Eine rein deutsche Seite ist vollständig nutzbar.
2. Leser erhalten die veröffentlichte Fassung in ihrer Oberflächensprache, sonst die veröffentlichte Ausgangssprache, sonst die andere veröffentlichte Fassung, jeweils mit sichtbarem Sprachhinweis. Entwürfe sind nie Rückfallinhalt, ihr Bestehen wird Lesern nicht verraten.
3. Eine Fassung merkt sich die übersetzte Revision der anderen Sprache. Wird die Ausgangsfassung danach neu veröffentlicht, sehen Personen mit `wiki.edit` „Ausgangsfassung wurde geändert“ mit Vergleich, Leser einen dezenten Hinweis.
4. Das Vorführimage erhält im Demoprojekt einen kleinen Seitenbaum mit der Seite „Serviceanfrage ergänzen“, veröffentlicht auf Deutsch, mit englischem Entwurf.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Eine deutsche Seite ohne Übersetzung ist vollständig nutzbar (W01); eine englische Fassung gehört zur selben Seite.
2. Ist die gewünschte Sprache nur Entwurf, erhalten Leser ausschließlich eine veröffentlichte Rückfallfassung mit Sprachhinweis (W08).
3. Nach neuer Veröffentlichung der Ausgangsfassung bleibt die Übersetzung erhalten und zeigt den Hinweis (W07).
4. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests in Deutsch und Englisch.

**Nächster Schritt:** Nach dem Ergebnis von TASK-093 auf main die Blockade aufheben und umsetzen.
