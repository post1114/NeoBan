package com.neoban.common;

import com.neoban.common.command.NeoBanCommand;
import com.neoban.common.config.Settings;
import com.neoban.common.listener.CoreListener;
import com.neoban.common.listener.RestrictListener;
import com.neoban.common.model.Punishment;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.storage.AppealStore;
import com.neoban.common.storage.LocationStore;
import com.neoban.common.storage.PunishmentStore;
import com.neoban.common.manager.AppealManager;
import com.neoban.common.manager.BanManager;
import com.neoban.common.manager.MuteManager;
import com.neoban.common.util.Durations;
import com.neoban.common.util.Text;
import com.neoban.common.util.YamlIo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class NeoBanBase extends JavaPlugin {

    private static final String[] COMMAND_NAMES = {
            "ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "appeal", "neoban"
    };

    private PlatformAdapter adapter;
    private Settings settings;
    private PunishmentStore banStore;
    private PunishmentStore muteStore;
    private AppealStore appealStore;
    private LocationStore locationStore;
    private BanManager banManager;
    private MuteManager muteManager;
    private AppealManager appealManager;
    private World appealWorld;
    private Location appealSpawn;
    private boolean internalTeleport;
    private final Map<UUID, Set<UUID>> hiddenBy = new HashMap<UUID, Set<UUID>>();
    private List<BukkitTask> tasks = new ArrayList<BukkitTask>();

    protected abstract PlatformAdapter createAdapter();

    protected abstract void registerPlatformListeners();

    @Override
    public void onEnable() {
        adapter = createAdapter();
        settings = new Settings(loadConfigYaml());

        File dataDir = getDataFolder();
        banStore = new PunishmentStore(new File(dataDir, "bans.yml"), PunishmentType.BAN);
        muteStore = new PunishmentStore(new File(dataDir, "mutes.yml"), PunishmentType.MUTE);
        appealStore = new AppealStore(new File(dataDir, "appeals.yml"));
        locationStore = new LocationStore(new File(dataDir, "locations.yml"));
        banStore.load();
        muteStore.load();
        appealStore.load();
        locationStore.load();

        banManager = new BanManager(this);
        muteManager = new MuteManager(this);
        appealManager = new AppealManager(this);

        ensureAppealWorld();

        getServer().getPluginManager().registerEvents(new CoreListener(this), this);
        getServer().getPluginManager().registerEvents(new RestrictListener(this), this);

        NeoBanCommand command = new NeoBanCommand(this);
        for (String name : COMMAND_NAMES) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(command);
                pc.setTabCompleter(command);
            } else {
                getLogger().warning("Command missing in plugin.yml: " + name);
            }
        }

        registerPlatformListeners();

        tasks.add(getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                sweep();
            }
        }, 1200L, 1200L));
        tasks.add(getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                syncAllVisibility();
            }
        }, 100L, 100L));

        getLogger().info("NeoBan enabled. Appeal world: "
                + (appealWorld != null ? appealWorld.getName() : "none"));
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : tasks) {
            try {
                t.cancel();
            } catch (Throwable ignored) {
            }
        }
        tasks.clear();
        if (banStore != null) {
            banStore.save();
        }
        if (muteStore != null) {
            muteStore.save();
        }
        if (appealStore != null) {
            appealStore.save();
        }
        if (locationStore != null) {
            locationStore.save();
        }
    }

    public void reloadPlugin() {
        settings = new Settings(loadConfigYaml());
        ensureAppealWorld();
    }

    private YamlConfiguration loadConfigYaml() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        return YamlIo.load(configFile);
    }

    // ---------- world ----------

    private void ensureAppealWorld() {
        World world;
        boolean isVoid;
        if (settings.worldType() == Settings.WorldType.CUSTOM) {
            world = getServer().getWorld(settings.customName());
            if (world == null) {
                getLogger().warning("Custom appeal world '" + settings.customName()
                        + "' not found, falling back to void world.");
                world = null;
            }
            isVoid = world == null;
            if (isVoid) {
                world = ensureVoidWorld();
            }
        } else {
            isVoid = true;
            world = ensureVoidWorld();
        }
        appealWorld = world;
        if (world == null) {
            appealSpawn = null;
            getLogger().severe("Failed to create/locate appeal world!");
            return;
        }
        if (isVoid) {
            Block b = world.getBlockAt(0, 0, 0);
            if (b.getType() != Material.BARRIER) {
                b.setType(Material.BARRIER);
            }
            world.setSpawnLocation(0, 1, 0);
            appealSpawn = new Location(world, 0.5, 1.0, 0.5, 0f, 0f);
        } else if (settings.hasCustomSpawn()) {
            double x = settings.spawnX();
            double y = settings.spawnY();
            double z = settings.spawnZ();
            world.setSpawnLocation((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            appealSpawn = new Location(world, x, y, z, 0f, 0f);
        } else {
            appealSpawn = world.getSpawnLocation();
        }
    }

    private World ensureVoidWorld() {
        String name = settings.voidName();
        World w = getServer().getWorld(name);
        if (w != null) {
            return w;
        }
        ChunkGenerator gen = adapter.createVoidGenerator();
        WorldCreator creator = new WorldCreator(name);
        if (gen != null) {
            creator.generator(gen);
        }
        return getServer().createWorld(creator);
    }

    // ---------- state accessors ----------

    public PlatformAdapter adapter() {
        return adapter;
    }

    public Settings settings() {
        return settings;
    }

    public PunishmentStore banStore() {
        return banStore;
    }

    public PunishmentStore muteStore() {
        return muteStore;
    }

    public AppealStore appealStore() {
        return appealStore;
    }

    public LocationStore locationStore() {
        return locationStore;
    }

    public BanManager banManager() {
        return banManager;
    }

    public MuteManager muteManager() {
        return muteManager;
    }

    public AppealManager appealManager() {
        return appealManager;
    }

    public World appealWorld() {
        return appealWorld;
    }

    public Location appealSpawn() {
        if (appealSpawn != null) {
            return appealSpawn.clone();
        }
        List<World> worlds = getServer().getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0).getSpawnLocation();
    }

    public boolean isInternalTeleport() {
        return internalTeleport;
    }

    public void teleportInternal(Player player, Location loc) {
        if (loc == null) {
            return;
        }
        internalTeleport = true;
        try {
            player.teleport(loc);
        } finally {
            internalTeleport = false;
        }
    }

    // ---------- predicates ----------

    public boolean inAppealWorld(Player player) {
        return appealWorld != null && player.getWorld().equals(appealWorld);
    }

    public boolean isBanned(Player player) {
        return banStore.findActive(player.getUniqueId(), player.getName()) != null;
    }

    public boolean isRestricted(Player player) {
        if (player.hasPermission("neoban.bypass")) {
            return false;
        }
        if (!inAppealWorld(player)) {
            return false;
        }
        return banStore.findActive(player.getUniqueId(), player.getName()) != null;
    }

    public boolean handleChat(Player player, String message) {
        Punishment mute = muteStore.findActive(player.getUniqueId(), player.getName());
        if (mute != null) {
            long rem = mute.remaining(System.currentTimeMillis());
            send(player, "mute-blocked", "duration", Durations.format(rem));
            return true;
        }
        if (isRestricted(player)) {
            send(player, "chat-blocked");
            return true;
        }
        return false;
    }

    public boolean allowCommand(Player player, String rawMessage) {
        if (!isRestricted(player)) {
            return true;
        }
        String body = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        String first = body.split(" ")[0].toLowerCase(Locale.ROOT);
        return settings.allowedCommands().contains(first);
    }

    // ---------- visibility ----------

    private boolean shouldHide(Player viewer, Player target) {
        return isRestricted(viewer) || isRestricted(target);
    }

    private void setVisibility(Player viewer, Player target) {
        boolean hide = shouldHide(viewer, target);
        Set<UUID> set = hiddenBy.get(viewer.getUniqueId());
        boolean currently = set != null && set.contains(target.getUniqueId());
        if (hide && !currently) {
            adapter.hidePlayer(viewer, target);
            if (set == null) {
                set = new HashSet<UUID>();
                hiddenBy.put(viewer.getUniqueId(), set);
            }
            set.add(target.getUniqueId());
        } else if (!hide && currently) {
            adapter.showPlayer(viewer, target);
            set.remove(target.getUniqueId());
        }
    }

    public void syncVisibilityAround(Player player) {
        for (Player other : getServer().getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            setVisibility(player, other);
            setVisibility(other, player);
        }
    }

    public void syncAllVisibility() {
        List<Player> online = new ArrayList<Player>(getServer().getOnlinePlayers());
        for (Player viewer : online) {
            for (Player target : online) {
                if (viewer.equals(target)) {
                    continue;
                }
                setVisibility(viewer, target);
            }
        }
    }

    public void onPlayerQuit(Player player) {
        hiddenBy.remove(player.getUniqueId());
        for (Set<UUID> set : hiddenBy.values()) {
            set.remove(player.getUniqueId());
        }
    }

    // ---------- join / release flows ----------

    public void handleJoin(Player player) {
        banStore.migrateToUuid(player.getName(), player.getUniqueId());
        muteStore.migrateToUuid(player.getName(), player.getUniqueId());

        Punishment ban = banStore.findActive(player.getUniqueId(), player.getName());
        if (ban != null) {
            if (!inAppealWorld(player)) {
                locationStore.save(player.getUniqueId(), player.getLocation());
                teleportInternal(player, appealSpawn());
            }
            syncVisibilityAround(player);
            String remaining = ban.isPermanent()
                    ? "Permanent"
                    : Durations.format(ban.remaining(System.currentTimeMillis()));
            send(player, "ban-join",
                    "reason", ban.getReason(),
                    "duration", remaining,
                    "world", appealWorld != null ? appealWorld.getName() : "-");
        } else if (inAppealWorld(player)) {
            restoreFromAppeal(player);
        } else {
            syncVisibilityAround(player);
        }

        if (player.hasPermission("neoban.appeal.admin")) {
            appealManager.deliverPending(player);
        }
    }

    public void restoreFromAppeal(Player player) {
        Location dest = locationStore.get(player.getUniqueId());
        if (dest == null) {
            List<World> worlds = getServer().getWorlds();
            dest = worlds.isEmpty() ? null : worlds.get(0).getSpawnLocation();
        }
        if (dest != null) {
            teleportInternal(player, dest);
        }
        locationStore.clear(player.getUniqueId());
        syncVisibilityAround(player);
    }

    public void handleBanLifted(UUID uuid, String name) {
        Player player = findOnline(uuid, name);
        if (player == null) {
            return;
        }
        send(player, "ban-removed");
        if (inAppealWorld(player)) {
            restoreFromAppeal(player);
        } else {
            syncVisibilityAround(player);
        }
    }

    public Player findOnline(UUID uuid, String name) {
        if (uuid != null) {
            Player p = getServer().getPlayer(uuid);
            if (p != null) {
                return p;
            }
        }
        if (name != null) {
            for (Player p : getServer().getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(name)) {
                    return p;
                }
            }
        }
        return null;
    }

    // ---------- sweep ----------

    private void sweep() {
        List<Punishment> expired = new ArrayList<Punishment>();
        expired.addAll(banStore.pollExpired());
        expired.addAll(muteStore.pollExpired());
        for (Punishment p : expired) {
            Player online = findOnline(p.getUuid(), p.getName());
            if (p.getType() == PunishmentType.BAN) {
                getLogger().info("Ban expired for " + p.getName());
                if (online != null) {
                    send(online, "ban-expired");
                    if (inAppealWorld(online)) {
                        restoreFromAppeal(online);
                    } else {
                        syncVisibilityAround(online);
                    }
                }
            } else {
                getLogger().info("Mute expired for " + p.getName());
                if (online != null) {
                    send(online, "mute-expired");
                }
            }
        }
    }

    // ---------- messaging ----------

    public String formatMessage(String key, String... kv) {
        return Text.apply(settings.message(key), Text.vars(kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        String full = formatMessage(key, kv);
        String[] lines = full.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                to.sendMessage(settings.prefix() + lines[i]);
            } else {
                to.sendMessage(lines[i]);
            }
        }
    }
}
