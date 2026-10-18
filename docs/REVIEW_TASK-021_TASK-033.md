# Claude-Gegenprüfung von TASK-021 und TASK-033

Stand 18. Oktober 2026 · TASK-034 · Sitzung claude-20261018-review-01 · Technische Gegenprüfung, keine fachliche Abnahme

Dieser Bericht ist die nach DEC-040 vorgesehene gegenseitige KI-Prüfung durch eine Claude-Sitzung. Er bewertet die gelieferten Ergebnisse, ändert sie aber nicht. Über die Annahme der Befunde und der Ergebnisse entscheidet Holger. Status und Zuständigkeit von TASK-021 und TASK-033 bleiben unverändert.

## Geprüfter Stand

| Gegenstand | Commit | Geprüfte Inhalte |
|---|---|---|
| main zum Prüfbeginn | `c341fd43be3e3f3114b3e76ea550e4cdf38fd4f4` | Gesamtstand nach Integration des Claims #59 |
| TASK-021 | `0dc3a4c11fd95e37514c972e6abbda37b2244efe` (#56) | [TECHNICAL_BASELINE.md](TECHNICAL_BASELINE.md), `qualification/`, `contracts/`, Workflow `qualification.yml` |
| TASK-033 | `1906fc6d92c884316d763380e21982d2bcb164d1` (#58) | `scripts/task_board.py`, Änderungen an `scripts/validate_repository.py`, Tests, Workflow `repository-checks.yml`, Verfahrensbeschreibung |

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Umgebung | Ergebnis |
|---|---|---|
| Repository-Validator, Übersichtsprüfung, 25 Regressionstests, `git diff --check` | lokal, Python 3 | erfolgreich |
| Spring-Modulprüfung und Java-Transportfixture (4 Tests) | lokal, OpenJDK 21.0.12 statt Temurin 25 über `-Djava.version=21` | 4/4 bestanden; Abweichung der Java-Version bewusst, nur für diese beiden Testklassen |
| Ausgelöste Modulith-Verstöße im Wortlaut | lokal, eigener temporärer Test außerhalb des Repositorys | Zugriffsfall meldet „depends on non-exposed type … Secret“, Zyklusfall meldet „Cycle detected: Slice a → Slice b → Slice a“ |
| Aufgelöste Maven-Versionen | lokal, `dependency:tree` | Boot 4.1.1, Modulith 2.1.1, Hibernate 7.4.5.Final, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, Testcontainers 2.0.5, JUnit Jupiter 6.0.3 wie dokumentiert |
| Prüfsummen Maven 3.9.16 und Wrapper 3.3.4 | lokal, Download von Maven Central | SHA-256 des Maven-Archivs entspricht `distributionSha256Sum`; Wrapper-Archiv entspricht der veröffentlichten SHA-1 und dem dokumentierten SHA-256 |
| Herkunft der Wrapper-Skripte | Vergleich mit dem Wrapper-Archiv | `mvnw` bytegleich; `mvnw.cmd` inhaltlich gleich, nur Zeilenenden abweichend (siehe Q3) |
| Frontend `npm ci --ignore-scripts` und `npm run verify` | lokal, Node 22.22.0 / npm 10.9.4 statt 24.22.0 / 11.19.1 | Generierung ohne Abweichung, strenge Typprüfung, 6/6 Clienttests mit den lokal erzeugten Java-Fixtures, React-Build erfolgreich |
| Wirksamkeit eines statischen Negativbeispiels | lokal, `@ts-expect-error` vor dem PATCH-Aufruf vorübergehend entfernt | Compiler meldet genau das fehlende `If-Match` (TS2741); Datei danach unverändert wiederhergestellt |
| Vollständiger Lauf mit PostgreSQL | GitHub, [Lauf 39114361862](https://github.com/1uedev/task-manager/actions/runs/39114361862) auf `c341fd4` | Temurin 25.0.4.1, Node v24.22.0, npm 11.19.1; Java 8/8 ohne übersprungene Tests, PostgreSQL 18.6 über den festgelegten Digest, Folgeschritte erfolgreich (Protokoll gelesen) |
| Verhalten des Whitespace-Schritts | lokal nachgestellt mit Merge-Commit, voller und flacher Klon | siehe T2 |

Nicht ausgeführt: PostgreSQL-/Testcontainers-Tests lokal (kein Docker-Daemon in dieser Umgebung), Läufe mit exakt Temurin 25 und Node 24 lokal, Download der CI-Artefakte (vom Netzwerk dieser Umgebung abgewiesen; stattdessen Jobprotokoll gelesen), Prüfung der GitHub-Darstellung der Ankerlinks im Browser, Lizenzprüfung transitiver Komponenten.

## Ergebnis TASK-033

Keine blockierenden Befunde. Die Übersicht wird ausschließlich aus den Aufgabeneinträgen erzeugt, weist inkonsistente Besitzer, unbekannte Status, doppelte IDs, ungültige Daten und fehlerhafte Überschriften vor jedem Schreiben zurück und verändert außerhalb des Markerpaars nichts. Die Tests decken diese Fälle ab. Ein nützlicher Nebeneffekt: Zwei gleichzeitige Claim-PRs ändern dieselbe Zählzeile und geraten dadurch sichtbar in einen Git-Konflikt.

| ID | Schwere | Befund | Vorschlag |
|---|---|---|---|
| T1 | niedrig | Der Validator überspringt Links, die mit `#` beginnen (`scripts/validate_repository.py`, Linkprüfung). Die Ankerlinks der Übersicht hängen deshalb allein an `Task.anchor` in `scripts/task_board.py`. Für die heutigen Titel stimmt die Nachbildung des GitHub-Verfahrens; Zeichen wie Emoji oder kombinierende Akzente könnten abweichen, ohne dass eine Prüfung es meldet. | Hinnehmbar. Bei Bedarf einen Test mit einer Liste bekannter GitHub-Anker ergänzen oder Titel auf einfache Zeichen begrenzen. |
| T2 | Hinweis | Der Schritt `git show --format= --check HEAD` in `repository-checks.yml` prüft nur deshalb den gesamten Baum, weil `actions/checkout` flach klont und der Merge-Commit dann keine Eltern hat. Nachgestellt: Bei vollem Klon zeigt `git show` für einen Merge-Commit nur den kombinierten Diff, und ein Leerzeichenfehler aus dem PR-Zweig bleibt unbemerkt. Der Schritt stammt nicht aus TASK-033. | Kommentar im Workflow oder robustere Prüfung, zum Beispiel `git diff --check` gegen die Basis, falls später `fetch-depth` geändert wird. |

## Ergebnis TASK-021

Die dokumentierten Versionen, Prüfsummen, Testzahlen und Aussagegrenzen sind zutreffend; die Kernaussagen zu Modulprüfung, Transaktion, Generator und Client sind durch Quellen und CI belegt. Keine blockierenden Befunde. Die folgenden Punkte betreffen Nachweislücken und Übergaben an TASK-022 und TASK-023.

| ID | Schwere | Befund | Vorschlag |
|---|---|---|---|
| Q1 | mittel | Die Vertragsregel „beteiligte Operationen verlangen eine bestehende Transaktion“ ([TECHNICAL_BASELINE.md](TECHNICAL_BASELINE.md), Zeile Schreibtransaktion) ist mit `Propagation.MANDATORY` umgesetzt (`RecordService`, `JdbcProjection`), aber nicht negativ geprüft. Fiele die Annotation weg, blieben alle Tests grün, weil `Composition.create` immer eine Transaktion öffnet. | Einen Test ergänzen, der `Records.create` oder `Projection.index` ohne Transaktion aufruft und `IllegalTransactionStateException` erwartet. Spätestens beim Übertrag nach TASK-022 nachholen. |
| Q2 | niedrig | `ModuleQualificationTest` prüft in beiden Negativfällen nur, dass irgendeine `Violations` auftritt. Heute sind es die beabsichtigten Verstöße (oben im Wortlaut geprüft); ein anderer Verstoß würde die Tests aber ebenfalls bestehen lassen. | Zusätzlich die Meldung prüfen, etwa „non-exposed type“ beziehungsweise „Cycle detected“. |
| Q3 | niedrig | `qualification/backend/mvnw.cmd` liegt wegen `* text=auto eol=lf` in `.gitattributes` mit LF-Zeilenenden vor; das Originalarchiv verwendet CRLF. `cmd.exe` kann Sprungmarken in Batchdateien mit LF falsch auflösen. Windows gehört bisher nicht zur geprüften Plattform. | `*.cmd text eol=crlf` in `.gitattributes`, spätestens bevor Windows-Entwicklung unterstützt wird. |
| Q4 | niedrig | `TransportFixtureTest.java` Zeile 18 verwendet das in Jackson 3.1.5 veraltete `JsonNode.asText()`; das CI-Protokoll meldet die Deprecation. | `asString()` verwenden. |
| Q5 | Hinweis | Das Schema `Problem` in `contracts/qualification.openapi.yaml` hat `additionalProperties: false`. Die Standardfelder `detail` und `instance` aus RFC 9457 und Spring `ProblemDetail` wären damit ungültig. Für die synthetische Probe richtig, als Vorbild für die Produkt-API zu eng. | Im produktiven Vertrag unter `/api/v1` Standard- und Erweiterungsfelder ausdrücklich zulassen. Übergabe an TASK-022/TASK-023. |
| Q6 | Hinweis | `RecordService.create` ruft selbst `saveAndFlush` auf. Die Regel „JPA wird vor abhängigem JDBC-Zugriff geflusht“ nennt nicht, wer flusht. Flusht jede schreibende Moduloperation selbst, entstehen bei größeren Abläufen unnötige Datenbankrunden. | Im Modulvertrag festlegen, ob die schreibende Operation oder der Orchestrator flusht. |
| Q7 | Hinweis | Das CI-Artefakt `qualification-evidence` des im Bericht genannten Laufs 39063473112 läuft am 24. Oktober 2026 ab; der Bericht nennt die siebentägige Aufbewahrung. Der aktuelle main-Lauf 39114361862 hält ein gleichwertiges Artefakt bis 25. Oktober 2026. | Falls Holger die Berichte selbst einsehen will, vorher herunterladen oder einen neuen Lauf über `workflow_dispatch` starten. |

## Abgrenzung

Dieser Bericht ist eine technische Gegenprüfung durch eine Claude-Sitzung. Er ist kein Nachweis einer fachlichen Abnahme und keine menschliche technische Prüfung. Er hebt keine Blockierung von TASK-022 bis TASK-031 auf und erteilt keinen Implementierungsauftrag. Die Befunde Q1 bis Q4 und T1 bis T2 werden nicht in diesem Ergebnis korrigiert, weil TASK-034 die geprüften Ergebnisse nicht ohne gesonderte Abstimmung ändert.

**Nachtrag aus TASK-035 am 18. Oktober 2026:** Holger hat entschieden, Q1 bis Q4 sofort zu beheben. Die Korrekturen und ihre Nachweise stehen in [TECHNICAL_BASELINE.md](TECHNICAL_BASELINE.md) im Nachtrag aus TASK-035. T1, T2 und Q5 bis Q7 bleiben offen.
