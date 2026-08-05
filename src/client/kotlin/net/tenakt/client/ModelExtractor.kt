package net.tenakt.client

import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ModelExtractor {

    /**
     * Возвращает папку с распакованной моделью.
     * Если модели ещё нет - распаковывает её из resources/models/<name>.zip
     */
    fun extractModel(name: String): File {
        val gameDir = FabricLoader.getInstance().gameDir.toFile()

        val modelsDir = File(gameDir, "chunk-destroyer/models")
        val outputDir = File(modelsDir, name)

        // Уже распакована
        if (outputDir.exists()) {
            return outputDir
        }

        modelsDir.mkdirs()

        val resourcePath = "/models/$name.zip"

        val stream = ModelExtractor::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Model archive not found: $resourcePath")

        ZipInputStream(stream).use { zip ->

            var entry: ZipEntry?

            while (true) {

                entry = zip.nextEntry ?: break

                val outFile = File(outputDir, entry.name)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {

                    outFile.parentFile.mkdirs()

                    FileOutputStream(outFile).use { output ->
                        zip.copyTo(output)
                    }
                }

                zip.closeEntry()
            }
        }

        println("[ChunkDestroyer] Model extracted to ${outputDir.absolutePath}")

        return outputDir
    }
}