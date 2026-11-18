package com.example.scanpro.core.designsystem

/*
 * Tool demonstrations (F195) for: Merge, Split, Organise (rotate, extract), Insert pages, Remove blank pages, N-up, Page numbers, Compress PDF.
 * Built with the shared pieces in ToolDemos.kt. Keep the map lazy (or at the bottom of the file): top-level values
 * are set in file order, so a map above the scenes it names would hold nulls.
 */

/** Scenes by tool id for this group; [toolDemoScene] looks here after ToolDemos.kt's own. */
internal val PAGE_TOOL_SCENES: Map<String, DemoScene> by lazy { emptyMap() }
