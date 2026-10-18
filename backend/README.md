# Backend (Grundlage TASK-022, Identität TASK-024)

Spring-Boot-Anwendung des modularen Monolithen mit PostgreSQL. Dieser Stand enthält Build, leere Modulgrenzen, Konfiguration, gemeinsame Fehlerbehandlung, Datenbankanbindung und den Migrationsweg. Dazu kommt das Identitätsmodul aus TASK-024 (lokale Konten, Anmeldung, feste Sitzungsdauer, siehe [Identität](#identität-task-024)). Er enthält **keine weitere Fachfunktion**: keine Projekte, Tickets, Tags oder Suche. Diese folgen in TASK-026 bis TASK-029; die Rechte kommen mit TASK-025. Versionen stammen aus [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md).

## Voraussetzungen und Befehle

Temurin 25.0.4.1+1 und ein erreichbarer Docker-Daemon für die Integrationstests. Maven 3.9.16 lädt der Apache-Wrapper mit SHA-256-Prüfung. Vom Repository-Stamm:

```sh
cd backend
./mvnw --batch-mode --no-transfer-progress verify
```

Die Integrationstests starten ein flüchtiges PostgreSQL 18.6 über Testcontainers mit festem Digest. Ohne Docker scheitern sie; sie werden nicht übersprungen. Wer lokal ohne Docker arbeitet, kann die Tests mit `-Dtaskmanager.test.jdbc-url=…`, `-Dtaskmanager.test.username=…`, `-Dtaskmanager.test.password=…` und gegebenenfalls `-Dtaskmanager.test.server-version=…` auf einen eigenen leeren Testserver richten. Ein solcher Lauf ist eine Entwicklerhilfe und kein Nachweis für die festgelegte Version; maßgeblich ist der CI-Lauf.

Der Workflow [backend.yml](../.github/workflows/backend.yml) baut, prüft Modulgrenzen und Migrationskonvention, führt die Integrationstests aus, lehnt übersprungene Tests ab und startet danach das gepackte JAR mit [lifecycle-check.sh](scripts/lifecycle-check.sh) gegen eine leere Datenbank: Abweisung ohne Einstellungen, erster Start mit Migration, Stopp per SIGTERM mit geordnetem Herunterfahren, Neustart ohne Schemaänderung. Alle Warte und Stoppschritte sind zeitlich begrenzt; bricht das Skript ab, nennt es die Phase und beendet den gestarteten Prozess.

## Konfiguration

| Umgebungsvariable | Pflicht | Bedeutung |
|---|---|---|
| `TASK_MANAGER_DB_URL` | ja | JDBC-URL der PostgreSQL-Datenbank, zum Beispiel `jdbc:postgresql://db:5432/taskmanager` |
| `TASK_MANAGER_DB_USER` | ja | Datenbankbenutzer |
| `TASK_MANAGER_DB_PASSWORD` | ja | Passwort des Datenbankbenutzers |
| `TASK_MANAGER_HTTP_PORT` | nein | HTTP-Port, Standard 8080 |

Fehlt eine Pflichtangabe, bricht der Start ab, bevor Webserver oder Datenbankverbindung entstehen. Die Meldung nennt alle fehlenden Variablen und nie einen gesetzten Wert. Werte gehören in die Umgebung des Betriebs, nicht ins Repository.

`/actuator/health`, `/actuator/health/liveness` und `/actuator/health/readiness` melden nur den Zustand ohne Details. Weitere Actuator-Endpunkte sind nicht freigegeben. Alle übrigen Pfade unter `/api` verlangen eine Anmeldung (siehe [Identität](#identität-task-024)).

## Module

Jedes direkte Unterpaket von `taskmanager` ist ein Modul. Spring Modulith prüft im Test Zyklen, Zugriffe auf interne Pakete und die in `package-info.java` erlaubten Abhängigkeiten.

| Modul | Aufgabe | Darf verwenden |
|---|---|---|
| `platform` | Kleine technische Verträge; derzeit Korrelationskennung, Problemantworten, Pflichtkonfiguration | nichts |
| `identity` | Lokale Konten, Anmeldung, Sitzungen (TASK-024) | platform |
| `permissions` | Gruppen und Rechteprüfung (TASK-025) | platform, identity |
| `projects` | Projekte und Konfigurationsversionen (TASK-026) | platform, identity, permissions |
| `tickets` | Ticketkern (TASK-027) | platform, identity, permissions, projects |
| `tags` | Tag-Katalog und Zuordnungen (TASK-028) | platform, identity, permissions, projects |
| `search` | Berechtigte Suchprojektion (TASK-029) | platform, identity, permissions, projects |

Die Tabelle folgt der Ordnung aus [TECHNICAL_CONTRACTS.md](../docs/TECHNICAL_CONTRACTS.md). Wie Tickets, Tags und Suche in einem gemeinsamen Ablauf zusammengesetzt werden, entscheiden TASK-027 bis TASK-029; eine neue erlaubte Abhängigkeit wird dort begründet und im selben PR eingetragen. Öffentlich ist jeweils nur das Modulpaket selbst; Unterpakete sind intern.

## Datenbankmigration

Flyway ist der einzige Weg für Schemaänderungen. Hibernate prüft nur (`ddl-auto: validate`), `clean` ist abgeschaltet, eine Baseline auf eine vorhandene Datenbank erfolgt nicht automatisch.

- Ort: `src/main/resources/db/migration`.
- Name: `V<vierstellige Nummer>__<Modul>_<beschreibung>.sql`, Kleinbuchstaben, Ziffern und Unterstriche, zum Beispiel `V0003__identity_accounts.sql`. Das Modul ist Besitzer der Migration und ihrer Objekte.
- Die nächste freie Nummer wird im Claim-PR der Aufgabe genannt. Zwei gleiche Nummern lassen den Build scheitern; der spätere PR nimmt dann die nächste freie Nummer.
- Eine nach main übernommene Migration wird nie geändert. Korrekturen sind neue Migrationen. Flyway weist geänderte Prüfsummen beim Start ab.
- Ein Modul legt nur eigene Objekte an und liest keine fremden Tabellen.

`MigrationConventionTest` prüft Name, Modul und Eindeutigkeit ohne Datenbank. `V0001__platform_baseline.sql` ist bewusst leer und beginnt die Schemahistorie. `V0003__identity_accounts.sql` gehört dem Identitätsmodul; die Nummer V0002 ist für die Berechtigungen (TASK-025) reserviert, Flyway verträgt die Lücke.

## Fehlerantworten

Alle Fehlerantworten, aus Controllern ebenso wie Fehler außerhalb davon (etwa `sendError` oder Fehler in Filtern), werden `application/problem+json` nach RFC 9457 mit `type`, `title`, `status`, `instance`, stabilem `code` und `correlationId`. `instance` ist die Korrelationskennung als `urn:uuid:…`; Anfragepfad und Query werden nicht zurückgegeben. Framework-Texte im Feld `detail`, Stacktraces und Ausnahmemeldungen werden nicht ausgegeben. Jede Anfrage erhält eine vom Server erzeugte Kennung im Kopf `X-Correlation-Id`; ein mitgesendeter Wert wird ignoriert. Die Kennung steht auch in jeder Logzeile der Anfrage.

Erste technische Codes: `request.invalid` (400), `resource.not_found` (404), `request.method_not_allowed` (405), `request.not_acceptable` (406), `request.too_large` (413), `request.unsupported_media_type` (415), `request.rejected` (sonstige 4xx), `server.unavailable` (503), `server.internal_error` (sonstige 5xx). Fachcodes, feldbezogene Fehler und `type`-URIs ergänzen die Fachaufgaben über eine abgestimmte Vertragsänderung.

## Identität (TASK-024)

Umgesetzt sind lokale Konten, Anmeldung und Abmeldung, Deaktivierung, der Pflichtwechsel des Einmalpassworts und die feste Sitzungsdauer. Der Vertrag steht in [identity.openapi.yaml](../contracts/identity.openapi.yaml); ein Test weist Abweichungen zwischen Vertrag und tatsächlichen Routen ab. Die Entscheidungen stehen in DEC-058. Eine bedienbare Anmeldeseite folgt in TASK-030, LDAP und Portalkonten sind nicht Teil dieser Aufgabe; das Modell ist kontoart neutral vorbereitet.

**Erstadministrator und Notweg.** Es gibt kein Standardkonto. Im Container legt `java -jar app.jar bootstrap-admin NAME` das erste Konto an, und zwar nur, solange noch kein Konto existiert. Das Einmalpasswort erscheint einmal auf der Standardausgabe und nirgends sonst. `java -jar app.jar reset-admin-password NAME` stellt für einen vorhandenen Administrator ein neues Einmalpasswort aus, beendet seine Sitzungen und aktiviert ihn bei Bedarf wieder; das setzt Serverzugriff voraus und steht im Prüfprotokoll. Exitcodes: 0 Erfolg, 2 abgelehnt (zum Beispiel `bootstrap.accounts_exist`, `account.not_found`), 1 unerwarteter Fehler. `lifecycle-check.sh` führt den Befehl im gepackten JAR aus.

**Einmalpasswort.** 20 zufällige Zeichen ohne Verwechslungszeichen, gültig 72 Stunden und für genau eine erfolgreiche Anmeldung; eine neue Ausgabe macht das alte ungültig. Die Oberfläche beziehungsweise die Antwort der Kontoanlage zeigt es einmal. Es ist nicht erneut abrufbar, wird nur als Argon2id Hash gespeichert und steht in keinem Protokoll. Ein Versand per Mail gibt es nicht.

**Passwortregeln.** 12 bis 128 Zeichen, keine Zeichenklassenpflicht, keine Steuerzeichen, nicht gleich dem Benutzernamen, nicht identisch mit dem alten Passwort, Abgleich mit einer kleinen mitgelieferten Liste häufiger Passwörter (`identity/common-passwords.txt`; ein Grundschutz, kein Abgleich mit bekannten Datenlecks). Hash: Argon2id (Bouncy Castle über `spring-security-crypto`, Speicher 19 MiB, 2 Durchgänge).

**Sitzungen.** Eigene serverseitige Sitzungen, in der Datenbank steht nur der SHA 256 Hash des Tokens. Cookie `__Host-tm-session` (HttpOnly, Secure, SameSite=Lax, Pfad `/`, ohne Domain). Die Dauer ist fest: 24 Stunden in neuen Installationen, einstellbar von 1 bis 720 Stunden (`PUT /api/v1/settings/session-duration`), nicht abschaltbar. Weder Aktivität noch Neustart noch der Passwortwechsel verlängern sie. Eine Verkürzung gilt für bestehende Sitzungen bei der nächsten Anfrage, eine Verlängerung nur für neue Anmeldungen. Die Anmeldung mit einem Einmalpasswort startet die feste Frist und erzeugt eine eingeschränkte Sitzung (nur Passwortwechsel und Abmeldung, höchstens 30 Minuten). Nach dem Wechsel gilt eine neue Sitzungskennung mit demselben Ablauf; alle anderen Sitzungen des Kontos enden. Deaktivierung und Zurücksetzen durch einen Administrator beenden alle Sitzungen des Kontos sofort.

**CSRF.** Jede Anfrage außer GET, HEAD und OPTIONS braucht den Wert des lesbaren Cookies `__Host-tm-csrf` im Kopf `X-CSRF-Token`; mit einer Sitzung ist das Token zusätzlich an diese Sitzung gebunden. Ein vorhandener `Origin` Kopf muss zum Host passen. Fehler: `auth.csrf_invalid` (403). `GET /api/v1/session` gibt dem ersten Besuch das Cookie. Die Cookies tragen immer `Secure` und das Präfix `__Host-`; sie funktionieren daher nur über HTTPS (Browser behandeln `http://localhost` als sicher). Ein vorgeschalteter Proxy beendet TLS und reicht den `Host` Kopf unverändert weiter, weil der Herkunftsvergleich ihn nutzt.

**Zugriffsschutz.** Ein Filter verweigert jeden Pfad unter `/api` ohne gültige Sitzung (`401`, `WWW-Authenticate: Session realm="task-manager"`), auch unbekannte Pfade. Ausgenommen ist nur `/api/v1/session`. Antworten unter `/api` sind `Cache-Control: no-store`. Fachmodule erhalten den Zugriffskontext über `taskmanager.identity.AccessContexts` (Konto, Sitzung, Stufe, Ablauf); sie lesen keine Identitätstabellen. Das Feld `systemAdministrator` ist eine vorläufige technische Kennzeichnung für Konten-, Dauer- und Notwegfunktionen, kein Rechtemodell. Der Anschluss an das Berechtigungsmodul (`IdentityAccess` aus TASK-025) ist noch zu koordinieren, siehe Grenzen.

**Fehlversuche.** Nach fünf Fehlversuchen je Anmeldename wächst die Wartezeit (5 Sekunden, verdoppelt bis höchstens 15 Minuten), ebenso je Quelladresse nach 20. Es gibt keine Kontosperre. Die Antwort `429` nennt `Retry-After`. Bekannte, unbekannte und deaktivierte Namen werden gleich beantwortet (`auth.invalid_credentials`). Auch ein falsches aktuelles Passwort beim Wechsel zählt.

| Einstellung | Standard |
|---|---|
| `taskmanager.identity.one-time-password-validity` | 72h |
| `taskmanager.identity.restricted-session-validity` | 30m |
| `taskmanager.identity.user-failure-threshold` | 5 |
| `taskmanager.identity.source-failure-threshold` | 20 |
| `taskmanager.identity.base-delay` / `max-delay` | 5s / 15m |

**Fehlercodes dieses Moduls:** `auth.required`, `auth.session_expired`, `auth.invalid_credentials`, `auth.one_time_password_expired`, `auth.password_change_required`, `auth.csrf_invalid`, `auth.forbidden`, `auth.too_many_attempts`, `password.current_incorrect`, `password.policy_violation`, `account.not_found`, `account.username_taken`, `account.last_administrator`, `account.not_administrator`, `bootstrap.accounts_exist` und `request.invalid` mit stabilen Gründen unter `violations`.

**Grenzen und offene Punkte.** (1) Die Quelladresse ist `request.getRemoteAddr()`; hinter einem Proxy gilt sie erst, wenn dessen Weiterleitungskopfzeilen konfiguriert sind, andernfalls teilen sich alle Nutzer eine Adresse. (2) Die Passwortliste ist klein. (3) Kein zweiter Faktor, kein LDAP, keine Portalkonten, keine Selbstbedienung per Mail. (4) Der Administrator Marker ist vorläufig bis zur Integration mit TASK-025. Dessen `IdentityAccess` Port erwartet Mandant, Organisation und Rechteversion, die das Identitätsmodul noch nicht führt; Adapter und Datenmodell sind mit der Koordination abzustimmen. (5) Die Migration trägt die Nummer V0003, weil V0002 für TASK-025 reserviert ist. (6) Java 25 und PostgreSQL 18.6 sind nur durch CI belegt; lokale Läufe nutzten Java 21 und PostgreSQL 16. (7) Eine getrennte KI Prüfung der Sicherheitsgrenzen nach DEC-040 steht aus.
