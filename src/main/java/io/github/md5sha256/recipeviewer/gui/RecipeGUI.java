package io.github.md5sha256.recipeviewer.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.Pane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.component.PagingButtons;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import io.github.md5sha256.recipeviewer.CategoryRegistry;
import io.github.md5sha256.recipeviewer.model.BrewingRecipeList;
import io.github.md5sha256.recipeviewer.model.RecipeCategory;
import io.github.md5sha256.recipeviewer.model.RecipeCategoryPointer;
import io.github.md5sha256.recipeviewer.model.RecipeElement;
import io.github.md5sha256.recipeviewer.model.RecipeList;
import io.github.md5sha256.recipeviewer.recipe.CustomBrewingRecipe;
import io.github.md5sha256.recipeviewer.renderer.Renderers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeGUI {

    private final Renderers renderers;
    private final CategoryRegistry registry;
    private final Plugin plugin;
    private final Server server;

    public RecipeGUI(@Nonnull Renderers renderers,
                     @Nonnull CategoryRegistry registry,
                     @Nonnull Plugin plugin,
                     @Nonnull Server server) {
        this.renderers = renderers;
        this.registry = registry;
        this.plugin = plugin;
        this.server = server;
    }

    public ChestGui createGui(@Nonnull Component title, @Nonnull List<? extends RecipeElement> recipeElements, @Nullable Gui parent) {
        ChestGui gui = new RecipeCategoryGui(title, this.plugin);
        List<GuiItem> items = new ArrayList<>();
        for (RecipeElement element : recipeElements) {
            if (element instanceof RecipeCategoryPointer(String categoryName)) {
                Optional<RecipeCategory> optional = this.registry.getByName(categoryName);
                if (optional.isEmpty()) {
                    this.plugin.getLogger().warning("Missing category: " + categoryName);
                    continue;
                }
                RecipeCategory recipeCategory = optional.get();
                GuiItem item = createCategoryItem(recipeCategory);
                item.setAction(event -> {
                    ChestGui subcatGui = createGui(recipeCategory, gui);
                    subcatGui.show(event.getWhoClicked());
                });
                items.add(item);
            } else if (element instanceof RecipeList(List<Recipe> recipes)) {
                items.addAll(recipes.stream().map(recipe -> createRecipeItem(recipe, gui)).toList());
            } else if (element instanceof BrewingRecipeList(List<CustomBrewingRecipe> brewingRecipes)) {
                items.addAll(brewingRecipes.stream()
                        .map(brewingRecipe -> createBrewingRecipeItem(brewingRecipe, gui))
                        .toList());
            }
        }
        PaginatedPane mainPane = new PaginatedPane(9, 5);
        mainPane.populateWithGuiItems(items);


        StaticPane footerPane = getFooterPane(parent);

        ItemStack fillItem = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        fillItem.editMeta(meta -> meta.displayName(Component.empty()));
        footerPane.fillWith(fillItem, null, this.plugin);

        PagingButtons pagingButtons = getPagingButtons(mainPane);

        gui.addPane(Slot.fromXY(0, 0), mainPane);
        gui.addPane(Slot.fromXY(0, 5), footerPane);
        gui.addPane(Slot.fromXY(3, 5), pagingButtons);
        gui.setOnGlobalClick(event -> event.setCancelled(true));
        if (parent != null) {
            gui.setOnClose(event -> {
                // Don't force-open the parent gui if the reason is OPEN_NEW
                if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) {
                    return;
                }
                // Delay opening the ui 1 tick later otherwise all IF listeners will break
                this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                    parent.show(event.getPlayer());
                }, 1);
            });
        }
        return gui;
    }

    public ChestGui createGui(@Nonnull RecipeCategory category) {
        return createGui(category, null);
    }

    public ChestGui createGui(@Nonnull RecipeCategory category, @Nullable Gui parent) {
        return createGui(category.displayName(), category.elements(), parent);
    }

    private @NotNull StaticPane getFooterPane(@Nullable Gui parent) {
        StaticPane footerPane = new StaticPane(9, 1, Pane.Priority.LOWEST);
        ItemStack backItem = ItemStack.of(Material.ARROW);
        backItem.editMeta(meta -> {
            Component displayName = Component.text("Back", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
        });
        GuiItem backButton = new GuiItem(backItem, event -> {
            event.getView().close();
            if (parent != null) {
                parent.show(event.getWhoClicked());
            }
        }, this.plugin);
        footerPane.addItem(backButton, 4, 0);
        return footerPane;
    }

    private @NotNull PagingButtons getPagingButtons(PaginatedPane mainPane) {
        PagingButtons pagingButtons = new PagingButtons(3,
                Pane.Priority.HIGH,
                mainPane,
                this.plugin);
        Component nextPageComp = Component.text("Next Page")
                .decoration(TextDecoration.ITALIC, false);
        Component prevPageComp = Component.text("Prev Page")
                .decoration(TextDecoration.ITALIC, false);
        ItemStack nextButton = ItemStack.of(Material.PAPER);
        nextButton.editMeta(meta -> meta.displayName(nextPageComp));
        ItemStack prevButton = ItemStack.of(Material.PAPER);
        prevButton.editMeta(meta -> meta.displayName(prevPageComp));
        pagingButtons.setForwardButton(new GuiItem(nextButton, this.plugin));
        pagingButtons.setBackwardButton(new GuiItem(prevButton, this.plugin));
        return pagingButtons;
    }

    private GuiItem createCategoryItem(@Nonnull RecipeCategory category) {
        ItemStack icon = category.icon();
        Component displayName = category.displayName().decoration(TextDecoration.ITALIC, false);
        icon.editMeta(meta -> {
            meta.displayName(displayName);
            meta.lore(null);
        });
        return new GuiItem(icon, this.plugin);
    }


    @Nonnull
    private GuiItem createRecipeItem(@Nonnull Recipe recipe, @Nonnull Gui returnGui) {
        ItemStack icon = recipe.getResult().asOne();
        return new GuiItem(icon, event -> {
            if (!this.renderers.tryRenderRecipe(this.server, event.getWhoClicked(), recipe, returnGui)) {
                Component msg = Component.text("Recipe type not supported: " + recipe.getClass(),
                        NamedTextColor.RED);
                event.getWhoClicked().sendMessage(msg);
            }
        }, this.plugin);
    }

    private GuiItem createBrewingRecipeItem(@Nonnull CustomBrewingRecipe recipe, @Nonnull Gui returnGui) {
        ItemStack icon;
        List<ItemStack> outputs = recipe.outputs();
        if (!outputs.isEmpty()) {
            icon = outputs.getFirst().asOne();
        } else {
            icon = ItemStack.of(Material.BARRIER);
        }
        return new GuiItem(icon, event -> {
            if (!this.renderers.tryRenderRecipe(this.server, event.getWhoClicked(), recipe, returnGui)) {
                Component msg = Component.text("Recipe type not supported: " + recipe.getClass(),
                        NamedTextColor.RED);
                event.getWhoClicked().sendMessage(msg);
            }
        }, this.plugin);
    }

}
