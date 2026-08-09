💥 Chunk Destroyer
<p align="center"> <b>Destroy thousands of blocks in seconds.</b><br> A powerful Fabric mod for Minecraft with command and voice control. </p> <p align="center"> <a href="https://modrinth.com/mod/chunk-destroyer-tenakt"> <img src="https://img.shields.io/modrinth/dt/chunk-destroyer-tenakt?logo=modrinth&label=Modrinth&color=00AF5C" alt="Modrinth Downloads"> </a> <a href="https://github.com/Tenakt/chunk-destroyer"> <img src="https://img.shields.io/github/stars/Tenakt/chunk-destroyer?style=flat&logo=github&label=Stars" alt="GitHub Stars"> </a> <img src="https://img.shields.io/badge/Minecraft-1.21.11-62B47A?logo=minecraft" alt="Minecraft Version"> <img src="https://img.shields.io/badge/Fabric-Mod-DBD9D5?logo=fabric" alt="Fabric"> <img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"> </p>
🧨 What is Chunk Destroyer?

Chunk Destroyer is a Fabric mod that lets you remove large amounts of a specific block type from the world with a single command — or simply by using your voice.

Need to clear a massive area of stone?
Want to remove sand from a desert?
Building a custom challenge or mini-game?

Just tell Chunk Destroyer what to remove.

/destroy stone


And let the destruction begin. 💥

✨ Features
Feature	Description
💥 Mass Block Removal	Remove thousands of blocks at once
🎯 Block Filtering	Destroy only the block type you specify
📐 Configurable Area	Control the horizontal radius and vertical range
⌨️ Simple Commands	Use /destroy <block> to start
🎙️ Voice Control	Destroy blocks by speaking their names
🧠 Offline Speech Recognition	Powered by Vosk
🔊 Plasmo Voice Support	Integrated voice communication
🧩 Modded Blocks	Supports blocks from other mods
⚡ Fast Execution	Designed for large-scale block operations
🎮 Usage
Command

The main command is:

/destroy <block>


For example:

/destroy stone

/destroy dirt

/destroy minecraft:deepslate


Block IDs are supported through Minecraft's block registry, with command suggestions helping you find the block you're looking for.

🎙️ Voice Control

Chunk Destroyer can also be controlled using your microphone.

With Plasmo Voice installed, you can simply say the name of a block instead of typing a command.

For example:

🎙️ "stone"

The voice pipeline looks like this:

🎙️ Your Voice
      │
      ▼
🔊 Plasmo Voice
      │
      ▼
🧠 Vosk Speech Recognition
      │
      ▼
🧱 Block Identification
      │
      ▼
📡 Client → Server
      │
      ▼
💥 Block Removal


Speech recognition runs locally using Vosk, so no external speech recognition service is required.

The recognizer can use a configurable list of allowed block names, making recognition more focused and reliable.

⚙️ Configuration

Chunk Destroyer provides configurable parameters for the destruction area.

Default values:

Horizontal Radius: 16 blocks
Height Up:         384 blocks
Height Down:       384 blocks


This allows you to control how large the affected area is without changing the code.

The configuration also contains the block names used by the voice recognition system:

allowedBlocks


You can customize this list to fit your modpack or server.

🗺️ How the Destruction Area Works

The affected area is centered around the player.

              Height Up
                 ↑
                 │
        ┌─────────────────┐
        │                 │
        │                 │
        │     PLAYER      │
        │        ●        │
        │                 │
        │                 │
        └─────────────────┘
                 │
                 ↓
             Height Down

        ←── Radius ──→


Only blocks matching the selected block type are removed.

Other blocks remain untouched.

⚡ Performance

Chunk Destroyer is designed for large-scale block operations, but large areas can naturally create a significant amount of server-side work.

For example, a 16 × 16 × 768 area contains up to 196,608 block positions to process.

Because of this:

Avoid unnecessarily large destruction areas.
Be careful when using the mod on public servers.
Always keep a backup of important worlds.
Consider using server optimization mods for large operations.

⚠️ Use with care. Chunk Destroyer can permanently modify large parts of your world.

🧩 Requirements
Required
Fabric Loader
Fabric API
Fabric Language Kotlin
owo-lib
Optional
Plasmo Voice
 — required for voice control
Minecraft

The current development version targets:

Minecraft 1.21.11

Legacy releases for older Minecraft versions may also be available on Modrinth.

🛠️ Development

Chunk Destroyer is built using Fabric Loom with both Java and Kotlin.

The project is structured around separate client and server functionality:

src/
├── client/
│   └── kotlin/
│       └── net/tenakt/client/
│           ├── ChunkDestroyerClient.kt
│           ├── PlasmoVoiceAddon.kt
│           ├── VoskManager.kt
│           └── ModelExtractor.kt
│
└── main/
    ├── java/
    │   └── net/tenakt/
    │       ├── ChunkDestroyer.java
    │       ├── MyConfigModel.java
    │       └── network/
    │
    └── kotlin/
        └── net/tenakt/server/
            └── VoiceServerHandler.kt

Tech Stack
☕ Java
🟣 Kotlin
🧵 Fabric
🎙️ Plasmo Voice
🧠 Vosk
⚙️ owo-lib
🚀 Roadmap
 Better voice recognition
 Improved support for modded blocks
 Visual destruction-area preview
 GUI configuration
 More area selection modes
 Additional destruction filters
 Further performance improvements
 More voice commands

Have an idea?

Open an issue and let us know!

🐛 Bug Reports & Feature Requests

Found a bug or have an idea?

Please use the GitHub Issues page.

When reporting a bug, include:

Minecraft version
Mod version
Fabric Loader version
Other installed mods
Crash log or relevant log output
Steps to reproduce the issue
📦 Download
Modrinth

Get the latest release from:

Download Chunk Destroyer on Modrinth →

GitHub

View the source code →

❤️ Credits

Created by Tenakt.

Built with:

Fabric
Fabric API
Fabric Language Kotlin
owo-lib
Plasmo Voice
Vosk
📜 License

Chunk Destroyer is licensed under the GNU General Public License v3.0 or later.

See LICENSE for the full license text.

<p align="center"> <b>💥 Destroy blocks. Build faster.</b> </p>