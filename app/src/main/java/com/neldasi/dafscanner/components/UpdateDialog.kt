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
import androidx.compose.ui.res.stringResource
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.extras.UpdateManager

@Composable
fun UpdateDialog(
    info: UpdateManager.ReleaseInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_update_available), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.version_available, info.tagName), fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}
