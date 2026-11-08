# Prüfung TASK-090 / PR #268

**Empfehlung: nachbessern, klein.** T1 und T2 sind in der verlangten Form behoben. Meine eigenen Proben mit späten Antworten, Dateiauswahl, Ablage und Tastatur laufen gegen den neuen Stand wie vorgesehen, und die gelieferten Regressionen scheitern mit der Seite von main. Die neue Abfrage `alive` ist aber breiter als nötig: Sie verwirft auch die Antwort eines **erfolgreichen** Speicherns, das erst nach dem Verlassen der Seite zurückkommt. Danach kennt die Oberfläche eine ältere Version als der Server, zeigt den alten Stand und schickt die nächste Änderung mit veralteter Version (412). Auf main ist das nicht so; es ist eine kleine Rückentwicklung (B1). Die Hinweise 1 und 2 sind tragfähig umgesetzt.

## Prüfstand und Umfang

- Claude, Sitzung `claude-20261108-task090-review-01`, 8. November 2026, als eigener Prüf-Thread an Stelle von Codex nach DEC-040. Nicht an der Umsetzung beteiligt.
- Geprüfter Kopf von `claude/project-thread-tzp0cw`: `c028119af9d8bf1f0ce102348e9f96ffddfa3b87`, Basis `0319aeb`; main bei der Prüfung `419d8b2` (TASK-091, ohne Berührung des Auftritts).
- PR #268 ist ein offener Entwurf. Für den Kopf liegen auf GitHub keine Check-Läufe vor; die Aussagen unten beruhen auf eigener Ausführung.
- Grundlagen: docs/REVIEW_TASK-089_PR-263.md mit Nachtrag, TODO.md unter TASK-090, DEC-120.
- Diese Sitzung ändert nur diesen Bericht und docs/review-evidence/task-090/. Kein Produktcode, kein Aufgabenstatus, keine Abnahme.

## Befund

### B1 [P3] Eine erfolgreich gespeicherte Änderung, deren Antwort nach dem Verlassen der Seite eintrifft, geht der Oberfläche verloren

**Stelle:** `frontend/src/pages/AppearancePage.tsx:59–64` (`end`) mit `run` in Zeile 132–143.

`end()` gibt bei verlassener Seite `false` zurück, `run` endet dann vor `take`, und damit unterbleibt auch `accept`. Für einen Lesevorgang ist das gewollt. Für ein Speichern verwirft es aber den Stand, den der Server gerade festgeschrieben hat. Seit TASK-090 nimmt `BrandProvider.accept` ohnehin nur gleiche oder neuere Versionen an; eine neuere Version aus einer verlassenen Seite zu übernehmen ist daher sicher und richtig.

**Probe P5** (Belege in docs/review-evidence/task-090/): Namen „Gespeichert“ speichern, die Antwort zurückhalten, auf die Startseite wechseln, Antwort freigeben, Erscheinungsbild wieder öffnen und eine Farbe speichern.

| Stand | Server | Namensfeld danach | Nächste Änderung |
| --- | --- | --- | --- |
| PR-Kopf | „Gespeichert“, Version 4 | „Nordlicht“ | `If-Match: "3"`, also 412 |
| Seite und Provider von main | „Gespeichert“, Version 4 | „Gespeichert“ | `If-Match: "4"`, gespeichert als Version 5 |

Folge: Kopfzeile, Vorschau und Formular zeigen den alten Namen, obwohl er gespeichert ist; der nächste Schritt läuft in einen Versionskonflikt. Über „Aktuellen Stand laden“ lässt sich das beheben, Daten gehen auf dem Server nicht verloren. Deshalb P3.

Eine Gegenprobe zeigt, dass `alive` für T1 gar nicht nötig ist: Ohne die Abfrage in `end()` bleibt der gelieferte T1-Test grün, weil `accept` die alte Version 9 verwirft, und P5 verhält sich wie auf main.

**Vorschlag:** Bei verlassener Seite eine erfolgreiche Antwort weiter an `accept` geben und nur die lokalen Zustände der Seite (Felder, Meldung, Fokus) auslassen; zum Beispiel in `run` und `loadCurrent` vor `if (!end()) return;` bei `outcome.kind === 'ok'` `accept(outcome.data)` aufrufen. Eine Regression nach dem Muster von P5 ergänzen.

## Antworten auf die Prüffragen

### 1. Keine Übernahme nach dem Verlassen, keine ältere Version im gemeinsamen Auftritt

**Ja für die Lesewege aus T1 und T2, mit B1 als Ausnahme beim Speichern.**

- Wiederholung T1 (Name): gelieferter Test grün am PR-Kopf, rot mit `AppearancePage.tsx` und `BrandProvider.tsx` von main. Nach Freigabe der alten Antwort bleibt „Neu gespeichert“, die nächste Änderung geht mit `"10"`.
- Eigene Variante P4 (Farbe statt Name): nach Verlassen, Neuladen und Speichern der Farbe als Version 10 ändert die späte Antwort mit Version 9 die Akzentfarbe nicht mehr.
- Wiederholung T2: gelieferter Test grün am PR-Kopf, rot mit der Seite von main. Ein Drop während des Lesens sendet nichts, das später gespeicherte Logo bleibt, die Entfernung geht mit `"10"`.
- `accept` lässt eine kleinere Version nicht mehr zu, gleiche Versionen werden übernommen. Der Vergleich greift nur, wenn beide ETags ganze Zahlen sind; der Server liefert immer solche.
- **Grenze 1:** Der Versionsvergleich hat keinen eigenen Test. Ohne ihn bleiben beide gelieferten Tests grün, weil `alive` und die Sperre die Fälle schon abfangen. Der Schutz „auf keinem Weg“ ruht damit im Test nur auf der Seite. Ein kleiner Test, der `accept` eine ältere Version anbietet, würde ihn festhalten (Hinweis 1).
- **Grenze 2:** Das eigene Laden des Providers (`round`, Zeile 75–95) setzt den Stand ohne Versionsvergleich. Es läuft heute nur beim Wechsel der Anmeldung und ist von der Seite aus nicht erreichbar; für eine strenge Lesart von „auf keinem Weg“ ist es aber ein zweiter Eingang (Hinweis 2).

### 2. Einstiege der Seite an der gemeinsamen Sperre vorbei

**Keinen gefunden.** Alle Schritte mit Serveranfrage laufen über `run` oder `loadCurrent` und damit über `begin()`; die Sperre ist ein `ref` und gilt schon im selben Ereignis.

| Einstieg | Ergebnis |
| --- | --- |
| Datei auswählen während des Konfliktlesens (P1, `setInputFiles` am verborgenen Feld) | kein PUT, die Leseantwort wird danach normal übernommen |
| Logo ablegen während ein Name gespeichert wird (P2) | kein PUT |
| Zweimal schnell Enter auf „Namen speichern“ (P3) | genau eine Änderung |
| Logo ablegen während des Lesens (gelieferter T2-Test) | kein PUT |
| „Logo entfernen“, „Alles zurücksetzen“ mit Bestätigung, Speichern von Name und Farbe | während `busy` deaktiviert und zusätzlich durch `begin()` geschützt |
| „Aktuellen Stand laden“ | `begin()` blendet die Meldung samt Schaltfläche aus |

Lokal bleiben die Auswahl der Farben, „Standard“ beim Namen und das Tippen in die Felder auch während eines Vorgangs möglich; sie senden nichts. Die Mailvorlage auf derselben Seite hat ihre eigene Version und berührt den gemeinsamen Auftritt nicht. Wie in der Übergabe genannt, wird eine Ablage oder Dateiauswahl während eines Vorgangs ohne Meldung übergangen.

### 3. `together` unter Last

**Ja, der Nachweis ist jetzt tragfähig.** Eine Testverbindung hält `appearance_setting` mit `FOR UPDATE`, beide Aufträge melden sich bereit, und die Sperre wird erst freigegeben, wenn `pg_stat_activity` zwei Anfragen mit `wait_event_type = 'Lock'` auf diese Tabelle zeigt. Die eigene Abfrage der Beobachtung wartet nicht und zählt daher nicht mit; die haltende Verbindung ist untätig in der Transaktion und zählt ebenfalls nicht.

- `AppearanceApiTest` 25 von 25 grün; der Nebenläufigkeitstest dreimal unter Last (acht Endlosschleifen auf vier Kernen) grün.
- **Grenze:** Das wirksame Zeitfenster sind nicht die 20 Sekunden der Schleife, sondern `lock_timeout = 5s` aus `application.yaml`. Eine Abwandlung, die drei wartende Anfragen verlangt, zeigt das: Beide Anfragen werden nach 5 Sekunden mit 55P03 abgebrochen, und der Test meldet am Ende „expected 3 but was 0“. Erreicht die zweite Anfrage die Sperre unter starker Last mehr als 5 Sekunden nach der ersten, scheitert der Test mit einer irreführenden Meldung. In meinen Läufen lag das weit außerhalb; der Kommentar sollte die Grenze aber nennen (Hinweis 3).
- Bei einem Fehlschlag von `ready.await` würden beide Aufträge auf `start` warten und `pool.close()` hält den Test an, weil der Pool vor der haltenden Verbindung geschlossen wird. Sehr unwahrscheinlich; mit umgekehrter Reihenfolge der Ressourcen oder `shutdownNow` im Fehlerfall ausgeschlossen (Hinweis 3).

### 4. Wahrheitswerte und Vorrang des Logotypfehlers

**Im verlangten Umfang vollständig.**

| Fall | Server (`wrongJsonTypesAreRefusedLikeTheTestStandIn`) | Testersatz (Browsertest) |
| --- | --- | --- |
| `{"accentColor":true}` | 400 `appearance.color_invalid` | 400 `appearance.color_invalid` |
| `{"name":true}` | 200, Name `"true"` | 200, Name `"true"` |
| `{"name":false}` | 200, Name `"false"` | nicht geprüft |
| PNG als `image/jpeg` mit veralteter ETag `"1"` | 415 `appearance.logo_type_invalid` | 415 |
| Unbekannte Signatur mit veralteter ETag | 415 | 415 |
| Gültiges PNG mit veralteter ETag (Gegenprobe zur Reihenfolge) | 412 im Nebenläufigkeitstest | 412 |

Die Version steigt am Server danach genau auf 4 (42, true, false), damit ist auch kein Speichern durch die abgelehnten Fälle möglich. Kleine Lücke: `{"name":false}` fehlt im Testersatz; das Verhalten stimmt dort nach dem Code (`String(value)`). `appearance.logo_unreadable` bildet der Testersatz weiter nicht nach, das ist als Grenze dokumentiert.

## Hinweise ohne Abnahmeblocker

1. Einen Test für `BrandProvider.accept` mit älterer, gleicher und neuerer Version ergänzen, damit der Versionsvergleich auch ohne die Schutzschichten der Seite belegt ist.
2. Das eigene Laden des Providers könnte denselben Vergleich nutzen, damit die Zusage „nie eine ältere Version“ ohne Ausnahme gilt.
3. In `together` die Grenze durch `lock_timeout` im Kommentar nennen und die haltende Verbindung vor dem Pool schließen.
4. Außerhalb von TASK-090: Ein Sperrablauf am Auftritt endet als 500 „Unexpected error“, während andere Module 55P03 als wiederholbares 503 melden. Das passt nicht zu den übrigen Schnittstellen und gehört in eine spätere Aufgabe.

## Tatsächlich ausgeführte Prüfungen

- `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `python3 -m unittest discover -s tests -v`, `git diff --check`: grün (38 Python-Tests).
- Frontend `npm ci` und `npm run verify`: 83 Tests, Typprüfung und Build grün (Node 22.23.4 statt der Projektvorgabe).
- `npm run test:browser` mit `TASKMANAGER_BROWSER=/opt/pw-browsers/chromium`: 207 von 207 grün.
- Gelieferte Tests zu T1 und T2 mit Seite und Provider von main: beide rot. Gegenproben ohne `alive` und ohne Versionsvergleich wie in den Belegen.
- Eigene Browserproben P1 bis P5 gegen den Produktionsbuild mit dem Testersatz.
- Backend mit JDK 21 (`-Djava.version=21`) gegen lokales PostgreSQL 16: `AppearanceApiTest` 25 von 25, Lastläufe und Abwandlung wie oben. Vollständige Testsuite: 542 Tests, 541 grün; einzig `DatabaseFoundationTest.runsAgainstThePinnedPostgresqlVersion` scheitert erwartungsgemäß an der lokalen Version 16.15. Kein Lauf gegen die festgelegte PostgreSQL 18.6 und kein Docker.

## Empfehlung an Holger

**Nachbessern mit B1**, dann ist der Stand aus meiner Sicht abnahmefähig. B1 ist klein und lässt sich mit einer Zeile in `run` und `loadCurrent` sowie einem Browsertest beheben; die Hinweise 1 bis 3 können im selben Zug mit. Alternativ kann Holger B1 als bekannte Grenze annehmen, weil ein Speichern selten genau beim Verlassen der Seite endet und „Aktuellen Stand laden“ den Zustand wiederherstellt.

Über die gemeinsame Abnahme von TASK-081, TASK-087, TASK-089 und TASK-090 entscheidet Holger. Diese Sitzung nimmt nichts ab.
