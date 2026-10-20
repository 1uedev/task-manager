# Unabhängige Nachprüfung TASK-040 / PR #101

Stand: 20. Oktober 2026. Prüfer: Codex, Sitzung `codex-20261019-admin-rights-review-01`, Branch `review/task-040-pr101-codex`, eigener Arbeitsbereich.

Geprüft wurde die Ablösung der Administratorkennzeichnung durch Verwaltungsrechte gemäß DEC-064 und die technische Integration nach DEC-040. Ergebnis: **ein Befund P2 zur Verfügbarkeit und ein Befund P3 zur Adapterkonfiguration**. Die vorhandenen Tests sind grün; zwei zusätzliche Prüfexperimente reproduzieren die beschriebenen Abweichungen. Ein unberechtigter Schreibzugriff in der regulären Anwendungskonfiguration wurde dabei nicht nachgewiesen.

## Grundlage und Abgrenzung

- Ergebnis: [PR #101](https://github.com/1uedev/task-manager/pull/101), letzter Head `e2f0581ee8225fb0739c2f4c62eae1c85c38a455`, integriert auf main als `ef7dbbc`.
- Prüf- und Veröffentlichungsbasis: main `78a858c6e9d4692e5daf5348764c0be94b6fb071`. [PR #102](https://github.com/1uedev/task-manager/pull/102) änderte danach nur TODO.md und DONE.md; der geprüfte Produktcode ist derselbe.
- Holgers Abnahme ist seit PR #102 bereits dokumentiert. Dort ist die ausstehende unabhängige KI-Prüfung ausdrücklich genannt. Dieser Bericht holt sie nach, ändert keine Abnahme und eröffnet oder reserviert keine Aufgabe.
- Maßstab: AGENTS.md, CLAUDE.md, README.md, TODO.md/DONE.md, docs/AI_WORKFLOW.md, Anforderungen REQ-ACL-001/003, REQ-AUTH-001/003, REQ-BRAND-001 und der Sicherheitsvorschlag REQ-SEC-001; DEC-040/063/064, docs/TECHNICAL_BASELINE.md, relevante Modul-/Transaktionsverträge sowie Backend-, Rechte- und API-Dokumentation. Allgemeine Architekturvorschläge werden nicht als zusätzliche bestätigte Produktvorgaben ausgegeben.
- Frühere Codex-Arbeit am grundlegenden Berechtigungsmodul aus TASK-025 ist keine unabhängige Neuprüfung dieses eigenen Grundmoduls. Gegenstand hier ist Claudes neue Integration der Verwaltungsrechte und deren Wechselwirkung mit Identität und Oberfläche.
- Fehlende Rechteendpunkte, die Neuanlage alter Entwicklungsdatenbanken und die weiter sichtbare Erscheinungsbildvorschau sind in DEC-064 ausdrücklich dokumentierte Grenzen. Die früheren Erscheinungsbildbefunde aus PR #96 werden hier nicht erneut als TASK-040-Befunde gezählt. Eine Prüfung natürlicher Sitzungsabläufe erst beim Commit ist nicht vereinbart.

## Befunde

### F1 — P2: Gegenseitiges Passwortzurücksetzen erzeugt einen Deadlock mit HTTP 500

**Fundstellen:** [AccountController.java:88](https://github.com/1uedev/task-manager/blob/e2f0581ee8225fb0739c2f4c62eae1c85c38a455/backend/src/main/java/taskmanager/identity/web/AccountController.java#L88), [JdbcIdentityVerification.java:44–53](https://github.com/1uedev/task-manager/blob/e2f0581ee8225fb0739c2f4c62eae1c85c38a455/backend/src/main/java/taskmanager/identity/session/JdbcIdentityVerification.java#L44), dessen Kontosperre in Zeile 80 und [AccountService.java:133–135](https://github.com/1uedev/task-manager/blob/e2f0581ee8225fb0739c2f4c62eae1c85c38a455/backend/src/main/java/taskmanager/identity/account/AccountService.java#L133). Die anschließende Zielkontosperre steht in AccountRepository.findByIdForUpdate.

Zwei aktive Konten A und B besitzen jeweils `accounts.manage` und eine vollständige Sitzung. A setzt B zurück, während B A zurücksetzt. Die vorgeschaltete Rechteprüfung hält jeweils das **eigene** Konto `FOR SHARE`; der eigentliche Reset verlangt danach das **andere** Konto `FOR UPDATE`. Damit wartet A auf B und B auf A. PostgreSQL bricht einen Aufruf ab; die Anwendung antwortet dafür mit 500. Der andere Reset wird verbindlich gespeichert.

**Tatsächlicher Nachweis:** Zwei echte HTTP-Aufrufe gegen die unveränderten Controller, Dienste und Adapter auf PostgreSQL 18.6. Eine zusätzliche Testtransaktion hielt vorübergehend ausschließlich die beiden vollständigen Sitzungszeilen. Beide HTTP-Verbindungen warteten nachweislich mit `FOR SHARE OF s` auf genau deren Backend-PID, hatten also bereits ihre jeweiligen Kontosperren passiert. Nach Freigabe meldete PostgreSQL den gegenseitigen Deadlock der beiden HTTP-PIDs bei `identity_account ... FOR UPDATE`. Antworten: **200 und 500**, danach genau eines der beiden Konten mit erneut erforderlichem Passwortwechsel. Weder Rechteprüfung noch Anmeldungszustand wurden durch Testadapter ersetzt.

**Auswirkung:** Ein erlaubter Verwaltungsaufruf scheitert als Serverfehler. Die abgebrochene Transaktion wird zurückgerollt; der Versuch belegt keine unberechtigte Freigabe. DEC-064 erwähnt bereits Deadlocks bei gleichzeitiger Verwaltung des **eigenen** Kontos über zwei Wege. Dieser Versuch erweitert den nachgewiesenen Umfang auf zwei unterschiedliche Berechtigte, die ausschließlich das jeweils andere Konto verwalten. Eine allgemeine Ausnahmeregel für diese Kreuzverwaltung ist dort nicht beschrieben.

**Vorschlag:** Für Verwaltungsabläufe, die neben dem Anfragenden weitere Identitäten sperren, eine gemeinsame Reihenfolge beziehungsweise eine vorgeschaltete Serialisierung festlegen, bevor die jeweiligen gemeinsamen Identitätssperren erworben werden. Dabei auch Reaktivierung, Deaktivierung, Sitzungsdaueränderung und Anmelde-/Entzugswege berücksichtigen; nur das eigene Konto früher exklusiv zu sperren löst die umgekehrte Reihenfolge zweier Konten noch nicht. Den Kreuzfall als Regressionstest übernehmen. Die Rechteprüfung muss weiterhin bis zum Commit wirksam bleiben. Eine Korrektur und die Behandlung des dokumentierten Restrisikos sind separat zu koordinieren.

### F2 — P3: Ein Primary-Adapter umgeht die vorgeschriebene Eindeutigkeit

**Fundstellen:** [AdministrationRequests.java:49 und 61](https://github.com/1uedev/task-manager/blob/e2f0581ee8225fb0739c2f4c62eae1c85c38a455/backend/src/main/java/taskmanager/identity/web/AdministrationRequests.java#L49), [AppearanceService.java:98](https://github.com/1uedev/task-manager/blob/e2f0581ee8225fb0739c2f4c62eae1c85c38a455/backend/src/main/java/taskmanager/appearance/internal/AppearanceService.java#L98), AppearanceController.java:113 und SessionController.java:85. Maßstab: AdministrationRights.java und AdministrationRequests.java verlangen ausdrücklich genau eine Implementierung.

`ObjectProvider.getIfUnique()` ist hier keine Prüfung der Anzahl der Implementierungen: Gibt es mehrere Kandidaten und genau einen Primary-Kandidaten, wählt Spring diesen aus. Die Verbraucher führen dessen Rechteprüfung beziehungsweise Momentaufnahme aus, anstatt wegen der mehrdeutigen Verdrahtung zu verweigern. Die vorhandenen strengen Portprüfungen in AccountService und JdbcPermissions zählen dagegen die Kandidaten und lassen sich nicht auf diese Weise überstimmen.

**Tatsächlicher Nachweis:** Ein isolierter JUnit-Versuch registrierte zwei Implementierungen von AdministrationRights in einem echten DefaultListableBeanFactory, eine davon Primary, und rief die unveränderte AdministrationRequests.require-Methode auf. Beide synthetischen Adapter lassen zu. Die erwartete Ablehnung blieb aus; die Methode kehrte normal zurück. Dieser Versuch braucht keine Datenbank und ersetzt keine Implementierung in der regulären Spring-Anwendung.

**Auswirkung und Grenze:** Der zugesagte Schutz gegen mehrdeutige Adapterkonfiguration ist nicht erfüllt. Ein versehentlich zusätzlich aktivierter Primary-Adapter kann den vorgesehenen Adapter verdrängen. In der derzeitigen Produktionsverdrahtung ist nur eine Implementierung vorhanden; der Befund ist **kein Nachweis eines durch normale HTTP-Eingaben erreichbaren Rechtebypasses**.

**Vorschlag:** An allen Verbrauchern dieselbe strikte Kandidatenzählung verwenden oder die Eindeutigkeit zentral vor dem Start prüfen. Tests für keinen Adapter, genau einen Adapter, mehrere ohne Primary und mehrere mit Primary ergänzen; bei Mehrdeutigkeit auch keine Verwaltungsrechte als Sitzungshinweis ausgeben.

## Weitere Prüfergebnisse und Grenzen

| Bereich | Ergebnis der Durchsicht und tatsächlich vorhandener Nachweis |
|---|---|
| Verwaltungsaktionen | Kontenoperationen verwenden `accounts.manage`, Sitzungsdauer `session_duration.manage`, Erscheinungsbildschreiben `appearance.manage`. Die API-Rechtematrix prüft getrennte Rechte, nur `permissions.manage` und fehlende Rechte. Logo-Upload wird zusätzlich durch die vorhandenen AppearanceApiTest-Fälle abgedeckt. |
| Veralteter oder entzogener Kontext | Der neue Root-Adapter verwendet Permissions.require in der Schreibtransaktion. Rechteversion und die sperrende Identitätsprüfung bleiben beteiligt. Die bestehenden Adaptertests prüfen fremde/unvollständige Kontexte, inaktive Konten, Entzugswege und Ablauf nach Sperrwartezeit. Diese Tests liefen im gesamten Backendlauf erneut erfolgreich. |
| Konkurrenztest des Autors | Der Rechteentzugstest belegt eine reale Wartebeziehung und anschließend 403 ohne Farbänderung/Audit. Beim Erscheinungsbild läuft allerdings bereits die vorgeschaltete Momentaufnahme `held()` gegen dieselbe Rechtesperre; dieser Test allein beweist daher nicht die Notwendigkeit der zweiten, maßgeblichen Prüfung im Dienst. Deren Aufruf wurde im Code geprüft. Die Warteabfrage des Autors benennt einen blockierten SQL-Text, aber nicht den konkreten Halter. Der zusätzliche F1-Versuch bindet beide wartenden PIDs an seinen konkreten Halter. |
| Reihenfolge bei Sitzungsdauer | Die Einstellungszeile wird vor Rechten und Identität gesperrt. Der vorhandene Konkurrenztest für Anmeldung gegen Daueränderung ist grün. Er deckt den Kreuzfall F1 nicht ab. |
| Bootstrap, Notweg, letzte Verwaltung | Die ersten vier Aktionsrechte einschließlich `permissions.manage`, Gruppe und Mitgliedschaft entstehen in der gemeinsamen Transaktion; Revision und Rechteaudit erreichen sechs Einträge. CLI-, Bootstrap-Rollback- und Schutztests im Backend sind grün. Der Notweg verlangt weiterhin Unternehmensverwaltung und Serverzugang. Keine zusätzlichen realen Konten angelegt. |
| Module und Schema | Der Verwaltungsport liegt in platform; die Verknüpfung mit permissions im Anwendungseinstieg. Die Verbraucher importieren nicht permissions. V0010 entfernt die alte Spalte; das alte Anfragefeld erteilt keine Rechte. Modul- und Migrationstests liefen erfolgreich. |
| Oberfläche | Die Anzeige verwendet die vom Server gelisteten Rechte, insbesondere `appearance.manage`. Der Server prüft Änderungen selbst erneut. Vorschau und bestehende Grenzen entsprechen DEC-064. Typgenerierung, Typprüfung, 71 Tests, Build und 45 Browserprüfungen sind grün. Die Browserprüfungen verwenden simulierte API-Antworten und belegen keinen vollständigen Ablauf gegen das echte Backend. |

## Ausgeführte Prüfungen

| Prüfung | Ergebnis / Herkunft |
|---|---|
| Backend `clean verify` auf unverändertem Produkt- und Testcode | Selbst ausgeführt: **130 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen**, Build erfolgreich. Temurin 25.0.4.1+1, Maven-Wrapper 3.9.16, eigene native PostgreSQL 18.6 mit synthetischen Daten; kein Containernachweis dieses lokalen Laufs. |
| Zwei zusätzliche Prüfexperimente | Selbst ausgeführt: **2 Tests, 2 erwartete Assertion-Fehlschläge, 0 Ausführungsfehler, 0 übersprungen**. Bestätigen F1/F2; keine Produktänderung. Reproduktionsquellen und gekürzte Ergebnisse unter [review-evidence/task-040](review-evidence/task-040/README.md). |
| Wiederherstellung | Temporäre Testerweiterung bytegleich zurückgesetzt, zusätzliche Testklasse entfernt, Produkt- und Frontenddiff leer. Danach `clean test` für AdministrationRightsApiTest, IdentityPermissionsAdapterTest und AdminCommandTest, um auch alte kompilierte Prüfklassen auszuschließen: **30 Tests grün, 0 übersprungen**. |
| Frontend | Selbst ausgeführt: `npm ci --ignore-scripts`, `npm run verify` einschließlich **71 Tests**, Generator, Typprüfung und Build; `npm run test:browser` mit **45 bestandenen Tests**, keine übersprungen. Node 24.22.0; lokales Chrome. Generierte Vertragsdateien ohne Diff. |
| Repository | Selbst ausgeführt: Repository-Validator und Aufgabenübersicht erfolgreich, **25 Python-Regressionstests** grün; `git diff --check` einschließlich der veröffentlichten Dokumente. |
| CI des Ergebnis-PRs | Separat gelesen, nicht als eigener lokaler Lauf ausgegeben: [Backendlauf 39271290318](https://github.com/1uedev/task-manager/actions/runs/39271290318), **130 Tests**, PostgreSQL **18.6**, `Lifecycle check passed`. Auch Frontend, Repository und Qualifikation erfolgreich. |

Das Linux-Skript `lifecycle-check.sh` wurde lokal auf Windows nicht nochmals ausgeführt; sein erfolgreicher Start-/Stopp-/Neustartnachweis stammt aus der oben verlinkten CI. Kein Lasttest, keine vollständige Penetrationsprüfung, kein Kundendatenbestand, keine LDAP-/Portal-/Fachmodulprüfung und kein Versions-/Schwachstellenaudit aller Abhängigkeiten.

## Übergabe

Dieser PR veröffentlicht nur Bericht und reproduzierbare Nachweise. TASK-040 bleibt mit der bereits dokumentierten Abnahme in DONE.md; TODO.md und DONE.md werden hier nicht geändert. Koordination und umsetzende Sitzung können F1 und F2 als getrennte Folgearbeit bewerten und reservieren. Es wurde weder eine Korrektur eingebaut noch eine erneute fachliche Abnahme erklärt.
