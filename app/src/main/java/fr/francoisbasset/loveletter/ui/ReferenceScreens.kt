package fr.francoisbasset.loveletter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.francoisbasset.loveletter.ui.theme.*

@Composable
fun HomeScreen(hasSavedGame: Boolean, start: () -> Unit, resume: () -> Unit, rules: () -> Unit, cards: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            CardBack(Modifier.width(92.dp).height(128.dp))
            Surface(Modifier.align(Alignment.BottomEnd).size(48.dp), shape = CircleShape, color = Gold, shadowElevation = 4.dp) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.FavoriteBorder, null, tint = Burgundy, modifier = Modifier.size(26.dp)) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Love Letter", style = MaterialTheme.typography.displayLarge, color = Burgundy)
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 8.dp)) {
            Text("v0.3 · La cour s’anime", style = MaterialTheme.typography.labelMedium, color = Burgundy, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Une lettre. Un secret.\nToute la cour à convaincre.", textAlign = TextAlign.Center,
            color = Muted, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(30.dp))
        Button(onClick = start, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("nouvelle_partie")) {
            Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Nouvelle partie")
        }
        if (hasSavedGame) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = resume, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text("Reprendre la partie")
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = rules, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Règles") }
            OutlinedButton(onClick = cards, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Style, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cartes") }
        }
        Spacer(Modifier.height(28.dp))
        Text("Édition française Z-Man Games · 2019", style = MaterialTheme.typography.labelMedium, color = Muted)
        Text("2 à 6 joueurs · Hors ligne", style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(10.dp))
        Text("Adaptation de fan non officielle\nVisuels originaux, règles de l'édition 2019", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(state: AppUiState, onName: (String) -> Unit, onPlayers: (Int) -> Unit, onDifficulty: (AiLevel) -> Unit, onFirstPlayer: (Int?) -> Unit, onPace: (GamePace) -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Entrez à la cour", style = MaterialTheme.typography.headlineLarge, color = Burgundy)
        Text("À vous de jouer.", color = Muted)
        OutlinedTextField(value = state.humanName, onValueChange = { onName(it.take(24)) },
            label = { Text("Votre nom") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Person, null) })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nombre de joueurs", style = MaterialTheme.typography.titleLarge)
            Text("Vous et ${state.playerCount - 1} adversaire${if (state.playerCount > 2) "s" else ""} IA", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..6).forEach { count -> FilterChip(selected = state.playerCount == count, onClick = { onPlayers(count) }, label = { Text("$count joueurs") }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Qui commence ?", style = MaterialTheme.typography.titleLarge)
            Text("Choisissez un joueur ou laissez faire le hasard.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until state.playerCount).forEach { player ->
                    FilterChip(selected = state.firstPlayer == player, onClick = { onFirstPlayer(player) }, label = { Text(if (player == 0) "Vous" else "IA $player") })
                }
                FilterChip(selected = state.firstPlayer == null, onClick = { onFirstPlayer(null) }, label = { Text("Aléatoire") })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Difficulté de la cour", style = MaterialTheme.typography.titleLarge)
            AiLevel.entries.forEach { level ->
                Surface(onClick = { onDifficulty(level) }, color = if (state.difficulty == level) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp), border = if (state.difficulty == level) androidx.compose.foundation.BorderStroke(1.dp, Wine) else null) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.difficulty == level, onClick = { onDifficulty(level) })
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(level.label, style = MaterialTheme.typography.titleMedium)
                            Text(level.explanation, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Le rythme de la partie", style = MaterialTheme.typography.titleLarge)
            Text("Une action, une animation. À votre rythme.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            GamePace.entries.forEach { pace ->
                Surface(onClick = { onPace(pace) }, color = if (state.pace == pace) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("rythme_${pace.name}")) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.pace == pace) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null, tint = Wine)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(pace.label, style = MaterialTheme.typography.titleMedium)
                            Text(pace.explanation, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.secondary)
                Text("Les IA jouent avec leurs propres cartes et les informations autorisées. Vos secrets restent privés.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("lancer_partie")) { Text("Distribuer les cartes") }
        Text("La partie est sauvegardée automatiquement à chaque décision.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(12.dp))
    }
}

private data class RuleParagraph(val title: String, val text: String)
private val RuleSections = listOf(
    RuleParagraph("L'objectif", "Gagnez des pions Faveur en remportant des manches. La partie se termine dès qu'un joueur atteint le seuil : 6 à 2 joueurs, 5 à 3, 4 à 4 et 3 à 5 ou 6 joueurs. Plusieurs joueurs peuvent partager la victoire finale."),
    RuleParagraph("La mise en place", "Le paquet contient 21 cartes. Une carte est mise de côté, face cachée. À 2 joueurs, trois cartes supplémentaires sont retirées, face visible. Chacun reçoit une carte, puis le premier joueur pioche pour commencer son tour."),
    RuleParagraph("Votre tour", "Piochez une carte, puis jouez l'une des deux cartes de votre main et résolvez son effet. Vous conservez l'autre. La Comtesse est obligatoire lorsque vous avez aussi un Prince ou le Roi. Les défausses restent publiques."),
    RuleParagraph("Choisir une cible", "Le Garde, le Prêtre, le Baron et le Roi visent un autre joueur encore en lice et non protégé. Le Prince peut aussi vous viser. S'il n'y a aucune cible valide pour une carte qui vise seulement un autre joueur, elle est défaussée sans effet."),
    RuleParagraph("La protection", "La Servante vous protège des effets des autres joueurs jusqu'au début de votre prochain tour. Vous ne pouvez donc pas être choisi comme cible par un adversaire pendant cette période. Votre propre Prince peut néanmoins vous viser."),
    RuleParagraph("L'élimination", "Une déduction exacte du Garde, un duel perdu au Baron ou la perte de la Princesse vous élimine. Votre main est défaussée et vous ne jouez plus jusqu'à la manche suivante. Après votre élimination, vous pouvez observer la fin de la manche."),
    RuleParagraph("La fin d'une manche", "S'il ne reste qu'un joueur en lice, il remporte la manche. Sinon, lorsque le dernier tour avec la dernière carte de la pioche est résolu, les survivants révèlent leur main. La plus forte valeur gagne. En cas d'égalité, chacun des ex æquo gagne un pion : la somme des défausses ne sert pas de départage dans cette édition."),
    RuleParagraph("Le bonus d'Espionne", "L'unique joueur encore en lice ayant joué ou défaussé une ou plusieurs Espionnes gagne un pion supplémentaire. Il peut gagner ce bonus sans remporter la manche. Les Espionnes des joueurs éliminés ne comptent pas."),
    RuleParagraph("La manche suivante", "Les cartes sont mélangées et les protections sont supprimées. Le vainqueur commence. Si plusieurs joueurs ont remporté la manche, cette application tire au sort le premier joueur parmi eux, à la place du critère social de la règle papier."),
    RuleParagraph("Les informations secrètes", "Seuls votre main, les informations que vous avez obtenues et les mains révélées à la fin de la manche sont visibles. Les comparaisons du Baron et le regard du Prêtre apparaissent dans une notification privée. Un échange ou une nouvelle carte peut rendre une ancienne information inutilisable.")
)

@Composable
fun RulesScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Les règles de la cour", style = MaterialTheme.typography.headlineLarge, color = Burgundy)
            Spacer(Modifier.height(8.dp))
            Text("Love Letter · Édition française Z-Man Games 2019\n21 cartes · 2 à 6 joueurs", color = Muted)
        }
        items(RuleSections) { paragraph ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(paragraph.title, style = MaterialTheme.typography.titleLarge, color = Burgundy)
                Text(paragraph.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Suivre la partie sur le plateau", style = MaterialTheme.typography.titleLarge)
                    Text("Tous les joueurs restent visibles autour de la table. La carte jouée, son acteur et sa cible apparaissent au centre. Une bordure bordeaux désigne l'acteur ; une bordure dorée désigne sa cible. Les petits dos de carte indiquent seulement le nombre de cartes secrètes.", style = MaterialTheme.typography.bodyMedium)
                    Text("Votre main reste en bas. Touchez une carte jouable, puis une cible autorisée sur le plateau ou dans le panneau d'actions. Ce panneau possède son propre défilement : le plateau reste visible. Touchez un siège pour consulter sa défausse publique (D).", style = MaterialTheme.typography.bodyMedium)
                    Text("À mon rythme : chaque action attend votre appui sur Continuer. Lent et Fluide avancent automatiquement après un temps de lecture. Pause arrête la progression ; les informations privées doivent toujours être lues avant la suite.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("À propos de cette adaptation", fontWeight = FontWeight.Bold)
                    Text("Projet de fan non officiel, sans affiliation ni approbation de Z-Man Games ou Asmodee. Les règles et les noms désignent le jeu Love Letter. Les illustrations, le dos des cartes, les icônes et les textes d'aide sont des créations originales.", style = MaterialTheme.typography.bodyMedium)
                    Text("Les fichiers officiels ne sont pas redistribués : leur disponibilité publique ne constitue pas une licence pour les inclure dans une application. Sources et limites documentées dans docs/assets-sources.md du dépôt.", style = MaterialTheme.typography.bodyMedium)
                    Text("Version 0.2.0 · Partie locale, sans compte ni connexion", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
        }
    }
}

@Composable
fun CardsScreen() {
    var detail by remember { mutableStateOf<Int?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Les personnages", style = MaterialTheme.typography.headlineLarge, color = Burgundy)
            Spacer(Modifier.height(8.dp))
            Text("10 personnages · 21 cartes\nTouchez une carte pour comprendre son effet.", color = Muted)
        }
        items(CardCatalogue) { card ->
            Surface(onClick = { detail = card.value }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(Burgundy, CircleShape), contentAlignment = Alignment.Center) {
                            Text(card.value.toString(), fontWeight = FontWeight.Bold, color = Ivory)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(card.name, style = MaterialTheme.typography.titleLarge, color = Burgundy)
                            Text("${card.copies} exemplaire${if (card.copies > 1) "s" else ""}", style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                    }
                    Text(card.effect, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    detail?.let { CardDetailDialog(it) { detail = null } }
}
