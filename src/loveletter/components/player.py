from textual.app import ComposeResult
from textual.containers import Vertical
from textual.widgets import Label

from loveletter.components.card import Card


class Player(Vertical):
    is_bot: bool = False
    name: str = ""
    card: str | None = None

    def __init__(self):
        super().__init__()
        self.styles.width = 10
        self.styles.height = "auto"
        self.styles.position = "absolute"
        self.styles.layer = "players"
        self.styles.background = "transparent"

    def compose(self) -> ComposeResult:
        label = Label(self.name)
        label.styles.width = "100%"
        label.styles.text_align = "center"
        yield label

        card = Card("back")
        card.styles.width = "auto"
        yield card
