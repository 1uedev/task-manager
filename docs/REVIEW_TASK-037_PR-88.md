# Gezielte Gegenprüfung TASK-037 / PR #88

19. Oktober 2026 · Codex-Sitzung `codex-20261019-session-review-01`.
Implementierung: Claude-Sitzung `claude-20261019-identity-01`.
Unabhängige technische Nachprüfung nach DEC-040, eigener Branch und Worktree.

**Ergebnis: Im begrenzten Umfang keine neuen Befunde.** Die Korrektur des Rennens zwischen
Passwortwechsel und Fristverkürzung ist nachvollziehbar und durch eigene Läufe belegt.
N1 aus dem [vorherigen Bericht zu PR #79](https://github.com/1uedev/task-manager/blob/review/task-024-fixes-79-codex/docs/REVIEW_TASK-024_PR-79.md)
ist für die geprüften Abläufe technisch behoben. Das ist keine fachliche Abnahme von
TASK-037 oder erneute Sicherheitsfreigabe des gesamten Identitätsmoduls.

## Stand, Auftrag und Grenzen

- Korrektur: [PR #88](https://github.com/1uedev/task-manager/pull/88), Head
  `5ef853c587c74c7e6bf33ee014ed3106de2e2a24`, Merge
  `421d4b89e39770503d16c918f38944d32dc95fa2`.
- Tatsächlich lokal geprüft: main `d8374632ee91030fd851468b3f6ecd75a7b74cd1`.
  Eigener Git-Vergleich mit dem PR-Head: Backend, Verträge und Workflows identisch.
  Die nachfolgende Reservierung von TASK-038 betrifft die Aufgabenkoordination.
- Maßgeblich: TASK-037, DEC-061 und DEC-040; REQ-AUTH-001, REQ-AUTH-003,
  REQ-SEC-001 sowie die feste Frist aus REQ-AUTH-005/DEC-048/DEC-058.
- Gelesen: gemeinsame Einstiegs-/Arbeitsregeln einschließlich CLAUDE.md, technische Basis,
  relevante Anforderungen, Entscheidungen, Architektur-/Modulverträge, Erstbefund,
  vollständiger Korrekturdiff, Sitzungsanlage/-rotation, Fristeinstellung und zugehörige Tests.

DEC-061 beauftragt ausdrücklich **nur N1**. N2, der Verbrauch alter Einmalpasswörter beim
Upgrade V0003 → V0004, bleibt eine gewählte Einschränkung. Die Erläuterung und der Hinweis
zur neuen Ausgabe solcher Passwörter stehen in `backend/README.md`, Abschnitt „Bekannte
Grenze bei Altdatenbanken“. Dieser Bericht behauptet keine Korrektur oder neue Abnahme
von N2. TASK-036/TASK-038 und der echte Identitäts-/Berechtigungsadapter bleiben außerhalb
dieser Prüfung. TODO.md, DONE.md und Zuständigkeiten werden nicht geändert.

## Warum die Korrektur N1 adressiert

Fundstellen im geprüften Stand:

- [SessionService.java:217–222](../backend/src/main/java/taskmanager/identity/session/SessionService.java#L217):
  `replacePassword` liest die Dauer mit `hoursForShare` innerhalb seiner Transaktion vor
  Konto- und Sitzungssperren.
- [SessionService.java:250–254](../backend/src/main/java/taskmanager/identity/session/SessionService.java#L250):
  Die Ersatzsitzung übernimmt den alten Beginn und höchstens das kleinere Ende aus
  gespeichertem Ablauf und Beginn plus aktueller Dauer.
- [SessionService.java:114–117](../backend/src/main/java/taskmanager/identity/session/SessionService.java#L114):
  Auch die bestehende Loginanlage verwendet dieselbe Sperrreihenfolge. Eine Quellsuche
  nach allen Aufrufen von `insertSession` und allen Inserts in `identity_session` bestätigt
  genau diese beiden Anlagewege im Produktcode.
- [SessionDuration.java:52–57](../backend/src/main/java/taskmanager/identity/session/SessionDuration.java#L52):
  Die Einstellungsänderung sperrt den Einstellungsdatensatz durch ihr UPDATE und kürzt
  anschließend vorhandene offene Sitzungen in derselben Transaktion.

Passwortwechsel zuerst: Die Verkürzung wartet an der Einstellung, bis die Ersatzsitzung
festgeschrieben ist, und erfasst diese anschließend. Verkürzung zuerst: Der Passwortwechsel
wartet dort und verwendet nach ihrem Commit den verkürzten Wert. Die gemeinsame Sperre
verhindert damit den ursprünglichen Snapshotfall, bei dem die neue Sitzungszeile erst nach
Beginn des Verkürzungs-UPDATEs entstand und von diesem verfehlt wurde.

## Eigene Ausführung und Gegenproben

Alle Java-Läufe nutzten Windows, Temurin 25.0.4.1+1, Maven 3.9.16 und eine eigene native
PostgreSQL-18.6-Instanz auf Loopback mit ausschließlich synthetischen Daten. Verwendet wurde
die bereits vorgesehene externe Testdatenbankkonfiguration. Kein eigener Nachweis des
gepinnten Docker-Images und kein eigener lokaler Bash-/SIGTERM-Lifecycle-Lauf.

| Ausgeführt | Tatsächliches Ergebnis |
|---|---|
| Unverändertes Backend `verify` | **89 Tests bestanden**, 0 Fehler/Fehlschläge/übersprungen; Build erfolgreich. Enthält die bisherigen 87 und beide neuen N1-Tests. |
| Sperrmutation, ausschließlich im eigenen Worktree | Nur in `replacePassword` wurde `hoursForShare()` vorübergehend durch `hours()` ersetzt; die neue Ablaufbegrenzung blieb bestehen. Beide unveränderten N1-Tests scheiterten gezielt an ihrer jeweiligen Wartebehauptung: **2 Fehlschläge, 0 Ausführungsfehler**. |
| Frischer Rücklauf nach Wiederherstellung | `clean test` mit beiden N1-Methoden erfolgreich: **2 bestanden**, keine Fehler/Fehlschläge/übersprungen. Im zweiten Ablauf zusätzlich Beginn, Widerruf des alten Tokens, Ablauf nach zwei Stunden und keine Reaktivierung nach Rückstellung auf 24 Stunden direkt geprüft. |
| Repositoryprüfungen | Validator und `task_board.py --check` erfolgreich, **25 Python-Tests bestanden**, Diffprüfung einschließlich der neuen Dateien erfolgreich. |
| Original-CI des Autors | Alle vier Workflowstatus auf `5ef853c` erfolgreich; [Backend 39231163886](https://github.com/1uedev/task-manager/actions/runs/39231163886) einschließlich Jobschritten für Build, Integration und Start/Stopp/Neustart gelesen. Kein vollständig nachgelesenes Jobprotokoll. |

Die beiden N1-Tests erzwingen beide Reihenfolgen durch Latches und prüfen die tatsächliche
Sperrwartebeziehung über `pg_stat_activity` und `pg_blocking_pids`; eine bloße Wartezeit gilt
nicht als Beleg. Der erste Ablauf prüft bereits im Autorentest Ablauf und ausbleibende
Reaktivierung. Der zweite Autorentest prüft das gespeicherte verkürzte Ende; die zusätzliche
Reviewer-Prüfung bestätigt dort außerdem das tatsächliche Auflösungsverhalten nach Ablauf.

Ein erster Rücklauf nach Wiederherstellung der Quelldatei verwendete wegen des älteren
Dateizeitstempels noch die kompilierten Mutationsklassen und scheiterte erneut an denselben
Wartebehauptungen. Das Compilerprotokoll meldete „Nothing to compile“. Dieser Lauf ist kein
Beleg gegen den Korrekturcode. Erst der dokumentierte erfolgreiche **Clean-Rücklauf** mit
neu kompilierten Klassen wird als Bestätigung gewertet.

Beigefügt:

- [Genaue Sperrmutation](review-evidence/TASK-037-lock-mutation.diff), nur als Prüfnachweis;
  sie ist im Produktcode vollständig zurückgenommen.
- [Zusätzliche Reviewer-Assertionen](review-evidence/TASK-037-review-assertions.diff), ebenfalls
  nur vorübergehend ausgeführt und anschließend aus dem Backendtest zurückgenommen.
- [Gekürzte tatsächliche Ausgaben](review-evidence/TASK-037-observations.txt) aus Gesamtlauf,
  Mutation und frischem Rücklauf. Fehlgeschlagene Mutation und erfolgreicher Produktstand
  sind ausdrücklich getrennt.

Die regulären Testquellen liegen in
[IdentityHardeningTest.java](../backend/src/test/java/taskmanager/identity/IdentityHardeningTest.java).
Reproduktion des gezielten Produktlaufs auf dem genannten Stand:

```sh
cd backend
./mvnw --batch-mode --no-transfer-progress \
  '-Dtest=IdentityHardeningTest#n1ARotationInFlightIsShortenedByAConcurrentChangeOfThePeriod+n1BARotationThatWaitsForAnUncommittedShorteningReadsTheShortenedPeriod' \
  -Dtaskmanager.test.jdbc-url="$REVIEW_DB_URL" \
  -Dtaskmanager.test.username="$REVIEW_DB_USER" \
  -Dtaskmanager.test.password="$REVIEW_DB_PASSWORD" clean test
```

Die optionale Mutation ausschließlich in einem eigenen Prüfcheckout anwenden und vor jedem
Rücklauf zurücknehmen; nach Wiederherstellung immer `clean` verwenden. Die Testergänzung
prüft dieselben Methoden weiter und ist keine neue reguläre Produktänderung.

## Übergabe

Für N1 bleibt nach dieser gezielten Prüfung kein konkreter technischer Befund offen.
Die Koordination kann den Bericht für TASK-037 heranziehen; Holger entscheidet über die
fachliche Annahme und eine spätere Verschiebung nach DONE.md. Die bewusste Einschränkung N2
aus DEC-061 bleibt erhalten. Die historischen Prüfberichte werden nicht umgeschrieben.

Mutation und Testergänzung sind vollständig zurückgenommen, Produktquellen unverändert.
Nach Sicherung der Nachweise wurden die eigenen Builddateien bereinigt und der eigene
Testserver beendet. Nicht geprüft wurden neue Browserabläufe, Last-/Schwachstellenscans,
echte LDAP-/Portal-/Berechtigungsadapter oder der gesamte Sicherheitsumfang aller Module.
