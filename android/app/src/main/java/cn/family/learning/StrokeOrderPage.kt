package cn.family.learning

import android.content.res.AssetManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.GZIPInputStream

private data class Stroke(val outline: Path, val start: Offset)
private data class StrokeLookup(val strokes: List<Stroke> = emptyList(), val message: String = "")

private fun loadStrokes(assets: AssetManager, character: String): List<Stroke> {
    val group = character.codePointAt(0) ushr 6
    val data = try {
        // Android's packager expands assets ending in .gz and changes their names.
        assets.open("strokes/$group.json.gzip").use { raw ->
            GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        }
    } catch (_: FileNotFoundException) {
        return emptyList()
    }
    val record = data.optJSONArray(character) ?: return emptyList()
    val outlines = record.getJSONArray(0)
    val medians = record.getJSONArray(1)
    return List(outlines.length()) { index ->
        val start = medians.getJSONArray(index).getJSONArray(0)
        Stroke(
            PathParser().parsePathString(outlines.getString(index)).toPath(),
            Offset(start.getDouble(0).toFloat(), start.getDouble(1).toFloat())
        )
    }
}

@Composable
internal fun StrokeOrderPage() {
    var input by rememberSaveable { mutableStateOf("") }
    var character by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val search = {
        val query = input.trim()
        if (query.codePointCount(0, query.length) == 1 &&
            Character.UnicodeScript.of(query.codePointAt(0)) == Character.UnicodeScript.HAN
        ) {
            character = query
            error = ""
            focus.clearFocus()
        } else {
            character = ""
            error = "请输入一个汉字"
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("输入一个汉字，按顺序看每一笔。", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it.take(16); error = "" },
                label = { Text("汉字") }, placeholder = { Text("例如 春") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = search) { Text("查询") }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        if (character.isNotEmpty()) key(character) { StrokeDemonstration(character) }
        Text("笔顺数据来自 Make Me a Hanzi，可离线查询。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StrokeDemonstration(character: String) {
    val assets = LocalContext.current.assets
    val lookup by produceState<StrokeLookup?>(null, character) {
        value = withContext(Dispatchers.IO) {
            try {
                val strokes = loadStrokes(assets, character)
                StrokeLookup(strokes, if (strokes.isEmpty()) "离线笔顺库暂无“$character”" else "")
            } catch (_: IOException) {
                StrokeLookup(message = "读取笔顺数据失败")
            } catch (_: org.json.JSONException) {
                StrokeLookup(message = "笔顺数据格式错误")
            } catch (_: IllegalArgumentException) {
                StrokeLookup(message = "笔顺图形读取失败")
            }
        }
    }
    val result = lookup
    if (result == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    if (result.strokes.isEmpty()) {
        Text(result.message)
        return
    }
    val strokes = result.strokes
    var step by remember { mutableIntStateOf(1) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing) {
            while (step < strokes.size) {
                delay(540)
                step++
            }
            playing = false
        }
    }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val faint = MaterialTheme.colorScheme.surfaceVariant
    val drawn = MaterialTheme.colorScheme.onSurface
    val current = MaterialTheme.colorScheme.primary
    val label = "“$character”第 $step 笔，共 ${strokes.size} 笔"
    Text(label, style = MaterialTheme.typography.titleMedium)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp)
                .semantics { contentDescription = label }
        ) {
            drawLine(grid, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
            drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            drawLine(grid, Offset.Zero, Offset(size.width, size.height))
            drawLine(grid, Offset(0f, size.height), Offset(size.width, 0f))
            val factor = size.width / 1024f
            withTransform({
                translate(0f, 900f * factor)
                scale(factor, -factor, pivot = Offset.Zero)
            }) {
                strokes.forEach { drawPath(it.outline, faint) }
                strokes.take(step).forEachIndexed { index, stroke ->
                    drawPath(stroke.outline, if (index == step - 1) current else drawn)
                }
            }
            val start = strokes[step - 1].start
            drawCircle(drawn, 3.dp.toPx(), Offset(start.x * factor, (900f - start.y) * factor))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { playing = false; step-- }, enabled = step > 1) { Text("上一笔") }
        Button(onClick = {
            if (playing) playing = false
            else { step = 1; playing = true }
        }) { Text(if (playing) "暂停" else "播放笔顺") }
        OutlinedButton(onClick = { playing = false; step++ }, enabled = step < strokes.size) { Text("下一笔") }
    }
    Text("蓝色为当前一笔，圆点标记起笔处。", style = MaterialTheme.typography.bodySmall)
}
