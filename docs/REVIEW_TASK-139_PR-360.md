# Prüfbericht zu TASK-139 / PR #360

**Technische Empfehlung – nachbessern.** Der manuelle Release-Lauf prüft das Tag jetzt anhand eines festen `main`-Commits. Die CI belegt den Update- und Rückkehrweg, gültige und ungültige Prüfsummenzeilen und frische Dateibereinigungsaufträge. Offene Befunde betreffen unter anderem historische Release-Trigger, bereits vorhandene Sicherungspunkte und die Firewall-Regel für entfernte Proxys.

**Prüfstand.** [PR #360](https://github.com/1uedev/task-manager/pull/360), Branch `claude/project-thread-elfpvq`, Head `b29dabb1f8b1f82a5fededde4cd0f1165b6c384f`, Basis `59bd4cfa3e93a1a0910c615ed48f9d57441483c0`, GitHub-Prüfmerge `b6f6b21e6c25b73fc63977bcc5a68b1fc925ab2f`. Head und Prüfmerge haben denselben Dateibaum. Der PR war am 25. November 2026 bei Abschluss der Prüfung offen. Maßstab sind AGENTS.md, docs/DEVELOPMENT_REVIEW.md, [TASK-139](../TODO.md#task-139-befunde-f1-bis-f10-und-hinweise-h1-bis-h4-der-codex-prüfung-von-task-137-beheben), DEC-170, der [Bericht zu TASK-137](REVIEW_TASK-137_PR-355.md) sowie O01 und O11. Zeilenangaben beziehen sich auf den PR-Head.

## Befunde vor der Abnahme

### N1 – Hoch – Tags auf Altständen können weiterhin den alten Push-Workflow starten (F1)

**Ort.** `.github/workflows/release.yml:13–24, :43–72`, `docs/INSTALL.md:175, :191–192`, die Release-Workflow-Datei des Vorgängers auf `main`.

**Beobachtung.** Der neue Workflow hat nur `workflow_dispatch`. Sein Job `check` checkt `GITHUB_SHA` aus, holt nur die Git-Objekte des Tags und prüft dessen Commit gegen den festen Referenz-Commit. Die Anleitung erlaubt weiterhin ein Tag auf einem gewünschten Commit in der Geschichte von `main`. Dazu gehören veröffentlichbare Altstände wie der Merge von #355. Deren Workflow-Datei enthält noch den Auslöser `push.tags: ['v*']`. GitHub nimmt für das Push-Ereignis die Workflow-Version des zugehörigen Stands. Damit kann beim Setzen eines solchen Tags der alte `check` aus dem Tag-Stand laufen, bevor der neue manuelle Lauf überhaupt startet. Die vorgesehene Beschränkung der Umgebung auf den Branch `main` sperrt den alten Veröffentlichungsjob, sobald sie eingerichtet ist. Der vorgelagerte alte Lesejob nutzt diese Umgebung nicht.

**Korrektur.** Den frühesten für eine Veröffentlichung vorgesehenen Stand festlegen und den Tag- und Release-Weg ab diesem Stand verbindlich absichern. Den Altstand im Verfahren behandeln und die historischen Tag-Trigger bei der Freigabeprüfung berücksichtigen. [GitHub – Zuordnung eines Ereignisses zur Workflow-Version](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflows#workflow-triggers) · [Workflow am Merge von PR #355](https://github.com/1uedev/task-manager/blob/48bbbcad65a28b50a6ce50ecc6c7c35ecdfc421f/.github/workflows/release.yml#L14-L17)

### N2 – Mittel – Das Tag kann zwischen dem letzten Abgleich und der Veröffentlichung auf einen anderen Commit wechseln (F1/F2)

**Ort.** `.github/workflows/release.yml:89–112, :136–151, :164–186`, `docs/INSTALL.md:181–185, :192`.

**Beobachtung.** Nach der Freigabe prüft der Workflow das Tag und baut den geprüften Commit. Zwischen dieser Prüfung und `gh release create` liegen Build, Analyse, Image-Push und Paketbau. Der dokumentierte Regelsatz lässt Holgers Konto auch bei Änderungen und Löschungen passieren. Wechselt das Tag in dieser Zeit, erkennt `--verify-tag` die Abweichung nicht, weil der Schalter nur das Vorhandensein des entfernten Tags prüft. Die Release-Downloads können so zu einem anderen Commit als das Tag gehören. Eine Gegenprobe in einem lokalen synthetischen Git-Repository bestätigte, dass das Tag nach dem Wechsel weiter existiert, während sich der aufgelöste Commit unterscheidet. Es wurde kein GitHub-Tag verändert.

**Korrektur.** Die Zuordnung während der Veröffentlichung durch unveränderliche Release-Tags absichern und den Commit unmittelbar vor der Veröffentlichung erneut abgleichen. Die spätere Tag-Änderung in die Gegenprobe aufnehmen. [GitHub CLI – `gh release create` und `--verify-tag`](https://cli.github.com/manual/gh_release_create)

### N3 – Mittel – `CONNECTION_ONLY` nimmt auch Einstellungen aus, die die Migration beeinflussen (F5)

**Ort.** `backend/src/main/java/taskmanager/operations/internal/SchemaGuard.java:149–187, :214–241`, `backend/src/test/java/taskmanager/operations/internal/SchemaGuardSettingsTest.java:75–114`.

**Beobachtung.** Die neuen Tests decken Platzhalter, Callbacks und Schemaanlage ab. Der reflektierende Vergleich überspringt aber `class-loader` und `plugin-register` vollständig. Flyway nutzt den konfigurierten ClassLoader für Migrationen und Resolver. Ein unveränderter Wert `classpath:db/migration` bezeichnet mit einem anderen ClassLoader daher nicht zwingend dieselben Ressourcen. Die eigene Java-Gegenprobe mit Flyway 12.4.0 stellte unter diesem Pfad eine zusätzliche synthetische Textressource bereit, die nur der geänderte Loader sah. Der Guard akzeptierte die Konfiguration. Auch ein geänderter JDBC-Wert `currentSchema` wurde akzeptiert. Die Prüfung aufzählbarer Spring-Einstellungen bleibt wirksam. Die Gegenprobe betrifft die verlangte Kontrolle programmatischer Änderungen, ohne SQL auszuführen.

**Korrektur.** Die Ausnahmen auf die tatsächlich nötigen Verbindungsparameter begrenzen und die Ressourcen- und Plugin-Auflösung an den Stand des Images binden. Die Auswahl des Schemas nicht über pauschal freigegebene JDBC-Eigenschaften verändern lassen. Neben den bestehenden Tests eine programmatische Änderung an der Ressourcenauflösung prüfen. [Flyway – Schnittstelle `Configuration`](https://github.com/flyway/flyway/blob/main/flyway-core/src/main/java/org/flywaydb/core/api/configuration/Configuration.java)

### N4 – Mittel – Ältere aufbewahrte Punkte können einen höheren Namen tragen als `latest-point` (F6)

**Ort.** `deploy/backup/backup.sh:29–61`, `tests/test_backup_names.py:53–83`, `docs/BACKUP_RESTORE.md:119`.

**Beobachtung.** Der neue Zähler übernimmt `base-name` und `latest-point` und bricht bei beschädigtem Zustand ab. Die Vorlage von TASK-136 schrieb die UTC-Sekunde direkt als Namen und speicherte in `latest-point` den zuletzt erstellten Punkt. Nach einem Uhrenrücksprung unter TASK-136 kann der zuletzt geschriebene Name kleiner sein als der eines weiter aufbewahrten Punkts. In der synthetischen Gegenrechnung blieb ein Punkt von 10:00:00 erhalten, der alte Dienst schrieb danach 09:59:59 als `latest-point` und die Basis stammte von 08:00:00. Ohne Zähler vergab die neue Berechnung bei 09:59:59 wieder den Namen von 10:00:00. Der Altbestandstest setzt den höchsten vorhandenen Namen als `latest-point` voraus und erfasst diesen Verlauf nicht.

**Korrektur.** Beim ersten Lauf ohne Zähler auch aufbewahrte ältere Kennungen berücksichtigen oder neue Namen eindeutig vom alten Format abgrenzen. Den zweifachen Wechsel der Reihenfolge im Altbestand als Regression absichern. [Namensvergabe im Stand von TASK-136](https://github.com/1uedev/task-manager/blob/135a2c337655c53d9612e8b38bb65bdec9859d8a/deploy/backup/backup.sh#L145-L186)

### N5 – Mittel – Die Sicherung im Rückkehrschritt kann nach dem ersten Fehler scheinbar erfolgreich enden (F9)

**Ort.** `docs/INSTALL.md:124–151`, `tools/update/update-check.sh:611–646`.

**Beobachtung.** Der geschützte Löschblock prüft den eingegebenen Projektnamen und die beiden Volume-Labels. CI führt genau diesen Block mit falschem und richtigem Namen aus. Das davor gezeigte optionale Archivieren ruft `docker run` in einer Schleife ohne Abbruch bei Fehlern auf. Scheitert das Archiv von `db-data` und gelingt anschließend `files-data`, endet der kopierte Block in einer normalen Shell mit Erfolg. Der Löschblock darf anschließend beide Volumes entfernen, obwohl die gewählte Sicherung unvollständig ist. Der Kontrollblock führt `docker compose down` zudem vor der Kontrolle des Projektnamens aus und kann damit eine versehentlich ausgewählte Installation anhalten.

**Korrektur.** Zuerst das Ziel prüfen. Den Archivblock beim ersten Fehler abbrechen lassen und vor dem Löschen bestätigen, dass beide gewählten Archive vollständig angelegt wurden. Den Löschblock mit seinen bestehenden Kontrollen beibehalten und den Fehler beim ersten Archiv zusätzlich abdecken.

### N6 – Mittel – Die Proxy-Regel kann auch Antworten an den erlaubten Proxy verwerfen (H4)

**Ort.** `docs/INSTALL.md:73–76`.

**Beobachtung.** Das Beispiel prüft den ursprünglichen Zielendpunkt per Conntrack. Ohne Begrenzung auf die ursprüngliche Paketflussrichtung gilt dieser Vergleich in beiden Richtungen. Ein Antwortpaket des Anwendungscontainers hat denselben ursprünglichen Zielendpunkt im Conntrack-Eintrag, aber die Quelladresse des Containers. Damit trifft auch `! -s <Adresse des Proxys>` zu. Wird die Regel wie angegeben am Anfang von `DOCKER-USER` ohne Interface-Einschränkung eingesetzt, kann bereits das Antwortpaket des TCP-Aufbaus verworfen werden. Ein synthetischer Tupelvergleich bestätigte, dass die veröffentlichte Regel sowohl die fremde Anfrage als auch die Antwort an den erlaubten Proxy trifft. Es wurde keine Firewall geändert.

**Korrektur.** Die DROP-Regel auf die ursprüngliche Richtung beschränken, etwa mit `--ctdir ORIGINAL`. Den dokumentierten Test mit dem erlaubten Proxy, einem zweiten Host und dem Antwortverkehr ausführen. [iptables-extensions – Conntrack-Richtung](https://man7.org/linux/man-pages/man8/iptables-extensions.8.html) · [Docker – ursprünglicher Zielendpunkt und Reihenfolge der Filterketten](https://docs.docker.com/engine/network/firewall-iptables/#match-the-original-ip-and-ports-for-requests)

## Hinweise

### H1 – Portwiederholung funktioniert bei zwei automatisch gewählten Ports

`tools/update/update-check.sh:141–164` wiederholt erkannte Bindefehler bis zu dreimal und aktualisiert URL sowie alle vorhandenen Laufkonfigurationen. Wenn nur einer der beiden Ports fest vorgegeben ist, schaltet die Bedingung die Wiederholung für beide Ports ab. Ein Konflikt auf dem weiterhin automatisch gewählten Port bleibt dann ein Sofortabbruch. Den frei gewählten Port separat behandeln, falls die gemischte Konfiguration ebenfalls wiederholen soll. Der beschriftete Zwischencontainer ist von der Abschlussaktion auffindbar.

### H2 – Die Vorprüfung und der Start verwenden bei mehrzeiligen Eingaben unterschiedliche Grenzen

`deploy/check-images.sh:18–25` prüft mit zeilenweisem `grep -Eqx`. Eine Eingabe mit einer gültigen Digest-Zeile und einer weiteren Zeile besteht die gemeinsame Vorprüfung. Java und der Bash-Einstieg des Sicherungsimages prüfen jeweils den ganzen Wert. Der feste Repository-Name mit `@` verhindert, dass ein Tag an die Stelle des Digests des gezogenen oder gestarteten Images tritt. Für eine einheitliche Diagnose sollte auch `images` mehrzeilige Werte ausdrücklich ablehnen.

### H3 – Die vollständig gelesenen Metadaten schließen die frühere Pipeline-Lücke

`tools/update/update-check.sh:674–693` prüft Docker-Rückgabewerte und nichtleere Dateien, bevor es nach synthetischen Werten sucht. Ein frühes Ende von `grep -q` kann dadurch den Erfolg von `docker image inspect` und `docker history` nicht mehr beeinflussen. Der Nachweis gilt für Konfiguration, Umgebung und Historie in dieser Probe. Schichten und Dateien im Image gehören nicht zu dieser Prüfung.

## Einzelbewertung von TASK-137

| Bezug | Stand und Nachweis |
|---|---|
| F1 | **Teilweise.** Der neue manuelle Lauf nutzt `GITHUB_SHA` aus `main`, bindet den Checkout genau daran und führt vor der Freigabe keinen Code des eingegebenen Tags aus. Mit dieser Workflow-Datei wird `check` auf einem anderen Ref übersprungen und `release` läuft nicht. Die Git-Gegenproben für spätere Integration sowie bewegliche und unbekannte Referenzen sind grün. Historische Push-Trigger und spätere Tag-Änderung siehe N1/N2. |
| F2 | **In der Anleitung umgesetzt.** REST-Anlage mit `actor_type: User`, Kontrolle des einzigen Kontos und Gegenprobe mit zweitem schreibberechtigtem Konto sind beschrieben. Die Release-Umgebung ist auf den Branch `main` ausgelegt. Einrichtung und Nachweis in GitHub bleiben Holgers offener Schritt. |
| F3 | **Behoben.** Für jede Datei werden identischer Filter und dieselbe Zeile zum Zählen und Vergleichen verwendet. Linux-CI belegt beide fehlerhaften Mischformen und akzeptiert zwei richtige Binärzeilen. |
| F4 | **Behoben.** `pg_trgm` bleibt bei seinem ursprünglichen Eigentümer. Der Test enthält das bytegleiche Rollenskript von TASK-136 und migriert bis V0072 mit der Migrationsrolle, inklusive V0033. Der Übergang prüft Besitzer, Nutzung und unzulässige Änderungen durch die Laufzeitrolle. |
| F5 | **Teilweise.** Die ursprünglich fehlenden Platzhalter, Callbacks und Schemaanlage werden nachweislich abgelehnt. Ausnahmen von der wirksamen Konfigurationsprüfung siehe N3. |
| F6 | **Teilweise.** Zähler, Basis, zuletzt geschriebener Punkt und Fehler beim Lesen sind abgedeckt. Ein höher benannter, weiter aufbewahrter Altpunkt bleibt unberücksichtigt, siehe N4. |
| F7 | **Startprüfung belegt.** Beide Einstiege prüfen beide Werte vor der Arbeit. CI belegt die asymmetrischen Fälle mit `--no-deps`. Ein isolierter Pull prüft die Referenz des ausgewählten Images und startet keinen Prozess. Damit gilt die Digestbindung auch beim Pull, während der gemeinsame Abgleich beider Einstellungen einen Aufruf von `images` oder den späteren Start voraussetzt. Die Anleitung macht diesen Unterschied kenntlich. Abweichung für die mehrzeilige Vorprüfung siehe H2 oben. |
| F8 | **Behoben.** In fünf Phasen wurde je ein danach erstellter und beendeter `recurring.files.cleanup` nachgewiesen. Alte Erfolge zählen nicht. Die Dateiprobe bricht bei Fehlern in Suche, Sortierung, Hashing, Dateilesen oder Zählung ab. |
| F9 | **Teilweise.** Projekt- und Labelkontrolle vor dem Löschen sowie falscher und richtiger Projektname sind in der Update-CI nachgewiesen. Die optionale Sicherung und die Reihenfolge der Kontrolle siehe N5. |
| F10 | **Umgesetzt.** Die Caddy-Probe reicht Werte per Umgebungsname weiter und liest die Muster aus privaten Dateien. Fundmeldungen benennen Fall und Feld. Positivprobe und `old`-Gegenprobe stammen aus der Autorübergabe. |
| H1 | **Behoben.** Die Update-CI prüft einen synthetisch gespeicherten Fehler einer unbekannten Migration mit passender Diagnose bei `migrate` und beim Start eines Verwaltungsbefehls. Das Szenario der tatsächlich gescheiterten V0075 mit Rückkehr bleibt getrennt und ist ebenfalls grün. |
| H2 | **Weitgehend behoben.** Der Zwischencontainer trägt das Compose-Projektlabel. Für automatisch gewählte Ports gibt es Wiederholungen. Der gemischte Fall ist oben unter H1 beschrieben. |
| H3 | **Behoben.** Metadaten werden vor der Suche vollständig gelesen und auf den Rückgabewert geprüft. Siehe H3 oben zum Umfang. |
| H4 | **Teilweise.** Ursprüngliche Zieladresse und ursprünglicher Port sind in der Regel enthalten. Die Rückrichtung des erlaubten Proxy-Verkehrs bleibt offen, siehe N6. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| PR und Quellen | 27 geänderte Pfade geprüft. 1.022 Dateiblobs des Heads lokal gegen ihre GitHub-Blob-SHAs verifiziert. Head und Prüfmerge haben denselben Dateibaum. Keine nachgestellten Leerzeichen in hinzugefügten Diff-Zeilen, keine neue Datenbankmigration. |
| Repository | `scripts/validate_repository.py` und `scripts/task_board.py --check` lokal grün. Neun Tests zur Updatebasis, drei zur Frische von Aufträgen und drei Pakettests lokal grün. Die statische Workflow-Prüfung bestand ihre Assertions. Ihr Testklassen-Abbau stieß lokal auf ein Windows-Zugriffsproblem im temporären Git-Verzeichnis. Linux-CI bestätigt 88 grüne Repositorytests und die Whitespace-Prüfung. |
| Java und PostgreSQL | Maven mit Java 25 kompilierte die getrennte Prüfkopie. Vier datenbankfreie Methoden aus `SchemaGuardSettingsTest` und drei aus `OperationsStartImagesTest` waren lokal grün. Separate Java-Gegenprobe bestätigt N3. GitHub-CI mit PostgreSQL 18.6 bestätigt 775 grüne Backend-Tests, darunter 13 für Migration, vier für Rollen, fünf für Flyway-Einstellungen und drei für Imageangaben. Der Lebenszyklustest war grün. |
| Synthetische Gegenproben | Lokales Git für die Änderung der Tag-Zuordnung. Auswertung eines synthetischen Altzustands der Sicherung für N4 und der Conntrack-Prädikate mit Testadressen für N6. Die Gegenproben veränderten weder Repository-Einstellungen noch eine Firewall. |
| Update und Restore | CI-Protokolle am identischen Dateibaum ausgewertet. Update V0069 → V0073 mit 911 Zeilen, 51 Tabellen und drei Dateien. Fünf aktuelle Dateibereinigungsaufträge, falsche Imageangaben, sichere Löschblock-Gegenprobe, synthetischer `FUTURE_FAILED` und Rückkehr nach echter V0075-Fehlmigration sind belegt. Die Restore-Übung war grün. |
| Grenzen | Lokal waren Docker-Daemon und Linux-Shell nicht verfügbar. Eigene Caddy-Ausführung, ein realer Zwei-Rechner-Firewalltest und der Lauf über private Vorgänger-Images stehen aus. Der Release-Workflow und die produktive Anlage des Regelsatzes wurden nicht ausgelöst. |

- [Repository-CI](https://github.com/1uedev/task-manager/actions/runs/45666311476) · [Backend und Lebenszyklus](https://github.com/1uedev/task-manager/actions/runs/45666311902)
- [Update](https://github.com/1uedev/task-manager/actions/runs/45666311416) · [Restore](https://github.com/1uedev/task-manager/actions/runs/45666311199)

## Abnahmekriterien

| Kriterium | Technischer Stand |
|---|---|
| 1. F1 bis F10 und H1 bis H4 nach den Regeln behoben, mit Gegenproben | **Offen.** N1–N6. Die GitHub-Einrichtung durch Holger ist weiterhin ausdrücklich offen. |
| 2. Backend, Lebenszyklus, Update und Restore grün | **Erfüllt** für den geprüften Dateibaum und die in CI verwendete feste Updatebasis. |
| 3. Handbücher, TODO und DEC-170 entsprechen dem Code | **Teilweise erfüllt.** Die Aussagen über Tag-Trigger, die Ausnahmen der Flyway-Prüfung, den Übergang der Sicherungsnamen und die Anleitungen zu Archivierung und Proxy sind zu präzisieren oder durch die beschriebenen Korrekturen zu erfüllen. |

Die technische Empfehlung lautet **nachbessern**. Die fachliche Abnahme liegt bei Holger.
