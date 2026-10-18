package dev.casperrs.cookieClicker.economy;

/** What one click gave: the amount added (crit included) and whether it was a crispy crit. */
public record ClickResult(double amount, boolean crit) {
}
