package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

/** Sends {@code path} with {@code <percent>} each time another {@value #STEP_PERCENT}% of a ticked job is done. */
final class PasteProgress {

    private static final int STEP_PERCENT = 10;

    private final Messages messages;
    private final CommandSender sender;
    private final String path;
    private final long total;
    private long done;
    private int lastStep;

    PasteProgress(Messages messages, CommandSender sender, String path, long total) {
        this.messages = messages;
        this.sender = sender;
        this.path = path;
        this.total = Math.max(1, total);
    }

    void add(long n) {
        done += n;
        int step = (int) (done * 100 / total / STEP_PERCENT);
        if (step > lastStep && done < total) {
            lastStep = step;
            messages.send(sender, path, Placeholder.unparsed("percent", Integer.toString(step * STEP_PERCENT)));
        }
    }
}
