package pl.cardioscp.rehab.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.auth.DemoUser
import pl.cardioscp.rehab.auth.DemoUsers
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun RolePinLoginScreen(
    onLogin: (String) -> DemoUser?,
) {
    var selected by remember { mutableStateOf<DemoUser?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun tryLogin() {
        val candidate = selected
        val entered = pin.trim()
        if (candidate != null && entered != candidate.pin) {
            error = "PIN nie pasuje do wybranej roli"
            return
        }
        val user = onLogin(entered)
        if (user == null) {
            error = "Nieprawidłowy PIN"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Pro-PLUS Cardio Rehab",
            style = MaterialTheme.typography.headlineSmall,
            color = ProPlusColors.Navy,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Wybierz rolę i podaj PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        DemoUsers.all.forEach { user ->
            val isSelected = selected?.pin == user.pin
            Surface(
                onClick = {
                    selected = user
                    pin = ""
                    error = null
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    ProPlusColors.Accent.copy(alpha = 0.12f)
                } else {
                    ProPlusColors.Surface
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) ProPlusColors.Accent else ProPlusColors.Line,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = ProPlusColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            user.roleLabelPl,
                            style = MaterialTheme.typography.bodySmall,
                            color = ProPlusColors.Muted,
                        )
                    }
                    Text(
                        "PIN ${user.pin}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) ProPlusColors.Accent else ProPlusColors.Muted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter { ch -> ch.isDigit() }.take(8)
                error = null
            },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { tryLogin() }),
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
        Button(
            onClick = { tryLogin() },
            enabled = pin.length >= 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Zaloguj", style = MaterialTheme.typography.labelLarge)
        }
    }
}
