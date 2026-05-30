package com.Zero23.countdown
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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
                try { Color(it.toColorInt()) } catch(_: Exception) { null }
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
    val exportSuccessMsg = stringResource(R.string.export_success)
    val importSuccessMsg = stringResource(R.string.import_success)

    val rawEvents by dataManager.events.collectAsState(initial = emptyList())
    val sortAscendingPref by dataManager.sortAscending.collectAsState(initial = false)
    val sortByCreationDatePref by dataManager.sortByCreationDate.collectAsState(initial = true)
    val isGridViewPref by dataManager.isGridView.collectAsState(initial = false)

    val sortAscending = sortAscendingPref
    val sortByCreationDate = sortByCreationDatePref
    val isGridView = isGridViewPref
    var currentTick by remember { mutableStateOf(LocalDateTime.now()) }
    
    val events = remember(rawEvents, sortAscending, sortByCreationDate, currentTick) {
        val sorted = if (sortByCreationDate) {
            rawEvents.sortedBy { it.createdAt }
        } else {
            rawEvents.sortedBy { event ->
                val target = event.calculateTarget(currentTick)
                Duration.between(currentTick, target).abs().toMillis()
            }
        }
        if (sortAscending) sorted else sorted.reversed()
    }
    
    var eventToDelete by remember { mutableStateOf<CountdownEvent?>(null) }
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
                                            resolver.openInputStream(uriStr.toUri())?.use { input ->
                                                zos.putNextEntry(ZipEntry(entryName))
                                                input.copyTo(zos)
                                                zos.closeEntry()
                                                zipImages["${event.id}_bg"] = entryName
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                                event.widgetImageUri?.let { uriStr ->
                                    if (!uriStr.startsWith("images/")) {
                                        val entryName = "images/${event.id}_widget"
                                        try {
                                            resolver.openInputStream(uriStr.toUri())?.use { input ->
                                                zos.putNextEntry(ZipEntry(entryName))
                                                input.copyTo(zos)
                                                zos.closeEntry()
                                                zipImages["${event.id}_widget"] = entryName
                                            }
                                        } catch (_: Exception) {}
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
                        Toast.makeText(context, exportSuccessMsg, Toast.LENGTH_SHORT).show()
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
                            var entry = zis.nextEntry
                            var backupJson: String? = null
                            val imageFiles = mutableMapOf<String, ByteArray>()
                            
                            while (entry != null) {
                                if (entry.name == "backup.json") {
                                    backupJson = zis.readBytes().decodeToString()
                                } else if (entry.name.startsWith("images/")) {
                                    imageFiles[entry.name] = zis.readBytes()
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
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
                                    Toast.makeText(context, importSuccessMsg, Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                        }
                    } catch (_: Exception) {}

                    // Fallback to JSON TXT
                    val json = bytes.decodeToString()
                    dataManager.restoreAllDataFromJson(json)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, importSuccessMsg, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTick = LocalDateTime.now().plusSeconds(globalTimeOffset)
            delay(1000.milliseconds)
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
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isSyncingGlobal = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            fetchTime()
            delay(5.minutes) // 每5分钟同步一次
        }
    }

    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    Scaffold(
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
            // Custom Top Bar Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 20.sp)
                    Text(
                        text = if (isSyncingGlobal) stringResource(R.string.syncing) else "${stringResource(R.string.current_time)} ${currentTick.format(formatter)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        IconButton(
                            onClick = { isBackupMenuExpanded = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                .size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "Import/Export", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                    IconButton(
                        onClick = { navController.navigate("settings") },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            .size(48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (rawEvents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch Button
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
                            .width(88.dp)
                            .height(40.dp)
                            .clickable { scope.launch { dataManager.setIsGridView(!isGridView) } }
                            .padding(4.dp)
                    ) {
                        // Sliding white background
                        val isLeftSelected = !isGridView
                        val alignment = if (isLeftSelected) Alignment.CenterStart else Alignment.CenterEnd
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(40.dp)
                                .align(alignment)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        )

                        // Icons
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Icon: List View (two horizontal lines)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.wrapContentSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(15.dp)
                                            .height(5.dp)
                                            .background(Color.Black, RoundedCornerShape(1.5.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(15.dp)
                                            .height(5.dp)
                                            .background(Color.Black, RoundedCornerShape(1.5.dp))
                                    )
                                }
                            }
                            // Right Icon: Grid View (four squares)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.wrapContentSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 6.5.dp, height = 5.dp)
                                                .background(Color.Black, RoundedCornerShape(1.5.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(width = 6.5.dp, height = 5.dp)
                                                .background(Color.Black, RoundedCornerShape(1.5.dp))
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 6.5.dp, height = 5.dp)
                                                .background(Color.Black, RoundedCornerShape(1.5.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(width = 6.5.dp, height = 5.dp)
                                                .background(Color.Black, RoundedCornerShape(1.5.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.clickable { 
                            scope.launch {
                                if (sortByCreationDate) {
                                    if (!sortAscending) {
                                        dataManager.setSortByCreationDate(false)
                                        dataManager.setSortAscending(true)
                                    } else {
                                        dataManager.setSortAscending(false)
                                    }
                                } else {
                                    if (!sortAscending) {
                                        dataManager.setSortByCreationDate(true)
                                        dataManager.setSortAscending(true)
                                    } else {
                                        dataManager.setSortAscending(false)
                                    }
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
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(items = events, key = { it.id }) { event ->
                            SmallCountdownItem(
                                event = event,
                                now = currentTick,
                                onEdit = { navController.navigate("add_edit?eventId=${event.id}") },
                                onDelete = { eventToDelete = event }
                            )
                        }
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
        topBar = { }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Custom Top Bar Area (using same horizontal = 16.dp and vertical = 8.dp as CountdownApp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 20.sp
                    )
                }
                
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Setting card content wrapped with padding horizontal = 24.dp so they align beautifully
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Appearance Section
                Text(
                    text = stringResource(R.string.appearance),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                )

                // Theme Mode Card
                var isThemeMenuExpanded by remember { mutableStateOf(false) }
                val themeOptions = listOf(
                    stringResource(R.string.theme_follow_system),
                    stringResource(R.string.theme_light),
                    stringResource(R.string.theme_dark)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.page_appearance), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .clickable { isThemeMenuExpanded = true }
                    ) {
                        Text(
                            text = themeOptions[themeMode],
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        DropdownMenu(
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

                Spacer(modifier = Modifier.height(16.dp))

                // Theme Color Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                        .clickable { showThemeColorPicker = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.app_theme_color), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = if (themeColorHex == null) stringResource(R.string.theme_follow_system) else "${stringResource(R.string.custom_color)} ($themeColorHex)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                themeColorHex?.let { Color(it.toColorInt()) } ?: MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(12.dp)
                            )
                    )
                }

                // Notifications Section
                Text(
                    text = stringResource(R.string.notifications),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.global_notification_switch), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = stringResource(R.string.notification_switch_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { scope.launch { dataManager.setNotificationsEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }

                // About Section
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.my_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    text = stringResource(R.string.version_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.made_by),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable { navController.navigate("changelog") }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = stringResource(R.string.changelog_full_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "mailto:ZErO23_FeedBack@outlook.com".toUri()
                                }
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_email),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ZErO23_FeedBack@outlook.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
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
    val noChangelogMsg = stringResource(R.string.no_changelog)
    val changelogText = remember(noChangelogMsg) {
        try {
            context.assets.open("changelog.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            noChangelogMsg
        }
    }

    Scaffold(
        topBar = {}
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Custom Top Bar Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.changelog_full_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 20.sp
                    )
                }
                
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(text = changelogText, style = MaterialTheme.typography.bodyMedium)
            }
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
    var reminderMinutes by remember(initialEvent) { mutableIntStateOf(initialEvent?.reminderMinutesBefore ?: -1) }
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
    var backgroundBrightness by remember(initialEvent) { mutableFloatStateOf(initialEvent?.backgroundBrightness ?: 0.5f) }
    
    var cropOriginalUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                cropOriginalUri = it
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
            delay(1000.milliseconds)
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onSave,
                containerColor = if (name.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check, 
                    contentDescription = stringResource(R.string.save), 
                    tint = if (name.isNotBlank()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Custom Top Bar Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (initialEvent == null) stringResource(R.string.create_card) else stringResource(R.string.edit_card),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 20.sp
                    )
                }
                
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Preview Card
                val previewEvent = CountdownEvent(
                    id = "preview",
                    name = name.ifBlank { stringResource(R.string.event_name) },
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
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(selectedDate.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Surface(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(selectedTime.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Color Section Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .clickable { showColorPicker = true }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.card_color), 
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = selectedColorHex ?: String.format(LocalConfiguration.current.locales[0], "#%06X", (0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                selectedColorHex?.let { Color(it.toColorInt()) } ?: MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Background Image Section Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.background_image), 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (backgroundImageUri != null) {
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
                        } else {
                            IconButton(
                                onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                if (backgroundImageUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.background_brightness), 
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Slider(
                            value = backgroundBrightness,
                            onValueChange = { backgroundBrightness = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Notification Section Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .clickable { isReminderExpanded = !isReminderExpanded }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reminder_settings), 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = if (isReminderExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                if (isReminderExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val isNotificationPartEnabled = globalNotificationsEnabled && hasNotificationPermission
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                            .alpha(if (isNotificationPartEnabled) 1f else 0.5f)
                    ) {
                        OutlinedTextField(
                            value = notificationContent,
                            onValueChange = { notificationContent = it },
                            label = { Text(stringResource(R.string.reminder_content)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.placeholder_title)) },
                            enabled = isNotificationPartEnabled,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                disabledContainerColor = MaterialTheme.colorScheme.surface
                            )
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
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                                enabled = isNotificationPartEnabled,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surface
                                )
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

                Spacer(modifier = Modifier.height(16.dp))

                // Repeat Section Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .clickable { isRepeatExpanded = !isRepeatExpanded }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.repeat_settings), 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = if (isRepeatExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (isRepeatExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
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
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surface
                                )
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
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        disabledContainerColor = MaterialTheme.colorScheme.surface
                                    )
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
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            disabledContainerColor = MaterialTheme.colorScheme.surface
                                        )
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

    if (cropOriginalUri != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val cropContext = LocalContext.current
            var cropBitmap by remember(cropOriginalUri) { mutableStateOf<Bitmap?>(null) }
            
            LaunchedEffect(cropOriginalUri) {
                cropOriginalUri?.let { uri ->
                    try {
                        cropContext.contentResolver.openInputStream(uri)?.use { stream ->
                            cropBitmap = BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            var rotation by remember { mutableFloatStateOf(0f) }
            var isMirrored by remember { mutableStateOf(false) }
            var cropRatio by remember { mutableFloatStateOf(2f) } 
            
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var containerWidthState by remember { mutableFloatStateOf(0f) }
            var containerHeightState by remember { mutableFloatStateOf(0f) }

            val cropImagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    uri?.let {
                        cropOriginalUri = it
                        rotation = 0f
                        isMirrored = false
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Custom Top Bar Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.crop_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 20.sp
                        )
                    }
                    
                    IconButton(
                        onClick = { cropOriginalUri = null },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Crop Window Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    if (cropBitmap != null) {
                        val bitmapVal = cropBitmap!!
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(clip = true)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                                        offset += pan
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val containerWidthPx = constraints.maxWidth.toFloat()
                            val containerHeightPx = constraints.maxHeight.toFloat()
                            
                            SideEffect {
                                containerWidthState = containerWidthPx
                                containerHeightState = containerHeightPx
                            }
                            
                            val fitScale = remember(bitmapVal, containerWidthPx, containerHeightPx) {
                                kotlin.math.min(containerWidthPx / bitmapVal.width, containerHeightPx / bitmapVal.height)
                            }
                            val layoutImgW = bitmapVal.width * fitScale
                            val layoutImgH = bitmapVal.height * fitScale
                            
                            val density = LocalDensity.current
                            val cropWidthPx = containerWidthPx - with(density) { 64.dp.toPx() }
                            val cropHeightPx = if (cropRatio == 2f) cropWidthPx / 2f else cropWidthPx
                            
                            val left = (containerWidthPx - cropWidthPx) / 2f
                            val top = (containerHeightPx - cropHeightPx) / 2f

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bitmapVal.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(
                                            width = with(density) { layoutImgW.toDp() },
                                            height = with(density) { layoutImgH.toDp() }
                                        )
                                        .graphicsLayer(
                                            scaleX = scale * (if (isMirrored) -1f else 1f),
                                            scaleY = scale,
                                            rotationZ = rotation,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        )
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                                            offset += pan
                                        }
                                    }
                                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                    .drawWithContent {
                                        drawContent()
                                        
                                        drawRect(color = Color.Black.copy(alpha = 0.6f))
                                        
                                        drawRect(
                                            color = Color.Transparent,
                                            topLeft = Offset(left, top),
                                            size = Size(cropWidthPx, cropHeightPx),
                                            blendMode = BlendMode.Clear
                                        )
                                        
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                                            size = Size(cropWidthPx + 2.dp.toPx(), cropHeightPx + 2.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                            )
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                // Bottom Control Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                            .height(48.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )

                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.5f..5.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                                thumbColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .width(88.dp)
                                .height(38.dp)
                                .padding(3.dp)
                        ) {
                            val isLeftSelected = cropRatio == 2f
                            val alignment = if (isLeftSelected) Alignment.CenterStart else Alignment.CenterEnd
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(38.dp)
                                    .align(alignment)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            )

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.wrapContentSize(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(13.dp)
                                                .height(4.dp)
                                                .background(Color.Black, RoundedCornerShape(1.dp))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(13.dp)
                                                .height(4.dp)
                                                .background(Color.Black, RoundedCornerShape(1.dp))
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.wrapContentSize(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 5.5.dp, height = 4.dp)
                                                    .background(Color.Black, RoundedCornerShape(1.dp))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 5.5.dp, height = 4.dp)
                                                    .background(Color.Black, RoundedCornerShape(1.dp))
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 5.5.dp, height = 4.dp)
                                                    .background(Color.Black, RoundedCornerShape(1.dp))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 5.5.dp, height = 4.dp)
                                                    .background(Color.Black, RoundedCornerShape(1.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { cropImagePickerLauncher.launch(arrayOf("image/*")) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Reselect",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                        val currentDensity = LocalDensity.current.density
                        IconButton(
                            onClick = {
                                cropBitmap?.let { bitmapVal ->
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            // Calculate fit scale and crop window dimensions based on hoisted containerState sizes
                                            val fitScale = kotlin.math.min(containerWidthState / bitmapVal.width, containerHeightState / bitmapVal.height)
                                            val cropWidthPx = containerWidthState - 64 * currentDensity
                                            val cropHeightPx = if (cropRatio == 2f) cropWidthPx / 2f else cropWidthPx
                                            
                                            val left = (containerWidthState - cropWidthPx) / 2f
                                            val top = (containerHeightState - cropHeightPx) / 2f
                                            
                                            val targetW = if (cropRatio == 2f) 1200 else 800
                                            val targetH = if (cropRatio == 2f) 600 else 800
                                            val outScale = targetW.toFloat() / cropWidthPx
                                            
                                            val croppedBitmap = createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(croppedBitmap)
                                            
                                            val matrix = android.graphics.Matrix()
                                            
                                            // 1. Center the bitmap at (0,0)
                                            matrix.postTranslate(-bitmapVal.width / 2f, -bitmapVal.height / 2f)
                                            
                                            // 2. Apply mirroring
                                            if (isMirrored) {
                                                matrix.postScale(-1f, 1f)
                                            }
                                            
                                            // 3. Apply rotation
                                            matrix.postRotate(rotation)
                                            
                                            // 4. Apply scale (fitScale * scale)
                                            val totalScale = fitScale * scale
                                            matrix.postScale(totalScale, totalScale)
                                            
                                            // 5. Apply pan offset
                                            matrix.postTranslate(offset.x, offset.y)
                                            
                                            // 6. Translate to the center of the container
                                            matrix.postTranslate(containerWidthState / 2f, containerHeightState / 2f)
                                            
                                            // 7. Translate to (0,0) relative to crop window
                                            matrix.postTranslate(-left, -top)
                                            
                                            // 8. Scale to target output bitmap size
                                            matrix.postScale(outScale, outScale)
                                            
                                            val paint = android.graphics.Paint().apply {
                                                isFilterBitmap = true
                                                isAntiAlias = true
                                            }
                                            canvas.drawBitmap(bitmapVal, matrix, paint)
                                            
                                            val cacheFile = File(cropContext.cacheDir, "cropped_image_${System.currentTimeMillis()}.jpg")
                                            FileOutputStream(cacheFile).use { out ->
                                                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                            }
                                            val croppedUriStr = Uri.fromFile(cacheFile).toString()
                                            
                                            withContext(Dispatchers.Main) {
                                                if (cropRatio == 2f) {
                                                    backgroundImageUri = croppedUriStr
                                                    // First step (Big Card) done. Instantly switch to second step (Small Card / Widget)!
                                                    cropRatio = 1f
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                    rotation = 0f
                                                    isMirrored = false
                                                } else {
                                                    widgetImageUri = croppedUriStr
                                                    // Second step (Small Card / Widget) done. Close the crop screen overlay!
                                                    cropOriginalUri = null
                                                }
                                            }
                                        } catch (_: Exception) {
                                            // Handle or ignore
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                                .size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirm",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                }
            }
        }
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
    val target = event.calculateTarget(now)

    val duration = Duration.between(now, target)
    val isPast = duration.isNegative
    val absDuration = duration.abs()

    val days = absDuration.toDays()
    val hours = absDuration.toHours() % 24
    val minutes = absDuration.toMinutes() % 60
    val seconds = absDuration.seconds % 60

    val customColor = event.colorHex?.let { try { Color(it.toColorInt()) } catch(_:Exception) { null } }
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
                            kotlin.math.abs(days) > 0 -> kotlin.math.abs(days).toString() to stringResource(R.string.unit_days)
                            kotlin.math.abs(hours) > 0 -> kotlin.math.abs(hours).toString() to stringResource(R.string.unit_hours)
                            else -> kotlin.math.abs(minutes).toString() to stringResource(R.string.unit_minutes)
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

@Composable
fun SmallCountdownItem(
    event: CountdownEvent,
    now: LocalDateTime,
    showActions: Boolean = true,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val target = event.calculateTarget(now)

    val duration = Duration.between(now, target)
    val isPast = duration.isNegative
    val absDuration = duration.abs()

    val days = absDuration.toDays()
    val hours = absDuration.toHours() % 24
    val minutes = absDuration.toMinutes() % 60

    val customColor = event.colorHex?.let { try { Color(it.toColorInt()) } catch(_:Exception) { null } }
    val isFuture = !isPast
    val baseColor = customColor ?: (if (isFuture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    
    val hasImage = event.widgetImageUri != null
    val cardBgColor = if (hasImage) Color.Black else baseColor
    val titleColor = if (hasImage) (customColor ?: Color.White) else Color.White
    val numberColor = if (hasImage) (customColor ?: Color.White).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f)

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (event.widgetImageUri != null) {
                AsyncImage(
                    model = event.widgetImageUri,
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
                modifier = Modifier.padding(16.dp).fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.target),
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = target.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)),
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (showActions) {
                        Row {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit_card), modifier = Modifier.size(16.dp).clickable { onEdit() }, tint = titleColor.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), modifier = Modifier.size(16.dp).clickable { onDelete() }, tint = titleColor.copy(alpha = 0.6f))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isPast) {
                    val (value, unit) = when {
                        kotlin.math.abs(days) > 0 -> kotlin.math.abs(days).toString() to stringResource(R.string.unit_days)
                        kotlin.math.abs(hours) > 0 -> kotlin.math.abs(hours).toString() to stringResource(R.string.unit_hours)
                        else -> kotlin.math.abs(minutes).toString() to stringResource(R.string.unit_minutes)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = value,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = numberColor,
                            lineHeight = 32.sp
                        )
                        Text(
                            text = unit + stringResource(R.string.ago_suffix),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = numberColor,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }
                } else {
                    if (days > 0) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = days.toString(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = numberColor,
                                lineHeight = 36.sp
                            )
                            Text(
                                text = stringResource(R.string.unit_days),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = numberColor,
                                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = String.format(LocalConfiguration.current.locales[0], "%02d:%02d", hours, minutes),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = numberColor,
                            lineHeight = 36.sp
                        )
                    }
                }
            }
        }
    }
}















