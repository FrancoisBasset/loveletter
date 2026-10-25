from random import shuffle

from textual.app import ComposeResult
from textual.widgets import Static

from loveletter.components.card import Card


class Deck(Static):
    cards: list[Card] = []

    def on_mount(self):
        self.cards = [
            Card("spy"),
            Card("spy"),
            Card("guard"),
            Card("guard"),
            Card("guard"),
            Card("guard"),
            Card("guard"),
            Card("guard"),
            Card("priest"),
            Card("priest"),
            Card("baron"),
            Card("baron"),
            Card("handmaid"),
            Card("handmaid"),
            Card("chancellor"),
            Card("chancellor"),
            Card("prince"),
            Card("prince"),
            Card("king"),
            Card("countess"),
            Card("princess"),
        ]
        shuffle(self.cards)

    def compose(self) -> ComposeResult:
        yield Card("back")

    def pop(self):
        if len(self.cards) == 0:
            return

        return self.cards.pop()
