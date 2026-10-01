# AndroidGo — Master Plan (all Android skills, one place)

> One consolidated playbook that merges the **AndroidGo orchestration** (`SKILL.md`) with the distilled
> essentials of every installed Android skill, so a single read gives the whole picture. `SKILL.md` stays the
> trigger + doctrine; this file is the deep how-to. When a section needs more depth, open the named skill.
> Audience build target: **phone-first**, with a **Google TV (API 34)** path available (see §12).

---

## 0. How to use this
1. Confirm the goal in one line. Pick a **distinct art direction** (NO SAMENESS — §8).
2. Walk the pipeline (§1). Pull the matching cheat-sheet (§2–§8) as you go.
3. Add the default libraries (§9). Build, self-review, **deliver the APK** (§11). Emulator is opt-in only.
4. Router table (§13) says which skill/agent/MCP to consult for each decision.

## 1. Pipeline (condensed from SKILL.md)
**Design → Build → Deliver `.APK` → user tests on their own device → reports → rectify.**
- **A Foundation** — IA, navigation graph, adaptive plan, persisted theme, screen state contracts. (`android-ux-architect`)
- **B Visual system** — ColorScheme + Typography + tokens + signature motion/3D concept. (`android-ui-designer`, `android-material3-design`, `ui-ux-pro-max --stack jetpack-compose`)
- **C Build** — real Kotlin/Compose, wired state/nav, 3D/motion, library stack. (`engineering-mobile-app-builder`, `android-compose-expert`, `android-architecture`)
- **D Self-review (no emulator)** — correctness pass + `security-senior-secops` if secrets; build **debug APK**, hand over path + "enable unknown sources, tap to install" + a short test checklist. Optionally run `/security-audit`, `/performance-audit`, `/ui-audit` on the codebase (§10).
- **E Opt-in test** — only on "run → test apps": boot emulator, drive via `mobile-mcp`.
- **F Release** — `marketing-app-store-optimizer` for the Play listing.

## 2. Architecture cheat-sheet  → skill: `android-architecture`, plugin `android-skills`
- **Pattern:** MVVM + **UDF** (unidirectional data flow). UI emits events ↑, state flows ↓.
- **Layers:** `ui` (Compose + ViewModel) → `domain` (optional use-cases) → `data` (repository → Room + network).
- **State:** one immutable `data class XxxUiState` per screen, exposed as `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), Initial)`. Collect with `collectAsStateWithLifecycle()`.
- **DI:** **Hilt** (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Module @InstallIn`). Koin only if asked.
- **Offline-first:** Room is the single source of truth; network writes into Room; UI observes Room `Flow`. Expose `Flow<List<Entity>>` from DAOs.
- **Async:** Coroutines + Flow. `viewModelScope`; `Dispatchers.IO` for disk/net; never block main. Use `Turbine` to test flows.
- **Modularize when it grows:** `:core:*` (designsystem, data, model, network) + `:feature:*`. Mirror **nowinandroid** (cloned at `~/.claude/repos/nowinandroid`).

## 3. Material 3 / theming cheat-sheet  → skill: `android-material3-design`
- `MaterialTheme(colorScheme, typography, shapes)` wraps everything. Build schemes with **Material Theme Builder** export.
- **Dynamic color** (Android 12+): `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`; fall back to brand scheme. Offer a toggle.
- **Roles, not hex:** use `primary / secondary / tertiary / surface / surfaceContainer*` + `onX`. Never hardcode colors in components.
- **Type scale:** display/headline/title/body/label. Pair fonts via `ui-ux-pro-max`; load with Google Fonts in Compose.
- **M3 Expressive:** motion-physics, larger shapes, bolder color — lean in for "god-tier" unless the brief is utilitarian.
- Components: prefer M3 (`Button`, `Card`, `NavigationBar/Rail`, `SearchBar`, `Carousel`, `BottomSheetScaffold`). Reskin via tokens, don't fork.

## 4. Compose mastery cheat-sheet  → skill: `android-compose-expert`, plugin skill `compose`
- **State:** hoist state; `remember {}` for in-composition, `rememberSaveable` across config change. Derive with `derivedStateOf`. Never mutate non-`State` vars in composition.
- **Effects:** `LaunchedEffect(key)` for suspend on enter/key-change; `rememberCoroutineScope` for event-driven; `DisposableEffect` for cleanup; `produceState` to adapt non-Compose → State.
- **Recomposition perf:** pass stable params; use `key=` in `LazyColumn/Row`; hoist heavy reads; avoid lambdas allocating each recompose where it matters; `@Stable/@Immutable` on model classes; prefer `Modifier.graphicsLayer{}` for animating transform/alpha (skips relayout).
- **Modifier order matters** (padding before/after background, clickable surface size). Chain deliberately.
- **Lists:** `LazyColumn`/`LazyVerticalGrid` with stable keys + `contentType`. **Paging 3** for big/remote lists.
- **Images:** **Coil 3** (`AsyncImage`), with crossfade + placeholder/shimmer.

## 5. Navigation 3 cheat-sheet  → skill: `android-navigation3`
- Type-safe routes (`@Serializable` route objects/data classes). Back stack is a list you own.
- **Scenes** for dialogs, bottom sheets, list-detail, two-pane, supporting-pane — compose them, don't hack.
- Deep links, multiple back stacks (per-tab), conditional graphs (logged-in vs anonymous), return-a-result flows.
- Scope ViewModels to nav entries; integrate with Hilt. View interop only for legacy.

## 6. Adaptive layouts cheat-sheet  → skill: `android-compose-adaptive`
- Drive layout off **WindowSizeClass** (Compact/Medium/Expanded) — never raw dp checks for structure.
- Phones = bottom `NavigationBar`; Medium = `NavigationRail`; Expanded = permanent drawer / list-detail.
- Use list-detail & supporting-pane scaffolds for tablets/foldables; handle hinge/posture. Support keyboard/mouse on large screens.

## 7. Edge-to-edge cheat-sheet  → skill: `android-edge-to-edge`
- Call `enableEdgeToEdge()` in `onCreate` before `setContent`.
- Consume insets with `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` / `.systemBars` / `.ime`; let scaffolds handle most.
- Keep status/nav-bar icons legible (light/dark per background). Handle the **IME** (keyboard) insets for inputs. Don't double-pad.

## 8. God-tier visuals: 3D, motion, liquid glass  → skills: `android-compose-expert`, `kmp-liquid-glass`, `glassmorphism`
- **Real-time 3D:** Filament (`com.google.android.filament:filament-android`) or **SceneView** (`io.github.sceneview:sceneview`, + `sceneview` MCP). Heroes/scenes, not chrome.
- **Vector animation:** Rive (`app.rive:rive-android`) interactive; Lottie cinematic micro-anim.
- **Shaders:** AGSL `RuntimeShader` (API 33+) for gradients/noise/glass/glow; animate via a time uniform; drive reactivity from focus/scroll.
- **Liquid glass** (consult `kmp-liquid-glass` skill): pick **ONE** library per project — skydoves **Cloudy**, **Kyant0/AndroidLiquidGlass**, **Kashif-E backdrop**, or **Haze** (simple frost). **Fallback chain:** AGSL (33+) → `RenderEffect` blur (31+) → gradient+alpha. Always provide a fallback; keep text legible.
- **Compose motion:** `graphicsLayer` pseudo-3D (rotationX/Y + cameraDistance), `SharedTransitionLayout` hero transitions, spring/physics, parallax.

## 9. Default per-project library stack (add automatically)
- **Foundation:** Compose BOM, Coroutines/Flow, Lifecycle ViewModel Compose, Navigation 3.
- **Visuals/3D/motion:** Filament, SceneView, Rive, Lottie, AGSL, `material-icons-extended`, Google Fonts, **Coil 3**, **Haze**, **Compose Shimmer**, Palette, shared-element.
- **Brain/data:** **Hilt, Room, DataStore, WorkManager, Retrofit+OkHttp/Ktor, kotlinx.serialization, Paging 3.**
- **Quality:** Detekt, Spotless/ktlint, LeakCanary (debug), Turbine, Compose UI Test, Roborazzi/Paparazzi, Macrobenchmark + Baseline Profiles, R8.
- **Backend (if used):** Firebase BoM. Verify latest versions via `context7` / Maven Central before pinning. Avoid deprecated Accompanist.

## 10. Testing & quality  → skill: `android-testing-setup`, plugin `android-skills`, + audit commands
- **Unit:** JUnit + Turbine (flows) + MockK/fakes. Test ViewModels + repositories.
- **Compose UI test:** `createAndroidComposeRule`, `onNodeWithText/Tag`, assertions + actions.
- **Screenshot:** **Roborazzi** (or Paparazzi) for visual regressions on components/screens.
- **Perf:** Macrobenchmark + Baseline Profiles for startup/scroll.
- **Codebase audits (installed 2026-06-29):** run `/security-audit`, `/performance-audit`, `/ui-audit` — each works through 100 expert prompts on a dedicated `vibecoder/*` branch, one commit per fix, fully resumable. Great as a pre-release sweep.

## 11. Build, sign, deliver the APK
- **JDK:** project Gradle uses **JDK 21 (LTS)** — `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`. (For `sdkmanager`/`avdmanager` use Android Studio's JBR: `C:\Program Files\Android\Android Studio\jbr`.)
- **Debug build:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. Debug signs with `~/.android/debug.keystore` (restored; alias `androiddebugkey`, pw `android`) → stable signature so users update without uninstalling.
- **Release (when shipping):** create/keep a release keystore; `assembleRelease` (APK) or `bundleRelease` (AAB for Play). R8/minify on.
- **Handoff:** give the user the APK path + "Settings → allow install from unknown sources → tap the file" + a 3-item "what to try" checklist.
- **adb:** standalone at `C:\adb` (on PATH) and the SDK `platform-tools`. Deploy to a real device: `adb install -r app-debug.apk`.

## 12. Android TV / Google TV specifics (target: Chromecast w/ Google TV, API 34)
- Use **Compose for TV** (`androidx.tv:tv-material`, `tv-foundation`) — not Leanback. Phone/tablet stay standard M3 (don't pull tv-* libs into them).
- **10-foot UI:** 16:9, overscan-safe margins (48dp horizontal / 27dp vertical), big readable type.
- **D-pad only:** every element focusable + a clear focus highlight; no touch/mouse reliance. Use TV `Carousel`, immersive lists.
- **Emulator (opt-in):** `Television_1080p` = Google TV API 34 (kept). Games need arm64-v8a to also run on the real Chromecast; deploy via wireless adb (`adb connect <tv-ip>:5555`).

## 13. Router — which tool for which decision
| Need | Go to |
|---|---|
| App structure, nav graph, adaptive plan | `android-ux-architect` agent · §1A, §5, §6 |
| Color/type/visual identity | `android-ui-designer` + `android-material3-design` · `ui-ux-pro-max --stack jetpack-compose` · §3 |
| Clean architecture / data layer | `android-architecture` + `android-skills` plugin · §2 |
| Tricky Compose state/perf/motion | `android-compose-expert` · §4, §8 |
| Glass / blur / refraction UI | `kmp-liquid-glass` skill (+ cloned glass repos) · §8 |
| Live API/library docs while coding | `android-docs` + `context7` MCP |
| 3D / AR scene content | `sceneview` MCP · §8 |
| Figma handoff | `figma` MCP (only with a Figma URL) |
| Pre-release quality sweep | `/security-audit` `/performance-audit` `/ui-audit` · §10 |
| Emulator test (opt-in) | `mobile-mcp` on "run → test apps" |

---
*Keep `SKILL.md` as the trigger/doctrine. Update this plan when the stack changes. NO SAMENESS — invent a fresh art direction per app.*
