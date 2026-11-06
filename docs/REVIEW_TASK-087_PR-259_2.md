# Zweite Nachprüfung TASK-087 / PR #259

**Empfehlung: nachbessern.** R2 und die beanstandete Vertragsbeschreibung sind korrigiert. R1 funktioniert bei erfolgreichem Nachladen mit neuer ETag, ist aber nicht sauber an den Abschluss dieses Ladevorgangs gebunden (S1). H1 ist für fehlende/null Teile und ungültige Zeichenfolgen korrigiert; falsche JSON-Typen bleiben abweichend (S2).

## Prüfstand

- Codex, Sitzung `codex-20261106-task087-followup-02`, 6. November 2026.
- Repository `1uedev/task-manager`, Branch `claude/project-thread-tzp0cw`, geprüfter Kopf `45e23d51175eea35913c14f09cb4bffbc09cebed`. GitHub meldet #259 bereits als gemergt, Mergecommit `b783b35c62bd30e2680d168f69113fd4bba655f5`; dies ersetzt keine fachliche Abnahme.
- Bezugspunkt des Auftrags: `aee0b0b922db58b0f69e24778002d8fbc7fe2709`. Der geprüfte Kopf liegt drei Commits darüber. Die genannten Appearance-Implementierungen, API-/Browsertests und der Vertrag sind gegenüber diesem Bezugspunkt unverändert; im inzwischen veränderten `backend.mjs` ist der Appearance-Schreibblock identisch.
- Grundlagen: erster Nachprüfbericht, TODO/TASK-087 einschließlich Nachbesserung, DEC-117 (7)–(9), AGENTS.md und ergänzende Provider-/Controller-/Vorschauimplementierung. Keine Anwendungsänderung, keine Abnahme und kein externer Reviewkommentar durch diese Sitzung.

## Befunde

### S1 [P2] – Die Übernahme hängt an einer ETag-Änderung statt am angeforderten Nachladen

**Stellen:** `frontend/src/pages/AppearancePage.tsx:75–89,312–315`; `frontend/src/brand/BrandProvider.tsx:73–89`.

Die Aktion setzt `takeSaved.current = notice.part`, löscht die Konfliktmeldung und startet `reload()`. Der übernehmende Effekt läuft ausschließlich bei Änderung von `loadedEtag`. Der Provider behält während des Ladevorgangs zunächst seine bisherige Verbindung. Kommt erfolgreich dieselbe ETag zurück, läuft der Effekt deshalb nicht: Der gescheiterte Teil behält seinen Entwurf, und der Übernahmeauftrag bleibt offen. Eine unveränderte ETag ist beim normalen Konflikt mit inzwischen höherer Serverversion nicht zu erwarten, wird aber als ausdrücklich angefragter Randfall nicht korrekt abgeschlossen.

**Gezielte Ablaufprobe:** ETag `"1"`, Namensentwurf und ungespeicherte Farbe; Übernahmeauftrag `name`; Antwort wieder `"1"`. Name und Farbe bleiben unverändert, Auftrag weiterhin `name`. Erhöht anschließend ein erfolgreiches Farbspeichern die ETag, wird der alte Auftrag abgearbeitet und der dann aktuelle Namensentwurf unerwartet durch den Servernamen ersetzt. Damit kann eine spätere, andere Teiländerung erneut Eingaben verwerfen.

Scheitert das Nachladen, setzt der Provider die Verbindung auf `offline`; `loaded` wird null. Der Effekt kehrt vor dem Löschen des Auftrags zurück. Bearbeitete Name-/Farbwerte bleiben zwar erhalten, aber die Konfliktmeldung samt Ladeaktion ist bereits entfernt, administrative Aktionen verschwinden beziehungsweise sind deaktiviert, und gespeicherte Werte fallen für die Darstellung auf Vorgaben zurück. Es gibt hier keine gezielte Wiederholung des fehlgeschlagenen Konflikt-Nachladens. Bei einer späteren erfolgreichen Provider-Ladung kann der offene Auftrag inzwischen neu bearbeitete Eingaben übernehmen. Die Ablaufprobe bestätigt den bestehenbleibenden Auftrag und dessen spätere Übernahme; ein vollständiger Browserlauf wurde nicht ausgeführt.

**Korrektur:** Ergebnis und Zielteil an genau den gestarteten Ladevorgang binden. Auch eine erfolgreiche Antwort mit gleicher ETag ausdrücklich abschließen. Bei Fehler die Eingaben und eine wiederholbare Fehlermeldung erhalten und keinen ungebundenen Übernahmeauftrag hinterlassen. Regressionen für gleiche ETag, Ladefehler und anschließende Bearbeitung/andere Teiländerung ergänzen.

### S2 [P3] – Ungültige JSON-Typen lassen den Testersatz weiterhin ohne HTTP-Antwort abbrechen

**Stelle:** `frontend/tests/browser/backend.mjs:472–479`.

`{}` und `{name:null}` ergeben jetzt korrekt 400 `appearance.change_empty`; null zusammen mit gültiger Farbe lässt den Namen unverändert. Leere/überlange/verbotene Namen und ungültige Farbzeichenfolgen werden vor dem Inhalts-/Versionsvergleich geprüft. Diese konkrete H1-Korrektur ist gelungen.

Falsche Typen sind jedoch nicht abgefangen: `{name:42}` und `{name:{}}` werfen bei `.replace` einen `TypeError`. `{accentColor:["#f97316"]}` besteht durch implizite Stringumwandlung den Regex-Test und wirft anschließend bei `.toLowerCase` ebenfalls `TypeError`. Alle drei Abbrüche wurden über den tatsächlichen installierten Route-Handler reproduziert. Der Server bindet dagegen auf ein `ChangeRequest` mit Stringfeldern und liefert eine reguläre HTTP-Antwort; für Objekte/Arrays ist insbesondere die JSON-Bindung maßgeblich. Die genaue serverseitige Zahl-zu-String-Konvertierung wurde hier nicht live geprüft.

**Korrektur:** Typbehandlung an der tatsächlichen Serverbindung ausrichten und reguläre Fehlerantworten liefern. Nicht nur die String-Zeichenregel vergleichen. Betrifft den Testersatz, kein nachgewiesener Produktionsfehler.

## Antworten auf die Prüffragen

### 1. Erhalten der anderen Eingaben

Bei erfolgreichem Nachladen mit neuer ETag ist die Auswahl des Teils korrekt. Die Original-Effektfunktion wurde mit kontrollierten Eingaben für alle vier Teile ausgeführt:

| Gescheiterte Aktion | Übernahme nach erfolgreichem Nachladen | Andere ungespeicherte Eingaben |
| --- | --- | --- |
| Name | Servername | Farbe einschließlich rohem, auch ungültigem Hex-Eingabetext bleibt. |
| Farbe | Serverfarbe und zugehöriger Hex-Text | Namensentwurf bleibt. |
| Logo, einschließlich Entfernen | Neue gespeicherte Logodarstellung über Provider | Name und Farbe bleiben. |
| Alles zurücksetzen | Servername und Serverfarbe, Logo über Provider | Übernimmt absichtlich alle Auftrittsteile, passend zu `part: all`; führt keinen erneuten Reset aus. |

Für Administratoren wird eine gewählte Logodatei unmittelbar hochgeladen; es gibt keinen separaten wartenden Logoentwurf. Die neue Browserregression prüft Name und Farbe bei jeweils höherer Version. Logo-/Reset-Konfliktnachladen, gleiche ETag und Ladefehler deckt sie nicht ab. Für die beiden letzten Fälle gilt S1.

### 2. Keine Änderung, Version, Audit und Gleichzeitigkeit

**Nach Codeprüfung korrekt.** `reset` prüft Standardname, Standardfarbe und fehlendes Logo; `removeLogo` prüft fehlendes Logo; `replaceLogo` vergleicht erkannten Typ und vollständige Bytes. Alle Rückgaben liegen vor UPDATE und Audit. Auch Änderungszeitpunkt und Bearbeiter bleiben dadurch unverändert.

Jede dieser Prüfungen liegt innerhalb derselben Transaktion nach `locked(expectedVersion)`, das die einzige Einstellungszeile mit `FOR UPDATE` sperrt und zuerst die Version prüft. Zwei gleichzeitig bereits unveränderte Anfragen mit gültiger gleicher Version können daher nacheinander beide 200 erhalten, ohne Version oder Audit zu ändern. Ändert die erste Anfrage tatsächlich den Inhalt, wird die zweite mit alter Version weiterhin mit 412 abgewiesen, selbst wenn ihr Inhalt dann gleich wäre. Beim Logo findet dessen Formatvalidierung vor der Sperre statt; die Versionsprüfung liegt weiterhin vor dem Vergleich mit dem gespeicherten Inhalt.

Der ergänzte API-Test prüft seriell Standardreset, Entfernen ohne Logo, identischen PNG-Upload, alte Version beim Entfernen und die resultierende Auditliste. Er enthält **keinen parallelen Aufruf**. Die Nebenläufigkeitsaussage ist hier eine Prüfung der Sperr-/Transaktionslogik, kein neu ausgeführter Datenbanknachweis.

### 3. Testersatz

Für `{}`, null Teile und ungültige Stringwerte mit vorhandenem gültig geformtem If-Match ist die konkrete Korrektur passend. Ungültige Werte werden vor dem Versionskonflikt geprüft. Falsche JSON-Typen bleiben S2. Die Gleichheit sämtlicher HTTP-/Bindungsregeln ist damit nicht nachgewiesen.

### 4. Vorschauvertrag

**Die beanstandete Namensregel ist korrigiert.** Appearance 0.3.2 entfernt `maxLength: 80` vom Rohwert von `MailTemplatePreviewRequest.name`. Die Beschreibung verweist auf `AppearanceChange.name` und setzt die Obergrenze ausdrücklich nach dem Entfernen der Ränder an. Das entspricht `MailTemplateService.preview`, das `AppearanceService.validName` verwendet. Ein Rohwert mit 80 Zeichen plus geschützten Randzeichen wird somit nicht mehr durch eine falsche Roh-Obergrenze ausgeschlossen. Keine erneute Vollprüfung aller Vorschaufelder.

## Hinweise

- **H1 – Nebenläufigkeitsnachweis ergänzen:** Den seriellen R2-Test um zwei echte gleichzeitige unveränderte Reset-/Entfern-/Logoanfragen sowie veraltete, inhaltlich identische Anfragen ergänzen. Erwartung bei bereits gleichem Inhalt: 200/200, identische Version, unveränderte Auditanzahl.
- **H2 – Logo-Testersatz bleibt vereinfacht:** `backend.mjs:493–497` vergleicht nur Bytes und setzt den Typ immer auf PNG. Eine Probe mit gleichen Bytes und danach `image/jpeg` wurde unverändert akzeptiert; der echte Service würde einen unpassenden deklarierten Typ vor dem Vergleich ablehnen. Logo-Typvalidierung und Audit sind daher weiterhin am echten Server zu belegen.

## Tatsächlich ausgeführte Prüfungen und Grenzen

- Quellen am festen GitHub-Kopf gelesen; Vergleich mit `aee0b0b` und Prüfung des PR-Status über die GitHub-Anbindung.
- Node 24.22.1: direkter Aufruf des echten Browser-Testersatz-Route-Handlers mit synthetischen Requests für fehlende/null Teile, ungültige Strings und Typen, unveränderten Reset/Entfernen sowie wiederholten Logo-Upload. Erwartete Antworten beziehungsweise die oben beschriebenen Abbrüche bestätigt.
- Gezielte Ablaufproben mit der unveränderten Effektfunktion und kontrollierten Abhängigkeiten: alle vier Zielteile, gleiche ETag, neue ETag, Ladefehler und spätere Übernahme. Dies sind isolierte Logikproben, keine React-/DOM-/Browserintegration.
- Bericht auf Whitespacefehler geprüft.
- **Nicht ausgeführt:** Maven-/PostgreSQL-Tests, Frontend-Verifikation, Browser-Suite und Repository-Pythonprüfungen. Der Git-Clone scheiterte an der nicht erreichbaren Netzwerkproxy-Verbindung; verfügbar war nur ein gezielt über die GitHub-Anbindung bezogener Quelldateiauszug, kein vollständiger ausführbarer Checkout. Frühere grüne Testläufe werden nicht als eigene Verifikation übernommen.

## Empfehlung an Holger

**Nachbessern, insbesondere S1 schließen, danach gezielt nachprüfen.** R2 und die konkrete H2-Vertragsabweichung sind nach der beschriebenen Prüfung behoben; H1 ist nur teilweise vollständig. TASK-081 und TASK-087 bleiben fachlich ohne Abnahme durch diese Sitzung. Über ihre gemeinsame Abnahme entscheidet Holger.
