# ADM Modder Storage And Dialogue Guide

This guide explains how to use Aviel's Dialogue Mod as a library mod for modpacks and NeoForge mods.

ADM is not meant to be a content-heavy quest mod by itself. It is a dialogue foundation. You provide JSON files or register dialogue mappings from Java, and ADM handles NPCs, entity interactions, the dialogue GUI, choices, player state, item checks, sounds, text effects, and persistence.

## Table Of Contents

- [What ADM Stores](#what-adm-stores)
- [Folder Structure](#folder-structure)
- [Datapack Content](#datapack-content)
- [Dialogue Files](#dialogue-files)
- [Dialogue Nodes](#dialogue-nodes)
- [Choices](#choices)
- [Text Formatting](#text-formatting)
- [Typewriter Tags](#typewriter-tags)
- [Sound Support](#sound-support)
- [Style And Colors](#style-and-colors)
- [Player Flags And Choice Memory](#player-flags-and-choice-memory)
- [Item Requirements And Rewards](#item-requirements-and-rewards)
- [Commands In Choices](#commands-in-choices)
- [Trades](#trades)
- [Emotes](#emotes)
- [NPC Templates](#npc-templates)
- [Entity Dialogue Mapping](#entity-dialogue-mapping)
- [Validation](#validation)
- [Commands](#commands)
- [Java API](#java-api)
- [JSON Schemas](#json-schemas)
- [Complete Examples](#complete-examples)
- [Troubleshooting](#troubleshooting)
- [Recommended Workflow](#recommended-workflow)

## What ADM Stores

ADM reads global config files from:

```text
config/adm-dialogues
```

ADM stores player dialogue progress in world saved data. This means player flags and remembered choices are still world-specific, while the dialogue definitions themselves are global.

## Folder Structure

Use this layout:

```text
config/
  adm-dialogues/
    dialogues/
      blacksmith.json
      pig.json
      guard.json
    trades/
      blacksmith_shop.json
    emotes/
      wave.json
    npc_templates/
      guard.json
      Aviel__.json
    skins/
      guard.png
    sounds/
      guard_voice.ogg
    lang/
      en_us.json
    entity_dialogues.json
```

### `dialogues`

Put all dialogue JSON files here.

```text
config/adm-dialogues/dialogues/pig.json
```

### `npc_templates`

Put reusable ADM NPC templates here.

```text
config/adm-dialogues/npc_templates/guard.json
```

### `skins`

Put local NPC skin PNG files here if you do not want to create a separate resource pack.

```text
config/adm-dialogues/skins/guard.png
```

Then use:

```json
{
  "skin": "guard.png"
}
```

ADM turns that into:

```text
adm:textures/entity/npc/guard.png
```

File names should be lowercase and use only letters, numbers, `_`, `-`, `.`, and folders.

### `sounds`

Put local dialogue OGG files here.

```text
config/adm-dialogues/sounds/guard_voice.ogg
```

Then use:

```json
{
  "sound": {
    "mode": "full",
    "full": "guard_voice"
  }
}
```

ADM turns that into:

```text
adm:dialogue/guard_voice
```

After adding or replacing local skins or sounds while the game is running, run `/adm_npc assets reload`, then reload client resources with `F3+T` or restart the client.

### `trades`

Put trade shop JSON files here. Choices open them with the `trade` field.

```text
config/adm-dialogues/trades/blacksmith_shop.json
```

### `emotes`

Put EmoteCraft-format emote JSON files here. Dialogue text plays them with `<anim:...>` tags, and `/adm_npc emote play` uses them directly.

```text
config/adm-dialogues/emotes/wave.json
```

### `entity_dialogues.json`

This file maps vanilla or modded entity types to dialogue files.

```text
config/adm-dialogues/entity_dialogues.json
```

## Datapack Content

Dialogues, trades and emotes can also ship inside datapacks or mod jars instead of the config folder:

```text
data/<namespace>/adm_dialogues/dialogues/guard.json
data/<namespace>/adm_dialogues/trades/blacksmith_shop.json
data/<namespace>/adm_dialogues/emotes/wave.json
```

Reference them anywhere a file name is accepted, using the namespaced id without the `.json` suffix:

```json
{
  "entities": {
    "minecraft:villager": "mymod:villager"
  }
}
```

The same ids work in NPC templates (`"dialogue": "mymod:guard"`), choice `trade` fields, `<anim:mymod:wave>` tags, `/adm_npc set dialogue` and the Java API. Datapack content reloads with the vanilla `/reload` command; config-folder files with the same plain name are unrelated and never conflict, because plain names and `ns:id` references are separate namespaces.

Subfolders become part of the id: `data/mymod/adm_dialogues/dialogues/npcs/guard.json` is referenced as `mymod:npcs/guard`.

Skins, sounds and lang files are client resources, so they cannot ship in a datapack. Put them in a mod jar under `assets/<namespace>/` or in a resource pack, or use the config folders.

## Dialogue Files

A dialogue file has a root object and a `nodes` object.

Minimal example:

```json
{
  "title": "Pig",
  "speaker": "Pig",
  "start": "start",
  "nodes": {
    "start": {
      "text": "Oink.",
      "choices": [
        {
          "text": "Close.",
          "close": true
        }
      ]
    }
  }
}
```

Place it at:

```text
config/adm-dialogues/dialogues/pig.json
```

### Root Fields

| Field | Type | Description |
| --- | --- | --- |
| `title` | string | Name of the dialogue. Used by the GUI if needed. |
| `speaker` | string | Default speaker name for all nodes. |
| `start` | string or array | First node to open. |
| `random_start` | string or array | Same as `start`, but intended for random openings. |
| `text_speed` | number | Default typewriter speed. |
| `text_color` | color string | Default text color. |
| `speaker_color` | color string | Default speaker color. |
| `sound` | object | Default sound settings. |
| `style` | object | Default GUI colors. |
| `nodes` | object or array | Dialogue nodes. |

ADM accepts `start`, `starts`, and `random_start`. If multiple valid start nodes are provided, ADM randomly picks one when the dialogue opens.

Example:

```json
{
  "random_start": ["hello", "busy", "rare_line"]
}
```

## Dialogue Nodes

Each node is one dialogue screen state: text plus choices.

```json
{
  "nodes": {
    "start": {
      "speaker": "Blacksmith",
      "text_speed": 2,
      "text": [
        "The forge is hot.",
        "Steel remembers every hand that shaped it."
      ],
      "choices": []
    }
  }
}
```

### Node Fields

| Field | Type | Description |
| --- | --- | --- |
| `speaker` | string | Overrides root speaker for this node. |
| `text` | string or array | Dialogue text. Arrays are joined as separate lines. |
| `text_speed` or `speed` | number | Typewriter speed for this node. |
| `text_color` | color string | Text color for this node. |
| `speaker_color` | color string | Speaker color for this node. |
| `sound` | object | Sound settings for this node. |
| `choices` | array | Player choices shown on the right side of the GUI. |

## Choices

Choices are buttons. They can move to another node, close the dialogue, run actions, check conditions, and give or take items.

Basic choice:

```json
{
  "text": "What can you craft?",
  "next": "craft"
}
```

Close choice:

```json
{
  "text": "Goodbye.",
  "close": true
}
```

Back choice:

```json
{
  "text": "Back.",
  "action": "back"
}
```

### Choice Fields

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | Optional stable id used for remembered choices. |
| `text` or `label` | string | Button text. |
| `next` | string | Node id to open after clicking. |
| `close` | boolean | Close the GUI after clicking. |
| `action` | string | Special action. `back`, `close`, and `exit` are supported. |
| `trade` / `shop` / `open_trade` | string | Trade file or id to open after the choice actions run. |
| `commands` / `command` | array/string | Server commands to execute. |
| `set_flags` / `set_flag` | array/string | Add player flags. |
| `add_flags` / `add_flag` | array/string | Add player flags. |
| `clear_flags` / `clear_flag` | array/string | Remove player flags. |
| `remove_flags` / `remove_flag` | array/string | Remove player flags. |
| `requires_flags` / `requires_flag` | array/string | Only show if player has flags. |
| `missing_flags` / `missing_flag` | array/string | Only show if player does not have flags. |
| `requires_tags` / `requires_tag` | array/string | Requires vanilla scoreboard tags on player. |
| `missing_tags` / `missing_tag` | array/string | Hidden if player has these tags. |
| `requires_choices` / `requires_choice` | array/string | Requires remembered choices. |
| `requires_items` | array/object/string | Requires items in player inventory. |
| `take_items` | array/object/string | Takes items after clicking. |
| `give_items` | array/object/string | Gives items after clicking. |

### Choosing Options

Players can click a choice, press the number shown on its button, or walk the list with the arrow keys and confirm with Enter or Space. When a node has more choices than fit on screen, the list scrolls (mouse wheel or arrow keys) and chevrons on the right edge indicate more options above or below. Any key press while text is still printing completes the typewriter first instead of selecting.

## Text Formatting

ADM supports Minecraft-style formatting codes using `&`.

Examples:

```json
{
  "text": "&eGolden text&r and normal text."
}
```

Common codes:

| Code | Meaning |
| --- | --- |
| `&0` to `&f` | Colors |
| `&l` | Bold |
| `&o` | Italic |
| `&n` | Underline |
| `&m` | Strikethrough |
| `&k` | Obfuscated |
| `&r` | Reset |

You can combine formatting with typewriter tags.

```json
{
  "text": "&cYou <pause:short>should not have come here.&r"
}
```

## Typewriter Tags

ADM removes these tags from visible text and uses them to control the typewriter effect.

### Pause Tags

```text
<pause:short>
<pause:medium>
<pause:long>
<pause:20>
<pause:0.5s>
```

Aliases:

```text
<wait:short>
<p:short>
<p=short>
```

Examples:

```json
{
  "text": "You <pause:short>will regret <pause:0.5s>this."
}
```

### Speed Tags

```text
<slow>
<normal>
<fast>
<instant>
<speed:1>
<speed:4>
<speed:normal>
```

Aliases:

```text
<spd:1>
<s:1>
<s=1>
</speed>
```

Example:

```json
{
  "text": "I will speak <slow>very slowly<normal>, then <fast>quickly."
}
```

### Inline Sound Tags

```text
<sound:minecraft:entity.pig.ambient>
```

Example:

```json
{
  "text": "Listen closely. <sound:minecraft:block.amethyst_block.chime>There it is."
}
```

## Sound Support

ADM supports two dialogue sound modes:

- `repeating`: plays a short sound while letters appear.
- `full`: plays one full sound when the node opens.
- `none`: disables sound.

Root-level sound:

```json
{
  "sound": {
    "mode": "repeating",
    "text": "minecraft:ui.button.click",
    "volume": 0.25,
    "pitch": 1.2,
    "letter_interval": 2
  }
}
```

Local config sound:

```json
{
  "sound": {
    "mode": "repeating",
    "text": "type_click",
    "volume": 0.25,
    "pitch": 1.1,
    "letter_interval": 1
  }
}
```

This expects:

```text
config/adm-dialogues/sounds/type_click.ogg
```

Node-level full voice:

```json
{
  "nodes": {
    "warning": {
      "sound": {
        "mode": "full",
        "full": "minecraft:entity.warden.angry",
        "volume": 0.8,
        "pitch": 0.9
      },
      "text": "&cLeave."
    }
  }
}
```

### Sound Fields

| Field | Type | Description |
| --- | --- | --- |
| `mode` | string | `repeating`, `full`, or `none`. |
| `text` / `text_sound` | string | Sound for repeating text. |
| `full` / `full_sound` / `voice` | string | Full node sound. |
| `volume` | number | Sound volume, clamped by ADM. |
| `pitch` | number | Sound pitch, clamped by ADM. |
| `letter_interval` | number | For repeating mode, how often letters play sound. |

## Style And Colors

ADM uses dark amethyst-like defaults. You can override them per dialogue.

```json
{
  "style": {
    "outer_color": "#2E08040F",
    "text_background": "#A20A0613",
    "choices_background": "#A1140B24",
    "divider_color": "#6BB89CFF",
    "button_color": "#8424143E",
    "button_hover_color": "#B0341D5B",
    "button_disabled_color": "#55222222",
    "button_text_color": "#E9DCFF",
    "button_disabled_text_color": "#8F849C"
  }
}
```

Colors accept:

```text
#RRGGBB
#AARRGGBB
0xRRGGBB
0xAARRGGBB
```

Use alpha values if you want transparent panels.

## Player Flags And Choice Memory

ADM stores flags per player in world saved data.

Set a flag:

```json
{
  "text": "I accept the job.",
  "set_flags": ["blacksmith_ore_order"],
  "next": "accepted"
}
```

Require a flag:

```json
{
  "text": "I brought the ore.",
  "requires_flags": ["blacksmith_ore_order"],
  "next": "turn_in"
}
```

Hide a choice if a flag exists:

```json
{
  "text": "Do you have work?",
  "missing_flags": ["blacksmith_ore_order"],
  "next": "job_offer"
}
```

Clear a flag:

```json
{
  "text": "Here is the ore.",
  "clear_flags": ["blacksmith_ore_order"],
  "set_flags": ["blacksmith_ore_done"],
  "close": true
}
```

### Remembered Choices

If a choice has an `id`, ADM remembers that the player clicked it.

```json
{
  "id": "asked_about_forge",
  "text": "Tell me about the forge.",
  "next": "forge_lore"
}
```

Require that choice later:

```json
{
  "text": "About what you said earlier...",
  "requires_choices": ["asked_about_forge"],
  "next": "follow_up"
}
```

## Item Requirements And Rewards

ADM can require, take, and give items.

### Item Rule Forms

Simple string means count `1`:

```json
"minecraft:raw_iron"
```

Object form:

```json
{
  "item": "minecraft:raw_iron",
  "count": 3
}
```

Array form:

```json
[
  { "item": "minecraft:raw_iron", "count": 3 },
  { "item": "minecraft:coal", "count": 1 }
]
```

### Require Items

Choice only appears if the player has the items:

```json
{
  "text": "I have the materials.",
  "requires_items": [
    { "item": "minecraft:raw_iron", "count": 3 }
  ],
  "next": "materials_ready"
}
```

### Take Items

Items are removed when clicked:

```json
{
  "text": "Take the ore.",
  "take_items": [
    { "item": "minecraft:raw_iron", "count": 3 }
  ],
  "next": "paid"
}
```

### Give Items

Items are added to the player inventory, or dropped if inventory is full:

```json
{
  "text": "Thanks.",
  "give_items": [
    { "item": "minecraft:iron_ingot", "count": 1 }
  ],
  "close": true
}
```

## Commands In Choices

ADM can run server commands when a choice is clicked.

```json
{
  "text": "Bless me.",
  "commands": [
    "effect give {player} minecraft:regeneration 10 1 true",
    "say {player} received a blessing from {npc}"
  ],
  "close": true
}
```

Supported placeholders:

| Placeholder | Meaning |
| --- | --- |
| `{player}` / `%player%` | Player name |
| `{player_uuid}` / `%player_uuid%` | Player UUID |
| `{npc}` / `%npc%` | NPC or entity display name |
| `{entity}` / `%entity%` | Same as NPC/entity display name |
| `{npc_uuid}` / `%npc_uuid%` | Target entity UUID |
| `{entity_uuid}` / `%entity_uuid%` | Target entity UUID |

## Trades

A trade shop is a JSON file with offers. Open it from a dialogue choice:

```json
{
  "text": "Show me your wares.",
  "trade": "blacksmith_shop.json"
}
```

`config/adm-dialogues/trades/blacksmith_shop.json`:

```json
{
  "title": "Blacksmith Shop",
  "subtitle": "Steel for coin.",
  "offers": [
    {
      "id": "iron_sword",
      "title": "Iron Sword",
      "cost": [{ "item": "minecraft:iron_ingot", "count": 3 }],
      "result": [{ "item": "minecraft:iron_sword", "count": 1 }]
    },
    {
      "title": "Secret Blade",
      "requires_flags": ["blacksmith_ore_done"],
      "cost": [{ "item": "minecraft:diamond", "count": 2 }],
      "result": [{ "item": "minecraft:diamond_sword" }]
    }
  ]
}
```

### Offer Fields

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | Stable offer id. Defaults to `offer_<index>`. |
| `title` / `name` | string | Offer name shown in the shop. |
| `description` / `lore` | string | Tooltip text. |
| `category` | string | Optional grouping label. |
| `cost` (`price`, `buy`, `take`, `take_items`) | items | Items taken from the player. |
| `result` (`sell`, `give`, `give_items`, `reward`) | items | Items given to the player. |
| `commands` | array | Server commands; placeholders include `{amount}`. |
| `requires_flags` / `missing_flags` | array | Flag conditions, like dialogue choices. |
| `requires_tags` / `missing_tags` | array | Scoreboard tag conditions. |

Item entries use the same forms as dialogue item rules. The shop screen lets the player buy up to 64 at once; costs and results scale with the amount.

## Emotes

ADM plays EmoteCraft-format JSON animations on its NPCs. Put files in `config/adm-dialogues/emotes/` (or a datapack) and trigger them from dialogue text:

```json
{
  "text": "Hello there! <anim:wave>Nice to meet you."
}
```

Tag forms:

```text
<anim:wave>
<animation:wave>
<emote:wave>
<anim:wave:loop>
<anim:stop>
```

`<anim:stop>` (also `clear`, `idle`, `none`) stops the current emote. `:loop` / `:repeat` keeps the animation running. Emotes are synced to all nearby players automatically; the server validates the file before playing, and only players with the dialogue open can trigger animations.

## NPC Templates

NPC templates are reusable JSON files for ADM's own dialogue NPC entity.

Put templates in:

```text
config/adm-dialogues/npc_templates
```

Example:

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

### Template Fields

| Field | Type | Description |
| --- | --- | --- |
| `name` | string | NPC name shown above the entity. |
| `dialogue` / `dialogue_file` | string | Dialogue file from `dialogues`. |
| `scale` | number | Visual scale. Default is `1.0`. |
| `name_visible` | boolean | Whether the nameplate is visible. |
| `invulnerable` | boolean | Whether the NPC can be damaged. |
| `look_distance` | number | Distance at which the NPC looks at nearby players. Use `0` to disable. |
| `model` / `player_model` | string | `steve` or `slim`. |
| `skin` | string | Resource location for a custom texture. |
| `equipment` | object | Armor and held items; keys `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet` (aliases `weapon`, `helmet`, `boots`... work). Values are an item id or `{ "item": ..., "count": ... }`. |

Equipment example:

```json
{
  "equipment": {
    "mainhand": "minecraft:iron_sword",
    "offhand": "minecraft:shield",
    "chest": { "item": "minecraft:iron_chestplate" }
  }
}
```

NPC gear is decorative: it renders on the NPC but never drops and cannot be picked up.

### Custom Skins

The `skin` field must be a resource location:

```json
{
  "skin": "yourmod:textures/entity/npc/guard.png"
}
```

Or it can be a local file from `config/adm-dialogues/skins`:

```json
{
  "skin": "guard.png"
}
```

The texture must exist in a mod or resource pack:

```text
assets/yourmod/textures/entity/npc/guard.png
```

## Entity Dialogue Mapping

ADM can attach dialogues to any vanilla or modded entity type.

Create:

```text
config/adm-dialogues/entity_dialogues.json
```

Example:

```json
{
  "entities": {
    "minecraft:pig": "pig.json",
    "minecraft:villager": "villager.json",
    "yourmod:custom_npc": "custom_npc.json"
  },
  "default": ""
}
```

When a player right-clicks a mapped entity, ADM opens the configured dialogue.

If `default` is set, every unmapped entity can use that dialogue:

```json
{
  "entities": {},
  "default": "default_entity.json"
}
```

Be careful with `default`, because it can make every interactable entity open a dialogue.

## Validation

`/adm_npc validate` checks every dialogue, trade, NPC template and the entity mapping file in one pass:

- JSON parse errors are reported as `[ERROR]` — the file will not work at all.
- Broken references are reported as `[WARN]`: a choice `next` pointing to a missing node, a `trade` field pointing to a missing shop, an `<anim:...>` tag for an unknown emote, unknown item ids, and mappings for unknown entity types.

`/adm_npc dialogue reload` clears the definition caches, regenerates config assets and then runs the same validation. Dialogue and trade files are also re-read automatically when their modification time changes, so a plain save-and-reopen usually works without any command.

## Commands

ADM commands use this root:

```text
/adm_npc
```

### The NPC Editor (GUI)

Shift+right-click any ADM NPC while you have the `/adm_npc` permission level (server operator, or cheats in singleplayer) to open the visual editor: live 3D preview, name field, dialogue/skin/template pickers, scale and look-distance sliders, model and invulnerability toggles, equipment copy/clear, and NPC removal with a confirm step. Regular players still get the dialogue on right-click — the editor never opens for them.

### Targeting

Edit commands act on one NPC resolved in this order: your explicit selection, then the NPC under your crosshair (up to 24 blocks), then the nearest within 8 blocks.

```text
/adm_npc select
/adm_npc select <name>
/adm_npc deselect
/adm_npc info
```

`select` grabs the NPC you are looking at (or the nearest); with a name it searches within 64 blocks. Spawning an NPC selects it automatically. `info` prints every setting of the targeted NPC.

### NPC Commands

```text
/adm_npc npc spawn <name>
/adm_npc npc list
/adm_npc npc remove
/adm_npc npc remove_all <radius>
/adm_npc npc rename <name>
/adm_npc npc tp_here
/adm_npc npc save_template <id> [force]
```

`spawn` creates a basic ADM NPC at your position and selects it.

`remove` removes the targeted NPC; `remove_all` clears every ADM NPC within the radius and reports the count.

`rename` renames the targeted NPC (`&` color codes supported). `tp_here` teleports it to you.

`save_template` writes the targeted NPC — including equipment — into `npc_templates/<id>.json`; it refuses to overwrite unless you add `force`.

### Equipment Commands

```text
/adm_npc equip <slot>
/adm_npc equip <slot> <item> [count]
/adm_npc equip from_me
/adm_npc equip clear <slot>
/adm_npc equip clear_all
/adm_npc equip list
```

Slots: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`. Without an item argument the NPC receives a copy of whatever you are holding. `from_me` copies your full armor and both hands.

### Set Commands

These commands edit the nearest ADM NPC within 8 blocks:

```text
/adm_npc set dialogue <file-or-id>
/adm_npc set clear_dialogue
/adm_npc set invulnerable <true|false>
/adm_npc set look_distance <0-64>
/adm_npc set scale <0.25-3>
/adm_npc set model <steve|slim>
/adm_npc set skin <png-from-config-skins-or-resource-location>
/adm_npc set clear_skin
```

`set dialogue` accepts config file names (`guard.json`) and datapack/API ids (`mymod:guard`). `set skin` accepts both `guard.png` from `config/adm-dialogues/skins` and full resource locations.

`scale` uses player size as its base: `1.0` is normal player height, `0.5` is half height, and `2.0` is double height. The NPC hitbox scales with the rendered model.

### Template Commands

```text
/adm_npc template list
/adm_npc template apply <id>
/adm_npc template spawn <id>
/adm_npc template spawn_aviel
```

`template spawn <id>` creates an ADM NPC from a template file.

`template apply <id>` applies a template to the nearest ADM NPC.

`template spawn_aviel` uses the built-in `Aviel__` template.

### Dialogue Commands

```text
/adm_npc dialogue files
/adm_npc dialogue reload
/adm_npc validate
```

`files` lists available dialogue files and ids, including datapack and API-registered dialogues.

`reload` clears caches, regenerates config assets and runs the full validation.

`validate` checks all dialogues, trades, templates and entity mappings without touching the caches. See [Validation](#validation).

### Emote Commands

```text
/adm_npc emote files
/adm_npc emote play <file> [loop]
/adm_npc emote stop
```

`play` runs an emote on the nearest ADM NPC and syncs it to all players. `stop` stops it.

### Trade Commands

```text
/adm_npc trade files
/adm_npc trade open <file>
```

`open` opens a trade screen with the nearest ADM NPC as the merchant.

### Entity Commands

```text
/adm_npc entity config_path
```

Prints the global path to `entity_dialogues.json`.

### Asset Commands

```text
/adm_npc assets path
/adm_npc assets reload
```

`path` prints the local skin folder, local sound folder, and generated resource pack path.

`reload` regenerates the config resource pack from `config/adm-dialogues/skins` and `config/adm-dialogues/sounds`. After running it, reload client resources with `F3+T` or restart the client.

## Java API

Other mods can use ADM without writing config files manually.

```java
import net.aviel.dialogue.api.AdmDialogueApi;
import net.minecraft.world.entity.EntityType;

AdmDialogueApi.registerEntityDialogue(EntityType.VILLAGER, "villager.json");
AdmDialogueApi.setDefaultEntityDialogue("default_entity.json");
```

Open a dialogue manually:

```java
AdmDialogueApi.openDialogue(serverPlayer, entity, "custom.json");
```

Get storage paths:

```java
Path root = AdmDialogueApi.globalRootDirectory();
Path dialogues = AdmDialogueApi.globalDialogueDirectory();
Path templates = AdmDialogueApi.globalNpcTemplateDirectory();
Path entityConfig = AdmDialogueApi.globalEntityDialogueConfigPath();
```

Apply an NPC template from Java:

```java
AdmDialogueApi.applyNpcTemplate(dialogueNpc, "guard", server);
```

### Building Dialogues In Code

`DialogueBuilder` produces the same structure as a dialogue file. Register the result under a namespaced id and open it like any other dialogue — no file on disk needed:

```java
import net.aviel.dialogue.api.DialogueBuilder;

AdmDialogueApi.registerDialogue("mymod:intro", DialogueBuilder.create()
        .title("Guide")
        .speaker("Guide")
        .node("start", node -> node
                .text("Welcome, traveler!")
                .choice(choice -> choice.text("Tell me more.").next("more"))
                .choice(choice -> choice.text("Thanks!").setFlag("intro_done").close()))
        .node("more", node -> node
                .text("This land is full of secrets.")
                .choice(choice -> choice.text("Back").next("start"))));

AdmDialogueApi.openDialogue(serverPlayer, entity, "mymod:intro");
```

`registerDialogue` also accepts a raw JSON string. Registration validates the dialogue eagerly and throws on invalid input, so broken dialogues fail at startup instead of at first click. `unregisterDialogue(id)` removes one.

### Events

Two cancellable events fire on `NeoForge.EVENT_BUS`, both server-side:

```java
import net.aviel.dialogue.api.event.DialogueOpenEvent;
import net.aviel.dialogue.api.event.DialogueChoiceEvent;

NeoForge.EVENT_BUS.addListener((DialogueOpenEvent event) -> {
    if (event.getPlayer().getTags().contains("cursed")) {
        event.setDialogueFile("mymod:cursed_greeting");
    }
});

NeoForge.EVENT_BUS.addListener((DialogueChoiceEvent event) -> {
    if ("forbidden_choice".equals(event.getChoice().id())) {
        event.setCanceled(true);
    }
});
```

`DialogueOpenEvent` fires before a dialogue opens; cancel it to suppress the dialogue, or replace the file/id. `DialogueChoiceEvent` fires after a choice passed validation but before its actions (flags, items, commands) apply; cancelling rejects the choice.

## JSON Schemas

Machine-readable schemas for every JSON format live in [`docs/schemas/`](schemas/):

- `dialogue.schema.json`
- `trade.schema.json`
- `npc_template.schema.json`
- `entity_dialogues.schema.json`

Point your editor at them for autocomplete and inline validation, e.g. in VS Code:

```json
{
  "json.schemas": [
    { "fileMatch": ["**/adm-dialogues/dialogues/*.json"], "url": "./docs/schemas/dialogue.schema.json" },
    { "fileMatch": ["**/adm-dialogues/trades/*.json"], "url": "./docs/schemas/trade.schema.json" },
    { "fileMatch": ["**/adm-dialogues/npc_templates/*.json"], "url": "./docs/schemas/npc_template.schema.json" },
    { "fileMatch": ["**/adm-dialogues/entity_dialogues.json"], "url": "./docs/schemas/entity_dialogues.schema.json" }
  ]
}
```

## Complete Examples

### Pig Dialogue With Random Lines

`config/adm-dialogues/entity_dialogues.json`

```json
{
  "entities": {
    "minecraft:pig": "pig.json"
  },
  "default": ""
}
```

`config/adm-dialogues/dialogues/pig.json`

```json
{
  "title": "Pig",
  "speaker": "Pig",
  "text_speed": 2,
  "sound": {
    "mode": "repeating",
    "text": "minecraft:entity.pig.ambient",
    "volume": 0.18,
    "pitch": 1.35,
    "letter_interval": 4
  },
  "random_start": ["oink_1", "oink_2", "oink_3", "blood"],
  "nodes": {
    "oink_1": {
      "text": "Oink.",
      "choices": [{ "text": "Close.", "close": true }]
    },
    "oink_2": {
      "text": "Oink oink.",
      "choices": [{ "text": "Close.", "close": true }]
    },
    "oink_3": {
      "text_speed": 1,
      "text": "Oiiiiiiiiink.",
      "choices": [{ "text": "Close.", "close": true }]
    },
    "blood": {
      "text_speed": 4,
      "sound": {
        "mode": "full",
        "full": "minecraft:entity.pig.hurt",
        "volume": 0.8,
        "pitch": 0.8
      },
      "text": "&cBLOOD FOR THE BLOOD GOD",
      "choices": [{ "text": "Pretend you heard nothing.", "close": true }]
    }
  }
}
```

### Two-NPC Quest Flow

Blacksmith starts a quest:

```json
{
  "title": "Blacksmith",
  "speaker": "Blacksmith",
  "start": "start",
  "nodes": {
    "start": {
      "text": "I need ore from the miner.",
      "choices": [
        {
          "id": "accepted_ore_job",
          "text": "I will ask the miner.",
          "set_flags": ["ore_job_started"],
          "close": true
        },
        {
          "text": "Maybe later.",
          "close": true
        }
      ]
    },
    "turn_in": {
      "text": "Do you have the ore?",
      "choices": [
        {
          "text": "Here it is.",
          "requires_flags": ["ore_job_started"],
          "take_items": [{ "item": "minecraft:raw_iron", "count": 3 }],
          "give_items": [{ "item": "minecraft:iron_ingot", "count": 1 }],
          "clear_flags": ["ore_job_started"],
          "set_flags": ["ore_job_done"],
          "close": true
        }
      ]
    }
  }
}
```

Miner only helps after the quest starts:

```json
{
  "title": "Miner",
  "speaker": "Miner",
  "start": "start",
  "nodes": {
    "start": {
      "text": "The mine is quiet today.",
      "choices": [
        {
          "text": "The blacksmith needs ore.",
          "requires_flags": ["ore_job_started"],
          "give_items": [{ "item": "minecraft:raw_iron", "count": 3 }],
          "set_flags": ["miner_gave_ore"],
          "close": true
        },
        {
          "text": "Goodbye.",
          "close": true
        }
      ]
    }
  }
}
```

## Troubleshooting

### The dialogue does not open

Check:

- Is the file in `config/adm-dialogues/dialogues`?
- Does `entity_dialogues.json` point to the correct file name?
- Did you restart the game or run `/adm_npc dialogue reload`?
- Is the JSON valid?
- Are you right-clicking with the main hand?

### A choice is missing

Check conditions:

- `requires_flags`
- `missing_flags`
- `requires_tags`
- `missing_tags`
- `requires_choices`
- `requires_items`
- `take_items`

If any requirement fails, ADM hides the choice.

### Items are not detected

Use full item ids:

```json
{ "item": "minecraft:raw_iron", "count": 3 }
```

Modded items should use their namespace:

```json
{ "item": "yourmod:custom_ore", "count": 1 }
```

### Sounds do not play

Check that the sound id exists, or that the local `.ogg` file is in `config/adm-dialogues/sounds`.

Good:

```json
"minecraft:entity.pig.ambient"
```

Bad:

```json
"minecraft:entity.pig.angry"
```

Local sound example:

```text
config/adm-dialogues/sounds/type_click.ogg
```

JSON:

```json
"type_click"
```

### Custom NPC skin is missing

Check:

- The template uses a valid resource location.
- The texture path exists in a mod, resource pack, or `config/adm-dialogues/skins`.
- The path starts after `assets/<namespace>/`.

Example:

```text
assets/yourmod/textures/entity/npc/guard.png
```

Template:

```json
{
  "skin": "yourmod:textures/entity/npc/guard.png"
}
```

Local skin example:

```text
config/adm-dialogues/skins/guard.png
```

Template:

```json
{
  "skin": "guard.png"
}
```

### JSON text looks broken

Save JSON files as UTF-8. This matters for non-English dialogue text.
