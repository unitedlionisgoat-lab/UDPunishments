# UDPunishments

Custom punishment plugin for Spigot, Paper, Purpur, and Folia-style servers.

## Install

1. Put `target/UDPunishments-1.0.0.jar` into your server `plugins` folder.
2. Restart the server.
3. Edit `plugins/UDPunishments/config.yml` to change broadcasts, presets, warning title text, and punishment messages.
4. Run `/punishreload` after config changes.

## Commands

- `/punishban <player> <duration|perm> <preset|reason...>`
- `/punishipban <player|ip> <duration|perm> <preset|reason...>`
- `/punishkick <player> <preset|reason...>`
- `/punishmute <player> <duration|perm> <preset|reason...>`
- `/punishunmute <player>`
- `/punishwarn <player> <preset|reason...>`
- `/punishhistory <player>`
- `/punishreload`

Aliases are also included, such as `/pban`, `/pipban`, `/pkick`, `/pmute`, `/pwarn`, and `/phistory`.

## Durations

Use `perm`, `permanent`, or `forever` for permanent punishments.

Timed examples:

- `30m`
- `2h`
- `7d`
- `1d12h30m`

## Presets

Presets live in `config.yml` under `presets`.

Example:

```yaml
presets:
  cheating:
    reason: "Unfair advantage"
    broadcast: "&8[&cAnti-Cheat&8] &f{player} &7was removed by &f{staff}&7 for &c{reason}&7."
    target-message:
      - "&cYou are banned from this server."
      - "&7Reason: &f{reason}"
      - "&7Staff: &f{staff}"
      - "&7Expires: &f{expires}"
```

If a preset name is used as the reason argument, the plugin uses that preset. Extra words after the preset override the preset reason.

Example:

```text
/punishban Steve 7d cheating X-Ray mining
```

## Logs

All punishments are recorded in:

```text
plugins/UDPunishments/Log auf Punishments/punishments.yml
```

Mute records are also stored there so they survive restarts.

## Permissions

- `udpunishments.admin`
- `udpunishments.ban`
- `udpunishments.ipban`
- `udpunishments.kick`
- `udpunishments.mute`
- `udpunishments.warn`
- `udpunishments.history`
- `udpunishments.reload`

## Compatibility

The plugin is compiled with Java 8 bytecode so it can run on Java 21 servers while staying compatible with many older Bukkit-family versions. It avoids Paper-only APIs and declares `folia-supported: true`; it does not use schedulers or async entity access.

Very old servers running outdated Java or heavily modified ban APIs may still need version-specific testing.
