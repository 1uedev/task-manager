# V1-Tags und performante Suche

Stand 17. Oktober 2026 · TASK-018 · Fachlicher und technischer Entwurf zur Prüfung

**Bestätigt:** Umfangreiches Tag-System für Tickets/Tasks und Projekte sowie sehr performante Suche gehören zu V1 (DEC-046, DEC-047; REQ-TAG-001, REQ-SRCH-001). Tasks sind Arbeitstypen im bestehenden Ticketmodell. **Alle folgenden Einzelmerkmale, Grenzen und Messwerte sind Vorschläge**, soweit keine weitere Entscheidung ausdrücklich genannt ist. Es wurde keine Suchfunktion implementiert oder gemessen.

## Jira als Orientierung, eigener klarer Umfang

Atlassian beschreibt für Jira Cloud mehrere Labels pro Vorgang, Vorschläge vorhandener Labels und Label-Filter; die [Label-Anleitung](https://support.atlassian.com/jira/kb/how-to-create-and-use-labels-in-jira-cloud/) dient als Funktionsreferenz. [Jira-Suche](https://support.atlassian.com/jira-software-cloud/docs/search-for-work-items-in-jira/) unterscheidet einfache und erweiterte Suchwege. Daraus wird weder eine vollständige JQL-Kompatibilität noch identisches Verhalten aller Jira-Produkte abgeleitet. Projekt-Tags sind hier eine eigene ausdrückliche Produktanforderung. Katalogpflege, Farben und die unten vorgeschlagenen Sichtregeln sind unser Entwurf, keine behauptete Jira-Standardausstattung.

## Vorgeschlagener Tag-Umfang

| Funktion | V1-Vorschlag und prüfbare Grenze |
|---|---|
| Mehrfachzuordnung | Mehrere Tags an einem Ticket/Task oder Projekt; derselbe Tag kann wiederverwendet werden. Projekt-Tags werden standardmäßig nicht auf Tickets vererbt. |
| Schnelle Bedienung | Autovervollständigung, Tastaturbedienung, Mehrfachauswahl und Entfernen einzelner Tags. Nicht vorhandene Tags nur bei gesondertem Erstellrecht direkt anlegen. |
| Darstellung | Name, optional Beschreibung und Farbe; der Name bleibt unabhängig von Farbe lesbar. DE/EN-Oberfläche, kundeneigene Tag-Namen bleiben unverändert und werden nicht automatisch übersetzt. |
| Katalogpflege | Berechtigte Verwaltung kann Namen ändern, Dubletten zusammenführen und Tags archivieren. Referenzen nutzen stabile IDs, nicht den Namen. |
| Massenänderung | Ausgewählte erlaubte Objekte erhalten oder verlieren einen Tag; Vorschau zeigt den Bereich und das Vorhaben. Kein ungefragtes Ersetzen der gesamten Tag-Liste. |
| Filter | Enthält alle gewählten Tags, enthält mindestens einen, enthält keinen der gewählten Tags sowie „ohne sichtbare Tags“. Mit Status, Projekt, Typ, Zuständigkeit und berechtigten Datumsfeldern kombinierbar. |
| Gespeicherte Abfragen | Vorschlag für private und ausdrücklich geteilte Filter; ein geteilter Filter speichert Kriterien, keine Freigabe seiner Treffer. Dieser Komfortteil von REQ-TKT-004 bleibt offen. |
| API | Dieselben Zuordnungs-/Verwaltungsaktionen und Prüfungen wie in der Oberfläche; Konfliktschutz und Wiederholbarkeit gemäß TECHNICAL_CONTRACTS.md. |

Holger wurde gefragt, wer neue Tags anlegen darf. **Ausgangsvorschlag:** berechtigte interne Nutzer erstellen Tags, eine ausdrücklich berechtigte Verwaltung pflegt den Katalog; externe Benutzer verwenden nur gezielt freigegebene Tags. Ohne Antwort ist dies keine bestätigte Rollenregel.

### Katalog, Identität und Änderungen

Ein Tag besitzt Unternehmenskennung, stabile ID, Namen, normierten Vergleichsschlüssel, Beschreibung/Farbe, Katalogbereich, Zielgruppe, Zustand und Änderungsstand. Vorgeschlagen werden ein bewusst gemeinsam nutzbarer Unternehmenskatalog und projektgebundene Kataloge. Ein projektgebundener Tag kann sein Projekt sowie dessen Tickets markieren; er ist keine unternehmensweite Offenlegung seines Namens. Die konkrete Katalogaufteilung bleibt unter OPEN-005/OPEN-021 offen.

Namen werden für Dubletten konsistent normalisiert; Vorschlag: äußere Leerzeichen entfernen, wiederholte Leerzeichen vereinheitlichen, Unicode normalisieren und Groß-/Kleinschreibung für den Vergleich ignorieren. Der Anzeigename bleibt erhalten. Keine stille Gleichsetzung verschiedener Wörter durch Übersetzung, Akzententfernung oder semantische KI-Ähnlichkeit. Umbenennungen behalten die ID, und Filter verwenden IDs. Eine Such-Synonymliste oder Aliasfunktion wäre eine gesonderte Erweiterung.

Zusammenführen darf Katalog-/Sichtbarkeitsgrenzen nicht unbemerkt erweitern. Vorschlag V1: nur innerhalb desselben Bereichs und derselben Zielgruppe, mit separatem Verwaltungsrecht und bestätigter Vorschau. Ziel-ID wird wirksam, alte ID bleibt als auflösbarer historischer Verweis; Zuordnungen werden dedupliziert. Bereits gelöschte Inhalte werden dadurch nicht wiederhergestellt. Archivierte Tags bleiben in erlaubten Bestandsansichten/filterbaren Zuordnungen erhalten, werden aber nicht für neue Zuordnungen vorgeschlagen. Endgültiges Löschen und Aufbewahrung folgen OPEN-016.

Massenänderung arbeitet auf ausdrücklich ausgewählten IDs mit begrenzter Anzahl; Vorschlag höchstens 500 je Auftrag. Jede Einzeländerung prüft aktuelle Rechte, Objektversion und Tag-Zustand und ist für sich atomar. Teilergebnisse sind erkennbar; der Auftrag behauptet keine Gesamttransaktion über 500 Objekte. Bei verlorenem Zugriff nennt das Ergebnis keinen fremden Titel oder Inhalt. Wiederholung erzeugt keine doppelten Zuordnungen. Wiederholen einer fehlgeschlagenen Teilaktion benötigt einen kontrollierten Folgeauftrag. Ein vollständiger Tag-Listenersatz aus einer alten Maske darf inzwischen ergänzte Tags nicht still löschen.

### Rechte vor Auffindbarkeit

Tag-Sichtbarkeit und Objekt-Sichtbarkeit sind getrennt. Das Lesen eines Tags erteilt kein Projekt- oder Ticketrecht. Ein Nutzer benötigt für die Anzeige einer Zuordnung sowohl Zugang zum Objekt/Feld als auch zum Tag. Ein ausdrückliches Feldverbot auf die Tags gewinnt gemäß DEC-028. Verbotene Tags beeinflussen weder Treffer, Reihenfolge, Facetten, Zähler noch Vorschläge. „Ohne sichtbare Tags“ bedeutet nur, dass in der eigenen Sicht keine vorhanden sind; daraus folgt keine Aussage über interne Tags.

Die Vorschlagsliste zeigt nur Katalogeinträge, für die ein ausdrückliches Entdeckungs-/Verwendungsrecht besteht. Bei einem gemeinsam freigegebenen Unternehmenskatalog sind dessen Namen bewusst im freigegebenen Kreis sichtbar, unabhängig von einzelnen Zuordnungen. Vertrauliche Projekte verwenden keine allgemein sichtbaren Tag-Namen. Nutzungszahlen werden zusätzlich nach den zugänglichen Objekten berechnet oder weggelassen. Ein allgemeiner Systemadministrator erhält dadurch weiterhin nicht automatisch sämtliche Fachinhalte.

Im Portal gilt als konservativer Vorschlag: nur ausdrücklich kundenlesbare Tags innerhalb erlaubter Projekte und Organisationsanfragen; keine internen Tags. Die Freigabe eines Tag-Namens legt die zugehörigen fremden Kundenanfragen nicht offen. Auftrag, Filter, Suche, E-Mail-Vorlage und KI-Kontext nutzen dieselben Sichtregeln. KI darf Tags vorschlagen; Anlegen, Zuordnen oder Zusammenführen verlangt die normale Berechtigung und ausdrückliche Bestätigung und ist kein zusätzlicher zugesagter V1-KI-Anwendungsfall.

## Vorgeschlagener Suchumfang

Bestätigt ist die performante Suche im Zusammenhang mit Tickets/Tasks, Projekten und Tags. Die Frage nach den weiteren Bereichen wurde Holger vorgelegt. **Empfehlung für die gemeinsame Suche:** zusätzlich Wiki und Anforderungen; Dateiinhalte erst später. Bis zur Antwort bleiben diese Erweiterungen Vorschläge.

| Bereich | Vorgeschlagene V1-Inhalte und Grenzen |
|---|---|
| Tickets/Tasks | Kennung, Titel, Beschreibung, berechtigte Kommentare und ausgewählte durchsuchbare eigene Felder; interne Beiträge nur mit ihrem eigenen Leserecht. |
| Projekte | Kennung, Name, Beschreibung und sichtbare Tags. Die Projektansicht darf keine nicht freigegebenen Projektinformationen an Portalnutzer ausgeben. |
| Wiki, falls aufgenommen | Berechtigte Sprachfassungen; öffentliche Leser finden nur veröffentlichte Stände. Entwurfssuche ist eine getrennte, ausdrücklich berechtigte Ansicht, nicht die Standardkundensuche. |
| Anforderungen, falls aufgenommen | Aktuelle berechtigte Inhalte einschließlich Kriterien; freigegebene historische Revisionen über einen gesonderten Filter. Keine Vermischung von alter Freigabe und neuem Entwurf. |
| Dateien | Vorschlag: berechtigte Dateinamen und Metadaten, keine PDF-/Office-Inhaltsextraktion, OCR oder Archivdatei-Inhalte in V1. Umfang bleibt von der offenen Antwort abhängig. |
| Git und KI | Geschützte Git-Providerinhalte bleiben gemäß GIT_MIGRATION.md aus dem globalen Index ausgeschlossen. Private KI-Vorschläge und Rohprompts werden nicht global durchsuchbar. |

Direkte Kennungssuche wird von Volltext und Tag-Auswahl getrennt. Exakte Tag-IDs sind Filter; Tippfehlertoleranz dient nur als sichtbar gemachter Vorschlag, nicht als stiller Wechsel des gewählten Tags. Vorgeschlagen werden Wortsuche und Phrasen, DE/EN-Verarbeitung sowie eine verständliche Filteroberfläche. Beliebige reguläre Ausdrücke, ausführbarer Abfragetext, vollständiges JQL und semantische Vektorsuche sind durch den V1-Kern nicht zugesagt. Anfrageumfang, zulässige Felder, Timeout und Fehlermeldungen sind Teil des API-Vertrags.

Eine Abfrage ist als strukturierter, validierter Ausdruck aus erlaubten Filtern darstellbar, ohne SQL an den Client zu geben. UND/ODER innerhalb der Tag-Auswahl und Kombination mit anderen Feldern werden eindeutig angezeigt. Unbekannte oder nicht sichtbare Tag-IDs erhalten dieselbe allgemeine Ablehnung. Keine nachträgliche Browserfilterung bereits gelieferter vertraulicher Daten. Vorschauen stammen nur aus zulässigen Textsegmenten und werden sicher dargestellt. Such- und Filterberechtigung auf ein Feld setzt dessen Lesbarkeit voraus.

## Technischer Ausgangspunkt: PostgreSQL vor zusätzlichem Suchdienst

**Empfehlung, noch keine Werkzeugfreigabe:** zunächst PostgreSQL-Volltextsuche mit geeigneten Indizes, relationale Tag-Zuordnungen und gezielte Ähnlichkeitssuche für Namen. PostgreSQL beschreibt [GIN-Indizes für Volltext](https://www.postgresql.org/docs/current/textsearch-indexes.html) und [pg_trgm für Ähnlichkeits-/Mustersuche](https://www.postgresql.org/docs/current/pgtrgm.html). Das sind geeignete Kandidaten, kein Nachweis, dass jede erlaubte Abfrage die Ziele erreicht. Die konkrete unterstützte PostgreSQL-Version bleibt offen.

Tag-Zuordnungen werden nicht als kommaseparierter Text gespeichert. Eindeutige Schlüssel verhindern doppelte Kombinationen aus Unternehmen, Tag und Ziel; Zugriffe erhalten passende kombinierte Indizes. Die Tabellen- und Adaptergestaltung bleibt ein Umsetzungsschritt. Datenbankabhängige Suche liegt hinter einem begrenzten Port; spätere MariaDB-/SQL-Server-/Oracle-Unterstützung braucht eigene Qualifikation, keine vorgetäuschte identische Suchfunktion.

**Vorgeschlagene Modulrichtung:** Das Klassifikationsmodul besitzt Katalog und Zuordnungen. Anwendungskomposition prüft Zielobjekte über deren öffentliche Schnittstellen und orchestriert Änderungen. Fachmodule importieren keine Tag-Implementierung, die wiederum diese Fachmodule importiert. Suche besitzt ihre abgeleiteten Projektionen; Fachmodule liefern versionierte, definierte Suchdaten über einen gemeinsamen Indexierungsvertrag. Der Suchadapter liest keine fremden Fachtabellen als undokumentierte Abkürzung. Berechtigungen liefern einen serverseitigen Abfragebereich über ihren Vertrag; konkrete SQL-/Join-Planung bleibt im Infrastrukturadapter und ist gemeinsam zu prüfen.

Ein einzelner Volltextvektor für alle sichtbaren und verborgenen Felder eines Tickets reicht nicht aus. **Suchbare Einheiten müssen nach Feld-/Beitrags-/Publikationssicht trennbar sein.** Ein vertrauliches Wort darf nicht einmal bewirken, dass ein sonst sichtbares Ticket erscheint oder höher rangiert. Erst aus aktuell erlaubten Segmenten werden Treffer, Rangfolge, Vorschau und Zähler gebildet; danach erfolgen Sortierung und Seitenauswahl. Ranking verwendet keine verdeckten Segmente oder globalen Statistiken, aus denen geschützte Inhalte abgeleitet werden könnten. Das konkrete Verfahren wird mit negativen Suchfällen und Abfrageplänen belegt.

Für reguläre V1-Änderungen wird eine synchrone Aktualisierung der lokalen Suchprojektion in derselben Fachtransaktion vorgeschlagen. Damit soll ein erfolgreich gespeicherter Titel oder Tag in der folgenden Suche sichtbar sein. Rechteentscheidungen dürfen trotzdem nicht nur aus einem kopierten Index-ACL-Stand stammen: aktuelle Objekt-, Feld- und Beitragsregeln gelten bei jeder Suche. Ein Wiederaufbau verwendet versionierte Projektionen und einen kontrollierten Wechsel; veraltete Versionen dürfen neue Inhalte nicht überschreiben. Bei unbekannter Aktualität keine alten Inhalte oder Trefferzahlen als gültig ausgeben. Defekte Projektionen führen zu erkennbar eingeschränkter Suche, nicht zur Entfernung der Rechteprüfung.

Indizes lassen sich wiederaufbauen; Quelldaten, aktuelle Berechtigungen und Wiederherstellungsprozess bleiben maßgeblich. Suchbegriffe, Tag-Namen und Vorschautexte gehören nicht in gewöhnliche Diagnoseprotokolle. Anonyme Messkennungen und Laufzeiten genügen für die Performanceprüfung. Caches müssen Rechte-/Unternehmens-/Benutzerkontext berücksichtigen; Browserantworten folgen dem vorgeschlagenen `no-store`-Vertrag.

Ein zusätzlicher Suchdienst wird erst bei nachgewiesenem Bedarf vorgeschlagen: dokumentierte Abfragepläne und Indexoptimierung verfehlen die freigegebenen Ziele, oder ein neuer Suchumfang verlangt ihn. Dann sind Ressourcen, Aktualisierungsverzug, Rechtefilter, Betrieb und Wiederherstellung gesondert zu entscheiden. Weder Elasticsearch/OpenSearch noch ein Message-Broker werden jetzt als Pflichtdienst eingeführt. REST oder gRPC lösen keine fehlenden Indizes oder zu teure Berechtigungsabfragen.

## Messbare Zielvorschläge

Die Messung baut auf [OPERATIONS.md](OPERATIONS.md) auf: 4 vCPU/16 GB als unverbindliche Referenz, SSD, 100 Konten, 15 gleichzeitig aktive Benutzer, 20 Projekte und 90.000 Tickets. Ergänzendes synthetisches Tag-Profil: 5.000 Katalogeinträge, durchschnittlich fünf Tags je Ticket und zehn je Projekt, entsprechend 450.200 Zuordnungen. Zusätzlich 180.000 durchsuchbare Kommentare unterschiedlicher Zielgruppen, vorhandene eigene Felder und bei erweitertem Suchumfang 2.000 Wiki-Seiten sowie 10.000 Anforderungen. Realistische Textlängen und Sprachmischung werden im späteren Messdatensatz festgehalten.

| Messfall | Vorgeschlagenes Ziel, kein gemessener Wert |
|---|---|
| Tag-/Kennungsvorschläge | p95 höchstens 200 ms serverseitig einschließlich Authentifizierung und Rechtefiltern, begrenzte Ergebnismenge |
| Erste Seite regulärer Suche, maximal 50 Treffer | p95 höchstens 500 ms serverseitig, einschließlich Volltext/Tagfilter, Rechteprüfung und Antwortaufbereitung |
| Komplexe freigegebene Kombinationen | p95 höchstens 1 s; bis zu zehn gewählte Tags, mehrere Projekt-/Feldfilter; keinesfalls Weglassen der Rechteprüfung |
| Wahrgenommene reguläre Suche | p95 höchstens 1 s von abgeschlossener Eingabe/ausgelöster Suche bis zur verwendbaren ersten Seite, einschließlich Debounce, Netz und Darstellung unter dokumentiertem Netzprofil |
| Schreibsichtbarkeit | Nach erfolgreicher regulärer Speicherung sieht eine danach gestartete erlaubte Suche die aktuelle Fassung; Reindexierung und Wartung werden gesondert geprüft |

Regulär umfasst direkte Kennung, ein Suchwort beziehungsweise eine Phrase und übliche Projekt-/Status-/Tagfilter. Kurze Präfixe, sehr häufige Begriffe, leere Treffer, mehrere große Berechtigungsgruppen, negative Tagfilter und Portalabfragen bilden eigene Messklassen. Jede Klasse bekommt genügend Stichproben; p95/p99, Fehlerquote und Abfragepläne werden getrennt erfasst. Der vorgeschlagene 30-Minuten-Lauf und zweistündige Dauerlauf aus OPERATIONS.md bleiben bestehen, mit paralleler Mail-/SLA-Last und zusätzlichen Spitzen durch Autovervollständigung. Kalt-/Warmzustand, Timeout und abgebrochene alte Browserabfragen werden geprüft. Ein Gesamtmittelwert über einfache und schwierige Suchen reicht nicht.

Diese Ziele konkretisieren „sehr performant“ zur Entscheidung. Sie ändern keine bereits bestätigten RPO-/RTO-Vorgaben und behaupten keinen Leistungsnachweis gegenüber Jira oder Redmine. Datenmengen, Indexgröße und Arbeitsspeicher sind vor Auslieferung praktisch zu messen.

## Konkrete spätere Prüffälle

| Fall | Auslöser | Erwartetes Ergebnis im Vorschlag |
|---|---|---|
| T01 | Zwei berechtigte Nutzer legen gleichzeitig denselben normierten Tag im selben Bereich an | Ein Katalogeintrag, nachvollziehbare Wiederverwendung oder Konflikt; keine Dublette. |
| T02 | Tag wird umbenannt | Alle Zuordnungen und gespeicherten Filter bleiben über die ID korrekt; keine neue Sichtberechtigung. |
| T03 | Zwei Tags zusammenführen, Quelle und Ziel waren am selben Ticket | Eine wirksame Zuordnung; historische Referenzen bleiben verständlich, keine unzulässige Bereichserweiterung. |
| T04 | Interner Projekt-Tag soll im Portal vorgeschlagen werden | Kein Name, Zähler, Treffer oder Hinweis auf sein Vorhandensein. |
| T05 | Projekt erhält „kritisch“, Ticket hat keinen sichtbaren Tag | Ticket erbt nichts automatisch; Filter „ohne sichtbare Tags“ bleibt korrekt. |
| T06 | Massenänderung nach Rechteentzug an einem Teil der Auswahl | Aktuell erlaubte Einzelaktionen korrekt, andere abgewiesen; keine fremden Namen im Ergebnis, Wiederholung ohne Duplikate. |
| T07 | Zwei Bearbeiter ergänzen verschiedene Tags | Kein stiller Listenverlust; Konflikt oder sichere mengenbasierte Einzelaktion nach Vertragsprüfung. |
| T08 | Archivierten Tag neu zuordnen oder vorhandene Zuordnung suchen | Neue Zuordnung abgewiesen; erlaubter Bestand weiterhin auffindbar. |
| T09 | Vertrauliches Wort steht nur in einem verborgenen Feld eines sonst sichtbaren Tickets | Das Wort erzeugt für Unberechtigte weder Treffer noch Rangänderung oder Vorschau. |
| T10 | Nordwerk-Portalnutzer sucht Tag, den auch Südtechnik verwendet | Nur eigene erlaubte Anfragen; Zähler/Facetten verraten keine Südtechnik-Zuordnung. |
| T11 | Veröffentlichtes Wiki wird geändert, Entwurf enthält neuen Suchbegriff | Ohne Entwurfsrecht kein Treffer auf den neuen Begriff; Veröffentlichung bleibt ausdrückliche Aktion. |
| T12 | Rechte werden während Reindexierung entzogen | Nächste Suche respektiert aktuelle Rechte; alte Projektion wird nicht als Erlaubnis benutzt. |
| T13 | Titel/Tag wird erfolgreich gespeichert, danach direkt gesucht | Aktueller erlaubter Stand auffindbar; alte Version kann ihn nicht durch spätes Indexereignis ersetzen. |
| T14 | Geteilter Filter oder manipulierte Tag-ID | Filter gewährt keine Rechte; unzulässige IDs ohne Existenzdetails abgewiesen. |
| T15 | Referenzlast mit häufigen Begriffen, zehn Tags, Portalrechten und Hintergrundjobs | Messklassen getrennt gegen vorgeschlagene Ziele auswerten; Zielverfehlung sichtbar, keine Aussage allein aus Mittelwerten. |
| T16 | Suchprojektionsfehler oder Wiederherstellung | Kein stiller Zugriff über alte Inhalte; kontrollierter Wiederaufbau und sichtbarer Betriebszustand. |

T11 gilt nur, wenn Wiki in die gemeinsame Suche aufgenommen wird. Alle Fälle sind Planung, keine ausgeführten Produktprüfungen. Quellen wurden am 17. Oktober 2026 gelesen. Offene Katalog-/Erstellrechte, Suchbereiche, Grenzen und Leistungsziele werden unter OPEN-005, OPEN-011, OPEN-014 und OPEN-021 weitergeführt; Prüfnachweise zu diesem Dokument stehen zentral bei TASK-018.
