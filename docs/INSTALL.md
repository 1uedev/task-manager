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

1. **Release holen.** Aus dem Release der gewünschten Version `task-manager-VERSION-deploy.tar.gz` und `SHA256SUMS-VERSION` laden, mit `sha256sum --check --ignore-missing SHA256SUMS-VERSION` prüfen und entpacken. Das Paket hat den Aufbau des Repositorys: `deploy/` mit `compose.yaml`, `compose.proxy.yaml`, `Caddyfile`, `check-images.sh`, `db-init.sh`, `db-roles.sh`, `db-roles.sql`, `archive-wal.sh`, `restore-place.sh` und `.env.example` mit den Digests genau dieser Version, `docs/` mit dieser Anleitung, [BACKUP_RESTORE.md](BACKUP_RESTORE.md), [OPERATIONS.md](OPERATIONS.md) und den Anleitungen, auf die sie verweisen, Die Probe der Rücksicherung und alle weiteren Verweise führen auf das Repository beim Tag der Version, weil sie Buildkontexte und Demodaten des Quellstands brauchen (TASK-137). Der Workflow `Release` prüft das Paket vor der Veröffentlichung entpackt in einem frischen Verzeichnis (`tools/release/check-package.sh`). Alle folgenden Befehle laufen im Verzeichnis `deploy/`.
2. **Einstellungen anlegen.** `.env.example` nach `.env` kopieren und mit `chmod 600 .env` schützen. Domain, TLS Art, Sicherungsziel eintragen; die vier Datenbankpasswörter, das Sicherungspasswort und den Schlüssel mit `openssl rand -base64 32` erzeugen. `TASK_MANAGER_SECRET_KEY`, das Sicherungspasswort und die Zugangsdaten des Ziels zusätzlich getrennt vom Host ablegen und prüfen, dass sie dort lesbar sind (Regel 9 von TASK-131).
3. **Zertifikat.** Mit `TASK_MANAGER_TLS=acme` holt Caddy das Zertifikat selbst. Mit `TASK_MANAGER_TLS=own` liegen `certificate.pem` (mit Zwischenzertifikaten) und `key.pem` im Verzeichnis `TASK_MANAGER_TLS_DIR` (Vorgabe `./tls`), lesbar für den Container. Mit einem eigenen Proxy entfällt dieser Schritt (siehe unten).
4. **Images prüfen und holen:** `docker compose run --rm images`, dann `docker compose pull`. In `.env` stehen nur die beiden Digests `TASK_MANAGER_IMAGE_DIGEST` und `TASK_MANAGER_BACKUP_IMAGE_DIGEST` aus `digests-VERSION.env`; `compose.yaml` setzt sie hinter die festen Namen `ghcr.io/1uedev/task-manager@` und `ghcr.io/1uedev/task-manager-backup@`. Damit kann bei keinem Befehl ein Tag an ihre Stelle treten, auch nicht bei `pull`, `up --no-deps` oder `run --no-deps`: Docker lehnt die Angabe als ungültige Referenz ab (TASK-137). Der Dienst `images` nennt einen falschen Wert vorher mit Grund und meldet sonst `both images are referenced by digest`.
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

**Eigentümer der Erweiterung `pg_trgm`** (Suche nach Tags, V0033): PostgreSQL kann eine Erweiterung keinem anderen Eigentümer übergeben; sie behält die Rolle, die sie angelegt hat (TASK-139). In einer neuen Installation und in einer aus der Zeit vor TASK-136 ist das `taskmanager`, in einer Installation mit der Vorlage von TASK-136 `taskmanager_migrate`, weil dort V0033 mit dieser Rolle lief. Beides ist ein unterstützter Zustand und bleibt nach jedem Update so; nie gehört sie `taskmanager_app`. Ihre Funktionen gehören immer dem Eigentümer der Erweiterung, `taskmanager_app` darf sie ausführen und nichts an ihr ändern. Den Eigentümer zeigt `docker compose exec db psql -U taskmanager -d taskmanager -c "select pg_get_userbyid(extowner) from pg_extension where extname = 'pg_trgm'"`.

## Eigener Proxy des Kunden

Ein vorhandener Proxy darf Caddy ersetzen. Der Weg ist vollständig in der Lieferung:

1. In `.env` die Zeilen `COMPOSE_FILE=compose.yaml:compose.proxy.yaml` und `TASK_MANAGER_PROXY_BIND=127.0.0.1:8080` aktivieren. Docker Compose liest damit bei jedem Befehl beide Dateien; `caddy` startet nicht, Port 80 und 443 bleiben frei, und der Webprozess ist nur unter `TASK_MANAGER_PROXY_BIND` erreichbar. Für einen Proxy auf demselben Rechner bleibt es bei `127.0.0.1`.
   **Proxy auf einem anderen Rechner.** Die Adresse allein begrenzt nur die Schnittstelle, nicht wer sie erreicht. Die Quelle muss zusätzlich begrenzt werden (TASK-137), auf eine von zwei Arten:
   - ein eigenes Netz nur zwischen Proxy und Host (eigenes VLAN oder eine Punkt zu Punkt Verbindung wie WireGuard), und `TASK_MANAGER_PROXY_BIND` auf die Adresse des Hosts in genau diesem Netz, oder
   - eine Regel der Host Firewall, die den Port nur für die Adresse des Proxys öffnet. Docker umgeht Regeln in `INPUT` für veröffentlichte Ports; die Regel gehört deshalb in die Kette `DOCKER-USER`, zum Beispiel `iptables -I DOCKER-USER -p tcp -m conntrack --ctorigdst <Adresse des Hosts> --ctorigdstport <Port> ! -s <Adresse des Proxys> -j DROP` mit Adresse und Port aus `TASK_MANAGER_PROXY_BIND` (bei mehreren Schnittstellen zusätzlich mit `-i <Schnittstelle>`), dauerhaft gespeichert. Die Regel prüft den ursprünglichen Zielendpunkt vor der Adressumsetzung von Docker; ein `--dport 8080` träfe nach der Umsetzung auch andere Dienste des Hosts mit demselben Port im Container (TASK-139, H4).
   Danach den Zugang zweimal prüfen: vom Proxy liefert `curl -sS -o /dev/null -w '%{http_code}\n' -H 'Host: <Domain>' http://<Adresse>:<Port>/actuator/health/readiness` den Status `200`, von einem anderen Rechner im selben Netz endet derselbe Aufruf ohne Verbindung (Zeitüberschreitung oder abgelehnt). Beides mit Datum festhalten; ohne die zweite Prüfung gilt der Zugang nicht als begrenzt. Nie `0.0.0.0` in einem offenen Netz.
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

Nur der Containerbefehl `migrate` ändert das Schema. Die Vorlage startet jeden Prozess der Anwendung über den Einstiegspunkt `java -jar /app/app.jar operations`; ein so gestarteter Prozess prüft immer nur und migriert nie, gleich was `TASK_MANAGER_SCHEMA_MODE`, ein Profil, eine Umgebungsvariable oder eine andere Schreibweise sagt: ein anderer Wert als leer oder `verify` wird mit Meldung abgelehnt (TASK-137). Webprozess, Ausführer und alle anderen Befehle laufen damit wie `TASK_MANAGER_SCHEMA_MODE=verify` (Prüffall O11). Für `migrate` und `verify` ist die Prüfung Bedingung des Starts: abgeschaltetes Flyway und jede geänderte Flyway Einstellung der wirksamen Konfiguration (Ablage der Migrationen, Ziel, Historientabelle, Schemas, Baseline, Reihenfolge, Prüfungen, Namensregeln, Platzhalter, eigene Migrationen und Resolver und weitere) werden abgelehnt, ebenso ein Image, das keine seiner Migrationen findet, und ein Prozess, der ohne Prüfung bis zum Start käme, bricht ab. Die Laufzeitrolle darf die Migrationshistorie und die Sperre der Bereinigung seit V0073 nur lesen.

| Lage | Verhalten |
|---|---|
| Schema passt zum Image | Start |
| Migrationen fehlen | kein Start, Meldung „… migration(s) are missing … run the command 'migrate' once“ |
| Datenbank neuer als das Image | kein Start, Meldung „… newer than this image …“; auch `migrate` lehnt mit Rückgabewert 2 ab |
| eine Migration ist früher gescheitert | kein Start, Meldung „… failed in an earlier run …“; Rückkehr zum Punkt von vor dem Update |
| eine Migration scheitert in diesem Lauf von `migrate` | Rückgabewert 2, Meldung „A migration failed in this run; the schema stays at version … Do not start any version on this database: return …“ mit der Ursache; weiter mit „Rückkehr“ |
| geänderte Flyway Einstellungen | kein Start, Meldung „The schema check refuses changed Flyway settings (…)“ mit den Namen; verglichen wird jeder Wert der wirksamen Flyway Konfiguration außer Verbindung und Laden, auch Platzhalter, Callbacks und Anlage von Schemas (TASK-139) |
| eine der beiden Imageangaben in `.env` ist kein Digest oder eine Angabe der Form vor TASK-137 steht darin | kein Start dieses Prozesses, auch bei `run --no-deps`, Rückgabewert 2 (Sicherungsdienst 1), Meldung „Refused: <Name> must be the digest …“ (TASK-139) |
| unbekannter Wert von `TASK_MANAGER_SCHEMA_MODE` | kein Start, Meldung nennt die erlaubten Werte `migrate` und `verify`, nie den Wert selbst |

`migrate` läuft genau einmal: Flyway sperrt seine Historie, ein zweiter Lauf zur selben Zeit wartet und findet nichts mehr zu tun, ein späterer meldet `Migrations applied: 0`. Rückgabewerte: 0 erledigt oder schon aktuell, 2 abgelehnt oder in diesem Lauf gescheitert, 1 unerwarteter Fehler. Die Vorführung startet ohne `operations` und migriert wie bisher beim Start (`TASK_MANAGER_SCHEMA_MODE` leer oder `migrate`).

## Update

Ein Update unterbricht den Betrieb für die Dauer von `migrate` und dem Start, in der Regel wenige Minuten.

1. **Hinweise der Version lesen.** Das Release nennt die Digests und Besonderheiten. Das Paket der bisherigen Version aufbewahren; es ist Teil des Rückwegs.
2. **Sicherungspunkt anlegen.** Über den laufenden Dienst: `docker compose exec backup /opt/backup/entrypoint.sh once` legt sofort einen Punkt an und meldet `point NAME complete (database up to ...)`. Macht der Dienst gerade selbst einen Punkt, meldet der Befehl `another point is being made; this one waits for it` und beginnt danach; beide teilen eine Sperre im Volume `backup-state` (TASK-136). Jeder Punkt und jede Basis erhalten einen eigenen Namen, auch zwei Läufe in derselben Sekunde (TASK-137). Den Namen notieren; er ist der Rückweg. Nur bei angehaltenem Dienst `backup` gilt stattdessen `docker compose run --rm backup once`.
3. **Anhalten:** `docker compose stop app worker caddy`.
4. **Neue Version eintragen:** eine Kopie von `deploy/` mit `.env` als Paket der bisherigen Version aufbewahren. Dann alle Dateien aus `deploy/` des neuen Pakets über die bisherigen legen, `.env` behalten und darin `TASK_MANAGER_IMAGE_DIGEST` und `TASK_MANAGER_BACKUP_IMAGE_DIGEST` auf die beiden Zeilen aus `digests-VERSION.env` des Release setzen; die Zeilen `TASK_MANAGER_IMAGE` und `TASK_MANAGER_BACKUP_IMAGE` aus der Zeit vor TASK-137 entfernen, der Dienst `images` lehnt sie ab. Neue Einstellungen aus `.env.example` übernehmen (seit TASK-136 `TASK_MANAGER_MIGRATE_DB_PASSWORD` und `TASK_MANAGER_APP_DB_PASSWORD`). Dann `docker compose run --rm images` und `docker compose pull`.
5. **Rollen nachführen:** `docker compose up -d --wait db` (wartet, bis die Datenbank Verbindungen annimmt), dann `docker compose exec db sh /opt/taskmanager/db-roles.sh`. Der Befehl ist bei jedem Update gefahrlos und überträgt beim ersten Mal die Objekte einer älteren Installation an die Migrationsrolle.
6. **Schema ändern:** `docker compose run --rm migrate`, genau einmal.
7. **Starten:** `docker compose up -d`.
8. **Kernabläufe prüfen:** anmelden, Projekt öffnen, Ticket ändern, suchen, Anhang hochladen und herunterladen, Seite der Hintergrundaufträge ansehen.

## Rückkehr

Es gibt keinen Weg zurück über ein älteres Image: die Datenbank ist dann neuer als das Image, und der Start wird abgelehnt. Das gilt auch, wenn `migrate` in einem Lauf scheitert: Migrationen vor der gescheiterten sind dann schon bestätigt, und `migrate` meldet mit Rückgabewert 2 die Version, auf der das Schema steht, und verweist auf diesen Abschnitt. Der einzige Rückweg ist die Rücksicherung des Punkts aus Schritt 2 mit dem Paket und der `.env` der vorherigen Version aus Schritt 4. Was nach diesem Punkt geschrieben wurde, geht dabei verloren; deshalb wird früh entschieden. `tools/update/update-check.sh` geht genau diese Schritte durch: Punkt vor dem Wechsel, eine synthetische nächste Version, deren zweite neue Migration wirklich scheitert, Rückkehr mit dem aufbewahrten Paket bis zum Vergleich aller Inhalte (TASK-137).

1. **Anhalten:** `docker compose stop`.
2. **Vorherige Version eintragen:** `deploy/` und `.env` aus dem aufbewahrten Paket der vorherigen Version zurücklegen, dann `docker compose run --rm images` und `docker compose pull`. Die `.env` aus der Zeit des Punkts gehört dazu, weil die zurückgespielte Datenbank die Passwörter ihrer Rollen mitbringt. Wer innerhalb derselben Version zurückkehrt, überspringt diesen Schritt.
3. **Punkt zurückspielen und beweisen:** `docker compose run --rm --no-deps backup points` zeigt die Punkte; der Name aus Update Schritt 2 muss darin stehen. `docker compose run --rm --no-deps backup restore /restore NAME` mit genau diesem Namen. Der Befehl meldet `point NAME restored and proven`; fehlt die Meldung, nicht weitermachen.
4. **Daten des gescheiterten Updates entfernen**, in drei getrennten Schritten (TASK-139). Erst kontrollieren, dann sichern, dann löschen; der Löschbefehl prüft die Kontrolle selbst noch einmal.
   1. **Kontrollieren:**
      ```sh
      docker compose down
      docker compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])'
      docker volume ls --filter "label=com.docker.compose.project=task-manager"
      ```
      Die erste Ausgabe muss der Name dieser Installation sein (in der Vorlage `task-manager`), die Liste muss `task-manager_db-data` und `task-manager_files-data` zeigen. Weicht etwas ab, hier aufhören: dann liegt das Verzeichnis einer anderen Installation vor.
   2. **Sichern, wenn der Stand des gescheiterten Updates zur Analyse bleiben soll:** beide Volumes vor dem Löschen als Archiv ablegen, zum Beispiel
      ```sh
      for volume in db-data files-data; do
        docker run --rm -v "task-manager_$volume:/from:ro" -v "$PWD:/to" --entrypoint tar \
          postgres:18.6 -czf "/to/gescheitert-$volume.tar.gz" -C /from .
      done
      ```
   3. **Löschen**, erst nachdem 1 stimmt und 2 erledigt oder bewusst ausgelassen ist. Der Block trägt den erwarteten Namen fest ein und löscht nur, wenn Projekt und die Labels beider Volumes ihn bestätigen; sonst meldet er den Grund und löscht nichts:
      ```sh
      (
        set -eu
        expected=task-manager        # der Name aus Schritt 1
        project=$(docker compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')
        [ "$project" = "$expected" ] || { echo "abgebrochen: Projekt $project statt $expected" >&2; exit 1; }
        for volume in db-data files-data; do
          [ "$(docker volume inspect -f '{{index .Labels "com.docker.compose.project"}}' "${project}_$volume")" = "$expected" ] \
            && [ "$(docker volume inspect -f '{{index .Labels "com.docker.compose.volume"}}' "${project}_$volume")" = "$volume" ] \
            || { echo "abgebrochen: ${project}_$volume gehört nicht zu $expected" >&2; exit 1; }
        done
        docker volume rm "${expected}_db-data" "${expected}_files-data"
      )
      ```
   Alle anderen Volumes bleiben, auch das Ziel der Rücksicherung.
5. **An ihren Platz legen:** `docker compose run --rm restore-place`, dann `docker compose up -d --wait db`.
6. **Freigeben:** `docker compose run --rm app after-restore "<Name der Person>"`. Rückgabewert 0: weiter. Rückgabewert 3: Inhalte fehlen oder sind beschädigt; nicht starten, sondern nach [BACKUP_RESTORE.md](BACKUP_RESTORE.md) einen anderen Punkt prüfen. 2 oder 1: nichts ist geändert, Meldung lesen.
7. **Rechte abgleichen:** die von `after-restore` genannten Konten mit Rechten für das ganze Unternehmen durchsehen und Sperren oder Entzüge seit dem Punkt nachziehen, bevor jemand arbeitet (Schritt 8 in [BACKUP_RESTORE.md](BACKUP_RESTORE.md)).
8. **Starten:** `docker compose up -d`. Die Vormerkung schreibt die Rücksicherung in ihrer abgeschotteten Instanz als `taskmanager` (`TASK_MANAGER_RESTORE_DB_USER`).
9. **Kernabläufe prüfen** wie nach einem Update.

## Prüfung im Repository

| Workflow | Wann | Was |
|---|---|---|
| Update | bei Änderungen an Migrationen, Start der Anwendung (`TaskManagerApplication`, `application.yaml`), Betriebsmodul, Vorlage, Demodaten, dieser Anleitung oder Prüfung, auf Anforderung | nimmt als ältere Version das neueste veröffentlichte Release, das nicht dieser Stand ist und in seiner Geschichte liegt, mit Vorlage und Digests aus dem Release; nur solange kein stabiles Release veröffentlicht ist, den festen Stand vor Paket A18 auf main, gebaut. Gibt es Releases, aber keinen passenden Vorgänger, bricht die Prüfung mit eigenem Grund ab (TASK-137). Installiert die ältere Version hinter TLS mit Demodaten, Anhang und Wikibild, aktualisiert nach dieser Anleitung und prüft: ein Tag wird von der Vorlage bei `pull`, `up --no-deps` und `run --no-deps` abgelehnt, Rollen übertragen, ein synthetischer Fehler von `db-roles.sh` bei vollständiger Protokollierung zeigt kein Passwort, Start vor `migrate` abgelehnt, alle Zeilen aller Tabellen, die der Seed füllt (ohne eine benannte Liste technischer Tabellen), und jede Datei des Dateispeichers unverändert, mit einem absichtlichen Verlust als Gegenprobe, Kernabläufe mit Suche nach dem geänderten Ticket, aktivem Ausführer, Hoch und Herunterladen vor und nach dem Austausch der Container, Laufzeitrolle ohne Schemarechte, ein eigener Punkt neben einem laufenden des Dienstes, ein Update auf eine synthetische nächste Version, deren Migration wirklich scheitert, und die Rückkehr mit Paket und `.env` der vorherigen Version auf genau diesen Punkt, die Metadaten der Images ohne Geheimnisse und die Härtung der Container. Seit TASK-139 außerdem: je eine falsche Imageangabe neben einer gültigen bei `run --no-deps`, je Phase ein frisch fällig gemachter und erfolgreich beendeter Auftrag `files.cleanup`, jede Dateilesung einzeln geprüft, der Löschblock aus „Rückkehr“ erst mit falschem, dann mit richtigem Namen, und ein gespeicherter Fehler einer dem Image unbekannten Migration mit eigener Meldung. Der Bericht `update-run.json` nennt Basis mit Tag oder Commit, Ziel, Images und die geprüften Tabellen |
| Release | von Hand auf main gestartet mit einem Tag `vMAJOR.MINOR.PATCH`, dessen Commit im Stand von main beim Start liegt (Job `check`, nur Code von main), nach Holgers Freigabe in der Umgebung `release` | baut beide Images, erzeugt Komponentenverzeichnis (SPDX) und Schwachstellenbericht, veröffentlicht in `ghcr.io/1uedev` und legt Digests, Berichte, Prüfsummen und das Paket der Vorlage zur Version |
| Restore | wöchentlich und bei Änderungen am Sicherungsweg | die Probe aus [BACKUP_RESTORE.md](BACKUP_RESTORE.md) |

Ohne Tag wird nichts veröffentlicht. Die Prüfung des Tags (`tools/release/check-tag.sh`) und das Paket (`tools/release/bundle.py`, mit Prüfung jedes Verweises) testet `tests/test_release_tools.py` ohne Veröffentlichung.

## Veröffentlichen einer Version

Zwei Schutzstufen in den Einstellungen des Repositorys sorgen dafür, dass nur Holger eine Version veröffentlicht (TASK-136, TASK-137, TASK-139). Holger richtet sie einmal ein und bestätigt die Kontrolle; ohne sie wartet der Workflow `Release` nicht auf eine Freigabe. Die Einrichtung ist sein offener Schritt.

1. **Umgebung `release`:** Settings, Environments, New environment `release`. Unter „Deployment protection rules“ „Required reviewers“ mit Holgers persönlichem Konto als einzigem Prüfer, kein Team und keine Rolle, „Prevent self-review“ aus, damit er selbst freigeben kann. „Allow administrators to bypass configured protection rules“ ausschalten, damit auch ein Konto mit Administratorrechten ohne seine Freigabe nichts veröffentlicht. Unter „Deployment branches and tags“ „Selected branches and tags“ mit genau der Regel für den Branch `main`, weil der Lauf auf main startet. Der Job `release` läuft erst nach seiner Freigabe an; vorher gibt es kein Token mit `packages: write`.
2. **Tag Regelsatz mit persönlicher Ausnahme:** Der Einstellungsdialog bietet für die „Bypass list“ Rollen, Teams und Apps an; die persönliche Ausnahme für ein einzelnes Konto legt die REST Schnittstelle mit dem Typ `User` an. Mit der GitHub CLI, angemeldet als Holger:

   ```sh
   LOGIN=holgers-login                      # Holgers persönlicher GitHub Login
   ID=$(gh api "users/$LOGIN" --jq .id)     # die feste Kontonummer, nicht der Name
   gh api -X POST repos/1uedev/task-manager/rulesets --input - <<JSON
   {"name": "Versionen", "target": "tag", "enforcement": "active",
    "conditions": {"ref_name": {"include": ["refs/tags/v*"], "exclude": []}},
    "rules": [{"type": "creation"}, {"type": "update"}, {"type": "deletion"}, {"type": "non_fast_forward"}],
    "bypass_actors": [{"actor_id": $ID, "actor_type": "User", "bypass_mode": "always"}]}
   JSON
   ```

   **Kontrolle des eingetragenen Kontos:** `gh api repos/1uedev/task-manager/rulesets --jq '.[] | select(.name == "Versionen") | .id'` nennt die Nummer des Regelsatzes, `gh api repos/1uedev/task-manager/rulesets/NUMMER --jq '[.enforcement, .bypass_actors]'` muss genau `"active"` und eine Liste mit einem einzigen Eintrag zeigen: `actor_type` `User` und `actor_id` gleich der Ausgabe von `gh api users/$LOGIN --jq .id`. Jeder weitere Eintrag, eine Rolle wie „Repository admin“ (`RepositoryRole`), ein Team oder eine App (`Integration`) ist zu entfernen. Die Liste auch nach jeder Änderung an Rechten oder Konten des Repositorys so prüfen.

   **Ablehnung eines anderen Kontos:** Mit einem zweiten Konto mit Schreibrecht, wenn es eines gibt, wird `git tag v0.0.0 && git push origin v0.0.0` abgelehnt, mit einem Hinweis auf die Regeln des Repositorys; danach das lokale Tag mit `git tag -d v0.0.0` löschen. Ohne zweites Konto belegt die Kontrolle oben mit genau einem Eintrag `User` die Regel. Holger setzt mit seinem Konto sein erstes Tag wie unter 3; ist die Ausnahme nicht wirksam, lehnt GitHub auch seinen Push ab, und der Regelsatz ist zu prüfen.
3. **Version setzen und Lauf starten:** auf dem gewünschten Commit von main `git tag vX.Y.Z` und `git push origin vX.Y.Z`. Danach unter Actions den Workflow `Release` mit „Run workflow“ auf dem Branch `main` starten und `vX.Y.Z` als Tag eintragen (oder `gh workflow run release.yml --ref main -f tag=vX.Y.Z`). Workflow und Prüfung kommen damit aus dem Stand von main, auf dem der Lauf startet, nie aus dem Tag (TASK-139). Der Job `check` holt vom Tag nur die Git Objekte, führt keinen Code des Tags aus und prüft mit Leserechten Form `vMAJOR.MINOR.PATCH` und dass der Commit des Tags in der Geschichte genau dieses Stands von main liegt. **Referenzzeitpunkt** ist der Start des Laufs: Ein Commit, der erst danach auf main kommt, wird abgelehnt; dann den Lauf neu starten. Ein Lauf auf einem anderen Branch wird abgelehnt. Danach die Ausführung freigeben; erst dann läuft im Job `release` Code des Tags, der genau den geprüften Commit baut und abbricht, wenn das Tag inzwischen auf einen anderen zeigt.

Grenze: Wer Holgers Konto benutzt, kann für GitHub Holger sein. Ein gemeinsam genutztes Konto lässt sich durch keine dieser Einstellungen von ihm unterscheiden; sein Konto deshalb mit eigenem zweitem Faktor schützen und nicht teilen.

Ob ein Befund des Schwachstellenberichts eine Freigabe verhindert, entscheidet die V1 Freigabeprüfung (TASK-134).

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
3. Der Weg vom festen Stand vor Paket A18 kennt den Sicherungsdienst noch nicht; Installationen beginnen mit der ersten Version, die ihn enthält. Bis zum ersten Release belegt die Updateprüfung die Rückkehr mit dem Paar aus diesem Stand und einer synthetischen nächsten Version; das Holen privater Images aus `ghcr.io` mit Anmeldung läuft erst ab dem ersten Release mit, die Prüfung der Releasedateien testet `tests/test_update_release_files.py` mit synthetischen Dateien.
4. Die Pakete in `ghcr.io` sind nach der ersten Veröffentlichung privat, bis Holger sie freigibt.
