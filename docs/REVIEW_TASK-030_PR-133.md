# Unabhängige Prüfung TASK-030 / PR #132 und #133

**Ergebnis:** TASK-030 ist im geprüften Stand **technisch noch nicht abnahmefähig**. Es bestehen **fünf Befunde P2 und ein Befund P3**: eine nach Feldrechteentzug unverändert sichtbare Historie, Verlust nachträglicher Eingaben beim Speichern, fehlende Schutzabfragen bei Anlageformularen, eine blockierte Neuanmeldung nach Abmeldung aus dem Pflichtwechsel, HTTP 500 bei Sperrzeitüberschreitung und veraltete Verwaltungsnavigation nach Rechteentzug. Die direkten API-Prüfungen schützen unberechtigte Zugriffe, verborgene Felder und die letzte Unternehmensverwaltung. Der Bericht ist keine fachliche Abnahme.

## Prüfgrundlage

- **Veröffentlichung am 22. Oktober 2026:** Der fertige Bericht und seine Nachweise waren bisher nur lokal vorhanden. Der Reviewbranch wurde für die Veröffentlichung auf main `124d2d81425ce4012b257049fc5c932e84ad2b44` vorgezogen. Die betroffenen Frontendquellen, `PermissionsController` und `JdbcPermissions` sind seit dem unten genannten Prüfstand unverändert (Git-Diff abgeglichen). Die damaligen Backend-/Browserläufe wurden nicht erneut ausgeführt. Die Repositorypflichtprüfungen wurden vor Veröffentlichung auf dem aktuellen Branch wiederholt: Validator, Aufgabenübersicht und Whitespaceprüfung erfolgreich; 35 Python-Tests ohne Fehler, davon einer unter Windows mangels `time.tzset` übersprungen. Die folgende Prüfung und ihre 203/68/60 Testzahlen bleiben ausdrücklich dem historischen Prüfstand zugeordnet.
- Getrennte Codex-Sitzung `codex-20261021-task030-review-01`, 21. Oktober 2026; eigener Klon, Veröffentlichungsbranch `review/task-030-pr-133`. Kein Produktcode und keine vorhandenen Tests verändert, auch nicht vorübergehend. TODO.md und DONE.md unverändert.
- Backend: [PR #132](https://github.com/1uedev/task-manager/pull/132), integriert in `4cbb0c91c8207723a49ad2c99cce4f3b7a91aab4`. Oberfläche: [PR #133](https://github.com/1uedev/task-manager/pull/133), ausgeführter Kopf `d53e347c3f6c8983e5497e99764592d67e014c75`.
- PR #133 wurde während der Prüfung als `58ffc8787588440aa65429b20836f34450854331` nach main übernommen. Beide Git-Bäume sind identisch: `c2eeac60a2a1ab1f4bc6f02247c3c8b935c54290`. Der Bericht basiert auf diesem main; die ausgeführten Tests gelten damit unveränderten Quellen. Ein Merge ersetzt keine Abnahme.
- Maßstab: [TASK-030 in TODO.md](../TODO.md#task-030-durchgängige-oberfläche-und-minimale-administration-integrieren), DEC-040 und DEC-070 einschließlich Nachtrag 8 bis 13 in [DECISIONS.md](../DECISIONS.md). Abhängigkeiten TASK-023 und TASK-029 samt vorgelagerten Verträgen; TASK-030 ist beim Autor `claude-20261021-ui-01` BEREIT ZUR PRÜFUNG. Dieser ausdrückliche Reviewauftrag übernimmt keine Implementierungsreservierung.
- Gelesen: AGENTS.md, CLAUDE.md, README.md, TODO.md, DONE.md, docs/AI_WORKFLOW.md, relevante Anforderungen und Entscheidungen, ROADMAP.md, docs/TECHNICAL_BASELINE.md, Rechte-/Fach-/API-Verträge und die beiden Komponenten-READMEs. Bezug: REQ-PROD-002, REQ-TKT-001, REQ-TAG-001, REQ-SRCH-001, REQ-I18N-001, REQ-UX-001 sowie REQ-ACL-001/003 für die geprüften Grenzen.
- Alle zusätzlichen Daten sind synthetisch und ausschließlich in einer eigenen lokalen PostgreSQL-18.6-Instanz angelegt. Browseraktionen laufen gegen das echte gebaute Backend; der gesondert gekennzeichnete Verzögerungsversuch hält ausschließlich die Übermittlung einer bereits erfolgreichen Antwort auf.

## Befunde nach Schwere

### F1 — P2: Nach Feldrechteentzug bleibt die Historie mit jetzt verborgenen Werten stehen

**Fundstelle:** `frontend/src/pages/TicketPage.tsx:121`, `:184–185`, `:268`; der Neuladezähler wird nur im Erfolgsweg `:42–45` erhöht. Die Historienabfrage hängt von Ticketkennung, Zähler und Ticketversion ab. Ein Rechteentzug ändert diese Ticketversion nicht.

**Eigener Ablauf am echten Backend:**

1. Ticket mit lesbarer Beschreibung und ihrer Änderungshistorie öffnen. Eine Beschreibungseingabe verändern.
2. Mit einem zweiten berechtigten Konto die Leseregel für `ticket.description` entfernen; die übrigen Ticketrechte bleiben bestehen. Die Feldänderung verbindlich abschließen.
3. Im ersten Browser speichern: PATCH wird korrekt mit 403 verweigert. Das Ticket wird neu geladen und zeigt „Beschreibung: für Sie verborgen“; Version bleibt 4.
4. Die Historie zeigt weiterhin `Beschreibung: SecretDescription030 wird Server changed description 030` und den ursprünglichen Beschreibungswert. Die **frische direkte Historienabfrage enthält dagegen null Beschreibungsänderungen**.

**Auswirkung:** Die Oberfläche passt eine abgeleitete Sicht nach dem festgestellten Entzug nicht an die aktuellen Feldrechte an. Es geht um zuvor rechtmäßig geladene, weiterhin dargestellte Werte, nicht um einen nachgewiesenen neuen Datenabruf an der API vorbei. Der Server filtert korrekt. Der ausgewählte Ablauf aus DEC-070 (11) und die Sichtgrenze für verborgene Felder sind in der offenen Ansicht trotzdem nicht durchgängig umgesetzt.

**Vorschlag:** Nach Rechteablehnung die vom Rechtekontext abhängigen Ticketansichten einschließlich Historie und Tagzuordnungen invalidieren und mit aktuellen Rechten laden. Bis zur Antwort keine alte, bereits als möglicherweise unzulässig erkannte Sicht weiter anzeigen. Rechtewechsel dürfen nicht allein an einer unveränderten Ticketversion hängen. Regression mit Entzug eines einzelnen Feldleserechts bei geöffnetem Ticket ergänzen.

**Nachweis:** [API-Vergleich](review-evidence/task-030/field-revocation-results.txt), [vollständiger sichtbarer Seitenbaum](review-evidence/task-030/field-revocation-ax.txt), [Ticket nach Entzug](review-evidence/task-030/07-description-hidden.png), [weiterhin sichtbare Historie](review-evidence/task-030/08-history-still-reveals-description.png).

### F2 — P2: Während einer Speicherung getippte Eingaben werden bei Erfolg gelöscht

**Fundstelle:** `frontend/src/pages/TicketPage.tsx:100–113`, `:135–149`, `:175–190`. Die Felder bleiben während `busy` editierbar; die erfolgreiche Antwort ersetzt den gesamten Entwurf durch den abgesendeten Serverstand.

**Eigener Ablauf:** Titel auf „Gesendeter Titel 030“ ändern und speichern. Ein lokaler Prüfproxy bestätigt zuerst, dass die echte PATCH-Antwort 200 bereits vorliegt, und hält deren Zustellung zurück. Währenddessen Beschreibung auf „Während des Speicherns zusätzlich getippt 030“ ändern. Danach die Antwort freigeben. Die neue Beschreibung verschwindet, stattdessen steht wieder „Server changed description 030“ im Feld; die Oberfläche meldet „Gespeichert.“ und der Speichernknopf ist deaktiviert. Die zusätzliche Beschreibung war nicht Teil des abgesendeten PATCH und wurde auch nicht gespeichert.

**Auswirkung/Vorschlag:** Bei üblicher Netzverzögerung gehen neue Benutzereingaben unbemerkt verloren. Entweder Eingaben während der laufenden Speicherung sperren oder den beim Absenden festgehaltenen Entwurf mit dem aktuellen Entwurf vergleichen und spätere Änderungen als weiterhin ungespeichert erhalten. Erfolg darf nur die tatsächlich bestätigten Änderungen als gespeichert markieren.

**Nachweis:** [Eingabe während ausstehender Antwort](review-evidence/task-030/05-inflight-draft.png), [Eingabe nach erfolgreicher Antwort verloren](review-evidence/task-030/06-inflight-input-lost.png); [Prüfproxy](review-evidence/task-030/delay_proxy.py). Kein Produktcode und keine Backendantwort verändert, nur die Zustellung verzögert.

### F3 — P2: Anlageformulare verlassen die Seite ohne Schutz ungespeicherter Eingaben

**Fundstellen:** `frontend/src/pages/ProjectsPage.tsx:49–85`, außerdem `frontend/src/pages/AccountsPage.tsx:14–39` und `frontend/src/pages/GroupsPage.tsx:17–44`. Diese Formulare halten ihre Eingaben nur lokal und registrieren keinen `useUnsavedGuard`.

**Eigener Browserablauf:** Unter Projekte „Neues Projekt anlegen“ öffnen, Schlüssel `DRAFT` und Namen „Nicht gespeichertes Reviewprojekt“ eingeben. Über die Navigation zur Suche wechseln. Es erscheint **keine Rückfrage**. Zu Projekte zurückkehren und das Formular öffnen: beide Felder sind leer. Es wurde kein Projekt angelegt.

**Auswirkung/Vorschlag:** DEC-070 (12) verspricht den Schutz bei ungespeicherten Eingaben vor einem Seitenwechsel. Er ist bisher nur für einzelne Bearbeitungsseiten umgesetzt. Die Anlageformulare an denselben Schutz anbinden; erfolgreiche Anlage muss ihn vor der anschließenden Navigation aufheben. Auch den Fenster-/Tab-Schließschutz für diese Formulare prüfen. Für Konto- und Gruppenanlage ist der gleiche fehlende Schutz statisch belegt; separat praktisch nachgestellt wurde hier die Projektanlage.

**Nachweis:** [gefüllter Projektentwurf](review-evidence/task-030/01-project-unsaved.png), [leeres Formular nach Rückkehr](review-evidence/task-030/02-project-draft-lost.png).

### F4 — P2: Abmelden aus dem Pflichtwechsel verhindert folgende Anmeldungen bis zum Neuladen

**Fundstellen:** `frontend/src/app/session.tsx:47–50`, `:64–67`; `frontend/src/pages/LoginPage.tsx:133–135`; Gegenweg `backend/src/main/java/taskmanager/identity/web/SessionController.java:89–93`, `:109–118` und `AuthenticationFilter.java:110–123`.

**Eigener Browserablauf in EN:** Ein neues synthetisches Konto mit Einmalpasswort anmelden. Es erscheint korrekt nur „Choose your password“. Dort „Sign out“ betätigen. Anschließend mit gültigen Zugangsdaten eines anderen, vollständigen Kontos anmelden: wiederholt 403, Anzeige „You do not have permission for this action.“. Ein vollständiges Neuladen der Seite ermöglicht mit denselben Zugangsdaten sofort die Anmeldung.

**Ursache und API-Gegenprobe:** Logout löscht auch das CSRF-Cookie. `signOut` stellt nur die anonyme Ansicht her. `ready` war bereits vor der Abmeldung `false`, deshalb startet der daran gebundene Brand-/Sitzungs-Reload nicht erneut. Es fehlt ein GET `/session`, das den anonymen CSRF-Nachweis neu ausstellt. Die direkte Gegenprobe ergibt ohne diesen GET **403 `auth.csrf_invalid`**, danach mit denselben Zugangsdaten **200**. Der Schutz am Server arbeitet richtig; die Oberfläche initialisiert die neue anonyme Sitzung nicht.

**Vorschlag:** Nach jeder Abmeldung unabhängig von der vorherigen Anmeldestufe den anonymen Sitzungs-/CSRF-Zustand neu beziehen und die Anmeldung erst danach freigeben. Browserregression „eingeschränkte Sitzung → Abmelden → anderes gültiges Konto anmelden“ gegen das echte Cookie-/CSRF-Verhalten ergänzen. Das Testbackend nimmt die Anmeldung ohne diese Prüfung an und bildet die Cookie-Löschung beim Logout nicht entsprechend ab.

**Nachweis:** [Pflichtwechselansicht](review-evidence/task-030/13-pending-gate-en.png), [Anmeldeablehnung](review-evidence/task-030/14-login-after-pending-logout-refused.png), [HTTP-Gegenprobe](review-evidence/task-030/logout-results.txt), [bereinigte Browser-Anfragesequenz](review-evidence/task-030/session-request-sequence.jsonl).

### F5 — P2: Rechtesperrzeitüberschreitung wird als 500 statt wiederholbares 503 beantwortet

**Fundstelle:** `backend/src/main/java/taskmanager/PermissionsController.java:265–287`; `backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java:221–240`.

**Eigener Ablauf auf PostgreSQL 18.6:** In einer separaten Transaktion `permissions_revision FOR UPDATE` sperren und halten. Den Halter über `pg_stat_activity` mit aktivem `pg_sleep` nachweisen. Parallel als berechtigter Verwalter POST `/api/v1/permissions/groups` absenden. Nach **5,05 Sekunden** folgt **500 `server.internal_error` ohne `Retry-After`**, mit Korrelationskennung. Die Transaktion des Aufrufs wird zurückgerollt.

Der JDBC-Treiber liefert SQLSTATE **55P03**, von Spring hier als `UncategorizedSQLException` verpackt. Der Controller fängt ausschließlich `PessimisticLockingFailureException`. DEC-070 (3), der OpenAPI-Vertrag und der Kommentar zu `busy()` verlangen **503 `permissions.busy` mit `Retry-After`**. Das ist ein echter Datenbankversuch, kein gemockter Exception-Test.

**Vorschlag:** Die für diese Operation vorgesehenen vorübergehenden Datenbanksperrfehler anhand der Ursachen-/SQLSTATE-Kette zentral oder entsprechend den vorhandenen Fachcontrollern behandeln. Den erzwungenen Sperrzeitablauf als HTTP-Regression prüfen; keine pauschale Umdeutung aller SQL-Fehler in 503.

**Nachweis:** [API-Ergebnis](review-evidence/task-030/api-results.txt), [ausführbare Sperrprobe](review-evidence/task-030/api_probe.py), bereinigter Exception-Typ/SQLSTATE in [Prüfprotokoll](review-evidence/task-030/README.md). Keine Ausnahmemeldung oder Stacktrace wurde an den Aufrufer zurückgegeben.

### F6 — P3: Nach 403 bleiben die Sitzungsrechte und die Verwaltungsnavigation veraltet

**Fundstellen:** `frontend/src/pages/TicketPage.tsx:65`, `:121`; `frontend/src/app/session.tsx:34–44`, `:76`; `frontend/src/app/App.tsx:168–175`.

**Eigener Ablauf:** Im geöffneten Ticket einen Titel ändern. Ein zweites Verwaltungskonto entzieht `ticket.edit` und die Mitgliedschaft in der Unternehmensverwaltungsgruppe. Der direkte Sitzungsabruf des betroffenen Kontos enthält nun `rights: []`, Speichern wird mit 403 abgewiesen. Das Ticket wechselt korrekt zur Leseansicht. Die Oberfläche zeigt aber weiterhin „Konten“ und „Gruppen und Rechte“ und behauptet „Ticket und Rechte sind neu geladen“. Auch beim Wechsel zu Gruppen bleibt die Navigation stehen; die Gruppenabfrage wird vom Server korrekt mit 403 verweigert.

**Auswirkung/Vorschlag:** Keine serverseitige Rechteumgehung nachgewiesen. Die Bedienhilfe und die ausdrückliche Rückmeldung widersprechen dem aktuellen Zustand und DEC-070 (11). Zusätzlich zum Ticket den gemeinsamen Sitzungsrechtezustand tatsächlich aktualisieren und die Navigations-/Ansichtsrechte daraus neu ableiten. Nicht lediglich den Meldungstext ändern.

**Nachweis:** [entzogene Rechte und verweigertes PATCH](review-evidence/task-030/revocation-results.txt), [veraltete Navigation nach Ablehnung](review-evidence/task-030/04-revoked-stale-navigation.png).

## Positiv geprüfte Grenzen und Verhalten

| Schwerpunkt | Eigene Beobachtung / Grenze der Aussage |
|---|---|
| Neue Rechteendpunkte | Ein vollständiges Konto ohne `permissions.manage` erhält an allen neun geprüften Operationen 403: Katalog, Gruppenliste/-detail/-anlage, Mitglied aufnehmen/entfernen, Aktion, Feldregel, Audit. Die sechs vorhandenen realen `PermissionsApiTest` laufen ebenfalls erfolgreich, einschließlich Projektumfang, sofortiger Entzugswirkung und eigenem Auditrecht. Audit bleibt bewusst an `permissions.audit.read` gebunden. |
| Verzeichnis | Vollständiges gewöhnliches Konto: 200; jeder Eintrag hat genau `id`, `username`, `displayName`, `active`. Vor Pflichtwechsel 403 `auth.password_change_required`; auch Gruppen vor Pflichtwechsel verweigert. Keine Passwort-/Sitzungs-/Rechtefelder ausgegeben. |
| Letzte Unternehmensverwaltung | Eigene echte HTTP-Proben: letzte Mitgliedschaft entfernen und letztes `permissions.manage` entziehen jeweils 409 `permissions.last_administrator`; letztes Verwaltungskonto deaktivieren 409 `account.last_administrator`. Quellprüfung: Änderungen halten exklusiv dieselbe Rechteversion, prüfen aktive verbliebene Konten und rollen den Entzug bei Ablehnung zurück. Vorhandener API-Test bestätigt außerdem 204 beim eigenen Austritt, wenn eine zweite Verwaltung bleibt. Kein neuer eigener Mehrtransaktions-Rennversuch für sämtliche Entzugswege; dies ist keine erneute vollständige Prüfung von TASK-039/041. |
| Verborgene Felder | Direkte Liste, Ticket und Historie enthalten für das eingeschränkte Konto keine Titel-/Beschreibungswerte; Suche nach diesen exklusiven Begriffen ergibt null Treffer. Eigene Tagzuordnung: Ticket-Tags 403, keine Tagänderung in der frischen Historie, null Suchtreffer über den nur dort zugeordneten Tag. Der lesbare Katalogname allein ist gemäß Vertrag kein Geheimnis der Zuordnung. In der EN-Browseransicht bleiben Titel/Beschreibung/Tags verborgen; F1 betrifft den bereits offenen Altstand nach Entzug. |
| Versionskonflikt | Echte konkurrierende Änderung nur der Beschreibung, während im Browser nur der Titel geändert wurde: 412, neue Version und neue Beschreibung geladen, eigener Titelentwurf erhalten; erneutes Speichern erfolgreich. Kein stilles Überschreiben der fremden Beschreibung. |
| Sitzung und Pflichtwechsel | Echte Browseransicht vor Pflichtwechsel ohne Fachnavigation. Tatsächlich abgelaufene synthetische Sitzungen führen beim nächsten geschützten Abruf über 401 zur Anmeldung mit übersetztem Hinweis. Dazu wurden ausschließlich die Zeitpunkte dieser Testlesersitzungen vorverlegt; keine 24 Stunden Echtzeit abgewartet. F4 betrifft den separaten Abmeldeweg aus der eingeschränkten Sitzung. |
| DE/EN | Verwaltungskonto: DE-Bearbeitung, Konflikt und Entzug; EN-Anmeldung und erfolgreiche Ticketanlage. Eingeschränktes Konto: DE/EN-Ticketansicht, EN-Liste/Suche/Sitzungsende. Oberflächentexte und Datumsformat wechseln. „Aufgabe“ und „Neu“ stammen aus der Projektkonfiguration und werden nicht als fehlende UI-Übersetzung bewertet. Vollständiger Erstwechsel über echte HTTP-Aufrufe und vorhandene Browsertests erfolgreich; im eigenen sichtbaren Browser wurde der Pflichtwechselbildschirm geprüft, der neue Passwortwert dort nicht eingegeben. |

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Unverändertes Backend `clean verify`, Java 25.0.4.1+1, Maven 3.9.16, PostgreSQL 18.6 | **203 Tests, 0 Fehler, 0 ausgelassen**, JAR erfolgreich gebaut; davon sechs `PermissionsApiTest`. Native externe Testdatenbank über dokumentierte `taskmanager.test.*`-Parameter, kein Docker. |
| Frontend `npm ci` und `npm run verify`, Node 24.22.0 / npm 11.19.1 | Erzeugung aller Vertragstypen, Typprüfung, **68 Tests erfolgreich**, Produktionsbuild erfolgreich. Erzeugte Dateien ohne Diff. |
| Unverändertes `npm run test:browser` | **60/60 Chromium-Tests erfolgreich**, gegen das vorhandene synthetische Testbackend. Diese Tests beweisen nicht das reale Backend-/Cookie-Verhalten; F4 zeigt konkret eine Lücke des Testdoubles. |
| Zusätzliche echte HTTP-/Datenbankproben | Skript und bereinigte Ergebnisse unter [review-evidence/task-030](review-evidence/task-030/README.md); F5 scheitert am vereinbarten Verhalten, die genannten API-Negativfälle halten. |
| Eigene sichtbare Browserprüfung | In-App-Chromium gegen unverändertes Frontend, echtes JAR und eigene PostgreSQL-18.6-Datenbank. Aktuelle eigene Screenshots und Protokoll; keine Übernahme der Autor-Screenshots als eigene Durchführung. |
| Repositorypflichtprüfungen | `validate_repository.py`, `task_board.py --check`, **25 Python-Tests**, `git diff --check` erfolgreich. Der erste Python-Teststart scheiterte am Zugriff auf das temporäre Sandbox-Verzeichnis; mit eigenem TEMP/TMP-Verzeichnis erfolgreich wiederholt. |

Keine neue Lastmessung, kein Docker-/Compose-Test, kein erneuter Lauf von `backend/scripts/lifecycle-check.sh`, kein Screenreader- oder weiterer Browserlauf. Das ist ein begrenztes unabhängiges Review, keine allgemeine Penetrationsprüfung. Die bereits benannten Grenzen – Konfiguration nur lesend, keine Gruppenumbenennung/-löschung, kein Auditbildschirm, angebotene Ticketanlage mit serverseitiger Ablehnung, Suchoberfläche ohne zusätzliche Fachfilter und Board als Vorschau – werden nicht als neue Befunde ausgegeben.

## Technische Übergabe

Vor technischer Freigabe F1 bis F5 beheben und gezielt mit den beschriebenen Abläufen nachprüfen; F6 ebenfalls korrigieren oder ausdrücklich durch die Koordination behandeln lassen. Die unveränderten grünen Suites ersetzen die fehlenden Regressionen nicht. TASK-030 bleibt im zentralen Aufgabenstand; der Bericht ändert keine Reservierung und erteilt keine fachliche Abnahme. Holgers Abnahme bleibt gesondert.
