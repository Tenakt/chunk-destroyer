package net.tenakt.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VoiceDestroyPayload(String blockId) implements CustomPayload {

    public static final CustomPayload.Id<VoiceDestroyPayload> ID = new CustomPayload.Id<>(Identifier.of("chunk-destroyer", "voice_destroy"));

    // Учим майнкрафт кодировать и раскодировать строку (название блока)
    public static final PacketCodec<RegistryByteBuf, VoiceDestroyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, VoiceDestroyPayload::blockId,
            VoiceDestroyPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}