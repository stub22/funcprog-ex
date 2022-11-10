package com.appstract.fpex.weather

import cats.effect.IO
import fs2.Pure
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.http4s.Method.GET
import org.http4s.{EntityDecoder, EntityEncoder, Method, ParseFailure, ParseResult, Request, Uri}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.client.Client
import org.log4s
import org.log4s.{Logger, getLogger}

private trait Forecasts

// Our output message for our user:
case class Msg_Forecast(lat : String, lng : String, summary : String, temp : String)

// AreaInfo types produced by backend weather service "points", which returns the enclosing grid area for a lat+long pair.
// These field names match the fields in the JSON message.  We include only the fields we are interested in.
// We expect an AreaInfo entity in the HTTP response, encoded as Json.
// The AreaInfo json contains a "properties" map, containing a "forecast" value, which is a URL for local forecast info.
case class Msg_AreaInfoFromBackend(properties : Msg_AreaPropsFromBackend)	// Entity type returned from "points"
case class Msg_AreaPropsFromBackend(forecast : String)	// forecast is the URL

// ForecastInfo types from backend weather service "gridpoints/TOP".
case class Msg_ForecastInfoFromBackend()

final case class Msg_ForecastError(e: Throwable) extends RuntimeException

trait ForecastSupplier {
	def getTotallyFakeForecast : IO[Msg_Forecast]

	def fetchMostlyFakeForecastIO : IO[Msg_Forecast]
}
object JsonEnc_Forecast {
	implicit val areaPropsDecoder: Decoder[Msg_AreaPropsFromBackend] = deriveDecoder[Msg_AreaPropsFromBackend]
	implicit val areaInfoDecoder: Decoder[Msg_AreaInfoFromBackend] = deriveDecoder[Msg_AreaInfoFromBackend]
	implicit val areaInfoEntityDecoder: EntityDecoder[IO, Msg_AreaInfoFromBackend] = jsonOf

	implicit val forecastEncoder: Encoder[Msg_Forecast] = deriveEncoder[Msg_Forecast]
	implicit val forecastEntityEncoder: EntityEncoder[IO, Msg_Forecast] = jsonEncoderOf
}

trait ForecastSupplierFactory {
	import cats.implicits._
	import org.http4s.implicits._

	lazy val myLog: Logger = log4s.getLogger

	def getImpl(dataSrcCli: Client[IO]): ForecastSupplier = new ForecastSupplier {
		import JsonEnc_Forecast._
		override def getTotallyFakeForecast: IO[Msg_Forecast] = {
			val fakeForecastMsg = Msg_Forecast("FAKE_LAT", "FAKE_LONG", "FAKE_SUMMARY", "FAKE_TEMP")
			IO.pure(fakeForecastMsg)
		}
		override def fetchMostlyFakeForecastIO: IO[Msg_Forecast] = {
			// val withQuery: Uri = withPath.withQueryParam("hello", "world")
			val latLongTxtIO : IO[String] = IO.pure("39.7456,-97.0892")
			// https://http4s.org/v0.23/docs/client.html#constructing-a-uri
			import org.http4s.client.dsl.io._	// Pulls in the uri macro
			// "This only works with literal strings because it uses a macro to validate the URI format at compile-time."
			val areaRequestIO: Request[IO] = GET(uri"https://api.weather.gov/points/39.7456,-97.0892")
			// val withQuery: Uri = withPath.withQueryParam("hello", "world")
			val areaResultIO: IO[Msg_AreaInfoFromBackend] = dataSrcCli.expect[Msg_AreaInfoFromBackend](areaRequestIO)
			val robustAreaIO : IO[Msg_AreaInfoFromBackend] = areaResultIO.adaptError{ case t => Msg_ForecastError(t)}
			val forcUrlIO: IO[String] = robustAreaIO.map(_.properties.forecast)

			val fakeForcIO: IO[Msg_Forecast] = robustAreaIO.map(ainf => {
				val msg = Msg_Forecast("GeeWow", ainf.toString, ainf.properties.toString, ainf.properties.forecast)
				myLog.info(s"fetchMostlyFakeForecastIO built dummy output msg: ${msg}")
				msg
			})

			fakeForcIO
		}

		// latLonTxt is in the comma separated lat-long format used by the backend weather service, e.g. "39.7456,-97.0892"
		def fetchAreaInfoThenForecastInfo (latLonTxt : String) : IO[Msg_ForecastInfoFromBackend] = {
			// Each of these steps may encounter errors.
			val areaRq: IO[Request[IO]] = areaGetRequest(latLonTxt)
			val areaInfo: IO[Msg_AreaInfoFromBackend] = areaRq.flatMap(fetchAreaInfoOrError(_))
			val forecastRq: IO[Request[IO]] = areaInfo.flatMap(buildForecastRqFromAreaInfo(_))
			val forecastInfo: IO[Msg_ForecastInfoFromBackend] = forecastRq.flatMap(fetchForecastInfoOrError(_))
			forecastInfo
		}
		def areaGetRequest(latLonTxt : String) : IO[Request[IO]] = {
			val fullUriTxt = "https://api.weather.gov/points/" + latLonTxt
			totalGetUriRequestWithBodyEffectIO(latLonTxt)
		}
		def buildForecastRqFromAreaInfo(areaInfoIO : IO[Msg_AreaInfoFromBackend]) : IO[Request[IO]]= {
			// Extract forecastURL from AreaInfo.
			val forcUrlIO: IO[String] = areaInfoIO.map(_.properties.forecast)
			// Build a new request using the forecastURL.
			val forcRqIO : IO[Request[IO]] = forcUrlIO.flatMap(uriTxt => totalGetUriRequestWithBodyEffectIO(uriTxt))
			forcRqIO
		}
		def fetchAreaInfoOrError(areaRq : Request[IO]) : IO[Msg_AreaInfoFromBackend] = {
			// Submit Area request, expecting a Json response-body, which we decode into AreaInfo message, or an error.
			val areaResultIO: IO[Msg_AreaInfoFromBackend] = dataSrcCli.expect[Msg_AreaInfoFromBackend](areaRq)
			val robustAreaIO: IO[Msg_AreaInfoFromBackend] = areaResultIO.adaptError { case t => Msg_ForecastError(t) }
			robustAreaIO
		}
		def buildForecastRqFromAreaInfo(areaInfo : Msg_AreaInfoFromBackend) : IO[Request[IO]] = {
			// Extract forecastURL from AreaInfo.  Note that these field names match the expected Json.
			val forcUrlTxt : String = areaInfo.properties.forecast
			// Build a new request using the forecastURL, or an error e.g. if the URL is bad.
			val forcRq: IO[Request[IO]] = totalGetUriRequestWithBodyEffectIO(forcUrlTxt)
			forcRq
		}
		def fetchForecastInfoOrError(forcRq : Request[IO]) : IO[Msg_ForecastInfoFromBackend] = {
			// Bring implicit EntityDecoder[Json] into our scope:
			import org.http4s.circe._
			// Submit forecast request, expecting a Json response-body.
			val forecastJson: IO[Json] = dataSrcCli.expect[Json](forcRq)
			// Map any exception thrown (during HTTP fetch) into a Msg_ForecastError
			val robustForecastJson = forecastJson.adaptError { case t => Msg_ForecastError(t) }
			val forecastInfo: IO[Msg_ForecastInfoFromBackend] = robustForecastJson.flatMap(decodeJsonForecast(_))
			forecastInfo
		}

		def decodeJsonForecast(forecastJson : Json) : IO[Msg_ForecastInfoFromBackend]  = {
			val jsonTxt = forecastJson.spaces4
			myLog.info(s"Pretending to decode forecast-json: ${jsonTxt}")
			val forcInfo = Msg_ForecastInfoFromBackend()
			IO.pure(forcInfo)
		}

		private def mkDummyAreaRqIO : Request[IO] = {
			// https://http4s.org/v0.23/docs/client.html#constructing-a-uri
			import org.http4s.client.dsl.io._ // Pulls in the uri macro
			// "This only works with literal strings because it uses a macro to validate the URI format at compile-time."
			val areaRequestIO: Request[IO] = GET(uri"https://api.weather.gov/points/39.7456,-97.0892")
			areaRequestIO
		}
		// For a simple GET query (with no ENTITY in the REQUEST, hence the inner Pure) where the uri-parse might fail, with
		// that error suspended in the outer IO.
		def totalGetUriRequestWithBodyEffectPure(uriTxt : String) : IO[Request[Pure]] = {
			val totalUri: ParseResult[Uri] = Uri.fromString(uriTxt) //   type ParseResult[+A] = Either[ParseFailure, A]
			val x: IO[Uri] = IO.fromEither(totalUri)
			val rqio: IO[Request[Pure]] = x.map(uri => Request[Pure](Method.GET, uri))
			rqio
		}
		def totalGetUriRequestWithBodyEffectIO(uriTxt : String) : IO[Request[IO]] = {
			val totalUri: ParseResult[Uri] = Uri.fromString(uriTxt) //   type ParseResult[+A] = Either[ParseFailure, A]
			val x: IO[Uri] = IO.fromEither(totalUri)
			val rqio: IO[Request[IO]] = x.map(uri => Request[IO](Method.GET, uri))
			rqio
		}

		def unusedThing(robustAreaIO: IO[Msg_AreaInfoFromBackend]) : Unit = {
			// IORequest[IO]
			val weathRqIO: IO[ParseResult[Uri]] = robustAreaIO.map(ainf => {
				val weatherUrlTxt : String = ainf.properties.forecast
				val errOrUrl: ParseResult[Uri] = Uri.fromString(weatherUrlTxt)	//   type ParseResult[+A] = Either[ParseFailure, A]
				errOrUrl
			})
		}

	}
}

trait BackendWeather {
	// https://api.weather.gov/points/39.7456,-97.0892
	//  "properties": {  "forecast": "https://api.weather.gov/gridpoints/TOP/31,80/forecast",
	// https://api.weather.gov/gridpoints/TOP/31,80/forecast
	//	"properties": {	 "periods": [
}
/*
Write an HTTP server that serves the current weather. Your server should expose an endpoint that:
1. Accepts latitude and longitude coordinates
2. Returns the short forecast for that area for Today (“Partly Cloudy” etc)
3. Returns a characterization of whether the temperature is “hot”, “cold”, or “moderate” (use your
discretion on mapping temperatures to each type)
4. Use the National Weather Service API Web Service as a data source.

 */
/***

trait Client[F[_]] {
  def run(req: Request[F]): Resource[F, Response[F]]

"properties": {
      "periods": [
             {
                "number": 1,
                "name": "Tonight",
                "startTime": "2022-11-08T23:00:00-06:00",
                "endTime": "2022-11-09T06:00:00-06:00",
                "isDaytime": false,
                "temperature": 63,
                "temperatureUnit": "F",
                "temperatureTrend": null,
                "windSpeed": "15 mph",
                "windDirection": "S",
                "icon": "https://api.weather.gov/icons/land/night/ovc?size=medium",
                "shortForecast": "Cloudy",
                "detailedForecast": "Cloudy, with a low around 63. South wind around 15 mph, with gusts as high as 35 mph."
            },
            {
                "number": 2,
                "name": "Wednesday",
                "startTime": "2022-11-09T06:00:00-06:00",
                "endTime": "2022-11-09T18:00:00-06:00",
                "isDaytime": true,
                "temperature": 74,
                "temperatureUnit": "F",
                "temperatureTrend": null,
                "windSpeed": "15 to 25 mph",
                "windDirection": "S",
                "icon": "https://api.weather.gov/icons/land/day/wind_bkn?size=medium",
                "shortForecast": "Mostly Cloudy",
                "detailedForecast": "Mostly cloudy, with a high near 74. South wind 15 to 25 mph, with gusts as high as 40 mph."
            },
 */