# Prüfbelege TASK-058

Prüfbasis `ab9a6eea5589b4489437bf6fe31064e20dd5c74f` (gemergter PR #202), 29. Oktober 2026. Ausschließlich synthetische Daten, JDK 21.0.12, lokales PostgreSQL 16. Bewertung im [Bericht](../../REVIEW_TASK-058_PR-202.md). Keine Prozessprotokolle oder Zugangsdaten.

## Nachstellung im Backend (E1 bis E4)

[HelpdeskApiTest.probes.java.txt](HelpdeskApiTest.probes.java.txt) enthält vier Methoden `e1` bis `e4`, die vorübergehend vor der Hilfsmethode `unknown` eingefügt werden, in `backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java`. Ausgeführt in einem getrennten Arbeitsverzeichnis mit `./mvnw -Djava.version=21 -Dtaskmanager.test.jdbc-url=… -Dtest='HelpdeskApiTest#e*' test`. Produktcode unverändert. Wo der Test einen Schritt des Jobmoduls nachbildet, verwendet er wörtlich die SQL Anweisung aus `JobRepository.releaseExpired` beziehungsweise `JobRepository.cancel`.

| Experiment | Ablauf | Beobachtung |
|---|---|---|
| E1 | Antwort bestätigt, Antwort auf `sending`, Auftrag im letzten Versuch mit abgelaufener Lease, dann `releaseExpired` | Auftrag `failed`, Antwort bleibt `sending`, Oberfläche und Übersicht zeigen `pending`; erneut senden 409 `helpdesk.reply_not_failed`, festhalten 409 `helpdesk.reply_not_uncertain` (F1) |
| E2 | Antwort bestätigt, wartender Auftrag durch die Jobverwaltung abgebrochen | Antwort bleibt `pending`; erneut senden 409 `helpdesk.reply_not_failed` (F1) |
| E3 | Urheber A bestätigt, Antwort endet nach 451 im letzten Versuch als `failed`, A verlässt das Projekt, Kollegin B mit allen Rechten sendet erneut | B erhält 200, der neue Auftrag endet ohne Verbindung zum Server mit `helpdesk.author_inactive` (F2) |
| E4 | 20 Antworten am selben Ticket, die letzte über GreenMail gesendet | Zeile `References:` mit 1074 Zeichen ohne Faltung (F3) |

## SMTP Einordnung (S1 bis S5)

[SmtpExperiment.java.txt](SmtpExperiment.java.txt) ruft das echte `SmtpMailSender.send` gegen einen kleinen lokalen SMTP Server mit Zeitlimit 2 s auf (Klasse im Paket `taskmanager.helpdesk.internal`, Klassenpfad aus `target/classes` und den Abhängigkeiten).

| Fall | Ergebnis |
|---|---|
| S1 Verbindung bricht bei `RCPT TO` ab | `helpdesk.send_uncertain`, certain=false; der Server hatte keine Daten (F4) |
| S2 keine Antwort auf `RCPT TO` bis zum Zeitlimit | `helpdesk.send_uncertain`, certain=false; keine Daten (F4) |
| S3 554 nach dem Ende der Daten | `helpdesk.send_refused`, certain=true, permanent=true |
| S4 451 nach dem Ende der Daten | `helpdesk.send_refused`, certain=true, permanent=false |
| S5 250 nach dem Ende der Daten erst nach 3 s | `helpdesk.send_uncertain`; der Server hat die Mail angenommen (F4) |
