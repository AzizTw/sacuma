package io.github.aziztw.sacuma.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aziztw.sacuma.AdapterState
import io.github.aziztw.sacuma.PadState
import io.github.aziztw.sacuma.Settings
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Everything the screen needs; MainActivity owns the state and the actions. */
data class UiState(
    val adapter: AdapterState.Snapshot,
    val settings: Settings,
    val message: String,  // shown when the driver isn't running (e.g. "Plug in the adapter")
)

class UiActions(
    val onDriverToggle: (Boolean) -> Unit,
    val onSettings: (Settings) -> Unit,
    val onRecalibrate: () -> Unit,
)

@Composable
fun SacumaScreen(state: UiState, actions: UiActions) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Gc.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val landscape = maxWidth > maxHeight
        // Phones in landscape are shorter than the Thor (~360 vs 468 dp): shrink a bit.
        val compact = landscape && maxHeight < 400.dp
        if (landscape) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
                Header(state, actions)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until 4) PortCard(i, state, Modifier.weight(1f).fillMaxHeight())
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SettingsItems(state, actions) { w -> if (w > 0f) Modifier.weight(w) else Modifier }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Header(state, actions)
                for (row in 0 until 2) {
                    Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (col in 0 until 2) PortCard(row * 2 + col, state, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsItems(state, actions) { Modifier.fillMaxWidth() }
                }
            }
        }
    }
}

// ---------- header ----------

@Composable
private fun Header(state: UiState, actions: UiActions) {
    val running = state.adapter.running
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Logo()
        Label("Sacuma", 21.sp, Gc.Text, FontWeight.Medium)
        val badge = when {
            running -> "Active · ${state.adapter.rateHz.roundToInt()} Hz"
            state.message.isNotEmpty() -> state.message
            else -> "Off"
        }
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (running) Gc.GoodBg else Gc.Well)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Label(badge, 13.sp, if (running) Gc.GoodText else Gc.TextMuted) }
        Spacer(Modifier.weight(1f))
        Label("Driver", 13.sp, Gc.TextMuted)
        Toggle(running, actions.onDriverToggle)
    }
}

@Composable
private fun Logo() {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Gc.Accent),
        contentAlignment = Alignment.Center,
    ) {
        // A small cube seen corner-on: a rotated rounded square outline.
        Canvas(Modifier.size(20.dp)) {
            val s = size.minDimension * 0.62f
            val c = center
            rotateSquare(c, s)
        }
    }
}

private fun DrawScope.rotateSquare(c: Offset, s: Float) {
    val h = s / 2 * 1.414f
    val p = Path().apply {
        moveTo(c.x, c.y - h); lineTo(c.x + h, c.y); lineTo(c.x, c.y + h); lineTo(c.x - h, c.y); close()
    }
    drawPath(p, Gc.Text, style = Stroke(width = 3.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val knob by animateDpAsState(if (on) 25.dp else 3.dp, label = "knob")
    val track by animateColorAsState(if (on) Gc.AOn else Gc.Well, label = "track")
    Box(
        Modifier
            .size(52.dp, 30.dp)
            .clip(CircleShape)
            .background(track)
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier.offset(x = knob, y = 3.dp).size(24.dp).clip(CircleShape)
                .background(if (on) Gc.Text else Gc.TextMuted),
        )
    }
}

// ---------- port cards ----------

@Composable
private fun PortCard(index: Int, state: UiState, modifier: Modifier) {
    val pad = state.adapter.pads[index]
    val connected = state.adapter.running && pad.connected
    if (!connected) {
        EmptyPort(index, modifier)
        return
    }
    val cal = state.adapter.calibration.copyOfRange(index * 6, index * 6 + 6)
    val hasCal = cal.any { it != 0 }
    val center = if (hasCal) cal else intArrayOf(pad.sx, pad.sy, pad.cx, pad.cy, pad.lt, pad.rt)
    val live = state.adapter.activePads and (1 shl index) != 0
    val dz = state.settings.stickDeadzone / 100f

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Gc.Card)
            .border(if (live) 1.5.dp else 0.dp, if (live) Gc.ActiveOutline else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("P${index + 1}", 24.sp, Gc.Text, FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            ZPill(pad.pressed(b2Bit = PadState.B2_Z))
            Spacer(Modifier.width(8.dp))
            Label(if (pad.type == 2) "Wireless" else "Wired", 13.sp, Gc.GoodText)
        }
        // Laid out like the controller: main stick top-left, face buttons top-right,
        // D-pad bottom-left, C-stick bottom-right. Everything scales with the card.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val col = (maxWidth - 8.dp) / 2
            val top = maxHeight * 0.54f
            val bottom = maxHeight - top
            Column {
                Row(Modifier.height(top), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(col), contentAlignment = Alignment.Center) {
                        // GC sticks report up as a larger value, so up = (raw - center) > 0.
                        OctagonGate(
                            size = minOf(col, top) * 0.96f,
                            x = (pad.sx - center[0]) / 100f, y = (pad.sy - center[1]) / 100f,
                            dot = Gc.StickDot, deadzone = dz,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FaceButtons(pad, Modifier.width(col).fillMaxHeight())
                }
                Row(Modifier.height(bottom), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(col), contentAlignment = Alignment.Center) {
                        DPad(pad, minOf(col, bottom) * 0.5f)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(col), contentAlignment = Alignment.Center) {
                        OctagonGate(
                            size = minOf(col, bottom) * 0.78f,
                            x = (pad.cx - center[2]) / 85f, y = (pad.cy - center[3]) / 85f,
                            dot = Gc.CStick, deadzone = dz,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Label("Start", 13.sp, if (pad.pressed(b2Bit = PadState.B2_START)) Gc.Text else Gc.EmptyLabel, FontWeight.Medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TriggerBar("L", pad.lt, center[4], pad.pressed(b2Bit = PadState.B2_L), state.settings.triggerDeadzone)
            TriggerBar("R", pad.rt, center[5], pad.pressed(b2Bit = PadState.B2_R), state.settings.triggerDeadzone)
        }
    }
}

@Composable
private fun EmptyPort(index: Int, modifier: Modifier) {
    Column(
        modifier.drawBehind {
            drawRoundRect(
                Gc.EmptyBorder,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
        },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label("P${index + 1}", 24.sp, Gc.EmptyNumber, FontWeight.Medium)
        Label("Empty", 13.sp, Gc.EmptyLabel)
    }
}

/** The notched octagon around a GameCube stick, with the stick's position as a dot.
 *  x/y are -1..1 with +y = up. */
@Composable
private fun OctagonGate(size: Dp, x: Float, y: Float, dot: Color, deadzone: Float) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        val k = 0.414f * r // octagon edge offset
        val gate = Path().apply {
            moveTo(c.x - k, c.y - r); lineTo(c.x + k, c.y - r); lineTo(c.x + r, c.y - k)
            lineTo(c.x + r, c.y + k); lineTo(c.x + k, c.y + r); lineTo(c.x - k, c.y + r)
            lineTo(c.x - r, c.y + k); lineTo(c.x - r, c.y - k); close()
        }
        drawPath(gate, Gc.Well)
        val dotR = r * 0.26f
        val travel = r - dotR - 2.dp.toPx()
        if (deadzone > 0f) {
            drawCircle(Gc.Card, radius = travel * deadzone + 1f, center = c, style = Stroke(1.dp.toPx()))
        }
        // Keep the dot inside the gate even past the nominal range.
        var dx = x; var dy = y
        val m = sqrt(dx * dx + dy * dy)
        if (m > 1f) { dx /= m; dy /= m }
        drawCircle(dot, radius = dotR, center = Offset(c.x + dx * travel, c.y - dy * travel))
    }
}

/**
 * A, B, X, Y in the controller's real arrangement, measured from a photo with A's
 * radius as the unit: B (r 0.6) sits 2.0 away at ~155°; X and Y are short kidney
 * beans centered 1.85 away at ~-14° and ~-104°, hugging A. Angles: 0° = right,
 * positive = clockwise (screen y points down).
 */
@Composable
private fun FaceButtons(pad: PadState, modifier: Modifier) {
    Canvas(modifier) {
        // Cluster bounds in A-radius units: x -2.65..2.35, y -2.35..1.5 (may spill
        // slightly into the gap between columns).
        val r = minOf(size.width / 4.6f, size.height / 3.75f)
        val c = Offset(size.width / 2 + 0.15f * r, size.height / 2 + 0.425f * r)

        drawCircle(if (pad.pressed(b1Bit = PadState.B1_A)) Gc.AOn else Gc.AOff, r, c)
        drawCircle(
            if (pad.pressed(b1Bit = PadState.B1_B)) Gc.BOn else Gc.BOff,
            0.6f * r, c + polar(2.0f * r, 155f),
        )
        val x = if (pad.pressed(b1Bit = PadState.B1_X)) Gc.XyOn else Gc.XyOff
        val y = if (pad.pressed(b1Bit = PadState.B1_Y)) Gc.XyOn else Gc.XyOff
        drawPath(kidney(c, r, from = -27f, to = -1f), x)
        drawPath(kidney(c, r, from = -117f, to = -91f), y)
    }
}

private fun polar(radius: Float, degrees: Float): Offset {
    val a = Math.toRadians(degrees.toDouble())
    return Offset((radius * cos(a)).toFloat(), (radius * sin(a)).toFloat())
}

/**
 * Kidney-bean button around A (center [c], radius [r]) between two angles: an even-
 * thickness band whose inner edge follows A, with round ends — one smooth outline.
 */
private fun kidney(c: Offset, r: Float, from: Float, to: Float): Path {
    val inner = 1.4f * r
    val thick = 0.9f * r
    val n = 24
    return Path().apply {
        // outer edge, from -> to
        for (i in 0..n) {
            val p = c + polar(inner + thick, from + (to - from) * i / n)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        // round end at `to`, sweeping outward -> forward -> inward
        val capEnd = c + polar(inner + thick / 2, to)
        for (i in 1..12) {
            val p = capEnd + polar(thick / 2, to + 180f * i / 12)
            lineTo(p.x, p.y)
        }
        // inner edge, to -> from
        for (i in 0..n) {
            val p = c + polar(inner, to + (from - to) * i / n)
            lineTo(p.x, p.y)
        }
        // round end at `from`
        val capStart = c + polar(inner + thick / 2, from)
        for (i in 1..12) {
            val p = capStart + polar(thick / 2, from + 180f + 180f * i / 12)
            lineTo(p.x, p.y)
        }
        close()
    }
}

/** Plus-shaped D-pad; the pressed direction(s) light up. */
@Composable
private fun DPad(pad: PadState, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val arm = s / 3
        val corner = CornerRadius(arm * 0.25f)
        fun cell(col: Int, row: Int, color: Color) = drawRoundRect(
            color, topLeft = Offset(col * arm, row * arm),
            size = androidx.compose.ui.geometry.Size(arm, arm), cornerRadius = corner,
        )
        // body: vertical and horizontal bars
        drawRoundRect(Gc.Well, Offset(arm, 0f), androidx.compose.ui.geometry.Size(arm, s), corner)
        drawRoundRect(Gc.Well, Offset(0f, arm), androidx.compose.ui.geometry.Size(s, arm), corner)
        if (pad.pressed(b1Bit = PadState.B1_UP)) cell(1, 0, Gc.XyOn)
        if (pad.pressed(b1Bit = PadState.B1_DOWN)) cell(1, 2, Gc.XyOn)
        if (pad.pressed(b1Bit = PadState.B1_LEFT)) cell(0, 1, Gc.XyOn)
        if (pad.pressed(b1Bit = PadState.B1_RIGHT)) cell(2, 1, Gc.XyOn)
    }
}

/** Z, shaped like the controller's pill-shaped Z button. */
@Composable
private fun ZPill(pressed: Boolean) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (pressed) Gc.ActiveOutline else Gc.Well)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Label("Z", 12.sp, if (pressed) Gc.Text else Gc.EmptyLabel, FontWeight.Medium) }
}

/** Same math as vpad.c's trigger(): what the game actually receives, 0..1. */
@Composable
private fun TriggerBar(name: String, raw: Int, rest: Int, digital: Boolean, deadzone: Int) {
    val base = rest + deadzone
    val span = (225 - base).coerceAtLeast(64)
    val v = if (digital) 1f else ((raw - base).toFloat() / span).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(12.dp)) { Label(name, 13.sp, Gc.TextMuted) }
        Track(v, if (digital) Gc.Text else Gc.TriggerFill, Modifier.weight(1f), height = 6.dp)
    }
}

// ---------- settings ----------

/**
 * The four settings controls. [itemModifier] places each one: weighted in the
 * landscape row (weight 0 = natural width), full width in the portrait column.
 */
@Composable
private fun SettingsItems(state: UiState, actions: UiActions, itemModifier: (weight: Float) -> Modifier) {
    val slot: @Composable (Float, @Composable () -> Unit) -> Unit = { w, content ->
        Box(itemModifier(w)) { content() }
    }
    val s = state.settings
    slot(1.1f) {
        Column {
            Label("Button layout", 13.sp, Gc.TextMuted)
            Spacer(Modifier.height(7.dp))
            Segmented(
                options = listOf("Nintendo", "Xbox"),
                selected = if (s.xboxLayout) 1 else 0,
                onSelect = { actions.onSettings(s.copy(xboxLayout = it == 1)) },
            )
        }
    }
    slot(1f) {
        SliderSetting("Stick deadzone · ${s.stickDeadzone}%", s.stickDeadzone, 0, 30) {
            actions.onSettings(s.copy(stickDeadzone = it))
        }
    }
    slot(1f) {
        SliderSetting("Trigger deadzone · ${s.triggerDeadzone}", s.triggerDeadzone, 0, 40) {
            actions.onSettings(s.copy(triggerDeadzone = it))
        }
    }
    slot(0f) {
        Box(
            Modifier
                .clip(CircleShape)
                .border(1.5.dp, Gc.Accent, CircleShape)
                .clickable(onClick = actions.onRecalibrate)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) { Label("Recalibrate", 14.sp, Gc.StickDot) }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Gc.Well).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == selected) Gc.Accent else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(CircleShape).background(bg)
                    .clickable { onSelect(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Label(label, 14.sp, if (i == selected) Gc.Text else Gc.TextMuted) }
        }
    }
}

@Composable
private fun SliderSetting(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column {
        Label(title, 13.sp, Gc.TextMuted)
        Spacer(Modifier.height(10.dp))
        val fraction = (value - min).toFloat() / (max - min)
        // Touch target is taller than the visible track.
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(min, max) {
                    fun set(px: Float) = onChange((min + (px / size.width).coerceIn(0f, 1f) * (max - min)).roundToInt())
                    detectTapGestures { set(it.x) }
                }
                .pointerInput(min, max) {
                    fun set(px: Float) = onChange((min + (px / size.width).coerceIn(0f, 1f) * (max - min)).roundToInt())
                    detectHorizontalDragGestures { change, _ -> set(change.position.x) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Track(fraction, Gc.AccentSoft, Modifier.fillMaxWidth(), height = 6.dp)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.offset(x = (maxWidth - 18.dp) * fraction).size(18.dp)
                        .clip(CircleShape).background(Gc.StickDot),
                )
            }
        }
    }
}

// ---------- primitives ----------

@Composable
private fun Track(fraction: Float, fill: Color, modifier: Modifier, height: Dp) {
    Box(modifier.height(height).clip(CircleShape).background(Gc.Well)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(fill))
    }
}

@Composable
private fun Label(text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) {
    BasicText(text, style = TextStyle(fontFamily = Rubik, fontSize = size, color = color, fontWeight = weight))
}
