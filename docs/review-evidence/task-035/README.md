# Reproduktion der Nachprüfung TASK-035 / PR #62

Sitzung `codex-20261020-qualification-followup-01`, 20. Oktober 2026. Geprüft auf main `3e3349ec7cfd8854138af6c4d9b405d33e4a32f2`; relevante Qualifikationsquellen unverändert seit PR #62. Einordnung im [Prüfbericht](../../REVIEW_TASK-035_PR-62.md), gekürzte Ausgaben in [results.txt](results.txt).

## Verbindungsadapter und Gegenproben

- [native-postgresql-adapter.patch](native-postgresql-adapter.patch): ersetzt ausschließlich den Containeraufbau und die drei dynamischen Verbindungseigenschaften. Bestehende Testfälle und Assertions bleiben unverändert. Nur in einem eigenen Testcheckout verwenden; keine Produktkonfiguration.
- [records-mandatory-mutation.patch](records-mandatory-mutation.patch): entfernt nur die MANDATORY-Annotation in RecordService.
- [projection-mandatory-mutation.patch](projection-mandatory-mutation.patch): entfernt nur die MANDATORY-Annotation in JdbcProjection.
- [swapped-module-fixtures-mutation.patch](swapped-module-fixtures-mutation.patch): vertauscht die beiden fehlerhaften Fixtures. Violations bleiben vorhanden, aber die präzise erwartete Verstoßart fehlt.

Die drei Mutationen werden **einzeln** angewendet und jeweils vor der nächsten zurückgenommen. Die ersten beiden benötigen die echte synthetische Testdatenbank. Der Modultest benötigt keine Datenbank. Sichere Ausgangs- und Rückläufe müssen bestehen; die Mutation eines Ports muss genau einen der fünf Transaktionstests scheitern lassen, die vertauschten Fixtures zwei der drei Modultests.

## Beispiel mit einer eigenen nativen PostgreSQL 18.6

Leere synthetische Datenbank `review` mit einem eigens dafür bestimmten Benutzer bereitstellen. Den Adapter im Repository-Stamm anwenden. Verbindung an die eigene Testdatenbank anpassen; keine vorhandene Anwendung oder Kundendatenbank verwenden.

```sh
git apply --unidiff-zero docs/review-evidence/task-035/native-postgresql-adapter.patch
cd qualification/backend
./mvnw -Dreview.jdbc-url=jdbc:postgresql://127.0.0.1:5432/review \
  -Dreview.username=reviewer -Dreview.password=synthetic-review-only clean verify
```

Unter Windows wurde tatsächlich `mvnw.cmd` mit denselben Mavenargumenten verwendet. Für jede Portmutation denselben Verbindungsaufruf mit `-Dtest=TransactionQualificationTest clean test` ausführen. Nach jeweiliger Rücknahme beide Ports wieder mit Annotation belassen. Die Modulmutation mit `-Dtest=ModuleQualificationTest clean test` prüfen. Anschließend alle Mutationen zurücknehmen und die neun Fälle mit dem Adapter erneut sauber bauen.

Erst **nach** dem positiven Java-Lauf in `qualification/frontend` `npm ci --ignore-scripts` und `npm run verify` ausführen. Der Client liest die tatsächlich erzeugten Java-Fixtures; ein gleichzeitiges `clean` im Backend löscht diese. Danach den lokalen Verbindungsadapter ebenfalls zurücknehmen und den unveränderten Modul-/Transportlauf mit `-Dtest=ModuleQualificationTest,TransportFixtureTest clean verify` neu kompilieren. Mit erreichbarem Docker kann stattdessen der unveränderte reguläre Neun-Test-Lauf nach qualification/README.md ausgeführt werden.

## Wrapper-Nachweis

Das passende Herkunftsarchiv ist die **only-script**-Ausgabe 3.3.4, wie in `.mvn/wrapper/maven-wrapper.properties` angegeben. Das ähnlich benannte bin-Archiv enthält eine andere Wrapper-Variante und ist dafür kein Vergleichsmaßstab. Im frischen Windows-Worktree wurden die Bytes der ausgecheckten Datei direkt mit dem entpackten Archiveintrag verglichen; kein vorheriges Normalisieren der Zeilenenden.
