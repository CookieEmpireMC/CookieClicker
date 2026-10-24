package dev.casperrs.cookieClicker.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.storage.ConnectionFactory;
import dev.casperrs.cookieClicker.storage.DatabaseSettings;
import dev.casperrs.cookieClicker.storage.DriverConnectionFactory;
import dev.casperrs.cookieClicker.storage.H2Migration;
import dev.casperrs.cookieClicker.storage.StorageException;
import dev.casperrs.cookieClicker.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /bakery db migrate [--force]}: copies the H2 file at {@code database.h2.file} into the running MariaDB.
 * Goes through {@link SessionManager#runExclusive}: online players are saved and unloaded first, the copy runs on the
 * storage thread after those saves, and everyone loads again from the migrated data afterwards.
 */
public final class DbCommand {

    /** What the storage thread found; handled on the main thread. */
    private record Outcome(boolean noH2File, H2Migration.@Nullable Result result, @Nullable String problem) {
    }

    private final Supplier<PluginSettings> settings;
    private final SessionManager sessions;
    private final ConnectionFactory target;
    private final Messages messages;
    private final Logger log;

    /** {@code target} is the running store's pool (the configured MariaDB). */
    public DbCommand(Supplier<PluginSettings> settings, SessionManager sessions, ConnectionFactory target,
                     Messages messages, Logger log) {
        this.settings = settings;
        this.sessions = sessions;
        this.target = target;
        this.messages = messages;
        this.log = log;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("db")
                .then(Commands.literal("migrate")
                        .executes(ctx -> migrate(ctx.getSource().getSender(), false))
                        .then(Commands.literal("--force")
                                .executes(ctx -> migrate(ctx.getSource().getSender(), true))));
    }

    private int migrate(Audience sender, boolean force) {
        DatabaseSettings db = settings.get().database(); // the running database (reload keeps it until a restart)
        if (db.type() != DatabaseSettings.Type.MARIADB) {
            messages.send(sender, "db.migrate.not-mariadb");
            return 0;
        }
        DatabaseSettings h2 = db.asH2();
        boolean started = sessions.runExclusive(() -> copy(h2, force), outcome -> report(sender, h2, outcome));
        if (!started) {
            messages.send(sender, "db.migrate.running");
            return 0;
        }
        messages.send(sender, "db.migrate.started", Placeholder.unparsed("id", h2.h2DataFile().toString()));
        return Command.SINGLE_SUCCESS;
    }

    /** Storage thread. */
    private Outcome copy(DatabaseSettings h2, boolean force) {
        if (!Files.isRegularFile(h2.h2DataFile())) {
            return new Outcome(true, null, null);
        }
        try {
            H2Migration.Result result = new H2Migration(new DriverConnectionFactory(h2), target, h2.tablePrefix())
                    .run(force);
            if (!result.refused()) {
                log.info("db migrate: copied " + result.copied() + " from " + h2.describe());
            }
            return new Outcome(false, result, null);
        } catch (StorageException e) {
            log.log(Level.SEVERE, "db migrate from " + h2.describe() + " failed", e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new Outcome(false, null, String.valueOf(cause.getMessage()));
        }
    }

    /** Main thread. */
    private void report(Audience sender, DatabaseSettings h2, @Nullable Outcome outcome) {
        if (outcome == null) {
            messages.send(sender, "db.migrate.failed", Placeholder.unparsed("problem", "unexpected error"));
        } else if (outcome.noH2File()) {
            messages.send(sender, "db.migrate.no-h2-file", Placeholder.unparsed("id", h2.h2DataFile().toString()));
        } else if (outcome.problem() != null) {
            messages.send(sender, "db.migrate.failed", Placeholder.unparsed("problem", outcome.problem()));
        } else if (outcome.result().refused()) {
            messages.send(sender, "db.migrate.not-empty",
                    Placeholder.unparsed("count", Integer.toString(outcome.result().existingPlayers())));
        } else {
            for (Map.Entry<String, Integer> table : outcome.result().copied().entrySet()) {
                messages.send(sender, "db.migrate.table", Placeholder.unparsed("id", table.getKey()),
                        Placeholder.unparsed("count", Integer.toString(table.getValue())));
            }
            messages.send(sender, "db.migrate.done",
                    Placeholder.unparsed("count", Integer.toString(outcome.result().total())));
        }
    }
}
