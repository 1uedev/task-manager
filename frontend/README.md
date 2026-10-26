# React Oberfläche (TASK-023, TASK-036, TASK-030)

Oberfläche des Task Managers mit React und TypeScript. TASK-023 legte Rahmen, Navigation, Sprachumschaltung, erzeugten API Client und gemeinsame Zustände an, TASK-036 die Gestaltung mit Seitenleiste, Hell und Dunkel, Drag and Drop und dem Erscheinungsbild. TASK-030 macht daraus die durchgängige Oberfläche: Anmeldung mit Pflichtwechsel, Konten, Projekte mit Mitgliedern und Tags, Tickets mit Feldrechten, Historie und Konfliktbehandlung, Suche sowie Gruppen und Rechte, alles gegen die echten Endpunkte. Nur das Board ist weiterhin eine Vorschau mit Beispieldaten (Kanban folgt nach V1, DEC-041). Versionen stammen aus [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md).

## Voraussetzungen und Befehle

Node.js 24.22.0 mit npm 11.19.1 (`engines` und `packageManager` legen das fest) und für die Browserprüfung ein Chromium basierter Browser. Vom Repository-Stamm:

```sh
cd frontend
npm ci --ignore-scripts
npm run verify        # Typen erzeugen, Typprüfung, Unit-Tests, Build
npm run test:browser  # echter Browser, Deutsch und Englisch, Tastatur (braucht den Build)
npm run dev           # lokaler Entwicklungsserver, leitet /api an http://127.0.0.1:8080 weiter
```

`npm run dev` und `npm run preview` leiten `/api` an ein lokal laufendes Backend weiter (Ziel änderbar mit `TASKMANAGER_API`), damit Sitzungs- und CSRF-Cookie auf derselben Herkunft bleiben. Ohne Backend zeigt die Oberfläche das Standardorange und die Seite Erscheinungsbild nur eine Vorschau.

Die Browserprüfung startet einen kleinen Server für `dist/` und nutzt das installierte Chrome; der Server liefert nur Dateien innerhalb von `dist/` aus. Dateipfade der Tests werden mit `fileURLToPath` gebildet. Linux, auch mit Leerzeichen und Umlauten im Verzeichnisnamen, ist geprüft; Windows nicht (die Korrektur nach dem Befund F4 ist dort ungeprüft). Mit `TASKMANAGER_BROWSER=/Pfad/zum/chrome` lässt sich eine andere Chromium Datei angeben. Der Workflow [frontend.yml](../.github/workflows/frontend.yml) führt beides auf einem frischen Runner aus, lehnt übersprungene Tests ab und prüft, dass die erzeugten Typen nicht von Hand abweichen.

Als Browserziel gelten aktuelle Chromium basierte Browser (Entscheidung DEC-056). Andere Browser und feste mobile Mindestansichten sind noch nicht festgelegt; die Prüfung stellt nur sicher, dass bei 320 Pixel Breite nichts horizontal gescrollt werden muss.

## Aufbau

| Pfad | Inhalt |
|---|---|
| `src/app` | Rahmen mit Sprunglink, Seitenleiste, Navigation, Hauptbereich; Anmeldezustand (`session.tsx`); Seitenwahl über den URL-Anker mit Schutz ungespeicherter Eingaben (`route.ts`, `hooks.ts`); kleine Bausteine (`ui.tsx`); Symbole; `useReorder` für Drag and Drop mit Tastatur; `preferences.ts` als einziger Browserspeicher |
| `src/i18n` | Übersetzungskern, Kataloge `de.ts` und `en.ts`, Formatierung |
| `src/brand` | Unternehmensauftritt: Akzentfarbe, Schrift auf dem Akzent, abgeleitete Farben, Hell und Dunkel, Laden vom Server |
| `src/api` | Erzeugte Typen aller Verträge, Client mit CSRF-Token und Sitzungsende, Fachaufrufe mit Laufzeitprüfung (`product.ts`), Fehlerabbildung, synthetische Antworten für Unit-Tests |
| `src/states` | Gemeinsame Zustandsansichten (Laden, leer, Fehler, Erfolg) |
| `src/pages` | Anmeldung und Passwortwechsel, Start, Projekte, Projekt, Ticket, Suche, Konten, Gruppen und Rechte, Erscheinungsbild, Board-Vorschau |
| `tests` | Unit-Tests (`node --test`), `tests/browser` für die Browserprüfung mit einem Testbackend nach den Verträgen (`backend.mjs`) |

Es gibt bewusst nur drei Laufzeitabhängigkeiten: React, React DOM und openapi-fetch. Eine Routing-, Übersetzungs- oder Komponentenbibliothek ist nicht gewählt; das bleibt eine spätere Entscheidung (TECHNICAL_CONTRACTS.md).

## Sprachen

- Texte stehen ausschließlich in den Katalogen. Komponenten übergeben nur Schlüssel; ein Test weist Text zwischen Tags und in Attributen in Komponenten ab.
- Deutsch ist der Quellkatalog, Englisch muss dieselben Schlüssel und Platzhalter haben. Der Typ `Record<MessageKey, Message>` und ein Test erzwingen das.
- **Rückfallregel:** gewählte Sprache, dann Englisch, dann der Schlüssel selbst. Jede Lücke wird gemeldet (Konsole), sodass sie auffällt.
- **Mehrzahl:** Einträge können Formen nach `Intl.PluralRules` haben (`one`, `few`, `many`, `other`). Zahlen in Texten werden in der Schreibweise der Sprache gesetzt.
- **Neue Sprache:** ein Katalog in `src/i18n` und ein Eintrag in `src/i18n/index.ts`, dazu der Anzeigename in allen Katalogen. Der Test `an additional test language works through the registry` belegt den Weg mit Polnisch als Testsprache, ohne Änderung an Komponenten.
- **Wahl:** gespeicherte Wahl, sonst Browsersprache, sonst Deutsch. `localStorage` wird nur in `src/app/preferences.ts` verwendet, und zwar für persönliche Anzeigeeinstellungen dieses Browsers: Sprache (`taskmanager.language`), Hell oder Dunkel (`taskmanager.theme`) und die eigene Reihenfolge von Navigation und Startkarten (`taskmanager.order.navigation`, `taskmanager.order.cards`). Ein Test hält diese Liste fest und stellt sicher, dass kein anderer Code Browserspeicher nutzt. Sitzungsdaten gehören nicht dorthin (DEVELOPMENT_START.md).
- **Darstellung:** Datum, Zeit und Zahlen nutzen `Intl` mit der gewählten Sprache. Ausgetauschte Werte bleiben ISO 8601 beziehungsweise reine Zahlen. Dezimaleingaben werden in der Schreibweise der Sprache gelesen: nur die Trennzeichen dieser Sprache, Gruppen nur zu genau drei Ziffern, nur äußere Leerzeichen werden ignoriert. Mehrdeutige oder fehlerhafte Eingaben wie `1.5` im Deutschen oder `1 5` in jeder Sprache werden abgewiesen statt als 15 gelesen. Sprachen mit Leerzeichen als Gruppentrenner (zum Beispiel Französisch) akzeptieren dafür normale, geschützte und schmale geschützte Leerzeichen. Die Zeitzone der Beispieldarstellung ist fest (`Europe/Berlin`); die Benutzereinstellung folgt mit den Konten.

## Gestaltung und Unternehmensauftritt (TASK-036)

**Rahmen.** Seitenleiste mit Marke, Navigation, Verwaltungsbereich, Sprache und Darstellung; unter 48rem wird sie zur Kopfleiste mit waagrecht scrollbarer Navigation. Hell, Dunkel oder wie das System; die Wahl gilt je Browser.

**Akzentfarbe.** Standard ist ein sattes Orange `#F97316`. Die Schrift auf dem Akzent wählt `textOn` (warmes Dunkel oder Weiß, notfalls Schwarz), damit sie immer mindestens 4,5 erreicht; Weiß auf dem Orange käme nur auf 2,8. Verweise und Akzenttexte nutzen eine aus dem Akzent abgeleitete Farbe, die gegen die Seitenfläche 4,5 erreicht. Ein Test prüft das für 4096 Farben in Hell und Dunkel, ein Browsertest misst jeden sichtbaren Text auf allen Seiten in beiden Darstellungen.

**Erscheinungsbild.** Beim Start fragt die Oberfläche `GET /api/v1/session` und mit vollständiger Anmeldung `GET /api/v1/appearance` ([Vertrag](../contracts/appearance.openapi.yaml)). Antworten werden zur Laufzeit geprüft; das Logo muss ein Pfad auf dem eigenen Server sein. Auf der Seite Erscheinungsbild zeigt die ganze Oberfläche die gewählte Farbe schon vor dem Speichern; beim Verlassen gilt wieder die gespeicherte. Speichern sendet `If-Match` mit der zuletzt gelesenen Version und das CSRF-Token aus dem Cookie `__Host-tm-csrf` (das einzige Cookie, das die Oberfläche liest, `src/api/csrf.ts`). Ein Logo wird per Ablegen oder Dateiauswahl als PNG, JPEG oder WebP bis 512 KiB sofort hochgeladen. Wer das Recht `appearance.manage` nicht hält (die Liste `rights` aus `GET /api/v1/session`), bekommt eine Vorschau mit Hinweis, und nichts wird gesendet; die eigentliche Prüfung macht der Server. Angemeldet wird über die Anmeldeseite (TASK-030).

**Drag and Drop.** Navigation (ohne Verwaltungsbereich), Startkarten, Logoablage und die Board-Vorschau. Jede Verschiebung geht auch mit der Tastatur: Alt und Pfeiltasten auf dem Eintrag, bei Startkarten auf dem Griff, im Board auf der Karte (links und rechts wechseln die Spalte). Der Fokus bleibt auf dem verschobenen Element, und eine Live-Region sagt die neue Position an. Die Board-Vorschau ist als Beispieldaten gekennzeichnet und speichert nichts; Kanban selbst ist nach V1 geplant (DEC-041).

**Vorgänger.** `parseBrand` prüft weiterhin vollständige Auftritte (Name, Logo als Pfad der gleichen Herkunft oder `https`, vier Farben, Kontrast 4,5) und bleibt Grundlage für `brandToCssVariables`. Die synthetischen Beispielauftritte der Startseite aus TASK-023 sind durch die Seite Erscheinungsbild ersetzt.

## API Client und Fehler

Die Typen werden mit `npm run generate` aus [qualification.openapi.yaml](../contracts/qualification.openapi.yaml) (synthetischer Vertrag aus TASK-021, nur noch für Unit-Tests des Clients), [identity](../contracts/identity.openapi.yaml), [appearance](../contracts/appearance.openapi.yaml), [projects](../contracts/projects.openapi.yaml), [tickets](../contracts/tickets.openapi.yaml), [tags](../contracts/tags.openapi.yaml), [search](../contracts/search.openapi.yaml) und [permissions](../contracts/permissions.openapi.yaml) erzeugt und nie von Hand geändert; CI weist Abweichungen und fehlende Dateien ab.

- Antworten werden zur Laufzeit zusätzlich geprüft; erzeugte Typen sind nur Übersetzungszeitprüfung.
- `ETag` (Darstellungsversion, wird als `If-Match` gesendet) und `version` (Fachversion) bleiben getrennt.
- Die Fehlerbehandlung richtet sich nach HTTP-Status und stabilem `code`, nie nach Servertexten. Server `title` und `detail` werden nicht angezeigt. Statusbezogene Meldungen (kein Zugriff, nicht gefunden, Konflikt, Vorbedingung fehlt, nicht erreichbar, Netzwerkfehler) haben Vorrang; bei sonstigen 4xx und 5xx wird der bekannte Code übersetzt, unbekannte Codes fallen auf die Art zurück. Die Kennung `correlationId` wird als Referenz für den Support angezeigt.
- Das Problemformat der Backendgrundlage (`code`, `correlationId`, `instance` als `urn:uuid:`) wird verstanden. **Hinweis für die Produktverträge:** `Problem` im synthetischen Vertrag kennt `correlationId` und `instance` noch nicht und ist mit `additionalProperties: false` geschlossen. Der Produktvertrag muss beide Felder aufnehmen und das Schema für Erweiterungsfelder öffnen (Befund Q5 aus der Gegenprüfung von TASK-021).
- Abbruch: ein älterer Aufruf überschreibt nie eine neuere Auswahl.

## Bedienung und Zugänglichkeit

Umgesetzt und im Browser geprüft: Sprunglink als erster Tabulatorstopp, Landmarken (Seitenleiste als Kopfbereich, Navigation, Hauptbereich, Fußbereich), Drag and Drop mit Tastatur und Ansage, Kontrast aller sichtbaren Texte in Hell und Dunkel, aktuelle Seite per `aria-current`, Fokus auf die Seitenüberschrift nach einem Seitenwechsel, sichtbarer Fokusrahmen, Seitentitel je Seite und Sprache, Dokumentsprache, Beschriftungen mit Pflichtangabe, Hinweise und Fehler per `aria-describedby`, Fehlerzusammenfassung mit Fokus und Sprungverweisen, Fehler nie nur durch Farbe (Symbol, Wort „Fehler“, Rahmen), Statusmeldungen als Live-Regionen, reduzierte Bewegung und erzwungene Farben berücksichtigt.

**Grenzen:** Es gibt keine automatische Barrierefreiheitsprüfung (kein axe oder Lighthouse) und keine Prüfung mit einem Screenreader. Der verbindliche Standard, unterstützte Browser außerhalb von Chromium und mobile Mindestansichten sind laut REQ-UX-001 noch festzulegen. Die Kontrastmessung im Browser nimmt die Hintergrundfarbe der Vorfahren; Verläufe und Bilder misst sie nicht.

## Fachseiten (TASK-030)

**Anmeldung.** Vor der vollständigen Anmeldung zeigt die Oberfläche nur die Anmeldung, den Pflichtwechsel des Passworts oder den Hinweis, dass der Server nicht erreichbar ist. Fehlercodes der Identität werden übersetzt, die Wartezeit nach zu vielen Versuchen genannt. Jede 401 Antwort außerhalb der Sitzungsabfrage, etwa nach Deaktivierung oder Ablauf, führt zurück zur Anmeldung mit Hinweis. Abmelden beendet die Sitzung am Server.

**Rechte.** Welche Bereiche erscheinen, folgt der Rechteliste der Sitzung (`accounts.manage`, `permissions.manage`, `projects.manage`, `appearance.manage`) und bei Tickets `writableFields`. Das ist nur Bedienhilfe; jede Prüfung macht der Server. Projektaktionen nennt die Projektantwort nicht, deshalb wird die Ticketanlage angeboten und eine Ablehnung verständlich gemeldet.

**Tickets.** Lesbare Felder werden gezeigt, schreibbare als Eingabe, verborgene als „für Sie verborgen“. Gesendet werden nur geänderte Felder mit `If-Match`. Bei einem Versionskonflikt lädt die Seite den aktuellen Stand und behält die eigenen Änderungen; bei einer Ablehnung (zum Beispiel weil ein Recht inzwischen entzogen wurde) lädt sie Ticket und Rechte neu. Statuswechsel bietet sie nur entlang der Übergänge an. Historie mit Namen, Werten in der Sprache der Konfiguration und Tagänderungen.

**Ungespeicherte Eingaben.** Ein Seitenwechsel über Navigation oder Adresse fragt nach, Ablehnen lässt Seite und Eingabe stehen; das Schließen des Fensters fragt über den Browser. Nach erfolgreichem Speichern wird nicht gefragt.

**Verwaltung.** Konten anlegen, deaktivieren, wieder aktivieren und neues Einmalpasswort ausstellen; das Einmalpasswort erscheint nur einmal. Gruppen anlegen, Mitglieder, Aktionen je Unternehmen oder Projekt und Feldregeln Lesen und Schreiben setzen; jede Änderung gilt sofort. Wer sich selbst das Recht zur Gruppenverwaltung nimmt, landet auf der Startseite.

**Prüfung.** `tests/browser/app.test.mjs` prüft die Abläufe mit zwei unterschiedlich berechtigten Konten in mehreren Browserkontexten auf einem gemeinsamen Testbackend, `design.test.mjs` Kontrast aller Seiten in Hell und Dunkel, Erscheinungsbild und Drag and Drop. Die echte Browserprüfung gegen Backend und PostgreSQL ist in [docs/screenshots/task-030](../docs/screenshots/task-030/README.md) beschrieben.

**Grenzen.** Typen, Status und Übergänge sind fest und nur lesbar; Gruppen lassen sich nicht umbenennen oder löschen; kein Rechteprotokoll in der Oberfläche; keine Kommentare, Anhänge, Filter nach Status oder Tag in der Suchoberfläche und keine Projektarchivierung. Ein Screenreader und andere Browser als Chromium sind nicht geprüft.
