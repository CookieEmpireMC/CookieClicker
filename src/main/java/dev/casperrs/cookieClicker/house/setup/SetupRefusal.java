package dev.casperrs.cookieClicker.house.setup;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** A setup check failed: the message to send instead. Nothing in the world was changed by the failing step. */
final class SetupRefusal extends Exception {

    private final String path;
    private final transient TagResolver[] resolvers;

    SetupRefusal(String path, TagResolver... resolvers) {
        super(path, null, false, false);
        this.path = path;
        this.resolvers = resolvers;
    }

    String path() {
        return path;
    }

    TagResolver[] resolvers() {
        return resolvers;
    }
}
