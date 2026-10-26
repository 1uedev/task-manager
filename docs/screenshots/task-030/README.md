# Screenshots und echte Browserprüfung zu TASK-030

Aufgenommen am 21. Oktober 2026 in Chromium (1280 mal 860 Pixel, mobil 320 Pixel breit) gegen das gebaute Backend (JAR dieses Stands) mit PostgreSQL 16 hinter `vite preview`, das `/api` an das Backend weiterleitet. Die Datenbank war leer; das erste Konto entstand mit `bootstrap-admin`, alles Weitere über die Oberfläche. Konten (admin, Berta Beispiel, Carl Muster), Projekt PLAT und Tickets sind synthetisch; Passwörter und Einmalpasswörter des Durchlaufs stehen nirgends im Repository, die Datenbank wurde danach verworfen. Die Bilder sind auf 256 Farben reduziert.

## Ablauf

1. Anmeldung als `admin` mit dem Einmalpasswort aus `bootstrap-admin`, Pflichtwechsel des Passworts; ein zu kurzes Passwort und abweichende Wiederholung werden am Feld gemeldet.
2. Konten Berta und Carl angelegt; das Einmalpasswort erscheint je einmal.
3. Projekt PLAT angelegt, Berta und Carl als Mitglieder aufgenommen (der Anlegende ist bereits Mitglied).
4. Gruppe „Entwicklung“ (Berta, admin): Projekt lesen, Tickets lesen, anlegen, bearbeiten, Tags anlegen; Lesen und Schreiben aller Ticketfelder. Gruppe „Lesen“ (Carl): Projekt lesen, Tickets lesen; Lesen von Titel, Typ, Status, Zuständig und Tags, Beschreibung ohne Regel.
5. Drei Tickets angelegt, Projekttag „Sicherheit“ angelegt und PLAT-1 zugeordnet.
6. Berta und Carl melden sich zum ersten Mal an und wählen ein Passwort. Carl sieht PLAT-1 nur lesend, die Beschreibung ist verborgen, auch in der Historie; Tags sieht er ohne Bearbeitung.
7. Konflikt: Berta ändert den Titel und speichert, admin ändert danach auf dem alten Stand den Status. Die Oberfläche meldet den Konflikt, lädt Bertas Titel, behält den Status von admin und speichert ihn beim zweiten Versuch als Version 3.
8. Ungespeicherte Eingabe: Berta ändert die Beschreibung von PLAT-2 und klickt auf „Suche“. Die Rückfrage erscheint, nach Ablehnen bleibt die Seite mit der Eingabe.
9. Rechteentzug: admin entzieht der Gruppe „Entwicklung“ das Recht „Tickets bearbeiten“, während Bertas Eingabe offen ist. Ihr Speichern wird abgelehnt (403), die Seite lädt Ticket und Rechte neu und zeigt PLAT-2 nur noch lesend; nichts wurde geschrieben. Danach wurde das Recht zurückgegeben.
10. Suche als Carl nach „Anmeldung“ findet PLAT-1 mit Tag.
11. admin deaktiviert Carl; Carls nächste Seite endet an der Anmeldung mit dem Hinweis, dass die Sitzung beendet ist. Danach wieder aktiviert.
12. Englisch, Dunkel und 320 Pixel Breite (kein waagrechtes Scrollen), Abmelden.

## Bilder

| Datei | Inhalt |
|---|---|
| [03-password-error.png](03-password-error.png) | Pflichtwechsel des Passworts mit Fehlern am Feld |
| [04-start.png](04-start.png) | Startseite mit Verwaltungsbereich für admin |
| [08-group-entwicklung.png](08-group-entwicklung.png) | Gruppe mit Mitgliedern, Aktionen und Feldregeln |
| [11-ticket-list.png](11-ticket-list.png) | Ticketliste des Projekts |
| [13-ticket-admin.png](13-ticket-admin.png) | Ticket bearbeitbar, mit Tag und Historie |
| [14-ticket-carl-readonly.png](14-ticket-carl-readonly.png) | Dasselbe Ticket für Carl: nur lesend, Beschreibung verborgen |
| [16-conflict.png](16-conflict.png) | Konfliktmeldung, fremde Änderung geladen, eigene Eingabe erhalten |
| [17-revoked.png](17-revoked.png) | Recht entzogen: Speichern abgelehnt, Seite nur noch lesend |
| [18-search-carl.png](18-search-carl.png) | Suche als Carl |
| [19-carl-ended.png](19-carl-ended.png) | Nach Deaktivierung: zurück an der Anmeldung |
| [20-ticket-english.png](20-ticket-english.png) | Ticket auf Englisch |
| [21-dark-project.png](21-dark-project.png) | Projekt in dunkler Darstellung |
| [22-ticket-320.png](22-ticket-320.png) | Ticket bei 320 Pixel Breite |

## Grenzen

Ein Browser (Chromium), kein Screenreader, keine automatische Barrierefreiheitsprüfung. Typen, Status und Übergänge sind in dieser Ausbaustufe fest; ihre Bearbeitung gehört nicht zu TASK-030.
