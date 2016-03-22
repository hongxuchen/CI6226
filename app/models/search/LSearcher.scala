package models.search

import java.nio.file.{Files, Paths}

import models.common.{Config, LAnalyzer, LOption}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import play.api.Logger
import play.api.libs.json._

import scala.collection.JavaConversions._


case class LSearchPub(docID: Int, score: Double, info: Map[String, String])

case class LSearchStats(timecode: Long, queryString: String, query: Option[Query]) {
  def toJson(): JsValue = {
    val parsedQuery = {
      query match {
        case Some(q) => JsString(q.toString())
        case None => JsNull
      }
    }
    JsObject(Seq(
      "time" -> JsString(time.toString + "ms"),
      "queryString" -> JsString(queryString),
      "parsedQuery" -> parsedQuery
    ))
  }
}

class LSearchResult(stats: LSearchStats, lOption: Option[LOption], val pubs: Array[LSearchPub]) {
  def toJson(): JsValue = {
    val lOptionJson = {
      lOption match {
        case Some(l) => l.toJson()
        case None => JsNull
      }
    }
    JsObject(Seq(
      "stats" -> stats.toJson(),
      "found" -> JsNumber(pubs.length),
      "searchOption" -> lOptionJson
    ))
  }

  def toJsonString(): String = {
    Json.prettyPrint(toJson())
  }
}

class LSearcher(lOption: LOption, indexFolderString: String, topN: Int) {
  val analyzer = new LAnalyzer(lOption, None)
  val reader = {
    val indexFolder = Paths.get(indexFolderString)
    require(Files.exists(indexFolder))
    val directory = FSDirectory.open(indexFolder)
    DirectoryReader.open(directory)
  }

  val searcher = {
    val s = new IndexSearcher(reader)

    //    val similarity = new BM25Similarity()
    //    s.setSimilarity(similarity)
    s
  }

  private def validate(queryString: String): Unit = {
  }

  def getTopFreq(topDocs: TopDocs): List[(String, Long)] = {
    List.empty
  }

  def evaluate(queryString: String): List[(String, Long)] = {
    val queryOrNone = Option {
      val parser = new QueryParser(Config.I_PUB_YEAR, analyzer)
      parser.setAllowLeadingWildcard(false)
      parser.parse(queryString)
    }
    Logger.info(s"string=$queryString, query=$queryOrNone")
    queryOrNone match {
      case None => List.empty
      case Some(query) => {
        val collector = new TotalHitCountCollector()
        val result = searcher.search(query, collector.getTotalHits)
        getTopFreq(result)
      }
    }
  }

  def search(queryString: String): LSearchResult = {
    val startTime = System.currentTimeMillis()
    validate(queryString)
    val queryOrNone = Option {
      val parser = new QueryParser(Config.COMBINED_FIELD, analyzer)
      parser.setAllowLeadingWildcard(true)
      parser.parse(queryString)
    }
    Logger.info(s"string=$queryString, query=$queryOrNone")
    queryOrNone match {
      case None => {
        val searchStats = LSearchStats(0, queryString, queryOrNone)
        new LSearchResult(searchStats, Some(lOption), Array())
      }
      case Some(query) => {
        val topDocs = searcher.search(query, topN)
        val duration = System.currentTimeMillis() - startTime
        val foundPubs = getSearchPub(topDocs, query)
        reader.close()
        val searchStats = LSearchStats(duration, queryString, queryOrNone)
        new LSearchResult(searchStats, Some(lOption), foundPubs)
      }
    }
  }

  def getSearchPub(topDocs: TopDocs, query: Query): Array[LSearchPub] = {
    for (hit <- topDocs.scoreDocs) yield {
      val (docID, score) = (hit.doc, hit.score)
      val hitDoc = searcher.doc(docID)
      //      Logger.info(s"Explain: ${searcher.explain(query, docID)}")
      val fieldValues = for {
        field <- hitDoc.getFields
        if field.name() != Config.COMBINED_FIELD
      } yield field.name() -> field.stringValue()
      val fieldDocMap = Map(fieldValues: _*)
      new LSearchPub(docID, score, fieldDocMap)
    }
  }

}