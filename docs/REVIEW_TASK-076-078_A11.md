# Unabhängige Prüfung TASK-076, TASK-078 und TASK-077 · Paket A11

**Prüfsitzung:** `codex-20261102-portal-review-02`, unabhängige zweite KI nach DEC-040.

**Stand:** 2. November 2026, main, Commit `07968e79dd1af8d628aa55f48684104afdf907cc`. Geprüft wurden die bereits gemergten Ergebnisse [TASK-076, PR #234](https://github.com/1uedev/task-manager/pull/234), [TASK-078, PR #236](https://github.com/1uedev/task-manager/pull/236) und [TASK-077, PR #237](https://github.com/1uedev/task-manager/pull/237).

Maßstab: AGENTS.md, die drei Aufgabeneinträge in TODO.md, DEC-106 bis DEC-108, die zugehörigen Abschnitte in backend/README.md, Organisationssicht und Fall 8 in docs/PERMISSIONS.md, T04/T10/T18/T19 in docs/TAGS_SEARCH.md und docs/REVIEW_TASK-075_PR-233.md. Im Prüfcheckout wurde ausschließlich dieser Bericht ergänzt. Produktcode, Tests und Aufgabenstatus wurden nicht geändert; eine Abnahme erfolgt hier nicht.

**Ergebnis:** ein Befund P1 und vier Befunde P2. Die vorhandenen vollständigen Prüfketten sind grün; die Fehler wurden durch zusätzliche, getrennte Nachstellungen gefunden.

## Befunde

### F1 · TASK-076 · P1 · Kundenwechsel gibt historische Korrespondenz der bisherigen Organisation frei

**Datei/Zeile:** `backend/src/main/java/taskmanager/helpdesk/internal/PortalConversation.java:223`, `:234`, `:240`; `backend/src/main/java/taskmanager/files/internal/AttachmentService.java:318`; Datenmodell `backend/src/main/resources/db/migration/V0038__helpdesk_portal_conversation.sql:3`.

Die eingegangenen Mails werden nach ihrer Kundenorganisation gefiltert. Gesendete Antworten und Portalbeiträge werden dagegen allein nach dem Ticket ausgewählt. Beiträge besitzen keine gespeicherte Zielorganisation. Auch ein als `customer` gespeicherter Anhang folgt ohne weitere Zielgruppenprüfung dem aktuellen Ticketkunden. Das genügt nicht der Forderung aus docs/PERMISSIONS.md:72 nach eindeutigen Zielgruppen und Neubewertung beim Organisationswechsel.

**Nachstellung:** Nordwerk und Südtechnik haben Portalkonten im selben freigegebenen Projekt. Auf einer Nordwerk-Anfrage einen Kundenbeitrag, eine Datei, einen Supportbeitrag, eine eingegangene Nordwerk-Mail und eine gesendete Mailantwort an den Nordwerk-Kontakt hinterlegen. Dann über `PUT /api/v1/projects/{projectId}/tickets/{number}/helpdesk/customer` den Ticketkunden auf Südtechnik ändern. Mit beiden bestehenden Portalsitzungen das Gespräch lesen und mit Südtechnik den bekannten Dateilink öffnen.

**Beobachtet am echten Backend:** Vor dem Wechsel erhält Südtechnik 404; danach 200 mit dem alten Nordwerk-Kundenbeitrag, dem alten Supportbeitrag und der an Nordwerk gesendeten Antwort. Die alte Datei ist ebenfalls mit 200 herunterladbar. Nur die eingegangene Nordwerk-Mail wird ausgeblendet. Nordwerk verliert den Zugriff mit 404. Es handelt sich somit um eine ungewollte Weitergabe historischer Inhalte an den neuen Kunden, obwohl der Entzug für den alten Kunden funktioniert. Beiträge und Datei wurden über die Portal-API angelegt; Mail und gesendete Antwort wurden als synthetische Testdaten ergänzt.

**Vorschlag:** Zielorganisation bei Beiträgen und veröffentlichten Antworten unveränderlich festhalten und beim Lesen berücksichtigen; entsprechende Regel auch für Dateien und Aktivitätsdaten anwenden. Beim Kundenwechsel bisherige Veröffentlichungen standardmäßig nicht für die neue Organisation freigeben. Ein ausdrücklich gewünschter Transfer muss eine bewusste Freigabe mit nachvollziehbarem Umfang sein. Bestehende Daten konservativ behandeln und den Kundenwechsel als Regressionstest aufnehmen.

### F2 · TASK-076 · P2 · Gelesen-Markierung quittiert eine noch nicht angezeigte Antwort

**Datei/Zeile:** `frontend/src/pages/PortalPage.tsx:347`; `backend/src/main/java/taskmanager/helpdesk/internal/PortalConversation.java:253`; `PortalRepository.java:85`; Vertrag `contracts/helpdesk.openapi.yaml:793`.

Nach dem Laden des Gesprächs sendet die Oberfläche einen separaten Aufruf an `/seen`. Dieser speichert die aktuelle Serverzeit, ohne Bezug zu den tatsächlich gelieferten Einträgen. Eine zwischen beiden Aufrufen eingetroffene Antwort gilt dadurch ebenfalls als gelesen. Die Ansicht lädt diese Antwort nicht automatisch nach.

**Nachstellung:** Gespräch per GET laden und dessen Ergebnis anzeigen. Danach eine neue Supportantwort veröffentlichen. Erst anschließend den vom geöffneten Bildschirm vorgesehenen `PUT .../seen` ausführen und `/activity` lesen.

**Beobachtet am echten Backend:** Die neue Antwort fehlt im zuvor geladenen Gespräch; `/activity` meldet trotzdem `unread: false`. Das normale Browsertestszenario mit einer bereits vor dem Öffnen vorhandenen Antwort deckt diese Reihenfolge nicht ab.

**Vorschlag:** Mit dem Gespräch einen serverseitigen Lesestand liefern und genau diesen Stand quittieren, etwa über einen Cursor oder ein serverseitig erzeugtes Zeit-/Versionsmerkmal. Spätere Einträge müssen ungelesen bleiben. Vertrag und Client gemeinsam ergänzen; fehlgeschlagene Quittierungen nicht still als erledigt behandeln.

### F3 · TASK-078 · P2 · Verspätete Folgeseite wird unter einem neuen Filter angezeigt

**Datei/Zeile:** `frontend/src/pages/PortalPage.tsx:142`, `:162`, `:166`.

Der Filterwechsel setzt vorhandene Folgeseiten zurück. Ein schon laufender `loadMore`-Aufruf prüft beim Eintreffen seiner Antwort aber nicht mehr, zu welchem Filter sie gehört. Er hängt seine alte Seite an das aktuelle Ergebnis an.

**Nachstellung im Browser mit dem gebauten Frontend:** 51 offene Anfragen bereitstellen. „Weitere Anfragen“ anklicken und die Antwort auf die Anfrage mit `before` zurückhalten. Währenddessen den Filter „Offen oder erledigt“ auf „Erledigt“ stellen; die neue erste Seite ist leer. Danach die zurückgehaltene Antwort freigeben.

**Beobachtet:** Unter dem gewählten Wert `done` erscheint wieder die alte offene Anfrage `PLAT-1`, Status „Neu“. Damit kann auch eine Folgeseite einer früheren Text-, Status- oder Tagabfrage die neue Liste verfälschen. Es wurde kein Zugriff über die Organisationsgrenze festgestellt.

**Vorschlag:** Jede Abfragefolge an eine Generation aus Projekt und sämtlichen Filterwerten binden. Bei deren Wechsel laufende Folgeseiten abbrechen oder ihre Ergebnisse verwerfen; auch Lade- und Fehlerzustand an diese Generation binden. Einen Test mit gezielt verzögerter zweiter Seite ergänzen.

### F4 · TASK-077 · P2 · Neue Namensräume machen die Katalogpagination unvollständig

**Datei/Zeile:** `backend/src/main/java/taskmanager/tags/internal/TagRepository.java:68`; `backend/src/main/resources/db/migration/V0040__tags_portal.sql:15`; Client `frontend/src/pages/TagParts.tsx:175`.

V0040 erlaubt denselben normalisierten Namen in verschiedenen Organisationszielgruppen eines Katalogs. Der Katalog verwendet weiterhin ausschließlich den Namen als Cursor: `normalized > after`, sortiert ebenfalls nur nach diesem Namen. Endet eine Seite innerhalb einer Gruppe gleichnamiger Tags, überspringt die nächste Seite deren übrige Tags vollständig.

**Nachstellung am echten Backend:** 51 gleichnamige Projekt-Tags mit jeweils anderer Organisationskennung als synthetische, nach dem Schema gültige Daten anlegen. Intern `GET /api/v1/projects/{projectId}/catalog/tags?q=A11PageSame&limit=50` lesen, anschließend dieselbe Abfrage mit `after=A11PageSame`.

**Beobachtet:** 51 gespeicherte Tags, erste Seite 50, nächste Seite 0. Mindestens ein Tag ist über diesen Katalogseitenlauf nicht erreichbar. Die 51 Zielgruppen wurden für den Versuch direkt in der Testdatenbank vorbereitet; es wurden dafür keine 51 Konten angelegt. Bei der Standardseitengröße tritt derselbe Fehler entsprechend an deren Grenze auf.

**Vorschlag:** Eine eindeutige Ordnung und einen dazu passenden Cursor verwenden, zum Beispiel `(normalized, id)`. Server, Vertrag und Katalogoberfläche gemeinsam anpassen und eine Seitengrenze innerhalb identischer Namen testen. Die neue Namensregel ist richtig; ihr bisheriger Cursor ist es nicht mehr.

### F5 · TASK-077 · P2 · Portalfilter erreicht nur die ersten 20 sichtbaren Tags

**Datei/Zeile:** `frontend/src/pages/PortalPage.tsx:121`, `:125`; begrenzte Vorschläge in `backend/src/main/java/taskmanager/tags/internal/TagRepository.java:148`.

Der Tagfilter lädt einmal Vorschläge mit leerem Suchtext und macht daraus eine feste Auswahlliste. Vorschläge sind auf höchstens 20 begrenzt. Der Filter bietet weder Texteingabe noch weitere Seiten an; zusätzliche sichtbare Tags bleiben unerreichbar.

**Nachstellung im Browser:** Im einzigen Portalprojekt 21 sichtbare Tags „A11 Tag 01“ bis „A11 Tag 21“ anlegen und den letzten einer eigenen Anfrage zuordnen. Den Tagfilter öffnen. Zum Vergleich dieselbe Anfrage direkt mit `GET .../tickets?tag={id-des-letzten-Tags}` filtern.

**Beobachtet:** 20 auswählbare Tags; „A11 Tag 21“ fehlt. Der direkte Listenfilter antwortet 200 mit genau einer Anfrage. Die Beschränkung betrifft die Oberfläche, nicht die grundsätzliche Berechtigung oder den Listenfilter des Servers.

**Vorschlag:** Den Filter mit einer Suche über die vorhandene Vorschlags-API ausstatten oder eine vollständig erreichbare Auswahl vorsehen. Der bereits gewählte Tag muss auch bei neuen Vorschlägen sichtbar bleiben. Mehr als 20 sichtbare Tags im Browsertest abdecken.

## Hinweise

### H1 · TASK-077 · Bestätigte 30er-Grenze bleibt ein Hinweis auf verborgene Zuordnungen

**Datei/Zeile:** `backend/src/main/java/taskmanager/tags/internal/PortalTags.java:127`; DEC-108, Punkt 5; TASK-077, fachliche Regel 1 in TODO.md.

Bei 30 internen Tags liefert die Portal-Tagliste null Einträge, das Hinzufügen eines neuen Namens aber 409 `tag.limit_reached`. Genau das wurde am echten Backend nachgestellt. Es bleibt dabei kein neu angelegter Tag zurück. Namen oder Identitäten interner Tags wurden nicht ausgegeben; die ausgeschöpfte verborgene Kapazität ist dennoch erkennbar.

Das entspricht ausdrücklich der bestätigten Entscheidung, alle Tags zu zählen, und wird hier deshalb nicht als eigenmächtiger Änderungsauftrag oder Implementierungsbefund behandelt. Die pauschale Zusage „ohne Hinweis auf interne“ ist damit aber zu weit. Diese bekannte Ausnahme muss bei der Abnahme ausdrücklich berücksichtigt und in den Abnahmefällen präzisiert werden.

### H2 · TASK-077 · Nebenläufigkeitstest prüft keinen laufenden Sichtbarkeitswechsel

**Datei/Zeile:** `backend/src/test/java/taskmanager/PortalTagsApiTest.java:551`.

Der Kommentar kündigt einen gleichzeitigen Sichtbarkeitswechsel an. Tatsächlich setzen die beiden Aufgaben sichtbare beziehungsweise interne Tags. Nach dem parallelen Teil werden die Zuordnungen erneut hergestellt; ein vorübergehendes falsches Ergebnis wäre dadurch nicht immer erkennbar. Archivieren und Zusammenführen während eines Portalzugriffs werden ebenfalls nicht so getestet.

Zusätzlich nachgestellt wurde deshalb eine gehaltene Änderung auf `internal`, während Portal-Zuordnung beziehungsweise -Entfernung nachweislich an dieser Tagzeile warteten. Nach dem Commit antworteten beide Aufrufe 404; beim Entfernen blieb die nun interne Zuordnung bestehen. Diese beiden Versuche bestanden. Sie sollten als dauerhafte Tests aufgenommen und um Archivieren/Zusammenführen ergänzt werden.

### H3 · TASK-077 · Testersatz behält einen Tag nach abgelehnter Anlage

**Datei/Zeile:** `frontend/tests/browser/backend.mjs:421`, `:425`.

Der Testersatz fügt den neuen Tag vor der Kapazitätsprüfung in `state.tags` ein. Beim anschließenden 409 wird diese Änderung nicht zurückgenommen. Im eigenen Browserversuch blieb ein solcher Tag zurück; derselbe Versuch am echten Backend ließ keinen Tag zurück. Zudem ignoriert die Vorschlagsattrappe den angefragten `limit`-Wert und begrenzt fest auf 20 (`backend.mjs:396`).

Die aktuellen grünen Browsertests prüfen diese Fehlerpfade nicht. Den Testersatz mit denselben Zustandsänderungen und Grenzen wie den Server ausführen lassen und ausdrücklich prüfen, dass eine abgelehnte Anlage nichts hinterlässt. Er ist kein Ersatz für Transaktions- oder Rechteprüfungen am Backend.

### H4 · TASK-076/TASK-077 · Seitengröße begrenzt die Arbeit am Backend noch nicht

**Datei/Zeile:** `backend/src/main/java/taskmanager/tickets/internal/TicketService.java:143`, `:149`.

Auch eine Seite mit 50 Treffern sperrt zunächst die Kundenzuordnungen sämtlicher eigener Anfragen im Projekt. Danach werden mit `Integer.MAX_VALUE` die Kandidaten geladen und erst in der Anwendung gefiltert und begrenzt. Tagfilter und Aktivitätsabfrage ändern diese grundsätzliche Skalierung nicht. Bei vielen Anfragen können somit kleine Seiten umfangreiche Arbeit und längere Sperrzeiten verursachen.

Diese Grenze ist bereits dokumentiert. Im Review wurde kein Lastgrenzwert gemessen; daraus wird kein nachgewiesener Ausfall abgeleitet. Vor einer größeren Portalnutzung mit realistischen synthetischen Mengen messen und die Auswahl möglichst in die Datenbank verlagern.

## Rechte, Sperren, Namensräume und Verträge

- **Sofortiger Entzug:** Bei bestehenden Sitzungen verweigerten die zusätzlich geprüften Gesprächs-, Datei- und Tagpfade nach einer Deaktivierung den Zugriff mit 401. Nach Entzug von `ticket.read` antworteten Detail, Gespräch, Dateiliste, bekannter Download, Tags, Vorschläge und die geprüften Schreibaufrufe mit 404. Der Rechteentzug wurde im synthetischen Versuch atomar in den Rechtegruppendaten samt Erhöhung der Rechteversion vorgenommen. Nach Projektentzug über die echte Helpdesk-API ergab dieselbe Zugriffsauswahl ebenfalls 404. Die Kundenzuordnung selbst wurde wie in F1 beschrieben gesondert geprüft.
- **Prüfung und Schreiben:** Die geprüften Schreibdienste arbeiten innerhalb einer Transaktion mit der gemeinsamen Rechte-/Identitätsprüfung und gesperrtem Projekt sowie passender Ticketorganisation. Uploads prüfen nach dem vorgelagerten Dateitransfer erneut. Portalbeiträge sperren vor der Dateizuordnung Ticket, Beitragsbereich und Dateimetadaten; fremde, interne, entfernte, bereits verwendete oder von einem anderen Konto hochgeladene Dateien werden nicht als eigene Beitragsdateien angenommen.
- **Sperrfolge:** `Permissions.current` nimmt die Rechtesperre vor Identität und Projektliste. PortalAccounts und Helpdesk-Entzug sortieren Projekte nach UUID-Text und damit wie die Datenbank. Portal-Tagänderungen halten den Tag vor der Ticket-/Zuordnungssperre; interne Tagänderungen können nicht während dieser gehaltenen Tagsperre die Sichtbarkeit umstellen. Für diese geprüften Kombinationen wurde keine neue Verklemmung nachgewiesen. Das ist kein Beweis für sämtliche möglichen Interleavings.
- **Sperrfehler:** Die Webadapter behandeln `55P03`, `40P01` und `40001` als 503 mit passendem `*.busy` und `Retry-After`, nach Abbruch der Transaktion. Die vollständige Testsuite und die beschriebenen gehaltenen Sperren liefen; nicht für jeden neuen Portalpfad wurde jeder dieser SQL-Fehler eigens erzwungen. Insbesondere fehlt ein vollständiger dynamischer Nachweis aller Kombinationen von Zusammenführen, Archivieren und Helpdesk-Entzug.
- **Namen:** Index und `lockByName`/`nameTaken` beziehen die Organisation in denselben Katalognamensraum ein. `lockVisibleByName` sucht nur freigegebene oder eigene kundensichtbare Tags. Eigener Portalname trotz gleichnamigem internen Tag: 200 mit eigener Kennung; gleicher Name durch die andere Organisation: ebenfalls 200 mit anderer Kennung. Freigabe des eigenen Tags in einen dort belegten allgemeinen Namensraum: 409. Das war ein interner Verwaltungsaufruf, kein Namensorakel im Portal. Umbenennen und Aufheben der Bindung prüfen den Zielnamensraum; gesperrte Tagzeilen und der eindeutige Index sichern konkurrierende Änderungen ab. Der davon unabhängige Seitenfehler steht in F4.
- **Verträge:** Portal-, Helpdesk-, Datei-, Ticket-, Tag- und Projektverträge mit den betroffenen Controllern/DTOs und Fehlerübersetzungen abgeglichen. Kundendateien im Portal enthalten keine internen Uploaderdetails; Portal-Tags keine fremde Organisationskennung. Fehlende optionale Werte werden als null abgebildet. Die Vertragstests bestanden. Sie prüfen überwiegend Routen und Struktur, nicht sämtliche Berechtigungs-/Nebenläufigkeitsfälle. Der Vertrag von `/seen` braucht die unter F2 beschriebene Präzisierung; die Attrappe weicht unter H3 ab.
- **Oberfläche:** Portalansicht, angebotene Typen, Statusnamen, Supportantwort, interne Dateifreigabe und Tagfreigabe sind vorhanden. Die Portaloberfläche verwendet für diese Funktionen die Portalpfade; die Tests zeigen keine interne Navigation und keinen Aufruf des internen Kontoverzeichnisses durch das Portalkonto. Rechteablehnungen werden vom Server durchgesetzt; ein ausgeblendetes Bedienelement wurde nicht als Sicherheitsgrenze gewertet.

## Abgleich der Abnahmekriterien und des Vorberichts

| Aufgabe / Kriterium | Prüfergebnis |
|---|---|
| TASK-076 AK 1: beschränkter Verlauf und direkter interner Download | Normalfall und Fall 8 bestehen; Kundenwechsel verletzt die Zielgruppengrenze, F1. |
| TASK-076 AK 2: Kundenantwort und Servicefristen wie Mailwege | Bestanden. Vorhandene Ereignistests und eigener Versuch mit echter Regel: Portal-Anlage erzeugt Zyklus; Supportantwort setzt Erstreaktion auf `met`; Kundenbeitrag setzt pausierte Lösungsfrist auf `running`. |
| TASK-076 AK 3: Migration, Vertrag, Backend und Browser | Prüfketten bestanden; Zielgruppenwechsel und GET-/Seen-Reihenfolge fehlen als Regressionstests, F1/F2. Demo-Anlage mit Nordwerk/Südtechnik und beiden Portalkonten in `tools/demo/demo.py:460` gelesen; kein Vorführimage gestartet. |
| TASK-077 AK 1: T04, T10, T18, T19 automatisch | Vorhandene Tests bestanden: verborgene Tags, eigene Treffer, Namensanlage/Freigabe und Ablehnung ohne Recht/Mitgliedschaft. Ausnahme H1; größere Namensgruppen und mehr als 20 Filteroptionen fehlen, F4/F5. |
| TASK-077 AK 2: interne Tags auch bei gleichzeitiger Änderung unberührt | Parallele Zuordnungen bestanden; Kommentar und tatsächlicher Testumfang unterscheiden sich, H2. Eigene gehaltene Sichtbarkeitswechsel für Setzen und Entfernen bestanden. Archivieren/Zusammenführen noch nicht in dieser Weise abgesichert. |
| TASK-077 AK 3: Migration, Vertrag und Oberflächenfälle | Grundfälle bestanden; Migration mit Bestandsdaten zusätzlich bestanden. Katalogcursor und vollständige Filterauswahl fehlerhaft, F4/F5; Attrappe H3. |
| TASK-078 AK 1: alle beauftragten Befunde/Hinweise behoben und belegt | Alte Befunde im Normalfall behoben, Einzelabgleich unten. Neue Schwäche beim Filterwechsel während einer Folgeseite, F3. |
| TASK-078 AK 2: bestehende Backend-/Browsertests grün | Bestanden, vollständige Läufe. |

| Vorbericht TASK-075 | Ergebnis im geprüften Stand |
|---|---|
| F1: gehaltene fremde Kundenzuordnung | Behoben: `JdbcTicketOrganizations.java:31` sperrt nur bei passender Organisation. Neuer Backendtest bestanden. |
| F2: Reihenfolge der Projektsperren | Behoben: `PortalAccounts.java:176` verwendet UUID-Text; `Permissions.current` serialisiert die Projektliste gegenüber der Portalverwaltung. Test mit UUID-Vorzeichenwechsel bestanden. |
| F3: veraltete Identität in Projektliste | Behoben: `ProjectService.java:397` prüft `current` innerhalb der Transaktion. Test mit während des Wartens deaktiviertem Konto bestanden. |
| F4: fehlende/null Projektliste | Behoben: Controller und Dienst verweigern mit 400 `projectIds.required`; ausdrücklich leer entfernt weiter alle Projekte. Backendtest bestanden. |
| F5: Portalpersonen als Bearbeiter/Personenfeld | Behoben: Auswahlen filtern Portalkonten; entsprechender Browserfall für bestehendes Ticket, Neuanlage und Personenfeld bestanden. |
| F6: nur erste 50 Anfragen erreichbar | Reguläres Nachladen behoben und mit 51 eigenen Anfragen getestet. Ergebnis bei konkurrierendem Filterwechsel noch falsch, F3. |
| H1: doppelte Nummer | Behoben; Browser prüft Schlüssel in Liste und Detail. |
| H2: 503 im Projektlistenvertrag | Ergänzt; Vertragstest bestanden. |
| H3: konkrete Ablehnungsgründe | Formular ordnet die Gründe einzeln zu; Kontaktwechsel-Browserfall bestanden. |
| H5: Helpdesk-Projektentzug | In derselben Transaktion werden die Portalmitgliedschaften entfernt. Backendtest und zusätzliche Prüfung neuer Portalpfade bestanden. |

## Tatsächlich ausgeführte Prüfungen

Alle zusätzlichen Daten waren synthetisch. Ergänzende Java-/Browserprogramme und Laufprotokolle lagen außerhalb des Prüfcheckouts; sie wurden nicht als Produkt- oder Teständerung eingecheckt.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Backend: vollständiges `mvn verify`, Java 21, Maven 3.9.16, PostgreSQL **16.15**, explizite Testverbindung und Serverversion | **496 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen; BUILD SUCCESS**, einschließlich Paketierung. Darin PortalAccountsApiTest 12, PortalConversationApiTest 5, PortalTagsApiTest 5 und die Vertragstests. |
| Frontend: `npm ci`, danach `npm run verify`, Node 24.22.0 | Erfolgreich: generierte Verträge/Typen, Typprüfung, **77 Tests**, Produktionsbuild. Vite meldet lediglich die bestehende Warnung zum großen JavaScript-Bündel. |
| Frontend: vollständiges `npm run test:browser` mit lokalem Chrome | **161 Tests in 31 Suiten**, alle bestanden, nichts übersprungen; darin elf Fälle in portal.test.mjs. |
| `python3 scripts/validate_repository.py` | Erfolgreich, unter Windows mit dem verfügbaren Python-Interpreter ausgeführt. |
| `python3 scripts/task_board.py --check` | Erfolgreich, keine Änderung an TODO.md. |
| `python3 -m unittest discover -s tests -v` | **38 Tests entdeckt, 37 bestanden, 1 übersprungen**: Wechsel der lokalen Zeitzone ist im betreffenden Test nur auf Unix möglich. |
| Eigene API-/Datenbankversuche gegen PostgreSQL 16 | Kundenwechsel F1; Gelesen-Reihenfolge F2; echte SLA-Regel; Deaktivierung, Rechte- und Helpdesk-Projektentzug; gleiche Namen verschiedener Zielgruppen; Katalogcursor F4; gehaltene Sichtbarkeitswechsel beim Setzen/Entfernen; 30 interne Tags und Rückrollen bei Ablehnung. Alle Versuche lieferten die oben protokollierten Ergebnisse. |
| Eigene Browserversuche mit Produktionsbuild und vorhandener API-Attrappe | Kontrolliert verspätete Folgeseite F3, 21 sichtbare Tags F5 und abgelehnte Anlage ohne Rückrollen in der Attrappe H3 reproduziert. |
| Zusätzlicher Migrationstest mit Bestandsdaten | Zunächst bis V0036 migriert, bestehenden Tag, Anhang, Portalgruppe und interne Gruppe vorbereitet, dann V0037–V0040 angewendet. Alte Datei/Tag bleiben `internal`, Tag ohne Organisationsbindung; nur Portalgruppe erhält genau einmal `portal.answer`; Rechteversion 7 → 8; neue Indizes vorhanden. Ungültige Sichtbarkeit bzw. unzulässige Organisationsbindung in vier Versuchen mit SQLSTATE `23514` abgelehnt. |

Ein erster Backendversuch scheiterte an einer bereits belegten Verbindung des zunächst gewählten Ports. Der erfolgreiche vollständige Lauf verwendete anschließend eine gesondert gestartete und vorher geprüfte PostgreSQL-16-Instanz. Dieser Infrastrukturfehler wurde nicht als Produktbefund gezählt; die fremde Instanz wurde nicht verändert.

Nicht ausgeführt: Docker-/Vorführimage und dessen vollständige Datenbefüllung, Linux-CI-Lauf, Lasttest mit großen Portalbeständen, exhaustive Sperr-/Abbruchmatrix und Browserprüfung mit dem echten Backend statt der API-Attrappe. Die zusätzlichen API-Versuche liefen dagegen gegen die echte Anwendung mit PostgreSQL und HTTP.

## Empfehlung

- **TASK-076: nicht abnehmen.** Zuerst die historische Zielgruppengrenze aus F1 schließen und den Kundenwechsel absichern; außerdem Gelesen-Markierung F2 korrigieren. Anlage, Rechteprüfungen, Dateigrundfälle und SLA-Wirkung sind positiv geprüft.
- **TASK-078: abnehmen nach Korrektur.** Die beauftragten Altfälle sind behoben. Den neu eingeführten Fehler beim Nachladen nach einem Filterwechsel aus F3 vor der Abnahme korrigieren und testen.
- **TASK-077: abnehmen nach Korrektur.** Katalogpagination F4 und vollständige Filterauswahl F5 korrigieren; die bestätigte Kapazitätsausnahme H1 bei der Bewertung der Datenschutzkriterien ausdrücklich berücksichtigen. H2/H3 begrenzen die Aussagekraft der bisherigen Tests und sollten nachgezogen werden.

Die gemeinsame Abnahme des Pakets wird derzeit nicht empfohlen. Entscheidung und Aufgabenabnahme bleiben bei Holger.
