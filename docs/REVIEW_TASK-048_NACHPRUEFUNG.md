# Getrennte technische Nachprüfung TASK-048 / PR #160

**Empfehlung an Holger: nicht annehmbar im geprüften Stand.** Die vier ursprünglichen Regressionen sind korrigiert. F1 ist insgesamt aber nur **teilweise behoben**: Beim Upgrade bleiben Altschlüssel bestehen, die mit der neuen Kodierung eines anderen Verzeichniseintrags kollidieren. N1 belegt den Zugriff auf ein fremdes Konto einschließlich `accounts.manage`. N2 ist eine kleinere Regression beim Anzeigenamen. Die fachliche Abnahme bleibt ausschließlich bei Holger.

## Prüfstand und ausgeführte Prüfungen

- Datum: **24. Oktober 2026**, Sitzung `codex-20261024-task048-047-review-01`, eigener Klon und Branch `review/task-048-nachpruefung`; keine Mitwirkung an der Produktimplementierung.
- Geprüft und tatsächlich ausgeführt: main **`1274c318de3a2baa995118804b5debc9391e93d3`**, Merge von [PR #160](https://github.com/1uedev/task-manager/pull/160), PR-Head `ff4bb8e525f37143349940b12f130ca5c254c09a`. main vor der Veröffentlichung erneut abgeglichen, unverändert.
- Maßstab: AGENTS.md, CLAUDE.md, docs/AI_WORKFLOW.md, TODO.md TASK-048, DEC-077 und der [erste Bericht zu TASK-046 aus PR #158](https://github.com/1uedev/task-manager/blob/0e4b985725c3ea62771f595f66d214cbe7ab877f/docs/REVIEW_TASK-046_PR-155.md). TASK-048 steht bei `claude-20261024-reviewfix-01` auf BEREIT ZUR PRÜFUNG. Hier wird keine Implementierungsreservierung übernommen.
- Umfang dieses Berichts ist die beauftragte Nachprüfung von TASK-048. Keine Produkt-, TODO- oder DONE-Änderungen; Ergänzungen im Belegverzeichnis sind eigenständige Prüfprogramme, keine eingebauten Produkttests.

| Selbst ausgeführter Befehl / Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Repository contract OK |
| `python3 scripts/task_board.py --check` | Aufgabenübersicht aktuell |
| `python3 -m unittest discover -s tests -v` | **38 Tests, OK, 1 Skip** mangels `time.tzset` unter Windows; 37 ausgeführt |
| Backend: `./mvnw -B --no-transfer-progress verify` mit `-Dtaskmanager.test.jdbc-url`, `.username`, `.password`, `.server-version=18.6` | **272 Tests, 0 Fehler, 0 übersprungen, BUILD SUCCESS**, 4:37 Minuten; darunter LdapLoginTest 25, LdapTlsTest 4 und DirectoryIdentifierTest 4 |
| Frontend: `npm ci`, `npm run verify` | Vertragsgenerierung, Typprüfung, **75 Tests** und Produktionsbuild erfolgreich |
| Zusätzlich: `TASKMANAGER_BROWSER=<Chromium> node --test --test-concurrency=1 tests/browser/*.test.mjs` | **92 Tests / 16 Suiten, alle erfolgreich**, Dateien nacheinander, 76,08 Sekunden |
| Vier unveränderte neue LdapLoginTest-Regressionen, separater JUnit-Starter | **Altklassen aus PR #155: 4 erwartete Fehler; aktueller Stand: 4 erfolgreich**. Kein Produktcode im Checkout ersetzt; nur isolierter Klassenpfad, siehe [Prüfprotokoll](review-evidence/task-048/verification.md) |
| Eigene HTTP-, LDAP-, Kennungs- und Upgradeproben | Originalabläufe F1 bis F4, 32 weitere Validatoreingaben, 65.792 Bytefolgen plus Unicodefälle, echte Flyway-Aktualisierung einer befüllten V0021-Datenbank und die Gegenbeispiele N1/N2; Quellen und Ergebnisse im [Belegverzeichnis](review-evidence/task-048/verification.md) |
| `git diff --check`, erneute Repositoryvalidierung nach Berichtserstellung | Erfolgreich; siehe Prüfprotokoll |

Laufzeit: Temurin **25.0.4.1+1**, Maven 3.9.16, native **PostgreSQL 18.6**, Node 24.22.0 und npm 11.19.1. Die angeforderten Unix-Befehle wurden unter Windows mit dem jeweiligen Windows-Starter und demselben Programm ausgeführt. PostgreSQL lief ausschließlich für eigene synthetische Testdatenbanken. Die Browserprüfungen verwenden überwiegend API-Fixtures und ersetzen keinen durchgängigen Bedienablauf an einem echten Verzeichnis.

**Nicht ausgeführt:** Qualifikation mit OpenLDAP oder Active Directory, Docker-/CI-Testcontainer, ein erfolgreicher neuer Lauf des unveränderten `lifecycle-check.sh`, vollständiger manueller DE-/EN-Bedienablauf, exhaustive Nebenläufigkeitsprüfung oder alle denkbaren Validator-/Unicodeeingaben. Für den zusätzlichen Shell-Lebenszykluslauf war die vorbereitete WSL-Laufzeit nicht mit der eigenen Windows-Testdatenbank verbunden; die Vorprüfung scheiterte vor dem eigentlichen Skript. Er wird nicht als bestanden gewertet. Die genannten Zielprodukte und ein solcher Lebenszykluslauf sind kein Bestandteil des zuletzt beauftragten Ausführungsumfangs; die geforderten Backend-/Frontendprüfungen liefen vollständig.

## F1 Kennung: teilweise behoben

**Fundstellen:** [DirectoryClient.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryClient.java), Zeilen 198–235 und 272; [DirectoryAccounts.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryAccounts.java), Zeile 43. Die ursprüngliche Verwechslung der zwei binären Kennungen ist behoben.

Der eigene HTTP-Ablauf verwendet exakt die beiden Kennungen aus dem ersten Bericht:

```text
A: 80808080808040808080808080808080
B: 81818181818140818081818181818181
```

A meldet sich an und erhält ausschließlich über das Gruppen-API `accounts.manage`. B meldet sich mit eigenem Namen und Passwort an: **200, Benutzer `binary-b`, eigenes Konto**, danach `GET /api/v1/accounts`: **403**. Gespeichert werden zwei verschiedene Schlüssel mit `hex:`. Beleg: `F1-original` in [http-results.json](review-evidence/task-048/http-results.json), Quelle [FollowupProbes.java](review-evidence/task-048/FollowupProbes.java).

Für **neu aus Rohbytes erzeugte Schlüssel** ist die Abbildung schlüssig: strikter UTF-8-Decoder, keine Normalisierung oder Kürzung; zulässiger Text wird unverändert übernommen. Werte mit Steuer-/Ersatzzeichen, ausschließlich Leerraum, ungültigem UTF-8 oder dem reservierten Anfang `hex:` werden vollständig hexkodiert. Damit sind beide Darstellungen jeweils eindeutig und disjunkt. Die eigene endliche Suche über sämtliche Bytefolgen der Längen eins und zwei sowie zusätzliche Unicodefälle fand **keine Kollision**. Das ist ein begrenzter Test; die Aussage über die Konstruktion stützt sich zusätzlich auf die Codeprüfung. Beleg: [identifier-results.json](review-evidence/task-048/identifier-results.json).

Normale gespeicherte Textschlüssel wie `entryUUID` und `Müller-17` behalten ihre Darstellung; `entryUUID` wurde zusätzlich über die echte LDAP-Verbindung geprüft. **Nicht jeder alte Textschlüssel bleibt unverändert:** führender/abschließender Leerraum wurde früher entfernt, heute erhalten; ein bisheriger Textwert `hex:80` wird heute anders dargestellt. Die Koexistenz dieser alten und neuen Schlüssel ist N1.

Falls JNDI entgegen der binären Anforderung einen **String mit U+FFFD** liefert, verweigert `identifier` ihn; der Suchpfad meldet `directory.id_attribute_missing`. Der direkte Ersatzzeichen-String wurde geprüft. Rohbytes, die die UTF-8-Kodierung von U+FFFD enthalten, bleiben dagegen eindeutig als Hex erhalten. Groß-/Kleinschreibungen des Attributnamens lieferten über JNDI weiterhin Rohbytes. Ein isolierter nicht gepaarter UTF-16-Surrogatwert im künstlich erzeugten String wird durch `getBytes` zu `?`; eine Herkunft dieses Werts über den geprüften LDAP-Rohbytepfad wurde **nicht** nachgewiesen. Das ist eine zusätzliche Robustheits-Testlücke, kein belegter weiterer Kontozugriff.

### Migration V0022: technisch lauffähig, Auswahl nicht ausreichend sicher

**Fundstelle:** [V0022__identity_directory_identifier_repair.sql](../backend/src/main/resources/db/migration/V0022__identity_directory_identifier_repair.sql), Zeilen 6–18.

[UpgradeProbe.java](review-evidence/task-048/UpgradeProbe.java) baut mit Flyway zunächst wirklich **V0021** auf, fügt neun synthetische LDAP-Altkonten samt Sitzungen und eine Verwaltungszuordnung ein und migriert anschließend mit Flyway auf V0022. **Eine Migration ausgeführt, keine SQL-/Constraintfehler; ein zweiter Flyway-Aufruf führt null Migrationen aus.** Das ist eine befüllte Upgrade-Datenbank, keine bloße erneute Ausführung des SQL auf bereits aktuellem Schema.

- Schlüssel mit U+FFFD, C0 U+0001, DEL U+007F und C1 U+0085: alle vier Konten anschließend **local, ohne external_id, deaktiviert**; offene Sitzungen beendet und je ein festes Auditereignis `account.directory_identifier_unreliable`, Akteur `migration`, ohne Detailinhalt.
- Unauffälliger UUID-Text, Emoji und Formatzeichen U+200B: Kennung, Kontotyp, Aktivstatus, Änderungszeit und offene Sitzung unverändert. Die Erhaltung solcher Zeichen ist keine zusätzliche Normalisierung.
- Auch `hex:80` und `trim-id` bleiben unverändert. **Diese Auswahl erfasst jedoch nicht alle wegen der alten Darstellung unsicheren Zuordnungen**, siehe N1.
- Wiederherstellung eines abgetrennten Kontos funktioniert über den echten Container-/CLI-Einstieg `reset-admin-password` und den anschließenden HTTP-Pflichtpasswortwechsel. Ein weiteres betroffenes Konto ließ sich per Verwaltung zurücksetzen und reaktivieren und bekam dadurch keine zusätzlichen Gruppen. Beleg: [upgrade-results.json](review-evidence/task-048/upgrade-results.json). Ein Einmalpasswort wird nicht veröffentlicht.

## F2 Namenskollision eines bekannten Eintrags: behoben

**Fundstelle:** [DirectoryAccounts.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryAccounts.java), Zeilen 43–47. Auch im Wiederfindezweig wird jetzt geprüft, ob der Anmeldename zu einem anderen Anwendungskonto gehört.

Eigener Ablauf: A und B anmelden, Verzeichniseintrag B löschen, A auf B umbenennen. Anmeldung mit B-Namen und A-Passwort: **401**. A anschließend auf einen freien Namen umbenennen: **200**, weiterhin das ursprüngliche Anwendungskonto A. Beleg: `F2-original` in [http-results.json](review-evidence/task-048/http-results.json). Die unveränderte neue Autorenregression scheitert mit den Altklassen mit **200 statt 401** und besteht aktuell. Es wird keine vollständige Aussage über sämtliche gleichzeitigen Kontoanlagen aus diesem sequenziellen Nachweis abgeleitet.

## F3 Unvollständige Verschlüsselungseinstellung: behoben

**Fundstelle:** [LdapSettingsService.java](../backend/src/main/java/taskmanager/identity/directory/LdapSettingsService.java), Zeilen 81–83. Die explizite null-Prüfung steht vor `List.of(...).contains`.

Je **fehlendes**, **null** und **leeres/blankes** `security` bei PUT und beim Testendpunkt selbst gesendet: **sechs Mal 400**, jeweils `ldap.security.invalid`. Zusätzlich acht Eingabefelder mit je fehlendem, null, überlangem oder falsch typisiertem Wert über PUT geprüft: **32 Fälle, kein 500**, ausschließlich 200 oder 400 entsprechend der Eingabe. Fehlendes optionales Feld darf dabei gültig sein. Beleg: `F3-original` und `validator-matrix` in [http-results.json](review-evidence/task-048/http-results.json). Das ist keine vollständige Fuzzing-Abdeckung. Die Altklassen-Regressionsprobe erzeugt weiterhin den erwarteten **500 statt 400**, der aktuelle Stand besteht.

## F4 Ablehnung von StartTLS: behoben

**Fundstelle:** [DirectoryClient.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryClient.java), Zeilen 286–300 und 342–359. Die protokollseitige Ablehnung der StartTLS-Operation wird jetzt am Aufbau der Verschlüsselung auf `directory.tls_failed` abgebildet. Andere schon spezifisch erkannte Ursachen werden nicht pauschal überschrieben.

Der eigene erreichbare LDAP-Server ohne StartTLS liefert beim HTTP-Test **200 `{ok:false, reason:"directory.tls_failed"}`**. Ein zusätzlicher LDAP-Interceptor zählt nach dem Versuch **null Suchoperationen und null Bindungen mit Kontonamen**. Es wird weder mit dem Suchkonto noch als Person weitergebunden. Ein Verbindungsfehler vor dem TLS-Aufbau bleibt **`directory.unreachable`**, ein abgewiesenes Suchkonto **`directory.bind_failed`**. Belege: [http-results.json](review-evidence/task-048/http-results.json) und [wire-results.json](review-evidence/task-048/wire-results.json).

Die selbst ausgeführten vier LdapTlsTest-Tests prüfen außerdem vertrauenswürdiges LDAPS/StartTLS, falschen Zertifikatsnamen, verwechselte verschlüsselte/unverschlüsselte Ports und Personenanmeldung über beide verschlüsselten Wege. Der vorhandene `theConnectionCheckNamesTheFailingStep` prüft weiterhin die falsche Suchbasis als `directory.search_failed`. Die vierfache Rot-/Grünprobe belegt für F4 **search_failed statt tls_failed** bei Altklassen und den korrekten aktuellen Grund. Ein allgemeiner Klartext-Rückfall wurde nicht gefunden.

## Neue Beobachtungen

### N1 — hoch: Alte und neue Schlüssel können auf das Konto einer anderen Person führen

**Fundstellen:** [DirectoryClient.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryClient.java), Zeilen 198–233; [V0022__identity_directory_identifier_repair.sql](../backend/src/main/resources/db/migration/V0022__identity_directory_identifier_repair.sql), Zeilen 7–9; [DirectoryAccounts.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryAccounts.java), Zeilen 43–52.

**Belegter Fehler, keine Vermutung:** Die beiden neuen Kodierungsformen überschneiden sich untereinander nicht. Sie können sich aber mit **bisher gespeicherten** Textschlüsseln überschneiden. V0022 kennt deren Kodierungsgeschichte nicht und lässt ausschließlich druckbare Altschlüssel stehen. Der zusätzliche Namensvergleich aus F2 verhindert das nicht, wenn der neue Anmeldename noch keinem Anwendungskonto gehört.

**Reproduktion 1, reserviertes Präfix:**

1. Eine befüllte V0021-Datenbank mit einem LDAP-Konto `legacy-prefix`, gespeichertem Textschlüssel **`hex:80`**, aufbauen. Dieser Schlüssel war mit dem alten Textpfad zulässig. Die SQL-Fixture stellt einen möglichen Altzustand dar; die ursprüngliche Anmeldung dieses Kontos wurde in diesem Lauf nicht mit PR #155 ausgeführt.
2. V0022 regulär per Flyway ausführen: Konto und Zuordnung bleiben aktiv.
3. Ausschließlich diesem Altkonto über das echte Gruppen-API `accounts.manage` geben.
4. Einen **anderen** synthetischen LDAP-Eintrag `prefix-new` mit eigener Kennung Rohbyte **`80`** und eigenem Passwort anmelden. Die neue Kodierung ergibt ebenfalls **`hex:80`**.
5. Ergebnis: **200**, Sitzung als **`legacy-prefix`**, `GET /api/v1/accounts` **200**. Ein Konto `prefix-new` wird nicht angelegt. Das Passwort des alten Kontoinhabers wird nicht benötigt.

**Reproduktion 2, frühere Kürzung:** Altkonto `legacy-trim` mit gespeichertem **`trim-id`**, das früher aus der Rohkennung **` trim-id `** entstanden sein kann. Die Migration lässt den Schlüssel stehen. Der ursprüngliche Eintrag mit gepolsterter Kennung und ursprünglichem Kontonamen erhält nun **401**. Ein anderer Eintrag `trim-new` mit Rohkennung **`trim-id`** meldet sich dagegen erfolgreich als **`legacy-trim`** an und erreicht dessen `accounts.manage` (**200**).

Beide Abläufe liefen am unveränderten aktuellen Backend nach dem echten Upgrade: `legacy-prefix-collision` und `legacy-trim-collision` in [upgrade-results.json](review-evidence/task-048/upgrade-results.json). [IdentifierProbe.java](review-evidence/task-048/IdentifierProbe.java) dokumentiert zusätzlich den Vergleich zur früheren Textbehandlung mit `trim()`.

**Voraussetzung und Grenze:** Es muss ein entsprechender Altschlüssel existieren und das konfigurierte Kennungsattribut einen kollidierenden Wert eines anderen Eintrags liefern. Die Proben verwenden ein synthetisches LDAP ohne Produktschema und das konfigurierbare Attribut `employeeNumber`; sie behaupten nicht, dass Active Directory in einem 16-Byte-`objectGUID` jeden dieser Werte zulässt. Die Kürzungsvariante betrifft bereits gewöhnlichen UTF-8-Text. Konkrete Eintrittswahrscheinlichkeit und Vergaberegeln des Zielverzeichnisses müssen gesondert qualifiziert werden. Ein neuer leerer Datenbestand allein reproduziert diesen Upgradefehler nicht.

**Vorschlag:** Den Übergang zwischen alter und neuer Schlüsseldarstellung ausdrücklich versionieren beziehungsweise alle potentiell mehrdeutigen Altzuordnungen vor der weiteren Anmeldung sicher verifizieren oder sperren. Eine Zuordnung darf nicht allein deshalb übernommen werden, weil ihr unversionierter Alttext zufällig der neuen Kodierung entspricht. Präfixfälle lassen sich gezielt erkennen; die ursprünglichen entfernten Randzeichen lassen sich aus dem gespeicherten Schlüssel allein nicht rekonstruieren. Deshalb die Reparaturstrategie und manuelle Wiederzuordnung mit klarer Identitätsprüfung festlegen; nicht einfach den neuen Schlüssel wieder kürzen. Upgrade-Regressionen mit beiden Gegenbeispielen, getrennten Rechten und versagendem fremdem Kontozugriff ergänzen.

### N2 — niedrig: Gleiches Kennungs- und Namensattribut verliert den Anzeigenamen

**Fundstellen:** [DirectoryClient.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryClient.java), Zeilen 184, 239–242 und 272; [LdapSettingsService.java](../backend/src/main/java/taskmanager/identity/directory/LdapSettingsService.java), Zeilen 99–105.

**Ablauf:** Zulässige Einstellung `idAttribute: uid`, `nameAttribute: UID`, Verzeichniseintrag mit `uid: case-user`. JNDI behandelt die Attribute unabhängig von der Großschreibung als identisch und liefert durch die neue Binäranforderung ein `byte[]`. Die Kennung wird korrekt zu **`case-user`**, aber `displayName` verwirft jedes `byte[]`: **Anzeigename null**. Die Konfiguration verbietet gleiche Attribute nicht. Bei getrenntem Namensattribut `cn` kommt derselbe LDAP-Eintrag mit Anzeigenamen an. Beleg: `same-identifier-name` und `wire-attribute` in [wire-results.json](review-evidence/task-048/wire-results.json).

**Auswirkung:** Ein konfigurierter textueller Anzeigename wird bei dieser Attributkombination ignoriert beziehungsweise nicht mehr aktualisiert; kein zusätzlicher Rechtezugriff nachgewiesen.

**Vorschlag:** Den Namenswert unabhängig von der Rohbytebehandlung der Kennung sicher als Text lesen/dekodieren oder diese Kombination ausdrücklich mit nachvollziehbarer Validierung und Dokumentation ausschließen. Groß-/Kleinschreibung beim Vergleich berücksichtigen. Eine Regression für gleiche Attribute ergänzen.

## Aussagekraft der Tests und fehlende Fälle

Die eigenen Läufe bestätigen die Wirkung der vier neuen LdapLoginTest-Regressionen: dieselben unveränderten Tests ergeben mit den drei isoliert kompilierten Klassen `DirectoryClient`, `DirectoryAccounts` und `LdapSettingsService` aus PR #155 **vier Assertionsfehler mit den ursprünglichen Symptomen**, am aktuellen Stand **vier erfolgreiche Tests**. Dies ist ein gezielter Altklassenvergleich in der heutigen Testumgebung, kein vollständiger Build des historischen Produktstands. [regression-old-results.json](review-evidence/task-048/regression-old-results.json) und [regression-current-results.json](review-evidence/task-048/regression-current-results.json) enthalten die Ergebnisse ohne Sitzungsantworten.

- **LdapLoginTest**, Zeilen 650–673: echte LDAP-Rohwerte, zwei Konten, nur A berechtigt, B-Identität und 403 geprüft. Aussagekräftiger Schutz des ursprünglichen F1, keine bloße Prüfung einer Hilfsfunktion.
- **DirectoryIdentifierTest**, Zeilen 20–64: UUID-/Unicode-Text, binäre Trennung, Präfix, Leerraum, ungültiges UTF-8, U+FFFD als String, fehlende/mehrfache/zu lange Werte. Gute Abdeckung der neuen isolierten Kodierung. Die zweite GUID im Unit-Test unterscheidet sich geringfügig vom berichteten Wert; die eigene HTTP-Probe verwendet exakt beide vorgegebenen Werte.
- **F2-/F3-Regressionen**, Zeilen 688–719: echte HTTP-Verweigerung und beide Einstellungswege. Eigene Probe ergänzt die freie Umbenennung und bei POST die Prüfung des konkreten Verstoßcodes.
- **F4-Regression**, Zeilen 723–732: Grund im Testendpunkt und verweigerte Anmeldung; sie allein zählt keine LDAP-Such-/Bindoperationen. Das ergänzt der eigene WireProbe mit Interceptor.
- **Migrationstest**, Zeilen 736–764: setzt Altdaten ein und führt das Migrations-SQL auf dem schon aktuellen Schema aus; prüft Reparatur, Sitzung und wiederholte SQL-Ausführung. Er belegt allein keinen Flyway-Upgrade von V0021. Der eigene UpgradeProbe schließt diese Lücke.

**Fehlend im Produkttestsatz:** Upgrade mit alten Präfix-/gekürzten Schlüsseln und späterer Anmeldung beider realer Einträge (N1), gleiches Kennungs-/Namensattribut (N2), breitere Grenzen der 512-Zeichen-Darstellung und zusätzliche fehlerhafte Stringwerte aus einem atypischen Provider. Ein Zielverzeichnistest muss insbesondere die tatsächlich gelieferten Rohbytes, Bestandskonten, Umbenennungen und getrennte Rechte nachweisen. Der grüne Gesamtlauf widerlegt N1 nicht.

## Nicht gefunden

- Keine erneute Kontoverwechslung mit den beiden ursprünglich berichteten binären Kennungen auf einer frischen Datenbank; B bekommt A-Verwaltungsrechte nicht.
- Keine Normalisierungskollision bei den geprüften frischen Bytefolgen, NFC/NFD, Umlauten und ergänzenden Unicodezeichen; normale UUID-Textschlüssel bleiben erhalten.
- Kein Annehmen eines bereits verlustbehafteten U+FFFD-Strings als Kennung; fehlende und mehrfache Attribute verweigert. Attributoptionen und numerische OIDs wurden vom aktuellen Validator mit 400 abgewiesen und daher nicht als unterstützte Konfiguration vorausgesetzt.
- Keine unbeabsichtigte Änderung der geprüften unauffälligen Migrationsdatensätze oder ihrer offenen Sitzungen; kein SQL-Fehler beim befüllten V0021→V0022-Upgrade und keine doppelte Flyway-Anwendung.
- Keine erfolgreiche Anmeldung bei der ursprünglichen Namenskollision, kein 500 bei den sechs `security`-Varianten und den 32 weiteren PUT-Eingaben.
- Kein Suchen, Suchkonto-Bind oder Personen-Bind nach der beobachteten StartTLS-Ablehnung; spezifische Verbindungs-/Bindgründe und die vorhandenen Zertifikatsprüfungen bleiben wirksam.
- Keine Produktcodeänderung und keine Änderung an Aufgabenstatus oder fachlicher Abnahme durch diese Prüfung.

## Empfehlung an Holger

Von den drei Stufen **annehmbar / annehmbar nach Behebung von … / nicht annehmbar** empfehle ich **nicht annehmbar im geprüften Stand**: N1 lässt im unterstützten allgemeinen Zuordnungsweg nach einem Upgrade erneut ein fremdes Bestandskonto samt Rechten erreichen. Vor Freigabe den Übergang der Altschlüssel absichern und beide Upgrade-Gegenbeispiele erneut prüfen. N2 sollte ebenfalls korrigiert oder ausdrücklich als Konfigurationsgrenze behandelt werden. F2 bis F4 können technisch als behoben vermerkt werden; die Qualifikation am vorgesehenen Verzeichnisprodukt bleibt offen. Diese Empfehlung ist keine fachliche Abnahme.
