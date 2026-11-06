# Nachprüfung TASK-087 / PR #259

**Empfehlung: nachbessern.** Die gemeinsame Namensregel, Bestandsbereinigung, Fokusführung und sichtbare Darstellung des Namens sind wesentlich verbessert und in den geprüften Fällen korrekt. Im Konfliktablauf geht jedoch weiterhin eine ungespeicherte Farbe verloren (R1). Die allgemein formulierte Garantie für Anfragen ohne Änderung gilt außerdem bisher nur für PUT von Name/Farbe (R2).

## Prüfstand und Umfang

- Reviewer/Sitzung: unabhängige zweite KI Codex, `codex-20261104-branding-followup-01`, 4. November 2026.
- [PR #259](https://github.com/1uedev/task-manager/pull/259), Branch `claude/project-thread-tzp0cw`, exakter Kopf **`234d663e9a749b9783443ecfc494dbb05372462f`**. PR bei Prüfungsbeginn offen und als Entwurf markiert. Aktuelles main `2222f687a98146b2b0c528b869b6b4dd16e7a811` mit TASK-069/V0053 ist bereits eingemischt.
- Grundlagen: AGENTS.md, TODO.md/TASK-087, DEC-117, docs/REVIEW_TASK-081_PR-243.md mit F1–F4 und H1–H3, betroffene Implementierung, Verträge und Tests. TASK-081 und TASK-087 bleiben bis zu Holgers Entscheidung ohne fachliche Abnahme.
- Eigener Arbeitsbereich und Branch `review/codex-20261104-branding-followup-01`. Im Repository wird ausschließlich dieser Bericht ergänzt. Zusätzliche Prüfprogramme, Logs und Bilder liegen außerhalb des Repository-Arbeitsbereichs; ausschließlich synthetische Daten und eigene Testdatenbanken.

| Alte Kennung aus dem TASK-081-Bericht | Ergebnis der Nachprüfung |
| --- | --- |
| F1 / H1: gemeinsame Namens- und Datenbankregel | In den geprüften Vergleichsmengen behoben; Bestandsbereinigung erfolgreich. |
| F2: andere ungespeicherte Teile erhalten | Erfolgsweg und unmittelbare Fehler korrekt, Konflikt-Nachladen bleibt R1. |
| F3: Fokus | Die geforderten Standardabläufe sind korrigiert. |
| F4: sichtbarer Name | Korrigiert, einschließlich Logo und 80-Zeichen-Name bei 320 px. |
| H2: echte Änderungen / keine unnötige Version | Konkreter PUT-Fall korrigiert; allgemeine Zusage außerhalb dieses Pfads bleibt R2. |
| H3: HEAD und Normalisierung im Vertrag | Öffentliche HEAD-Operationen und PUT-Eingabe korrigiert; Vorschau-Hinweis H2 dieses Berichts. |

## Befunde

### R1 [P2] – Nach einem Versionskonflikt verwirft „Aktuellen Stand laden“ weiterhin die ungespeicherte Farbe

**Datei/Zeile:** `frontend/src/pages/AppearancePage.tsx:58–64,291–297`, insbesondere `:294`.

Der Erfolgsweg `run(part, …)` erhält die anderen bearbeiteten Teile korrekt. Der Fehlerweg für Versionskonflikte setzt beim Nachladen aber weiterhin `edited.current = false`. Ändert sich die gespeicherte Farbe, übernimmt der Effekt für `savedAccent` anschließend diese Farbe in `draft` und `hex`, obwohl die Person die Farbe noch nicht gespeichert hat. `nameEdited` wird dagegen nicht zurückgesetzt; der ungespeicherte Name bleibt erhalten.

**Reproduktion:** Gespeicherte Farbe Blau, im Formular Name „Ungespeicherter Name“ und ungespeicherte Farbe `#7C3AED`. Eine zweite Person ändert auf dem Server die Farbe auf `#16A34A` und erhöht die Version. „Namen speichern“ antwortet deshalb mit 412. „Aktuellen Stand laden“ anklicken, um die neue Fassung für den erneuten Speicherversuch zu holen. Ergebnis der Browserprobe: Name weiterhin „Ungespeicherter Name“, Farbwert **`#16A34A` statt `#7C3AED`**. „Alles zurücksetzen“ wurde in dieser Reproduktion nicht verwendet.

**Auswirkung:** F2 ist für den normalen Speicherweg behoben, für den ausdrücklich nachgefragten Fehler-/Konfliktablauf noch nicht vollständig. Eine unabhängige Farbeingabe geht verloren. Die asymmetrische Behandlung von Name und Farbe macht das Verhalten zusätzlich schwer vorhersehbar.

**Vorschlag:** Beim Nachladen zur Konfliktauflösung die aktuelle Serverfassung/Version aktualisieren und die Bearbeitungsmarker anderer ungespeicherter Teile erhalten. Ein ausdrückliches Verwerfen kann eine getrennte, klar bezeichnete Aktion sein. Den beschriebenen Ablauf als Browsertest ergänzen; der vorhandene Test prüft nur erfolgreiche Teiländerungen.

### R2 [P2] – Unveränderte Reset- und Logoanfragen erzeugen weiter Versionen und Änderungsereignisse

**Datei/Zeile:** `backend/src/main/java/taskmanager/appearance/internal/AppearanceService.java:101–107,133–163`; Gegenbeispiel des korrekt korrigierten PUT-Pfads `:80–96`; `DECISIONS.md:207` (DEC-117, Regel 5).

Nur `change` vergleicht den neuen mit dem bisherigen Inhalt. `reset`, `removeLogo` und `replaceLogo` erhöhen die Version und schreiben ein Ereignis ohne Inhaltsvergleich.

**Reproduktion über die echte HTTP-Schnittstelle:** Ausgangszustand „Task Manager“, `#f97316`, kein Logo, Version 1, Audit leer:

| Anfrage mit jeweils aktuellem If-Match | Ergebnis |
| --- | --- |
| Zwei gleichzeitige unveränderte PUT für Name/Farbe | Beide 200, Version bleibt 1, Audit bleibt leer: korrekt. |
| DELETE `/api/v1/appearance` im bereits vorhandenen Standardzustand | 200, Version 2, `appearance.reset`; Inhalt unverändert. |
| DELETE `/api/v1/appearance/logo` bei weiterhin fehlendem Logo | 200, Version 3, `appearance.logo_removed`; Inhalt unverändert. |
| Dasselbe gültige PNG zweimal per PUT `/api/v1/appearance/logo` | Erste Speicherung Version 4, identischer zweiter Inhalt Version 5; zweimal `appearance.logo_replaced`. |

**Auswirkung:** Die ursprüngliche konkrete H2-Reproduktion für PUT von Name/Farbe ist behoben. Die in DEC-117 und Frage 6 allgemein formulierte Zusage „eine Anfrage ohne Änderung erhöht die Version nicht“ gilt jedoch noch nicht für alle Auftrittsanfragen. Das Audit kann weiterhin Entfernen/Ersetzen ohne tatsächliche Änderung melden; solche Aufrufe verursachen außerdem vermeidbare Versionskonflikte bei anderen Personen.

**Vorschlag:** Unter derselben Zeilensperre auch für Reset/Entfernen/Ersetzen auf tatsächliche Inhaltsänderungen prüfen. Bei gleichem Inhalt Version, Änderungszeitpunkt und Änderungsaudit erhalten. Falls die Zusage bewusst ausschließlich für PUT von Name/Farbe gelten soll, diese Ausnahme ausdrücklich entscheiden und in DEC-117/Abnahmeregeln präzisieren. Das README beschreibt derzeit bereits die engere PUT-Semantik; diese ist nicht mit der uneingeschränkten Formulierung gleichzusetzen.

## Hinweise

### H1 – Der Testersatz hat dieselbe Zeichenregel, aber weiterhin andere Regeln für ungültige PUT-Körper

**Datei/Zeile:** `frontend/tests/browser/backend.mjs:469–479`; `backend/src/main/java/taskmanager/appearance/internal/AppearanceService.java:74–76`; `AppearanceController.java:70–77`.

**Beobachtung/Auswirkung:** Für Zeichenfolgen stimmen Randzeichen, Verbote, Kürzen und Codepunktlänge überein. Für `{}` liefert der Testersatz dagegen eine erfolgreiche unveränderte Antwort, während der Server `400 appearance.change_empty` liefert. `name: null` führt im Testersatz zu `.replace` auf null; der Server behandelt null wie einen nicht gesendeten Teil. Das ist kein weiterer Unicode-Fehler, begrenzt aber die Aussagekraft der Browserprüfungen zu fehlerhaften Anfragen. Die Rohkörper-Differenzen wurden aus dem Code festgestellt; der vollständige Zeichenvergleich betrifft Strings.

**Vorschlag:** Fehlende beziehungsweise null Teile und `appearance.change_empty` im Testersatz wie im Server behandeln. Die bestehende Servervalidierung soll dabei maßgeblich bleiben.

### H2 – Die Mailvorschau beschreibt denselben normalisierten Namen noch mit der alten Roh-Längengrenze

**Datei/Zeile:** `contracts/appearance.openapi.yaml:413`; `backend/src/main/java/taskmanager/appearance/internal/MailTemplateService.java:164`; zum Vergleich korrigiertes `AppearanceChange.name` `:318–325`.

**Beobachtung/Auswirkung:** Das Eingabeschema für PUT ist richtig korrigiert: Die Obergrenze gilt laut Beschreibung nach dem Kürzen, kein `maxLength: 80` am Rohwert. `MailTemplatePreviewRequest.name` behält dagegen diese Roh-Längengrenze. Der Vorschau-Service verwendet dieselbe neue `validName`-Methode. Eine zusätzliche echte API-Probe mit 80 `x` plus je einem U+00A0 am Rand ergab dort HTTP 200 und einen normalisierten Namen mit 80 Zeichen; das Schema mit Roh-Maximum 80 würde diesen 82-Zeichen-Eingabewert ablehnen.

**Vorschlag:** Die Namens-Eingaberegel als gemeinsame Vertragsdefinition verwenden und auch bei der optionalen Vorschau-Eingabe beschreiben. Die Ausgabegrenze 80 für bereits normalisierte Namen bleibt korrekt. Dies ist eine verbleibende Vertragsabweichung im separat eingeführten Mailvorschaupfad, kein Fehler der jetzt korrigierten PUT-Normalisierung.

## Antworten auf die sieben Prüffragen

### 1. Gleichheit der Namensregel

**Kein abweichender String im geprüften Zeichenvorrat gefunden.** Der Vergleich wurde nicht nur mit einzelnen Beispielen durchgeführt:

- TypeScript gegen Testersatz: alle **1.114.112 Codepunkte** von U+0000 bis U+10FFFF, jeweils an beiden Rändern und innerhalb eines Namens; **0 Unterschiede**. 30 Randzeichen, 2.285 verbotene Codepunkte einschließlich 2.048 isolierter Surrogat-Codeeinheiten.
- Java `EDGE`/`FORBIDDEN` samt explizitem Surrogatverbot gegen dieselben TypeScript-Ergebnisse: alle **1.114.112 Codepunkte**, **0 Unterschiede**.
- Tatsächliche PostgreSQL-Check-Bedingung aus `pg_constraint` gegen TypeScript: **2.224.126 Fälle** mit je einem Codepunkt innerhalb beziehungsweise an beiden Rändern eines gespeicherten Namens; **0 Unterschiede**. U+0000 und isolierte Surrogate sind im PostgreSQL-UTF-8-Textformat nicht darstellbar und wurden dort ausgespart; Java/TypeScript lehnen sie ab.

Dabei ist die unterschiedliche Aufgabe der Stellen berücksichtigt: Server und `normalizeBrandName` nehmen einen Rohwert entgegen und entfernen Randzeichen. `isBrandName` und Datenbankbedingung prüfen die bereits normalisierte gespeicherte Form und lehnen Randzeichen dort ab. Das ist beabsichtigt und kein Widerspruch.

Zusätzlich echte API-Grenzproben: Nur Randzeichen ergeben 400; 80 Raketenzeichen plus zwei geschützte Randzeichen ergeben 200 mit 80 gespeicherten Codepunkten; 81 Raketenzeichen und ein inneres U+200B ergeben 400. Die neuen API-Tests prüfen halbe Surrogatpaare ausdrücklich als JSON-Escapes, bevor ein Datenbanktreiber sie verändern könnte. Der feste Formatzeichenkatalog folgt Unicode 15.1, wie im PR offengelegt; er wächst nicht automatisch mit zukünftigen Unicode-Versionen.

### 2. Bereinigung und Reihenfolge der Migration

**Die geprüften Bestandswerte werden korrekt bereinigt.** V0054 entfernt zuerst Randzeichen; der zweite UPDATE ersetzt leere, überlange und danach noch verbotene Werte durch „Task Manager“. Erst danach wird die neue Check-Bedingung angelegt. V0046 bleibt unverändert.

`AppearanceNameMigrationTest` migriert sieben Datenbestände von V0052 zum aktuellen Stand und durchläuft damit tatsächlich V0053 vor V0054. Dieser Test ist erfolgreich. Zusätzlich wurde das unveränderte SQL von V0054 gegen eine temporäre Tabelle mit permissiver alter Check-Bedingung ausgeführt, damit auch beschädigte Altwerte geprüft werden konnten, die eine intakte V0046-Bedingung gar nicht zugelassen hätte. Alle **neun Zusatzfälle** waren korrekt: leer, nur geschützte Leerzeichen, nur Tab/Zeilenumbruch/Steuer-Randzeichen/BOM, normal zu kürzender Name, inneres Formatzeichen, innerer Zeilentrenner, 81 Zeichen, 80 ergänzende Unicode-Zeichen mit Rändern und ein zulässiges geschütztes Leerzeichen im Inneren.

Die Migrationslisten in `DatabaseFoundationTest.java:36`, `ApplicationLifecycleTest.java:34` und `lifecycle-check.sh:89–91` sind mit **51 Migrationen bis V0054**, einschließlich V0053 und den vorhandenen Nummernlücken, konsistent.

### 3. Teiländerungen und Fehler

`run(part, …)` übernimmt nach Erfolg nur den gesendeten Teil; Bearbeitungsmarker anderer Teile bleiben erhalten. Die vorhandenen Browsertests für Farbe → Name, Name → Farbe sowie Logo → beide Eingaben sind grün. Eigene Zusatzproben bestätigten erhaltenen Namen und Farbwert beim Logo-Upload, nach HTTP 500 beim Namensspeichern sowie nach HTTP 412 beim Gesamtreset. Ein erfolgreich bestätigter Gesamtreset setzte Name und Farbe auf den Standard und entfernte das Logo. **Der anschließende Konflikt-Nachladeweg bleibt R1.**

### 4. Fokus

Die korrigierten regulären Abläufe funktionieren in den Tastatur-/DOM-Proben: Nach erfolgreichem Speichern/Logo-Upload/Gesamtreset liegt der Fokus auf der fokussierbaren Bestätigung mit `role="status"`; nach Abbrechen auf „Alles zurücksetzen“. Nach einem Serverfehler wird der fokussierbare Meldungsbereich mit dem Fehlerinhalt fokussiert. Die eingeführten `ref`s, `tabIndex=-1` und Status-/Live-Semantik sind dafür schlüssig. Es wurde kein manueller Lauf mit einem echten Screenreader ausgeführt; geprüft wurden Tastatur, Fokusziel, Text und entsprechende DOM-Semantik.

### 5. Sichtbarer Name mit Logo und bei 320 px

Die ausblendenden Regeln sind entfernt. Bei schmalen Ansichten erhält der Markenbereich eine eigene umbruchfähige Zeile. Die vorhandenen Tests mit/ohne Logo, vor/nach Anmeldung und bei 1100/320 px sind grün.

Eigene Zusatzproben mit Logo und **80 ungetrennten `W`** bei 320 px, Anmeldung und Anwendung jeweils hell/dunkel: Namensfeld ungefähr **230 × 152 px**, `clip: auto`, innerhalb des Fensters; Dokumentbreite jeweils **320/320 px**, kein waagrechtes Scrollen. Die Kontraststichproben meldeten keine Verletzung. Zwei der vier erzeugten Bilder wurden zusätzlich visuell betrachtet. Für Portal und Pflichtwechsel ist derselbe korrigierte `BrandMark`/CSS-Pfad zuständig; es wurde kein gesonderter manueller Langnamenlauf dieser beiden Zustände durchgeführt.

### 6. Audit, Version und Nebenläufigkeit

Für PUT von Name/Farbe ist H2 korrekt behoben: Nach Normalisierung werden Vorher/Nachherwerte unter der Zeilensperre verglichen; bei Gleichheit wird vor dem UPDATE und vor jedem Audit zurückgegeben. Zwei echte gleichzeitige unveränderte PUT mit derselben Version ergaben **200/200, weiterhin Version 1 und 0 Auditeinträge**. Eine veraltete Version wird weiterhin vor der Prüfung auf Inhaltsgleichheit abgewiesen; ein scheinbar unveränderter Aufruf umgeht also keine Versionsprüfung. Bei wirklicher Änderung wird jeder tatsächlich geänderte Teil einmal protokolliert. **Die übrigen Änderungsoperationen erfüllen die allgemeine Garantie noch nicht, siehe R2.**

### 7. Vertrag und HEAD

Appearance 0.3.1 dokumentiert die beiden öffentlichen HEAD-Operationen mit `security: []`, Status und leerem Antwortkörper. Echte anonyme Serverproben ergaben HEAD auf `/appearance/public` **200/0 Bodybytes**, auf `/appearance/public/logo` ohne Logo **404/0 Bytes**, mit Logo **200/0 Bytes**, `Content-Type: image/png` und `Cache-Control: no-store`. Die Normalisierungsbeschreibung von `AppearanceChange.name` entspricht dem Server einschließlich erst nachträglich anzuwendender Codepunkt-Obergrenze. Der Vertragsabgleich und der ergänzte HEAD-Dokumentationstest sind grün; generierte Typen wurden ohne Differenz neu erstellt. Die verbleibende Vorschau-Abweichung ist H2.

## Prüfungen und Grenzen

Stand aller aufgeführten Läufe ist ausschließlich der genannte PR-Kopf. Windows: Python 3.12 mit `PYTHONUTF8=1`, Node 24.22.0 / npm 11.19.1, Java **25.0.4.1+1**, eigene PostgreSQL **16.15**. Der Windows-Wrapper `mvnw.cmd` und der vorhandene Python-Interpreter ersetzen die entsprechenden Unix-Befehlsnamen. Keine Anwendung wurde veröffentlicht und kein externer Reviewkommentar gesendet.

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Erfolgreich; nach dem Bericht erneut geprüft. |
| `python3 scripts/task_board.py --check` | Erfolgreich, Übersicht aktuell. |
| `python3 -m unittest discover -s tests -v` | 38 Tests: 37 erfolgreich, ein Unix-spezifischer Zeitzonentest unter Windows übersprungen. |
| Backend `./mvnw verify` mit PostgreSQL 16 | **BUILD SUCCESS: 534 Tests, 0 Fehlschläge, 0 Fehler, 0 übersprungen**, Laufzeit 16:45 Minuten. Darunter AppearanceApiTest 22, AppearanceContractTest 2, AppearanceNameMigrationTest 1 sowie ApplicationLifecycleTest 3 und DatabaseFoundationTest 4, jeweils erfolgreich. |
| Frontend `npm ci` | Erfolgreich. |
| Frontend `npm run verify` | Erfolgreich: Generierung, Typecheck, **83/83 Tests**, Produktionsbuild. Vite meldete einen großen Bundle-Chunk. |
| Frontend `npm run test:browser` | Erfolgreich: **198/198 Tests**, keine übersprungenen Tests. Kein Wiederholungslauf erforderlich. |
| Vollständiger Zeichenvergleich | Erfolgreich ausgeführt, 0 Unterschiede in den oben genannten Vergleichsmengen. |
| Zusätzliche Migrations-, API- und Browserproben | Erfolgreich ausgeführt; R1/R2 und H2 wie beschrieben reproduziert. Erfolgreiche Zusatzprüfungen und ihre Grenzen stehen oben. |
| `git diff --check` / Anwendungsdateien | Sauber; ausschließlich dieser Bericht ergänzt. |

**Nicht ausgeführt:** PostgreSQL-18.6-Testcontainer, separates Unix-Prozessskript `lifecycle-check.sh`, manueller Screenreader-Test und vollständiger Live-Browserlauf gegen das echte Backend. Die Java-Lebenszyklus-/Migrationstests verwenden die echte PostgreSQL-16-Testinstanz. Browserprüfungen verwenden den Testersatz; gezielte API-/Datenbankproben prüfen die genannten Servereigenschaften unabhängig davon.

## Abnahmeempfehlung

**TASK-087 nachbessern; gemeinsame Abnahme von TASK-081 und TASK-087 noch zurückstellen.** R1 schließen und die in R2 beschriebene allgemeine Versions-/Auditzusage erfüllen oder ihre Ausnahme ausdrücklich festlegen. Die ursprünglichen Unicode-, Datenbank-, Fokus- und Sichtbarkeitsprobleme sind in den geprüften Fällen behoben. Für die Nachprüfung der Korrekturen gezielte Regressionen ergänzen; die bereits grünen normalen Abläufe müssen dabei erhalten bleiben.
