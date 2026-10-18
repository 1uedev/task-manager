# Isolierter technischer Nachweis (TASK-021)

Dieser Ordner enthält ausschließlich synthetische Build- und Vertragsversuche. Es gibt kein auslieferbares Produkt, keine Konten, keine Fachmodule und keinen HTTP-Server. Das Java-Bootstrap liegt unter `src/test`; der React-Einstieg dient nur dem Buildnachweis.

Voraussetzungen: Temurin 25.0.4.1+1, Node 24.22.0 mit npm 11.19.1 und ein erreichbarer Docker-Daemon. Maven 3.9.16 wird über den übernommenen Apache-Wrapper mit SHA-256-Prüfung geladen. Erste Läufe benötigen Internet für öffentliche Abhängigkeiten und das PostgreSQL-Testimage. Keine Zugangsdaten oder Kundendaten angeben. Es werden flüchtige Testcontainer erzeugt; keine vorhandene Datenbank wird verwendet.

Vom Repository-Stamm, in dieser Reihenfolge:

```sh
cd qualification/backend
./mvnw --batch-mode --no-transfer-progress verify
cd ../frontend
npm ci --ignore-scripts
npm run verify
```

Der Java-Test erzeugt synthetische JSON-Dateien unter `backend/target/contract-fixtures`; die Node-Tests verwenden genau diese Dateien. Fehlende Fixtures oder ein fehlender Docker-Daemon sind Fehler, keine übersprungenen grünen Nachweise. Der CI-Workflow [qualification.yml](../.github/workflows/qualification.yml) führt dieselbe Reihenfolge aus, prüft die unveränderte Typgenerierung und archiviert vorübergehend Testberichte und Buildartefakte.

Geprüft werden:

- Erlaubte Modulverträge sowie absichtlich unerlaubter interner Zugriff und ein Abhängigkeitszyklus.
- Flyway-Migration gegen echtes PostgreSQL, JPA und JDBC in derselben Transaktion, erfolgreicher Commit sowie Rückabwicklung bei Java- und Datenbankfehlern.
- Moduloperationen schreiben nur innerhalb einer bestehenden Transaktion; ein Aufruf ohne Transaktion wird abgewiesen.
- Java-JSON → OpenAPI-Typen → TypeScript-Client: Erfolg, 201/Location, Konflikt/If-Match, Fehler, Unicode, Nullwerte und Netzfehler.
- Statische Negativbeispiele: unzulässige Versionsdatentypen, fehlendes Pflichtfeld und fehlende Versionsbedingung müssen Compilerfehler bleiben.
- React-Build und eine ausdrücklich begrenzte Laufzeitprüfung eines synthetischen Antwortobjekts.

Nicht nachgewiesen sind Produktanmeldung, 24-Stunden-Sitzung, Einmalpasswort, CSRF, Fachrechte, konkurrierender Rechteentzug, echte HTTP-Routen, API-Wiederholungsspeicher, Suche oder Leistungs-/Wiederherstellungsziele. Die Tests ersetzen diese späteren Prüfungen nicht. Referenzhardware und Plattformabdeckung sind noch nicht festgelegt.

Versionen, gemeinsame Vertragsgrundlage und tatsächliche Ergebnisse stehen in [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md). Die Dateien hier werden nicht ungeprüft in die spätere Anwendung kopiert.
