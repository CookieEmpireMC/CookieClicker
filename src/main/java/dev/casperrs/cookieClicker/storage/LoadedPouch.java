package dev.casperrs.cookieClicker.storage;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;

import java.util.List;

/** A loaded save plus what was wrong with it and got repaired (NaN/negative values, bad rows). */
public record LoadedPouch(PouchSnapshot snapshot, List<String> problems) {
    public LoadedPouch {
        problems = List.copyOf(problems);
    }
}
