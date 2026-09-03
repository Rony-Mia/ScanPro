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
import com.example.model.ToolType
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
    val defaultSaveLocation by viewModel.defaultSaveLocation.collectAsState()
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val language by viewModel.language.collectAsState()
    val searchablePdfEnabled by viewModel.searchablePdfEnabled.collectAsState()
    val homeQuickActions by viewModel.homeQuickActions.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var checkedOnce by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSaveLocationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showOcrLanguagesDialog by remember { mutableStateOf(false) }
    var showHomeQuickActionsDialog by remember { mutableStateOf(false) }

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
                    onClick = { showLanguageDialog = true },
                    testTag = "settings_language_row"
                )
            }

            item {
                SettingsClickableRow(
                    icon = Icons.Outlined.DashboardCustomize,
                    title = "Customize Home Quick Actions",
                    subtitle = "${homeQuickActions.size} tools configured on Home screen",
                    onClick = { showHomeQuickActionsDialog = true },
                    testTag = "settings_customize_quick_actions_row"
                )
            }

            // Scanning & Image Quality Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsSectionHeader("SCANNING & QUALITY")
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Search,
                    title = "Searchable PDF",
                    subtitle = "Embed OCR text layer so text in generated PDFs can be selected and searched",
                    checked = searchablePdfEnabled,
                    onCheckedChange = { viewModel.toggleSearchablePdf(it) },
                    testTag = "settings_searchable_pdf_switch"
                )
            }

            item {
                SettingsValueRow(
                    icon = Icons.Outlined.HighQuality,
                    title = "Default Image Quality",
                    value = defaultQuality,
                    onClick = { showQualityDialog = true },
                    testTag = "settings_image_quality_row"
                )
            }

            item {
                SettingsClickableRow(
                    icon = Icons.Outlined.Language,
                    title = "OCR Language Packs",
                    subtitle = "English & Bengali pre-installed. Download offline packs.",
                    onClick = { showOcrLanguagesDialog = true },
                    testTag = "settings_ocr_languages_row"
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
                    subtitle = "100% on-device processing. No cloud uploads.",
                    onClick = { showPrivacyDialog = true },
                    testTag = "settings_privacy_policy_row"
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

    if (showQualityDialog) {
        val qualityOptions = listOf(
            "High (300 dpi)" to "Crisp text & best archival print quality",
            "Medium (200 dpi)" to "Balanced clarity with moderate file size",
            "Low (150 dpi)" to "Compact size, ideal for quick sharing"
        )
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HighQuality, contentDescription = null, tint = ScanProGreenContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Default Image Quality")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualityOptions.forEach { (option, desc) ->
                        val isSelected = defaultQuality == option
                        Surface(
                            onClick = {
                                viewModel.setDefaultQuality(option)
                                showQualityDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ScanProGreenContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setDefaultQuality(option)
                                        showQualityDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = ScanProGreenContainer)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(option, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Cancel", color = ScanProGreenContainer)
                }
            }
        )
    }

    if (showLanguageDialog) {
        val languageOptions = listOf(
            Triple("English (US)", "en-US", "Default"),
            Triple("Spanish (Español)", "es", "Español"),
            Triple("French (Français)", "fr", "Français"),
            Triple("German (Deutsch)", "de", "Deutsch"),
            Triple("Portuguese (Português)", "pt", "Português"),
            Triple("Chinese (Simplified)", "zh-CN", "简体中文"),
            Triple("Japanese (日本語)", "ja", "日本語"),
            Triple("Hindi (हिन्दी)", "hi", "हिन्दी")
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = ScanProGreenContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select App Language")
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(languageOptions.size) { idx ->
                        val (name, tag, nativeName) = languageOptions[idx]
                        val isSelected = language == name
                        Surface(
                            onClick = {
                                viewModel.setLanguage(name, tag)
                                showLanguageDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ScanProGreenContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ScanProGreenContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setLanguage(name, tag)
                                        showLanguageDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = ScanProGreenContainer)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(nativeName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Cancel", color = ScanProGreenContainer)
                }
            }
        )
    }

    if (showOcrLanguagesDialog) {
        com.example.ui.components.ManageOcrLanguagesDialog(
            viewModel = viewModel,
            onDismiss = { showOcrLanguagesDialog = false }
        )
    }

    if (showHomeQuickActionsDialog) {
        var tempActions by remember(homeQuickActions) { mutableStateOf(homeQuickActions.toSet()) }
        AlertDialog(
            onDismissRequest = { showHomeQuickActionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DashboardCustomize, contentDescription = null, tint = ScanProGreenContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Home Quick Actions")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose up to 4 quick action shortcuts to show on your Home screen dashboard:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ToolType.values().size) { idx ->
                            val tool = ToolType.values()[idx]
                            val isChecked = tempActions.contains(tool)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChecked) ScanProGreenContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isChecked) 1.5.dp else 1.dp,
                                    color = if (isChecked) ScanProGreenContainer else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempActions = if (isChecked) {
                                            if (tempActions.size > 1) tempActions - tool else tempActions
                                        } else {
                                            if (tempActions.size < 4) tempActions + tool else tempActions
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = tool.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isChecked) ScanProGreenContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            tempActions = if (checked) {
                                                if (tempActions.size < 4) tempActions + tool else tempActions
                                            } else {
                                                if (tempActions.size > 1) tempActions - tool else tempActions
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = ScanProGreenContainer)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setHomeQuickActions(tempActions.toList())
                        showHomeQuickActionsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScanProGreenContainer)
                ) {
                    Text("Apply (${tempActions.size}/4)", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHomeQuickActionsDialog = false }) {
                    Text("Cancel", color = ScanProGreenContainer)
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
                            "• Offline Multi-Language OCR: Bundled English and Bengali OCR work immediately offline with zero setup. Additional language packs can be downloaded once and then work 100% offline forever.\n\n" +
                            "• Encrypted Storage: Protected documents are safeguarded using AES-256 and standard PDF encryption.",
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
