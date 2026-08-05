package net.tenakt.client;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.tenakt.network.ConfigSyncPayload;
import net.tenakt.MyModInitializer;

public class DestroyConfigScreen extends BaseUIModelScreen<FlowLayout> {

    public DestroyConfigScreen() {
        super(FlowLayout.class, DataSource.asset(Identifier.of("chunk-destroyer", "destroy_config")));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        TextBoxComponent radiusInput = rootComponent.childById(TextBoxComponent.class, "radius-input");
        TextBoxComponent upInput = rootComponent.childById(TextBoxComponent.class,"up-input");
        TextBoxComponent downInput = rootComponent.childById(TextBoxComponent.class, "down-input");
        ButtonComponent saveButton = rootComponent.childById(ButtonComponent.class, "save-button");

        if (radiusInput != null) {
            int currentRadius = MyModInitializer.CONFIG.destroyRadius();
            radiusInput.text(String.valueOf(currentRadius));
        }

        if (upInput != null) {
            int currentUpRadius = MyModInitializer.CONFIG.heightUp();
            upInput.text(String.valueOf(currentUpRadius));
        }

        if (downInput != null) {
            int currentDownRadius = MyModInitializer.CONFIG.heightDown();
            downInput.text(String.valueOf(currentDownRadius));
        }


        if (saveButton != null && radiusInput != null && upInput != null && downInput != null) {
            saveButton.onPress(button -> {
                try {
                    int newRadius = Integer.parseInt(radiusInput.getText());
                    int newUpRadius = Integer.parseInt(upInput.getText());
                    int newDownRadius = Integer.parseInt(downInput.getText());

                    MyModInitializer.CONFIG.destroyRadius(newRadius);
                    MyModInitializer.CONFIG.heightUp(newUpRadius);
                    MyModInitializer.CONFIG.heightDown(newDownRadius);

                    if (MinecraftClient.getInstance().getNetworkHandler() != null){
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ConfigSyncPayload(newRadius, newUpRadius, newDownRadius));
                    }
                    this.close();
                } catch (NumberFormatException e) {
                }
            });
        }
    }
}