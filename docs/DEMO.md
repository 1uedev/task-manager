# Vorführung mit Docker (TASK-031)

Stand 21. Oktober 2026 · Ergebnis von TASK-031 zur Prüfung (DEC-071). Dieses Dokument beschreibt das Vorführimage, die synthetischen Demodaten, den Ablauf für die fachliche Abnahme und die begrenzte Suchmessung. Es ist kein Betriebshandbuch: TLS, Sicherung und Wiederherstellung, Hochverfügbarkeit und eine Veröffentlichung des Images gehören nicht dazu.

## Was gestartet wird

| Teil | Inhalt |
|---|---|
| [Dockerfile](../Dockerfile) | Ein Image mit Backend und gebauter Oberfläche. Drei Stufen: Oberfläche mit Node 24.22.0 bauen, Backend mit Temurin 25 und der gebauten Oberfläche paketieren, Laufzeit auf Temurin 25 JRE. Basisimages mit Tag und Digest festgelegt. Läuft als eigenes Konto ohne Root, ohne Passwort, Schlüssel oder Daten im Image. |
| [compose.yaml](../compose.yaml) | PostgreSQL 18.6 (gleicher Digest wie Tests und CI) mit dem benannten Volume `db-data`, die Anwendung mit dem Volume `files-data` für die Anhänge (TASK-051), der getrennte Prozess `worker` aus demselben Image für die Hintergrundaufträge und wiederkehrenden Aufgaben (TASK-054, ohne Webserver und ohne Port) und auf Wunsch das Werkzeug `demo`. Die Anwendung ist nur auf diesem Rechner erreichbar (`127.0.0.1`). |
| `.env.example` | Vorlage für die zwei Pflichtwerte. Ohne sie verweigert Compose den Start; es gibt keine Standardkennwörter. |
| [tools/demo/demo.py](../tools/demo/demo.py) | Synthetische Demodaten, Referenzbestand, Prüfungen und Suchmessung, ausschließlich über die öffentliche HTTP API. Nur Python Standardbibliothek. |
| [tools/demo/image-check.sh](../tools/demo/image-check.sh) | Gesamtprüfung des Images, läuft in CI als Workflow `Image`. |

Das Backend liefert die Oberfläche unter `/` vom selben Ursprung wie die API aus. Jede Antwort trägt eine Content Security Policy (Skripte und Stile nur vom eigenen Ursprung, Logo zusätzlich von https Adressen, keine Einbettung in fremde Seiten), `nosniff` und `no-referrer`. Gebaute Dateien unter `/assets/` dürfen ein Jahr im Browser bleiben, weil ihr Name einen Inhaltsschlüssel trägt; alles andere wird bei jedem Aufruf neu geprüft, damit ein neues Image beim nächsten Neuladen wirkt.

## Starten

Voraussetzung ist Docker mit Compose (geprüft mit Docker 29.9 und Compose 5.6) und ein freier Port 8080 auf dem Rechner, auf dem auch der Browser läuft.

```sh
git clone https://github.com/1uedev/task-manager.git
cd task-manager
cp .env.example .env          # beide Werte in .env setzen, siehe unten
docker compose up -d --build  # baut das Image beim ersten Mal, einige Minuten
docker compose run --rm app bootstrap-admin admin
```

In `.env` stehen:

- `TASK_MANAGER_DB_PASSWORD`: Passwort des Datenbankkontos der Anwendung, ein beliebiger langer Zufallswert.
- `TASK_MANAGER_DEMO_PASSWORD`: Passwort, das das Demowerkzeug dem Administrator und allen synthetischen Konten gibt, mindestens 12 Zeichen. Es wird nirgends gespeichert außer in `.env`.
- Optional `TASK_MANAGER_PORT`, wenn 8080 belegt ist.
- Optional `TASK_MANAGER_SECRET_KEY` für den Helpdesk (TASK-057), 32 zufällige Bytes in Base64 (`openssl rand -base64 32`). Ohne ihn startet alles wie bisher, nur lassen sich keine Postfächer mit Passwort anlegen. Webserver und `worker` erhalten denselben Wert.

`bootstrap-admin` legt das erste Konto mit allen Verwaltungsrechten an und zeigt einmalig ein Einmalpasswort. Danach entweder im Browser unter <http://localhost:8080> anmelden und das Passwort selbst wechseln, oder die Demodaten gleich mit dem Einmalpasswort anlegen lassen:

```sh
docker compose --profile tools run --rm demo seed --profile demo --one-time-password EINMALPASSWORT
```

Das Werkzeug ersetzt dabei das Einmalpasswort durch `TASK_MANAGER_DEMO_PASSWORD`. Wurde das Passwort vorher im Browser auf denselben Wert gesetzt, entfällt `--one-time-password`.

Die Oberfläche funktioniert nur unter `http://localhost:…` auf demselben Rechner. Die Sitzungscookies tragen das Präfix `__Host-` und das Merkmal `Secure`; Browser nehmen sie ohne TLS nur für `localhost` an. Zugriff von einem anderen Rechner braucht einen TLS Zugang davor, der nicht Teil dieser Aufgabe ist.

Weitere Befehle:

| Befehl | Wirkung |
|---|---|
| `docker compose stop` / `docker compose start` | Anhalten und fortsetzen; Daten und Sitzungen bleiben. Laufende Hintergrundaufträge haben 15 Sekunden zum Abschluss und werden dann unterbrochen; wer an dieser Unterbrechung endet, wartet wieder, ohne einen Versuch zu verbrauchen. Docker beendet den Prozess nach 30 Sekunden (`stop_grace_period`) hart. |
| `docker compose down` | Container entfernen; die Volumes und damit alle Daten und Anhänge bleiben. |
| `docker compose down -v` | Container **und Daten samt Anhängen** entfernen, für einen frischen Start. |
| `docker compose run --rm app reset-admin-password admin` | Neues Einmalpasswort für ein Konto mit Kontenverwaltung, wenn niemand mehr angemeldet ist. |
| `docker compose run --rm app rebuild-search` | Suchprojektion aus den Fachdaten neu aufbauen. |
| `docker compose logs app` | Protokoll der Anwendung mit Korrelationskennung je Anfrage. |
| `docker compose logs worker` | Protokoll der Hintergrundaufträge; ein Fehler steht dort mit der Referenz, die die Seite Hintergrundaufträge zeigt. |

### Sicherung der Vorführumgebung

Die Vorführumgebung ist kein Betrieb und die Sicherung kein Nachweis für A18. Wer Daten und Anhänge einer Vorführung trotzdem aufheben will, sichert Datenbank und Dateispeicher zusammen und **erst die Datenbank, dann die Dateien**; die Gründe stehen in [backend/README.md](../backend/README.md), Abschnitt Dateien und Anhänge, „Betrieb und Sicherung“. Die Befehle sind aus dem Verzeichnis dieser Datei heraus gedacht; Compose nennt das Volume der Anhänge `task-manager_files-data`, `IMAGE` ist das PostgreSQL Abbild aus compose.yaml (es enthält `tar`).

| Schritt | Befehl |
|---|---|
| Anwendung anhalten (empfohlen) | `docker compose stop app worker` |
| Datenbank sichern | `docker compose exec -T db pg_dump -U taskmanager -Fc taskmanager > taskmanager.dump` |
| Anhänge sichern | `docker run --rm -v task-manager_files-data:/data/files:ro -v "$PWD":/backup IMAGE tar czf /backup/files-data.tar.gz -C /data files` |
| Anwendung fortsetzen | `docker compose start app worker` |
| Zurückspielen | Anwendung anhalten, `docker compose exec -T db pg_restore -U taskmanager -d taskmanager --clean --if-exists < taskmanager.dump`, dann `docker run --rm -v task-manager_files-data:/data/files -v "$PWD":/backup IMAGE tar xzf /backup/files-data.tar.gz -C /data`, danach `docker compose start app worker` und im Protokoll des Ausführers (`docker compose logs worker`) oder auf der Seite Hintergrundaufträge den ersten Aufräumlauf prüfen. |

`tar` läuft im Abbild als `root` und behält Besitzer und Rechte; die Dateien gehören danach weiter dem Konto der Anwendung (10001). Wer statt des benannten Volumes ein eigenes Verzeichnis einbindet, gibt es diesem Konto (`chown -R 10001:10001 <verzeichnis>`).

## Demodaten

`seed --profile demo` legt über die API an:

| Konto | Gruppe | Projekte | Sichtbar und änderbar |
|---|---|---|---|
| `admin` | Verwaltung Demo und alle Verwaltungsrechte | alle vier | alles |
| `anna` | Projektleitung | WEB, APP, HELP | alle Felder lesen und schreiben, Projekte verwalten |
| `ben` | Entwicklung | WEB, APP | alle Felder lesen; Titel, Beschreibung, Status, Zuständigkeit und Tags schreiben |
| `carla` | Lesen ohne Beschreibung | WEB | nur Titel, Typ, Status und Tags lesen, nichts ändern |
| `david` | Entwicklung | HELP | wie ben, aber nur im Helpdesk |

Dazu vier Projekte (WEB Webportal Relaunch, APP Mobile App, HELP Interner Helpdesk, INF Infrastruktur, das nur der Administrator sieht), 43 Tickets in allen drei Status, fünf Tags im Unternehmenskatalog und zwei bis drei je Projektkatalog. Für die Servicefristen (TASK-068) kommen im Projekt HELP der Kunde Nordwerk Maschinenbau, der Kalender „Bürozeiten“ (Montag bis Freitag 8 bis 17 Uhr in Europe/Berlin, Ausnahmen an Weihnachten) und die Regel „Helpdesk Standard“ mit 120 Minuten bis zur Erstreaktion und 480 Minuten bis zur Lösung für jede Priorität dazu, mit den Eskalationsstufen (TASK-070) 80 % für die Gruppe Projektleitung und 100 % für Verwaltung Demo je Uhr; „Erledigt“ hat dort die SLA Art gelöst. Der Ausführer prüft die Stufen jede Minute; in den Bürozeiten erscheinen HELP-1 bis HELP-3 nach 96 Minuten unter „SLA Eskalationen“. HELP-1 bis HELP-3 tragen den Kunden und zeigen ihre Fristen, HELP-1 mit Priorität Hoch. Gruppen mit allen Feldern sehen auch Kunde, Kontakt und Priorität. Für das Kundenportal (TASK-076) bietet HELP den Typ Aufgabe im Portal an; dazu kommen der zweite Kunde Südtechnik und je ein Portalkonto mit Kontakt: `lea.nordwerk` für Nordwerk Maschinenbau und `tom.suedtechnik` für Südtechnik, beide mit dem Demopasswort. HELP-4 gehört Südtechnik. An HELP-1 stehen eine Portalantwort des Supports und ein Beitrag von Lea; die Gruppe Verwaltung Demo hat dafür `helpdesk.reply` und die Leseregel für Mails. Mit `lea.nordwerk` angemeldet sieht man nur HELP-1 bis HELP-3, mit `tom.suedtechnik` nur HELP-4. Für das Wiki (TASK-093, TASK-094) haben Projektleitung und Verwaltung Demo `wiki.read`, `wiki.edit` und `wiki.publish`, Entwicklung `wiki.read` und `wiki.edit`, Lesen ohne Beschreibung nur `wiki.read`; im Wiki von HELP stehen die Seite „Helpdesk Handbuch“ und darunter „Serviceanfrage ergänzen“, beide auf Deutsch veröffentlicht. Die zweite Seite hat einen englischen Entwurf „Complete a service request“: wer mit englischer Oberfläche nur lesen darf, bekommt die deutsche Fassung mit Sprachhinweis, wer bearbeiten darf, wählt die englische Fassung über die Sprachleiste. Für die Anforderungen (TASK-096 bis TASK-098) hat HELP die eigenen Felder „Bereich“ (Einfachauswahl Kundenportal oder Interne Bearbeitung, Pflicht für „Zur Prüfung“) und „Aufwand in Tagen“; die Gruppe Anforderungen mit dem Administrator und der Projektleitung hat `requirement.read`, `requirement.edit` und `requirement.approve` mit allen Feldern. ANF-1 „Kunden sehen den Stand ihrer freigegebenen Supportanfragen“ hat drei Kriterien; Revision 1 ist freigegeben und mit HELP-1 und der Wikiseite „Serviceanfrage ergänzen“ verknüpft, danach hat ein geänderter Aufwand Revision 2 als Entwurf eröffnet. HELP-1 zeigt darum am Feld „Anforderungen“ den Hinweis auf die neuere Revision. Alle Namen und Texte sind erfunden. Ein zweiter Lauf gegen dieselbe Datenbank bricht ab, statt doppelte Daten anzulegen.

## Ablauf für die fachliche Abnahme

Der erste vorführbare Abschnitt aus DEC-051 lautet: anmelden, Projekt öffnen, Ticket bearbeiten, Tags vergeben, Ticket wiederfinden. Mit den Demodaten in zwei Browserfenstern (eines davon privat):

1. Als `anna` anmelden. Startseite, Projekte WEB, APP und HELP; INF fehlt, weil anna dort kein Mitglied ist.
2. WEB öffnen, ein Ticket öffnen, Titel und Status ändern, speichern. Die Historie zeigt Version, Zeit und anna als Urheberin.
3. Im Ticket einen vorhandenen Tag zuordnen und einen neuen Projekttag anlegen.
4. Unter Suche nach einem Wort aus dem neuen Titel oder nach `WEB-` und der Nummer suchen; der Treffer erscheint sofort.
5. Im zweiten Fenster als `carla` anmelden. Nur WEB ist sichtbar, Beschreibung und Zuständigkeit fehlen, es gibt keine Bearbeitungsfelder. Die Suche nach „Drucker“ findet nichts, obwohl es im Helpdesk ein solches Ticket gibt.
6. Konflikt: anna und ben öffnen dasselbe WEB Ticket, beide ändern den Titel. Der zweite Speichervorgang meldet den Konflikt und behält die eigene Eingabe.
7. Als `admin` unter Gruppen und Rechte der Gruppe „Entwicklung“ das Schreiben des Titels nehmen. ben sieht beim nächsten Speichern die Ablehnung und danach nur noch lesbare Felder.
8. Im Ticket unter Anhänge eine Datei auswählen oder auf die Fläche ziehen: sie erscheint mit Größe und Urheberin, lässt sich als Datei herunterladen und taucht in der Historie auf. Eine Datei `programm.exe` wird abgelehnt. Entfernen fragt vorher; die Historie behält den Eintrag. Als `carla` fehlt der Abschnitt ganz, weil ihre Gruppe das Feld nicht lesen darf.
9. Unter Konten ein neues Konto anlegen; das Einmalpasswort erscheint einmal. Mit ihm anmelden, der Passwortwechsel ist Pflicht.
10. `docker compose down` und `docker compose up -d`: Daten und Anmeldungen bleiben, das Sitzungsende ändert sich nicht. Der erste Start nach `bootstrap-admin` baut die Suchprojektion einmal neu auf, weil das Unternehmen erst mit diesem Befehl entsteht; bei den Demodaten dauert das Sekunden, beim Referenzbestand mit 90.000 Tickets etwa zwei Minuten, in denen die Anwendung noch nicht bereit meldet.

## Nachweise zu den zehn Punkten aus DEVELOPMENT_START.md

| Punkt | Nachweis | Grenze |
|---|---|---|
| 1 Reproduzierbarer Build | Workflows Backend, Frontend und Image bauen aus sauberem Checkout mit festen Versionen; Image mit festgelegten Basisimages. | Kein reproduzierbares Byte für Byte Image. |
| 2 Konten, Pflichtwechsel, Sitzungsfrist | Backendtests aus TASK-024/TASK-039; `verify` prüft 24 Stunden Standard und gleiches Sitzungsende nach Neustart des Images. Browsertests aus TASK-030 für Erstanmeldung. | Verkürzung und Verlängerung nur durch die Backendtests. |
| 3 Zwei Benutzer, Rechteentzug | `verify`: carla und david sehen nur ihre Projekte und Felder, Änderung durch carla 403. Rechteentzug während der Bearbeitung in TASK-030 im echten Browser. | |
| 4 Konfiguration, Anlage, Historie, Konflikt | `verify`: veraltete Änderung mit 412; Backendtests TASK-026/027 für Rückabwicklung. | |
| 5 Tags | Backendtests TASK-028/042. | Keine eigene Vorführprüfung über die Demodaten hinaus. |
| 6 Suche | `verify`: neue Fassung sofort gefunden, verborgene Projekte ohne Treffer; Backendtests TASK-029. | |
| 7 Sprachen, Tastatur, Zustände im Browser | Browsertests TASK-030 (60 Fälle); Browserlauf gegen das Image ohne CSP Verstoß. | |
| 8 Docker Start und Neustart | Workflow `Image`: Start, Ablehnung ohne Einstellungen, Neustart mit erhaltenen Daten und unverändertem Sitzungsende. | Kein Betriebsnachweis, keine Sicherung. |
| 9 Suchmessung | Abschnitt Suchmessung unten. | Siehe Grenzen der Messung. |
| 10 Getrennte KI Prüfung | Steht aus; Holger erhält den Ablauf oben. | |

## Suchmessung

Ziel war ein nachvollziehbarer Messlauf mit dem Profil aus [OPERATIONS.md](OPERATIONS.md) und den Messklassen aus [TAGS_SEARCH.md](TAGS_SEARCH.md) auf dem tatsächlich vorhandenen Umfang. Die Ziele wurden vorher aus diesen Dokumenten übernommen; sie bleiben Vorschläge (OPEN-014).

### Verfahren

```sh
docker compose down -v && docker compose up -d --build
docker compose run --rm app bootstrap-admin admin
docker compose --profile tools run --rm demo seed --profile reference --one-time-password EINMALPASSWORT
docker compose --profile tools run --rm demo measure            # 15 Sitzungen, 10 Minuten Aufwärmen, 30 Minuten Messung
```

- **Bestand**, über die API erzeugt in 17,5 Minuten: 100 Referenzkonten in drei Gruppen, dazu das Administratorkonto aus `bootstrap-admin` (101 Konten) (Projektleitung, Bearbeitung, Lesen ohne Beschreibung und Zuständigkeit), 20 Projekte mit je 25 Mitgliedern und dem Administrator, 90.000 Tickets mit Titel und ein bis vier Sätzen Beschreibung aus einem synthetischen Wortschatz mit sehr häufigen und seltenen Wörtern, 5.000 Tags (1.000 im Unternehmenskatalog, 200 je Projekt), 448.682 Tagzuordnungen an Tickets (rund fünf je Ticket) und 200 an Projekten, 988.514 Historieneinträge (rund elf je Ticket, aus Anlage, Statuswechseln und Tagzuordnungen). Datenbank 641 MB, davon Suchprojektion 209 MB. Nach dem Erzeugen einmal `VACUUM ANALYZE`.
- **Last**: 15 gleichzeitig angemeldete Konten, über alle drei Gruppen verteilt, je Person eine Aktion im Mittel alle fünf Sekunden (exponentiell verteilte Pause). Anteile nach OPERATIONS.md: Lesen und Listen 45, Suche 25, Ticketänderung 15; Zeitbuchung (10) und Wiki (5) gibt es noch nicht, die übrigen 85 Teile werden hochgerechnet. Personen ohne Schreibrecht ändern nicht; ihr Anteil verteilt sich auf die anderen Aktionen. Ticketänderung heißt Lesen und dann Titeländerung mit `If-Match`; gemessen werden das Speichern allein (PATCH) und die ganze Aktion aus Lesen und Speichern.
- **Messklassen der Suche**: Kennung (`R05-1234`), ein Wort, zwei Wörter, sehr häufiger Begriff (in rund 60 Prozent der Titel und jeder Beschreibung), kurzer Präfix aus zwei Buchstaben, ohne Treffer, Wort mit Projekt- und Statusfilter, komplexe Suche mit Treffern, komplexe Suche selektiv und Tagvorschlag. Die komplexe Suche mit Treffern geht von einem sichtbaren Ticket mit Tags aus: ein Wort seines Titels, sein Projekt unter bis zu drei, sein Status und Typ unter weiteren und ein bis zehn seiner eigenen Tags, also nur Tags aus seinem Projektkatalog oder dem Unternehmenskatalog. Mindestens dieses Ticket passt, solange niemand den Titel inzwischen geändert hat. Die selektive Klasse kombiniert drei zufällige Projekte mit ein bis zehn zufälligen sichtbaren Tags und findet meist nichts. Zu jeder Suche wird die Trefferzahl erfasst. Jede Suche mit 20 Treffern je Seite und allen Rechtefiltern der jeweiligen Person.
- **Messpunkt**: Antwortzeit beim Aufrufer im selben Docker Netz, vom Senden bis zur gelesenen Antwort. Das ist eine obere Schranke der Serverzeit; Browser, Darstellung und Netz sind nicht enthalten. Gezählt werden nur erfolgreiche Antworten, abgewiesene (Konflikt, fehlendes Recht) und Fehler getrennt. Geht die Antwort auf eine Änderung verloren, bricht sie mitten im Inhalt ab oder kommt sie nach der Wartezeit, sendet das Werkzeug die Änderung nicht erneut und zählt sie als Fehler mit unklarem Ausgang; nur wenn schon der Verbindungsaufbau scheitert, steht fest, dass nichts gespeichert wurde; nur lesende Anfragen werden einmal wiederholt.
- **Maschine**: eine virtuelle Maschine mit 4 vCPU (Intel Xeon, 2,1 GHz) und 16 GB, Linux, Docker 29.9. Anwendung, PostgreSQL und Messwerkzeug liefen gemeinsam darauf, ohne Grenzen für die Container. Das entspricht der unverbindlichen Referenz von 4 vCPU und 16 GB nur in der Größe; Plattenart und Nachbarlast der virtuellen Maschine sind unbekannt.

### Ergebnis vom 22. Oktober 2026

Nachmessung nach der unabhängigen Prüfung (Bericht in PR #135) auf demselben Bestand: 15 Sitzungen, 30 Minuten nach 10 Minuten Aufwärmen, 5.385 Aktionen, keine Fehler, keine Abweisungen, kein unklarer Ausgang. „mit Treffern“ zählt die Anfragen mit mindestens einem Treffer; Median und Höchstwert beziehen sich auf `total`, also alle erlaubten Treffer, nicht nur die erste Seite.

| Messklasse | Messwerte | p50 ms | p95 ms | p99 ms | Ziel p95 | erfüllt | mit Treffern | Treffer Median | Treffer max |
|---|---:|---:|---:|---:|---:|---|---:|---:|---:|
| Ticketliste (50 Tickets) | 1361 | 8,3 | 11,8 | 16,5 | 500 ms | ja | | | |
| Ticketdetail | 1691 | 7,8 | 11,1 | 14,9 | 500 ms | ja | | | |
| Ticketänderung Speichern (PATCH) | 651 | 12,0 | 16,7 | 22,2 | 1 s | ja | | | |
| Ticketänderung gesamt (Lesen und Speichern) | 651 | 20,2 | 27,3 | 33,0 | kein eigenes Ziel | | | | |
| Suche Kennung | 194 | 14,4 | 20,3 | 23,4 | 500 ms | ja | 194 von 194 | 1 | 1 |
| Suche ein Wort | 273 | 113,2 | 192,5 | 222,9 | 500 ms | ja | 273 von 273 | 14.344 | 21.803 |
| Suche zwei Wörter | 217 | 84,4 | 166,7 | 184,9 | 500 ms | ja | 217 von 217 | 2.741 | 6.803 |
| Suche häufiger Begriff | 202 | 93,0 | 127,9 | 146,3 | 500 ms | ja | 202 von 202 | 10.801 | 13.582 |
| Suche kurzer Präfix | 133 | 27,6 | 142,5 | 169,4 | 500 ms | ja | 133 von 133 | 1.053 | 22.500 |
| Suche ohne Treffer | 121 | 12,1 | 18,4 | 27,0 | 500 ms | ja | 0 von 121 | 0 | 0 |
| Suche mit Filtern | 271 | 32,4 | 65,1 | 72,1 | 500 ms | ja | 271 von 271 | 774 | 2.229 |
| Komplexe Suche mit Treffern | 113 | 22,9 | 43,0 | 53,5 | 1 s | ja | 113 von 113 | 1 | 25 |
| Komplexe Suche selektiv | 37 | 23,5 | 55,3 | 61,1 | 1 s | ja | 3 von 37 | 0 | 10 |
| Tagvorschlag | 121 | 5,1 | 7,7 | 9,7 | 200 ms | ja | | | |

Die komplexe Suche mit Treffern fand in jedem Fall mindestens ein Ticket. Weil alle gewählten Tags zugleich zutreffen müssen, bleibt die Treffermenge klein (Median 1, höchstens 25); große Treffermengen mit vielen Filtern deckt die Messung damit nicht ab, sie stecken in „Suche mit Filtern“ und den Wortsuchen. Der Vergleichswert von 1 s für reguläre Speicherung gilt für das Speichern allein; für die ganze Aktion aus Lesen und Speichern nennt OPERATIONS.md kein eigenes Ziel. Alle Werte bleiben Vorschläge (OPEN-014), keine zugesagten Produktgrenzen.

### Ergebnis vom 21. Oktober 2026

Erster Lauf vor der Prüfung, gleicher Bestand und gleiche Last: 5.365 Messwerte, keine Fehler, alle p95 Ziele eingehalten, größter Suchwert „Suche ein Wort“ mit p95 209,6 ms. Er ist in zwei Punkten überholt: Die damalige Klasse „Komplexe Suche“ (p95 56,9 ms) kombinierte zufällige Projekte und Tags, deren Treffer nicht erfasst wurden; die Prüfung zeigte, dass solche Kombinationen fast immer nichts finden. Ihr Wert entspricht der heutigen selektiven Klasse und belegt keine komplexe Suche mit Treffern. „Ticketänderung“ (p95 12,0 ms) war allein die Zeit des Speicherns. Die Tabelle steht in [measurement-20261021-193921.md](measurements/task-031/measurement-20261021-193921.md).

### Zusätzliche Laststufen

| Lauf | Aktionen je Sekunde | Ergebnis |
|---|---:|---|
| 30 Sitzungen am 21. Oktober, sonst gleiches Profil, 2 Minuten Aufwärmen, 10 Minuten Messung | 5,9 | 3.523 Messwerte, keine Fehler. Alle Ziele eingehalten; größter p95 Wert „Suche ein Wort“ mit 239 ms. |
| 15 Sitzungen **ohne Pause** am 21. Oktober, 1 Minute Aufwärmen, 5 Minuten Messung | 207 | 62.127 Messwerte, keine Fehler, 5 erwartete Versionskonflikte. Rund siebzigmal die Normallast. „Suche ein Wort“ (p95 596 ms) und „Suche zwei Wörter“ (p95 550 ms) überschreiten das Ziel von 500 ms, alle anderen Klassen bleiben darunter, Ticketliste und Detail bei p95 unter 70 ms. |

Beide Stufen liefen mit dem Werkzeugstand vor der Prüfung; für ihre komplexe Suche und Ticketänderung gilt dasselbe wie für den ersten Lauf. Die aggregierten Ergebnisse aller Läufe stehen unter [measurements/task-031](measurements/task-031/) (je eine Markdown Tabelle und eine JSON Datei mit Maschine, Parametern und p50/p95/p99 je Klasse).

### Messung mit Wiki, Anforderungen und Übersicht (TASK-104)

Messung zu Paket A14 (DEC-134, Regeln 4 bis 8 von TASK-104) am 11. November 2026. Ziele nach Regel 6, serverseitig als p95 unter der Referenzlast: Kennung und Vorschläge höchstens 200 ms, reguläre Suche erste Seite höchstens 500 ms, komplexe Suche und Übersicht „Alle“ höchstens 1 s. Damit gilt für „Suche Kennung“ jetzt 200 ms statt 500 ms. Im Browser vom Auslösen bis zur sichtbaren ersten Seite höchstens 1 s (Regel 7).

```sh
docker compose --profile tools run --rm demo seed --profile reference --one-time-password EINMALPASSWORT   # mit Wiki und Anforderungen
docker compose --profile tools run --rm demo seed --profile reference-content                             # nur Wiki und Anforderungen, auf einem Bestand aus TASK-031
docker compose --profile tools run --rm demo measure
cd frontend && TASK_MANAGER_URL=http://localhost:8080 TASK_MANAGER_DEMO_PASSWORD=... npm run measure:search -- --repetitions 200
```

- **Bestand**: der Referenzbestand aus TASK-031 (17,4 Minuten) und zusätzlich über die öffentliche API in 2,2 Minuten: 2.000 Wikiseiten in den 20 Projekten als Seitenbaum mit höchstens vier Ebenen, davon 630 mit veröffentlichter englischer Fassung, 242 mit offenem Entwurf nach der Veröffentlichung, 87 nie veröffentlicht und 40 archiviert; 10.000 Anforderungen mit je ein bis acht Kriterien (45.000 Kriterien), davon 962 in Prüfung und 2.923 freigegeben, 975 davon mit neuer Revision im Entwurf. Texte aus demselben synthetischen Wortschatz wie die Tickets. Rechte in drei Gruppen wie bei den Tickets: Verwaltung mit allen Wiki und Anforderungsaktionen, Bearbeitung mit `wiki.edit` und `requirement.edit`, Lesen mit `wiki.read` und `requirement.read` ohne die Beschreibung. Datenbank 749 MB, davon Suchprojektion 277 MB, danach `VACUUM ANALYZE`. Zählung in [reference-content.json](measurements/task-104/reference-content.json).
- **Last**: wie in TASK-031, 15 Sitzungen, 5 Sekunden mittlere Denkzeit, 10 Minuten Aufwärmen, 30 Minuten Messung, dazu der Wikianteil von OPERATIONS.md (5) als Lesen von Wikiseiten und die neuen Suchklassen. Personen ohne Recht in einem Bereich führen dessen Klassen nicht aus; wer nur liest, sucht keine Entwürfe.
- **Neue Messklassen**: Wikiseite lesen, Wiki ein Wort (veröffentlichte Fassungen), Wiki Entwürfe, Anforderung ein Wort, Anforderung Kennung (`R05 ANF-123`), Anforderung mit Filtern (Projekt, Status, zwei Prioritäten), Übersicht „Alle“ (ein Wort, zwei Wörter oder ein häufiger Begriff) und Schnellsuche mit Kennung (`/search/all` mit einer Ticketkennung oder zu 30 Prozent einer Anforderungskennung, erfolgreich nur mit Direkttreffer). Bei „Alle“ zählt „Treffer“ die Summe der Anzahlen aller Bereiche.
- **Browsermessung**: Chromium 153 mit dem Konto `ref001` (Verwaltung), je Klasse 10 Durchläufe zum Aufwärmen und 200 gemessene. Jeder Durchlauf öffnet seinen Reiter neu, trägt ein häufiges Wort ein, und gemessen wird vom Klick auf „Suchen“ bis zum ersten sichtbaren Treffer oder „Keine Treffer“; bei der Schnellsuche von Enter bis zur Überschrift der geöffneten Ticketseite. Die Zeit nimmt der Messprozess um die Steuerung des Browsers herum, sie ist eine obere Schranke. Die Browsermessung lief nach der Servermessung ohne gleichzeitige Last.
- **Maschine und Abweichung**: dieselbe Art virtueller Maschine wie in TASK-031, 4 vCPU (Intel Xeon, 2,3 GHz) und 16 GB. Weil Docker Hub die Abrufe der Basisimages drosselte (HTTP 429), lief die Anwendung nicht im Vorführimage, sondern als dasselbe Jar mit Java 21 und PostgreSQL 16 direkt auf der Maschine, Hintergrundaufträge im selben Prozess. Messwerkzeug, Browser, Anwendung und Datenbank teilten sich die Maschine. Der Vergleich mit dem Image (Java 25, PostgreSQL 18.6) steht aus.

**Ergebnis der Servermessung** ([measurement-20261111-230003.md](measurements/task-104/measurement-20261111-230003.md)): 5.415 Aktionen, keine Fehler, keine Abweisungen. Alle Klassen erfüllen ihr Ziel.

| Messklasse | Messwerte | p50 ms | p95 ms | p99 ms | Ziel p95 | erfüllt | mit Treffern | Treffer Median | Treffer max |
|---|---:|---:|---:|---:|---:|---|---:|---:|---:|
| Ticketliste (50 Tickets) | 1188 | 6,5 | 9,3 | 11,7 | 500 ms | ja | | | |
| Ticketdetail | 1499 | 6,1 | 8,5 | 10,7 | 500 ms | ja | | | |
| Ticketänderung Speichern (PATCH) | 531 | 9,2 | 13,2 | 17,7 | 1 s | ja | | | |
| Ticketänderung gesamt | 531 | 15,7 | 21,9 | 26,8 | kein eigenes Ziel | | | | |
| Suche Kennung | 176 | 12,0 | 17,7 | 21,5 | 200 ms | ja | 176 von 176 | 1 | 1 |
| Suche ein Wort | 225 | 95,7 | 181,0 | 205,4 | 500 ms | ja | 225 von 225 | 12.060 | 21.803 |
| Suche zwei Wörter | 188 | 59,1 | 166,9 | 193,9 | 500 ms | ja | 188 von 188 | 1.274 | 6.800 |
| Suche häufiger Begriff | 205 | 87,4 | 120,4 | 135,3 | 500 ms | ja | 205 von 205 | 10.825 | 13.600 |
| Suche kurzer Präfix | 118 | 18,8 | 126,1 | 155,3 | 500 ms | ja | 118 von 118 | 214 | 22.500 |
| Suche ohne Treffer | 134 | 10,0 | 16,0 | 22,5 | 500 ms | ja | 0 von 134 | 0 | 0 |
| Suche mit Filtern | 243 | 34,9 | 63,3 | 73,2 | 500 ms | ja | 243 von 243 | 751 | 2.230 |
| Komplexe Suche mit Treffern | 87 | 17,8 | 28,0 | 29,9 | 1 s | ja | 86 von 87 | 1 | 39 |
| Komplexe Suche selektiv | 29 | 16,5 | 42,4 | 48,0 | 1 s | ja | 0 von 29 | 0 | 0 |
| Tagvorschlag | 126 | 5,0 | 8,0 | 9,7 | 200 ms | ja | | | |
| Wikiseite lesen | 285 | 11,0 | 15,1 | 17,2 | 500 ms | ja | | | |
| Wiki ein Wort | 73 | 46,6 | 69,3 | 78,3 | 500 ms | ja | 73 von 73 | 416 | 470 |
| Wiki Entwürfe | 21 | 48,8 | 65,3 | 68,7 | 500 ms | ja | 21 von 21 | 487 | 489 |
| Anforderung ein Wort | 61 | 29,2 | 45,1 | 52,2 | 500 ms | ja | 61 von 61 | 2.358 | 2.499 |
| Anforderung Kennung | 26 | 24,3 | 33,8 | 39,6 | 200 ms | ja | 26 von 26 | 1 | 1 |
| Anforderung mit Filtern | 37 | 13,1 | 34,4 | 37,3 | 500 ms | ja | 37 von 37 | 39 | 250 |
| Übersicht „Alle“ | 101 | 180,3 | 268,8 | 288,4 | 1 s | ja | 101 von 101 | 13.026 | 24.766 |
| Schnellsuche mit Kennung | 62 | 72,6 | 97,9 | 109,7 | 200 ms | ja | 62 von 62 | 1 | 1 |

„Alle“ führt die vier Bereichssuchen nacheinander in einer Transaktion aus; ihre Zeit liegt deshalb etwa bei der Summe aus Ticket, Projekt, Wiki und Anforderungssuche und wird von der Ticketsuche mit vielen Treffern bestimmt. Die Schnellsuche fragt ebenfalls alle vier Bereiche und braucht daher mehr als die reine Ticketkennung, bleibt aber unter 200 ms. Die seltenen Klassen haben wenige Messwerte (21 bis 101); ihr p95 ist entsprechend unsicher.

**Ergebnis der Browsermessung** ([browser-20261111-230324.md](measurements/task-104/browser-20261111-230324.md)): je 200 Messwerte, keine Fehler, keine Fehler der Seite, alle unter dem Ziel von 1 s.

| Messklasse | Messwerte | p50 ms | p95 ms | p99 ms | Ziel p95 | erfüllt |
|---|---:|---:|---:|---:|---:|---|
| Tickets ein Wort | 200 | 88,2 | 225,8 | 232,1 | 1 s | ja |
| Wiki ein Wort | 200 | 129,7 | 148,7 | 162,2 | 1 s | ja |
| Anforderung ein Wort | 200 | 97,9 | 148,6 | 158,7 | 1 s | ja |
| Übersicht „Alle“ | 200 | 218,1 | 320,2 | 342,3 | 1 s | ja |
| Schnellsuche mit Kennung bis zur Ticketseite | 200 | 113,6 | 130,4 | 151,8 | 1 s | ja |

Kein Ziel wurde verfehlt, eine Nachbesserung nach Regel 8 war nicht nötig. Ein Probelauf der Browsermessung auf einem kleinen Bestand während des Erzeugens der Daten (rund 90 Anfragen je Sekunde) lag bei „Alle“ bei 838 ms p95 aus nur fünf Werten; unter Volllast der Maschine wird es also deutlich knapper. Dieser Probelauf ist kein Ergebnis.

### Grenzen der Messung

- Ein Lauf von 30 Minuten statt zusätzlich des zweistündigen Dauerlaufs; keine Hintergrundlast durch Mailabruf, SLA oder Sicherung, weil es diese Teile noch nicht gibt.
- Kommentare und Portalkonten fehlen im Bestand; Wiki und Anforderungen sind seit TASK-104 enthalten (DEC-050).
- Wahrgenommene Suche im Browser misst seit TASK-104 die Browsermessung ohne gleichzeitige Last und ohne Netz zwischen Browser und Server. Kalt- und Warmzustand, abgebrochene Browserabfragen und Abfragepläne sind nicht getrennt erfasst.
- Eine einzelne virtuelle Maschine, Messwerkzeug auf derselben Maschine. Die Werte sind ein Anhaltspunkt für diese Maschine, keine Zusage für die Referenzhardware und kein Vergleich mit Easy8, Redmine oder Jira.
