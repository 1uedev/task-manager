# Gezielte Nachprüfung TASK-039 / PR #97

19. Oktober 2026 · Codex-Sitzung `codex-20261019-adapter-followup-01`.
Unabhängige technische Nachprüfung nach DEC-040, eigener Worktree und Branch
`review/task-039-pr97-followup-codex`.

**Ergebnis: F1 und F2 des Erstberichts sind im geprüften Umfang technisch behoben.
Keine neuen Befunde in dieser begrenzten Nachprüfung.** Der Ablauf während einer
Sperrwartezeit wird jetzt nach dem Warten geprüft; unvollständige Anmeldungen liefern
keinen Rechtekontext. Beide Regressionserwartungen erkennen das jeweilige alte Verhalten.

## Prüfstand und Umfang

- Ausgangspunkt: [Erstbericht](REVIEW_TASK-039_PR-97.md), veröffentlicht mit
  [PR #98](https://github.com/1uedev/task-manager/pull/98), auf PR-Head
  `18d6774290b1d1aa1972e9fceb4819d34e45f2af`.
- Korrektur: `eadfb11b3ebc45a9c625f9ca9dda58b26b322dae` in
  [PR #97](https://github.com/1uedev/task-manager/pull/97), finaler Head
  `4e0407f61726465580678f9f2b71b5e731dbfc33`, integriert mit `8f95f04`.
- Tatsächlich lokal geprüft: main `635dbca39ebc1965dbfa15c0c494b8c560194539`.
  Eigener Git-Vergleich bestätigt identische Backend-/Frontendquellen, Verträge und
  Workflows gegenüber dem finalen PR-Head. Der nachfolgende Abschluss von TASK-036
  ändert diese Quellen nicht.
- Vor Veröffentlichung auf main `68878ab376f750cb0779ed0e4db8553aad955b29`
  aktualisiert: ausschließlich die Reservierung von TASK-040 in TODO.md, keine
  Änderung der getesteten Produktquellen. Diese Reservierung wird nicht übernommen.
- Gelesen und abgeglichen: aktuelle Einstiegs-/Arbeitsregeln einschließlich CLAUDE.md,
  Aufgabenstatus und Abnahmeeintrag, Erstbericht, Korrekturdiff, DEC-063,
  technische Basis, relevante Anforderungen und Modul-/Transaktionsverträge.
  Maßgeblich sind TASK-039, DEC-040/DEC-063, REQ-AUTH-001, REQ-AUTH-003,
  REQ-ACL-001 und REQ-SEC-001.

Die Abnahme durch Holger ist bereits in DONE.md vermerkt. Diese Nachprüfung ergänzt
den technischen Nachweis; sie erteilt keine neue fachliche Abnahme. Keine Änderung
an TODO.md, DONE.md, fremdem Branch oder Produktcode im Berichts-PR. Kein
Implementierungsclaim, keine Übernahme einer Zuständigkeit und keine Freigabe der
weiterhin blockierten TASK-026 bis TASK-031.

## F1 · vorher mittel / P2 · Prüfzeitpunkt nach dem Sperrwarten

[JdbcIdentityVerification.java:57–62](../backend/src/main/java/taskmanager/identity/session/JdbcIdentityVerification.java#L57)
liest `clock.instant()` erst im Row-Mapper, nachdem die gemeinsame Sitzungssperre
erworben und die aktuelle Sitzung gelesen wurde. Ein vor dem Warten erfasster Zeitpunkt
wird nicht mehr wiederverwendet. Sperrfolge und Transaktionspropagierung bleiben erhalten.

Der neue Autorentest
`aCheckThatWaitedForAShorteningReadsTheClockAfterTheWaitNotBefore` erzwingt die
Verkürzung kurz vor Ablauf, hält deren Transaktion offen und stellt die Testuhr erst
nach bestätigtem PostgreSQL-Warten weiter. Anschließend muss die wartende Prüfung
verweigern. Für diesen Lauf wurde der Wartehelfer zusätzlich an die bekannte
Halter-PID gebunden; der Test besteht auch mit dieser strengeren Bedingung.

Zusätzlich wurde der eigene Nachweis aus dem Erstbericht erneut ausgeführt: Die
wartende Fachtransaktion versucht nach `Permissions.require` einen Datensatz in
einer ausschließlich synthetischen Fachtabelle zu schreiben. Warte- und Halter-PID
sind direkt verknüpft. Mit der Korrektur wird verweigert, **0 Fachdatensätze** werden
committet. Eine neue Prüfung desselben Kontexts verweigert ebenfalls.

**Gegenprobe:** Nur das Lesen der Uhr vor die wartende Abfrage zurückverlegt. Sowohl
der neue Autorentest als auch die eigene Schreibgegenprobe scheitern gezielt:
**2 Fehlschläge, 0 Ausführungsfehler**. In der eigenen Probe erscheint wieder genau
ein committeter synthetischer Fachdatensatz. Damit ist die alte Fehlfreigabe
reproduziert und die Sensitivität beider Tests belegt. Mutation zurückgenommen.

**Zeitsemantik:** DEC-063, Codekommentar und backend/README.md benennen jetzt die
Prüfung nach dem Halten der Sperren und Lesen der Sitzung. Ein erst danach eintretendes
Sitzungsende stoppt laufende Facharbeit nicht; es gibt keine erneute Prüfung beim Commit.
DONE.md hält fest, dass Holger diese Semantik mit der Abnahme akzeptiert und keine
Abschlussprüfung beauftragt hat. Diese gewählte Grenze wird hier nicht als behoben
oder als neue eigene Freigabe ausgegeben. Die frühere unzutreffende Zusage einer
Gültigkeit zum Commit ist in der betroffenen Dokumentation ersetzt.

## F2 · vorher niedrig / P3 · Leerer Kontext bei unvollständiger Anmeldung

[RequestPermissionContexts.java:35–43](../backend/src/main/java/taskmanager/RequestPermissionContexts.java#L35)
verweigert früh, wenn die Anmeldung nicht FULL ist. Nur eine vollständige Anmeldung
gelangt zur Kontextkonstruktion; deren Pflichtwechselkennzeichen ist dann folgerichtig
`false`. Damit entspricht das Verhalten dem unveränderten Portvertrag
`PermissionContexts.current()`.

Die zwei neuen Tests in `RequestPermissionContextsTest` bestehen: vollständige
Anmeldung mit serverseitigen IDs und aktueller Rechteversion als positive Kontrolle;
leere Antwort bei Pflichtwechsel, fehlender Anfrage, fehlender Firma und Firma ohne
Rechtestand als Negativfälle.

**Gegenprobe:** Nur den Builder auf das frühere Verhalten zurückgesetzt: keine
FULL-Prüfung, stattdessen ein Kontext mit Pflichtwechselkennzeichen. Der Negativtest
scheitert an „forced password change“, während die positive Kontrolle besteht:
**2 Tests, 1 bestanden, 1 gezielter Fehlschlag, 0 Ausführungsfehler**. Mutation
zurückgenommen. Wie im Erstbericht ist damit ein Vertragsfehler belegt, keine frühere
Umgehung der abschließenden serverseitigen Rechteprüfung.

## Eigene Ausführung und Nachweise

Windows, Temurin 25.0.4.1+1, Maven 3.9.16 und eine eigene native PostgreSQL-18.6-Instanz
auf Loopback mit synthetischen Daten. Die vorgesehene externe Testdatenbankkonfiguration
wurde verwendet. Keine globale Installation oder Änderung an fremden Testdiensten.

| Eigene Ausführung | Ergebnis |
|---|---|
| Unveränderter main-Stand, Backend `verify` | **123 bestanden**, 0 Fehlschläge/Fehler/übersprungen, Build erfolgreich. |
| Neue F1-/F2-Tests plus unabhängige Schreibprobe | **4 bestanden**: zwei echte Datenbankfälle zu F1, zwei Buildertests mit simulierten Ports. Die F1-Wartebedingungen sind an die konkrete Halterverbindung gebunden. |
| Früher Prüfzeitpunkt, nur F1 | **2 gezielte Fehlschläge**, 0 Ausführungsfehler/übersprungen; unerwünschtes Schreiben wiederhergestellt. |
| Früher Builder, nur F2 | **1 bestanden, 1 gezielter Fehlschlag**, 0 Ausführungsfehler/übersprungen. |
| Bytegenau wiederhergestellt und frisch kompiliert | `clean test` für `IdentityPermissionsAdapterTest,RequestPermissionContextsTest,PermissionContextsProbeTest`: **21 bestanden**, 0 Fehlschläge/Fehler/übersprungen. Keine kompilierten Mutationen oder ergänzten Reviewer-Tests verbleiben im Produktprüfstand. |
| Berichtsrepository | Validator und Aufgabenübersicht erfolgreich, **25 Python-Tests mit `PYTHONUTF8=1` bestanden**, Diffprüfung einschließlich neuer Dateien erfolgreich. |

Beigefügt: [bereinigte Ergebnisse](review-evidence/task-039-followup/results.txt),
[erneut ausgeführte Schreibprobe](review-evidence/task-039-followup/expiry-reviewer-probe.java.txt),
[F1-Mutation](review-evidence/task-039-followup/early-clock-mutation.diff.txt),
[F2-Mutation](review-evidence/task-039-followup/incomplete-context-mutation.diff.txt).
Die Mutationspatches verwenden Nullkontext (`git apply --unidiff-zero`) und dienen
ausschließlich der Reproduktion. Die zusätzliche Halterprüfung entspricht dem
[Nachweis des Erstberichts](review-evidence/task-039/wait-holder-instrumentation.diff.txt).
Die eigene Schreibprobe wird in die bestehende Adaptertestklasse mit ihren Helfern
eingefügt; sie ist keine neue aktive Produkttestdatei.

**Fremde CI, getrennt von eigenen Läufen:** Alle vier Workflows des finalen PR-Heads
`4e0407f` sind erfolgreich. Im [Backendlauf 39262111709](https://github.com/1uedev/task-manager/actions/runs/39262111709)
wurden Jobschritte und relevante Protokollstellen gelesen: 123 Tests ohne
Fehler/übersprungene Tests, PostgreSQL 18.6 und `Lifecycle check passed` für das
gepackte JAR einschließlich Bootstrap. Kein eigener lokaler Docker-/Bash-/SIGTERM-Lauf.
Frontendtests wurden für die eng begrenzten Backendkorrekturen nicht lokal wiederholt.

Die im Erstbericht belegten allgemeinen Sperr-/Schranken-/Bootstrap-Prüfungen bleiben
historische Ergebnisse für ihren dort genannten Stand. Der volle unveränderte
123-Test-Lauf enthält die vorhandenen Regressionstests erneut; eine neue allgemeine
Sicherheitsprüfung, beliebige Deadlockfreiheit oder neue Fachmodule werden hier nicht
behauptet. Der Erstbericht bleibt als Ausgangsanalyse nachvollziehbar erhalten.

**Übergabe:** F1 und F2 können für den beschriebenen Korrekturstand technisch als
nachgeprüft behoben referenziert werden. Für diese beiden Befunde ist aus dieser
Nachprüfung keine weitere Korrektur offen. Aufgabenstatus und fachliche Abnahme bleiben
unverändert.
