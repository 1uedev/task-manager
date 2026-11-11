# Unabhängige Prüfung von TASK-098 / PR #291

**Empfehlung: nachbessern.** Vier reproduzierte Befunde betreffen den Schutz der Felddefinitionen, die Personenauswahl, die Größenprüfung und den Browser-Testersatz. Die grundlegende Umsetzung von Konfiguration, Revisionen, Feldrechten und Pflichtprüfung ist vorhanden; grüne Standardtests allein decken diese Fehler nicht ab. Fachliche Abnahme und Bestätigung der Auslegungen bleiben Holger vorbehalten.

- **Sitzung:** `codex-20261110-requirements-fields-01`.
- **Prüfung:** 10./11. November 2026, unabhängige zweite KI, keine Implementierungskorrekturen.
- **Gegenstand:** [PR #291](https://github.com/1uedev/task-manager/pull/291), Branch `claude/project-thread-dyiimb`, Merge auf `main`: `3e729f55e45205c6407a4001ab07fa1bd8c79af8`; Vergleich mit erstem Elterncommit `c11cf7bf60caf28ae03b94ed4213a23906d40a22`.
- **Grundlage:** AGENTS.md, TASK-098 in TODO.md:135–154, DEC-128 in DECISIONS.md:218, backend/README.md:566–576 sowie die referenzierten Anforderungen und Verträge. Die drei fachlichen Regeln sind bestätigt; die Abnahmekriterien und Auslegungen sind als zur Prüfung stehend gekennzeichnet.
- **Änderungen dieses Reviews:** nur dieser Bericht. Zusätzliche Testtreiber, Protokolle, Datenbanken und Screenshots liegen außerhalb des Git-Arbeitsbaums. Kein Produktcode, Testcode, Aufgabenstatus oder Vertrag wurde geändert.

## Befunde

### F1 – Verborgene Felddefinitionen gelangen über die Projekt-API an normale Mitglieder

**Priorität:** hoch, Rechteverletzung; verhindert die Abnahme von Regel 2 und Kriterium 1.

**Datei/Zeile:** `backend/src/main/java/taskmanager/projects/ProjectConfiguration.java:33`; Weitergabe über `backend/src/main/java/taskmanager/projects/web/ProjectController.java:69` und `backend/src/main/java/taskmanager/projects/internal/ProjectService.java:134` / `:468`. Der gefilterte Anforderungsweg steht in `RequirementService.java:632`.

**Nachweis:** Im isolierten HTTP-Test hat ein internes Mitglied `project.read`, `requirement.read` und Leserecht auf `title`, aber keinerlei Regel für `custom.secret` und keine Projektverwaltung. `GET /api/v1/requirements/{id}` liefert korrekt `customFields: []`. Dagegen liefert `GET /api/v1/projects/{projectId}` mit 200 und `manageable: false` die vollständige Definition:

```json
{"key":"secret","name":"Vertrauliche Bewertung","type":"select","required":false,"active":true,"options":[{"key":"internal","name":"Interne Eskalation","active":true}]}
```

Die allgemeine Projektliste enthält diese Definition nicht; der Befund betrifft den Detailendpunkt. Bereits der neue Bestandteil `requirementFields` der ansonsten ungefiltert ausgegebenen Konfiguration eröffnet den zweiten Zugriffsweg.

**Auswirkung:** Name, Schlüssel, Typ und alle Auswahlmöglichkeiten eines nicht lesbaren Felds sind verfügbar. Die Zusage aus DEC-128(e) gilt damit nicht über alle API-Antwortwege. Es wurden auf diesem Weg keine Anforderungswerte festgestellt.

**Vorschlag:** Die normale Projektansicht um leserechtsabhängige Ausgabe der Anforderungsdefinitionen ergänzen beziehungsweise die vollständige Konfiguration einer ausdrücklich berechtigten Verwaltungsansicht vorbehalten. Ein Regressionstest muss denselben eingeschränkt berechtigten Nutzer über Anforderungs- und Projektdetail-API prüfen; zusätzlich die übrigen Konfigurationsausgaben auf diesen neuen Bestandteil kontrollieren.

### F2 – Eigenes Personenfeld verlangt faktisch zusätzlich Schreibrecht auf „Verantwortlich“

**Priorität:** mittel, Funktionsfehler; betrifft Regel 1/2 und Kriterium 1.

**Datei/Zeile:** `frontend/src/pages/RequirementsPage.tsx:405–407`, `:540`, `:573`; serverseitige Schranke in `backend/src/main/java/taskmanager/requirements/internal/RequirementService.java:317–322`; Vertrag `contracts/requirements.openapi.yaml:165–169`.

**Nachweis:** Ein Mitglied hat `requirement.read`, `requirement.edit` sowie Lesen/Schreiben für `custom.reviewer` vom Typ `user`, aber kein Schreibrecht für `owner`. Der vom Editor verwendete Endpunkt `/requirements/projects/{projectId}/owners` antwortet mit **403**. Derselbe Nutzer kann per PATCH eine gültige Person in `custom.reviewer` speichern (**200**). Die Browserprobe zeigt für das eigene Personenfeld nur die Option „Niemand“.

**Auswirkung:** Ein nach seinen eigenen Feldregeln beschreibbares Personenfeld lässt sich in der Oberfläche nicht befüllen. Ist es Pflicht, kann der Nutzer den Übergang nach „Zur Prüfung“ praktisch nicht selbst ermöglichen. Der Ladefehler wird als leere Auswahlliste behandelt.

**Vorschlag:** Die Kandidatenabfrage für eigene Personenfelder mit deren tatsächlichen Rechten autorisieren, etwa durch eine passende Erweiterung oder einen eigenen Endpunkt. `owner`-Rechte dürfen dadurch nicht pauschal erweitert werden. Vertrag und Oberfläche anpassen und Ladefehler anzeigen. Backend- und Browsertest mit beschreibbarem Personenfeld und unveränderbarem `owner` ergänzen.

### F3 – Gültige Einzelwerte überschreiten die neue Speichergrenze und führen zu HTTP 500

**Priorität:** mittel, Speicher- und Validierungsfehler; betrifft Regel 1 und Kriterium 4.

**Datei/Zeile:** `backend/src/main/resources/db/migration/V0060__requirements_custom_fields.sql:5–6`; `backend/src/main/java/taskmanager/requirements/internal/RequirementService.java:674–705`; Speicherung in `RequirementRepository.java:223–232`. Einzelgrenzen: `ProjectConfiguration.java:59`, `:65`, `:466–480`.

**Nachweis:** 30 erlaubte `longtext`-Felder konfigurieren. Eine Anforderung anlegen und anschließend einzeln je Feld 20.000 Eurozeichen speichern. Jeder Request bleibt klein und jeder Wert entspricht der Feldprüfung. Die ersten 17 Speicherungen funktionieren; die 18. (`long17`) liefert **500 `server.internal_error`**, weil die zusammenhängende JSON-Spalte mehr als 1.048.576 UTF-8-Bytes erreicht. Die Datenbank verweigert den Check-Constraint; die Serviceprüfung kennt nur Einzelwerte.

**Auswirkung:** Eine nach den veröffentlichten Einzelgrenzen zulässige Bearbeitung ist nicht speicherbar und endet mit einem internen Fehler statt einer verständlichen Validierung. Der fehlgeschlagene Schreibvorgang wird zurückgerollt; ein Verlust bereits gespeicherter Werte wurde nicht beobachtet.

**Vorschlag:** Speichergrenze und zulässige Kombination der Feldwerte aufeinander abstimmen. Soll eine Gesamtgrenze gelten, vor dem Schreiben den vollständig serialisierten neuen Bestand prüfen, mit dokumentiertem 400-Code und übersetztem Fehlertext. Mehrbytezeichen sowie ein kleines inkrementelles PATCH an einem bereits großen Bestand in einen Regressionstest aufnehmen.

### F4 – Neuer Browser-Testersatz akzeptiert Werte, die der Server zurückweist

**Priorität:** mittel, unzuverlässige Prüfbasis; betrifft Kriterium 4.

**Datei/Zeile:** `frontend/tests/browser/backend.mjs:2046–2068` und `:2295–2299`; maßgebliche Serverprüfung in `backend/src/main/java/taskmanager/requirements/web/RequirementController.java:407–430` und `ProjectConfiguration.java:466–518`.

**Nachweis:** Gleiche Eingaben mit passenden Feldrechten gegen echten HTTP-Server und `requirementCall` des Testersatzes:

| Eingabe | Echter Server | Testersatz |
|---|---|---|
| `{"customFields":null}` | 400, `customFields.invalid` | 200, unverändert |
| Datumsfeld `2026-02-30` | 400, `custom.day.invalid` | 200, gespeichert |
| Ganzzahl `9007199254740992` | 400, `custom.count.invalid` | 200, gespeichert |
| Text `"  hello  "` | 200, kanonischer Wert `"hello"` | 200, Leerzeichen bleiben erhalten |

**Auswirkung:** Browserprüfungen können erfolgreiche Speicherungen und abweichende Änderungs-/Historienzustände bestätigen, die im echten System so nicht auftreten. Der neue Prüfcode übernimmt weder die echte Kalenderprüfung noch die Ganzzahlgrenze oder die Kanonisierung vollständig; `?? {}` verschluckt außerdem die ungültige Null-Feldkarte.

**Vorschlag:** Die neuen Regeln des Testersatzes an den Server angleichen. Eine kleine gemeinsame Fallmatrix gegen beide Wege ausführen, einschließlich deaktivierter Optionen, Personenmitgliedschaft, leerer Werte und kanonisch unveränderter Werte. Die 259 grünen Browsertests ersetzen diesen Abgleich nicht.

## Hinweise und Bewertung der Auslegungen

### H1 – Pflicht gilt tatsächlich nur beim Übergang, nicht während der gesamten Prüfung

**Datei/Zeile:** `DECISIONS.md:218`; `RequirementService.java:402–427`, `:450–463`, `:470–492`.

**Auswirkung:** Nach erfolgreichem Übergang nach „Zur Prüfung“ kann ein eigenes Pflichtfeld wieder geleert werden; eine Inhaltsänderung vor Freigabe lässt den Prüfstatus bestehen. Die Freigabe prüft Leserechte, aber nicht erneut Pflichtwerte. Dasselbe gilt bei nachträglicher Konfiguration eines Pflichtfelds. Das folgt aus DEC-128(a) zusammen mit DEC-126(a) und wird deshalb nicht als Verletzung der ausdrücklich bestätigten Übergangsregel gewertet.

**Vorschlag:** Bei der fachlichen Bestätigung diese Folge ausdrücklich berücksichtigen. Falls „freigegeben“ immer vollständige Pflichtwerte voraussetzen soll, ist eine weitere fachliche Regel mit Prüfung bei Freigabe oder Rückkehr in den Entwurf nötig; sie ist nicht stillschweigend Teil dieses Reviews.

| Auslegung | Bewertung |
|---|---|
| **(a)** Pflicht nur beim Übergang nach „Zur Prüfung“ | Entspricht Regel 2; Anlegen und Speichern dürfen leere Pflichtwerte behalten. H1 ist bei Bestätigung zu beachten. `false` zählt korrekt als gesetzter Booleanwert. |
| **(b)** Aktives Pflichtfeld benötigt Lesen und Schreiben, sonst wertunabhängig 409 | Sinnvoll und dem Ticketprinzip entsprechend. Die Prüfung läuft vor der Auswertung fehlender Werte und nennt weder versteckten Wert noch Feld. Kein technischer Einwand. |
| **(c)** Freigeben verlangt Lesen aller eigenen Felder, auch deaktivierter | Technisch umgesetzt, konservative Freigabeschranke. Sie gilt auch für leere Felder und später ergänzte Definitionen und kann bisher mögliche Freigaben blockieren. Fachlich ausdrücklich bestätigen; keine implizite Rechtevergabe. |
| **(d)** Bestehende Werte deaktivierter Felder/Optionen bleiben erhalten | Technisch stimmig: identische kanonische Werte bleiben zulässig, neue Werte werden abgelehnt, Leeren bleibt möglich. Alte Revisionen behalten ihre Werte. Zusätzliche HTTP-Proben für deaktivierte Optionen und beide Sperrreihenfolgen bestanden. |
| **(e)** Nur lesbare Definitionen, Werte, Ereignisse und Bearbeitende | In den Anforderungsantworten umgesetzt: Werte und Definitionen gefiltert, feldbezogene Ereignisse ausgeblendet, `editors` berücksichtigt nur lesbare Änderungen. Wegen F1 ist die Zusage über alle Antwortwege noch nicht erfüllt. |

Revisions-/Statusmetadaten wie `revision_opened`, `openedBy`, `savedBy` und `updatedBy` bleiben nach dem bestehenden Vertrag sichtbar. Die bestätigte Einschränkung der Liste `editors` ist davon zu unterscheiden; dieses Review erweitert TASK-098 nicht um eine allgemeine Anonymisierung der Metadaten.

### H2 – Grenzen des Ausführungsnachweises

**Datei/Zeile:** `backend/scripts/lifecycle-check.sh:1–6`, `:89–91`; `backend/src/test/java/taskmanager/TestDatabase.java:20–22`; `docs/DEMO.md:26–39`.

**Auswirkung:** Die lokale Umgebung ist Windows mit PostgreSQL 16.15. Der im Repository gepinnte PostgreSQL-18.6-Container, das Bash-Lifecycle-Skript und das Docker-Vorführimage sind damit nicht automatisch geprüft. Die Docker-Engine war nicht erreichbar. Die lokale Demo ist ein zusätzlicher Nachweis für Seed, Server und Oberfläche, kein Image-Abnahmetest.

**Vorschlag:** Nach Korrektur der Befunde den vorhandenen CI-/Linux-Lifecycle-Lauf und das Vorführimage ergänzend prüfen. Kein Wechsel der bestätigten Produktversionen wird aus dem lokalen PostgreSQL-16-Lauf abgeleitet.

## Abdeckung der angeforderten Prüfung

| Bereich | Ergebnis |
|---|---|
| **Regel 1: Definitionen und Konfiguration** | `requirementFields` verwendet dieselbe `CustomField`-Struktur und `customFieldChecks` wie Tickets; alle sieben Typen geprüft. Normalisierung, Entwurf und Veröffentlichung laufen über die bestehende Projektkonfiguration. `incompatibilities`/`kept` verbieten Entfernen veröffentlichter Felder/Optionen und Typwechsel mit `requirementFields.*`. F2 und F3 begrenzen die Nutzbarkeit. |
| **Regel 2 / Kriterium 1: Revision, Historie, Vergleich, Freigabe** | Eigene Werte liegen je Revision; eine tatsächliche Änderung nach Freigabe öffnet Revision n+1 im Entwurf. Gleichbleibende Werte öffnen keine neue Revision. Alte Revision bleibt erhalten. `changed` nennt `custom.<key>`, vorherigen und neuen Wert. Ansicht, Revision, Vergleich und Freigabedialog berücksichtigen lesbare eigene Felder. F1 und F2 verhindern vollständige Abnahme. |
| **Kriterium 2: Pflichtprüfung** | Aktive Pflichtfelder werden beim Übergang geprüft; fehlender Wert liefert 400 `request.invalid` mit `custom.<key>.required_for_review`. Die Oberfläche nennt den Feldnamen. Rechtekonflikt wird zuerst mit 409 beantwortet. |
| **Rechteverwaltung** | `PermissionsController.setField` unterscheidet Ressourcentyp und verlangt für `custom.<key>` ein Projekt sowie ein Feld der aktuellen Konfiguration. Unbekannte Schlüssel sind 400. `RequirementService.access` liest eigene Feldkennungen erst nach dem projektbezogenen Rechteaufruf. Freigabe ergänzt alle eigenen Felder zu den lesepflichtigen Inhaltsfeldern. F1 betrifft einen zusätzlichen Ausgabeweg. |
| **Gleichzeitigkeit** | Rechteprüfung hält Projektsperre; Konfiguration wird danach gelesen. Veröffentlichung sperrt das Projekt exklusiv, Anforderungsschreiben hält die geteilte Projektsperre vor der Anforderungssperre. Zwei zusätzliche HTTP-Proben hielten gezielt die reale Veröffentlichungs- bzw. Speichertransaktion kurz an: Veröffentlichung zuerst → nachfolgender neuer Wert erhält 400 `.inactive`; Speicherung zuerst → Speicherung 200, anschließende Deaktivierung 200, vorhandener Wert bleibt erhalten. |
| **Deaktivierte Felder und Optionen** | Bestehender kanonischer Wert bleibt erlaubt, neuer Wert wird abgelehnt. Entfernen einer veröffentlichten Option liefert 409 `.option_removed`; ein Wert einer alten freigegebenen Revision bleibt lesbar. Ein solcher Wert wird nicht allein durch spätere Deaktivierung entfernt. |
| **Migration** | V0060 ergänzt `custom` mit nichtleerem Standard `{}` für bestehende Revisionen. Repository liest/schreibt die Spalte und schützt freigegebene Revisionen weiterhin über `approved_at is null`. Migrationslisten in Lifecycle-Skript und DatabaseFoundationTest sowie Zählung 57 in ApplicationLifecycleTest wurden konsistent ergänzt. Die neue Bytegrenze hat F3. |
| **Verträge** | Versionsstände 0.3.0 / 0.2.0 / 0.5.0 stimmen. Neue Definitionen, Werte, Feldkennungen und Fehler sind beschrieben; generierte TypeScript-Dateien entsprechen der Generierung. Bestehende Routen werden erweitert, keine neue Anforderungsroute. Die Routentests vergleichen Methoden/Pfade; sie sind keine vollständige JSON-Schema-Prüfung jeder Antwort. HTTP-Antworten der Zusatzproben wurden mit den neuen Vertragsbestandteilen abgeglichen; F2 betrifft die fehlende Kandidatenabfrage für eigene Personenfelder, F3 die nicht fachlich abgefangene Gesamtgrenze. |
| **Oberfläche / Englisch** | Separater Konfigurationsbereich samt Pflichtkennzeichnung vorhanden. Die vorhandenen Tests decken Veröffentlichung, Pflichtfeld, Revision, Freigabedialog, Historie, Vergleich und ausgeblendete Felder ab. Zusätzliche Browserprobe: bei Versionskonflikt bleibt eigener Wert `first=11`, unberührtes Feld übernimmt fremden Wert `second=22`; anschließend Speichern erfolgreich. Englischer Pflichtfehler zeigt `first: is needed for "In review".`. F2 bleibt offen. |
| **Regel 3 / Kriterium 3: Demo** | `seed_requirements` und Beschreibung entsprechen dem vorgesehenen Ablauf. Unit-Tests prüfen Konfiguration, Regeln, drei Kriterien und Aufrufreihenfolge. Der unveränderte vollständige `seed --profile demo` lief zusätzlich gegen den echten lokalen Server erfolgreich. Sichtprüfung und Endzustand: siehe Ausführungsprotokoll unten. |
| **Kriterium 4: Tests und Verträge** | Standardprüfungen und zusätzliche Gegenproben sind unten aufgeführt. Wegen F3/F4 und der expliziten Laufzeitgrenzen noch nicht vollständig abnahmefähig. |
| **Umfang** | Keine fachfremden Produktänderungen im Merge gefunden. Die Generalisierung der Ticket-Feldprüfungen und der gemeinsame Feldeditor dienen TASK-098. Mitgeliefert wurde außerdem der bereits vorhandene Reviewbericht zu TASK-097; das ist Dokumentationsbeilage, keine neue Umsetzung. Keine erneute Bewertung oder Änderung von TASK-097 in diesem Review. |

## Ausgeführte Prüfungen

Die Befundproben liegen getrennt vom unveränderten Repository. Sie bestätigen teilweise ausdrücklich das fehlerhafte Ist-Verhalten; „Probe bestanden“ bedeutet deshalb nicht „Produkt fehlerfrei“.

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py` | OK; ausgeführt mit vorhandenem Python 3.12, weil der Windows-Alias `python3` nicht startbar war. |
| `python3 scripts/task_board.py --check` | OK, Aufgabenübersicht aktuell. |
| `python3 -m unittest discover -s tests -v` | 42 Tests, OK, davon 1 übersprungen: Umschalten der lokalen Zeitzone ist im Test nur unter Unix vorgesehen. UTF-8 für den Windows-Lauf gesetzt. |
| Backend `./mvnw verify` | **BUILD SUCCESS: 583 Tests, 0 Fehler, 0 übersprungen.** Darunter RequirementApiTest (17), die drei betroffenen Routentests, DatabaseFoundationTest (4) und ApplicationLifecycleTest (3). Windows-Aufruf `mvnw.cmd`, JDK 25.0.4.1, eigene PostgreSQL-16.15-Datenbank, explizite `taskmanager.test.*`-Parameter. |
| Frontend `npm ci` | OK, Node 24.22.0 / npm 11.19.1; 111 Pakete installiert, keine gemeldete Schwachstelle. |
| Frontend `npm run verify` | OK: Vertragsgenerierung, TypeScript, 100 Unit-Tests und Build. Build meldet Hinweise zu Modul-Direktiven und Bundlegröße, keine Fehler. |
| Frontend `npm run test:browser` | 259 Tests in 39 Suites, alle bestanden, keiner übersprungen. |
| Zusätzliche HTTP-Proben | Sechs Szenarien abgeschlossen: Personenfeld ohne `owner`, sieben Typen/inaktive Option/alte Revision/Optionsentfernung, Gesamtgröße, beide Sperrreihenfolgen, vier Validierungsfälle, verborgene Definition über Projektdetail. F1–F3 reproduziert; die positiven Szenarien bestanden. |
| Zusätzliche Browser-/Testersatz-Proben | F2 und vier Abweichungen von F4 reproduziert; Konfliktübernahme eigener Felder und englischer Pflichtfehler bestanden. |
| Echte synthetische Demo | Seed vollständig erfolgreich: 4 interne Demokonten, 2 Portalkonten, 4 Projekte, 43 Tickets, Wiki und Anforderungen. Echte HTTP-Antworten bestätigen ANF-1 mit drei Kriterien, Revision 1 freigegeben und Aufwand 3, Revision 2 im Entwurf und Aufwand 5; Ticketlink HELP-1 bleibt auf Revision 1, Wikilink nennt „Serviceanfrage ergänzen“. Gebautes Frontend gegen den echten Server: HELP-1 zeigt „Neuere Revision 2 vorhanden“; Screenshot visuell geprüft. Anmeldung für diese Sichtprüfung direkt über die echte Sitzungs-API, übrige Browser-API-Anfragen durch lokalen Testproxy weitergeleitet; keine Antwortattrappen. |
| `backend/scripts/lifecycle-check.sh` | Quelltext und Migrationsliste geprüft; Bash-Skript selbst nicht ausgeführt. ApplicationLifecycleTest wird getrennt im Maven-Ergebnis ausgewiesen. |
| Docker-Vorführimage / gepinnter PostgreSQL-Container | Nicht gestartet: keine erreichbare Docker-Engine. Native Demo mit echtem Backend und PostgreSQL 16 ist ausdrücklich kein Containerlauf. |
| `git diff --check` und Abschlussstatus | OK; abschließend bestätigt: keine Änderung an versionierten Dateien, ausschließlich dieser neue Bericht im Arbeitsbaum. Auch der neue Bericht wurde auf Whitespacefehler geprüft. |

**Laufumstände:** Der erste Backend-Versuch scheiterte an einem leeren Testpasswort in der Review-Umgebung. Ein Toolprozessabbruch beendete danach auch die Testdatenbank; ein weiterer Versuch während ihrer Wiederherstellung war kein gültiger Produktnachweis. Der korrekt konfigurierte Lauf wurde nach bestätigter Datenbankbereitschaft begonnen und über eine nächtliche Rechnerunterbrechung hinweg fortgesetzt. Diese Umweltfehler sind keine Befunde gegen TASK-098. Bei der Definitionsprobe wurde eine zunächst falsche Erwartung an die Projektliste korrigiert und der tatsächliche Detailendpunkt-Befund separat erneut bestätigt.

**Lokale Belege, nicht eingecheckt:** `repository.log`, `python-tests.log`, `npm-ci.log`, `frontend-verify.log`, `browser-all.log`, `api-probes-fixed.log`, `definitions-probe.log`, `browser-probes-final.log`, `conflict-english.log`, `demo-seed.log` `backend-verify-ready.log`, `demo-browser.log`, `demo-browser.json` und `demo-help1.png`. Sie enthalten die oben beschriebenen Ausgaben; keine Zugangsdaten werden in diesen Bericht übernommen.

Die Abnahmeempfehlung bleibt **nachbessern**: F1–F4 beheben, gezielt nachprüfen und die Auslegungen mit den genannten Konsequenzen fachlich bestätigen.
