/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import _root_.forms.FastPassForm._
import _root_.forms.PersonalDetailsForm
import config.CSRCache
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.exchange.CivilServiceExperienceDetails._
import connectors.exchange.{ CivilServiceExperienceDetails, SelectedSchemes }
import connectors.{ ApplicationClient, SchemeClient, UserManagementClient }
import helpers.NotificationType._
import mappings.{ Address, DayMonthYear }
import models.ApplicationData.ApplicationStatus._
import models.{ ApplicationRoute, CachedDataWithApp }
import org.joda.time.LocalDate
import play.api.data.Form
import play.api.mvc.{ Request, Result }
import security.Roles.{ EditPersonalDetailsAndContinueRole, EditPersonalDetailsRole }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import security.SilhouetteComponent

object PersonalDetailsController extends PersonalDetailsController(ApplicationClient, SchemeClient, CSRCache, UserManagementClient) {
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class PersonalDetailsController(applicationClient: ApplicationClient,
                                schemeClient: SchemeClient,
                                cacheClient: CSRCache,
                                userManagementClient: UserManagementClient)
  extends BaseController(applicationClient, cacheClient) with PersonalDetailsToExchangeConverter {

  private sealed trait OnSuccess
  private case object ContinueToNextStepInJourney extends OnSuccess
  private case object RedirectToTheDashboard extends OnSuccess

  def presentAndContinue = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      personalDetails(afterSubmission = ContinueToNextStepInJourney)
  }

  def present = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      personalDetails(afterSubmission = RedirectToTheDashboard)
  }

  private def personalDetails(afterSubmission: OnSuccess)
                             (implicit user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    implicit val now: LocalDate = LocalDate.now
    val continueToTheNextStep = continuetoTheNextStep(afterSubmission)

    applicationClient.getPersonalDetails(user.user.userID, user.application.applicationId).map { gd =>
      val form = PersonalDetailsForm.form.fill(PersonalDetailsForm.Data(
        gd.firstName,
        gd.lastName,
        gd.preferredName,
        gd.dateOfBirth,
        Some(gd.outsideUk),
        gd.address,
        gd.postCode,
        gd.country,
        gd.phone,
        gd.civilServiceExperienceDetails,
        gd.edipCompleted.map(_.toString)
      ))
      Ok(views.html.application.generalDetails(form, continueToTheNextStep))

    }.recover {
      case e: PersonalDetailsNotFound =>
        val formFromUser = PersonalDetailsForm.form.fill(PersonalDetailsForm.Data(
          user.user.firstName,
          user.user.lastName,
          user.user.firstName,
          DayMonthYear.emptyDate,
          outsideUk = None,
          address = Address.EmptyAddress,
          postCode = None,
          country = None,
          phone = None,
          civilServiceExperienceDetails = EmptyCivilServiceExperienceDetails,
          edipCompleted = None
        ))
        Ok(views.html.application.generalDetails(formFromUser, continueToTheNextStep))
    }
  }

  def submitPersonalDetailsAndContinue() = CSRSecureAppAction(EditPersonalDetailsAndContinueRole) { implicit request =>
    implicit user =>
      val redirect = if(user.application.applicationRoute == ApplicationRoute.Faststream) {
        Redirect(routes.SchemePreferencesController.present())
      } else {
        Redirect(routes.AssistanceDetailsController.present())
      }
      submit(PersonalDetailsForm.form(LocalDate.now), ContinueToNextStepInJourney, redirect)
  }

  def submitPersonalDetails() = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      submit(PersonalDetailsForm.form(LocalDate.now, ignoreFastPassValidations = true), RedirectToTheDashboard,
        Redirect(routes.HomeController.present()).flashing(success("personalDetails.updated")))
  }

  private def continuetoTheNextStep(onSuccess: OnSuccess) = onSuccess match {
    case ContinueToNextStepInJourney => true
    case RedirectToTheDashboard => false
  }

  private def submit(personalDetailsForm: Form[PersonalDetailsForm.Data], onSuccess: OnSuccess, redirectOnSuccess: Result)
                    (implicit cachedData: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]) = {

    val handleFormWithErrors = (errorForm:Form[PersonalDetailsForm.Data]) => {
      Future.successful(Ok(views.html.application.generalDetails(
        personalDetailsForm.bind(errorForm.data.cleanupFastPassFields), continuetoTheNextStep(onSuccess)))
      )
    }

    val handleValidForm = (form: PersonalDetailsForm.Data) => {
      val civilServiceExperienceDetails: Option[CivilServiceExperienceDetails] =
        cachedData.application.civilServiceExperienceDetails.orElse(form.civilServiceExperienceDetails)
      val edipCompleted = cachedData.application.edipCompleted.orElse(form.edipCompleted.map(_.toBoolean))
      for {
        _ <- applicationClient.updatePersonalDetails(cachedData.application.applicationId, cachedData.user.userID,
          toExchange(form, cachedData.user.email, Some(continuetoTheNextStep(onSuccess)), edipCompleted))
        _ <- createDefaultSchemes
        _ <- userManagementClient.updateDetails(cachedData.user.userID, form.firstName, form.lastName, Some(form.preferredName))
        redirect <- updateProgress(data => {
          val applicationCopy = data.application.map(
            _.copy(civilServiceExperienceDetails = civilServiceExperienceDetails, edipCompleted = edipCompleted))
          data.copy(user = cachedData.user.copy(firstName = form.firstName, lastName = form.lastName,
            preferredName = Some(form.preferredName)), application =
            if (continuetoTheNextStep(onSuccess)) applicationCopy.map(_.copy(applicationStatus = IN_PROGRESS)) else applicationCopy)
        })(_ => redirectOnSuccess)
      } yield {
        redirect
      }
    }
    personalDetailsForm.bindFromRequest.fold(handleFormWithErrors, handleValidForm)
  }

  private def createDefaultSchemes(implicit cacheData: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]): Future[Unit] =
    cacheData.application.applicationRoute match {
    case appRoute@(ApplicationRoute.Edip | ApplicationRoute.Sdip) =>
      for {
        _ <- schemeClient.updateSchemePreferences(SelectedSchemes(List(appRoute), orderAgreed = true,
          eligible = true))(cacheData.application.applicationId)
        _ <- env.userService.refreshCachedUser(cacheData.user.userID)
      } yield ()
    case _ => Future.successful(())
  }

}

trait PersonalDetailsToExchangeConverter {

  def toExchange(personalDetails: PersonalDetailsForm.Data, email: String, updateApplicationStatus: Option[Boolean],
                 edipCompleted: Option[Boolean] = None) = {
    val pd = personalDetails.insideUk match {
      case true => personalDetails.copy(country = None)
      case false => personalDetails.copy(postCode = None)
    }
    pd.toExchange(email, updateApplicationStatus, edipCompleted)
  }
}
