package com.appstract.fpex.weather.impl

import cats.data.EitherT
import cats.effect.IO
import com.appstract.fpex.weather.api._
import io.circe._
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Method, ParseResult, Request, Uri}
import org.log4s
import org.log4s.Logger

private object JsonDecoders_BackendAreaInfo {
	// Circe decoder bindings for building Msg_AreaInfo* from JSON tree.
	// These implicits could instead be defined locally in the BackendForecastProviderImpl,
	// but Stu chose to share them in this top-level object, following common practice in Scala dev community.
	import org.http4s.circe.jsonOf

	implicit val areaPropsDecoder: Decoder[Msg_BackendAreaProps] = deriveDecoder[Msg_BackendAreaProps]
	implicit val areaInfoDecoder: Decoder[Msg_BackendAreaInfo] = deriveDecoder[Msg_BackendAreaInfo]
	implicit val areaInfoEntityDecoder: EntityDecoder[IO, Msg_BackendAreaInfo] = jsonOf
}

trait BackendResponseMapper {
	
	private def wrapAndLogErrors[MsgT](eff: IO[MsgT], opName: String, rqTxt: => String): EitherT[IO, OldeBackendError, MsgT] = {
		val eitherEff: IO[Either[Throwable, MsgT]] = eff.attempt
		val eithT = EitherT(eitherEff)
		val leftWrappedEithT = eithT.leftMap(thrwn => {
			OldeBackendError(opName, rqTxt, thrwn)
		})
		val loggedEff = leftWrappedEithT.leftSemiflatTap((bckErr: OldeBackendError) => {
			IO.blocking {
				// In general a logging call MIGHT be blocking.
				log4s.getLogger.warn(bckErr)(s"${opName} for ${rqTxt} produced error : ${bckErr.asText}  ")
			}
		})
		loggedEff
	}
}
