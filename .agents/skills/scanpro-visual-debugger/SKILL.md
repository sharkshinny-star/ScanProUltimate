---
name: scanpro-visual-debugger
description: Debug, isolate, and fix visual, interaction, layout, state, recomposition, or navigation defects in ScanPro Jetpack Compose screens and routes. Use when diagnosing UI glitches, state desynchronization, improper insets, clipping, or visual rendering issues.
---

# ScanPro Visual Debugger Skill

This skill provides a structured, evidence-based diagnostic and debugging workflow for Jetpack Compose UI issues in **ScanPro Ultimate**.

## Mandatory Diagnostic Workflow

When a visual, layout, interaction, state, or navigation issue is detected, follow this 8-step workflow strictly:

### 1. Reproduce on Running App / Environment
- Observe the defect in its actual runtime context (connected device, emulator, or rendered Compose Previews).
- Capture the initial state and document the precise steps to trigger the defect.

### 2. Capture Visual Artifacts & Inspect UI Hierarchy
- Take screenshots or render Compose Previews (`render_compose_preview`) to analyze visual layout bounds, alignment, and clipping.
- Inspect accessibility semantics and layout hierarchy bounds to detect overlapping or misaligned nodes.

### 3. Trace Code Components & Data Flow
- **Stateless UI (`*Screen`)**: Inspect composable layout parameters, modifier chains, and styling.
- **Stateful Wrapper (`*Route`)**: Verify event callbacks, parameter passing, and `StateFlow` collection.
- **ViewModel (`@HiltViewModel`)**: Trace `StateFlow` / `SharedFlow` transformations, initial state seeds, and async dispatchers.
- **Navigation Graph (`ScanProNavHost.kt`)**: Verify route destinations, argument bundle decoding, and backstack management.

### 4. Investigate Layout & Runtime Constraints
- **Layout Constraints**: Check `BoxWithConstraints`, `Modifier.fillMaxSize()`, `Modifier.weight()`, and content paddings.
- **System Insets & Edge-to-Edge**: Verify `WindowInsets` handling (status bar, navigation bar, IME keyboard insets).
- **Recomposition & Draw Phase**: Inspect state reads—prefer lambda-based modifiers (`.graphicsLayer { ... }`) over direct state reads in composition to eliminate dropped frames.
- **Lifecycle & Asynchronous State**: Check `collectAsStateWithLifecycle()`, `rememberSaveable` state preservation, and WorkManager status integration.

### 5. Identify Root Cause Before Modifying Code
- Pinpoint the exact failure mechanism (e.g., state read timing, missing `key`/`contentType` in `LazyColumn`, constraint overflow, or context wrapping). Do not make trial-and-error edits.

### 6. Apply Surgical & Minimal Fix
- Implement the smallest reliable change that resolves the root cause.
- Preserve UDF (Unidirectional Data Flow), Hilt dependency injection, `StateFlow` usage, and `*Screen`/`*Route` architecture.

### 7. Build, Run & Verify Visual Parity
- Run local unit tests: `./gradlew :app:testDebugUnitTest`.
- Render updated Compose Previews or take new screenshots to visually verify the fix.
- Ensure no visual regressions occur across alternate themes (Light/Dark) or font scales.

### 8. Require Evidence Before Marking Verified
- Mark a defect resolved **only** when verified with direct evidence (passing unit tests, clean build output, or visual screenshot/preview comparisons).

---

## Architectural & Project Constraints

- **Architecture**: UDF architecture (`ViewModel` + `StateFlow` $\rightarrow$ `*Route` $\rightarrow$ `*Screen`).
- **Navigation**: Keep Navigation Compose (`ScanProNavHost.kt`). Do NOT migrate to Navigation 3 automatically.
- **Dependencies**: Do not add unnecessary third-party libraries.
- **Thread Safety**: Never perform heavy computations or image decoding on `Dispatchers.Main`.
- **API Parity**: Ensure UI logic functions correctly across API 24 through API 37 (Android 17 adaptability).
