# Prüfung TASK-058 / PR #202

Ergebnis: **Der Kern hält. Genau ein Versandauftrag je Bestätigung, keine automatische Wiederholung einer möglicherweise angenommenen Mail und die Rechte bei der Bestätigung sind in Code und Nachstellung belegt. Es gibt einen P2 Befund (F1) und drei P3 Befunde (F2 bis F4), dazu acht Hinweise.** F1: Endet der letzte Versuch eines Auftrags ohne vermerkten Ausgang oder bricht die Jobverwaltung einen wartenden Auftrag ab, bleibt die Antwort für immer „ausstehend“, und die Helpdesk Oberfläche bietet keinen Ausweg. Eine Antwort an den Kunden kann so unbemerkt liegen bleiben.

Alle angeforderten Prüfungen sind grün: Backend 380 Tests, Frontend 77 Tests und 128 Browser Tests. Keine Abnahme und keine Änderung des Aufgabenstatus.

Stand: 29. Oktober 2026. Claude, Sitzung `claude-20261029-task058-review-01`, getrennte technische Prüfung nach DEC-040 anstelle von Codex, auf Holgers Wunsch. Die Umsetzung stammt aus `claude-20261028-helpdesk-01`; diese Sitzung hat daran nicht mitgearbeitet.

## Grundlage und Abgrenzung

- [PR #202](https://github.com/1uedev/task-manager/pull/202), gemergte Prüfbasis auf main: **`ab9a6eea5589b4489437bf6fe31064e20dd5c74f`** (Squash aus Ergebnis 8c1335f und CI Korrektur 7283009).
- Maßstab: AGENTS.md, TODO.md (TASK-058, Abnahmekriterien 1 bis 8), DEC-091, backend/README.md (Abschnitt „Helpdesk Antworten (TASK-058)“), contracts/helpdesk.openapi.yaml und der Prüfauftrag mit sieben Schwerpunkten.
- Durchgesehen: `ReplyService`, `ReplySender`, `SmtpMailSender`, `MailSender`, `Reply`, die Änderungen in `MailIntake`, `ParsedMail`, `ReviewService`, `TicketOpener`, `HelpdeskRepository`, `TicketCustomerService`, die drei Controller, Migration V0028, die betroffenen Wege im Modul `jobs` (`JobExecutor`, `JobRepository.claim`, `releaseExpired`, `cancel`, `JobAdministration.retry`), `TicketHelpdesk.tsx`, `InboxPage.tsx` sowie die neuen Backend und Browser Tests.
- Ausschließlich synthetische Daten. Kein Produktcode verändert; Nachstellungen liefen in einem getrennten Arbeitsverzeichnis. [Nachstellcode und Ergebnisse](review-evidence/task-058/README.md).

## Was gelaufen ist

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | erfolgreich |
| `python3 scripts/task_board.py --check` | erfolgreich, Übersicht aktuell |
| `python3 -m unittest discover -s tests` | 38 Tests ohne Fehler |
| Backend `./mvnw -Djava.version=21 verify`, JDK 21.0.12, PostgreSQL 16 lokal über `-Dtaskmanager.test.jdbc-url` | **380 Tests, 0 Fehler, BUILD SUCCESS**, darin die sechs neuen Antworttests und `HelpdeskContractTest` |
| Frontend `npm ci` und `npm run verify`, Node 22.22.0 | erfolgreich: Generierung, Typprüfung, 77 Tests, Build |
| Frontend `npm run test:browser` mit Chromium aus `/opt/pw-browsers` | **128/128 erfolgreich**, darin die vier neuen Antworttests |
| Nachstellungen E1 bis E4 (Backend, echte Datenbank, echte HTTP Wege) | 4/4 bestätigen die beschriebenen Beobachtungen, siehe F1 bis F3 |
| SMTP Nachstellungen S1 bis S5 (echtes `SmtpMailSender` gegen lokalen Server) | Einordnung wie unter F4 und Schwerpunkt 2 beschrieben |
| Faltung der Kopfzeilen mit Jakarta Mail 2 | `setHeader("References", …)` faltet nicht; 20 IDs ergeben 1031 bis 1074 Zeichen in einer Zeile |

Nicht gelaufen: JDK 25 und PostgreSQL 18.6 aus dem gepinnten Container, `backend/scripts/lifecycle-check.sh` mit Docker (nur Syntaxprüfung), ein echter Mailserver mit TLS, ein echter Prozessabbruch und zwei echte Ausführer gleichzeitig. Die Fälle mit verlorenem Versuch sind über den Datenbankzustand nachgebildet, den `JobRepository` in diesen Lagen schreibt.

## Abgleich mit den Abnahmekriterien

| Kriterium | Bewertung |
|---|---|
| **1. Rechte, Empfänger, Absender, Prüfschritt** | Erfüllt. `requireReply` antwortet ohne `ticket.read` mit 404, ohne `helpdesk.reply` oder Leseregel für `mail` oder `contact` mit 403 (`ReplyService.java:271`). Empfänger nur aus den Adressen des Kontakts, nicht die eigene Postfachadresse; Absender nur `sends()`. Ohne Kontakt 409. Die Oberfläche zeigt Absender, Empfänger, Betreff und Text vor der Bestätigung; erst „Bestätigen“ sendet den POST. |
| **2. Genau ein Auftrag je Bestätigung** | Erfüllt. Antwort, Historie, Audit und Auftrag in einer Transaktion (`ReplyService.java:110` bis `:148`); neue Kennung je Bestätigung, Schlüssel `reply:<id>:<n>`; erneutes Senden unter Zeilensperre und Version. Ein zweiter Ausführer oder ein erneuter Lauf findet `sending` oder einen anderen Auftrag und sendet nicht (`ReplySender.java:111` bis `:124`). Grenze: zwei Bestätigungen sind zwei Antworten, siehe H2. |
| **3. Prüfung im Auftrag** | Erfüllt für Projekt, aktiven Urheber, Mitgliedschaft, Postfach und Empfänger (`ReplySender.java:149`). Die geprüfte Person ist immer der ursprüngliche Urheber, auch nach erneutem Senden durch eine andere Person, siehe F2. Die Grenze zu Gruppenrechten ist dokumentiert und vertretbar, weil der Zeitraum zwischen Bestätigung und Versand kurz ist; nach einem erneuten Senden gelten die Rechte der erneut sendenden Person zum Zeitpunkt ihrer Bestätigung. |
| **4. Fehlerarten** | Im Kern erfüllt: Ablehnung mit Code nach dem Datenende ist sicher (S3, S4), fehlende Antwort nach dem Datenende ist unklar (S5, Test `DROP_AFTER_DATA`), ein Versuch, der `sending` findet, wird unklar ohne Versand. Zwei Lücken: Endet der **letzte** Versuch ohne Ausgang, kommt kein Versuch mehr, der `sending` findet (F1). Abbrüche vor dem Datenende gelten unnötig als unklar (F4). |
| **5. Erneut senden und festhalten** | Erfüllt für `failed` und `uncertain`; Duplikatbestätigung serverseitig erzwungen (409 `helpdesk.reply_uncertain`). Kein Ausweg für hängende `pending`/`sending` Antworten (F1). |
| **6. Zuordnung eingehender Antworten** | Erfüllt. Zuordnung nur über eine Message-ID einer Antwort desselben Projekts, nur wenn die Mail ohnehin ein Ticket würde (bestätigter Absender eines bekannten Kontakts) und der Kunde dem des Tickets entspricht; sonst Prüfwarteschlange mit `relatedTicket`, Grund `reply_customer_mismatch` bei anderem Kunden. Die eigene Message-ID ist eine zufällige UUID; ein Fremder kann sie nicht erraten, und selbst mit Kenntnis der ID landet seine Mail nur in der Prüfwarteschlange. |
| **7. Automatische Mails, eigene Antwort, Schleifen** | Erfüllt. Es gibt keinen automatischen Versand; Autoresponder werden `automatic`, eine zurückkommende eigene Antwort `own_reply`. Siehe H1 zur Erkennung allein über die Message-ID. |
| **8. Sichtbarkeit und Protokoll** | Erfüllt. `GET /helpdesk/replies` filtert über Mitgliedschaft und `mayReply`; das Protokoll nennt Antwort, Code und Ausnahmeklassen, `JobFailure` trägt keine Ursache mit Servertext. |
| **Kriterien selbst** | Kriterium 4 und 5 sollten ausdrücklich verlangen, dass jede Antwort einen Endzustand erreicht oder eine Person sie auflösen kann, auch wenn der Auftrag selbst endet (F1). |

### Schwerpunkt 2: Einordnung der SMTP Fehler

Gefahr eines Doppelversands besteht nur, wenn eine angenommene Mail als sicher gescheitert gilt. Das ist nicht der Fall: Als sicher gelten nur Fehler beim Aufbau und bei der Anmeldung (`SmtpMailSender.java:60` bis `:73`, vor jeder Übergabe) und `SendFailedException` mit Code 400 bis 599 (`:76` bis `:85`). Angus wirft diese bei Ablehnung von `MAIL FROM`, `RCPT TO`, `DATA` und der Antwort auf das Datenende; alle bedeuten nach RFC 5321, dass der Server die Mail nicht übernommen hat. Die Option `sendpartial` ist nicht gesetzt, also gibt es keine Ausnahme nach einer Annahme. Jede andere `MessagingException` während `sendMessage` gilt als unklar (`:86`). S3 bis S5 bestätigen das.

Zeitlimit, Stopp und Prozessende: Der Wächter des Jobmoduls unterbricht den Thread, blockierende Socketaufrufe bleiben bis zum Zeitlimit von 20 s je Schritt; der Ausgang wird erst danach vermerkt. Eine abgelaufene Lease führt zu einem neuen Versuch, der `sending` findet und unklar vermerkt. Ein verspätetes `finish(sent)` überschreibt `uncertain` nicht (`ReplySender.java:169`); das ist vorsichtig richtig. Offen bleibt nur der letzte Versuch (F1).

## Befunde

### F1 · P2 · Eine Antwort kann dauerhaft „ausstehend“ bleiben, ohne Auftrag und ohne Ausweg in der Helpdesk Oberfläche

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/ReplySender.java:117`, `ReplyService.java:158` und `:184`, `backend/src/main/java/taskmanager/jobs/internal/JobRepository.java:142`, `JobAdministration.java:82`.

Die Umwandlung `sending` zu `uncertain` hängt davon ab, dass derselbe Auftrag noch einmal läuft. Das geschieht nicht, wenn

1. der Prozess im **letzten** Versuch nach dem Vermerk `sending` endet: `releaseExpired` setzt den Auftrag bei `attempts >= max_attempts` auf `failed`, ein weiterer Versuch kommt nicht;
2. ein Handler im letzten Versuch mit einer anderen Ausnahme als `JobFailure` endet, etwa einem Datenbankfehler in `finish` nach dem Gespräch mit dem Server (Zustand `sending`) oder in `prepare` (Zustand `pending`);
3. eine Person mit `jobs.manage` einen wartenden Auftrag zwischen zwei Versuchen abbricht (Zustand `pending`).

In allen Fällen zeigen Ticket und Übersicht weiter „ausstehend“. `POST …/send` antwortet mit 409 `helpdesk.reply_not_failed`, `POST …/settle` mit 409 `helpdesk.reply_not_uncertain`. Auflösen kann es nur, wer `jobs.manage` hat, über „erneut ausführen“ in der Jobverwaltung; das Helpdesk Team erfährt davon nichts. Im Fall 1 hat der Kunde die Mail vielleicht erhalten, im Fall 2 und 3 sicher nicht, und niemand wird darauf hingewiesen.

**Nachstellung E1/E2:** genau so beobachtet, siehe Belege. Wahrscheinlichkeit: Fall 1 und 2 brauchen vier vorausgegangene sichere Fehlschläge, Fall 3 nur einen Klick in der Jobverwaltung.

**Vorschlag:** Den Zustand der Antwort an den Auftrag koppeln, nicht an einen weiteren Versuch. Zum Beispiel behandelt `sendAgain` und `settle` eine Antwort in `pending` oder `sending` wie `uncertain` beziehungsweise `failed`, wenn ihr Auftrag `failed` oder `cancelled` ist (Abfrage über einen Port des Jobmoduls), oder eine wiederkehrende Aufgabe setzt solche Antworten auf `uncertain` (`sending`) beziehungsweise `failed` (`pending`, Code `helpdesk.job_ended`). Regressionstest für alle drei Fälle.

### F2 · P3 · Erneutes Senden durch eine Kollegin scheitert, sobald der ursprüngliche Urheber nicht mehr aktiv oder Mitglied ist

**Datei:** `ReplySender.java:154`; `ReplyService.java:156`.

`sendAgain` prüft die Rechte der erneut sendenden Person, der Auftrag prüft aber Aktivität und Mitgliedschaft von `reply.createdBy()`. Hat der Urheber das Projekt verlassen oder ist deaktiviert, nimmt der Server das erneute Senden mit 200 an, der Auftrag endet ohne Versand mit `helpdesk.author_inactive` (E3). Die Antwort lässt sich dann nie mehr senden; das Team muss sie neu schreiben. Das ist gerade dann ärgerlich, wenn jemand das Unternehmen verlässt und Antworten liegen bleiben.

**Vorschlag:** Die Person der letzten Bestätigung speichern (`sent_again_by` oder `updated_by` beim erneuten Senden) und diese im Auftrag prüfen; DEC-091 Punkt 3 entsprechend formulieren.

### F3 · P3 · `References` wird nicht gefaltet und überschreitet bei längeren Verläufen die Zeilengrenze von 998 Zeichen

**Datei:** `SmtpMailSender.java:137` und `:140`; `ReplyService.java:138` bis `:142` und `:227`.

`setHeader` übernimmt den Wert unverändert in eine Zeile. Mit 20 Message-IDs (die Obergrenze) entstehen bei kurzen eigenen IDs schon 1031 bis 1074 Zeichen (E4); mit üblichen IDs von Outlook oder Gmail etwa 1400 bis 1700, mit langen eingehenden IDs bis zu etwa 20000. RFC 5322 Abschnitt 2.1.1 verbietet Zeilen über 998 Zeichen. Strenge Server lehnen die Mail mit 5xx ab (die Antwort endet dann dauerhaft als `failed`), andere brechen die Zeile um und verfälschen den Kopf. Typisch tritt das ab etwa 13 bis 18 Mails und Antworten am Ticket auf, also gerade bei langen Fällen. Nebenbefund: eingehende Message-IDs mit Zeichen außerhalb von ASCII werden ungeprüft in `In-Reply-To` und `References` übernommen.

**Vorschlag:** IDs mit `"\r\n "` trennen oder `MimeUtility.fold` verwenden, nur IDs aus druckbarem ASCII übernehmen, Test auf Zeilenlänge.

### F4 · P3 · Unklar wird häufiger vermerkt als nötig

**Datei:** `SmtpMailSender.java:44` bis `:46` und `:86`; `HelpdeskProperties.java:24`.

1. Für die Antwort auf das Datenende gilt dasselbe Zeitlimit von 20 s wie für jeden anderen Schritt. RFC 5321 Abschnitt 4.5.3.2.6 empfiehlt hier 10 Minuten, weil Server die Mail an dieser Stelle prüfen (Viren, Spam). Ein Server, der nach 25 s mit 250 antwortet, hat die Mail angenommen; der Auftrag vermerkt unklar (S5), und wer dann mit Duplikatbestätigung erneut sendet, schickt sie doppelt.
2. Ein Abbruch oder Zeitlimit bei `MAIL FROM`, `RCPT TO` oder `DATA`, also vor dem Datenende, gilt ebenfalls als unklar (S1, S2), obwohl der Server zu diesem Zeitpunkt sicher nichts übernommen hat. Das kostet nur Handarbeit, ist aber bei kurz gestörten Verbindungen häufig.

Beides ist auf der sicheren Seite und widerspricht keinem Kriterium, verlagert aber vermeidbare Entscheidungen auf Personen und erhöht über Punkt 1 das Duplikatrisiko. **Vorschlag:** eigenes längeres Lesezeitlimit nur für die Antwort auf das Datenende (innerhalb des Zeitlimits des Auftrags, das dann mitwachsen muss) und die Phase vor dem Datenende als sicher einordnen, etwa über einen `TransportListener` oder eine eigene Phasenmarke.

## Hinweise

- **H1 Eigene Antwort nur an der Message-ID erkannt.** `ownReply` vergleicht nur die Message-ID (`MailIntake.java:230`, vor DMARC und Absender). Eine Mail, die die ID einer Antwort trägt, etwa nach einer Weiterleitung mit „Umleiten“ oder durch Absicht, wird stillschweigend `automatic`. Abgleich zusätzlich mit Absenderadresse des Postfachs wäre robuster.
- **H2 Keine Wiederholungssicherheit der Bestätigung.** Zwei POST auf `…/replies` ergeben zwei Antworten und zwei Mails. Die Oberfläche sperrt den Knopf während der Anfrage; ein Neuladen nach einer abgebrochenen Verbindung und erneute Bestätigung bleibt möglich. Ein Schlüssel aus der Oberfläche (Idempotency-Key) würde das schließen.
- **H3 Reihenfolge des Verlaufs.** `thread()` sortiert eingehende Mails nach ihrem Datum aus dem Kopf der Mail (vom Absender gesetzt) und Antworten nach Erstellung; `In-Reply-To` ist das letzte Element und kann eine eigene, auch nie gesendete Antwort sein. backend/README.md spricht von der „letzten Mail des Tickets“. Entweder die eingehende Mail nehmen oder die Beschreibung anpassen; Empfangszeit statt Absenderdatum verwenden.
- **H4 Kurzes Fenster für vorübergehende Fehler.** Fünf Versuche mit 30 s, 1, 2 und 4 Minuten Abstand decken etwa 7,5 Minuten ab. Eine längere Störung des Relays beendet Antworten als `failed`. Sichtbar und erneut sendbar, aber ein längerer Rückzug wäre für Mail üblicher.
- **H5 Systemübergänge auf den Urheber gebucht.** `ReplySender` schreibt `updated_by = created_by` für `sending`, `sent`, `failed`, `uncertain`. Für die Nachvollziehbarkeit wäre der Kennwert der Mailannahme (`MAIL_INTAKE`) oder ein Systemkonto klarer.
- **H6 Testlücken.** Nicht getestet: `helpdesk.author_inactive`, `helpdesk.mailbox_unavailable` nach Änderung des Postfachs, Ablehnung nach dem Datenende, Übernahme einer Mail mit Kontakt eines anderen Kunden über `/attach`, zwei Ausführer gleichzeitig, und das erneute Senden einer `failed` Antwort im Backend. Die Browser Tests laufen gegen das Testmodell, nicht gegen den Server.
- **H7 Kundenantworten am bestehenden Ticket fallen nicht auf.** Eine angehängte Antwort ändert Status und Zuständigkeit nicht und öffnet ein erledigtes Ticket nicht wieder; ohne Benachrichtigungen (bewusst nicht Teil) sieht sie nur, wer das Ticket öffnet. Für die Abnahme bewusst entscheiden.
- **H8 Sperrreihenfolge.** Die Klassenbeschreibung von `ReplyService` nennt Projekt vor Antwort; `ReplySender.prepare` sperrt die Antwort vor dem Projekt (`FOR SHARE`). Wegen der geteilten Projektsperre derzeit ohne Verklemmung, aber leicht zu brechen, wenn ein Weg das Projekt exklusiv sperrt und danach Antworten sperrt.

## Empfehlung

F1 vor der Abnahme beheben, weil eine Kundenantwort sonst ohne Hinweis liegen bleiben kann und das Helpdesk Team keinen Ausweg hat. F2 bis F4 sind klein und lassen sich im selben Korrekturauftrag erledigen; F3 trifft lange Fälle und sollte ebenfalls nicht lange warten. Die Hinweise sind optional; H7 braucht eine bewusste Entscheidung bei der Abnahme. Ansonsten erfüllt die Umsetzung die Kriterien 1, 2, 3, 6, 7 und 8 und im Kern 4 und 5.
