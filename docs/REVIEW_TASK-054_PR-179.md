# Aktuelle unabhängige Nachprüfung TASK-054 / PR #179 und PR #180

Stand: **30. Oktober 2026**, aktuelles main beim Prüfstart **`3f3864366457038efd9345307b9ac81fda75af18`**. Codex, Sitzung `codex-20261030-task054-review-02`, eigene Prüfkopie und getrennte Kopie für Experimente. Keine Mitarbeit an der Produktimplementierung dieser Aufgabe; Prüfung nach DEC-040.

**Ergebnis: Ein neu nachgewiesener P2-Befund und ein P3-Befund.** Die regulären Prüfungen bestehen: **404 Backendtests, 77 Frontendtests und 128 Browsertests**. Ein gezieltes Experiment zeigt dennoch, dass der verspätete Abschluss eines alten Versuchs einen neueren Versuch desselben Ausführers überschreiben kann (F1). Ein Browserexperiment zeigt eine veraltete Liste nach einem Filterwechsel (F2). Vor einer erneuten positiven technischen Freigabe sollte F1 korrigiert werden.

## Grundlage und Einordnung

- Auftrag: unabhängige Prüfung des Ergebnisses [PR #179](https://github.com/1uedev/task-manager/pull/179), Merge `dc99440c4184e592e3c162e95404a6e5748c637f`, mit [Nachtrag #180](https://github.com/1uedev/task-manager/pull/180), Merge `23adfe3ab745ba9914d8681c388bf6ef00c51a3b`, auf **aktuellem main**.
- TASK-054 steht dort bereits in **DONE.md**, einschließlich seiner ursprünglichen Abnahmekriterien und Holgers dokumentierter Abnahme vom 28. Oktober. TODO.md enthält den Eintrag nicht mehr. Diese Prüfung benutzt deshalb den erhaltenen Eintrag in DONE.md; sie verändert weder TODO/DONE noch die historische Abnahme.
- Aktuelles main enthält die Korrekturen aus TASK-055/056 und spätere Erweiterungen wie `Jobs.active`. Die alten F1–F4 des Berichts vom 27. Oktober werden deshalb nicht erneut als offene Implementierungsfehler ausgegeben. Die neuen Befunde sind eigene Ergebnisse dieser Nachprüfung. Die für beide neuen Befunde maßgeblichen Codepfade waren bereits am Stand von PR #180 vorhanden.
- Gelesen: AGENTS.md, Aufgabeneintrag und Entscheidungen DEC-040/083, Architekturabschnitt „Zuverlässige Hintergrundarbeit“, Backend-README „Hintergrundaufträge“, Modul jobs einschließlich Ports, Executor, Repository, Retention, Administration und Controller, Migration V0025, jobs.openapi.yaml, Worker-Einstieg, Datei-Aufräumlauf, Rechtekatalog/AdministrationRight/identity.openapi.yaml, JobsPage mit de/en und Browsertests, compose.yaml, image-check.sh und lifecycle-check.sh sowie die zugehörigen Tests.
- Die bestätigte Rückstauanzeige in Übersicht und Protokoll wird auf Folgen geprüft. Ihre Aufnahme in die Bereitschaftsanzeige wird nicht neu zur Entscheidung gestellt.
- Der frühere Bericht unter diesem Pfad bleibt **unverändert als historischer Anhang** erhalten. Dessen Befundnummern, Zeilen und Testergebnisse gehören zum damaligen Stand.

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis dieser Sitzung |
|---|---|
| `python scripts/validate_repository.py` | erfolgreich; nach Fertigstellung des Berichts nochmals geprüft |
| `python scripts/task_board.py --check` | erfolgreich, Aufgabenübersicht unverändert |
| `python -m unittest discover -s tests -v` | **38 Tests erfolgreich, 1 Skip** für den Unix-spezifischen Zeitzonenwechsel; letzter Lauf mit UTF-8-Modus |
| Backend Maven `verify`, JDK 25.0.4.1, Maven 3.9.16, lokales PostgreSQL 18.6 | **404 Tests, 0 Failures, 0 Errors, 0 Skips, BUILD SUCCESS**, 9 Minuten |
| Darin `JobExecutorTest` / `JobsApiTest` / `JobsContractTest` | **23 / 7 / 1 erfolgreich** |
| Darin Dateien und Lebenszyklus | FilesApiTest 9, FilesContractTest 1, AttachmentServiceTest 6, FileSystemStorageTest 9, UploadRulesTest 4, ApplicationLifecycleTest 3 erfolgreich |
| Frontend `npm ci --offline`, `npm run verify`, Node 24.22.0 | Generierung, Typprüfung, **77 Tests** und Build erfolgreich |
| `npm run test:browser`, installiertes Chrome über Playwright | **128/128 erfolgreich**, 23 Suites, kein Skip; einschließlich Jobs-Oberfläche de/en |
| Zusatzexperimente E1/E2 | **2/2 Beobachtungstests erfolgreich**; E1 erwartet ausdrücklich den falschen Zustand aus F1 |
| E3: Browser mit gesteuerter Antwortreihenfolge | erwarteter Anzeigefehler reproduziert, Prozess erfolgreich beendet |
| E4: Original-JAR, Worker → harter Abbruch → Web → harter Abbruch → Worker | **1/1 erfolgreich**, beide bestätigten Aufträge bleiben erhalten und werden erledigt |
| E5: zwei unabhängige Worker-JVMs mit Original-JAR | **1/1 erfolgreich**, genau ein zusätzlicher Aufräumauftrag für die gemeinsam fällige Aufgabe |
| `git diff --check` und Prüfung der Änderungsliste | nur dieser Bericht geändert, keine Produkt-/Testdatei im Ergebnis-PR |

Der erste Python-Lauf hatte acht fehlgeschlagene Assertions wegen der Windows-Standardkodierung temporärer Testdateien. Der erneute Lauf mit `PYTHONUTF8=1` bestand. Ein erster Zusatztest-Anlauf kompilierte wegen einer mehrdeutigen Assertion im **eigenen Testcode** nicht; nach deren Korrektur bestanden die Experimente. Beide Vorläufe sind keine Produktfehler.

**Grenzen:** PostgreSQL lief lokal in Version 18.6, nicht im gepinnten Testcontainer. Docker ist installiert, der Linux-Daemon war jedoch nicht erreichbar. Deshalb weder `image-check.sh`, `lifecycle-check.sh` noch einen Compose-Stopp tatsächlich ausgeführt. Die Skripte wurden gelesen. E4/E5 benutzen das unveränderte gebaute JAR in echten Windows-JVMs, keine Container; E4 verwendet harte Prozessabbrüche, keinen SIGTERM-Nachweis. Geordneter Context-Stopp, letzter Versuch und Unterbrechung sind zusätzlich durch die vorhandenen Backendtests abgedeckt. Keine aktuellen CI-Ergebnisse werden als eigene Ausführung ausgegeben.

## Befunde der aktuellen Nachprüfung

### F1 · P2 · Ein alter Versuch desselben Ausführers kann den neueren Versuch als erledigt markieren

**Dateien/Zeilen:** `backend/src/main/java/taskmanager/jobs/internal/JobRepository.java:68–79` und `:101–134`; `JobExecutor.java:133`, `:287`, `:313`, `:333`, `:369`.

`lease_owner` identifiziert den Ausführer, nicht die einzelne Reservierung. Er bleibt über mehrere Versuche gleich. `succeed`, `fail` und `giveBack` prüfen nur Auftrags-ID, `running` und diesen Besitzer. Nach Ablauf und erneuter Übernahme **durch einen anderen Faden desselben Ausführers** können sie den alten und den neuen Versuch daher nicht unterscheiden.

**Nachstellung E1, echte Datenbank und echte Worker-Fäden:**

1. Ein Testbearbeiter hält Versuch 1 an einer Barriere fest.
2. Der Test stellt den gespeicherten Lease-Ablauf her und ruft die echte `releaseExpired`-Methode auf, während Versuch 1 weiterläuft.
3. Ein freier Faden desselben Ausführers übernimmt den Auftrag als Versuch 2; dieser Bearbeiter wartet an einer eigenen Barriere. Zwei überlappende Aufrufe werden nachgewiesen.
4. Versuch 1 wird freigegeben und endet erfolgreich. Der Auftrag wird **`succeeded`**, obwohl Versuch 2 weiterhin läuft.
5. Versuch 2 wird freigegeben und meldet einen endgültigen Fehler. Der Fehler kann nicht mehr gespeichert werden, weil Versuch 1 den Zustand bereits geändert hat.

Das ist eine kontrollierte Nachstellung des Zustands nach einem Reservierungsverlust, etwa nach vorübergehend ausgefallener Verlängerung. Es wurde keine reale Netzwerkstörung erzeugt. Der Kontrollversuch E2 bestätigt: Ein alter Abschluss mit **anderem** Besitzer wird korrekt zurückgewiesen. Die Lücke betrifft dieselbe Ausführerkennung.

**Auswirkung:** Der Betriebszustand kann einen noch laufenden beziehungsweise endgültig gescheiterten aktuellen Versuch als erfolgreich ausweisen. Ein verspäteter Fehler kann entsprechend einen neueren Lauf zurückstellen. Die Absicherung „nur der Besitzer seiner Reservierung schreibt“ ist für diesen Fall unvollständig. Zusätzlich arbeitet `active` nur mit Auftrags-IDs; das Ende des alten Versuchs entfernt damit auch den gemeinsamen Eintrag einer noch laufenden neuen Ausführung.

**Vorschlag:** Pro Übernahme eine eindeutige Reservierungskennung beziehungsweise eine nicht wiederverwendbare Generation erzeugen. Verlängerung, Erfolg, Fehlschlag und Rückgabe müssen diese Kennung atomar prüfen. Die lokalen laufenden Versuche ebenfalls getrennt führen, damit ein alter Abschluss nicht die Verlängerung des neuen entfernt. Regressionsfälle für beide Ergebnisreihenfolgen und erneutes Einstellen durch die Verwaltung ergänzen. Der zurückgesetzte Versuchszähler allein genügt nicht als dauerhaft eindeutige Generation.

### F2 · P3 · Eine verspätete Listenantwort überschreibt den neueren Zustandsfilter

**Datei/Zeilen:** `frontend/src/pages/JobsPage.tsx:39–53`.

Mehrere `load`-Aufrufe können gleichzeitig laufen. Jeder setzt die Liste nach seinem Abschluss, ohne zu prüfen, ob Filter beziehungsweise Laderunde noch aktuell sind.

**Nachstellung E3:** Produktionsbuild der echten Oberfläche im Chrome, API-Modell der vorhandenen Browsertests. Die Antwort auf „Gescheitert“ wird zurückgehalten. Danach wird „Abgebrochen“ ausgewählt und dessen korrekte Liste angezeigt. Anschließend wird die ältere Antwort freigegeben. Ergebnis: Auswahl weiterhin **Abgebrochen**, Tabelle enthält **Gescheitert** und bietet „Erneut einstellen“ an.

**Auswirkung:** Die Ansicht widerspricht der Auswahl. Es wurde keine Rechteumgehung nachgewiesen; beide Aufträge liegen im erlaubten Bestand der Person.

**Vorschlag:** Laderunden beziehungsweise Anfragekennungen vergleichen und veraltete Ergebnisse verwerfen; beim Filterwechsel laufende Anfragen gegebenenfalls abbrechen. Fehler, Ladezustand und Nachladen älterer Seiten derselben Runde zuordnen. Einen Browserfall mit umgekehrter Antwortreihenfolge ergänzen.

## Hinweise

### H1 – Gleichzeitige Ausführung und Exactly-once müssen eingeschränkt beschrieben werden

`SKIP LOCKED` verhindert eine doppelte **gleichzeitige Erstreservierung**. Nach Verlust einer Lease kann der alte Bearbeiter weiterlaufen, während ein neuer übernimmt. `renew` wertet verlorene Reservierungen nicht aus; ein Zeitlimit unterbricht kooperativ und beendet keinen Faden zwangsweise. Das war im früheren Bericht bereits als Grenze genannt.

Die absolute Aussage „Mehrere Ausführer führen denselben Auftrag nicht gleichzeitig aus“ ist deshalb zu weit. Der Vertrag C12 mit Wirkung unter stabiler Auftrags-ID bleibt notwendig; Exactly-once-Fachwirkung wird vom Modul nicht allgemein garantiert. F1 betrifft darüber hinaus die fehlerhafte Zuordnung des **Ergebnisses**, die auch ein idempotenter Bearbeiter nicht selbst repariert.

### H2 – Uhren, Wiederholungen und Retention bleiben Betriebsgrenzen

Reservierung, Verlängerung und Ablauf verwenden Prozessuhren. Eine ausreichende Uhrabweichung kann zur vorzeitigen Übernahme führen. Dedup-Schlüssel gelten bis zur Löschung des Auftrags und liefern auch einen bereits erledigten Auftrag zurück; nach dessen Aufbewahrung kann derselbe Schlüssel einen neuen Auftrag erzeugen. Wiederholung einer alten wiederkehrenden Aufgabe durch die Verwaltung prüft nicht auf einen neueren wartenden/laufenden Auftrag derselben Art. Diese Grenzen sind gegenüber dem früheren Bericht weiterhin erkennbar.

### H3 – Rechte, Unternehmensgrenze, Audit und Datenminimierung

Übersicht/Liste enthalten eigene und Installationsaufträge; fremde Unternehmensaufträge sind ausgeschlossen. Aktionen sperren nur sichtbare Aufträge und protokollieren Konto, Name, Auftrag und Operation in derselben Transaktion. Ohne `jobs.manage` antworten die vier gültigen Aufrufe mit 403; ohne Anmeldung 401. Katalog, Initialvergabe aus `AdministrationRight` und Identitätsvertrag enthalten das Recht. Die API- und Browsertests stützen dies.

Die Anforderung zu „keinen Nutzdaten in der Datenbank“ ist wörtlich nicht mit einer dauerhaften Queue vereinbar: `job.payload` speichert die notwendigen Referenznutzdaten. **Keine Fehlermeldungen oder geheimen Volltexte** sollen darin stehen. API und Oberfläche liefern das Payload nicht aus; Fehler bestehen dort nur aus Code und Referenz. Die Begrenzung auf 20 Werte à 200 Zeichen erkennt semantisch keine Geheimnisse. Diese Verantwortung bleibt beim internen Aufrufer; externe beliebige Aufträge werden hier nicht angeboten.

### H4 – Aufräumlauf, Bereitschaft und Dokumentation

Die Datei-Aufräumlogik selbst bleibt in `AttachmentCleanup`; `FilesCleanupTask` ruft sie auf. Die vorhandenen Dateitests bestehen. Neu gegenüber dem ursprünglichen Timer sind insbesondere der frühere erste Lauf, das Zeitlimit der wiederkehrenden Aufgabe und die Abhängigkeit von einem eingeschalteten Ausführer. Ohne Web-Ausführer und ohne eigenen Worker läuft kein Aufräumen.

Die akzeptierte Rückstauanzeige bleibt erhalten. Sie zählt zuletzt gesehene Prozesse, nicht garantiert funktionsfähige Bearbeiter, und die Worker-Gesundheitsprüfung prüft nur den lebenden Java-Prozess. Nach dem Ausfall aller Ausführer erkennen die API-Zähler den Rückstau; die Bereitschaft bleibt absichtlich davon getrennt.

Die README-Aussage, die Vorführumgebung betreibe zusätzlich einen Ausführer im Webprozess, widerspricht weiterhin `TASK_MANAGER_JOBS_WORKER: "false"` in Compose und dem genau einen Ausführer erwartenden Imagecheck. Auch der Kommentar in V0025 über einen Fälligkeitsschlüssel beschreibt nicht die aktuelle Implementierung: Diese verwendet die gesperrte Schedule-Zeile, Fortschreiben und `pending(kind)`. Die Abweichungen sind Dokumentationshinweise, kein Nachweis einer doppelten regulären Fälligkeit.

## Einzelabgleich der ursprünglichen Abnahmekriterien

Die Kriterien sind wortsinngleich aus dem auf main erhaltenen Eintrag in DONE.md übernommen.

| Nr. | Kriterium | Beleg und Bewertung |
|---|---|---|
| 1 | Zurückgerollter Auftrag existiert nicht | `MANDATORY`, atomarer Insert; `aJobOfARolledBackChangeDoesNotExistAndACommittedOneRunsOnce` und Verweigerung ohne Transaktion bestehen. **Erfüllt.** |
| 2 | Bestätigter Auftrag genau einmal erfolgreich, auch mit zwei Ausführern | Der vorhandene Test bearbeitet 150 Aufträge mit zwei Ausführern ohne Überlappung. **Im regulären Fall erfüllt; als absolute Zusage nicht erfüllt**, siehe F1/H1. |
| 3 | Abbruch während der Ausführung führt nach Lease-Ablauf zu einer weiteren Ausführung mit Wiederholungsnachweis | Vorhandener Effekt-Test erkennt die unter Auftrags-ID gespeicherte frühere Wirkung; E4 bestätigt reale Prozessabbrüche und Wiederaufnahme. **Für Restversuche erfüllt.** Der absichtlich verlorene letzte Versuch endet laut DEC-083 als `failed/job.lease_expired`; geordneter Stopp gibt den Versuch seit TASK-055/056 zurück. |
| 4 | Dauerfehler erreicht Höchstzahl, ist ohne Geheimnisse sichtbar und mit Recht erneut einstellbar | Executor-, API- und Browsertests bestehen, Audit wird mitgeschrieben. **Erfüllt im regulären Ablauf; F1 bleibt Ausnahme bei überlappenden Versuchen.** |
| 5 | Ohne jobs.manage weder Übersicht noch Liste noch Aktion, 403 | `withoutTheRightNothingIsVisibleOrChangeable` und Entzug beim nächsten Aufruf bestehen. **Erfüllt für gültige authentifizierte Anfragen.** |
| 6 | Wiederkehrende Aufgabe mit zwei Prozessen einmal je Fälligkeit | Schedule-Sperre/Commit im Code, vorhandener Zwei-Ausführer-Test und **E5 mit zwei unabhängigen Original-JAR-Workern**. **Für normale Fälligkeiten erfüllt**, administrative Wiederholung siehe H2. |
| 7 | Aufräumlauf verhält sich wie vorher, Tests grün | Files-Tests und `theFileCleanUpIsARecurringTaskOfTheExecutor` bestehen; E5 führt die echte wiederkehrende Dateiaufgabe aus. **Fachlogik erfüllt, Betriebsänderungen siehe H4.** |
| 8 | Neustart von Webprozess und Ausführer verliert keinen bestätigten Auftrag | ApplicationLifecycleTest sowie **E4 mit Worker/Web/Worker und harten Abbrüchen**. Beide bestätigten Aufträge vorhanden und erfolgreich, 2 beziehungsweise 1 Versuche. **Im nachgestellten Ablauf erfüllt**, keine Zusage unbegrenzter Wiederholung nach ausgeschöpftem Budget. |

## Zusatzexperimente und Aussagegrenzen

| Kennung | Durchführung | Beobachtung |
|---|---|---|
| E1 | `reviewOldAttemptOfSameExecutorOverwritesNewAttempt`, Testbearbeiter mit zwei Barrieren; Lease-Zustand kontrolliert abgelaufen, echte Freigabe und echte Worker-Fäden | Alter Erfolg setzt den laufenden neuen Versuch auf succeeded; dessen endgültiger Fehler bleibt unvermerkt |
| E2 | `reviewOldOwnerCannotFinishNewOwner`, echte claim/releaseExpired/succeed-Operationen | Unterschiedliche Besitzerkennungen: alter Abschluss zurückgewiesen |
| E3 | Vorhandene Browserhilfen und Produktionsbuild, ältere Filterantwort gezielt verzögert | Auswahl cancelled, sichtbarer Datensatz failed |
| E4 | `reviewPackagedWorkerAndWebRestartKeepCommittedJobs`, Original-JAR; Retention-Bearbeiter über eine externe Datenbanksperre angehalten, Worker hart beendet, Web übernimmt; danach Web hart beendet und weiterer Auftrag durch neuen Worker verarbeitet | Bestätigte Aufträge nicht verloren, wiederaufgenommener Auftrag mit Versuch 2 erfolgreich |
| E5 | `reviewTwoPackagedWorkersEnqueueOneDueRun`, zwei echte JVMs, gemeinsam fällige files.cleanup nach Bereitschaft beider Prozesse | Genau ein zusätzlicher Auftrag, erfolgreich abgeschlossen, beide Prozesse lebendig |

Die zusätzlichen Java-Beobachtungstests wurden ausschließlich in der getrennten Experimentkopie ergänzt. Sie und das lokale Browserskript werden nicht als Produktänderung ausgeliefert. Die vollständigen regulären Tests liefen vorher auf unverändertem Produkt- und Testcode. Insbesondere ist „Beobachtungstest erfolgreich“ bei E1/E3 kein bestandenes fachliches Abnahmekriterium.

## Empfehlung

**F1 vor einer erneuten technischen Freigabe beheben und mit beiden Reihenfolgen verspäteter Ergebnisse nachprüfen.** F2 unabhängig als kleine Korrektur der Betriebsoberfläche behandeln. Die bestehenden Grenzen zu kooperativen Zeitlimits, Uhrquelle, Wiederholung und Betriebsanzeige im Vertrag beziehungsweise in der Dokumentation präzise benennen; die bereits akzeptierte Entscheidung zur Bereitschaftsanzeige bleibt erhalten.

Die neue Prüfung erklärt keine Aufgabe für abgenommen und ändert keinen Status. Die frühere fachliche Abnahme in DONE.md bleibt als historische Entscheidung bestehen. Der Ergebnis-PR enthält ausschließlich diesen erweiterten Bericht.

---

# Historischer Anhang – Bericht vom 27. Oktober 2026

Der folgende frühere Bericht bleibt unverändert erhalten. Seine Testergebnisse und offenen Befunde beziehen sich auf seinen damaligen Prüfstand, nicht auf die oben dokumentierte Nachprüfung.

# Prüfung TASK-054 / PR #179 und PR #180

Stand: 27. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261027-review-054-01`, Branch `claude/project-thread-t9h650`, eigener Arbeitsbereich. Auftrag: Holgers Bitte vom 27. Oktober 2026, die getrennte Prüfung von TASK-054 statt Codex selbst vorzunehmen (DEC-040). Die Sitzung, die TASK-054 umgesetzt hat (`claude-20261027-jobs-01`), ist eine andere als diese. Prüfumfang und Schwerpunkte folgen dem Prüfauftrag, den die Umsetzungssitzung für Codex formuliert hat.

Ergebnis: **Die Grundlage trägt. Kein P1, ein Befund P2, drei Befunde P3, dazu acht Hinweise.** Einstellen in der Transaktion des Aufrufers, Abholen mit `FOR UPDATE SKIP LOCKED`, die Besitzerbedingung beim Abschluss, die Einmaligkeit wiederkehrender Aufgaben, die Unternehmensgrenze und das Fernhalten von Nutzdaten und Fehlermeldungen sind richtig umgesetzt und durch Tests belegt. Die Befunde betreffen Randfälle der Ausführung: ein Error im Bearbeiter beendet einen Ausführerfaden dauerhaft (F1), ein Stopp im letzten Versuch beendet einen Auftrag endgültig (F2), `repeated()` ist nach erneutem Einstellen falsch (F3), und die Vorführumgebung gibt dem Ausführer beim Stopp weniger Zeit, als er erwartet (F4).

## Grundlage und Abgrenzung

- Ergebnis: [PR #179](https://github.com/1uedev/task-manager/pull/179) auf main als `dc99440`, [PR #180](https://github.com/1uedev/task-manager/pull/180) (Bestätigung der Rückstauanzeige) als `23adfe3`. Prüfbasis ist `23adfe3`.
- Maßstab: AGENTS.md, TODO.md (TASK-054 mit Umfang und Abnahmekriterien), DEC-083, docs/ARCHITECTURE.md (Zuverlässige Hintergrundarbeit mit Nachtrag), docs/TECHNICAL_CONTRACTS.md (C12), backend/README.md (Hintergrundaufträge), `contracts/jobs.openapi.yaml`.
- Die von Holger bestätigte Abweichung (Rückstau in Übersicht und Protokoll statt in der Bereitschaftsanzeige) wurde nur auf Folgen geprüft und nicht neu bewertet.
- Diese Prüfung ändert keinen Produktcode, keinen Aufgabenstatus und erklärt keine Abnahme.

## Was gelaufen ist

| Prüfung | Ergebnis |
|---|---|
| Backend `mvnw verify` mit JDK 21 (`-Djava.version=21`) gegen PostgreSQL 16.15 | grün, 44 Testklassen, 335 Tests, keine Fehler, nichts übersprungen; darunter `JobExecutorTest` (15), `JobsApiTest`, `JobsContractTest`, die Rechte und Lebenszyklustests |
| `npm run verify` im Frontend | grün, Build erfolgreich |
| `npm run test:browser` mit `TASKMANAGER_BROWSER=/opt/pw-browsers/chromium` und `LC_ALL=C.UTF-8` | grün, 114 von 114 Tests, davon die neuen in `jobs.test.mjs` |
| Eigene Experimente E1 bis E3 gegen PostgreSQL mit echten Ausführern | alle drei bestätigen das beschriebene Verhalten, siehe [review-evidence/task-054](review-evidence/task-054/README.md) |
| Durchsicht von Code, Migration V0025, Vertrag, Oberfläche, compose.yaml, image-check.sh, lifecycle-check.sh | siehe Befunde und Abgleich |

Nicht gelaufen: JDK 25 und PostgreSQL 18.6 (prüft CI), `image-check.sh` und `lifecycle-check.sh` (kein Docker Build in dieser Sitzung), ein echter `docker compose stop` (F4 ist aus der Konfiguration abgeleitet). H4 und H5 sind aus dem Code abgeleitet und nicht nachgestellt.

## Befunde

### F1 · P2 · Ein Error im Bearbeiter beendet einen Ausführerfaden dauerhaft, der Ausführer meldet sich weiter als lebendig

**Datei:** `backend/src/main/java/taskmanager/jobs/internal/JobExecutor.java:280` (Weiterwerfen), `:216` (Schleife ohne Schutz).

`execute` vermerkt den Fehlschlag und wirft danach jeden `Error` außer `AssertionError` weiter. `work()` fängt ihn nicht, also endet der Faden `job-worker-n`. Er wird nicht ersetzt. Der Wartungsfaden schreibt weiter `job_worker.seen_at`, die Übersicht zählt den Ausführer als aktiv, und die Gesundheitsprüfung des Dienstes `worker` fragt nur, ob der Java Prozess lebt. Mit dem Standard von zwei Fäden genügen zwei solche Fehler, und der Prozess arbeitet keinen Auftrag mehr ab, bis jemand ihn neu startet. Sichtbar wird das erst nach 15 Minuten als Rückstau.

Auslöser sind keine exotischen Fälle: `StackOverflowError` bei tiefer Rekursion (etwa beim späteren Zerlegen einer Mail), `NoClassDefFoundError` oder `ExceptionInInitializerError` beim ersten Laden einer Klasse des Bearbeiters.

**Nachstellung E1:** ein Faden, ein Bearbeiter wirft `StackOverflowError`. Der Auftrag wird richtig als `failed` vermerkt, im Protokoll steht `Exception in thread "job-worker-1" java.lang.StackOverflowError`. Ein danach eingestellter Auftrag bleibt `queued`, `job_worker` zeigt den Ausführer weiter als gesehen.

**Vorschlag:** `Error` nach dem Vermerk nicht aus der Schleife fallen lassen. Entweder in `work()` fangen, protokollieren und weiterlaufen (nur `VirtualMachineError` wie `OutOfMemoryError` weiterwerfen und dann den Prozess bewusst beenden, damit `restart: unless-stopped` greift), oder einen beendeten Faden ersetzen. Dazu ein Test mit einem Faden wie E1.

### F2 · P3 · Ein Stopp während des letzten Versuchs beendet den Auftrag endgültig

**Datei:** `JobExecutor.java:301` bis `:312`; Beschreibung in backend/README.md, Abschnitt Hintergrundaufträge, „Prozesse“.

Die README sagt: „ein unterbrochener Versuch wird sofort wieder fällig (`job.interrupted`)“. Das gilt nur, solange Versuche übrig sind. `record` rechnet den abgebrochenen Versuch als normalen Versuch; ist es der letzte (`attempt >= maxAttempts`), wird der Auftrag `failed`. Ein geplanter Neustart (Update, `docker compose restart`) macht damit aus einem Auftrag, der gar nicht gescheitert ist, einen endgültig gescheiterten, der von Hand wieder eingestellt werden muss. Das Abnahmekriterium „Neustart von Webprozess und Ausführer verliert keinen bestätigten Auftrag“ ist dem Buchstaben nach erfüllt (der Auftrag bleibt sichtbar), dem Sinn nach nicht ganz. Für wiederkehrende Aufgaben mit einem Versuch ist es harmlos, weil der nächste Abstand ohnehin läuft. Gleiches gilt für eine abgelaufene Reservierung nach einem Absturz im letzten Versuch (`releaseExpired`); dort ist es gewollt und getestet, beim geordneten Stopp eher nicht.

**Nachstellung E2:** Auftrag mit einem Versuch, Bearbeiter schläft 60 s, `stop()` während er läuft. Der Stopp dauert 15,0 s, danach `state=failed, attempts=1, error_code=job.interrupted`.

**Vorschlag:** Einen durch den Stopp unterbrochenen Versuch nicht zählen, also in derselben Anweisung `attempts = attempts - 1` und `state = 'queued'` setzen (der Wiederholungsnachweis bleibt über die Kennung erhalten, siehe aber F3). Alternativ die README auf das tatsächliche Verhalten ändern. Holger sollte entscheiden, ob ein geordneter Neustart Versuche verbrauchen darf.

### F3 · P3 · `repeated()` sagt nach erneutem Einstellen „kein früherer Versuch“, obwohl einer lief

**Datei:** `backend/src/main/java/taskmanager/jobs/JobExecution.java:23`, `JobRepository.java:223` (`requeue` setzt `attempts = 0`).

`JobExecution.repeated()` ist dokumentiert als „Whether an earlier attempt of the same job ran, so its effect may already exist“ und wird in backend/README.md als Teil des Wiederholungsnachweises genannt. Es ist aber nur `attempt > 1`. Nach `POST /api/v1/jobs/{id}/retry` beginnt der Zähler wieder bei 0, der nächste Versuch hat `attempt = 1` und `repeated() = false`, obwohl frühere Versuche liefen und ihre Wirkung erzeugt haben können. Gerade gescheiterte Aufträge werden wieder eingestellt, und gerade bei ihnen ist eine Teilwirkung wahrscheinlich. Ein Bearbeiter, der sich auf `repeated()` verlässt, um die Prüfung auf eine vorhandene Wirkung zu sparen, erzeugt sie dann doppelt (TECHNICAL_CONTRACTS.md C12). Dasselbe gilt für einen abgebrochenen Auftrag, der vorher schon Versuche hatte.

**Nachstellung E3:** Bearbeiter schreibt `attempt` und `repeated()` mit, erzeugt im ersten Versuch seine Wirkung und scheitert dann. Nach `requeue` (genau das, was `JobAdministration.retry` tut) sieht der zweite Lauf `attempt=1, repeated=false`.

**Vorschlag:** Einen Gesamtzähler der Läufe führen, der beim erneuten Einstellen nicht zurückgesetzt wird (etwa eine Spalte `runs`), und `repeated()` daraus ableiten; alternativ `repeated()` streichen und in Javadoc und README nur die Kennung als Nachweis nennen. Bisher nutzt kein Bearbeiter `repeated()`, die Änderung ist jetzt billig.

### F4 · P3 · Die Vorführumgebung beendet den Ausführer, bevor seine Gnadenfrist abläuft

**Datei:** `compose.yaml` (Dienste `app` und `worker` ohne `stop_grace_period`), `JobExecutor.java:58` und `:190` (15 s plus 3 s).

Docker wartet nach SIGTERM standardmäßig 10 Sekunden und sendet dann SIGKILL. Der Ausführer wartet bis zu 15 Sekunden auf laufende Versuche und danach bis zu 3 weitere. Ein Versuch, der beim Stopp länger als etwa 10 Sekunden läuft, wird deshalb nicht wie beschrieben unterbrochen und sofort wieder fällig, sondern hart beendet: der Auftrag bleibt bis zum Ablauf der Reservierung (Standard 2 Minuten) `running`, der Ausführer meldet sich nicht ab und zählt in der Übersicht noch kurz als aktiv. Es geht nichts verloren (die Reservierung läuft ab), aber das in README und DEMO.md beschriebene geordnete Herunterfahren findet in genau der Umgebung, die es vorführen soll, nicht statt. `lifecycle-check.sh` prüft den Stopp mit 60 Sekunden außerhalb von Docker und bemerkt das nicht.

Abgeleitet aus Konfiguration und Code, nicht mit Docker nachgestellt.

**Vorschlag:** `stop_grace_period: 30s` für `worker` (und für `app`, solange dort ein Ausführer laufen kann), oder die Gnadenfrist konfigurierbar machen und unter die Wartezeit der Umgebung legen.

## Hinweise

- **H1 Zeitlimit nur kooperativ.** Ein Bearbeiter, der die Unterbrechung nicht beachtet (Rechenschleife, nicht unterbrechbarer Socket), läuft über sein Zeitlimit hinaus, und weil seine Kennung in `active` bleibt, verlängert der Ausführer die Reservierung unbegrenzt. Endet er doch normal, zählt der Versuch als erfolgreich. Bei einer wiederkehrenden Aufgabe blockiert ein solcher Lauf zudem alle weiteren Läufe (`pending`). Das ist eine Folge von Java und vertretbar, sollte aber in der Javadoc von `JobHandler` und `RecurringTask` stehen. Erwägenswert: nach Zeitlimit plus Frist die Verlängerung einstellen.
- **H2 Uhren verschiedener Prozesse.** `lease_until` setzt die Uhr des haltenden Prozesses, `releaseExpired` vergleicht mit der Uhr eines anderen. Bei Prozessen auf verschiedenen Rechnern mit Uhrabweichung über einem Drittel der Reservierung kann ein laufender Auftrag zu früh zurückgegeben werden. Mit `now()` der Datenbank für Reservierung und Ablauf entfällt das.
- **H3 Verlorene Reservierung wird nicht bemerkt.** `renew` liefert die Zahl der noch gehaltenen Aufträge, `JobExecutor.renew` wertet sie nicht aus. Ein Ausführer, dessen Reservierung nach einer längeren Datenbankstörung abgelaufen und neu vergeben ist, arbeitet weiter, parallel zum neuen Besitzer. Das Ergebnis schreibt richtig nur der neue (Besitzerbedingung), die Wirkung kann aber doppelt entstehen. C12 fängt das bei sauberen Bearbeitern ab; ein Protokolleintrag oder eine Unterbrechung des Bearbeiters wäre hilfreich.
- **H4 Erneutes Einstellen einer wiederkehrenden Aufgabe.** `retry` prüft nicht, ob von derselben Art schon ein Lauf wartet oder läuft. Wird ein gescheiterter Lauf von `recurring.files.cleanup` wieder eingestellt, während ein planmäßiger läuft, laufen zwei Aufräumläufe gleichzeitig, was `RecurringTask` sonst ausschließt. Aus dem Code abgeleitet. Vorschlag: für Arten mit `recurring.` dieselbe Prüfung wie bei der Fälligkeit, sonst 409.
- **H5 Reihenfolge beim Herunterfahren.** Bearbeiter und Aufgaben kommen über `ObjectProvider`, sind also keine Abhängigkeiten des Ausführers. Spring kann sie und ihre Abhängigkeiten deshalb vor `JobExecutor.stop()` zerstören, während Versuche in der Gnadenfrist noch laufen. Für den heutigen Aufräumlauf ohne Folgen, für spätere Bearbeiter mit eigenen Ressourcen (Mailverbindung) relevant. Aus dem Code abgeleitet; ein `SmartLifecycle` mit früher Phase statt `@PreDestroy` würde den Ausführer vor allen anderen Beans anhalten.
- **H6 Dokumentation weicht ab.** backend/README.md („Die Vorführumgebung startet beides: den Webserver mit Ausführer und zusätzlich den Dienst `worker`“) und der Umfang in TODO.md („startet zusätzlich einen getrennten Ausführer“) passen nicht zu compose.yaml, das im Dienst `app` `TASK_MANAGER_JOBS_WORKER: "false"` setzt; `image-check.sh` verlangt sogar genau einen Ausführer. Der Kommentar in V0025 sagt, die wiederkehrende Aufgabe werde „with the due time as its key“ eingestellt; der Code setzt keinen Schlüssel, die Einmaligkeit kommt aus der Zeilensperre (die richtig wirkt).
- **H7 Eindeutigkeitsschlüssel gilt bis zur Aufbewahrung.** Der Schlüssel bindet auch erledigte, gescheiterte und abgebrochene Aufträge, bis die Aufbewahrung sie löscht (7 oder 30 Tage). Ein späteres Einstellen mit demselben Schlüssel liefert still den alten, fertigen Auftrag. Das ist so dokumentiert und für Outbox Zwecke richtig, sollte aber für den ersten Verbraucher (Mail) ausdrücklich in der Javadoc von `NewJob.dedupKey` stehen.
- **H8 Aufräumlauf nicht ganz unverändert.** Neu sind ein Zeitlimit von 30 Minuten (vorher keins), der erste Lauf sofort statt nach 30 Sekunden und kein Aufräumen mehr, wenn der Webprozess `TASK_MANAGER_JOBS_WORKER=false` hat und kein Dienst `worker` läuft. Fachlich unkritisch, die Tests des Aufräumlaufs sind grün; der letzte Punkt sollte in der Betriebsbeschreibung als Warnung stehen.

## Schwerpunkte des Prüfauftrags

| Schwerpunkt | Ergebnis |
|---|---|
| 1. Einstellen nur in der Transaktion des Aufrufers, Eindeutigkeitsschlüssel | **Erfüllt.** `@Transactional(propagation = MANDATORY)`, zurückgerollte Aufträge existieren nicht (Test). Der Schlüssel ist ein Teilindex über Unternehmen (leer als Nullkennung), Art und Schlüssel, `on conflict do nothing` mit anschließendem Lesen; in READ COMMITTED sieht das Lesen die konkurrierend bestätigte Zeile. Siehe H7. |
| 2. `FOR UPDATE SKIP LOCKED`, nur der Besitzer schreibt | **Erfüllt.** Abholen in einer Anweisung (Unterabfrage mit `SKIP LOCKED`, `UPDATE … RETURNING`), Abschluss und Fehlschlag nur mit `state = 'running' and lease_owner = ?`. Zwei Ausführer mit 150 Aufträgen ohne Überschneidung (Test). |
| 3. Reservierung, letzter Versuch, Zeitlimit, Abstände | **Im Wesentlichen erfüllt.** Verlängerung alle Drittel der Reservierung, Ablauf gibt mit Restversuchen sofort zurück, sonst `failed`; Zeitlimit unterbricht (Test); Abstände 30 s verdoppelt bis 1 h, Höchstzahl 1 bis 100. Siehe F2, H1 bis H3. |
| 4. Wiederkehrende Aufgaben einmal je Fälligkeit, Aufräumlauf | **Erfüllt.** Zeilensperre mit `SKIP LOCKED` und Fortschreiben in derselben Transaktion, kein zweiter Lauf, solange einer wartet (Test mit zwei Ausführern). Siehe H4, H8. |
| 5. Unternehmensgrenze, 403 ohne `jobs.manage`, Audit | **Erfüllt.** Alle Abfragen mit `company_id is null or company_id = ?`, fremde Aufträge 404, Recht in der Transaktion zuerst geprüft und am Endpunkt vorab, entzogenes Recht wirkt mit der nächsten Anfrage, `job_audit_event` mit Konto und Name (Tests). |
| 6. Keine Nutzdaten oder Meldungen nach außen | **Erfüllt.** `Job` und `JobBody` enthalten keine Nutzdaten, die Tabelle nur Code und Referenz; ein Test prüft die ganze Zeile auf den Meldungstext. Die Oberfläche zeigt Code (übersetzt, wenn bekannt) und Referenz. Nutzdaten sind auf 20 kurze Einträge begrenzt. |
| 7. Herunterfahren und Neustart | **Mit Einschränkung.** Ein bestätigter Auftrag geht nicht verloren (Tabelle, abgelaufene Reservierung wird zurückgegeben, `lifecycle-check.sh` prüft den Stopp des `worker`). Siehe F2 und F4. Die bestätigte Abweichung bei der Bereitschaftsanzeige hat keine Folgen über das Beschriebene hinaus; die Übersicht erkennt auch „Aufträge warten, kein Ausführer“. Sie erkennt aber nicht F1. |

## Abgleich der Abnahmekriterien

| Kriterium aus TODO.md | Beleg | Ergebnis |
|---|---|---|
| Auftrag einer zurückgerollten Transaktion existiert nicht | `aJobOfARolledBackChangeDoesNotExistAndACommittedOneRunsOnce` | erfüllt |
| Genau einmal erfolgreich, auch mit zwei Ausführern | `twoExecutorsRunEveryJobExactlyOnce` | erfüllt |
| Abbruch während der Ausführung: nach Ablauf genau eine weitere Ausführung mit Wiederholungsnachweis | `aJobOfAnExecutorThatEndedRunsOnceMoreAndSeesItsEarlierEffect` | erfüllt, solange es nicht der letzte Versuch war (`anExpiredLeaseOnTheLastAttemptFailsTheJob`); siehe F3 für `repeated()` |
| Immer scheiternder Auftrag wird `failed`, sichtbar mit Code ohne Geheimnisse, mit `jobs.manage` wieder einstellbar | `aJobThatAlwaysFailsEnds…`, `JobsApiTest`, Browsertests | erfüllt |
| Ohne `jobs.manage` 403 für Übersicht, Liste, Aktionen | `withoutTheRightNothingIsVisibleOrChangeable`, `aWithdrawnRightIsSeenByTheNextRequest` | erfüllt |
| Wiederkehrende Aufgabe mit zwei Prozessen einmal je Fälligkeit | `aDueRecurringTaskIsEnqueuedOnce…` (zwei Ausführer in einem Prozess, gleiche Datenbank) | erfüllt; mit zwei echten Prozessen nur über `image-check.sh` in CI |
| Aufräumlauf verhält sich wie vorher, Tests grün | `theFileCleanUpIsARecurringTaskOfTheExecutor`, Tests von `files` grün | erfüllt mit den Abweichungen aus H8 |
| Neustart verliert keinen bestätigten Auftrag | Tabelle als Speicher, `lifecycle-check.sh` | erfüllt im Sinne von „nicht verloren“; F2 und F4 betreffen das Wie |

## Empfehlung

F1 vor dem ersten fachlichen Verbraucher (Mail, A9) beheben, F2 bis F4 im selben kleinen Korrekturauftrag. Für F2 braucht es Holgers Entscheidung, ob ein geordneter Neustart Versuche verbrauchen darf; empfohlen ist: nein. Die Hinweise sind keine Voraussetzung der Abnahme.
