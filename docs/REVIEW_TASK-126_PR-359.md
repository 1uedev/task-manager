# Unabhängige technische Prüfung: TASK-126, PR #359

Prüfer: Codex. Prüfung vom 25./26. November 2026, Sitzung `codex-20261125-task126-review`.

Geprüft wurde [PR #359](https://github.com/1uedev/task-manager/pull/359) im Repository `1uedev/task-manager`, Ergebnisbranch `claude/project-thread-av1na6`, Head `aa5b3eed39aa10953b099e3e7533b080edf9ede1` gegen Basis `59bd4cfa3e93a1a0910c615ed48f9d57441483c0`. Alle Zeilenangaben beziehen sich auf diesen Head. Der PR ist bereits gemergt; das ersetzt nach AGENTS.md keine fachliche Abnahme. Die Prüfung erfolgte in einem getrennten Worktree, ohne Änderungen am Produktcode und ohne Aufrufe gegen echte KI-Anbieter.

Maßstab: AGENTS.md, docs/DEVELOPMENT_REVIEW.md, TASK-126 einschließlich Regeln 1 und 2, Abnahmekriterien und Übergabe in TODO.md, DEC-106/DEC-157, die beiden KI-Abschnitte in backend/README.md sowie AI-UC-01/02 und A01–A03, A08–A11, A15 in docs/AI_USE_CASES.md. Geprüft wurden das neue Modul und seine Ports, die Änderungen der KI-Grundlage, Verträge, Oberfläche, Übersetzungen und Testersatz. Keine Migration gehört zum Ergebnis.

## Befunde – vor der Abnahme zu beheben

### F1 – Der Fingerabdruck bindet einen Entwurf nicht an Kunde und Kontakt

**Datei/Zeile:** `backend/src/main/java/taskmanager/assist/internal/TicketAssist.java`, Zeilen 88–100 und 212–224; ergänzend `backend/src/main/java/taskmanager/helpdesk/TicketConversation.java`, Zeile 32, und `backend/src/main/java/taskmanager/helpdesk/internal/ConversationReader.java`, Zeilen 79–87.

**Szenario:** Einen Mailentwurf für einen englischsprachigen Kontakt erzeugen. Anschließend am Ticket zu einem anderen englischsprachigen Kontakt desselben Kunden wechseln, ohne den Verlauf zu ändern. Beim erneuten Lesen des fertigen Laufs liefert der echte Server weiterhin den Inhalt mit `invalid: null`. Eine zweite Gegenprobe erzeugt einen Portalentwurf ausschließlich aus dem Titel und wechselt danach den Kunden samt Kontakt bei gleicher Sprache. Auch dieser Lauf bleibt gültig.

**Ursache und Auswirkung:** Der Hash enthält Zweck, Sprache und die gerenderten Quellen mit ihren laufenden Bezeichnungen `S1`, `S2` usw. Kunde, Kontakt, tatsächliche Eintragskennungen und die vorhandene Ticketversion gehen nicht ein. `CustomerView.customerId` wird beim Aufbau verworfen; eine Kontaktkennung liefert der Port gar nicht. Bei gleichem Quelltext bleibt deshalb ein für den früheren Empfänger erzeugter Entwurf verwendbar. Ein Kundenwechsel wird nicht, wie in backend/README.md, Zeile 739 zugesagt, zuverlässig erkannt. Dies verletzt die geforderte Aktualitätsprüfung aus A09. Die Gegenprobe mit Titel allein belegt eine falsche Gültigkeitsentscheidung, keinen nachgewiesenen Abfluss von Inhalten eines früheren Kunden.

**Nachweis:** Die zusätzlichen API-Gegenproben `reviewContactChangeMustInvalidateDraft` und `reviewCustomerChangeMustInvalidateTitleOnlyDraft` scheitern am erwarteten Entzug des Inhalts. Beide laufen durch die echten HTTP-, Rechte-, Fachmodul- und PostgreSQL-Pfade mit einem lokalen Modell-Testserver.

**Vorschlag:** Den Prüfnachweis um stabile Kunden- und Kontaktkennung sowie die relevanten Empfängerdaten beziehungsweise deren Version ergänzen. Echte Quellenkennungen gehören ebenfalls in den Vergleich. Der Port muss die dazu nötigen, intern verwendeten Bezüge liefern; sie müssen nicht als zusätzliche Inhalte an das Modell gehen. Kontakt- und Kundenwechsel müssen auch bei identischer Sprache und identischem Quelltext entwerten. Dauerhafte API-Tests für beide Kanäle ergänzen. Ein pauschaler Vergleich aller Ticketänderungen sollte keine unnötige Entwertung durch sachfremde, nicht verwendete Felder erzeugen.

### F2 – Während der Generierung geschriebener eigener Text wird überschrieben

**Datei/Zeile:** `frontend/src/pages/TicketAi.tsx`, Zeilen 260–274, insbesondere 269–270.

**Szenario:** Im zunächst leeren Mail- oder Portalantwortfeld „Entwurf mit KI“ starten. Während der Lauf arbeitet, eigenen Text schreiben. Sobald das Ergebnis eintrifft, ersetzt die Oberfläche diesen Text unmittelbar durch den KI-Entwurf. „Text ersetzen“ wird nicht angeboten.

**Ursache und Auswirkung:** Die asynchrone Funktion prüft nach `await start(...)` die beim Start eingefangene Eigenschaft `body`. Diese bleibt für den laufenden Funktionsaufruf leer, auch wenn das aktuelle Textfeld inzwischen Inhalt hat. Eigene Arbeit geht verloren. Die ausdrückliche Regel „Einsetzen nur in ein leeres Feld, sonst Text ersetzen“ wird gerade bei langsamen Antworten verletzt.

**Nachweis:** Zwei zusätzliche Browsertests gegen den unveränderten Produktionsbuild halten den Lauf an, schreiben `My text written while waiting` in das Feld und geben danach das Ergebnis frei. In beiden Kanälen steht anschließend stattdessen der KI-Text im Feld. Die Assertions auf Erhalt des eigenen Texts scheitern.

**Vorschlag:** Beim tatsächlichen Einsetzen den aktuellen Feldinhalt prüfen, zum Beispiel durch eine atomare Zustandsentscheidung im besitzenden Formular oder eine aktuell gehaltene Referenz. Sobald dort eigener Text steht, den Entwurf nur als ausstehend anbieten und eine ausdrückliche Ersetzung verlangen. Beide verzögerten Abläufe dauerhaft testen.

### F3 – Eine fertige Zusammenfassung bleibt nach Änderung ihrer Quelle aktuell dargestellt

**Datei/Zeile:** `frontend/src/pages/TicketAi.tsx`, Zeilen 74–76, 181–198; `frontend/src/pages/TicketPage.tsx`, Zeile 318.

**Szenario:** Eine Zusammenfassung erzeugen, anschließend auf derselben Ticketseite den Titel ändern und erfolgreich speichern. Die alte Zusammenfassung bleibt einschließlich „Kopieren“ sichtbar. Die Oberfläche liest den Lauf nicht erneut und zeigt keinen Hinweis auf die inzwischen geänderte Quelle.

**Ursache und Auswirkung:** Das Abfragen endet nach dem ersten abgeschlossenen Ergebnis. Der fertige Inhalt verbleibt im Komponentenstatus. Der Schlüssel der Komponente hängt nur von `refusals` ab, nicht von einer Änderung ihrer Quellen. Auch „Kopieren“ verwendet ausschließlich den gespeicherten Inhalt. Der serverseitige Prüfer würde eine geänderte Quelle beim nächsten Lesen erkennen, wird hier aber nicht mehr erreicht. So kann die Person einen überholten Sachstand als gültige Zusammenfassung übernehmen.

**Nachweis:** Der zusätzliche Browsertest `review: summary must be rechecked after editing its source` erzeugt die Zusammenfassung, speichert einen geänderten Titel, wartet auf „Gespeichert.“ und prüft Anzeige und Laufabrufe. Der alte Inhalt bleibt sichtbar, die Anzahl der Laufabrufe unverändert. Die Prüfung scheitert. Dafür ist keine Fingerabdrucksimulation im Testersatz erforderlich: Schon das Ausbleiben einer erneuten Anfrage beziehungsweise lokalen Entwertung ist beobachtbar.

**Vorschlag:** Fertige Ergebnisse nach bekannten Änderungen ihrer Quellen entwerten oder erneut vom Server prüfen lassen. Änderungen von Ticketfeldern, Verlauf und Kundenbezug müssen die betreffenden Komponenten erreichen. Vor einer späteren Übernahme, insbesondere beim Kopieren und beim Einsetzen eines zurückgehaltenen Entwurfs, erneut die Gültigkeit prüfen. Einen Browsertest für „Erzeugen – Quelle ändern – Übernehmen“ ergänzen; bei ungültigem Ergebnis darf keine unverändert gültige Vorschau stehen bleiben.

### F4 – Portalkonten erhalten 404 statt der geforderten und dokumentierten 403

**Datei/Zeile:** `backend/src/main/java/taskmanager/assist/web/AssistRequests.java`, Zeilen 33–38; `backend/src/main/java/taskmanager/helpdesk/internal/ConversationReader.java`, Zeilen 61–64. Widersprüchliche Beschreibung in `backend/README.md`, Zeile 737, gegenüber `contracts/assist.openapi.yaml`, Zeile 13; zu weicher Test in `backend/src/test/java/taskmanager/assist/AssistApiTest.java`, Zeilen 688–690.

**Szenario:** Ein vollständig angemeldetes Portalkonto ruft die Zusammenfassung auf. Erwartet ist entsprechend Prüfauftrag und README eine 403-Antwort. Der echte Server antwortet `404 project.not_found`. Der Entwurfspfad verweigert externe Konten laut Quellcode ebenfalls mit 404.

**Ursache und Auswirkung:** `AssistRequests.actor()` lehnt externe Konten nicht vor dem fachlichen Objektzugriff ab. Der Aufruf fällt deshalb in die 404-Regel des Fachmoduls. Der Vertrag beschreibt 404, während README und Testersatz 403 vorsehen. Der vorhandene API-Test lässt beide Antworten gelten und kann diese Abweichung nicht erkennen. Es wurde dadurch kein unerlaubter Zugriff ermöglicht; der Befund betrifft den ausdrücklich verlangten und widerspruchsfrei zu liefernden API-Vertrag.

**Nachweis:** Die zusätzliche Gegenprobe mit exakt erwarteter 403 scheitert bereits beim Summary-Aufruf mit tatsächlicher 404 und `project.not_found`. Ein Anbieteraufruf findet nicht statt.

**Vorschlag:** Externe Konten an der gemeinsamen Assist-Anfragegrenze mit 403 zurückweisen. Vertrag, README, Testersatz und exakte Assertions für alle drei Assist-Routen angleichen. Die bisherige 404-Abschirmung fremder oder unbekannter Tickets für interne Konten beibehalten. Falls stattdessen bewusst 404 gelten soll, braucht das eine ausdrückliche Korrektur der Vorgabe und sämtlicher widersprechender Beschreibungen; der jetzige Mischzustand ist nicht abnahmefähig.

## Hinweise – können später bearbeitet werden

### H1 – Die Doppelklicksperre erfüllt nur die ausdrücklich genannte Oberflächengrenze

**Datei/Zeile:** `frontend/src/pages/TicketHelpdesk.tsx`, Zeilen 142–155 und 397–407; `backend/src/main/java/taskmanager/helpdesk/internal/ReplyService.java`, Zeilen 141–157.

**Szenario:** Zwei schnelle Bestätigungen innerhalb desselben geöffneten Formulars werden durch die synchrone Referenz gesperrt. Geht dagegen die erfolgreiche Serverantwort verloren, kann eine erneute Bestätigung einen zweiten Versand erzeugen. Dasselbe gilt für zwei Formulare beziehungsweise direkte API-Aufrufe. `ReplyService.create()` erzeugt pro Bestätigung eine neue Antwortkennung und einen neuen Auftrag.

**Bewertung:** Die schmale Zusage „zweiter Druck während des ersten Versands sendet nichts“ ist umgesetzt und durch Browsertests belegt. Die weitergehende Zusage aus A11, dass wiederholte Bestätigung oder Wiederholung nach Timeout höchstens eine Aktion erzeugt, ist damit nicht erfüllt. Dieser Unterschied ist eine bewusst benannte Umfangsgrenze, kein versteckter Anspruch auf serverseitige Idempotenz. Er wird hier nicht zusätzlich als zwingende Ausweitung von TASK-126 behandelt. Eine fachliche Abnahme sollte A11 deshalb nur in dieser eingeschränkten Bedeutung bewerten.

**Vorschlag:** Die Einschränkung in der Abnahme ausdrücklich beibehalten. Für die umfassende A11-Garantie später eine dauerhafte Bestätigungskennung einführen, die serverseitig denselben Versand zurückliefert, statt einen neuen anzulegen. Timeout, parallele Bestätigung und zwei Tabs als Gegenproben aufnehmen.

### H2 – Der Testersatz bildet mehrere relevante Serverregeln nicht nach

**Datei/Zeile:** `frontend/tests/browser/backend.mjs`, Zeilen 3687–3694 und 3918–3944.

**Szenario und Abweichungen:** Der Summary-Ersatz verlangt ausdrücklich `de` oder `en`, während der Server eine fehlende Sprache auf Deutsch setzt. Seine Feldsammlung verwendet die allgemeine Liste lesbarer Felder, enthält dadurch unter anderem `assignee` und bildet lesbare eigene Felder nicht wie der Server ab. Eine nicht ausdrücklich gesetzte Priorität fehlt im Ersatz, obwohl der echte Prioritätsport den Standardwert liefern kann. Für den Mailentwurf genügt im Ersatz ein Kontaktobjekt; der echte Antwortweg verlangt einen Kontakt mit Adresse. Die Laufantwort benutzt einen vorbereiteten Ungültigkeitszustand statt des Neuaufbaus von Quellen und Rechten. Die Portalstatus-Abweichung ist zusätzlich F4.

**Auswirkung:** Grüne Browsertests sind für Bedienabläufe nützlich, belegen jedoch diese Kontext- und Aktualitätsregeln nicht. Insbesondere darf eine vorher gesetzte Markierung „ungültig“ nicht als Nachweis einer tatsächlich erkannten Quellenänderung gewertet werden.

**Vorschlag:** Die neuen Assist-Pfade anhand gemeinsamer Antwortbeispiele mit dem Server abgleichen. Mindestens fehlende Sprache, eigene Felder, Standardpriorität, Kontakt ohne Adresse und Quellenänderung abdecken. Sicherheitsgrenzen weiter mit echten API-Tests prüfen; die Oberfläche sollte unabhängig davon die notwendigen erneuten Prüfungen auslösen, siehe F3.

### H3 – Die Quellengrenze kann Inhalte ohne Kürzungshinweis entfernen

**Datei/Zeile:** `backend/src/main/java/taskmanager/assist/internal/TicketAssist.java`, Zeilen 231–241.

**Szenario:** Ein Ticket hat mehr Verlaufseinträge, als in die Grenze von 500 Quellen passen. `addEntries()` entfernt bereits vor der Übergabe an die KI-Grundlage die ältesten Einträge. Sind die verbleibenden Einträge kurz genug, muss deren spätere Verarbeitung keine weitere Kürzung vornehmen. Der Lauf kann deshalb `truncated: false` melden, obwohl der fachliche Verlauf bereits verkürzt wurde.

**Auswirkung:** Das Weglassen älterer Einträge ist in der README beschrieben; die Person sieht in diesem Fall aber nicht zuverlässig, dass die Zusammenfassung nur einen Ausschnitt berücksichtigt. Dieser Hinweis beruht auf dem Quellcodeabgleich, nicht auf einem zusätzlich ausgeführten 500-Einträge-Test.

**Vorschlag:** Eine Kürzung schon beim Aufbau des fachlichen Kontexts kennzeichnen und bis zum Lauf beziehungsweise zur Oberfläche weiterreichen. Einen Grenztest mit vielen kurzen Einträgen ergänzen, der allein diese erste Kürzungsstufe auslöst.

## Abgleich mit dem Prüfauftrag

| Bereich | Ergebnis und Grenze des Nachweises |
|---|---|
| Regel 1, A01 | `TicketReaderAdapter` filtert feste und eigene Felder nach ihren Leseregeln; `TicketPrioritiesAdapter` verwendet die Prioritätsleseregel. `ConversationReader.readable()` liefert nur bei lesbarem `mail` die Mails, gesendeten Antworten und Portalbeiträge. Nicht gesendete Antworten werden ausgeschlossen. Die Assistenz schreibt weder Zusammenfassung noch Entwurf am Ticket. Lesbare eigene Felder wurden im Code geprüft; die vorhandenen Assist-Tests decken nicht jede eigene Feldart mit einer eigenen Negativprobe ab. Vollständigkeit bei großen Verläufen siehe H3. |
| Regel 2, A02 | Entwürfe erhalten den lesbaren Titel und den Verlauf des aktuellen Kunden. Beschreibung, Typ, Status, Priorität, eigene Felder und interne Anhänge werden nicht extrahiert. Mails und gesendete Antworten werden nach Kundenkennung gefiltert, Portalbeiträge über `postsFor(ticket, customer)`. Ein interner Inhalt gelangt über diese Datenwege nicht allein deshalb in den Entwurf, weil die Person ihn intern lesen darf. Ein vom Kunden selbst zitierter interner Text kann naturgemäß bereits Bestandteil einer Kundenmail sein. Empfängerbindung nach Änderungen siehe F1. |
| Sprache, A03 | Beide Kanäle verwenden die Kontaktsprache, Englisch bei `en`, sonst Deutsch; ohne Kontakt ist die Portalvorgabe Deutsch. Sprache der Zusammenfassung und Antwortsprache sind getrennt. Tatsächliche Sprachqualität eines echten Modells wurde auftragsgemäß nicht getestet. |
| A08 | Kundentext steht als unzuverlässige Quelle unter festen Anweisungen; es gibt keinen Werkzeugpfad, der daraus selbstständig Aktionen ausführt. Die Tests verwenden eine Kundenmail mit einer fremden Anweisung. Ein Modell-Testserver mit vorgefertigter Antwort beweist aber keine allgemeine semantische Widerstandsfähigkeit eines echten Modells gegen solche Anweisungen. Diese Qualität darf aus den grünen Tests nicht abgeleitet werden. |
| A15 | Ausgaben mit unbekannten Quellenkennungen werden von der KI-Grundlage abgelehnt. Der bestehende Negativtest ist grün. Die Oberfläche rendert Antworttexte als Text, nicht als ausführbares HTML. |
| Prüfer, A09 | Die Auswahl in `RunService.read()` berücksichtigt Anwendungsfall und Objektart; ein fremder Prüfer ersetzt keinen passenden. Damit werden spätere Fälle aus TASK-127 nicht pauschal an Ticketprüfer gebunden. Fehlt der passende Nachweis, wird Inhalt nicht ungeprüft ausgeliefert. Quellenänderungen werden grundsätzlich neu aufgebaut; Ausnahmen und Lücken siehe F1/F3. |
| Transaktionen | Der Neuaufbau läuft in einer eigenen Transaktion. Die zusätzliche API-Gegenprobe `reviewLossOfTicketReadMustNotPoisonOuterTransaction` entzieht das Ticketleserecht bei weiter bestehendem `ai.use`. Das Lesen liefert regulär HTTP 200 mit entzogenem Inhalt und `ai.source_changed`; die äußere Lesetransaktion wird nicht durch den Fehler des Neuaufbaus zerstört. Diese Probe ist grün. |
| Rechte | `ai.use`, Ticketleserecht und für Entwürfe die normalen Antwortrechte werden serverseitig geprüft. Fremde beziehungsweise unbekannte Tickets bleiben abgeschirmt. Portalabweichung siehe F4. |
| `portal.answer` | Die Präzisierung aus DEC-106/DEC-157 ist sachlich sauber: `portal.answer` betrifft das antwortende Portalkonto; die interne Supportantwort und ihr KI-Entwurf verwenden `helpdesk.reply` sowie die Leseregeln von `mail` und `contact`. Die Assistenz verlangt nicht irrtümlich zusätzlich das Portalnutzerrecht. |
| A10 und Versand | Generierung allein erzeugt keinen Versand. Mailentwürfe gehen durch die vorhandene Prüfung und ausdrückliche Bestätigung; geänderter Text benötigt in der Oberfläche eine neue Prüfung. Portalantworten folgen dem vorhandenen ausdrücklichen „Im Portal veröffentlichen“, ohne zusätzliche Mail-Vorschau. Dies ist der dokumentierte bestehende Portalablauf. Doppelklickgrenze siehe H1. |
| Modulgrenzen | `assist` liest Fachinhalte über öffentliche Ports und greift nicht auf fremde interne Repositories zu. Die Adapter liegen in den zuständigen Fachmodulen. Der Modulstrukturtest besteht. |
| Oberfläche | Zusammenfassungen bieten Quellenverweise und Kopieren. Quellen lassen sich zu den betreffenden Einträgen öffnen und fokussieren. Rückfragen und Hinweise gehen nicht automatisch in den Antworttext. Ersetzung vorhandenen Texts ist grundsätzlich vorgesehen, aber F2 verletzt sie während laufender Generierung. Enter im Zielfeld startet den Entwurf statt den Versand. Deutsche und englische Texte sind vorhanden; die vorgesehenen Browserabläufe bestehen. Eine gesonderte manuelle Screenreader-Prüfung wurde nicht vorgenommen. |
| Vertrag | Assist-Routen und Vertrag stimmen in der Routenprüfung überein; generierte Typen und Frontend-Prüfung bestehen. Das ist kein vollständiger Nachweis jeder Antwortvariante. F4 und H2 nennen konkrete Abweichungen zwischen Vorgabe, Beschreibung, Server und Testersatz. |

## Ausgeführte Prüfungen

Alle Produktprüfungen verwendeten synthetische Daten und lokale Testserver. Es wurden keine echten KI-Zugänge angesprochen. Der lokale PostgreSQL-Testdienst wurde nach den Prüfungen beendet.

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` (lokal mit dem verfügbaren Python ausgeführt) | Bestanden. |
| `python3 scripts/task_board.py --check` (gleicher Interpreter) | Bestanden; Übersicht aktuell. |
| `python3 -m unittest discover -s tests -v` | Nicht vollständig grün: abschließender nativer Windows-Lauf mit UTF-8, 77 Tests, 5 Fehlschläge, 9 Fehler, 1 übersprungen. Probleme betreffen die POSIX-/Release-/Backup-Testumgebung, Windows-Pfade, Prozessausführung und das Entfernen temporärer Git-Dateien; nicht die hier geänderten Assist-Dateien. Ein Linux-Gegenlauf war nicht möglich, da in der vorhandenen WSL-Umgebung Python fehlt. Dies wird ausdrücklich nicht als bestandene Gesamtsuite gewertet. |
| Backend: `AssistApiTest,AssistContractTest,Ai*,ModuleStructureTest,Helpdesk*,Portal*,Sla*,Ticket*` | 194 Tests bestanden, keine Fehler oder übersprungenen Tests. Darunter `AiApiTest`, `AiInternalsTest` und die angeforderten Helpdesk-, Portal- und SLA-Tests. Java 25.0.4.1, lokales PostgreSQL 18; kein eigener Nachweis des gepinnten Containerbetriebs. |
| Frontend: `npm run verify` | Bestanden, einschließlich 102 Unit-Tests, Typen/Verträge und Produktionsbuild. Vite-Hinweise zu `use client` und Paketgröße verhindern den Build nicht. |
| Browser: `ai-assist.test.mjs`, `ai.test.mjs`, `helpdesk.test.mjs`, `portal.test.mjs` | 61 Tests bestanden. |
| Zusätzliche Backend-Gegenproben | Zwei gezielte Fehlschläge für F1; erfolgreiche Gegenprobe zur getrennten Lesetransaktion. Eine weitere gezielt scheiternde Statusprüfung bestätigt F4. |
| Zusätzliche Browser-Gegenproben | Drei gezielte Fehlschläge: Überschreiben bei Mail und Portal (F2), veraltete Zusammenfassung nach gespeicherter Titeländerung (F3). |

Die zusätzlichen Gegenproben wurden vorübergehend aus den vorhandenen Testfixtures abgeleitet und gegen unveränderten Produktcode ausgeführt. Ihre Quelldateien und die Laufprotokolle wurden lokal für eine Nachprüfung aufbewahrt; sie sind keine produktive Änderung dieses Berichts. Die oben beschriebenen Abläufe geben die jeweils entscheidenden Schritte und Assertions wieder. Die regulären grünen Suiten widerlegen diese Befunde nicht, weil genau diese Abläufe dort fehlen beziehungsweise die Statusprüfung beide Antworten akzeptiert.

## Empfehlung

**Nachbessern.** F1 bis F4 vor der Abnahme beheben und durch dauerhafte, gezielte Regressionstests absichern. Danach die betroffenen API- und Browserabläufe erneut unabhängig prüfen. Die Datenabgrenzung und Modulstruktur sind grundsätzlich nachvollziehbar; die Empfängerbindung, der Schutz eigener Texte, die Gültigkeit sichtbarer Ergebnisse und der Portalvertrag sind noch nicht ausreichend erfüllt.

Die weitergehende serverseitige A11-Garantie bleibt eine ausdrücklich benannte Grenze gemäß H1. Die nicht bestandene Python-Gesamtsuite muss in einer geeigneten Linux-Umgebung vervollständigt werden; aus diesem Review folgt dafür keine Freigabe. Fachliche Abnahme und eine etwaige Annahme bewusst eingeschränkter Kriterien bleiben bei Holger.
