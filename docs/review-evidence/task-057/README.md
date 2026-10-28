# Prüfbelege TASK-057

Prüfbasis: `1d80f189aa2932a08f4f08d937a6a91b71d8d4fd`, 28. Oktober 2026. Alle Nachrichten, Kontakte und Serverantworten sind synthetisch. Keine produktiven Daten oder Zugangsdaten wurden verwendet. Vollständige Prozessprotokolle werden wegen ihrer Umgebungsangaben und des bewusst ausgelösten Passwort-Protokollfehlers nicht veröffentlicht.

## Nachstellung

Die [zusätzlichen Testmethoden](HelpdeskReview057.methods.java.txt) benutzen die Fixture aus `backend/src/test/java/taskmanager/helpdesk/HelpdeskApiTest.java` exakt auf der Prüfbasis. Für den tatsächlichen Lauf wurde diese Datei vorübergehend nach `HelpdeskReview057Test.java` im selben Paket kopiert:

1. Klassenname in `HelpdeskReview057Test` ändern.
2. `java.util.concurrent.TimeUnit` importieren.
3. In `settings` zusätzlich `registry.add("taskmanager.helpdesk.batch", () -> "1");` aufnehmen.
4. Die beigefügten Methoden vor der letzten schließenden Klassenklammer ergänzen.
5. Ausschließlich `./mvnw '-Dtest=HelpdeskReview057Test#review*' test` mit der in backend/README.md beschriebenen lokalen PostgreSQL-Testkonfiguration ausführen. Unter Windows wurde Maven direkt aus der Wrapper-Installation aufgerufen. Alle übrigen Tests der kopierten Fixture bleiben unselektiert.
6. Die vorübergehende Java-Datei danach entfernen. Die regulären 361 Tests wurden zuvor auf unverändertem Produkt-/Testcode mit `verify` ausgeführt.

Die Methoden übernehmen ausschließlich die bereits vorhandene synthetische Testfixture. Im Beleg stehen keine Schlüssel- oder Passwortwerte. Die Namen `MAIL_PASSWORD`, `AUTHSERV` usw. verweisen auf diese Fixture. Die Konsolenmarkierungen `P1` bis `P4` im frühen Teil des Experiments sind interne Probenummern, **keine Schweregrade**; maßgeblich sind E1–E9 und F1–F5 im Bericht.

## Tatsächlich beobachtete Ergebnisse

| Experiment | Testmethode | Beobachtung |
|---|---|---|
| E1 | `reviewForgedLowerHeaderIsTrusted` | Fremder oberer Fail-Header plus unterer Pass-Header mit konfigurierter Kennung: ein Ticket. Bedingte Vertrauensgrenze, Bericht H1. |
| E2 | `reviewUidValidityDedupEndsAfterFirstBatch` | Nach simuliertem UIDVALIDITY-Wechsel und Limit 1: Ticketzahlen 2 → 2 → 3. F2. |
| E3 | `reviewOptionsLeakUnreadableContacts` | Hauptansicht verbirgt Kontakt; Auswahlendpunkt liefert mit bloßem Schreibrecht auf customer dennoch seine Adresse. F4. |
| E4 | `reviewForeignCustomerAfterFiftiethRecipientIsIgnored` | Fremder Kunde an CC-Position 51: Ticket erzeugt, Adresse in gespeichertem CC nicht vorhanden. F5. |
| E5 | `reviewUpperTrustedFailureStillRejectsAndMissingFromQueues` | Oberer eigener Fail vor eigenem Pass: `sender_unconfirmed`; kein From: `sender_missing`; kein Ticket. |
| E6 | `reviewChangedAccountCollidesWithPreviousOrigin` | Konto über versionierte API gewechselt, zulässige Herkunftskollision vorbereitet: neue Mail übersprungen und gelesen markiert. F3. |
| E7 | `reviewImapFailureLeaksReflectedSecretIntoLog` | Zwei Logereignisse enthalten den zurückgespiegelten synthetischen Passwortwert, API-Antwort enthält ihn nicht. F1. |
| E8 | `reviewBusyAndConcurrentQueueActions` | Gehaltene Datenbanksperre: 503 `helpdesk.busy`, keine Mutation; konkurrierendes Anlegen/Verwerfen: 200 und 409, höchstens ein Ticket. |
| E9 | `reviewSilentImapServerTimesOut` | Stiller IMAP-Server: `helpdesk.timeout` nach der konfigurierten Wartezeit, kein Eingang/Ticket. |

Abschließender Maven-Lauf: **9 Tests, 0 Fehler, 0 übersprungen, BUILD SUCCESS**. Die Assertions bestätigen das beobachtete Verhalten, bei F1–F5 ausdrücklich den Fehler. Ausführungszeit rund 37 Sekunden einschließlich Start und Kompilierung. Die Aussage zu Retry-After beruht auf dem Code (`Requests.retrying`), der HTTP-Header wurde in E8 nicht eigens ausgewertet.

E2 folgt der bereits im ursprünglichen `HelpdeskApiTest` verwendeten Simulation des Gültigkeitswechsels in den gespeicherten Herkunftswerten; es ist kein tatsächlich neu gestarteter IMAP-Server. E6 stellt eine erlaubte Kollision zwischen unabhängigen IMAP-Quellen deterministisch her. Die anschließende Konfigurationsänderung erfolgt über die echte HTTP-API. E8 konkurriert mit einer separaten JDBC-Verbindung und zwei HTTP-Anfragen. E7 verwendet einen kleinen Socket-Server statt GreenMail, um die Fehlerantwort kontrolliert zu erzeugen.

Weitere Läufe: Backend 361/361; Frontend 77/77; Browser zuerst 123/124, isoliert Helpdesk 10/10, erneut vollständig 124/124; Repository-Unittests 38 mit einem plattformbedingt übersprungenen Test. Details und nicht ausgeführte Prüfungen stehen im [Bericht](../../REVIEW_TASK-057_PR-189.md).
