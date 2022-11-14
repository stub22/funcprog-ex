package com.appstract.fpex.weather

import cats.data.Kleisli
import cats.effect.IO

import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.io.Path
import org.http4s.dsl.io._

// Originally based on htt4s-io giter8 skeleton.
trait WeatherRoutes {

	val OP_NAME_WEATHER_DUMMY = "check-weather-dummy"
	val OP_NAME_WEATHER_FIXED = "check-weather-fixed"
	val OP_NAME_WEATHER_WPATH = "check-weather-wpath"
	val OP_NAME_WEATHER_WQRY = "check-weather-wquery"

	// Grr.  Many types + vals have these names
	// val PATH_ROOT: Path = Path.Root

	// Lat,Lon may come in as query parameters.
	private object QPM_Latitude extends QueryParamDecoderMatcher[String]("lat")
	private object QPM_Longitude extends QueryParamDecoderMatcher[String]("lon")

	def reportRoutes(frcstSupp: WeatherReportSupplier): HttpRoutes[IO] = {
		import JsonEncoders_Report._

		// FIXME:  So far we only support the latLonPairTxt input format, which is a single String containing
		// latitude,longitude as decimals
		HttpRoutes.of[IO] {
			case GET -> Root / OP_NAME_WEATHER_DUMMY => {
				// Just a frontend plumbing test.  Does not access the backend weather.gov service.
				for {
					wrprtOrErr <- frcstSupp.getFakeWeather
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr) // Ok(wrprtOrErr)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_FIXED => {
				// Real forecast-maker effect, but doesn't use any input from the user's request.
				// Uses some fixed lat-long location.
				for {
					wrprtOrErr <- frcstSupp.fetchWeatherForFixedLocation
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr) //  Ok(wrprtOrErr)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_WPATH / latLonTxt => {
				// Expects a latLon text pair in the request path, in form compatible with weather.gov/points service,
				// Example:  "39.7456,-97.0892"
				for {
					wrprtOrErr <- frcstSupp.fetchWeatherForLatLonPairTxt(latLonTxt)
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr) // Ok(wrprtOrErr)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_WQRY :? QPM_Latitude(latTxt) +& QPM_Longitude(lonTxt) => {
				for {
					wrprtOrErr <- frcstSupp.fetchWeatherForLatLon(latTxt, lonTxt)
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr) //  Ok(wrprtOrErr)
				} yield resp
			}
		}
	}

	def weatherOutputMsgToWebResponse(msg : Either[Msg_WeatherError, Msg_WeatherReport]) : IO[Response[IO]] = {
		import JsonEncoders_Report._
		msg match {

			case Left(err) => Ok(err)
			case Right(report) => Ok(report)
		}
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