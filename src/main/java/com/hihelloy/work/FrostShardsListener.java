package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;

public class FrostShardsListener implements Listener {

    @EventHandler
    public void onClick(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (event.isCancelled() || bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (bPlayer.getBoundAbilityName().equalsIgnoreCase("FrostShards")) {
            if (!CoreAbility.hasAbility(player, FrostShards.class)) {
                new FrostShards(player);
            } else {
                CoreAbility.getAbility(player, FrostShards.class).onLeftClick();
            }
        }
    }
}
