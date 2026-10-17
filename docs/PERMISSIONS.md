# Identität und Berechtigungen

Stand 17. Oktober 2026 · TASK-004, ergänzt durch TASK-018 und TASK-019 · Entwurf zur fachlichen und technischen Prüfung

Dieser Entwurf konkretisiert REQ-ACL-001 bis REQ-ACL-005, REQ-AUTH-001 bis REQ-AUTH-005 und REQ-PORTAL-003 aus den [zentralen Anforderungen](../REQUIREMENTS.md). Der ursprüngliche Entwurf wurde von Codex erstellt; der damalige Claude-Aufruf scheiterte an der Anmeldung und lieferte keinen Entwurf. Für die Ergänzungen vom 17. Oktober wird der tatsächliche Prüfstand separat bei TASK-018 und TASK-019 in TODO.md geführt; daraus folgt keine nachträgliche Gesamtprüfung der ursprünglichen Fassung.

## Bestätigte Vorgaben und offene Ausgestaltung

Holger hat am 13. Oktober 2026 drei Entscheidungen getroffen: Externe sehen standardmäßig die Anfragen ihrer Kundenorganisation, innerhalb ihrer freigegebenen Projekte; ein ausdrückliches Feldverbot gewinnt gegenüber einer Freigabe durch eine andere Gruppe; Zwei-Faktor-Authentifizierung folgt zeitnah nach V1. Diese Vorgaben stehen unter DEC-027 bis DEC-029 in [DECISIONS.md](../DECISIONS.md).

Ebenfalls bestätigt sind frei anlegbare Gruppen, projektspezifische Rechte, getrennte Feldsichtbarkeit und Änderbarkeit, lokale Anmeldung und LDAP. Alle nachfolgenden Algorithmen, Rollenbeispiele, technischen Schnittstellen und Abnahmefälle sind Vorschläge zur Umsetzung dieser Vorgaben. Insbesondere ist ein allgemeiner Vorrang von Verboten für sämtliche Aktionen damit noch nicht beschlossen.

Seit dem 17. Oktober ist zusätzlich eine konfigurierbare automatische Abmeldung nach Stunden bestätigt (DEC-045); DEC-048 legt ausschließlich feste Laufzeit seit Anmeldung fest. DEC-049 erlaubt Tag-Erstellung auch berechtigten externen Portalbenutzern. Tag-Sichtbarkeit, Verwaltungs- und Suchrechte werden im [V1-Tag- und Suchentwurf](TAGS_SEARCH.md) konkretisiert. Nicht ausdrücklich bestätigte Detailregeln bleiben Vorschläge.

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

1. Identität und der zum Zugangsweg gehörende Ausführungskontext müssen gültig sein. Interaktive UI-/Cookie-API-Aufrufe benötigen ein aktives Konto und eine aktive Anwendungssitzung; Integrationen ein aktives Konto beziehungsweise eine aktive Dienstidentität und ihren gesondert gültigen API-Zugang. Bereits verbindlich angenommene Benutzeraufträge verwenden ihren begrenzten, nicht widerrufenen Auftragskontext und ein weiterhin aktives Konto, ohne eine noch laufende Browsersitzung zu verlangen. Explizite Systemaufgaben besitzen einen eigens autorisierten Dienstkontext. Keiner dieser Wege ersetzt die folgenden aktuellen Rechteprüfungen. Unternehmenskontext und Ressource müssen zusammenpassen.
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

## Konfigurierbare automatische Abmeldung

**Bestätigt:** Der Kunde stellt eine feste Sitzungsdauer in Stunden seit erfolgreicher Anmeldung ein (REQ-AUTH-005, DEC-045, DEC-048). V1 enthält keine zusätzliche Inaktivitätsfrist. Der frühere Zwei-Fristen-Vorschlag aus TASK-018 ist durch diese Auswahl ersetzt. Die nachfolgenden Einstell-, Regeländerungs- und UX-Details bleiben Vorschläge.

V1 erhält im Vorschlag eine Unternehmensrichtlinie, verwaltet über ein eigenes Administrationsrecht. Die Oberfläche erlaubt Stundenwerte mit klarer Einheit, auch für kürzere Bruchteile einer Stunde; validierte positive Werte und dokumentierte Grenzen ersetzen eine mehrdeutige Null. Standardwert, Minimal-/Maximalwert und eine mögliche vollständige Deaktivierung des Zeitablaufs bleiben ausdrücklich zu entscheiden. Gruppenspezifische Ausnahmen werden nicht still in V1 eingeführt.

| Aspekt | Festgelegter Kern / vorgeschlagene Erläuterung |
|---|---|
| Fester Beginn | Erfolgreiche interaktive Anmeldung; Aktivität, Polling oder Erneuern der Sitzungskennung setzt den Beginn nicht zurück. |
| Zeitablauf | Ausschließlich die konfigurierte Dauer seit Anmeldung. Beispiel: Anmeldung 08:00, Dauer 8 Stunden → Ablauf um 16:00, unabhängig davon, ob seit 10:00 keine Aktivität stattfand oder um 15:59 noch geschrieben wird. |
| Unabhängige Beendigung | Manuelle Abmeldung, Kontosperre oder expliziter Sitzungswiderruf können früher wirken; sie sind keine Inaktivitätsfrist. |

Acht Stunden sind ein Rechenbeispiel, kein gewählter Standardwert. Laufzeiten sind tatsächliche Zeitdauern, keine Geschäftszeiten und kein sprachabhängiges Kalenderdatum.

Das Backend erzwingt Fristen auf Basis seiner Zeitquelle; ein Browser-Timer ist nur eine Bedienhilfe. Bei abgelaufener Sitzung erfolgt vor geschütztem Lesen/Schreiben eine allgemeine 401-Antwort mit geeignetem Produktcode. Längere schreibende Vorgänge prüfen den noch gültigen Ausführungskontext erneut am verbindlichen Autorisierungsschritt vor der Fachänderung. Bereits zuvor dauerhaft angenommene Hintergrundaufträge werden durch einen Browser-Timeout nicht automatisch widerrufen: Sie benötigen einen gesonderten begrenzten Auftragskontext und aktuelle Konto-/Objektrechte gemäß TECHNICAL_CONTRACTS.md. Eine noch unbestätigte KI-Änderung wird niemals nach Ablauf automatisch übernommen. Kontosperre oder Rechteentzug bleiben vom bloßen Sitzungsablauf getrennt und verhindern unzulässige Ausführung.

Navigation, Such-/Bearbeitungsaktionen, aktives Schreiben, Polling und technische Token-Erneuerung verändern den festen Sitzungsbeginn nicht. Ein Aktivitätssignal oder Inaktivitätszähler ist für diesen V1-Ablauf nicht erforderlich. Der Server bestimmt Beginn und Ende, nicht ein frei übergebener Client-Zeitstempel. Eine offene Wiki-Eingabe soll vor Ablauf eine sichtbare Warnung erhalten; nach Ablauf ist auch bei aktivem Schreiben eine neue Anmeldung erforderlich.

Alle Tabs derselben Sitzung und deren Cookie-API-Aufrufe folgen derselben Frist; andere separat angemeldete Geräte behalten ihre eigenen Sitzungen. Lokale Anmeldung und LDAP benutzen dieselbe Anwendungsrichtlinie. Ein weiterhin gültiger LDAP-Nachweis verlängert die abgelaufene Anwendungssitzung nicht still. Integrations-API-Schlüssel haben eigene Laufzeiten und sind kein „angemeldet bleiben“-Ersatz für die Weboberfläche. Dauerhafte Browser-Anmeldung oder spätere SSO-Verfahren dürfen die festgelegte Maximaldauer nicht unbemerkt umgehen.

**Regeländerungen im Vorschlag:** Eine Verkürzung wirkt auf bestehende Sitzungen spätestens bei der nächsten geschützten Anfrage; bereits überschrittene Grenzen führen zur Abmeldung. Eine Verlängerung gilt erst nach neuer Anmeldung, damit laufende Sitzungen nicht heimlich erweitert werden. Die Verwaltung sieht vor Bestätigung die Auswirkung und das Risiko der eigenen Abmeldung. Richtlinie und Sitzungsversion werden konsistent ausgewertet; Werte werden nicht pro Tab zwischengespeichert und ungeprüft weiterverwendet.

Warnung und Wiedereinstieg müssen verständlich sein. Nach Ablauf werden Fachinhalte verdeckt; neue Abfragen enden. Ein bereits manuell gespeicherter Entwurf bleibt gespeichert. Ein noch nicht gespeicherter Formularpuffer kann im selben Tab vorübergehend im Arbeitsspeicher zurückgehalten werden, ohne ihn auf dem Sperrbildschirm anzuzeigen oder in ungeschütztem Local Storage abzulegen. Er darf nur nach Anmeldung derselben Identität und erneuter Rechte-/Versionsprüfung wieder angeboten werden; andernfalls wird er verworfen. Ein Neuladen oder Schließen kann ungesicherte Eingaben verlieren, worauf die Warnung hinweist. Fremde Konten erhalten keine alten Formulardaten, und Wiederanmeldung löst keine automatische Speicherung oder Sendung aus.

Die bereits in TASK-018 herangezogene [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#automatic-session-expiration), gelesen am 17. Oktober 2026, unterscheidet Inaktivitäts- und absolute Frist. Für dieses Produkt hat Holger ausschließlich die absolute Frist gewählt (DEC-048); daraus folgt keine pauschale Übernahme aller Empfehlungen der Quelle. Konkrete Einstell- und UX-Details bleiben projektbezogene Vorschläge.

| Fall | Später nachzuweisen, kein bereits ausgeführter Test |
|---|---|
| S01 | Beispiel 08:00 + 8 Stunden läuft um 16:00 ab, auch bei Aktivität um 15:59. |
| S02 | Anmeldung 08:00 mit beispielhaft 8 Stunden: Inaktivität seit 10:00 führt um 11:15 zu keiner zeitbedingten Abmeldung; Sitzung läuft bis 16:00, sofern kein unabhängiger Beendigungsgrund eintritt. |
| S03 | Navigation, Schreiben, Polling und technische Erneuerung verschieben das Beispielende 16:00 nicht; manueller Widerruf oder Kontosperre wirken weiterhin früher. |
| S04 | Offener zweiter Tab und direkter Cookie-API-Aufruf scheitern nach Ablauf; erneute Anmeldung setzt einen neuen Sitzungsbeginn. |
| S05 | Verkürzung meldet bereits überfällige Sitzungen bei nächstem Zugriff ab; Verlängerung belebt keine abgelaufene Sitzung wieder. |
| S06 | Lokale und LDAP-Sitzung folgen derselben Regel; Zeitzonen-/Sommerzeitwechsel verändert keine tatsächliche Dauer. |
| S07 | Ablauf während Wiki-/Ticketbearbeitung verdeckt Inhalte; Wiederanmeldung als anderer Nutzer zeigt keinen alten Puffer. |
| S08 | Nach Ablauf keine neue KI-Bestätigung; schon verbindlich angenommener Auftrag läuft nur mit seinem eigenen zulässigen Kontext und aktuellen Rechten. |

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
