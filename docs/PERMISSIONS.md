# Identität und Berechtigungen

Stand 16. Oktober 2026 · TASK-004 · Entwurf zur fachlichen und technischen Prüfung

Dieser Entwurf konkretisiert REQ-ACL-001 bis REQ-ACL-005, REQ-AUTH-001 bis REQ-AUTH-004 und REQ-PORTAL-003 aus den [zentralen Anforderungen](../REQUIREMENTS.md). Er wurde von Codex erstellt. Der vorgesehene Claude-Aufruf scheiterte an der Anmeldung und lieferte keinen Entwurf; eine unabhängige Prüfung hat nicht stattgefunden.

## Bestätigte Vorgaben und offene Ausgestaltung

Holger hat am 13. Oktober 2026 drei Entscheidungen getroffen: Externe sehen standardmäßig die Anfragen ihrer Kundenorganisation, innerhalb ihrer freigegebenen Projekte; ein ausdrückliches Feldverbot gewinnt gegenüber einer Freigabe durch eine andere Gruppe; Zwei-Faktor-Authentifizierung folgt zeitnah nach V1. Diese Vorgaben stehen unter DEC-027 bis DEC-029 in [DECISIONS.md](../DECISIONS.md).

Ebenfalls bestätigt sind frei anlegbare Gruppen, projektspezifische Rechte, getrennte Feldsichtbarkeit und Änderbarkeit, lokale Anmeldung und LDAP. Alle nachfolgenden Algorithmen, Rollenbeispiele, technischen Schnittstellen und Abnahmefälle sind Vorschläge zur Umsetzung dieser Vorgaben. Insbesondere ist ein allgemeiner Vorrang von Verboten für sämtliche Aktionen damit noch nicht beschlossen.

## Begriffe und Grenzen

| Begriff | Bedeutung und Wirkung |
|---|---|
| Unternehmen | Betreiber der Installation und spätere Mandantengrenze. V1 hat genau ein Unternehmen. |
| Kundenorganisation | Kunde dieses Unternehmens, etwa Nordwerk GmbH. Mehrere Kundenorganisationen innerhalb einer Installation sind keine Mehrmandantenfähigkeit. |
| Projekt | Fachlicher Arbeitsraum. Projektzugang allein gibt externen Nutzern keinen Einblick in alle dort vertretenen Kundenorganisationen. |
| Benutzer | Stabile interne Identität, unabhängig von Benutzername, E-Mail-Adresse und Anmeldeanbieter. |
| Gruppe | Vom Administrator definierte Menge von Benutzern. Rechte werden einer Gruppe in einem festgelegten Geltungsbereich zugewiesen. |
| Mitgliedschaft | Zuordnung zu Projekt beziehungsweise Kundenorganisation; ersetzt keine Aktionsberechtigung. |
| Ressource | Ticket, Wiki-Seite, Anforderung, Zeitbuchung, Datei oder Integrationseintrag mit eigenem Kontext. |

Für das erste Modell wird eine Kundenorganisation pro externem Konto vorgeschlagen. Mehrfachzugehörigkeit und Wechsel zwischen Organisationen bleiben unter OPEN-005 zu entscheiden. Es darf keine automatische Organisationszuordnung allein anhand einer E-Mail-Domain geben.

## Rechte aus Gruppen statt fest eingebauter Rollen

Ein Berechtigungseintrag besteht im Vorschlag aus Gruppe, Geltungsbereich, Aktion und gegebenenfalls Objekt- oder Feldregel. Geltungsbereiche sind Unternehmen oder ein bestimmtes Projekt. Namen wie „Support“ haben keine fest eingebaute Wirkung. Eine spätere Umbenennung ändert keine Kennungen oder Rechte.

Die folgende Matrix illustriert mögliche Startgruppen. Ein Administrator kann sie anpassen oder ersetzen. „Eigen“ und „Organisation“ sind Objektbereiche zusätzlich zur Projektgrenze. Ein Strich bedeutet keine Freigabe, kein ausdrückliches Verbot.

| Aktion / Daten | Systemverwaltung | Projektleitung | Support | Externer Kunde |
|---|---|---|---|---|
| Benutzer und Gruppen verwalten | Ja, Unternehmen | Nur gesondert delegiert | — | — |
| Projekt konfigurieren | Nur gesondert erteilt | Zugewiesenes Projekt | — | — |
| Tickets lesen | Nur gesondert erteilt | Projekt | Projekt | Eigene Kundenorganisation im Projekt |
| Tickets bearbeiten / Status wechseln | Nur gesondert erteilt | Erlaubte Übergänge | Erlaubte Übergänge | Freigegebene Kundenaktionen |
| Anfragen erstellen / antworten | Nur gesondert erteilt | Ja | Ja | Organisation im Projekt |
| Interne Kommentare lesen / schreiben | Nur gesondert erteilt | Ja | Ja | — |
| Zeiten buchen / lesen | Nur gesondert erteilt | Nach Zeitrechten | Eigene Buchungen | — |
| Wiki lesen / ändern | Nur gesondert erteilt | Nach Wiki-Rechten | Nach Wiki-Rechten | Nur freigegebene Artikel lesen |
| Anforderungen freigeben | Nur gesondert erteilt | Eigenes Freigaberecht | — | — |
| Git-Metadaten sehen | Nur gesondert erteilt | Nach Integrationsrecht | Nach Integrationsrecht | — |
| Anbieter, Modelle und Skills verwalten | Nur berechtigte Administratoren | — | — | — |

Eine reine Systemverwaltungsgruppe erhält im Vorschlag keinen automatischen Lesezugriff auf alle Fachinhalte. Ein Administrator, der Rechte ändern darf, kann trotzdem mittelbar Zugriff ermöglichen. Diese weitreichende Befugnis muss bewusst vergeben und protokolliert werden; die Matrix verspricht keinen Schutz gegen den Betreiber mit Datenbank- oder Serverzugriff. Delegierte Projektadministration darf keine Unternehmensrechte verleihen. Die erlaubte Delegationsgrenze wird ausdrücklich konfiguriert.

## Vorschlag für die Rechteauswertung

Jede Auswertung erhält die interne Benutzerkennung, den serverseitig ermittelten Unternehmenskontext, Projekt, Ressource und angefragte Aktion. Eine vom Browser übermittelte Unternehmens- oder Kundenkennung gilt nur als Eingabe, niemals als Berechtigungsnachweis.

1. Konto und Sitzung müssen aktiv sein. Unternehmenskontext und Ressource müssen zusammenpassen.
2. Projektmitgliedschaft und weitere Objektgrenzen werden geprüft. Für Externe ist der Standard die eigene Kundenorganisation innerhalb freigegebener Projekte.
3. Für die Aktion muss mindestens eine passende Gruppenfreigabe bestehen. Ohne Freigabe wird abgewiesen. Mehrere passende Aktionsfreigaben werden im vorgeschlagenen V1-Modell zusammengeführt; außerhalb von Feldern wird zunächst kein zusätzliches Verbotssystem eingeführt. Diese Vereinfachung bedarf noch der Zustimmung.
4. Fachregeln gelten zusätzlich: erlaubter Statusübergang, Archivierung, Freigabezustand und Bearbeitungskonflikt. Ein Leserecht erlaubt keinen Schreibvorgang.
5. Für jedes Feld werden Lesen und Schreiben getrennt ausgewertet. Ein passendes ausdrückliches Verbot schlägt alle passenden Freigaben derselben Operation. Ein Verbot aus einem anderen Projekt wirkt nicht in diesem Projekt. Feldschreiben verlangt im Vorschlag zusätzlich Feldlesen; ein Leseverbot verhindert daher auch Änderungen.
6. Vor dem Speichern und bei später ausgeführten Aufträgen werden die aktuelle Berechtigung und der aktuelle Datenstand erneut geprüft. Änderungen und Rechteversion werden konsistent geprüft, damit ein zwischenzeitlicher Rechteentzug keinen alten Auftrag legitimiert.

Die Liste gilt unabhängig vom Zugang über UI, API oder Hintergrundverarbeitung. UI-Hinweise erklären Rechte, ersetzen aber keine Serverprüfung. Der Ansatz folgt den Grundsätzen „ohne Freigabe kein Zugriff“ und Prüfung jeder Anfrage aus der [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html); die konkrete Gruppenlogik ist ein Produktvorschlag.

**Beispiel Feldkonflikt:** Lea ist in „Support“ und „Externe Mitarbeit“. Beide Gruppen gelten im Projekt Service. Support erlaubt das Lesen des Felds „Interne Kalkulation“, Externe Mitarbeit verbietet es ausdrücklich. Lea erhält das Feld nicht. Entfernt der Administrator dieses Verbot, reicht eine weiterhin vorhandene Freigabe für das Lesen; eine Schreibfreigabe entsteht dadurch nicht. Auch eine administrative Gruppenzugehörigkeit überstimmt ein Feldverbot nicht automatisch.

**Verborgene Pflichtfelder:** Eine Projektkonfiguration darf externe Nutzer nicht in einen unlösbaren Formularschritt führen. Vorschlag: Beim Anlegen kommt ein fachlich zulässiger, nicht vertraulicher Standardwert zum Einsatz oder die interne Vervollständigung wird einem späteren Schritt zugeordnet. Fehlt beides, wird die Veröffentlichung dieser Formularkonfiguration abgewiesen. Es gibt keine heimliche Sonderfreigabe für Pflichtfelder. Die konkrete Regel gehört zu TASK-005.

## Organisationssicht im Kundenportal

Ein Beispielprojekt „Service“ betreut Nordwerk und Südtechnik. Lea und Paul sind Nordwerk zugeordnet; Mia gehört zu Südtechnik. Alle drei haben Portalrechte im Projekt. Lea sieht standardmäßig auch Pauls Nordwerk-Anfragen. Sie sieht keine Südtechnik-Anfragen. Ein weiteres Nordwerk-Projekt ohne Leas Projektmitgliedschaft bleibt ebenfalls unsichtbar.

Eine Organisationszuordnung erweitert keine Feldrechte. Interne Kommentare, interne Anhänge, Zeitdaten und vertrauliche Git-Inhalte werden durch das Lesen einer Anfrage nicht automatisch freigegeben. Kundenlesbare Beiträge und ihre Dateien benötigen eine eindeutige Zielgruppe. Beim Verschieben einer Anfrage in eine andere Organisation oder ein anderes Projekt werden Rechte, Empfänger, Verknüpfungen und bereits vorbereitete Benachrichtigungen neu bewertet. Bereits versandte E-Mails können nicht zurückgenommen werden.

Administratoren können zusätzliche Rechte gezielt erteilen. Eine Erweiterung auf weitere Organisationen oder alle Projektanfragen benötigt eine ausdrücklich dargestellte Bereichsfreigabe; sie entsteht niemals allein durch das Hinzufügen zur Projektgruppe. Welche erweiterten Bereichsoptionen V1 bereitstellt, bleibt unter OPEN-005 offen. Die bestätigte Standardsicht wird dadurch nicht eingeschränkt.

## Einheitliche Durchsetzung in allen Datenwegen

| Datenweg | Vorgeschlagene Regel |
|---|---|
| API und Formulare | Nur erlaubte Felder ausgeben. Schreibversuche auf unerlaubte Felder vollständig ablehnen; keine Teiländerung ohne ausdrücklichen API-Vertrag. Objektkennungen verleihen keine Rechte. |
| Suche, Filter und Sortierung | Nur zugängliche Datensätze und Felder durchsuchen. Auch Trefferzahl, Reihenfolge, Facetten, Vorschautext und Autovervollständigung dürfen verborgene Inhalte nicht verraten. Unerlaubte Filter werden abgewiesen. |
| Historie und Beziehungen | Aktuelle Leserechte gelten auch für alte Werte und verknüpfte Titel. Rechteentzug macht vorher erzeugte Vorschauen nicht weiter verfügbar. Historie ist kein Umgehungsweg. |
| Exporte und Berichte | Abfrage und Ausgabe unter dem Auftraggeberkontext. Vor Ausführung und Download erneut prüfen. Nach Rechteentzug vorhandene Exportdatei sperren; Aufbewahrung separat festlegen. |
| E-Mail | Fachliche Empfängerrechte vor Versand prüfen; Inhalt je Empfänger begrenzen. CC und Weiterleitungen verleihen keine Mitgliedschaft. Vorlagen erhalten nur freigegebene Platzhalterwerte. Unsichere Empfängerzuordnung stoppt den Versand. |
| Dateien | Berechtigung auf Elternobjekt und Beitrag prüfen, auch bei direktem Download. Vorschlag V1: authentifizierter Download über die Anwendung statt dauerhaft öffentlicher Dateilinks. |
| KI | Kontext vor Modellaufruf begrenzen. Interne Felder nicht erst aus der Antwort entfernen. Vorschläge und Quellen bei Anzeige und Bestätigung erneut prüfen. Geänderte Rechte machen betroffene alte Vorschläge ungültig. |
| Caches und Jobs | Unternehmens-, Benutzer- und Rechtekontext berücksichtigen; Rechteänderungen invalidieren betroffene Daten. Ein Worker besitzt keinen pauschalen Fachzugriff für den Auftraggeber. |

Bei kopierten Inhalten hilft die ursprüngliche Feldmaske allein nicht: Eine Zusammenfassung aus vertraulichen Quellen bleibt an deren Zugriff gebunden. Für Quellen, die sich technisch nicht zuverlässig nachverfolgen lassen, werden solche Zusammenfassungen nicht als organisationsweit sichtbares Wissen wiederverwendet. Menschlich verfasste Freitexte können vertrauliche Inhalte enthalten; eine Rechteprüfung kann deren Bedeutung nicht verlässlich klassifizieren. Sichtbare Zielgruppen und bewusste Veröffentlichungen bleiben erforderlich.

## Anmeldung und Kontenlebenszyklus

Anmeldeanbieter liefern einen überprüften Identitätsnachweis. Erst die Anwendung ordnet diesen einer internen Identität zu und wertet Gruppen und Projekte aus. Der vorgeschlagene Schlüssel einer externen Identität besteht aus Anbieterkennung und unveränderlicher Verzeichniskennung. Anzeigename und E-Mail dürfen sich ändern. Gleichnamige lokale und LDAP-Konten werden nie automatisch zusammengeführt.

| Vorgang | Vorgeschlagenes Verhalten; Details unter OPEN-006 |
|---|---|
| Lokales Konto | Einladung durch berechtigte Verwaltung, Passwort als zeitgemäß geschützter Hash, begrenzte Anmeldeversuche, allgemeine Fehlermeldung ohne Kontenauskunft. Passwortpolitik und Verfahren vor Umsetzung festlegen. |
| Wiederherstellung | Zeitlich begrenzter, einmaliger Nachweis; erfolgreiche Änderung widerruft betroffene Sitzungen. Keine Klartextpasswörter per E-Mail. |
| LDAP-Anmeldung | Verifiziertes TLS, Verzeichnisprüfung und Zuordnung zur stabilen internen Identität. Erfolgreiche Anmeldung allein erzeugt keine Projekt- oder Administratorrechte. |
| LDAP-Gruppen | Nur ausdrücklich konfigurierte Zuordnungen; Namensgleichheit mit lokalen Gruppen genügt nicht. Synchronisation und zulässige Rechte müssen überprüfbar sein. |
| Deaktivierung | Anwendungskonto sperren, Sitzungen und API-Zugänge widerrufen, ausstehende Benutzeraufträge stoppen beziehungsweise erneut bewerten. Historische Urheberschaft bleibt erhalten. |
| LDAP-Entzug | Sperre im Verzeichnis wird bei erneuter Prüfung erkannt. Maximale Verzögerung für bestehende Sitzungen, Synchronisationsintervall und Fehlerverhalten sind vor Freigabe verbindlich zu entscheiden. Sofortige Erkennung wird ohne diesen Mechanismus nicht zugesagt. |
| LDAP-Ausfall | Keine neue LDAP-Anmeldung und kein stiller Wechsel auf ein lokales Passwort. Behandlung bestehender Sitzungen ausdrücklich festlegen. Ein getrenntes lokales Notfallkonto ist ein zu prüfender Betriebsweg, keine Hintertür. |

Bei Java unterstützt Spring Security LDAP-Bind, bei dem das Verzeichnis das Passwort prüft; ein Abruf des Passwort-Hashes ist dafür nicht notwendig. Diese technische Möglichkeit entscheidet weder die Benutzeranlage noch die Fachrechte. Siehe [Spring Security LDAP](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/ldap.html).

Vorgeschlagene Anmeldezustände sind „nicht angemeldet“, „erster Faktor bestätigt“, „vollständig angemeldet“ und „gesperrt“. In V1 genügt das konfigurierte Erstverfahren. Der Vertrag lässt später eine zusätzliche Prüfung zu, ohne Fachmodule auf den jeweiligen Anbieter festzulegen.

**2FA folgt zeitnah nach V1.** Verfahren, Erzwingung für ausgewählte Gruppen, Wiederherstellung und Zusammenspiel mit LDAP bleiben offen. TOTP und Wiederherstellungscodes sind ein möglicher Ausgangspunkt, keine Festlegung. Faktorwechsel und Wiederherstellung brauchen eigene Prüfungen; siehe [OWASP Multifactor Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html). V1 darf nicht als bereits mit 2FA ausgestattet beschrieben werden.

## Schnittstellen zum Architekturentwurf

Die folgenden Verträge sind fachliche Schnittstellenentwürfe, kein implementiertes API:

- Die Identitätskomponente erzeugt einen serverseitigen Zugriffskontext mit Unternehmenskennung, Benutzerkennung, Anmeldestufe und Sitzungskennung. Sitzungs- und Rechteversionen verhindern die Nutzung veralteter Freigaben.
- Die Rechtekomponente bewertet Aktion und Ressourcenmerkmale und liefert erlaubte Feldoperationen sowie intern nachvollziehbare Gründe. Fachmodule liefern die Merkmale ihrer Ressourcen, ohne der Rechtekomponente direkten Tabellenzugriff zu geben.
- Fachmodule prüfen vor jeder Mutation Rechte und Regeln. Für Listen nutzen sie berechtigungsbeschränkte Abfragen vor Pagination und Zählung; nachträgliches Ausblenden von Treffern genügt nicht.
- Hintergrundaufträge enthalten Unternehmenskontext, Auftraggeber, Aktion und relevante Versionen. Systemaufträge wie SLA-Eskalationen erhalten eine begrenzte Dienstidentität und prüfen jeden ausgehenden Empfänger separat.
- Ein Rechteänderungsereignis invalidiert Sitzungen beziehungsweise Berechtigungscaches und abhängige Vorschläge. Die verbindliche Auswertung darf bis dahin keine veralteten Freigaben akzeptieren.
- Administration zeigt im Vorschlag eine effektive Rechtesicht: Welche Gruppe erlaubt oder verbietet warum? Ein simulierter Zugriff erteilt selbst keine Rechte und wird für sensible Daten auf Metadaten begrenzt.

## Fachliche Prüfszenarien

Diese Szenarien sind geplante Abnahmefälle, keine ausgeführten Softwaretests. Holger kann insbesondere Fälle 1 bis 5 anhand der beschriebenen Kundensituation prüfen.

| Nr. | Ausgangssituation und Aktion | Erwartetes Ergebnis |
|---|---|---|
| 1 | Lea öffnet Pauls Anfrage, beide Nordwerk im Projekt Service. | Erlaubt: bestätigte Organisationssicht. |
| 2 | Lea errät die Ticketkennung einer Südtechnik-Anfrage. | Keine Anfrage, kein Titel und keine Existenzinformation über Detailantwort oder Zähler. |
| 3 | Lea öffnet eine Nordwerk-Anfrage aus einem ihr nicht zugewiesenen Projekt. | Abgewiesen trotz gleicher Kundenorganisation. |
| 4 | Zwei Gruppen erlauben und verbieten das Lesen der internen Kalkulation. | Das ausdrückliche Leseverbot gewinnt; auch API und Historie liefern keinen Wert. |
| 5 | Eine Gruppe erlaubt Lesen und Schreiben, eine andere verbietet nur Schreiben. | Lesen bleibt erlaubt; die Änderung wird abgewiesen. |
| 6 | Lea sendet das verborgene Feld zusätzlich zu einem erlaubten Titel per API. | Ganze Änderung abgewiesen; der Titel bleibt ebenfalls unverändert. |
| 7 | Lea sucht oder sortiert nach dem verborgenen Kalkulationswert. | Kein indirekter Rückschluss über Reihenfolge, Treffer oder Trefferzahl. |
| 8 | Ein interner Anhang wird über seinen direkten Link im Portal geöffnet. | Download abgewiesen; ein kundenlesbarer Anhang derselben Anfrage bleibt lesbar. |
| 9 | Ein Export wartet, danach wird die Projektmitgliedschaft entzogen. | Auftrag darf die alten Rechte nicht nutzen; vorhandener Download ist gesperrt. |
| 10 | Eine Mail enthält interne Notizen; ein externer CC-Empfänger ist eingetragen. | Keine internen Daten an den Empfänger; bei unklarer Zuordnung kein Versand. |
| 11 | Ein KI-Antwortentwurf wurde erstellt, anschließend Quelle oder Berechtigung geändert. | Alte Bestätigung genügt nicht. Erneute Prüfung und gegebenenfalls neuer Entwurf sind nötig. |
| 12 | Eine eingelesene E-Mail fordert die KI zum Freischalten interner Felder auf. | Keine Rechteänderung, keine Übernahme der Anweisung als Systemvorgabe. |
| 13 | Ein LDAP-Konto hat dieselbe E-Mail wie ein vorhandener lokaler Administrator. | Keine automatische Verknüpfung und keine Übernahme der Adminrechte. |
| 14 | LDAP ist ausgefallen, ein lokales Passwort wird für dasselbe LDAP-Konto versucht. | Kein stiller Authentifizierungswechsel. |
| 15 | Ein Konto wird lokal deaktiviert und versucht einen alten Sitzungsschlüssel. | Zugriff abgewiesen; neue Benutzeraufträge werden verhindert. |
| 16 | Eine später eingeführte 2FA-Wiederherstellung wird zweimal verwendet. | Zweite Nutzung abgewiesen; getrennte Prüfung des ersten Wiederherstellungswegs. Kein V1-Funktionsnachweis. |

## Verbleibende Entscheidungen und Übergabe

OPEN-005 ist durch die Organisationssicht und Feldkonfliktregel teilweise geklärt. Offen bleiben allgemeine Aktionsverbote, Delegation, zusätzliche externe Sichtbereiche und Mehrfachzugehörigkeit. OPEN-006 benötigt konkrete LDAP-Umgebung, Kontoanlage, Synchronisation, Sitzungsfristen und Wiederherstellung. Bei OPEN-004 ist der Release geklärt, nicht das Verfahren.

TASK-005 konkretisiert Feld- und Workflowkonflikte; TASK-006 die Empfänger- und Portalregeln; TASK-008 KI-Sichtbarkeit und Datenwege; TASK-003 die technischen Modulverträge. DEC-040 bestätigt zunächst gegenseitige KI-Reviews; konkrete Prüfzuordnung und Nachweise werden unter OPEN-017 festgelegt. Zusätzliche menschliche Prüfung bleibt offen. Die fachliche Prüfung dieses Entwurfs bleibt bei Holger. Primärquellen wurden am 13. Oktober 2026 gelesen; daraus wird keine Abnahme der vorgeschlagenen Produktregeln abgeleitet.
