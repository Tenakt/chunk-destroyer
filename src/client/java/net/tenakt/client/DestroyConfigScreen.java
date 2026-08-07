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
import io.wispforest.owo.ui.component.DiscreteSliderComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DestroyConfigScreen extends BaseOwoScreen<FlowLayout> {

    // Список блоков, которые сейчас выбраны (активны для удаления)
    private List<String> activeBlocks = new ArrayList<>(MyModInitializer.CONFIG.allowedBlocks());
    private FlowLayout blockListContainer;
    private TextBoxComponent searchInput;

    // Режим отображения: true — показываем только выбранные блоки, false — результаты поиска
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

        // ==========================================
        // ЛЕВАЯ КОЛОНКА (Настройки)
        // ==========================================
        var leftColumn = UIContainers.verticalFlow(Sizing.fill(50), Sizing.fill(100));
        leftColumn.horizontalAlignment(HorizontalAlignment.CENTER);

        var title = UIComponents.label(Text.literal("Chunk Destroyer Settings")).shadow(true);
        leftColumn.child(title.margins(Insets.bottom(20)));

        var radiusSlider = UIComponents
                .discreteSlider(Sizing.fixed(180), 1, 128)
                .decimalPlaces(0)
                .setFromDiscreteValue(MyModInitializer.CONFIG.destroyRadius());

        radiusSlider.message(value -> Text.literal("Destroy Radius: " + value));

        var upSlider = UIComponents
                .discreteSlider(Sizing.fixed(180),1,384)
                .decimalPlaces(0)
                .setFromDiscreteValue(MyModInitializer.CONFIG.heightUp());

        upSlider.message(value -> Text.literal("Destroy Radius UP: " + value));

        var downSlider = UIComponents
                .discreteSlider(Sizing.fixed(180),1,384)
                .decimalPlaces(0)
                .setFromDiscreteValue(MyModInitializer.CONFIG.heightUp());

        downSlider.message(value -> Text.literal("Destroy Radius Down: " + value));

        leftColumn.child(radiusSlider);
        leftColumn.child(upSlider);
        leftColumn.child(downSlider);

        var saveButton = UIComponents.button(Text.literal("Save & Close"), button -> {
            try {
                int newRadius = (int) radiusSlider.discreteValue();
                int newUpRadius = (int) upSlider.discreteValue();
                int newDownRadius = (int) downSlider.discreteValue();

                MyModInitializer.CONFIG.destroyRadius(newRadius);
                MyModInitializer.CONFIG.heightUp(newUpRadius);
                MyModInitializer.CONFIG.heightDown(newDownRadius);

                MyModInitializer.CONFIG.allowedBlocks(new ArrayList<>(activeBlocks));

                if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new ConfigSyncPayload(newRadius, newUpRadius, newDownRadius)
                    );
                }

                VoskManager.reloadRecognizer();
                this.close();
            } catch (NumberFormatException ignored) {}
        });

        saveButton.sizing(Sizing.fixed(200), Sizing.fixed(20)).margins(Insets.top(30));
        leftColumn.child(saveButton);

        // ==========================================
        // ПРАВАЯ КОЛОНКА (Список блоков и Поиск)
        // ==========================================
        var rightColumn = UIContainers.verticalFlow(Sizing.fill(50), Sizing.fill(100));
        rightColumn.horizontalAlignment(HorizontalAlignment.CENTER);

        var blocksTitle = UIComponents.label(Text.literal("Allowed Blocks")).shadow(true);
        rightColumn.child(blocksTitle.margins(Insets.bottom(2)));

        var warningLabel = UIComponents.label(Text.literal("§7(Search to add, X to remove)"));
        rightColumn.child(warningLabel.margins(Insets.bottom(8)));

        // Контейнер списка со скроллом
        blockListContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        blockListContainer.padding(Insets.of(3));

        var scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fill(75), blockListContainer);
        scroll.surface(Surface.DARK_PANEL);
        rightColumn.child(scroll);

        // Строка поиска (вместо добавления)
        var searchRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.verticalAlignment(VerticalAlignment.CENTER).margins(Insets.top(10));

        searchInput = UIComponents.textBox(Sizing.fill(85));
        searchInput.setSuggestion("Search block...");

        var searchBtn = UIComponents.button(Text.literal("🔍"), button -> {
            String query = searchInput.getText().trim().toLowerCase();
            if (query.isEmpty()) {
                // Если поиск пустой, возвращаем показ активных блоков
                isShowingSearchResults = false;
            } else {
                // Ищем по всем блокам игры и модов (проверяем ID вроде minecraft:stone или modid:block)
                isShowingSearchResults = true;
                searchResults = Registries.BLOCK.getIds().stream()
                        .map(id -> id.getPath().replace('_', ' '))
                        .filter(path -> path.contains(query))
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
            }
            refreshBlockList();
        });

        searchBtn.sizing(Sizing.fixed(24), Sizing.fixed(24)).margins(Insets.left(5));

        searchRow.child(searchInput).child(searchBtn);
        rightColumn.child(searchRow);

        // Кнопка возврата к списку выбранных, если мы в режиме поиска
        var backToListBtn = UIComponents.button(Text.literal("Show My Active Blocks"), button -> {
            isShowingSearchResults = false;
            searchInput.text("");
            refreshBlockList();
        });
        backToListBtn.sizing(Sizing.fill(100), Sizing.fixed(18)).margins(Insets.top(4));
        rightColumn.child(backToListBtn);

        rootComponent.child(leftColumn).child(rightColumn);
        refreshBlockList();
    }

    private FlowLayout createInputRow(String text, Object inputObj) {
        var row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER).margins(Insets.bottom(8));

        var label = UIComponents.label(Text.literal(text));
        label.margins(Insets.right(8));

        row.child(label).child((io.wispforest.owo.ui.core.UIComponent) inputObj);
        return row;
    }

    private void refreshBlockList() {
        if (blockListContainer == null) return;
        blockListContainer.clearChildren();

        // Определяем, что выводить: результаты поиска или текущий список активных блоков
        List<String> itemsToDisplay = isShowingSearchResults ? searchResults : activeBlocks;

        if (itemsToDisplay.isEmpty()) {
            var emptyLabel = UIComponents.label(Text.literal(isShowingSearchResults ? "§cNo blocks found" : "§7List is empty"));
            blockListContainer.child(emptyLabel);
            return;
        }

        for (String block : itemsToDisplay) {
            var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.surface(Surface.flat(0x80000000)); // Черная полупрозрачная подложка
            row.padding(Insets.of(3));
            row.margins(Insets.bottom(3));

            // Делаем текст немного меньше (можно визуально отделить)
            var label = UIComponents.label(Text.literal(block));
            label.sizing(Sizing.fill(80), Sizing.content());
            label.margins(Insets.left(3));

            // Кнопка справа: если мы в активных — это удалить ("X"), если в поиске — добавить ("+")
            String actionText = isShowingSearchResults ? (activeBlocks.contains(block) ? "✔" : "+") : "X";

            var actionBtn = UIComponents.button(Text.literal(actionText), btn -> {
                if (isShowingSearchResults) {
                    // Режим поиска: клик добавляет блок в активные, если его там еще нет
                    if (!activeBlocks.contains(block)) {
                        activeBlocks.add(block);
                        refreshBlockList(); // Обновит галочку
                    }
                } else {
                    // Режим активных: клик удаляет блок
                    activeBlocks.remove(block);
                    refreshBlockList();
                }
            });

            actionBtn.sizing(Sizing.fixed(18), Sizing.fixed(18));

            row.child(label).child(actionBtn);
            blockListContainer.child(row);
        }
    }
}