package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class OrbitalStrikeListener implements Listener {

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (bPlayer.getBoundAbilityName().equalsIgnoreCase("OrbitalStrike")) {
            if (event.isSneaking()) {
                if (!CoreAbility.hasAbility(player, OrbitalStrike.class)) {
                    new OrbitalStrike(player);
                }
            } else {
                if (CoreAbility.hasAbility(player, OrbitalStrike.class)) {
                    CoreAbility.getAbility(player, OrbitalStrike.class).onSneak();
                }
            }
        }
    }
}
