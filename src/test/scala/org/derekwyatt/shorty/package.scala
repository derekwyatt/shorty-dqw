package org.derekwyatt

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

package object shorty {
  def executionContext(numThreads: Int = 1) = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(numThreads))
}
