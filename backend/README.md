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

`MigrationConventionTest` prüft Name, Modul und Eindeutigkeit ohne Datenbank. `V0001__platform_baseline.sql` ist bewusst leer und beginnt die Schemahistorie. `V0002__permissions_baseline.sql` legt sechs ausschließlich vom Berechtigungsmodul besessene Tabellen ohne Startdaten an, `V0003__identity_accounts.sql` fünf Tabellen des Identitätsmoduls (ebenfalls ohne Konto), `V0004__identity_one_time_consumption.sql` ergänzt das Verbrauchsdatum des Einmalpassworts, `V0005__permissions_rule_origin.sql` die Herkunft einer Feldregel (`set_by`), `V0007__permissions_rule_origin_role.sql` ob sie mit Unternehmensverwaltung gesetzt wurde (`set_by_company`). `V0008__appearance_settings.sql` legt die Einstellungszeile mit der Standardfarbe `#f97316` und die Audittabelle des Erscheinungsbilds an (TASK-036); die ursprünglich dafür reservierte Nummer V0006 bleibt frei, weil Flyway eine Migration unter einer bereits angewandten V0007 nicht nachträglich anwendet. `V0009__platform_company.sql` legt die Tabelle für die eine Firma der Installation an (TASK-039), ohne Zeile. `V0010__identity_drop_administrator_marker.sql` entfernt die vorläufige Spalte `identity_account.administrator` (TASK-040); danach gibt es keinen Administrator Marker mehr. `V0011__projects_baseline.sql` legt die vier Tabellen des Projektmoduls an (TASK-026): Projekte, unveränderliche Konfigurationsversionen, Mitglieder und Audit, ohne Startdaten. `V0012__tickets_baseline.sql` legt die drei Tabellen des Ticketmoduls an (TASK-027): Tickets, Nummernzähler je Projekt und Feldhistorie. Lifecycle- und Foundation-Prüfungen erwarten genau die angewandten Migrationen, keine Defaultrechte, keine Firma und kein Konto.

## Fehlerantworten

Alle Fehlerantworten, aus Controllern ebenso wie Fehler außerhalb davon (etwa `sendError` oder Fehler in Filtern), werden `application/problem+json` nach RFC 9457 mit `type`, `title`, `status`, `instance`, stabilem `code` und `correlationId`. `instance` ist die Korrelationskennung als `urn:uuid:…`; Anfragepfad und Query werden nicht zurückgegeben. Framework-Texte im Feld `detail`, Stacktraces und Ausnahmemeldungen werden nicht ausgegeben. Jede Anfrage erhält eine vom Server erzeugte Kennung im Kopf `X-Correlation-Id`; ein mitgesendeter Wert wird ignoriert. Die Kennung steht auch in jeder Logzeile der Anfrage.

Erste technische Codes: `request.invalid` (400), `resource.not_found` (404), `request.method_not_allowed` (405), `request.not_acceptable` (406), `request.too_large` (413), `request.unsupported_media_type` (415), `request.rejected` (sonstige 4xx), `server.unavailable` (503), `server.internal_error` (sonstige 5xx). Fachcodes, feldbezogene Fehler und `type`-URIs ergänzen die Fachaufgaben über eine abgestimmte Vertragsänderung.

## Identität (TASK-024)

Umgesetzt sind lokale Konten, Anmeldung und Abmeldung, Deaktivierung, der Pflichtwechsel des Einmalpassworts und die feste Sitzungsdauer. Der Vertrag steht in [identity.openapi.yaml](../contracts/identity.openapi.yaml); ein Test weist Abweichungen zwischen Vertrag und tatsächlichen Routen ab. Die Entscheidungen stehen in DEC-058. Eine bedienbare Anmeldeseite folgt in TASK-030, LDAP und Portalkonten sind nicht Teil dieser Aufgabe; das Modell ist kontoart neutral vorbereitet.

**Erstadministrator und Notweg.** Es gibt kein Standardkonto. Im Container legt `java -jar app.jar bootstrap-admin NAME` das erste Konto an, und zwar nur, solange noch kein Konto existiert. Das Einmalpasswort erscheint einmal auf der Standardausgabe und nirgends sonst. `java -jar app.jar reset-admin-password NAME` stellt für ein vorhandenes Konto der Unternehmensverwaltung (Mitglied einer Gruppe mit `permissions.manage`) ein neues Einmalpasswort aus, beendet seine Sitzungen und aktiviert ihn bei Bedarf wieder; das setzt Serverzugriff voraus und steht im Prüfprotokoll. Exitcodes: 0 Erfolg, 2 abgelehnt (zum Beispiel `bootstrap.accounts_exist`, `account.not_found`), 1 unerwarteter Fehler. `lifecycle-check.sh` führt den Befehl im gepackten JAR aus. Seit TASK-039 legt `bootstrap-admin` in derselben Transaktion auch die Firma und die ersten Rechte an (Abschnitt „Adapter zwischen Identität und Berechtigungen“); scheitert das, entsteht auch kein Konto.

**Einmalpasswort.** 20 zufällige Zeichen ohne Verwechslungszeichen, gültig 72 Stunden und für genau eine erfolgreiche Anmeldung; eine neue Ausgabe macht das alte ungültig. Die erste erfolgreiche Anmeldung verbraucht es in derselben Transaktion, die die eingeschränkte Sitzung anlegt. Der Pflichtwechsel geschieht in dieser Sitzung; läuft sie vorher ab, lehnt jede weitere Anmeldung mit dem Einmalpasswort `auth.one_time_password_used` ab, und ein Konto mit dem Recht `accounts.manage` muss ein neues ausgeben. Die Oberfläche beziehungsweise die Antwort der Kontoanlage zeigt es einmal. Es ist nicht erneut abrufbar, wird nur als Argon2id Hash gespeichert und steht in keinem Protokoll. Ein Versand per Mail gibt es nicht.

**Passwortregeln.** 12 bis 128 Zeichen, keine Zeichenklassenpflicht, keine Steuerzeichen, nicht gleich dem Benutzernamen, nicht identisch mit dem alten Passwort, Abgleich mit einer kleinen mitgelieferten Liste häufiger Passwörter (`identity/common-passwords.txt`; ein Grundschutz, kein Abgleich mit bekannten Datenlecks). Hash: Argon2id (Bouncy Castle über `spring-security-crypto`, Speicher 19 MiB, 2 Durchgänge).

**Sitzungen.** Eigene serverseitige Sitzungen, in der Datenbank steht nur der SHA 256 Hash des Tokens. Cookie `__Host-tm-session` (HttpOnly, Secure, SameSite=Lax, Pfad `/`, ohne Domain). Die Dauer ist fest: 24 Stunden in neuen Installationen, einstellbar von 1 bis 720 Stunden (`PUT /api/v1/settings/session-duration`), nicht abschaltbar. Weder Aktivität noch Neustart noch der Passwortwechsel verlängern sie. Eine Verkürzung wird beim Ändern in alle offenen Sitzungen geschrieben und gilt bei der nächsten Anfrage; sie wird nie zurückgenommen, auch nicht durch eine spätere Verlängerung. Eine Verlängerung gilt nur für neue Anmeldungen. Die Anmeldung mit einem Einmalpasswort startet die feste Frist und erzeugt eine eingeschränkte Sitzung (nur Passwortwechsel und Abmeldung, höchstens 30 Minuten). Nach dem Wechsel gilt eine neue Sitzungskennung mit demselben Ablauf; alle anderen Sitzungen des Kontos enden. Deaktivierung und Zurücksetzen durch die Kontenverwaltung beenden alle Sitzungen des Kontos sofort. Anmeldung und Passwortwechsel nehmen die Einstellung der Dauer als erstes unter einer gemeinsamen Sperre (`FOR SHARE`) und begrenzen das Ablaufdatum der neuen Sitzung darauf; eine gleichzeitige Verkürzung wartet dadurch auf den laufenden Wechsel und kürzt auch die Ersatzsitzung (DEC-061).

**Bekannte Grenze bei Altdatenbanken (N2, DEC-061).** Eine Datenbank, die zwischen den Migrationen V0003 und V0004 bestand und bereits eine eingeschränkte Sitzung für ein Einmalpasswort enthielt, hat kein Verbrauchsdatum für dieses Passwort; nach V0004 ist es für eine weitere Anmeldung noch gültig, bis es abläuft oder ein Administrator ein neues ausgibt. Das wird nicht korrigiert, weil es weder eine Installation noch echte Konten gibt; vor einem Update einer solchen Datenbank sind offene Einmalpasswörter durch das Zurücksetzen neu auszugeben.

**CSRF.** Jede Anfrage außer GET, HEAD und OPTIONS braucht den Wert des lesbaren Cookies `__Host-tm-csrf` im Kopf `X-CSRF-Token`; mit einer Sitzung ist das Token zusätzlich an diese Sitzung gebunden. Ein vorhandener `Origin` Kopf muss zum Host passen. Fehler: `auth.csrf_invalid` (403). `GET /api/v1/session` gibt dem ersten Besuch das Cookie. Die Cookies tragen immer `Secure` und das Präfix `__Host-`; sie funktionieren daher nur über HTTPS (Browser behandeln `http://localhost` als sicher). Ein vorgeschalteter Proxy beendet TLS und reicht den `Host` Kopf unverändert weiter, weil der Herkunftsvergleich ihn nutzt.

**Zugriffsschutz.** Ein Filter verweigert jeden Pfad unter `/api` ohne gültige Sitzung (`401`, `WWW-Authenticate: Session realm="task-manager"`), auch unbekannte Pfade. Er entscheidet auf dem Pfad, wie Spring MVC ihn zuordnet (Pfadparameter wie `/api;x/` entfernt, Prozentkodierung wie `/%61pi/` aufgelöst), nicht auf der rohen Anfragekennung. Ausgenommen ist nur `/api/v1/session`. Antworten unter `/api` sind `Cache-Control: no-store`. Fachmodule erhalten den Zugriffskontext über `taskmanager.identity.AccessContexts` (Konto, Sitzung, Stufe, Ablauf); sie lesen keine Identitätstabellen. Konten-, Dauer- und Erscheinungsbildfunktionen prüfen echte Unternehmensrechte über den Port `AdministrationRights` (TASK-040, siehe Abschnitt „Verwaltungsrechte“); das Modul Identität kennt weder einen Administrator Marker noch das Berechtigungsmodul.

**Fehlversuche.** Nach fünf Fehlversuchen je Anmeldename wächst die Wartezeit (5 Sekunden, verdoppelt bis höchstens 15 Minuten), ebenso je Quelladresse nach fünf (die Schwelle ist einstellbar; hinter einem gemeinsamen Proxy oder NAT teilen sich Nutzer eine Adresse, siehe Grenzen). Es gibt keine Kontosperre. Die Antwort `429` nennt `Retry-After`. Bekannte, unbekannte und deaktivierte Namen werden gleich beantwortet (`auth.invalid_credentials`). Auch ein falsches aktuelles Passwort beim Wechsel zählt.

| Einstellung | Standard |
|---|---|
| `taskmanager.identity.one-time-password-validity` | 72h |
| `taskmanager.identity.restricted-session-validity` | 30m |
| `taskmanager.identity.user-failure-threshold` | 5 |
| `taskmanager.identity.source-failure-threshold` | 5 |
| `taskmanager.identity.base-delay` / `max-delay` | 5s / 15m |

**Fehlercodes dieses Moduls:** `auth.required`, `auth.session_expired`, `auth.invalid_credentials`, `auth.one_time_password_expired`, `auth.one_time_password_used`, `auth.password_change_required`, `auth.csrf_invalid`, `auth.forbidden`, `auth.too_many_attempts`, `password.current_incorrect`, `password.policy_violation`, `account.not_found`, `account.username_taken`, `account.last_administrator`, `account.not_administrator`, `bootstrap.accounts_exist` und `request.invalid` mit stabilen Gründen unter `violations`.

**Grenzen und offene Punkte.** (1) Die Quelladresse ist `request.getRemoteAddr()`; hinter einem Proxy gilt sie erst, wenn dessen Weiterleitungskopfzeilen konfiguriert sind, andernfalls teilen sich alle Nutzer eine Adresse. (2) Die Passwortliste ist klein. (3) Kein zweiter Faktor, kein LDAP, keine Portalkonten, keine Selbstbedienung per Mail. (4) Der frühere Administrator Marker ist mit TASK-040 durch die Unternehmensrechte `accounts.manage`, `session_duration.manage` und `appearance.manage` ersetzt (siehe Abschnitt „Verwaltungsrechte“). (5) Die Migration trägt die Nummer V0003, weil V0002 der Berechtigung aus TASK-025 gehört. (6) Java 25 und PostgreSQL 18.6 sind nur durch CI belegt; lokale Läufe nutzten Java 21 und PostgreSQL 16. (7) Eine getrennte KI Prüfung der Sicherheitsgrenzen nach DEC-040 steht aus.

## Adapter zwischen Identität und Berechtigungen (TASK-039)

Das Berechtigungsmodul (TASK-025) fragt über seinen Port `IdentityAccess` nach Konto und Sitzung; bis TASK-039 gab es dafür nur Testdoubles. Der Adapter liegt im Paket `taskmanager` (Anwendungseinstieg), weil keines der beiden Module das andere in dieser Richtung kennen darf. Entscheidung: DEC-063.

**Firma.** Die eine Firma der Installation (DEC-006) steht in `platform_company` (V0009, höchstens eine Zeile). Das Plattformmodul liest sie über `CompanyContext`. Sie kommt nie aus einer Anfrage und wird nur von `bootstrap-admin` angelegt. Ohne Firma verweigert der Adapter jede Auskunft, und es gibt nichts zu schützen.

**Prüfung im Identitätsmodul.** `IdentityVerification` verlangt eine laufende Transaktion und sperrt mit `FOR SHARE` erst die Kontozeile, dann die Sitzungszeile, bis zum Commit des Aufrufers. `isCurrentFullSession` gilt nur für eine offene, nicht abgelaufene Sitzung dieses Kontos mit voller Anmeldestufe bei aktivem Konto ohne offenen Pflichtwechsel. Die entscheidenden Bedingungen stehen in der WHERE Klausel der sperrenden Abfrage; ein Entzug, der während des Wartens committet, wird unter READ COMMITTED erneut ausgewertet und gesehen. Abmelden (Sitzungszeile), Deaktivieren, Zurücksetzen und Passwortwechsel (Kontozeile, dann Sitzungszeile) sowie die Verkürzung der Dauer (Sitzungszeilen) schreiben auf diese Zeilen und warten daher auf den Commit einer laufenden Prüfung. Umgekehrt wartet eine Prüfung auf einen noch offenen Entzug und liest danach dessen Ergebnis. Die Uhr wird erst gelesen, nachdem die Sperren gehalten werden und die Sitzungsdaten gelesen sind: Die Sperre stabilisiert die Zeile, nicht die Zeit, und das Warten auf eine noch offene Verkürzung kann das Sitzungsende überschreiten (Befund F1 der Codex Prüfung von PR #97). Die Einstellungszeile der Dauer wird bewusst nicht gesperrt: Sie läge in der Reihenfolge der Verkürzung vor den Sitzungszeilen. Sperrreihenfolge insgesamt: Rechte, Konto, Sitzung, Ressource, Fachobjekt.

**Adapter.** `IdentityPermissionsAdapter` ist die einzige Implementierung von `IdentityAccess`. Er antwortet nur für die Firma der Installation und verweigert Kontexte, die extern sind, eine Organisation tragen oder einen Pflichtwechsel melden. `userExists` zählt auch deaktivierte Konten (für Mitgliedschaften), `userActive` nur aktive; alle Konten gelten bis zur Einführung von Portalkonten als intern. Kein Testdouble ist im Produktstart registriert; `PermissionsIntegrationTest` ersetzt den Adapter unter demselben Beannamen.

**Kontext einer Anfrage.** `PermissionContexts.current()` (Port im Berechtigungsmodul, umgesetzt am Anwendungseinstieg) baut aus der Sitzung der Anfrage, der Firmenkennung und der aktuellen Rechteversion den `AccessContext`. Ein Fachendpunkt ruft ihn in der Transaktion auf, in der er auch `Permissions.require` aufruft; ein zwischenzeitlich geänderter Rechtestand macht den Kontext veraltet und verweigert. Bei unvollständiger Anmeldung (Pflichtwechsel) liefert der Port nichts (Befund F2).

**Letzte Unternehmensverwaltung.** Beim Deaktivieren fragt das Identitätsmodul zuerst den Port `AccountDeactivationGuard`, noch vor allen eigenen Sperren. Der Anwendungseinstieg beantwortet ihn über `CompanyAdministration.remainsWithout`: Das nimmt die exklusive Revisionssperre der Firma, wie jede Rechteänderung, und verlangt ein anderes aktives Konto mit dem Unternehmensrecht `permissions.manage`. Ein Konto außerhalb der Verwaltung darf deaktiviert werden. Antwort bei Verweigerung: `409 account.last_administrator`. Wegen der gemeinsamen Revisionssperre sind Deaktivieren und `setMember` oder `setAction` in beiden Reihenfolgen serialisiert. Fehlt der Port oder gibt es mehrere, wird nichts deaktiviert und die Anfrage endet mit einem internen Fehler.

**Erster Start.** `bootstrap-admin` ruft in der Transaktion des ersten Kontos den Port `FirstAdministratorHandler`: Der Anwendungseinstieg legt die Firma an und `CompanyAdministration.initialize` die Rechteversion, genau eine Gruppe „Unternehmensverwaltung“ mit `permissions.manage` ohne Projekt und den Erstadministrator als Mitglied und mit den Verwaltungsrechten `accounts.manage`, `session_duration.manage`, `appearance.manage` (TASK-040) und `projects.manage` (TASK-026; sieben Versionen und sieben Auditeinträge: Gruppe, fünf Rechte, Mitglied). Keine Fachrechte, kein Auditrecht, keine Projekte. Ein zweiter Aufruf scheitert wie bisher an `bootstrap.accounts_exist`.

**Grenzen.** (1) Eine Datenbank mit Konten, aber ohne Firma (nur aus Entwicklungsständen vor TASK-039 denkbar) wird nicht nachgerüstet: `bootstrap-admin` lehnt ab, solange ein Konto existiert, und ohne Firma verweigert der Adapter alles. Eine solche Entwicklungsdatenbank wird neu angelegt. (2) Das Ablaufen der Sitzung ist keine Schreibaktion und wird nicht gesperrt. Eine Prüfung gilt für den Zeitpunkt, an dem sie ihre Sperren hält und die Sitzung gelesen hat; die Facharbeit bis zum Commit läuft ohne erneute Zeitprüfung, ein Sitzungsende in dieser Zeit stoppt sie nicht. Eine Abschlussprüfung zum Commit gibt es nicht; ob sie nötig ist, entscheidet die Koordination. (3) Jede Deaktivierung nimmt kurz die exklusive Revisionssperre der Firma, auch bei einem Konto ohne Rechte; sie wartet höchstens fünf Sekunden (`lock_timeout` der Transaktion, auch für die folgenden Sperren dieser Deaktivierung). (4) Es gibt weiterhin keine HTTP Endpunkte für Rechte, keine Dienst- und Jobkontexte und keine Projekt- und Objektadapter (TASK-026); P2 bis P6 sind unberührt. (5) Der Marker `administrator` ist mit TASK-040 entfernt. (6) Lokale Läufe nutzten Java 21 und PostgreSQL 16; maßgeblich ist CI mit Java 25 und PostgreSQL 18.6. (7) Die Tests nutzen eine kleine Verbindungsgruppe (`src/test/resources/application.properties`), weil mehrere gleichzeitig gehaltene Spring Kontexte sonst die 100 Verbindungen des Datenbankservers ausschöpfen. (8) Eine getrennte KI Prüfung von Adapter, Sperrreihenfolge und Schranke nach DEC-040 steht aus.

## Verwaltungsrechte (TASK-040)

Konten, Sitzungsdauer und Erscheinungsbild verlangen echte Unternehmensrechte statt der früheren Kennzeichnung `administrator` (DEC-064). Die drei Aktionsschlüssel `accounts.manage`, `session_duration.manage` und `appearance.manage` stehen ohne Projekt im Berechtigungsmodul. Der Katalog `AdministrationRight` und der Port `AdministrationRights` (`require` in der Transaktion der Änderung, `held` als Momentaufnahme) liegen im Plattformmodul; `AdministrationRightsAdapter` im Anwendungseinstieg setzt ihn mit `Permissions` um. Kein Modul importiert `permissions`. Fehlendes Recht, veralteter Kontext, deaktiviertes Konto und abgelaufene Sitzung antworten gleich mit `403 auth.forbidden`.

**Sperrreihenfolge.** Rechtesperre, dann Identität (Konto, dann Sitzung), dann Ressource. Konten, Zurücksetzen und Erscheinungsbild prüfen als erstes. Beim Deaktivieren folgt die Prüfung auf die Schranke der letzten Verwaltung, die die Rechtesperre exklusiv nimmt, denn eine gemeinsame Sperre lässt sich nicht ohne Deadlock aufwerten; eine Momentaufnahme im Endpunkt hält Unberechtigte vorher ab. Die Sitzungsdauer sperrt zuerst die Einstellungszeile (`FOR UPDATE`), weil Anmeldung und Passwortwechsel sie vor dem Konto lesen. `AdministrationRightsApiTest` weist die Sperrfolge mit einem festen Ablauf nach: ohne die Vorsperre gibt es einen Deadlock und eine Antwort 500. Seit TASK-041 (DEC-066) nehmen Deaktivieren, Reaktivieren, Zurücksetzen und die Sitzungsdauer vor der Prüfung die gemeinsame Verwaltungsserialisierung (`AccountRepository.serializeAdministration`, transaktionsgebundene Advisory Sperre), bei der Sitzungsdauer direkt nach der Einstellungszeile, beim Deaktivieren vor der Schranke. Sonst halten zwei Berechtigte, die gleichzeitig das Konto des anderen verwalten, ihre eigenen Konten gemeinsam gesperrt und warten auf das jeweils andere. Die Tests halten die Serialisierung fest, prüfen, dass beide Anfragen dort ohne Identitätssperre warten, und danach eindeutige Ergebnisse ohne 500. Alle Verbraucher des Ports zählen die Kandidaten über `AdministrationRights.unique`; mehrere Implementierungen verweigern auch dann, wenn eine davon Primary ist.

**Sitzungsantwort.** `GET /api/v1/session` nennt die gehaltenen Verwaltungsrechte als Liste `rights`. Sie steuert nur die Anzeige der Oberfläche; entschieden wird auf dem Server.

**Erster Start und Notweg.** `bootstrap-admin` ordnet der Gruppe „Unternehmensverwaltung“ neben `permissions.manage` alle Verwaltungsrechte zu, seit TASK-026 auch `projects.manage`. `reset-admin-password` verlangt über `EmergencyResetPolicy` ein Konto der Unternehmensverwaltung.

**Grenzen.** (1) Ohne HTTP Endpunkte für Rechte (TASK-030) kann kein weiteres Konto Verwaltungsrechte erhalten. (2) Eine nach TASK-039 gestartete Entwicklungsdatenbank hat die drei Rechte nicht und wird neu angelegt. (3) Die Prüfung gilt zum Sperrzeitpunkt; eine Abschlussprüfung beim Commit gibt es nicht. (4) Die Seite Erscheinungsbild bleibt für jede vollständige Anmeldung als Vorschau sichtbar.

## Erscheinungsbild (TASK-036)

Eine Zeile mit Akzentfarbe (`#rrggbb`, kleingeschrieben gespeichert) und optionalem Logo. Vertrag: [appearance.openapi.yaml](../contracts/appearance.openapi.yaml), ein Test vergleicht ihn mit den Routen. Lesen darf jede vollständige Anmeldung, ändern nur ein Konto mit dem Unternehmensrecht `appearance.manage`; die Prüfung liegt im Server, die Oberfläche blendet nur aus.

**Version.** Der `ETag` ist die Version der Zeile. Jede Änderung braucht ihn als `If-Match` (`428 appearance.precondition_required` ohne, `412 appearance.version_conflict` bei veraltetem Wert). Die Zeile wird für die Prüfung gesperrt, zwei Administratoren überschreiben sich also nicht unbemerkt.

**Logo.** PNG, JPEG oder WebP bis 512 KiB als Rohdaten mit passendem `Content-Type`. Der Typ wird an den ersten Bytes erkannt; weicht er ab oder ist es ein anderes Format, antwortet der Server `415 appearance.logo_type_invalid`. SVG wird abgelehnt, weil es Skript tragen kann. Ausgeliefert wird das Logo mit `X-Content-Type-Options: nosniff` und `Content-Security-Policy: default-src 'none'; sandbox`.

**Audit.** Jede Änderung schreibt in `appearance_audit_event` Zeitpunkt, Konto, Benutzername, Ereignis (`appearance.accent_changed`, `appearance.logo_replaced`, `appearance.logo_removed`) und eine kurze Angabe wie alte und neue Farbe. Eine Leseschnittstelle für das Audit gehört zur Administrationsoberfläche (TASK-030).

**Fehlercodes:** `appearance.color_invalid`, `appearance.logo_empty`, `appearance.logo_type_invalid`, `appearance.logo_not_found`, `appearance.version_conflict`, `appearance.precondition_required`, `request.too_large`, dazu `auth.required`, `auth.forbidden`, `auth.csrf_invalid` und `auth.password_change_required` aus der Identität.

## Projekte (TASK-026)

Projekte des Unternehmens mit Mitgliedern und versionierter minimaler Konfiguration (DEC-065). Vertrag: [projects.openapi.yaml](../contracts/projects.openapi.yaml), `ProjectsContractTest` vergleicht ihn mit den Routen. Tickets, Tags, Suche und eine Oberfläche gehören nicht dazu.

**Rechte.** Das Unternehmensrecht `projects.manage` (Katalog `AdministrationRight`, beim ersten Start vergeben) legt Projekte an und verwaltet jedes Projekt des Unternehmens samt Konfiguration und Mitgliedern, ohne Mitglied zu sein. Im Projekt gelten die Aktionen `project.read` (lesen) und `project.manage` (ändern, Mitglieder pflegen). Beide verlangen die Mitgliedschaft und können für das ganze Unternehmen oder nur für ein Projekt vergeben werden; so lässt sich die Pflege eines Projekts an Mitglieder delegieren. Ein Projekt, das jemand nicht lesen darf, antwortet `404 project.not_found` wie ein unbekanntes; ein lesbares, aber nicht änderbares `403 auth.forbidden`. Die Liste zeigt mit `projects.manage` alle Projekte, sonst nur die eigenen mit `project.read`, sortiert nach Schlüssel. Die Antwort nennt `manageable` als Hinweis für die Oberfläche.

**Objektkontext.** `ProjectAccess.resource` ist der Projektadapter des Berechtigungsvertrags: Der Lieferant sperrt in der Transaktion der Prüfung die Projektzeile (`FOR SHARE` zum Lesen, `FOR UPDATE` zum Ändern) und liest danach die Mitgliedschaft. Jede Änderung an Projekt, Konfiguration oder Mitgliedern hält die Zeile exklusiv, also kann eine laufende Prüfung keine halb geänderte Mitgliedschaft sehen; eine Anfrage eines gerade entfernten Mitglieds wartet und antwortet danach 404. Sperrreihenfolge: Rechte, Identität des Anfragenden, Projektzeile, Konto eines neuen Mitglieds. Für spätere Module liefert der Port außerdem die aktuelle Konfigurationsversion und jede gespeicherte Fassung.

**Konfiguration.** Jedes neue Projekt erhält Version 1: Typen Aufgabe und Fehler, Status Neu, In Arbeit, Erledigt (Kategorien `open`, `in_progress`, `done`), Anfangsstatus Neu, Übergänge Neu nach In Arbeit, In Arbeit nach Erledigt und jeweils zurück, Felder Titel, Beschreibung, Typ, Status und Zuständigkeit. Die Felder sind fest; Titel, Typ und Status bleiben Pflicht, Beschreibung und Zuständigkeit können Pflicht werden. Eine Änderung speichert die vollständige Konfiguration als nächste Version (jsonb, nie überschrieben); frühere Versionen bleiben lesbar. Grenzen: 20 Typen, 30 Status, 200 Übergänge, Schlüssel `[a-z][a-z0-9_]{0,39}`, Namen 1 bis 80 Zeichen. Ungültige Konfigurationen antworten `400 request.invalid` mit den Gründen in `violations`.

**Version.** Der `ETag` ist die Version des Projekts und bewegt sich mit jeder Änderung von Name, Beschreibung, Konfiguration oder Mitgliedern. Jede Änderung braucht ihn als `If-Match` (`428 project.precondition_required` ohne, `412 project.version_conflict` bei veraltetem Wert).

**Audit.** `project_audit_event` hält Anlage, Änderung, Konfigurationswechsel und Mitgliederänderungen mit Zeitpunkt und Konto. Eine Leseschnittstelle gehört zur Administrationsoberfläche (TASK-030).

**Fehlercodes:** `project.not_found`, `project.key_taken`, `project.configuration_not_found`, `project.version_conflict`, `project.precondition_required`, `request.invalid`, dazu `auth.required`, `auth.forbidden`, `auth.csrf_invalid` und `auth.password_change_required` aus der Identität.

**Grenzen.** (1) Die Mitgliederliste nennt nur Kontokennungen, keine Namen; ein Leseweg für Namen ohne Kontoverwaltungsrecht kommt mit der Oberfläche. (2) Externe Portalkonten erhalten keinen Zugriff, weil Projekte noch keine Organisation tragen. (3) Eine Konfigurationsänderung prüft nicht, ob Tickets alte Typen oder Status nutzen; das regelt TASK-027 über die Konfigurationsversion des Tickets. (4) Projekte werden weder gelöscht noch archiviert. (5) Eine nach TASK-040 gestartete Entwicklungsdatenbank hat `projects.manage` nicht und wird neu angelegt. (6) Die Prüfung gilt zum Sperrzeitpunkt; eine Abschlussprüfung beim Commit gibt es weiterhin nicht. (7) Eine getrennte KI Prüfung nach DEC-040 steht aus.

## Tickets (TASK-027)

Tickets eines Projekts mit Feldrechten, Historie und Konflikterkennung (DEC-067). Vertrag: [tickets.openapi.yaml](../contracts/tickets.openapi.yaml), `TicketsContractTest` vergleicht ihn mit den Routen. Endpunkte unter `/api/v1/projects/{projectId}/tickets`: Liste (neueste zuerst, `limit` und `before`), Anlage, Lesen und Ändern über die Nummer, Historie.

**Rechte.** Die Aktionen `ticket.read`, `ticket.create` und `ticket.edit` gelten im Projekt und verlangen die Mitgliedschaft; sie können für das Unternehmen oder ein Projekt vergeben werden. Ohne eine davon antwortet das Projekt `404 project.not_found`, ein lesbares Ticket ohne Anlage- oder Änderungsrecht `403 auth.forbidden`. `projects.manage` gibt keinen Zugriff auf Tickets. Vergeben werden die Aktionen und Feldregeln bis zu den Rechteendpunkten (TASK-030) nur direkt in den Tabellen, wie die Tests es tun.

**Feldrechte.** Jedes der fünf Felder braucht eine Leseregel für den Ressourcentyp `ticket`, um gezeigt zu werden, und eine Schreibregel, um geändert zu werden. Ein verborgenes Feld fehlt in `fields` und in der Historie, eine Version mit nur verborgenen Änderungen fehlt ganz. `writableFields` nennt die schreibbaren Felder. Eine Änderung mit einem nicht schreibbaren Feld antwortet 403 und schreibt nichts. Ist Beschreibung oder Zuständigkeit Pflicht, aber für die anlegende Person nicht schreibbar, antwortet die Anlage `409 ticket.required_field_inaccessible`.

**Konfiguration.** Typ, Status, Übergänge und Pflichtfelder kommen aus der aktuellen Konfiguration des Projekts. Ein neues Ticket beginnt im Anfangsstatus; ein Statuswechsel außerhalb der Übergänge antwortet `409 ticket.transition_not_allowed`. Pflicht wird bei der Anlage und für geänderte Felder geprüft. `configurationVersion` nennt die Fassung der letzten Prüfung. Zuständig kann nur ein aktives Mitglied sein (`assignee.not_member`).

**Version und Historie.** Der `ETag` ist die Ticketversion; jede Änderung braucht `If-Match` (`428 ticket.precondition_required`, `412 ticket.version_conflict`). Eine Änderung ohne neuen Wert lässt die Version stehen. `ticket_change` hält je geändertem Feld Version, Zeitpunkt, Konto, alten und neuen Wert; die Anlage steht als Version 1 darin.

**Sperren.** Reihenfolge: Rechte, Identität, Projektzeile geteilt, dann die Ticketzeile exklusiv oder die Zählerzeile, zuletzt das Konto der Zuständigkeit. Eine Anfrage eines gerade entfernten Mitglieds wartet an der Projektzeile und antwortet danach 404. Sperrzeitüberschreitung, Verklemmung und Serialisierungsfehler antworten `503 ticket.busy` mit `Retry-After: 1` (P4 der Gegenprüfung von TASK-025); die Anfrage kann unverändert wiederholt werden.

**Fehlercodes:** `project.not_found`, `ticket.not_found`, `ticket.version_conflict`, `ticket.precondition_required`, `ticket.transition_not_allowed`, `ticket.required_field_inaccessible`, `ticket.busy`, `request.invalid` (Gründe wie `title.invalid`, `type.unknown`, `status.unknown`, `description.required`, `assignee.not_member`, `fields.unknown`), dazu die Codes der Identität.

**Grenzen.** (1) Ein Status, den eine neuere Konfiguration nicht mehr kennt, bleibt am Ticket stehen und kann nur über einen Übergang der aktuellen Fassung verlassen werden. (2) Eine Zuständigkeit bleibt bestehen, wenn das Konto das Projekt verlässt. (3) Tickets werden nicht gelöscht. (4) Keine Kommentare, Anhänge, Beziehungen, Benachrichtigungen, Oberfläche, Tags und Suche. (5) Die Prüfung gilt zum Sperrzeitpunkt, nicht beim Commit. (6) Eine getrennte KI Prüfung nach DEC-040 steht aus.
