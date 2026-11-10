# Nachprüfung TASK-096 / PR #286

Datum: 10. November 2026. Unabhängige zweite KI: Codex, Sitzung `codex-20261110-requirements-rereview-01` (DEC-040).

**Empfehlung: nachbessern.** Die bisherigen Reproduktionen F1–F7 bestehen nach der Korrektur. Zwei ergänzende Randfälle bleiben offen: Die eigene Position eines neuen Kriteriums geht bei der Konfliktübernahme verloren (R1); ohne lesbare Inhaltsfelder nennt `editors` weiterhin den Ersteller (R2). Der Testersatz ist außerdem bei der Beschreibungsprüfung noch nicht vollständig servergleich (N1). Die fachliche Abnahme bleibt bei Holger.

## Prüfstand und Grundlage

Geprüft wurde der weiterhin offene Entwurfs-PR [#286](https://github.com/1uedev/task-manager/pull/286), Branch `claude/project-thread-dyiimb`, Commit `73d00f1b63dcb81d13e37c00386a5e3c2e6b66c6`. Eine erneute GitHub-Abfrage bestätigte denselben Stand. Grundlage des Nachbesserungsvergleichs ist der im Erstbericht geprüfte Commit `0aaed21dfbc76615187f422a2cd09c79566de2ec`; die Nachbesserung verändert 18 Dateien einschließlich des Erstberichts.

Zuerst gelesen wurden `AGENTS.md`, TASK-096 einschließlich „Nachbesserung 10. November 2026“, DEC-126 mit Holgers Wahl zu H3 und `docs/REVIEW_TASK-096_PR-286.md`. Ergänzend: betroffene Verträge, Implementierung, Tests und Sitzungsregeln. Geprüft wurden ausschließlich F1–F7, H1–H3 und die davon berührten Abläufe. Holgers Wahl zu H3 ist jetzt verbindlicher Maßstab: `editors` nennt nur Personen lesbarer Inhaltsänderungen. Die weiterhin als zur Bestätigung bezeichneten Auslegungen a bis e werden durch diese technische Prüfung nicht beschlossen.

## Befunde – vor Abnahme zu beheben

### R1 – P2: Die gewählte Position eines neuen Kriteriums bleibt bei der Übernahme nicht erhalten

**Ort:** `frontend/src/pages/requirementCriteria.ts:33`, `:34`, `:48`, `:50`; Aufruf `frontend/src/pages/RequirementsPage.tsx:437`.

Die Erkennung einer eigenen Reihenfolge vergleicht ausschließlich bereits nummerierte Kriterien. Neue Kriterien mit `id: null` werden dafür ausgeblendet. Solange die alten Kennungen untereinander dieselbe Reihenfolge behalten, hängt der zweite Zweig der Funktion eigene neue Kriterien nach der vollständigen fremden Liste an – auch wenn sie zuvor bewusst in die Mitte oder nach oben verschoben wurden.

**Reproduktion:**

1. Ausgangsliste: K1 „K1 alt“, K2 „K2 alt“.
2. Ein eigenes neues Kriterium „EIGEN NEU“ hinzufügen und mit dem Pfeil zwischen K1 und K2 setzen. Sichtbare eigene Reihenfolge: `[K1, Neu, K2]`.
3. Ben ändert inzwischen K2 und ergänzt K3.
4. Die eigene Speicherung erhält 412. „Meine Eingaben behalten“ wählen und erneut speichern.
5. Tatsächlich gespeichert wird `[K1, K2 VON BEN, K3 VON BEN, EIGEN NEU]`; das eigene neue Kriterium erhält K4 am Ende. Erwartet nach der vereinbarten eigenen Reihenfolge: `[K1, EIGEN NEU, K2 VON BEN, K3 VON BEN]`.

Mit echtem Produktionsfrontend und Testserver bis zum gespeicherten Ergebnis reproduziert. Ein zusätzlicher direkter Test der unveränderten `mergeCriteria`-Funktion liefert dieselbe Abweichung. Die sechs neuen Repository-Unit-Tests bestehen, enthalten aber nur einen Fall, in dem zusätzlich alte Kennungen gegeneinander umgeordnet werden; diese Konstellation erreicht den anderen Funktionszweig.

**Auswirkung:** Die Texte bleiben erhalten, aber eine ausdrücklich vorgenommene Anordnung geht verloren. F1 ist für die ursprüngliche Textverlust-Reproduktion behoben, für die im Nachprüfauftrag verlangte eigene Reihenfolge noch nicht vollständig.

**Vorschlag:** Eigene Einfügepositionen anhand der stabilen Editoridentität beziehungsweise benachbarter Kriterienkennungen in die Reihenfolgeentscheidung einbeziehen. Fremde Ergänzungen entsprechend der festgelegten Vorrangregel einordnen. Mindestens `[K1, Neu, K2]` und `[Neu, K1, K2]` mit unveränderter relativer Reihenfolge der alten Kriterien dauerhaft testen.

### R2 – P3: Das Erstellungsereignis umgeht die Inhaltsfeldprüfung von `editors`

**Ort:** `backend/src/main/java/taskmanager/requirements/internal/RequirementService.java:67`, `:541`, `:542`; gleiche Ausnahme im Testersatz `frontend/tests/browser/backend.mjs:1785`.

Die Filterung späterer Feldänderungen funktioniert. `revision_opened` zählt ebenfalls nicht mehr. `created` bleibt jedoch in `EDITING` und hat kein `field`; die Bedingung `event.field() == null` lässt es unabhängig von den lesbaren Inhaltsfeldern durch.

**Reproduktion gegen den echten Server:** Eine Anforderung mit Anna anlegen. Ein Konto nur mit `requirement.read`, ohne Feldleseregel, und ein zweites Konto mit ausschließlich lesbarem `owner` rufen sie ab. Beide erhalten `editors: [Anna]`, obwohl keines Titel, Beschreibung, Kriterien oder Priorität lesen darf. Erwartet ist nach Holgers Wahl eine leere Liste. Nach Öffnen einer zweiten Revision ausschließlich durch eine verborgene Beschreibungsänderung erhalten dieselben Konten korrekt eine leere Liste; das grenzt den Restfehler auf das Erstellungsereignis ein.

Der vorhandene Test `editorsOnlyNameChangesOfReadableFields` und die ursprüngliche Reproduktion „Beschreibung verborgen, andere Inhaltsfelder lesbar“ bestehen. Der ergänzende Test für null lesbare Inhaltsfelder schlägt fehl.

**Auswirkung:** H3 ist nicht für alle Feldrechtekombinationen umgesetzt. Dies gibt keinen zusätzlichen Feldwert preis; die Erstellerkennung steht ohnehin in `createdBy`. Beanstandet wird die falsche Ableitung der ausdrücklich eingeschränkten Menge `editors`.

**Vorschlag:** Das Erstellungsereignis nur dann für `editors` berücksichtigen, wenn mindestens ein Inhaltsfeld lesbar ist, oder dessen lesbare Inhaltsanteile ausdrücklich abbilden. Konten ohne Feldleseregel sowie nur mit `owner` in Backend- und Mock-Test aufnehmen. Die erlaubte allgemeine Erstellungsmetadaten-Ausgabe davon getrennt behandeln.

## Hinweis

### N1 – Beschreibung wird im Testersatz weiterhin über JSON-Text statt Dokumentstruktur geprüft

**Ort:** `frontend/tests/browser/backend.mjs:1839`, `:1840`, `:1841`, `:1842`.

Die ursprünglichen drei Gegenbeispiele aus H2 sind behoben: doppelte Kriteriennummern, `javascript:`-Links und unbekannte Verantwortliche erhalten nun 400 mit den passenden Codes. Verbleibende Abweichungen wurden ebenfalls auf beiden Seiten ausgeführt:

| Beschreibung | Echter Server | Testersatz |
| --- | --- | --- |
| Normaler Absatz mit Text `image` | 201, gültiger Klartext | 400 `description.image_not_allowed` |
| Normaler Absatz mit Text `wiki:ordinary text` ohne Linkmarkierung | 201, gültiger Klartext | 400 `description.page_link_not_allowed` |
| Linkziel `https://example.test/a<U+0000>b` | 400 `description.link_invalid` | 200 |

Die Zeichenfolgensuche verwechselt normalen Text mit Knotentypen beziehungsweise Links. Umgekehrt prüft der URL-Ausdruck die JSON-escapete Schreibweise des Nullzeichens statt des tatsächlichen Zeichens.

**Auswirkung:** Die Behauptung vollständiger Serverparität gilt weiterhin nicht. Browserprüfungen können zulässige Beschreibungen abweisen oder serverseitig verbotene URLs akzeptieren. Ein Durchlass am echten Server wurde nicht gefunden; er weist den ungültigen Link korrekt ab.

**Vorschlag:** Die strukturierten Knoten und Linkmarkierungen durchlaufen und die tatsächlichen Attributwerte prüfen. Die drei Fälle als gemeinsame Vertragsbeispiele gegen Server und Testersatz aufnehmen; nicht anhand beliebiger Vorkommen im serialisierten Dokument entscheiden.

## Ergebnis je ursprünglichem Punkt

| Punkt | Nachprüfung |
| --- | --- |
| **F1 – Kriterienübernahme** | Die ursprüngliche eigene Änderung an K1 erhält jetzt Bens K2-Änderung und K3-Ergänzung, auch nach erneutem Speichern. Eigener Text bei beidseitiger Änderung, eigene Entfernung, Umordnung alter Kennungen und fremde Ergänzungen werden durch die sechs erfolgreichen Unit-Tests abgedeckt. Zusätzlich im Browser geprüft: Ein selbst geändertes, inzwischen entferntes K1 bleibt mit eigenem Text als neues Kriterium erhalten und bekommt K3 statt der alten K1-Kennung. Restfehler bei der Position eigener neuer Kriterien: R1. |
| **F2 – Freigabedialog** | Behoben. Titel und Priorität stehen im Dialog. Eigene Wiederholung bestanden. Zusätzlich Titel/Priorität während des offenen Dialogs fremd ändern, 412 provozieren: Keine Freigabe, neuer Titel und „Kritisch“ erscheinen, erst die erneute ausdrückliche Bestätigung gibt Revision 1 auf der neuen Version frei. |
| **F3 – gemeinsame Vergleichsansicht** | Behoben. `WikiDiffView.tsx` übernimmt die bisherige Wiki-Darstellung einschließlich Referenzen, Formatänderungshinweis und `lang`. Wiki nutzt denselben Standardaufruf, Anforderungen verwenden `changesOnly`. Der Codeabgleich ergibt für den Wiki-Standardfall dieselbe Darstellung. Eigener Linkzielvergleich der Anforderungen zeigt beide Ziele; alle Wiki-Browserfälle bestehen. |
| **F4 – Seitenleiste** | Behoben. Eigene frühere Desktop-Reproduktion besteht jetzt mit normalem Klick. Die neuen Repository-Tests für 1100 × 800, 600 und 480 px bestehen einschließlich Sprachwechsel und Lage der Schaltfläche im Fenster. Scrollbereich und `flex-shrink` sind für Desktop gesetzt; mobile Darstellung setzt sie passend zurück. Die DE-/EN-Prüfungen bei 320 px ergeben keinen horizontalen Dokumentüberlauf. |
| **F5 – Pfeilschaltflächen** | Behoben. Beide Schaltflächen rufen `moveItem` aus `useReorder` auf; Ansage lautet im eigenen Fall „K2 steht jetzt an Stelle 1 von 2.“. Alt-Pfeil-Bedienung und Ansagen in DE/EN bestehen ebenfalls. |
| **F6 – Anlegeformular** | Behoben. Die eigene Reproduktion fragt beim Verlassen genau einmal; Ablehnen erhält Titel und Route. Der neue Repository-Browsertest bestätigt anschließend Anlegen ohne weitere Rückfrage. `navigate` verwendet dafür den bereits vorhandenen bewussten Guard-Bypass; es gibt keine Rückfrage nach erfolgreicher Anlage. |
| **F7 – Kriterienkennungen** | Behoben. Server und Mock liefern K-Präfixe; die Oberfläche fügt keines hinzu. Eigene Wiederholung ergibt „K1, K2, K3“ und „K2, K1, K3“, ohne `KK`. Das aktuelle Mockformat wurde übernommen und nicht wie vor der Korrektur künstlich erneut präfigiert. |
| **H1 – Zeilenumbrüche** | Behoben. CRLF und CR werden vor Längen-/Steuerzeichenprüfung zu LF. Gegen den echten Server Kriterien, Zurückgeben- und Verwerfen-Begründung mit gemischten Zeilenumbrüchen geprüft; gespeicherte Werte enthalten LF. Eingebettete andere Steuerzeichen bleiben abgewiesen. Ein nach Normalisierung genau 2000 Zeichen langes Kriterium wird akzeptiert. Auch die alte CRLF-Reproduktion besteht nun. |
| **H2 – Mock und Codes** | Die ursprünglichen drei Negativfälle erhalten nun 400 mit `criteria.unknown`, `description.link_invalid` beziehungsweise `owner.not_member`. `description.*`-Zuordnung und UI-Meldungskatalog sind angepasst; der echte Server nennt den Linkfehler jetzt `description.link_invalid`. Weitere Struktur-/Escape-Grenzfälle bleiben abweichend, siehe N1. |
| **H3 – lesbare Bearbeitungspersonen** | Die ursprüngliche Reproduktion und der neue Repository-API-Test bestehen: verborgene Beschreibungsänderung nennt Ben nicht, nur für vollständig Leseberechtigte; das Öffnen einer Revision allein nennt ihn ebenfalls nicht mehr. Holgers Entscheidung steht in DEC-126 und im Vertrag. Die Ausnahme bei `created` bleibt R2. |

Die darüber hinaus wiederholten eigenen API-Prüfungen für unveränderliche Freigaben, Verantwortlichenwechsel, Verwerfen/Wiederherstellen, Zähler-/Kriterienkonkurrenz, Rechteentzug, Portalkonten, Projektdelegation und sichere Beschreibung bestanden. Keine neue Verletzung dieser Abläufe festgestellt. Es erfolgte keine erneute allgemeine Erweiterungsprüfung von TASK-097/098.

## Tatsächlich ausgeführte Prüfungen

Getrennter Prüfbereich; Windows, Python 3.12.14, Java 25.0.4.1, Maven 3.9.16, Node 24.22.0/npm 11.19.1 und eigene PostgreSQL-16.15-Instanz. Die verfügbaren Python-/Maven-Programme wurden direkt mit denselben Skripten beziehungsweise dem Repository-POM und Ziel `verify` verwendet; die Unix-Startnamen `python3` und `./mvnw` waren hier nicht die tatsächlich gestarteten Programme. Ausschließlich synthetische Daten.

| Prüfung | Tatsächliches Ergebnis |
| --- | --- |
| `scripts/validate_repository.py` | Bestanden. |
| `scripts/task_board.py --check` | Bestanden, Aufgabenübersicht aktuell. |
| `python -m unittest discover -s tests -v` | 40 Tests: 39 bestanden, ein vorgesehener Windows-Skip zur Zeitzonenumstellung. |
| Backend vollständiges `verify`, PostgreSQL 16.15 | **578 Tests bestanden, 0 Fehlschläge, 0 Fehler, 0 Skips; Build und Verpackung erfolgreich.** Einschließlich des neuen `editorsOnlyNameChangesOfReadableFields` sowie der erweiterten Kriterien-/Begründungs- und Linkprüfungen. |
| `npm ci` | Bestanden; 111 Pakete installiert, 112 geprüft, keine gemeldeten Schwachstellen. |
| `npm run verify` | Bestanden: Vertragsgenerierung, Typprüfung, **97 Unit-Tests** und Produktionsbuild. Darin alle sechs neuen `mergeCriteria`-Tests. Nicht blockierende Bundlerhinweise zu Tiptap-Moduldirektiven und Paketgröße. |
| `npm run test:browser -- --test-concurrency=1` | Vollständig ausgeführt: **248 Tests, 247 bestanden, ein Fehlschlag**, keine Abbrüche/Skips. Alle Anforderungen-, Wiki- und Darstellungsauswahl-Fälle bestanden. |
| Isolierte Wiederholung des Browserfehlers | `tags.test.mjs:56`, „a tag gets a description and a color, both shown with the name“, bestand isoliert. Im Gesamtlauf war „Kunde Nord bearbeiten“ nicht innerhalb von 30 Sekunden verfügbar. Derselbe Fall trat bereits in der Erstprüfung auf; keine neue Zuordnung zur Nachbesserung festgestellt. Der Erstlauf bleibt als fehlgeschlagen dokumentiert. |
| Eigene bisherigen Browserreproduktionen | **8/8 bestanden:** Kriterienverlust, Titel/Priorität im Dialog, Linkzielvergleich, Ansage der Pfeilschaltflächen, Verlassen der Anlage, DE/EN bei 320 px, K-Präfixe der Historie und Desktop-Darstellungsauswahl. |
| Eigene ergänzende Browserfälle | **2/3 bestanden:** Wiederaufnahme eines inzwischen entfernten Kriteriums mit neuer Kennung und Dialogaktualisierung nach 412 bestanden; Einfügeposition nach Konflikt fehlgeschlagen (R1). |
| Eigener direkter `mergeCriteria`-Grenztest | Reproduzierbarer Fehlschlag zur selben Einfügeposition (R1). |
| Eigene API-/Datenbankfälle | **9/10 bestanden.** Bisherige Gegenprüfungen und zusätzliche Zeilenumbruch-/Steuerzeichen-/Strukturfälle bestanden. Ein Fehlschlag zu `editors` ohne lesbares Inhaltsfeld (R2). Echte HTTP-Schnittstelle und PostgreSQL, keine Speicherattrappe. |
| Direkte Mock-Gegenproben | Die drei ursprünglichen H2-Fälle liefern jetzt die erwarteten 400-Antworten. Drei zusätzliche Struktur-/Escape-Fälle zeigen die Abweichungen aus N1. |
| `git diff --check`, Änderungs-/Berichtskontrolle | Bestanden. Produktcode, generierte Vertragsdateien, TODO.md und DONE.md unverändert; hinzugefügt wird ausschließlich dieser Nachprüfbericht. |

Die abgefragte CI des geprüften Commits war ebenfalls erfolgreich: [Backend](https://github.com/1uedev/task-manager/actions/runs/42853766353), [Frontend](https://github.com/1uedev/task-manager/actions/runs/42853766827), [Repository checks](https://github.com/1uedev/task-manager/actions/runs/42853766407), [Technical qualification](https://github.com/1uedev/task-manager/actions/runs/42853766628), [Image](https://github.com/1uedev/task-manager/actions/runs/42853766394). Diese abgefragten Ergebnisse sind keine weiteren eigenen lokalen Ausführungen.

**Nicht lokal ausgeführt:** Docker-/Compose-Lifecyclecheck, eigenständiger Unix-Lifecycle-Aufruf und vollständiger Barrierefreiheitstest. Die vorhandenen Datenbank-/Anwendungstests liefen im vollständigen Backendlauf. Keine zweite komplette Browsersuite nach dem isolierten Tag-Erfolg. Browsernachweise verwenden den echten Produktionsbuild mit dem Testserver; die API-/Sperrnachweise verwenden den echten Backendserver und PostgreSQL. Die Zusatzprüfungen wurden außerhalb des Produktcheckouts angelegt.

Rohprotokolle und Screenshots liegen im benachbarten Prüfbereich `review-tools/requirements096-rereview-01`, unter anderem `repository.log`, `backend-verify.log`, `frontend-verify.log`, `browser-all.log`, `browser-tag-isolated.log`, `browser-repeat.log`, `browser-extra.log`, `criteria-extra.log`, `api-probes.log` und `mock-probes.log`. Es wurden keine Zusatztests oder sonstigen Codeänderungen in den Produktcheckout aufgenommen.
