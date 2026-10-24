# Prüfbelege TASK-048

Eigene Ausführungen vom **24. Oktober 2026**, Sitzung `codex-20261024-task048-047-review-01`. Produktstand **`1274c318de3a2baa995118804b5debc9391e93d3`**, TASK-048 / PR #160 / DEC-077. Bericht: [REVIEW_TASK-048_NACHPRUEFUNG.md](../../REVIEW_TASK-048_NACHPRUEFUNG.md).

Alle Konten, Verzeichnisdaten, Kennwörter und Rechtezuordnungen der Quellen sind **synthetische Testfixtures**. Programme ausschließlich gegen eigene wegwerfbare Datenbanken ausführen. Es werden keine Produktdatenbanken benötigt. Kein Produktcode wurde verändert. Die Ergebnisse enthalten keine Einmalpasswörter, Sitzungsantworten oder lokalen Rechnerpfade. Vollständige lokale Buildlogs werden wegen ihrer Umgebungsangaben nicht veröffentlicht; [build-results.json](build-results.json) fasst deren Ergebnisse und die Surefire-Ergebnisse zusammen.

## Ausgeführte Standardprüfungen

Die Windows-Starter `python.exe`, `mvnw.cmd` und `npm.cmd` entsprechen hier den folgenden angeforderten Befehlen. Java: Temurin 25.0.4.1+1, Maven 3.9.16, PostgreSQL 18.6, Node 24.22.0, npm 11.19.1. Die lokale PostgreSQL-Instanz wurde separat und nur für synthetische Tests gestartet; kein Docker-Nachweis.

```sh
python3 scripts/validate_repository.py
python3 scripts/task_board.py --check
python3 -m unittest discover -s tests -v

cd backend
./mvnw -B --no-transfer-progress verify \
  -Dtaskmanager.test.jdbc-url="$REVIEW_JDBC_URL" \
  -Dtaskmanager.test.username="$REVIEW_DB_USER" \
  -Dtaskmanager.test.password="$REVIEW_DB_PASSWORD" \
  -Dtaskmanager.test.server-version=18.6
cd ../frontend
npm ci
npm run verify
TASKMANAGER_BROWSER="$REVIEW_CHROMIUM" node --test --test-concurrency=1 tests/browser/*.test.mjs
```

Ergebnisse: Validator OK, Aufgabenübersicht aktuell, Python **38 Tests / 1 Skip / kein Fehler**, Backend **272 / kein Fehler / kein Skip**, Frontend **75 erfolgreich** und Build erfolgreich, Browser **92 / 16 Suiten / kein Fehler / kein Skip**. Python-Skip: Windows bietet `time.tzset` nicht. Backendlauf 4:37 Minuten; LdapLoginTest **25**, LdapTlsTest **4**, DirectoryIdentifierTest **4**, jeweils erfolgreich. Browserdauer 76,08 Sekunden, Dateien nacheinander. Browserchecks größtenteils mit API-Fixtures, kein vollständiger LDAP-Bedienablauf.

## Eigene Proben und Quellen

| Quelle | Ergebnis | Tatsächlicher Weg |
| --- | --- | --- |
| [ProbeSupport.java](ProbeSupport.java), [FollowupProbes.java](FollowupProbes.java) | [http-results.json](http-results.json) | Unveränderte Spring-Produktbeans, echtes HTTP, PostgreSQL und UnboundID; die beiden vorgegebenen binären IDs, getrenntes `accounts.manage` über Gruppen-API, Namenskollision/freie Umbenennung, sechs unvollständige `security`-Anfragen, 32 zusätzliche PUT-Eingaben und StartTLS-Testantwort |
| [IdentifierProbe.java](IdentifierProbe.java) | [identifier-results.json](identifier-results.json) | Aktuelle Produktfunktion `DirectoryClient.identifier`, alle 256 Ein-Byte- und 65.536 Zwei-Byte-Folgen, zusätzliche Unicode-/Präfix-/Leerraumwerte, Mehrfachattribut und verlustbehaftete Strings; früherer **Textpfad** als Vergleich `trim()`, keine Behauptung eines vollständigen historischen LDAP-Laufs für diese Tabelle |
| [WireProbe.java](WireProbe.java) | [wire-results.json](wire-results.json) | Echte LDAP-Verbindung durch JNDI, Attributschreibweisen, gleiche Kennungs-/Namensattribute, fehlende/mehrfache Werte, Validatorablehnung von Attributoptionen/OIDs, StartTLS-Verweigerung mit Such-/Bindzählern sowie Verbindungs- und Suchkonto-Bindfehler |
| [UpgradeProbe.java](UpgradeProbe.java) | [upgrade-results.json](upgrade-results.json) | Flyway zuerst bis V0021, SQL-Fixture mit neun Altkonten und Sitzungen, reguläres Flyway-Upgrade auf V0022 und zweiter Migrationsaufruf, echter CLI-Notweg samt HTTP-Pflichtwechsel, Wiederherstellung per Verwaltung und beide fremden Kontozugriffe nach Upgrade |
| [ReviewJUnit.java](ReviewJUnit.java) | [regression-old-results.json](regression-old-results.json), [regression-current-results.json](regression-current-results.json) | Dieselben vier aktuellen Autorenregressionen über JUnit Platform 6.0.3; einmal drei isolierte Altklassen im vorderen Klassenpfad, einmal ausschließlich aktueller Produktcode |

Der Upgradeprobe setzt die Altkonten direkt als **V0021-Datenbankfixture** ein. Die frühen Konten wurden nicht in diesem Lauf durch die historische Anwendung angelegt. Anfangsrechte des Wiederherstellungskontos sind ebenfalls Teil dieser Fixture. Anschließend erfolgen die Rechtevergabe für die beiden N1-Konten und die LDAP-Konfiguration über das öffentliche API; beide fremden Sitzungen und ihre `GET /api/v1/accounts`-Antwort **200** sind echte HTTP-Ergebnisse. UnboundID arbeitet ohne Zielschema; die gezeigten Rawbytewerte sind daher kein Nachweis ihrer Zulässigkeit in einem bestimmten AD-Attribut. Die UTF-8-Randzeichenvariante benötigt keine ungültigen UTF-8-Bytes.

Die JSON-Ergebnisse sind von den Quellen erzeugte Messausgaben, keine vollständigen Protokollmitschnitte. Prüfungen ohne explizites Assertion in einem Hilfsprogramm wurden anhand ihrer gespeicherten Ausgabe bewertet; ihr Prozessende allein wäre kein ausreichender fachlicher Nachweis. Die Argumente eines künstlich erzeugten Strings mit nicht gepaartem Surrogat stammen nicht aus einer nachgewiesenen LDAP-Antwort und werden nicht als zusätzliches Sicherheitsloch dargestellt.

## Nachvollziehbare Ausführung der Hilfsprogramme

Aus dem Repositorywurzelverzeichnis, nach dem Backend-Build. `TASK_MANAGER_DB_URL`, `TASK_MANAGER_DB_USER` und `TASK_MANAGER_DB_PASSWORD` müssen auf die eigene frische PostgreSQL-Datenbank zeigen. Für `FollowupProbes` und `UpgradeProbe` **verschiedene frische Datenbanken** verwenden. Die folgende Schreibweise ist die plattformneutrale Anleitung; ausgeführt wurden entsprechende Windows-Befehle mit Semikolon als Java-Klassenpfadtrenner. Keine dieser Variablen verweist auf eine Produktumgebung.

```sh
(cd backend && ./mvnw -B --no-transfer-progress dependency:build-classpath \
  -DincludeScope=test -Dmdep.outputFile=target/review-classpath.txt)
CP="backend/target/classes:backend/target/test-classes:$(cat backend/target/review-classpath.txt)"
mkdir -p backend/target/review-probes
javac -encoding UTF-8 -cp "$CP" -d backend/target/review-probes \
  docs/review-evidence/task-048/ProbeSupport.java \
  docs/review-evidence/task-048/FollowupProbes.java \
  docs/review-evidence/task-048/UpgradeProbe.java \
  docs/review-evidence/task-048/IdentifierProbe.java \
  docs/review-evidence/task-048/WireProbe.java
CP="backend/target/review-probes:$CP"
java -cp "$CP" taskmanager.identity.FollowupProbes docs/review-evidence/task-048/http-results.json
java -cp "$CP" taskmanager.identity.directory.IdentifierProbe docs/review-evidence/task-048/identifier-results.json
java -cp "$CP" taskmanager.identity.directory.WireProbe docs/review-evidence/task-048/wire-results.json
# Vor diesem Programm eine andere, frische Datenbank einstellen:
java -cp "$CP" taskmanager.identity.UpgradeProbe docs/review-evidence/task-048/upgrade-results.json
```

`UpgradeProbe` startet das durch `verify` gebaute unveränderte Anwendungs-JAR für `reset-admin-password` als Kindprozess. Das ausgegebene synthetische Einmalpasswort wird nur im Arbeitsspeicher für den Pflichtwechsel genutzt, nicht in die Ergebnisdatei geschrieben.

## Eigener Rot-/Grünvergleich der vier neuen Regressionen

Die Quellen `DirectoryClient.java`, `DirectoryAccounts.java` und `LdapSettingsService.java` wurden mit `git show 6e40ebfd88199f4aa517b55294f8389cc377dbdf:backend/src/main/java/taskmanager/identity/directory/<Datei>` aus PR #155 gelesen, **außerhalb der Produktquellen** gespeichert und nach `backend/target/review-old` kompiliert. Der unveränderte aktuelle Klassenpfad blieb daneben erhalten. Es wurde kein historischer Produktbuild vorgetäuscht.

`ReviewJUnit` verwendet den vorhandenen JUnit-Platform-Launcher 6.0.3 zusätzlich zum Testklassenpfad. Aufruf mit den üblichen `-Dtaskmanager.test.*`-Datenbankparametern:

```sh
# JUNIT_LAUNCHER enthält nur den Pfad zum vorhandenen junit-platform-launcher-6.0.3.jar.
javac -encoding UTF-8 -cp "$CP:$JUNIT_LAUNCHER" -d backend/target/review-probes \
  docs/review-evidence/task-048/ReviewJUnit.java
java $REVIEW_TEST_PROPERTIES -cp "backend/target/review-old:$CP:$JUNIT_LAUNCHER" \
  ReviewJUnit docs/review-evidence/task-048/regression-old-results.json PR-155-overlay 4
java $REVIEW_TEST_PROPERTIES -cp "$CP:$JUNIT_LAUNCHER" \
  ReviewJUnit docs/review-evidence/task-048/regression-current-results.json 1274c31 0
```

Ausgeführt werden ausschließlich die vier Methoden zu binären Personen, bekanntem Namenskonflikt, unvollständiger Verschlüsselung und abgelehntem StartTLS. Alt: **vier Tests entdeckt, vier Assertionsfehler**, keine Container-/Startfehler. Beobachtet: falscher Benutzer, 200 statt 401, 500 statt 400 und `search_failed` statt `tls_failed`. Aktuell: **vier Tests entdeckt und erfolgreich**. Meldungen im Ergebnis sind auf die Assertions verkürzt; HTTP-Sitzungsantworten werden entfernt. Ein erster Stand des eigenen Starters hielt nach fertigen Tests den Spring-Testkontext offen; nur dieser eigene Prozess wurde beendet und der Starter mit explizitem Prozessende erneut vollständig ausgeführt.

## Grenzen und Abschluss

Keine Qualifikation von OpenLDAP/AD und kein CI-/Dockerlauf durch diese Prüfung. Ein zusätzlich vorbereiteter WSL-Shell-Lebenszykluslauf scheiterte an der Verbindung zu der eigenen Windows-Testdatenbank bereits vor dem Aufruf des eigentlichen Skripts; dafür wird kein positives Ergebnis beansprucht. Kein vollständiger manueller Übersetzungsdurchlauf, kein umfassender Last-/Rennentest. Die nachgewiesene N1-Kollision hängt von Bestandsdaten und tatsächlichen Kennungswerten ab; sie darf weder aus einem frischen Build noch aus der Zahl grüner Tests ausgeschlossen werden.

Nach Berichtserstellung erneut ausgeführt: `python3 scripts/validate_repository.py` **OK**, `python3 scripts/task_board.py --check` **aktuell**, `git diff --check` und `git diff --cached --check` **ohne Befund**. Veröffentlichte Änderungen ausschließlich Bericht und dieses Belegverzeichnis; TODO.md, DONE.md und Produktdateien unverändert.
