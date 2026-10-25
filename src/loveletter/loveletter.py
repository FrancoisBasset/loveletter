from dataclasses import replace

from pathlib import Path

from textual import on
from textual.app import App, ComposeResult
from textual.containers import Horizontal
from textual.events import Click
from textual.reactive import reactive
from textual.widgets import Footer, Header
from textual_image.widget import Image

from loveletter.components.card import Card
from loveletter.components.deck import Deck
from loveletter.components.discard import Discard
from loveletter.components.player import Player

ASSETS_DIR = Path(__file__).parent / "assets"


class LoveLetterApp(App):
    ENABLE_COMMAND_PALETTE = False
    TITLE = "Love letter"
    CSS_PATH = "style.tcss"

    BINDINGS: list[tuple[str, str, str]] = [
        ("n", "new_game", "New Game"),
        ("q", "quit", "Quitter"),
        ("c", "set_player_count", "Set player count (2)"),
    ]

    started: bool = reactive(False, recompose=True)
    players_count: int = 2
    deck: Deck = None
    discard: Discard = None
    player: Player = None
    players: list[Player] = []

    def on_mount(self):
        self.screen.screen_layout_refresh_signal.subscribe(
            self, lambda _: self.position_player()
        )

    def compose(self) -> ComposeResult:
        yield Header(show_clock=False)

        with Image(image=ASSETS_DIR / "board.png", id="board"):
            if self.started:
                with Horizontal(classes="central"):
                    yield self.deck
                    yield self.discard

                # for player in self.players:
                #    yield player
                yield self.player

        yield Footer()

    def action_new_game(self):
        self.deck = Deck()
        self.discard = Discard()
        self.generate_player()
        self.generate_players()

        if self.started:
            self.refresh(recompose=True)
        else:
            self.started = True

    def action_set_player_count(self):
        self.players_count += 1

        if self.players_count == 7:
            self.players_count = 2

        bindings = self._bindings.key_to_bindings["c"]
        bindings[0] = replace(
            bindings[0], description=f"Set player count ({self.players_count})"
        )
        self.refresh_bindings()

    def generate_player(self):
        self.player = Player()
        self.player.name = "François"

    def position_player(self):
        if self.player is None or not self.player.is_mounted:
            return

        width, height = self.player.parent.content_size
        player_width, player_height = self.player.outer_size
        self.player.styles.offset = (
            (width - player_width) // 2,
            height - player_height,
        )

    def generate_players(self):
        self.players = []

        width, height = self.app.size

        for _ in range(self.players_count - 1):
            player: Player = Player()
            player.styles.position = "absolute"

            if self.players_count == 2:
                player.styles.offset = (width / 2 - player.size.width / 2, 1)

                self.players.append(player)

    @on(Click, "Deck, Deck *")
    def on_click_deck(self):
        card: Card = self.deck.pop()

        self.discard.last_card = card.value
