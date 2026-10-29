# Zentrale offene Aufgaben

Stand 18. Oktober 2026 · Planung, Repository-Infrastruktur und Frontend-Grundfläche (TASK-023)

Diese Datei enthält den gemeinsamen Arbeitsstand aller beauftragten KI-Sitzungen. Status und Zuständigkeit werden gemäß [AGENTS.md](AGENTS.md) koordiniert. Eine Aufgabe kann mehrere Anforderungen konkretisieren. Ein offener Entwurf gilt nicht als fachlich abgenommen.

Repository: [1uedev/task-manager](https://github.com/1uedev/task-manager). Maßgeblich ist diese Datei auf main. TASK-013 richtet die Arbeitsumgebung ein. Die übergreifende Koordination liegt bei Holger oder einer ausdrücklich von ihm benannten Sitzung. Aufgaben werden über einen reservierenden Pull Request vergeben; siehe docs/AI_WORKFLOW.md. GitHub Issues sind Diskussions- und Einstiegspunkte, keine zweite Aufgabenquelle.

Am 28. Oktober 2026 hat Holger alle bis dahin zur Prüfung stehenden Aufgaben abgenommen, darunter TASK-001 bis TASK-021; sie stehen in [DONE.md](DONE.md). Neue Aufgaben brauchen weiterhin einen eigenen Auftrag und eine Reservierung.

<!-- TASK-BOARD:START -->
## Aufgabenübersicht

Automatisch aus den Aufgabeneinträgen weiter unten erzeugt. Maßgeblich ist TODO.md auf main; eine lokale Kopie kann veraltet sein. Aktualisieren: `python3 scripts/task_board.py --write`.

| Kategorie | Anzahl | Bedeutung |
|---|---:|---|
| Frei und bearbeitbar (OFFEN) | 0 | Zur Reservierung verfügbar; Auftrag und Abhängigkeiten im Detail prüfen. |
| Durch eine KI reserviert (IN ARBEIT) | 1 | Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe. |
| Blockiert (BLOCKIERT) | 1 | Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig. |
| Zur Prüfung bereit (BEREIT ZUR PRÜFUNG) | 2 | Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen. |

Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md).

### Frei und bearbeitbar – OFFEN

Derzeit keine Aufgaben in dieser Kategorie.

### Durch eine KI reserviert – IN ARBEIT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-060](#task-060-befunde-r1-bis-r3-der-nachprüfung-von-task-059-beheben) | Befunde R1 bis R3 der Nachprüfung von TASK-059 beheben | claude-20261028-helpdeskfix-02 | 2026-10-28 |

### Blockiert – BLOCKIERT

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-058](#task-058-helpdesk-antworten-über-smtp-paket-a9-teil-2) | Helpdesk Antworten über SMTP (Paket A9, Teil 2) | claude-20261028-helpdesk-01 | 2026-10-28 |

### Zur Prüfung bereit – BEREIT ZUR PRÜFUNG

| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |
|---|---|---|---|
| [TASK-057](#task-057-helpdesk-eingang-über-imap-mit-prüfwarteschlange-paket-a9-teil-1) | Helpdesk Eingang über IMAP mit Prüfwarteschlange (Paket A9, Teil 1) | claude-20261028-helpdesk-01 | 2026-10-28 |
| [TASK-059](#task-059-befunde-f1-bis-f5-der-prüfung-von-task-057-beheben) | Befunde F1 bis F5 der Prüfung von TASK-057 beheben | claude-20261028-helpdeskfix-01 | 2026-10-28 |

Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).
<!-- TASK-BOARD:END -->

## Neue Aufgaben und Übergaben

Neue Aufgaben verwenden die nächste freie TASK-ID. Pflichtangaben sind Status, verantwortliche Sitzung oder unbesetzt, Aktualisierungsdatum, REQ-Bezug, Abhängigkeiten, Arbeitsstand oder Branch, Umfang, Abnahmekriterien und nächster Schritt. Bei Blockaden Ursache und benötigte Entscheidung ergänzen. Statusänderungen dürfen keine ausgeführten Prüfungen oder Freigaben erfinden.

## Vorgemerkte Entwicklung nach gesonderter Beauftragung

Die folgenden Aufgaben wurden in TASK-020 vorgemerkt. DEC-054 beauftragte TASK-021 als isolierten Nachweis, DEC-055 die Backendgrundlage aus TASK-022, DEC-056 die React-Grundfläche aus TASK-023 und DEC-057 das Berechtigungsmodul aus TASK-025; die weiteren Implementierungsaufgaben bleiben Vorschläge, keine laufenden Aufträge. **BLOCKIERT** bedeutet: Implementierung noch nicht beauftragt und jeweils genannte Entscheidungen/Abhängigkeiten noch nicht erfüllt. TASK-024 ist inzwischen durch Claim-PR #73 für claude-20261018-identity-01 reserviert; TASK-026 bis TASK-031 haben weiterhin keinen Besitzer oder Arbeitsbranch. Nach ausdrücklicher Freigabe hebt die Koordination den passenden Blocker auf; dann gilt die normale Claim-PR-Regel. Umfang, Detailverträge und Folgeabhängigkeiten sind vor der Reservierung gegen die tatsächliche Entscheidung zu prüfen. Ein Merge allein ersetzt keine Abnahme.

## TASK-057 Helpdesk Eingang über IMAP mit Prüfwarteschlange (Paket A9, Teil 1)

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261028-helpdesk-01 · **Aktualisiert:** 2026-10-28

**Bezug:** REQ-HD-001 (Eingang, geschützte Zugangsdaten, erkennbare Verbindungsfehler), REQ-HD-002 (Duplikate, unbekannte Absender nach DEC-031, automatische Antworten und Schleifen beim Eingang), REQ-ORG-002 (Unternehmensgrenze), REQ-TKT-003 (nur Mailanhänge über das Modul `files`). **Abhängigkeiten:** TASK-027 (Ticketkern), TASK-044 (Projektkonfiguration), TASK-051 (Dateien), TASK-054 bis TASK-056 (Hintergrundaufträge), alle abgenommen. **Arbeitsstand:** Paket A9 der Reihenfolge aus DEC-072, Holgers Zustimmung „Ja“ vom 28. Oktober 2026 im Projektgespräch. Branch claude/project-thread-b4u17z nach dem Merge dieser Reservierung; Ergebnis mit DEC-086 und Migration V0026. Die folgenden Festlegungen sind Umsetzungsvorschlag der Sitzung zu OPEN-009 und zum Entwurf in docs/HELPDESK_SLA.md; Holger kann sie vor dem Merge dieser Reservierung ändern.

**Umfang:**
1. Kundenorganisationen und Kontakte (minimal): Unternehmensweite Kundenorganisationen mit Name und den Projekten, für die sie freigegeben sind; Kontakte mit Name und einer oder mehreren Mailadressen, genau einer Organisation zugeordnet. Pflege nur mit dem neuen Unternehmensrecht `helpdesk.manage`, Audit. Ein Kontakt ist kein Konto und bekommt kein Portalrecht (DEC-031); Portalkonten bleiben Paket A11.
2. Postfächer: Je Postfach IMAP Server, Port, Verschlüsselung (TLS oder StartTLS, unverschlüsselt nur ausdrücklich für Testumgebungen), Anmeldename, Kennwort, überwachter Ordner, Zielprojekt und Tickettyp; SMTP Angaben werden mit gespeichert, genutzt erst in TASK-058. Mehrere Postfächer je Projekt sind möglich. Kennwörter werden mit einem Schlüssel aus der Umgebung (`TASK_MANAGER_SECRET_KEY`, AES-GCM) verschlüsselt gespeichert, nie ausgegeben, nie protokolliert; ohne Schlüssel lassen sich keine Postfächer anlegen. Verbindungsprüfung wie bei LDAP. Pflege mit `helpdesk.manage`, Audit.
3. Abruf: Ein wiederkehrender Auftrag (Modul `jobs`, Vorschlag alle 60 Sekunden) je aktivem Postfach liest neue Nachrichten über IMAP (Jakarta Mail, Eclipse Angus). Herkunft je Postfach, Ordner, UIDVALIDITY und UID; dieselbe Nachricht erzeugt bei erneutem Abruf oder Neustart keinen zweiten Eingang. Ein Wechsel von UIDVALIDITY verarbeitet nichts neu, sondern legt einen sichtbaren Abgleichshinweis an; Nachrichten mit bekannter Message-ID desselben Postfachs werden dabei übersprungen. Nachrichten bleiben im Postfach (nur als gelesen markiert), damit nichts verloren geht. Grenze je Nachricht konfigurierbar (Vorschlag 25 MB); größere Nachrichten gehen mit Grund in die Prüfwarteschlange ohne Anhänge. Verbindungs und Anmeldefehler sind in der Postfachliste mit Zeitpunkt und Fehlerkennung ohne Geheimnisse sichtbar.
4. Zuordnung: Ein Eingang wird nur dann direkt zum Ticket, wenn die Absenderadresse genau einem Kontakt gehört, dessen Organisation für das Zielprojekt freigegeben ist, und der Mailweg den Absender bestätigt. Vorschlag für die Bestätigung: Kopfzeile `Authentication-Results` des eigenen Empfangsservers (am Postfach hinterlegte authserv-id) mit `dmarc=pass` für die Domäne der From Adresse; Postfächer ohne diese Angabe schicken jeden Eingang in die Prüfwarteschlange. Alles andere (unbekannt, mehrdeutig, nicht bestätigt, fremde Organisation in CC, Größe) geht mit Grund in die Prüfwarteschlange. CC Adressen werden nicht Kontakte oder Empfänger. Automatische Antworten und Zustellberichte (`Auto-Submitted`, `Precedence: bulk/junk/list`, `X-Autoreply`, Rückmeldungen mit leerem Return-Path) werden als technischer Eingang vermerkt, erzeugen kein Ticket und keine Antwort.
5. Prüfwarteschlange: Eingänge mit Grund, Zustand und Bearbeitung im Audit. Sehen und bearbeiten darf, wer im Zielprojekt die neue Projektaktion `helpdesk.review` hat (über die Gruppen der Projektverwaltung vergeben). Aktionen: einem vorhandenen Kontakt zuordnen und Ticket anlegen, Ticket ohne Kontakt anlegen, verwerfen mit Begründung. Doppelte Bearbeitung desselben Eingangs legt nichts doppelt an (Versionsprüfung). Neue Kontakte entstehen dort nicht automatisch; wer auch `helpdesk.manage` hat, kann aus dem Eingang einen Kontakt anlegen.
6. Ticket aus Mail: Titel aus dem Betreff, Beschreibung aus dem Textteil (HTML nur in reinen Text umgewandelt, keine aktiven Inhalte), Kundenorganisation und Kontakt als neue Ticketfelder `customer` und `contact` mit Feldrechten und Historie, Anhänge über das Modul `files` mit dessen Prüfungen. Am Ticket ein Bereich „E-Mail Verlauf“ mit der eingegangenen Nachricht (Absender, Empfänger, Zeit, Text, Anhänge), sichtbar mit der Leseregel des neuen Ticketfelds `mail`. Die Suche findet Betreff und Text über die Beschreibung wie bisher.
7. Oberfläche, Katalog de und en: Administration „Helpdesk“ mit Postfächern, Kundenorganisationen und Kontakten; Seite „Prüfwarteschlange“; Kunde, Kontakt und E-Mail Verlauf im Ticket.

Nicht Teil (TASK-058 oder später): Antworten über SMTP, Versandzustände, Zuordnung von Antworten zu bestehenden Tickets, Benachrichtigungen, SLA, Portal und Portalkonten, OAuth Anmeldung bei Mailanbietern, Microsoft 365 Sonderanbindung, Weiterleitungsregeln über die Prüfwarteschlange hinaus, Aufbewahrung verworfener Eingänge (TASK-010 Vorschlag), Virenscanner.

**Schnittstellen und Arbeitsbereich:** Backend (neues Modul `helpdesk`, Migration V0026, Vertrag `contracts/helpdesk.openapi.yaml`, Rechtekatalog, Ticketfelder `customer`, `contact` und `mail`, Bearbeiter für `jobs`, Abhängigkeiten Jakarta Mail und GreenMail für Tests), Frontend (Administration, Prüfwarteschlange, Ticketansicht, Browsertests), Vorführimage (Schlüssel in der Umgebung, optional ein lokaler Testmailserver), Dokumentation (docs/HELPDESK_SLA.md Nachtrag, backend/README.md Betrieb und Sicherung des Schlüssels), DEC-086.

**Abnahmekriterien:** Eine Nachricht eines bestätigten bekannten Kontakts einer freigegebenen Organisation erzeugt genau ein Ticket mit Kunde, Kontakt, Text und Anhängen, auch nach erneutem Abruf, Neustart oder zwei gleichzeitigen Ausführern; ein unbekannter, nicht bestätigter oder mehrdeutiger Absender landet mit Grund in der Prüfwarteschlange und erzeugt kein Ticket; eine gefälschte From Adresse ohne `dmarc=pass` des eigenen Empfangsservers führt nie direkt zum Ticket; eine automatische Antwort erzeugt kein Ticket; ein Wechsel von UIDVALIDITY erzeugt keine Duplikate; die Prüfwarteschlange ist ohne `helpdesk.review` im Zielprojekt nicht sichtbar (403), eine doppelte Bearbeitung legt nichts doppelt an; Kennwörter erscheinen weder in Antworten noch im Protokoll noch unverschlüsselt in der Datenbank; ein falsches Kennwort oder ein nicht erreichbarer Server ist in der Postfachliste sichtbar; ohne Leserecht auf `mail`, `customer` oder `contact` erscheinen diese Felder weder in Ticket noch Historie noch Suche. Tests gegen GreenMail. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt fachlich ab.

**Ergebnis:** Umgesetzt im Ergebnis PR vom Branch claude/project-thread-b4u17z, beschrieben in DEC-086, backend/README.md (Helpdesk Eingang), contracts/helpdesk.openapi.yaml und dem Nachtrag in docs/HELPDESK_SLA.md. Modul `helpdesk` mit Migration V0026, Port `AttachmentIntake` im Modul `files`, Ticketfelder `customer`, `contact` und `mail`, Rechte `helpdesk.manage` und `helpdesk.review`, Oberfläche Helpdesk, Prüfwarteschlange und Bereich „Kunde und Mails“ mit E-Mail Verlauf am Ticket. Abweichung vom Abnahmekriterium „nicht sichtbar (403)“: Ohne `helpdesk.review` ist die Liste leer und eine einzelne Mail antwortet mit 404, damit fremde Eingänge nicht einmal als vorhanden erkennbar sind; Holger entscheidet bei der Abnahme. Prüfungen: `HelpdeskApiTest` gegen GreenMail und PostgreSQL (genau ein Ticket auch nach erneutem Abruf, verlorener Position und zwei gleichzeitigen Abrufen; unbekannt, unbestätigt, gefälschter Header und fremder Kunde in die Prüfwarteschlange; automatische und zu große Mails; neue UIDVALIDITY ohne Duplikat; Passwort weder in Antworten noch Audit noch im Klartext in der Datenbank; falsches Passwort und geschlossener Port in der Liste; Rechte und Versionen der Prüfwarteschlange; Feldregeln), `SenderCheckTest`, `ParsedMailTest`, `SecretBoxTest`, `HelpdeskContractTest`, alle Backendtests mit `./mvnw verify`, Frontend Typprüfung, Einheitstests, Build und alle Browsertests einschließlich `helpdesk.test.mjs`, `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `python3 -m unittest discover -s tests -v`, `git diff --check`. Nicht lokal gelaufen: Backend mit Java 25 und PostgreSQL 18 (CI), Docker Image.

**Nächster Schritt:** Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt fachlich ab. Danach wird TASK-058 freigegeben.

## TASK-058 Helpdesk Antworten über SMTP (Paket A9, Teil 2)

**Status:** BLOCKIERT · **Verantwortlich:** claude-20261028-helpdesk-01 · **Aktualisiert:** 2026-10-28

**Bezug:** REQ-HD-001 (Versand, erkennbare ausstehende Sendungen), REQ-HD-002 (Antworten zuordnen, Wiederholungen, Schleifen), REQ-HD-005, REQ-TKT-005 (interne Versandaufträge, unklare Zustellung). **Abhängigkeiten:** TASK-057. **Arbeitsstand:** Zweiter Teil von Paket A9 mit derselben Zustimmung vom 28. Oktober 2026; blockiert, bis das Ergebnis von TASK-057 vorliegt. Branch wird beim Start festgelegt; Ergebnis mit eigener DEC und Migration, Nummern beim Start auf main prüfen.

**Umfang:** Vorschlag: Kundenantwort am Ticket an den Kontakt über das SMTP des Postfachs, sichtbar getrennt von internen Arbeiten, mit Prüfung der aktuellen Rechte vor dem Versand; Versand als Auftrag im Modul `jobs` mit den Zuständen aus docs/HELPDESK_SLA.md (ausstehend, vom Server angenommen, sicher gescheitert, Versandstatus unklar ohne blinde Wiederholung); eigene Message-ID und Antwortkennung, damit Antworten des Kunden über `In-Reply-To` und `References` zusammen mit der Absenderprüfung aus TASK-057 dem bestehenden Ticket zugeordnet werden, sonst Prüfwarteschlange; keine automatische Antwort auf automatische Nachrichten; ausstehende und gescheiterte Sendungen für zuständige Personen sichtbar. Nicht Teil: Vorlagen mit Branding (A17), Benachrichtigungen, SLA, KI Entwürfe, Portal.

**Abnahmekriterien:** Werden mit dem Start nach Vorlage des Ergebnisses von TASK-057 festgelegt; mindestens: genau ein interner Versandauftrag je bestätigter Antwort auch nach Neustart, unklarer Versandstatus ohne automatische Wiederholung, Antwort des Kunden landet im richtigen Ticket nur bei bestätigtem Absender, keine Mailschleife mit einem Autoresponder.

**Nächster Schritt:** Nach Ergebnis PR von TASK-057 Blocker aufheben und Umsetzung beginnen.

## TASK-059 Befunde F1 bis F5 der Prüfung von TASK-057 beheben

**Status:** BEREIT ZUR PRÜFUNG · **Verantwortlich:** claude-20261028-helpdeskfix-01 · **Aktualisiert:** 2026-10-28

**Bezug:** REQ-HD-001 (geschützte Zugangsdaten), REQ-HD-002 (Duplikate, Zuordnung), REQ-ORG-002, Feldrechte nach DEC-067. **Abhängigkeiten:** TASK-057 (Ergebnis PR #189), Prüfbericht docs/REVIEW_TASK-057_PR-189.md (PR #191). **Arbeitsstand:** Holgers Wahl „F1 bis F5 plus H1 und H3“ vom 28. Oktober 2026 im Projektgespräch. Branch claude/project-thread-b4u17z nach dem Merge dieser Reservierung; Ergebnis mit DEC-087 und Korrekturmigration V0027.

**Umfang:** Nur die Befunde und Hinweise aus dem Prüfbericht:

1. F1: Fehler beim IMAP Abruf und bei der Verbindungsprüfung protokollieren nur Postfach und Fehlercode, keinen Text des Servers und keine verschachtelte Ursache.
2. F2: Der Abgleich über die Message-ID nach einem Wechsel von UIDVALIDITY wird dauerhaft gespeichert und gilt über alle Abrufpakete, Fehler und Neustarts, bis der Ordner vollständig abgeglichen ist.
3. F3: Die Herkunft einer Nachricht enthält die konkrete Quelle (eine Quellgeneration je Änderung von Server, Port, Konto oder Ordner), Korrekturmigration V0027 für bestehende Herkünfte; ein laufender Abruf schreibt seine Position nur für die noch aktuelle Quelle fort.
4. F4: Die Kundenauswahl am Ticket liefert Kontakte nur mit Lese und Schreibrecht auf `contact`, Kunden nur mit den Rechten auf `customer`.
5. F5: Alle Empfängeradressen zählen für die Prüfung auf fremde Kunden; eine Mail über der Verarbeitungsgrenze geht mit eigenem Grund in die Prüfwarteschlange.
6. H1: Dokumentation der Vertrauensvoraussetzung, dass der eigene Empfangsserver eingeschleuste Authentication-Results Köpfe seiner Kennung entfernt.
7. H3: Die Browsertests des Helpdesks und die Verbindungszahl der Backendtests werden stabil, beide waren in CI zeitweise rot.

Nicht Teil: H2 und H4 (Entscheidung bei der Abnahme), H5 und H6, TASK-058.

**Abnahmekriterien:** Für jeden Befund ein Regressionstest nach der Nachstellung im Prüfbericht (E2, E3, E4, E6, E7), der vor der Korrektur rot und danach grün ist; kein Passwort im Protokoll bei einem Server, der die Anmeldung spiegelt; keine doppelten Tickets bei mehr wieder aufgetauchten Nachrichten als dem Paketlimit; keine übergangene Nachricht nach Wechsel von Konto oder Ordner; keine Kontaktdaten ohne Leserecht auf `contact`; ein fremder Kunde an beliebiger Empfängerposition führt in die Prüfwarteschlange; alle Backend, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-057 und TASK-059 zusammen ab.

**Ergebnis:** Umgesetzt im Ergebnis PR vom Branch claude/project-thread-b4u17z, beschrieben in DEC-087, backend/README.md (Helpdesk Eingang) und dem Nachtrag TASK-059 in docs/HELPDESK_SLA.md. F1: Protokoll nur mit Postfach, Code und Klassen der Ursachen (`MailClient.kinds`). F2: Grenze `compare_until` am Postfach, Abgleich und Eintrag unter Sperre des Postfachs. F3: Quelle `source` am Postfach und in der Herkunft, Migration V0027, Positionen nur für die aktuelle Quelle. F4: Kundenauswahl mit Lese und Schreibregel für `customer`, Kontakte nur mit beiden Regeln für `contact`. F5: alle Empfänger bis 1000 in einer Abfrage, sonst Grund `recipients_incomplete`. H1: Vertrauensvoraussetzung dokumentiert. H3: Browsertests warten auf den Endzustand; Backendtests mit höchstens acht offenen Spring Kontexten (`src/test/resources/spring.properties`) und zehn Sekunden Leerlauf je Verbindung. Prüfungen: fünf neue Fälle in `HelpdeskApiTest` nach den Nachstellungen E2, E3, E4, E6 und E7 des Berichts, vor der Korrektur rot und danach grün; volle Backendsuite mit `./mvnw clean verify` (JDK 21, PostgreSQL 16.15) 366 Tests grün; `scripts/lifecycle-check.sh` grün; Frontend `npm run verify` und alle 124 Browsertests grün, `helpdesk.test.mjs` dreimal hintereinander grün; `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `python3 -m unittest discover -s tests -v`, `git diff --check`. Nicht lokal gelaufen: Backend mit Java 25 und PostgreSQL 18 (CI), Docker Image.

**Nächster Schritt:** Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-057 und TASK-059 zusammen ab und entscheidet dabei H2 und H4.

## TASK-060 Befunde R1 bis R3 der Nachprüfung von TASK-059 beheben

**Status:** IN ARBEIT · **Verantwortlich:** claude-20261028-helpdeskfix-02 · **Aktualisiert:** 2026-10-28

**Bezug:** REQ-HD-002 (Duplikate, Zuordnung), REQ-ORG-002, Feldrechte nach DEC-067. **Abhängigkeiten:** TASK-059 (Ergebnis PR #193), Prüfbericht docs/REVIEW_TASK-059_PR-193.md (PR #194). **Arbeitsstand:** Holgers Wahl „R1 bis R3 plus Testmodell“ vom 28. Oktober 2026 im Projektgespräch. Branch claude/project-thread-b4u17z nach dem Merge dieser Reservierung; Ergebnis mit DEC-088 und, falls der Abrufzustand eine Spalte braucht, Migration V0028.

**Umfang:** Nur die Befunde und der Hinweis aus dem Prüfbericht:

1. R1: Position und Abgleichgrenze eines Postfachs werden an Quelle und UIDVALIDITY gebunden. Ein gewöhnliches Fortschreiben der Position wechselt die UIDVALIDITY nicht, ein veralteter Abruf schreibt nichts, und der Neustart eines Ordners gilt nur gegen den erwarteten bisherigen Stand.
2. R2: Adressgruppen in To und CC werden in ihre Postfachadressen aufgelöst, bevor normalisiert, begrenzt und auf fremde Kunden geprüft wird; eine nicht vollständig lesbare Gruppe führt in die Prüfwarteschlange.
3. R3: Die Grenze von 1000 Empfängern gilt gemeinsam für To und CC einer Mail, auch bei zu großen Mails, die nur über den Kopf gelesen werden.
4. Hinweis 3: Das Testmodell der Browsertests liefert für die Kundenauswahl Kunden und Kontakte nach denselben Regeln wie das Backend.

Nicht Teil: H2 und H4 der Prüfung von TASK-057 (Entscheidung bei der Abnahme), H5 und H6, TASK-058.

**Abnahmekriterien:** Für jeden Befund ein Regressionstest nach der Nachstellung im Prüfbericht (E6, E1, E2), der vor der Korrektur rot und danach grün ist; nach überlappenden Abrufen über einen Wechsel der UIDVALIDITY wird die neue Mail übernommen; ein fremder Kundenkontakt in einer Gruppe in To oder CC führt in die Prüfwarteschlange, ebenso insgesamt 1001 Empfänger bei beliebiger Aufteilung auf To und CC, 1000 dagegen nicht; alle Backend, Frontend und Browsertests grün. Getrennte KI prüft das Ergebnis (DEC-040); Holger nimmt TASK-057, TASK-059 und TASK-060 zusammen ab.

**Nächster Schritt:** Nach dem Merge dieser Reservierung umsetzen.
