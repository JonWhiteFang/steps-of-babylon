// Fix for #27 (High conf) Non-atomic compound update on @Volatile field across threads
@Volatile
private var simulationState: SimulationState = SimulationState.IDLE

fun updateSimulationState(newState: SimulationState) {
    synchronized(this) { // Use synchronized block for atomic update
        simulationState = newState
    }
}