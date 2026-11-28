# Hörprobe: Was macht ein Ton-Kanal mit dem Hörgerät?

**Stand:** 28. November 2026 · **Status:** vorbereitet, noch nicht gemacht

## Worum es geht

Die App soll das Hörgerät nicht bei seiner eigentlichen Aufgabe stören: die Umgebung zu verstärken.

Bekannt ist aus dem Code, aus Phonaks Unterlagen und aus dem Android-Quelltext: Die App hat keinen Befehl, mit dem sie am Hörgerät etwas umschaltet, und sie verstellt keine Lautstärke. Aber jeder Ton vom Telefon, auch eine Ansage der App, lässt das Hörgerät von selbst in ein Streaming-Programm wechseln und danach wieder zurück. Laut Phonak bleiben die Mikrofone dabei in der Standardeinstellung an. Wie laut die Umgebung in dieser Zeit bleibt und wie lange der Weg zurück dauert, steht nirgends. Das kann nur das Ohr sagen. Alles Übrige schreibt die App jetzt mit.

Die Quellen dazu: [`../research/hearing-function.md`](../research/hearing-function.md) (englisch).

## Vorbereitung

- Hörgerät verbunden, die App offen, Abschnitt „Starten“ aufgeklappt.
- Ein Radio mit einer Sprechsendung, etwa zwei Meter entfernt, in normaler Zimmerlautstärke. Es geht auch jemand, der gleichmäßig vorliest. Die Lautstärke während der Probe nicht ändern.
- **Das Telefon auf „Vibrieren“ stellen.** Sonst spielt Android bei jedem Druck auf einen Knopf einen Klick-Ton, und der geht wie jeder Ton des Telefons ins Hörgerät: Er öffnet denselben Kanal, um den es hier geht. Dasselbe gilt für Tastatur- und Benachrichtigungstöne. Die Ansagen der App sind auch bei „Vibrieren“ zu hören. Wer das Telefon nicht umstellen will, schaltet in den Einstellungen unter Ton und Vibration die Töne beim Tippen aus. Die Knöpfe im Abschnitt „Starten“ sollen von sich aus nicht klicken; ob das auf dem Telefon so ist, ist noch nicht ausprobiert.
- Im Protokoll unten in der App steht nach der Installation, kurz nach `process_start`, eine Zeile `aid_channels_watching`: Die App schreibt die Kanäle mit. Steht dort `aid_channels_no_permission`, fehlt die Berechtigung für Geräte in der Nähe: in „Einrichtung“ erteilen und die App noch einmal öffnen. An späteren Tagen steht dasselbe in der ersten Zeile des Tages, `journal_day`, als `aidChannelsWatching=true`.
- Für einen späteren Tag mit normalem Gebrauch die Töne des Telefons wieder anlassen: Dann zeigt „Zähler heute“, wie viel Kanal-Zeit vom Telefon selbst kommt.

## Die Proben

Bei jeder Probe auf das Radio achten, nicht auf die Ansage. **In dem Moment, in dem das Radio wieder klingt wie vorher, „Klingt wieder normal“ drücken.** Hat sich nichts geändert, den Knopf nicht drücken und das notieren. Zwischen zwei Proben eine halbe Minute warten.

1. **Ansage.** „Ansage jetzt“ drücken. Dreimal.
2. **Stille.** „Stille-Test (5 s)“ drücken. Die App spielt fünf Sekunden Stille. Zu hören ist nichts, der Musik-Kanal ist trotzdem offen, und zwar etwa acht Sekunden: Android hält ihn nach jedem Ton noch drei Sekunden nach. Ändert sich das Radio in dieser Zeit? Dreimal.
3. **Sitzung.** Doppel-Tap am Hörgerät, einmal eine Taste am Shelly drücken, dann die Sitzung auslaufen lassen, bis „Beende“ kommt (30 Sekunden ohne Druck). Ohne angelernten Shelly startet der Tap den Echo-Test; auch der ist eine Sitzung.
4. **Sitzung mit zweitem Tap**, wer mag: Doppel-Tap, nach ein paar Sekunden noch ein Doppel-Tap. Die Sitzung endet, „Beende“ kommt kurz danach über den Musik-Kanal.

## Was zu notieren ist

Ein Satz je Probe genügt, am einfachsten gleich in „Marker fürs Protokoll“, zum Beispiel „Probe 1: Radio deutlich leiser, ab dem ersten Ton“.

- Wird das Radio leiser, dumpfer, lauter, oder bleibt es gleich?
- Ab wann: mit dem ersten Ton oder schon davor?
- Lässt sich dem Radio während der Ansage oder der Sitzung noch folgen?
- Kommt ein Ton vom Hörgerät selbst, der nicht von der App ist?
- Bei Probe 3: Klingt die Umgebung in der Sitzung anders als bei der Ansage?
- Wer mag: in myPhonak nachsehen, welcher Programmname während der Sitzung angezeigt wird.

## Danach

„Heute teilen“ drücken und das Protokoll schicken, dazu den Build, der oben in der App steht. Im Abschnitt „Zähler heute“ stehen zwei neue Zeilen: wie oft und wie lange heute ein Kanal zum Hörgerät offen war, und wie viel davon auf die App geht.

## Welche Zeile im Protokoll was beantwortet

| Frage | Zeilen im Protokoll |
|---|---|
| Wie lange war der Musik-Kanal für eine Ansage offen? | `aid_media_stream` mit `state=stopped` und `announcing=true`: der Wert `onMs` |
| Wie lange hält Android den Kanal nach dem letzten Wort noch offen? | Zeit von `aid_media_stream state=stopped` minus Zeit von `announce_end`. Erwartet: etwa 3 s |
| Schaltet das Hörgerät schon um, wenn nur Stille kommt? | die Notiz zu Probe 2, neben `announce_start` mit `silence=true` und den `aid_media_stream`-Zeilen darum. Die Probe zählt nur, wenn `aid_media_stream state=playing` erst nach `announce_start` kommt: War der Kanal schon offen, war vorher ein Ton darin |
| Wie lange braucht das Hörgerät selbst für den Weg zurück? | Zeit von `marker` mit `text=surroundings_normal` minus Zeit der Zeile davor, die den Kanal schließt: `aid_media_stream state=stopped` nach einer Ansage, `hfp_audio_state state=disconnected` nach einer Sitzung |
| Wie lange war der Sprach-Kanal für eine Sitzung offen? | `hfp_audio_state` mit `state=disconnected` und `session=true`: der Wert `onMs` |
| Öffnet Android nach einer Sitzung noch kurz den Musik-Kanal? | ein `aid_media_stream state=playing` mit `announcing=false` in den Sekunden nach `hfp_audio_state state=disconnected`. Nur bei „Vibrieren“ aussagekräftig: Ein Klick-Ton in diesen Sekunden sieht genauso aus |
| Nach dem zweiten Tap: ein Wechsel oder zwei? | die Reihenfolge von `tap_stop`, `hfp_audio_state state=disconnected`, `aid_media_stream state=playing`, `announce_end`, `aid_media_stream state=stopped`, und die Notiz zu Probe 4 |
| Was kommt vom Telefon selbst, ohne die App? | `aid_media_stream` mit `state=stopped` und `announcing=false`, und `hfp_audio_state` mit `state=disconnected` und `session=false` (Telefonate). `state=not_playing` ist kein Ton: Das meldet Android bei jedem Verbinden des Hörgeräts |
| Blieb ein Sprach-Kanal nach einer Sitzung hängen? | `session_link_down` mit `down=false`, danach `aid_voice_link_follow_up` oder `aid_voice_link_stuck`. Beim ersten `aid_voice_link_stuck` vibriert das Telefon. Schließen kann die App so einen Kanal meist nicht, sie meldet ihn |

## Wenn etwas nicht stimmt

- **Keine einzige Zeile `aid_media_stream`, obwohl die Ansage zu hören war:** Dann erreicht Androids Meldung die App auf diesem Telefon nicht. Das ist selbst ein Ergebnis; bitte so melden.
- **„Stille-Test“ schreibt `announce_skipped`:** Das Hörgerät ist nicht verbunden oder nicht der einzige Ausgang. Die Zeile nennt den Grund.
- **Das Hörgerät bleibt nach einer Probe auffällig lange leise:** die Zeit notieren. Laut Phonak startet das Hörgerät neu, wenn seine Taste 15 Sekunden gehalten wird; die Einstellungen bleiben dabei erhalten.
