package com.visualindicators.listener;

import com.visualindicators.VisualIndicatorsPlugin;
import com.visualindicators.config.PluginSettings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class CombatIndicatorListener implements Listener {
    private final VisualIndicatorsPlugin plugin;

    public CombatIndicatorListener(VisualIndicatorsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent damageByEntityEvent)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        PluginSettings.CombatSettings combat = this.plugin.settings().combat();
        if (!combat.enabled()) {
            return;
        }
        if (victim instanceof Player && !combat.showOnPlayers()) {
            return;
        }
        if (combat.worldDisabled(victim.getWorld().getName())) {
            return;
        }
        if (!combat.shouldShow(victim.getType())) {
            return;
        }

        Player attacker = getAttackingPlayer(damageByEntityEvent.getDamager());
        if (attacker == null) {
            return;
        }
        if (!this.plugin.preferencesStore().isCombatEnabled(attacker.getUniqueId())) {
            return;
        }

        double damage = event.getFinalDamage();
        if (damage <= 0.0D) {
            return;
        }

        this.plugin.indicatorService().spawnCombatIndicator(attacker, victim, damage);
    }

    private Player getAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
