# Getrennte technische Prüfung von TASK-022 / PR #65

Datum: 18. Oktober 2026 · Prüfsitzung: `codex-20261018-backend-review-01`

Geprüft wurde [PR #65](https://github.com/1uedev/task-manager/pull/65), Branch `claude/project-thread-qfsobf`, mit Head `bc48d1259f6028b15c6434f31c64416668b280a5` gegen main `26ac52dfc5589df6f53f7d00594744174d204f08`. Eigenes Arbeitsverzeichnis und eigener Branch `review/codex-20261018-backend-review-01`; keine Änderungen am geprüften Branch, an TODO.md oder DONE.md, keine Veröffentlichung eines GitHub-Reviews oder Kommentars.

Grundlage sind AGENTS.md, README.md, TODO.md einschließlich TASK-022, DONE.md, CLAUDE.md, docs/AI_WORKFLOW.md, die einschlägigen Anforderungen und Entscheidungen, ROADMAP.md sowie Architektur, TECHNICAL_CONTRACTS.md, DEVELOPMENT_START.md und TECHNICAL_BASELINE.md. DEC-040 bestätigt die getrennte KI-Prüfung. Die begrenzte Freigabe von TASK-022 wird im main-Aufgabeneintrag dokumentiert und im Ergebnis als DEC-055 nachgeführt. Daraus folgt keine Freigabe weiterer Produktaufgaben oder fachliche Abnahme.

## Ergebnis

Ein mittlerer Befund zur Rückgabe von Eingabewerten und ein niedriger Befund zur Robustheit des Lebenszyklus-Prüfskripts. Keine weiteren konkreten Befunde an der vorhandenen Startmigration oder den sieben Moduldeklarationen. Empfehlung: R1 vor Übernahme der Fehlerbehandlung korrigieren und gezielt nachprüfen. R2 ist eine Verbesserung des Fehlerpfads im Prüfwerkzeug; der erfolgreiche Lebenszykluslauf wird dadurch nicht widerlegt.

## Befunde

### R1 · P2 / mittel · Anfragepfad spiegelt Eingabewerte in `instance` zurück

**Fundstelle:** [ProblemResponses.java, Zeilen 35–46](https://github.com/1uedev/task-manager/blob/bc48d1259f6028b15c6434f31c64416668b280a5/backend/src/main/java/taskmanager/platform/web/ProblemResponses.java#L35-L46); zugehörige Prüflücke in [ProblemResponsesTest.java, Zeilen 46–50](https://github.com/1uedev/task-manager/blob/bc48d1259f6028b15c6434f31c64416668b280a5/backend/src/test/java/taskmanager/platform/web/ProblemResponsesTest.java#L46-L50).

**Befund:** Der Handler entfernt `detail` und zusätzliche Eigenschaften, setzt aber keine bereinigte `instance`. Spring MVC ergänzt danach den unveränderten Anfragepfad. Beispiel für eine gezielte Gegenprobe: `GET /api/v1/unknown/synthetic-private-value` führt auf diesem Fehlerweg zu einer 404-Problemantwort mit `instance: "/api/v1/unknown/synthetic-private-value"`. Damit werden in Pfadsegmenten übergebene Werte trotz gelöschtem `detail` zurückgespiegelt. Der bestehende Test übergibt den Marker lediglich als Queryparameter und prüft für diesen Fall nur das Fehlen von `detail`.

Das ist eine Abweichung von der im Auftrag und im technischen Vertragsentwurf verlangten Regel, keine Eingabewerte zurückzugeben. Es wird keine Offenlegung fremder Daten oder bereits vorhandener echter Geheimnisse behauptet. Dass backend/README.md den Anfragepfad ausdrücklich beschreibt, beseitigt den Widerspruch zur strengeren Regel nicht.

**Nachweisart:** Statischer Nachweis am PR-Code und am zur CI-Abhängigkeit passenden [Spring-Framework-Quellstand 7.0.9, HttpEntityMethodProcessor](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/HttpEntityMethodProcessor.java#L191-L195). Dort wird bei fehlender `instance` unmittelbar `getRequestURI()` übernommen. Keine eigene HTTP-Reproduktion ausgeführt.

**Vorschlag:** `instance` vor Rückgabe explizit auf eine vom Server erzeugte opake URI, beispielsweise eine aus der Korrelationskennung abgeleitete URN, setzen. Nur auf `null` setzen genügt wegen der anschließenden Spring-Ergänzung nicht. Dokumentation entsprechend anpassen. Einen Test mit synthetischem Marker im Pfad und im Query ergänzen, der den gesamten Antwortkörper auf den Marker prüft und weiterhin Status, Code sowie übereinstimmende Korrelationskennungen nachweist. Den dokumentierten Boot-Fallback außerhalb der Controller bei dieser Regel ebenfalls berücksichtigen.

### R2 · P3 / niedrig · Lebenszyklusprüfung hat unbeschränkte Wartepfade und keine Prozessbereinigung bei Fehlern

**Fundstelle:** [lifecycle-check.sh, Zeilen 35–55](https://github.com/1uedev/task-manager/blob/bc48d1259f6028b15c6434f31c64416668b280a5/backend/scripts/lifecycle-check.sh#L35-L55).

**Befund:** Die Readiness-Schleife begrenzt die Anzahl der Versuche, aber die beiden `curl`-Aufrufe haben kein Verbindungs- oder Antwortzeitlimit. Nimmt der Server die Verbindung an und liefert keine vollständige Antwort, kann bereits ein einzelner Versuch hängen. Auch `wait "$pid"` nach SIGTERM ist unbeschränkt. Bei einem vorherigen Fehler beendet `fail` das Skript ohne EXIT-Trap für den gestarteten Prozess. Im CI beendet schließlich das globale 20-Minuten-Limit den Job; bei lokaler Nutzung gibt es diese äußere Grenze nicht. Dadurch fehlen bei solchen Fehlern ein zeitnaher gezielter Befund und eine verlässliche Bereinigung.

**Nachweisart:** Quellprüfung, keine simulierte Hänge- oder Abbruchprobe. Der vorhandene erfolgreiche CI-Lauf hat diese Fehlerpfade nicht ausgelöst.

**Vorschlag:** Kurze Verbindungs- und Gesamtzeitlimits für HTTP-Proben, eine Gesamtschranke für Start und Stopp sowie eine EXIT-Trap zur Bereinigung des eigenen Kindprozesses ergänzen. Bei Überschreitung Protokoll und Phase ausgeben. Das normale SIGTERM-Ergebnis mit geordnetem Herunterfahren weiterhin prüfen; eine erzwungene Bereinigung darf nicht als erfolgreicher Stopp zählen.

## Prüfung der vier Schwerpunkte

| Schwerpunkt | Bewertung des geprüften Umfangs |
|---|---|
| Migration | Flyway-Pfad ist festgelegt; `ddl-auto: validate`, `clean-disabled: true`, `baseline-on-migrate: false` und Namensvalidierung sind gesetzt. V0001 enthält ausschließlich Kommentare. MigrationConventionTest prüft vierstellige Nummer, bekannten Modulbesitzer, Eindeutigkeit und Beginn bei V0001. Der Datenbanktest erwartet nur die Flyway-Historientabelle. Keine konkrete Abweichung an diesem Stand gefunden. Objektbesitz und Unveränderlichkeit späterer Migrationen bleiben zusätzlich im jeweiligen Review zu prüfen. |
| Modulgrenzen | Genau `platform`, `identity`, `permissions`, `projects`, `tickets`, `tags`, `search` sind deklariert und werden im Test gezählt. `platform` erlaubt keine Modulabhängigkeit; Identität nutzt nur platform, Rechte zusätzlich identity, Projekte zusätzlich permissions, die drei Fachmodule zusätzlich projects. Keine Zyklen oder Zugriffe auf fremde interne Pakete im vorhandenen Code festgestellt. Die explizit leere Abhängigkeitsliste ist laut [Modulith 2.1.1](https://github.com/spring-projects/spring-modulith/blob/2.1.1/spring-modulith-api/src/main/java/org/springframework/modulith/ApplicationModule.java#L45-L58) tatsächlich restriktiv. Die Detailordnung ist ein zum Vertragsentwurf passender Umsetzungsvorschlag; sie ist keine pauschale fachliche Freigabe. Die spätere Zusammensetzung von Tickets, Tags und Suche bleibt ausdrücklich offen. |
| Konfiguration und Fehler | Pflichtangaben werden vor regulärer Bean-Erzeugung auf fehlende, leere oder nicht auflösbare Werte geprüft. Die eigene Fehlermeldung verwendet nur Variablennamen. UUID-Korrelationskennung wird serverseitig erzeugt, als Antwortheader und Requestattribut gesetzt und im synchronen Request über MDC protokolliert. Die geprüften CI-Fälle bestätigen identische Kennungen in Header und Problemkörper sowie das Ignorieren eines Clientwerts. `detail` und Erweiterungen werden bereinigt; unerwartete Ausnahmemeldungen und Stacktraces gelangen im geprüften Controllerfall nicht in den HTTP-Körper. R1 bleibt offen. Der dokumentierte Boot-Fallback außerhalb der Controller liefert nicht denselben vollständigen Problemvertrag. |
| CI und Lebenszyklus | Lesende Repository-Rechte, festgelegte Action-Commits, Temurin-Version und PostgreSQL-Digest. Backendtests und Lebenszyklus verwenden getrennte Datenbanken. Das Skript prüft leere Ausgangslage, Abweisung ohne Einstellungen, erste Migration, SIGTERM, geordnetes Herunterfahren und Neustart mit unveränderter Historie. Ausführungsbits für Wrapper und Skript sind im Git-Index gesetzt. Aktueller CI-Lauf erfolgreich; Robustheitsbefund R2 bleibt bestehen. |

## Tatsächlich selbst ausgeführte Prüfungen

- Aktuelles main und PR-Branch abgerufen; PR-Metadaten und Head vor Abschluss erneut gelesen. Head und Basis waren unverändert.
- Gesamten Änderungsumfang ermittelt und die Backendquellen, Tests, Konfiguration, Moduldeklarationen, Migrationskonvention, Workflow, Lebenszyklusskript sowie dokumentarische Übergabe gegen main geprüft.
- `scripts/validate_repository.py`: erfolgreich.
- `scripts/task_board.py --check`: erfolgreich.
- `python -m unittest discover -s tests -v`: 25/25 erfolgreich, unter Windows mit `PYTHONUTF8=1`.
- `git diff --check origin/main...HEAD`: erfolgreich.
- SHA-256-Vergleich von `mvnw`, `mvnw.cmd` und `maven-wrapper.properties` zwischen backend/ und qualification/backend/: jeweils identisch.
- Unverändertheit von DONE.md, REQUIREMENTS.md, ROADMAP.md, TECHNICAL_CONTRACTS.md und TECHNICAL_BASELINE.md im PR-Diff geprüft.

Ein erster Repository-Testlauf auf main ohne UTF-8-Modus scheiterte an acht Encoding-Assertions einschließlich Unterfällen. Die unveränderten Fixtures verwenden teilweise die plattformabhängige Standardkodierung. Der anschließende vollständige Lauf auf dem PR-Stand mit UTF-8-Modus bestand; kein daraus abgeleiteter neuer Backendbefund.

## Unabhängig gelesene CI-Nachweise

Für den aktuellen Head melden [Repository checks 39118823455](https://github.com/1uedev/task-manager/actions/runs/39118823455), [Technical qualification 39118823451](https://github.com/1uedev/task-manager/actions/runs/39118823451) und [Backend 39118823461](https://github.com/1uedev/task-manager/actions/runs/39118823461) Erfolg. Für Backend wurde zusätzlich das vollständige Jobprotokoll von Job `117650046730` gelesen. Es dokumentiert den PR-Testmerge `8a1b9b7e8f86e210c0184ea719001cc4cde8bf6c` aus genau dem geprüften Head und main.

Das Backendprotokoll bestätigt:

- OpenJDK/Temurin 25.0.4.1 und tatsächlich PostgreSQL 18.6;
- 13 Tests, keine Fehler, Fehlschläge oder übersprungenen Tests: Module 2, Migrationskonvention 1, Datenbank 4, Problemantworten 4, Lebenszyklus/Pflichtkonfiguration 2;
- erfolgreichen Maven-Build;
- erfolgreichen separaten Lebenszykluslauf mit erster Historienzeile `0001 true` und abschließendem `Lifecycle check passed`.

Das Skript verlangt dafür unter anderem Exitcode 143 und den Eintrag zum geordneten Herunterfahren sowie eine beim Neustart unveränderte Historie. Diese Aussagen sind damit durch gelesenes CI-Protokoll und geprüfte Skriptbedingungen gestützt, nicht durch einen eigenen lokalen Java-Lauf. Das Artefakt `backend-evidence` wurde über die Metadaten bestätigt, Ablaufdatum 25. Oktober 2026; sein ZIP-Inhalt wurde nicht heruntergeladen oder einzeln inspiziert.

## Grenzen und Übergabe

Java, Docker und PostgreSQL-Werkzeuge sind in dieser lokalen Prüfumgebung nicht verfügbar. Deshalb wurden Maven-/Java-/Testcontainers-Tests, das Lebenszyklusskript und zusätzliche HTTP-, Modul- oder Migrations-Gegenproben hier nicht selbst ausgeführt. Ebenso keine Last-, Sicherheits-, Restore-, Lizenz- oder fachlichen Anwendungstests. Die früher vom Autor beschriebene Modul-Gegenprobe wurde nicht eigenständig wiederholt.

Die vorhandenen Nachweise betreffen die Backendgrundlage. Noch leere Fachmodule beweisen keine Datenhoheit späterer SQL-Zugriffe oder korrekte Autorisierung. Q5 zum späteren Produkt-Problem-Schema und Q6 zur Flush-Verantwortung aus dem früheren Prüfbericht bleiben in der Übergabe sichtbar; weder Fachendpunkte noch die spätere Transaktionsorchestrierung werden hier als fertig bewertet.

Nächster koordinierter Schritt: R1 korrigieren und gezielt gegenprüfen; über R2 entscheiden. Danach entscheidet Holger über die Annahme. Dieser Bericht erteilt keine fachliche Abnahme, ändert keinen Aufgabenstatus und hebt keine Blockierung auf.
