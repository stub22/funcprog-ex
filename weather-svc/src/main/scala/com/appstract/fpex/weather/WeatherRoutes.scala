package com.appstract.fpex.weather

import cats.data.Kleisli
import cats.effect.IO
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.io.Path
import org.http4s.dsl.io._

// Originally based on htt4s-io giter8 skeleton.
object WeatherRoutes {

	val OP_NAME_GREET = "greet-user"
	val OP_NAME_JOKE = "tell-joke"
	val OP_NAME_WEATHER_FAKE = "check-weather-fake"
	val OP_NAME_WEATHER_WPATH = "check-weather-wpath"
	val OP_NAME_WEATHER_WQRY = "check-weather-wquery"

	// Grr.  Many types + vals have these names
	val PATH_ROOT: Path = Path.Root

	def jokeRoutes(jokeSupp: JokeSupplier): HttpRoutes[IO] = {
		import JsonEnc_Joke._
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
		import JsonEnc_Greeting._
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
		import JsonEnc_Greeting._
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
	// Lat,Lon may come in as query parameters.
	private object QPM_Latitude extends QueryParamDecoderMatcher[String]("lat")
	private object QPM_Longitude extends QueryParamDecoderMatcher[String]("lon")
	def forecastRoutes(frcstSupp: ForecastSupplier, flg_useFake : Boolean): HttpRoutes[IO] = {
		import JsonEnc_Forecast._
		// Here is a dummy forecast-maker effect that doesn't need any input from the user's request.
		// For example, if might use a fixed lat-long.
		val fakeForcIO: IO[Msg_Forecast] = if (flg_useFake) frcstSupp.getTotallyFakeForecast else frcstSupp.fetchMostlyFakeForecastIO
		HttpRoutes.of[IO] {
			case GET -> Root / OP_NAME_WEATHER_FAKE => {
				for {
					frcstMsg <- fakeForcIO
					resp: Response[IO] <- Ok(frcstMsg)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_WPATH / latLonTxt => {

				for {
					frcstMsg <- fakeForcIO
					resp: Response[IO] <- Ok(frcstMsg)
				} yield resp
			}
		}
	}


	def composeRoutesIntoKleisli(helloWorldAlg: GreetingSupplier, jokeAlg: JokeSupplier, frcstSupp : ForecastSupplier): Kleisli[IO, Request[IO], Response[IO]] = {
		import cats.implicits._
		val composedRoutes = greetingRoutes(helloWorldAlg) <+>  jokeRoutes(jokeAlg) <+> forecastRoutes(frcstSupp, false)
		composedRoutes.orNotFound
	}

}

/*
  	Definition of "HttpRoutes.of" from from HttpRoutes.scala

    * Lifts a partial function into an [[HttpRoutes]].  The application of the
    * partial function is suspended in `F` to permit more efficient combination
    * of routes via `SemigroupK`.
    *
    * @tparam F the base effect of the [[HttpRoutes]] - Defer suspends evaluation
    * of routes, so only 1 section of routes is checked at a time.
    * @param pf the partial function to lift
    * @return An [[HttpRoutes]] that returns some [[Response]] in an `OptionT[F, *]`
    * wherever `pf` is defined, an `OptionT.none` wherever it is not

  def of[F[_]: Monad](pf: PartialFunction[Request[F], F[Response[F]]]): HttpRoutes[F] =
    Kleisli(req => OptionT(Applicative[F].unit >> pf.lift(req).sequence))

    Ok.apply is defined by

   trait EntityResponseGenerator[F[_], G[_]] extends Any with ResponseGenerator {

	  def apply[A](body: G[A])(implicit F: Monad[F], w: EntityEncoder[G, A]): F[Response[G]] =
    		F.flatMap(liftG(body))(apply[A](_))
 */