# ADM Starter Examples

A ready-to-copy content set: a guard NPC with a small quest (flags, item turn-in, rewards),
a trade shop gated behind quest completion, and a translated hunter quest driven by a nested
condition tree.

## Install

Copy the folders into your config directory so the layout becomes:

```text
config/adm-dialogues/
  dialogues/guard.json
  dialogues/hunter_quest.json
  dialogues/langs/ru_ru/hunter_quest.json
  trades/guard_armory.json
  npc_templates/guard.json
```

Then in game:

```text
/npc reload
/npc spawn Guard guard
/npc validate
```

## Notes

- `hunter_quest.json` contains all quest logic. Its Russian text-only overlay lives at
  `dialogues/langs/ru_ru/hunter_quest.json`. The quest also demonstrates nested `all`, `any`,
  and `not` conditions.

- The template references `skin": "guard.png"` — drop any 64x64 player skin at `config/adm-dialogues/skins/guard.png`, or delete the field to use the default skin.
- The dialogue plays `<anim:wave>` on quest completion — drop a `wave.json` keyframe animation into `config/adm-dialogues/emotes/`, or remove the tag. A missing emote is skipped and reported by `/npc validate`.
- To ship this set in a datapack, preserve the `dialogues/langs/<language>/` structure below
  `data/<ns>/adm_dialogues/`; reference the base dialogues as `<ns>:guard` or
  `<ns>:hunter_quest`.
- The `$schema` lines enable editor autocomplete when the files stay next to `docs/schemas/`; they are ignored by ADM and safe to delete after copying.
