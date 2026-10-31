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

Dazu vier Projekte (WEB Webportal Relaunch, APP Mobile App, HELP Interner Helpdesk, INF Infrastruktur, das nur der Administrator sieht), 43 Tickets in allen drei Status, fünf Tags im Unternehmenskatalog und zwei bis drei je Projektkatalog. Für die Servicefristen (TASK-068) kommen im Projekt HELP der Kunde Nordwerk Maschinenbau, der Kalender „Bürozeiten“ (Montag bis Freitag 8 bis 17 Uhr in Europe/Berlin, Ausnahmen an Weihnachten) und die Regel „Helpdesk Standard“ mit 120 Minuten bis zur Erstreaktion und 480 Minuten bis zur Lösung für jede Priorität dazu, mit den Eskalationsstufen (TASK-070) 80 % für die Gruppe Projektleitung und 100 % für Verwaltung Demo je Uhr; „Erledigt“ hat dort die SLA Art gelöst. Der Ausführer prüft die Stufen jede Minute; in den Bürozeiten erscheinen HELP-1 bis HELP-3 nach 96 Minuten unter „SLA Eskalationen“. HELP-1 bis HELP-3 tragen den Kunden und zeigen ihre Fristen, HELP-1 mit Priorität Hoch. Gruppen mit allen Feldern sehen auch Kunde, Kontakt und Priorität. Alle Namen und Texte sind erfunden. Ein zweiter Lauf gegen dieselbe Datenbank bricht ab, statt doppelte Daten anzulegen.

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

### Grenzen der Messung

- Ein Lauf von 30 Minuten statt zusätzlich des zweistündigen Dauerlaufs; keine Hintergrundlast durch Mailabruf, SLA oder Sicherung, weil es diese Teile noch nicht gibt.
- Kommentare, Wiki, Anforderungen und Portalkonten fehlen im Bestand und in der Suche; die Messung belegt nicht den vollständigen V1 Suchumfang (DEC-050).
- Wahrgenommene Suche im Browser (Ziel 1 s einschließlich Debounce, Netz und Darstellung) wurde nicht gemessen. Kalt- und Warmzustand, abgebrochene Browserabfragen und Abfragepläne sind nicht getrennt erfasst.
- Eine einzelne virtuelle Maschine, Messwerkzeug auf derselben Maschine. Die Werte sind ein Anhaltspunkt für diese Maschine, keine Zusage für die Referenzhardware und kein Vergleich mit Easy8, Redmine oder Jira.
