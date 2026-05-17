package com.Zero23.countdown
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

import android.Manifest
import android.content.Intent
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.Zero23.countdown.ui.ColorPickerDialog
import com.Zero23.countdown.data.CountdownEvent
import com.Zero23.countdown.data.DataManager
import com.Zero23.countdown.notifications.NotificationHelper
import com.Zero23.countdown.ui.theme.CountDownTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.File
import java.io.FileOutputStream
import com.Zero23.countdown.data.BackupData
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val dataManager = remember { DataManager(context) }
            val themeMode by dataManager.themeMode.collectAsState(initial = 0)
            val themeColorHex by dataManager.themeColor.collectAsState(initial = null)
            
            val isDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            
            val customThemeColor = themeColorHex?.let { 
                try { Color(android.graphics.Color.parseColor(it)) } catch(e: Exception) { null }
            }

            CountDownTheme(darkTheme = isDarkTheme, customColor = customThemeColor) {
                val navController = rememberNavController()

                // Request notification permission on start
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    
                    if (intent?.action == "com.Zero23.countdown.ACTION_CREATE_EVENT") {
                        navController.navigate("add_edit")
                    }
                }
                
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        CountdownApp(navController, dataManager)
                    }
                    composable("settings") {
                        SettingsScreen(navController, dataManager)
                    }
                    composable("changelog") {
                        ChangelogScreen(navController)
                    }
                    composable(
                        "add_edit?eventId={eventId}",
                        arguments = listOf(navArgument("eventId") { 
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { backStackEntry ->
                        val eventId = backStackEntry.arguments?.getString("eventId")
                        AddEditScreen(navController, dataManager, eventId)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

var lastSyncTimeMillis by mutableLongStateOf(0L)
var globalTimeOffset by mutableLongStateOf(0L)
var isSyncingGlobal by mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownApp(navController: NavController, dataManager: DataManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rawEvents by dataManager.events.collectAsState(initial = emptyList())
    var sortAscending by remember { mutableStateOf(false) }
    var sortByCreationDate by remember { mutableStateOf(true) }
    
    val events = remember(rawEvents, sortAscending, sortByCreationDate) {
        val sorted = if (sortByCreationDate) {
            rawEvents.sortedBy { it.createdAt }
        } else {
            rawEvents.sortedBy { LocalDateTime.parse(it.targetDateTime) }
        }
        if (sortAscending) sorted else sorted.reversed()
    }
    
    var eventToDelete by remember { mutableStateOf<CountdownEvent?>(null) }
    var currentTick by remember { mutableStateOf(LocalDateTime.now()) }
    var isBackupMenuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val backup = dataManager.getAllData()
                    val resolver = context.contentResolver
                    val zipImages = mutableMapOf<String, String>()
                    
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        ZipOutputStream(os).use { zos ->
                            backup.events.forEach { event ->
                                event.backgroundImageUri?.let { uriStr ->
                                    if (!uriStr.startsWith("images/")) {
                                        val entryName = "images/${event.id}_bg"
                                        try {
                                            resolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                                                zos.putNextEntry(ZipEntry(entryName))
                                                input.copyTo(zos)
                                                zos.closeEntry()
                                                zipImages["${event.id}_bg"] = entryName
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }
                                event.widgetImageUri?.let { uriStr ->
                                    if (!uriStr.startsWith("images/")) {
                                        val entryName = "images/${event.id}_widget"
                                        try {
                                            resolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                                                zos.putNextEntry(ZipEntry(entryName))
                                                input.copyTo(zos)
                                                zos.closeEntry()
                                                zipImages["${event.id}_widget"] = entryName
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                            
                            val backupForZip = backup.copy(
                                events = backup.events.map { event ->
                                    event.copy(
                                        backgroundImageUri = zipImages["${event.id}_bg"] ?: event.backgroundImageUri,
                                        widgetImageUri = zipImages["${event.id}_widget"] ?: event.widgetImageUri
                                    )
                                }
                            )
                            
                            zos.putNextEntry(ZipEntry("backup.json"))
                            zos.write(Json.encodeToString(backupForZip).toByteArray())
                            zos.closeEntry()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: return@launch
                    
                    // Try Zip
                    try {
                        ZipInputStream(bytes.inputStream()).use { zis ->
                            var entry = zis.getNextEntry()
                            var backupJson: String? = null
                            val imageFiles = mutableMapOf<String, ByteArray>()
                            
                            while (entry != null) {
                                if (entry.name == "backup.json") {
                                    backupJson = zis.readBytes().decodeToString()
                                } else if (entry.name.startsWith("images/")) {
                                    imageFiles[entry.name] = zis.readBytes()
                                }
                                zis.closeEntry()
                                entry = zis.getNextEntry()
                            }
                            
                            if (backupJson != null) {
                                val backup = Json.decodeFromString<BackupData>(backupJson)
                                val imagesDir = File(context.filesDir, "imported_images").apply { mkdirs() }
                                
                                val restoredEvents = backup.events.map { event ->
                                    var bgUri = event.backgroundImageUri
                                    var widgetUri = event.widgetImageUri
                                    
                                    if (bgUri?.startsWith("images/") == true) {
                                        imageFiles[bgUri]?.let { data ->
                                            val file = File(imagesDir, "${event.id}_bg")
                                            file.writeBytes(data)
                                            bgUri = Uri.fromFile(file).toString()
                                        }
                                    }
                                    if (widgetUri?.startsWith("images/") == true) {
                                        imageFiles[widgetUri]?.let { data ->
                                            val file = File(imagesDir, "${event.id}_widget")
                                            file.writeBytes(data)
                                            widgetUri = Uri.fromFile(file).toString()
                                        }
                                    }
                                    event.copy(backgroundImageUri = bgUri, widgetImageUri = widgetUri)
                                }
                                
                                dataManager.restoreAllData(backup.copy(events = restoredEvents))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                        }
                    } catch (e: Exception) {}

                    // Fallback to JSON TXT
                    val json = bytes.decodeToString()
                    dataManager.restoreAllDataFromJson(json)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTick = LocalDateTime.now().plusSeconds(globalTimeOffset)
            delay(1000)
        }
    }

    val fetchTime = {
        isSyncingGlobal = true
        scope.launch(Dispatchers.IO) {
            try {
                val connection = URL("https://www.microsoft.com").openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val dateHeader = connection.getHeaderField("Date")
                val networkNow = if (dateHeader != null) {
                    val httpFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
                    LocalDateTime.ofInstant(Instant.from(httpFormatter.parse(dateHeader)), ZoneId.systemDefault())
                } else {
                    val response = URL("https://worldtimeapi.org/api/timezone/Etc/UTC").readText()
                    val json = JSONObject(response)
                    val datetime = json.getString("datetime")
                    Instant.parse(datetime).atZone(ZoneId.systemDefault()).toLocalDateTime()
                }
                
                withContext(Dispatchers.Main) {
                    globalTimeOffset = Duration.between(LocalDateTime.now(), networkNow).seconds
                    lastSyncTimeMillis = System.currentTimeMillis()
                    isSyncingGlobal = false
                    // Update currentTick immediately after sync
                    currentTick = networkNow
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSyncingGlobal = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            fetchTime()
            delay(5 * 60 * 1000) // 每5分钟同步一次
        }
    }

    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isSyncingGlobal) stringResource(R.string.syncing) else "${stringResource(R.string.current_time)}${currentTick.format(formatter)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { isBackupMenuExpanded = true }) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = "Import/Export")
                        }
                        DropdownMenu(
                            expanded = isBackupMenuExpanded,
                            onDismissRequest = { isBackupMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_config)) },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = {
                                    isBackupMenuExpanded = false
                                    importLauncher.launch(arrayOf("text/plain", "application/zip"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_config)) },
                                leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                                onClick = {
                                    isBackupMenuExpanded = false
                                    exportLauncher.launch("countdown_backup.txt")
                                }
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_edit") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_card), tint = Color.White)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (rawEvents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.my_events),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.clickable { 
                            if (sortByCreationDate) {
                                if (!sortAscending) {
                                    sortByCreationDate = false
                                    sortAscending = true
                                } else {
                                    sortAscending = false
                                }
                            } else {
                                if (!sortAscending) {
                                    sortByCreationDate = true
                                    sortAscending = true
                                } else {
                                    sortAscending = false
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (sortByCreationDate) stringResource(R.string.sort_by_creation) else stringResource(R.string.sort_by_time),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.add_new_event), color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(items = events, key = { it.id }) { event ->
                        CountdownItem(
                            event = event,
                            now = currentTick,
                            onEdit = { navController.navigate("add_edit?eventId=${event.id}") },
                            onDelete = { eventToDelete = event }
                        )
                    }
                }
            }
        }

        if (eventToDelete != null) {
            AlertDialog(
                onDismissRequest = { eventToDelete = null },
                title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Text(stringResource(R.string.delete_confirm_msg, eventToDelete?.name ?: "")) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = eventToDelete?.id
                            val newList = events.filter { it.id != id }
                            scope.launch { 
                                dataManager.saveEvents(newList)
                                if (id != null) NotificationHelper.cancelNotification(context, id)
                            }
                            eventToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { eventToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, dataManager: DataManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by dataManager.themeMode.collectAsState(initial = 0)
    val themeColorHex by dataManager.themeColor.collectAsState(initial = null)
    val notificationsEnabled by dataManager.notificationsEnabled.collectAsState(initial = true)
    
    var showThemeColorPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium) },

                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.appearance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            var isThemeMenuExpanded by remember { mutableStateOf(false) }
            val themeOptions = listOf(
                stringResource(R.string.theme_follow_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.page_appearance)) },
                trailingContent = {
                    ExposedDropdownMenuBox(
                        expanded = isThemeMenuExpanded,
                        onExpandedChange = { isThemeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = themeOptions[themeMode],
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isThemeMenuExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).width(150.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = isThemeMenuExpanded,
                            onDismissRequest = { isThemeMenuExpanded = false }
                        ) {
                            themeOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        scope.launch { dataManager.setThemeMode(index) }
                                        isThemeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.app_theme_color)) },
                supportingContent = { 
                    Text(if (themeColorHex == null) stringResource(R.string.theme_follow_system) else themeColorHex!!) 
                },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                themeColorHex?.let { Color(android.graphics.Color.parseColor(it)) } 
                                ?: MaterialTheme.colorScheme.primary
                            )
                            .clickable { showThemeColorPicker = true }
                    )
                },
                modifier = Modifier.clickable { showThemeColorPicker = true }
            )

            HorizontalDivider()

            Text(
                stringResource(R.string.notifications),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.global_notification_switch)) },
                supportingContent = { Text(stringResource(R.string.notification_switch_desc)) },
                trailingContent = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { scope.launch { dataManager.setNotificationsEnabled(it) } }
                    )
                }
            )

            HorizontalDivider()

            // About Info moved to the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.my_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.version_text), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = { navController.navigate("changelog") }) {
                        Text(stringResource(R.string.changelog), textDecoration = TextDecoration.Underline)
                    }
                }

                Text(
                    text = stringResource(R.string.app_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Text(
                    text = stringResource(R.string.made_by),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    text = "ZErO23_FeedBack@outlook.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:ZErO23_FeedBack@outlook.com")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        if (showThemeColorPicker) {
            ColorPickerDialog(
                initialColorHex = themeColorHex,
                showFollowSystem = true,
                onDismiss = { showThemeColorPicker = false },
                onColorSelected = { 
                    scope.launch { dataManager.setThemeColor(it) }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(navController: NavController) {
    val context = LocalContext.current
    val changelogText = remember {
        try {
            context.assets.open("changelog.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            context.getString(R.string.no_changelog)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changelog_full_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(text = changelogText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(navController: NavController, dataManager: DataManager, eventId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val events by dataManager.events.collectAsState(initial = emptyList())
    val globalNotificationsEnabled by dataManager.notificationsEnabled.collectAsState(initial = true)
    
    val initialEvent = remember(eventId, events) {
        events.find { it.id == eventId }
    }

    var name by remember(initialEvent) { mutableStateOf(initialEvent?.name ?: "") }
    var selectedDate by remember(initialEvent) { 
        mutableStateOf(
            if (initialEvent != null) LocalDate.parse(initialEvent.targetDateTime.split("T")[0])
            else LocalDate.now().plusDays(1)
        ) 
    }
    var selectedTime by remember(initialEvent) { 
        mutableStateOf(
            if (initialEvent != null) LocalTime.parse(initialEvent.targetDateTime.split("T")[1].substring(0, 5))
            else LocalTime.of(0, 0)
        )
    }
    var selectedColorHex by remember(initialEvent) { mutableStateOf(initialEvent?.colorHex) }
    
    var notificationContent by remember(initialEvent) { mutableStateOf(initialEvent?.notificationContent ?: "") }
    var reminderMinutes by remember(initialEvent) { mutableStateOf(initialEvent?.reminderMinutesBefore ?: -1) }
    var repeatType by remember(initialEvent) { mutableStateOf(initialEvent?.repeatType ?: "none") }
    var repeatInterval by remember(initialEvent) { mutableStateOf(initialEvent?.repeatInterval?.toString() ?: "1") }
    var repeatUnit by remember(initialEvent) { mutableStateOf(initialEvent?.repeatUnit ?: "days") }
    var isRepeatMenuExpanded by remember { mutableStateOf(false) }
    var isRepeatUnitMenuExpanded by remember { mutableStateOf(false) }

    val repeatOptions = listOf(
        "none" to stringResource(R.string.repeat_none),
        "daily" to stringResource(R.string.repeat_daily),
        "weekly" to stringResource(R.string.repeat_weekly),
        "monthly" to stringResource(R.string.repeat_monthly),
        "yearly" to stringResource(R.string.repeat_yearly),
        "custom" to stringResource(R.string.repeat_custom)
    )

    val repeatUnits = listOf(
        "seconds" to stringResource(R.string.unit_seconds),
        "minutes" to stringResource(R.string.unit_minutes),
        "hours" to stringResource(R.string.unit_hours),
        "days" to stringResource(R.string.unit_days),
        "weeks" to stringResource(R.string.unit_weeks),
        "months" to stringResource(R.string.unit_months),
        "years" to stringResource(R.string.unit_years)
    )
    var isReminderMenuExpanded by remember { mutableStateOf(false) }

    val reminderOptions = listOf(
        -1 to stringResource(R.string.no_reminder),
        0 to stringResource(R.string.remind_on_time),
        5 to stringResource(R.string.remind_5m),
        10 to stringResource(R.string.remind_10m),
        30 to stringResource(R.string.remind_30m),
        60 to stringResource(R.string.remind_1h),
        1440 to stringResource(R.string.remind_1d)
    )

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var backgroundImageUri by remember(initialEvent) { mutableStateOf(initialEvent?.backgroundImageUri) }
    var widgetImageUri by remember(initialEvent) { mutableStateOf(initialEvent?.widgetImageUri) }
    var backgroundBrightness by remember(initialEvent) { mutableStateOf(initialEvent?.backgroundBrightness ?: 0.5f) }
    
    var originalImageUri by remember { mutableStateOf<Uri?>(null) }

    val widgetCropLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract(),
        onResult = { result ->
            if (result.isSuccessful) {
                widgetImageUri = result.uriContent.toString()
            }
        }
    )

    val cardCropLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract(),
        onResult = { result ->
            if (result.isSuccessful) {
                backgroundImageUri = result.uriContent.toString()
                // Start second crop for widget
                originalImageUri?.let { uri ->
                    val cropOptions = CropImageContractOptions(
                        uri = uri,
                        cropImageOptions = CropImageOptions(
                            guidelines = CropImageView.Guidelines.ON,
                            aspectRatioX = 1,
                            aspectRatioY = 1,
                            fixAspectRatio = true
                        )
                    )
                    widgetCropLauncher.launch(cropOptions)
                }
            }
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                originalImageUri = it
                val cropOptions = CropImageContractOptions(
                    uri = it,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON,
                        aspectRatioX = 2,
                        aspectRatioY = 1,
                        fixAspectRatio = true
                    )
                )
                cardCropLauncher.launch(cropOptions)
            }
        }
    )
    
    var isReminderExpanded by remember { mutableStateOf(false) }
    var isRepeatExpanded by remember { mutableStateOf(false) }

    // Live preview tick
    var previewNow by remember { mutableStateOf(LocalDateTime.now().plusSeconds(globalTimeOffset)) }
    LaunchedEffect(Unit) {
        while (true) {
            previewNow = LocalDateTime.now().plusSeconds(globalTimeOffset)
            delay(1000)
        }
    }

    // Notification permission check
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val onSave = {
        if (name.isNotBlank()) {
            val newEvent = CountdownEvent(
                id = initialEvent?.id ?: UUID.randomUUID().toString(),
                name = name,
                targetDateTime = LocalDateTime.of(selectedDate, selectedTime).toString(),
                colorHex = selectedColorHex,
                notificationContent = notificationContent.ifBlank { null },
                reminderMinutesBefore = if (reminderMinutes == -1) null else reminderMinutes,
                repeatType = repeatType,
                repeatInterval = if (repeatType == "custom") repeatInterval.toIntOrNull() ?: 1 else null,
                repeatUnit = if (repeatType == "custom") repeatUnit else null,
                backgroundImageUri = backgroundImageUri,
                widgetImageUri = widgetImageUri,
                backgroundBrightness = backgroundBrightness,
                createdAt = initialEvent?.createdAt ?: System.currentTimeMillis()
            )
            scope.launch {
                if (initialEvent != null) {
                    dataManager.saveEvents(events.map { if (it.id == newEvent.id) newEvent else it })
                } else {
                    dataManager.saveEvents(events + newEvent)
                }
                if (globalNotificationsEnabled && hasNotificationPermission) {
                    NotificationHelper.scheduleNotification(context, newEvent)
                }
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialEvent == null) stringResource(R.string.create_card) else stringResource(R.string.edit_card), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onSave,
                containerColor = if (name.isNotBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save), tint = if (name.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Preview Card
            val previewEvent = CountdownEvent(
                id = "preview",
                name = if (name.isBlank()) stringResource(R.string.event_name) else name,
                targetDateTime = LocalDateTime.of(selectedDate, selectedTime).toString(),
                colorHex = selectedColorHex,
                backgroundImageUri = backgroundImageUri,
                widgetImageUri = widgetImageUri,
                backgroundBrightness = backgroundBrightness,
                createdAt = System.currentTimeMillis()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            CountdownItem(
                event = previewEvent,
                now = previewNow,
                showActions = false
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.event_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedDate.toString(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Surface(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedTime.toString(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Color Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showColorPicker = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.card_color), style = MaterialTheme.typography.bodyLarge)
                Surface(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { showColorPicker = true },
                    color = selectedColorHex?.let { Color(android.graphics.Color.parseColor(it)) } ?: MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Palette, 
                        null, 
                        modifier = Modifier.padding(8.dp), 
                        tint = selectedColorHex?.let { Color.White } ?: MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.background_image), style = MaterialTheme.typography.bodyLarge)
                if (backgroundImageUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.image_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { imagePickerLauncher.launch(arrayOf("image/*")) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = { 
                                backgroundImageUri = null
                                widgetImageUri = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    IconButton(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (backgroundImageUri != null) {
                Text(stringResource(R.string.background_brightness), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = backgroundBrightness,
                    onValueChange = { backgroundBrightness = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            
            // Notification Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isReminderExpanded = !isReminderExpanded }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.reminder_settings), style = MaterialTheme.typography.bodyLarge)
                Icon(
                    if (isReminderExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    null
                )
            }
            
            if (isReminderExpanded) {
                val isNotificationPartEnabled = globalNotificationsEnabled && hasNotificationPermission
                Column(modifier = Modifier.padding(bottom = 16.dp).alpha(if (isNotificationPartEnabled) 1f else 0.5f)) {
                    OutlinedTextField(
                        value = notificationContent,
                        onValueChange = { notificationContent = it },
                        label = { Text(stringResource(R.string.reminder_content)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.placeholder_title)) },
                        enabled = isNotificationPartEnabled,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = isReminderMenuExpanded && isNotificationPartEnabled,
                        onExpandedChange = { if (isNotificationPartEnabled) isReminderMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = reminderOptions.find { it.first == reminderMinutes }?.second ?: stringResource(R.string.no_reminder),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.reminder_time)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isReminderMenuExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                            enabled = isNotificationPartEnabled,
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = isReminderMenuExpanded,
                            onDismissRequest = { isReminderMenuExpanded = false }
                        ) {
                            reminderOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.second) },
                                    onClick = {
                                        reminderMinutes = option.first
                                        isReminderMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    if (!globalNotificationsEnabled) {
                        Text(
                            stringResource(R.string.notif_disabled_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (!hasNotificationPermission) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                stringResource(R.string.notif_permission_msg),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.go_to_settings),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = TextDecoration.Underline,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.clickable {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Repeat Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isRepeatExpanded = !isRepeatExpanded }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.repeat_settings), style = MaterialTheme.typography.bodyLarge)
                Icon(
                    if (isRepeatExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    null
                )
            }

            if (isRepeatExpanded) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = isRepeatMenuExpanded,
                        onExpandedChange = { isRepeatMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = repeatOptions.find { it.first == repeatType }?.second ?: stringResource(R.string.repeat_none),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.repeat_mode)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRepeatMenuExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = isRepeatMenuExpanded,
                            onDismissRequest = { isRepeatMenuExpanded = false }
                        ) {
                            repeatOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.second) },
                                    onClick = {
                                        repeatType = option.first
                                        isRepeatMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (repeatType == "custom") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = repeatInterval,
                                onValueChange = { if (it.all { char -> char.isDigit() }) repeatInterval = it },
                                label = { Text(stringResource(R.string.repeat_every)) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = isRepeatUnitMenuExpanded,
                                onExpandedChange = { isRepeatUnitMenuExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = repeatUnits.find { it.first == repeatUnit }?.second ?: stringResource(R.string.unit_days),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.repeat_unit)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRepeatUnitMenuExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = isRepeatUnitMenuExpanded,
                                    onDismissRequest = { isRepeatUnitMenuExpanded = false }
                                ) {
                                    repeatUnits.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.second) },
                                            onClick = {
                                                repeatUnit = option.first
                                                isRepeatUnitMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute
        )

        androidx.compose.ui.window.Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = {
                            selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColorHex = selectedColorHex ?: String.format("#%06X", (0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())),
            showFollowSystem = false,
            onDismiss = { showColorPicker = false },
            onColorSelected = { selectedColorHex = it }
        )
    }
}


@Composable
fun CountdownItem(
    event: CountdownEvent,
    now: LocalDateTime,
    showActions: Boolean = true,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    // Calculate the next occurrence if it's a recurring event and passed
    var target = LocalDateTime.parse(event.targetDateTime)
    if (event.repeatType != null && event.repeatType != "none" && target.isBefore(now)) {
        while (target.isBefore(now)) {
            target = when (event.repeatType) {
                "daily" -> target.plusDays(1)
                "weekly" -> target.plusWeeks(1)
                "monthly" -> target.plusMonths(1)
                "yearly" -> target.plusYears(1)
                "custom" -> {
                    val interval = event.repeatInterval?.toLong() ?: 1L
                    when (event.repeatUnit) {
                        "seconds" -> target.plusSeconds(interval)
                        "minutes" -> target.plusMinutes(interval)
                        "hours" -> target.plusHours(interval)
                        "days" -> target.plusDays(interval)
                        "weeks" -> target.plusWeeks(interval)
                        "months" -> target.plusMonths(interval)
                        "years" -> target.plusYears(interval)
                        else -> target.plusDays(interval)
                    }
                }
                else -> target
            }
        }
    }

    val duration = Duration.between(now, target)
    val isPast = duration.isNegative
    val absDuration = duration.abs()

    val days = absDuration.toDays()
    val hours = absDuration.toHours() % 24
    val minutes = absDuration.toMinutes() % 60
    val seconds = absDuration.seconds % 60

    val customColor = event.colorHex?.let { try { Color(android.graphics.Color.parseColor(it)) } catch(e:Exception) { null } }
    val isFuture = !isPast
    val baseColor = customColor ?: (if (isFuture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    
    // Solid background with rounded corners or Image
    val hasImage = event.backgroundImageUri != null
    val cardBgColor = if (hasImage) Color.Black else baseColor
    val titleColor = if (hasImage) (customColor ?: Color.White) else Color.White
    val numberColor = if (hasImage) (customColor ?: Color.White).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (event.backgroundImageUri != null) {
                AsyncImage(
                    model = event.backgroundImageUri,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 1f - event.backgroundBrightness))
                ) {}
            }
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth()
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor
                    )
                    Text(
                        text = "${stringResource(R.string.target)}${target.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = titleColor.copy(alpha = 0.7f)
                    )
                }
                if (showActions) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit_card), tint = titleColor.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = titleColor.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column {
                if (isPast) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val (value, unit) = when {
                            Math.abs(days) > 0 -> Math.abs(days).toString() to stringResource(R.string.unit_days)
                            Math.abs(hours) > 0 -> Math.abs(hours).toString() to stringResource(R.string.unit_hours)
                            else -> Math.abs(minutes).toString() to stringResource(R.string.unit_minutes)
                        }
                        Text(
                            text = value,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            color = numberColor,
                            lineHeight = 56.sp
                        )
                        Text(
                            text = unit + stringResource(R.string.ago_suffix),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = numberColor,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                    }
                } else {
                    // Combined single row for future events
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Days
                        Text(
                            text = days.toString(),
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            color = numberColor,
                            lineHeight = 56.sp
                        )
                        Text(
                            text = stringResource(R.string.unit_days),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = numberColor,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        // Hours and Minutes
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp) // Align slightly better with the large text
                        ) {
                            Text(
                                text = hours.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = numberColor
                            )
                            Text(
                                text = stringResource(R.string.unit_hours),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = numberColor,
                                modifier = Modifier.padding(start = 2.dp, end = 8.dp)
                            )
                            Text(
                                text = minutes.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = numberColor
                            )
                            Text(
                                text = stringResource(R.string.unit_minutes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = numberColor,
                                modifier = Modifier.padding(start = 2.dp, end = 8.dp)
                            )
                            if (days == 0L && hours == 0L) {
                                Text(
                                    text = seconds.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = numberColor
                                )
                                Text(
                                    text = stringResource(R.string.unit_seconds),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = numberColor,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (event.reminderMinutesBefore != null || (event.repeatType != null && event.repeatType != "none")) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (event.reminderMinutesBefore != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                null,
                                tint = titleColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = " ${stringResource(R.string.reminder_enabled)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = titleColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (event.repeatType != null && event.repeatType != "none") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Repeat,
                                null,
                                tint = titleColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = " ${stringResource(R.string.repeat_enabled)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = titleColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
}













