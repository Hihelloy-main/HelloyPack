package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VoidPortalListener implements Listener {

    private final Map<UUID, Long> lastSneakTime = new HashMap<>();
    private static final long DOUBLE_SNEAK_WINDOW = 350;

    @EventHandler
    public void onClick(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (event.isCancelled() || bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (!bPlayer.getBoundAbilityName().equalsIgnoreCase("VoidPortal")) return;
        if (!CoreAbility.hasAbility(player, VoidPortal.class)) {
            new VoidPortal(player);
        } else {
            CoreAbility.getAbility(player, VoidPortal.class).onLeftClick();
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (!bPlayer.getBoundAbilityName().equalsIgnoreCase("VoidPortal")) return;
        if (!event.isSneaking()) return;
        if (!CoreAbility.hasAbility(player, VoidPortal.class)) return;

        VoidPortal ability = CoreAbility.getAbility(player, VoidPortal.class);
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();

        if (lastSneakTime.containsKey(uid) && now - lastSneakTime.get(uid) < DOUBLE_SNEAK_WINDOW) {
            lastSneakTime.remove(uid);
            ability.onDoubleSneak();
        } else {
            lastSneakTime.put(uid, now);
            ability.onSneak();
        }
    }
}
