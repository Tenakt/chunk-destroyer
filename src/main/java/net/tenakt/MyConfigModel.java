package net.tenakt;

import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import java.util.List;

@Modmenu(modId = "chunk-destroyer")
@Config(name = "chunk-destroyer", wrapperName = "MyConfig")
public class MyConfigModel {
    public int destroyRadius = 16;
    public int heightUp = 384;
    public int heightDown = 384;
    public List<String> allowedBlocks = List.of("stone", "dirt", "grass block", "sand");
}