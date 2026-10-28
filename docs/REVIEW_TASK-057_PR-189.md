# Prüfung TASK-057 / PR #189

Ergebnis: **Fünf reproduzierte Befunde P2, kein nachgewiesener P1.** Der normale Eingang, die transaktionale Ticketanlage, die Verschlüsselung in der Datenbank und die Bearbeitung der Prüfwarteschlange funktionieren in den ausgeführten Tests. Offen bleiben Passwortoffenlegung durch IMAP-Fehlermeldungen im Protokoll (F1), doppelte Tickets nach einem UIDVALIDITY-Wechsel (F2), übergangene Nachrichten nach einem Wechsel der IMAP-Quelle (F3), Kontaktdaten trotz fehlender Feldrechte (F4) und eine unvollständige Prüfung fremder Kunden in Empfängerlisten (F5). Damit sind die technischen Abnahmekriterien nicht vollständig erfüllt.

Stand: 28. Oktober 2026. Prüfer: Codex, Sitzung `codex-20261028-task057-review-01`. Getrennte technische Prüfung nach DEC-040; die Umsetzung stammt aus einer anderen, von Claude geführten Sitzung. Diese Prüfung hat daran nicht mitgearbeitet, ändert keinen Produktcode und keinen Aufgabenstatus und erklärt keine Abnahme.

## Grundlage und Abgrenzung

- Ergebnis [PR #189](https://github.com/1uedev/task-manager/pull/189), feste Prüfbasis **`1d80f189aa2932a08f4f08d937a6a91b71d8d4fd`** auf main. Spätere Änderungen sind nicht Gegenstand dieses Berichts.
- Maßstab: AGENTS.md, TODO.md (TASK-057 einschließlich Übergabe), DEC-086, Nachtrag in docs/HELPDESK_SLA.md, backend/README.md (Helpdesk Eingang), `contracts/helpdesk.openapi.yaml` sowie die Änderungen an den Verträgen für identity, permissions und tickets.
- Durchgesehen: Helpdesk-Modul, Anbindung an Jobs, Rechte, Tickets und Dateien, Migration V0026, Oberfläche und Browser-Testmodell, `compose.yaml`, `.env.example` und `backend/scripts/lifecycle-check.sh`.
- Die bekannte Abweichung „leere Liste und 404 statt 403 ohne helpdesk.review“ wird nur auf Folgen geprüft. Die fachliche Entscheidung darüber bleibt bei Holger.
- Alle Laufzeitprüfungen verwendeten eine isolierte Testdatenbank und synthetische Nachrichten/Kontakte. Die zusätzliche Testklasse war nur vorübergehend vorhanden. Die [Prüfbelege](review-evidence/task-057/README.md) enthalten Nachstellcode und bereinigte Ergebnisse, keine Zugangsdaten oder vollständigen Laufzeitprotokolle.

## Was gelaufen ist

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` mit dem verfügbaren Python-Interpreter | erfolgreich |
| `python3 scripts/task_board.py --check` mit dem verfügbaren Python-Interpreter | erfolgreich; Aufgabenstatus unverändert |
| `python3 -m unittest discover -s tests -v` | 38 Tests, keine Fehler, 1 übersprungen: Umschalten der Zeitzone wird nur unter Unix unterstützt |
| Backend-Ziel `verify`, Maven aus der Wrapper-Installation, JDK 25.0.4.1, PostgreSQL 18.6 lokal | **361 Tests, 0 Fehler, 0 übersprungen**, Build erfolgreich; darunter 9 `HelpdeskApiTest`, Vertrags-, Parser-, Schlüssel-, Rechte-, Datenbank- und Lebenszyklustests |
| Frontend `npm ci`, danach `npm run verify`, Node 24.22.0 | erfolgreich: Vertragsgenerierung, Typprüfung, 77 Tests und Build |
| Frontend `npm run test:browser`, installiertes Chrome über Playwright | erster Gesamtlauf **123/124**, ein fehlgeschlagener Helpdesk-Test; isolierter Helpdesk-Lauf **10/10**; zweiter Gesamtlauf **124/124**; siehe H3 |
| Eigene Backend-Experimente E1–E9, HTTP/GreenMail/PostgreSQL, für Fehlerfälle zusätzlich synthetischer IMAP-Server | abschließender Lauf **9/9**; fünf Fehlerbilder und vier Kontroll-/Abgrenzungsfälle nachgestellt; siehe Belege |
| `git diff --check` und Abgleich der ausgelieferten Dateien | erfolgreich; nur Bericht und Prüfbelege |

Die Befundtests sind **Beobachtungstests**: Sie bestätigen das derzeitige fehlerhafte Verhalten und werden deshalb grün. Sie belegen keine Fehlerbehebung. Während der Erstellung der zusätzlichen Testklasse gab es korrigierte Übersetzungsfehler und einen zunächst falsch konstruierten If-Match-Testaufruf; diese waren keine Produktfehler.

Nicht gelaufen: der angegebene Linux-Browserpfad, JDK 21 mit Versionsüberschreibung, ein Docker-Image-Build, `image-check.sh`, `lifecycle-check.sh` als Shell-Lauf und ein echter Compose-Start/Stopp. Die Sitzung lief unter Windows; Maven wurde direkt aus der Wrapper-Installation gestartet, der Browser über den im Testhelfer vorgesehenen Chrome-Ersatz. Kein produktiver SMTP/DMARC-Mailweg, kein externer TLS-Mailserver, kein Lasttest mit sehr großen Ordnern oder maximal großen Anhängen. Die vorhandenen Browsertests verwenden ihr Backend-Testmodell, keine durchgehende Browser-zu-GreenMail-Integration.

## Befunde

### F1 · P2 · IMAP-Fehlermeldungen können das Postfachpasswort ins Protokoll schreiben

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/MailboxService.java:124`, `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:132`.

Beide Stellen protokollieren `failure.getCause()` einschließlich des Textes vom IMAP-Server. Dieser Text ist nicht bereinigt. Damit gilt die Zusage „das Protokoll nennt nur Postfach und Code“ nicht. Ein Server, der bei einem fehlgeschlagenen LOGIN Teile der Anmeldung zurückmeldet, kann das zuvor entschlüsselte Passwort ins Anwendungsprotokoll gelangen lassen. Das ist ein zusätzlicher Offenlegungsweg für Personen mit Protokollzugriff; die verschlüsselte Speicherung verhindert ihn nicht. Es wird nicht behauptet, dass jeder gewöhnliche Anmeldefehler das Passwort enthält.

**Nachstellung E7:** Ein synthetischer IMAP-Server beantwortet CAPABILITY und gibt nach LOGIN eine NO-Antwort mit dem synthetischen Testpasswort zurück. Sowohl die Verbindungsprüfung über HTTP als auch `MailIntake.fetch` schreiben diesen Wert ins Protokoll. Ein Logback-Appender bestätigt zwei entsprechende Ereignisse. Die HTTP-Antwort bleibt bereinigt und nennt nur `helpdesk.authentication_failed`.

**Vorschlag:** An diesen Grenzen ausschließlich die Postfachkennung, den stabilen Fehlercode und gegebenenfalls eine sichere Korrelationskennung protokollieren. Keine ungeprüften Servertexte oder verschachtelten Ausnahmen anhängen. Den Test um einen Server ergänzen, der Anmeldedaten in seiner Fehlerantwort spiegelt; auch der allgemeine Fehlerpfad in `MailIntake.run` sollte auf solche Ursachen geprüft werden.

### F2 · P2 · Der Message-ID-Abgleich nach UIDVALIDITY-Wechsel endet nach einem Abrufpaket

**Datei:** `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:107`, `:119`, `:122`, `:168`.

`compare` ist nur eine lokale Variable des Aufrufs, in dem der Wechsel erkannt wird. Die neue UIDVALIDITY wird vor der Verarbeitung gespeichert. Beim nächsten Aufruf stimmt sie bereits mit dem Server überein, `compare` bleibt falsch und bekannte Message-IDs werden nicht mehr geprüft. Liegen mehr wieder aufgetauchte Nachrichten vor als das Abruflimit, werden ab dem zweiten Paket bereits bearbeitete Mails erneut zu Tickets. Ein Abbruch während des Abgleichs hat dieselbe Schwäche. Der sichtbare Hinweis speichert keinen fortzusetzenden Abgleichzustand.

**Nachstellung E2:** Abruflimit auf 1 setzen, zwei bestätigte Mails importieren: zwei Tickets. Wie im vorhandenen UIDVALIDITY-Test die gespeicherten alten Herkunftswerte auf eine andere Gültigkeit setzen und die beiden Servernachrichten wieder ungelesen markieren. Erster Abruf nach dem Wechsel: weiterhin zwei Tickets. Zweiter Abruf: **drei Tickets**, obwohl beide Message-IDs bereits bekannt sind. Mit dem Standardlimit liegt die entsprechende Grenze bei 50 Nachrichten.

**Vorschlag:** Den Abgleichzustand samt Grenze dauerhaft speichern und über Pakete, Fehler und Neustarts fortsetzen. Die Entscheidung über eine bekannte Nachricht muss auch bei konkurrierenden Abrufen atomar sein. Regressionstest mit mehr Nachrichten als dem Paketlimit und mit einem unterbrochenen Abgleich ergänzen.

### F3 · P2 · Ein Wechsel von Konto oder Ordner kann neue Mails als alte Herkunft überspringen

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/HelpdeskRepository.java:219`, `:265`; `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:145`, `:175`; `backend/src/main/resources/db/migration/V0026__helpdesk_baseline.sql:119`.

Beim Wechsel von Server, Port, Konto oder Ordner werden Position und UIDVALIDITY zurückgesetzt. Die gespeicherte Herkunft enthält aber nur die unveränderte Helpdesk-Postfachkennung, UIDVALIDITY und UID. Der Ordner beziehungsweise die konkrete IMAP-Quelle fehlt. Eine identische Kombination in einem anderen Konto oder Ordner ist zulässig: Die IMAP-Kennung bezieht sich auf das jeweilige Postfach, nicht auf alle Konten eines Servers. Siehe [RFC 9051, Abschnitt 2.3.1.1](https://www.rfc-editor.org/rfc/rfc9051.html#section-2.3.1.1).

`knownOrigin` hält die neue Nachricht deshalb für bereits verarbeitet. Anschließend wird sie trotzdem als gelesen markiert und die Position weitergeschoben. Sie fehlt sowohl im Helpdesk-Eingang als auch als neues Ticket. Die Originalmail bleibt auf dem Server, ist dort aber nicht mehr ungelesen.

**Nachstellung E6:** Eine Nachricht aus Konto A importieren. Konto B enthält eine neue Nachricht; für die Testanordnung wird die alte gespeicherte Herkunft auf die zulässigerweise gleiche UIDVALIDITY/UID aus B gesetzt. Über die reguläre versionierte Postfach-API auf Konto B umstellen und abrufen. Ergebnis: weiterhin nur der alte Helpdesk-Eingang, die neue Nachricht in B ist gelesen. Es wird kein Konflikt angezeigt.

**Vorschlag:** Die konkrete Quelle beziehungsweise eine dauerhafte Quellgeneration zum Bestandteil der Herkunft machen. Bei Konfigurationswechseln alte Herkunft erhalten, aber eindeutig vom neuen Konto/Ordner trennen. Laufende Abrufe müssen ihre Position nur für die noch aktuelle Quellgeneration fortschreiben dürfen. Die bestehende Migration nicht rückwirkend ändern, sondern eine Korrekturmigration vorsehen.

### F4 · P2 · Die Ticket-Kundenauswahl umgeht die Leseregel für Kontakte

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/TicketCustomerService.java:83`, `:88`; `backend/src/main/java/taskmanager/helpdesk/web/TicketCustomerController.java:63`.

`GET …/tickets/{number}/helpdesk/options` verlangt nur `ticket.edit` und Schreiben auf `customer`. Danach liefert es zu allen für das Projekt freigegebenen Kunden sämtliche Kontakte einschließlich Namen und Adressen. Lesen auf `contact` wird ebenso wenig verlangt wie Schreiben auf `contact`. Dadurch kann ein Benutzer, dem die Kontaktanzeige am Ticket korrekt vorenthalten wird, dieselben Daten und weitere Kontakte über den Auswahlendpunkt abrufen. Das Ausblenden des Bearbeitungsknopfs in der Oberfläche schützt den Endpunkt nicht.

**Nachstellung E3:** Projektmitglied mit `ticket.read`, `ticket.edit`, Lesen auf `title` und `customer`, Schreiben ausschließlich auf `customer`; kein `helpdesk.manage`, kein `helpdesk.review`, weder Lesen noch Schreiben auf `contact`. Die Ticket-Helpdesk-Antwort enthält `contact: null`. Der direkte Aufruf von `/helpdesk/options` antwortet dagegen mit 200 und enthält die Kontaktadresse.

**Vorschlag:** Auswahlantworten nach den aktuellen Leseregeln filtern beziehungsweise den jeweiligen Teil nur mit der erforderlichen Leseberechtigung liefern. Schreibberechtigungen gesondert prüfen. API-Tests für diese Kombination und für explizite Verweigerungen ergänzen.

### F5 · P2 · Fremde Kunden ab der 51. Empfängeradresse umgehen die Prüfwarteschlange

**Dateien:** `backend/src/main/java/taskmanager/helpdesk/internal/ParsedMail.java:37`, `:241`; `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:252`.

Der Parser übernimmt jeweils höchstens 50 To- und CC-Adressen. Weitere Adressen werden ohne Fehler oder Hinweis verworfen. Die Zuordnung prüft nur diese gekürzten Listen auf Kontakte eines anderen Kunden. Damit wird eine vollständige Empfängerprüfung behauptet, obwohl ein relevanter Teil fehlt. Außerdem fehlt die verworfene Adresse später in der gespeicherten Mailansicht.

**Nachstellung E4:** Bestätigter bekannter Absender, 50 unbekannte Adressen in CC und an Position 51 die Adresse eines Kontakts eines anderen Kunden. Ergebnis: **automatisch ein Ticket**, kein `foreign_customer`; die fremde Adresse fehlt in `cc_addresses`.

**Vorschlag:** Alle Adressen für die Sicherheitsentscheidung auswerten. Falls eine Verarbeitungsgrenze notwendig ist, bei Überschreitung die Mail mit eigenem Grund zur Prüfung geben und die Unvollständigkeit kenntlich machen. Eine begrenzte Anzeige darf die Zuordnungsentscheidung nicht begrenzen. Auch nicht lesbare Empfängerlisten sollten nicht stillschweigend als unbedenklich gelten.

## Abgleich der Abnahmekriterien

| Kriterium aus TASK-057 | Bewertung und Beleg |
|---|---|
| Bestätigter bekannter Kontakt einer freigegebenen Organisation erzeugt genau ein Ticket mit Kunde, Kontakt, Text und Anhängen; Wiederholung/Neustart/parallele Ausführer ohne Duplikat | **Normalfall belegt.** `HelpdeskApiTest` prüft erneuten Abruf, verlorene Position und zwei parallele direkte Abrufe; eindeutige Herkunft und Ticketanlage liegen in derselben Transaktion. Kein eigener Prozessabsturz während des Helpdesk-Commits erzeugt. Grenzen F2/F3. |
| Unbekannte, unbestätigte oder mehrdeutige Absender in die Prüfwarteschlange | **Für die getesteten Fälle belegt.** Vorhandene API-/Parser-Tests, E5 für fehlenden From. Unvollständige Empfängerprüfung verletzt den verwandten Zuordnungsumfang: F5. |
| Gefälschte From-Adresse ohne eigenes DMARC-pass nie direkt zum Ticket | **Nur unter der Vertrauensvoraussetzung H1.** Oberster passender eigener Fail-Header schlägt einen darunterliegenden Pass-Header. Fremde Kennung allein bestätigt nichts. Ohne Bereinigung vorgeschalteter Header ist die Zusage nicht gegeben; E1. |
| Automatische Antwort erzeugt kein Ticket | **In den vorhandenen Fällen belegt.** Automatische Eingänge werden als `automatic` gespeichert. Keine SMTP-Antwortfunktion in TASK-057. |
| UIDVALIDITY-Wechsel ohne Duplikate | **Nicht erfüllt: F2.** Der vorhandene Test deckt nur einen einzigen Abrufdurchlauf ab. |
| Prüfwarteschlange nur mit helpdesk.review; doppelte Bearbeitung ohne doppeltes Ticket | **Belegt für geprüfte Rechte-/Versionsfälle.** Bekannte 404-Abweichung siehe H2. E8: gesperrte Mail ergibt 503 `helpdesk.busy` ohne Mutation; paralleles Anlegen/Verwerfen mit derselben Version ergibt 200/409. Vorhandene Tests prüfen 412 bei veralteter Version und wiederholte Ticketanlage mit 409. |
| Passwörter weder in Antwort noch Protokoll noch unverschlüsselt in DB | **Teilweise erfüllt, insgesamt offen wegen F1.** AES-256-GCM, zufällige Nonce, Postfach-ID als zugehörige Daten; DB/API/Audit-Prüfungen erfolgreich. `SecretBoxTest` prüft falschen/fehlenden Schlüssel, manipulierten Chiffretext und fremde Postfach-ID. |
| Falsches Passwort/nicht erreichbarer Server in der Postfachliste sichtbar | **Belegt.** API-Test für beide Fälle; E9 zusätzlich stiller IMAP-Server: Zeitüberschreitung nach konfigurierten fünf Sekunden, Fehlercode gespeichert, kein Eingang/Ticket. |
| Verborgene Felder fehlen in Ticket, Historie und Suche | **Nicht durchgängig erfüllt: F4.** Hauptansicht und Historie filtern in vorhandenen Tests korrekt; Auswahlendpunkt nicht. Zur Kopie des Mailtexts in Titel/Beschreibung siehe H4. |

## Hinweise

### H1 · Absenderprüfung setzt einen korrekt eingerichteten Vertrauensbereich voraus

`SenderCheck.java:32` sucht den ersten Header mit passender authserv-id und überspringt vorherige Header anderer Server. Groß-/Kleinschreibung von Kennung und Domain wird ignoriert; die Domain wird vollständig verglichen, eine Subdomain ist keine Übereinstimmung. Fehlender From führt in E5 zu `sender_missing`; ein oberer eigener `dmarc=fail` kann nicht mit einem späteren eigenen Pass überschrieben werden. Kommentare werden entfernt.

E1 zeigt aber auch: Ein oberer Header einer anderen Kennung mit Fail und darunter ein mitgelieferter Header der konfigurierten Kennung mit Pass erzeugen ein Ticket für den bekannten Kontakt. Das ist **kein Nachweis eines Angriffs durch einen korrekt bereinigenden Empfangsserver**. Es zeigt die Folgen einer falschen/veralteten authserv-id oder eines Servers, der fremd eingespeiste Header nicht entfernt. Die Kennung allein beweist die Herkunft nicht. [RFC 8601, Abschnitte 1.6, 5 und 7.1](https://www.rfc-editor.org/rfc/rfc8601.html#section-5) verlangt eine entsprechende Vertrauensgrenze und Entfernung eingeschleuster eigener Ergebnisse.

Diese Betriebsvoraussetzung sollte ausdrücklich dokumentiert und mit einem Ende-zu-Ende-Test des vorgesehenen Mailwegs geprüft werden. Falls stattdessen ausschließlich der insgesamt oberste Authentication-Results-Header gelten soll, muss diese strengere Regel ausdrücklich festgelegt und implementiert werden. Die aktuelle Auswahl entspricht „oberster Header der eigenen Kennung“. DMARC bestätigt dabei die Domain, keine individuelle Person hinter dem lokalen Teil der Adresse. Der Regex-Parser ist außerdem kein vollständiger RFC-Parser; verschachtelte/quotierte Eigenschaftswerte wurden nicht umfassend gefuzzt.

### H2 · Die bekannte 404-Abweichung verrät in den geprüften Wegen keine fremden Eingänge

Ohne Projektaktion sind Liste und Zusammenfassung auf berechtigte Projekte beschränkt; Einzelansicht und Bearbeitung antworten wie bei einer unbekannten Mail. Auch ein Projektmitglied mit `ticket.read` erhält dadurch keine fremde Mail. `helpdesk.manage` ist ein eigenständiges Verwaltungsrecht und ersetzt die Projektaktion nicht. Ein Benutzer ohne Projekt-Leserecht sieht folgerichtig auch keinen ausführlichen Ablehnungsgrund. Keine zusätzliche Abnahmeentscheidung aus dieser Prüfung.

### H3 · Ein Browsertest war im ersten Gesamtlauf instabil

`frontend/tests/browser/helpdesk.test.mjs:235` erwartete nach dem Anlegen eines Kontakts dessen Auswahl, las aber noch den leeren Wert. Der isolierte Lauf und der zweite Gesamtlauf bestanden ohne Produktänderung. `InboxPage.tsx:185` lädt die Kandidaten asynchron nach; die Erfolgsmeldung entsteht schon in `:232`. Das passt zu einem Zwischenzustand, ist aber keine abschließend bewiesene Fehlerursache. Die Auswahl gezielt nach verzögerter Kandidatenantwort prüfen und den Test auf den endgültigen Zustand warten lassen. Ein dauerhaft verlorener Kontakt wurde nicht nachgewiesen.

### H4 · Feldrecht mail schützt nicht die gesonderte Ticketbeschreibung

Das Modul kopiert Betreff und Text in Titel/Beschreibung des Tickets. Die Suche behandelt diese Kopien mit den Rechten für `title` und `description`; Kunden und Kontakte werden nicht in den Suchindex aufgenommen. Wer die Beschreibung lesen darf, kann daher Mailtext auch ohne Leserecht auf `mail` lesen beziehungsweise suchen. Das ist in backend/README.md beschrieben und folgt dem Ticket-aus-Mail-Modell, sollte bei der Auslegung des Abnahmekriteriums aber ausdrücklich berücksichtigt werden. Anhänge unterliegen analog dem eigenen Feld `attachments`. Keine zusätzliche Projektgrenzenverletzung im geprüften Such-/Historienpfad festgestellt.

### H5 · Größen- und Zeitgrenzen haben noch nicht geprüfte Betriebsgrenzen

Zu große Nachrichten mit korrekter IMAP-Größenangabe gelangen im vorhandenen Test ohne Inhalt in die Prüfwarteschlange; erlaubte und verweigerte Anhänge werden über das Dateimodul behandelt. `ImapMailClient.java:134` liest eine als klein gemeldete Mail jedoch vollständig in den Speicher. Die UID-Abfrage materialisiert vor der Paketbegrenzung die passenden Nachrichten des Ordners. Verbindung und Lesen haben Socket-Zeitlimits, Schreiben kein eigenes konfiguriertes Zeitlimit. E9 belegt einen stillen Server, nicht einen langsamen endlosen Datenstrom oder einen blockierten Schreibvorgang. Ein Ressourcen-/Lasttest bleibt offen; insbesondere sollte die Bytegrenze auch am tatsächlich gelesenen Datenstrom gelten.

### H6 · Weitere Abgrenzungen von Vertrag und Umsetzung

- Ein einziger wiederkehrender Auftrag arbeitet alle Postfächer nacheinander ab (`MailIntake.run`), während der Umfang in TODO einen Auftrag je aktivem Postfach vorschlägt. Der Betriebsteil der README beschreibt die Umsetzung richtig. Langsame Postfächer verzögern dadurch die späteren; keine Prüfung mit vielen blockierenden Postfächern durchgeführt.
- Message-ID ist nur beim erkannten UIDVALIDITY-Wechsel ein Abgleichsschlüssel, kein allgemeiner eindeutiger Schlüssel. Zwei verschiedene UIDs mit gleicher Message-ID in derselben Gültigkeit werden laut Code getrennt verarbeitet. Für Nachrichten ohne Message-ID ist der beschriebene Abgleich nicht möglich. Nicht mit der nachgewiesenen Einmaligkeit derselben IMAP-Herkunft verwechseln.
- Die Schlüsselprüfung bei Postfachanlage greift in `MailboxService.java:200` nur bei einem nicht leeren Passwort. Ein Postfach ohne Passwort kann laut Code auch ohne Schlüssel angelegt werden; das ist enger als die Aussage „ohne Schlüssel keine Postfächer“. Keine Klartextspeicherung daraus festgestellt. Ein falsch formatiert konfigurierter Schlüssel verhindert den Start; ein anderer gültiger Schlüssel macht bestehende Geheimnisse unlesbar. Die vollständige API mit fehlendem Schlüssel wurde nicht separat gestartet.
- Migration V0026 lief in den Backendtests auf frischen PostgreSQL-Datenbanken. Die Tabellen und die Anpassungen des Lebenszyklusskripts sind vorhanden; F3 betrifft die fachliche Vollständigkeit des Herkunftsschlüssels. `compose.yaml` reicht denselben optionalen Schlüssel an app und worker weiter, `.env.example` beschreibt Erzeugung und Sicherung. Der Containerbetrieb selbst ist hier nicht nachgewiesen.
