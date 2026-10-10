package io.github.codenextdoor.wealth.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.localDateFormat
import java.time.YearMonth

/** Overview, first week of a month: last month's review is ready. */
@Composable
fun ReviewReadyCard(month: YearMonth, onOpen: () -> Unit, onNotNow: () -> Unit) {
    val monthName = remember(month) { month.atDay(1).format(localDateFormat("MMMM")) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.review_card_title, monthName), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.review_card_text), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.reminders_not_now)) }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.review_open)) }
            }
        }
    }
}
