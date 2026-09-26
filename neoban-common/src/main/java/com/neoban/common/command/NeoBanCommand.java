package com.neoban.common.command;

import com.neoban.common.NeoBanBase;
import com.neoban.common.config.Settings;
import com.neoban.common.model.Appeal;
import com.neoban.common.model.AppealStatus;
import com.neoban.common.model.IpBan;
import com.neoban.common.model.Punishment;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.manager.AppealManager;
import com.neoban.common.storage.PunishmentStore;
import com.neoban.common.util.Durations;
import com.neoban.common.util.Ips;
import com.neoban.common.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class NeoBanCommand implements CommandExecutor, TabCompleter {

    private final NeoBanBase plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public NeoBanCommand(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String l = command.getName().toLowerCase(Locale.ROOT);
        if ("ban".equals(l)) {
            if (args.length < 1) {
                plugin.send(sender, "usage-ban");
                return true;
            }
            doPunish(sender, args[0], Text.join(args, 1), -1L, PunishmentType.BAN);
        } else if ("tempban".equals(l)) {
            if (args.length < 2) {
                plugin.send(sender, "usage-tempban");
                return true;
            }
            long ms = Durations.parse(args[1]);
            if (ms < 0) {
                plugin.send(sender, "invalid-duration");
                return true;
            }
            doPunish(sender, args[0], Text.join(args, 2), ms, PunishmentType.BAN);
        } else if ("mute".equals(l)) {
            if (args.length < 1) {
                plugin.send(sender, "usage-mute");
                return true;
            }
            doPunish(sender, args[0], Text.join(args, 1), -1L, PunishmentType.MUTE);
        } else if ("tempmute".equals(l)) {
            if (args.length < 2) {
                plugin.send(sender, "usage-tempmute");
                return true;
            }
            long ms = Durations.parse(args[1]);
            if (ms < 0) {
                plugin.send(sender, "invalid-duration");
                return true;
            }
            doPunish(sender, args[0], Text.join(args, 2), ms, PunishmentType.MUTE);
        } else if ("unban".equals(l)) {
            if (args.length < 1) {
                plugin.send(sender, "usage-unban");
                return true;
            }
            doLift(sender, args[0], PunishmentType.BAN);
        } else if ("unmute".equals(l)) {
            if (args.length < 1) {
                plugin.send(sender, "usage-unmute");
                return true;
            }
            doLift(sender, args[0], PunishmentType.MUTE);
        } else if ("kick".equals(l)) {
            doKick(sender, args);
        } else if ("ipban".equals(l)) {
            if (args.length < 1) {
                plugin.send(sender, "usage-ipban");
                return true;
            }
            doIpPunish(sender, args[0], Text.join(args, 1), -1L);
        } else if ("iptempban".equals(l)) {
            if (args.length < 2) {
                plugin.send(sender, "usage-iptempban");
                return true;
            }
            long ms = Durations.parse(args[1]);
            if (ms < 0) {
                plugin.send(sender, "invalid-duration");
                return true;
            }
            doIpPunish(sender, args[0], Text.join(args, 2), ms);
        } else if ("ipunban".equals(l)) {
            if (args.length < 1) {
                plugin.send(sender, "usage-ipunban");
                return true;
            }
            doIpUnban(sender, args[0]);
        } else if ("appeal".equals(l)) {
            doAppeal(sender, args);
        } else if ("neoban".equals(l)) {
            doNeoBan(sender, args);
        }
        return true;
    }

    // ---------- punishment helpers ----------

    private static class Target {
        UUID uuid;
        String name;
        Player online;
    }

    private Target resolve(String name) {
        Target t = new Target();
        t.name = name;
        t.online = plugin.findOnline(null, name);
        if (t.online != null) {
            t.uuid = t.online.getUniqueId();
            t.name = t.online.getName();
            return t;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op.hasPlayedBefore()) {
            t.uuid = op.getUniqueId();
        }
        return t;
    }

    private void doPunish(CommandSender sender, String rawName, String rawReason, long durationMs, PunishmentType type) {
        if (sender instanceof Player && ((Player) sender).getName().equalsIgnoreCase(rawName)) {
            plugin.send(sender, "self-not-allowed");
            return;
        }
        Target t = resolve(rawName);
        String reason = rawReason == null || rawReason.trim().isEmpty()
                ? plugin.formatMessage("reason-default") : Text.color(rawReason.trim());

        PunishmentStore store = type == PunishmentType.BAN ? plugin.banStore() : plugin.muteStore();
        if (store.findActive(t.uuid, t.name) != null) {
            plugin.send(sender, type == PunishmentType.BAN ? "already-banned" : "already-muted");
            return;
        }

        String issuer = sender.getName();
        Punishment p;
        if (type == PunishmentType.BAN) {
            String ip = resolveBanIp(t);
            p = plugin.banManager().ban(t.uuid, t.name, issuer, reason, durationMs, ip);
            if (ip != null) {
                plugin.ipBanManager().checkThreshold(ip);
            }
        } else {
            p = plugin.muteManager().mute(t.uuid, t.name, issuer, reason, durationMs);
        }

        String duration = durationMs <= 0 ? "Permanent" : Durations.format(durationMs);

        if (type == PunishmentType.BAN) {
            if (t.online != null) {
                plugin.send(t.online, "ban-join",
                        "reason", reason, "duration", duration,
                        "world", plugin.appealWorld() != null ? plugin.appealWorld().getName() : "-");
                if (!plugin.inAppealWorld(t.online)) {
                    plugin.locationStore().save(t.online.getUniqueId(), t.online.getLocation());
                    plugin.teleportInternal(t.online, plugin.appealSpawn());
                    plugin.syncVisibilityAround(t.online);
                }
            }
        } else if (t.online != null) {
            plugin.send(t.online, "mute-notify", "reason", reason, "duration", duration);
        }

        plugin.send(sender, type == PunishmentType.BAN ? "ban-success" : "mute-success",
                "player", t.name, "duration", duration, "reason", reason);
        plugin.getLogger().info(issuer + " -> " + type + " " + t.name + " (" + duration + "): " + reason);
    }

    private void doLift(CommandSender sender, String rawName, PunishmentType type) {
        Target t = resolve(rawName);
        boolean removed;
        if (type == PunishmentType.BAN) {
            removed = plugin.banManager().unban(t.uuid, t.name);
        } else {
            removed = plugin.muteManager().unmute(t.uuid, t.name);
        }
        if (!removed) {
            plugin.send(sender, type == PunishmentType.BAN ? "unban-not-found" : "unmute-not-found");
            return;
        }
        plugin.send(sender, type == PunishmentType.BAN ? "unban-success" : "unmute-success", "player", t.name);
        if (type == PunishmentType.BAN) {
            plugin.handleBanLifted(t.uuid, t.name);
        } else if (t.online != null) {
            plugin.send(t.online, "unmute-notify");
        }
    }

    private void doKick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            plugin.send(sender, "usage-kick");
            return;
        }
        Player target = plugin.findOnline(null, args[0]);
        if (target == null) {
            plugin.send(sender, "target-not-found", "player", args[0]);
            return;
        }
        String reason = Text.join(args, 1);
        if (reason.trim().isEmpty()) {
            reason = plugin.formatMessage("kick-default");
        } else {
            reason = Text.color(reason.trim());
        }
        plugin.adapter().kick(target, reason);
        plugin.send(sender, "kick-success", "player", target.getName());
    }

    // ---------- IP punishment helpers ----------

    private String resolveBanIp(Target t) {
        if (t.online != null) {
            return plugin.currentIp(t.online);
        }
        String ip = null;
        if (t.uuid != null) {
            ip = plugin.playerIpStore().findByUuid(t.uuid);
        }
        if (ip == null && t.name != null) {
            ip = plugin.playerIpStore().findByName(t.name);
        }
        return ip;
    }

    private String resolveIpTarget(CommandSender sender, String rawTarget) {
        String ip = Ips.normalize(rawTarget);
        if (ip != null) {
            return ip;
        }
        if (Ips.looksLike(rawTarget)) {
            plugin.send(sender, "invalid-ip", "ip", rawTarget);
            return null;
        }
        Player online = plugin.findOnline(null, rawTarget);
        if (online != null) {
            String onlineIp = plugin.currentIp(online);
            if (onlineIp != null) {
                return onlineIp;
            }
            plugin.send(sender, "target-not-found", "player", rawTarget);
            return null;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(rawTarget);
        String known = null;
        if (op.hasPlayedBefore()) {
            known = plugin.playerIpStore().findByUuid(op.getUniqueId());
        }
        if (known == null) {
            known = plugin.playerIpStore().findByName(rawTarget);
        }
        if (known != null) {
            return known;
        }
        plugin.send(sender, "target-not-found", "player", rawTarget);
        return null;
    }

    private void doIpPunish(CommandSender sender, String rawTarget, String rawReason, long durationMs) {
        String ip = resolveIpTarget(sender, rawTarget);
        if (ip == null) {
            return;
        }
        String reason = rawReason == null || rawReason.trim().isEmpty()
                ? plugin.formatMessage("reason-default") : Text.color(rawReason.trim());
        if (plugin.ipBanStore().findActive(ip) != null) {
            plugin.send(sender, "already-ip-banned");
            return;
        }
        IpBan ban = plugin.ipBanStore().create(ip, sender.getName(), reason, durationMs, false);
        String duration = durationMs <= 0 ? "Permanent" : Durations.format(durationMs);
        Player senderPlayer = sender instanceof Player ? (Player) sender : null;
        int kicked = plugin.ipBanManager().kickPlayersOn(ip, ban, senderPlayer);
        plugin.send(sender, "ipban-success", "ip", ip, "duration", duration, "reason", reason);
        plugin.getLogger().info(sender.getName() + " -> IPBAN " + ip + " (" + duration + "): " + reason
                + (kicked > 0 ? " [kicked " + kicked + " player(s)]" : ""));
    }

    private void doIpUnban(CommandSender sender, String rawIp) {
        String ip = Ips.normalize(rawIp);
        if (ip == null) {
            plugin.send(sender, "invalid-ip", "ip", rawIp);
            return;
        }
        IpBan ban = plugin.ipBanStore().findActive(ip);
        if (ban == null) {
            plugin.send(sender, "ipunban-not-found");
            return;
        }
        plugin.ipBanStore().deactivate(ban);
        plugin.send(sender, "ipunban-success", "ip", ip);
        plugin.getLogger().info(sender.getName() + " -> IPUNBAN " + ip);
    }

    // ---------- appeal ----------

    private void doAppeal(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showAppealStatus(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("view".equals(sub) || "accept".equals(sub) || "deny".equals(sub) || "list".equals(sub)) {
            if (!sender.hasPermission("neoban.appeal.admin")) {
                plugin.send(sender, "no-permission");
                return;
            }
            doAppealAdmin(sender, sub, args);
            return;
        }
        if (!(sender instanceof Player)) {
            plugin.send(sender, "player-only");
            return;
        }
        Player player = (Player) sender;
        String reason = Text.join(args, 0).trim();
        if (reason.isEmpty()) {
            plugin.send(sender, "usage-appeal");
            return;
        }
        AppealManager.SubmitResult result = plugin.appealManager().submit(player, reason);
        switch (result) {
            case OK:
                Appeal newest = latestAppeal(player);
                plugin.send(player, "appeal-submitted",
                        "id", newest == null ? "?" : String.valueOf(newest.getId()));
                break;
            case NO_PUNISHMENT:
                plugin.send(player, "appeal-none");
                break;
            case ALREADY_PENDING: {
                Appeal pending = findPending(player);
                plugin.send(player, "appeal-pending-exists",
                        "id", pending == null ? "?" : String.valueOf(pending.getId()));
                break;
            }
            case LIMIT:
                plugin.send(player, "appeal-limit",
                        "used", String.valueOf(plugin.settings().appealMax()),
                        "max", String.valueOf(plugin.settings().appealMax()));
                break;
            case COOLDOWN: {
                String left = plugin.appealManager().cooldownRemaining(counterKey(player));
                plugin.send(player, "appeal-cooldown", "time", left == null ? "-" : left);
                break;
            }
            default:
                break;
        }
    }

    private static String counterKey(Player player) {
        return PunishmentStore.uuidKey(player.getUniqueId());
    }

    private Appeal findPending(Player player) {
        for (Appeal a : plugin.appealStore().pending()) {
            if (a.getUuid() != null && a.getUuid().equals(player.getUniqueId())) {
                return a;
            }
        }
        return null;
    }

    private Appeal latestAppeal(Player player) {
        Appeal best = null;
        for (Appeal a : plugin.appealStore().all()) {
            if (a.getUuid() != null && a.getUuid().equals(player.getUniqueId())) {
                if (best == null || a.getId() > best.getId()) {
                    best = a;
                }
            }
        }
        return best;
    }

    private void showAppealStatus(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.send(sender, "usage-appeal");
            return;
        }
        Player player = (Player) sender;
        Punishment ban = plugin.banStore().findActive(player.getUniqueId(), player.getName());
        Punishment mute = plugin.muteStore().findActive(player.getUniqueId(), player.getName());
        if (ban == null && mute == null) {
            plugin.send(sender, "appeal-status-none");
            return;
        }
        Punishment main = ban != null ? ban : mute;
        String duration = main.isPermanent()
                ? "Permanent"
                : Durations.format(main.remaining(System.currentTimeMillis()));
        plugin.send(sender, "appeal-status-punishment",
                "type", typeText(main.getType()),
                "reason", main.getReason(),
                "duration", duration);

        Settings settings = plugin.settings();
        int used = plugin.appealStore().countFor(counterKey(player), main.getType(), main.getId(),
                settings.countScope() == Settings.CountScope.LIFETIME);
        String scope = settings.countScope() == Settings.CountScope.LIFETIME ? "Lifetime" : "Per punishment";
        plugin.send(sender, "appeal-status-counts",
                "used", String.valueOf(used),
                "max", String.valueOf(settings.appealMax()),
                "scope", scope);

        String cooldown = plugin.appealManager().cooldownRemaining(counterKey(player));
        if (cooldown == null) {
            plugin.send(sender, "appeal-status-cooldown-none");
        } else {
            plugin.send(sender, "appeal-status-cooldown", "time", cooldown);
        }

        Appeal pending = findPending(player);
        if (pending != null) {
            plugin.send(sender, "appeal-status-pending", "id", String.valueOf(pending.getId()));
        }
        plugin.send(sender, "usage-appeal");
    }

    private void doAppealAdmin(CommandSender sender, String sub, String[] args) {
        if ("list".equals(sub)) {
            List<Appeal> pending = plugin.appealStore().pending();
            if (pending.isEmpty()) {
                plugin.send(sender, "appeal-list-empty");
                return;
            }
            plugin.send(sender, "appeal-list-header");
            for (Appeal a : pending) {
                plugin.send(sender, "appeal-list-entry",
                        "id", String.valueOf(a.getId()),
                        "player", a.getName(),
                        "type", typeText(a.getPunishmentType()),
                        "reason", a.getReason());
            }
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, "usage-appeal");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.send(sender, "appeal-not-found", "id", args[1]);
            return;
        }
        Appeal a = plugin.appealManager().get(id);
        if (a == null) {
            plugin.send(sender, "appeal-not-found", "id", args[1]);
            return;
        }
        if ("view".equals(sub)) {
            plugin.send(sender, "appeal-view-header",
                    "id", String.valueOf(a.getId()),
                    "status", statusText(a.getStatus()));
            plugin.send(sender, "appeal-view-entry",
                    "player", a.getName(),
                    "type", typeText(a.getPunishmentType()),
                    "punishment", String.valueOf(a.getPunishmentId()),
                    "reason", a.getReason(),
                    "created", dateFormat.format(new Date(a.getCreated())),
                    "decided", a.getStatus() == AppealStatus.PENDING
                            ? "-"
                            : a.getDecidedBy() + " @ " + dateFormat.format(new Date(a.getDecidedAt())),
                    "note", a.getNote().isEmpty() ? "-" : a.getNote());
            return;
        }
        boolean accept = "accept".equals(sub);
        String note = args.length > 2 ? Text.join(args, 2).trim() : "";
        if (!plugin.appealManager().decide(a, sender.getName(), accept, note)) {
            plugin.send(sender, "appeal-not-pending");
            return;
        }
        plugin.send(sender, accept ? "appeal-accepted-admin" : "appeal-denied-admin",
                "id", String.valueOf(a.getId()),
                "player", a.getName());
    }

    // ---------- /neoban ----------

    private void doNeoBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neoban.admin")) {
            plugin.send(sender, "no-permission");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(sub)) {
            plugin.reloadPlugin();
            plugin.send(sender, "reload-success");
        } else if ("info".equals(sub)) {
            plugin.send(sender, "info-bans", "count", String.valueOf(plugin.banStore().activeCount()));
            plugin.send(sender, "info-mutes", "count", String.valueOf(plugin.muteStore().activeCount()));
            plugin.send(sender, "info-appeals", "count", String.valueOf(plugin.appealStore().pendingCount()));
            plugin.send(sender, "info-ipbans", "count", String.valueOf(plugin.ipBanStore().activeCount()));
            plugin.send(sender, "info-world",
                    "world", plugin.appealWorld() != null ? plugin.appealWorld().getName() : "-");
        } else if ("resetappeals".equals(sub)) {
            if (args.length < 2) {
                plugin.send(sender, "usage-neoban");
                return;
            }
            String name = args[1];
            UUID uuid = null;
            Player online = plugin.findOnline(null, name);
            if (online != null) {
                uuid = online.getUniqueId();
                name = online.getName();
            } else {
                uuid = plugin.banStore().findUuidByName(name);
                if (uuid == null) {
                    uuid = plugin.muteStore().findUuidByName(name);
                }
            }
            plugin.appealManager().resetCounters(name, uuid);
            plugin.send(sender, "appeal-reset", "player", name);
        } else {
            plugin.send(sender, "usage-neoban");
        }
    }

    // ---------- tab complete ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        String l = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if ("ban".equals(l) || "tempban".equals(l) || "unban".equals(l) || "mute".equals(l)
                    || "tempmute".equals(l) || "unmute".equals(l) || "kick".equals(l)
                    || "ipban".equals(l) || "iptempban".equals(l)) {
                match(out, onlineNames(), args[0]);
            } else if ("ipunban".equals(l)) {
                match(out, plugin.ipBanStore().activeIps(), args[0]);
            } else if ("appeal".equals(l) && sender.hasPermission("neoban.appeal.admin")) {
                match(out, java.util.Arrays.asList("view", "accept", "deny", "list"), args[0]);
            } else if ("neoban".equals(l) && sender.hasPermission("neoban.admin")) {
                match(out, java.util.Arrays.asList("reload", "info", "resetappeals"), args[0]);
            }
        } else if (args.length == 2 && ("tempban".equals(l) || "tempmute".equals(l) || "iptempban".equals(l))) {
            match(out, java.util.Arrays.asList("30s", "5m", "1h", "1d", "7d", "30d"), args[1]);
        } else if (args.length == 2 && "neoban".equals(l) && "resetappeals".equalsIgnoreCase(args[0])) {
            match(out, onlineNames(), args[1]);
        } else if (args.length == 2 && "appeal".equals(l)
                && ("view".equalsIgnoreCase(args[0]) || "accept".equalsIgnoreCase(args[0])
                || "deny".equalsIgnoreCase(args[0]))) {
            List<String> ids = new ArrayList<String>();
            for (Appeal a : plugin.appealStore().pending()) {
                ids.add(String.valueOf(a.getId()));
            }
            match(out, ids, args[1]);
        }
        Collections.sort(out);
        return out;
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<String>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static void match(List<String> out, List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
    }

    private static String typeText(PunishmentType type) {
        return type == PunishmentType.BAN ? "Ban" : "Mute";
    }

    private static String statusText(AppealStatus status) {
        switch (status) {
            case PENDING:
                return "Pending";
            case ACCEPTED:
                return "Accepted";
            case DENIED:
                return "Denied";
            default:
                return status.name();
        }
    }
}
