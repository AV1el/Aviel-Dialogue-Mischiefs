# Factions and Player Opinions

Opinions are integers from -100 to 100. New players start at 0. Each score is
stored server-side in the world's `data/adm_opinions.dat`, keyed by player UUID
and opinion subject. No score is changed automatically for kills, damage, quest
acceptance, success, failure, trading or time. Authors explicitly add changes to
dialogue choices or invoke administrator commands from their own integrations.

## Faction relationships

Put `docs/examples/factions.json` at `config/adm-dialogues/factions.json`.
Each entry defines an NPC faction, with optional `relations` to other declared
factions: `allied`, `neutral` or `hostile`. Relationships are symmetric, so declare
a pair once. Conflicting pairs and references to undeclared factions are rejected.
Members of the same nonempty faction are allied; unspecified pairs are neutral.
NPCs sharing a companion owner are also allied. NPC allies cannot damage each
other, including through an NPC's projectile. Player faction membership is not
assigned: player reactions use opinion scores separately.

Faction configuration loads at server startup. `/npc reload` reloads it without
changing scores. An invalid reload retains the last valid relationships; invalid
configuration at startup prevents the server from starting with unintended rules.

## NPC templates

Faction and opinion configuration is JSON-only for now; there is no Faction tab
in the NPC editor. Edit `social` in the NPC template for affiliation, opinion
source, hostility, autonomous combat and discount tiers. Edit global definitions
and relationships in `factions.json`. Saving appearance/combat settings from the
editor leaves the existing `social` object unchanged, including extension fields.
Apply reloads the saved template onto this NPC. Actual player scores are not
changed by editing template settings.

Add this optional object to an NPC template:

```json
"social": {
  "faction": "wardens",
  "combat": true,
  "opinion": {
    "scope": "npc",
    "hostile_at": -50,
    "discounts": [{"min":50,"percent":10},{"min":80,"percent":20}]
  }
}
```

`scope: npc` stores a separate opinion for each NPC UUID. Two NPCs spawned from
the same template do not share personal opinion. `scope: faction` shares the
score with the template's faction, and requires a nonempty faction. Alternatively
`key: captain` uses an explicit shared subject; omit `scope` when using `key`.
Omitting all reference fields uses personal opinion. Explicit `faction: wardens`
inside `opinion` is also supported for a fixed faction subject.

`hostile_at` is inclusive: -50 means attack at -50 or below. Omitting it disables
opinion hostility, even at -100. The largest eligible discount is used; tiers do
not stack. Percent accepts 0-90, min accepts -100..100. Omitting discounts means
no discount, regardless of opinion.

`social.combat: true` opts into autonomous guarding, including while the NPC's
companion order is idle, and enables combat when the template is applied. The
normal combat policy and targeting restrictions still apply: use `policy: hostile`
for proactive faction/opinion attacks. Passive policy suppresses attacks. The
runtime combat toggle still disables combat. Creative/spectator players and
scoreboard allies are never selected. Low opinion may make even the companion's
owner a valid enemy if these rules explicitly allow it.

Personal/faction scores, NPC affiliation and social settings survive restarts.
Reapply a changed template to update existing NPC settings; doing so does not
reset any player's score. Faction IDs and shared keys use lowercase letters,
digits, underscores, periods, hyphens and colons, up to 80 characters.

## Explicit dialogue effects

Successful quest choice:

```json
"opinion_changes": [{"amount":20}]
```

Failed quest choice:

```json
"opinion_changes": [{"amount":-30},{"faction":"wardens","amount":-10}]
```

Set an exact value:

```json
"opinion_changes": [{"set":80}]
```

Each entry accepts either `amount` or `set`, never both. `amount` accepts whole
numbers -100000..100000 and saturates at -100/100; `set` accepts -100..100. There
may be at most 32 changes per choice. Without reference fields an effect uses the
current NPC's configured opinion subject. `scope: npc` forces personal opinion;
`scope: faction` selects the current NPC's faction. `faction: wardens` or
`key: captain` selects an explicit shared subject, even for non-NPC dialogue
entities. Use only one of scope, faction or key per reference.

Effects execute only after server condition/event checks and successful required
item removal, before dialogue commands and rewards. Cancellation or failed
requirements do not change scores. Use flags/conditions to prevent a quest reward
choice from being repeated: opinion changes deliberately have no automatic
once-per-quest rule. Completing a quest in another mod has no implicit effect.

## Dialogue conditions

```json
"condition": {"type":"opinion","min":80}
```

```json
"condition": {"type":"opinion","faction":"wardens","min":50,"max":100}
```

Opinion conditions support the same reference fields as effects and compose with
`all`, `any` and `not`. Bounds are inclusive and must lie within -100..100. A missing
score is 0. `npc_faction` with `id: wardens` tests the dialogue NPC's affiliation.
After an opinion-changing choice, available choices refresh without resetting the
dialogue history. Checks are always repeated on the server before rewards.

## Prices

Discounts apply to cost items, not rewards, in all shops opened through that NPC.
Cost per offer is rounded up, with a minimum of one item per cost entry, then
multiplied by the requested quantity. Both display and server payment use the
same calculation. Example: 10 emeralds becomes 8 at 20%; buying 3 costs 24.
If opinion or the underlying offer changes after opening the shop, the server
refreshes the shop without taking items; the player must confirm again.
Quotes expire after five minutes and are cleared on disconnect/reload.

## Testing

Copy the example dialogue, trade and two templates into their corresponding
`config/adm-dialogues` folders, and install the faction file. Restart with the
updated JAR. Matching client/server builds are required (network protocol 6).

```mcfunction
/npc spawn "Opinion Warden" opinion_warden
/npc opinion @s get
/npc opinion @s set 80
```

The opinion command uses the selected/looked-at NPC's configured subject and the
existing administrator permission level. It also supports `add -20`. At 80 the
special assignment becomes available and the example shop costs 8 instead of 10.
At -50 the warden may attack survival players. Spawn `opinion_bandit` nearby to
test faction hostility. Protect an NPC with invulnerability if needed while
testing menus. The supplied quest resolves only once, with explicit success and
failure choices; replay prevention is handled by flags in its JSON.
