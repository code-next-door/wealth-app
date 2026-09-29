package io.github.codenextdoor.wealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Round badge with the account's first letter; red-toned for debts. Colour alone
 * doesn't say "debt": screens using it also show it in words or a sign.
 */
@Composable
fun AccountBadge(name: String, isLiability: Boolean) {
    val container = if (isLiability) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isLiability) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
    ) {
        Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = content)
    }
}
