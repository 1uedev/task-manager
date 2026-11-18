# Prüfbericht zu TASK-129 / PR #341

**Empfehlung: nachbessern.** Die Nachbesserung behebt große Teile der zwölf Befunde und vier Hinweise aus TASK-125. Die verbleibenden Fehler und ein fehlender Pflichtnachweis betreffen bestehende HTTP-Zugänge, Schlüssel in JSON-Ausgaben, wieder gültig werdende Vorschläge, den Abbruch vor dem Versand sowie Parser und Schnittstellenvertrag.

Prüfstand vom 17. November 2026. Repository 1uedev/task-manager, Branch claude/project-thread-av1na6, Head ed4b2995bf898811c4cb7b68b94843b7ce528ec7, Basis 0551bdc74343bffcf2da1bb2dbfa15f98d2ab5df, CI-Merge-Stand 0be64676d18360de858b836452aadefe6f6478fa. Maßstab sind AGENTS.md, DEVELOPMENT_REVIEW.md, TASK-129 mit Regeln 1 bis 16, TASK-125, DEC-156 und DEC-158, die aktuelle README sowie der Vorgängerbericht ([REVIEW_TASK-125_PR-339.md](REVIEW_TASK-125_PR-339.md)).

## Befunde vor der Abnahme

### F1 – Hoch – Die HTTP-Hostfreigabe greift bei bestehenden Zugängen nicht

- Datei und Zeile: `backend/src/main/resources/db/migration/V0068__ai_review_fixes.sql` (Zeile 6), `backend/src/main/java/taskmanager/ai/internal/ConfigService.java` (Zeilen 348 und 370), `backend/src/main/java/taskmanager/ai/internal/AiHttp.java` (Zeile 99).
- Szenario: Unter TASK-125 wurde ein lokaler Datenweg mit einer HTTP-Adresse gespeichert. Nach V0068 ist `TASK_MANAGER_AI_PLAIN_HTTP_HOSTS` leer oder enthält diesen Host nicht. Die Migration bindet den vorhandenen Schlüssel an den Hash der bisherigen Adresse und CA. Erst das erneute Speichern durch die Verwaltung prüft die neue Freigabe. Verfügbarkeitsprüfung, Entschlüsselung und Versand prüfen sie nicht.
- Folge und Gegenprobe: Der alte Zugang bleibt verfügbar und sein Schlüssel lesbar, obwohl derselbe Zugang beim heutigen Anlegen verweigert würde. Eine Java-Gegenprobe mit einer der Migration entsprechenden Zeile bestätigte released=false, available=true und die erfolgreiche Schlüsselöffnung. Ein altes, falsch als lokal gespeichertes Standardziel behält außerdem seine bisherige Einstufung. Es wurde kein fremder Host aufgerufen.
- Vorschlag: Bestehende Zeilen beim Upgrade oder spätestens vor jedem Aufruf nach den neuen Adress- und Datenwegregeln prüfen. Nicht mehr freigegebene Zugänge müssen vor dem Versand gesperrt werden. Eine Upgrade-Gegenprobe mit einer vor V0068 angelegten HTTP-Zeile und leerer Betreiberfreigabe ergänzen, einschließlich der Modelllistenprüfung.

### F2 – Mittel – JSON-Escapes umgehen die Erkennung eines zurückgegebenen Schlüssels

- Datei und Zeile: `RunService.java` (Zeile 388), `AiFormats.java` (Zeile 83).
- Szenario: Ein nachgebauter Anbieter liefert einen formal gültigen Vorschlag, dessen Text den Schlüssel mit einem JSON-Escape enthält, beispielsweise mit `sk-…` am Anfang. Im ungeparsten Antworttext kommt der Schlüssel nicht wörtlich vor. Beim Parsen wird der Escape aufgelöst.
- Folge und Gegenprobe: Die Prüfung vor `AiFormats.parse` passiert. Der entschlüsselte Schlüssel steht anschließend wörtlich im gespeicherten Vorschlag und kann über den eigenen Lauf gelesen werden. Die unabhängige Java-Gegenprobe endete mit succeeded und bestätigte den vollständigen synthetischen Schlüssel im gespeicherten Inhalt. Der vorhandene Test prüft nur eine unmaskierte Wiederholung.
- Vorschlag: Auch die dekodierten und bereinigten Textwerte der endgültigen Ausgabe vor der Speicherung auf den verwendeten Schlüssel prüfen. Gegenproben mit JSON-Escapes und einer erst durch die Bereinigung zusammengesetzten Wiederholung ergänzen. Fehler und Nachweis bleiben ohne Antwortinhalt.

### F3 – Mittel – Wiederherstellen des eingebauten Skills macht alte Vorschläge erneut gültig

- Datei und Zeile: `RunService.java` (Zeilen 300 und 320), `SkillService.java` (Zeilen 144 und 158).
- Szenario: Eine Ausführung mit builtin-1 ist fertig. Die Verwaltung aktiviert einen eigenen Skill. Der alte Vorschlag wird korrekt als `ai.configuration_changed` verborgen. Anschließend stellt sie den eingebauten Skill wieder her, ohne Zugang, Modell oder Zuordnung zu ändern.
- Folge und Gegenprobe: Der berechnete Stempel entspricht wieder dem ursprünglichen Stempel, weil er für den Skill nur das wiederverwendete Label enthält. Der alte Vorschlag wird erneut ausgeliefert. Die Java-Gegenprobe bestätigte erst die Ungültigkeit und dann die erneute Sichtbarkeit. Der neue Integrationstest stellt zwar den eingebauten Skill wieder her, liest danach aber einen neu gestarteten Lauf.
- Vorschlag: Die Auswahl des geltenden Skills mit einer monotonen Revision des Slots versehen und diese im Stempel verwenden. Beim Wiederherstellen darf die frühere Auswahlrevision nicht zurückkehren. Dieselbe alte Lauf-ID vor Aktivierung, nach Aktivierung und nach Wiederherstellung prüfen.

### F4 – Mittel – Ein bereits verarbeiteter Abbruch verhindert den anschließenden Versand nicht

- Datei und Zeile: `RunService.java` (Zeilen 176, 376 und 384), `AiHttp.java` (Zeile 143).
- Szenario: Der Worker hat die Prüfung unmittelbar vor dem Aufruf beendet und wird während der Schlüsselphase unterbrochen. Die Person bricht ab oder der lokale Konfigurationslistener stoppt die Ausführung. Der Future unterbricht den Worker. Danach setzt dieser seinen Code fort.
- Folge und Gegenprobe: Vor `adapters.complete` wird weder der Abbruchgrund noch das Interruptmerkmal geprüft. `AiHttp` ruft auch bei bereits gesetztem Interrupt zuerst `sendAsync` auf und erkennt die Unterbrechung erst beim anschließenden Warten. Kontext und Schlüssel können deshalb noch versandt werden. Eine gehaltene Schlüsselphase reproduzierte den Adapteraufruf nach dem Abbruch. Eine getrennte Gegenprobe bestätigte `sendAsync` bei bereits unterbrochenem Aufrufer mit anschließendem `ai.cancelled`. Die Gegenproben verwenden Testdoppel, keine ausgehende Verbindung.
- Vorschlag: Einen bereits bekannten Abbruch vor dem Adapteraufruf und vor dem Aufbau beziehungsweise Start des HTTP-Aufrufs beachten. Die Konfigurations- und Benutzerabbrüche sollen denselben kontrollierten Übergang verwenden. Die Gegenprobe muss den Abbruch vor dem Versand halten und anschließend nachweisen, dass kein Anbieteraufruf begonnen hat.

### F5 – Niedrig – Gültiges JSON mit Markdown-Codebegrenzern wird im erlaubten Codeblock verweigert

- Datei und Zeile: `AiFormats.java` (Zeile 147), `contracts/ai.openapi.yaml` (Zeile 31).
- Szenario: Eine Wiki-Überarbeitung liefert ihr JSON in genau einem erlaubten äußeren Codeblock. Der JSON-String `text` enthält Markdown mit drei Backticks, beispielsweise einen Java-Codeabschnitt.
- Folge und Gegenprobe: Die Prüfung von `fence.group(1)` sucht pauschal nach drei Backticks im Inhalt und verweigert das gültige JSON als `ai.invalid_output`. Der identische JSON-Inhalt ohne äußeren Codeblock wird akzeptiert. Beide Ergebnisse wurden in der Java-Gegenprobe bestätigt. Das ist besonders für den Anwendungsfall Wiki-Überarbeitung ein regulärer Inhalt.
- Vorschlag: Nach Entfernen genau eines äußeren Codeblocks dessen Inhalt ausschließlich mit dem strikten JSON-Parser prüfen. Weitere echte äußere Blöcke müssen weiterhin scheitern, Backticks innerhalb eines JSON-Strings müssen erhalten bleiben. Beide Varianten gemeinsam testen.

### F6 – Niedrig – Der neue Status verworfener Entwürfe fehlt in Vertrag, Übersetzungen und Testersatz

- Datei und Zeile: `AiStore.java` (Zeile 175), `AiController.java` (Zeile 131), `contracts/ai.openapi.yaml` (Zeile 852), `frontend/src/pages/AiPage.tsx` (Zeile 519), `frontend/tests/browser/backend.mjs` (Zeile 3835).
- Szenario: Die Verwaltung verwirft einen gespeicherten Skillentwurf und öffnet anschließend die Versionsliste. Die Zeile bleibt seit V0068 als `discarded` vorhanden und wird vom Controller mit diesem Status ausgegeben.
- Folge und Gegenprobe: Der Vertrag und die generierten Typen erlauben nur draft, active und archived. Die Kataloge enthalten kein `ai.skillState.discarded`. In der Liste erscheint daher der interne Übersetzungsschlüssel. Der Testersatz entfernt seinen Entwurf und führt ihn nicht als verworfene Version weiter, wodurch der Browserfall die Abweichung verdeckt. Die Java-Gegenprobe bestätigte die Ausgabe `discarded` aus dem echten Controller-Mapping.
- Vorschlag: Die Darstellung der verworfenen Zeile durchgängig festlegen. Bei Ausgabe des Status Vertrag, Clienttypen, Texte und Testersatz entsprechend ergänzen. Den Ablauf Speichern, Verwerfen und Versionsliste auf Deutsch und Englisch mit einer Serverantwort dieses Zustands prüfen.

### F7 – Niedrig – Der getrennte Negativnachweis für ein fachliches Leserecht fehlt

- Datei und Zeile: `AiApiTest.java` (Zeilen 139 und 1147), `RunService.java` (Zeile 508).
- Szenario und Folge: Regel 8 verlangt getrennte Gegenproben für Quellenänderung und Entzug eines Fachrechts. Der Testguard hat ausschließlich den Schalter `valid`. Der neue Test schaltet ihn einmal aus und wieder ein. Der weitere vorhandene Rechtefall entzieht `ai.use`, also das KI-Startrecht. Ein Fachrecht wie das Lesen der ursprünglichen Ticketquelle wird bei weiterhin gültiger Quelle und weiterhin bestehendem `ai.use` nicht getrennt geprüft. Die Verweigerung ohne Guard und bei dessen negativem Ergebnis funktioniert, der ausdrücklich verlangte Fachrechtefall ist damit noch nicht nachgewiesen.
- Vorschlag: Den Testguard um einen getrennten, veränderlichen Fachrechtewert ergänzen. Einen bereits erzeugten Vorschlag bei unverändertem Quellenstand und erhaltenem `ai.use` nach Entzug dieses Fachrechts ohne Inhalt lesen. Quellen- und Fachrechtefall einzeln prüfen.

## Hinweise

### H1 – Gleichwertige IPv6-Adressen sind noch keine gemeinsame kanonische Adresse

- Datei und Zeile: `AiTargets.java` (Zeilen 46 und 86).
- Szenario und Vorschlag: IPv6-Literale werden nur kleingeschrieben. Die Gegenprobe liefert für `https://[::1]` und `https://[0:0:0:0:0:0:0:1]` verschiedene kanonische URLs. Eine bloße Schreibweisenänderung kann deshalb einen Schlüssel verwerfen oder an einer engeren HTTP-Hostfreigabe scheitern. Das Verhalten ist hinsichtlich der Schlüsselweitergabe konservativ. IPv6-Literale ohne DNS-Auflösung einheitlich darstellen und die Freigabeeinträge auf dieselbe Form bringen.

### H2 – Der Ablehnungsnachweis sollte auch unter einer äußeren Transaktion geprüft werden

- Datei und Zeile: `RunService.java` (Zeilen 216 und 256), `AiApiTest.java` (Zeile 123).
- Szenario und Vorschlag: Der Code verwendet für Ablehnungen ausdrücklich REQUIRES_NEW. Der Testcontroller öffnet selbst keine äußere Transaktion. Ergänzend einen Fachmodulaufruf aus einer bereits laufenden Transaktion prüfen, deren äußere Änderungen nach der Ablehnung zurückgerollt werden. Nur der inhaltsfreie Ablehnungsnachweis soll bestehen bleiben. Ein Fehler des bestehenden Normalfalls wurde hier nicht festgestellt.

## Abgleich mit allen Befunden und Hinweisen aus TASK-125

Die Kennungen links beziehen sich auf den Vorgängerbericht. Die neuen Befunde oben haben eine eigene Nummerierung.

| Vorgänger | Ergebnis der Nachprüfung |
| --- | --- |
| F1, Schlüsselziel | Der reguläre Updateweg verwirft den Schlüssel bei Änderung von kanonischer Adresse oder CA ohne Neueingabe. Vor dem Öffnen wird der Zielhash verglichen. Der neue Integrationstest verwendet zwei getrennte Anbieter, prüft den verworfenen Schlüssel, die Neueingabe und eine abweichende CA. Eine abweichende Adresse direkt in der Datenbank wird verweigert. Die Übernahme alter Zeilen bleibt von neuem F1 betroffen. |
| F2, Schlüssel in Log und Ausgabe | Sichtbares ASCII wird validiert, Headerfehler verlieren Ausnahmetext und Ursache, die unmaskierte Wiederholung wird verworfen. AiInternalsTest und AiApiTest prüfen diese Negativfälle. Die Ausgabeprüfung ist wegen neuem F2 noch unvollständig. Reguläre Providerantworten, Audits und absichtlich behandelte Fehler liefern den Schlüssel nicht aus. |
| F3, Adressen und lokaler Datenweg | Neue und geänderte Einträge prüfen Hostfreigabe und local, Standardhosts werden als extern behandelt oder bei falscher Einstufung abgewiesen. IDN, Punkt am Ende und Standardports werden geprüft. Bestehende Zeilen passieren den Versand ohne die neue Prüfung, siehe neues F1. H1 betrifft eine konservative Gleichheitsabweichung bei IPv6. |
| F4, Versionswechsel | Zuordnung, Anbieter und Modell erhalten im Stempel ID beziehungsweise Revision. Entwurfsprobeläufe enthalten zusätzlich die Entwurfsrevision. Neue Tests prüfen eine Modellrevision bei gehaltener Antwort, Aktivierung eines Skills und Aus-/Einschalten der Zuordnung. Neue F3 und F4 bleiben offen. |
| F5, Deaktivierung | Offene Läufe werden unter Zeilensperren beendet, anschließend Vorschläge gelöscht. Eine spätere Speicherung findet den Lauf beendet. Die Gegenprobe hält die Anbieterantwort, deaktiviert, reaktiviert und prüft ausbleibende Vorschläge. Der vorangegangene Fehler der erneuten Speicherung ist behoben. |
| F6, gesamter Antwortkörper | Das eigene LimitedBody beendet den Response-Future erst mit dem begrenzten Body. Die gemeinsame Frist und cancel(true) erfassen dadurch den Body. Der Integrationstest mit sofortigem Kopf und acht Sekunden offenem Körper endet mit `ai.timeout` innerhalb seines Grenzwerts und führt anschließend erfolgreich einen neuen Lauf aus. |
| F7, Plätze | Task veröffentlicht den Future vor der Aufnahme in pending. Der atomare Übergang von WAITING nach DROPPED gibt genau einmal frei. Ein laufender Task gibt erst in seinem tatsächlichen finally frei. Der Test bricht fünf wartende Läufe jeweils zweimal ab und belegt danach wieder alle Testplätze. Der frühere dauerhafte Platzverlust ist behoben. Neues F4 betrifft den Beginn des HTTP-Aufrufs. |
| F8, zuständiger Prüfer | Ohne passenden Guard wird `ai.source_unchecked` mit leerem Inhalt geliefert. Der Test prüft dies für einen Fall ohne Guard. Der andere Fall prüft ein negatives Quellenurteil über den veränderlichen Guard. Die Grundlage liefert ohne zuständigen Prüfer keinen Inhalt. Der zusätzliche Fachrechte-Negativnachweis aus Regel 8 fehlt, siehe neues F7. |
| F9, striktes Format | Doppelte Schlüssel und nachfolgende Tokens scheitern im strikten Mapper. Exakte Felder, Pflichtwerte und Typen werden pro Fall geprüft. Fremdtext, zwei Objekte, zusätzliche Felder und unbekannte Quellen haben Gegenproben. Die ausdrücklich erlaubte äußere Codeblockform verweigert noch einen gültigen Inhalt, siehe neues F5. |
| F10, erste Zuordnung | Vor dem Lesen der Version wird der Advisory Lock pro Unternehmen und Fall genommen. Zwei echte Transaktionen warten im Test auf denselben gehaltenen Lock. Nach Freigabe erhält genau eine 200 und die andere 412. |
| F11, Entwurfsrevision | Änderungen erhöhen die gespeicherte Revision. Verworfene Zeilen behalten ihre Nummer, neue Entwürfe erhalten eine neue Nummer. Der Test setzt gleiche Zeitstempel, prüft verschiedene Tags, 412 beim veralteten Aktivieren und draft-2.1 nach Verwerfen und Neuanlage. Die Versionsdarstellung hat die Abweichung aus neuem F6. |
| F12, Ablehnungen | Berechtigte fachliche Ablehnungen werden in einer eigenen Transaktion mit Zustand refused gespeichert. Tageszähler und Nutzungsaggregation lassen sie aus. Der Test prüft fehlende Zuordnung, Kontextgröße, Auslastung und Tagesgrenze sowie das Ausbleiben eines Eintrags ohne Recht. Die nullable Felder sind bei refused in Migration und API berücksichtigt. H2 ergänzt einen noch fehlenden Transaktionsnachweis. |
| H1, Aufräumfrist | README nennt jetzt 14 Minuten bis zur Fälligkeit, den nächsten 15-Minuten-Lauf, bis etwa 29 Minuten und die Abhängigkeit von laufenden Workern. DEC-158 legt Plätze je Prozess fest. |
| H2, Protokollseite | Der interne Abruf lässt 201 Zeilen zu. AiRecordsPageTest legt genau 201 abgelehnte Einträge an und prüft die Seite mit 200 Zeilen, den Folgelink und die letzte Zeile. Damit werden auch die nullable Felder im Leseweg geprüft. |
| H3, Testersatz und Oberfläche | `sample` fällt aus den Laufantworten weg. `content: null` mit `invalid` sowie ein laufender Probelauf und dessen Abbruch werden im neuen Browsertest geprüft. Die Oberfläche zeigt den Ungültigkeitsgrund. Neues F6 bleibt beim Verwerfen eines Entwurfs offen. |
| H4, TLS und Portal | Der lokale HTTPS-Test unterscheidet richtige CA, fehlende CA und falschen Hostnamen. Ein echtes Portalkonto wird gegen Status, eigene Laufroute und Verwaltung mit 403 geprüft. Diese Fälle sind in der CI grün. |

## Weitere Prüfergebnisse

| Bereich | Bewertung |
| --- | --- |
| Bindung und Kanonisierung | Neue Schlüssel sind weiterhin durch AES-GCM an Zweck und Zugangs-ID gebunden. key_target prüft zusätzlich Adresse und CA. Die regulären Updatefälle, IDN-Umwandlung, Groß-/Kleinschreibung, DNS-Endpunkt, Standardport und Weglassen von Schrägstrichen sind nachvollziehbar. Für Migration, Rückgabe im JSON und IPv6 gelten die Befunde oben. Modellkennungen gehen unverändert als JSON-Werte an den festgelegten Adapter. |
| Migration und ai_run_planned | Nicht geplante Ablehnungen dürfen Anbieter, Modell, Skill und Datenweg leer lassen. Andere Zustände müssen diese Angaben weiterhin haben. Bestehende Läufe ohne Stempel werden beim Lesen als geändert behandelt. Der Controller übernimmt leere Felder und der Vertrag macht sie nullable. Die neue Protokollseitenprobe läuft gerade mit solchen Zeilen. |
| Vertrag und Antwortformen | AiContractTest bestätigt weiterhin 26 Methoden-/Pfadpaare. Clientgenerierung und Typprüfung sind grün. Das prüft keine vollständige Konformität jeder Laufzeitantwort. Die konkrete Enum-Abweichung bei verworfenen Skills steht in F6. |
| Keine Ersatzmodelle | Der Plan enthält genau Zugang und Modell der Zuordnung. Die Adapter besitzen keinen Fallbackpfad. Die Fehler-Gegenproben prüfen weiterhin, dass kein Wechsel zu einem zweiten Anbieter erfolgt. |
| Grenzen über mehrere Prozesse | Plätze und sofortige Konfigurationsabbrüche gelten je Prozess. Das ist für die von DEC-158 beschriebene Betriebsgrenze vertretbar. Andere Prozesse erfahren Änderungen beim Stempelvergleich vor dem Versand, beim Speichern und beim Lesen. Tagesgrenzen bleiben über den gemeinsamen Advisory Lock installationsweit. Eine Installation mit mehreren Webprozessen hat entsprechend mehr parallele Plätze. Die neuen F3 und F4 gelten zusätzlich. |

## Tatsächlich ausgeführte Prüfungen

Die separate lokale Prüffassung verwendet anhand ihrer Git-Blob-Hashes verifizierte Head-Dateien. Der rückwärts angewandte PR-Diff und der erneute Vergleich ergeben genau 32 geänderte Dateien mit 1.741 hinzugefügten und 314 entfernten Zeilen. Die unabhängigen Java-Proben liegen außerhalb des Repository-Quellbaums und verwenden ausschließlich synthetische Werte und Testdoppel. Die Funktionsfähigkeit der datenbankgebundenen Gegenproben wurde zusätzlich anhand der CI-Protokolle für den genannten Merge-Stand geprüft.

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `git diff --check` | Lokal grün. Die Repository-CI ist ebenfalls grün. |
| `python3 -m unittest discover -s tests -v` | Lokal 50 Tests gestartet. Neun Fehler und eine fehlgeschlagene Assertion bei den lokalen Socket-/HTTP-Prüfungen, ein Fall übersprungen. In CI 50 Tests erfolgreich. |
| Backendauswahl | Lokal AiInternalsTest 4/4 und ModuleStructureTest 2/2 erfolgreich. AiApiTest, AiRecordsPageTest, AiContractTest, DatabaseFoundationTest und ApplicationLifecycleTest scheiterten beim Aufbau ihrer Docker-/Datenbankumgebung. Der lokale Docker-Daemon ist nicht erreichbar. |
| Vollständiges Backend in CI | 719 Tests mit PostgreSQL 18.6 grün. Darunter AiApiTest 20, AiInternalsTest 4, AiRecordsPageTest 1, AiContractTest 1, ModuleStructureTest 2, DatabaseFoundationTest 4 und ApplicationLifecycleTest 3. |
| `backend/scripts/lifecycle-check.sh` | Lokal wegen der fehlenden isolierten Docker-/PostgreSQL-Umgebung nicht ausgeführt. Im Backend-CI für den geprüften Stand erfolgreich einschließlich Migrationen. |
| `npm run verify` | Nach einem erneuten Versuch wegen einer vorübergehenden Windows-Dateisperre erfolgreich, 102 Unit-Tests, Typprüfung und Build. Die generierten Clientdateien entsprechen dem Index. |
| `ai.test.mjs` | Lokal sieben Versuche mit ERR_NETWORK_ACCESS_DENIED beim Zugriff auf localhost. In CI sieben KI-Browsertests und die gesamte Suite mit 317 Tests grün. |
| Unabhängige Gegenproben | Die sechs Verhaltensbefunde wurden anhand des echten Mapping-/Service-/Parsercodes und kontrollierter Testdoppel reproduziert. Der siebte Befund ergibt sich aus dem Abgleich des Testguards und seiner Aufrufe mit Regel 8. Der Versand nach Abbruch wurde durch einen gehaltenen Schritt unmittelbar vor dem Adapteraufruf sowie einen separat instrumentierten HTTP-Client geprüft. Der Migrationsfall stellt die von V0068 erzeugte Kombination aus alter Adresse, Chiffrat und Zielhash nach. Eine zusätzliche PostgreSQL-Upgradeausführung war lokal nicht möglich. |

Es gab keine Aufrufe gegen echte KI-Anbieter. Die Produktdateien der Prüffassung entsprechen dem geprüften Stand.
