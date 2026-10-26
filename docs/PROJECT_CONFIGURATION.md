# Projektkonfiguration mit Tickettypen, Abläufen und eigenen Feldern

Umsetzung von TASK-044 (Paket A2, DEC-073). Dieses Dokument beschreibt, was gebaut ist, wie die Prüffälle F-03 bis F-06 aus [DOMAIN_MODEL.md](DOMAIN_MODEL.md) abgedeckt sind und was bewusst fehlt. Verbindlich sind die Verträge [projects.openapi.yaml](../contracts/projects.openapi.yaml), [tickets.openapi.yaml](../contracts/tickets.openapi.yaml), [search.openapi.yaml](../contracts/search.openapi.yaml) und [permissions.openapi.yaml](../contracts/permissions.openapi.yaml). Die Ausgestaltung ist ein Umsetzungsvorschlag und steht mit dem Ergebnis zur Prüfung.

## Das Konfigurationsdokument

Eine Konfigurationsversion ist ein unveränderliches JSON Dokument je Projekt. Zu den Teilen aus TASK-026 (Tickettypen, Status, erster Status, Standardablauf, feste Felder) kommen drei hinzu:

| Teil | Bedeutung |
|---|---|
| `workflows` | Ablauf einzelner Tickettypen: Typ, erster Status, Übergänge. Ein Typ mit Eintrag kennt nur den ersten Status und die Status seiner Übergänge; alle anderen Typen folgen dem Standardablauf. |
| `transitions[].requiredFields` | Felder (`description`, `assignee`, `custom.<schlüssel>`), die das Ticket nach der Änderung mit diesem Übergang gefüllt haben muss. |
| `customFields` | Eigene Felder mit Schlüssel, Name, Art, Pflicht bei der Anlage, aktiv und, bei der Einfachauswahl, Optionen. |

Ältere Dokumente ohne diese Teile lesen sich als Konfiguration ohne eigene Abläufe, Felder und Übergangsfelder. Es gibt keine Datenmigration der gespeicherten Versionen.

## Eigene Felder

| Art | Wert in der API | Grenzen |
|---|---|---|
| `text` | Text | 1 bis 200 Zeichen, ohne NUL |
| `longtext` | Text | 1 bis 20000 Zeichen |
| `integer` | Zahl | ganze Zahl zwischen −(2^53 minus 1) und 2^53 minus 1 |
| `date` | Text `yyyy-MM-dd` | gültiger Kalendertag |
| `boolean` | `true` oder `false` | |
| `select` | Schlüssel einer Option | höchstens 50 Optionen, Namen verschieden |
| `user` | Kennung eines Kontos | aktives Mitglied des Projekts |

Ein Projekt hat höchstens 30 eigene Felder. Ein Wert ist leer, wenn er fehlt; `null` leert ihn bei einer Änderung. Gespeichert wird die kanonische Textform in `ticket.custom` (Migration V0019), die Antwort der API gibt Zahl und Wahrheitswert wieder als solche zurück.

**Nie entfernen, nie umwandeln.** Ein veröffentlichtes Feld und jede seiner Optionen bleiben in allen späteren Versionen erhalten und behalten die Art. Eine Version, der ein Feld oder eine Option fehlt oder in der ein Feld die Art wechselt, wird mit 409 `project.configuration_incompatible` (`customFields.removed`, `customFields.option_removed`, `customFields.type_changed`) abgewiesen. Stattdessen wird `active` auf `false` gesetzt:

- Vorhandene Werte bleiben lesbar und gültig, auch beim erneuten Schreiben desselben Wertes.
- Ein neuer Wert wird abgelehnt (`custom.<schlüssel>.inactive`, bei einer Option `custom.<schlüssel>.option_inactive`); das Leeren ist erlaubt.
- Das Feld wird nicht mehr als beschreibbar gemeldet und verlangt nichts mehr.

## Abläufe und Pflichtfelder

- Ein neues Ticket beginnt im ersten Status seines Typs.
- Ein Statuswechsel muss im Ablauf des Typs stehen (409 `ticket.transition_not_allowed`). Wechselt gleichzeitig der Typ, gilt der Ablauf des neuen Typs.
- Wechselt nur der Typ und kennt der Ablauf des neuen Typs den Status nicht, kommt das Ticket in den ersten Status des neuen Typs (TASK-052, DEC-081). Das ist kein Übergang und umgeht deshalb keine Pflichtfelder; es verlangt die Schreibregel für `status` (sonst 400 `type.status_unavailable`). Einen frei gewählten Zielstatus gibt es beim Typwechsel nicht. Wird der Status zusammen mit dem neuen Typ mitgegeben, auch der bisherige, muss der Ablauf des neuen Typs ihn kennen, sonst 400 `status.unavailable` (TASK-053, Befund N1 der Nachprüfung von TASK-052).
- Verlangt der Übergang ein Feld, das die anfragende Person nicht lesen oder nicht schreiben darf, antwortet der Server immer mit 409 `ticket.required_field_inaccessible`, gleich ob das Feld gefüllt ist (TASK-052). So verrät die Antwort nichts über ein verborgenes Feld.
- Verlangt der Übergang Felder, die nach der Änderung leer sind, antwortet der Server mit 400 und `<feld>.required_for_transition`. Nichts wird geschrieben, auch die Version bleibt.
- Ein Schlüssel in `customFields`, den die Konfiguration nicht kennt, antwortet mit 400 `customFields.unknown`.
- Pflicht bei der Anlage gilt für `fields` (Beschreibung, Zuständigkeit) und für aktive `customFields[].required`. Ein Pflichtfeld lässt sich nicht leeren. Darf die anfragende Person ein Pflichtfeld nicht schreiben, antwortet die Anlage mit 409 `ticket.required_field_inaccessible`.
- **Bestandstickets bleiben gültig.** Pflichtfelder werden nur geprüft, wenn das Feld geschrieben, geleert oder ein Übergang genommen wird, der es verlangt, und bei der Anlage. Ein Ticket, dem ein später verlangtes Feld fehlt, kann weiter bearbeitet werden.

## Entwurf und Veröffentlichung

```text
GET    /projects/{id}/configuration/draft      Entwurf lesen (404 ohne Entwurf)
PUT    /projects/{id}/configuration/draft      anlegen (ohne If-Match) oder ersetzen (mit Revision)
DELETE /projects/{id}/configuration/draft      verwerfen (Revision als If-Match)
GET    /projects/{id}/configuration/usage      Tickets je Typ und Status
POST   /projects/{id}/configuration/publish    Entwurf veröffentlichen
PUT    /projects/{id}/configuration            direkt veröffentlichen, ohne Entwurf
```

Ein Projekt hat höchstens einen Entwurf (Migration V0018). Er trägt eine Revision, die im `ETag` steht und bei jedem Speichern steigt, und die veröffentlichte Version, von der er ausgeht. Zwei Bearbeitende überschreiben sich so nicht (412 `project.draft_conflict`), und ein Entwurf, der älter ist als die veröffentlichte Version, lässt sich weder speichern noch veröffentlichen (409 `project.draft_stale`). Ein Entwurf muss aufbaubar sein wie eine Konfiguration; ob er veröffentlicht werden darf, prüft erst die Veröffentlichung.

Die Veröffentlichung läuft in einer Transaktion unter der Sperre des Projekts:

1. Der Entwurf wird geprüft (400 mit Gründen) und gegen die veröffentlichte Version verglichen (409 `project.configuration_incompatible`).
2. Die Tickets werden gegen die neue Version geprüft. Ein Status oder Tickettyp, den Tickets noch verwenden und den die neue Version nicht mehr kennt oder für den Typ nicht mehr vorsieht, braucht eine Zuordnung `statusMapping` und `typeMapping`. Fehlt sie, antwortet der Server mit 409 `project.configuration_in_use` und nennt `status.in_use:<schlüssel>` beziehungsweise `type.in_use:<schlüssel>`; nichts ändert sich (F-05). Ein Zielstatus, den der Typ nicht kennt, wird mit `status.mapping_invalid:<schlüssel>` abgewiesen. Ein Schlüssel von `statusMapping` ist ein Status oder `<typ>:<status>` für die Tickets eines bisherigen Typs; dieser hat Vorrang (TASK-052). So lässt sich ein Status, den mehrere Typen mit verschiedenen Abläufen verwenden, je Typ zuordnen; der Zuordnungsdialog bietet dann je Typ eine Auswahl. Ein wegfallender Typ zählt dabei mit dem Ablauf des Typs, auf den seine Tickets wechseln, und behält im Schlüssel seinen bisherigen Namen; der Dialog zeigt für ihn eine eigene Auswahl mit den Zielen dieses Ablaufs (TASK-053, Befund N2).
3. Die zugeordneten Tickets erhalten eine neue Version, einen Historieneintrag des Veröffentlichenden für Typ und Status und ein Ereignis für die Suchprojektion.
4. Die nächste Version wird geschrieben, der Entwurf entfällt, die Version des Projekts steigt.

**Geltung.** Eine veröffentlichte Version gilt sofort für alle Tickets des Projekts; kein Ticket bleibt an eine alte Version gebunden (Standardwahl aus TASK-044). `configurationVersion` am Ticket nennt nur die Version, unter der es zuletzt geschrieben wurde. Frühere Versionen bleiben unter `GET /projects/{id}/configurations/{version}` lesbar.

Das Projektmodul liest keine Tickettabellen und hängt nicht vom Ticketmodul ab. Es nutzt den Port `TicketUsage`, den das Ticketmodul umsetzt.

## Rechte, Historie, Suche

- **Rechte.** Die Feldkennung eines eigenen Feldes ist `custom.<schlüssel>` im Ressourcentyp `ticket`. Ohne Leseregel fehlt das Feld in der Antwort, in der Historie und in der Suche; ohne Schreibregel wird jede Änderung mit 403 abgewiesen. Eine Regel dafür setzt `PUT /permissions/groups/{id}/fields` nur mit Projekt und nur für ein Feld der aktuellen Konfiguration (sonst 400 `field.unknown`, nachdem ein verborgenes Projekt mit 404 beantwortet wurde). Der Reiter Rechte des Projekts bietet die Felder an.
- **Historie.** Jede Änderung eines eigenen Feldes ist ein Eintrag `custom.<schlüssel>` mit altem und neuem Wert in der kanonischen Textform; die Oberfläche zeigt Optionsnamen, Kontonamen und Datum in der Schreibweise der Sprache. Sichtbar ist er mit der Leseregel des Feldes.
- **Suche.** Die Projektion hält die Werte in `search_ticket_custom` (V0020). Suchwörter treffen die Werte lesbarer Felder der Arten Kurztext und Langtext; der Filter `custom=<schlüssel>=<wert>` verlangt einen genauen Wert eines lesbaren Feldes; `customFields` im Treffer zeigt nur Lesbares. Ein verborgenes Feld ändert weder Treffer noch Rang noch Anzahl. Die Migration leert `search_state`, der erste Start danach baut die Projektion neu auf.

## Prüffälle

| Fall | Regression |
|---|---|
| F-03 Interne Ursache erst beim Lösen | `ProjectConfigurationApiTest.twoProjectsRunTheirOwnTypesWorkflowsAndFieldsSideBySide` (Kundenanlage ohne Feld, Lösen ohne Ursache abgewiesen, Ticket unverändert, Kunde sieht das Feld nie), Browsertest `a custom field is drafted, kept over a reload, published and then required for a step` |
| F-04 Prüfanleitung vor Prüfung | derselbe Test (Beispiel B, Übergang `in_entwicklung` nach `in_pruefung`) |
| F-05 Verwendeter Status | `aStatusOrTypeThatTicketsStillUseCanOnlyGoWithAMappingAndMovesThemWithHistory`, `aWorkflowThatDropsAStatusOfATypeNeedsAMappingForTheTicketsInIt`, Browsertest `a status that tickets use goes only with an assignment, which moves them` |
| F-06 Deaktivierte Option | `customFieldsAndOptionsAreDeactivatedNeverRemovedAndKeepTheirValues` |
| Befunde F1 bis F5 der Prüfung ([Bericht](REVIEW_TASK-044_PR-151.md), TASK-052) | `aStepThatRequiresAFieldTheCallerCannotSeeIsRefusedWithoutTellingWhetherItIsFilled`, `wholeNumbersBeyondTheBoundAreRefusedOnBothSides`, `aTicketChangesBetweenTypesWithSeparateWorkflowsIntoTheFirstStatusOfTheNewType`, `aStatusUsedByTwoTypesIsMappedPerType`, `anUnknownCustomFieldIsNamedAsUnknown` |

Dazu: `draftsAreSavedWithRevisionsAndPublishedOnlyFromTheirBase`, `anInvalidConfigurationNamesItsReasonsAndChangesNothing`, `customValuesAreTypedValidatedAndRecordedInTheHistory`, `requiredCustomFieldsBindCreationAndCannotBeEmptiedButStayOutOfTheWayOfExistingTickets`, `customFieldRulesHideValuesInAnswersHistoryAndSearchLikeStandardFields`, `theGroupAdministrationOffersAndChecksTheCustomFieldsOfAProject` und `theRegisteredConfigurationOfAnOldProjectReadsAsOneWithoutNewFeatures`.

## Nicht Teil und offen

Nicht umgesetzt, wie in der Aufgabe ausgeschlossen: Formulare und Portalformulare, Automationen, Projektvorlagen, Mehrfachauswahl und Dezimalzahl, Verschieben zwischen Projekten, Archivierung. Außerdem fehlt eine Oberfläche für den Filter auf eigene Felder in der Suche; der Vertrag kennt ihn. Die Oberfläche der Rechte zeigt eigene Felder nur im Reiter Rechte des Projekts, nicht in der Gruppenverwaltung des Unternehmens, weil eine Regel dafür immer ein Projekt braucht.

Entscheidungen für Holger (siehe DEC-073): ob das Löschen nie verwendeter Felder erlaubt sein soll, ob Pflichtfelder bestehende Tickets nachträglich zur Ergänzung zwingen sollen und ob die Direktveröffentlichung neben dem Entwurf bleibt.
