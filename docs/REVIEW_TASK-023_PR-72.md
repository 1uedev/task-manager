# Unabhängige Nachprüfung TASK-023 / PR #72

Stand: 20. Oktober 2026. Prüfer: Codex, Sitzung `codex-20261020-frontend-followup-01`, eigene Arbeitsbereiche und Berichtszweig `review/task-023-pr72-codex`.

**Ergebnis: F1 bis F4 der ursprünglichen Frontendprüfung sind für die geprüften Fälle behoben. Keine neuen Befunde aus dieser Nachprüfung.** Der damalige Korrekturstand besteht erstmals auch in diesem unabhängigen Windows-Lauf unverändert, einschließlich des Prüfpfads mit Leerzeichen und Umlaut. Gezielte Mutationen belegen, dass die Tests die ursprünglichen Fehler wieder erkennen.

## Grundlage und Abgrenzung

- Erstbericht: [Prüfung von PR #70 in PR #71](https://github.com/1uedev/task-manager/blob/6ab54470e0d622273f24a6eb8f674dd8d409ca09/docs/REVIEW_TASK-023_PR-70.md), ursprüngliche Fehler auf `518814295b8e14a78c6c2f8ee3031f362dd690ea`.
- Korrekturen und bereits dokumentierte Abnahme: [PR #72](https://github.com/1uedev/task-manager/pull/72), finaler Head **`78eed607f339dc61a66606833205da17b8433627`**. Dieser exakte Stand wurde in einem zweiten eigenen Arbeitsbereich ausgecheckt und ausgeführt, nicht nachträglich durch heutige Quelldateien ersetzt.
- Geprüfter heutiger Integrationsstand: main **`3e3349ec7cfd8854138af6c4d9b405d33e4a32f2`**, ebenfalls separat gebaut und geprüft. Seit PR #72 sind insbesondere TASK-036 und TASK-040 integriert. Beide Prüfstände und ihre Testzahlen bleiben unten getrennt. Zur Veröffentlichung wurde der Berichtszweig auf `6c59d4c` aktualisiert; dazwischen änderte PR #104 ausschließlich TODO.md, der Produktcode ist identisch.
- Maßstab: AGENTS.md, README.md, TODO.md/DONE.md, CLAUDE.md, docs/AI_WORKFLOW.md, DEC-040/056, die Kriterien von TASK-023, REQ-I18N-001/002, REQ-BRAND-001, REQ-UX-001 und die technische Basis sowie relevanten Frontend-/API-Vertragsunterlagen. Allgemeine Detailvorschläge werden nicht als zusätzliche bestätigte Produktvorgaben behandelt.
- DONE.md nennt die Nachprüfung der vier Korrekturen und den Windows-Lauf noch als nicht erfolgt. Dieser Bericht liefert diese Nachweise. Holgers Abnahme wird dadurch weder neu erklärt noch ersetzt; TODO.md und DONE.md bleiben unverändert. TASK-026 ist durch den gemergten Claim-PR #104 für Claude reserviert und wird nicht übernommen.
- Diese F1–F4 gehören zum ursprünglichen Bericht über TASK-023. Sie sind **andere Befunde als F1–F3 aus PR #96 zu TASK-036**; letztere werden hier nicht geschlossen. Auch die späteren Rechtebefunde aus PR #103 bleiben unberührt.

## Bewertung der Korrekturen

| ID | Fundstelle am Korrekturstand | Nachprüfung | Ergebnis |
|---|---|---|---|
| F1: „1 5“ wurde still zu 15 | [parseDecimal, format.ts](https://github.com/1uedev/task-manager/blob/78eed607f339dc61a66606833205da17b8433627/frontend/src/i18n/format.ts#L23), Tests in format.test.ts und Browserformular | Jeweils normales, geschütztes und schmales geschütztes Leerzeichen in DE/EN/FR: **9 Parser- und Formularvarianten verweigert**, gültige sprachabhängige Schreibweisen bleiben gültig. Die vorhandenen Browserfälle für DE/EN bestehen. Vorübergehendes Entfernen aller inneren Leerzeichen lässt **2 von 7 Formattests** scheitern. | **Behoben.** FR ist hier ein Parserprüffall, keine Behauptung einer französischen Produktoberfläche. |
| F2: Eine vermeintlich lokale Logo-Adresse wechselte den Host | [acceptableLogo, brand.ts](https://github.com/1uedev/task-manager/blob/78eed607f339dc61a66606833205da17b8433627/frontend/src/brand/brand.ts#L54), brand.test.ts | Das Originalbeispiel `/\example.invalid/logo.svg` sowie sechs weitere unzulässige Varianten werden abgewiesen. Gültiger lokaler Pfad samt Query bleibt erhalten; eine zulässige HTTPS-Adresse wird normalisiert. Wiedereinbau der frühen bloßen Präfixannahme lässt **1 von 11 Brandingtests** scheitern. | **Behoben.** Nur String-/URL-Prüfung; keine externe Logo-Anfrage ausgelöst. Die dokumentierte Erlaubnis externer HTTPS-Adressen wird nicht als neue Sicherheitsfreigabe ausgegeben. |
| F3: Akzeptierter dunkler Auftritt hatte zu dunkle Hinweise | [derivePalette und brandToCssVariables, brand.ts](https://github.com/1uedev/task-manager/blob/78eed607f339dc61a66606833205da17b8433627/frontend/src/brand/brand.ts#L127), styles.css und Browser-Kontrasttest | Das ursprüngliche Schwarz-Weiß-Beispiel bleibt zulässig. Hinweisfarbe jetzt `#bfbfbf` auf Schwarz: **11,42:1** statt 2,37:1. Fehler, Verweise, Rahmen und Fokus erreichen ihre im Projekt geprüften Schwellen. Rücksetzen der Hinweisfarbe auf `#4a4a4a` lässt **3 von 11 Unit-Tests** und genau **1 von 21 Browserfällen** scheitern; der Browser misst dabei **2,369563:1** für den Hinweis. | **Behoben und Empfindlichkeit des Browsertests belegt.** Das ist keine vollständige Barrierefreiheitsprüfung des heutigen Designs. |
| F4: Unveränderte Testwerkzeuge scheiterten unter Windows | [static.test.ts](https://github.com/1uedev/task-manager/blob/78eed607f339dc61a66606833205da17b8433627/frontend/tests/static.test.ts#L7), [browser/support.mjs](https://github.com/1uedev/task-manager/blob/78eed607f339dc61a66606833205da17b8433627/frontend/tests/browser/support.mjs#L7) | `fileURLToPath` statt URL.pathname für Dateizugriffe. Der exakte PR-Stand besteht unter Windows im frischen Pfad mit Leerzeichen und Umlaut: **59 Unit- und 21 Browserprüfungen**. Zusätzlicher lokaler Serverversuch: Startseite 200, kodierter Ausbruch zu package.json 404, ungültige Prozentkodierung 400. | **Behoben; bisher fehlender Windows-Nachweis erbracht.** Keine Aussage über alle Windows-/Browserkonfigurationen. |

## Tatsächlich ausgeführte Prüfungen

Umgebung: Windows, Node **24.22.0**, npm **11.19.1**, lokal installiertes Google Chrome. Beide Installationen über die jeweilige Lockdatei mit `npm ci --ignore-scripts`; keine globale Laufzeitumstellung.

| Stand / Prüfung | Ergebnis |
|---|---|
| Exakter PR-#72-Head, unverändertes `npm run verify` | Generierung ohne Diff, Typprüfung, **59 Tests**, Produktionsbuild erfolgreich; keine übersprungen. |
| Exakter PR-#72-Head, unverändertes `npm run test:browser` | **21/21 grün**, DE/EN, Formular- und Kontrastfälle, keine übersprungen; Arbeitsbereich mit Leerzeichen/Umlaut. Kein externer angepasster Testharness erforderlich. |
| Heutiges main, unverändertes `npm run verify` | Generierung ohne Diff, Typprüfung, **71 Tests**, Produktionsbuild erfolgreich; keine übersprungen. |
| Heutiges main, unverändertes `npm run test:browser` | **45/45 grün**, keine übersprungen. API-Antworten in diesen Browserfällen sind simuliert; kein vollständiger Frontend-/Backendablauf behauptet. |
| Eigene fokussierte Probe auf beiden Ständen | Jeweils **9 fehlerhafte Zahlenvarianten**, **7 ungültige Logo-Adressen**, gültige Gegenfälle, ursprüngliche dunkle Palette und lokale Testservergrenzen geprüft. Kein externer Abruf. |
| Drei getrennte Mutationen ausschließlich im historischen Arbeitsbereich | F1 **7 Tests / 2 Fehlschläge**; F2 **11 / 1**; F3 **11 / 3** und Browser **21 / 1**. Erwartete Assertion-Fehlschläge; keine ausgelassenen Testfälle. Mutationen jeweils vor der nächsten zurückgenommen. |
| Wiederherstellung | format.ts und brand.ts bytegleich mit den eigenen Originalkopien, Git-Diff des historischen Frontends leer. Danach erneut vollständiges `verify` mit **59 Tests** und **21 Browserprüfungen grün**; auch der Build neu erzeugt. Heutiger Produktcode durchgehend unverändert. |
| Repository | Validator, Aufgabenübersicht, **25 Python-Regressionstests** und `git diff --check` einschließlich aller neuen Berichtdateien erfolgreich. |

Zusätzlich separat gelesen: [Frontend-CI 39134912439](https://github.com/1uedev/task-manager/actions/runs/39134912439) am finalen PR-#72-Head, Job 117697352084: **59 Unit- und 21 Browsertests grün, 0 übersprungen**, Build und Generierung erfolgreich. Dies ist ein gelesener CI-Nachweis, kein weiterer eigener Lauf. Die eigenen Windows-Läufe stehen davon unabhängig oben.

[Reproduktionsquellen und gekürzte tatsächliche Ergebnisse](review-evidence/task-023-pr72/README.md) enthalten die fokussierte Probe und die drei Mutation-Patches. Die Mutationen sind Prüfmittel, keine vorgeschlagenen Produktänderungen. Die normalen Browserprüfungen und die Zusatzprobe schließen ihre eigenen lokalen Server wieder.

## Übergabe und Grenzen

Aus den ursprünglichen Befunden F1–F4 bleibt für diese Prüffälle kein konkreter offener Befund. Die dokumentierte Annahme von TASK-023 bleibt erhalten. Keine Änderung an Reservierungen, Blockierungen, TODO.md, DONE.md, Produktdateien oder gemeinsamen Verträgen. Eine getrennte Folgearbeit wäre erforderlich, falls die Koordination weitere Befunde bearbeiten lassen will.

Nicht geprüft wurden Screenreader, andere Browserfamilien, jede denkbare Farbkombination, eine vollständige automatische Barrierefreiheitsanalyse, reale Benutzer-/Sitzungsverwaltung oder Backendzugriffe, Uploadsicherheit sowie Last- oder Wiederherstellungsziele. Das offene synthetische Problem-Schema Q5 und spätere Erscheinungsbild-/Rechtebefunde werden hier nicht entschieden.
