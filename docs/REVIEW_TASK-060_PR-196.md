# Nachprüfung TASK-060 / PR #196

Ergebnis: **Zwei offene Befunde mit Priorität P2 (S1, S2), kein P1.** Die Positionskorrektur aus R1 verhindert in den geprüften Abläufen das Überspringen neuer Mails; ein überholter Abruf kann aber noch ein zweites Ticket für dieselbe Nachricht anlegen. R2 ist für normale Adressgruppen behoben, für verschachtelte beziehungsweise unvollständige Gruppen nicht. R3 und die Korrektur der Kundenauswahl im Browser-Testmodell sind im geprüften Umfang behoben.

Stand: 29. Oktober 2026. Prüfer: Codex, Sitzung `codex-20261029-task060-review-01`, getrennte technische Prüfung nach DEC-040. An der Umsetzung aus der Claude-Sitzung `claude-20261028-helpdeskfix-02` war diese Sitzung nicht beteiligt. Keine Änderung von Produktcode oder Aufgabenstatus, keine Abnahme.

## Grundlage und Abgrenzung

- [PR #196](https://github.com/1uedev/task-manager/pull/196), Branch `claude/project-thread-b4u17z`, gemergt auf main als **`c22d6f75f36eb3dbe7f28721c537f5b440897d48`**. Dies ist die feste Prüfbasis.
- Maßstab: [Nachprüfung TASK-059](REVIEW_TASK-059_PR-193.md), R1 bis R3 und Hinweis 3; AGENTS.md, TODO.md (TASK-060 einschließlich Abnahmekriterien und Übergabe), DEC-088, backend/README.md und Nachtrag TASK-060 in docs/HELPDESK_SLA.md.
- Der Diff zum vorherigen main-Stand `463c3e4` umfasst 13 Dateien: die drei betroffenen Produktklassen, Tests und Testmodell sowie zugehörige Aufgaben-, Entscheidungs- und Dokumentationsangaben. Keine Migration, keine Vertragsänderung und keine zusätzliche Produktfunktion festgestellt.
- Ausschließlich synthetische Daten und lokale Testdienste. Die zusätzlichen Java-Methoden waren vorübergehende Tests und sind kein Bestandteil des Produkt- oder regulären Testcodes. Reproduzierbare Belege: [review-evidence/task-060](review-evidence/task-060/README.md).

## Was gelaufen ist

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | erfolgreich über den verfügbaren Python-Interpreter |
| `python3 scripts/task_board.py --check` | erfolgreich; Aufgabenübersicht unverändert |
| `python3 -m unittest discover -s tests -v` | 38 Tests ohne Fehler, ein Skip für den nur unter Unix möglichen Zeitzonenwechsel |
| Backend `clean verify`, Maven aus der Wrapper-Installation, JDK 25.0.4.1, PostgreSQL 18.6 lokal | **370 Tests, 0 Fehler, 0 übersprungen, BUILD SUCCESS**; darunter 18 `HelpdeskApiTest` einschließlich der vier neuen Fälle |
| Frontend `npm ci`, `npm run verify`, Node 24.22.0 | erfolgreich; Generierung, Typprüfung, 77 Tests und Build |
| Frontend `npm run test:browser`, installiertes Chrome über Playwright | erster Lauf 123/124; Wiederholung 124/124. Der erste Fehler betrifft den Sitzungsende-Test, siehe H2. |
| Vier neue `HelpdeskApiTest` gegen Produktcode **vor** PR #196 | alle vier erwartungsgemäß rot: 4 Assertion-Fehler, keine Ausführungsfehler; siehe Tabelle unten |
| Zusätzliche Java-Experimente E1–E6 auf der Prüfbasis | 6 ausgeführt: 5 bestanden, E4 scheitert an der geforderten Zurückhaltung einer verschachtelten Gruppe. E1 ist ein Beobachtungstest auf das fehlerhafte Verhalten. |
| E4 zur Eingrenzung wiederholt | erneut rot; erkannte Empfänger und sämtliche Warteschlangengründe protokolliert |
| E7: Browser-Testmodell, 16 Kombinationen der Feldrechte | neuer Stand 16/16 erwartungsgemäß; alter Stand 3/16 Abweichungen durch zu viele Kontakte |
| E8: direktes Verhalten des verwendeten Mail-Parsers | toleranter Modus übernimmt problematische Syntax; strenger Modus wirft bei den drei geprüften fehlerhaften Gruppen eine `AddressException` |
| `git diff --check` und abschließende Repositoryprüfung | erfolgreich; nur Bericht und Belege unter docs/ |

Die Windows-Sitzung verwendet Maven aus der Wrapper-Installation statt des Unix-Aufrufs `./mvnw`; kein Override auf Java 21 war nötig. Vorläufe mit fehlendem Pflichtwert für das synthetische Datenbankpasswort sowie einer parallelen Zusatztest-Kompilierung waren ungültig und wurden verworfen. Letztere verursachte fehlende Testklassen während des Gesamtlaufs. Auch der erste Python-Lauf scheiterte an lokalen Zugriffsrechten; der dokumentierte Wiederholungslauf benutzt erlaubte temporäre Dateien und lokale Testserver. Ein Typfehler im zusätzlichen Prüfcode wurde vor dessen sechs Experimenten korrigiert. Diese Probleme sind keine Produktbefunde.

Nicht gelaufen: Docker-Image, Compose-Betrieb, `lifecycle-check.sh`, JDK 21, Linux-Chromium, produktiver Mailweg sowie echter JVM-/IMAP-Serverabsturz oder Lease-Verlust. Der Wiederanlaufnachweis benutzt neue `MailIntake`-Objekte bei erhaltener PostgreSQL-Datenbank. Die deterministische Überlappung benutzt `ScriptedMailbox`, echte MIME-Verarbeitung, Transaktionen und Ticketanlage.

## Abgleich mit Umfang und Abnahmekriterien

| Gegenstand | Bewertung und Beleg |
|---|---|
| **R1: veralteter Stand verändert keine Position, UIDVALIDITY oder Abgleichgrenze** | **Für diese drei Zustandswerte behoben.** `position` verlangt Quelle und UIDVALIDITY und ändert letztere nie. `startFolder` verlangt dieselbe Quelle und bisher `null`; `restartFolder` dieselbe Quelle und den erwarteten alten Gültigkeitswert und erhöht die Quelle atomar. Der vorhandene Überlappungstest und E2/E3 bestätigen die relevanten Sperrbedingungen. Die weitergehende Aussage „endet ohne Schreiben“ ist wegen S1 nicht erfüllt. |
| **R1: zwei Abrufe sehen denselben Wechsel** | Der neue Test prüft einmaliges Erhöhen der Quelle, genau einen Audit-Eintrag und zwei statt vier Tickets. Ein zusätzlicher gleichzeitiger Erstabruf in E3 ergibt genau ein Ticket. Kein Deadlock beobachtet. |
| **R1: Rückkehr zu einem alten UIDVALIDITY** | E2 modelliert 100 → 200 → 100 bei noch angehaltenem Abruf aus Generation 1. Generation 3 behält Position 1 und Abgleichgrenze 2; die nächste UID 2 wird übernommen. Die Quelle schützt damit auch bei gleichem Gültigkeitswert vor einem alten Positionsupdate. |
| **R1: Einstellungen ändern sich während des Abrufs** | E3 ändert das Konto über die versionierte HTTP-API, während der alte Erstabruf nach Lesen der Einstellungen angehalten ist. Der neue Abruf übernimmt Quelle 2; der alte `startFolder` wird abgewiesen und übernimmt keine Mail. Bereits weiter fortgeschrittene alte Abrufe sind vor dem Speichern nicht entsprechend abgesichert, siehe S1. |
| **R1: Wiederanlauf ohne übersprungene oder doppelte Nachricht** | E6 bestätigt den persistierten Message-ID-Abgleich über Pakete, einen simulierten Fehler und neue Ausführerobjekte: sechs vorhandene Mails ergeben weiter sechs Tickets. Unter der in E1 geprüften Überlappung entsteht dagegen ein Duplikat. Deshalb **nicht uneingeschränkt erfüllt**. |
| **R2: Gruppen in To und Cc vollständig prüfen** | Normale und leere Gruppen, Anzeigenamen und Großschreibung funktionieren. Verschachtelte Gruppen und fehlender Abschluss können als vollständige Einzeladressen durchgehen: **teilweise behoben, S2**. |
| **R3: gemeinsame Grenze einschließlich `headerOnly` und mehrfacher Köpfe** | **Behoben in den geprüften Fällen.** Der gemeinsame `Recipients`-Zähler wird in beiden Einlesewegen für alle To- und Cc-Kopfzeilen benutzt. E5 kombiniert mehrere Kopfzeilen und Gruppen: 1000 vollständig, 1001 unvollständig; identische Empfänger und Vollständigkeit bei `parse` und `headerOnly`. |
| **Hinweis 3: Kundenauswahl im Browser-Testmodell** | **Behoben im geprüften Feldregelschema.** `writableHere` enthält schon nur lesbare und schreibbare Felder bei `ticket.edit`; der neue Kontaktfilter nutzt diese Schnittmenge. Kunden brauchen beide customer-Regeln, Kontakte zusätzlich beide contact-Regeln. E7 prüft alle 16 Kombinationen; die echten API-Tests prüfen die wesentlichen Berechtigungsfälle. |
| **Jeder bisherige Befund mit vorher rotem Regressionstest** | **Unabhängig bestätigt.** Die vier neuen Methoden wurden unverändert mit `ScriptedMailbox` in einen separaten Checkout des Vorgängerstands übernommen; dessen Produktcode blieb unverändert. Alle vier scheitern fachlich wie erwartet. |
| **Alle Backend-, Frontend- und Browsertests grün** | Reguläres Backend im sauberen Gesamtlauf und Frontend grün; Browser im zweiten Gesamtlauf grün, aber nicht in beiden Läufen. Der zusätzliche Randfalltest E4 bleibt wegen S2 rot. |
| **Nur R1 bis R3 und Testmodell** | Im Ergebnisdiff eingehalten; keine Migration oder zusätzliche Vertrags-/Produktfunktion. |

### Unabhängiger Rot-Nachweis

| Neue Methode in `HelpdeskApiTest` | Beobachtung auf `463c3e4` ohne Produktkorrektur |
|---|---|
| `aFetchOfTheOldUidValidityDoesNotMoveThePositionOfTheNewOne` | Position 100 statt 2 |
| `twoFetchesThatSeeTheSameNewUidValidityStartTheFolderOverOnce` | Quelle 1 statt 2 |
| `aContactOfAnotherCustomerInsideAGroupCounts` | keine Gründe statt zweimal `foreign_customer` |
| `theLimitOfRecipientsHoldsForToAndCcTogether` | keine Gründe statt `recipients_incomplete` bei beiden Überschreitungen |

## Befunde

### S1 · P2 · Ein überholter Abruf kann nach dem neuen Abruf dieselbe Mail ein zweites Mal zum Ticket machen

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:188`, `:191`, `:197`, `:205`; `backend/src/main/java/taskmanager/helpdesk/internal/HelpdeskRepository.java:331`.

Die Quell-/Gültigkeitsprüfung kommt erst beim Positionsupdate, **nachdem** `record` samt Ticketanlage bereits in einer eigenen Transaktion festgeschrieben ist. Der Message-ID-Abgleich und die Postfachsperre werden nur ausgeführt, wenn der Abruf sein lokales `compare` gesetzt hatte. Ein gewöhnlicher alter Abruf hat dieses Merkmal nicht. Die eindeutige Herkunft verhindert das Duplikat ebenfalls nicht: Alter und neuer Abruf verwenden unterschiedliche Quellen und UIDVALIDITY-Werte.

**Nachstellung E1:**

1. Postfach in Quelle 1, UIDVALIDITY 100, Position 0. Abruf A liest UID 100, wird aber vor Übergabe der Rohmail an die Verarbeitung angehalten. Die Mail hat eine gültige Message-ID und einen bestätigten bekannten Absender.
2. Abruf B sieht UIDVALIDITY 200 und dieselbe Rohmail als UID 1. Er beginnt Quelle 2 mit Abgleichgrenze 1. Da A noch nichts gespeichert hat, findet B keine Message-ID und legt das erste Ticket an.
3. A wird freigegeben. Sein `compare` ist falsch. Er legt dieselbe Nachricht unter Quelle 1 an und erzeugt ein zweites Ticket. Erst sein Positionsupdate wird abgewiesen.

**Beobachtet:** zwei Tickets und zwei Eingänge bei genau **einer** unterschiedlichen Message-ID. Der aktuelle Postfachstand bleibt korrekt bei Quelle 2, Position 1. Es handelt sich um ein Nachstellmodell der überlappenden Abrufe; ein tatsächlicher Jobs-Lease-Ausfall wurde nicht erzeugt.

Der neue vorhandene Überlappungstest verwendet verschiedene Nachrichten für alten und neuen Abruf und erwartet ausdrücklich vier Eingänge. Er belegt den Positionsschutz, aber nicht die zugesicherte Dublettenfreiheit beim verspäteten Schreiben derselben Mail. Die Einschränkung im PR-Text („höchstens eine Mail“, durch Herkunft und Message-ID nicht doppelt) reicht deshalb nicht aus.

**Vorschlag:** Vor dem Speichern in derselben Transaktion die aktuelle Abrufgeneration unter einer zum Ordnerneustart kompatiblen Postfachsperre prüfen. Ein überholter Abruf darf keinen Eingang und kein Ticket mehr schreiben. Eine separate Prüfung vor dem IMAP-Lesen reicht nicht, weil der Stand danach wechseln kann. E1 mit Erwartung „genau ein Ticket“ als Regression ergänzen; zusätzlich die umgekehrte Reihenfolge prüfen, in der der alte Abruf zuerst festschreibt und der neue ihn danach beim Abgleich erkennt.

### S2 · P2 · Tolerantes Lesen lässt verschachtelte und unvollständige Gruppen an der Empfängerprüfung vorbeikommen

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/ParsedMail.java:257`, `:261`, `:265`, `:266`, `:289`.

Sowohl `InternetAddress.parseHeader(value, false)` als auch `getGroup(false)` lesen tolerierend. Dabei wird fehlerhafte Gruppensyntax teilweise bereits als gewöhnliche Adresse dargestellt. Die anschließende Prüfung `member.isGroup()` erkennt dann keine verschachtelte Gruppe; eine `AddressException` wird ebenfalls nicht ausgelöst. `complete` bleibt wahr und der gesamte Text wird mit den einzelnen Kontaktadressen verglichen.

**Nachstellung E4:** Bestätigter Absender eines freigegebenen Kunden, Kontakt eines anderen Kunden in einer der folgenden Cc-Kopfzeilen:

```text
Cc: Outer: Inner: foreign@example.test;;
Cc: MissingEnd: foreign@example.test
```

Der echte Parser liefert beim ersten Beispiel `inner: foreign@example.test`, beim zweiten `missingend: foreign@example.test`, jeweils als vollständigen Empfänger. Beide Zeichenfolgen treffen die gespeicherte Kontaktadresse nicht. Die Verarbeitung über `ScriptedMailbox` und PostgreSQL erzeugt jeweils ein automatisches Ticket statt `recipients_incomplete`. Der zusätzliche Test scheitert an dieser Erwartung.

Zum Vergleich: Normale Gruppen in To/Cc mit Anzeigenamen und groß geschriebener Adresse liefern `foreign_customer`; eine leere Gruppe bleibt zulässig. Eine Gruppe mit nicht geschlossenem Anführungszeichen wurde ebenfalls als vollständig interpretiert, der Kontakt in diesem Beispiel aber noch erkannt und mit `foreign_customer` zurückgehalten. E8 zeigt für alle drei fehlerhaften Syntaxvarianten im strengen Parsermodus eine `AddressException`.

**Vorschlag:** Kopfzeilen und Gruppenmitglieder ohne die hier beobachtete stille Fehlerkorrektur einlesen und ungültige beziehungsweise nicht vollständig auswertbare Syntax als unvollständig kennzeichnen. Den strengen Modus für beide Ebenen prüfen. Regressionen für verschachtelte Gruppen, fehlendes Semikolon und nicht geschlossene Anzeigenamen ergänzen, auch in `headerOnly`. Fachlich muss nicht jede fehlerhafte Syntax rekonstruiert werden; die sichere Prüfwarteschlange genügt.

## Hinweise

### H1 · Grenzen der vorhandenen Regressionen und des Testadapters

Die vier neuen Tests sind aussagekräftig für ihre konkreten Fehlerbilder und waren unabhängig auf dem alten Produktstand rot. Sie decken jedoch weder die gleiche Message-ID in den überlappenden Generationen noch verschachtelte/unvollständige Gruppen ab. `ScriptedMailbox.header` (`backend/src/test/java/taskmanager/helpdesk/internal/ScriptedMailbox.java:95`) ruft `ParsedMail.parse` auf und prüft somit nicht den echten `headerOnly`-Pfad. E5 ergänzt diesen direkten Vergleich; ein produktiver IMAP-Abruf übergroßer Mails wurde nicht wiederholt.

### H2 · Browserlauf nicht durchgehend stabil

Im ersten vollständigen Lauf scheiterte ausschließlich `a session the server ended leads back to the login on the next request` (`frontend/tests/browser/app.test.mjs:126`): Beim Klick auf „Projekte“ wurde der Link währenddessen aus dem DOM entfernt, danach lief das 30-Sekunden-Limit ab. Der zweite vollständige Lauf besteht mit 124/124. Kein reproduzierbarer TASK-060-Produktfehler nachgewiesen; die Beobachtung wird als Teststabilitätshinweis festgehalten. Die Helpdesk-Browserfälle bestanden in beiden Läufen.

### H3 · Präzisierung der Dokumentation nach S1 erforderlich

DEC-088, TODO-Übergabe, README und SLA-Nachtrag sagen sinngemäß, dass ein überholter Abruf ohne Schreiben endet. Belegt ist derzeit nur, dass er keine Position, UIDVALIDITY und Abgleichgrenze mehr überschreibt. S1 zeigt einen bereits festgeschriebenen Eingang samt Ticket. Nach der Korrektur sollten Dokumentation und Regressionstest denselben verbindlichen Zeitpunkt für das Verwerfen eines überholten Abrufs beschreiben.

H2/H4 der Erstprüfung von TASK-057 bleiben Holgers Entscheidung bei der Abnahme vorbehalten; TASK-058 und die übrigen nicht beauftragten Punkte werden hier nicht neu bewertet.
