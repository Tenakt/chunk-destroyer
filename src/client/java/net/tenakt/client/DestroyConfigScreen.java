package net.tenakt.client;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.tenakt.MyModInitializer;
import net.tenakt.network.ConfigSyncPayload;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DestroyConfigScreen extends BaseOwoScreen<FlowLayout> {

    // Храним системные ID блоков, например "minecraft:grass_block"
    private Set<String> activeBlocks = new HashSet<>();
    private FlowLayout blockListContainer;
    private TextBoxComponent searchInput;
    private boolean isShowingSearchResults = false;
    private List<BlockCacheEntry> searchResults = new ArrayList<>();

    // Кэш для моментального поиска без фризов
    private final List<BlockCacheEntry> allBlocksCache = new ArrayList<>();

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::horizontalFlow);
    }

    @Override
    protected void init() {
        // 1. Загружаем кэш ДО создания интерфейса
        if (allBlocksCache.isEmpty()) {
            Language language = Language.getInstance();
            for (Identifier id : Registries.BLOCK.getIds()) {
                Block block = Registries.BLOCK.get(id);
                String path = id.getPath();
                String localized = normalizeRus(language.get(block.getTranslationKey()));
                String voiceAlias = normalizeRus(getVoiceAlias(path));

                allBlocksCache.add(new BlockCacheEntry(id, path, localized, voiceAlias));
            }
        }

        // 2. Читаем текущий конфиг
        activeBlocks.clear();
        for (String s : MyModInitializer.CONFIG.allowedBlocks()) {
            if (!s.contains(":")) {
                String normalizedS = normalizeRus(s);
                Optional<BlockCacheEntry> match = allBlocksCache.stream()
                        .filter(b -> b.path.equals(normalizedS)
                                || (b.voiceAlias != null && b.voiceAlias.equals(normalizedS))
                                || (b.localized != null && b.localized.equals(normalizedS)))
                        .findFirst();

                if (match.isPresent()) {
                    activeBlocks.add(match.get().id.toString());
                } else {
                    activeBlocks.add("minecraft:" + s);
                }
            } else {
                activeBlocks.add(s);
            }
        }

        // 3. ТОЛЬКО ТЕПЕРЬ вызываем super.init(), который запустит метод build()
        // и отрисует список, так как activeBlocks и allBlocksCache уже заполнены
        super.init();
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.sizing(Sizing.fill(100), Sizing.fill(100));
        rootComponent.padding(Insets.of(15));

        // --- ЛЕВАЯ КОЛОНКА ---
        var leftColumn = UIContainers.verticalFlow(Sizing.fill(50), Sizing.fill(100));
        leftColumn.horizontalAlignment(HorizontalAlignment.CENTER);

        var title = UIComponents.label(Text.translatable("gui.chunkdestroyer.title")).shadow(true);
        leftColumn.child(title.margins(Insets.bottom(20)));

        var radiusSlider = UIComponents.discreteSlider(Sizing.fixed(180), 1, 128)
                .decimalPlaces(0)
                .setFromDiscreteValue(MyModInitializer.CONFIG.destroyRadius());
        radiusSlider.message(value -> Text.translatable("gui.chunkdestroyer.radius", value));

        var upSlider = UIComponents.discreteSlider(Sizing.fixed(180), 1, 384)
                .decimalPlaces(0)
                .setFromDiscreteValue(MyModInitializer.CONFIG.heightUp());
        upSlider.message(value -> Text.translatable("gui.chunkdestroyer.radius_up", value));

        var downSlider = UIComponents.discreteSlider(Sizing.fixed(180), 1, 384)
                .decimalPlaces(0)
                .setFromDiscreteValue(MyModInitializer.CONFIG.heightDown());
        downSlider.message(value -> Text.translatable("gui.chunkdestroyer.radius_down", value));

        leftColumn.child(radiusSlider);
        leftColumn.child(upSlider);
        leftColumn.child(downSlider);

        // Подбрасывание
        var levRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        levRow.horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .margins(Insets.top(12).add(0, 8, 0, 0));

        boolean currentLev = MyModInitializer.CONFIG.enableLevitation();
        var levCheckbox = UIComponents.button(Text.literal(currentLev ? "[✔]" : "[X]"), btn -> {
            boolean newState = !MyModInitializer.CONFIG.enableLevitation();
            MyModInitializer.CONFIG.enableLevitation(newState);
            btn.setMessage(Text.literal(newState ? "[✔]" : "[X]"));
        });
        levCheckbox.sizing(Sizing.fixed(24), Sizing.fixed(20)).margins(Insets.right(4));

        var levLabel = UIComponents.label(Text.translatable("gui.chunkdestroyer.bounce_word"));
        levLabel.margins(Insets.right(4));

        var levWordInput = UIComponents.textBox(Sizing.fixed(55));
        levWordInput.text(MyModInitializer.CONFIG.levitationWord());

        levRow.child(levCheckbox).child(levLabel).child(levWordInput);
        leftColumn.child(levRow);

        var levHeightInput = UIComponents.textBox(Sizing.fixed(55));
        levHeightInput.text(String.valueOf(MyModInitializer.CONFIG.levitationHeight()));
        leftColumn.child(createInputRow(Text.translatable("gui.chunkdestroyer.fly_height"), levHeightInput));

        // Кнопка сохранения
        var saveButton = UIComponents.button(Text.translatable("gui.chunkdestroyer.save"), button -> {
            try {
                int newRadius = (int) radiusSlider.discreteValue();
                int newUpRadius = (int) upSlider.discreteValue();
                int newDownRadius = (int) downSlider.discreteValue();

                MyModInitializer.CONFIG.destroyRadius(newRadius);
                MyModInitializer.CONFIG.heightUp(newUpRadius);
                MyModInitializer.CONFIG.heightDown(newDownRadius);

                // Сохраняем уникальные блоки напрямую
                List<String> toSave = activeBlocks.stream().distinct().toList();
                MyModInitializer.CONFIG.allowedBlocks(new ArrayList<>(toSave));

                MyModInitializer.CONFIG.levitationWord(levWordInput.getText().trim().toLowerCase());

                try {
                    MyModInitializer.CONFIG.levitationHeight(Integer.parseInt(levHeightInput.getText()));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }

                MyModInitializer.CONFIG.save();

                // Перезагрузка Vosk в фоне, чтобы игра не зависала
                CompletableFuture.runAsync(() -> {
                    try {
                        VoskManager.reloadRecognizer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new ConfigSyncPayload(newRadius, newUpRadius, newDownRadius)
                    );
                }

                this.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        saveButton.sizing(Sizing.fixed(180), Sizing.fixed(20)).margins(Insets.top(30));
        leftColumn.child(saveButton);


        // --- ПРАВАЯ КОЛОНКА ---
        var rightColumn = UIContainers.verticalFlow(Sizing.fill(50), Sizing.fill(100));
        rightColumn.horizontalAlignment(HorizontalAlignment.CENTER);

        var blocksTitle = UIComponents.label(Text.translatable("gui.chunkdestroyer.allowed_blocks")).shadow(true);
        rightColumn.child(blocksTitle.margins(Insets.bottom(2)));

        var warningLabel = UIComponents.label(Text.translatable("gui.chunkdestroyer.search_hint"));
        rightColumn.child(warningLabel.margins(Insets.bottom(8)));

        blockListContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        blockListContainer.padding(Insets.of(3));

        var scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fill(60), blockListContainer);
        scroll.surface(Surface.DARK_PANEL);
        rightColumn.child(scroll);

        // Поиск
        var searchRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.verticalAlignment(VerticalAlignment.CENTER).margins(Insets.top(8));

        searchInput = UIComponents.textBox(Sizing.fill(75));
        searchInput.setSuggestion(Text.translatable("gui.chunkdestroyer.search_suggestion").getString());

        searchInput.setChangedListener(text -> {
            if (text.isEmpty()) {
                searchInput.setSuggestion(Text.translatable("gui.chunkdestroyer.search_suggestion").getString());
                isShowingSearchResults = false;
                searchResults.clear();
            } else {
                searchInput.setSuggestion("");
                isShowingSearchResults = true;

                String query = normalizeRus(text);

                // Моментальный поиск по кэшу
                searchResults = allBlocksCache.stream()
                        .filter(b -> b.matches(query))
                        .sorted(Comparator.comparingInt(b -> b.score(query)))
                        .collect(Collectors.toList());
            }
            refreshBlockList();
        });

        var searchBtn = UIComponents.button(Text.literal("🔍"), button -> {
            // Кнопка поиска теперь просто триггерит то же самое, но логика уже в setChangedListener
            refreshBlockList();
        });

        searchBtn.sizing(Sizing.fixed(24), Sizing.fixed(24)).margins(Insets.left(4));
        searchRow.child(searchInput).child(searchBtn);
        rightColumn.child(searchRow);

        // Кнопка возврата к активным
        var backToListBtn = UIComponents.button(Text.translatable("gui.chunkdestroyer.show_active"), button -> {
            isShowingSearchResults = false;
            searchInput.text("");
            searchInput.setSuggestion(Text.translatable("gui.chunkdestroyer.search_suggestion").getString());
            refreshBlockList();
        });

        backToListBtn.sizing(Sizing.fill(100), Sizing.fixed(20)).margins(Insets.top(4));
        rightColumn.child(backToListBtn);

        rootComponent.child(leftColumn).child(rightColumn);

        // Рисуем список в первый раз
        refreshBlockList();
    }

    private FlowLayout createInputRow(Text text, Object inputObj) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .margins(Insets.bottom(8));

        var label = UIComponents.label(text);
        label.margins(Insets.right(6));

        row.child(label).child((io.wispforest.owo.ui.core.UIComponent) inputObj);
        return row;
    }

    // Здесь блок травы привязан ТОЛЬКО к grass_block
    private String getVoiceAlias(String blockPath) {
        if (blockPath.equals("grass_block")) return "блок травы";
        if (blockPath.equals("netherrack")) return "адский камень";
        return null;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String normalizeRus(String s) {
        if (s == null) return null;
        return s.toLowerCase().replace('ё', 'е').replaceAll("\\s+", " ").trim();
    }

    // --- Отрисовка списка ---
    private void refreshBlockList() {
        if (blockListContainer == null) return;
        blockListContainer.clearChildren();

        List<BlockCacheEntry> itemsToDisplay;

        if (isShowingSearchResults) {
            itemsToDisplay = searchResults;
        } else {
            itemsToDisplay = allBlocksCache.stream()
                    .filter(b -> activeBlocks.contains(b.id.toString()))
                    .collect(Collectors.toList());
        }

        if (itemsToDisplay.isEmpty()) {
            Text emptyText = isShowingSearchResults
                    ? Text.translatable("gui.chunkdestroyer.no_blocks")
                    : Text.translatable("gui.chunkdestroyer.empty_list");

            blockListContainer.child(UIComponents.label(emptyText));
            return;
        }

        for (BlockCacheEntry entry : itemsToDisplay) {
            var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.surface(Surface.flat(0x80000000));
            row.padding(Insets.of(3));
            row.margins(Insets.bottom(3));

            // Если есть voiceAlias ("блок травы"), выводим его. Иначе обычный перевод.
            Text displayComponent = (entry.voiceAlias != null && !entry.voiceAlias.isEmpty())
                    ? Text.literal(capitalizeFirst(entry.voiceAlias))
                    : Text.translatable(Registries.BLOCK.get(entry.id).getTranslationKey());

            var label = UIComponents.label(displayComponent);
            label.sizing(Sizing.fill(75), Sizing.content());
            label.margins(Insets.left(3));

            boolean isActive = activeBlocks.contains(entry.id.toString());
            String actionText = isShowingSearchResults ? (isActive ? "✔" : "+") : "X";

            var actionBtn = UIComponents.button(Text.literal(actionText), btn -> {
                if (isShowingSearchResults) {
                    if (!isActive) activeBlocks.add(entry.id.toString());
                } else {
                    activeBlocks.remove(entry.id.toString());
                }
                refreshBlockList(); // Моментальное обновление
            });

            actionBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));
            row.child(label).child(actionBtn);
            blockListContainer.child(row);
        }
    }

    // --- Класс для кэширования данных блока ---
    private static class BlockCacheEntry {
        final Identifier id;
        final String path;
        final String localized;
        final String voiceAlias;

        BlockCacheEntry(Identifier id, String path, String localized, String voiceAlias) {
            this.id = id;
            this.path = path.replace('_', ' ').toLowerCase();
            this.localized = localized;
            this.voiceAlias = voiceAlias;
        }

        boolean matches(String query) {
            return path.contains(query)
                    || (localized != null && localized.contains(query))
                    || (voiceAlias != null && voiceAlias.contains(query))
                    || id.toString().toLowerCase().contains(query);
        }

        int score(String query) {
            if (path.equals(query) || (localized != null && localized.equals(query)) || (voiceAlias != null && voiceAlias.equals(query))) return 0;
            if (path.startsWith(query) || (localized != null && localized.startsWith(query)) || (voiceAlias != null && voiceAlias.startsWith(query))) return 1;
            return 2;
        }
    }
}