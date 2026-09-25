package app.sonveil.music.data.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watches the connected output and loads that device's saved equalizer.
 * Editing the equalizer writes it back onto the output that is playing now.
 */
class AudioOutputMonitor(
    context: Context,
    private val settings: PlayerSettings,
) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val _output = MutableStateFlow(OutputDevice.SPEAKER)
    val output: StateFlow<OutputDevice> = _output
    private var started = false

    private val devicesCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (settings.applyingProfile) return@OnSharedPreferenceChangeListener
        if (key != null && key !in PlayerSettings.LIVE_EQ_KEYS) return@OnSharedPreferenceChangeListener
        settings.saveProfileFor(_output.value)
    }

    fun start() {
        if (started) return
        started = true
        settings.ensureBaseline()
        val now = currentDevice()
        _output.value = now
        settings.profileFor(now.id)?.let { settings.writeEqSnapshot(it) }
        audio.registerAudioDeviceCallback(devicesCallback, main)
        settings.register(prefsListener)
    }

    private fun refresh() {
        val next = currentDevice()
        val previous = _output.value
        if (next.id == previous.id) {
            if (next.name != previous.name) _output.value = next
            return
        }
        _output.value = next
        settings.writeEqSnapshot(settings.profileFor(next.id) ?: settings.baselineProfile())
    }

    private fun currentDevice(): OutputDevice =
        AudioOutputs.select(connectedOutputs())

    private fun connectedOutputs(): List<OutputDevice> =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { info ->
            val ranked = rank(info.type) ?: return@mapNotNull null
            val address = deviceAddress(info)
            val fallback = typeName(info.type)
            val product = info.productName?.toString()?.trim().orEmpty().ifBlank { fallback }
            val id = if (address.isNotBlank()) "${info.type}:$address" else "${info.type}:$product"
            OutputDevice(id = id, name = product, kind = ranked.first, rank = ranked.second)
        }

    private fun deviceAddress(info: AudioDeviceInfo): String {
        if (Build.VERSION.SDK_INT < 28) return ""
        val raw = info.address?.trim().orEmpty()
        if (raw.isBlank() || raw == "00:00:00:00:00:00") return ""
        return raw
    }

    private fun rank(type: Int): Pair<OutputKind, Int>? = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.Bluetooth to 0
        AudioDeviceInfo.TYPE_BLE_HEADSET -> OutputKind.Bluetooth to 1
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> OutputKind.Bluetooth to 2
        AudioDeviceInfo.TYPE_HEARING_AID -> OutputKind.Bluetooth to 3
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutputKind.Bluetooth to 4
        AudioDeviceInfo.TYPE_USB_HEADSET -> OutputKind.Usb to 5
        AudioDeviceInfo.TYPE_USB_DEVICE -> OutputKind.Usb to 6
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputKind.Wired to 7
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.Wired to 8
        AudioDeviceInfo.TYPE_LINE_ANALOG -> OutputKind.Wired to 9
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        -> OutputKind.Speaker to 10
        else -> null
    }

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
        else -> "Phone speaker"
    }
}
