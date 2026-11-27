# Unabhängige technische Nachprüfung: TASK-141, PR #363

Prüfer: Codex, 27. November 2026, Sitzung `codex-20261127-task141-pr363`.

Geprüft wurde [PR #363](https://github.com/1uedev/task-manager/pull/363), Branch `claude/project-thread-av1na6`, Head `bebf763be2a5823bc95fe11af5a2ee92ea937797`, gegen Basis `0ae43571da3faa6ed54bb7bdbf59b913001eb688`. Die Zeilenangaben beziehen sich auf diesen Head. Maßstab sind AGENTS.md, docs/DEVELOPMENT_REVIEW.md, TASK-141 mit Regeln 1 bis 6 und Übergabe, DEC-172, die ursprüngliche Prüfung in [REVIEW_TASK-126_PR-359.md](REVIEW_TASK-126_PR-359.md) und der Abschnitt „KI am Ticket“ in backend/README.md.

Die Nachprüfung umfasst alle 20 geänderten Dateien und die betroffenen Aufrufwege. Sie erfolgte in einem getrennten Worktree mit synthetischen Daten und lokalen Modell-Testservern. Keine Migration und keine Aufrufe gegen echte KI-Anbieter. Für Gegenproben zeitweise zurückgenommene Korrekturen wurden vollständig wiederhergestellt. Dieser Bericht ändert weder Produktcode noch Aufgabenstatus und ist keine fachliche Abnahme.

Bei der abschließenden Statusprüfung war PR #363 bereits zusammengeführt (27. November 2026, 07:40 Uhr MEZ; Merge-Commit `92003b8d31764f263e108b14c43d291cb154753f`). Der PR-Head ist unverändert. Die Empfehlung bezieht sich auf den geprüften Ergebnisstand; der bereits erfolgte Merge ersetzt die technische und fachliche Abnahme nicht.

## Befunde – vor der Abnahme zu beheben

### F1 – Nach dem Neuladen infolge eines Speicherkonflikts bleibt die alte Zusammenfassung sichtbar

**Datei/Zeile:** `frontend/src/pages/TicketPage.tsx`, Zeilen 197–201 und 318; `frontend/src/pages/TicketAi.tsx`, Zeilen 209–214.

**Szenario:** Eine Zusammenfassung erzeugen. Eine andere Person speichert danach einen geänderten Titel. Die erste Person versucht mit ihrer alten Ticketversion eine eigene Änderung zu speichern. Der Server antwortet mit 412; die Seite lädt das inzwischen geänderte Ticket und zeigt den Konflikthinweis sowie den neuen Titel im Seitenkopf. Die bisherige Zusammenfassung bleibt dennoch als Vorschau sichtbar, ohne erneutes Lesen des Laufs.

**Ursache und Auswirkung:** Nur `onChanged` erhöht die Änderungsrunde der Ticketseite. Der Konfliktpfad ruft dagegen ausschließlich `reloadTicket()` auf. Der Zusammenfassungseffekt reagiert nur auf `round + attachmentChanges`, nicht auf die tatsächlich neu geladene Ticketversion. Die Seite kennt somit einen neuen Quellenstand, zeigt aber weiter den alten Sachstand. Die zusätzliche Prüfung beim späteren Kopieren kann den Inhalt dann noch ablehnen; sie beseitigt die falsche Anzeige nach dem Neuladen nicht. Regel 3 ist für diesen vorhandenen Änderungsweg noch nicht erfüllt.

**Nachweis:** Die zusätzliche Browser-Gegenprobe `review: a conflict reload must invalidate the displayed summary` ändert Titel und Version im Testserver, löst den normalen Speicherkonflikt aus und wartet auf den neu geladenen Titel. Die Anzahl der Laufabrufe bleibt unverändert; die alte Vorschau bleibt vorhanden. Die Assertion scheitert. Der Zustand wurde zusätzlich als Screenshot geprüft.

**Vorschlag:** Die Gültigkeitsprüfung auch an einen erfolgreich neu geladenen Quellenstand binden, insbesondere Ticketversion beziehungsweise die relevanten Datenänderungen nach einem Konflikt. Alle Neuladewege zentral berücksichtigen, statt nur erfolgreiche eigene Schreibaktionen zu zählen. Einen Regressionstest mit „Zusammenfassen – fremde Änderung – eigener 412-Konflikt – neuer Ticketstand“ ergänzen.

### F2 – Überlappende Gültigkeitsabfragen können die neuere Ablehnung verwerfen

**Datei/Zeile:** `frontend/src/pages/TicketAi.tsx`, Zeilen 106–117, 209–214 und 235–244.

**Szenario:** Bei einer gültigen Zusammenfassung „Kopieren“ wählen. Dessen Laufabfrage wird serverseitig noch vor einer Änderung geprüft, ihre Antwort kommt aber verzögert zurück. Währenddessen den Titel auf derselben Seite ändern und speichern. Dadurch beginnt die zweite Prüfung, die den Vorschlag korrekt als ungültig erkennt. Treffen die Antworten in der Reihenfolge „ältere gültige Antwort, neuere ungültige Antwort“ ein, wird der alte Inhalt kopiert und die spätere Ablehnung verworfen. Die alte Vorschau bleibt sichtbar.

**Ursache und Auswirkung:** Beide `recheck()`-Aufrufe übernehmen dieselbe `round.current` und dasselbe Objekt `shown`. Ein neuer Prüfaufruf erhöht keine Anfragesequenz. Die erste fertige Antwort ersetzt `current.current`; die zweite wird anschließend an `current.current !== shown` verworfen, selbst wenn sie zu einer neueren Quellenänderung gehört. Damit entscheidet die zuerst eintreffende Antwort, statt der neuesten notwendigen Prüfung Vorrang zu geben. Auch automatische Prüfungen können auf diese Weise konkurrieren.

**Nachweis:** Die zusätzliche Browser-Gegenprobe `review: a later source check must win over an older copy check` steuert genau diese beiden zulässigen Antwortzeitpunkte. Nach erfolgreichem Speichern liefert sie erst die vorher geprüfte gültige Antwort und dann `invalid: ai.source_changed` mit `content: null`. Beobachtet wurden alter Text in der Zwischenablage und weiterhin eine sichtbare Vorschau. Die Assertion auf Entfernen der Vorschau scheitert. Die kontrollierten Antworten bilden eine mögliche Netzwerkreihenfolge ab; sie behaupten keinen Fehler des serverseitigen Fingerabdrucks.

**Vorschlag:** Gültigkeitsprüfungen mit einer eigenen fortlaufenden Anfragesequenz beziehungsweise Quellengeneration versehen. Eine bekannte Quellenänderung muss ältere Prüfungen und daraus folgende Übernahmen entwerten. Nur die zur aktuellen Generation gehörende Antwort darf Zustand, Zwischenablage oder Antwortfeld verändern. Beide Antwortreihenfolgen testen; während einer überholten Prüfung darf kein alter Inhalt übernommen werden.

### F3 – Der Testersatz verweigert Portalkonten weiterhin erst nach dem Objektzugriff

**Datei/Zeile:** `frontend/tests/browser/backend.mjs`, Zeilen 903–906, 1053–1056, 1102 und 3992–3994.

**Szenario:** Ein vollständig angemeldetes Portalkonto ruft jede der drei Assist-Routen für ein vorhandenes Ticket, eine unbekannte Ticketnummer und ein unbekanntes Projekt auf. Der echte Server verweigert diese Zugriffe vor der Objektsuche mit 403 `auth.forbidden`. Der Testersatz erreicht `assistRoute()` jedoch erst nach Projekt- und Ticketsuche.

**Nachweis:** Eine direkte Probe durch den registrierten Request-Handler des Testersatzes prüfte alle neun Kombinationen. Für das vorhandene Ticket antworten die drei Routen mit 403 `auth.forbidden`, für die unbekannte Ticketnummer mit 404 `ticket.not_found`, für das unbekannte Projekt mit 404 `project.not_found`. Die Prüfung auf neun identische Ablehnungen scheitert. Der verschärfte echte Backend-Test besteht dagegen.

**Auswirkung:** Die Korrektur im Produktserver ist wirksam, aber Regel 4 verlangt ausdrücklich auch einen übereinstimmenden Testersatz. Der Kommentar „before any object is read“ in `assistRoute()` trifft auf den tatsächlichen Aufrufweg nicht zu. Browserprüfungen können die falsche Antwort weiterhin als Serververhalten behandeln.

**Vorschlag:** Die Portalablehnung für die drei Assist-Routen im Testersatz vor Projekt- und Ticketsuche anordnen. Exakte Status- und Codeprüfungen für bekannte und unbekannte Objekte ergänzen. Interne Konten müssen ihre bestehenden 404-Antworten behalten.

### F4 – Eigene Felder im Testersatz folgen nicht ihren Leseregeln

**Datei/Zeile:** `frontend/tests/browser/backend.mjs`, Zeilen 403 und 3930–3931; Vergleich mit `backend/src/main/java/taskmanager/tickets/internal/TicketReaderAdapter.java`, Zeilen 24–34.

**Szenario:** Eine Person darf den Titel lesen, hat aber ein ausdrückliches Leseverbot auf `custom.secret`. Das Ticket enthält einen Wert in diesem eigenen Feld. Beim Zusammenfassen übernimmt der Testersatz den verborgenen Wert trotzdem in Quelle S1.

**Ursache und Auswirkung:** `customReadable()` prüft nur, ob der Titel lesbar ist. Danach werden alle konfigurierten eigenen Felder übernommen. Die neue Assist-Implementierung verwendet diese grobe Hilfsfunktion, obwohl der echte Ticketport jedes Feld gegen `view.readable()` prüft. Auch ein später entzogener Zugriff auf ein einzelnes eigenes Feld verändert den so aufgebauten Fingerabdruck nicht zuverlässig. Regel 5 und der gewählte ursprüngliche Hinweis H2 sind in diesem Punkt noch nicht erfüllt. Dies ist eine Abweichung des Testersatzes; ein entsprechender Datenabfluss im Produktserver wurde nicht festgestellt.

**Nachweis:** Die zusätzliche Browser-Gegenprobe `review: custom fields with a read deny must not reach summary stand-in` setzt ein ausdrückliches Verbot für `custom.secret` bei weiterhin lesbarem Titel. Im aufgezeichneten Kontext steht trotzdem `Secret review field: INTERNAL_SECRET`. Die Assertion auf Ausschluss dieses synthetischen Werts scheitert.

**Vorschlag:** Eigene Felder im Assist-Kontext einzeln über ihre `custom.<key>`-Leseregel prüfen. Positive und negative Beispiele sowie eine nachträglich entzogene Feldberechtigung gegen den echten Server abgleichen. Die Änderung kann auf die Assist-Pfade begrenzt bleiben; ein pauschaler Umbau aller vorhandenen Testhilfen ist dafür nicht erforderlich.

## Hinweise – können später bearbeitet werden

### H1 – Mehrere H2-Korrekturen können entfernt werden, ohne dass die vorhandenen Assist-Browsertests scheitern

**Datei/Zeile:** `frontend/tests/browser/backend.mjs`, Zeilen 3926–3931, 3947 und 4000–4002; `frontend/tests/browser/ai-assist.test.mjs`, insbesondere Zeilen 117–124 und 181–239.

**Szenario/Nachweis:** In einer gesonderten Mutation wurden gleichzeitig die Vorgabe Deutsch bei fehlender Sprache, die Aufnahme eigener Felder, die Standardpriorität und die Ablehnung eines Kontakts ohne Adresse zurückgenommen. Alle 13 bestehenden Assist-Browsertests blieben grün. Die Änderungen sind im Original überwiegend implementiert; eine zusätzliche direkte Positivprobe bestätigte fehlende Sprache, lesbares eigenes Textfeld, Standardpriorität, Ausschluss der zuständigen Person und die Ablehnung eines Kontakts ohne Adresse. Die vorhandene Suite schützt diese vier Korrekturen aber nicht zuverlässig gegen Rückschritte.

**Vorschlag:** Kleine dauerhafte Vertragsproben für diese Antwort- und Kontextvarianten hinzufügen. Sie brauchen keinen vollständigen Browserablauf. Jede gezielte Rücknahme sollte am jeweils zugehörigen Test scheitern. Die für F3/F4 notwendigen Negativtests gehören bereits zur Behebung dieser Befunde.

Dieser H1 ist ein Hinweis zur Testabdeckung der Nachprüfung. Der ursprüngliche H1 aus der Prüfung von TASK-126 bezeichnet dagegen die serverseitige Idempotenz. Diese bleibt gemäß DEC-172 ausdrücklich außerhalb von TASK-141: Die Oberflächensperre verhindert überlappende Doppelklicks desselben Formulars, aber keine doppelte Bestätigung über getrennte Anfragen oder nach verloren gegangener Antwort. Sie wird hier nicht erneut als Nachbesserungsauftrag erhoben.

## Abgleich der sechs Regeln und der ursprünglichen Befunde

| Regel / ursprünglicher Punkt | Ergebnis |
|---|---|
| 1 / F1: Fingerabdruck | Die Backend-Korrektur besteht die vorgesehenen und unabhängigen Gegenproben. Zweck, Sprache, Ticket, echte Eintragskennungen und Bindung werden berücksichtigt. Wechsel des Mailkontakts bei gleicher Sprache, geänderte Kontaktadresse, Kundenwechsel beim Titelentwurf und ein ersetzter Eintrag mit gleichem Text/Zeitpunkt entwerten. Die ursprünglichen Gegenproben `reviewContactChangeMustInvalidateDraft` und `reviewCustomerChangeMustInvalidateTitleOnlyDraft` bestehen jetzt. Zusätzlich bestehen ein Portal-Kontaktwechsel gleicher Sprache und die Änderung eines nur als Empfängermetadatum verwendeten Kontaktnamens. |
| 1: Nicht verwendete Felder / Modellgrenze | Eine geänderte Beschreibung entwertet einen Antwortentwurf weiterhin nicht. Der Vergleich benutzt nicht pauschal die Ticketversion. Bindungsdaten werden nur gehasht; `AiRequest.sources` enthält sie nicht. Eine zusätzliche echte API-Probe bestätigt, dass die ausschließlich als Empfängermetadaten gesetzte Kontaktbezeichnung sowie Kunden- und Kontaktkennung nicht im Modellprompt stehen. Kennungen und Namen, die bereits Teil eines zulässigen Quelltextes sind, werden dadurch nicht allgemein aus diesem Text entfernt. |
| 2 / F2: Eigener Text | Die aktuelle Referenz auf `body` wird beim Eintreffen des Ergebnisses geprüft. Beide regulären verzögerten Browsertests bestehen: Während der Generierung geschriebener Text bleibt erhalten, der Entwurf wird zum Ersetzen angeboten. Ohne diese Korrektur scheitern beide Tests. |
| 3 / F3: Erneute Prüfung | Eigene erfolgreiche Ticketänderungen, Kunden-/Kontaktänderungen, Portalantworten, Versandbestätigungen und die vorhandenen Änderungsrückmeldungen des Helpdesks erreichen die Änderungsrunde. Kopieren und Ersetzen lesen grundsätzlich erneut und lehnen ungültige Ergebnisse ab. Die isolierten Tests dafür reagieren auf das Entfernen der Prüfungen. Es bleiben der Konflikt-Neuladeweg (F1 dieses Berichts) und konkurrierende Prüfungen (F2). |
| 4 / F4: Portal | Im echten Server verweigert die gemeinsame Anfragegrenze externe Konten mit exakt 403 `auth.forbidden`, bevor Assistenz oder Fachports aufgerufen werden. Der API-Test prüft die drei Routen mit bekannter und unbekannter Nummer, beim Entwurf beide Kanäle. Interne fremde und unbekannte Tickets bleiben 404. Vertrag und README stimmen mit dem Server überein. Der Testersatz weicht noch ab, siehe F3. |
| 5 / H2: Testersatz | Fehlende Sprache, Standardpriorität, Kontakt ohne Adresse und die grundlegende Neubildung eines Fingerabdrucks aus Quellen, Eintragskennungen und Bindung sind umgesetzt. Zuständige Person wird nicht als festes Zusammenfassungsfeld übernommen. Die positiven Varianten wurden zusätzlich direkt ausgeführt. Eigene Feldrechte und Portal-Reihenfolge siehe F3/F4; dauerhafte Testabdeckung siehe H1. |
| 6 / H3: Kürzung | `TicketAssist` meldet die Kürzung bei mehr als 500 Quellen. `AiRequest.truncated`, `RunService` und `AiFrame` transportieren sie bis zum gespeicherten Lauf und zum Modellhinweis. Der Grenztest mit 520 kurzen Mails bestätigt genau 500 Modellquellen, Entfernen der ältesten Einträge, `truncated: true` und den Hinweis im Prompt, ohne zusätzliche Budgetkürzung. |
| 6: Oberfläche und Probelauf | Zusammenfassung sowie Mail- und Portalentwurf zeigen den Kürzungshinweis. Für beide Entwurfskanäle wurden zusätzliche Browserproben erfolgreich ausgeführt. Deutsche und englische Texte sind vorhanden. Der administrative Probelauf setzt das neue Feld ausdrücklich auf `false`; eine gegebenenfalls spätere Kürzung durch das Kontextbudget bleibt möglich. Die KI-Grundlagentests bestehen. |
| Grenzen / Regression | Öffentliche Fachports, Eintragsauswahl, kundenbezogene Datenabgrenzung, Prüferauswahl nach Anwendungsfall und Objektart sowie getrennte Lesetransaktion bleiben erhalten. Die unabhängige Probe zum Entzug des Ticketleserechts liefert weiter regulär ungültigen Inhalt, ohne die äußere Lesetransaktion zu zerstören. Es werden keine neuen automatischen Schreib- oder Versandwege eingeführt. |

## Tests ohne ihre jeweilige Korrektur

Die Mutationstests verändern ausschließlich die isolierte Prüfkopie. Sie wurden nach den regulären Läufen ausgeführt und anschließend zurückgenommen. Erwartete Fehlschläge dieser Mutationen zählen nicht als Fehler des unveränderten PR-Stands.

| Zurückgenommene Korrektur | Beobachtung |
|---|---|
| Bindungswerte im Fingerabdruck, echte Eintragskennung, frühe Portalablehnung und Übernahme von `alreadyTruncated` | Alle fünf gezielt ausgewählten Backend-Tests scheitern: Kontaktwechsel, Kundenwechsel beim Titelentwurf, ersetzter Eintrag, Portalstatus und Quellengrenze. Keine Initialisierungsfehler; es sind fünf fachliche Assertions. |
| `TicketAi.tsx` und `TicketPage.tsx` auf dem Basisstand | Vier ausgewählte neue Tests scheitern gezielt: gespeicherte Titeländerung, Kürzungshinweis und Erhalt eigener Texte in Mail und Portal. |
| Nur erneutes Lesen vor Kopieren und Ersetzen entfernt, übrige Korrekturen aktiv | Der neue Kopiertest scheitert. Auch der Portaltest scheitert nun ausdrücklich an der fehlenden Ablehnung nach Quellenänderung, nachdem der Erhalt des während der Generierung geschriebenen Texts erfolgreich war. Damit ist seine zweite Prüfung unabhängig von F2 wirksam. |
| Vier H2-Varianten im Testersatz entfernt | Alle 13 Assist-Browsertests bestehen weiterhin; siehe H1. |
| Originalstand wiederhergestellt | Alle 11 `AssistApiTest`-Tests sowie sechs unabhängige API-Gegenproben bestehen erneut. Der Frontend-Produktionsbuild wurde aus den wiederhergestellten Dateien neu erstellt. |

Damit sind die vier neuen Backend-Tests, der verschärfte Portaltest und alle fünf neuen Browsertests grundsätzlich gegen die jeweilige Fehlerklasse empfindlich. Das beweist weder die Vollständigkeit der Änderungswege noch die vollständige Abdeckung aller H2-Unterfälle; die zusätzlichen Befunde zeigen diese Grenzen.

## Ausgeführte Prüfungen und Grenzen

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` mit dem lokal verfügbaren Python | Bestanden. |
| `python3 scripts/task_board.py --check` | Bestanden; Übersicht aktuell. |
| `python3 -m unittest discover -s tests -v` lokal | Ausgeführt, unter Windows nicht vollständig bestanden: 88 Tests, 25 Fehlschläge, 8 Fehler, 1 übersprungen im abschließenden Lauf. Ursachen sind unter anderem POSIX-Shellaufrufe mit Windows-Pfaden, Kodierung von Prozessausgaben und die Bereinigung temporärer Git-Dateien. Ein zuvor eingeschränkt gestarteter Lauf hatte zusätzlich gesperrte Prozessausführung. Diese lokalen Läufe werden nicht als grün gewertet. |
| Dieselbe Python-Suite unter Linux in der CI am geprüften Head | 88 Tests bestanden. Jobprotokoll unabhängig gelesen: [Repository checks](https://github.com/1uedev/task-manager/actions/runs/45778719597). Dies ist ausgewertete CI-Evidenz, kein eigener lokaler Linux-Lauf. |
| Backend lokal: `AssistApiTest,AssistContractTest,Ai*,ModuleStructureTest,Helpdesk*,Portal*,Sla*,Ticket*` | 198 Tests bestanden, 0 Fehler, 0 übersprungen; einschließlich aller angeforderten Klassen. Java 25.0.4.1 mit lokalem PostgreSQL 18. Ein erster Lauf konnte wegen gesperrter lokaler Datenbankverbindungen nicht initialisieren; der vollständige Wiederholungslauf bestand. |
| Backend nach Wiederherstellung plus eigene Gegenproben | 17 Tests bestanden: 11 `AssistApiTest` und sechs `AssistReviewProbeTest`. |
| Backend-CI am geprüften Head | 787 Tests bestanden, 0 Fehler, 0 übersprungen; Build und Start-/Stopp-/Neustartprüfung erfolgreich. Jobprotokoll unabhängig gelesen: [Backend](https://github.com/1uedev/task-manager/actions/runs/45778719511). |
| Frontend: `npm run verify` | Bestanden: Vertragsgenerierung, Typprüfung, 102 Unit-Tests und Produktionsbuild. Bekannte Vite-Hinweise zu Modul-Direktiven und Paketgröße bleiben Hinweise. |
| Browser: `ai-assist.test.mjs`, `ai.test.mjs`, `helpdesk.test.mjs`, `portal.test.mjs` | 66 Tests bestanden. Der erste Start wurde durch gesperrte lokale Browserverbindungen verhindert; der vollständige Wiederholungslauf bestand. |
| Zusätzliche Browser-/Testersatzproben | Vier gezielt scheiternde Gegenproben bestätigen F1 bis F4 dieses Berichts. Zwei weitere Browserproben für den Kürzungshinweis in Mail und Portal bestehen. Die direkte Testersatzprobe bestätigt positive H2-Varianten und reproduziert die abweichenden Portalantworten für alle drei Routen. |
| Migration und Arbeitskopie | Keine Migration im Diff. Zeitweise mutierte Produktdateien wiederhergestellt; die Prüfproben und Laufprotokolle werden lokal getrennt aufbewahrt. |

Der zusätzliche Lauf für die beiden Entwurfshinweise wurde zunächst wegen eines Nutzungslimits der automatischen Freigabeprüfung nicht gestartet. Nach Fortsetzung konnte er regulär ausgeführt werden; beide Tests bestanden. Daraus verbleibt keine offene Prüfung. Alle produktbezogenen Modellaufrufe gingen ausschließlich an lokale Testserver; es wurden keine echten KI-Anbieter verwendet.

## Empfehlung

**Nachbessern.** Die ursprünglichen Backend-Befunde zur Bindung und Portalablehnung, der Schutz während der Generierung geschriebener Texte sowie die Weitergabe der Kürzungsmarkierung sind wirksam korrigiert. Die erneute Prüfung sichtbarer Ergebnisse ist jedoch bei Konflikten und überlappenden Anfragen noch unvollständig. Außerdem erfüllt der Testersatz die ausgewählten Rechte- und Portalregeln noch nicht durchgehend.

F1 bis F4 dieses Berichts beheben und durch gezielte Regressionstests absichern; danach die betroffenen Abläufe erneut unabhängig prüfen. Die bewusst ausgesparte serverseitige Idempotenz bleibt eine benannte Grenze. Die fachliche Abnahme von TASK-126 und TASK-141 bleibt bei Holger.
