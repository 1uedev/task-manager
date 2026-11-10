# Nachprüfung TASK-099 / PR #288

Datum: 10. November 2026. Unabhängige zweite KI: Codex, Sitzung `codex-20261110-requirements-rereview-02` (DEC-040).

**Empfehlung: nachbessern.** R1 und R2 sind in den wiederholten Reproduktionen behoben. N1 behebt die drei bisherigen Gegenbeispiele und verwendet jetzt die Dokumentstruktur. Die Prüfung des Testersatzes entspricht aber weiterhin nicht vollständig `WikiDocument.href` und `RichText`: Zusätzliche Gegenproben ergeben unterschiedliche Annahmeentscheidungen sowie zwei unterschiedliche Fehlerkennungen. S1 und S2 betreffen ausschließlich diesen ausdrücklich beauftragten Testersatz; ein entsprechender Durchlass am echten Server wurde nicht festgestellt. Die fachliche Abnahme bleibt bei Holger.

## Prüfstand und Umfang

Geprüft wurde [PR #288](https://github.com/1uedev/task-manager/pull/288), Branch `claude/project-thread-dyiimb`, Commit `46b7530fe62db47ea1518bb99a9620537d1e7121`, gegen main `4d3c978a5b4a209ff373009cca6d742b824622fc`. Eine erneute GitHub-Abfrage bestätigte denselben offenen Entwurfs-PR und Commit. Gelesen wurden `AGENTS.md`, TASK-099 in `TODO.md`, DEC-129, die einschlägigen Teile von DEC-126 und die Grundlage [Nachprüfung TASK-096 / PR #286](REVIEW_TASK-096_PR-286_NACHPRUEFUNG.md). Sitzungsregeln und betroffene Implementierungen, Verträge und Tests wurden ergänzend herangezogen.

Die Prüfung beschränkt sich auf R1, R2 und N1. Keine erneute allgemeine Prüfung von TASK-096, keine Prüfung von TASK-097/098 und keine Entscheidung über die Auslegungen a bis e in DEC-126. Die vollständigen angeforderten Tests sind Regressionsevidenz und erweitern diesen fachlichen Prüfumfang nicht.

## Befunde – vor Abnahme zu beheben

### S1 – P3: Die Linkprüfung des Testersatzes hat andere Unicode-Regeln als der Server

**Ort:** `frontend/tests/browser/backend.mjs:1851`, `:1854`. Vergleich: `backend/src/main/java/taskmanager/wiki/internal/WikiDocument.java:38`, `:326`, `:330`, `:339`.

JavaScript `trim()` und `\s` bilden die Regeln von Java `strip()` und dem dort verwendeten regulären Ausdruck nicht gleich ab. Vier Gegenbeispiele wurden mit identischem Dokument über die echte Anforderungs-HTTP-Schnittstelle, direkt über `RichText.check` und im Testersatz ausgeführt. Jedes Dokument besteht aus einem Absatz mit dem Text „Link“ und einer Linkmarkierung:

| Tatsächliches `href` | Echter Server | Testersatz |
| --- | --- | --- |
| `<U+00A0>https://example.test` | 400 `description.link_invalid` | 201 |
| `<U+FEFF>https://example.test` | 400 `description.link_invalid` | 201 |
| `https://example.test/a<U+00A0>b` | 201 | 400 `description.link_invalid` |
| `https://example.test/a<U+2003>b` | 201 | 400 `description.link_invalid` |

Die Platzhalter bezeichnen jeweils das wirkliche Unicode-Zeichen im Attribut, keine wörtlichen Zeichenfolgen. Die bisherigen C0-/C1-Prüfungen einschließlich Nullzeichen funktionieren; der Unterschied liegt hier in Normalisierung und Leerzeichenklassen.

**Auswirkung:** Browserprüfungen können serverseitig unzulässige Linkziele als gespeichert behandeln und zulässige Eingaben ablehnen. Die in DEC-129 zugesagte Parität tatsächlicher Linkziele ist damit unvollständig. Der echte Server bleibt der oben dokumentierte Maßstab; dies ist kein Befund gegen dessen URL-Regel.

**Vorschlag:** Die vorhandene Java-Normalisierung und deren Zeichenklassen im Testersatz ausdrücklich nachbilden. Die vier Dokumente als gemeinsame Vertragsbeispiele gegen Server und Testersatz prüfen. Eine Änderung der produktiven Linkregeln wäre eine eigene Entscheidung und ist für diese Korrektur nicht erforderlich.

### S2 – P3: Der Strukturvergleich überspringt Normalisierung und wesentliche RichText-Prüfungen

**Ort:** `frontend/tests/browser/backend.mjs:1845`, `:1848`, `:1856`; nachgelagerter Fehler bei `:1646`. Vergleich: `backend/src/main/java/taskmanager/wiki/internal/WikiDocument.java:85`, `:88`, `:142`, `:278`, `:281`, `:292`, `:305`; `backend/src/main/java/taskmanager/wiki/RichText.java:38`, `:44`, `:47`.

Der neue rekursive Durchlauf prüft jedes gefundene `marks`-/`content`-Attribut unmittelbar. Der Server prüft dagegen zuerst die zulässige Dokumentgrammatik und normalisiert sie; erst danach verbietet `RichText` die tatsächlich verbliebenen Bilder und Wikiverweise. Zehn weitere identische HTTP-/Fassaden-/Mock-Gegenproben zeigen diese Abweichung:

| Dokumentfall | Echter Server | Testersatz |
| --- | --- | --- |
| Text mit zwei `link`-Markierungen: zuerst HTTPS, danach `javascript:alert(1)` | 201; nur die erste Linkmarkierung bleibt | 400 `description.link_invalid` |
| Dasselbe mit gültigem `wiki:`-Ziel als zweiter Linkmarkierung | 201; zweite Linkmarkierung entfällt | 400 `description.page_link_not_allowed` |
| `javascript:`-Linkmarkierung an `hardBreak` | 201; Markierung entfällt | 400 `description.link_invalid` |
| `javascript:`-Linkmarkierung am Absatz | 201; Markierung entfällt | 400 `description.link_invalid` |
| Bild als `content` eines `horizontalRule` | 201; Inhalt entfällt | 400 `description.image_not_allowed` |
| Unbekannter Block `iframe` im Dokument | 400 `description.unsupported` | 201 |
| Beschreibung ist `[]` statt eines Dokumentobjekts | 400 `description.invalid` | 201 |
| Textknoten mit `marks: "invalid"` | 400 `description.invalid` | JavaScript-Ausnahme statt einer geregelten Antwort |
| Linkmarkierung innerhalb eines `codeBlock` | 400 `description.unsupported` | 201 |
| Absatz mit 1.048.576 ASCII-Textzeichen; normalisiertes JSON ist größer als 1 MiB | 400 `description.too_large` | 201 |

**Auswirkung:** Auch nach dem Wechsel zur Strukturprüfung können Browsertests andere Ergebnisse als die Produktion liefern oder an ungültigen Eingaben selbst abstürzen. Ein bloßer rekursiver Suchlauf ist noch kein gleichwertiger Ersatz für den produktiven Prüf- und Normalisierungspfad. Die akzeptierten serverseitigen Beispiele behalten keine gefährlichen Markierungen oder verbotenen Bilder; diese werden dort verworfen.

**Vorschlag:** Im Testersatz zuerst die für Anforderungen zulässigen Knoten, Markierungen, Typen und Größen nach dem vorhandenen Serverformat prüfen und normalisieren. Danach Bilder und Wikiverweise auf dem normalisierten Dokument bestimmen. Doppelte Markierungen, ignorierte Attribute sowie ungültige Struktur und Größe mit gemeinsamen Beispielen absichern. Produktcode und Vertrag brauchen dafür keine neue Regel.

## Hinweise

### M1 – Der neue Paritätstest schreibt für ein externes Bild die falsche Fehlerkennung fest

**Ort:** `frontend/tests/browser/requirements.test.mjs:351`, `:352`; Ursache `frontend/tests/browser/backend.mjs:1847`. Vergleich: `backend/src/main/java/taskmanager/wiki/internal/WikiDocument.java:239`, `backend/src/main/java/taskmanager/wiki/RichText.java:38`.

Das neue Beispiel verwendet `src: "/x"` und erwartet `description.image_not_allowed`. Der echte Server weist dieses Bild bereits vor der RichText-Einschränkung mit `description.image_external` ab. Ein syntaktisch gültiges internes Bild erhält dagegen auf beiden Seiten `description.image_not_allowed`.

Eine zweite reine Codeabweichung: `wiki:ABCDEFAB-ABCD-4000-8000-ABCDEFABCDEF` liefert beim Server `description.link_invalid`, im Testersatz wegen des zusätzlichen `i` am UUID-Ausdruck (`backend.mjs:1842`) `description.page_link_not_allowed`. Der Server erwartet bei UUID-Text Kleinbuchstaben (`WikiDocument.java:36`, `:333`). Beide Seiten antworten hier jeweils mit 400.

**Auswirkung:** Der als servergleich bezeichnete Test bestätigt eine abweichende Fehlerklassifikation; Meldungszuordnungen werden dadurch nicht zuverlässig abgesichert.

**Vorschlag:** Für `image_not_allowed` einen gültigen internen Bildpfad verwenden. Den externen Bildpfad und die großgeschriebene Wiki-UUID separat mit den tatsächlichen Servercodes prüfen und den Testersatz angleichen.

### M2 – Im dauerhaften Mock-Test fehlt das ausdrücklich genannte Konto ohne Feldleseregel

**Ort:** `frontend/tests/browser/requirements.test.mjs:354`, `:356`, `:358`; Abnahmekriterium `TODO.md:108`.

Der neue Repositorytest prüft für R2 nur die Erstellerin und Ben mit lesbarem `owner`. Das Konto ohne Feldleseregel wird im Backendtest geprüft, im dauerhaften Mock-Test fehlt es. Die eigene zusätzliche Gegenprobe bestätigt das richtige Mock-Verhalten auch ohne Feldleseregel; es besteht hier kein weiterer Funktionsfehler.

**Auswirkung:** Ein ausdrücklich zugesagter Grenzfall ist im Testersatz noch nicht dauerhaft gegen Regression gesichert.

**Vorschlag:** Vor dem Erteilen von `READ:owner` auch `editors: []` für `requirement.read` ohne Feldregel prüfen. Ergänzend eine zweite Person nur die Beschreibung ändern lassen und ihre Sichtbarkeit für vollständige und eingeschränkte Leser vergleichen.

## Ergebnis der verlangten Reproduktionen

| Punkt | Ergebnis |
| --- | --- |
| **R1: `[K1, Neu, K2]`** | Behoben. Die ursprüngliche Reproduktion mit Bens geändertem K2 und zusätzlichem K3 wurde erneut bis zur Speicherung und anschließend nach Neuladen geprüft: Texte `[K1 alt, EIGEN NEU, K2 VON BEN, K3 VON BEN]`, Kennungen `[1, 4, 2, 3]`. |
| **R1: vorne, mehrere neue, entfernter Vorgänger** | Bestanden: `[Neu, K1, K2]`, zwei neue Kriterien hintereinander, zwei neue hinter unverändertem und inzwischen entferntem K2, entfernter erster Vorgänger. Der nächste verbliebene Vorgänger bestimmt die Position; ohne ihn bleiben die neuen vorn. |
| **R1: eigene Umordnung** | Unverändert richtig: Eigene Reihenfolge gewinnt, fremde Ergänzungen folgen. Ebenfalls geprüft: fremde Umordnung ohne eigene Umordnung und ein selbst geändertes, inzwischen entferntes Kriterium mit neuen Kriterien dahinter. Eigener Text bleibt mit neuer Kennung erhalten. |
| **R1: Tests** | Alle neun Repository-Unit-Tests der Datei bestehen im Frontendlauf. Zehn eigene direkte Fälle bestanden. Acht eigene Browserfälle bestanden jeweils nach 412, „Meine Eingaben behalten“, Speichern und Neuladen. Der neue Repository-Browsertest bis zum gespeicherten Ergebnis besteht ebenfalls. |
| **R2: echter Server** | Behoben. `editorsOnlyNameChangesOfReadableFields` prüft vollständige Leser, verborgene Beschreibungsänderung, keine Feldleseregel, nur `owner` und die neue Revision. Eine eigene erneute HTTP-Reproduktion bestätigt insbesondere `owner-only=[]` und `bare=[]`, für vollständige Leser beide Inhaltsautoren und nach der Revision nur den tatsächlichen Inhaltsautor. |
| **R2: Testersatz** | Eigene Gegenproben: ohne Feldleseregel `[]`, nur `owner` ebenfalls `[]`, mit allen Inhaltsfeldern die Erstellerin. Der Repository-Browsertest besteht; ergänzende Testabdeckung siehe M2. |
| **N1: bisheriger Fall „image“ als Klartext** | Echter Server und Testersatz jeweils 201. |
| **N1: bisheriger Fall „wiki:ordinary text“ als Klartext** | Echter Server und Testersatz jeweils 201. |
| **N1: bisheriger Fall Nullzeichen im HTTPS-Linkziel** | Echter Server und Testersatz jeweils 400 `description.link_invalid`. |
| **N1: ergänzende Parität** | 96 identische Beschreibungsdokumente ausgeführt. HTTP-Schnittstelle und direkte produktive RichText-Fassade stimmen bei Status und Fehlercodes in allen 96 Fällen überein. Zum Testersatz stimmen 80 Fälle überein; 14 unterscheiden sich im Annahme-/Fehlerverhalten (S1/S2), zwei nur im Fehlercode (M1). Darunter 65 einzeln geprüfte C0-/DEL-/C1-Zeichen, die auf allen Seiten korrekt abgewiesen werden. |

## Tatsächlich ausgeführte Prüfungen

Getrennter Prüfbereich mit ausschließlich synthetischen Daten. Windows, Python 3.12.14, Java 25.0.4.1, Maven 3.9.16, Node 24.22.0/npm 11.19.1 und eigene PostgreSQL-16.15-Instanz. Die Python-Skripte liefen über die verfügbare Python-Programmdatei. Das Maven-Ziel `verify` lief mit dem Repository-POM über die vorhandene Maven-Installation und den lokalen Abhängigkeitscache; die Unix-Startnamen `python3` und `./mvnw` waren hier nicht die verwendeten Programme.

| Prüfung | Tatsächliches Ergebnis |
| --- | --- |
| `scripts/validate_repository.py` | Bestanden. |
| `scripts/task_board.py --check` | Bestanden, Aufgabenübersicht aktuell. |
| `python -m unittest discover -s tests -v` | Vollständiger abschließender Lauf: 40 Tests, 39 bestanden und ein vorgesehener Windows-Skip zur Zeitzonenumstellung. Ein erster eingeschränkter Lauf scheiterte an temporären Verzeichnisrechten; der nächste an der Windows-Standardkodierung. Mit eigenem temporärem Verzeichnis und `PYTHONUTF8=1` erfolgreich wiederholt. |
| Backend vollständiges `verify`, PostgreSQL 16.15 | Bestanden: 578 Tests, 0 Fehlschläge, 0 Fehler, 0 Skips; Build und Verpackung erfolgreich. Darin auch `editorsOnlyNameChangesOfReadableFields` und `valuesAndTheDescriptionAreChecked`. Laufzeit 17:04 Minuten. |
| `npm ci` | Bestanden: 111 Pakete installiert, 112 geprüft, keine gemeldeten Schwachstellen. |
| `npm run verify` | Bestanden: Vertragsgenerierung, Typprüfung, 100 Unit-Tests und Produktionsbuild. Nicht blockierende Bundlerhinweise zu Tiptap-Moduldirektiven und Paketgröße. |
| `npm run test:browser -- --test-concurrency=1` | Vollständig bestanden: 250 Tests, 0 Fehlschläge, 0 Abbrüche, 0 Skips. |
| Eigene Kriterienproben | 10/10 direkte Fälle und 8/8 Browserfälle bis zur Speicherung einschließlich Neuladen bestanden. |
| Eigene API-Gegenproben | Zwei zusätzliche Testmethoden erfolgreich: erneute R2-HTTP-Reproduktion und 96 Beschreibungen über den echten Server. Dazu dieselben 96 Beschreibungen direkt durch die produktive `RichText.check`-Fassade und im Testersatz. Die Differenzen aus S1/S2/M1 sind tatsächliche Vergleichsergebnisse, keine fehlgeschlagenen Behauptungen des Repositorytests. |
| `git diff --check`, Dateikontrolle sowie erneute Repository-/Aufgabenprüfung nach Erstellung des Berichts | Bestanden. Gegenüber dem geprüften Commit ist ausschließlich dieser neue Bericht vorhanden; keine Änderungen an verfolgten Dateien. |

**Nicht ausgeführt:** wörtlicher Unix-Aufruf `./mvnw verify`, Docker-/Compose-Lifecycleprüfung und zusätzliche Prüfungen außerhalb der drei beauftragten Punkte. Es wurde kein zweiter vollständiger Browserlauf benötigt. Die Browserprüfungen verwenden den echten Produktionsbuild mit dem Repository-Testersatz; die Backend-/API-Prüfungen verwenden den echten Server und PostgreSQL.

Zusatzproben und Rohprotokolle liegen außerhalb des Produktcheckouts im benachbarten Prüfbereich `review-tools/requirements099-review-02`, insbesondere `repository.log`, `python-tests-utf8.log`, `backend-verify.log`, `npm-ci.log`, `frontend-verify.log`, `browser-all.log`, `criteria-probes.log`, `browser-probes.log`, `api-probes.log`, `server-results.json`, `http-results.json`, `mock-results.json` und `parity-comparison.json`. Auch die für die Zusatzprüfungen verwendeten Testkopien liegen ausschließlich dort. Im Produktcheckout wird nur dieser Bericht hinzugefügt; Produktcode, Repositorytests, Verträge und Aufgabenstatus bleiben unverändert.
