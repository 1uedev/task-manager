# Zusätzliche Codex-Prüfnachweise TASK-027 / PR #112

**Stand bei Veröffentlichung am 21. Oktober 2026:** TASK-027 wurde inzwischen mit PR #114 korrigiert und nach der getrennten Claude-Prüfung #115 durch Holger abgenommen (#118). Der hier am 20. Oktober auf dem ursprünglichen PR-#112-Stand reproduzierte Befund F1 ist damit kein aktueller offener Befund. Die damaligen eigenen PostgreSQL-18.6- und Deadlock-Nachweise werden als Ergänzung unter einem eigenen Dateinamen erhalten; der Bericht aus #115 wird nicht ersetzt. Die Abnahme und DONE.md bleiben unberührt.

**Ergebnis des ursprünglichen Prüflaufs vom 20. Oktober:** Die nicht dokumentierte Kopplung von Anlage und Änderung an `ticket.read` war über die echte API reproduziert (**F1/P2**). Zusätzlich wich die dokumentierte Sperrfolge für die Anlage vom Code ab (**H-C1/P3**, Dokumentationshinweis, kein daraus nachgewiesener Deadlock). Die geprüften Projekt- und Feldgrenzen, Entzugsfälle, Rückabwicklung, Historie und Versionskonflikte funktionierten. Eine Rechteumgehung wurde in diesen Versuchen nicht festgestellt.

## Abgleich am 21. Oktober 2026

Veröffentlichungsbasis ist main `b85abdddce048fb7a76a44222e61a969cc6417e6`. Im Quelltext ist die Korrektur aus #114 vorhanden: Schreibantworten benutzen `readableFields`, das ohne `ticket.read` eine leere Feldausgabe liefert, statt die Transaktion abzubrechen. Der zusätzliche öffentliche Ticketport ist inzwischen ebenfalls vorhanden; TASK-028 und TASK-042 haben den ursprünglichen Stand erweitert. Die Funde zu NUL-Zeichen und unbekannten Anlagefeldern aus der anderen Prüfung sowie Holgers Entscheidung zu sichtbaren Änderungsmetadaten sind in main dokumentiert. Die früher gemeldeten Projektbefunde wurden ebenfalls durch #114 korrigiert.

Dieser Abgleich war eine Quelltext-/Dokumentnachprüfung, kein neuer eigener Backendlauf des erweiterten aktuellen main. Die Laufzahlen unten gelten ausschließlich für den exakt benannten ursprünglichen Stand. H-C1 ist am aktuellen Quelltext weiterhin nachvollziehbar und bleibt ein niedriger Dokumentationshinweis; er ist nicht F2 der Claude-Prüfung (dort bezeichnet F2 die NUL-Eingabe). Dieser Zusatzbericht erklärt keine abgenommene Aufgabe erneut für offen.

## Grundlage und Umfang

- Codex, getrennt vom Autor Claude; Sitzung `codex-20261020-tickets-review-01`, 20. Oktober 2026; eigener Klon und Branch `review/task-027-codex`.
- Am 20. Oktober ausgeführtes main: [`9a6a20048189e89cb5d23afdafef0fd0a2826305`](https://github.com/1uedev/task-manager/commit/9a6a20048189e89cb5d23afdafef0fd0a2826305), Integration von [PR #112](https://github.com/1uedev/task-manager/pull/112). PR-Kopf: `1eec61c80fb2d1de96dc88995cfe8fb17df86c63`; die gelesene CI bezieht sich genau auf diesen Kopf. Die neuere Veröffentlichungsbasis und deren Abgrenzung stehen oben.
- Maßstab: AGENTS.md, CLAUDE.md, README.md, TODO.md/TASK-027, DONE.md, docs/AI_WORKFLOW.md, DEC-040/DEC-067, REQ-TKT-001, REQ-TKT-005, REQ-ACL-003, REQ-API-001, relevante Architektur-/Rechte-/Fachmodellunterlagen und docs/TECHNICAL_BASELINE.md. Bestätigte Beauftragung und technische Umsetzungsvorschläge in DEC-067 sind keine fachliche Gesamtabnahme.
- Zum ursprünglichen Prüfzeitpunkt stand TASK-027 auf BEREIT ZUR PRÜFUNG bei `claude-20261020-tickets-01`; TASK-026 war integriert, aber noch nicht abgenommen. Inzwischen sind beide abgenommen. Die Gegenprüfung übernimmt keine Implementierungsaufgabe und ändert weder TODO.md noch DONE.md.
- Kein Produktcode verändert, auch nicht vorübergehend. Eigene Ergänzungen ausschließlich in einer Testdatei; danach bytegleich wiederhergestellt. Bericht und wiederholbare Nachweise sind die einzige Lieferung.

## Befunde nach Schwere

### F1 — P2: Erlaubte Schreibaktionen scheitern nachträglich ohne `ticket.read`

**Fundstelle:** `backend/src/main/java/taskmanager/tickets/internal/TicketService.java:166`, `:229` und `:235`; `readable` ab Zeile 288. Die Methode prüft dort für die Antwort nochmals die Aktion `ticket.read` und wirft andernfalls `404 project.not_found`. Das geschieht bei Anlage und echter Änderung nach dem Schreiben von Ticket und Historie, innerhalb derselben Transaktion; diese wird vollständig zurückgerollt.

**Reproduzierter Ablauf:**

1. Drei aktive, vollständig angemeldete Konten sind Mitglieder desselben Projekts. Ein Besitzer mit vollständigen Ticketrechten legt Ticket 1 an.
2. Konto A erhält ausschließlich `ticket.create` sowie Lese- und Schreibregeln für alle fünf Ticketfelder. Konto B erhält ausschließlich `ticket.edit` und dieselben Feldregeln. Es fehlen weder Mitgliedschaft noch Feldrechte.
3. A legt ein gültiges Ticket an; B ändert mit korrektem `If-Match: "1"` den Titel von Ticket 1. Beide Aufrufe liefern **404**. Es bleibt genau ein Ticket mit unverändertem Titel bestehen.
4. Ausschließlich `ticket.read` für A und B zusätzlich vergeben; dieselben Schreibwege funktionieren nun mit **201 beziehungsweise 200**.

| Aktionsrechte, jeweils mit passenden Feldrechten und Mitgliedschaft | Anlage | Änderung |
| --- | --- | --- |
| Nur `ticket.create` beziehungsweise nur `ticket.edit` | 404, Rückabwicklung | 404, Rückabwicklung |
| Dieselben Rechte plus `ticket.read` | 201 | 200 |

**Auswirkung:** Die getrennt beschriebenen Anlage- und Änderungsaktionen sind faktisch nur zusammen mit der zusätzlichen Leseaktion nutzbar. DEC-067, backend/README.md und `contracts/tickets.openapi.yaml` nennen für diese Operationen `ticket.create` beziehungsweise `ticket.edit` und Feldrechte; die zusätzliche Voraussetzung ist dort nicht vereinbart. Selbst der Zweig ohne Wertänderung verlangt sie. Die Verweigerung schützt zwar Daten, meldet aber ein verborgenes Projekt statt der tatsächlichen Rechtekombination. Bestehende Autorentests nutzen für erfolgreiche Schreibfälle stets auch `ticket.read` und erkennen die Kopplung deshalb nicht.

**Vorschlag:** Das Verhalten für separat vergebene Schreibaktionen ausdrücklich festlegen und konsistent umsetzen. Beim beschriebenen Modell die Quittung beziehungsweise die für den Schreibaufruf erlaubte Feldausgabe bilden, ohne am Ende zusätzlich die allgemeine Abrufaktion zu verlangen. Keine Leseaktion stillschweigend vergeben. Falls bewusst beide Aktionen nötig sein sollen, braucht es eine abgestimmte Vertragsänderung samt eindeutigen Fehlerregeln. Regressionen für Anlage ohne Leseaktion, Änderung ohne Leseaktion und deren Kontrollen mit Leseaktion ergänzen. Eigene Probe: `reviewCreateAndEditAreIndependentOfTheReadAction` im [Prüfpatch](review-evidence/task-027-codex/ticket-review-probes.patch).

### H-C1 — P3: Dokumentierte Sperrfolge der Anlage stimmt nicht mit dem Code überein

**Fundstellen:** `backend/src/main/java/taskmanager/tickets/internal/TicketService.java:35–38` und `:152–157`, `backend/README.md`, Abschnitt „Tickets (TASK-027) / Sperren“, sowie DEC-067 Punkt 6.

Die Dokumentation nennt **Rechte → Identität → Projekt → Ticket oder Zähler → Konto der Zuständigkeit**. Die Anlage ruft jedoch zuerst `assignee(...)` auf (Zeile 152). Bei gesetzter gültiger Zuständigkeit nimmt `IdentityVerification.accountIsActive` bereits die geteilte Kontosperre. Erst anschließend nimmt `tickets.nextNumber(...)` die Zählersperre (Zeile 157). Der tatsächliche Anlageweg lautet daher **Rechte → Identität → Projekt → Zuständigenkonto → Zähler**. Der Änderungsweg nimmt dagegen tatsächlich zuerst die Ticketzeile und danach das gegebenenfalls neue Zuständigenkonto.

**Prüfablauf:** Quelltextpfad einer gültigen Anlage mit aktivem Projektmitglied als Zuständigem vom Controller über `create`, `assignee`, `accountIsActive` bis `nextNumber` verfolgt und mit dem veröffentlichten Sperrvertrag verglichen. Dies ist ein statischer Befund; kein eigener Versuch behauptet einen durch diese Abweichung verursachten Produktdeadlock. Geteilte Kontosperren und die aktuellen Gegenwege ergaben in der Prüfung keinen belegten solchen Zyklus.

**Auswirkung/Vorschlag:** Weitere Module könnten sich bei der Transaktionskomposition auf eine falsche Reihenfolge verlassen. Tatsächlichen Ablauf und vereinbarten Vertrag vor dieser Nutzung angleichen; entweder die dokumentierte Reihenfolge herstellen oder die abweichende Anlagereihenfolge mit Begründung und Gegenwegen abstimmen und richtig dokumentieren. Kein eigenmächtiger Umbau eines gemeinsamen Sperrvertrags durch diesen Review.

## Erfolgreich geprüfte Schutz- und Transaktionsregeln

### Projektgrenzen und Feldrechte

Eigene HTTP-Proben prüfen Liste, Detail, Historie, Anlage und Änderung: Ein Konto mit unternehmensweit vergebenen Ticketrechten, aber ohne Projektmitgliedschaft, erhält 404. Dasselbe gilt für den Unternehmensprojektverwalter ohne Ticketrechte und für ein nur auf Projekt A berechtigtes Mitglied beim Zugriff auf Projekt B. `projects.manage` wird damit nicht zu einem Ticketrecht. Die Mitgliedschaftsprüfung liegt im unter der Projektzeilensperre gebildeten Kontext; die Tabellenabfragen des Ticketmoduls begrenzen zusätzlich auf Unternehmen und Projekt.

Für **jedes der fünf Felder** wurde einer sonst vollständig berechtigten Person ein ausdrückliches Leseverbot aus einer zusätzlichen Gruppe gegeben. Das Feld fehlt in Liste, Detail und sämtlichen sichtbaren Historienänderungen sowie in `writableFields`. Der Versuch, genau dieses Feld zu schreiben, endet mit 403; die Ticketversion bleibt unverändert. Die unveränderten Autorentests prüfen zusätzlich, dass eine Historienversion mit ausschließlich verborgenen Änderungen ganz fehlt und dass ein verbotener Teil einer gemischten Änderung die gesamte Änderung verhindert.

### Entzug und echte Wartebeziehungen

Die eigenen Hilfen verlangen die **konkrete PID des Halters in `pg_blocking_pids`** des wartenden Aufrufs. Eine bloße zeitliche Überschneidung von Threads reicht nicht.

| Eigener Versuch | Beobachtetes Ergebnis |
| --- | --- |
| Projektmitglied wird entfernt, während Historienabruf und Schreiben warten | Beide echten HTTP-Aufrufe warten auf genau die gehaltene Projektzeile; nach Entzug/Commit beide 404, spätere Liste 404, Titel unverändert. |
| `ticket.edit` wird unter Revisionssperre entzogen | Wartender Schreibaufruf 404 für den veralteten Kontext; frischer Abruf 200, frische Änderung 403; Ticket/Historie unverändert. |
| Schreibrecht des Titelfelds wird unter Revisionssperre verboten | Derselbe Ablauf: wartend 404, frisch Lesen 200/Schreiben 403; keine Änderung. |
| Sitzung wird während der Identitätsprüfung widerrufen | Der Schreibaufruf wartet nachweislich an der Sitzungszeile, danach 404; frischer Abruf 401 `auth.session_expired`, Ticket unverändert. |
| Bereits erlaubtes Schreiben vor Mitgliedschaftsentzug | Echter `TicketService.update` mit aktuellem Identitäts-/Sitzungskontext hält die Transaktion. HTTP-Entfernen wartet auf genau dessen Projektzeilensperre. Erst Schreibcommit, dann Entfernen 200; weiterer Mitgliedszugriff 404. Dieser Arm ist Dienstaufruf gegen HTTP, nicht zweimal HTTP. |
| Konfiguration wird vor wartendem Statuswechsel verbindlich | Wartender HTTP-Aufruf sieht die neue Konfiguration und antwortet 409 `ticket.transition_not_allowed`; Status und Ticketversion bleiben unverändert. |

Die Entzugs-/Konfigurationsfixtures benutzen synthetisches SQL mit derselben Revisions-/Projekt-/Sitzungssperre und Versionsänderung. Eine Rechteverwaltungs-HTTP-API existiert noch nicht. Der erfolgreiche Gegensinn mit bereits erlaubter Änderung verwendet den wirklichen Dienst und den wirklichen HTTP-Mitgliederweg. Nicht jede Kombination aller Identitätsentzugswege wurde nochmals neu aufgebaut; die integrierten bestehenden Identitäts-/Konkurrenztests bestehen im unveränderten Gesamtlauf.

Die Prüfung gilt bis zur gehaltenen Schreibgrenze: Eine schon erlaubte Transaktion kann zuerst committen und hält den Entzug bis dahin auf. Ein zuvor verbindlicher Entzug wird vom wartenden Aufruf gesehen. Daraus folgt keine zusätzliche Zeit-/Rechteprüfung unmittelbar beim Commit.

### Historie, Rückabwicklung und ETag

Ein eigener synthetischer Datenbanktrigger verweigert gezielt das späte Schreiben eines Historieneintrags. Sowohl eine Anlage als auch eine Änderung antworten allgemein mit 500, ohne die künstliche Ausnahmemeldung auszugeben. **Ticket, Zähler und Historie werden gemeinsam zurückgerollt**; die anschließende erfolgreiche Anlage erhält Nummer 2 und verbraucht keine Nummer des gescheiterten Versuchs. Dies ist absichtliche Fehlerinduktion zum Nachweis der Atomarität, kein weiterer Produktbefund. Trigger und Funktion wurden im Fixture wieder entfernt.

Zwei echte HTTP-Änderungen verschiedener berechtigter Personen mit identischem `If-Match: "1"` ergeben genau **200 und 412**, Ticketversion 2 und einen Historieneintrag der Änderung. Die bestehenden Tests prüfen zusätzlich 428 ohne ETag, veraltete/ungültige Werte, No-op ohne Versionswechsel, Nummernvergabe bei sechs gleichzeitigen Anlagen, Anfangsstatus, Übergänge, Pflichtkonflikte und historischen Feldinhalt.

### Sperrfehler und Migration

Ein eigener Versuch erzeugt absichtlich einen **echten PostgreSQL-Deadlock**: Ein synthetischer Gegner sperrt die Ticketzeile und wartet danach auf die Rechtezeile, während der HTTP-Schreibweg die Rechte hält und auf das Ticket wartet. Beide Richtungen werden über die exakten PIDs belegt. PostgreSQL bricht den HTTP-Arm wegen Deadlock ab; die API antwortet **503 `ticket.busy`**, `Retry-After: 1`, ohne Eingabewert oder Deadlockdetails. Ticket/Version bleiben unverändert; die Wiederholung gelingt. Der Gegner verletzt gezielt die reguläre Reihenfolge, um die Fehlerabbildung zu prüfen; dies belegt keinen natürlich erreichbaren Produktzyklus. Der unveränderte Autorentest für echten Sperrtimeout besteht ebenfalls. Für einen Serialisierungsfehler wurde die SQLSTATE-Abbildung `40001` im Quelltext geprüft, kein eigener realer Serialisierungsabbruch erzeugt.

V0012 besitzt ausschließlich `ticket`, `ticket_counter` und `ticket_change`, mit eindeutiger Projektnummer und Historienreferenz auf das Ticket. Das Modul liest keine fremden Tabellen; es benutzt Projekt-, Identitäts- und Rechteports. Modulprüfung und Flyway-Prüfungen bestehen. Der Projektport wurde um Mitgliedschaft und unveränderlichen Projektschlüssel erweitert. Die Projektkonfiguration wird unter der gehaltenen Projektzeile gelesen; Konfigurationsfassungen bleiben unveränderlich.

`contracts/tickets.openapi.yaml` beschreibt die fünf tatsächlichen Operationen. Der vorhandene Vertragstest gleicht **Routen**, nicht sämtliche Status-/Schema-/Rechtekombinationen ab; seine grüne Ausführung schließt F1 nicht aus. Die Startprüfung erwartet jetzt elf erfolgreiche Migrationen einschließlich V0012 und die drei Tickettabellen. Ihre grüne Ausführung auf PostgreSQL 18.6 ist durch die unten gelesene CI belegt; kein eigener Linux-Lifecycle-Lauf unter Windows.

## Tatsächliche Prüfungen und Grenzen

- Eigener unveränderter Backendlauf `clean verify`, Java **25.0.4.1+1**, native lokale PostgreSQL **18.6**: **165 Tests, 0 Fehler, 0 übersprungen**.
- Eigene endgültige Zusatzproben: **11 Tests, 10 erfolgreich, 1 fehlgeschlagene Assertion**, 0 Ausführungsfehler, 0 übersprungen. Der eine Fehlschlag reproduziert F1 mit beiden Schreibaktionen und erfolgreicher Kontrollprobe nach zusätzlichem Leserecht.
- Erste lokale Versuche waren keine Produktnachweise: Ein leer von mir übergebenes Pflichtkennwort verhinderte zunächst den Anwendungsstart; eine Testergänzung hatte zunächst einen falschen Methodennamen, danach eine falsche Erwartung des Sitzungsfehlercodes. Diese Prüfkonfiguration/-proben wurden berichtigt. Die oben genannten endgültigen Ergebnisse enthalten diese Aufbaufehler nicht.
- Nach bytegleicher Wiederherstellung der einzigen erweiterten Testdatei: Originalklassen `TicketsApiTest` und `TicketsContractTest` mit `clean test` erneut ausgeführt: **11 Tests erfolgreich**, 0 Fehler, 0 übersprungen. Die eigene synthetische PostgreSQL-Instanz wurde nach Abgleich von PID und Programmpfad kontrolliert gestoppt. Einzelheiten im [Laufprotokoll](review-evidence/task-027-codex/results.txt).
- Gelesener exakter Backend-CI-Lauf von #112: [39410043140](https://github.com/1uedev/task-manager/actions/runs/39410043140), Job 118585282804, **165 Tests erfolgreich**, PostgreSQL **18.6**, `Lifecycle check passed`. Dies ist gelesene CI-Evidenz und kein eigener Docker-/Linux-Lauf.
- Wiederholbarer Prüfpatch, Anleitung und ausgewählte eigene/CI-Laufbelege: [review-evidence/task-027-codex](review-evidence/task-027-codex/README.md). Rohprotokolle mit privaten Rechnerpfaden oder Zugangsdaten werden nicht veröffentlicht.
- Abschlussprüfung der ergänzenden Dokumentation auf der Veröffentlichungsbasis am 21. Oktober: Repository-Validator und Aufgabenübersicht erfolgreich, **25 Python-Regressionstests erfolgreich**, `git diff --cached --check` ohne Befund. Keine neue Backendprüfung der späteren Produktänderungen.

## Bekannte Grenzen und Übergabe

Die ausdrücklich beschriebenen Grenzen von DEC-067 sind für diesen ersten internen Abschnitt nachvollziehbar: Pflichtprüfungen bei Anlage und für tatsächlich angesprochene Felder; entfernte Status bleiben am Ticket, entfernte Projektmitglieder können als alte Zuständigkeit stehen bleiben; keine Löschung, Kommentare, Anhänge, Beziehungen, Benachrichtigungen, Oberfläche, Tags oder Suche; keine erneute Prüfung beim Commit. Die Statusgrenze kann eine administrative Wiederaufnahme des alten Status in die Konfiguration erfordern, um einen Übergang zu ermöglichen. Das ist dokumentierter Funktionsumfang, kein neuer Sicherheitsbefund und keine vollständige V1-/Betriebsfreigabe.

Zum ursprünglichen Stand waren die zwei Projektbefunde aus [Review-PR #113](https://github.com/1uedev/task-manager/pull/113) offene Abhängigkeiten; auch ein veröffentlichter Ticketport fehlte damals. Beides ist durch die inzwischen integrierten Korrekturen beziehungsweise Erweiterungen überholt. Die am 21. Oktober weiterhin blockierten Aufgaben sind TASK-029 bis TASK-031; daraus wird keine Freigabe oder Übernahme abgeleitet.

**Technische Schlussfolgerung:** Die ursprüngliche Blockade F1 aus diesem eigenen Prüflauf ist im aktuellen main durch #114 korrigiert. Dieser Zusatzbericht liefert die historischen Laufnachweise und den verbleibenden Dokumentationshinweis H-C1; er beansprucht keine eigene vollständige Laufprüfung aller späteren Änderungen. Holgers bereits dokumentierte Abnahme bleibt bestehen. TODO.md und DONE.md bleiben unverändert.
