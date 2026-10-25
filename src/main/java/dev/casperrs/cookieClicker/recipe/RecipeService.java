package dev.casperrs.cookieClicker.recipe;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.PurchaseResult;
import dev.casperrs.cookieClicker.economy.RecipeBook;
import dev.casperrs.cookieClicker.economy.RecipePurchase;
import dev.casperrs.cookieClicker.economy.RecipeStatus;
import dev.casperrs.cookieClicker.hud.Hud;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The player-facing side of the Recipe Book (what the GUI calls): recipe states for a player and buying one with its
 * feedback ({@code recipe_buy} sound + effect at the player, or the {@code cant_afford} sound), then HUD and panels.
 * Main thread only.
 */
public final class RecipeService {

    static final String RECIPE_BUY = "recipe_buy";
    static final String DENIED = "cant_afford";

    private final SessionManager sessions;
    private final Economy economy;
    private final RecipeBook book;
    private final SoundService sounds;
    private final Effects effects;
    private final Hud hud;
    private BiConsumer<Player, CookiePouch> cpsChanged = (player, pouch) -> { };

    public RecipeService(SessionManager sessions, Economy economy, RecipeBook book, SoundService sounds,
                         Effects effects, Hud hud) {
        this.sessions = sessions;
        this.economy = economy;
        this.book = book;
        this.sounds = sounds;
        this.effects = effects;
        this.hud = hud;
    }

    /** Runs after every recipe bought (the panels show the new CPS). */
    public void setCpsChanged(BiConsumer<Player, CookiePouch> cpsChanged) {
        this.cpsChanged = cpsChanged;
    }

    /** Owns the Recipe Book; false while the save is still loading. */
    public boolean unlocked(Player player) {
        return sessions.get(player.getUniqueId()).map(economy::recipesUnlocked).orElse(false);
    }

    /** Every recipe in {@code upgrades.yml} order; empty while the save is still loading. */
    public List<RecipeStatus> statuses(Player player) {
        return sessions.get(player.getUniqueId()).map(book::statuses).orElse(List.of());
    }

    public Optional<RecipeStatus> status(Player player, String recipeId) {
        return sessions.get(player.getUniqueId()).flatMap(pouch -> book.status(pouch, recipeId));
    }

    /** The player's stock of one ingredient (0 while loading). */
    public double stock(Player player, String ingredientId) {
        return sessions.get(player.getUniqueId()).map(p -> p.ingredient(ingredientId)).orElse(0.0);
    }

    /**
     * Buys a recipe atomically (see {@link RecipeBook#buy}) and plays the feedback. UNKNOWN (status null) also while
     * the save is still loading; on CANT_AFFORD the status says what is missing.
     */
    public RecipePurchase buy(Player player, String recipeId) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null) {
            return new RecipePurchase(PurchaseResult.UNKNOWN, null);
        }
        RecipePurchase result = book.buy(pouch, recipeId);
        Location at = player.getLocation();
        if (!result.bought()) {
            sounds.play(player, DENIED, at);
            return result;
        }
        effects.play(RECIPE_BUY, at, player);
        sounds.play(player, RECIPE_BUY, at);
        hud.refresh(player, pouch);
        cpsChanged.accept(player, pouch);
        return result;
    }
}
