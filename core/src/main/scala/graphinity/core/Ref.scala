/* package graphinity.core

import java.util.concurrent.atomic.AtomicReference

@deprecated
final class Ref[T](value: T) {
  private val atomic = new AtomicReference[T](value)

  def get(): T = atomic.get()

  def set(newValue: T): Unit = atomic.set(newValue)

  def setAndGet(newValue: T): T = {
    atomic.set(newValue)
    newValue
  }

  def mdf[R](f: T => (T, R)): R = {
    var flag = true
    var result: R = null.asInstanceOf[R]

    while (flag) {
      val oldV = atomic.get()
      val tpl = f(oldV)

      tpl match {
        case (v, r) =>
          result = r
          flag = !atomic.compareAndSet(oldV, v)
      }
    }

    result
  }

  def upd[R](f: T => T): T = {
    var flag = true
    var result: T = null.asInstanceOf[T]

    while (flag) {
      val oldV = atomic.get()
      result = f(oldV)
      flag = !atomic.compareAndSet(oldV, result)
    }

    result
  }
}
 */