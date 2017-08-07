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

import java.nio.file.{ Files, Path }

import com.mohiva.play.silhouette.api.{ LogoutEvent, Silhouette }
import config.CSRCache
import connectors.{ ApplicationClient, ReferenceDataClient, SiftClient }
import connectors.ApplicationClient.{ ApplicationNotFound, CandidateAlreadyHasAnAnalysisExerciseException, CannotWithdraw, OnlineTestNotFound }
import connectors.UserManagementClient.InvalidCredentialsException
import connectors.exchange._
import forms.WithdrawApplicationForm
import helpers.NotificationType._
import helpers.CachedUserMetadata
import models.ApplicationData.ApplicationStatus
import models.page._
import models._
import models.events.EventType
import play.api.Logger
import play.api.mvc.{ Action, AnyContent, Request, Result }
import security.RoleUtils._
import security.{ Roles, SecurityEnvironment, SignInService, SilhouetteComponent }
import security.Roles._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.api.mvc.Results.Redirect

object HomeController extends HomeController(
  ApplicationClient,
  ReferenceDataClient,
  SiftClient,
  SignInController,
  CSRCache
) {
  val appRouteConfigMap: Map[ApplicationRoute.Value, ApplicationRouteStateImpl] = config.FrontendAppConfig.applicationRoutesFrontend
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}

abstract class HomeController(
  applicationClient: ApplicationClient,
  refDataClient: ReferenceDataClient,
  siftClient: SiftClient,
  signInService: SignInService,
  cacheClient: CSRCache
) extends BaseController(applicationClient, cacheClient) with CampaignAwareController {

  val Withdrawer = "Candidate"

  private lazy val validMSWordContentTypes = List(
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  )

  private lazy val maxAnalysisExerciseFileSizeInBytes = 4096 * 1024

  // scalastyle:off cyclomatic.complexity
  def present(implicit displaySdipEligibilityInfo: Boolean = false): Action[AnyContent] = CSRSecureAction(ActiveUserRole) {
    implicit request =>
      implicit cachedData =>
        for {
        page <- cachedData.application.map { implicit application =>
          cachedData match {
            case _ if isPhase1TestsPassed && (isEdip(cachedData) || isSdip(cachedData)) => displayEdipOrSdipResultsPage
            case _ if isPhase3TestsPassed => displayPostOnlineTestsPage
            case _ => dashboardWithOnlineTests.recoverWith(dashboardWithoutOnlineTests)
          }
        }.getOrElse {
          dashboardWithoutApplication
        }
      } yield page
  }
  // scalastyle:on cyclomatic.complexity

  def showSdipNextSteps: Action[AnyContent] = CSRSecureAction(ActiveUserRole) { implicit request =>
    implicit cachedData =>
      implicit val displaySdipEligibilityInfo = false
      cachedData.application.map { implicit application =>
        cachedData match {
          case _ if isPhase1TestsPassed && isSdipFaststream => displayEdipOrSdipResultsPage
          case _ => dashboardWithOnlineTests.recoverWith(dashboardWithoutOnlineTests)
        }
      }.getOrElse {
        dashboardWithoutApplication
      }
  }

  def resume: Action[AnyContent] = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      Future.successful(Redirect(Roles.userJourneySequence.find(_._1.isAuthorized(user)).map(_._2).getOrElse(routes.HomeController.present())))
  }

  def create: Action[AnyContent] = CSRSecureAction(ApplicationStartRole) { implicit request =>
    implicit cachedData =>
      for {
        response <- applicationClient.findApplication(cachedData.user.userID, FrameworkId).recoverWith {
          case _: ApplicationNotFound => applicationClient.createApplication(cachedData.user.userID, FrameworkId)
        }
        _ <- env.userService.save(cachedData.copy(application = Some(response)))
        if canApplicationBeSubmitted(response.overriddenSubmissionDeadline)(response.applicationRoute)
      } yield {
        Redirect(routes.PersonalDetailsController.presentAndContinue())
      }
  }

  def presentWithdrawApplication: Action[AnyContent] = CSRSecureAppAction(AbleToWithdrawApplicationRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.application.withdraw(WithdrawApplicationForm.form)))
  }

  def withdrawApplication: Action[AnyContent] = CSRSecureAppAction(AbleToWithdrawApplicationRole) { implicit request =>
    implicit user =>

      def updateApplicationStatus(data: CachedData): CachedData = {
        data.copy(application = data.application.map { app =>
          app.copy(
            applicationStatus = ApplicationStatus.WITHDRAWN,
            progress = app.progress.copy(withdrawn = true)
          )
        }
        )
      }

      WithdrawApplicationForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.application.withdraw(invalidForm))),
        data => {
          applicationClient.withdrawApplication(user.application.applicationId, WithdrawApplication(data.reason.get, data.otherReason,
            Withdrawer)).flatMap { _ =>
            updateProgress(updateApplicationStatus)(_ =>
              Redirect(routes.HomeController.present()).flashing(success("application.withdrawn", feedbackUrl)))
          }.recover {
            case _: CannotWithdraw => Redirect(routes.HomeController.present()).flashing(danger("error.cannot.withdraw"))
          }
        }
      )
  }

  private def displayPostOnlineTestsPage(implicit application: ApplicationData, cachedData: CachedData,
    request: Request[_], hc: HeaderCarrier) = {
    for {
      allSchemes <- refDataClient.allSchemes()
      schemeStatus <- applicationClient.getCurrentSchemeStatus(application.applicationId)
      siftAnswersStatus <- siftClient.getSiftAnswersStatus(application.applicationId)
      assessmentCentreEvents <- applicationClient.eventWithSessionsForApplicationOnly(application.applicationId, EventType.FSAC)
      assessmentCentreEvent = assessmentCentreEvents.headOption // Candidate can only be assigned to one assessment centre event and session
      hasWrittenAnalysisExercise <- applicationClient.hasAnalysisExercise(application.applicationId)
    } yield {
      val page = PostOnlineTestsPage(
        CachedUserMetadata(cachedData.user, application, allSchemes, schemeStatus),
        assessmentCentreEvent,
        siftAnswersStatus,
        hasWrittenAnalysisExercise
      )
      Ok(views.html.home.postOnlineTestsDashboard(page))
    }
  }

  protected def getAllBytesInFile(path: Path): Array[Byte] = Files.readAllBytes(path)

  def submitAnalysisExercise(): Action[AnyContent] = CSRSecureAppAction(AssessmentCentreRole) { implicit request =>
    implicit cachedData =>
      request.asInstanceOf[Request[AnyContent]].body.asMultipartFormData.flatMap { multiPartRequest =>
        multiPartRequest.file("analysisExerciseFile").map {
          case document if document.ref.file.length() > maxAnalysisExerciseFileSizeInBytes =>
            Future.successful(Redirect(routes.HomeController.present()).flashing(danger("assessmentCentre.analysisExercise.upload.tooBig")))
          case document =>
            document.contentType match {
              case Some(contentType) if validMSWordContentTypes.contains(contentType) =>
                applicationClient.uploadAnalysisExercise(cachedData.application.applicationId, contentType,
                  getAllBytesInFile(document.ref.file.toPath)).map { result =>
                  Redirect(routes.HomeController.present()).flashing(success("assessmentCentre.analysisExercise.upload.success"))
                }.recover {
                  case _: CandidateAlreadyHasAnAnalysisExerciseException =>
                    Logger.warn(s"A duplicate written analysis exercise submission was attempted " +
                      s"(applicationId = ${cachedData.application.applicationId})")
                    Redirect(routes.HomeController.present()).flashing(danger("assessmentCentre.analysisExercise.upload.error"))
                }
              case Some(contentType) =>
                Future.successful(
                  Redirect(routes.HomeController.present()).flashing(danger("assessmentCentre.analysisExercise.upload.wrongContentType"))
                )
            }
        }
      }.getOrElse {
        Logger.info(s"A malformed file request was submitted as a written analysis exercise " +
          s"(applicationId = ${cachedData.application.applicationId})")
        Future.successful(Redirect(routes.HomeController.present()).flashing(danger("assessmentCentre.analysisExercise.upload.error")))
      }
  }

  private def displayEdipOrSdipResultsPage(implicit cachedData: CachedData,
    request: Request[_], hc: HeaderCarrier) =
    Future.successful(Ok(views.html.home.edipAndSdipFinalResults(cachedData)))

  private def dashboardWithOnlineTests(implicit application: ApplicationData,
    displaySdipEligibilityInfo: Boolean,
    cachedData: CachedData, request: Request[_]) = {
    for {
      adjustmentsOpt <- getAdjustments
      assistanceDetailsOpt <- getAssistanceDetails
      phase1TestsWithNames <- applicationClient.getPhase1TestProfile(application.applicationId)
      phase2TestsWithNames <- getPhase2Test
      phase3Tests <- getPhase3Test
      updatedData <- env.userService.refreshCachedUser(cachedData.user.userID)(hc, request)
    } yield {
      val dashboardPage = DashboardPage(updatedData, Some(Phase1TestsPage(phase1TestsWithNames)),
        phase2TestsWithNames.map(Phase2TestsPage(_, adjustmentsOpt)),
        phase3Tests.map(Phase3TestsPage(_, adjustmentsOpt))
      )
      Ok(views.html.home.dashboard(updatedData, dashboardPage, assistanceDetailsOpt, adjustmentsOpt,
        submitApplicationsEnabled = true, displaySdipEligibilityInfo))
    }
  }

  private def dashboardWithoutOnlineTests(implicit application: ApplicationData,
    displaySdipEligibilityInfo: Boolean,
    cachedData: CachedData,
    request: Request[_]): PartialFunction[Throwable, Future[Result]] = {
    case e: OnlineTestNotFound =>
      val applicationSubmitted = !cachedData.application.forall { app =>
        app.applicationStatus == ApplicationStatus.CREATED || app.applicationStatus == ApplicationStatus.IN_PROGRESS
      }
      val isDashboardEnabled = canApplicationBeSubmitted(application.overriddenSubmissionDeadline)(application.applicationRoute) ||
        applicationSubmitted
      val dashboardPage = DashboardPage(cachedData, None, None, None)
      Future.successful(Ok(views.html.home.dashboard(cachedData, dashboardPage,
        submitApplicationsEnabled = isDashboardEnabled,
        displaySdipEligibilityInfo = displaySdipEligibilityInfo)))
  }

  private def dashboardWithoutApplication(implicit cachedData: CachedData,
    displaySdipEligibilityInfo: Boolean,
    request: Request[_]) = {
    val dashboardPage = DashboardPage(cachedData, None, None, None)
    Future.successful(
      Ok(views.html.home.dashboard(cachedData, dashboardPage,
        submitApplicationsEnabled = canApplicationBeSubmitted(None),
        displaySdipEligibilityInfo = displaySdipEligibilityInfo))
    )
  }

  private def getPhase2Test(implicit application: ApplicationData, hc: HeaderCarrier) = if (application.isPhase2) {
    applicationClient.getPhase2TestProfile(application.applicationId).map(Some(_))
  } else {
    Future.successful(None)
  }

  private def getPhase3Test(implicit application: ApplicationData, hc: HeaderCarrier) = if (application.isPhase3) {
    applicationClient.getPhase3TestGroup(application.applicationId).map(Some(_))
  } else {
    Future.successful(None)
  }

  private def getAdjustments(implicit application: ApplicationData, hc: HeaderCarrier) =
    if (application.progress.assistanceDetails) {
      applicationClient.findAdjustments(application.applicationId)
    } else {
      Future.successful(None)
    }

  private def getAssistanceDetails(implicit application: ApplicationData,
    hc: HeaderCarrier, cachedData: CachedData) =
    if (application.progress.assistanceDetails) {
      applicationClient.getAssistanceDetails(cachedData.user.userID, application.applicationId).map(a => Some(a))
    } else {
      Future.successful(None)
    }

}
