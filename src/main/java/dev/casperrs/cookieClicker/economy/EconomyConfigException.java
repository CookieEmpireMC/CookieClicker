package dev.casperrs.cookieClicker.economy;

/** A balance file is missing a field or has an invalid value; the message names the file and key. */
public class EconomyConfigException extends Exception {
    public EconomyConfigException(String message) {
        super(message);
    }
}
