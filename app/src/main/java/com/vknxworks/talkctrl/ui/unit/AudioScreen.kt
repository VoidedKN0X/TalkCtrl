package com.vknxworks.talkctrl.ui.unit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicExternalOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.talkctrl.R
import com.vknxworks.talkctrl.container
import com.vknxworks.talkctrl.protocol.Caip
import com.vknxworks.talkctrl.protocol.Languages
import com.vknxworks.talkctrl.protocol.Volumes
import com.vknxworks.talkctrl.ui.components.Group
import com.vknxworks.talkctrl.ui.components.GroupChoice
import com.vknxworks.talkctrl.ui.components.GroupSwitch

@Composable
fun AudioScreen() {
    val c = LocalContext.current.container
    val unit = rememberUnit()
    val send = rememberSender()
    TabPage(stringResource(R.string.tab_audio), stringResource(R.string.label_sound)) {
        if (!unit.connected) return@TabPage NotConnectedView(unit)
        val v = unit.volumes
        val u = unit.user
        val caps = unit.caps

        Group(stringResource(R.string.group_volumes), footer = stringResource(R.string.volumes_footer)) {
            if (v == null) {
                NeedsConfirmRow(stringResource(R.string.volumes_unknown))
            } else {
                LevelRow(stringResource(R.string.vol_speaker), v.announcements, Volumes.MAX) { n -> send { c.link.setVolumes { it.copy(announcements = n) } } }
                LevelRow(stringResource(R.string.vol_intercom), v.intercom, Volumes.MAX) { n -> send { c.link.setVolumes { it.copy(intercom = n) } } }
                LevelRow(stringResource(R.string.vol_music), v.music, Volumes.MAX) { n -> send { c.link.setVolumes { it.copy(music = n, music2 = n) } } }
                LevelRow(stringResource(R.string.vol_phone), v.phone, Volumes.MAX) { n -> send { c.link.setVolumes { it.copy(phone = n, phone2 = n) } } }
                LevelRow(stringResource(R.string.vol_fm), v.fm, Volumes.MAX) { n -> send { c.link.setVolumes { it.copy(fm = n) } } }
                LevelRow(
                    stringResource(R.string.vol_music_under_ic),
                    Volumes.reductionPercent(v.musicUnderIntercom) / 10 - 1,
                    9,
                    valueLabel = { "${(it + 1) * 10} %" },
                ) { n -> send { c.link.setVolumes { it.copy(musicUnderIntercom = Volumes.reductionCode((n + 1) * 10)) } } }
            }
        }

        Group(stringResource(R.string.group_sound)) {
            if (u != null) {
                val autoOk = caps?.autoVolume != false
                GroupChoice(
                    stringResource(R.string.auto_volume),
                    listOf(0 to stringResource(R.string.off), 1 to stringResource(R.string.low), 2 to stringResource(R.string.medium), 3 to stringResource(R.string.high)),
                    if (autoOk) u.autoVolume else null,
                    icon = Icons.Outlined.Speed,
                    fallbackLabel = stringResource(R.string.not_on_this_unit),
                ) { n -> if (autoOk) send { c.link.setUser { it.withAutoVolume(n) } } }
                val eqs = unit.eqs.ifEmpty { listOf(u.eqProfile) }
                GroupChoice(
                    stringResource(R.string.eq),
                    eqs.map { it to eqName(it) },
                    u.eqProfile,
                    icon = Icons.Outlined.GraphicEq,
                    fallbackLabel = eqName(u.eqProfile),
                ) { n -> send { c.link.setUser { it.withEq(n) } } }
                if (caps?.phoneMixing != false) {
                    GroupSwitch(stringResource(R.string.phone_mixing), stringResource(R.string.phone_mixing_sub), u.phoneMixing, Icons.Outlined.PhoneInTalk) { on ->
                        send { c.link.setUser { it.withPhoneMixing(on) } }
                    }
                }
            }
            if (caps?.musicSharing != false) {
                GroupSwitch(stringResource(R.string.music_sharing), stringResource(R.string.music_sharing_sub), unit.musicSharing != 0, Icons.Outlined.Share) { on ->
                    send { c.link.send(Caip.musicSharing(on)) }
                }
            }
        }

        if (u != null) {
            Group(stringResource(R.string.group_voice)) {
                GroupSwitch(stringResource(R.string.voice_prompts), stringResource(R.string.voice_prompts_sub), u.voicePrompts, Icons.Outlined.Campaign) { on ->
                    send { c.link.setUser { it.withVoicePrompts(on) } }
                }
                val langs = unit.languages.ifEmpty { listOf(u.language) }
                GroupChoice(
                    stringResource(R.string.language),
                    langs.map { code -> code to (Languages.byCode(code)?.name ?: "$code") },
                    u.language,
                    icon = Icons.Outlined.Language,
                ) { n -> send { c.link.setUser { it.withLanguage(n) } } }
                if (caps?.voiceCommands != false) {
                    GroupSwitch(stringResource(R.string.voice_commands), stringResource(R.string.voice_commands_sub), u.voiceCommands, Icons.Outlined.RecordVoiceOver) { on ->
                        send { c.link.setUser { it.withVoiceCommands(on) } }
                    }
                }
            }

            Group(stringResource(R.string.group_mic), footer = stringResource(R.string.mic_footer)) {
                GroupChoice(
                    stringResource(R.string.vox_sensitivity),
                    listOf(1 to stringResource(R.string.low), 0 to stringResource(R.string.medium), 2 to stringResource(R.string.high)),
                    u.micSensitivity,
                    icon = Icons.Outlined.Mic,
                ) { n -> send { c.link.setUser { it.withMicSensitivity(n) } } }
                GroupSwitch(stringResource(R.string.noise_gate), stringResource(R.string.noise_gate_sub), u.noiseGate, Icons.Outlined.MicExternalOn) { on ->
                    send { c.link.setUser { it.withNoiseGate(on) } }
                }
                NeedsConfirmRow(stringResource(R.string.vox_on_off), Icons.Outlined.MusicNote)
            }
        }
    }
}

@Composable
private fun eqName(code: Int): String = when (code) {
    0, 16 -> stringResource(R.string.eq_high_volume)
    1, 9, 17, 22 -> stringResource(R.string.eq_bass_boost)
    2, 10, 18, 24 -> stringResource(R.string.eq_vocal)
    3, 19, 30 -> stringResource(R.string.eq_off)
    8, 21, 29 -> stringResource(R.string.eq_standard)
    20 -> stringResource(R.string.eq_balanced)
    23 -> stringResource(R.string.eq_extreme_bass)
    25 -> stringResource(R.string.eq_treble)
    else -> stringResource(R.string.eq_profile_n, code)
}
