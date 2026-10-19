# Backend (Grundlage TASK-022, Identität TASK-024, Berechtigungen TASK-025)

Spring-Boot-Anwendung des modularen Monolithen mit PostgreSQL. Dieser Stand enthält Build, Modulgrenzen, Konfiguration, gemeinsame Fehlerbehandlung, Datenbankanbindung und den Migrationsweg. TASK-025 ergänzt Gruppen-, Aktions-/Projekt-/Feldrechte und Audit; der [Berechtigungsvertrag](PERMISSIONS.md) beschreibt Nutzung und Grenzen, und ohne echten Identitätsadapter verweigert das Modul Zugriff. TASK-024 ergänzt das Identitätsmodul (lokale Konten, Anmeldung, feste Sitzungsdauer, siehe [Identität](#identität-task-024)). TASK-036 ergänzt das Erscheinungsbild (Akzentfarbe und Logo, siehe [Erscheinungsbild](#erscheinungsbild-task-036)). Projekte, Tickets, Tags und Suche sind noch nicht implementiert. Versionen stammen aus [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md).

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
| `appearance` | Akzentfarbe und Logo des Unternehmens (TASK-036) | platform, identity |
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

`MigrationConventionTest` prüft Name, Modul und Eindeutigkeit ohne Datenbank. `V0001__platform_baseline.sql` ist bewusst leer und beginnt die Schemahistorie. `V0002__permissions_baseline.sql` legt sechs ausschließlich vom Berechtigungsmodul besessene Tabellen ohne Startdaten an, `V0003__identity_accounts.sql` fünf Tabellen des Identitätsmoduls (ebenfalls ohne Konto), `V0004__identity_one_time_consumption.sql` ergänzt das Verbrauchsdatum des Einmalpassworts, `V0005__permissions_rule_origin.sql` die Herkunft einer Feldregel (`set_by`), `V0007__permissions_rule_origin_role.sql` ob sie mit Unternehmensverwaltung gesetzt wurde (`set_by_company`). `V0008__appearance_settings.sql` legt die Einstellungszeile mit der Standardfarbe `#f97316` und die Audittabelle des Erscheinungsbilds an (TASK-036); die ursprünglich dafür reservierte Nummer V0006 bleibt frei, weil Flyway eine Migration unter einer bereits angewandten V0007 nicht nachträglich anwendet. Lifecycle- und Foundation-Prüfungen erwarten genau die angewandten Migrationen, keine Defaultrechte und kein Konto.

## Fehlerantworten

Alle Fehlerantworten, aus Controllern ebenso wie Fehler außerhalb davon (etwa `sendError` oder Fehler in Filtern), werden `application/problem+json` nach RFC 9457 mit `type`, `title`, `status`, `instance`, stabilem `code` und `correlationId`. `instance` ist die Korrelationskennung als `urn:uuid:…`; Anfragepfad und Query werden nicht zurückgegeben. Framework-Texte im Feld `detail`, Stacktraces und Ausnahmemeldungen werden nicht ausgegeben. Jede Anfrage erhält eine vom Server erzeugte Kennung im Kopf `X-Correlation-Id`; ein mitgesendeter Wert wird ignoriert. Die Kennung steht auch in jeder Logzeile der Anfrage.

Erste technische Codes: `request.invalid` (400), `resource.not_found` (404), `request.method_not_allowed` (405), `request.not_acceptable` (406), `request.too_large` (413), `request.unsupported_media_type` (415), `request.rejected` (sonstige 4xx), `server.unavailable` (503), `server.internal_error` (sonstige 5xx). Fachcodes, feldbezogene Fehler und `type`-URIs ergänzen die Fachaufgaben über eine abgestimmte Vertragsänderung.

## Identität (TASK-024)

Umgesetzt sind lokale Konten, Anmeldung und Abmeldung, Deaktivierung, der Pflichtwechsel des Einmalpassworts und die feste Sitzungsdauer. Der Vertrag steht in [identity.openapi.yaml](../contracts/identity.openapi.yaml); ein Test weist Abweichungen zwischen Vertrag und tatsächlichen Routen ab. Die Entscheidungen stehen in DEC-058. Eine bedienbare Anmeldeseite folgt in TASK-030, LDAP und Portalkonten sind nicht Teil dieser Aufgabe; das Modell ist kontoart neutral vorbereitet.

**Erstadministrator und Notweg.** Es gibt kein Standardkonto. Im Container legt `java -jar app.jar bootstrap-admin NAME` das erste Konto an, und zwar nur, solange noch kein Konto existiert. Das Einmalpasswort erscheint einmal auf der Standardausgabe und nirgends sonst. `java -jar app.jar reset-admin-password NAME` stellt für einen vorhandenen Administrator ein neues Einmalpasswort aus, beendet seine Sitzungen und aktiviert ihn bei Bedarf wieder; das setzt Serverzugriff voraus und steht im Prüfprotokoll. Exitcodes: 0 Erfolg, 2 abgelehnt (zum Beispiel `bootstrap.accounts_exist`, `account.not_found`), 1 unerwarteter Fehler. `lifecycle-check.sh` führt den Befehl im gepackten JAR aus.

**Einmalpasswort.** 20 zufällige Zeichen ohne Verwechslungszeichen, gültig 72 Stunden und für genau eine erfolgreiche Anmeldung; eine neue Ausgabe macht das alte ungültig. Die erste erfolgreiche Anmeldung verbraucht es in derselben Transaktion, die die eingeschränkte Sitzung anlegt. Der Pflichtwechsel geschieht in dieser Sitzung; läuft sie vorher ab, lehnt jede weitere Anmeldung mit dem Einmalpasswort `auth.one_time_password_used` ab, und ein Administrator muss ein neues ausgeben. Die Oberfläche beziehungsweise die Antwort der Kontoanlage zeigt es einmal. Es ist nicht erneut abrufbar, wird nur als Argon2id Hash gespeichert und steht in keinem Protokoll. Ein Versand per Mail gibt es nicht.

**Passwortregeln.** 12 bis 128 Zeichen, keine Zeichenklassenpflicht, keine Steuerzeichen, nicht gleich dem Benutzernamen, nicht identisch mit dem alten Passwort, Abgleich mit einer kleinen mitgelieferten Liste häufiger Passwörter (`identity/common-passwords.txt`; ein Grundschutz, kein Abgleich mit bekannten Datenlecks). Hash: Argon2id (Bouncy Castle über `spring-security-crypto`, Speicher 19 MiB, 2 Durchgänge).

**Sitzungen.** Eigene serverseitige Sitzungen, in der Datenbank steht nur der SHA 256 Hash des Tokens. Cookie `__Host-tm-session` (HttpOnly, Secure, SameSite=Lax, Pfad `/`, ohne Domain). Die Dauer ist fest: 24 Stunden in neuen Installationen, einstellbar von 1 bis 720 Stunden (`PUT /api/v1/settings/session-duration`), nicht abschaltbar. Weder Aktivität noch Neustart noch der Passwortwechsel verlängern sie. Eine Verkürzung wird beim Ändern in alle offenen Sitzungen geschrieben und gilt bei der nächsten Anfrage; sie wird nie zurückgenommen, auch nicht durch eine spätere Verlängerung. Eine Verlängerung gilt nur für neue Anmeldungen. Die Anmeldung mit einem Einmalpasswort startet die feste Frist und erzeugt eine eingeschränkte Sitzung (nur Passwortwechsel und Abmeldung, höchstens 30 Minuten). Nach dem Wechsel gilt eine neue Sitzungskennung mit demselben Ablauf; alle anderen Sitzungen des Kontos enden. Deaktivierung und Zurücksetzen durch einen Administrator beenden alle Sitzungen des Kontos sofort. Anmeldung und Passwortwechsel nehmen die Einstellung der Dauer als erstes unter einer gemeinsamen Sperre (`FOR SHARE`) und begrenzen das Ablaufdatum der neuen Sitzung darauf; eine gleichzeitige Verkürzung wartet dadurch auf den laufenden Wechsel und kürzt auch die Ersatzsitzung (DEC-061).

**Bekannte Grenze bei Altdatenbanken (N2, DEC-061).** Eine Datenbank, die zwischen den Migrationen V0003 und V0004 bestand und bereits eine eingeschränkte Sitzung für ein Einmalpasswort enthielt, hat kein Verbrauchsdatum für dieses Passwort; nach V0004 ist es für eine weitere Anmeldung noch gültig, bis es abläuft oder ein Administrator ein neues ausgibt. Das wird nicht korrigiert, weil es weder eine Installation noch echte Konten gibt; vor einem Update einer solchen Datenbank sind offene Einmalpasswörter durch das Zurücksetzen neu auszugeben.

**CSRF.** Jede Anfrage außer GET, HEAD und OPTIONS braucht den Wert des lesbaren Cookies `__Host-tm-csrf` im Kopf `X-CSRF-Token`; mit einer Sitzung ist das Token zusätzlich an diese Sitzung gebunden. Ein vorhandener `Origin` Kopf muss zum Host passen. Fehler: `auth.csrf_invalid` (403). `GET /api/v1/session` gibt dem ersten Besuch das Cookie. Die Cookies tragen immer `Secure` und das Präfix `__Host-`; sie funktionieren daher nur über HTTPS (Browser behandeln `http://localhost` als sicher). Ein vorgeschalteter Proxy beendet TLS und reicht den `Host` Kopf unverändert weiter, weil der Herkunftsvergleich ihn nutzt.

**Zugriffsschutz.** Ein Filter verweigert jeden Pfad unter `/api` ohne gültige Sitzung (`401`, `WWW-Authenticate: Session realm="task-manager"`), auch unbekannte Pfade. Er entscheidet auf dem Pfad, wie Spring MVC ihn zuordnet (Pfadparameter wie `/api;x/` entfernt, Prozentkodierung wie `/%61pi/` aufgelöst), nicht auf der rohen Anfragekennung. Ausgenommen ist nur `/api/v1/session`. Antworten unter `/api` sind `Cache-Control: no-store`. Fachmodule erhalten den Zugriffskontext über `taskmanager.identity.AccessContexts` (Konto, Sitzung, Stufe, Ablauf); sie lesen keine Identitätstabellen. Das Feld `systemAdministrator` ist eine vorläufige technische Kennzeichnung für Konten-, Dauer- und Notwegfunktionen, kein Rechtemodell. Der Anschluss an das Berechtigungsmodul (`IdentityAccess` aus TASK-025, Sperrvertrag in [PERMISSIONS.md](PERMISSIONS.md)) ist noch nicht gebaut, siehe Grenzen.

**Fehlversuche.** Nach fünf Fehlversuchen je Anmeldename wächst die Wartezeit (5 Sekunden, verdoppelt bis höchstens 15 Minuten), ebenso je Quelladresse nach fünf (die Schwelle ist einstellbar; hinter einem gemeinsamen Proxy oder NAT teilen sich Nutzer eine Adresse, siehe Grenzen). Es gibt keine Kontosperre. Die Antwort `429` nennt `Retry-After`. Bekannte, unbekannte und deaktivierte Namen werden gleich beantwortet (`auth.invalid_credentials`). Auch ein falsches aktuelles Passwort beim Wechsel zählt.

| Einstellung | Standard |
|---|---|
| `taskmanager.identity.one-time-password-validity` | 72h |
| `taskmanager.identity.restricted-session-validity` | 30m |
| `taskmanager.identity.user-failure-threshold` | 5 |
| `taskmanager.identity.source-failure-threshold` | 5 |
| `taskmanager.identity.base-delay` / `max-delay` | 5s / 15m |

**Fehlercodes dieses Moduls:** `auth.required`, `auth.session_expired`, `auth.invalid_credentials`, `auth.one_time_password_expired`, `auth.one_time_password_used`, `auth.password_change_required`, `auth.csrf_invalid`, `auth.forbidden`, `auth.too_many_attempts`, `password.current_incorrect`, `password.policy_violation`, `account.not_found`, `account.username_taken`, `account.last_administrator`, `account.not_administrator`, `bootstrap.accounts_exist` und `request.invalid` mit stabilen Gründen unter `violations`.

**Grenzen und offene Punkte.** (1) Die Quelladresse ist `request.getRemoteAddr()`; hinter einem Proxy gilt sie erst, wenn dessen Weiterleitungskopfzeilen konfiguriert sind, andernfalls teilen sich alle Nutzer eine Adresse. (2) Die Passwortliste ist klein. (3) Kein zweiter Faktor, kein LDAP, keine Portalkonten, keine Selbstbedienung per Mail. (4) Der Administrator Marker ist vorläufig bis zur Integration mit TASK-025. Dessen `IdentityAccess` Port erwartet Mandant, Organisation und Rechteversion sowie einen Adapter außerhalb des Identitätsmoduls, der Konto und Sitzung unter einer bis zum Commit gehaltenen Sperre prüft und jede Entzugs- und Änderungsstelle (Deaktivierung, Zurücksetzen, Abmeldung, Passwortwechsel) mit unverträglichen Sperren versieht. Das Identitätsmodul führt weder diese Daten noch diese Sperren; Adapter und Datenmodell sind mit der Koordination abzustimmen. (5) Die Migration trägt die Nummer V0003, weil V0002 der Berechtigung aus TASK-025 gehört. (6) Java 25 und PostgreSQL 18.6 sind nur durch CI belegt; lokale Läufe nutzten Java 21 und PostgreSQL 16. (7) Eine getrennte KI Prüfung der Sicherheitsgrenzen nach DEC-040 steht aus.

## Erscheinungsbild (TASK-036)

Eine Zeile mit Akzentfarbe (`#rrggbb`, kleingeschrieben gespeichert) und optionalem Logo. Vertrag: [appearance.openapi.yaml](../contracts/appearance.openapi.yaml), ein Test vergleicht ihn mit den Routen. Lesen darf jede vollständige Anmeldung, ändern nur ein Konto mit dem vorläufigen Administrator Marker aus dem Identitätsmodul; die Prüfung liegt im Server, die Oberfläche blendet nur aus. Ein eigenes Recht „Erscheinungsbild verwalten“ im Berechtigungsmodul folgt mit dem Identitätsadapter.

**Version.** Der `ETag` ist die Version der Zeile. Jede Änderung braucht ihn als `If-Match` (`428 appearance.precondition_required` ohne, `412 appearance.version_conflict` bei veraltetem Wert). Die Zeile wird für die Prüfung gesperrt, zwei Administratoren überschreiben sich also nicht unbemerkt.

**Logo.** PNG, JPEG oder WebP bis 512 KiB als Rohdaten mit passendem `Content-Type`. Der Typ wird an den ersten Bytes erkannt; weicht er ab oder ist es ein anderes Format, antwortet der Server `415 appearance.logo_type_invalid`. SVG wird abgelehnt, weil es Skript tragen kann. Ausgeliefert wird das Logo mit `X-Content-Type-Options: nosniff` und `Content-Security-Policy: default-src 'none'; sandbox`.

**Audit.** Jede Änderung schreibt in `appearance_audit_event` Zeitpunkt, Konto, Benutzername, Ereignis (`appearance.accent_changed`, `appearance.logo_replaced`, `appearance.logo_removed`) und eine kurze Angabe wie alte und neue Farbe. Eine Leseschnittstelle für das Audit gehört zur Administrationsoberfläche (TASK-030).

**Fehlercodes:** `appearance.color_invalid`, `appearance.logo_empty`, `appearance.logo_type_invalid`, `appearance.logo_not_found`, `appearance.version_conflict`, `appearance.precondition_required`, `request.too_large`, dazu `auth.required`, `auth.forbidden`, `auth.csrf_invalid` und `auth.password_change_required` aus der Identität.
