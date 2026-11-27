# Lara im Ohr – Mess-App (Version 0.1): Design

**Stand:** 27. November 2026 · **Status:** vom Auftraggeber am 27.11.2026 zum Bauen freigegeben; wird mit den Messungen fortgeschrieben
**Vorgänger:** Projekt-Summary vom 17.11.2026 (außerhalb des Repos). Wo dieses Dokument dem Summary widerspricht, gilt dieses Dokument.

## Nachtrag: Stand nach dem ersten Gerätetest (27.11.2026, abends)

Die erste Ausbaustufe läuft auf dem Telefon. Dieser Abschnitt hält fest, was dabei herauskam. Wo er dem Rest des Dokuments widerspricht, gilt der Nachtrag.

**Am Gerät belegt**

- Die per ADB installierte App lässt sich als digitaler Assistent wählen und behält die Rolle über Updates mit demselben Signaturschlüssel.
- Ein Doppeltipp auf das Hörgerät startet die App bei gesperrtem Telefon mit ausgeschaltetem Display. Das Display bleibt aus.
- Die Sprachverbindung steht nach 0,6 bis 0,8 s. Die Aufnahme kommt vom Hörgeräte-Mikrofon und ist nicht stummgeschaltet. Die Wiedergabe landet im Hörgerät.
- Eine Ansage ohne Auslöser über den Musik-Kanal funktioniert (bisher bei eingeschaltetem Display geprüft).

**Noch nicht belegt:** dasselbe nach Stunden Ruhe und in der Hosentasche; die Stundenansage aus dem Tiefschlaf; alles rund um den Shelly.

**Was sich gegenüber diesem Dokument geändert hat**

- *Audio-Sitzung (6.5):* Es gibt nur noch einen Weg. Die App antwortet mit `startVoiceRecognition` und wartet, bis die Verbindung steht. Sie fordert die Audio-Route in dieser Betriebsart **nicht** zusätzlich selbst an: Das führte dazu, dass Android die Verbindung als „virtuellen Anruf" öffnen wollte und die Sprachsitzung dabei abbrach (im ersten Build scheiterte deshalb einer von drei Taps). Die Verbindung bleibt nur bestehen, solange die App etwas abspielt; deshalb läuft durchgehend eine eigene Tonspur mit. Der „virtuelle Anruf" ist nur noch der Ersatzweg, falls das Hörgerät die Sprachsitzung ablehnt.
- *Hörgerät festlegen (neu):* Der Besitzer wählt einmal aus, welches Bluetooth-Gerät das Hörgerät ist. Töne, Ansagen und Sitzungen gehen ausschließlich an dieses Gerät, nie an Auto, anderes Headset oder Telefonlautsprecher. Die Sprachtaste anderer Geräte reicht die App an den bisherigen Assistenten weiter. Fehler vor dem Aufbau der Verbindung werden per Vibration gemeldet, nicht per Ton.
- *Ansagen ohne Auslöser (neu):* Über den Musik-Kanal, ausgelöst per Wecker. Als Test gibt es eine abschaltbare Ansage der Uhrzeit zu jeder vollen Stunde. Sie liefert nebenbei stündlich einen Messpunkt nach langer Ruhe.
- *Absicherung der Sitzung (neu):* Obergrenze von fünf Minuten je Sitzung, Abbruch bei Verlust des Audio-Fokus (Anruf, Wecker), Aufräumen einer offen gebliebenen Verbindung nach einem Absturz.
- *Shelly-Messung (6.4 B, 7):* Verpasste Drücke werden über die fortlaufende Paketnummer des Shelly gezählt, nicht durch Mitzählen. Der Scan wird nur während einer offenen Sitzung hochgefahren. Für lange Reihen gibt es einen stillen Modus, der nur mitschreibt.
- *Scan-Drossel (3):* Am Telefon ist sie als Einstellung gesetzt. Ob und wie stark sie wirkt, ist noch nicht gemessen.

## 1. Worum es geht

Lara ist eine persönliche KI-Assistentin (Hermes Agent auf eigenem Server). Sie soll über ein Phonak-Hörgerät und eine ertastbare Taschen-Fernbedienung bedienbar sein, während das Telefon gesperrt und dunkel in der Hosentasche bleibt.

Bisher ist keine Ende-zu-Ende-Funktion nachgewiesen. Ob Android das überhaupt zulässt, hängt an vier Fragen, die sich nur am echten Gerät beantworten lassen. Version 0.1 ist deshalb eine **Mess-App**: Sie enthält bereits das Fundament der späteren App (Rechte, Eingänge, Audio-Weg, Protokoll), aber noch kein Menü und keine Hermes-Anbindung. Ihr Ergebnis sind belastbare Messwerte und eine Go/No-Go-Aussage je Eingang.

## 2. Ziele, Nicht-Ziele, Anti-Ziele

**Ziele von Version 0.1**

1. Die vier Blocker am echten Gerät messen, alles bei gesperrtem Telefon mit dunklem Display:
   - Shelly-Tastendruck kommt an, auch der erste nach Stunden Ruhe.
   - Phonak-Tap startet unsere App, das Display bleibt aus.
   - Aufnahme kommt vom Hörgeräte-Mikrofon, Ausgabe landet im Hörgerät.
   - Medientasten kommen an, ohne dem normalen Player etwas wegzunehmen.
2. Jede Messung ist blind bedienbar: Rückmeldung kommt als Ton oder Ansage ins Hörgerät.
3. Jedes Ereignis steht mit vollem Kontext in einem dauerhaften, exportierbaren Protokoll.
4. Die App kommt als signiertes APK aus einem GitHub-Release und lässt sich darüber aktualisieren.
5. Das Repo hat eine README, die das Projekt erklärt und verwandte Projekte für Hörgeräteträger verlinkt.

**Nicht-Ziele von Version 0.1** (kommen später, jeweils mit eigenem Design)

- Angesagtes Menü, Sprachsteuerung, Spracherkennung.
- Hermes-Anbindung, Erinnerungen, Benachrichtigungen.
- Verschlüsselte Shelly-Pakete. Verschlüsselung lässt sich am Shelly nur per Werksreset wieder ausschalten und wird erst nach den Messungen entschieden.
- Betrieb nach einem Neustart vor dem ersten Entsperren.
- Play Store, andere Telefone, andere Hörgeräte, Flic oder Eigenbau-Fernbedienung.
- Steuerung des Hörgeräts selbst (Lautstärke, Programme).

**Anti-Ziele** (wären ein Fehlschlag, auch wenn es technisch läuft)

- Das Display geht durch die App an.
- Das Mikrofon läuft ohne bewusste Auslösung.
- Musik- oder Podcast-Apps verlieren ihre Medientasten.
- Schutzmechanismen von Android werden über Programmierlücken umgangen. Erlaubt sind nur dokumentierte Wege, die der Besitzer bewusst per Einstellung oder ADB freischaltet.
- Fehler bleiben still. Was scheitert, wird angesagt oder protokolliert.
- Geheimnisse, Geräteadressen oder Mitschnitte landen im öffentlichen Repo.

## 3. Randbedingungen

**Vom Auftraggeber festgelegt**

- Native Android-App in Kotlin, kein Cross-Plattform-Framework.
- Keine Store-Veröffentlichung. Verteilung als APK über ADB und GitHub Releases.
- Maximale Debug-Tiefe, keine künstlichen Einschränkungen beim Plattformzugriff.
- Repo `Domi-cc/phonak-assistant`, öffentlich, Apache-2.0.
- Hardware vorhanden: Pixel, Phonak Audéo EON R (nur rechts), Shelly BLU RC Button 4 (Modell SBBT-004CUS, **ohne** Zigbee).

**Am Gerät gemessen (27.11.2026, per ADB)**

| Punkt | Befund |
|---|---|
| Telefon | Pixel 10 Pro XL, Android 17, SDK 37.1, Build CP3A.261005.005 |
| Hörgerät | als „R-Phonak Hörgerät" gekoppelt; Freisprechen (HFP), Musik (A2DP) und Medienbefehle (AVRCP) verbunden; zusätzlich nur Auracast-Empfang, kein zweiseitiges LE Audio |
| Phonak-Tap | meldet sich als Freisprech-Sprachtaste; Android startet binnen rund 60 ms eine Activity mit `android.intent.action.VOICE_COMMAND`; das gilt auch gesperrt und dunkel, das Display bleibt aus |
| Zweiter Tap | innerhalb von 5 s nach einem unbeantworteten Tap wird verworfen; ein Tap in laufender Sitzung beendet sie |
| Referenz Google-App | öffnet die Sprachverbindung über `BluetoothHeadset.startVoiceRecognition`; Verbindung steht nach 0,4 bis 0,7 s in Breitband-Qualität (16 kHz); im Sperrzustand bricht Google nach etwa 5 s von selbst ab |
| Sprachtasten-Standard | Bewerber: Google-App und Home Assistant; ohne festen Standard geht der Tap im Sperrzustand ins Leere |
| Assistentenrolle | hält die Google-App |
| Scan-Drossel | als Einstellung gesetzt (`scan_allowance_throttling_enabled=true`); ob sie wirkt, ist noch nicht gemessen |
| Medientasten-Recht | `SET_MEDIA_KEY_LISTENER` ist per ADB erteilbar; der Platz war frei |
| Audio-Härtung | aktiv; das System protokolliert Verstöße unter `dumpsys audio` |

**Aus dem Android-17-Quellcode gelesen und gegengeprüft, am Gerät noch zu bestätigen**

- Löst ein Shelly-Paket oder ein Wecker eine Sitzung aus, gibt es das Mikrofon nur, solange das System den `VoiceInteractionService` der App gebunden hält. Das ist bei der als Assistent gewählten App der Fall, nach dem ersten Entsperren. Die Akku-Ausnahme allein reicht fürs Mikrofon nicht. Der Phonak-Tap bringt laut Quellcode ein eigenes Zeitfenster von 10 s mit. Sprachausgabe braucht bei `targetSdk` 36 nur einen laufenden Vordergrunddienst.
- Die Rolle wird bei jeder Paketänderung neu geprüft. Fehlt in einem Build einer der drei Dienste (Interaktion, Sitzung, Erkennung), fliegt die App still aus der Rolle und Google rückt nach.
- Scan-Drossel: Ausgenommen ist nur eine App mit sichtbarer Oberfläche. Für alle anderen gilt pro Stunde ein Kontingent von 6 Minuten im schnellsten Scan-Modus oder 24 Minuten im mittleren. Danach bleibt bei dunklem Display nur der Sparmodus (0,5 s Lauschen alle 10 s). Dieses Shelly-Modell sendet laut Änderungsprotokoll des Herstellers pro Geste etwa eine Sekunde lang. Das trifft der Sparmodus rechnerisch in etwa 15 % der Fälle.
- Scan-Zeiten, Drossel und Zeitlimits lassen sich per ADB nicht verstellen.
- Der Shelly wertet erst die Geste aus (einfach, doppelt, dreifach, lang, gehalten) und sendet dann. Schnelle Wiederholungen derselben Taste verschmelzen zu einer Geste. Die Wartezeit dafür ist am Gerät in sechs Stufen zwischen 55 und 800 ms einstellbar.
- Zwei Tasten länger als 10 s gehalten öffnen am Shelly den Kopplungsmodus. Vor Firmware 1.0.22 reichte dafür eine einzelne lang gehaltene Taste.
- Beim Halten einer Taste sendet der Shelly laut Hersteller fortlaufend, bis zu 60 s. Das wäre ein Weg, auch den Sparmodus sicher zu treffen. Ob und wie das am Telefon ankommt, ist offen.
- Für genau dieses Modell berichten Home-Assistant-Nutzer, dass mit der üblichen Scan-Art nur etwa jeder fünfzehnte Druck ankam und mit passiver Scan-Art jeder.
- Am EON R gibt es pro Ohr nur eine Tipp-Funktion. Mit einem Gerät also „Sprachassistent" oder „Pause", nicht beides.

Der veröffentlichte Quellcode ist der von Android 17.0 (Juni 2026). Das Telefon läuft auf einem neueren Stand, und Bluetooth wird unabhängig vom System aktualisiert. Jede Code-Aussage ist deshalb eine Erwartung, keine Garantie.

## 4. Bauvarianten und Entscheidung

| Variante | Idee | Dafür | Dagegen |
|---|---|---|---|
| **1. Eine App, Messmodus zuerst** | Die echte App, zunächst nur mit Messfunktionen | Rechte hängen an Paketname, Signatur und Rolle zusammen und sind nur in der echten App ehrlich messbar; nichts wird weggeworfen | mehr Gerüst vor dem ersten Erfolgserlebnis |
| 2. Wegwerf-Prototypen | drei Mini-APKs, danach Neustart | schnellste Einzelmessung | die Rechte-Frage spannt über alle drei; Einrichtung und Code entstehen doppelt |
| 3. Hermes-Client forken | bestehende Chat-App erweitern | Chat und Hermes geschenkt | Chat-Apps für eingeschaltetes Display; der schwere Teil fehlt dort; fremde Architektur |

**Gewählt: Variante 1** (Entscheidung des Auftraggebers am 27.11.2026).

## 5. Was sich gegenüber dem Summary geändert hat

| Summary | Jetzt |
|---|---|
| Weg A läuft über die Assistentenrolle | Weg A läuft über eine normale Activity für `VOICE_COMMAND`, die einmal als „Immer" gewählt wird. Die Rolle ist stattdessen das Rechte-Fundament für alle Wege. |
| Shelly ist der primäre Weg, auch zum Aufwecken | Shelly ist der Weg zum Navigieren in einer Sitzung. Ob er zum Aufwecken taugt, entscheidet die Messung. Erwartung wegen der Drossel: schlecht. |
| Medientasten über `MediaSession`, nach bestem Bemühen | Medientasten über den per ADB freigeschalteten Tasten-Listener. Eine eigene `MediaSession` wird nicht gebaut. |
| Telefon: Pixel 11 Pro | Pixel 10 Pro XL mit Android 17 |
| Shelly BLU RC Button 4 ZB | Shelly BLU RC Button 4 ohne Zigbee (SBBT-004CUS) |
| Hintergrund-Audio nicht erwähnt | Android 17 schaltet Hintergrund-Audio ohne passende Rechte stumm |

Daraus folgt die Arbeitsteilung, die Version 0.1 überprüft: **Aufwecken per Phonak-Tap, Navigieren per Shelly, Medientasten als Zusatz.**

## 6. Architektur

### 6.1 Überblick

```text
 Phonak-Tap ──HFP──► Android ──VOICE_COMMAND──► Trampolin-Activity ─┐
 Shelly ──BLE-Werbepaket──► Lausch-Dienst (Scan) ──────────────────┤
 Medientaste ──► Tasten-Listener (optional, ADB-Recht) ────────────┼─► Eingangs-Router ─► Modus
 Bildschirm-Knopf / ADB-Befehl ─────────────────────────────────────┘        │
                                                                             ▼
                                              Sitzungs-Dienst (Mikrofon) ─► Audio-Weg zum Hörgerät
                                                                             │
 Assistenten-Dienst (vom System gebunden) = Rechte-Anker           Protokoll (jedes Ereignis)
```

### 6.2 Module

- **`:core`** – reines Kotlin ohne Android-Importe: BTHome-Parser, Ereignismodell, Entprellung, Eingangs-Router, Zähl- und Statistik-Logik, Protokollformat. Alles hier ist auf dem Rechner testbar, und Feldfehler lassen sich aus Protokoll-Auszügen als Test nachstellen.
- **`:app`** – Android-Anbindung und die kleine Oberfläche.

### 6.3 Bausteine in `:app`

| Baustein | Aufgabe |
|---|---|
| **Assistenten-Dienste** (`VoiceInteractionService`, Sitzungsdienst, Erkennungsdienst als Stub) | Machen die App als Assistent wählbar. Die Bindung durch das System ist die Quelle aller Hintergrund-Rechte. `onReady()` ist der Startpunkt: Lausch-Dienst sicherstellen, Tasten-Listener anmelden, Verbindung zum Freisprech-Profil holen. `onShutdown()` wird als „Rechte verloren" protokolliert. |
| **Lausch-Dienst** (Vordergrunddienst, Typ `connectedDevice`, läuft dauerhaft) | Besitzt die BLE-Scans und hält den Prozess wach. Startet Scans erst, nachdem er selbst im Vordergrund ist, weil Android die Einstufung nur beim Scan-Start prüft. Meldet Scans neu an nach Bluetooth-Neustart, Systemstart, App-Update und per Wecker. |
| **Sitzungs-Dienst** (Vordergrunddienst, Typ `microphone`, nur während einer Sitzung) | Führt einen Messablauf aus: Audio-Weg öffnen, Ton, Ansage, Aufnahme, Wiedergabe, schließen. |
| **Trampolin-Activity** (unsichtbar, exportiert) | Empfängt `VOICE_COMMAND`, protokolliert, übergibt an den Router und beendet sich in `onCreate`. Fordert nie das Einschalten des Displays an. |
| **Scan-Empfänger** | Nimmt Treffer des `PendingIntent`-Scans entgegen, auch wenn der Prozess neu gestartet werden musste. |
| **Tasten-Listener** (optional, eigener Adapter) | Meldet sich per Reflection bei `MediaSessionManager.setOnMediaKeyListener` an, sofern das Recht erteilt ist. Fällt sauber weg, wenn nicht. |
| **Audio-Weg** | Öffnet, überwacht und schließt die Sprachverbindung zum Hörgerät; spielt Töne und Ansagen; nimmt auf. |
| **Protokoll** | Schreibt jedes Ereignis dauerhaft, zeigt es an, exportiert es. |
| **Oberfläche** | Ein Bildschirm mit Einrichtung, Zählern und Aktionen, dazu Protokollansicht und Einstellungen. |

Alle Bausteine laufen unter derselben App-Kennung. Das ist Voraussetzung dafür, dass die Rechte der Assistenten-Bindung auch für Scan-Empfänger und Sitzungs-Dienst gelten.

### 6.4 Eingänge

**A – Phonak-Tap**

1. Android startet die Trampolin-Activity.
2. Die App antwortet innerhalb von 5 s mit `BluetoothHeadset.startVoiceRecognition` für das gespeicherte Hörgerät. Die Verbindung zum Freisprech-Profil hält sie dafür vorab bereit.
3. In Version 0.1 löst der Tap den Echo-Test aus (Abschnitt 7).
4. Ein zweiter Tap in laufender Sitzung kommt als „Stopp" an und beendet sie.

Das Hörgerät wird über die gespeicherte Adresse bestimmt, nicht über den Namen, damit nie die Freisprechanlage im Auto oder ein Kopfhörer gewählt wird.

**B – Shelly**

1. Einmaliges Anlernen bei offener App: Scan nach BTHome-Paketen, Taste drücken, Gerät auswählen. Gespeichert wird die Adresse.
2. Danach lauscht der Lausch-Dienst mit zwei gleich gefilterten Scans (Adresse und BTHome-Kennung `0xFCD2`): einem Rückruf-Scan für die Ereignisse und einem `PendingIntent`-Scan als Rettungsweg, der die App neu startet.
3. Beide Scans nutzen die passive Scan-Art. Sie ist für Vergleichsmessungen umschaltbar.
4. Der Parser erkennt unverschlüsselte BTHome-v2-Pakete mit vier Tasten-Feldern. Verschlüsselte oder beschädigte Pakete werden protokolliert und verworfen.
5. Ein Sendestoß enthält viele gleiche Pakete. Entprellt wird über Adresse und Paketnummer innerhalb weniger Sekunden, nie dauerhaft.
6. Das **Scan-Profil** ist umschaltbar, damit sich die Drossel vermessen lässt:

| Profil | Rückruf-Scan | Erwartung bei dunklem Display |
|---|---|---|
| `schnell` | schnellster Modus | trifft sicher, Kontingent nach rund 6 min/h verbraucht |
| `mittel` | mittlerer Modus | trifft sicher, Kontingent nach rund 24 min/h verbraucht |
| `spar` | Sparmodus | etwa jeder siebte Druck, dafür unbegrenzt |
| `nur-rettung` | kein Rückruf-Scan | zeigt, was ohne Dienst übrig bleibt |

7. Tastenbelegung in Version 0.1: Tasten 1, 2 und 4 zählen. Taste 3 startet den Echo-Test. Welche Geste gesendet wurde, wird immer protokolliert.

**C – Medientasten**

1. Voraussetzung ist der einmalige Befehl `adb shell pm grant <paket> android.permission.SET_MEDIA_KEY_LISTENER`.
2. Der Listener hat drei Stellungen: `aus`, `beobachten` (Standard: protokolliert und reicht jede Taste unverändert weiter) und `abfangen` (nur für Tests: Tasten zählen wie Shelly-Tasten und erreichen den Player nicht).
3. Der Listener entscheidet sofort und wartet nie, weil ein hängender Listener jede Medientaste auf dem Telefon um eine Sekunde verzögert.
4. Hat sich bereits eine andere App (etwa die Google-App) angemeldet, meldet Android keinen Fehler. Die App prüft das selbst und zeigt es in der Einrichtung an.

**Bildschirm und ADB** lösen dieselben Abläufe aus und laufen durch denselben Router.

### 6.5 Audio-Sitzung

Eine Sitzung ist eine zusammenhängende Sprachverbindung zum Hörgerät.

1. Sitzungs-Dienst startet als Vordergrunddienst mit Typ `microphone`.
2. Audio-Weg öffnen. Zwei Methoden sind wählbar und werden verglichen:
   - `sprachtaste`: `BluetoothHeadset.startVoiceRecognition` (wie Google; Pflicht nach einem Tap).
   - `anruf`: `AudioManager.setCommunicationDevice` auf das Hörgerät.
   Standard: `sprachtaste` nach einem Tap, sonst `anruf`.
3. Warten, bis Android die Verbindung bestätigt. Erst dann ein kurzer Vorlauf und ein Startton.
4. Ausgabe läuft immer über eine eigene Tonspur der App mit Sprachanruf-Kennung. Ansagen werden vorab mit der Sprachausgabe des Telefons in Audiodateien gewandelt und zwischengespeichert, nicht direkt gesprochen. Grund: Android trennt die Verbindung etwa 6 s, nachdem die App selbst nichts mehr abspielt oder aufnimmt, und direkt gesprochene Ausgabe zählt nicht als Ausgabe der App.
5. Zwischen Ansagen hält eine leise Tonspur die Verbindung offen.
6. Aufnahme mit Sprachanruf-Quelle und ausdrücklich gewähltem Hörgeräte-Mikrofon. Eine Aufnahme gilt nur, wenn Android als tatsächliches Aufnahmegerät das Hörgerät meldet und die Aufnahme nicht stummgeschaltet war. Es gibt keinen Rückfall auf das Telefon-Mikrofon.
7. Ende durch Ablauf, Abbruch, Fehler oder Verbindungsverlust. Aufgeräumt wird immer vollständig: Aufnahme und Tonspur freigeben, Verbindung schließen, Dienst beenden.

Fehler werden, soweit die Verbindung steht, als kurzer Fehlerton ins Hörgerät gemeldet, sonst per Vibration.

### 6.6 Protokoll

- Eine Zeile pro Ereignis im Format JSON Lines, in App-privaten Dateien, täglich rotiert und in der Größe begrenzt.
- Jede Zeile enthält: Wanduhr, Zeit seit Systemstart mit und ohne Schlafphasen (die Differenz zeigt, ob das Telefon geschlafen hat), Kennung des Systemstarts und des App-Prozesses, Display an/aus, gesperrt, Schlafzustand (Doze), Ladezustand, ob die Rolle gehalten und der Assistenten-Dienst gebunden ist, aktives Scan-Profil, App-Version.
- Dazu der ereignisspezifische Teil. Beispiele:
  - Shelly: Taste, Geste, Paketnummer, Signalstärke, Anzahl empfangener Pakete im Stoß, welcher Scan geliefert hat, ob der Prozess dafür neu gestartet wurde.
  - Tap: Inhalt des Startbefehls, Zeit bis zur Antwort, Ergebnis der Antwort.
  - Sitzung: jeder Schritt mit Zeitstempel (ausgelöst, Dienst gestartet, Weg angefordert, Weg bestätigt, erster Ton, erster nicht-stiller Aufnahmewert, tatsächliches Aufnahmegerät, Stummschaltung, Ende und Grund).
  - Medientaste: Taste, Quelle, Entscheidung.
  - Lebenszeichen: Prozessstart mit Grund des vorigen Endes, Systemstart, Bluetooth an/aus, Scan neu angemeldet, Scan-Fehler, empfangene Bake des Shelly.
- Export: Teilen-Dialog, Ablage als Datei, Ausgabe über `adb shell dumpsys`.
- Im Protokoll stehen Geräteadressen. Es bleibt auf dem Telefon und wird nie automatisch verschickt.

### 6.7 Einrichtung und Selbstdiagnose

Der Hauptbildschirm zeigt eine Prüfliste. Jeder Punkt hat einen Zustand, einen Weg zur Behebung und, wo möglich, den gleichwertigen ADB-Befehl.

```text
Lara im Ohr · Messmodus                        v0.1.0
──────────────────────────────────────────────────────
Einrichtung
 ✓ Berechtigungen            Geräte in der Nähe, Mikrofon, Mitteilungen
 ✓ Assistentenrolle          Lara · Dienst gebunden
 ✓ Hörgeräte-Sprachtaste     → Lara („Immer")
 ✗ Akku-Ausnahme             [Beheben]
 ✓ Hörgerät                  R-Phonak Hörgerät · verbunden
 ✓ Shelly                    angelernt · letzter Druck vor 4 min
 – Medientasten-Recht        fehlt  [adb-Befehl zeigen]

Messungen                     heute    gesamt
 Shelly-Drücke empfangen       41       212
 Hörgeräte-Taps                 6        19
 Echo-Tests bestanden          5/6     17/19

[Zählmodus]  [Echo-Test jetzt]  [Protokoll]  [Export]  [Einstellungen]
```

Dieselbe Prüfung läuft bei jedem Prozessstart und nach jedem App-Update. Ein Verlust von Rolle, Bindung oder Sprachtasten-Standard wird protokolliert und als Mitteilung angezeigt.

### 6.8 Steuerung per ADB

Ein Empfänger, den nur die ADB-Shell ansprechen darf, erlaubt: Echo-Test starten, Scan-Profil und Audio-Methode setzen, Eingaben simulieren, Marker ins Protokoll schreiben („ab jetzt 2 h Ruhe") und den Zustand ausgeben. Damit lassen sich Messreihen vom Rechner aus fahren und wiederholen.

## 7. Die vier Messungen

Alle Messungen laufen mit gesperrtem Telefon und dunklem Display. Für Ruhe-Messungen muss das Telefon vom Kabel getrennt sein, weil es am Ladegerät nicht in den Tiefschlaf geht.

| Nr. | Messung | Ablauf | Bestanden, wenn (vorläufig) |
|---|---|---|---|
| 1 | **Shelly-Zählmodus** | Nach jedem empfangenen Druck sagt die App die laufende Zahl ins Hörgerät. Die Sitzung bleibt offen, solange gedrückt wird, und schließt nach 20 s Ruhe. Du zählst mit und hörst sofort, wenn ein Druck fehlt. Wiederholt je Scan-Profil, sofort und nach 5 min, 30 min und über 2 h Ruhe. | Navigation in der Sitzung: mindestens 49 von 50. Aufwecken: mindestens 49 von 50 erste Drücke nach über 2 h Ruhe. |
| 2 | **Tap startet App** | Tap bei dunklem Telefon, sofort und nach über 2 h Ruhe. | Jeder Tap wird protokolliert, die Antwort erfolgt in unter 5 s, das Display bleibt aus. |
| 3 | **Echo-Test** | Startton, Ansage „Sprich jetzt", 5 s Aufnahme, Wiedergabe der Aufnahme ins Hörgerät. Auslösbar per Tap, Shelly-Taste 3, Medientaste, Bildschirm und ADB. Mit beiden Audio-Methoden. | Aufnahmegerät ist das Hörgerät, die Aufnahme ist nicht stumm, du hörst dich selbst; höchstens 2 s vom Auslöser bis zum Startton. |
| 4 | **Medientasten** | Tasten an Kopfhörer oder Fernbedienung drücken, mit und ohne laufende Musik. | Jede Taste wird protokolliert; in Stellung `beobachten` verhält sich der Player wie ohne App; das Display bleibt aus. |

Die Zielwerte sind ein Vorschlag und noch nicht vereinbart. Für Messung 1 beim Aufwecken wird erwartet, dass sie wegen der Scan-Drossel scheitert. Auch das ist ein Ergebnis: Es entscheidet, ob der Shelly Aufweck-Taste bleibt oder ob dafür Tap, Medientaste oder eine andere Fernbedienung nötig ist.

Zusätzlich hält das Protokoll fest, was der Shelly wirklich sendet: welche Gesten ankommen, wie lange er nach dem Druck wartet, wie dicht die Pakete im Sendestoß liegen und ob eine gehaltene Taste fortlaufend sendet. Davon hängt die spätere Tastenbelegung ab. Sendet der Shelly beim Halten tatsächlich durchgehend, wäre „halten, bis der Ton kommt" eine sichere Aufweck-Geste, rechnerisch mit bis zu 10 s Wartezeit.

## 8. Build, Release, Installation

- **Werkzeuge** (Stand 27.11.2026, gegen die Paketquellen geprüft): Gradle 9.9.2, Android Gradle Plugin 9.5.3, Kotlin 2.4.21, JDK 21, Jetpack Compose (BOM 2026.11.01).
- **SDK-Stufen:** `compileSdk` 37.2, `minSdk` 36, `targetSdk` 36 als Standard und über eine Gradle-Eigenschaft auf 37 umschaltbar. Mit 36 gilt unter Android 17 die tolerantere Audio-Regel. 37 wird nachgezogen, wenn die Messungen stehen.
- **Lokal:** JDK und Android-SDK werden ohne sudo im Benutzerverzeichnis eingerichtet. `adb` liegt bereits unter `~/Android/Sdk/platform-tools`.
- **Signatur:** ein einziger Schlüssel für lokale und Release-Builds und eine einzige App-Kennung. So ersetzen sich alle Builds gegenseitig, ohne dass Rolle, Berechtigungen und Sprachtasten-Standard verloren gehen. Der Schlüssel liegt außerhalb des Repos und als Secret bei GitHub Actions.
- **Release:** Ein Tag `v…` löst bei GitHub Actions den Build aus. Ergebnis ist genau eine APK-Datei mit festem Namen plus Prüfsumme am GitHub-Release.
- **Prüfung bei jedem Push:** Unit-Tests, Lint, Debug-Build und eine Prüfung, dass die Assistenten-Metadaten vollständig sind.
- **Installation:** `adb install -r` während der Entwicklung; unterwegs über die Release-Seite oder Obtainium.
- **Lieferung in Stufen**, jede als eigenes Release:
  1. Gerüst: Projekt, Protokoll, Prüfliste, Release-Pipeline, README.
  2. Rolle, Tap und Audio-Sitzung (Messungen 2 und 3).
  3. Shelly: Parser, Anlernen, Lausch-Dienst, Zählmodus (Messung 1).
  4. Medientasten (Messung 4), ADB-Steuerung, Export-Feinschliff.

## 9. README

Auf Englisch, damit andere Hörgeräteträger etwas davon haben. Inhalt:

1. Was das Projekt ist, für wen, und der ehrliche Stand („measurement app, nothing guaranteed").
2. Getestete Hardware und Android-Version.
3. Wie es funktioniert: die drei Eingänge mit Diagramm und den am Gerät belegten Fakten.
4. Installation und Einrichtung, einschließlich der ADB-Befehle.
5. Anleitung zu den vier Messungen und zum Protokoll-Export.
6. Werkzeugkasten zur Fehlersuche (`logcat`-Filter, `dumpsys`-Befehle, Tiefschlaf erzwingen).
7. Fahrplan: Menü, Hermes.
8. **Related projects:** kuratierte, nach Thema gruppierte Liste mit je einem Satz, was man dort lernen kann. Quelle ist die Recherche vom 27.11.2026 mit rund 130 geprüften Projekten. Aufgenommen werden die etwa 30 bis 40 nützlichsten, darunter Hörgeräte-Protokolle und -Apps, Hermes-Clients, Assistenten-Apps, BTHome und Shelly, Sprache auf dem Gerät und Debug-Werkzeuge.
9. Lizenz, Danksagungen und der Hinweis, dass keine Verbindung zu Phonak/Sonova, Shelly, Google oder Nous Research besteht.

## 10. Tests

- **`:core`:** Unit-Tests für Parser (mit echten und synthetischen Paketen aus der Recherche), Entprellung, Router, Zähler und Protokollformat. Auch beschädigte Eingaben müssen sauber verworfen werden.
- **`:app`:** Tests für Prüfliste und Zustandslogik, soweit ohne Gerät möglich. Der Rest ist Gerätetest nach Abschnitt 7.
- Jede Stufe gilt erst als fertig, wenn ihre Messung auf dem Pixel gelaufen ist und das Protokoll die erwarteten Einträge zeigt.

## 11. Risiken

| Risiko | Folge | Umgang |
|---|---|---|
| Scan-Drossel verhindert Shelly-Aufwecken | Shelly nur in der Sitzung nutzbar | Messen; Tap bleibt Aufweck-Weg; Fernbedienung ist austauschbar gehalten |
| Hintergrund-Rechte verhalten sich anders als im Quellcode | Mikrofon oder Ausgabe stumm | Stufe 2 prüft genau das zuerst; jeder Schritt wird protokolliert; Audio-Härtung lässt sich per ADB auf „Fehler werfen" stellen |
| Fehlerhafter Build verliert die Rolle | alle Hintergrund-Rechte weg | Prüfung der Metadaten im Build; Selbstdiagnose nach jedem Update |
| Google-App belegt den Tasten-Listener | Weg C fällt aus | Prüfen und anzeigen; Weg C bleibt Zusatz |
| System- oder Bluetooth-Update ändert das Verhalten | Messwerte veralten | Protokoll enthält Build- und Modulversion; Messreihe nach Updates wiederholen |
| Versteckte Schnittstelle (Tasten-Listener) wird gesperrt | Weg C fällt aus | eigener Adapter, App läuft ohne ihn |
| Hörgerät verhält sich bei kurzen Sitzungen störend (Töne, Programmwechsel) | Bedienung unangenehm | Echo-Test mit beiden Audio-Methoden vergleichen, Beobachtung im README festhalten |
| Kopplungsmodus des Shelly wird versehentlich geöffnet | Fremde könnten koppeln | keine Geste mit zwei lang gehaltenen Tasten; Firmware-Stand des Shelly prüfen (ab 1.0.22) |

## 12. Festlegungen

**Vom Auftraggeber entschieden**

- Kotlin, nativ; kein Store; APK über ADB und GitHub Releases.
- Variante 1: eine App, zuerst als Mess-App.
- README mit verlinkten verwandten Projekten.
- udev-Regel für ADB auf dem Entwicklungsrechner.

**Von Claude vorgeschlagen, bisher unwidersprochen**

- App-Name „Lara im Ohr", App-Kennung `io.github.domicc.lara`. Die Kennung lässt sich später nur mit Neuinstallation und neuer Einrichtung ändern.
- README auf Englisch; Design-Dokument und Ansagen auf Deutsch.
- Vorläufige Zielwerte aus Abschnitt 7.
- `targetSdk` 36 mit Umschalter.
- Oberfläche mit Jetpack Compose.
- Kein Betrieb vor dem ersten Entsperren nach einem Neustart.
- JDK und SDK ohne sudo im Benutzerverzeichnis.
- Recherche-Berichte und Geräte-Mitschnitte bleiben lokal unter `.local/` und sind von Git ausgeschlossen. Ins Repo kommt nur die kuratierte Liste in der README.
- Tastenbelegung und Auslöser der Mess-App aus Abschnitt 6.4.

**Freigaben**

- Commit und Push ins öffentliche Repo: am 27.11.2026 freigegeben.
- Noch offen: Signaturschlüssel als Secret im GitHub-Repo ablegen und das erste Release bauen lassen.

## 13. Der Umsetzung überlassen

- Klassen- und Paketstruktur innerhalb der Module.
- Genaue Feldnamen des Protokolls, solange die Inhalte aus 6.6 enthalten sind.
- Gestaltung von Tönen, Ansagetexten und Oberfläche.
- Form der ADB-Befehle.
- Wahl kleiner Hilfsbibliotheken.
- Aufteilung der Stufen in Einzelschritte.

## 14. Danach

1. **Angesagtes Menü:** Zustandsautomat, Ansagen, endgültige Tastenbelegung auf Basis der Messwerte.
2. **Hermes:** Anbindung über die HTTP-Schnittstelle des Hermes-Servers, Spracherkennung, Rückkanal für Erinnerungen.

Beides bekommt ein eigenes Design, sobald die Messungen ausgewertet sind.

## Quellen

Die ausführlichen Recherche- und Prüfberichte (20 Dateien) liegen lokal unter `.local/research/`, die Mitschnitte vom Gerät unter `.local/recon/`. Beide Ordner sind nicht Teil des Repos.
