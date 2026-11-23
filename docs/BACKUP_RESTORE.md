# Sicherung und Wiederherstellung einer Installation (TASK-131)

Stand 23. November 2026 · Ergebnis von TASK-131 zur Prüfung (DEC-161). Diese Anleitung beschreibt den Sicherungsdienst, den gemeinsamen Sicherungspunkt, die Rücksicherung in eine frische Umgebung, den Befehl `after-restore` und die Probe, mit der Datenverlust und Wiederanlaufzeit gemessen werden. Die Planungsziele stehen in DEC-038: höchstens eine Stunde Datenverlust und höchstens vier Stunden bis zum geprüften Wiederbetrieb. Der Entwurf dahinter ist [OPERATIONS.md](OPERATIONS.md).

Nicht Teil dieser Anleitung: TLS, Installationsvorlage und Updates (TASK-132), Betriebsübersicht und Überwachung (TASK-133), die Messung auf der Referenzumgebung und die V1 Freigabe (TASK-134). Die Vorführung aus [DEMO.md](DEMO.md) bleibt unverändert und ist kein Betrieb.

Die Betriebsvorlage `deploy/compose.yaml` mit diesem Sicherungsdienst, die Installation und der Updateweg stehen in [INSTALL.md](INSTALL.md) (TASK-132).

## Überblick

| Teil | Inhalt |
|---|---|
| [deploy/backup/Dockerfile](../deploy/backup/Dockerfile) | Das Image des Sicherungsdiensts: die Clientwerkzeuge von PostgreSQL 18 und restic 0.18.1, mit Tag und Digest festgelegt. Kein Port, eigenes Konto, keine Zugangsdaten im Image. |
| [deploy/backup/backup.sh](../deploy/backup/backup.sh) | Der Dienst: alle 15 Minuten ein Sicherungspunkt, täglich eine Basissicherung, Aufbewahrung 14 Tage. |
| [deploy/backup/restore.sh](../deploy/backup/restore.sh) | Punkte auflisten, einen Punkt in eine frische Umgebung zurückspielen, die Rücksicherung vormerken. |
| [tools/restore/restore-check.sh](../tools/restore/restore-check.sh) | Die Probe: spielt den vollständigen Verlust durch und misst Datenverlust und Wiederanlaufzeit. Läuft in CI als Workflow `Restore`. |
| [tools/restore/compose.restore.yaml](../tools/restore/compose.restore.yaml) | Die Umgebung der Probe: Installation mit Sicherung und eine zweite, leere Umgebung für die Rücksicherung. |
| Befehl `after-restore` | Bereitet eine zurückgespielte Installation vor und gibt sie frei. Teil des Anwendungsimages. |

## Was ein Sicherungspunkt ist

Ein Sicherungspunkt ist **ein** restic Stand mit drei Dingen und einem Zeitpunkt T:

1. dem Archiv der Schreibprotokolle der Datenbank, vollständig bis T,
2. dem Dateispeicher, gelesen **nach** T, also gleich alt oder jünger,
3. der Beschreibung (`manifest.json`) mit T, der Schreibposition, der Kennung der Basissicherung, der Schemaversion und der Zahl der Inhalte.

Die Rücksicherung stellt die Datenbank genau auf T. Dateien sind unveränderlich abgelegt: ein jüngerer Dateistand bringt höchstens Inhalte mit, zu denen es keine Zeile gibt, und die räumt der vorhandene Aufräumlauf später weg (siehe [backend/README.md](../backend/README.md), „Betrieb und Sicherung“). Umgekehrt wäre es ein Fehler, und genau das verhindert diese Reihenfolge.

Ein Punkt gilt erst als vollständig, wenn restic ihn auf dem Ziel bestätigt hat. **Fehlt ein Inhalt, den die Datenbank nennt, wird der Punkt abgelehnt** (Prüffall O06): der vorige Punkt bleibt der neueste, und die Überwachung seines Alters zeigt den Rückstand. Eine grüne Sicherung, die sich nicht zurückspielen lässt, gibt es damit nicht.

Takt und Grenzen (bestätigte Regeln 7 und 8 von TASK-131):

| Größe | Wert | Bedeutung |
|---|---|---|
| Wechsel der Schreibprotokolle | spätestens alle 5 Minuten | `archive_timeout=300` in der Datenbank |
| Sicherungspunkt | alle 15 Minuten | `TASK_MANAGER_BACKUP_INTERVAL`, Vorgabe 900 s |
| Basissicherung | täglich | `TASK_MANAGER_BACKUP_BASE_AGE`, Vorgabe 86400 s |
| Aufbewahrung | 14 Tage vollständig wiederherstellbar | `TASK_MANAGER_BACKUP_RETENTION`, Vorgabe 14 |
| Warnung | Alter des neuesten vollständigen Punkts über 30 Minuten | Überwachung, Umsetzung in TASK-133 |
| Kritisch | über 45 Minuten | darüber, ab 60 Minuten, ist das Ziel aus DEC-038 verletzt |

## Einrichten

### 1. Die Datenbank archiviert ihre Schreibprotokolle

In der Compose Datei der Installation:

```yaml
command:
  - postgres
  - -c
  - wal_level=replica
  - -c
  - archive_mode=on
  - -c
  - archive_command=test ! -f /archive/%f && cp %p /archive/%f
  - -c
  - archive_timeout=300
volumes:
  - db-data:/var/lib/postgresql
  - wal-archive:/archive
```

Das Volume `wal-archive` wird im Sicherungsdienst nur lesend eingebunden, der Dateispeicher ebenfalls.

### 2. Ein Datenbankkonto für die Sicherung

Der Dienst liest die Datenbank mit einem eigenen Konto. Es braucht `REPLICATION` für die Basissicherung, das Recht, ein Schreibprotokoll zu schließen, und Lesen auf die Sicht der Inhalte:

```sql
create role taskmanager_backup login replication password '<langes Zufallspasswort>';
grant execute on function pg_switch_wal() to taskmanager_backup;
grant select on operations_backup_content to taskmanager_backup;
grant select on flyway_schema_history to taskmanager_backup;
```

Das Konto braucht keine Rechte an Fachtabellen. In `pg_hba.conf` beziehungsweise über die Umgebung der Datenbank muss es sich auch als `replication` verbinden dürfen.

### 3. Das Sicherungsziel

Unterstützt sind ein S3 kompatibler Objektspeicher (`s3:https://host/bucket`) und SFTP auf einen anderen Rechner (`sftp:konto@host:/pfad`), jeweils mit einem eigenen Zugang, der nach Möglichkeit nicht löschen darf. Ein Verzeichnis auf demselben Rechner ist **nur für Tests** erlaubt: es schützt nicht vor dem Verlust des Rechners.

### 4. Der Dienst

```yaml
backup:
  build: ./deploy/backup
  depends_on:
    db:
      condition: service_healthy
  environment:
    TASK_MANAGER_BACKUP_REPOSITORY: s3:https://speicher.example/taskmanager
    RESTIC_PASSWORD: ${TASK_MANAGER_BACKUP_PASSWORD:?}
    AWS_ACCESS_KEY_ID: ${BACKUP_TARGET_KEY:?}
    AWS_SECRET_ACCESS_KEY: ${BACKUP_TARGET_SECRET:?}
    PGHOST: db
    PGUSER: taskmanager_backup
    PGPASSWORD: ${TASK_MANAGER_BACKUP_DB_PASSWORD:?}
    PGDATABASE: taskmanager
  volumes:
    - wal-archive:/archive:ro
    - files-data:/data/files:ro
    - backup-state:/state
```

Fehlt eine Pflichtangabe, startet der Dienst nicht und nennt sie. Ein einzelner Punkt zum Prüfen: `docker compose run --rm backup once`.

### 5. Die Schlüssel liegen außerhalb der Sicherung

Drei Werte gehören **nicht** in den Sicherungsstand, sondern getrennt zum Betreiber, etwa in einen Passwortmanager oder Tresor:

| Wert | Ohne ihn |
|---|---|
| `RESTIC_PASSWORD` | ist kein Sicherungspunkt lesbar; der ganze Bestand ist verloren. |
| Zugangsdaten des Ziels | kommt niemand an den Bestand. |
| `TASK_MANAGER_SECRET_KEY` der Anwendung | sind Postfachpasswörter, Git Tokens, Anwendungsgeheimnisse und KI Schlüssel nach einer Rücksicherung unbrauchbar und müssen neu eingetragen werden; alles andere, auch Tickets, Dateien und Anmeldungen, funktioniert. |

Beim Einrichten einmal prüfen, dass alle drei dort vorhanden sind, und das in der Vorlage unten festhalten. Eine alte Version des Anwendungsschlüssels nicht löschen, solange noch Sicherungen aus ihrer Zeit aufbewahrt werden.

## Wiederanlauf nach dem Verlust des Rechners

Der Ablauf gilt für einen vollständigen Ausfall. Die Zeiten aus DEC-038 laufen ab dem Feststellen des Ausfalls innerhalb der vereinbarten Servicezeit.

1. **Ausfall feststellen, Zeitpunkt notieren.** Ersatzrechner mit derselben Plattform bereitstellen (Linux x86 64, Ubuntu 24.04 LTS, Docker mit Compose), Anwendungsimage in derselben Version, Schlüssel und Zugangsdaten des Ziels zur Hand.
2. **Punkte ansehen:** `docker compose run --rm backup /opt/backup/restore.sh points` zeigt die Punkte, den neuesten zuerst.
3. **Zurückspielen:** `docker compose run --rm backup /opt/backup/restore.sh restore /restore` nimmt den neuesten Punkt, oder `... restore /restore 20261123T120000Z` einen bestimmten. Dabei wird geprüft, dass der Stand lesbar ist, die Beschreibung vollständig, die Basissicherung vorhanden und die Zahl der Inhalte erreicht. Scheitert das, nimmt der Befehl den nächstälteren Punkt und sagt es; einen falschen Erfolg gibt es nicht (Prüffall O08). Ergebnis sind `/restore/pgdata` (startklar, erholt sich bis T), `/restore/files` und `/restore/manifest.json`.
4. **Besitzer setzen.** Die Rücksicherung schreibt als Konto des Sicherungsdienstes. `/restore/pgdata` gehört damit bereits dem Konto der Datenbank; den Dateispeicher übergibt der Betrieb dem Konto der Anwendung: `chown -R 10001:10001 /restore/files`. Ohne diesen Schritt startet der Webserver nicht, weil er in den Dateispeicher schreiben muss.
5. **Datenbank starten.** Der Datenbankcontainer bindet `/restore` unter demselben Pfad ein und setzt `PGDATA=/restore/pgdata`. Die Datenbank spielt die Schreibprotokolle bis T nach und öffnet dann; bis dahin antwortet sie nicht.
6. **Rücksicherung vormerken:** `restore.sh mark /restore` schreibt die Zeile, die Webserver und Ausführer am Start hindert. Die Rücksicherung schreibt sie, bevor jemand arbeitet; ein Start ohne sie würde alte Sitzungen gelten lassen und Versandaufträge wiederholen.
7. **Freigeben:** `docker compose run --rm app after-restore "<Name der Person>"`. Der Befehl beendet alle Sitzungen, hält alle offenen Mailantworten als „unsicher“ an, verwirft die KI Vorschläge, prüft zu jedem Anhang und Wikibild Inhalt und Prüfsumme, nennt die Konten, deren Rechte für das ganze Unternehmen gelten, also aus Regeln ohne Projekt stammen, und entfernt die Vormerkung. Rückgabewerte: 0 alles stimmt, 3 gelaufen, aber Inhalte fehlen oder sind beschädigt, 2 abgelehnt, 1 unerwarteter Fehler.
8. **Bekannte Änderungen nach T nachziehen.** Kontosperren und entzogene Rechte seit dem Sicherungspunkt kennt die Anwendung nicht; der Befehl listet dafür die Konten mit Rechten für das ganze Unternehmen. Lokale Passwörter stehen wieder auf dem Stand von T. Unklare Konten vor der Freigabe sperren.
9. **Webserver und Ausführer starten, Kernabläufe prüfen** (anmelden, Projekt öffnen, Ticket ändern, Suche, Anhang herunterladen). Danach Datenverlust, Wiederanlaufzeit, verwendeten Punkt und Entscheidungen festhalten.

### Was `after-restore` abfängt

| Punkt | Verhalten | Prüffall |
|---|---|---|
| Alte Sitzungen | alle beendet, Grund `restore` | O10 |
| Mailantworten in „wartet“ oder „wird gesendet“ | „unsicher“ mit `helpdesk.restored`; der zurückgespielte Auftrag findet sie nicht mehr wartend und sendet nichts. Eine Person sendet erneut oder vermerkt den Versand, wie bei jeder unsicheren Antwort. | O09 |
| KI Vorschläge | verworfen; der Nachweis der Läufe bleibt | |
| Dateispeicher | jeder Inhalt der Datenbank wird mit Größe und SHA-256 geprüft, fehlende und abweichende werden mit Kennung genannt | O07 |
| Unternehmensrechte | alle Konten mit ihren Rechten aufgelistet | O10 |
| Mailabruf | holt nach dem Start erneut, was nach T im Postfach ankam; die daraus entstandenen Tickets fehlen im Sicherungsstand und entstehen neu | |

Der Bericht nennt nur Kennungen und Zahlen, keine Inhalte. Was er tat, steht mit Zeitpunkt, Person und Punkt in `operations_audit_event`.

## Die Probe

`tools/restore/restore-check.sh` spielt den vollständigen Verlust durch: Installation mit Sicherung, Demodaten, laufende Schreibvorgänge mit Protokoll jedes bestätigten Schreibens, ein abgelehnter Punkt mit fehlendem Inhalt (O06), Löschen von Datenbank, Dateispeicher und Archiv, ein beschädigter neuester Punkt mit Rückfall auf den älteren (O08), `after-restore`, der geprüfte Wiederbetrieb und der Bericht.

```sh
tools/restore/restore-check.sh            # baut die Images und räumt am Ende alles weg
RESTORE_CHECK_SKIP_BUILD=1 tools/restore/restore-check.sh   # mit bereits gebauten Images
```

Gemessen wird:

- **Datenverlust:** die Zeit zwischen dem letzten bestätigten Schreiben vor dem Ausfall und dem jüngsten Schreiben, das die zurückgespielte Installation noch hat. Jede Zeile des Protokolls ist ein Schreiben, das die Installation bestätigt hat.
- **Wiederanlaufzeit:** vom Ausfall bis zur geprüften Nutzbarkeit, einschließlich Rücksicherung, `after-restore` und Prüfung.

Der Bericht `restore-run.json` nennt Rechner, verwendete Punkte, beide Werte und die Ziele (3600 s und 14400 s). Er gilt für den Rechner des Laufs und für die Datenmenge des Laufs. **Der Nachweis auf der Referenzumgebung mit vollem Daten- und Dateibestand ist Teil von TASK-134; nach DEC-038 muss er vor der ersten Kundenzusage vorliegen.**

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
