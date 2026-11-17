# Prüfbericht zu TASK-123 / PR #334

**Empfehlung: nachbessern.** Die direkten Hikari- und PostgreSQL-Array-Rückwege aus dem Vorgängerbericht sind im Quellcode korrigiert. Die allgemeine Regel hat noch zwei Lücken bei der SQL-Zählung und eine beim typisierten `getObject`.

Die lokale Ausführungsumgebung war nicht verfügbar. Der Bericht konnte deshalb nicht als docs/REVIEW_TASK-123_PR-334.md abgelegt werden und steht hier vollständig. Die Codeprüfung und die Auswertung der CI-Protokolle wurden unabhängig durchgeführt.

**Prüfstand** · 16. November 2026. Head `c9aac92acf7d6ca60b7712a8e6f08d9569a1f57f`, Basis `a9039cd7804f437a5137e2b4e761026c5a17f60c`, Merge auf main `d804c630396e52b2f4a051b4db1c6ddd3779fb16`. Die vier geänderten Dateien betreffen Testcode, Dokumentation und Aufgabenübergabe. PR #334 · [TASK-123](../TODO.md) · [DEC-152 und DEC-153](../DECISIONS.md)

Codex konnte den Bericht nicht selbst veröffentlichen. Holger hat ihn am 16. November 2026 im Projektgespräch übergeben, und er wurde inhaltlich unverändert übernommen.

## Befunde vor der Abnahme

### F1 · Mittel · Die Datenquelle gibt weitere JDBC-Objekte direkt heraus

- **Datei und Zeile.** [ObservedConnections.java:149](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L149)
- **Szenario und Folge.** Ist an Hikari eine Datenquelle explizit konfiguriert, liefert `getDataSource()` sie zurück. Der Mock delegiert den Getter und umhüllt als Rückgabe ausschließlich `Connection`. Eine Abfrage über die zurückgegebene Datenquelle bleibt damit ungezählt. Dasselbe gilt für `createConnectionBuilder().build()` bei einer Datenquelle, die den Builder unterstützt. Nach einem gezählten Join kann die Assertion auf genau eine Ausführung weiterhin grün bleiben. HikariConfig 7.0.2 · JDBC DataSource
- **Vorschlag.** JDBC-Rückgaben der Datenquelle über dieselbe Beobachtungs- und Verweigerungsregel wie Verbindungsrückgaben führen. Eine Gegenprobe mit einer explizit gesetzten Hikari-Datenquelle ergänzen.

### F2 · Mittel · Verschachtelte Objektarrays werden nur auf der ersten Ebene geprüft

- **Datei und Zeile.** [ObservedConnections.java:294](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L294) · [ObservedConnections.java:318](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L318)
- **Szenario und Folge.** Liefert ein JDBC-Testdoppel über `getObject()` ein `Object[][]` mit einem Statement im inneren Array, kopiert die Hülle nur das äußere Array. Das innere Array implementiert kein JDBC-Interface und wird übersprungen. Über das ursprüngliche Statement kann eine weitere Abfrage ungezählt ausgeführt werden. Auch in Gegenrichtung bleibt die Verarbeitung flach. Bei einem verschachtelten Attributarray für `createStruct(...)` kann der Treiber den beobachteten Proxy anstelle des Originals erhalten.
- **Vorschlag.** Objektarrays in beiden Richtungen rekursiv verarbeiten. Dabei Komponententyp und Objektidentität berücksichtigen. Beide Übergaberichtungen mit einem verschachtelten Array prüfen.

### F3 · Niedrig · Typisiertes `getObject` kann den angeforderten Java-Typ verletzen

- **Datei und Zeile.** [ObservedConnections.java:287](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L287) · [ObservedConnections.java:379](../backend/src/test/java/taskmanager/git/internal/ObservedConnections.java#L379)
- **Szenario und Folge.** Unterstützt ein Treiber `getObject(1, MeinSqlTyp.class)` für eine konkrete `SQLData`-Klasse, prüft die Hülle ihren Interface-Proxy nur gegen den durch Typlöschung sichtbaren Rückgabetyp `Object`. Der Proxy ist keine Instanz von `MeinSqlTyp`. Die Zuweisung scheitert beim Aufrufer mit `ClassCastException`, auch außerhalb der Aufzeichnung. docs.oracle.com
- **Vorschlag.** Die übergebene `Class` bei typisierten JDBC-Rückgaben berücksichtigen. Falls der Proxy den Typ nicht erfüllt, während der Aufzeichnung mit `SQLException` abbrechen und außerhalb den typgerechten Originalwert weitergeben.

## Hinweise

### H1 · Die erste Ausführung der Unwrap-Gegenprobe lässt den Parameter offen

- **Datei und Zeile.** [ObservedConnectionsTest.java:119](../backend/src/test/java/taskmanager/git/internal/ObservedConnectionsTest.java#L119)
- **Szenario und Vorschlag.** Die zweite Ausführung ist jetzt gültig. Sie bindet den Parameter und ruft `PreparedStatement.executeQuery()` ohne SQL-Argument auf. Der erste Aufruf verwendet denselben Join mit `?` ohne Bindung. Auch vor der ersten Ausführung einen Wert binden, damit beide Aufrufe an einem echten Treiber funktionieren.

## Abgleich mit TASK-122

| Prüfpunkt | Ergebnis |
|---|---|
| Hikari `unwrap` und `isWrapperFor` | Für `DataSource.class` und `HikariDataSource.class` gibt `unwrap` den beobachteten Mock zurück und `isWrapperFor` liefert `true`. Der Zweig liegt vor der Delegation und gilt auch außerhalb der Aufzeichnung. Fremde Ziele werden während der Aufzeichnung verweigert und sonst delegiert. Der neue Test verwendet einen echten `HikariDataSource`. |
| Echtes `PgArray` und `PgResultSet` | Der Treiber erzeugt beim Array-Rückweg ein eigenes Statement. Dessen `PgResultSet.getStatement()` und `Statement.getConnection()` liefern direkte JDBC-Objekte, die der PR umhüllt. Kommt dasselbe Treiberobjekt zurück, wird dessen Hülle wiederverwendet. Eine vom Hikari-Proxy verschiedene physische Verbindung bekommt eine eigene beobachtete Hülle. Der Treiberquellcode bestätigt den Rückweg. Der PR-Test nutzt dafür ein Testdoppel, eine erneute Java-Probe mit den echten Treiberobjekten blieb offen. PgArray 42.7.13 · PgResultSet 42.7.13 |
| Weitere JDBC-Typen und Argumente | Direkte Rückgaben von `Struct`, `Ref`, Metadaten und großen Objekten werden anhand ihrer JDBC-Interfaces erkannt. Direkt übergebene eigene Proxys erreichen den Treiber als Originale. Die Grenzen bei `javax.sql`-Rückgaben, verschachtelten Werten und typisierten Rückgaben stehen in F1 bis F3. |
| Gegenprobe gegen TASK-122 | Der Quellvergleich erklärt alle drei vom Autor gemeldeten Fehlschläge. Der alte Hikari-Pfad gibt den ursprünglichen Pool zurück. Der alte Typfilter lässt `Array` und `Blob` unbeobachtet. Eine unabhängige JVM-Wiederholung war mangels Ausführungsumgebung nicht möglich. Hülle der PR-Basis · Eigenbeleg im PR |

## Verifizierte Prüfungen

| Angeforderte Prüfung | Ergebnis für den PR-Head |
|---|---|
| `validate_repository.py`, `task_board.py --check`, `unittest`, Whitespace | Grün, 50 Python-Tests. Repository-CI |
| Backend | 688 Tests grün. Darunter `GitApiTest` (31), `ObservedConnectionsTest` (11), `GitCacheTest` (3), `GitAddressTest` (1) und `GitContractTest` (1). Backend-CI |
| `npm run verify` und Browsertests | 102 Unit-Tests und Build grün. Die vollständige Browsersuite einschließlich der Git-Tests bestand mit 310 Tests. Frontend-CI |
| `tools/demo/image-check.sh` | Grün. Image-CI |
| `tools/git/gitlab-check.sh` | Grün im manuell gestarteten Lauf mit GitLab 16.11.10. GitLab-CI |
