package io.github.md5sha256.recipeviewer.gui;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

/** Identifies this plugin's category inventories for independent item protection. */
public final class RecipeCategoryGui extends ChestGui {
    public RecipeCategoryGui(Component title, Plugin plugin) {
        super(6, ComponentHolder.of(title), plugin);
    }
}
