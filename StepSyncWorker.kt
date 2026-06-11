// Fix for #42 (Medium conf) Data race / ConcurrentModificationException
fun syncSteps() {
    // ...
    synchronized(DailyStepManager.stepList) { // Use synchronized block for atomic update
        // Update steps
    }
    // ...
}