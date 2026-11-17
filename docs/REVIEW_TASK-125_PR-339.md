# Prüfbericht zu TASK-125 / PR #339

**Empfehlung: nachbessern.** Der PR liefert die drei Adapter und den Verwaltungsablauf. Vor der Abnahme müssen insbesondere der Umgang mit Schlüsseln bei Zieländerungen, die Freigabe bei Konfigurationswechsel, die Frist für den Antwortkörper und der Abbruch wartender Ausführungen korrigiert werden.

**Prüfstand** · 17. November 2026. Repository 1uedev/task-manager, Branch claude/project-thread-av1na6, Head `8efbce1b1b897b1965d30e850537a68c3b3a010a`, Basis `f9674fe6a650fa94292bdad90211f9b563962984`, CI-Merge-Stand `df578c40bf8ab63b3accf330f14e477a2324e12c`. Die Prüfung legt die 13 Regeln, die Abnahmekriterien und die Übergabe von TASK-125, DEC-156 sowie A06, A07 und A12 bis A14 zugrunde. PR #339 · [TASK-125](../TODO.md#task-125-ki-grundlage-mit-anbietern-modellen-skills-grenzen-und-nachweis-a16-teil-1) · [DECISIONS.md](../DECISIONS.md) · [AI_USE_CASES.md](AI_USE_CASES.md) · [Backend-README](../backend/README.md#ki-grundlage-task-125)

Codex konnte den Bericht nicht selbst veröffentlichen. Holger hat ihn am 17. November 2026 im Projektgespräch übergeben, und er wurde inhaltlich unverändert übernommen.

## Befunde vor der Abnahme

### F1 · Hoch · Ein gespeicherter Schlüssel folgt der Zugangs-ID an eine neue Zieladresse

- **Datei und Zeile.** [ConfigService.java:367](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L367) · [ConfigService.java:403](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L403) · [ConfigService.java:418](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L418) · [AiAdapters.java:49](../backend/src/main/java/taskmanager/ai/internal/AiAdapters.java#L49)
- **Szenario und Folge.** Ein bestehender OpenAI-kompatibler oder Ollama-Zugang erhält eine neue Adresse. Beim Update ohne neues Schlüsselfeld wird das bisherige Chiffrat übernommen. „Verbindung prüfen“ und Generierungen verwenden den entschlüsselten Schlüssel am neuen Empfänger. Damit kann ein Bedienfehler oder ein Konto mit ai.manage einen bisher geschützten fremden Schlüssel weitergeben. Eine Java-Gegenprobe bestätigte mit zwei synthetischen Zielnamen, dass der alte Schlüssel nach dem Wechsel unverändert verwendet werden kann. Der AES-GCM-Test mit einer anderen Zugangs-ID scheiterte erwartungsgemäß.
- **Vorschlag.** Die Wiederverwendung des Schlüssels an das kanonische Ziel und die Vertrauenskonfiguration des Zugangs binden. Bei Änderung von Ziel oder Vertrauensankern den bisherigen Schlüssel löschen oder eine ausdrückliche Neueingabe verlangen. Mit zwei nachgebauten Anbietern prüfen, dass der neue Empfänger das alte Geheimnis nie erhält.

### F2 · Mittel · Schlüsselinhalte können im Fehlerlog und in Vorschlägen landen

- **Datei und Zeile.** [ConfigService.java:412](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L412) · [AiHttp.java:126](../backend/src/main/java/taskmanager/ai/internal/AiHttp.java#L126) · [ConfigService.java:141](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L141) · [ProblemResponses.java:29](../backend/src/main/java/taskmanager/platform/web/ProblemResponses.java#L29) · [RunService.java:252](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L252)
- **Szenario und Folge.** Ein eingegebener Unicode-Schlüssel ohne Steuerzeichen kann gespeichert werden, obwohl der Java-HTTP-Client ihn als Headerwert verweigert. Java 25 gab in der Gegenprobe den vollständigen synthetischen Schlüssel in der Ausnahme aus. Die Modell- und Verbindungsprüfung fangen diese Ausnahme nicht. Der allgemeine Fehlerhandler protokolliert sie einschließlich Text, auch wenn die HTTP-Antwort eine bereinigte Fehlermeldung enthält. Außerdem kann ein kompatibler Server den empfangenen Schlüssel im gültig formatierten Freitext einer erfolgreichen Antwort zurückgeben. Die Gegenprobe bestätigte, dass die Ausgabeprüfung so einen Schlüssel als Teil des Vorschlags akzeptiert.
- **Vorschlag.** Die Zulässigkeit von Schlüsseln für HTTP-Header vor der Speicherung prüfen und Fehler beim Aufbau authentifizierter Anfragen ohne Schlüsseltext weitergeben. Erfolgreiche Antworten mit einer wörtlichen Wiederholung des verwendeten Schlüssels vor der Speicherung verwerfen. Beide Fälle mit einem synthetischen Geheimnis und einer Prüfung von API-Antwort, Log und Vorschlag abdecken.

### F3 · Hoch · Der Datenwegname allein erlaubt unverschlüsseltes HTTP zu beliebigen Hosts

- **Datei und Zeile.** [ConfigService.java:390](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L390) · [ConfigService.java:458](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L458) · [AiProperties.java:20](../backend/src/main/java/taskmanager/ai/internal/AiProperties.java#L20)
- **Szenario und Folge.** Bei einer eigenen Adresse entscheidet der frei wählbare Wert local allein über die Zulässigkeit von HTTP. Es gibt keine Begrenzung auf vom Betreiber freigegebene lokale Ziele. Auch eine Schreibvariante des OpenAI-Standardhosts mit anderer Groß-/Kleinschreibung wird als eigener Zugang behandelt und kann als lokal angezeigt werden. Die Java-Gegenprobe bestätigte beide Fälle in der Validierung, ohne einen der Hosts aufzurufen. Eine falsche Kennzeichnung kann Kontext und Schlüssel unverschlüsselt aus dem vorgesehenen Netz senden.
- **Vorschlag.** Endpoint-Identitäten vor Vergleich und Speicherung kanonisieren. Unverschlüsseltes HTTP auf ausdrücklich vom Betreiber freigegebene lokale Zielnetze oder Endpunkte begrenzen, auch bei Proxy- und Namensauflösung. Die Adressfreigabe und die Beschriftung des Datenwegs gemeinsam testen. Den allgemeinen HTTP-Override auf Tests beschränken.

### F4 · Hoch · Konfigurationsrevisionen stoppen weder alte Pläne noch alte Ergebnisse

- **Datei und Zeile.** [RunService.java:102](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L102) · [RunService.java:245](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L245) · [RunService.java:299](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L299) · [RunService.java:337](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L337)
- **Szenario und Folge.** Eine wartende Ausführung enthält Anbieter, Modell, Skill und Prompt vom Start. Sie sendet später an den alten Empfänger, auch wenn der Zugang inzwischen deaktiviert oder ersetzt wurde. Beim Liefern und Lesen wird nur geprüft, ob das heute zugeordnete Modell verfügbar ist und dieselbe Modell-UUID hat. Ein geänderter Endpunkt, Schlüssel, Modellname beim Anbieter oder Skill unter derselben Zuordnung bleibt damit unbeachtet. Ebenso kann eine zwischenzeitlich aus- und wieder eingeschaltete Zuordnung einen alten Vorschlag wieder sichtbar machen. In der Java-Gegenprobe wurde ein Vorschlag nach Modell- und Skillrevision weiter ausgeliefert. Der vorhandene Integrationstest deckt nur einen Zugang ab, der bei Eingang der Antwort weiterhin deaktiviert ist.
- **Vorschlag.** Die Revisionen von Zuordnung, Anbieter, Modell und angewandtem Skill beim Start festhalten und vor dem Versand, bei der Ergebnisspeicherung und beim Lesen prüfen. Abweichende wartende Ausführungen mit ai.configuration_changed beenden. Laufende Anfragen bei einem Wechsel soweit möglich abbrechen. Gegenproben für gleiche Modell-ID mit neuer Revision, Skillwechsel sowie Aus- und Wiedereinschalten ergänzen.

### F5 · Mittel · Nach Kontodeaktivierung kann eine laufende Ausführung einen Vorschlag anlegen

- **Datei und Zeile.** [RunService.java:275](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L275) · [RunService.java:404](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L404) · [AccountService.java:217](../backend/src/main/java/taskmanager/identity/account/AccountService.java#L217)
- **Szenario und Folge.** Die Kontodeaktivierung löscht bereits vorhandene Vorschläge. Wenn die Anbieterantwort erst danach eintrifft, prüft die Speicherung den Kontostatus nicht und legt erneut einen Vorschlag für das deaktivierte Konto an. Eine blockierte synthetische Anbieterantwort reproduzierte die erneute Speicherung nach dem Deaktivierungsereignis. Bei einer späteren Reaktivierung könnte der Vorschlag innerhalb seiner Frist wieder sichtbar sein, sofern die übrigen Prüfungen passen. Der vorhandene Test deaktiviert erst nach erfolgreicher Speicherung.
- **Vorschlag.** Deaktivierung und Vorschlagsanlage für dasselbe Konto transaktional ordnen. Offene Ausführungen bei Deaktivierung beenden und eine spätere Speicherung verweigern. Gegenprobe mit einer gehaltenen Antwort und einer möglichen anschließenden Reaktivierung ergänzen.

### F6 · Mittel · Die Frist endet praktisch mit den Antwortheadern

- **Datei und Zeile.** [AiHttp.java:133](../backend/src/main/java/taskmanager/ai/internal/AiHttp.java#L133) · [AiHttp.java:139](../backend/src/main/java/taskmanager/ai/internal/AiHttp.java#L139)
- **Szenario und Folge.** Der HTTP-Client liefert bei ofInputStream() einen Stream für den Antwortkörper. Das zeitbegrenzte get wartet auf die Antwort mit diesem Stream. Anschließend liest readNBytes synchron ohne eigene Frist. Gibt ein Server die Header schnell aus und hält dann den Antwortkörper offen, kann er einen Ausführungsplatz oder den anfragenden Prüfvorgang über die Frist hinaus blockieren. Der Java-Test mit sofortigen Headern und verzögertem Body erhielt trotz eines Limits von 30 ms nach rund 240 ms eine erfolgreiche Antwort. Ein späteres ai.cleanup aktualisiert nur die Datenbank und beendet keinen blockierten Worker.
- **Vorschlag.** Eine gemeinsame Frist für Verbindung, Header und gesamten begrenzten Antwortkörper durchsetzen. Bei Ablauf die zugrunde liegende Anfrage und Body-Subscription abbrechen. Eine Gegenprobe mit sofortigen Headern und offenen Body-Daten muss ai.timeout liefern und den Platz freigeben.

### F7 · Mittel · Der Abbruch einer wartenden Ausführung verliert dauerhaft einen Platz

- **Datei und Zeile.** [RunService.java:229](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L229) · [RunService.java:358](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L358)
- **Szenario und Folge.** Ein wartender Future wird vor seinem Start erfolgreich abgebrochen. Der Executor führt dessen Runnable und damit finally nicht aus. Der reservierte Semaphore-Platz und der Eintrag in pending werden deshalb auf diesem Weg nicht freigegeben. In der Gegenprobe mit einem laufenden und einem wartenden Platz blieb nach Ende aller Arbeiten nur ein freier Platz übrig. Schnelles reguläres Ende kann zusätzlich mit pending.put konkurrieren. Der vorhandene Test liest den Status cancelled, füllt die Warteschlange danach aber nicht erneut.
- **Vorschlag.** Future und Platz gemeinsam verwalten. Bei Abbruch vor dem Start den Auftrag aus der Executor-Warteschlange entfernen und den Platz genau einmal freigeben. Bei laufenden Arbeiten den Platz erst nach tatsächlichem Ende freigeben. Nach wiederholtem Abbruch wartender Ausführungen erneut alle Plätze belegen und prüfen.

### F8 · Mittel · Ohne fachlichen Prüfer kann ein Vorschlag mit veralteten Quellen ausgegeben werden

- **Datei und Zeile.** [AiSuggestionGuard.java:7](../backend/src/main/java/taskmanager/ai/AiSuggestionGuard.java#L7) · [RunService.java:341](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L341) · [AiApiTest.java:746](../backend/src/test/java/taskmanager/ai/AiApiTest.java#L746)
- **Szenario und Folge.** Ein Fachmodul hat den Lauf gestartet, aber für den Anwendungsfall ist kein AiSuggestionGuard registriert. Der Besitzer mit ai.use erhält den Inhalt. Die Gegenprobe reproduzierte dies. Die Grundlage kann dann weder eine Quellenänderung noch den Entzug eines fachlichen Leserechts erkennen. Dass die vier Fachfunktionen erst später folgen, erklärt die fehlende Produktintegration. Der öffentliche Modul-Port kann den gefährlichen Standard schon jetzt vermeiden.
- **Vorschlag.** Für fachliche Ausführungen einen zuständigen Guard verbindlich verlangen und bei fehlendem Prüfer keinen Inhalt ausliefern. Probeläufe ohne Fachkontext separat behandeln. Den Testcontroller mit einem echten veränderlichen Prüfwert ausstatten und die Quellenänderung sowie den Entzug eines Fachrechts separat prüfen.

### F9 · Mittel · Die Ausgabeprüfung nimmt Antworten außerhalb des verlangten Formats an

- **Datei und Zeile.** [AiFormats.java:74](../backend/src/main/java/taskmanager/ai/internal/AiFormats.java#L74) · [AiFormats.java:117](../backend/src/main/java/taskmanager/ai/internal/AiFormats.java#L117) · [AiFormats.java:141](../backend/src/main/java/taskmanager/ai/internal/AiFormats.java#L141)
- **Szenario und Folge.** Die Prüfung extrahiert den Teil zwischen der ersten öffnenden und letzten schließenden Klammer. Sie akzeptiert damit einen Antwortsatz um das JSON und bei einer Ticketzusammenfassung selbst ein leeres Objekt. Bei doppelten JSON-Feldern gewinnt der letzte Wert. Die Java-Gegenprobe akzeptierte auch eine doppelte Quellenangabe, die erst eine unbekannte und anschließend eine bekannte Quelle enthielt. Eindeutige unbekannte Quellen in einem regulären Quellenfeld werden korrekt verworfen.
- **Vorschlag.** Genau ein vollständiges JSON-Objekt ohne umgebenden Text und ohne doppelte Schlüssel parsen. Pflichtfelder und zulässige Werte pro Anwendungsfall im Code und im Vertrag eindeutig festlegen. Unbekannte Quellen in einer strukturell gültigen Antwort als eigenen Fehler beibehalten.

### F10 · Mittel · If-Match schützt die erste Zuordnung nicht unter Nebenläufigkeit

- **Datei und Zeile.** [ConfigService.java:251](../backend/src/main/java/taskmanager/ai/internal/ConfigService.java#L251) · [AiStore.java:194](../backend/src/main/java/taskmanager/ai/internal/AiStore.java#L194) · [JdbcPermissions.java:422](../backend/src/main/java/taskmanager/permissions/internal/JdbcPermissions.java#L422)
- **Szenario und Folge.** Zwei zulässige Verwaltungsanfragen schreiben die erste Zuordnung eines Anwendungsfalls mit If-Match: "0". Wenn die Zeile fehlt, sperrt SELECT … FOR UPDATE keine Zeile. Beide Anfragen können Version 0 lesen. Das anschließende Upsert lässt die spätere Anfrage die frühere mit derselben neuen Version 1 überschreiben. Ein Modell- oder Anbieterschloss nach dem Lesen verhindert das nicht. Der Fall lässt sich bereits mit zwei ausgeschalteten Zuordnungen ohne Modell durch den SQL-Ablauf nachvollziehen.
- **Vorschlag.** Die Anlage je Unternehmen und Anwendungsfall vor dem Versionslesen serialisieren oder das Schreiben als atomaren Versionsvergleich implementieren. Gegenprobe mit zwei Transaktionen und unverändertem Ausgangsstand. Genau eine Anfrage soll erfolgreich sein, die andere 412 erhalten.

### F11 · Mittel · Zwei unterschiedliche Skillentwürfe können denselben ETag und Nachweis haben

- **Datei und Zeile.** [SkillService.java:65](../backend/src/main/java/taskmanager/ai/internal/SkillService.java#L65) · [SkillService.java:175](../backend/src/main/java/taskmanager/ai/internal/SkillService.java#L175) · [AiStore.java:150](../backend/src/main/java/taskmanager/ai/internal/AiStore.java#L150) · [SkillVersion.java:35](../backend/src/main/java/taskmanager/ai/internal/SkillVersion.java#L35)
- **Szenario und Folge.** Das Entwurfskennzeichen besteht aus Versionsnummer und Zeitstempel in Millisekunden. Zwei unterschiedliche Inhalte eines Entwurfs innerhalb derselben Millisekunde haben denselben ETag. Eine veraltete Ansicht kann danach speichern oder aktivieren. Eine Java-Gegenprobe mit zwei Zeitstempeln innerhalb derselben Millisekunde bestätigte die Kollision. Probeläufe speichern zudem nur draft-N, wodurch mehrere Bearbeitungsstände und nach einem Verwerfen wiederverwendete Nummern im Nachweis gleich aussehen.
- **Vorschlag.** ETag und Probelauf-Nachweis auf eine dauerhaft eindeutige Entwurfsidentität mit monotoner Revision stützen. Mit fester Uhr zwei Änderungen und anschließendes Aktivieren über ein altes Kennzeichen prüfen.

### F12 · Niedrig · Fachlich abgelehnte Starts fehlen im Ausführungsnachweis

- **Datei und Zeile.** [RunService.java:134](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L134) · [RunService.java:166](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L166) · [V0067__ai_baseline.sql:99](../backend/src/main/resources/db/migration/V0067__ai_baseline.sql#L99)
- **Szenario und Folge.** Ein berechtigter Versuch wird beispielsweise wegen einer Tagesgrenze oder fehlenden Zuordnung verworfen. Die Fehlerantwort geht vor insertRun zurück. Damit fehlt das in Regel 13 vorgesehene Ergebnis „abgelehnt“ im Nachweis. Der Zustand refused ist migriert und dokumentiert, aber kein Pfad schreibt ihn.
- **Vorschlag.** Fachlich abgelehnte Starts knapp und ohne Inhalt nachweisen. Die Erfassung nach dem Rollback sicherstellen und Ablehnungen von der verbrauchten Tagesquote abgrenzen. Einen Nachweis für einen berechtigten, wegen einer Grenze abgelehnten Start prüfen.

## Hinweise

### H1 · Ein unterbrochener Lauf wird nach 14 Minuten erst zur Bereinigung fällig

- **Datei und Zeile.** [RunService.java:410](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L410) · [AiCleanupTask.java:26](../backend/src/main/java/taskmanager/ai/internal/AiCleanupTask.java#L26) · [backend/README.md:683](../backend/README.md#L683)
- **Szenario und Vorschlag.** 14 Minuten sind bei Standardwerten die Altersgrenze ab der Aufnahme. Der nächste reguläre Durchlauf im 15-Minuten-Takt kann das sichtbare Ende auf ungefähr 29 Minuten verschieben, bei stillstehendem Worker weiter. Die Übergabe und README entsprechend präzisieren. Falls 14 Minuten eine zugesagte Frist sein sollen, die laufenden Ausführungen über einen eigenen Frist- oder Neustartnachweis identifizieren.

### H2 · Die größte Protokollseite verliert ihren Folgelink

- **Datei und Zeile.** [AiController.java:403](../backend/src/main/java/taskmanager/ai/web/AiController.java#L403) · [RunService.java:380](../backend/src/main/java/taskmanager/ai/internal/RunService.java#L380) · [ai.openapi.yaml:532](../contracts/ai.openapi.yaml#L532)
- **Szenario und Vorschlag.** Bei limit=200 bittet der Controller zur Ermittlung von next um 201 Zeilen, der Dienst begrenzt wieder auf 200. Mit mehr als 200 Einträgen fehlt der Folgelink. Den zusätzlichen Datensatz bei der internen Abfrage zulassen und den Vertrag mit exakt 201 Einträgen prüfen.

### H3 · Testersatz und Oberfläche decken ungültige Vorschläge und laufende Abbrüche kaum ab

- **Datei und Zeile.** [backend.mjs:3646](../frontend/tests/browser/backend.mjs#L3646) · [backend.mjs:3756](../frontend/tests/browser/backend.mjs#L3756) · [AiPage.tsx:595](../frontend/src/pages/AiPage.tsx#L595)
- **Szenario und Vorschlag.** Der Testersatz beendet Probeläufe sofort, setzt invalid immer auf null und gibt beim eigenen Lauf durch die Verteilung des internen Objekts zusätzlich sample zurück. Der Server liefert dieses Feld nie und kann einen zuvor erfolgreichen Lauf mit content: null und invalid liefern. Die Oberfläche meldet dann weiterhin einen fertigen Vorschlag. Den Testersatz an die Serverantworten angleichen und die Hinweise für einen ungültig gewordenen Vorschlag sowie den Abbruch eines laufenden Probelaufs prüfen.

### H4 · TLS mit eigener CA und der Portalweg brauchen direkte Gegenproben

- **Datei und Zeile.** [AiHttp.java:190](../backend/src/main/java/taskmanager/ai/internal/AiHttp.java#L190) · [AiRequests.java:35](../backend/src/main/java/taskmanager/ai/web/AiRequests.java#L35) · [AiApiTest.java:85](../backend/src/test/java/taskmanager/ai/AiApiTest.java#L85)
- **Szenario und Vorschlag.** Alle drei Adapter im Integrationstest nutzen einen lokalen HTTP-Server und die HTTP-Testfreigabe. Ein lokaler HTTPS-Test sollte korrekte CA, fehlende CA und falschen Hostnamen unterscheiden. Für A06 ergänzend einen echten Portalaccount gegen Status, Lauf und Verwaltung prüfen. Die 403-Prüfung steht im gemeinsamen Request-Pfad und beim Start über den Port.

## Abgleich der übrigen Prüfbereiche

| Bereich | Ergebnis |
| --- | --- |
| Geheimhaltung und Audit | AES-256-GCM verwendet einen zufälligen Nonce und trennt Zweck ai:provider:…:key und Zugang. Reguläre Anbieterantworten und Konfigurationsaudits enthalten den Schlüssel nur als Präsenzmerkmal. Der Protokollendpunkt setzt den Inhalt explizit auf null. F1 und F2 betreffen andere Wege. |
| Ziele, TLS und kein Ersatzmodell | Die Adapter hängen feste API-Pfade an die eingetragene Basis. Modellkennungen gehen als JSON-Werte in den Body. Eine CA ändert die Vertrauensprüfung und kein Ziel. Redirects sind deaktiviert, das Größenlimit liegt bei 2.000.000 Antwortbytes. Die Ausführung sendet genau die Modellkennung und den Zugang ihres Plans. Es gibt keinen automatischen Wechsel. F3, F4 und F6 begrenzen die Aussage. |
| Frame und Kontext | Quellen stehen ausschließlich in der User-Nachricht. Zufällige Markierungen und bereinigte Labels schützen die syntaktische Abgrenzung zur Systemanweisung. Dass ein Modell die Anweisung semantisch befolgt, muss am späteren Prüfsatz gemessen werden. Die Grundlage gibt den Adaptern keine Werkzeuge zur Änderung oder zum Versand. Der Zeichenrahmen entfernt optionale ältere Einträge, bevor gesendet wird. |
| Tagesgrenzen | Starts und Änderungen an den Grenzen nutzen denselben transaktionsgebundenen Advisory Lock pro Unternehmen. Zählen und Anlegen liegen im selben Vorgang. Der UTC-Tagesbeginn wird nach dem Lock ermittelt. Die CI-Tests prüfen die beiden Limits in Folge. Ein echter Konkurrenztest über zwei DB-Verbindungen war lokal nicht möglich. |
| Sichtbarkeit | Lauf und Abbruch sind auf Unternehmen und Besitzer begrenzt. Fremde IDs ergeben 404. Vorschläge sind nach 24 Stunden über die API unsichtbar, die physische Löschung folgt über den Worker. Fehlendes ai.use wird bei fachlichen Läufen erneut geprüft. Konfigurations- und Quellengrenzen sowie Deaktivierung unter Last sind Gegenstand von F4, F5 und F8. |
| Versionen und HTTP-Vertrag | Zugänge und Modelle sperren ihre Zeile, Grenzen nutzen den Advisory Lock. Die Bedingungen für vorhandene Zuordnungen und Entwürfe sind implementiert, mit den Lücken aus F10 und F11. AiContractTest belegt 26 Methoden-/Pfadpaare. JSON-Formen, Statuscodes und Header sind dadurch allein nicht abgedeckt. ai.manage ist im Identitätsvertrag und ai.use in der Projektaktionsliste ergänzt. H2 und H3 betreffen den beobachteten Vertragsabgleich. |

## Bewertung der Abweichungen in der Übergabe

| Abweichung | Bewertung |
| --- | --- |
| Modellliste statt Textgenerierung bei „Verbindung prüfen“ | Vertretbar und in DEC-156 festgehalten. Das prüft den Zugang und die gemeldeten Modellnamen. Die Eignung zur Generierung belegt der getrennte Probelauf. |
| Zwei laufende und vier wartende Ausführungen je Prozess | DEC-156 nennt ausdrücklich den Prozess. Für einen einzelnen Webprozess entspricht das der beabsichtigten Kapazität. Mehrere Webprozesse vervielfachen die Plätze, die Tagesgrenzen bleiben gemeinsam. TODO-Regel 10 spricht weiterhin von der Installation. Die Betriebsgrenze daher eindeutig formulieren. F7 bleibt unabhängig davon bestehen. |
| Unterbrechung durch Neustart endet „nach höchstens 14 Minuten“ | Der Bereinigungsmechanismus ist als nachgelagerte Statuskorrektur vertretbar. Die genannte Obergrenze gilt im bestehenden Takt nicht, siehe H1. |
| ai.use bisher über den Testcontroller | Für die Grundlage vertretbar. Der Testcontroller ruft den echten Port und die echte Projektberechtigung auf. Die Fachmodule müssen ihre zusätzlichen Lese- und Aktionsrechte mit ihren konkreten Quellen nachweisen. F8 betrifft die Voreinstellung des Ports. |

## Ausgeführte Prüfungen

Die lokale Prüffassung wurde aus der vorherigen isolierten Kopie, den verifizierten Basisdateien und dem veröffentlichten PR-Diff rekonstruiert. Der Vergleich umfasst 67 Dateien, 8.559 hinzugefügte und 42 entfernte Zeilen. Es wurden ausschließlich synthetische Werte, lokale Testdoppel und vorhandene CI-Protokolle verwendet. Die zusätzlichen Java-Gegenproben liefen außerhalb des Repository-Quellbaums. Es gab keine Aufrufe gegen echte KI-Anbieter.

| Prüfung | Ergebnis |
| --- | --- |
| `python3 scripts/validate_repository.py`, `python3 scripts/task_board.py --check`, `git diff --check` | Lokal und in CI grün. |
| `python3 -m unittest discover -s tests -v` | Lokal 50 Fälle gestartet. Neun Fehler und eine fehlgeschlagene Assertion beim Zugriff auf lokale HTTP-Testserver, ein Fall übersprungen. In CI 50 grün. |
| Backend, angeforderte Auswahl | Lokales Maven kompiliert und führt ModuleStructureTest 2/2 erfolgreich aus. Die übrigen angeforderten Klassen benötigen den hier nicht erreichbaren Docker-Daemon für Testcontainers. In CI mit PostgreSQL 18.6 alle 700 Tests grün, darunter AiApiTest 6/6, AiContractTest 1/1, ModuleStructureTest 2/2, DatabaseFoundationTest 4/4, IdentityPermissionsAdapterTest 18/18 und AdministrationRightsApiTest 10/10. |
| `backend/scripts/lifecycle-check.sh` | Lokal ohne erreichbaren Docker-Daemon und ohne isolierte PostgreSQL-Umgebung nicht ausgeführt. Im Backend-CI für den Prüfstand erfolgreich. |
| `npm run verify` | Lokal und in CI grün, 102 Unit-Tests, Typprüfung und Build. Generierte Clienttypen stimmen lokal mit dem Index überein. |
| `ai.test.mjs` | Lokal fünf Versuche mit ERR_NETWORK_ACCESS_DENIED beim Zugriff auf localhost. In CI fünf KI-Browsertests und alle 315 Browsertests grün. |
| Unabhängige Java-Gegenproben | Bestätigt wurden Schlüsselbindung an die Zugangs-ID, Mitnahme des Schlüssels bei Zieländerung, falsche lokale Einstufung, Schlüssel im Headerfehler und im validierten Antworttext, fehlende Body-Frist, verlorener Queue-Platz, neu angelegter Vorschlag nach Deaktivierungsereignis, Auslieferung ohne Guard und nach Revision sowie ausgeweitete JSON-Annahme und Kollision der Entwurfskennzeichen. Details stehen bei F1 bis F11. |
