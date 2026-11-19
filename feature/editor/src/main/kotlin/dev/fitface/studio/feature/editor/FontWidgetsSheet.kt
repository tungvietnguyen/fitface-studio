package dev.fitface.studio.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fitface.studio.core.data.BundledSpriteFont
import dev.fitface.studio.core.data.FontSpriteRenderer
import dev.fitface.studio.core.data.FontSpriteSheet
import dev.fitface.studio.core.model.EditorSnapshot
import dev.fitface.studio.core.model.WidgetGuide
import dev.fitface.studio.core.ui.FitButton
import dev.fitface.studio.core.ui.FitButtonStyle
import dev.fitface.studio.core.ui.FitChip
import dev.fitface.studio.core.ui.FitHeading
import dev.fitface.studio.core.ui.FitIconButton
import dev.fitface.studio.core.ui.InfoText
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontWidgetsSheet(
    snapshot: EditorSnapshot,
    selected: WidgetGuide?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onAddSprite: (File) -> Unit,
    onSelectWidget: (Int?) -> Unit,
    onWidgetColor: (Int?) -> Unit,
    onApplyColorToAll: (Int) -> Unit,
    onResize: (Int) -> Unit,
    onPlaceWidget: (WidgetGuide, Int, Int) -> Unit,
    onNudgeWidget: (Int, Int, Int) -> Unit,
) {
    val context = LocalContext.current
    var bundledFontName by rememberSaveable { mutableStateOf(BundledSpriteFont.ORBITRON.name) }
    var importedFontPath by rememberSaveable { mutableStateOf<String?>(null) }
    var importedFontLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var importedFonts by remember { mutableStateOf<List<ImportedFont>>(emptyList()) }
    var fontError by rememberSaveable { mutableStateOf<String?>(null) }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var importingFont by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var sizePx by rememberSaveable { mutableIntStateOf(42) }
    var outlinePx by rememberSaveable { mutableIntStateOf(0) }
    var fillColor by rememberSaveable { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var fillOpacity by rememberSaveable { mutableIntStateOf(100) }
    var outlineColor by rememberSaveable { mutableIntStateOf(0xFF000000.toInt()) }
    var characterSpacingPx by rememberSaveable { mutableIntStateOf(0) }
    val spriteFillColor = (fillColor and 0x00FFFFFF) or ((fillOpacity * 255 / 100) shl 24)

    val scope = rememberCoroutineScope()
    LaunchedEffect(context.filesDir) {
        val savedFonts = withContext(Dispatchers.IO) { listImportedFonts(context) }
        importedFonts = (importedFonts + savedFonts).distinctBy { it.file.absolutePath }
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importingFont = true
            importError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { copyAndValidateFont(context, uri) }
                }
                result.onSuccess { (file, label) ->
                    importedFontPath = file.absolutePath
                    importedFontLabel = label
                    importedFonts = (importedFonts + ImportedFont(file, label))
                        .distinctBy { it.file.absolutePath }
                    fontError = null
                }.onFailure {
                    importError = it.message ?: context.getString(R.string.editor_fonts_import_failed)
                }
                importingFont = false
            }
        }
    }

    val bundledFont = BundledSpriteFont.entries.first { it.name == bundledFontName }
    val typeface = remember(context.assets, bundledFont, importedFontPath) {
        if (importedFontPath != null) {
            FontSpriteRenderer.fileTypeface(requireNotNull(importedFontPath))
        } else {
            FontSpriteRenderer.bundledTypeface(context.assets, bundledFont)
        }
    }
    var sheet by remember { mutableStateOf<FontSpriteSheet?>(null) }
    LaunchedEffect(typeface, sizePx, outlinePx, outlineColor, characterSpacingPx, spriteFillColor) {
        sheet?.bitmap?.recycle()
        sheet = null
        val result = withContext(Dispatchers.Default) {
            runCatching {
                FontSpriteRenderer.render(
                    typeface,
                    sizePx,
                    spriteFillColor,
                    outlinePx.toFloat(),
                    outlineColor,
                    characterSpacingPx,
                )
            }
        }
        result.onSuccess {
            sheet = it
            fontError = null
        }.onFailure {
            fontError = it.message ?: context.getString(R.string.editor_fonts_render_failed)
        }
    }
    DisposableEffect(Unit) { onDispose { sheet?.bitmap?.recycle() } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FitHeading(stringResource(R.string.editor_fonts_widgets_title)) {
                InfoText(stringResource(R.string.editor_fonts_widgets_info))
            }
            Text(
                stringResource(R.string.editor_fonts_static_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snapshot.widgets.isNotEmpty()) {
                FitHeading(stringResource(R.string.editor_fonts_layers))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    snapshot.widgets.forEach { widget ->
                        FitChip(
                            text = stringResource(R.string.editor_fonts_layer, widget.globalIndex + 1),
                            selected = widget.globalIndex == selected?.globalIndex,
                            onClick = { onSelectWidget(widget.globalIndex) },
                            enabled = enabled,
                        )
                    }
                }
            }
            TabRow(selectedTabIndex = selectedTab) {
                val labels = listOf(
                    R.string.editor_fonts_tab_font,
                    R.string.editor_fonts_tab_color,
                    R.string.editor_fonts_tab_resize,
                )
                labels.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(label)) },
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    FitHeading(stringResource(R.string.editor_fonts_picker))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BundledSpriteFont.entries.forEach { font ->
                            FitChip(
                                text = font.label,
                                selected = importedFontPath == null && bundledFontName == font.name,
                                onClick = {
                                    bundledFontName = font.name
                                    importedFontPath = null
                                    importedFontLabel = null
                                    importError = null
                                    fontError = null
                                },
                                enabled = enabled,
                            )
                        }
                        importedFonts.forEach { font ->
                            FitChip(
                                text = font.label,
                                selected = importedFontPath == font.file.absolutePath,
                                onClick = {
                                    importedFontPath = font.file.absolutePath
                                    importedFontLabel = font.label
                                    importError = null
                                    fontError = null
                                },
                                enabled = enabled,
                            )
                        }
                    }
                    importedFontLabel?.let { Text(stringResource(R.string.editor_fonts_selected, it)) }
                    FitButton(
                        stringResource(if (importingFont) R.string.editor_fonts_importing else R.string.editor_fonts_import),
                        onClick = { fontPicker.launch(arrayOf("*/*")) },
                        enabled = enabled && !importingFont,
                        style = FitButtonStyle.Secondary,
                    )
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    ValueSlider(
                        label = stringResource(R.string.editor_fonts_size),
                        value = sizePx.toFloat(),
                        range = FontSpriteRenderer.MIN_FONT_SIZE_PX.toFloat()..FontSpriteRenderer.MAX_FONT_SIZE_PX.toFloat(),
                        valueLabel = stringResource(R.string.editor_fonts_size_value, sizePx),
                        enabled = enabled,
                        onChange = { sizePx = it.roundToInt() },
                    )
                    ValueSlider(
                        label = stringResource(R.string.editor_fonts_outline),
                        value = outlinePx.toFloat(),
                        range = 0f..FontSpriteRenderer.MAX_OUTLINE_PX,
                        valueLabel = stringResource(R.string.editor_fonts_outline_value, outlinePx),
                        enabled = enabled,
                        onChange = { outlinePx = it.roundToInt() },
                    )
                    ValueSlider(
                        label = stringResource(R.string.editor_fonts_spacing),
                        value = characterSpacingPx.toFloat(),
                        range = 0f..FontSpriteRenderer.MAX_CHARACTER_SPACING_PX.toFloat(),
                        valueLabel = stringResource(R.string.editor_fonts_spacing_value, characterSpacingPx),
                        enabled = enabled,
                        onChange = { characterSpacingPx = it.roundToInt() },
                    )
                    sheet?.let { rendered ->
                        Image(
                            bitmap = rendered.bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.editor_fonts_preview_a11y),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        )
                    }
                    fontError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    FitButton(
                        stringResource(R.string.editor_fonts_add_sprite),
                        onClick = {
                            val bitmap = sheet?.bitmap ?: return@FitButton
                            val file = File.createTempFile("fitface-font-sprite-", ".png", context.cacheDir)
                            try {
                                file.outputStream().use { output ->
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                        context.getString(R.string.editor_fonts_save_failed)
                                    }
                                }
                                onAddSprite(file)
                                onDismiss()
                            } catch (error: Exception) {
                                file.delete()
                                fontError = error.message ?: context.getString(R.string.editor_fonts_save_failed)
                            }
                        },
                        enabled = enabled && sheet != null,
                    )
                    Text(
                        stringResource(R.string.editor_fonts_add_sprite_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                1 -> {
                    ColourPicker(
                        selected = fillColor,
                        original = false,
                        enabled = enabled,
                        commits = true,
                        onColour = { fillColor = it ?: 0xFFFFFFFF.toInt() },
                        stateKey = "font-sprite-fill",
                        heading = R.string.editor_fonts_fill_color,
                        showOriginal = false,
                        info = { InfoText(stringResource(R.string.editor_fonts_color_info)) },
                    )
                    RgbColorSliders(
                        color = fillColor,
                        enabled = enabled,
                        onColor = { fillColor = it },
                    )
                    ValueSlider(
                        label = stringResource(R.string.editor_fonts_opacity),
                        value = fillOpacity.toFloat(),
                        range = 0f..100f,
                        valueLabel = stringResource(R.string.editor_fonts_opacity_value, fillOpacity),
                        enabled = enabled,
                        onChange = { fillOpacity = it.roundToInt() },
                    )
                    ColourPicker(
                        selected = outlineColor,
                        original = false,
                        enabled = enabled,
                        commits = true,
                        onColour = { outlineColor = it ?: 0xFF000000.toInt() },
                        stateKey = "font-sprite-outline",
                        heading = R.string.editor_fonts_outline_color,
                        showOriginal = false,
                    )
                    RgbColorSliders(
                        color = outlineColor,
                        enabled = enabled,
                        onColor = { outlineColor = it },
                    )
                    selected?.let { widget ->
                        if (widget.colorArgb == null) {
                            Text(
                                stringResource(R.string.editor_fonts_widget_color_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ColourPicker(
                                selected = widget.colorArgb,
                                original = widget.colorArgb == widget.originalColorArgb,
                                enabled = enabled,
                                commits = true,
                                onColour = onWidgetColor,
                                stateKey = widget.globalIndex,
                                heading = R.string.editor_fonts_widget_color,
                            )
                        }
                    }
                    FitButton(
                        stringResource(R.string.editor_fonts_apply_color_all),
                        onClick = { onApplyColorToAll(fillColor) },
                        enabled = enabled && snapshot.widgets.any {
                            it.colorArgb != null && it.colorArgb != fillColor
                        },
                        style = FitButtonStyle.Secondary,
                        info = {
                            InfoText(stringResource(R.string.editor_fonts_apply_color_scope, snapshot.selectedVariantLabel()))
                        },
                    )
                }
                else -> {
                    selected?.let { widget ->
                        val sizes = remember(
                            widget.globalIndex,
                            widget.originalWidth,
                            widget.originalHeight,
                            widget.resizeKind,
                            widget.rotationKind,
                            widget.rotationTenths,
                            snapshot.preview.width,
                            snapshot.preview.height,
                        ) {
                            widgetResizeOptions(widget, snapshot.preview.width, snapshot.preview.height)
                        }
                        if (widget.canResize && widget.canEditPosition && sizes.size >= 2) {
                            val startIndex = sizes.indices.minByOrNull { index ->
                                kotlin.math.abs(
                                    sizes[index].width.toLong() * sizes[index].height -
                                        widget.width.toLong() * widget.height,
                                )
                            } ?: 0
                            var selectedSize by rememberSaveable(widget.globalIndex, widget.width, widget.height) {
                                mutableIntStateOf(startIndex)
                            }
                            val safeIndex = selectedSize.coerceIn(sizes.indices)
                            val currentSize = sizes[safeIndex]
                            FitHeading(stringResource(R.string.editor_fonts_widget_size)) {
                                InfoText(stringResource(R.string.editor_fonts_widget_size_info))
                            }
                            ValueSlider(
                                label = stringResource(R.string.editor_fonts_scale),
                                value = safeIndex.toFloat(),
                                range = 0f..sizes.lastIndex.toFloat(),
                                valueLabel = stringResource(
                                    R.string.editor_fonts_scale_value,
                                    currentSize.percentOfOriginal,
                                ),
                                enabled = enabled,
                                steps = (sizes.size - 2).coerceAtLeast(0),
                                onChange = { selectedSize = it.roundToInt().coerceIn(sizes.indices) },
                                onChangeFinished = { onResize(sizes[selectedSize.coerceIn(sizes.indices)].percentOfOriginal) },
                            )
                            Text(
                                stringResource(
                                    R.string.editor_fonts_widget_dimensions,
                                    currentSize.width,
                                    currentSize.height,
                                ),
                            )
                        } else if (widget.canResize) {
                            Text(
                                stringResource(
                                    if (sizes.size == 1 && widget.canEditPosition) {
                                        R.string.editor_fonts_widget_size_fixed
                                    } else {
                                        R.string.editor_fonts_widget_size_unavailable
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (widget.canEditPosition) {
                            FitHeading(stringResource(R.string.editor_fonts_alignment)) {
                                InfoText(stringResource(R.string.editor_fonts_nudge_info))
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FitButton(
                                    stringResource(R.string.editor_fonts_center_horizontal),
                                    onClick = {
                                        val bounds = widget.visualBounds
                                        val left = snapshot.preview.width / 2 - bounds.width / 2 - bounds.left
                                        onPlaceWidget(widget, left, widget.drawTop)
                                    },
                                    enabled = enabled,
                                    style = FitButtonStyle.Secondary,
                                )
                                FitButton(
                                    stringResource(R.string.editor_fonts_center_vertical),
                                    onClick = {
                                        val bounds = widget.visualBounds
                                        val top = snapshot.preview.height / 2 - bounds.height / 2 - bounds.top
                                        onPlaceWidget(widget, widget.drawLeft, top)
                                    },
                                    enabled = enabled,
                                    style = FitButtonStyle.Secondary,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FitIconButton(
                                    glyph = "↑",
                                    contentDescription = stringResource(R.string.editor_fonts_nudge_up),
                                    onClick = { onNudgeWidget(widget.globalIndex, 0, -1) },
                                    enabled = enabled,
                                )
                                FitIconButton(
                                    glyph = "←",
                                    contentDescription = stringResource(R.string.editor_fonts_nudge_left),
                                    onClick = { onNudgeWidget(widget.globalIndex, -1, 0) },
                                    enabled = enabled,
                                )
                                FitIconButton(
                                    glyph = "→",
                                    contentDescription = stringResource(R.string.editor_fonts_nudge_right),
                                    onClick = { onNudgeWidget(widget.globalIndex, 1, 0) },
                                    enabled = enabled,
                                )
                                FitIconButton(
                                    glyph = "↓",
                                    contentDescription = stringResource(R.string.editor_fonts_nudge_down),
                                    onClick = { onNudgeWidget(widget.globalIndex, 0, 1) },
                                    enabled = enabled,
                                )
                            }
                        }
                    } ?: Text(
                        stringResource(R.string.editor_fonts_select_widget),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onChangeFinished,
        )
    }
}

@Composable
private fun RgbColorSliders(color: Int, enabled: Boolean, onColor: (Int) -> Unit) {
    listOf(
        Triple(R.string.editor_fonts_red, 16, color ushr 16 and 0xFF),
        Triple(R.string.editor_fonts_green, 8, color ushr 8 and 0xFF),
        Triple(R.string.editor_fonts_blue, 0, color and 0xFF),
    ).forEach { (label, shift, channel) ->
        ValueSlider(
            label = stringResource(label),
            value = channel.toFloat(),
            range = 0f..255f,
            valueLabel = channel.toString(),
            enabled = enabled,
            onChange = { value -> onColor(withColorChannel(color, shift, value.roundToInt())) },
        )
    }
}

private fun withColorChannel(color: Int, shift: Int, value: Int): Int {
    val mask = 0xFF shl shift
    return (color and mask.inv()) or ((value.coerceIn(0, 255) and 0xFF) shl shift)
}

private data class ImportedFont(val file: File, val label: String)

private fun listImportedFonts(context: Context): List<ImportedFont> =
    importedFontsDirectory(context).listFiles().orEmpty()
        .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
        .map { file -> ImportedFont(file, file.nameWithoutExtension.substringBefore("--")) }
        .sortedBy { it.label.lowercase() }

private fun importedFontsDirectory(context: Context): File =
    File(context.filesDir, IMPORTED_FONTS_DIRECTORY).apply {
        check(isDirectory || mkdirs()) { "Imported fonts could not be stored." }
    }

private fun copyAndValidateFont(context: Context, uri: Uri): Pair<File, String> {
    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException(context.getString(R.string.editor_fonts_name_unavailable))
    val extension = displayName.substringAfterLast('.', "").lowercase()
    require(extension == "ttf" || extension == "otf") {
        context.getString(R.string.editor_fonts_import_type)
    }
    val directory = importedFontsDirectory(context)
    val existing = directory.listFiles().orEmpty().filter {
        it.isFile && it.extension.lowercase() in setOf("ttf", "otf")
    }
    require(existing.size < MAX_IMPORTED_FONT_COUNT) {
        context.getString(R.string.editor_fonts_library_full)
    }
    val safeName = displayName.substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .replace("--", "_")
        .take(48)
        .ifBlank { "font" }
    val file = File(
        directory,
        "$safeName--${UUID.randomUUID().toString().replace("-", "")}.$extension",
    )
    check(file.createNewFile()) { context.getString(R.string.editor_fonts_import_failed) }
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException(context.getString(R.string.editor_fonts_import_failed))
        input.use { source -> file.outputStream().use { destination -> copyBounded(source, destination) } }
        require(file.length() > 0) { context.getString(R.string.editor_fonts_import_failed) }
        require(existing.sumOf { it.length() } + file.length() <= MAX_IMPORTED_FONT_LIBRARY_BYTES) {
            context.getString(R.string.editor_fonts_library_full)
        }
        Typeface.createFromFile(file.absolutePath)
        return file to displayName
    } catch (error: Exception) {
        file.delete()
        throw error
    }
}

private fun copyBounded(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return
        total += count
        require(total <= MAX_IMPORTED_FONT_BYTES) {
            "Font files must be ${MAX_IMPORTED_FONT_BYTES / (1024 * 1024)} MiB or smaller."
        }
        output.write(buffer, 0, count)
    }
}

private const val COPY_BUFFER_SIZE = 8 * 1024
private const val MAX_IMPORTED_FONT_BYTES = 10L * 1024 * 1024
private const val MAX_IMPORTED_FONT_COUNT = 50
private const val MAX_IMPORTED_FONT_LIBRARY_BYTES = 100L * 1024 * 1024
private const val IMPORTED_FONTS_DIRECTORY = "imported-fonts"
