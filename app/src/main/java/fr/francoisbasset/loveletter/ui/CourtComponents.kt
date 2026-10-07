package fr.francoisbasset.loveletter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Gold) {
    Text(text.uppercase(), modifier, color, style = MaterialTheme.typography.labelSmall)
}

@Composable
internal fun CourtPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(Panel.copy(alpha = .86f))
            .border(1.dp, Gold.copy(alpha = .12f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

@Composable
internal fun ScreenHeading(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = "Retour" }) {
            Text("‹", fontSize = 32.sp, color = Gold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Original geometric court emblems; no publisher artwork is embedded. */
@Composable
internal fun CourtEmblem(value: Int, modifier: Modifier = Modifier, color: Color = Gold) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * .35f
        val stroke = Stroke(width = minOf(w, h) * .024f)
        drawCircle(color.copy(alpha = .12f), r * 1.25f)
        drawCircle(color.copy(alpha = .5f), r * 1.21f, style = Stroke(stroke.width * .45f))
        when (value) {
            0 -> { // Espionne: eye and hidden gaze
                val eye = Path().apply {
                    moveTo(cx - r, cy)
                    quadraticTo(cx, cy - r, cx + r, cy)
                    quadraticTo(cx, cy + r, cx - r, cy)
                }
                drawPath(eye, color, style = stroke)
                drawCircle(color, r * .27f, style = stroke)
            }
            1 -> { // Garde: shield
                val shield = Path().apply {
                    moveTo(cx - r * .7f, cy - r * .8f); lineTo(cx + r * .7f, cy - r * .8f)
                    lineTo(cx + r * .6f, cy + r * .22f); quadraticTo(cx + r * .4f, cy + r * .65f, cx, cy + r)
                    quadraticTo(cx - r * .4f, cy + r * .65f, cx - r * .6f, cy + r * .22f); close()
                }
                drawPath(shield, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(cx, cy - r * .4f), androidx.compose.ui.geometry.Offset(cx, cy + r * .45f), stroke.width)
            }
            2 -> { // Prêtre: an open book
                val book = Path().apply {
                    moveTo(cx, cy - r * .5f); quadraticTo(cx - r * .5f, cy - r * .9f, cx - r, cy - r * .6f)
                    lineTo(cx - r, cy + r * .65f); quadraticTo(cx - r * .4f, cy + r * .3f, cx, cy + r * .8f)
                    quadraticTo(cx + r * .4f, cy + r * .3f, cx + r, cy + r * .65f); lineTo(cx + r, cy - r * .6f)
                    quadraticTo(cx + r * .5f, cy - r * .9f, cx, cy - r * .5f); lineTo(cx, cy + r * .8f)
                }
                drawPath(book, color, style = stroke)
            }
            4 -> { // Servante: protective arch
                drawCircle(color, r * .27f, center = androidx.compose.ui.geometry.Offset(cx, cy - r * .3f), style = stroke)
                val cloak = Path().apply {
                    moveTo(cx - r * .8f, cy + r * .8f); quadraticTo(cx - r * .7f, cy - r * 1.2f, cx, cy - r)
                    quadraticTo(cx + r * .7f, cy - r * 1.2f, cx + r * .8f, cy + r * .8f); close()
                }
                drawPath(cloak, color, style = stroke)
            }
            6 -> { // Chancelier: feather
                val feather = Path().apply {
                    moveTo(cx - r * .65f, cy + r * .9f); quadraticTo(cx - r * .6f, cy - r * .8f, cx + r * .8f, cy - r)
                    quadraticTo(cx + r * .95f, cy + r * .2f, cx - r * .65f, cy + r * .9f)
                }
                drawPath(feather, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(cx - r * .85f, cy + r * 1.1f), androidx.compose.ui.geometry.Offset(cx + r * .5f, cy - r * .65f), stroke.width)
            }
            7, 8, 9 -> { // Royal crowns
                val crown = Path().apply {
                    moveTo(cx - r, cy - r * .55f); lineTo(cx - r * .75f, cy + r * .55f)
                    lineTo(cx + r * .75f, cy + r * .55f); lineTo(cx + r, cy - r * .55f)
                    lineTo(cx + r * .4f, cy - r * .1f); lineTo(cx, cy - r)
                    lineTo(cx - r * .4f, cy - r * .1f); close()
                }
                drawPath(crown, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(cx - r * .68f, cy + r * .85f), androidx.compose.ui.geometry.Offset(cx + r * .68f, cy + r * .85f), stroke.width)
                if (value == 9) drawCircle(color, r * .13f, center = androidx.compose.ui.geometry.Offset(cx, cy + r * .18f))
            }
            else -> { // Baron / Prince: court star
                val path = Path()
                repeat(16) { i ->
                    val angle = i * Math.PI / 8 - Math.PI / 2
                    val length = if (i % 2 == 0) r else r * .48f
                    val x = cx + cos(angle).toFloat() * length
                    val y = cy + sin(angle).toFloat() * length
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color, style = stroke)
                drawCircle(color, r * .22f)
            }
        }
    }
}

@Composable
internal fun PlayingCard(
    value: Int,
    name: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    width: Dp = 146.dp,
    onClick: (() -> Unit)? = null,
) {
    val elevation by animateFloatAsState(if (selected) -7f else 0f, label = "carte sélectionnée")
    val border by animateColorAsState(if (selected) Gold else Gold.copy(alpha = .3f), label = "contour de carte")
    Column(
        modifier.width(width).height(214.dp)
            .graphicsLayer { translationY = elevation; alpha = if (enabled) 1f else .65f }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF5E8CF), Color(0xFFD4C2A2))))
            .border(if (selected) 3.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "$name, valeur $value. $subtitle${if (selected) ". Sélectionnée" else ""}" }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), color = Wine, fontSize = 25.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("LL", color = Wine.copy(alpha = .55f), fontFamily = FontFamily.Serif, fontSize = 11.sp)
        }
        CourtEmblem(value, Modifier.size(87.dp), Wine)
        Spacer(Modifier.height(5.dp))
        Text(name, color = Ink, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Ink.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
internal fun CardBack(modifier: Modifier = Modifier, count: Int? = null) {
    Box(
        modifier.width(66.dp).height(88.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(listOf(Wine, Color(0xFF321E31))))
            .border(1.dp, Gold.copy(alpha = .55f), RoundedCornerShape(9.dp))
            .padding(5.dp)
            .border(1.dp, Gold.copy(alpha = .25f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (count == null) Text("L", color = Gold, fontFamily = FontFamily.Serif, fontSize = 29.sp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), color = Gold, fontSize = 27.sp, fontFamily = FontFamily.Serif)
            Text("CARTES", color = Gold, fontSize = 8.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
internal fun Token(text: String, color: Color = Gold, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .11f)).padding(horizontal = 10.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelMedium)
}

@Composable
internal fun EmptyState(message: String) {
    Text(message, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
}
