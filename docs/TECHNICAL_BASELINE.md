# Technische Basis und Qualifikation

Stand 17. Oktober 2026 · TASK-021 · Technischer Nachweis, fachliche Abnahme ausstehend

Holgers Fortsetzung nach TASK-032 beauftragt den angekündigten begrenzten Werkzeug-/Vertragsnachweis. Die bisherigen Kandidaten werden geprüft; ein positives Ergebnis ersetzt weder die Abnahme aller Detailvorschläge noch die Beauftragung von TASK-022 bis TASK-031. Der Versuch liegt ausschließlich unter [qualification](../qualification/README.md), sein synthetischer Vertrag unter [contracts](../contracts/README.md). Es entsteht keine Produktinstallation.

## Versionen und Herkunft

| Komponente | Festgehaltene Version | Zweck / Quelle |
|---|---|---|
| Temurin | 25.0.4.1+1 | Java-Laufzeit des CI-Versuchs; [offizielles Release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1) |
| Maven / Wrapper | 3.9.16 / 3.3.4 | Reproduzierbarer Aufruf; [Maven-Archiv](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/), [Wrapper-Archiv](https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/) |
| Spring Boot | 4.1.1 | Versionen über dessen Parent/BOM; [Systemanforderungen](https://docs.spring.io/spring-boot/system-requirements.html) |
| Spring Modulith | 2.1.1 | Prüfung von Modulzugriffen und Zyklen; [Verifikation](https://docs.spring.io/spring-modulith/reference/verification.html) |
| PostgreSQL | 18.6 | Echte flüchtige Testdatenbank; [Versionspolitik](https://www.postgresql.org/support/versioning/) |
| Flyway / PostgreSQL JDBC | 12.4.0 / 42.7.13 | Migration / Datenzugriff, aus Boot-BOM |
| Testcontainers / JUnit Jupiter | 2.0.5 / 6.0.3 | Testumgebung, aus Boot-BOM; [PostgreSQL-Modul](https://java.testcontainers.org/modules/databases/postgres/) |
| Node / npm | 24.22.0 / 11.19.1 | Buildwerkzeuge; [offizielles Versionsarchiv](https://nodejs.org/dist/v24.22.0/) |
| React / React DOM | 19.3.1 | Synthetischer UI-Build; [Versionen](https://react.dev/versions) |
| TypeScript | 5.9.3 | Kompatibilitätsvariante nach dem unten beschriebenen Konflikt |
| Vite | 8.3.4 | Frontend-Build; [offizielle Dokumentation](https://vite.dev/guide/) |
| openapi-typescript / openapi-fetch | 7.13.0 / 0.17.0 | Typgenerator / typisierter Transportclient; [Herstellerdokumentation](https://openapi-ts.dev/introduction) |
| OpenAPI | 3.1.1 | Gewählte Versuchsfassung; [Spezifikation](https://spec.openapis.org/oas/v3.1.1.html) |

npm-Direktversionen und transitive Auflösung sind in `qualification/frontend/package-lock.json` festgehalten. Der Maven-Build verwendet feste direkte Versionen und die Boot-/Modulith-BOM; sein tatsächlich aufgelöster Abhängigkeitsbaum wird im CI-Nachweis gespeichert. Das ist keine vollständige Maven-Lockdatei. Wiederholte Builds verwenden dieselben Koordinaten; die Herkunft sämtlicher transitiver Artefakte und eine vollständige Reproduzierbarkeit des späteren Produktimages sind gesondert zu qualifizieren.

**Gefundene Abweichung:** Die ursprünglich vorgeschlagene Kombination TypeScript 6.0.3 mit openapi-typescript 7.13.0 scheitert bei normaler npm-Auflösung: Der Generator verlangt `typescript: ^5.x`. Der Versuch verwendet deshalb 5.9.3; weder `--force` noch `--legacy-peer-deps` wird eingesetzt. Das ist eine begründete technische Versuchsauswahl, keine neue fachliche Vorgabe. Für TypeScript 6 oder 7 braucht es später eine passende Generatorversion beziehungsweise einen erneut geprüften anderen Generator. React mit TypeScript bleibt unverändert bestätigt.

Die öffentlich gelesene Modulith-Kompatibilitätstabelle belegt Boot 4.1 nicht eindeutig. Deshalb enthält der Versuch eine positive Prüfung und zwei bewusst fehlerhafte Modulstrukturen. Ein grüner Versuch qualifiziert diese benutzten APIs und diese Kombination, nicht jede Modulith-Funktion oder jede spätere Erweiterung.

## Gemeinsame Verträge für die folgenden Aufgaben

Diese Fassung konkretisiert den Entwurf für die Übergabe. Die fachliche/technische Annahme durch Holger und die folgenden Implementierungsaufträge bleiben erforderlich.

| Grenze | Zu übernehmende Vertragsregel | Noch vor der betreffenden Umsetzung nötig |
|---|---|---|
| Identität → Rechte | Serverseitiger Kontext mit Unternehmens-/Benutzer-ID, Anmeldestufe, Sitzungsreferenz und aktueller Rechteversion. Kein vom Browser behauptetes „bereits geprüft“. Pflichtwechsel verhindert regulären Fachzugriff. | Konto-/Sitzungsübergänge, LDAP-Lebenszyklus und Wiederherstellung; TASK-024 |
| Rechte → Fachmodul | Aktion plus vertrauenswürdige Objekt-/Projektmerkmale ergeben erlaubte Sicht und Änderungen. Feldverbot gewinnt. Keine Tickets/Wiki-Entitäten oder fremden Tabellen im Rechtemodul. | Allgemeiner Aktionsalgorithmus, Administrationsgrenzen und Serialisierung bei Rechteentzug; TASK-025 |
| Projekt → Tickets | Projekt-ID, Unternehmens-/Organisationsbezug, aktuelle Konfigurationsversion und begrenzte DTOs über öffentliche Modulschnittstellen. Keine fremden ORM-Entitäten. | Konkrete Typen/Felder/Übergänge und Änderungsverfahren; TASK-026 |
| Fachmodule → Tags/Suche | Objekt-IDs, Versionen und nach Sichtgrenzen getrennte Inhalte über freigegebene Operationen. Eine Projektion besitzt eigene Tabellen. | Rechtesegmentierung, Katalogregeln und Suchfelder/-filter; TASK-028/TASK-029 |
| Zusammensetzung → Module | Ein verantwortlicher Orchestrator setzt öffentliche Operationen zusammen. Module greifen direkt in Java aufeinander zu; keine internen HTTP-/gRPC-Netzaufrufe im gemeinsamen Prozess erforderlich. | Prozess-/Auftragsvertrag für spätere Worker; keine flüchtige Zustellgarantie |
| Schreibtransaktion | Ein gemeinsamer DataSource-/Transaktionsmanager; beteiligte Operationen verlangen eine bestehende Transaktion. JPA wird vor abhängigem JDBC-Zugriff geflusht. Fehler rollen alle Beteiligten zurück. | Autorisierungsrennen, Sperrreihenfolge, Zeitüberschreitungen, Outbox und Wiederholungsregeln für echte Abläufe |
| Datenbankmigration | Ein Flyway-Weg, keine automatische Produktions-Schemaänderung durch ORM. Jede Migration hat einen Besitzer; gemeinsame Nummern und Root-Konfiguration werden koordiniert. | Reale Erstinstallation, Updates, Rollback-/Vorwärtsstrategie und Restore |
| API → Oberfläche | OpenAPI als gemeinsame Transportquelle; generierte Typen nicht manuell ändern. Erfolg und Problem-Details getrennt, opake Versionen als Zeichenfolgen, ETag und Fachversion nicht gleichsetzen. | Produktive Pfade/Authentifizierung, vollständige Schema-/Runtimevalidierung und Datenschutzregeln |

Modulabhängigkeiten bleiben gerichtet wie in [TECHNICAL_CONTRACTS.md](TECHNICAL_CONTRACTS.md): kleine technische Verträge → Identität/Rechte → Projekte → Fachmodule → zusammengesetzte Abläufe. Ein Modultest ersetzt keine Prüfung dynamischen SQLs oder der tatsächlichen Datenhoheit. In der Probe lesen nur die Tests für Nachweise beide Tabellen; die beiden Module schreiben ausschließlich ihre eigenen Tabellen über öffentliche Operationen.

Für TASK-022 liegt damit ein überprüfbares Beispiel für Migration, Modulprüfung und Transaktion vor; TASK-023 erhält einen reproduzierbaren Generator-/Client-/Buildablauf. Fachendpunkte werden daraus nicht automatisch übernommen. Änderungen an gemeinsamen Verträgen erfolgen zuerst mit begründeter Änderung und angepassten Nachweisen, danach in den Verbrauchern. Getrennte Arbeitsbereiche und der zentrale TODO bleiben maßgeblich.

## Lizenz- und Betriebsgrenzen

Erfasste Angaben aus direkten Paket-/POM-Metadaten: Spring Boot und Modulith Apache-2.0; Maven/Wrapper Apache-2.0; PostgreSQL-JDBC BSD-2-Clause; Testcontainers MIT; React, Vite, openapi-typescript und openapi-fetch MIT; TypeScript Apache-2.0. Die direkte Flyway-POM enthält keinen eigenen Lizenzeintrag; dessen vererbte Angaben und alle transitiven Komponenten sind zusätzlich zu erfassen, bevor daraus ein Produkt freigegeben wird. Temurin, PostgreSQL, Betriebssystemschichten und native Node-/Vite-Abhängigkeiten benötigen ebenfalls ihre versionsbezogenen Hinweise im späteren Auslieferungspaket. Diese Liste ist keine vollständige rechtliche oder Sicherheitsfreigabe. Produktlizenz bleibt OPEN-020.

Nur die Wrapper-Skripte werden als Fremdquellen übernommen; Originalheader, [Lizenz](../qualification/backend/.mvn/wrapper/LICENSE.txt) und [NOTICE](../qualification/backend/.mvn/wrapper/NOTICE.txt) bleiben dabei. Alle übrigen Komponenten werden als Build-/Testabhängigkeiten bezogen. Der Wrapper lädt Maven mit festgeschriebener SHA-256-Prüfsumme. Das ursprüngliche Wrapper-Archiv wurde über Maven Central bezogen, gegen dessen veröffentlichte SHA-1-Angabe verglichen und zusätzlich als SHA-256 `6cb584c2bc907b849a0b931d8266d3ff3214cdd3127115ed4f49fb7176413d36` erfasst.

Die lokale Umgebung wurde nicht global umgestellt. Der lokale Frontendnachweis verwendet einen temporären Node-24-Download, geprüft gegen die offiziellen SHA-256-Angaben. Java-/PostgreSQL-Nachweise erfolgen im flüchtigen GitHub-Runner; ein lokaler fehlender Docker-Daemon wird nicht als erfolgreicher Datenbanktest ausgegeben. Der Workflow hat nur lesende Repository-Rechte und verwendet keine Produktgeheimnisse.

## Tatsächlicher Prüfstand

Der erste npm-Versuch mit TypeScript 6.0.3 ist wie oben beschrieben fehlgeschlagen. Die kompatible Variante und der gesamte Java-/PostgreSQL-/Frontend-Lauf werden derzeit geprüft. Ein erfolgreicher Gesamtnachweis wird erst mit konkretem Commit und CI-Lauf eingetragen. Keine Produktanmeldung, Fachrechte, Last- oder Restore-Erfüllung wird aus diesem Versuch abgeleitet.
