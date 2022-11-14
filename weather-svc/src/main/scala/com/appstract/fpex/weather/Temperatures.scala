package com.appstract.fpex.weather

/**
 * This file includes both API and Impl for a feature that interprets temperatures.
 */

// Internal API type
trait TemperatureInterpreter {
	// Assign a descriptive label ("hot", "cold", "moderate") for a given temperature, ocurring in either daytime or nightime.
	def describeTempFarenheit(temp : Float, flg_isDaytime : Boolean) : String

	// TODO:  Add Celsius API method, if we decide users need this.
	// Seems api.weather.gov returns Farenheit by default.  But does that vary by query location?
	// def describeTempCelsius(tempC : Float, flg_isDaytime : Boolean) : String
}

sealed trait TempScale
case object Celsius extends TempScale
case object Farenheit extends TempScale

// Use Option.None to indicate that a range is open on the minTemp or maxTemp side.
// If a range specifies None for both min and max, then the range would logically contain ANY numeric temperature value.
// We use Floats here for a balance of convenient brevity and generality.
private case class TempRange(name : String, scale : TempScale, minTemp_opt : Option[Float], maxTemp_opt : Option[Float]) {
	def containsTemp(temp : Float) : Boolean = {
		minTemp_opt.fold(true)(temp >= _) && maxTemp_opt.fold(true)(temp < _)
	}
}

class TempInterpImpl extends TemperatureInterpreter {

	// Explicitly defined constant values which are used multiple times in describing temperature ranges for day and night.
	// Here "MAX_F" means max degrees Farenheit.
	private val TEMP_RANGE_NAME_COLD = "cold"
	private val BOUND_DAY_COLD_MAX_F = 54.5f
	private val BOUND_NIGHT_COLD_MAX_F = 39.9f

	private val TEMP_RANGE_NAME_MODERATE = "moderate"
	private val BOUND_DAY_MODERATE_MAX_F = 84.7f
	private val BOUND_NIGHT_MODERATE_MAX_F = 73.1f

	// Any temperature hotter than the MODERATE_MAX must be a HOT temp.
	private val TEMP_RANGE_NAME_HOT = "hot"

	private val TEMP_RANGE_NAME_ERR = "RANGE-ERROR"

	// We define a list of ranges that we can match an incoming temperature value against.
	// Each range should have a maxTemp equal to the minTemp of the following range.

	// We have only defined ranges for Farenheit.
	private val rangesDayF: List[TempRange] = List(
		TempRange(TEMP_RANGE_NAME_COLD, Farenheit, None, Some(BOUND_DAY_COLD_MAX_F)),
		TempRange(TEMP_RANGE_NAME_MODERATE, Farenheit, Some(BOUND_DAY_COLD_MAX_F), Some(BOUND_DAY_MODERATE_MAX_F)),
		TempRange(TEMP_RANGE_NAME_HOT, Farenheit, Some(BOUND_DAY_MODERATE_MAX_F), None)
	)
	private val rangesNightF: List[TempRange] = List(
		TempRange(TEMP_RANGE_NAME_COLD, Farenheit, None, Some(BOUND_NIGHT_COLD_MAX_F)),
		TempRange(TEMP_RANGE_NAME_MODERATE, Farenheit, Some(BOUND_NIGHT_COLD_MAX_F), Some(BOUND_NIGHT_MODERATE_MAX_F)),
		TempRange(TEMP_RANGE_NAME_HOT, Farenheit, Some(BOUND_NIGHT_MODERATE_MAX_F), None)
	)

	// Implements our requirement to describe a temperature with a single word.
	override def describeTempFarenheit(tempF : Float, flg_isDaytime : Boolean) : String = {
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