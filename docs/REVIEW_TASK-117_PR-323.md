# Unabhängige Nachprüfung von TASK-117 / PR #323

**Empfehlung · Nachbessern.** Parallele Listenabrufe können bereits gelesene Daten einer überholten Verbindung noch ausgeben. Der Erneuerungspfad hat einen verbleibenden Zustandswechsel am neueren Token. Die Evidenzprüfung und das Demo-SQL halten ihre zugesicherten Grenzen in den unten beschriebenen Fällen noch nicht ein.

**Prüfstand** · 15. November 2026. PR #323, Branch `claude/project-thread-6p8449`, Ergebnis 35d7e52, PR-Basis `6adb9873c7e80095dc988184a313cccbb6ae8044`. Der rekonstruierte lokale Dateibaum stimmt mit dem Git-Baum `54328b04d007dcdcc16385193c026a2c8c32e7fd` des Ergebniscommits überein. Maßstab waren [AGENTS.md](../AGENTS.md), [DEVELOPMENT_REVIEW.md](DEVELOPMENT_REVIEW.md), [TASK-117 mit Regeln, Abnahmekriterien und Übergabe](../TODO.md#L223), [DEC-144 bis DEC-147](../DECISIONS.md#L234) sowie die [Nachprüfung zu PR #322](REVIEW_TASK-116_PR-322.md). Die 15 Dateien des PRs einschließlich der generierten Frontend-Typen gehören zur Nachbesserung.

## Befunde vor der Abnahme

### F1 · Hoch · Die fertige Liste kann Daten einer inzwischen ersetzten Verbindung ausgeben

- **Dateien und Zeilen** · [ConnectionService.java:249](../backend/src/main/java/taskmanager/git/internal/ConnectionService.java#L249), [LinkService.java:138](../backend/src/main/java/taskmanager/git/internal/LinkService.java#L138), [LinkService.java:167](../backend/src/main/java/taskmanager/git/internal/LinkService.java#L167), [LinkService.java:189](../backend/src/main/java/taskmanager/git/internal/LinkService.java#L189), [ReleaseService.java:77](../backend/src/main/java/taskmanager/git/internal/ReleaseService.java#L77).
- **Szenario und Folge** · In einer Liste mit mehreren Verknüpfungen beendet ein schneller Abruf seine Generationsprüfung, während ein zweiter Abruf noch läuft. Die Person verbindet anschließend ein anderes Git-Konto oder trennt die Verbindung. Der zweite Abruf fällt nach Abschluss auf einen Platzhalter zurück. Das bereits fertige Ergebnis des ersten Abrufs liegt ohne Generation in der Liste und wird weiterhin ausgegeben, einschließlich der zuvor gelesenen Repository- oder Objektdaten. Derselbe Ablauf ist bei der Auswahl freigegebener Repositories und der Liste der Freigaben möglich. Die Prüfung je Anbieterabruf erfasst diesen Zeitraum vor der gemeinsamen Ausgabe nicht. Der Regressionstest mit einem gehaltenen Objektabruf deckt ihn nicht ab. Befund aus Codeprüfung.
- **Vorschlag** · Ergebnisse der parallelen Abrufe bis zur Zusammenführung mit ihrer tatsächlich verwendeten Generation führen. Die Generationen unmittelbar vor der Ausgabe gemeinsam prüfen und veraltete Einträge durch Platzhalter ersetzen oder aus der Auswahl entfernen. Einen Test mit einem bereits abgeschlossenen und einem gehaltenen Abruf ergänzen und dabei eine Neuverbindung sowie eine Erneuerung durch einen parallelen Abruf abdecken.

### F2 · Mittel · Eine alte Ablehnung kann einen neueren Tokenstand bei einem Lesefehler verwerfen

- **Dateien und Zeilen** · [ConnectionService.java:354](../backend/src/main/java/taskmanager/git/internal/ConnectionService.java#L354), [ConnectionService.java:372](../backend/src/main/java/taskmanager/git/internal/ConnectionService.java#L372), [GitStore.java:106](../backend/src/main/java/taskmanager/git/internal/GitStore.java#L106).
- **Szenario und Folge** · Eine verspätete 401 trifft auf dieselbe Verbindung mit einem inzwischen erneuerten, aktiven Token. Der Normalfall verwendet das aktuelle Token ohne zusätzlichen Refresh. Kann der alte Abruf dessen verschlüsselte Form nicht öffnen, etwa bei einem beschädigten gespeicherten Token, ruft der Zweig für den abweichenden Stand trotzdem `markRenew` auf. Damit löscht er das Access- und das Refresh-Token des neueren Stands. Regel 1 bindet Zustandsänderungen an den erwarteten Tokenstand. Befund aus Codeprüfung.
- **Vorschlag** · Im Zweig für eine abweichende Generation einen Öffnungsfehler ohne Schreibzugriff beenden. Einen tatsächlich mit dem neueren Stand begonnenen Abruf den Zustand wie bisher bedingt markieren lassen.

### F3 · Mittel · Doppelte JSON-Schlüssel entziehen Werte der Inhalts- und Geheimnisprüfung

- **Dateien und Zeilen** · [redact.py:90](../tools/git/redact.py#L90), [redact.py:108](../tools/git/redact.py#L108), [redact.py:137](../tools/git/redact.py#L137), [gitlab-check.sh:42](../tools/git/gitlab-check.sh#L42).
- **Szenario und Folge** · `json.loads` behält bei mehrfach vorkommenden Schlüsseln nur den letzten Wert. Feldprüfung und Suche in decodierten Zeichenketten sehen daher nur den überschreibenden Wert. Der Upload verwendet anschließend die ursprüngliche Datei, in der beide Werte stehen. Eine lokale Probe mit einem harmlosen zusätzlichen `detail`-Wert bestätigte, dass der Prüfer die Datei akzeptiert. Ein verworfener Wert fehlt ebenso in der Prüfung der decodierten Zeichenketten.
- **Vorschlag** · Den JSON-Parser doppelte Schlüssel auf jeder Ebene ablehnen lassen und den einmal streng decodierten Wert für Schema und Geheimnissuche wiederverwenden. Den Negativtest beim Aufruf von `--check` verankern.

### F4 · Mittel · Eine bearbeitete Revision 2 bleibt für das Demo-SQL zulässig

- **Dateien und Zeilen** · [git-examples.sql:88](../tools/demo/git-examples.sql#L88), [RequirementService.java:432](../backend/src/main/java/taskmanager/requirements/internal/RequirementService.java#L432), [RequirementRepository.java:240](../backend/src/main/java/taskmanager/requirements/internal/RequirementRepository.java#L240), [GIT_MIGRATION.md:134](GIT_MIGRATION.md#L134).
- **Szenario und Folge** · Admin bearbeitet nach dem Seed die Beschreibung der schon offenen Revision 2. Die normale Anforderungslogik überschreibt den Entwurf in derselben Revision und erhöht die Version der Anforderung. Titel, Autor, Status sowie aktuelle und freigegebene Revisionsnummer können dabei gleich bleiben. Das Skript prüft weder die Version der Anforderung noch den Inhalt des Entwurfs. Eine synthetische PostgreSQL-18.6-Probe mit dem unveränderten Skript bestätigte für diesen Fall fünf Links, zwei Freigaben, zwei Audits und fünf Historieneinträge. Bei entfernter Mitgliedschaft, geänderter Git-Feldregel oder aktueller Revision 3 waren alle fünf Tabellen danach leer.
- **Vorschlag** · Die beim Seed erreichte Anforderungsversion oder einen überprüfbaren Beleg über die gespeicherten Stände mitprüfen. Den normalen Speichervorgang von Admin innerhalb derselben Revision in die Gegenproben aufnehmen.

### F5 · Mittel · Demo-SQL und Instanzänderung können sich gegenseitig sperren

- **Dateien und Zeilen** · [git-examples.sql:22](../tools/demo/git-examples.sql#L22), [git-examples.sql:26](../tools/demo/git-examples.sql#L26), [ProviderService.java:96](../backend/src/main/java/taskmanager/git/internal/ProviderService.java#L96), [ProviderService.java:111](../backend/src/main/java/taskmanager/git/internal/ProviderService.java#L111), [ProviderService.java:114](../backend/src/main/java/taskmanager/git/internal/ProviderService.java#L114).
- **Szenario und Folge** · Das Skript sperrt `git_audit_event` zuerst im Modus SHARE ROW EXCLUSIVE und fordert danach SHARE auf `git_provider` an. Eine bereits laufende Instanzänderung hält nach dem Update ROW EXCLUSIVE auf `git_provider` und benötigt für ihr Audit ROW EXCLUSIVE auf `git_audit_event`. Die Sperren stehen in beiden Richtungen im Konflikt. PostgreSQL muss eine Transaktion abbrechen. Das Skript bleibt atomar, aber bei paralleler Nutzung kann auch die Instanzänderung abgebrochen werden. Befund aus der Sperrreihenfolge im Code. Der lokale Einzelbenutzermodus hatte keine zweite Session für diesen Fall.
- **Vorschlag** · Eine eigene Sperre für parallele Demo-Läufe verwenden und die übrigen Sperren mit der Reihenfolge der normalen Schreibwege abstimmen. Mit zwei Sessions nachweisen, dass die Instanzänderung und das Demo-Skript in beiden Startreihenfolgen terminieren und auf dem jeweils gültigen Bestand arbeiten.

## Hinweise

### H1 · Die Zugriffs- und Sperrtests können zwei weitere Reihenfolgen belegen

- **Dateien und Zeilen** · [GitApiTest.java:1443](../backend/src/test/java/taskmanager/git/GitApiTest.java#L1443), [GitApiTest.java:1487](../backend/src/test/java/taskmanager/git/GitApiTest.java#L1487), [GitApiTest.java:1586](../backend/src/test/java/taskmanager/git/GitApiTest.java#L1586).
- **Szenario und Vorschlag** · Der Sperrtest prüft für den Ticket-Link beide Reihenfolgen und bei der Freigabe den Fall mit zuerst festgeschriebenem Trennen. Ergänzend kann die Freigabe nach `holdCurrent` gezielt aufgehalten werden, um das wartende Trennen abzusichern. Beim Token prüft der neue Test die bereits auf `renew` gesetzte Zeile. Ein eigener Test kann den erfolgreichen Refresh derselben Verbindung durch einen zweiten Abruf, die Wiederverwendung des neuen Tokens durch den ersten Abruf und genau einen Refresh zusammen belegen.

### H2 · Die Aussage zum OAuth-Nachweis lässt sich genauer abgrenzen

- **Dateien und Zeilen** · [GIT_MIGRATION.md:138](GIT_MIGRATION.md#L138), [redact.py:28](../tools/git/redact.py#L28), [gitlab_check.py:67](../tools/git/gitlab_check.py#L67).
- **Szenario und Vorschlag** · Die Betriebsanleitung nennt jetzt die Diagnosefelder, die fünf in den Fehler eingespeisten Werte und den Abbruch vor OAuth bei `fail_at=4` korrekt. Der folgende Satz über die ergänzenden Belege kann so gelesen werden, als würde der Positivlauf auch nach den im OAuth-Ablauf erzeugten Werten suchen. Die sieben bekannten Werte sind Umgebungswerte des Laufs. Für Zustand, Autorisierungscode und vom Anbieter ausgegebene Token belegt der Lauf den Ablauf und die fest definierten Belegfelder. Eine Suche nach diesen konkreten Werten ist darin nicht enthalten. Diese beiden Nachweise im Text präzisieren.

## Abgleich mit PR #322

| Punkt | Ergebnis |
| --- | --- |
| F1 · Token und Generation | Die verspätete 401 einer früheren Verbindung endet unter Zeilensperre vor Verwendung des neueren Refresh-Tokens. Die eigene Erneuerung liefert die neue Generation, eine fremde Erneuerung derselben Verbindung liefert deren aktuelles Token. Cache und anfrageinterner Repository-Nachweis verwenden dessen Generation. Eine parallel auf `renew` gesetzte Zeile ohne Access-Token liefert jetzt den richtigen Platzhalter. Zur abschließenden Ausgabe und zum Öffnungsfehler siehe F1 und F2 dieses Berichts. |
| F2 · Schreibtransaktion | Link und Freigabe sperren die Instanz vor der Verbindung. `holdCurrent` hält die Zeilensperre in der jeweiligen Schreibtransaktion. Rückruf und Instanzänderung folgen ebenfalls der Reihenfolge Instanz vor Verbindung. Trennen und Kontodeaktivierung löschen die Verbindungszeilen. Refresh und `renewLater` halten die Verbindungszeile und lesen die Instanz ohne Zeilensperre. Der Refresh schreibt bei unverändertem Fremdschlüssel dieselbe Verbindungszeile. In diesen API-Pfaden ergibt sich daraus kein umgekehrter Wartepfad. Der ausgeführte CI-Test bestätigt 409 ohne neuen Link, Historieneintrag, Freigabe oder Freigabeaudit, wenn das Trennen zuerst festgeschrieben wurde. Die separat hinzugekommenen Tabellensperren des Demo-Skripts siehe F5. |
| F3 · GitLab-Evidenz | Erzeuger und Prüfer teilen die Listen für Schritttexte und Codes. Dienste haben eine feste Liste. Die vorhandenen Negativtests für unbekannten Code, abweichenden Detailtext, Unicode-Escape und fremden Dienst bestehen. Die Inhaltsprüfung bei doppelten Schlüsseln bleibt offen, siehe F3. |
| F4 · Demo | Die sechs Mitgliedschaftsmengen und die Git-Feldregeln werden geprüft. Die Tabellen für Gruppen, Mitgliedschaften, Feldregeln und Anforderungen bleiben bis zum Commit gegen schreibende Änderungen gesperrt. Eine fachliche Bearbeitung innerhalb von Revision 2 passiert die Prüfung, siehe F4. |
| H1 · Betriebsanleitung | Diagnosefelder und Umfang des Fehlerlaufs sind berichtigt. Zur Formulierung des ergänzenden OAuth-Nachweises siehe H2. |
| H2 · 60-Sekunden-Frist | `FollowingClock` springt für 403 und 404 jeweils um 61 Sekunden vor. Der Test ruft danach ohne `refresh=true` ab, prüft beide Platzhalter samt fehlender Objektangaben und den Zustand `active` der Verbindung ([GitApiTest.java:1661](../backend/src/test/java/taskmanager/git/GitApiTest.java#L1661)). |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `git diff --check` | Lokal erfolgreich. GitHub Actions bestätigt die Prüfungen auf dem Ergebnisstand. |
| `python3 -m unittest discover -s tests -v` | Lokal 50 Tests. Alle acht GitLab-Evidenztests bestanden. Die netzabhängigen Fälle in LostAnswerTests endeten mit neun Fehlern und einem Folgefehler der Zählprüfung, weil Windows Loopback-Verbindungen mit WinError 10013 verweigerte. Ein Unix-Zeitzonentest wurde übersprungen. Die Repository-Checks einschließlich Python-Tests waren in CI grün. |
| Backend `GitApiTest`, `GitCacheTest`, `GitAddressTest`, `GitContractTest` | Lokale Kompilierung mit Java 25 erfolgreich. GitCacheTest und GitAddressTest bestanden. GitApiTest und GitContractTest konnten ihren lokalen PostgreSQL-Testkontext wegen gesperrtem Loopback nicht starten. CI auf dem Ergebnisstand meldet 672 bestandene Backend-Tests, darunter 26 API-, drei Cache-, ein Adress- und ein Vertragstest. |
| `npm ci --offline`, `npm run verify` | Lokal erfolgreich. Typgenerierung, Typprüfung, 102 Unit-Tests und Produktionsbuild. |
| `node --test tests/browser/gitLinks.test.mjs tests/browser/git.test.mjs` | Lokal zwei Tests des Testersatzes grün. Die sieben Browserfälle scheiterten vor dem Seitenaufbau mit `net::ERR_NETWORK_ACCESS_DENIED` für localhost. Die CI-Browserprüfung mit diesen Dateien war grün, insgesamt 310 Tests. |
| `tools/demo/image-check.sh`, `tools/git/gitlab-check.sh` | Lokale Aufrufe scheiterten bereits beim Bash-Start. Die Docker-Linux-Engine war ebenfalls nicht erreichbar. Der Imagecheck und der reguläre GitLab-16-Lauf einschließlich Evidenz-Upload waren in CI auf 35d7e52 grün. |
| GitLab 16 mit `fail_at=4` | CI am direkten Vorgänger 85a0473 meldete wie vorgesehen in Schritt 4 HTTP 500 `forced.failure` und lud das freigegebene Artefakt hoch. Zwischen diesem Commit und 35d7e52 änderten sich nur ConnectionService.java, GitApiTest.java und backend/README.md. Ein erzwungener CI-Lauf für den Ergebniscommit blieb unbestätigt. |
| Eigene Gegenproben | Synthetische PostgreSQL-18.6-Bestände im Einzelbenutzermodus mit tatsächlichem Demo-Skript, siehe F4. Harmloser doppelter JSON-Schlüssel gegen den tatsächlichen Prüfer, siehe F3. F1, F2 und F5 beruhen auf Codeprüfung. |

Die Live-Abnahme mit registrierten Anwendungen bei GitHub.com und GitLab.com bleibt gemäß TASK-113 separat offen.
