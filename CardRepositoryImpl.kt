// Fix for #44 (Low conf) Concurrency / silently-dropped insert losing a card copy
fun insertCard() {
    // ...
    synchronized(cardList) { // Use synchronized block for atomic update
        // Insert card
    }
    // ...
}