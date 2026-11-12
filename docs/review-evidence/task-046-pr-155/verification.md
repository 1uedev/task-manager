# Ausführungsnachweise TASK-046 / PR #155

23. Oktober 2026, Sitzung `codex-20261023-task046-review-01`. Getesteter Checkout `434592f5b17552e0daff5247f1b06342f59c724b`; Produktcode und Verträge identisch zu Merge `6e40ebfd88199f4aa517b55294f8389cc377dbdf`. Ausschließlich synthetische Daten und eigene Wegwerfdatenbanken. Kein Passwort, Cookie, Token, privater Maschinenpfad oder privates Prozesslog wird als Ergebnis veröffentlicht. Die Geheimnisliterale in den ausführbaren Quellen sind ausdrücklich künstliche Fixturewerte, keine Produktstandards.

## Selbst ausgeführte Standardprüfungen

```text
python3 scripts/validate_repository.py
python3 scripts/task_board.py --check
python3 -m unittest discover -s tests -v

# backend/; unter Windows mvnw.cmd, Temurin 25.0.4.1+1
./mvnw -B --no-transfer-progress verify \
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:61565/task046_review_20261023 \
  -Dtaskmanager.test.username=reviewer \
  -Dtaskmanager.test.password=synthetic-review-only \
  -Dtaskmanager.test.server-version=18.6

# frontend/, nach erfolgreichem Backendlauf
npm ci
npm run verify
# danach; tatsächliche lokale Chromium-Datei nur in der Umgebung gesetzt
TASKMANAGER_BROWSER=<Chromium> node --test --test-concurrency=1 tests/browser/*.test.mjs
```

Werkzeuge: Maven 3.9.16, Temurin 25.0.4.1+1, PostgreSQL 18.6 nativ, Node 24.22.0, npm 11.19.1, Chrome über Playwright-Core. Kein Testcontainer-/Docker-Nachweis.

Aus den eigenen lokalen Prozesslogs zusammengefasste tatsächliche Ergebnisse:

```text
Repository contract: OK (IDs, links, task metadata and completion evidence)
Aufgabenübersicht: aktuell

Python, nach Netzwerkfreigabe für eigene Loopback-Testserver:
Ran 38 tests in 46.934s
OK (skipped=1)

Backend:
Tests run: 259, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 05:25 min
Finished at: 2026-10-23T17:27:40+02:00
LdapLoginTest: 16; LdapTlsTest: 4, jeweils ohne Fehler

Frontend verify:
tests 75; pass 75; fail 0; skipped 0
vite v8.3.4, 53 modules transformed, production build successful

Browserdateien, explizit concurrency=1:
tests 90; suites 15; pass 90; fail 0; cancelled 0; skipped 0
duration_ms 86272.2881
```

Der Python-Skip betrifft `SessionTimeTests.test_local_time_zone_plays_no_role`: unter Windows fehlt `time.tzset`. Erster unveränderter Python-Lauf in der Netzwerksandbox: lokale Socketzugriffe mit WinError 10013 verweigert, 1 Fehlerassertion/9 Errors als Folge; danach vollständiger erfolgreicher Lauf mit erlaubtem Loopback. Dies wurde nicht als Produktfehler gewertet. Rohlogs bleiben wegen möglicher lokaler Pfade und synthetischer Sitzungswerte privat; obige Abschnitte sind ausdrücklich Zusammenfassungen, keine CI-Artefakte.

## Eigene Proben ausführen

Alle Quellen unter diesem Verzeichnis verändern ihre eigene synthetische Installation. **Nur mit einer eigenen leeren Wegwerfdatenbank ausführen.** Keine Testquelle in Produktpfade kopieren. Keine Änderungen an Produktbeans, keine Ersatz-Identitätsadapter und keine geänderte Produkt-Uhr.

Nach `mvnw verify` im Backend einmal mit `dependency:build-classpath`, `-DincludeScope=test` und `-Dmdep.outputFile=target/review-classpath.txt` den Classpath erstellen. Für die folgenden Befehle steht `<CP>` für `backend/target/classes`, `backend/target/test-classes` und diese Abhängigkeiten (unter Windows mit Semikolon, sonst Doppelpunkt trennen). Das Testverzeichnis wird nur für die bereits vorhandene kleine HTTP-Hilfe `ApiClient` benötigt.

```text
# Repository-Stamm, Temurin 25
javac -cp <CP> -d backend/target/review-probes \
  docs/review-evidence/task-046-pr-155/ReviewLdapProbes.java \
  docs/review-evidence/task-046-pr-155/BinaryIdentifierProbe.java \
  docs/review-evidence/task-046-pr-155/TransportProbes.java \
  docs/review-evidence/task-046-pr-155/MigrationProbe.java
```

Den entstandenen Ordner `backend/target/review-probes` vor `<CP>` in den Laufzeit-Classpath aufnehmen. `TASK_MANAGER_DB_URL`, **`TASK_MANAGER_DB_USER`**, `TASK_MANAGER_DB_PASSWORD` auf die eigene Datenbank setzen. Für jeden der folgenden drei Datenbankläufe eine **neue** leere Datenbank verwenden; die Quellen bootstrappen selbst und lassen keine anderen Daten zurücksetzen:

```text
java -cp <CP-mit-review-probes> taskmanager.identity.ReviewLdapProbes \
  docs/review-evidence/task-046-pr-155/ldap-probe-results.json

# neue leere Datenbank setzen
java -cp <CP-mit-review-probes> taskmanager.identity.BinaryIdentifierProbe \
  docs/review-evidence/task-046-pr-155/binary-id-results.json

# neue leere Datenbank setzen
java -cp <CP-mit-review-probes> MigrationProbe \
  docs/review-evidence/task-046-pr-155/migration-results.json

# ohne Datenbank; erzeugt temporäre, ausschließlich synthetische Schlüssel/Stores
java -cp <CP-mit-review-probes> taskmanager.identity.directory.TransportProbes \
  docs/review-evidence/task-046-pr-155/transport-results.json
```

TransportProbes verwendet im tatsächlich ausgeführten Windowslauf `keytool.exe` unter `java.home`; auf anderen Betriebssystemen ist der Name entsprechend `keytool`. Es erzeugt den Testserver mit synthetischem Schlüssel, nicht mit echtem Unternehmensvertrauen. Das Ergebnis enthält keinen Dateipfad und keinen privaten Schlüssel. Die lokale JVM-/Netzwerkumgebung muss das Starten der flüchtigen LDAP-/HTTP-Testserver erlauben.

### Ergebnisse und Beweisgrenzen

- [ldap-probe-results.json](ldap-probe-results.json): drei fehlerhafte Verschlüsselungswerte, Namenskollision bekannter Konten, Maskierung **auf dem Draht**, Sonderzeichenpasswort, Passwortgrenzen, einzelne Slash-Suchbasis, Alias/Referral, Größen-/Zeitlimit, absichtlich geheime Serverdiagnose, abgelehntes StartTLS ohne Suche oder Credential-Bind, vier nachweislich am INSERT wartende Anmeldungen mit erfolgreicher Wiederholung, zwei beobachtete Sperrfolgen, Kontotyp-Schranke und Netzaufruf mit null aktiven DB-Verbindungen.
- [binary-id-results.json](binary-id-results.json): zwei verschiedene 16-Byte-Kennungen, beide Logins 200, zweite Person wird als erste angemeldet und liest mit deren `accounts.manage` die Kontenliste. Rechte werden **regulär über das Gruppen-API** nur der ersten Person gegeben. Auf zwei frischen Datenbanken reproduziert; die zweite Fassung hält zusätzlich die U+FFFD-Codepunkte fest. Das ist ein belegter Rechteübergriff ohne Produktänderung, kein bloßer Identifikatorvergleich.
- [transport-results.json](transport-results.json): eigene erfolgreiche LDAPS-/StartTLS-Personenbindung, falscher Zertifikatsname vor Credential-Bind verweigert, unvertrauenswürdige CA verweigert, drei kaputte Storekonfigurationen fail closed. Der serverseitige TrustAllTrustManager ist ausschließlich Fixture-Zubehör; der Produktclient verwendet immer das konfigurierte Java-Vertrauen und wird nicht auf TrustAll umgestellt.
- [migration-results.json](migration-results.json): befüllter V0020-Stand, echtes Flyway-Upgrade nach V0021, Defaultwerte und Constraint-Verweigerungen mit SQLSTATE. Nur kleine synthetische Daten, keine Großmigration.

In ReviewLdapProbes bereitet eine ausdrücklich gehaltene erste `DirectoryAccounts.assign`-Transaktion das Erstanmeldungsrennen vor; die vier konkurrierenden Anmeldungen laufen vollständig über HTTP. Datenbankbeobachtung zählt tatsächliche Lock-Wartepositionen, statt Gleichzeitigkeit nur aus Pausen abzuleiten. Die Dauerprobe hält absichtlich eine Zielkontozeile, die Deaktivierungsprobe hält die echte Serviceänderung vor Commit. Der künstlich bekannte lokale Hash ist eine weitere explizite Datenbankfixture, kein erreichbarer Produktweg.

Der Referral-Beleg zählt Bind-/Suchanfragen an der zweiten LDAP-Gegenstelle, nicht jeden möglichen TCP-Verbindungsversuch. Der beobachtete Clientmodus `ignore` und die ausgebliebene Anfrage belegen in dieser Probe keine Referral-Verfolgung. Es wurde kein massenhafter Socket-Leck-/Lasttest durchgeführt. Browser-Standardsuiten arbeiten mit API-Fixtures; eigene HTTP-Proben ergänzen die wirkliche Anmeldung, aber keinen vollständigen End-to-End-Bedienablauf.

Anfängliche Fehler der Review-Hilfen wurden vor den endgültigen Läufen korrigiert: Java-Source-Launcher benötigte hier eine zum Package passende Ablage, deshalb explizit kompiliert; zuerst war der falsche Name der DB-Benutzervariable gesetzt. Daraus wird kein Produktfehler abgeleitet. Die Produktdateien wurden während der gesamten Prüfung nicht verändert.

## Abschließende Kontrollen

- Repositoryvalidierung auf fertigem Bericht: **OK**.
- `task_board.py --check`: **aktuell**.
- `git diff --check` und `git diff --cached --check`: ohne Fehler.
- Nur neuer Bericht und Textbelege unter `docs/`; keine Produkt-, Vertrags-, TODO- oder DONE-Änderung. Alle eigenen Anwendungs-/LDAP-Proben beendeten ihre Kontexte; die eigens gestartete PostgreSQL-Instanz anschließend gestoppt.
- Der veröffentlichte Remote-Branch wird vor dem Öffnen des PR zurückgelesen und gegen den geprüften Index verglichen; bei einer Abweichung wird der PR nicht als fertig veröffentlicht.
