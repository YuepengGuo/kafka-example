package com.despegar.p13n.kafka.client

import kafka.consumer.ConsumerIterator
import kafka.consumer.KafkaStream
import java.util.Properties
import kafka.consumer.ConsumerConfig
import kafka.consumer.Consumer
import collection.JavaConverters._
import collection.JavaConversions._
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

object HighLevelConsumer extends App {
  val zookeeper = "localhost:3181,localhost:4181,localhost:2181/p13n-kafka"
  val groupId = "highLevelConsumer"
  val topic = "testTopic"
  val threads = 3

  val consumer = Consumer.createJavaConsumerConnector(createConsumerConfig(zookeeper, groupId))
  var executor: ExecutorService = _
  startup(threads)

  try {
    Thread.sleep(10000);
  } catch {
    case ie: InterruptedException =>
  }
  shutdown
  
  def startup(numThreads: Integer) {
    println(s"Starting consumer with $numThreads threads")
    val topicCountMap = Map(topic -> numThreads)
    val consumerMap = consumer.createMessageStreams(topicCountMap.asJava)
    val streams = consumerMap.get(topic)

    // launch all the threads
    executor = Executors.newFixedThreadPool(numThreads)

    // create an object to consume the messages
    var threadNumber = 0
    for (stream <- streams) {
      executor.submit(new HighLevelConsumer(stream, threadNumber))
      threadNumber += 1
    }
  }
  
  def shutdown {
    if(consumer != null) consumer.shutdown()
    if(executor != null) executor.shutdown()
    
    try {
      if(!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
        println("Timed out waiting for consumer threads to shut down, exiting uncleanly")
      }
    } catch {
      case e: InterruptedException => println("Interrupted during shutdown, exiting uncleanly")
    }
  }
  
  def createConsumerConfig(zookeper: String, groupId: String) = {
    val props: Properties = new Properties()
    props.put("zookeeper.connect", zookeper)
    props.put("group.id", groupId)
    props.put("zookeeper.session.timeout.ms", "400")
    props.put("zookeeper.sync.time.ms", "200")
    props.put("auto.commit.interval.ms", "1000")
    new ConsumerConfig(props)
  }
}

class HighLevelConsumer(stream: KafkaStream[Array[Byte], Array[Byte]], threadNumber: Int) extends Runnable {
  override def run {
    val it: ConsumerIterator[Array[Byte], Array[Byte]] = stream.iterator()
    for (kafkaMessage <- it) {
      println(s"Thread $threadNumber: ${new String(kafkaMessage.message())}")
    }

    println(s"Shutting down Thread $threadNumber")
  }
}