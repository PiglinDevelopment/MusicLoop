package dev.piglin.musicloop;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class MusicLoop extends JavaPlugin implements Listener {
    private static final Random rand = new Random();
    private static final List<Sound> netherMusic = Arrays.asList(
            Sound.MUSIC_NETHER_BASALT_DELTAS,
            Sound.MUSIC_NETHER_CRIMSON_FOREST,
            Sound.MUSIC_NETHER_NETHER_WASTES,
            Sound.MUSIC_NETHER_SOUL_SAND_VALLEY,
            Sound.MUSIC_NETHER_WARPED_FOREST
    );
    private final HashMap<UUID, MusicStatus> statuses = new HashMap<>();
    private List<Loop> loops;
    private boolean resourcePack;
    private boolean stopDefaultMusic;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        resourcePack = getConfig().getBoolean("resource pack");
        stopDefaultMusic = getConfig().getBoolean("stop default music");
        loops = getConfig().getStringList("loop priority")
                .stream()
                .map(loopName -> new Loop(
                        loopName,
                        getConfig()
                                .getConfigurationSection("loops." + loopName + ".tracks")
                                .getKeys(true)
                                .stream()
                                .map(name -> new AbstractMap.SimpleEntry<>(name, getConfig().get("loops." + loopName + ".tracks." + name)))
                                .filter(entry -> entry.getValue() instanceof Integer)
                                .map(entry -> new Track(NamespacedKey.fromString(entry.getKey()), 1000 * (Integer) entry.getValue()))
                                .collect(Collectors.toList()),
                        getConfig().getBoolean("loops." + loopName + ".shuffle")
                ))
                .collect(Collectors.toList());
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            var now = System.currentTimeMillis();
            statuses.forEach((uuid, status) -> {
                if (status.end() <= now) {
                    var trackNext = switch (status.loop().isShuffle() ? 1 : 0) {
                        case 1 -> {
                            var tracksInLoop = new ArrayList<>(status.loop().tracks());
                            if (tracksInLoop.size() == 1) yield status.track();
                            tracksInLoop.remove(status.track());
                            yield tracksInLoop.get(rand.nextInt(tracksInLoop.size()));
                        }
                        case 0 -> {
                            var index = status.loop().tracks().indexOf(status.track());
                            index++;
                            if (index == status.loop().tracks().size()) index = 0;
                            yield status.loop().tracks().get(index);
                        }
                        default -> throw new IllegalStateException("Unreachable");
                    };
                    getServer().getPlayer(uuid).playSound(getServer().getPlayer(uuid).getLocation(), trackNext.key().toString(), SoundCategory.MUSIC, 1f, 1f);
                    statuses.put(uuid, new MusicStatus(trackNext, now + trackNext.duration(), status.loop()));
                }
            });
        }, 1L, 1L);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (stopDefaultMusic) {
                getServer().getOnlinePlayers().forEach(player -> {
                    if (player.getGameMode() == GameMode.CREATIVE) {
                        player.stopSound(Sound.MUSIC_CREATIVE);
                    }
                    if (player.isInWater()) {
                        player.stopSound(Sound.MUSIC_UNDER_WATER);
                    }
                    switch (player.getWorld().getEnvironment()) {
                        case NETHER -> netherMusic.forEach(player::stopSound);
                        case THE_END -> player.stopSound(Sound.MUSIC_END);
                        default -> player.stopSound(Sound.MUSIC_GAME);
                    }
                });
            }
        }, 1L, 20L);
        getServer().getPluginManager().registerEvents(this, this);
        if (!getServer().getOnlinePlayers().isEmpty()) {
            getServer().getScheduler().runTask(this, () -> {
                getServer().dispatchCommand(getServer().getConsoleSender(), "minecraft:stopsound @a music");
                getServer().getOnlinePlayers().forEach(this::startMusic);
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!resourcePack) {
            startMusic(event.getPlayer());
        }
    }

    @EventHandler
    public void onResourcePackLoaded(PlayerResourcePackStatusEvent event) {
        if (resourcePack) {
            if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                startMusic(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        statuses.remove(event.getPlayer().getUniqueId());
    }

    private void startMusic(Player player) {
        for (var loop : loops) {
            if (player.hasPermission("musicloop.loop." + loop.name())) {
                var track = loop.isShuffle()
                        ? loop.tracks().get(rand.nextInt(loop.tracks().size()))
                        : loop.tracks().get(0);
                final var ticksDelay = 1L;
                statuses.put(player.getUniqueId(), new MusicStatus(track, System.currentTimeMillis() + track.duration() + ticksDelay * 20, loop));
                getServer().getScheduler().scheduleSyncDelayedTask(this, () -> player.playSound(player.getLocation(), track.key().toString(), SoundCategory.MUSIC, 1f, 1f), ticksDelay);
                break;
            }
        }
    }
}
