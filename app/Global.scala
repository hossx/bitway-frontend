import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.Logger
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter
import scala.concurrent._
import com.coinport.bitway._

//object Global extends WithFilters(new GzipFilter(), SecurityHeadersFilter()) with GlobalSettings {
object Global extends WithFilters(new GzipFilter()) with GlobalSettings {
  override def onStart(app: Application) {
    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")

  }

  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound(
      views.html.notfoundPage()
    ))
  }

  /*

  override def doFilter(next: EssentialAction): EssentialAction = {
    Filters(super.doFilter(next), LoggingFilter)
  }


  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest("Bad Request: " + error))
  }*/

  // override def onError(request: RequestHeader, ex: Throwable) = {
  //   Future.successful(InternalServerError(
  //     views.html.errorPage("internalServerErrorKey")
  //   ))
  // }

}
