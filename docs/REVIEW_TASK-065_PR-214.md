# Unabhängige Prüfung TASK-065 / PR #214

Stand: 30. Oktober 2026. Prüfung nach DEC-040 durch Codex, ohne Mitarbeit an der Umsetzung. PR #214 (claude/project-thread-lxrdya, Head `9647f6db6215d86d13957f7d3dab6f2d40fb07f8`) gegen main (Base `ca618a1812d5327f2c740f1915f20f9e2ac70c58`). Von Holger im Projektgespräch übergeben und unverändert abgelegt.

## Ergebnis und Empfehlung

Vorläufig: keine abschließende technische Freigabe. Der Code behebt den Kern von F1 und F2 plausibel. Ein eigener Testlauf und die geforderte vollständige Querverwendungsprüfung waren in dieser Prüfungsumgebung nicht möglich. Die vom PR-Autor gemeldeten grünen Tests sind nicht als unabhängig ausgeführte Prüfungen zu werten.

## Befunde (P1–P3)

**F1 – P3:** Mehrere Seitenladeanforderungen innerhalb derselben Laderunde sind nicht getrennt abgesichert. `JobsPage.tsx` prüft `current !== loading.current`, aber `load(before, current)` vergibt keine separate Anforderungskennung. Werden mehrere `load`-Aufrufe in derselben Runde überlappend ausgelöst, kann eine ältere Antwort später angehängt werden und `more` überschreiben. Das betrifft insbesondere die Konsistenz von Pagination bei überlappenden Aufrufen. Der normale Button ist während `busy` deaktiviert, weshalb der konkrete Benutzerpfad eingeschränkt ist. Empfehlung: gezielter Test mit verzögerten Seitenantworten und konkurrierender Neuladung; gegebenenfalls eindeutige Request-ID bzw. Serialisierung je Seite.

**H1 – Hinweis, kein bestätigter P1/P2-Befund:** Der Wechsel von `filter` bzw. `round` erhöht `loading.current` erst im `useEffect`. Ein bereits laufender Request könnte theoretisch vor diesem Effekt noch mit der alten Runde abschließen. Die neue UI-Interaktion und der Effekt müssen im Browser auf diese Zeitfolge geprüft werden. Die vorhandene Regression testet nur einen Filterwechsel mit später eintreffender Antwort.

**H2 – Hinweis:** Die neue Browserregression testet nur verspätete Antwort des ersten gefilterten Listenabrufs, nicht eine verspätete ältere Seite und nicht das explizite „Aktualisieren“ mit ausstehender Antwort. Beide Szenarien sind als gezielte Regressionen zu ergänzen.

## Prüffragen

1. **Alte Versuche:** `JobRepository.claim` erzeugt je Claim `owner + '#' + UUID`; `renew` bindet an `lease_owner`, `succeed`, `fail` und `giveBack` an Job-ID, Status `running` und genaue Lease-Kennung. `JobExecutor.active` ist nach Lease statt nach Job-ID indiziert. Dadurch kann ein alter Versuch, auch desselben Executors und nach administrativem Requeue, einen neueren Versuch nach der dokumentierten SQL-Logik weder abschließen noch zurückstellen, zurückgeben oder aus `active` entfernen. Unabhängiger Datenbanktest ausstehend. Nebenläufige Handler-Nebenwirkungen bleiben außerhalb dieser Sicherung.
2. **Verwendungen von `lease_owner`:** Die gelesenen Kernpfade verwenden es als Reservierungskennung, nicht als reinen Ausführernamen. Eine vollständige repositoryweite Suche in Helpdesk `ReplyWatch`, `Jobs.active`, Tests und Skripten wurde nicht abgeschlossen; keine Entwarnung für alle Querverwendungen.
3. **Jobübersicht:** Alte Antworten aus früheren Laderunden werden für Summary und Liste verworfen. Das umfasst Filterwechsel und „Aktualisieren“, sobald der neue Effekt ausgeführt wurde. Seitenantworten aus derselben Runde werden nicht unabhängig versioniert; entsprechende Browserbelege fehlen.
4. **Tests:** Die drei `anOldAttempt...`-Tests und der neue Browsertest werden im PR als rot gegen den alten Code und grün mit Korrektur beschrieben. Eine unabhängige Red/Green-Ausführung sowie Stabilitätswiederholungen konnten nicht vorgenommen werden. Der Browsertest verwendet eine feste Wartezeit von 300 ms, was ein potenzielles Stabilitätsrisiko darstellt.

## Ergänzung bei der Ablage (Autorensitzung, keine unabhängige Prüfung)

Zu Prüffrage 2 eine Suche über `backend/`, `frontend/`, `contracts/` und Skripte am Merge `7dd7c84`: `lease_owner` wird im Produktcode nur in `JobRepository` gelesen und geschrieben. `Jobs.active` und `ReplyWatch` fragen nur den Zustand des Auftrags ab. Tests setzen `lease_owner` in `JobsApiTest` und `HelpdeskApiTest` auf frei gewählte Werte, um laufende oder verwaiste Aufträge nachzustellen; diese Werte werden nie mit einem Ausführernamen verglichen.
