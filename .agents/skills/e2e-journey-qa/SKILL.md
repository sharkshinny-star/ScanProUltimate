---
name: e2e-journey-qa
description: Plans, writes, and executes End-to-End Critical User Journey (CUJ) QA tests on Android apps using Android CLI journeys, UI Automator, or Compose UI Test. Validates user flows, screen transitions, state preservation, edge cases, and generates test execution reports.
metadata:
  author: ScanPro Team
  keywords:
  - e2e
  - cuj
  - journeys
  - qa
  - testing
  - ui-automator
  - android-screen
---

# E2E Journey QA Skill

Use this skill to design, write, automate, and execute Critical User Journey (CUJ) tests across Android applications to verify end-to-end functionality, screen transitions, and user experience resilience.

## Step 1: Map Critical User Journeys (CUJs)

1. Break down the application into high-priority end-to-end journeys:
   - **Primary Workflows:** Document scanning -> Edge adjustment -> Image filter application -> PDF export -> Sharing.
   - **Onboarding & Permissions:** Permission request prompts (Camera, Storage/Media), grant/deny recovery paths.
   - **Navigation & Back-Stack:** Deep-linking, back-navigation integrity, drawer/tab switching without state loss.
   - **Failure & Recovery:** Network disconnection during cloud sync, invalid file formats, low storage warning handling.

2. Document each journey with concrete, observable actions and assertions:
   - Initial precondition (e.g. app launched, cold start).
   - Sequence of user interactions (Tap element X, scroll down, type text into input field).
   - Expected assertions at each milestone (Verify screen title is "Edit Document", verify button "Export" is enabled).

---

## Step 2: Journey Test Formats & Implementations

### Format A: Android Journey XML (`.journey.xml`)
For use with Android CLI, ADB interactions, and device screen/layout inspection:

```xml
<journey name="Document Scan and Export">
  <description>
    Tests the complete user journey from scanning a new document to exporting as PDF.
  </description>
  <actions>
    <action>Tap the floating "Scan New Document" button</action>
    <action>Verify the camera preview viewfinder is displayed</action>
    <action>Tap the shutter capture button</action>
    <action>Verify the document crop review screen appears</action>
    <action>Tap "Save & Export PDF"</action>
    <action>Verify success notification or exported document detail view</action>
  </actions>
</journey>
```

### Format B: Automated Compose UI Test (`createAndroidComposeRule`)
For fast, deterministic in-repository automated UI tests:

```kotlin
@RunWith(AndroidJUnit4::class)
class ScanExportJourneyTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scanAndExportDocument_completesSuccessfully() {
        // Step 1: Tap scan button
        composeTestRule.onNodeWithContentDescription("Scan Document").performClick()

        // Step 2: Verify preview screen
        composeTestRule.onNodeWithText("Crop & Filter").assertIsDisplayed()

        // Step 3: Trigger export
        composeTestRule.onNodeWithText("Export PDF").performClick()

        // Step 4: Verify confirmation
        composeTestRule.onNodeWithText("PDF Exported Successfully").assertIsDisplayed()
    }
}
```

---

## Step 3: Execution and Verification Checklist

When executing a journey test on an active device or emulator:

1. **Pre-flight Check:**
   - Confirm target device is connected (`adb devices`).
   - Launch app or reset app state if clean test is required (`adb shell pm clear <package>`).
2. **Step-by-Step Evaluation:**
   - Execute each UI action (`adb shell input tap`, `adb shell input text`, or Compose node actions).
   - Allow animations and async jobs to settle before verifying expectations.
   - Capture screen state if an expectation fails (`android screen capture -o failure.png` or `screencap`).
3. **Robust Element Locator Hierarchy:**
   - Prefer semantic test tags (`testTag("tag_name")`) or explicit accessibility labels (`contentDescription`).
   - Fall back to text matching (`onNodeWithText`).
   - Avoid brittle absolute pixel coordinates whenever possible.

---

## Step 4: Generate QA Journey Report

At the conclusion of journey execution, generate an actionable QA report:

```markdown
# E2E Journey QA Report: [Journey Name]

## Test Summary
- **App Version:** 1.0.0 (Build 42)
- **Device / API:** Pixel 8 - Android 15 (API 35)
- **Status:** Passed (5/5 steps) / Failed at Step 4

## Step-by-Step Results
### Step 1: Launch app and navigate to Scan ✅
- **Action:** Tapped "Scan" button
- **Outcome:** Camera viewport initialized within 300ms

### Step 2: Crop boundary detection ✅
- **Action:** Verified auto-detected quad polygon
- **Outcome:** Bounds correctly matched sample test target

### Step 3: Export PDF ❌ (if failed)
- **Action:** Tapped "Save PDF"
- **Observed Behavior:** Export spinner persisted indefinitely
- **Failure Artifact:** `screenshots/failure_step3.png`
- **Root Cause / Logcat:** `IOException: Storage space insufficient`

## Defects Identified & Recommendations
- [High] Provide user-facing dialog when disk write fails during export.
```
