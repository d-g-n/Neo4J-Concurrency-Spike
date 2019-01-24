import java.util.UUID

import org.neo4j.ogm.annotation.{GeneratedValue, Id, NodeEntity, Relationship}
import org.neo4j.ogm.config.Configuration
import org.neo4j.ogm.session.SessionFactory
import org.neo4j.ogm.transaction.Transaction
import pkg.{TestChild, TestChildOfChild, TestRoot}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
object Test {

  def getAndUpdateNode(rootUUID: String, sessionFactory: SessionFactory): Future[Unit] = {

    Future{
      val insertSesh = sessionFactory.openSession()
      val trans = insertSesh.beginTransaction(Transaction.Type.READ_WRITE)

      try{

        // best case assumption, you acquiring a lock on the relevant top level node that the store is interacting with
        // should reduce 99% of concurrency issues, still need to be cautious around writing repository functionality that can
        // mutate other nodes but honestly even public-public should still be fine, worst case you can write the below query to
        // "start" from n, then look around for all top level nodes it's likely to edit at a depth of 1 and lock them,
        // seems optimistic and not very smart though.

        // ideally though we should practise some form of logical distinction/store definition at for each top level node,
        // then it's just the really complex queries we have to ensure we're properly locking on

        // i can't really see any other way around this because it's a fundamental design requirement on our side, and a limitation on theirs

        // again though, most of the existing behaviour is caused by plain upserts, and that'll likely be what most of the
        // actual functionality reflects but i agree that there are edge cases.
        val res = insertSesh.queryForObject[TestRoot](classOf[TestRoot],
          s"""
             |MATCH(n:TestRoot{uuid:"$rootUUID"})
             |SET n._lock_ = true
             |RETURN n
             |""".stripMargin, Map[String, Object]().asJava)

        //val res = insertSesh.load(classOf[TestRoot], rootUUID)
        //insertSesh.load()

        // add a child and mutate asynchronously
        res.value = res.value + 1
        res.child.add(TestChild())

        // actually do the insertion, save uses the @Id annotated property to decide on insert vs upsert, it's uuid for me
        insertSesh.save(res)

        // nuke the lock
        insertSesh.query(
          s"""
             |MATCH(n:TestRoot{uuid:"$rootUUID"})
             |REMOVE n._lock_
             |""".stripMargin, Map[String, Object]().asJava)

        trans.commit()
      }catch {
        case e: Exception =>
          e.printStackTrace()
          trans.rollback()
      }finally {
        insertSesh.clear()
        trans.close()
      }
    }
  }

  def main(args: Array[String]): Unit = {

    val config = new Configuration.Builder()
      .uri("bolt://neo4j:neo4j@localhost")
      .build()

    val sessionFactory = new SessionFactory(config, "pkg")

    (1 to 10).foreach{
      i =>

        val testChildOfChild = TestChildOfChild()
        val testChild = TestChild()
        testChild.childOfChild = Set(testChildOfChild).asJava
        val root = TestRoot()
        root.child = List(testChild).asJava
        root.uuid = UUID.randomUUID().toString
        root.value = 0L

        val initialSesh = sessionFactory.openSession()

        println("purging db")
        initialSesh.query("MATCH(n) DETACH DELETE n", Map[String, Object]().asJava)

        println("saving initial node")
        initialSesh.save(root)

        println(s"running test case $i")
        val first = Future.sequence((1 to 10).map{
          _ =>
            getAndUpdateNode(root.uuid, sessionFactory)
        })

        val second = Future.sequence((1 to 10).map{
          _ =>
            getAndUpdateNode(root.uuid, sessionFactory)
        })

        val third = Future.sequence((1 to 10).map{
          _ =>
            getAndUpdateNode(root.uuid, sessionFactory)
        })


        Await.result(Future.sequence(Set(first, second, third)), Duration.Inf)

        // required because otherwise the cache provides stale since i'm reusing an earlier sesh
        initialSesh.clear()
        val origNode = initialSesh.load(classOf[TestRoot], root.uuid)

        println(s"validating assertions $i")
        assert(origNode.value == 30)
        assert(origNode.child.size() == 31)
        println("validated")
        println()
    }
  }

}
