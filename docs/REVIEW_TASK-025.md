# Getrennte technische Prüfung von TASK-025

Datum: 18. Oktober 2026. Ergebnis-PR: [#74](https://github.com/1uedev/task-manager/pull/74).
Autor der Umsetzung: `codex-20261018-permissions-01`.
Getrennter Prüfer: Codex-Agent `permissions_review`, eigene Prüfung innerhalb dieser Sitzung.
Dies ist eine getrennte **statische Codex-Prüfung**, kein gegenseitiger Claude-Review und keine
fachliche Abnahme durch Holger. Der gegenseitige Review gemäß DEC-040 bleibt offen.

## Gegenstand und tatsächlich vorgenommene Prüfung

Der Prüfer las den Java-Vertrag und die Implementierung unter
`backend/src/main/java/taskmanager/permissions`, Migration V0002, PolicyTest,
PermissionsIntegrationTest sowie AGENTS.md, TASK-025, DEC-028/DEC-040,
PERMISSIONS.md und TECHNICAL_CONTRACTS.md. Schwerpunkt: Aktionsunion, Feldverbotsvorrang,
Unternehmens-/Projekt-/Organisationsgrenzen, Delegationsgrenzen, Audit und konkurrierender
Entzug. Nachprüfung der Korrektur am Stand von PR #74 (`19a6755`) und im lokalen Vertrag.
Die Auswahl von genau einem Identitätsadapter einschließlich Gegenprobe mit `@Primary`
wurde am Stand `3395672` zusätzlich gezielt gelesen; kein neuer Befund.

Der getrennte Prüfer führte **keine Tests selbst aus**. Er las vorhandene lokale Surefire-
Berichte (4 Policytests und 1 Konventionstest). Den laufenden PostgreSQL-CI wertete er nicht
als Erfolg. Die Eigenprüfungen des Autors und CI-Nachweise werden getrennt im TASK-025-
Eintrag und PR dokumentiert.

## Befund R1 – mittlere Schwere: Sperrpflicht der Kontextadapter unvollständig

**Fundstelle:** ursprünglicher Arbeitsstand von `IdentityAccess.isCurrent`,
`ResourceContext` und `JdbcPermissions.require`/`lock`; korrigierte Verträge in
[IdentityAccess.java](../backend/src/main/java/taskmanager/permissions/IdentityAccess.java),
[ResourceContext.java](../backend/src/main/java/taskmanager/permissions/ResourceContext.java)
und [Permissions.java](../backend/src/main/java/taskmanager/permissions/Permissions.java).

**Problem:** Die Revisionssperre schützt eigene Gruppen- und Rechteänderungen. Die zunächst
nur verlangte aktuelle Lektüre fremder Identitäts-/Ressourcenmerkmale schützt nicht vor deren
Änderung zwischen Prüfung und Fachcommit. Ein normaler SELECT im Adapter könnte den Vertrag
erfüllen, obwohl Kontoentzug, Projektmitgliedschaftsentzug oder Organisationswechsel danach
zuerst committen und der alte Fachauftrag anschließend dennoch schreibt.

**Vorschlag:** Gemeinsame Transaktion, bis zum Commit gehaltene Sperren auf den geprüften
Merkmalen, inkompatible Sperren auf allen entsprechenden Schreibwegen und feste
Sperrreihenfolge verbindlich verlangen. Beide Reihenfolgen mit realen Datenbanksperren prüfen.

**Änderung und Nachprüfung:** Öffentliche Verträge verlangen jetzt diese Pflichten und
verbieten `REQUIRES_NEW` beziehungsweise reine Snapshot-/Cachelektüre.
[Berechtigungsvertrag](../backend/PERMISSIONS.md) beschreibt die Reihenfolge Rechte →
Identität → Ressource → Fachobjekt und die Grenze zur echten Integration.
Synthetische Adapter halten eigene FOR-SHARE-Sperren. Die zusätzlichen Tests
`identityOrResourceRevocationCommittedFirstRejectsWaitingWrite` und
`identityAndResourceAdaptersHoldTheirOwnLocksThroughBusinessCommit` prüfen beide
Commitreihenfolgen für synthetischen Identitäts- und Projektmitgliedschaftsentzug;
`pg_blocking_pids` weist das tatsächliche Warten nach.

Der getrennte Prüfer bewertet R1 im begrenzten TASK-025-Auftrag als **adressiert** und die
Tests statisch als plausibel. Das ist keine Behauptung einer eigenen Testausführung oder
eines Nachweises echter Konto-, Sitzungs-, Organisations- oder Objektänderungen.

## Weitere Ergebnisse und verbleibende Grenzen

Keine weiteren konkreten Sicherheitsbefunde bei Aktionsunion, explizitem Feldverbot,
Lesen vor Schreiben, Mandantengrenzen, delegierten Änderungen und Audittransaktion gefunden.
Das Audit benötigt ein eigenes Unternehmensrecht; delegierte Projektverwaltung erhält
keinen allgemeinen Auditzugriff. Änderungen, Revision und Audit teilen die bestehende
Transaktion. Fehlende oder mehrdeutige Identitätsadapter verweigern Zugriff.

Echte Identitäts-/Sitzungsadapter und Projekt-/Objektadapter sind noch nicht vorhanden.
TASK-024/TASK-026 müssen den Sperrvertrag und sämtliche wirklichen Entzugs-/Wechselwege
integriert nachweisen. Bootstrap, dienstliche/Worker-Kontexte, HTTP-Fachfehler und
Auditaufbewahrung sind weitere ausdrücklich offene Punkte. Dieser Bericht nimmt weder
diese Folgeaufgaben noch fachliche Abnahme vorweg; TASK-025 bleibt in TODO.md.
