package com.appstract.fpex.weather.impl.backend

import cats.data.EitherT
import cats.effect.IO
import com.appstract.fpex.weather.api.backend.BackendEffectTypes.BackendETIO
import com.appstract.fpex.weather.api.backend.{BackendForecastProvider, DataFetchError, DataDecodeFailure, Msg_BackendAreaInfo, Msg_BackendPeriodForecast, OldeBackendError, OurBackendError}
import io.circe.{DecodingFailure, Json}
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

	override def fetchAndExtractPeriodForecast(latLonPairTxt: String): BackendETIO[Msg_BackendPeriodForecast] = {
		// val opName = "fetchForecastInfo"
		val areaRqIO: IO[Request[IO]] = myRequestBuilder.buildAreaInfoGetRqIO(latLonPairTxt)
		/*
		val areaETIO: EitherT[IO, OurBackendError, Msg_BackendAreaInfo] = for {
			areaInfoRq <- EitherT.right(areaRqIO)
			areaInfoMsg <- fetchAreaInfo(areaInfoRq)
		} yield (areaInfoMsg)
		val fetchETIO: BackendETIO[Json] = areaETIO.flatMap(fetchForecastJson(_))
		 */
		val periodForeETIO: EitherT[IO, OurBackendError, Msg_BackendPeriodForecast] = for {
			areaInfoRq <- EitherT.right(areaRqIO)
			areaInfoMsg <- fetchAreaInfo(areaInfoRq)
			foreJson <- fetchForecastJson(areaInfoMsg)
			periodForecast <- extractForecastPeriod(foreJson)
		} yield (periodForecast)
		periodForeETIO
	}
/*		areaInfo => {
			val foreRqIO: IO[Request[IO]] = myRequestBuilder.buildForecastRqIoFromAreaInfo(areaInfo)
			// Hung up because we want to have the foreRq around during the later mapping phase, in case of an error.
			val fetchJsonOrErr: IO[Either[DataFetchError, Json]] = foreRqIO.flatMap(foreRq => {
				// Bring generic EntityDecoder[Json] implicits into scope
				import org.http4s.circe._
				val fetchJsonEff: IO[Json] = dataSrcCli.expect[Json](foreRq)
				fetchJsonEff.attempt.map(_.left.map(DataFetchError(opName, foreRq.toString, _)))
				//reportIO.redeem(err => Left(exceptToWeatherErr(latLonPairTxt, err)), Right(_))
				// val rqTxt = () => {foreRq.toString()}
				// jsonAtt.product(IO.delay(rqTxt))
			})
			EitherT(fetchJsonOrErr)
		})
 */

		/*
			fetchETIO.flatMap(jsonDat => )
			forecastRq =  myRequestBuilder.buildForecastRqIoFromAreaInfo(areaInfoMsg)
			// oldForeRq <- myRequestBuilder.buildForecastRqIoFromAreaInfo(areaInfoMsg)
			forecastMsg <- oldeFetchCurrentForecastPeriodOrError(forecastRq)
		} yield (forecastMsg)
		 */


	private def fetchForecastJson(areaInfo : Msg_BackendAreaInfo) : BackendETIO[Json] =  {
		val opName = "fetchForecastJson"
		val foreRqIO: IO[Request[IO]] = myRequestBuilder.buildForecastRqIoFromAreaInfo(areaInfo)
		// Hung up because we want to have the foreRq around during the later mapping phase, in case of an error.
		val fetchJsonOrErr: IO[Either[DataFetchError, Json]] = foreRqIO.flatMap(foreRq => {
			// Bring generic EntityDecoder[Json] implicits into scope
			import org.http4s.circe._
			val fetchJsonEff: IO[Json] = dataSrcCli.expect[Json](foreRq)
			fetchJsonEff.attempt.map(_.left.map(DataFetchError(opName, foreRq.toString, _)))
			//reportIO.redeem(err => Left(exceptToWeatherErr(latLonPairTxt, err)), Right(_))
			// val rqTxt = () => {foreRq.toString()}
			// jsonAtt.product(IO.delay(rqTxt))
		})
		EitherT(fetchJsonOrErr)
	}

	private def extractForecastPeriod(forecastJson : Json) : BackendETIO[Msg_BackendPeriodForecast] = {
		val opName = "extractForecastPeriod"
		val prdForecastOrDecFail: Either[DecodingFailure, Msg_BackendPeriodForecast] =
				myForecastExtractor.extractFirstPeriodForecast(forecastJson)
		val prdForeOrDDF = prdForecastOrDecFail.left.map(decFail => {
			// TODO: Supply a more useful+efficient summary of the Json in the DataDecodeFailure
			val jsonFullTxt = forecastJson.toString()
			val pre = jsonFullTxt.take(1000)
			DataDecodeFailure(opName, jsonFullTxt.take(1000), decFail)
		})
		EitherT.fromEither(prdForeOrDDF)
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

	private def fetchCurrentForecastPeriod(forecastRq : Request[IO]) : BackendETIO[Msg_BackendPeriodForecast] = {
//		import JsonDecoders_BackendAreaInfo._
		// Bring generic EntityDecoder[Json] implicits into scope
		import org.http4s.circe._
		import cats.syntax.all._
		val opName : String = "fetchCurrentForecastPeriod"
		// Submit forecast request, expecting a Json response-body.  Note that we do NOT ask Circe to map this
		// body directly into our Msg_ types.  Compare with the fetchAreaInfo method above.
		val forecastJsonIO: IO[Json] = dataSrcCli.expect[Json](forecastRq)
		val forecastET: EitherT[IO, Throwable, Json] = EitherT(forecastJsonIO.attempt)
		// val next = forecastET.biflatMap(
		// 		EitherT.leftT(DataFetchError(opName, forecastRq.toString, _)),
		//		myForecastExtractor.extractFirstPeriodForecast(_))
		???
	}
	override def fetchAreaInfo(areaRq: Request[IO]): BackendETIO[Msg_BackendAreaInfo] = {
		import JsonDecoders_BackendAreaInfo._
		val opName : String = "better-fetchAreaInfoOrError"
		val areaResultIO: IO[Msg_BackendAreaInfo] = dataSrcCli.expect[Msg_BackendAreaInfo](areaRq)
		val eitherEff: IO[Either[Throwable, Msg_BackendAreaInfo]] = areaResultIO.attempt
		val eithT: EitherT[IO, Throwable, Msg_BackendAreaInfo] = EitherT(eitherEff)
		val areaETIO : BackendETIO[Msg_BackendAreaInfo] = eithT.leftMap(origExc => {
			DataFetchError(opName, areaRq.toString, origExc)
		})
		val loggedETIO = areaETIO.leftSemiflatTap((bckErr: OurBackendError) => {
			IO.blocking {
				// In general a logging call MIGHT be blocking.
				myLog.warn(s"${opName} for ${areaRq.toString} failed with error : ${bckErr.asText}")
			}
		})
		loggedETIO
	}



}


