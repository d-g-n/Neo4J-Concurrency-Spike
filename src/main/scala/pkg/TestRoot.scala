package pkg

import java.util
import java.util.UUID

import org.neo4j.ogm.annotation._

@NodeEntity
case class TestRoot(){

  @Id
  var uuid: String = _
  @Relationship(`type` = "private", direction = "OUTGOING")
  var child: java.util.Collection[TestChild] = new util.ArrayList[TestChild]()

  var value : java.lang.Long = _

}