# Nachweise der Frontend-Nachprüfung TASK-023 / PR #72

Sitzung `codex-20261020-frontend-followup-01`, 20. Oktober 2026. Gegenstand und Einordnung im [Prüfbericht](../../REVIEW_TASK-023_PR-72.md); gekürzte tatsächliche Ergebnisse in [results.txt](results.txt).

Geprüfte Stände:

- Historischer Korrekturstand: `78eed607f339dc61a66606833205da17b8433627` (finaler Head PR #72), eigener Windows-Arbeitsbereich mit Leerzeichen und Umlaut.
- Heutiges main: `3e3349ec7cfd8854138af6c4d9b405d33e4a32f2`, eigener Berichtszweig. Keine Änderungen an seinem Produktcode.

## Positive Prüfungen

In jedem eigenen Checkout unter `frontend` zuerst `npm ci --ignore-scripts`, dann `npm run verify` und `npm run test:browser` ausführen. Node 24.22.0 / npm 11.19.1 verwenden; TASKMANAGER_BROWSER kann auf einen lokalen Chromium-Browser zeigen. Geprüft wurden die unveränderten Suiten mit 59/21 beziehungsweise 71/45 Fällen.

Die [followup-probe.mjs](followup-probe.mjs) importiert die tatsächlichen Quelldateien aus dem übergebenen Frontendverzeichnis. Sie prüft Parser und Formular, die alten Logo-Gegenbeispiele, die ursprüngliche Schwarz-Weiß-Palette und die Pfadgrenzen des eigenen lokalen Testservers. Ein vorhandener Build aus `npm run verify` und installierte Testabhängigkeiten sind deshalb erforderlich. Keine externe HTTP-Anfrage; der eigene Testserver wird im finally-Block geschlossen.

```sh
node docs/review-evidence/task-023-pr72/followup-probe.mjs frontend current
```

Für den historischen Teststand dasselbe Skript aus dem Berichtszweig aufrufen und als erstes Argument dessen eigenes Frontendverzeichnis übergeben. Das Skript verändert keine Quell- oder Produktdateien.

## Drei getrennte Gegenproben

Die folgenden Patches nur in einem separaten Checkout des **historischen PR-#72-Heads** einzeln anwenden und jeweils vollständig zurücknehmen:

- [decimal-mutation.patch](decimal-mutation.patch): führt das Entfernen innerer Leerzeichen wieder ein. Unter frontend: `node --test tests/format.test.ts`; 7 Tests, 2 erwartete Fehlschläge.
- [logo-mutation.patch](logo-mutation.patch): nimmt vermeintlich lokale Rohpfade vor der URL-Prüfung allein anhand des Präfixes an. Unter frontend: `node --test tests/brand.test.ts`; 11 Tests, 1 erwarteter Fehlschlag.
- [contrast-mutation.patch](contrast-mutation.patch): setzt die Hinweisfarbe wieder auf das feste `#4a4a4a`. Dieselben 11 Brandingtests ergeben 3 Fehlschläge. Nach `npm run build` ergibt `npm run test:browser` 21 Fälle, genau 1 erwarteten Fehlschlag am gemessenen Hinweis-Kontrast von 2,369563:1.

Die Patches haben keinen Kontext und werden vom jeweiligen Repository-Stamm mit `git apply --unidiff-zero <patch>` angewendet; zur Rücknahme `git apply -R --unidiff-zero <patch>` benutzen. Alternativ die eigenen vorher gesicherten Originaldateien bytegleich wiederherstellen. Danach **vollständig neu bauen**, `npm run verify` und `npm run test:browser` wiederholen, damit keine absichtlich fehlerhafte Buildausgabe zurückbleibt. Bei dieser Prüfung wurden beide geänderten Quelltexte über ihre SHA-256-Werte mit den eigenen Originalkopien abgeglichen und alle 59/21 Fälle erneut bestanden.
