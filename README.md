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

This plugin does not expose a public developer API.

</details>
