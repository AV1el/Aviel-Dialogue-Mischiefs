# Aviel's Dialogue Mod

A lightweight NeoForge dialogue API and library mod for Minecraft 1.21.1.

ADM gives modders and modpack creators a reusable foundation for RPG-style dialogue: custom dialogue NPCs, dialogue mappings for vanilla or modded entities, JSON-driven branching conversations, and a small Java API for deeper integration.

## What is it?

ADM is primarily a mod. It does not try to be a full quest/content mod by itself. Instead, it provides the dialogue layer that other mods, and modpacks can build on.

Use it when you want:

- NPCs with RPG-style dialogue windows
- Dialogue on vanilla or modded entities
- Branching JSON conversations
- Typewriter text with pauses, speed changes, formatting, and sounds
- Player flags and persistent choice tracking
- Item requirements, item taking, and item rewards
- Trade shops opened from dialogue choices
- NPC emotes (EmoteCraft-style JSON animations) triggered from dialogue text

## Supported Version

- Minecraft: `1.21.1`
- Loader: `NeoForge`
- Mod id: `adm`

## Global Config Layout

ADM reads its content from the global Minecraft config folder:

```text
config/adm-dialogues/
  dialogues/
  npc_templates/
  trades/
  emotes/
  lang/
  skins/
  sounds/
  entity_dialogues.json
```

## Datapack Content

Besides the config folder, dialogues, trades and emotes can ship inside datapacks or mod jars:

```text
data/<namespace>/adm_dialogues/dialogues/guard.json
data/<namespace>/adm_dialogues/trades/blacksmith_shop.json
data/<namespace>/adm_dialogues/emotes/wave.json
```

Reference them anywhere a file name is accepted using the namespaced id, e.g. `"mymod:guard"` in `entity_dialogues.json`, NPC templates, `"trade"` fields or `/adm_npc set dialogue`. Datapack content reloads with `/reload`.

## Entity Dialogue Example

`config/adm-dialogues/entity_dialogues.json`

```json
{
  "entities": {
    "minecraft:pig": "pig.json",
    "minecraft:villager": "villager.json"
  },
  "default": ""
}
```

`config/adm-dialogues/dialogues/pig.json`

```json
{
  "title": "Pig",
  "speaker": "Pig",
  "random_start": ["oink", "oink_twice", "blood"],
  "nodes": {
    "oink": {
      "text": "Oink.",
      "choices": [{ "text": "Close.", "close": true }]
    },
    "oink_twice": {
      "text": "Oink oink.",
      "choices": [{ "text": "Close.", "close": true }]
    },
    "blood": {
      "text": "&cBLOOD FOR THE BLOOD GOD",
      "choices": [{ "text": "Close.", "close": true }]
    }
  }
}
```

## Trade Example

Open a trade shop from a dialogue choice with `"trade"` (aliases: `"shop"`, `"open_trade"`):

```json
{
  "text": "Show me your wares.",
  "trade": "blacksmith_shop.json"
}
```

`config/adm-dialogues/trades/blacksmith_shop.json`

```json
{
  "title": "Blacksmith Shop",
  "subtitle": "Steel for coin.",
  "offers": [
    {
      "id": "iron_sword",
      "title": "Iron Sword",
      "description": "Freshly forged.",
      "cost": [{ "item": "minecraft:iron_ingot", "count": 3 }],
      "result": [{ "item": "minecraft:iron_sword", "count": 1 }]
    },
    {
      "title": "Secret Blade",
      "requires_flags": ["blacksmith_ore_done"],
      "cost": [{ "item": "minecraft:diamond", "count": 2 }],
      "result": [{ "item": "minecraft:diamond_sword", "count": 1 }],
      "commands": ["say {player} bought a blade x{amount}"]
    }
  ]
}
```

Offers support `requires_flags`, `missing_flags`, `requires_tags`, `missing_tags`, multi-item `cost`/`result`, `commands` with `{player}`/`{npc}`/`{amount}` placeholders, and a `style` block for screen colors.

## NPC Emotes

Put EmoteCraft-style JSON animations into `config/adm-dialogues/emotes/` and trigger them from dialogue text with inline tags:

```json
{
  "text": "Watch this! <anim:wave> Impressive, right? <pause:long><anim:dance:loop>"
}
```

- `<anim:file>`, `<animation:file>`, and `<emote:file>` are equivalent
- Append `:loop` (or `:repeat`) to keep the animation looping
- `<anim:stop>` stops the current emote
- Emotes also work via `/adm_npc emote play <file> [loop]`
- If `player-animation-lib` is installed, ADM uses it for exact playback; otherwise a built-in interpolation fallback is used

## Localization

Any dialogue or trade text can contain `{{translation.key}}` placeholders. They are resolved on the client against the player's game language. Put lang files into `config/adm-dialogues/lang/`:

`config/adm-dialogues/lang/en_us.json`

```json
{ "my.npc.greeting": "&eWelcome, traveler!" }
```

`config/adm-dialogues/lang/ru_ru.json`

```json
{ "my.npc.greeting": "&eДобро пожаловать, путник!" }
```

Then in a dialogue: `"text": "{{my.npc.greeting}}"`. The files are delivered to clients through the generated ADM resource pack (`/adm_npc assets reload` + F3+T after changes). Formatting codes and inline tags (`<pause>`, `<anim>`, ...) work inside translations.

## Config

`config/adm-common.toml`:

- `enableEntityDialogues` — dialogues on vanilla/modded entities (default `true`)
- `maxInteractDistance` — server-side range for choices, trades, emotes (default `8.0`)
- `commandPermissionLevel` — permission level for `/adm_npc` (default `2`)

## NPC Template Example

`config/adm-dialogues/npc_templates/guard.json`

```json
{
  "name": "Guard",
  "dialogue": "guard.json",
  "scale": 1.0,
  "name_visible": true,
  "invulnerable": true,
  "look_distance": 8.0,
  "model": "steve",
  "skin": "adm:textures/entity/npc/guard.png"
}
```

## Commands

```text
/adm_npc npc spawn <name>
/adm_npc npc list
/adm_npc npc remove_nearest

/adm_npc set dialogue <file>
/adm_npc set clear_dialogue
/adm_npc set invulnerable <true|false>
/adm_npc set look_distance <0-64>
/adm_npc set model <steve|slim>
/adm_npc set skin <namespace:textures/...png>
/adm_npc set clear_skin

/adm_npc template list
/adm_npc template apply <id>
/adm_npc template spawn <id>
/adm_npc template spawn_aviel

/adm_npc dialogue files
/adm_npc dialogue reload
/adm_npc validate

/adm_npc trade files
/adm_npc trade open <file>

/adm_npc emote files
/adm_npc emote play <file> [loop]
/adm_npc emote stop

/adm_npc entity config_path
```

## Java API

Other mods can register entity dialogues or open dialogue screens manually:

```java
import net.aviel.dialogue.api.AdmDialogueApi;

AdmDialogueApi.registerEntityDialogue(EntityType.VILLAGER, "villager.json");
AdmDialogueApi.setDefaultEntityDialogue("default_mob.json");
AdmDialogueApi.openDialogue(serverPlayer, entity, "custom.json");
AdmDialogueApi.openTrade(serverPlayer, entity, "shop.json");
AdmDialogueApi.playNpcEmote(npc, server, "wave.json", false);
AdmDialogueApi.stopNpcEmote(npc);
```

Dialogues can also be built in code and registered under a namespaced id, without a file on disk:

```java
import net.aviel.dialogue.api.DialogueBuilder;

AdmDialogueApi.registerDialogue("mymod:intro", DialogueBuilder.create()
        .title("Guide")
        .speaker("Guide")
        .node("start", node -> node
                .text("Welcome, traveler!")
                .choice(choice -> choice.text("Thanks!").setFlag("intro_done").close())));

AdmDialogueApi.openDialogue(serverPlayer, entity, "mymod:intro");
```

Two cancellable server-side events fire on `NeoForge.EVENT_BUS`:

- `DialogueOpenEvent` — before a dialogue opens; cancel it or swap the dialogue file
- `DialogueChoiceEvent` — after a choice passed validation, before its actions apply

Useful paths:

```java
AdmDialogueApi.globalDialogueDirectory();
AdmDialogueApi.globalNpcTemplateDirectory();
AdmDialogueApi.globalTradeDirectory();
AdmDialogueApi.globalEmoteDirectory();
AdmDialogueApi.globalEntityDialogueConfigPath();
```

## Documentation

More details are available in:

- [`docs/modder-storage-guide.md`](docs/modder-storage-guide.md)
- [`docs/schemas/`](docs/schemas/) — JSON Schemas for dialogues, trades, NPC templates and entity mappings; point your editor at them for autocomplete and validation
- [`docs/examples/`](docs/examples/) — ready-to-copy starter set: guard NPC template, quest dialogue with flags and item turn-in, and a flag-gated trade shop

## Project Status

ADM is in early development. The core dialogue runtime is usable, but JSON schemas and API details may still change before a stable release.
