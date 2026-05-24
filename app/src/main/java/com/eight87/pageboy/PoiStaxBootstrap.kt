package com.eight87.pageboy

/**
 * Phase I.2 / J.1 — wires the three `org.apache.poi.javax.xml.stream.*`
 * system properties that POI 5.x reads when looking up its StAX
 * factories.
 *
 * Android does not ship the `javax.xml.stream.*` factory APIs the
 * mainstream JVM provides; POI delegates to whatever
 * `XMLInputFactory.newInstance()` returns, which fails-fast on Android
 * with `FactoryConfigurationError`. POI 5.x respects these three
 * `org.apache.poi.*` overrides explicitly so we wire them to the
 * `com.fasterxml.aalto` impl (which Android's stripped runtime CAN host
 * because Aalto ships its own factory implementations rather than
 * relying on the missing JDK `META-INF/services` discovery path).
 *
 * Must run BEFORE any POI class is touched (otherwise the static
 * factory lookup happens against the broken default and POI caches the
 * failure). Called from [PageboyApplication.onCreate]; the
 * [installAaltoOnce] guard makes it safe to call repeatedly from
 * Robolectric tests that don't go through the Application lifecycle.
 */
internal object PoiStaxBootstrap {

  private const val IN_KEY = "org.apache.poi.javax.xml.stream.XMLInputFactory"
  private const val OUT_KEY = "org.apache.poi.javax.xml.stream.XMLOutputFactory"
  private const val EVT_KEY = "org.apache.poi.javax.xml.stream.XMLEventFactory"

  private const val IN_IMPL = "com.fasterxml.aalto.stax.InputFactoryImpl"
  private const val OUT_IMPL = "com.fasterxml.aalto.stax.OutputFactoryImpl"
  private const val EVT_IMPL = "com.fasterxml.aalto.stax.EventFactoryImpl"

  @Volatile
  private var installed: Boolean = false

  /**
   * Idempotent installer. The volatile guard avoids re-setting the
   * properties on every call (cheap, but the guard documents intent —
   * once installed for a process, never re-install).
   */
  fun installAaltoOnce() {
    if (installed) return
    synchronized(this) {
      if (installed) return
      System.setProperty(IN_KEY, IN_IMPL)
      System.setProperty(OUT_KEY, OUT_IMPL)
      System.setProperty(EVT_KEY, EVT_IMPL)
      installed = true
    }
  }
}
