# Aviel's Dialogue Mod

A lightweight NeoForge dialogue API and library mod for Minecraft 1.21.1.

ADM gives modders and modpack creators a reusable foundation for RPG-style dialogue: custom dialogue NPCs, JSON-driven branching conversations, and a small Java API for deeper integration.

## What is it?

ADM is primarily a mod. It does not try to be a full quest/content mod by itself. Instead, it provides the dialogue layer that other mods, and modpacks can build on.

Use it when you want:

- NPCs with RPG-style dialogue windows
- Branching JSON conversations
- Typewriter text with pauses, speed changes, formatting, and sounds
- Player flags and persistent choice tracking
- Item requirements, item taking, and item rewards
- Trade shops opened from dialogue choices
- NPC emotes (keyframe JSON animations) triggered from dialogue text

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
```

## Datapack Content

Besides the config folder, dialogues, trades and emotes can ship inside datapacks or mod jars:

```text
data/<namespace>/adm_dialogues/dialogues/guard.json
data/<namespace>/adm_dialogues/trades/blacksmith_shop.json
data/<namespace>/adm_dialogues/emotes/wave.json
```

Reference them anywhere a file name is accepted using the namespaced id, e.g. `"mymod:guard"` in NPC templates, `"trade"` fields or `/npc set dialogue`. Datapack content reloads with `/reload`.

## Dialogue Example

`config/adm-dialogues/dialogues/greeter.json`

```json
{
  "title": "Greeter",
  "speaker": "Greeter",
  "random_start": ["hello", "busy", "blood"],
  "nodes": {
    "hello": {
      "text": "Hello, traveler.",
      "choices": [{ "text": "Close.", "close": true }]
    },
    "busy": {
      "text": "Not now, I'm busy.",
      "choices": [{ "text": "Close.", "close": true }]
    },
    "blood": {
      "text": "&cBLOOD FOR THE BLOOD GOD",
      "choices": [{ "text": "Close.", "close": true }]
    }
  }
}
```

Spawn an NPC and assign it in game:

```text
/npc reload
/npc spawn Greeter
/npc set dialogue greeter.json
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

Put keyframe JSON animations into `config/adm-dialogues/emotes/` and trigger them from dialogue text with inline tags:

```json
{
  "text": "Watch this! <anim:wave> Impressive, right? <pause:long><anim:dance:loop>"
}
```

- `<anim:file>`, `<animation:file>`, and `<emote:file>` are equivalent
- Append `:loop` (or `:repeat`) to keep the animation looping
- `<anim:stop>` stops the current emote
- Emotes also work via `/npc emote <file> [loop]`
- ADM plays these files itself and requires no animation mod. If **PlayerAnimator** (`player-animation-lib`) is present it is used for exact playback and limb bending; otherwise a built-in interpolation fallback runs the same emote
- The format is plain keyframe JSON, so animations exported from EmoteCraft can be dropped in as-is

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

Then in a dialogue: `"text": "{{my.npc.greeting}}"`. The files are delivered to clients through the generated ADM resource pack (`/npc reload` + F3+T after changes). Formatting codes and inline tags (`<pause>`, `<anim>`, ...) work inside translations.

## Config

`config/adm-common.toml`:

- `maxInteractDistance` — server-side range for choices, trades, emotes (default `8.0`)
- `commandPermissionLevel` — permission level for `/npc` and the in-game editor (default `2`)

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

Everything is under one command, `/npc`, one level deep:

```text
/npc spawn <name> [template]
/npc select [name] | select clear
/npc info
/npc remove [name] | remove all <radius>

/npc set dialogue [file]        omit the argument to clear
/npc set skin [file-or-id]      omit the argument to clear
/npc set model <steve|alex>
/npc set scale <0.25-3>
/npc set look <0-64>
/npc set invulnerable <bool>
/npc set name <text>
/npc set template <id>          apply to the targeted NPC
/npc set here                   teleport it to you
/npc set save <id> [force]      save it as a template

/npc equip <slot> [item] [count] | from_me | clear <slot> | clear_all | list
/npc point list | marker [id] | here <id> | remove <id> | send <id> [speed] | stop

/npc emote <file> [loop] | emote stop
/npc trade <file>

/npc list [npcs|dialogues|trades|emotes|templates|points|folders]
/npc reload
/npc validate
```

See [`docs/dialogue-guide.md`](docs/dialogue-guide.md) for the full command reference.

## Java API

Other mods can open dialogue or trade screens and drive NPCs manually:

```java
import net.aviel.dialogue.api.AdmDialogueApi;

AdmDialogueApi.openDialogue(serverPlayer, entity, "custom.json");
AdmDialogueApi.openTrade(serverPlayer, entity, "shop.json");
AdmDialogueApi.playNpcEmote(npc, server, "wave.json", false);
AdmDialogueApi.stopNpcEmote(npc);
AdmDialogueApi.applyNpcTemplate(npc, "guard", server);
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
```

## Documentation

More details are available in:

- [`docs/dialogue-guide.md`](docs/dialogue-guide.md) — the full guide, from a first dialogue to the Java API
- [`docs/schemas/`](docs/schemas/) — JSON Schemas for dialogues, trades and NPC templates; point your editor at them for autocomplete and validation
- [`docs/examples/`](docs/examples/) — ready-to-copy starter set: guard NPC template, quest dialogue with flags and item turn-in, and a flag-gated trade shop

## Project Status

ADM is in early development. The core dialogue runtime is usable, but JSON schemas and API details may still change before a stable release.
