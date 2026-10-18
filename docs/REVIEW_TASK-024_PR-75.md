# Sicherheitsgegenprüfung TASK-024 / PR #75 nach DEC-040

18. Oktober 2026 · Prüfer: Codex, `codex-20261018-identity-security-01`.
Umsetzung: Claude, `claude-20261018-identity-01`.
Prüfgegenstand: [PR #75](https://github.com/1uedev/task-manager/pull/75), lokale Konten,
Anmeldung, Pflichtwechsel, feste Sitzungsfristen und zugehörige Sicherheitsgrenzen.
Dies ist die von Holger beauftragte gegenseitige technische Prüfung, keine fachliche Abnahme.
TASK-024 wird nicht übernommen; Status, Zuständigkeit, TODO.md und DONE.md bleiben unverändert.

**Ergebnis: Nachbesserung erforderlich.** Zwei hohe und drei mittlere Befunde sind durch
eigene Gegenproben reproduziert. Die 73 bestehenden Backendtests bestehen trotzdem.
Ein erfolgreicher Build ist daher hier kein Nachweis der betroffenen Sicherheitsgrenzen.

## Geprüfter Stand und Abgrenzung

- PR-Basis: `d60c5548ab9f2b5d599ca57b370a323b7f660aaa`.
- PR-Head: `d271b8e1b38bb73b1e6a27b97be28a90b4fa01a3`.
- Tatsächlich lokal ausgeführt: integrierter Produktstand
  `508f0df5fcf47ab67969344f5362573e578cf441`.
- Backend, Verträge und Backendworkflow sind zwischen PR-Head und diesem integrierten Stand
  identisch (eigener Git-Vergleich). Der später übernommene Stand `54245fd` ergänzt lediglich
  Claudes Bericht zu TASK-025, keinen Produktcode.
- Gelesen: AGENTS.md, README.md, TODO.md, DONE.md, AI_WORKFLOW.md, CLAUDE.md,
  relevante Anforderungen, DEC-040/DEC-052/DEC-053/DEC-058, Rechte-/Architektur-/Vertragsentwürfe,
  TECHNICAL_BASELINE.md, Backenddokumentation, Identitätsquellen und -tests, Migration V0003,
  Produkt-OpenAPI, gemeinsame Fehlerbehandlung und CI-/Lifecycleweg.

Maßgeblich sind REQ-AUTH-001, REQ-AUTH-003, REQ-AUTH-005 und REQ-SEC-001 sowie die zehn
bestätigten Beschlüsse aus DEC-058. Weitergehende Entwürfe werden nicht pauschal als
angenommene Anforderungen behandelt. Mein eigenes Berechtigungsmodul aus TASK-025 ist
kein Gegenstand einer behaupteten unabhängigen Abnahme durch mich.

## Befunde

| ID | Schwere | Befund | Fundstelle |
|---|---|---|---|
| R1 | hoch | Laufender Login mit altem Passwort erzeugt nach verbindlichem Reset eine volle Sitzung | `SessionService.java:71–104`, insbesondere 81 und 93–94 |
| R2 | hoch | Nach Verkürzung abgelaufene Sitzung wird durch spätere Verlängerung wieder gültig | `SessionService.java:135–136`, `SessionDuration.java:38–47` |
| R3 | mittel | Einmalpasswort ist vor dem Pflichtwechsel mehrfach zur Anmeldung verwendbar | `SessionService.java:89–101`, `IdentityApiTest.java:323–325` |
| R4 | mittel | URL-Varianten umgehen den vorgeschalteten API-/CSRF-Filter | `AuthenticationFilter.java:48–52` |
| R5 | mittel | Wartezeit je Quelladresse beginnt nach 20 statt den bestätigten fünf Fehlversuchen | `IdentityProperties.java:23`, `LoginThrottle.java:recordFailure` |

### R1 – altes Passwort bleibt im konkurrierenden Login wirksam

**Ursache:** [login](../backend/src/main/java/taskmanager/identity/session/SessionService.java#L71)
liest Konto und Hash und prüft das Passwort außerhalb der späteren Sitzungstransaktion.
Nach `throttle.recordSuccess` beginnt `startSession` mit dem vorher gelesenen `AccountRecord`.
Zwischenzeitliche Passwortänderung, Zurücksetzung und der neue Pflichtwechselzustand werden
nicht nochmals geprüft. `resolve` prüft den Pflichtwechselzustand des Kontos ebenfalls nicht
gegen eine so neu angelegte volle Sitzung.

**Reproduktion:** Ein Konto hat ein gewähltes Passwort und eine volle Sitzung. Der Test hält
einen zweiten Login nach erfolgreicher Passwortprüfung am Löschzugriff auf dessen
Fehlversuchzeile an. `pg_blocking_pids` bestätigt den tatsächlichen Wartezustand. Dann führt
ein Administrator den Passwortreset aus und committet. Die alte Sitzung ist nun nachweislich
widerrufen. Nach Freigabe des wartenden Logins legt dieser trotzdem eine neue Sitzung an:
`resolve` meldet `FULL`, obwohl das Konto in der Datenbank `password_change_required=true`
hat. Es wurde kein neues Einmalpasswort vorgelegt.

**Auswirkung:** Der Passwortreset unterbricht einen bereits laufenden Zugriff mit dem alten
Passwort nicht zuverlässig; ein kompromittierter Zugang kann den Reset überleben. Das ist
ein Fehler innerhalb des bereits gelieferten Identitätsmoduls, nicht nur die bekannte
fehlende Anbindung an TASK-025.

**Vorschlag:** Konto-/Zugangsversion und aktuellen Zustand bei der verbindlichen
Sitzungsanlage in derselben geeigneten Sperr-/Transaktionsordnung erneut prüfen. Reset,
Passwortwechsel, Deaktivierung und Login müssen diese Ordnung gemeinsam einhalten. Bei
verändertem Hash beziehungsweise Zustand keine Sitzung aus dem alten Nachweis anlegen.
Den deterministischen Ablauf als negative Konkurrenzprüfung aufnehmen.

### R2 – einmal verkürzte Frist wird wieder verlängert

**Ursache:** [resolve](../backend/src/main/java/taskmanager/identity/session/SessionService.java#L135)
verwendet jeweils das Minimum aus ursprünglich gespeichertem Ablauf und Start plus
**aktuellem** Stundenwert. [change](../backend/src/main/java/taskmanager/identity/session/SessionDuration.java#L38)
persistiert eine Verkürzung nicht in den betroffenen Sitzungen. Die bereits wirksame frühere
Obergrenze geht bei der nächsten Einstellungsänderung verloren.

**Reproduktion:** Anmeldung bei 24 Stunden, dann Einstellung auf eine Stunde verkürzen.
Die nächste Auflösung zeigt korrekt eine Stunde. Nach mehr als zwei Stunden ist dasselbe
Token ungültig. Anschließend die Einstellung wieder auf 24 Stunden setzen: **dasselbe Token**
wird wieder als `FULL` akzeptiert. Auch ohne zwischenzeitlichen Ablauf verlängert die zweite
Änderung die zuvor wirksame Frist einer bestehenden Sitzung.

**Auswirkung:** Ein abgelaufenes, etwa zuvor kopiertes Sitzungstoken kann wieder zugreifen.
Das verletzt die bestätigte Regel aus DEC-058, dass Verlängerungen ausschließlich neue
Anmeldungen betreffen. Das Löschen eines Browsercookies verhindert keine Wiederverwendung
einer bereits kopierten Tokenfassung.

**Vorschlag:** Die pro Sitzung wirksame Ablaufgrenze monoton begrenzen, beispielsweise bei
Verkürzung unter abgestimmten Sperren mit `LEAST` dauerhaft herabsetzen. Auch nie zwischenzeitlich
abgefragte Sitzungen und konkurrierende Anmeldung/Passwortrotation berücksichtigen.
24 → 1 → 24 sowie mehrfaches Verkürzen/Verlängern vor und nach Ablauf negativ prüfen.

### R3 – Einmalpasswort wird erst beim Wechsel verbraucht

**Ursache:** [login](../backend/src/main/java/taskmanager/identity/session/SessionService.java#L89)
prüft nur die 72-Stunden-Frist. Eine erfolgreiche Anmeldung markiert den Zugangsnachweis nicht
als verbraucht. Die bestehenden Tests erwarten sogar ausdrücklich eine erneute Anmeldung nach
Ablauf der eingeschränkten Sitzung; sie prüfen einen anderen Verbrauchszeitpunkt als DEC-058.

**Reproduktion:** Dasselbe ausgegebene Einmalpasswort öffnet zwei verschiedene eingeschränkte
Sitzungen. Nach 31 Minuten ist deren erste Sitzung abgelaufen; dasselbe Einmalpasswort öffnet
ohne administrative Neuausgabe eine dritte eingeschränkte Sitzung.

**Auswirkung:** Ein bereits verwendeter Zugangsnachweis bleibt bis zum Passwortwechsel
wiederverwendbar. Bestätigt ist jedoch genau **eine erfolgreiche Anmeldung**, nicht lediglich
ein erfolgreicher Passwortwechsel. Die eingeschränkten Rechte verhindern hier weiterhin
regulären Fachzugriff; eine volle Sitzung allein durch die Wiederholung wurde nicht behauptet.

**Vorschlag:** Den Anmeldeverbrauch atomar bei der ersten erfolgreichen Anmeldung festhalten,
einschließlich paralleler Anmeldungen. Den legitimen Pflichtwechsel innerhalb dieser Sitzung
gesondert ermöglichen. Nach deren Ablauf administrative Neuausgabe entsprechend dem bestätigten
Kontenweg; eine davon abweichende Wiederaufnahme erfordert eine ausdrückliche neue Entscheidung.

### R4 – rohe URI und tatsächlich gerouteter Pfad stimmen nicht überein

**Ursache:** [AuthenticationFilter](../backend/src/main/java/taskmanager/identity/web/AuthenticationFilter.java#L48)
entscheidet anhand des rohen `getRequestURI()` und eines Zeichenkettenpräfixes. Tomcat/Spring
routen gültige Varianten mit Pfadparametern oder kodierten Zeichen zur selben API-Ressource.
Der Filter überspringt sie, bevor Sitzung, Pflichtwechsel, CSRF oder `no-store` geprüft/gesetzt werden.

**Reproduktion mit echtem HTTP und unverändertem Produktfilter:** Ein ausschließlich im Test
registrierter Handler `/api/v1/review-protected` ist kanonisch ohne Sitzung mit 401 gesperrt.
`/api;review/v1/review-protected` und `/%61pi/v1/review-protected` erreichen ihn dagegen anonym
mit 200; `Cache-Control: no-store` fehlt. Am **vorhandenen** Login-Endpunkt liefert
`POST /api;review/v1/session` mit gültigen Testzugangsdaten trotz fehlendem CSRF-Cookie/-Header
und fremdem `Origin` ebenfalls 200.

**Auswirkung und Grenze:** Die versprochene Standardverweigerung gilt nicht für dieselben
Ressourcen unter allen vom Server akzeptierten Schreibweisen. Die aktuellen
Administrationshandler prüfen zusätzlich das gesetzte Sitzungsattribut und geben dadurch
nicht einfach anonym Kontodaten frei; eine solche Offenlegung wurde nicht reproduziert.
Der Testhandler enthält keine echten Fachinhalte. Der Login-Gegenfall ist ein HTTP-Nachweis,
kein vollständig demonstrierter browserübergreifender CSRF-Angriff; JSON/CORS und Cookieverhalten
eines Browsers wurden dafür nicht getestet. Vor späteren Fachendpunkten muss die zentrale
Schutzgrenze trotzdem geschlossen sein.

**Vorschlag:** Sicherheitsentscheidung und Routing auf dieselbe kanonische, kontextbezogene
Pfaddarstellung stützen oder mehrdeutige Schreibweisen zentral ablehnen. Keine eigene
unvollständige URL-Dekodierung einführen. Kodierung, Matrixparameter, Kontextpfad und
abweichende Dispatcherwege mit echten HTTP-Gegenfällen prüfen.

### R5 – abweichender Standard für die Quelladresse

**Ursache:** [IdentityProperties](../backend/src/main/java/taskmanager/identity/config/IdentityProperties.java#L23)
setzt den Adressschwellwert auf 20. README dokumentiert das korrekt als Implementierungsstand,
aber DEC-058 bestätigt fünf Fehlversuche **je Konto und Quelladresse**. Der Produkt-OpenAPI-Text
nennt ebenfalls fünf. Eine bestätigte Ausnahme für 20 ist im geprüften Stand nicht verzeichnet.

**Reproduktion:** Sechs Fehlversuche mit sechs verschiedenen unbekannten Namen von derselben
synthetischen Quelladresse liefern alle 401. Der Adresszähler enthält sechs Fehler, aber noch
keine Wartezeit/429. Die kontobezogene Schwelle wurde durch die wechselnden Namen nicht erreicht.

**Vorschlag:** Standard der bestätigten Schwelle anpassen und kontenübergreifende Negativfälle
mit unveränderten Produktionsstandards prüfen. Falls wegen Proxy-/NAT-Nutzung ein anderer Wert
gewollt ist, muss die Koordination diesen ausdrücklich entscheiden und die Quellen nachführen.

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis | Aussagegrenze |
|---|---|---|
| Unverändertes `mvnw verify` vor den Gegenproben | 73 Tests, 0 Fehler/Fehlschläge/übersprungen, Build erfolgreich | Eigener Windows-Lauf, Temurin 25.0.4.1+1, Maven 3.9.16, native PostgreSQL 18.6 über den vorgesehenen externen Testdatenbankparameter; kein lokaler Docker-/Digestnachweis |
| Fünf eigene Gegenproben | Alle fünf beschriebenen Fehler reproduziert; 5 Prüfmethoden ohne Ausführungsfehler | Die Assertionen erwarten bewusst den beobachteten fehlerhaften Zustand. Ihr grüner Lauf ist **keine Sicherheitsfreigabe** |
| Ursprünglicher Backend-CI-Lauf [39146584613](https://github.com/1uedev/task-manager/actions/runs/39146584613) auf `8b000f1` | Vollständiges Protokoll gelesen: 73 erfolgreiche Tests, PostgreSQL 18.6, Build und Lifecycle erfolgreich | Gelesener Nachweis des Autors, nicht mein lokaler SIGTERM-/Containerlauf |
| Quell-/Diffprüfung | PR-Head und integrierter Produktstand identisch in Backend, Verträgen und Backendworkflow | Keine unveröffentlichte Korrektur angenommen |
| Repository-Validator, Aufgabenübersicht, Regressionstests, Diffprüfung | Erfolgreich, 25 Repositorytests | Dokument-/Metadatenprüfung, keine Sicherheitsabnahme |

Die lokale Datenbank wurde neu und isoliert unter einem eigenen Testport gestartet, nur auf
Loopback, ohne Dienstinstallation und mit synthetischen Konten. Native Laufzeit aus dem von
[PostgreSQL verlinkten Windows-Angebot](https://www.postgresql.org/download/windows/) und dessen
[EDB-Binärarchiv](https://www.enterprisedb.com/download-postgresql-binaries), Version 18.6.
Der SHA-256-Fingerabdruck des bezogenen Archivs ist
`e2246ba91d22345bc3d017586c09ede52d9df180b1eeb480f050445f1cad84e2`;
kein unabhängiger veröffentlichter Vergleichswert wird dafür behauptet.

Der [vollständige Quelltext der Gegenproben](review-evidence/IdentitySecurityProbeTest.java)
und die [gekürzten tatsächlichen Beobachtungen](review-evidence/TASK-024-observations.txt)
liegen bei. Die Testklasse wurde nur vorübergehend in den Backendtestpfad kopiert und danach
entfernt. Produktquellen, Originaltests und PR #75 wurden nicht verändert. Die Klasse unter
`docs/` wird vom normalen Backendbuild nicht ausgeführt.

Beispiel zur Wiederholung in einem eigenen Checkout mit einer leeren Testdatenbank und
einem Testbenutzer, der Datenbanken anlegen darf (Variablen lokal setzen):

```sh
cp docs/review-evidence/IdentitySecurityProbeTest.java backend/src/test/java/taskmanager/identity/
cd backend
./mvnw --batch-mode --no-transfer-progress -Dtest=IdentitySecurityProbeTest \
  -Dtaskmanager.test.jdbc-url="$REVIEW_DB_URL" \
  -Dtaskmanager.test.username="$REVIEW_DB_USER" \
  -Dtaskmanager.test.password="$REVIEW_DB_PASSWORD" test
```

Anschließend nur die hinzugefügte Kopie aus `backend/src/test/java/taskmanager/identity/`
entfernen und vor einem regulären Gesamtlauf `clean` verwenden, damit keine kompilierten
Testhandler aus der Gegenprobe zurückbleiben. Nach Korrektur die Proben in echte negative
Regressionstests umarbeiten; keine Assertion auf das fehlerhafte Verhalten als Soll übernehmen.

## Grenzen und Übergabe

Die bekannte fehlende Identitäts-/Berechtigungsanbindung wird nicht als neu entdeckter Fehler
gezählt. Sie bleibt vor Integration echter Fachschreibwege samt Mandanten-/Organisationskontext
und bis Commit gehaltener Sperren verbindlich zu klären. Bootstrap, Hash-/Tokenaufbau,
Cookieattribute, Fehlerantworten und Audit wurden gelesen und durch die vorhandenen lokalen
Tests mitgeprüft; daraus folgt keine Vollständigkeits- oder allgemeine Sicherheitszusage.

Nicht selbst ausgeführt: vollständiger Browser-CSRF-Angriff, Last-/Ressourcenmessung,
Abhängigkeitsschwachstellenscan, lokales Bash-Lifecycle-Skript mit SIGTERM sowie echte
LDAP-/Portal-/Fachmoduladapter. LDAP, Portal, 2FA und Anmeldeseite sind nicht Teil dieses Auftrags.

Die [OWASP-Sitzungshinweise](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
stützen die technische Notwendigkeit serverseitigen Ablaufs und Widerrufs; maßgeblicher
Produktsollwert für diese Befunde bleibt DEC-058, ohne ein neues Inaktivitätslimit einzuführen.

**Nächster Schritt:** Koordination weist die Korrekturen R1–R5 der umsetzenden Sitzung zu.
Danach gezielte Gegenprüfung am benannten Korrekturcommit, anschließend Holgers Entscheidung
über Annahme. Keine fachliche Abnahme, keine selbstständige Status-/DONE-Verschiebung und
keine eigenmächtige Aufhebung der Blockierungen von TASK-026 bis TASK-031.
