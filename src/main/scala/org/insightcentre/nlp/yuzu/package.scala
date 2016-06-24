package org.insightcentre.nlp

package object yuzu {
  implicit class Opt2Err[A](a : Option[A]) {
    def orErr(str : String) = a.map(Right(_)).getOrElse(Left(str))
  }

  implicit class ErrorMonad[A](a : Either[String, A]) {
    def map[B](f: A => B) : Either[String, B] = a match {
      case x@Left(a) => x.asInstanceOf[Left[String, B]]
      case Right(b) => Right(f(b))
    }
    def flatMap[B](f : A => Either[String, B]) : Either[String, B] = a match {
      case x@Left(a) => x.asInstanceOf[Left[String, B]]
      case Right(b) => f(b)
    }
    def check(f : A => Boolean, err : String) = a match {
      case x@Left(a) => x
      case Right(b) =>
        if(f(b)) {
          a
        } else {
          Left(err)
        }
    }
    def getOrElse[B <: A](b : B) = a match {
      case Left(_) => b
      case Right(a) => a
    }
  }
}
