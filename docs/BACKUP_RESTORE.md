# Sicherung und Wiederherstellung einer Installation (TASK-131)

Stand 23. November 2026 · Ergebnis von TASK-131 (DEC-161), nach der Codex Prüfung überarbeitet mit TASK-135 (DEC-165). Diese Anleitung beschreibt den Sicherungsdienst, den gemeinsamen Sicherungspunkt, die Rücksicherung in eine frische Umgebung, den Befehl `after-restore` und die Probe, mit der Datenverlust und Wiederanlaufzeit gemessen werden. Die Planungsziele stehen in DEC-038: höchstens eine Stunde Datenverlust und höchstens vier Stunden bis zum geprüften Wiederbetrieb. Der Entwurf dahinter ist [OPERATIONS.md](OPERATIONS.md).

Nicht Teil dieser Anleitung: TLS, Installationsvorlage und Updates (TASK-132), Betriebsübersicht und Überwachung (TASK-133), die Messung auf der Referenzumgebung und die V1 Freigabe (TASK-134). Die Vorführung aus [DEMO.md](DEMO.md) bleibt unverändert und ist kein Betrieb.

Die Betriebsvorlage `deploy/compose.yaml` mit diesem Sicherungsdienst, die Installation und der Updateweg stehen in [INSTALL.md](INSTALL.md) (TASK-132).

## Überblick

| Teil | Inhalt |
|---|---|
| [deploy/backup/Dockerfile](../deploy/backup/Dockerfile) | Das Image des Sicherungsdiensts: die Clientwerkzeuge von PostgreSQL 18, restic 0.18.1 und der ssh Client für SFTP Ziele, mit Tag und Digest festgelegt. Kein Port, eigenes Konto, keine Zugangsdaten im Image. |
| [deploy/backup/backup.sh](../deploy/backup/backup.sh) | Der Dienst: alle 15 Minuten ein Sicherungspunkt, täglich eine Basissicherung, Aufbewahrung 14 Tage. |
| [deploy/backup/restore.sh](../deploy/backup/restore.sh) | Punkte auflisten und einen Punkt zurückspielen und dabei beweisen. |
| [deploy/backup/entrypoint.sh](../deploy/backup/entrypoint.sh) | Verteilt `points` und `restore` an die Rücksicherung, alles andere an den Dienst. |
| [deploy/archive-wal.sh](../deploy/archive-wal.sh) | Das Archivkommando der Datenbank: ein Segment erscheint erst vollständig unter seinem Namen. |
| [deploy/restore-place.sh](../deploy/restore-place.sh) | Legt eine bewiesene Rücksicherung in die Volumes von Datenbank und Dateispeicher. |
| [tools/restore/restore-check.sh](../tools/restore/restore-check.sh) | Die Probe: spielt den vollständigen Verlust gegen ein SFTP Ziel durch und misst Datenverlust und Wiederanlaufzeit. Läuft in CI als Workflow `Restore`. |
| [tools/restore/compose.restore.yaml](../tools/restore/compose.restore.yaml) | Die Umgebung der Probe: Installation mit Sicherung, SFTP Ziel, Mailserver und eine zweite, leere Umgebung wie in der Betriebsvorlage. |
| Befehl `after-restore` | Bereitet eine zurückgespielte Installation vor und gibt sie frei. Teil des Anwendungsimages. |

## Was ein Sicherungspunkt ist

Ein Sicherungspunkt ist **ein** restic Stand mit drei Dingen und einer genau festgelegten Grenze:

1. dem Archiv der Schreibprotokolle der Datenbank, lückenlos vom Beginn seiner Basissicherung bis zum Segment mit dem Wiederherstellungspunkt,
2. dem Dateispeicher, gelesen **nach** der Grenze,
3. der Beschreibung (`manifest.json`) mit dem Namen des Punkts, den Zeitpunkten `time` und `timeAfter`, dem Namen des Wiederherstellungspunkts, seiner Schreibposition und seinem Segment, der Basissicherung, der Schemaversion und der Zahl der Inhalte.

**Die Grenze** ist ein Wiederherstellungspunkt, den die Datenbank selbst schreibt (`pg_create_restore_point('tm-NAME')`). In derselben Sitzung liest der Dienst die Uhr davor (`time`) und danach (`timeAfter`). Die Rücksicherung stellt die Datenbank genau auf diesen Punkt: alles, was vor `time` bestätigt war, ist darin; nichts, was nach `timeAfter` bestätigt wurde, ist darin. Eine Transaktion, die vor der Grenze begann und danach bestätigt wurde, fehlt, wie es sein muss. Auch ein Punkt, nach dem nichts mehr geschrieben wurde, ist erreichbar, weil der Wiederherstellungspunkt ein eigener Eintrag im Schreibprotokoll ist.

**Das Archiv** füllt das Archivkommando [archive-wal.sh](../deploy/archive-wal.sh): es schreibt ein Segment unter einem vorläufigen Namen, schreibt es auf die Platte und benennt es erst dann um. Ein abgebrochener Versuch hinterlässt nur die vorläufige Datei, und der nächste ersetzt sie. Vor dem Punkt prüft der Dienst jedes Segment vom Beginn der Basissicherung bis zum Segment der Grenze mit seiner vollen Größe.

**Der Dateispeicher** wird gegen das Aufräumen geschützt: vom Setzen der Grenze bis zum gespeicherten Punkt löscht der Aufräumlauf keinen Inhalt (`files_purge_hold`, V0071). Jeder Inhalt, den die Datenbank an der Grenze nennt, liegt damit im gesicherten Dateispeicher. Dateien sind unveränderlich abgelegt; ein jüngerer Dateistand bringt höchstens Inhalte ohne Zeile mit, und die räumt der Aufräumlauf später weg. Gespeicherte Inhalte sind für das Konto und die Gruppe der Anwendung lesbar, sonst für niemanden; der Sicherungsdienst liest als Mitglied dieser Gruppe (`group_add: ["10001"]`).

**Vollständig** ist ein Punkt erst, wenn restic mit Status 0 endete und die gespeicherte Beschreibung genau die geschriebene ist. Bis dahin heißt der Stand „candidate“ und wird nie angeboten; ein Lauf, der scheitert, auch mit Status 3 bei einer unlesbaren Datei, verwirft ihn. **Fehlt ein Inhalt, den die Datenbank nennt, wird der Punkt abgelehnt** (Prüffall O06): der vorige Punkt bleibt der neueste, und die Überwachung seines Alters zeigt den Rückstand. Im Dauerbetrieb läuft jeder Punkt als eigener Prozess; ein Fehler beendet genau diesen Punkt.

Takt und Grenzen (bestätigte Regeln 7 und 8 von TASK-131):

| Größe | Wert | Bedeutung |
|---|---|---|
| Wechsel der Schreibprotokolle | spätestens alle 5 Minuten | `archive_timeout=300` in der Datenbank |
| Sicherungspunkt | alle 15 Minuten | `TASK_MANAGER_BACKUP_INTERVAL`, Vorgabe 900 s |
| Basissicherung | täglich | `TASK_MANAGER_BACKUP_BASE_AGE`, Vorgabe 86400 s |
| Aufbewahrung | 14 Tage vollständig wiederherstellbar | `TASK_MANAGER_BACKUP_RETENTION`, Vorgabe 14 |
| Lokales Archiv | Segmente ab dem Beginn der jüngsten Basissicherung | ältere entfernt der Dienst nach jedem Punkt; jeder ältere Punkt trägt seine Segmente selbst |
| Warnung | Alter des neuesten vollständigen Punkts über 30 Minuten | Überwachung, Umsetzung in TASK-133 |
| Kritisch | über 45 Minuten | darüber, ab 60 Minuten, ist das Ziel aus DEC-038 verletzt |

## Einrichten

Die Betriebsvorlage [deploy/compose.yaml](../deploy/compose.yaml) enthält alles, was hier steht; [INSTALL.md](INSTALL.md) führt durch die Installation. Dieser Abschnitt erklärt die Teile.

### 1. Die Datenbank archiviert ihre Schreibprotokolle

```yaml
db:
  command:
    - postgres
    - -c
    - wal_level=replica
    - -c
    - archive_mode=on
    - -c
    - archive_command=/opt/taskmanager/archive-wal.sh %p %f
    - -c
    - archive_timeout=300
  volumes:
    - db-data:/var/lib/postgresql
    - wal-archive:/archive
    - ./db-init.sh:/docker-entrypoint-initdb.d/10-roles.sh:ro
    - ./db-roles.sh:/opt/taskmanager/db-roles.sh:ro
    - ./db-roles.sql:/opt/taskmanager/db-roles.sql:ro
    - ./archive-wal.sh:/opt/taskmanager/archive-wal.sh:ro
```

### 2. Ein Datenbankkonto für die Sicherung

[db-init.sh](../deploy/db-init.sh) legt beim ersten Start der Datenbank mit [db-roles.sql](../deploy/db-roles.sql) die Rollen der Vorlage an, darunter `taskmanager_backup` mit dem Passwort aus `TASK_MANAGER_BACKUP_DB_PASSWORD` ([INSTALL.md](INSTALL.md), „Datenbankrollen“). Das Konto darf sich als Replikationsclient verbinden (Basissicherung), ein Segment schließen (`pg_switch_wal`) und den Wiederherstellungspunkt schreiben (`pg_create_restore_point`). Lesen darf es seit TASK-136 nur, was `backup.sh` liest: die Migrationshistorie und die Liste der gespeicherten Inhalte `operations_backup_content`; keine Inhalte der Anwendung. Den Schutz des Dateispeichers setzt es nur über die beiden Funktionen `files_purge_hold_begin` und `files_purge_hold_end`, die V0071 bereitstellt und V0072 nur ihm freigibt; die Laufzeitrolle darf sie seit V0073 nicht mehr aufrufen und liest die Tabelle `files_purge_hold` nur (TASK-137). Die Vormerkung einer Rücksicherung schreibt nicht dieses Konto, sondern die Rücksicherung in ihrer abgeschotteten Instanz als Verwaltungskonto (`TASK_MANAGER_RESTORE_DB_USER`, Vorgabe `taskmanager`).

### 3. Speicherbereiche und Besitzer

| Volume | Besitzer | Inhalt |
|---|---|---|
| `wal-archive` | Konto der Datenbank (999) | das Archiv; die Datenbank schreibt, der Dienst liest und räumt alte Segmente weg |
| `backup-state` | Konto des Dienstes (999) | Basissicherung in Arbeit, Zustand, Cache von restic |
| `restore-target` | Konto des Dienstes (999) | das Ergebnis einer Rücksicherung, bleibt nach dem Befehl erhalten |
| `files-data` | Konto der Anwendung (10001) | der Dateispeicher, für den Dienst nur lesbar |

Neue Volumes gehören zunächst root. Der Dienst `prepare` der Vorlage übergibt `wal-archive`, `backup-state` und `restore-target` bei jedem Start an das Konto 999; von Hand ist nichts zu tun.

### 4. Das Sicherungsziel

Unterstützt sind ein S3 kompatibler Objektspeicher (`s3:https://host/bucket`) und SFTP auf einen anderen Rechner (`sftp:konto@host:/pfad`), jeweils mit einem eigenen Zugang, der nach Möglichkeit nicht löschen darf. Ein Verzeichnis auf demselben Rechner ist **nur für Tests** erlaubt: es schützt nicht vor dem Verlust des Rechners.

**SFTP:** restic startet dafür den ssh Client des Images. Das Verzeichnis `TASK_MANAGER_BACKUP_SSH_DIR` (Vorgabe `./ssh` neben der Compose Datei) wird als `~/.ssh` des Dienstes nur lesend eingebunden und enthält:

```sh
mkdir -p ssh
ssh-keygen -t ed25519 -N "" -C taskmanager-backup -f ssh/id_ed25519      # öffentlichen Teil beim Ziel hinterlegen
ssh-keyscan -t ed25519 sicherung.example > ssh/known_hosts                 # Fingerabdruck mit dem Betreiber des Ziels vergleichen
cat > ssh/config <<'TEXT'
Host sicherung.example
  User taskmanager
  IdentityFile ~/.ssh/id_ed25519
  StrictHostKeyChecking yes
  UpdateHostKeys no
TEXT
sudo chown -R 999:999 ssh && sudo chmod 0700 ssh && sudo chmod 0600 ssh/*
```

`StrictHostKeyChecking yes` lässt keine unbekannte Gegenstelle zu; der Fingerabdruck in `known_hosts` wird einmal mit dem Betreiber des Ziels verglichen. Der private Schlüssel gehört wie die anderen Geheimnisse zusätzlich getrennt vom Rechner abgelegt (Abschnitt 6). In `.env` steht dann `TASK_MANAGER_BACKUP_REPOSITORY=sftp:sicherung.example:/taskmanager`.

**S3:** Zugang über `TASK_MANAGER_BACKUP_S3_KEY_ID` und `TASK_MANAGER_BACKUP_S3_SECRET` in `.env`.

### 5. Der Dienst

Der Dienst `backup` der Vorlage läuft mit `loop`. Fehlt eine Pflichtangabe, startet er nicht und nennt sie. Ein einzelner Punkt, etwa vor einem Update: über den laufenden Dienst `docker compose exec backup /opt/backup/entrypoint.sh once`, nur bei angehaltenem Dienst `docker compose run --rm backup once`. Er meldet `point NAME complete (database up to ZEIT, restore point tm-NAME, N contents)`. Ein solcher Punkt und die Punkte des Dienstes laufen nie gleichzeitig: beide nehmen dieselbe Sperre im Volume `backup-state`, und ein zweiter Lauf meldet `another point is being made; this one waits for it` und beginnt nach dem ersten (TASK-136). Der Name eines Punkts und einer Basis ist die Sekunde in UTC, nie dieselbe oder eine frühere als der letzte Name dieses Volumes; zwei Läufe in derselben Sekunde oder nach einem Zeitsprung erhalten die nächste freie Sekunde (TASK-137).

### 6. Die Schlüssel liegen außerhalb der Sicherung

Diese Werte gehören **nicht** in den Sicherungsstand, sondern getrennt zum Betreiber, etwa in einen Passwortmanager oder Tresor:

| Wert | Ohne ihn |
|---|---|
| `RESTIC_PASSWORD` (`TASK_MANAGER_BACKUP_PASSWORD`) | ist kein Sicherungspunkt lesbar; der ganze Bestand ist verloren. |
| Zugangsdaten des Ziels, bei SFTP das Verzeichnis `ssh` | kommt niemand an den Bestand. |
| `TASK_MANAGER_SECRET_KEY` der Anwendung | sind Postfachpasswörter, Git Tokens, Anwendungsgeheimnisse und KI Schlüssel nach einer Rücksicherung unbrauchbar und müssen neu eingetragen werden; alles andere, auch Tickets, Dateien und Anmeldungen, funktioniert. |
| `TASK_MANAGER_DB_PASSWORD` (Verwaltungskonto), `TASK_MANAGER_MIGRATE_DB_PASSWORD` (Migrationsrolle), `TASK_MANAGER_APP_DB_PASSWORD` (Laufzeitrolle), `TASK_MANAGER_BACKUP_DB_PASSWORD` (Sicherungsrolle) | melden sich Verwaltung, `migrate`, Webprozess und Ausführer oder der Dienst `backup` an der zurückgespielten Datenbank nicht an, weil die Datenbank die Passwörter ihrer Rollen mitbringt. Nach einem Wechsel eines Passworts gilt für einen älteren Punkt das Passwort aus der Zeit dieses Punkts; die alte `.env` deshalb aufbewahren, solange Punkte aus ihrer Zeit bestehen. |

Am einfachsten wird die ganze `.env` mit dem Verzeichnis `ssh` getrennt abgelegt. Beim Einrichten einmal prüfen, dass alles dort vorhanden ist, und das in der Vorlage unten festhalten. Eine alte Version des Anwendungsschlüssels nicht löschen, solange noch Sicherungen aus ihrer Zeit aufbewahrt werden.

## Wiederanlauf nach dem Verlust des Rechners

Der Ablauf gilt für einen vollständigen Ausfall und für den Rückweg nach einem Update ([INSTALL.md](INSTALL.md)). Die Zeiten aus DEC-038 laufen ab dem Feststellen des Ausfalls innerhalb der vereinbarten Servicezeit. Alle Befehle laufen im Verzeichnis der Vorlage auf dem Ersatzrechner; genau diese Folge spielt die Probe durch.

1. **Ausfall feststellen, Zeitpunkt notieren.** Ersatzrechner mit derselben Plattform bereitstellen (Linux x86 64, Ubuntu 24.04 LTS, Docker mit Compose). Das Release **derselben Version** holen und entpacken wie in [INSTALL.md](INSTALL.md), Schritt 1. Die abgelegte `.env` und bei SFTP das Verzeichnis `ssh` danebenlegen, mit denselben Werten wie zuvor. Weder `migrate` noch `bootstrap-admin` noch `up` ausführen.
2. **Speicherbereiche vorbereiten und Punkte ansehen:**
   ```sh
   docker compose pull
   docker compose run --rm prepare
   docker compose run --rm --no-deps backup points
   ```
   Die Liste zeigt die vollständigen Punkte, den neuesten zuerst. Auch mit `--no-deps` startet nur das Image mit dem Digest aus `.env`: die Vorlage kennt die Images nur als `ghcr.io/1uedev/task-manager-backup@DIGEST` und `ghcr.io/1uedev/task-manager@DIGEST`, ein Tag kann an ihre Stelle nicht treten (TASK-137). `docker compose run --rm images` nennt einen falschen Wert vorher mit Grund.
3. **Zurückspielen und beweisen:**
   ```sh
   docker compose run --rm --no-deps backup restore /restore              # der neueste Punkt
   docker compose run --rm --no-deps backup restore /restore 20261123T120000Z   # oder ein bestimmter
   ```
   Der Befehl holt Punkt und Basissicherung, startet im Container eine abgeschottete PostgreSQL ohne Netz, die das Schreibprotokoll genau bis zum Wiederherstellungspunkt des Punkts nachspielt und öffnet, und prüft jeden Inhalt, den diese Datenbank nennt, mit Größe und SHA-256 im Dateispeicher. Erst dann schreibt sie die Vormerkung, die Webserver und Ausführer am Start hindert, und hält an. Scheitert ein Schritt, ist der Punkt verworfen, der Befehl nennt den Grund und nimmt den nächstälteren; einen falschen Erfolg gibt es nicht (Prüffall O08). Ergebnis im Volume `restore-target`: `pgdata` (bereits erholt), `files`, `manifest.json` und `point`.
4. **An ihren Platz legen:** `docker compose run --rm restore-place`. Der Befehl kopiert die Datenbank in das Volume `db-data` und den Dateispeicher in `files-data`, jeweils mit ihrem Besitzer, und lehnt ab, wenn eines davon schon Daten enthält.
5. **Datenbank starten:** `docker compose up -d --wait db`. Sie öffnet ohne Nachspielen. Die Vormerkung prüfen: `docker compose exec db psql -U taskmanager -d taskmanager -c 'select * from operations_restore_pending'` zeigt den Punkt.
6. **Vormerkung bestehen lassen.** Sie steht seit Schritt 3 in der Datenbank. Ein Start von Webserver oder Ausführer vor Schritt 7 bricht mit „was not released yet“ ab; ein Start ohne sie würde alte Sitzungen gelten lassen und Versandaufträge wiederholen. Kein Startparameter hebt die Sperre auf; nur die Befehle des Images (`after-restore`, `migrate`, `bootstrap-admin`, …) laufen trotz Vormerkung.
7. **Freigeben:** `docker compose run --rm app after-restore "<Name der Person>"`. Der Befehl beendet alle Sitzungen, auch die des Portals, hält alle offenen Mailantworten als „unsicher“ an, verwirft die KI Vorschläge, prüft zu jedem Anhang und Wikibild Inhalt und Prüfsumme, nennt die Konten, deren Rechte für das ganze Unternehmen gelten, und entfernt die Vormerkung. Alles geschieht in einer Transaktion unter der Sperre der Vormerkung: scheitert etwas, ist nichts geändert und die Vormerkung bleibt; ein zweiter gleichzeitiger Aufruf wird mit `restore.busy` abgelehnt. Rückgabewerte: 0 alles stimmt, 3 gelaufen, aber Inhalte fehlen oder sind beschädigt, 2 abgelehnt, 1 unerwarteter Fehler ohne Änderung.
8. **Bekannte Änderungen nach dem Punkt nachziehen.** Kontosperren und entzogene Rechte seit dem Sicherungspunkt kennt die Anwendung nicht; der Befehl listet dafür die Konten mit Rechten für das ganze Unternehmen. Lokale Passwörter stehen wieder auf dem Stand des Punkts. Unklare Konten vor dem Start sperren.
9. **Alles starten und Kernabläufe prüfen:** `docker compose up -d`, dann anmelden, Projekt öffnen, Ticket ändern, suchen, Anhang herunterladen. Danach Datenverlust, Wiederanlaufzeit, verwendeten Punkt und Entscheidungen festhalten. Der Sicherungsdienst beginnt auf dem neuen Zeitstrahl der Datenbank von selbst mit einer neuen Basissicherung.

### Was `after-restore` abfängt

| Punkt | Verhalten | Prüffall |
|---|---|---|
| Alte Sitzungen | alle beendet, auch die des Portals, Grund `restore` | O10 |
| Mailantworten in „wartet“ oder „wird gesendet“ | „unsicher“ mit `helpdesk.restored`; der zurückgespielte Auftrag findet sie nicht mehr wartend und sendet nichts. Eine Person sendet erneut oder vermerkt den Versand, wie bei jeder unsicheren Antwort. | O09 |
| KI Vorschläge | verworfen; der Nachweis der Läufe bleibt | |
| Dateispeicher | jeder Inhalt der Datenbank wird mit Größe und SHA-256 geprüft, fehlende und abweichende werden mit Kennung genannt | O07 |
| Unternehmensrechte | alle Konten mit ihren Rechten aufgelistet | O10 |
| Mailabruf | holt nach dem Start erneut, was nach dem Punkt im Postfach ankam; die daraus entstandenen Tickets fehlen im Sicherungsstand und entstehen neu | |

Der Bericht nennt nur Kennungen und Zahlen, keine Inhalte, und erscheint erst nach der bestätigten Transaktion. Was er tat, steht mit Zeitpunkt, Person und Punkt in `operations_audit_event`.

## Die Probe

`tools/restore/restore-check.sh` spielt den vollständigen Verlust gegen ein synthetisches SFTP Ziel in einem eigenen Container durch, mit Schlüssel und geprüftem Hostschlüssel wie in Abschnitt 4. Die Probe baut ihre Images und Demodaten aus dem ganzen Quellstand; sie liegt deshalb nicht im Paket der Version, sondern läuft in einem Klon des Repositorys am Tag der Version (`git clone --branch vVERSION https://github.com/1uedev/task-manager`, TASK-137):

1. Installation mit Sicherung, Demodaten und den Daten, die die Rücksicherung tragen oder anhalten muss: zwei Anhänge und ein Wikibild mit bekannten Prüfsummen, eine Portalsitzung, ein KI Vorschlag, eine Mailantwort in „wird gesendet“ (der synthetische Mailserver hält die Verbindung) und eine in „wartet“, beide mit ihren Aufträgen. Ihr Bestehen wird vor der Sicherung geprüft.
2. Zwei Punkte, während nichts schreibt, und die Rücksicherung des zweiten.
3. Der Dienst im Dauerbetrieb scheitert an der Basissicherung, an einer unlesbaren Datei (restic Status 3) und am Ziel; keiner dieser Läufe bietet einen Punkt an, kein Kandidat bleibt liegen.
4. Laufende Schreibvorgänge mit Protokoll, ein Schreiben vor, eine offene Transaktion über und ein Schreiben nach der Grenze eines Punkts, ein abgelehnter Punkt mit fehlendem Inhalt (O06).
5. Drei Schäden, die die Sicherung nicht sehen kann: ein ersetzter Inhalt gleicher Größe, ein fehlendes Segment in der Mitte und eine verlorene Basissicherung.
6. Löschen von Datenbank, Dateispeicher, Archiv und Zustand des Dienstes.
7. Eine Rücksicherung in ein zu kleines Ziel scheitert mit Meldung; die richtige verwirft die drei beschädigten Punkte nacheinander mit Grund und nimmt den intakten (O08), danach `restore-place`.
8. Webserver und Ausführer verweigern den Start vor `after-restore`; danach sind Sitzungen beendet, beide Antworten „unsicher“, der Vorschlag verworfen. Webserver und Ausführer laufen, der Mailserver nimmt jetzt alles an, und es kommt keine Mail und keine Verbindung an.
9. Die Datenbank steht genau an der Grenze: das Schreiben davor ist da, die offene Transaktion und das Schreiben danach nicht; jedes vor `time` bestätigte Schreiben ist da, keines, das nach `timeAfter` abging. Anmelden, Projekt öffnen, Ticket ändern, suchen, beide Anhänge und das Bild byteweise herunterladen.

```sh
tools/restore/restore-check.sh            # baut die Images und räumt am Ende alles weg
RESTORE_CHECK_SKIP_BUILD=1 tools/restore/restore-check.sh   # mit bereits gebauten Images
```

Gemessen wird:

- **Datenverlust:** die Zeit zwischen dem letzten bestätigten Schreiben vor dem Ausfall und dem jüngsten Schreiben, das die zurückgespielte Installation noch hat. Der Schreiber ist vorher angehalten; Zeitpunkt und Nummer stammen aus derselben letzten Zeile. Ein negativer Wert oder eine Nummer über der letzten bestätigten lässt die Probe scheitern. Weil die drei neuesten Punkte absichtlich beschädigt sind, enthält der Wert auch die Zeit bis zu ihnen.
- **Wiederanlaufzeit:** vom Ausfall bis nach den bestandenen Kernabläufen, einschließlich Rücksicherung, `after-restore` und Prüfung.

Der Bericht `restore-run.json` nennt Rechner, Ziel, verwendete Punkte, Grenze, beide Werte und die Ziele (3600 s und 14400 s). Er gilt für den Rechner des Laufs und für die Datenmenge des Laufs. **Der Nachweis auf der Referenzumgebung mit vollem Daten- und Dateibestand ist Teil von TASK-134; nach DEC-038 muss er vor der ersten Kundenzusage vorliegen.**

## Zuständigkeit je Installation

Diese Vorlage gehört ausgefüllt zu jeder Installation; ohne erreichbares Betriebspersonal dürfen die Ziele aus DEC-038 nicht als zugesagt gelten.

| Angabe | Wert |
|---|---|
| Betreiber (Rechner, Netz, Sicherungsziel) | |
| Servicezeit, in der die vier Stunden gelten | |
| Ansprechpartner und Erreichbarkeit | |
| Sicherungsziel und Zugang | |
| Ablage von `RESTIC_PASSWORD`, Zielzugang und `TASK_MANAGER_SECRET_KEY` | |
| Letzte erfolgreiche Probe (Datum, Datenverlust, Wiederanlaufzeit) | |

## Ausgehende Verbindungen

Jede Verbindung der Installation nach außen mit ihrem Zweck. Es gibt keine Telemetrie, keine Lizenzprüfung und keine Verbindung zum Hersteller.

| Ziel | Zweck | Ohne Verbindung |
|---|---|---|
| Sicherungsziel (S3 oder SFTP) | Sicherungspunkte | kein neuer vollständiger Punkt; die Überwachung meldet das Alter |
| Mailserver (IMAP, SMTP) | Helpdesk | Rückstand, sichtbar; nichts geht verloren |
| Verzeichnisdienst (LDAP) | Anmeldung von Verzeichniskonten | 503 bei diesen Anmeldungen; lokale Konten gehen |
| Git Anbieter | Verknüpfungen an Tickets und Anforderungen | Verknüpfungen zeigen ihren Fehler |
| KI Anbieter | die KI Funktionen | Funktionen ausgegraut, sonst nichts |
| Image Registry | Updates | die laufende Installation arbeitet weiter |

Tickets, Wiki, Anforderungen, Zeiten und Suche arbeiten ohne jede dieser Verbindungen weiter.

## Grenzen

1. Ein einzelner Rechner ist keine Hochverfügbarkeit. Die vier Stunden setzen Ersatzkapazität und erreichbares Personal voraus.
2. Geprüft ist PostgreSQL 18 als Container aus der Lieferung. Eine eigene Instanz des Kunden muss Version 18 haben und nach demselben Verfahren gesichert werden.
3. Der Dateispeicher ist ein Verzeichnis. Netzwerkfreigaben und Objektspeicher sind keine geprüfte Referenz.
4. Die Rücksicherung ist ein Weg für die ganze Installation. Einzelne Tickets, Projekte oder Dateien lassen sich damit nicht zurückholen.
5. Aufbewahrungsregeln mit Löschung von Fachdaten sind nicht Teil davon (OPEN-016).
6. Der gemessene Lauf der Probe belegt die Ziele für seinen Rechner und seine Datenmenge, nicht für die Referenzumgebung (TASK-134).
