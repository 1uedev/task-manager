# Belege der Nachprüfung TASK-053

Proben zum Bericht [REVIEW_TASK-053_PR-175.md](../../REVIEW_TASK-053_PR-175.md). Die Dateien enden auf `.txt`, damit kein Werkzeug sie als Teil des Produkts ausführt oder prüft. Sie laufen gegen die Vorführumgebung aus compose.yaml nach `bootstrap-admin` und `demo seed --profile demo`, aus dem Wurzelverzeichnis des Repositorys, mit `TASK_MANAGER_DEMO_PASSWORD` in der Umgebung (`python3 -I probeN.py`, `node ui-probe.mjs <projekt> <nummer>` aus `frontend/tests/browser`).

| Datei | Inhalt |
|---|---|
| `probe1.py.txt` | F1: drei Gruppen (nur Anhänge, nur feste Felder, nur Tags), `writableFields` in Liste und Einzelabruf gegen das Ergebnis von Hochladen, Entfernen und Tagzuordnung |
| `probe2.py.txt` | F8 Kopf, HEAD, F5 Sperrliste, F4 fehlender Inhalt, F7 nicht beschreibbarer Speicher und abgebrochene Übertragung, HEAD nach dem Abmelden |
| `probe3.py.txt` | R1: gelesene Bytes des Servers je HEAD und GET |
| `probe4.py.txt` | F2: Anhänge vor und nach der Rücksicherung nach docs/DEMO.md |
| `probe5.py.txt` | F8: Grenze und Sperrliste über Umgebungsvariablen |
| `ui-probe.mjs.txt` | F3, F4 und Makrohinweis in Chromium gegen den echten Server |
| `results.txt` | Ausgaben aller Proben |

Die Proben ändern Daten der Vorführung (zusätzliche Konten, Gruppen und Anhänge) und gehören nicht in eine Umgebung mit echten Daten.
