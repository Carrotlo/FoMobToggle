# FoMobToggle

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fomobtoggle) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fomobtoggle/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Personal GUI controls for nearby natural mob spawning.

*Developed by Carrotio.*

## Features

- Lets players control nearby natural mob spawning through a GUI.
- Supports individual mobs plus broad monster and passive categories.
- Uses permission-based final overrides for server policy.
- Provides configurable spawn-check radius and menu behavior.
- Includes administrator reload, version, and editor controls.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fomobtoggle). Check the [versions page](https://modrinth.com/plugin/fomobtoggle/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** None

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fomobtoggle` — Opens the FoMobToggle menu. Aliases: `/mobtoggle`, `/togglemob`, `/togglemobs`.
- `/fomobtoggleadmin <version|reload|editor>` — Manages FoMobToggle and checks for updates. Aliases: `/mobtoggleadmin`.

### Permissions

- `fomobtoggle.use` — Allows the player to open the FoMobToggle GUI. Default: `true`.
- `fomobtoggle.admin` — Receives FoMobToggle update notices and accesses admin features. Default: `op`.
- `fomobtoggle.spawn.mob` — Final override for all mob spawning around the player. Default: `false`.
- `fomobtoggle.spawn.monster` — Final override for monster spawning around the player. Default: `false`.
- `fomobtoggle.spawn.passive` — Final override for passive mob spawning around the player. Default: `false`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `guis/*.yml` — Public GUI layout in rows, button slots, icons, and lore. GUI state labels are in `messages.yml`.

The bundled `guis/toggle-menu.yml` is generated from `scripts/toggle-menu.json` with FoPluginCore 1.8's `scripts/generate_public_gui.py`. Generate it during development; the server uses the installed YAML and never runs the script.

Main config areas: `spawn-check-radius`, `gui`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

FoMobToggle 1.8 provides a plugin-owned Java API in `me.foesio.foMobToggle.api` and one Bukkit event in
`me.foesio.foMobToggle.api.event`. Compile against the matching FoMobToggle jar as a **provided** dependency;
do not shade it into a consumer. For a local Maven build, install the jar locally with
`mvn install:install-file -Dfile=/path/to/FoMobToggle-1.8.jar -DgroupId=me.foesio -DartifactId=FoMobToggle -Dversion=1.8 -Dpackaging=jar`,
then use `me.foesio:FoMobToggle:1.8` with `<scope>provided</scope>`. This is a local coordinate, not a claim
that the jar is published to a Maven repository. Consumers also need their normal provided Bukkit/Spigot API.

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoMobToggle</artifactId>
  <version>1.8</version>
  <scope>provided</scope>
</dependency>
```

For `plugin.yml`, use `depend: [FoMobToggle]` when required. For an optional integration, use
`softdepend: [FoMobToggle]`, check that the provider plugin is enabled, then load a **separate integration class**
that directly references FoMobToggle types. This keeps the consumer loadable without FoMobToggle:

```java
// In the consumer's onEnable, after checking that FoMobToggle is enabled:
FoMobToggleApi api = Bukkit.getServicesManager().load(FoMobToggleApi.class);
if (api != null) {
    ToggleState state = api.getState(player, ToggleCategory.MONSTERS);
    boolean monstersEnabled = state.effectiveEnabled();
    SetPreferenceResult result = api.setPreference(player, ToggleCategory.MONSTERS,
            !state.savedPreference());
    SpawnDecision preview = api.evaluateSpawn(location, EntityType.ZOMBIE,
            CreatureSpawnEvent.SpawnReason.NATURAL);
}
```

`getState` returns an immutable snapshot for an online player: `savedPreference` is the current in-memory
preference, `permissionLocked` means this category's permission node is explicitly set, and
`effectiveEnabled` includes permission overrides and the `ALL_MOBS` parent gate. A disabled public GUI does
not change the spawn policy; stored preferences remain active. `setPreference` follows the GUI's permission
lock. It returns `CHANGED`, `UNCHANGED`, or `LOCKED`; `CHANGED` schedules the normal delayed SQLite write.
It does not confirm disk persistence. Null arguments are invalid, and the state/write methods reject offline
players. A stored service reference throws after provider disable; reacquire it after a server/plugin restart.

`evaluateSpawn` returns `IGNORED` for reasons other than `NATURAL` and `CHUNK_GEN`, or when the entity type's
class is unavailable or is not a mob.
For applicable mobs, it checks players in the configured cuboid radius and returns `BLOCKED` if any one of them
blocks the spawn. It uses `EntityType.getEntityClass()` to classify monsters and passive mobs before spawn;
unclassified mobs are controlled by `ALL_MOBS` only. `ALLOWED` is FoMobToggle's decision, not a guarantee that
another plugin will allow the spawn. The location must have a world. The live listener uses the same policy
with the actual mob instance.

`MobTogglePreferenceChangedEvent` fires after a successful in-memory change from either the GUI (`GUI`) or API
(`API`), before its delayed database write. It is synchronous and non-cancellable. It carries the player UUID,
category, old and new saved preferences, and cause. It does not fire for no-ops, permission-locked requests,
permission changes, startup loading, reloads, or database writes. Its values do not claim the effective spawn
state changed. Register a normal Bukkit listener after the provider is available.

Call API methods from the relevant player or location's owning server thread/region, never from an async task.
The event runs in that same caller context; listeners must follow the server's entity and world access rules.
FoMobToggle does not move calls across threads. A first state lookup may read SQLite, so avoid repeatedly querying
uncached players on a hot path.

</details>
