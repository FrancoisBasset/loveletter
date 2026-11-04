from dataclasses import replace

from textual import work
from textual.app import App, ComposeResult
from textual.widgets import Footer, Header

from loveletter.components.board import Board


class LoveLetterApp(App):
    ENABLE_COMMAND_PALETTE = False
    TITLE = "Love letter"
    CSS_PATH = "style.tcss"

    BINDINGS: list[tuple[str, str, str]] = [
        ("q", "quit", "Quitter"),
        ("c", "set_player_count", "Set player count (2)"),
        ("n", "new_game", "New Game"),
    ]

    board: Board = Board()
    players_count: int = 2

    def on_mount(self):
        self.screen.screen_layout_refresh_signal.subscribe(
            self, lambda _: self.board.position_player()
        )

    def compose(self) -> ComposeResult:
        yield Header(show_clock=False)

        yield self.board

        yield Footer()

    @work(exclusive=True)
    async def action_new_game(self):
        self.board.players_count = self.players_count
        await self.board.new_game()

    def action_set_player_count(self):
        self.players_count += 1

        if self.players_count == 7:
            self.players_count = 2

        bindings = self._bindings.key_to_bindings["c"]
        bindings[0] = replace(
            bindings[0], description=f"Set player count ({self.players_count})"
        )
        self.refresh_bindings()
