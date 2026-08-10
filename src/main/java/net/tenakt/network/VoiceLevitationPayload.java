package net.tenakt.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// Теперь пакет принимает цифру (height)
public record VoiceLevitationPayload(int height) implements CustomPayload {
    public static final CustomPayload.Id<VoiceLevitationPayload> ID = new CustomPayload.Id<>(Identifier.of("chunk-destroyer", "voice_levitation"));

    // Учим майнкрафт кодировать и раскодировать эту цифру
    public static final PacketCodec<RegistryByteBuf, VoiceLevitationPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, VoiceLevitationPayload::height,
            VoiceLevitationPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}