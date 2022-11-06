package com.appstract.fpex.weather

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

object WeatherRoutes {

  // FIXME:  This object is renamed, but the contents are still from htt4s-io giter8 skeleton.
  def jokeRoutes(J: Jokes): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {
      case GET -> Root / "joke" =>
        for {
          joke <- J.get
          resp <- Ok(joke)
        } yield resp
    }
  }

  def helloWorldRoutes(H: HelloWorld): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }
}
