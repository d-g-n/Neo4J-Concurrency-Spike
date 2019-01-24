package pkg

import java.util

import org.neo4j.ogm.annotation.{GeneratedValue, Id, NodeEntity, Relationship}

@NodeEntity
case class TestChild(){
  @Id @GeneratedValue
  var id: java.lang.Long = _

  @Relationship(`type` = "private", direction = "OUTGOING")
  var childOfChild: java.util.Collection[TestChildOfChild] = new util.ArrayList[TestChildOfChild]()
}