package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class IronMantleListener implements Listener {

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (bPlayer.getBoundAbilityName().equalsIgnoreCase("IronMantle")) {
            if (!event.isSneaking()) return;
            if (!CoreAbility.hasAbility(player, IronMantle.class)) {
                new IronMantle(player);
            } else {
                CoreAbility.getAbility(player, IronMantle.class).onSneak();
            }
        }
    }
}
