import json
import random
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

                for player in self.players:
                    yield player

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
        self.player.card = self.deck.pop().value
        self.player.name = "François"

    def position_player(self):
        if self.player is None or not self.player.is_mounted:
            return

        width, height = self.player.parent.content_size
        player_width, player_height = self.player.outer_size
        self.player.styles.offset = (
            (width - player_width) // 2,
            height - player_height - 1,
        )

        if self.players_count == 2:
            self.players[0].styles.offset = (
                (width - self.players[0].outer_size.width) // 2,
                1,
            )
        elif self.players_count == 3:
            self.players[0].styles.offset = (
                (width - self.players[0].outer_size.width) // 3,
                1,
            )
            self.players[1].styles.offset = (
                (width - self.players[1].outer_size.width) // 1.5,
                1,
            )
        elif self.players_count == 4:
            self.players[0].styles.offset = (
                (width - self.players[0].outer_size.width) // 3,
                1,
            )
            self.players[1].styles.offset = (
                (width - self.players[1].outer_size.width) // 2,
                1,
            )
            self.players[2].styles.offset = (
                (width - self.players[2].outer_size.width) // 1.5,
                1,
            )
        elif self.players_count == 5:
            self.players[0].styles.offset = (
                (width - self.players[0].outer_size.width) // 4.5,
                1,
            )
            self.players[1].styles.offset = (
                (width - self.players[1].outer_size.width) // 2.5,
                1,
            )
            self.players[2].styles.offset = (
                (width - self.players[2].outer_size.width) // 1.7,
                1,
            )
            self.players[3].styles.offset = (
                (width - self.players[3].outer_size.width) // 1.3,
                1,
            )
        elif self.players_count == 6:
            self.players[0].styles.offset = (
                (width - self.players[0].outer_size.width) // 10,
                1,
            )
            self.players[1].styles.offset = (
                (width - self.players[1].outer_size.width) // 3.5,
                1,
            )
            self.players[2].styles.offset = (
                (width - self.players[2].outer_size.width) // 2,
                1,
            )
            self.players[3].styles.offset = (
                (width - self.players[3].outer_size.width) // 1.4,
                1,
            )
            self.players[4].styles.offset = (
                (width - self.players[4].outer_size.width) // 1.1,
                1,
            )

    def generate_players(self):
        self.players = []

        for _ in range(self.players_count - 1):
            player: Player = Player()
            player.is_bot = True
            player.card = self.deck.pop().value
            player.styles.position = "absolute"

            with open("./src/loveletter/assets/firstnames.json") as file:
                firstnames: list[str] = json.load(file)
                player.name = random.choice(firstnames)

            self.players.append(player)

    @on(Click, "Deck, Deck *")
    def on_click_deck(self):
        card: Card = self.deck.pop()

        if card is None:
            return

        self.discard.set_last_card(card)
