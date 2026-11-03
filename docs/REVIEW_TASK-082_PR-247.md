# Prüfung von TASK-082 / PR #247

**Empfehlung: nachbessern.** Zwei reproduzierte Befunde verhindern die Abnahme: Beim Bestätigen kann ein anderer Antworttext versendet werden als der angezeigte; außerdem akzeptiert die Vorschau bestimmte https-Adressen, deren Speicherung mit HTTP 500 scheitert. Für die Maskierung, die Speicherung der bestätigten Mail und den MIME-Aufbau wurde kein weiterer Fehler gefunden.

## Prüfstand und Auftrag

- Sitzung: `codex-20261103-mailtemplate-review-01`, unabhängige zweite KI, 3. November 2026.
- Repository: https://github.com/1uedev/task-manager, PR https://github.com/1uedev/task-manager/pull/247.
- Geprüfter Branch: `claude/project-thread-tzp0cw`, Commit `79b1d38ba979a4455943e1f8668d60303acd6ca1`; PR zum Prüfzeitpunkt offen und Entwurf. Basis `main`: `4d103607ad0f9c3e8dee08f5ebf7748135eca8b3`.
- Die Anwendungstests starteten auf `8f8f6bb3ae5949b53e38e5b8fb4f850a0631db19`. Der während der Prüfung hinzugekommene Commit `79b1d38` ergänzt ausschließlich die Tabellenliste in `backend/scripts/lifecycle-check.sh:98`. Er wurde übernommen und geprüft; Anwendungscode, Verträge und Tests sind gegenüber dem getesteten Stand identisch. Repositoryprüfungen wurden auf dem Endstand wiederholt.
- Gelesen: `AGENTS.md`, `README.md`, `TODO.md` einschließlich TASK-082, `DONE.md`, `docs/AI_WORKFLOW.md`, relevante Anforderungen und Planung, DEC-112, beide genannten OpenAPI-Verträge, der Mailvorlagenabschnitt im Backend-README und der TASK-082-Nachtrag in `docs/HELPDESK_SLA.md`.
- Umfang: Paket A17 Teil 2 gemäß Holgers Auftrag und DEC-112. Eigener Prüfklon, ausschließlich dieser Bericht ergänzt. Keine Änderungen an Anwendungscode, Tests, Verträgen, Migrationen oder Aufgabenständen; keine Veröffentlichung auf GitHub.

## Befunde

### F1 – P2: Bestätigte Antwort kann von der geprüften Vorschau abweichen

**Datei/Zeile:** `frontend/src/pages/TicketHelpdesk.tsx:321`, ergänzend Zeilen 324–330 und 350–357.

**Befund:** `check()` schickt die derzeitigen Formulardaten an die Vorschau und übernimmt die Antwort nach dem Warten ohne Prüfung, ob diese Daten noch aktuell sind. Währenddessen sind Empfänger, Postfach, Betreff und Antworttext weiter bedienbar; nur die Prüfschaltfläche ist gesperrt. `confirm()` sendet anschließend die aktuellen Zustandswerte des Formulars. Die Vorschau gehört damit gegebenenfalls zu einem älteren Entwurf.

**Nachweis:** Im Browser gegen den Produktionsbuild wurde die Vorschauanfrage mit dem Antworttext „Geprüfter Text A“ angehalten. Während sie lief, wurde das Textfeld auf „Ungeprüfter Text B“ geändert. Nach Freigabe der Anfrage zeigte die Prüfung weiterhin A. „Jetzt senden“ bestätigte B; auch die festgehaltene Mail enthielt B. Der Testersatz wurde für diesen Fall ausschließlich zum Verzögern der bestehenden Vorschau verwendet; der Fehler entsteht in der Oberfläche.

**Auswirkung:** Die Person bestätigt nicht zuverlässig den Inhalt, den sie geprüft hat. Derselbe fehlende Abgleich betrifft auch die übrigen Formulardaten. Das verletzt die vorgesehene Prüfung der fertigen Antwort vor der Bestätigung. Die nach DEC-112 zulässige erneute Auswertung der dann gültigen Vorlage ist hiervon unabhängig.

**Vorschlag:** Den geprüften Entwurf einschließlich Postfach, Empfänger, Betreff und Text festhalten und die Bestätigung daran binden. Änderungen müssen eine laufende beziehungsweise vorhandene Vorschau ungültig machen; verspätete Ergebnisse dürfen nicht zur Bestätigung führen. Alternativ die Eingaben während des Prüfens sperren und Abbruch beziehungsweise erneutes Öffnen ebenfalls gegen verspätete Ergebnisse absichern. Einen Browsertest mit gehaltener Vorschauanfrage und zwischenzeitlicher Eingabe ergänzen.

### F2 – P2: Adressprüfung und V0049 widersprechen sich bei der Großschreibung des Schemas

**Datei/Zeile:** `backend/src/main/java/taskmanager/appearance/internal/MailTemplateService.java:239` und `:246`; `backend/src/main/resources/db/migration/V0049__appearance_mail_template.sql:7`.

**Befund:** Die Anwendung prüft das Schema ohne Beachtung der Großschreibung, gibt die Adresse danach aber unverändert zurück. Die Datenbank erlaubt ausschließlich den kleingeschriebenen Anfang `https://` mittels eines großschreibungsabhängigen regulären Ausdrucks.

**Nachweis:** Über die echte HTTP-API und PostgreSQL 16, jeweils mit gültigem Recht und aktuellem If-Match:

| Adresse | Vorlagenvorschau | Speichern |
|---|---:|---:|
| `https://support.example.com` | 200 | 200 |
| `HTTPS://support.example.com` | 200 | 500 `server.internal_error` |
| `Https://support.example.com` | 200 | 500 `server.internal_error` |

**Auswirkung:** Eine zuvor als gültig dargestellte Adresse verhindert das Speichern der gesamten Vorlage. Statt eines nachvollziehbaren Prüfhinweises erscheint ein interner Serverfehler. Die Transaktion rollt zurück; eine Umgehung der https-Beschränkung wurde dadurch nicht gefunden.

**Vorschlag:** Das Schema vor der Rückgabe auf `https` vereinheitlichen oder die Datenbankprüfung an dieselbe zulässige Schreibweise anpassen. Pfad und Abfrage dürfen dabei ihre Großschreibung behalten. Vorschau und Speicherung für beide Schreibweisen gemeinsam testen. Falls V0049 bereits in einer Installation angewendet wurde, eine Korrekturmigration statt einer Änderung ihrer Prüfsumme verwenden.

## Hinweise

### H1 – P3: Fokus geht beim Speichern der Vorlage verloren

**Datei/Zeile:** `frontend/src/pages/MailTemplateSection.tsx:60` und `:62`.

**Befund/Nachweis:** Der Editor wird durch `key={template.version}` nach erfolgreichem Speichern vollständig ersetzt. Im Browser lag `document.activeElement` anschließend auf `BODY`, nicht mehr bei der Vorlagenpflege. Die Erfolgsmeldung ist zwar als Status ausgezeichnet, stellt den Fokus aber nicht wieder her. Auch beim Übergang zur Antwortprüfung wurde Fokus auf `BODY` beobachtet (`frontend/src/pages/TicketHelpdesk.tsx:325`). Dieser zweite Übergang bestand grundsätzlich bereits vor TASK-082.

**Auswirkung:** Tastatur- und Screenreader-Nutzende verlieren ihren Bedienort. Die neuen Felder sind ansonsten beschriftet; dieser Hinweis betrifft den Fokus nach Zustandswechseln.

**Vorschlag:** Nach dem Speichern den Fokus gezielt in den erneuerten Editor beziehungsweise auf eine geeignete Bestätigung setzen. Beim Öffnen der fertigen Antwort einen sinnvoll beschrifteten Prüfbereich fokussieren und beim Zurückgehen zum Formular zurückkehren. Den Fokus mit einer Tastaturprüfung absichern.

### H2 – P3: Vorlagenvorschau akzeptiert eine vertragsfremde Sprache

**Datei/Zeile:** `backend/src/main/java/taskmanager/appearance/internal/MailTemplateService.java:154`; `contracts/appearance.openapi.yaml:380` und `:383`.

**Befund/Nachweis:** Der Vertrag verlangt `language` mit `de` oder `en`. Eine echte Anfrage mit `language: "fr"` erhielt jedoch 200 und eine deutsche Vorschau; auch eine fehlende Sprache fällt im Code auf Deutsch zurück. Der Testersatz verwendet denselben Rückfall. Die Kontaktsprache selbst wird korrekt auf `de` und `en` begrenzt.

**Auswirkung:** Fehler von API-Aufrufern werden verdeckt; Vertrag und beobachtbares Verhalten der neuen Vorschau stimmen an dieser Stelle nicht überein. Die reguläre Sprachwahl der Oberfläche ist nicht betroffen.

**Vorschlag:** Die erforderliche Sprache an der Vorschaugrenze ausdrücklich prüfen und ungültige beziehungsweise fehlende Werte mit 400 ablehnen; Fehlercode, Vertrag und Testersatz entsprechend angleichen. Den internen, ausdrücklich dokumentierten Rückfall beim Rendern getrennt davon behandeln.

### H3 – Browser-Gesamtprüfung bleibt rot

**Datei/Zeile:** `frontend/tests/browser/portal.test.mjs:152`, vorausgehende Wartebedingung in Zeile 151.

**Befund/Nachweis:** Im parallelen Lauf wurden 177 erfolgreiche und drei fehlgeschlagene Prüfungen gemeldet, insgesamt 180 einschließlich des Fehlers der LDAP-Testsuite. Betroffen waren LDAP, die Prüfung des Portalauftritts und eine Zeitüberschreitung bei der Tagpflege. Die vollständige Wiederholung mit `--test-concurrency=1` ergab 178 von 179 erfolgreich. Verbleibend: Der Portalkopf zeigte beim unmittelbaren Vergleich `Task Manager` statt `Nordlicht Service`. Die neuen Mailvorlagenprüfungen bestanden.

**Auswirkung:** Ein vollständig grüner Browsernachweis liegt in dieser Umgebung nicht vor. Die Fehlerstelle gehört zur TASK-081-Prüfung und ist in diesem PR unverändert; eine durch TASK-082 eingeführte fachliche Regression ist damit nicht nachgewiesen. Die beiden anderen Fehler wiederholten sich seriell nicht.

**Vorschlag:** Das Laden des Auftritts gezielt abwarten und bei Bedarf gegen den Basisstand vergleichen, bevor ein Produktfehler oder eine reine Test-Wartebedingung zugeordnet wird. Nicht ohne weiteren Auftrag den Portalauftritt umbauen. Den verbleibenden Fehler vor einer Aussage „alle Browserprüfungen grün“ klären.

## Ergebnis zu den beauftragten Prüfpunkten

| Prüfpunkt | Ergebnis und Grundlage |
|---|---|
| Platzhalter und Maskierung | `MailLayout` ersetzt nur den festen Katalog und wertet eingesetzte Werte nicht erneut als Vorlage aus. Unbekannte Platzhalter, offene und schließende Doppelklammern sowie verschachtelte Fehlformen wurden mit 400 abgelehnt. HTML in Kontaktname, Tickettitel, Absendername und Antworttext wurde maskiert; der Antworttext behielt einen enthaltenen Platzhalter als gewöhnlichen Text. Platzhalterwerte werden auf eine Zeile gebracht. Farbe ist auf einen Hexwert begrenzt; Portalanker entstehen nur aus der geprüften https-Adresse. F2 betrifft deren Speicherung. |
| Festhalten der Mail | `ReplyService.create` rendert über denselben Entwurf wie die Vorschau und schreibt Antwort, Text, HTML, Logo-Verweis, Auftrag und Historie in einer Transaktion. `HelpdeskRepository.insertReplyMail` hält tatsächliche Logobytes nach Inhaltsdigest fest. `ReplySender` liest die gespeicherte Fassung; `SmtpMailSender` rendert keine Vorlage nach. Die Backendprüfung über den SMTP-Testserver bestätigt die unveränderte Mail nach Vorlagen-/Auftrittsänderung und beim erneuten Senden. F1 liegt vor der Bestätigung in der Oberfläche. |
| Altantworten | V0050 schreibt keine rückwirkenden Mailfassungen. Fehlt `helpdesk_reply_mail`, sendet der Sender den ursprünglichen Antworttext ohne aktuelle Vorlage. Dies wurde zusätzlich mit einer Antwort ohne diesen Datensatz über den SMTP-Testserver nachgestellt. Maßgebliche technische Grenze ist V0050, wie DEC-112 dokumentiert, nicht V0049. |
| Rechte | Vorlagenrouten prüfen vollständige Anmeldung und `appearance.manage`; der Dienst prüft das Recht nochmals. Vorlagenvorschauen verwenden fest definierte Beispieldaten. Vorschau und Senden einer Antwort rufen dieselbe `draft()`-Prüfung auf. Zusätzlich über die echte API nachgestellt: Ohne Leserecht auf `title` blieb ein markierter Tickettitel aus Text und HTML entfernt; nach Entzug von `helpdesk.reply` antworteten Vorschau und Senden beide mit 403. |
| Kopfzeilen und MIME | Name, Kontaktname, Tickettitel und Antworttext werden nicht in zusätzliche Kopfzeilen übernommen. Der bestehende SMTP-Adapter normalisiert Anzeigenamen, kodiert den Betreff und prüft Adressen und Thread-Kennungen. Neuer Aufbau: `multipart/alternative`, Klartext zuerst; danach HTML, mit Logo als `multipart/related`, `Content-ID: <logo>` und Inline-Disposition. Ohne Logo folgt HTML direkt. Text und HTML werden ausdrücklich als UTF-8 gesetzt. SMTP-Tests prüfen Teile, Inhalt und Logobytes; bei der zusätzlichen Altmailprüfung wurden keine alleinstehenden LF-Zeilenenden festgestellt. Keine HTML- oder Kopfzeileninjektion gefunden. |
| Version, Audit, Zurücksetzen | Fehlendes If-Match ergibt 428, veraltete beziehungsweise unzulässige Version 412; Versionsprüfung unter Zeilensperre. Speichern auditiert tatsächlich geänderte Sprachen einzeln, eine Adressänderung separat; identische Texte erzeugen keine zusätzlichen Änderungsereignisse. Zurücksetzen hat ein eigenes Auditereignis und behält die Adresse. Die vorhandenen API-Tests bestätigen diese Abläufe. |
| Kontaktsprache und Demo | V0050 ergänzt bestehende Kontakte mit `de` und begrenzt die Spalte auf `de`/`en`. Anlage ohne Sprache verwendet Deutsch; beim Ändern hält ein ausgelassenes Feld die bisherige Sprache. Sprache steht in der Auditbeschreibung und im Vertrag; Kontaktformular und Liste zeigen sie. Demo enthält Signaturen für beide Sprachen und den englischen Südtechnik-Kontakt. Vollständiger Demoaufbau wurde nicht zusätzlich durchgeführt. |
| Migrationen und Verträge | V0049 und V0050 folgen auf die bereits in der Basis vorhandenen V0047/V0048; keine neue Lücke und keine Änderung älterer Migrationen. 47 Migrationen in Lebenszyklusskript und Anwendungstest; vollständige Versions- und Tabellenlisten in `DatabaseFoundationTest`. Der letzte PR-Commit ergänzt auch die drei neuen Tabellen in der Skriptprüfung. Routenvergleich für appearance/helpdesk und Typgenerierung bestanden. H2 ist die gefundene Abweichung bei der Vorlagensprache. |
| Oberfläche und Barrierefreiheit | Mailvorlage auf „Erscheinungsbild“, Kontaktsprache und fertige Antwort vorhanden. `MailView.tsx` setzt ausschließlich React-Textknoten aus den Teilen ein; `mail.html` wird nicht in die Seite eingesetzt. Zusätzliche Browserprüfung und Bildkontrolle bei 320 px: kein horizontaler Überlauf bei Vorlagenpflege und Antwortprüfung; Textfelder besitzen Beschriftungen; Kontrastmessung ohne Fehler in hellem und dunklem Vorlagenbild sowie in der Antwortprüfung. H1 beschreibt die Fokuslücke. Keine umfassende Prüfung mit realem Screenreader oder sämtlichen Mailprogrammen durchgeführt. |
| Auftragsgrenze | Keine PDFs, weiteren Mailarten, Projekt-/Postfachvorlagen, freie HTML-Eingabe oder Betreffvorlage gefunden. Die zusätzlichen Ports dienen ausschließlich dem beauftragten Rendern. |

## Ausgeführte Prüfungen

Windows, Python 3.12, Node 24.22.0/npm 11.19.1, JDK 25.0.4.1 gemäß `backend/pom.xml`; eigene lokale PostgreSQL-16.15-Testinstanz. `python3` beziehungsweise `./mvnw` wurden mit dem verfügbaren Python und `mvnw.cmd` ausgeführt. Lokale Abhängigkeitscaches wurden verwendet.

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | Erfolgreich; auf dem Endstand einschließlich Bericht wiederholt. |
| `python3 scripts/task_board.py --check` | Erfolgreich; auf dem Endstand wiederholt. |
| `python3 -m unittest discover -s tests -v` | 38 Tests, keine Fehler; einer unter Windows übersprungen, weil er das Umschalten der Unix-Zeitzone benötigt. |
| Backend `./mvnw verify` | BUILD SUCCESS, 515 Tests, 0 Fehler, 0 übersprungen, mit PostgreSQL 16.15. Einschließlich Vertrags-, Datenbank-, Anwendungslebenszyklus- und SMTP-Tests. |
| Frontend `npm ci --offline --no-audit --no-fund` | Erfolgreich, 58 Pakete installiert. |
| Frontend `npm run verify` | Erfolgreich: Typgenerierung, Typprüfung, 79 Tests und Produktionsbuild. Bestehender Hinweis auf ein JavaScript-Paket über 500 kB. |
| Frontend `npm run test:browser` | Ausgeführt, nicht erfolgreich: erster Lauf 177/180; vollständige serielle Wiederholung 178/179, siehe H3. |
| Zusätzliche API-/SMTP-Prüffälle | Adressvarianten, fehlerhafte Platzhalter, HTML-Werte, fremde Vorlagensprache, Titel ohne Leserecht, Antwortrecht entzogen und Altantwort ohne gespeicherte Vorlage. F2 und H2 reproduziert; übrige genannten Kontrollen bestanden. |
| Zusätzliche Browserprüffälle | Verzögerte Antwortvorschau mit Eingabewechsel: F1 reproduziert. Fokus nach Vorlagenspeicherung: H1 reproduziert. Beschriftungen, Kontrast und Darstellung bei 320 px geprüft. |
| `git diff --check` und Prüfung des neuen Berichts auf Leerraumfehler | Erfolgreich. |

`backend/scripts/lifecycle-check.sh` wurde nicht zusätzlich als Shellprozess ausgeführt; Versionszahl und Tabellenliste wurden gelesen und mit den erfolgreichen Datenbank-/Anwendungslebenszyklustests abgeglichen. Kein Dockerlauf und kein Test über einen externen SMTP-Dienst. Die temporäre eigene PostgreSQL-Instanz wurde nach Abschluss beendet.

Die ausführlichen lokalen Protokolle und unabhängigen Prüffälle liegen außerhalb des Prüfklons unter `../review-tools/mailtemplate01-*` beziehungsweise `../review-tools/MailTemplate082Probe.java` und `../review-tools/mailtemplate082-browser-probe.mjs`. Die Beobachtungen und Ergebnisse sind oben vollständig beschrieben; die Prüfhilfen sind keine vorgeschlagenen Produktänderungen.

## Abnahmeempfehlung

**Nachbessern:** F1 und F2 beheben und die beschriebenen Gegenproben absichern. H1 und H2 bei der Nacharbeit berücksichtigen; H3 klären, bevor die Browser-Gesamtprüfung als erfolgreich gilt. Die grundlegende Umsetzung von Vorlage, Berechtigungen, Sprache, Maskierung, gespeichertem Mailinhalt und MIME-Aufbau entspricht im geprüften Umfang dem Auftrag.
