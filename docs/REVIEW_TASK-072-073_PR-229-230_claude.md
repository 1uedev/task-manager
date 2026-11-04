# Prüfung TASK-072 und TASK-073 / PR #229 und #230 (Claude)

2. November 2026 · Claude, unabhängige Prüfsitzung ohne Beteiligung an der Umsetzung · Prüfung nach DEC-040, getrennt vom Codex Bericht `docs/REVIEW_TASK-072-073_PR-229-230.md`.

**Ergebnis: Zur Abnahme empfohlen, ohne blockierende Befunde.** Gefunden wurden zwei Befunde der Stufe P3 (F1, F2) und fünf Hinweise (H1 bis H5). Keiner verletzt eine bestätigte Regel so, dass Daten verloren gehen oder nicht lesbare Tickets, Tags oder Kataloge sichtbar werden. Die Behebung kann in einer kleinen Folgeaufgabe geschehen oder auf später verschoben werden.

## Prüfstand

Geprüft wurde `main` mit Commit `2fed223` (TASK-073, PR #230) auf Commit `3ad1c89` (TASK-072, PR #229). Maßstab sind TODO.md mit den Regeln 1 bis 13 von TASK-072 und 1 bis 4 von TASK-073 samt den vorgeschlagenen Abnahmekriterien, DEC-102, DEC-103 und die Abschnitte zu TASK-072 und TASK-073 in backend/README.md. Kein Produktcode und kein Aufgabenstatus wurden geändert.

Gelesen wurden Migration V0033, `TagService`, `TagRepository`, `TagBulk`, `TagLook`, `TagController` (Massenänderung, Fehlerbehandlung), `SearchService` und `SearchController` (Tagfilter), `SearchProjection` und `SearchRepository.mergeTag`, `JdbcTagAccess`, die Tests in `TagsApiTest` und `SearchApiTest` sowie in der Oberfläche `TagParts.tsx`, `ProjectPage.tsx`, `SearchPage.tsx`, `App.tsx` und die Browsertests `tags.test.mjs` und `tags-bulk.test.mjs`.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Backend `mvn verify` mit `-Djava.version=21` gegen lokales PostgreSQL 16 | bestanden, 473 Tests ohne Fehler |
| Oberfläche `npm ci` und `npm run verify` | bestanden |
| `node --test tests/browser/tags.test.mjs tests/browser/tags-bulk.test.mjs` | 10 von 11 im ersten Lauf, der Fehlschlag ist H4; die beiden betroffenen Tests danach dreimal einzeln bestanden |

Der lokale Lauf gegen PostgreSQL 16 ist eine Entwicklerhilfe und ersetzt nicht den CI Lauf mit der festgelegten Version.

## Was trägt

- **Zusammenführen (Regeln 6 bis 9):** Beide Tags werden `FOR UPDATE` in fester Reihenfolge nach Kennung gesperrt; jede Zuordnung sperrt ihren Tag vorher `FOR SHARE`. Eine wartende Zuordnung sieht die Quelle danach als zusammengeführt (409), eine Zuordnung des Ziels findet die übernommene Zuordnung vor. `moveAssignments` entfernt zuerst die Doppelten und ändert dann die übrigen Zeilen per `update`; eine gleichzeitige Entfernung der Quelle an einem Ticket wird dadurch nicht wiederbelebt, weil das `update` die gelöschte Zeile nach dem Warten übergeht. Ketten früherer Zusammenführungen zeigen nach `markMerged` immer auf ein nicht zusammengeführtes Ziel. Die Obergrenze von 20.000 wird nach der Sperre gezählt, als neue Zuordnungen an der Quelle nicht mehr möglich sind.
- **Suche:** `mergeTag` führt Zuordnungen, Tagliste und Suchtext aller betroffenen Tickets und Projekte in derselben Transaktion nach; der Neuaufbau lässt zusammengeführte Tags aus (`JdbcTagAccess`).
- **Sichtbarkeit (Regeln 5, 7, 11):** Nutzungszahlen zählen nur Tickets in Projekten mit Leserecht auf das Feld `tags` und nur lesbare Projekte. Der Hinweis auf weitere, nicht lesbare Zuordnungen steht in der Vorschau immer und ohne Zahl, verrät also nichts. Vorschläge kommen nur aus dem Unternehmenskatalog und dem Katalog eines Projekts, dessen Katalog die Person sehen darf; archivierte und zusammengeführte Tags fehlen. `lookup` beschränkt sich auf diese beiden Kataloge.
- **Obergrenze (Regel 12):** Die Beratungssperre je Objekt nach den Zeilensperren serialisiert Prüfung und Einfügen; bestehende Zuordnungen über der Grenze bleiben, eine Massenänderung, die die Grenze überschreiten würde, ändert das Ticket gar nicht.
- **Massenänderung (TASK-073 Regeln 1 bis 3):** Jedes Ticket läuft in eigener Transaktion über den Spring Proxy und wird mit den aktuellen Rechten geprüft; eine Ablehnung rollt nur dieses Ticket ganz zurück. Ein Ticket, das die Person nicht lesen darf, erscheint nur mit Kennung und ohne Schlüssel und Grund; unbekannte Kennungen, Tickets fremder Unternehmen und Tickets ohne Recht sind im Ergebnis nicht unterscheidbar. „nicht passend“ wird erst nach der Rechteprüfung vergeben und verrät daher nichts. Die Sperrfolge Tags (sortiert, `FOR SHARE`), Ticket, Beratungssperre stimmt mit der Einzelzuordnung überein; mit dem Zusammenführen kann es keine Verklemmung geben, weil beide in aufsteigender Kennung sperren.
- **Tagfilter (Regel 4):** `any`, `none` und `untagged` schließen Projekte ohne Leseregel für `tags` ganz aus, Treffer und Zähler nutzen dieselbe Bedingung. Weil an einem Ticket nur Tags des Unternehmenskatalogs und des eigenen Projektkatalogs hängen und wer die Tags lesen darf beide Kataloge sieht, ist „ohne sichtbare Tags“ dort gleich „ohne Tags“. Die Ticketliste nutzt mit Tagfilter die Suche und zeigt damit dieselben Tickets.
- **Migration V0033:** nur hinzugefügte, nullbare Spalten mit Prüfbedingungen und ein neu angelegter eindeutiger Index mit derselben Bedingung für bestehende Tags (alle nicht zusammengeführt); sicher auf einer Datenbank mit vorhandenen Tags. Zu `pg_trgm` siehe H5.

## Befunde

### F1 · P3 · Massenentfernung meldet „schon so“, wenn der Tag inzwischen zusammengeführt wurde

**Datei/Zeile:** `backend/src/main/java/taskmanager/tags/internal/TagService.java:396` bis `:406` (Zweig „entfernen“ von `bulkTicket`).

Die Tags einer Massenänderung werden nur einmal vorab in `bulkTags` geprüft. Der Zweig zum Entfernen sperrt den Tag danach nicht mehr und prüft nicht, ob er inzwischen zusammengeführt wurde; er löscht nur die Zeile `(ticket, tag)`.

**Fall:** Eine Person wählt 200 Tickets und „Tags entfernen“ mit Tag `Rechnung`. Nach der Vorabprüfung, während die Tickets abgearbeitet werden, führt die Tagpflege `Rechnung` in `Abrechnung` zusammen. Für alle noch folgenden Tickets findet `unassignTicket` keine Zeile mit `Rechnung` mehr, das Ergebnis zählt sie als „schon so“. Tatsächlich tragen diese Tickets den Begriff jetzt als `Abrechnung` weiter. Die Person liest „0 geändert, 150 schon so“ und hält die Entfernung für erledigt. Beim Hinzufügen tritt das nicht auf, dort sperrt `lockForAssignment` den Tag und `assignable` lehnt mit `tag.merged` ab.

**Vorschlag:** Im Zweig „entfernen“ die gewählten Tags wie beim Hinzufügen `FOR SHARE` sperren und einen inzwischen zusammengeführten Tag mit `tag.merged` ablehnen (Ticket „abgelehnt“). Ein Test mit einer Zusammenführung zwischen Vorabprüfung und Ticket fehlt bisher; die vorhandene Prüfung in `TagsApiTest.java:1113` deckt nur den Fall vor der Vorabprüfung ab.

### F2 · P3 · Ein Abbruch nach schon geänderten Tickets beendet die Anfrage ohne Ergebnis

**Datei/Zeile:** `backend/src/main/java/taskmanager/tags/internal/TagBulk.java:50` bis `:63`.

`apply` fängt je Ticket nur `ApiProblemException` und wiederholbare Datenbankfehler aus `bulkTicket`. Zwei Wege beenden die ganze Anfrage, obwohl schon Tickets geändert sind:

1. Die Prüfung des Sitzungsendes direkt vor dem Commit (TASK-043, `JdbcPermissions.CommitDeadline.beforeCommit`) wirft `PermissionDenied`. Diese Ausnahme ist weder `ApiProblemException` noch `DataAccessException` und geht aus der Schleife heraus. Läuft die feste Sitzungsdauer während einer Massenänderung über 500 Tickets ab, endet die Anfrage mit einem Fehler statt mit dem Ergebnis, und die schon geänderten Tickets bleiben geändert.
2. `readableKey` (Zeile 63) läuft in eigener Transaktion außerhalb des `try`. Eine Sperrzeitüberschreitung bei der Rechteprüfung dort wird über `retrying` im Controller zu 503 `tag.busy` für die ganze Anfrage.

Die Grenze „eine abgebrochene Anfrage lässt bis dahin geänderte Tickets geändert“ ist in TODO.md genannt, und eine Wiederholung ist ohne doppelte Zuordnung möglich. Regel 3 verlangt aber ein Ergebnis mit Zahlen; in beiden Fällen sieht die Person nur einen Fehler und weiß nicht, was schon geschehen ist.

**Vorschlag:** `PermissionDenied` aus `bulkTicket` als „abgelehnt“ mit `auth.session_expired` werten und die restlichen Tickets ohne weitere Versuche als abgelehnt zählen; `readableKey` in das `try` nehmen und bei einem Fehler den Schlüssel weglassen.

## Hinweise

**H1 · Grund „zusammengeführt“ wird als „archiviert“ angezeigt.** `frontend/src/pages/TagParts.tsx:478` ordnet `tag.merged` dem Text `tagBulk.reason.archived` zu. Nach einer Zusammenführung während einer Massenänderung lesen Personen „Tag archiviert“, obwohl der Tag zusammengeführt wurde. Ein eigener Text wäre klarer.

**H2 · Ticketliste mit Tagfilter endet still bei 1000 Treffern.** `frontend/src/pages/ProjectPage.tsx:151` bietet „mehr laden“ nur bis 1000 Zeilen an, passend zur Grenze `MAX_OFFSET` der Suche. Bei mehr Treffern verschwindet die Schaltfläche ohne Hinweis, während die Liste ohne Filter weiter lädt. Ein Satz wie „1000 von 1234 angezeigt, bitte Filter verfeinern“ würde das erklären.

**H3 · Neu laden nach der Massenänderung in der Suche nutzt den aktuellen Formularstand.** `frontend/src/pages/SearchPage.tsx:67` ruft nach dem Ergebnis `run(offset)` mit den aktuellen Feldern auf. Hat die Person Text, Projekt oder Tagfilter inzwischen geändert, aber nicht abgeschickt, zeigt die Liste danach eine andere Suche als vorher. Die Auswahl und das Ergebnis bleiben richtig; nur die Liste wechselt unerwartet. Besser die zuletzt abgeschickte Anfrage merken und wiederholen.

**H4 · Browsertest mit zeitabhängigem Aufbau.** `frontend/tests/browser/tags.test.mjs:78` legt den Tag `Alt` erst in der Testattrappe an, nachdem die Seite geladen ist, und verlässt sich darauf, dass die verzögerte Katalogabfrage (200 ms in `TagCatalog`) erst danach läuft. Im ersten gemeinsamen Lauf beider Dateien schlug „a stale version names the conflict…“ deshalb mit einer Zeitüberschreitung bei „Alt bearbeiten“ fehl; einzeln bestand er dreimal. Der Test sollte den Tag in `prepare` anlegen oder nach dem Anlegen den Katalog neu laden (wie die anderen Tests über den Menüpunkt).

**H5 · Testabdeckung und Betrieb.** Für Abnahmekriterium 2 von TASK-072 („sicher gegen gleichzeitige Zuordnung“) und Regel 12 unter Gleichzeitigkeit gibt es keinen Test, der eine Zuordnung der Quelle während des Zusammenführens oder zwei gleichzeitige Zuordnungen an der Grenze von 30 durchspielt; der Code ist nach meiner Lesart richtig (siehe „Was trägt“), ein Test würde ihn absichern. Die Ablehnung über 20.000 Zuordnungen ist nicht getestet. V0033 braucht für `pg_trgm` das Recht `CREATE` auf der Datenbank; backend/README.md nennt das, für eine spätere Installationsanleitung sollte es dort ebenfalls stehen.

## Empfehlung

TASK-072 und TASK-073 können abgenommen werden. F1 und F2 sind seltene Gleichzeitigkeitsfälle mit irreführender Rückmeldung, ohne Datenverlust und ohne Offenlegung. Ich empfehle, sie zusammen mit H1 und H4 in einer kleinen Folgeaufgabe zu beheben, gern zusammen mit den Befunden des Codex Berichts; H2, H3 und H5 können in die Liste für später.
