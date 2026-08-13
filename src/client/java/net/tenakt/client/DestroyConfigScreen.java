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
        searchInput.setChangedListener(text -> {
            if (text.isEmpty()) {
                searchInput.setSuggestion(Text.translatable("gui.chunkdestroyer.search_suggestion").getString());
            } else {
                searchInput.setSuggestion("");
            }
        });

        var searchBtn = UIComponents.button(Text.literal("🔍"), button -> {
            String query = searchInput.getText().trim().toLowerCase();

            if (query.isEmpty()) {
                isShowingSearchResults = false;
            } else {
                isShowingSearchResults = true;
                searchResults = Registries.BLOCK.getIds().stream()
                        .filter(id -> {
                            net.minecraft.block.Block block = Registries.BLOCK.get(id);
                            String englishName = id.getPath().replace('_', ' ').toLowerCase();
                            String localizedName = net.minecraft.util.Language.getInstance()
                                    .get(block.getTranslationKey()).toLowerCase();
                            return englishName.contains(query)
                                    || localizedName.contains(query)
                                    || id.toString().toLowerCase().contains(query);
                        })
                        .map(id -> id.getPath().replace('_', ' '))
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
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

            Text displayComponent = Text.literal(block);

            String fixedForId = block.replace(' ', '_');
            String idString = fixedForId.contains(":") ? fixedForId : "minecraft:" + fixedForId;
            Identifier blockId = Identifier.tryParse(idString);

            if (blockId != null && Registries.BLOCK.containsId(blockId)) {
                displayComponent = Text.translatable(
                        Registries.BLOCK.get(blockId).getTranslationKey()
                );
            }

            var label = UIComponents.label(displayComponent);
            label.sizing(Sizing.fill(75), Sizing.content());
            label.margins(Insets.left(3));

            String actionText = isShowingSearchResults
                    ? (activeBlocks.contains(block) ? "✔" : "+")
                    : "X";

            var actionBtn = UIComponents.button(Text.literal(actionText), btn -> {
                if (isShowingSearchResults) {
                    if (!activeBlocks.contains(block)) {
                        activeBlocks.add(block);
                        refreshBlockList();
                    }
                } else {
                    activeBlocks.remove(block);
                    refreshBlockList();
                }
            });

            actionBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));

            row.child(label).child(actionBtn);
            blockListContainer.child(row);
        }
    }
}