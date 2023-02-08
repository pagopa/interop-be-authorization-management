package it.pagopa.interop.authorizationmanagement.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.AkkaResponses

import scala.util.{Failure, Success, Try}

object KeyApiResponseHandlers extends AkkaResponses {

  def getClientKeyByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: ClientKeyNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def getClientKeysResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                       => success(s)
      case Failure(ex: ClientNotFoundError) => notFound(ex, logMessage)
      case Failure(ex)                      => internalServerError(ex, logMessage)
    }

  def deleteClientKeyByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: ClientKeyNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def getEncodedClientKeyByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: ClientKeyNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def createKeysResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                    => success(s)
      case Failure(ex: CreateKeysBadRequest)             => badRequest(ex, logMessage)
      case Failure(ex: PartyRelationshipNotAllowedError) => badRequest(ex, logMessage)
      case Failure(ex: InvalidKeys)                      => badRequest(ex, logMessage)
      case Failure(ex: KeysAlreadyExist)                 => conflict(ex, logMessage)
      case Failure(ex)                                   => internalServerError(ex, logMessage)
    }

}
