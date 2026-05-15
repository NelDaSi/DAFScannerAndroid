package com.neldasi.dafscanner.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neldasi.dafscanner.extras.UpdateManager

@Composable
fun UpdateDialog(
    info: UpdateManager.ReleaseInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Update Available!", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Version ${info.tagName} is available.", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(info.body)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    UpdateManager.downloadAndInstall(
                        context,
                        info.downloadUrl,
                        "dafscanner-${info.tagName}.apk"
                    )
                    onDismiss()
                }
            ) {
                Text("Update Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
