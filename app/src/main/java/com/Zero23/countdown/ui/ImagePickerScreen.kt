package com.Zero23.countdown.ui

import android.content.ContentUris
import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.Zero23.countdown.R
import com.Zero23.countdown.TOP_BAR_ACTION_CORNER
import com.Zero23.countdown.TOP_BAR_ACTION_OFFSET
import com.Zero23.countdown.TOP_BAR_CONTENT_HEIGHT
import com.Zero23.countdown.TOP_BAR_TITLE_CORNER
import com.Zero23.countdown.TOP_BAR_TITLE_FONT_SIZE
import com.Zero23.countdown.TOP_BAR_TITLE_LINE_HEIGHT
import com.Zero23.countdown.data.DataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Upper bound for the stacked (multi image) selection. */
private const val MaxSelectableImages = 9

@Composable
fun StackedCardsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(width * 0.2f, 0f),
            size = Size(width * 0.6f, height * 0.25f),
            cornerRadius = CornerRadius(width * 0.08f)
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.75f),
            topLeft = Offset(width * 0.1f, height * 0.15f),
            size = Size(width * 0.8f, height * 0.25f),
            cornerRadius = CornerRadius(width * 0.08f)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, height * 0.3f),
            size = Size(width, height * 0.7f),
            cornerRadius = CornerRadius(width * 0.12f)
        )
    }
}

@Composable
fun ImagePickerScreen(
    navController: NavController,
    dataManager: DataManager,
    initialAllowMulti: Boolean = false,
    onImagesSelected: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isMultiSelectEnabled by remember { mutableStateOf(false) }
    
    val themeMode by dataManager.themeMode.collectAsState(initial = 0)
    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    
    val appBgImage by dataManager.appBackgroundImage.collectAsState(initial = null)
    val appBgBrightness by dataManager.appBackgroundBrightness.collectAsState(initial = 0.5f)

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val filePickerSingle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onImagesSelected(listOf(uri))
            }
        }
    )

    // Hoisted here so the launcher callback (which is not a composable scope) never has to
    // read resources off LocalContext.current.
    val maxSelectionMsg = stringResource(R.string.max_selection_reached, MaxSelectableImages)

    val filePickerOpenMultiple = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                if (uris.size > MaxSelectableImages) {
                    Toast.makeText(
                        context,
                        maxSelectionMsg,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onImagesSelected(uris.take(MaxSelectableImages))
            }
        }
    )

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // The camera always captures a single image, so the photo is returned straight away.
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && cameraImageUri != null) {
                onImagesSelected(listOf(cameraImageUri!!))
            }
        }
    )

    val launchCamera = {
        try {
            val photoFile = File(
                context.cacheDir,
                "camera_photo_${System.currentTimeMillis()}.jpg"
            )
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraImageUri = uri

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }

            val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot launch camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchCamera()
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        } else {
            withContext(Dispatchers.IO) {
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                try {
                    val query = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortOrder
                    )
                    query?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val uris = mutableListOf<Uri>()
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            uris.add(contentUri)
                        }
                        images = uris
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val boxColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val selectionColor = MaterialTheme.colorScheme.primary

    // Height of the floating bottom bar (its margins and the navigation bar inset included),
    // measured at runtime so the grid can reserve exactly the room it needs. The previous fixed
    // padding was smaller than the real bar on most devices, so the last row slid underneath it.
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Scaffold(
        containerColor = if (appBgImage != null) Color.Transparent else MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (appBgImage != null) {
                AsyncImage(
                    model = appBgImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) Color.Black.copy(alpha = appBgBrightness)
                            else Color.White.copy(alpha = appBgBrightness)
                        )
                )
            }

            if (images.isEmpty() && hasPermission) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = "No images found", color = contentColor.copy(alpha = 0.5f))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        // Clears the floating top bar, which is TOP_BAR_CONTENT_HEIGHT of content
                        // plus the bar's own 8dp inset. The extra two offsets are the difference
                        // between the bar's old natural height and that fixed height.
                        top = 100.dp + TOP_BAR_ACTION_OFFSET * 2,
                        // Clears the floating bottom bar; until the bar has been measured the
                        // previous fixed value is used as a floor, so nothing ever gets tighter.
                        bottom = (bottomBarHeight + 16.dp).coerceAtLeast(120.dp),
                        start = 16.dp,
                        end = 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(images) { uri ->
                        val selectedIndex = selectedImages.indexOf(uri)
                        val isSelected = selectedIndex != -1
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) selectionColor else Color.Transparent)
                                .clickable {
                                    if (initialAllowMulti && isMultiSelectEnabled) {
                                        selectedImages = when {
                                            isSelected -> selectedImages - uri
                                            selectedImages.size >= MaxSelectableImages -> {
                                                Toast.makeText(context, maxSelectionMsg, Toast.LENGTH_SHORT).show()
                                                selectedImages
                                            }
                                            else -> selectedImages + uri
                                        }
                                    } else {
                                        onImagesSelected(listOf(uri))
                                    }
                                }
                                .padding(if (isSelected) 4.dp else 0.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (initialAllowMulti && isMultiSelectEnabled && isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .background(selectionColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${selectedIndex + 1}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    // Same fixed content height as the settings screen, so this title and its back
                    // button share one centre line with the rest of the app instead of depending on
                    // how tall the title pill happens to be.
                    .height(TOP_BAR_CONTENT_HEIGHT),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title Box
                Box(
                    modifier = Modifier
                        .background(boxColor, RoundedCornerShape(TOP_BAR_TITLE_CORNER))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.select_image),
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        fontSize = TOP_BAR_TITLE_FONT_SIZE,
                        lineHeight = TOP_BAR_TITLE_LINE_HEIGHT
                    )
                }

                // Back Button, or just the slot when the shared button takes this corner over: that
                // happens when the page was opened from the settings page, where the shell's button
                // turns from the arrow into the gear and stays on screen. Opened from the add/edit
                // form (picking a card's photos) it stays this page's own arrow.
                if (navController.previousBackStackEntry?.destination?.route?.substringBefore('?') == "settings") {
                    Spacer(modifier = Modifier.size(48.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(TOP_BAR_ACTION_CORNER))
                            .background(boxColor)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = contentColor
                        )
                    }
                }
            }

            // Floating Bottom Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    // Measured ahead of the paddings, so the reported height covers the whole bar:
                    // navigation bar inset, outer margins and content. The grid reads it back to
                    // keep its last row clear of the bar instead of guessing a fixed padding.
                    .onSizeChanged { barSize ->
                        bottomBarHeight = with(density) { barSize.height.toDp() }
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Capsule Bar containing File Manager, Camera, and (if initialAllowMulti) Stacked Cards Toggle
                Box(
                    modifier = Modifier
                        // Animates the capsule so it can stretch to the right when the counter shows up.
                        .animateContentSize()
                        .background(boxColor, RoundedCornerShape(24.dp))
                        .padding(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // File Manager Button
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 48.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    if (initialAllowMulti && isMultiSelectEnabled) {
                                        filePickerOpenMultiple.launch(arrayOf("image/*"))
                                    } else {
                                        filePickerSingle.launch(arrayOf("image/*"))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = "File Manager",
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Camera Button (always a single image, so it turns the stack mode off)
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 48.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    if (initialAllowMulti && isMultiSelectEnabled) {
                                        isMultiSelectEnabled = false
                                        selectedImages = emptyList()
                                    }
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        launchCamera()
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = stringResource(R.string.camera),
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Stacked Cards Multi-select Toggle (Only when initialAllowMulti is true)
                        if (initialAllowMulti) {
                            // White pill, matching the navigation pill of the add/edit screen. While the
                            // stack mode is on it stretches to the right to host the selected counter.
                            val pillColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White
                            val pillContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .widthIn(min = 56.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isMultiSelectEnabled) pillColor else Color.Transparent)
                                    .clickable {
                                        isMultiSelectEnabled = !isMultiSelectEnabled
                                        if (!isMultiSelectEnabled) {
                                            selectedImages = emptyList()
                                        }
                                    }
                                    .padding(horizontal = if (isMultiSelectEnabled) 12.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StackedCardsIcon(
                                        tint = if (isMultiSelectEnabled) pillContentColor else contentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (isMultiSelectEnabled) {
                                        Text(
                                            text = "${selectedImages.size}/$MaxSelectableImages",
                                            color = pillContentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Confirm Button: while the stack mode is off a thumbnail tap returns the single
                // image right away, so the button has nothing to do and stays greyed out.
                val isConfirmEnabled = initialAllowMulti && isMultiSelectEnabled && selectedImages.isNotEmpty()
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isConfirmEnabled) boxColor else boxColor.copy(alpha = 0.5f))
                        .clickable(enabled = isConfirmEnabled) {
                            if (initialAllowMulti && isMultiSelectEnabled) {
                                if (selectedImages.isNotEmpty()) {
                                    onImagesSelected(selectedImages)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.confirm),
                        tint = if (isConfirmEnabled) contentColor else contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
