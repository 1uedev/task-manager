# Gegenseitige technische Prüfung von TASK-025 (Claude)

Datum: 18. Oktober 2026. Prüfgegenstand: Berechtigungsmodul aus [PR #74](https://github.com/1uedev/task-manager/pull/74) im Stand von `main` (`508f0df`).
Autor der Umsetzung: `codex-20261018-permissions-01`.
Prüfer: Claude-Sitzung `claude-20261018-identity-01`. Diese Sitzung hat TASK-025 nicht geschrieben, ist aber Autorin des
benachbarten Identitätsmoduls (TASK-024) und kennt dessen Grenzen aus eigener Arbeit.

Dies ist der gegenseitige Claude-Review nach DEC-040, ergänzend zur statischen Codex-Prüfung in
[REVIEW_TASK-025.md](REVIEW_TASK-025.md). Er ist **keine fachliche Abnahme durch Holger** und setzt TASK-025 nicht auf erledigt.
TASK-025 bleibt BEREIT ZUR PRÜFUNG in TODO.md. Dieser Bericht ändert weder TODO.md noch DONE.md.

## Gesamturteil

Ich fand **keinen Fehler im Rechtealgorithmus und keine Lücke in der Sperrreihenfolge**, die zu einem unberechtigten Zugriff
führt. Alle Auffälligkeiten sind fehlgeschlagen im sicheren Sinn (Zugriff verweigert oder Fehler statt Freigabe).
Es bleiben **zwei Befunde mittlerer Schwere** (Aussperrung der letzten Administration, Audit nur über die letzten 100 Einträge),
mehrere niedrige Befunde und Entscheidungspunkte für Holger. Keiner davon blockiert die technische Annahme der Berechtigungsbasis.
Die mittleren Befunde sollten vor der Nutzung durch TASK-026 und TASK-030 entschieden werden.

## Was tatsächlich geprüft wurde

Gelesen: `backend/PERMISSIONS.md`, Migration `V0002__permissions_baseline.sql`, alle Typen unter
`backend/src/main/java/taskmanager/permissions` einschließlich `internal/Policy` und `internal/JdbcPermissions`,
`PermissionsIntegrationTest` (10 Tests), `PolicyTest` (4 Tests), TASK-025 in TODO.md, REQ-ACL-001 bis REQ-ACL-005, DEC-057.

Ausgeführt (lokal, Java 21 mit `-Djava.version=21`, PostgreSQL 16, nicht Temurin 25 und PostgreSQL 18.6 wie in CI):

1. Gesamter Backendlauf `clean verify` auf `main`: 73 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen.
2. Elf eigene Gegenproben als vorübergehende Testklasse mit synthetischem Identitätsadapter (Ergebnisse unten). Die Klasse wurde
   nach dem Lauf wieder entfernt und ist nicht Teil des Repositorys; die Belege stehen in diesem Bericht.
3. Mutationsprobe: In `JdbcPermissions.lock` wurde die Zeilensperre auf `permissions_revision` testweise entfernt. Daraufhin
   scheiterten `revocationCommittedFirstPreventsWaitingOldWrite` und `writeCommittedFirstFinishesBeforeRevocationAndLaterWriteIsDenied`
   mit "Expected a real PostgreSQL lock wait". Die Konkurrenztests unterscheiden also tatsächlich zwischen gesperrter und ungesperrter
   Umsetzung. Die Änderung wurde danach zurückgenommen (`git checkout`), der Arbeitsbaum ist unverändert.

Nicht geprüft: Verhalten unter PostgreSQL 18.6 und Temurin 25 durch eigenen Lauf (nur die CI des Autors belegt das), Lastverhalten,
echte Identitäts- und Objektadapter (existieren nicht), HTTP-Schicht (existiert nicht), Auditaufbewahrung (OPEN-014).

## Bestätigte Eigenschaften (durch Lektüre und Gegenprobe)

- Aktionsunion über Gruppen, Feldverbot gewinnt über Freigabe, Schreiben setzt Lesen voraus. Auch über Geltungsbereiche hinweg:
  ein Verbot auf Unternehmensebene wirkt in jedem Projekt, ein Verbot in Projekt B beeinflusst Projekt A nicht (Probe p4).
- Projektbezogene Ressourcen verlangen Projektmitgliedschaft; ohne sie wird auch bei Unternehmensregel verweigert (p4).
- Externe Nutzer: nur mit Projekt und gleicher Organisation. Fremde Organisation, fehlende Organisation der Ressource,
  fehlende Mitgliedschaft und fremdes Projekt werden verweigert, der Positivfall funktioniert (p2).
- Der `ResourceContext`-Lieferant wird erst nach Revisionssperre, Versionsvergleich und Identitätsprüfung ausgewertet
  (Auswertungsreihenfolge in `inspect` und `require`).
- Die Regelabfragen nach der Sperre laufen unter READ COMMITTED mit neuem Snapshot je Anweisung; der Isolationstest vermeidet das
  bekannte REPEATABLE-READ-Problem und wird mit einem eigenen Test belegt.
- Mandantentrennung im Schema: zusammengesetzte Fremdschlüssel `(company_id, group_id)` bei Mitgliedschaft, Aktion und Feld, eindeutige Gruppennamen je
  Unternehmen ohne Beachtung der Groß-/Kleinschreibung.
- Audit und Rechteänderung sind atomar mit der Revisionserhöhung; `rights_version` ist je Unternehmen eindeutig.

## Befunde

Schweregrade: mittel = vor Folgenutzung entscheiden, niedrig = dokumentieren oder bei Gelegenheit beheben.

### P1 (mittel): Die letzte Administration kann sich selbst die Verwaltungsrechte entziehen

**Fundstelle:** `JdbcPermissions.setAction`, Zweig für Unternehmensverwalter.
**Szenario (Probe p18):** Die einzige Administratorgruppe ist `permissions.manage` auf Unternehmensebene. Der Administrator ruft
`setAction(..., "permissions.manage", false)` auf. Der Aufruf gelingt. Danach kann niemand mehr Gruppen, Mitgliedschaften oder
Rechte verwalten oder das Audit lesen. Ein Folgeaufruf desselben Administrators wird mit `PermissionDenied` verweigert.
Das Modul bietet keinen Wiederherstellungsweg. Der Notfallbefehl `reset-admin-password` des Identitätsmoduls setzt nur ein
Kennwort zurück und stellt keine Rechte wieder her. Die Gruppenzuordnung des Bootstrap-Kontos gibt es noch nicht
(Unternehmensinitialisierung ist ausdrücklich offen).
**Vorschlag:** Entscheidung nötig. Entweder verweigert die Verwaltung jede Änderung, nach der kein aktives Mitglied mehr
`permissions.manage` auf Unternehmensebene hält (prüfbar in derselben Transaktion unter der exklusiven Sperre), oder die
Unternehmensinitialisierung liefert einen dokumentierten Wiederherstellungsbefehl. Die erste Variante ist klein und lokal;
sie sollte einen eigenen Konkurrenztest erhalten (zwei Administratoren entziehen sich gleichzeitig gegenseitig die Rechte; die
exklusive Sperre serialisiert das).

### P2 (mittel): Das Audit ist nur über die letzten 100 Einträge lesbar

**Fundstelle:** `JdbcPermissions.audit`, Prüfung `limit < 1 || limit > 100`, Sortierung nach `rights_version desc`.
**Problem:** Die Schnittstelle kennt weder Seitenbeginn noch Zeitraum noch Rückwärtsblättern. Nach dem 101. Eintrag eines
Unternehmens sind ältere Einträge über den Vertrag nicht mehr erreichbar. REQ-ACL-005 verlangt, dass berechtigte Personen Urheber,
Zeitpunkt, Gegenstand und Änderung erkennen. Der Vorgängerwert einer Änderung (zum Beispiel was ein `REMOVE` entfernte) wird nicht
festgehalten, die Wirkung eines `REMOVE` ist nur aus der Historie rekonstruierbar, die dann unvollständig ist.
Ein Schutz der Tabelle gegen nachträgliches Ändern oder Löschen durch den Anwendungsbenutzer besteht nicht (Aufbewahrung und
Unveränderlichkeit sind mit OPEN-014 offen).
**Vorschlag:** Blättern über `rights_version` (`before`-Wert) und optional den Vorgängerwert im Eintrag vor der
Administrationsoberfläche (TASK-030) ergänzen. Aufbewahrung und Schutz folgen OPEN-014.

### P3 (niedrig): Externe Nutzer können Projektverwalter werden

**Fundstelle:** `Policy.decide` zusammen mit `JdbcPermissions.administer`.
**Szenario (Probe p3):** Erhält die Gruppe eines externen Nutzers `permissions.manage` auf ein Projekt, passt die Organisation und
ist die Mitgliedschaft gegeben, darf der externe Nutzer Projektregeln anderer Gruppen ändern. Das Entziehen geht nur durch eine
Unternehmensadministration (Delegierte dürfen `permissions.*` nicht vergeben), eine versehentliche Vergabe ist also unwahrscheinlich.
REQ-ACL-002 verlangt, dass Externe nur ausdrücklich zugewiesene Rechte erhalten; eine Verwaltungsbefugnis ist ausdrücklich
zuweisbar, aber selten gewollt.
**Vorschlag:** Entscheiden, ob `administer` und `isCompanyManager` externe Akteure grundsätzlich verweigern sollen (Tiefenverteidigung,
eine Zeile). Falls nicht, im Vertrag festhalten.

### P4 (niedrig): Betriebsfehler sind nicht auf Vertragsfehler abgebildet

Alle Fälle verweigern korrekt, liefern aber unterschiedliche Ausnahmetypen:

| Probe | Ereignis | Beobachtung |
| --- | --- | --- |
| p1 | Gruppenname doppelt (auch in anderer Schreibweise) | `DuplicateKeyException` statt eines Fachfehlers |
| p6 | `inspect` wartet auf eine gehaltene Verwaltungssperre | nach 5008 ms `UncategorizedSQLException`; `inspect` soll laut Vertrag `Decision.denied()` liefern, nicht werfen |
| p7 | zwei Transaktionen rufen erst `inspect`, dann `createGroup` auf | eine gelingt, die andere endet mit `PessimisticLockingFailureException` (Verklemmung erkannt) |

**Vorschlag:** Doppelten Namen als eigenen Verwaltungsfehler abbilden. Für Sperrzeitüberschreitung und Verklemmung im Vertrag
festhalten, dass `inspect` und `require` unter Last auch Ausnahmen werfen können und die HTTP-Schicht diese als wiederholbaren
Fehler beantwortet. Der Hinweis in PERMISSIONS.md (Wiederholung beginnt die gesamte Transaktion neu) deckt das inhaltlich ab.
Zusätzlich dokumentieren: Verwaltungsaufrufe nicht in einer Transaktion nach `inspect` oder `require` ausführen; das aufgerüstete
Sperrverhältnis (erst geteilt, dann exklusiv) ist die Ursache von p7.

### P5 (niedrig): Die Sperrzeitgrenze gilt für den Rest der Transaktion

**Fundstelle:** `lock()` setzt `lock_timeout` mit `set_config(..., true)`.
**Beobachtung (Probe p12):** Nach `require` zeigt `show lock_timeout` im selben Vorgang `5s`, in der nächsten Transaktion `0`.
Die Grenze wirkt also auch auf alle nachfolgenden Fachanweisungen derselben Transaktion. Das ist verträglich (verhindert
endloses Warten), sollte aber im Vertrag stehen, damit Fachmodule nicht überrascht werden. Eine Wiederherstellung des vorherigen Werts
nach der Prüfung wäre die saubere Alternative.

### P6 (niedrig): Jede Rechteänderung entwertet alle Kontexte des Unternehmens

**Beobachtung (Probe p8):** Die Version ist je Unternehmen, nicht je Nutzer. Ein beliebiger Gruppenwechsel (hier das Anlegen einer
unbeteiligten Gruppe) macht den bereits gebildeten Kontext eines unbeteiligten Nutzers ungültig; sein nächster Auftrag wird
verweigert, bis der Kontext neu gebildet wurde. Das ist bewusst sicher, erzeugt aber Scheinabweisungen proportional zur
Änderungshäufigkeit. Hinzu kommt (Probe p9), dass Änderungen ohne Wirkung (zweimal dasselbe Mitglied setzen, ein Nichtmitglied
entfernen) die Version erhöhen und Audit schreiben: drei Aufrufe, drei Versionen.
**Vorschlag:** Nichtwirksame Änderungen weder zählen noch protokollieren. Der Kontextaufbau der Identitätsadapter muss je Auftrag
frisch lesen und bei Ablehnung wegen veralteter Version einmal automatisch wiederholen.
Zusätzlich vor Nutzung durch Suche und Listen messen: jeder `inspect` nimmt `FOR SHARE` auf der Revisionszeile und schreibt damit
bei jedem Aufruf einen Zeilenvermerk. Bei hoher gleichzeitiger Last ist das ein möglicher Engpass. Eine gemessene Aussage fehlt
(PERMISSIONS.md macht bewusst keine). Ob lesende Prüfungen ohne Zeilensperre auskommen, sollte nach Messung entschieden werden.

### P7 (niedrig, Entscheidungspunkt): Delegationsumfang

**Beobachtung (Probe p19):**
- Ein Projektverwalter darf Projektregeln beliebiger Gruppen des Unternehmens entziehen, sofern er die Aktion selbst hält.
- Er darf auch ein ausdrückliches Feldverbot einer anderen Gruppe entfernen, wenn er selbst Lese- und Schreibrecht auf das Feld
  hat. Das ist im Code als bewusste, konservative Grenze kommentiert, kann aber dem Betroffenen Zugriff verschaffen, den ein
  Unternehmensverwalter gesperrt hatte, sofern das Verbot auf Projektebene stand.
- Er darf `groups()` nicht aufrufen und kennt daher keine Gruppen-IDs; die Delegation ist ohne Zusatzweg nicht bedienbar
  (TASK-030 muss dafür einen eingeschränkten Gruppen-Lesezugriff vorsehen).
**Vorschlag:** Holger entscheidet bei der Abnahme, ob Projektverwalter Verbote anderer Gruppen entfernen dürfen oder ob nur ihre
eigenen Setzungen zurücknehmbar sein sollen. Bis dahin ist die Grenze zu weit, nicht zu eng, daher als Entscheidungspunkt geführt.

## Mapping auf die Anforderungen (kurz)

| Anforderung | Stand |
| --- | --- |
| REQ-ACL-001 Gruppen, projektweise Rechte | Anlegen, Mitgliedschaft, Aktionen und Felder je Projekt vorhanden. Kein Umbenennen oder Löschen von Gruppen. |
| REQ-ACL-003 Feldrechte | Algorithmus korrekt, Sichtbarkeit und Änderbarkeit getrennt, Verbot gewinnt. Wirkung auf Suche, Export und KI liegt bei den Verbrauchern. |
| REQ-ACL-004 Einheitliche Durchsetzung | Zentraler Port vorhanden. "Rechteänderungen wirken auf zwischengespeicherte Inhalte": nur wenn Verbraucher an jedem Zugriff prüfen. |
| REQ-ACL-005 Nachvollziehbare Verwaltung | Urheber, Zeitpunkt, Gegenstand vorhanden. P2 (Blättern, Vorgängerwert, Schutz) offen. |

## Verbleibende Grenzen

- Es gibt weiterhin keinen echten Identitäts-/Sitzungsadapter und keinen Projekt-/Objektadapter. Die Sperrverträge sind nur mit
  synthetischen Adaptern bewiesen. Das Identitätsmodul aus TASK-024 kennt weder Unternehmen noch Organisation noch Rechteversion
  und nimmt auf seinen Entzugswegen (Deaktivieren, Zurücksetzen, Abmelden, Kennwortwechsel) die geforderten Sperren nicht.
  Wer den Adapter baut, ist nicht zugewiesen; ohne Auftrag wird er nicht gebaut.
- Dieser Bericht beruht auf PostgreSQL 16 und Java 21 lokal. Das CI des Autors lief mit PostgreSQL 18.6 und Temurin 25.
- Der Prüfer ist ebenfalls ein Claude-Modell. Das ist die vorgesehene gegenseitige KI-Prüfung nach DEC-040; eine zusätzliche
  menschliche technische Prüfung bleibt offen.

## Empfehlung für die Abnahme

Technisch bestehen keine Gründe gegen die Annahme der Berechtigungsbasis als Baustein. Vor Annahme entscheiden: P1 (Schutz der
letzten Administration) und P7 (Delegationsumfang). P2 gehört spätestens zu TASK-030. P3 bis P6 sind niedrig und können als
Folgeaufgabe oder in die Integration des Identitätsadapters aufgenommen werden. Ich habe keine Folgeaufgaben angelegt; das
bleibt der Koordination überlassen.
