package com.jeongmin.honeymoondoctor.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeongmin.honeymoondoctor.R

/**
 * Firebase 설정이 없거나 초기화되지 않았을 때 항상 화면 상단에 노출하는 배너.
 * 사용자가 "왜 로그인이 안 되지"라고 오해하지 않도록 상태를 분명히 알린다.
 */
@Composable
fun DemoModeBanner(modifier: Modifier = Modifier) {
    val text = stringResource(R.string.demo_mode_banner)
    Surface(
        modifier = modifier.semantics { contentDescription = text },
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
