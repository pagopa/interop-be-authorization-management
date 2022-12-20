package it.pagopa.interop.authorizationmanagement.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.AkkaResponses

import scala.util.{Failure, Success, Try}

object ClientApiResponseHandlers extends AkkaResponses {

  def createClientResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                         => success(s)
      case Failure(ex: ClientAlreadyExisting) => conflict(ex, logMessage)
      case Failure(ex)                        => internalServerError(ex, logMessage)
    }

  def getClientResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                       => success(s)
      case Failure(ex: ClientNotFoundError) => notFound(ex, logMessage)
      case Failure(ex)                      => internalServerError(ex, logMessage)
    }

  def listClientsResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def addRelationshipResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                       => success(s)
      case Failure(ex: ClientNotFoundError) => notFound(ex, logMessage)
      case Failure(ex)                      => internalServerError(ex, logMessage)
    }

  def deleteClientResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                       => success(s)
      case Failure(ex: ClientNotFoundError) => notFound(ex, logMessage)
      case Failure(ex)                      => internalServerError(ex, logMessage)
    }

  def removeClientRelationshipResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                  => success(s)
      case Failure(ex: ClientNotFoundError)            => notFound(ex, logMessage)
      case Failure(ex: PartyRelationshipNotFoundError) => notFound(ex, logMessage)
      case Failure(ex)                                 => internalServerError(ex, logMessage)
    }

  def getClientByPurposeIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                  => success(s)
      case Failure(ex: ClientWithPurposeNotFoundError) => notFound(ex, logMessage)
      case Failure(ex)                                 => internalServerError(ex, logMessage)
    }

}
