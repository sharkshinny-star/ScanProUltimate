package com.example.scanpro.feature.document

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.intelligence.BusinessCard
import com.example.scanpro.intelligence.DocumentInsights
import com.example.scanpro.intelligence.DocumentKind

/**
 * The document at a glance: what kind it is, a short extractive summary, its key amount and date, and the one
 * action that fits (adding a business card to contacts). Tap the summary to read it all.
 */
@Composable
fun SummaryCard(
    documentId: String,
    insights: DocumentInsights,
    card: BusinessCard?,
    onAddContact: (BusinessCard) -> Unit,
    /** Opens the full insights; null where they are already shown (the Insights sheet), which hides the link. */
    onMore: (() -> Unit)? = null
) {
    var expanded by rememberSaveable(documentId) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.doc_summary), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(insights.kind.labelRes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                insights.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) stringResource(R.string.doc_show_less) else stringResource(R.string.doc_show_all)) { expanded = !expanded }
            )
            val amount = insights.amounts.firstOrNull()
            val date = insights.dates.firstOrNull()
            val actionItemCount = insights.actionItems.size
            val facts = listOfNotNull(
                if (amount != null) stringResource(R.string.doc_fact_amount, amount) else null,
                if (date != null) stringResource(R.string.doc_fact_date, date) else null,
                if (actionItemCount > 0) pluralStringResource(R.plurals.doc_action_items, actionItemCount, actionItemCount) else null
            )
            if (facts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    facts.forEach { fact ->
                        if (onMore != null) AssistChip(onClick = onMore, label = { Text(fact, maxLines = 1) })
                        // Where there is nothing further to open, a fact is a plain label rather than a dead button.
                        else androidx.compose.material3.Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(fact, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Receipts and invoices carry a name and phone too; the card offers this only for business cards,
                // while the Insights tab lists contact details for any document.
                if (card != null && card.isUseful && insights.kind == DocumentKind.BUSINESS_CARD) {
                    FilledTonalButton(onClick = { onAddContact(card) }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.doc_add_to_contacts))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onMore != null) {
                    val openInsights = stringResource(R.string.doc_open_insights)
                    TextButton(onClick = onMore, modifier = Modifier.semantics { onClick(label = openInsights) { onMore(); true } }) { Text(stringResource(R.string.doc_details)) }
                }
            }
        }
    }
}
