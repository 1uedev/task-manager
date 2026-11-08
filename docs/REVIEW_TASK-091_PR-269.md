# Prüfung TASK-091 / PR #269 (Claude statt Codex)

**Empfehlung: kleine Nachbesserung, danach TASK-067, TASK-088 und TASK-091 ohne weitere Nachprüfung abnehmen.** R1 und R2 sind in der Sache wirksam behoben. Die Ladeanzeige unterscheidet ladend, leer und fehlgeschlagen in beiden Sprachen, und alle drei Gegenproben zu R2 schlagen ohne die jeweilige Absicherung fehl. Offen bleibt ein Befund P3: Ein bestehender Browsertest aus TASK-088 wurde nicht an die neue Ladeanzeige angepasst und ist seitdem unzuverlässig (lokal 2 von 6 Läufen rot). Das verletzt Abnahmekriterium 2 von TASK-091 („Bestehende Backend und Browser Tests bleiben grün“), lässt sich aber mit einer Zeile im Test beheben.

Sitzung: claude-20261108-newanswersfix-review-01, unabhängig vom Autor-Thread. Geprüfter Stand: main `62007bf` mit dem Merge von PR #269 (`419d8b2`). Grundlage: AGENTS.md, TODO.md (TASK-067, TASK-088, TASK-091), DECISIONS.md (DEC-118, DEC-121), docs/REVIEW_TASK-088_PR-261.md und der Prüfauftrag reviews/codex-prompt-task-091.md. Es wurde kein Produktcode geändert; Gegenproben liefen in einem getrennten Arbeitsbaum und wurden verworfen.

## Befunde

### P1 und P2

Keine.

### B1 [P3] – Bestehender Browsertest zählt die Liste, während sie noch lädt

- **Datei:** frontend/tests/browser/helpdesk.test.mjs:547 (Test „a customer answer that joined its ticket is listed as new until somebody marks it as seen“), Ursache frontend/src/pages/InboxPage.tsx:295–301.
- **Ablauf:** Am Ende navigiert der Test zurück zur Prüfwarteschlange, wartet nur auf die Seitenüberschrift und prüft sofort `page.getByTestId('new-answers').count() === 0`. Seit TASK-091 rendert die Liste beim Laden einen Abschnitt mit demselben `data-testid="new-answers"`. Kommt die Antwort des Testersatzes nicht vor der Zählung, ist das Ergebnis 1.
- **Nachweis:** Im vollständigen Lauf von `npm run test:browser` schlug genau dieser Test fehl (`1 !== 0` in Zeile 547). Fünf Einzelläufe danach: 4 grün, 1 rot. Eine gezielte Probe mit zurückgehaltenem Abruf (`state.hold`) ergibt beim Zählen nach der Seitenüberschrift deterministisch 1. Die CI von PR #269 war grün; das zeigt nur, dass der Wettlauf dort meist gewonnen wird.
- **Auswirkung:** Kein Fehler im Produkt. Die Browsertests werden aber unzuverlässig, und das Abnahmekriterium 2 ist nicht verlässlich erfüllt. Weitere Stellen, die `new-answers` sofort zählen, gibt es nicht (geprüft per Suche).
- **Vorschlag:** In Zeile 547 statt der Zählung `await page.getByTestId('new-answers').waitFor({ state: 'detached' });` wie im neuen Test von TASK-091, oder vorher auf das Ende des Ladens warten.

## Hinweise

### H1 – `lockWaiters()` zählt alle Wartenden der Datenbank statt der eigenen

- **Datei:** backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java:1525 mit den Wartebedingungen in 1389, 1391, 1495 und 1497.
- Die Hilfsfunktion zählt jeden Prozess der Testdatenbank mit `pg_blocking_pids`. Die seit TASK-063 (H4) übliche Hilfe `blockedBy(pid)` (Zeile 2105) zählt nur die Prozesse, die auf die Sperre einer bestimmten Verbindung warten; der neue Projektwechseltest nutzt sie richtig. Da Surefire die Tests nacheinander in einer JVM ausführt und der Ausführer in den Tests keine Sperren hält, ist das heute kein Fehler. Eine fremde Wartende würde die Gleichheit `== 1` aber entweder nie erreichen (Zeitüberschreitung nach 20 Sekunden) oder zu früh erfüllen.
- **Vorschlag:** `blockedBy(pid(holder))` und dessen Größe statt `lockWaiters()`. Beim zweiten Schritt wartet die zweite Anfrage je nach Sperrfolge auf die erste statt auf den Halter; dann `lockWaiters` auf die Prozesse beider Kennungen beschränken. Nicht abnahmehindernd.

### H2 – Kurzes Aufblitzen für Personen ohne Antwortrecht

- **Datei:** frontend/src/pages/InboxPage.tsx:295–301.
- Wer die Prüfwarteschlange ohne `helpdesk.reply` öffnet, sieht beim ersten Laden kurz „Neue Kundenantworten“ mit „Wird geladen …“, danach verschwindet der Abschnitt und der Inhalt darunter rückt nach oben. Der Server antwortet für diese Personen mit einer leeren Liste statt 403, die Oberfläche kann es vorher also nicht wissen. Das Ausblenden der leeren Liste ist vertretbar (sonst stünde für alle ein leerer Kasten da), das kurze Springen ist der Preis dafür. Nur zur Kenntnis.

### H3 – Englisch nur durch Probe belegt

- Der neue Browsertest prüft die Ladeanzeige nur auf Deutsch. Eine eigene Probe mit `locale: 'en-US'` zeigte „New customer answers“ und „Loading …“ mit `role="status"`. Die Texte kommen aus den vorhandenen Schlüsseln `inbox.newAnswers` und `states.loading`; das Abnahmekriterium verlangt nur Deutsch. Kein Handlungsbedarf.

### H4 – Beobachtung außerhalb des Auftrags

- Nach „Gesehen“ bleibt die markierte Zeile bis zum Ende des Neuladens stehen und ihre Schaltfläche ist wieder aktiv; ein zweiter Klick ergäbe die Meldung „bereits geändert“. Das ist Verhalten aus TASK-088, nicht aus TASK-091, und nur bei langsamer Verbindung sichtbar. Für eine spätere Pflege notiert.

## Prüfung nach den Fragen des Auftrags

### 1. R1: ladend, leer, fehlgeschlagen

**Behoben.** Beim Laden zeigt `NewAnswers` Überschrift, `aria-busy="true"` und `LoadingView` mit `role="status"`; Fehler zeigt Überschrift, Problem und „Erneut versuchen“ wie seit TASK-088; leer ohne Meldung blendet aus. Kein Flackern beim Neuladen nach dem Markieren: `useLoad` (frontend/src/app/hooks.ts:19) behält einen geladenen Stand bei, bis die neue Antwort da ist. Eine Probe mit zurückgehaltenem Neuladen nach „Gesehen“ zeigte weiter die Liste ohne `aria-busy` und ohne Ladeanzeige, der Fokus blieb auf der Überschrift `new-answers` und blieb dort auch nach der Antwort. Der Fokus geht also nicht durch einen Wechsel in den Ladeabschnitt verloren. Die beiden neuen Browsertests (leer und gefüllt nach dem Laden) laufen grün. Siehe aber B1.

### 2. R2: dauerhafte Tests

**Behoben, mit Gegenproben belegt.**

- `NewAnswersMigrationTest` migriert eine leere Datenbank bis V0051, legt eine eröffnende Mail, eine später angehängte Antwort und eine wartende Mail an, migriert weiter und verlangt `joined_at`, `seen_at` und `seen_by` leer, keine neue Antwort und die Ablehnung einer Markierung ohne `joined_at` durch die Datenbank. Das ist ein echtes Upgrade über V0052.
- **Gegenprobe 1:** V0052 um `update helpdesk_message set joined_at = received_at where ticket_id is not null;` ergänzt. Ergebnis: Test rot (`expected: <[null, null, null]> but was: <[2026-10-01 10:00:00.0, null, null]>`).
- **Gegenprobe 2:** die zweite Prüfung `inPlace` nach `tickets.lockForHelpdeskChange` in AnswerService.seen entfernt. Ergebnis: `aTicketThatLeavesTheProjectWhileTheMarkingWaitsMakesTheAnswerUnknown` rot (`expected: <404> but was: <200>`). Die beiden anderen Sperrtests blieben im selben Lauf grün.
- **Gegenprobe 3 (zusätzlich):** in AnswerService.seen die Mail ohne Sperre gelesen (`repository.message` statt `repository.lockMessage`). Ergebnis: `twoPeopleWhoMarkAnAnswerAtOnceLeaveOneMarking` rot (`[200, 200]` statt `[200, 412]`, Version doppelt erhöht) und `attachingAndMarkingThatWaitForTheSameMailEndInEitherOrder` rot (zweite Anfrage wartet nie). Die Tests hängen also wirklich an der Mailsperre.
- Beide Reihenfolgen werden geprüft: Anhängen zuerst ergibt 200 und 412, Markieren zuerst 409 und 200; danach ist die Mail jeweils eine neue Antwort. Die Reihenfolge ist deterministisch, weil PostgreSQL Wartende auf dieselbe Zeile in Ankunftsreihenfolge bedient und der Test die zweite Anfrage erst schickt, wenn die erste nachweislich wartet.
- Die Wartebedingungen über `pg_stat_activity` sind im heutigen Testaufbau verlässlich (keine parallelen Tests, getrennte Datenbank je Lauf). Der Projektwechseltest grenzt richtig auf die eigene Verbindung ein, die beiden anderen nicht (H1).

### 3. Umfang

Eingehalten. Geändert sind nur InboxPage.tsx (Ladezustand), der Testersatz (`state.hold`), Browser- und Backendtests sowie AGENTS.md, CLAUDE.md, DECISIONS.md (DEC-121), TODO.md und backend/README.md. Keine Migration, kein Produktcode im Backend.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | OK |
| `python3 scripts/task_board.py --check` | aktuell |
| `python3 -m unittest discover -s tests -v` | 38 Tests OK |
| `git diff --check 419d8b2^ 419d8b2` | ohne Befund |
| Backend `./mvnw verify` mit lokalem PostgreSQL 16, JDK 21 (`-Djava.version=21`) | 545 Tests, 0 Fehler, BUILD SUCCESS |
| Frontend `npm ci`, `npm run verify` | 84 Tests grün, Build erfolgreich |
| Frontend `npm run test:browser` (Chromium unter /opt/pw-browsers) | 210 Tests, 209 grün, 1 rot (B1) |
| Einzelläufe des Tests aus B1 | 5 Läufe: 4 grün, 1 rot |
| Gegenproben 1 bis 3 in getrenntem Arbeitsbaum | alle wie erwartet rot, danach verworfen |
| Browserproben (Englisch, Zählung während des Ladens, Neuladen nach „Gesehen“) | wie unter Frage 1 beschrieben |

Nicht ausgeführt: der gepinnte PostgreSQL 18.6 Container (kein Docker in der Prüfumgebung). Der lokale Lauf gegen PostgreSQL 16 ist kein Nachweis für die gepinnte Version; die CI von PR #269 lief mit ihr grün.

## Empfehlung

B1 mit einer Zeile im Browsertest beheben, das kann auch im Abnahme-PR geschehen. Danach TASK-067, TASK-088 und TASK-091 gemeinsam abnehmen; eine weitere Nachprüfung halte ich nicht für nötig. H1 bis H4 sind keine Abnahmehindernisse. Aufgabenstatus und fachliche Abnahme bleiben bei Holger.
