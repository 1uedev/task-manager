# Prüfung TASK-062 / PR #206

Stand: main nach PR #206, Commit `e975fcba20ff75581e11421e66e1f348082a8b1f`. Bericht vom 30. Oktober 2026, Sitzung `codex-20261030-task062-review-01`.

Hinweis zur Ablage: Codex konnte den Bericht nicht selbst veröffentlichen. Holger hat ihn am 30. Oktober 2026 im Projektgespräch übergeben; die Sitzung claude-20261030-replyfix-01 legt ihn unverändert ab. Die Prüfung lief in drei Teilen mit vorgegebenem Lesestoff (Prüfaufträge Teil 1 bis 3), ohne Testausführung.

**Ergebnis:** Noch keine Empfehlung zur gemeinsamen Abnahme von TASK-058 und TASK-062. Die Korrekturen schließen die gelesenen Wege von alten Versandaufträgen in neue Runden und schützen den automatischen Kundenvergleich. Offen bleiben die in Teil 1 beschriebene Absicherung gegen administrative Job-Wiederholung (F1, P2) sowie drei P3-Punkte zur Kopfzeilenprüfung und Aussagekraft der Tests. Es handelt sich um eine begrenzte statische Prüfung, ohne Testausführung oder Produktcodeänderung.

Die Abschnitte aus Teil 1 und Teil 2 des Chats sind nachfolgend zusammengeführt; die Befunde sind für den Gesamtbericht fortlaufend nummeriert. Frühere Testergebnisse aus TASK-058 und die in TODO genannten Ergebnisse der Umsetzung werden nicht als eigene Prüfungen dieses Berichts ausgegeben.

## Teil 1 – Versand und verwaiste Antworten

### F1 · P2 · Verwaistenklärung ist nicht gegen gleichzeitige Job-Wiederholung abgesichert

`settleOrphan` hält die Antwortsperre, verwendet aber `Jobs.active` als Zustandsabfrage. Der gelesene Portvertrag garantiert keine gemeinsame Sperre mit der Jobverwaltung. Möglich bleibt nach diesem Vertrag: `active` liefert `false` → Administration wiederholt den Auftrag → Auftrag wartet oder läuft wieder → Antwort wird trotzdem `failed` beziehungsweise `uncertain`. Ein anschließendes `prepare` kann dadurch ohne Versand zurückkehren. Die administrative Wiederholung bleibt wirkungslos.

Ist der Auftrag beim Lesen bereits `queued` oder `running`, verändert `settleOrphan` die Antwort dagegen nicht. Der Einwand betrifft das Zeitfenster zwischen Lesen und Schreiben.

**Empfehlung:** Klärung und Wiederholung gemeinsam atomar absichern; eine zweite ungesicherte Zustandsabfrage genügt nicht. Alternativ eine bereits vorhandene gemeinsame Sperrsemantik nachweisen. Die internen Wiederholungs- und Repositorymethoden des Jobs-Moduls waren nicht Teil des freigegebenen Lesestoffs. Der Befund bezeichnet deshalb eine offene Absicherung im gelesenen Ablauf und Portvertrag, keinen dynamisch nachgestellten Fehler.

**Stellen:** `ReplyService.java:265–292`, `Jobs.java:19–23`, `JobService.java:52–54`.

### X-F1 – Schutz neuer Versandrunden

Im geprüften Code behoben: `prepare` prüft unter der Antwortsperre zuerst die Job-ID, bevor es Zustände verändert. `finish` verlangt unter derselben Sperre sowohl `sending` als auch die passende Job-ID. Eine manuell neu bestätigte Runde erhält über `enqueue` einen Auftrag mit dem Schlüssel der neuen Runde. Ein Weg, auf dem ein alter Auftrag diese neuere Runde verändert, ist hier nicht erkennbar.

**Stellen:** `ReplySender.java:117–130,185–192`, `ReplyService.java:173–174,251–253`.

### H1 – Bestätigende Person und Altaufträge

`sendAgain` übergibt die aktuell bestätigende Person als `by`; der Worker prüft deren Aktivität und Mitgliedschaft. Der dokumentierte Rückfall auf den Urheber bei Altaufträgen **ohne `by` ist als Übergangsregel vertretbar**. Die damalige bestätigende Person lässt sich daraus nicht zuverlässig rekonstruieren.

Allerdings fällt auch ein vorhandenes, ungültiges `by` still auf den Urheber zurück. Diesen Datenfehler sollte der Worker ausdrücklich zurückweisen. Die neue Bestätigung darf ihre Bedeutung nicht durch eine fehlerhafte Nutzlast verlieren.

**Stellen:** `ReplySender.java:155–172`, `ReplyService.java:251–253`.

## Teil 2 – Adressen, Kopfzeilen und SMTP-Phasen

### F2 · P3 · Die Zeilengrenze ist für Betreff und Namen nicht vollständig abgesichert

`message` übergibt Betreff und Namen an Jakarta Mail, ohne die fertigen Kopfzeilen auf 998 Zeichen zu prüfen. Besonders lange ASCII-Folgen ohne trennbare Leerzeichen benötigen eine gezielte Prüfung. Der vorhandene Längentest verwendet kurze Namen und einen kurzen Betreff; er belegt ausschließlich die Faltung langer `References`.

**Empfehlung:** Lange Betreff- und Namenswerte einschließlich nicht trennbarer ASCII-Folgen als Regression prüfen und die fertigen Kopfzeilen begrenzen beziehungsweise ungültige Eingaben sicher zurückweisen. Die Erreichbarkeit solcher Eingaben über die Oberfläche wurde im vorgegebenen Umfang nicht geprüft. Es wurde keine tatsächlich überlange versandte Nachricht nachgestellt.

**Stellen:** `SmtpMailSender.java:172–210`, `SmtpMailSenderTest.java:89–102`.

### H2 – Sichere, aber eingeschränkte Adressregel

Kommentare, Gruppen, eingebettete Steuerzeichen und CR/LF werden ausgeschlossen; der Versand prüft gespeicherte Adressen erneut. Gültige Adressen wie `"lea lang"@example.org`, Domainliterale und internationale Adressen werden jedoch ebenfalls abgelehnt. Das entspricht der ausdrücklich gewählten Einschränkung auf einfache Adressen, begrenzt aber die Erreichbarkeit echter Kunden. Zusätzlich wird der gesamte lokale Teil kleingeschrieben; dessen Groß-/Kleinschreibung darf ein empfangender Server unterscheiden.

Die Kompatibilitätsgrenze sollte bei der Abnahme bewusst bestätigt werden. Es wurde keine konkrete Zustellung an eine andere Person nachgewiesen.

**Stellen:** `DirectoryService.java:30–32,199–214`, `SmtpMailSenderTest.java:36–58`.

### H3 – Kopfzeilen überwiegend abgesichert

Namen werden von ASCII-Steuerzeichen bereinigt und kodiert. Message-IDs sind auf höchstens 252 druckbare ASCII-Zeichen begrenzt; `References` wird je ID gefaltet. Der frühere Injektionsweg über Adresskommentare ist geschlossen. Für den Betreff enthält der Sender selbst keine ausdrückliche CR/LF-Prüfung; die Absicherung seiner Eingabe liegt außerhalb der für Teil 2 gelesenen Stellen.

### C-F4 – Einordnung der SMTP-Phasen

Kein Weg „angenommen → sicher gescheitert“ festgestellt: `finishData` setzt `ended` **vor** dem Bibliotheksaufruf. Ein Verbindungsfehler während Datenabschluss oder Schlussantwort wird dadurch konservativ `uncertain`. Vor dieser Grenze ist Wiederholung vertretbar; eine ausdrückliche SMTP-Ablehnung wird als sicher gescheitert behandelt. Fehler beim anschließenden Schließen ändern das Ergebnis nicht.

Verbindungsaufbau mit standardmäßig 20 Sekunden und Antworten mit zehn Minuten sind sinnvoll getrennt. Das dokumentierte Auftragslimit von 15 Minuten garantiert allerdings keine vollen zehn Minuten für die Schlussantwort, wenn frühere Phasen bereits viel Zeit verbraucht haben. Die einzelnen Zeitlimits ergeben zusammen keine garantierte Gesamtlaufzeit.

**Stellen:** `SmtpMailSender.java:45–136`, `HelpdeskProperties.java:16–34`, DEC-092.

## Teil 3 – Kundengrenze und Tests

### X-F4 – Gemeinsame Sperre schützt den automatischen Kundenvergleich

Der gelesene Ablauf ist schlüssig: `MailIntake.record` nimmt die transaktionsgebundene Advisory-Sperre des Tickets, sperrt den Kontakt mit `FOR SHARE` und prüft anschließend sowohl dessen Kundenorganisation als auch den aktuellen Ticketkunden erneut. Erst danach wird angehängt. Bei Abweichung setzt `reviewWithTicket` die Mail auf `review` mit `reply_customer_mismatch` und Ticketverweis.

`TicketCustomerService` nimmt dieselbe Ticketsperre vor dem Lesen und Ändern der Zuordnung; `ReviewService.attach` nimmt sie vor dem Vergleich und Anhängen. Damit ist der frühere automatische Vergleich gegen einen zwischenzeitlich gewechselten Ticketkunden im gelesenen Ablauf abgesichert. Ein späterer, nach dem Anhängen erfolgender Kundenwechsel bleibt eine eigenständige berechtigte Änderung.

**Sperrreihenfolge:** Im automatischen Pfad ist sichtbar: Advisory-Sperre des Tickets → Kontakt-Zeilensperre → Aufruf zum Anhängen. Der Kundenwechsel nimmt die Advisory-Sperre vor dem späteren Ticket-Sperraufruf. Daraus ergibt sich zwischen diesen beiden Ausschnitten kein gegenläufiger Sperrzyklus.

Ein allgemeiner Ausschluss von Deadlocks mit Postfach, Ticket oder Kontakt ist damit **nicht nachgewiesen**: Der frühere Teil von `record`, die Implementierung des Anhängens, `lockForChange` und die Kontaktänderungspfade waren ausdrücklich nicht zum Lesen vorgegeben. `attach` verwendet außerdem keine geteilte Kontaktsperre; seine explizite manuelle Zuordnung ist nicht derselbe Ablauf wie die automatische Kundenprüfung. Aus den erlaubten Ausschnitten wird kein konkreter Deadlock behauptet.

**Stellen:** `MailIntake.java:258–267`, `HelpdeskRepository.java:399–420`, `TicketCustomerService.java:126–157`, `ReviewService.java:141–164`.

### F3 · P3 · Der Kundengrenzentest prüft nicht beide vollständigen Commit-Reihenfolgen

Die erste Hälfte von `aChangeOfTheCustomerDuringTheComparisonSendsTheAnswerToReview` startet den echten Eingang, hält die gemeinsame Sperre und ändert den Kunden per SQL vor dessen Fortsetzung. Sie prüft den Abweichungsgrund, Ticketverweis und die unveränderte Zahl angehängter Mails. Das ist ein sinnvoller Nachweis für „Kundenwechsel zuerst“, auch wenn der Wechsel nicht über die Produkt-API erfolgt.

In der zweiten Hälfte hält dagegen nur eine Hilfsverbindung die Advisory-Sperre. Sie führt weder Kundenvergleich noch Anhängen einer Mail durch. Der echte HTTP-Kundenwechsel muss warten und wird danach erfolgreich abgeschlossen. Das belegt die Benutzung des Sperrschlüssels durch die API, **nicht** „echtes Anhängen committet zuerst, Kundenwechsel danach“. Auch ein expliziter Assert auf `state = review` fehlt in der ersten Hälfte.

**Empfehlung:** Die zweite Reihenfolge mit einer tatsächlich anhängenden Transaktion prüfen und Mailzuordnung sowie anschließenden Kundenwechsel kontrollieren.

**Stelle:** `HelpdeskApiTest.java:1578–1635`.

### F4 · P3 · SMTP-Testserver zählt auch einen Abbruch vor dem Datenende als Übergabe

`ScriptedSmtp` beendet die Datenschleife sowohl bei der Abschlusszeile `.` als auch bei EOF und trägt anschließend in beiden Fällen die Mail in `handedOver` ein. Das widerspricht dem dokumentierten Sinn „bis zum Ende gelesen“. Bei einem Verbindungsende während des Inhalts kann die Zählung eine vollständige Übergabe vortäuschen.

**Empfehlung:** Nur eine tatsächlich empfangene Abschlusszeile als Übergabe zählen; abgebrochene Inhalte getrennt erfassen. Der neue Test `DROP_AT_DATA` prüft den Abbruch vor der Datenübertragung, nicht innerhalb des Inhalts.

**Stellen:** `ScriptedSmtp.java:109–118`, `HelpdeskApiTest.java:1548–1561`.

### H4 – CI-Stabilität: sinnvolle Barrieren, aber knappe Zeitreserven

- `HELD` und die beobachtete Übergabeliste ordnen die Schritte besser als feste kurze Wartezeiten. Die SMTP-Lesezeit läuft während der administrativen Zwischenaktionen jedoch weiter. Unter Last kann der Client vor `release()` auslaufen; das spätere `Future.get(30s)` verlängert dieses SMTP-Zeitlimit nicht.
- `SLOW_ACCEPT` schläft sechs Sekunden. Laut Testkommentar stehen fünf Sekunden Verbindungs- und acht Sekunden Antwortzeit gegenüber: Die Reserve zur Antwortgrenze beträgt damit nur zwei Sekunden. Eine verspätete Einplanung des Serverthreads kann einen Ausreißer verursachen. Die tatsächliche Bindung dieser Testeinstellungen liegt außerhalb des freigegebenen Testabschnitts.
- Die Abfragen von `pg_locks` suchen datenbankweit irgendeine nicht gewährte Advisory-Sperre. Sie identifizieren weder diesen Ticketschlüssel noch den erwarteten wartenden Prozess. Fremde Wartevorgänge können das vermeintliche Erreichen der Barriere vorzeitig bestätigen. Auf Schlüssel und beteiligte Verbindungen begrenzen.
- `awaitTrue` und Futures haben endliche Grenzen von 20 beziehungsweise 30 Sekunden; `HELD` wird beim Schließen freigegeben. `ScriptedSmtp.close` wartet aber nicht auf das Ende seines Threads und schließt keinen bereits angenommenen Socket ausdrücklich. Vollständiges Aufräumen nach Fehlern ist damit nicht abgesichert.

Das sind aus dem Code abgeleitete Stabilitätsrisiken, keine in dieser Prüfung beobachteten CI-Fehlschläge.

### H5 – Umfang der übrigen Regressionen

Die Tests der verwaisten Antworten stellen den Zustand eines verlorenen letzten Versuchs per SQL her und benutzen anschließend die echte Lease-Freigabe und den Watch. Sie prüfen `sending → uncertain`, `pending → failed`, einen administrativen Abbruch und die Wiederherstellung. Ein tatsächliches Prozessende wird nicht ausgelöst. Der Kommentar „While the leases last“ ist ungenau: Die Lease wurde bereits abgelaufen gespeichert; noch nicht ausgeführt ist deren Freigabe.

Der Test alter Versuche hält echte SMTP-Schlussantworten zurück und prüft zwei Reihenfolgen von Annahme und fehlender Antwort. Er prüft insbesondere den Schutz in `finish`, aber keinen verspäteten Eintritt eines alten Auftrags in `prepare` und keine verspätete ausdrückliche Ablehnung. Der Schutz in `prepare` wurde in Teil 1 statisch geprüft.

Der Vertretungstest prüft Versand durch eine andere bestätigende Person nach Austritt des Urhebers sowie die Verweigerung nach Austritt der bestätigenden Person. Die Adresstests prüfen API-Ablehnung und eine frühere gespeicherte Kommentaradresse ohne SMTP-Verbindung. Diese Aussagen werden durch ihre Assertions getragen.

### H6 – Watch-Batch begrenzt die Wiederherstellungsgarantie

`unsettledReplies(200)` wählt die ältesten `pending`/`sending`-Antworten, unabhängig davon, ob ihre Jobs noch aktiv sind. Bleiben die ersten 200 aktiv und unverändert, werden spätere verwaiste Antworten in folgenden Watch-Läufen weiterhin nicht ausgewählt. „Jede Minute ausgeführt“ bedeutet daher keine garantierte Klärung jeder verwaisten Antwort innerhalb einer Minute.

Die Auswahl benötigt eine Fortsetzung beziehungsweise faire Rotation oder eine geeignete Auswahl verwaister Kandidaten. Die Klärung bei einer Aktion am Ticket bleibt davon unabhängig.

**Stellen:** `HelpdeskRepository.java:478–480`, `ReplyWatch.settle` aus Teil 1.

## Gelesen und ausgeführt

| Teil | Gelesener Umfang |
|---|---|
| 1 | TASK-062 aus TODO; DEC-092; Absatz zu TASK-062 im Backend-README; ReplySender und ReplyWatch; sendAgain, settle, lockForChange, settleOrphan, enqueue aus ReplyService; Jobs und JobService.active |
| 2 | DEC-092; SmtpMailSender; PLAIN_ADDRESS und normalizedAddress aus DirectoryService; HelpdeskProperties; SmtpMailSenderTest |
| 3 | record ab „The answer of a confirmed contact“; lockTicketCustomer, shareContact, reviewWithTicket, unsettledReplies aus HelpdeskRepository; Sperrstelle in TicketCustomerService; ReviewService.attach; neuer TASK-062-Testabschnitt aus HelpdeskApiTest; ScriptedSmtp |

Die Quelltexte wurden über GitHub gelesen und in Teil 1 mit main nach PR #206 abgeglichen. Für die Ablage wurde eine getrennte lokale Prüfkopie desselben Commits angelegt. Es wurden keine Testklassen, vollständigen Testläufe oder CI-Auswertungen ausgeführt. Abschlusskontrolle: ausschließlich neue Berichtsdatei und Prüfung auf überflüssige Leerzeichen.

## Empfehlung zur gemeinsamen Abnahme

**TASK-058 und TASK-062 noch nicht gemeinsam zur Abnahme empfehlen.** Zuerst F1 durch eine atomare Absicherung oder einen belastbaren Nachweis der gemeinsamen Sperrsemantik klären. F2 bis F4 gezielt ergänzen beziehungsweise korrigieren; insbesondere die zweite echte Commit-Reihenfolge und den Unterschied zwischen vollständigem Datenende und EOF nachweisen. Die Zeitreserven und die Zuordnung der Datenbank-Wartebarrieren stabilisieren.

Die Korrekturen für neue Versandrunden, bestätigende Person, einfache Versandadressen, gefaltete Referenzen, konservative SMTP-Phasen und den automatischen Kundenvergleich sind im gelesenen Umfang nachvollziehbar. Die Adressbeschränkung, der Altauftragsrückfall und die begrenzte Watch-Auswahl bleiben bewusst zu bewertende Hinweise. Eine umfassende Deadlock-Freigabe oder bestandene Regressionen werden mit dieser begrenzten Prüfung nicht behauptet. Die fachliche Abnahme bleibt bei Holger.
