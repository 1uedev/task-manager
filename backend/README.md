# Backendgrundlage (TASK-022)

Spring-Boot-Anwendung des modularen Monolithen mit PostgreSQL. Dieser Stand enthält Build, leere Modulgrenzen, Konfiguration, gemeinsame Fehlerbehandlung, Datenbankanbindung und den Migrationsweg. Er enthält **keine Fachfunktion**: keine Anmeldung, Rechte, Projekte, Tickets, Tags oder Suche. Diese folgen in TASK-024 bis TASK-029. Versionen stammen aus [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md).

## Voraussetzungen und Befehle

Temurin 25.0.4.1+1 und ein erreichbarer Docker-Daemon für die Integrationstests. Maven 3.9.16 lädt der Apache-Wrapper mit SHA-256-Prüfung. Vom Repository-Stamm:

```sh
cd backend
./mvnw --batch-mode --no-transfer-progress verify
```

Die Integrationstests starten ein flüchtiges PostgreSQL 18.6 über Testcontainers mit festem Digest. Ohne Docker scheitern sie; sie werden nicht übersprungen. Wer lokal ohne Docker arbeitet, kann die Tests mit `-Dtaskmanager.test.jdbc-url=…`, `-Dtaskmanager.test.username=…`, `-Dtaskmanager.test.password=…` und gegebenenfalls `-Dtaskmanager.test.server-version=…` auf einen eigenen leeren Testserver richten. Ein solcher Lauf ist eine Entwicklerhilfe und kein Nachweis für die festgelegte Version; maßgeblich ist der CI-Lauf.

Der Workflow [backend.yml](../.github/workflows/backend.yml) baut, prüft Modulgrenzen und Migrationskonvention, führt die Integrationstests aus, lehnt übersprungene Tests ab und startet danach das gepackte JAR mit [lifecycle-check.sh](scripts/lifecycle-check.sh) gegen eine leere Datenbank: Abweisung ohne Einstellungen, erster Start mit Migration, Stopp per SIGTERM mit geordnetem Herunterfahren, Neustart ohne Schemaänderung.

## Konfiguration

| Umgebungsvariable | Pflicht | Bedeutung |
|---|---|---|
| `TASK_MANAGER_DB_URL` | ja | JDBC-URL der PostgreSQL-Datenbank, zum Beispiel `jdbc:postgresql://db:5432/taskmanager` |
| `TASK_MANAGER_DB_USER` | ja | Datenbankbenutzer |
| `TASK_MANAGER_DB_PASSWORD` | ja | Passwort des Datenbankbenutzers |
| `TASK_MANAGER_HTTP_PORT` | nein | HTTP-Port, Standard 8080 |

Fehlt eine Pflichtangabe, bricht der Start ab, bevor Webserver oder Datenbankverbindung entstehen. Die Meldung nennt alle fehlenden Variablen und nie einen gesetzten Wert. Werte gehören in die Umgebung des Betriebs, nicht ins Repository.

`/actuator/health`, `/actuator/health/liveness` und `/actuator/health/readiness` melden nur den Zustand ohne Details. Weitere Actuator-Endpunkte sind nicht freigegeben. Die Absicherung aller Endpunkte folgt mit TASK-024.

## Module

Jedes direkte Unterpaket von `taskmanager` ist ein Modul. Spring Modulith prüft im Test Zyklen, Zugriffe auf interne Pakete und die in `package-info.java` erlaubten Abhängigkeiten.

| Modul | Aufgabe | Darf verwenden |
|---|---|---|
| `platform` | Kleine technische Verträge; derzeit Korrelationskennung, Problemantworten, Pflichtkonfiguration | nichts |
| `identity` | Konten und Sitzungen (TASK-024) | platform |
| `permissions` | Gruppen und Rechteprüfung (TASK-025) | platform, identity |
| `projects` | Projekte und Konfigurationsversionen (TASK-026) | platform, identity, permissions |
| `tickets` | Ticketkern (TASK-027) | platform, identity, permissions, projects |
| `tags` | Tag-Katalog und Zuordnungen (TASK-028) | platform, identity, permissions, projects |
| `search` | Berechtigte Suchprojektion (TASK-029) | platform, identity, permissions, projects |

Die Tabelle folgt der Ordnung aus [TECHNICAL_CONTRACTS.md](../docs/TECHNICAL_CONTRACTS.md). Wie Tickets, Tags und Suche in einem gemeinsamen Ablauf zusammengesetzt werden, entscheiden TASK-027 bis TASK-029; eine neue erlaubte Abhängigkeit wird dort begründet und im selben PR eingetragen. Öffentlich ist jeweils nur das Modulpaket selbst; Unterpakete sind intern.

## Datenbankmigration

Flyway ist der einzige Weg für Schemaänderungen. Hibernate prüft nur (`ddl-auto: validate`), `clean` ist abgeschaltet, eine Baseline auf eine vorhandene Datenbank erfolgt nicht automatisch.

- Ort: `src/main/resources/db/migration`.
- Name: `V<vierstellige Nummer>__<Modul>_<beschreibung>.sql`, Kleinbuchstaben, Ziffern und Unterstriche, zum Beispiel `V0002__identity_accounts.sql`. Das Modul ist Besitzer der Migration und ihrer Objekte.
- Die nächste freie Nummer wird im Claim-PR der Aufgabe genannt. Zwei gleiche Nummern lassen den Build scheitern; der spätere PR nimmt dann die nächste freie Nummer.
- Eine nach main übernommene Migration wird nie geändert. Korrekturen sind neue Migrationen. Flyway weist geänderte Prüfsummen beim Start ab.
- Ein Modul legt nur eigene Objekte an und liest keine fremden Tabellen.

`MigrationConventionTest` prüft Name, Modul und Eindeutigkeit ohne Datenbank. `V0001__platform_baseline.sql` ist bewusst leer und beginnt die Schemahistorie.

## Fehlerantworten

Fehler aus Controllern werden `application/problem+json` nach RFC 9457 mit `type`, `title`, `status`, `instance` (Anfragepfad), stabilem `code` und `correlationId`. Framework-Texte im Feld `detail`, Stacktraces und Ausnahmemeldungen werden nicht ausgegeben. Jede Anfrage erhält eine vom Server erzeugte Kennung im Kopf `X-Correlation-Id`; ein mitgesendeter Wert wird ignoriert. Die Kennung steht auch in jeder Logzeile der Anfrage.

Erste technische Codes: `request.invalid` (400), `resource.not_found` (404), `request.method_not_allowed` (405), `request.not_acceptable` (406), `request.too_large` (413), `request.unsupported_media_type` (415), `request.rejected` (sonstige 4xx), `server.unavailable` (503), `server.internal_error` (sonstige 5xx). Fachcodes, feldbezogene Fehler und `type`-URIs ergänzen die Fachaufgaben über eine abgestimmte Vertragsänderung. Fehler außerhalb der Controller, etwa bei Actuator-Endpunkten, verwenden die Standardantwort von Spring Boot ohne Stacktrace und Meldung.
