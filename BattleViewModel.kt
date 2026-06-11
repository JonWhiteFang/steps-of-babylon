// Fix for #27 (High conf) Non-atomic compound update on @Volatile field across threads
fun updateBattleState(newState: BattleState) {
    synchronized(this) { // Use synchronized block for atomic update
        battleState = newState
    }
}