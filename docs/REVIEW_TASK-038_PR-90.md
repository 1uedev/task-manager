# Gezielte Gegenprüfung TASK-038 / PR #90

19. Oktober 2026 · Codex-Sitzung `codex-20261019-permissions-closeout-01`.
Korrekturautor: Claude-Sitzung `claude-20261019-permissions-01`.

**Ergebnis:** R1 und die durch DEC-062 streng entschiedene K1-Regel sind für die geprüften
Abläufe korrigiert. Alle **92 bestehenden Backendtests** bestehen im eigenen Lauf.
Eine zusätzliche Upgradeprobe zeigt **M1, mittel**: Eine Datenbank mit bereits angewendeter
V0007 übernimmt die noch offene V0006 aus PR #85 mit den regulären Flyway-Einstellungen
nicht. Das ist ein gesonderter Integrationsbefund, keine weitere Rechteumgehung.

Dies ist die technische Gegenprüfung von Claudes Korrekturen nach DEC-040. Codex schrieb
die ursprüngliche Berechtigungsbasis aus PR #74; dieser Bericht ist deshalb kein neuer
unabhängiger Gesamtreview der eigenen ursprünglichen Basis. Der Claude-Erstbericht und der
[vorangegangene Codex-Bericht zu PR #82](https://github.com/1uedev/task-manager/blob/review/task-025-pr82-codex/docs/REVIEW_TASK-025_PR-82.md)
bleiben historisch erhalten. Keine fachliche Abnahme, keine Änderung von TODO.md/DONE.md,
Zuständigkeiten oder Blockierungen.

## Prüfstand und Auftrag

- Korrektur: [PR #90](https://github.com/1uedev/task-manager/pull/90), Head
  `9d2ac545b84dd6855329eb289bc4d8933db85480`, main-Merge
  `5dbe32efb7ac362dd70351f35f32c5240f4d385d`.
- Eigener ausgeführter Stand: main `24b942225656961957c2c696532e736c3bd43172`.
  Git-Vergleich: Backend, Verträge und Workflows identisch mit dem PR-Head.
- Während der Prüfung dokumentiert [PR #92](https://github.com/1uedev/task-manager/pull/92)
  Holgers Annahme von TASK-025/TASK-038. Berichtsgrundlage danach: main
  `f2009537750b0fc0b7c51c86df6226c5e10aba21`; geändert sind nur TODO.md und DONE.md,
  nicht der ausgeführte Produktcode. Dieser Bericht ergänzt die historische Annahme.
- Maßgeblich: TASK-038, DEC-062, DEC-040 und REQ-ACL-001/003/004/005.
  K1 ist inzwischen ausdrücklich als Herkunft nach damaliger Setzbefugnis entschieden;
  die frühere Klärungsfrage wird nicht mehr als offene Alternative behandelt.
- Gelesen: gemeinsame Einstiegs-/Arbeitsregeln einschließlich CLAUDE.md, technische Basis,
  relevante Anforderungen, Entscheidungen, Architektur-/Modulverträge, vorheriger Bericht,
  vollständiger Korrekturdiff, Ports, Rechteimplementierung, Migration und Tests.

P2 bis P6 des Erstberichts sind nicht Gegenstand dieser
Nachbesserung. Der echte Identitäts-/Projektadapter ist weiterhin offen. TASK-036 wird nicht
übernommen; sein ausstehendes Migrationsskript wird nur als dokumentierte Abhängigkeit der
neuen V0007 untersucht. Eine UI-/Sicherheitsprüfung von PR #85 wird damit nicht behauptet.

## Bestätigung von R1 und K1

| Punkt | Geprüfte Umsetzung und eigener Nachweis |
|---|---|
| R1, letzte aktive Administration | [JdbcPermissions.java:204–207](../backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java#L204) verwendet `userActive` unter der exklusiven Revisionssperre. Der [Port](../backend/src/main/java/taskmanager/permissions/IdentityAccess.java) verlangt ein aktives internes Konto derselben Firma, stabil bis zum Commit; seine Standardimplementierung verweigert. Der Regressionstest weist beide Entzugswege bei nur deaktiviertem weiteren Konto ab, prüft Rollback von Mitgliedschaft, Revision und Audit und erlaubt den Entzug nach Aktivierung eines weiteren Verwalters. |
| K1, Firmenverbot nach Rollenverlust | [JdbcPermissions.java:127–136](../backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java#L127) leitet die Setzbefugnis aus der aktuellen Policy ab und speichert sie. Delegierte ändern ein bestehendes DENY nur bei eigenem Urheber und `set_by_company = false`; NULL bleibt geschützt. Der Test entzieht die Unternehmensrolle bei verbleibender Delegation: REMOVE, ALLOW und DENY werden für das alte Firmenverbot abgewiesen; ein neu als Delegation gesetztes eigenes Verbot bleibt entfernbar. |
| Bestehende Regeln | Der echte Flyway-Upgrade V0005 → V0007 erhält DENY, Urheber und frühere Prüfsummen; die neue Rollenangabe bleibt NULL. Der Verhaltenstest weist auch die Rücknahme einer eigenen alten Regel ohne Rollenangabe ab. V0005 wurde nicht geändert. |

**Eigene Verhaltensmutation:** Nur die Aktivitätsabfrage wurde vorübergehend auf bloße
Existenz zurückgestellt und bei der DENY-Prüfung die Rollenbedingung entfernt. Neue Spalte
und Speicherung der Rollenherkunft blieben erhalten. Beide unveränderten Regressionstests
scheiterten **am erwarteten Verweigern**, nicht an einer fehlenden Spalte oder einem
Datenbankfehler. Nach vollständiger Rücknahme und frischer Übersetzung bestehen sie wieder.
Das bestätigt auch für K1 die Erkennung des tatsächlichen unerlaubten Verhaltens.

Die Sperr-/Aktivitätsaussage bleibt durch einen synthetischen Identitätsadapter belegt.
Die Prüfung eines echten Kontos, seines externen Status und konkurrierender Identitäts-
änderungen durch einen späteren Produktionsadapter ist dadurch nicht vorweggenommen.

## M1 – mittel: Nach angewendeter V0007 scheitert die spätere V0006

**Fundstellen und beteiligte Stände:**

- [V0007__permissions_rule_origin_role.sql:4](../backend/src/main/resources/db/migration/V0007__permissions_rule_origin_role.sql#L4)
  und die Migrationsfolge von TASK-038.
- [application.yaml:14–18](../backend/src/main/resources/application.yaml#L14): regulärer
  Flyway-Weg; kein aktiviertes `outOfOrder`. Keine entsprechende Produktanpassung oder
  eigener Flyway-Konfigurator in den geprüften Quellen gefunden.
- Noch offene [V0006 aus PR #85](https://github.com/1uedev/task-manager/blob/46335383dd949dbe3ae86a378fc0468c3089fafb/backend/src/main/resources/db/migration/V0006__appearance_settings.sql),
  gelesener Head `46335383dd949dbe3ae86a378fc0468c3089fafb`. TASK-038 und PR #90 nennen
  diese reservierte Nummer ausdrücklich und erwarten bisher nur Konflikte in Testlisten.

**Eigene PostgreSQL-18.6-Probe:**

1. Leere Datenbank mit den tatsächlichen main-Skripten V0001 bis V0005 und V0007 migriert.
   Die Historie enthält sechs erfolgreiche Einträge und endet bei V0007.
2. Das **unveränderte tatsächliche V0006-Skript** des genannten PR-85-Heads zu derselben
   Skriptmenge hinzugefügt. Kein synthetischer SQL-Platzhalter.
3. Mit den regulären Flyway-Einstellungen erneut validiert/migriert. Die Probe prüft
   zusätzlich, dass `outOfOrder` ausgeschaltet ist.
4. Validierung und Migration werden mit
   `RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED` für V0006 abgewiesen. Die Historie bleibt
   unverändert, die Erscheinungsbildtabelle wird nicht angelegt.
5. Kontrolle: Dieselben sieben Skripte auf einer weiteren **leeren** Datenbank angewendet.
   Alle sieben gelingen; die Erscheinungsbildeinstellung mit Standardorange ist vorhanden.

**Auswirkung:** Ein Bestand, auf dem der gegenwärtige main bereits V0007 ausgeführt hat,
kann den geplanten kombinierten Stand mit V0006 so nicht regulär übernehmen. Korrigierte
Migrationslisten und grüne Tests auf jeweils frischen Datenbanken lösen diesen Übergang
nicht. Die Fehlerursache ist die Reihenfolge, nicht der Inhalt der Erscheinungsbildmigration.
Der ganze künftige Anwendungsstand aus PR #85 wurde nicht gestartet oder geprüft.

Das ist ein konditionaler Updatebefund: Eine produktive Installation, echter Datenverlust
oder ein aktuell defekter frischer main-Start wird nicht behauptet. Die bisher dokumentierte
Voraussetzung fehlender Installationen kann bei der Koordination berücksichtigt werden;
der geprüfte Upgradepfad selbst funktioniert dennoch nicht.

**Vorschlag:** Vor Integration von PR #85 den gemeinsamen Nummern-/Updateweg abstimmen.
Das noch nicht integrierte Erscheinungsbildskript kann koordiniert eine freie höhere Nummer
als V0007 erhalten; Listen und Nachweise sind entsprechend nachzuführen. Die bereits
integrierte V0007 bleibt unverändert. Soll stattdessen bewusst außerhalb der Reihenfolge
migriert werden, braucht dieser abweichende Weg eine ausdrückliche Entscheidung und einen
Nachweis am bestehenden Schema; hier wird `outOfOrder` nicht eigenmächtig aktiviert.
Keine Änderung an Claudes PR oder an Produktmigrationen durch diesen Reviewer.

## Tatsächlich ausgeführte Prüfungen und Reproduktion

Eigene Java-Läufe: Windows, Temurin 25.0.4.1+1, Maven 3.9.16, native PostgreSQL 18.6 über
die vorhandene externe Testdatenbankkonfiguration; nur synthetische isolierte Datenbanken.
Kein eigener Docker-Digestnachweis und kein lokaler Bash-/SIGTERM-Lifecycle-Lauf.

| Lauf | Ergebnis |
|---|---|
| Unverändertes Backend `verify` | **92 bestanden**, keine Fehler/Fehlschläge/übersprungen; Build erfolgreich. |
| Gezielte Verhaltensmutation für R1/K1 | **2 gezielte Fehlschläge**, 0 Ausführungsfehler; beide erwarteten Verweigerungen bleiben aus. Mutation vollständig zurückgenommen. |
| Frischer Rücklauf `clean test` | **4 Methoden bestanden**: die drei Autorenprüfungen zu R1, K1 und V0005-Upgrade sowie die eigene M1-Probe mit frischer Gegenkontrolle. Ein grüner M1-Probelauf bestätigt den beschriebenen Upgradefehler. |
| Repositoryprüfungen | Validator, Aufgabenübersicht und **25 Python-Tests** erfolgreich; Diffprüfung einschließlich der neuen Nachweisdateien erfolgreich. |
| Original-CI auf `9d2ac54` | Alle vier Workflows erfolgreich; [Backend 39241837469](https://github.com/1uedev/task-manager/actions/runs/39241837469) einschließlich Jobschritten für Build, Integration und Start/Stopp/Neustart gelesen. Kein vollständig nachgelesenes Jobprotokoll. |

Belege: [tatsächliche gekürzte Ausgaben](review-evidence/TASK-038-observations.txt),
[genaue Verhaltensmutation](review-evidence/TASK-038-behavior-mutation.diff),
[eigene Migrationstestquelle](review-evidence/MigrationOrderingProbeTest.java) und
[unveränderte V0006-Snapshotfixture](review-evidence/V0006__appearance_settings.sql).
Die Fixture ist nur Prüfeingabe unter `docs/`, keine weitere Produktmigration.
Ihr SHA-256 lautet `1071b9363bed7741b897833a664bdd63c0f2ad8928947d7422893b1a3978ad69`.

Die eigene Testklasse wurde nur vorübergehend unter `backend/src/test` ausgeführt und
danach entfernt. In einem eigenen Checkout des benannten Stands mit leerem Testserver:

```sh
cp docs/review-evidence/MigrationOrderingProbeTest.java backend/src/test/java/taskmanager/permissions/
cd backend
./mvnw --batch-mode --no-transfer-progress -Dtest=MigrationOrderingProbeTest \
  -Dtaskmanager.test.jdbc-url="$REVIEW_DB_URL" \
  -Dtaskmanager.test.username="$REVIEW_DB_USER" \
  -Dtaskmanager.test.password="$REVIEW_DB_PASSWORD" clean test
```

Die Probe kopiert die Skripte in ein eigenes temporäres Prüfverzeichnis und verändert keine
Produktmigration. Anschließend nur die hinzugefügte Testkopie entfernen. Nach Sicherung der
Nachweise wurden eigene Builddateien bereinigt und der eigene PostgreSQL-Testserver beendet.

## Übergabe

R1 und K1 können für die nachgeprüften Abläufe technisch als korrigiert eingeordnet werden.
M1 ist vor der Folgeintegration mit TASK-036 zu koordinieren. Die in PR #92 dokumentierte
fachliche Annahme von TASK-038/TASK-025 bleibt unverändert; keine Änderung an DONE.md.
P2 bis P6, der echte Adapter und dessen Konkurrenznachweise bleiben unverändert offen.
Nicht ausgeführt: UI-/Browserprüfung von PR #85, Last-/Schwachstellenscans oder echte
LDAP-/Portal-/Identitäts-/Projektadapter. Es wurden keine Produktdateien geändert.
