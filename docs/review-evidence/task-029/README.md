# Prüfnachweise TASK-029 / PR #128

Codex, Sitzung `codex-20261021-search-review-01`, 21. Oktober 2026. Geprüft: `90f43be75a220eb78591a5673858cae1aff6e882`. Bewertung und Fundstellen: [Prüfbericht](../../REVIEW_TASK-029_PR-128.md).

`search-review-probes.patch` ergänzt ausschließlich die vorhandene `SearchApiTest`. Kein Produktcode wurde für die Versuche verändert. `results.txt` trennt eigene Laufbelege und gelesene CI. Die PIDs gehören zur eigenen flüchtigen PostgreSQL-Instanz; private Rechnerpfade, Kennwörter und Sitzungstoken sind nicht enthalten.

## Wiederholung

Eigenen Wegwerfklon des geprüften Commits mit Java 25.0.4.1+1 und PostgreSQL 18.6 verwenden. Die Variablen unten stehen ausschließlich für die eigene synthetische Testverbindung. Der Benutzer muss isolierte Testdatenbanken erstellen dürfen; das Prüfkennwort darf wegen der Anwendungskonfiguration nicht leer sein.

```sh
git apply --unidiff-zero docs/review-evidence/task-029/search-review-probes.patch
cd backend
./mvnw "-Dtaskmanager.test.jdbc-url=$REVIEW_JDBC_URL" \
  "-Dtaskmanager.test.username=$REVIEW_DB_USER" "-Dtaskmanager.test.password=$REVIEW_DB_PASSWORD" \
  '-Dtest=SearchApiTest#review*' test
```

Der Patch enthält elf Tests. Erwartet auf dem geprüften Stand insgesamt **8 erfolgreich, 3 fehlgeschlagene Assertions**, keine Ausführungsfehler oder übersprungenen Tests:

- `reviewSameUnicodeInputMustFindItsStoredText`: erwartet einen Ticket- und einen Projekttreffer für exakt denselben gültigen Vollbreitentext; beide 0 (F1).
- `reviewDeclaredWordTokenizerMustAlsoApplyToIndexedEmailText`: erwartet einen Treffer für denselben gespeicherten E-Mail-Text; 0 (ebenfalls F1).
- `reviewTagRenameAndRebuildMustNotTakeTablesInOppositeOrder`: erwartet 200/erfolgreichen Neuaufbau; im eigenen Lauf 200/`PessimisticLockingFailureException` und echter PostgreSQL-Deadlock (F2).

Tatsächlich ausgeführt wurden die ersten zehn Proben gemeinsam (7 erfolgreich, 3 Fehlschläge) und anschließend nur der neue Plantest (1 erfolgreich). Eine gemeinsame Ausführung aller elf wird hier als Wiederholung angeboten, nicht als bereits ausgeführt behauptet.

## Aussagekraft der Fixtures

Die Wartetests verlangen die genaue Halter-PID in `pg_blocking_pids` des wartenden Vorgangs. Entzugsfixtures ändern synthetische Rechte/Mitgliedschaften unter Revisions-/Projektzeilensperre und erhöhen die zugehörige Version. Die Gegenrichtung einer bereits erlaubten Suche ist ein echter Dienstaufruf mit vollständigem aktuellem Identitätskontext in einer gehaltenen Transaktion gegen eine echte HTTP-Mitgliederentfernung.

Der Rebuild-/Umbenennungsversuch hält kurz `search_tag` im SHARE-Modus, damit beide tatsächlichen Produktwege ihre gegensätzliche Tabellenfolge erreichen. Diese Schranke wird vor dem Deadlock committet. Der Zyklus danach besteht zwischen wirklicher HTTP-Umbenennung und wirklichem Neuaufbaudienst. Der Serverlog bestätigt beide Kanten. Kein Produktpfad wurde ersetzt.

Zwei nur im synthetischen Testschema angelegte Trigger verweigern gezielt eine späte Projektionsänderung beziehungsweise -zuordnung. Fachdatensatz, Historie und Projektion müssen dadurch gemeinsam zurückrollen. Trigger und Funktion werden im Fixture entfernt. Die Fehlerantworten werden auf das Fehlen der künstlichen Ausnahmemeldung geprüft.

Der Planversuch deaktiviert sequentielle Scans ausschließlich lokal in einer synthetischen Transaktion und führt `EXPLAIN` ohne `ANALYZE` aus. Er belegt die unterschiedliche Indexverwendbarkeit, keine Last, Laufzeit oder SLA. Die Assertion erwartet die beobachtete Indexlücke; der erfolgreiche Plantest bedeutet nicht, dass F3 behoben wäre.

Nach den Proben wurde die einzige erweiterte Testdatei bytegleich wiederhergestellt und mit `git apply --check --unidiff-zero` die Anwendbarkeit des Dokumentationspatches geprüft. Die Originalklassen `SearchApiTest,SearchContractTest` wurden danach mit `clean test` ausgeführt; Ergebnis in `results.txt`. Der Patch bleibt Nachweis und wird nicht in die reguläre grüne Testsuite übernommen.
