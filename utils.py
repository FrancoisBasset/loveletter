import numpy as np
import sounddevice as sd
from piper import PiperVoice, SynthesisConfig


def say(texte):
    config = SynthesisConfig(
        length_scale=1.2,
        volume=1,
        noise_scale=0.8,
        noise_w_scale=0.8,
    )

    voix = PiperVoice.load("fr_FR-siwis-medium.onnx")

    for chunk in voix.synthesize(texte, syn_config=config):
        sd.play(
            np.frombuffer(chunk.audio_int16_bytes, dtype=np.int16),
            chunk.sample_rate,
            blocking=True,
        )
