from enum import StrEnum

from textual_image.widget import Image

from loveletter.utils import ASSETS_DIR

class CardValue(StrEnum):
    SPY = "spy"
    GUARD = "guard"
    PRIEST = "priest"
    BARON = "baron"
    HANDMAID = "handmaid"
    PRINCE = "prince"
    CHANCELLOR = "chancellor"
    KING = "king"
    COUNTESS = "countess"
    PRINCESS = "princess"


class Card(Image, Renderable=Image._Renderable):
    def __init__(self, value: str, classes: str = ""):
        super().__init__(
            image=ASSETS_DIR / (value + ".png"),
            classes=f"card {classes}".strip(),
        )
        self.value = value

    def get_name(self) -> str:
        return Card.get_translation(self.value)

    @staticmethod
    def get_name_from_point(point: str) -> str:
        return {
            "0": CardValue.SPY,
            "1": CardValue.GUARD,
            "2": CardValue.PRIEST,
            "3": CardValue.BARON,
            "4": CardValue.HANDMAID,
            "5": CardValue.PRINCE,
            "6": CardValue.CHANCELLOR,
            "7": CardValue.KING,
            "8": CardValue.COUNTESS,
            "9": CardValue.PRINCESS,
        }[point]

    @staticmethod
    def get_translation(key: str) -> str:
        return {
            CardValue.SPY: "l'Espionne",
            CardValue.GUARD: "le Garde",
            CardValue.PRIEST: "le Prêtre",
            CardValue.BARON: "le Baron",
            CardValue.HANDMAID: "la Servante",
            CardValue.PRINCE: "le Prince",
            CardValue.CHANCELLOR: "le Chancelier",
            CardValue.KING: "le Roi",
            CardValue.COUNTESS: "la Comtesse",
            CardValue.PRINCESS: "la Princesse",
        }[key]