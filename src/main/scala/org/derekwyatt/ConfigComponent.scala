package org.derekwyatt

/**
 * Abstracts the notion of a configuration object from the component being
 * configured.  The idea was ripped off from Daniel Spiewak in his article
 * entitled "Existential Types FTW".
 *
 * You would use it like this:
 *
 * {{{
 * trait MyConfigComponent extends ConfigComponent {
 *   type Configuration <: MyConfig
 *
 *   trait MyConfig {
 *     val someConfigVal: String
 *     val someOtherConfigVal: Int
 *   }
 * }
 *
 * trait MyComponent { this: MyConfigComponent =>
 *   def doThing(): String = config.someConfigVal
 *   def doOtherThing(): Int = config.someOtherConfigVal
 * }
 *
 * trait AnotherConfigComponent extends ConfigComponent {
 *   type Configuration <: AnotherConfig
 *
 *   trait AnotherConfig {
 *     val someFloat: Float
 *   }
 * }
 *
 * trait AnotherComponent { this: AnotherConfigComponent =>
 *   def another(): Float = config.someFloat
 * }
 * }}}
 *
 * And then use it later:
 *
 * {{{
 * class ProductionConfiguration extends MyComponent
 *                                  with AnotherComponent
 *                                  with MyConfigComponent
 *                                  with AnotherConfigComponent { 
 *
 *   type Configuration = MyConfig with AnotherConfig
 *
 *   object config extends MyConfig with AnotherConfig {
 *     val someConfigVal = "Configured"
 *     val someOtherConfigVal = 42
 *     val someFloat 42.0f
 *   }
 * }
 * }}}
 *
 * Everyone gets to see their slices of the "thickly typed" `config` object
 * without having to worry about what everyone else is interested in.  Of
 * course, this can create name clashes, but the compiler will pick those up,
 * and eliminating them before hand with another level of indirection is a pain.
 * Best just not to clash your names :)
 */
trait ConfigComponent {
  type Configuration
  def config: Configuration
}
