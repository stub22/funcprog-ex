package com.appstract.fpex.weather

import munit.CatsEffectSuite
import cats.effect.IO
import org.http4s.{HttpRoutes, Method, Request, Response, Status, Uri}
import org.log4s
import org.log4s.Logger

import scala.annotation.nowarn

class GreetingRouteSpec extends CatsEffectSuite {
	val USERNAME_01 = "gilligan"
	val OP_NM_GREET = WeatherRoutes.OP_NAME_GREET
	val expectedMsgFname = JsonEnc_Greeting.FIELD_NAME_MSG_TXT // "messageTxt"

	test("Regular greetingRoutes returns status code 200") {
		val greetResponseIO = invokeGreetingRoute(USERNAME_01, false)
		assertIO(greetResponseIO.map(_.status) ,Status.Ok)
	}
	test("otherGreetingRoutes returns status code 200") {
		val greetResponseIO = invokeGreetingRoute(USERNAME_01, true)
		assertIO(greetResponseIO.map(_.status) ,Status.Ok)
	}
	test("Regular greetingRoutes returns expected greeting message") {
		val unm = USERNAME_01
		val greetResponseIO = invokeGreetingRoute(unm, false)
		val expectedRespTxt = mkExpectedResponseTxt(unm)
		assertIO(greetResponseIO.flatMap(_.as[String]), expectedRespTxt) //"{\"message\":\"Hello, world\"}")
	}
	test("otherGreetingRoutes returns expected greeting message") {
		val unm = USERNAME_01
		val greetResponseIO = invokeGreetingRoute(unm, true)
		val greetMsgIO: IO[String] = greetResponseIO.flatMap(_.as[String])
		val expectedRespTxt = mkExpectedResponseTxt(unm)
		assertIO(greetMsgIO, expectedRespTxt)
	}

	private[this] def invokeGreetingRoute(unm : String, flg_useOther : Boolean) : IO[Response[IO]] = {
		// Builds the response-out effect for a simulated request-in message.
		val log: Logger = log4s.getLogger
		val greetUriPath = s"/${OP_NM_GREET}/${unm}"
		val greetUri = Uri.unsafeFromString(greetUriPath)
		log.info(s"GreetingRouteSpec.invokeGreetingRoute made greetUri: ${greetUri}")
		val requestIO: Request[IO] = Request[IO](Method.GET, greetUri) //  uri"/greetingForUser/world")
		val greetSupp: GreetingSupplier = GreetingSupplierSingleton.getImpl

		val route: HttpRoutes[IO] = if (flg_useOther) {
			WeatherRoutes.otherGreetingRoutes(greetSupp)
		} else {
			WeatherRoutes.greetingRoutes(greetSupp)
		}
		route.orNotFound(requestIO)
	}
	private[this] def mkExpectedResponseTxt(unm : String) : String = {
		val expectedMsgTxt = s"${GreetingSupplierSingleton.GREETING_TEXT_PREFIX}${unm}"
		// val expectedRespTxt_OLD  = s"{\"${expectedMsgFname}\":\"${expectedMsgTxt}\"}"
		val expectedRespTxt = "{" + doubleQuoted(expectedMsgFname) + ':' + doubleQuoted(expectedMsgTxt) + "}"
		expectedRespTxt
	}
	// method + in class Char is deprecated (since 2.13.0): Adding a number and a String is deprecated. Use the string interpolation `s"$num$str"`
	val DQ_CHAR = '"'
	// val DQ_STRING : String = '"'.toString
	private def doubleQuoted(txt : String) : String =  s"${DQ_CHAR}${txt}${DQ_CHAR}"
	/*
	import org.http4s.implicits._
	winds up giving us:
	class LiteralsOps(val sc: StringContext) extends AnyVal {
	def uri(args: Any*): Uri = macro LiteralSyntaxMacros.uri.make
	 */
	@nowarn("cat=unused")
	private[this] def mkUriUsingMacro : Uri = {
		import org.http4s.implicits._
		val x: Uri = uri"/WRONG_IGNORE/world"
		x
	}
	// typeParseResult[+A] = Either[ParseFailure, A]
	// val URI_01: Uri = Uri.unsafeFromString(URI_TXT_01)
	// val URI_SAFE_01: ParseResult[Uri] = Uri.fromString(URI_TXT_01)
}
