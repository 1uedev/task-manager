# Unabhängige Prüfung von TASK-102 / PR #299

**Empfehlung: nachbessern.** Das Archivrecht und die Darstellung unsichtbarer Elternseiten weichen von den bestätigten Regeln ab. Außerdem können Treffer bei gleichzeitiger Veröffentlichung Inhalte aus verschiedenen Revisionen enthalten. Normalisierte Suchwörter können ohne Markierung erscheinen, und der Trefferlink kann eine andere Sprachfassung oder einen anderen Stand öffnen.

- **Sitzung:** `codex-20261111-search-wiki-01`, Prüfstand 11. November 2026.
- **Gegenstand:** [PR #299](https://github.com/1uedev/task-manager/pull/299), Branch `claude/project-thread-hulabb`, Head `8f81284e72fff490339aab057a474ce14e6f1021`, PR-Basis auf main `890a7c957eb261d9b2ac36d478d20362061ae9ce`. GitHub führte den PR zum Prüfzeitpunkt als offen. Geprüft wurde der konkrete PR-Head. Für die lokalen Läufe wurde der Quellstand aus einem lokalen Vorfahren und den 41 von GitHub ausgewiesenen Folgedateien rekonstruiert; die Blob-Hashes aller 41 Dateien stimmen mit dem PR-Head überein. Der PR selbst umfasst 34 Dateien.
- **Grundlage:** AGENTS.md, TASK-102 in TODO.md, DEC-132, der Abschnitt „Wiki in der Suche (TASK-102)“ in backend/README.md und die Suchregeln aus TASK-029.
- **Weitergabe:** Holger hat den Bericht am 11. November 2026 in den Arbeitsfaden von Paket A14 eingefügt; die umsetzende Sitzung hat ihn unverändert in dieses Dokument übernommen und nur die Tabellen als Markdown gesetzt.

## Befunde

### F1 · Archivsuche verlangt ein zusätzliches Recht

- **Datei und Zeile:** TODO.md:69, TODO.md:78, DECISIONS.md:222, `SearchService.java:477`, `WikiSearch.tsx:58`, `WikiSearchApiTest.java:365`.
- **Auswirkung:** Mitglieder mit `wiki.edit` ohne `wiki.publish` sehen den Archivfilter nicht. Der direkte Aufruf mit `archived=true` liefert für sie 403, sofern ihnen das Veröffentlichungsrecht in allen Projekten fehlt. Das verfehlt Regeln 4 und 5 sowie Abnahmekriterium 3. Auslegung (1) ist aus Sicht des Zugriffsschutzes nachvollziehbar. Das Wiki selbst reserviert Archivansicht und Direktzugriff auf archivierte Seiten für `wiki.publish`. Eine isolierte Lockerung der Suche würde Titel und Ausschnitte archivierter Seiten für Personen sichtbar machen, die diese Seiten im Wiki nicht öffnen können.
- **Vorschlag:** Das Archivrecht fachlich entscheiden und TODO, DEC, Wiki-Zugriff, Suche, Vertrag und UI darauf abstimmen. Bei Bestätigung von `wiki.publish` die Regeln und das Kriterium ausdrücklich anpassen. Bei Festhalten an `wiki.edit` die Zugriffspolitik für archivierte Inhalte gemeinsam klären. Die Rechtefälle jeweils mit und ohne Mitgliedschaft prüfen.

### F2 · Der Pfad unterschlägt unsichtbare Ebenen und zeigt im veröffentlichten Modus Entwurfstitel

- **Datei und Zeile:** TODO.md:71, `SearchService.java:544`, `WikiService.java:257`, `WikiService.java:619`, `WikiService.java:729`, `search.openapi.yaml:303`.
- **Auswirkung:** Auslegung (3) übernimmt die Brotkrumen des Wikis. Eine veröffentlichte Kindseite kann unter einem unveröffentlichten Elternknoten liegen. Für eine Person mit `wiki.read` verschwindet die Ebene. Regel 6 verlangt an dieser Stelle „Seite nicht verfügbar“. Das Überspringen schützt Titel und Kennung, verändert aber die dargestellte Hierarchie. Außerdem entscheidet allein das Entwurfsrecht über die im Pfad verwendeten Titel. Ein Mitglied mit Bearbeitungsrecht sieht bei einem Treffer im veröffentlichten Modus den neuen Entwurfstitel eines umbenannten Elternknotens. Das mischt die Stände innerhalb der Trefferanzeige.
- **Vorschlag:** Den Suchmodus an die Pfadauflösung übergeben. Im veröffentlichten Modus veröffentlichte Elterntitel und für unsichtbare Ebenen einen lokalisierten Platzhalter ohne UUID, Titel oder Link liefern. Im Entwurfsmodus die Entwurfssicht anwenden. Den Vertrag um den Platzhalter ergänzen und beide Fälle mit veröffentlichter Kindseite prüfen. Falls das Überspringen gewollt ist, Regel 6 ausdrücklich ändern.

### F3 · Der Ausschnitt normalisiert anders als der Suchindex

- **Datei und Zeile:** `SearchText.java:58`, `SearchSnippet.java:40`, `SearchSnippet.java:64`.
- **Auswirkung:** Der Index normalisiert den gesamten Text vor der Wortzerlegung. Der Ausschnitt und die Titelmarkierungen zerlegen den Originaltext zuerst. Bei `Café` endet das Originalwort für den regulären Ausdruck vor dem kombinierenden Akzent. Die Suche nach `café` findet die Seite, doch Titel und Ausschnitt liefern leere Markierungslisten. Eine separate Java-Probe mit den Klassen des PR hat beide Listen geprüft. Steht das Wort weiter hinten, kann der Ausschnitt zudem am Textanfang liegen.
- **Vorschlag:** Normalisierung und Wortgrenzen für Suche und Markierung angleichen und Positionen auf die UTF-16-Einheiten des Originaltexts zurückführen. Die Markierungen im vollständigen Text bestimmen und beim Ausschneiden umrechnen. Tests für zerlegte Akzente, NFKC-Kompatibilitätszeichen und Zeichen außerhalb der BMP ergänzen.

### F4 · Zähler, Trefferzeile und Ausschnitt können verschiedene Datenstände lesen

- **Datei und Zeile:** `SearchService.java:457`, `SearchService.java:517`, `SearchService.java:526`, `SearchService.java:538`, `SearchRepository.java:75`.
- **Auswirkung:** Bei der üblichen PostgreSQL-Isolation READ COMMITTED erhält jedes Statement einen eigenen Snapshot. Veröffentlicht jemand Revision 2 nach der Auswahl einer Trefferzeile für Revision 1, kann der nachgeladene `body` aus Revision 2 stammen. Der Nachlade-Query bindet Seite, Sprache und Stand, aber keine Revision. Ein Treffer kann deshalb den Titel und die Revisionsnummer von Revision 1 mit dem Ausschnitt von Revision 2 kombinieren. Beim Zurückziehen kann der Ausschnitt ganz entfallen. Ein Commit zwischen Zählung und Seitenauswahl kann ebenso `total` und Trefferliste auseinanderlaufen lassen. Die Sperren für Projektmitgliedschaft und Berechtigungen verhindern parallele Wiki-Änderungen durch andere berechtigte Mitglieder nicht. Befund aus dem Codepfad und der Abfragefolge.
- **Vorschlag:** Den Text für die ausgewählten Zeilen in derselben SQL-Anweisung aus demselben Snapshot holen, möglichst erst nach Auswahl und Begrenzung der Trefferseite. Zähler und Ergebnisse aus einem konsistenten Snapshot liefern. Eine Konkurrenzprüfung mit Veröffentlichung oder Zurückziehen zwischen den Leseschritten ergänzen.

### F5 · Der Trefferlink öffnet möglicherweise eine andere Fassung

- **Datei und Zeile:** `WikiSearch.tsx:111`, `route.ts:57`, `WikiPage.tsx:729`, `WikiPage.tsx:844`.
- **Auswirkung:** Ein englischer Treffer auf einer zweisprachigen Seite öffnet bei deutscher Oberfläche zunächst die deutsche Fassung. Wer Entwürfe sehen darf, kann nach Klick auf einen veröffentlichten Treffer den geänderten Entwurf angezeigt bekommen. Der Link übergibt nur Projekt- und Seitenkennung. Der gesuchte Begriff kann in der geöffneten Fassung fehlen.
- **Vorschlag:** Sprache und gesuchten Stand an die bestehende Wiki-Ansicht übergeben und dort als Einstieg verwenden. Das Wiki prüft den aktuellen Zugriff weiterhin selbst. Je einen Browsertest für einen englischen Treffer in deutscher Oberfläche und einen veröffentlichten Treffer mit abweichendem Entwurf ergänzen.

## Hinweise

### H1 · Sichtbare interne Linktexte und Länge des indexierten Texts

- **Datei und Zeile:** `WikiDocument.java:147`, `WikiDocument.java:176`, `SearchText.java:77`, backend/README.md:640, DECISIONS.md:222.
- **Auswirkung:** Auslegung (4) entfernt sämtliche internen Linkbeschriftungen aus Index und Ausschnitt, auch bei einem veröffentlichten und lesbaren Ziel. Das schützt Titel verborgener Ziele. Regel 1 umfasst sichtbare Linktexte, deshalb ist die Einschränkung fachlich zu bestätigen. Beim Weglassen können direkt angrenzende Textstücke zu einem Wort verschmelzen. Zu Auslegung (5) gilt die Grenze für normalisierte Suchwörter und ganze Wörter, nicht für den ursprünglichen Klartext. Eine Java-Probe mit 200.001 Ausrufezeichen und anschließendem `zielwort` indiziert `zielwort` weiterhin. Der Vektor ist begrenzt, der Klartext für Ausschnitte bleibt vollständig gespeichert.
- **Vorschlag:** Die Einschränkung interner Linktexte explizit entscheiden und beim Entfernen Wortgrenzen erhalten. Die 200.000 Bytes konsistent als Obergrenze der normalisierten UTF-8-Suchwörter dokumentieren oder, falls die ersten Bytes des Klartexts gemeint sind, vor dem Normalisieren begrenzen. Beide Grenzfälle als Tests erfassen.

### H2 · Abfrageaufwand bei vielen Seiten

- **Datei und Zeile:** `SearchService.java:501`, `:517`, `:526`, `:540`, `WikiService.java:623`, `WikiRepository.java:136`.
- **Auswirkung:** Zähler und Ergebnisse verwenden dieselbe Rechte- und Treffermenge. `count(distinct t.page_id)` zählt Seiten. `DISTINCT ON` wählt je Seite zunächst die bevorzugte passende Fassung, dann die passende Quellsprache. Erst danach sortiert die äußere Abfrage nach Rang, Titel und Seiten-ID. Das begrenzt die Antwortmenge, doch Auswahl und Rangfolge müssen alle passenden Sprachzeilen verarbeiten. Pro Treffer wird der Text separat geladen. Der Pfad liest zusätzlich den gesamten aktiven Seitenbaum je vertretenem Projekt.
- **Nachweis:** EXPLAIN (ANALYZE, BUFFERS) mit PostgreSQL 16.15, V0062, 50.000 synthetischen Seiten, 200.000 Textzeilen, 100 Projekten und 10 berechtigten Projekten. Nach VACUUM ANALYZE nutzte der seltene Präfix BitmapOr über beide GIN-Indizes. Beim häufigen Präfix lieferte der Körperindex 199.800 Kandidaten vor Stand- und Projektfilter. Die Zählung verarbeitete 9.648 Sprachzeilen für 4.824 Seiten. Ohne Suchtext las die Trefferabfrage die Texttabelle sequenziell. Bei `alpha beta` als Suche und je einem Wort in Titel und Text ergab die aktuelle Rangformel rund 3e-20. Beide Wörter nur im Text ergaben rund 0,0991. Die beiden Felder werden jeweils mit der gesamten UND-Abfrage bewertet, wodurch verteilte Treffer trotz Titelgewichtung sehr weit zurückfallen. Der Versuch umfasst SQL mit festen Parametern. Rechteprüfungen, Pfad und HTTP-Laufzeit fehlen in der Messung.
- **Vorschlag:** Beim Leistungsnachweis in TASK-104 seltene und häufige Präfixe, die Liste ohne Suchtext sowie große Einzelprojekte messen. Zähler, Seitenauswahl und Pfadauflösung getrennt betrachten. Gebündelte Textabfrage und gezielte Ahnenabfrage prüfen. Die gewünschte Rangfolge bei über Titel und Text verteilten Wörtern festlegen und testen.

### H3 · Browser-Testersatz deckt mehrere Serverregeln anders ab

- **Datei und Zeile:** `backend.mjs:917`, `:2472`, `:2485`, `:2516`, `wiki.test.mjs:758`.
- **Auswirkung:** Der Testersatz bildet die grundlegenden Rechte und die Fassungswahl ab. Er ignoriert die serverseitigen Wortgrenzen, prüft die Seitenparameter kaum und behandelt einen Suchtext nur aus Satzzeichen wie eine leere Suche. Der Server gibt für Satzzeichen ohne Suchwörter keine Treffer. Der Testersatz bewertet den Rang nicht, begrenzt den indexierten Text nicht und zeigt immer die ersten 240 UTF-16-Einheiten. Der Server bildet einen Ausschnitt um das erste passende Wort und schützt Surrogatpaare am Ausschnittrand. Der vorhandene Browsertest belegt die deutsche Grundsuche mit Pfad, Markierung und Entwurfsfilter. Er prüft weder Sprachwechsel per Trefferlink noch Archiv, Blättern, überholte Antworten oder den Wechsel zwischen Projekten mit unterschiedlichen Rechten.
- **Vorschlag:** Dieselben konkreten Antwortfälle gegen Server und Testersatz prüfen. Die Browserfälle gezielt um Sprache, Rechte, zweite Seite, verspätete Antwort und einen Treffer außerhalb der ersten 240 Zeichen ergänzen.

### H4 · Filter, Wiederholen und Tastaturfokus

- **Datei und Zeile:** `SearchPage.tsx:35`, `WikiSearch.tsx:47`, `:56`, `:60`, `:102`, `:113`, `:132`.
- **Auswirkung:** Die Bereiche sind eine per Tastatur bedienbare Radiogruppe. Formularfelder sind beschriftet, überholte Suchantworten werden verworfen und Texte werden als React-Text mit `mark` ausgegeben. Deutsch und Englisch sind vorhanden. Entwurfs- und Archivfilter erscheinen nach Rechten über alle Projekte, auch wenn das aktuell gewählte Projekt nur Leserechte bietet. Fehler beim Laden der Wiki-Projekte bleiben unkommentiert. Scheitert „Weiter“, verwendet „Wiederholen“ den Offset der letzten erfolgreichen Seite. Beim Laden werden Ergebnisliste und fokussierte Seitentaste ausgehängt. Die Live-Region wird erst zusammen mit den Ergebnissen erzeugt. Der Treffertext im Titel trägt kein `lang` für eine anderssprachige Fassung.
- **Vorschlag:** Filter am gewählten Projekt ausrichten, unzulässige Filter beim Projektwechsel zurücksetzen und Ladefehler mit Wiederholen sichtbar machen. Den zuletzt angefragten Offset für den Wiederholungsversuch speichern. Eine dauerhafte Statusregion und eine Fokusstrategie beim Blättern verwenden. `lang` am Treffertitel und an der Vorschau setzen, die Sprachbezeichnung in der Oberflächensprache belassen.

### H5 · OpenAPI bildet die Route ab, dokumentiert die neuen Fehlergründe unvollständig

- **Datei und Zeile:** `search.openapi.yaml:139`, `:218`, `SearchController.java:116`, `SearchContractTest.java:66`.
- **Auswirkung:** Der Vertrag 0.3.0 enthält `GET /search/wiki` mit Basis `/api/v1`, Filtern, Statuscodes, `{total, items}` sowie den Feldern für Veröffentlichungszeit, Stand, Pfad und UTF-16-Positionen. Die allgemeine Fehlerbeschreibung listet `language.invalid`, `preferred.invalid`, `drafts.invalid` und `archived.invalid` nicht. Die Rechtebedingungen von `drafts` und `archived` sind bei den Parametern knapp beschrieben. Der Routentest belegt die Zuordnung der Routen. Die Form der Antworten und die Fehlergründe sind separat zu prüfen.
- **Vorschlag:** Neue Fehlergründe, Rechte und Standardwerte in den Wiki-Parametern dokumentieren. Eine repräsentative 200-Antwort und die Wiki-spezifischen Fehlerfälle gegen den Vertrag prüfen.

### H6 · Projektionsabdeckung und Betriebsnachweis

- **Datei und Zeile:** `WikiService.java:367`, `:496`, `:576`, `SearchProjection.java:41`, `SearchRepository.java:180`, `WikiSearchApiTest.java:485`.
- **Auswirkung:** Anlage, Sprachfassung, Speichern, Veröffentlichen, Zurückziehen, Übernehmen einer alten Revision, Archivieren und Wiederherstellen veröffentlichen das Ereignis. Verschieben und Bildupload veröffentlichen es nicht. Der Pfad wird live gelesen und Bilder sind kein Suchinhalt. Die Ausnahmen lassen die Suchprojektion fachlich konsistent. Der synchrone Listener setzt eine vorhandene Transaktion voraus und nimmt die gemeinsame Schreibsperre vor Änderungen der Projektion. Der Neuaufbau sperrt `search_state` zuerst exklusiv, liest die Wiki-Quellen ohne Wiki-Schreibsperre und schreibt den Aufbauvermerk mit. Der vorhandene Vergleich vor und nach dem Neuaufbau prüft einen repräsentativen Treffer. Er prüft keinen gleichzeitigen Wiederaufbau und keine vollständige Folge aus Übernehmen, Zurückziehen, Archivieren und Wiederherstellen.
- **Vorschlag:** Die zwei ausgenommenen Schreibvorgänge explizit dokumentieren. Rücknahme, Übernahme einer alten Revision und Wiederherstellung jeweils über die Suche prüfen. Einen gleichzeitigen Schreibvorgang beim Neuaufbau und das Starten mit Bestandsdaten aus V0061 abdecken.

## Abdeckung von Regeln und Abnahmekriterien

| Regel | Prüfergebnis |
| --- | --- |
| 1 · Textgewinnung | `WikiDocument.plainText` arbeitet aus der geprüften gespeicherten Dokumentstruktur. Textblöcke und externe Linktexte gehen ein. Bilder samt Alternativtext sowie Linkziele und gespeicherte IDs gehen nicht ein. Auslegung (4) zu internen Linktexten siehe H1. |
| 2 · Veröffentlichungen und Entwürfe | Die Trefferabfrage bindet genau einen Stand. Für Entwürfe prüft sie `wiki.edit` oder `wiki.publish`. Auslegung (2) beschränkt den Entwurfsmodus auf diese Projekte und liefert ohne entsprechendes Recht in sämtlichen Mitgliedsprojekten 403. Pfadanzeige und Nebenläufigkeit siehe F2 und F4. |
| 3 · Sprache | Die Fassung muss alle Suchwörter selbst erfüllen. Bei zwei Treffern bevorzugt die Abfrage die Oberflächensprache, Sprachfilter und ein Treffer pro Seite sind umgesetzt. Das Öffnen der Fassung ist F5. |
| 4 · Archiv | Archivierte Seiten sind standardmäßig ausgeschlossen. Auslegung (1) setzt `wiki.publish` voraus. Siehe F1. |
| 5 · Rechte | Das Projekt muss Mitgliedsprojekt sein. Für veröffentlichte Inhalte gelten wie im Wiki die Alternativen `wiki.read`, `wiki.edit`, `wiki.publish`. Die drei Aktionen werden pro Projekt mit aktuellem Kontext geprüft, bevor die SQL-Treffermenge aufgebaut wird. Zähler, Titel und Ausschnitte beziehen sich auf dieselbe berechtigte Projektmenge. Die Pfadauflösung prüft Projektzugriff erneut. Für gültige Suchanfragen eines Portalkontos erzwingt `SearchService.internal` 403. Ein dedizierter Portaltest für die neue Route fehlt. |
| 6 · Anzeige und Ausschnitt | Titel, Projekt, Pfad, Sprache, Stand, Veröffentlichung und Klartext mit UTF-16-Positionen sind vorgesehen. Auslegung (3), Markierungen und Konsistenz siehe F2 bis F4. |
| 7 · Suchregeln | NFKC, Präfix je Wort, alle Wörter, keine Suchsyntax und dieselben Grenzen für Text und Paging wie in TASK-029. Gebundene Parameter und `search.busy` mit Retry-After bei Sperrfehlern. Ohne Suchtext listet Auslegung (6) berechtigte Seiten nach Titel. Auslegung (5) zur Byte-Grenze siehe H1. |
| 8 · Projektion | Ereignis in der Wiki-Transaktion, synchroner Listener mit `enterWrite`, zentraler Neuaufbau und V0062 mit Löschen des Aufbauvermerks. Siehe H6 zur Testabdeckung. |

| Abnahmekriterium | Prüfergebnis |
| --- | --- |
| 1 · Entwurfswort | Erfüllt und in `WikiSearchApiTest` abgedeckt. |
| 2 · Englische Fassung und Filter | Backend erfüllt und in `WikiSearchApiTest` abgedeckt. Navigation siehe F5. |
| 3 · Mitgliedschaft, Rechte und Archiv | Ausschluss unberechtigter Projekte und Zähler geprüft. Archivrecht weicht ab. Siehe F1. |
| 4 · Sichtbarkeit nach Veröffentlichung und Neuaufbau | Die Projektion ist synchron. Der Backendtest prüft Veröffentlichung und Gleichheit eines repräsentativen Treffers nach Neuaufbau. Gleichzeitige Änderungen siehe F4 und H6. |
| 5 · Migration, Vertrag und Tests | V0062, Lebenszyklusprüfung, OpenAPI 0.3.0 und Tests sind vorhanden. Umfang der Ausführung siehe unten. |

**Migration und Umfang.** V0062 legt beide Suchtabellen samt Projekt- und GIN-Indizes an und löscht den Aufbauvermerk. Die Migration liest keine Wiki-Fachtabellen. `lifecycle-check.sh:89` und `:98` prüfen Historie und Tabellen, `DatabaseFoundationTest.java:36` und `:49` ebenso. `ApplicationLifecycleTest.java:34` erwartet 59 angewendete Migrationen und prüft beim Neustart unveränderte Einträge. Die 34 PR-Dateien betreffen TASK-102. Anforderungen, eine Übersicht „Alle“ und Schnellsuche sind im PR nicht hinzugekommen.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Lokal erfolgreich mit Python 3.12. |
| `python3 scripts/task_board.py --check` | Lokal erfolgreich. |
| `python3 -m unittest discover -s tests -v` | Lokal 42 Tests. Der UTF-8-Wiederholungslauf hatte 1 Fehlerassertion und 9 Fehler aus der blockierten lokalen Socket-Verbindung (WinError 10013) sowie 1 plattformbedingten Skip. Die zusätzlichen Kodierungsfehler des ersten Windows-Laufs entfielen im Wiederholungslauf. |
| `./mvnw verify` mit PostgreSQL 16 | Windows-Wrapper `mvnw.cmd`, Java 25, PostgreSQL 16.15. Haupt- und Testquellen kompiliert. Der lokale Integrationslauf scheitert beim JDBC-Verbindungsaufbau mit `SocketException: Permission denied: getsockopt`. Separater Maven-Lauf für `SearchSnippetTest` und `WikiPlainTextTest`: alle 5 Tests bestanden. PostgreSQL war für die Planprüfung im Einzelbenutzermodus nutzbar. |
| `npm ci` | Mit Node 24.22.0 und npm 11.19.1 im zweiten Anlauf aus dem lokalen Cache mit `--offline --ignore-scripts --maxsockets=1` erfolgreich, 111 Pakete installiert. Der erste Anlauf scheiterte beim Anlegen von `node_modules/@types` mit EPERM. |
| `npm run verify` | Lokal erfolgreich. Typen generiert, Typprüfung erfolgreich, 100 Unit-Tests bestanden, Produktions-Build erstellt. |
| `npm run test:browser` | Lokal ausgeführt und mit Exitcode 1 beendet. Der gezielte Wiki-Test scheiterte in Chrome beim Laden von localhost mit `ERR_NETWORK_ACCESS_DENIED`, vor den Assertions zur Suche. |
| PostgreSQL-Abfragepläne und Java-Probe | Lokal ausgeführt. Ergebnisse in F3, H1 und H2. |
| `git diff --check` | Lokal für die verfolgten Dateien gegen den lokalen Vorfahren erfolgreich. |

**CI des geprüften PR-Heads.** Repository checks, Backend, Frontend, Image und Technical qualification erfolgreich. Das Backend meldet erfolgreiche Integrationstests auf dem festgelegten PostgreSQL 18.6 und einen erfolgreichen Start/Stopp/Neustart des Pakets über `lifecycle-check.sh`. Das Frontend meldet erfolgreiche Browserprüfungen. Diese Läufe ergänzen die lokalen Ergebnisse.
