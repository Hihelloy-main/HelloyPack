package com.hihelloy.work;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PhoenixCoilListener implements Listener {

    @EventHandler
    public void onClick(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (event.isCancelled() || bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (!bPlayer.getBoundAbilityName().equalsIgnoreCase("PhoenixCoil")) return;
        if (!CoreAbility.hasAbility(player, PhoenixCoil.class)) {
            new PhoenixCoil(player);
        } else {
            CoreAbility.getAbility(player, PhoenixCoil.class).onLeftClick();
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        if (bPlayer.getBoundAbilityName() == null) return;
        if (!bPlayer.getBoundAbilityName().equalsIgnoreCase("PhoenixCoil")) return;
        if (event.isSneaking() && CoreAbility.hasAbility(player, PhoenixCoil.class)) {
            CoreAbility.getAbility(player, PhoenixCoil.class).onSneak();
        }
    }
}