package com.appstract.fpex.weather

import cats.data.EitherT
import cats.effect.IO
import org.http4s.Method.GET
import org.http4s.{EntityEncoder, Request}
import org.http4s.client.Client
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.log4s
import org.log4s.Logger

/***
 * Initial goals for our endpoint
 * 1. Accepts latitude and longitude coordinates
 * 2. Returns the short forecast for that area for Today (“Partly Cloudy” etc)
 * 3. Returns a characterization of whether the temperature is “hot”, “cold”, or “moderate”
 */
// Output message for our service user, to be converted to Json.
// trait WeatherResult
case class Msg_WeatherReport(messageType : String, latLongPairTxt : String, summary : String, temperatureDescription : String) // extends WeatherResult
case class Msg_WeatherError(messageType : String , latLongPairTxt : String, errorName : String, errorInfo : String) // extends WeatherResult

object JsonEncoders_Report {
	// Derive json encoding automatically, based on fields of the Msg_WeatherReport.
	implicit val weatherReportEncoder: Encoder[Msg_WeatherReport] = deriveEncoder[Msg_WeatherReport]
	implicit val weatherReportEntityEncoder: EntityEncoder[IO, Msg_WeatherReport] = jsonEncoderOf

	implicit val weatherErrorEncoder: Encoder[Msg_WeatherError] = deriveEncoder[Msg_WeatherError]
	implicit val weatherErrorEntityEncoder: EntityEncoder[IO, Msg_WeatherError] = jsonEncoderOf
}

trait WeatherReportSupplier {
	type WReportOrErr = Either[Msg_WeatherError, Msg_WeatherReport]

	def getFakeWeather : IO[WReportOrErr]

	def fetchWeatherForFixedLocation : IO[WReportOrErr]

	def fetchWeatherForLatLonPairTxt(latLonPairTxt : String) : IO[WReportOrErr]

	def fetchWeatherForLatLon(latTxt : String, longTxt : String) : IO[WReportOrErr]
}

class WReportSupplierImpl(dataSrcCli: => Client[IO]) extends WeatherReportSupplier {

	private val MTYPE_REPORT = "WEATHER_REPORT"
	private val MTYPE_ERROR = " WEATHER_ERROR"

	private lazy val myLog: Logger = log4s.getLogger

	private lazy val myBFP : BackendForecastProvider = new BackendForecastProviderImpl(dataSrcCli)

	private lazy val myInterp : TemperatureInterpreter = new TemperInterpImpl

	override def getFakeWeather: IO[WReportOrErr] = {
		val latLong = "FAKE_LAT,FAKE_LONG"
		val fakeForecastMsg = Msg_WeatherReport(MTYPE_REPORT, latLong, "FAKE_SUMMARY", "FAKE_TEMP")
		val rprtIO = IO.pure(fakeForecastMsg)
		wrioToWROEIO(rprtIO, latLong)
	}

	override def fetchWeatherForFixedLocation: IO[WReportOrErr] = {
		val fixedLatLonPairTxt = "39.7456,-97.0892"
		fetchWeatherForLatLonPairTxt(fixedLatLonPairTxt)
	}

	// latLonTxt is in the comma separated lat-long format used by the backend weather service, e.g. "39.7456,-97.0892"
	override def fetchWeatherForLatLonPairTxt(latLonPairTxt : String) : IO[WReportOrErr] = {
		// val areaRq: IO[Request[IO]] = myBFP.areaGetRequest(latLonPairTxt)
		// val forecastInfo: IO[Msg_BackendPeriodForecast] = myBFP.fetchAreaInfoThenForecastInfo(areaRq)
		val forecastInfo: IO[Msg_BackendPeriodForecast] = myBFP.fetchForecastInfoForLatLonTxt(latLonPairTxt)
		val report: IO[Msg_WeatherReport] = forecastInfo.flatMap(buildWeatherReport(latLonPairTxt, _))
		wrioToWROEIO(report, latLonPairTxt)
	}

	private def buildWeatherReport(latLonPairTxt : String, backendForecast : Msg_BackendPeriodForecast) : IO[Msg_WeatherReport]  = {
		myLog.info(s"buildWeatherReport using backendForecast : ${backendForecast}")
		val tempDesc = myInterp.describeTempFarenheit(backendForecast.temperature, backendForecast.isDaytime)
		val report = Msg_WeatherReport(MTYPE_REPORT, latLonPairTxt, backendForecast.shortForecast, tempDesc)
		myLog.info(s"buildWeatherReport made report: ${report}")
		IO.pure(report)
	}

	private def mkFixedAreaRqIO : Request[IO] = {
		// https://http4s.org/v0.23/docs/client.html#constructing-a-uri
		import org.http4s.implicits._
		import org.http4s.client.dsl.io._ // Pulls in the uri macro
		// "This only works with literal strings because it uses a macro to validate the URI format at compile-time."
		val areaRequestIO: Request[IO] = GET(uri"https://api.weather.gov/points/39.7456,-97.0892")
		areaRequestIO
	}

	override def fetchWeatherForLatLon(latTxt : String, lonTxt : String) : IO[WReportOrErr] = {
		myLog.info(s"fetchWeatherForLatLon(lat=${latTxt}, lon=${lonTxt}")
		// TODO:  Validate input values
		val latLonPairTxt = s"${latTxt},${lonTxt}"
		if (true) {
			fetchWeatherForLatLonPairTxt(latLonPairTxt)
		} else {
			val unfinished = IO.raiseError(new Exception("fetchWeatherForLatLon ain't ready yet"))
			wrioToWROEIO(unfinished, latLonPairTxt)
		}
	}

	private def excToWerr(geoLoc : String, t : Throwable) = {
		t match {
			case backendErr : Msg_BackendError => Msg_WeatherError(MTYPE_ERROR, geoLoc, "BACKEND_ERR", backendErr.toString)
			case other => Msg_WeatherError(MTYPE_ERROR, geoLoc, "OTHER_ERR", other.toString)
		}
		Msg_WeatherError(MTYPE_ERROR, geoLoc, "errName", t.getMessage)
	}
	private def excToLeftWerr(geoLoc : String, t : Throwable) : WReportOrErr = Left(excToWerr(geoLoc, t))
	private def wrToRightWrep(mwr : Msg_WeatherReport) : WReportOrErr = Right(mwr)

	private def wrioToWROEIO(wrio : IO[Msg_WeatherReport], geoLoc : String) : IO[WReportOrErr] =  wrio.redeem(excToLeftWerr(geoLoc, _), wrToRightWrep(_))

	// private def wrioToWresIO(wrio : IO[Msg_WeatherReport]) : IO[WeatherResult] = wrio.handleError(excToWerr(_))

	private def playWithErrorHandling(wrprtIO : IO[Msg_WeatherReport] ) : IO[WReportOrErr] = {
		import cats.implicits._
		val wAtt: IO[Either[Throwable, Msg_WeatherReport]] = wrprtIO.attempt
		val wAttT: EitherT[IO, Throwable, Msg_WeatherReport] = wrprtIO.attemptT
		val rthr: IO[Msg_WeatherReport] = wAtt.rethrow

		// ambiguous implicits
		// val wm: IO[Msg_WeatherError] = wrprtIO.forceR(Msg_WeatherError("latLong", "errName", "errInf").pure)

		// val usingSupertype: IO[WeatherResult] = wrprtIO.handleError(excToWerr(_))

		// val wroe01: IO[WReportOrErr] = wrprtIO.redeem(excToLeftWerr(_), wrToRightWrep(_))

		val wroe02 = wrioToWROEIO(wrprtIO, "geoLoc like lat,lon")
		wroe02
	}
}
/*
Write an HTTP server that serves the current weather. Your server should expose an endpoint that:

4. Use the National Weather Service API Web Service as a data source.
 */