# Wiki, Sprachen und Unternehmensauftritt

Stand 16. Oktober 2026 · Planungsentwurf für TASK-007

Dieses Dokument konkretisiert REQ-DOC-001 bis REQ-DOC-004, REQ-I18N-001 bis REQ-I18N-003, REQ-BRAND-001, REQ-BRAND-002 und REQ-UX-001. Maßgeblich bleiben die [zentralen Anforderungen](../REQUIREMENTS.md) und das [Rechtekonzept](PERMISSIONS.md).

Bestätigt sind moderne Textbearbeitung, optionale Wiki-Sprachfassungen, Deutsch und Englisch, Konfliktschutz ohne gemeinsame Echtzeitbearbeitung sowie Logo und Farben für Anwendung, Portal, Anmeldung und E-Mails. Holger hat am 15. Oktober ausdrücklich Entwurf und anschließende Veröffentlichung gewählt (DEC-033). Alle weiteren Detailregeln und Prüfkriterien unten sind Vorschläge zur fachlichen Prüfung. Eine Veröffentlichung macht Inhalte nur innerhalb ihres berechtigten Leserkreises zugänglich; sie bedeutet keine öffentliche Freigabe im Internet.

## Seite, Sprachfassung und Revision

| Bestandteil | Inhalt und vorgeschlagene Regel |
|---|---|
| Logische Seite | Stabile Kennung, Unternehmens- und Projektkontext, Wissensbereich und Rechte. Umbenennen ändert keine Verknüpfung. |
| Sprachfassung | Sprache, Titel, eigene Revisionen und eigener Veröffentlichungsstand. Mindestens eine Fassung genügt. |
| Revision | Inhalt, Schema-Version, Autor, Zeitpunkt und Ausgangsrevision; gespeicherte Revisionen werden nicht überschrieben. |
| Entwurfsstand | Neueste gespeicherte Bearbeitung. Nur Personen mit Entwurfsrechten können sie lesen, auch über API, Suche und KI. |
| Veröffentlichter Stand | Verweis auf eine ausdrücklich veröffentlichte Revision je Sprache. Ein neuer Entwurf verändert diesen Verweis nicht. |
| Datei | Eigene Dateikennung und Rechteprüfung bei jedem Abruf. Ein eingebettetes Bild wird durch die Veröffentlichung der Seite nicht automatisch allgemein freigegeben. |

Vorgeschlagene getrennte Gruppenrechte: veröffentlichte Inhalte lesen, Entwürfe lesen, bearbeiten, veröffentlichen, archivieren und Historie lesen. Bearbeiten setzt das Lesen des bearbeiteten Entwurfs voraus. Eine Gruppe kann Bearbeitung und Veröffentlichung kombinieren; ein zwingendes Vier-Augen-Verfahren ist nicht beschlossen. Der Veröffentlichungsdialog zeigt Sprache, Revision und Leserkreis. Eine Änderung des Leserkreises benötigt eine gesonderte Berechtigung und Vorschau.

Bei externer Freigabe prüft der Veröffentlichungsweg den Zielkreis erneut. Verweise und Dateien dürfen keine internen Titel, Vorschautexte oder Inhalte preisgeben. Die Sichtbarkeit im Kundenportal bleibt der Vorschlag REQ-PORTAL-002; interne Veröffentlichung ist auch ohne diese Erweiterung nutzbar.

## Bearbeiten und veröffentlichen

1. Die Autorin öffnet die deutsche Revision 4 und bearbeitet eine Kopie. „Entwurf speichern“ erzeugt Revision 5. Für Leser bleibt Revision 4 sichtbar.
2. „Vorschau“ zeigt Revision 5 mit der vorgesehenen Sprache und dem Leserkreis. Entwurfskennzeichnung und Warnungen zu nicht erreichbaren Verweisen bleiben sichtbar.
3. „Veröffentlichen“ benötigt das entsprechende Recht und die aktuelle Versionskennung. Die Anwendung prüft Inhalt, Zielkreis und Referenzen erneut und setzt den veröffentlichten Stand auf Revision 5.
4. Eine weitere Bearbeitung erzeugt Revision 6 als Entwurf. Revision 5 bleibt veröffentlicht, bis eine berechtigte Person erneut veröffentlicht.

Die Veröffentlichung protokolliert Person, Zeitpunkt, Sprachfassung und Revision. API und UI verwenden denselben Vorgang. Vorschlag: „Veröffentlichung zurücknehmen“ entfernt die Leserfassung, bewahrt aber die Historie; eine Rückkehr zu älterem Inhalt erzeugt einen neuen Entwurf aus der gewählten Revision und erfordert erneute Veröffentlichung. Archivieren und endgültiges Löschen folgen den noch festzulegenden Aufbewahrungsregeln aus OPEN-016.

**Konfliktbeispiel:** Zwei Personen öffnen Revision 5. A speichert Revision 6. B sendet ihre Änderungen auf Basis von 5. Das System weist die veraltete Basis zurück, erhält Bs noch nicht gespeicherte Eingabe und zeigt Unterschiede zu 6. B übernimmt die gewünschten Änderungen und speichert mit der aktuellen Basis. Es gibt weder stilles Überschreiben noch eine fachlich ungeprüfte automatische Zusammenführung. Dieselbe Prüfung gilt, wenn sich der Entwurf zwischen Vorschau und Veröffentlichung ändert.

Vorschlag V1: ausdrücklich serverseitig speichern, sichtbarer Speicherzustand und Warnung beim Verlassen mit ungespeicherten Änderungen. Automatisches Speichern und dauerhafte Browser-Zwischenspeicherung sind kein zusätzlicher V1-Lieferumfang dieses Entwurfs. Eine entzogene Berechtigung stoppt weitere Speicherung und Auslieferung; bereits gelesene Inhalte lassen sich beim Empfänger nicht zurückholen.

## Editor und Speicherformat

Der vorgeschlagene Mindestumfang umfasst Absätze, Überschriften, Fett/Kursiv, geordnete und ungeordnete Listen, Tabellen mit Kopfzeile, Links, Bilder mit Alternativtext, Zitate und Codeblöcke. Tastaturbedienung, erkennbare Fokusposition und verständliche Werkzeugnamen gehören zur Prüfung. Beliebiges HTML, eingebettete Skripte und interaktive Fremdinhalte sind kein Bedarf für diesen Kern.

Beim Einfügen aus Zwischenablage oder importiertem Inhalt werden erlaubte Elemente übernommen. Entfernte Formatierungen werden erkennbar, statt unbemerkt Text zu verlieren. Links verwenden zugelassene Protokolle; Codeblöcke sind Text, keine Ausführung. Bilder werden über den vorgesehenen Dateidienst verwaltet. Externe Bilder werden nicht ungefragt vom Leserbrowser oder Server nachgeladen.

**Speichervorschlag:** Ein versioniertes strukturiertes Dokument mit ausdrücklich erlaubten Elementen ist die bearbeitbare Quelle. Gerendertes HTML und Suchtext werden daraus abgeleitet. Der Server prüft Schema, Größen und Referenzen unabhängig vom Editor. Ein späterer Formatwechsel benötigt eine dokumentierte Migration, Sicherung und Prüfung der Bestandsseiten. JSON allein garantiert keine Austauschbarkeit zwischen Editoren.

| Kandidat | Verifizierte Grundlage | Noch zu prüfen |
|---|---|---|
| Tiptap | Kann HTML oder JSON ausgeben; die Dokumentation empfiehlt JSON für den gespeicherten Editorzustand. [Persistenz-Dokumentation](https://tiptap.dev/docs/editor/core-concepts/persistence) | Aufwand für eigene Oberfläche, zulässige Erweiterungen, React-Einbindung und vollständige Lizenz-/Kostenprüfung des gewählten Paketsatzes. |
| Lexical | Bietet React-Anbindung, einen eigenen Dokumentzustand und JSON-Speicherung. Layout, Werkzeugleisten und Speicherung gehören zur Anwendung. [Offizielle Einführung](https://lexical.dev/docs/intro) | Aufwand für vollständige Bedienung, Tabellen, Bilder, Zwischenablage, Migration und Lizenzprüfung der konkreten Abhängigkeiten. |

Die Primärquellen wurden am 15. Oktober 2026 gelesen. Empfehlung: beide Kandidaten nach späterer Implementierungsfreigabe mit derselben Beispielsammlung vergleichen. Kein Editor ist ausgewählt, kein Prototyp erstellt. Für das inzwischen bestätigte Java/Spring-Boot-Backend (DEC-039) bleibt die fachliche Speicherung hinter einer eigenen Schnittstelle; eine eventuell nötige zusätzliche Render-Laufzeit muss vor Auswahl berücksichtigt werden. Editorinterne Undo-Historie ersetzt keine serverseitigen Revisionen.

Ein verlustfreier Wechsel über Markdown oder HTML wird nicht zugesagt. Import-/Exportformate, Grenzen für Dokumentgröße und Bilder sowie die Suchstrategie bleiben unter OPEN-011 offen. Sicherungen müssen Originaldokumente, Schema-Versionen und referenzierte Dateien gemeinsam erfassen.

## Optionale Übersetzungen

Eine deutsche Seite ist ohne englische Fassung vollständig nutzbar. „Sprachfassung hinzufügen“ erzeugt eine zugeordnete Fassung, keinen zweiten unabhängigen Artikel. Übersetzungen haben eigene Entwürfe und Veröffentlichungen. Die neue Fassung kann auf die übersetzte Ausgangsrevision verweisen.

Beispiel: Deutsch 5 ist veröffentlicht, Englisch 2 wurde aus Deutsch 4 übersetzt. Nach Veröffentlichung von Deutsch 5 zeigt die Redaktion „Ausgangsfassung wurde geändert“. Englisch 2 bleibt mit diesem Hinweis nutzbar; die Anwendung behauptet keine automatische inhaltliche Gleichwertigkeit. Der Hinweis bewertet nur den Revisionsbezug, nicht die Qualität der Übersetzung. KI-Übersetzungen wären bestätigungspflichtige Entwürfe; ihre Lieferung ist durch die vier ersten KI-Anwendungsfälle nicht automatisch beschlossen.

Für Leser ohne passende Sprachfassung: gewünschte veröffentlichte Sprache, danach konfigurierte Ausgangssprache, danach eine berechtigte veröffentlichte Variante. Der tatsächliche Sprachname wird sichtbar angezeigt. Entwürfe oder unberechtigte Varianten sind nie Rückfallinhalte; ihre Titel und Existenz werden nicht verraten. Gibt es keine lesbare Veröffentlichung, erscheint keine Seite. Explizite Links zu einer bestimmten historischen Revision benötigen zusätzlich Historienrechte.

## Oberflächensprache, Formate und Zeitzone

| Bereich | Vorgeschlagene Regel |
|---|---|
| Oberfläche nach Anmeldung | Gespeicherte Benutzerwahl vor Unternehmensstandard; Auslieferungsstandard Deutsch. |
| Anmeldung ohne Sitzung | Explizite Sprachauswahl; Browserpräferenz nur als Hinweis, danach Unternehmensstandard. Keine Ableitung von Benutzeridentität. |
| Fehlender Oberflächentext | Unternehmensstandard, danach eingebautes Deutsch. DE/EN müssen vor Auslieferung vollständig geprüft sein; fehlende Schlüssel sind ein Qualitätsfehler. |
| Kundenfelder und Status | Stabile technische Kennung plus übersetzbare Bezeichnungen. Fehlende Übersetzung zeigt die konfigurierte Originalbezeichnung. |
| E-Mail | Verifizierte Empfängerpräferenz, danach vereinbarte Kundensprache, danach Unternehmensstandard. UI-Sprache des Absenders ändert dies nicht. |
| Zahlen und Zeit | Sprache, regionale Formatierung und Zeitzone sind getrennte Einstellungen. UTC-Zeitpunkte bleiben eindeutig; fachliche Kalendertage werden nicht zu Mitternachts-Zeitpunkten umgedeutet. |

Beispiel: Eine englische Oberfläche kann Zahlen im deutschen Format und Zeiten in Europe/Berlin zeigen. Eine Dauer von 90 Minuten bleibt 90 Minuten, ob sie als „1,5 h“ oder „1.5 h“ dargestellt wird. SLA-Geschäftszeiten verwenden weiterhin den Servicekalender aus dem [Helpdesk-Entwurf](HELPDESK_SLA.md), nicht die Zeitzone des Betrachters.

Übersetzungskataloge erhalten stabile Schlüssel, benannte Platzhalter, Mehrzahlregeln und einen Prüfweg für fehlende Einträge. Ein zusätzliches Sprachpaket muss ohne Änderung der Fachmodule integrierbar sein. Lange Texte, Tastaturführung und später mögliche andere Schreibrichtungen werden bei der Komponentenauswahl berücksichtigt; zusätzliche ausgelieferte Sprachen sind damit nicht zugesagt.

## Logo, Farben und Vorlagen

Administratoren konfigurieren ein Logo sowie begrenzte Designvariablen für Akzent, Hintergrund, Text und Links. Die Vorschau umfasst Anwendung, Portal, Anmeldung und eine E-Mail in DE/EN. Freigabe und Zurücksetzen beziehen sich auf eine versionierte Konfiguration. Kontrast und Erkennbarkeit werden geprüft; ungültige Werte können nicht aktiviert werden. Verbindliche Kontrastziele und erlaubte Formate/Größen sind in OPEN-015 noch festzulegen. Das Design darf Fehler, Status oder Fokus nicht ausschließlich durch Farbe ausdrücken.

Vorschlag: zugelassene Bildformate nach Dateiprüfung, keine beliebigen CSS-, JavaScript- oder HTML-Eingaben. Vorlagen verwenden einen begrenzten Platzhalterkatalog und erlaubte Formatierung. Die technische Prüfung muss auch importierte Logos, Links und HTML-Ausgaben berücksichtigen. Unternehmensbranding wird bereits vor Anmeldung aus dem Installationskontext geladen; ein künftiger Mandant wird nicht über unzuverlässige Benutzereingaben gewählt.

Eine E-Mail-Vorlage besitzt Sprachfassungen, Version, Vorschau und eine getestete Rückfallvorlage. Die Vorlage erhält nur für den Empfänger freigegebene Werte, keinen allgemeinen Datenbankzugriff. Vorschau verwendet synthetische Daten. Platzhalterwerte werden kontextgerecht maskiert; ein fehlendes Pflichtfeld verhindert den Versand mit nachvollziehbarer Meldung. Pro Empfänger kann eine andere Sprachfassung nötig sein; eine gemeinsame Nachricht darf nur Inhalte enthalten, die alle Empfänger lesen dürfen.

Vorgeschlagen werden HTML und Klartext derselben Nachricht. Ein Logoausfall lässt Absender und Inhalt weiterhin verständlich. PDFs folgen später mit eigenen Layouttests; gleiche Platzhalter bedeuten keine identische Darstellung in E-Mail und PDF.

## Durchgängiges Beispiel

Die synthetische Wiki-Seite „Serviceanfrage ergänzen“ enthält folgende Elemente:

| Element | Beispielinhalt |
|---|---|
| Überschrift | Angaben zu einer Störung |
| Absatz und Hervorhebung | **Keine Passwörter** in eine Supportanfrage eintragen. |
| Geordnete Liste | 1. Anfrage öffnen. 2. Zeitpunkt und betroffene Funktion ergänzen. 3. Antwort prüfen und senden. |
| Tabelle | Spalten „Angabe“ und „Beispiel“; Zeilen „Zeitpunkt: 15.10.2026, 09:30 Europe/Berlin“ und „Funktion: Berichte“. |
| Link | Berechtigungsgeprüfte Referenz auf die zugehörige Anfrage; der angezeigte Titel folgt deren Leserechten. |
| Bild | Verwaltetes Beispielbild mit Alternativtext „Formular zur Ergänzung einer Serviceanfrage“. |
| Codeblock | Rein textuelles Beispiel `Fehlercode: DEMO-42`. |
| Englische Variante | Optional „Add details to a support request“, separat als Entwurf und später ausdrücklich veröffentlicht. |

Beispiel einer Benachrichtigungsvorlage mit ausschließlich freigegebenen Platzhaltern:

| Teil | Deutsch | Englisch |
|---|---|---|
| Betreff | Antwort auf Anfrage {{ticket.number}} | Reply to request {{ticket.number}} |
| Anrede | Guten Tag {{recipient.display_name}}, | Hello {{recipient.display_name}}, |
| Nachricht | zu Ihrer Anfrage liegt eine neue Antwort vor. | a new reply is available for your request. |
| Linktext | Anfrage öffnen | Open request |
| Signatur | {{company.display_name}} | {{company.display_name}} |

Das Linkziel `{{ticket.portal_url}}` führt zur Anmeldung beziehungsweise erneuten Rechteprüfung. Die Vorlage enthält absichtlich keinen internen Notiztext. Platzhalternamen sind ein Vertragsvorschlag, kein bereits implementiertes Vorlagenformat. Offen bleiben die genaue Logoeinbettung und der unterstützte Mailclient-Umfang.

## Fachliche Prüffälle

Diese Fälle sind Abnahmeentwürfe, keine ausgeführten Anwendungstests.

| Fall | Erwartetes Ergebnis |
|---|---|
| W01 Deutsche Seite ohne Übersetzung | Vollständig nutzbar; keine Pflicht zur englischen Fassung. |
| W02 Gesamte Beispielseite dreimal bearbeiten | Struktur, Text, Links, Bildreferenz und Alternativtext bleiben erhalten. |
| W03 Entwurf speichern | Leser sehen weiter die bisherige Veröffentlichung; kein Entwurf in Suche, API oder KI-Kontext ohne Entwurfsrecht. |
| W04 Veröffentlichung bestätigen | Nur die angezeigte aktuelle Revision der gewählten Sprache wird veröffentlicht; Audit ist nachvollziehbar. |
| W05 Zwei Bearbeiter auf gleicher Basis | Zweites Speichern meldet Konflikt, überschreibt nichts und erhält die eigene Eingabe. |
| W06 Entwurf nach Vorschau geändert | Veröffentlichung mit alter Versionskennung scheitert; neue Vorschau erforderlich. |
| W07 Deutsche Ausgangsrevision geändert | Englische Fassung bleibt erhalten und erhält einen überprüfbaren Aktualisierungshinweis. |
| W08 Gewünschte Sprache nur als Entwurf vorhanden | Leser erhalten ausschließlich eine berechtigte veröffentlichte Rückfallfassung mit Sprachhinweis. |
| W09 Rechte entzogen | Seite, Historie, Suchtreffer, Dateien und neue KI-Abrufe beachten die Änderung. |
| W10 Eingefügtes Skript oder fremdes Bild | Kein ausführbarer Inhalt und kein ungefragter externer Bildabruf; verständliche Rückmeldung. |
| W11 Seite umbenennen und verschieben | Stabile berechtigte Referenzen bleiben gültig; neue Projektgrenze wird erneut geprüft. |
| W12 DE/EN und zusätzliche Testsprache | Gleicher Arbeitsablauf; lange Texte, Platzhalter und Mehrzahlformen bleiben bedienbar. |
| W13 Sprache und Zeitzone wechseln | Zeitbuchungsdauer und SLA-Termin bleiben fachlich identisch. |
| W14 Branding aktivieren und zurücksetzen | Alle vier Oberflächen zeigen die gewählte Version; Fehler- und Fokusdarstellung bleiben verständlich. |
| W15 Vorlage mit internem oder unbekanntem Platzhalter | Keine Datenfreigabe; verständlicher Validierungsfehler. |
| W16 Mailvorschau und spätere Formatmigration | DE/EN und Klartext bleiben nutzbar; Formatmigration wird separat an allen Beispielelementen auf Erhalt geprüft. |

## Noch festzulegen

OPEN-011 umfasst weiterhin Editor, genaues Dokumentformat, Größen, Dateispeicher, Suche und Import/Export; das Prinzip „Entwurf vor Veröffentlichung“ ist durch DEC-033 entschieden. OPEN-015 umfasst konkrete Sprachstandards, Rückfallregeln, Designgrenzen, Barrierefreiheitsziel, Browser/Mailclients und Vorlagenbearbeitung. TASK-010 ergänzt Aufbewahrung und Sicherung. Holger prüft die vorgeschlagenen Abläufe und Beispiele; technische Prüfung und Produktimplementierung bleiben getrennte nächste Schritte.
