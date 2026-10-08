package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.DiseaseStatus
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun DiseasesScreen(clinic: ClinicSnapshot) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Choroby", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
        Text("Aktualne", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        clinic.diseases.filter { it.status == DiseaseStatus.AKTUALNA }.forEach { d ->
            DiseaseCard(d.name, d.icd, d.diagnosedLabel, active = true)
        }
        Text("Historyczne", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        clinic.diseases.filter { it.status == DiseaseStatus.HISTORYCZNA }.forEach { d ->
            DiseaseCard(d.name, d.icd, d.diagnosedLabel, active = false)
        }
    }
}

@Composable
private fun DiseaseCard(name: String, icd: String, diagnosed: String, active: Boolean) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = if (active) ProPlusColors.Surface else ProPlusColors.Bg,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy, fontWeight = FontWeight.SemiBold)
            if (icd.isNotBlank()) {
                Text("ICD: $icd", style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Accent)
            }
            Text("Diagnoza: $diagnosed", style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
            Text(
                if (active) "Aktualna" else "Historyczna",
                style = MaterialTheme.typography.labelLarge,
                color = if (active) ProPlusColors.ResultWatch else ProPlusColors.Muted,
            )
        }
    }
}
