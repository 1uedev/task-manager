# Gemeinsame Verträge

[qualification.openapi.yaml](qualification.openapi.yaml) ist ein synthetischer Transportvertrag nach OpenAPI 3.1.1. Er demonstriert die künftige Vertragsform, JSON/Problem-Details, ETag und If-Match sowie Generator-/Client-Kompatibilität. Die Pfade unter `/_qualification` und der Host `qualification.invalid` sind keine Produktendpunkte. Der Versuch implementiert weder Authentifizierung noch Rechte, Wiederholbarkeit oder HTTP-Vorbedingungslogik; Headertransport ist davon zu unterscheiden.

Die generierten Typen liegen im isolierten Frontendversuch und dürfen nicht von Hand bearbeitet werden. Eine Vertragsänderung regeneriert sie und muss statische wie dynamische Prüfungen bestehen. Die gemeinsamen Regeln für die späteren Modulschnittstellen stehen in [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md); fachliche Beispiele weiterhin in [TECHNICAL_CONTRACTS.md](../docs/TECHNICAL_CONTRACTS.md).

Vor der jeweiligen Fachimplementierung erhält die produktive API einen eigenen versionierten Vertrag unter `/api/v1`. Insbesondere Identität, Projektverwaltung und Feldsicht sind in dieser technischen Probe nicht vollständig modelliert. Optionale, verborgene und ausdrücklich leere Felder dürfen im Produkt nicht durch Übernahme der einfachen Probe gleichgesetzt werden.

[identity.openapi.yaml](identity.openapi.yaml) ist der erste Teil der **Produkt API** unter `/api/v1` (TASK-024): Sitzung, Anmeldung, Passwortwechsel, Konten und feste Sitzungsdauer. Anders als der synthetische Versuchsvertrag beschreibt er tatsächlich implementierte Endpunkte; `IdentityContractTest` im Backend weist Abweichungen zwischen Vertrag und Routen ab. Der Vertrag kennt noch keine Fachbereiche, keine Rechte und keinen erzeugten Client; das Problemschema ist hier bereits für `code`, `correlationId`, `instance` und `violations` geöffnet (Befund Q5).

[appearance.openapi.yaml](appearance.openapi.yaml) beschreibt Akzentfarbe und Logo des Unternehmens (TASK-036): Lesen mit vollständiger Anmeldung, Ändern mit der vorläufigen Administratorkennzeichnung, jede Änderung mit `If-Match` auf die Version der Einstellung. `AppearanceContractTest` gleicht ihn mit den Routen ab. Die Oberfläche erzeugt ihre Typen aus diesem Vertrag und aus `identity.openapi.yaml`; die Identitätstypen nutzt sie bisher nur für den Sitzungszustand.

[projects.openapi.yaml](projects.openapi.yaml) beschreibt Projekte, Mitglieder und Konfigurationsversionen (TASK-026, DEC-065). `ProjectsContractTest` gleicht ihn mit den Routen ab. Die Oberfläche nutzt ihn noch nicht und erzeugt daraus keine Typen; das folgt mit der Projektoberfläche. Der Identitätsvertrag nennt seither `projects.manage` in der Rechteliste der Sitzung.

[tickets.openapi.yaml](tickets.openapi.yaml) beschreibt Tickets eines Projekts mit Feldrechten, Historie und Versionen (TASK-027, DEC-067). `TicketsContractTest` gleicht ihn mit den Routen ab; die Oberfläche nutzt ihn noch nicht.

[tags.openapi.yaml](tags.openapi.yaml) beschreibt den Unternehmenskatalog, die Projektkataloge und die Tag Zuordnungen zu Tickets und Projekten (TASK-028, DEC-068). `TagsContractTest` gleicht ihn mit den Routen ab; die Oberfläche nutzt ihn noch nicht. Der Identitätsvertrag nennt seither `tags.manage` in der Rechteliste der Sitzung.

[search.openapi.yaml](search.openapi.yaml) beschreibt die Suche und die Filter für Tickets und Projekte (TASK-029, DEC-069). `SearchContractTest` gleicht ihn mit den Routen ab; die Oberfläche nutzt ihn noch nicht.

[permissions.openapi.yaml](permissions.openapi.yaml) beschreibt die Verwaltung der Gruppen, Mitglieder, Aktionen und Feldregeln (TASK-030, DEC-070). `PermissionsContractTest` gleicht ihn mit den Routen ab. Der Identitätsvertrag nennt seither `permissions.manage` in der Rechteliste der Sitzung und das Kontenverzeichnis `GET /directory/accounts`.

[files.openapi.yaml](files.openapi.yaml) beschreibt die Anhänge von Tickets (TASK-051, DEC-080): Auflisten, Hochladen als roher Körper, Herunterladen nur als Datei zum Speichern und Entfernen. `FilesContractTest` gleicht ihn mit den Routen ab. Der Ticketvertrag nennt seither das Feld `attachments` in der Historie, der Rechtevertrag in den Feldregeln.

[jobs.openapi.yaml](jobs.openapi.yaml) beschreibt die Sicht des Betriebs auf die Hintergrundaufträge (TASK-054, DEC-083): Übersicht der Warteschlange, Liste, erneutes Einstellen und Abbrechen, alles mit dem Unternehmensrecht `jobs.manage`. `JobsContractTest` gleicht ihn mit den Routen ab. Der Identitätsvertrag nennt seither `jobs.manage` in der Rechteliste der Sitzung.

[helpdesk.openapi.yaml](helpdesk.openapi.yaml) beschreibt den Eingang des Helpdesks über Mail (TASK-057, DEC-086): Kunden, Kontakte und Postfächer mit dem Unternehmensrecht `helpdesk.manage`, die Prüfwarteschlange mit der Projektaktion `helpdesk.review` und Kunde, Kontakt und Mails eines Tickets nach den Feldregeln `customer`, `contact` und `mail`. `HelpdeskContractTest` gleicht ihn mit den Routen ab. Identitäts-, Rechte- und Ticketvertrag nennen seither `helpdesk.manage` und die drei Felder.
