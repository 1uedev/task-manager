# Prüfung TASK-058 / PR #202 (Codex)

Ergebnis: **Noch nicht zur fachlichen Abnahme empfohlen. Vier P2-Befunde betreffen die Zuverlässigkeit des Versandzustands, die Wiederherstellung nach dem letzten Versuch, die Adressverarbeitung und die Kundengrenze bei gleichzeitigen Änderungen.** Die regulären Prüfungen bestehen: 380 Backendtests, 77 Frontendtests und 128 Browsertests. Die zusätzlichen Nachstellungen zeigen Fehler, die diese Tests nicht abdecken.

Berichtsabschluss: 30. Oktober 2026. Testausführung: 29. Oktober 2026. Codex, Sitzung `codex-20261029-task058-review-01`, getrennte technische Prüfung nach DEC-040. Keine Mitarbeit an der Umsetzung, keine fachliche Abnahme und keine Änderung des Aufgabenstatus.

Hinweis zur Ablage: Codex konnte den Bericht nicht selbst veröffentlichen. Holger hat ihn am 30. Oktober 2026 im Projektgespräch übergeben; die Sitzung claude-20261030-replyfix-01 legt ihn unverändert ab. Daneben gibt es die getrennte Claude Prüfung [REVIEW_TASK-058_PR-202.md](REVIEW_TASK-058_PR-202.md). Die Zusatznachstellungen E1 bis E9 dieses Berichts liegen nur in der Prüfkopie von Codex und sind nicht im Repository.

## Grundlage und Umfang

- [PR #202](https://github.com/1uedev/task-manager/pull/202), letzter PR-Commit **`72830096a05dab052362c2dfef628244f08adf39`**; Vergleichsbasis `abbac7084f4444f48ad0401d1f1f6244cfc964d8`. Der PR war beim Prüfstart bereits zusammengeführt; der ursprüngliche Branch `claude/project-thread-b4u17z` nicht mehr vorhanden. Geprüft wurde der feste PR-Stand, nicht ein späterer Stand von main.
- Maßstab: AGENTS.md, README.md, TODO.md mit den acht Kriterien von TASK-058, DONE.md, docs/AI_WORKFLOW.md, DEC-090/DEC-091, die einschlägigen Anforderungen REQ-HD-001/-002/-005 und REQ-TKT-005, backend/README.md („Helpdesk Antworten“), docs/HELPDESK_SLA.md und contracts/helpdesk.openapi.yaml.
- Durchgesehen wurden die Versanddienste, ihre Datenbankoperationen, das Zusammenspiel mit dem Jobs-Modul, die Änderungen am Eingang und an der manuellen Zuordnung, die sechs neuen Routen, Migration V0028, die Oberfläche und die zugehörigen Tests. Der Bericht folgt dem Aufbau der früheren Helpdesk-Prüfberichte.
- Reguläre Prüfungen liefen in einer getrennten Prüfkopie. Zusatztests wurden ausschließlich in einer zweiten Kopie ergänzt; beide verwendeten unveränderten Produktcode dieses Commits und synthetische Daten. In der ausgelieferten Prüfkopie ist ausschließlich dieser Bericht neu.

Die acht Kriterien stehen ausdrücklich als Vorschlag der Umsetzungssitzung zur Prüfung. Ihre Erfüllung und ihre ausreichende Schärfe werden deshalb getrennt bewertet.

## Ausgeführte Prüfungen

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| `python scripts/validate_repository.py` | Erfolgreich |
| `python scripts/task_board.py --check` | Erfolgreich; Aufgabenübersicht unverändert |
| `python -m unittest discover -s tests -v` | 38 Tests, erfolgreich; ein Skip für den nur unter Unix möglichen Zeitzonenwechsel |
| Backend `verify`, Maven 3.9.16, JDK 25.0.4.1, lokales PostgreSQL 18.6 | **380 Tests, 0 Fehler, 0 Ausführungsfehler, 0 Skips; BUILD SUCCESS** |
| Darin `HelpdeskApiTest` | **27/27 erfolgreich**, einschließlich der sechs neuen Antworttests |
| Darin `HelpdeskContractTest` | Erfolgreich: alle 26 dokumentierten Operationen entsprechen den gemappten Routen |
| Frontend `npm ci --offline` und `npm run verify`, Node 24.22.0 | Erfolgreich: Generierung, Typprüfung, **77 Tests**, Build |
| Frontend `npm run test:browser`, installiertes Chrome über Playwright | **128/128 erfolgreich**, einschließlich der vier neuen Antworttests |
| Zusatznachstellungen E1–E8 | 8/8 Beobachtungstests erfolgreich; darunter die nachgewiesenen Fehlzustände aus F1–F3 |
| Zusatznachstellung E9 | 1/1 Beobachtungstest erfolgreich; bestätigt F4 |
| `git diff --check` | Erfolgreich; abschließende Kontrolle auf ausschließliche Berichtsänderung |

Die Zusatztests sind **keine bestandenen Abnahmetests**: Sie erwarten teilweise ausdrücklich den beobachteten Fehler, um seine Reproduzierbarkeit zu sichern.

Die Repositorytests benötigten einen erneuten Lauf mit geeigneten lokalen Test- und Temporärdateirechten. Ein erster Backend-Anlauf wurde wegen der fehlenden synthetischen Test-Passworteinstellung abgebrochen; der anschließend vollständig ausgeführte Lauf mit korrigierter Testkonfiguration ist oben ausgewiesen. Zwei ergänzende SMTP-Experimente wurden zunächst mit unzutreffenden Testannahmen vorbereitet und anschließend berichtigt: Die Adressen werden angenommen, der echte SMTP-Adapter verweigert aber den Versand. Es wurde dafür kein Produktcode geändert.

**CI am geprüften Commit:** Backend, Frontend, Repository checks, Technical qualification und Image waren erfolgreich. Im [Backendlauf](https://github.com/1uedev/task-manager/actions/runs/40933051079) war auch „Start, stop and restart the packaged application“ erfolgreich. Weitere Belege: [Frontend](https://github.com/1uedev/task-manager/actions/runs/40933051159), [Repository](https://github.com/1uedev/task-manager/actions/runs/40933051056), [Qualifikation](https://github.com/1uedev/task-manager/actions/runs/40933051087), [Image](https://github.com/1uedev/task-manager/actions/runs/40933051089). Das sind gelesene CI-Ergebnisse, keine in dieser Sitzung wiederholten Containerläufe.

**Grenzen:** Lokal kein Docker-/Compose-Lauf und kein `lifecycle-check.sh`; der vollständige Backendlauf enthält die Java-Lebenszyklus- und Migrationstests. Kein absichtlich getöteter JVM-Prozess, kein tatsächlicher Ausfall der Lease-Erneuerung und kein eigener Test eines zweiminütigen Watchdogs. E2 stellt den gespeicherten Zustand nach einem Prozessende her und benutzt die echte Lease-Freigabe. E3 steuert zwei überlappende Handler mit einem Testtransport. E9 verwendet eine kontrollierte Unterbrechung vor dem Anhängen und einen echten HTTP-Kundenwechsel. Die SMTP-Tests sprechen ausschließlich lokale Testserver an.

## Abgleich der acht Abnahmekriterien

| Nr. | Kriterium und Bewertung |
|---|---|
| **1** | **Rechte und Prüfschritt im regulären Fall erfüllt, Adressgrenze unzureichend.** Bestätigung verlangt `helpdesk.reply`, `ticket.read` und Lesen von `mail`/`contact`. Fehlendes `ticket.read` führt zu 404, fehlende Antwort-/Feldrechte zu 403; kein Kontakt zu 409. Postfach und ausgewählte Empfängeradresse werden geprüft. Der Browser zeigt Empfänger, Absender, Betreff und Text vor der Bestätigung. F3 zeigt jedoch, dass gespeicherte „Adressen“ keine gültigen einzelnen Versandadressen sein müssen. |
| **2** | **Für eine erzeugte Antwort beziehungsweise explizite Versandrunde erfüllt.** Antwort, Auftrag, Historie und Audit werden gemeinsam committet; E1 bestätigt den vollständigen Rollback bei einem späten Auditfehler. Der Schlüssel `reply:<id>:<n>` und die Sperre beim erneuten Senden verhindern einen zweiten Auftrag derselben Runde. Eine Wiederholung der erstmaligen HTTP-Bestätigung erhält hingegen eine neue Antwort-ID und einen neuen Auftrag: H2. Auftragseindeutigkeit verhindert außerdem nicht F1. |
| **3** | **Für vor dem Versuch abgeschlossene Änderungen im geprüften Umfang erfüllt.** Projektbestand, aktiver ursprünglicher Autor mit Mitgliedschaft, eingeschaltetes Postfach im Projekt mit gleicher Absenderadresse sowie unveränderter Kontakt/Empfänger werden erneut geprüft. E7 bestätigt Konto-Deaktivierung, Mitgliedschaftsentzug, Postfach-Deaktivierung und Adresswechsel; der reguläre Test bestätigt einen geänderten Empfänger. Die Grenze für Gruppenrechte und den Autor einer erneuten Bestätigung verlangt eine bewusste Entscheidung: H1. |
| **4** | **Nicht vollständig erfüllt.** Verbindungsfehler, 451, 550 und fehlende Antwort nach DATA sind im regulären Ablauf korrekt unterschieden. F1 kann jedoch eine tatsächlich angenommene neuere Sendung als `failed` erscheinen lassen. F2 lässt nach dem letzten Versuch `sending` ohne automatische Klärung stehen. F3 zeigt einen sicher vor der Übergabe scheiternden Adressfall, der als `uncertain` behandelt wird. |
| **5** | **Reguläre Bedienung erfüllt, unter F1/F2 nicht verlässlich.** `failed` und `uncertain` sind die zulässigen Ausgangszustände, bei `uncertain` ist `duplicateAccepted` Pflicht; If-Match schützt gegen veraltete Änderungen. Festhalten als angekommen sendet nichts. F1 umgeht die Risikobestätigung durch einen falschen `failed`-Zustand; F2 sperrt die vorgesehene Wiederherstellung. |
| **6** | **Im ruhenden Fall geschützt, unter Nebenläufigkeit nicht vollständig erfüllt.** Eine bekannte Message-ID allein genügt nicht: bestätigter Absender, freigegebener Kunde, Empfängerprüfung, gleiches Projekt und passender Ticketkunde sind erforderlich. Anderer Kunde führt zu `reply_customer_mismatch`, unbestätigter Absender zur Prüfwarteschlange. Die explizite Übernahme verlangt `helpdesk.review`, Version und Audit. F4 zeigt einen Kundenwechsel zwischen Prüfung und Anhängen. |
| **7** | **Im geprüften Umfang erfüllt.** Automatische Antworten und eine zurückkommende eigene Message-ID erzeugen weder neues Ticket noch automatische Antwort. `own_reply` ist eine Schleifen-/Kopieerkennung, kein Nachweis der Absenderidentität. Eine automatische Versandreaktion auf den Eingang existiert nicht. |
| **8** | **Teilweise erfüllt.** Die Übersicht filtert nach Mitgliedschaft und Antwort-/Leserechten; reguläre Fehlercodes enthalten keine Texte des SMTP-Servers. F2 zeigt einen irreführend dauerhaft offenen Zustand. Die Übersicht liefert nur die neuesten 100 Antworten ohne Fortsetzung und verschweigt Ladefehler: H3. |

## Befunde

### F1 · P2 · Ein alter Versandversuch kann das Ergebnis einer neueren Versandrunde überschreiben

**Stellen:** `backend/src/main/java/taskmanager/helpdesk/internal/ReplySender.java:93`, `:98`, `:104`, `:167`; `ReplyService.java:153`; `HelpdeskRepository.java:453`.

`prepare` prüft die Job-ID, `finish` dagegen nur die Antwort-ID und den Zustand `sending`. Sobald eine Person nach einem unklaren Versuch eine neue Versandrunde startet, kann ein noch laufender alter Versuch deshalb den Zustand der neuen Runde ändern.

**Nachstellung E3:**

1. Versuch A der ersten Runde steht im Transportaufruf.
2. Ein zweiter Handler derselben Runde findet `sending` und setzt `uncertain`. Das modelliert einen zweiten Ausführer nach abgelaufener Lease, während A noch nicht beendet ist.
3. Eine berechtigte Person bestätigt das Duplikatrisiko. Die API legt Runde 2 mit einer neuen Job-ID an; deren Versuch B beginnt.
4. A meldet verspätet eine endgültige Ablehnung. Sein `finish` setzt die inzwischen von B verwendete Antwort auf `failed`.
5. B meldet Annahme. Sein Abschluss wird ignoriert, weil der Zustand jetzt nicht mehr `sending` ist.
6. Erneutes Senden **ohne** `duplicateAccepted` ist mit HTTP 200 möglich. Der Testtransport zählt danach zwei Annahmen.

**Auswirkung:** Eine angenommene Mail wird als sicher nicht gesendet ausgewiesen. Eine weitere Person kann sie ohne Warnpflicht erneut senden. Der gleiche fehlende Abgleich betrifft auch verspätete Erfolgsmeldungen und wiederholbare Fehler.

E3 benutzt die echte API, echte Datenbanktransaktionen und den echten `ReplySender`; die Transportergebnisse werden mit Barrieren gesteuert. Zwei reale SMTP-Zustellungen wurden in diesem Experiment nicht erzeugt. Die Reihenfolge und der falsche Datenbankzustand sind reproduziert.

**Vorschlag:** Jeder Abschluss muss die vorbereitete Job-ID beziehungsweise Versandrunde und gegebenenfalls eine Versuchkennung prüfen. Ein älteres Ergebnis darf eine neuere Runde niemals verändern. Die Prüfung muss mit dem Zustandsupdate atomar sein. Beide Ergebnisreihenfolgen mit einer zwischenzeitlichen manuellen Wiederholung als Regression ergänzen.

### F2 · P2 · Nach einem Prozessende im letzten Versuch bleibt die Antwort dauerhaft im Versand

**Stellen:** `backend/src/main/java/taskmanager/helpdesk/internal/ReplySender.java:117`; `backend/src/main/java/taskmanager/jobs/internal/JobRepository.java:142`; `JobExecutor.java:365`; `backend/src/main/java/taskmanager/helpdesk/web/TicketCustomerController.java:64`.

Die Umwandlung von `sending` in `uncertain` geschieht ausschließlich beim nächsten Aufruf von `prepare`. Nach ausgeschöpften Versuchen gibt es diesen Aufruf nicht mehr: `releaseExpired` setzt den Auftrag unmittelbar auf `failed`.

**Nachstellung E2:** Eine bestätigte Antwort wird mit `sending` und ihr Auftrag mit `running`, fünf von fünf verbrauchten Versuchen und abgelaufener Lease gespeichert. Die echte Methode `JobRepository.releaseExpired` läuft. Ergebnis:

- Auftrag: `failed`, Fehler `job.lease_expired`;
- Antwort: weiterhin `sending`;
- Ticket-API: `pending`, Oberfläche damit weiterhin „wird gesendet“;
- `POST …/send` auch mit Risikobestätigung: **409 `helpdesk.reply_not_failed`**.

Ein erneuter normaler Worker-Start behebt das nicht, weil der Auftrag nicht erneut eingeplant ist. Eine Administration könnte den fehlgeschlagenen Job manuell wieder einplanen; das ist jedoch weder die zugesagte automatische Einordnung noch die Wiederherstellung im Antwortdialog.

**Auswirkung:** Eine möglicherweise angenommene Antwort bleibt ohne korrekten Klärungszustand stehen. Die Aussage „Prozessende/Zeitlimit/Stopp führt zu Versand unklar“ ist daher zu weit. E2 beweist den Prozessende-/Lease-Fall; Zeitlimit und fehlgeschlagener Abschluss können denselben Endzustand erreichen, wurden hier aber nicht durch einen realen Watchdog provoziert.

**Vorschlag:** Verwaiste Versandzustände unabhängig von einem weiteren fachlichen Sendeversuch erkennen. Auch endgültig beendete und gegebenenfalls entfernte Jobs müssen berücksichtigt werden. Vor einer Einstufung als `uncertain` zuverlässig ausschließen, dass noch ein aktueller Versuch läuft; F1 dabei mitlösen. Das Abbrechen eines noch wartenden Jobs ebenfalls mit dem fachlichen Antwortzustand abstimmen.

### F3 · P2 · Die Adressprüfung akzeptiert Kommentare mit Zeilenumbrüchen und Gruppen; der Versand endet irreführend als unklar

**Stellen:** `backend/src/main/java/taskmanager/helpdesk/internal/DirectoryService.java:190` bis `:201`; `MailboxService.java:173`; `ReplyService.java:120` und `:141`; `SmtpMailSender.java:76` und `:128`.

`normalizedAddress` validiert die von Jakarta Mail geparste Adresse, gibt aber die ursprüngliche Zeichenfolge zurück. Ein Adresskommentar oder eine Gruppe kann deshalb durchkommen. Der bisherige Helfer wird nun für ausgehende Mails benutzt; insbesondere baut der neue Code die Message-ID aus dem ungeprüft übernommenen Domainteil.

**Nachstellungen E4–E6:**

- `support@firma.example(\r\nX-Review-Injected:yes)` wird über die Postfach-API mit HTTP 200 angenommen. Die Antwortbestätigung liefert HTTP 201. Die gespeicherte Message-ID enthält den Zeilenumbruch.
- Der separate Test des echten MIME-Aufbaus erzeugt daraus eine zusätzliche Kopfzeile; nach erneutem Parsen existiert `X-Injected` mit dem Wert `yes)>`.
- Eine Kontaktadresse der Form `team:lea@nordwerk.example,outsider@foreign.example;` wird ebenfalls angenommen, obwohl ein einzelner Empfänger vorgesehen ist.
- **Im vollständigen Versandweg scheitern beide getesteten Formen mit einer lokalen `AddressException`.** Der Antwortzustand wird `uncertain`, obwohl keine Nachricht übergeben wurde. Der SMTP-Testserver erhielt im ersten Fall keinen Mailinhalt; für die Gruppe wurde keine Mail an die beiden Testempfänger empfangen.

Damit sind ungültige Bestätigungen, eine manipulierbare MIME-Kopfzeile und die falsche Unsicherheitsklassifikation nachgewiesen. **Nicht nachgewiesen sind eine zugestellte manipulierte Mail, zusätzliche SMTP-Empfänger oder eine erfolgreiche SMTP-Befehlsinjektion.** Die Konfiguration dieser Werte verlangt Verwaltungsrechte; eine Ausnutzung durch einen beliebigen externen Absender wurde nicht gezeigt. Deshalb P2, kein behaupteter P1-Datenabfluss.

Betreff, Kontakt- und Postfachname werden dagegen bereinigt und auf eingebettete Zeilenumbrüche geprüft. Die einfache Adresse mit unmittelbar angehängtem `Bcc:` wurde zurückgewiesen; der relevante Weg läuft über den akzeptierten Kommentar.

**Vorschlag:** Für Versandadressen explizit genau eine einfache Mailbox zulassen, Gruppen und Steuerzeichen ablehnen und den kanonisch geparsten Adressteil verwenden. Die Message-ID aus einer unabhängig geprüften Domain erzeugen und vor der Serialisierung prüfen. Einen nachweislich lokalen Fehler vor dem Transport nicht als möglicherweise angenommen klassifizieren.

### F4 · P2 · Die Kundengrenze wird vor dem Anhängen geprüft und kann inzwischen veraltet sein

**Stellen:** `backend/src/main/java/taskmanager/helpdesk/internal/MailIntake.java:239`, `:259`, `:315`; `TicketOpener.java:83`; `TicketCustomerService.java:155`.

`sameCustomer` liest den aktuellen Ticketkunden vor dem Anhängen. Zwischen dieser Entscheidung und `opener.append` kann die Kundenzuordnung über die API geändert werden. Die Zuordnung wird beim Anhängen nicht erneut abgesichert.

**Nachstellung E9:**

1. Ticket und ausgehende Antwort gehören zu Kunde A.
2. Eine bestätigte Mail eines Kontakts von A verweist auf die Message-ID dieser Antwort. Die Eingangsklassifikation und der Kundenvergleich sind erfolgreich.
3. Direkt vor `lockForHelpdeskChange` in `append` wechselt eine berechtigte Person über die echte HTTP-API Ticketkunde und Kontakt auf Kunde B. Dieser Wechsel wird vollständig committet.
4. Der Eingang setzt seine Verarbeitung fort. Ergebnis: Mailzustand `ticket`, Mailkunde A, aktueller Ticketkunde B. Keine Prüfwarteschlange und kein `reply_customer_mismatch`.

Der Test unterbricht an einer kontrollierten Methodengrenze; Kundenzuordnung und Anhängen verwenden die echte API beziehungsweise echte Datenbankoperationen. Es werden keine Prüfergebnisse oder Rückgabewerte der Kundenprüfung gefälscht.

**Auswirkung:** Neue Inhalte des bisherigen Kunden werden ohne Entscheidung eines Reviewers einem inzwischen anderen Kunden zugeordneten Ticket hinzugefügt. Der Test zeigt keine automatische Einsicht eines Kunden oder Portalnutzers, aber eine Verletzung der zugesagten Zuordnungsgrenze.

**Vorschlag:** Kundenvergleich und Anhängen unter einer mit dem Kundenwechsel gemeinsamen Sperre durchführen und den aktuellen Stand dort erneut prüfen. Eine bloße zusätzliche ungesperrte Abfrage reicht nicht. Auch die Änderung eines Kontakts zu einer anderen Kundenorganisation berücksichtigen. Bei Abweichung die Mail in der Prüfwarteschlange mit Ticketverweis belassen. Die beiden Commit-Reihenfolgen als Regressionen testen.

## Hinweise und Bewertung der Kriterien

### H1 · Gruppenrechte bei Bestätigung: vertretbare Produktentscheidung, keine technische Notwendigkeit

Der dokumentierte Ansatz ist als bewusste Regel vertretbar: Eine bereits berechtigt bestätigte Sendung bleibt beauftragt, auch wenn später eine Gruppenregel entzogen wird. Die erneuten Prüfungen von Kontoaktivität und Projektmitgliedschaft begrenzen das Risiko. Die Begründung „ohne Sitzung“ macht einen erneuten Rechteentscheid jedoch nicht grundsätzlich unmöglich; dafür wäre ein ausdrücklich definierter Kontext des Hintergrundauftrags erforderlich.

Vor Abnahme sollte feststehen, ob ein Rechteentzug bereits bestätigte, wartende oder automatisch wiederholte Sendungen stoppen soll. Wenn die Regel von DEC-091 übernommen wird, sollten Oberfläche und Betriebsdokumentation den Unterschied zwischen Gruppenentzug und Deaktivierung/Mitgliedschaftsentzug erklären. Außerdem prüft der Auftrag auch nach `sendAgain` weiterhin `createdBy`, also den ursprünglichen Autor. Eine neue Bestätigung durch eine andere berechtigte Person ersetzt diesen nicht. Ob das gewollt ist, gehört in das Kriterium.

`POST /helpdesk/inbox/{id}/attach` ist laut Vertrag eine ausdrücklich durch `helpdesk.review` erlaubte Übernahme. Es verlangt nicht zusätzlich `ticket.edit` oder die Feldrechte des normalen Ticketdialogs. Das ist eine delegierte Eingangsentscheidung und sollte als solche bewusst bestätigt werden; ein nicht berechtigter externer Absender kann diese API nicht benutzen.

### H2 · Der Auftragsschlüssel sichert keine wiederholte HTTP-Bestätigung ab

E8 sendet denselben Bestätigungs-POST zweimal. Beide Aufrufe liefern 201 mit unterschiedlichen Antwort-IDs; dadurch entstehen zwei unterschiedliche Schlüssel `reply:<id>:1`. Der Server kann einen nach verloren gegangener HTTP-Antwort wiederholten Klick nicht von einer bewusst zweiten Nachricht unterscheiden. Der Browser sperrt die Schaltfläche während der laufenden Anfrage, besitzt aber keinen stabilen Bestätigungsschlüssel für eine Wiederholung.

Das ist **kein Gegenbeweis** zur engeren Zusage „ein Job pro gespeicherter Antwort und Versandrunde“. Für „genau einmal je menschlicher Bestätigung“ reicht diese Zusage aber nicht. Das Kriterium sollte einen vom Client stabil gehaltenen Bestätigungsschlüssel oder eine bewusst akzeptierte und deutlich angezeigte Unsicherheit bei verloren gegangener Bestätigungsantwort vorsehen.

### H3 · Die Übersicht kann offene Antworten ausblenden

`HelpdeskRepository.openReplies` liefert nur die neuesten 100 Einträge. Es gibt weder Cursor noch Gesamtzahl oder Hinweis auf weitere Antworten. Ältere Fehlfälle bleiben am jeweiligen Ticket sichtbar, sind aber nicht vollständig über diese Übersicht auffindbar. Außerdem gibt `OpenReplies` bei einem Ladefehler genauso `null` zurück wie bei einer leeren Liste (`frontend/src/pages/InboxPage.tsx:276`).

Die Grenze 100 ist dokumentiert, daher wird sie nicht als verdeckte Vertragsabweichung behandelt. Für eine zuverlässige Arbeitsübersicht ist Kriterium 8 dennoch zu schwach: vollständige erreichbare Liste, erkennbare Kürzung und sichtbarer Ladefehler sollten verlangt werden.

### H4 · Antwortenreferenzen und Vertragsprüfung benötigen präzisere Grenzen

Die reguläre Referenzzuordnung funktioniert. Bei mehreren passenden Message-IDs wählt `repliedTicket` aber die zeitlich neueste gespeicherte Antwort, nicht zwingend das ausdrückliche `In-Reply-To`. Verweisen die Header auf unterschiedliche Tickets desselben Kunden, fehlt eine festgelegte Konfliktregel. Dafür sollte entweder eine klare Priorität oder eine manuelle Prüfung gelten.

Der erfolgreiche `HelpdeskContractTest` vergleicht Routen und Methoden; er validiert keine vollständigen Antwortkörper, Fehlercodes oder Feldlängen. Beispielsweise kann `Requests.retrying` auch für `attach` und die Übersicht 503 erzeugen, während deren neue Vertragsabschnitte diesen Status nicht nennen. Die Prüfung von Routen ist nützlich, aber kein vollständiger Nachweis der Vertragskonformität.

### H5 · Fehlende Regressionen und erforderliche Schärfung

Die sechs neuen API-Tests rufen den Antwort-Handler direkt auf. Der Abbruchtest setzt `sending` per SQL und ruft danach ausdrücklich einen weiteren Versuch auf. Dadurch fehlen gerade der letzte verbrauchte Versuch und das Zusammenspiel mit der echten Lease-Freigabe. Die vier Browserprüfungen benutzen ein modelliertes Backend; sie belegen die Bedienung, keine SMTP- oder Datenbankgarantien.

Vor Abnahme sollten die Kriterien mindestens Folgendes ausdrücklich verlangen:

1. Atomarer Auftrag je Versandrunde **und** festgelegtes Verhalten bei wiederholter Bestätigungsanfrage.
2. Keine Zustandsänderung einer neueren Runde durch ältere Versuche; keine automatische Wiederholung nach möglicherweise erfolgter Annahme.
3. Klärung verwaister Antworten auch nach dem letzten Versuch, nach abgebrochenen Jobs und bei fehlgeschlagenem Ergebnis-Commit.
4. Gültige einzelne Absender-/Empfängeradressen, sichere Header und Message-IDs; Tests für Kommentare, Gruppen, CR/LF und übliche internationale Anzeigenamen.
5. Eine mit gleichzeitigen Änderungen abgestimmte Prüfung des Ticketkunden beim Anhängen; ausdrücklich definierte Rechte für manuelle Übernahme und spätere Gruppenänderungen.
6. Sichtbare Fehler und vollständig erreichbare offene Antworten; definierter Umgang mit widersprüchlichen Antwortreferenzen.
7. Ergänzende Transportfälle: Ablehnung vor DATA und am DATA-Ende, verlorene Schlussantwort, Stopp/Zeitlimit in unterschiedlichen Phasen, letzter Versuch sowie verspätete Ergebnisse. Die bestehenden 451-/550-/Verbindungs-/DATA-Abbruchtests bleiben erhalten.

## Technische Einordnung

**SMTP:** Für die geprüften Standardfälle wurde keine falsche Einstufung einer tatsächlich angenommenen Mail durch die 4xx-/5xx-Auswertung selbst gefunden. Fehlende Schlussantwort führt zu `uncertain`; eine Ausnahme beim Schließen verändert das Ergebnis nicht. Der nachgewiesene Weg „angenommen, dennoch failed“ liegt in F1 bei der Zuordnung des Ergebnisses zur Versandrunde. Ein tatsächlicher Prozessabbruch kann nicht unmittelbar seinen Zustand in der Datenbank ändern; die Garantie muss deshalb die spätere Wiederherstellung beschreiben und F2 abdecken.

**Eingang:** Eine erratene oder kopierte Message-ID genügt im ruhenden Zustand nicht, um als unbekannter beziehungsweise fremder Kunde Inhalte automatisch anzuhängen. Der eigene Empfangsserver bleibt die Vertrauensbasis der Absenderprüfung, wie bereits im abgenommenen Eingang dokumentiert. Eine bekannte Message-ID als eigene `Message-ID` führt zu `own_reply`; das verhindert einen Ticketzugang, beweist aber nicht, wer die Kopie gesendet hat. F4 betrifft die zeitliche Gültigkeit des zusätzlich notwendigen Kundenvergleichs.

**Migration V0028:** Neue Tabelle mit Zustands-, Versions- und Längenbedingungen, eindeutiger Message-ID, Ticket-/Offen-Indexes und nullable `related_ticket_id` im Eingang. Die vorhandenen Eingänge benötigen keinen Pflichtwert für die neue Spalte. Die 27 Migrationen bis V0028, Tabellenliste und Anwendungslebenszyklus bestehen im vollständigen Backendlauf; die korrigierte Shell-Lebenszyklusprüfung ist in CI erfolgreich. Eine gesonderte Datenübernahmeprobe mit einem befüllten V0027-Bestand wurde nicht ausgeführt. Die Migration synchronisiert fachliche Antwortzustände nicht mit endgültigen Jobzuständen; F2 ist durch die Tabellenbedingungen nicht verhindert.

**Oberfläche:** Der Prüfschritt enthält die wesentlichen Angaben. Erneutes Senden einer unklaren Antwort bleibt bis zum Setzen der Duplikatbestätigung gesperrt; „als angekommen festhalten“ löst keinen Versand aus. Das wird durch die Browserprüfungen gestützt. Diese Schutzmechanismen setzen jedoch einen korrekten Serverzustand voraus; F1 und F2 sind deshalb auch für die Bedienung relevant.

## Zusatznachstellungen im Einzelnen

| Kennung | Methode in der getrennten Prüfkopie | Beobachtung |
|---|---|---|
| E1 | `reviewConfirmationRollsBackAsOneTransaction` | Ein Trigger verweigert den abschließenden Bestätigungsaudit: keine Antwort, kein zusätzlicher Job, kein zusätzlicher Historieneintrag |
| E2 | `reviewLastLeaseExpiryLeavesSending` | Echter Lease-Ablauf nach Versuch 5: Job failed, Antwort sending, API pending, erneutes Senden 409 |
| E3 | `reviewOldAttemptOverwritesAcceptedResend` | Verspätete Ablehnung aus Runde 1 überschreibt Runde 2; Annahme aus Runde 2 bleibt failed; erneutes Senden ohne Risikoflag möglich |
| E4 | `ReplyReviewProbeTest.reviewAddressHeaders` | CR/LF im Adresskommentar und Gruppen akzeptiert; zusätzliche Kopfzeile beim MIME-Aufbau; einfache angehängte Bcc-Zeile abgelehnt |
| E5 | `reviewMailboxAddressIsAcceptedButCannotBeSent` | Echte Postfach- und Antwort-API akzeptieren CR/LF-Kommentar; SMTP-Adapter bricht vor Übergabe ab, Zustand uncertain |
| E6 | `reviewContactGroupIsAcceptedButCannotBeSent` | Echte Kontakt- und Antwort-API akzeptieren Gruppe; SMTP-Adapter bricht ab, Zustand uncertain, keine Zustellung |
| E7 | `reviewWorkerRechecksAuthorMembershipAndMailbox` | Vier vor Versand abgeschlossene Änderungen verhindern den Versand mit passendem Code |
| E8 | `reviewReplayConfirmationCreatesTwoReplies` | Zwei identische Bestätigungs-POSTs erzeugen zwei Antworten und Aufträge |
| E9 | `reviewCustomerChangeBetweenClassificationAndAppend` | Nach erfolgreichem Kundenvergleich committet ein echter HTTP-Kundenwechsel; der Eingang hängt die Mail trotzdem automatisch an |

## Empfehlung

**F1 bis F4 vor der Abnahme korrigieren und gezielt nachprüfen.** Priorität haben der Schutz neuer Versandrunden vor alten Ergebnissen und die atomare Kundengrenze; anschließend die vollständige Wiederherstellung und die Versandadressprüfung. Die in H1 bis H4 genannten Verhaltensgrenzen ausdrücklich entscheiden beziehungsweise präzisieren und die Kriterien um die Regressionen aus H5 ergänzen.

Die erfolgreichen regulären Tests und CI-Läufe sind eine tragfähige Grundlage, schließen die nachgestellten Randfälle jedoch nicht aus. TASK-058 bleibt fachlich bei Holger zur Entscheidung; dieser Bericht nimmt keine Abnahme vor.
