# Vorschlag für Frontend-, Modul- und API-Verträge

Stand 16. Oktober 2026 · TASK-016 · Ergänzung zu TASK-003, zur Prüfung

Dieser Entwurf konkretisiert [ARCHITECTURE.md](ARCHITECTURE.md) für REQ-ARCH-001, REQ-ARCH-002, REQ-API-001 und REQ-API-002. Er soll verhindern, dass verschiedene KI-Sitzungen unterschiedliche Daten-, Rechte- oder Fehlerverträge implementieren. Java/Spring Boot, React, PostgreSQL und Docker sind bestätigt. **Alle neu beschriebenen technischen Ausgestaltungen sind Vorschläge.** Es wurden keine Anwendung, Bibliothekskonfiguration, OpenAPI-Datei oder Container erstellt.

## Drei Grundentscheidungen

| Auswahl unter den offenen Fragen | Empfehlung und Nutzen | Aufwand und Grenze |
|---|---|---|
| OPEN-001: React mit TypeScript | TypeScript mit strenger Typprüfung; fehlende oder falsch verwendete API-Felder werden früh sichtbar | Typen müssen gepflegt werden. Sie ersetzen keine Laufzeitvalidierung oder Rechteprüfung. JavaScript bleibt technisch möglich, verlangt aber mehr Disziplin bei denselben Datenverträgen. |
| OPEN-002: modularer Monolith | Eine gemeinsam ausgelieferte Anwendung mit getrennten Fachmodulen; Webprozess und Worker können dasselbe Image verwenden | Modulgrenzen müssen geprüft werden. Mehrere Prozesse desselben Produkts sind möglich; unabhängige Microservices mit verteilten Transaktionen werden für V1 nicht empfohlen. |
| OPEN-013: HTTP/JSON im REST-Stil mit OpenAPI | Ein beschriebener Vertrag für Oberfläche und Integrationen, einschließlich fachlicher Aktionen | OpenAPI dokumentiert Formen und Operationen; Fachregeln und Zugriffsrechte brauchen eigene Prüfungen. Eine zweite GraphQL- oder öffentliche RPC-Schnittstelle würde zusätzliche Verträge erzeugen und wird zunächst nicht empfohlen. |

Die drei Grundentscheidungen sind Holger vorgelegt. Ihre Bestätigung würde weder alle folgenden Detailregeln noch konkrete Werkzeugversionen oder den Beginn der Implementierung einschließen. Der modulare Monolith ist eine Empfehlung für den bekannten Funktionsumfang und Betrieb, keine Behauptung über gemessene Leistung.

## Frontend und Datenvertrag

**Vorgeschlagene Arbeitsweise:** Den freigegebenen API-Vertrag zuerst beschreiben, daraus später TypeScript-Transporttypen und einen dünnen Client ableiten. OpenAPI ist dabei die gemeinsame Quelle für den HTTP-Vertrag; Java-Fachobjekte und Datenbanktabellen werden nicht automatisch veröffentlicht. Generator, OpenAPI-Version und Java-Anbindung sind erst nach einem Kompatibilitätsnachweis festzulegen. Generierte Dateien werden nicht von Hand korrigiert; eine Änderung erfolgt an der Quelle und wird reproduzierbar neu erzeugt. Kleine lokale UI-Zustände bleiben von Transporttypen getrennt.

`strict` wird für eigenen TypeScript-Code vorgeschlagen. Neue Umgehungen über `any`, ungeprüfte Typumwandlungen oder deaktivierte Prüfungen benötigen eine begründete Ausnahme im Review. Ein Typversprechen nach dem JSON-Einlesen ist kein Nachweis korrekter Daten: Das Backend validiert jede Eingabe, und der Client behandelt fehlerhafte oder unerwartete Antworten kontrolliert. Die offizielle Dokumentation beschreibt [strenge Typprüfung](https://www.typescriptlang.org/tsconfig/strict.html) und das [Entfernen der Typangaben beim Übersetzen](https://www.typescriptlang.org/docs/handbook/2/basic-types.html#erased-types). Daraus folgt keine automatische Laufzeitabsicherung.

| Datenthema | Gemeinsamer Vorschlag |
|---|---|
| Kennungen | Opaque Zeichenketten, keine fachliche Bedeutung aus laufenden Zahlen ableiten. Sichtbare Ticketnummer und interne Kennung trennen. Die konkrete ID-Erzeugung bleibt offen. |
| Zeiten | Ereigniszeitpunkt eindeutig mit UTC-Bezug; fachliches Datum einer Zeitbuchung separat als Datum. Geschäftszeitzone getrennt speichern. Anzeigen hängen von Sprache und Zeitzone ab, nicht vom Serverstandort. |
| Dauern und Dezimalwerte | Buchungsdauer als ganze Sekunden; fachliche Rundung nur ausdrücklich. Exakte Dezimalwerte als normierte Dezimalzeichenketten mit dokumentierter Skala, damit Java und JavaScript nicht still unterschiedlich runden. |
| Freie Felder | Stabile Feld-ID plus Typ und erlaubte Werte aus der Projektkonfiguration; keine beliebige Ausführung von Formeln oder Kundencode. Umbenennen ändert keine API-ID. |
| Fehlend, leer und verborgen | Ein lesbares, leeres optionales Feld kann `null` sein. Ein verborgenes Feld einschließlich seiner Metadaten wird weggelassen. In einem Änderungsauftrag bedeutet ein ausgelassenes Feld „unverändert“; `null` bedeutet ausdrücklich leeren, soweit erlaubt. |
| Erlaubte Aktionen | Der Server kann eine erlaubte Aktionsliste für die Oberfläche liefern. Sie ist eine momentane Bedienhilfe und keine Berechtigung für einen späteren Schreibaufruf. |
| Fehler und Sprache | Stabile Fehlercodes und Übersetzungsschlüssel; keine Logik anhand deutscher/englischer Meldungstexte. Übersetzte Bezeichnungen ändern weder Workflow- noch Feldkennungen. |

Beispiel: Das Portal erhält für ein Ticket weder das Feld „Interne Kalkulation“ noch dessen Auswahlwerte, Validierungsdetails oder Suchfilter. Ein selbst gebauter Client darf es auch dann nicht setzen, wenn er die Feldkennung kennt. Der gesamte Änderungsauftrag wird abgewiesen; erlaubte Teilfelder werden nicht heimlich schon gespeichert. Die bestätigte Feldverbotsregel aus DEC-028 bleibt bestehen.

UI-Bausteine für Tabellen, Formulare, Fehlermeldungen und Editorzustände verwenden dieselben Verträge in Anwendung und Portal. Berechtigte Wiki-Entwürfe, Veröffentlichungen und Sprachfassungen bleiben unterschiedliche Zustände. Die konkrete Komponentenbibliothek, der Editor, der Bundler und die unterstützten Browser werden hier nicht ausgewählt.

## Modulgrenzen und Verantwortlichkeiten

Die fachliche Datenhoheit aus [ARCHITECTURE.md](ARCHITECTURE.md) bleibt bestehen. Folgende Abhängigkeitsordnung konkretisiert sie; Aufrufe nach unten erfolgen ausschließlich über veröffentlichte Modulverträge. Kein Modul liest fremde Tabellen oder übernimmt fremde ORM-Entitäten. Der Anwendungseinstieg setzt die Module zusammen.

| Ebene | Zuständigkeit und erlaubte Nutzung |
|---|---|
| Technische Verträge | Kleine Verträge für IDs, Zugriffskontext, Zeitquelle, Transaktion, Dateispeicher, Aufträge und Audit. Keine zentrale Universalklasse für alle Fachobjekte. Infrastruktur implementiert diese Verträge. |
| Identität und Berechtigungen | Identität liefert geprüfte Konten/Sitzungen. Berechtigungen bewerten Identität, Gruppen und vertrauenswürdige Ressourcenmerkmale, laden aber keine Tickets oder Wiki-Seiten. |
| Unternehmen/Projekte und Dateien | Projekte besitzen Organisationszuordnung und Konfigurationsversionen. Dateiverwaltung besitzt Uploadzustand, Inhalt und Speicherreferenz. Beide verwenden Basisverträge und Rechteprüfung. |
| Tickets, Wissen, Anforderungen, Git | Jedes Modul besitzt sein Fachmodell, nutzt Projekte/Rechte und bei Bedarf Dateien. Diese vier Module importieren keine internen Modelle der anderen. |
| Zeit, Helpdesk, Verknüpfungen | Zeit prüft Tickets über deren öffentliche Schnittstelle. Helpdesk orchestriert Ticketaktionen und besitzt Mail-/SLA-Zustände. Verknüpfungen führen typisierte Referenzen und prüfen deren Ziele über öffentliche Schnittstellen. |
| KI und zusammengesetzte Ansichten | KI liest berechtigte Quellen und ruft nach Bestätigung normale Fachaktionen auf. Eine Ticketansicht mit Zeit und Links setzt die jeweiligen Leseergebnisse zusammen; Tickets bekommen dadurch keine Rückabhängigkeit auf Zeit, Links oder KI. |

**Zwei Grenzfälle:** Eine Anforderung besitzt ihre Revisionen selbst. Beziehungen zu Ticket, Wiki oder Git liegen im Verknüpfungsmodul; eine gemeinsame Ansicht wird darüber zusammengesetzt. Ein Datei-Download wird am Anwendungseinstieg über das zuständige Elternmodul autorisiert; erst danach liefert der interne Dateidienst den Inhalt. Der Browser kann keinen „bereits geprüft“-Schalter übergeben. Dadurch muss der Dateidienst nicht seinerseits Tickets importieren. Auch Vorschau, Metadaten und Download benötigen die Elternprüfung; eine bekannte Speicherreferenz reicht nie.

Öffentliche Modulverträge nennen für jede Operation: Eingaben, Unternehmens-/Benutzerkontext, Datenhoheit, erforderliche Rechte, erwartete Objekt-/Konfigurationsversion, Transaktionsgrenze, Ergebnis, Fehler und Wiederholung. Eine Rückgabe ist eine begrenzte Datenansicht, kein veränderbares Datenbankobjekt. Adapter dürfen nicht still auf ein anderes Unternehmen, einen privilegierten Git-Zugang oder ein Cloud-Modell ausweichen.

Für die spätere Überprüfung wird Spring Modulith als Kandidat vorgeschlagen. Seine [Modulprüfung](https://docs.spring.io/spring-modulith/reference/verification.html) kann unter anderem Zyklen und Zugriffe auf interne Pakete erkennen. Die hier beschriebene fachliche Ordnung und sichere Laufzeitaufrufe müssen zusätzlich geprüft werden; das Werkzeug und seine Version sind noch nicht gewählt.

### Transaktionen, Rechteänderungen und Ereignisse

Eine lokale Transaktion darf mehrere Module über deren öffentliche Operationen verbinden, wenn ein fachliches Alles-oder-nichts nötig ist. Beispiel: Der Helpdesk speichert eine zugeordnete eingehende Nachricht, legt das Ticket an und vermerkt den Eingang als verarbeitet. Scheitert ein Teil, darf kein halbes Ergebnis bestehen bleiben. Zuständiger Orchestrator, Sperrreihenfolge und beteiligte Versionen gehören in den Vertrag dieses Ablaufs.

Fachänderung, relevante Historie, Wiederholungsnachweis und ausgehender Auftrags-/Ereigniseintrag werden in derselben PostgreSQL-Transaktion verbindlich gespeichert. SMTP, Git und KI-Aufrufe laufen außerhalb dieser Transaktion. Benachrichtigungen und andere verzögerbare Nebenwirkungen nutzen dauerhafte Aufträge; eine erneute Verarbeitung verwendet eine eindeutige Auftrags-/Ereigniskennung und erzeugt keine zweite interne Fachwirkung. Der unsichere externe SMTP-Zustellstatus bleibt gemäß [HELPDESK_SLA.md](HELPDESK_SLA.md) gesondert behandelt.

Eine bloße Rechteprüfung einige Millisekunden vor dem Speichern reicht nicht aus. Vorgeschlagen wird ein gemeinsames Serialisierungsverfahren für Fachschreiben und Änderungen relevanter Rechte-/Mitgliedschaftsversionen: Sperren beziehungsweise bedingte Versionsprüfung müssen bis zum Commit wirken; Rechteänderungen nehmen am selben Verfahren teil. Eine feste Reihenfolge verhindert wechselseitiges Warten. Detailtechnik und Lastwirkung sind vor Implementierung dieses Vertrags nachzuweisen. Verbindlicher Prüffall im Vorschlag: **Ist der Rechteentzug zuerst verbindlich gespeichert, darf der konkurrierende Schreibvorgang nicht mehr mit alten Rechten erfolgreich sein.** Ein zuvor abgeschlossenes, damals berechtigtes Schreiben wird dadurch nicht rückwirkend unzulässig.

Ereignisse enthalten Unternehmenskennung, Ereignis-ID, Art, Schemaversion, Zeitpunkt und erforderliche Ressourcenreferenzen. Vertrauliche Volltexte werden nicht vorsorglich an alle Verbraucher verteilt. Worker laden nur den zulässigen aktuellen Kontext; historische Fachereignisse bleiben versioniert. Benutzeraufträge und explizite Systemaufgaben wie SLA-Verarbeitung besitzen unterschiedliche, begrenzte Ausführungskontexte. Ein Dienstkonto ist kein allgemeiner Rechteumgehungsweg.

## HTTP-Vertrag mit konkreten Abläufen

Alle Pfade und Werte im Folgenden sind Entwurfsbeispiele. `/api/v1` ist die vorgeschlagene Hauptversion, nicht die Versionsnummer von OpenAPI oder der Anwendung. Der Standard [OpenAPI](https://spec.openapis.org/oas/latest.html) beschreibt HTTP-Schnittstellen; die kompatible Werkzeugkombination wird separat festgelegt.

### Anmeldung und aktuelle Rechte

Für die eigene Weboberfläche wird eine serverseitig widerrufbare Sitzung in einem `Secure`-/`HttpOnly`-Cookie vorgeschlagen, mit passender SameSite-Regel und CSRF-Prüfung für schreibende Aufrufe einschließlich Anmeldeablauf. Oberfläche und API sollen zunächst denselben Ursprung verwenden. CORS ist standardmäßig geschlossen; eine spätere Freigabe braucht konkrete erlaubte Ursprünge. SameSite und CORS ersetzen weder CSRF- noch Fachrechteprüfung.

Für externe Integrationen werden separat widerrufbare, zufällige opake API-Zugänge vorgeschlagen: einmalige Anzeige des Geheimnisses, serverseitig nur eine geschützte Prüfreferenz, feste Identität, Ablaufdatum, ausgewählte Projekte und Aktionen. Der aktuelle Kontostatus, die derzeitigen Gruppenrechte und der Tokenumfang begrenzen gemeinsam den Zugriff. Der Token erzeugt weder eine Kundenmitgliedschaft noch zusätzliche Rechte. Geheimnisse gehören in den Authorization-Header, nicht in URLs oder Protokolle. Ausgabe darf nur den eigenen zulässigen Umfang delegieren; wer Zugänge administrieren darf, wird mit OPEN-006 abgestimmt. Tokenlaufzeit und Sitzungsfristen bleiben dort offen. Ein späterer SSO-Anbieter liefert denselben internen Identitätskontext.

Jeder Aufruf prüft zuerst Identität und erlaubten Ressourcenbereich. Ein angemeldeter Benutzer erhält für eine nicht sichtbare oder nicht existierende Ressource dieselbe knappe 404-Antwort. Ist das Objekt lesbar, die angefragte Aktion aber verboten, folgt 403. Fehler, Suchtreffer, Zähler, Links und Datei-Metadaten dürfen keine fehlenden Rechte umgehen. Anwendungsdaten erhalten im V1-Vorschlag `Cache-Control: no-store`; öffentliche statische Assets werden getrennt behandelt. Bereits gelesene oder kopierte Daten können nicht nachträglich zurückgerufen werden.

### Ressourcen ändern und Fachaktionen auslösen

| Beispiel | Vorschlag für Vorbedingung und Ergebnis |
|---|---|
| `POST /api/v1/projects/{projectId}/tickets` | Anlageberechtigung, gültige Konfiguration und `Idempotency-Key`; 201 mit kleiner Anlagequittung und Location der neuen Ressource. Organisationszuordnung serverseitig prüfen. |
| `GET /api/v1/tickets/{ticketId}` | Nur erlaubte Felder, starke opake ETag für diese Darstellung. Die ETag berücksichtigt Darstellungs-/Versionskontext; keine rohe fortlaufende Datenbankversion als universelle ETag. |
| `PATCH /api/v1/tickets/{ticketId}` | Begrenzte, ausdrücklich dokumentierte Feldänderungen mit `If-Match` derselben Ressource; bei Erfolg 200 mit aktueller erlaubter Darstellung und neuer ETag. Kein frei beschreibbarer Status oder Administrationskontext. |
| `POST /api/v1/tickets/{ticketId}/transitions` | Erlaubte Übergangs-ID, opake `expectedTicketVersion`, Konfigurationsbezug und Wiederholungsschlüssel. Veraltete fachliche Version führt zu 409, ebenso ein inzwischen unzulässiger Übergang; unterschiedliche Fehlercodes. |
| `POST /api/v1/tickets/{ticketId}/time-entries` | Eigene Zeitrechte, Leistungsdatum und genaue Dauer; Wiederholungsschlüssel. Löschen/Korrigieren folgt dem Zeitvertrag, kein beliebiges Überschreiben der Historie. |
| `POST /api/v1/wiki/{documentId}/languages/{language}/publications` | Gesondertes Veröffentlichungsrecht, erwartete Entwurfsrevision und Wiederholungsschlüssel. Speichern eines Entwurfs allein veröffentlicht nichts. |
| `POST /api/v1/requirements/{requirementId}/approvals` | Freigaberecht, erwartete fachliche Revision und technische Inhaltsversion gemeinsam atomar prüfen. Entwurfsänderung innerhalb derselben Revision verhindert die Freigabe des alten Bildschirmstands mit 409. Eine neue fachliche Revision erbt keine historische Freigabe. |
| `POST /api/v1/ai-proposals/{proposalId}/confirmations` | Bestätigte Vorschlagsversion, fachliche Aktionskennung, unveränderte Quellen-/Zielversionen und aktuelle Rechte; Anwendung über normale Fachaktion. Versand und Wiki-Veröffentlichung bleiben gesondert erkennbare Bestätigungen. |

Der Unterschied zwischen den beiden Versionsverfahren ist beabsichtigt: `If-Match` prüft die Darstellung der **aufgerufenen Ressource**. Eine ETag von `/tickets/{id}` wird daher nicht ungeprüft als HTTP-Vorbedingung für eine andere URL wie `/transitions` verwendet. Fachaktionen nennen ihre erwarteten Versionen im Auftrag. GET liefert dafür zusätzlich zur ETag den jeweils benötigten opaken Fachversionswert, soweit die Aktion zulässig ist. Der Fachversionswert ist keine Zugriffsberechtigung.

Die PATCH-Operation verwendet einen eigens dokumentierten JSON-Änderungsauftrag mit erlaubten Feldern und der oben beschriebenen Fehlend-/Null-Semantik. Sie ist weder ein generischer Objektsetter noch stillschweigend JSON Patch oder JSON Merge Patch. Konkreter Medientyp und Schema werden im späteren OpenAPI-Vertrag festgelegt. Fehlende erforderliche `If-Match`-Vorbedingung ergibt 428, eine nicht mehr passende 412. `If-Match: *` genügt für diesen Bearbeitungsschutz nicht. Ungültige/ungeeignete Vorbedingungen werden ohne Änderung abgewiesen. Die HTTP-Grundlagen sind [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1) und [RFC 6585](https://www.rfc-editor.org/rfc/rfc6585.html#section-3); diese Projektregeln engen den erlaubten Clientvertrag ein.

**Beispiel gleichzeitige Bearbeitung:** Lea und Paul lesen dieselbe sichtbare Ticketfassung. Lea speichert zuerst. Pauls PATCH mit alter ETag erhält 412; seine Eingabe bleibt lokal verfügbar, und er lädt die aktuelle erlaubte Fassung zum Vergleich. Kein automatisches Überschreiben und keine automatische Zusammenführung vertraulicher, nicht sichtbarer Felder. Wurde ihm inzwischen das Leserecht entzogen, erhält er stattdessen 404 ohne fremde Inhalte.

### Wiederholung und lange Vorgänge

Für Anlage- und Aktionsaufrufe wird `Idempotency-Key` als **projektspezifischer Vertrag** vorgeschlagen. Er ist kein Versprechen einer exakt einmaligen externen Zustellung. Schlüssel gelten innerhalb Unternehmen, Identität, API-Zugang beziehungsweise Browser-Client und Operation einschließlich Zielpfad. Ein normierter Fingerabdruck umfasst alle fachlich wirksamen Eingaben einschließlich erwarteter Versionen. Unterschiedliche JSON-Schlüsselreihenfolge allein verändert den Fingerabdruck nicht; Normierung und relevante Header gehören in den späteren Vertrag.

Der erste erfolgreiche Auftrag speichert Fachwirkung und kleine Quittung atomar. Die Quittung enthält nur Operation, Ergebnis-/Ressourcenreferenz und Zeitpunkt, keine dauerhaft kopierten Feldinhalte. Eine gleichlautende Wiederholung prüft zuerst aktuelle Identität, Zugangsbereich sowie die Rechte auf Aktion und Ergebnis. Erst dann wird dieselbe Quittung mit demselben Erfolgsstatus zurückgegeben; die Fachaktion wird nicht nochmals ausgeführt und ihre inzwischen alte erwartete Version nicht als neuer Auftrag bewertet. Die aktuelle Ressource wird separat mit aktuellen Leserechten geladen. Bei Rechteentzug gibt es keine alte Antwort als Hintertür. Eine geänderte Nutzlast unter demselben Schlüssel ergibt 409 ohne neue Wirkung. Parallele gleiche Aufträge dürfen durch eindeutige Reservierung nur eine Wirkung erzeugen; bei noch laufender Verarbeitung folgt ein dokumentierter Konfliktcode mit Wiederholungshinweis.

Als Ausgangswert werden **24 Stunden** für abgeschlossene Wiederholungsnachweise vorgeschlagen; laufende Aufträge behalten ihre Zuordnung bis zum Abschluss. Das Fenster wird dem Client mitgeteilt. Nach Ablauf ist keine Duplikatfreiheit für denselben Schlüssel zugesagt: Der Client wiederholt nicht blind, sondern klärt den fachlichen Ergebnisstand. Bei langen Importen wird später eine eigene dauerhafte Importzuordnung gemäß [GIT_MIGRATION.md](GIT_MIGRATION.md) verwendet. Personenbezogene Protokollierung, Speichergrenzen und Aufbewahrung sind mit OPEN-014 abzustimmen.

Dieses Fenster begrenzt ausschließlich den **HTTP-Wiederholungsnachweis**, nicht die Einmaligkeit einer fachlichen Aktion. Für KI-Bestätigungen gilt zusätzlich [AI_USE_CASES.md](AI_USE_CASES.md): Dieselbe fachliche Aktionskennung darf nur einen internen Schreib- oder Versandauftrag erzeugen, auch mit einem neuen HTTP-Schlüssel oder nach Ablauf der 24 Stunden. Der verbrauchte Zustand wird mit Fachwirkung beziehungsweise Auftrag atomar gespeichert und bleibt erhalten, solange die Aktion erneut adressierbar ist. Löschen eines Vorschlags ermöglicht kein Wiederanlegen unter derselben Kennung; eine endgültig entfernte Aktion ist nicht mehr ausführbar. Der Rechteprüfung folgt bei erneuter Bestätigung nur ein erlaubter Verweis auf den bestehenden Ausgang beziehungsweise eine allgemeine Ablehnung, niemals eine zweite Ausführung.

Ein langer Vorgang antwortet nach verbindlicher Auftragsanlage mit 202 und einer Job-Referenz. Status und Ergebnis sind nur innerhalb des erlaubten Bereichs abrufbar. Die Quittung sagt „angenommen“, nicht „erfolgreich abgeschlossen“. Rechte werden bei Ausführung und Ergebnisabruf erneut geprüft. Ein Neustart setzt reservierte Arbeit kontrolliert fort; eine doppelte Worker-Ausführung wird durch denselben Fachnachweis abgefangen. Auftragsfristen, Abbruch und Wiederholungsbudgets bleiben je Integration zu spezifizieren.

### Listen, Dateien und Fehler

Listen erhalten im Vorschlag standardmäßig 50 und höchstens 100 Einträge. Rechte werden vor Filterergebnis, Zählung und Seitenauswahl angewendet. Cursor binden Identität/Zugangsbereich, Unternehmen, Filter, Sortierung und letzte Position, sind gegen Manipulation geschützt und geben keine vertraulichen Werte im Klartext preis. Sie ersetzen nie eine neue Rechteprüfung. Standardsortierung ist ein unveränderlicher Erstellzeitpunkt mit eindeutiger ID als Zweitschlüssel. Bei veränderlichen Sortierfeldern können parallele Änderungen Treffer verschieben; die UI muss neu laden können. Eine seitenübergreifende Momentaufnahme wird dadurch nicht zugesagt. Ein späterer Export benötigt einen eigenen konsistenten Vertrag. Fremde, ungültige oder abgelaufene Cursor werden allgemein abgewiesen, nicht als leerer Erfolg ausgegeben.

Datei-Uploads erhalten zunächst einen begrenzten temporären Zustand. Erst nach Größen-/Typprüfung, erforderlicher Inhaltsprüfung und berechtigter Zuordnung werden sie lesbar. Datenbanktransaktion und Dateisystem sind keine gemeinsame ACID-Transaktion: finaler Zustand, wiederholbare Zuordnung und Aufräumen verwaister Inhalte brauchen ein eigenes Wiederanlaufverfahren. Dieses muss zum konsistenten Backup aus [OPERATIONS.md](OPERATIONS.md) passen. Größenwerte und Prüfwerkzeug bleiben unter OPEN-014 offen; es wird kein neuer Pflichtdienst still vorausgesetzt.

Fehler verwenden `application/problem+json` nach [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html), ergänzt um stabilen Produktcode, Korrelations-ID und ausschließlich zulässige Feldfehler. Keine Eingabewerte, Geheimnisse oder Stacktraces werden zurückgespiegelt. `type` bleibt eine stabile Problemart; lokalisierte Texte dienen der Anzeige. Für ein fremdes Objekt verrät auch das Fehlerdetail nicht dessen Existenz.

| Status | Vorgeschlagener Einsatz |
|---|---|
| 400 | Unlesbare Anfrage, ungültiger Cursor oder ungeeignete Vorbedingung |
| 401 | Fehlende oder ungültige Anmeldung; keine Konten-/Geheimnisdetails |
| 403 / 404 | Aktion auf lesbarem Objekt verboten / Ressource nicht sichtbar oder nicht vorhanden |
| 409 | Fachzustand oder erwartete Fachversion kollidiert; abweichende Wiederholung oder Auftrag noch in Bearbeitung; getrennte Produktcodes |
| 412 / 428 | HTTP-Vorbedingung nicht erfüllt / vorgeschriebene Vorbedingung fehlt |
| 422 | Inhalt formal lesbar, aber erlaubte fachliche Eingaben ungültig; nur sichtbare Feldfehler |
| 429 / 503 | Begrenzung beziehungsweise vorübergehende Nichtverfügbarkeit; Wiederholungsverhalten passend zur Operation, keine automatische Wiederholung unklarer Sendungen |

## Änderungen an Verträgen und parallele KI-Arbeit

Innerhalb `/api/v1` sind nur nachgewiesen kompatible Änderungen vorgesehen. Neue optionale Antwortfelder können kompatibel sein; neue Pflichtangaben, geänderte Bedeutung oder entfernte Felder sind es nicht. Neue Enum-Werte können vorhandene Clients brechen und brauchen eine vereinbarte Behandlung unbekannter Werte. Eine zweite API-Hauptversion und deren Übergangsfrist werden bei einem tatsächlichen Bedarf geplant; ein unbegrenzter Parallelbetrieb wird nicht zugesagt.

Eine spätere Entwicklungsaufgabe benennt vor Beginn den betroffenen Modulvertrag, die Operationen, Eigentümer der Daten, Beispielantworten und Fehlerfälle. Ändert eine KI einen gemeinsam genutzten Vertrag, wird dies zuerst im eigenen PR integriert; abhängige Arbeiten übernehmen dessen Commit. Backend und Frontend pflegen keine unabhängigen Definitionen derselben HTTP-Datenform. Vertragsänderung, Generatoranpassung und notwendige Verbraucheranpassung müssen in der freigegebenen Kombination bestehen, bevor diese ausgeliefert wird. Die zentrale Aufgabenführung bleibt ausschließlich in [TODO.md](../TODO.md) und [DONE.md](../DONE.md).

Vorgeschlagene spätere Nachweise sind: Prüfung der Modulabhängigkeiten, OpenAPI-/Beispielvalidierung, reproduzierbare Erzeugung der Clienttypen, gezielte Integrationstests der Konflikt- und Rechtefälle sowie ein Browserablauf in Deutsch und Englisch. Keine dieser Produktprüfungen ist durch die heutigen Repository-Tests bereits erbracht. Verwendete Sprach-, Framework-, Generator- und Bibliotheksversionen samt Lizenz und Aktualisierungspfad werden unter OPEN-001 festgelegt.

## Prüfszenarien für die spätere Umsetzung

Die folgenden Fälle sind **beschriebene Abnahmevorschläge, keine ausgeführten Anwendungstests**. Synthetische Personen und Organisationen entsprechen den Fachentwürfen.

| Fall | Auslöser | Erwartetes Ergebnis im Vorschlag |
|---|---|---|
| C01 | Ticketmodul importiert interne Zeit-Repositories | Modulprüfung scheitert; zusammengesetzte Ansicht verwendet öffentliche Schnittstellen. |
| C02 | Client behandelt ein optionales Feld ohne Leerzustand | TypeScript-Prüfung beziehungsweise gezielter Vertragstest macht den Fehler sichtbar; kein globales Abschalten der Prüfung. |
| C03 | Portalnutzer fragt Südtechnik-Ticket ab, sieht nur Nordwerk | 404 wie bei unbekannter ID, auch für Historie, Dateien und Links; keine Existenzdetails. |
| C04 | Eine Gruppe erlaubt, andere verbietet das Feld | Feld und Metadaten fehlen; Schreibversuch scheitert vollständig, andere Felder bleiben unverändert. |
| C05 | Lea und Paul speichern parallel dieselbe Ticketdarstellung | Ein erfolgreicher Commit; veraltetes PATCH erhält 412, kein stiller Datenverlust. |
| C06 | Übergangsaktion trägt alte fachliche Ticketversion | 409 mit Fachkonfliktcode; kein irreführender HTTP-ETag-Vergleich gegen eine andere URL. |
| C07 | Zwei gleiche Anlagen treffen gleichzeitig ein | Genau ein Ticket, eine Historienwirkung und ein interner Folgeauftrag; zweite Anfrage erhält laufenden Zustand oder dieselbe Anlagequittung. |
| C08 | Gleicher Wiederholungsschlüssel, veränderter Titel | 409; weder neues Ticket noch Änderung am zuerst erzeugten Ticket. |
| C09 | Zugriff wird nach Anlage vor Wiederholung entzogen | Kein erneuter Schreibvorgang und keine gespeicherten vertraulichen Antworten; 403/404 entsprechend Sichtbarkeit. |
| C10 | Rechteentzug und Fachschreiben konkurrieren | Der zuerst verbindliche Entzug verhindert den alten Schreibauftrag. Die geprüfte Transaktionsreihenfolge wird im Test erzwungen. |
| C11 | Fehler zwischen Mailzuordnung und Ticketanlage | Gesamte lokale Fachtransaktion rückgängig; erneuter Eingang erzeugt ein vollständiges Ergebnis, keinen halben oder doppelten Datensatz. |
| C12 | Worker startet nach Commit, aber vor Zustandsbestätigung neu | Dieselbe interne Fachwirkung entsteht nicht erneut; ein unsicherer SMTP-Versand folgt dem gesonderten Prüfweg. |
| C13 | Cursor wird geändert oder von anderem Konto verwendet | Allgemeine Ablehnung; keine fremde Seite, kein fremder Zähler und kein Recht aus dem Cursor. |
| C14 | Wiki-Entwurf speichern; freigegebene Anforderung ändern; ungeprüften Inhalt derselben Anforderungsrevision zwischen Lesen und Freigabe ändern | Wiki-Entwurf bleibt unveröffentlicht; neue Anforderungsrevision benötigt neue Freigabe und erhält die historische alte Freigabe. Veränderte technische Inhaltsversion verhindert auch bei gleicher fachlicher Revision die Freigabe mit 409. |
| C15 | KI-Bestätigung nach Quellenänderung/Rechteentzug; bereits ausgeführte Aktionskennung nach 24 Stunden mit neuem HTTP-Schlüssel bestätigen | Bei geänderten Quellen/Rechten keine Anwendung/Sendung und keine automatische Bestätigung. Eine bereits verbrauchte Aktionskennung erzeugt unabhängig vom HTTP-Fenster keinen zweiten internen Auftrag. |
| C16 | Unvollständiger Upload oder direkter Downloadversuch | Unfertige Datei bleibt unsichtbar; jede fertige Datei benötigt aktuelle Eltern-/Beitragsrechte. Bereinigung gefährdet keinen gesicherten gültigen Stand. |

## Verbleibende Entscheidungen und Nachweise

Die drei Grundentscheidungen stehen unter OPEN-001, OPEN-002 und OPEN-013 aus. Danach sind die Details dieses Entwurfs als Paket zu prüfen, insbesondere Sitzungs-/Tokenverfahren mit OPEN-006, 24-Stunden-Wiederholungsfenster, Listengrenzen, konkrete Modul- und Transaktionsverträge sowie Kompatibilitätsregeln. Die Vorbereitung weiterer Datenbanken oder Mandanten führt hier zu keiner zusätzlichen Implementierung.

Quellen zu TypeScript, Spring Modulith, OpenAPI und HTTP wurden am 16. Oktober 2026 gelesen. Sie belegen die beschriebenen Werkzeug-/Protokolleigenschaften, nicht die Sicherheit oder Leistungsfähigkeit des geplanten Produkts. Der tatsächliche Prüfstand dieses Dokuments und die Übergabe stehen bei TASK-016 in TODO.md. Fachliche Abnahme und ein gesonderter Implementierungsauftrag bleiben ausstehend.
