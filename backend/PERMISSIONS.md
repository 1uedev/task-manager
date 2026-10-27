# Berechtigungsmodul (TASK-025)

Stand 18. Oktober 2026 · Implementierungs- und Integrationsvertrag zur Prüfung.
Die bestätigten Regeln stehen in DEC-057 und im reservierten TASK-025 (Claim-PR #69).
Die folgenden Java-Typen, Aktionsschlüssel, Sperren und Auditgrenzen sind die technische
Ausgestaltung dieses Ergebnis-PRs, keine fachliche Abnahme weiterer Entwürfe.

## Wirkung und Grenzen

Gruppen sind frei benannt; Namen verleihen keine Rechte. Passende Unternehmens- und
Projektfreigaben werden für Aktionen vereinigt. Ohne Freigabe kein Zugriff. Ein allgemeines
Aktionsverbot wird nicht eingeführt. Feldregeln gelten getrennt für READ und WRITE;
jedes passende DENY schlägt ALLOW, Schreiben erfordert zusätzlich Lesen. Regeln aus
anderen Projekten passen nicht. Unbekannte Felder bleiben unsichtbar und unschreibbar.
`require` weist einen Auftrag mit nur einem unerlaubten Feld vollständig ab; der Aufrufer
muss die gesamte Fachänderung in derselben Transaktion ausführen.

Unternehmen müssen übereinstimmen. Jede Projektressource verlangt Projektmitgliedschaft.
Externe benötigen zusätzlich ihre eigene Kundenorganisation; Unternehmensressourcen sind
ihnen in diesem begrenzten Modell nicht zugänglich. Erweiterte Organisationsfreigaben und
mehrere Organisationszugehörigkeiten werden hier nicht als entschieden eingeführt.
Fachregeln wie Statusübergänge, veröffentlichte Portalbeiträge oder eine erwartete
Objektversion prüft weiterhin das zuständige Fachmodul zusätzlich.

Es gibt keine HTTP-Endpunkte im Modul selbst (die Verwaltung über HTTP liegt seit TASK-030 am Anwendungseinstieg, siehe `backend/README.md`), keine Rollen mit Sonderzugriff, Standardgruppen, Initialrechte,
Benutzerkopien oder Bootstrapkonten im Berechtigungsmodul selbst. Ohne genau einen echten `IdentityAccess`-Adapter
verweigert die Anwendung alle Auswertungen/Verwaltungsaufrufe. Seit TASK-039 (DEC-063) besteht dieser Adapter am
Anwendungseinstieg; Aufbau, Sperren und der erste Start stehen in `backend/README.md`, Abschnitt
„Adapter zwischen Identität und Berechtigungen“. Die Rechte-Tests verwenden weiterhin ausschließlich synthetische
Adapter unter `src/test`, die den echten unter demselben Beannamen ersetzen. LDAP und API-/Job-/Dienstkontexte sind
nicht implementiert. TASK-026 muss die echten Projekt-/Objektadapter liefern. Deren integrierte Sicherheit ist
durch die synthetischen Nachweise nicht belegt. Die Verwaltungsendpunkte liefert TASK-030 Teil 1 (DEC-070), die Oberfläche Teil 2.

## Öffentlicher Java-Vertrag und Datenhoheit

Nur `taskmanager.permissions` ist öffentlich. `internal` bleibt intern. Kein neuer
Transportvertrag und keine Änderung der erlaubten Modulabhängigkeiten. Der Anwendungseinstieg
verbindet die verbrauchereigenen Ports mit späteren Identitäts-/Projektverträgen; das
Identitätsmodul importiert dafür nicht rückwärts `permissions`.

| Operation | Eingabe und erforderliche Rechte | Ergebnis / Fehler / Wiederholung |
|---|---|---|
| `Permissions.currentVersion` | Vertrauenswürdige Unternehmens-ID, nur interne Kontextbildung | Aktuelle Rechteversion; unbekanntes Unternehmen: `PermissionDenied`. Keine Autorisierung und kein öffentlicher Versionsendpunkt. |
| `inspect` | Serverseitiger `AccessContext`, Ressourcenlieferant, Aktion, benötigte Feldkennungen | Unveränderliche `Decision` mit erlaubten Feldkennungen; fehlender/alter Kontext liefert leere Ablehnung. Nur Momentaufnahme; nie Berechtigung für späteres Schreiben. |
| `require` | Dieselben vertrauenswürdigen Angaben, getrennte benötigte Lese-/Schreibfelder, bestehende Transaktion | Keine Rückgabe; jede fehlende Erlaubnis wirft `PermissionDenied` und macht die gemeinsame Transaktion rückgängig. |
| `createGroup`, `setMember` | Aktueller interner Kontext, Unternehmensrecht `permissions.manage`; Zielkonto im selben Unternehmen | Gruppen-ID bzw. keine Rückgabe. Unbekannte/fremde Ziele allgemein abgewiesen. Mitgliedschaft betrifft nur Berechtigungsgruppen, nicht Projektmitgliedschaften. |
| `setAction`, `setField` | Aktueller Kontext, Gruppe und vertrauenswürdig gelieferter Geltungsbereich; Unternehmensverwaltung oder eng begrenzte Projektdelegation | Setzen/Entfernen einer Regel; neue Rechteversion plus Audit in derselben Transaktion. |
| `groups` | Unternehmensrecht `permissions.manage` | Nur Gruppen dieses Unternehmens. |
| `projectGroups` | Unternehmensrecht `permissions.manage` oder internes Konto mit `permissions.manage` im Projekt (TASK-043) | Jede Gruppe mit ihren Regeln genau dieses Projekts; keine Mitglieder, keine Unternehmensregeln, keine anderen Projekte. |
| `audit` | Eigenes Unternehmensrecht `permissions.audit.read`, Limit 1–100, optional `before` | Neueste Einträge nur dieses Unternehmens, mit `before` nur ältere Rechteversionen; jeder Eintrag nennt den ersetzten Zustand (`previousEffect`) und ob er ihn kennt (`previousRecorded`). Verwaltungsrecht allein genügt nicht. |

Eine Unternehmensverwaltung hat keinen automatischen Zugriff auf Tickets, andere Fachinhalte
oder das Audit. Sie kann Rechte bewusst vergeben; diese weitreichende Befugnis ist kein
Schutz gegen den Betreiber mit Datenbankzugriff. Eine Projektdelegation benötigt
`permissions.manage` im Projekt, aktuelle Mitgliedschaft und Organisationsgrenzen. Sie darf
keine Gruppen/Mitgliedschaften oder Unternehmensregeln verändern und keine
`permissions.*`-Aktionen weitergeben. Aktionen kann sie nur innerhalb ihrer eigenen Freigabe
ändern. Für **jede** Feldregeländerung einschließlich Entfernen eines Verbots muss sie Lesen
**und** Schreiben des Felds besitzen: Auch eine Lesefreigabe könnte sonst eine bereits
vorhandene Schreibfreigabe eines Empfängers aktivieren. Ein bestehendes **Verbot** darf eine
Projektdelegation nur ändern, entfernen oder ersetzen, wenn sie es selbst **als Delegation**
gesetzt hat (`permissions_field.set_by` und `set_by_company = false`); Verbote der
Unternehmensverwaltung, anderer Delegierter und Regeln ohne erfasste Herkunft bleiben
unberührt (Entscheidung Holger 2026-10-18, Review P7). Die Herkunft hält fest, ob die setzende
Person zum Zeitpunkt der Setzung Unternehmensverwaltung war. Ein Firmenverbot bleibt deshalb
auch dann geschützt, wenn dieselbe Person die Unternehmensrolle später verliert und eine
Projektdelegation behält (Entscheidung Holger 2026-10-19, Nachprüfung K1, DEC-062). Regeln aus
der Zeit vor V0007 haben keine Rollenangabe (`NULL`) und zählen als geschützt.
Die Unternehmensverwaltung ist davon nicht eingeschränkt. Diese Delegationsgrenze ist ein
überprüfbarer Umsetzungsvorschlag; differenziertere Delegationsprofile bleiben offen.

**Schutz der letzten Administration (Review P1):** `setAction` (Entzug von `permissions.manage`
auf Unternehmensebene) und `setMember` (Entfernen einer Mitgliedschaft) werden mit
`PermissionDenied` abgewiesen und mit der gemeinsamen Transaktion zurückgerollt, wenn danach
keine Gruppe mit diesem Unternehmensrecht ein Mitglied hat, das der Identitätsadapter über
`userActive` als aktives internes Konto der Firma meldet; ein bekanntes, aber deaktiviertes
Konto zählt nicht (Nachprüfung R1, DEC-062). `userExists` bleibt für das Hinzufügen von
Mitgliedern bestehen. Die Prüfung läuft nach der Änderung unter der exklusiven Revisionssperre,
gleichzeitige Entzüge werden dadurch serialisiert. Der Schutz gilt für Änderungen über
dieses Modul und seit TASK-039 auch für das Deaktivieren eines Kontos: Die Identität fragt vor ihren Sperren
`CompanyAdministration.remainsWithout`, das die exklusive Revisionssperre nimmt und ein anderes aktives Konto mit dem
Unternehmensrecht verlangt. Löscht ein künftiger Weg Konten, muss er dieselbe Schranke durchsetzen.

Namen sind auf 80 Zeichen, technische Schlüssel auf 120 Zeichen begrenzt. Schlüssel sind
kleine ASCII-Kennungen mit Ziffern, Punkt und Unterstrich. Weder vertrauliche Feldwerte noch
freie Fehlermeldungen gehören in solche Kennungen. Java-Ausnahmen nennen keine Identität,
Ressource, Gruppen-/Feldkennung oder deren Existenz. Die vorhandene globale HTTP-Fehlerbehandlung
bleibt unverändert; konkrete Fachcodes/403-/404-Abbildung werden vor Fachendpunkten abgestimmt.

## Transaktion, Entzug und Sperrreihenfolge

Ein DataSource-/Transaktionsmanager, PostgreSQL READ COMMITTED. Alle Änderungen und `require`
verlangen eine bestehende Transaktion (`MANDATORY`). Nicht unterstützte Isolation wird
abgewiesen, damit ein älterer Snapshot nach einer Sperrwartezeit keine alten Gruppenregeln
liest. Keine externen Aufrufe oder `REQUIRES_NEW` zwischen Prüfung und Fachcommit.

1. Die Unternehmenszeile in `permissions_revision` wird zuerst gesperrt: FOR SHARE für
   `require`/Lesen, FOR UPDATE für Rechteverwaltung. Fehlende Zeile verweigert Zugriff.
2. Die mitgegebene Rechteversion muss passen. Danach prüft `IdentityAccess` die **aktuellen**
   vollständigen Identitätseigenschaften und sperrt Konto/Sitzung gegen Entzug oder Wechsel
   bis zum gemeinsamen Commit. Ein normaler Snapshot-SELECT genügt nicht.
3. Erst danach lädt der Lieferant die tatsächlichen Projekt-/Organisations-/Objektmerkmale.
   Er hält ebenfalls passende Sperren bis zum Commit; alle entsprechenden Schreiber verwenden
   inkompatible Sperren. Ein Browser-DTO oder zuvor gelesener Cache erfüllt den Vertrag nicht.
4. Das Fachmodul prüft eigene Versionen/Zustände und schreibt. Die Sperren enden erst am
   gemeinsamen Commit/Rollback. Feste Ordnung: Rechte → Identität → Ressource → Fachobjekt;
   mehrere gleichartige Zeilen in stabiler ID-Reihenfolge sperren.

Jede Rechteänderung erhöht die Unternehmensversion und erzeugt einen Audit-Eintrag atomar.
Alte Kontexte werden auch bei anderen Benutzern dieses Unternehmens ungültig; der
Anwendungseinstieg baut sie aus aktuellen serverseitigen Angaben neu auf. Es gibt keinen
Rechtecache. Der grobe Unternehmensbezug ist eine bewusste einfache Ausgestaltung, keine
gemessene Leistungsaussage. Sperrwartezeit höchstens fünf Sekunden; Timeout/Deadlock bedeutet
Rollback, keine Freigabe. Eine Wiederholung beginnt die **gesamte** Transaktion mit erneutem
Kontext und erneuter Prüfung; es gibt noch keinen allgemeinen Idempotenzvertrag.

Entzug zuerst: Ein wartender Fachauftrag liest danach die neue Version und scheitert.
Fachprüfung zuerst: Die Änderung/der Entzug wartet auf dessen Fachcommit; spätere Aufträge
scheitern. Diese Reihenfolge gilt für Gruppen-/Rechteänderungen unmittelbar im Modul;
für Identitäts-/Projektentzug nur bei Erfüllung des beschriebenen Adaptervertrags.
Die echten Adapter müssen dies in TASK-024/TASK-026 mit eigenen Konkurrenztests nachweisen.
Bei Leseausgaben, die bis zur Projektion konsistent bleiben müssen, ruft der Fachlesevorgang
`require` innerhalb seiner Transaktion auf und liest/projiziert dort; ein separat abgeschlossener
`inspect`-Aufruf ist nur eine Bedienhilfe.

Grundlage der Mechanik: [PostgreSQL-18-Zeilensperren](https://www.postgresql.org/docs/18/explicit-locking.html)
und [Spring-Transaktionspropagierung](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).

## Migration und Audit

`V0001__platform_baseline.sql` bleibt unverändert leer. Die reservierte
`V0002__permissions_baseline.sql` legt ausschließlich sechs eigene Tabellen an: Revision,
Gruppe, Gruppenmitgliedschaft, Aktion, Feldregel und Audit. Unternehmensbezogene Schlüssel
und Fremdschlüssel verhindern fremde Gruppenzuordnung; `UNIQUE NULLS NOT DISTINCT` schützt
auch Unternehmensregeln ohne Projekt-ID. Keine Fremdschlüssel oder SELECTs auf fremde
Modultabellen. Keine Seeds oder Defaultfreigaben in Migrationen. Der Initialisierungspfad ist seit TASK-039
`CompanyAdministration.initialize`, aufgerufen vom Containerbefehl `bootstrap-admin` in der Transaktion des ersten Kontos:
Rechteversion, genau eine Gruppe mit `permissions.manage` sowie den Verwaltungsrechten `accounts.manage`, `session_duration.manage` und
`appearance.manage` (TASK-040), später ergänzt um `projects.manage` (TASK-026), `tags.manage` (TASK-028) und `jobs.manage` (TASK-054), und der Erstadministrator als Mitglied, ohne Fachrechte und ohne Auditrecht. Die Rechte-Tests säen weiterhin synthetische Ausgangspolicies direkt in ihre getrennte Testdatenbank.

Audit enthält Zeitpunkt, Akteur-, Gruppen-, gegebenenfalls betroffene Benutzer-/Projekt-ID,
Operation, technische Aktions-/Feldkennung, Wirkung und Rechteversion. Keine Namen,
Passwörter, Sitzungsschlüssel, Fachfeldwerte oder Ausnahmemeldungen. Auch wirkungslose
erlaubte Setzaufrufe werden versioniert/protokolliert. Ein Rollback entfernt Änderung,
Revisionserhöhung und Audit zusammen. Keine Änderungs-/Löschoperation für Audit im Modul;
Aufbewahrung, Pseudonymisierung und Betriebslöschung bleiben unter OPEN-014 offen. Keine
Manipulationssicherheit gegenüber einem Datenbankadministrator zugesagt. Unternehmensweites
Audit statt Projektansicht verhindert, dass delegierte Projektverwaltung fremde Metadaten liest.

## Prüfungen und Übergabe

`PolicyTest` prüft Aktion, Projekt-/Unternehmens-/Organisationsgrenze und Feldkonflikte rein
in Java. `PermissionsIntegrationTest` verwendet eine eigene leere PostgreSQL-Datenbank:
Migration, echte JDBC-Transaktionen, Mehrgruppen, veralteter/fehlender Kontext, fehlende/mehrdeutige
Adapter, Pflichtwechsel, vollständiger Rollback, Administration, Auditgrenze und Konkurrenz.
Die Konkurrenztests erzwingen die Reihenfolge mit Latches und prüfen die echte Wartebeziehung
über `pg_blocking_pids`, keine allein zeitbasierte Annahme. Synthetische Identitäts- und
Ressourcenadapter halten eigene FOR-SHARE-Sperren; ihre Entzüge müssen auf Fachcommit warten.

Foundation-/Lifecycle-Prüfungen erwarten jetzt genau V0001+V0002 und die sieben Tabellen
einschließlich Flyway-Historie; sie verlangen weiterhin leere Startdaten, ausgeschaltetes
Clean, ORM validate und unveränderte Migrationshistorie nach Neustart. Der CI-Workflow wird
weiterverwendet, ohne Tests auszuschalten. Tatsächliche Läufe und Reviewgrenzen stehen in
TODO.md und im Ergebnis-PR; beschriebene Tests allein sind kein Ausführungsnachweis.


## Härtung (TASK-043, DEC-072)

Stand 22. Oktober 2026. Umsetzung der Punkte P2 bis P6 der Prüfung von TASK-025 und der Prüfung beim Commit.

- **Protokoll (P2).** `audit(actor, before, limit)` blättert über die Rechteversion, die nur wächst: Die kleinste Version
  einer Seite holt als `before` die nächste. `previous_effect` (Migration V0016) hält fest, was eine Änderung ersetzt hat
  (`ALLOW`, `DENY` oder nichts). `previous_recorded` (Migration V0017, Befund F4 der Prüfung von TASK-043) sagt, ob der
  Eintrag seinen Vorzustand kennt: neue Einträge immer, Einträge vor V0016 nie, Einträge zwischen V0016 und V0017 nur mit
  erfasstem Wert. Ein fehlender Wert gilt also nur bei `previous_recorded` als „vorher nichts“. Aufbewahrung und Schutz
  bleiben bei OPEN-014.
- **Externe Konten (P3).** `administer` und die Prüfung der Unternehmensverwaltung lehnen externe Konten immer ab, auch
  wenn eine Gruppe ihnen `permissions.manage` gibt.
- **Fachfehler und Sperrfehler (P4).** Ein vorhandener Gruppenname, ohne Rücksicht auf Groß- und Kleinschreibung, wirft
  `GroupNameTaken` (eine `PermissionDenied`); die exklusive Revisionssperre ordnet alle Anlagen, der eindeutige Index bleibt
  die letzte Sicherung. `inspect` und `require` können unter Last mit einer Sperrzeitüberschreitung (55P03) oder Verklemmung
  (40P01) enden statt mit einer Ablehnung; die Transaktion ist dann abgebrochen, wiederholt wird die ganze Transaktion. Eine
  Verwaltungsänderung gehört nicht in eine Transaktion, die schon `inspect` oder `require` ausgeführt hat: die geteilte
  Sperre lässt sich nicht ohne Verklemmungsgefahr zur exklusiven aufwerten.
- **Sperrwartezeit (P5).** Fünf Sekunden gelten nur für die Prüfung von Rechten, Identität und Ressource. Danach setzt das
  Modul den vorherigen Wert der Transaktion wieder ein. Bisher wirkte die Grenze der Prüfung auch auf alle Fachanweisungen
  danach, und die Fachmodule verließen sich darauf (etwa 503 `ticket.busy`); deshalb ist fünf Sekunden jetzt der Vorgabewert
  jeder Verbindung (`spring.datasource.hikari.connection-init-sql`), den eine Transaktion mit `SET LOCAL` ändern kann. Endet die Prüfung mit einem Datenbankfehler, ist die Transaktion
  abgebrochen und es läuft nichts mehr darin.
- **Änderungen ohne Wirkung (P6).** Dasselbe Mitglied erneut, ein fehlendes Mitglied entfernen, eine vorhandene Aktion
  erneut gewähren, eine fehlende Regel entfernen: keine neue Rechteversion, kein Protokoll. Dieselbe Feldregel von einer
  anderen Person oder in einer anderen Rolle ist eine Änderung, weil der Urheber bestimmt, wer ein Verbot zurücknehmen darf.
  Weiterhin offen aus P6: Jede wirksame Änderung macht alle Kontexte des Unternehmens ungültig (Version je Unternehmen), und
  `inspect` nimmt eine Zeilensperre; beides ist ungemessen.
- **Prüfung beim Commit.** Rechte, Identität und Ressource bleiben durch die Sperren bis zum Commit stabil; ein Entzug wartet,
  bis die geprüfte Änderung festgeschrieben ist. Nicht gesperrt ist die Zeit. `IdentityAccess.currentUntil` liefert deshalb
  mit der Prüfung das Ende der Sitzung, und `require` vergleicht es unmittelbar vor dem Commit noch einmal mit der Uhr
  (`TransactionSynchronization.beforeCommit`); mehrere Prüfungen einer Transaktion behalten das früheste Ende. Ist es
  überschritten, wirft der Commit `PermissionDenied`, die ganze Transaktion rollt zurück, und die HTTP Schicht antwortet 403
  `auth.forbidden`. Was die eigene Transaktion am eigenen Konto ändert (etwa die Sitzungsdauer kürzen), zählt dabei nicht:
  maßgeblich ist das Ende, das die Prüfung unter ihren Sperren festgestellt hat. Die Voreinstellung des Ports kennt kein Ende.
- **Gruppen für Projektverwaltungen.** `projectGroups` gibt jeder Projektverwaltung alle Gruppen des Unternehmens mit ihren
  Regeln in diesem Projekt, weil sie die Projektregeln jeder Gruppe ändern darf. Lese- und Schreibweg sind deckungsgleich;
  eine engere Grenze ist Holgers offene Entscheidung zu P7.
