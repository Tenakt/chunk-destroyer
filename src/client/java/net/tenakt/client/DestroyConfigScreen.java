package net.tenakt.client;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.tenakt.MyModInitializer;
import net.tenakt.network.ConfigSyncPayload;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Comparator;

public class DestroyConfigScreen extends BaseOwoScreen<FlowLayout> {

    private List<String> activeBlocks = new ArrayList<>(MyModInitializer.CONFIG.allowedBlocks());
    private FlowLayout blockListContainer;
    private TextBoxComponent searchInput;
    private boolean isShowingSearchResults = false;
    private List<String> searchResults = new ArrayList<>();

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::horizontalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.sizing(Sizing.fill(100), Sizing.fill(100));
        rootComponent.padding(Insets.of(15));

        // Левая колонка
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

        // Сохранение
        var saveButton = UIComponents.button(Text.translatable("gui.chunkdestroyer.save"), button -> {
            try {
                int newRadius = (int) radiusSlider.discreteValue();
                int newUpRadius = (int) upSlider.discreteValue();
                int newDownRadius = (int) downSlider.discreteValue();

                MyModInitializer.CONFIG.destroyRadius(newRadius);
                MyModInitializer.CONFIG.heightUp(newUpRadius);
                MyModInitializer.CONFIG.heightDown(newDownRadius);
                MyModInitializer.CONFIG.allowedBlocks(new ArrayList<>(activeBlocks));
                MyModInitializer.CONFIG.levitationWord(levWordInput.getText().trim().toLowerCase());

                try {
                    MyModInitializer.CONFIG.levitationHeight(Integer.parseInt(levHeightInput.getText()));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }

                MyModInitializer.CONFIG.save();

                // Важно: пересобираем grammar Vosk после изменения списка блоков
                VoskManager.reloadRecognizer();

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

        // Правая колонка
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
        // Real-time search: update results as user types
        searchInput.setChangedListener(text -> {
            if (text.isEmpty()) {
                searchInput.setSuggestion(Text.translatable("gui.chunkdestroyer.search_suggestion").getString());
                isShowingSearchResults = false;
                searchResults = new ArrayList<>();
                refreshBlockList();
                return;
            } else {
                searchInput.setSuggestion("");
            }

            String query = text.trim().toLowerCase();
            isShowingSearchResults = true;
            searchResults = performSearch(query);

            refreshBlockList();
        });

        var searchBtn = UIComponents.button(Text.literal("🔍"), button -> {
            String query = searchInput.getText().trim().toLowerCase();

            if (query.isEmpty()) {
                isShowingSearchResults = false;
            } else {
                isShowingSearchResults = true;
                searchResults = performSearch(query);
            }

            refreshBlockList();
        });

        searchBtn.sizing(Sizing.fixed(24), Sizing.fixed(24)).margins(Insets.left(4));
        searchRow.child(searchInput).child(searchBtn);
        rightColumn.child(searchRow);

        // Кнопка возврата
        var backToListBtn = UIComponents.button(Text.translatable("gui.chunkdestroyer.show_active"), button -> {
            isShowingSearchResults = false;
            searchInput.text("");
            searchInput.setSuggestion(Text.translatable("gui.chunkdestroyer.search_suggestion").getString());
            refreshBlockList();
        });

        backToListBtn.sizing(Sizing.fill(100), Sizing.fixed(20)).margins(Insets.top(4));
        rightColumn.child(backToListBtn);

        rootComponent.child(leftColumn).child(rightColumn);
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

    private String getVoiceAlias(String blockPath) {
        if (blockPath.equals("dirt")) return "блок травы";
        if (blockPath.equals("netherrack")) return "адский камень";
        return null;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private Identifier resolveIdFromDisplayString(String display) {
        // Try as id path
        String fixed = display.replace(' ', '_');
        String idString = fixed.contains(":") ? fixed : "minecraft:" + fixed;
        Identifier maybe = Identifier.tryParse(idString);
        if (maybe != null && Registries.BLOCK.containsId(maybe)) return maybe;

        // Try matching localized names or voice aliases
        String lower = display.toLowerCase();
        for (Identifier id : Registries.BLOCK.getIds()) {
            net.minecraft.block.Block block = Registries.BLOCK.get(id);
            String localized = net.minecraft.util.Language.getInstance().get(block.getTranslationKey()).toLowerCase();
            String voice = getVoiceAlias(id.getPath());
            if (localized.equals(lower) || (voice != null && voice.equalsIgnoreCase(lower))) return id;
        }

        return null;
    }

    private List<String> performSearch(String query) {
        List<Identifier> candidates = Registries.BLOCK.getIds().stream()
                .filter(id -> {
                    net.minecraft.block.Block block = Registries.BLOCK.get(id);
                    String englishName = id.getPath().replace('_', ' ').toLowerCase();
                    String localizedName = net.minecraft.util.Language.getInstance()
                            .get(block.getTranslationKey()).toLowerCase();
                    String voiceAlias = getVoiceAlias(id.getPath());
                    
                    return englishName.contains(query)
                            || localizedName.contains(query)
                            || id.toString().toLowerCase().contains(query)
                            || (voiceAlias != null && voiceAlias.contains(query));
                })
                .distinct()
                .collect(Collectors.toList());

        Collections.sort(candidates, new Comparator<Identifier>() {
            @Override
            public int compare(Identifier a, Identifier b) {
                String aPath = a.getPath().replace('_', ' ').toLowerCase();
                String bPath = b.getPath().replace('_', ' ').toLowerCase();

                String aLocalized = net.minecraft.util.Language.getInstance()
                        .get(Registries.BLOCK.get(a).getTranslationKey()).toLowerCase();
                String bLocalized = net.minecraft.util.Language.getInstance()
                        .get(Registries.BLOCK.get(b).getTranslationKey()).toLowerCase();

                String aVoiceAlias = getVoiceAlias(a.getPath());
                String bVoiceAlias = getVoiceAlias(b.getPath());

                int scoreA = scoreFor(aPath, aLocalized, aVoiceAlias, query, a);
                int scoreB = scoreFor(bPath, bLocalized, bVoiceAlias, query, b);

                if (scoreA != scoreB) return Integer.compare(scoreA, scoreB);
                // Prefer shorter names then lexicographic
                if (aPath.length() != bPath.length()) return Integer.compare(aPath.length(), bPath.length());
                return aPath.compareTo(bPath);
            }

            private int scoreFor(String path, String localized, String voiceAlias, String q, Identifier id) {
                if (path.equals(q) || localized.equals(q) || (voiceAlias != null && voiceAlias.equals(q)) || id.getPath().equals(q)) return 0; // exact
                if (path.startsWith(q) || localized.startsWith(q) || (voiceAlias != null && voiceAlias.startsWith(q))) return 1; // starts with
                if (path.contains(q) || localized.contains(q) || (voiceAlias != null && voiceAlias.contains(q)) || id.toString().toLowerCase().contains(q)) return 2; // contains
                return 3;
            }
        });

        return candidates.stream()
                .map(id -> id.getPath().replace('_', ' '))
                .distinct()
                .collect(Collectors.toList());
    }

    private void refreshBlockList() {
        if (blockListContainer == null) return;

        blockListContainer.clearChildren();

        List<String> itemsToDisplay = isShowingSearchResults ? searchResults : activeBlocks;

        if (itemsToDisplay.isEmpty()) {
            Text emptyText = isShowingSearchResults
                    ? Text.translatable("gui.chunkdestroyer.no_blocks")
                    : Text.translatable("gui.chunkdestroyer.empty_list");

            blockListContainer.child(UIComponents.label(emptyText));
            return;
        }

        for (String block : itemsToDisplay) {
            var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.surface(Surface.flat(0x80000000));
            row.padding(Insets.of(3));
            row.margins(Insets.bottom(3));

            // Resolve the Identifier for this displayed item. It may be an id ("dirt"),
            // a localized name ("Дёрн"), or a voice alias ("блок травы").
            Identifier blockId = resolveIdFromDisplayString(block);

            Text displayComponent = Text.literal(block);

            String canonical = null;
            String voiceAlias = null;
            String localizedName = null;

            if (blockId != null && Registries.BLOCK.containsId(blockId)) {
                canonical = blockId.getPath().replace('_', ' ');
                String blockPath = blockId.getPath().toLowerCase();
                voiceAlias = getVoiceAlias(blockPath);
                localizedName = net.minecraft.util.Language.getInstance()
                        .get(Registries.BLOCK.get(blockId).getTranslationKey());

                if (voiceAlias != null) {
                    displayComponent = Text.literal(capitalizeFirst(voiceAlias));
                } else {
                    displayComponent = Text.translatable(
                            Registries.BLOCK.get(blockId).getTranslationKey()
                    );
                }
            } else {
                // If we couldn't resolve an id, show the raw text
                displayComponent = Text.literal(block);
            }

            var label = UIComponents.label(displayComponent);
            label.sizing(Sizing.fill(75), Sizing.content());
            label.margins(Insets.left(3));

            String checkCanonical = canonical != null ? canonical : block;
            String checkVoice = voiceAlias != null ? voiceAlias : "";
            String checkLocalized = localizedName != null ? localizedName : "";

            final boolean isActive = activeBlocks.stream().anyMatch(s ->
                    s.equalsIgnoreCase(checkCanonical)
                            || s.equalsIgnoreCase(checkVoice)
                            || s.equalsIgnoreCase(checkLocalized)
            );

            String actionText = isShowingSearchResults
                    ? (isActive ? "✔" : "+")
                    : "X";

            var actionBtn = UIComponents.button(Text.literal(actionText), btn -> {
                if (isShowingSearchResults) {
                    if (!isActive) {
                        // Add canonical id string (e.g., "dirt") so storage is consistent
                        if (canonical != null) activeBlocks.add(canonical);
                        else activeBlocks.add(block);
                        refreshBlockList();
                    }
                } else {
                    // Removing: remove any matching variants
                    activeBlocks.removeIf(s -> s.equalsIgnoreCase(checkCanonical)
                            || s.equalsIgnoreCase(checkVoice)
                            || s.equalsIgnoreCase(checkLocalized));
                    refreshBlockList();
                }
            });

            actionBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));

            row.child(label).child(actionBtn);
            blockListContainer.child(row);
        }
    }
}