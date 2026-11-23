# Prüfbericht zu TASK-131 / PR #345

**Technische Empfehlung: nachbessern.** Es bestehen elf Befunde vor der Abnahme. Sie betreffen vor allem die Bestätigung vollständiger Punkte, die Grenze der Datenbankrücksicherung, die gemeinsame Sicherung von Datenbank und Dateien, den Erfolg der Rücksicherung und die Startsperre. Die grüne CI belegt den dort durchlaufenen Ablauf, deckt diese Fehlerfälle aber nicht ab. Dieser Bericht nimmt TASK-131 nicht ab; die Entscheidung trifft Holger.

**Prüfstand:** 23. November 2026. Sitzung codex-20261123-task131-review-01. Repository 1uedev/task-manager, PR #345, Branch claude/project-thread-elfpvq, Ergebnis-Head 4451342665e84bb07be924ff5332e5dfcfd1495d, PR-Basis 924bd63278805030088cd1bced5503936466ea9b. Der PR ist inzwischen gemergt. Geprüft wurden seine 39 geänderten Dateien und die zum Verständnis nötigen Aufrufwege, nicht spätere Änderungen auf main. Alle Zeilenangaben beziehen sich auf diesen Ergebnis-Head.

**Maßstab:** AGENTS.md, docs/DEVELOPMENT_REVIEW.md, der vollständig gelesene Eintrag TASK-131 mit den zwölf bestätigten Regeln, fünf Abnahmekriterien und der Übergabe, DEC-161 sowie docs/OPERATIONS.md, insbesondere O06 bis O10. Produktcode, vorhandene Tests und Aufgabenstatus wurden nicht geändert. Prüfprogramme liegen außerhalb des Quellbaums.

## Befunde vor der Abnahme

### F1 – Befund, hoch – Der Dauermodus kann fehlgeschlagene Sicherungen als vollständig melden

- **Ort:** deploy/backup/backup.sh:29, :57, :95, :97, :119, :129; deploy/backup/restore.sh:30.
- **Beobachtung:** Der Dauermodus ruft `run_once || log ...` auf. Bash unterdrückt in diesem Kontext errexit auch innerhalb der aufgerufenen Funktionen. Ein fehlgeschlagenes pg_basebackup, eine fehlgeschlagene SQL-Abfrage oder eine fehlgeschlagene restic-Pipeline beendet den Ablauf deshalb nicht zuverlässig. Nach einer leeren Snapshot-ID liefert `fail` lediglich Status 1; danach werden trotzdem latest-point und die Meldung „complete as snapshot …“ geschrieben. Auch ein explizites `return 1` aus make_point wird in run_once durch den anschließenden Erfolg von forget_old verdeckt.
- **Zusätzlicher Fehler:** restic kann bei Lesefehlern mit Status 3 einen unvollständigen Snapshot speichern. Er trägt hier bereits die Tags point und point=.... `restore.sh points` berücksichtigt alle so markierten Snapshots, unabhängig vom Exitstatus des Sicherungslaufs oder von latest-point. Das betrifft auch once, obwohl dort der Shellprozess bei der fehlgeschlagenen Pipeline endet.
- **Wirkung:** Fehlgeschlagene oder unvollständige Sicherungen können als neuester Punkt angeboten beziehungsweise im Dauermodus ausdrücklich als vollständig gemeldet werden. Regeln 6 und 7 sowie Abnahmekriterium 1/O06 sind damit nicht erfüllt. Die Probe benutzt ausschließlich once und erfasst den Unterschied nicht.
- **Vorschlag:** Jeden fehlerkritischen Aufruf ausdrücklich auswerten und sofort aus der jeweiligen Funktion zurückkehren. Einen Fehler aus make_point bis zum Aufrufer erhalten. Snapshots zunächst als Kandidaten anlegen und erst nach restic-Status 0 und geprüfter Beschreibung als vollständigen Punkt kennzeichnen. Fehler beim Erstellen der Basis, SQL-Fehler, Uploadfehler und restic-Status 3 im tatsächlichen Dauermodus prüfen; dabei auch die anschließend angebotene Punktliste kontrollieren.
- **Nachweisart:** Statische Prüfung der Shellkontrollflüsse; restic-Verhalten anhand der Dokumentation von Version 0.18.1. Kein lokaler Bash-Gegenlauf möglich.

### F2 – Befund, hoch – Die gespeicherte Schreibposition ist weder genau T noch immer ein erreichbares Wiederherstellungsziel

- **Ort:** deploy/backup/backup.sh:47, :52, :57, :81, :82; deploy/backup/restore.sh:88.
- **Beobachtung:** T und `pg_current_wal_lsn()` stammen aus zwei getrennten Verbindungen/Abfragen. Ein zwischen beiden Abfragen bestätigter Schreibvorgang liegt nach T und wird dennoch bis zur späteren Schreibposition wiederhergestellt. Darüber hinaus ist die zurückgegebene aktuelle Schreibposition eine Grenze des geschriebenen WAL, kein ausdrücklich erzeugter Wiederherstellungsdatensatz. `recovery_target_inclusive` bleibt auf seiner Vorgabe on: PostgreSQL prüft bei einem LSN-Ziel nach dem Anwenden eines Datensatzes, ob dessen Anfangsposition mindestens dem Ziel entspricht. Dadurch kann auch der nächste Datensatz nach der gelesenen Grenze angewendet werden, etwa der Commit einer schon vorher begonnenen Transaktion.
- **Erreichbarkeit:** Nach einem Segmentwechsel kann ohne weitere Schreibvorgänge die Position am Anfang eines noch leeren Segments liegen. wait_for_archive akzeptiert nach 120 Sekunden ausdrücklich den Fall „Position unverändert“, obwohl das Segment fehlt. Damit ist aber kein auswertbarer WAL-Datensatz am oder nach dem inklusiven Ziel bewiesen. Die Rücksicherung kann dann mit „recovery ended before configured recovery target was reached“ enden. Die durchgehend schreibende Probe erzwingt diesen Ruhefall nicht.
- **Wirkung:** Regel 6 und Abnahmekriterium 2 (Datenbank genau bis T) sind nicht erfüllt. Ein als vollständig angebotener Punkt kann außerdem erst beim Datenbankstart scheitern.
- **Vorschlag:** Einen tatsächlich geschriebenen, eindeutig benannten Wiederherstellungspunkt mit festgelegter Zeitsemantik erzeugen und dessen vollständige Archivierung beweisen, oder ein gleichwertiges Verfahren für eine exakt definierte Grenze verwenden. T nicht unabhängig von dieser Grenze behaupten. Die Fälle „Commit zwischen den Abfragen“, „Transaktion vor der Grenze begonnen, danach bestätigt“ und „weiterer Punkt ohne neue Schreibvorgänge“ mit echtem PostgreSQL 18 prüfen.
- **Nachweisart:** Statische Ablaufprüfung und Abgleich mit dem PostgreSQL-18-Code von recoveryStopsAfter, pg_walfile_name und der dokumentierten inklusiven Zielsemantik; kein lokal ausgeführter PITR-Lauf.

### F3 – Befund, hoch – Eine vorhandene Archivdatei beweist keine abgeschlossene WAL-Archivierung

- **Ort:** deploy/backup/backup.sh:52; tools/restore/compose.restore.yaml:18; docs/BACKUP_RESTORE.md:55.
- **Beobachtung:** Das Archivkommando kopiert direkt auf den endgültigen Dateinamen. Die Sicherung prüft ausschließlich `-f`. Während der Kopie ist dieser Name bereits sichtbar. Bei einer abgebrochenen Kopie, beispielsweise wegen Platzmangels, kann außerdem eine unvollständige Datei liegen bleiben. Der nächste Archivierungsversuch wird durch `test ! -f ...` blockiert, während der Sicherungsdienst die vorhandene Datei bereits akzeptiert. Größe, abgeschlossener Archivierungserfolg und eine durchgehende WAL-Kette ab der Basis werden vor dem Punkt nicht geprüft.
- **Wirkung:** restic kann die lesbaren, aber unvollständigen WAL-Bytes fehlerfrei sichern. Sein Erfolg beweist in diesem Fall nicht die Wiederherstellbarkeit. O06/O08 können somit einen scheinbar vollständigen Punkt erhalten.
- **Vorschlag:** WAL unter einem temporären Namen schreiben, vollständig und dauerhaft abschließen und erst dann atomar veröffentlichen. Vorhandene, unvollständige Dateien dürfen die Wiederholung nicht dauerhaft verhindern. Zusätzlich die vollständig archivierte Zielposition und die benötigte Kette prüfen. Eine verlangsamte sowie eine abgebrochene Archivkopie als Fehlerfälle aufnehmen.
- **Nachweisart:** Statische Prüfung; die derzeitige Probe beschädigt stattdessen die Referenz auf eine Basissicherung.

### F4 – Befund, hoch – Löschen nach T kann einen Punkt mit fehlenden Inhalten erzeugen

- **Ort:** deploy/backup/backup.sh:69, :81, :84, :95; backend/src/main/resources/db/migration/V0070__operations_restore.sql:6. Relevanter bestehender Aufrufweg: backend/src/main/java/taskmanager/files/internal/AttachmentCleanup.java:90.
- **Beobachtung:** Die Inhaltsliste wird erst nach T und der Archivwartezeit aus dem dann aktuellen Datenbankstand gelesen. Ein zu T noch nicht bereinigter Anhang kann anschließend vom regulären Aufräumlauf gelöscht und mit purged_at markiert werden. Die spätere Sicht enthält ihn dann nicht mehr; auch im Dateispeicher fehlt er. Der Punkt besteht die Prüfung, obwohl die auf T zurückgesetzte Datenbank ihn wieder nennt. Ebenso fehlt zwischen Inhaltsprüfung und restic-Lesen eine Sperre gegen die physische Bereinigung.
- **Wirkung:** „Dateien nach der Datenbank lesen“ reicht bei physischer Löschung nicht für die behauptete Konsistenz aus. Regel 6, O06 und Abnahmekriterien 1/2 werden verletzt. Die spätere SHA-Prüfung kann den Schaden feststellen, verhindert aber den zuvor veröffentlichten falschen Punkt nicht.
- **Vorschlag:** Die zu T benötigte Inhaltsmenge festhalten und deren physische Inhalte bis zum abgeschlossenen Snapshot gegen Löschen schützen, beispielsweise mit einem vereinbarten Sicherungs-/Bereinigungsprotokoll oder einem konsistenten Dateisnapshot. Eine Bereinigung gezielt nach T beziehungsweise nach der Inhaltsprüfung anhalten und den Punkt anschließend tatsächlich zurückspielen.
- **Nachweisart:** Statische Prüfung eines durch den bestehenden Bereinigungscode möglichen Ablaufs; keine Änderungen am Bereinigungscode für diese Prüfung.

### F5 – Befund, hoch – Der Rückfall endet vor der entscheidenden Wiederherstellungsprüfung

- **Ort:** deploy/backup/restore.sh:58, :59, :65, :72, :85, :95, :117.
- **Beobachtung:** restore_point meldet Erfolg, sobald die Verzeichnisse ausgepackt, einige Metadaten gefunden und die Konfigurationszeilen geschrieben wurden. PostgreSQL wurde dann noch nicht gestartet. Eine fehlende WAL-Datei in der Mitte, ein unerreichbares Ziel aus F2 oder eine beschädigte, aber von restic korrekt gespeicherte Datenbank werden erst später erkannt; dann ist die Schleife zum älteren Punkt bereits mit Status 0 verlassen.
- **Weitere Lücken:** `found >= contents` prüft nur eine Dateianzahl. Eine zusätzliche, unreferenzierte Datei kann eine fehlende referenzierte Datei zahlenmäßig ausgleichen. Identitäten, Größen und Prüfsummen werden hier nicht mit dem wiederhergestellten Datenbankstand verglichen. Außerdem läuft restore_point als if-Bedingung; damit unterbricht set -e die Funktion bei ungesicherten Fehlern aus mv, cp, dem Schreiben der Konfiguration, touch oder chmod nicht zuverlässig. Die abschließenden Logmeldungen können den Fehler verdecken.
- **Wirkung:** O08/Abnahmekriterium 3 sind nur für die ausdrücklich abgefangenen Fehler erfüllt. Ein unbrauchbarer neuer Punkt kann als erfolgreich zurückgespielt gelten; ein brauchbarer älterer wird nicht mehr versucht. Die Probe löscht ausschließlich den Basissnapshot und trifft damit einen bereits ausdrücklich behandelten Fehler.
- **Vorschlag:** Dateisystemoperationen ausdrücklich prüfen. Den Erfolg erst bestätigen, wenn eine isolierte PostgreSQL-Instanz das vereinbarte Ziel erreicht hat und die referenzierten Dateien validiert sind. Bei einem Fehler diesen Kandidaten verwerfen und den älteren prüfen. Mindestens eine fehlende mittlere WAL-Datei, einen defekten Basisinhalt, eine ersetzte referenzierte Datei bei unveränderter Gesamtzahl und einen Schreib-/Verschiebefehler prüfen.
- **Nachweisart:** Statische Prüfung. Dass restic selbst beschädigte verschlüsselte Blöcke beim Lesen erkennen kann, schließt diese logisch unvollständigen, korrekt gespeicherten Bestände nicht aus.

### F6 – Befund, hoch – Ein gesetzter Prozessparameter umgeht die Startsperre

- **Ort:** backend/src/main/java/taskmanager/operations/internal/RestoreGate.java:14; backend/src/main/java/taskmanager/TaskManagerApplication.java:42.
- **Beobachtung:** `taskmanager.process=command` entfernt den Gate-Bean vollständig. Dieser Parameter schaltet im normalen Einstieg aber nicht den Webanwendungstyp auf NONE. Ein Aufruf ohne echten Containerbefehl, etwa `java -jar app.jar --taskmanager.process=command`, folgt weiter dem normalen SpringApplication.run. Die HTTP-Komponenten bleiben vorhanden, während die Vormerkung nicht gelesen wird. Auch die Worker-Voreinstellung aus dem Builder ist durch eine höher priorisierte Konfiguration überschreibbar.
- **Wirkung:** Eine noch nicht freigegebene Installation kann über HTTP erreichbar werden; alte Sitzungen können gültig bleiben. Die Sperre aus Regel 10 ist keine verlässliche Startbedingung. Im üblichen Modus ist ein Lesefehler der Vormerkung dagegen nicht still als „keine Vormerkung“ behandelt: die Exception verhindert den Start.
- **Gegenprobe:** Ein separater Spring-Kontext mit unverändertem Gate und einer vorgemerkten Rücksicherung scheiterte ohne Parameter wie erwartet. Mit `taskmanager.process=command` startete derselbe Kontext und enthielt keine Sperre: blocked=true gegenüber blocked=false. Der vollständige HTTP-Start wurde wegen der lokalen Socketbeschränkung nicht ausgeführt; der verbleibende Webpfad ist am Einstieg geprüft.
- **Vorschlag:** Den tatsächlichen, validierten Startbefehl und Anwendungstyp aneinander binden. Nur ausdrücklich zulässige Prozesse ohne Webserver dürfen die Sperre auslassen. Unzulässige Kombinationen ablehnen; Webstart und Worker mit gesetztem Parameter sowie einen Fehler beim Lesen der Vormerkung dauerhaft testen.

### F7 – Befund, mittel – Die Freigabeschritte und der gemeinsame Audit sind nicht atomar

- **Ort:** backend/src/main/java/taskmanager/operations/internal/RestoreCommandRunner.java:82, :106, :124, :125; backend/src/main/java/taskmanager/operations/internal/RestoreRepository.java:24.
- **Beobachtung:** run hat keine übergreifende Transaktion. Sitzungsende, Anhalten der Antworten und Löschen der Vorschläge bestätigen jeweils ihre eigene Transaktion; Audit und Löschen der Vormerkung sind nochmals getrennt. Ein Fehler beim Inhaltslesen lässt die ersten Änderungen bestehen, aber ohne gemeinsamen Audit. Eine Wiederholung zählt dann für diese Schritte null. Ein Abbruch nach dem Audit und vor dem Löschen hinterlässt bereits restore.released, obwohl die Vormerkung noch sperrt. Das Lesen reserviert die Vormerkung auch nicht gegen einen zweiten gleichzeitigen Befehl.
- **Wirkung:** Die Vormerkung bleibt bei einem frühen Fehler zwar erhalten (das ist positiv), aber es gibt weder eine atomare Freigabe noch einen vollständigen, verlässlich einmaligen Nachweis ihrer Änderungen. Abbruch und Wiederholung können einen irreführenden Audit erzeugen.
- **Gegenprobe:** Der unveränderte Runner wurde mit über Spring-Transaktionsinterzeptoren angebundenen Testports ausgeführt. Beim injizierten Fehler der Inhaltsprüfung waren drei Transaktionen bereits bestätigt, keine zurückgerollt, kein gemeinsamer Audit geschrieben; die Vormerkung blieb bestehen und der Runner setzte Code 1. Diese Probe verwendet einen zählenden Transaktionsmanager, keine PostgreSQL-Datenbank.
- **Vorschlag:** Die Datenbankänderungen, Audit und Freigabe gemeinsam transaktional und unter exklusiver Reservierung der Vormerkung ausführen. Falls bewusst in Etappen gearbeitet werden soll, braucht es stattdessen einen dauerhaften, wiederaufnehmbaren Schrittstand einschließlich der ursprünglichen Zähler. Erfolgsbericht erst nach bestätigtem Abschluss ausgeben. Fehler und Prozessabbruch zwischen den Schritten sowie zwei gleichzeitige Aufrufe prüfen.

### F8 – Befund, mittel – Dem Sicherungsimage fehlt der SSH-Client für das bestätigte SFTP-Ziel

- **Ort:** deploy/backup/Dockerfile:5, :6; docs/BACKUP_RESTORE.md:80.
- **Beobachtung:** Das endgültige PostgreSQL-Image erhält nur die restic-Binärdatei. Es wird kein SSH-Client installiert oder aus der ersten Stufe übernommen. Die PostgreSQL-18.6-Imagebeschreibung installiert ihn ebenfalls nicht. restic 0.18.1 startet für sein SFTP-Backend ausdrücklich einen externen ssh-Prozess; der Client ist nicht in restic eingebaut. Die Anleitung nennt zudem keinen vollständigen Weg für Schlüssel/Agent und Hostschlüsselvertrauen unter dem Dienstkonto.
- **Wirkung:** Das nach Regel 8 unterstützte SFTP-Ziel ist mit dem gelieferten Image nicht nutzbar. Die lokale Verzeichnisprobe prüft es nicht.
- **Vorschlag:** Den benötigten Client liefern und die sichere Übergabe der Authentisierung sowie das Hostschlüsselvertrauen dokumentieren. Eine synthetische SFTP-Gegenstelle im Test verwenden und Sicherung plus Rücksicherung damit nachweisen.
- **Nachweisart:** Abgleich mit der offiziellen PostgreSQL-18.6-Imagebeschreibung und dem restic-0.18.1-SFTP-Quellcode. Kein lokaler Image-/SFTP-Lauf.

### F9 – Befund, hoch – Die Anleitung lässt sich mit dem gelieferten Image nicht wie beschrieben ausführen

- **Ort:** docs/BACKUP_RESTORE.md:83, :100, :124, :125, :128; deploy/backup/Dockerfile:11, :12. Gegenbeispiel der Probe: tools/restore/restore-check.sh:64 und :66.
- **Beobachtung:** Die Schritte 2 und 3 verwenden `docker compose run --rm backup /opt/backup/restore.sh ...`. Wegen des unveränderten Entrypoints wird daraus jedoch ein Aufruf von backup.sh mit dem Modus /opt/backup/restore.sh; er endet bei „usage“. Die Probe verwendet dafür korrekt `--entrypoint /opt/backup/restore.sh`.
- **Weitere fehlende Voraussetzungen:** Das Einrichtungsbeispiel legt für das Konto postgres weder die Schreibrechte des frischen backup-state-Volumes noch die des WAL-Archivs fest. Die Probe erledigt beides ausdrücklich als root. Das Beispiel bindet außerdem keinen dauerhaften Rücksicherungsbereich ein; ein nur im mit --rm gestarteten Container erzeugtes Ergebnis wäre anschließend verloren. Schritt 6 nennt den Skriptaufruf ohne Containerkontext und ohne die notwendige Verbindung zur gerade wiederhergestellten Datenbank.
- **Wirkung:** Ein Betreiber erreicht über die neun Schritte weder zuverlässig einen laufenden Sicherungsdienst noch die Vormerkung und Freigabe der zurückgespielten Installation. Die Eigentumsübergabe der Dateien in Schritt 4 ist richtig, behebt diese vorherigen Voraussetzungen aber nicht. Abnahmekriterium 5 ist nicht erfüllt.
- **Vorschlag:** Die benötigten Speicherbereiche und Besitzer einmal vollständig angeben und alle neun Schritte mit den wirklichen Entrypoints, Mounts und Zielverbindungen ausführen. Dafür genügt eine Ergänzung der Sicherungsanleitung; die TLS-/Betriebsvorlage aus TASK-132 muss nicht vorgezogen werden. Genau die abgedruckten Befehle in einer frischen Umgebung prüfen.

### F10 – Befund, mittel – Die Probe kann wesentliche Wiederherstellungsprüfungen ohne entsprechende Daten bestehen

- **Ort:** tools/restore/restore-check.sh:75, :170, :178, :180, :183; tools/restore/compose.restore.yaml:125.
- **Beobachtung:** Der Seed und der Schreiber erzeugen für diese Probe keine offenen SMTP-Antworten, KI-Vorschläge oder dauerhaft hochgeladenen Anhänge/Wikibilder. Die Prüfungen „keine pending/sending-Antworten“ und „keine Vorschläge“ sind damit auch ohne die betreffenden Freigabeschritte erfüllt. Eine wiederhergestellte Worker-Instanz wird nicht gestartet; restored-app hat den Ausführer ausdrücklich abgeschaltet. Ein tatsächlich wiederaufgenommener Versandauftrag wird nicht geprüft.
- **Weitere Lücke:** Nach der Freigabe werden Anmeldung, eine Ticketliste und ein Suchergebnis geprüft. Projekt öffnen, Ticket ändern und Anhang herunterladen aus Schritt 9 fehlen. Für die Schreiblast wird nur die höchste wiedergefundene Ticketnummer geprüft, nicht die Übereinstimmung der protokollierten Schreibvorgänge bis zur festgelegten Grenze oder das Fehlen von Änderungen nach T.
- **Wirkung:** Die Probe kann trotz fehlender Dateiübernahme, nicht funktionierender Versandunterdrückung oder falscher Zielgrenze grün werden. Die sechs Backendtests liefern nützliche synthetische Einzelprüfungen, ersetzen aber diesen durch Regel 12 und Abnahmekriterium 2 verlangten vollständigen Ausfallweg nicht.
- **Vorschlag:** Vor dem Punkt tatsächlich Dateien und Bilder mit bekannten Prüfsummen, Antworten in beiden offenen Zuständen mit ihren Aufträgen, interne und Portalsitzungen sowie einen KI-Lauf mit Vorschlag erzeugen. Ihre Existenz vor der Sicherung belegen. Nach der Rücksicherung den Worker mit einer synthetischen Mailgegenstelle starten und nachweisen, dass nichts erneut gesendet wird. Kernabläufe einschließlich Änderung/Download sowie Daten vor und nach T prüfen.
- **Nachweisart:** Statische Verfolgung des aufgerufenen Seeds und der Prüfungen; CI-Log des vollständigen vorhandenen Laufs ausgewertet.

### F11 – Befund, mittel – Datenverlust und Wiederanlaufzeit werden an unstabilen beziehungsweise zu frühen Grenzen gemessen

- **Ort:** tools/restore/restore-check.sh:116, :117, :118, :186, :193, :211.
- **Beobachtung:** Letzter Schreibzeitpunkt und letzte Ticketnummer werden mit zwei getrennten tail-Aufrufen gelesen, während der Schreiber noch läuft. Bis kill und wait können weitere bestätigte Zeilen folgen; sogar Zeitpunkt und Nummer können aus unterschiedlichen Zeilen stammen. usable_at wird direkt nach Readiness festgehalten, noch vor Anmeldung, Ticketliste und Suche. Die anschließenden Fachprüfungen zählen folglich nicht zur gemeldeten Wiederanlaufzeit.
- **Wirkung:** Der Bericht kann den letzten bestätigten Schreibvorgang zu früh ansetzen und beide Zielwerte zu günstig ausweisen. Falls die Rücksicherung eine später protokollierte Zeile enthält, kann sogar ein negativer Verlust entstehen; die reine Obergrenzenprüfung würde ihn akzeptieren. Die vorhandenen Messwerte sind deshalb kein genauer Nachweis der dokumentierten Definitionen.
- **Vorschlag:** Den Schreiber zuerst anhalten und abwarten, dann Zeitpunkt und Nummer atomar aus derselben letzten Zeile des stabilen Protokolls lesen. Wiederanlaufzeit erst nach den erfolgreichen Kernprüfungen beenden. Nichtnegative Zeitdifferenzen und konsistente Schreibnummern ausdrücklich prüfen. Den Bericht aus den so festgehaltenen Grenzen erzeugen.

## Hinweise

### H1 – Hinweis – Die geänderten Modulzugänge fehlen teilweise im Workflowfilter

- **Ort:** .github/workflows/restore.yml:9, :17.
- **Beobachtung und Wirkung:** Änderungen ausschließlich an TaskManagerApplication, ReplyRestore, SessionRestore, SuggestionRestore, ContentCheck oder den Rechtezugängen starten den Restore-Workflow nicht. Der aktuelle PR wurde durch seine anderen Änderungen erfasst; später kann eine relevante Regression erst beim Wochenlauf auffallen.
- **Vorschlag:** Die tatsächlich benötigten Integrationsdateien beziehungsweise Module und die Anleitung in die Filter aufnehmen. `always()` beim Artefakt und das Weiterreichen eines fehlgeschlagenen Probelaufs sind richtig vorhanden. Ein zusätzlicher Cleanup-Schritt mit `if: always()` würde auch einen hart abgebrochenen Shelllauf ergänzen; der vorhandene EXIT-Trap deckt den normalen Fehlerweg ab.

### H2 – Hinweis – Einmalpasswort der synthetischen Probe steht im Prozessargument

- **Ort:** tools/restore/restore-check.sh:75; außerdem :194.
- **Beobachtung und Wirkung:** Das generierte Einmalpasswort wird über `--one-time-password` an den Seed übergeben; beim Login enthält auch das curl-Datenargument das aus der Umgebung gelesene Demopasswort. Es wurden keine echten Zugangsdaten im Diff gefunden, und die Probe verwendet ausdrücklich wegwerfbare synthetische Werte. Die strenge Vorgabe „nur über die Umgebung“ ist an diesen Prozessgrenzen dennoch nicht eingehalten; Argumente können über Prozess-/Containerdiagnostik sichtbar sein.
- **Vorschlag:** Auch das Einmalpasswort über eine explizite Umgebungsvariable oder eine geeignete nicht protokollierte Eingabe übergeben; den HTTP-Body ohne Passwort im Argument aufbauen. Diese synthetischen Werte ebenfalls aus veröffentlichten Diagnosepaketen ausschließen.

### H3 – Hinweis – Die lokale WAL-Ablage wächst ohne Begrenzung weiter

- **Ort:** deploy/backup/backup.sh:95, :104.
- **Beobachtung und Wirkung:** `forget --prune` entfernt alte restic-Stände, aber keine Dateien aus dem lokalen WAL-Archiv. Jeder neue Punkt referenziert das gesamte weiterhin wachsende Archiv erneut. Die alten WAL-Daten bleiben damit auch im neuesten Snapshot enthalten. Eine mindestens 14 Tage lange Wiederherstellbarkeit ist dadurch nicht verkürzt, aber der Speicherbedarf und die Scanzeit wachsen über dieses Fenster hinaus.
- **Vorschlag:** Die Löschgrenze aus sämtlichen noch benötigten Basen/Punkten ableiten und eine sichere Archivbereinigung festlegen, oder den unbegrenzten Zwischenbedarf ausdrücklich dokumentieren. Ein Löschverfahren darf die durchgehende WAL-Kette alter, noch aufbewahrter Punkte nicht verletzen.

## Positiv geprüfte Teile und fachliche Grenzen

- V0070 nennt in der Sicht alle nicht physisch bereinigten Anhänge und alle Bilder; ContentCheck verwendet derzeit dieselbe Auswahl. Es liest in Seiten von 500 über die letzte UUID und hasht die Inhalte mit einem begrenzten Puffer über Größe und SHA-256. Fehlende und unlesbare Inhalte werden als Kennungen, nicht als Dateitext ausgegeben. Der gewöhnliche Ablauf vermeidet OFFSET-Seiten; ein Nachweis mit sehr großem Bestand wurde hier nicht ausgeführt. Die doppelte Formulierung der Sicht sollte bei künftigen Änderungen zusammengeführt oder durch einen Vergleichstest abgesichert werden.
- ReplyRestore erfasst ausdrücklich pending und sending, setzt uncertain/`helpdesk.restored`, erhöht die Version und schreibt Fachaudit. Der bestehende ReplySender versendet eine so angehaltene Antwort beim späteren Auftrag nicht erneut. Das ersetzt nicht den fehlenden vollständigen Worker-Nachweis aus F10.
- Interne und Portalkonten verwenden dieselbe identity_session-Tabelle. Die neue Aktualisierung schränkt weder Kontoart noch Kontostatus ein und erfasst alle noch nicht widerrufenen Sitzungen.
- Nur ai_suggestion wird gelöscht; ai_run bleibt bestehen. Das entspricht Regel 10.
- companyRightHolders berücksichtigt genau die Aktionsregeln ohne Projekt, ohne Projektregeln oder Geheimnisse hinzuzufügen. Inaktive Inhaber werden zusätzlich mit „nicht aktiv“ gekennzeichnet; aktive Loginnamen kommen aus dem Identitätsmodul.
- helpdesk.restored ist im Vertrag, in den daraus erzeugten Typkommentaren und in beiden Oberflächensprachen ergänzt. Der Zustand uncertain und seine bestehenden Bedienwege sind bereits vorhanden.
- Die erwarteten Codes 0, 3 und 2 sind im normalen Befehlsweg passend zugeordnet; unerwartete Laufzeitfehler führen zum Fehlerpfad 1. Code 3 entfernt ebenfalls die Vormerkung. Das ist in Anleitung und Tests ausdrücklich beschrieben; der Betreiber muss vor dem anschließenden Start über den gemeldeten unvollständigen Dateibestand entscheiden.
- Es wurden im geprüften Diff keine echten Passwörter, privaten Schlüssel, realen Kundendaten oder produktiven Zielhosts festgestellt. Containerpfade sind keine lokalen Betreiberpfade. Takt und Grenzwerte sind konsistent dokumentiert; Warnung/kritisch werden zutreffend erst TASK-133 zugeordnet.
- Die Abgrenzung der kleinen Messung gegenüber TASK-134 und dem Nachweis vor einer Kundenzusage ist richtig dokumentiert. Dieser Bericht erhebt die kleine Probe nicht zum Nachweis für den vollen Referenzbestand.

## Ausgeführte Prüfungen und Nachweise

### Unabhängig in dieser Sitzung

| Prüfung | Ergebnis und Grenze |
|---|---|
| PR-Metadaten, vollständiger Diff, Anforderungen und Aufrufwege | Alle 39 geänderten Dateien geprüft; Head und Basis festgehalten. Lokale Quelldateien gegen die 963 Blobs des Head-Trees abgeglichen; lediglich Windows-Zeilenenden zweier vorhandener .cmd-Dateien mussten für den Vergleich normalisiert werden. |
| python scripts/validate_repository.py | Erfolgreich. |
| python scripts/task_board.py --check | Erfolgreich. |
| python -m unittest discover -s tests -v | Gestartet: 50 Tests, zunächst 10 Fehlschläge, 13 Fehler und 1 Unix-spezifisch übersprungener Test. Die Umgebung verweigert lokale Socketverbindungen; weitere Fehler betrafen die Windows-Vorgabekodierung. Kein erfolgreicher Gesamtlauf behauptet. |
| Gezielte Wiederholung mit PYTHONUTF8=1 | test_repository_contract.py: 14 Tests erfolgreich; test_task_board.py: 11 Tests erfolgreich. Die netzabhängigen Tests wurden dadurch nicht nachgewiesen. |
| Maven offline, Java 25.0.4.1, PostgreSQL 18.6 nativ | Haupt- und Testquellen erfolgreich kompiliert. Ausgewählte 15 Tests: 12 Fehler wegen verweigerter Socketverbindungen; 2 Modulstrukturtests und die Befehlsargumentprüfung erfolgreich. Dieser Lauf belegt keine PostgreSQL-Integration. |
| Zusätzliche Spring-Gegenprobe für F6 | Unverändertes Gate mit vorgemerktem Punkt: Standardmodus blockiert; Parameter command entfernt die Sperre. Kein echter HTTP-Server gestartet. |
| Zusätzliche Transaktions-Gegenprobe für F7 | Unveränderter Runner, echte Spring-Transaktionsinterzeptoren mit zählendem Testmanager: injizierter Inhaltsfehler nach 3 Commits, 0 Rollbacks, ohne gemeinsamen Audit; Vormerkung bleibt. Keine Datenbanktransaktion simulativ als echte PostgreSQL-Prüfung ausgegeben. |
| Leerzeichenprüfung des vollständigen PR-Diffs | Keine nachgestellten Leerzeichen in den hinzugefügten Zeilen. Ein lokaler Git-Clone scheiterte an der fehlenden Namensauflösung; der Stand wurde über den GitHub-Zugang und den Tree-Abgleich bereitgestellt. |
| Vollständige Restore-Probe, lifecycle-check, Frontend verify | Lokal nicht wiederholt: Docker-Daemon/Bash-Laufzeit nicht verfügbar und lokale Netzwerkzugriffe gesperrt. Die zugehörigen CI-Nachweise wurden getrennt gelesen. |

Die Shellfehler, die genaue PITR-Grenze und die gleichzeitige Dateibereinigung sind in diesem Bericht als statisch hergeleitete Befunde gekennzeichnet. Es werden dafür keine lokal ausgeführten Docker-, Bash- oder PITR-Tests behauptet.

### Gelesene CI-Nachweise des geprüften PRs

Die Logs nennen ausdrücklich den Prüf-Merge 49764e5203e870e1b1c9ce8430637b746310f82c als Merge des Ergebnis-Heads 4451342665e84bb07be924ff5332e5dfcfd1495d in die oben genannte Basis.

- Restore-Lauf 45160860754, Job 137645626224: erfolgreich. Gemeldet werden Linux x86_64, 4 vCPU, 34 bestätigte Schreibvorgänge, 10 Sekunden Datenverlust und 90 Sekunden Wiederanlauf; Rückfall vom dritten auf den zweiten Punkt. Das sind die CI-Werte, getrennt von den 11/105 Sekunden des Autors. Sie unterliegen F10/F11.
- Backend-Lauf 45160860601, Job 137645625808: Log ausgewertet; 736 Tests, 0 Fehler/Fehlschläge, 0 übersprungen; darunter sechs AfterRestoreCommandTest. Auch „Lifecycle check passed“ einschließlich after-restore ist enthalten.
- Repository checks 45160860779: Job und Schritte erfolgreich, einschließlich Vertragsprüfung, Aufgabenübersicht, Tests und Whitespace.
- Frontend 45160860730: Job und Schritte einschließlich Typen/Tests/Build sowie Browserprüfungen erfolgreich; nicht als eigener lokaler Frontendlauf ausgegeben.

Die außerhalb des Repositories genannten Autorenprotokolle wurden nicht bereitgestellt und nicht als selbst gelesener Nachweis behandelt. Die öffentlich zugänglichen CI-Logs liefern den oben getrennt benannten Nachweis.

### Ergänzend verwendete Primärquellen

- Bash: The Set Builtin: Ausnahmen von errexit bei Funktionen in bedingten Aufrufen.
- restic 0.18.1: Backup und Exitstatus: Status 3 kann einen unvollständigen Snapshot hinterlassen.
- PostgreSQL 18: Recovery Target und Recovery-Code: inklusive LSN-Grenze und Abbruch bei nicht erreichtem Ziel.
- PostgreSQL 18: WAL-Funktionen: Segmentzuordnung und WAL-Wechsel.
- Offizielles PostgreSQL-18.6-Image und restic-0.18.1-SFTP-Backend: Paketbestand und externer SSH-Prozess. Das Fehlen des Clients im Endimage ist aus diesen Bauanweisungen hergeleitet, nicht lokal durch einen Containeraufruf gemessen.

## Empfehlung und nächste Nachprüfung

Vor einer Abnahme F1 bis F11 beheben oder Holger ausdrücklich je Befund über eine Abweichung entscheiden lassen. Vorrang haben F1 bis F6 sowie die ausführbare Wiederanlaufanleitung F9. Danach die korrigierten Fehlerfälle im Dauermodus und im vollständigen Restore-Workflow mit echtem PostgreSQL 18 nachweisen; die bisher grüne Probe allein genügt dafür nicht.

TASK-131 bleibt aus dieser Prüfung fachlich unabgenommen. Dieser Bericht ändert weder den Aufgabenstatus noch den Code und veröffentlicht keine GitHub-Abnahme.
