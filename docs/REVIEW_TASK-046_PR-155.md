# Getrennte technische Prüfung TASK-046 / PR #155

**Technische Empfehlung: nicht annehmbar im geprüften Stand.** F1 ermöglicht bei binären Verzeichniskennungen die Anmeldung als anderes Anwendungskonto einschließlich dessen Rechten. Daneben sind F2 bis F4 belegt. Dies ist die technische Empfehlung der getrennten KI nach DEC-040, keine fachliche Abnahme durch oder im Namen von Holger.

## Prüfstand, Auftrag und ausgeführte Befehle

- Datum: **23. Oktober 2026**. Sitzung `codex-20261023-task046-review-01`, eigener Klon, Branch `review/task-046-pr-155`; keine Beteiligung an der Implementierung.
- Gegenstand: [PR #155](https://github.com/1uedev/task-manager/pull/155), Merge **`6e40ebfd88199f4aa517b55294f8389cc377dbdf`**, PR-Head `60f8cc661f2b777396c75f1a9f880caf2cd7cbce`, DEC-075.
- Ausgangs-main und tatsächlich getesteter Checkout: `434592f5b17552e0daff5247f1b06342f59c724b`. Gegenüber dem Merge enthält dieser nur den Claim für TASK-047 in TODO.md. Backend, Frontend und Verträge sind identisch. main vor Berichterstellung erneut abgeglichen, unverändert. Keine Umsetzung von TASK-047 geprüft oder vorweggenommen.
- Gelesen: AGENTS.md, CLAUDE.md, docs/AI_WORKFLOW.md, README.md, TODO.md TASK-046, DONE.md, DEC-075, relevante Anforderungen/Modulverträge, TECHNICAL_BASELINE und LDAP-Dokumentation. TASK-046 ist BEREIT ZUR PRÜFUNG bei `claude-20261023-ldap-01`; keine Übernahme dieser Implementierungsreservierung. Die ausdrücklich beauftragte Prüfung liefert nur Bericht und Belege, keine Produkt-, TODO- oder DONE-Änderungen.

| Selbst ausgeführter Befehl / Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Repository contract OK |
| `python3 scripts/task_board.py --check` | Aufgabenübersicht aktuell |
| `python3 -m unittest discover -s tests -v` | **38 Tests, OK, ein Skip** mangels `time.tzset` unter Windows; 37 ausgeführt |
| Backend: `mvnw -B --no-transfer-progress verify` mit `taskmanager.test.jdbc-url`, `.username`, `.password`, `.server-version=18.6` | **259 Tests, 0 Fehler, 0 übersprungen, BUILD SUCCESS**, 5:25 Minuten; darunter LdapLoginTest 16 und LdapTlsTest 4 |
| Frontend: `npm ci`, `npm run verify` | Vertragsgenerierung, Typprüfung, **75 Tests**, Produktionsbuild erfolgreich; keine generierten Produktänderungen |
| `TASKMANAGER_BROWSER=<Chromium> node --test --test-concurrency=1 tests/browser/*.test.mjs` | **90 Tests / 15 Suiten, alle erfolgreich**, 86,27 Sekunden; Dateien nacheinander |
| Eigene Java-Proben mit unveränderten Produktbeans, echten HTTP-Anfragen, UnboundID und separaten PostgreSQL-Datenbanken | Vier Quellen und Ergebnisdateien im [Belegverzeichnis](review-evidence/task-046-pr-155/verification.md) |
| `git diff --check`, Repositoryvalidierung nach Berichtserstellung | Siehe abschließendes Prüfprotokoll im Belegverzeichnis |

Laufzeit: Temurin **25.0.4.1+1**, Maven 3.9.16, native **PostgreSQL 18.6**, Node 24.22.0, npm 11.19.1, installierter Chrome. Der vollständige Backendlauf, dann Frontendprüfung, dann Browsertests wurden selbst ausgeführt. Der erste Python-Lauf scheiterte beim Zugriff auf lokale Testserver an der Sandbox (`WinError 10013`); der unveränderte erneute Lauf mit lokalem Netzwerkzugriff war erfolgreich. Das ist kein LDAP-Produktfehler.

**Nicht ausgeführt:** OpenLDAP oder Active Directory als echtes Zielprodukt, Docker-/CI-Testcontainer, neuer Container-Lebenszykluslauf, Massentest, vollständiger manueller DE-/EN-Bedienablauf und Zertifikatswechsel ohne Neustart. Gründe: eigener lokaler PostgreSQL-Testweg und synthetische LDAP-Gegenstellen; diese liefern gezielte Protokollnachweise, keine Zielsystemqualifikation. Die bekannten Grenzen werden unten bewertet. Kein Produktfix und kein Rückbau von Produktcode für einen Rot-vor-Grün-Test.

## Befunde

### F1 — hoch: Verschiedene binäre Verzeichniskennungen führen zum selben Konto und dessen Rechten

**Fundstelle:** [DirectoryClient.java:158–176, 194–200](../backend/src/main/java/taskmanager/identity/directory/DirectoryClient.java), Umgebung in Zeilen 230–245; anschließend [DirectoryAccounts.java:43–49](../backend/src/main/java/taskmanager/identity/directory/DirectoryAccounts.java).

Der Client fordert das einstellbare Kennungsattribut an, erklärt es JNDI aber nicht als binär. JNDI hat nur eine begrenzte eingebaute Liste binärer Attribute; `objectGUID` gehört nicht dazu. Ohne `java.naming.ldap.attributes.binary` kommen solche Werte als String zurück. Die spätere `instanceof byte[]`-Hexkodierung greift dann nicht. Die [Oracle-Dokumentation zu binären JNDI-Attributen](https://docs.oracle.com/javase/jndi/tutorial/ldap/misc/attrs.html) beschreibt diese notwendige Typangabe.

**Reproduktion am unveränderten Backend:**

1. Zwei synthetische LDAP-Personen `binary-a` und `binary-b` mit unterschiedlichen Passwörtern und jeweils fester, verschiedener 16-Byte-Kennung im Attribut `objectGUID` anlegen:

   ```text
   A: 80808080808040808080808080808080
   B: 81818181818140818081818181818181
   ```

2. Die Anbindung regulär über `PUT /api/v1/settings/ldap` mit `idAttribute: objectGUID` konfigurieren. A meldet sich erfolgreich an.
3. Ausschließlich dem Anwendungskonto A über das echte Gruppen-API `accounts.manage` geben.
4. B mit **B-eigenem** Namen und Passwort anmelden: **200**, Antwort nennt **`binary-a`**. Ein Anwendungskonto `binary-b` wird nicht angelegt.
5. Mit der so erhaltenen B-Sitzung `GET /api/v1/accounts` aufrufen: **200**. B besitzt damit tatsächlich das A gewährte Verwaltungsrecht.

In der Datenbank ist als gemeinsame Kennung eine Folge von U+FFFD-Ersatzzeichen mit U+0040 gespeichert, keine verlustfreie Hexdarstellung. Die Eindeutigkeit des Datenbankindex schützt nicht vor bereits vorher zusammengefallenen Werten. Auf zwei frischen eigenen Datenbanken reproduziert; endgültiger Beleg mit Codepunkten in [binary-id-results.json](review-evidence/task-046-pr-155/binary-id-results.json), vollständiger Ablauf in [BinaryIdentifierProbe.java](review-evidence/task-046-pr-155/BinaryIdentifierProbe.java). Kein echter AD-Server wurde dafür benötigt oder behauptet.

**Auswirkung:** Kontoverwechslung und Übernahme der Berechtigungen des zuerst zugeordneten Kontos bei betroffener Kennungsdarstellung. Kein lokales Passwort wird dabei erraten und kein Suchfilter manipuliert. Die Abgrenzung „kein echtes AD geprüft“ macht diesen am allgemeinen LDAP-Pfad belegten Fehler nicht zu einer bloßen Vermutung.

**Vorschlag:** Die Darstellung des Kennungsattributs explizit festlegen und vor der JNDI-Dekodierung verlustfrei behandeln, etwa binäre Attribute als solche konfigurieren und kanonisch kodieren. Unterschiedliche Bytefolgen müssen unterschiedliche Zuordnungsschlüssel liefern. Regression mit diesen beiden Personen, verschiedenen Rechten und erfolgreicher Eigenanmeldung ergänzen. Für bereits gespeicherte Kennungen eine kontrollierte Übergangs-/Reparaturstrategie vorsehen: Ein stiller Wechsel der Kodierung kann vorhandene Zuordnungen verlieren; beschädigte Kennungen lassen sich nicht aus Ersatzzeichen zurückrechnen. Bis zur Korrektur keine Anbindung mit binären Kennungsattributen einsetzen.

### F2 — mittel: Namenskollision eines schon bekannten LDAP-Eintrags wird nicht verweigert

**Fundstelle:** [DirectoryAccounts.java:43–52](../backend/src/main/java/taskmanager/identity/directory/DirectoryAccounts.java). Die Namenskollisionsprüfung wird nur im Neuanlagezweig erreicht.

**Ablauf:** A und B melden sich zunächst als `collision-a` und `collision-b` an und erhalten verschiedene Konten. Im Verzeichnis B löschen und A auf `collision-b` umbenennen; das Anwendungskonto B besteht fort. Anmeldung mit `collision-b` und A-eigenem Passwort antwortet **200** mit Anwendungskonto **`collision-a`**. Beleg `known-entry-name-collision` in [ldap-probe-results.json](review-evidence/task-046-pr-155/ldap-probe-results.json).

Damit wird der DEC-075-Fall „anderer Eintrag mit dem Namen eines vorhandenen Kontos wird abgewiesen (401)“ nicht für bereits bekannte Kennungen erfüllt. **Hier wurde keine Übernahme von B nachgewiesen:** die Kennung führt weiterhin zu A. Der Fehler ist die zugelassene widersprüchliche Namensbelegung und deren Anmeldeverhalten; er ist vom tatsächlichen Rechteübergriff in F1 zu unterscheiden.

**Vorschlag:** Auch beim Wiederfinden einer Kennung prüfen, ob der verwendete Anmeldename einem anderen Anwendungskonto gehört; diesen Fall konsistent mit 401 verweigern. Die konfliktfreie Umbenennung desselben Eintrags muss weiter funktionieren. Die Prüfung mit der gleichzeitigen lokalen Kontoanlage abstimmen, damit die lokale Vorrangregel auch bei einer während der LDAP-Anfrage neu belegten Namenszuordnung gilt. Für dieses zusätzliche lokale Rennen liegt hier nur eine Code-Risikoanalyse, kein eigener Ablaufnachweis vor.

### F3 — niedrig: Fehlender, null oder leerer Verschlüsselungswert erzeugt 500

**Fundstelle:** [LdapSettingsService.java:81–82](../backend/src/main/java/taskmanager/identity/directory/LdapSettingsService.java). `blank` liefert null; `List.of(...).contains(null)` wirft eine NullPointerException.

**Ablauf:** Als berechtigte Verwaltung eine ansonsten gültige `PUT /api/v1/settings/ldap`-Anfrage senden, einmal ohne `security`, einmal mit `security: null`, einmal mit `security: " "`. Alle drei antworten **500 `server.internal_error`**, statt 400 `request.invalid` mit `ldap.security.invalid`. Belege `security-missing`, `security-null`, `security-blank` in [ldap-probe-results.json](review-evidence/task-046-pr-155/ldap-probe-results.json).

**Auswirkung:** Vertragswidrige Behandlung ungültiger Eingaben und unnötige Fehlerprotokolle. Kein Rechteumgehen und kein gespeicherter ungültiger Verschlüsselungsmodus nachgewiesen.

**Vorschlag:** null vor der Enum-Mengenprüfung explizit behandeln und die drei Varianten als Eingaberegression aufnehmen; wegen des gemeinsamen Validators auch den Testendpunkt absichern. Der eigene Lauf belegt die drei PUT-Antworten, nicht einen zusätzlichen POST-Test dieser Werte.

### F4 — niedrig: Abgelehntes StartTLS wird als Suchfehler gemeldet

**Fundstelle:** [DirectoryClient.java:249–269, 298–315](../backend/src/main/java/taskmanager/identity/directory/DirectoryClient.java). Der Fehler der Extended Operation fällt ohne SSLException auf `directory.search_failed` zurück.

**Ablauf:** Ein erreichbarer LDAP-Server ohne StartTLS-Unterstützung, gespeicherte oder mitgesendete Einstellung `security: starttls`, dann `POST /api/v1/settings/ldap/test`. Beobachtet: **200 `{ok:false, reason:"directory.search_failed"}`**. Die beobachtete Gegenstelle erhielt dabei **keine Suchoperation, keine Bindung des Suchkontos und keine Personenbindung**. Beleg `starttls-no-support` in [ldap-probe-results.json](review-evidence/task-046-pr-155/ldap-probe-results.json).

**Auswirkung:** Der benannte erste fehlgeschlagene Schritt ist laut Vertrag/DEC-075 die Verschlüsselung, nicht die Suche. Die falsche Ursache erschwert die Einrichtung. Die sicherheitsrelevante Zusicherung hält dagegen: **kein Rückfall auf Klartext**.

**Vorschlag:** Fehler beim geforderten StartTLS-Aufbau dem festen Grund `directory.tls_failed` zuordnen, auch bei protokollseitiger Ablehnung der Operation; weiterhin keine rohe LDAP-Meldung ausgeben und keine Bindung fortsetzen. Regression für einen erreichbaren Server ohne StartTLS ergänzen.

## Nicht gefunden

In den folgenden untersuchten Bereichen wurde außer den genannten Befunden kein weiterer Fehler nachgewiesen. „Nicht gefunden“ ist kein Beweis für alle denkbaren Zeitpläne oder Verzeichnisprodukte.

### 1. Lokale Anmeldung und Kontotyp

`SessionService.login` (Zeilen 95–111) wählt ein lokales Konto vor dem LDAP-Pfad aus. Auch falsches Passwort oder Deaktivierung eines gefundenen lokalen Kontos führen nicht zum LDAP-Fallback. Der selbst ausgeführte `LdapLoginTest.aLocalAccountOfTheSameNameDecidesAloneAndNeverBecomesADirectoryAccount` belegt lokales Einmalpasswort versus fremdes Verzeichnispasswort. Die Umsetzung ändert den Kontotyp eines bestehenden lokalen Kontos nicht.

Eine eigene zusätzliche Fixture gab einem LDAP-Konto absichtlich einen **bekannten** lokalen Hash und schaltete LDAP ab: Anmeldung mit diesem Passwort trotzdem **401**; nach Einschalten des Verzeichnisses dessen Passwort wieder **200** (`auth-kind-gate`). Das beweist die Kontotyp-Schranke zusätzlich zur Unwahrscheinlichkeit, das zufällige Ausgangsgeheimnis zu kennen. Es ist eine direkte synthetische Datenbankfixture, kein erreichbarer Produktweg zur Änderung dieses Hashs.

### 2. Zuordnung, Erstanmeldung und Sperrfolge

Textuelle `entryUUID`-Zuordnung bei normaler Umbenennung, Verweigerung eines **neuen** Eintrags mit altem belegtem Namen und deaktivierte Konten sind in den selbst ausgeführten Produkttests belegt. Zu den nicht abgedeckten Varianten siehe F1/F2.

Die eigene Gegenprobe hielt eine erste Zuordnung noch uncommitted und ließ vier reale HTTP-Erstanmeldungen am gleichen eindeutigen INSERT warten. **Vier INSERT-Wartepositionen wurden in PostgreSQL beobachtet**, dann der erste Commit freigegeben: viermal 200, insgesamt ein Konto (`forced-first-login-race`). Somit wird die DuplicateKeyException-Wiederholung tatsächlich erzwungen; die bloße Gleichzeitigkeit des Produkttests würde das für sich allein nicht beweisen.

`startDirectorySession` hält die Einstellungszeile geteilt **vor** `assign` und der Kontosperre, `startSession` liest dieselbe Einstellung erneut unter derselben Transaktion. Eigene Warteprobe: Login wartet sichtbar an der Kontosperre, parallele Verkürzung der Sitzungsdauer wartet sichtbar an der Einstellungszeile; nach Freigabe beide 200 und alle offenen Zielsitzungen höchstens eine Stunde (`duration-race`). Zweite Probe: Deaktivierung noch uncommitted, Login wartet sichtbar am Konto; nach deren Commit Login 401 und null offene Sitzungen (`deactivation-race`). Keine Verklemmung in diesen Abläufen.

### 3. Suchfilter, Basis, Passwörter und Ergebnisgrenzen

Der Name wird ausschließlich als JNDI-Filterargument übergeben, nicht in Filter oder Basis konkatenert. Eigene Mitschrift der tatsächlich empfangenen Filter zeigt Maskierung von `*`, Klammern, Backslash und NUL. Ein normaler Eintrag mit Passwort einschließlich Leerzeichen, Umlaut, Backslash und Sonderzeichen bleibt anmeldbar. Leeres Passwort und Länge 1025 verursachen **weder Suche noch Personenbindung**, Länge 1024 wurde erfolgreich verwendet. Die optionale **anonyme Suche ohne Suchkonto** ist davon getrennt und in DEC-075 ausdrücklich erlaubt; eine leere Personenbindung wurde nicht beobachtet. Das entspricht der sicherheitsrelevanten Trennung aus [RFC 4513, Abschnitt 5.1](https://www.rfc-editor.org/rfc/rfc4513#section-5.1).

Die Basis `ou=a/b,dc=review,dc=test` kam unverändert als **eine** LDAP-Basis an und funktionierte. `new CompositeName().add(base)` ist hier richtig: Es verhindert, dass JNDI den Slash als zusammengesetzten Namenspfad aufteilt. Die konfigurierte Basis wird als DN validiert; der Anmeldename verändert sie nicht.

Eigene beobachtete Suchsteuerung: Aliasauflösung **NEVER**, Größenlimit **3**, Zeitlimit auf dem Draht **3 Sekunden** bei 2 Sekunden JNDI-Timeout (Provider-Rundung). Alias auf einen gültigen Benutzer authentifiziert nicht dessen Ziel. Erzwungener Referral wurde mit festem Fehler verweigert; die zweite Gegenstelle erhielt **null Bind-/Suchanfragen**. Teilergebnis mit anschließendem Größenlimit wurde nicht als einzelner gültiger Benutzer akzeptiert. Zeitlimit und tatsächlicher Netzwerk-Leseablauf werden fail closed behandelt. Mehrere Treffer verweigern die Produkttests. Grenzen sind pro Operation/Antwort, kein nachgewiesenes absolutes Gesamtbudget aller Netzwerkphasen.

`validFilter` prüft Platzhalter und Klammerstruktur, nicht die vollständige LDAP-Filtergrammatik. Das schützt nicht vor einer administrativ falsch formulierten Fachsuche; Fehler müssen beim Verbindungstest auffallen. Eine Eingabe-Einschleusung durch einen Login wurde nicht gefunden. Einige syntaktisch fragwürdige Escapeformen werden vom verwendeten JNDI toleriert; daraus wird ohne nachgewiesene Sicherheitswirkung kein zusätzlicher Befund abgeleitet.

### 4. TLS und optionaler Vertrauensspeicher

`DirectorySocketFactory` setzt die Endpunktprüfung für LDAPS und die StartTLS-Schicht. Zusätzlich zu den ausgeführten Produkttests verwendete [TransportProbes.java](review-evidence/task-046-pr-155/TransportProbes.java) einen eigenen synthetischen Schlüssel und TLS-Server: vertrauenswürdiges Zertifikat für `localhost` → Anmeldung über beide Wege erfolgreich; derselbe Server als `127.0.0.1` → zweimal `directory.tls_failed`, **keine Suchkonto-/Personenbindung**. Ohne Vertrauen in dessen CA ebenfalls zweimal `directory.tls_failed`. Fehlende Datei, falsches Speicherpasswort und unbekannter Speichertyp scheitern geschlossen; kein schwächerer Ersatzspeicher. Belege in [transport-results.json](review-evidence/task-046-pr-155/transport-results.json).

Der statische volatile Halter ist für **eine beim Start erzeugte Clientinstanz, ein Verzeichnis und einen Anwendungskontext pro JVM** vertretbar. Mehrere parallel betriebene Spring-Kontexte oder spätere Hot-Reload-/Mehrverzeichnisfunktionen könnten sich dadurch die LDAPS-Fabrik überschreiben. Das wird nicht als aktuelle Mehrverzeichnisfunktion oder zugesagter Zertifikatswechsel bewertet. Ohne eigenen Store gilt Java-Vertrauen; ein angegebener Store wird nicht still ignoriert. Explizit konfiguriertes `none` ist die bestätigte unverschlüsselte Betriebswahl samt UI-Hinweis, kein versteckter TLS-Fallback.

### 5. Geheimnisse und Fehlerwortschatz

Das Suchkontopasswort wird über `LdapProperties` aus der Betriebsumgebung gelesen, nicht über das Einstellungs-DTO. Der Personenpasswortwert geht an die Bindung; das neu angelegte Anwendungskonto erhält einen Hash aus Zufallsdaten. Der selbst ausgeführte Produkttest prüft Datenbanktabellen und CapturedOutput auf Suchgeheimnis, richtiges und falsches Personenpasswort. Eigene Zusatzprobe ließ die Gegenstelle ihr synthetisches Suchgeheimnis in einen LDAP-Diagnosetext schreiben: Antwort nur `directory.search_failed`, kein Geheimnis; auch im lokalen Anwendungslog keines der gesuchten Testgeheimnisse gefunden. `LdapProperties.toString` verbirgt Bindungs- und Speicherpasswort, zusätzlich selbst geprüft.

Die oberste `DirectoryUnavailableException` trägt nur einen festen Grund, behält aber intern die rohe Ursache in der Exception-Kette. Die untersuchten Aufrufer geben/loggen nur `reason()` und nicht diese Ursache. Eine spätere ungefilterte Ausgabe der ganzen Exception wäre deshalb gesondert zu prüfen; ein aktueller Geheimnisabfluss dadurch wurde **nicht** belegt. Die feste Wortwahl hält, die fachlich falsche Zuordnung eines StartTLS-Fehlers steht in F4.

### 6. Ausfall, Ressourcen und laufende Sitzungen

Eigene reale Probe: während einer angehaltenen LDAP-Suche **null aktive Verbindungen im Anwendungspool**; eine lokale Anmeldung gelingt parallel mit 200; die wartende LDAP-Anmeldung endet nach dem konfigurierten Lesezeitlimit mit **503 `auth.directory_unavailable`**. Der selbst ausgeführte Produkttest wiederholt einen Ausfall achtmal ohne neue Anmeldesperre und prüft eine vorherige LDAP-Sitzung sowie lokale Anmeldung. Die Aussage „keine Wartezeit“ bedeutet hier **keine zusätzliche Fehlversuchs-Wartezeit**, nicht verzögerungsfreie Netzwerkausfallerkennung.

Such- und Personenverbindungen werden durch try-with-resources geschlossen; Suchenumerationen im finally, die TLS-Schicht und der Kontext bei fehlgeschlagenem Upgrade ausdrücklich. Bei einem bereits im JNDI-Konstruktor fehlgeschlagenen Kontext liegt die Bereinigung beim Provider; kein eigener Langzeit-Lecktest durchgeführt. Containerbefehle authentifizieren nicht am Verzeichnis. Ein ungültiger ausdrücklich gesetzter Vertrauensspeicher verhindert absichtlich den Anwendungsstart, also auch lokale Anmeldung; das ist ein Betriebs-Konfigurationsfehler und nicht der zugesicherte Ausfall einer ansonsten korrekt konfigurierten Gegenstelle.

### 7. Verwaltungsrechte, Kontoschranken und SSRF

GET und Test verwenden `AdministrationRequests.run` mit maßgeblichem `require(accounts.manage)`. PUT prüft zusätzlich `early`, dann `require` in der Änderungstransaktion. GET/Test haben davor keine geschützte Fachaktion, die einen separaten `early`-Aufruf erfordern würde. Der Test lässt die Autorisierungstransaktion vor dem Netzwerkaufruf enden; damit werden keine Rechte-/Identitätssperren während fremder Netzwerkantworten gehalten. Der Produkttest verweigert alle drei Endpunkte einem vollständigen Konto ohne Recht mit 403.

`requireLocal` schützt Reset, Anzeigenamenänderung und den Notweg; der Passwortwechsel hat eine eigene Kontotypprüfung. Die Produkt-HTTP-Tests liefern 409 `account.directory_managed` für Reset, Anzeigename und Passwortwechsel. Der Notweg wurde im Code bis zum CLI-Aufrufer geprüft, **nicht als eigener Containerprozess ausgeführt**. Deaktivierung beendet Sitzungen, Reaktivierung erlaubt neue Anmeldung; zusätzlich eigene konkurrierende Deaktivierungsprobe, siehe Punkt 2. Keine Umstellung des Kontotyps aus TASK-047 bewertet.

**SSRF-Vertrauensgrenze:** `accounts.manage` darf bewusst einen vom Server erreichbaren Host und Port angeben, auch interne Adressen; keine Host-Allowlist, Privatnetz- oder Loopback-Sperre. Das umfasst Verbindungsversuche und bei entsprechendem LDAP-Server die Bindung mit dem Betriebs-Suchgeheimnis. Diese weitreichende Konfigurationsbefugnis ist in DEC-075 ausdrücklich beschrieben, kein neu entdeckter anonymer Zugriff. Schranken: ausschließlich `ldap`/`ldaps`, keine URL-Zugangsdaten, Pfade, Query oder Fragmente, nur passende Protokoll-/Verschlüsselungskombination; keine Referral-Verfolgung, feste Ergebnisgründe und Zeitlimits. Kein beliebiges HTTP-Auslesen beobachtet. Das Recht muss deshalb als vertrauenswürdige Betriebsverwaltung behandelt werden; für weniger vertrauenswürdige Konten sind Betriebs-Egressregeln beziehungsweise eine künftig enger abgestimmte Zielpolitik sinnvoll.

### 8. Migration und Modulzuordnung

V0021 gehört laut Owner-Kommentar dem Identitätsmodul und verwendet ausschließlich dessen Tabellen. Bestehende Konten werden `local` mit NULL-`external_id`; die Paarprüfung verbietet LDAP ohne Kennung und lokale Konten mit Kennung, die Längenprüfung leere/überlange Kennungen, der Teilindex doppelte Kennungen. Die Einstellung ist auf Zeile 1 begrenzt, initial keine Anbindung. Die fehlende Datenbank-Schemaänderung außerhalb Flyway wurde im vollständigen Konventions-/Modullauf mitgeprüft.

Eigene befüllte Upgradeprobe von V0020 auf V0021: bestehender Hash unverändert, Kontotyp `local`, keine externe Kennung; fünf ungültige Kombinationen mit SQLSTATE 23514, doppelte Kennung 23505, zweite Einstellungszeile 23514. [migration-results.json](review-evidence/task-046-pr-155/migration-results.json). Das ist eine kleine synthetische Alt-Datenprobe, kein Restore-/Großdaten-Migrationsnachweis. F1 entsteht **vor** diesen korrekten Constraints.

### 9. Vertrag und Oberfläche

Routen, neue `authKind`-Werte, 401/403/409/503 und Grundkatalog mit Implementierung verglichen; der vollständige IdentityContractTest war grün. Er vergleicht primär Routen und ist kein vollständiger Antwort-/Statusvalidator. Die bestätigten Abweichungen stehen in F1 bis F4, insbesondere 500 bei ungültiger Einstellung und falscher Diagnose.

LdapPanel bietet keinen Passwort-Eingabewert für das Suchkonto; es zeigt nur den Betriebsstatus, Verschlüsselung und Prüfung. AccountsPage blendet Reset und Anzeigenamenänderung für LDAP aus, lässt Deaktivieren/Reaktivieren und Aufheben der Anmelde-Wartezeit zu. LoginPage übersetzt den Verzeichnisausfall. Der Pflichtwechsel wird nur für die entsprechende Anmeldestufe gezeigt; neue LDAP-Konten besitzen diese nicht. Keine weitere widersprüchliche Freigabe oder versteckte zulässige LDAP-Kontoaktion gefunden. DE-/EN-Schlüssel typgeprüft und Quellkataloge geprüft: keine Gedankenstriche (`–`, `—`), keine fehlende neue Übersetzung gefunden. Die 90 Browserprüfungen verwenden überwiegend API-Fixtures; sie sind **kein durchgängiger realer LDAP-UI-Nachweis**. Eigene reale Gegenproben verwenden HTTP und Produktbeans, nicht die gesamte Oberfläche.

### 10. Aussagekraft der vorhandenen Tests und Lücken

LdapLoginTest führt tatsächliche HTTP-Anmeldungen gegen echtes PostgreSQL und einen LDAP-Protokollserver aus; LdapTlsTest erzeugt eine CA-Vertrauenskonfiguration und prüft sowohl Zertifikatsname als auch Personenanmeldung über beide verschlüsselten Wege. Die beworbenen Kernfälle sind damit mehr als nur Mocks.

Der Sonderzeichentest ist **teilweise tatsächlich sensitiv**: Der Fall `eve*` mit richtigem Passwort trifft ohne Maskierung den einzelnen zuvor angelegten `eve-…`-Benutzer und würde erfolgreich anmelden, also die Empty-Assertion verletzen. Ein bloßes `*` könnte wegen mehrerer Treffer auch ohne Maskierung verweigert werden; dieser einzelne Fall allein wäre kein Nachweis. Eigene Drahtmitschnitte und eine gezielt vorhandene Wildcard-Positivkontrolle ergänzen dies ohne Änderung des Produktcodes. Der Testname „never reaches the filter“ ist ungenau: die Zeichen erreichen den Filter als maskierte Werte.

Der Vierfach-Erstanmeldungstest startet parallele Requests, erzwingt aber selbst keine DuplicateKeyException oder konkrete Warteposition. Das schließt die eigene beobachtete INSERT-Warteprobe. Fehlende Regressionen: **binäre Kennungen mit unterscheidbaren Rechten (F1)**, bereits bekannte Namenskonkurrenten (F2), fehlendes/null/leeres Enum (F3), StartTLS-Ablehnung vor irgendeiner Suche (F4). Eigene zusätzliche Abdeckung: Alias, Referral, Größenlimit nach Teilresultat, Zeitlimit, fehlendes CA-Vertrauen, defekter Store, Sitzungsdauer-/Deaktivierungsrennen und Netzaufruf ohne DB-Verbindung. Nicht untersucht wurden alle LDAP-Rückgabecodes, Schema-/Matching-Regeln aller Zielsysteme, DNS-Ausfallvarianten oder eine vollständige Prüfung aller möglichen parallelen Verwaltungsaktionen.

### 11. Bekannte Grenzen und Einsatzrisiko

Der eigene erfolgreiche Lauf mit **PostgreSQL 18.6** verringert die vom Autor genannte Lücke „lokal nur PostgreSQL 16“. Er ersetzt weiterhin keinen gepinnten CI-Container. Die Einschränkung „kein echtes OpenLDAP/AD“ bleibt bestehen und ist nach F1 praktisch relevant: ein grüner Text-`entryUUID`-Test qualifiziert keinen binären Identifikator oder AD-Betrieb.

Vor Einsatz empfehlen sich nach Fehlerbehebung ein Pilot mit dem tatsächlich vorgesehenen Verzeichnisprodukt und seiner Version: unveränderliche Kennung einschließlich Rohbytes, zwei verschiedene Personen mit verschiedenen Rechten, Umbenennung/Namenswiedervergabe, Bind-/Such-ACLs, tatsächlich verwendeter Filter und Basis, CA-/Namensprüfung beider freigegebenen TLS-Wege, nicht gefolgte Referrals/Aliasse, Timeouts und Ausfall bei weiter funktionierendem lokalen Notzugang. Hohe gleichzeitige Anmeldezahlen gesondert messen; die Hashbildung der Erstanlage und begrenzte Verbindungskapazität sind kein gemessener Skalierungsnachweis. Die lokale Installation braucht weiterhin eine betrieblich belastbare lokale Wiederherstellungsmöglichkeit; die neue Entscheidung dazu aus TASK-047 wird hier ausdrücklich nicht bewertet.

## Empfehlung an Holger

Von den vorgegebenen drei Stufen lautet die technische Empfehlung **nicht annehmbar im geprüften Stand**: F1 verletzt die zentrale Identitäts- und Rechtegrenze; die 259/75/90 erfolgreichen Tests widerlegen diesen eigenen Ablauf nicht. F1 mit verlustfreier Kennungsdarstellung und gesicherter Alt-Zuordnung korrigieren, F2/F3 als Regressionen ergänzen und F4 diagnostisch berichtigen. Danach eine gezielte getrennte Nachprüfung, insbesondere mit zwei binären Kennungen und voneinander getrennten Rechten, sowie die Zielverzeichnis-Qualifikation durchführen. Die fachliche Abnahme und deren Zeitpunkt entscheidet ausschließlich Holger.
