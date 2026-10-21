# Vorführung mit Docker (TASK-031)

Stand 21. Oktober 2026 · Ergebnis von TASK-031 zur Prüfung (DEC-071). Dieses Dokument beschreibt das Vorführimage, die synthetischen Demodaten, den Ablauf für die fachliche Abnahme und die begrenzte Suchmessung. Es ist kein Betriebshandbuch: TLS, Sicherung und Wiederherstellung, Hochverfügbarkeit und eine Veröffentlichung des Images gehören nicht dazu.

## Was gestartet wird

| Teil | Inhalt |
|---|---|
| [Dockerfile](../Dockerfile) | Ein Image mit Backend und gebauter Oberfläche. Drei Stufen: Oberfläche mit Node 24.22.0 bauen, Backend mit Temurin 25 und der gebauten Oberfläche paketieren, Laufzeit auf Temurin 25 JRE. Basisimages mit Tag und Digest festgelegt. Läuft als eigenes Konto ohne Root, ohne Passwort, Schlüssel oder Daten im Image. |
| [compose.yaml](../compose.yaml) | PostgreSQL 18.6 (gleicher Digest wie Tests und CI) mit dem benannten Volume `db-data`, die Anwendung und auf Wunsch das Werkzeug `demo`. Die Anwendung ist nur auf diesem Rechner erreichbar (`127.0.0.1`). |
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

`bootstrap-admin` legt das erste Konto mit allen Verwaltungsrechten an und zeigt einmalig ein Einmalpasswort. Danach entweder im Browser unter <http://localhost:8080> anmelden und das Passwort selbst wechseln, oder die Demodaten gleich mit dem Einmalpasswort anlegen lassen:

```sh
docker compose --profile tools run --rm demo seed --profile demo --one-time-password EINMALPASSWORT
```

Das Werkzeug ersetzt dabei das Einmalpasswort durch `TASK_MANAGER_DEMO_PASSWORD`. Wurde das Passwort vorher im Browser auf denselben Wert gesetzt, entfällt `--one-time-password`.

Die Oberfläche funktioniert nur unter `http://localhost:…` auf demselben Rechner. Die Sitzungscookies tragen das Präfix `__Host-` und das Merkmal `Secure`; Browser nehmen sie ohne TLS nur für `localhost` an. Zugriff von einem anderen Rechner braucht einen TLS Zugang davor, der nicht Teil dieser Aufgabe ist.

Weitere Befehle:

| Befehl | Wirkung |
|---|---|
| `docker compose stop` / `docker compose start` | Anhalten und fortsetzen; Daten und Sitzungen bleiben. |
| `docker compose down` | Container entfernen; das Volume und damit alle Daten bleiben. |
| `docker compose down -v` | Container **und Daten** entfernen, für einen frischen Start. |
| `docker compose run --rm app reset-admin-password admin` | Neues Einmalpasswort für ein Konto mit Kontenverwaltung, wenn niemand mehr angemeldet ist. |
| `docker compose run --rm app rebuild-search` | Suchprojektion aus den Fachdaten neu aufbauen. |
| `docker compose logs app` | Protokoll der Anwendung mit Korrelationskennung je Anfrage. |

## Demodaten

`seed --profile demo` legt über die API an:

| Konto | Gruppe | Projekte | Sichtbar und änderbar |
|---|---|---|---|
| `admin` | Verwaltung Demo und alle Verwaltungsrechte | alle vier | alles |
| `anna` | Projektleitung | WEB, APP, HELP | alle Felder lesen und schreiben, Projekte verwalten |
| `ben` | Entwicklung | WEB, APP | alle Felder lesen; Titel, Beschreibung, Status, Zuständigkeit und Tags schreiben |
| `carla` | Lesen ohne Beschreibung | WEB | nur Titel, Typ, Status und Tags lesen, nichts ändern |
| `david` | Entwicklung | HELP | wie ben, aber nur im Helpdesk |

Dazu vier Projekte (WEB Webportal Relaunch, APP Mobile App, HELP Interner Helpdesk, INF Infrastruktur, das nur der Administrator sieht), 43 Tickets in allen drei Status, fünf Tags im Unternehmenskatalog und zwei bis drei je Projektkatalog. Alle Namen und Texte sind erfunden. Ein zweiter Lauf gegen dieselbe Datenbank bricht ab, statt doppelte Daten anzulegen.

## Ablauf für die fachliche Abnahme

Der erste vorführbare Abschnitt aus DEC-051 lautet: anmelden, Projekt öffnen, Ticket bearbeiten, Tags vergeben, Ticket wiederfinden. Mit den Demodaten in zwei Browserfenstern (eines davon privat):

1. Als `anna` anmelden. Startseite, Projekte WEB, APP und HELP; INF fehlt, weil anna dort kein Mitglied ist.
2. WEB öffnen, ein Ticket öffnen, Titel und Status ändern, speichern. Die Historie zeigt Version, Zeit und anna als Urheberin.
3. Im Ticket einen vorhandenen Tag zuordnen und einen neuen Projekttag anlegen.
4. Unter Suche nach einem Wort aus dem neuen Titel oder nach `WEB-` und der Nummer suchen; der Treffer erscheint sofort.
5. Im zweiten Fenster als `carla` anmelden. Nur WEB ist sichtbar, Beschreibung und Zuständigkeit fehlen, es gibt keine Bearbeitungsfelder. Die Suche nach „Drucker“ findet nichts, obwohl es im Helpdesk ein solches Ticket gibt.
6. Konflikt: anna und ben öffnen dasselbe WEB Ticket, beide ändern den Titel. Der zweite Speichervorgang meldet den Konflikt und behält die eigene Eingabe.
7. Als `admin` unter Gruppen und Rechte der Gruppe „Entwicklung“ das Schreiben des Titels nehmen. ben sieht beim nächsten Speichern die Ablehnung und danach nur noch lesbare Felder.
8. Unter Konten ein neues Konto anlegen; das Einmalpasswort erscheint einmal. Mit ihm anmelden, der Passwortwechsel ist Pflicht.
9. `docker compose down` und `docker compose up -d`: Daten und Anmeldungen bleiben, das Sitzungsende ändert sich nicht.

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

- **Bestand**, über die API erzeugt in 17,5 Minuten: 100 Konten in drei Gruppen (Projektleitung, Bearbeitung, Lesen ohne Beschreibung und Zuständigkeit), 20 Projekte mit je 25 Mitgliedern und dem Administrator, 90.000 Tickets mit Titel und ein bis vier Sätzen Beschreibung aus einem synthetischen Wortschatz mit sehr häufigen und seltenen Wörtern, 5.000 Tags (1.000 im Unternehmenskatalog, 200 je Projekt), 448.682 Tagzuordnungen an Tickets (rund fünf je Ticket) und 200 an Projekten, 988.514 Historieneinträge (rund elf je Ticket, aus Anlage, Statuswechseln und Tagzuordnungen). Datenbank 641 MB, davon Suchprojektion 209 MB. Nach dem Erzeugen einmal `VACUUM ANALYZE`.
- **Last**: 15 gleichzeitig angemeldete Konten, über alle drei Gruppen verteilt, je Person eine Aktion im Mittel alle fünf Sekunden (exponentiell verteilte Pause). Anteile nach OPERATIONS.md: Lesen und Listen 45, Suche 25, Ticketänderung 15; Zeitbuchung (10) und Wiki (5) gibt es noch nicht, die übrigen 85 Teile werden hochgerechnet. Personen ohne Schreibrecht ändern nicht; ihr Anteil verteilt sich auf die anderen Aktionen. Ticketänderung heißt Lesen und dann Titeländerung mit `If-Match`.
- **Messklassen der Suche**: Kennung (`R05-1234`), ein Wort, zwei Wörter, sehr häufiger Begriff (in rund 60 Prozent der Titel und jeder Beschreibung), kurzer Präfix aus zwei Buchstaben, ohne Treffer, Wort mit Projekt- und Statusfilter, komplexe Suche (Wort, drei Projekte, zwei Status, zwei Typen, ein bis zehn Tags) und Tagvorschlag. Jede Suche mit 20 Treffern je Seite und allen Rechtefiltern der jeweiligen Person.
- **Messpunkt**: Antwortzeit beim Aufrufer im selben Docker Netz, vom Senden bis zur gelesenen Antwort. Das ist eine obere Schranke der Serverzeit; Browser, Darstellung und Netz sind nicht enthalten. Gezählt werden nur erfolgreiche Antworten, abgewiesene (Konflikt, fehlendes Recht) und Fehler getrennt.
- **Maschine**: eine virtuelle Maschine mit 4 vCPU (Intel Xeon, 2,1 GHz) und 16 GB, Linux, Docker 29.9. Anwendung, PostgreSQL und Messwerkzeug liefen gemeinsam darauf, ohne Grenzen für die Container. Das entspricht der unverbindlichen Referenz von 4 vCPU und 16 GB nur in der Größe; Plattenart und Nachbarlast der virtuellen Maschine sind unbekannt.

### Ergebnis vom 21. Oktober 2026

15 Sitzungen, 30 Minuten nach 10 Minuten Aufwärmen, 5.365 Messwerte, keine Fehler, keine Abweisungen.

| Messklasse | Messwerte | p50 ms | p95 ms | p99 ms | Ziel p95 | erfüllt |
|---|---:|---:|---:|---:|---:|---|
| Ticketliste (50 Tickets) | 1352 | 6,1 | 8,9 | 11,7 | 500 ms | ja |
| Ticketdetail | 1647 | 5,7 | 8,4 | 11,5 | 500 ms | ja |
| Ticketänderung | 646 | 8,2 | 12,0 | 15,9 | kein Ziel | |
| Suche Kennung | 213 | 12,2 | 17,2 | 19,2 | 500 ms | ja |
| Suche ein Wort | 283 | 113,7 | 209,6 | 239,7 | 500 ms | ja |
| Suche zwei Wörter | 209 | 65,3 | 177,1 | 192,7 | 500 ms | ja |
| Suche häufiger Begriff | 211 | 98,1 | 141,5 | 187,2 | 500 ms | ja |
| Suche kurzer Präfix | 135 | 23,0 | 152,4 | 193,0 | 500 ms | ja |
| Suche ohne Treffer | 120 | 9,7 | 16,0 | 17,2 | 500 ms | ja |
| Suche mit Filtern | 267 | 36,6 | 67,7 | 85,5 | 500 ms | ja |
| Komplexe Suche | 155 | 28,7 | 56,9 | 66,3 | 1 s | ja |
| Tagvorschlag | 127 | 3,4 | 5,8 | 6,3 | 200 ms | ja |

### Zusätzliche Laststufen

| Lauf | Aktionen je Sekunde | Ergebnis |
|---|---:|---|
| 30 Sitzungen, sonst gleiches Profil, 2 Minuten Aufwärmen, 10 Minuten Messung | 5,9 | 3.523 Messwerte, keine Fehler. Alle Ziele eingehalten; größter p95 Wert „Suche ein Wort“ mit 239 ms. |
| 15 Sitzungen **ohne Pause**, 1 Minute Aufwärmen, 5 Minuten Messung | 207 | 62.127 Messwerte, keine Fehler, 5 erwartete Versionskonflikte. Rund siebzigmal die Normallast. „Suche ein Wort“ (p95 596 ms) und „Suche zwei Wörter“ (p95 550 ms) überschreiten das Ziel von 500 ms, alle anderen Klassen bleiben darunter, Ticketliste und Detail bei p95 unter 70 ms. |

Die Rohdaten aller drei Läufe stehen unter [measurements/task-031](measurements/task-031/) (je eine Markdown Tabelle und eine JSON Datei mit Maschine, Parametern und p50/p95/p99 je Klasse).

### Grenzen der Messung

- Ein Lauf von 30 Minuten statt zusätzlich des zweistündigen Dauerlaufs; keine Hintergrundlast durch Mailabruf, SLA oder Sicherung, weil es diese Teile noch nicht gibt.
- Kommentare, Wiki, Anforderungen und Portalkonten fehlen im Bestand und in der Suche; die Messung belegt nicht den vollständigen V1 Suchumfang (DEC-050).
- Wahrgenommene Suche im Browser (Ziel 1 s einschließlich Debounce, Netz und Darstellung) wurde nicht gemessen. Kalt- und Warmzustand, abgebrochene Browserabfragen und Abfragepläne sind nicht getrennt erfasst.
- Eine einzelne virtuelle Maschine, Messwerkzeug auf derselben Maschine. Die Werte sind ein Anhaltspunkt für diese Maschine, keine Zusage für die Referenzhardware und kein Vergleich mit Easy8, Redmine oder Jira.
