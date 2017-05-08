package sharry.server.routes

import shapeless.{::, HNil}
import fs2.{Stream, Task}
import cats.syntax.either._
import spinoco.fs2.http.routing._

import sharry.store.data._
import sharry.server.paths
import sharry.server.config._
import sharry.store.upload.UploadStore
import sharry.server.routes.syntax._
import sharry.server.jsoncodec._

object alias {

  def endpoint(auth: AuthConfig, store: UploadStore) =
    choice(updateAlias(auth, store)
      , createAlias(auth, store)
      , getAlias(store)
      , listAliases(auth, store)
      , deleteAlias(auth, store))

  def updateAlias(authCfg: AuthConfig, store: UploadStore): Route[Task] =
    Post >> paths.aliases.matcher /"update" >> authz.user(authCfg) :: jsonBody[AliasUpdate] map {
      case login :: alias :: HNil =>
        val a = Alias.generate(login, alias.name).
          copy(id = alias.id).
          copy(enable = alias.enable)
        UploadCreate.parseValidity(alias.validity).
          map(v => a.copy(validity = v)).
          map(a => store.updateAlias(a).
            map({ n => if (n == 0) NotFound[Task,String]("0") else Ok[Task,String](n.toString) })).
          valueOr(msg => Stream.emit(BadRequest[Task,String](msg)))
    }

  def createAlias(authCfg: AuthConfig, store: UploadStore): Route[Task] =
    Post >> paths.aliases.matcher >> authz.user(authCfg) map { (login: String) =>
      val alias = Alias.generate(login, "New alias")
      store.createAlias(alias).
        map(_ => Ok[Task,Alias](alias))
    }

  def listAliases(authCfg: AuthConfig, store: UploadStore): Route[Task] =
    Get >> paths.aliases.matcher >> authz.user(authCfg) map { (login: String) =>
      Stream.eval(store.listAliases(login).runLog).
        map(Ok[Task,Vector[Alias]](_))
    }

  def getAlias(store: UploadStore): Route[Task] =
    Get >> paths.aliases.matcher / as[String] map { (id: String) =>
      store.getAlias(id).
        map(Ok[Task,Alias](_)).
        through(NotFound.whenEmpty)
    }

  def deleteAlias(authCfg: AuthConfig, store: UploadStore): Route[Task] =
    Delete >> paths.aliases.matcher / as[String] :: authz.user(authCfg) map {
      case id :: login :: HNil =>
        store.deleteAlias(id, login).
          map({ n => if (n == 0) NotFound("0") else Ok(n.toString) })
    }
}
