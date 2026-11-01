from textual_image.widget import Image

from loveletter.utils import ASSETS_DIR


class FavorToken(Image, Renderable=Image._Renderable):
    def __init__(self):
        super().__init__(ASSETS_DIR / "favor_token.png")
