---
name: ui-design-system
description: Guides the design, implementation, and governance of an Android Material 3 Design System in Jetpack Compose. Covers design tokens (colors, typography, shapes, elevation), reusable UI components, theming (light, dark, dynamic color), spacing grids, and component catalog maintenance.
metadata:
  author: ScanPro Team
  keywords:
  - design-system
  - material3
  - compose
  - tokens
  - typography
  - color-palette
  - components
  - theming
---

# UI Design System Skill

Use this skill to establish, maintain, and enforce a consistent Material Design 3 (M3) design system and component architecture in Android applications using Jetpack Compose.

## Step 1: Design Tokens Architecture

Every UI element should derive from centralized design tokens rather than hardcoded raw values.

### 1. Color Palette & Roles
- Define color tokens according to M3 color roles:
  - `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`
  - `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`
  - `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`
  - `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`
  - `background`, `onBackground`
  - `error`, `onError`, `errorContainer`, `onErrorContainer`
  - `outline`, `outlineVariant`
- Support both **Light Theme** and **Dark Theme** color schemes.
- Enable Dynamic Color on Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`) via `dynamicLightColorScheme(context)` and `dynamicDarkColorScheme(context)`.
- For domain-specific custom colors (e.g., custom warning, premium gold, success green), extend the theme using `CompositionLocalProvider` and immutable data classes.

### 2. Spacing & Grid System
- Standardize spacing using an 8dp/4dp base grid scale:
  - `extraSmall`: 4.dp
  - `small`: 8.dp
  - `medium`: 16.dp
  - `large`: 24.dp
  - `extraLarge`: 32.dp
- Store spacing values in a dedicated `Dimensions` or `Spacing` object exposed through `MaterialTheme.spacing`.

### 3. Typography Scale
- Configure all 15 M3 typography roles:
  - `displayLarge`, `displayMedium`, `displaySmall`
  - `headlineLarge`, `headlineMedium`, `headlineSmall`
  - `titleLarge`, `titleMedium`, `titleSmall`
  - `bodyLarge`, `bodyMedium`, `bodySmall`
  - `labelLarge`, `labelMedium`, `labelSmall`
- Never specify hardcoded raw font sizes (`fontSize = 16.sp`) inside feature screens; always reference `MaterialTheme.typography.bodyLarge`.

### 4. Shapes & Corners
- Maintain consistent shape tokens:
  - `extraSmall`: RoundedCornerShape(4.dp)
  - `small`: RoundedCornerShape(8.dp)
  - `medium`: RoundedCornerShape(12.dp)
  - `large`: RoundedCornerShape(16.dp)
  - `extraLarge`: RoundedCornerShape(28.dp)

---

## Step 2: Core Reusable Component Library

Centralize core UI components under a `core/designsystem` or `ui/theme/components` package:

1. **Buttons & Actions:**
   - Standardize `PrimaryButton`, `SecondaryButton`, `OutlineButton`, and `TextActionButton`.
   - Ensure built-in loading indicator support, disabled states, and minimum 48dp touch heights.
2. **Cards & Containers:**
   - Consistent surface cards (`AppCard`, `OutlinedAppCard`) with unified border stroke, tonal elevation, and shape.
3. **Form Controls & Inputs:**
   - Unified `AppTextField` with predefined error styling, placeholder, leading/trailing icons, and focus outlines.
4. **App Bars & Navigation:**
   - Standard `AppTopBar` with title typography, navigation back action, and optional trailing action icons.
5. **Dialogs & Bottom Sheets:**
   - Reusable `AppModalBottomSheet` and `AppConfirmDialog` adhering to the design system corner radii, scrim colors, and action layouts.

---

## Step 3: Previews and Component Catalog

- For each reusable component, provide a rich `@Preview` covering:
  - Light theme vs. Dark theme
  - Normal vs. Pressed vs. Disabled state
  - Default text scale vs. 1.5x / 2.0x accessibility font scale
- Multipreview annotations:
  ```kotlin
  @Preview(name = "Light Mode", showBackground = true)
  @Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
  annotation class ThemePreviews
  ```
- Keep components decoupled from feature domain models: components must receive primitive or UI-specific state values and lambda callbacks.
