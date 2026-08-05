package net.tenakt.client

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors

object VoskManager {

    private var recognizer: Recognizer? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var isInitialized = false

    private var lastSentTime = 0L
    private const val COOLDOWN_MS = 1500L

    private var lastRecognized = ""

    fun init() {
        println("Initializing Vosk...")

        executor.submit {
            try {
                val gameDir = FabricLoader.getInstance().gameDir.toFile()
                val modelDir = ModelExtractor.extractModel("en-us")
                if (!modelDir.exists()) {
                    println("[ChunkDestroyer] Vosk model not found!")
                    return@submit
                }
                val model = Model(modelDir.absolutePath)
                val grammar = loadGrammar()

                println("[ChunkDestroyer] Loaded grammar:")
                println(grammar)

                recognizer = Recognizer(
                    model,
                    48000f,
                    grammar
                )
                isInitialized = true

                println("[ChunkDestroyer] Vosk model loaded successfully!")

            } catch (e: Exception) {
                println("[ChunkDestroyer] ERROR initializing Vosk:")
                e.printStackTrace()
            }
        }
    }
    private fun loadGrammar(): String {
        // Пытаемся прочитать файл из ресурсов мода
        val inputStream = javaClass.classLoader.getResourceAsStream("assets/chunk-destroyer/blocks.json")

        if (inputStream != null) {
            println("[ChunkDestroyer] Successfully loaded blocks.json from resources!")
            return inputStream.bufferedReader().use { it.readText() }
        }

        println("[ChunkDestroyer] blocks.json not found in resources, using default")

        return """
    [
      "stone",
      "dirt",
      "grass block",
      "sand",
      "gravel",
      "diamond",
      "iron",
      "gold",
      "coal",
      "obsidian",
      "[unk]"
    ]
    """.trimIndent()
    }
    fun processAudio(
        pcmData: ShortArray,
        onBlockRecognized: (String) -> Unit
    ) {
        if (!isInitialized || recognizer == null)
            return

        executor.submit {
            val rec = recognizer ?: return@submit
            val isFinal =
                rec.acceptWaveForm(
                    pcmData,
                    pcmData.size
                )
            val jsonResult =
                if (isFinal)
                    rec.result
                else
                    rec.partialResult

            parseAndTrigger(
                jsonResult,
                onBlockRecognized
            )
        }
    }
    private fun parseAndTrigger(
        json: String,
        onBlockRecognized: (String) -> Unit
    ) {

        val now = System.currentTimeMillis()
        val textMatch =
            """"text"\s*:\s*"([^"]+)""""
                .toRegex()
                .find(json)
                ?: """"partial"\s*:\s*"([^"]+)""""
                    .toRegex()
                    .find(json)
        val text =
            textMatch
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.lowercase()
                ?: return

        if (text.isEmpty())
            return

        if (text == lastRecognized)
            return

        lastRecognized = text
        println("Recognized text: $text")

        if (now - lastSentTime < COOLDOWN_MS)
            return

        // сначала проверяем целую фразу
        val fullId =
            text.replace(" ", "_")

        if (checkBlock(fullId)) {
            println("Block found: $fullId")

            lastSentTime = now
            onBlockRecognized(fullId)
            return
        }

        // потом отдельные слова
        val words =
            text.split(" ")

        for (word in words) {
            val cleanWord =
                word.replace(
                    Regex("[^a-z0-9_]"),
                    ""
                )

            if (cleanWord.length < 3)
                continue

            if (checkBlock(cleanWord)) {
                println("Block found: $cleanWord")

                lastSentTime = now
                onBlockRecognized(cleanWord)

                break
            }
        }
    }

    private fun checkBlock(name: String): Boolean {

        val id =
            Identifier.of(
                "minecraft",
                name
            )
        return Registries.BLOCK.containsId(id)
    }

    fun resetState() {
        if (!isInitialized || recognizer == null) return

        executor.submit {
            try {
                recognizer?.reset()
                lastRecognized = ""
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}