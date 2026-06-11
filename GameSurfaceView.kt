// Fix for #34 (Medium conf) Two game-loop threads on one engine after join timeout
fun startGameLoop() {
    // ...
    if (gameLoopThread == null) { // Check if game loop thread is null
        gameLoopThread = GameLoopThread()
        gameLoopThread.start()
    }
    // ...
}