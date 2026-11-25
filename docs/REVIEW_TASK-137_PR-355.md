# Prüfbericht zu TASK-137 / PR #355

**Technische Empfehlung – nachbessern.** Der Update-Lauf belegt den Wechsel von V0069 auf V0073 und die Rückkehr von einem tatsächlich fehlgeschlagenen Versionswechsel. Offen bleiben insbesondere die Vertrauensbasis der Tag-Prüfung, die Prüfsummenprüfung von Release-Dateien und der Eigentümerwechsel einer bereits unter TASK-136 eingerichteten Datenbank.

**Prüfstand** [PR #355](https://github.com/1uedev/task-manager/pull/355), Branch `claude/project-thread-elfpvq`, Head `35e0fabd2e0c2919789f932b45bb6198437bbc4c`, Basis `93e6b78d2f4a9ad82f18baf5673e4fa84f1bebe3`, GitHub-Prüfmerge `52431560ce032dfe7868607c37b8ca6e544a1284`. Merge auf `main` am 25. November 2026: `48bbbcad65a28b50a6ce50ecc6c7c35ecdfc421f`. Head, Prüfmerge und Merge haben denselben Dateibaum. Stand 25. November 2026. Maßstab sind AGENTS.md, docs/DEVELOPMENT_REVIEW.md, TASK-137 mit Regeln 1–13, DEC-167, der [Bericht zu TASK-136](REVIEW_TASK-136_PR-350.md) und O01/O11. Die Zeilenangaben beziehen sich auf den PR-Head.

## Befunde vor der Abnahme

### F1 – Hoch – Der Tag-Stand liefert seinen eigenen Prüfschritt (N6)

**Ort.** `.github/workflows/release.yml:14–20, :35–54, :56–82`, `tools/release/check-tag.sh:13–24`, `docs/INSTALL.md:148–154`.

**Beobachtung.** `check` hat Leserechte und läuft vor `release`. `release` verwendet den ausgegebenen Commit und prüft nach der Freigabe erneut, ob das Tag auf diesen Commit zeigt. Beim Tag-Push stammen jedoch Workflow und `check-tag.sh` aus dem zu prüfenden Stand. Dessen Herkunft von `main` ist zum Zeitpunkt der Skriptausführung noch ungeprüft. Der spätere Vergleich von Tag und Checkout prüft die Herkunft nicht erneut. Außerdem holt `check` den dann aktuellen Stand von `main`. Ein zunächst außerhalb von `main` liegender Commit kann zwischen Tag-Push und Runner-Start aufgenommen werden und die Prüfung passieren. Die Anleitung verspricht für diesen Fall eine Ablehnung.

**Korrektur.** Die Herkunft mit einem Prüfablauf aus einem bereits vertrauenswürdigen Stand feststellen und erst danach Code des Release-Kandidaten ausführen. Den Referenzzeitpunkt konkret festlegen und dokumentieren. Falls der Push-Zeitpunkt maßgeblich sein soll, einen daran gebundenen Hauptbranch-Stand prüfen. Die Gegenprobe soll einen später integrierten Commit und einen abweichenden Prüfstand abdecken. [GitHub – Ereignis `push`](https://docs.github.com/en/webhooks/webhook-events-and-payloads#push)

### F2 – Mittel – Der beschriebene Weg zur persönlichen Tag-Ausnahme ist unvollständig (N6)

**Ort.** `docs/INSTALL.md:148–154`.

**Beobachtung.** Die Anleitung benennt Holgers Konto als alleinigen Reviewer und deaktiviert den administrativen Bypass der Umgebung. Für den Tag-Regelsatz beschreibt sie ausschließlich den Einstellungsdialog. Die GitHub-Dokumentation für diesen Dialog nennt Rollen, Teams und Apps. Die REST-Dokumentation für Repository-Regelsätze führt inzwischen auch den Typ `User` auf. Der dokumentierte Klickpfad belegt daher die verlangte persönliche Ausnahme nicht. Mit aktiver Erstellungssperre und ohne wirksame Ausnahme wäre das Setzen des Tags gesperrt.

**Korrektur.** Den unterstützten Weg für die persönliche Ausnahme konkret dokumentieren, einschließlich der Kontrolle des eingetragenen Kontos und der Ablehnung eines anderen Kontos. Die Einrichtung und ihre Bestätigung bleiben Holgers offener Schritt. [GitHub – Einstellungen für Regelsätze](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository#granting-bypass-permissions-for-your-branch-or-tag-ruleset) · [GitHub – REST-Schema für Repository-Regelsätze](https://docs.github.com/en/rest/repos/rules#create-a-repository-ruleset)

### F3 – Hoch – Eine gezählte Prüfsummenzeile kann aus der Prüfung herausfallen (N8)

**Ort.** `tools/update/release-files.sh:23–28`, `tests/test_update_release_files.py:40–78`.

**Beobachtung.** Die erste Auswahl zählt die üblichen SHA-256-Zeilen für Text- und Binärmodus. Die zweite Auswahl übernimmt nur Zeilen ohne das Binärkennzeichen `*` in die Datei für `sha256sum --check`. Eine gültige Textzeile für die Digest-Datei und eine falsche Binärzeile für das Paket ergeben jeweils die geforderte Anzahl eins. Danach wird nur die Digest-Datei geprüft und das Paket entpackt. Die eigene Gegenprobe hat beide Auswahlregeln mit einem synthetischen Paket nachvollzogen. Die gefilterte Prüfliste enthielt ausschließlich die gültige Digest-Zeile, obwohl die mitgelieferte Paketprüfsumme falsch war.

**Korrektur.** Pro Datei genau die gezählte Zeile prüfen. Text- und Binärmodus durchgehend gleich behandeln oder Binärzeilen ausdrücklich ablehnen. Beide Kombinationen mit fehlerhafter Prüfsumme testen.

### F4 – Mittel – Ein V0072-Bestand kann `pg_trgm` bei der Migrationsrolle behalten (N3)

**Ort.** `deploy/db-roles.sql:5–7, :41–55, :90–100`, `backend/src/test/java/taskmanager/DatabaseRolesTest.java:104–112, :152–195`, `docs/INSTALL.md:53–64`.

**Beobachtung.** Die Neuinstallation legt die Erweiterung als Verwaltungskonto an. Im getesteten Übergang von V0071 gehört sie bereits diesem Konto. Die Vorlage von TASK-136 legte sie bei einer Neuinstallation dagegen über V0033 als `taskmanager_migrate` an. `CREATE EXTENSION IF NOT EXISTS` behält diesen Eigentümer beim Übergang auf TASK-137. Eigene PostgreSQL-18.6-Gegenprobe mit dem Rollenskript von TASK-136, der Erweiterungsanlage wie in V0033 und danach dem Rollenskript aus PR #355: Das Schema wechselte von `pg_database_owner` zu `taskmanager_migrate`. Die Erweiterung blieb bei `taskmanager_migrate`. Die Betriebsanleitung beschreibt das Eigentum an der Erweiterung weder für die Neuinstallation noch für diesen Übergang.

**Korrektur.** Die Eigentümerregel für den V0072-Ausgangsstand festlegen und dokumentieren. Wenn die Erweiterung dabei bewusst ihren Besitzer behält, den tatsächlich unterstützten Zustand samt Funktionsrechten prüfen. Den Übergang aus TASK-136 als eigenen Fall ergänzen.

### F5 – Mittel – Der Vergleich der gebundenen Flyway-Konfiguration bleibt unvollständig (N2)

**Ort.** `backend/src/main/java/taskmanager/operations/internal/SchemaGuard.java:155–189, :211–246`, `backend/src/test/java/taskmanager/operations/internal/SchemaGuardSettingsTest.java:44–99`.

**Beobachtung.** Der Klassenpfad wird jetzt exakt verglichen. Geänderte und indexierte Pfade, ausgeschaltetes Flyway und ein leer aufgelöster Satz sind geprüft. Der Vergleich der fertigen `Configuration` lässt unter anderem Platzhalterwerte, das Abschalten von Standard-Callbacks und die automatische Schemaanlage aus. Eine eigene Java-Gegenprobe mit Flyway 12.4.0 änderte diese drei Werte in der gebundenen Konfiguration. `refuseChangedConfiguration` akzeptierte sie. Die vorgelagerte Prüfung lehnt die entsprechenden Namen in aufzählbaren Spring-Quellen ab. Die Gegenprobe belegt die Lücke im zweiten Vergleich und keinen Umgehungsweg über gewöhnliche Umgebungsvariablen oder Profile.

**Korrektur.** Die für Migration und Prüfung relevanten wirksamen Werte vollständig festlegen. Eine programmatische Änderung an Platzhaltern oder Callback-Auflösung muss dieselbe Ablehnung auslösen wie die entsprechende Spring-Einstellung.

### F6 – Mittel – Das Namensregister übernimmt den letzten Namen eines vorhandenen Volumes nicht (N4)

**Ort.** `deploy/backup/backup.sh:26–43, :49–51, :159–162, :216–241`, `tests/test_backup_names.py:28–44`.

**Beobachtung.** Mit initialisierter Zustandsdatei vergibt die Sperre steigende Namen, auch bei gleicher Sekunde und zurückgestellter Uhr. Beim ersten Lauf mit einem vorhandenen Volume aus TASK-136 fehlt `last-name-epoch`. Die Funktion setzt den letzten Namen dann auf null und berücksichtigt weder `base-name` noch `latest-point`. Liegt die Uhr bei oder vor einer noch vorhandenen alten Kennung, kann diese wiederverwendet werden. Die gleiche Rücksetzung ist bei einer defekten Zustandsdatei ausdrücklich getestet. Damit ist die zugesagte Eindeutigkeit über den Wechsel der Vorlage hinweg offen.

**Korrektur.** Einen vorhandenen Namensstand beim Übergang übernehmen oder ein Format verwenden, das bisherige Kennungen eindeutig ausschließt. Einen gültigen Altbestand mit eingefrorener oder zurückgestellter Uhr prüfen. Lesefehler der Zustandsdatei müssen zum Abbruch führen oder eine belegbar eindeutige Wiederaufnahme auslösen.

### F7 – Mittel – Die gemeinsame Vorprüfung läuft bei Einzelbefehlen nicht (N7)

**Ort.** `deploy/compose.yaml:16–20, :46–59, :82–88, :189–195`, `tools/update/update-check.sh:296–320`, `docs/BACKUP_RESTORE.md:139–150`.

**Beobachtung.** Der feste Name mit `@` schließt Tags für das jeweilige Anwendungs- oder Sicherungsimage aus. Die CI belegt das für `pull`, `up --no-deps`, `migrate` und die Sicherungsbefehle mit lokal vorhandenen Tags. Der gemeinsame Dienst `images` wird von `pull` und `run --no-deps` nicht ausgeführt. Der dokumentierte Wiederanlauf nutzt beide Formen unmittelbar. Der Digest des tatsächlich benutzten Images ist festgelegt, während der gemeinsame Abgleich beider Werte einschließlich alter Einstellungen ausbleibt. Die Gegenprobe setzt beide Werte zugleich auf Tags und deckt die asymmetrischen Fälle nicht ab.

**Korrektur.** Den gemeinsamen Abgleich auch für die dokumentierten Einzelbefehle vorsehen oder die gleichwertige Prüfinvariante in Regel 7 ausdrücklich festlegen. Je einen fehlerhaften Wert bei gültigem zweitem Digest in den unterstützten Wegen prüfen.

### F8 – Mittel – Die Updateprobe kann alte Aufträge als Erfolg werten und einen Dateilesefehler übergehen (N9)

**Ort.** `tools/update/probe.py:113–125`, `tools/update/update-check.sh:222–244, :372–386, :549–559`, `backend/src/main/java/taskmanager/jobs/internal/JobRepository.java:216–228`.

**Beobachtung.** Die Suche prüft das konkrete Ticket. Die Jobprobe kombiniert einen aktuellen oder kürzlich gesehenen Ausführer mit irgendeinem erfolgreichen wiederkehrenden Auftrag. Sie prüft weder Auftrag noch Abschlusszeit gegen den Beginn der Phase. Ein neuer Ausführer, der sich meldet und dessen Aufträge alle scheitern, kann mit einem alten erfolgreichen Auftrag aus der vorherigen Datenbank oder dem rückgesicherten Punkt bestehen. Bei der Dateiprobe wird der Bytezähler in einer Befehlsersetzung als Argument von `printf` berechnet. Ein Fehler der zweiten `find`-/`cat`-Pipeline geht nicht in den Rückgabewert von `printf` ein. Die erste Prüfsummen-Pipeline fängt den Fehler ab, wenn er dort bereits auftritt. Ein späterer Lesefehler kann dennoch ohne Abbruch in die Zusammenfassung eingehen.

**Korrektur.** Pro Phase einen definierten Auftrag frisch erfolgreich abschließen lassen oder dessen erwartete Fachwirkung neu belegen. Jede Dateilesepipeline separat auf Erfolg prüfen. Eine Gegenprobe mit einem vorhandenen alten Erfolg und einem fehlerhaften aktuellen Ausführer sowie eine mit einem fehlgeschlagenen Leseschritt ergänzen.

### F9 – Mittel – Der kopierbare Rückkehrblock löscht vor der Bestätigung (N10)

**Ort.** `docs/INSTALL.md:118–132`, `tools/update/update-check.sh:518–545`.

**Beobachtung.** Die Updateprobe prüft den Projektnamen und das Compose-Label beider Volumes vor dem Löschen und setzt die Rückkehr erst nach einem belegten Punkt fort. Die Anleitung fordert dieselbe Kontrolle im Text. Ihr Codeblock gibt Projektname und Volumes aus und löscht anschließend direkt. Wer den Block als Einheit einfügt, hat vor dem Löschbefehl keine Bestätigungsmöglichkeit. Bei einem falschen Projekt mit gleichnamigen Volumes kann der Block dessen Daten entfernen.

**Korrektur.** Kontroll- und Löschbefehle getrennt zeigen oder den Löschbefehl durch Prüfungen gegen den erwarteten Projektnamen und die Labels beider Volumes sperren. Die Sicherungsoption vor dem Löschschritt nennen.

### F10 – Mittel – Die Caddy-Probe übergibt ihre Suchmuster als Prozessargumente (N12)

**Ort.** `tools/caddy/check-log.sh:74–77, :95–102`, `tools/update/update-check.sh:119–124, :330–349, :561–571`, `tools/restore/restore-check.sh:183–202`.

**Beobachtung.** Die Update- und Restore-Proben reichen ihre Passwortwerte über Dateien beziehungsweise Umgebung weiter. CI belegt den synthetischen Rollenfehler bei eingeschalteter SQL-Protokollierung. Die Caddy-Probe übergibt ihre synthetischen Rückrufwerte, Cookie- und Referrer-Marker dagegen über `docker run -e NAME=WERT` und als Muster an `grep`. Beide Aufrufe enthalten die Werte in der Argumentliste. Beim Fehler schreibt die Probe zudem die passenden Zeilen mit den synthetischen Werten aus.

**Korrektur.** Die Variablen per Namen aus der Umgebung übernehmen und die Muster per geschützter Datei an die Suche geben. Für die Fehlermeldung genügen der betroffene Fall und der gefundene Feldtyp.

## Hinweise

### H1 – Die Diagnose einer unbekannten fehlgeschlagenen Migration ist im Backend geprüft

`SchemaGuard.java:273–279` behandelt `FUTURE_FAILED` vor `FUTURE_SUCCESS`. `MigrateCommandTest.java:410–423` erwartet die spezifische Diagnose. Die Updateprobe erwartet dagegen ausschließlich `newer than this image` (`update-check.sh:500–515`). Das ist für ihre echte, transaktional zurückgerollte V0075 korrekt. Regel 13 fordert die eigene Diagnose zusätzlich in der Updateprobe. Dafür eignet sich ein getrenntes synthetisches Szenario mit gespeichertem Fehler einer dem alten Image unbekannten Migration. Den echten Rückkehrtest beibehalten.

### H2 – Abschlussaktion erfasst Compose-Netze, die Portwahl bleibt vorläufig

`.github/workflows/update.yml:121–132` entfernt anhand der Laufkennung Compose-Container, Volumes, Netzwerke und die drei temporären Image-Tags. Die regulären Update-Build-Tags aus den Schritten für Basis und Ziel bleiben bis zum Ende des Runners bestehen. Der Zwischencontainer für das Kopieren des JARs (`tools/update/update-check.sh:454–456`) trägt kein Compose- oder Lauf-Label. Bei Abbruch zwischen `create` und `rm` kann ihn die Abschlussaktion nicht zuordnen. Die Portsuche (`:56–63`) schließt den Socket vor dem Compose-Start. Das vermeidet feste Portkonflikte, reserviert den Port aber nicht gegen einen Parallelstart. Automatischer Wiederholungsversuch oder verbindlich zugewiesene Ports würden die Restlücke schließen.

### H3 – Die Reichweite der Imageprüfung ist benannt

`tools/update/update-check.sh:561–571` prüft Konfiguration, Umgebung und Historie gegen die synthetischen Laufwerte. Dateien und frühere Build-Geheimnisse sind ausdrücklich außerhalb dieser Prüfung. Bei großen Ausgaben kann `grep -q` die Pipe beim ersten Treffer schließen. Mit `pipefail` kann der dadurch fehlgeschlagene Produzent einen Treffer als fehlgeschlagene Prüfung erscheinen lassen. Die Metadaten vollständig erfassen, Lesefehler getrennt bewerten und anschließend in den erfassten Daten suchen.

### H4 – Die Firewall-Regel gilt für den internen Zielport

Der Zugriffstest von zwei Rechnern ist in `docs/INSTALL.md:70–74` konkret beschrieben. Der `DOCKER-USER`-Beispielbefehl trifft nach DNAT auf Containerport 8080. Auf einem gemeinsam genutzten Host kann er damit auch andere veröffentlichte Dienste mit demselben internen Port betreffen. Bei solchen Installationen sollte das Beispiel zusätzlich den ursprünglichen Zielendpunkt oder das betroffene Netz eingrenzen. [Docker – iptables und ursprünglicher Zielendpunkt](https://docs.docker.com/engine/network/firewall-iptables/#match-the-original-ip-and-ports-for-requests)

## Einzelbewertung

| Bezug | Stand und Nachweis |
|---|---|
| N1 | **Behoben.** Compose setzt `operations` als festes JVM-Argument. Web, Ausführer und Verwaltungsbefehle registrieren dadurch den Marker. Nur der ausdrücklich gewählte Befehl `migrate` migriert. Höher priorisierte Kommandozeile und Systemeigenschaft sowie neuere Historie sind in `MigrateCommandTest` geprüft. Umgebung, Profil und alternative Schreibweise können den registrierten Einstieg nicht entfernen. |
| N2 | **Teilweise.** Exakter Pfad, Listenbindung, Profil, Umgebung, fehlende Flyway-Autokonfiguration und leerer Migrationssatz sind abgedeckt. Wirksame Konfiguration siehe F5. |
| N3 | **Teilweise.** V0073 und das Rollenskript begrenzen die Laufzeitrolle auf SELECT der technischen Tabellen und entziehen beide Sperrfunktionen. Die Linux-CI deckt Neustart, Reset, Neuaufbau der Suche und Auftragsverarbeitung unter der Laufzeitrolle sowie den Übergang ab V0071 mit Folgen, Partition, Typen, Sichten und Funktion ab. Eigentümer beim Übergang aus TASK-136 siehe F4. |
| N4 | **Teilweise.** Die beiden Handbücher und die Probe verwenden denselben manuellen Aufruf über den laufenden Dienst. Der Zähler für neue Namen ist getestet. Vorhandene Zustände siehe F6. |
| N5 | **Im Code umgesetzt.** Caddy filtert Anfragefelder im Standard- und im Access-Log sowie `Set-Cookie` und `Location` im Access-Log. Das Skript prüft 200, Upstream-302, HTTP-Weiterleitung und 502 und durchsucht stdout und stderr zusammen. Die positive und die alte negative Ausführung stammen aus der Autorübergabe. Die Argumentübergabe ist unter F10 erfasst. |
| N6 | **Teilweise.** Vorgelagerter Job mit Leserechten, `needs: check`, geschützte Umgebung und Checkout des ausgegebenen Commits sind vorhanden. Vertrauensbasis und Einrichtung siehe F1/F2. |
| N7 | **Teilweise.** Feste Registry-Namen mit Digest und Verweigerung der lokal vorhandenen Tags sind in CI belegt. Gemeinsamer Abgleich der Eingaben siehe F7. |
| N8 | **Teilweise.** Die Auswahl verwendet den festen Stand nur ohne stabile Release-Kandidaten und liefert bei fehlendem geeignetem Vorgänger 3. Der Anmeldeabschnitt zieht beide Alt-Images. Das Update prüft deren lokale Verfügbarkeit und zieht sie nicht erneut explizit. Der JSON-Bericht enthält Tag oder Basis-Commit, Ziel und Images ohne Runner-Pfad. Die letzte Konsolenausgabe enthält den absoluten Berichtspfad. Prüfsummen siehe F3. Der Pfad mit privaten Release-Images wurde in CI übersprungen. |
| N9 | **Teilweise.** 911 Zeilen in 51 Tabellen und drei Dateien mit 23.871 Bytes wurden unverändert gefunden. Technische Ausschlüsse und Pflichttabellen sind benannt. Die verlorene Mailvorlage wurde erkannt. Suche nach Projekt, Ticketnummer und neuem Titel ist belegt. Auftragswirkung und Lesefehler siehe F8. |
| N10 | **Teilweise.** Vor dem Wechsel ist ein Punkt belegt. V0074 wurde bestätigt, V0075 scheiterte tatsächlich. Das alte Image verweigerte die neuere Datenbank. Paket und `.env` der Vorgängerversion stellten den Punkt wieder her. Die Anleitung enthält `up -d --wait db`, Rückgabewert 3 und Rechteabgleich. Im erfolgreichen Rückkehrlauf ergab `after-restore` 0. Löschschritt siehe F9. |
| N11 | **Behoben.** Entfernte Proxys erhalten zwei Wege zur Quellenbegrenzung und einen Zugriffstest von zwei Rechnern. Die Paketprüfung läuft im Release-Workflow und wurde in Repository-CI mit intaktem Paket sowie fehlender Mount-Datei geprüft. Die Restore-Probe verweist auf den vollständigen Quellstand am Tag. Ein echter Zwei-Rechner-Zugriffstest war nicht Teil der ausgeführten Prüfungen. |
| N12 | **Teilweise.** Geschützte Musterdatei, synthetischer Rollenfehler und Passwortübergabe im Restore sind belegt. Caddy-Argumente und Prüfschärfe siehe F10 und H3. |
| H1 aus TASK-136 | **Teilweise.** Eigene Diagnose und Backend-Gegenprobe vorhanden. Update-Gegenprobe offen, siehe H1 oben. |
| H3 aus TASK-136 | **Weitgehend behoben.** Netze und zugehörige Testimages werden bereinigt. Hostports werden pro Lauf gewählt und sind überschreibbar. Restlücken bei Abbruch und Parallelstart siehe H2 oben. |
| H5 aus TASK-136 | **Behoben.** Init-Mount und beide Rollen-Mounts stimmen mit der Vorlage überein. Das Handbuch nennt vier getrennte Datenbankpasswörter und die zum Punkt gehörende `.env`. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| PR und Quellen | 38 geänderte Pfade gegen die Regeln geprüft. Alle 1.017 Dateiblobs des Head lokal gegen die GitHub-Blob-SHAs verifiziert. Identischer Dateibaum beim Prüfmerge. Keine nachgestellten Leerzeichen in hinzugefügten Diff-Zeilen. |
| Repository | `scripts/validate_repository.py` und `scripts/task_board.py --check` lokal erfolgreich. Alle neun `ChooseBaseTest` lokal grün. Die lokale Gesamtsuite mit 77 Tests war unter Windows wegen der fehlenden oder nicht ausführbaren Shell-Werkzeuge sowie Zugriffs- und Socket-Beschränkungen nicht grün. Repository-CI bestätigt 77 erfolgreiche Linux-Tests und die Whitespace-Prüfung. |
| Java | Maven mit Java 25 kompilierte Backend und Tests. Drei datenbankfreie Methoden aus `SchemaGuardSettingsTest` lokal grün. Separate Java-Gegenprobe für geänderte Werte der gebundenen Flyway-Konfiguration reproduzierte F5. In GitHub liefen 770 Backend-Tests mit PostgreSQL 18.6 ohne Fehler, Ausfälle oder übersprungene Tests. Die separate Lebenszyklusprüfung war grün. |
| PostgreSQL und Release-Dateien | PostgreSQL 18.6 im Einzelbenutzermodus bestätigte F4 in einer isolierten synthetischen Datenbank. In einer separat erzeugten synthetischen Release-Lieferung wurde die unterschiedliche Auswahl der beiden Prüfsummenfilter für F3 nachvollzogen. Das Original-Shellskript lief für diesen Zusatzfall lokal nicht. |
| Update und Restore | CI-Protokolle am identischen Dateibaum ausgewertet. Update V0069 → V0073 mit Inhaltsvergleich, Rollenfehler, falschen Tags, Verlustprobe und echter Rückkehr V0074 → V0073 grün. Restore-Übung grün. Der lokale Docker-Daemon war nicht erreichbar. Caddy `old`, die tatsächliche Release-Ausführung und der authentifizierte Abruf eines Vorgänger-Releases wurden nicht unabhängig ausgeführt. |

- [Backend und Lebenszyklus](https://github.com/1uedev/task-manager/actions/runs/45283214891) · [Repository-Prüfungen](https://github.com/1uedev/task-manager/actions/runs/45283214854)
- [Update](https://github.com/1uedev/task-manager/actions/runs/45283214841) · [Restore](https://github.com/1uedev/task-manager/actions/runs/45283214944)

## Abnahmekriterien

| Kriterium aus TASK-137 | Technischer Stand |
|---|---|
| 1. Einzelbefunde und Hinweise nach den Regeln behoben, mit Gegenproben | **Offen.** F1–F10 sowie die fehlende Update-Gegenprobe aus H1. Die Einrichtung der Release-Umgebung und des Tag-Regelsatzes ist weiterhin Holgers Aufgabe. |
| 2. Backend, Lebenszyklus, Update und Restore grün | **Erfüllt** für den geprüften Dateibaum mit fester V0069-Basis. |
| 3. Handbücher, TODO und DEC-167 stimmen mit dem Code überein | **Teilweise erfüllt.** Abweichungen betreffen unter anderem den Zeitpunkt der Tag-Prüfung, den V0072-Eigentümerwechsel, die gemeinsame Digestprüfung und den kopierbaren Löschschritt. |

Die technische Empfehlung lautet **nachbessern**. Die fachliche Abnahme liegt bei Holger.
