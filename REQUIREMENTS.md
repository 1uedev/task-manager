# Zentrale Anforderungen an das Projektmanagementprodukt

Version 0.3 · Stand 13. Oktober 2026 · Entwurf zur fachlichen Prüfung

Das Produkt unterstützt DACH-Unternehmen mit 10 bis 100 Nutzern bei technischen Projekten, Softwareentwicklung und Kundenservice. Es wird als eigenständiges Produkt entwickelt, mit Easy8 beziehungsweise Easy Redmine als funktionalem Vergleich. Die erste verkaufbare Version verbindet Tickets, Zeiterfassung, E-Mail-Helpdesk, Kundenportal, Dokumentation, Anforderungen und Git-Verknüpfung. Moderne Bedienung, nachvollziehbare Berechtigungen und eine erweiterbare API gehören zum Kern.

Fachliche Abnahme: Holger Gerlach. Entwicklung später überwiegend durch Codex und Claude Code. Seit dem 13. Oktober 2026 sind zusätzlich die Einrichtung des GitHub-Repositories und der gemeinsamen KI-Arbeitsumgebung beauftragt. Die Produktimplementierung ist weiterhin nicht beauftragt.

## Verbindlichkeit und Leseschlüssel

- **B — bestätigt:** Der Kern der Anforderung wurde vom Auftraggeber vorgegeben oder ausdrücklich bestätigt. Die hier formulierten Einzelheiten der Abnahmekriterien sind ein prüfbarer Entwurf und noch keine erfolgte Abnahme.
- **V — Vorschlag:** Ergänzung zur Ausgestaltung. Eine Aufnahme in diese Datei macht sie nicht zu einer bestätigten Produktentscheidung.
- **V1:** Erste verkaufbare Version. **Früh danach:** zeitnahe Erweiterung ohne zugesagtes Datum. **Später:** spätere Ausbaustufe. **Offen:** Release noch festzulegen. **Planung:** vor Beginn der Implementierung zu bearbeiten.
- Ein bestätigter Funktionswunsch kann einen noch offenen Liefertermin haben. Diese beiden Zustände werden getrennt geführt.
- Jede REQ-ID bleibt dauerhaft erhalten. Bei Ablösung wird der Nachfolger vermerkt; IDs werden nicht neu vergeben. Aufgaben verweisen auf diese IDs.

Die fachlichen Anforderungen stehen ausschließlich hier. [DECISIONS.md](DECISIONS.md) führt Entscheidungen und offene Fragen, [ROADMAP.md](ROADMAP.md) die Reihenfolge, [TODO.md](TODO.md) die Arbeit und [DONE.md](DONE.md) abgenommene Ergebnisse. Eine erledigte Aufgabe bedeutet nicht automatisch, dass eine Produktfunktion implementiert ist.

## Produkt und erste Ausbaustufe

### REQ-PROD-001 Eigenständiges Produkt

**B · V1.** Das Produkt erhält eine eigene Codebasis und orientiert sich funktional an Easy8 beziehungsweise Easy Redmine. Die Umsetzung beginnt mit einem klar begrenzten Kern und wird schrittweise erweitert.

**Abnahme:** Die Funktionsübersicht unterscheidet V1, spätere Erweiterungen und offene Wünsche. Sie behauptet keine vollständige Funktionsgleichheit. Eine Nutzung vorhandener Bibliotheken wird separat hinsichtlich Eignung und Lizenz geprüft.

### REQ-PROD-002 Vollständiger Arbeitsablauf in Version 1

**B · V1.** Tickets, Zeiterfassung, E-Mail-Helpdesk, Kundenportal, Dokumentation, Anforderungsmanagement und Git-Verknüpfung sind gemeinsam Bestandteil der ersten verkaufbaren Version.

**Abnahme:** Eine Supportanfrage lässt sich erfassen, bearbeiten, mit Zeitbuchungen und Wissen verbinden und dem berechtigten Kunden anzeigen. Eine Anforderung lässt sich mit Umsetzungstickets und Git-Einträgen verknüpfen. Der Ablauf ist mit dokumentierten Beispieldaten fachlich prüfbar.

### REQ-PROD-003 Erweiterungen vorab planen

**B · Planung.** Vor der Implementierung wird ein abgestimmtes Funktionsinventar mit einer Roadmap erstellt. Spätere Module erhalten mindestens Zweck, Abgrenzung, Abhängigkeiten und ein Abnahmebeispiel; technische Detailplanung erfolgt rechtzeitig vor ihrer Umsetzung.

**Abnahme:** Jeder inventarisierte Funktionsbereich hat eine Release-Zuordnung oder einen ausdrücklich offenen Status. Die erste Roadmap ist bis zur Prüfung des vollständigen Inventars vorläufig.

### REQ-PROD-004 Moderne und messbar bessere Nutzung

**B · V1.** Bedienung und wahrgenommene Geschwindigkeit sollen gegenüber den Vergleichsprodukten verbessert werden.

**Abnahme:** Vor der Implementierung sind Referenzsystem, Version, typische Aufgaben und Messverfahren festgelegt. Die spätere Abnahme vergleicht unter nachvollziehbaren Bedingungen Bearbeitungsdauer, Bedienprobleme und Antwortzeiten. Konkrete Zielwerte sind noch offen; eine Überlegenheit wird vorher nicht behauptet.

## Unternehmen und Projekte

### REQ-ORG-001 Eine Installation pro Unternehmen

**B · V1.** Eine Installation bedient zunächst ein Unternehmen. Sie kann mehrere interne Projekte und Projekte mit externen Teilnehmern enthalten.

**Abnahme:** Projekte und ihre Mitglieder sind getrennt verwaltbar. Ein externer Nutzer ohne Projektzuordnung erhält keinen Zugriff auf dessen Daten.

### REQ-ORG-002 Spätere Mandantenfähigkeit vorbereiten

**B · Planung und später.** Mehrere Unternehmen innerhalb eines Betriebsmodells müssen später möglich sein. Die anfängliche Architektur berücksichtigt den Unternehmenskontext durchgängig, ohne V1 bereits als Mehrmandantenprodukt auszugeben.

**Abnahme:** Die Architektur beschreibt Unternehmensgrenzen für Daten, Dateien, Suche, Hintergrundaufgaben, Vorlagen, Authentifizierung, Integrationen und KI. Die konkrete Datenbank-Isolation ist als eigene Entscheidung dokumentiert. Ein Unternehmenskennzeichen allein gilt nicht als Nachweis der Isolation.

### REQ-ORG-003 Konfiguration pro Projekt

**B · V1.** Berechtigte Administratoren konfigurieren Tickettypen, Status, Übergänge, Pflichtfelder, eigene Felder, Formulare, Automationen und Berechtigungen pro Projekt.

**Abnahme:** Zwei Projekte können unterschiedliche Regeln verwenden. Ungültige Übergänge oder fehlende Pflichtangaben werden auch über die API abgewiesen. Die erlaubte Automationssprache und die Behandlung bereits vorhandener Datensätze sind vor Umsetzung zu spezifizieren.

### REQ-ORG-004 Projektvorlagen und Projektlebenszyklus

**V · V1.** Wiederverwendbare Projektvorlagen sowie Archivieren und Wiederöffnen sollen die Einrichtung und Pflege vereinfachen.

**Abnahme:** Vorlagen übernehmen nur ausdrücklich ausgewählte Einstellungen. Archivierung erhält Historie und Verknüpfungen. Auswirkungen auf neue E-Mails, Zeitbuchungen und KI-Vorschläge sind festgelegt.

## Benutzergruppen und Berechtigungen

### REQ-ACL-001 Frei definierbare Benutzergruppen

**B · V1.** Kundenadministratoren legen Benutzergruppen an und weisen ihnen Berechtigungen zu. Gruppen können in unterschiedlichen Projekten unterschiedliche Rechte erhalten.

**Abnahme:** Ein Administrator kann eine interne Bearbeitergruppe und eine externe Kundengruppe mit verschiedenen Projektberechtigungen einrichten. Das Zusammenwirken mehrerer Gruppen ist dokumentiert und anhand von Konfliktfällen geprüft.

### REQ-ACL-002 Externe Teilnehmer

**B · V1.** Externe Nutzer erhalten nur die ausdrücklich zugewiesenen Projekt- und Funktionsrechte. Standardmäßig sehen sie alle Anfragen ihrer Kundenorganisation innerhalb der ihnen freigegebenen Projekte. Administratoren können zusätzliche Rechte gezielt vergeben (DEC-027).

**Abnahme:** Zwei externe Nutzer derselben Kundenorganisation sehen gegenseitig deren Anfragen im gemeinsam freigegebenen Projekt. Anfragen anderer Kundenorganisationen und nicht zugewiesener Projekte bleiben ohne zusätzliche Freigabe unsichtbar. Die Organisationssicht erweitert keine Rechte auf interne Kommentare, Felder oder Anhänge. Welche zusätzlichen Sichtbereiche V1 anbietet, bleibt offen. Negative Zugriffstests decken fremde Ticketnummern und direkte Dateilinks ab.

### REQ-ACL-003 Feldberechtigungen

**B · V1.** Einzelne Felder lassen sich für bestimmte Gruppen verbergen. Sichtbarkeit und Änderbarkeit werden getrennt behandelt. Wenn anwendbare Gruppenrechte ein Feld freigeben und ausdrücklich verbieten, hat das ausdrückliche Verbot Vorrang (DEC-028).

**Abnahme:** Ein verbotenes Feld wird auch bei einer Freigabe durch eine andere anwendbare Gruppe nicht offengelegt. Lesen und Schreiben werden getrennt geprüft. Ein verborgenes Feld wird weder als Wert noch indirekt über Suche, Filter, API, Historie, Export, Benachrichtigung oder KI-Ausgabe offengelegt. Pflichtfeldregeln dürfen Nutzer nicht zum Ausfüllen eines für sie unzugänglichen Felds zwingen; das Konfliktverhalten ist festgelegt.

### REQ-ACL-004 Einheitliche Durchsetzung

**V · V1.** Das Backend setzt Rechte zentral durch. Die Oberfläche dient der verständlichen Darstellung dieser Rechte.

**Abnahme:** Derselbe Zugriff wird über Oberfläche, API, Suche und Hintergrundprozess konsistent bewertet. Rechteänderungen wirken auch auf neue Zugriffe auf zwischengespeicherte Inhalte. Eine Gruppenmitgliedschaft berechtigt nicht zur eigenständigen Rechteerweiterung.

### REQ-ACL-005 Nachvollziehbare Verwaltung

**V · V1.** Änderungen an Benutzern, Gruppen, Rechten und kritischen Projekteinstellungen werden protokolliert.

**Abnahme:** Berechtigte Personen erkennen Urheber, Zeitpunkt, Gegenstand und Änderung. Protokolle enthalten keine Passwörter oder API-Schlüssel. Sichtbarkeit und Aufbewahrungsfrist werden vor Umsetzung festgelegt.

## Anmeldung

### REQ-AUTH-001 Interne Konten

**B · V1.** Anmeldung mit internem Benutzernamen und Passwort wird unterstützt.

**Abnahme:** Anmeldung, Abmeldung, Deaktivierung und Passwortwiederherstellung sind spezifiziert und prüfbar. Nach Deaktivierung bleiben keine unzulässigen aktiven Sitzungen bestehen. Details zu Sitzungsdauer und Passwortrichtlinien sind offen.

### REQ-AUTH-002 LDAP

**B · V1.** Alternativ zur internen Anmeldung wird LDAP unterstützt.

**Abnahme:** Ein Kunde kann einen LDAP-Dienst konfigurieren und die Verbindung prüfen. Kontenzuordnung, eindeutige Identitäten, Deaktivierung, Gruppenzuordnung und Verhalten bei LDAP-Ausfall sind dokumentiert. Eine gleichnamige lokale Identität darf nicht versehentlich das LDAP-Konto übernehmen.

### REQ-AUTH-003 Erweiterbare Anmeldeverfahren

**B · V1 Architektur.** Authentifizierung ist so gekapselt, dass später zusätzliche Verfahren eingebunden werden können.

**Abnahme:** Neue Anmeldeanbieter erfordern keine Neuentwicklung der Fachmodule. Interne Benutzeridentität, Berechtigungen und Anmeldeanbieter sind getrennt beschrieben. Konkrete spätere SSO-Verfahren sind noch nicht zugesagt.

### REQ-AUTH-004 Zweiter Faktor

**B · Früh danach.** Zwei-Faktor-Authentifizierung folgt zeitnah nach Version 1 und gehört nicht zum ersten verkaufbaren Release (DEC-029). Die Anmeldearchitektur berücksichtigt die spätere Ergänzung.

**Abnahme:** Vor dem 2FA-Release sind Verfahren, Wiederherstellung, mögliche Erzwingung und Zusammenspiel mit LDAP entschieden. Verlust des zweiten Faktors und Administratorkonten sind als Abnahmefälle vorgesehen. V1 wird nicht als bereits mit 2FA ausgestattet beschrieben; ein konkretes Datum für das zeitnahe Folgerelease ist noch offen.

## Tickets und Zusammenarbeit

### REQ-TKT-001 Ticketbearbeitung

**B · V1.** Tickets bilden den zentralen Arbeitsgegenstand und berücksichtigen projektspezifische Typen, Felder und Workflows.

**Abnahme:** Ein Ticket kann angelegt, zugewiesen, bearbeitet und innerhalb des erlaubten Workflows abgeschlossen werden. Pflichtfelder, Rechte und Historie bleiben nachvollziehbar. Die endgültige Liste der Standardfelder wird im Fachmodell festgelegt.

### REQ-TKT-002 Beziehungen und Hierarchien

**V · V1.** Tickets können miteinander und mit Anforderungen, Wiki-Seiten, Zeitbuchungen sowie Git-Einträgen verknüpft werden. Unteraufgaben und Abhängigkeiten werden als getrennte Beziehungstypen geplant.

**Abnahme:** Beziehungen sind von beiden Seiten aus erreichbar, soweit der Nutzer die Ziele sehen darf. Unzulässige Zyklen und das Verhalten bei Archivierung sind festgelegt. Verborgene Ziele verraten keine Titel oder Inhalte.

### REQ-TKT-003 Kommentare und Anhänge

**V · V1.** Tickets unterstützen Kommentare und Dateien. Interne Notizen und kundenlesbare Kommunikation sind klar unterscheidbar.

**Abnahme:** Vor dem Speichern ist die Zielgruppe erkennbar. Externe Nutzer sehen keine internen Notizen oder deren Anhänge. Dateigrößen, zulässige Inhalte und der Umgang mit potenziell schädlichen Dateien sind festgelegt.

### REQ-TKT-004 Finden und Bearbeiten

**V · V1.** Tickets bieten Suche, Filter, Sortierung, gespeicherte Ansichten und eine verständliche Listenansicht. Weitere Ansichten werden separat priorisiert.

**Abnahme:** Nutzer finden eigene offene Vorgänge und überfällige Vorgänge eines berechtigten Projekts. Zähler, Vorschautexte und Suchvorschläge berücksichtigen dieselben Rechte wie die Treffer.

### REQ-TKT-005 Änderungen und Benachrichtigungen

**V · V1.** Fachlich relevante Änderungen werden historisiert. Benachrichtigungen richten sich nach Empfängerrechten und konfigurierten Ereignissen.

**Abnahme:** Die Historie zeigt Urheber und Zeitpunkt. Benachrichtigungen werden nicht mehrfach durch denselben internen Wiederholungsversuch versandt. Gleichzeitige Änderungen werden erkannt; stille Datenverluste sind ausgeschlossen.

## Zeiterfassung

### REQ-TIME-001 Zeit auf Arbeit buchen

**B · V1.** Benutzer erfassen Arbeitszeiten im Zusammenhang mit Projekten und Tickets.

**Abnahme:** Datum, Dauer und Beschreibung lassen sich erfassen und durch berechtigte Nutzer korrigieren. Die Regeln für Buchungen auf abgeschlossene Vorgänge und für fremde Buchungen sind definiert. Zeitzonen verändern eine erfasste Dauer nicht.

### REQ-TIME-002 Auswerten und weitergeben

**V · V1.** Zeitbuchungen lassen sich nach Projekt, Person und Zeitraum auswerten und in einem festgelegten Format exportieren.

**Abnahme:** Summen stimmen mit den zugrunde liegenden Buchungen überein. Rechte auf Personendaten gelten auch für Summen und Exporte. Rundung, Einheiten und Korrekturen sind nachvollziehbar.

### REQ-TIME-003 Erweiterungen der Zeiterfassung

**V · Später oder gesondert priorisieren.** Timer, Freigaben, abrechenbare Zeiten, Stundensätze und Abrechnung werden als separate Erweiterungen behandelt.

**Abnahme:** Die Roadmap grenzt diese Funktionen von der bestätigten Basis-Zeiterfassung ab. Keine davon wird stillschweigend als vorhanden oder für V1 zugesagt behandelt.

## E Mail Helpdesk und Servicefristen

### REQ-HD-001 IMAP und SMTP

**B · V1.** Der Helpdesk empfängt E-Mails über IMAP und versendet sie über SMTP. Eine spezielle Microsoft-365-Anbindung ist für V1 nicht erforderlich.

**Abnahme:** Ein konfiguriertes Postfach erzeugt nachvollziehbar ein Ticket; berechtigte Antworten werden über SMTP versandt. Zugangsdaten bleiben geschützt. Verbindungsfehler und ausstehende Sendungen sind für zuständige Personen erkennbar.

### REQ-HD-002 Zuordnung und Wiederholungen

**V · V1.** Antworten werden bestehenden Konversationen zugeordnet. Mehrfach zugestellte Nachrichten, automatische Antworten und Wiederholungen bei Fehlern werden kontrolliert behandelt.

**Abnahme:** Die erneute Verarbeitung derselben Nachricht erzeugt kein zweites Ticket. Eine Absenderadresse allein gewährt keinen Zugriff auf ein beliebiges Ticket. Regeln für Weiterleitungen, unbekannte Absender, Anhänge und Mail-Schleifen sind spezifiziert.

### REQ-HD-003 SLA Fristen

**B · V1.** Der Helpdesk unterstützt SLA-Fristen.

**Abnahme:** Administratoren können die vereinbarten Fristtypen und Zuordnungsregeln einstellen. Vorschlag für den Mindestumfang: erste Antwort und Lösung. Start, Stopp, Pausen, Wiedereröffnung und Wechsel der Priorität sind vor Umsetzung fachlich definiert und anhand von Beispielen berechenbar.

### REQ-HD-004 Geschäftszeiten

**B · V1.** Geschäftszeiten beeinflussen die Berechnung von Servicefristen.

**Abnahme:** Die Behandlung von Wochenenden, Feiertagen, Kalenderzeitzone und Zeitumstellung ist festgelegt. Ein Freitagabend eingehendes Ticket erhält abhängig vom zugeordneten Kalender eine nachvollziehbare Fälligkeit. Anzahl und Zuordnung verschiedener Kalender sind offen.

### REQ-HD-005 Eskalationen

**B · V1.** Das System eskaliert drohende oder eingetretene Fristverletzungen nach konfigurierbaren Regeln.

**Abnahme:** Schwellen, Empfänger, Kanäle und zulässige Aktionen sind festgelegt. Neustart oder Wiederholung eines Hintergrundjobs erzeugt keine unkontrollierten Mehrfachaktionen. Berechnungen und Auslöser sind deterministisch; KI entscheidet nicht über Fristberechnung.

## Kundenportal

### REQ-PORTAL-001 Kundenanfragen

**B · V1.** Externe Benutzer können im Kundenportal die für sie freigegebenen Anfragen bearbeiten und ihren Stand nachvollziehen.

**Abnahme:** Der abgestimmte Ablauf umfasst Anlegen, Anzeigen und Antworten. Die Sicht auf eigene, organisationsbezogene oder projektbezogene Anfragen folgt REQ-ACL-002. Portal und Hauptanwendung verwenden dieselben fachlichen Daten und Rechte.

### REQ-PORTAL-002 Freigegebenes Wissen

**V · V1.** Freigegebene Dokumentation soll im Portal zugänglich sein.

**Abnahme:** Eine für interne Nutzer sichtbare Seite wird nicht allein durch ihre Verknüpfung mit einem Kundenticket öffentlich. Suchergebnisse, Anhänge und Übersetzungen folgen der Freigabe der jeweiligen Inhalte.

### REQ-PORTAL-003 Zugang und Einladung

**V · V1.** Einladung, Kontowiederherstellung und Verwaltung externer Teilnehmer werden ausdrücklich spezifiziert.

**Abnahme:** Eine Einladung legt die zulässige Zugehörigkeit fest und läuft kontrolliert ab. Selbstregistrierung, anonyme Anfragen und Zuordnung unbekannter E-Mail-Absender bleiben bis zur Entscheidung deaktivierte oder nicht zugesagte Varianten.

## Dokumentation und Wiki

### REQ-DOC-001 Moderne Textbearbeitung

**B · V1.** Das Wiki bietet moderne visuelle Textbearbeitung.

**Abnahme:** Der abgestimmte Mindestumfang wird mit realen Dokumentationsseiten geprüft. Vorgeschlagen sind Überschriften, Listen, Tabellen, Links, Bilder und Codeblöcke. Inhalte bleiben nach Speichern und erneutem Bearbeiten strukturell erhalten. Import- und Exportformate sind noch offen.

### REQ-DOC-002 Versionen und Bearbeitungskonflikte

**B · V1.** Gleichzeitige gemeinsame Echtzeitbearbeitung ist nicht erforderlich. Änderungen müssen nachvollziehbar bleiben und dürfen sich nicht unbemerkt überschreiben.

**Abnahme:** Ein Nutzer erkennt einen Konflikt mit einer neueren gespeicherten Fassung und kann seine Arbeit erhalten. Ob Bearbeitungssperren, Versionsprüfung oder eine Kombination verwendet wird, bleibt eine Architekturentscheidung.

### REQ-DOC-003 Optionale Sprachfassungen

**B · V1.** Eine Wiki-Seite kann eine oder mehrere Sprachfassungen besitzen. Übersetzungen sind freiwillig.

**Abnahme:** Eine ausschließlich deutsche Seite ist vollständig nutzbar. Eine englische Fassung kann derselben logischen Seite zugeordnet werden. Fehlende Übersetzungen werden verständlich angezeigt. Automatische Übersetzungen werden nur nach Bestätigung übernommen.

### REQ-DOC-004 Wissensstruktur und Auffindbarkeit

**V · V1.** Seiten werden geordnet, gesucht und mit anderen Arbeitsgegenständen verknüpft. Historie und Sprachfassungen bleiben auffindbar.

**Abnahme:** Ein Nutzer kann eine Seite ihrem Projekt oder Wissensbereich zuordnen und berechtigte Inhalte über die Suche erreichen. Verschieben, Umbenennen und Wiederherstellen erhalten gültige Verknüpfungen nach dokumentierten Regeln.

## Anforderungsmanagement im Produkt

### REQ-REQ-001 Strukturierte Anforderungen

**B · V1.** Eine Anforderung enthält Beschreibung, Verantwortliche, Priorität, Status und konkrete Abnahmekriterien.

**Abnahme:** Die Beispielanforderung „Kunden sehen den Stand ihrer freigegebenen Supportanfragen“ lässt sich mit überprüfbaren Kriterien erfassen. Administrierbare Felder und Rechte funktionieren auch für Anforderungen.

### REQ-REQ-002 Einfacher Freigabestatus und Historie

**B · V1.** Anforderungen besitzen einen einfachen Freigabestatus und eine nachvollziehbare Änderungshistorie. Wird eine freigegebene Anforderung inhaltlich geändert, entsteht eine neue Revision zur erneuten Freigabe. Die bisherige Freigabe bleibt historisch erhalten (DEC-030).

**Abnahme:** Berechtigte Personen können eine Anforderung freigeben. Sichtbar bleiben freigebende Person, Zeitpunkt und die freigegebene Fassung. Neue Abnahmekriterien erzeugen eine ungeprüfte Revision; die vorherige Freigabe wird weder überschrieben noch auf den neuen Inhalt übertragen. Welche rein organisatorischen Änderungen keine neue Fachrevision benötigen, wird vor Umsetzung festgelegt.

### REQ-REQ-003 Nachvollziehbare Umsetzung

**B · V1.** Anforderungen werden mit Umsetzungstickets, Dokumentation und Git-Einträgen verknüpft.

**Abnahme:** Von einer Anforderung aus sind ihre berechtigten Umsetzungsvorgänge erreichbar. Eine Verknüpfung allein erklärt eine Anforderung noch nicht für erfüllt. Fachliche Abnahme und technischer Bearbeitungsstatus bleiben unterscheidbar.

### REQ-REQ-004 Formale Anforderungsstände

**B · Später.** Verbindlich eingefrorene Stände mehrerer Anforderungen und formale Änderungsanträge werden später ergänzt.

**Abnahme:** Die spätere Spezifikation unterscheidet unveränderliche Freigabestände, nachfolgende Änderungen und deren Genehmigung. V1 wird nicht als vollständiges formales Änderungsmanagement ausgewiesen.

## Git Verknüpfung

### REQ-GIT-001 GitHub und GitLab

**B · V1.** GitHub und GitLab werden für Zuordnung und Anzeige unterstützt.

**Abnahme:** Berechtigte Nutzer können die vereinbarten Git-Objekte mit Tickets und Anforderungen verknüpfen und deren Informationen anzeigen. Vorschlag: Repository, Commit und Pull beziehungsweise Merge Request. Unterstützung kundeneigener GitLab- oder GitHub-Server bleibt gesondert zu entscheiden.

### REQ-GIT-002 Anzeige ohne Schreibaktionen

**B · V1.** In V1 reichen Zuordnung und Anzeige. Änderungen an Quellcode, Branches und Pull beziehungsweise Merge Requests sind nicht Teil des bestätigten Mindestumfangs.

**Abnahme:** Die Integration benötigt für diesen Funktionsumfang keine Git-Schreibrechte. Ein Nutzer erhält durch eine lokale Verknüpfung nicht automatisch Zugang zu vertraulichen Repository-Inhalten. Aktualisierung und Verhalten bei entzogenem Zugriff sind definiert.

### REQ-GIT-003 Erweiterbare Integration

**B · V1 Architektur und später.** Weitere Git-Funktionen sollen später ergänzt werden können.

**Abnahme:** Anbieterzugang, Zuordnung, Ereignisverarbeitung und fachliche Aktionen sind getrennt beschrieben. Spätere Schreibaktionen benötigen eigene Berechtigungen, Abnahmekriterien und bei KI-Auslösung die vorgesehene Bestätigung.

## Sprachen und Erscheinungsbild

### REQ-I18N-001 Deutsch und Englisch

**B · V1.** Anwendung und Kundenportal unterstützen Deutsch und Englisch. Systemmeldungen, Fehler, Anmeldung und E-Mails sind einbezogen.

**Abnahme:** Für beide Sprachen lässt sich derselbe zentrale Arbeitsablauf durchführen. Fehlende Übersetzungen folgen einer definierten Rückfallregel. Sprachabhängige Texte sind von der Fachlogik getrennt.

### REQ-I18N-002 Erweiterbare Sprachpakete

**B · V1 Architektur.** Weitere Sprachen lassen sich ohne Umbau der Fachmodule ergänzen. Kunden können Bezeichnungen konfigurierbarer Felder, Status und Typen übersetzen.

**Abnahme:** Eine zusätzliche Testsprache wird über den vorgesehenen Übersetzungsweg ergänzt. Interne Kennungen bleiben unverändert. Längere Texte und unterschiedliche Mehrzahlformen beschädigen keine wesentlichen Bedienabläufe.

### REQ-I18N-003 Regionale Darstellung

**B · V1.** Datum, Zahlen und Zeiten werden passend zur Benutzereinstellung dargestellt.

**Abnahme:** Sprache, Formatierung und Zeitzone sind nachvollziehbar festgelegt. Datenaustausch und gespeicherte Zeitpunkte bleiben eindeutig. Dieselbe Zeitbuchung hat in deutscher und englischer Darstellung denselben fachlichen Wert.

### REQ-BRAND-001 Unternehmensauftritt

**B · V1.** Administratoren konfigurieren Logo und Farben für Anwendung, Kundenportal, Anmeldeseite und E-Mails.

**Abnahme:** Eine Änderung wirkt auf die genannten Bereiche und lässt sich zurücksetzen. Der Auftritt gehört zum Unternehmenskontext und unterstützt spätere Mandantenfähigkeit. Beliebiger ausführbarer Kundencode ist daraus nicht abgeleitet.

### REQ-BRAND-002 Vorlagen und spätere PDF Ausgaben

**B · V1 E-Mails und später PDFs.** E-Mails und später erzeugte PDFs basieren auf Vorlagen und berücksichtigen Unternehmensauftritt und Sprache.

**Abnahme:** Eine Vorlage zeigt eine Vorschau mit Beispieldaten. Verfügbare Platzhalter, Rückfallvorlagen und Übersetzungen sind festgelegt. Das Bearbeiten einer Vorlage verleiht keinen Zugriff auf verborgene Daten.

### REQ-UX-001 Verständliche und zugängliche Bedienung

**V · V1.** Formulare, Navigation, Fehlermeldungen und Bestätigungen sollen mit Maus und Tastatur verständlich bedienbar sein. Farben allein tragen keine notwendige Information.

**Abnahme:** Repräsentative Abläufe werden auf Fokusführung, Beschriftungen, Kontrast und Fehlerkorrektur geprüft. Verbindlicher Barrierefreiheitsstandard, unterstützte Browser und mobile Mindestansichten sind noch festzulegen.

## KI Funktionen im Produkt

### REQ-AI-001 Nutzen pro Anwendungsfall planen

**B · Planung.** Für jeden fachlichen Anwendungsfall wird eine sinnvolle KI-Unterstützung bewertet. Eine Bewertung darf ergeben, dass deterministische Software oder manuelle Bearbeitung geeigneter ist.

**Abnahme:** Ein KI-Verzeichnis beschreibt Zweck, Eingaben, Datenumfang, Rechte, Ausgabe, Bestätigung und Qualitätsprüfung. Es umfasst mindestens die unten aufgeführten Anwendungsfälle. Die konkrete Lieferreihenfolge ist separat zu entscheiden.

### REQ-AI-002 Vom Administrator gepflegte Skills

**B · Liefertermin offen.** Administratoren hinterlegen Markdown-Anweisungen je Thema oder Anwendungsfall.

**Abnahme:** Ein Administrator kann einen Skill erstellen, bearbeiten und einem Anwendungsfall zuordnen. Ein Skill enthält Textanweisungen; daraus entsteht keine Berechtigung, beliebigen serverseitigen Code auszuführen. Fachliche Rechte bleiben unverändert.

### REQ-AI-003 Anbieter und Modelle konfigurieren

**B · Liefertermin offen, Architektur von Anfang an.** Die Anbindung unterstützt OpenAI, Anthropic beziehungsweise Claude und Ollama. Nur Administratoren verwalten Anbieter, Endpunkte, Zugangsdaten, Modelle und ihre Zuordnung.

**Abnahme:** Die Architektur enthält eine gemeinsame Anbindung mit expliziten Fähigkeiten pro Anbieter und Modell. Nutzer starten einen fachlichen Anwendungsfall ohne Modellwahl. Ein nicht geeigneter Modelltyp führt zu einer verständlichen Meldung statt zu einer unkontrollierten Ausführung. Keine konkrete Modellversion ist festgeschrieben.

### REQ-AI-004 Vorschläge und ausdrückliche Bestätigung

**B · Für jede ausgelieferte KI-Funktion.** KI liefert Vorschläge. Änderungen und ausgehende Nachrichten benötigen eine ausdrückliche Bestätigung durch eine dazu berechtigte Person.

**Abnahme:** Vor Bestätigung sind Ziel, Datenänderung beziehungsweise Empfänger und Nachricht sichtbar. Vorschläge sind verwerfbar und soweit sinnvoll bearbeitbar. Ein neuer oder veränderter Vorschlag benötigt eine neue Bestätigung. Sammelbestätigung ist nur für eine vollständig sichtbare, abgegrenzte Liste zulässig.

### REQ-AI-005 Rechte und Quellen

**V · Ab erster KI-Funktion.** KI verwendet nur Daten, die der ausführenden Identität für den betreffenden Anwendungsfall zugänglich sind. Quellen werden bei wissensbasierten Antworten angegeben.

**Abnahme:** Verborgene Felder, interne Notizen und fremde Projekte erscheinen weder im Modellkontext noch in Ausgabe oder Quellenverweisen. Suchindex, Zwischenspeicher und frühere Zusammenfassungen beachten entzogene Rechte. Unzureichende Quellen werden erkennbar behandelt.

### REQ-AI-006 Kontrollierte Ausführung

**V · Ab erster KI-Funktion.** Die Anwendung prüft KI-Ausgaben und führt zulässige Änderungen über ihre normale Fachlogik aus.

**Abnahme:** Unmittelbar vor Ausführung werden Rechte, Datenstand und Gültigkeit des Vorschlags erneut geprüft. Konflikte erzeugen eine erneute Prüfung statt stiller Überschreibung. Anweisungen in eingelesenen E-Mails oder Dokumenten können keine Freigaben und Rechte erweitern.

### REQ-AI-007 Datenwege und Ausfälle

**V · Ab erster KI-Funktion.** Administratoren legen erlaubte Datenwege und gegebenenfalls Ersatzmodelle fest. Die Wahl eines lokalen Dienstes erlaubt keinen stillen Wechsel zu einem Cloud-Anbieter.

**Abnahme:** Anbieterwechsel, Zeitüberschreitungen und Fehler folgen der Konfiguration. Ticketbearbeitung und andere Kernfunktionen bleiben bei KI-Ausfall nutzbar. Ein Ollama-Endpunkt wird nach seiner tatsächlichen Betriebsart bewertet und nicht pauschal als lokal angenommen.

### REQ-AI-008 Versionen und Nachvollziehbarkeit

**V · Ab erster KI-Funktion.** Skills erhalten Versionen und eine Testmöglichkeit. KI-Aktionen protokollieren verantwortlichen Nutzer, Skill-Version, Anbieter, Modell und Ausführungsergebnis.

**Abnahme:** Ein Vorschlag lässt sich der tatsächlich verwendeten Konfiguration zuordnen. Vollständige Inhalte werden nicht allein für dieses Audit ungeprüft dauerhaft gespeichert. Inhaltsprotokollierung, Löschung, Laufzeit- und Kostenlimits sind vor Umsetzung festgelegt.

### REQ-AI-009 Prüffälle je Anwendungsfall

**V · Ab erster KI-Funktion.** Qualität wird je Anwendungsfall mit fachlichen Beispielen und Fehlerfällen geprüft.

**Abnahme:** Tests umfassen hilfreiche Antworten, fehlende Quellen, unzulässige Datenzugriffe, manipulierte Dokumentanweisungen und gescheiterte Modellaufrufe. Rechenwerte, Zeiterfassungsdauern und Berechtigungen werden nicht durch freie Modellantworten verbindlich festgelegt.

### Geplante KI Anwendungsfälle

Die folgende Tabelle ist ein Vorschlag zur fachlichen Ausgestaltung von REQ-AI-001. Sie ist keine bereits freigegebene Lieferliste für V1.

| Anwendungsfall | Sinnvolle Unterstützung | Bestätigung und fachliche Grenze |
|---|---|---|
| Tickets | Beschreibung strukturieren, fehlende Angaben finden, Priorität und Zuordnung vorschlagen, ähnliche Vorgänge anzeigen | Änderungen erst nach Bestätigung; Ähnlichkeit ersetzt keine fachliche Entscheidung |
| Zeitbuchungen | Tätigkeitsbeschreibungen verbessern und vorhandene Buchungen zusammenfassen | Keine erfundenen Dauern; Buchungsänderungen bestätigen |
| Helpdesk | Anfrage zusammenfassen, Antwort entwerfen, Wissen und Übergaben vorbereiten | Empfänger und Antwort vor Versand prüfen |
| Kundenportal | Aus freigegebenem Wissen antworten, Anfrage vorbereiten | Quellen und Grenzen sichtbar; Ticketanlage bestätigen; Verfügbarkeit für Externe offen |
| Wiki | Gliedern, überarbeiten, Artikel aus gelösten Fällen entwerfen | Übernahme und Veröffentlichung bestätigen |
| Mehrsprachige Inhalte | Übersetzungen entwerfen und auf veraltete Fassungen hinweisen | Sprachfassung gezielt bestätigen |
| Anforderungen | Unklarheiten, Widersprüche und fehlende Kriterien finden; Umsetzungsschritte vorschlagen | Fachliche Freigabe bleibt beim berechtigten Menschen |
| Git | Verknüpfte Änderungen erklären, Zusammenfassungen und Versionshinweise entwerfen | Nur zugängliche Git-Daten; keine ungeplanten Schreibaktionen |
| Projektstatus | Nachvollziehbare Statusberichte, Hindernisse und Entscheidungen vorbereiten | Quellen angeben; Aussagen vor Übernahme prüfen |
| Suche | Berechtigte Inhalte finden und mit Quellen zusammenfassen | Rechte auch für indirekte Zusammenfassungen beachten |
| Administration | Workflows, Projektkonfiguration und Auswirkungen von Rechteänderungen erklären oder vorschlagen | Anwendung nur durch berechtigten Administrator nach Bestätigung |
| Spätere Module | Ressourcenplanung, Budgethinweise, Tests und Dokumentausgaben unterstützen | Pro Modul eigene Bewertung und Abnahme; Berechnungen bleiben prüfbar |

## API und technische Erweiterbarkeit

### REQ-API-001 API von Anfang an

**B · V1.** Eine dokumentierte API gehört von Beginn an zum Produkt.

**Abnahme:** Die vereinbarten fachlichen Kernabläufe sind über die API ausführbar und unterliegen denselben Rechten und Regeln wie die Oberfläche. Versionierung, Authentifizierung, Seitennavigation, Fehlerformat und Kompatibilitätsregeln sind vor Implementierung festgelegt.

### REQ-API-002 Integrationen erweitern

**V · V1 Architektur.** E-Mail, Git, Anmeldeanbieter und KI-Anbieter werden über abgegrenzte Schnittstellen angebunden. Erweiterbarkeit setzt nicht automatisch ein beliebiges Plugin-System voraus.

**Abnahme:** Jede Schnittstelle beschreibt Ein- und Ausgaben, Rechte, Fehlerfälle und Wiederholungsverhalten. Eine zusätzliche Anbieteranbindung erfordert keine Änderung aller Fachmodule.

### REQ-ARCH-001 Frontend und Backend

**B · Planung.** React ist als Frontend vorgesehen. Die Entscheidung zwischen Go und Java als Backend ist offen.

**Abnahme:** Vor Implementierung ist eine begründete Backend-Entscheidung festgehalten. TypeScript, Java mit Spring Boot und ein modularer Monolith sind derzeit Vorschläge, keine bestätigten Festlegungen.

### REQ-ARCH-002 Prüffähige Modulgrenzen

**V · V1 Architektur.** Fachbereiche besitzen klare Zuständigkeiten und möglichst wenige Abhängigkeiten. Ein modularer Monolith wird als Ausgangspunkt geprüft.

**Abnahme:** Das Architekturkonzept zeigt Datenverantwortung, öffentliche Modulschnittstellen und erlaubte Abhängigkeiten. Es begründet zusätzliche Betriebsdienste. Die Wahl mehrerer KI-Entwickler begründet für sich keine Microservice-Architektur.

## Datenbank und Dateien

### REQ-DATA-001 PostgreSQL

**B · V1.** PostgreSQL ist die anfangs einzige unterstützte Datenbank.

**Abnahme:** Installation, Änderungen am Schema, Sicherung und Wiederherstellung sind für die freigegebenen PostgreSQL-Versionen beschrieben und geprüft. Die konkrete Versionsspanne wird vor Implementierung entschieden.

### REQ-DATA-002 Weitere Datenbanken vorbereiten

**B · V1 Architektur.** MariaDB, Microsoft SQL Server und Oracle werden nur architektonisch vorbereitet. V1 enthält dafür keine Implementierung, Zertifizierung oder Unterstützungszusage.

**Abnahme:** Datenzugriff, Migrationen und datenbankspezifische Funktionen sind im Konzept auffindbar abgegrenzt. Die Vorbereitung bedeutet keine Zusage, dass später lediglich ein Treiberwechsel genügt. Aufwand und Abnahmetests jeder weiteren Datenbank werden separat geplant.

### REQ-DATA-003 Dauerhafte Dateien

**V · V1.** Anhänge, Bilder, Exporte und gegebenenfalls Suchdaten sind unabhängig vom Lebenszyklus eines Containers zu verwalten.

**Abnahme:** Austausch oder Neustart des Anwendungscontainers löscht keine Nutzdaten. Dateisystem- beziehungsweise Objektspeicherstrategie, Rechteprüfung, Uploadgrenzen und Konsistenz mit Datenbanksicherungen sind entschieden.

### REQ-DATA-004 Aufbewahrung und Löschung

**V · V1.** Für Fachinhalte, personenbezogene Daten, Protokolle, E-Mails, KI-Inhalte und Sicherungen werden nachvollziehbare Aufbewahrungs- und Löschregeln festgelegt.

**Abnahme:** Archivieren, Löschen und Wiederherstellen haben unterscheidbare Wirkungen. Referenzen, Suchdaten und Sprachfassungen werden konsistent behandelt. Es werden keine gesetzlichen Aufbewahrungsfristen ohne fachliche beziehungsweise rechtliche Klärung erfunden.

## Auslieferung und Betrieb

### REQ-OPS-001 Docker Auslieferung

**B · V1.** Die Anwendung wird als Docker-Image ausgeliefert und zunächst überwiegend beim Kunden betrieben. Der Betrieb soll nicht an einen einzelnen Hosting-Anbieter gebunden sein.

**Abnahme:** Eine dokumentierte Installation und Aktualisierung ist mit freigegebenen Voraussetzungen reproduzierbar. Anwendungsimage, Datenbank, persistente Daten und notwendige Begleitdienste sind eindeutig beschrieben. Ein einzelnes Image bedeutet nicht zwingend einen einzigen Container für den gesamten Betrieb.

### REQ-OPS-002 Wartung durch Anbieter oder Kunden

**B · V1.** Wartung kann per Fernzugriff durch den Anbieter oder durch den Kunden erfolgen.

**Abnahme:** Zuständigkeiten, Zugriffsmöglichkeiten und notwendige Betriebsinformationen sind dokumentiert. Eine Fernwartung erhält keine eingebauten universellen Zugangsdaten. Der Kunde kann die vorgesehenen Standardaufgaben anhand der Anleitung ausführen.

### REQ-OPS-003 Internetzugang

**B · V1.** Internetzugang darf als Betriebsvoraussetzung verlangt werden.

**Abnahme:** Erforderliche Ziele und Zwecke, Proxy-Anforderungen und Verhalten bei Ausfall sind vor Implementierung dokumentiert. Aus dieser Vorgabe werden weder eine bestimmte Lizenzprüfung noch Telemetrie oder ein automatischer Transfer von Kundendaten abgeleitet.

### REQ-OPS-004 Sicherung und Wiederherstellung

**V · V1.** Ein dokumentiertes Verfahren sichert Datenbank, Dateien, relevante Konfiguration und notwendiges Schlüsselmaterial konsistent.

**Abnahme:** Eine Wiederherstellung in einer frischen Umgebung wird mit Beispieldaten praktisch geprüft. Verantwortliche, Sicherungsziel, zulässiger Datenverlust und Wiederanlaufzeit sind festgelegt. Ein separates Sicherungsziel wird empfohlen; es ist kein Teil des vorläufigen 1-TB-Live-Speicherbudgets.

### REQ-OPS-005 Updates und Schemaänderungen

**V · V1.** Updates erhalten einen nachvollziehbaren Pfad inklusive Datenmigrationen, Kompatibilitätsangaben und Wiederherstellungsverfahren.

**Abnahme:** Eine ältere unterstützte Installation lässt sich mit Datenerhalt aktualisieren. Vor Änderungen sind Voraussetzungen und Sicherungsschritte bekannt. Ein älteres Image allein wird nicht als sichere Rücknahme einer bereits erfolgten Schemaänderung ausgegeben.

### REQ-OPS-006 Betriebszustand und Fehler

**V · V1.** Betriebspersonal erkennt Erreichbarkeit, Datenbankprobleme, fehlgeschlagene E-Mails, ausstehende Jobs und Speicherengpässe.

**Abnahme:** Diagnoseinformationen enthalten eine zuordenbare Fehlerkennung, jedoch keine Geheimnisse. Regeln für Wiederholungen, Warteschlangen und Benachrichtigung sind definiert. Ein Hintergrundfehler führt nicht unbemerkt zum Verlust eines Auftrags.

### REQ-SEC-001 Sicherheitsanforderungen

**V · V1.** Vor Kundeneinsatz werden Bedrohungen für Anmeldung, Rechte, Uploads, Rich Text, E-Mail, API und ausgehende Integrationen geprüft. Geheimnisse werden getrennt von gewöhnlichen Konfigurations- und Protokolldaten behandelt.

**Abnahme:** Es liegen negative Zugriffstests und überprüfbare Schutzmaßnahmen vor. HTML-Inhalte können keine unerlaubten Skripte ausführen. Kundeneigene interne Dienste wie LDAP und Ollama bleiben gezielt konfigurierbar; ausgehende Verbindungen folgen einer ausdrücklich definierten Vertrauensgrenze.

## Leistung und Kapazität

### REQ-PERF-001 Referenzlast

**B · V1.** Das anfängliche Mengengerüst beträgt 500 bis 1.500 neue Tickets monatlich und ungefähr 15 gleichzeitig aktive Benutzer in Unternehmen mit 10 bis 100 Nutzern.

**Abnahme:** Ein Lastprofil bildet Ticketbearbeitung, Suche, Wiki, Zeitbuchungen, E-Mail-Verarbeitung und SLA-Jobs ab. Als vorgeschlagener Langzeittest dienen 90.000 Tickets, entsprechend fünf Jahren am oberen Monatsvolumen. Anhänge, Historieneinträge und Suchumfang werden gesondert dimensioniert.

### REQ-PERF-002 RAM und Speicherbudget

**B · Planung.** Es besteht keine feste RAM-Obergrenze. Bis zu 1 TB SSD bleibt der bisherige Planungsrahmen für Live-Daten; die Machbarkeit hängt insbesondere von Anhängen und Aufbewahrung ab.

**Abnahme:** Die Dimensionierung nennt CPU, RAM, SSD und nötige Speicherleistung mit Annahmen und Messwerten. Docker hebt die Ressourcenanforderungen des Hosts nicht auf. Das Budget wird nicht als garantierte unbegrenzte Aufnahmekapazität dargestellt.

### REQ-PERF-003 Messbare Antwortzeiten

**V · V1.** Leistungsziele werden für zentrale Abläufe und Hintergrundverarbeitung getrennt festgelegt.

**Abnahme:** Für Listen, Suche, Öffnen und Speichern sowie Mail- und SLA-Verarbeitung sind Zielwerte einschließlich Perzentil, Last, Datenumfang und Hardware dokumentiert. Externe Modellantworten werden getrennt gemessen. Werte sind aktuell offen und werden nicht nachträglich passend zu Messergebnissen gewählt.

### REQ-PERF-004 Getrennte KI Dimensionierung

**V · Ab lokaler KI-Anbindung.** Ressourcen für ein lokal betriebenes Modell werden zusätzlich zum eigentlichen Produktbedarf ausgewiesen.

**Abnahme:** Hardwareempfehlungen trennen Anwendung, Datenbank, Hintergrunddienste und Modellbetrieb. Die Anbindung eines extern betriebenen Ollama-Dienstes setzt keinen lokalen Modellserver im Anwendungscontainer voraus.

## Migration und spätere Funktionen

### REQ-MIG-001 Migration zeitnah nach Version 1

**B · Früh danach.** Datenmigration aus bestehenden Lösungen ist nicht Teil der ersten verkaufbaren Version, soll aber zeitnah folgen.

**Abnahme:** Vor Umsetzung sind Quellsystem, Version, Priorität und zu übernehmende Datenarten entschieden. Redmine, Easy8 beziehungsweise Easy Redmine und Jira sind zu prüfende Kandidaten, keine bereits bestätigte Importgarantie.

### REQ-MIG-002 Migration vorbereiten

**V · V1 Architektur und früh danach.** Stabile Kennungen, Herkunftsbezüge und eine spätere Importgrenze werden im Datenkonzept berücksichtigt.

**Abnahme:** Die spätere Spezifikation behandelt Benutzer, Rechte, Historie, Dateien, Wiki, Zeitbuchungen, eigene Felder, Wiederholbarkeit, Probelauf und Ergebnisabgleich. Nicht übertragbare Inhalte werden transparent ausgewiesen.

### REQ-EXT-001 Weitere Fachmodule bewerten

**B · Planung für die Roadmap.** Weitere Funktionen eines vergleichbaren Produkts werden vorab bewertet und eingeordnet.

**Abnahme:** Das Inventar prüft mindestens die Kandidaten Projektplanung und Gantt, agile Ansichten, Ressourcen und Kapazitäten, Budgets und Kosten, Berichte und Dashboards, Testmanagement, Vorlagen und PDF-Ausgabe, zusätzliche Integrationen sowie weitergehende Kundenverwaltung. Die Aufzählung ist ein Vorschlag für die Prüfung und noch keine Lieferzusage für jeden Kandidaten.

## Zusammenarbeit mehrerer KI Entwickler

### REQ-DEV-001 Gemeinsame Projektregeln

**B · Planung und Entwicklung.** Codex und Claude Code sollen dieselben Anforderungen und Regeln verwenden können. Das Repository liegt später auf GitHub; Arbeit erfolgt teilweise gleichzeitig.

**Abnahme:** AGENTS.md benennt verbindliche Einstiegsdateien, Zuständigkeiten und Prüfregeln. CLAUDE.md verweist auf dieselben Regeln. Neue Sitzungen können ohne private Chatkenntnisse den Arbeitsstand verstehen. Diese lokalen Dateien allein richten noch kein GitHub-Repository ein.

### REQ-DEV-002 Zentrale Aufgaben und Ergebnisse

**B · Planung und Entwicklung.** TODO.md führt offene Arbeit, DONE.md die abgeschlossenen und geprüften Ergebnisse.

**Abnahme:** Jede Aufgabe hat ID, Bezug zu Anforderungen, Status, Verantwortlichkeit, Abnahmekriterien und Abhängigkeiten. Ergebnisse enthalten Prüfung, Review beziehungsweise Abnahme, Datum und soweit vorhanden Commit- oder PR-Verweis. Vorhandene Historie wird durch neue Einträge ergänzt statt still überschrieben.

### REQ-DEV-003 Gleichzeitige Arbeit koordinieren

**V · Vor paralleler Entwicklung.** Aufgabenvergabe und Integration werden über eine koordinierende Instanz geordnet. Parallel arbeitende KIs verwenden getrennte Arbeitsstände.

**Abnahme:** Das Verfahren verhindert Doppelvergabe und unkoordinierte Änderungen zentraler Dateien. Einträge in Markdown gelten nicht als technische Sperren. Der Umgang mit Konflikten, abgebrochenen Sitzungen und Übergaben ist dokumentiert und mit einem Beispielszenario geprüft.

### REQ-DEV-004 Fachliche Abnahme

**B · Alle Lieferstände.** Holger Gerlach übernimmt die fachliche Abnahme. Der Reviewer verfügt über grundsätzliches Coding-Verständnis; Ergebnisse müssen deshalb fachlich nachvollziehbar demonstrierbar sein.

**Abnahme:** Jede relevante Lieferung benennt geändertes Verhalten, Prüfszenarien, Ergebnisse und verbleibende Einschränkungen. Technische Prüfung und fachliche Freigabe werden getrennt festgehalten. KI-Übereinstimmung allein wird nicht als Qualitätsnachweis gewertet.

### REQ-DEV-005 Nachvollziehbare Änderungen

**V · Entwicklung.** Änderungen werden klein genug gehalten, um sie anhand der Anforderungen prüfen zu können. Besonders kritische Bereiche erhalten gezielte technische Prüfung.

**Abnahme:** Eine Änderung nennt betroffene REQ-IDs und sinnvolle Prüfungen. Rechte, Anmeldung, SLA-Berechnung, Datenmigration und Wiederherstellung erhalten Risiko und Verhalten entsprechende Tests. Die Wahl des technischen Reviewers und der Freigabeschwellen bleibt offen.

## Fachliche Abnahmeszenarien für Version 1

Diese Szenarien verbinden mehrere Anforderungen. Sie sind Entwürfe für die spätere Prüfung, keine bereits ausgeführten Tests.

| Szenario | Prüffall | Wesentliche Anforderungen |
|---|---|---|
| ACC-001 | Administrator richtet zwei Projekte mit unterschiedlichen Workflows und internen sowie externen Gruppen ein | REQ-ORG-003, REQ-ACL-001, REQ-ACL-002, REQ-ACL-003 |
| ACC-002 | E-Mail erzeugt genau einen Vorgang, Geschäftszeiten bestimmen die Frist und eine Eskalation erreicht die festgelegten Empfänger | REQ-HD-001, REQ-HD-002, REQ-HD-003, REQ-HD-004, REQ-HD-005 |
| ACC-003 | Kunde sieht und beantwortet freigegebene Anfragen; interne Notizen und geschützte Felder bleiben unzugänglich | REQ-PORTAL-001, REQ-TKT-003, REQ-ACL-003, REQ-ACL-004 |
| ACC-004 | Bearbeiter bucht und korrigiert Zeit; die berechtigte Auswertung stimmt mit den Einzelbuchungen überein | REQ-TIME-001, REQ-TIME-002 |
| ACC-005 | Wiki-Seite wird zuerst nur deutsch angelegt, später übersetzt; veraltete Bearbeitung überschreibt keine neuere Fassung | REQ-DOC-001, REQ-DOC-002, REQ-DOC-003 |
| ACC-006 | Anforderung erhält Kriterien und Freigabe, wird mit Ticket und GitHub- beziehungsweise GitLab-Objekt verknüpft | REQ-REQ-001, REQ-REQ-002, REQ-REQ-003, REQ-GIT-001 |
| ACC-007 | Deutscher und englischer Nutzer arbeiten mit demselben Datenbestand im konfigurierten Unternehmensauftritt | REQ-I18N-001, REQ-I18N-002, REQ-I18N-003, REQ-BRAND-001 |
| ACC-008 | Installation wird aktualisiert und aus einer Sicherung einschließlich Dateien wiederhergestellt | REQ-OPS-001, REQ-OPS-004, REQ-OPS-005, REQ-DATA-003 |
| ACC-009 | Referenzlast wird mit dokumentierter Hardware und vorab festgelegten Leistungszielen geprüft | REQ-PROD-004, REQ-PERF-001, REQ-PERF-002, REQ-PERF-003 |
| ACC-010 | Sobald KI ausgeliefert wird: Antwortvorschlag bleibt bis zur Freigabe unverändert, prüft Rechte erneut und versendet nichts automatisch | REQ-AI-003, REQ-AI-004, REQ-AI-005, REQ-AI-006 |

## Offene Punkte und nächster Prüfstand

Die maßgeblichen offenen Fragen stehen in DECISIONS.md unter OPEN-001 bis OPEN-021. Vorrang haben Backend und Modulstruktur, Berechtigungsmodell, SLA-Regeln, KI-Lieferumfang sowie Betriebs- und Leistungsziele. Bereits bestätigte Vorgaben werden dabei nicht erneut zur Disposition gestellt.

Die erste fachliche Prüfung soll insbesondere die als V gekennzeichneten Ergänzungen und die vorgeschlagenen Abnahmekriterien beurteilen. Bis dahin bleiben diese Teile Entwürfe. Eine Freigabe dieser Spezifikation ist keine automatische Freigabe zur Programmierung.
