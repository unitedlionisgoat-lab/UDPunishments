package com.example.udpunishments;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class UDPunishmentsPlugin extends JavaPlugin implements Listener {
    private File logFolder;
    private File recordsFile;
    private YamlConfiguration records;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadRecords();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveRecords();
    }

    private void loadRecords() {
        String folderName = getConfig().getString("settings.log-folder", "Log auf Punishments");
        logFolder = new File(getDataFolder(), folderName);
        if (!logFolder.exists() && !logFolder.mkdirs()) {
            getLogger().warning("Could not create log folder: " + logFolder.getAbsolutePath());
        }
        recordsFile = new File(logFolder, "punishments.yml");
        records = YamlConfiguration.loadConfiguration(recordsFile);
    }

    private void saveRecords() {
        if (records == null || recordsFile == null) {
            return;
        }
        try {
            records.save(recordsFile);
        } catch (IOException exception) {
            getLogger().warning("Could not save punishment records: " + exception.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("punishreload")) {
            return reloadCommand(sender);
        }
        if (name.equals("punishhistory")) {
            return historyCommand(sender, args);
        }
        if (name.equals("punishunmute")) {
            return unmuteCommand(sender, args);
        }
        if (name.equals("punishban")) {
            return banCommand(sender, args, false);
        }
        if (name.equals("punishipban")) {
            return banCommand(sender, args, true);
        }
        if (name.equals("punishkick")) {
            return kickCommand(sender, args);
        }
        if (name.equals("punishmute")) {
            return muteCommand(sender, args);
        }
        if (name.equals("punishwarn")) {
            return warnCommand(sender, args);
        }
        return false;
    }

    private boolean reloadCommand(CommandSender sender) {
        if (!has(sender, "udpunishments.reload")) {
            return deny(sender);
        }
        reloadConfig();
        loadRecords();
        send(sender, msg("messages.reloaded"));
        return true;
    }

    private boolean historyCommand(CommandSender sender, String[] args) {
        if (!has(sender, "udpunishments.history")) {
            return deny(sender);
        }
        if (args.length < 1) {
            send(sender, color("&cUsage: /punishhistory <player>"));
            return true;
        }
        String target = args[0].toLowerCase(Locale.ROOT);
        ConfigurationSection section = records.getConfigurationSection("records");
        if (section == null) {
            send(sender, format(msg("messages.history-empty"), args[0], sender.getName(), "", "Permanent", "Never"));
            return true;
        }
        int shown = 0;
        for (String id : section.getKeys(false)) {
            String path = "records." + id + ".";
            if (!target.equals(records.getString(path + "target-key", "").toLowerCase(Locale.ROOT))) {
                continue;
            }
            String line = msg("messages.history-line")
                    .replace("{id}", id)
                    .replace("{type}", records.getString(path + "type", "UNKNOWN"))
                    .replace("{staff}", records.getString(path + "staff", "Console"))
                    .replace("{reason}", records.getString(path + "reason", "No reason"))
                    .replace("{created}", records.getString(path + "created", "Unknown"));
            send(sender, color(line));
            shown++;
            if (shown >= 10) {
                break;
            }
        }
        if (shown == 0) {
            send(sender, format(msg("messages.history-empty"), args[0], sender.getName(), "", "Permanent", "Never"));
        }
        return true;
    }

    private boolean unmuteCommand(CommandSender sender, String[] args) {
        if (!has(sender, "udpunishments.mute")) {
            return deny(sender);
        }
        if (args.length < 1) {
            send(sender, color("&cUsage: /punishunmute <player>"));
            return true;
        }
        String key = args[0].toLowerCase(Locale.ROOT);
        if (!records.contains("mutes." + key)) {
            send(sender, color(msg("messages.not-muted").replace("{player}", args[0])));
            return true;
        }
        records.set("mutes." + key, null);
        saveRecords();
        send(sender, color(msg("messages.unmuted").replace("{player}", args[0])));
        return true;
    }

    private boolean banCommand(CommandSender sender, String[] args, boolean ipBan) {
        if (!has(sender, ipBan ? "udpunishments.ipban" : "udpunishments.ban")) {
            return deny(sender);
        }
        if (args.length < 3) {
            send(sender, msg(ipBan ? "messages.usage-ipban" : "messages.usage-ban"));
            return true;
        }
        String targetInput = args[0];
        DurationSpec duration = DurationSpec.parse(args[1]);
        ReasonSpec reason = reasonFrom(args, 2);
        Date expires = duration.expiresAt();
        String staff = sender.getName();
        String type = ipBan ? "IP_BAN" : "BAN";

        Player online = Bukkit.getPlayerExact(targetInput);
        String targetName = online != null ? online.getName() : targetInput;
        String banTarget = targetName;
        if (ipBan) {
            banTarget = resolveIpTarget(targetInput, online);
        }

        Bukkit.getBanList(ipBan ? BanList.Type.IP : BanList.Type.NAME)
                .addBan(banTarget, reason.reason, expires, staff);

        record(type, targetName, targetName.toLowerCase(Locale.ROOT), staff, reason.reason, duration.display(), expires, reason.preset);
        broadcast(reason, type, targetName, staff, duration.display(), expires);
        if (online != null) {
            online.kickPlayer(joinLines(targetLines(reason.preset, ipBan ? "punishment-messages.ipban-kick" : "punishment-messages.ban-kick"),
                    targetName, staff, reason.reason, duration.display(), expires));
        }
        return true;
    }

    private boolean kickCommand(CommandSender sender, String[] args) {
        if (!has(sender, "udpunishments.kick")) {
            return deny(sender);
        }
        if (args.length < 2) {
            send(sender, msg("messages.usage-kick"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, msg("messages.player-not-found"));
            return true;
        }
        ReasonSpec reason = reasonFrom(args, 1);
        record("KICK", target.getName(), target.getName().toLowerCase(Locale.ROOT), sender.getName(), reason.reason, "Instant", null, reason.preset);
        broadcast(reason, "KICK", target.getName(), sender.getName(), "Instant", null);
        target.kickPlayer(joinLines(getConfig().getStringList("punishment-messages.kick"),
                target.getName(), sender.getName(), reason.reason, "Instant", null));
        return true;
    }

    private boolean muteCommand(CommandSender sender, String[] args) {
        if (!has(sender, "udpunishments.mute")) {
            return deny(sender);
        }
        if (args.length < 3) {
            send(sender, msg("messages.usage-mute"));
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        String targetName = offline.getName() != null ? offline.getName() : args[0];
        String key = targetName.toLowerCase(Locale.ROOT);
        DurationSpec duration = DurationSpec.parse(args[1]);
        ReasonSpec reason = reasonFrom(args, 2);
        Date expires = duration.expiresAt();

        records.set("mutes." + key + ".player", targetName);
        records.set("mutes." + key + ".uuid", uuidString(offline));
        records.set("mutes." + key + ".reason", reason.reason);
        records.set("mutes." + key + ".staff", sender.getName());
        records.set("mutes." + key + ".created", now());
        records.set("mutes." + key + ".expires", expires == null ? -1L : expires.getTime());
        records.set("mutes." + key + ".preset", reason.preset);

        record("MUTE", targetName, key, sender.getName(), reason.reason, duration.display(), expires, reason.preset);
        saveRecords();
        broadcast(reason, "MUTE", targetName, sender.getName(), duration.display(), expires);
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            for (String line : getConfig().getStringList("punishment-messages.mute-target")) {
                send(online, format(line, targetName, sender.getName(), reason.reason, duration.display(), expiresText(expires)));
            }
        }
        return true;
    }

    private boolean warnCommand(CommandSender sender, String[] args) {
        if (!has(sender, "udpunishments.warn")) {
            return deny(sender);
        }
        if (args.length < 2) {
            send(sender, msg("messages.usage-warn"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, msg("messages.player-not-found"));
            return true;
        }
        ReasonSpec reason = reasonFrom(args, 1);
        record("WARNING", target.getName(), target.getName().toLowerCase(Locale.ROOT), sender.getName(), reason.reason, "Instant", null, reason.preset);
        broadcast(reason, "WARNING", target.getName(), sender.getName(), "Instant", null);
        for (String line : getConfig().getStringList("warning.chat")) {
            send(target, format(line, target.getName(), sender.getName(), reason.reason, "Instant", "Never"));
        }
        sendTitle(target,
                format(getConfig().getString("warning.title", "&cWARNING"), target.getName(), sender.getName(), reason.reason, "Instant", "Never"),
                format(getConfig().getString("warning.subtitle", "&f{reason}"), target.getName(), sender.getName(), reason.reason, "Instant", "Never"),
                getConfig().getInt("warning.fade-in", 5),
                getConfig().getInt("warning.stay", 45),
                getConfig().getInt("warning.fade-out", 10));
        return true;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Mute mute = getMute(event.getPlayer().getName());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        send(event.getPlayer(), format(msg("messages.muted-chat"), event.getPlayer().getName(), mute.staff, mute.reason, mute.durationText(), mute.expiresText()));
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Mute mute = getMute(event.getPlayer().getName());
        if (mute == null) {
            return;
        }
        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        for (String blocked : getConfig().getStringList("settings.block-muted-commands")) {
            if (command.equals(blocked.toLowerCase(Locale.ROOT))) {
                event.setCancelled(true);
                send(event.getPlayer(), format(msg("messages.muted-chat"), event.getPlayer().getName(), mute.staff, mute.reason, mute.durationText(), mute.expiresText()));
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        getMute(event.getPlayer().getName());
    }

    private Mute getMute(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        String path = "mutes." + key + ".";
        if (!records.contains(path + "reason")) {
            return null;
        }
        long expires = records.getLong(path + "expires", -1L);
        if (expires > 0L && System.currentTimeMillis() > expires) {
            records.set("mutes." + key, null);
            saveRecords();
            return null;
        }
        return new Mute(records.getString(path + "reason", "No reason"),
                records.getString(path + "staff", "Console"),
                expires);
    }

    private void record(String type, String target, String targetKey, String staff, String reason, String duration, Date expires, String preset) {
        int id = records.getInt("next-id", 1);
        String path = "records." + id + ".";
        records.set(path + "type", type);
        records.set(path + "target", target);
        records.set(path + "target-key", targetKey);
        records.set(path + "staff", staff);
        records.set(path + "reason", reason);
        records.set(path + "duration", duration);
        records.set(path + "expires", expiresText(expires));
        records.set(path + "preset", preset);
        records.set(path + "created", now());
        records.set("next-id", id + 1);
        saveRecords();
    }

    private void broadcast(ReasonSpec reason, String type, String target, String staff, String duration, Date expires) {
        if (!getConfig().getBoolean("settings.broadcast-punishments", true)) {
            return;
        }
        String template = getConfig().getString("presets." + reason.preset + ".broadcast",
                getConfig().getString("presets.default.broadcast", "&c{player} was punished."));
        Bukkit.broadcastMessage(format(template, target, staff, reason.reason, duration, expiresText(expires)).replace("{type}", type));
    }

    private ReasonSpec reasonFrom(String[] args, int start) {
        String preset = getConfig().getString("settings.default-preset", "default");
        if (args.length > start && getConfig().isConfigurationSection("presets." + args[start].toLowerCase(Locale.ROOT))) {
            preset = args[start].toLowerCase(Locale.ROOT);
            if (args.length > start + 1) {
                return new ReasonSpec(preset, join(args, start + 1));
            }
            return new ReasonSpec(preset, getConfig().getString("presets." + preset + ".reason", "No reason"));
        }
        return new ReasonSpec(preset, join(args, start));
    }

    private String resolveIpTarget(String input, Player online) {
        if (online != null) {
            InetSocketAddress address = online.getAddress();
            if (address != null && address.getAddress() != null) {
                return address.getAddress().getHostAddress();
            }
        }
        return input;
    }

    private boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender.hasPermission("udpunishments.admin");
    }

    private boolean deny(CommandSender sender) {
        send(sender, msg("messages.no-permission"));
        return true;
    }

    private String msg(String path) {
        return getConfig().getString(path, "");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    private String format(String message, String player, String staff, String reason, String duration, String expires) {
        return color(message)
                .replace("{player}", player)
                .replace("{staff}", staff)
                .replace("{reason}", reason)
                .replace("{duration}", duration)
                .replace("{expires}", expires);
    }

    private String joinLines(List<String> lines, String player, String staff, String reason, String duration, Date expires) {
        List<String> output = new ArrayList<>();
        for (String line : lines) {
            output.add(format(line, player, staff, reason, duration, expiresText(expires)));
        }
        return String.join("\n", output);
    }

    private List<String> targetLines(String preset, String fallbackPath) {
        List<String> presetLines = getConfig().getStringList("presets." + preset + ".target-message");
        if (!presetLines.isEmpty()) {
            return presetLines;
        }
        return getConfig().getStringList(fallbackPath);
    }

    private String expiresText(Date expires) {
        return expires == null ? "Never" : dateFormat.format(expires);
    }

    private String now() {
        return dateFormat.format(new Date());
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.length() == 0 ? "No reason" : builder.toString();
    }

    private String uuidString(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        return uuid == null ? "" : uuid.toString();
    }

    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Method method = player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            method.invoke(player, color(title), color(subtitle), fadeIn, stay, fadeOut);
        } catch (Exception ignored) {
            send(player, title);
            if (subtitle != null && !subtitle.isEmpty()) {
                send(player, subtitle);
            }
        }
    }

    private static final class ReasonSpec {
        private final String preset;
        private final String reason;

        private ReasonSpec(String preset, String reason) {
            this.preset = preset;
            this.reason = reason;
        }
    }

    private final class Mute {
        private final String reason;
        private final String staff;
        private final long expires;

        private Mute(String reason, String staff, long expires) {
            this.reason = reason;
            this.staff = staff;
            this.expires = expires;
        }

        private String expiresText() {
            return expires <= 0L ? "Never" : dateFormat.format(new Date(expires));
        }

        private String durationText() {
            if (expires <= 0L) {
                return "Permanent";
            }
            long millis = Math.max(0L, expires - System.currentTimeMillis());
            long minutes = Math.max(1L, millis / 60000L);
            return minutes + "m remaining";
        }
    }

    private static final class DurationSpec {
        private final boolean permanent;
        private final long millis;
        private final String raw;

        private DurationSpec(boolean permanent, long millis, String raw) {
            this.permanent = permanent;
            this.millis = millis;
            this.raw = raw;
        }

        private static DurationSpec parse(String input) {
            String value = input.toLowerCase(Locale.ROOT);
            if (value.equals("perm") || value.equals("permanent") || value.equals("forever")) {
                return new DurationSpec(true, -1L, "Permanent");
            }
            long total = 0L;
            StringBuilder number = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (Character.isDigit(character)) {
                    number.append(character);
                    continue;
                }
                if (number.length() == 0) {
                    continue;
                }
                long amount = Long.parseLong(number.toString());
                number.setLength(0);
                switch (character) {
                    case 'd':
                        total += Duration.ofDays(amount).toMillis();
                        break;
                    case 'h':
                        total += Duration.ofHours(amount).toMillis();
                        break;
                    case 'm':
                        total += Duration.ofMinutes(amount).toMillis();
                        break;
                    case 's':
                        total += Duration.ofSeconds(amount).toMillis();
                        break;
                    default:
                        break;
                }
            }
            if (number.length() > 0) {
                total += Duration.ofMinutes(Long.parseLong(number.toString())).toMillis();
            }
            if (total <= 0L) {
                return new DurationSpec(true, -1L, "Permanent");
            }
            return new DurationSpec(false, total, input);
        }

        private Date expiresAt() {
            return permanent ? null : new Date(System.currentTimeMillis() + millis);
        }

        private String display() {
            return raw;
        }
    }
}
