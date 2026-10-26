# Unabhängige Nachprüfung TASK-030 / PR #141

**Ergebnis:** **F1 bis F6 sind im nachgespielten Umfang behoben. Die Nachbesserung ist technisch abnahmefähig.** Alle elf eigenen Gegenproben gegen das echte Backend bestehen. Kein neuer blockierender Produktbefund P1 oder P2. Die lokale Python-Pflichtsuite ist wegen einer bestehenden Testannahme außerhalb von PR #141 nicht vollständig grün (H1/P3 unten); daraus folgt keine allgemeine Windows-Qualifikation. Keine fachliche Abnahme.

## Stand und Auftrag

- Unabhängige Sitzung `codex-20261022-task030-followup-01`, 22. Oktober 2026, eigener Klon, Branch `review/task-030-pr-141`. Nur Bericht und eigene bereinigte Nachweise; Produktcode, vorhandene Tests, TODO.md und DONE.md unverändert. Der ausdrückliche Nachprüfauftrag übernimmt keine Implementierungsreservierung.
- [PR #141](https://github.com/1uedev/task-manager/pull/141), Kopf `aa0202f98e40191237c061c7cf3274730c36604f`, Basis `7e8b7f0bae4d361babf2c9e5800081fea7785651`, bereits integriert als main `a8489bcd07570e679a2ba77870ad002a4a53f1c3`. Dieser Integrationsstand wurde unverändert gebaut und ausgeführt; sein Dateibaum stimmt mit dem PR-Kopf überein.
- Maßstab: [ursprünglicher Bericht zu PR #132/#133](https://github.com/1uedev/task-manager/blob/ae1ac92fbe1e83280dd837cb83b35de4ebadf9b7/docs/REVIEW_TASK-030_PR-133.md) aus [PR #140](https://github.com/1uedev/task-manager/pull/140), [TASK-030](../TODO.md#task-030-durchgängige-oberfläche-und-minimale-administration-integrieren), DEC-040 sowie DEC-070 einschließlich Nachtrag 8 bis 14. Der ursprüngliche Bericht liegt nicht auf dem geprüften main; er wurde aus dem festgehaltenen Reviewstand gelesen.
- Gelesen: AGENTS.md, CLAUDE.md, README.md, TODO.md, DONE.md, docs/AI_WORKFLOW.md, docs/TECHNICAL_BASELINE.md, relevante Anforderungen/Entscheidungen und Verträge sowie sämtliche Änderungen von PR #141 und die zugehörigen Tests. Bezug: REQ-PROD-002, REQ-UX-001, REQ-ACL-001/003, REQ-I18N-001; weitere Fachanforderungen bleiben wie im Erstbericht eingegrenzt.
- TASK-030 bleibt BEREIT ZUR PRÜFUNG bei `claude-20261021-ui-01`. Voraussetzungen TASK-023 und TASK-029 einschließlich vorgelagerter Verträge werden nicht erneut abgenommen. Die Aussage des Autors „alle sechs behoben“ ist im Folgenden durch eigene Ausführungen geprüft, keine übernommene Abnahme.

## Rückprüfung F1 bis F6

### F1 — zuvor P2: Historie nach Feldrechteentzug — behoben

**Fundstellen:** `frontend/src/pages/TicketPage.tsx:35–53`, `:135–138`, `:211–212`.

Der Zähler `refusals` gibt Historie und Tags nach einer abgewiesenen Speicherung neue Komponentenschlüssel. Sie beginnen damit ohne den vorherigen Inhalt neu; die Ticketversion muss sich dafür nicht ändern. Die Ticketantwort wird ebenfalls neu geladen.

**Eigener Ablauf:** Verwaltungskonto A öffnet ein Ticket mit einer Beschreibung und zwei historischen Beschreibungswerten. Während einer lokalen Beschreibungseingabe entfernt Verwaltungskonto B über die echte Rechte-API das Leserecht für genau dieses Feld. Speichern durch A erhält 403. Die Oberfläche zeigt anschließend die verborgene Beschreibung und keine Beschreibungseinträge mehr in der Historie; die direkte frische Historienantwort enthält ebenfalls null solche Änderungen. Die Ticketversion bleibt **3**.

Zusätzlich wurde die Zustellung der bereits am echten Backend gelesenen neuen Historienantwort angehalten. **Auch während dieses Wartezustands zeigt die Historiensektion keinen alten Beschreibungswert.** Nach Freigabe bleibt die gefilterte Sicht korrekt. Damit ist die ursprüngliche Lücke einschließlich des Zwischenzustands nachgewiesen geschlossen. Die Tags werden nach demselben Schlüsselmechanismus neu aufgebaut; ein zusätzlicher Entzug ausschließlich des Tagleserechts wurde hier nicht nachgespielt.

Nachweis: Fall `F1` in [replay-results.json](review-evidence/task-030-pr-141/replay-results.json). Die zugehörige eigene Bildschirmaufnahme ist lokal gesichert und wurde angesehen.

### F2 — zuvor P2: Eingaben während einer Speicherung — behoben

**Fundstellen:** `frontend/src/pages/TicketPage.tsx:115–127` und die anschließende Übernahme einer neuen Ticketfassung.

Der beim Absenden festgehaltene Entwurf `sent` wird mit dem bei Antwortempfang aktuellen Entwurf verglichen. Nur seitdem unveränderte Felder werden aus der bestätigten Antwort ersetzt. Spätere Eingaben bleiben gegenüber der neuen Basis ungespeichert.

**Eigener Ablauf:** A ändert den Titel und speichert. Der echte Server führt PATCH aus und antwortet 200; nur die Übermittlung dieser fertigen Antwort an den Browser wird verzögert. Währenddessen wird eine neue Beschreibung getippt. Nach Antwortfreigabe ist der Titel gespeichert, die nachträgliche Beschreibung steht weiter im Feld und der Speichernknopf bleibt aktiv. Ein unabhängiger API-Abruf bestätigt, dass diese Beschreibung noch nicht gespeichert ist. Beim Wechsel zur Suche erscheint die Rückfrage; Abbrechen erhält Route und Eingabe. Eine zweite Speicherung übernimmt anschließend die Beschreibung nachweislich ins echte Backend.

Nachweis: Fall `F2` in [replay-results.json](review-evidence/task-030-pr-141/replay-results.json). Die Meldung „Gespeichert.“ bezieht sich auf den abgeschlossenen ersten Vorgang; weitere Eingaben bleiben korrekt speicherbar und geschützt.

### F3 — zuvor P2: Schutz der Anlageformulare — behoben

**Fundstellen:** `frontend/src/pages/ProjectsPage.tsx:58–60`, `AccountsPage.tsx:25–26`, `GroupsPage.tsx:24–25`; gemeinsamer `useUnsavedGuard` und Navigation aus `frontend/src/app/hooks.ts` / `route.ts`.

**Eigener Ablauf jeweils für Projekt, Konto und Gruppe in DE und EN:** Formular ausfüllen, zur Suche wechseln, Rückfrage abbrechen. Die Route bleibt erhalten und das eingegebene Feld unverändert. Zusätzlich einen vollständigen Seitenreload versuchen: Der Browser löst `beforeunload` aus; Abbrechen erhält das Formular. Danach dasselbe Formular erfolgreich über die echte API anlegen und zur Suche wechseln: **keine weitere Schutzabfrage**. Bei Projekten/Gruppen umgeht die programmgesteuerte Erfolgsnavigation den Schutz; bei Konten werden die Eingabefelder geleert.

Damit bestehen **sechs eigene Formularabläufe**, einschließlich Rückfrage beim Dokumentverlassen und Erfolgsweg. Das Schließen eines echten Browserfensters wurde nicht separat ausgeführt; der verwendete `beforeunload`-Pfad wurde durch den Reload geprüft. Nachweise: die sechs `F3-*`-Fälle in [replay-results.json](review-evidence/task-030-pr-141/replay-results.json).

### F4 — zuvor P2: Neuanmeldung nach Abmeldung aus dem Pflichtwechsel — behoben

**Fundstelle:** `frontend/src/app/session.tsx:69–76`; angepasste Cookie-/CSRF-Nachbildung im Browser-Testbackend.

**Eigener Ablauf im englischen Chromium-Browser:** Ein neues synthetisches Konto mit dem vom echten Backend ausgegebenen Einmalpasswort anmelden. Auf „Choose your password“ abmelden und anschließend mit dem vollständigen Konto A anmelden, **ohne Seitenreload**. Die Anmeldung gelingt.

Die tatsächliche, bereinigte Anfragesequenz enthält `DELETE /session` → 204, danach `GET /session` → 200 zur anonymen CSRF-Initialisierung, anschließend `POST /session` → 200 und die Sitzungsabfrage. Kein 403 in dieser Sequenz. Hier wurde das echte Cookie-/CSRF-Verhalten nachgespielt, nicht die Nachbildung des Autors. Nachweis: Fall `F4` in [replay-results.json](review-evidence/task-030-pr-141/replay-results.json).

### F5 — zuvor P2: Rechtesperrzeitüberschreitung — behoben

**Fundstellen:** `backend/src/main/java/taskmanager/PermissionsController.java:55`, `:270–300`, `:330–348`; `PermissionsApiTest.java:241–272`.

`retryable` prüft die Ursachenkette auf die bereits berücksichtigte Spring-Ausnahme und die SQLSTATEs `55P03`, `40P01`, `40001`. Andere Laufzeit-/SQL-Fehler werden weitergereicht; es gibt keine pauschale 503-Umdeutung.

**Eigener Ablauf auf PostgreSQL 18.6:** Eine separate psql-Transaktion hält `permissions_revision FOR UPDATE` und schläft danach neun Sekunden. Der tatsächliche Halter wird vor dem HTTP-Aufruf über `pg_stat_activity` mit seinem eigenen `application_name` und `PgSleep` beobachtet. Ein berechtigter POST zur Gruppenanlage erhält nach **5,01 Sekunden 503 `permissions.busy`**, `Retry-After: 1` und eine Korrelationskennung. Die Antwort enthält weder SQLSTATE noch Stacktrace/Ausnahmetyp. Nach Freigabe ist keine blockierte Gruppe angelegt; ein neuer Anlageversuch gelingt mit 201.

Zusätzlich besteht der unveränderte neue Backend-Regressionstest zusammen mit allen sieben `PermissionsApiTest` auf derselben PostgreSQL-Version. Die weiteren SQLSTATE-Zweige für Deadlock/Serialisierungsfehler wurden gelesen, aber nicht zusätzlich als echte Datenbankfehler erzwungen. Nachweis: Fall `F5` in [replay-results.json](review-evidence/task-030-pr-141/replay-results.json).

### F6 — zuvor P3: Veraltete Verwaltungsnavigation — behoben

**Fundstellen:** `frontend/src/api/client.ts:94–124`, `frontend/src/app/session.tsx:40–50`, `TicketPage.tsx:135–139`.

403 außerhalb der Sitzungsabfrage stößt das Neuladen der gemeinsamen Sitzungsrechte an. Im abgewiesenen Ticketspeicherweg wird zusätzlich ausdrücklich auf `refresh()` gewartet, bevor die Meldung über die neu geladenen Rechte erscheint.

**Eigener Ablauf:** A öffnet das Ticket und ändert den Titel. B entzieht über die echte API `ticket.edit` und As Mitgliedschaft in der Unternehmensverwaltungsgruppe; B bleibt als zweite Verwaltung bestehen. Nach As verweigertem Speicherversuch enthält die frische Sitzung `rights: []`. Beim Erscheinen der Meldung „Ticket und Rechte sind neu geladen“ sind **Konten und Gruppen und Rechte bereits aus der Navigation verschwunden**, und der Titel ist nur noch lesbar. Der serverseitige Entzug bleibt wirksam.

Die bekannte zusätzliche Sitzungsabfrage auch bei erwarteten 403 ist eine begrenzte Mehrbelastung, keine neue fachliche Berechtigung. Keine neue vollständige Prüfung überlappender Antworten oder sämtlicher gleichzeitiger Entzugswege. Nachweis: Fall `F6` in [replay-results.json](review-evidence/task-030-pr-141/replay-results.json).

## Zusätzliche Prüfbeobachtung außerhalb der Nachbesserung

### H1 — P3: Bestehender Test setzt einen bestimmten Verbindungsfehler voraus

**Fundstelle:** `tests/test_demo_tool.py:106–116`, `LostAnswerTests.test_change_that_could_not_connect_stays_a_plain_error`. Die Datei ist in PR #141 unverändert; sie stammt aus der TASK-031-Nachbesserung.

Die Pflichtsuite umfasst hier 38 Tests: **36 bestanden, ein Fehler, eine Auslassung**. Der genannte Test erwartet bei einem geschlossenen lokalen Port exakt `ConnectionRefusedError`; in dieser Windows-Umgebung entsteht beim Verbindungsaufbau `TimeoutError`. Eine isolierte unveränderte Wiederholung ergibt denselben Fehler. Es geht um das Demo-Werkzeug, nicht um einen neuen Fehler des TASK-030-Backends oder der Oberfläche. Der Zeitzonentest wird mangels `time.tzset` ausgelassen.

Der gelesene [Repository-CI-Lauf am exakten PR-Kopf](https://github.com/1uedev/task-manager/actions/runs/39780621683) besteht auf Ubuntu mit 38 Tests einschließlich dieses Tests und ohne Auslassung. Das ersetzt den fehlgeschlagenen eigenen Lauf nicht. **Vorschlag:** Die erlaubten Fehler eines sicher vor dem Senden gescheiterten Verbindungsaufbaus portabel prüfen oder einen kontrollierten Verbindungsfehler injizieren; nicht jeden beliebigen Fehler als Erfolg des Tests werten. Keine Änderung an diesem Test im Review vorgenommen. Bereinigter Nachweis im [Prüfprotokoll](review-evidence/task-030-pr-141/verification.md).

## Tatsächlich ausgeführt und Grenzen

| Eigene Prüfung | Ergebnis |
|---|---|
| Unverändertes Backend, Java 25.0.4.1+1, PostgreSQL 18.6, Maven `-Dtest=PermissionsApiTest package` | 7 Tests, null Fehler/Auslassungen; Paketbau erfolgreich. Kein erneuter Lauf aller 208 Backendtests. |
| Frontend `npm ci`, `npm run verify` | Vertragstypen erzeugt, Typprüfung, 68 Unit-Tests und Produktionsbuild erfolgreich; erzeugte Quellen ohne Diff. |
| Unveränderte Chromium-Suite `npm run test:browser` | 67 Tests in zehn Suites, null Fehler/Auslassungen; gegen das Testbackend des Autors. |
| Eigener Chromium-Lauf gegen echtes JAR und PostgreSQL | 11/11 Gegenproben bestanden: F1, F2, sechs Formularfälle zu F3, F4, F5, F6. Keine Ersetzung von API-Antworten; bei F1/F2 nur Zustellung echter Antworten verzögert. |
| Repositorypflichtprüfungen | Validator, Aufgabenübersicht und `git diff --check` erfolgreich; Python-Suite mit H1 wie oben offengelegt. |

Das [ausführbare eigene Skript](review-evidence/task-030-pr-141/replay.mjs), [Ergebnisse](review-evidence/task-030-pr-141/replay-results.json) und das [Prüfprotokoll](review-evidence/task-030-pr-141/verification.md) sind beigefügt. Drei in diesem Lauf erstellte und angesehene Bilder bleiben lokal gesichert; ihre Übertragung wurde nicht abgeschlossen, sie sind nicht Bestandteil dieses PRs. Die eigenen Prüfroutinen wurden vor dem endgültigen Lauf bei Pflichtfeldselektoren, einer asynchronen Route und einem wiederverwendeten Testtitel korrigiert; diese Vorversuche waren keine Produktbefunde. Produktquellen und vorhandene Tests wurden nie verändert.

Nicht erneut ausgeführt: vollständige ursprüngliche API-Negativmatrix, sämtliche Versionskonflikt-/Sitzungsablauffälle, Image-/Compose-Lifecycle, Leistungsmessung oder weitere Browser-/Screenreaderprüfung. Die vorhandenen sieben Rechte-API-Tests decken zusätzlich die bisherigen Verwaltungsgrenzen ab; daraus wird keine erneute allgemeine Sicherheitsfreigabe abgeleitet.

## Übergabe

Die sechs ursprünglichen Befunde blockieren die technische Abnahme von TASK-030 nicht mehr. H1 bleibt ein gesonderter P3-Testportabilitätshinweis mit offenem eigenem Pflichtprüfergebnis; eine uneingeschränkt grüne Windows-Prüfung wird nicht behauptet. Die Entscheidung über die fachliche Abnahme und den Aufgabenstatus verbleibt bei Holger beziehungsweise der Koordination. TODO.md und DONE.md bleiben unverändert.
