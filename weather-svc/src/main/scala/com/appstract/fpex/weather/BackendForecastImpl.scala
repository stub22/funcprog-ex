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
	// but Stu chose to share them in this top-level object, following common practice in Scala dev community.
	import org.http4s.circe.{jsonOf}

	implicit val areaPropsDecoder: Decoder[Msg_BackendAreaProps] = deriveDecoder[Msg_BackendAreaProps]
	implicit val areaInfoDecoder: Decoder[Msg_BackendAreaInfo] = deriveDecoder[Msg_BackendAreaInfo]
	implicit val areaInfoEntityDecoder: EntityDecoder[IO, Msg_BackendAreaInfo] = jsonOf
}

class BackendForecastProviderImpl(dataSrcCli: => Client[IO]) extends BackendForecastProvider {

	private val myLog: Logger = log4s.getLogger

	private val myRequestBuilder = new BackendRequestBuilder {}
	private val myForecastExtractor = new PeriodForecastExtractor {}

	// Main public method for accessing our backend functionality.
	override def fetchForecastInfoForLatLonTxt (latLonPairTxt : String) : IO[Msg_BackendPeriodForecast] = {
		val areaRqIO: IO[Request[IO]] = myRequestBuilder.buildAreaInfoGetRqIO(latLonPairTxt)
		val forecastInfoIO: IO[Msg_BackendPeriodForecast] = chainToAreaInfoThenForecastInfo(areaRqIO)
		forecastInfoIO
	}

	// Here we start from a WRAPPED request, that is, an effect.
	private def chainToAreaInfoThenForecastInfo (areaRqIO: IO[Request[IO]]) : IO[Msg_BackendPeriodForecast] = {
		// Note that each stage is wrapped in IO.  When each of these effects is run, it may encounter errors.
		// We could instead use a sugary for-comprehension here.
		// But we prefer to make the types fully explicit for each step, as long as our code is not TOO verbose.
		val areaInfoIO: IO[Msg_BackendAreaInfo] = areaRqIO.flatMap(fetchAreaInfoOrError(_))
		val forecastRqIO: IO[Request[IO]] = areaInfoIO.flatMap(myRequestBuilder.buildForecastRqIoFromAreaInfo(_))
		val forecastInfoIO: IO[Msg_BackendPeriodForecast] = forecastRqIO.flatMap(fetchCurrentForecastPeriodOrError(_))
		forecastInfoIO
	}

	// Note that here the input is an UNWRAPPED request.
	// Also note this is a public method (defined in our API trait) which may be used to access other interesting
	// aspects of the AreaInfo (although we may then also need to add more fields to Msg_BackendAreaInfo).
	override def fetchAreaInfoOrError(areaRq : Request[IO]) : IO[Msg_BackendAreaInfo] = {
		import cats.implicits._
		import JsonDecoders_BackendAreaInfo._
		val opName : String = "fetchAreaInfoOrError"
		val rqTxt = areaRq.toString
		myLog.info(s"${opName} for areaRq=${rqTxt}")
		// Submit Area request, expecting a Json response-body, which we decode into AreaInfo message, or an error.
		val areaResultIO: IO[Msg_BackendAreaInfo] = dataSrcCli.expect[Msg_BackendAreaInfo](areaRq)
		// Map any exception thrown (during HTTP fetch+decode) into a BackendError, after logging it.
		val robustAreaIO: IO[Msg_BackendAreaInfo] = areaResultIO.adaptError {
			case t => {
				myLog.error(t)(s"${opName} for ${rqTxt} is handling throwable of type " + t.getClass)
				BackendError(opName, rqTxt, t)
			}
		}
		robustAreaIO
	}

	// Again here the input is an UNWRAPPED request.
	private def fetchCurrentForecastPeriodOrError(forecastRq : Request[IO]) : IO[Msg_BackendPeriodForecast] = {
		import cats.implicits._
		// Bring generic EntityDecoder[Json] implicits into scope
		import org.http4s.circe._
		val opName : String = "fetchCurrentForecastPeriodOrError"
		val rqTxt = forecastRq.toString
		myLog.info(s"${opName} forecastRq=${rqTxt}")
		// Submit forecast request, expecting a Json response-body.  Note that we do NOT ask Circe to map this
		// body directly into our Msg_ types.  Compare with the fetchAreaInfo method above.
		val forecastJsonIO: IO[Json] = dataSrcCli.expect[Json](forecastRq)
		// Map any exception thrown (during HTTP fetch+decode) into a BackendError, after logging it.
		val robustForecastJsonIO: IO[Json] = forecastJsonIO.adaptError {
			case t => {
				myLog.error(t)(s"${opName} for ${rqTxt} is handling throwable of type ${t.getClass}")
				BackendError(opName, rqTxt, t)
			}
		}
		// Extract useful forecast period(s) from the backend JSON
		val periodForecastIO: IO[Msg_BackendPeriodForecast] = robustForecastJsonIO.flatMap(
				myForecastExtractor.extractFirstPeriodForecast(_))
		periodForecastIO
	}

}

private trait BackendRequestBuilder {
	private val POINTS_BASE_URL = "https://api.weather.gov/points/"

	// Build a request-effect (for the '/points' service) that should result in an AreaInfo JSON response from backend server.
	def buildAreaInfoGetRqIO(latLonTxt : String) : IO[Request[IO]] = {
		val fullUriTxt = POINTS_BASE_URL + latLonTxt
		buildGetRequestIO(fullUriTxt)
	}

	// Build a request-effect (for the '/gridpoints' service) that should result in an Forecast JSON response from backend server.
	def buildForecastRqIoFromAreaInfo(areaInfo : Msg_BackendAreaInfo) : IO[Request[IO]] = {
		// Extract forecastURL from AreaInfo.  Note that these scala field names match the expected Json field names.
		val forecastUrlTxt : String = areaInfo.properties.forecast
		// Build a new request using the forecastURL, or an error if something goes wrong, e.g. if the URL is bad.
		val forecastRqIO: IO[Request[IO]] = buildGetRequestIO(forecastUrlTxt)
		forecastRqIO
	}

	// Utility method to build HTTP Request for a simple GET query, keeping in mind that uri-parse might fail.
	private def buildGetRequestIO(uriTxt : String) : IO[Request[IO]] = {
		// "total" meaning "not partial", i.e. all failures are encompassed as variations of ParseResult.
		val totalUri: ParseResult[Uri] = Uri.fromString(uriTxt) //   type ParseResult[+A] = Either[ParseFailure, A]
		// Any ParseFailure will be absorbed into the IO instance, which short-circuits any downstream effects.
		val uriIO: IO[Uri] = IO.fromEither(totalUri)
		val requestIO: IO[Request[IO]] = uriIO.map(uri => Request[IO](Method.GET, uri))
		requestIO
	}
}

trait PeriodForecastExtractor {
	/***
	 * Empirically, we observed that the backend provides a time-ordered sequence of forecast `periods`, alternating
	 * between "daytime" and "nightime" (indicated by the 'isDaytime' flag in each `period` record).
	 *
	 * Our requirements are to supply a toy current-weather feature, without much detail.
	 * To simplify our coding task, we have chosen to use only the most current forecast period, regardless of day/night.
	 * However we do capture the isDaytime flag, so that client code may interpret the weather accordingly.
	 * If needed, we could build a larger data structure to capture multiple periods.
	 * But for now we assume that returning a single period forecast is sufficient.
	 */

	private val myLog: Logger = log4s.getLogger
	private val FLD_PROPS = "properties"
	private val FLD_PERIODS = "periods"

	// We need only this one Circe decoder.  The rest of our work is done with explicit Circe cursor navigation.
	private implicit val periodForecastDecoder: Decoder[Msg_BackendPeriodForecast] = deriveDecoder[Msg_BackendPeriodForecast]

	def extractFirstPeriodForecast(forecastJson : Json) : IO[Msg_BackendPeriodForecast]  = {
		val flg_dumpJson = false
		if (flg_dumpJson) {
			val jsonTxt = forecastJson.spaces4
			myLog.info(s"Extracing first period block from forecast-json: ${jsonTxt}")
		}
		// Use Circe cursors to navigate to the 'periods' array in the JSON, found at ROOT.properties.periods
		val rootCursor: HCursor = forecastJson.hcursor
		val propCursor = rootCursor.downField(FLD_PROPS)
		val periodsArrayCursor = propCursor.downField(FLD_PERIODS)
		// We expect 'periods' array to contain multiple entries, ordered by time, alternating between daytime and nighttime.
		// The first period in the array corresponds to the 'current' forecast, which may be for either day or night.
		// Currently we assume that only this first period is interesting.
		val pCurs0: ACursor = periodsArrayCursor.downN(0)
		val p0JsonTXT: String = pCurs0.focus.map(_.spaces4).getOrElse({"No json found at period[0]"})
		myLog.info(s"period[0].history: ${pCurs0.history}")
		myLog.info(s"period[0].json: ${p0JsonTXT}")
		val p0Json: Option[Json] = pCurs0.focus

		// Use our implicit periodForecastDecoder, defined above.
		val p0Rec_orErr: Decoder.Result[Msg_BackendPeriodForecast] = p0Json.map(_.as[Msg_BackendPeriodForecast])
					.getOrElse(Left(DecodingFailure("Could not focus on period[0]", pCurs0.history)))
		//   final type Result[A] = Either[DecodingFailure, A]
		val decoded0_IO: IO[Msg_BackendPeriodForecast] = IO.fromEither(p0Rec_orErr)
		decoded0_IO
	}
}

/***

Fragment of an example JSON response from the api.weather.gov/gridpoints service, showing a sequencew of

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