package com.appstract.fpex.h4sio

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {
  def run:IO[Unit] = Http4siog8Server.run
}
