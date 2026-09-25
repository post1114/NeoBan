package com.neoban.common.manager;

import com.neoban.common.NeoBanBase;
import com.neoban.common.config.Settings;
import com.neoban.common.model.Appeal;
import com.neoban.common.model.AppealStatus;
import com.neoban.common.model.Punishment;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.storage.PunishmentStore;
import com.neoban.common.util.Durations;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class AppealManager {

    public enum SubmitResult {
        OK,
        NO_PUNISHMENT,
        ALREADY_PENDING,
        LIMIT,
        COOLDOWN
    }

    private final NeoBanBase plugin;

    public AppealManager(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    private Punishment findAppealable(Player player) {
        Punishment ban = plugin.banStore().findActive(player.getUniqueId(), player.getName());
        if (ban != null) {
            return ban;
        }
        return plugin.muteStore().findActive(player.getUniqueId(), player.getName());
    }

    private static String counterKey(Player player) {
        return PunishmentStore.uuidKey(player.getUniqueId());
    }

    public SubmitResult submit(Player player, String reason) {
        Punishment target = findAppealable(player);
        if (target == null) {
            return SubmitResult.NO_PUNISHMENT;
        }
        for (Appeal a : plugin.appealStore().all()) {
            if (a.getStatus() == AppealStatus.PENDING && a.matchesPunishment(target)) {
                return SubmitResult.ALREADY_PENDING;
            }
        }
        Settings settings = plugin.settings();
        String key = counterKey(player);
        int used = plugin.appealStore().countFor(key, target.getType(), target.getId(),
                settings.countScope() == Settings.CountScope.LIFETIME);
        if (used >= settings.appealMax()) {
            return SubmitResult.LIMIT;
        }
        long now = System.currentTimeMillis();
        long last = plugin.appealStore().lastAppeal(key);
        if (last > 0 && settings.cooldownMs() > 0 && now - last < settings.cooldownMs()) {
            return SubmitResult.COOLDOWN;
        }

        Appeal a = new Appeal();
        a.setPunishmentType(target.getType());
        a.setPunishmentId(target.getId());
        a.setUuid(player.getUniqueId());
        a.setName(player.getName());
        a.setReason(reason);
        a.setCreated(now);
        a.setStatus(AppealStatus.PENDING);
        a.setDecidedBy("");
        a.setDecidedAt(0L);
        a.setNote("");
        a.setDelivered(false);
        plugin.appealStore().add(a);
        plugin.appealStore().bump(key, target.getType(), target.getId());

        int notified = notifyAdmins(a);
        a.setDelivered(notified > 0);
        plugin.appealStore().save();

        plugin.getLogger().info("Appeal #" + a.getId() + " from " + player.getName()
                + " (" + target.getType() + " #" + target.getId() + "): " + reason);
        return SubmitResult.OK;
    }

    public Appeal get(int id) {
        return plugin.appealStore().get(id);
    }

    public boolean decide(Appeal a, String admin, boolean accept, String note) {
        if (a.getStatus() != AppealStatus.PENDING) {
            return false;
        }
        a.setStatus(accept ? AppealStatus.ACCEPTED : AppealStatus.DENIED);
        a.setDecidedBy(admin);
        a.setDecidedAt(System.currentTimeMillis());
        a.setNote(note == null ? "" : note);
        plugin.appealStore().save();

        if (accept) {
            liftPunishment(a);
        }
        notifyTarget(a, admin);
        return true;
    }

    private void liftPunishment(Appeal a) {
        PunishmentStore store = a.getPunishmentType() == PunishmentType.BAN
                ? plugin.banStore() : plugin.muteStore();
        Punishment p = store.findById(a.getPunishmentId());
        if (p == null || !p.isActive()) {
            return;
        }
        store.deactivate(p);
        if (a.getPunishmentType() == PunishmentType.BAN) {
            plugin.handleBanLifted(p.getUuid(), p.getName());
        } else {
            Player target = plugin.findOnline(p.getUuid(), p.getName());
            if (target != null) {
                plugin.send(target, "mute-expired");
            }
        }
    }

    private void notifyTarget(Appeal a, String admin) {
        Player target = plugin.findOnline(a.getUuid(), a.getName());
        if (target == null) {
            return;
        }
        if (a.getStatus() == AppealStatus.ACCEPTED) {
            plugin.send(target, "appeal-accepted-player", "id", String.valueOf(a.getId()), "admin", admin);
        } else {
            String note = a.getNote().isEmpty() ? "-" : a.getNote();
            plugin.send(target, "appeal-denied-player", "id", String.valueOf(a.getId()), "admin", admin, "note", note);
        }
    }

    private int notifyAdmins(Appeal a) {
        int count = 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission("neoban.appeal.admin")) {
                sendAppealInfo(p, a);
                count++;
            }
        }
        return count;
    }

    private void sendAppealInfo(Player admin, Appeal a) {
        plugin.send(admin, "appeal-notify-admin",
                "player", a.getName(),
                "id", String.valueOf(a.getId()),
                "type", a.getPunishmentType() == PunishmentType.BAN ? "Ban" : "Mute",
                "reason", a.getReason());
    }

    public int deliverPending(Player admin) {
        List<Appeal> pending = plugin.appealStore().pending();
        int count = 0;
        for (Appeal a : pending) {
            if (!a.isDelivered()) {
                sendAppealInfo(admin, a);
                a.setDelivered(true);
                count++;
            }
        }
        if (count > 0) {
            plugin.appealStore().save();
        }
        return count;
    }

    public void resetCounters(String name, UUID uuid) {
        if (uuid != null) {
            plugin.appealStore().reset(PunishmentStore.uuidKey(uuid));
        }
        plugin.appealStore().reset(PunishmentStore.nameKey(name));
    }

    public String cooldownRemaining(String counterKey) {
        long last = plugin.appealStore().lastAppeal(counterKey);
        if (last <= 0) {
            return null;
        }
        long left = plugin.settings().cooldownMs() - (System.currentTimeMillis() - last);
        if (left <= 0) {
            return null;
        }
        return Durations.format(left);
    }
}
