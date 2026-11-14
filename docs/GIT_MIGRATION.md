# Git-Verknüpfung und erster Datenimport

Stand 15. Oktober 2026 · Planungsentwurf für TASK-009 · Git Teil umgesetzt mit TASK-111 bis TASK-113 (DEC-141 bis DEC-143), Einrichtung und Betrieb im Abschnitt [Einrichtung und Betrieb](#einrichtung-und-betrieb-task-113)

Dieses Dokument konkretisiert REQ-GIT-001 bis REQ-GIT-003, REQ-MIG-001, REQ-MIG-002 und REQ-API-002. Grundlage sind die [Anforderungen](../REQUIREMENTS.md), das [Rechtekonzept](PERMISSIONS.md) und das [Fachmodell](DOMAIN_MODEL.md). Git-Verknüpfung und Datenmigration sind unterschiedliche Funktionen: Die erste zeigt Informationen aus einem Quellcodesystem, die zweite übernimmt später einen Bestand aus einem Projektmanagementsystem.

**Bestätigt:** V1 enthält Zuordnung und Anzeige ohne Git-Schreibaktionen. Am 15. Oktober hat Holger GitHub.com, GitLab.com und selbst betriebenes GitLab für V1 gewählt; GitHub Enterprise Server folgt später (DEC-036). Migration ist erst zeitnah nach V1 vorgesehen, zuerst aus Redmine; Easy Redmine/Easy8 und Jira werden anschließend geprüft (DEC-037). Alle weiteren Detailregeln, Mengen und Prüffälle sind Vorschläge. Unterstützte Quellversionen werden vor der jeweiligen Implementierung festgelegt und praktisch qualifiziert.

## Git-Umfang in V1

| Objekt | Vorgeschlagene Anzeige | Eindeutige Referenz |
|---|---|---|
| Repository / GitLab-Projekt | Name, Anbieter und berechtigter Link | Anbieterinstanz und stabile Repository-Kennung; ein veränderlicher Pfad allein genügt nicht. |
| Commit | Vollständige Kennung, kurze Nachricht, Autoranzeige und Zeitpunkte | Repository-Kennung und vollständige Commit-Kennung; Kurzkennungen nur zur Darstellung. |
| Pull / Merge Request | Nummer, Titel, Autoranzeige, Zustand und Quelllink | Anbieterinstanz, Repository-Kennung und Objektkennung; lokale Nummer allein genügt nicht. |

Vorschlag: Nutzer verknüpfen diese Objekte manuell mit einem Ticket oder einer Anforderung. Mehrere Links sind möglich; dieselbe Kombination aus Fachobjekt und Git-Objekt wird nur einmal gespeichert. Entfernen löst die lokale Beziehung, verändert aber kein Git-Objekt. Historie dokumentiert wer welche Beziehung wann angelegt oder entfernt hat, ohne unberechtigte Metadaten erneut offenzulegen.

V1 enthält weder Repository-Klon noch integrierten Quellcodebrowser. Diffs, Dateien, Branchlisten, CI-Protokolle und Releases sind mögliche spätere Erweiterungen. Eine Commit-Nachricht mit „Fixes 42“ ändert keinen Ticketstatus. Automatische Erkennung von Ticketnummern ist ebenfalls ein späterer Kandidat. Die Zuordnung allein beweist weder Umsetzung noch bestandene Tests oder fachliche Freigabe einer Anforderung.

**Beispiel:** Anforderung ANF-17 ist mit Ticket DEV-42 und einem Merge Request verknüpft. Wird der Merge Request zusammengeführt, aktualisiert die Git-Anzeige dessen Zustand. Das Ticket und die Anforderungsfreigabe bleiben durch ihre eigenen Regeln bestimmt. Der Leser kann die Verbindung nachvollziehen, soweit er beide Datenquellen lesen darf.

## Verbindung und Berechtigungen

Administratoren registrieren Anbieterinstanzen, freigegebene Repository-Zuordnungen und Anwendungskonfiguration. Eine Git-Anbindung ist kein zusätzlicher Anmeldeweg für das Produkt; lokale Anmeldung und LDAP bleiben der bestätigte V1-Umfang. Nach Anmeldung kann ein Benutzer sein Git-Konto ausdrücklich mit seiner Produktidentität verbinden. Eine gleiche E-Mail-Adresse stellt diese Verbindung nicht automatisch her.

| Anbieter | Authentifizierungsvorschlag | Grenze |
|---|---|---|
| GitHub.com | GitHub App mit ausgewählten Repositories und ausschließlich benötigten Leserechten; Benutzerzugriff über User Access Token. | Installationszugriff allein belegt keine Rechte des betrachtenden Benutzers. Konkrete Endpunkte und minimal nötige Rechte vor Umsetzung prüfen. |
| GitLab.com / Self-Managed | Registrierte OAuth-Anwendung und benutzergebundener Zugriff mit lesendem API-Umfang. | GitLab `read_api` kann mehr für den Benutzer zugängliche Ressourcen umfassen als die lokal ausgewählten Projekte. Der Adapter begrenzt zusätzlich auf die konfigurierte Liste. |
| GitHub Enterprise Server | Gemeinsamer Vertrag vorbereitet; spätere gesonderte Qualifikation. | Keine V1-Kompatibilitätszusage für selbst betriebene GitHub-Versionen. |

Die GitHub-Dokumentation beschreibt User Access Tokens als Schnittmenge der Rechte und Ressourcen von App und Benutzer. Daraus folgt die Empfehlung, Benutzeranzeigen nicht über ein pauschal privilegiertes Installationstoken zu berechtigen. [GitHub: User Access Tokens](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app)

GitLab unterscheidet den lesenden Umfang `read_api` von `api` mit Schreibzugriff sowie Git-over-HTTP-Zugriff. Für diesen Metadatenumfang ist lesender API-Zugriff der zu prüfende Kandidat. [GitLab: OAuth und Scopes](https://docs.gitlab.com/integration/oauth_provider/)

Die Anwendung prüft beim Verknüpfen: Recht am Ticket beziehungsweise der Anforderung, Recht zum Anlegen der Beziehung, administrative Repository-Freigabe und Zugriff der ausführenden Person auf das konkrete Git-Objekt. Bei jeder späteren Anzeige wird der Zugriff erneut im Benutzerkontext beim Anbieter geprüft. Ohne erfolgreichen Nachweis werden keine gespeicherten Titel, Autoren, Commit-Nachrichten, Repository-Pfade oder URLs ausgeliefert. Unbekannte und unberechtigte Ziele erzeugen dieselbe neutrale Meldung.

Diese konservative Regel gilt auch bei Anbieterfehlern: Das Ticket bleibt benutzbar; die Git-Information erscheint vorübergehend nicht verfügbar. Es gibt keinen Rückfall auf ein höher berechtigtes Dienstkonto. Abmeldungen, Kontosperren und Tokenentzug invalidieren ausstehende Benutzerabrufe. Bereits betrachtete Informationen lassen sich nicht zurückrufen.

Vorschlag V1: Git-Metadaten werden nicht in globale Volltextsuche, allgemeine Exporte, Kunden-E-Mails oder KI-Kontexte übernommen. Das begrenzt zusätzliche Auslieferungswege und entspricht dem separaten [V1-KI-Umfang](AI_USE_CASES.md). Eine spätere Aufnahme braucht dieselbe Quellenprüfung. Externe Portalnutzer erhalten standardmäßig keine Git-Anzeige; gezielt erteilte Produktrechte würden weiterhin einen passenden eigenen Quellzugriff erfordern. Auch Linktitel, Zähler und Änderungsprotokolle dürfen verborgene Informationen nicht indirekt verraten.

## Aktualisierung und Anbietergrenzen

Vorgeschlagener V1-Einstieg ist Abruf bei Anzeige mit einer ausdrücklichen Aktualisierungsaktion. Die Fachseite lädt unabhängig von der Git-Antwort. Zeitstempel unterscheiden Quellenänderung und erfolgreichen Abruf. Ein flüchtiger Zwischenspeicher darf einen aktuellen positiven Zugriffsnachweis nicht ersetzen; er wird nach Anbieterinstanz, Benutzer und Objekt getrennt. Begrenzte parallele Abrufe, Pagination und Rückoff-Zeiten verhindern unkontrollierte Last.

Ein allgemeiner Synchronisationsdienst und eingehende Webhooks sind für diesen Mindestumfang nicht erforderlich. Die Architektur trennt dennoch Anbieteradapter, Referenzen, Metadaten und spätere Ereignisverarbeitung. Falls Webhooks später eingeführt werden, benötigen sie Herkunftsprüfung, Wiederholungsschutz und anschließenden autorisierten Datenabruf. Ein Ereignis darf keine Benutzerberechtigung verleihen. Der V1-Betrieb benötigt damit für diese Git-Anbindung keinen öffentlich erreichbaren Webhook-Endpunkt; Rückleitung der Benutzerautorisierung und deren Erreichbarkeit sind je Anbieter zu prüfen.

| Fehler / Änderung | Vorgeschlagenes Verhalten |
|---|---|
| API meldet fehlenden Zugriff oder unbekanntes Objekt | Metadaten nicht ausliefern; Verbindung nicht ungefragt löschen. Kein Unterschied, der die Existenz verrät. |
| Token abgelaufen | Kontrollierte Erneuerung, soweit unterstützt; sonst Benutzerverbindung erneuern. Keine Anmeldung mit fremdem Konto. |
| Rate-Limit / Timeout | Anbieterhinweise und begrenzte Wartezeit beachten; übrige Fachseite bleibt nutzbar. Kein endloses Wiederholen. |
| Repository umbenannt | Stabile Anbieterkennung bleibt maßgeblich; neue URL nur nach zulässigem Abruf übernehmen. |
| Repository auf andere Instanz übertragen | Neue Zuordnung und Zugriffskontrolle; keine automatische Vertrauensübernahme. |
| Commit nicht mehr abrufbar | Referenz bleibt dokumentiert, Inhalt als nicht verfügbar behandeln. Keine Garantie, dass ein Git-Anbieter alte Objekte dauerhaft vorhält. |

Selbst betriebenes GitLab benötigt eine bekannte Basis-URL, freigegebenen Netzwerkweg und geprüfte Zertifikate, gegebenenfalls eine Kunden-CA. Beliebige Nutzereingaben dürfen keine Serverabrufe auslösen. Endpunkt-, DNS- und Weiterleitungsprüfung begrenzen ausgehenden Zugriff; explizit erlaubte interne GitLab-Adressen bleiben möglich. Zugangsdaten liegen geschützt außerhalb von normalen Logs und URLs. Proxy, Zertifikatsrotation, Tokenrotation und eine getestete Versionsmatrix gehören zur Installationsabnahme.

## Einrichtung und Betrieb (TASK-113)

Stand 14. November 2026 · Ergebnis von TASK-113 zur Prüfung (DEC-143). Umgesetzt sind Anbieterinstanzen, die Verbindung des eigenen Kontos, Freigaben je Projekt (TASK-111) und Verknüpfungen an Tickets und Anforderungen (TASK-112); die technischen Einzelheiten stehen in [backend/README.md](../backend/README.md), Abschnitt Git Anbindung. Dieser Abschnitt beschreibt, was beim Anbieter zu registrieren ist und was der Betrieb einstellt.

### Voraussetzungen in der Installation

| Einstellung | Zweck |
|---|---|
| `TASK_MANAGER_SECRET_KEY` | 32 zufällige Bytes in Base64 (`openssl rand -base64 32`). Verschlüsselt das Geheimnis jeder Anwendung und die Token jeder Person. Ohne Schlüssel lässt sich kein Geheimnis speichern und niemand verbinden (`git.secret_key_missing`). Der Schlüssel gehört zur Sicherung der Datenbank; ein anderer Schlüssel macht Geheimnisse und Token unlesbar, die Verbindungen stehen dann auf „erneuern“ und jedes Geheimnis muss neu eingegeben werden. |
| `TASK_MANAGER_PUBLIC_URL` | Die Adresse, unter der Browser die Anwendung erreichen, ohne Schrägstrich am Ende, etwa `https://tasks.example.com`. In der Vorführung ist sie `http://localhost:8080` (compose.yaml setzt sie aus dem Port). Ohne sie gibt es keine Rückleitung (`git.public_url_missing`). |

**Rückleitungsadresse.** Bei jedem Anbieter wird genau `TASK_MANAGER_PUBLIC_URL` plus `/api/v1/git/oauth/callback` eingetragen, in der Vorführung also `http://localhost:8080/api/v1/git/oauth/callback`. Die Seite „Git“ der Verwaltung zeigt diese Adresse an. Ändert sich die öffentliche Adresse, muss die Registrierung bei jedem Anbieter angepasst werden; bis dahin lehnt der Anbieter die Rückleitung ab.

### GitHub App für GitHub.com registrieren

Schritt für Schritt, mit einem Konto, das in der Organisation GitHub Apps anlegen darf (oder im eigenen Konto für eine Vorführung):

1. Bei GitHub unter Settings der Organisation (oder des eigenen Kontos) „Developer settings“, dann „GitHub Apps“ und „New GitHub App“ öffnen.
2. **GitHub App name**: ein bei GitHub eindeutiger Name, etwa „Task Manager Vorführung Firma“. **Homepage URL**: die öffentliche Adresse der Anwendung.
3. **Callback URL**: die Rückleitungsadresse von oben. **Expire user authorization tokens**: eingeschaltet lassen (die Anwendung erneuert Token selbst). **Request user authorization (OAuth) during installation** und **Enable Device Flow**: aus.
4. **Webhook**: „Active“ ausschalten. Die Anwendung braucht keine Webhooks und keinen von außen erreichbaren Endpunkt.
5. **Repository permissions**: „Contents“ Read-only, „Metadata“ Read-only (Pflicht), „Pull requests“ Read-only. Alle anderen Rechte und alle „Organization“ und „Account permissions“ bleiben „No access“. Die Anwendung schreibt nie in Git.
6. **Where can this GitHub App be installed?**: „Only on this account“, wenn nur Repositories dieser Organisation gebraucht werden, sonst „Any account“.
7. „Create GitHub App“. Auf der Seite der App die **Client ID** notieren (nicht die App ID) und unter „Client secrets“ mit „Generate a new client secret“ ein Geheimnis erzeugen. GitHub zeigt es nur einmal.
8. „Install App“: die App in der Organisation installieren und die Repositories wählen, die Projekte freigeben sollen („Only select repositories“ oder „All repositories“). Die Auswahl beim Freigeben zeigt nur Repositories dieser Installationen, die die verbundene Person selbst lesen darf.
9. Im Task Manager als Person mit `git.manage` unter „Git“ die Instanz „GitHub.com“ bearbeiten (oder neu anlegen, Art GitHub.com): Kennung der Anwendung = Client ID, Geheimnis = Client secret, speichern, „Prüfen“.

Jede Person verbindet danach unter „Git“ ihr eigenes Konto. GitHub fragt beim ersten Mal nach der Zustimmung zur App. Ein User Access Token sieht nur, was App und Person gemeinsam sehen dürfen; private Repositories einer Organisation also nur, wenn die App dort installiert ist und die Person Zugriff hat. Die Beispiele der Vorführung zeigen auf das öffentliche Repository dieses Projekts; ob GitHub es einer Verbindung auch ohne Installation der App im Konto `1uedev` zeigt, gehört zur Abnahme (Abnahmekriterium 2).

### Anwendung für GitLab.com registrieren

1. Bei GitLab.com für eine Gruppe unter „Settings“, „Applications“ (oder für das eigene Konto unter „Edit profile“, „Applications“) „Add new application“ öffnen.
2. **Name**: etwa „Task Manager Vorführung“. **Redirect URI**: die Rückleitungsadresse von oben.
3. **Confidential**: eingeschaltet. **Scopes**: nur `read_api`. Kein `api`, kein `read_repository`, kein `openid`.
4. „Save application“. **Application ID** und **Secret** notieren; GitLab zeigt das Geheimnis nur einmal.
5. Im Task Manager die Instanz „GitLab.com“ bearbeiten: Kennung der Anwendung = Application ID, Geheimnis = Secret, speichern, „Prüfen“.

`read_api` erlaubt der Verbindung alles zu lesen, was die Person in GitLab lesen darf. Die Anwendung begrenzt das selbst: freigegeben wird nur, was die verbundene Person lesen kann, und angezeigt wird jedes Objekt nur mit dem Token der betrachtenden Person.

### Eigenes GitLab ab Version 16.0

1. In GitLab unter „Admin Area“, „Applications“ eine Anwendung für die ganze Instanz anlegen (oder wie oben für eine Gruppe): Redirect URI wie oben, Confidential an, Scope `read_api`. In der Admin Area lässt sich die Anwendung als „Trusted“ markieren; dann entfällt die Zustimmung der Personen beim Verbinden.
2. Im Task Manager eine Instanz der Art „Eigenes GitLab“ anlegen: Adresse mit https, Rechner, optional Port und Pfad, genau so, wie GitLab sie als `external_url` kennt; Kennung und Geheimnis der Anwendung.
3. **Eigene CA**: Ist das Zertifikat von GitLab von einer eigenen CA ausgestellt, die Zertifikate der CA (Wurzel und gegebenenfalls Zwischenstufen) im PEM Format in das Feld „Eigene Zertifizierungsstelle“ eintragen, ein bis fünf Zertifikate. TLS wird immer geprüft, auch der Rechnername; es gibt keinen Schalter, die Prüfung abzuschalten. „Prüfen“ meldet ohne passende CA `git.tls_failed`.
4. **Version**: Beim Verbinden liest die Anwendung die Version; unter 16.0 lehnt sie mit `git.version_unsupported` ab. „Prüfen“ nennt die Version nur, wenn GitLab sie ohne Anmeldung verrät, was GitLab 16 nicht tut.
5. **Netzweg**: Der Container der Anwendung muss den Rechner auflösen und erreichen können. Weiterleitungen folgt die Anwendung nicht (`git.redirect_refused`): die Adresse muss genau die sein, unter der GitLab ohne Umleitung antwortet, also mit https und ohne fehlenden Pfadteil.

Der Lauf gegen ein echtes GitLab 16 (unten) prüft genau diesen Weg mit einer eigenen CA.

### Proxy

Ausgehende Abrufe gehen nur an eingetragene Instanzen und nutzen den Proxy der JVM. Im Container setzt der Betrieb ihn über `JAVA_TOOL_OPTIONS`, für Webserver und `worker` gleich, zum Beispiel `-Dhttps.proxyHost=proxy.intern -Dhttps.proxyPort=3128 -Dhttp.nonProxyHosts=gitlab.intern|*.intern`; ein eigenes GitLab im internen Netz gehört in `http.nonProxyHosts`. Ein Proxy mit Anmeldung wird nicht unterstützt. Ein Proxy, der TLS aufbricht, braucht seine CA im Feld „Eigene Zertifizierungsstelle“ der Instanz; bei GitHub.com und GitLab.com wird das Feld dafür ebenso genutzt.

### Verhalten bei Tokenentzug und Trennen

| Ereignis | Verhalten |
|---|---|
| Die Person entzieht der App beim Anbieter die Zustimmung, der Anbieter läuft das Token ab, oder die Anwendung wird beim Anbieter gelöscht | Beim nächsten Abruf (nach höchstens 60 Sekunden aus dem Zwischenspeicher der Person, mit „Aktualisieren“ sofort) lehnt der Anbieter das Token ab. Die Anwendung versucht genau eine Erneuerung; misslingt sie, steht die Verbindung auf „erneuern“, beide Token sind gelöscht, und jede Verknüpfung zeigt nur ihre Art und „Verbindung erneuern“, ohne Pfad, Titel oder Adresse. Nichts davon ist gespeichert. Der GitLab Lauf zeigt das, indem er die Anwendung in GitLab löscht. |
| „Trennen“ im Task Manager | Die Token werden sofort gelöscht, der Zwischenspeicher der Person geleert. Beim Anbieter bleibt die Zustimmung bestehen, bis die Person sie dort entzieht: GitHub unter „Settings“, „Applications“, „Authorized GitHub Apps“, GitLab unter „Preferences“, „Applications“. |
| Konto im Task Manager deaktiviert, Instanz entfernt, Adresse oder Kennung der Anwendung geändert | Alle betroffenen Verbindungen enden in derselben Transaktion, ihre Token sind gelöscht. |
| Geheimnis der Anwendung beim Anbieter erneuert | Das neue Geheimnis in der Instanz eintragen; bestehende Verbindungen bleiben, bis ein Token erneuert werden muss. |

### Vorführung

Das Vorführimage legt mit `demo seed --profile demo` die Instanzen „GitHub.com“ und „GitLab.com“ ohne Geheimnis an (Kennung `noch-nicht-registriert`) und gibt den Demogruppen die Regeln des Felds `git`. `tools/demo/git-examples.sql` speichert danach Beispielverknüpfungen auf öffentliche Repositories: an WEB-1 das Repository 1uedev/task-manager, den Merge Commit von Pull Request 317 und diesen Pull Request, an ANF-1 im Helpdesk Merge Request 1 von gitlab-org/gitlab und einen Commit daraus, dazu die Freigaben der beiden Repositories für WEB und HELP. Ohne Verbindung erscheinen sie als „nicht verfügbar“; „Git Konto verbinden“ meldet, dass die Instanz noch nicht vollständig eingerichtet ist. Nach der Registrierung wie oben und dem Eintragen von Kennung und Geheimnis verbindet jede Person ihr Konto und sieht die Beispiele mit Titel und Zustand. Ablauf in [docs/DEMO.md](DEMO.md).

### Lauf gegen ein echtes GitLab 16

`tools/git/gitlab-check.sh` startet mit Docker Compose GitLab CE 16.11 mit HTTPS und einem Zertifikat einer für den Lauf erzeugten CA, das Vorführimage und PostgreSQL, legt in GitLab ein Token des Administrators und eine Anwendung mit `read_api` an und führt [tools/git/gitlab_check.py](../tools/git/gitlab_check.py) aus: „Prüfen“ ohne CA meldet `git.tls_failed`, mit CA gelingt es; Verbinden mit Anmeldung bei GitLab und PKCE; Freigabe eines Repositorys einer Untergruppe; Verknüpfen von Repository, Commit über einen kurzen SHA und Merge Request am Ticket und des Merge Requests an einer Anforderung, Duplikat und fremder Rechner abgewiesen; nach dem Zusammenführen in GitLab zeigt „Aktualisieren“ den Zustand „merged“, nach dem Umbenennen den neuen Pfad unter derselben Kennung (G05); nach dem Löschen der Anwendung in GitLab nur noch „Verbindung erneuern“. Alle Kennwörter, Schlüssel und Token entstehen für den Lauf und verschwinden mit ihm. In CI läuft er als Workflow „GitLab 16“ bei Änderungen am Git Modul oder am Lauf, wöchentlich und auf Anforderung; er ist keine Pflichtprüfung, weil GitLab mehrere Minuten zum Start braucht. Lokal genügt `tools/git/gitlab-check.sh` mit Docker und mindestens 8 GB Arbeitsspeicher; ein Lauf dauert mit fertigem Image einige Minuten, fast alles davon ist der Start von GitLab.

## Erster Import nach V1: Redmine

Vorschlag ist ein einmaliger, administrativ gesteuerter Import ausgewählter Projekte aus einem festgelegten Redmine-Stand. Das ist keine laufende bidirektionale Synchronisation. V1 bereitet stabile interne Kennungen, Herkunftskennungen und eine begrenzte Importschnittstelle vor; es enthält noch keinen Importer.

| Datenart | Vorgeschlagener erster Umfang | Behandlung von Grenzen |
|---|---|---|
| Projekte, Tracker, Status, Prioritäten | Geprüfte Zuordnung auf Zielprojekte, Typen und Status | Workflowregeln nicht blind kopieren; unbekannte Zuordnung blockiert den betroffenen Import. |
| Benutzer und Gruppen | Herkunftsbezug und Autorenzuordnung; Zielkonten zunächst deaktiviert | Keine Passwort-, Token- oder ungeprüfte LDAP-Übernahme. Zusammenführen gleicher Namen/E-Mails nur nach Prüfung. |
| Tickets und einfache Beziehungen | Offene und geschlossene Vorgänge mit ausgewählten Standardfeldern | Fremdschlüssel über stabile Herkunftskennungen auflösen; fehlende Ziele ausweisen. |
| Kommentare und Änderungshistorie | Zugängliche Journale mit ursprünglichem Autor/Zeitpunkt und Importkennzeichnung | Historische Ereignisse nicht als neue Live-Aktionen abspielen. Private oder unklare Sichtbarkeit bleibt gesperrt. |
| Zeitbuchungen | Datum, Dauer, Tätigkeit, Autor und Ticket-/Projektbezug | Summen vor/nach Import nach Projekt und Benutzer abgleichen; Rundungen explizit ausweisen. |
| Anhänge | Erreichbare Dateien mit Größe, Prüfsumme und Herkunft | Fehlende oder abgelehnte Dateien einzeln melden; kein stilles erfolgreiches Gesamtergebnis. |
| Wiki | Aktueller Inhalt ausgewählter Seiten und zugehörige Dateien | Übernahme als Entwurf mit Herkunft; Übersetzung und Veröffentlichung nicht automatisch ableiten. Vollständige Wiki-Historie zunächst separat bewerten. |
| Eigene Felder | Begrenzte freigegebene Typen: Text, Zahl, Datum, Auswahl, Benutzerbezug | Nicht abbildbare Werte im geschützten Importbericht erhalten; kein stiller Textverlust oder Freigeben verborgener Werte. |
| Rollen und Mitgliedschaften | Explizite geprüfte Zielmatrix | Rechte nicht eins zu eins voraussetzen; fehlende Zuordnung sperrt Zugriff. Kein Portalzugang durch bloßen Import. |

Nicht im vorgeschlagenen ersten Umfang: Plugin-Daten, individuelle Skripte, Redmine-Automationen, API-Schlüssel, Mailserverzugänge, Berechtigungsgarantien für unbekannte Erweiterungen und vollständige historische Wiki-Konvertierung. Aus Redmine importierte Tickets werden nicht allein aufgrund eines Trackernamens zu formal freigegebenen Anforderungen.

Easy Redmine/Easy8 muss wegen eigener Module und Erweiterungen gesondert inventarisiert werden; die Redmine-Priorisierung ist keine automatische Kompatibilitätszusage. Für Jira sind später insbesondere Betriebsvariante, Export/API, App-Felder, Historie und Rechteabbildung zu prüfen. Beide Quellen erhalten erst nach dieser Prüfung einen eigenen Umfang und Lieferentscheid.

## Gewinnung der Quelldaten

Empfohlen wird ein dokumentierter, lesender Exportweg über Redmines API mit einer für den vereinbarten Umfang berechtigten Identität. Er darf keine Daten im Quellsystem verändern. Ob ergänzende, administrativ bereitgestellte Exporte nötig sind, entscheidet ein Quelleninventar. Direkter Zugriff auf eine produktive Redmine-Datenbank ist kein stillschweigender Importweg.

Redmines API liefert bei Ticketlisten standardmäßig nur offene Vorgänge und arbeitet mit Pagination. Ein vollständiger Projektimport muss daher Statusumfang und alle Seiten ausdrücklich behandeln. Journale, Anhänge und Beziehungen können für einen Vorgang gezielt angefordert werden. [Redmine: Issues API](https://www.redmine.org/projects/redmine/wiki/Rest_Issues)

Die Wiki-API beschreibt Seitenlisten, aktuelle Inhalte und den Abruf einer angegebenen älteren Version. Das belegt noch keine vollständige oder verlustfreie Migration beliebiger Wiki-Bestände und Makros. Diese muss am gewählten Quellsystem nachgewiesen werden. [Redmine: Wiki API](https://www.redmine.org/projects/redmine/wiki/Rest_WikiPages)

Vor der Extraktion werden Quellversion, Plugins, Textformat, Projektumfang und Zugriffsmöglichkeiten festgehalten. Ein Abgleich mit dem Quelladministrator ist nötig: Ein eingeschränktes API-Ergebnis kann fehlende Rechte statt eines leeren Bestands bedeuten. Ohne bestätigte Ausgangsmengen und Sichtbarkeitsprüfung darf kein vollständiger Import behauptet werden. Alle hier genannten Primärquellen wurden am 15. Oktober 2026 geprüft.

## Probelauf, Wiederholung und Umschaltung

1. **Inventar und Zuordnung:** Quellumfang festhalten, Objektmengen ermitteln, Status/Felder/Rechte zuordnen und Ausschlüsse bestätigen. Eine Quellinstanz erhält eine dauerhafte Herkunftskennung.
2. **Extraktion:** Lesenden Export mit Manifest, Prüfsummen und Extraktionszeitpunkten erzeugen. Die abschließende Extraktion erfolgt aus einem konsistenten, für Änderungen gesperrten Quellstand oder einer geeigneten isolierten Kopie; bloße Zeitfilter sind kein Vollständigkeitsbeweis.
3. **Geschützter Probelauf:** Daten in einen nicht für normale Benutzer sichtbaren Bereich importieren. Kein Versand, keine SLA-Eskalation, keine automatischen KI-Aufrufe und keine Benutzereinladung werden ausgelöst.
4. **Abgleich:** Mengen je Objektart, Zeitbuchungssummen, Dateiprüfsummen, Beziehungen und ausgewählte Inhalte prüfen. Negative Rechtefälle mit internen und externen Identitäten ausführen. Originalzeitpunkt und Importzeitpunkt bleiben unterscheidbar.
5. **Freigabe und Umschaltung:** Verantwortlicher prüft Bericht und bekannte Lücken. Erst die ausdrückliche Freigabe macht den vereinbarten Umfang sichtbar; Benutzeraktivierung und Wiki-Veröffentlichung bleiben eigene Schritte. Alte SLA-Ereignisse werden nicht nachträglich versandt; laufende Fristen brauchen eine ausdrücklich entschiedene Startregel.
6. **Nachkontrolle:** Quellsystem zunächst lesend vorhalten, Ergebnis und Fehler dokumentieren, Exportmaterial nach vereinbarter Frist löschen. Erst nach fachlicher Abnahme folgt eine getrennte Entscheidung zur Stilllegung der Quelle.

Wiederholungsschutz verwendet Quellinstanz, Objektart und Quell-ID; zusätzlich wird pro Lauf die verarbeitete Quellversion beziehungsweise Prüfsumme aufgezeichnet. Ein identischer erneuter Lauf erzeugt keine doppelten Tickets, Zeiten oder Dateien. Veränderte Quelldaten können vor Umschaltung den Importstand kontrolliert ersetzen. Nach Umschaltung geänderte Zielobjekte werden nie ungefragt überschrieben. Fortsetzungsmarken gelten nur innerhalb desselben bestätigten Exportstands.

Vor Umschaltung kann ein fehlgeschlagener Probelauf vollständig verworfen werden, weil noch kein Live-Betrieb darauf aufsetzt. Nach Aufnahme neuer Arbeit ist eine Rückkehr zur Quelle kein einfacher Löschvorgang: Neue Tickets, Zeiten und Nachrichten müssen zuvor abgeglichen werden. Ein Rückfallplan benennt Schreibstopp, Verantwortlichen und Umgang mit dieser zwischenzeitlichen Arbeit. Der Import ändert die Quelle nicht.

## Fachliche Prüffälle

Die Git Fälle G01 bis G08 sind mit TASK-111 bis TASK-113 umgesetzt und geprüft (Tests gegen nachgebaute Anbieter in `GitApiTest`, Browser Tests und der Lauf gegen GitLab 16); die Migrationsfälle M01 bis M08 bleiben Abnahmeentwürfe, es wurde keine Migration ausgeführt.

| Fall | Erwartetes Ergebnis |
|---|---|
| G01 GitHub-/GitLab-Objekt zuordnen | Berechtigtes Ticket und Repository ergeben eine eindeutige Beziehung; wiederholte Eingabe erzeugt kein Duplikat. |
| G02 Ticket lesbar, Repository nicht lesbar | Keine Git-Metadaten, URL, Zähler oder Historienvorschau des verborgenen Objekts. |
| G03 Quellzugriff nach erfolgreicher Anzeige entzogen | Nächster Abruf verweigert Metadaten; kein Rückfall auf Dienstkonto oder alten Cache. |
| G04 PR/MR zusammengeführt | Git-Zustand aktualisiert; Ticket und Anforderungsfreigabe ändern sich nicht. |
| G05 Repository umbenannt / Instanz gewechselt | Stabile Identität bei Umbenennung; neuer Vertrauensentscheid bei Instanzwechsel. |
| G06 Rate-Limit, abgelaufener Token oder Ausfall | Begrenzte Fehlerbehandlung; Ticket bleibt bedienbar, Geheimnisse bleiben verborgen. |
| G07 Interne GitLab-CA und manipulierte URL | Freigegebener interner Dienst funktioniert; andere Ziele und unsichere Weiterleitungen werden abgewiesen. |
| G08 Suche, Portal und KI | Keine Umgehung der Git-Rechte über zusätzliche Ausgabekanäle. |
| M01 Offene/geschlossene Tickets und mehrere API-Seiten | Abgestimmte Ausgangsmenge wird vollständig erfasst; fehlende Quellrechte sind kein leerer Erfolgsfall. |
| M02 Identischer Import zweimal / Unterbrechung | Keine doppelten Objekte; Fortsetzung gehört zum selben Exportstand. |
| M03 Private Notiz oder unbekannte Rollenabbildung | Inhalt bleibt gesperrt; keine automatische Portalfreigabe oder Kontenaktivierung. |
| M04 Zeitbuchungen und Dateien | Summen und Prüfsummen stimmen; Rundungen, fehlende Dateien und Größenüberschreitungen sind einzeln ausgewiesen. |
| M05 Wiki mit Makro und nicht abbildbarem Feld | Verlust wird gemeldet; Wiki bleibt Entwurf, keine stille Veröffentlichung. |
| M06 Import historischer Nachrichten und Fristen | Keine Mail, Einladung, KI-Generierung oder vergangene Eskalation wird ausgelöst. |
| M07 Quelle ändert sich während Extraktion | Kein ungeprüfter konsistenter Abschluss; abschließender Quellstand wird neu abgesichert. |
| M08 Nach Umschaltung geänderte Zielobjekte | Wiederholung überschreibt keine neue Arbeit; Rückfall erfordert expliziten Abgleich. |

OPEN-010 ist mit DEC-141 bis DEC-143 entschieden: GitHub App mit User Access Tokens und OAuth Anwendung mit `read_api` bei GitLab, Repositories, Commits, Pull und Merge Requests, manuelle Zuordnung mit Abruf bei Anzeige, eigenes GitLab ab 16.0; G01 bis G08 sind mit den Tests von TASK-111 und TASK-112 und dem GitLab Lauf von TASK-113 belegt. OPEN-016 enthält weiterhin Quellversion, vollständige Mappingregeln, Aufbewahrung und Importmengen; Redmine als erste Quelle ist entschieden. TASK-010 ergänzt Netzwerk, Dateien, Sicherung und Lastmessung. Die Detailvorschläge benötigen fachliche und technische Prüfung vor Umsetzung.
