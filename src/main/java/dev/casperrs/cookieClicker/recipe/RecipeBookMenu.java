package dev.casperrs.cookieClicker.recipe;

import dev.casperrs.cookieClicker.config.RecipeGuiSettings;
import dev.casperrs.cookieClicker.economy.RecipeStatus;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The Recipe Book chest GUI: a 4-row {@link MenuType#GENERIC_9X4} with the textured title (players with the pack)
 * or plain text, one icon per recipe in {@code recipes.gui.slots}. Recognised by its view (never by title); every
 * click and drag in it is cancelled, a left/right click on an available recipe buys it and the slots update in place.
 * Main thread only.
 */
public final class RecipeBookMenu implements Listener {

    static final String OPEN_SOUND = "recipe_book_open";

    /** One open book: its view and the key of what each top slot shows (null = empty). */
    private record Open(InventoryView view, @Nullable String[] keys) {
    }

    private final RecipeService recipes;
    private final Messages messages;
    private final Supplier<RecipeGuiSettings> settings;
    private final Predicate<Player> hasPack;
    private final SoundService sounds;
    private final RecipeIcons icons;
    private final Map<UUID, Open> open = new HashMap<>();
    private int titleRevision = Integer.MIN_VALUE;
    private Component plainTitle = Component.empty();
    private Component packTitle = Component.empty();

    public RecipeBookMenu(RecipeService recipes, Messages messages, Supplier<RecipeGuiSettings> settings,
                          Predicate<Player> hasPack, SoundService sounds, Logger log) {
        this.recipes = recipes;
        this.messages = messages;
        this.settings = settings;
        this.hasPack = hasPack;
        this.sounds = sounds;
        this.icons = new RecipeIcons(messages, settings, new WarnOnce(log));
    }

    /** Opens the book; false (with the "buy the Recipe Book first" line) before it is owned. */
    public boolean open(Player player) {
        if (!recipes.unlocked(player)) {
            messages.send(player, "gui.recipes.need-book");
            return false;
        }
        InventoryView view = MenuType.GENERIC_9X4.builder().title(title(hasPack.test(player))).build(player);
        Open book = new Open(view, new String[RecipeGuiSettings.SIZE]);
        fill(player, book);
        view.open();
        open.put(player.getUniqueId(), book); // after open(): closing a previous book must not drop this one
        sounds.play(player, OPEN_SOUND, player.getLocation());
        return true;
    }

    /** Re-sends the slots whose look changed (stock grew, a recipe unlocked); no-op when the book isn't open. */
    public void refresh(Player player) {
        Open book = open.get(player.getUniqueId());
        if (book != null) {
            fill(player, book);
        }
    }

    /** Closes every open book (reload, shutdown): its items are display-only. */
    public void closeAll() {
        for (UUID id : List.copyOf(open.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && book(p.getOpenInventory()) != null) {
                p.closeInventory(); // its close event removes the entry
            }
        }
        open.clear();
    }

    private void fill(Player player, Open book) {
        boolean withPack = hasPack.test(player);
        RecipeGuiSettings s = settings.get();
        List<RecipeStatus> statuses = recipes.statuses(player);
        for (int i = 0; i < statuses.size(); i++) {
            int slot = s.slot(i);
            if (slot < 0) {
                continue;
            }
            RecipeStatus status = statuses.get(i);
            String key = icons.key(status, id -> recipes.stock(player, id), withPack);
            if (!key.equals(book.keys()[slot])) {
                book.keys()[slot] = key;
                book.view().getTopInventory().setItem(slot, icons.icon(status, id -> recipes.stock(player, id), withPack));
            }
        }
    }

    private Component title(boolean withPack) {
        if (titleRevision != messages.revision()) {
            titleRevision = messages.revision();
            plainTitle = messages.component("gui.recipes.title");
            // the glyphs must stay white (bitmap colours are multiplied by the text colour) and in their own child,
            // so the readable title keeps the default font and the vanilla title colour
            packTitle = Component.text()
                    .append(Component.text(RecipeGlyphs.TITLE_PREFIX).color(NamedTextColor.WHITE)
                            .font(RecipeGlyphs.GUI_FONT))
                    .append(plainTitle)
                    .build();
        }
        return withPack ? packTitle : plainTitle;
    }

    private @Nullable Open book(InventoryView view) {
        if (!(view.getPlayer() instanceof Player p)) {
            return null;
        }
        Open book = open.get(p.getUniqueId());
        return book != null && (book.view() == view || book.view().getTopInventory().equals(view.getTopInventory()))
                ? book : null;
    }

    /** Also receives {@link InventoryCreativeEvent} (it shares this event's handler list): cancelled, never a buy. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Open book = book(event.getView());
        if (book == null) {
            return;
        }
        event.setCancelled(true); // bottom inventory, shift, number keys, offhand swap, double click, drop, outside
        if (event instanceof InventoryCreativeEvent || !(event.getWhoClicked() instanceof Player player)
                || !buys(event.getClick()) || event.getRawSlot() < 0 || event.getRawSlot() >= RecipeGuiSettings.SIZE) {
            return;
        }
        int index = settings.get().slots().indexOf(event.getRawSlot());
        List<RecipeStatus> statuses = recipes.statuses(player);
        if (index < 0 || index >= statuses.size()
                || statuses.get(index).state() != RecipeStatus.State.AVAILABLE) {
            return;
        }
        recipes.buy(player, statuses.get(index).recipe().id());
        fill(player, book);
    }

    private static boolean buys(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (book(event.getView()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (book(event.getView()) != null) {
            open.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
