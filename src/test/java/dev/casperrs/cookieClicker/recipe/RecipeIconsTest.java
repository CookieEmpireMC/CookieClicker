package dev.casperrs.cookieClicker.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What the Recipe Book icons print for a stock that production left a hair off a whole number. */
class RecipeIconsTest {

    @Test
    void aStockAHairBelowAWholeNumberShowsThatNumber() {
        double flour = 0;
        for (int i = 0; i < 10; i++) {
            flour += 0.2;
        }
        assertEquals(2.0, RecipeIcons.shownHave(flour), "10 x 0.2 sums to 1.9999999999999998");
        assertEquals(1.0, RecipeIcons.shownHave(1.5), "real fractions still round down");
        assertEquals(0.0, RecipeIcons.shownHave(0));
    }

    @Test
    void aMissingAmountAHairAboveAWholeNumberShowsThatNumber() {
        assertEquals(2.0, RecipeIcons.shownMissing(2.0000000000000004));
        assertEquals(1.0, RecipeIcons.shownMissing(0.5), "real fractions still round up");
        assertEquals(0.0, RecipeIcons.shownMissing(0));
    }
}
