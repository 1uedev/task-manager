# Getrennte technische Prüfung TASK-047 / PR #157

**Empfehlung an Holger: nicht annehmbar im geprüften Stand.** Die relative Sperre der letzten lokalen Verwaltung hält in den geprüften Entzugswegen einschließlich paralleler Änderungen. Die Umstellung selbst ist aber gegenüber einer laufenden LDAP-Anmeldung nicht sicher: F1 erlaubt danach eine volle Sitzung auf dem lokalen Konto mit dessen Rechten, obwohl nur das Verzeichnispasswort gesendet wurde. F2 betrifft den Verlust der Einmalpasswortanzeige bei Selbstumstellung. Die fachliche Abnahme bleibt bei Holger.

## Prüfstand und ausgeführte Befehle

- Datum: **24. Oktober 2026**. Sitzung `codex-20261024-task047-review-01`, eigener Klon, Branch `review/task-047`; keine Mitwirkung an der Produktimplementierung.
- Gegenstand: [PR #157](https://github.com/1uedev/task-manager/pull/157), Merge **`a1426fbd1bc58bf90ad7382bfa433201d0b2e597`**, DEC-076.
- Tatsächlich getesteter aktueller main: **`1274c318de3a2baa995118804b5debc9391e93d3`**. Dieser enthält zusätzlich TASK-048 / PR #160 und V0022. Gegenüber PR #157 sind im Produktcode nur `DirectoryAccounts`, `DirectoryClient` und `LdapSettingsService` geändert; TASK-047-Kontoservice, Sitzungsservice, Rechtekern, Adapter, Oberfläche und Verträge sind unverändert. Der für F1 maßgebliche fehlende Kontotyp-/Kennungsabgleich nach der Zeilensperre besteht bereits in PR #157. Kein vollständiger historischer Build von PR #157 wird behauptet.
- Gelesen: AGENTS.md, CLAUDE.md, docs/AI_WORKFLOW.md, README.md, TODO.md TASK-047, DONE.md, DEC-076, relevante technische Basis, Modul-/Rechte- und LDAP-Dokumentation, Implementierung, Verträge und Tests. TASK-047 ist BEREIT ZUR PRÜFUNG bei `claude-20261023-localadmin-01`, abhängig von TASK-046. Die frühere TASK-046-Prüfung wird hier nicht nachträglich als Prüfung von TASK-047 ausgegeben.
- Nur Bericht und Prüfbelege; keine Produkt-, TODO- oder DONE-Änderung, keine neue Aufgabenreservierung.

| Selbst ausgeführter Befehl / Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py` | Repository contract OK |
| `python3 scripts/task_board.py --check` | Aufgabenübersicht aktuell |
| `python3 -m unittest discover -s tests -v` | Mit `PYTHONUTF8=1`: **38 Tests, OK, 1 Skip** mangels `time.tzset`; 37 ausgeführt. Erster Lauf ohne UTF-8-Vorgabe: acht fehlgeschlagene Prüfungen wegen falsch kodierter temporärer Testdateien unter Windows, siehe Prüfprotokoll |
| Backend: `./mvnw -B --no-transfer-progress verify` mit `taskmanager.test.jdbc-url`, `.username`, `.password`, `.server-version=18.6` | **272 Tests, 0 Fehler, 0 Skips, BUILD SUCCESS**, 6:15 Minuten. Darunter LdapLoginTest 25, IdentityPermissionsAdapterTest 18, PermissionsApiTest 8, IdentityApiTest 33 |
| Frontend: `npm ci`, `npm run verify` | Vertragsgenerierung, Typprüfung, **75 Tests** und Produktionsbuild erfolgreich |
| `TASKMANAGER_BROWSER=<Chromium> node --test --test-concurrency=1 tests/browser/*.test.mjs` | **92 Tests / 16 Suiten, alle erfolgreich**, 131,28 Sekunden; Dateien nacheinander |
| Eigene Backendproben | Echte HTTP-Anfragen, PostgreSQL-Wartebeobachtungen, LDAP-Gegenstelle, beide Umstellungs-/Deaktivierungsreihenfolgen, spätes Rechteentziehen, zwei LDAP-/Umstellungsrennen, CLI-Wiederherstellung und befüllter V0021→V0022-Upgrade |
| Eigene zusätzliche Browserprobe | Selbstumstellung mit der vom echten Backend separat belegten Sitzungsbeendigung als API-Fixture, **DE und EN**, F2 reproduziert |
| `git diff --check`, erneute Repositoryprüfungen nach Berichtserstellung | Erfolgreich, siehe [Prüfprotokoll](review-evidence/task-047/verification.md) |

Laufzeit: Temurin **25.0.4.1+1**, Maven 3.9.16, native **PostgreSQL 18.6**, Node 24.22.0, npm 11.19.1, installierter Chrome. Windows-Starter wurden entsprechend den angeforderten Unix-Befehlen verwendet. Synthetische Daten ausschließlich in eigenen Testdatenbanken. Quellen, Ergebnisdateien und Grenzen stehen unter [docs/review-evidence/task-047](review-evidence/task-047/verification.md).

**Nicht ausgeführt:** OpenLDAP-/AD-Qualifikation, Docker-/CI-Containerlauf, zusätzlicher Shell-Lebenszykluslauf, vollständiger Browserablauf gegen das echte Backend, langdauernde Lastprüfung oder exhaustive Prüfung aller Zeitpläne. Die 92 vorhandenen Browserprüfungen und die eigene Zusatzprobe verwenden API-Fixtures; echte Backendabläufe sind getrennt belegt.

## Befunde

### F1 — hoch: Laufende LDAP-Anmeldung überlebt die Umstellung und umgeht das lokale Passwort

**Fundstellen:** [DirectoryAccounts.java](../backend/src/main/java/taskmanager/identity/directory/DirectoryAccounts.java), Zeilen 43–52, insbesondere der nicht sperrende Lookup mit anschließendem `findByIdForUpdate`; [SessionService.java](../backend/src/main/java/taskmanager/identity/session/SessionService.java), Zeilen 139–175 und 185–201; Umstellung in [AccountService.java](../backend/src/main/java/taskmanager/identity/account/AccountService.java), Zeilen 223–231.

**Belegter Fehler:** `assign` findet die LDAP-Zuordnung zunächst ohne Zeilensperre und lädt das Konto danach unter `FOR UPDATE` neu. Nach einer dazwischen abgeschlossenen Umstellung akzeptiert es auch den nun **lokalen** Datensatz ohne `external_id`. `startSession` bekommt diesen neuen Datensatz als angeblich schon verifizierten Zustand. Der Hashvergleich prüft dann den neuen lokalen Hash gegen denselben neuen lokalen Hash; das Passwort der LDAP-Anfrage wurde damit nie verglichen.

**Reproduktion einer vollen Sitzung, eigenes [ConversionRaceProbe.java](review-evidence/task-047/ConversionRaceProbe.java):**

1. LDAP-Person `race-person` anmelden, ihrem Anwendungskonto über das echte Gruppen-API `accounts.manage` geben. Eine davon unabhängige lokale Verwaltung bleibt erhalten.
2. In einem anderen HTTP-Client eine weitere Anmeldung **nur mit dem Verzeichnispasswort** beginnen. Sie hat das Verzeichnis bereits erfolgreich geprüft und den alten Datensatz über `external_id` gelesen.
3. Ausschließlich die Ausführung **zwischen diesem abgeschlossenen Lookup und der folgenden Zeilensperre** pausieren. Dafür umschließt ein Prüf-BeanPostProcessor die Repositorymethode und hält nach deren echter Rückgabe an; kein Argument, Datenbankwert, Passwortprüfer oder Rückgabewert wird ersetzt.
4. Die lokale Verwaltung stellt das Konto regulär per HTTP auf lokal um (**200**). Der berechtigte andere Client meldet sich mit dem frisch ausgegebenen Einmalpasswort an und schließt den erzwungenen lokalen Passwortwechsel ebenfalls per HTTP ab (**200**).
5. Vor Fortsetzung ist das Konto nachweislich **local, external_id null, Pflichtwechsel abgeschlossen**; die frühere Sitzung ist beendet. Dann die pausierte LDAP-Anmeldung fortsetzen.
6. Ergebnis des wartenden Clients: **200, `level: full`, `authKind: local`, Benutzer `race-person`**. Mit dieser Sitzung `GET /api/v1/accounts`: **200**. Der wartende Client hat weder das Einmalpasswort noch das neue lokale Passwort gesendet. Eine erst danach neu begonnene Anmeldung mit demselben Verzeichnispasswort wird korrekt mit **401** verweigert.

Beleg: [conversion-race-results.json](review-evidence/task-047/conversion-race-results.json). Die Pause erzwingt einen konkreten zulässigen Zeitplan; sie beweist nicht dessen Häufigkeit unter Last. Sie ist keine Manipulation des Produktzustands, mit der die Authentifizierung erst künstlich erlaubt würde.

**Zusätzlicher Nachweis ohne Repository-Wrapper:** [GuardProbes.java](review-evidence/task-047/GuardProbes.java) pausiert die echte Umstellung durch einen Testtrigger vor deren Commit. Eine LDAP-Anmeldung liest noch die alte Kennung und wartet nachweislich an `FOR UPDATE` auf das Zielkonto. Nach dem Commit bekommt sie **200 als lokales Konto mit `password_change_required`** und verbraucht dessen gerade ausgegebenes Einmalpasswort. Die berechtigte Person erhält anschließend **401 `auth.one_time_password_used`**. Beide Wartezustände wurden in `pg_stat_activity` beobachtet, siehe `conversion-login-race` in [guard-results.json](review-evidence/task-047/guard-results.json). Dieser Ablauf belegt bereits eine unzulässige lokale Sitzung und den Verbrauch des Einmalpassworts; die volle Rechteübernahme ist durch den ersten Ablauf zusätzlich belegt.

**Auswirkung:** Die von DEC-076 und dem Vertrag zugesicherte Trennung „danach entscheidet allein das lokale Passwort“ gilt für bereits laufende Anmeldungen nicht. Ein Inhaber des bisherigen Verzeichniszugangs kann das umgestellte Konto einschließlich bestehender Rechte erreichen oder die legitime Erstbenutzung des neuen Einmalpassworts verhindern. Die funktionierende Sperre der letzten lokalen Verwaltung verhindert diesen Authentifizierungsfehler nicht.

**Vorschlag:** Nach dem Erwerb der Kontosperre den aktuellen Kontotyp **und** die aktuelle externe Kennung gegen die authentifizierte Verzeichnisidentität prüfen. Wurde die Zuordnung aufgehoben oder geändert, die Anmeldung verweigern; den neuen lokalen Datensatz nicht als erfolgreich LDAP-verifiziert behandeln und kein Einmalpasswort konsumieren. Zusätzlich die Erwartung des Authentifizierungswegs bei der Sitzungserzeugung absichern. Beide Zeitpläne als Regression mit getrennten Clients und getrennten Rechten aufnehmen. Den Netzwerkaufruf dafür nicht unter eine globale Datenbanksperre verlagern.

### F2 — mittel: Selbstumstellung entfernt die einmalige Passwortanzeige sofort

**Fundstellen:** [AccountsPage.tsx](../frontend/src/pages/AccountsPage.tsx), Zeilen 67–76 und 182–188; [client.ts](../frontend/src/api/client.ts), Zeilen 118–119; [session.tsx](../frontend/src/app/session.tsx), Zeilen 44–47. Die serverseitige Sitzungsbeendigung in `AccountService.issueNewOneTimePassword` ist hier korrekt.

**Ablauf:** Ein LDAP-Konto mit `accounts.manage` sieht auch für sich selbst die Umstellungsschaltfläche. Es bestätigt die Umstellung. Das Backend erlaubt sie mit **200 und Einmalpasswort**, beendet dabei vertragsgemäß auch die ausführende eigene Sitzung. Die Oberfläche setzt zunächst `issued`, ruft aber unmittelbar `reloadDirectory()` und `reload()` auf. Deren geschützte Leseanfragen erhalten **401**; die globale Sitzungsbehandlung zeigt die Anmeldung und entfernt die Kontenseite samt Geheimnisanzeige, bevor eine verlässliche Übernahme des einmaligen Passworts möglich ist. Es fehlt ein eigener Abschlussablauf für diesen erlaubten Fall.

**Beleg:** `self-conversion` in [administration-results.json](review-evidence/task-047/administration-results.json) bestätigt am **echten Backend** 200 mit Passwort, anschließend anonyme Sitzung und 401 beim Kontenlesen. [self-conversion-browser.mjs](review-evidence/task-047/self-conversion-browser.mjs) bildet genau dieses Ende der Sitzung im Browser als API-Fixture ab: in **DE und EN** enthält die erfolgreiche Antwort ein Einmalpasswort, danach ist die Anmeldeseite sichtbar und die Passwortanzeige nicht mehr vorhanden. Ergebnisse: [self-conversion-browser-results.json](review-evidence/task-047/self-conversion-browser-results.json). Das ist ein Browsernachweis mit kontrollierten Antworten plus separater Backendnachweis, kein durchgängiger Browserlauf am echten Backend.

**Auswirkung:** Nach erlaubter Selbstumstellung benötigt die Person unter Umständen ein erneutes Zurücksetzen durch eine andere Verwaltung; das frühere LDAP-Passwort gilt regulär nicht mehr. Kein Datenabfluss oder zusätzlicher Rechtezugriff durch diesen Oberflächenfehler nachgewiesen.

**Vorschlag:** Für die Selbstumstellung einen ausdrücklichen Abschlussablauf mit einmaliger Passwortübernahme vor dem Wechsel zur Anmeldung vorsehen, ohne weitere geschützte Aktionen anzubieten. Alternativ eine fachlich abgestimmte Beschränkung der Selbstumstellung konsistent in API, Vertrag und Oberfläche einführen. Die bestehende Testfixture sollte auch die Beendigung der ausführenden Sitzung abbilden und diesen Ablauf in DE/EN prüfen.

## Prüfung der Verwaltungszusicherung und weiterer Schreibwege

### 1. Entzug über Konto, Mitgliedschaft und Recht

| Weg | Prüfung und Ergebnis |
| --- | --- |
| `AccountService.deactivate` | Zuerst gemeinsame Verwaltungsserialisierung, dann `AccountDeactivationGuard` → `CompanyAdministration.remainsWithout` unter exklusiver Rechtesperre, danach verbindliche Rechteprüfung und Kontosperre. Letzte aktive lokale Verwaltung mit beiden Rechten: **409 `account.last_administrator`**, unverändert |
| `JdbcPermissions.setMember(..., false)` | Exklusive Rechtesperre; Zustand `hadLocal` vor dem Löschen; nach dem Löschen bisherige Unternehmensverwaltung und lokale Verwaltung erneut prüfen. Verlust der letzten lokalen Verwaltung trotz verbleibender LDAP-Verwaltung: **409 `permissions.last_local_administrator`**, Transaktion wird zurückgerollt |
| `JdbcPermissions.setAction(..., false)` | Für die Unternehmensrechte `permissions.manage` und `accounts.manage` dieselbe relative Prüfung; Projektrechte und andere Aktionen verändern diese Zusicherung nicht. Rechte dürfen aus verschiedenen Gruppen stammen: die Inhabermengen werden pro Konto geschnitten |
| Gruppen ändern/löschen | Im aktuellen API/Java-Schreibweg gibt es keine Gruppenlöschung oder Änderung einer bestehenden Gruppe mit kaskadierendem Rechteentzug. Keine ausgelassene Löschfunktion gefunden; eine künftige Funktion müsste dieselbe Schranke verwenden |
| Initialisierung | `bootstrap-admin` erzeugt Konto, Firma und Anfangsrechte atomar. `initialize` fügt Rechte/Mitgliedschaft hinzu, entfernt keine. Bei bereits vorhandenen Konten wird Bootstrap verweigert |
| Reaktivieren / Notbefehl | `setActive(true)` nur bei regulärer Reaktivierung oder erfolgreichem lokalem Notreset; dies beseitigt keine vorhandene lokale Verwaltung. Notreset vergibt selbst keine fehlenden Rechte |
| Kontoumstellung | Nur LDAP→local, `external_id` wird null, Aktivstatus unverändert. Kein lokaler→LDAP-Schreibweg vorhanden. Sie kann eine lokale Verwaltung hinzufügen, aber keine bestehende lokale Verwaltung entfernen; F1 betrifft den veralteten Anmeldezustand |
| Direkte Repository-Schreiber | `insert` für Neuanlage, `setActive` und `convertToLocal` mit den genannten Dienstaufrufern. Andere Passwort-/Namensänderungen schreiben weder `active`, `auth_kind` noch `external_id`. Keine weitere ausführbare Mutation an `permissions_membership` oder `permissions_action` außerhalb `setMember`, `setAction` und der Initialisierung gefunden |

Fundstellen: [AccountService.java](../backend/src/main/java/taskmanager/identity/account/AccountService.java), Zeilen 160–195, 223–231, 241–251 und 265–280; [JdbcPermissions.java](../backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java), Zeilen 104–155, 201–238 und 428–449; [JdbcIdentityVerification.java](../backend/src/main/java/taskmanager/identity/session/JdbcIdentityVerification.java), Zeilen 89–91; [AccountLifecyclePorts.java](../backend/src/main/java/taskmanager/AccountLifecyclePorts.java), Zeilen 38–52.

`userActiveLocal` fragt unter `FOR SHARE` ausdrücklich `active and auth_kind='local'` ab. Ein LDAP-Konto zählt unabhängig von seinen Rechten nicht. Ein aktives lokales Konto mit abgelaufenem oder noch unbenutztem Einmalpasswort zählt dagegen nach dem ausdrücklich offenen Punkt in DEC-076 weiter; das wird nicht als neuer Implementierungsfehler gemeldet.

### 2. Gleichzeitige Änderungen und Sperrreihenfolge

Die eigene Warteprobe startet jeweils zwei echte HTTP-Änderungen gegen zwei verschiedene lokale Verwaltungen, während eine LDAP-Verwaltung mit beiden Rechten unverändert bestehen bleibt. Die vorgeschaltete Testtransaktion erzwingt eine Warteschlange; `pg_stat_activity` muss `wait_event_type='Lock'` und die passende SQL-Stelle zeigen. Es wird nicht nur aus einer noch laufenden Future auf Sperrwirkung geschlossen.

| Gleichzeitige Operationen | Beobachtete Wartende | Endergebnis |
| --- | --- | --- |
| Zwei Mitgliedschaften entfernen | **2** an exklusiver Rechtesperre | Eine Änderung 200, andere 409; genau eine lokale Verwaltung bleibt |
| Zwei Verwaltungsrechte entziehen | **2** an exklusiver Rechtesperre | Eine Änderung 200, andere 409; genau eine lokale Verwaltung bleibt |
| Zwei lokale Verwaltungen deaktivieren | **2** an Verwaltungsserialisierung | Eine Änderung 200, andere 409 `account.last_administrator`; genau eine bleibt |
| Deaktivieren und Mitgliedschaft entfernen | **2** an Rechteprüfungen, frühe gemeinsame beziehungsweise exklusive Sperre | Eine Änderung 200, andere 409; genau eine bleibt |

Beleg: [guard-results.json](review-evidence/task-047/guard-results.json). Die Abfrage für den gemischten Fall umfasst bewusst `FOR SHARE` und `FOR UPDATE`: eine Anfrage kann bereits bei der frühen Rechteansicht warten. Es wird nicht behauptet, beide seien dort schon im exklusiven Änderungsteil.

Für die Änderungen an Konten läuft die Serialisierung **vor** den verbindlichen Rechte-/Identitätssperren. Die Rechteänderungen brauchen keine Verwaltungsserialisierung und sperren zuerst die Rechteversion, dann die Identität. Die zusätzlichen geteilten Kontosperren aller Inhaber in `localAdministrators` können Änderungen verzögern, schaffen in den untersuchten Wegen aber keinen rückwärts gerichteten Bedarf an der Verwaltungsserialisierung. Bei Rechten werden Konten in sortierter Inhaberreihenfolge abgefragt; geteilte Kontosperren miteinander sind verträglich. Der Container-Notreset sperrt dagegen Konto zuerst, liest `isAdministrator` anschließend **ohne** Rechtesperre; dort wurde keine behauptete umgekehrte Rechte-Sperrkante gefunden. Er ist kein allgemeiner Schreibzugang auf Rechte.

Eigene Proben für **Umstellung vor Deaktivierung** und **Deaktivierung vor Umstellung** halten den ersten Vorgang nach dessen echter Kontomutation an. Der zweite wartet nachweislich entweder an der Verwaltungsserialisierung oder bereits bei der frühen gemeinsamen Rechtesperre. Beide enden mit **200/200**, anschließend **local, external_id null, inaktiv**; auch das neue Einmalpasswort meldet nicht an (**401**). Keine unbeabsichtigte Reaktivierung, kein Deadlock und kein 500 beobachtet. Beleg: [administration-results.json](review-evidence/task-047/administration-results.json). Diese endlichen Abläufe sind kein Beweis für alle Lastsituationen; F1 zeigt gerade eine nicht abgesicherte andere zeitliche Überlappung.

### 3. Relative Sperre, Bestandsinstallationen und Notweg

Die Schranke ist wie DEC-076 **relativ**, keine automatische Herstellung eines gültigen Ausgangszustands. Eine durch Bootstrap korrekt eingerichtete Installation verlor in den geprüften regulären Wegen ihre letzte lokale Verwaltung nicht. Ein historischer Zustand, in dem zuvor keine aktive lokale Verwaltung mit beiden Rechten vorhanden war, wird dagegen zugelassen.

Eigene ausdrücklich als **Altzustand per SQL** eingesetzte Fixtures, keine erfolgreich an der aktuellen Schranke vorbeigeführten API-Entzüge:

1. **Inaktives lokales Konto mit `permissions.manage`, aber ohne `accounts.manage`:** echter Prozess `java -jar … reset-admin-password NAME` erfolgreich (**Exit 0**), Konto reaktiviert, Einmalpasswort und Pflichtwechsel funktionieren. Konten-API zunächst **403**. Danach gibt sich dieses Konto über das Gruppen-API `accounts.manage` (**200**); Konten-API **200**, eine lokale Verwaltung vorhanden.
2. **Nur LDAP-Verwaltung, keinerlei lokaler Inhaber von `permissions.manage`:** lokaler Notreset **Exit 2 `account.not_administrator`**, Notreset der LDAP-Verwaltung **Exit 2 `account.directory_managed`**, Bootstrap **Exit 2 `bootstrap.accounts_exist`**. Der Notweg kann diesen Altzustand bei einem Verzeichnisausfall **nicht allein reparieren**. Eine noch offene LDAP-Sitzung kann während des Ausfalls ein lokales Konto wieder mit beiden Rechten ausstatten, was die eigene Probe mit **200** nachweist. Sind auch solche Sitzungen weg, braucht es ein wieder erreichbares Verzeichnis oder eine gesondert festzulegende Betreiberwiederherstellung.

Beleg: [RelativeGuardProbe.java](review-evidence/task-047/RelativeGuardProbe.java), [relative-results.json](review-evidence/task-047/relative-results.json). Das ist eine betriebliche Grenze der ausdrücklich relativen Entscheidung und wird nicht als neu gebrochene relative Zusicherung ausgegeben. **Empfehlung:** Vor Einsatz beziehungsweise Upgrade den Bestand auf eine lokale Verwaltung mit beiden Rechten prüfen und deren Notweg erproben; den nicht selbstheilenden Altzustand in der Betriebsdokumentation ausdrücklich benennen.

### 4. Migration V0022 als zusätzlicher Schreiber

V0022 gehört zu TASK-048, wird hier wegen der gefragten Verwaltungszusicherung mitbetrachtet. Sie schreibt ohne Dienst-/Rechtesperre direkt `auth_kind`, `external_id`, `active` und Sitzungen. Durch die V0021-Paarungsregel besitzt ein lokales Konto jedoch immer `external_id null`; der V0022-Filter auf nichtleere unzuverlässige externe Schlüssel kann **keine bereits aktive lokale Verwaltung treffen**. Er kann nur LDAP-Konten abtrennen und deaktivieren. Eine vorher vorhandene lokale Verwaltung wird dadurch nicht beseitigt.

Eigener echter Flyway-Lauf von befülltem **V0021 auf V0022**: vier betroffene LDAP-Konten wurden inaktiv/lokal, Sitzungen beendet und Ereignisse geschrieben; fünf unauffällige LDAP-Konten blieben erhalten, zweiter Migrationsaufruf ohne neue Migration. In der Legacy-Fixture bestand schon vorher keine lokale Verwaltung. Nach dem Upgrade ließ sich die jetzt lokale ehemalige LDAP-Verwaltung über den **echten CLI-Notreset** reaktivieren; danach eine lokale Verwaltung, Konten-API **200**, erneutes Deaktivieren **409**. Beleg: [RecoveryUpgradeProbe.java](review-evidence/task-047/RecoveryUpgradeProbe.java), [upgrade-results.json](review-evidence/task-047/upgrade-results.json). Die anderen bereits in PR #161 berichteten TASK-048-Kodierungsbefunde werden hier nicht als neue TASK-047-Befunde dupliziert.

## Umstellung, Rechte, Vertrag und Oberfläche außerhalb der Befunde

- `AccountController` verwendet **early und require** für `accounts.manage`; die verbindliche Prüfung läuft nach der Serialisierung. Eigene wartende Umstellung mit inzwischen entzogenem Recht: beobachteter Wartender an der Serialisierung, anschließend **403 `auth.forbidden`**, Ziel weiterhin LDAP. Die vorhandene LdapLoginTest-Probe ohne Befugnis besteht ebenfalls.
- Umstellung liefert ein neues Einmalpasswort mit Pflichtwechsel, löst `external_id`, beendet alte Sitzungen, behält Konto-ID, Namen und Gruppenmitgliedschaften und schreibt genau ein `account.converted_to_local` ohne Geheimnisdetail. Wiederholte Umstellung: **409 `account.already_local`**. Die vorhandenen Tests prüfen zudem Umstellung und lokalen Pflichtwechsel bei ausgefallenem Verzeichnis. Die beiden Gegenbeispiele F1 und F2 betreffen die darüber hinaus nicht abgesicherten Abläufe.
- Ein anderer LDAP-Anmeldename desselben Eintrags kann danach ein **neues LDAP-Konto ohne Gruppen** erhalten. Eigene Umbenennung `convert-race`→`convert-alias`: neue Konto-ID, LDAP, **null Mitgliedschaften**. Dies entspricht dem ausdrücklich beschriebenen DEC-076-Verhalten und verleiht keine Rechte des lokalen Kontos. Für die Trennung ist das vertretbar, sofern Betreiber wissen, dass die Umstellung den Verzeichniseintrag selbst nicht sperrt. F1 ist davon zu unterscheiden: dort wird das bestehende lokale Konto erreicht.
- `identity.openapi.yaml` dokumentiert Umstellung, `accounts.manage`, 200/401/403/404/409, `authKind`, Einmalpasswort, Sitzungsende und `account.already_local`; `permissions.openapi.yaml` dokumentiert die relative lokale Schranke und `permissions.last_local_administrator`. Die beobachteten regulären Statuscodes passen. Die Aussage zum ausschließlichen lokalen Passwort ist wegen F1 nicht vollständig erfüllt.
- Bestätigung, Warnung vor fehlendem Rückweg, Anzeige des neuen Passworts und Entfernen der Umstellungsschaltfläche nach erfolgreicher Fremdumstellung vorhanden. Spezifische Meldungen für `account.last_administrator` und `permissions.last_local_administrator` sind verdrahtet; Übersetzungen stehen in **DE und EN**. Die vorhandenen Browserprüfungen sind grün. Die eigene Selbstumstellungsprobe zeigt die zusätzliche Lücke F2; ein vollständiger manueller Sprachdurchlauf wird nicht behauptet.
- Der Zertifikatswechsel ohne Neustart steht wie beauftragt im Backlog der ROADMAP; daraus wird keine Abnahme oder zusätzliche Umsetzung abgeleitet.

## Aussagekraft und Lücken der Produkttests

Die drei TASK-047-Fälle in `LdapLoginTest` (Zeilen 541–633) prüfen sinnvoll: LDAP-Verwaltung zählt nicht als lokale Rückfallebene, Entzug über Konto/Mitgliedschaft/beide Rechte, Umstellung bei Ausfall mit Pflichtwechsel und behaltenen Gruppen sowie unbefugte Umstellung und Zählung eines konvertierten Kontos. Sie laufen überwiegend **sequenziell** und prüfen kein Rennen von LDAP-Anmeldung gegen Umstellung.

Die Anpassung in `IdentityPermissionsAdapterTest` (Zeilen 633–637) ergänzt beim zweiten lokalen Konto `accounts.manage`, damit der vorhandene parallele Entzugstest weiterhin zwei tatsächlich vollständige lokale Verwaltungen hat. Das erhält einen aussagekräftigen Test der exklusiven Rechtesperre; es ist allein kein neuer umfassender Nachweis aller LDAP-/lokalen Kombinationen. Die vorhandenen Adaptertests beobachten echte Datenbankwartebeziehungen. Die eigenen HTTP-Proben ergänzen zwei lokale Verwaltungen **plus weiterhin berechtigte LDAP-Verwaltung** und die konkreten Gegenabläufe F1.

Es fehlen insbesondere beide in F1 belegten Regressionen, die Selbstumstellung samt echter Sitzungsbeendigung im Browser, die Legacy-Wiederherstellungsgrenzen und beide Umstellungs-/Deaktivierungsreihenfolgen. Die Browser-Fixture `backend.mjs` (Zeilen 228–234) ändert beim Konvertieren das Konto, beendet dabei aber nicht die ausführende Sitzung; die vorhandenen Umstellungstests behandeln außerdem eine andere Person. Dadurch wird F2 vom grünen Browserlauf nicht ausgeschlossen.

## Nicht gefunden

- Kein regulärer Entzug der letzten vorher vorhandenen aktiven lokalen Verwaltung über die untersuchten Konto-, Mitgliedschafts- und Aktionswege; LDAP-Verwaltungen zählen dafür nicht.
- Kein Deadlock oder 500 in den konkret erzwungenen parallelen Entzügen und beiden Umstellungs-/Deaktivierungsreihenfolgen; Wartebeziehungen und Endzustände wurden gesondert beobachtet.
- Keine erfolgreiche Umstellung ohne das passende Recht oder nach dem geprüften späten Rechteentzug; keine reguläre lokale→LDAP-Umstellung.
- Keine verlorene Gruppenmitgliedschaft beim Umstellen, kein Geheimnisdetail im Umstellungsereignis, kein Überleben der vor der Umstellung eröffneten Sitzung. F1 erzeugt danach eine **neue** unzulässige Sitzung.
- Keine automatische Rechteübernahme durch den in DEC-076 vorgesehenen späteren LDAP-Alias; dort entsteht ein anderes Konto ohne Gruppen.
- Kein Verlust einer vorher vorhandenen aktiven lokalen Verwaltung durch die Auswahl von V0022; der Notreset für einen vorhandenen lokalen Rechteinhaber und dessen anschließende Rechteergänzung funktionieren.

## Empfehlung an Holger

Von **annehmbar / annehmbar nach Behebung von … / nicht annehmbar** empfehle ich **nicht annehmbar im geprüften Stand**. F1 durchbricht die zentrale Authentifizierungsgrenze nach der Umstellung; F2 verhindert bei erlaubter Selbstumstellung die verlässliche Passwortübernahme. Nach Behebung beide Rennen und die Selbstumstellung erneut prüfen. Die relative Verwaltungssperre hat sich in den ausgeführten Abläufen bestätigt, ihre Legacy-Grenze sollte vor dem Einsatz ausdrücklich abgesichert werden. Dies ist eine technische Empfehlung der getrennten KI nach DEC-040, keine fachliche Abnahme durch oder im Namen von Holger.
