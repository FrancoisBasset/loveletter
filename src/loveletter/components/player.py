from textual.app import ComposeResult
from textual.containers import Horizontal, Vertical
from textual.geometry import Region
from textual.strip import Strip
from textual.widgets import Label

from loveletter.components.card import Card


class Player(Vertical):
    is_bot: bool = False
    name: str = ""
    card: str | None = None
    have_spy: bool = False
    have_handmaid: bool = False

    def render_lines(self, crop: Region) -> list[Strip]:
        return []

    def compose(self) -> ComposeResult:
        if not self.is_bot and (self.have_spy or self.have_handmaid):
            with Horizontal():
                if self.have_spy:
                    yield Card("spy")

                if self.have_handmaid:
                    yield Card("handmaid")

        if self.is_bot:
            yield Label(self.name)

            card = Card("back")
        else:
            card = Card(self.card or "back")

        yield card

        if self.is_bot and (self.have_spy or self.have_handmaid):
            with Horizontal():
                if self.have_spy:
                    yield Card("spy")

                if self.have_handmaid:
                    yield Card("handmaid")
