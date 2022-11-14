package com.appstract.fpex.weather

import cats.effect.IO
import io.circe.{ACursor, Decoder, DecodingFailure, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder}
import org.http4s.{EntityDecoder, Method, ParseResult, Request, Uri}
import org.http4s.client.Client
import org.log4s
import org.log4s.{Logger}


private object JsonDecoders_BackendAreaInfo {
	// Circe decoder bindings for building Msg_AreaInfo* from JSON tree.
	// These implicits could instead be defined locally in the BackendForecastProviderImpl,
	// but Stu chose to share them in this top-level object, following common practice.
	import org.http4s.circe.{jsonOf}

	implicit val areaPropsDecoder: Decoder[Msg_BackendAreaProps] = deriveDecoder[Msg_BackendAreaProps]
	implicit val areaInfoDecoder: Decoder[Msg_BackendAreaInfo] = deriveDecoder[Msg_BackendAreaInfo]
	implicit val areaInfoEntityDecoder: EntityDecoder[IO, Msg_BackendAreaInfo] = jsonOf
}


class BackendForecastProviderImpl(dataSrcCli: => Client[IO]) extends BackendForecastProvider {

	private val myLog: Logger = log4s.getLogger

	private val myRequestBuilder = new BackendRequestBuilder {}
	private val myForecastExtractor = new PeriodForecastExtractor {}

	override def fetchForecastInfoForLatLonTxt (latLonPairTxt : String) : IO[Msg_BackendPeriodForecast] = {
		val areaRq: IO[Request[IO]] = myRequestBuilder.areaGetRequest(latLonPairTxt)
		val forecastInfo: IO[Msg_BackendPeriodForecast] = fetchAreaInfoThenForecastInfo(areaRq)
		forecastInfo
	}

	private def fetchAreaInfoThenForecastInfo (areaRq: IO[Request[IO]]) : IO[Msg_BackendPeriodForecast] = {
		// Each of these steps is suspended and may encounter errors.  Notice that each LHS is wrapped in IO.
		// We could instead use a for-comprehension, but this way the types are fully explicit at each step.
		// If we had more step permutations to manage, we might use a Kleisli approach.
		val areaInfo: IO[Msg_BackendAreaInfo] = areaRq.flatMap(fetchAreaInfoOrError(_))
		val forecastRq: IO[Request[IO]] = areaInfo.flatMap(myRequestBuilder.buildForecastRqFromAreaInfo(_))
		val forecastInfo: IO[Msg_BackendPeriodForecast] = forecastRq.flatMap(fetchCurrentForecastPeriodOrError(_))
		forecastInfo
	}

	override def fetchAreaInfoOrError(areaRq : Request[IO]) : IO[Msg_BackendAreaInfo] = {
		import cats.implicits._
		import JsonDecoders_BackendAreaInfo._
		val rqTxt = areaRq.toString
		myLog.info(s"fetchAreaInfoOrError for areaRq=${rqTxt}")
		// Submit Area request, expecting a Json response-body, which we decode into AreaInfo message, or an error.
		val areaResultIO: IO[Msg_BackendAreaInfo] = dataSrcCli.expect[Msg_BackendAreaInfo](areaRq)
		// Map any exception thrown (during HTTP fetch+decode) into a Msg_BackendError, after logging it
		val robustAreaIO: IO[Msg_BackendAreaInfo] = areaResultIO.adaptError {
			case t => {
				myLog.error(t)(s"fetchAreaInfoOrError for ${rqTxt} is handling throwable of type " + t.getClass)
				Msg_BackendError("fetchAreaInfoOrError", rqTxt, t)
			}
		}
		robustAreaIO
	}

	private def fetchCurrentForecastPeriodOrError(forecastRq : Request[IO]) : IO[Msg_BackendPeriodForecast] = {
		import cats.implicits._
		// Bring generic EntityDecoder[Json] implicits into scope
		import org.http4s.circe._
		// Submit forecast request, expecting a Json response-body.  Note that we do NOT ask circe to map this
		// body directly into our Msg_ types.  Compare with the fetchAreaInfo method above.
		val rqTxt = forecastRq.toString
		myLog.info(s"fetchCurrentForecastPeriodOrError forecastRq=${rqTxt}")

		val forecastJson: IO[Json] = dataSrcCli.expect[Json](forecastRq)
		// Map any exception thrown (during HTTP fetch+decode) into a Msg_BackendError, after logging it
		val robustForecastJson: IO[Json] = forecastJson.adaptError {
			case t => {
				myLog.error(t)(s"fetchCurrentForecastPeriodOrError for ${rqTxt} is handling throwable of type ${t.getClass}")
				Msg_BackendError("fetchCurrentForecastPeriodOrError", rqTxt, t)
			}
		}
		// Extract useful forecast period(s) from the backend JSON
		val periodForecastIO = robustForecastJson.flatMap(myForecastExtractor.extractFirstPeriodForecast(_))
		periodForecastIO
	}

}

private trait BackendRequestBuilder {
	val POINTS_BASE_URL = "https://api.weather.gov/points/"

	def areaGetRequest(latLonTxt : String) : IO[Request[IO]] = {
		val fullUriTxt = POINTS_BASE_URL + latLonTxt
		totalGetUriRequestWithBodyEffectIO(fullUriTxt)
	}

	def buildForecastRqFromAreaInfo(areaInfo : Msg_BackendAreaInfo) : IO[Request[IO]] = {
		// Extract forecastURL from AreaInfo.  Note that these field names match the expected Json.
		val forcUrlTxt : String = areaInfo.properties.forecast
		// Build a new request using the forecastURL, or an error e.g. if the URL is bad.
		val forcRq: IO[Request[IO]] = totalGetUriRequestWithBodyEffectIO(forcUrlTxt)
		forcRq
	}
	// For a simple GET query, where the uri-parse might fail, with that error suspended in the outer IO.
	private def totalGetUriRequestWithBodyEffectIO(uriTxt : String) : IO[Request[IO]] = {
		val totalUri: ParseResult[Uri] = Uri.fromString(uriTxt) //   type ParseResult[+A] = Either[ParseFailure, A]
		val x: IO[Uri] = IO.fromEither(totalUri)
		val rqio: IO[Request[IO]] = x.map(uri => Request[IO](Method.GET, uri))
		rqio
	}
}

trait PeriodForecastExtractor {
	/***
	 * Logically and semantically we have some ambiguity here.
	 * Note that the user may be querying from anywhere, for any location, at any time.
	 * We have not studied the backend API contract thoroughly.
	 * Empirically, the backend seems to provide a time-ordered sequence of forecast periods, alternating between
	 * "daytime" and "nightime" (indicated by the 'isDaytime' flag in the JSON response).
	 *
	 * We have so far been asked to supply only a toy current-weather feature without much detail.
	 * To simplify coding we have chosen to use only the most current forecast period, regardless of day/night.
	 * However we do capture the isDaytime flag, so that client code may interpret the weather accordingly.
	 * If desired we could build a larger data structure to capture multiple periods, but for now
	 * we assume that returning a single period forecast is sufficient.
	 */

	private val myLog: Logger = log4s.getLogger
	private val FLD_PROPS = "properties"
	private val FLD_PERIODS = "periods"

	// We need only this one circe decoder.  The rest of our work is done with explicit circe cursor navigation.
	private implicit val periodForecastDecoder: Decoder[Msg_BackendPeriodForecast] = deriveDecoder[Msg_BackendPeriodForecast]

	def extractFirstPeriodForecast(forecastJson : Json) : IO[Msg_BackendPeriodForecast]  = {
		val flg_dumpJson = false
		if (flg_dumpJson) {
			val jsonTxt = forecastJson.spaces4
			myLog.info(s"Extracing first period block from forecast-json: ${jsonTxt}")
		}
		// First navigate to the periods array at    ROOT.properties.periods
		val rootCursor: HCursor = forecastJson.hcursor
		val propCursor = rootCursor.downField(FLD_PROPS)
		val periodsCursor = propCursor.downField(FLD_PERIODS)
		// We assume there are at least 2 periods, one of which will be for Daytime, the other for NightTime.
		// But we don't know which one will be first/current, because we don't know if it is currently
		// day or night at the forecast location (as reported by the api.weather.gov service).
		val pCurs0: ACursor = periodsCursor.downN(0)

		val p0JsonTXT = pCurs0.focus.map(_.spaces4).getOrElse({"No json found at period[0]"})
		myLog.info(s"period[0].history: ${pCurs0.history}")
		myLog.info(s"period[0].json: ${p0JsonTXT}")
		val p0Json: Option[Json] = pCurs0.focus
		//   final type Result[A] = Either[DecodingFailure, A]
		// Use our implicit periodForecastDecoder, defined above.
		val p0Rec_orErr: Decoder.Result[Msg_BackendPeriodForecast] = p0Json.map(_.as[Msg_BackendPeriodForecast])
					.getOrElse(Left(DecodingFailure("Could not focus on period[0]", pCurs0.history)))

		val decoded0_IO: IO[Msg_BackendPeriodForecast] = IO.fromEither(p0Rec_orErr)
		val flg_doUnusedManualCaptureOfSecondPeriod = true
		if (flg_doUnusedManualCaptureOfSecondPeriod) {
			// Testing that we can fetch multiple periods, and also that the manual capture code works.
			val pCurs1 = periodsCursor.downN(1)
			val p1Rec_orErr = manuallyDecodeForecastPeriod(pCurs1)
		}
		decoded0_IO
	}

	// FIXME:  All the code below is unnecessary because  .as[Msg_BackendPeriodForecast] works just fine.

	private val FLD_IS_DAY = "isDaytime"
	private val FLD_TEMP = "temperature"
	private val FLD_TEMP_UNIT = "temperatureUnit"
	private val FLD_SHORT_FORE = "shortForecast"
	private val FLD_DETAILED_FORE = "detailedForecast"
	private def manuallyDecodeForecastPeriod(periodCursor: ACursor) : Decoder.Result[Msg_BackendPeriodForecast] = {
		// typeResult[A] = Either[DecodingFailure, A]
		val forcInf: Either[DecodingFailure, Msg_BackendPeriodForecast] = for {
			// Each of these "get" fetchers returns an Option
			isDay <- periodCursor.get[Boolean](FLD_IS_DAY)
			// Expect temperatureDescription to be an Int
			temp <- periodCursor.get[Int](FLD_TEMP)
			// Expect temperatureDescription-unit to be a single char ('F' or 'C')
			tempUnit <- periodCursor.get[Char](FLD_TEMP_UNIT)
			shortFore <- periodCursor.get[String](FLD_SHORT_FORE)
			detailedFore <- periodCursor.get[String](FLD_DETAILED_FORE)
		} yield Msg_BackendPeriodForecast(isDay, temp, tempUnit, shortFore, detailedFore)
		myLog.info(s"decodeInfoManually at periodCursor=${periodCursor} produced: ${forcInf}")
		forcInf
	}
}

/***

trait Client[F[_]] {
  def runOld(req: Request[F]): Resource[F, Response[F]]

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
 			...

 Common error response from gridpoints is:

 {
    "correlationId": "1ed872c0",
    "title": "Unexpected Problem",
    "type": "https://api.weather.gov/problems/UnexpectedProblem",
    "status": 500,
    "detail": "An unexpected problem has occurred.",
    "instance": "https://api.weather.gov/requests/1ed872c0"
}
 */