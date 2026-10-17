# Gemeinsame Verträge

[qualification.openapi.yaml](qualification.openapi.yaml) ist ein synthetischer Transportvertrag nach OpenAPI 3.1.1. Er demonstriert die künftige Vertragsform, JSON/Problem-Details, ETag und If-Match sowie Generator-/Client-Kompatibilität. Die Pfade unter `/_qualification` und der Host `qualification.invalid` sind keine Produktendpunkte. Der Versuch implementiert weder Authentifizierung noch Rechte, Wiederholbarkeit oder HTTP-Vorbedingungslogik; Headertransport ist davon zu unterscheiden.

Die generierten Typen liegen im isolierten Frontendversuch und dürfen nicht von Hand bearbeitet werden. Eine Vertragsänderung regeneriert sie und muss statische wie dynamische Prüfungen bestehen. Die gemeinsamen Regeln für die späteren Modulschnittstellen stehen in [TECHNICAL_BASELINE.md](../docs/TECHNICAL_BASELINE.md); fachliche Beispiele weiterhin in [TECHNICAL_CONTRACTS.md](../docs/TECHNICAL_CONTRACTS.md).

Vor der jeweiligen Fachimplementierung erhält die produktive API einen eigenen versionierten Vertrag unter `/api/v1`. Insbesondere Identität, Projektverwaltung und Feldsicht sind in dieser technischen Probe nicht vollständig modelliert. Optionale, verborgene und ausdrücklich leere Felder dürfen im Produkt nicht durch Übernahme der einfachen Probe gleichgesetzt werden.
