# Prüfbelege TASK-090 / PR #268

Gehört zu docs/REVIEW_TASK-090_PR-268.md. Alle Daten sind synthetisch (Browser-Testersatz `tests/browser/backend.mjs`).

## Browserproben

`probe-task090.test.mjs` nach `frontend/tests/browser/` kopieren, im Frontend `npm run verify` (Build) ausführen und dann:

```
TASKMANAGER_BROWSER=/opt/pw-browsers/chromium node --test tests/browser/probe-task090.test.mjs
```

| Probe | PR-Kopf c028119 | Seite und Provider von main |
| --- | --- | --- |
| P1 Datei auswählen während des Konfliktlesens | kein Logo-PUT, grün | nicht wiederholt |
| P2 Logo ablegen während ein Name gespeichert wird | kein Logo-PUT, grün | nicht wiederholt |
| P3 zweimal Enter schnell auf „Namen speichern“ | genau ein PUT, grün | nicht wiederholt |
| P4 späte Antwort einer verlassenen Seite nach neu gespeicherter Farbe | Farbe bleibt, grün | nicht wiederholt |
| P5 erfolgreiches Speichern, Antwort erst nach dem Verlassen | `{"serverName":"Gespeichert","serverVersion":4,"before":3,"field":"Nordlicht","nextIfMatch":"\"3\""}` | `{"serverName":"Gespeichert","serverVersion":5,"before":3,"field":"Gespeichert","nextIfMatch":"\"4\""}` |

P5 gibt nur Werte aus und prüft nichts; die Auswertung steht im Bericht unter B1.

## Gegenproben an den gelieferten Browsertests

| Stand der Seite | Test T1 | Test T2 |
| --- | --- | --- |
| PR-Kopf | grün | grün |
| AppearancePage.tsx und BrandProvider.tsx von main | rot | rot |
| PR-Kopf ohne Abfrage `alive` in `end()` | grün | nicht ausgeführt |
| PR-Kopf ohne Versionsvergleich in `accept` | grün | grün |

## Backendproben

- `AppearanceApiTest`: 25 von 25 grün (PostgreSQL 16, JDK 21, `-Djava.version=21`).
- `concurrentRequestsWithoutAChangeLeaveVersionAndAuditAndAStaleOneIsRefused` dreimal unter Last (acht Endlosschleifen auf vier Kernen): 3 von 3 grün.
- Abwandlung: `together` verlangt drei statt zwei wartende Anfragen. Der Test endet nach rund 21 Sekunden mit `expected: <3> but was: <0>`; beide Anfragen wurden vorher nach 5 Sekunden mit `55P03 canceling statement due to lock timeout` abgebrochen und im Log als `Unexpected error` gemeldet.
