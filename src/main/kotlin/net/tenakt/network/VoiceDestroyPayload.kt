package net.tenakt.network

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class VoiceDestroyPayload(val blockId: String) : CustomPayload {
    override fun getId(): CustomPayload.Id<VoiceDestroyPayload> = ID

    companion object {
        val ID = CustomPayload.Id<VoiceDestroyPayload>(Identifier.of("chunk-destroyer", "voice_destroy"))
        val CODEC: PacketCodec<RegistryByteBuf, VoiceDestroyPayload> = PacketCodec.tuple(
            PacketCodecs.STRING, VoiceDestroyPayload::blockId,
            ::VoiceDestroyPayload
        )
    }
}