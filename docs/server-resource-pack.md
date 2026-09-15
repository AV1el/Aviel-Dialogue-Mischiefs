# Server NPC resources

ADM can export server skins, sounds and language assets and offer them to joining
players through Minecraft's resource-pack protocol. Dialogues, quest evaluation
and trades stay on the server. Install matching ADM and Minecraft versions on both
sides. This feature is for dedicated servers.

## Built-in hosting (recommended)

Set these values in `config/adm-common.toml`:

```toml
assetHttpEnabled = true
assetHttpBind = "0.0.0.0"
assetHttpPort = 25566
assetHttpPublicUrl = "http://play.example.com:25566"
resourcePackRequired = false
```

Replace the example hostname with your server's public hostname or IP. Open/forward
TCP port 25566 in your firewall/router or request it from your hosting provider.
The public URL must be reachable from players' computers; do not use localhost.
The built-in listener serves HTTP. HTTPS requires a reverse proxy terminating TLS.
Restart once after configuring the listener. Built-in hosting takes precedence
over `resourcePackUrl`.

Add files in the skins, sounds and lang folders described below, then run:

```text
/npc assets reload
```

ADM rebuilds and publishes the ZIP and offers the new pack to all connected
players, without requiring reconnects. Joining players receive the latest pack.
The command uses the same permission level as other `/npc` commands and also works
from the server console. It reports publication, not confirmation that every
player accepted and loaded the pack. Minecraft still controls player consent.
The pack uses a stable ID so updates replace the previous ADM pack.

The listener exposes only published ZIP snapshots, never a directory listing.
Compressed packs are limited to 64 MiB; the current and previous snapshots are
retained in memory to allow downloads during updates. A failed rebuild retains the
previous published pack. Building runs on the server thread, so large resource
collections can briefly pause ticks; run the command after finishing file uploads.
The listener shuts down with the server. `/npc reload` still reloads dialogue
caches; use `/npc assets reload` to publish resource changes.

## External hosting

1. Put NPC PNG files in `config/adm-dialogues/skins`, OGG files in
   `config/adm-dialogues/sounds`, and resource language JSON in
   `config/adm-dialogues/lang`. Dialogue translations remain server dialogue data.
2. In `config/adm-common.toml`, set:

   ```toml
   resourcePackUrl = "https://example.com/minecraft/adm-server-assets.zip"
   resourcePackRequired = false
   ```

3. Restart the server. ADM logs the exported ZIP path and its SHA-1. Upload that
   exact ZIP to the configured URL before players connect. Serve the ZIP directly,
   without a login or HTML download page. Leave `assetHttpEnabled = false` for this mode.
4. A joining player receives Minecraft's normal resource-pack offer. Minecraft
   handles consent, downloading, hash verification, caching and load-status replies.
   With `resourcePackRequired = true`, vanilla handles refusal/load failure by
   disconnecting the player. Otherwise gameplay continues without those assets.

After changing resources, restart and publish the newly exported ZIP. Its hash and
pack ID change with its contents. Existing players should reconnect. Do not alter
or recompress the published ZIP: its bytes must match the server's SHA-1.
An invalid URL or export failure prevents startup when delivery is configured.
An empty URL disables external delivery. Delivery does not block NPC rendering while an
optional pack loads; missing textures may be visible before it finishes.

## Resource locations

For `skins/merchant.png`, an NPC can use `merchant.png` or
`adm:textures/entity/npc/merchant.png`. The generated pack includes
`assets/adm/textures/entity/npc/merchant.png`.

Existing server resource packs can instead provide resources under their own
namespace, such as `myserver:textures/entity/npc/merchant.png`. Use Minecraft's
`server.properties` resource-pack settings for that workflow and leave ADM's URL
empty. ADM does not need a separate downloader or a custom client cache.

Server packs are removed by Minecraft on disconnect. Keep shared modpack assets
in a distinct namespace to avoid collisions. Each Minecraft version needs a pack
exported by its matching ADM build because pack metadata differs between versions.
