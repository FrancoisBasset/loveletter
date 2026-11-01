from random import shuffle

from textual.app import ComposeResult
from textual.widgets import Static

from loveletter.components.card import Card


class Deck(Static):
    cards: list[Card] = []

    def __init__(self):
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
        super().__init__()

    def compose(self) -> ComposeResult:
        yield Card("back")

    def pick_card(self) -> Card:
        card: Card = self.cards.pop()

        if len(self.cards) == 0:
            self.display = False

        return card

    def is_empty(self):
        return len(self.cards) == 0
