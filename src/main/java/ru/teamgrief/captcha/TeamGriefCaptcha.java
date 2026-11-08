package ru.teamgrief.captcha;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.Event;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class TeamGriefCaptcha extends JavaPlugin implements Listener {
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private static final Pattern SIX_DIGITS = Pattern.compile("[0-9]{6}");
    private record Session(String code, AtomicInteger attempts, long expiresAt) {}
    private BukkitTask ticker;

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);
        ticker = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Session s = sessions.get(p.getUniqueId());
                if (s == null) continue;
                if (System.currentTimeMillis() >= s.expiresAt()) {
                    sessions.remove(p.getUniqueId());
                    p.kick(Component.text("Время капчи истекло. Перезайди на сервер."));
                } else {
                    p.sendActionBar(Component.text("Капча: " + s.code() + " | Введи код в чат"));
                }
            }
        }, 20L, 20L);
    }
    @Override public void onDisable() {
        if (ticker != null) ticker.cancel();
        sessions.clear();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission("teamgriefcaptcha.bypass")) return;
        String code = String.format("%06d", random.nextInt(1_000_000));
        long until = System.currentTimeMillis() + Math.max(10, getConfig().getInt("captcha-seconds", 60)) * 1000L;
        sessions.put(p.getUniqueId(), new Session(code, new AtomicInteger(), until));
        p.sendMessage(Component.text("§d✦ TEAMGRIEF CAPTCHA ✦"));
        p.sendMessage(Component.text("§fВведи в чат цифры: §e" + code));
        p.sendMessage(Component.text("§7У тебя 60 секунд и 3 попытки (по умолчанию)."));
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        event.setCancelled(true);
        // PlainTextComponentSerializer извлекает только текст; ответ не публикуется в общий чат.
        String answer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(this, () -> checkAnswer(p, s, answer));
    }
    private void checkAnswer(Player p, Session expected, String answer) {
        if (!p.isOnline() || sessions.get(p.getUniqueId()) != expected) return;
        if (System.currentTimeMillis() >= expected.expiresAt()) {
            sessions.remove(p.getUniqueId());
            p.kick(Component.text("Время капчи истекло."));
            return;
        }
        if (SIX_DIGITS.matcher(answer).matches() && expected.code().equals(answer)) {
            sessions.remove(p.getUniqueId());
            p.sendMessage(Component.text("§aКапча пройдена! Перенаправляем на регистрацию..."));
            String target = getConfig().getString("next-server", "auth");
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(target);
            p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        } else {
            int count = expected.attempts().incrementAndGet();
            if (count >= Math.max(1, getConfig().getInt("max-attempts", 3))) {
                sessions.remove(p.getUniqueId());
                p.kick(Component.text("Слишком много ошибок капчи."));
            } else p.sendMessage(Component.text("§cНеверный код. Осталось попыток: " + (getConfig().getInt("max-attempts", 3) - count)));
        }
    }
    private boolean blocked(Player p) { return sessions.containsKey(p.getUniqueId()); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) {
        if (blocked(e.getPlayer())) { e.setCancelled(true); e.getPlayer().sendMessage(Component.text("§cСначала пройди капчу в чате.")); }
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (blocked(e.getPlayer()) && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ())) e.setTo(e.getFrom());
    }
    @EventHandler public void onInteract(PlayerInteractEvent e) { if (blocked(e.getPlayer())) e.setUseInteractedBlock(Event.Result.DENY); if (blocked(e.getPlayer())) e.setUseItemInHand(Event.Result.DENY); }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && blocked(p)) e.setCancelled(true); }
}
