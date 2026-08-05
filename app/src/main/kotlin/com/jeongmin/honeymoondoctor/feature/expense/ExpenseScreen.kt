package com.jeongmin.honeymoondoctor.feature.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jeongmin.honeymoondoctor.R
import com.jeongmin.honeymoondoctor.core.ui.PlaceholderScreen

@Composable
fun ExpenseScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(id = R.string.tab_expense), modifier = modifier)
}
