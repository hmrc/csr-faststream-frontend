/*
 * Copyright 2016 HM Revenue & Customs
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

import config.CSRCache
import connectors.ApplicationClient
import connectors.ApplicationClient.CannotSubmit
import helpers.NotificationType._
import models.ApplicationData.ApplicationStatus.SUBMITTED
import models.CachedData
import security.Roles.{ SubmitApplicationRole, WithdrawApplicationRole }

import scala.concurrent.Future

object SubmitApplicationController extends SubmitApplicationController(ApplicationClient, CSRCache)

class SubmitApplicationController(applicationClient: ApplicationClient, cacheClient: CSRCache)
  extends BaseController(applicationClient, cacheClient) {

  def present = CSRSecureAppAction(SubmitApplicationRole) { implicit request =>
    implicit user =>
      if (faststreamConfig.applicationsSubmitEnabled) {
        Future.successful(Ok(views.html.application.submit()))
      } else {
        Future.successful(Ok(views.html.home.submit_disabled(CachedData(user.user, Some(user.application)))))
      }
  }

  def success = CSRSecureAppAction(WithdrawApplicationRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.application.success()))
  }

  def submit = CSRSecureAppAction(SubmitApplicationRole) { implicit request =>
    implicit user =>
      if (faststreamConfig.applicationsSubmitEnabled) {
        applicationClient.submitApplication(user.user.userID, user.application.applicationId).flatMap { _ =>
          updateProgress(data =>
            data.copy(application = data.application.map(_.copy(applicationStatus = SUBMITTED))))(_ =>
            Redirect(routes.SubmitApplicationController.success()))
        }.recover {
          case _: CannotSubmit => Redirect(routes.PreviewApplicationController.present()).flashing(danger("error.cannot.submit"))
        }
      } else {
        Future.successful(Ok(views.html.home.submit_disabled(CachedData(user.user, Some(user.application)))))
      }
  }
}
