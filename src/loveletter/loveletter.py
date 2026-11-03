import asyncio
import json
import random
from dataclasses import replace
from pathlib import Path

from textual import on, work
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
        ("q", "quit", "Quitter"),
        ("c", "set_player_count", "Set player count (2)"),
        ("n", "new_game", "New Game"),
    ]

    started: bool = reactive(False, recompose=True)
    players_count: int = 2
    deck: Deck = None
    discard: Discard = None
    player: Player = None
    players: list[Player] = []
    burned_card: Card | None = None
    deck_clicked: asyncio.Event | None = None
    player_card_selected: asyncio.Future[Card] | None = None

    def on_mount(self):
        self.screen.screen_layout_refresh_signal.subscribe(
            self, lambda _: self.position_player()
        )

    def compose(self) -> ComposeResult:
        yield Header(show_clock=False)

        with Image(image=ASSETS_DIR / "board.png", id="board"):
            if self.started:
                yield from self.players

                with Horizontal(id="central"):
                    yield self.deck
                    yield self.discard

                yield self.player

        yield Footer()

    @work(exclusive=True)
    async def action_new_game(self):
        self.reset_game()

        while not self.game_is_finished():
            self.reset_round()

            if self.started:
                self.refresh(recompose=True)
            else:
                self.started = True

            self.call_after_refresh(self.refresh_bindings)

            await self.start_round()
            self.notify("La manche est terminée")

        self.notify("Le jeu est terminé")

    def reset_game(self):
        self.generate_player()
        self.generate_players()

    def reset_round(self):
        self.deck = Deck()
        self.burned_card = self.deck.pick_card()
        self.discard = Discard()

        for p in [self.player] + self.players:
            p.card = self.deck.pick_card()

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
        self.player = Player("François", False)
        self.player.display = False

    def position_player(self):
        if self.player is None or not self.player.is_mounted:
            return

        width, height = self.player.parent.content_size
        player_width, player_height = self.player.outer_size
        self.player.styles.offset = (
            (width - player_width) // 2,
            height - player_height - 1,
        )
        self.player.display = True

        count_center = {
            "2": [2],
            "3": [3, 1.5],
            "4": [3, 2, 1.5],
            "5": [4.5, 2.5, 1.7, 1.3],
            "6": [10, 3.5, 2, 1.4, 1.1],
        }

        for i, center in enumerate(count_center.get(str(self.players_count))):
            self.players[i].offset = (
                (width - self.players[i].outer_size.width) // center,
                1,
            )
            self.players[i].display = True

    def generate_players(self):
        self.players = []

        for _ in range(self.players_count - 1):
            with open("./src/loveletter/assets/firstnames.json") as file:
                firstnames: list[str] = json.load(file)
                first_name: str = random.choice(firstnames)

            player: Player = Player(first_name, True)
            player.styles.position = "absolute"
            player.display = False
            self.players.append(player)

    @on(Click, "Deck, Deck *")
    def on_click_deck(self):
        if self.deck_clicked is None or self.deck_clicked.is_set():
            return

        card: Card = self.deck.pick_card()

        if card is None:
            return

        self.deck_clicked.set()

        self.player.second_card = card

    @on(Click, "Player Card")
    def on_click_player_card(self, e: Click):
        if self.player_card_selected is None or self.player_card_selected.done():
            return

        self.player_card_selected.set_result(e.control)

    def players_remain(self) -> list[Player]:
        return [p for p in [self.player] + self.players if not p.is_stopped]

    def round_is_finished(self) -> bool:
        return len(self.players_remain()) == 1 or self.deck.is_empty()

    def game_is_finished(self) -> bool:
        necessary_tokens: int = {
            "2": 6,
            "3": 5,
            "4": 4,
            "5": 3,
            "6": 3,
        }.get(str(self.players_count))

        return len([p for p in [self.player] + self.players if p.favor_tokens >= necessary_tokens]) > 0

    async def start_round(self):
        players: list[Player] = self.players + [self.player]
        first_to_play: Player = random.choice(players)

        player_index: int = players.index(first_to_play)
        players = (
            [first_to_play] + players[player_index + 1 :] + players[0:player_index]
        )

        i: int = 0

        while not self.round_is_finished():
            player: Player = players[i]

            i = (i + 1) % self.players_count

            if player.is_stopped:
                continue

            player.can_play = True

            if player.is_bot:
                await asyncio.sleep(1.5)

                played_card = player.card
                player.second_card = self.deck.pick_card()

                await asyncio.sleep(1)

                player.card = player.second_card
                player.second_card = None

                self.discard.card = played_card
            else:
                self.deck_clicked = asyncio.Event()
                await self.deck_clicked.wait()

                self.player_card_selected = asyncio.get_running_loop().create_future()
                card_to_play: Card = await self.player_card_selected

                player.card = (
                    player.second_card
                    if player.card.value == card_to_play.value
                    else player.card
                )
                player.second_card = None

                self.discard.card = card_to_play

            player.can_play = False

        first_to_play.favor_tokens += 1
