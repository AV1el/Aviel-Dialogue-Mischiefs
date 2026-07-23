# ADM Starter Examples

A ready-to-copy content set: a guard NPC with a small quest (flags, item turn-in, rewards) and a trade shop gated behind quest completion.

## Install

Copy the folders into your config directory so the layout becomes:

```text
config/adm-dialogues/
  dialogues/guard.json
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

- The template references `skin": "guard.png"` — drop any 64x64 player skin at `config/adm-dialogues/skins/guard.png`, or delete the field to use the default skin.
- The dialogue plays `<anim:wave>` on quest completion — drop a `wave.json` keyframe animation into `config/adm-dialogues/emotes/`, or remove the tag. A missing emote is skipped and reported by `/npc validate`.
- To ship this set in a datapack instead, move the dialogue and trade to `data/<ns>/adm_dialogues/dialogues/` and `data/<ns>/adm_dialogues/trades/`, and reference them as `<ns>:guard` and `<ns>:guard_armory`.
- The `$schema` lines enable editor autocomplete when the files stay next to `docs/schemas/`; they are ignored by ADM and safe to delete after copying.
