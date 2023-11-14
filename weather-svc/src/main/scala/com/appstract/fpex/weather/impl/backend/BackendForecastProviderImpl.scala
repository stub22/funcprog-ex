package com.appstract.fpex.weather.impl.backend

import cats.data.EitherT
import cats.effect.IO
import com.appstract.fpex.weather.api.backend.BackendEffectTypes.BackendETIO
import com.appstract.fpex.weather.api.backend.{BackendForecastProvider, BackendFetchError, BackendDecodeFailure, Msg_BackendAreaInfo, Msg_BackendPeriodForecast, BackendError}
import io.circe.{DecodingFailure, Json}
import org.http4s.Request
import org.http4s.client.Client
import org.log4s
import org.log4s.Logger

class BackendForecastProviderImpl(dataSrcCli: => Client[IO]) extends BackendForecastProvider {

	private def getLogger: Logger = log4s.getLogger

	private val myRequestBuilder = new BackendRequestBuilder {}
	private val myForecastExtractor = new PeriodForecastExtractor {}

	// Main public method to build an effect that accesses our backend functionality, for a given input location.
	// When successful this effect will access *both* backend HTTP services.
	// Sends the given area-info request, and use the area-info response to build a followup forecast-info request.
	override def fetchAndExtractPeriodForecast(latLonPairTxt: String): BackendETIO[Msg_BackendPeriodForecast] = {
		val areaRqIO: IO[Request[IO]] = myRequestBuilder.buildAreaInfoGetRqIO(latLonPairTxt)
		val periodForeETIO = for {
			areaInfoRq <- EitherT.right(areaRqIO)
			areaInfoMsg <- fetchAreaInfo(areaInfoRq)
			foreJson <- fetchForecastJson(areaInfoMsg)
			periodForecast <- extractForecastPeriod(foreJson)
		} yield (periodForecast)
		periodForeETIO
	}

	override def fetchAreaInfo(areaRq: Request[IO]): BackendETIO[Msg_BackendAreaInfo] = {
		import JsonDecoders_BackendAreaInfo._
		val opName: String = "fetchAreaInfo"
		val areaResultIO: IO[Msg_BackendAreaInfo] = dataSrcCli.expect[Msg_BackendAreaInfo](areaRq)
		val eitherEff: IO[Either[Throwable, Msg_BackendAreaInfo]] = areaResultIO.attempt
		val eithT: EitherT[IO, Throwable, Msg_BackendAreaInfo] = EitherT(eitherEff)
		val areaETIO: BackendETIO[Msg_BackendAreaInfo] = eithT.leftMap(origExc => {
			BackendFetchError(opName, areaRq.toString, origExc)
		})
		// Design decision:  Make sure that any error gets logged from here (as well as delivered as our result).
		val loggedETIO = areaETIO.leftSemiflatTap((bckErr: BackendError) => IO.blocking {
			getLogger.warn(s"${opName} for ${areaRq.toString} failed with error : ${bckErr.asText}")
		})
		loggedETIO
	}

	private def fetchForecastJson(areaInfo : Msg_BackendAreaInfo) : BackendETIO[Json] =  {
		val opName = "fetchForecastJson"
		val foreRqIO: IO[Request[IO]] = myRequestBuilder.buildForecastRqIoFromAreaInfo(areaInfo)
		val fetchJsonOrErr: IO[Either[BackendFetchError, Json]] = foreRqIO.flatMap(foreRq => {
			import org.http4s.circe._  // Brings generic EntityDecoder[Json] implicits into scope
			val fetchJsonEff: IO[Json] = dataSrcCli.expect[Json](foreRq)
			fetchJsonEff.attempt.map(_.left.map(BackendFetchError(opName, foreRq.toString, _)))
		})
		val fetchETIO : BackendETIO[Json] = EitherT(fetchJsonOrErr)
		// Design decision:  Make sure that any error gets logged from here (as well as delivered as our result).
		val loggedETIO = fetchETIO.leftSemiflatTap((bckErr: BackendError) => IO.blocking {
			getLogger.warn(s"${opName} failed with error : ${bckErr.asText}")
		})
		loggedETIO

	}

	private def extractForecastPeriod(forecastJson : Json) : BackendETIO[Msg_BackendPeriodForecast] = {
		val opName = "extractForecastPeriod"
		val prdForecastOrDecFail: Either[DecodingFailure, Msg_BackendPeriodForecast] =
				myForecastExtractor.extractFirstPeriodForecast(forecastJson)
		val prdForeOrBDF = prdForecastOrDecFail.left.map(decFail => {
			// TODO: Supply a more useful+efficient summary of the Json in the BackendDecodeFailure
			val jsonFullTxt = forecastJson.toString()
			BackendDecodeFailure(opName, jsonFullTxt.take(1000), decFail)
		})
		EitherT.fromEither(prdForeOrBDF)
	}
}


