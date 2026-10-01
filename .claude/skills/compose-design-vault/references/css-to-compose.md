# CSS → Compose translation cookbook

How any vault design's mechanics become native Compose. Each entry: the CSS concept, the
Compose equivalent, a working Kotlin sketch, and traps. Verify volatile APIs against live
androidx sources (the Compose expert skills' standing rule) before shipping.

**Reading a vault item:** open `markup.html` for structure, `style.css` for the mechanic.
Identify WHAT moves/layers/glows (the mechanic), then translate the mechanic into the
app's own invented direction — never the item's palette wholesale.

---

## 1. Gradients

| CSS | Compose |
|-----|---------|
| `linear-gradient(...)` | `Brush.linearGradient(colors, start, end)` |
| `radial-gradient(...)` | `Brush.radialGradient(...)` |
| `conic-gradient(...)` | `Brush.sweepGradient(...)` |
| animated mesh/aurora | `MeshGradientPainter` (Compose 1.12+) or AGSL shader |

```kotlin
Box(
    Modifier.background(
        Brush.linearGradient(listOf(colorA, colorB, colorC))
    )
)
// Animated gradient: animate the colors or offsets with rememberInfiniteTransition
val t by rememberInfiniteTransition(label = "grad").animateFloat(
    0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
    label = "t"
)
val brush = Brush.linearGradient(
    colors = listOf(lerp(colorA, colorB, t), lerp(colorB, colorC, t))
)
```
Trap: rebuilding a `Brush` every frame allocates — fine for hero surfaces, not for lists.

## 2. Shadows and glow (`box-shadow`)

- Plain elevation shadow → `Modifier.shadow(elevation, shape)`.
- **Colored glow / neon** (multiple colored box-shadows) → draw it yourself:

```kotlin
fun Modifier.glow(color: Color, radius: Dp, shape: Shape = CircleShape) = drawBehind {
    val paint = Paint().apply {
        asFrameworkPaint().setShadowLayer(radius.toPx(), 0f, 0f, color.toArgb())
    }
    drawIntoCanvas { canvas ->
        val outline = shape.createOutline(size, layoutDirection, this)
        canvas.drawOutline(outline, paint)
    }
}
```
  (`setShadowLayer` on non-text drawing is hardware-accelerated only from **API 28** —
  below that it needs a software layer; fine on the modern device base.)
- Stacked neon (CSS `box-shadow: 0 0 5px c, 0 0 20px c, 0 0 60px c`) → call the shadow
  layer 2–3 times with growing radii, or one blurred layer via
  `Modifier.graphicsLayer { renderEffect = BlurEffect(r, r) }` on a duplicate glow layer
  (RenderEffect needs API 31+). Compose 1.12's `LayerOutsets` prevents the glow being
  clipped by the layer bounds.
- **Neumorphism** (dual light/dark shadows) → two `drawBehind` shadow layers, light
  top-left + dark bottom-right; or a per-project lib (`~/.claude/repos/`
  `CuriousNikhil-neumorphic-compose`).

## 3. Glass (`backdrop-filter: blur`)

House rule: route through the `kmp-liquid-glass` skill; pick ONE lib per project (Cloudy /
Kyant0 AndroidLiquidGlass / KMPLiquidGlass backdrop / Haze). Fallback chain: AGSL
RuntimeShader (API 33+) → RenderEffect blur (API 31+) → gradient + transparency simulation.
Never hand-roll refraction when a maintained lib fits.

## 4. Transforms (`transform: ...`)

```kotlin
Modifier.graphicsLayer {
    translationX = x; translationY = y
    rotationZ = angle            // CSS rotate()
    rotationX = tiltX            // CSS rotateX() — needs cameraDistance for depth feel
    scaleX = s; scaleY = s
    cameraDistance = 12f * density   // CSS perspective()
    transformOrigin = TransformOrigin(0.5f, 1f)  // CSS transform-origin
}
```
- CSS `skew()` has no graphicsLayer field, and `DrawTransform` exposes no skew member —
  its transform surface is `translate`/`rotate`/`scale`/`inset`/`clip*` plus the general
  `transform(matrix: Matrix)`. Two lawful routes: bake the skew into the geometry (a
  `GenericShape` whose points are pre-sheared — no API risk), or apply a shear matrix in a
  draw scope via `withTransform({ transform(Matrix().apply { this[1, 0] = tan(ax) }) })`
  — equivalently `values[Matrix.SkewX] = tan(ax)`: SkewX is flattened index 4, which the
  `set(row, column)` operator addresses as row 1 / column 0 (`values[(row * 4) + column]`,
  per the androidx Matrix source). CSS `skew(ax)`/`skewX` is the horizontal shear
  x' = x + y·tan(ax). (`this[0, 1]` is SkewY — the wrong axis.)
- 3D card-tilt-on-touch (vault favorite): map pointer position to `rotationX/rotationY`
  via `pointerInput` + `detectDragGestures`, spring back with `Animatable`.

## 5. Keyframes (`@keyframes` + `animation:`)

| CSS pattern | Compose |
|-------------|---------|
| infinite loop | `rememberInfiniteTransition().animateFloat/Color(...)` |
| one-shot on state | `animate*AsState(target, spring()/tween())` |
| multi-step % keyframes | `keyframes { durationMillis = N; v1 at 0; v2 at 300; ... }` |
| orchestrated multi-property | `updateTransition(state)` with one `animate*` per property |
| scriptable/interruptible | `Animatable` + `animateTo` in a coroutine |

```kotlin
// CSS: animation: bounce 0.6s cubic-bezier(...) infinite;
val y by rememberInfiniteTransition(label = "b").animateFloat(
    0f, -12f,
    infiniteRepeatable(
        keyframes {
            durationMillis = 600
            0f at 0; -12f at 260 using FastOutSlowInEasing; 0f at 600
        }
    ),
    label = "y"
)
```
2026 default: prefer `spring(dampingRatio, stiffness)` over tween — M3 Expressive's motion
system is physics-first (see trends digest, Group 3).

## 6. Pseudo-elements (`::before` / `::after`)

CSS layers decorations under/over content; Compose composes layers explicitly:
- Under → `Modifier.drawBehind { }` or a first child in a `Box`.
- Over → `Modifier.drawWithContent { drawContent(); ... }` or a last child in a `Box`.
- The vault's "animated border ::before" trick → see recipe 10 (moving neon border).

## 7. Hover, focus, press states

- Android touch: press replaces hover —
  `val pressed by interactionSource.collectIsPressedAsState()`, then animate from it.
- Focus (TV/keyboard): `Modifier.onFocusChanged { }` + focus-driven animation (the
  10-foot doctrine's focus-highlight rule).
- Real hover exists on desktop CMP and Android trackpads (1.11 added first-class trackpad
  events): `Modifier.hoverable(interactionSource)` +
  `collectIsHoveredAsState()`.

## 8. Text effects

- Gradient text (CSS `background-clip: text`) →
  `Text(style = TextStyle(brush = Brush.linearGradient(...)))`.
- Per-span styling → `buildAnnotatedString { withStyle(SpanStyle(...)) { append(...) } }`;
  editable rich text → Compose 1.12 `BasicTextField` `addStyle()` (trends digest, Group 1).
- Variable-font weight animation (CSS `font-variation-settings`) →
  `FontVariation.Settings(FontVariation.weight(w))` on the `Font`; 1.12 extends this to
  downloadable fonts. Animate `w` with `animateIntAsState` for the "breathing weight" look.

## 9. Clip paths and blob shapes

- `clip-path: polygon(...)` → `Modifier.clip(GenericShape { size, _ -> moveTo(...); lineTo(...) })`.
- Organic blobs → cubic Béziers in a `GenericShape`, morphing by animating control points;
  M3 Expressive shape-morphing (trends, Group 3) covers the sanctioned morph transitions;
  `graphicsLayer { clip = true; shape = ... }` when the clip must animate cheaply.

## 10. Signature vault mechanics, pre-translated

**Moving neon border** (the owner's named favorite — "thin animated gradient border,
slowly rotating color / travelling light streak around SOLID panels, no glass"). The
border GEOMETRY stays fixed on the panel; only the sweep shader's local matrix rotates,
so the light streak travels around a stationary rounded-rect stroke. The streak color is
a required parameter — it comes from the app's own `ART_DIRECTION.md` palette (a
`MaterialTheme.colorScheme` read at the call site is fine); a baked-in hex here would
seed the same identity into every app.
```kotlin
@Composable
fun Modifier.neonBorder(
    streakColors: List<Color>,   // REQUIRED — the app's own accent(s), never a default hex
    width: Dp = 2.dp,
    corner: Dp = 16.dp,
    cycleMillis: Int = 6000,
): Modifier {
    val angle by rememberInfiniteTransition(label = "nb").animateFloat(
        0f, 360f, infiniteRepeatable(tween(cycleMillis, easing = LinearEasing)), label = "a"
    )
    return drawBehind {
        // fixed stops: transparent most of the way round, then the streak
        val n = streakColors.size
        val colors = IntArray(n + 3)
        val stops = FloatArray(n + 3)
        colors[0] = android.graphics.Color.TRANSPARENT; stops[0] = 0f
        colors[1] = android.graphics.Color.TRANSPARENT; stops[1] = 0.70f
        streakColors.forEachIndexed { i, c ->
            colors[i + 2] = c.toArgb()
            stops[i + 2] = 0.72f + 0.24f * (i + 1) / (n + 1)
        }
        colors[n + 2] = android.graphics.Color.TRANSPARENT; stops[n + 2] = 1f
        val shader = android.graphics.SweepGradient(
            size.width / 2f, size.height / 2f, colors, stops
        ).apply {
            // rotate ONLY the shader — the drawn rect never moves
            setLocalMatrix(android.graphics.Matrix().apply {
                postRotate(angle, size.width / 2f, size.height / 2f)
            })
        }
        drawRoundRect(
            brush = ShaderBrush(shader),
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(width.toPx())
        )
    }
}
// call site: Modifier.neonBorder(listOf(MaterialTheme.colorScheme.primary))
```
(What this draws: one stationary rounded-rect stroke filling the panel bounds; the sweep
gradient's local matrix carries the rotation, so only the light position changes frame to
frame. Tune the 0.70–1.0 stop window for streak length. For a perimeter-hugging comet on
very elongated panels, the alternative is animating a `PathMeasure` segment along the
rounded-rect path — dashed-stroke chase — instead of a sweep shader.)

**Gooey/metaball toggles** (SVG `filter: blur + contrast`): AGSL RuntimeShader thresholding
a blurred alpha (API 33+), or fake it with two overlapping circles + a `blur` +
`BlendMode`; below API 33 ship the simple morph, not a broken filter.

**Skeleton shimmer**: already canonical — `rememberInfiniteTransition` translating a
`linearGradient` brush (see android-skills:compose `references/animation-recipes.md`).

**3D flip card** (`transform: rotateY(180deg)` + `backface-visibility`):
```kotlin
val rot by animateFloatAsState(if (flipped) 180f else 0f, spring(stiffness = 300f), label = "flip")
Box(Modifier.graphicsLayer { rotationY = rot; cameraDistance = 12f * density }) {
    if (rot <= 90f) Front() else Back(Modifier.graphicsLayer { rotationY = 180f })
}
```

**Animated gradient button sheen** (CSS `background-position` sweep): overlay a narrow
white-alpha `linearGradient` band and animate its `translationX` across the button on an
infinite transition; pause between sweeps with `delayMillis` inside `infiniteRepeatable`.

---

## Performance guardrails for every translation

Defer reads: animate inside `graphicsLayer { }` / `drawBehind { }` lambdas so per-frame
values never recompose the tree (performance canon, trends Group 4). Never use
`Modifier.composed { }` for these — build stateless draw modifiers or use
`Modifier.Node`. Infinite animations on list items: gate on visibility
(`Modifier.onVisibilityChanged`, 1.12) so off-screen items don't burn frames.
