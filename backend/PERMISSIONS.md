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

Es gibt keine HTTP-Endpunkte, Rollen mit Sonderzugriff, Standardgruppen, Initialrechte,
Benutzerkopien oder Bootstrapkonten. Ohne genau einen echten `IdentityAccess`-Adapter
verweigert die Anwendung alle Auswertungen/Verwaltungsaufrufe. Tests verwenden ausschließlich
synthetische Adapter unter `src/test`. TASK-024 ist separat für Claude reserviert (Claim-PR #73); Anmeldung,
Sitzungen, LDAP, API-/Job-/Dienstkontexte und produktiver Bootstrap sind nicht implementiert.
TASK-026 muss die echten Projekt-/Objektadapter liefern. Deren integrierte Sicherheit ist
durch die synthetischen Nachweise nicht belegt. Die Administrationsoberfläche folgt TASK-030.

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
| `audit` | Eigenes Unternehmensrecht `permissions.audit.read`, Limit 1–100 | Neueste Einträge nur dieses Unternehmens. Verwaltungsrecht allein genügt nicht. |

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
gleichzeitige Entzüge werden dadurch serialisiert. Grenze: Der Schutz gilt für Änderungen über
dieses Modul. Deaktiviert oder löscht der Identitätsadapter später das letzte Konto einer
Administrationsgruppe, muss er dieselbe Schranke auf seinem Entzugsweg selbst durchsetzen.

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
Modultabellen. Keine Seeds, automatische Unternehmensanlage oder Defaultfreigaben.
Der echte Initialisierungspfad benötigt den abgestimmten Bootstrap aus TASK-024; die Tests
säen ausschließlich synthetische Ausgangspolicies direkt in ihre getrennte Testdatenbank.

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
