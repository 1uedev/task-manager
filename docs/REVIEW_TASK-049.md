# Technische Nachprüfung TASK-049 / PR #164

**Ergebnis: Die angeforderten Tests sind grün; ein mittlerer Restbefund bleibt in der Selbstumstellung. Empfehlung: annehmbar nach Behebung von R1.** Die beiden Identitätsprobleme und der verlorene Anzeigename sind im geprüften Umfang behoben. Das Einmalpasswort bleibt im einfachen Ablauf sichtbar, kann aber durch eine verspätete Leseanfrage oder weiterhin angebotene Verwaltungsaktionen erneut verschwinden. Die fachliche Abnahme bleibt bei Holger.

## Prüfstand und Umfang

- Datum: **25. Oktober 2026**, Sitzung `codex-20261025-task049-review-01`, getrennte technische Prüfung nach DEC-040.
- Gegenstand: [PR #164](https://github.com/1uedev/task-manager/pull/164), Merge **`ad44fdb09be39017d5197f01efba83fdbad4e821`** auf `main`. Genau dieser Stand wurde in einem eigenen Klon gebaut und getestet; beim Abruf war er auch die Spitze von `main`.
- Gelesen: [TASK-049](../TODO.md#task-049-befunde-der-nachprüfung-von-task-048-und-der-prüfung-von-task-047-beheben), DEC-078 in [DECISIONS.md](../DECISIONS.md), die vorausgehenden Entscheidungen DEC-076/077, Repository-Anweisungen und die betroffenen Implementierungen samt Aufrufern, Verträgen und Tests. TASK-049 bleibt **BEREIT ZUR PRÜFUNG** bei der umsetzenden Sitzung; dieser Bericht ändert keine Zuständigkeit und keine Abnahme.
- Alle **18 geänderten Dateien** betrachtet: AGENTS.md, CLAUDE.md, DECISIONS.md, TODO.md; Backend-README und Lebenszyklusskript; DirectoryAccounts, DirectoryClient, SessionService, Migration V0023, ApplicationLifecycleTest, DatabaseFoundationTest, LdapLoginTest; AccountsPage, beide Sprachkataloge und die beiden geänderten Browser-Testdateien.
- Bericht auf **`review/task-049`**. Keine Produktänderung, keine Änderung an TODO/DONE und kein Schreiben auf `main`.

## R1 — mittel / P2: Einmalpasswort nach Selbstumstellung weiterhin nicht zuverlässig geschützt

**Fundstelle im PR:** [AccountsPage.tsx](../frontend/src/pages/AccountsPage.tsx), Zeilen **77–80** und **139–155**. Die neue Variable `sessionEnded` ergänzt ausschließlich Hinweis und Anmeldeschaltfläche; sie schafft keinen eigenständigen Abschlusszustand. Kontenaktionen, LDAP-Formular und weitere Seitenteile bleiben aktiv, und schon laufende Anfragen werden nicht berücksichtigt.

**Ursache:** Der Verzicht auf `reloadDirectory()` und `reload()` verhindert die unmittelbar vom Umstellungsaufruf ausgelösten Folgeanfragen. Jede andere geschützte Anfrage kann nach dem serverseitigen Sitzungsende aber noch 401 liefern. Die bestehende globale Behandlung in [client.ts](../frontend/src/api/client.ts), Zeilen 117–119, und [session.tsx](../frontend/src/app/session.tsx), Zeile 43, wechselt dann zur Anmeldung und entfernt die Kontenseite einschließlich ihres ausschließlich dort gespeicherten Einmalpassworts.

**Eigene Browserreproduktion am unveränderten Produktionsbuild, Deutsch:**

1. In der vorhandenen Browser-Fixture `anna.authKind = 'ldap'` setzen und als Anna die Kontenseite öffnen.
2. Die beim Seitenaufbau angeforderte Abfrage `GET /api/v1/settings/ldap` vor der Bearbeitung durch die Fixture zurückhalten; die Kontenliste bereits anzeigen lassen.
3. Anna auf lokal umstellen und bestätigen. Die Antwort enthält das Einmalpasswort; die neue Passwortanzeige ist zunächst sichtbar.
4. Die zurückgehaltene Abfrage anschließend unverändert an die bestehende Fixture weiterreichen. Diese liefert wegen der inzwischen beendeten Sitzung **401**. Ohne weiteren Klick erscheint die Anmeldung; die Passwortanzeige ist **nicht mehr vorhanden**.

Der zeitliche Ablauf wurde mit einem zusätzlichen Playwright-Routenhandler hergestellt: vor der Bearbeitung der LDAP-Leseanfrage warten, danach `route.fallback()` aufrufen. Weder die Umstellungsantwort noch die Sitzungslogik der im PR geänderten Fixture wurden ersetzt; der 401 wurde nicht künstlich vorgegeben. Dies ist ein Browsernachweis mit kontrollierter API-Fixture, kein durchgängiger Browserlauf am echten Backend.

**Zweite Reproduktion ohne verzögerte Anfrage:** Nach erfolgreicher Selbstumstellung bleibt beispielsweise **„ben deaktivieren“** bedienbar. Anklicken löst einen geschützten POST aus, der **401** erhält; das Einmalpasswort verschwindet ebenfalls und die Anmeldung wird gezeigt. Der zugehörige Handler leert `issued` sogar bereits vor der Antwort. Das betrifft die in DEC-078 zugesicherte sichere Passwortübernahme, nicht nur eine veraltete Schaltfläche.

**Separater Nachweis am echten Backend:** Eigenes LDAP-Konto mit `accounts.manage` meldet sich an und stellt sich per HTTP selbst um: **200**, Einmalpasswort vorhanden; anschließendes `GET /api/v1/settings/ldap`: **401**. Damit entspricht die für die Browserprobe verwendete Sitzungsbeendigung dem tatsächlich geprüften Backend.

| Eigene Browserprobe | Passwort zunächst sichtbar | Antwort nach Umstellung | Passwort danach sichtbar |
| --- | --- | --- | --- |
| Bereits angeforderte LDAP-Leseanfrage später bearbeiten | ja | 401 | nein, Anmeldung sichtbar |
| Weiterhin angebotene Kontenaktion betätigen | ja | 401 | nein, Anmeldung sichtbar |

**Auswirkung:** Die Person kann ihr einmalig ausgegebenes Passwort verlieren, bevor sie es übernommen hat, und benötigt dann Hilfe einer anderen Verwaltung. Kein zusätzlicher Rechtezugriff oder Datenabfluss durch diesen Oberflächenbefund nachgewiesen.

**Empfehlung:** Nach erfolgreicher Selbstumstellung einen gesonderten Abschlusszustand verwenden, der das Einmalpasswort bis zum bewussten Wechsel zur Anmeldung im Speicher hält, keine geschützten Aktionen mehr anbietet und verspätete Antworten berücksichtigt. Diesen Zustand so in die Sitzungsbehandlung integrieren, dass ein verspäteter 401 die Anzeige nicht entfernt; die normale 401-Behandlung außerhalb dieses Abschlusses beibehalten. Beide obigen Abläufe als Regression ergänzen. Der neue Test in [ldap.test.mjs](../frontend/tests/browser/ldap.test.mjs), Zeilen 188–207, prüft lediglich den ungestörten Ablauf mit 500 ms Wartezeit und anschließendem Klick auf „Zur Anmeldung“; er deckt diese Gegenbeispiele nicht ab.

## Ergebnis der gezielten Nachprüfung

| Ursprünglicher Befund | Ergebnis an `ad44fdb` |
| --- | --- |
| TASK-048 N1: Altschlüssel kollidiert mit neuer Kennung | **Behoben im geprüften Umfang.** Echtes, befülltes Flyway-Upgrade V0022 → V0023 und beide kollidierenden Anmeldungen erfolgreich geprüft; keine Übernahme der Altkonten oder ihrer Rechte. |
| TASK-048 N2: Anzeigename bei gleichem Kennungs-/Namensattribut | **Behoben.** Strenge UTF-8-Dekodierung der Rohbytes; vorhandener Integrationstest mit `uid`/`UID` erfolgreich. Die ausgelagerte Dekodierung erhält die bisherigen Regeln der Kennungsbildung. |
| TASK-047 F1: Laufende LDAP-Anmeldung überlebt lokale Umstellung | **Behoben in beiden geprüften Zeitplänen.** Vorhandene Regression mit unverbrauchtem Einmalpasswort und eigener erneuter Lauf mit bereits abgeschlossenem lokalem Pflichtwechsel liefern 401. |
| TASK-047 F2: Einmalpasswort bei Selbstumstellung verloren | **Teilweise behoben.** Der neue reguläre Browserablauf besteht; die zusätzlichen Abläufe unter R1 verlieren das Passwort weiter. |

### Befülltes Upgrade und Wiederherstellung

Die eigene Probe migrierte eine separate leere Datenbank zunächst regulär bis **V0022**, richtete eine lokale Verwaltung ein und fügte zwei synthetische LDAP-Altkonten mit den Schlüsseln `hex:80` und `trim-id`, jeweils einer Gruppenmitgliedschaft mit `accounts.manage` und einer offenen Sitzung ein. Nach Schließen des Anwendungskontexts führte Flyway **genau eine Migration V0023** aus; ein weiterer Migrationslauf führte **keine** aus.

- Beide Altkonten danach: `auth_kind=local`, `external_id=null`, inaktiv; alte Sitzungen beendet; genau ein `account.directory_link_reset` je Konto; Konto-IDs und Mitgliedschaften erhalten.
- Die vorhandene lokale Verwaltung konnte sich weiterhin anmelden und die Konten lesen (**200**).
- Jeweils den ursprünglichen Eintrag (`hex:80` als Text beziehungsweise ` trim-id ` mit Rand-Leerzeichen) und einen anderen Eintrag mit kollidierendem neuem Schlüssel bereitgestellt. Anmeldung unter dem alten Kontonamen mit dem Verzeichnispasswort: **401**. Die andere Person mit Rohbyte `80` beziehungsweise Text `trim-id` erhielt ein **eigenes LDAP-Konto**; Kontenverwaltung jeweils **403**.
- Für ein getrenntes Altkonto regulär Passwort zurückgesetzt, Konto reaktiviert und Pflichtwechsel durchgeführt: erfolgreich, dieselbe Konto-ID und wieder **200** auf die Kontenverwaltung. Die Rechte wurden nicht auf das neue LDAP-Konto übertragen.

Die pauschale Trennung aller vorhandenen LDAP-Konten ist eine ausdrücklich in DEC-078 gewählte Betriebsfolge, kein unbeabsichtigter Datenverlust. Der neue Hinweis in `backend/README.md` auf die relative Verwaltungssperre und den vorab zu prüfenden Notweg passt dazu. Er ersetzt weder die Prüfung einer realen Installation noch deren Wiederherstellungsübung.

### Beide Zeitpläne der laufenden Anmeldung

1. **Umstellung gewinnt die Kontosperre, Einmalpasswort noch unbenutzt:** Der neue Test `aDirectoryLoginThatIsOverhauledByTheConversionIsRefusedAndKeepsTheOneTimePassword` lief im vollständigen Backendlauf erfolgreich. Er beobachtet die PostgreSQL-Sperrwartebeziehung, erwartet anschließend 401 ohne neue Sitzung und prüft, dass die rechtmäßige Anmeldung mit dem Einmalpasswort weiterhin gelingt.
2. **Umstellung und lokaler Pflichtwechsel bereits abgeschlossen:** Die frühere [ConversionRaceProbe](https://github.com/1uedev/task-manager/blob/review/task-047/docs/review-evidence/task-047/ConversionRaceProbe.java) wurde mit ihrem Hilfscode gegen die neu kompilierten Klassen von `ad44fdb` und eine neue Datenbank erneut ausgeführt. Sie pausiert nach dem tatsächlichen Lookup der alten LDAP-Zuordnung, ohne dessen Ergebnis zu verändern. Währenddessen laufen Umstellung und lokaler Passwortwechsel über andere HTTP-Clients. Die fortgesetzte Anmeldung, die ausschließlich das alte LDAP-Passwort gesendet hat, erhält jetzt **401**, ihr Zugriff auf die Kontenverwaltung ebenfalls **401**; das frühere Gegenbeispiel mit voller Sitzung tritt nicht mehr auf.

`DirectoryAccounts.assign`, Zeilen 43–48, prüft den aktuellen Kontotyp und die externe Kennung unmittelbar nach der Zeilensperre. Diese Sperre bleibt bis zur Sitzungserzeugung in derselben Transaktion erhalten. `SessionService.startSession`, Zeilen 189–192, prüft zusätzlich beide Werte gegen den zuvor verifizierten Datensatz. Keine Verlagerung des LDAP-Netzwerkaufrufs unter diese Datenbanksperre gefunden.

**Testabdeckungshinweis:** Der zweite Zeitplan ist in diesem Review erneut nachgewiesen, aber nicht als zusätzliche dauerhafte Regression in PR #164 enthalten, obwohl TASK-049 beide nennt. Die bereits vorhandene Probe sollte bei der Ergänzung der Tests übernommen werden. Das ist hier kein weiterer nachgewiesener Produktfehler.

## Selbst ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | **Repository contract: OK** |
| `python3 scripts/task_board.py --check` | **Aufgabenübersicht aktuell** |
| `python3 -m unittest discover -s tests -v` | **38 Tests erfasst, 37 ausgeführt erfolgreich, 1 plattformbedingter Skip** (`time.tzset`/Zeitzonenwechsel nur unter Unix) |
| Backend `./mvnw -B verify` mit den drei angeforderten Datenbankeigenschaften | **BUILD SUCCESS; 275 Tests, 0 Failures, 0 Errors, 0 Skips**, 36 Testsuiten, 6:16 Minuten |
| Davon LDAP und Lebenszyklus | LdapLoginTest **28**, LdapTlsTest **4**, DirectoryIdentifierTest **4**, ApplicationLifecycleTest **2**; alle erfolgreich |
| Frontend `npm ci --ignore-scripts --offline`, `npm run verify` | Erfolgreich; Vertragsgenerierung, Typprüfung, **75 Tests** und Produktionsbuild |
| Browser `node --test --test-concurrency=1 tests/browser/*.test.mjs` | **93 Tests / 17 Suiten, alle erfolgreich, 0 Skips** |
| Zusätzliche eigene Prüfungen | Befülltes Upgrade, beide Kennungskollisionen, lokale Wiederherstellung, zweiter Umstellungszeitplan und echte HTTP-Selbstumstellung erfolgreich; zwei Browsergegenbeispiele bestätigen **R1** |
| Berichtskontrolle | Repositoryvalidator, Aufgabenübersicht und `git diff --check` nach Berichtserstellung erfolgreich |

Laufzeit: Windows, Python **3.12.14** mit `PYTHONUTF8=1`, Temurin **25.0.4.1+1**, Maven **3.9.16**, native PostgreSQL **18.6**, Node **24.22.0** und installiertes Chrome. Die 275 Tests wurden zusätzlich aus den 36 Surefire-XML-Berichten summiert; keine übersprungenen Backendtests. Sämtliche Datenbanken und Verzeichniseinträge waren isolierte synthetische Testdaten.

Unter Windows wurde der mitgelieferte Starter **`mvnw.cmd`** als Entsprechung zu `./mvnw` verwendet; `python3` war auf den vorhandenen Python-3-Interpreter abgebildet. Tatsächlicher Maven-Aufruf in anonymisierter Form:

```text
./mvnw.cmd -B --no-transfer-progress verify
  -Dmaven.repo.local=<vorhandener lokaler Cache>
  -Dtaskmanager.test.jdbc-url=jdbc:postgresql://127.0.0.1:<Testport>/task049_review
  -Dtaskmanager.test.username=<eigener Testbenutzer>
  -Dtaskmanager.test.password=<synthetisches Testpasswort>
```

Es wurde kein Override von `taskmanager.test.server-version` gesetzt; die erwartete Version **18.6** wurde vom Testserver bestätigt. Die ersten Python-Regressionstestversuche innerhalb der Sandbox scheiterten an temporären Dateirechten beziehungsweise blockierter lokaler Testkommunikation. Der endgültige Lauf mit beschreibbarem Temp-Verzeichnis außerhalb dieser Einschränkungen ist der oben ausgewiesene erfolgreiche Lauf; dafür wurden keine Produkt- oder Testdateien geändert.

## Grenzen und Empfehlung

Nicht ausgeführt: Qualifikation gegen ein reales OpenLDAP-/AD-System, Docker-/CI-Containerlauf, zusätzlicher Lauf von `backend/scripts/lifecycle-check.sh`, vollständiger Browserablauf am echten Backend und umfassender Last-/Parallelitätstest. Die geänderten Migrationserwartungen im Shellskript wurden gelesen; Erststart und Neustart wurden durch ApplicationLifecycleTest, das befüllte Upgrade zusätzlich durch die eigene Flyway-Probe geprüft. Keine Behauptung einer Prüfung aller möglichen Zeitpläne oder eines produktionsreifen Gesamtprodukts.

**Keine weiteren konkreten Fehler in den geänderten Dateien nachgewiesen.** Die sicherheitsrelevanten Backendkorrekturen bestehen die untersuchten Gegenbeispiele. Für den vollständigen Abschluss von TASK-049 sollte **R1** behoben und mit den beschriebenen Browserfällen nachgeprüft werden; technische Empfehlung daher **annehmbar nach Behebung von R1**, keine fachliche Abnahme im Namen von Holger.
