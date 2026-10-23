package dev.casperrs.cookieClicker;

/** One shutdown step of a built part; {@link PluginWiring#shutdownOrder()} lists them in the order they run. */
record PluginPart(String name, Runnable shutdown) {
}
