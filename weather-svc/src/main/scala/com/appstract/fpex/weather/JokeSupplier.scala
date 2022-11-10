package com.appstract.fpex.weather

import cats.effect.IO
import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.http4s.Method._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import org.log4s
import org.log4s.Logger

// AnyVal => value class needs to have exactly one val parameter
final case class Msg_JokeForFrontend(jokeTxt: String) extends AnyVal // Is AnyVal clue used by Circe?
final case class Msg_JokeFromBackend(joke: String, id : String)
final case class Msg_JokeError(e: Throwable) extends RuntimeException

trait JokeSupplier{
  def getOneJokeIO: IO[Msg_JokeForFrontend]
}

object JsonEnc_Joke {
  implicit val jokeDecoder: Decoder[Msg_JokeFromBackend] = deriveDecoder[Msg_JokeFromBackend]
  implicit val jokeEntityDecoder: EntityDecoder[IO, Msg_JokeFromBackend] = jsonOf
  implicit val jokeEncoder: Encoder[Msg_JokeForFrontend] = deriveEncoder[Msg_JokeForFrontend]
  implicit val jokeEntityEncoder: EntityEncoder[IO, Msg_JokeForFrontend] = jsonEncoderOf
}

trait JokeSupplierFactory {
  lazy val myLog: Logger = log4s.getLogger

  def getImpl(dataSrcCli: Client[IO]): JokeSupplier = new JokeSupplier {
    import JsonEnc_Joke._
    override def getOneJokeIO: IO[Msg_JokeForFrontend] = {
      val dataRequest: Request[IO] = GET(uri"https://icanhazdadjoke.com/")
      val dataResultIO: IO[Msg_JokeFromBackend] = dataSrcCli.expect[Msg_JokeFromBackend](dataRequest)
      val robustResultIO : IO[Msg_JokeFromBackend] = dataResultIO.adaptError{ case t => Msg_JokeError(t)} // Prevent Client Json Decoding Failure Leaking
      val msgForFrontendIO: IO[Msg_JokeForFrontend] = robustResultIO.map(fromBack => Msg_JokeForFrontend(fromBack.joke))
      myLog.info(s"getOneJokeIO got dataResultIO=${dataResultIO}, msgForFrontendIO=${msgForFrontendIO}")
      msgForFrontendIO
    }
  }
}
