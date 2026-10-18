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

Erfasste Angaben aus direkten Paket-/POM-Metadaten: Spring Boot und Modulith Apache-2.0; Maven/Wrapper Apache-2.0; PostgreSQL-JDBC BSD-2-Clause; Testcontainers MIT; React, Vite, openapi-typescript und openapi-fetch MIT; TypeScript Apache-2.0. Flyway 12.4.0 erbt Apache-2.0 aus seiner [Parent-POM](https://repo.maven.apache.org/maven2/org/flywaydb/flyway-parent/12.4.0/flyway-parent-12.4.0.pom); Hibernate 7.4.5.Final nennt ebenfalls Apache-2.0 in seiner [versionsbezogenen POM](https://repo.maven.apache.org/maven2/org/hibernate/orm/hibernate-core/7.4.5.Final/hibernate-core-7.4.5.Final.pom). Weitere transitive Komponenten sind vor Produktfreigabe vollständig zu erfassen. Temurin, PostgreSQL, Betriebssystemschichten und native Node-/Vite-Abhängigkeiten benötigen ebenfalls ihre versionsbezogenen Hinweise im späteren Auslieferungspaket. Diese Liste ist keine vollständige rechtliche oder Sicherheitsfreigabe. Produktlizenz bleibt OPEN-020.

Nur die Wrapper-Skripte werden als Fremdquellen übernommen; Originalheader, [Lizenz](../qualification/backend/.mvn/wrapper/LICENSE.txt) und [NOTICE](../qualification/backend/.mvn/wrapper/NOTICE.txt) bleiben dabei. Alle übrigen Komponenten werden als Build-/Testabhängigkeiten bezogen. Der Wrapper lädt Maven mit festgeschriebener SHA-256-Prüfsumme. Das ursprüngliche Wrapper-Archiv wurde über Maven Central bezogen, gegen dessen veröffentlichte SHA-1-Angabe verglichen und zusätzlich als SHA-256 `6cb584c2bc907b849a0b931d8266d3ff3214cdd3127115ed4f49fb7176413d36` erfasst.

Das PostgreSQL-Image ist zusätzlich an den öffentlich abgerufenen Manifest-Digest `sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336` gebunden. Damit führt eine nachträgliche Änderung des Tags nicht still zu einem anderen Testimage.

Die lokale Umgebung wurde nicht global umgestellt. Der lokale Frontendnachweis verwendet einen temporären Node-24-Download, geprüft gegen die offiziellen SHA-256-Angaben. Java-/PostgreSQL-Nachweise erfolgen im flüchtigen GitHub-Runner; ein lokaler fehlender Docker-Daemon wird nicht als erfolgreicher Datenbanktest ausgegeben. Der Workflow hat nur lesende Repository-Rechte und verwendet keine Produktgeheimnisse.

## Wartung und verbleibende Freigaben

Die festgeschriebenen Versionen sichern diesen Versuch, nicht die gesamte künftige Produktlaufzeit. Vor der Produktfreigabe müssen Supportzeiträume, verfügbare Sicherheitskorrekturen und die vollständige Komponentenliste erneut geprüft werden. Ein kostenpflichtiger Hersteller-Support oder ein Wartungs-SLA ist nicht ausgewählt. Empfohlen ist, spätere Versionsänderungen als eigenen PR mit denselben Modul-, Transaktions-, Generator- und Clientprüfungen vorzulegen; ein erfolgreicher Build allein ersetzt die betroffenen Produktprüfungen nicht. Die alternative Datenbankunterstützung bleibt architektonisch vorzubereiten und ist hier nicht nachgewiesen.

## Tatsächlicher Prüfstand

Der vollständige technische Lauf auf Commit `79ad5fe18dfbbffeabbd46cd4643cf9bf01459c1` ist erfolgreich: [Qualifikation 39063473112](https://github.com/1uedev/task-manager/actions/runs/39063473112), [Repository-Prüfung 39063473068](https://github.com/1uedev/task-manager/actions/runs/39063473068). Die Testberichte wurden heruntergeladen und gegen das CI-Protokoll geprüft. Der Versuch lief auf Ubuntu 24.04 x64 mit den oben genannten Versionen.

| Tatsächlich ausgeführter Nachweis | Ergebnis | Aussagegrenze |
|---|---|---|
| Repository-Validator und Regressionstests | Validator erfolgreich; 12 Tests bestanden | Metadaten, Links und Aufgabenführung; keine Produktabnahme |
| Spring-Modulprüfung | 3 Tests bestanden, 0 übersprungen | Zwei Module erkannt; erlaubte Struktur akzeptiert, verbotener interner Zugriff und Zyklus abgewiesen |
| PostgreSQL / Flyway / JPA / JDBC | 4 Tests bestanden, 0 übersprungen | Echte Datenbank 18.6, Migration, gemeinsamer Commit und zwei Rückabwicklungsfälle; keine Rechte- oder Parallelitätsprüfung |
| Java-Transportfixture | 1 Test bestanden, 0 übersprungen | Synthetische JSON-Antworten aus Jackson; kein laufender HTTP-Server |
| TypeScript-Client | 6 Tests bestanden, 0 übersprungen | Java-Fixtures über simulierten HTTP-Transport; Erfolg, Fehler, Konflikt, Header, Netzfehler und begrenzte Laufzeitprüfung |
| OpenAPI und Oberfläche | Generierung ohne Git-Abweichung, strenge Typprüfung inklusive Negativbeispielen, React-Build erfolgreich | Keine Browser-/Bedienbarkeitsprüfung oder vollständige Laufzeit-Schemavalidierung |
| Aufgelöste Abhängigkeiten | Maven-Abhängigkeitsbaum und npm-Lockdatei vorhanden | Kein vollständiges Produkt-SBOM und keine allgemeine Sicherheitsfreigabe |

Java ergibt zusammen **8/8**, Node **6/6**, Repository **12/12** erfolgreiche Tests. Zusätzlich liefen der Repository-Validator und `git diff --check` lokal erfolgreich; Typgenerierung, Typprüfung und React-Build wurden auch mit dem temporären lokalen Node ausgeführt. Java-/Datenbanktests liefen ausschließlich im CI-Runner. Das CI-Artefakt `qualification-evidence` enthält Surefire-Berichte, synthetische JSON-Fixtures, Abhängigkeitsbaum und Buildausgabe mit sieben Tagen Aufbewahrung. Danach bleiben Quellstand, dieser Ergebnisbericht und die CI-Links zur Reproduktion; die Verfügbarkeit der GitHub-Protokolle folgt deren Aufbewahrungseinstellung.

### Gefundene Fehler und Korrekturen

- Der erste npm-Versuch mit TypeScript 6.0.3 scheiterte am beschriebenen Peer-Konflikt. Die erfolgreiche Variante verwendet 5.9.3 ohne erzwungene Auflösung.
- Ein bestehendes Repository-Testfixture kopierte nur Markdown-Dateien und verlor dadurch neue gültige Links auf YAML-/Lizenz-/Workflow-Dateien. Es kopiert jetzt auch diese Quellen, schließt generierte/private Laufzeitverzeichnisse aus und hat einen zusätzlichen Link-Regressionstest.
- [Erster CI-Lauf 39063143728](https://github.com/1uedev/task-manager/actions/runs/39063143728): Modulith schloss die Klassen unter `src/test` standardmäßig aus. Die [öffentliche ImportOption-Überladung](https://github.com/spring-projects/spring-modulith/blob/2.1.1/spring-modulith-core/src/main/java/org/springframework/modulith/core/ApplicationModules.java) nimmt nun die synthetischen Fixtures auf; alle Fälle prüfen zusätzlich die tatsächliche Modulzahl. Die Regeln wurden nicht abgeschaltet.
- [Zweiter CI-Lauf 39063368895](https://github.com/1uedev/task-manager/actions/runs/39063368895): Testcontainers akzeptierte die kombinierte Tag-/Digest-Angabe nicht als PostgreSQL-Familie. Die Referenz verwendet jetzt den unveränderten offiziellen Digest ohne Tag und erklärt dessen PostgreSQL-Kompatibilität explizit. Die Prüfung der tatsächlichen Serverversion bleibt erhalten.
- Das getrennte KI-Review fand eine unquotierte Beschreibung mit Komma in einer YAML-Flow-Map. Sie ist quotiert und die Typdatei neu generiert; die vollständige Abgrenzung von Fachversion und ETag bleibt erhalten.

### Nachtrag aus TASK-035 am 18. Oktober 2026

Die Befunde Q1 bis Q4 der [Claude-Gegenprüfung](REVIEW_TASK-021_TASK-033.md) sind im Versuch behoben; Versionen und Vertragsregeln bleiben unverändert.

- Q1: `TransactionQualificationTest` prüft zusätzlich, dass `Records.create` und `Projection.index` ohne bestehende Transaktion mit `IllegalTransactionStateException` abgewiesen werden und nichts schreiben. Gegenprobe: Mit vorübergehend entfernter Transaktionspflicht schlägt genau dieser Test fehl ([Lauf 39116009163](https://github.com/1uedev/task-manager/actions/runs/39116009163), 9 Tests, 1 Fehler); die Gegenprobe ist im Ergebnis zurückgenommen.
- Q2: Die beiden Modulith-Negativtests prüfen zusätzlich die Verstoßart („depends on non-exposed type“ beziehungsweise „Cycle detected“).
- Q3: `.gitattributes` checkt `*.cmd` mit CRLF aus; `mvnw.cmd` ist damit bytegleich mit dem Wrapper-Archiv 3.3.4. Der gespeicherte Inhalt ändert sich nicht.
- Q4: `TransportFixtureTest` verwendet `asString()` statt des veralteten `asText()`; die Deprecation-Meldung entfällt.

Damit ergibt Java **9/9** Tests (Modulprüfung 3, PostgreSQL/Transaktion 5, Transportfixture 1); Node bleibt bei **6/6**. Die Zahlen und Läufe in der Tabelle oben beschreiben weiterhin den ursprünglichen Stand `79ad5fe`.

Keine Produktanmeldung, Fachrechte, Suchleistung oder Restore-Erfüllung wird aus diesem Versuch abgeleitet. TASK-021 ist ein technisches Ergebnis zur Prüfung; Annahme der gemeinsamen Vertragsgrundlage und Auftrag für die nächsten Produktschritte bleiben getrennt.


Die getrennte Codex-Sitzung `sessions_tags_search_review` prüfte Quellen und Verträge sowie die beiden Korrekturstufen bis `79ad5fe`; zuletzt las sie auch die heruntergeladenen Testberichte und das CI-Protokoll. Nach Behebung des YAML-Befunds blieben keine offenen Befunde. Sie führte selbst keine Java-/Docker-Tests aus. Dieser abschließend ergänzte Ergebnisbericht stammt von der koordinierenden Sitzung; die fachliche Abnahme durch Holger bleibt offen.
