# Nachprüfung der Berechtigungskorrekturen TASK-025 / PR #82

19. Oktober 2026 · Codex-Sitzung `codex-20261019-permissions-followup-01`.

**Ergebnis:** Die 87 bestehenden Backendtests bestehen im eigenen Lauf. Die Korrektur von
P1 schützt vor unbekannten Konten und leeren Gruppen, zählt jedoch auch ein deaktiviertes
Konto als verbleibende Administration: **R1, mittel, reproduziert**. Bei P7 ist zusätzlich
die Behandlung einer Setzung durch eine frühere Unternehmensverwaltung zu präzisieren:
**K1, Klärung mit mittlerer Tragweite**, beobachtetes Verhalten reproduziert.

Dies ist eine technische Gegenprüfung von Claudes Korrekturen nach DEC-040. Codex hat die
ursprüngliche Berechtigungsbasis aus PR #74 geschrieben; dieser Bericht ist deshalb kein
neuer unabhängiger Gesamtreview des eigenen ursprünglichen Moduls. Der gegenseitige
[Claude-Erstbericht](REVIEW_TASK-025_PR-74.md) bleibt erhalten. Keine fachliche Abnahme,
keine Übernahme von TASK-025 oder TASK-036, keine Änderungen an TODO.md oder DONE.md.

## Geprüfter Stand und Belege

- Prüfgegenstand: [PR #82](https://github.com/1uedev/task-manager/pull/82), Korrekturen P1/P7
  nach der integrierten Reservierung [#81](https://github.com/1uedev/task-manager/pull/81).
- Tatsächlich lokal ausgeführter Commit: `8a2a57283caa7ea1fa2b12b79b0c2df2599a78d6`.
- Der PR war zu Beginn ein Entwurf und wurde während dieser Prüfung integriert.
  Letzter PR-Head: `d0ce1c2ebda94b05ddef86e0167a2713cdab9017`;
  main-Merge: `dbc188b47358a2f20e76d46a2fbd5420dbf5d118`.
- Eigener Git-Vergleich zwischen dem ausgeführten Stand und diesem main: **nur TODO.md
  verschieden**. Backendquellen, Tests, Migrationen, Verträge und Workflows sind identisch.
  Die eigenen Nachweise gelten damit für denselben integrierten Produktcode.
- Maßgebliche Anforderungen: REQ-ACL-001, REQ-ACL-003, REQ-ACL-004, REQ-ACL-005;
  DEC-040, DEC-057, der Auftrag in TASK-025 und die Ergänzung DEC-059 aus PR #82.

Gelesen: gemeinsame Einstiegs-/Arbeitsregeln einschließlich CLAUDE.md, Anforderungen und
Entscheidungen, Roadmap, relevante Architektur- und Modulverträge, TECHNICAL_BASELINE.md,
Erstbericht, vollständiger PR-Diff, Backendvertrag, Java-Ports, Rechteimplementierung,
V0002/V0005 und vorhandene Integrations-/Konkurrenztests. Geprüft wird die begrenzte
Korrektur; P2 bis P6 und der echte Identitätsadapter werden nicht nebenbei implementiert.

## R1 – mittel: Deaktiviertes Konto genügt als verbleibende Administration

**Fundstellen im geprüften Stand:**
[JdbcPermissions.java:200–203](../backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java#L200),
aufgerufen von `setMember` Zeile 88 und `setAction` Zeile 107;
[IdentityAccess.java:19–20](../backend/src/main/java/taskmanager/permissions/IdentityAccess.java#L19).
Die vorhandene Testfixture unterscheidet ausdrücklich zwischen Aktivität bei `isCurrent`
und bloßer Existenz bei `userExists`.

**Auftrag und Abweichung:** Der auf main integrierte Claim #81 verlangt im TASK-025-Eintrag,
dass nach der Änderung noch ein **aktives Mitglied** `permissions.manage` auf Unternehmensebene
hält. Das entspricht dem im Erstbericht angenommenen P1-Vorschlag. Die neue Prüfung fragt
stattdessen nur `userExists` ab. Der neue DEC-059-Text und Backendvertrag beschreiben das
schwächer als „bekanntes Mitglied“. Das dokumentiert den implementierten Unterschied,
belegt aber keine gesonderte Entscheidung, die Aktivitätsanforderung zu entfernen.

**Eigene Gegenprobe N1, beide Entzugswege ausgeführt:**

1. A ist aktiver Unternehmensverwalter. B ist ebenfalls einer Verwaltungsgruppe zugeordnet,
   aber bereits deaktiviert. Konto, Gruppen und Mitgliedschaften sind synthetische Testdaten.
2. Der Adapter verweigert B über `isCurrent`, kennt das Konto jedoch über `userExists`.
   Diese Semantik und die bis zum Commit gehaltenen FOR-SHARE-Sperren entsprechen der
   vorhandenen Autorenfixture; es wird kein ungesperrter oder absichtlich freigebender
   Produktionsadapter unterstellt.
3. A entfernt über `setMember` seine eigene Mitgliedschaft. In einem getrennten frischen
   Ausgangszustand entzieht A alternativ über `setAction` das Unternehmens-Verwaltungsrecht.
4. Beide Aufrufe **committen**, erhöhen die Revision und schreiben Audit. Danach wird die
   Unternehmensverwaltung sowohl für A als auch für B verweigert. Es bleibt kein aktiver
   Verwalter, obwohl der aktuelle Aufruf aus dem Berechtigungsmodul den Entzug ausführt.

Das Konto B wird **vor** dem geprüften Entzug deaktiviert. Dies ist daher nicht nur der
bereits dokumentierte zukünftige Fall, dass der Identitätsadapter später selbst das letzte
aktive Administrationskonto deaktiviert. Die ausschließlich prüfende Gegenprobe hat keine
Produktquelle geändert. Ohne echten Identitätsadapter verweigert die aktuelle Anwendung
ohnehin alle Modulaufrufe; eine bereits erreichbare produktive HTTP-Lücke wird nicht behauptet.

**Kontrolle:** Fehlt das Konto B vollständig, werden beide Entzugswege korrekt mit
`PermissionDenied` abgewiesen. Mitgliedschaft, Aktionsregel, Revision und Audit bleiben
unverändert. Der Fehler ist somit auf „bekannt, aber nicht aktiv“ eingegrenzt.

**Vorschlag:** Die Koordination soll den ursprünglichen Aktivitätsauftrag in Entscheidung
und Vertrag konsistent erhalten. Für die verbleibenden Rechteinhaber ist ein aktueller,
unter Sperre geprüfter aktiver/interner Unternehmenskontext nötig. Die bloße Existenzprüfung
für Gruppenzuordnungen ist dafür kein hinreichender Nachweis. Einen gegebenenfalls neuen
Adapterport zuerst abstimmen; keine fremden Identitätstabellen im Rechtemodul lesen.
Für beide Entzugswege den obigen Fall als ablehnenden Regressionstest aufnehmen und auch
konkurrierende Zustandswechsel mit dem späteren echten Adapter nachweisen.

## K1 – Klärung: Eigener Urheber oder Herkunft aus Unternehmensverwaltung?

**Fundstellen:**
[JdbcPermissions.java:125–133](../backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java#L125),
[V0005__permissions_rule_origin.sql:3](../backend/src/main/resources/db/migration/V0005__permissions_rule_origin.sql#L3),
Backendvertrag im Abschnitt Delegation und DEC-059/P7.

Der PR verspricht einerseits, dass eine Delegation eigene Setzungen zurücknehmen darf,
andererseits, dass Verbote der Unternehmensverwaltung unberührt bleiben. `set_by` erfasst
nur die Person, nicht ihre Verwaltungsbefugnis zum Zeitpunkt der Setzung. Diese beiden
Aussagen unterscheiden sich, sobald dieselbe Person ihre Unternehmensrolle verliert und
eine Projektdelegation behält.

**Eigene Gegenprobe N2:** A setzt als Unternehmensverwalter ein Schreibverbot für eine
andere Gruppe. Ein unabhängiger, aktiver Verwalter B entzieht A anschließend ausschließlich
die Unternehmensrolle. A besitzt weiterhin eine Projektdelegation und eigene Feldrechte.
Die Probe bestätigt: keine Unternehmensverwaltung mehr, Projektverwaltung weiterhin erlaubt.
Dennoch ersetzt A das alte Verbot als Projektdelegation erfolgreich durch `ALLOW`; der
betroffene Benutzer kann das Feld danach schreiben. Alle drei Änderungen erfolgen über
öffentliche Moduloperationen mit neuer Revision und Audit, nicht durch direkte Manipulation
der Regel nach der synthetischen Ausgangseinrichtung.

**Einordnung:** Das Verhalten ist reproduziert. Ob es als verbotene Aufhebung einer
Unternehmenssetzung oder als erlaubte Änderung der eigenen persönlichen Setzung gilt,
muss die Koordination ausdrücklich präzisieren. Deshalb ist dies **keine zweite als
bestätigt behauptete Sicherheitslücke**, sondern eine relevante Vertragsunklarheit.
Der jetzige pauschale Satz zum Schutz von Unternehmensverboten deckt den Ablauf nicht ab.

**Vorschlag:** Wenn Unternehmenssetzungen unabhängig vom späteren Rollenwechsel geschützt
bleiben sollen, muss die Herkunft zusätzlich zur Urheber-ID die Setzbefugnis festhalten;
eine Delegation darf dann nur eigene als Delegation angelegte Verbote ändern. Bereits
unklare Herkunft konservativ behandeln. Andernfalls die personenbezogene Bedeutung und
das gezeigte Rollenwechselverhalten ausdrücklich entscheiden und im Vertrag dokumentieren.
V0005 ist inzwischen integriert: spätere Schemaergänzungen benötigen eine abgestimmte
neue Vorwärtsmigration; nicht die alte Migration ändern.

## Bestätigte Teilkorrekturen und ausgeführte Prüfungen

| Eigener Nachweis | Ergebnis und Grenze |
|---|---|
| Unverändertes Backend `verify` | **87 Tests bestanden**, 0 Fehler/Fehlschläge/übersprungen; Build erfolgreich. Windows, Temurin 25.0.4.1+1, Maven 3.9.16, native PostgreSQL 18.6 über die vorhandene externe Testkonfiguration; kein eigener Docker-Digestnachweis. |
| Vier neue Autorenprüfungen innerhalb dieser 87 Tests | Letzte Verwaltung/unbekannte Gruppe, verbleibende Verwaltung, konkurrierender Entzug und Delegationsherkunft bestanden. Der Autor dokumentiert selbst, dass der Konkurrenztest bereits vor seiner Änderung bestand. Kein eigener Mutationslauf hierzu. |
| Vier eigene zusätzliche Prüfmethoden | N1 reproduziert beide Entzugswege; N2 reproduziert den Rollenwechsel. Zwei Kontrollmethoden prüfen vollständigen Rollback beim unbekannten Konto und den echten Upgradeweg V0004 → V0005. Alle vier ohne Ausführungsfehler. Ein grüner N1-/N2-Lauf bestätigt die Beobachtung, keine Abnahme. |
| Upgrade mit vorhandener Feldregel | Flyway zunächst bis V0004, synthetische vorhandene DENY-Regel, dann echte V0005. Regel bleibt DENY, Herkunft NULL, frühere Migrationsprüfsummen unverändert. Der bestandene Autorenlauf prüft zusätzlich die Verweigerung bei unbekannter Herkunft. |
| CI auf dem ausgeführten Head | [Backend 39164582353](https://github.com/1uedev/task-manager/actions/runs/39164582353): erfolgreicher Status einschließlich Build, Integration und Start/Stopp/Neustart anhand der Jobschritte gelesen; die drei anderen Workflows ebenfalls erfolgreich. Kein vollständig nachgelesenes Jobprotokoll und kein eigener lokaler Bash-/SIGTERM-Lauf. |
| Repositoryprüfungen des Berichts | Repositoryvalidator und Aufgabenübersicht erfolgreich; 25 Python-Tests bestanden; `git diff --check` einschließlich der neu gestagten Dateien erfolgreich. Eigenes beschreibbares Tempverzeichnis für die Python-Fixtures. |

Die [ausgeführte Testquelle](review-evidence/PermissionsFixesFollowupProbeTest.java) und
[gekürzte tatsächliche Ausgaben](review-evidence/TASK-025-pr82-observations.txt) liegen bei.
Die Klasse lag nur vorübergehend unter `backend/src/test` und wurde danach entfernt.
Unter `docs/` nimmt sie nicht am regulären Build teil. Zum Wiederholen auf dem benannten
Produktstand mit isoliertem Testserver und Berechtigung zum Anlegen leerer Datenbanken:

```sh
cp docs/review-evidence/PermissionsFixesFollowupProbeTest.java backend/src/test/java/taskmanager/permissions/
cd backend
./mvnw --batch-mode --no-transfer-progress -Dtest=PermissionsFixesFollowupProbeTest \
  -Dtaskmanager.test.jdbc-url="$REVIEW_DB_URL" \
  -Dtaskmanager.test.username="$REVIEW_DB_USER" \
  -Dtaskmanager.test.password="$REVIEW_DB_PASSWORD" test
```

Danach nur die hinzugefügte Testkopie entfernen und vor regulären Gesamtläufen `clean`
verwenden. Der eigene Loopback-Testserver mit ausschließlich synthetischen Daten wurde
nach Sicherung der Nachweise beendet; die erzeugten Backend-Builddateien wurden bereinigt.

## Übergabe und Grenzen

R1 an die Koordination für eine Korrektur im bestehenden Zuständigkeitsverfahren geben;
K1 vor einer Zusage zum uneingeschränkten Herkunftsschutz entscheiden. Anschließend den
benannten Korrekturcommit gezielt nachprüfen. P2 bis P6 des Erstberichts bleiben unverändert.
Die echte Identitäts-/Projektintegration ist weiterhin nicht nachgewiesen oder zugewiesen.
Keine neue Oberfläche, Schnittstelle oder Folgefunktion implementiert; keine Blockierung
aufgehoben und keine fachliche Abnahme erklärt.

Nicht selbst ausgeführt: Last-/Schwachstellenscan, HTTP-/Browserablauf, lokales Lifecycle-
Bashskript und echte Identitäts-/LDAP-/Portal-/Projektadapter. Die Gegenproben verwenden
synchronen Modulzugriff in echten JDBC-Transaktionen, keine simulierten Datenbankantworten.
