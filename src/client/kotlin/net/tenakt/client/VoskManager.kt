package net.tenakt.client

import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.Language
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.Executors

object VoskManager {

    private var recognizer: Recognizer? = null
    private var currentModel: Model? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var isInitialized = false
    private var lastSentTime = 0L
    private const val COOLDOWN_MS = 1500L
    private var lastRecognized = ""

    private var isRussian = false
    private val ruToEnMap = mutableMapOf<String, String>()

    private var lastKnownLang = "uninitialized"

    fun init() {
        println("Initializing VoskManager...")
        System.setProperty("jna.encoding", "UTF8")
    }

    @JvmStatic
    fun reloadRecognizer() {
        val client = MinecraftClient.getInstance()
        val currentLang = client?.options?.language ?: "en_us"
        lastKnownLang = currentLang
        isRussian = currentLang.contains("ru")

        executor.submit {
            try {
                val modelName = if (isRussian) "ru" else "en-us"
                val modelDir = ModelExtractor.extractModel(modelName)

                if (!modelDir.exists()) {
                    println("[ChunkDestroyer] Vosk model $modelName not found!")
                    return@submit
                }

                recognizer?.close()
                currentModel?.close()

                val model = Model(modelDir.absolutePath)
                currentModel = model

                val grammar = generateGrammarFromConfig()

                println("[ChunkDestroyer] Loaded grammar for $modelName:")
                println(grammar)

                recognizer = Recognizer(model, 48000f, grammar)
                isInitialized = true

                println("[ChunkDestroyer] Vosk model $modelName loaded successfully!")

            } catch (e: Exception) {
                println("[ChunkDestroyer] ERROR initializing Vosk:")
                e.printStackTrace()
            }
        }
    }

    private fun generateGrammarFromConfig(): String {
        val configBlocks = net.tenakt.MyModInitializer.CONFIG.allowedBlocks()
        val grammarList = mutableListOf<String>()
        ruToEnMap.clear()

        for (blockId in configBlocks) {
            val safeBlockId = blockId.replace(" ", "_")

            val identifier = Identifier.tryParse(
                if (safeBlockId.contains(":")) safeBlockId else "minecraft:$safeBlockId"
            )

            if (identifier != null && Registries.BLOCK.containsId(identifier)) {
                val block = Registries.BLOCK.get(identifier)
                val cleanBlockId = safeBlockId
                    .replace("minecraft:", "")
                    .replace('_', ' ')

                if (isRussian) {
                    val rawRuName = Language.getInstance()
                        .get(block.translationKey)
                        .lowercase()

                    val cleanRuName = rawRuName
                        .replace("ё", "е")
                        .replace("-", " ")
                        .replace(Regex("[^а-яa-z0-9 ]"), "")
                        .trim()

                    val ruName = cleanRuName.replace(Regex("\\s+"), " ")

                    ruToEnMap[ruName] = cleanBlockId

                    if (cleanBlockId == "grass block") {
                        ruToEnMap["блок травы"] = cleanBlockId

                        if (!grammarList.contains("блок")) grammarList.add("блок")
                        if (!grammarList.contains("травы")) grammarList.add("травы")
                    }

                    if (cleanBlockId == "netherrack") {
                        ruToEnMap["адский камень"] = cleanBlockId

                        if (!grammarList.contains("адский")) grammarList.add("адский")
                        if (!grammarList.contains("камень")) grammarList.add("камень")
                    }

                    val words = ruName.split(" ")
                    for (w in words) {
                        if (w.length < 2) continue

                        if (!grammarList.contains(w)) {
                            grammarList.add(w)
                        }
                    }
                } else {
                    val cleanEnName = cleanBlockId
                        .replace("-", " ")
                        .replace(Regex("[^a-z0-9 ]"), "")
                        .trim()
                        .replace(Regex("\\s+"), " ")

                    val words = cleanEnName.split(" ")

                    for (w in words) {
                        if (w.length < 2) continue

                        if (!grammarList.contains(w)) {
                            grammarList.add(w)
                        }
                    }
                }
            }
        }

        val levWord = net.tenakt.MyModInitializer.CONFIG
            .levitationWord()
            .lowercase()
            .trim()

        if (levWord.isNotEmpty()) {
            val levWords = levWord.split(" ")

            for (w in levWords) {
                if (w.length >= 2 && !grammarList.contains(w)) {
                    grammarList.add(w)
                }
            }
        }

        if (!grammarList.contains("[unk]")) {
            grammarList.add("[unk]")
        }

        return grammarList.joinToString(
            separator = "\", \"",
            prefix = "[\"",
            postfix = "\"]"
        )
    }

    fun processAudio(pcmData: ShortArray, onBlockRecognized: (String) -> Unit) {
        val client = MinecraftClient.getInstance()
        val currentLang = client?.options?.language ?: "en_us"

        if (currentLang != lastKnownLang) {
            println("[ChunkDestroyer] Language changed to $currentLang, reloading Vosk...")
            lastKnownLang = currentLang
            reloadRecognizer()
            return
        }

        if (!isInitialized || recognizer == null) return

        executor.submit {
            try {
                val rec = recognizer ?: return@submit
                val isFinal = rec.acceptWaveForm(pcmData, pcmData.size)
                val jsonResult = if (isFinal) rec.result else rec.partialResult

                if (parseAndTrigger(jsonResult, onBlockRecognized)) {
                    rec.reset()
                    lastRecognized = ""
                    println("[ChunkDestroyer] Recognizer reset after command")
                }
            } catch (e: Exception) {
                println("[ChunkDestroyer] Error processing audio:")
                e.printStackTrace()
            }
        }
    }

    private fun parseAndTrigger(json: String, onBlockRecognized: (String) -> Unit): Boolean {
        val now = System.currentTimeMillis()
        val textMatch = "\"text\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)
            ?: "\"partial\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)

        val text = textMatch?.groupValues?.get(1)?.trim()?.lowercase() ?: return false

        if (text.isEmpty() || text == lastRecognized) return false

        lastRecognized = text
        println("Recognized raw text: $text")

        if (now - lastSentTime < COOLDOWN_MS) return false

        var commandTriggered = false

        val levWord = net.tenakt.MyModInitializer.CONFIG
            .levitationWord()
            .lowercase()
            .trim()

        if (net.tenakt.MyModInitializer.CONFIG.enableLevitation() && levWord.isNotEmpty()) {
            if (text == levWord || text.contains(levWord)) {
                println("Levitation word detected: $text")
                lastSentTime = now

                val height = net.tenakt.MyModInitializer.CONFIG.levitationHeight()

                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    net.tenakt.network.VoiceLevitationPayload(height)
                )

                commandTriggered = true
            }
        }

        val translatedFullText = if (isRussian) ruToEnMap[text] ?: text else text
        val fullId = translatedFullText.replace(" ", "_")

        if (checkBlock(fullId)) {
            println("Block found (Full phrase): $fullId")
            lastSentTime = now
            onBlockRecognized(fullId)
            return true
        }

        if (isRussian) {
            for ((ruName, enName) in ruToEnMap) {
                if (ruName.contains(" ") && text.contains(ruName)) {
                    val phraseId = enName.replace(" ", "_")

                    if (checkBlock(phraseId)) {
                        println("Block found (Phrase in text): $phraseId")
                        lastSentTime = now
                        onBlockRecognized(phraseId)
                        return true
                    }
                }
            }
        }

        val words = text.split(" ")

        for (word in words) {
            val translatedWord = if (isRussian) ruToEnMap[word] ?: word else word

            val withUnderscores = translatedWord.replace(" ", "_")
            val cleanWord = withUnderscores.replace(Regex("[^a-zа-яё0-9_]"), "")

            if (cleanWord.length < 3) continue

            if (checkBlock(cleanWord)) {
                println("Block found (Word): $cleanWord")
                lastSentTime = now
                onBlockRecognized(cleanWord)
                commandTriggered = true
                break
            }
        }

        return commandTriggered
    }

    private fun checkBlock(name: String): Boolean {
        val id = Identifier.of("minecraft", name)
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