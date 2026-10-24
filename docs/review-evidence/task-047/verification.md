# Prüfbelege TASK-047 / PR #157

Eigene Ausführung am **24. Oktober 2026**, Sitzung `codex-20261024-task047-review-01`. Getesteter Produktcommit **`1274c318de3a2baa995118804b5debc9391e93d3`**; Prüfgegenstand ist die Umsetzung aus PR #157 / `a1426fbd1bc58bf90ad7382bfa433201d0b2e597`, DEC-076. Siehe [Bericht](../../REVIEW_TASK-047.md) für Abgrenzung der späteren TASK-048-Änderungen.

Alle Beispiele und Kennwörter sind synthetische Testfixtures. Die Hilfsprogramme benötigen **eigene frische wegwerfbare Datenbanken**, keine Produktdaten. Veröffentlichung nur von Quellen und bereinigten Ergebnissen; keine Einmalpasswörter, Sitzungstoken, Kundendaten oder lokalen Rechnerpfade. Kein Produktcode wurde geändert. Die Java-Quellen liegen bewusst außerhalb von `backend/src` und sind kein automatisch eingebauter Produkttestsatz.

## Standardprüfungen

Windows mit Temurin **25.0.4.1+1**, Maven **3.9.16**, eigener nativer PostgreSQL **18.6**, Node **24.22.0**, npm **11.19.1** und installiertem Chrome. Ausgeführt mit den entsprechenden Windows-Startern; semantisch diese Befehle:

```sh
python3 scripts/validate_repository.py
python3 scripts/task_board.py --check
PYTHONUTF8=1 python3 -m unittest discover -s tests -v
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

Ergebnis: Repositoryvertrag **OK**, Aufgabenübersicht **aktuell**; Python **38 Tests / ein Skip / kein Fehler** mit UTF-8-Vorgabe. Erster Python-Lauf ohne Vorgabe: acht fehlgeschlagene Prüfungen, weil die Windows-Standardkodierung temporäre TODO-Testdateien nicht als UTF-8 schrieb; der unveränderte erneute Lauf mit `PYTHONUTF8=1` bestand. Skip wegen fehlendem `time.tzset`. Kein Produktfehler aus diesem Umgebungsproblem abgeleitet.

Backend: **272 Tests / kein Fehler / kein Skip, BUILD SUCCESS**, 6:15 Minuten; LdapLoginTest **25**, IdentityPermissionsAdapterTest **18**, PermissionsApiTest **8**, IdentityApiTest **33**. Frontend: Vertragsgenerierung, Typprüfung, **75 Tests** und Produktionsbuild erfolgreich. Browser: **92 Tests / 16 Suiten / alle erfolgreich**, Dateien nacheinander, **131275,5201 ms**. [build-results.json](build-results.json) enthält aus eigenen lokalen Logs und Surefire-XML extrahierte Zusammenfassungen, keine CI-Ergebnisse. Vollständige lokale Buildlogs mit Umgebungsangaben werden nicht veröffentlicht.

## Eigene Programme und tatsächlich erzeugte Ergebnisse

| Quelle | Ergebnisdatei | Nachweis |
| --- | --- | --- |
| [ProbeSupport.java](ProbeSupport.java), [GuardProbes.java](GuardProbes.java) | [guard-results.json](guard-results.json) | Echte HTTP-Änderungen, lokale/LDAP-Verwaltungen, Rechte-/Mitgliedschafts-/Kontoentzug, explizite PostgreSQL-Wartebeobachtungen, Umstellung gegen LDAP-Anmeldung mit echter Kontosperre, bereits lokal und späterer Alias |
| [ConversionRaceProbe.java](ConversionRaceProbe.java) | [conversion-race-results.json](conversion-race-results.json) | F1 mit voller lokaler Sitzung nach abgeschlossenem lokalem Pflichtwechsel; tatsächliche HTTP-Aufrufe, nur die Lücke nach dem echten Kennungslookup wird durch einen Repository-Wrapper angehalten |
| [AdministrationProbes.java](AdministrationProbes.java) | [administration-results.json](administration-results.json) | Verbindliche Rechteprüfung nach frühem Prüfergebnis und Warteschlange; Umstellung/Deaktivierung in beiden Reihenfolgen; echte Selbstumstellung mit anschließend ungültiger ausführender Sitzung |
| [RelativeGuardProbe.java](RelativeGuardProbe.java) | [relative-results.json](relative-results.json) | Zwei explizite Legacy-Fixtures, echte CLI-Kindprozesse, Pflichtwechsel und spätere API-Rechteergänzung, Grenzen des Notwegs, Wiederherstellung mit offener LDAP-Sitzung während Ausfall |
| [RecoveryUpgradeProbe.java](RecoveryUpgradeProbe.java) | [upgrade-results.json](upgrade-results.json) | Echtes befülltes Flyway-Upgrade V0021→V0022, Konten-/Sitzungs-/Auditwirkung, realer CLI-Notreset und anschließender HTTP-Pflichtwechsel |
| [self-conversion-browser.mjs](self-conversion-browser.mjs) | [self-conversion-browser-results.json](self-conversion-browser-results.json) | F2 im gebauten Frontend, DE und EN, mit dem separat am Backend bestätigten 401 nach Selbstumstellung als API-Fixture |

Die Programme verwenden unveränderte Produktbeans und HTTP-Routen mit einer synthetischen UnboundID-Gegenstelle. Anfangsrechte werden in den normalen HTTP-Proben über das Gruppen-API vergeben. `GuardProbes` basiert auf der eigenen zuvor vorbereiteten, für TASK-048 nicht veröffentlichten Warteprobe und wurde hier im neuen Klon auf neuen Datenbanken erneut ausgeführt und erweitert. Frühere Ergebnisse werden nicht als neue Ausführung ausgegeben.

**Wartebelege:** `waitCount` beobachtet `pg_stat_activity`, eingeschränkt auf die aktuelle Testdatenbank, `wait_event_type='Lock'` und die jeweilige SQL-Stelle. Mitgliedschaft/Recht: je zwei Wartende an exklusiver Rechteversion; Deaktivierung: zwei Wartende an Verwaltungsserialisierung. Gemischter Fall: zwei Wartende an der Rechteversion, bewusst sowohl `FOR SHARE` als auch `FOR UPDATE`, weil die frühe Rechteansicht bereits dort warten kann. Die Ergebnisse einer ersten weniger passenden SQL-Filterung wurden durch die endgültige passende Abfrage und neue vollständige Läufe ersetzt.

Bei **Umstellung zuerst** beobachtet die Probe den ersten Vorgang nach der Kontomutation und den zweiten an der Verwaltungsserialisierung. Bei **Deaktivierung zuerst** beobachtet sie den ersten nach der Kontomutation und den zweiten schon an der frühen gemeinsamen Rechtesperre. Null in der jeweils anderen Zählerspalte wird nicht als beobachtete Sperre ausgegeben. Die Testtrigger pausieren nur nach der tatsächlichen Mutation, ändern keinen Produktwert und werden wieder entfernt.

**F1, volle Sitzung:** Der BeanPostProcessor pausiert genau nach `AccountRepository.findByExternalId` und vor `findByIdForUpdate`, nachdem die echte SQL-Abfrage bereits den alten LDAP-Datensatz geliefert hat. Rückgabe und Argumente bleiben unverändert. Währenddessen laufen Umstellung und lokaler Pflichtwechsel über getrennte echte HTTP-Clients. Der wartende Client sendet ausschließlich das alte Verzeichnispasswort; er erhält danach trotzdem `full`, `local` und Konten-API 200. Der zweite F1-Ablauf verwendet keinen Repository-Wrapper: beobachtetes Warten an echter Kontosperre führt nach der Umstellung zur lokalen eingeschränkten Sitzung und verbraucht das neue Einmalpasswort.

**Altzustände:** `RelativeGuardProbe` setzt historisch mögliche Daten mit direktem SQL ein, die von den heutigen relativen Regeln nicht neu erzeugt werden müssen. Das ist kein Nachweis, dass eine aktuelle API-Entkleidung der letzten lokalen Verwaltung gelungen wäre. `RecoveryUpgradeProbe` migriert zunächst regulär bis V0021, setzt neun Altkonten/Sitzungen und die ehemalige LDAP-Verwaltung als Fixture ein und migriert anschließend weiter. Eine zunächst schon fehlende lokale Verwaltung ist ausdrücklich Teil dieses Ausgangszustands. Beide Programme fangen das synthetische CLI-Einmalpasswort nur für den anschließenden HTTP-Pflichtwechsel im Speicher ab.

**Browsergrenze:** Die eigene Zusatzprobe verwendet das gebaute Frontend und die vorhandene API-Fixture, ergänzt um das echte Sitzungsende nach Selbstumstellung. Der reale HTTP-Teil ist separat in `administration-results.json` belegt. Es wird kein vollständiger Ende-zu-Ende-Browsernachweis am echten Backend behauptet. Die vorhandene Fixture setzt Cookies für `localhost`; nach Korrektur des anfänglich anders gewählten Hosts reproduzierte die eigene Probe F2 vollständig in beiden Sprachen.

## Wiederholung

Nach dem Backend-Build den Testklassenpfad erzeugen. Die folgende Anleitung verwendet Unix-Klassenpfadtrenner; unter Windows wurde `;` verwendet. Die fünf Java-Programme mit Datenbankzugriff jeweils auf **verschiedene frische Datenbanken** richten. `TASK_MANAGER_DB_URL`, `TASK_MANAGER_DB_USER` und `TASK_MANAGER_DB_PASSWORD` sind die regulären Produktvariablen für diese eigenen Testdatenbanken.

```sh
(cd backend && ./mvnw -B --no-transfer-progress dependency:build-classpath \
  -DincludeScope=test -Dmdep.outputFile=target/review-classpath.txt)
CP="backend/target/classes:backend/target/test-classes:$(cat backend/target/review-classpath.txt)"
mkdir -p backend/target/review-probes
javac -encoding UTF-8 -cp "$CP" -d backend/target/review-probes docs/review-evidence/task-047/*.java
CP="backend/target/review-probes:$CP"
java -cp "$CP" taskmanager.identity.GuardProbes docs/review-evidence/task-047/guard-results.json
java -cp "$CP" taskmanager.identity.ConversionRaceProbe docs/review-evidence/task-047/conversion-race-results.json
java -cp "$CP" taskmanager.identity.AdministrationProbes docs/review-evidence/task-047/administration-results.json
java -cp "$CP" taskmanager.identity.RelativeGuardProbe docs/review-evidence/task-047/relative-results.json
java -cp "$CP" taskmanager.identity.RecoveryUpgradeProbe docs/review-evidence/task-047/upgrade-results.json
TASKMANAGER_BROWSER="$REVIEW_CHROMIUM" node docs/review-evidence/task-047/self-conversion-browser.mjs \
  docs/review-evidence/task-047/self-conversion-browser-results.json
```

Für die tatsächliche Ausführung wurde der bereits vorhandene Testklassenpfad desselben unveränderten Backend-POM aus dem vorherigen eigenen Build übernommen; alle hier ausgeführten Produkt-/Testklassen stammen aus dem neu geprüften Klon und seinem eigenen vollständigen Build. Die Java-Hilfsprogramme wurden hier separat kompiliert. Die Befehle mit `dependency:build-classpath` zeigen, wie der Pfad ohne diesen lokalen Cache reproduzierbar erzeugt werden kann.

Nicht ausgeführt: Zielverzeichnisqualifikation OpenLDAP/AD, Docker-/CI-Container, zusätzlicher Shell-Lebenszykluslauf, vollständiger echter Backend-Browserablauf und exhaustive Zeitplan-/Lastprüfung. Nach Berichtserstellung erneut erfolgreich: Repositoryvalidator, Aufgabenübersicht, `git diff --check`, `git diff --cached --check`. Veröffentlichte Dateien ausschließlich Bericht und Belegverzeichnis; Aufgabenstatus und Produktdateien unverändert.
