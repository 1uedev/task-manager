# Unabhängige Prüfung TASK-051 / PR #168

Stand: 26. Oktober 2026. Prüfer: Claude, eigene Prüfsitzung („Claude prüft“ als Ersatz für eine Codex Prüfung nach DEC-040), Branch `claude/project-thread-0amt42`, eigener Arbeitsbereich. Der Bericht ändert keinen Code und keinen Aufgabenstatus.

Geprüft wurden die Dateien und Anhänge an Tickets nach DEC-080 in den vier Bereichen, die Holger am 26. Oktober 2026 vorgegeben hat: Auslieferung, Oberfläche, Betrieb und Dokumentation. Ergebnis: **zwei Befunde P2, sechs Befunde P3 und acht Hinweise**. Ein Weg, eine gespeicherte Datei im Browser anzeigen oder ausführen zu lassen, wurde nicht gefunden. Ein Anhang übersteht den Austausch des Containers. Alle vorhandenen Tests sind grün.

## Grundlage und Abgrenzung

- Ergebnis: [PR #168](https://github.com/1uedev/task-manager/pull/168), Head `7bf8488`, auf main als `37580d0` (Prüfbasis).
- Maßstab: AGENTS.md, CLAUDE.md, TODO.md (TASK-051), DEC-080, backend/README.md (Abschnitt TASK-051), docs/DEMO.md, docs/OPERATIONS.md, contracts/files.openapi.yaml.
- Nicht Teil der Prüfung: Virenscanner, Objektspeicher, Kontingente und alles, was DEC-080 Punkt 7 ausdrücklich ausnimmt. Der Nachweis einer konsistenten Sicherung gehört nach DEC-080 zu A18; hier wird nur geprüft, ob die vorhandenen Hinweise stimmen.

## Was gelaufen ist

| Prüfung | Ergebnis |
|---|---|
| `npm run verify` im Frontend | grün, 75 von 75 Tests, Build erfolgreich |
| `npm run test:browser` mit `TASKMANAGER_BROWSER=/opt/pw-browsers/chromium` | grün, 102 von 102 Tests (davon 7 in attachments.test.mjs) |
| Backend: `FilesApiTest`, `FilesContractTest`, `AttachmentServiceTest`, `FileSystemStorageTest`, `UploadRulesTest`, `ModuleStructureTest` gegen PostgreSQL 18.6 (Testcontainers) | grün, 29 von 29 |
| Vorführumgebung mit compose.yaml (Image mit derselben Laufzeitstufe wie das Dockerfile, JAR mit JDK 25 gebaut, weil die Maven Stufe im Docker Build dieser Umgebung keinen Netzzugang hatte) | `bootstrap-admin`, `demo seed`, `demo verify --save` grün |
| `docker compose down`, `docker compose up -d`, `demo verify --resume` | grün, darunter „the attachment is still there after the restart, byte for byte“ |
| Eigene Proben gegen den laufenden Server: Antwortköpfe, 29 ungewöhnliche Dateinamen, Range, HEAD, 30 MB mit Länge und mit chunked Übertragung | Ergebnisse unten |
| Eigene Browserproben mit Chromium gegen den echten Server (nicht gegen die Testattrappe): 25,5 MB und 300 MB, drei Dateien nacheinander, nicht beschreibbarer Speicher (503) in de und en, Herunterladen mit Umlauten, Herunterladen bei fehlendem Inhalt, Fokus beim Entfernen, Historienzeilen | Ergebnisse unten |

Nicht gelaufen: der vollständige `image-check.sh` und `lifecycle-check.sh` (der Image Bau mit Maven scheitert in dieser Umgebung am Netz; die Schritte von `image-check.sh` nach dem Bau wurden von Hand nachgestellt), andere Browser als Chromium, andere Dateisysteme als overlay und ext4 des Containers.

## 1. Auslieferung

Antwort des Servers auf `GET …/attachments/{id}` (gemessen):

```
Content-Disposition: attachment; filename="Pruefbericht.txt"; filename*=UTF-8''Pr%C3%BCfbericht.txt
X-Content-Type-Options: nosniff
Content-Security-Policy: default-src 'none'; sandbox
Cache-Control: no-store, private
Content-Type: application/octet-stream
Referrer-Policy: no-referrer
Cross-Origin-Opener-Policy: same-origin
```

- **Anzeigen oder Ausführen im Browser:** nicht möglich gefunden. `attachment` mit `application/octet-stream` und `nosniff` lässt Chromium die Datei immer speichern, auch beim direkten Aufruf der Adresse ohne `download` Attribut. Würde ein Browser sie doch darstellen, verbietet die Policy mit `sandbox` jedes Skript und jeden Zugriff auf den Ursprung der Anwendung.
- **Ungewöhnliche Namen:** Steuerzeichen werden beim Hochladen entfernt (`x\r\nSet-Cookie: evil=1.txt` wird `xSet-Cookie: evil=1.txt`), damit ist keine Kopfzeile einschleusbar. Anführungszeichen werden im Fallback maskiert (`filename="a\"b.txt"`), `;` und `=` stehen innerhalb der Anführung beziehungsweise prozentkodiert in `filename*`. Schreibrichtungszeichen werden entfernt, `rechnung‮txt.exe` wird abgewiesen. Pfadanteile mit `/` und `\` werden abgeschnitten. Nicht ASCII Namen kommen in `filename*` korrekt an; Chromium speichert `Größe.txt`, `Café.txt` und `日本.txt` unter ihrem Namen, `x.exe::$DATA` als `x.exe__$DATA`.
- **Range und HEAD:** Ein `Range` Kopf wird ignoriert (200 mit vollem Inhalt), HEAD liefert dieselben Köpfe ohne Körper.
- **Grenze:** Eine Sperrliste ist eine Hürde, das sagt DEC-080 selbst. Sie lässt aber Typen durch, die DEC-080 als „aktive Webinhalte“ ausschließen will, siehe F5.

## 2. Oberfläche und Browsertest

Fehlerfälle gegen den echten Server: 25,5 MB und 300 MB ergeben „Die Datei ist zu groß.“ (413 kommt im Browser an, die Verbindung wird nicht vorher abgebrochen). Ein nicht beschreibbarer Speicher ergibt „Der Dateispeicher ist gerade nicht erreichbar …“ und „The file store cannot be reached right now …“. Programm und leere Datei werden mit ihrem Grund gemeldet. Die Historienzeile passt zum Format des Servers: der Server schreibt `{"id","name","size","sha256"}` als JSON Text, die Oberfläche zeigt „Anhang Größe.txt hinzugefügt (14 B)“ und „Anhang … entfernt“; ohne Leseregel für `attachments` liefert der Server die Einträge nicht aus (FilesApiTest) und die Oberfläche blendet den Abschnitt aus.

Zur Frage nach der Testattrappe: `attachments.test.mjs` läuft ausschließlich gegen `backend.mjs`, nicht gegen den Server. Die Attrappe folgt dem Vertrag in den getesteten Fällen, weicht aber in Punkten ab, die der Test damit nicht absichert:

| Punkt | Attrappe | Server |
|---|---|---|
| Gesperrte Endungen | nur `exe`, `bat`, `js`, `sh`, `html` | 29 Endungen und Prüfung der ersten Bytes |
| Namensbereinigung | nur Pfadtrenner | NFC, Steuer und Formatzeichen, Punkte und Leerzeichen am Ende, höchstens 255 Zeichen |
| Antwortköpfe des Downloads | nur `filename*`, kein `nosniff`, keine Policy | wie oben |
| Größengrenze | 1000 Bytes | 25.000.000 Bytes, Ablehnung schon an der angegebenen Länge |
| 503 (`attachment.storage_unavailable`, `attachment.busy`) | gibt es nicht | gibt es |
| `writableFields` | ohne `attachments` | ohne `attachments` (gleich, siehe F1) |

Die Köpfe und die Regeln des Servers sind in `FilesApiTest`, `UploadRulesTest` und `demo verify` abgedeckt, nicht im Browsertest. Für 503, mehrere Dateien und den Entzug des Rechts während der Arbeit gibt es keinen Browsertest; die Proben oben ersetzen ihn für diese Prüfung.

## 3. Betrieb

- **Austausch des Containers:** bestätigt. compose.yaml bindet das benannte Volume `files-data` an `/data/files`, das Dockerfile legt das Verzeichnis mit Besitzer 10001 an, ein leeres benanntes Volume übernimmt diesen Besitzer (gemessen: `content`, `staging`, `quarantine` gehören `taskmanager`). Nach `down` und `up` lieferte `verify --resume` denselben Anhang Byte für Byte.
- **lifecycle-check.sh:** prüft 24 Migrationen, die neuen Tabellen, das Anlegen der Unterverzeichnisse und die Ablehnung des Starts ohne `TASK_MANAGER_FILES_DIR`. Nicht selbst ausgeführt (siehe oben); der Inhalt ist schlüssig.
- **demo.py und DEMO.md:** Hochladen, Herunterladen, abgewiesenes Programm, fehlende Leseregel und der Erhalt nach dem Neustart stimmen mit dem Server überein (alle Prüfungen grün). DEMO.md Schritt 8 beschreibt den Ablauf zutreffend.
- **Sicherung und Wiederherstellung in backend/README.md:** zutreffend, aber nicht vollständig, siehe F2.

## 4. Dokumentation

DEC-080, AGENTS.md und CLAUDE.md stimmen untereinander und mit dem Code überein. TODO.md (Umfang von TASK-051) verspricht zwei Dinge, die der Code nicht einlöst und denen DEC-080 und README widersprechen, siehe F6. Die Abnahmeaussage „die Rechte gelten in Oberfläche, API und Historie gleich“ hält die Oberfläche nicht ein, siehe F1.

## Befunde

### F1 · P2: Die Oberfläche leitet das Schreibrecht für Anhänge aus den anderen Feldern ab

**Fundstellen:** [TicketAttachments.tsx:37](https://github.com/1uedev/task-manager/blob/37580d0/frontend/src/pages/TicketAttachments.tsx#L37), [TicketController.java:80](https://github.com/1uedev/task-manager/blob/37580d0/backend/src/main/java/taskmanager/tickets/web/TicketController.java#L80).

`editable` ist `ticket.writableFields.length > 0`. `writableFields` enthält nach TicketController nur die fünf festen und die eigenen Felder, nie `attachments`. Damit gilt in der Oberfläche etwas anderes als in der API:

1. Eine Gruppe mit `ticket.edit` und nur der Schreibregel für `attachments` (etwa „darf Belege anhängen, aber keine Felder ändern“) sieht keine Ablagefläche und keinen Entfernen Knopf, obwohl der Server Hochladen und Entfernen erlaubt.
2. Eine Gruppe mit `ticket.edit` und Schreibregel für den Titel, aber ohne Schreibregel für `attachments` (etwa jede Gruppe mit Schreibrechten, die vor TASK-051 angelegt wurde und die neue Regel nicht erhalten hat) sieht Ablagefläche und Entfernen Knöpfe und erhält erst nach dem Hochladen 403.

Das Muster stammt aus den Tags (TicketPage.tsx:304, dort ebenso). Der Abnahmesatz in TODO.md „die Rechte gelten in Oberfläche, API und Historie gleich“ ist damit für Anhänge nicht erfüllt. **Vorschlag:** `attachments` (und `tags`) in `writableFields` mitliefern oder ein eigenes Kennzeichen in der Antwort der Liste, und beide Fälle in den Browsertest aufnehmen.

### F2 · P2: Hinweise zur Sicherung sind zutreffend, aber für eine Rücksicherung nicht ausreichend

**Fundstelle:** [backend/README.md:228](https://github.com/1uedev/task-manager/blob/37580d0/backend/README.md#L228), docs/DEMO.md (Befehle).

Was stimmt: Datenbank und Dateispeicher gehören zusammen; ein älterer Dateistand führt zu 404 bei jüngeren Anhängen (geprüft: fehlender Inhalt ergibt 404 `attachment.not_found`), ein jüngerer zu Inhalten ohne Zeile. Was fehlt oder ungenau ist:

1. **Reihenfolge.** Sicher ist nur „erst Datenbank, dann Dateispeicher“: dann ist der Dateistand jünger und es entstehen höchstens Waisen. Umgekehrt entstehen Anhänge ohne Inhalt. Das steht nirgends.
2. **„nach 24 Stunden in die Quarantäne“ ist ungenau.** Der Aufräumlauf vergleicht mit dem Änderungszeitpunkt der Datei. Wird ein Dateistand mit erhaltenen Zeitstempeln zurückgespielt oder startet der Speicher mit einer falschen Datenbank, wandern alle Inhalte, die älter als 24 Stunden sind, schon beim ersten Lauf 30 Sekunden nach dem Start in `quarantine/` und werden 7 Tage danach gelöscht.
3. **Zurückholen aus der Quarantäne.** DEC-080 begründet die Quarantäne damit, dass man die Dateien „zurückbewegen“ kann. Wie (von `quarantine/<id>` nach `content/<zwei Zeichen>/<id>`), steht nicht in der README.
4. **Entfernte Anhänge.** Wird eine Datenbank vor einem Entfernen zurückgespielt, der Dateispeicher aber nach dem Löschen des Inhalts (7 Tage), ist der Anhang wieder sichtbar und liefert 404. Nicht erwähnt.
5. **Vorführumgebung.** DEMO.md nennt das Volume `files-data`, aber keinen Befehl, es zu sichern (etwa mit einem Hilfscontainer und `tar`). Für ein eigenes Verzeichnis statt des benannten Volumes fehlt der Hinweis, dass es dem Konto 10001 gehören muss.

DEC-080 verschiebt den Nachweis zu A18; die genannten Punkte sind aber Aussagen über das jetzige Verhalten und sollten jetzt stimmen.

### F3 · P3: Mehrere Dateien: nach der ersten Ablehnung bricht der Rest still ab

**Fundstelle:** [TicketAttachments.tsx:49 bis 58](https://github.com/1uedev/task-manager/blob/37580d0/frontend/src/pages/TicketAttachments.tsx#L49).

Gemessen gegen den Server mit `eins.txt`, `zwei.txt` (beginnt mit `MZ`) und `drei.txt`: `eins.txt` wird angehängt, `zwei.txt` abgewiesen, `drei.txt` gar nicht gesendet. Die Meldung nennt nur `zwei.txt`; dass `eins.txt` angehängt wurde und `drei.txt` fehlt, sagt sie nicht. Jede Erfolgsmeldung überschreibt die vorige. **Vorschlag:** alle Dateien versuchen und am Ende eine Zusammenfassung zeigen, oder den Abbruch ausdrücklich nennen.

### F4 · P3: Ein misslungener Download bleibt in der Oberfläche unerklärt

**Fundstelle:** [TicketAttachments.tsx:98](https://github.com/1uedev/task-manager/blob/37580d0/frontend/src/pages/TicketAttachments.tsx#L98).

Der Download ist ein Link. Antwortet der Server mit 404 (Inhalt fehlt, etwa nach einer Rücksicherung), 403 (Recht entzogen) oder 503, bricht Chromium den Download ab (gemessen: `failure=canceled`), die Seite zeigt nichts. Der Text `attachments.error.gone` ist vorhanden, wird aber nie verwendet. Ein abgelaufenes Sitzungsende führt ebenso nur zu einem abgebrochenen Download. **Vorschlag:** vor dem Link einen kurzen Abruf mit HEAD oder nach einem Fehler die Liste neu laden und den Grund zeigen.

### F5 · P3: Die Sperrliste lässt aktive Webinhalte und Windows Ausführbares durch

**Fundstelle:** [FilesProperties.java:26](https://github.com/1uedev/task-manager/blob/37580d0/backend/src/main/java/taskmanager/files/internal/FilesProperties.java#L26).

Angenommen wurden (201): `.xht`, `.shtml`, `.mht`, `.mhtml`, `.svgz`, `.xml`, `.chm`, `.url`, `.iso`, `.vhd`, `.jnlp`, `.docm`. `.xht` und `.shtml` öffnet ein Browser nach dem Speichern als Seite mit Skript, `.chm` und `.url` führen unter Windows Code aus oder auf fremde Ziele, `.iso` und `.vhd` umgehen dort die Herkunftsmarkierung. Im Browser der Anwendung ist das wegen der Auslieferung ungefährlich; es betrifft das Öffnen der gespeicherten Datei. DEC-080 nennt die Liste eine Hürde, sagt aber „Sperrliste für Programme, Skripte und aktive Webinhalte“. **Vorschlag:** mindestens `xht`, `shtml`, `mht`, `mhtml`, `svgz`, `chm`, `url` ergänzen und über `iso`, `img`, `vhd`, `vhdx`, `jnlp` und Makrodokumente entscheiden.

### F6 · P3: TODO.md verspricht eine inhaltsadressierte Ablage und einen Scanner Port

**Fundstelle:** [TODO.md:647 und 649](https://github.com/1uedev/task-manager/blob/37580d0/TODO.md#L647).

Umfang 1 sagt „die Ablage ist inhaltsadressiert über die SHA-256 Prüfsumme“. Der Code legt unter der Kennung des Anhangs ab (FileSystemStorage, `content/<zwei Zeichen>/<Kennung>`), DEC-080 Punkt 1 sagt genau das, und die README nennt „keine gemeinsame Ablage gleicher Inhalte“ als Grenze. Umfang 3 nennt „einen Port für einen späteren Scanner“; einen solchen Port gibt es im Modul `files` nicht. Umfang 5 („wenn kein Anhang ihn mehr verwendet“) setzt ebenfalls die gemeinsame Ablage voraus. **Vorschlag:** TODO.md an DEC-080 angleichen oder den Port nachliefern.

### F7 · P3: Fehler des Dateispeichers werden nicht protokolliert, ein Abbruch des Uploads gilt als Speicherfehler

**Fundstelle:** [AttachmentService.java:97, 120, 134](https://github.com/1uedev/task-manager/blob/37580d0/backend/src/main/java/taskmanager/files/internal/AttachmentService.java#L120).

Bei nicht beschreibbarem `staging/` antwortete der Server 503 `attachment.storage_unavailable`; im Protokoll der Anwendung stand nichts (gemessen mit `docker compose logs app`). Der Betrieb sieht also nur 503 im Browser, ohne Ursache und ohne Pfad. Dieselbe Antwort entsteht, wenn der Client die Übertragung abbricht, weil `stage` dessen IOException nicht vom Speicherfehler unterscheidet. **Vorschlag:** die Ursache mit Korrelationskennung als Warnung protokollieren und den Lesefehler des Körpers getrennt behandeln.

### F8 · P3: Grenze und Sperrliste sind „einstellbar“, aber nicht dokumentiert und die Oberfläche kennt nur 25 MB

**Fundstellen:** [TicketAttachments.tsx:12](https://github.com/1uedev/task-manager/blob/37580d0/frontend/src/pages/TicketAttachments.tsx#L12), backend/README.md (Tabelle der Umgebungsvariablen).

DEC-080 nennt Grenze und Sperrliste einstellbar. Die README nennt nur die Eigenschaften `taskmanager.files.*`, keine Umgebungsvariablen, mit denen man sie im Container setzt. Wird die Grenze geändert, zeigt die Oberfläche weiter „Höchstens 25 MB je Datei“, weil der Wert fest im Code steht. **Vorschlag:** Umgebungsvariablen dokumentieren und die Grenze vom Server liefern lassen, oder ausdrücklich festlegen, dass sie in V1 nicht geändert wird.

## Hinweise

- **H1 Tastatur:** Nach „Anhang … entfernen“ und nach „Abbrechen“ verschwindet der fokussierte Knopf, der Fokus landet auf `body` (gemessen). Mit Tab geht es zwar weiter, ein Screenreader verliert aber den Ort. Fokus auf „Entfernen“ beziehungsweise zurück auf den Knopf setzen.
- **H2 Abbruch:** Ein laufender Upload lässt sich nicht abbrechen; wer die Seite wechselt, lädt weiter hoch und der Anhang erscheint später. Ein Ablegen während eines Uploads wird still ignoriert.
- **H3 Meldungen:** Fehler erscheinen im Stil einer Information (`kind: 'info'`). `attachment.busy` hat keinen eigenen Text. `request.invalid` wird immer als „Der Dateiname ist nicht verwendbar.“ gezeigt, auch wenn der Server den Code aus einem anderen Grund sendet.
- **H4 Rechteentzug während der Arbeit:** Wird die Leseregel entzogen, lädt die Oberfläche nach einem 403 die Liste neu, erhält 403 und blendet den ganzen Abschnitt samt der eben gesetzten Meldung aus. Die Person sieht den Abschnitt verschwinden, ohne Grund.
- **H5 Historie:** Ist der Text eines Eintrags kein gültiges JSON, lautet die Zeile „Anhang Leer hinzugefügt ()“. Kommt nach heutigem Code nicht vor.
- **H6 Dauerhaftigkeit:** Nach dem atomaren Verschieben wird das Zielverzeichnis nicht mit `fsync` gesichert. „Eine Zeile ohne Inhalt kann nicht entstehen“ gilt damit für einen Absturz des Prozesses, nicht sicher für einen Stromausfall.
- **H7 Rückstrich:** `\` gilt auch auf Linux als Pfadtrenner; `a"b\c.txt` wird zu `c.txt`. Vermutlich gewollt, sollte aber in der README stehen.
- **H8 Testumgebung:** Chromium speichert Dateien mit nicht ASCII Namen unter dem Namen `download`, wenn der Prozess ohne UTF 8 Gebietsschema läuft (hier ohne `LANG`). Mit `LC_ALL=C.UTF-8` stimmen die Namen. Das ist kein Fehler des Produkts; ein künftiger Browsertest mit Umlauten braucht diese Einstellung.

## Bewertung der Abnahmekriterien aus TODO.md

| Kriterium | Stand |
|---|---|
| Hochladen bis zur Grenze, dieselben Bytes zurück | erfüllt (FilesApiTest, demo verify) |
| Zu große, gesperrte, leere Dateien mit stabilem Code, ohne Reste | erfüllt; Sperrliste mit Lücken (F5) |
| Neustart oder Austausch des Containers behält Anhänge | erfüllt (gemessen) |
| Abbruch zwischen Ablegen und Zuordnung hinterlässt nur eine Datei, die der Aufräumlauf entfernt | erfüllt über Quarantäne (FilesApiTest); Zeitpunkt siehe F2 |
| 404/403 auch über den direkten Link und nach Entzug | erfüllt in der API; Oberfläche erklärt es nicht (F4) |
| Historie nur mit Leserecht | erfüllt |
| Name und Inhalt verlassen keine Pfade, kein Skript beim Download | erfüllt |
| Rechte in Oberfläche, API und Historie gleich | **nicht erfüllt** für das Schreibrecht in der Oberfläche (F1) |
