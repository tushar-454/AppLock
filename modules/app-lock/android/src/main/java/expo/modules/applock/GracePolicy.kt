package expo.modules.applock

data class GracePolicy(val enabled: Boolean = true, val durationMs: Long = 30_000L) {
  init { require(durationMs in DURATIONS) { "Unsupported grace duration" } }
  companion object { val DURATIONS = setOf(10_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L) }
}
