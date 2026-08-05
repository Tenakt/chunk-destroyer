package net.tenakt

import io.wispforest.owo.config.annotation.Config
import io.wispforest.owo.config.annotation.Modmenu

@Modmenu(modId = "chunk-destroyer")
@Config(name = "chunk-destroyer", wrapperName = "MyConfig")
class MyConfigModel {
    @JvmField var destroyRadius: Int = 16
    @JvmField var heightUp: Int = 384
    @JvmField var heightDown: Int = 384
}