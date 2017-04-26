/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.inventory.listeners;

import io.github.nucleuspowered.nucleus.Nucleus;
import io.github.nucleuspowered.nucleus.internal.CommandPermissionHandler;
import io.github.nucleuspowered.nucleus.internal.ListenerBase;
import io.github.nucleuspowered.nucleus.modules.inventory.commands.InvSeeCommand;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.filter.type.Exclude;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.type.CarriedInventory;

public class InvSeeListener extends ListenerBase {

    private CommandPermissionHandler invSeePermissionHandler =
            Nucleus.getNucleus().getPermissionRegistry().getPermissionsForNucleusCommand(InvSeeCommand.class);

    /**
     * Fired when a {@link Player} interacts with another's inventory.
     *
     * @param event The {@link ClickInventoryEvent} event to handle.
     * @param player The {@link Player} that is doing the interacting.
     * @param targetInventory The {@link CarriedInventory} that is being interacted with.
     */
    @Listener
    @Exclude({ClickInventoryEvent.Open.class, ClickInventoryEvent.Close.class})
    public void onInventoryChange(ClickInventoryEvent event, @Root Player player, @Getter("getTargetInventory") Inventory targetInventory) {

        // This seems to be throwing an NPE. Can't confirm, but will put this in debug mode.
        //noinspection ConstantConditions
        if (targetInventory == null) {
            if (plugin.isDebugMode()) {
                // Tell the console
                plugin.getLogger().warn("When trying to listen for the inventory events, targetInventory is null");
            }

            return;
        }

        if (!(targetInventory instanceof CarriedInventory)) {
            return;
        }

        CarriedInventory<?> carriedInventory = (CarriedInventory)targetInventory;
        //noinspection ConstantConditions
        if (carriedInventory.getCarrier() == null) {
            if (plugin.isDebugMode()) {
                // Tell the console
                plugin.getLogger().warn("When trying to listen for the inventory events, targetInventory#getCarrier() is null. "
                        + "THIS SHOULD NOT HAPPEN - this is a Sponge issue.");
            }

            return;
        }

        if (!carriedInventory.getCarrier().isPresent() || !(carriedInventory.getCarrier().get() instanceof Player)) {
            return;
        }

        Player target = (Player)carriedInventory.getCarrier().get();
        if (player.equals(target)) {
            return;
        }

        // Ok, so we're interacting with another subject's inventory.
        if (!invSeePermissionHandler.testSuffix(player, "modify") ||
                invSeePermissionHandler.testSuffix(target, "exempt.interact", player,false)) {
            event.getCursorTransaction().setValid(false);
            event.setCancelled(true);
        }
    }
}
