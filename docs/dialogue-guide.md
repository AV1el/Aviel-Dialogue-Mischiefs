<div align="center">

# Aviel's Dialogue Mod

### The complete guide

</div>

---

Welcome, and thank you for building with ADM.

This mod is a foundation, not a quest pack. You write JSON — or call the Java API — and ADM
takes care of the rest: the NPCs, the dialogue screen, branching choices, player state, item
checks, shops, sounds, animations and movement. Nothing here requires a Java toolchain; a text
editor and a running game are enough.

The guide is written to be read in order the first time and skimmed forever after. Every path
mentioned below is relative to `config/adm-dialogues`.

---

## Contents

**Getting started** — [Your first dialogue](#your-first-dialogue) ·
[Where files live](#where-files-live) · [Shipping in a datapack](#shipping-in-a-datapack)

**Writing dialogues** — [Dialogue files](#dialogue-files) · [Nodes](#nodes) ·
[Choices](#choices) · [Inline tags](#inline-tags) · [Text formatting](#text-formatting)

**Quests and state** — [Flags](#flags) · [Remembered choices](#remembered-choices) ·
[Items](#items) · [Commands](#commands)

**Shops** — [Trade files](#trade-files) · [Offers](#offers)

**NPCs** — [Templates](#templates) · [Skins](#skins) · [Equipment](#equipment) ·
[Movement and points of interest](#movement-and-points-of-interest)

**Presentation** — [Sound](#sound) · [Screen colours](#screen-colours)

**Tools** — [The in-game editor](#the-in-game-editor) ·
[Command reference](#command-reference) · [Validation](#validation) ·
[JSON schemas](#json-schemas)

**For mod authors** — [Java API](#java-api) · [Dialogues in code](#dialogues-in-code) ·
[Events](#events)

**Reference** — [Worked examples](#worked-examples) · [Troubleshooting](#troubleshooting) ·
[Where to go next](#where-to-go-next)

---

# Getting started

## Your first dialogue

Save this as `dialogues/greeter.json`:

```json
{
  "title": "Greeter",
  "speaker": "Greeter",
  "start": "start",
  "nodes": {
    "start": {
      "text": "Hello, traveler.",
      "choices": [
        { "text": "Goodbye.", "close": true }
      ]
    }
  }
}
```

Spawn an NPC, hand it the file, and right-click it:

```text
/npc reload
/npc spawn Greeter
/npc set dialogue greeter.json
```

`/npc reload` re-reads the folder so a freshly saved file is picked up; after that, editing a
file in place is enough. That is a complete, working dialogue.

## Where files live

```text
config/adm-dialogues/
├── dialogues/            conversations
├── trades/               shops
├── emotes/               keyframe animations
├── npc_templates/        reusable NPC presets
├── skins/                PNG skins for NPCs
├── sounds/               OGG voice lines and clicks
└── lang/                 translation files
```

Files are referenced by name, with or without the extension — `guard.json` and `guard` both
work.

**Skins and sounds are client resources.** ADM copies them into a generated resource pack, so
`skins/guard.png` becomes `adm:textures/entity/npc/guard.png` and `sounds/voice.ogg` becomes
`adm:dialogue/voice`. You only ever write the short name. Keep filenames lowercase, using
letters, digits, `_`, `-` and `.`.

After adding or replacing a skin or sound while the game is running, run
`/npc reload`, then press `F3+T` to reload client resources.

**Player progress** — flags and remembered choices — is stored per world. The dialogue files
themselves are global and shared by every world.

## Shipping in a datapack

Dialogues, trades and emotes can live in a datapack or a mod jar instead of the config folder:

```text
data/<namespace>/adm_dialogues/dialogues/guard.json
data/<namespace>/adm_dialogues/trades/armory.json
data/<namespace>/adm_dialogues/emotes/wave.json
```

Reference them by namespaced id, without the `.json` — for example `mymod:guard`.

Those ids work everywhere a filename does: template `dialogue` fields, choice `trade` fields,
`<anim:mymod:wave>` tags, `/npc set dialogue`, and the Java API. Subfolders become part of
the id, so `dialogues/npcs/guard.json` is `mymod:npcs/guard`.

Datapack content reloads with vanilla `/reload`. Plain names and `ns:id` references live in
separate namespaces, so a config file and a datapack entry sharing a name never collide.

Skins, sounds and lang files cannot ship in a datapack — they are client resources. Put them in
a mod jar under `assets/<namespace>/`, in a resource pack, or in the config folders.

---

# Writing dialogues

## Dialogue files

A dialogue is a root object plus a `nodes` object.

| Field | Type | What it does |
| --- | --- | --- |
| `nodes` | object or array | **Required.** The conversation itself. |
| `title` | string | Name shown in the screen header. |
| `speaker` | string | Default speaker for every node. |
| `start` · `starts` · `random_start` | string or array | Where the conversation opens. |
| `text_speed` | number | Default typewriter speed; `0` is instant. |
| `text_color` · `speaker_color` | colour | Defaults for every node. |
| `sound` | object | Default sound settings. |
| `style` | object | Screen colours. |

With no start given, the first node in the file is used. Listing several starts makes ADM pick
one at random each time the dialogue opens, which is the easiest way to give an NPC idle
variety:

```json
{ "random_start": ["hello", "busy", "rare_line"] }
```

## Nodes

A node is one screen: what the NPC says, and what the player may answer.

```json
{
  "nodes": {
    "start": {
      "speaker": "Blacksmith",
      "text": [
        "The forge is hot.",
        "Steel remembers every hand that shaped it."
      ],
      "choices": []
    }
  }
}
```

| Field | Type | What it does |
| --- | --- | --- |
| `text` | string or array | What the NPC says; array entries become separate lines. |
| `choices` | array | What the player may answer. |
| `speaker` | string | Overrides the dialogue speaker here. |
| `text_speed` · `speed` | number | Typewriter speed for this node. |
| `text_color` · `speaker_color` | colour | Colours for this node. |
| `sound` | object | Sound for this node. |

A node without choices simply shows a close button.

## Choices

Choices are the buttons down the right-hand side. Three shapes cover most of them:

```json
{ "text": "What can you craft?", "next": "craft" }
{ "text": "Goodbye.",            "close": true }
{ "text": "Back.",               "action": "back" }
```

Everything you can add to a choice falls into one of three groups.

### Where it leads

| Field | Type | What it does |
| --- | --- | --- |
| `text` · `label` | string | The button text. |
| `next` | string | Node to open. |
| `close` | boolean | Ends the conversation. |
| `action` | string | `back` returns to the previous node; `close` and `exit` end it. |
| `trade` · `shop` · `open_trade` | string | Opens a shop. |
| `id` | string | Stable id, so other choices can require this one. |

### When it is shown

| Field | Shown only when |
| --- | --- |
| `requires_flags` · `requires_flag` | the player has these flags |
| `missing_flags` · `missing_flag` | the player does **not** have these flags |
| `requires_tags` · `requires_tag` | the player has these scoreboard tags |
| `missing_tags` · `missing_tag` | the player does **not** have these tags |
| `requires_choices` · `requires_choice` | the player picked those choices earlier |
| `requires_items` | the player is carrying these items |
| `take_items` | the player can afford this cost |

A choice failing any condition is hidden, not greyed out.

### What it does

| Field | What it does |
| --- | --- |
| `set_flags` · `add_flags` | Adds flags |
| `clear_flags` · `remove_flags` | Removes flags |
| `add_tags` · `remove_tags` | Adds or removes scoreboard tags |
| `take_items` | Removes items |
| `give_items` | Grants items, dropping any overflow |
| `commands` · `command` | Runs server commands |

Everything runs server-side, in the order listed above, only after the choice passes its own
conditions again.

### How players pick

Click a choice, press the number printed on its button, or walk the list with the arrow keys and
confirm with Enter or Space. Long lists scroll, and chevrons on the right edge show there is
more above or below. Any key pressed while text is still printing finishes the typewriter
instead of choosing, so nobody skips a line by accident.

## Inline tags

Tags live inside node text. ADM strips them from what the player reads and uses them to drive
the typewriter, sounds, animations and movement.

| Tag | Effect |
| --- | --- |
| `<pause:short>` | Pauses the typewriter |
| `<slow>` `<fast>` `<instant>` | Changes typing speed |
| `<sound:…>` | Plays a sound at that exact moment |
| `<anim:…>` | Plays an emote on the NPC |
| `<moveto:…>` | Sends the NPC walking — see [Movement](#movement-and-points-of-interest) |

They mix freely with `&` colour codes:

```json
{ "text": "&cYou <pause:short>should not have come here.&r" }
```

### Pauses

```text
<pause:short>    <pause:medium>    <pause:long>
<pause:20>       in ticks
<pause:0.5s>     in seconds
```

Aliases: `<wait:…>`, `<p:…>`, `<p=…>`.

### Speed

```text
<slow>    <normal>    <fast>    <instant>
<speed:1>    <speed:4>    <speed:normal>
```

Aliases: `<spd:…>`, `<s:…>`, and `</speed>` to return to normal.

```json
{ "text": "I will speak <slow>very slowly<normal>, then <fast>quickly." }
```

### Emotes

ADM plays keyframe animations from `emotes/`:

```json
{ "text": "Hello there! <anim:wave>Nice to meet you." }
```

```text
<anim:wave>         play once
<anim:wave:loop>    keep looping
<anim:stop>         stop the current emote
```

Aliases: `<animation:…>` and `<emote:…>`; `:repeat` behaves like `:loop`; `clear`, `idle` and
`none` behave like `stop`.

Emotes sync to everyone nearby. The server validates the file before playing it, and only a
player with that dialogue open can trigger one.

ADM reads and plays these files itself and needs no animation mod installed. If
**PlayerAnimator** (`player-animation-lib`) happens to be present, ADM hands playback to it for
exact interpolation and limb bending; without it the built-in player takes over and the emote
still runs. Nothing about your files changes either way.

The format is ordinary keyframe JSON — `beginTick`, `endTick`, a list of `moves`, and per-part
channels with an easing. Any tool that writes it will do, so animations exported from EmoteCraft
drop straight into `emotes/` if you already have some.

## Text formatting

Minecraft formatting codes, written with `&`:

| Code | Effect |
| --- | --- |
| `&0`–`&f` | Colours |
| `&l` | Bold |
| `&o` | Italic |
| `&n` | Underline |
| `&m` | Strikethrough |
| `&k` | Obfuscated |
| `&r` | Reset |

```json
{ "text": "&eGolden text&r and normal text." }
```

Translation keys work too: `{{my.lang.key}}` is looked up in the player's language and falls
back to the key itself. Put the translations in `lang/en_us.json`.

---

# Quests and state

## Flags

Flags are per-player booleans kept in the world save. They are how a quest remembers anything.

```json
{ "text": "I accept the job.",   "set_flags": ["ore_job"],   "next": "accepted" }
{ "text": "I brought the ore.",  "requires_flags": ["ore_job"], "next": "turn_in" }
{ "text": "Do you have work?",   "missing_flags": ["ore_job"],  "next": "job_offer" }
```

Flags are global per player, not per NPC, so one character can react to what another was told.

## Remembered choices

The same idea with no bookkeeping. Give a choice an `id` and ADM records that the player picked
it:

```json
{ "id": "asked_about_forge", "text": "Tell me about the forge.", "next": "forge_lore" }
```

```json
{ "text": "About what you said earlier…", "requires_choices": ["asked_about_forge"] }
```

Use flags for quest state you set and clear deliberately. Use remembered choices for "has this
ever come up".

## Items

Item rules accept three shapes:

```json
"minecraft:raw_iron"
{ "item": "minecraft:raw_iron", "count": 3 }
[ { "item": "minecraft:raw_iron", "count": 3 }, "minecraft:coal" ]
```

Always include the namespace: `minecraft:` for vanilla, the mod's id otherwise.

| Field | When it applies |
| --- | --- |
| `requires_items` | Checked before the choice is shown; items stay with the player |
| `take_items` | Checked before it is shown, then removed when picked |
| `give_items` | Granted when picked; overflow drops at the player's feet |

A typical hand-in uses both sides at once:

```json
{
  "text": "Here are the bones.",
  "take_items": [{ "item": "minecraft:bone",    "count": 3 }],
  "give_items": [{ "item": "minecraft:emerald", "count": 2 }],
  "set_flags": ["bones_delivered"],
  "close": true
}
```

## Commands

A choice can run server commands at permission level 4:

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

| Placeholder | Becomes |
| --- | --- |
| `{player}` | Player name |
| `{player_uuid}` | Player UUID |
| `{npc}` · `{entity}` | NPC or entity display name |
| `{npc_uuid}` · `{entity_uuid}` | Target entity UUID |

The `%player%` style works as well. Commands that fail are logged rather than shown to the
player.

---

# Shops

## Trade files

A shop is a JSON file in `trades/`, opened from a choice:

```json
{ "text": "Show me your wares.", "trade": "blacksmith_shop.json" }
```

```json
{
  "title": "Blacksmith Shop",
  "subtitle": "Steel for coin.",
  "offers": [
    {
      "id": "iron_sword",
      "title": "Iron Sword",
      "cost":   [{ "item": "minecraft:iron_ingot", "count": 3 }],
      "result": [{ "item": "minecraft:iron_sword", "count": 1 }]
    },
    {
      "title": "Secret Blade",
      "requires_flags": ["ore_done"],
      "cost":   [{ "item": "minecraft:diamond", "count": 2 }],
      "result": [{ "item": "minecraft:diamond_sword" }]
    }
  ]
}
```

## Offers

| Field | Type | What it does |
| --- | --- | --- |
| `cost` · `price` · `buy` · `take` | items | Taken from the player |
| `result` · `sell` · `give` · `reward` | items | Given to the player |
| `title` · `name` | string | Offer name |
| `description` · `lore` | string | Tooltip text |
| `id` | string | Stable id; defaults to `offer_<index>` |
| `category` | string | Grouping label |
| `commands` | array | Run on purchase; `{amount}` is available |
| `requires_flags` · `missing_flags` | array | Flag conditions |
| `requires_tags` · `missing_tags` | array | Tag conditions |

Items use the same shapes as [Items](#items). Players can buy up to 64 at a time, and both cost
and result scale with the amount. Offers locked behind flags arrive already marked, so the
screen shows them as unavailable rather than pretending they can be bought.

---

# NPCs

## Templates

Templates are presets for ADM's own NPC, stored in `npc_templates/`:

```json
{
  "name": "Guard",
  "dialogue": "guard.json",
  "scale": 1.0,
  "name_visible": true,
  "invulnerable": true,
  "look_distance": 8.0,
  "model": "steve",
  "skin": "guard.png",
  "equipment": {
    "mainhand": "minecraft:iron_sword",
    "chest": "minecraft:iron_chestplate"
  }
}
```

| Field | Type | What it does |
| --- | --- | --- |
| `name` | string | Nameplate text; `&` colours work |
| `dialogue` · `dialogue_file` | string | Filename or `ns:id` |
| `scale` | number | `1.0` is player height; the hitbox scales with it |
| `name_visible` | boolean | Show the nameplate |
| `invulnerable` | boolean | Ignore all damage |
| `look_distance` | number | How far the NPC tracks players; `0` disables |
| `model` · `player_model` | string | `steve` or `alex` (also accepted as `slim`) |
| `skin` | string | See [Skins](#skins) |
| `equipment` | object | See [Equipment](#equipment) |

Spawn straight from a template by naming it after the NPC name — `/npc spawn Guard guard` — or
save a live NPC back into one with `/npc set save guard`.

## Skins

Two ways to point at a texture:

```json
{ "skin": "guard.png" }
{ "skin": "yourmod:textures/entity/npc/guard.png" }
```

The short form reads from `skins/`. The long form is any resource location from a mod or
resource pack. An NPC with no skin at all uses the bundled Aviel skin on the wide model, and
vanilla Alex on the slim one.

## Equipment

Six slots, each holding an item id or an object with a count:

```json
{
  "equipment": {
    "mainhand": "minecraft:iron_sword",
    "offhand":  "minecraft:shield",
    "head":     { "item": "minecraft:iron_helmet" },
    "chest":    "minecraft:iron_chestplate",
    "legs":     "minecraft:leather_leggings",
    "feet":     "minecraft:leather_boots"
  }
}
```

Aliases work throughout: `weapon` and `held` for `mainhand`, `helmet` and `hat` for `head`,
`boots` for `feet`, and so on.

NPC gear is decorative. It renders on the model, but never drops and cannot be picked up.

The fastest way to dress an NPC is to wear the outfit yourself and run `/npc equip from_me`.

## Movement and points of interest

NPCs walk to named points placed in the world. Points live in the world save rather than in
JSON, so moving a marker updates every dialogue that references it.

### Placing points

Get a marker with `/npc point marker`, or `/npc point marker market` to name it up front.

| Action | Result |
| --- | --- |
| Right-click a block | Places a point on the face you clicked |
| Sneak + right-click | Removes the nearest point within 6 blocks |
| Right-click in the air | Lists every point |

An unnamed marker numbers its points `poi_1`, `poi_2` and so on. A named marker reuses its id,
so placing it again **moves** that point instead of creating a second one. Rename a marker in an
anvil to change its id.

### Walking from a dialogue

```json
{ "text": "Follow me. <moveto:slow:market>I will show you the stalls." }
```

The NPC sets off the moment the typewriter reaches the tag, so movement lines up with the line
being spoken. Speed is optional — `<moveto:market>` walks at the normal pace. Aliases:
`<walkto:…>` and `<goto:…>`.

| Speed | Pace |
| --- | --- |
| `slow` | A deliberate, in-character walk |
| `walk` | Default |
| `fast` | Jogging, for urgent scenes |
| `sprint` | Flat out; looks frantic, so use it sparingly |

While walking, an NPC stops turning its head to follow players, then faces the way it stopped on
arrival. An interrupted walk survives a server restart.

The server checks every move: only the NPC the player is actually talking to, only within the
configured interact distance, and only to points in the same dimension. A tag naming a point
that does not exist is ignored and logged, and `/npc validate` reports it.

---

# Presentation

## Sound

Three modes, set per dialogue or per node:

| Mode | Behaviour |
| --- | --- |
| `repeating` | A short click as each letter appears |
| `full` | One sound when the node opens — voice lines |
| `none` | Silent, the default |

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

| Field | What it does |
| --- | --- |
| `mode` | `repeating`, `full` or `none` |
| `text` · `text_sound` | Sound used by repeating mode |
| `full` · `full_sound` · `voice` | Sound used by full mode |
| `volume` · `pitch` | Clamped to sane ranges |
| `letter_interval` | Letters between clicks in repeating mode |

A sound is either a resource location (`minecraft:entity.villager.yes`) or the name of a file in
`sounds/` — `"voice"` finds `sounds/voice.ogg`. Only OGG works; that is a Minecraft limitation,
not an ADM one.

A node-level `sound` replaces the dialogue-level one entirely, so a voiced line usually looks
like this:

```json
{
  "sound": { "mode": "full", "full": "guard_warning", "volume": 0.8 },
  "text": "&cLeave."
}
```

For a one-off effect mid-sentence, reach for the inline tag instead:

```json
{ "text": "Listen closely. <sound:minecraft:block.amethyst_block.chime>There it is." }
```

> **On a dedicated server**, the generated resource pack is built on each client from that
> client's own config folder. Players who do not have your `sounds/` files will hear nothing.
> Ship them in the modpack, or host a server resource pack.

## Screen colours

ADM's defaults are the amethyst palette. Override any of them per dialogue:

```json
{
  "style": {
    "outer_color": "#66000000",
    "text_background": "#F60F0817",
    "choices_background": "#F6160D22",
    "divider_color": "#FFA986EC",
    "button_color": "#FF1B1026",
    "button_hover_color": "#FF3C2A55",
    "button_disabled_color": "#FF241732",
    "button_text_color": "#FFF2E7FF",
    "button_disabled_text_color": "#FF6A5F78"
  }
}
```

`divider_color` doubles as the accent colour: the rule under the header, the highlight on the
selected answer, and the typing caret all use it.

Colours accept `#RRGGBB`, `#AARRGGBB`, `0xRRGGBB` and `0xAARRGGBB`. Include alpha for
translucent panels.

---

# Tools

## The in-game editor

Shift+right-click an ADM NPC to open a visual editor: a live 3D preview, a name field,
searchable pickers for dialogue, skin and template, sliders for scale and look distance, model
and invulnerability toggles, equipment copy and clear, and NPC removal behind a confirmation
step.

It requires the `/npc` permission level — a server operator, or cheats in singleplayer. For
everyone else Shift+right-click simply opens the dialogue as usual, so the editor never leaks
to players.

## Command reference

Everything lives under one command, `/npc`, one level deep. It needs permission level 2 by
default (`commandPermissionLevel` in the config).

**Which NPC does a command act on?** In this order: your explicit selection, then the NPC under
your crosshair up to 24 blocks away, then the nearest one within 8 blocks.

### Spawning and selecting

```text
/npc spawn <name>              a fresh NPC where you stand
/npc spawn <name> <template>   from a template (see below)
/npc select [name]             target by crosshair, or by name within 64 blocks
/npc select clear              release the target
/npc info                      print every setting of the targeted NPC
/npc remove                    remove the targeted NPC
/npc remove <name>             remove a specific nearby NPC — tab completes their names
/npc remove all <radius>       remove every NPC within the radius
```

Spawning an NPC selects it automatically, so the `set` commands below act on it right away.

### Editing the targeted NPC

```text
/npc set dialogue <file-or-id>   assign a dialogue; omit the argument to clear
/npc set skin <file-or-id>       assign a skin; omit the argument to clear
/npc set model <steve|alex>
/npc set scale <0.25-3>
/npc set look <0-64>             how far it tracks players; 0 disables
/npc set invulnerable <bool>
/npc set name <text>             & colour codes work
/npc set template <id>           apply a template to the existing NPC
/npc set here                    teleport it to you
/npc set save <id> [force]       save it, equipment included, to npc_templates/<id>.json
```

`set save` refuses to overwrite an existing template unless you pass `force`.

### Equipment

```text
/npc equip <slot>                use whatever you are holding
/npc equip <slot> <item> [count]
/npc equip from_me               copy your full kit
/npc equip clear <slot>
/npc equip clear_all
/npc equip list
```

Slots: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`.

### Points of interest

```text
/npc point list
/npc point marker [id]           get a marker item
/npc point here <id>             place a point where you stand
/npc point remove <id>
/npc point send <id> [speed]     walk the targeted NPC there now
/npc point stop
```

`point send` is the quickest way to test a route without touching a dialogue.

### Previewing emotes and trades

```text
/npc emote <file> [loop]         play an emote on the targeted NPC
/npc emote stop
/npc trade <file>                open a shop as if a choice had
```

### Listing, reloading and validating

```text
/npc list [npcs|dialogues|trades|emotes|templates|points|folders]
/npc reload                      clear caches, rebuild assets, validate
/npc validate                    validate without touching caches
```

`/npc list` with no category lists nearby NPCs; `folders` prints the config, skin, sound and
resource-pack paths.

## Validation

`/npc validate` checks every dialogue, trade and NPC template in a single pass.

**`[ERROR]`** means the file will not load at all — a JSON syntax problem, or a choice with no
text.

**`[WARN]`** means it loads, but something will not behave as written:

- a `next` pointing at a node that does not exist
- a `trade` pointing at a missing shop
- an `<anim:…>` tag for an unknown emote, or `<moveto:…>` for an unknown point
- an item id with no namespace

Editing a dialogue while the game runs is usually enough on its own — files are re-read whenever
their modification time changes. `/npc reload` forces it and rebuilds the resource pack at the
same time.

## JSON schemas

Schemas for every format live in [`docs/schemas/`](schemas/). Point your editor at them for
autocomplete and inline errors. In VS Code:

```json
{
  "json.schemas": [
    { "fileMatch": ["**/adm-dialogues/dialogues/*.json"],     "url": "./docs/schemas/dialogue.schema.json" },
    { "fileMatch": ["**/adm-dialogues/trades/*.json"],        "url": "./docs/schemas/trade.schema.json" },
    { "fileMatch": ["**/adm-dialogues/npc_templates/*.json"], "url": "./docs/schemas/npc_template.schema.json" }
  ]
}
```

Ready-made content to copy sits in [`docs/examples/`](examples/).

---

# For mod authors

## Java API

```java
import net.aviel.dialogue.api.AdmDialogueApi;

AdmDialogueApi.openDialogue(serverPlayer, entity, "custom.json");
AdmDialogueApi.openTrade(serverPlayer, entity, "shop.json");
AdmDialogueApi.playNpcEmote(npc, server, "wave.json", false);
AdmDialogueApi.applyNpcTemplate(dialogueNpc, "guard", server);

Path root = AdmDialogueApi.globalRootDirectory();
Path dialogues = AdmDialogueApi.globalDialogueDirectory();
```

## Dialogues in code

`DialogueBuilder` produces the same structure as a file. Register it under a namespaced id and
it works everywhere a file does — with no JSON on disk:

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
```

`registerDialogue` also accepts a raw JSON string. It validates eagerly and throws on bad input,
so a broken dialogue fails at startup rather than at the player's first click.
`unregisterDialogue(id)` removes one.

## Events

Two cancellable events fire on `NeoForge.EVENT_BUS`, both server-side:

```java
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

`DialogueOpenEvent` fires before a dialogue opens — cancel it, or swap the file.
`DialogueChoiceEvent` fires after a choice passes validation but before its actions apply, so
cancelling rejects the choice cleanly.

---

# Reference

## Worked examples

### Random idle lines

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
    "oink_1": { "text": "Oink.",        "choices": [{ "text": "Close.", "close": true }] },
    "oink_2": { "text": "Oink oink.",   "choices": [{ "text": "Close.", "close": true }] },
    "oink_3": { "text": "Oiiiiiiiiink.", "text_speed": 1, "choices": [{ "text": "Close.", "close": true }] },
    "blood": {
      "text_speed": 4,
      "sound": { "mode": "full", "full": "minecraft:entity.pig.hurt", "volume": 0.8, "pitch": 0.8 },
      "text": "&cBLOOD FOR THE BLOOD GOD",
      "choices": [{ "text": "Pretend you heard nothing.", "close": true }]
    }
  }
}
```

### A quest across two NPCs

The blacksmith starts it:

```json
{
  "title": "Blacksmith",
  "speaker": "Blacksmith",
  "nodes": {
    "start": {
      "text": "I need ore from the miner.",
      "choices": [
        { "id": "accepted_ore_job", "text": "I will ask the miner.", "set_flags": ["ore_job"], "close": true },
        { "text": "Maybe later.", "close": true }
      ]
    },
    "turn_in": {
      "text": "Do you have the ore?",
      "choices": [
        {
          "text": "Here it is.",
          "requires_flags": ["ore_job"],
          "take_items": [{ "item": "minecraft:raw_iron",  "count": 3 }],
          "give_items": [{ "item": "minecraft:iron_ingot", "count": 1 }],
          "clear_flags": ["ore_job"],
          "set_flags": ["ore_done"],
          "close": true
        }
      ]
    }
  }
}
```

The miner only helps once it has begun:

```json
{
  "title": "Miner",
  "speaker": "Miner",
  "nodes": {
    "start": {
      "text": "The mine is quiet today.",
      "choices": [
        {
          "text": "The blacksmith needs ore.",
          "requires_flags": ["ore_job"],
          "give_items": [{ "item": "minecraft:raw_iron", "count": 3 }],
          "close": true
        },
        { "text": "Goodbye.", "close": true }
      ]
    }
  }
}
```

### A guided walk

```json
{
  "nodes": {
    "start": {
      "text": "Welcome to town.",
      "choices": [{ "text": "Show me around.", "next": "tour" }]
    },
    "tour": {
      "text": "Follow me. <moveto:slow:market>The market is this way.",
      "choices": [{ "text": "Thanks.", "close": true }]
    }
  }
}
```

Place the point first with `/npc point marker market`, then right-click where the NPC should
end up.

## Troubleshooting

**The dialogue does not open.** Is the file in `dialogues/`? Did you run `/npc set dialogue` on
the NPC, and `/npc reload` after saving the file? Is the JSON valid — `/npc validate` will say.
Are you clicking with the main hand?

**A choice is missing.** Some condition failed: `requires_flags`, `missing_flags`,
`requires_tags`, `missing_tags`, `requires_choices`, `requires_items`, or an unaffordable
`take_items`. Failing choices are hidden rather than greyed out. `/npc info` shows the NPC's
state.

**Items are not detected.** Use the full id, namespace included: `minecraft:raw_iron`, never
`raw_iron`. `/npc validate` flags missing namespaces.

**Sounds do not play.** Check the id exists — `minecraft:entity.pig.ambient` is real,
`minecraft:entity.pig.angry` is not. For local files the OGG must be in `sounds/`, referenced by
bare name (`"voice"` for `sounds/voice.ogg`), followed by `/npc reload` and `F3+T`.
On a dedicated server, every client needs those files in its own config folder.

**A skin does not show.** Either it is missing from `skins/`, or the resource location is wrong.
Local files are written as `guard.png`; resource pack textures as
`yourmod:textures/entity/npc/guard.png`. New files need `/npc reload` and `F3+T`.

**An NPC will not walk.** Does the point exist — `/npc list points`? Is it in the same
dimension? Is the route actually walkable? Test with `/npc point send <id>` before blaming the
dialogue.

**Text looks broken.** Save your JSON as UTF-8. This matters for any non-English text.

---

## Where to go next

If you have read this far, you already know everything ADM can do. A good next step is to copy
the starter content from [`docs/examples/`](examples/) into your own
`config/adm-dialogues`, spawn the guard with `/npc spawn Guard guard`, and take it apart:
change a line, add an answer, place a point and send him walking. Every change is a file save
and a `/npc reload` away.

Two habits will save you the most time. Wire up the [JSON schemas](#json-schemas) so mistakes
surface while you type, and run [`/npc validate`](#validation) before you call a dialogue
finished — it catches the broken links and missing references that are invisible until a player
walks into them.

Happy writing.

<div align="center">

---

**[Report an issue](https://github.com/AV1el/Aviel-Dialogue-Mischiefs/issues)** ·
**[Schemas](schemas/)** · **[Examples](examples/)**

</div>
