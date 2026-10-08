package fr.francoisbasset.loveletter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.francoisbasset.loveletter.ui.theme.*
import kotlin.math.abs

private val Victory = Color(0xFF3A6B57)
private val Defeat = Color(0xFF9C3C48)

/**
 * Presentation is a snapshot: this code never reads a live hidden hand.
 * One finite transition per action, using Compose's inherited MotionDurationScale
 * (including Android's zero animator scale). No timers delay game interaction.
 */
@Composable
fun EffectScene(scene: UiEffectScene, modifier: Modifier = Modifier, animationKey: Any = scene) {
    val progress = remember(animationKey) { Animatable(0f) }
    LaunchedEffect(animationKey) { progress.animateTo(1f, tween(1150, easing = FastOutSlowInEasing)) }
    BoxWithConstraints(modifier.testTag("effect_${scene.kind.name.lowercase()}")) {
        val compact = maxHeight < 142.dp
        when (scene.kind) {
            UiEffectKind.BARON -> BaronScene(scene, progress.value, compact)
            UiEffectKind.PRIEST -> PriestScene(scene, progress.value, compact)
            UiEffectKind.KING -> KingScene(scene, progress.value, compact)
            UiEffectKind.GUARD -> GuardScene(scene, progress.value, compact)
            UiEffectKind.PRINCE -> PrinceScene(scene, progress.value, compact)
            UiEffectKind.DEAL -> DealScene(scene, progress.value, compact)
            else -> EmblemScene(scene, progress.value, compact)
        }
    }
}

private fun phase(progress: Float, start: Float, end: Float): Float = ((progress - start) / (end - start)).coerceIn(0f, 1f)

@Composable
private fun BaronScene(scene: UiEffectScene, progress: Float, compact: Boolean) {
    val actor = scene.participants.getOrNull(0)
    val target = scene.participants.getOrNull(1)
    if (actor == null || target == null) {
        EmblemScene(scene, progress, compact)
        return
    }
    val comparison = if (actor.cardValue != null && target.cardValue != null) {
        when { actor.cardValue > target.cardValue -> ">"; actor.cardValue < target.cardValue -> "<"; else -> "=" }
    } else if (scene.outcome == UiEffectOutcome.DRAW) "=" else null
    val decided = scene.outcome == UiEffectOutcome.WIN
    val reveal = phase(progress, .18f, .68f)
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ScenePlayerCard(actor, reveal, Modifier.weight(1f).fillMaxHeight().testTag("baron_card_actor"),
            verdict = if (actor.eliminated) "Éliminé" else if (decided) "Gagne" else null,
            entrance = -1f * (1f - phase(progress, 0f, .25f)), resultProgress = phase(progress, .7f, 1f))
        Column(Modifier.width(if (compact) 44.dp else 60.dp).testTag("baron_verdict"), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
            Icon(Icons.Outlined.Balance, null, Modifier.size(if (compact) 20.dp else 27.dp), tint = Gold)
            Box(Modifier.size(if (compact) 35.dp else 46.dp).background(Ivory, CircleShape), contentAlignment = Alignment.Center) {
                if (comparison != null) Text(comparison, color = Wine, fontWeight = FontWeight.Black,
                    fontSize = if (compact) 27.sp else 35.sp,
                    modifier = Modifier.graphicsLayer { alpha = phase(progress, .55f, .8f) })
                else Icon(Icons.Outlined.Lock, "Comparaison secrète", Modifier.size(19.dp), tint = Wine)
            }
            Text(if (scene.outcome == UiEffectOutcome.DRAW) "Égalité" else if (scene.privateToHuman) "Duel" else "Privé",
                color = Burgundy, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        ScenePlayerCard(target, reveal, Modifier.weight(1f).fillMaxHeight().testTag("baron_card_target"),
            verdict = if (target.eliminated) "Éliminé" else if (decided) "Gagne" else null,
            entrance = 1f - phase(progress, 0f, .25f), resultProgress = phase(progress, .7f, 1f))
    }
}

@Composable
private fun PriestScene(scene: UiEffectScene, progress: Float, compact: Boolean) {
    val target = scene.participants.lastOrNull()
    if (target == null || scene.outcome == UiEffectOutcome.NO_TARGET) { EmblemScene(scene, progress, compact); return }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        Column(Modifier.width(if (compact) 72.dp else 92.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SceneEmblem(if (target.cardValue == null) Icons.Outlined.Lock else Icons.Outlined.Visibility,
                Wine, phase(progress, 0f, .4f), if (compact) 45 else 62)
            Text(if (target.cardValue == null) "Regard privé" else "Révélée", style = MaterialTheme.typography.labelMedium, color = Burgundy, textAlign = TextAlign.Center)
        }
        ScenePlayerCard(target, phase(progress, .2f, .76f), Modifier.widthIn(max = 146.dp).weight(1f, fill = false).fillMaxHeight(),
            verdict = if (target.cardValue != null) "Pour vous" else null, resultProgress = phase(progress, .7f, 1f))
    }
}

@Composable
private fun KingScene(scene: UiEffectScene, progress: Float, compact: Boolean) {
    val actor = scene.participants.firstOrNull()
    val target = scene.participants.getOrNull(1)
    if (actor == null || target == null) { EmblemScene(scene, progress, compact); return }
    val exchange = phase(progress, .12f, .85f)
    val showingNew = exchange >= .5f
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ScenePlayerCard(actor.copy(cardValue = if (showingNew) actor.nextCardValue else actor.cardValue),
            if (showingNew) (exchange - .5f) * 2f else 1f - exchange * 2f,
            Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                translationX = (1f - abs(exchange * 2f - 1f)) * 24.dp.toPx()
            }, verdict = if (showingNew) "Reçue" else null, resultProgress = phase(progress, .8f, 1f))
        Column(Modifier.width(if (compact) 40.dp else 58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.SwapHoriz, "Échange des cartes", Modifier.size(if (compact) 32.dp else 44.dp).graphicsLayer {
                rotationZ = (1f - phase(progress, .16f, .85f)) * 180f
            }, tint = Wine)
            Text("Échange", style = MaterialTheme.typography.labelSmall, color = Burgundy, maxLines = 1)
        }
        ScenePlayerCard(target.copy(cardValue = if (showingNew) target.nextCardValue else target.cardValue),
            if (showingNew) (exchange - .5f) * 2f else 1f - exchange * 2f,
            Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                translationX = -(1f - abs(exchange * 2f - 1f)) * 24.dp.toPx()
            }, verdict = if (showingNew) "Reçue" else null, resultProgress = phase(progress, .8f, 1f))
    }
}

@Composable
private fun GuardScene(scene: UiEffectScene, progress: Float, compact: Boolean) {
    val target = scene.participants.getOrNull(1)
    if (target == null || scene.guessedCardValue == null) { EmblemScene(scene, progress, compact); return }
    val hit = scene.outcome == UiEffectOutcome.HIT
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ScenePlayerCard(UiSceneParticipant(-1, "Annonce", cardValue = scene.guessedCardValue), phase(progress, 0f, .4f), Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.width(if (compact) 46.dp else 62.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.MyLocation, null, Modifier.size(if (compact) 24.dp else 33.dp), tint = Wine)
            Icon(if (hit) Icons.Outlined.CheckCircle else Icons.Outlined.Close,
                if (hit) "Bonne annonce" else "Annonce manquée", Modifier.size(if (compact) 27.dp else 35.dp).graphicsLayer {
                    alpha = phase(progress, .6f, .95f)
                    scaleX = .8f + phase(progress, .6f, .95f) * .2f; scaleY = scaleX
                }, tint = if (hit) Victory else Muted)
            Text(if (hit) "Trouvé !" else "Raté", style = MaterialTheme.typography.labelSmall, color = Burgundy)
        }
        ScenePlayerCard(target, phase(progress, .22f, .72f), Modifier.weight(1f).fillMaxHeight(),
            verdict = if (target.eliminated) "Éliminé" else "En jeu", resultProgress = phase(progress, .7f, 1f))
    }
}

@Composable
private fun PrinceScene(scene: UiEffectScene, progress: Float, compact: Boolean) {
    val target = scene.participants.lastOrNull()
    if (target == null || scene.outcome == UiEffectOutcome.NO_TARGET) { EmblemScene(scene, progress, compact); return }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ScenePlayerCard(target.copy(name = "Défausse"), phase(progress, 0f, .3f), Modifier.weight(1f).fillMaxHeight().graphicsLayer {
            rotationZ = phase(progress, .2f, .8f) * -5f
            alpha = 1f - phase(progress, .65f, 1f) * .15f
        }, verdict = if (target.eliminated) "Éliminé" else null, resultProgress = phase(progress, .7f, 1f))
        Column(Modifier.width(if (compact) 44.dp else 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(if (target.eliminated) Icons.Outlined.Close else Icons.Outlined.ArrowForward, null, Modifier.size(30.dp), tint = Wine)
            Text(target.name, style = MaterialTheme.typography.labelSmall, color = Burgundy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!target.eliminated) ScenePlayerCard(target.copy(name = "Nouvelle carte", cardValue = target.nextCardValue),
            phase(progress, .3f, .95f), Modifier.weight(1f).fillMaxHeight(), entrance = 1f - phase(progress, .3f, .9f))
        else Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            SceneEmblem(Icons.Outlined.HeartBroken, Defeat, phase(progress, .5f, 1f), if (compact) 40 else 60)
            Text("Éliminé", color = Defeat, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun DealScene(scene: UiEffectScene, progress: Float, compact: Boolean) {
    val spread = phase(progress, .12f, .95f)
    val dealtCount = scene.participants.size.coerceIn(2, 6)
    val fanCentre = (dealtCount - 1) / 2f
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            for (index in 0 until dealtCount) {
                CardBack(Modifier.width(if (compact) 38.dp else 64.dp).fillMaxHeight(.78f).heightIn(max = 104.dp)
                    .graphicsLayer {
                        translationX = (index - fanCentre) * (if (compact) 24.dp else 32.dp).toPx() * spread
                        translationY = abs(index - fanCentre) * 4.dp.toPx() * spread
                        rotationZ = (index - fanCentre) * 9f * spread
                        alpha = phase(progress, index * .04f, .4f + index * .07f)
                    }, small = compact)
            }
        }
        Text("Les lettres sont distribuées", style = MaterialTheme.typography.labelMedium, color = Burgundy,
            modifier = Modifier.padding(top = 5.dp).graphicsLayer { alpha = phase(progress, .6f, 1f) })
    }
}

private fun effectEmblem(kind: UiEffectKind): ImageVector = when (kind) {
    UiEffectKind.HANDMAID -> Icons.Outlined.Shield
    UiEffectKind.CHANCELLOR -> Icons.Outlined.AccountBalance
    UiEffectKind.SPY -> Icons.Outlined.Visibility
    UiEffectKind.COUNTESS -> Icons.Outlined.Diamond
    UiEffectKind.PRINCESS -> Icons.Outlined.HeartBroken
    UiEffectKind.BARON -> Icons.Outlined.Balance
    UiEffectKind.PRIEST -> Icons.Outlined.MenuBook
    UiEffectKind.KING -> Icons.Outlined.SwapHoriz
    UiEffectKind.GUARD -> Icons.Outlined.Security
    UiEffectKind.PRINCE -> Icons.Outlined.Refresh
    UiEffectKind.DEAL -> Icons.Outlined.MailOutline
}

private fun sceneCaption(scene: UiEffectScene): String = when {
    scene.outcome == UiEffectOutcome.NO_TARGET -> "Aucune cible"
    scene.kind == UiEffectKind.HANDMAID -> "À l’abri"
    scene.kind == UiEffectKind.SPY -> "Faveur en jeu"
    scene.kind == UiEffectKind.COUNTESS -> "Défaussée"
    scene.kind == UiEffectKind.PRINCESS -> "Élimination"
    scene.kind == UiEffectKind.CHANCELLOR && scene.outcome == UiEffectOutcome.CHOOSING -> "Un choix à faire"
    scene.kind == UiEffectKind.CHANCELLOR -> "Une carte gardée"
    else -> "Effet résolu"
}

@Composable
private fun EmblemScene(scene: UiEffectScene, progress: Float, compact: Boolean) {
    val tint = when (scene.kind) { UiEffectKind.HANDMAID -> Victory; UiEffectKind.PRINCESS -> Defeat; else -> Wine }
    val enter = phase(progress, 0f, .65f)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (compact) 13.dp else 23.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (compact) 74.dp else 110.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (scene.kind == UiEffectKind.CHANCELLOR) {
                val count = (scene.cardsDrawn + 1).coerceIn(1, 3)
                val centre = (count - 1) / 2f
                repeat(count) { index -> CardBack(Modifier.size(if (compact) 34.dp else 48.dp, if (compact) 48.dp else 70.dp).graphicsLayer {
                    rotationZ = (index - centre) * 13f * enter
                    translationX = (index - centre) * 25.dp.toPx() * enter
                    translationY = if (index == 1) -7.dp.toPx() * enter else 0f
                }, small = compact) }
            } else {
                Canvas(Modifier.size(if (compact) 72.dp else 104.dp)) {
                    drawCircle(tint.copy(alpha = .10f * (1f - enter)), radius = size.minDimension * (.2f + enter * .3f))
                    drawCircle(tint.copy(alpha = .24f), radius = size.minDimension * .46f * enter, style = Stroke(1.dp.toPx()))
                    repeat(8) { index ->
                        val angle = Math.toRadians(index * 45.0)
                        val radius = size.minDimension * .43f
                        val point = center + Offset(kotlin.math.cos(angle).toFloat() * radius, kotlin.math.sin(angle).toFloat() * radius)
                        drawCircle(Gold.copy(alpha = phase(progress, .35f, .85f)), radius = 2.dp.toPx(), center = point)
                    }
                }
                SceneEmblem(if (scene.outcome == UiEffectOutcome.NO_TARGET) Icons.Outlined.Block else effectEmblem(scene.kind), tint, enter, if (compact) 53 else 76)
            }
        }
        Column(Modifier.widthIn(max = 144.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sceneCaption(scene), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, color = tint,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            scene.participants.firstOrNull()?.let { Text(it.name, style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (scene.kind == UiEffectKind.SPY) Icon(Icons.Outlined.Favorite, "Pion Faveur possible", Modifier.size(20.dp), tint = Gold)
            if (scene.kind == UiEffectKind.CHANCELLOR) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Victory)
                Text("1", color = Victory, fontWeight = FontWeight.Bold)
                if (scene.cardsDrawn > 0) {
                    Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(16.dp), tint = Muted)
                    Icon(Icons.Outlined.Layers, "Autres cartes sous la pioche", Modifier.size(20.dp), tint = Wine)
                }
            }
        }
    }
}

@Composable
private fun SceneEmblem(icon: ImageVector, tint: Color, progress: Float, size: Int) {
    Box(Modifier.size(size.dp).graphicsLayer {
        scaleX = .65f + .35f * progress; scaleY = scaleX; alpha = progress
    }.background(tint.copy(alpha = .09f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .57f).dp), tint = tint)
    }
}

@Composable
private fun ScenePlayerCard(player: UiSceneParticipant, reveal: Float, modifier: Modifier,
    verdict: String? = null, entrance: Float = 0f, resultProgress: Float = 1f) {
    BoxWithConstraints(modifier) {
        val small = maxHeight < 122.dp
        val squeezed = maxHeight < 74.dp
        Column(Modifier.fillMaxSize().graphicsLayer { translationX = entrance * 25.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (small) 2.dp else 4.dp)) {
            Text(player.name, style = if (squeezed) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = Burgundy, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            SceneCard(player.cardValue, reveal, player.eliminated, Modifier.weight(1f).widthIn(max = 146.dp).fillMaxWidth(),
                highlight = verdict == "Gagne" || verdict == "Reçue", resultProgress = resultProgress)
            if (verdict != null && !squeezed) {
                Row(Modifier.graphicsLayer { alpha = resultProgress }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val tint = if (player.eliminated) Defeat else Victory
                    Icon(if (player.eliminated) Icons.Outlined.Cancel else if (verdict == "Gagne") Icons.Outlined.EmojiEvents else Icons.Outlined.CheckCircle,
                        null, Modifier.size(if (small) 12.dp else 15.dp), tint = tint)
                    Text(verdict, fontWeight = FontWeight.SemiBold, fontSize = if (small) 10.sp else 12.sp,
                        lineHeight = if (small) 12.sp else 16.sp, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** Face-down remains semantically secret, including during a flip and AI-only duels. */
@Composable
private fun SceneCard(value: Int?, reveal: Float, eliminated: Boolean, modifier: Modifier,
    highlight: Boolean = false, resultProgress: Float = 1f) {
    val front = value != null && reveal >= .5f
    val rotation = if (front) (1f - reveal) * -180f else reveal * 180f
    Box(modifier.graphicsLayer {
        rotationY = if (value == null) 0f else rotation
        cameraDistance = 16f * density
        scaleX = 1f + if (highlight) .025f * resultProgress else 0f; scaleY = scaleX
    }) {
        if (!front) {
            CardBack(Modifier.fillMaxSize().testTag("scene_card_hidden"), small = true)
        } else {
            val info = cardInfo(requireNotNull(value))
            val border = if (eliminated) Defeat else if (highlight) Gold else Gold.copy(alpha = .65f)
            Surface(Modifier.fillMaxSize().testTag("scene_card_$value").semantics {
                contentDescription = "${info.name}, valeur $value${if (eliminated) ", éliminé" else ""}"
            }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (highlight || eliminated) 2.dp else 1.dp, border), shadowElevation = 2.dp) {
                BoxWithConstraints(Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
                    val availableHeight = maxHeight
                    val short = availableHeight < 82.dp
                    if (short) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(value.toString(), fontWeight = FontWeight.Black, color = Wine, fontSize = if (availableHeight < 37.dp) 19.sp else 30.sp, lineHeight = if (availableHeight < 37.dp) 21.sp else 34.sp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                if (availableHeight > 43.dp) Icon(cardSymbol(value), null, Modifier.size(23.dp), tint = Gold)
                                Text(info.name, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = Burgundy,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(value.toString(), fontWeight = FontWeight.Black, color = Wine, fontSize = 26.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(cardSymbol(value), null, Modifier.size(17.dp), tint = Gold)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(if (availableHeight < 122.dp) 37.dp else 54.dp).background(Gold.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(cardSymbol(value), null, Modifier.size(if (availableHeight < 122.dp) 25.dp else 36.dp), tint = Wine)
                                }
                            }
                            Text(info.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Burgundy,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (eliminated) Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = resultProgress * .22f }) {
                        drawLine(Defeat, Offset(size.width * .1f, size.height * .85f), Offset(size.width * .9f, size.height * .15f), strokeWidth = 3.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
fun PrivateEffectDialog(notice: UiPrivateNotice, onDismiss: () -> Unit) {
    val configuration = LocalConfiguration.current
    val sceneHeight = (configuration.screenHeightDp - 172).coerceIn(92, 248).dp
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 18.dp).widthIn(max = 520.dp).fillMaxWidth().testTag("effet_prive"),
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Gold.copy(alpha = .65f)), shadowElevation = 12.dp) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, "Information privée", Modifier.size(20.dp), tint = Wine)
                    Text(if (notice.scene?.kind == UiEffectKind.BARON) "Le duel du Baron" else notice.title,
                        style = MaterialTheme.typography.titleLarge, color = Burgundy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val scene = notice.scene
                if (scene == null) Text(notice.message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = sceneHeight).verticalScroll(rememberScrollState()))
                else EffectScene(scene, Modifier.fillMaxWidth().height(sceneHeight), animationKey = notice)
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(40.dp), contentPadding = PaddingValues(horizontal = 17.dp)) {
                    Text("J'ai lu"); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
