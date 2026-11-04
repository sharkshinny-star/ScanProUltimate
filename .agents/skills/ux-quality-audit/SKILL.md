---
name: ux-quality-audit
description: Audits Android apps for UX quality, accessibility, touch target sizing (minimum 48dp), visual hierarchy, edge-to-edge insets, color contrast, typography scaling, loading and empty states, haptic feedback, and user friction points.
metadata:
  author: ScanPro Team
  keywords:
  - ux
  - audit
  - accessibility
  - touch-targets
  - contrast
  - empty-states
  - user-experience
  - quality
---

# UX Quality Audit Skill

Use this skill to conduct structured, expert-level user experience (UX) and accessibility (a11y) audits across Android app screens and workflows.

## Step 1: Discover and Scope Target Screens

1. Identify the key screens and user flows to audit:
   - Onboarding / Welcome flow
   - Primary dashboards and main capture/scanner interfaces
   - Detail views, preview screens, and edit workflows
   - Settings, dialogs, and bottom sheets
2. Review the screen implementation in Jetpack Compose or Views, checking layouts, modifiers, and theme usage.

## Step 2: Audit Core UX Dimensions

### 1. Touch Targets & Ergonomics
- Ensure all clickable/interactive elements meet the minimum accessible touch target size: **48dp × 48dp** (or use `Modifier.minimumInteractiveComponentSize()`).
- Verify sufficient spacing between adjacent touch targets (at least 8dp padding) to avoid accidental mis-taps.
- Place primary actions within easy thumb reach (bottom navigation, sticky bottom action bars, floating action buttons).

### 2. Accessibility & Screen Reader (TalkBack)
- Check that all icon-only buttons (`IconButton`, custom icons) provide clear, localized `contentDescription` strings. Decorative icons should set `contentDescription = null`.
- Ensure headers and section titles have semantic heading traits (`Modifier.semantics { heading() }`).
- Verify correct traversal and focus order for composite components using `Modifier.semantics(mergeDescendants = true)`.
- Support system font scaling up to 200% without clipping text or overlapping adjacent components (use `sp` for font sizes and avoid fixed component heights for text containers).

### 3. Visual Hierarchy & Contrast (WCAG 2.1 AA)
- Text contrast ratio:
  - Minimum **4.5:1** for regular body text.
  - Minimum **3:1** for large text (18sp bold or 24sp regular) and active UI controls/icons.
- Maintain a clear typographic rhythm (Title -> Body -> Caption) that guides the eye effortlessly.
- Use primary, secondary, and surface tonal variations intentionally to delineate interactive vs. background content.

### 4. Edge-to-Edge & System Insets
- Verify that app content renders behind status and navigation bars seamlessly.
- Ensure critical UI elements (buttons, bottom bars, text fields) properly pad against `WindowInsets.safeDrawing`, `navigationBars`, or `ime` (keyboard insets) so they are never obscured.

### 5. State Handling (The 4 States of UI)
- **Loading State:** Smooth indicators (circular progress, skeleton/shimmer placeholders) rather than blank screens or frozen UI.
- **Empty State:** Informative illustrations/icons, clear explanation of why it is empty, and a prominent call-to-action (CTA) to create or add content.
- **Error State:** Human-readable error messages explaining what happened and how to fix it, accompanied by a "Retry" or recovery action.
- **Success / Content State:** Instant visual confirmation of user actions with appropriate feedback.

### 6. Interaction & Micro-feedback
- Immediate visual response on tap (Material ripple effects enabled on clickable items).
- Subtle haptic feedback (`LocalHapticFeedback.current.performHapticFeedback(...)`) for key events (e.g., scan completed, delete confirmed, error triggered).

---

## Step 3: Generate the UX Audit Report

Compile audit findings into a structured markdown report formatted as follows:

```markdown
# UX Quality Audit Report: [Screen / Feature Name]

## Executive Summary
Brief summary of the audit, highlighting major strengths and critical friction points.

## Issue Scorecard
| Severity | Category | Description | Recommendation |
|----------|----------|-------------|----------------|
| 🔴 High   | Touch Target | Button size 32dp causes mis-taps | Increase padding to 48dp minimum |
| 🟡 Medium | Accessibility | Missing contentDescription on icon | Add localized string resource |
| 🟢 Low    | Visual Polish | Inconsistent card elevation | Align with M3 surface elevation tokens |

## Detailed Findings & Action Plan
### 1. [Finding Title]
- **Current Behavior:** Description of the issue
- **Impact:** User impact and friction
- **Proposed Fix (Code Snippet):**
  ```kotlin
  // Solution
  ```
```
