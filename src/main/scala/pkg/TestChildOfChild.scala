package pkg

import org.neo4j.ogm.annotation.{GeneratedValue, Id, NodeEntity}

@NodeEntity
case class TestChildOfChild(){

  @Id @GeneratedValue
  var id: java.lang.Long = _

  var value: Long = _
}