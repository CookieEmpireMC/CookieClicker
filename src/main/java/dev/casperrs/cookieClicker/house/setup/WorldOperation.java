package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One world-writing command at a time, hopping between the main and the IO thread. Every step checks the
 * operation's {@link OperationGate} token first, so after a {@link #reset()} (reload, disable) late callbacks neither
 * change the world nor end a newer operation. Main-thread API except {@link #finish}.
 */
final class WorldOperation {

    /** One running command: who to answer, and its gate token. */
    record Op(CommandSender sender, long token) {
    }

    private final Logger log;
    private final Messages messages;
    private final Executor io;
    private final Executor main;
    private final String busyPath;
    private final String failedPath;
    private final String label;
    private final OperationGate gate = new OperationGate();
    /** Set by {@link #submit} while a {@link #step} runs: the operation continues on the IO thread. */
    private boolean handedOff;

    /** Message paths for "busy" and "failed" ({@code <problem>} placeholder); {@code label} is log text. */
    WorldOperation(Logger log, Messages messages, Executor io, Executor main, String busyPath, String failedPath,
                   String label) {
        this.log = log;
        this.messages = messages;
        this.io = io;
        this.main = main;
        this.busyPath = busyPath;
        this.failedPath = failedPath;
        this.label = label;
    }

    /** Starts an operation, or tells the sender another one runs (null). */
    @Nullable Op begin(CommandSender sender) {
        long token = gate.begin();
        if (token == OperationGate.REFUSED) {
            messages.send(sender, busyPath);
            return null;
        }
        return new Op(sender, token);
    }

    boolean isCurrent(Op op) {
        return gate.valid(op.token());
    }

    /**
     * One main-thread step. A stale token runs nothing. Unless the step handed the operation to the IO thread, the
     * operation ends in {@code finally}, so an exception can never leave the gate busy.
     */
    void step(Op op, Runnable body) {
        if (!gate.valid(op.token())) {
            return;
        }
        handedOff = false;
        boolean completed = false;
        try {
            body.run();
            completed = true;
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, label + " failed", e);
            messages.send(op.sender(), failedPath, problem(e.getMessage()));
        } finally {
            if (!completed || !handedOff) {
                gate.end(op.token());
            }
            handedOff = false;
        }
    }

    /**
     * Main thread, once backup files are written: runs {@code apply} as a step if the operation is still current;
     * a cancelled one changes nothing and its now useless backup is deleted.
     */
    void stepOrDiscard(Op op, Path backupYml, Runnable apply) {
        if (gate.valid(op.token())) {
            step(op, apply);
        } else {
            deleteLater(backupYml);
        }
    }

    /** Inside a {@link #step}: the operation goes on in a later tick (a ticked task), so the step must not end it. */
    void hold() {
        handedOff = true;
    }

    /** Continues on IO; an exception there replies and ends the operation, a rejected task ends it right away. */
    void submit(Op op, Runnable task) {
        try {
            io.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.log(Level.SEVERE, label + " failed on the IO thread", e);
                    finish(op, failedPath, problem(e.getMessage()));
                }
            });
            handedOff = true;
        } catch (RuntimeException e) {
            gate.end(op.token());
            messages.send(op.sender(), failedPath, problem("server is shutting down"));
        }
    }

    /** From the IO thread: hop to main, end the operation, reply. */
    void finish(Op op, String path, TagResolver... resolvers) {
        main.execute(() -> finishNow(op, path, resolvers));
    }

    /** Ends the operation and replies; a stale operation stays silent and leaves the gate alone. */
    void finishNow(Op op, String path, TagResolver... resolvers) {
        if (!gate.valid(op.token())) {
            return;
        }
        gate.end(op.token());
        messages.send(op.sender(), path, resolvers);
    }

    /** Deletes a backup pair on the IO thread; when IO already stopped (disable) a stale backup is harmless. */
    void deleteLater(Path backupYml) {
        try {
            io.execute(() -> BackupFiles.deleteQuietly(log, backupYml));
        } catch (RuntimeException e) {
            log.fine("Left a cancelled backup behind: " + backupYml);
        }
    }

    void reset() {
        gate.reset();
    }

    static TagResolver problem(@Nullable String text) {
        return Placeholder.unparsed("problem", String.valueOf(text));
    }
}
