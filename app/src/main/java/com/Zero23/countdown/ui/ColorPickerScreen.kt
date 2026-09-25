package com.Zero23.countdown.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import com.Zero23.countdown.R
import com.Zero23.countdown.data.DataManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ColorPickerScreen(
    navController: NavController,
    dataManager: DataManager,
    initialColorHex: String?,
    isDark: Boolean
) {
    val scope = rememberCoroutineScope()

    val appBgImage by dataManager.appBackgroundImage.collectAsState(initial = null)
    val appBgBrightness by dataManager.appBackgroundBrightness.collectAsState(initial = 0.5f)

    // Load initial color or default to theme primary
    val defaultColor = String.format("#%06X", (0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb()))
    val startColor = if (initialColorHex.isNullOrEmpty()) defaultColor else initialColorHex

    var selectedColor by remember { mutableStateOf(try { Color(startColor.toColorInt()) } catch(_: Exception) { Color.Blue }) }
    var savedColors by remember { mutableStateOf<List<String>>(emptyList()) }

    // Read saved/recent colors from DataManager
    LaunchedEffect(Unit) {
        dataManager.savedColors.collect {
            savedColors = it
        }
    }

    // Color conversion helper
    fun Color.toHex(): String {
        return String.format("%02X%02X%02X", (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    }

    // State for HSV
    val hsv = remember(selectedColor) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), arr)
        arr
    }

    var hue by remember(selectedColor) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(selectedColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(selectedColor) { mutableFloatStateOf(hsv[2]) }

    fun updateColorFromHsv() {
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        selectedColor = Color(colorInt)
    }

    // Text field values
    var hexInput by remember(selectedColor) { mutableStateOf(selectedColor.toHex()) }
    var rInput by remember(selectedColor) { mutableStateOf(((selectedColor.red * 255).toInt()).toString()) }
    var gInput by remember(selectedColor) { mutableStateOf(((selectedColor.green * 255).toInt()).toString()) }
    var bInput by remember(selectedColor) { mutableStateOf(((selectedColor.blue * 255).toInt()).toString()) }

    val presets = listOf(
        "#2196F3", "#F44336", "#4CAF50", "#FFEB3B", "#9C27B0",
        "#FF9800", "#795548", "#607D8B", "#000000", "#FFFFFF",
        "#88DD44", "#FFCCAA", "#99CCFF", "#FFAACC", "#99EEDD"
    )

    val bgColor = if (appBgImage != null) Color.Transparent else MaterialTheme.colorScheme.background
    
    // Use stable theme colors instead of dynamic preview accents
    val pickerAccent = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    val fieldBg = MaterialTheme.colorScheme.surface
    val onFieldColor = MaterialTheme.colorScheme.onSurface
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (appBgImage != null) {
            coil3.compose.AsyncImage(
                model = appBgImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
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

        Scaffold(
            containerColor = bgColor,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title Pill
                    Box(
                        modifier = Modifier
                            .background(pickerAccent, RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.choose_theme_color),
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            fontSize = 18.sp
                        )
                    }

                    // Back Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(pickerAccent)
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
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Confirm/Check Button
                    Box(
                        modifier = Modifier
                            .background(pickerAccent, RoundedCornerShape(16.dp))
                            .size(56.dp)
                            .clickable {
                                val finalHex = "#${selectedColor.toHex()}"
                                val isPreset = presets.any { it.equals(finalHex, ignoreCase = true) }
                                if (!isPreset) {
                                    scope.launch {
                                        dataManager.addSavedColor(finalHex)
                                    }
                                }
                                navController.previousBackStackEntry?.savedStateHandle?.set("selected_color", finalHex)
                                navController.popBackStack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.confirm),
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // 1. Preview
                Text(
                    text = stringResource(R.string.preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(selectedColor)
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Preset Colors (预设颜色)
                Text(
                    text = stringResource(R.string.preset_color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val spacing = 12.dp
                        val itemWidth = (maxWidth - spacing * 4) / 5
                        val chunks = presets.chunked(5)
                        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                            chunks.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    rowItems.forEach { colorHex ->
                                        val color = Color(colorHex.toColorInt())
                                        Box(
                                            modifier = Modifier
                                                .size(width = itemWidth, height = 30.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(color)
                                                .border(
                                                    width = if (colorHex.equals("#FFFFFF", ignoreCase = true) || (isDark && colorHex.equals("#000000", ignoreCase = true))) 1.dp else 0.dp,
                                                    color = if (colorHex.equals("#FFFFFF", ignoreCase = true) || (isDark && colorHex.equals("#000000", ignoreCase = true))) Color.Gray.copy(alpha = 0.5f) else Color.Transparent,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    selectedColor = color
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Recently used colors (最近使用的颜色)
                val filteredSavedColors = remember(savedColors) {
                    savedColors.filter { color ->
                        presets.none { it.equals(color, ignoreCase = true) }
                    }
                }
                if (filteredSavedColors.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.saved_colors),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val spacing = 12.dp
                            val itemWidth = (maxWidth - spacing * 4) / 5
                            
                            val chunks = remember(filteredSavedColors) { filteredSavedColors.chunked(5) }
                            
                            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                                chunks.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (rowItems.size == 5) Arrangement.SpaceBetween else Arrangement.spacedBy(spacing)
                                    ) {
                                        rowItems.forEach { colorHex ->
                                            val color = Color(colorHex.toColorInt())
                                            Box(
                                                modifier = Modifier
                                                    .size(width = itemWidth, height = 30.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(color)
                                                    .combinedClickable(
                                                        onClick = {
                                                            selectedColor = color
                                                        },
                                                        onLongClick = {
                                                            scope.launch {
                                                                dataManager.removeSavedColor(colorHex)
                                                            }
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 4. Color Palette (调色板)
                Text(
                    text = stringResource(R.string.palette),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(pickerAccent)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SV Selector (2D gradient box)
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            val widthPx = constraints.maxWidth.toFloat()
                            val heightPx = constraints.maxHeight.toFloat()

                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(hue) {
                                        fun handleTouch(offset: Offset) {
                                            saturation = (offset.x / widthPx).coerceIn(0f, 1f)
                                            value = (1f - (offset.y / heightPx)).coerceIn(0f, 1f)
                                            updateColorFromHsv()
                                        }
                                        detectTapGestures(onTap = { handleTouch(it) })
                                    }
                                    .pointerInput(hue) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val currentPos = Offset(
                                                x = (saturation * widthPx + dragAmount.x).coerceIn(0f, widthPx),
                                                y = ((1f - value) * heightPx + dragAmount.y).coerceIn(0f, heightPx)
                                            )
                                            saturation = (currentPos.x / widthPx).coerceIn(0f, 1f)
                                            value = (1f - (currentPos.y / heightPx)).coerceIn(0f, 1f)
                                            updateColorFromHsv()
                                        }
                                    }
                            ) {
                                // Draw base color with saturation gradient (white to pure color)
                                val baseColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.White, baseColor)
                                    )
                                )
                                // Draw black overlay (transparent to black top-to-bottom)
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black)
                                    )
                                )

                                // Draw selector circle
                                val selectorX = saturation * size.width
                                val selectorY = (1f - value) * size.height
                                drawCircle(
                                    color = Color.White,
                                    radius = 6.dp.toPx(),
                                    center = Offset(selectorX, selectorY),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Hue Selector (Vertical Slider)
                        BoxWithConstraints(
                            modifier = Modifier
                                .width(36.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            val heightPx = constraints.maxHeight.toFloat()

                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        fun handleTouch(offset: Offset) {
                                            hue = (offset.y / heightPx).coerceIn(0f, 1f) * 360f
                                            updateColorFromHsv()
                                        }
                                        detectTapGestures(onTap = { handleTouch(it) })
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val currentY = ((hue / 360f) * heightPx + dragAmount.y).coerceIn(0f, heightPx)
                                            hue = (currentY / heightPx) * 360f
                                            updateColorFromHsv()
                                        }
                                    }
                            ) {
                                // Hue colors array
                                val hueColors = (0..360).map { step ->
                                    Color(android.graphics.Color.HSVToColor(floatArrayOf(step.toFloat(), 1f, 1f)))
                                }
                                drawRect(
                                    brush = Brush.verticalGradient(colors = hueColors)
                                )

                                // Draw slider indicator triangles / bar
                                val indicatorY = (hue / 360f) * size.height
                                
                                // Let's draw horizontal lines or a small rectangle indicator
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(0f, indicatorY - 2.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx())
                                )
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(0f, indicatorY - 2.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        // HEX & RGB Inputs (Right side)
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // HEX Section
                            Column {
                                Text(
                                    text = stringResource(R.string.hex),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicTextField(
                                    value = hexInput,
                                    onValueChange = {
                                        val cleaned = it.take(6).uppercase()
                                        hexInput = cleaned
                                        if (cleaned.length == 6) {
                                            try {
                                                selectedColor = Color("#$cleaned".toColorInt())
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = onFieldColor),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(fieldBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }

                            // RGB Section
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.rgb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor,
                                    fontWeight = FontWeight.Bold
                                )
                                // R Field
                                BasicTextField(
                                    value = rInput,
                                    onValueChange = {
                                        rInput = it
                                        try {
                                            val r = it.toInt().coerceIn(0, 255)
                                            val currentG = (selectedColor.green * 255).toInt()
                                            val currentB = (selectedColor.blue * 255).toInt()
                                            selectedColor = Color(r, currentG, currentB)
                                        } catch (_: Exception) {}
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = onFieldColor),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(fieldBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                // G Field
                                BasicTextField(
                                    value = gInput,
                                    onValueChange = {
                                        gInput = it
                                        try {
                                            val currentR = (selectedColor.red * 255).toInt()
                                            val g = it.toInt().coerceIn(0, 255)
                                            val currentB = (selectedColor.blue * 255).toInt()
                                            selectedColor = Color(currentR, g, currentB)
                                        } catch (_: Exception) {}
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = onFieldColor),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(fieldBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                // B Field
                                BasicTextField(
                                    value = bInput,
                                    onValueChange = {
                                        bInput = it
                                        try {
                                            val currentR = (selectedColor.red * 255).toInt()
                                            val currentG = (selectedColor.green * 255).toInt()
                                            val b = it.toInt().coerceIn(0, 255)
                                            selectedColor = Color(currentR, currentG, b)
                                        } catch (_: Exception) {}
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = onFieldColor),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(fieldBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
