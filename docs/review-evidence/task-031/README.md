# Eigene Nachweise TASK-031 / PR #134

Sitzung `codex-20261022-task031-review-01`, 22. Oktober 2026. [Prüfbericht](../../REVIEW_TASK-031_PR-134.md). Geprüftes main: `28becad967722994423db0bf5ab49ebdeb3a9c6f`; Image-CI zum PR-Kopf `6203269f9738aa312d9bbded7cfd4b57cf0c627e`. Keine Produktquellen oder vorhandenen Tests verändert. Alle Daten und Inhalte der eigenen Proben sind synthetisch.

## Reproduzierbare eigene Helfer

```text
python docs/review-evidence/task-031/measurement_probes.py
```

[measurement_probes.py](measurement_probes.py) importiert die unveränderte Datei `tools/demo/demo.py` und prüft:

1. Nearest-rank-Perzentile an 1 bis 100 und die rechnerische Konsistenz der drei veröffentlichten Aggregate. Einzelne historische Antwortzeiten stehen nicht zur Verfügung.
2. Die originale komplexe Suchkonstruktion mit dem Standardumfang der vorbereiteten Tag-/Projektlisten eines Referenzbenutzers. Der HTTP-Transport wird hierfür nur durch einen aufzeichnenden Empfänger ersetzt. Das verändert keine Produktdatei und misst keine Servergeschwindigkeit. 8.914/10.000 Kombinationen sind konstruktiv leer; Überschneidungen der beiden ausgewiesenen Ursachen sind möglich.
3. Welche Dauer für die Schreibklasse erfasst wird, mit kontrollierten Antwortdauern.
4. Unveränderten POST-Retry gegen einen eigenen lokalen HTTP-Server, der erst nach Annahme die Antwortverbindung abbricht.
5. Die ursprüngliche Zeitrechnung unter der jeweiligen lokalen Zeitzone; auf der Prüfmaschine mit mitteleuropäischer Sommerzeit wird ein korrektes 24-Stunden-Intervall als 23 Stunden ausgewiesen.

Ausgabe: [probe-results.txt](probe-results.txt).

```text
python docs/review-evidence/task-031/http_probes.py DEMO_URL SMALL_REFERENCE_URL
```

[http_probes.py](http_probes.py) verwendet den Originalclient gegen zwei eigene lokale Instanzen. Vorher das unveränderte JAR jeweils auf einer leeren PostgreSQL-18.6-Datenbank starten, mit `bootstrap-admin admin` einrichten und über das Originalwerkzeug befüllen. Passwort nur in der lokalen Variable `TASK_MANAGER_DEMO_PASSWORD`; Einmalpasswortausgabe und Sitzungsdateien außerhalb des Repositorys halten.

- Erste Instanz: `seed --profile demo`.
- Zweite Instanz: `seed --profile reference --projects 4 --tickets 200 --accounts 12 --company-tags 40 --project-tags 10 --workers 4`.
- Bei der ersten Befüllung jeweils den vom lokalen Bootstrap gelieferten Einmalwert über die vorgesehene Option übergeben. Kein Einmalwert und kein Cookie sind Teil dieser Nachweise.

Der Helfer vergleicht das echte Sitzungsende mit UTC, führt 100 originale komplexe Suchen auf dem kleinen Bestand und eine gezielt positive Kontrollsuche aus. Abschließend leitet ein eigener lokaler Transporthelfer die echte Ticketanlage weiter, verwirft nur deren erste vollständige Erfolgsantwort und lässt den Originalclient erneut anfragen. Die folgende öffentliche Liste weist zwei gespeicherte Tickets für diesen einen Aufruf nach. Ausschließlich lokale Testziele werden für diesen letzten Versuch akzeptiert. Der Helfer protokolliert keine Cookie-/Passwortwerte. Die Wiederholung des Helfers legt jeweils zwei weitere synthetische Prüftickets an.

Ausgabe: [http-results.txt](http-results.txt). Die 91/100 leeren Fälle betreffen den verkleinerten Prüfbestand, nicht die historischen Messläufe des Autors. Der große ursprüngliche Lauf wird durch diese Probe nicht nachgestellt.

## Weitere eigene Ausführung

- Java 25.0.4.1+1 / Maven 3.9.16: `-Dtest=WebInterfaceHeadersTest package`, native externe PostgreSQL 18.6 über die dokumentierten `taskmanager.test.*`-Parameter. Drei HTTP-Tests erfolgreich, Build erfolgreich. Dieser Build enthält nicht die nur im Dockerbau eingefügte echte Frontenddistribution; die Kopfzeilentests verwenden ihre versionierte synthetische HTML-/Assetfixture.
- Originale Demo-Befüllung: vier zusätzliche Konten, vier Projekte, 43 Tickets. [verify-results.txt](verify-results.txt): elf Prüfungen erfolgreich, nur die Fristrechnung scheitert. Der erste Aufruf speicherte seine Testsitzung lokal außerhalb des Repositorys.
- Eigene Backendinstanz beendet und mit derselben Datenbank neu gestartet; originales `verify --resume`: [restart-results.txt](restart-results.txt), Sitzung gültig, Endzeit unverändert, erwartete Projekte vorhanden. Dies ist ein nativer Prozessneustart, kein Docker-Volume-Versuch.
- Zweiter `seed --profile demo` nach abgeschlossenem Neustart: Exit 1 mit Hinweis auf bereits vorhandene Projekte/Konten; keine zweite Befüllung. Ein vorheriger Versuch kollidierte mit dem Neustart und erreichte den Server nicht; die aussagekräftige Gegenprobe wurde danach wiederholt.
- Kleiner Referenzbestand: zwölf zusätzliche Konten, vier Projekte, 200 anfängliche Tickets, 80 Tags, 984 Ticket-Tagzuordnungen. Kurzer Original-`measure`-Durchlauf: drei Benutzer, `--think 0.1 --warmup 2 --duration 10`, 265 Werte, zwölf Klassen, Fehlerquote 0. Nur Funktionsprüfung des gesamten Mess-/Exportwegs; keine Leistungszusage aus diesen kurzen Daten. Ausgabe mit lokalen Metadaten bleibt außerhalb des Repositorys.
- `docker compose config` ohne Pflichtwerte: Exit 1. Mit synthetischen Pflichtwerten: erfolgreich, App nur 127.0.0.1:8080, DB ohne veröffentlichten Port, Volume `db-data` auf `/var/lib/postgresql`. Keine Passwortwerte ausgegeben.
- Aktuellen versionierten Bestand nach typischen Private-Key-/Tokenmustern durchsucht, ohne Treffer; `.env.example` ist die einzige versionierte passende Umgebungsdatei. Ergänzend COPY-/ENV-/ARG-/USER-Pfade manuell geprüft. Kein vollständiger Secret-/Supply-Chain-Scan der Imageschichten oder Git-Historie.
- Repositorypflichtprüfungen erfolgreich. Keine eigenen Lasttests mit 90.000 Tickets, keine Browserzeit-, Restore-, TLS- oder Wettbewerbsprüfung.

## Gelesene CI-Ausführung

[ci-excerpt.txt](ci-excerpt.txt) enthält ausgewählte Originalzeilen des unabhängig gelesenen [Image-Jobs](https://github.com/1uedev/task-manager/actions/runs/39666854242/job/119454062593). Der Job zum geprüften Kopf ist erfolgreich. Er baut das echte mehrstufige Image, führt `image-check.sh` aus und weist den Docker-Neustart mit erhaltenem Sitzungsende nach. Keine Gleichsetzung mit eigener Ausführung: Die lokale Engine konnte nicht starten (`Docker Desktop is unable to start`), deshalb wurden hier weder `docker build`, `docker inspect` noch `image-check.sh` erfolgreich lokal ausgeführt.

Die eigenen Testserver werden nach den Proben gestoppt. Rohprotokolle und private Laufzeitdateien bleiben außerhalb des Repositorys; der Bericht liefert nur bereinigte Ergebnisse und die eigenen Prüfscripte.
