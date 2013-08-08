package org.apache.solr.client.solrj.impl

import play.api.test.WithApplication
import akka.actor.Cancellable
import org.apache.solr.client.solrj.response.QueryResponse

import scala.concurrent.Future.successful
import scala.concurrent.Future.failed

import java.io.IOException

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

import org.apache.solr.client.solrj.{SolrServerException, SolrRequest, SolrQuery}
import org.apache.solr.common.util.NamedList
import org.apache.solr.common.params.SolrParams
import org.apache.solr.common.SolrException


/**
 * Test the LBHttpSolrServer.
 */
class AsyncLBHttpSolrServerSpec extends Specification with Mockito {

  val parser = new BinaryResponseParser

  "The LBHttpSolrServer" should {

    "mark failed server as zombie and try another alive server" in new WithApplication {
      val lbServer = createServer()
      val server1 = mock[AsyncHttpSolrServer]
      val server2 = mock[AsyncHttpSolrServer]
      val response = mock[NamedList[Object]]
      val query = new SolrQuery("*:*")
      val failedResponse = failed(new SolrServerException(new IOException()))

      server1.baseUrl returns "http://foo.org:8900/solr"
      server2.baseUrl returns "http://foo.org:8901/solr"

      server1.request(any[SolrRequest]) returns failedResponse
      server1.query(any[SolrParams]) returns failedResponse
      server2.request(any[SolrRequest]) returns successful(response)

      lbServer.addSolrServer(server1)
      lbServer.addSolrServer(server2)

      val futureResponse = lbServer.query(query)

      try {
        there was one(server1).request(any[SolrRequest])
        there was one(server1).baseUrl
        there was one(server2).request(any[SolrRequest])

        lbServer.aliveServerCount must equalTo(1)
        lbServer.zombieServerCount must equalTo(1)
        futureResponse.value.get.isSuccess must beTrue
        futureResponse.value.get.isFailure must beFalse
      } finally {
        lbServer.shutdown()
      }
    }

    "mark all failed servers as zombies and throw an exception" in new WithApplication {
      val lbServer = createServer()
      val server1 = mock[AsyncHttpSolrServer]
      val server2 = mock[AsyncHttpSolrServer]
      val response = mock[NamedList[Object]]
      val query = new SolrQuery("*:*")
      val failedResponse = failed(new SolrServerException(new IOException()))

      server1.baseUrl returns "http://foo.org:8900/solr"
      server2.baseUrl returns "http://foo.org:8901/solr"


      server1.request(any[SolrRequest]) returns failedResponse
      server1.query(any[SolrParams]) returns failedResponse
      server2.request(any[SolrRequest]) returns failedResponse
      server2.query(any[SolrParams]) returns failedResponse

      lbServer.addSolrServer(server1)
      lbServer.addSolrServer(server2)

      val futureResponse = lbServer.query(query)

      try {
        there was one(server1).request(any[SolrRequest])
        there was one(server1).baseUrl
        there was one(server2).request(any[SolrRequest])
        there was one(server2).baseUrl

        lbServer.aliveServerCount must equalTo(0)
        lbServer.zombieServerCount must equalTo(2)
        futureResponse.value.get.isSuccess must beFalse
        futureResponse.value.get.isFailure must beTrue
      } finally {
        lbServer.shutdown()
      }
    }

    "mark zombie servers that recovered from failure as alive" in new WithApplication {
      val lbServer = createServer()
      val server1 = mock[AsyncHttpSolrServer]
      val server2 = mock[AsyncHttpSolrServer]
      val response = mock[NamedList[Object]]
      val query = new SolrQuery("*:*")
      val failedResponse = failed(new SolrServerException(new IOException()))

      server1.baseUrl returns "http://foo.org:8900/solr"
      server2.baseUrl returns "http://foo.org:8901/solr"


      server1.request(any[SolrRequest]) returns failedResponse
      server1.query(any[SolrParams]) returns failedResponse
      server2.request(any[SolrRequest]) returns failedResponse
      server2.query(any[SolrParams]) returns failedResponse

      lbServer.addSolrServer(server1)
      lbServer.addSolrServer(server2)

      val futureResponse = lbServer.query(query)

      try {
        there was one(server1).request(any[SolrRequest])
        there was one(server1).baseUrl
        there was one(server2).request(any[SolrRequest])
        there was one(server2).baseUrl

        lbServer.aliveServerCount must equalTo(0)
        lbServer.zombieServerCount must equalTo(2)
        futureResponse.value.get.isSuccess must beFalse
        futureResponse.value.get.isFailure must beTrue
      } finally {
        // nothing
      }

      val queryResponse = mock[QueryResponse]
      server1.query(any[SolrParams]) returns successful(queryResponse)
      server2.query(any[SolrParams]) returns successful(queryResponse)

      lbServer.checkZombieServers()

      try {
        lbServer.aliveServerCount must equalTo(2)
        lbServer.zombieServerCount must equalTo(0)

      } finally {
        lbServer.shutdown()
      }
    }

    "not mark server as zombie when query error occurs" in new WithApplication {
      val lbServer = createServer()
      val server1 = mock[AsyncHttpSolrServer]
      val server2 = mock[AsyncHttpSolrServer]
      val response = mock[NamedList[Object]]
      val query = new SolrQuery("*:*")
      val exception = mock[SolrException]
      val failedResponse = failed(exception)

      server1.baseUrl returns "http://foo.org:8900/solr"
      server2.baseUrl returns "http://foo.org:8901/solr"

      server1.request(any[SolrRequest]) returns failedResponse
      server2.request(any[SolrRequest]) returns successful(response)

      lbServer.addSolrServer(server1)
      lbServer.addSolrServer(server2)

      val futureResponse = lbServer.query(query)

      try {
        there was one(server1).request(any[SolrRequest])
        there was one(server1).baseUrl
        there was no(server2).request(any[SolrRequest])

        lbServer.aliveServerCount must equalTo(2)
        lbServer.zombieServerCount must equalTo(0)
        futureResponse.value.get.isSuccess must beFalse
        futureResponse.value.get.isFailure must beTrue
      } finally {
        lbServer.shutdown()
      }
    }
  }


  private def createServer() : AsyncLBHttpSolrServer = {
    val server = new AsyncLBHttpSolrServer(parser)

    // prevent alive check thread from starting
    server.aliveCheckActor = mock[Cancellable]
    server
  }


}