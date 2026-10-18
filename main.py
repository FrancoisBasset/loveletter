from textual.app import App, ComposeResult
from textual.widgets import Footer, Header
from textual_image.widget import Image

from utils import say


class LoveLetterApp(App):
    ENABLE_COMMAND_PALETTE = False
    TITLE = "Love letter"
    CSS_PATH = "style.tcss"

    BINDINGS = [("n", "new_game", "New Game"), ("q", "quit", "Quitter")]

    def compose(self) -> ComposeResult:
        yield Header(show_clock=False)

        with Image(image="assets/board.png", id="board"):
            yield Image(image="assets/spy.png")
            yield Image(image="assets/guard.png")
            yield Image(image="assets/priest.png")
            yield Image(image="assets/baron.png")
            yield Image(image="assets/handmaid.png")
            yield Image(image="assets/prince.png")
            yield Image(image="assets/chancellor.png")
            yield Image(image="assets/king.png")
            yield Image(image="assets/countess.png")
            yield Image(image="assets/princess.png")

            yield Image(image="assets/back.png")
            yield Image(image="assets/help.png", classes="help")
            yield Image(image="assets/seal.png", classes="seal")

        yield Footer()

    def action_new_game(self) -> None:
        say("Bonjour mon petit Chochodile !")


LoveLetterApp().run()
