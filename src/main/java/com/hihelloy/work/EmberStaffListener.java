package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class EmberStaffListener implements Listener {

    @EventHandler
    public void onClick(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (event.isCancelled() || bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (bPlayer.getBoundAbilityName().equalsIgnoreCase("EmberStaff")) {
            if (!CoreAbility.hasAbility(player, EmberStaff.class)) {
                new EmberStaff(player);
            } else {
                CoreAbility.getAbility(player, EmberStaff.class).onLeftClick();
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (!bPlayer.getBoundAbilityName().equalsIgnoreCase("EmberStaff")) return;
        if (event.isSneaking() && CoreAbility.hasAbility(player, EmberStaff.class)) {
            CoreAbility.getAbility(player, EmberStaff.class).onSneak();
        }
    }
}
