package object `type` {
  type ObjectToString[T] = T => String
  type StringToObject[T] = String => T
  type Predicate[T] = T => Boolean
}
