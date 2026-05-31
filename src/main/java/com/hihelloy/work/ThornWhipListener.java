package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;

public class ThornWhipListener implements Listener {

    @EventHandler
    public void onClick(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (event.isCancelled() || bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (bPlayer.getBoundAbilityName().equalsIgnoreCase("ThornWhip")) {
            if (!CoreAbility.hasAbility(player, ThornWhip.class)) {
                new ThornWhip(player);
            }
        }
    }
}
