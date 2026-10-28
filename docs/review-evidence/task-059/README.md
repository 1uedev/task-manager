# Nachprüfbelege TASK-059

Prüfbasis `059de83526a04a43e462ab7897f9164d4b3d838a`, 28. Oktober 2026. Synthetische Daten, isoliertes PostgreSQL 18.6, JDK 25.0.4.1. Keine Zugangsdaten oder vollständigen Prozessprotokolle werden veröffentlicht.

## Nachstellung

Die [Prüfmethoden und Hilfen](HelpdeskReview059.probes.java.txt) benutzen die unveränderte Fixture aus `backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java` der Prüfbasis:

1. Diese Fixture vorübergehend als `backend/src/test/java/taskmanager/helpdesk/internal/HelpdeskReview059Test.java` kopieren.
2. Paket auf `taskmanager.helpdesk.internal`, Klassenname auf `HelpdeskReview059Test` ändern.
3. Den beigefügten Block am Anfang des Klassenrumpfs einfügen. Die ursprünglichen Fixture-Methoden und Importe bleiben bestehen.
4. Nur `./mvnw '-Dtest=HelpdeskReview059Test#probe*' test` mit der lokalen PostgreSQL-Testkonfiguration aus backend/README.md ausführen. Im tatsächlichen Windows-Lauf wurde Maven direkt aus der Wrapper-Installation gestartet.
5. Die vorübergehende Testklasse nach der Prüfung entfernen. Die reguläre Suite wurde vor ihrer Kompilierung unverändert mit `verify` ausgeführt.

Der Block enthält keine konkreten Passwort-/Schlüsselwerte; Bezeichner wie `MAIL_PASSWORD` beziehen sich auf die bestehende synthetische Fixture. Produktcode wird für die Nachstellung nicht geändert.

## Ergebnisse

| Experiment | Ergebnis | Art des Nachweises |
|---|---|---|
| E1 `probeRecipientGroupStillBypassesForeignCustomerCheck` | Gruppentext statt einzelner fremder Kontaktadresse; Vollständigkeit wahr; ein automatisches Ticket | Echte MIME-Verarbeitung, echte PostgreSQL-/Ticketverarbeitung, MailClient-Testadapter. Belegt R2. |
| E2 `probeCombinedRecipientLimitIsPerHeaderInsteadOfPerMail` | Eine To- plus 1000 CC-Adressen: ein Ticket statt Prüfwarteschlange | GreenMail und echte Abruf-/Datenbankverarbeitung, gefalteter CC-Header. Belegt R3. |
| E3 `probeConcurrentComparisonSerializesUnknownDuplicateIds` | Zwei parallele Ausführer, zwei UIDs mit gleicher Message-ID: genau ein Eingang/Ticket, Abgleich beendet | Zwei MailClient-Verbindungen mit synchronisiertem Einstieg, echte Transaktionen und Postfachsperre. Keine Verklemmung. |
| E4 `probeComparisonSurvivesFailureAndRecreatedIntake` | Sechs alte Nachrichten, Paketgröße zwei, Fehler nach dem ersten Abgleichpaket, neue Ausführerobjekte: weiterhin sechs Tickets, Grenze am Ende gelöscht | Simulierte IMAP-Gültigkeit und Ausnahme, dauerhafter PostgreSQL-Zustand. Kein echter JVM-Neustart. |
| E5 `probeSourceChangeDuringFetchPreservesNewPosition` | Kontowechsel während altem Abruf: Quelle 2 behält Gültigkeit 200/Position 1; alter Eingang wird Quelle 1 zugeordnet | Latches an MailClient-Grenze, Kontowechsel über echte versionierte HTTP-API, echte Datenbank. |
| E6 `probeOldUidValidityCanAdvanceNewCursorPastUnprocessedMail` | Überlappende Gültigkeiten: neue Gültigkeit 200 erhält Position 100 nach UID 2; neue UID 3 wird übergangen | Deterministisch angeordnete Abrufe mit Testadapter, echte Positionsupdates/Transaktionen. Belegt R1; kein echter IMAP-Neustart oder Jobs-Lease-Ausfall. |
| E7 `probeMigrationPreservesExistingOriginAndSeparatesSources` | V0026-Daten werden migriert: Quelle 1 ergänzt, Position 7 erhalten; identische UID in Quelle 2 erlaubt, in Quelle 1 verweigert | Flyway zunächst bis V0026, synthetischer Bestand eingefügt, dann V0027 gegen echte neue PostgreSQL-Datenbank. |
| E8 `probeUnexpectedFailureLogContainsNoMessagesOrThrowable` | Verschachtelte Laufzeitausnahme: nur Klassen im Log, kein Meldungstext und kein Throwable-Anhang; Fehlercode gespeichert | Allgemeiner `run`-Fehlerpfad und Logback-Appender; ergänzt den vorhandenen Test zur gespiegelten IMAP-Anmeldung. |
| E9 `probeContactWriteWithoutReadDoesNotExposeOptions` | Schreibrecht auf contact ohne Leserecht: leere Kontaktliste, keine Kontaktadresse | Echte HTTP-API und Rechteprüfung. |

Abschließender Lauf: **9 Tests, 0 Fehler, 0 übersprungen, BUILD SUCCESS**. E1, E2 und E6 sind Beobachtungstests auf das fehlerhafte Verhalten, keine bestandenen Abnahmekriterien. Die übrigen Fälle bestätigen die genannte Korrektur oder Kontrollbedingung.

E1 wurde zunächst über GreenMail versucht. Dabei meldete die IMAP-Strecke `helpdesk.protocol_error`, bevor ein Eingang entstand; die ursprüngliche Erwartung „ein Ticket“ schlug entsprechend fehl. Dieser Vorversuch wird nicht als Beleg einer erfolgreichen Ende-zu-Ende-Übernahme dargestellt. Die endgültige Probe isoliert die fachliche Schwäche, indem sie die synthetische MIME-Nachricht über den vorgesehenen MailClient-Port liefert. E2 wurde abschließend mit einer gefalteten Kopfzeile wiederholt.

Weitere tatsächlich ausgeführte Prüfungen: reguläres Backend 366/366, Frontend 77/77 und Build, Browser vollständig 124/124 sowie Helpdesk dreimal 10/10, Repository-Unittests 38 ohne Fehler mit einem plattformbedingten Skip. Einschränkungen und Bewertung stehen im [Bericht](../../REVIEW_TASK-059_PR-193.md).
