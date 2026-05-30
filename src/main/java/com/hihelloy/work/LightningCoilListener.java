package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class LightningCoilListener implements Listener {

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (!bPlayer.getBoundAbilityName().equalsIgnoreCase("LightningCoil")) return;
        if (event.isSneaking()) {
            if (!CoreAbility.hasAbility(player, LightningCoil.class)) {
                new LightningCoil(player);
            }
        } else {
            if (CoreAbility.hasAbility(player, LightningCoil.class)) {
                CoreAbility.getAbility(player, LightningCoil.class).onSneak();
            }
        }
    }
}
