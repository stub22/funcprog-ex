package com.appstract.fpex.extra

import cats.effect.IO
import cats.implicits._
import io.circe.{Encoder, Json}
import org.http4s.EntityEncoder
import org.http4s.circe._


final case class Msg_UserJoined(nameTxt: String) extends AnyVal
final case class Msg_Greeting(messageTxt: String) extends AnyVal

trait GreetingSupplier {
	def greetingForUser(n: Msg_UserJoined): IO[Msg_Greeting]
}

object JsonEnc_Greeting {
	val FIELD_NAME_MSG_TXT = "messageTxt"
	implicit val greetingEncoder: Encoder[Msg_Greeting] = new Encoder[Msg_Greeting] {
		final def apply(a: Msg_Greeting): Json = {
			val kvPair_msgTxt = (FIELD_NAME_MSG_TXT, Json.fromString(a.messageTxt))
			val jsonObj = Json.obj(kvPair_msgTxt)
			jsonObj
		}
	}
	implicit val greetingEntityEncoder: EntityEncoder[IO, Msg_Greeting] =
		jsonEncoderOf[IO, Msg_Greeting]
}

// Here we are using a singleton, to getOneJokeIO the convenience of sharing some constants.
// However in general we use traits for factories.
trait GreetingSupplierFactory {
	val GREETING_TEXT_PREFIX = "Welcome to WeatherTown, "
	def getImpl: GreetingSupplier = new GreetingSupplier {
		override def greetingForUser(usrNm: Msg_UserJoined): IO[Msg_Greeting] =
			Msg_Greeting(GREETING_TEXT_PREFIX + usrNm.nameTxt).pure[IO]
	}
}

object GreetingSupplierSingleton extends GreetingSupplierFactory