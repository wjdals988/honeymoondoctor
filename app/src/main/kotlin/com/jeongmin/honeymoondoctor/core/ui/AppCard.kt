package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 화면마다 개별 구현되던 카드(ItineraryCard/TopPlaceCard/ExpenseRow/BudgetCard 등)의 공통 베이스. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Neutral,
    shape: Shape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = tone.containerColor(),
        contentColor = tone.contentColor(),
    )
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}
