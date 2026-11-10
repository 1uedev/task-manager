# Nachprüfung TASK-094 / PR #281

Datum: 10. November 2026; Prüfungen am 9./10. November. Unabhängige zweite KI: Codex, Sitzung `codex-20261109-wiki-review-03` (DEC-040).

**Empfehlung: nachbessern.** Die ursprünglichen Korrekturen am Leserschutz, an der Sprachwahl und am Ausgangsvergleich sind nachweisbar wirksam. Der neue automatische Sprachwechsel lässt jedoch eine geöffnete Historienrevision in der bisherigen Sprache stehen und kann verspätete Revisionsantworten der alten Sprache übernehmen (**R1**). Außerdem bleibt ein Randfall des Testersatzes aus H1 abweichend (**N1**). Die fachliche Abnahme bleibt bei Holger.

## Prüfstand und Umfang

Geprüft wurde aktuelles `main` auf `042b7383a41147c2215966ab07a761fdc5242e37`, der gemergte [PR #281](https://github.com/1uedev/task-manager/pull/281). Der genannte Nachbesserungscommit `dfc9ad5ed8a9c971be5c6e7e76b4adf10dedd1ae` wurde zusätzlich abgerufen: `git diff dfc9ad5 HEAD` ist leer. Die erneute Abfrage von `origin/main` bestätigte denselben Stand.

Zuerst gelesen: `AGENTS.md`, TASK-094 in `TODO.md`, DEC-124 in `DECISIONS.md`, der ursprüngliche Bericht `docs/REVIEW_TASK-094_PR-281.md` und `contracts/wiki.openapi.yaml` Version 0.2.1. Ergänzend wurden die Sitzungsregeln, betroffenen Anforderungen, Implementierung, Tests und Demowerkzeug gelesen.

Umfang dieser Nachprüfung sind ausschließlich F1–F4 und H1–H4 sowie unmittelbar durch deren Behebung betroffene Abläufe. Die bestätigte Erweiterung der Rechte gilt weiterhin: Auch `wiki.publish` ohne `wiki.edit` darf Entwürfe, Historie und Ausgangsvergleich lesen; Speichern und Abgleichen verlangen `wiki.edit`. Alte TASK-093-Befunde werden hier nicht neu aufgerollt.

## Befund – vor Abnahme zu beheben

### R1 – P2: Sprachwechsel und verspätete Antworten vermischen Historienfassungen

**Datei und Zeile:** `frontend/src/pages/WikiPage.tsx:464`, `:467`, `:471`, `:477`, `:533`, `:767`, `:877`.

`PageView` lädt nach dem Wechsel der Oberflächensprache korrekt die andere Fassung. Der weiterhin offene `HistoryPanel` bleibt jedoch dieselbe Komponenteninstanz. Seine Zustände `shown` und `compared` sind nicht an Seitenkennung und Sprache gebunden und werden beim Fassungswechsel nicht verworfen. Auch `show` und `compare` prüfen nach ihren asynchronen Abrufen nicht, ob deren Sprache noch aktuell ist. Die neue Sprachmarkierung richtet sich gleichzeitig schon nach `page.language`.

**Reproduktion 1, vorhandene Ansicht: Bruch zwischen F2 und H2.**

1. Dieselbe Seite auf Deutsch und Englisch mit unterschiedlichen Titeln und Inhalten veröffentlichen.
2. Mit deutscher Oberfläche die Historie öffnen und die deutsche Revision 1 ansehen.
3. Die Oberflächensprache auf Englisch wechseln, ohne die Historie zu schließen.
4. Seitentitel und Revisionsliste wechseln auf Englisch. Die geöffnete Einzelrevision zeigt weiterhin „Deutscher Titel“ und `DEUTSCHER REVISIONSINHALT`. Ihr Inhaltscontainer trägt nun aber `lang="en"`.

Im Browser mit dem echten Produktionsbuild reproduziert; der Test protokolliert `{"text":"DEUTSCHER REVISIONSINHALT","lang":"en"}`. Ein Screenshot bestätigt die gleichzeitig sichtbare englische Revisionsliste und deutsche Einzelrevision.

**Reproduktion 2, verspätete Antwort.** Die Antwort auf `GET .../revisions/1?language=de` anhalten, während ihres Ladens die Oberfläche und Seite auf Englisch wechseln, anschließend die deutsche Antwort freigeben. Die deutsche Revision erscheint danach in der englischen Historie. Auch diese Variante schlägt im unabhängigen Browsertest reproduzierbar fehl.

**Auswirkung:** Revisionsnummern werden im Zusammenhang der falschen Fassung präsentiert; die Person kann einen Inhalt der falschen Sprache beurteilen. Zusätzlich ist das Sprachattribut falsch, was die Aussprache durch Hilfsmittel betrifft. Der normale Seitenabruf verwirft alte Antworten bereits korrekt; dessen Schutz erfasst diese untergeordneten Abrufe nicht. Kein zusätzlicher Zugriff auf unberechtigte Entwürfe wurde damit nachgewiesen.

**Vorschlag:** Die Historienansicht mindestens an `(page.id, page.language)` binden, etwa mit einem entsprechenden Komponentenschlüssel. Bei einem Wechsel Auswahl, Einzelrevision und Vergleich zurücksetzen oder ausdrücklich die Fassung ihrer Daten beibehalten. Laufende Lese-/Vergleichsanfragen mit einer Anfragegeneration beziehungsweise dem vollständigen Kontext versehen und überholte Antworten verwerfen. Die beiden Reproduktionen und den entsprechenden Fall eines offenen Historienvergleichs dauerhaft testen. Nur `lang` zu ändern oder nur den sichtbaren Zustand zurückzusetzen beseitigt die verspätete Antwort nicht.

## Hinweise

### N1 – H1 ist für unsichtbare und unbekannte Seiten noch nicht vollständig angeglichen

**Datei und Zeile:** `frontend/tests/browser/backend.mjs:1833`, `:1839`, `:1841`; Gegenstelle `backend/src/main/java/taskmanager/wiki/internal/WikiService.java:367`.

Der behobene Fall „Leser einer veröffentlichten Seite, fehlende Sprache“ antwortet nun korrekt mit 400. Der Mock prüft jedoch weiterhin vor dem Routenblock, ob die Seite existiert und sichtbar ist. Der echte `addLanguage` prüft die Körpersprache bereits vor dem Seitenzugriff.

**Reproduktion:** Als Leser `POST /wiki/pages/{id}/languages` mit `{}` oder `{"language":"fr"}` senden, einmal für eine unveröffentlichte Seite und einmal für eine unbekannte UUID. Der echte Server antwortet in allen vier Fällen mit 400 `request.invalid`, mit `language.required` beziehungsweise `language.invalid`. Der Testersatz liefert jeweils 404 `wiki.page_not_found`. Beide Seiten wurden ausgeführt; dies ist kein bloßer Vergleich des Quelltexts.

**Auswirkung:** Die allgemeine Aussage „Testersatz wie der Server“ trifft für diese Prüfungsreihenfolge noch nicht zu. Browserprüfungen können weiterhin einen abweichenden Fehlerfall bestätigen. Eine zusätzliche Offenlegung eines Entwurfs durch den echten Server wurde nicht gefunden: Unsichtbar und unbekannt liefern dieselbe Antwort.

**Vorschlag:** Die sprachbezogene Körperprüfung dieser Route auch im Mock vor die Sichtbarkeitsprüfung ziehen und die vier Fälle in einen Paritätstest aufnehmen. Der neue Test in `wiki.test.mjs:636` deckt bislang die sichtbare Seite sowie archivierte Fassungen ab.

### N2 – Den erzwungenen Sperrwartefall dauerhaft absichern

**Datei und Zeile:** `backend/src/test/java/taskmanager/wiki/WikiApiTest.java:995`, `:1002`, `:1016`.

Die neuen Konkurrenztests starten Aufrufe über gemeinsame Startsignale und prüfen sinnvolle Ergebnisse. Sie halten jedoch keine konkrete Datenbanksperre an und weisen nicht nach, dass die Anfragen tatsächlich gleichzeitig an der relevanten Sperre warten. Das ist eine verbleibende Einschränkung des dauerhaften Nachweises aus H4, kein festgestellter Fehler der aktuellen Sperrung.

**Auswirkung:** Ein Lauf kann die Ergebnisprüfungen auch bei zufällig nacheinander ausgeführten Datenbankzugriffen erfüllen. Damit hängt die Empfindlichkeit gegen eine spätere Sperrregression vom Ablauf ab.

**Vorschlag:** Eine kontrolliert gehaltene `wiki_page`-Sperre oder eine gleichwertige Testschranke ergänzen und vor der Freigabe die Warteposition nachweisen. Genau dies wurde ergänzend außerhalb des Produktbaums ausgeführt: Zwei wartende Sprach-Anlagen ergaben 201/409; zwei wartende Speicherungen in DE/EN ergaben 200/200 mit getrennten Zählern. Die aktuelle Implementierung bestand diesen Test.

## Ergebnis je ursprünglichem Punkt

| Punkt | Nachprüfung und Ergebnis |
| --- | --- |
| **F1: unveröffentlichte Ausgangsfassung** | **Behoben.** `WikiService.java:612`, `:641` bilden `sourceVisible`, nullable `sourceLanguage`, `translation` und `fallback` entsprechend; der Mock in `backend.mjs:1679` folgt dem. Vertrag 0.2.1 und generierte Typen passen. Der vorhandene neue API-Test besteht. Zusätzlich gegen den echten Server für DE und EN als Ausgangssprache geprüft: Ausgangsfassung nie veröffentlicht, danach veröffentlicht und zurückgenommen, schließlich beide Fassungen zurückgenommen. Für jede Stufe Abrufe ohne Sprachparameter und mit beiden Sprachwünschen geprüft. Keine verborgenen Titel, Ausgangssprache oder Übersetzungsbeziehung in Leserantworten; nur veröffentlichte Einträge in `languages`, kein Entwurfs-ETag. Baum und Pfad lassen eine zusätzliche unveröffentlichte Elternseite aus. Historie/Revisionslesen bleiben 404, Speichern ohne Recht 403; ohne Veröffentlichung stets `wiki.page_not_found`. |
| **F2: Sprache der geöffneten Seite** | **Grundfall behoben, Folgefehler R1.** `WikiPage.tsx:765`–`:768` trennen ausdrückliche Wahl, angeheftete Editorfassung und Standard. Der vorhandene Browsertest besteht. Ergänzend beide Oberflächenwechsel, eine ausdrückliche Fassungswahl und unveränderte eigene Editoreingabe geprüft. Nach ausdrücklich gewähltem Englisch lösen weitere Oberflächenwechsel keine zusätzlichen Seitenabrufe aus; die Eingabe bleibt in Englisch. Ein angehaltener älterer englischer Seitenabruf überschreibt die später angeforderte deutsche Fassung nicht. Beobachtete Abruffolge im gezielten Rennen: `en`, `de`, danach einmal `en` für die ausdrückliche Wahl. Der offene Historienzustand ist davon ausgenommen und fehlerhaft, siehe R1. |
| **F3: Titel im Ausgangsvergleich** | **Behoben.** `WikiPage.tsx:708`, `:729`–`:739` vergleichen und zeigen Titel vorher/jetzt. Bei reinem Titelwechsel wird der unveränderte Inhalt ausdrücklich so benannt, statt pauschal „Keine Unterschiede.“ auszugeben. Vorhandener deutscher und ergänzender englischer Ausgangsfall im Browser bestanden; auch mit ausschließlich `wiki.publish`. |
| **F4: Bindung des Ausgangsvergleichs** | **Behoben.** `WikiPage.tsx:692`–`:706` binden das Ergebnis an Seite, Ausgangssprache und beide Revisionsnummern. Der vorhandene Test des Wechsels von 1/2 auf 1/3 besteht. Zusätzlich die Antwort der alten Revision 2 angehalten, währenddessen das neue Paar 1/3 geladen und erst danach die alte Antwort freigegeben: Das Ergebnis bleibt bei Revision 3; kein alter Vergleich wird eingesetzt. Dies betrifft `SourceChange`; der separate Historienabruf benötigt den Schutz aus R1. |
| **H1: Testersatz** | **Für die ursprünglichen konkreten Beispiele behoben; Rest N1.** Fehlender Sprachkörper auf sichtbarer Leser-Seite ergibt 400; Zurücknehmen archivierter DE-/EN-Fassungen ergibt 409 `wiki.page_archived`. Der neue Browsertest besteht. Die vorgeschaltete Sichtbarkeit bleibt im Mock aber in anderer Reihenfolge, siehe N1. |
| **H2: Fokus und `lang`** | **Die vorgesehenen Ergänzungen funktionieren; R1 bleibt.** Nach Sprach-Anlage wird die neue Sprachschaltfläche fokussiert (`:636`); nach Ausgangsvergleich dessen Ergebnis (`:700`). Ausgangstitel/-blöcke und Revisionsinhalt erhalten ihre Sprache. Vorhandene Fokustests sowie zusätzliche Tastaturprüfung mit englischer Ausgangssprache und nur `wiki.publish` bestanden. DE-/EN-Oberfläche bei 320 px angesehen: Ergebnis hat sichtbaren Fokus, kein horizontaler Dokumentüberlauf; `lang` der Ausgangstitel und des Revisionsinhalts geprüft. Bei einem späteren Fassungswechsel kann der Revisionsinhalt allerdings falsch beschriftet werden, siehe R1. Keine vollständige Barrierefreiheitsabnahme behauptet. |
| **H3: Rückfalltext** | **Behoben.** `frontend/src/i18n/de.ts:1437` und `en.ts:1437` erklären nur gewünschte und gezeigte Fassung, ohne das Nichtbestehen eines Entwurfs zu behaupten. Der neue Leserfall und bestehende Rückfalltests bestehen. |
| **H4: Rechte-, Revisions- und Konkurrenznachweise** | **Wesentlich ergänzt und erfolgreich ausgeführt; Nachweisverbesserung N2 empfohlen.** `translationsKeepTheirSourceAcrossRightsAndRevisions` prüft Quellgrenzen, unveröffentlichte Quellrevision, ausschließlich `wiki.publish`, verweigertes Speichern und Übernahme einer alten Übersetzung samt Quelle. `concurrentLanguageChangesStaySeparate` prüft mehrfache Anlage und Speicherungen beider Sprachen. Alle bestanden gegen PostgreSQL 16. Die zusätzlichen kontrollierten Sperrfälle und gleichzeitiges Veröffentlichen in DE/Speichern in EN bestanden ebenfalls. Auch das unveränderte Demowerkzeug wurde hier gegen einen frisch gestarteten echten Server ausgeführt. |

## Tatsächlich ausgeführte Prüfungen

Getrennter Prüfbereich; Windows, Python 3.12.14, Java 25.0.4.1, Maven 3.9.16, Node 24.22.0/npm 11.19.1, PostgreSQL 16.15. Für Python und Maven wurden die verfügbaren lokalen Programme mit denselben Skripten beziehungsweise dem Repository-POM und Ziel `verify` benutzt; die Unix-Startnamen `python3` und `./mvnw` waren hier nicht die ausgeführten Programme. Die Datenbanken enthielten ausschließlich synthetische Daten.

| Prüfung | Ergebnis |
| --- | --- |
| `scripts/validate_repository.py` | Bestanden. |
| `scripts/task_board.py --check` | Bestanden, Aufgabenübersicht aktuell. |
| `python -m unittest discover -s tests -v` | 40 Tests: 39 bestanden, ein vorgesehener Skip, weil sich Zeitzonen in diesem Test nur unter Unix umstellen lassen. |
| Backend vollständiges `verify` mit PostgreSQL 16.15 | **Bestanden: 564 Tests, 0 Fehler, 0 Fehlschläge, 0 Skips; Verpackung erfolgreich.** Darin `WikiApiTest` mit zwölf Fällen und `WikiContractTest` mit einem Test für 16 Routen. Alle 54 Migrationen bis V0057 wurden in den Integrationstests erfolgreich angewandt. |
| `npm ci` | Bestanden; 111 Pakete installiert, 112 geprüft, keine gemeldeten Schwachstellen. |
| `npm run verify` | Bestanden: Vertragsgenerierung, Typprüfung, 90 Unit-Tests und Produktionsbuild. Nicht blockierende Bundlerhinweise zu Tiptap-Moduldirektiven und Paketgröße. |
| `npm run test:browser -- --test-concurrency=1` | Vollständig ausgeführt: **231 Tests, 230 bestanden, ein Fehlschlag**, keine Abbrüche/Skips. Alle 20 Wiki-Fälle bestanden, einschließlich der fünf ergänzten Fälle F1, F2, F3/F4, H1 und H2. |
| Isolierte Wiederholung des Browserfehlers | `tags.test.mjs:56`, „a tag gets a description and a color, both shown with the name“, bestand isoliert. Im vollständigen Lauf war die Schaltfläche „Kunde Nord bearbeiten“ nicht innerhalb von 30 Sekunden verfügbar. Der ursprüngliche Gesamtlauf bleibt als fehlgeschlagen dokumentiert; keine Zuordnung dieses Tag-Fehlers zur Wiki-Änderung festgestellt. |
| Eigene API-/Datenbanknachprüfung | Drei zusätzliche Fälle bestanden: vollständige Lesermatrix für beide Ausgangssprachen; Fehlerreihenfolge an unsichtbarer/unbekannter Seite; kontrollierte Sperrkonkurrenz und getrennte Sprachrevisionen. Echte HTTP-Schnittstelle und PostgreSQL, keine Speicherattrappe. |
| Eigene Browsernachprüfung | Sechs Fälle: vier bestanden, zwei reproduzierbare Fehlschläge zu demselben Befund R1 (bereits angezeigte und verspätete Revision). Erfolgreich: Sprachwahl/Editor/alte Seitenantwort, alte Ausgangsvergleichsantwort, mobile DE-/EN-Fokus-/Sprachprüfung sowie Beobachtung des Mock-Verhaltens aus N1. Browser verwendet echten Produktionsbuild mit dem Repository-Testserver. |
| Demo gegen frisches Backend | `tools/demo/demo.py ... seed --profile demo` erfolgreich auf neuer PostgreSQL-16-Datenbank: vier interne Konten, zwei Portalkonten, vier Projekte, 43 Tickets und Wikibaum. Anschließend als `david` über die echte API geprüft: „Helpdesk Handbuch“ mit Kind „Serviceanfrage ergänzen“, Deutsch veröffentlicht auf Revision 1, Englisch als Entwurf auf Revision 2, Titel „Complete a service request“, `sourceRevision=1`. |
| `git diff --check`, Stand-/Änderungskontrolle | Erfolgreich. Produktcode, generierte Verträge, `TODO.md` und `DONE.md` unverändert; ausschließlich dieser Bericht wird hinzugefügt. |

Die GitHub-Abfrage des Nachbesserungscommits bestätigte außerdem erfolgreiche Workflows für [Backend](https://github.com/1uedev/task-manager/actions/runs/42682706634), [Frontend](https://github.com/1uedev/task-manager/actions/runs/42682706815), [Repository checks](https://github.com/1uedev/task-manager/actions/runs/42682706907), [Technical qualification](https://github.com/1uedev/task-manager/actions/runs/42682706882) und [Image](https://github.com/1uedev/task-manager/actions/runs/42682706804). Dies sind abgefragte CI-Ergebnisse, keine zusätzlichen eigenen lokalen Ausführungen.

**Nicht lokal ausgeführt:** Docker-Image-/Compose-Lifecyclecheck und kompletter Barrierefreiheitstest. Die vorhandenen Integrations- und Anwendungstests liefen im vollständigen Backendlauf; der zusätzliche Demostart ersetzt keinen Docker-Lifecyclecheck. Eine zweite vollständige Browsersuite wurde nach dem isolierten Tag-Erfolg nicht gestartet. Die ursprünglichen Einschränkungen „kein erfolgreicher PostgreSQL-, Browser- oder echter Demolauf“ sind durch die hier ausgeführten Prüfungen weitgehend geschlossen; der vollständige Browserlauf ist wegen des benannten Tag-Fehlers dennoch nicht insgesamt grün.

Rohprotokolle und synthetische Zusatzprüfungen liegen außerhalb des Produktcheckouts im benachbarten Prüfbereich `review-tools/wiki094-review-03`, insbesondere `backend-verify.log`, `frontend-verify.log`, `browser-all.log`, `browser-tag-isolated.log`, `api-probes.log`, `browser-probes-final.log`, `browser-mobile.log`, `demo-seed.log` und `demo-verify.log`. Die Zusatzprogramme und Screenshots wurden nicht in den Produktcode aufgenommen.
