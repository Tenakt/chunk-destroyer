package net.tenakt.server

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.tenakt.ChunkDestroyer

object VoiceServerHandler {
    fun init() {
        // No-op: packet handlers are registered in ChunkDestroyer for PacketByteBuf-based channels
    }
}