from textual_image.widget import Image

from loveletter.utils import ASSETS_DIR


class Card(Image, Renderable=Image._Renderable):
    def __init__(self, value: str, classes: str = ""):
        super().__init__(
            image=ASSETS_DIR / (value + ".png"),
            classes=f"card {classes}".strip(),
        )
        self.value = value
