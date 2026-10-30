# Prüfung TASK-063 / PR #208

30. Oktober 2026 · main nach PR #208, Commit `86c0faa9ea50a48a061b54d9d90549e934cc5299` · Sitzung `codex-20261030-task063-review-01`.

Hinweis zur Ablage: Codex konnte den Bericht nicht selbst veröffentlichen. Holger hat ihn am 30. Oktober 2026 im Projektgespräch übergeben; die Sitzung claude-20261030-replyfix-02 legt ihn unverändert ab. Die Prüfung lief in einer Sitzung mit vorgegebenem Lesestoff, ohne Testausführung.

**Ergebnis:** Die bisherigen F1 bis F4, H1 und H6 sind im gelesenen Code nachvollziehbar korrigiert. H4 ist hinsichtlich Barrieren und Aufräumen verbessert; die verlängerten SMTP-Testzeitlimits liegen außerhalb des vorgegebenen Testabschnitts und sind hier nicht verifiziert. Kein neuer P1-/P2-Befund; ein P3-Randfall beim Betreff bleibt.

## Abgleich der Korrekturen

| Punkt aus TASK-062 | Bewertung |
|---|---|
| **F1 – Job-Wiederholung gegen Klärung** | `Jobs.active` verlangt mit `MANDATORY` eine bestehende Transaktion. `shareState` liest mit `FOR SHARE`; `lock` der Jobverwaltung verwendet `FOR UPDATE`. Damit bleibt der Zustand bis zum Ende der klärenden Transaktion gegen Wiederholung/Abbruch gesperrt. Der neue Test prüft ein wartendes Watch gegen eine uncommittete Wiederholung und anschließend den normalen Versand. Die andere Reihenfolge wird nach bereits abgeschlossener Klärung geprüft. |
| **F2 – lange Kopfzeilen** | Betreff erhält Faltung beziehungsweise UTF-8/Base64-Wörter mit höchstens 45 Nutzdatenbytes; Codepoints werden nicht geteilt. Faltungsleerraum zwischen kodierten Wörtern gehört nicht zum dekodierten Inhalt. Namen werden nach Codepoints gekürzt; abschließend werden alle Kopfzeilen auf das konfigurierte Zeilenmaximum geprüft. Damit wird auch eine nicht ausreichend gefaltete Zeile vor dem Versand zurückgewiesen. Ein Inhaltsrandfall bleibt: neuer F1. |
| **F3 – zweite echte Commit-Reihenfolge** | Jetzt startet tatsächlich `intake.fetch`. Die Hilfsverbindung hält die Ticketzeile; der Eingang wartet nach seinem Kundenvergleich, und der echte Kundenwechsel wartet seinerseits auf den Eingang. Nach Freigabe werden angehängte Mail, erhöhte Mailzahl und anschließender Kundenwechsel geprüft. Auch `state = review` wird in der ersten Reihenfolge ausdrücklich geprüft. |
| **F4 – EOF als Übergabe gezählt** | EOF vor der Abschlusszeile landet in `broken`, nicht in `handedOver`. `DROP_IN_DATA` erfasst einen Abbruch nach der ersten Inhaltszeile. |
| **H1 – ungültiges by** | Fehlendes `by` bleibt der dokumentierte Altauftragsfall. Ungültiges `by` ergibt bei `pending` jetzt `failed/helpdesk.job_invalid`, ohne SMTP-Verbindung. Bei bereits `sending` bleibt die konservative Einstufung `uncertain` richtig: Eine vorherige Übergabe darf nicht nachträglich als sicher gescheitert gelten. |
| **H4 – Teststabilität** | `pg_blocking_pids` identifiziert Wartevorgänge auf der eigenen Verbindung statt datenbankweit irgendeine Advisory-Sperre. Der SMTP-Testserver schließt den aktuellen Socket und wartet begrenzt auf seinen Thread. Längere Zeitreserven für HELD/SLOW_ACCEPT sind aus den erlaubten Ausschnitten nicht nachgewiesen; siehe H2. |
| **H6 – Watch-Batch** | Keyset-Seiten nach unveränderlicher Antwort-ID ersetzen die Auswahl nur der ersten 200 Einträge. Aktive Antworten verdecken spätere verwaiste Antworten nicht mehr. Der Test stellt 250 früh einsortierte aktive Kopien vor einen verwaisten Eintrag. |

## Befund

### F1 · P3 · Ein wie ein kodiertes Wort aussehender Betreff kann seinen Inhalt ändern

`subject` übernimmt einen kurzen ASCII-Betreff nach `MimeUtility.encodeText/fold`, wenn die Zeilen kurz genug sind. Ein wörtlich eingegebener Betreff wie `=?UTF-8?B?SGFsbG8=?=` kann damit unverändert als RFC-2047-Wort in der Kopfzeile stehen und beim Empfänger als `Hallo` erscheinen. Die Eingabezeichenfolge wird dann als Kodierung interpretiert, statt als sichtbarer Text erhalten zu bleiben.

**Empfehlung:** Solche Eingaben ebenfalls vollständig kodieren und einen Dekodierungs-Roundtrip für normale, lange, internationale sowie bereits wie kodierte Wörter aussehende Betreffe prüfen. Statische Bewertung, nicht nachgestellt; der Randfall bestand möglicherweise bereits mit der früheren Jakarta-Mail-Verwendung und wird nicht als neu eingeführter Fehler behauptet.

**Stelle:** `backend/src/main/java/taskmanager/helpdesk/internal/SmtpMailSender.java:222–249`.

## Hinweise

**H1 – Kein konkreter neuer Deadlock im gelesenen Ablauf.** Der Watch nimmt Antwort → geteilte Jobsperre; `prepare` nimmt die Antwortsperre. Die gelesene Jobverwaltungssperre sperrt nur den Job. Daraus allein entsteht kein geschlossener Wartezyklus. Die übrigen Jobs-Aufrufer waren nicht freigegeben; eine allgemeine Deadlock-Freigabe erfolgt daher nicht. Der neue Test belegt insbesondere nicht durch Ausführung dieser Prüfung sämtliche Sperrreihenfolgen.

**H2 – Verbleibende Testannahmen.** `SLOW_ACCEPT` wartet weiterhin sechs Sekunden. Testkonfiguration und ältere HELD-Tests vor `pid(` wurden entsprechend dem Auftrag nicht gelesen. Deren Zeitreserven sind deshalb offen. Der neue `DROP_IN_DATA`-Test erwartet zwingend `uncertain`, setzt aber voraus, dass der Client bereits alles geschrieben hat, bevor er den Abbruch bemerkt. Der Testserver erzwingt diese Reihenfolge nicht ausdrücklich; die Erwartung hängt vom Schreib-/Pufferverhalten des Clients ab. Das ist ein Stabilitätsrisiko, kein hier beobachteter Fehlschlag.

**H3 – Begrenzte Garantien.** Der Watch läuft durch mehrere einzelne Transaktionen. Neue Einträge hinter einer bereits passierten UUID werden gegebenenfalls erst im nächsten Lauf erreicht; fünf Minuten Laufzeitlimit und eine große Liste bleiben Grenzen. Die ursprüngliche Verdeckung durch die ersten 200 aktiven Antworten ist trotzdem behoben. `ScriptedSmtp.close` wartet höchstens fünf Sekunden und prüft danach nicht ausdrücklich, ob der Thread beendet ist.

## Gelesen und ausgeführt

Gelesen wurden ausschließlich die angeforderten Berichtspunkte F1–F4/H1/H4/H6, DEC-093, die genannten Jobs-Methoden, ReplyWatch, unsettledReplies, prepare/confirmedBy, message/subject/encodedWord/address, ScriptedSmtp und der Testabschnitt ab pid bis vor unknown.

main wurde auf den oben genannten PR-208-Commit aufgelöst; für den Bericht wurde eine getrennte lokale Prüfkopie angelegt. **Keine Tests und keine CI-Läufe ausgeführt oder ausgewertet.** Nur die Berichtsdatei wurde hinzugefügt; abschließende Kontrolle mit Git-Status und Leerzeichenprüfung.

## Empfehlung zur gemeinsamen Abnahme

**Die Korrekturen von TASK-062/TASK-063 sind im begrenzten Leseumfang als Grundlage für die gemeinsame Abnahme von TASK-058, TASK-062 und TASK-063 vertretbar.** Kein nachgewiesener neuer schwerwiegender Fehler verhindert diese Empfehlung. Den P3-Betreffrandfall und die noch nicht verifizierten Zeitreserven bewusst als Restpunkte behandeln; vor einer uneingeschränkten technischen Freigabe den gezielten Betreff-Roundtrip und die SMTP-Testannahmen nachweisen.

Dieser Bericht bestätigt keine grünen Testläufe. Die bereits dokumentierten Produktgrenzen und die fachliche Abnahme bleiben bei Holger.
