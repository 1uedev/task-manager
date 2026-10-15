# Docker-Betrieb, Sicherung und Leistungsplanung

Stand 15. Oktober 2026 · Planungsentwurf für TASK-010

Dieses Dokument konkretisiert REQ-OPS-001 bis REQ-OPS-006, REQ-SEC-001, REQ-PERF-001 bis REQ-PERF-004 sowie REQ-DATA-003 und REQ-DATA-004. Es ergänzt [Architektur](ARCHITECTURE.md), [Helpdesk](HELPDESK_SLA.md), [Git und Migration](GIT_MIGRATION.md) und [KI-Betriebsgrenzen](AI_USE_CASES.md).

**Bestätigt:** Docker-Auslieferung, überwiegend beim Kunden, PostgreSQL, eine Installation pro Unternehmen, Wartung durch Kunde oder Anbieter, zulässige Internetvoraussetzung und keine feste RAM-Obergrenze. Der Planungsrahmen beträgt bis zu 1 TB SSD für Live-Daten. Holger hat am 15. Oktober für einen vollständigen Serverausfall höchstens eine Stunde Datenverlust und höchstens vier Stunden bis zum Wiederbetrieb als Planungsziel gewählt (DEC-038). Diese Ziele sind noch nicht praktisch nachgewiesen. Alle folgenden Betriebsverfahren, Plattformen, Grenzwerte und Prüfkriterien sind Vorschläge.

## Referenzinstallation

Vorgeschlagen wird eine Linux-VM mit x86-64, Docker Engine und Compose als dokumentierter Einstieg. Ein unterstützter Linux-Host kann lokal oder bei einem Hosting-Anbieter betrieben werden. Das legt keinen Cloud-Anbieter fest. ARM64, Kubernetes und andere Betriebssysteme werden vor einer Zusage separat qualifiziert; ein Docker-Image allein garantiert keine Unterstützung beliebiger Plattformen.

| Bestandteil | Verantwortung und Grenze |
|---|---|
| TLS-Einstieg / Reverse Proxy | HTTPS für Browser und API, begrenzte Anfragen, geprüfte Weiterleitung. Kunde kann einen geeigneten vorhandenen Proxy einsetzen. |
| Webprozess | API, Fachlogik und ausgelieferte React-Oberfläche aus einem versionierten Anwendungsimage. |
| Worker | Separater Prozess aus demselben Image für Mail, SLA und andere Hintergrundaufträge; eigene Ressourcenbegrenzung. |
| PostgreSQL | Eigener Dienst mit persistentem Speicher; keine öffentlich erreichbare Datenbank. Extern betreibbare PostgreSQL-Instanz nach derselben Versions-/Sicherungsprüfung. |
| Dateispeicher | Persistentes Volume für unveränderlich abgelegte Uploads, Wiki-Bilder und verwaltete Dateien; Zugriff über die Anwendung. |
| Sicherung und Überwachung | Vom zu sichernden Host unabhängiges Ziel und externe Erreichbarkeits-/Sicherungsüberwachung. |
| Lokales KI-Modell | Optionaler externer Dienst mit eigenem Ressourcenprofil. Kein Modellserver im Anwendungsimage. |

PostgreSQL und Dateispeicher können zunächst auf demselben Host laufen. Redis, ein eigener Suchserver oder ein Message-Broker sind keine ungeprüften Pflichtdienste; der Architekturvorschlag nutzt zunächst PostgreSQL für dauerhafte Aufträge. Die Wahl eines zusätzlichen Dienstes braucht einen messbaren Bedarf und einen eigenen Sicherungs-/Betriebsvertrag.

Die Auslieferung enthält später Image mit unveränderlicher Versionsreferenz, geprüfte Installationsvorlage, Beispielkonfiguration ohne Geheimnisse, unterstützte Versionsmatrix, Prüfsummen sowie Update- und Wiederherstellungsanleitung. Unterstützte Betriebssystem-, Docker- und PostgreSQL-Versionen werden vor Implementierung anhand ihrer dann aktuellen Wartung festgelegt. Es wird hier weder eine Compose-Datei erstellt noch etwas installiert.

Container laufen ohne privilegierten Modus, ohne eingebundenen Docker-Socket und mit getrennten Dienstkonten. Schreibrechte werden auf erforderliche Daten- und temporäre Bereiche beschränkt. Anwendung und Datenbank verwenden getrennte Laufzeit- und Schemaänderungsrechte. Speicher- und CPU-Grenzen verhindern, dass ein Worker die interaktive Anwendung unbegrenzt verdrängt.

## Persistenz und Dateien

Docker-Volumes existieren unabhängig vom Lebenszyklus eines Containers. Sie schützen dadurch vor Datenverlust beim Austausch des Containers, ersetzen aber keine separate Sicherung gegen Hostausfall oder Löschen. [Docker: Volumes](https://docs.docker.com/engine/storage/volumes/)

Vorschlag: getrennte benannte Volumes beziehungsweise dokumentierte Speicherbereiche für PostgreSQL, Dateien und betriebliche Sicherungszwischenstände. Für den Referenzhost wird lokaler SSD-Speicher mit geprüftem Linux-Dateisystem, etwa ext4 oder XFS, bewertet. Ungeprüfte Netzwerkfreigaben sind keine gleichwertige Referenz. Eine spätere Objektspeicheranbindung bleibt über die Dateischnittstelle möglich.

Dateiannahme erfolgt in einen begrenzten temporären Bereich. Erst nach Größen-/Inhaltsprüfung, Prüfsumme und dauerhaftem Ablegen wird die Datei fachlich referenziert. Änderungen erzeugen neue Dateiversionen; bestehende Inhalte werden nicht in-place überschrieben. Verwaiste Uploads werden kontrolliert entfernt. Berechtigungen gelten auch für Vorschaubilder, Downloadlinks und ältere Revisionen. Ein frei erreichbarer Dateipfad ist kein Berechtigungsnachweis.

Vorgeschlagene Startgrenzen: 25 MB je Datei und 40 MB je eingehender Rohmail, jeweils dezimal. Mailkodierung kann den Rohumfang erhöhen; nutzbare Anhangsgröße hängt zusätzlich vom Mailanbieter ab. Große oder ungeprüfte Dateien werden nicht still verworfen: Bei IMAP bleibt die Originalnachricht bis zur dokumentierten Behandlung erhalten; die Anwendung speichert einen begrenzten Prüfhinweis. Dateiprüfung, Quarantäne und Scanner-Ausfallverhalten müssen vor Pilotbetrieb qualifiziert werden. Erfolgreich importierte Mailanhänge sollen nicht zusätzlich unbegrenzt in Rohmailkopien gespeichert bleiben.

## Netzwege, Geheimnisse und Ausfälle

| Zweck | Vorgeschlagene Freigabe | Verhalten bei Ausfall |
|---|---|---|
| Browser / API | HTTPS am einzigen Anwendungseinstieg; Verwaltungszugang gezielt einschränken | Externe Überwachung meldet Nichterreichbarkeit. |
| PostgreSQL und Dateien | Nur interne Dienstverbindungen und notwendige Dateirechte | Kein erfolgreicher Schreibstatus ohne dauerhafte Speicherung; keine Neustartschleife als Fehlerersatz. |
| IMAP / SMTP | Konfigurierte Server und TLS-Verfahren | Sichtbarer Rückstand; kontrollierte Wiederholung nach Helpdesk-Vertrag. |
| Git | Freigegebene Anbieterinstanzen, Proxy und gegebenenfalls Kunden-CA | Keine vertraulichen Metadaten ohne aktuellen Quellzugriff; übrige Fachseite bleibt nutzbar. |
| LDAP | Gezielt freigegebener interner Verzeichnisdienst | Keine Umgehung der Anmeldung. Lokaler administrativer Wiederherstellungsweg ist gesondert zu definieren. |
| KI | Nur administrativ aktivierte Anbieter/Endpunkte und Datenklassen | Keine automatische Cloud-Ausweichroute; manuelle Facharbeit bleibt möglich. |
| Updates / Images | Freigegebene Registry beziehungsweise geprüfter Übertragungsweg | Laufende Installation arbeitet weiter; Update wird später kontrolliert nachgeholt. |
| DNS / Zeit / Zertifikate | Dokumentierte interne oder externe Infrastruktur | Abweichungen melden; Zeitquelle ist für Fristen und Wiederherstellung zu überwachen. |

Vorschlag: Ein reiner Internetausfall blockiert keine lokal verfügbare Ticket-, Wiki- oder Zeiterfassungsfunktion. Nicht erreichbare Integrationen zeigen ihren Zustand. Lizenzprüfung, Telemetrie oder automatische Inhaltsübertragung sind nicht durch die Internetvorgabe beschlossen. Die vollständige Zielliste und das verbindliche Offline-Verhalten bleiben Teil von OPEN-012.

Passwörter, Schlüssel und Tokens werden geschützt bereitgestellt und nicht in Images, normale Konfigurationsexporte, URLs oder Logs geschrieben. Verschlüsseltes Schlüsselmaterial und dessen Wiederherstellungsweg gehören dennoch in das Sicherungskonzept. Alte Schlüsselversionen dürfen nicht entfernt werden, solange sie für aufbewahrte Sicherungen erforderlich sind. Vollverschlüsselung des Datenträgers, Transportverschlüsselung und Zugriffsschutz der Sicherungen ergänzen einander.

Fernwartung erfolgt über einen vom Kunden freigegebenen, zeitlich begrenzten Zugang mit nachvollziehbarem Bearbeiter. Es gibt keine universellen Herstellerzugänge und keine routinemäßige Kopie der Kundendaten zum Anbieter. Diagnosepakete enthalten standardmäßig Status, Fehlerkennungen und bereinigte Konfiguration, keine Tickets, Mailtexte oder Geheimnisse.

## Sicherungsziel und vollständige Wiederherstellung

**RPO höchstens 1 Stunde** bedeutet: Nach Serververlust darf der jüngste konsistent wiederherstellbare Stand höchstens eine Stunde vor dem Ausfall liegen. **RTO höchstens 4 Stunden** bedeutet hier als Messvorschlag: vom simulierten Ausfallzeitpunkt bis zur geprüften erneuten Nutzbarkeit der vereinbarten Kernabläufe. Erkennung, Bereitstellung, Rücksicherung und Prüfung zählen mit; nur die Kopierzeit zu messen wäre unzureichend.

Für dieses Ziel werden ein erreichbares unabhängiges Sicherungsziel, verfügbare Schlüssel, ausreichend schneller Wiederherstellungsweg, vorbereitete Ersatzkapazität und erreichbares Betriebspersonal benötigt. Ein einzelner Docker-Host bietet keine Hochverfügbarkeit. Ob die vier Stunden rund um die Uhr oder in einer vereinbarten Servicezeit organisatorisch abgesichert werden, ist noch zu entscheiden; ohne entsprechend erreichbares Betriebspersonal darf das Ziel nicht als gewährleistet verkauft werden.

### Vorgeschlagenes Sicherungsverfahren

PostgreSQL-Basissicherungen und fortlaufende Archivierung der Schreibprotokolle ermöglichen zeitpunktbezogene Wiederherstellung. Erforderlich ist eine vollständige Protokollkette seit der Basissicherung; ein logischer Dump ersetzt diese Kette nicht. [PostgreSQL: Continuous Archiving und PITR](https://www.postgresql.org/docs/current/continuous-archiving.html)

Empfohlen wird ein erprobtes Sicherungswerkzeug nach gesonderter Auswahl statt eigener Sicherungslogik als Produktnebenfunktion. Vorgeschlagener Start: tägliche Basissicherung, fortlaufend übertragene Schreibprotokolle und geprüfte gemeinsame Wiederherstellungspunkte spätestens alle 15 Minuten. Ein Punkt gilt erst als vollständig, wenn sämtliche benötigten Daten außerhalb des Produktivhosts vorhanden und überprüft sind:

- passender PostgreSQL-Stand mit benötigter Basissicherung und lückenloser Protokollkette;
- alle zu diesem Datenbankstand referenzierten unveränderlichen Dateien samt Manifest und Prüfsummen;
- zugehörige Anwendungsversion, Schemaversion, Konfiguration und notwendige Schlüsselversionen;
- dokumentierter Wiederherstellungszeitpunkt und erfolgreicher Vollständigkeitsnachweis.

Die technische Zuordnung von Datenbank-Wiederherstellungspunkt und Dateimanifest muss in der Umsetzung präzise festgelegt und getestet werden. Ein beliebiger aktueller Dateiabzug zusammen mit einem älteren Datenbankbackup genügt nicht. Dateien dürfen erst endgültig aus dem Sicherungsbestand entfernt werden, wenn alle noch aufbewahrten Wiederherstellungspunkte ohne sie auskommen. Die Bereinigung des Live-Speichers muss sicherstellen, dass benötigte gesicherte Dateiversionen unabhängig davon erhalten bleiben. Zusätzliche neuere Dateien verleihen nach einer Rücksicherung keine Sichtbarkeit ohne zugehörige berechtigte Datenbankreferenz.

Das Alter des **jüngsten vollständigen gemeinsamen Punkts** wird überwacht: Vorschlag Warnung ab 30 Minuten, kritischer Alarm ab 45 Minuten; über 60 Minuten ist das RPO-Ziel verletzt. Die Uhrzeit des letzten gestarteten Jobs oder der letzten übertragenen WAL-Datei ist kein ausreichender Nachweis. Bei Fehlern wird keine grüne Sicherung gemeldet. Unabhängige Überwachung muss auch den Totalausfall des Servers erkennen.

Sicherungen werden verschlüsselt und vor unbemerkter Veränderung geschützt; das Sicherungsziel erhält getrennte Zugänge und nach Möglichkeit Schutz vor Löschung durch kompromittierte Laufzeitkonten. Vorgeschlagen wird ein Wiederherstellungsfenster von 14 Tagen, einschließlich aller dafür benötigten Basissicherungen, Protokolle, Dateien und Schlüssel. Langzeitarchive, zusätzliche Offline-Kopie, Speicherbedarf und konkrete Löschfristen sind gesondert zu entscheiden. 14 Tage sind kein gesetzlicher Aufbewahrungswert.

### Wiederanlauf nach vollständigem Hostverlust

1. Ausfall feststellen und Zeitpunkt erfassen. Ersatzumgebung mit dokumentierten Versionen, Netzwerk, TLS und Speicherkapazität bereitstellen.
2. Einen vollständigen gemeinsamen Wiederherstellungspunkt wählen; Prüfsummen und Schlüsselzugriff kontrollieren. Datenbank und Dateien zu diesem Stand wiederherstellen.
3. Anwendung zunächst isoliert und ohne ausgehende Worker starten. Schema, Dateien, Anmeldewege und Rechte an synthetischen beziehungsweise freigegebenen Prüfdaten kontrollieren.
4. Alte Sitzungen und API-Zugänge ungültig machen. Seit dem Sicherungsstand bekannte Sperren, Rechteänderungen und Löschungen abgleichen; unklare Konten bleiben gesperrt. Lokale Wiederanmeldung/Kontowiederherstellung muss verhindern, dass zwischenzeitlich ersetzte alte Zugangsdaten erneut gelten. Dafür ist vor Pilotbetrieb ein geprüfter Wiederanlaufprozess festzulegen.
5. Mailzustände und externe Wirkungen abgleichen: Ein zurückgesicherter Versandauftrag könnte außerhalb des Sicherungsstands bereits versandt worden sein. Solche Aufträge zurückhalten und nach dem Helpdesk-Vertrag klären. IMAP-Wiederaufnahme darf weder Nachrichten verlieren noch unkontrolliert doppelte Tickets erzeugen. Veraltete KI-Vorschläge und Bestätigungen verwerfen.
6. Erst nach Prüfung Benutzerzugriff und Hintergrundverarbeitung kontrolliert freigeben. SLA-Rückstände nach ihren tatsächlichen Fristen bewerten; keinen unkontrollierten Stapel historischer Benachrichtigungen versenden. RPO, RTO, Lücken und Entscheidungen dokumentieren.

Ein Restore-Test vor Pilotbetrieb sowie wiederkehrende Tests, vorgeschlagen quartalsweise und nach relevanten Sicherungsänderungen, weisen die Ziele nach. Der Test verwendet eine frische Umgebung und realistische Daten-/Dateimengen. Keine Nachricht darf aus der Testumgebung an echte Kunden gesendet werden.

## Updates und Rückkehr zum vorherigen Stand

Updates werden ausdrücklich geplant, nicht ungefragt aus einem wechselnden Image-Tag installiert. Vorab stehen Versionskompatibilität, Wartungsfenster, Speicherreserve, gesicherter Stand und geprüfter Rückkehrweg fest. Major-Updates von PostgreSQL sind ein eigener Vorgang, kein gewöhnlicher Imageaustausch.

Vorgeschlagene Reihenfolge: neue Version in einer isolierten Wiederherstellung erproben; Benutzer informieren; Schreibzugriffe und relevante Jobs anhalten; Sicherungspunkt abschließen; genau einen Schemaänderungslauf ausführen; Webprozess und Worker passend zur Schemaversion starten; Kernabläufe prüfen; Zugriff wieder freigeben. Verbotene Versionskombinationen werden beim Start erkannt.

Ein älteres Image kann eine Schemaänderung nicht automatisch rückgängig machen. Rückkehr erfolgt nur über einen dokumentierten kompatiblen Pfad oder vollständige Wiederherstellung. Sobald nach dem Update neue Arbeit erfasst wurde, erfordert eine Rücksicherung eine ausdrückliche Entscheidung über deren Erhalt beziehungsweise Abgleich. Für den ersten Updatepfad wird daher ein geschlossenes Wartungsfenster empfohlen; unterbrechungsfreie Updates sind kein bestätigtes V1-Ziel.

## Aufbewahrung und Löschung

Die folgenden Werte sind betriebliche Diskussionsvorschläge, keine gesetzlichen Fristen und keine bereits aktivierten Löschregeln. Vor Kundeneinsatz sind Fachbedarf, Verträge, Datenschutz und Speicherbedarf gemeinsam abzustimmen. Eine Löschung benötigt einen berechtigten Auftrag beziehungsweise eine bewusst aktivierte Regel; ein volles Laufwerk ist keine Freigabe zum Löschen von Kundendaten.

| Datenart | Vorschlag zur Aufbewahrung / Löschung |
|---|---|
| Tickets, Zeitbuchungen, Anforderungen und Wiki | Keine pauschale automatische Löschung aus dieser Planung. Kunde legt Fachfristen fest; Archivierung verbirgt nicht automatisch vor berechtigten Lesern und ist keine Löschung. |
| Zugehörige Dateien / Revisionen | Folgen bestätigten Fachfristen und Referenzen. Physische Löschung erst nach Prüfung benötigter Wiederherstellungspunkte. |
| Erfolgreich verarbeitete Rohmails | 30 Tage als Diagnosevorschlag; extrahierte Fachnachrichten folgen eigener Frist. Originalanhänge nicht unbegrenzt doppelt aufbewahren. |
| Ungeklärte Eingänge / Quarantäne | Keine stille automatische Entsorgung; Erinnerung nach 7 Tagen, verantwortliche Prüfung spätestens nach 30 Tagen. Obergrenzen und weitere Behandlung vor Betrieb entscheiden. |
| Temporäre Uploads / Exporte | Vorschlag 24 Stunden nach Abbruch beziehungsweise Bereitstellung; laufende Aufträge und berechtigte Abrufe berücksichtigen. |
| Unübernommene KI-Vorschläge | Vorschlag 24 Stunden; angenommener Fachinhalt folgt seinem Zielobjekt. Rohprompts nicht im normalen Diagnoseprotokoll. |
| Diagnoseprotokolle | Vorschlag 14 Tage mit Volumenrotation; ohne Fachinhalte oder Geheimnisse. |
| Sicherheits-/Änderungsnachweise | Frist offen; Integrität und eingeschränkte Leserechte. Nicht zusammen mit kurzlebigen Diagnoselogs behandeln. |
| Sicherungen | Vorschlag 14 Tage vollständig wiederherstellbar; benötigte älteste Basis und Protokollkette nicht vorzeitig löschen. |
| Importpakete / Probeläufe | Nach fachlichem Abschluss und bestätigter Frist entfernen; keine dauerhafte unkontrollierte Zweitkopie. |

Löschung erfasst Verknüpfungen, Suchdaten, Vorschauen und abgeleitete KI-Inhalte. Sie wird nachvollziehbar, ohne gelöschte Inhalte im Löschprotokoll erneut zu speichern. Daten können bis zum Ablauf einer vereinbarten Sicherungsfrist noch in geschützten Backups enthalten sein. Vor Wiederfreigabe einer Rücksicherung müssen bekannte spätere Löschungen und Rechteentzüge erneut angewandt werden. Sicherungsaufbewahrung und individuelle Löschanforderungen benötigen deshalb ein abgestimmtes Verfahren; ein Sofortlöschen aus allen historischen Sicherungen wird nicht behauptet.

## Ressourcen- und Speicherrechnung

Als **Messreferenz, nicht als garantierte Mindesthardware**, bleiben 4 vCPU, 16 GB RAM und SSD aus TASK-003 vorgeschlagen. Der Host betreibt Anwendung, Worker und PostgreSQL. Grobe Startbudgets: 4 GB für Webprozess, 2 GB für Worker, 6 GB für PostgreSQL, 4 GB für Betriebssystem und gemeinsame Reserve. Die tatsächlichen Limits und Einstellungen hängen vom noch offenen Backend und den Messungen ab. Bei Engpässen wird mit 8 vCPU / 32 GB verglichen. Es entsteht keine neue RAM-Obergrenze.

Lokale Modellhardware, separate Sicherungen und gegebenenfalls Scan-/Suchdienste kommen zusätzlich hinzu. CPU-Modell, Virtualisierungsanteil, SSD-Latenz, Speicherdurchsatz und Netzweg werden im Messbericht festgehalten; „4 vCPU“ allein ist keine vergleichbare Leistungsangabe.

**Fünfjahresbeispiel mit Dezimaleinheiten:** 1.500 Tickets pro Monat × 60 Monate = 90.000 Tickets. Die Annahme von durchschnittlich 2 MB ursprünglichen Anhängen je Ticket ergibt 180 GB. Mit einem ausdrücklich angenommenen Faktor 1,3 für weitere gespeicherte Dateifassungen und Duplikate entstehen 234 GB. Diese Werte müssen am Pilotbestand geprüft werden.

| Bestandteil | Vorgeschlagenes Budget im Beispiel |
|---|---:|
| Ticketdateien einschließlich Faktor 1,3 | 234 GB |
| Datenbank, Indizes, Suchtext und Fachhistorie | 60 GB |
| Wiki- und sonstige zusätzliche Dateien | 25 GB |
| Begrenzte Rohmail-/Quarantäneablage | 10 GB |
| Lokaler WAL-Puffer | 40 GB |
| Betriebssystem, Laufzeit und Images | 20 GB |
| Rotierende Diagnoseprotokolle | 10 GB |
| Temporäre Daten und Updatearbeitsraum | 50 GB |
| Zwischensumme | 449 GB |
| Zusätzliche Planungsreserve: 40 Prozent | 179,6 GB |
| Gesamter Planungsbedarf | **628,6 GB** |

Ein nominelles 1-TB-Laufwerk bietet 1.000 GB beziehungsweise ungefähr 931 GiB vor Dateisystem- und weiteren Systemanteilen. Das Beispiel liegt unter diesem Rahmen, belegt aber keine reale Auslastung. Die Datenbank- und Pufferwerte sind Budgets, keine Berechnung aus implementierten Tabellen. Sicherungsspeicher wird separat anhand Basisgröße, Änderungen, WAL-Menge, Dateiversionen und Aufbewahrung gemessen; eine pauschale Verdopplung des Live-Bedarfs ist kein belastbares Backupbudget.

**Gegenbeispiel:** Bei durchschnittlich 10 MB Anhängen je Ticket entstehen mit demselben Faktor bereits 1.170 GB Ticketdateien. Mit den übrigen 215 GB und 40 Prozent Reserve wären es 1.939 GB. Dafür reicht der bisherige 1-TB-Rahmen nicht. Erforderlich wären ein geändertes Mengenprofil, eine fachlich beschlossene Aufbewahrung oder mehr Speicher; keine dieser Änderungen wird automatisch vorgenommen.

Vorgeschlagene Warnstufen: 70 Prozent belegter Nutzkapazität und absehbare Erschöpfung innerhalb von 30 Tagen; kritisch bei 85 Prozent oder weniger als 14 Tagen. Bei 90 Prozent werden neue große Uploads kontrolliert begrenzt. Notwendige Reserven für Datenbank, WAL und Auftragsstatus bleiben geschützt. Kein stilles Löschen und kein Erfolgsstatus für einen nicht dauerhaft gespeicherten Vorgang. Exakte Grenzen sind im Störungstest zu erproben.

**Wiederherstellungsdurchsatz:** Rein rechnerisch benötigen 500 GB bei effektiv 100 MB/s rund 83 Minuten Kopierzeit, bei 25 MB/s rund 333 Minuten. Der langsamere Weg verfehlt das Vierstundenziel bereits vor Datenbankwiederanlauf und Prüfung. Kompression, Entschlüsselung, viele kleine Dateien, WAL-Wiedergabe und Ersatzbereitstellung sind zusätzlich zu messen. Ein schneller Anschluss allein beweist den effektiven Gesamtdurchsatz nicht.

## Lastprofil und vorgeschlagene Messziele

Der Referenzbestand enthält 100 Konten, 20 Projekte mit unterschiedlichen Gruppen-/Feldrechten, 90.000 Tickets, durchschnittlich zehn Historieneinträge und fünf Zeitbuchungen je Ticket, 2.000 Wiki-Seiten mit mehreren Revisionen sowie das oben beschriebene Dateiprofil. Eigene Felder, interne und externe Sichten, DE/EN und lange Ticketverläufe werden einbezogen. Unberechtigte oder leere Datenbestände dürfen keine günstigeren Scheinmessungen liefern.

Vorgeschlagen sind 15 aktive Sitzungen mit im Mittel einer Fachaktion alle fünf Sekunden. Aktionen verteilen sich auf Lesen/Listen (45 Prozent), Suche/Filter (25 Prozent), Ticketänderung/Antwort (15 Prozent), Zeitbuchung (10 Prozent) und Wiki-Bearbeitung (5 Prozent). Ein 30-Minuten-Messlauf nach zehn Minuten Aufwärmzeit sowie ein zweistündiger Dauerlauf prüfen Normalbetrieb. Eine zusätzliche Laststufe mit 30 Sitzungen zeigt die Überlastreaktion; die Normalziele sind dafür nicht automatisch zugesagt.

Parallel laufen reale Hintergrundmuster: Mailabruf, SLA-Fälligkeiten und Sicherung. Ein separater Spitzenfall mit 30 eingehenden Mails je Minute über fünf Minuten ergibt 150 Eingänge. KI-Last wird zusätzlich mit höchstens zwei parallelen Generierungen aus dem KI-Entwurf geprüft; Modellserver beziehungsweise Providerlaufzeit wird getrennt ausgewiesen.

| Messgröße | Vorgeschlagener Zielwert unter Referenzlast |
|---|---|
| Ticketliste / Detail-API | p95 höchstens 500 ms innerhalb des Anwendungsservers, ohne externe Integration. |
| Suche / reguläre Speicherung | p95 höchstens 1 Sekunde; komplexe erlaubte Rechtefilter enthalten. |
| Sichtbare Kernbedienung | p95 höchstens 2 Sekunden vom Auslösen bis zum verwendbaren Ergebnis im Referenzbrowser und festgehaltenen Netzprofil. |
| Mailverarbeitung | p95 höchstens 60 Sekunden vom Abrufbereitstehen im Testpostfach bis zum verarbeiteten Eingang, einschließlich Polling. Prüfwarteschlangen bleiben sichtbar. |
| SLA-Eskalationsauftrag | p95 höchstens 60 Sekunden nach Fälligkeit dauerhaft angelegt; Zustellung und SMTP-Annahme separat messen. |
| Technische Fehler bei zulässigen Aktionen | Unter 0,5 Prozent; erwartete Fachablehnungen separat zählen. Kein stiller Datenverlust. |
| Sicherung / Wiederherstellung | Bestätigtes RPO ≤ 60 Minuten, RTO ≤ 240 Minuten für den geprüften vollständigen Ausfallfall. |

Perzentile werden je Aktion und mit Stichprobengröße ausgewiesen; ein schneller Gesamtmittelwert ersetzt keine langsame Suche. Kaltstart, Cachezustand, CPU/RAM/IO, Warteschlangenalter, erfolgreiche Schreibvorgänge und Datenkorrektheit gehören in den Bericht. Git- und KI-Aufrufe werden separat gemessen und dürfen die Kernseite nicht blockieren. Für einen Leistungsvorteil gegenüber Redmine/Easy8 braucht es vergleichbare Produktversionen, Funktionen, Daten, Rechte und Hardware. Der Entwurf behauptet keinen solchen Nachweis.

## Betriebsverantwortung und Sicherheitsprüfung

Der Kunde beziehungsweise ausdrücklich beauftragte Betreiber verantwortet Host, Netzwerk, Sicherungsziel, Erreichbarkeit, Wartungsfenster und lokale Zugänge. Der Produktanbieter liefert später unterstützte Versionen, Installations-/Updateanleitung, Migrationspfad, Diagnosebeschreibung und geprüfte Anwendungsartefakte. Bei Fernwartung bleiben Zuständigkeit, Erreichbarkeit und Freigabeverfahren schriftlich zuzuordnen. Es gibt hier keine zugesagte Bereitschaftsleistung.

Die Überwachung unterscheidet Web-Erreichbarkeit, Bereitschaft der Kernanwendung, Datenbank, Dateispeicher, Jobrückstand, Postfächer, Integrationen, Zertifikate und letzte vollständige Sicherung. Ein KI-Ausfall allein macht die Kernanwendung nicht unbereit. Ein externer Meldeweg ist nötig, damit eine Störung nicht nur über den ebenfalls ausgefallenen Produkt-Mailversand gemeldet werden soll. Konkrete Alarmkanäle und Reaktionszeiten bleiben offen.

Vor Kundeneinsatz benötigt die technische Prüfung negative Rechte-/Anmeldetests, Sitzungs- und API-Schutz, Upload-/Rich-Text-Prüfung, Schutz vor unzulässigen ausgehenden Zugriffen, Geheimnisbehandlung und nachvollziehbare Build-Abhängigkeiten. Vorgeschlagen sind reproduzierbare Images, Komponentenverzeichnis, Prüfung bekannter Schwachstellen und verifizierbare Herkunft der Releases. Verbindliche Freigabeschwellen und der technische Reviewer werden unter OPEN-017 beschlossen. Fachliche Abnahme und ein erfolgreicher Repository-Check ersetzen diese Prüfung nicht.

## Fachliche und betriebliche Prüffälle

Diese Fälle beschreiben spätere Tests. Jetzt wurden nur Dokumentkonsistenz und Rechenbeispiele geprüft.

| Fall | Erwartetes Ergebnis |
|---|---|
| O01 Container austauschen | Datenbank, Dateien und Konfiguration bleiben erhalten; keine Geheimnisse im Image. |
| O02 Webprozess oder Worker abstürzen | Keine verlorenen bestätigten Daten; Aufträge werden kontrolliert fortgesetzt, ohne doppelte Fachaktion. |
| O03 Internet oder einzelner Anbieter ausfallen | Lokale Kernarbeit bleibt nutzbar; Mail/Git/KI zeigen getrennte Fehlerzustände. |
| O04 Datenbank-/Dateispeicher nicht verfügbar | Kein falscher Speichererfolg; Wiederanlauf und verwaiste Uploads bleiben nachvollziehbar. |
| O05 Upload zu groß oder Scanner ausgefallen | Kontrollierte Ablehnung/Quarantäne, keine stille Maillöschung und keine ungeschützte Dateifreigabe. |
| O06 WAL-Archivierung erfolgreich, Datei fehlt | Kein vollständiger gemeinsamer Sicherungspunkt; RPO-Überwachung meldet den Rückstand. |
| O07 Host vollständig verloren | Frische Wiederherstellung einschließlich Dateien, Konfiguration und Schlüsseln; RPO/RTO unter dem gewählten Profil nachweisen. |
| O08 Sicherung beschädigt / Schlüssel fehlt | Punkt gilt als nicht wiederherstellbar; Alarm und transparenter Rückfall auf älteren geprüften Punkt, keine falsche Zielerfüllung. |
| O09 Rücksicherung nach erfolgtem Versand | Unklare alte Versandaufträge bleiben angehalten; keine blinde erneute Mailzustellung. |
| O10 Rücksicherung nach Rechteentzug oder Löschung | Sitzungen ungültig; bekannte Änderungen vor Freigabe nachziehen; unklare Zugänge gesperrt. |
| O11 Update / Schemaänderung schlägt fehl | Genau ein Schemaänderungslauf, dokumentierte Rückkehr; kein Start inkompatibler Anwendungsversion. |
| O12 Speichergrenzen / blockiertes Backupziel | Frühzeitige Warnung und kontrollierte Begrenzung; keine automatische Löschung von Fachinhalten. |
| O13 Aufbewahrung ausführen | Nur bewusst freigegebene Fristen anwenden; Referenzen und benötigte Sicherungsketten bleiben konsistent. |
| O14 15 aktive Benutzer plus Hintergrundarbeit | Antwortzeiten, Fehlerrate, Datenkorrektheit und Rückstände je Vorgang messen. |
| O15 Mailspitze / 30 Sitzungen / KI-Last | Begrenzte Warteschlangen und nachvollziehbare Überlastreaktion; keine unbegrenzten Wiederholungen. |
| O16 Fernwartung / Diagnose / Zielmanipulation | Zeitlich begrenzte Zugänge, bereinigte Diagnosen und nur freigegebene Netzwerkziele. |

OPEN-012 bleibt für Plattformmatrix, Sicherungswerkzeug/-ziel, Netzwege, Servicezeit und Verantwortlichkeit offen; DEC-038 bestätigt die Wiederherstellungsziele. OPEN-014 entscheidet Lastprofil und Leistungswerte; OPEN-016 die Aufbewahrung. Primärquellen zu Docker und PostgreSQL wurden am 15. Oktober 2026 geprüft. Es wurden keine Container installiert, Daten gesichert, Wiederherstellungen durchgeführt oder Leistungs-/Sicherheitstests an einer Anwendung ausgeführt.
