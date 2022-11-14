package com.appstract.fpex.extra

import cats.effect.IO
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.io._

trait ExtraRoutes {

	val OP_NAME_GREET = "greet-user"
	val OP_NAME_JOKE = "tell-joke"

	val PATH_ROOT: Path = Path.Root

	def jokeRoutes(jokeSupp: JokeSupplier): HttpRoutes[IO] = {
		import com.appstract.fpex.extra.JsonEnc_Joke._
		HttpRoutes.of[IO] {
			case GET -> PATH_ROOT / OP_NAME_JOKE =>
				for {
					joke: Msg_JokeForFrontend <- jokeSupp.getOneJokeIO
					resp <- Ok(joke)
				} yield resp
		}
	}

	def greetingRoutes(greetSupp: GreetingSupplier): HttpRoutes[IO] = {
		// Original example approach - using a for comprehension.
		// Pull encoders into scope for use in encoding greetMsg
		import com.appstract.fpex.extra.JsonEnc_Greeting._
		HttpRoutes.of[IO] {
			case GET -> Root / OP_NAME_GREET / userName =>
				for {
					greetMsg: Msg_Greeting <- greetSupp.greetingForUser(Msg_UserJoined(userName))
					resp: Response[IO] <- Ok(greetMsg)
				} yield resp
		}
	}

	def otherGreetingRoutes(greetSupp: GreetingSupplier): HttpRoutes[IO] = {
		// Same functionality (ALMOST?!) without the for comprehension.
		import com.appstract.fpex.extra.JsonEnc_Greeting._
		val flg_otherOther = true
		HttpRoutes.of[IO] {
			case GET -> Root / OP_NAME_GREET / userName => {
				val joinMsg = Msg_UserJoined(userName)
				val greetMsg_io: IO[Msg_Greeting] = greetSupp.greetingForUser(joinMsg)
				if (flg_otherOther) {
					val otherResp_io: IO[Response[IO]] = greetMsg_io.flatMap(Ok(_))
					otherResp_io
				}
				else {
					val resp_io: IO[Response[IO]] = Ok.apply(greetMsg_io)
					resp_io
				}
			}
		}
	}
}
