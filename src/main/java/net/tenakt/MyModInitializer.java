package net.tenakt;

import net.fabricmc.api.ModInitializer;
import net.tenakt.MyConfig;

public class MyModInitializer implements ModInitializer {


    public static final MyConfig CONFIG = MyConfig.createAndLoad();

    @Override
    public void onInitialize() {
    }
}