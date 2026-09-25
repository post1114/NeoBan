# NeoBan

[English](README.md) | [简体中文](README.zh-CN.md)

Minecraft ban / mute / kick plugin with a built-in appeal system, built for both **Paper 1.8.8** and **Paper 26.3** from a single codebase. Two independent jars are produced, each targeting one server version.

Banned players are sent to a dedicated **appeal world** (a plugin-generated void world, or an existing world of your choice) where they are fully restricted and can only run the allowed commands — most importantly `/appeal <reason>`.

## Features

- Permanent and temporary **bans** (`/ban`, `/tempban`) and **mutes** (`/mute`, `/tempmute`)
- **Kick** players (`/kick`)
- **Appeal system**: players submit an appeal with a reason; admins review with `/appeal list|view|accept|deny`
- Banned players are confined to a configurable **appeal world** on join, with their previous location saved and restored
- Restrictions inside the appeal world: no block break/place, no interactions, no inventory access, no item drops, no damage, no chat, no commands other than the allow-list, no leaving the world
- Player visibility is synced so appeal-world players and normal players do not see each other
- Muted players cannot chat but can still appeal (they are not moved)
- Appeal **limits** and **cooldown** are configurable, counted per punishment or per player lifetime
- Admins with `neoban.appeal.admin` receive pending appeals automatically when they join (offline admins never miss an appeal)
- Appeal accept/deny notifies the player, even if they are offline at decision time
- Durations like `7d`, `1d12h`, `45m`, `30s`, combinations allowed
- YAML storage (no external dependencies): `bans.yml`, `mutes.yml`, `appeals.yml`, `locations.yml`
- All messages live in `config.yml` and are fully customizable (English by default)
- UUID migration: name-keyed punishments are migrated to UUIDs automatically on first join

## Requirements

| Server | Jar | Java |
|---|---|---|
| Paper 1.8.8 | `NeoBan-1.8.8.jar` | Java 8 |
| Paper 26.3 | `NeoBan-26.3.jar` | Java 25+ (Minecraft 26.1 and newer will not start below Java 25) |

## Installation

1. Build (see below) or download the jar matching your server version.
2. Drop it into your server's `plugins/` folder.
3. Start the server — `plugins/NeoBan/config.yml` is created on first run.

## Building from source

The build has two passes because the legacy jar must be compiled against Spigot 1.8.8 (Java 8) while the modern jar is compiled against Paper 26.3 (Java 25).

Requirements: Maven 3.9+, JDK 8, JDK 25.

```powershell
# Pass 1 — neoban-common + legacy jar  (JDK 8)
$env:JAVA_HOME = 'C:\path\to\jdk8'
mvn clean install -pl neoban-common,neoban-v1_8_8

# Pass 2 — compat check + modern jar  (JDK 25)
$env:JAVA_HOME = 'C:\path\to\jdk25'
mvn clean package -pl neoban-compat-check,neoban-v26_3
```

On Linux/macOS use `export JAVA_HOME=...` instead. Always build with `clean`.

Artifacts:

- `neoban-v1_8_8/target/NeoBan-1.8.8.jar`
- `neoban-v26_3/target/NeoBan-26.3.jar`

Module layout:

| Module | Purpose |
|---|---|
| `neoban-common` | All shared logic (compiled with `-source/-target 8`) |
| `neoban-v1_8_8` | Paper 1.8.8 adapter, legacy chat/pickup listeners, void generator |
| `neoban-v26_3` | Paper 26.3 adapter, Adventure chat listener, tab-completion filter, void generator |
| `neoban-compat-check` | Compiles the common sources against Paper 26.3 to catch modern-API breakage |

## Commands

| Command | Permission | Description |
|---|---|---|
| `/ban <player> [reason]` | `neoban.ban` | Permanent ban |
| `/tempban <player> <duration> [reason]` | `neoban.tempban` | Temporary ban, e.g. `7d`, `1d12h`, `45m` |
| `/unban <player>` | `neoban.unban` | Lift a ban |
| `/mute <player> [reason]` | `neoban.mute` | Permanent mute |
| `/tempmute <player> <duration> [reason]` | `neoban.tempmute` | Temporary mute |
| `/unmute <player>` | `neoban.unmute` | Lift a mute |
| `/kick <player> [reason]` | `neoban.kick` | Kick a player |
| `/appeal` | — | Show your appeal status |
| `/appeal <reason>` | — | Submit an appeal for your active punishment |
| `/appeal list` | `neoban.appeal.admin` | List pending appeals |
| `/appeal view <id>` | `neoban.appeal.admin` | View an appeal |
| `/appeal accept <id> [note]` | `neoban.appeal.admin` | Accept an appeal (lifts the punishment) |
| `/appeal deny <id> [note]` | `neoban.appeal.admin` | Deny an appeal |
| `/neoban reload` | `neoban.admin` | Reload config |
| `/neoban info` | `neoban.admin` | Counts of active bans / mutes / pending appeals |
| `/neoban resetappeals <player>` | `neoban.admin` | Reset a player's appeal count and cooldown |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `neoban.ban` / `neoban.tempban` / `neoban.unban` | `op` | Ban commands |
| `neoban.mute` / `neoban.tempmute` / `neoban.unmute` | `op` | Mute commands |
| `neoban.kick` | `op` | Kick |
| `neoban.appeal.admin` | `op` | Review appeals / receive pending-appeal notifications |
| `neoban.admin` | `op` | `/neoban` admin commands |
| `neoban.bypass` | `op` | Ignore appeal-world restrictions |

## Appeal flow

1. A banned player joins and is teleported to the appeal world; their previous location is saved.
2. They run `/appeal <reason>` — limits and cooldown (configurable) are enforced.
3. Online admins with `neoban.appeal.admin` are notified immediately; offline admins receive all pending appeals when they next join.
4. The admin decides: `/appeal accept <id>` lifts the ban and notifies the player (who then returns to their saved location); `/appeal deny <id>` keeps the punishment and notifies the player with the note.

## Configuration (excerpt)

```yaml
appeal:
  max-appeals: 5              # max appeals per punishment (or lifetime)
  cooldown-minutes: 60        # cooldown between appeals
  count-scope: PER_PUNISHMENT # PER_PUNISHMENT | LIFETIME
  allowed-commands:
    - "/appeal"
  world:
    type: VOID                # VOID (generated) | CUSTOM (existing world)
    void-name: NeoBan_AppealVoid
    custom-name: appeal_world

messages:
  prefix: "&8[&cNeoBan&8] &7"
  ban-success: "&aBanned &e{player}&a, duration: &f{duration}&a, reason: &f{reason}"
  # ... every message can be edited or translated here
```

## Data files

All data is stored as UTF-8 YAML in `plugins/NeoBan/`:

| File | Contents |
|---|---|
| `config.yml` | Settings and messages |
| `bans.yml` | Ban records |
| `mutes.yml` | Mute records |
| `appeals.yml` | Appeals and per-player counters |
| `locations.yml` | Saved locations of banned players |

## License

[GPL-3.0](LICENSE)
