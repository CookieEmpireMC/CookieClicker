package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.BasketSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.text.TextPadding;
import dev.casperrs.cookieClicker.text.TextWidth;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import dev.casperrs.cookieClicker.view.panel.PanelLayout;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Builds the entities of one owner's view at one station, and the item stacks they show. Main thread. */
final class StationSpawner {

    private static final Key COOKIE_MODEL = Key.key("cookieclicker", "cookie");
    private static final Key BASKET_MODEL = Key.key("cookieclicker", "cookie_basket");

    private final Plugin plugin;
    private final Supplier<StationVisualSettings> settings;
    private final Economy economy;
    private final Messages messages;
    private final Predicate<Player> hasPack;
    private final NamespacedKey entityKey;
    private final WarnOnce warnings;

    StationSpawner(Plugin plugin, Supplier<StationVisualSettings> settings, Economy economy, Messages messages,
                   Predicate<Player> hasPack, NamespacedKey entityKey, WarnOnce warnings) {
        this.plugin = plugin;
        this.settings = settings;
        this.economy = economy;
        this.messages = messages;
        this.hasPack = hasPack;
        this.entityKey = entityKey;
        this.warnings = warnings;
    }

    /** The whole view (not registered or filled yet); null when spawning failed (nothing is left behind). */
    @Nullable StationView spawn(Player owner, Station station, StationVisualSettings s, CookiePouch pouch,
                                CookieMotion motion) {
        boolean pack = hasPack.test(owner);
        List<Entity> spawned = new ArrayList<>();
        try {
            StationFrame frame = StationFrame.of(station);
            Offset pedOffset = StationAnchors.pedestalOffset(station, s);
            BasketLook look = pack && s.pedestal().enabled() ? BasketLook.pick(pouch.pouch(), null, s.basket()) : null;
            double[] centre = frame.point(cookieOffset(station, s, look, pedOffset));
            long spawnTick = Bukkit.getCurrentTick();
            ItemDisplay cookie = spawnCookie(owner, frame, centre, s, pack, look != null, motion, spawnTick, spawned);
            Interaction cookieBox = spawnCookieBox(owner, centre, s.cookie(), spawned);
            double[] pedestalBottom = frame.point(pedOffset);
            Display pedestal = look != null
                    ? spawnBasket(owner, pedestalBottom, frame.facingViewers(0), look, spawned)
                    : spawnPedestal(owner, frame, pedOffset, s.pedestal(), spawned);
            int buildingRows = Math.min(s.buildings().maxRows(), economy.config().buildings().size());
            int clickRows = Math.min(s.clicks().maxRows(), economy.config().upgrades().size());
            StationView.Panel buildings = spawnPanel(PanelKind.BUILDINGS, owner, station, s, buildingRows, spawned);
            StationView.Panel clicks = spawnPanel(PanelKind.CLICKS, owner, station, s, clickRows, spawned);

            StationView view = new StationView(owner.getUniqueId(), station, cookie, cookieBox, centre, pedestal,
                    buildings, clicks, pack);
            view.motionTarget = spawnTick;
            view.pedestalBottom = pedestalBottom;
            view.pedestalUp = pedOffset.up();
            view.basketLook = look;
            return view;
        } catch (RuntimeException e) {
            spawned.forEach(Entity::remove);
            warnings.warn("spawn", "Could not spawn the bakery view at " + station.id() + ": " + e);
            return null;
        }
    }

    /** With a basket the cookie floats above the basket's current look. */
    private static Offset cookieOffset(Station station, StationVisualSettings s, @Nullable BasketLook look,
                                       Offset pedOffset) {
        Offset co = StationAnchors.cookieOffset(station, s);
        if (look == null) {
            return co;
        }
        StationVisualSettings.Cookie c = s.cookie();
        return new Offset(co.right(), look.cookieCentre(s.basket(), pedOffset.up(), c.scale(), c.bobAmplitude()),
                co.forward());
    }

    private ItemDisplay spawnCookie(Player owner, StationFrame frame, double[] centre, StationVisualSettings s,
                                    boolean pack, boolean onBasket, CookieMotion motion, long spawnTick,
                                    List<Entity> spawned) {
        StationVisualSettings.Cookie c = s.cookie();
        BasketSettings b = s.basket();
        float scale = c.scale();
        return spawn(owner, centre, frame.facingViewers(0), ItemDisplay.class, spawned, d -> {
            d.setItemStack(cookieStack(pack));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(c.viewRange());
            d.setBrightness(new Display.Brightness(c.brightness(), c.brightness()));
            if (onBasket) {
                d.setTeleportDuration(b.moveTicks()); // the cookie glides when the basket grows
            }
            d.setTransformation(new Transformation(new Vector3f(0, (float) motion.bob(spawnTick), 0),
                    new Quaternionf().rotationY((float) motion.angle(spawnTick)),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    private Interaction spawnCookieBox(Player owner, double[] centre, StationVisualSettings.Cookie c,
                                       List<Entity> spawned) {
        float boxWidth = (float) (c.scale() + c.hitboxMargin());
        float boxHeight = (float) (c.scale() + 2 * c.bobAmplitude() + c.hitboxMargin());
        double[] boxAt = {centre[0], centre[1] - boxHeight / 2, centre[2]};
        return spawnBox(owner, boxAt, boxWidth, boxHeight, spawned);
    }

    Interaction spawnBox(Player owner, double[] at, float width, float height, List<Entity> spawned) {
        return spawn(owner, at, 0, Interaction.class, spawned, i -> {
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
        });
    }

    /** One panel laid out by {@link PanelLayout}: header, ×N toggle, row slots (icon, text) and one click box. */
    private StationView.Panel spawnPanel(PanelKind kind, Player owner, Station station, StationVisualSettings s,
                                         int rowCount, List<Entity> spawned) {
        StationVisualSettings.Panel p = s.panel();
        PanelLayout layout = PanelLayout.of(s, rowCount);
        StationFrame pf = StationAnchors.panelFrame(station, kind);
        Offset po = StationAnchors.panelOffset(station, kind, s);
        float textYaw = pf.facingViewers(p.textYawOffset());
        float iconYaw = pf.facingViewers(p.iconYawOffset());
        TextDisplay header = spawn(owner, pf.point(po), textYaw, TextDisplay.class, spawned, t -> {
            styleSingleLine(t, p, p.headerScale(), p.color("header"));
            t.text(TextPadding.centred(messages.component(kind == PanelKind.BUILDINGS ? "panel.buildings.header"
                    : "panel.clicks.header"), p.textPadding(), layout.headerTargetPx()));
        });
        TextDisplay toggle = spawn(owner, pf.point(po, layout.toggleAt()), textYaw, TextDisplay.class, spawned,
                t -> styleSingleLine(t, p, s.bulk().scale(), p.color("toggle")));
        Interaction panelBox = spawnBox(owner, pf.point(po, layout.boxBottom()), (float) layout.boxWidth(),
                (float) layout.boxHeight(), spawned);
        List<StationView.Row> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            rows.add(spawnRow(owner, index, pf, po, layout, p, textYaw, iconYaw, spawned));
        }
        return new StationView.Panel(kind, pf, po, iconYaw, header, toggle, panelBox, rows);
    }

    private StationView.Row spawnRow(Player owner, int index, StationFrame pf, Offset po, PanelLayout layout,
                                     StationVisualSettings.Panel p, float textYaw, float iconYaw,
                                     List<Entity> spawned) {
        double[] iconAt = pf.point(po, layout.iconAt(index));
        float is = p.iconScale();
        ItemDisplay icon = spawn(owner, iconAt, iconYaw, ItemDisplay.class, spawned, d -> {
            d.setItemStack(ItemStack.empty());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(p.viewRange());
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(is, is, is), new Quaternionf()));
        });
        TextDisplay text = spawn(owner, pf.point(po, layout.textAt(index)), textYaw, TextDisplay.class, spawned,
                t -> {
                    styleText(t, p, p.textScale(), 0);
                    t.setAlignment(TextDisplay.TextAlignment.LEFT);
                });
        double[] rowCentre = pf.point(po, new Offset(0, layout.rowBottom(index) + layout.rowHeight() / 2,
                p.textOffset().forward()));
        return new StationView.Row(index, icon, text, rowCentre, iconAt);
    }

    private @Nullable BlockDisplay spawnPedestal(Player owner, StationFrame frame, Offset offset,
                                                 StationVisualSettings.Pedestal ped, List<Entity> spawned) {
        if (!ped.enabled()) {
            return null;
        }
        BlockData block;
        try {
            block = Bukkit.createBlockData(ped.block());
        } catch (IllegalArgumentException e) {
            warnings.warn("pedestal", "station.pedestal.block '" + ped.block() + "' is not a block; no pedestal");
            return null;
        }
        float w = (float) ped.width();
        float h = (float) ped.height();
        // block displays grow from their corner: shift by half the width so the offset is the bottom centre
        return spawn(owner, frame.point(offset), 0, BlockDisplay.class, spawned, d -> {
            d.setBlock(block);
            d.setTransformation(new Transformation(new Vector3f(-w / 2, 0, -w / 2), new Quaternionf(),
                    new Vector3f(w, h, w), new Quaternionf()));
        });
    }

    /** The model's origin is its bottom centre and all sides look the same. */
    private ItemDisplay spawnBasket(Player owner, double[] at, float yaw, BasketLook look, List<Entity> spawned) {
        float viewRange = settings.get().cookie().viewRange();
        return spawn(owner, at, yaw, ItemDisplay.class, spawned, d -> {
            d.setItemStack(basketStack(look));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(viewRange);
        });
    }

    private static void styleSingleLine(TextDisplay t, StationVisualSettings.Panel p, float scale, int argb) {
        styleText(t, p, scale, argb);
        t.setLineWidth(TextWidth.SINGLE_LINE_WIDTH);
        t.setAlignment(TextDisplay.TextAlignment.CENTER);
    }

    private static void styleText(TextDisplay t, StationVisualSettings.Panel p, float scale, int argb) {
        t.setBillboard(Display.Billboard.FIXED);
        t.setLineWidth(p.lineWidth());
        t.setSeeThrough(p.textSeeThrough());
        t.setShadowed(p.textShadowed());
        t.setDefaultBackground(p.textDefaultBackground());
        t.setBackgroundColor(Color.fromARGB(argb));
        t.setBrightness(new Display.Brightness(p.textBrightness(), p.textBrightness()));
        t.setViewRange(p.viewRange());
        t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    /** Spawned hidden inside the consumer (never visible to anyone else, not even for a tick), then shown to the owner. */
    <T extends Entity> T spawn(Player owner, double[] p, float yaw, Class<T> type, List<Entity> spawned,
                               Consumer<? super T> setup) {
        World world = owner.getWorld();
        T entity = world.spawn(new Location(world, p[0], p[1], p[2], yaw, 0), type, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.addScoreboardTag(StationViews.TAG);
            e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
            setup.accept(e);
        });
        spawned.add(entity);
        owner.showEntity(plugin, entity);
        return entity;
    }

    private static ItemStack cookieStack(boolean withPack) {
        ItemStack stack = ItemStack.of(Material.COOKIE);
        if (withPack) {
            stack.setData(DataComponentTypes.ITEM_MODEL, COOKIE_MODEL);
        }
        return stack;
    }

    /** {@code cookieclicker:cookie_basket} with {@code custom_model_data} strings[0] = {@code <tier>_<fill>}. */
    static ItemStack basketStack(BasketLook look) {
        ItemStack stack = ItemStack.of(Material.COOKIE);
        stack.setData(DataComponentTypes.ITEM_MODEL, BASKET_MODEL);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(look.id()).build());
        return stack;
    }

    /** Row / shelf icon: {@code cookieclicker:building/<id>} or {@code cookieclicker:upgrade/<id>} on the fallback. */
    ItemStack iconStack(PanelKind kind, String id, boolean withPack) {
        StationVisualSettings s = settings.get();
        boolean building = kind == PanelKind.BUILDINGS;
        String name = building ? s.panel().fallbackFor(id) : s.upgradeFallbackFor(id);
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            warnings.warn("icon:" + kind + ":" + id, "Fallback icon '" + name + "' for " + id + " is not an item; using COOKIE");
            material = Material.COOKIE;
        }
        ItemStack stack = ItemStack.of(material);
        if (withPack) {
            stack.setData(DataComponentTypes.ITEM_MODEL,
                    Key.key("cookieclicker", (building ? "building/" : "upgrade/") + id));
        }
        return stack;
    }
}
