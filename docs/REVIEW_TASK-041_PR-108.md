# Unabhängige Nachprüfung TASK-041 / PR #108

**Ergebnis:** TASK-041 ist im vereinbarten Umfang von DEC-066 technisch abnahmefähig. Die beiden ursprünglichen Befunde F1 und F2 aus [TASK-040](REVIEW_TASK-040_PR-101.md) sind behoben; diese Prüfung ergibt keinen neuen offenen Befund. Dies ist keine fachliche Abnahme durch Holger und keine allgemeine Betriebsfreigabe.

## Grundlage und Abgrenzung

- Prüfer: Codex, getrennt vom Autor Claude; Sitzung `codex-20261020-projects-admin-review-01`, 20. Oktober 2026.
- Eigenständiger Klon, Branch `review/task-041-task-026-codex`; kein Produktcode, Aufgabenstatus oder DONE-Eintrag geändert.
- Geprüftes aktuelles main: [`cf89e43292ba6d7415aa507edc2d4ab8186a2401`](https://github.com/1uedev/task-manager/commit/cf89e43292ba6d7415aa507edc2d4ab8186a2401), einschließlich TASK-026 und dessen Startprüfungskorrektur.
- Vor Veröffentlichung erneut abgeglichen: main ist inzwischen `b1c88eebf36ed1f66ec685375734ec9109495100` (Reservierung TASK-027, #111). Ausschließlich TODO.md wurde geändert; der geprüfte Produkt- und Testinhalt ist identisch. Der Review-Branch baut auf diesem neueren main auf, ohne dessen Aufgabenstand zu verändern.
- [PR #108](https://github.com/1uedev/task-manager/pull/108), Kopf `c1e96c858a5363eacba7277e03d1f9814989b353`, ist integriert. Der Backendinhalt dieses Kopfes entspricht seinem Merge `f953045`; die eigenen Laufversuche prüfen den oben genannten integrierten Stand.
- Maßstab: AGENTS.md, README.md, TODO.md (TASK-041), DONE.md, docs/AI_WORKFLOW.md, DEC-040/DEC-066, einschlägige Anforderungen, docs/TECHNICAL_CONTRACTS.md, docs/TECHNICAL_BASELINE.md, backend/README.md und backend/PERMISSIONS.md; CLAUDE.md verweist auf AGENTS.md.
- Bezug: REQ-ACL-001, REQ-ACL-003, REQ-AUTH-001, REQ-SEC-001. Die Aufgabenreservierung des Autors bleibt unberührt. Diese ausdrücklich beauftragte Gegenprüfung reserviert keine Implementierungsaufgabe und verändert TODO.md nicht.

## F1: Sperrreihenfolge und Entzug

`AccountRepository.serializeAdministration` (`backend/src/main/java/taskmanager/identity/account/AccountRepository.java:61`) nimmt eine transaktionsgebundene gemeinsame Advisory-Sperre. Deaktivieren, Reaktivieren und Zurücksetzen nehmen sie vor der verbindlichen Rechte- und Identitätsprüfung (`AccountService.java:102`, `:127`, `:148`). Sie bleibt bis zum Transaktionsende bestehen. Der nächste Verwaltungsaufruf prüft deshalb nach dem Vorgänger neu, bevor er Identitäten sperrt oder ändert.

Die frühe Momentaufnahme in `AdministrationRequests.early` ist nur eine Verweigerungsschranke. Sie ersetzt die spätere verbindliche Prüfung nicht. Die Sperren des Rechteadapters und der Identitätsprüfung bleiben in der Änderungstransaktion. Ein bereits wartender Aufruf erhält daher keine Berechtigung aus einer vor dem Warten gespeicherten Momentaufnahme.

**Eigener Prüfversuch:** Eine synthetische Transaktion hält die echte Serialisierung; zwei echte HTTP-Aufrufe zum gegenseitigen Zurücksetzen warten nachweislich auf genau deren PostgreSQL-Prozesskennung (`pg_blocking_pids`). Die wartenden Transaktionen halten keine Relationssperren auf `identity_account` oder `identity_session`. Ohne zwischenzeitlichen Rechteentzug enden die Aufrufe mit **200 und 403**: Der erste setzt das andere Konto zurück, dessen bisherige Anmeldung den zweiten Aufruf nicht mehr erlaubt. Mit Rechteentzug des ersten Anfragenden während des Wartens ergeben sich **403 und 200** und ausschließlich die jeweils erlaubte Änderung. Eine Anfrage ohne Recht antwortet trotz gehaltener Serialisierung sofort mit 403. Kein Deadlock und kein 500 in diesen Fällen.

Die Autorentests für gegenseitiges Zurücksetzen, Deaktivieren/Reaktivieren gegen Zurücksetzen sowie Sitzungsdauer gegen Zurücksetzen sind im eigenen unveränderten Gesamtlauf ebenfalls erfolgreich. Deren geänderte Wartebedingung ist sachgerecht: Nach DEC-066 müssen Aufrufe vor ihren Identitätssperren warten; die frühere Prüfung auf zwei bereits blockierte Sitzungssperren würde gerade das alte Problem voraussetzen.

### Sitzungsdauer und zusätzliche Wartebeziehung

`SessionDuration.change` (`backend/src/main/java/taskmanager/identity/session/SessionDuration.java:56`) sperrt **Einstellungszeile → Verwaltungsserialisierung → Rechte → Identität**, bevor Sitzungen geändert werden. Anmeldung und Passwortwechsel benötigen die Einstellungszeile vor ihren Identitätssperren. Die Kontoverwaltungswege nehmen diese Einstellungszeile nicht nachträglich; im geprüften Ablauf entsteht deshalb keine Rückkante.

**Eigener Prüfversuch:** Die Sitzungsdaueränderung wartet auf die gehaltene Serialisierung und hält dabei bereits die Einstellungszeile, aber noch keine Identitätssperren. Eine gleichzeitig gestartete echte HTTP-Anmeldung wartet nachweislich auf genau diese Daueränderung. Nach Freigabe antworten beide mit **200**. Die Prozessbeziehungen sind im [Ergebnisprotokoll](review-evidence/task-041/results.txt) festgehalten.

Damit gibt es eine zusätzliche, beabsichtigte globale Warteschlange. Eine wartende Daueränderung kann auch Anmeldungen aufhalten; das ist eine Verfügbarkeits-/Durchsatzabwägung der gewählten Serialisierung. Der Versuch zeigt für diese Beziehung keinen Zyklus und keinen Rechteumgehungsweg; er beweist keine allgemeine obere Antwortzeit unter beliebiger Last.

### Letzte Unternehmensverwaltung

Beim Deaktivieren folgt auf die gemeinsame Serialisierung zuerst `AccountDeactivationGuard`, danach die eigene Rechteprüfung und das Zielkonto. `CompanyAdministration.remainsWithout` nimmt die exklusive Revisionssperre wie `setMember` und `setAction` (`backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java:167`). Die anschließende eigene Prüfung versucht daher kein konkurrierendes Hochstufen einer zuvor gemeinsam gehaltenen Revisionssperre. Ein anderer verbleibender Verwalter muss aktiv sein; Identitätsprüfung und Rechteentzug verwenden weiterhin die gemeinsamen Transaktionssperren.

**Eigene Gegenprobe:** Deaktivieren des letzten Unternehmensverwalters endet mit **409 `account.last_administrator`**; das Konto bleibt aktiv. Bestehende Schutz- und Entzugstests bestehen im unveränderten Gesamtlauf. Die Sperrordnung des Guards wurde zusätzlich im Quelltext gegen die Rechteänderungen geprüft; nicht jede mögliche Dreifachverschachtelung wurde neu als HTTP-Versuch aufgebaut.

## F2: Strenge Eindeutigkeit des Verwaltungsports

`AdministrationRights.unique` zählt bis zu zwei Kandidaten und erlaubt ausschließlich genau einen (`backend/src/main/java/taskmanager/platform/AdministrationRights.java:35`). Ein Primary-Merkmal wählt keinen Kandidaten aus mehreren aus.

Alle fünf Verwendungen wurden geprüft:

| Tatsächlicher Verbraucher | Kein Adapter | Genau einer | Zwei | Zwei, davon einer Primary |
| --- | --- | --- | --- | --- |
| `AdministrationRequests.require` | 403 | erlaubt | 403 | 403 |
| `AdministrationRequests.early` | 403 | erlaubt | 403 | 403 |
| `AppearanceService.requireRight` | 403 | erlaubt | 403 | 403 |
| `AppearanceController.administrator` | 403 | erlaubt | 403 | 403 |
| `SessionController.heldRights` | leer | Rechtehinweise | leer | leer |

Die eigene Ergänzung verwendet echte Spring-Bean-Provider mit den vier Konfigurationen und ruft die tatsächlichen Verbraucherhilfen auf, bei privaten Hilfen per Reflection. Ports und Anfragekontext sind synthetisch. Damit sind **20 Verhaltensprüfungen** belegt; es sind keine vier vollständig neu gestarteten HTTP-Anwendungen. Zusätzlich bestehen die fünf unveränderten Wiring-Tests einschließlich der Quelltextprüfung auf verbliebenes `getIfUnique`.

## Tatsächlich ausgeführte Prüfungen

- Eigener unveränderter Backendlauf `clean verify` auf dem geprüften main mit Java **25.0.4.1+1** und echter lokaler PostgreSQL **18.6**: **154 Tests, 0 Fehler, 0 übersprungen**. Er umfasst auch die integrierten Projekttests.
- Eigene temporäre Prüfungen: drei HTTP-/Sperrtests und eine Verbrauchermatrix, zusammen **4 Tests erfolgreich**. Die gemeinsame Ausführung mit den Projektproben umfasst 12 Tests; deren zwei beabsichtigte Regressionsfehlschläge gehören ausschließlich zu TASK-026.
- Nach bytegleicher Wiederherstellung der beiden erweiterten Testdateien und Entfernung der zusätzlichen Testklasse: `clean test` der Originalklassen `AdministrationRightsApiTest`, `AdministrationRightsWiringTest`, `ProjectsApiTest`: **23 Tests erfolgreich**, keine Fehler oder übersprungenen Tests. Produktquellen wurden auch für die Versuche nicht verändert.
- Gelesener Backend-CI-Nachweis auf dem exakten Kopf von #108: [Lauf 39385815671](https://github.com/1uedev/task-manager/actions/runs/39385815671), **138 Tests erfolgreich**, PostgreSQL **18.6**, `Lifecycle check passed`. Das ist gelesene CI-Evidenz, kein eigener Linux-Lifecycle-Lauf.
- Die Prüfquellen, Wiederholungsanleitung und ausgewählte Laufbelege stehen unter [review-evidence/task-041](review-evidence/task-041/README.md).
- Abschlussprüfung der Berichtslieferung: Repository-Validator und Aufgabenübersicht erfolgreich, **25 Python-Regressionstests erfolgreich**, `git diff --cached --check` ohne Befund. Die nach der Prüfung gestartete eigene PostgreSQL-Instanz wurde nach Abgleich von Prozesskennung und Programmpfad gestoppt.

## Grenzen und Übergabe

Die in DEC-066 und backend/README.md ausdrücklich abgegrenzten Selbstverwaltungsfälle gegen nicht serialisierte Passwortwechsel-/Abmeldewege sowie der CLI-Notweg sind keine Zusage beliebiger konfliktfreier Identitätsoperationen. Sie wurden hier nicht zu einer allgemeinen Betriebsfreigabe erweitert. Eine erneute Ablaufprüfung beim Commit bleibt außerhalb des Auftrags. Frühere, ausdrücklich ausgeschlossene Befunde werden nicht als neue Befunde ausgegeben.

**Technische Schlussfolgerung:** F1 und F2 sind im beauftragten Umfang behoben und TASK-041 ist technisch abnahmefähig. Die globale Wartebeziehung ist transparent und im geprüften Ablauf zyklusfrei. Holger entscheidet über die fachliche Abnahme; dieser Bericht ändert weder TODO.md noch DONE.md.
