package com.appstract.fpex.weather.impl.backend

import cats.data.EitherT
import cats.effect.IO
import com.appstract.fpex.weather.api._
import com.appstract.fpex.weather.api.backend.{BackendForecastProvider, Msg_BackendAreaInfo, Msg_BackendPeriodForecast, OldeBackendError}
import io.circe.Json
import org.http4s.Request
import org.http4s.client.Client
import org.log4s
import org.log4s.Logger

class BackendForecastProviderImpl(dataSrcCli: => Client[IO]) extends BackendForecastProvider {

	private val myLog: Logger = log4s.getLogger

	private val myRequestBuilder = new BackendRequestBuilder {}
	private val myForecastExtractor = new PeriodForecastExtractor {}

	// Main public method to build an effect that accesses our backend functionality, for a given input location.
	// When successful the effect will access *both* backend HTTP services.
	override def oldeFetchForecastInfoForLatLonTxt(latLonPairTxt : String) : IO[Msg_BackendPeriodForecast] = {
		val areaRqIO: IO[Request[IO]] = myRequestBuilder.buildAreaInfoGetRqIO(latLonPairTxt)
		val forecastInfoIO: IO[Msg_BackendPeriodForecast] = oldeFetchAreaInfoThenForecastInfo(areaRqIO)
		forecastInfoIO
	}

	// Send the given area-info request, and use the area-info response to build a followup forecast-info request.
	private def oldeFetchAreaInfoThenForecastInfo (areaRqIO: IO[Request[IO]]) : IO[Msg_BackendPeriodForecast] = {
		// When each of these effect stages is run, it may encounter errors.
		for {
			areaInfoRq <- areaRqIO
			areaInfoMsg <- oldeFetchAreaInfoOrError(areaInfoRq)
			forecastRq <- myRequestBuilder.buildForecastRqIoFromAreaInfo(areaInfoMsg)
			forecastMsg <- oldeFetchCurrentForecastPeriodOrError(forecastRq)
		} yield (forecastMsg)
	}


	override def oldeFetchAreaInfoOrError(areaRq : Request[IO]) : IO[Msg_BackendAreaInfo] = {
		import JsonDecoders_BackendAreaInfo._
		import cats.implicits._
		val opName : String = "fetchAreaInfoOrError"
		val rqTxt = areaRq.toString
		myLog.info(s"${opName} for areaRq=${rqTxt}")
		// Submit Area request, expecting a Json response-body, which we decode into AreaInfo message, or an error.
		val areaResultIO: IO[Msg_BackendAreaInfo] = dataSrcCli.expect[Msg_BackendAreaInfo](areaRq)
		// Map any exception thrown (during HTTP fetch+decode) into a BackendError, and log it.
		val x: IO[Either[Throwable, Msg_BackendAreaInfo]] = areaResultIO.attempt
		val etht: EitherT[IO, Throwable, Msg_BackendAreaInfo] = EitherT(x)
		val out: EitherT[IO, OldeBackendError, Msg_BackendAreaInfo] = etht.leftMap(thrwn => {
			OldeBackendError(opName, rqTxt, thrwn)
		})
		val outLogged: EitherT[IO, OldeBackendError, Msg_BackendAreaInfo] = out.leftSemiflatTap((bckErr : OldeBackendError) => {
			IO.blocking {
				// In general a logging call MIGHT be blocking.
				myLog.warn(bckErr)(s"${opName} for ${rqTxt} produced error : ${bckErr.asText}  ")
			}
		})
		val robustAreaIO: IO[Msg_BackendAreaInfo] = areaResultIO.adaptError {
			case t => {
				OldeBackendError(opName, rqTxt, t)
			}
		}.onError(errToLog => {
			IO.blocking {
				myLog.error(errToLog)(s"${opName} for ${rqTxt} produced error : ${errToLog}  ")
			}
		})
		robustAreaIO
	}

	// Two kinds of error are possible.
	// We might
	private def oldeFetchCurrentForecastPeriodOrError(forecastRq : Request[IO]) : IO[Msg_BackendPeriodForecast] = {
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
				OldeBackendError(opName, rqTxt, t)
			}
		}
		// Extract useful forecast period(s) from the backend JSON
		val periodForecastIO: IO[Msg_BackendPeriodForecast] = robustForecastJsonIO.flatMap(forecastJson =>
				IO.fromEither(myForecastExtractor.extractFirstPeriodForecast(forecastJson)))
		periodForecastIO
	}
}


