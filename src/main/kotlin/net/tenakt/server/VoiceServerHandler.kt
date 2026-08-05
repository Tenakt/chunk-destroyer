package net.tenakt.server

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.tenakt.ChunkDestroyer
import net.tenakt.network.VoiceDestroyPayload

object VoiceServerHandler {
    fun init() {
        // Регистрируем пакет
        PayloadTypeRegistry.playC2S().register(VoiceDestroyPayload.ID, VoiceDestroyPayload.CODEC)

        // Слушаем пакеты
        registerGlobalReceiver(VoiceDestroyPayload.ID) { payload, context ->
            val player = context.player()
            val rawId = payload.blockId
            val identifier = Identifier.tryParse(if (rawId.contains(":")) rawId else "minecraft:$rawId")

            if (identifier != null && Registries.BLOCK.containsId(identifier)) {
                val block = Registries.BLOCK.get(identifier)
                context.server().execute {
                    ChunkDestroyer.destroyBlocksForPlayer(player, block)
                }
            }
        }
    }
}