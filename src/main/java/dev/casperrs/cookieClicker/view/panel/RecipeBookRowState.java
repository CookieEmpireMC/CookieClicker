package dev.casperrs.cookieClicker.view.panel;

/** The Recipe Book row on the Bakery Shop: a fixed bright link (a click opens the book), nothing rolls. */
public record RecipeBookRowState() implements PanelRowState {

    public static final RecipeBookRowState INSTANCE = new RecipeBookRowState();

    @Override
    public double cost() {
        return 0;
    }

    @Override
    public double gain() {
        return 0;
    }

    @Override
    public String colorKey() {
        return RowState.Style.AFFORDABLE.key();
    }

    @Override
    public boolean bright() {
        return true;
    }

    @Override
    public boolean rolls() {
        return false;
    }
}
