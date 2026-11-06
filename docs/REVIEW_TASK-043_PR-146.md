# Unabhängige Prüfung TASK-043 / PR #146

**Ergebnis:** Vier nachvollziehbare Befunde: **F1 P2**, **F2 bis F4 P3**. Die vorgeschriebenen Tests sind erfolgreich; eigene Proben zeigen zusätzliche Lücken. Dieser Bericht erklärt keine Abnahme. Die fachliche Abnahme bleibt Holger vorbehalten.

- Prüfung: 22.–23. Oktober 2026, Sitzung `codex-20261022-task043-review-01`, getrennte KI nach DEC-040, eigener Klon und Branch `review/task-043-pr-146`.
- Ergebnis-PR: [#146](https://github.com/1uedev/task-manager/pull/146), Head `5621dc45cb642278a16e1163459e29073d57958b`, Basis `d04926b9a1c0224accbe9fe1ec1e75d436c1c16b`.
- Geprüfter main: `a210423b82fcc6c76dacbb0be67ff7f77e4e98f3` (Merge von #146). Sein Produktbaum entspricht dem PR-Head. main unmittelbar vor der Berichterstellung erneut abgeglichen, unverändert.
- Maßstab: AGENTS.md, README.md, TODO.md TASK-043, DONE.md, AI_WORKFLOW, TECHNICAL_BASELINE, relevante Anforderungen und Modulverträge, DEC-072, backend/PERMISSIONS.md einschließlich Härtung sowie P2 bis P7 aus [TASK-025 / PR #74](REVIEW_TASK-025_PR-74.md). CLAUDE.md berücksichtigt.
- TASK-043 bleibt bei seiner bisherigen Sitzung BEREIT ZUR PRÜFUNG; die eingetragenen Abhängigkeiten TASK-025, TASK-039 bis TASK-042 und TASK-030 sind als abgenommen verzeichnet. Dieser ausdrücklich beauftragte Review übernimmt keine Implementierungsaufgabe. Produktcode, TODO.md und DONE.md wurden nicht geändert.

## F1 — P2: Animiertes WebP ohne Bilddaten wird als vollständiges Logo gespeichert

**Fundstelle:** [Logo.java:82–112](../backend/src/main/java/taskmanager/appearance/internal/Logo.java), insbesondere Zeile 106: `case "ANMF" -> image |= size >= 16`.

Die Prüfung wertet bereits den 16 Byte langen ANMF-Frameheader als Bild. Sie verlangt weder Bilddaten im Frame noch die nötige Animationsstruktur. Ein ANMF-Chunk besteht jedoch aus Frameheader **und** Frame-Daten mit Bild-Bitstream; das ist bereits eine Anforderung an den Containeraufbau, keine Forderung nach einem vollständigen WebP-Decoder. Siehe [WebP RIFF Container Specification, ANMF](https://developers.google.com/speed/webp/docs/riff_container#animation_frame_anmf).

**Reproduktion:** Als berechtigte Erscheinungsbildverwaltung mit aktuellem ETag ein Logo vom Typ `image/webp` hochladen. Die folgende vollständige Datei hat 36 Byte und enthält nur RIFF/WEBP und einen leeren ANMF-Frameheader:

```text
524946461c00000057454250414e4d461000000000000000000000000000000000000000
```

Der echte Server antwortet **200**, speichert das Logo und erhöht die Erscheinungsbildversion von 9 auf 10. Eine zweite, 68 Byte lange Datei mit VP8X, ANIM und ANMF, aber ebenfalls ohne Frame-Bilddaten, liefert erneut 200 und Version 11. Pillow und der tatsächliche Chrome-Browser können beide Dateien nicht dekodieren; im Browser ist die natürliche Bildbreite 0. Bytes, Antworten und unabhängige Dekodierversuche stehen in [http-results.json](review-evidence/task-043-pr-146/http-results.json) und [ui-results.json](review-evidence/task-043-pr-146/ui-results.json).

**Auswirkung:** Eine kaputte, nicht darstellbare Datei ersetzt ein funktionierendes Firmenlogo, obwohl DEC-072 für unvollständige Bilder 415 vorsieht. Kein Nachweis einer Rechteausweitung oder eines Decoderangriffs.

**Vorschlag:** Animationsstruktur und ANMF-Unterchunks prüfen, insbesondere tatsächlich vorhandenen VP8-/VP8L-Bildinhalt sowie erforderliche Chunks, Grenzen und Reihenfolge. Beide Varianten mit 415 `appearance.logo_unreadable` ohne Versions- oder Protokolländerung verweigern; gültige statische und animierte WebP-Dateien als Gegenproben erhalten. Die dokumentierte Grenze „WebP strukturell, ohne vollständigen JDK-Decoder“ bleibt vertretbar, deckt diese Containerlücke aber nicht ab.

## F2 — P3: Neuer Gruppenlesepfad unterscheidet fremdes Projekt von fehlender Kennung

**Fundstelle:** [PermissionsController.java:242–245](../backend/src/main/java/taskmanager/PermissionsController.java) und `scope` in Zeilen 303–310. `projects.key(project)` prüft die Existenz vor der anschließenden Berechtigungsprüfung.

**Reproduktion:** Ein angemeldetes internes Konto ohne Mitgliedschaft und ohne Verwaltungsrechte für Projekt Y ruft auf:

1. `GET /api/v1/permissions/projects/{Y}/groups` → **403 `auth.forbidden`**.
2. Dieselbe Route mit einer zufälligen, nicht vorhandenen UUID → **404 `project.not_found`**.
3. Zum Vergleich `GET /api/v1/projects/{Y}` mit demselben Konto → **404**.

Alle Antworten wurden am echten Backend erzeugt; Beleg `project_existence` in [http-results.json](review-evidence/task-043-pr-146/http-results.json), Ablauf in [http_probes.py](review-evidence/task-043-pr-146/http_probes.py).

**Auswirkung:** Wer eine Projekt-UUID kennt oder erhält, kann ihre Existenz bestätigen, obwohl das normale Projekt-API sie verbirgt. Es werden keine Projektnamen, Regeln oder Mitglieder ausgelesen und keine Änderungen ermöglicht. Wegen dieser begrenzten Metadatenoffenlegung P3.

**Vorschlag:** Für ein dem Konto verborgenes Projekt dieselbe Antwort wie für ein nicht vorhandenes Projekt liefern, auch im neuen Lesepfad. Sichtbarkeit und Verwaltungsberechtigung unter der geltenden Sperrreihenfolge prüfen. Regression mit fremder vorhandener und nicht vorhandener UUID sowie einem berechtigten Projektverwalter ergänzen.

## F3 — P3: Mit Logo verschwindet der Markenname auch aus dem zugänglichen Baum

**Fundstelle:** [styles.css:361](../frontend/src/styles.css) (`display: none` für `.brand:has(.brand-logo) .brand-name`) und Zeilen 500–503 (mobile Regel ohne Rücknahme von `display: none`); [App.tsx:69–78](../frontend/src/app/App.tsx), generischer Logo-Alternativtext in [de.ts:16](../frontend/src/i18n/de.ts).

**Reproduktion:** In der echten Oberfläche ein gültiges PNG als Logo speichern und das Browserfenster auf 320 Pixel Breite setzen. Der Markenbereich liefert im Accessibility-Snapshot nur `img "Firmenlogo"`; der Markenname hat berechnet `display: none` und fehlt. Nach Entfernen des Logos liefert derselbe Bereich `text: Task Manager`. In beiden Fällen kein horizontaler Überlauf. Beleg `appearance-real` in [ui-results.json](review-evidence/task-043-pr-146/ui-results.json).

**Auswirkung:** Die Überlagerung ist behoben, das ausdrückliche Ziel aus DEC-072, den Markennamen für Bildschirmleser beizubehalten, wird bei vorhandenem Logo verfehlt. Dies ist ein Browser-/Accessibility-Baum-Nachweis, kein manueller Test mit einem Bildschirmleseprogramm.

**Vorschlag:** Den Namen bei vorhandenem Logo visuell verstecken, ohne `display: none`, oder dem Logo einen gleichwertigen zugänglichen Namen geben. Beide Varianten bei 320 Pixeln prüfen: mit Logo und ohne Logo. Der generische Text „Firmenlogo“ allein vermittelt den Markennamen nicht.

## F4 — P3: Unbekannter Vorgänger älterer Feldregeln wird als „Nichts“ dargestellt

**Fundstelle:** [GroupsPage.tsx:93–96](../frontend/src/pages/GroupsPage.tsx), Übersetzungen `audit.previous.none` / `audit.previous.unknown` in [de.ts:367–368](../frontend/src/i18n/de.ts). V0016 ergänzt einen nullable Vorgängerwert; laut DEC-072 und PERMISSIONS.md haben ältere Einträge keinen solchen Wert.

**Reproduktion:** Auf der ausschließlich synthetischen Testdatenbank einen tatsächlich protokollierten Wechsel einer Feldregel **DENY → ALLOW** wählen und für diesen einen Eintrag den Vorgänger auf NULL setzen. Das bildet die fehlende Information eines Eintrags vor V0016 nach; es ist kein behaupteter API-Schreibzugriff auf das Protokoll. [legacy_fixture.sql](review-evidence/task-043-pr-146/legacy_fixture.sql) und [legacy-fixture.json](review-evidence/task-043-pr-146/legacy-fixture.json) zeigen Auswahl, bekannten ursprünglichen Vorgänger und NULL. In der echten Oberfläche über „Ältere Einträge laden“ bis zu diesem markierten Eintrag blättern. Die Spalte „Vorher“ zeigt **„Nichts“**, nicht „Nicht erfasst“. Tatsächlicher Zeilentext in [legacy-ui-results.json](review-evidence/task-043-pr-146/legacy-ui-results.json).

Der Fallback behandelt nur `effect === 'remove'` als unbekannt. Ein älterer ALLOW-/DENY-Eintrag kann aber ebenfalls einen anderen Zustand ersetzt haben. Aus NULL lässt sich kein fehlender Vorgänger ableiten.

**Auswirkung:** Das Änderungsprotokoll behauptet einen nicht belegten Ausgangszustand. Das betrifft die Nachvollziehbarkeit bestehender Installationen, nicht die korrekt gespeicherten neuen `previousEffect`-Werte.

**Vorschlag:** „Kein Vorgänger“ und „Vorgänger nicht erfasst“ im Vertrag und in der Darstellung eindeutig unterscheiden, etwa durch eine Verfügbarkeitsmarkierung für alte Einträge. Unbekannte alte ALLOW-, DENY- und Entfernungs-Einträge entsprechend anzeigen. Ein Datum allein ist kein belastbarer Ersatz für diese Information.

## Geprüfte Rechtegrenzen und Nebenläufigkeit

- **P3 / externe Konten:** Eigene Kernprobe mit einem synthetisch gültigen externen Kontext, der sogar `permissions.manage` im Projekt hält: gewöhnliche Prüfung erlaubt dieses Recht, aber `projectGroups`, `setAction` und `setField` verweigern die Verwaltung. Die produktiven Identity-/HTTP-Tests liefen zusätzlich im vollständigen Backendlauf. Die Kernprobe ersetzt den Identitätsport ausschließlich in ihrem isolierten CLI-Testkontext; sie ist kein Nachweis eines realen Portal-Loginwegs.
- **Projektdelegation:** Eigene reale HTTP- und Browserproben: nur Regeln des eigenen Projekts, keine Mitglieder, Unternehmensregeln oder Regeln des zweiten Projekts. Angebotene Aktionen und Felder entsprechen den gehaltenen Rechten. Acht unerlaubte Lese-/Schreibvarianten antworten 403, darunter globale Gruppen-/Detail-/Protokollabfrage, Mitgliederänderung, nicht gehaltene Aktion, fremdes Projekt, Unternehmensrecht und nicht gehaltenes Feld. Erlaubte Änderung im eigenen Projekt 204; Entfernen eines von der Unternehmensverwaltung gesetzten Verbots 403. Der UI-Reiter führte zwei echte erlaubte Änderungen mit 204 aus. Keine weitergehende Umgehung gefunden; begrenzte Existenzoffenlegung siehe F2.
- **P4:** Gruppenname mit abweichender Groß-/Kleinschreibung am echten Backend 409 `permissions.group_name_taken`.
- **P5:** Eigene Spring-/PostgreSQL-Kernprobe verwendet die unveränderte Anwendungskonfiguration: Verbindungsstandard tatsächlich `5s`; während der Ressourcenprüfung `5s`; danach die vorherigen Werte `0`, `1234ms` und `37s` exakt wiederhergestellt, auch bei verweigertem `inspect`. Anschließender Poolzugriff wieder `5s`. Eine abgebrochene Datenbanktransaktion lässt sich nach Sperrfehlern nicht durch eine Wiederherstellung „heilen“; die Fehler müssen weiterhin die ganze Transaktion beenden. Die dokumentierte Beschränkung auf einen Transaktionsmanager ohne `REQUIRES_NEW` bleibt maßgeblich.
- **Commit-Prüfung:** Eigene Kernprobe: Ablauf nach `require` vor Commit rollt einen Marker zurück; nächste Transaktion funktioniert, also keine beobachtete verschleppte Deadline; mehrere Prüfungen behalten das früheste Ende. Eigene reale HTTP-Probe: gültige Sitzung, Ablauf in zwei Sekunden, Ticketzeile durch eine zweite PostgreSQL-Verbindung etwa drei Sekunden gesperrt, danach PATCH mit **403 `auth.forbidden` nach 2,95 Sekunden**, Titel und Version unverändert, Korrelationskennung vorhanden. Eigener Passwortreset antwortet 200; die eigene Sitzungsentziehung rollt diesen Vorgang nicht fälschlich zurück.
- **Sperrfolge / Entzug:** Die Prüfung und die weiter gehaltenen Rechte-, Identitäts- und Ressourcensperren wurden im Code mit den vorhandenen Nebenläufigkeitstests abgeglichen und diese selbst ausgeführt. Ein Entzug wartet bis zum Ende der geschützten Transaktion; die Commit-Synchronisierung ergänzt den zeitlichen Ablauf, statt unter gehaltenen Sperren einen unmöglichen parallelen Entzug zu simulieren. In den ausgeführten Abläufen keine neue Verklemmung oder Commit-Umgehung beobachtet. Dies ist keine vollständige Untersuchung aller möglichen Zeitpläne.
- **Tagzeitpunkt:** `TagsApiTest.anAssignmentThatWaitedForAFieldChangeCarriesALaterTime` (Zeilen 678–719) lief im eigenen Backendlauf. Er beobachtet die tatsächliche Warteposition an `select version from ticket`, gibt danach die sperrende Feldänderung frei und vergleicht Historien- und Zuordnungszeit. Das belegt die relevante Wartebeziehung; der Test stützt sich nicht nur auf die enthaltene kurze Pause. Keine zusätzliche eigene Tag-Stressmessung.
- **P6 / Protokoll:** Eigene Kern- und HTTP-Proben: identische Mitgliedschaft/Aktion/Feldregel und Entfernen einer nicht vorhandenen Regel ohne Versionszuwachs; gleiche Feldregel desselben Urhebers unverändert; anderer Urheber sowie Rollenwechsel derselben Person von Unternehmensverwaltung zu Projektdelegation zählen als Änderung, mit richtigem `previousEffect`. 174 Protokolleinträge über `before` mit Seitengröße 17 vollständig, ohne Duplikate und streng fallende Rechteversionen gelesen; Anzahl mit Datenbank verglichen. Echte UI lädt 50, 100 und 150 Einträge. Alte unbekannte Vorgänger siehe F4.
- **Erscheinungsbild:** Acht eigenständig erzeugte und dekodierte gültige Dateien akzeptiert: PNG RGB/Alpha, JPEG Baseline/Progressive/CMYK, WebP Lossy/Lossless/Animation. Die vorhandene Regression gegen abgeschnittene und strukturell falsche Dateien lief erfolgreich. Weitere kaputte Varianten siehe F1. Bei sichtbarem ungültigem Farbwert ist Speichern gesperrt, Enter löst keinen Schreibzugriff aus; gültige Farbe am echten Backend gespeichert.

## Tatsächlich ausgeführte Prüfungen und Grenzen

Ausführung in der verlangten Reihenfolge, jeweils auf unverändertem Produktcode:

| Prüfung | Eigenes Ergebnis |
| --- | --- |
| Backend `mvnw verify`, Java 25, eigene leere PostgreSQL-18.6-Instanz über dokumentierte Testparameter | **217 Tests, 0 Fehler, 0 übersprungen, BUILD SUCCESS**, 4:26 Minuten |
| Frontend `npm ci`, danach `npm run verify` | Vertragsgenerierung, Typprüfung, **68 Tests**, Produktionsbuild erfolgreich; keine generierten Produktänderungen |
| Browsertests mit `--test-concurrency=1`, nach Frontendprüfung | **75 Tests / 11 Suiten, 0 Fehler, 0 übersprungen**, 67,67 Sekunden |
| Eigene `CoreProbes.java`, `http_probes.py`, `ui_probes.mjs` und ältere Protokollfixture | Ergebnisse und reproduzierbare Quellen im [Belegverzeichnis](review-evidence/task-043-pr-146/verification.md) |
| Repositoryvalidierung, Aufgabenübersicht, Python-Tests, Diffprüfung | Siehe abschließendes Prüfprotokoll im Belegverzeichnis |

PostgreSQL wurde nativ betrieben; dies ist der dokumentierte lokale Testserverweg, **kein Docker-/CI-Image-Nachweis**. Zusätzliche Browserproben liefen mit Produktionsbuild und echtem Backend über einen lokalen Durchleitungsserver, nicht mit API-Mocks. Keine Änderung des Produktcodes, keine Rücknahme von Korrekturen für einen eigenen Rot-vor-Grün-Vergleich, kein neuer vollständiger Lebenszyklus-/Image-Test. Die Behauptung des Autors, jede Regression gegen den ungefixten Stand geprüft zu haben, wurde deshalb nicht als eigener Nachweis übernommen. Ein vollständiger WebP-Decoder, Lasttest, manueller Bildschirmlesertest und vollständige EN-Browserabnahme waren nicht Gegenstand der eigenen Zusatzproben.

P7 (jede Unternehmensgruppe als Delegationsziel), unternehmensweite Kontextinvalidierung, die noch ausstehende Messung der `inspect`-Zeilensperre und OPEN-014 sind dokumentierte offene Grenzen, **keine neuen Befunde dieses Reviews**. Insbesondere wird das Anzeigen aller Gruppennamen nicht als zusätzliche Rechteoffenlegung bewertet; DEC-072 bestätigt diesen Umfang ausdrücklich.

**Übergabe:** F1 bis F4 zur Korrektur und anschließenden gezielten Nachprüfung vorlegen. Die erfolgreich ausgeführten Tests ersetzen diese Befunde nicht. Keine fachliche Abnahme und keine Statusänderung durch diesen Bericht.
