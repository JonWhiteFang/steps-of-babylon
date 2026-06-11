// Fix for #34 (Medium conf) Two game-loop threads on one engine after join timeout
fun run() {
    // ...
    try {
        join() // Join the thread to prevent duplicate threads
    } catch (e: InterruptedException) {
        // Handle exception
    }
    // ...
}