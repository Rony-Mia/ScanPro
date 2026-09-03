package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ScanProViewModel
import com.example.model.DocumentItem
import com.example.ui.components.ScanLineDivider
import com.example.ui.theme.ScanProGreenContainer
import com.example.ui.theme.ScanProGreenPrimary
import com.example.util.rememberDocumentScannerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExtractedContact(
    val name: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
    val rawText: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessCardScannerScreen(
    viewModel: ScanProViewModel,
    onBack: () -> Unit,
    onDone: (DocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var cardUri by remember { mutableStateOf<String?>(null) }
    var isExtractingOcr by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var jobTitle by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var rawOcrText by remember { mutableStateOf("") }
    var showRawText by remember { mutableStateOf(false) }

    val launchScanner = rememberDocumentScannerLauncher(
        viewModel = viewModel,
        onPagesScanned = { uris ->
            if (uris.isNotEmpty()) {
                val uri = uris.first()
                cardUri = uri.toString()
                // Automatically run OCR & extraction
                isExtractingOcr = true
                coroutineScope.launch {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }
                        if (bitmap != null) {
                            val text = viewModel.recognizeTextFromBitmap(bitmap)
                            val extracted = parseBusinessCardText(text)
                            rawOcrText = text
                            name = extracted.name
                            jobTitle = extracted.jobTitle
                            company = extracted.company
                            phone = extracted.phone
                            email = extracted.email
                            website = extracted.website
                            address = extracted.address
                            viewModel.showToast("Contact details extracted!")
                        } else {
                            viewModel.showToast("Could not load scanned image")
                        }
                    } catch (e: Exception) {
                        viewModel.showToast("OCR error: ${e.localizedMessage}")
                    } finally {
                        isExtractingOcr = false
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Business Card Scanner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("business_card_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (cardUri != null) {
                        IconButton(
                            onClick = { launchScanner() },
                            modifier = Modifier.testTag("business_card_rescan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cameraswitch,
                                contentDescription = "Rescan",
                                tint = ScanProGreenPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (cardUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                    if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
                                    if (phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                                    if (email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                                    if (company.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, company)
                                    if (jobTitle.isNotBlank()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, jobTitle)
                                    if (address.isNotBlank()) putExtra(ContactsContract.Intents.Insert.POSTAL, address)
                                }
                                try {
                                    context.startActivity(intent)
                                    viewModel.showToast("Opening Contacts...")
                                } catch (e: Exception) {
                                    viewModel.showToast("No contacts app found: ${e.message}")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("business_card_save_contact_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ScanProGreenContainer,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save to Contacts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val uri = cardUri?.let { Uri.parse(it) }
                                if (uri != null) {
                                    val details = buildString {
                                        if (jobTitle.isNotBlank()) append("Role: $jobTitle\n")
                                        if (phone.isNotBlank()) append("Phone: $phone\n")
                                        if (email.isNotBlank()) append("Email: $email\n")
                                        if (website.isNotBlank()) append("Web: $website\n")
                                        if (address.isNotBlank()) append("Address: $address\n")
                                    }
                                    viewModel.saveBusinessCardDocument(
                                        imageUri = uri,
                                        name = name,
                                        company = company,
                                        details = details,
                                        onComplete = onDone
                                    )
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("business_card_save_doc_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = ScanProGreenPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creating Searchable PDF...")
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save as Searchable PDF Document")
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Preview Area or Empty Placeholder
            if (cardUri == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.75f) // standard business card aspect ratio
                        .clickable { launchScanner() }
                        .testTag("business_card_scan_placeholder")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(ScanProGreenContainer.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContactPage,
                                contentDescription = null,
                                tint = ScanProGreenContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scan Business Card",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Auto-extracts name, phone, email & company",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = { launchScanner() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("business_card_start_scan_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScanProGreenContainer,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Business Card", fontWeight = FontWeight.Bold)
                }
            } else {
                // Card preview thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.75f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .background(Color.Black.copy(alpha = 0.05f))
                ) {
                    AsyncImage(
                        model = cardUri,
                        contentDescription = "Card Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    if (isExtractingOcr) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Reading card information...",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                ScanLineDivider(opacity = 0.35f)

                Text(
                    text = "EXTRACTED INFORMATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Editable Contact Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_field_name")
                )

                OutlinedTextField(
                    value = jobTitle,
                    onValueChange = { jobTitle = it },
                    label = { Text("Job Title / Role") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Badge, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_field_job")
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / Organization") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Business, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_field_company")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Phone, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_field_phone")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Email, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_field_email")
                )

                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Language, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_field_website")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    leadingIcon = {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = ScanProGreenContainer)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_field_address")
                )

                // Raw OCR Text Expander
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRawText = !showRawText }
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Raw Scanned Text",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (showRawText) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showRawText) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (rawOcrText.isNotBlank()) rawOcrText else "No text recognized",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

/**
 * Heuristic parsing of OCR output for common business card fields.
 */
private fun parseBusinessCardText(text: String): ExtractedContact {
    if (text.isBlank()) return ExtractedContact()

    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

    // Email regex
    val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    val foundEmail = emailRegex.find(text)?.value ?: ""

    // Website regex
    val websiteRegex = Regex("(?:https?://)?(?:www\\.)?[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?")
    val foundWebsite = websiteRegex.findAll(text)
        .map { it.value }
        .firstOrNull { !it.contains("@") && (it.contains("www.") || it.contains("http") || it.endsWith(".com") || it.endsWith(".org") || it.endsWith(".net") || it.endsWith(".io")) }
        ?: ""

    // Phone regex
    val phoneRegex = Regex("(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")
    val foundPhone = phoneRegex.find(text)?.value ?: ""

    // Candidates for name, company, title from lines
    val filteredLines = lines.filter { line ->
        !line.contains(foundEmail) &&
        (foundPhone.isEmpty() || !line.contains(foundPhone)) &&
        (foundWebsite.isEmpty() || !line.contains(foundWebsite))
    }

    var extractedName = ""
    var extractedTitle = ""
    var extractedCompany = ""
    val addressParts = mutableListOf<String>()

    val jobKeywords = listOf("manager", "director", "engineer", "lead", "developer", "founder", "ceo", "cto", "cfo", "vp", "president", "consultant", "designer", "architect", "sales", "specialist")
    val companyKeywords = listOf("inc", "llc", "corp", "corporation", "ltd", "technologies", "tech", "company", "group", "studios", "solutions", "co.")

    filteredLines.forEachIndexed { idx, line ->
        val lower = line.lowercase()
        when {
            // Check company
            companyKeywords.any { lower.contains(it) } && extractedCompany.isEmpty() -> {
                extractedCompany = line
            }
            // Check title
            jobKeywords.any { lower.contains(it) } && extractedTitle.isEmpty() -> {
                extractedTitle = line
            }
            // First clean line that looks like a name (2-4 words, letters only)
            extractedName.isEmpty() && line.split(" ").size in 2..4 && line.all { it.isLetter() || it.isWhitespace() || it == '.' } -> {
                extractedName = line
            }
            // Check address
            lower.contains("street") || lower.contains("st.") || lower.contains("ave") || lower.contains("blvd") || lower.contains("road") || lower.contains("rd") || lower.contains("suite") || lower.contains("box") -> {
                addressParts.add(line)
            }
        }
    }

    if (extractedName.isEmpty() && filteredLines.isNotEmpty()) {
        extractedName = filteredLines.first()
    }

    return ExtractedContact(
        name = extractedName,
        jobTitle = extractedTitle,
        company = extractedCompany,
        phone = foundPhone,
        email = foundEmail,
        website = foundWebsite,
        address = addressParts.joinToString(", "),
        rawText = text
    )
}
