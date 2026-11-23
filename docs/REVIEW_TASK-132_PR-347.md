# Prüfbericht zu TASK-132 / PR #347

**Technische Empfehlung: nachbessern.** Zwölf Befunde stehen vor der Abnahme. Die wichtigsten betreffen die Schemaprüfung, die Rechte der laufenden Anwendung, die Sicherung von Uploads und den Rückweg nach einem Update. Der Update-Lauf auf GitHub belegt den erfolgreichen Wechsel von V0069 auf V0070 mit dem geprüften Teil des Demobestands. Die fachliche Abnahme liegt bei Holger.

**Prüfstand:** 23. November 2026, Sitzung codex-20261123-task132-review-01. Ergebnis-Head cd7aa286c53c1fc9dade36fb2cb0e6c9a8923421, Basis cb663dd8bf7b0dca818628af35ba0d3025f0ac51. Die Zeilenangaben beziehen sich auf diesen Head des Branches claude/project-thread-elfpvq. Der PR ist bereits gemergt.

**Maßstab:** AGENTS.md, docs/DEVELOPMENT_REVIEW.md, der vollständige TASK-132-Eintrag mit seinen vier Regeln, vier Abnahmekriterien und der Übergabe, DEC-162 sowie O01 und O11 aus docs/OPERATIONS.md. Die Prüfung umfasst die 28 geänderten Dateien und die dazugehörigen Aufrufwege. Aus TASK-131 sind hier nur Auswirkungen der neuen Betriebsvorlage und des neuen Updateablaufs berücksichtigt.

## Befunde vor der Abnahme

### F1 – Befund, hoch – Ein Konfigurationswert erteilt Webprozess und Befehlen die Migrationsfreigabe

- **Ort:** backend/src/main/java/taskmanager/operations/internal/SchemaGuard.java:37, :50, :54. backend/src/main/java/taskmanager/TaskManagerApplication.java:28, :47, :104. deploy/compose.yaml:26.
- **Beobachtung:** Der Einstieg unterscheidet den tatsächlichen Containerbefehl, die Schemaprüfung übernimmt ihre Entscheidung aber aus der frei konfigurierbaren Eigenschaft taskmanager.operations-command. Sobald diese migrate enthält, ruft der Guard auch bei gesetztem verify direkt Flyway auf. Der gewöhnliche Webeinstieg und der Ausführer können die Eigenschaft aus der Spring-Konfiguration übernehmen. Gleiches gilt für Aufrufe wie bootstrap-admin, deren eigener Einstieg den Wert nicht belegt. Eine übergeordnete Spring-Einstellung kann zudem den Schema-Modus selbst auf migrate setzen.
- **Wirkung:** Die in DEC-162 zugesicherte Trennung des einzigen Wartungsbefehls von Webprozess, Ausführer und anderen Befehlen ist in der Anwendung nicht verankert. Da die Vorlage für alle diese Prozesse dasselbe schreibberechtigte Datenbankkonto verwendet, gibt es auch auf Datenbankebene keine zweite Schranke.
- **Gegenprobe:** Mit unverändertem SchemaGuard und simuliertem Flyway meldet verify eine ausstehende Migration und ruft migrate() nicht auf. Derselbe Guard mit dem Befehlswert migrate ruft migrate() trotz verify auf. Der Test verwendet keinen Webserver und keine Datenbank.
- **Vorschlag:** Die Freigabe an den tatsächlich vom Einstieg ausgewählten Wartungsbefehl binden. Eine gewöhnliche Spring-Eigenschaft darf diese Freigabe nicht erteilen. Die Vorführung behält einen ausdrücklich gewählten Weg für ihr bisheriges Startverhalten. Die Betriebsvorlage braucht zusätzlich ein Datenbankkonto ohne Schemaänderungsrechte für ihre laufenden Prozesse.

### F2 – Befund, hoch – Spring- und Flyway-Einstellungen können die Prüfung auslassen oder verkürzen

- **Ort:** backend/src/main/java/taskmanager/operations/internal/SchemaGuard.java:28, :58, :61, :68. backend/src/main/resources/application.yaml:18, :23.
- **Beobachtung:** Der Guard hängt allein an der automatischen Flyway-Initialisierung. Bei spring.flyway.enabled=false wird der Bean weiterhin erstellt, aber nicht aufgerufen. Die verbleibende Hibernate-Prüfung und die Abfrage der Vormerkung ersetzen keinen Vergleich der Flyway-Historie. Eine Datenbank mit vorhandenen Anwendungstabellen und einer neueren oder fehlerhaften Historie kann so unbeanstandet bleiben. Eine zweite, unabhängige Lücke entsteht über spring.flyway.target. Flyway 12.4 ordnet Migrationen oberhalb des gesetzten Ziels als ABOVE_TARGET ein, nimmt sie aus pending() heraus und akzeptiert sie in validate(). Dadurch kann der Guard einen unvollständigen Stand akzeptieren. Bei einem konkreten Stand direkt vor V0070 kann der gewöhnliche Prozess noch an der fehlenden Tabelle der Wiederherstellungssperre scheitern. Dieser Folgefehler ist keine Schemaprüfung und greift etwa bei einer vorhandenen Tabelle oder einem davon befreiten Administrationsbefehl nicht.
- **Wirkung:** O11 ist nur für die standardmäßige Flyway-Konfiguration abgesichert. Einstellungen, die den sichtbaren Migrationsbestand oder die Initialisierung beeinflussen, können die zugesicherte Ablehnung verhindern.
- **Gegenprobe:** Ein kleiner Spring-Boot-Kontext startete in verify mit vorhandenem Guard und abgeschaltetem Flyway ohne Initializer. Ein zweiter Test erzeugte mit Flyway 12.4 den echten Zustand ABOVE_TARGET, bestätigte dessen erfolgreiche Validierung und zeigte anschließend, dass der unveränderte Guard diese Situation akzeptiert. Es wurde keine vollständige Anwendung gestartet.
- **Vorschlag:** Im Betriebsmodus die Prüfung als zwingende Startvoraussetzung behandeln und den geprüften Satz aus dem Image fest vorgeben. Abgeschaltetes Flyway oder Einstellungen, die Satz, Ziel oder Historie verändern, mit einer klaren Meldung ablehnen. Webprozess, Ausführer und bootstrap-admin mit abweichenden Einstellungen sowie mit einer tatsächlich fehlerhaften Historie prüfen.

### F3 – Befund, hoch – Die laufende Anwendung verwendet den PostgreSQL-Superuser

- **Ort:** deploy/compose.yaml:19, :21, :75, :95, :103, :110. deploy/db-init.sh:10, :13. docs/OPERATIONS.md:27.
- **Beobachtung:** Das offizielle PostgreSQL-Image initialisiert POSTGRES_USER=taskmanager als Datenbank-Superuser. Webprozess, Ausführer und migrate verwenden dieses Konto und dasselbe Passwort. Die einzige zusätzliche Datenbankrolle ist taskmanager_backup. Ihre direkten SQL-Rechte umfassen über die Standardprivilegien auch alle künftigen Tabellen im öffentlichen Schema. Für den gezeigten SQL-Leseweg braucht sie die Inhaltssicht und die Flyway-Historie. Ihre ohnehin weitreichenden Rechte für die physische Replikation bleiben davon zu unterscheiden.
- **Wirkung:** Der Web- und Workerbetrieb besitzt die Rechte zur Änderung des Schemas und zur Administration anderer Datenbankrollen. verify begrenzt den normalen Startpfad, kann diese Rechte aber nicht entziehen. Der Least-Privilege-Anspruch der Betriebsvorlage und die in OPERATIONS.md beschriebene Aufteilung von Laufzeit- und Schemaänderungsrechten bleiben unerfüllt.
- **Vorschlag:** Separate Rollen für Laufzeit, Migration und initiale Datenbankverwaltung einsetzen. Die Laufzeit erhält die benötigten Rechte an Anwendungsobjekten und Historie, aber keine Eigentümer-, DDL- oder Rollenverwaltungsrechte. Die direkte Abfragerolle der Sicherung auf ihren tatsächlichen Bedarf beschränken. Im Test die laufende Identität und eine verweigerte Schemaänderung mit dem Laufzeitkonto prüfen.

### F4 – Befund, hoch – Die Sicherung der Vorlage kann gewöhnliche Uploads nicht lesen

- **Ort:** deploy/compose.yaml:30, :171, :177. deploy/backup/Dockerfile:16. Dockerfile:38, :44. Bestehender Dateipfad backend/src/main/java/taskmanager/files/internal/FileSystemStorage.java:76, :123. tools/update/update-check.sh:194.
- **Beobachtung:** Die Anwendung schreibt als UID 10001. Sie legt Uploads und Bilder über Files.createTempFile an. OpenJDK setzt für temporäre Dateien auf dem POSIX-Dateisystem standardmäßig nur Lese- und Schreibrecht für den Eigentümer (0600). Das atomare Verschieben derselben Datei in content/ ändert den Modus nicht. Der neue Betriebsdienst liest das Volume als Konto postgres mit UID 999 und ohne Capabilities. Weder eine Freigabe für dieses Konto noch ein Wechsel des Dateimodus ist eingerichtet. Ein bloßer Größenvergleich kann diese vorhandenen Dateien trotzdem mitzählen.
- **Wirkung:** Sobald echte Anhänge oder Wikibilder vorhanden sind, kann restic sie in der ausgelieferten Konfiguration nicht vollständig lesen. Die Vorlage liefert damit keinen belastbaren Sicherungspunkt für einen normalen Bestand. Die Update-CI meldet ausdrücklich 0 contents im als vollständig ausgegebenen Punkt. Der Fehlerpfad für unvollständige restic-Snapshots gehört weiterhin zur Nachbesserung von TASK-131. Der Befund hier betrifft die Schutzrechte der konkreten neuen Vorlage.
- **Vorschlag:** Das Leserecht gezielt für den Sicherungsdienst einrichten, etwa über ein gemeinsames Gruppenkonzept mit passendem Modus für gespeicherte und zurückgespielte Dateien. Das Volume für die Sicherung nur lesbar eingebunden lassen. O01 und den Sicherungsweg mit einem über die Anwendung geschriebenen Anhang und Wikibild unter den echten Containerkonten prüfen.

### F5 – Befund, hoch – Der dokumentierte manuelle Sicherungspunkt konkurriert mit dem Sicherungsdienst

- **Ort:** deploy/compose.yaml:152, :158, :174. docs/INSTALL.md:80, :81. tools/update/update-check.sh:148, :194. Gemeinsame Zustandsdateien in deploy/backup/backup.sh:16, :23, :68, :90.
- **Beobachtung:** Die Vorlage startet backup loop. Der abgedruckte Updatebefehl docker compose run --rm backup once startet eine zweite Instanz mit demselben /state und demselben Repository. Der Ablauf hält den Dauerdienst nicht an und synchronisiert beide Aufrufe nicht. Sie bearbeiten dieselben Basisverzeichnisse, die Inhaltsliste und manifest.json. So kann ein Lauf die Beschreibung des anderen zwischen ihrer Erstellung und dem Lesen durch restic ersetzen. Auch der Basissicherungsaufbau kann kollidieren. Eine etwaige restic-Sperre schützt die vorherigen lokalen Schritte nicht.
- **Wirkung:** Der neue Updateablauf kann im vorgesehenen Normalbetrieb einen konkurrierenden oder falschen Rückkehrpunkt erzeugen. Im ausgewerteten ersten CI-Update gab es vor dem Wechsel kein Backup, da die Basis V0069 war. Der nachgelagerte Aufruf prüft keinen gezielt überlappenden Lauf.
- **Vorschlag:** Den manuellen Punkt über dieselbe Instanz anfordern oder den Sicherungsdienst für den manuellen Lauf kontrolliert anhalten. Die lokale Punkt- und Basiserstellung exklusiv serialisieren. Den dokumentierten Weg mit gleichzeitig fälligem Sicherungsdienst und einem tatsächlich verwendbaren Rückkehrpunkt prüfen.

### F6 – Befund, hoch – Caddy protokolliert sensible OAuth-Rückgabeparameter

- **Ort:** deploy/Caddyfile:29. Bestehende Route backend/src/main/java/taskmanager/git/web/GitController.java:129, :130. docs/OPERATIONS.md:54.
- **Beobachtung:** Der neue TLS-Einstieg aktiviert ein Access-Log ohne Filter für die URI. Caddy 2.10.2 schreibt dort RequestURI einschließlich Query. Der vorhandene Git-OAuth-Callback transportiert in dieser Query code und state. Die Standardmaskierung bestimmter Header schützt diese Werte nicht.
- **Wirkung:** Ein regulärer Git-Verbindungsablauf kann kurzlebige Autorisierungsdaten in Containerlogs und damit auch in deren Weiterleitung oder Export hinterlassen. Der Fehler entsteht mit dem neuen Proxy in TASK-132. Im Diff selbst wurden keine echten Zugangsdaten gefunden.
- **Vorschlag:** URI-Querys im Access-Log grundsätzlich auslassen oder die betreffenden Parameter zuverlässig entfernen. Den Callback mit synthetischen Parametern ausführen und das resultierende Access-Log auf Abwesenheit der Werte prüfen.

### F7 – Befund, hoch – Der Release-Workflow prüft das Tagformat und die Herkunft, aber keine Freigabe durch Holger

- **Ort:** .github/workflows/release.yml:7, :28, :38, :46, :79. TODO.md:303.
- **Beobachtung:** Das Push-Ereignis, der reguläre Ausdruck und merge-base --is-ancestor prüfen ein SemVer-Tag und einen Commit auf der Historie von main. Eine Prüfung des freigebenden Akteurs oder ein geschütztes Release-Gate fehlt. Die im Prüfzeitpunkt über die GitHub-API sichtbare Repository-Ruleset-Liste war leer. Aus dem Workflow ist damit kein Schutz erkennbar, der die Veröffentlichung auf Holger begrenzt.
- **Wirkung:** Soweit ein anderer Akteur ein passendes Tag pushen kann, kann er den Job mit Schreibrechten für Repository und Registry auslösen. Die Nebenbedingungen „gültiges Tag“ und „auf main“ sind umgesetzt. Die ausdrückliche Freigabebedingung aus Regel 2 bleibt ungesichert.
- **Vorschlag:** Das Anlegen und Verschieben von Release-Tags durch eine verifizierte Repository-Regel beziehungsweise ein geschütztes Veröffentlichungs-Gate auf die autorisierte Freigabe begrenzen. Den Schutz dokumentieren und den Tag-Prüfschritt für erlaubte und abgelehnte Fälle ohne Veröffentlichung nachweisen.

### F8 – Befund, mittel – Die Betriebsvorlage akzeptiert veränderliche Image-Tags

- **Ort:** deploy/compose.yaml:15, :154. deploy/.env.example:4. docs/INSTALL.md:10, :42, :82.
- **Beobachtung:** `${…:?...}` prüft nur, dass beide Werte gesetzt sind. Die Vorlage validiert weder die Digestsyntax noch die erwartete Registry. Die Release-Bündelung schreibt zwar die genauen Digests in ihre Beispieldatei, die zur Installation tatsächlich gelesene .env kann aber beliebige Tags enthalten.
- **Wirkung:** Ein versehentlich eingetragener Tag kann bei erneutem Pull auf einen anderen Stand zeigen. Das widerspricht der in Regel 2 geforderten ausschließlichen Bindung an den Digest.
- **Gegenprobe:** docker compose config --images akzeptierte beide synthetischen Tag-Referenzen und gab sie unverändert als aufgelöste Images aus. Dazu musste kein Image gezogen werden. Die Update-CI benutzt ebenfalls Tags und kann diesen Schutz daher nicht belegen.
- **Vorschlag:** Beide Referenzen vor Pull, Start und Update strikt prüfen. Den Produktivweg nur mit vollständigen Digestreferenzen zulassen. Temporäre Images der CI über einen ausdrücklich getrennten Testweg oder über lokale Digestreferenzen einsetzen.

### F9 – Befund, mittel – Die Update-Basis kann ein unveröffentlichtes Tag oder der Zielstand selbst sein

- **Ort:** .github/workflows/update.yml:48, :51, :53, :62. tools/update/update-check.sh:117, :157. TODO.md:305.
- **Beobachtung:** Der Workflow wählt den höchsten passenden Tag, der von HEAD erreichbar ist. Ein Tag wird auch dann gewählt, wenn sein Release fehlgeschlagen ist oder der Tag direkt auf dem geprüften HEAD liegt. Im zweiten Fall baut der Job zweimal denselben Quellstand. Der Ablehnungsfall vor migrate entfällt bei gleicher Versionsnummer der Migrationen. Ein gleiches Schema ist für manche echten Updates legitim. Die Tag-Auswahl kann aber den vereinbarten Vorgänger ganz aus der Probe entfernen.
- **Wirkung:** Eine grüne Prüfung kann einen Neuaufbau desselben Stands oder den Wechsel von einem nie ausgelieferten Stand belegen. Damit ist Regel 4 ab der ersten Veröffentlichung nicht verlässlich nachgewiesen.
- **Gegenprobe:** Ein lokales künstliches Git-Repository mit zwei SemVer-Tags und ohne Veröffentlichungen genügte. Die unveränderte Git-Auswahl nahm den höchsten Tag direkt auf HEAD. Der tatsächliche CI-Lauf vor der ersten Version hat korrekt den festgelegten Stand 7731cd0 gewählt.
- **Vorschlag:** Die Basis anhand einer tatsächlich veröffentlichten Version und ihrer veröffentlichten Referenzen bestimmen. Den für die Prüfung bestimmten Zielstand aus der Auswahl ausschließen, wenn ein Übergang geprüft werden soll. Identität und Herkunft von Basis und Ziel im Nachweis festhalten.

### F10 – Befund, mittel – Der Update-Lauf prüft nur einen Teil der Fachdaten und keine Dateiinhalte

- **Ort:** tools/update/update-check.sh:92, :101, :141, :181, :190, :194. docs/INSTALL.md:85.
- **Beobachtung:** Der Fingerabdruck zählt sechs Tabellen und hasht pro Ticket nur Projekt, Nummer, Titel und Status. Er ist unempfindlich gegen Änderungen etwa an Beschreibungen und Beziehungen oder den Verlust von Wiki-Inhalten, Anforderungen und Dateibytes. Login, Ticketliste und eine Antwort der Suche werden geprüft. Die in der Anleitung genannten Schritte Projekt öffnen, Ticket ändern, Anhang herunterladen und Hintergrundaufträge ansehen fehlen. Die Probe enthält keinen dauerhaften Upload in ihrem Sicherungspunkt.
- **Wirkung:** CI kann trotz fachlichem Datenverlust oder nicht funktionsfähiger Dateisicherung grün werden. Die Aussage „all data kept“ ist durch die vorhandenen Vergleiche nicht gedeckt.
- **Gegenprobe:** Der aus dem Skript gelesene SQL-Ausdruck ergab bei synthetischen Daten vor und nach Änderungen einer Ticketbeschreibung und eines Mitgliedschaftsinhalts exakt denselben Wert. Für den kleinen Ein-Zeilen-Nachweis wurde das Aggregat in SQLite nachgebildet. PostgreSQL wurde hierzu nicht ausgeführt.
- **Vorschlag:** Ein stabiles, fachlich abgegrenztes Inventar mit inhaltlichen Fingerabdrücken für die tatsächlich gesäten Fachobjekte vergleichen. Einen Upload inklusive Download und Prüfsumme vor und nach Austausch der Container nachweisen. Die in der Anleitung genannten Kernabläufe prüfen und im Bericht die geprüfte Datenmenge nennen.

### F11 – Befund, hoch – Der neue Rückkehrweg lässt sich mit der ausgelieferten Betriebsvorlage nicht vollständig ausführen

- **Ort:** docs/INSTALL.md:87, :89. docs/BACKUP_RESTORE.md:126, :129, :130, :131. deploy/compose.yaml:20, :30, :78, :168, :176.
- **Beobachtung:** Die Updateanleitung verweist für die Rückkehr auf die generische Anleitung von TASK-131. In der neuen Vorlage bekommt nur backup das Rücksicherungsvolume /restore. Die Datenbank startet aus db-data, und Anwendung und Ausführer binden files-data ein. Die Anleitung zeigt keinen vollständigen Wechsel beider Dienste auf die zurückgespielten Daten. Außerdem verwendet der Backup-Dienst im Auslieferungszustand PGUSER=taskmanager_backup. Diese Rolle hat kein INSERT-Recht für die Vormerktabelle, das restore.sh mark benötigt. Der bereits aus TASK-131 bekannte Entrypoint-Aufruf in der generischen Anleitung wird hier nicht nochmals als eigener Befund gezählt.
- **Wirkung:** Nach einem gescheiterten Update führt ein Start der Vorlage mit den alten Digests weiterhin auf die bisherigen Volumes oder scheitert an der Startprüfung. Ein Betreiber muss die Änderungen an der Vorlage und die getrennten Rechte für die Vormerkung selbst ableiten. Für O11 fehlt der dokumentierte und geprüfte Rückweg mit der ausgelieferten Vorlage.
- **Vorschlag:** Den Rückkehrweg für genau diese Compose-Datei liefern. Mounts und Ziele für Datenbank und Dateien, Eigentümerwechsel, Rolle für die Vormerkung sowie Startreihenfolge müssen aus den Befehlen hervorgehen. Die Probe mit einem synthetisch gescheiterten Update und Wiederanlauf am vorherigen Punkt durchführen.

### F12 – Befund, mittel – Die Release-Anleitung hat im Paket Lücken beim eigenen Proxy und bei mitgelieferten Verweisen

- **Ort:** docs/INSTALL.md:3, :50, :52, :81, :103. deploy/compose.yaml:127, :135. .github/workflows/release.yml:98, :105.
- **Beobachtung:** Die Anleitung nennt für einen Kundenproxy nur eine eigene Override-Datei, die Port 8080 für den Proxy veröffentlicht. Das entfernt den standardmäßig aktiven Dienst Caddy und dessen Hostports nicht. Der gewöhnliche Aufruf docker compose up -d bleibt beschrieben, ebenso das direkte Stoppen von caddy beim Update. Das Release-Archiv enthält außerdem nur INSTALL und BACKUP_RESTORE als Markdown-Dateien. Lokale Verweise aus INSTALL auf OPERATIONS und aus den Anleitungen auf weitere Dokumente und Prüfskripte funktionieren im entpackten Paket nicht.
- **Wirkung:** Bei vorhandenen Hostports kann Caddy mit dem Kundenproxy kollidieren. Der dokumentierte alternative Installationsweg hängt von einer nicht beschriebenen Änderung ab. Betreiber können aus dem Bundle auch Teile der Voraussetzungen und der verlinkten Prüfanleitungen nicht aufrufen.
- **Vorschlag:** Einen vollständigen Betriebsweg für den Kundenproxy liefern. Dazu gehören eine wirksame Deaktivierung von Caddy, eine auf den Proxy begrenzte Verbindung zum Webprozess, die bereits genannten Forwarded-Header und passende Start-/Updatebefehle. Verlinkte Inhalte mitliefern oder auf die Quellen des konkreten Releases verweisen. Die ausgehenden Verbindungen bei Installation mit den tatsächlich benötigten Registries für GHCR, PostgreSQL und Caddy abgleichen.

## Hinweise

### H1 – Hinweis – Die Fehlercodes sind nur für einen Teil der negativen Fälle belegt

- **Ort:** backend/src/main/java/taskmanager/TaskManagerApplication.java:96, :108. backend/src/main/java/taskmanager/operations/internal/SchemaGuard.java:43, :54, :74. backend/src/test/java/taskmanager/MigrateCommandTest.java:129. docs/INSTALL.md:73.
- **Beobachtung und Wirkung:** Der CI-Test startet zwei gleichzeitige Aufrufe derselben Version gegen dieselbe PostgreSQL-Datenbank. Jede Migration steht danach genau einmal in der Historie. CI bestätigt zudem 0 für einen aktuellen Stand und 2 für Argumentfehler sowie eine neuere Datenbank. Eine bereits als fehlgeschlagen gespeicherte Migration wird bewusst mit 2 behandelt. Scheitert die gerade ausgeführte SQL-Migration, läuft ihre ungefangene Ausnahme über den Fehlerpfad des JVM-Prozesses, der regulär mit 1 endet. Der neu hinzugekommene Meldungspfad für einen ungültigen Modus gibt den eingetragenen Rohwert wieder. Bei einer Fehlkonfiguration können so versehentlich sensible Werte in die Startlogs gelangen.
- **Vorschlag:** Frühere gegenüber im aktuellen Lauf aufgetretenen Migrationsfehlern in der Anleitung ausdrücklich unterscheiden. Einen bereits fehlgeschlagenen Historieneintrag und eine nun fehlschlagende Migration in dauerhaftem Testmaterial prüfen. Bei ungültigem Modus die zulässigen Werte nennen, den Rohwert weglassen. Die Gleichzeitigkeit unterschiedlicher Imageversionen separat betrachten, da die Vorabprüfung außerhalb des Flyway-Migrationsaufrufs liegt.

### H2 – Hinweis – Geheimnisprüfung und temporäre Testdaten enger absichern

- **Ort:** deploy/db-init.sh:9. tools/update/update-check.sh:32, :74, :103, :107, :137, :199.
- **Beobachtung und Wirkung:** Die Vorlage enthält keine eingetragenen Passwörter oder privaten Schlüssel. Das neue Initialisierungsskript übergibt das Betreiberpasswort jedoch kurz als psql-Prozessargument. Die Probe tut Ähnliches mit ihren synthetischen Passwörtern. Ihr außerhalb von work angelegter Cookie-Jar kann bei einem Abbruch vor rm zurückbleiben. Die Prüfung der Image-Historie und -Konfiguration sucht vor allem nach pro Lauf erst nach dem Build erzeugten Testwerten. Sie kann keine beliebigen Geheimnisse in Image-Dateien ausschließen.
- **Vorschlag:** Das Initialisierungspasswort aus der Umgebung im psql-Prozess beziehen. Temporäre Cookies und Dateien unter dem bereits aufgeräumten Verzeichnis anlegen. Die Aussage der Image-Prüfung auf den geprüften Umfang begrenzen und bei Bedarf gezielt Image-Ebenen einbeziehen.

### H3 – Hinweis – Auslöser und Aufräumen der Update-CI decken den Standardlauf ab

- **Ort:** .github/workflows/update.yml:8, :17, :42, :72. tools/update/update-check.sh:73.
- **Beobachtung und Wirkung:** Änderungen an Migrationen lösen den Workflow wie gefordert aus. Änderungen allein an TaskManagerApplication, application.yaml, dem Demoseed oder der Installationsanleitung lösen ihn nicht aus. Die 75-Minuten-Grenze und das Sammeln von Artefakten auch im Fehlerfall sind vorhanden. Der Shell-Trap räumt im normalen Lauf auf, ignoriert Fehler von Compose und ist bei hartem Abbruch nicht garantiert. GitHub stellt hier einen kurzlebigen Runner bereit.
- **Vorschlag:** Die direkt an der Probe beteiligten Start- und Seed-Dateien in beide Pfadfilter aufnehmen. Aufräumfehler protokollieren und für lokale Parallelität pro Lauf einen Compose-Projektnamen verwenden. Eine separate Abschlussaktion kann das Aufräumen bei abgebrochenen Läufen ergänzen.

### H4 – Hinweis – SSH-Client ist eingebaut, der externe Zielweg bleibt zu prüfen

- **Ort:** deploy/backup/Dockerfile:4, :9, :13. deploy/compose.yaml:175. docs/INSTALL.md:115.
- **Beobachtung und Wirkung:** Die CI baute das neue Image und meldete OpenSSH 10.0p2. Die Paketversion kommt aus der laufenden Paketquelle. Das ist in der Anleitung offengelegt. Die Update- und Restore-Probe benutzen ein lokales Verzeichnis und zeigen keine Verbindung mit den dokumentierten SFTP-Schlüssel- und Hostschlüsselrechten.
- **Vorschlag:** Den bereits vorgesehenen SFTP-Nachweis in der Nachbesserung von TASK-131 mit der ausgelieferten Benutzerkennung und den vorgesehenen Dateirechten durchführen. Den in CI beobachteten Paketstand beim Release über das SBOM erfassen.

## Positiv geprüfte Teile

- Die Vorführung behält den bisherigen Automigrationspfad als Voreinstellung. Der neue Betriebspfad startet mit unveränderter Standardkonfiguration in verify. Die dokumentierten Ablehnungen bei fehlendem und neuerem Schema sind in der CI für die dort geprüften Fälle sichtbar.
- Die Vorlage verwendet einen separaten Ausführer und stellt nur Caddy-Ports nach außen bereit. Weder Docker-Socket noch privilegierter Modus sind in der Vorlage konfiguriert. Die Rollen der Hilfscontainer und die normalen Speicher- und CPU-Grenzen sind im Compose-Aufbau nachvollziehbar.
- Der Release-Workflow verlangt syntaktisch vMAJOR.MINOR.PATCH auf der Historie von main. Die Schreibrechte contents und packages entsprechen den beiden Publikationszielen. Syft und Grype sind als Container über Digests festgelegt. Der Workflow plant für beide Images SPDX, Schwachstellenberichte, Registry-Digests und Prüfsummen ein. Eine tatsächliche Veröffentlichung oder vollständige Generierung dieser Artefakte wurde nicht unabhängig ausgeführt.
- Die Repository-Dateien enthalten Platzhalter, Pseudodaten und die beauftragten Registry-Adressen. Im Diff wurden keine eingebetteten echten Betreiberpasswörter, privaten Schlüssel oder Kundendaten festgestellt. Anwendungs- und Sicherungs-Dockerfile kopieren nur ihre ausdrücklich benötigten Eingaben.

## Prüfungen und Nachweise

| Unabhängige Prüfung | Ergebnis |
| --- | --- |
| PR-Diff, Pfade, Zeilen und vollständiger Quellstand | 28 Änderungen gelesen. Alle 979 Dateiblobs des Head-Trees im isolierten Prüfverzeichnis abgeglichen. In den hinzugefügten Diff-Zeilen keine nachgestellten Leerzeichen festgestellt. |
| python scripts/validate_repository.py und python scripts/task_board.py --check | Beide erfolgreich. |
| Python-Gesamtsuite mit UTF-8 | 50 Tests ausgeführt. Neun Socketfehler, ein daraus folgender Fehlschlag und ein unter Windows übersprungener Zeitzonentest. Die übrigen 39 Tests bestanden. |
| Maven test-compile offline auf Temurin 25.0.4.1 | Haupt- und Testquellen erfolgreich kompiliert. |
| Drei Gegenproben zum Schema-Guard | Normales verify, Migrationsaufruf über Eigenschaft, fehlender Initializer und Flyway-Zielzustand wie bei F1 und F2 beschrieben. Spring-Kontext und Flyway-Zustand wurden getrennt getestet. |
| Compose-Konfigurationsprobe | Beide synthetischen Image-Tags wurden von der Originalvorlage akzeptiert. Kein Container gestartet. |
| Gegenproben zu Update-Basis und Datenvergleich | Lokale Git-Auswahl und synthetischer Fingerabdruck wie bei F9 und F10 beschrieben. |
| Linux-/Docker-Integration und Veröffentlichung | Der lokale Docker-Daemon war nicht verfügbar. TLS, PostgreSQL-Integration, Update und Sicherung sind daher über die ausgewerteten CI-Läufe belegt. Es wurde kein Release-Tag gesetzt. |

Die Backend-, Update-, Restore- und Repository-CI liefen auf 24fd464. Die Logs benennen diesen Commit als Prüf-Merge von cd7aa286 in cb663dd.

- Update verwendete den vorgesehenen Startstand 7731cd0, führte eine Migration von V0069 auf V0070 aus und verglich vor und nach Update sowie Containerwechsel den vorhandenen Fingerabdruck. Geprüft wurden Anmeldung, Ticketliste und Suche über TLS mit eigenem Zertifikat. Der Sicherungspunkt nach dem Update nennt null Inhalte.
- Backend meldete 742 Tests ohne Fehler, Fehlschläge oder übersprungene Tests, darunter sechs Tests von MigrateCommandTest. Der Lifecycle-Test einschließlich Phase schema bestand.
- Restore und Repository liefen erfolgreich. In den Logs des Restore-Jobs ist der Bau mit OpenSSH 10.0p2 sichtbar. Die Repository-CI führte zusätzlich ihre Whitespace-Prüfung aus.

Die extern abgelegten Autorenprotokolle waren im geprüften Quellstand nicht enthalten. Der veröffentlichende Job ist für den PR nicht gelaufen.

## Primärquellen zu den Laufzeitdetails

- Spring Boot 4.1.1 koppelt seine Flyway-Autokonfiguration an spring.flyway.enabled (FlywayAutoConfiguration).
- Flyway 12.4 klassifiziert Migrationen jenseits des Ziels als ABOVE_TARGET und akzeptiert diesen Zustand bei der Validierung (ResolvedMigration, MigrationInfoImpl).
- Die PostgreSQL-Initialisierung verwendet POSTGRES_USER für den durch initdb angelegten Superuser. OpenJDK beschränkt die Standardrechte einer temporären POSIX-Datei auf ihren Eigentümer (PostgreSQL-Entrypoint, OpenJDK TempFileHelper).
- Caddy serialisiert die vollständige Request-URI in das Access-Log (Caddy 2.10.2 Request-Marshaler).

Vor der Abnahme sollten F1 bis F12 behoben oder von Holger als konkrete Abweichungen entschieden werden. Für die Nachprüfung sind ein erzwungener fehlerhafter Schemalauf mit Rückkehr, ein Backup mit tatsächlich gespeicherten Dateien und eine Probe des Release-Gates ohne Veröffentlichung die entscheidenden fehlenden Nachweise.
