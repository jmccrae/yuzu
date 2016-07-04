package org.insightcentre.nlp.yuzu

import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import scala.util.{Try, Success, Failure}

case class DianthusID(id : Array[Byte]) {
  assert(id.length == 9)
  lazy val base64 = DianthusID.base64encoder.encodeToString(id)

  private def onesInByte(b : Int) = {
    ((b >> 7) & 1) +
    ((b >> 6) & 1) +
    ((b >> 5) & 1) +
    ((b >> 4) & 1) +
    ((b >> 3) & 1) +
    ((b >> 2) & 1) +
    ((b >> 1) & 1) +
    ((b     ) & 1)
  }

  def xor(id2 : DianthusID) = (0 to 8).map({ i =>
    onesInByte(id(i) ^ id2.id(i))
  }).sum

  override def toString = "DianthusID(%s)" format base64
  override def equals(any : Any) = any match {
    case DianthusID(id2) => java.util.Arrays.equals(id, id2)
    case _ => false
  }
  override def hashCode = java.util.Arrays.hashCode(id)
}

object DianthusID {
  lazy val base64encoder = Base64.getEncoder()
  lazy val base64decoder = Base64.getDecoder()
  def apply(id : String) = {
    if(id.length != 12) throw new IllegalArgumentException("Dianthus ID must be string of length 12")
    new DianthusID(base64decoder.decode(id))
  }

  def make(content : String) = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(content.getBytes("UTF-8"))
    new DianthusID(digest.digest().take(9))
  }
}

sealed trait DianthusResult
sealed trait DianthusLocalResult extends DianthusResult
case class DianthusStoredLocally(id : String) extends DianthusLocalResult
case class DianthusInBackup(content : String, format : ResultType) extends DianthusLocalResult
case class DianthusRedirect(url : URL) extends DianthusResult
case class DianthusFailed() extends DianthusResult

class Dianthus(peers : Seq[URL], backend : Backend) {
  private val serverResponse = "[A-Za-z0-9/\\+]{12} \\d+".r

  def peerIds : Map[URL, (DianthusID, Int)] = {
    val m : collection.parallel.ParSeq[(URL, (DianthusID, Int))] = peers.par.flatMap({ peerUrl =>
      Try(io.Source.fromURL(peerUrl).mkString) match {
        case Success(serverResponse(id, dist)) =>
          Some(peerUrl -> (DianthusID(id), dist.toInt))
        case _ =>
          System.err.println("Could not retrieve ID from " + peerUrl)
          None
      }
    })
    val m2 : List[(URL, (DianthusID, Int))] = m.toList
    m2.toMap
  }

  def find(id : DianthusID) : DianthusResult = {
    backend.lookup(id) match {
      case Some(doc) =>
        doc
      case None =>
        val thisDist = (backend.dianthusId xor id) - backend.dianthusDist
        val pids = peerIds
        peers.map(p => {
          val (did, dist) = pids(p)
          (p, (id xor did) - dist)
        }).filter(_._2 >= 0).filter(_._2 < thisDist)
        peers.headOption.map(DianthusRedirect(_)).getOrElse(DianthusFailed())
    }
  }
}

class DianthusBackupService(poolSize : Int, peers : Seq[URL]) {
  import java.util.concurrent.{Executors, ExecutorService}
  import java.net.HttpURLConnection
  private val serverResponse = "[A-Za-z0-9/\\+]{12} \\d+".r

  private val peerIds = {
    val m : collection.parallel.ParSeq[(URL, (DianthusID, Int))] = peers.par.flatMap({ peerUrl =>
      Try(io.Source.fromURL(peerUrl).mkString) match {
        case Success(serverResponse(id, dist)) =>
          Some(peerUrl -> (DianthusID(id), dist.toInt))
        case _ =>
          System.err.println("Could not retrieve ID from " + peerUrl)
          None
      }
    })
    val m2 : List[(URL, (DianthusID, Int))] = m.toList
    m2.toMap
  }

  private val pool: ExecutorService = Executors.newFixedThreadPool(poolSize)

  def backup(content : String, format : ResultType) = {
    val did = DianthusID.make(content)
    peerIds.filter({
      case (_, (did2, dist)) => (did xor did2) - dist >= 0
    }).keys.foreach({ peer =>
      pool.execute(new Runnable {
        def run() {
          post(content, format, did, peer)
        }
      })
    })
  }

  def post(content : String, format : ResultType, did : DianthusID, peer : URL) {
    val url = new URL(peer, did.base64)
    val httpCon = url.openConnection().asInstanceOf[HttpURLConnection]
    httpCon.setDoOutput(true)
    httpCon.setRequestMethod("PUT")
    val out = new java.io.OutputStreamWriter(
            httpCon.getOutputStream())
    out.write(format.name + " " + content)
    out.close()
    httpCon.getInputStream()
  }

  def close {
    pool.shutdown()
  }
}
