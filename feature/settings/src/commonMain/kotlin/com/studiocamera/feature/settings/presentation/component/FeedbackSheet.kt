package com.studiocamera.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class FeedbackData(
    val cameraBrand: String = "",
    val cameraModel: String = "",
    val worksChecks: Set<String> = emptySet(),
    val issueChecks: Set<String> = emptySet(),
    val comment: String = "",
    val email: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackSheet(
    onDismiss: () -> Unit,
    onSend: (FeedbackData) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var data by remember { mutableStateOf(FeedbackData()) }

    val brands = listOf("Sony", "Canon", "Nikon", "Fujifilm", "Panasonic/Lumix", "OM System", "Other")
    val features = listOf("Connection", "Live View", "Capture", "Settings Control", "Media Transfer")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Feedback & support",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "We'll do our best to address your request within a week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Camera brand dropdown
            var brandExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = brandExpanded,
                onExpandedChange = { brandExpanded = it }
            ) {
                OutlinedTextField(
                    value = data.cameraBrand,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Camera brand") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = brandExpanded,
                    onDismissRequest = { brandExpanded = false }
                ) {
                    brands.forEach { brand ->
                        DropdownMenuItem(
                            text = { Text(brand) },
                            onClick = {
                                data = data.copy(cameraBrand = brand)
                                brandExpanded = false
                            }
                        )
                    }
                }
            }

            // Camera model
            OutlinedTextField(
                value = data.cameraModel,
                onValueChange = { data = data.copy(cameraModel = it) },
                label = { Text("Camera model") },
                placeholder = { Text("e.g. A7 III, EOS R5, X-T5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // What works checkboxes
            Text("What works?", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = feature in data.worksChecks,
                            onCheckedChange = { checked ->
                                data = data.copy(
                                    worksChecks = if (checked) data.worksChecks + feature
                                    else data.worksChecks - feature
                                )
                            }
                        )
                        Text(feature, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // What doesn't work checkboxes
            Text("What doesn't work?", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = feature in data.issueChecks,
                            onCheckedChange = { checked ->
                                data = data.copy(
                                    issueChecks = if (checked) data.issueChecks + feature
                                    else data.issueChecks - feature
                                )
                            }
                        )
                        Text(feature, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Comment
            OutlinedTextField(
                value = data.comment,
                onValueChange = { data = data.copy(comment = it) },
                label = { Text("Comments") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            // Email
            OutlinedTextField(
                value = data.email,
                onValueChange = { data = data.copy(email = it) },
                label = { Text("Email (optional)") },
                placeholder = { Text("For follow-up") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onSend(data) },
                modifier = Modifier.fillMaxWidth(),
                enabled = data.cameraBrand.isNotBlank()
            ) {
                Text("Send feedback")
            }
        }
    }
}
