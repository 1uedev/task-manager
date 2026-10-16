# Entwicklung und Prüfung mit mehreren KI-Sitzungen

Stand 16. Oktober 2026 · TASK-011 / TASK-015 · Review-Weg bestätigt, Detailverfahren vorgeschlagen

Codex und Claude Code sollen kleine, nachvollziehbare Arbeitsergebnisse liefern können. Entscheidend sind prüfbare Änderungen und klare Zuständigkeiten. Zwei zustimmende Modelle ersetzen keine Fachabnahme und beweisen keine technische Korrektheit.

Das bestehende Reservierungsverfahren in [AGENTS.md](../AGENTS.md) und [AI_WORKFLOW.md](AI_WORKFLOW.md) gilt bereits für diese Planungsarbeit. DEC-040 bestätigt zunächst gegenseitige KI-Reviews, auch für kritische Änderungen; zusätzliche menschliche technische Prüfung bleibt ausdrücklich offen. Die übrigen Rollen, Risikostufen und Freigabeschwellen unten sind **Vorschläge** und werden erst nach entsprechender Entscheidung verbindlich. Dieses Dokument erteilt weder einen Programmierauftrag noch neue Befugnisse zur Veröffentlichung, Beauftragung von Personen oder Nutzung kostenpflichtiger Dienste.

Bezug: REQ-DEV-001 bis REQ-DEV-005, REQ-SEC-001. Aufgabenstatus bleibt ausschließlich in [TODO.md](../TODO.md), abgenommene Ergebnisse in [DONE.md](../DONE.md).

## Verantwortlichkeiten

| Rolle | Aufgabe | Besetzung / Grenze |
|---|---|---|
| Produktverantwortlicher | Umfang, fachliche Beispiele, Prioritäten und Abnahme entscheiden | Holger Gerlach bestätigt; grundlegendes Coding-Verständnis ist keine behauptete Sicherheitsexpertise |
| Koordination | Freie Aufgabe prüfen, Reservierung integrieren, Überschneidungen auflösen, Übergaben dokumentieren | Holger oder ausdrücklich benannte Sitzung gemäß bestehendem Ablauf; Berechtigung gilt für den beauftragten Umfang |
| Autor | Anforderungen lesen, Änderung umsetzen, Eigenprüfungen und Einschränkungen liefern | Eindeutige KI-Sitzung oder Person pro Aufgabe; eigenes Arbeitsverzeichnis |
| Prüfende KI | Konkreten Änderungsstand gegen Anforderungen und Fehlerfälle prüfen | Gegenseitige KI-Reviews bestätigt (DEC-040); Autor und prüfende KI unterscheiden. Konkrete Sitzung durch Koordination zuordnen; Werkzeugkombination noch festzulegen |
| Zusätzlicher menschlicher Reviewer | Bei späterer Entscheidung technische Beurteilung ergänzen | Ausdrücklich offen (DEC-040); keine verpflichtende menschliche Freigabestufe beschlossen und keine Person beauftragt |
| Integration / Release | Prüfergebnisse zum richtigen Commit abgleichen, Integration beziehungsweise Auslieferung im genehmigten Umfang durchführen | Koordination; Integration ist keine fachliche Abnahme und keine automatische Releasefreigabe |

Der anfängliche technische Review-Weg ist entschieden: KI-Sitzungen prüfen gegenseitig ihre Änderungen, Holger nimmt fachlich ab. Menschliche technische Prüfung wird weder als bereits erfolgt noch als verpflichtende Voraussetzung behandelt. Offen bleiben konkrete Zuordnung, Prüfbelege und Freigabeschwellen. Ein KI-Review besteht aus nachvollziehbaren Befunden und überprüften Fehlerfällen; bloße Zustimmung eines Modells genügt nicht. Die Entscheidung ist kein Programmier- oder Auslieferungsauftrag.

## Eine Aufgabe muss konkret prüfbar sein

Nach einem gesonderten Implementierungsauftrag erhält jede Entwicklungsaufgabe: REQ-Bezug, genaues Nutzerverhalten, erlaubten Umfang, betroffene Schnittstellen, positive und negative Prüffälle, zuständige Sitzung, Abhängigkeiten und nächsten Schritt. Die vorhandenen TASK-IDs bleiben historisch erhalten; neue Aufgaben bekommen neue IDs. Planungstasks werden nicht still zu Implementierungsaufgaben umgedeutet.

Beispiel für eine spätere Aufgabe: „Berechtigter interner Benutzer erfasst Zeit auf einem vorhandenen Ticket; unberechtigter Benutzer erhält über UI und API keinen Zugriff; Korrektur bleibt historisch nachvollziehbar.“ Ein Titel wie „gesamte Zeiterfassung bauen“ ist ohne abgegrenztes Verhalten zu groß. Ein Dialog oder eine Typdatei allein genügt umgekehrt nicht als Nachweis eines vollständigen Fachablaufs.

Neue Abhängigkeiten, Datenbankschemata oder API-Verträge werden vor paralleler Nutzung benannt. Für gemeinsam bearbeitete Verträge reserviert die Koordination einen eigenen Änderungsschritt. Andere Aufgaben referenzieren dessen freigegebenen Stand; sie erfinden keine zweite Version desselben Vertrags.

## Ablauf eines späteren Entwicklungsbeitrags

1. **Reservieren:** aktuellen main lesen, freie Aufgabe prüfen, Claim-PR integrieren lassen. Keine inhaltliche Arbeit aufgrund eines nur geöffneten Claims beginnen.
2. **Abgrenzen:** eigenen Worktree erstellen; erwartetes Verhalten, Risikostufe und Schnittstellenänderungen im PR benennen. Kein ungefragter Technologiewechsel.
3. **Erstellen und selbst prüfen:** kleine Änderung mit den zum Risiko passenden Tests. Tatsächliche Befehle, Ergebnisse und Grenzen dokumentieren; keine erfundenen Screenshots oder Ausführungen.
4. **Gezielt gegenprüfen:** prüfende Sitzung erhält Aufgaben-ID, Commit, Anforderungen und Diff. Sie sucht konkrete Fehler, vergessene Rechte, Nebenwirkungen und unzureichende Tests. Der Autor beantwortet Befunde am Code beziehungsweise mit Nachweis.
5. **Technisch beurteilen:** vorgeschlagene zusätzliche Prüfung nach folgender Risikomatrix. Ein offener Befund erhält Schwere, Auswirkung und Entscheider; „KI sagt okay“ schließt ihn nicht.
6. **Integrieren:** aktuellen main einarbeiten, relevante Prüfungen am neuen Stand durchführen und erforderliche CI abwarten. Review bezieht sich auf einen Commit; nach Änderungen betroffene Stellen erneut prüfen.
7. **Fachlich abnehmen:** Holger bekommt eine reproduzierbare Demonstration oder bei Dokumenten klare Entscheidungsbeispiele. Bis zur Abnahme bleibt die Aufgabe BEREIT ZUR PRÜFUNG. Erst danach erfolgt der nachvollziehbare Übergang nach DONE.

Fachliche Abnahme kann vor oder nach Integration erfolgen, muss aber den tatsächlich gelieferten Stand benennen. Für eine spätere Kundenauslieferung sind zusätzlich Versionsstand, offene Fehler, Installations-/Updateweg und erforderliche Betriebsnachweise zu prüfen. Ein grüner Entwicklungsbranch ist kein Kundenrelease.

## Vorgeschlagene Prüftiefe nach Risiko

| Änderung | Erforderliche Nachweise nach Freigabe dieses Vorschlags | Zusätzliche Prüfung |
|---|---|---|
| Planung, Übersetzung, statischer Text oder rein visuelle Korrektur | Dokument-/Linkprüfung beziehungsweise sichtbare Prüfung der betroffenen Oberfläche; keine Tests, die nur den geänderten Wortlaut wiederholen | Gezielte Gegenprüfung nach Bedarf; fachliche Entscheidung durch Holger bei Inhaltsänderung |
| Normaler Fachablauf | Verhaltenstest für erlaubten/unerlaubten Fall, relevante Integration mit PostgreSQL, API-Konflikt-/Validierungsfall, UI-Demonstration bei UI-Änderung | Gegenseitiges KI-Review; unklare Befunde bleiben zur Entscheidung offen, zusätzliche menschliche Prüfung nicht beschlossen |
| Rechte, Anmeldung, Portalgrenzen, Rich Text, Uploads, externe Verbindungen oder KI-Daten/Bestätigung | Negative Zugriffstests über betroffene UI/API/Datei-/Such-/Export-/Jobwege, Missbrauchs-/Fehlerfälle, Geheimnis- und Datenflussprüfung | Gegenseitiges KI-Review mit gezielten Negativfällen; zusätzliche menschliche Prüfung bleibt offen |
| SLA, Zeitkorrektur, Vertrags-/Kostensummen | Unabhängig nachvollziehbare Sollwerte, Grenzzeiten, Rundung, Sommerzeit, Wiederholung und Regelversionswechsel; keine Sollwerte nur aus derselben Implementierung ableiten | Fachliche Beispiele mit Holger; prüfende KI kontrolliert Sollwerte und Grenzfälle getrennt von der Implementierung |
| Datenmigration, Schemaänderung, Update, Löschung oder Restore | Probe mit synthetischem repräsentativem Bestand, Konsistenz-/Mengenprüfung, Wiederholbarkeit, Abbruch und Wiederanlauf; gemessene Wiederherstellung bei betroffenem Verfahren | Gegenseitiges KI-Review bestätigt; Nachweise mit zuständigem Betreiber vorgeschlagen; zusätzliche menschliche Codeprüfung bleibt offen |
| Laufzeit, Bibliothek, Build oder Auslieferungsartefakt | Gepinnte Versionen, Eignung/Lizenz prüfen, betroffene Regressionen und reproduzierbares Artefakt; Herkunft und Abhängigkeiten dokumentieren | Technische Prüfung entsprechend den betroffenen Sicherheits-/Betriebswegen |

Bei mehreren Kategorien gilt die sachlich strengste betroffene Prüfung. Eine kleine Änderung an einer Rechtebedingung bleibt kritisch. Automatische Scanner sind Ergänzungen; ein leerer Scan ist keine fachliche Sicherheitsfreigabe. Die konkrete Werkzeugauswahl für Java/Spring Boot bleibt offen; hier wird kein Dienst aktiviert.

Bestehende Pflichtprüfungen für das Repository bleiben: `python3 scripts/validate_repository.py`, `python3 -m unittest discover -s tests -v` und `git diff --check`. Sie prüfen Dokumentstruktur und Zusammenarbeit, nicht die künftige Anwendung. Ausführliche Last- und Wiederherstellungsmessungen sind nur bei entsprechender Änderung oder Releaseanforderung nötig; ein Textfehler löst keinen vollständigen Produkttest aus.

## Parallelarbeit, Konflikte und Unterbrechungen

| Szenario | Erwartetes Vorgehen | Nachweis / Grenze |
|---|---|---|
| K1: Beide Sitzungen beanspruchen dieselbe freie Aufgabe | Koordination integriert einen Claim; die zweite liest main neu und arbeitet nicht an dieser Aufgabe | Reservierung und Eigentümer auf main prüfen. Auch ein konfliktfreier PR wird bei bereits belegter Aufgabe abgelehnt |
| K2: Zwei verschiedene Aufgaben ändern unabhängige Bereiche | Getrennte Worktrees; gemeinsame Verträge vorab abstimmen; Integrationsreihenfolge festlegen | Nach Zusammenführen sind beide Eigentümer und Ergebnisse noch vorhanden |
| K3: Eine alte TODO-Datei enthält einen inzwischen überholten Eigentümer | Kein vollständiges Ersetzen aus altem Checkout. Aktuellen Stand lesen und nur eigene Aufgabe ändern | Diff gegen aktuellen main prüfen. Git kann textuelle Konflikte erkennen, aber einen bewusst zurückkopierten veralteten Inhalt nicht zuverlässig verhindern |
| K4: Zwei Änderungen betreffen dieselbe API oder dasselbe Schema | Koordination trennt Vertragsentscheidung und abhängige Arbeiten; eine Änderung zuerst integrieren | Zweite Aufgabe aktualisiert Vertrag und Tests; kein unkoordiniertes Parallelmodell |
| K5: Sitzung bricht ab oder verliert Anmeldung | Letzten Commit/Branch, geänderte Dateien und nächsten Schritt erfassen; keine Zugangsdaten dokumentieren | Alter Besitzer bleibt bis ausdrücklicher Übergabe bestehen; Alter des Datums ist kein Freigabesignal |
| K6: Neue Sitzung übernimmt nach ausdrücklicher Übergabe | Vorherige und neue Sitzung sowie Anlass und Bezug im Aufgabenabschnitt dokumentieren; Arbeitsstand prüfen | Übernahme-PR vor weiteren Änderungen; uncommittierte fremde Dateien nicht überschreiben |
| K7: CI ist grün, fachlicher Fehler bleibt offen | Aufgabe nicht nach DONE verschieben; Befund beheben oder ausdrücklich zur Entscheidung stellen | CI und Merge beweisen keine Zustimmung Holgers |
| K8: Review war auf altem Commit erfolgreich | Seitdem geänderte Stellen und betroffene Nachweise erneut prüfen | Commitbezug im Review; kein ungeprüftes Übertragen eines alten „approved“ auf neuen Inhalt |

**Übergabebeispiel:** „Vorige Sitzung `claude-beispiel-01`; neue Sitzung `codex-beispiel-02`; Übergabe durch Koordination ausdrücklich beschlossen; letzter gesicherter Commit im PR; geändert: API-Vertrag und Fachtests; offen: Konfliktfall; nächster Schritt: Test nachvollziehen.“ Die Namen sind synthetisch und dokumentieren keine tatsächlich erteilte Übergabe.

## Technische Kontrollen und ihre Grenze

Die bestehende Einrichtung verlangt PRs und aktuelle erfolgreiche `repository-checks`. Erforderliche zustimmende Reviews stehen derzeit auf null, weil Werkzeuge und Holger denselben GitHub-Zugang nutzen können. CODEOWNERS und eine Sitzung in Markdown beweisen keine menschliche Identität. Die Regeln bleiben daher teilweise organisatorisch.

Vorgeschlagen wird später ein getrenntes Konto für die menschliche technische Prüfung und eine dazu passende geschützte Freigabe. Diese Änderung ist nicht Bestandteil dieses Entwurfs und wird nicht als eingerichtet behauptet. Auch dann bleibt Holgers fachliche Abnahme separat dokumentiert. Keine Sitzung darf Schutzregeln umgehen oder einen fremden Review unter dem gemeinsamen Konto vortäuschen.

## Mindestinhalt eines prüfbaren Ergebnisses

Eine Lieferung nennt Problem und neues Verhalten, TASK-/REQ-Bezug, Basis- und Ergebnisstand, relevante Dateien, tatsächlich ausgeführte Prüfungen, Reproduktion der Nutzerabläufe, offene Befunde und Einschränkungen. Bei UI-Änderungen gehören eine sichtbare Kontrolle und konkrete Bedienwege dazu. Bei kritischen Datenänderungen müssen Datenmenge, Sicherung und Wiederanlauf nachvollziehbar sein.

Reviewbefunde enthalten Ort, auslösenden Fall, beobachtete Folge und erforderliche Korrektur. Technische Eigenprüfung, Prüfung einer anderen KI, menschlicher technischer Review und Holgers Fachabnahme werden getrennt benannt. Eine nicht ausgeführte Prüfung wird ausdrücklich als offen geführt. Private Kundendaten, Zugangsdaten und vollständige private Gesprächsprotokolle gehören nicht in öffentliche PRs.

## Ausstehende Entscheidungen und Prüfstand

OPEN-017: Konkrete KI-Review-Zuordnung, Freigabeschwellen und Umgang mit offenen Befunden festlegen; zusätzliche menschliche Prüfung bleibt gemäß DEC-040 offen. OPEN-018: übrigen erweiterten Ablauf für künftige parallele Entwicklung bestätigen. Die Koordination weist prüfende Sitzungen zu; Holger entscheidet fachliche und organisatorische Fragen. Die bereits gültigen Regeln in AGENTS.md gelten weiter.

Die Szenarien K1 bis K8 sind am 16. Oktober 2026 gedanklich gegen den bestehenden Ablauf geprüft. K1 und K2 wurden zusätzlich in lokalen synthetischen Git-Repositories nachvollzogen: gleiche Aufgabenzeile mit verschiedenen Eigentümern ergab einen Textkonflikt; getrennte Aufgaben ließen sich unter Erhalt beider Eigentümer zusammenführen. TASK-011 hält diesen Nachweis fest. Das ist kein Test gleichzeitig laufender KI-Dienste, keine unabhängige technische Prüfung und keine neue GitHub-Sperrfunktion.
