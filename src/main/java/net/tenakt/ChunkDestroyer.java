package net.tenakt;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.tenakt.network.ConfigSyncPayload;
import net.tenakt.network.VoiceLevitationPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkDestroyer implements ModInitializer {
    public record PlayerSettings(int radius, int heightUp, int heightDown){}

    public static final Map<UUID, PlayerSettings> PLAYER_SETTINGS = new ConcurrentHashMap<>();

    public static final PlayerSettings DEFEAULT_SETTINGS = new PlayerSettings(16, 384, 384);

    @Override
    public void onInitialize() {

        // ==========================================
        // 1. РЕГИСТРАЦИЯ ПАКЕТА ЛЕВИТАЦИИ
        // ==========================================
        PayloadTypeRegistry.playC2S().register(VoiceLevitationPayload.ID, VoiceLevitationPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(VoiceLevitationPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (player == null) return;

                int height = payload.height();

                // 1. Снимаем флаг земли, чтобы игра не гасила импульс
                player.setOnGround(false);

                // 2. Сбрасываем дистанцию падения чтобы не получить урон
                player.fallDistance = 0F;

                // 3. Задаем вертикальную скорость (выстрел вверх) — рассчитываем по g≈0.08 чтобы достичь примерно указанной высоты
                double velocityY = Math.sqrt(0.16 * height);
                player.addVelocity(0, velocityY, 0);

                // 4. Сообщаем серверу, что скорость изменилась — он сам отправит пакет клиенту в следующем тике
                player.velocityDirty = true;

                // 5. Даем плавное падение на 10 секунд без пузырьков
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, false, false, true));
            });
        });

        // ==========================================
        // 2. РЕГИСТРАЦИЯ СИНХРОНИЗАЦИИ НАСТРОЕК
        // ==========================================
        PayloadTypeRegistry.playC2S().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                PLAYER_SETTINGS.put(player.getUuid(), new PlayerSettings(payload.radius(), payload.up(), payload.down()));
            });
        });

        // ==========================================
        // 3. РЕГИСТРАЦИЯ КОМАНДЫ /destroy
        // ==========================================
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("destroy")
                    .then(CommandManager.argument("blockname", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                String remaining = builder.getRemaining().toLowerCase();

                                for (Identifier id : Registries.BLOCK.getIds()) {
                                    String fullId = id.toString();

                                    if (fullId.contains(remaining)) {
                                        builder.suggest(fullId);
                                    }
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player == null) return 0;

                                String text = StringArgumentType.getString(context, "blockname").trim().toLowerCase();
                                if (!text.contains(":")) {
                                    text = "minecraft:" + text;
                                }

                                Identifier id = Identifier.tryParse(text);

                                if (id != null && Registries.BLOCK.containsId(id)) {
                                    Block targetBlock = Registries.BLOCK.get(id);

                                    if (targetBlock == Blocks.AIR) {
                                        context.getSource().sendError(Text.translatable("command.chunkdestroyer.error.air"));
                                        return 0;
                                    }

                                    // Вызываем публичную функцию очистки
                                    int removedCount = destroyBlocksForPlayer(player, targetBlock);

                                    context.getSource().sendFeedback(() -> Text.translatable("command.chunkdestroyer.success", removedCount), false);
                                    return 1;
                                } else {
                                    context.getSource().sendError(Text.translatable("command.chunkdestroyer.error.not_found", text));
                                    return 0;
                                }
                            })));
        });
    }

    /**
     * Публичный метод для уничтожения блоков вокруг игрока на основе его настроек радиуса.
     * Возвращает количество удалённых блоков.
     */
    public static int destroyBlocksForPlayer(ServerPlayerEntity player, Block targetBlock) {
        if (targetBlock == Blocks.AIR) return 0;

        ServerWorld world = player.getCommandSource().getWorld();
        BlockPos playerPos = player.getBlockPos();

        PlayerSettings settings = PLAYER_SETTINGS.getOrDefault(player.getUuid(), DEFEAULT_SETTINGS);

        int radius = settings.radius();
        int halfRadius = radius / 2;

        int minX = playerPos.getX() - halfRadius;
        int maxX = playerPos.getX() + (radius - halfRadius - 1);

        int minZ = playerPos.getZ() - halfRadius;
        int maxZ = playerPos.getZ() + (radius - halfRadius - 1);

        int heightUp = settings.heightUp();
        int heightDown = settings.heightDown();

        int minY = playerPos.getY() - heightDown;
        int maxY = playerPos.getY() + heightUp;

        int worldMinY = world.getBottomY();
        int worldMaxY = world.getBottomY() + world.getHeight() - 1;

        if (minY < worldMinY) minY = worldMinY;
        if (maxY > worldMaxY) maxY = worldMaxY;

        AtomicInteger removedCount = new AtomicInteger(0);
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    mutablePos.set(x, y, z);

                    if (world.getBlockState(mutablePos).isOf(targetBlock)) {
                        world.setBlockState(mutablePos, Blocks.AIR.getDefaultState());
                        removedCount.incrementAndGet();
                    }
                }
            }
        }

        return removedCount.get();
    }
}