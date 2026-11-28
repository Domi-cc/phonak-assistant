package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.unit.dp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.core.announce.AnnounceChannel
import io.github.domicc.lara.core.announce.AnnounceSettings
import io.github.domicc.lara.core.announce.AnnounceTexts
import io.github.domicc.lara.core.announce.AnnounceVolumeRules
import io.github.domicc.lara.core.announce.LeadIn
import io.github.domicc.lara.core.announce.VolumeLive
import io.github.domicc.lara.core.input.InputSource
import kotlin.math.roundToInt

/** For the folded section: the step announcements are played with, or that they follow the phone. */
fun announceSummary(settings: AnnounceSettings, live: VolumeLive): String = AnnounceTexts.summary(settings, live)

/**
 * How loud announcements are: one line that says what an announcement would be played with
 * right now, the slider in the real steps of Android's media volume, the switch between "as
 * loud as the phone's media" and a step of the app's own for announcements, and the two
 * settings a listening test varies (the silence in front of an announcement, and the volume
 * channel).
 *
 * The slider acts when it is let go, not while it is moved: then the step is stored or
 * written, and the sample "Lautstärke Stufe N" is played as an announcement, so that it
 * sounds like one. The line under the slider says afterwards what really happened.
 *
 * Nothing in this section clicks, like in "Starten": Android's click sound for a press goes
 * to the hearing aid like every sound of the phone, right in front of a sample.
 *
 * The wording is in [AnnounceTexts], the rules in [AnnounceVolumeRules], what Android says
 * and takes in [io.github.domicc.lara.announce.AnnounceVolume].
 *
 * @param live Android's volumes, read every second by the screen
 * @param refresh reads them again at once, after this section changed one
 */
@Composable
fun AnnounceSection(live: VolumeLive, refresh: () -> Unit) {
    val app = LaraApp.instance
    val settings by app.volume.store.settings.collectAsState()
    val report by app.volume.report.collectAsState()
    // the step under the finger while the slider is moved; null while it is not
    var dragged by remember { mutableStateOf<Float?>(null) }
    // what the slider said when it was let go, if no sample followed
    var released by remember { mutableStateOf<String?>(null) }
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // journals the state of the volumes each time the section is opened
    LaunchedEffect(Unit) {
        app.volume.onSection()
        refresh()
    }

    SoundEffectOnInteraction(enabled = false) {
        Text(
            AnnounceTexts.status(settings, live),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 3.dp),
        )

        if (settings.channel == AnnounceChannel.MEDIA) {
            val shown = AnnounceTexts.sliderStep(settings, live)
            val max = live.mediaMax
            Text(
                AnnounceTexts.sliderLabel(settings, live, dragged?.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 3.dp),
            )
            // every step of Android's scale and none between them; 0 is not offered: it mutes
            val slider = remember(max) {
                SliderState(value = (shown ?: 1).toFloat(), steps = maxOf(max - 2, 0), trackRange = 1f..maxOf(max, 2).toFloat())
            }
            // while no finger is on it, the slider stands where the step is
            SideEffect { if (dragged == null) slider.value = (shown ?: 1).toFloat() }
            Slider(
                state = slider,
                onValueChange = {
                    dragged = it
                    slider.value = it
                },
                onValueChangeFinished = {
                    // also after a touch that moved nothing: the sample once more
                    val result = app.volume.release((dragged ?: slider.value).roundToInt(), InputSource.SCREEN.word)
                    released = result.line
                    result.sample?.let(app.announcer::sample)
                    refresh()
                    dragged = null
                },
                enabled = max > 1 &&
                    AnnounceVolumeRules.sliderEnabled(settings.channel, settings.ownLevel, live.mediaToAid, live.mediaSilent, live.mediaFixed),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (settings.ownLevel) AnnounceTexts.OWN_SLIDER_DETAIL else AnnounceTexts.PHONE_SLIDER_DETAIL,
                style = small,
                color = muted,
            )
            // the truth after the slider was let go: what the sample was played with, or why not
            val after = released ?: report?.takeIf { it.reason == AnnounceTexts.SAMPLE_REASON }?.let(AnnounceTexts::afterSample)
            if (after != null) {
                Text(after, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
            SwitchRow(AnnounceTexts.OWN_LEVEL, AnnounceTexts.OWN_LEVEL_DETAIL, settings.ownLevel) { on ->
                released = null
                app.volume.setOwnLevel(on, InputSource.SCREEN.word)
                refresh()
            }
        } else {
            Text(AnnounceTexts.ASSISTANT_DETAIL, style = small, color = muted)
        }

        ChoiceRow(
            AnnounceTexts.LEAD_IN,
            AnnounceTexts.LEAD_IN_DETAIL,
            LeadIn.entries.map { it to AnnounceTexts.leadIn(it) },
            settings.leadIn,
        ) { app.volume.store.setLeadIn(it, InputSource.SCREEN.word) }
        ChoiceRow(
            AnnounceTexts.CHANNEL,
            AnnounceTexts.CHANNEL_DETAIL,
            AnnounceChannel.entries.map { it to AnnounceTexts.channel(it) },
            settings.channel,
        ) {
            released = null
            app.volume.store.setChannel(it, InputSource.SCREEN.word)
            refresh()
        }
    }
}
