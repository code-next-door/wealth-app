package io.github.codenextdoor.wealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R

/**
 * Asks for a locked statement's password. The typed text lives only in memory
 * ([remember], not saved screen state, which Android may write to disk), is handed to
 * [onUnlock] for one read, and cleared.
 */
@Composable
fun StatementPassword(title: String, wrongPassword: Boolean, onUnlock: (String) -> Unit, modifier: Modifier = Modifier) {
    val password = remember { TextFieldState() }
    val submit = {
        val typed = password.text.toString()
        password.clearText()
        if (typed.isNotEmpty()) onUnlock(typed)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.statement_password_text), style = MaterialTheme.typography.bodyMedium)
        OutlinedSecureTextField(
            state = password,
            label = { Text(stringResource(R.string.statement_password_label)) },
            isError = wrongPassword,
            supportingText = if (wrongPassword) ({ Text(stringResource(R.string.statement_password_wrong)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { submit() },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = submit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.statement_password_open)) }
    }
}
