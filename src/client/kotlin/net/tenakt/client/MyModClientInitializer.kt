package net.tenakt.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import su.plo.voice.api.client.PlasmoVoiceClient

class MyModClientInitializer : ClientModInitializer {

    companion object {
        private lateinit var openConfigKey: KeyBinding
    }

    private val voiceAddon = PlasmoVoiceAddon()

    override fun onInitializeClient() {

        PlasmoVoiceClient.getAddonsLoader().load(voiceAddon)

        openConfigKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.chunk-destroyer.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KeyBinding.Category.create(
                    Identifier.of("category.chunk-destroyer")
                )
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openConfigKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(DestroyConfigScreen())
                }
            }
        }
    }
}