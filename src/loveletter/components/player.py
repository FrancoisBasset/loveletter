from textual.app import ComposeResult
from textual.containers import Horizontal, Vertical
from textual.geometry import Region
from textual.reactive import reactive
from textual.strip import Strip
from textual.widgets import Label, Static

from loveletter.components.card import Card
from loveletter.components.favor_token import FavorToken


class Player(Vertical):
    is_bot: bool = False
    name: str = ""
    card: Card | None = reactive(None, recompose=True)
    second_card: Card | None = reactive(None, recompose=True)
    has_spy: bool = False
    has_handmaid: bool = False
    favor_tokens: int = 0
    is_stopped: bool = False
    can_play: bool = reactive(False, recompose=True)

    def __init__(self, name: str, is_bot: bool):
        self.name = name
        self.is_bot = is_bot
        super().__init__()

    def render_lines(self, crop: Region) -> list[Strip]:
        return []

    def compose(self) -> ComposeResult:
        if self.is_bot:
            if self.can_play:
                yield self.show_active_bar()

            yield from self.show_tokens()
            yield self.show_name()
            yield from self.show_cards()
            yield from self.show_spy_or_handmaid()
        else:
            yield from self.show_spy_or_handmaid()
            yield from self.show_cards()
            yield from self.show_tokens()

            if self.can_play:
                yield self.show_active_bar()

    def show_name(self) -> ComposeResult:
        return Label(self.name)

    def show_spy_or_handmaid(self) -> ComposeResult:
        with Horizontal():
            if self.has_spy:
                yield Card("spy")

            if self.has_handmaid:
                yield Card("handmaid")

    def show_cards(self) -> ComposeResult:
        if self.is_bot:
            with Horizontal():
                yield Card("back")
                if self.second_card:
                    yield Card("back")
        else:
            with Horizontal():
                yield self.card
                if self.second_card:
                    yield self.second_card

    def show_tokens(self):
        with Horizontal():
            for _ in range(self.favor_tokens):
                yield FavorToken()

            if self.favor_tokens == 0:
                yield Static()

    def show_active_bar(self):
        bar: Static = Static()
        bar.styles.height = 1
        bar.styles.background = "springgreen"
        return bar
