package com.appstract.fpex.weather

import cats.effect.IO
import org.http4s.client.Client
import org.log4s
import org.log4s.Logger

class WeatherReportSupplierImpl(dataSrcCli: => Client[IO]) extends WeatherReportSupplier {

	private val myLog: Logger = log4s.getLogger

	private val myBFP : BackendForecastProvider = new BackendForecastProviderImpl(dataSrcCli)

	private val myInterp : TemperatureInterpreter = new TempInterpImpl

	override def fetchWeatherForLatLon(latTxt : String, lonTxt : String) : IO[WReportOrErr] = {
		myLog.info(s"fetchWeatherForLatLon(lat=${latTxt}, lon=${lonTxt}")
		// TODO:  Validate input values.  On failure, should produce a validation error and prevent any further work.
		val latLonPairTxt = s"${latTxt},${lonTxt}"
		fetchWeatherForLatLonPairTxt(latLonPairTxt)
	}

	// latLonTxt is in the comma separated lat-long format used by the backend weather service, e.g. "39.7456,-97.0892"
	override def fetchWeatherForLatLonPairTxt(latLonPairTxt : String) : IO[WReportOrErr] = {
		val forecastInfoIO: IO[Msg_BackendPeriodForecast] = myBFP.fetchForecastInfoForLatLonTxt(latLonPairTxt)
		val reportIO: IO[Msg_WeatherReport] = forecastInfoIO.map(buildWeatherReport(latLonPairTxt, _))
		buildOutputMessageIO(reportIO, latLonPairTxt)
	}

	private def buildWeatherReport(latLonPairTxt : String, backendForecast : Msg_BackendPeriodForecast) : Msg_WeatherReport  = {
		myLog.info(s"buildWeatherReport using backendForecast : ${backendForecast}")
		val tempDesc = myInterp.describeTempFahrenheit(backendForecast.temperature.toFloat, backendForecast.isDaytime)
		val report = Msg_WeatherReport(MTYPE_REPORT, latLonPairTxt, backendForecast.shortForecast, tempDesc)
		myLog.info(s"buildWeatherReport made report: ${report}")
		report
	}

	// Translate any possible result from reportIO into a message (successful or error) we can output as JSON.
	// geoLoc contains a text description of the location; it is currently the same as a latLonPairTxt.
	private def buildOutputMessageIO(reportIO : IO[Msg_WeatherReport], geoLoc : String) : IO[WReportOrErr] = {
		reportIO.redeem(errorToLeftResult(geoLoc, _), reportToRightResult(_))
	}

	private def reportToRightResult(report : Msg_WeatherReport) : WReportOrErr = Right(report)

	private def errorToLeftResult(geoLoc : String, t : Throwable) : WReportOrErr = Left(exceptToWeatherErr(geoLoc, t))

	private def exceptToWeatherErr(geoLoc : String, t : Throwable): Msg_WeatherError = {
		t match {
			case backendErr : BackendError => Msg_WeatherError(MTYPE_ERROR, geoLoc, "BACKEND_ERR", backendErr.asText)
			case other => Msg_WeatherError(MTYPE_ERROR, geoLoc, "OTHER_ERR", other.toString)
		}
	}

}
