package com.example.scanpro

import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Nodes carrying any of [texts] (a screen's tab labels change with the language, so tests look for a few). */
fun ComposeTestRule.onAllNodesWithTextAny(vararg texts: String): SemanticsNodeInteractionCollection =
    onAllNodes(texts.map { hasText(it) }.reduce { a, b -> a.or(b) })
