# Love Letter

Application de terminal avec Textual. Nécessite Python 3.14+ et `uv`.

## Lancement

```bash
uv run loveletter
```

La commande installe automatiquement le paquet et ses dépendances. Le lancement
en tant que module est également disponible :

```bash
uv run python -m loveletter
```

## Structure

```text
src/loveletter/
├── __init__.py
├── __main__.py      # Lancement avec python -m loveletter
├── app.py           # Interface Textual et point d'entrée de la commande
├── speech.py        # Synthèse vocale
├── style.tcss       # Style de l'interface
├── assets/          # Images du plateau et des cartes
└── models/          # Modèle vocal Piper et sa configuration
```
