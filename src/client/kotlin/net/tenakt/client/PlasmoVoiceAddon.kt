package net.tenakt.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.tenakt.network.VoiceDestroyPayload
import net.tenakt.network.VoiceLevitationPayload
import su.plo.voice.api.addon.AddonInitializer
import su.plo.voice.api.addon.InjectPlasmoVoice
import su.plo.voice.api.addon.annotation.Addon
import su.plo.voice.api.client.PlasmoVoiceClient
import su.plo.voice.api.client.event.audio.capture.AudioCaptureEvent
import su.plo.voice.api.event.EventSubscribe

@Addon(
    id = "chunk_destroyer",
    version = "1.1.0",
    authors = ["Tenakt"]
)
class PlasmoVoiceAddon : AddonInitializer {

    @InjectPlasmoVoice
    private lateinit var voiceClient: PlasmoVoiceClient

    private var lastMicState = false

    override fun onAddonInitialize() {
        println("=== Chunk Destroyer Addon initialized ===")
        VoskManager.init()
        println("Addon loaded successfully")
    }

    @EventSubscribe
    fun onAudioCapture(event: AudioCaptureEvent) {
        val isActivated = voiceClient.activationManager.activations.any { it.isActive }

        if (isActivated != lastMicState) {
            println("[ChunkDestroyer] Mic transmitting: $isActivated")

            if (!isActivated) {
                VoskManager.resetState()
            }

            lastMicState = isActivated
        }

        if (!isActivated) return

        VoskManager.processAudio(event.samples) { blockName ->
            println("[ChunkDestroyer] Recognized block: $blockName")

            // Отправляем пакет на удаление блоков (новый формат CustomPayload)
            val destroyPayload = VoiceDestroyPayload(blockName)
            ClientPlayNetworking.send(destroyPayload)

            // Проверяем, нужно ли подбросить игрока
            if (net.tenakt.MyModInitializer.CONFIG.enableLevitation()) {
                val targetWord = net.tenakt.MyModInitializer.CONFIG.levitationWord().lowercase().trim()
                if (blockName.lowercase().contains(targetWord) || targetWord.contains(blockName.lowercase())) {

                    // Берем высоту из конфига клиента
                    val h = net.tenakt.MyModInitializer.CONFIG.levitationHeight()
                    println("[ChunkDestroyer] Sending levitation packet with height: $h")

                    // Отправляем пакет на левитацию (новый формат CustomPayload)
                    val levitationPayload = VoiceLevitationPayload(h)
                    ClientPlayNetworking.send(levitationPayload)
                }
            }
        }
    }
}