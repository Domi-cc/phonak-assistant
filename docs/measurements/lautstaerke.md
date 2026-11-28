# Lautstärke der Ansagen: einstellen und ausprobieren

**Stand:** 28. November 2026 · **Status:** gebaut, noch nicht auf dem Telefon gelaufen

## Worum es geht

Die Terminerinnerung war viel zu leise. Der wahrscheinliche Grund steht im Android-Quelltext und in den Mitschnitten vom Telefon vom 27. November:

- Seit Android 17 hat alles, was ein Assistent spricht, eine **eigene Lautstärke**, getrennt von der Medienlautstärke. Sie hat 15 Stufen und stand auf dem Telefon bei 5, dann 13, dann 8. Stufe 5 ist sehr leise.
- Die App hat ihre Ansagen bisher als Assistent gespielt. Eine App kann diese Lautstärke nicht stellen, und der Medien-Regler ändert sie nicht. Verstellt haben sie am 27. November Meldungen des Hörgeräts während einer Sprach-Sitzung.

Deshalb spielt die App ihre Ansagen ab diesem Build **als Medien**. Dann gilt für sie die Medienlautstärke des Telefons: der normale Regler und die Lautstärketasten.

Eines kann keine App ändern: Das Hörgerät nimmt vom Telefon keine Lautstärke an. Das Telefon macht den Ton nur leiser oder lässt ihn, wie er ist. **Die höchste Stufe ist das Lauteste, was das Telefon senden kann.** Reicht das nicht, bleibt die Einstellung des Hörgeräts beim Akustiker (mehr Verstärkung in den Streaming-Programmen).

Nichts davon ist bisher auf dem Telefon gelaufen. Diese Proben zeigen, ob es stimmt. Die Quellen: [`../research/announce-volume.md`](../research/announce-volume.md) (englisch).

## Was neu in der App ist

Der Abschnitt **„Ansagen“**, zwischen „Starten“ und „Kalender“:

- Oben eine Zeile, die sagt, womit eine Ansage jetzt gespielt würde, zum Beispiel „Medienlautstärke fürs Hörgerät: Stufe 15 von 25“.
- **Der Regler.** Er hat die echten Stufen von Android. Beim Loslassen sagt die App „Lautstärke Stufe 18“ ins Hörgerät, so laut wie eine Ansage. Darunter steht, sobald die Ansage zu Ende ist, was wirklich passiert ist: „Gespielt mit Stufe 18“, warum Android die Stufe nicht genommen hat, oder „Abgebrochen: …“, wenn sie nicht zu Ende gesprochen wurde.
- **Der Schalter „Eigene Lautstärke nur für Ansagen“.**
  - Aus: Ansagen sind so laut wie die Medien des Telefons. Der Regler stellt dann genau diese Medienlautstärke, dauerhaft, wie die Lautstärketasten.
  - An: Der Regler gilt nur für Ansagen. Die App stellt die Medienlautstärke für die Dauer der Ansage auf diese Stufe und danach zurück. Musik und Videos bleiben, wie sie waren.
- **„Vorlauf vor der Ansage“**: die Stille vor dem Ton (0,5 s, 1,5 s oder 3 s). Für die Probe 5. Ein längerer Vorlauf kostet Hörzeit: Das Hörgerät bleibt bei jeder Ansage entsprechend länger im Streaming-Programm.
- **„Lautstärke-Kanal“**: „Medien“ (neu) oder „Assistent“ (wie bisher). Für die Proben 6 und 9, und als Weg zurück.

Im Abschnitt **„Kalender“** der Knopf **„Erinnerung testen“**. Er spielt die Erinnerung für den nächsten Termin genau so, wie sie zehn Minuten vorher klingt: „In 10 Minuten: Zahnarzt“. Ohne Termin sagt er „In 10 Minuten: Beispieltermin“. Er geht auch, wenn „Termine ansagen“ aus ist.

## Vorbereitung

- Hörgerät verbunden, die App offen. Den Build notieren, der oben in der App steht.
- **Nichts anderes spielt:** keine Musik, kein Podcast, kein Video. Läuft etwas, verstellt die App nichts, damit es nicht mit lauter wird.
- **„Bitte nicht stören“ aus.** Schaltet es Medien stumm, sind auch die Ansagen stumm.
- Das Telefon auf „Vibrieren“, wie bei der Hörprobe: Dann kommen keine Klick-Töne dazwischen. Die Ansagen sind trotzdem zu hören.
- In der Zeile oben im Abschnitt „Ansagen“ muss eine Stufe stehen. Steht dort „Das Hörgerät ist gerade nicht der Tonausgang“: in Androids Auswahl der Medienausgabe (über die Lautstärke-Anzeige oder die Medien-Steuerung in den Schnelleinstellungen) das Hörgerät wählen.
- **Vorher merken, auf welcher Stufe die Medienlautstärke steht** (die Zeile oben im Abschnitt „Ansagen“). Auf diese Stufe kommt der Regler nach Probe 2 zurück, und am Ende muss sie wieder dort stehen.
- Für die ersten Proben ein ruhiger Raum.

## Die Proben

Zwischen zwei Ansagen fünf Sekunden warten. Ein Satz je Probe genügt, am einfachsten gleich in „Marker fürs Protokoll“ im Abschnitt „Starten“.

1. **Erster Eindruck.** Nichts verstellen. „Erinnerung testen“ drücken. Ist die Erinnerung lauter als bisher, gleich oder leiser?
2. **Die Stufen nach Gehör.** Schalter „Eigene Lautstärke nur für Ansagen“ **aus**. Den Regler nacheinander auf etwa ein Fünftel, zwei Fünftel, drei Fünftel, vier Fünftel und ganz nach oben stellen (bei 25 Stufen: 5, 10, 15, 20, 25) und jedes Mal loslassen. Die App sagt die Stufe an.
   - Wird jede Stufe hörbar lauter?
   - Ab welcher Stufe ist die Ansage gut zu verstehen?
   - Ist die höchste Stufe laut genug? Wenn möglich dasselbe noch einmal draußen oder bei Lärm.
   - **Danach den Regler wieder auf die gemerkte Stufe stellen.** In dieser Einstellung stellt der Regler die Medienlautstärke des Telefons dauerhaft; bleibt er oben, startet die nächste Musik im Hörgerät auf der höchsten Stufe.
3. **Eigene Lautstärke nur für Ansagen.** Der Regler steht auf der gemerkten Stufe (das ist die Medienlautstärke des Telefons). Jetzt den Schalter **an** und den Regler auf die Stufe, die in Probe 2 gut war. Die App sagt die Stufe an.
   - Steht unter dem Regler, wenn die Ansage zu Ende ist, „Gespielt mit Stufe …“ mit der gewählten Stufe?
   - Steht in der Zeile oben danach wieder die gemerkte Stufe?
   - Klang das letzte Wort so laut wie der Rest?
   - Danach kurz Musik anmachen: so laut wie sonst, nicht so laut wie die Ansage?
4. **Die Lautstärketasten während einer Ansage.** Dass die Tasten eine laufende Ansage erreichen, steht bisher nur im Android-Quelltext; diese Probe zeigt, ob es stimmt. Schalter an, „Vorlauf“ auf 3 s. „Erinnerung testen“ drücken und, während sie spricht, zweimal „Lauter“ drücken.
   - Wird die Ansage sofort lauter?
   - Steht der Regler danach höher als vorher, und steht in der Zeile oben wieder die Medienlautstärke von vorher?
   - Dasselbe in der Tasche: „Stundenansage“ im Abschnitt „Starten“ einschalten, zur vollen Stunde das Telefon gesperrt in der Tasche haben und während der Ansage „Lauter“ oder „Leiser“ drücken. Kommt die nächste Ansage mit der neuen Stufe?
5. **Der Vorlauf.** Dafür braucht es eine lange Ansage: einen Termin anlegen, der der nächste ist, mit dem Titel „eins zwei drei vier fünf sechs sieben acht neun zehn elf zwölf“, und „Termine ansagen“ einschalten. Dann „Erinnerung testen“, je einmal mit Vorlauf 0,5 s, 1,5 s und 3 s, bei derselben Stufe.
   - Sind die ersten Wörter leiser als die letzten? Wird die Ansage beim Zählen lauter?
   - Ab welchem Vorlauf ist der Anfang so laut wie das Ende?
   - Den Vorlauf danach auf den kürzesten stellen, bei dem der Anfang gut war. Der Grund: Mit 3 s bleibt das Hörgerät bei jeder Ansage, auch bei jeder Stundenansage und jeder Erinnerung, rund 2,5 s länger im Streaming-Programm, solange die Einstellung so steht.
6. **Die zwei Kanäle.** „Lautstärke-Kanal“ auf „Assistent“. Die Zeile oben nennt jetzt Androids eigene Stufe („Android spielt Ansagen mit Stufe … von 15“). „Erinnerung testen“.
   - Lauter oder leiser als im Kanal „Medien“ bei der Stufe aus Probe 2?
   - Mit eingeschaltetem Display während der Ansage „Lauter“ drücken: Welcher Regler erscheint, und wird die Ansage lauter?
   - Danach zurück auf „Medien“.
7. **Im Alltag.** Einen Termin anlegen, der in zwölf Minuten beginnt, das Telefon sperren und einstecken. Kommt die Erinnerung zehn Minuten vorher so laut wie die Probe?
8. **Hörgerät mitten in der Ansage ausschalten.** Schalter an, Vorlauf 3 s, „Erinnerung testen“, dann das Hörgerät ausschalten oder in die Ladeschale legen.
   - Aus dem Telefon darf nichts zu hören sein.
   - Die Lautstärke des Telefon-Lautsprechers darf sich nicht geändert haben.
   - Hörgerät wieder an, **fünf Sekunden warten** (so lange braucht die App, um nachzusehen und zurückzustellen), Musik anmachen: so laut wie vorher, nicht so laut wie die Ansage? In der Zeile oben die gemerkte Stufe?
   - Dasselbe noch einmal, aber das Hörgerät erst **direkt nach dem letzten Wort** ausschalten. Wieder an, fünf Sekunden warten, Musik: so laut wie vorher?
9. **Ansage über Musik.** Leise Musik im Hörgerät laufen lassen, Schalter aus, „Erinnerung testen“. Dann dasselbe im Kanal „Assistent“.
   - In welchem Kanal ist die Erinnerung über der Musik besser zu verstehen? Im Kanal „Medien“ dreht Android die Musik unter der Ansage weniger weit herunter als im Kanal „Assistent“ (laut Quelltext; ungeprüft).
   - Ist die Musik danach so laut wie vorher?
   - Danach zurück auf „Medien“.
10. **Antippen während einer Ansage.** Schalter an, „Ansage jetzt“ im Abschnitt „Starten“, und während sie spricht das Hörgerät doppelt antippen (die Sitzung beginnt). Die Sitzung zu Ende gehen lassen („Beende“), fünf Sekunden warten, Musik anmachen.
    - Ist die Musik so laut wie vorher, nicht so laut wie die Ansage? In der Zeile oben die gemerkte Stufe?
11. **Tonausgang während des Vorlaufs umschalten.** Vorlauf 3 s, „Ansage jetzt“, und sofort, noch in der Stille vor dem Ton, in Androids Auswahl der Medienausgabe „Dieses Smartphone“ wählen. Das Telefon ans Ohr halten.
    - Aus dem Telefon darf kein Wort kommen, auch keine erste Silbe. Danach wieder das Hörgerät wählen.
12. **Keine Aufnahme (wenn Zeit ist).** Androids Bildschirmaufnahme mit „Geräteton“ starten, „Erinnerung testen“, Aufnahme stoppen und anhören.
    - In der Aufnahme darf von der Erinnerung nichts zu hören sein. Danach die Aufnahme löschen.

## Was wir wissen möchten

- Den Build.
- Zu jeder Probe den einen Satz.
- Die Stufe, die gut ist, und ob die höchste Stufe reicht.
- Ist die Ansage auch im stillen Raum zu leise, oder vor allem draußen und bei Lärm?
- Ist der Ton vor der Ansage im Verhältnis zur Stimme zu leise, zu laut oder passend?

## Danach

- Den Vorlauf auf den kürzesten stellen, der gut war, und den Kanal auf „Medien“ (oder auf den, der besser war).
- Prüfen, dass in der Zeile oben im Abschnitt „Ansagen“ wieder die gemerkte Stufe steht. Wenn nicht: bei ausgeschaltetem Schalter den Regler dorthin stellen und es dazuschreiben.
- „Heute teilen“ drücken und das Protokoll schicken.

## Welche Zeile im Protokoll was beantwortet

| Frage | Zeilen im Protokoll |
|---|---|
| Spielt Android die Ansage wirklich unter der Medienlautstärke? | `announce_volume`: `channel=media` und `governing=3`. Dazu `announce_volume_mid` mit `musicActive=true`, wenn sonst nichts spielt. `governing=11` oder `musicActive=false` hieße: Auf diesem Telefon gilt doch die Assistenten-Lautstärke; die Zeile oben im Abschnitt sagt das dann auch. Die Zeile `announce_volume_mid` kommt eine halbe Sekunde nach dem ersten Wort; bei einer Ansage, die kürzer ist, fehlt sie |
| Mit welcher Stufe wurde gespielt? | `announce_volume`: `mGot` von `mMax`, solange `mRoute=aid` dasteht (sonst gehört die Zahl einem anderen Ausgang). `mDb` ist, was diese Stufe auf dem Telefon in Dezibel bedeutet (0 ist das Lauteste) |
| Hat Android die Stufe der App übernommen? | `announce_volume` mit `action=set` (gestellt), `already` (stand schon so), `kept` (Schalter aus), `skipped` (nicht angefasst, `why` sagt warum) oder `refused` (Android hat sie nicht genommen, `why` nennt, was die App sehen kann) |
| Ist die Medienlautstärke danach wieder wie vorher? | `announce_volume_restore` mit `result=restored`, und zwei Sekunden später `volume_state` mit `trigger=restore_check` und `leftover=confirmed`: Erst dann gilt der Wert als sicher zurück. `adopted`: Die Tasten wurden während der Ansage gedrückt, `newStep` ist die neue Stufe. `deferred_route`: Das Hörgerät war nicht mehr der Tonausgang (ausgeschaltet, oder eine Sitzung oder ein Anruf hat übernommen). `deferred_going_away`: Android hat gemeldet, dass der Ausgang gleich weg ist. In beiden Fällen schreibt die App nichts und merkt sich den Wert. `left_muted`: Die Medien wurden während der Ansage stumm geschaltet; die App wartet, bis sie wieder laut sind. `restore_refused`: Android hat das Zurückstellen nicht genommen |
| Haben die Lautstärketasten die Ansage erreicht? | `announce_volume_seen` mit `mWas` und `mNow`, zur Zeit des Tastendrucks |
| Hat die App etwas nachgeholt? | `volume_state` mit `leftover=restored`; `trigger` nennt den Anlass (`aid_route`: Das Hörgerät ist wieder da, und `mIndex` zeigt, welche Stufe Android ihm beim Verbinden gegeben hat; `voice_link`: Eine Sitzung ist zu Ende; `mode`: Ein Anruf ist zu Ende; `devices`: Ein Tongerät kam oder ging; `dnd`: „Bitte nicht stören“ hat sich geändert; `announcement`: vor einer Ansage; `app_start`; `screen`: Die App kam auf den Bildschirm; `section`: Der Abschnitt „Ansagen“ wurde aufgeklappt). Nachgeholt wird nur, während das Hörgerät der Tonausgang ist und die Medien nicht stumm sind; sonst steht dort `waiting_route` oder `waiting_unmute`. Schaltet man die Medien von Hand wieder laut oder wählt in Androids Ausgabe-Auswahl wieder das Hörgerät, erfährt die App das nicht sofort: Sie holt es bei der nächsten Ansage nach oder wenn sie auf den Bildschirm kommt |
| Wo steht Androids Assistenten-Lautstärke? | `aIndex` von `aMax` in `announce_volume` und `volume_state` |
| Bekommt die App die Berechtigung, die Assistenten-Lautstärke zu stellen? | `volume_state`: `manageAssistantAudio=granted` oder `denied`. Die App nutzt sie in diesem Build nicht |
| Was bedeuten die Stufen auf diesem Telefon in Dezibel? | `volume_curve`, einmal je Android-Build, beim ersten Start der App mit diesem Build (nicht noch einmal nach einem Update der App): `mDb` für die Medien, `aDb` für den Assistenten, je ein Wert pro Stufe ab Stufe 1. Deshalb das Protokoll des Tages teilen, an dem dieser Build zum ersten Mal lief |
| Wie lang war der Vorlauf, wie laut der Ton? | `announce_start`: `leadInMs`, `chimeAmplitude` |
| Was wurde wann verstellt? | `announce_setting` (Schalter, Stufe, Vorlauf, Kanal; `origin=keys` ist eine Stufe von den Lautstärketasten) und `volume_slider` (der Regler hat die Medienlautstärke des Telefons gestellt) |
| Was hat „Erinnerung testen“ gespielt? | `calendar_test` mit `kind=rehearsal`: `sample=true` heißt „Beispieltermin“, `sampleWhy` nennt den Grund. Der Titel steht nicht im Protokoll |

## Wenn etwas nicht stimmt

- **„Das Hörgerät ist gerade nicht der Tonausgang“, obwohl es verbunden ist:** Im Kanal „Medien“ gehen Ansagen dorthin, wohin Android die Medien schickt. Ist dort „Dieses Smartphone“ gewählt, sagt die App nichts an (`announce_skipped`), denn aus dem Telefon soll nie etwas kommen. In Androids Auswahl der Medienausgabe das Hörgerät wählen.
- **„Android hat die Änderung nicht übernommen (…)“:** Der Grund steht in der Klammer. Bei „Bitte nicht stören“: ausschalten. Bei „Grund unbekannt“ bitte das Protokoll schicken; die Ansage kam dann mit der Lautstärke des Telefons.
- **„Nicht verstellt, weil gerade etwas anderes im Hörgerät spielt“:** Musik oder ein Video läuft. Die App fasst die Lautstärke dann nicht an.
- **Eine Ansage ist stumm:** „Bitte nicht stören“ oder die Medien sind stumm geschaltet. Im Kanal „Medien“ sagt es die Zeile oben im Abschnitt. Im Kanal „Assistent“ kann die App eine Stummschaltung nicht sehen; die Zeile sagt dort nur, dass „Bitte nicht stören“ an ist.
- **Unter dem Regler steht „Abgebrochen: …“:** Die Probe-Ansage wurde nicht zu Ende gesprochen, zum Beispiel weil das Hörgerät nicht mehr der Tonausgang war. Der Grund steht dabei oder im Protokoll (`announce_end`).
- **Die Zeile oben sagt, Android führe Ansagen nicht unter der Medienlautstärke:** Dann wirkt der Regler auf diesem Telefon nicht auf die Ansagen. Bitte genau das melden und das Protokoll schicken.
- **Musik ist nach einer Ansage lauter als vorher:** Das soll nie passieren. Die Uhrzeit notieren, die App öffnen (sie sieht dann nach und stellt zurück, wenn der Wert noch ihrer ist), sonst die Lautstärke von Hand zurückstellen, und das Protokoll schicken.
- **Im Kanal „Medien“ ist alles schlechter als vorher:** „Lautstärke-Kanal“ auf „Assistent“ stellen. Dann verhält sich die App wie die Builds davor.
- **Auch die höchste Stufe ist zu leise:** Dann ist das Telefon am Ende. Bitte genau das melden, mit der Antwort auf „stiller Raum oder Lärm“. Was dann bleibt: beim Akustiker mehr Verstärkung für die Streaming-Programme, oder ein späterer Build, der die Sprache selbst lauter rechnet.
