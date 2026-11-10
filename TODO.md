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
| Blockiert (BLOCKIERT) | 0 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 4 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

Derzeit keine Aufgaben in dieser Kategorie.

### Blockiert – BLOCKIERT

Derzeit keine Aufgaben in dieser Kategorie.

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-096](#task-096-anforderungen-mit-kriterien-revisionen-und-freigabe-a13-teil-1) | Anforderungen mit Kriterien, Revisionen und Freigabe (A13 Teil 1) | claude-20261110-requirements-01 | 2026-11-10 |
| [TASK-097](#task-097-verknüpfungen-von-anforderungen-mit-tickets-und-wikiseiten-a13-teil-2) | Verknüpfungen von Anforderungen mit Tickets und Wikiseiten (A13 Teil 2) | claude-20261110-requirements-01 | 2026-11-10 |
| [TASK-098](#task-098-eigene-felder-für-anforderungen-a13-teil-3) | Eigene Felder für Anforderungen (A13 Teil 3) | claude-20261110-requirements-01 | 2026-11-10 |
| [TASK-099](#task-099-befunde-der-codex-nachprüfung-von-task-096-beheben-a13) | Befunde der Codex Nachprüfung von TASK-096 beheben (A13) | claude-20261110-requirements-01 | 2026-11-10 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-096 Anforderungen mit Kriterien, Revisionen und Freigabe (A13 Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261110-requirements-01 · **Aktualisiert:** 2026-11-10

**Bezug:** REQ-REQ-001, REQ-REQ-002, REQ-ACL-004; DEC-030; OPEN-021; ACC-006; docs/DOMAIN_MODEL.md (Anforderungsrevisionen und Freigabe, Prüffall F-13). **Abhängigkeiten:** TASK-026, TASK-027, TASK-043, TASK-068 und TASK-093, geliefert. **Arbeitsstand:** Holger hat am 10. November 2026 im Projektgespräch mit „ja mache mit A13 weiter“ den Beginn von Paket A13 beauftragt und am selben Tag die 24 Vorschläge mit „passt“ übernommen (Regeln unten und bei TASK-097 und TASK-098). Geplanter Branch claude/project-thread-dyiimb, Entscheidung DEC-126, Migrationen ab V0058. Übergabe 10. November 2026: Ergebnis in PR #286 von claude/project-thread-dyiimb mit Modul `requirements`, Migration V0058, Vertrag `contracts/requirements.openapi.yaml` 0.1.0, Rechtekatalog mit Ressourcentyp `requirement` (`contracts/permissions.openapi.yaml` 0.4.0), Seite „Anforderungen“ mit Liste, Filtern, Bearbeitung, Kriterien per Ziehen und Tastatur, Konflikt mit markierten Unterschieden, Freigabedialog, Zurückgeben, Verwerfen, Wiederherstellen, Historie und Vergleich sowie Feldregeln für Anforderungen in Gruppen und Projektrechten. Fünf Auslegungen stehen zur Bestätigung in DEC-126, darunter Wiederherstellen nach Freigegeben, wenn die laufende Revision schon freigegeben ist. Belege und Grenzen in PR #286. Nachbesserung 10. November 2026 nach der Codex Prüfung (docs/REVIEW_TASK-096_PR-286.md, Empfehlung nachbessern) im selben PR: F1 „Meine Eingaben behalten“ übernimmt Kriterien je fester Nummer, fremde Änderungen und Ergänzungen bleiben; F2 Titel und Priorität im Freigabedialog; F3 Linkziele im Beschreibungsvergleich über die gemeinsame Vergleichsansicht des Wikis; F4 die Seitenleiste rollt in niedrigen Fenstern, Sprache und Darstellung bleiben erreichbar; F5 Ansage auch bei den Pfeilschaltflächen; F6 Schutz der Eingaben im Anlegeformular; F7 Kriterienkennungen der Historie ohne doppeltes K; H1 CRLF und CR werden vor der Prüfung zu LF; H2 der Testersatz prüft Kriteriennummern, Verantwortliche, Linkziele und Codes wie der Server, die Codes der Beschreibung heißen durchgehend `description.*`; H3 nach Holgers Wahl „Ja, nach lesbaren Feldern“: `editors` nennt nur Personen lesbarer Inhaltsänderungen.

**Umfang:** Anforderungen je Projekt mit Kennung, festen Feldern, Abnahmekriterien, festem Ablauf, fachlichen Revisionen, Freigabe genau einer Revision nach DEC-030, Historie, Vergleich, Liste, Feldrechten und den Projektaktionen `requirement.read`, `requirement.edit` und `requirement.approve`, mit Migration, Vertrag, Oberfläche und Tests. Nicht Teil: Verknüpfungen (TASK-097), eigene Felder (TASK-098), Git Objekte (A15), Suche (A14), KI (A16), Portal, formale Stände und Änderungsanträge (REQ-REQ-004), Testfälle, Beziehungen zwischen Anforderungen, Import und Export, Kommentare, endgültiges Löschen, Verschieben in ein anderes Projekt, Bilder in der Beschreibung, Vier Augen Pflicht.

**Fachliche Regeln** (von Holger am 10. November 2026 bestätigt):
1. Eine Anforderung gehört zu genau einem Projekt. Jedes Projekt zählt seine Anforderungen eigenständig, angezeigt als „ANF-17“ neben dem Projekt; die Kennung ändert sich nie.
2. Feste Felder: Titel (1 bis 200 Zeichen), Beschreibung, Verantwortliche Person (optional, internes Mitglied des Projekts, kein Portalkonto), Priorität niedrig, normal, hoch oder kritisch (Vorgabe normal) und Status.
3. Die Beschreibung nutzt Editor und vom Server geprüftes Format des Wikis ohne Bilder.
4. Abnahmekriterien sind eine geordnete Liste von 0 bis 50 Klartexten mit 1 bis 2000 Zeichen und fester Kennung innerhalb der Anforderung (K1, K2, nie neu vergeben), Reihenfolge per Ziehen und per Tastatur änderbar. Zur Prüfung geben setzt mindestens ein Kriterium voraus.
5. Fester Ablauf: Entwurf nach Zur Prüfung; Zur Prüfung nach Freigegeben oder mit Pflichtbegründung zurück nach Entwurf; jeder Stand nach Verworfen und von dort zurück nach Entwurf. Nicht je Projekt konfigurierbar.
6. Speichern im Entwurf ändert die laufende Revision und schreibt einen Historieneintrag. Erst die erste inhaltliche Änderung nach einer Freigabe öffnet Revision n+1 als Entwurf; die freigegebene Revision bleibt unverändert lesbar, angezeigt als „Revision 1 freigegeben, Revision 2 in Bearbeitung“.
7. Inhaltlich sind Titel, Beschreibung, Kriterien, Priorität und eigene Felder. Verantwortliche Person, Verknüpfungen und Verwerfen sind organisatorisch: keine neue Revision, aber ein Historieneintrag.
8. Der Freigabedialog zeigt die Revision mit allen Inhalten und gibt genau diese frei (If-Match, sonst 412). Freigebende Person, Zeitpunkt und Revision bleiben unveränderlich erhalten. Wer ein Inhaltsfeld nicht lesen darf, kann nicht freigeben (403).
9. Vier Augen werden nicht erzwungen; der Dialog nennt, wer die Revision bearbeitet hat.
10. `requirement.read` liest Anforderungen und Historie; `requirement.edit` legt an, bearbeitet, gibt zur Prüfung, verwirft und stellt wieder her; `requirement.approve` gibt frei und gibt zurück. Feldrechte je Feld über die Rechteverwaltung für den Ressourcentyp `requirement`. Portalkonten erhalten keine Anforderungsrechte.
11. Jede Änderung sendet die bekannte Version mit If-Match (428, 412); die eigene Eingabe bleibt erhalten, der neuere Stand erscheint mit markierten Unterschieden daneben.
12. Die Historie nennt Anlegen, Feldänderungen, Kriterienänderungen, Statuswechsel mit Begründung, Freigaben und neue Revisionen mit Person und Zeit; Einträge zu nicht lesbaren Feldern bleiben verborgen.
13. Zwei Revisionen lassen sich vergleichen: Felder einzeln, Kriterien nach Kennung, Beschreibung nach Blöcken; nur lesbare Felder.
14. Die Projektliste zeigt Kennung, Titel, Status, Revision, Priorität, Verantwortliche Person und Freigabestand, filterbar nach Status, Priorität, Verantwortlicher Person und Titeltext, sortiert nach Kennung, seitenweise.
15. Kein Löschen; verworfene Anforderungen bleiben mit Historie über einen Filter erreichbar.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Die Beispielanforderung „Kunden sehen den Stand ihrer freigegebenen Supportanfragen“ lässt sich mit prüfbaren Kriterien erfassen, zur Prüfung geben und freigeben (REQ-REQ-001, REQ-REQ-002).
2. Ein neues Kriterium nach der Freigabe erzeugt eine ungeprüfte Revision; die alte Freigabe mit Person, Zeit und Fassung bleibt sichtbar und wird nicht übertragen (F-13, DEC-030).
3. Eine Freigabe auf veralteter Version erhält 412; ohne Leserecht auf ein Inhaltsfeld keine Freigabe.
4. Ohne `requirement.read` keine Anforderung, ohne Leserecht eines Felds weder Wert noch Historie noch Vergleich dieses Felds.
5. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für Liste, Bearbeitung, Konflikt, Freigabe, Historie und Vergleich.

**Nächster Schritt:** Nach Holgers Wahl eine Codex Nachprüfung der Nachbesserung, dann fachliche Abnahme durch Holger, dabei die Auslegungen (a) bis (e) in DEC-126 bestätigen; nach dem Merge des Ergebnisses TASK-097 in derselben Sitzung.

## TASK-099 Befunde der Codex Nachprüfung von TASK-096 beheben (A13)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261110-requirements-01 · **Aktualisiert:** 2026-11-10

**Bezug:** REQ-REQ-001, REQ-REQ-002, REQ-ACL-004; DEC-126; docs/REVIEW_TASK-096_PR-286_NACHPRUEFUNG.md. **Abhängigkeiten:** TASK-096 geliefert (PR #286). **Arbeitsstand:** Holger hat am 10. November 2026 den Bericht der Codex Nachprüfung von TASK-096 (Empfehlung nachbessern) in den Thread gestellt und am selben Tag auf der Entscheidungskarte „Ja, alles als TASK-099“ gewählt. Branch claude/project-thread-dyiimb; Ergebnis mit DEC-129, ohne Migration. Übergabe 10. November 2026: Ergebnis im Ergebnis PR von claude/project-thread-dyiimb; R1 in `frontend/src/pages/requirementCriteria.ts` mit drei neuen Unittests und einem Browsertest bis zum gespeicherten Ergebnis, R2 im `RequirementService` und im Testersatz mit Konten ohne Feldleseregel und nur mit `owner`, N1 im Testersatz mit den drei Paritätsfällen des Berichts in `RequirementApiTest` und `requirements.test.mjs`. Nachprüfung von TASK-099 am 10. November 2026 (docs/REVIEW_TASK-099_PR-288.md, Empfehlung nachbessern, Server ohne Befund); Holger wählte „Im selben PR #288 nachbessern“. Nachbesserung im selben PR: S1 und S2, der Testersatz prüft und normalisiert die Beschreibung jetzt Schritt für Schritt wie `WikiDocument.check` und `RichText.check` (Grammatik, nur die erste Markierung je Art, Markierungen nur an Text, kein Inhalt von Blattknoten, Größe, Java `strip()` und die ASCII Leerzeichenklasse des Linkmusters) und speichert das normalisierte Dokument; M1, eigene Fälle für ein internes Bild, ein externes Bild und eine Wiki UUID in Großbuchstaben; M2, das Konto ohne Feldregel und eine zweite Person, die nur die Beschreibung ändert. Zwei neue Browsertests mit den Gegenbeispielen des Berichts.

**Umfang:** Befunde R1 und R2 und Hinweis N1 aus docs/REVIEW_TASK-096_PR-286_NACHPRUEFUNG.md:
1. R1: „Meine Eingaben behalten“ erhält die selbst gewählte Position neuer Kriterien, auch wenn die alten Kriterien ihre Reihenfolge behalten.
2. R2: `editors` nennt die anlegende Person nur, wenn der Aufrufer mindestens ein Inhaltsfeld lesen darf; Server und Testersatz gleich.
3. N1: Der Testersatz prüft die Beschreibung über die Dokumentstruktur (Knotentypen, Linkmarkierungen, tatsächliche Linkziele) statt über den serialisierten Text.

Nicht Teil: neue Regeln, Verknüpfungen (TASK-097), eigene Felder (TASK-098).

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. R1 mit Unittests für `[K1, Neu, K2]` und `[Neu, K1, K2]` und einem Browsertest bis zum gespeicherten Ergebnis.
2. R2 mit Backendtest und Testersatz für Konten ohne Feldleseregel und nur mit `owner`.
3. N1 mit den drei Paritätsfällen des Berichts gegen Server und Testersatz.
4. Bestehende Backend und Browser Tests bleiben grün.

**Nächster Schritt:** Kurze Codex Nachprüfung nach Holgers Wahl, danach fachliche Abnahme durch Holger zusammen mit TASK-096.

## TASK-097 Verknüpfungen von Anforderungen mit Tickets und Wikiseiten (A13 Teil 2)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261110-requirements-01 · **Aktualisiert:** 2026-11-10

**Bezug:** REQ-REQ-003, REQ-ACL-004; ACC-006; docs/DOMAIN_MODEL.md (Beispiel ANF-17 und DEV-42, Prüffall F-14). **Abhängigkeiten:** TASK-096 auf main. **Arbeitsstand:** Mit TASK-096 von Holger am 10. November 2026 beauftragt und mit den Vorschlägen bestätigt; das Ergebnis von TASK-096 ist seit dem 10. November 2026 auf main (PR #286), die Blockade ist damit aufgehoben. Branch claude/project-thread-dyiimb, Entscheidung DEC-127, eigene Migration nach denen von TASK-096. Übergabe 10. November 2026: Ergebnis in PR #290 von claude/project-thread-dyiimb mit Migration V0059, Dienst `RequirementLinks`, Vertrag `contracts/requirements.openapi.yaml` 0.2.0, Feld `requirements` in der Tickethistorie, Abschnitt „Verknüpfungen“ an der Anforderung (Tickets mit Revision, Hinweis und Umstellen, Zählung „n Tickets, davon m erledigt“, Wikiseiten) und Abschnitt „Anforderungen“ am Ticket mit Suche und Verknüpfen. Sechs Auslegungen stehen zur Bestätigung in DEC-127, darunter Regel 4 vor Abnahmekriterium 3 für Seiten ohne Leserecht. Belege und Grenzen im PR.

**Umfang:** Verknüpfungen einer Anforderung mit Tickets desselben Projekts mit Revisionsbezug, Hinweis und Umstellung auf neuere Revisionen, Umsetzungsstand aus den lesbaren Tickets, Verknüpfungen mit Wikiseiten desselben Projekts, das Feld „Anforderungen“ am Ticket, Einträge in beiden Historien, mit Migration, Vertrag, Oberfläche und Tests. Nicht Teil: projektübergreifende Verknüpfungen, Git Objekte (A15), Abnahme der Umsetzung je Kriterium, Beziehungen zwischen Anforderungen.

**Fachliche Regeln** (von Holger am 10. November 2026 bestätigt):
1. Eine Anforderung lässt sich mit Tickets desselben Projekts verknüpfen, jede Kombination einmal. Die Verknüpfung bezieht sich auf eine Revision, beim Anlegen auf die neueste. Bei einer neueren Revision zeigen Ticket und Anforderung „Neuere Revision n vorhanden“; erst eine ausdrückliche Umstellung bindet das Ticket an sie.
2. Verknüpfen und Lösen mit `requirement.edit` an der Anforderung und `ticket.read` am Ticket. Sichtbar nur für Personen, die beide Seiten lesen dürfen; nicht lesbare Gegenstücke werden weder gezeigt noch gezählt. Verknüpfen ändert weder Status noch Version des Tickets und steht in beiden Historien.
3. Die Anforderung zeigt ihre lesbaren Tickets mit deren Status, etwa „3 Tickets, davon 2 erledigt“. Ein erledigtes Ticket gibt nie frei und erklärt die Anforderung nie für erfüllt.
4. Eine Anforderung lässt sich mit Wikiseiten desselben Projekts verknüpfen; gezeigt wird der veröffentlichte Titel in der Oberflächensprache für Personen mit `wiki.read`, sonst „Seite nicht verfügbar“ ohne Titel. Kein Revisionsbezug.
5. Das Ticket erhält ein Feld „Anforderungen“ mit den lesbaren verknüpften Anforderungen, ihrer Revision und dem Hinweis auf neuere Revisionen; verknüpfen ist von beiden Seiten möglich.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. ANF-17 Revision 1 ist mit DEV-42 verknüpft; nach Revision 2 bleibt DEV-42 auf Revision 1 und zeigt den Hinweis, bis es ausdrücklich umgestellt wird.
2. Ein erledigtes Ticket ändert weder Freigabe noch Status der Anforderung (F-14).
3. Ohne Leserecht auf Ticket, Anforderung oder Wikiseite erscheint die Verknüpfung nicht und wird nicht gezählt.
4. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests an Anforderung und Ticket.

**Nächster Schritt:** Codex Prüfung des Ergebnisses, dann fachliche Abnahme durch Holger mit Bestätigung der Auslegungen (a) bis (f) in DEC-127; danach TASK-098.

## TASK-098 Eigene Felder für Anforderungen (A13 Teil 3)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261110-requirements-01 · **Aktualisiert:** 2026-11-10

**Bezug:** REQ-REQ-001, REQ-ACL-004; TASK-044 (Projektkonfiguration). **Abhängigkeiten:** TASK-097, auf main seit PR #290. **Arbeitsstand:** Mit TASK-096 von Holger am 10. November 2026 beauftragt und mit den Vorschlägen bestätigt; nach dem Ergebnis von TASK-097 auf main (PR #290) begonnen. Branch claude/project-thread-dyiimb, Entscheidung DEC-128. Übergabe 10. November 2026: Ergebnis im PR von claude/project-thread-dyiimb mit `requirementFields` in der Projektkonfiguration (Vertrag `contracts/projects.openapi.yaml` 0.2.0), Migration V0060 (Werte je Revision in `requirement_revision.custom`), Vertrag `contracts/requirements.openapi.yaml` 0.3.0, Feldregeln `custom.<schlüssel>` des Ressourcentyps `requirement` (`contracts/permissions.openapi.yaml` 0.5.0), dem Bereich „Eigene Felder der Anforderungen“ im Konfigurationseditor, den Feldern in Bearbeitung, Ansicht, Freigabedialog, Revision, Vergleich und Historie sowie der Beispielanforderung ANF-1 im Demoprojekt HELP. Fünf Auslegungen stehen zur Bestätigung in DEC-128. Belege und Grenzen im PR. Der Prüfbericht von Codex zu TASK-097 liegt als docs/REVIEW_TASK-097_PR-290.md bei.

**Umfang:** Bereich „Anforderungen“ in der Projektkonfiguration mit eigenen Feldern derselben Typen wie bei Tickets, mit Entwurf und Veröffentlichung der Konfiguration, Feldrechten, Historie, Vergleich, Freigabedialog und Pflichtfeldern beim Übergang nach Zur Prüfung, sowie die Demodaten, mit Migration, Vertrag, Oberfläche und Tests. Nicht Teil: weitere Feldtypen, Formulare, Automationen.

**Fachliche Regeln** (von Holger am 10. November 2026 bestätigt):
1. Eigene Felder für Anforderungen haben dieselben Typen wie bei Tickets und werden mit der Projektkonfiguration entworfen und veröffentlicht.
2. Eigene Felder zählen als inhaltlich, erscheinen in Historie, Vergleich und Freigabedialog und folgen den Feldrechten. Pflichtfelder gelten beim Übergang nach Zur Prüfung.
3. Das Vorführimage erhält im Demoprojekt die Beispielanforderung „Kunden sehen den Stand ihrer freigegebenen Supportanfragen“ mit drei Kriterien, Revision 1 freigegeben und Revision 2 im Entwurf, verknüpft mit einem Ticket auf Revision 1 und mit der Wikiseite „Serviceanfrage ergänzen“.

**Abnahmekriterien:** Vorschlag, zur Prüfung:
1. Ein veröffentlichtes eigenes Feld erscheint an Anforderungen und folgt den Feldrechten; seine Änderung nach Freigabe erzeugt eine neue Revision.
2. Ein leeres Pflichtfeld verhindert den Übergang nach Zur Prüfung mit 400 und Nennung des Felds.
3. Die Demo zeigt Freigabe, neue Revision und Revisionshinweis am Ticket.
4. Migration, Vertrag gegen Routen, Backend Tests, Browser Tests für Konfiguration und Anforderung.

**Nächster Schritt:** Codex Prüfung des Ergebnisses, dann fachliche Abnahme durch Holger mit Bestätigung der Auslegungen (a) bis (e) in DEC-128; danach die gemeinsame Abnahme von Paket A13.
