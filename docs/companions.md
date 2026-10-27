# Companion prototype (Minecraft 1.21.1)

NPCs start in idle mode with combat disabled. Enable following or guarding and
combat separately. Commands use the existing `/npc` permission level.

```mcfunction
/npc companion <npc UUID or single-entity selector> follow <player>
/npc companion <npc> guard
/npc companion <npc> idle
/npc companion <npc> combat true
/npc companion <npc> combat false
/npc companion <npc> release
```

Following without a player argument uses the player issuing the command. Guard
captures the NPC's current block as its post. Idle clears the target and movement
and disables ordinary companion processing, while explicit POI walks still work.
Templates opting into `social.combat` may still guard autonomously while idle;
disable combat explicitly to stop those attacks. See [factions and opinions](factions-opinions.md).
The first player to issue a companion order recruits the NPC. Ownership survives
idle and guard orders and world reloads; `release` clears it and disables combat.
Only the owner, server console or an operator with the configured NPC command
permission level can change orders. Dialogue command elevation does not bypass
this check: it uses the player's actual permission level. General commands such
as `/attribute` are still trusted administrator-authored dialogue actions.
Combat permission survives mode changes. Guard and follow return to their duties
after a fight. Explicit POI walks take priority and temporarily pause companions.
Following pauses if the owner dies, disconnects or changes dimension; it resumes
when the same player is available in the NPC's dimension. There is no teleporting,
chunk loading or cross-dimension following. Mode, owner and guard post persist.

Combat uses the vanilla player-model arm swing and player strong-hit/no-damage
sounds. Damage is applied immediately when the swing starts and range/visibility
permit a hit. Cooldown uses `ceil(20 / attack_speed)` ticks, including weapon
modifiers (default base 4; a normal sword yields 1.6, or 13 ticks).
Damage is still attributed to the NPC using Minecraft's mob melee function;
this does not create a fake player or enable player-only critical/sweeping attacks.
Targets include nearby hostile
mobs and non-player attackers of the companion or its owner. Players, ADM NPCs and
team allies are excluded. Target acquisition runs once every 20 entity ticks,
staggered by entity ID. Targets must be visible, within 12 blocks of the owner/post
and within 16 blocks of the companion by default. Navigation updates outside combat run once
per second. Vanilla melee navigation runs during combat. No performance benchmark
has been performed; idle mode still has normal Minecraft entity ticking.

NPCs are invulnerable by default. For a mortal companion, select it and run
`/npc set invulnerable false`. Existing vanilla attribute commands can configure
combat stats (replace `<npc>` with its UUID):

```mcfunction
/attribute <npc> minecraft:generic.max_health base set 40
/attribute <npc> minecraft:generic.attack_damage base set 6
/attribute <npc> minecraft:generic.armor base set 4
```

Changing maximum health does not heal current health. Damage defaults to 3 before
equipment modifiers. Existing equipment commands supply weapons and armor.

## Editing template stats

Edit a saved template directly (replace `companion_guard` with its file ID):

```mcfunction
/npc template companion_guard stats
/npc template companion_guard stats health 40
/npc template companion_guard stats damage 3
/npc template companion_guard stats armor 4
/npc template companion_guard stats speed 0.35
/npc template companion_guard stats attack_speed 4
/npc template companion_guard stats attack_range 3
/npc template companion_guard stats knockback 0.4
/npc template companion_guard stats knockback_resistance 0.2
/npc template companion_guard stats radius 12
/npc template companion_guard stats movement_multiplier 0.8
/npc template companion_guard stats policy defend_owner
/npc select
/npc template companion_guard apply
```

These are administrator commands under the existing `/npc` permission gate.
Values are base attributes before equipment modifiers. Edits are written directly
to `npc_templates/<ID>.json` and affect future spawns using that template. The
`apply` command reapplies the entire template to the selected existing NPC,
including its name, skin, dialogue and equipment. It preserves current health
percentage and ownership. Existing NPCs are not automatically changed as a group.
The old standalone `/npc stats` commands have been removed. To create a template
from a selected NPC first use `/npc set save <ID>`.
`attack_speed` accepts 0.1-20 attacks/second before equipment modifiers. Effective
values are bounded to 0.1-20 for scheduling. Each attack waits for full cooldown.

## Template combat profiles

See `docs/examples/npc_templates/companion_guard.json`. The optional `combat`
object sets base attributes (`health`, `damage`, `armor`, `speed`, `attack_speed`) and behaviour
(`radius`, `movement_multiplier`, `policy`, `targets`). Equipment adds its own
attribute modifiers. Applying a profile preserves the current health percentage.
Templates without `combat` retain existing attributes and behaviour. Saving a
template exports current base attributes and combat rules, but never its owner.

Policies: `passive` never acquires targets; `retaliate` responds only to attackers
of the NPC; `defend_owner` also responds to attackers of its owner; `hostile` also
searches for nearby hostile mobs. All policies still require combat to be enabled
and an active guard/follow order. A nonempty `targets` list restricts all targets,
including retaliation, to those entity IDs. An empty list permits the default
target rules. Invalid profiles are rejected when loading templates.

Defaults preserve the original prototype. Radius accepts 1-32 blocks; target
acquisition runs once per second, and chase distance from the NPC is radius + 4.
Profiles and ownership persist in entity NBT, while vanilla saves attributes.
Reapplying a template updates its profile without releasing ownership. Profile
changes on disk take effect when applied, not automatically to all spawned NPCs.

Custom attack clips and spells are not part of the ability system yet.
Vanilla combat animation takes priority over emotes; starting an attack
stops the current emote.

## Combat GUI

Shift-right-click an NPC to open the unified editor. Its tabs are Display, Stats,
AI, Inventory and Dialogue; the NPC preview remains visible on every
page. Long pages scroll, and switching tabs preserves template draft edits.
Choose a template in the bottom bar. Stats and AI targeting edit its
draft; Display, Inventory and Dialogue change the current NPC immediately.
Save Template writes the combat draft together with the current NPC's
appearance, equipment and dialogue to disk. Apply loads the saved template onto
this NPC, replacing unsaved edits. Closing discards unsaved combat edits,
but does not undo immediate NPC changes. Other NPCs are not changed automatically.
Delete NPC is on Display, with a second click required within three seconds.
Faction/opinion settings remain JSON-only and are not overwritten by GUI saves.

Stats includes `attack_range` (0.5-6 blocks, default 3), `knockback` (0-5,
default 0) and `knockback_resistance` (0-1, default 0). These fields also work
directly in a template's `combat` object. Attack reach measures from the NPC's
eyes to the nearest point of the target hitbox and still requires line of sight.
It is separate from `radius`, which controls target acquisition and pursuit.
Knockback attributes are base values before equipment modifiers.

Dialogue includes a 22-channel HEX palette, with a swatch and individual reset
for every channel. Narrow windows use one column; wide windows use two. Apply
sets the palette on this NPC; Reset All restores the dialogue's own colors.
Accepts `#RRGGBB` or `#AARRGGBB` (alpha first). Invalid input is highlighted and
cannot be applied. Empty fields inherit the original dialogue's colors.
Save Template also applies valid palette edits and stores them in its root
`dialogue_style` object. Shared dialogue files are not modified. Overrides survive
world reloads and are included in dialogue refreshes. Existing `dialogue_color`
templates and saved background colors migrate to the two background channels.

```json
"dialogue_style": {
  "text_background": "#F6162430",
  "choices_background": "#F6192028",
  "border_color": "#7CC9B4",
  "divider_color": "#7CC9B4",
  "text_color": "#F5F5F5",
  "speaker_color": "#FFD36B",
  "button_color": "#24343E",
  "button_hover_color": "#345449"
}
```

Other channels are `outer_color`, `header_background`, `npc_name_color`,
`button_disabled_color`, `button_text_color`, `button_disabled_text_color`,
`button_border_color`, `button_hover_border_color`, `button_hover_text_color`,
`inactive_color`, `error_color`, `scroll_track_color`, `scroll_thumb_color`, and
`typing_indicator_color`. Text/speaker overrides apply to all dialogue nodes;
explicit inline formatting codes inside authored strings retain their own colors.
Style channels also work in a shared dialogue's `style` object; `text_color` and
`speaker_color` remain root/node properties in shared dialogue files.

AI's Orders section controls this NPC immediately: wait, follow the editor player, guard the
current position, and enable/disable combat. These runtime orders do not change
the template. All actions require server-side editor permission and a distance
of at most 64 blocks. Errors and save confirmations come from the server.
The combat GUI uses an additional network payload: install matching updated ADM
builds on the server and clients.

## Target switching

The optional `combat.targeting` object accepts:

```json
{
  "switch_on_attack": true,
  "protect_owner": true,
  "switch_cooldown": 20,
  "lost_sight_ticks": 40
}
```

Recent attackers of the owner have priority over attackers of the NPC, who have
priority over a target acquired by proximity. The respective toggles and combat
policy still apply. Attack events are considered recent for 100 ticks. A new,
higher-priority target can replace a living current target after switch_cooldown.
Equal-priority candidates keep the current target to prevent oscillation. Death,
invalid range, allied status or an invalid entity type releases a target without
waiting for the cooldown. Lost line of sight instead allows lost_sight_ticks
before releasing it. Delays accept whole numbers from 0 to 200 ticks.
Target acquisition still runs once per second; delay values do not increase scan
frequency. These settings persist in templates and NPC saves.

Copy `docs/examples/dialogues/companion.json` into the server's dialogue folder and
assign it to the NPC. Its server-side commands use `{npc_uuid}` and `{player}` so
they affect the dialogue NPC directly, regardless of the player's editor selection.
Use dialogue conditions to restrict first recruitment when appropriate for your
server. Another player's existing companion cannot be recruited through dialogue.
