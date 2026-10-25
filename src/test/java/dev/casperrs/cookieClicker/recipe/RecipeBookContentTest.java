package dev.casperrs.cookieClicker.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The Recipe Book's code against the shipped messages, sounds, effects and resource pack. */
class RecipeBookContentTest {

    private static final Path PACK = Path.of("resourcepack/assets/cookieclicker");

    private static YamlConfiguration yaml(String name) {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/" + name));
    }

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    void soundAndEffectIdsTheBookPlaysExist() {
        YamlConfiguration sounds = yaml("sounds.yml");
        for (String id : List.of(RecipeBookMenu.OPEN_SOUND, RecipeService.RECIPE_BUY, RecipeService.DENIED)) {
            assertTrue(sounds.isConfigurationSection(id), "sounds.yml lacks " + id);
        }
        assertTrue(yaml("effects.yml").isConfigurationSection(RecipeService.RECIPE_BUY), "effects.yml lacks recipe_buy");
    }

    @Test
    void titleGlyphsAreDefinedInTheGuiFont() throws IOException {
        var providers = json(PACK.resolve("font/gui.json")).getAsJsonArray("providers");
        JsonObject advances = providers.get(0).getAsJsonObject().getAsJsonObject("advances");
        JsonObject bitmap = providers.get(1).getAsJsonObject();
        assertEquals(-8, advances.get(RecipeGlyphs.SHIFT_LEFT_8).getAsInt());
        assertEquals(-169, advances.get(RecipeGlyphs.BACK_TO_TITLE).getAsInt());
        assertEquals(RecipeGlyphs.BACKGROUND, bitmap.getAsJsonArray("chars").get(0).getAsString());
        assertTrue(Files.isRegularFile(PACK.resolve("textures/font/gui/recipe_book.png")));
    }

    @Test
    void ingredientIconsAreTheIngredientFontCharactersInOrder() throws IOException {
        String chars = json(PACK.resolve("font/ingredient.json")).getAsJsonArray("providers").get(0).getAsJsonObject()
                .getAsJsonArray("chars").get(0).getAsString();
        YamlConfiguration messages = yaml("messages.yml");
        List<String> ids = new ArrayList<>(messages.getConfigurationSection("ingredients").getKeys(false));
        assertEquals(chars.length(), ids.size(), "one glyph per ingredient");
        for (int i = 0; i < ids.size(); i++) {
            assertTrue(messages.getString("ingredients." + ids.get(i) + ".icon").contains("<font:cookieclicker:ingredient>"
                    + chars.charAt(i) + "</font>"), ids.get(i) + " icon differs from font/ingredient.json");
        }
    }

    @Test
    void everyRecipeHasItsTextTheLockedLookAndAnItemModel() throws IOException {
        YamlConfiguration messages = yaml("messages.yml");
        for (String key : List.of("title", "need-book", "locked-name", "locked", "flavour", "effect", "costs", "cookies",
                "cookies-missing", "ingredient", "ingredient-missing", "click", "short", "baked")) {
            assertTrue(messages.isString("gui.recipes." + key), "messages.yml lacks gui.recipes." + key);
        }
        assertTrue(messages.isString("panel.recipe-book.row"));
        assertTrue(messages.isString("commands.usage-player"));
        List<String> models = new ArrayList<>(List.of("locked"));
        models.addAll(yaml("upgrades.yml").getConfigurationSection("recipes").getKeys(false));
        for (String id : models) {
            if (!id.equals("locked")) {
                assertTrue(messages.isString("recipes." + id + ".name"), "no name for recipe " + id);
            }
            Path item = PACK.resolve("items/recipe/" + id + ".json");
            assertTrue(Files.isRegularFile(item), "no item definition for recipe/" + id);
            String model = json(item).getAsJsonObject("model").get("model").getAsString();
            assertTrue(Files.isRegularFile(PACK.resolve("models/" + model.substring("cookieclicker:".length()) + ".json")),
                    "no model " + model);
        }
        assertTrue(Files.isRegularFile(PACK.resolve("items/upgrade/recipe_book.json")));
    }
}
