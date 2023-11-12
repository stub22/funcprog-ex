package com.appstract.fpex.weather

/**
 * This file includes both API and Impl for a feature that interprets temperatures.
 */

// Internal API type
trait TemperatureInterpreter {
	// Assign a descriptive label ("hot", "cold", "moderate") for a given temperature, occurring in either daytime or nightime.
	def describeTempFahrenheit(temp : Float, flg_isDaytime : Boolean) : String

	// TODO: Consider adding Celsius API method.
	// Seems api.weather.gov returns Fahrenheit by default.  But does that vary by query location?
	// def describeTempCelsius(tempC : Float, flg_isDaytime : Boolean) : String
}

sealed trait TempScale
case object Celsius extends TempScale
case object Fahrenheit extends TempScale

// Use Option.None to indicate that a range is open on the minTemp or maxTemp side.
// If a range specifies None for both min and max, then the range would logically contain ANY numeric temperature value.
// We use Floats here for a balance of convenient brevity and generality.
private case class TempRange(name : String, scale : TempScale, minTemp_opt : Option[Float], maxTemp_opt : Option[Float]) {
	def containsTemp(temp : Float) : Boolean = {
		minTemp_opt.fold(true)(temp >= _) && maxTemp_opt.fold(true)(temp < _)
	}
}
object TempRangeNames {
	val COLD = "cold"
	val MODERATE = "moderate"
	val HOT = "hot"
}

class TempInterpImpl extends TemperatureInterpreter {
	import TempRangeNames._

	// These constant values which are used multiple times in describing temperature ranges for day and night.
	// Here "MAX_F" means max degrees Fahrenheit.
	//private val TEMP_RANGE_NAME_COLD = "cold"
	private val DAY_COLD_MAX_F = 54.5f	// Daytime temps lower than this are "cold"
	private val DAY_MODERATE_MAX_F = 84.7f	// Daytime temps below this (but above DAY_COLD_MAX_F) are "moderate"

	private val NIGHT_COLD_MAX_F = 39.9f	// Nighttime temps lower than this are "cold"
	//private val TEMP_RANGE_NAME_MODERATE = "moderate"

	private val NIGHT_MODERATE_MAX_F = 73.1f	// Nighttime temps below this (but above BOUND_NIGHT_COLD_MAX_F) are "moderate"

	// Any temperature hotter than the MODERATE_MAX must be a HOT temp.

	//private val TEMP_RANGE_NAME_HOT = "hot"

	private val TEMP_RANGE_NAME_ERR = "RANGE-ERROR"

	// We define a list of ranges that we can match an incoming temperature value against.
	// Each range should have a maxTemp equal to the minTemp of the following range.
	//
	// We have only defined ranges for Fahrenheit.
	private val rangesDayF: List[TempRange] = List(
		TempRange(COLD, Fahrenheit, None, Some(DAY_COLD_MAX_F)),
		TempRange(MODERATE, Fahrenheit, Some(DAY_COLD_MAX_F), Some(DAY_MODERATE_MAX_F)),
		TempRange(HOT, Fahrenheit, Some(DAY_MODERATE_MAX_F), None)
	)
	private val rangesNightF: List[TempRange] = List(
		TempRange(COLD, Fahrenheit, None, Some(NIGHT_COLD_MAX_F)),
		TempRange(MODERATE, Fahrenheit, Some(NIGHT_COLD_MAX_F), Some(NIGHT_MODERATE_MAX_F)),
		TempRange(HOT, Fahrenheit, Some(NIGHT_MODERATE_MAX_F), None)
	)

	// Implements our requirement to describe a temperature with a single word.
	override def describeTempFahrenheit(tempF : Float, flg_isDaytime : Boolean) : String = {
		val firstMatchingRange: Option[TempRange] = if (flg_isDaytime) {
			// Interpret tempF as a daytime temperature
			rangesDayF.find(_.containsTemp(tempF))
		} else {
			// Interpret tempF as a nighttime temperature
			rangesNightF.find(_.containsTemp(tempF))
		}
		val description : String = firstMatchingRange.map(_.name).getOrElse(TEMP_RANGE_NAME_ERR)
		description
	}
}