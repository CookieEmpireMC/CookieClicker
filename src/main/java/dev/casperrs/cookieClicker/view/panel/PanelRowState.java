package dev.casperrs.cookieClicker.view.panel;

/** What a panel row shows (buildings: {@link RowState}, click upgrades: {@link UpgradeRowState}). Pure Java. */
public sealed interface PanelRowState permits RowState, UpgradeRowState {

    /** The price shown (rolls smoothly to a new value). */
    double cost();

    /** The +CPS / +CPC shown (rolls smoothly to a new value). */
    double gain();

    /** Key of the row background colour. */
    String colorKey();

    /** Bright icon (affordable) or dimmed. */
    boolean bright();

    /** Whether the row shows cost and gain at all (locked and maxed rows don't, so they never roll). */
    boolean rolls();
}
