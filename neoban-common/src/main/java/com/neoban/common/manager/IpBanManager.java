package com.neoban.common.manager;

import com.neoban.common.NeoBanBase;
import com.neoban.common.config.Settings;
import com.neoban.common.model.IpBan;
import com.neoban.common.util.Durations;
import com.neoban.common.util.Ips;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class IpBanManager {

    private final NeoBanBase plugin;

    public IpBanManager(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    public void checkThreshold(String ip) {
        Settings s = plugin.settings();
        if (!s.ipAutoEnabled() || ip == null || ip.isEmpty()) {
            return;
        }
        if (plugin.ipBanStore().findActive(ip) != null) {
            return;
        }
        int count = plugin.banStore().countActiveByIp(ip);
        if (count < s.ipAutoThreshold()) {
            return;
        }
        IpBan ban = plugin.ipBanStore().create(ip, "AUTO", s.ipAutoReason(),
                s.ipAutoDurationMs(), true);
        String duration = ban.isPermanent() ? "Permanent" : Durations.format(s.ipAutoDurationMs());
        plugin.getLogger().info("Auto IP ban: " + ip + " has " + count
                + " banned players, blocked for " + duration + ".");
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission("neoban.ipban")) {
                plugin.send(p, "auto-ipban-notify", "ip", ip,
                        "count", String.valueOf(count), "duration", duration);
            }
        }
        kickPlayersOn(ip, ban, null);
    }

    public int kickPlayersOn(String ip, IpBan ban, Player except) {
        List<Player> targets = new ArrayList<Player>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (except != null && p.equals(except)) {
                continue;
            }
            if (p.hasPermission("neoban.ipban.bypass")) {
                continue;
            }
            if (ip.equals(Ips.of(p.getAddress()))) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        String remaining = ban == null || ban.isPermanent() ? "Permanent"
                : Durations.format(ban.remaining(now));
        String reason = ban == null ? plugin.formatMessage("reason-default") : ban.getReason();
        String message = plugin.formatMessage("ipban-join", "reason", reason, "duration", remaining);
        for (Player p : targets) {
            plugin.adapter().kick(p, message);
        }
        return targets.size();
    }
}
