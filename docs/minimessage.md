# MiniMessage — colored & formatted messages

Reference for all agents. Applies to **both** 1.21.x and 26.x projects; differences are marked.
Every player-facing text in this project — chat, titles, action bars, boss bars, item names/lore, GUI titles, scoreboard — uses **Adventure Components built with MiniMessage**.

Last checked: 2026-10-18

---

## Rules

1. **No legacy colors.** Don't use `§`/`&` codes, `ChatColor`, or `String`-based `sendMessage`/`setDisplayName`. Use `Component`s.
2. **Messages live in config**, written as MiniMessage strings (e.g. `messages.yml`), so server owners can restyle them.
3. **One shared `MiniMessage` instance**, created once in a central place (e.g. a `Messages` / `Text` class), not `MiniMessage.miniMessage()` scattered everywhere.
4. **Never parse player input as MiniMessage.** Insert it with `Placeholder.unparsed(...)`. Otherwise players can inject colors, fake messages, hover text or click actions (see Security).
5. **Dynamic values go in placeholders**, never via string concatenation into the MiniMessage string.

---

## Setup

Paper ships Adventure and MiniMessage **natively**. Don't add `adventure-text-minimessage` to `pom.xml`, and never shade it.

| | 1.21.x | 26.x (26.2+) |
|---|---|---|
| Adventure version | 4.x | 5.x |
| Presets (`NON_INTERACTABLE`, `FORMATTED_TEXT`) | ❌ build a restricted instance with the builder instead | ✅ |
| `<shadow>` tag | MC 1.21.4+ | ✅ |
| `<sprite>` / `<head>` tags | MC 1.21.9+ (Adventure 4.25+) | ✅ |
| Deprecated Adventure 4 API | works (avoid it anyway) | **removed** — see the Adventure 5 section below |

Check the exact server version before using version-specific tags. Unsupported tags render as plain text or not at all.

---

## Basic usage

```java
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static Component parse(String input, TagResolver... resolvers) {
        return MM.deserialize(input, resolvers);
    }
}

player.sendMessage(Text.parse("<green>Welcome back, <player>!",
        Placeholder.component("player", player.displayName())));
```

Common tags: `<red>`, `<#ff8800>`, `<color:gold>`, `<bold>`/`<b>`, `<italic>`/`<i>`, `<underlined>`, `<!italic>` (turn off), `<reset>`, `<gradient:#5e4fa2:#f79459>`, `<rainbow>`, `<hover:show_text:'...'>`, `<click:run_command:/cmd>`, `<click:open_url:'https://…'>`, `<newline>`/`<br>`, `<lang:key>`, `<key:key.jump>`, `<font:ns:font>`.
Full list: https://docs.papermc.io/adventure/minimessage/format/

Tags don't have to be closed (outside strict mode). Invalid tags are shown as plain text instead of throwing — use `MiniMessage.builder().strict(true)` or `.debug(...)` when validating config messages.

---

## Placeholders & formatters

| Need | Use |
|---|---|
| Insert a Component (player display name, item name) | `Placeholder.component("name", component)` |
| Insert untrusted text (chat, sign text, item names typed by players) | `Placeholder.unparsed("name", text)` |
| Insert trusted MiniMessage from config | `Placeholder.parsed("name", mmString)` — **never** with player input |
| Reusable style tag | `Placeholder.styling("accent", NamedTextColor.GOLD, TextDecoration.BOLD)` |
| Numbers (with format/locale) | `Formatter.number("amount", 250.25)` → `<amount:'#.00'>` |
| Dates | `Formatter.date("date", LocalDateTime.now())` → `<date:'yyyy-MM-dd'>` |
| Plurals / choices | `Formatter.choice("count", n)` → `<count:'0#no items\|1#one item\|1<many items'>` |

Combine several resolvers with `TagResolver.resolver(a, b, c)` or pass them as varargs to `deserialize`.

```java
// messages.yml:  balance: "<gray>Balance: <amount:'#,##0.00'> coins"
player.sendMessage(Text.parse(config.getString("messages.balance"),
        Formatter.number("amount", balance)));
```

---

## Item names & lore — the italic gotcha

Minecraft renders custom item names and lore **italic by default**. Turn it off explicitly:

```java
Component name = Text.parse("<gold>Ruby Sword").decoration(TextDecoration.ITALIC, false);
// or in the MiniMessage string itself: "<!italic><gold>Ruby Sword"
```

Use the Component-based item APIs (`ItemMeta#displayName(Component)`, `#lore(List<Component>)`, or the data component API) — not the deprecated `String` setters.

---

## Security (player input)

- Player-supplied text **always** goes through `Placeholder.unparsed`, so tags in it show literally.
- If players may format their own text (e.g. chat colors for donors), parse it with a **restricted** instance, never the default one:
  - **26.2+:** `MiniMessage.miniMessage(MiniMessage.Preset.FORMATTED_TEXT)` (colors/decorations only) or `Preset.NON_INTERACTABLE` (everything except click/hover/insertion).
  - **1.21.x:** build it yourself, e.g. `MiniMessage.builder().tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations())).build()`.
  - Grant extra tags by permission if needed: build the resolver per player.
- `run_command` click events from player input are dangerous — never allow them.
- To show raw input safely in other contexts, `MM.escapeTags(text)` / `MM.stripTags(text)` exist.

---

## Adventure 5 changes (Paper 26.2+)

Things removed in Adventure 5 that old code or tutorials still use:
- `ClickEvent.create(Action, String)` and `ClickEvent#value()` → use the direct factories (`ClickEvent.openUrl(..)`, `runCommand(..)`, …) and `payload()`
- `Audience#sendMessage(Identity, …)` overloads and `MessageType` → plain `sendMessage(Component)` (or signed messages for real chat)
- `Component.join(...)` without a `JoinConfiguration` → `Component.join(JoinConfiguration, …)` or `Component.textOfChildren(...)`
- Non-builder `replaceText` / `replaceFirstText` → `replaceText(TextReplacementConfig)`
- `TranslationRegistry` → `TranslationStore`; `PlainComponentSerializer` → `PlainTextComponentSerializer`
- Boss bar `percent` → `progress`
- Custom `Component` implementations are no longer possible (sealed); use `VirtualComponent`
- `BookMeta` no longer extends Adventure's `Book`

On 1.21.x these still compile but are deprecated — avoid them so code ports cleanly to 26.x.

---

## Custom fonts, glyphs & ItemsAdder

- **Own resource pack font:** use `<font:namespace:font_name>` around the characters, e.g. `"<font:myplugin:icons></font> <gold>Shop"`. Keep the font key and characters in config.
- **ItemsAdder glyphs** (font images like `:shop_icon:`): those `:name:` shortcuts are ItemsAdder's own syntax, **not** MiniMessage. Parse the MiniMessage first, then let ItemsAdder replace its glyphs using `FontImageWrapper` (check its Javadocs for the method that accepts the type you have, Component or String, for your ItemsAdder version). Do this through the ItemsAdder hook from `docs/integrations.md`, so text still works when ItemsAdder is missing.
- **Negative-space GUI titles** (offset characters for custom backgrounds): treat the title as trusted config, and never mix player input into it.

---

## Migrating legacy `&` messages

If existing configs use `&a`-style codes, convert once instead of mixing formats:

```java
Component legacy = LegacyComponentSerializer.legacyAmpersand().deserialize(oldString);
String asMiniMessage = MM.serialize(legacy); // write this back to the config
```

---

## Tools & docs

- Web viewer (test messages without a server): https://webui.advntr.dev
- Format (all tags): https://docs.papermc.io/adventure/minimessage/format/
- API (builder, presets, custom tags): https://docs.papermc.io/adventure/minimessage/api/
- Placeholders & formatters: https://docs.papermc.io/adventure/minimessage/dynamic-replacements/
- Translations / i18n: https://docs.papermc.io/adventure/minimessage/translator/
- Adventure 4 → 5 migration: https://docs.papermc.io/adventure/migration/adventure-4.x/
- Adventure Javadocs: https://jd.papermc.io/adventure
