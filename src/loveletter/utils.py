import random
from functools import cache, lru_cache
from pathlib import Path

import numpy as np
import sounddevice as sd
from piper import PiperVoice, SynthesisConfig

ASSETS_DIR = Path(__file__).parent / "assets"
VOICES_PATH = Path(__file__).parent / "voices"

type Voice = str | tuple[Path, int | None]

VOICES_MEN: list[Voice] = [
    (VOICES_PATH / "fr_FR-tom-medium.onnx", None),
    (VOICES_PATH / "fr_FR-upmc-medium.onnx", 1),
    "M1", "M2", "M3", "M4", "M5",
]
VOICES_WOMEN: list[Voice] = [
    (VOICES_PATH / "fr_FR-siwis-medium.onnx", None),
    (VOICES_PATH / "fr_FR-upmc-medium.onnx", 0),
    "F1", "F2", "F3", "F4", "F5",
]
voices_men: list[Voice] = []
voices_women: list[Voice] = []


@cache
def _get_supertonic():
    from supertonic import TTS

    return TTS(model="supertonic-3")


@cache
def _get_supertonic_style(voice: str):
    return _get_supertonic().get_voice_style(voice)


@lru_cache(maxsize=3)
def _get_piper_voice(model_path: Path) -> PiperVoice:
    return PiperVoice.load(model_path)


def init_voices():
    global voices_men, voices_women

    voices_men = VOICES_MEN.copy()
    voices_women = VOICES_WOMEN.copy()


def get_random_voice(sex: str) -> Voice:
    if sex == "M":
        random.shuffle(voices_men)
        return voices_men.pop()
    else:
        random.shuffle(voices_women)
        return voices_women.pop()


def speak(voice: Voice, text: str):
    if isinstance(voice, str):
        moteur = _get_supertonic()
        style = _get_supertonic_style(voice)
        audio, _ = moteur.synthesize(
            text, voice_style=style, lang="fr", total_steps=10, speed=1.0
        )
        sd.play(audio.reshape(-1), moteur.sample_rate, blocking=True)
    else:
        model_path, speaker_id = voice
        config = SynthesisConfig(
            speaker_id=speaker_id,
            length_scale=0.9,
            volume=1,
        )

        voix = _get_piper_voice(model_path)

        for chunk in voix.synthesize(text, syn_config=config):
            sd.play(
                np.frombuffer(chunk.audio_int16_bytes, dtype=np.int16),
                chunk.sample_rate,
                blocking=True,
            )
