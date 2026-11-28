# Lara im Ohr: Termine ansagen (Design)

**Stand:** 28. November 2026 · **Status:** gebaut, noch nicht auf dem Telefon gelaufen

## Ziel

Zehn Minuten vor einem Termin sagt das Hörgerät von selbst, was ansteht: „In 10 Minuten: Zahnarzt“. Das Telefon bleibt dabei gesperrt und dunkel in der Tasche. Die Termine kommen aus den Kalendern, die ohnehin auf dem Telefon liegen, zum Beispiel aus dem Google-Kalender.

Das ist bewusst die einfachste Form: eine feste Vorlaufzeit von zehn Minuten und eine Ansage je Termin. Andere Vorlaufzeiten, eine zweite Ansage oder Ansagen für ganztägige Termine kommen erst in Frage, wenn sich diese Form im Alltag bewährt hat.

## Was angesagt wird

- Jeder Termin mit Uhrzeit aus einem ausgewählten Kalender, zehn Minuten vor seinem Beginn.
- Die Ansage nennt die Minuten, die wirklich noch bleiben. Kommt die App erst sieben Minuten vorher dazu, weil der Wecker spät kam oder die Funktion gerade erst eingeschaltet wurde, heißt es „In 7 Minuten: Zahnarzt“. Bei einer Minute heißt es „In einer Minute: Zahnarzt“, darunter „Jetzt: Zahnarzt“.
- Beginnen mehrere Termine zur selben Zeit, nennt eine Ansage sie zusammen: „In 10 Minuten: Zahnarzt und Anruf Bank“, bei dreien „A, B und C“. Sind es mehr als drei, werden drei genannt und der Rest gezählt: „A, B, C und 2 weitere“. Gleiche Titel werden nur einmal gesagt, auch wenn sie sich nur in Groß- und Kleinschreibung unterscheiden.
- Ein Termin ohne Titel heißt „Termin“. Das gilt auch für einen Titel, in dem es nichts zu sprechen gibt, etwa nur Satzzeichen. Lange Titel werden nach 80 Zeichen abgeschnitten.

## Was nicht angesagt wird

- Ganztägige Termine.
- Abgesagte Termine und Termine, die der Besitzer abgelehnt hat.
- Termine aus Kalendern, die nicht ausgewählt sind.
- Termine, die schon begonnen haben.
- Ein Termin, der schon angesagt wurde. Wird er verschoben, wird er für die neue Zeit wieder angesagt.

Eine Ausnahme gibt es: Wird ein einzelner Termin einer Serie nach seiner Ansage noch geändert (anderer Raum, ein Link kommt dazu), führt der Kalenderspeicher ihn danach als eigenen, neuen Termin. Die App kann ihn nicht vom alten unterscheiden und sagt ihn noch einmal an, mit den Minuten, die dann bleiben. Dasselbe gilt für Kalender, die einen geänderten Termin beim Abgleich löschen und neu anlegen. Das ist so belassen, weil es selten ist und die einfache Regel „Termin und Beginn“ sonst nicht mehr reicht.

## Betrachtete Wege

| Weg | Entscheidung |
|---|---|
| **Kalenderspeicher des Telefons** (`CalendarContract`) | **Gewählt.** Dort liegen alle Kalender, die das Telefon synchronisiert, gleich von welchem Konto. Die App braucht dafür nur die Berechtigung „Kalender“, kein Netz und keine Anmeldung. |
| Google-Calendar-API mit Anmeldung | Verworfen. Die App müsste sich bei Google anmelden, Zugangsdaten verwalten und selbst ins Netz gehen, und sie sähe trotzdem nur Google-Kalender. |
| Benachrichtigungen der Kalender-App mitlesen | Verworfen. Dafür müsste die App alle Benachrichtigungen des Telefons lesen dürfen, und sie hinge davon ab, ob, wann und mit welchem Wortlaut die Kalender-App erinnert. |
| Erinnerungs-Rundruf des Kalenderspeichers (`EVENT_REMINDER`) | Verworfen. Er kommt nur für Termine, an denen im Kalender eine Erinnerung eingestellt ist, und zu deren Zeit, nicht zehn Minuten vorher. |

## Regeln

1. **Zeitpunkt.** Fällig ist ein Termin von zehn Minuten vor seinem Beginn bis zu seinem Beginn. In dieser Zeit wird er einmal angesagt, mit den Minuten, die dann noch bleiben (gerundet).
2. **Auswahl der Kalender.** Die App zeigt alle Kalender des Telefons, jeden mit einem Haken. Ohne eigene Auswahl gilt, was die Kalender-App anzeigt. Gespeichert wird nur, was der Besitzer von Hand an- oder abgewählt hat. Ein Kalender, der später dazukommt, verhält sich deshalb wie in der Kalender-App. Verschwindet ein Kalender vom Telefon, vergisst die App die Auswahl für ihn: Android vergibt seine Nummer sonst an den nächsten neuen Kalender, und der soll keinen fremden Haken erben.
3. **Aus ist der Normalzustand.** Nach der Installation ist die Funktion ausgeschaltet. Beim Einschalten fragt die App nach der Berechtigung „Kalender“. Solange die Funktion aus ist, liest die App nichts aus dem Kalender. Fehlt die Berechtigung später wieder, zeigt die App das an und bietet an, sie zu erteilen. Angesagt wird dann nichts.
4. **Nichts doppelt.** Jede Ansage merkt sich die App einen Tag lang, und zwar Termin und Beginn zusammen. Ein Neustart, ein Update oder eine neue Planung wiederholt deshalb keine Ansage. Die eine Ausnahme steht oben unter „Was nicht angesagt wird“.
5. **Nur ins Hörgerät.** Die Ansage nimmt denselben Weg wie die Stundenansage: ein kurzer Ton, dann Sprache über den Musik-Kanal, und nur, wenn das gewählte Hörgerät der einzige Ausgang ist. Ist es nicht verbunden oder ginge der Ton woandershin, bleibt die App stumm und schreibt den Grund ins Protokoll. Die Ansage wird nicht nachgeholt. Es gibt keine Vibration und keinen Ersatzweg.
6. **Bis zum letzten Wort.** Die App prüft den Ausgang nicht nur vor dem Ton, sondern beobachtet ihn, bis die Ansage zu Ende ist. Wird das Hörgerät mitten im Satz ausgeschaltet oder verbindet sich ein zweites Gerät, bricht sie die Ansage ab. Android würde den Rest sonst über den Lautsprecher des Telefons oder das andere Gerät abspielen. Das gilt für jede Ansage der App, auch für die Stundenansage. Eine abgebrochene Ansage wird nicht nachgeholt.
7. **Nicht in eine Sitzung und nicht in ein Telefonat hinein.** Läuft gerade eine Sitzung mit dem Hörgerät, oder telefoniert oder klingelt das Telefon, wartet die Ansage. Die App versucht es alle 20 Sekunden wieder, bis die Sitzung oder das Telefonat vorbei ist oder der Termin begonnen hat. Hat er begonnen, gibt sie auf und vermerkt das im Protokoll. Wegen der 20 Sekunden kann eine Ansage ausfallen, wenn die Sitzung erst in den letzten Sekunden vor dem Termin endet. Beginnt eine Sitzung, während eine Ansage läuft, bricht die Ansage ab. Das Telefonat stand nicht im ursprünglichen Auftrag; es ist hier aufgenommen, weil die App auch einen Druck auf die Fernbedienung nicht in ein Telefonat hinein beantwortet und die Ansage sonst ersatzlos entfiele.
8. **Wecker.** Für die nächste fällige Ansage stellt die App einen genauen Wecker, wie für die Stundenansage. Die kam am Gerät 48 ms nach der vollen Stunde aus dem Tiefschlaf. Es gibt immer nur diesen einen Wecker.
9. **Neu geplant wird**, wenn die App startet, wenn der Wecker klingelt, wenn der Schalter oder die Auswahl der Kalender geändert wird, wenn die Berechtigung erteilt wird, und wenige Sekunden nachdem der Kalenderspeicher eine Änderung meldet. Meldet er ohne Pause immer neue Änderungen, wartet die App höchstens 20 Sekunden.
10. **Vorausschau.** Geplant wird für die nächsten 24 Stunden. Spätestens nach sechs Stunden schaut die App von selbst wieder in den Kalender, auch wenn eine Ansage geplant ist. Das fängt eine Änderung auf, deren Meldung ihr entgangen ist.
11. **Kalender nicht lesbar.** Lässt sich der Kalenderspeicher einmal nicht lesen, bleibt der Wecker für die geplante Ansage stehen. Die App versucht das Lesen nach einer Minute wieder, danach in immer größeren Abständen, höchstens alle sechs Stunden. Antwortet der Kalenderspeicher gar nicht, bricht sie die Anfrage nach drei Sekunden ab.
12. **Verpasst.** Kommt die App erst nach dem Beginn eines Termins dazu (das Telefon war aus, der Wecker kam zu spät, der Kalender war nicht lesbar), sagt sie nichts mehr an. Sie vermerkt den Termin aber einmal als verpasst im Protokoll, wenn er in der letzten Stunde begonnen hat und sie ihn vorher schon eingeplant hatte. Ein Termin, der erst nach seinem Beginn eingetragen wird, gilt nicht als verpasst. Nach einem Neustart der App weiß sie nicht mehr, was eingeplant war; dann zählt alles, was in der letzten Stunde begonnen hat. So lässt sich später unterscheiden, ob die App einen Termin nie gesehen oder zu spät gesehen hat.

## Datenschutz

Das Protokoll der App wird zur Auswertung weitergegeben und läuft zusätzlich ins Systemprotokoll des Telefons. Deshalb gilt:

- Titel, Beschreibungen und Orte von Terminen, Namen von Kalendern und Namen von Konten (das sind E-Mail-Adressen) stehen **nie** im Protokoll, nie in der Antwort auf einen adb-Befehl und nie in einer Fehlermeldung der App.
- Ins Protokoll kommen nur Zahlen: wie viele Kalender und Termine, die Nummern, die Android den Kalendern und Terminen gibt, Uhrzeiten, und wie lang der angesagte Text war.
- Die Nummern der angesagten Termine liegen im privaten Speicher der App. Titel werden nicht gespeichert.
- Beschreibung und Ort eines Termins liest die App gar nicht erst.
- Auf dem Bildschirm des Telefons zeigt die App die Namen der Kalender, ihre Konten und den Titel der nächsten Ansage.
- Die App geht für diese Funktion nicht ins Netz. Der Text einer Ansage geht an die Sprachausgabe des Telefons, wie bei jeder anderen Ansage der App.

## Bedienung

Auf dem Bildschirm gibt es den Abschnitt „Kalender“. Zugeklappt zeigt er in einer Zeile, ob die Funktion an ist und wann die nächste Ansage kommt („keine Ansage in den nächsten 24 Stunden“, wenn keine ansteht). Aufgeklappt:

- der Schalter „Termine ansagen“,
- ein Hinweis mit Knopf, falls die Berechtigung fehlt,
- die Zeile „Nächste Ansage“ mit Uhrzeit und Titel, oder „keine in den nächsten 24 Stunden“,
- die Liste der Kalender mit Haken, darunter klein das Konto,
- der Knopf „Nächsten Termin jetzt ansagen“. Er sagt sofort den nächsten Termin der kommenden 24 Stunden an, zum Beispiel „Nächster Termin um 14 Uhr 30: Zahnarzt“ oder „Nächster Termin morgen um 9 Uhr: Zahnarzt“ (kurz vor Mitternacht kann es auch „übermorgen“ heißen). Gibt es keinen, sagt er „Kein Termin in den nächsten 24 Stunden“. Damit lässt sich prüfen, wie eine Ansage klingt und ob die richtigen Kalender gelesen werden. Während einer Sitzung oder eines Telefonats sagt der Knopf nichts.

Über adb nennt `lara.STATUS` den Zustand als `calendar=off`, `on` oder `no_permission`. `lara.CALENDAR` plant neu und antwortet mit Zahlen. Beides steht in [`docs/adb-control.md`](../adb-control.md).

## Was im Protokoll steht

| Zeile | Bedeutung |
|---|---|
| `calendar_enabled` | Der Schalter wurde umgelegt. |
| `calendar_selection` | Ein Kalender wurde an- oder abgewählt (mit seiner Nummer). |
| `calendar_selection_forgotten` | Die Auswahl für Kalender, die nicht mehr auf dem Telefon sind, wurde vergessen (nur die Anzahl). |
| `calendar_plan` | Eine Planung: ihr Anlass, wie viele Kalender ausgewählt sind, wie viele Termine in Frage kommen, wie viele aus welchem Grund wegfallen (`allDay`, `cancelled`, `declined`, `unselected`), wie viele fällig sind, wie viele verpasst wurden, wann der nächste Wecker klingelt, wofür, und ob er genau ist. Mit `readable=false`, wenn der Kalender nicht lesbar war; `failures` zählt dann die Fehlversuche in Folge. |
| `calendar_alarm` | Der Wecker hat geklingelt, mit seiner Verspätung in Millisekunden. |
| `calendar_announce` | Eine Ansage wurde an die Sprachausgabe übergeben: Zahl der Termine, Minuten, Länge der Titel. |
| `calendar_waiting`, `calendar_gave_up` | Eine Ansage wartet auf das Ende einer Sitzung (`why=session_busy`) oder eines Telefonats (`why=phone_call`); sie wurde aufgegeben, weil der Termin begonnen hat. |
| `calendar_missed` | Termine haben begonnen, ohne dass die App zu einer Ansage kam, obwohl sie eingeplant waren: ihre Nummern und vor wie vielen Millisekunden sie begonnen haben. `reason` nennt den Anlass der Planung, die das bemerkt hat. Mit `reason=process_start` stehen hier alle Termine der letzten Stunde, zu denen es keine Ansage gab. |
| `calendar_no_permission` | Die Funktion ist an, aber die Berechtigung fehlt. |
| `calendar_provider_error` | Der Kalenderspeicher ließ sich nicht lesen (nur die Art des Fehlers; `why=timeout`, wenn er nicht geantwortet hat). |
| `calendar_plan_timeout` | Der Wecker hat geklingelt, aber die Planung war nach 8 Sekunden nicht fertig. Sie kann noch folgen. |
| `calendar_observer` | Die App lässt sich Änderungen am Kalender melden, oder nicht mehr. |
| `calendar_test`, `calendar_test_skipped` | Der Knopf „Nächsten Termin jetzt ansagen“. |
| `calendar_plan_error` | Eine Planung ist unerwartet gescheitert (nur die Art des Fehlers). Danach folgt `calendar_plan` mit `readable=false` und dem Wecker für den nächsten Versuch. |
| `calendar_store_error` | Die App konnte sich eine Ansage nicht dauerhaft merken. Nach einem Neustart könnte sie wiederholt werden. |

Auf `calendar_announce` folgen die üblichen Zeilen jeder Ansage: `announce_start` (hier mit `textLength` statt des Textes), `announce_speaking` und `announce_end`, oder `announce_skipped` mit dem Grund. `announce_end` nennt in `result`, wie die Ansage endete: `ok`, oder abgebrochen mit `output_lost` oder `output_going_away` (der Ton ginge nicht mehr nur ins Hörgerät), `session_busy` oder `phone_call`. `announce_skipped` kennt zusätzlich `output_changed`: Der Ausgang hat gewechselt, während die Sprachausgabe startete.

## Am Gerät noch zu prüfen

Nichts davon ist bisher auf dem Telefon gelaufen. In dieser Reihenfolge:

1. Einschalten und die Berechtigung erteilen. Erscheinen alle Kalender, und stimmen die Haken mit der Kalender-App überein? Im Protokoll steht `calendar_plan`.
2. „Nächsten Termin jetzt ansagen“: Kommt die Ansage im Hörgerät, und stimmen Termin und Uhrzeit? Wie liest die Sprachausgabe „In 10 Minuten“, „um 14 Uhr 30“ und einen Titel, der nur aus einem Emoji besteht?
3. Die Ansage läuft jetzt für alle Ansagen durch die neue Beobachtung des Ausgangs. Kommen „Ansage jetzt“, die Stundenansage und „Beende“ nach einem Tap weiterhin vollständig an (`announce_end` mit `result=ok`)? Ein falscher Abbruch stünde als `output_lost` im Protokoll.
4. **Hörgerät mitten in der Ansage ausschalten** (Testknopf drücken, dann das Hörgerät ausschalten oder in die Ladeschale legen): Aus dem Telefon darf nichts zu hören sein. Im Protokoll steht `announce_end` mit `output_going_away` oder `output_lost`. Dasselbe, wenn sich während der Ansage ein zweites Audiogerät verbindet (Auto, Kopfhörer).
5. Einen Termin anlegen, der in zwölf Minuten beginnt. Meldet der Kalenderspeicher die Änderung (`calendar_plan` mit `reason=calendar_changed`)? Kommt die Ansage zehn Minuten vorher, bei gesperrtem und dunklem Telefon? `calendar_alarm` zeigt die Verspätung des Weckers.
6. Dasselbe nach längerer Ruhe, auch für einen Termin, der am Rechner angelegt und nur synchronisiert wurde, und einmal so, dass der Prozess der App nicht läuft, wenn der Wecker klingelt, und Android ihn dafür erst starten muss. „Beenden erzwingen“ taugt dafür nicht: Es löscht auch den Wecker.
7. Einen Termin verschieben, absagen und ablehnen; ein ganztägiger Termin; zwei Termine zur selben Zeit; ein Serientermin an zwei Tagen. Wichtig dabei: ob der Kalenderspeicher abgesagte und abgelehnte Termine so kennzeichnet, wie die App es erwartet. `calendar_plan` zählt dafür `cancelled` und `declined`.
8. Einen einzelnen Termin einer Serie nach seiner Ansage ändern: Kommt die zweite Ansage, wie oben beschrieben, und stört sie?
9. Eine Ansage, während eine Sitzung läuft: `calendar_waiting`, danach die Ansage oder `calendar_gave_up`. Kommt die Ansage auch, wenn die Sitzung mit einem Tap beendet wurde (nach „Beende“)?
10. Ein Tap, während eine Ansage läuft: Die Ansage bricht ab, die Sitzung öffnet sich.
11. Eine Ansage während eines Telefonats und während es klingelt: `calendar_waiting` mit `why=phone_call`, die Ansage nach dem Auflegen.
12. Eine Ansage, während Musik läuft.
13. Das Hörgerät ist nicht verbunden: `announce_skipped`, und keine Wiederholung.
14. Die Berechtigung in den Android-Einstellungen entziehen: Der Abschnitt zeigt „Kalender-Berechtigung fehlt“, im Protokoll steht `calendar_no_permission`, und nichts stürzt ab.
15. Neustart des Telefons und Update der App: Wird neu geplant, und wird nichts doppelt angesagt? Liegt ein Termin in der Zeit, in der das Telefon aus war, steht danach `calendar_missed` im Protokoll.
16. `lara.STATUS` und `lara.CALENDAR` über adb.
