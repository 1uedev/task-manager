# Nachprüfung TASK-053 / PR #175 und PR #176

Stand: 27. Oktober 2026. Prüfer: Claude, Sitzung `claude-20261027-review-053-01`, Branch `claude/project-thread-qgotlh`, eigener Arbeitsbereich. Auftrag: Holgers Zustimmung vom 27. Oktober 2026 zur getrennten Nachprüfung von TASK-053 (DEC-040). Die Sitzung, die TASK-053 umgesetzt hat (`claude-20261026-attachfix-01`), ist eine andere als diese.

Geprüft wurde die Behebung der Befunde F1 bis F8 aus dem [Prüfbericht zu TASK-051](REVIEW_TASK-051_PR-168.md) und N1 und N2 aus der [Nachprüfung von TASK-052](REVIEW_TASK-052_PR-172.md) gegen den Umfang von TASK-053 in TODO.md und DEC-082 samt Nachtrag (Makrodokumente erlaubt, mit Warnhinweis). Ergebnis: **Alle zehn Befunde sind behoben. Ein neuer Befund P3, kein P1 oder P2.** Alle vorhandenen Tests sind grün, die eigenen Proben gegen den laufenden Server bestätigen die Behebung.

## Grundlage und Abgrenzung

- Ergebnis: [PR #175](https://github.com/1uedev/task-manager/pull/175) auf main als `2bc252a`, [PR #176](https://github.com/1uedev/task-manager/pull/176) (Warnhinweis für Makrodokumente) auf main als `cf08a67`. Prüfbasis ist `cf08a67`.
- Maßstab: AGENTS.md, TODO.md (TASK-053 und der angeglichene Umfang von TASK-051), DEC-080, DEC-082 mit Nachtrag, backend/README.md, docs/DEMO.md, die Verträge `files` und `tickets`.
- Die Hinweise H1 bis H8 des Berichts zu TASK-051 gehören nicht zu TASK-053 und werden hier nicht erneut gezählt.
- Diese Prüfung ändert keinen Produktcode, keinen Aufgabenstatus und erklärt keine Abnahme.

## Was gelaufen ist

| Prüfung | Ergebnis |
|---|---|
| `npm run verify` im Frontend | grün, 77 von 77 Tests, Build erfolgreich |
| `npm run test:browser` mit `TASKMANAGER_BROWSER=/opt/pw-browsers/chromium` und `LC_ALL=C.UTF-8` | grün, 107 von 107 Tests |
| Backend `mvn verify` mit JDK 25 gegen PostgreSQL 18.6 (Testcontainers) | grün, 311 Tests, keine Fehler, nichts übersprungen |
| Vorführumgebung aus compose.yaml (Image mit derselben Laufzeitstufe wie das Dockerfile, JAR mit JDK 25 gebaut) | `bootstrap-admin`, `demo seed --profile demo`, `demo verify --save` grün |
| Eigene Proben gegen den laufenden Server (API) | siehe unten, Ausgaben in [review-evidence/task-053](review-evidence/task-053/results.txt) |
| Eigene Browserprobe mit Chromium gegen den echten Server, nicht gegen die Testattrappe | siehe unten |
| Sicherung und Rücksicherung Schritt für Schritt nach docs/DEMO.md | gelungen, siehe F2 |

Nicht gelaufen: `image-check.sh` und `lifecycle-check.sh` vollständig (der Maven Schritt im Docker Build hat in dieser Umgebung kein Netz), andere Browser als Chromium. N1 und N2 wurden über die neuen Tests und den Code geprüft, nicht zusätzlich gegen den laufenden Server.

## Befunde aus TASK-051 und TASK-052

| Befund | Ergebnis | Nachweis |
|---|---|---|
| F1 · P2 Schreibrecht für Anhänge aus anderen Feldern | **Behoben.** `writableFields` nennt `tags` und `attachments` genau nach ihrer eigenen Schreibregel unter `ticket.edit`, in Liste, Einzelabruf und den Antworten auf Änderungen. Die Oberfläche bietet Hochladen, Entfernen und Tagänderungen nur danach an. Da im Rechtemodul jede Schreibregel die Leseregel voraussetzt, folgt die Attrappe dem Server, wenn sie beides verlangt. | probe1: Gruppe nur mit Schreibregel für Anhänge sieht `['attachments']`, Hochladen 201, Entfernen 204, Tag 403. Gruppe mit festen Feldern ohne Anhänge: keine Nennung, Hochladen 403. Gruppe nur mit Tags: `['tags']`, Tag 204, Hochladen 403. `FilesApiTest`, Browsertest. |
| F2 · P2 Hinweise zur Sicherung | **Behoben.** Reihenfolge, Verhalten des Aufräumlaufs nach einer Rücksicherung, Zurückholen aus der Quarantäne, entfernte Anhänge und eigenes Verzeichnis stehen in backend/README.md und stimmen mit dem Code überein (Inhalt bekommt beim Ablegen und beim Verschieben in die Quarantäne die aktuelle Zeit, `tar` erhält sie). | Befehle aus DEMO.md wörtlich ausgeführt: Sicherung, ein weiterer Anhang, Rücksicherung. Danach liefern die gesicherten Anhänge dieselben Bytes, der spätere ist nicht mehr gelistet, sein Inhalt liegt als Inhalt ohne Zeile mit Besitzer 10001 im Speicher, wie beschrieben. |
| F3 · P3 mehrere Dateien | **Behoben.** Alle Dateien werden versucht, eine Meldung fasst zusammen. | Chromium gegen den Server: „2 von 3 Dateien angehängt (eins.txt, drei.docm). zwei.txt: Dieser Dateityp ist nicht erlaubt. …“ |
| F4 · P3 misslungener Download | **Behoben.** Vor dem Download fragt die Oberfläche mit HEAD, bei 404, 403 und 503 erscheint der Grund, bei 401 endet die Anmeldung wie bei jedem anderen Aufruf (`withSession`). Der Download bleibt ein Link; nach erfolgreicher Frage löst er im echten Chromium den Download aus, auch mit der Eingabetaste. | HEAD 200 ohne Körper mit denselben Köpfen, HEAD bei fehlendem Inhalt 404 mit Warnung im Protokoll, HEAD nach dem Abmelden 401. ui-probe: Download nach 103 ms, genau eine HEAD Anfrage. Siehe aber **R1**. |
| F5 · P3 Sperrliste | **Behoben wie vorgeschlagen.** Alle im Bericht genannten Endungen und `img`, `vhdx`, `jnlp` werden mit 415 abgewiesen, Makrodokumente bleiben nach Holgers Entscheidung erlaubt und tragen in der Liste einen Warnhinweis (PR #176). | probe2: zwölf Endungen 415, `docm` und `xlsm` 201; Browsertest und ui-probe zeigen genau einen Warnhinweis bei `drei.docm`. |
| F6 · P3 TODO.md | **Behoben.** Umfang 1, 3 und 5 von TASK-051 stimmen jetzt mit DEC-080 und dem Code überein (Ablage unter der Kennung, kein Scanner Port, Löschen nach 7 Tagen). | Abgleich mit `FileSystemStorage` und `AttachmentCleanup`. |
| F7 · P3 Protokoll und Abbruch | **Behoben.** Speicherfehler stehen als Warnung mit Schritt, Ursache und Korrelationskennung im Protokoll, ein Abbruch der Übertragung ergibt 400 `attachment.incomplete` und eine Information im Protokoll, ohne Rest in `staging/`. | probe2: nicht beschreibbares `staging/` ergibt 503 und die Warnung mit derselben Korrelationskennung wie die Antwort; ein nach 200 kB abgebrochener Upload von angekündigten 5 MB erscheint als `ClientAbortException`, `staging/` bleibt leer. |
| F8 · P3 Grenze und Umgebungsvariablen | **Behoben.** Die Liste nennt die Grenze im Kopf `X-Attachment-Max-Bytes`, die Oberfläche zeigt sie, die Umgebungsvariablen stehen in der README. | probe5 mit `TASKMANAGER_FILES_MAX_BYTES=1000`: Kopf 1000, 1000 Bytes 201, 1001 Bytes 413. `TASKMANAGER_FILES_BLOCKED_EXTENSIONS=exe,docm` ersetzt die ganze Liste (`docm` 415, `html` 201), wie die README warnt. |
| N1 · P3 mitgegebener bisheriger Status beim Typwechsel | **Behoben.** Ein beim Typwechsel mitgeschickter Status zählt als verlangt, auch wenn es der bisherige ist, und muss im neuen Ablauf vorkommen (400 `status.unavailable`). Ein Übergang wird nur geprüft, wenn sich der Status ändert. Die eigene Oberfläche schickt weiter nur geänderte Felder und ist davon nicht betroffen. | `TicketService.java:312`, Regression `aStatusSentWithTheNewTypeMustExistInItsWorkflowEvenWhenItIsTheCurrentOne`, Vertrag angepasst. |
| N2 · P3 Zuordnung eines wegfallenden Typs | **Behoben.** `mappingRows` zählt wegfallende Typen mit dem Ablauf ihres gewählten Zieltyps und behält ihren eigenen Schlüssel `<typ>:<status>`; Typen, die im selben Ablauf landen, teilen eine Zeile. Wechselt die Zielwahl, wird eine ungültig gewordene Statuswahl ersetzt. | Unittest in `configuration.test.ts`, Browsertest „tickets of a dropped type get their own status choice …“; die Attrappe prüft die Zuordnung jetzt wie der Server. |

## Neuer Befund

### R1 · P3: Die Frage mit HEAD liest auf dem Server den ganzen Inhalt

**Fundstellen:** [AttachmentController.java:95](https://github.com/1uedev/task-manager/blob/cf08a67/backend/src/main/java/taskmanager/files/web/AttachmentController.java#L95), [product.ts:277](https://github.com/1uedev/task-manager/blob/cf08a67/frontend/src/api/product.ts#L277).

Für HEAD gibt es keine eigene Abbildung. Spring führt dafür die GET Methode aus, öffnet den Inhalt und kopiert ihn in eine Antwort ohne Körper. Gemessen an den gelesenen Bytes des Java Prozesses: ein HEAD auf einen Anhang von 20 MB liest 20.005.241 Bytes, ein GET 20.005.272 (probe3). Seit F4 geht jedem Download in der Oberfläche ein HEAD voraus, also liest der Server jeden Anhang zweimal und hält dafür zweimal eine Anfrage offen.

**Auswirkung:** Gering. Die Antwort ist richtig und die Grenze liegt bei 25 MB; es kostet doppelte Last auf dem Dateispeicher und Zeit vor dem Beginn des Downloads, die mit der Größe wächst (lokal 103 ms für eine kleine Datei). Auf einem langsamen Netzlaufwerk wird es spürbar.

**Vorschlag:** HEAD ausdrücklich abbilden und nur Rechte, Zeile und Vorhandensein des Inhalts prüfen (etwa `Files.isReadable` im Speicher), ohne den Strom zu öffnen; Regression mit einem Speicher, der beim Öffnen fehlschlägt. Alternativ den HEAD Abruf auf ein eigenes leichtes Prüfergebnis umstellen.

## Hinweise ohne Befund

- **H1 Testabdeckung:** Dass `writableFields` `tags` nennt, prüft nur der Browsertest gegen die Attrappe; `FilesApiTest` prüft nur `attachments`. `attachment.incomplete` ist nur mit einem nachgebildeten Speicher getestet, nicht über HTTP. Beides ist mit probe1 und probe2 gegen den echten Server bestätigt; eine Servertest Zeile je Fall würde es festhalten.
- **H2 Sperrliste:** Weiter angenommen werden unter anderem `appref-ms`, `settingcontent-ms`, `library-ms`, `search-ms`, `scf`, `inf`, `wsc`, `msp`, `mst`, `xll` (Excel Add-in, ausführbarer Code), `gadget`, `application`, `xbap`, `msix`, `appx`, `hlp` und `cab`. F5 ist damit erfüllt, DEC-080 nennt die Liste eine Hürde und die Liste ist einstellbar. Ob weitere Windows Typen dazu sollen, ist eine Entscheidung, kein Fehler.
- **H3 Makrohinweis:** Der Warnhinweis hängt an der Endung. Ein Makrodokument, das als `.docx` umbenannt wurde, öffnet Office ohnehin nicht mit Makros; der Hinweis ist also für den gewöhnlichen Fall richtig.
- **H4 DEMO.md:** „aus dem Verzeichnis dieser Datei heraus“ meint das Verzeichnis von compose.yaml (Wurzel des Repositorys), nicht `docs/`, in dem DEMO.md liegt. Compose findet die Datei zwar auch aus `docs/`, Sicherungsdateien landen dann aber dort. Besser „aus dem Verzeichnis von compose.yaml“.
- **H5 Doppelklick:** Ein schneller Doppelklick auf einen Anhang löst zwei Downloads aus. Das tut ein gewöhnlicher Link ebenso; kein Befund.

## Bewertung

TASK-053 erfüllt seinen Umfang nach DEC-082 und dem Nachtrag. R1 ändert kein Ergebnis und keine Rechteprüfung und kann bei einer späteren Arbeit an den Anhängen mitgenommen werden. Aus technischer Sicht steht der Abnahme von TASK-044 bis TASK-053 durch Holger nichts entgegen.
