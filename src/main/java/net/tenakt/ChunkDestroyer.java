package net.tenakt;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.tenakt.network.VoiceDestroyPayload;
import net.tenakt.network.VoiceLevitationPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkDestroyer implements ModInitializer {

    // Мапа для отслеживания полета: UUID игрока -> количество тиков в воздухе
    public static final Map<UUID, Integer> LEVITATING_PLAYERS = new ConcurrentHashMap<>();

    public record PlayerSettings(int radius, int heightUp, int heightDown){}

    public static final Map<UUID, PlayerSettings> PLAYER_SETTINGS = new ConcurrentHashMap<>();

    public static final PlayerSettings DEFEAULT_SETTINGS = new PlayerSettings(16, 384, 384);

    @Override
    public void onInitialize() {
        // Регистрируем типы payload'ов на сервере (для приема от клиента)
        PayloadTypeRegistry.playC2S().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VoiceLevitationPayload.ID, VoiceLevitationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VoiceDestroyPayload.ID, VoiceDestroyPayload.CODEC);

        // 1. Регистрируем обработчик пакета синхронизации конфига
        ServerPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                PLAYER_SETTINGS.put(player.getUuid(), new PlayerSettings(payload.radius(), payload.up(), payload.down()));
            });
        });

        // 2. Регистрируем обработчик пакета подбрасывания
        ServerPlayNetworking.registerGlobalReceiver(VoiceLevitationPayload.ID, (payload, context) -> {
            int height = payload.height();
            ServerPlayerEntity player = context.player();

            System.out.println("[ChunkDestroyer Server] Received levitation packet with height: " + height);

            context.server().execute(() -> {
                try { player.stopRiding(); } catch (Exception ignored) {}

                // Защита от нулевой высоты из конфига
                if (height <= 0) return;

                // Чистая физическая формула для достижения нужной высоты
                double velocityY = Math.sqrt(2.0 * 0.08 * height);

                System.out.println("[ChunkDestroyer Server] Applying Y velocity: " + velocityY);

                // 1. Микро-телепорт на 0.1 блока вверх.
                // Это отрывает игрока от земли, чтобы клиентский античит/физика не обнулили скорость.
                player.requestTeleport(player.getX(), player.getY() + 0.1, player.getZ());

                // 2. Используем addVelocity вместо setVelocity
                player.addVelocity(0.0, velocityY, 0.0);
                player.velocityDirty = true;

                // 3. ПРИНУДИТЕЛЬНО отправляем пакет скорости прямо сейчас, не дожидаясь конца тика
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));

                // Применяем Slow Falling //

                // Выдаем Slow Falling с огромным запасом — на 27 минут (32767 тиков).
                // Мы не боимся, что он будет висеть долго, так как наш обработчик тиков снимет его при приземлении.
                StatusEffectInstance slowFalling = new StatusEffectInstance(StatusEffects.SLOW_FALLING, 32767, 0, false, false, true);
                player.addStatusEffect(slowFalling);

                // Добавляем игрока в наш список отслеживания. Начинаем счетчик с 0.
                LEVITATING_PLAYERS.put(player.getUuid(), 0);

                System.out.println("[ChunkDestroyer Server] Player launched successfully.");
            });
        });


        // 3. Регистрируем обработчик пакета разрушения блоков
        ServerPlayNetworking.registerGlobalReceiver(VoiceDestroyPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();

                String text = payload.blockId().trim().toLowerCase();

                if (!text.contains(":")) {
                    text = "minecraft:" + text;
                }

                Identifier id = Identifier.tryParse(text);
                if (id != null && Registries.BLOCK.containsId(id)) {
                    Block targetBlock = Registries.BLOCK.get(id);

                    if (targetBlock != Blocks.AIR) {
                        // Вызываем публичную функцию очистки
                        destroyBlocksForPlayer(player, targetBlock);
                    }
                }
            });
        });

        // 4. Регистрация команды /destroy
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
        // 5. Обработчик тиков: проверяем, приземлился ли игрок
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (LEVITATING_PLAYERS.isEmpty()) return;

            // Проходимся по всем летящим игрокам
            LEVITATING_PLAYERS.entrySet().removeIf(entry -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());

                // Если игрок вышел с сервера, перестаем его отслеживать
                if (p == null) return true;

                int ticks = entry.getValue() + 1;
                entry.setValue(ticks);

                // ВАЖНО: Ждем хотя бы 20 тиков (1 секунду) после запуска.
                // Иначе сервер может снять эффект в ту же миллисекунду,
                // когда мы подбросили игрока, думая, что он еще стоит на земле.
                if (ticks > 20 && p.isOnGround()) {
                    // Игрок коснулся земли! Забираем эффект.
                    p.removeStatusEffect(StatusEffects.SLOW_FALLING);
                    return true; // Удаляем игрока из списка отслеживания
                }

                return false; // Оставляем в списке, пусть летит дальше
            });
        });
    }

    /**
     * Публичный метод для уничтожения блоков вокруг игрока на основе его настроек радиуса.
     * Возвращает количество удалённых блоков.
     */
    public static int destroyBlocksForPlayer(ServerPlayerEntity player, Block targetBlock) {
        if (targetBlock == Blocks.AIR) return 0;

        // Исправлено: приводим player.getWorld() к ServerWorld
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