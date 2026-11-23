# Installation, Version und Update

Diese Anleitung gilt für den Betrieb einer Installation mit der Betriebsvorlage `deploy/compose.yaml` (TASK-132, DEC-162). Die Vorführung im Hauptverzeichnis (`compose.yaml`, [DEMO.md](DEMO.md)) bleibt davon unberührt und ist kein Betrieb. Sicherung und Wiederherstellung stehen in [BACKUP_RESTORE.md](BACKUP_RESTORE.md), die Anforderungen an den Betrieb in [OPERATIONS.md](OPERATIONS.md).

## Überblick

| Teil | Was es ist |
|---|---|
| Version | Schema `MAJOR.MINOR.PATCH`, entsteht nur, wenn Holger auf main ein Tag `vMAJOR.MINOR.PATCH` setzt |
| Images | `ghcr.io/1uedev/task-manager` und `ghcr.io/1uedev/task-manager-backup`, nur über den Digest; der Dienst `images` lehnt jede andere Angabe ab, auch ein Tag (TASK-136) |
| Betriebsvorlage | `deploy/compose.yaml` mit Caddy (TLS), Webprozess, Ausführer, PostgreSQL und Sicherungsdienst; `deploy/compose.proxy.yaml` für einen eigenen Proxy statt Caddy |
| Datenbankrollen | Verwaltung (`taskmanager`), Migration (`taskmanager_migrate`, besitzt das Schema), Laufzeit (`taskmanager_app`, nur Zeilen), Sicherung (`taskmanager_backup`) |
| Schemaänderung | genau einmal je Update mit `docker compose run --rm migrate`; Webprozess und Ausführer ändern das Schema nie und dürfen es mit ihrer Rolle auch nicht |
| Rückkehr | nur durch Rücksicherung des Sicherungspunkts von vor dem Update, nie durch ein älteres Image |

## Versionsmatrix

Geprüft und unterstützt ist genau diese Zusammenstellung. Anderes ist bis zu einer eigenen Prüfung nicht unterstützt (Regel 2 von TASK-131).

| Bestandteil | Version | Herkunft |
|---|---|---|
| Betriebssystem des Hosts | Ubuntu 24.04 LTS, x86 64 | Betreiber |
| Docker Engine und Docker Compose | die bei der Freigabe gewartete Version, im Freigabebericht festgehalten (TASK-134) | Betreiber |
| Anwendung | Java 25 (Eclipse Temurin JRE), im Image | Release |
| PostgreSQL | 18.6, Container aus der Lieferung | Vorlage, über Digest |
| TLS Einstieg | Caddy 2.10.2 | Vorlage, über Digest |
| Sicherung | restic 0.18.1 mit den Werkzeugen von PostgreSQL 18.6 | Release (`task-manager-backup`) |

Eine eigene PostgreSQL 18 Instanz des Kunden ist erlaubt, wenn die Sicherung nach demselben Verfahren läuft; die Vorlage geht von der mitgelieferten aus.

## Voraussetzungen

- Ein Host nach der Messreferenz aus [OPERATIONS.md](OPERATIONS.md) (4 vCPU, 16 GB RAM, SSD). Die Grenzen der Vorlage folgen deren Budgets: Webprozess 4 GB und 2 CPU, Ausführer 2 GB und 1 CPU, PostgreSQL 6 GB und 2 CPU, Caddy 256 MB, Sicherung 1 GB; sie lassen sich in `deploy/.env` ändern.
- Ein Name im DNS, der auf den Host zeigt, und die Ports 80 und 443. Für ein Zertifikat über ACME müssen beide aus dem Internet erreichbar sein; mit eigenem Zertifikat reicht 443 im eigenen Netz.
- Ein Sicherungsziel auf einem anderen Rechner: S3 kompatibler Objektspeicher oder SFTP ([BACKUP_RESTORE.md](BACKUP_RESTORE.md)).
- Lesezugriff auf zwei Registries: `ghcr.io` für die beiden Images des Release und `docker.io` für PostgreSQL und Caddy, beide über den Digest in `compose.yaml`. Solange die Pakete in `ghcr.io/1uedev` nicht öffentlich sind, meldet sich der Host mit einem Token mit dem Recht `read:packages` an (`docker login ghcr.io`). Ein eigener Spiegel ist möglich, wenn er dieselben Digests liefert; die Namen in `compose.yaml` und die Prüfung in `check-images.sh` sind dann anzupassen.

## Installation

1. **Release holen.** Aus dem Release der gewünschten Version `task-manager-VERSION-deploy.tar.gz` und `SHA256SUMS-VERSION` laden, mit `sha256sum --check --ignore-missing SHA256SUMS-VERSION` prüfen und entpacken. Das Paket hat den Aufbau des Repositorys: `deploy/` mit `compose.yaml`, `compose.proxy.yaml`, `Caddyfile`, `check-images.sh`, `db-init.sh`, `db-roles.sh`, `db-roles.sql`, `archive-wal.sh`, `restore-place.sh` und `.env.example` mit den Digests genau dieser Version, `docs/` mit dieser Anleitung, [BACKUP_RESTORE.md](BACKUP_RESTORE.md), [OPERATIONS.md](OPERATIONS.md) und den Anleitungen, auf die sie verweisen, und `tools/restore/` mit der Probe der Rücksicherung. Weitere Verweise führen auf das Repository beim Tag der Version. Alle folgenden Befehle laufen im Verzeichnis `deploy/`.
2. **Einstellungen anlegen.** `.env.example` nach `.env` kopieren und mit `chmod 600 .env` schützen. Domain, TLS Art, Sicherungsziel eintragen; die vier Datenbankpasswörter, das Sicherungspasswort und den Schlüssel mit `openssl rand -base64 32` erzeugen. `TASK_MANAGER_SECRET_KEY`, das Sicherungspasswort und die Zugangsdaten des Ziels zusätzlich getrennt vom Host ablegen und prüfen, dass sie dort lesbar sind (Regel 9 von TASK-131).
3. **Zertifikat.** Mit `TASK_MANAGER_TLS=acme` holt Caddy das Zertifikat selbst. Mit `TASK_MANAGER_TLS=own` liegen `certificate.pem` (mit Zwischenzertifikaten) und `key.pem` im Verzeichnis `TASK_MANAGER_TLS_DIR` (Vorgabe `./tls`), lesbar für den Container. Mit einem eigenen Proxy entfällt dieser Schritt (siehe unten).
4. **Images prüfen und holen:** `docker compose run --rm images`, dann `docker compose pull`. Der erste Befehl meldet `both images are referenced by digest` oder lehnt mit Rückgabewert 1 ab und nennt die Zeile; jeder Start prüft dasselbe noch einmal, weil die Datenbank auf diesen Dienst wartet.
5. **Schema anlegen:** `docker compose run --rm migrate`. Die Datenbank legt beim ersten Start die Rollen an (`db-roles.sh`); `migrate` meldet sich als `taskmanager_migrate` an und meldet `Migrations applied: N` und die Schemaversion.
6. **Erste Verwaltung:** `docker compose run --rm app bootstrap-admin <Anmeldename>`. Das Einmalpasswort steht nur in dieser Ausgabe.
7. **Starten:** `docker compose up -d`. Danach unter `https://<Domain>` anmelden und das Passwort ändern.
8. **Sicherung prüfen:** `docker compose logs backup` zeigt nach spätestens 15 Minuten `point NAME complete (database up to ...)`. Vor der ersten Nutzung einmal die Probe aus [BACKUP_RESTORE.md](BACKUP_RESTORE.md) durchgehen und die Vorlage für Zuständigkeiten ausfüllen.

Die Vorlage gibt keinem Container den Docker Socket und keinen privilegierten Modus. Webprozess, Ausführer und Caddy laufen mit schreibgeschütztem Dateisystem, ohne Linux Capabilities (Caddy nur mit dem Recht, Port 80 und 443 zu öffnen) und ohne Rechteerweiterung. Daten liegen nur in den benannten Volumes `db-data`, `files-data`, `wal-archive`, `backup-state`, `restore-target`, `caddy-data` und `caddy-config`; ein Austausch der Container behält sie (Prüffall O01). Das Image enthält keine Passwörter und keine Schlüssel.

Das Zugriffsprotokoll von Caddy hält Methode, Pfad ohne Query, Status und Dauer fest, nie Query, Referrer, Cookies oder `Authorization`: die Rückkehr einer Git Anmeldung trägt dort Code und Zustand (TASK-136, `tools/caddy/check-log.sh` prüft es mit einer synthetischen Rückkehr).

## Datenbankrollen

`db-roles.sql` legt vier Rollen fest (TASK-136):

| Rolle | Wer sie benutzt | Darf |
|---|---|---|
| `taskmanager` (`TASK_MANAGER_DB_PASSWORD`) | nur der Betreiber für Verwaltung und `db-roles.sh` | alles; kein Prozess der Anwendung meldet sich damit an |
| `taskmanager_migrate` (`TASK_MANAGER_MIGRATE_DB_PASSWORD`) | nur `migrate` | besitzt Schema, Tabellen, Folgen und Funktionen, darf sie ändern |
| `taskmanager_app` (`TASK_MANAGER_APP_DB_PASSWORD`) | Webprozess, Ausführer und alle anderen Befehle | Zeilen lesen und schreiben, Folgen benutzen, Funktionen ausführen; keine Tabelle anlegen, ändern, leeren oder löschen, keine Rollen |
| `taskmanager_backup` (`TASK_MANAGER_BACKUP_DB_PASSWORD`) | der Sicherungsdienst | Replikation für die Basissicherung, Wiederherstellungspunkt und Wechsel des Schreibprotokolls, die Migrationshistorie, die Liste der gespeicherten Inhalte und die Sperre der Bereinigung (Migration V0072); keine Inhalte der Anwendung |

`db-roles.sh` läuft bei der Anlage der Datenbank von selbst und bei jedem Update ein weiteres Mal (Schritt 5 dort); jeder Lauf bringt die Rollen auf diesen Stand. **Übergang für Installationen aus der Zeit vor TASK-136:** Dort besitzt `taskmanager` alle Objekte und Webprozess und Ausführer melden sich damit an. Der Update Schritt mit `db-roles.sh` überträgt alle Tabellen, Sichten, Folgen, Funktionen und Typen an `taskmanager_migrate`, gibt `taskmanager_app` die Rechte auf Zeilen und nimmt `taskmanager_backup` das Leserecht auf alle Tabellen; danach melden sich die Prozesse mit ihren eigenen Rollen an. Vorher die beiden neuen Passwörter in `.env` eintragen.

## Eigener Proxy des Kunden

Ein vorhandener Proxy darf Caddy ersetzen. Der Weg ist vollständig in der Lieferung:

1. In `.env` die Zeilen `COMPOSE_FILE=compose.yaml:compose.proxy.yaml` und `TASK_MANAGER_PROXY_BIND=127.0.0.1:8080` aktivieren. Docker Compose liest damit bei jedem Befehl beide Dateien; `caddy` startet nicht, Port 80 und 443 bleiben frei, und der Webprozess ist nur unter `TASK_MANAGER_PROXY_BIND` erreichbar. Ein Proxy auf einem anderen Rechner braucht die Adresse einer Schnittstelle, die nur er erreicht, nie `0.0.0.0` in einem offenen Netz.
2. `TASK_MANAGER_DOMAIN` bleibt der Name, unter dem Browser die Installation erreichen; die Vorlage setzt daraus `TASK_MANAGER_PUBLIC_URL=https://<Domain>`. `TASK_MANAGER_TLS` und das Zertifikat entfallen.
3. Der Proxy muss:
   - TLS beenden und nur HTTPS anbieten; der Sitzungscookie ist immer `Secure`,
   - `Host` unverändert weitergeben und `X-Forwarded-For`, `X-Forwarded-Proto` und `X-Forwarded-Host` setzen,
   - Anfragen bis 30 MB annehmen (Anhänge bis 25 MB) und Antworten nicht puffern müssen,
   - Lesezeiten von mindestens 60 Sekunden erlauben,
   - `Strict-Transport-Security` setzen,
   - in seinem Zugriffsprotokoll Query und Referrer weglassen oder kürzen, wie es die Vorlage für Caddy tut.
4. Weiter mit Schritt 4 der Installation. Updates und Rückkehr laufen ebenso, nur ohne `caddy` in den Befehlen.

## Was der Start prüft

Nur der Containerbefehl `migrate`, den der Einstiegspunkt des Images auswählt, ändert das Schema; keine Einstellung kann einem anderen Prozess dieses Recht geben (TASK-136). Webprozess, Ausführer und alle anderen Befehle laufen in der Vorlage mit `TASK_MANAGER_SCHEMA_MODE=verify` (Prüffall O11). Für `migrate` und `verify` ist die Prüfung Bedingung des Starts: abgeschaltetes Flyway und jede geänderte Flyway Einstellung (Ablage der Migrationen, Ziel, Historientabelle, Baseline, Reihenfolge) werden abgelehnt, und ein Prozess, der ohne Prüfung bis zum Start käme, bricht ab.

| Lage | Verhalten |
|---|---|
| Schema passt zum Image | Start |
| Migrationen fehlen | kein Start, Meldung „… migration(s) are missing … run the command 'migrate' once“ |
| Datenbank neuer als das Image | kein Start, Meldung „… newer than this image …“; auch `migrate` lehnt mit Rückgabewert 2 ab |
| eine Migration ist früher gescheitert | kein Start, Meldung „… failed in an earlier run …“; Rückkehr zum Punkt von vor dem Update |
| eine Migration scheitert in diesem Lauf von `migrate` | Rückgabewert 2, Meldung „A migration failed in this run; the schema stays at version …“ mit der Ursache |
| geänderte Flyway Einstellungen | kein Start, Meldung „The schema check refuses changed Flyway settings (…)“ mit den Namen |
| unbekannter Wert von `TASK_MANAGER_SCHEMA_MODE` | kein Start, Meldung nennt die erlaubten Werte `migrate` und `verify`, nie den Wert selbst |

`migrate` läuft genau einmal: Flyway sperrt seine Historie, ein zweiter Lauf zur selben Zeit wartet und findet nichts mehr zu tun, ein späterer meldet `Migrations applied: 0`. Rückgabewerte: 0 erledigt oder schon aktuell, 2 abgelehnt oder in diesem Lauf gescheitert, 1 unerwarteter Fehler. Die Vorführung migriert wie bisher beim Start (`TASK_MANAGER_SCHEMA_MODE=migrate`, Vorgabe).

## Update

Ein Update unterbricht den Betrieb für die Dauer von `migrate` und dem Start, in der Regel wenige Minuten.

1. **Hinweise der Version lesen.** Das Release nennt die Digests und Besonderheiten. Das Paket der bisherigen Version aufbewahren; es ist Teil des Rückwegs.
2. **Sicherungspunkt anlegen.** Über den laufenden Dienst: `docker compose exec backup /opt/backup/entrypoint.sh once` legt sofort einen Punkt an und meldet `point NAME complete (database up to ...)`. Macht der Dienst gerade selbst einen Punkt, meldet der Befehl `another point is being made; this one waits for it` und beginnt danach; beide teilen eine Sperre im Volume `backup-state` (TASK-136). Jeder Punkt und jede Basis erhalten einen eigenen Namen, auch zwei Läufe in derselben Sekunde (TASK-137). Den Namen notieren; er ist der Rückweg. Nur bei angehaltenem Dienst `backup` gilt stattdessen `docker compose run --rm backup once`.
3. **Anhalten:** `docker compose stop app worker caddy`.
4. **Neue Version eintragen:** alle Dateien aus `deploy/` des neuen Pakets über die bisherigen legen, `.env` behalten und darin `TASK_MANAGER_IMAGE` und `TASK_MANAGER_BACKUP_IMAGE` auf die beiden Zeilen aus `digests-VERSION.env` des Release setzen. Neue Einstellungen aus `.env.example` übernehmen (seit TASK-136 `TASK_MANAGER_MIGRATE_DB_PASSWORD` und `TASK_MANAGER_APP_DB_PASSWORD`). Dann `docker compose run --rm images` und `docker compose pull`.
5. **Rollen nachführen:** `docker compose up -d db`, dann `docker compose exec db sh /opt/taskmanager/db-roles.sh`. Der Befehl ist bei jedem Update gefahrlos und überträgt beim ersten Mal die Objekte einer älteren Installation an die Migrationsrolle.
6. **Schema ändern:** `docker compose run --rm migrate`, genau einmal.
7. **Starten:** `docker compose up -d`.
8. **Kernabläufe prüfen:** anmelden, Projekt öffnen, Ticket ändern, suchen, Anhang hochladen und herunterladen, Seite der Hintergrundaufträge ansehen.

## Rückkehr

Es gibt keinen Weg zurück über ein älteres Image: die Datenbank ist dann neuer als das Image, und der Start wird abgelehnt. Der einzige Rückweg ist die Rücksicherung des Punkts aus Schritt 2. Was nach diesem Punkt geschrieben wurde, geht dabei verloren; deshalb wird früh entschieden. Die Befehle laufen in `deploy/` mit genau der gelieferten Vorlage; `tools/update/update-check.sh` geht sie nach einem absichtlich gescheiterten Update bis zum Vergleich der Inhalte durch (TASK-136).

1. **Anhalten:** `docker compose stop`.
2. **Vorherige Version eintragen:** `deploy/` aus dem aufbewahrten Paket der vorherigen Version zurücklegen und in `.env` deren Digests eintragen, dann `docker compose run --rm images` und `docker compose pull`. Wer innerhalb derselben Version zurückkehrt, überspringt diesen Schritt.
3. **Punkt zurückspielen und beweisen:** `docker compose run --rm --no-deps backup points` zeigt die Punkte; `docker compose run --rm --no-deps backup restore /restore NAME` mit dem Namen aus Update Schritt 2. Der Befehl meldet `point NAME restored and proven`.
4. **Daten des gescheiterten Updates entfernen:** `docker compose down`, dann
   ```sh
   project=$(docker compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')
   docker volume rm "${project}_db-data" "${project}_files-data"
   ```
   Wer den Stand des gescheiterten Updates zur Analyse behalten will, sichert beide Volumes vorher. Alle anderen Volumes bleiben, auch das Ziel der Rücksicherung.
5. **An ihren Platz legen und freigeben:** `docker compose run --rm restore-place`, `docker compose up -d db`, `docker compose run --rm app after-restore "<Name der Person>"`, dann `docker compose up -d`. Bedeutung und Rückgabewerte wie in [BACKUP_RESTORE.md](BACKUP_RESTORE.md), Schritte 4 bis 9; die Vormerkung schreibt die Rücksicherung in ihrer abgeschotteten Instanz als `taskmanager` (`TASK_MANAGER_RESTORE_DB_USER`).
6. **Kernabläufe prüfen** wie nach einem Update.

## Prüfung im Repository

| Workflow | Wann | Was |
|---|---|---|
| Update | bei Änderungen an Migrationen, Start der Anwendung (`TaskManagerApplication`, `application.yaml`), Betriebsmodul, Vorlage, Demodaten, dieser Anleitung oder Prüfung, auf Anforderung | nimmt als ältere Version das neueste veröffentlichte Release, das nicht dieser Stand ist, mit Vorlage und Digests aus dem Release (bis zur ersten Version den festen Stand vor Paket A18 auf main, gebaut), installiert sie hinter TLS mit Demodaten, Anhang und Wikibild, aktualisiert nach dieser Anleitung und prüft: Tag statt Digest abgelehnt, Rollen übertragen, Start vor `migrate` abgelehnt, alle Zeilen von 27 Fachtabellen und jede Datei des Dateispeichers unverändert, Kernabläufe mit Hoch und Herunterladen vor und nach dem Austausch der Container, Laufzeitrolle ohne Schemarechte, ein eigener Punkt neben einem laufenden des Dienstes, die Rückkehr nach einem absichtlich gescheiterten Update auf genau diesen Punkt und die Härtung der Container. Der Bericht `update-run.json` nennt Basis und Ziel mit Images und die Menge der geprüften Daten |
| Release | nur auf ein Tag `vMAJOR.MINOR.PATCH` auf main, nach Holgers Freigabe in der Umgebung `release` | baut beide Images, erzeugt Komponentenverzeichnis (SPDX) und Schwachstellenbericht, veröffentlicht in `ghcr.io/1uedev` und legt Digests, Berichte, Prüfsummen und das Paket der Vorlage zur Version |
| Restore | wöchentlich und bei Änderungen am Sicherungsweg | die Probe aus [BACKUP_RESTORE.md](BACKUP_RESTORE.md) |

Ohne Tag wird nichts veröffentlicht. Die Prüfung des Tags (`tools/release/check-tag.sh`) und das Paket (`tools/release/bundle.py`, mit Prüfung jedes Verweises) testet `tests/test_release_tools.py` ohne Veröffentlichung.

## Veröffentlichen einer Version

Zwei Schutzstufen in den Einstellungen des Repositorys sorgen dafür, dass nur Holger eine Version veröffentlicht (TASK-136). Holger richtet sie einmal ein; ohne sie wartet der Workflow `Release` nicht auf eine Freigabe.

1. **Umgebung `release`:** Settings, Environments, New environment `release`. Unter „Deployment protection rules“ „Required reviewers“ mit Holger als einzigem Prüfer, „Prevent self-review“ aus, damit er selbst freigeben kann. Unter „Deployment branches and tags“ „Selected branches and tags“ mit der Regel für Tags `v*`. Der Job `release` läuft erst nach seiner Freigabe an; vorher gibt es kein Token mit `packages: write`.
2. **Tag Regelsatz:** Settings, Rules, Rulesets, New tag ruleset „Versionen“, Enforcement „Active“, Target „Include by pattern“ `v*`, Regeln „Restrict creations“, „Restrict updates“, „Restrict deletions“ und „Block force pushes“; unter „Bypass list“ nur die Rolle „Repository admin“ oder Holgers Konto.
3. **Version setzen:** auf dem gewünschten Commit von main `git tag vX.Y.Z` und `git push origin vX.Y.Z`, dann im Workflow `Release` die Ausführung freigeben. Ob ein Befund des Schwachstellenberichts eine Freigabe verhindert, entscheidet die V1 Freigabeprüfung (TASK-134).

## Ausgehende Verbindungen

Zusätzlich zu der Liste in [BACKUP_RESTORE.md](BACKUP_RESTORE.md):

| Ziel | Zweck | Ohne Verbindung |
|---|---|---|
| ACME Dienst (Let's Encrypt), nur mit `TASK_MANAGER_TLS=acme` | Zertifikat und Erneuerung | das vorhandene Zertifikat gilt bis zu seinem Ablauf; danach eigenes Zertifikat |
| `ghcr.io` | die Images der Anwendung und des Sicherungsdienstes bei Installation und Update | die laufende Installation arbeitet weiter |
| `docker.io` | PostgreSQL und Caddy bei Installation und Update | die laufende Installation arbeitet weiter |

Es gibt keine Telemetrie und keine Verbindung zum Hersteller.

## Grenzen

1. Updates unterbrechen den Betrieb; unterbrechungsfreie und automatische Updates gehören nicht zu V1.
2. Die Debian Pakete im Image des Sicherungsdienstes (ssh Client) kommen beim Bau aus den Paketquellen und sind nicht einzeln festgelegt; das Komponentenverzeichnis der Version hält sie fest, und der Workflow `Release` nennt die Version von `openssh-client` daraus und bricht ab, wenn sie fehlt.
3. Der Weg vom festen Stand vor Paket A18 kennt den Sicherungsdienst noch nicht; Installationen beginnen mit der ersten Version, die ihn enthält.
4. Die Pakete in `ghcr.io` sind nach der ersten Veröffentlichung privat, bis Holger sie freigibt.
