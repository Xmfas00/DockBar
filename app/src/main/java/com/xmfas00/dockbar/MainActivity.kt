package com.xmfas00.dockbar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.DataOutputStream
import java.util.Locale

data class AppStrings(
    val title: String,
    val settings: String,
    val tgGroup: String,
    val blur: String,
    val corner: String,
    val height: String,
    val widthFull: String,
    val width: String,
    val offset: String,
    val color: String,
    val alpha: String,
    val apply: String,
    val done: String
)

val RU = AppStrings(
    title = "Настройки Dock Bar",
    settings = "Настройки",
    tgGroup = "Телеграм група",
    blur = "Размытие",
    corner = "Скругление",
    height = "Высота",
    widthFull = "Ширина: во всю ширину",
    width = "Ширина",
    offset = "Смещение",
    color = "Цвет фона",
    alpha = "Прозрачность",
    apply = "Применить и перезапустить лаунчер",
    done = "Готово!"
)

val EN = AppStrings(
    title = "Dock Bar Settings",
    settings = "Settings",
    tgGroup = "Telegram group",
    blur = "Blur",
    corner = "Corner radius",
    height = "Height",
    widthFull = "Width: full screen",
    width = "Width",
    offset = "Offset",
    color = "Background color",
    alpha = "Opacity",
    apply = "Apply and restart launcher",
    done = "Done!"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = this@MainActivity
            val darkTheme = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                else -> if (darkTheme) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                DockBarScreen()
            }
        }
    }
}

@Composable
fun DockBarScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("dockbar", Context.MODE_PRIVATE)
    var isRu by remember { mutableStateOf(prefs.getBoolean("ru", Locale.getDefault().language == "ru")) }
    val s = if (isRu) RU else EN
    var menuOpen by remember { mutableStateOf(false) }

    var blurRadius by remember { mutableIntStateOf(60) }
    var cornerRadius by remember { mutableFloatStateOf(24f) }
    var dockHeight by remember { mutableIntStateOf(80) }
    var dockWidth by remember { mutableIntStateOf(0) }
    var verticalOffset by remember { mutableIntStateOf(0) }
    var alpha by remember { mutableIntStateOf(120) }
    var hue by remember { mutableFloatStateOf(210f) }
    var sat by remember { mutableFloatStateOf(0.4f) }
    var value by remember { mutableFloatStateOf(0.3f) }
    val bgColor = android.graphics.Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(s.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Text("⚙️", fontSize = 24.sp)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(s.tgGroup) },
                            onClick = {
                                menuOpen = false
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/xmfas_utb")))
                                } catch (e: Exception) { }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Русский") },
                            onClick = {
                                isRu = true
                                prefs.edit().putBoolean("ru", true).apply()
                                menuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = {
                                isRu = false
                                prefs.edit().putBoolean("ru", false).apply()
                                menuOpen = false
                            }
                        )
                    }
                }
            }

            SliderSection("${s.blur}: $blurRadius", blurRadius.toFloat(), { blurRadius = it.toInt() }, 0f..150f)
            SliderSection("${s.corner}: ${cornerRadius.toInt()}dp", cornerRadius, { cornerRadius = it }, 0f..60f)
            SliderSection("${s.height}: ${dockHeight}dp", dockHeight.toFloat(), { dockHeight = it.toInt() }, 40f..200f)
            SliderSection(if (dockWidth == 0) s.widthFull else "${s.width}: ${dockWidth}dp", dockWidth.toFloat(), { dockWidth = it.toInt() }, 0f..600f)
            SliderSection("${s.offset}: ${verticalOffset}dp", verticalOffset.toFloat(), { verticalOffset = it.toInt() }, -150f..150f)

            Text(s.color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Box(
                Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(bgColor))
            )
            SatValPad(hue, sat, value) { sv, v ->
                sat = sv
                value = v
            }
            HueBar(hue) { hue = it }
            SliderSection("${s.alpha}: $alpha", alpha.toFloat(), { alpha = it.toInt() }, 0f..255f)

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val json = """{"blurRadius":$blurRadius,"cornerRadius":$cornerRadius,"dockHeight":$dockHeight,"dockWidth":$dockWidth,"bgColor":$bgColor,"verticalOffset":$verticalOffset}"""
                    runAsRoot(context, "echo '$json' > /data/local/tmp/my_dock_config.json")
                    runAsRoot(context, "chmod 644 /data/local/tmp/my_dock_config.json")
                    runAsRoot(context, "am force-stop com.mi.android.globallauncher")
                    runAsRoot(context, "am force-stop com.miui.home")
                    Toast.makeText(context, s.done, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large
            ) { Text(s.apply, fontSize = 16.sp) }
        }
    }
}

@Composable
fun SatValPad(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val pure = Color(android.graphics.Color.HSVToColor(255, floatArrayOf(hue, 1f, 1f)))
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, pure)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val sv = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                    onChange(sv, v)
                }
            }
    ) {
        Box(
            Modifier
                .size(18.dp)
                .offset(x = maxWidth * sat - 9.dp, y = maxHeight * (1f - value) - 9.dp)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val h = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f
                    onChange(h)
                }
            }
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(36.dp)
                .offset(x = maxWidth * (hue / 360f) - 3.dp)
                .background(Color.White, RoundedCornerShape(3.dp))
        )
    }
}

@Composable
fun SliderSection(title: String, value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    Column {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

fun runAsRoot(context: Context, cmd: String) {
    try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$cmd\nexit\n")
        os.flush()
        process.waitFor()
    } catch (e: Exception) {
        Toast.makeText(context, "Root error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}