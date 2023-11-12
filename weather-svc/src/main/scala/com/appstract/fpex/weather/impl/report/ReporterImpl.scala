package com.appstract.fpex.weather.impl.report

import cats.data.EitherT
import cats.effect.IO
import com.appstract.fpex.weather.api._
import com.appstract.fpex.weather.api.backend.BackendEffectTypes.BackendETIO
import com.appstract.fpex.weather.api.backend.{BackendForecastProvider, Msg_BackendPeriodForecast, OurBackendError}
import com.appstract.fpex.weather.api.report.{Msg_WeatherError, Msg_WeatherReport, TemperatureClassifier, WeatherReportSupplier}
import com.appstract.fpex.weather.impl.backend.BackendForecastProviderImpl
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import org.http4s.client.Client
import org.log4s
import org.log4s.Logger

object JsonEncoders_Report {
	// These encoders may be pulled into scope wherever our messages need to be encoded as JSON.

	// Derive json encoding automatically, based on fields of the Msg_WeatherReport.
	// The names of the Scala msg fields are used as field names in the JSON messages.
	implicit val weatherReportEncoder: Encoder[Msg_WeatherReport] = deriveEncoder[Msg_WeatherReport]
	implicit val weatherReportEntityEncoder: EntityEncoder[IO, Msg_WeatherReport] = jsonEncoderOf

	// Derive json encoding automatically for the Msg_WeatherError.
	implicit val weatherErrorEncoder: Encoder[Msg_WeatherError] = deriveEncoder[Msg_WeatherError]
	implicit val weatherErrorEntityEncoder: EntityEncoder[IO, Msg_WeatherError] = jsonEncoderOf
}

class WeatherReportSupplierImpl(dataSrcCli: => Client[IO]) extends WeatherReportSupplier {

	private val myLog: Logger = log4s.getLogger

	private val myBFP : BackendForecastProvider = new BackendForecastProviderImpl(dataSrcCli)

	private val myInterp : TemperatureClassifier = new TemperClassifierImpl

	override def fetchWeatherForLatLon(latTxt : String, lonTxt : String) : IO[WReportOrErr] = {
		myLog.info(s"fetchWeatherForLatLon(lat=${latTxt}, lon=${lonTxt}")
		// TODO:  Validate input values.  On failure, should produce a validation error and prevent any further work.
		// Concatenate the lat,lon into the same format used by api.weather.gov backend.
		val latLonPairTxt = s"${latTxt},${lonTxt}"
		fetchWeatherForLatLonPairTxt(latLonPairTxt)
	}

	// latLonTxt is in the comma separated lat-long format used by the backend weather service, e.g. "39.7456,-97.0892"
	override def fetchWeatherForLatLonPairTxt(latLonPairTxt : String) : IO[WReportOrErr] = {
		val forecastETIO: BackendETIO[Msg_BackendPeriodForecast] = myBFP.fetchAndExtractPeriodForecast(latLonPairTxt)
		val wreportETIO: EitherT[IO, Msg_WeatherError, Msg_WeatherReport] = forecastETIO.bimap(
				backendErrToWeatherErr(latLonPairTxt, _), 	// Mapping the Left-Error case
				buildWeatherReport(latLonPairTxt, _))		// Mapping the Right-Success case
		val wreportIO : IO[WReportOrErr] = wreportETIO.value
		wreportIO
	}

	private def buildWeatherReport(latLonPairTxt : String, backendForecast : Msg_BackendPeriodForecast) : Msg_WeatherReport  = {
		myLog.info(s"buildWeatherReport using backendForecast : ${backendForecast}")
		val tempDesc: String = myInterp.findFahrenheitTempRange(backendForecast.temperature.toFloat, backendForecast.isDaytime)
		val report = Msg_WeatherReport(MTYPE_REPORT, latLonPairTxt, backendForecast.shortForecast, tempDesc)
		myLog.info(s"buildWeatherReport made report: ${report}")
		report
	}

	private def backendErrToWeatherErr(latLonTxt : String, backendErr : OurBackendError) : Msg_WeatherError = {
		Msg_WeatherError(MTYPE_ERROR, latLonTxt, "BACKEND_ERR", backendErr.asText)
	}

}
