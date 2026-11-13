import asyncio
import json
import random

from textual import on
from textual.app import ComposeResult
from textual.containers import Container, Horizontal
from textual.events import Click, Key
from textual.reactive import reactive
from textual_image.widget import Image

from loveletter import utils
from loveletter.components.card import Card, CardValue
from loveletter.components.deck import Deck
from loveletter.components.discard import Discard
from loveletter.components.player import Player


class Board(Container, can_focus=True):
    started: bool = reactive(False, recompose=True)
    players_count: int = 2
    deck: Deck = None
    discard: Discard = None
    player: Player = None
    players: list[Player] = []
    burned_card: Card | None = None
    deck_clicked: asyncio.Event | None = None
    player_card_selected: asyncio.Future[Card] | None = None
    player_selected: asyncio.Future[Player] | None = None
    wait_for_key: asyncio.Future[str] | None = None

    def compose(self) -> ComposeResult:
        with Image(image=utils.ASSETS_DIR / "board.png", id="board"):
            if self.started:
                yield from self.players

                with Horizontal(id="central"):
                    yield self.deck
                    yield self.discard

                yield self.player

    async def new_game(self):
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
        utils.init_voices()
        self.generate_player()
        self.generate_players()

    def reset_round(self):
        self.deck = Deck()
        self.burned_card = self.deck.pick_card()
        self.discard = Discard()

        for p in [self.player] + self.players:
            p.card = self.deck.pick_card()

    def generate_player(self):
        self.player = Player("François", False)
        self.player.display = False
        self.player.voice = utils.get_random_voice("M")

    def generate_players(self):
        self.players = []

        for _ in range(self.players_count - 1):
            sex: str = random.choice(["M", "F"])

            with open("./src/loveletter/assets/firstnames.json") as file:
                firstnames: list[str] = json.load(file)
                first_name: str = random.choice(firstnames[sex])

            player: Player = Player(first_name, True)
            player.voice = utils.get_random_voice(sex)
            player.speak(f"Je m'appelle {first_name} !")
            player.styles.position = "absolute"
            player.display = False
            self.players.append(player)

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

    def on_key(self, e: Key) -> None:
        if self.wait_for_key is None or self.wait_for_key.done():
            return

        self.wait_for_key.set_result(e.key)

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

    @on(Click, "Player")
    def on_click_player(self, e: Click):
        if self.player_selected is None or self.player_selected.done():
            return
            
        self.player_selected.set_result(e.control)

    def players_remain(self) -> list[Player]:
        return [p for p in [self.player] + self.players if not p.is_stopped]

    def round_is_finished(self) -> bool:
        return len(self.players_remain()) == 1 or self.deck.is_empty()

    def can_target_players(self) -> bool:
        return len([p for p in self.players if not p.is_stopped and not p.has_handmaid]) > 0

    def game_is_finished(self) -> bool:
        necessary_tokens: int = {
            "2": 6,
            "3": 5,
            "4": 4,
            "5": 3,
            "6": 3,
        }.get(str(self.players_count))

        return (
            len(
                [
                    p
                    for p in [self.player] + self.players
                    if p.favor_tokens >= necessary_tokens
                ]
            )
            > 0
        )

    def discard_card(self, player: Player, played_card: Card) -> Player:
        player.card = (
            player.second_card
            if player.card.value == played_card.value
            else player.card
        )
        player.second_card = None

        if played_card.value == CardValue.SPY:
            player.has_spy = True
        elif played_card.value == CardValue.HANDMAID:
            player.has_handmaid = True
        else:
            self.discard.card = played_card

        return player

    def play_prince(self, player: Player) -> Player:
        self.discard.card = player.card
        player.card = self.deck.pick_card()

        return player

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

            if player.has_handmaid:
                self.discard.card = Card(CardValue.HANDMAID)
                player.has_handmaid = False

            player.can_play = True

            if player.is_bot:
                await asyncio.sleep(1.5)

                player.second_card = self.deck.pick_card()
                await asyncio.sleep(1)
                player.speak(f"Je joue {player.card.get_name()}")
                self.discard_card(player, player.card)

                await asyncio.sleep(1)
            else:
                self.deck_clicked = asyncio.Event()
                await self.deck_clicked.wait()

                self.player_card_selected = asyncio.get_running_loop().create_future()
                card_to_play: Card = await self.player_card_selected

                player.speak(f"Je joue {card_to_play.get_name()}")
                self.discard_card(player, card_to_play)

                if card_to_play.value in [CardValue.GUARD, CardValue.PRIEST, CardValue.BARON, CardValue.PRINCE, CardValue.KING]:
                    if not self.can_target_players():
                        if card_to_play.value == "prince":
                            player.speak("Je joue le Prince sur moi-même")
                            player = self.play_prince(player)
                    else:
                        self.player_selected = asyncio.get_running_loop().create_future()
                        target_player: Player = await self.player_selected

                        self.notify(target_player.name)

                        if card_to_play.value == CardValue.GUARD:
                            key: str = ""

                            while not key in ["0", "2", "3", "4", "5", "6", "7", "8", "9"]:
                                self.notify("0 Espionne\n2 Prêtre\n3 Baron\n4 Servante\n5 Prince\n6 Chancelier\n7 Roi\n8 Comtesse\n9 Princesse")
                                self.wait_for_key = asyncio.get_running_loop().create_future()
                                key: str = await self.wait_for_key

                            name: str = Card.get_name_from_point(key)
                            player.speak(f"{target_player.name}, as-tu {Card.get_translation(name)} ?")

                            card_value = Card.get_name_from_point(key)
                            if card_value == target_player.card.value:
                                target_player.speak("Oui, je l'ai !")
                                self.discard_card(target_player, target_player.card)
                                target_player.has_spy = False
                                target_player.is_stopped = True
                            else:
                                target_player.speak("Non")
                        elif card_to_play.value == "priest":
                            player.speak(f"{target_player.name}, montre-moi ta carte !")
                            target_player.show_card = True
                            await asyncio.sleep(2)
                            target_player.show_card = False

            player.can_play = False

        first_to_play.favor_tokens += 1
