# React Grundfläche (TASK-023)

Oberfläche des Task Managers mit React und TypeScript. Dieser Stand enthält Rahmen, Navigation, Sprachumschaltung Deutsch und Englisch, Unternehmensauftritt, einen aus dem Vertrag erzeugten API Client und die gemeinsamen Zustände für Laden, leere Daten, Fehler, fehlende Rechte und Konflikte. Er enthält **keine Fachfunktion**: keine Anmeldung, keine Projekte, Tickets, Tags oder Suche. Alle Daten auf den Seiten sind synthetisch; es ist keine Backendfunktion angebunden. Versionen stammen aus [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md).

## Voraussetzungen und Befehle

Node.js 24.22.0 mit npm 11.19.1 (`engines` und `packageManager` legen das fest) und für die Browserprüfung ein Chromium basierter Browser. Vom Repository-Stamm:

```sh
cd frontend
npm ci --ignore-scripts
npm run verify        # Typen erzeugen, Typprüfung, Unit-Tests, Build
npm run test:browser  # echter Browser, Deutsch und Englisch, Tastatur (braucht den Build)
npm run dev           # lokaler Entwicklungsserver
```

Die Browserprüfung startet einen kleinen Server für `dist/` und nutzt das installierte Chrome; der Server liefert nur Dateien innerhalb von `dist/` aus. Dateipfade der Tests werden mit `fileURLToPath` gebildet. Linux, auch mit Leerzeichen und Umlauten im Verzeichnisnamen, ist geprüft; Windows nicht (die Korrektur nach dem Befund F4 ist dort ungeprüft). Mit `TASKMANAGER_BROWSER=/Pfad/zum/chrome` lässt sich eine andere Chromium Datei angeben. Der Workflow [frontend.yml](../.github/workflows/frontend.yml) führt beides auf einem frischen Runner aus, lehnt übersprungene Tests ab und prüft, dass die erzeugten Typen nicht von Hand abweichen.

Als Browserziel gelten aktuelle Chromium basierte Browser (Entscheidung DEC-056). Andere Browser und feste mobile Mindestansichten sind noch nicht festgelegt; die Prüfung stellt nur sicher, dass bei 320 Pixel Breite nichts horizontal gescrollt werden muss.

## Aufbau

| Pfad | Inhalt |
|---|---|
| `src/app` | Rahmen mit Sprunglink, Kopfbereich, Navigation, Hauptbereich; einfache Seitenwahl über den URL-Anker |
| `src/i18n` | Übersetzungskern, Kataloge `de.ts` und `en.ts`, Formatierung, Speicherung der Sprachwahl |
| `src/brand` | Unternehmensauftritt: Prüfung, Standard, Anwendung auf die Oberfläche |
| `src/api` | Erzeugte Typen, Client, Fehlerabbildung, synthetische Antworten |
| `src/states` | Gemeinsame Zustandsansichten (Laden, leer, Fehler, Erfolg) |
| `src/pages` | Startseite, Zustandsbeispiele, Beispielformular |
| `tests` | Unit-Tests (`node --test`), `tests/browser` für die Browserprüfung |

Es gibt bewusst nur drei Laufzeitabhängigkeiten: React, React DOM und openapi-fetch. Eine Routing-, Übersetzungs- oder Komponentenbibliothek ist nicht gewählt; das bleibt eine spätere Entscheidung (TECHNICAL_CONTRACTS.md).

## Sprachen

- Texte stehen ausschließlich in den Katalogen. Komponenten übergeben nur Schlüssel; ein Test weist Text zwischen Tags und in Attributen in Komponenten ab.
- Deutsch ist der Quellkatalog, Englisch muss dieselben Schlüssel und Platzhalter haben. Der Typ `Record<MessageKey, Message>` und ein Test erzwingen das.
- **Rückfallregel:** gewählte Sprache, dann Englisch, dann der Schlüssel selbst. Jede Lücke wird gemeldet (Konsole), sodass sie auffällt.
- **Mehrzahl:** Einträge können Formen nach `Intl.PluralRules` haben (`one`, `few`, `many`, `other`). Zahlen in Texten werden in der Schreibweise der Sprache gesetzt.
- **Neue Sprache:** ein Katalog in `src/i18n` und ein Eintrag in `src/i18n/index.ts`, dazu der Anzeigename in allen Katalogen. Der Test `an additional test language works through the registry` belegt den Weg mit Polnisch als Testsprache, ohne Änderung an Komponenten.
- **Wahl:** gespeicherte Wahl, sonst Browsersprache, sonst Deutsch. `localStorage` wird ausschließlich für diese Wahl unter `taskmanager.language` verwendet; ein Test stellt sicher, dass kein anderer Code Browserspeicher nutzt. Sitzungsdaten gehören nicht dorthin (DEVELOPMENT_START.md).
- **Darstellung:** Datum, Zeit und Zahlen nutzen `Intl` mit der gewählten Sprache. Ausgetauschte Werte bleiben ISO 8601 beziehungsweise reine Zahlen. Dezimaleingaben werden in der Schreibweise der Sprache gelesen: nur die Trennzeichen dieser Sprache, Gruppen nur zu genau drei Ziffern, nur äußere Leerzeichen werden ignoriert. Mehrdeutige oder fehlerhafte Eingaben wie `1.5` im Deutschen oder `1 5` in jeder Sprache werden abgewiesen statt als 15 gelesen. Sprachen mit Leerzeichen als Gruppentrenner (zum Beispiel Französisch) akzeptieren dafür normale, geschützte und schmale geschützte Leerzeichen. Die Zeitzone der Beispieldarstellung ist fest (`Europe/Berlin`); die Benutzereinstellung folgt mit den Konten.

## Unternehmensauftritt

`parseBrand` nimmt nur eine vollständige Einstellung an: Name bis 80 Zeichen, Logo als Pfad der gleichen Herkunft (`/…`) oder `https`, vier Farben als `#rrggbb`. Das Logo wird gegen eine feste Basis geparst und als normalisierter Wert gespeichert; Rückwärtsschrägstriche, Leer- und Steuerzeichen sowie Adressen mit Zugangsdaten werden abgewiesen, ein Pfad darf die Herkunft nicht wechseln. Texte und Hintergrund sowie Schrift auf Primärfarbe müssen mindestens Kontrast 4,5 erreichen. Alles andere lässt den bisherigen Auftritt bestehen; es gibt keine freien Stilangaben und keinen ausführbaren Inhalt. Farben wirken über CSS-Variablen auf Kopfbereich, Schaltflächen und Hintergrund; Zurücksetzen stellt den Standard her. Die übrigen Farben auf der Seite (Hinweise, Rahmen, Fehler, Fokus, Verweise) werden aus den vier Farben abgeleitet (`derivePalette`) und fallen im Zweifel auf die Textfarbe zurück, damit jede dargestellte Kombination ihren Mindestkontrast erreicht (Text 4,5, Rahmen und Fokus 3). Das gilt für die Fläche der Seite; feste helle Meldungsflächen behalten feste Farben. Ein dunkler Beispielauftritt ist auf der Startseite wählbar und im Browser geprüft. Die Verwaltung durch Administratoren und die Einbindung in Anmeldeseite, Portal und E-Mails gehören zu späteren Aufgaben. Die Startseite wendet zur Vorführung einen synthetischen Auftritt an.

## API Client und Fehler

Die Typen werden mit `npm run generate` aus [contracts/qualification.openapi.yaml](../contracts/qualification.openapi.yaml) erzeugt und nie von Hand geändert; CI weist Abweichungen ab. Das ist der synthetische Vertrag aus TASK-021, **nicht die Produkt-API**. Die Produkt-API erhält einen eigenen Vertrag unter `/api/v1` mit den Fachaufgaben; dann wird der Client daraus erzeugt.

- Antworten werden zur Laufzeit zusätzlich geprüft; erzeugte Typen sind nur Übersetzungszeitprüfung.
- `ETag` (Darstellungsversion, wird als `If-Match` gesendet) und `version` (Fachversion) bleiben getrennt.
- Die Fehlerbehandlung richtet sich nach HTTP-Status und stabilem `code`, nie nach Servertexten. Server `title` und `detail` werden nicht angezeigt. Statusbezogene Meldungen (kein Zugriff, nicht gefunden, Konflikt, Vorbedingung fehlt, nicht erreichbar, Netzwerkfehler) haben Vorrang; bei sonstigen 4xx und 5xx wird der bekannte Code übersetzt, unbekannte Codes fallen auf die Art zurück. Die Kennung `correlationId` wird als Referenz für den Support angezeigt.
- Das Problemformat der Backendgrundlage (`code`, `correlationId`, `instance` als `urn:uuid:`) wird verstanden. **Hinweis für die Produktverträge:** `Problem` im synthetischen Vertrag kennt `correlationId` und `instance` noch nicht und ist mit `additionalProperties: false` geschlossen. Der Produktvertrag muss beide Felder aufnehmen und das Schema für Erweiterungsfelder öffnen (Befund Q5 aus der Gegenprüfung von TASK-021).
- Abbruch: ein älterer Aufruf überschreibt nie eine neuere Auswahl.

## Bedienung und Zugänglichkeit

Umgesetzt und im Browser geprüft: Sprunglink als erster Tabulatorstopp, Landmarken (Kopfbereich, Navigation, Hauptbereich, Fußbereich), aktuelle Seite per `aria-current`, Fokus auf die Seitenüberschrift nach einem Seitenwechsel, sichtbarer Fokusrahmen, Seitentitel je Seite und Sprache, Dokumentsprache, Beschriftungen mit Pflichtangabe, Hinweise und Fehler per `aria-describedby`, Fehlerzusammenfassung mit Fokus und Sprungverweisen, Fehler nie nur durch Farbe (Symbol, Wort „Fehler“, Rahmen), Statusmeldungen als Live-Regionen, reduzierte Bewegung und erzwungene Farben berücksichtigt.

**Grenzen:** Es gibt keine automatische Barrierefreiheitsprüfung (kein axe oder Lighthouse) und keine Prüfung mit einem Screenreader. Der verbindliche Standard, unterstützte Browser außerhalb von Chromium und mobile Mindestansichten sind laut REQ-UX-001 noch festzulegen. Die Kontraste der festen Standardfarben im Stylesheet wurden rechnerisch geprüft, nicht für jede Kombination automatisch.
