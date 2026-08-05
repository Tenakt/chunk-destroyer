package net.tenakt

import net.fabricmc.api.ModInitializer
import net.tenakt.server.VoiceServerHandler
import net.tenakt.MyConfig;

class MyModInitializer : ModInitializer {

    companion object {
        @JvmField
        val CONFIG: MyConfig = MyConfig.createAndLoad()
    }

    override fun onInitialize() {
        VoiceServerHandler.init()
    }
}