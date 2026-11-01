from textual.app import ComposeResult
from textual.reactive import reactive
from textual.widgets import Static

from loveletter.components.card import Card


class Discard(Static):
    card: Card | None = reactive(None, recompose=True)

    def __init__(self):
        super().__init__()
        self.display = False

    def compose(self) -> ComposeResult:
        if self.card is not None:
            yield Card(self.card.value)

    def watch_card(self, last_card: Card):
        if last_card:
            self.display = True
