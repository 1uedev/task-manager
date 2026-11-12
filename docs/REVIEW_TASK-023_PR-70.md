# Getrennte technische Prüfung von TASK-023 / PR #70

18. Oktober 2026 · Prüfsitzung `codex-20261018-frontend-review-01` · DEC-040

Geprüft wurde [PR #70](https://github.com/1uedev/task-manager/pull/70), zunächst Head `0993925f1d1eab9b4449b5b143af3288891a3546` gegen main `71291c94ea54c80128c55c71fbb1f06019872913`. Während der Prüfung wurde PR #70 mit Abschluss-Head `518814295b8e14a78c6c2f8ee3031f362dd690ea` als `fad1648b69cd57f0cdc99d32f30fd5c4f0b85777` integriert. Der eigene Git-Vergleich bestätigt: `frontend/` und `.github/workflows/frontend.yml` sind zwischen Erstprüfstand, Abschluss-Head und integriertem Stand identisch. Die Befunde gelten daher auch für diesen main-Stand.

Eigener Arbeitsbereich und Branch; keine Änderungen an Frontendquellen, PR #70, TODO.md oder DONE.md. Die Dokumentation erfolgt als separater Bericht. Die Reservierung von TASK-025 bleibt bestehen.

Grundlage: AGENTS.md, README.md, TODO.md mit den Kriterien von TASK-023, DONE.md, CLAUDE.md, docs/AI_WORKFLOW.md, relevante Anforderungen und Entscheidungen einschließlich DEC-040/DEC-056, Architektur-/Vertragsentwürfe, DEVELOPMENT_START.md und TECHNICAL_BASELINE.md. Der begrenzte Auftrag umfasst eine synthetische Grundfläche, keine Anmeldung oder Fachfunktion. Die Prüfung erteilt keine fachliche Abnahme und hebt keine Aufgabenblockierung auf.

## Ergebnis

Drei mittlere Befunde an Eingabe-/Brandingvalidierung und ein niedriger Befund an der Portabilität der Testwerkzeuge. Empfohlen ist die Korrektur von F1 bis F3 vor Verwendung der betreffenden Bausteine in Fachfunktionen. F4 betrifft den dokumentierten lokalen Prüfweg unter Windows. Die grünen CI-Läufe und die erfolgreichen vorhandenen Browserfälle decken diese Gegenbeispiele nicht ab.

## F1 · P2 / mittel · Ungültige Zahleneingabe wird still zu einem anderen Wert

**Fundstelle:** [frontend/src/i18n/format.ts:20–29](https://github.com/1uedev/task-manager/blob/518814295b8e14a78c6c2f8ee3031f362dd690ea/frontend/src/i18n/format.ts#L20-L29), insbesondere das Entfernen sämtlicher innerer Leerzeichen in Zeile 24.

**Reproduktion:** `parseDecimal('1 5', 'de')` und dieselbe Eingabe mit `en` liefern jeweils `15`. `validateSample({ label: 'Synthetic review', hours: '1 5' }, 'de')` meldet Erfolg mit 15 Stunden. Im gebauten Frontend mit Chromium reproduziert: Im Beispielformular Bezeichnung ausfüllen, als Aufwand `1 5` eingeben und absenden. Deutsch bestätigt „15 Stunden“, Englisch „15 hours“, ohne Validierungsfehler.

**Auswirkung:** Anders als in der dokumentierten Regel „mehrdeutige Eingaben abweisen“ entfernt der Parser ungültige Zeichen vor der Strukturprüfung und fügt getrennte Ziffern zusammen. Im aktuellen Stand wird nichts gespeichert; bei Wiederverwendung für Fachformulare wäre eine unbemerkte Wertänderung möglich.

**Vorschlag:** Nur äußere Leerzeichen abschneiden. Innere Trennzeichen nur dann und erst nach Prüfung der vollständigen Gruppierung akzeptieren, wenn sie zur gewählten Zahlenkonvention gehören. Gegenproben für normale, geschützte und schmale geschützte Leerzeichen zwischen Ziffern in DE/EN sowie zulässige Gruppierung anderer Sprachen ergänzen. Erwartung für `1 5`: Fehler, keine synthetische Übernahme als 15.

## F2 · P2 / mittel · Logo-Prüfung lässt externe HTTP-Adresse als lokalen Pfad durch

**Fundstelle:** [frontend/src/brand/brand.ts:43–49](https://github.com/1uedev/task-manager/blob/518814295b8e14a78c6c2f8ee3031f362dd690ea/frontend/src/brand/brand.ts#L43-L49).

**Reproduktion:** Der Wert `/\example.invalid/logo.svg` enthält nach dem ersten Schrägstrich einen Rückwärtsschrägstrich. `parseBrand({ ...defaultBrand, logoUrl: dieserWert }).ok` ergibt `true`, weil der Rohtext mit `/`, aber nicht mit `//` beginnt. Die unabhängig in Chromium geprüfte URL-Auflösung bei lokaler HTTP-Herkunft ergibt jedoch `http://example.invalid/logo.svg`.

**Auswirkung:** Der Wert wird als Logo unverändert an `<img src>` weitergegeben und umgeht damit die dokumentierte Grenze „gleiche Herkunft oder HTTPS“. Bei HTTPS-Herkunft bleibt es ein externer Zielwechsel entgegen der Einordnung als lokaler Pfad. Es wird weder Skriptausführung noch ein bereits erfolgter Abfluss echter Daten behauptet. Die Gegenprobe hat nur die URL-Auflösung geprüft und keine externe Logo-Anfrage ausgelöst.

**Vorschlag:** URL mit einer vertrauenswürdigen Basis parsen und das normalisierte Ergebnis anhand von Protokoll und Herkunft prüfen. Rückwärtsschrägstriche und Steuerzeichen vor Annahme lokaler Pfade abweisen; rohe Präfixvergleiche reichen nicht. Tests für `/\host/…`, weitere Schrägstrichvarianten und gültige lokale/HTTPS-Adressen ergänzen.

## F3 · P2 / mittel · Gültiges Branding kann Hinweise und Pflichtangaben unleserlich machen

**Fundstellen:** [frontend/src/brand/brand.ts:74–79](https://github.com/1uedev/task-manager/blob/518814295b8e14a78c6c2f8ee3031f362dd690ea/frontend/src/brand/brand.ts#L74-L79) und [frontend/src/styles.css:89–90](https://github.com/1uedev/task-manager/blob/518814295b8e14a78c6c2f8ee3031f362dd690ea/frontend/src/styles.css#L89-L90), zusätzlich der feste `--muted`-Wert am Dateianfang.

**Reproduktion:** Folgende vollständige Farbkombination wird von `parseBrand` akzeptiert: `primary=#ffffff`, `onPrimary=#000000`, `background=#000000`, `text=#ffffff`. Beide geprüften Farbpaare haben hohen Kontrast. Hinweise, Pflichtangaben und Fußtext verwenden jedoch weiterhin `--muted: #4a4a4a` auf dem nun schwarzen Hintergrund. Der mit der Projekteigenen Funktion berechnete Kontrast beträgt nur **2,37:1**.

Zusätzlich im Browser geprüft: Die von `brandToCssVariables` erzeugten Variablen dieses akzeptierten Auftritts wurden auf den gebauten Formularbildschirm angewandt. Die berechneten Farben waren `rgb(74, 74, 74)` für den Hinweis und `rgb(0, 0, 0)` für den Hintergrund. Der lokale Screenshot wurde visuell geprüft; Hinweise und Pflichtfeldkennzeichnungen sind erheblich schlechter lesbar. Die Anwendung der Variablen erfolgte als gezielte Prüfprobe, nicht über eine bereits vorhandene Branding-Verwaltungsoberfläche.

**Auswirkung:** Die Prüfung akzeptiert einen Auftritt als lesbar, obwohl sichtbare Formularkomponenten die projektspezifisch verwendete Kontrastgrenze 4,5 nicht erreichen. Das ist keine Aussage über eine vollständige Prüfung nach einem bereits beschlossenen Barrierefreiheitsstandard; dieser bleibt laut REQ-UX-001 offen.

**Vorschlag:** Sekundäre Texte, Links, Fehler und Fokusfarben als Teil einer zusammenhängenden Palette ableiten bzw. ihre tatsächlich verwendeten Kombinationen prüfen. Alternativ zunächst nur solche Hintergrundfarben zulassen, mit denen die festen Komponentenfarben lesbar bleiben. Mindestens ein gültiges dunkles Branding im Browser prüfen.

## F4 · P3 / niedrig · Dokumentierte Prüfungen funktionieren unter Windows nicht unverändert

**Fundstellen:** [frontend/tests/static.test.ts:6](https://github.com/1uedev/task-manager/blob/518814295b8e14a78c6c2f8ee3031f362dd690ea/frontend/tests/static.test.ts#L6), dort außerdem die Pfadvergleiche in Zeilen 22 und 47–48; [frontend/tests/browser/support.mjs:6](https://github.com/1uedev/task-manager/blob/518814295b8e14a78c6c2f8ee3031f362dd690ea/frontend/tests/browser/support.mjs#L6).

**Reproduktion:** Mit exakt Node 24.22.0 und npm 11.19.1 lief die dokumentierte Installation erfolgreich. `npm run verify` scheitert danach beim Laden von `static.test.ts` mit `ENOENT`: `new URL(...).pathname` ist hier kein gültiger nativer Windows-Dateipfad. 48 einzelne Unit-Tests bestanden; die statische Testdatei wurde als fehlgeschlagene Testdatei gezählt, ihre fünf Fälle konnten nicht laufen. Die unveränderte Browser-Serverhilfe beantwortet `/` trotz vorhandenem Build mit HTTP 404 aus demselben Grund.

**Vorschlag:** `fileURLToPath` für Dateisystemzugriffe verwenden und Pfade vor plattformunabhängigen Vergleichen normalisieren bzw. mit den passenden Segmenten vergleichen. Auch Leerzeichen im Arbeitsverzeichnis berücksichtigen. Falls lokale Prüfungen ausdrücklich auf Linux begrenzt werden sollen, diese Einschränkung in den Voraussetzungen dokumentieren. Die vorhandene CI auf Ubuntu bleibt durch diesen Befund unbestritten.

## Geprüfte positive Eigenschaften und Grenzen

- Sprachkataloge und Platzhalter stimmen in den bestandenen Tests überein; Sprache und Darstellungsformate ändern sich, Austauschwerte bleiben fest. Eingabefehler und Zustandsmeldungen werden ebenfalls übersetzt.
- Der Client wird aus der synthetischen OpenAPI-Datei erzeugt; eigene Typerzeugung erzeugte keine Git-Abweichung. TypeScript-Prüfung ist streng und erfolgreich. `ETag` und Fachversion bleiben getrennt, `If-Match` wird als Header übertragen.
- Fehler werden über Status und stabile Codes klassifiziert. Servertexte werden nicht angezeigt; fehlerhafte Werte für Code und Referenz werden begrenzt. Ein nicht als JSON geliefertes Fehlerdokument wird im bestandenen Clienttest nach Status behandelt. Die Laufzeitprüfung der Erfolgsdaten bleibt eine begrenzte Formprüfung, kein vollständiger OpenAPI-Schemavalidator.
- Langsame/abgebrochene Antworten überschreiben im bestandenen Browserfall keine neuere Auswahl. Fehlender Zugriff, nicht sichtbar/nicht vorhanden, Konflikt und fehlende Vorbedingung besitzen passende Zustände; die synthetische Ausführung wird sichtbar erklärt.
- Sprunglink, Landmarken, Fokusführung, Sprachumschaltung mit Tastatur, Formularzusammenfassung, Fehlerverweise und 320-Pixel-Ansichten bestanden die vorhandenen Browserfälle. Geprüft wurden keine Screenreader und keine vollständige automatische Barrierefreiheitsanalyse.
- Browser-Speicherung dient im vorhandenen Quellstand der Sprachwahl. Keine Sitzungstokens, Anmeldung oder echten Fachzugriffe sind vorhanden. Das bekannte Q5 zur späteren Produkt-Problemdefinition bleibt eine dokumentierte Folgeaufgabe; das offene synthetische Vertragsproblem wird nicht als neue Produktanbindung ausgegeben.

## Tatsächlich selbst ausgeführte Prüfungen

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Portable Laufzeit | Offizielles Node-24.22.0-Windows-Archiv geladen und gegen SHASUMS256.txt verglichen; SHA-256 `f9683a19877e12a9b167305c8db71040d812876359de21f0d2e97c3f321f80c3`. Node 24.22.0 und npm 11.19.1 bestätigt; keine globale Umstellung. |
| Installation | `npm ci --ignore-scripts` erfolgreich mit Lockdatei; keine Installationsskripte ausgeführt. |
| Erzeugung / Typprüfung | Beide Schritte von `npm run verify` erfolgreich; generierte API-Datei unverändert. |
| Originale Unit-Suite | 48 Tests bestanden, eine Testdatei wegen F4 fehlgeschlagen; vollständiger Verify-Befehl daher **nicht erfolgreich**. |
| Produktionsbuild | Anschließend separat `npm run build`: erfolgreich. |
| Originale Browserhilfe | Eigene HTTP-Probe auf den lokalen Server: 404 statt Anwendung, siehe F4. |
| Browser-Testfälle | Alle **19** originalen Testfälle bestanden mit Chrome **155.0.8059.82**, DE/EN und Tastatur, aus einer externen Kopie des Testharness. Nur Dateipfad-/Paketimport-Anpassung für Windows in der Serverhilfe; Testfälle und Produktcode unverändert. Kein Erfolg der unveränderten Browserhilfe behauptet. Ein zunächst in der Sandbox hängender Browserlauf wurde abgebrochen; der erfolgreiche Lauf erfolgte außerhalb der Sandbox. |
| Eigene Gegenproben | F1 in reiner Parser-/Formularvalidierung und im gebauten Formular für DE/EN bestätigt; F2 mit Validator und Chromium-URL-Auflösung bestätigt; F3 mit akzeptierter Palette, Kontrastrechnung, berechneten Browserfarben und lokaler Screenshotprüfung bestätigt. |
| Repository-Prüfungen | Validator, erzeugte Aufgabenübersicht und 25 Regressionstests mit `PYTHONUTF8=1` erfolgreich; Whitespace-Prüfung erfolgreich. |
| Änderungsgrenzen | Prüfarbeitsbereich vor Berichterstellung sauber; keine Änderung an geprüften Produktquellen, Backend, gemeinsamen Verträgen oder DONE.md. Abschluss-Frontend samt Workflow mit dem ursprünglichen Prüfstand und main verglichen: identisch. |

## Unabhängig gelesene CI-Nachweise

Für Head `0993925` wurde das vollständige Protokoll von [Frontend-Lauf 39130192036](https://github.com/1uedev/task-manager/actions/runs/39130192036), Job `117683605212`, gelesen. Es dokumentiert Testmerge `3ec93fa` aus genau diesem Head und main `71291c9`: Node 24.22.0, npm 11.19.1, Google Chrome 155.0.8059.39, **53/53 Unit-Tests und 19/19 Browserprüfungen**, jeweils keine übersprungenen Tests, sowie erfolgreichen Build und driftfreie Erzeugung. Das ist CI-Evidenz unter Ubuntu, kein eigener lokaler Lauf dieser unveränderten Kombination.

Für den abschließenden Head `5188142` melden [Frontend 39132416859](https://github.com/1uedev/task-manager/actions/runs/39132416859), [Repository checks 39132416868](https://github.com/1uedev/task-manager/actions/runs/39132416868), Backend und Qualifikation Erfolg. Deren vollständige Jobprotokolle wurden nicht zusätzlich gelesen, da Frontend und Workflow zum bereits geprüften Stand unverändert sind. CI-Artefaktarchive wurden nicht heruntergeladen.

## Übergabe

F1 bis F4 sind konkrete, noch offene Befunde dieses Prüfstands. Der Merge von PR #70 ist keine Widerlegung der Gegenproben und wird nicht als fachliche Abnahme interpretiert. Korrekturen und deren Zuständigkeit koordiniert als Folgearbeit festlegen; danach gezielt gegenprüfen. Die Statusführung in TODO.md und DONE.md wird durch diesen Bericht nicht geändert. Die eigene Reservierung für TASK-025 wird weder aufgegeben noch für diese Prüfung verwendet.
