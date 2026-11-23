# Installation, Version und Update

Diese Anleitung gilt für den Betrieb einer Installation mit der Betriebsvorlage `deploy/compose.yaml` (TASK-132, DEC-162). Die Vorführung im Hauptverzeichnis (`compose.yaml`, [DEMO.md](DEMO.md)) bleibt davon unberührt und ist kein Betrieb. Sicherung und Wiederherstellung stehen in [BACKUP_RESTORE.md](BACKUP_RESTORE.md), die Anforderungen an den Betrieb in [OPERATIONS.md](OPERATIONS.md).

## Überblick

| Teil | Was es ist |
|---|---|
| Version | Schema `MAJOR.MINOR.PATCH`, entsteht nur, wenn Holger auf main ein Tag `vMAJOR.MINOR.PATCH` setzt |
| Images | `ghcr.io/1uedev/task-manager` und `ghcr.io/1uedev/task-manager-backup`, in der Vorlage nur über den Digest |
| Betriebsvorlage | `deploy/compose.yaml` mit Caddy (TLS), Webprozess, Ausführer, PostgreSQL und Sicherungsdienst |
| Schemaänderung | genau einmal je Update mit `docker compose run --rm migrate`; Webprozess und Ausführer ändern das Schema nie |
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
- Lesezugriff auf die Images in `ghcr.io/1uedev`. Solange die Pakete nicht öffentlich sind, meldet sich der Host mit einem Token mit dem Recht `read:packages` an (`docker login ghcr.io`).

## Installation

1. **Release holen.** Aus dem Release der gewünschten Version `task-manager-VERSION-deploy.tar.gz` und `SHA256SUMS-VERSION` laden, mit `sha256sum --check --ignore-missing SHA256SUMS-VERSION` prüfen und entpacken. Das Paket enthält `compose.yaml`, `Caddyfile`, `db-init.sh`, `.env.example` mit den Digests genau dieser Version und diese Anleitung.
2. **Einstellungen anlegen.** `.env.example` nach `.env` kopieren und mit `chmod 600 .env` schützen. Domain, TLS Art, Sicherungsziel eintragen; Passwörter und Schlüssel mit `openssl rand -base64 32` erzeugen. `TASK_MANAGER_SECRET_KEY`, das Sicherungspasswort und die Zugangsdaten des Ziels zusätzlich getrennt vom Host ablegen und prüfen, dass sie dort lesbar sind (Regel 9 von TASK-131).
3. **Zertifikat.** Mit `TASK_MANAGER_TLS=acme` holt Caddy das Zertifikat selbst. Mit `TASK_MANAGER_TLS=own` liegen `certificate.pem` (mit Zwischenzertifikaten) und `key.pem` im Verzeichnis `TASK_MANAGER_TLS_DIR` (Vorgabe `./tls`), lesbar für den Container.
4. **Images holen:** `docker compose pull`.
5. **Schema anlegen:** `docker compose run --rm migrate`. Der Befehl meldet `Migrations applied: N` und die Schemaversion.
6. **Erste Verwaltung:** `docker compose run --rm app bootstrap-admin <Anmeldename>`. Das Einmalpasswort steht nur in dieser Ausgabe.
7. **Starten:** `docker compose up -d`. Danach unter `https://<Domain>` anmelden und das Passwort ändern.
8. **Sicherung prüfen:** `docker compose logs backup` zeigt nach spätestens 15 Minuten `point ... complete as snapshot`. Vor der ersten Nutzung einmal die Probe aus [BACKUP_RESTORE.md](BACKUP_RESTORE.md) durchgehen und die Vorlage für Zuständigkeiten ausfüllen.

Die Vorlage gibt keinem Container den Docker Socket und keinen privilegierten Modus. Webprozess, Ausführer und Caddy laufen mit schreibgeschütztem Dateisystem, ohne Linux Capabilities (Caddy nur mit dem Recht, Port 80 und 443 zu öffnen) und ohne Rechteerweiterung. Daten liegen nur in den benannten Volumes `db-data`, `files-data`, `wal-archive`, `backup-state`, `restore-target`, `caddy-data` und `caddy-config`; ein Austausch der Container behält sie (Prüffall O01). Das Image enthält keine Passwörter und keine Schlüssel.

## Eigener Proxy des Kunden

Ein vorhandener Proxy darf Caddy ersetzen. Dann entfällt der Dienst `caddy` (mit einer eigenen `compose.override.yaml`, die den Webprozess nur für den Proxy auf Port 8080 veröffentlicht), und der Proxy muss:

- TLS beenden und nur HTTPS anbieten; der Sitzungscookie ist immer `Secure`,
- `Host` unverändert weitergeben und `X-Forwarded-For` und `X-Forwarded-Proto` setzen,
- Anfragen bis 30 MB annehmen (Anhänge bis 25 MB) und Antworten nicht puffern müssen,
- Lesezeiten von mindestens 60 Sekunden erlauben,
- `Strict-Transport-Security` setzen.

`TASK_MANAGER_PUBLIC_URL` ist die Adresse, unter der Browser die Installation erreichen; die Vorlage setzt sie auf `https://<Domain>`.

## Was der Start prüft

Webprozess, Ausführer und alle Befehle außer `migrate` laufen in der Vorlage mit `TASK_MANAGER_SCHEMA_MODE=verify` (Prüffall O11):

| Lage | Verhalten |
|---|---|
| Schema passt zum Image | Start |
| Migrationen fehlen | kein Start, Meldung „… migration(s) are missing … run the command 'migrate' once“ |
| Datenbank neuer als das Image | kein Start, Meldung „… newer than this image …“; auch `migrate` lehnt mit Rückgabewert 2 ab |
| eine Migration ist früher gescheitert | kein Start, Rücksicherung des Punkts von vor dem Update |

`migrate` läuft genau einmal: Flyway sperrt seine Historie, ein zweiter Lauf zur selben Zeit wartet und findet nichts mehr zu tun, ein späterer meldet `Migrations applied: 0`. Rückgabewerte: 0 erledigt oder schon aktuell, 2 abgelehnt, 1 unerwarteter Fehler. Die Vorführung migriert wie bisher beim Start (`TASK_MANAGER_SCHEMA_MODE=migrate`, Vorgabe).

## Update

Ein Update unterbricht den Betrieb für die Dauer von `migrate` und dem Start, in der Regel wenige Minuten.

1. **Hinweise der Version lesen.** Das Release nennt die Digests und Besonderheiten.
2. **Sicherungspunkt abwarten.** `docker compose run --rm backup once` legt sofort einen Punkt an und meldet `point NAME complete as snapshot`. Den Namen notieren; er ist der Rückweg.
3. **Anhalten:** `docker compose stop app worker caddy`.
4. **Neue Version eintragen:** in `.env` `TASK_MANAGER_IMAGE` und `TASK_MANAGER_BACKUP_IMAGE` auf die Digests des Releases setzen, neue Fassungen von `compose.yaml` und `Caddyfile` aus dem Release übernehmen, dann `docker compose pull`.
5. **Schema ändern:** `docker compose run --rm migrate`, genau einmal.
6. **Starten:** `docker compose up -d`.
7. **Kernabläufe prüfen:** anmelden, Projekt öffnen, Ticket ändern, suchen, Anhang herunterladen, Seite der Hintergrundaufträge ansehen.

## Rückkehr

Es gibt keinen Weg zurück über ein älteres Image: die Datenbank ist dann neuer als das Image, und der Start wird abgelehnt. Der einzige Rückweg ist die Rücksicherung des Punkts aus Schritt 2 nach [BACKUP_RESTORE.md](BACKUP_RESTORE.md), mit den Digests der vorherigen Version in `.env`. Was nach diesem Punkt geschrieben wurde, geht dabei verloren; deshalb wird früh entschieden.

## Prüfung im Repository

| Workflow | Wann | Was |
|---|---|---|
| Update | bei Änderungen an Migrationen, Vorlage, Betriebsmodul oder Prüfung, auf Anforderung | baut die letzte Version (bis zur ersten Version den festen Stand vor Paket A18 auf main) und diesen Stand, installiert die ältere mit der Vorlage hinter TLS und mit Demodaten, aktualisiert nach dieser Anleitung, prüft Datenerhalt, Kernabläufe, Austausch der Container, Sicherungspunkt und die Härtung der Container |
| Release | nur auf ein Tag `vMAJOR.MINOR.PATCH` auf main | baut beide Images, erzeugt Komponentenverzeichnis (SPDX) und Schwachstellenbericht, veröffentlicht in `ghcr.io/1uedev` und legt Digests, Berichte, Prüfsummen und die Vorlage zur Version |
| Restore | wöchentlich und bei Änderungen am Sicherungsweg | die Probe aus [BACKUP_RESTORE.md](BACKUP_RESTORE.md) |

Ohne Tag wird nichts veröffentlicht. Ob ein Befund des Schwachstellenberichts eine Freigabe verhindert, entscheidet die V1 Freigabeprüfung (TASK-134).

## Ausgehende Verbindungen

Zusätzlich zu der Liste in [BACKUP_RESTORE.md](BACKUP_RESTORE.md):

| Ziel | Zweck | Ohne Verbindung |
|---|---|---|
| ACME Dienst (Let's Encrypt), nur mit `TASK_MANAGER_TLS=acme` | Zertifikat und Erneuerung | das vorhandene Zertifikat gilt bis zu seinem Ablauf; danach eigenes Zertifikat |
| `ghcr.io` | Images bei Installation und Update | die laufende Installation arbeitet weiter |

Es gibt keine Telemetrie und keine Verbindung zum Hersteller.

## Grenzen

1. Updates unterbrechen den Betrieb; unterbrechungsfreie und automatische Updates gehören nicht zu V1.
2. Die Debian Pakete im Image des Sicherungsdienstes (ssh Client) kommen beim Bau aus den Paketquellen und sind nicht einzeln festgelegt; das Komponentenverzeichnis der Version hält sie fest.
3. Der Weg vom festen Stand vor Paket A18 kennt den Sicherungsdienst noch nicht; Installationen beginnen mit der ersten Version, die ihn enthält.
4. Die Pakete in `ghcr.io` sind nach der ersten Veröffentlichung privat, bis Holger sie freigibt.
