package io.github.md5sha256.recipeviewer;

import com.github.stefvanschie.inventoryframework.gui.GuiListener;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import io.github.md5sha256.recipeviewer.gui.RecipeCategoryGui;
import io.github.md5sha256.recipeviewer.util.RecipeView;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryProtectionTest {
    private MockedStatic<Bukkit> bukkit;
    private Plugin plugin;
    private InventoryListener guard;
    private GuiListener framework;
    private Inventory top;
    private PlayerInventory bottom;
    private InventoryView view;
    private Player player;
    private RecipeCategoryGui category;

    @BeforeAll static void initializeActualRuntime() throws Exception {
        if (Boolean.getBoolean("recipeviewer.actualPaper")) {
            Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
            Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        }
    }

    @BeforeEach void setup() throws Exception {
        Server server = mock(Server.class);
        when(server.getBukkitVersion()).thenReturn("26.3.build.206-stable");
        when(server.getMinecraftVersion()).thenReturn("26.3");
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getBukkitVersion).thenReturn("26.3.build.206-stable");
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        plugin = mock(Plugin.class);
        guard = new InventoryListener(plugin);
        framework = new GuiListener(plugin);
        category = new RecipeCategoryGui(Component.text("Recipes"), plugin);
        top = mock(Inventory.class);
        bottom = mock(PlayerInventory.class);
        when(bottom.getSize()).thenReturn(36);
        when(top.getHolder()).thenReturn(category);
        when(top.getSize()).thenReturn(54);
        player = mock(Player.class);
        when(player.getInventory()).thenReturn(bottom);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(view.getPlayer()).thenReturn(player);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getInventory(anyInt())).thenAnswer(call -> {
            int slot = call.getArgument(0);
            return slot < 0 ? null : slot < 54 ? top : bottom;
        });
        when(view.convertSlot(anyInt())).thenAnswer(call -> {
            int slot = call.getArgument(0); return slot < 54 ? slot : slot - 54;
        });
    }

    @AfterEach void closeBoundary() { if (bukkit != null) bukkit.close(); }

    private InventoryClickEvent click(int slot, ClickType type, InventoryAction action) {
        return type == ClickType.NUMBER_KEY
                ? new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, action, 2)
                : new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, action);
    }

    @ParameterizedTest @EnumSource(value=ClickType.class, names={"LEFT","RIGHT","SHIFT_LEFT","SHIFT_RIGHT","NUMBER_KEY","DROP","CONTROL_DROP","SWAP_OFFHAND","DOUBLE_CLICK"})
    void categoryCannotTransferDisplayItems(ClickType type) {
        InventoryAction action = switch (type) {
            case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case NUMBER_KEY, SWAP_OFFHAND -> InventoryAction.HOTBAR_SWAP;
            case DROP -> InventoryAction.DROP_ONE_SLOT;
            case CONTROL_DROP -> InventoryAction.DROP_ALL_SLOT;
            case DOUBLE_CLICK -> InventoryAction.COLLECT_TO_CURSOR;
            default -> InventoryAction.PICKUP_ALL;
        };
        InventoryClickEvent event = click(0, type, action);
        framework.onInventoryClick(event);
        guard.onInventoryClick(event);
        assertTrue(event.isCancelled(), type.toString());
    }

    @Test void outsideClickAndShiftFromPlayerInventoryCannotMoveItems() {
        for (int slot : new int[]{-999, 54}) {
            InventoryClickEvent event=click(slot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
            framework.onInventoryClick(event);
            guard.onInventoryClick(event);
            assertTrue(event.isCancelled(), "slot="+slot);
        }
    }

    @Test void navigationCallbackRunsBeforeIndependentProtection() throws Exception {
        StaticPane pane = new StaticPane(9, 5);
        AtomicInteger navigations = new AtomicInteger();
        pane.setOnClick(event -> navigations.incrementAndGet());
        category.addPane(com.github.stefvanschie.inventoryframework.pane.util.Slot.fromXY(0,0), pane);
        category.setOnGlobalClick(event -> event.setCancelled(true));
        InventoryClickEvent event = click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        // Bukkit runs NORMAL IF handlers before our HIGHEST fallback.
        assertEquals(EventPriority.HIGHEST, InventoryListener.class
                .getMethod("onInventoryClick", InventoryClickEvent.class)
                .getAnnotation(EventHandler.class).priority());
        framework.onInventoryClick(event);
        guard.onInventoryClick(event);
        assertEquals(1, navigations.get());
        assertTrue(event.isCancelled());
    }

    @Test void categoryIsProtectedEvenIfFrameworkNeverHandlesClick() {
        InventoryClickEvent event=click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        guard.onInventoryClick(event);
        assertTrue(event.isCancelled());
    }

    @Test void detailViewRemainsProtected() {
        RecipeView detail = new RecipeView(holder -> top);
        when(top.getHolder()).thenReturn(detail);
        InventoryClickEvent event=click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        guard.onInventoryClick(event);
        assertTrue(event.isCancelled());
    }

    @Test void dragAndCreativeCannotPlaceOrCloneDisplayItems() {
        InventoryDragEvent drag = new InventoryDragEvent(view, null, mock(ItemStack.class), false, Map.of(0, mock(ItemStack.class),54,mock(ItemStack.class)));
        framework.onInventoryDrag(drag);
        guard.onInventoryDrag(drag);
        assertTrue(drag.isCancelled());
        InventoryCreativeEvent creative = new InventoryCreativeEvent(view, InventoryType.SlotType.CONTAINER,0,mock(ItemStack.class));
        guard.onInventoryCreative(creative);
        assertTrue(creative.isCancelled());
    }

    @Test void singleSlotDragCannotBeUncancelledByFramework() throws Exception {
        InventoryDragEvent event = new InventoryDragEvent(view, null, mock(ItemStack.class), false,
                Map.of(0, mock(ItemStack.class)));
        assertEquals(EventPriority.HIGHEST, InventoryListener.class
                .getMethod("onInventoryDrag", InventoryDragEvent.class)
                .getAnnotation(EventHandler.class).priority());
        framework.onInventoryDrag(event);
        guard.onInventoryDrag(event);
        assertTrue(event.isCancelled());
    }

    @Test void realPaperInventoryViewProtectsCategoryAndOutsideSlots() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("recipeviewer.actualPaper"));
        Class<?> inventoryClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftInventoryCustom");
        Inventory realTop = (Inventory) inventoryClass.getConstructor(InventoryHolder.class, int.class)
                .newInstance(category, 54);
        Player realPlayer = (Player) mock(Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer"));
        when(realPlayer.getInventory()).thenReturn(bottom);
        Class<?> menuClass = Class.forName("net.minecraft.world.inventory.AbstractContainerMenu");
        Object menu = mock(menuClass);
        Object contents = Class.forName("net.minecraft.world.SimpleContainer").getConstructor(int.class).newInstance(90);
        var slotConstructor = Class.forName("net.minecraft.world.inventory.Slot")
                .getConstructor(Class.forName("net.minecraft.world.Container"), int.class, int.class, int.class);
        when(menuClass.getMethod("getSlot", int.class).invoke(menu, anyInt())).thenAnswer(call ->
                slotConstructor.newInstance(contents, call.getArgument(0), 0, 0));
        InventoryView realView = (InventoryView) Class.forName("org.bukkit.craftbukkit.inventory.CraftInventoryView")
                .getConstructor(org.bukkit.entity.HumanEntity.class, Inventory.class, menuClass)
                .newInstance(realPlayer, realTop, menu);
        for (int slot : new int[]{0, 53, 54, -999}) {
            InventoryClickEvent event = new InventoryClickEvent(realView, InventoryType.SlotType.CONTAINER,
                    slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            framework.onInventoryClick(event);
            guard.onInventoryClick(event);
            assertTrue(event.isCancelled(), "real Paper slot="+slot);
        }
    }

    @Test void normalInventoryClicksAndPickupAreUnaffected() {
        when(top.getHolder()).thenReturn(null);
        InventoryClickEvent event=click(54,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        framework.onInventoryClick(event);
        guard.onInventoryClick(event);
        assertFalse(event.isCancelled());
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(player, mock(Item.class),0);
        framework.onEntityPickupItem(pickup);
        assertFalse(pickup.isCancelled());
        verifyNoInteractions(pickup.getItem());
    }

    @Test void inventoryPickupCannotInsertItemsIntoCategoryButOrdinaryInventoryIsUnaffected() {
        InventoryPickupItemEvent categoryPickup = new InventoryPickupItemEvent(top, mock(Item.class));
        guard.onInventoryItemPickup(categoryPickup);
        assertTrue(categoryPickup.isCancelled());
        InventoryPickupItemEvent ordinaryPickup = new InventoryPickupItemEvent(mock(Inventory.class), mock(Item.class));
        guard.onInventoryItemPickup(ordinaryPickup);
        assertFalse(ordinaryPickup.isCancelled());
    }

    @Test void pickupWhileCategoryOpenStaysInLegitimatePlayerInventory() {
        Item item=mock(Item.class);
        EntityPickupItemEvent pickup=new EntityPickupItemEvent(player,item,0);
        framework.onEntityPickupItem(pickup);
        assertFalse(pickup.isCancelled());
        verifyNoInteractions(item);
    }
}
