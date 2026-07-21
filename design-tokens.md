# ABX-MCP Design Token Guideline (XML)

**Read this before writing or editing any XML layout, style, drawable, or
resource file.** This is the XML-world equivalent of AGENTS.md section 4
(Design Token Discipline), which governs the Compose side of the app during
this migration. The rules are the same; only the syntax differs.

---

## 1. Purpose — what this system is actually for

ABX-MCP is a hardware-backed, session-gated bridge between AI/MCP clients
and the device filesystem. The UI direction is deliberately **calm,
minimalist, low-cognitive-load** — modeled on Heroku's web console, but
that's really just one well-known example of a much older, more general
convention: the same grouped-list, restrained-color language used by
iOS's Settings app and stock Android Settings. This is not a stylistic
preference — it's the standard pattern for information-dense utility UIs
on both platforms, which means following it makes this app feel *more*
native, not less.

The core idea in one sentence: **whitespace, color, and type weight are
each drawn from a small, fixed vocabulary — never invented ad hoc per
screen.** Individually-reasonable one-off choices are exactly how this
drifts. A prior audit of the Compose UI found 18 distinct ad-hoc spacing
values and multiple hardcoded hex colors accumulated across a single
file, none of which were individually unreasonable — they just never had
a shared scale to draw from. This guideline exists so the XML port
doesn't repeat that.

**This branding must be applied app-wide, consistently, on every screen
— not just the ones explicitly called out below.** If you build a new
screen and it isn't obvious how to make it match, stop and ask rather
than inventing a new pattern.

---

## 2. The four rules, XML version

### Rule 1 — Spacing and sizing come from `dimens.xml`, never a raw value
Every `android:padding*`, `android:margin*`, `android:layout_width`, and
`android:layout_height` that isn't `match_parent`/`wrap_content` must
reference `@dimen/spacing_*` or `@dimen/icon_size_*`. Never write
`16dp` directly in a layout. If the value you need isn't in the scale
(`spacing_xs/sm/md/lg/xl/xxl` = 4/8/12/16/24/32dp, `icon_size_sm/md/lg/xl/xxl`
= 20/28/40/48/64dp), stop and ask before inventing one.

### Rule 2 — Color comes from semantic roles, never a hardcoded hex
Never write `android:textColor="#FF..."` or a raw color literal anywhere
in a layout, style, or drawable. Reference `@color/color_*` semantic
roles (`color_primary`, `color_on_surface_variant`, `color_error`, etc.)
defined in `values/colors.xml` / overridden in `values-night/colors.xml`.
This is what makes dark mode work automatically and is what keeps the
palette from fragmenting into unrelated one-off colors per screen.

### Rule 3 — Blue is reserved for active/selected/primary-action, never decorative
The accent color (`color_primary` / `color_primary_container`) may only
be applied to: the one primary action on a screen, an actively
selected/active state (a live session dot, a selected tab), or a
genuinely primary call-to-action button. It must NOT be the default tint
for icon containers, cards, or chips just because they exist. Default to
`color_surface_variant` / `color_on_surface_variant` (neutral gray) for
everything else. If in doubt, ask before adding a new use of blue —
this was violated once already in the Compose version (`ABXMetricCard`'s
icon container was unconditionally blue) and had to be fixed.

### Rule 4 — Type weight: `SemiBold`/`Medium` are the ceiling, `Bold`/`Black` are the rare exception
Use the `TextAppearance.Abx.*` styles from `themes.xml`, which cap weight
at SemiBold (600) for titles and Medium (500) for emphasized body text.
Do not apply `android:textStyle="bold"` or a black/heavy font weight ad
hoc in a layout — reserve true bold for exactly one hierarchy level
app-wide, decided once, not per screen. A prior Compose audit found
`FontWeight.Bold` used 24 times and `FontWeight.Black` twice across
~900 lines; that's the failure mode this rule prevents.

---

## 3. List rows vs. cards — read this before building any list screen

This was gotten wrong once already and is worth being explicit about.

**Lists of similar items (tools, connected clients, access entries,
activity log lines) use `Widget.Abx.ListRow` — a flat row with a hairline
`Widget.Abx.ListRow.Divider` between items — never a card per row.**
Rows in a list sit flush against each other; the divider is the entire
separation mechanism. Do not add a `CardView`, a rounded background, a
border, or a drop shadow around an individual row. An earlier version of
this component wrapped every row in its own bordered/rounded box, which
did not match the approved reference design and had to be corrected.

**Reserve `Widget.Abx.Card` for content that is genuinely one distinct,
self-contained unit** — a single metric, a dialog, a standalone summary
block. If you're building a `RecyclerView`/list of more than one similar
item, it's a list-row case, not a card case.

Example — correct list row markup:

```xml
<LinearLayout
    style="@style/Widget.Abx.ListRow"
    android:orientation="horizontal">

    <FrameLayout style="@style/Widget.Abx.ListRow.IconContainer">
        <ImageView
            style="@style/Widget.Abx.ListRow.Icon"
            android:src="@drawable/ic_file_search" />
    </FrameLayout>

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="@dimen/spacing_md"
        android:orientation="vertical">
        <TextView
            style="@style/Widget.Abx.ListRow.Title"
            android:text="Context inspector" />
        <TextView
            style="@style/Widget.Abx.ListRow.Subtitle"
            android:text="Preview files before sending" />
    </LinearLayout>

    <ImageView style="@style/Widget.Abx.ListRow.Chevron" />
</LinearLayout>

<View style="@style/Widget.Abx.ListRow.Divider" />
```

Omit the trailing `Widget.Abx.ListRow.Divider` on the last row in a
group so a section doesn't end on a stray hairline right before the
next section header's own spacing — same rule as the Compose version.

---

## 4. Chevron / trailing icon color

Trailing chevrons (`Widget.Abx.ListRow.Chevron`) must be tinted
`color_on_surface_variant`. **Do not tint them with `color_outline`.**
`outline` is deliberately low-contrast — it exists for hairline borders
and dividers, not for content that needs to read as legible foreground.
This exact mistake (a chevron tinted with the border token instead of
the text token, making it read as noticeably too light) was found and
fixed once already in the Compose version. Don't reintroduce it here.

---

## 5. Top bar

Use `Widget.Abx.TopBar` — fixed `top_bar_height` (48dp), **not** the
platform default `Toolbar` height (56–64dp). This compactness is
deliberate, matching the same restraint as the rest of the system. Do
not apply a stock `Widget.Material3.Toolbar` without overriding its
height, since that silently reverts to the oversized default.

---

## 6. Status indication

Use `Widget.Abx.StatusChip` combined with `.Success` / `.Warning` /
`.Error` / `.Info` — never a hardcoded background/text color combination
for a status chip. These map to `color_success_container`/`color_success`,
etc., which are dark-mode-aware. This mirrors `abxStatusColors` on the
Compose side; if you need a new status category, add it to both
`colors.xml` (this file) and `Theme.kt` (Compose) in the same change,
not just one.

---

## 7. Keeping Compose and XML in sync during the migration

Until the Compose UI is fully retired, **this app has two parallel
theme systems that must represent the same design**, not two designs
that happen to look similar. If you change a color, spacing value, or
component rule on one side, make the equivalent change on the other side
in the same task:

| Compose | XML |
|---|---|
| `ui/theme/Color.kt` | `values/colors.xml`, `values-night/colors.xml` |
| `ui/theme/Theme.kt` | `values/themes.xml` |
| `ui/theme/Spacing.kt` | `values/dimens.xml` |
| `ui/Components.kt` (`ABXListRow`, `ABXCard`, `ABXStatusChip`, `CompactTopBar`) | `values/styles_components.xml`, `drawable/bg_icon_container_*.xml` |

If a screen has already been ported to XML, do not carry over a
Compose-only pattern that contradicts this guideline (e.g. a card-per-row
list) just because that's how an earlier Compose version did it before
it was corrected — this document reflects the corrected, approved state.

---

## 8. Before you write any new layout

1. Does a `Widget.Abx.*` style already cover this? Use it.
2. Does the spacing/size value you need exist in `dimens.xml`? Use it.
   If not, stop and ask.
3. Is every color reference `@color/color_*`? No raw hex, anywhere.
4. Is blue used only where something is genuinely active/primary?
5. Is bold/heavy weight used in at most one place on this screen?
6. If this is a list, is it built from `Widget.Abx.ListRow` + divider,
   not a card per item?

If you're unsure on any of these, ask before proceeding — this issue is
considered important enough that a wrong guess is worse than a paused
task waiting for confirmation.
