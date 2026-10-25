package dev.casperrs.cookieClicker.recipe;

import net.kyori.adventure.key.Key;

/** Font and codepoints of the textured Recipe Book title (design/recipe-book-gui.md, font {@code cookieclicker:gui}). */
final class RecipeGlyphs {

    static final Key GUI_FONT = Key.key("cookieclicker", "gui");
    /** -8 space: the title starts 8 px right of the container's left edge. */
    static final String SHIFT_LEFT_8 = "";
    /** The 4-row background, 176x186, advance 177. */
    static final String BACKGROUND = "";
    /** -169 space: from the end of the background back to the vanilla title x (8). */
    static final String BACK_TO_TITLE = "";

    /** Background plus back-step, to be followed by the readable title in the default font. */
    static final String TITLE_PREFIX = SHIFT_LEFT_8 + BACKGROUND + BACK_TO_TITLE;

    private RecipeGlyphs() {
    }
}
