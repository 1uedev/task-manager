# Prüfbelege TASK-061

Prüfbasis `c49a53f2d19ed07b5232927b0c9a4536801894d2`, 29. Oktober 2026. Ausschließlich synthetische Daten, JDK 25.0.4.1, lokales PostgreSQL 18.6. Bewertung im [Bericht](../../REVIEW_TASK-061_PR-199.md). Keine vollständigen Prozessprotokolle oder Zugangsdaten werden veröffentlicht.

## Nachstellung

Die [zusätzlichen Prüfmethoden](HelpdeskReview061.probes.java.txt) ergänzen vorübergehend die Fixture der Prüfbasis:

1. `backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java` als `backend/src/test/java/taskmanager/helpdesk/internal/HelpdeskReview061Test.java` kopieren.
2. Paket auf `taskmanager.helpdesk.internal`, Klassenname auf `HelpdeskReview061Test` ändern.
3. Den beigefügten Block am Anfang des Klassenrumpfs einfügen; Originalimporte, Hilfsmethoden und `ScriptedMailbox` beibehalten.
4. Mit der lokalen PostgreSQL-Testkonfiguration aus backend/README.md nur `./mvnw '-Dtest=HelpdeskReview061Test#probe*' test` ausführen. Im tatsächlichen Windows-Lauf wurde Maven direkt aus der Wrapper-Installation gestartet.
5. Die vorübergehende Testklasse nach der Prüfung entfernen. Zusätzliche Tests und regulären Gesamtlauf in getrennten Buildverzeichnissen ausführen.

Namen wie `MAIL_PASSWORD` verweisen auf die bereits vorhandene synthetische Fixture; konkrete Passwort-/Schlüsselwerte sind im Block nicht enthalten. Die Produktklassen bleiben unverändert.

## Ergebnisse

| Experiment | Nachstellung und Ergebnis |
|---|---|
| E1 `probeKnownOriginCanBeMarkedSeenAfterRestartOrSettingsCommit` | Eingang zuerst speichern, Position auf 0 setzen. Beim nächsten Abruf nach dem Lesen des alten Postfachstands einen neuen Ordnerstand oder eine Kontoeinstellung per echter HTTP-API committen. Der bekannte Ursprung umgeht `lockCurrent`: alte Verbindung trotzdem als gelesen markiert, kein erneutes Lesen der Rohmail, kein zusätzliches Ticket/kein Positionsüberschreiben. Zwei Varianten. **Beobachtung von T1.** |
| E2 `probeNewRecordCanBeMarkedSeenAfterLaterRestartCommit` | Ein delegierender Transaktionsmanager führt nach dem echten Commit des alten Eintrags, noch vor Rückkehr zu `take`, einen neuen Abruf mit anderer UIDVALIDITY aus. Nach dessen Commit beginnt der alte `markSeen`-Aufruf. Ein Eingang/Ticket bleibt erhalten, Quelle 2 bleibt aktuell. **Beobachtung von T1.** |
| E3 `probeSettingsBeforeRecordRejectOldMailWithoutMarkingSeen` | Alten Abruf nach Lesen der Rohmail anhalten; Postfachkonto über versionierte HTTP-API ändern; alten Abruf fortsetzen. Kein Eingang, kein Ticket, kein Lesemarker, kein alter Cursor. |
| E4 `probeMailboxLockSerializesRestartAndSettingsWithoutDeadlock` | `lockCurrent` hält die Postfachzeile. Ein echtes `restartFolder` beziehungsweise eine HTTP-Einstellungsänderung wartet nachweisbar in PostgreSQL. Nach Freigabe beenden sich beide; alter Stand wird anschließend abgewiesen. Zwei Varianten, kein Deadlock. |
| E5 `probeUsualRecipientFormsAndMalformedGroupsForBothPaths` | Acht übliche Formen in To/Cc jeweils über `parse` und `headerOnly`: alle vollständig und mit erwarteten Adressen. Drei fehlerhafte Gruppenformen in To/Cc jeweils über beide Pfade: alle unvollständig. |
| E6 `probeWaitingLockCurrentSeesCommittedGenerationAndRejectsIt` | Ordnerneustart hält seine Änderung uncommittet; altes `lockCurrent` wartet auf die Zeile. Nach Commit bewertet es den neuen Stand und liefert false. Kein veraltetes true nach dem Warten. |
| E7 `probeOverlappingOldCommitFirstStillMakesOneTicket` | Alte Eintragstransaktion mit bereits geschriebenem Eingang/Ticket vor Commit anhalten. Neuer Abruf wartet nachweisbar auf die Postfachzeile. Alten Commit freigeben: neuer Abruf erkennt danach die Message-ID. Genau ein Eingang/Ticket, Quelle 2, kein Deadlock. |

E1/E2 sind grüne **Beobachtungstests auf das Fehlerbild** und bedeuten nicht, dass die entsprechende Abnahmeanforderung erfüllt ist. Die übrigen Tests erwarten das gewünschte Verhalten.

Tatsächlich ausgeführt: zuerst E1–E5 (5/5), anschließend die um E6 ergänzte Suite (6/6), danach E7 einzeln (1/1). Alle ohne Assertion- oder Ausführungsfehler. Insgesamt sieben unterschiedliche Experimente. Die Schranken für die angeordneten Nebenläufigkeiten liegen bei 10 beziehungsweise 20 Sekunden; das Warten auf die Datenbanksperre wird anhand von `pg_stat_activity` geprüft, nicht lediglich aus einer festen Pause abgeleitet.

### Parserformen in E5

- Anzeigename mit groß geschriebener Adresse.
- Name in Anführungszeichen mit Komma.
- Kodierter UTF-8-Anzeigename.
- Gruppe mit zwei benannten Mitgliedern.
- Leere Gruppe.
- `undisclosed-recipients:;`.
- Anzeigename mit Kommentar.
- Über eine Faltung fortgesetzte Gruppenzeile.

Alle acht Formen werden in To und Cc über Voll- und Kopfpfad geprüft. Die drei zurückgewiesenen Formen sind verschachtelte Gruppe, fehlendes Abschluss-Semikolon und nicht geschlossenes Anführungszeichen im Anzeigenamen. Keine Aussage über alle möglichen historischen Adresssyntaxvarianten oder einen produktiven Empfangsserver.

## Rot-Nachweis vor der Korrektur

Ein separater Checkout des Vorgängerstands `4b720c6` bekam ausschließlich die neuen `HelpdeskApiTest.java`, `ParsedMailTest.java` und `ScriptedMailbox.java` aus der Prüfbasis. Der alte Produktcode wurde nicht verändert. Selektiert wurden:

| Test | Ergebnis vor Produktkorrektur |
|---|---|
| `theSameMailInTheOldAndTheNewUidValidityBecomesOneTicket` | rot, 2 statt 1 Ticket |
| `aFetchOfTheOldUidValidityDoesNotMoveThePositionOfTheNewOne` | rot, 4 statt 3 Eingänge |
| `aBrokenGroupKeepsTheMailInTheQueue` | rot, `[null, null, foreign_customer]` statt dreimal `recipients_incomplete` |
| `ParsedMailTest.brokenGroupsLeaveTheRecipientsIncomplete` | rot, verschachtelte To-Gruppe gilt als vollständig |
| `aMailTooLargeToFetchIsReadByItsHeader` | grün, weil der korrigierte Header-Testadapter mitkopiert ist; reine Abdeckung |

Ergebnis: **5 Tests, 4 Assertion-Fehler, keine Ausführungsfehler**. Auf der Prüfbasis bestehen alle 21 regulären Helpdesk-API-Tests und die Parserregressionen.

## Sonstige tatsächlich gelaufene Prüfungen

- Repositoryvalidierung und Aufgabenübersicht erfolgreich; 38 Python-Tests ohne Fehler, ein plattformbedingter Skip.
- Backend `verify`: 374 Tests, ein Assertion-Fehler im LDAP-Test für gleichzeitige Erstanmeldungen. Einzelne Wiederholung dieser Methode: 1/1 erfolgreich. Der fehlgeschlagene Gesamtlauf bleibt als solcher ausgewiesen.
- Frontend `npm ci`, `npm run verify`: 77 Tests und Build erfolgreich.
- Vollständige Browsertests dreimal: jeweils 124/124 erfolgreich.
- Sitzungsende-Test achtmal zusätzlich: jeweils 1/1 erfolgreich, über `--test-name-pattern` ausgewählt.
- `git diff --check` und abschließende Repositoryvalidierung erfolgreich.

Einschränkungen und Einordnung stehen im Bericht. Es wurde kein Produktcode korrigiert, keine Aufgabe umgestellt und keine Abnahme erklärt.
