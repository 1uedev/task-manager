# Prüfbelege TASK-060

Prüfbasis `c22d6f75f36eb3dbe7f28721c537f5b440897d48`, 29. Oktober 2026. JDK 25.0.4.1, PostgreSQL 18.6 lokal, ausschließlich synthetische Daten. Auswertung im [Bericht](../../REVIEW_TASK-060_PR-196.md). Keine vollständigen Prozessprotokolle oder Zugangsdaten werden veröffentlicht.

## Java-Experimente E1 bis E6

Die [Prüfmethoden](HelpdeskReview060.probes.java.txt) ergänzen vorübergehend die bestehende Fixture der Prüfbasis:

1. `backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java` als `backend/src/test/java/taskmanager/helpdesk/internal/HelpdeskReview060Test.java` kopieren.
2. Paket auf `taskmanager.helpdesk.internal` und Klassenname auf `HelpdeskReview060Test` ändern.
3. Den beigefügten Block am Anfang des Klassenrumpfs einfügen. Originalimporte und Hilfsmethoden bleiben erhalten. `ScriptedMailbox` bleibt unverändert.
4. Mit der lokalen PostgreSQL-Testkonfiguration aus backend/README.md nur `./mvnw '-Dtest=HelpdeskReview060Test#probe*' test` ausführen. Unter Windows wurde Maven direkt aus der Wrapper-Installation verwendet.
5. Die vorübergehende Testklasse anschließend entfernen. Keine parallele Kompilierung im selben Buildverzeichnis wie der reguläre Gesamttest starten.

Der Block enthält keine konkreten Passwort-/Schlüsselwerte. Namen wie `MAIL_PASSWORD` referenzieren die bereits vorhandene synthetische Fixture.

| Experiment | Ergebnis |
|---|---|
| E1 `probeOldFetchCreatesSecondTicketAfterNewGenerationAlreadyImportedSameMail` | **Beobachteter Fehler S1:** dieselbe Rohmail mit derselben Message-ID wird zuerst in der neuen, danach in der alten Generation gespeichert. Zwei Tickets, eine unterschiedliche Message-ID; aktueller Cursor bleibt Quelle 2/Position 1. Dieser Test erwartet das Fehlerbild und ist deshalb grün. |
| E2 `probeReturnedValidityKeepsOldGenerationFromMovingCursor` | 100 → 200 → 100: Generation 3 bleibt bei Position 1/Abgleichgrenze 2, obwohl Generation 1 verspätet UID 100 verarbeitet; UID 2 wird beim nächsten Abruf übernommen. Unterschiedliche Nachrichten: vier Tickets wie erwartet. |
| E3 `probeInitialStartAndSettingsChangeAreConditional` | Zwei gleichzeitige Erstabrufe erzeugen ein Ticket. `startFolder`, `restartFolder` und `position` lehnen die geprüften falschen Vorbedingungen ab. Kontowechsel per HTTP-API während eines angehaltenen Erstabrufs: nur neue Generation übernommen. |
| E4 `probeGroupsAndMalformedGroupsThroughRealParser` | **Rot wegen S2:** erwartetes `recipients_incomplete` bei verschachtelter Gruppe fehlt. Sechs Varianten eingelesen und verarbeitet; Details unten. |
| E5 `probeOneBudgetAcrossRepeatedHeadersGroupsAndHeaderOnly` | Mehrere To-/Cc-Köpfe, Einzeladressen und Gruppen gemeinsam: 1000 vollständig, 1001 unvollständig. `parse` und `headerOnly` liefern identische Adressen und Vollständigkeit. |
| E6 `probePersistentComparisonAfterFailureAndNewIntakeInstances` | Sechs Mails, Paketgröße zwei, Fehler nach erstem Abgleichpaket, neue `MailIntake`-Objekte: sechs Tickets bleiben sechs, Grenze am Ende gelöscht. Kein echter Prozessabsturz. |

Gesamtlauf: **6 Tests, 5 bestanden, 1 Assertion-Fehler, keine Ausführungsfehler**. E4 wurde zur Eingrenzung nochmals einzeln ausgeführt und scheiterte erneut an derselben Assertion. Der veröffentlichte Block enthält die dabei ergänzte Ausgabe der Empfänger und Gründe. Produktcode unverändert.

### Einzelbeobachtungen zu E4

Die Kontaktadressen in dieser Tabelle sind vereinheitlichte synthetische Beispiele. Der Lauf erzeugt eigene synthetische Kontakte.

| Cc-/To-Wert | `recipientsComplete` | Ausgelesener Gruppeninhalt | Ergebnis |
|---|---|---|---|
| `TEAM: Anzeigename <FOREIGN@EXAMPLE.TEST>;` in To | wahr | `foreign@example.test` | `foreign_customer` |
| `TEAM: "Name, mit Komma" <foreign@example.test>;` in Cc | wahr | `foreign@example.test` | `foreign_customer` |
| `Leer:;` | wahr | keine Adresse | automatisches Ticket, zulässiger Kontrollfall |
| `Outer: Inner: foreign@example.test;;` | wahr | `inner: foreign@example.test` | automatisches Ticket; S2 |
| `Broken: "unterminated <foreign@example.test>;` | wahr | `foreign@example.test` | `foreign_customer`, obwohl Syntax unvollständig |
| `MissingEnd: foreign@example.test` | wahr | `missingend: foreign@example.test` | automatisches Ticket; S2 |

## E7: Feldrechte im Browser-Testmodell

[model-field-rules.mjs](model-field-rules.mjs) installiert den tatsächlichen `createBackend`-Routenhandler mit einer minimalen Route-/Context-Hülle. Es ist ein direkter Modelltest ohne Browser und ohne Produktserver; die vollständige Browserreihe wurde gesondert ausgeführt.

Aufruf mit dem gewünschten Modell als Argument:

```sh
node docs/review-evidence/task-060/model-field-rules.mjs frontend/tests/browser/backend.mjs
```

Die 16 Bitmasken kombinieren Lesen und Schreiben auf customer und contact bei vorhandenem `ticket.read`, `ticket.edit`, Projektzugriff und gültigem Ticket. Erwartet: ohne beide customer-Regeln 403; sonst 200 und Kontakte ausschließlich mit beiden contact-Regeln. Die aktuelle Fassung liefert **0/16 Abweichungen**. Der unveränderte Modellstand vor PR #196 liefert **3/16 Abweichungen**, jeweils Kontakte trotz fehlender Kontaktregel. Der alte Stand wurde mit `--observe` ausgeführt, um sämtliche Abweichungen auszugeben, ohne am Ende eine Assertion auszulösen.

## E8: Strenges und tolerantes Parsen

[RecipientParsingProbe.java.txt](RecipientParsingProbe.java.txt) als `RecipientParsingProbe.java` außerhalb des Produktquellbaums ausführen, mit den Jakarta-Mail-/Angus-Abhängigkeiten des Backends auf dem Klassenpfad. Die Probe ruft `parseHeader` und für Gruppen `getGroup` jeweils mit `false` und `true` auf.

Ergebnis: Normale und leere Gruppen werden in beiden Modi gleich gelesen. Verschachtelte Gruppen, nicht geschlossene Anführungszeichen und fehlendes Gruppen-Semikolon erzeugen im strengen Modus jeweils `AddressException`. Der tolerante Modus erzeugt die oben beschriebenen Adressen, ohne Fehler zu melden. Das stützt den Korrekturvorschlag; eine Produktkorrektur wurde nicht eingebaut.

## Vier neue Regressionen auf dem alten Produktstand

Ein separater Checkout des main-Stands `463c3e4` vor PR #196 bekam ausschließlich die neue `HelpdeskApiTest.java` und `ScriptedMailbox.java` aus der Prüfbasis. Kein Produktcode wurde ausgetauscht. Ausgeführt wurden die vier neuen Methoden, selektiert mit `-Dtest=HelpdeskApiTest#...`:

- `aFetchOfTheOldUidValidityDoesNotMoveThePositionOfTheNewOne`: Position 100 statt 2.
- `twoFetchesThatSeeTheSameNewUidValidityStartTheFolderOverOnce`: Quelle 1 statt 2.
- `aContactOfAnotherCustomerInsideAGroupCounts`: `[null, null, null]` statt `[foreign_customer, foreign_customer, null]`.
- `theLimitOfRecipientsHoldsForToAndCcTogether`: `[null, null, null]` statt `[recipients_incomplete, null, recipients_incomplete]`.

**4 Tests, 4 Assertion-Fehler, 0 Ausführungsfehler.** Auf der Prüfbasis bestehen alle 18 regulären `HelpdeskApiTest`, einschließlich dieser vier Fälle. Das bestätigt deren Wirksamkeit für ihre konkreten Szenarien, nicht die noch fehlenden Randfälle S1/S2.
