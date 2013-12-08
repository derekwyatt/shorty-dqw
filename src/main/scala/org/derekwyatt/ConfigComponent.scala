package org.derekwyatt

trait ConfigComponent {
  type Configuration
  def config: Configuration
}
