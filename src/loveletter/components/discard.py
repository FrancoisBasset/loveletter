from textual.app import ComposeResult
from textual.reactive import reactive
from textual.widgets import Static

from loveletter.components.card import Card


class Discard(Static):
    last_card: str = reactive("", recompose=True)

    def watch_last_card(self, last_card: str):
        self.display = bool(last_card)

    def compose(self) -> ComposeResult:
        if self.last_card != "":
            yield Card(self.last_card)
