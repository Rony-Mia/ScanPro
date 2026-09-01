package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.ScanProViewModel
import com.example.navigation.ScanProNavGraph
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppUpdater
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {

    private val viewModel: ScanProViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val isDark by viewModel.darkMode.collectAsState()
            MyApplicationTheme(darkTheme = isDark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ScanProNavGraph(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    AutoUpdateCheck()
                }
            }
        }
    }
}

/**
 * Silently checks GitHub Releases once per app launch. If a newer build is
 * available, shows a lightweight dialog so the user can update in a couple
 * of taps instead of going back to GitHub manually every time.
 */
@Composable
private fun AutoUpdateCheck() {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        updateInfo = AppUpdater.checkForUpdate()
    }

    val info = updateInfo
    if (info != null) {
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update available") },
            text = { Text("ScanPro ${info.versionName} is available. Update now?") },
            confirmButton = {
                TextButton(onClick = {
                    AppUpdater.downloadAndInstall(context, info)
                    updateInfo = null
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("Later") }
            }
        )
    }
}
