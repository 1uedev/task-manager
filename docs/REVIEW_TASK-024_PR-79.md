# Gezielte Sicherheitsnachprüfung TASK-024 / PR #79

19. Oktober 2026 · Codex-Sitzung `codex-20261019-identity-followup-01`.
Gegenseitige technische Prüfung nach DEC-040 der von Claude umgesetzten Korrekturen.
Ausgangspunkt: [Erstbericht zu PR #75](REVIEW_TASK-024_PR-75.md), Befunde R1 bis R5.

**Ergebnis:** Die ursprünglichen Einzelabläufe sind korrigiert; alle 83 bestehenden
Backendtests einschließlich der zehn neuen Regressionstests bestehen im eigenen Lauf.
Zwei ergänzende Gegenproben zeigen jedoch Restlücken bei R2 und R3: **N1 hoch, N2 mittel**.
Eine vorbehaltlose technische Schließung aller fünf Befunde ist deshalb nicht belegt.

Die bereits in [PR #80](https://github.com/1uedev/task-manager/pull/80) dokumentierte fachliche
Annahme durch Holger wird nicht umgeschrieben. Dieser Bericht ergänzt neue technische
Nachweise; TODO.md, DONE.md, Zuständigkeiten und Blockierungen bleiben unverändert.
Claudes laufende Nachbesserung von TASK-025 / PR #82 wird nicht übernommen.

## Stand und Umfang

- Korrektur-PR: [#79](https://github.com/1uedev/task-manager/pull/79).
- PR-Head: `eaa9c355c2192218b648a465d5570f6cd1e26d62`.
- Integrierter Korrekturcommit: `5381db391bab152497889f9b1065d507d089a41c`.
- Lokal geprüft: main `370a69cec0c6b4bfc8432f04247d40870a17ede7`.
- Eigener Git-Vergleich: Backend, Verträge und Backendworkflow sind zwischen diesen drei
  Ständen identisch. Die folgenden main-Commits ändern Annahme-/Reservierungsdokumentation.

Gelesen wurden die aktuellen gemeinsamen Arbeitsregeln und Einstiegsdateien,
TECHNICAL_BASELINE.md, DEC-040/DEC-058, relevante Anforderungen und Verträge, der Erstbericht,
der vollständige Korrekturdiff, die betroffenen Identitätsklassen, V0004 und
`IdentityHardeningTest`. Schwerpunkt dieser Nachprüfung sind die fünf vorherigen Befunde
und die durch ihre Korrektur berührten Übergänge. Keine neue Produktfunktion oder Abnahme.

## Einordnung der ursprünglichen Befunde

| Befund | Nachgeprüfte Korrektur | Bewertung |
|---|---|---|
| R1, Login überlebt Reset | Konto wird vor Sitzungsanlage unter Zeilensperre erneut gelesen; Hash-/Aktivstatus müssen passen. Volle Sitzung bei offenem Pflichtwechsel wird abgewiesen. | Für den beanstandeten Resetablauf adressiert; die beiden Regressionstests bestehen. |
| R2, 24 → 1 → 24 reaktiviert Token | Verkürzung schreibt die Ablaufgrenze in vorhandene offene Sitzungen; Login liest die Einstellung mit geteilter Sperre. | Sequenzieller Ablauf korrigiert; konkurrierende Passwortrotation bleibt offen, siehe N1. |
| R3, wiederholte Einmalanmeldung | `one_time_used_at` wird unter Kontosperre mit der ersten Sitzungsanlage gespeichert. | Neue Konten und parallele Anmeldungen abgesichert; bestehende V0003-Sitzungen beim Update nicht übernommen, siehe N2. |
| R4, URI-Varianten umgehen Schutz | Filter verwendet Spring-Pfadsegmente statt des rohen URI-Präfixes. | Die beanstandeten Matrix-/Kodierungsvarianten sowie weitere Varianten und Login-CSRF werden von den bestandenen HTTP-Tests abgedeckt. Keine universelle URL-/Browser-Sicherheitszusage. |
| R5, Adressschwelle 20 statt 5 | Standard nun fünf; Test wechselt Anmeldenamen bei gleicher Quelladresse. | Bestätigter Standard und negativer Fall adressiert. |

## N1 – hoch: Passwortrotation kann die dauerhafte Fristverkürzung umgehen

**Fundstellen:** [SessionService.java:238–249](../backend/src/main/java/taskmanager/identity/session/SessionService.java#L238)
und [SessionDuration.java:52–57](../backend/src/main/java/taskmanager/identity/session/SessionDuration.java#L52).

`replacePassword` liest den bisherigen Ablauf, widerruft die alten Sitzungen und legt eine
Ersatzsitzung mit dem zuvor gelesenen Ablauf an. Dieser Anlageweg nimmt die für den Login
ergänzte gemeinsame Sperre auf der Einstellung nicht. Das gleichzeitig laufende
Verkürzungs-UPDATE kann dadurch die Ersatzsitzung in seinem Anweisungssnapshot verpassen.

**Eigene deterministische Gegenprobe:**

1. Konto mit gewähltem Passwort und voller 24-Stunden-Sitzung anlegen.
2. Die alte Sitzungszeile vorübergehend in einer Instrumentierungstransaktion sperren.
3. Den öffentlichen Passwortwechsel starten. Sein Widerrufs-UPDATE wartet nach dem Lesen
   des alten Ablaufs nachweislich auf diese Zeile.
4. Parallel die öffentliche Einstellungsänderung auf eine Stunde starten. Auch deren
   Sitzungs-UPDATE wartet nachweislich. Beide Wartezustände werden mit `pg_blocking_pids`
   und den tatsächlichen SQL-Anweisungen geprüft, nicht nur durch Wartezeiten vermutet.
5. Instrumentierungssperre lösen. Passwortwechsel und Einstellungsänderung beenden ihre
   unveränderten Produkttransaktionen erfolgreich.

Danach gilt die Einstellung eine Stunde, die Ersatzsitzung speichert aber weiterhin 24
Stunden. Die Auflösung begrenzt sie zunächst auf eine Stunde. Nach zwei Stunden wird sie
korrekt als abgelaufen abgewiesen. Wird die Einstellung anschließend wieder auf 24 Stunden
gesetzt, liefert **dasselbe Ersatz-Token wieder `FULL`**.

Die finale Probe ruft `SessionService.changePassword` und `SessionDuration.change` direkt
ohne zusätzliche äußere Transaktion auf. Nur die Instrumentierung setzt eine Sperre zum
Erzwingen der Reihenfolge; Produktmethoden und Transaktionsgrenzen sind unverändert.
Ein Angreifer braucht für das zeitliche Rennen keinen Datenbankzugang; die Sperre macht
den ansonsten zeitabhängigen Ablauf im Test deterministisch.

**Auswirkung:** Die bestätigte feste Frist und der dauerhaft wirksame Entzug durch Verkürzung
sind nicht über alle Sitzungsanlagewege abgesichert. Ein kopiertes Token kann nach Ablauf
wieder gelten. Dies ist ein verbliebener Konkurrenzfall von R2, kein Fehler des noch
ausstehenden Berechtigungsadapters.

**Vorschlag:** Alle Sitzungsanlage-/Rotationswege gegen Einstellungsänderungen serialisieren,
beispielsweise dieselbe Einstellungssperre in fester Reihenfolge **vor** Konto-/Sitzungssperren
auch im Passwortwechsel halten und die neue Ablaufgrenze konsistent begrenzen. Beide
Reihenfolgen mit echter Sperrwartebeziehung negativ prüfen. Die alleinige Nachlese des
aktuellen Stundenwerts ohne gemeinsamen Transaktionsschutz genügt nicht.

Die [PostgreSQL-18-Dokumentation zu READ COMMITTED](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED)
erklärt die Mechanik: Ein UPDATE findet seine Zielzeilen anhand des Anweisungssnapshots;
nach einer Wartezeit neu eingefügte andere Zeilen werden nicht automatisch hinzugefügt.
Der konkrete Produktfehler wurde zusätzlich mit der beschriebenen Probe ausgeführt.

## N2 – mittel: Migration übernimmt bereits verbrauchte Einmalanmeldungen nicht

**Fundstelle:** [V0004__identity_one_time_consumption.sql:4–6](../backend/src/main/resources/db/migration/V0004__identity_one_time_consumption.sql#L4).

Die Migration ergänzt `one_time_used_at` leer. Für ein Konto, das schon unter V0003 mit
seinem Einmalpasswort erfolgreich angemeldet war, bleibt das Feld trotz vorhandener
eingeschränkter Sitzung `NULL`. Der korrigierte Login behandelt den Nachweis daher als
noch unbenutzt.

**Eigene Upgradegegenprobe:** Eine neue PostgreSQL-Datenbank wurde mit Flyway ausdrücklich
nur bis V0003 migriert. Dort wurde ein synthetischer, schema-konformer Altzustand angelegt:
Konto mit gültigem Einmalpasswort-Hash und eine bereits gestartete eingeschränkte Sitzung.
Es wurde kein neues V0004-Feld manuell simuliert. Anschließend führte Flyway den echten
Updateweg auf V0004 aus, und die aktuelle Anwendung startete auf dieser Datenbank.

Die alte Sitzung ist noch gültig. Dennoch akzeptiert der Login dasselbe bereits für ihre
Anmeldung verwendete Einmalpasswort erneut; anschließend existieren zwei offene
eingeschränkte Sitzungen. Es wurde kein neuer Zugangsnachweis administrativ ausgegeben.

**Auswirkung und Grenze:** Die Erstverwendung ist auf frischen Daten abgesichert, bei
übernommenen Konten jedoch nicht. Der wiederverwendete Nachweis öffnet weiterhin nur den
Pflichtwechsel; eine volle Sitzung ohne diesen Wechsel wird hier nicht behauptet. Der
Altzustand wurde synthetisch angelegt, nicht durch Ausführung eines alten Anwendungspakets;
Schema und Migration wurden tatsächlich durch Flyway ausgeführt.

**Vorschlag:** Eine abgestimmte **neue Vorwärtsmigration** beziehungsweise ein dokumentierter
Update-/Neu-Ausgabepfad muss solche Bestandszustände konservativ behandeln. Verbrauch nur
aus zuverlässig zum aktuellen Nachweis gehörenden Sitzungsdaten ableiten; bei Uneindeutigkeit
keinen benutzbaren Nachweis still wieder freigeben. Die bereits übernommene V0004 nicht ändern
und keine von Claude reservierte Migrationsnummer übernehmen. Upgradefälle zusätzlich zu
Tests mit frisch angelegten Konten prüfen.

## Tatsächlich ausgeführte Nachweise

| Prüfung | Ergebnis | Grenze |
|---|---|---|
| Eigener unveränderter Backendlauf `verify` | 83 Tests, 0 Fehler/Fehlschläge/übersprungen; Build erfolgreich | Windows, Temurin 25.0.4.1+1, Maven 3.9.16, native PostgreSQL 18.6 über die vorgesehene externe Testdatenbankkonfiguration; kein lokaler Docker-Digestnachweis |
| Zehn Regressionstests des Autors | Im obigen Gesamtlauf bestanden, einschließlich Resetrennen, Einmalverbrauch, URI-/CSRF-Fällen und Adressschwelle | Belegen ihre konkreten Abläufe, nicht die zusätzlichen N1-/N2-Fälle |
| Zwei eigene finale Gegenproben | N1 und N2 reproduziert; zwei Prüfmethoden ohne Ausführungsfehler | Assertionen erwarten absichtlich den beobachteten Fehler. Ein grüner Probelauf ist keine Sicherheitsfreigabe |
| CI [39153055006](https://github.com/1uedev/task-manager/actions/runs/39153055006) auf `eaa9c35` | Vollständiges Protokoll gelesen: 83 erfolgreiche Tests, Build und Lifecycle erfolgreich | Gelesener Nachweis des Autors; kein eigener lokaler Bash-/SIGTERM-Lauf |
| Repositoryvalidator, Aufgabenübersicht, Python-Tests und Diffprüfung | Validator und `task_board.py --check` erfolgreich; 25 Python-Tests bestanden; `git diff --check` erfolgreich | Erster Python-Testanlauf scheiterte am temporären Sandboxverzeichnis; Wiederholung mit eigenem beschreibbarem Tempverzeichnis erfolgreich |

Die [vollständige Testquelle](review-evidence/IdentityFixesFollowupProbeTest.java) und die
[gekürzten tatsächlichen Ausgaben](review-evidence/TASK-024-followup-79-observations.txt)
liegen bei. Die Klasse wurde nur vorübergehend in den Backendtestpfad kopiert und danach
entfernt. Unter `docs/` wird sie vom normalen Backendbuild nicht ausgeführt. Zum Wiederholen
in einem eigenen Checkout mit leerem Testserver und Berechtigung zum Anlegen von Testdatenbanken:

```sh
cp docs/review-evidence/IdentityFixesFollowupProbeTest.java backend/src/test/java/taskmanager/identity/
cd backend
./mvnw --batch-mode --no-transfer-progress -Dtest=IdentityFixesFollowupProbeTest \
  -Dtaskmanager.test.jdbc-url="$REVIEW_DB_URL" \
  -Dtaskmanager.test.username="$REVIEW_DB_USER" \
  -Dtaskmanager.test.password="$REVIEW_DB_PASSWORD" test
```

Danach nur die hinzugefügte Testkopie entfernen und vor einem regulären Gesamtlauf `clean`
verwenden. Die lokalen Testdatenbanken enthalten ausschließlich synthetische Daten und
laufen isoliert auf Loopback. Die native Laufzeit stammt aus dem beim Erstbericht genannten
EDB-Archiv; keine neue Version oder Produktabhängigkeit eingeführt.
Der eigene Testserver wurde nach Sicherung der Nachweise beendet; die erzeugten
Backend-Builddateien wurden bereinigt.

## Übergabe

R1, R4 und R5 sind für die beanstandeten Abläufe nachgeprüft. R2 und R3 benötigen die
beschriebene Ergänzung, bevor sie ohne Einschränkung technisch geschlossen werden können.
Die Koordination weist die Folgekorrekturen zu; danach gezielte Nachprüfung am benannten
Korrekturcommit. Die fachliche Annahme bleibt Holgers Entscheidung und historisch erhalten.

Nicht selbst ausgeführt: vollständiger Browser-CSRF-Angriff, Last-/Schwachstellenscan,
lokales Lifecycle-Bashskript sowie echte LDAP-/Portal-/Berechtigungsadapter. Die bekannte
Adapterlücke bleibt gesonderte Integrationsarbeit. Dieser Bericht implementiert keine
Folgefunktion, ändert keine Produktquelle und hebt keine Aufgabenblockierung auf.
