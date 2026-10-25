package dev.casperrs.cookieClicker.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.casperrs.cookieClicker.config.PlotSettings;
import dev.casperrs.cookieClicker.farm.Farm;
import dev.casperrs.cookieClicker.farm.FarmMarks;
import dev.casperrs.cookieClicker.farm.FarmRegistry;
import dev.casperrs.cookieClicker.farm.FarmTeleports;
import dev.casperrs.cookieClicker.house.setup.FarmSetup;
import dev.casperrs.cookieClicker.plot.Outline;
import dev.casperrs.cookieClicker.plot.PlotOutlines;
import dev.casperrs.cookieClicker.plot.PlotRegions;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@code /bakery farm} and {@code /bakery home} (permission {@value #TELEPORT_PERMISSION}), and the admin commands
 * {@code /bakery farm setup|undo|show <id>} ({@link BakeryCommand#PERMISSION}).
 */
public final class FarmCommand {

    public static final String TELEPORT_PERMISSION = "cookieclicker.teleport";
    private static final String ID = "id";

    private final FarmRegistry farms;
    private final FarmSetup setup;
    private final FarmTeleports teleports;
    private final PlotOutlines outlines;
    private final PlotRegions regions;
    private final Supplier<PlotSettings.Show> show;
    private final Messages messages;

    public FarmCommand(FarmRegistry farms, FarmSetup setup, FarmTeleports teleports, PlotOutlines outlines,
                       PlotRegions regions, Supplier<PlotSettings.Show> show, Messages messages) {
        this.farms = farms;
        this.setup = setup;
        this.teleports = teleports;
        this.outlines = outlines;
        this.regions = regions;
        this.show = show;
        this.messages = messages;
    }

    static boolean canTeleport(CommandSourceStack src) {
        return src.getSender().hasPermission(TELEPORT_PERMISSION);
    }

    static boolean isAdmin(CommandSourceStack src) {
        return src.getSender().hasPermission(BakeryCommand.PERMISSION);
    }

    LiteralArgumentBuilder<CommandSourceStack> farmNode() {
        return Commands.literal("farm")
                .requires(src -> canTeleport(src) || isAdmin(src))
                .executes(ctx -> teleport(ctx, true))
                .then(admin("setup", ctx -> setup.setup(ctx.getSource().getSender(), id(ctx))))
                .then(admin("undo", ctx -> setup.undo(ctx.getSource().getSender(), id(ctx))))
                .then(admin("show", this::show));
    }

    LiteralArgumentBuilder<CommandSourceStack> homeNode() {
        return Commands.literal("home")
                .requires(FarmCommand::canTeleport)
                .executes(ctx -> teleport(ctx, false));
    }

    private LiteralArgumentBuilder<CommandSourceStack> admin(String name,
                                                            Consumer<CommandContext<CommandSourceStack>> run) {
        return Commands.literal(name)
                .requires(FarmCommand::isAdmin)
                .then(Commands.argument(ID, StringArgumentType.word())
                        .suggests((c, b) -> {
                            farms.all().forEach(f -> b.suggest(f.id()));
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            run.accept(ctx);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static String id(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, ID).toLowerCase(Locale.ROOT);
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, boolean toFarm) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            messages.send(ctx.getSource().getSender(), "commands.players-only");
            return 0;
        }
        if (!canTeleport(ctx.getSource())) {
            messages.send(player, "farm.no-permission");
            return 0;
        }
        if (toFarm) {
            teleports.toFarm(player);
        } else {
            teleports.home(player);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void show(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            messages.send(ctx.getSource().getSender(), "commands.players-only");
            return;
        }
        String id = id(ctx);
        Farm farm = farms.get(id).orElse(null);
        World world = farm == null ? null : PlotService.worldByKey(farm.worldKey()).orElse(null);
        if (farm == null || world == null || !world.equals(player.getWorld())) {
            messages.send(player, "farm.not-set-up", Placeholder.unparsed("id", id));
            return;
        }
        PlotSettings.Show s = show.get();
        Optional<RegionShape> region = regions.shape(world, farm.regionId());
        RegionShape outline = region.orElseGet(() -> RegionShape.cuboid(farm.regionId(), farm.box().minX(),
                farm.box().minY(), farm.box().minZ(), farm.box().maxX(), farm.box().maxY(), farm.box().maxZ()));
        outlines.showPoints(player, world, Outline.points(outline, s.particleStep(), s.maxPoints()),
                FarmMarks.marks(farm, s.particleStep(), s.maxPoints()));
        messages.send(player, "farm.show-started", Placeholder.unparsed("id", id),
                Placeholder.unparsed("amount", Integer.toString(s.durationSeconds())));
    }
}
