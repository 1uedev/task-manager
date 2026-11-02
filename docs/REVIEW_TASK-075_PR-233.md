# Unabhängige Prüfung TASK-075 · PR #233

**Prüfsitzung:** codex-20261102-portal-review-01

**Stand:** 2. November 2026, Commit `eb1e5875fb7d68c619fd1f97cc50a56bfa051446`, Branch `claude/project-thread-houava`, [PR #233](https://github.com/1uedev/task-manager/pull/233). Der PR war beim Prüfbeginn bereits gemergt; der genannte Ergebniscommit wurde gesondert ausgecheckt.

Maßstab: AGENTS.md, TASK-075 in TODO.md, DEC-105, „Portalkonten, Sicht und Rechte (TASK-075)“ in backend/README.md und Organisationssicht/Fälle 1–8 in docs/PERMISSIONS.md. Anlegen, Antworten, Statusnamen, kundenlesbare Anhänge und Portaltags wurden nicht als Lieferumfang dieser Aufgabe bewertet. Im Prüfcheckout wurde ausschließlich dieser Bericht ergänzt; kein Produktcode, Testcode oder Aufgabenstatus geändert.

## Befunde

### F1 · P2 · Fremdes Ticket ist bei einer Sperre von einer unbekannten Nummer unterscheidbar

**Datei/Zeile:** `backend/src/main/java/taskmanager/helpdesk/internal/JdbcTicketOrganizations.java:31–32`; Aufruf durch `tickets/internal/TicketService.java:112–128`.

Die Detailabfrage sperrt die Kundenzuordnung allein über die Ticketkennung. Erst danach vergleicht der Rechtekern die gelesene Organisation mit der des Portalkontos. Bei einer fremden, gesperrten Zuordnung wartet der Aufruf deshalb auf Daten, die er gar nicht lesen darf.

**Nachstellung:** Nordwerk und Südtechnik in einem gemeinsamen Projekt einrichten. Mit einem Nordwerk-Portalkonto eine unbekannte Ticketnummer abfragen: 404. Dann in einer zweiten Transaktion die Zeile von `helpdesk_ticket_customer` eines Südtechnik-Tickets mit `FOR UPDATE` halten und dieselbe Art Portal-Detailabfrage für dieses Ticket senden.

**Beobachtet:** Die unbekannte Nummer antwortet 404; die fremde Nummer nach rund 5,1 Sekunden mit **503 `ticket.busy`**. Ohne Sperre antwortet auch die fremde Nummer 404. Damit besteht ein Existenz-/Aktivitätskanal über die Organisationsgrenze. Fremde Ticketinhalte wurden dabei nicht ausgegeben.

**Vorschlag:** Die erwartete Organisation in den Port und in die sperrende SQL-Bedingung aufnehmen, etwa `ticket_id = ? AND customer_id = ? FOR SHARE`. Ein bloßer ungesperrter Vorvergleich genügt nicht. Den Vergleich „fremd gegen unbekannt“ auch mit einer gehaltenen fremden Zeile testen.

### F2 · P2 · Projektliste und Kontenänderung sperren Projekte in entgegengesetzter Reihenfolge

**Datei/Zeile:** `backend/src/main/java/taskmanager/projects/internal/ProjectService.java:386–387`, `ProjectRepository.java:53–57` und `backend/src/main/java/taskmanager/PortalAccounts.java:140–146`.

Die Portal-Projektliste übernimmt die PostgreSQL-Sortierung `ORDER BY p.id`; die Kontenverwaltung sortiert mit Java `UUID.compareTo`. Bei UUIDs mit verschiedenem höchstem Vorzeichenbit sind diese Reihenfolgen entgegengesetzt. Die Projektliste nimmt außerdem keine gemeinsame Rechtesperre, die sie gegenüber der Kontenänderung serialisieren würde.

**Nachstellung:** Zwei Projekte A und B wählen: A mit positivem, B mit negativem höchstem UUID-Bit. Zwei Portalkonten derselben Organisation beiden Projekten zuordnen. Eine Mitgliedszeile des zweiten Kontos in B extern sperren. Über die Kontenverwaltung beide Projekte dieses Kontos entfernen: Der Aufruf hält B und wartet an der Mitgliedszeile. Nun mit dem ersten Konto die Portal-Projektliste lesen: Sie hält A und wartet auf B. Die Mitgliedszeile freigeben; die Kontenänderung will anschließend A sperren.

**Beobachtet:** PostgreSQL zählt einen zusätzlichen Deadlock. Die Kontenänderung antwortet im Versuch 200, die Projektliste **503 `project.busy`**. Kein Datenverlust und keine 500 beobachtet; die Übersetzung von 40P01 funktioniert, die Verklemmung besteht trotzdem.

**Vorschlag:** Eine tatsächlich einheitliche Projekt-Sperrordnung verwenden und die Portal-Lesepfade in das gemeinsame Rechte-/Identitätsprotokoll einbeziehen. Ein Test mit UUIDs auf beiden Seiten des Vorzeichenbits muss die Reihenfolge absichern.

### F3 · P2 · Wartende Portal-Projektliste liefert nach abgeschlossener Deaktivierung noch Daten

**Datei/Zeile:** `backend/src/main/java/taskmanager/projects/internal/ProjectService.java:381–390`.

`portalProjects` prüft nur `actor.external()` und Mitgliedschaften. Die aktuelle Sitzung und das Konto werden in dieser Transaktion nicht über `IdentityPermissionsAdapter` oder eine entsprechende gesperrte Identitätsprüfung erneut validiert. Der Zugriffskontext aus dem Anfragebeginn bleibt damit allein maßgeblich.

**Nachstellung:** Eine Projektzeile extern exklusiv sperren. Mit einem aktiven Portalkonto `GET /api/v1/portal/projects` starten und anhand von `pg_blocking_pids` auf das Warten an dieser Zeile warten. Das Konto über die Verwaltungs-API deaktivieren und deren erfolgreiche Antwort abwarten. Erst dann die Projektzeile freigeben.

**Beobachtet:** Deaktivierung 200, die wartende Projektliste danach ebenfalls **200 mit Projektkennung, Schlüssel und Name**. Ein anschließend neu gestarteter Aufruf erhält 401. Es handelt sich um den laufenden Leseaufruf, nicht um eine nach Deaktivierung dauerhaft nutzbare Sitzung.

**Vorschlag:** Vor den Projektsperren den aktuellen Portal-Kontext mit Konto-/Sitzungssperren und gleicher Sperrfolge wie die übrigen Lesepfade prüfen. Die Prüfung muss eine während des Wartens abgeschlossene Deaktivierung sehen. Dabei nicht versehentlich ein zusätzliches Projekt-Aktionsrecht verlangen, wenn die Liste laut Vertrag weiterhin nur Mitgliedschaften nennen soll.

### F4 · P2 · Fehlendes Pflichtfeld entfernt still alle Projektmitgliedschaften

**Datei/Zeile:** `backend/src/main/java/taskmanager/PortalAccountsController.java:112–123` und `PortalAccounts.java:169–171`; Vertrag `contracts/portal.openapi.yaml`, Schema `UpdateRequest`.

Der Vertrag verlangt `projectIds` als vollständige neue Liste. Der Controller prüft nur, ob überhaupt ein Körper vorhanden ist. Fehlt das Feld, wird `null` in eine leere Liste umgewandelt und als bewusster Entzug aller Projekte verarbeitet.

**Nachstellung:** Ein Portalkonto einem Projekt zuordnen. Dann `PUT /api/v1/portal-accounts/{id}` mit ausschließlich `{"contactId":"<gültiger Kontakt>"}` senden.

**Beobachtet:** **200**, Antwort `projectIds: []`; die bisherigen Projektmitgliedschaften sind entfernt. Ein Kontakt-Update eines unvollständigen Clients bewirkt damit einen nicht angeforderten Rechteentzug.

**Vorschlag:** Bei Updates fehlendes oder `null` gesetztes `projectIds` vor jeder Änderung mit 400 ablehnen. Eine ausdrücklich gesendete leere Liste muss weiterhin alle Projekte entfernen dürfen. Fehlend, null, leer sowie 100/101 Einträge getrennt testen.

### F5 · P2 · Portalkonten bleiben in Bearbeiter- und Personenfeldauswahl auswählbar

**Datei/Zeile:** `frontend/src/pages/TicketPage.tsx:131–134`; `frontend/src/pages/ProjectPage.tsx:219` und `261–270`.

Nur die Ausweichliste aus dem Kontoverzeichnis wird auf interne Konten begrenzt. Sobald die Mitgliederabfrage erfolgreich ist, werden wieder alle Projektmitglieder angeboten. Portalkonten sind reguläre Projektmitglieder und erscheinen deshalb in dieser Auswahl.

**Nachstellung:** Über die Oberfläche ein Portalkonto „lea.probe“ mit Projektmitgliedschaft anlegen. Intern ein Ticket dieses Projekts bearbeiten beziehungsweise das Formular „Neues Ticket“ öffnen und die Auswahl „Zuständig“ ansehen.

**Beobachtet:** Das Portalkonto wird in **beiden** Auswahllisten angeboten. Dieselbe ungefilterte Personenliste wird für eigene Personenfelder verwendet. Der Server lehnt eine tatsächliche Portal-Zuweisung ab; im vorhandenen Backendtest ist das 400 `assignee.not_member`. Eine unzulässige gespeicherte Bearbeiterzuordnung wurde nicht nachgewiesen.

**Vorschlag:** Auch Mitgliederlisten mit den Kontenarten des Verzeichnisses abgleichen und nur aktive interne Konten für diese Felder anbieten. Sowohl Neuanlage als auch Bearbeitung und eigene Personenfelder im Browser prüfen. Die normale Mitgliederauswahl ist bereits entsprechend gefiltert.

### F6 · P2 · Portaloberfläche erreicht nur die ersten 50 Anfragen eines Projekts

**Datei/Zeile:** `frontend/src/pages/PortalPage.tsx:40–75`, `frontend/src/api/product.ts:226–227`.

Die Oberfläche lädt einmal ohne Seitennavigation. Der echte Server begrenzt diesen Aufruf standardmäßig auf 50 Tickets; der Vertrag bietet dafür `limit` und `before`. Der Client übergibt beides nicht und bietet keinen Weg zu weiteren Tickets.

**Nachstellung:** 51 organisationszugehörige Anfragen in einem freigegebenen Projekt bereitstellen. Im Browserexperiment wurde die Antwort entsprechend der echten Servervorgabe auf die neuesten 50 begrenzt.

**Beobachtet:** 50 Zeilen, die älteste Anfrage fehlt, kein Nachladen oder Seitenschalter. Da die Portalansicht interne Ticket-URLs nicht als Detailnavigation verwendet, ist die ältere Anfrage aus dieser Oberfläche nicht erreichbar. Die API-Grenze wurde anhand des Controllers und Vertrags geprüft; das Browserexperiment bildete sie gezielt nach.

**Vorschlag:** `before` im Client unterstützen und weitere Seiten erreichbar machen. Einen Browserfall mit mehr als 50 Anfragen ergänzen. Die gegenwärtige Testattrappe gibt alle Anfragen zurück und verdeckt diesen Fehler.

## Hinweise

### H1 · P3 · Ticketnummer wird doppelt angehängt

**Stelle:** `frontend/src/pages/PortalPage.tsx:61` und `90`.

`Ticket.key` enthält bereits den vollständigen Schlüssel, beispielsweise `PLAT-51`. Die Oberfläche hängt die Nummer nochmals an. Im Browser nachgestellt: **`PLAT-51-51`**, auch in der Detailüberschrift. Den gelieferten Schlüssel direkt anzeigen. Der vorhandene Browsertest prüft den Titel, nicht den dargestellten Schlüssel.

### H2 · P3 · 503 fehlt im Vertrag der Portal-Projektliste

**Stelle:** `contracts/projects.openapi.yaml:28` (`GET /portal/projects`), gegenüber `projects/web/PortalProjectController.java:38` und `ProjectController.java:228–240`.

F2 liefert tatsächlich 503 `project.busy`; die Dokumentation dieses Pfads nennt nur 200/401/403/404. Die 503-Antwort einschließlich `Retry-After` ergänzen. Die Routentests vergleichen Methoden und Pfadmuster, keine vollständigen Antworten.

### H3 · P3 · Kontenformular nennt Validierungsgründe nur allgemein

**Stelle:** `frontend/src/pages/PortalAccountsPanel.tsx:13–17` und `151–166`.

Unbekannte Organisation, inzwischen verschobener Kontakt oder nicht mehr freigegebenes Projekt werden nicht über die jeweiligen `violations` in verständliche Einzelgründe übersetzt. Der Browserfall mit einem verschobenen Kontakt verlangt lediglich irgendeine Meldung. Feldbezogene Texte ergänzen, damit die Verwaltung erkennen kann, welche Eingabe sie ändern muss.

### H4 · Aktuelle Unternehmensrechte sind abgedeckt; Erweiterungen brauchen einen Katalogabgleich

**Stelle:** `backend/src/main/java/taskmanager/PortalAccountPorts.java:25–28`, `permissions/internal/JdbcPermissions.java:320–334`.

Die gegenwärtigen Unternehmensrechte des API-Katalogs sind über `AdministrationRight` plus `permissions.audit.read` erfasst; neue Enumwerte werden automatisch aufgenommen. Projektaktionen ohne Projektkennung sind teils globale Projektregeln und dürfen nicht pauschal als Unternehmensverwaltung behandelt werden. Die zwei schreibenden Wege prüfen unter derselben exklusiven Revisionssperre; in sechs parallelen Versuchen entstand nie eine Kombination aus Portalmitglied und `accounts.manage`.

Die Zukunftssicherheit gilt für Rechte, die weiter in diesem zentralen Enum modelliert werden. Ein neuer, außerhalb davon eingetragener Unternehmensschlüssel würde von dieser Liste nicht automatisch erkannt. Einen Test zwischen API-Katalog und Klassifizierung ergänzen; die sichere Ablehnung bei fehlendem oder mehrdeutigem `CompanyActions`-Port ersetzt diesen Vollständigkeitsvergleich nicht.

### H5 · Helpdesk-Freigabeentzug und Portal-Mitgliedschaft sind verschiedene Entzüge

**Stelle:** `backend/README.md`, Abschnitt TASK-075, Grenze 4; `backend/src/main/java/taskmanager/PortalAccountPorts.java:41–50` und `projects/internal/ProjectService.java:381–390`.

Nach Entzug der Projektzuordnung einer Organisation **im Helpdesk** bleiben Portalmitgliedschaften ausdrücklich bestehen. Im eigenen API-Versuch blieben Projektliste und Ticketliste/-detail danach mit 200 lesbar. Die Freigabe wird beim Beitritt geprüft, nicht bei jedem Lesen. Dagegen wirkt das Entfernen des Projekts **am Portalkonto** im vorhandenen Test sofort auf nachfolgende Ticketaufrufe.

Diese dokumentierte Grenze sollte Holger vor der Abnahme ausdrücklich mit Abnahmekriterium 3 („Entzug der Projektfreigabe“) abgleichen. Soll damit auch der Helpdesk-Entzug gemeint sein, erfüllt der Stand dieses Kriterium nicht; dann braucht es eine aktuelle Freigabeprüfung oder eine transaktionale Entfernung der betroffenen Mitgliedschaften. Eine neue, von Holger bestätigte Ausnahme wurde aus der Übergabebeschreibung nicht abgeleitet.

## Rechte, Migrationen und übrige Zugriffspfade

- Der Identitätsadapter prüft Konto/Sitzung sowie Art und Organisation unter Sperren. Ein gültiger Portal-Kontext wurde im eigenen Versuch akzeptiert; derselbe Kontext mit interner Art beziehungsweise fremder Organisation abgelehnt. F3 betrifft den Lesepfad, der diesen Einstieg auslässt.
- Die exklusiven Revisionssperren von `lockForPortalAccounts`, `admitToPortalGroup`, `setMember` und `setAction` schließen den geprüften Wettlauf um Mitgliedschaft und Unternehmensrecht. Gruppe und Standardregeln entsprechen dem Umfang; Beantworten gehört erst zu TASK-076.
- Ticketlisten begrenzen Organisation und Projekt; die Organisationszeilen bleiben geteilt gesperrt. Die Detailprüfung verhindert im Normalfall fremde Inhalte, hat aber den Seitenkanal F1. Kein fremder Ticketinhalt wurde in den ausgeführten Fällen gelesen.
- Interne Projekt-/Ticketdetails, Mitgliedschaften, Historie, Anhangsliste, Ticket-SLA, Ticket-Helpdesk und Tagkatalog: im eigenen Versuch 404. Kontoverzeichnis, Kontenverwaltung, Rechtekatalog/-gruppen, SLA-Optionen, Kunden/Kontakte und beide Suchpfade: 403. Eskalationen, Posteingang und offene Antworten antworteten mit 200 und leerer Liste; das ist keine pauschale Sperre des Endpunkts. Ihre projektbezogenen Rechteprüfungen begrenzen die Ergebnisse. Ein gefüllter Bestand dieser drei Listen wurde nicht zusätzlich experimentell geprüft.
- 55P03 wurde bei F1, 40P01 bei F2 tatsächlich ausgelöst und jeweils als 503 übersetzt. Keine 500 in diesen Versuchen. Das beweist keine Freiheit von sämtlichen weiteren Verklemmungen.
- V0034–V0036 wurden zusätzlich auf einen V0033-Bestand mit einem lokalen und einem LDAP-Konto angewendet. Beide blieben intern, Organisation/Kontakt leer. Vier Versuche zum Wechsel von Art, Organisation oder zur LDAP-Art eines Portalkontos wurden mit SQLSTATE 23514 abgewiesen; die drei neuen Indizes bestehen. Kein Volumentest und kein Test mit eingeschränkter Datenbankrolle.
- Die Grenze von 100 Projekten wird für Eingabelisten geprüft. Eine fehlende Liste im Update ist F4. Ein eigener Lauf mit exakt 100/101 Projekten wurde nicht ausgeführt; die Grenze ist zudem kein nachgewiesenes globales Mitgliedschaftslimit über sämtliche Verwaltungswege.

## Abgleich der Abnahmekriterien und Testlücken

| Kriterium TASK-075 | Bewertung |
| --- | --- |
| 1 · Fälle 1–8 und Pflichtwechsel | Pflichtwechsel und zentrale Organisations-/Projektgrenzen positiv geprüft; die acht fachlichen Fälle sind nicht vollständig als Portaltests abgebildet, siehe unten |
| 2 · Keine Unternehmensrechte, fremde Organisation oder unzulässige Projekte, auch gleichzeitig | Zuweisungsschutz positiv einschließlich sechs Wettläufen; F1 verletzt die geforderte Gleichbehandlung fremder und unbekannter Anfragen |
| 3 · Entzug/Deaktivierung sofort wirksam | Nachfolgende Ticketaufrufe im vorhandenen Test abgewiesen; laufende Projektliste umgeht die aktuelle Prüfung (F3); Helpdesk-Entzug gesondert abzugrenzen (H5) |
| 4 · Migration, Verträge, Rechte/Nebenläufigkeit und Browser | Migration und vorhandene Tests positiv, aber F2 und F4–F6 sowie die Antwortlücke H2 bleiben |

Die Kommentare in `PortalAccountsApiTest.java:297` und `345` sind kein vollständiger Nachweis der gleich nummerierten Fälle aus docs/PERMISSIONS.md:

| Fachlicher Fall | Tatsächliche Abdeckung beziehungsweise Lücke |
| --- | --- |
| 1 · Anfragen derselben Organisation | Mehrere Tickets derselben Organisation werden gelesen; Portal-Erstellung durch verschiedene Kollegen ist noch nicht Teil dieser Aufgabe |
| 2 · Fremde Organisation ohne Existenzinformation | Normaler Vergleich mit unbekannter Nummer vorhanden; gesperrte fremde Zuordnung fehlt im Bestand und scheitert im Zusatzversuch F1 |
| 3 · Gleiche Organisation, anderes Projekt | Vorhanden |
| 4 · Ausdrückliches Leseverbot gewinnt gegen Freigabe einer anderen Gruppe | Kein entsprechender Portal-Integrationsfall mit dem geschützten Feld; allgemeine Rechtekerntests ersetzen diesen konkreten Datenweg nicht vollständig |
| 5 · Schreiben verboten, Lesen erlaubt | Kein entsprechender Portal-Schreibfall; positive Schreibpfade sind erst TASK-076 |
| 6 · Verborgenes Feld zusammen mit erlaubter Änderung | Kein entsprechender Portal-Mutationsfall; für TASK-076 nachzuholen |
| 7 · Keine Rückschlüsse über verborgene Such-/Sortierwerte | Interne Suche wird insgesamt mit 403 abgewiesen; keine weitergehende Portalsuche geliefert |
| 8 · Direkter interner Dateilink | Der Bestand prüft die Anhangsliste, keinen bekannten vorhandenen Anhang über seinen Downloadlink. Der positive kundenlesbare Anhang gehört zu TASK-076. Fall 8 in diesem Dokument ist ein Anhangsfall, keine Supportantwort |

Weitere fehlende Regressionen: fehlendes/null `projectIds`, 100/101 Projekte, beide Reihenfolgen der gleichzeitigen Rechteänderung, Portal-Projektliste während Deaktivierung, die UUID-Sperrreihenfolge, reale Seitengrenzen, Bearbeiter-/Personenauswahl und genaue Validierungsgründe. Der neue Browserbestand hat drei Portaltests; die Attrappe ignoriert `limit`/`before` bei Portal-Tickets (`frontend/tests/browser/backend.mjs:374`) und verschleiert dadurch F6.

## Tatsächlich ausgeführte Prüfungen

Prüfumgebung: JDK 21.0.13, PostgreSQL 16.15, Maven 3.9.16 und Node.js 24.22.0. Ausschließlich synthetische Daten und eine eigene lokale PostgreSQL-Instanz.

| Prüfung | Ergebnis |
| --- | --- |
| Vollständiges Backend `mvn -Djava.version=21 -Dtaskmanager.test.jdbc-url=… verify` | **481 Tests: 480 bestanden, ein Fehler** im Versionsvergleich `DatabaseFoundationTest:29`; dieser erwartete ohne lokalen Parameter standardmäßig PostgreSQL 18.6 |
| Gezielte Wiederholung `-Dtest=DatabaseFoundationTest -Dtaskmanager.test.server-version=16.15 verify` | **4 Tests und verify erfolgreich**, einschließlich Migrationserwartung und Paketbau. Kein zweiter vollständiger 481-Test-Lauf |
| PortalAccountsApiTest / PortalContractTest im Gesamtlauf | **7 beziehungsweise 1 Test bestanden** |
| `npm ci`, `npm run verify` | Installation, Generierung, Typprüfung, **77 Tests** und Produktionsbau erfolgreich |
| `npm run test:browser` | Erster Lauf: **154 gezählte Tests, 152 bestanden, zwei Fehler** durch Zeitüberschreitungen in Helpdesk-/Tagtests; kein Portaltest fehlgeschlagen |
| Vollständige Browserwiederholung `node --test --test-concurrency=2 tests/browser/*.test.mjs` | **153 Tests bestanden**, keine Fehler/Skips |
| Zusätzliche Backend-Programme außerhalb des Checkouts | F1–F4 nachgestellt; F2 mit echtem 40P01; sechs Gruppen-/Aktionswettläufe; gültiger und verfälschte Identitätskontexte; Helpdesk-Freigabeentzug und 20 interne Lesepfade geprüft |
| Zusätzlicher Browserversuch | Bearbeiter in Anlage/Bearbeitung, doppelte Ticketkennung, 51 Tickets mit vertragsgemäßer 50er-Antwort sowie interne Abrufe geprüft; keine internen Abrufe der Portalansicht im Versuch |
| Zusätzlicher Migrationstest | V0033 → V0036 mit zwei vorhandenen Konten, vier abgelehnte unzulässige Änderungen, neue Indizes geprüft |
| `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check` | Erfolgreich, unveränderte Aufgabenübersicht |
| `python -m unittest discover -s tests -v` | 38 entdeckt: **37 bestanden, ein plattformbedingter Zeitzonen-Skip** |
| `git diff --check` einschließlich neuer Berichtsdatei | Erfolgreich |

Die erste Browserparallelität war deutlich stärker ausgelastet; ein endgültiger Beweis der Ursache ihrer zwei Timeouts wurde nicht geführt. Die erfolgreiche begrenzte Wiederholung ersetzt nicht die Offenlegung dieses ersten Laufs. Temporäre Prüfprogramme brauchten zunächst Korrekturen ihrer Startparameter beziehungsweise Oberflächen-Selektoren; die oben genannten Befunde beruhen auf den abgeschlossenen Versuchen. Installation/Bau meldeten ferner drei moderate Abhängigkeitsfunde und den bestehenden Hinweis zur Bündelgröße; keine Abhängigkeiten geändert.

Nicht ausgeführt: Docker-/Lebenszyklusprüfung, vollständiger Backendlauf mit PostgreSQL 18.6, Lasttest großer Organisationen, praktischer Bildschirmlesertest, Download eines tatsächlich vorhandenen internen Anhangs und Fachfunktionen von TASK-076/TASK-077. Die vorhandenen Tests liefern keine vollständige Bestätigung aller V1-Portalwege.

## Empfehlung

**Nicht abnehmen in diesem Stand.** F1–F6 korrigieren und die genannten Fälle als Regressionen belegen. H5 vor der Abnahme fachlich eindeutig festhalten. Die Unternehmensrechtssperre, der prüfende Identitätsadapter, die Grundgrenze zwischen Organisationen und die Migrationen liefern positive Nachweise; sie gleichen die nachgestellten Existenz-, Sperr-, Sitzungs- und Oberflächenfehler nicht aus. Eine spätere fachliche Abnahme trifft Holger.
