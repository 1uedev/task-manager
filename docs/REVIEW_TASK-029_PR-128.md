# Unabhängige Prüfung TASK-029 / PR #128

**Ergebnis:** TASK-029 ist auf dem geprüften Stand technisch noch nicht abnahmefähig. Eigene Versuche reproduzieren **F1/P2**, unterschiedliche Textaufbereitung von Anfrage und Index, sowie **F2/P2**, einen Deadlock zwischen Neuaufbau und Tagumbenennung. **F3/P3** betrifft die fehlende Nutzbarkeit der Feld-GIN-Indizes für das zusammengesetzte Mehrfeldprädikat. Die geprüften Projekt-/Feldgrenzen, Entzugsfälle und Transaktionsrücknahmen halten. Eine Rechteumgehung wurde in diesen Versuchen nicht festgestellt. Keine fachliche Abnahme durch diesen Bericht.

## Grundlage und Umfang

- Codex, getrennt vom Autor Claude; Sitzung `codex-20261021-search-review-01`, 21. Oktober 2026; eigener Klon und Branch `review/task-029-codex`.
- Ausgeführtes main: [`90f43be75a220eb78591a5673858cae1aff6e882`](https://github.com/1uedev/task-manager/commit/90f43be75a220eb78591a5673858cae1aff6e882), Integration von [PR #128](https://github.com/1uedev/task-manager/pull/128). PR-Kopf `3ad90081958c076007b0926a6e9f0bd8b7cec96b`; die gelesene CI gehört genau zu diesem Kopf. Main vor Veröffentlichung erneut abgeglichen, ohne zwischenzeitlichen Unterschied.
- Gelesen: AGENTS.md, CLAUDE.md, README.md, Aufgabenstand in TODO.md/DONE.md, docs/AI_WORKFLOW.md, DEC-040/DEC-069, relevante Anforderungen, Such-/Rechte-/Architekturverträge und docs/TECHNICAL_BASELINE.md sowie backend/README.md und `contracts/search.openapi.yaml`.
- TASK-029 steht auf BEREIT ZUR PRÜFUNG bei `claude-20261021-search-01`; die Voraussetzungen TASK-026/027/028/042 sind laut Repository abgenommen. Der ausdrückliche Reviewauftrag übernimmt keine Implementierungsaufgabe. TODO.md und DONE.md bleiben unverändert.
- Bezug: REQ-SRCH-001, REQ-TKT-004, REQ-TAG-001, REQ-PERF-003 sowie REQ-ACL-003 für die geprüfte Feldgrenze. Beauftragung und bereits bestätigter V1-Suchumfang werden von den technischen Ausgestaltungsvorschlägen in DEC-069 und noch nicht gemessenen Leistungszielen getrennt.
- Kein Produktcode verändert, auch nicht vorübergehend. Eine Testdatei wurde für eigene Proben ergänzt und danach bytegleich zurückgesetzt; ausschließlich Bericht und Nachweise werden geliefert.

## Befunde nach Schwere

### F1 — P2: Unterschiedliche Textaufbereitung macht gültige Inhalte unauffindbar

**Fundstellen:** `backend/src/main/java/taskmanager/search/internal/SearchText.java:19–46` und `backend/src/main/resources/db/migration/V0014__search_baseline.sql:13–14`, `:32–34`. Die Anfrage wird mit NFKC normalisiert und an jedem Zeichen außerhalb Buchstaben/Ziffern zerlegt. Die Indexvektoren entstehen dagegen aus unverändertem Quelltext durch den PostgreSQL-Parser `to_tsvector('simple', ...)`. Die beiden Aufbereitungen erzeugen nicht dieselben Suchwörter.

**Eigene echte HTTP-Versuche:**

1. Ein Projekt und ein Ticket mit gültigen, vollständig lesbaren Namen/Titeln wie `Ｆｕｌｌｗｉｄｔｈ123` anlegen. Jeweils genau diesen Text als `q` eingeben. Beide Suchwege antworten **200 mit `total: 0`**, obwohl die Daten vorhanden und autorisiert sind. Eine Suche nach der Ticketkennung liefert zum selben Zeitpunkt **einen Treffer**. Die NFKC-Anfrage sucht die ASCII-Fassung, während der Index diese Vereinheitlichung nicht übernimmt.
2. Ein lesbares Ticket mit dem Titel `support123@example.test` anlegen und genau diese Zeichenfolge als `q` suchen. Ergebnis ebenfalls **200 / `total: 0`**; Kennungssuche findet das Ticket. Die Anfrage fordert getrennte Präfixwörter an, während die Indexbildung die E-Mail-Struktur anders tokenisiert.

Die Zahlen sind synthetische Beispiele; die Proben erzeugen eigene Werte. Es fehlen weder Rechte noch Mitgliedschaft. Beide Versuche widersprechen dem beschriebenen Vertrag, Wörter aus Buchstaben und Ziffern als Präfixe zu suchen; beim Unicodefall ist sogar die identische sichtbare Eingabe erfolglos. Die Tests des Autors decken einfache Wörter und Sonderzeichen als Anfrage ab, aber nicht diese Übereinstimmung mit dem gespeicherten Text.

**Vorschlag:** Für Anfrage und durchsuchbare Inhalte eine gemeinsame Normalisierungs-/Tokenisierungsregel verwenden, Originalwerte für die Ausgabe erhalten und die betroffenen bestehenden Projektionen danach neu aufbauen. Unicodekompatibilitätszeichen und strukturierte Texte wie E-Mail-Adressen in die Regression aufnehmen, für Tickets und Projekte. Änderungen an gespeicherten Vektoren/Indizes über einen abgestimmten Migrationsweg behandeln. Eigene Proben: `reviewSameUnicodeInputMustFindItsStoredText`, `reviewDeclaredWordTokenizerMustAlsoApplyToIndexedEmailText` im [Prüfpatch](review-evidence/task-029/search-review-probes.patch).

### F2 — P2: Neuaufbau und Tagumbenennung sperren Projektionstabellen in Gegenrichtung

**Fundstellen:** `backend/src/main/java/taskmanager/search/internal/SearchRepository.java:45–51`, `:81–91`, `:106–108`; `SearchProjection.rebuild` und dessen Aufruf über `rebuild-search`.

`lockForRebuild` nimmt Tabellen exklusiv in der Reihenfolge `search_state`, `search_project`, **`search_ticket` vor `search_tag`**. Eine normale Tagumbenennung schreibt zuerst `search_tag` und aktualisiert danach die betroffenen `search_ticket`-Zeilen. Damit kann der Neuaufbau die Tickettabelle halten und auf die Tagtabelle warten, während die Umbenennung die Tagtabelle hält und auf die Tickettabelle wartet.

**Reproduzierter Ablauf:** Ein angelegtes Tag ist einem Ticket zugeordnet. Eine synthetische Transaktion hält `search_tag` kurz im SHARE-Modus, um die zeitliche Überlappung herzustellen. Eine echte HTTP-Tagumbenennung wartet dort bei ihrer Projektion. Anschließend startet der echte `SearchProjection.rebuild` und erreicht ebenfalls diese Schranke, nachdem er bereits die Tickettabelle exklusiv gesperrt hat. Nach Freigabe und Commit der synthetischen Schranke entsteht der Zyklus **zwischen den beiden unveränderten Produktwegen**:

| Vorgang im eigenen Lauf | Hält | Wartet auf |
| --- | --- | --- |
| HTTP-Tagumbenennung, PostgreSQL PID 14516 | Schreibsperre auf `search_tag` | RowShare-Zugang zu `search_ticket` für die Zeilensperren |
| Neuaufbau, PID 28724 | Exclusive-Zugang zu `search_ticket` | Exclusive-Zugang zu `search_tag` |

PostgreSQL protokolliert `deadlock detected` und beide Kanten. Die Umbenennung endet mit **HTTP 200**, der Neuaufbau mit **`PessimisticLockingFailureException`**. Die Assertion auf erfolgreich serialisierte Vorgänge schlägt fehl. Der Halter dient nur der reproduzierbaren Reihenfolge und ist zum eigentlichen Zyklus bereits beendet; er führt keinen erfundenen umgekehrt sperrenden Fachweg aus. Der Versuch ruft den Neuaufbaudienst direkt auf, nicht einen zusätzlich gestarteten CLI-Prozess; derselbe Dienst wird vom Befehl und vom Erstaufbau verwendet.

**Auswirkung/Vorschlag:** Der als neben laufenden Änderungen ausführbar beschriebene Neuaufbau kann an einer normalen Tagumbenennung abbrechen. Kein Datenverlust oder Teilcommit beobachtet; die Rückabwicklung funktioniert. Eine gemeinsame Sperrordnung für sämtliche Projektionsschreibwege und den Neuaufbau herstellen, gegebenenfalls eine davorliegende gemeinsame Serialisierung mit überprüften Gegenwegen. Regression für Tagumbenennung mit zugeordneten Tickets und parallelen Neuaufbau ergänzen; zusätzlich Feldschreiben, Zuordnung und Rückabwicklung erhalten. Eigene Probe: `reviewTagRenameAndRebuildMustNotTakeTablesInOppositeOrder`.

### F3 — P3: Die Feld-GIN-Indizes unterstützen das kombinierte Suchprädikat nicht

**Fundstellen:** `backend/src/main/java/taskmanager/search/internal/SearchService.java:132–141`, `backend/src/main/resources/db/migration/V0014__search_baseline.sql:41–43`.

Bei Leserecht auf mehrere Textfelder wird als Trefferprädikat beispielsweise `(title_tsv || description_tsv || tags_tsv) @@ to_tsquery(...)` gebildet. Die Migration legt jedoch nur getrennte GIN-Indizes auf den einzelnen Vektoren an. Es gibt keinen passenden Index auf dem zusammengesetzten Ausdruck.

**Eigener Planversuch:** In einer synthetischen Transaktion mit `SET LOCAL enable_seqscan=off` zwei gleichartige `EXPLAIN (COSTS OFF)`-Abfragen ausführen. Ein einzelner Titelvektor verwendet `Bitmap Index Scan on search_ticket_title`. Das tatsächlich zusammengesetzte Mehrfeldprädikat bleibt bei **`Seq Scan on search_ticket`, `Disabled: true`**. Diese Einstellung macht den Vergleich der Indexverwendbarkeit deutlich; sie misst keine Antwortzeit. Der zusätzliche Unternehmens-/Projektfilter kann andere Indizes nutzen, ersetzt aber keinen GIN-Zugang für den zusammengesetzten Textausdruck.

**Auswirkung/Vorschlag:** Gerade die übliche Sicht mit Titel, Beschreibung und Tags nutzt die dafür angelegten Feldindizes für diesen Textvergleich nicht. Das ist eine konkrete Lücke zwischen Abfrageform und Indexauslegung. Passende indexierbare Prädikate oder abgestimmte Ausdrucksindizes entwerfen und dabei die wortübergreifende AND-Semantik und den Ausschluss verborgener Felder erhalten. Den Planvergleich als Regression/Diagnose bewahren und in TASK-031 unter realistischen Datenmengen messen. Es wird weder eine gemessene SLA-Verletzung noch ein bereits bestätigter Zahlenwert behauptet. Eigene diagnostische Probe: `reviewGinPlansForSingleAndCombinedReadableVectors`.

## Erfolgreich geprüfte Schutz- und Konsistenzregeln

### Rechte vor Suche, Rang und Seite

`SearchService` ermittelt aktuelle Mitgliedsprojekte und liest je Projekt die Feldrechte über den echten Rechte-/Projektadapter. Projekte mit derselben Feldsicht bilden eine Teilabfrage. Nur erlaubte Vektoren gehen in Textvergleich und Rang ein; Filter auf verborgene Felder entfernen die betreffende Sicht aus den Teilabfragen. `total`, Sortierung und Seite entstehen aus diesen erlaubten Abfragen. Die Projektion enthält keine kopierten Rechte. Die Rechte-, Identitäts- und Projektprüfungen laufen in derselben Suchtransaktion; Projektzeilen bleiben geteilt bis zum Ende gesperrt.

Eigene HTTP-Proben mit unterschiedlicher Feldsicht in **zwei Projekten** belegen, dass eine sichtbare Titelgruppe und eine sichtbare Beschreibungsgruppe ihre Felder nicht über das UNION austauschen. Nach einer starken Änderung einer verborgenen Beschreibung bleiben **der exakte Inhalt der ersten Trefferseite und `total` unverändert**. Verborgene Wörter treffen nicht; ein Statusfilter ohne Statusleserecht liefert keine Treffer. Feldwerte fehlen in den Antworten, `tags` ist ohne Leserecht null. Die unveränderten Autorentests prüfen zusätzlich eine Rangänderung nur für den Vollberechtigten, sobald ein neueres Ticket als Gegenkontrolle existiert.

Bekannte fremde und unbekannte Projektkennungen ergeben denselben Fehler `project.unknown`; verborgene und unbekannte Tagkennungen denselben Fehler `tag.unknown`. Unternehmensweit vergebene Ticketaktionen ohne Mitgliedschaft finden keine Tickets. `projects.manage` erlaubt die Unternehmensprojektsuche, aber keinen Ticketzugriff. Das Recht zum Lesen der Such-Tags wird durch den aktuellen erlaubten Projektkatalog begrenzt.

### Gleichzeitiger Entzug

Die eigenen Hilfen verlangen die **konkrete Halter-PID in `pg_blocking_pids`** des wartenden Aufrufs. Es werden echte Datenbank-Wartebeziehungen geprüft.

| Eigener Versuch | Beobachtung |
| --- | --- |
| `ticket.read` unter Revisionssperre entzogen | Wartende echte HTTP-Suche sieht nach Commit keine Treffer: 200, `total: 0`, leere Liste; frische Suche ebenfalls leer. |
| Beschreibung unter Revisionssperre ausdrücklich verborgen | Wartende/frische Suche nach dem nur dort vorhandenen Wort leer; frische Kennungssuche findet das weiterhin lesbare Ticket. |
| Mitgliedschaft unter Projektzeilensperre entfernt | Wartende und nachfolgende Suche leer, kein alter Treffer oder alter Zähler. |
| Bereits erlaubte Suche vor Mitgliederentfernung | Echter Suchdienst mit aktuellem Konto-/Sitzungs-/Rechtekontext hält die Transaktion; echte HTTP-Entfernung wartet auf dessen PID. Nach Suchcommit Entfernen 200, danach leere Suche. Dieser Arm ist Dienstaufruf gegen HTTP, nicht zweimal HTTP. |

Entzugsfixtures verwenden synthetisches SQL mit derselben Revisions-/Projektzeile und den entsprechenden Versionsänderungen. Eine Rechteverwaltungs-HTTP-API besteht noch nicht. Die Gegenrichtung nutzt den wirklichen HTTP-Mitgliederweg. Nicht jeder Identitätsentzugsweg wurde neu aufgebaut; die bestehenden Identitätsprüfungen gehören zum erfolgreichen Gesamtlauf. Die bekannte Prüfung zum Sperrzeitpunkt wird nicht zu einer erneuten Prüfung unmittelbar beim Commit erweitert.

### Atomare Projektion und Neuaufbau

Projekt-, Ticket- und Tagmodule veröffentlichen ihre begrenzten Quellereignisse nach der Fachänderung. Die synchronen `@EventListener`-Methoden von `SearchProjection` verlangen eine bestehende Transaktion (`MANDATORY`). Such-SQL liest ausschließlich die sechs eigenen `search_*`-Tabellen. Neuaufbauquellen kommen über `ProjectAccess`, `TicketAccess` und `TagAccess`; deren interne SQL-Zugriffe bleiben bei ihren Besitzern. Modulprüfung und Migrationsprüfungen bestehen.

Eigene Trigger im synthetischen Testschema erzwingen ein spätes Scheitern der Projektion nach einem Ticketupdate beziehungsweise nach einer Tagzuordnung. Beide echten HTTP-Wege rollen **Fachdatensatz, Version, Feld-/Taghistorie, Zuordnung und Projektion** zurück; der alte Suchtreffer bleibt erhalten. Die absichtlich ausgelöste Datenbankausnahme gelangt nicht in die Antwort. Diese Fehlerinduktion belegt die Atomarität und ist kein zusätzlicher Produktbefund. Trigger/Funktion wurden danach entfernt. Der Autorentest prüft außerdem die direkte Verweigerung einer Projektion außerhalb einer Transaktion.

Zwei weitere eigene Warteversuche bestehen:

- Während ein abgeschlossener Neuaufbau seine Transaktion noch hält, wartet ein echtes HTTP-Ticketupdate an der Projektion. Eine parallele berechtigte Suche kann den alten committeten Stand lesen. Nach Neuaufbaucommit gelingt die Änderung; die neue Fassung ist suchbar, die alte nicht mehr.
- Ein bereits an der Projektion arbeitendes HTTP-Ticketupdate wartet an einer gehaltenen Projektionszeile; der gestartete Neuaufbau wartet nachweislich auf den Writer. Nach Freigabe committet erst das Update, dann der Neuaufbau. Der neue Suchtext bleibt erhalten.

Diese positiven Fälle ersetzen den in F2 gesondert nachgewiesenen Gegenzyklus bei Tagumbenennung nicht. Die Autorentests prüfen zudem Neuaufbau einer absichtlich beschädigten Projektion, Quellen-/Zuordnungszählungen und den einmaligen Aufbau über `search_state`.

## Tatsächlich ausgeführte Prüfungen

- Eigener unveränderter Backendlauf `clean verify` mit Java **25.0.4.1+1** und echter nativer PostgreSQL **18.6**: **193 Tests, 0 Fehler, 0 übersprungen**, Build erfolgreich.
- Eigene erste Zusatzgruppe: **10 Tests, 7 erfolgreich, 3 fehlgeschlagene Assertions**, 0 Ausführungsfehler/übersprungen. Zwei Assertions reproduzieren F1, eine F2. Keine dieser Assertion-Niederlagen ist ein Fehler beim Aufbau der Proben.
- Separater eigener Planversuch für F3: **1 Test erfolgreich**, mit den oben beschriebenen beobachtenden Assertions auf Einzel-GIN und kombinierten Seq Scan. Insgesamt 11 eigene Proben in zwei Ausführungen, 8 erfolgreich und 3 reproduzierende Fehlschläge.
- Die einzige erweiterte Testdatei wurde bytegleich wiederhergestellt. Danach die Originalklassen `SearchApiTest,SearchContractTest` mit `clean test` erneut ausgeführt: **10 Tests erfolgreich**, 0 Fehler, 0 übersprungen. Die eigene PostgreSQL-Instanz wurde nach Abgleich von PID und Programmpfad kontrolliert gestoppt; Einzelheiten im [Laufprotokoll](review-evidence/task-029/results.txt).
- Gelesene Backend-CI auf dem exakten PR-Kopf: [Lauf 39600717031](https://github.com/1uedev/task-manager/actions/runs/39600717031), Job 119225525614: **193 Tests erfolgreich**, PostgreSQL **18.6**, `Lifecycle check passed` einschließlich `rebuild-search`. Das ist gelesene CI-Evidenz; kein eigener Linux-Lifecycle-Lauf unter Windows.
- V0014 legt die sechs eigenen Projektionstabellen und Feld-/Filterindizes an. Startprüfung/Migrationserwartung enthalten V0014 und dreizehn erfolgreiche Migrationen. Der Vertragstest prüft die beiden Routen; seine grüne Ausführung beweist keine vollständige Schema- oder Eingabematrix.
- Eigene Prüfquellen und ausgewählte Laufbelege unter [review-evidence/task-029](review-evidence/task-029/README.md). Keine privaten Rechnerpfade, Kennwörter oder Sitzungstoken veröffentlicht.
- Abschlussprüfungen der Berichtslieferung: Repository-Validator und Aufgabenübersicht erfolgreich, **25 Python-Regressionstests erfolgreich**, `git diff --cached --check` ohne Befund. Kein Produkt-, Vertrags-, TODO- oder DONE-Unterschied zur Veröffentlichungsbasis.

## Grenzen und Übergabe

Die dokumentierten Grenzen sind für diesen ersten internen Abschnitt nachvollziehbar: keine Wiki-/Anforderungs-/Datei-/Portal-/Oberflächensuche, keine Vorschläge; Zählung aller erlaubten Treffer und Rechteprüfung je Mitgliedsprojekt können teuer werden. Der bestätigte spätere V1-Umfang bleibt bestehen. Noch vorgeschlagene Antwortzeitziele werden erst in TASK-031 qualifiziert. Diese Grenzen werden nicht als neue Befunde ausgegeben. F3 benennt konkret die Index-/Prädikatabweichung, ohne die spätere Messung vorwegzunehmen.

**Technische Schlussfolgerung:** F1 und F2 beheben und die entsprechenden HTTP-/Konkurrenzregressionen erneut prüfen; F3 bei Abfrage-/Indexauslegung bearbeiten und für die Leistungsmessung berücksichtigen. Die jetzige Umsetzung ist technisch noch nicht abnahmefähig. Holger behält die fachliche Abnahme. TASK-030/031 bleiben blockiert; dieser Review ändert weder TODO.md noch DONE.md und übernimmt keine Implementierungsaufgabe.
