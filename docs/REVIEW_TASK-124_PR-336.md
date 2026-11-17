# Prüfbericht zu TASK-124 / PR #336

**Empfehlung: nachbessern.** Zwei Stellen verletzen die zugesagte Identität beziehungsweise Rückgabe von JDBC-Originalen. Außerdem kann ein direkter Aufruf an einer beobachteten Verbindung zusätzliche SQL ausführen, während der Test weiterhin genau eine Ausführung meldet.

**Prüfstand** am 17. November 2026. Head `eeb7906a23ede1f8ad3ab09b88f509bb91682bbb`, Basis `1b484aee36e853e8c5c4cd49661dec762d94f77a`, CI-Merge-Stand `22612139ae3c3d80461add09c680d3a13a8e1153`. Geprüft gegen TASK-124, DEC-153, DEC-154 und den Bericht zu TASK-123. Der PR ändert vier Dateien in Testcode, Dokumentation und Aufgabenübergabe. PR #336 · [TASK-124](../TODO.md#task-124-befunde-f1-bis-f3-und-hinweis-h1-der-codex-prüfung-von-task-123-beheben) · [Entscheidungen](../DECISIONS.md) · [Vorgängerbericht](REVIEW_TASK-123_PR-334.md)

Codex konnte den Bericht nicht selbst veröffentlichen. Holger hat ihn am 17. November 2026 im Projektgespräch übergeben, und er wurde inhaltlich unverändert übernommen.

## Befunde vor der Abnahme

### F1 – Mittel – SQL aus Methoden der beobachteten Verbindung fehlt in der Zählung

- **Datei und Zeile.** [ObservedConnections.java (line 450)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L450) · [ObservedConnections.java (line 465)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L465) · [GitApiTest.java (line 1816)](../backend/src/test/java/taskmanager/git/GitApiTest.java#L1816)
- **Szenario und Folge.** Die Schlussprüfung führt den gezählten Join aus und ruft auf derselben beobachteten Verbindung zusätzlich `getSchema()` auf. Der im Projekt verwendete PostgreSQL-Treiber 42.7.13 führt in `PgConnection.getSchema()` intern `select current_schema()` aus. `ObservedConnections` zählt ausschließlich Aufrufe am Statement-Proxy. Bei `getSchema()` delegiert die Hülle direkt an das Original und gibt den String regulär zurück. Die zusätzliche Abfrage bleibt ungezählt. Eine lokale Java-Gegenprobe mit demselben Delegationsverhalten führte zwei Abfragen am Treiberdoppel aus und zeichnete nur eine auf. Der Bytecode des gepinnten Treibers bestätigt den internen Aufruf. Der aktuelle Produktpfad verwendet `getSchema()` nicht.
- **Vorschlag.** Den unterstützten JDBC-Pfad der Schlussprüfung explizit absichern, etwa indem zusätzliche JDBC-Methoden, die selbst Abfragen auslösen, im Aufzeichnungsfenster sauber verweigert werden. Alternativ die von der Anwendung ausgelösten Abfragen auf Treiber- oder Datenbankebene erfassen. Die Gegenprobe mit `getSchema()` muss die zusätzliche Abfrage sichtbar machen oder den Aufruf verweigern.

### F2 – Niedrig – Eine identische Verbindung erhält mehrfach eine Hülle

- **Datei und Zeile.** [ObservedConnections.java (line 388)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L388) · [ObservedConnections.java (line 412)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L412) · [ObservedConnectionsTest.java (line 467)](../backend/src/test/java/taskmanager/git/internal/ObservedConnectionsTest.java#L467)
- **Szenario und Folge.** Die Datenquelle und ihr Builder geben dasselbe `Connection`-Objekt zurück. Die Java-Gegenprobe mit einer an Hikari gesetzten Datenquelle erhält von `getConnection()` und `build()` zwei verschiedene Hüllen. Dasselbe tritt innerhalb einer einzigen verschachtelten Rückgabe auf, wenn ein `Connection[]` zweimal das gleiche Original enthält. Für jede Rückgabe der Verbindung wird vor der Identitätssuche ein neuer `Observed`-Bereich angelegt. Die Ausführungen beider Hüllen bleiben zählbar, die Identitätsgarantie aus Regel 2 und der Dokumentation ist verletzt. Das Testdoppel im PR liefert bei jedem Aufruf eine neue Verbindung und deckt den Fall daher nicht ab.
- **Vorschlag.** Bereits ausgegebene Hüllen für ein identisches Verbindungsobjekt wiederfinden. Die Lebensdauer der Zuordnung so wählen, dass abgeschlossene Pool-Ausleihen keine dauerhaften starken Referenzen erzeugen. Beide Varianten mit einer expliziten Identitätsassertion prüfen.

### F3 – Niedrig – Die äußere Hikari-Hülle wird als Argument nicht als Hülle erkannt

- **Datei und Zeile.** [ObservedConnections.java (line 128)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L128) · [ObservedConnections.java (line 258)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L258) · [ObservedConnections.java (line 436)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L436)
- **Szenario und Folge.** Zwei echte `HikariDataSource`-Instanzen werden beobachtet. Die erste beobachtete Instanz wird der zweiten über `setDataSource(...)` übergeben. Die Gegenprobe zeigt, dass der zweite echte Pool den Mockito-Mock statt des ersten echten Pools speichert. `originalOf` erkennt nur JDK-Proxys mit `Handler`, während die äußere Datenquelle ein Mockito-Mock ist. Damit gilt die zugesagte Rückgabe des Originals für diese Hülle weder als direktes Argument noch als Element eines Objektarrays.
- **Vorschlag.** Auch die äußere beobachtete Datenquelle bei der Identitätsprüfung für Argumente erkennen, während ihr konkreter Typ für Spring und Hikari erhalten bleibt. Die Gegenprobe muss am empfangenden echten Pool die Identität des Originals prüfen.

## Hinweise

### H1 – SQL auf einem anderen Thread liegt außerhalb der Aufzeichnung

- **Datei und Zeile.** [ObservedConnections.java (line 73)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L73) · [ObservedConnections.java (line 178)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L178) · [ObservedConnections.java (line 225)](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L225) · [Backend-README:663 (line 663)](../backend/README.md#L663)
- **Szenario und Vorschlag.** Selbst ein beobachtetes Statement schreibt bei einer Ausführung auf einem anderen Thread nichts in die laufende Aufzeichnung. Der aufrufende Thread könnte das Ergebnis abwarten und anschließend normal zurückkehren. Die aktuelle Schlussprüfung arbeitet synchron. Diese Threadgrenze im Kommentar und in der README ausdrücklich nennen.

### H2 – Gebundene Beispielparameter sollten zum SQL-Typ passen

- **Datei und Zeile.** [ObservedConnectionsTest.java (line 51)](../backend/src/test/java/taskmanager/git/internal/ObservedConnectionsTest.java#L51) · [ObservedConnectionsTest.java (line 145)](../backend/src/test/java/taskmanager/git/internal/ObservedConnectionsTest.java#L145) · [V0064__git_baseline.sql (line 9)](../backend/src/main/resources/db/migration/V0064__git_baseline.sql#L9)
- **Szenario und Vorschlag.** Beide Unwrap-Ausführungen binden jetzt einen Wert. Der Join vergleicht `git_provider.id` vom Typ UUID aber mit den Beispielstrings `"first"` und `"second"`. Das Testdoppel erlaubt diese Werte, PostgreSQL akzeptiert sie mit der regulären String-Bindung für diesen Vergleich nicht. Synthetische UUIDs binden oder für die Zählprobe SQL mit einem passenden Parametertyp verwenden.

## Abgleich mit TASK-124

| Prüfpunkt | Ergebnis |
|---|---|
| Regel 1, Datenquelle und Builder | Der PR-Test nutzt einen echten Hikari-Pool mit gesetztem `DataSource`-Doppel. Er zählt vier Ausführungen über die zurückgegebene Datenquelle, beide `getConnection`-Varianten und die vom Builder erzeugte Verbindung. Der Builder stammt aus der gesetzten Datenquelle. Der echte Hikari-Pool und die JDBC-Integration laufen zusätzlich im Backend-CI. Die Ausnahmen bei der Identität und Argumentübergabe stehen in F2 und F3. |
| Regel 2, Objektarrays | Beide Richtungen kopieren mit dem ursprünglichen Komponententyp, einem Identitätsregister pro Aufruf und Schutz vor Zyklen. Die PR-Tests decken mehrere Ebenen, Wiederverwendung einer inneren Argumentliste, Selbstreferenzen, unveränderte Originalarrays und dieselbe Statement-Hülle bei mehreren Vorkommen ab. Identische Verbindungen aus einer Datenquelle sind die Ausnahme in F2. |
| Regel 3, typisierte Abrufe | Eine konkrete `RowId`-Klasse führt während der Aufzeichnung zur erwarteten `SQLException`, außerhalb kommt das Original zurück. Abrufe über das JDBC-Interface erhalten den Proxy. `UUID` und `UUID[]` im heutigen Join-Pfad bleiben für PostgreSQL typgerecht. [GitStore.java (line 94)](../backend/src/main/java/taskmanager/git/internal/GitStore.java#L94) |
| Regel 4 und zusätzliche Parameterbindungen | Die Unwrap-Gegenprobe bindet vor beiden Ausführungen. Auch die weiteren Ausführungen und Batches mit Platzhaltern haben entsprechende Bindungen, während SQL für Statements ohne Parameter keinen Platzhalter enthält. Die Ausweitung ist sachgerecht. H2 betrifft die Typen im Test, nicht die Anzahl der Bindungen. |
| Zusätzliche Behandlung von `SQLData` | Vertretbar. Auch ein untypisierter Abruf mit Typzuordnung kann ein Anwendungsobjekt liefern, dessen konkreter Typ erhalten bleiben muss. Außerhalb der Aufzeichnung kommt das Objekt unverändert zurück. Während der Aufzeichnung wird es auch in verschachtelten Rückgaben mit `SQLException` verweigert. Der Rückruf des Treibers kann vor der Prüfung der Rückgabe stattfinden. |
| Grenzen und heutige Schlussprüfung | Die vier dokumentierten Grenzen sind zutreffend und für den synchronen Produktpfad vertretbar. `keepCurrent` liest bei vorhandenen Generationen einmal über `providersWithConnection` und liefert bei leerer Providermenge vor dem Lesen zurück. Im aktuellen Code wurde kein zusätzlicher ungezählter Anwendungsabruf gefunden. Die dokumentierte Grenze deckt treiberintern ausgelöste SQL und andere Threads noch nicht ab. F1 zeigt, wie eine zusätzliche Abfrage über ein bereits beobachtetes JDBC-Objekt die Zählung künftig passieren kann. [ConnectionService.java (line 269)](../backend/src/main/java/taskmanager/git/internal/ConnectionService.java#L269) · [GitStore.java (line 94)](../backend/src/main/java/taskmanager/git/internal/GitStore.java#L94) |

## Ausgeführte Prüfungen

Für die lokalen Läufe wurde der Stand aus der separaten Prüffassung zu PR #332 und den anhand ihrer Git-Hashes verifizierten Dateien der Folge-PRs rekonstruiert. Der rückwärts angewendete Diff ergab für beide Fassungen genau die vier PR-Dateien. Der Hash der Hülle aus der Basis entsprach dem GitHub-Blob. Die Gegenproben liefen mit Java 25 außerhalb des Repository-Quellbaums.

| Prüfung | Ergebnis |
|---|---|
| `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `git diff --check` | Lokal und in CI grün. Repository-CI |
| `python3 -m unittest discover -s tests -v` | Lokal gestartet. Zehn Fälle schlugen beim Zugriff auf lokale HTTP-Sockets fehl oder erreichten ihren Testserver nicht (WinError 10013), ein Fall wurde übersprungen. In CI 50 Tests grün. Repository-CI |
| Backend | Lokal `ObservedConnectionsTest` 16/16, `GitCacheTest` 3/3 und `GitAddressTest` 1/1 grün. Die lokalen Läufe von `GitApiTest`, `GitContractTest` und `GitHistoryMigrationTest` scheiterten beim Datenbankzugriff oder Anwendungsstart. Die lokale PostgreSQL-Gegenprobe meldete eine verweigerte Socketverbindung. In CI 693 Tests mit PostgreSQL 18.6 grün, einschließlich `GitApiTest` 31/31, `ObservedConnectionsTest` 16/16, `GitCacheTest` 3/3, `GitAddressTest`, `GitContractTest` und `GitHistoryMigrationTest` je 1/1. Backend-CI |
| `npm run verify` | Lokal und in CI grün, jeweils 102 Unit-Tests und erfolgreicher Build. Frontend-CI |
| `gitLinks.test.mjs` und `git.test.mjs` | Lokal 2/9 grün, sieben Browserfälle scheiterten mit `ERR_NETWORK_ACCESS_DENIED` beim Zugriff auf localhost. In CI sind beide Dateien in der vollständigen Browsersuite mit 310 grünen Tests enthalten. Frontend-CI |
| `tools/demo/image-check.sh` und `tools/git/gitlab-check.sh` | Lokal fehlte ein erreichbarer Docker-Daemon. Imagecheck in der PR-CI grün. Der manuelle GitLab-Lauf checkte den exakten Head aus und bestand mit GitLab CE 16.11.10. Image-CI · GitLab-CI |

## Gegenprobe mit der Hülle aus TASK-123

Mit unveränderten Tests aus PR #336 und ausschließlich der ausgetauschten Hülle von der PR-Basis blieben elf Tests grün. Genau die folgenden fünf schlugen fehl. Mit der Hülle des PR waren alle 16 grün.

| Test | Ergebnis mit TASK-123 |
|---|---|
| `aSecondExecutionThroughTheDataSourceSetAtThePoolCounts` | Die aus Hikari zurückgegebene Datenquelle ist unbeobachtet. |
| `aSecondExecutionThroughAStatementInANestedArrayCounts` | Das Statement im inneren Array ist unbeobachtet. |
| `anObservedObjectInANestedArrayReachesTheDriverAsTheOriginal` | Das Treiberdoppel lehnt die Hülle im verschachtelten Argument ab. |
| `aReadThatNamesAClassReturnsThatClassOrFailsWhileRecording` | `ClassCastException` bei `Key`. |
| `anObjectOfAClassOfTheApplicationIsNeverWrapped` | `ClassCastException` bei `Money`. |
