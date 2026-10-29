# Nachprüfung TASK-061 / PR #199

Ergebnis: **Die bisherigen P2-Fehler S1 (doppelte Tickets) und S2 (fehlerhafte Gruppen) sind in den nachgestellten Fällen behoben. Offen bleibt ein P3-Befund T1 zur Lesemarkierung nach einem Quellenwechsel.** Die Datenbankprüfung schützt Eingänge, Tickets und Positionen; die weitergehende Zusage, ab dem Commit einer neuen Quelle werde auch keine alte Mail mehr als gelesen markiert, hält der Code nicht durchgängig ein. H1 und H2 sind im geprüften Umfang umgesetzt, H3 bleibt hinsichtlich dieser Zusage unzutreffend.

**Der angeforderte Backend-Gesamtlauf war nicht vollständig grün:** 374 Tests, ein Fehler im unveränderten LDAP-Nebenläufigkeitstest; dessen separate Wiederholung besteht. Frontend, drei vollständige Browserläufe und acht Wiederholungen des Sitzungsende-Tests bestehen. Keine Abnahme und keine Änderung des Aufgabenstatus.

Stand: 29. Oktober 2026. Codex, Sitzung `codex-20261029-task061-review-01`, getrennte technische Prüfung nach DEC-040. Die Umsetzung stammt aus `claude-20261029-helpdeskfix-03`; diese Sitzung hat daran nicht mitgearbeitet.

## Grundlage und Abgrenzung

- [PR #199](https://github.com/1uedev/task-manager/pull/199), Branch `claude/project-thread-b4u17z`, gemergte Prüfbasis auf main: **`c49a53f2d19ed07b5232927b0c9a4536801894d2`**.
- Maßstab: [Prüfbericht TASK-060](REVIEW_TASK-060_PR-196.md), S1/S2 und H1–H3; AGENTS.md, TODO.md (TASK-061), DEC-089 und DEC-088, backend/README.md (Helpdesk Eingang), Nachtrag TASK-061 in docs/HELPDESK_SLA.md.
- Durchgesehen: alle 13 geänderten Dateien sowie die beteiligten Wege in `MailboxService`, `TicketOpener`, `JdbcTicketAccess`, `JdbcProjectAccess` und den zugehörigen Repositories. Keine Migration und keine Vertragsänderung. Produktänderungen beschränken sich auf S1/S2. Eine zusätzliche Stabilisierung eines anderen Browsertests ist unter H2 ausgewiesen.
- Ausschließlich synthetische Daten. Der reguläre Testlauf, die Zusatzexperimente und der alte Produktstand benutzten getrennte Checkouts/Buildverzeichnisse und eigene Testdatenbanken. Kein Produktcode wurde verändert; die Prüfmethoden wurden nur vorübergehend als Tests ergänzt. [Nachstellcode und bereinigte Ergebnisse](review-evidence/task-061/README.md).

## Was gelaufen ist

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | erfolgreich über den verfügbaren Python-Interpreter |
| `python3 scripts/task_board.py --check` | erfolgreich, Aufgabenübersicht unverändert |
| `python3 -m unittest discover -s tests -v` | 38 Tests ohne Fehler; ein Skip für den nur unter Unix möglichen Zeitzonenwechsel |
| Backend `verify`, Maven aus der Wrapper-Installation, JDK 25.0.4.1, PostgreSQL 18.6 lokal | **374 Tests, 1 Assertion-Fehler, 0 Ausführungsfehler, 0 übersprungen; BUILD FAILURE**. Alle 21 `HelpdeskApiTest` erfolgreich. Fehlgeschlagen: `LdapLoginTest.twoFirstLoginsOfTheSamePersonCreateOneAccount`, HTTP 401 statt 200; H1. |
| Derselbe LDAP-Test separat wiederholt | 1/1 erfolgreich, BUILD SUCCESS; kein zweiter vollständiger Backendlauf |
| Frontend `npm ci` und `npm run verify`, Node 24.22.0 | erfolgreich: Generierung, Typprüfung, 77 Tests und Build |
| Frontend `npm run test:browser` dreimal nacheinander | **jeweils 124/124 erfolgreich**, installiertes Chrome über Playwright |
| Sitzungsende-Test zusätzlich achtmal nacheinander | **jeweils 1/1 erfolgreich**; Testname gezielt ausgewählt |
| Neue/geänderte Backendregressionen auf altem Produktstand `4b720c6` | 5 Tests: 4 erwartete Assertion-Fehler, 1 bestanden, keine Ausführungsfehler; Aufschlüsselung unten |
| Zusatzexperimente E1–E6 auf der Prüfbasis | 6/6 erfolgreich. E1/E2 sind Beobachtungstests auf T1, keine bestandenen Abnahmekriterien. |
| Zusatzexperiment E7: alter Eintrag committet zuerst bei überlappenden Abrufen | 1/1 erfolgreich: neues Abholen wartet, danach genau ein Eingang/Ticket, kein Deadlock |
| `git diff --check` und abschließende Repositoryprüfung | erfolgreich; Auslieferung ausschließlich als Bericht und Prüfbelege |

Die Sitzung lief unter Windows; Maven wurde direkt aus der Wrapper-Installation statt über das Unix-Skript `./mvnw` gestartet. PostgreSQL lief lokal mit dem expliziten Test-Override, nicht als gepinntes Containerimage. Nicht gelaufen: JDK 21, Linux-Chromium, Docker-/Compose-Betrieb, `lifecycle-check.sh`, produktiver Empfangsserver, echter JVM-/IMAP-Absturz oder Jobs-Lease-Verlust. Die deterministischen Nebenläufigkeitstests verwenden echte PostgreSQL-Transaktionen, den echten Parser und die echte Ticketanlage, aber modellierte IMAP-Verbindungen beziehungsweise dekorierte Transaktionsgrenzen.

## Abgleich mit Umfang und Abnahmekriterien

| Gegenstand | Bewertung |
|---|---|
| **S1: keine alten Eingänge/Tickets nach Neustart oder Quellenwechsel** | **Behoben in den Nachstellungen.** `lockCurrent` hält die Postfachzeile und prüft Quelle/UIDVALIDITY in der Eintragstransaktion. Die ursprüngliche gleiche Mail erzeugt ein Ticket; ein Kontowechsel vor dem Eintrag verwirft den alten Abruf vollständig (E3). |
| **S1: beide Reihenfolgen, keine doppelte Ticketanlage** | Die reguläre Regression prüft „neue Generation zuerst“ und „alter Eintrag zuerst“. E7 ergänzt eine tatsächlich überlappende Ausführung mit angehaltener alter Eintragstransaktion und auf deren Postfachsperre wartendem neuen Abruf. |
| **S1: Position, UIDVALIDITY und Abgleichgrenze** | Der Schutz aus TASK-060 bleibt bestehen: `position` verlangt Quelle und UIDVALIDITY, `startFolder` zusätzlich den bisher unbegonnenen Zustand, `restartFolder` den erwarteten alten Stand. E1/E2 zeigen trotz später Lesemarkierung keine alten Positionsupdates. |
| **S1: Markieren als gelesen nach dem Commit einer neuen Quelle ausgeschlossen** | **Nicht erfüllt; T1.** Bereits bekannte Eingänge umgehen `lockCurrent`. Auch bei einem neuen Eintrag wird dessen Sperre vor `markSeen` freigegeben. |
| **S2: strenge Empfängerprüfung einschließlich `headerOnly`** | **Behoben in den geprüften Fällen.** Verschachtelte Gruppe, fehlendes Semikolon und offener Anzeigename werden in To/Cc durch beide Einlesewege als unvollständig erkannt. Die API-Regression führt diese normalen, nicht übergroßen Nachrichten zu `recipients_incomplete`. |
| **S2: übliche Formen bleiben zulässig** | E5 prüft acht Formen jeweils in To und Cc mit `parse` und `headerOnly`: Anzeigename, zitierter Name mit Komma, kodierter UTF-8-Name, Gruppe mit zwei Mitgliedern, leere Gruppe, `undisclosed-recipients:;`, Kommentar und gefaltete Gruppenzeile. Alle vollständig, Mitglieder korrekt aufgelöst. |
| **H1: Testadapter benutzt echten Headerpfad** | **Umgesetzt.** `ScriptedMailbox.header` ruft `ParsedMail.headerOnly` auf. Die Übergrößenregression bestätigt fehlenden Body, keinen Ticketzugang und Prüfwarteschlange. Der bestehende Grund `too_large` hat hier Vorrang vor `recipients_incomplete`; die Parser-Vollständigkeit wird gesondert getestet. |
| **H2: stabiler Sitzungsende-Test ohne schwächere Prüfung** | **Für die ausgeführten Läufe bestätigt.** Vor Deaktivierung wird Netzwerkruhe abgewartet. Klick, Loginseite und genaue Sitzungsende-Meldung bleiben geprüft; hinzu kommt die Prüfung einer weiteren Anfrage. Drei vollständige Läufe plus acht gezielte Wiederholungen bestehen. |
| **H3: Dokumentation entspricht dem Verhalten** | **Nur teilweise.** Der Commit-Zeitpunkt für Datenbankeinträge ist präzisiert und geschützt. DEC-089, README und TODO behaupten zusätzlich das Ausbleiben einer Lesemarkierung; T1 widerspricht diesem Teil. Der SLA-Nachtrag beschränkt sich auf Eingang/Ticket und strenge Gruppen. |
| **Vorher rot, danach grün** | Die vier fachlichen S1-/S2-/Überlappungsregressionen wurden unabhängig auf altem Produktcode rot ausgeführt; auf der Prüfbasis bestehen sie. Der Header-Abdeckungstest ist mit dem neuen Testadapter bereits auf dem alten Produktcode grün. |
| **Alle Prüfungen grün** | **Nicht uneingeschränkt erfüllt:** der vollständige Backendlauf enthält den LDAP-Fehler aus H1, obwohl die separate Wiederholung besteht. |

### Sperrreihenfolge und Rot-Nachweis

`lockCurrent` (`HelpdeskRepository.java:291`) und die drei Zustandsupdates sperren dieselbe Postfachzeile. Der Abruf nimmt beim Eintrag zuerst diese Zeile und bei der Ticketanlage danach das Projekt (`FOR SHARE`) und den Ticketzähler. `MailboxService.update` sperrt nach seiner Rechteprüfung ebenfalls zuerst das Postfach (`:90`) und erst bei der Validierung das Projekt (`:187`). Im betrachteten Weg wird kein Projekt zuerst gesperrt und anschließend dasselbe bestehende Postfach verlangt. `startFolder`, `restartFolder` und `position` nehmen keine nachfolgende Projekt-/Ticket-Sperre.

E4 beobachtet in PostgreSQL das Warten eines Ordnerneustarts beziehungsweise einer echten HTTP-Einstellungsänderung auf die gehaltene Postfachzeile. Nach Freigabe werden beide beendet. E6 prüft die Gegenrichtung: Der Neustart hält die Zeile; `lockCurrent` wartet und liefert nach dessen Commit **false** für den alten Stand. Kein Deadlock in diesen Abläufen. Das ist kein allgemeiner Beweis für alle Kombinationen mit anderen Modulen.

Für den Rot-Nachweis wurden ausschließlich die neuen Testdateien `HelpdeskApiTest`, `ParsedMailTest` und `ScriptedMailbox` in einen separaten Checkout des Vorgängerstands `4b720c6` übernommen; dessen Produktcode blieb unverändert:

| Test | Alter Produktstand mit neuer Testfixture |
|---|---|
| `theSameMailInTheOldAndTheNewUidValidityBecomesOneTicket` | 2 statt 1 Ticket |
| `aFetchOfTheOldUidValidityDoesNotMoveThePositionOfTheNewOne` | 4 statt 3 Eingänge |
| `aBrokenGroupKeepsTheMailInTheQueue` | Gründe `[null, null, foreign_customer]` statt dreimal `recipients_incomplete` |
| `ParsedMailTest.brokenGroupsLeaveTheRecipientsIncomplete` | verschachtelte To-Gruppe vollständig statt unvollständig |
| `aMailTooLargeToFetchIsReadByItsHeader` | bestanden, weil der neue Header-Testadapter bereits mitkopiert ist; Abdeckung, kein Rot-Nachweis einer Produktkorrektur |

## Befunde

### T1 · P3 · Nach dem Commit einer neuen Quelle kann der alte Abruf weiterhin eine Mail als gelesen markieren

**Datei:** `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:167`, `:193`, `:208`, `:212`. Betroffene Zusage: DEC-089, backend/README.md (Abruf), TODO.md (TASK-061 Ergebnis).

Die Sperre wird ausschließlich für einen noch nicht bekannten Eingang in dessen Eintragstransaktion genommen. Danach ist sie wieder frei, bevor `connection.markSeen(uid)` aufgerufen wird. Für einen bereits bekannten Ursprung wird dieser gesamte Sperrblock übersprungen. Das anschließende geschützte Positionsupdate kommt zu spät, um die IMAP-Schreiboperation zu verhindern.

**Nachstellung E1, bekannter Eingang:**

1. Eine synthetische Mail in Quelle 1/UIDVALIDITY 100 wird gespeichert. Für die Wiederaufnahme wird die Position auf 0 gesetzt, der Eingang bleibt vorhanden. Dies modelliert einen bereits committeten Eingang mit noch nicht fortgeschriebener Position; kein echter Absturz wird ausgelöst.
2. Ein alter Abruf liest Quelle 1. Vor seiner Verarbeitung der UID-Liste committet ein anderer Abruf einen Ordnerneustart auf Quelle 2/UIDVALIDITY 200; alternativ ändert die echte HTTP-API das Postfachkonto und beginnt Quelle 2.
3. Der alte Abruf findet den bekannten Ursprung, überspringt `lockCurrent` und ruft trotzdem `markSeen` auf. Sein Positionsupdate wird abgewiesen.

**Nachstellung E2, neuer Eingang:** Ein delegierender Transaktionsmanager lässt die echte alte Eintragstransaktion vollständig committen und führt unmittelbar danach, vor der Rückkehr zu `take`, einen neuen Abruf mit UIDVALIDITY 200 aus. Dieser committet Quelle 2 und erkennt die Message-ID. Anschließend ruft der alte Abruf `markSeen` auf. Es wurde nicht nur eine bereits laufende IMAP-Operation verspätet beantwortet: Der alte Aufruf beginnt im Experiment erst nach dem neueren Commit.

**Beobachtet:** Die alte Verbindung wird in E1 und E2 als gelesen markiert. Es bleibt jeweils genau ein Eingang/Ticket; Quelle, UIDVALIDITY und Position der neuen Generation werden nicht überschrieben. **P3**, weil hier eine ausdrücklich zugesagte Grenze für einen externen Schreibzugriff verletzt ist, aber kein verlorener Eingang, kein doppeltes Ticket und kein falscher Cursor nachgewiesen wurde. Die ursprüngliche P2-Dublettenlücke ist damit nicht wieder eröffnet.

**Vorschlag:** Die Behandlung bereits bekannter Eingänge in die Prüfung des aktuellen Stands einbeziehen. Wenn der zugesagte Commit-Zeitpunkt auch für den Beginn von `markSeen` verbindlich sein soll, müssen dessen Freigabe und ein Quellenwechsel über dieselbe Sperre geordnet werden, beispielsweise mit einer separaten, zeitlich begrenzten Transaktion nach dem Eintragscommit. Ein bloßes ungesperrtes Nachlesen vor `markSeen` schließt die Lücke nicht. Dabei sind IMAP-Zeitlimits, längere Sperrhaltung und bereits abgesendete Operationen ausdrücklich zu berücksichtigen. Alternativ bedarf eine bewusst schwächere Garantie für bereits gespeicherte Mails einer dokumentierten Entscheidung; sie darf nicht still als erfüllt gelten. E1/E2 als Regressionen mit erwarteter ausbleibender Lesemarkierung ergänzen.

## Hinweise

### H1 · Backend-Gesamtlauf mit einem nicht zugeordneten LDAP-Fehler

`backend/src/test/java/taskmanager/identity/LdapLoginTest.java:515` erwartet bei vier gleichzeitigen Erstanmeldungen jeweils HTTP 200; einmal kam 401 zurück. Im Gesamtlauf: 374 Tests, 1 Assertion-Fehler. Ein anschließender isolierter Lauf exakt dieser Methode besteht. Die betreffenden LDAP-Dateien sind in PR #199 unverändert; ein Zusammenhang mit TASK-061 wurde nicht nachgewiesen. Der Befund wird weder als bestätigte neue Helpdesk-Regression ausgegeben noch durch den isolierten grünen Lauf aus dem Gesamtergebnis entfernt. Ein weiterer vollständiger Lauf und gegebenenfalls eine gesonderte Untersuchung der Anmeldenebenläufigkeit bleiben sinnvoll; Produktcode wurde nicht verändert.

### H2 · Kleine zusätzliche Teständerung außerhalb des ausdrücklich benannten Sitzungsende-Falls

`frontend/tests/browser/app.test.mjs:291` wartet nun im Test „a right withdrawn in another browser…“ auf das Entfernen des Titelfelds, bevor die unveränderten Assertions ausgeführt werden. Das ist eine weitere Teststabilisierung neben dem konkret beauftragten Sitzungsende-Test. Sie steht in der TODO-Übergabe, erweitert keine Produktfunktion und schwächt die Prüfung nicht ab. Bei strengem Abgleich des benannten Testumfangs ist diese Zusatzänderung dennoch zu berücksichtigen.

### H3 · Grenzen der Belege

Die üblichen Empfängerformen wurden direkt gegen beide realen Parserpfade geprüft, nicht über einen produktiven Empfangsserver. Die Tests beweisen keine universelle Akzeptanz aller historischen Mailadressformen. Die Nebenläufigkeit wurde gezielt mit Barrieren, echten Datenbankwartezeiten und kontrollierten Transaktionsgrenzen angeordnet; ein tatsächlicher Ausfall des Jobs-Leasings wurde nicht erzeugt. H2/H4 der Erstprüfung von TASK-057 und TASK-058 bleiben außerhalb dieser Nachprüfung und Holgers Abnahmeentscheidung vorbehalten.
