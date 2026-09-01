package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanProViewModel
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProAccentRed
import com.example.ui.theme.ScanProGreenContainer
import com.example.util.AppUpdater
import com.example.util.Constants
import com.example.util.ShareUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkMode by viewModel.darkMode.collectAsState()
    val autoCapture by viewModel.autoCapture.collectAsState()
    val defaultSaveLocation by viewModel.defaultSaveLocation.collectAsState()
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val language by viewModel.language.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var checkedOnce by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSaveLocationDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomSaveLocation(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // General Section
            item {
                SettingsSectionHeader("GENERAL")
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Reduce eye strain with dark interface",
                    checked = darkMode,
                    onCheckedChange = { viewModel.toggleDarkMode(it) },
                    testTag = "settings_dark_mode_switch"
                )
            }

            item {
                SettingsValueRow(
                    icon = Icons.Outlined.Folder,
                    title = "Save Location",
                    value = defaultSaveLocation,
                    onClick = { showSaveLocationDialog = true },
                    testTag = "settings_save_location_row"
                )
            }

            item {
                SettingsValueRow(
                    icon = Icons.Outlined.Language,
                    title = "Language",
                    value = language,
                    onClick = { viewModel.showToast("Language: $language") }
                )
            }

            // Scanning Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsSectionHeader("SCANNING")
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Auto-Capture",
                    subtitle = "Automatically trigger shutter when document edge is detected",
                    checked = autoCapture,
                    onCheckedChange = { viewModel.toggleAutoCapture(it) },
                    testTag = "settings_auto_capture_switch"
                )
            }

            item {
                SettingsValueRow(
                    icon = Icons.Outlined.HighQuality,
                    title = "Default Image Quality",
                    value = defaultQuality,
                    onClick = { viewModel.showToast("Quality: $defaultQuality") }
                )
            }

            // About & Privacy
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsSectionHeader("ABOUT & PRIVACY")
            }

            item {
                SettingsClickableRow(
                    icon = Icons.Outlined.Shield,
                    title = "Privacy & Offline Guarantee",
                    subtitle = "100% on-device processing. No data collection.",
                    onClick = { showPrivacyDialog = true },
                    testTag = "settings_privacy_policy_row"
                )
            }

            item {
                SettingsClickableRow(
                    icon = Icons.Outlined.StarRate,
                    title = "Rate ScanPro",
                    subtitle = "Share your feedback on the store",
                    onClick = { viewModel.showToast("Thank you for supporting ScanPro!") }
                )
            }

            item {
                SettingsClickableRow(
                    icon = Icons.Outlined.Share,
                    title = "Share App",
                    subtitle = "Share ScanPro APK download link with others",
                    onClick = {
                        coroutineScope.launch {
                            val apkUrl = AppUpdater.getLatestReleaseApkDownloadUrl()
                            ShareUtil.shareApp(context, apkUrl)
                        }
                    },
                    testTag = "settings_share_app_row"
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsSectionHeader("APP UPDATE")
            }

            item {
                SettingsClickableRow(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = when {
                        isCheckingUpdate -> "Checking..."
                        updateInfo != null -> "New version ${updateInfo?.versionName} available — tap to download"
                        checkedOnce -> "You're on the latest version"
                        else -> "Current version: ${com.example.BuildConfig.VERSION_NAME}"
                    },
                    onClick = {
                        if (updateInfo != null) {
                            AppUpdater.downloadAndInstall(context, updateInfo!!)
                            viewModel.showToast("Downloading update...")
                        } else if (!isCheckingUpdate) {
                            isCheckingUpdate = true
                            coroutineScope.launch {
                                val result = AppUpdater.checkForUpdate()
                                updateInfo = result
                                isCheckingUpdate = false
                                checkedOnce = true
                                if (result == null) {
                                    viewModel.showToast("You're on the latest version")
                                }
                            }
                        }
                    },
                    testTag = "settings_check_update_row"
                )
            }

            // Developer / Demo state helpers
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsSectionHeader("DATA MANAGEMENT")
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.clearAllDocumentsForEmptyState() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ScanProAccentRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_clear_docs_button")
                ) {
                    Text("Clear All Documents", fontSize = 13.sp)
                }
            }

            // App Version Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ScanPro v${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Clean • Offline • Secure",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showSaveLocationDialog) {
        val isDefault = viewModel.isDefaultSaveLocation()
        AlertDialog(
            onDismissRequest = { showSaveLocationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.FolderSpecial,
                        contentDescription = null,
                        tint = ScanProGreenContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Choose Save Location", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select where newly scanned and exported PDFs, images, and documents will be saved:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: App Folder (Default)
                    Surface(
                        onClick = {
                            viewModel.setDefaultSaveLocation()
                            showSaveLocationDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDefault) ScanProGreenContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isDefault) 1.5.dp else 1.dp,
                            color = if (isDefault) ScanProGreenContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("save_location_default_option")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isDefault,
                                onClick = {
                                    viewModel.setDefaultSaveLocation()
                                    showSaveLocationDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ScanProGreenContainer
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "App folder (default)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = Constants.DEFAULT_SAVE_LOCATION_NAME,
                                    fontSize = 12.sp,
                                    color = if (isDefault) ScanProGreenContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Custom Folder via SAF
                    Surface(
                        onClick = {
                            showSaveLocationDialog = false
                            try {
                                folderPickerLauncher.launch(null)
                            } catch (_: Exception) {
                                viewModel.showToast("Could not open system folder picker")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isDefault) ScanProGreenContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (!isDefault) 1.5.dp else 1.dp,
                            color = if (!isDefault) ScanProGreenContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("save_location_custom_option")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isDefault,
                                onClick = {
                                    showSaveLocationDialog = false
                                    try {
                                        folderPickerLauncher.launch(null)
                                    } catch (_: Exception) {
                                        viewModel.showToast("Could not open system folder picker")
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ScanProGreenContainer
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Choose custom folder",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (!isDefault) defaultSaveLocation else "Pick any directory on device / SD card",
                                    fontSize = 12.sp,
                                    color = if (!isDefault) ScanProGreenContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSaveLocationDialog = false }) {
                    Text("Close", fontWeight = FontWeight.SemiBold, color = ScanProGreenContainer)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = ScanProGreenContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy & Security")
                }
            },
            text = {
                Text(
                    text = "ScanPro is designed from the ground up to protect your privacy:\n\n" +
                            "• Zero Cloud Uploads: All document scanning, OCR, PDF merging, and encryption happen strictly on your device.\n\n" +
                            "• Offline First: ScanPro never requires an internet connection to process your sensitive records.\n\n" +
                            "• Encrypted Storage: Protected documents are safeguarded with AES-256 standard encryption.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Got it", fontWeight = FontWeight.Bold, color = ScanProGreenContainer)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ScanProGreenContainer
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = ScanProGreenContainer,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
