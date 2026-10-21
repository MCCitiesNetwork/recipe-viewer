package io.github.md5sha256.recipeviewer;

import io.github.md5sha256.recipeviewer.util.RecipeView;
import io.github.md5sha256.recipeviewer.gui.RecipeCategoryGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class InventoryListener implements Listener {

    private final Plugin plugin;

    public InventoryListener(@Nonnull Plugin plugin) {
        this.plugin = plugin;
    }

    private boolean shouldCancel(@Nullable Inventory inventory) {
        return inventory != null && (inventory.getHolder() instanceof RecipeView
                || inventory.getHolder() instanceof RecipeCategoryGui);
    }

    private boolean shouldCancel(@NotNull InventoryView view) {
        return shouldCancel(view.getTopInventory()) || shouldCancel(view.getBottomInventory());
    }

    // Run after IF's NORMAL callback so navigation still works. Protect the view
    // independently even when IF throws before its global click handler runs.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Need to check the view because IF does not trigger the global
        // click listener if the clicked inventory is not a Gui
        // even if ANY inventory in the view is a Gui
        if (shouldCancel(event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryItemPickup(InventoryPickupItemEvent event) {
        if (shouldCancel(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (shouldCancel(event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (shouldCancel(event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof RecipeView recipeView)) {
            return;
        }
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) {
            return;
        }
        recipeView.returnGui().ifPresent(returnGui -> {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }
            // Delay opening the ui 1 tick later otherwise all IF listeners will break
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> returnGui.show(player), 1);
        });
    }

}
