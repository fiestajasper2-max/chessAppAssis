# ChessMate AI

An Android companion for reviewing **completed chess games** with Stockfish. It imports public game archives from Chess.com's Published Data API or reads PGN files selected on-device.

## Features

- Import completed games from a public Chess.com username.
- Import PGN files from device storage.
- Select a game and review positions with Stockfish.
- Android ARM64 debug APK built by GitHub Actions.

## Build an APK

1. Open the **Actions** tab in this repository.
2. Select **Build ChessMate AI APK**.
3. Run the workflow with **Run workflow**, or push a commit to `main`.
4. When the run finishes, open it and download the `ChessMateAI-debug-apk` artifact.
5. Extract the ZIP to get `app-debug.apk`.

## Project notes

- Requires Android 8.0 (API 26) or newer; the included Stockfish build targets ARM64 Android devices.
- The first build compiles Stockfish from its official source repository.
- This is a starter implementation and has not been verified on every device. Review the Actions build log and test the APK before relying on it.
- Analysis is intended for completed games, not live-game assistance.
- ChessMate AI is independent and is not affiliated with Chess.com.
- See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for license information.
