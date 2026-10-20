# Gemeinsame Verträge

[qualification.openapi.yaml](qualification.openapi.yaml) ist ein synthetischer Transportvertrag nach OpenAPI 3.1.1. Er demonstriert die künftige Vertragsform, JSON/Problem-Details, ETag und If-Match sowie Generator-/Client-Kompatibilität. Die Pfade unter `/_qualification` und der Host `qualification.invalid` sind keine Produktendpunkte. Der Versuch implementiert weder Authentifizierung noch Rechte, Wiederholbarkeit oder HTTP-Vorbedingungslogik; Headertransport ist davon zu unterscheiden.

Die generierten Typen liegen im isolierten Frontendversuch und dürfen nicht von Hand bearbeitet werden. Eine Vertragsänderung regeneriert sie und muss statische wie dynamische Prüfungen bestehen. Die gemeinsamen Regeln für die späteren Modulschnittstellen stehen in [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md); fachliche Beispiele weiterhin in [TECHNICAL_CONTRACTS.md](../docs/TECHNICAL_CONTRACTS.md).

Vor der jeweiligen Fachimplementierung erhält die produktive API einen eigenen versionierten Vertrag unter `/api/v1`. Insbesondere Identität, Projektverwaltung und Feldsicht sind in dieser technischen Probe nicht vollständig modelliert. Optionale, verborgene und ausdrücklich leere Felder dürfen im Produkt nicht durch Übernahme der einfachen Probe gleichgesetzt werden.

[identity.openapi.yaml](identity.openapi.yaml) ist der erste Teil der **Produkt API** unter `/api/v1` (TASK-024): Sitzung, Anmeldung, Passwortwechsel, Konten und feste Sitzungsdauer. Anders als der synthetische Versuchsvertrag beschreibt er tatsächlich implementierte Endpunkte; `IdentityContractTest` im Backend weist Abweichungen zwischen Vertrag und Routen ab. Der Vertrag kennt noch keine Fachbereiche, keine Rechte und keinen erzeugten Client; das Problemschema ist hier bereits für `code`, `correlationId`, `instance` und `violations` geöffnet (Befund Q5).

[appearance.openapi.yaml](appearance.openapi.yaml) beschreibt Akzentfarbe und Logo des Unternehmens (TASK-036): Lesen mit vollständiger Anmeldung, Ändern mit der vorläufigen Administratorkennzeichnung, jede Änderung mit `If-Match` auf die Version der Einstellung. `AppearanceContractTest` gleicht ihn mit den Routen ab. Die Oberfläche erzeugt ihre Typen aus diesem Vertrag und aus `identity.openapi.yaml`; die Identitätstypen nutzt sie bisher nur für den Sitzungszustand.

[projects.openapi.yaml](projects.openapi.yaml) beschreibt Projekte, Mitglieder und Konfigurationsversionen (TASK-026, DEC-065). `ProjectsContractTest` gleicht ihn mit den Routen ab. Die Oberfläche nutzt ihn noch nicht und erzeugt daraus keine Typen; das folgt mit der Projektoberfläche. Der Identitätsvertrag nennt seither `projects.manage` in der Rechteliste der Sitzung.

[tickets.openapi.yaml](tickets.openapi.yaml) beschreibt Tickets eines Projekts mit Feldrechten, Historie und Versionen (TASK-027, DEC-067). `TicketsContractTest` gleicht ihn mit den Routen ab; die Oberfläche nutzt ihn noch nicht.
