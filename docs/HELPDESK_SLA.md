# Helpdesk, Kundenportal und Servicefristen

Stand 15. Oktober 2026 · TASK-006 · Fachlicher Entwurf, keine implementierte Funktion

Dieses Dokument konkretisiert REQ-HD-001 bis REQ-HD-005 und REQ-PORTAL-001 bis REQ-PORTAL-003 aus [REQUIREMENTS.md](../REQUIREMENTS.md). Es ergänzt den [Fachmodellentwurf](DOMAIN_MODEL.md), die [Berechtigungen](PERMISSIONS.md) und die [Architektur](ARCHITECTURE.md). Die Beispieldaten sind synthetisch.

**Bestätigt:** IMAP/SMTP, SLA-Fristen, Geschäftszeiten und Eskalationen gehören zu V1. Externe sehen Anfragen ihrer Kundenorganisation in freigegebenen Projekten. Am 13. Oktober hat Holger zusätzlich entschieden: Unbekannte E-Mail-Absender kommen in eine Prüfwarteschlange zur Kundenzuordnung durch Support (DEC-031). Während auf den Kunden gewartet wird, pausiert die Lösungsfrist; eine noch offene Erstreaktionsfrist läuft während der Geschäftszeiten weiter (DEC-032).

**Vorschlag:** Die übrigen Regeln, Beispielwerte, Versandzustände, Fristdefinitionen und Prioritäten sind zur Prüfung gestellt. Insbesondere sind zwei Stunden für die erste Antwort und acht Stunden für die Lösung keine zugesagten Produktstandards oder Vertragswerte. Die Benutzeroberfläche soll diese Begriffe erklären und die konkrete Fälligkeit samt Kalender anzeigen.

## Vom E-Mail-Eingang zur Anfrage

Eine Postfachkonfiguration gehört im Vorschlag zu einem Unternehmen und einem Zielprojekt. Mehrere Postfächer können dasselbe Projekt bedienen; Anzahl und Anbieter werden vor dem Pilotbetrieb festgelegt. Abweichende Typ- oder Organisationsregeln sind nur mit eindeutig prüfbarer Zuordnung zulässig. Mailprotokolle allein legen noch kein unterstütztes Anmeldeverfahren beim Mailanbieter fest.

| Eingang | Vorgeschlagene Verarbeitung |
|---|---|
| Bekannter, ausreichend geprüfter Kontakt; neue Konversation | Anfrage im erlaubten Projekt anlegen, zugeordnete Kundenorganisation übernehmen. Keine Projekt- oder Gruppenerweiterung durch die Nachricht. |
| Unbekannter Absender | **Bestätigt DEC-031:** Eingang in Prüfwarteschlange. Support prüft und ordnet den Kunden zu. Kein automatischer Portalzugang. |
| Antwort auf vorhandene Konversation | Nachrichtenbezug und Absenderberechtigung gemeinsam prüfen. Ticketnummer, Betreff oder Antwortadresse allein reichen nicht. |
| Unklare Identität oder unzulässiger Kunden-/Projektbezug | Ebenfalls zur Prüfung zurückhalten; nichts an die mögliche fremde Konversation anhängen und keine vertrauliche automatische Antwort. |
| Erneuter Abruf derselben Nachricht | Bereits erfassten Eingang wiedererkennen; keine zweite Fachaktion. |
| Automatische Antwort, Zustellfehler oder Schleifenverdacht | Als technisches Ereignis behandeln beziehungsweise zurückhalten; kein unkontrolliertes Wechselspiel neuer Tickets und Antworten. |
| Nachricht an archiviertes Projekt | Sichtbarer Eingang zur internen Klärung, keine verdeckte Änderung des archivierten Projekts. |
| Dateianhang | Größen- und Inhaltsprüfung, Rechte am späteren Beitrag; kein ungeschützter öffentlicher Download. Gesperrter Inhalt bleibt für den Empfänger gesperrt. |

Eine bekannte Absenderadresse ist noch kein belastbarer Identitätsnachweis. Der Mailadapter muss definierte Vertrauenssignale des konfigurierten Mailwegs auswerten; zweifelhafte Eingänge bleiben in der Prüfwarteschlange. Welcher Mailanbieter und welches Verfahren dafür eingesetzt werden, bleibt unter OPEN-009 offen. Das Produkt darf aus einer gefälschten From-Adresse keine Zugriffsrechte ableiten.

Die Prüfwarteschlange speichert nachvollziehbar Eingang, Grund und Bearbeitungszustand. Nur zuständiger Support sieht den Inhalt. Er kann die Nachricht einem nachgewiesenen Kontakt und Kunden zuordnen, nach Prüfung eine Anfrage anlegen oder sie begründet verwerfen. Kontaktanlage und Konto-/Portalrechte sind getrennte Verwaltungsaktionen. Erneute Bearbeitung desselben Eingangs darf nicht doppelt anlegen. Aufbewahrung und Löschung verworfener Eingänge gehören zu TASK-010.

**CC und Weiterleitungen:** CC-Adressen werden nicht automatisch Projektmitglieder, Portalkontakte oder spätere Empfänger. Beim Weiterleiten zählt die tatsächliche geprüfte Identität des Einlieferers; zitierte ursprüngliche Absender verleihen keine Rechte. Eine fremde Kundenorganisation im Verteiler stoppt die automatische Zuordnung. Empfänger und sichtbare Inhalte werden vor Versand erneut geprüft.

Die Architektur sieht Herkunftskennungen je Mailkonto, Ordner, UIDVALIDITY und UID sowie getrennte Wiederholungsprüfungen vor. Ein Wechsel der Kennungen oder dieselbe Nachricht in einem weiteren Ordner braucht einen Abgleich. Message-ID und Inhaltsähnlichkeit helfen bei Verdachtsfällen, rechtfertigen aber kein ungeprüftes Zusammenführen verschiedener legitimer Nachrichten. Die technischen Grundlagen sind im [Architekturentwurf](ARCHITECTURE.md) verlinkt.

## Kundenportal und Antworten

Bestätigt für V1: Administratoren legen lokale Portalzugänge mit Einmalpasswort an; bei erster Anmeldung ist der Passwortwechsel verpflichtend (DEC-053). Der vorgeschlagene Einladungslink ist damit ersetzt. Vor dem Pflichtwechsel ist regulärer Fachzugriff über Portal und API gesperrt; danach gelten die aktuellen Organisations-/Projektfreigaben. Gültigkeit, sichere Übergabe, erneute Ausgabe und Wiederherstellung werden gemäß [PERMISSIONS.md](PERMISSIONS.md) noch ausgestaltet. Ein E-Mail-Eingang erzeugt kein automatisches Konto. Offene Selbstregistrierung gehört nicht zum gewählten Kontoanlageweg; anonyme Webformulare bleiben unbestätigt.

Im Servicebeispiel sehen Lea und Paul die Nordwerk-Anfragen des freigegebenen Projekts. Mia sieht die Südtechnik-Anfragen. Anlegen, Lesen und Antworten verwenden die gleichen Fachrechte wie interne Oberfläche und API. Interne Notizen, ihre Dateien, versteckte Felder und nicht freigegebenes Wissen bleiben unsichtbar. Eine Wissensseite wird nicht allein durch Verknüpfung mit einem Ticket extern lesbar.

Beim Antworten ist die Zielgruppe sichtbar: interne Notiz oder Kundenantwort. Der Entwurf zeigt Empfänger, Kanal, Sprache, Vorlage und Anhänge. Unternehmenslogo und Farben gelten auch für E-Mails. Die Vorlage erhält nur Daten, die den Empfängern offenstehen. Eine interne Notiz wird nicht durch eine spätere automatische Benachrichtigung zur Kundenantwort.

## Versand und Bestätigungen

| Zustand | Bedeutung im Vorschlag |
|---|---|
| Entwurf | Noch keine ausgehende Nachricht. Ein KI-Entwurf ist als solcher gekennzeichnet. |
| Zur Ausführung bestätigt | Berechtigte Person hat Inhalt, Empfänger und Anhänge freigegeben. Änderungen daran verlangen erneute Bestätigung. |
| Ausstehend | Dauerhafter Versandauftrag; ein Worker prüft aktuelle Rechte und Datenstand. |
| Vom SMTP-Server angenommen | Annahme wurde bestätigt; dies ist kein Beweis für Lesen oder endgültige Zustellung. |
| Sicher fehlgeschlagen | Fehler ist sichtbar; Wiederholung nur gemäß Fehlerart und gültiger Freigabe. |
| Versandstatus unklar | Annahme könnte erfolgt sein, Bestätigung fehlt. Keine blinde automatische Wiederholung; zuständige Person klärt und entscheidet mit Hinweis auf das Duplikatrisiko. |

Manuell formulierte Antworten werden über die normale Sendeaktion freigegeben. KI-generierte Änderungen und Nachrichten brauchen immer die bestätigte, konkrete Zustimmung; weder ein Skill noch eine Automationsregel ersetzt sie. Vordefinierte nicht durch KI erzeugte Systembenachrichtigungen können durch bewusst konfigurierte Regeln ausgelöst werden, sofern Empfängerrechte und Wiederholungsschutz gelten. Deren genaue V1-Auswahl bleibt offen.

Ein Neustart darf denselben internen Auftrag nicht als zweite Fachnachricht anlegen. Bei SMTP ist eine doppelte tatsächliche Zustellung nach einem unklaren Verbindungsabbruch trotzdem nicht generell auszuschließen. Der aktualisierte Abnahmevorschlag in REQ-TKT-005 unterscheidet deshalb interne Wiederholungen und nachvollziehbare Behandlung unklarer Zustellung; er ist keine uneingeschränkte Exactly-once-Zusage. Ein späterer Zustellfehler bleibt auch nach erfolgreicher SMTP-Annahme sichtbar.

## SLA-Regel und Kalenderzuordnung

Eine SLA-Regel besitzt im Vorschlag Kennung, Version, Erstreaktionsbudget, Lösungsbudget, Kalender, Prioritätswerte und Eskalationsstufen. Vorgeschlagene Auswahlreihenfolge: konkrete Kombination aus Projekt, Kundenorganisation und Tickettyp; danach Projekt und Kundenorganisation; danach Projektstandard. Mehrere gleich passende Regeln verhindern die Konfigurationsfreigabe. Ein Projekt ohne zugeordnete SLA zeigt „Keine SLA zugeordnet“, keine fiktive grüne Frist.

Beim Start werden Regel- und Kalenderversion gespeichert. Spätere Konfigurationsänderungen gelten zunächst für neue Bearbeitungszyklen. Bereits laufende Zyklen ändern sich nur durch eine ausdrücklich protokollierte Neubewertung mit alten und neuen Werten. Individuelle Serviceverträge und komplexere Vererbung sind keine zusätzliche V1-Zusage.

Ein Kalender definiert IANA-Zeitzone, Wochentage mit einem oder mehreren offenen Intervallen sowie datierte Ausnahmen. Geschlossene Intervalle und Kundenpausen zählen nicht für die Lösungszeit. Überschneidende Öffnungszeiten werden nicht doppelt gezählt. Startgrenzen sind eingeschlossen, Endgrenzen ausgeschlossen. Eine Antwort genau zum berechneten Fristende gilt als rechtzeitig.

Zeitdauer wird über tatsächliche verstrichene Minuten innerhalb der offenen Intervalle gezählt. Für eine Zeitumstellung werden Kalendergrenzen mit der gespeicherten Zeitzone in eindeutige Zeitpunkte aufgelöst; Anzeige und Protokoll enthalten bei Mehrdeutigkeit den UTC-Offset. Vorschlag für ungewöhnliche Intervallgrenzen: nicht existente Uhrzeiten auf den nächsten gültigen Zeitpunkt verschieben, doppelte Startgrenzen auf das frühere und doppelte Endgrenzen auf das spätere Vorkommen legen. Solche Grenzen benötigen eine Vorschau und eigene technische Prüffälle. Die Beispiele unten verwenden eindeutige Grenzen.

## Start, Stopp, Pause und Wiedereröffnung

| Ereignis | Erstreaktion | Lösung |
|---|---|---|
| Berechtigte neue Anfrage | Budget beginnt im zugeordneten Geschäftszeitkalender | Eigenes Budget beginnt zum selben Startzeitpunkt |
| Eingangsbestätigung, interne Notiz oder KI-Entwurf | Keine Erfüllung | Keine Erfüllung |
| Erste qualifizierte Kundenantwort des Supports | Stoppt bei nachweisbarer Veröffentlichung im Portal oder bestätigter SMTP-Annahme, je gewähltem Kanal | Läuft weiter, sofern nicht zugleich eine Lösung mitgeteilt wird |
| Wartet auf Kunde | **Bestätigt DEC-032:** läuft weiter, falls noch offen | **Bestätigt DEC-032:** pausiert |
| Kundenantwort oder explizite Fortsetzung | Keine Rücksetzung einer schon erfüllten Erstreaktion | Noch offenes Restbudget läuft ab Fortsetzung weiter |
| Wartet intern / auf Lieferant | Im Vorschlag keine Pause | Im Vorschlag keine Pause |
| Lösung fachlich erfasst und dem Kunden mitgeteilt | Gegebenenfalls ebenfalls erfüllt | Stoppt, wenn beide Voraussetzungen vorliegen |
| Bloßes Setzen auf „Gelöst“ bei noch ausstehender Mitteilung | Kein impliziter Erfolg | Kein impliziter Erfolg |
| Wiedereröffnung nach Lösung | Im Vorschlag neuer Bearbeitungszyklus mit neuem Budget | Neuer Zyklus; alter Ergebnis- und Verletzungsverlauf bleibt erhalten |
| Abbruch / ungültige Anfrage | Gesondert beendet mit Grund, kein erfolgreicher SLA-Abschluss | Gleiches Prinzip; Auswertungen unterscheiden Erfolg, Verletzung und Abbruch |

Der vorgeschlagene Start ist der erste dauerhaft registrierte Eingang im System; bei Portal/API die bestätigte Annahme, bei E-Mail die erste dauerhafte Erfassung durch den Connector. Der frei angebbare Date-Header ist keine Fristquelle. Später aus der Prüfwarteschlange als berechtigte Anfrage übernommene Nachrichten behalten diesen Eingang als Start; sie erhalten kein frisches Budget durch verspätete Zuordnung. Verzögerungen bis zum Mailabruf sind separat zu überwachen. Ob ein nachweisbarer früherer Eingang am Mailserver maßgeblich werden soll, bleibt vor Kundeneinsatz zu entscheiden.

Eine qualifizierte Antwort ist im Vorschlag ein tatsächlicher kundengerichteter Beitrag mit hilfreichem Inhalt oder konkreter Rückfrage. Eine automatische Empfangsbestätigung, eine interne Notiz oder ein unbestätigter Entwurf zählt nicht. Bei fehlgeschlagenem oder unklarem SMTP-Versand wird der Antwortzeitpunkt nicht als bewiesen eingetragen. Eine Veröffentlichung im für den Kunden zugänglichen Portal kann ihn unabhängig vom E-Mail-Benachrichtigungsstatus erfüllen. Der tatsächliche Kanal muss daher in der Aktion und im Audit eindeutig sein; ein Mailentwurf gilt nicht allein durch Speichern intern als Portalveröffentlichung.

„Wartet auf Kunde“ verlangt im Vorschlag eine begründete Rückfrage und eine berechtigte Statusaktion; Beginn und Ende der Pause werden protokolliert. Wird die Wartephase gesetzt, obwohl die erste qualifizierte Antwort noch nicht nachweisbar erfolgt ist, bleibt deren Uhr gemäß DEC-032 aktiv. Die Lösungsuhr beginnt nicht bei jeder Nachricht von vorn. Eine Supportantwort während der Wartephase hebt die Pause nicht automatisch auf; eine gültige Kundenantwort oder explizite Fortsetzung tut dies.

Während einer noch laufenden Kundenpause zeigt die Lösungsuhr ihr verbleibendes Budget und den Pausenbeginn. Eine endgültige neue Fälligkeit steht erst nach Fortsetzung fest; ein alter oder prognostizierter Termin wird nicht als weiterhin verbindlich ausgegeben. Die bloße Überschreitung des alten Termins während einer zuvor rechtzeitig begonnenen Pause löst keine neue Lösungsfristverletzung aus. Eine bereits vor der Pause bestehende Verletzung bleibt dagegen erhalten.

Bei Prioritätswechsel wird im Vorschlag das bereits verbrauchte Budget nicht gelöscht: neues Gesamtbudget minus bisherige anrechenbare Zeit ergibt das Restbudget. Bei negativem Rest wird die Frist spätestens im Moment der Neubewertung als verletzt markiert; frühere Regelstände werden nicht rückwirkend überschrieben. Bereits erfüllte Uhren werden nicht neu geöffnet. Berechtigte Wiedereröffnung eines gelösten Vorgangs ist eine eigene Aktion, nicht die Nebenwirkung jeder automatischen Mail.

## Berechenbare Beispiele

Grundlage, sofern nicht abweichend genannt: **Europe/Berlin, Montag bis Freitag 09:00–17:00, Erstreaktion 120 Minuten, Lösung 480 Minuten**, keine Antwort oder sonstiger Stopp im betrachteten Verlauf. Alle Daten sind im Jahr **2026**. Der geschlossene 21. Oktober in S-04 ist eine frei gewählte Betriebsruhe im Beispiel, keine Behauptung über einen gesetzlichen Feiertag.

| Fall | Eingang / Ereignis | Erwartetes Ergebnis |
|---|---|---|
| S-01 Normalfall | Mo 19.10., 10:30 | Erstreaktion Mo 12:30; Lösung Di 20.10., 10:30. Am Montag stehen noch 390 Geschäftsminuten zur Verfügung; 90 Minuten werden am Dienstag benötigt. |
| S-02 Wochenende | Fr 16.10., 16:30 | Erstreaktion Mo 19.10., 10:30; Lösung Mo 16:30. Freitag zählt 30 Minuten. |
| S-03 Außerhalb der Geschäftszeit | Fr 16.10., 18:00 | Zählung ab Mo 19.10., 09:00; Erstreaktion 11:00, Lösung 17:00. |
| S-04 Betriebsruhe / Feiertagsausnahme | Di 20.10., 16:00; Mi 21.10. vollständig geschlossen | Erstreaktion Do 22.10., 10:00; Lösung Do 16:00. Dienstag zählt 60 Minuten. |
| S-05 Wechsel zur Winterzeit übers Wochenende | Fr 23.10., 16:30 UTC+02:00 | Erstreaktion Mo 26.10., 10:30 UTC+01:00, entsprechend 09:30 UTC. Geschlossene Wochenendstunden zählen nicht. |
| S-06 Doppelte Stunde im Sonntagsdienst | Sonderkalender Sonntag 01:00–06:00; So 25.10., 01:30 UTC+02:00 | Nach 120 tatsächlichen Minuten: 02:30 UTC+01:00, also das zweite 02:30; 01:30 UTC. |
| S-07 Fehlende Stunde im Sonntagsdienst | Sonderkalender Sonntag 01:00–06:00; So 29.03., 01:30 UTC+01:00 | Nach 120 tatsächlichen Minuten: 04:30 UTC+02:00; 02:30 UTC. |
| S-08 Kundenpause | Mo 19.10., 10:00; Pause 11:00; Fortsetzung Di 20.10., 10:00 | 60 Lösungsminuten verbraucht, 420 verbleiben; Lösung Di 17:00. Eine noch offene Erstreaktion bleibt am Mo 12:00 fällig. |
| S-09 Höhere Priorität | Mo 19.10., 09:00; um 11:00 neue Budgets 60/240 Minuten | Offene Erstreaktion bei Wechsel verletzt; Lösung nach 120 verbrauchten und 120 verbleibenden Minuten um 13:00. Alte Bewertung bleibt nachvollziehbar. |
| S-10 Wiedereröffnung | Mo 19.10. um 11:00 gelöst und mitgeteilt; um 14:00 berechtigt wiedereröffnet | Neuer Zyklus: Erstreaktion Mo 16:00, Lösung Di 20.10., 14:00. Vorheriger Zyklus bleibt im Verlauf. |
| S-11 Genau an der Schließgrenze | Fr 16.10., 17:00 | Keine Freitagsminute mehr; Erstreaktion Mo 19.10., 11:00. Eine Antwort am Mo exakt 11:00 ist rechtzeitig. |
| S-12 Späte Zuordnung | Dauerhafter Eingang Di 20.10., 09:00; Freigabe aus Prüfwarteschlange um 12:00 | Erstreaktionsfrist war 11:00; ohne Antwort bereits verletzt. Lösung bleibt 17:00. Kein Neustart der Uhr bei Zuordnung. |
| S-13 Vorwarnung | Mo 19.10., 09:00; Warnung bei 80 % der 120 Erstreaktionsminuten | 96 Minuten ergeben 10:36. Fällig ist die Antwort um 11:00. |
| S-14 Mittagspause | Kalender 09:00–12:00 und 13:00–17:00; Mo 19.10., 11:30 | Erstreaktion um 14:30: 30 Minuten vormittags und 90 Minuten nach der Pause. |

Die Zeitpunkte und Offsets wurden mit einer separaten Hilfsrechnung auf Basis von Python `datetime`/`zoneinfo` überprüft, einschließlich 17 Terminberechnungen. Dies prüft die hier aufgeführten Rechenbeispiele, nicht eine bereits implementierte SLA-Engine. Die Grenzauflösungen für nicht existente oder doppelte Intervallanfänge sind noch zusätzlich technisch zu testen.

## Eskalation und Nachvollziehbarkeit

Vorschlag: Warnung bei 80 % und Fälligkeitseskalation bei 100 % des jeweiligen Budgets. Beispielempfänger sind zuständige Supportgruppe und anschließend Serviceleitung; weder Gruppen noch Prozentwerte sind feste Vorgaben. Eine Verletzung liegt bei ausbleibender Erfüllung nach dem Fristende vor. Eine genau am Ende registrierte Antwort wird bei der abschließenden Bewertung berücksichtigt.

Eine Eskalationsstufe wird pro Unternehmen, Ticket, Bearbeitungszyklus, Uhr und stabiler Stufenkennung höchstens einmal als Fachaktion erzeugt. Neuberechnung und Worker-Neustart erzeugen keine weitere Kopie derselben Stufe. Verpasst der Worker eine Schwelle, holt er nach Wiederanlauf die höchste aktuell relevante noch nicht ausgeführte Stufe nach und protokolliert übersprungene Warnungen. Das verhindert eine Flut veralteter Zwischenmeldungen. Aktuelle Empfängerrechte werden vor Versand erneut geprüft.

Benachrichtigung, Zuweisung und Statuswechsel sind getrennte Aktionen mit jeweils nötigen Rechten. Ein Eskalationsjob darf keine Kundenantwort erfinden, keine vertraulichen Felder veröffentlichen und kein Ticket nur zur Verbesserung einer Kennzahl lösen. Schlägt eine Aktion fehl, bleiben Fristverletzung, Fehler und nächste Bearbeitung sichtbar. Für SMTP gelten die beschriebenen Grenzen bei unklarem Versandstatus.

Für jede Frist sind Regel-/Kalenderversion, Start, verbrauchte Geschäftszeit, Pausen, Fälligkeit, Qualifikationsereignis und Neubewertungen nachvollziehbar. Audit-Daten selbst unterliegen Rechten und Aufbewahrung. Eine neue Kundenpause löscht keine bereits eingetretene Verletzung. Frist- und Versandstatus werden getrennt ausgewertet.

## Weitere fachliche Prüffälle

Diese Fälle sind spezifiziert, nicht als Anwendungstests ausgeführt.

| Fall | Versuch | Erwartetes Ergebnis |
|---|---|---|
| H-01 | Unbekannter Absender schreibt an Service. | Prüfwarteschlange, kein automatisches Ticket oder Portalrecht; berechtigter Support kann zuordnen. |
| H-02 | Unbekannter Absender zitiert eine fremde Ticketnummer und eine interne Antwort. | Keine Freigabe und kein Anhängen an das fremde Ticket durch den Betreff. |
| H-03 | Zwei Worker bearbeiten denselben Eingang. | Genau eine interne Ticket-/Beitragsaktion; bei konkurrierender Zuordnung nachvollziehbarer Konflikt. |
| H-04 | Eine automatische Antwort löst wiederum eine Antwort aus. | Schleife wird unterbrochen; keine Folge neuer fachlicher Vorgänge. |
| H-05 | Externer öffnet einen internen Anhang oder fremde Kundenanfrage. | Zugriff inklusive direktem Dateilink und Suchvorschau abgewiesen. |
| H-06 | Kundenzuordnung oder Rechte ändern sich zwischen Antwortentwurf und Versand. | Alte Bestätigung reicht für geänderte Zielgruppe nicht; kein unzulässiger Versand. |
| H-07 | KI bereitet Antwort vor, anschließend startet eine Benachrichtigungsregel. | KI-Entwurf wird nicht ohne ausdrückliche Bestätigung versandt. |
| H-08 | Verbindung bricht nach möglicher SMTP-Annahme ab. | Status unklar, Klärung erforderlich; kein behaupteter Zustellnachweis und keine blinde Wiederholung. |
| H-09 | Ticket wird auf „Gelöst“ gesetzt, aber Lösung wurde nicht kundenlesbar mitgeteilt. | Lösungsuhr wird nicht allein durch Statuswechsel erfüllt. |
| H-10 | Nach Ablauf der Frist startet eine Kundenpause. | Verletzung bleibt erhalten; Pause löscht sie nicht. |
| H-11 | Worker startet nach Warn- und Fälligkeitsschwelle neu. | Höchste relevante Stufe einmalig, keine Serie alter Warnmails. |
| H-12 | Kalender wird geändert, während ein Ticket läuft. | Gespeicherte Version bleibt gültig oder explizit protokollierte Neubewertung; keine stille Verschiebung. |

## KI-Nutzen und verbleibende Entscheidungen

KI kann Eingänge zusammenfassen, Zuordnung vorschlagen, Antwortentwürfe erstellen, Übergaben aufbereiten und Fristverläufe in Alltagssprache erklären. Sie entscheidet weder Kundenzugehörigkeit noch Fristberechnung oder Versandfreigabe. Vorschläge aus der Prüfwarteschlange verwenden nur für diesen Zweck freigegebene Daten; cloudseitige Verarbeitung wird nicht vorausgesetzt. DEC-034 bestätigt Zusammenfassung und Antwortentwurf für V1; DEC-035 verschiebt die direkte KI-Nutzung durch externe Portalnutzer auf später. Automatische Vorschlagserstellung und zusätzliche Anwendungsfälle bleiben offen. Der [KI-Entwurf](AI_USE_CASES.md) grenzt den bestätigten Umfang und vorgeschlagene Datenwege ab.

OPEN-008 ist durch die Kundenpause teilweise geklärt; konkretes Budget, Startmesspunkt, Antwortdefinition, Zuordnung, Wiedereröffnung, Prioritätswechsel und Eskalationswerte müssen noch bestätigt werden. OPEN-009 ist beim unbekannten Absender geklärt; Mailanbieter, Authentifizierung, Vertrauensprüfung, Anzahl der Postfächer, Details zur sicheren Übergabe/Verfall von Einmalpasswörtern und Grenzen für Anhänge bleiben offen. Der administrative Kontoanlageweg mit Pflichtwechsel ist durch DEC-053 bestätigt. TASK-010 ergänzt Betriebsüberwachung, Zeitquellen, tolerierte Jobverzögerung und Aufbewahrung.

Die fachliche Abnahme durch Holger und das unabhängige technische Review stehen aus. Es wurde kein Mailkonto verbunden, keine Nachricht versandt und keine Produktfunktion implementiert.
