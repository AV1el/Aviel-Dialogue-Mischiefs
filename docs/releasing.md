# Publishing ADM 0.9.0

This checklist does not publish anything automatically. The local release bundle
is ignored by Git; source changes and release notes are committed separately.

## Files and version metadata

- Upload file: `release/0.9.0/adm-0.9.0.jar`.
- SHA-256 checksum: `release/0.9.0/SHA256SUMS.txt`.
- Changelog: [0.9.0 release notes](releases/0.9.0.md), also copied into the bundle.
- Version number: `0.9.0`.
- Display name: `ADM 0.9.0 - Minecraft 1.21.1 (NeoForge)`.
- Minecraft version: **1.21.1 only**.
- Loader: **NeoForge**, minimum **21.1.228**.
- Java: **21**.
- Environment: **client and server**, with the same updated build installed on both.
- External mod dependencies: none; do not list AME as a required dependency.
- Recommended release channel: **Beta**, pending manual gameplay/server testing.

Do not upload an old JAR from `build/libs`, the experimental `hybrid` directory,
or a multiversion build. This release does not advertise support for other
Minecraft versions or for Forge/Fabric.

Before publishing, back up a test world and NPC templates. Check editor tabs,
template save/apply, melee reach/animations/sounds, following and guarding,
JSON factions/opinions/discounts, palette apply/reset and world reload. Repeat
the relevant interactions with a separate client on a dedicated server. Promote
to Release only after these manual checks pass.

## GitHub: main branch

The checkout tracks remote `mischiefs`, pointing to
[AV1el/Aviel-Dialogue-Mischiefs](https://github.com/AV1el/Aviel-Dialogue-Mischiefs).
Remote `origin` points to the older `ADM---Aviel-Dialogue-Mod` repository; do not
substitute it accidentally. These commands keep the existing author identity and
do not add any co-author trailers.

Run in PowerShell from `D:\AvielMods\AvielDialogueMod`:

```powershell
git branch --show-current
git remote get-url mischiefs
git fetch mischiefs
git log --oneline HEAD..mischiefs/main
```

The branch must be `main`. If the last command shows newer remote commits,
reconcile them before continuing; do not force-push or discard local changes.
Review all staged files before committing, including changes from earlier work.

```powershell
git config user.name "Av1el"
git config user.email "dsartlol4@gmail.com"
git add .gitignore README.md gradle.properties src docs
git diff --cached --stat
git diff --cached --check
git commit -m "Release 0.9.0: NPC companions, editor and dialogue palettes"
git push mischiefs main
```

The explicit staging scope excludes `hybrid`, build output and local release
files. No branch, commit, tag, push or release is created by preparing this bundle.

Optional GitHub prerelease after pushing and checking the GitHub build:

```powershell
gh release create v0.9.0 release/0.9.0/adm-0.9.0.jar release/0.9.0/SHA256SUMS.txt --repo AV1el/Aviel-Dialogue-Mischiefs --target main --title "ADM 0.9.0 - Minecraft 1.21.1 (NeoForge)" --notes-file docs/releases/0.9.0.md --prerelease
```

If the version/tag already exists, inspect it before doing anything else. Do not
overwrite a published artifact or force-move an existing tag.

## Modrinth

Open your existing [ADM project](https://modrinth.com/mod/aviel-dialogue-mod),
then its version-management page and create a new version. Upload the JAR above,
set the version number/display name, select **1.21.1**, **NeoForge** and **Beta**,
and use the release notes as the changelog. Preserve the project's client/server
support. Check that `0.9.0` does not already exist before publishing.

Only the runtime JAR is needed; do not upload the release folder as a ZIP.
See [Modrinth's version metadata reference](https://docs.modrinth.com/api/operations/createversion/).

## CurseForge

Open [ADM on CurseForge](https://www.curseforge.com/minecraft/mc-mods/aviel-dialogue-mischiefs)
in the author dashboard (project ID **1616745**). Add a file to the existing
project, upload the same JAR, set the display name above, choose **Beta**,
**Minecraft 1.21.1**, **NeoForge**, **Java 21** and both client/server environments
where those selectors are available. Paste the same changelog and submit.

Check the file's review/publication state afterward; submission is not proof of
public availability. Do not remove the previous working release.
See the official [file submission instructions](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).

## Rebuilding the bundle

```powershell
.\gradlew.bat test build --console=plain
New-Item -ItemType Directory -Path release/0.9.0 -Force
Copy-Item -LiteralPath build/libs/adm-0.9.0.jar -Destination release/0.9.0/adm-0.9.0.jar -Force
Copy-Item -LiteralPath docs/releases/0.9.0.md -Destination release/0.9.0/CHANGELOG.md -Force
$hash = (Get-FileHash -LiteralPath release/0.9.0/adm-0.9.0.jar -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath release/0.9.0/SHA256SUMS.txt -Value "$hash  adm-0.9.0.jar" -Encoding ascii
```

Use a successful build's artifact and verify that its checksum matches the
bundle. A successful build/test run does not replace the gameplay checklist.
