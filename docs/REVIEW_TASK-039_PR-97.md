# Unabhängige Gegenprüfung TASK-039 / PR #97

19. Oktober 2026 · Codex-Sitzung `codex-20261019-adapter-review-01`.
Implementierung: `claude-20261019-integration-01`. Technische Prüfung nach DEC-040,
eigene Arbeitsverzeichnisse und Berichtsbranch `review/task-039-pr97-codex`.

**Ergebnis: ein mittlerer Ablaufbefund F1 und ein niedriger Vertragshinweis F2.**
Die 120 unveränderten Backendtests bestehen auf PostgreSQL 18.6. Die untersuchten
Schreibentzüge und der Schutz der letzten Unternehmensverwaltung sind nachvollziehbar
serialisiert. Alle 16 vorhandenen Wartebehauptungen bestehen auch mit einer zusätzlichen
Bindung an die konkrete haltende Verbindung. Beim Sitzungsablauf während einer
Sperrwartezeit ist jedoch ein unerlaubtes synthetisches Fachschreiben reproduziert.
Keine fachliche Abnahme oder allgemeine Sicherheitsfreigabe.

## Stand, Auftrag und Abgrenzung

- [PR #97](https://github.com/1uedev/task-manager/pull/97), geprüfter Head
  `18d6774290b1d1aa1972e9fceb4819d34e45f2af`; bei Prüfbeginn offener Entwurf.
- Verglichen mit main `b57a63f00210e216494daed1e86b66a821ada70d`, einschließlich des
  vollständigen Änderungsumfangs. TASK-039 ist dort Claude zugeordnet; kein eigener
  Implementierungsclaim und keine Übernahme dieser Zuständigkeit. Diese Gegenprüfung
  ist ausdrücklich von Holger beauftragt.
- Maßgeblich: TASK-039 und Claim #94, DEC-040, DEC-006, DEC-062 und der im Ergebnis-PR
  vorgeschlagene DEC-063; REQ-ACL-001, REQ-ACL-003, REQ-AUTH-001, REQ-AUTH-003,
  REQ-ORG-001 und REQ-SEC-001. Die technische Ausgestaltung von DEC-063 wird hier
  geprüft, nicht als bereits von Holger abgenommen dargestellt.
- Gelesen: AGENTS.md, README.md, TODO.md, DONE.md, AI_WORKFLOW.md, CLAUDE.md,
  TECHNICAL_BASELINE.md, relevante Anforderungen, Entscheidungen, Roadmap,
  Architektur-/Modulverträge, Identitäts-/Rechtedokumentation, Produktquellen,
  Migration, geänderter Identitätsvertrag, Tests und Lifecycle-Skript.
- Die vorläufige Administratorkennzeichnung für Identität und Erscheinungsbild,
  fehlende Fach-/Projektadapter sowie die nicht nachgerüsteten alten
  Entwicklungsdatenbanken bleiben die vereinbarten Grenzen. Kein Auftrag für
  TASK-026 bis TASK-031. Die früheren Erscheinungsbildbefunde werden nicht erneut geprüft.

Der Berichts-PR ändert ausschließlich `docs/`. PR #97, Produktcode, TODO.md, DONE.md,
Zuständigkeiten und Blockierungen bleiben unverändert. Sämtliche lokalen Experimente
am separaten Prüfstand wurden zurückgenommen.

## F1 · Mittel / P2 · Sitzungsablauf während der Wartezeit wird mit altem Zeitpunkt geprüft

**Fundstelle:** [JdbcIdentityVerification.java:46–59](https://github.com/1uedev/task-manager/blob/18d6774290b1d1aa1972e9fceb4819d34e45f2af/backend/src/main/java/taskmanager/identity/session/JdbcIdentityVerification.java#L46),
ergänzend die Aussage zum Commit in [backend/README.md:114](https://github.com/1uedev/task-manager/blob/18d6774290b1d1aa1972e9fceb4819d34e45f2af/backend/README.md#L114).

`now` wird nach der Kontosperre, aber **vor** der möglicherweise wartenden
Sitzungsabfrage erfasst. Die nach dem Warten gelesene Sitzung wird dann mit diesem
alten Zeitpunkt verglichen. Die Zeilensperre stabilisiert die Daten, nicht die Uhr.

**Eigene Reproduktion mit echter PostgreSQL-Wartebeziehung:**

1. Vollständige Anmeldung, aktueller Rechtekontext und erlaubtes `ticket.read`.
   Die kontrollierte Uhr steht 59 Minuten und 59 Sekunden nach dem Sitzungsbeginn.
2. Eine Transaktion verkürzt die Dauer von 24 auf eine Stunde und bleibt vor ihrem
   Commit offen. Das neue Sitzungsende liegt noch eine Sekunde in der Zukunft.
3. Ein synthetischer Fachauftrag ruft `Permissions.require` auf. PostgreSQL bestätigt,
   dass dessen `FOR SHARE OF s` auf genau diese Verkürzungstransaktion wartet.
4. Während der bestätigten Wartebeziehung wird die Testuhr um zwei Sekunden
   weitergestellt. Anschließend committet die Verkürzung.
5. Die wartende Prüfung gibt trotzdem frei; **ein Datensatz in der synthetischen
   Fachtabelle wird committet**. Eine unmittelbar anschließende neue Prüfung desselben
   Kontexts verweigert bereits. Die Rechteversion wurde dabei nicht verändert.

Nachweis: `exactWait=true oldCheckAllowed=true freshAllowed=false committedEffects=1`.
Die Gegenprobe scheitert gezielt an `expected 0, actual 1`, ohne Ausführungsfehler.
Die Sekunden werden über die bestehende `MutableClock` modelliert; nicht behauptet
ist eine zweistündige echte Sperrwartezeit oder ein neues produktives Fachmodul.

**Auswirkung:** Eine Prüfung kann einen bereits bei ihrer Freigabe abgelaufenen
Kontext verwenden. Die fünf Sekunden `lock_timeout` begrenzen die einzelne
Sperrwartezeit, verhindern aber das Überschreiten des Sitzungsendes in dieser Zeit nicht.
Die bestehende Gegenprüfung der Verkürzung stellt die Uhr vor dem Start des wartenden
Checks weiter; sie erfasst diesen anderen Ablauf nicht.

**Vorschlag:** Den aktuellen Zeitpunkt erst nach Erwerb der entscheidenden Sperren
und Lesen der aktuellen Sitzungsdaten prüfen; diesen Wartefall als Regression aufnehmen.
Ein ausschließlich lokaler Ursachenversuch mit `clock.instant()` im Row-Mapper
verweigert denselben Auftrag: `oldCheckAllowed=false`, `committedEffects=0`, Test grün.
Das ist ein eingegrenzter Nachweis, kein gelieferter Produktfix.

Zusätzlich die Zeitsemantik zwischen erfolgreicher Prüfung und anschließendem
Fachcommit explizit festlegen: Auch nach dieser kleinen Korrektur kann die Uhr während
späterer Facharbeit weiterlaufen. Eine Gültigkeit **zum Commitzeitpunkt**, wie README.md
sie formuliert, ist durch die gezeigten Sperren allein nicht belegt. Falls das verlangt
wird, braucht es eine entsprechende Abschlussprüfung und einen eigenen Grenztest;
andernfalls muss die Dokumentation den tatsächlichen Prüfzeitpunkt benennen. Diese
fachliche Festlegung trifft die Koordination, nicht dieser Bericht.

## F2 · Niedrig / P3 · Rückgabevertrag für unvollständige Anmeldung widerspricht dem Builder

**Fundstellen:** [PermissionContexts.java:19–20](https://github.com/1uedev/task-manager/blob/18d6774290b1d1aa1972e9fceb4819d34e45f2af/backend/src/main/java/taskmanager/permissions/PermissionContexts.java#L19)
und [RequestPermissionContexts.java:34–48](https://github.com/1uedev/task-manager/blob/18d6774290b1d1aa1972e9fceb4819d34e45f2af/backend/src/main/java/taskmanager/RequestPermissionContexts.java#L34).

Der Port verspricht `Optional.empty()` bei unvollständiger Anmeldung. Der Builder
liefert bei vorhandener Identität und Firma dagegen einen Kontext mit
`passwordChangeRequired=true`, wenn die Anmeldestufe nicht FULL ist.

**Einordnung:** Statisch bestätigter Vertragswiderspruch, kein nachgewiesener
Fachzugriff: `JdbcPermissions.lock` und der echte Adapter verweigern solche Kontexte.
Die bestehenden Laufzeittests prüfen die Verweigerung; dieser Builder-Sonderfall
wurde nicht zusätzlich als eigene HTTP-Gegenprobe ausgeführt.

**Vorschlag:** Entweder den Builder entsprechend dem Portvertrag früh leer antworten
lassen oder den Vertrag ausdrücklich auf markierte, noch nicht verwendbare Kontexte
ändern. Den gewählten Fall direkt testen. `current()`/`require()` des Kontextports
dürfen dabei weiterhin nicht als Ersatz für `Permissions.require` verstanden werden.

## Prüfung der fünf Schwerpunkte

### 1. Sperrreihenfolge und Entzugswege

`JdbcPermissions.lock` nimmt vor der Identitätsprüfung die Rechteversion unter
`FOR SHARE` beziehungsweise für Administration unter `FOR UPDATE`. Es verlangt
READ COMMITTED und hält die Sperren in der Transaktion des Aufrufers. Der echte
Adapter und `IdentityVerification` verlangen MANDATORY, kein REQUIRES_NEW.

| Weg | Untersuchte Sperren und Ergebnis |
|---|---|
| Fachprüfung | Rechte → Konto `FOR SHARE` → Sitzung `FOR SHARE`; `Permissions.require` verlangt eine bestehende Fachtransaktion. Ein separat abgeschlossener `inspect`-Aufruf bleibt nur eine Bedienhilfe. |
| Abmelden | Ändert ausschließlich die Sitzung; kollidiert mit der gemeinsamen Sitzungssperre. Kein nachfolgender Konto-/Rechteerwerb in diesem Weg. Beide Reihenfolgen geprüft. |
| Deaktivieren | Schranke mit exklusiver Rechteversion **vor** Advisory-/Kontosperre, danach Konto `FOR UPDATE` und Sitzungswiderruf. Beide Reihenfolgen geprüft. |
| Administratives Zurücksetzen | Konto `FOR UPDATE`, Passwort-/Pflichtwechseländerung, Sitzungswiderruf in derselben Transaktion. Beide Reihenfolgen geprüft. |
| Passwortwechsel | Dauereinstellung gemeinsam, dann Konto exklusiv, anschließend Widerruf alter Sitzungen und Ersatzsitzung. Die neue Verifikation sperrt die Dauereinstellung nicht nachträglich und erzeugt damit keine Gegenkante Sitzung → Einstellung. Beide Reihenfolgen geprüft. |
| Fristverkürzung | Dauereinstellung exklusiv, dann UPDATE der offenen Sitzungen. Die gemeinsam gesperrte Sitzung hält den Entzug bis zum Fachcommit auf. Beide Schreibreihenfolgen geprüft; zeitlicher Restfehler siehe F1. |
| Notfall-Administratorreset | Konto exklusiv, gegebenenfalls Reaktivierung, Passwort-/Pflichtwechseländerung und Widerruf. Zusätzlich selbst in beiden Reihenfolgen mit konkreter PostgreSQL-Blockerkante geprüft; alte Kontexte danach verweigert. |
| Reaktivierung | Ändert das Konto exklusiv. Stellt zuvor widerrufene Sitzungen nicht wieder her; bestehender Laufzeittest bestätigt nötige Neuanmeldung. |

In diesen untersuchten Abläufen kein beobachteter Deadlock und keine neue
Sperrumkehr gefunden. Das ist kein Beweis für beliebige zukünftige Kombinationen,
mehrere Identitätsmutationen in einer Fachtransaktion oder unbegrenzt viele Sitzungen.
Eine natürliche Fristüberschreitung ist außerdem kein Schreibentzug mit sperrbarem Commit.

### 2. Alle Eigenschaften des Kontexts

| Eigenschaft | Prüfung im untersuchten Produktpfad |
|---|---|
| Firma | `CompanyContext` liest die eine serverseitige Firma; Adapter vergleicht sie. Ohne Firma oder bei fremder Firma wird verweigert. HTTP-Probetest ignoriert vom Client behauptete Firmenkennungen. |
| Konto | Existenz, `active` und fehlender Pflichtwechsel unter gemeinsamer Kontosperre. |
| Sitzung | Kennung **und** Kontozuordnung, nicht widerrufen, nicht eingeschränkt; effektives Ende ist das kleinere aus gespeichertem Ende und Beginn plus aktueller Dauer. Zeitfehler siehe F1. |
| Rechteversion | Liegt absichtlich im Rechtemodul: Vergleich nach Erwerb der Revisionssperre, vor Policy-Lesen und Identitätsfreigabe. Kein fehlender Versionsvergleich nur deshalb, weil der Adapter selbst ihn nicht wiederholt. |
| Behaupteter Pflichtwechsel | Verweigerung im Rechtemodul und Adapter; falsches `false` ersetzt nicht die erneute Datenbankprüfung des Kontos. |
| Extern / Organisation | Adapter verlangt `external=false` und `organizationId=null`; derzeit keine Portalkonten. |

`userExists` zählt bekannte Konten auch inaktiv, `userActive` nur aktive interne
Konten; beide sperren die Kontozeile bis zum Aufrufercommit. Für die einzige Firma
der Installation ist keine vom Browser gelieferte Mandantenzuordnung wirksam.
Fehlende/mehrere `IdentityAccess`-Implementierungen verweigern; die vorhandene Prüfung
testet auch einen zusätzlichen Primary-Bean. Der Produktkontext registriert genau den
echten Adapter. Die Fixture-Ersetzung ist auf `PermissionsIntegrationTest` begrenzt.

### 3. Letzte Unternehmensverwaltung gegen `setMember` und `setAction`

`AccountService.deactivate` fragt die Schranke vor jeder eigenen Sperre. Der
Anwendungseinstieg ruft `CompanyAdministration.remainsWithout` in derselben Transaktion
auf. Diese Methode nimmt dieselbe exklusive Rechteversion wie Rechteänderungen;
Mitgliedschaft und Unternehmensaktion können während ihrer Entscheidung nicht wechseln.
Sie zählt andere aktive Inhaber von `permissions.manage` ohne Projektbezug und hält
deren Kontosperren. Kein Zugriff des Identitätsmoduls auf Berechtigungstabellen.

Die vier Reihenfolgen Deaktivieren ↔ Mitgliedschaft entfernen und Deaktivieren ↔
Unternehmensaktion entziehen bestehen einschließlich der anschließend erwarteten
Verweigerung und erhaltenen Verwaltung. Die verschärfte Prüfung bestätigt dabei
jeweils die konkret haltende Rechte-Transaktion. Der zusätzliche Marker-Schutz in der
Identität bleibt erhalten; er ist kein Ersatz für diese Firmenschranke. Fehlender
oder mehrdeutiger Schrankenport verursacht eine Ausnahme und Rollback, keine Freigabe.

### 4. Atomarer Start und `platform_company`

V0009 gehört der Plattform. NOT NULL, CHECK auf den konstant wahren Wert und UNIQUE
auf `only_company` erlauben höchstens eine Zeile, ohne Seed oder vom Client gelieferte
ID. `CompanyContext.create` verlangt eine bestehende Transaktion und erzeugt die UUID.

`bootstrapAdministrator` serialisiert über die exklusive Identitätstabelle und
verweigert bei vorhandenen Konten. Konto, Firma, Rechteversion, genau eine Gruppe,
die Unternehmensaktion `permissions.manage`, Mitgliedschaft und Audit werden über
MANDATORY-Ports in derselben Transaktion geschrieben. Ergebnisversion 3, kein
Fach-/Auditrecht und keine zusätzliche Gruppe werden in den Tests kontrolliert.

Zusätzlich zum vorhandenen frühen Fehlerfall selbst geprüft: Eine ausschließlich in
der Testdatenbank gesetzte ablehnende Mitgliedschaftsconstraint lässt den Start erst
**nach** Konto, Firma, Gruppe und Aktion scheitern. Danach sind alle acht beteiligten
Konto-/Firmen-/Rechte-/Audittabellen leer. Zwei gleichzeitig gestartete Bootstraps
warten nachgewiesen aufeinander; nach dem ersten Commit wird der zweite mit
`bootstrap.accounts_exist` verweigert, genau eine Initialisierung bleibt erhalten.

### 5. Aussagekraft der Tests

Die Autorentests halten Transaktionen mit Latches offen und suchen in
`pg_stat_activity` nach dem erwarteten SQL und nichtleeren `pg_blocking_pids`.
Das ist eine echte Datenbank-Wartebeziehung, keine alleinige Schlafzeitbehauptung.
Der originale Helfer bindet die Blockerliste allerdings nicht an die bekannte
Halter-PID; sein grüner Befund ist im seriellen Lauf mit eigener Datenbank sinnvoll,
bei späterer Parallelisierung wäre diese Bindung wichtig.

Eigenständig ergänzt: Die bekannte Halter-PID wird in `hold` auf derselben
Transaktionsverbindung erfasst; `waitsAt` verlangt zusätzlich deren Vorkommen in
`pg_blocking_pids` der wartenden Verbindung. **Alle 16 Wartebehauptungen und alle 17
vorhandenen Adaptertests bestehen damit.** Die eigenen Ablauf-, Bootstrap- und
Notfallproben binden Warte- und Halterverbindung direkt aneinander. F1 bleibt auch
unter dieser strengeren Beobachtung reproduzierbar.

## Tatsächlich ausgeführte Prüfungen und Grenzen

Eigene Windows-Läufe: Temurin 25.0.4.1+1, Maven 3.9.16, separate native PostgreSQL 18.6
auf Loopback, ausschließlich synthetische Daten. Vorgesehene Java-Systemparameter
für eine externe Testdatenbank verwendet; kein eigener Docker-Nachweis.

| Eigene Ausführung | Ergebnis |
|---|---|
| Unveränderter PR-Head, Backend `verify` | **120 bestanden**, 0 Fehlschläge/Fehler/übersprungen, Build erfolgreich. |
| Vier zusätzliche Proben, unveränderter Produktcode | **3 bestanden, 1 gezielter Fehlschlag F1**, 0 Ausführungsfehler/übersprungen. |
| Alle Adaptertests plus vier Proben mit konkreter Halter-PID | **21 Tests: 20 bestanden, nur F1 fehlgeschlagen**, 0 Ausführungsfehler/übersprungen; 16 konkrete Halterbeziehungen bestätigt. |
| Lokaler späterer Prüfzeitpunkt, nur F1-Gegenprobe | **1 bestanden**; Auftrag verweigert, 0 synthetische Fachdatensätze committet. Änderung danach zurückgenommen. |
| Bytegenaue Wiederherstellung, frische Kompilierung | `clean test` für `IdentityPermissionsAdapterTest,PermissionContextsProbeTest`: **18 bestanden**, keine Fehler/Fehlschläge/übersprungen. Keine Gegenproben oder Versuchsänderung verbleiben im Produktprüfstand. |
| Repository des Berichts | Validator, Aufgabenübersicht, **25 Python-Tests mit `PYTHONUTF8=1`**, Diffprüfung einschließlich neuer Dateien erfolgreich. |

Ein vorbereitender Korrekturversuch fing die verweigerte Rechteprüfung innerhalb der
bereits rollbackpflichtigen Transaktion ab und endete deshalb mit
`UnexpectedRollbackException`. Die beigefügte und danach ausgeführte Fassung behandelt
die Verweigerung außerhalb der Transaktion; dieser Einrichtungsfehler ist kein weiterer
Produktbefund.

**Gelesene fremde CI-Nachweise, getrennt von eigenen Läufen:** Auf dem geprüften Head
sind alle vier Workflows erfolgreich. Im [Backendlauf 39255208031](https://github.com/1uedev/task-manager/actions/runs/39255208031)
wurden Jobschritte und die relevanten Protokollstellen nachgelesen: 120 Tests ohne
Fehler/übersprungene Tests, PostgreSQL 18.6 und `Lifecycle check passed` für den
gepackten Start/Stopp/Neustart samt Bootstrap. Das ist CI-Evidenz, kein eigener lokaler
Bash-/SIGTERM-Lauf. Frontendtests wurden für diesen Bericht nicht lokal wiederholt;
seine einzige Frontendänderung ist eine erzeugte Kommentarzeile des Identitätsvertrags.

Nicht nachgewiesen: beliebige Last-/Deadlockfreiheit, alle zukünftigen Fachtransaktionen,
Produktbetrieb, LDAP, Portal, Projektadapter oder Ablösung der Administratorkennzeichnung.
Die behauptete Zeitgültigkeit bis zum Fachcommit bleibt wie bei F1 beschrieben offen.
Die in PR #97 genannten Autorenmutationen wurden nicht als eigene Mutationstests wiederholt.

Beigefügt: [bereinigte Ergebnisse](review-evidence/task-039/results.txt),
[vier Reviewer-Proben](review-evidence/task-039/reviewer-probes.java.txt),
[strengere Halterprüfung](review-evidence/task-039/wait-holder-instrumentation.diff.txt),
[zurückgenommener Zeitversuch](review-evidence/task-039/expiry-time-experiment.diff.txt).
Die Fragmente sind Nachweise für den beschriebenen Prüfstand, keine aktiven Produkttests
oder als vollständig abgenommen angebotene Korrektur.
Der Halterpatch verwendet Nullkontext (`git apply --unidiff-zero`).

## Übergabe

F1 korrigieren, die Zeitsemantik ausdrücklich festhalten und den Wartefall unabhängig
nachprüfen. F2 zwischen Port und Builder vereinheitlichen. Die übrigen untersuchten
Sperr-/Schranken-/Bootstrap-Abläufe sind im beschriebenen Umfang positiv belegt.
Aufgabenstatus und fachliche Abnahme bleiben bei der Koordination beziehungsweise Holger.
