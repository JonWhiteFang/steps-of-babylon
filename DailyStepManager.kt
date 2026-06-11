// Fix for #42 (Medium conf) Data race / ConcurrentModificationException
private val stepList = CopyOnWriteArrayList<Step>() // Use thread-safe list

fun addStep(step: Step) {
    stepList.add(step)
}

fun removeStep(step: Step) {
    stepList.remove(step)
}