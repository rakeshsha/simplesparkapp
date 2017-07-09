package com.cloudera.sparkwordcount;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaPairRDD$;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.streaming.Time;
import scala.Tuple2;
import org.apache.spark.sql.Row;

import com.google.common.collect.Lists;
import kafka.serializer.StringDecoder;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.*;
import org.apache.spark.streaming.api.java.*;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.apache.spark.streaming.Durations;

/**
 * Consumes messages from one or more topics in Kafka and does wordcount.
 * Usage: JavaDirectKafkaWordCount <brokers> <topics>
 * <brokers> is a list of one or more Kafka brokers
 * <topics> is a list of one or more kafka topics to consume from
 * <p>
 * Example:
 * $ bin/run-example streaming.JavaDirectKafkaWordCount broker1-host:port,broker2-host:port topic1,topic2
 */

public final class JavaKafkaWordCount {
    private static final Pattern SPACE = Pattern.compile(" ");

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: JavaDirectKafkaWordCount <brokers> <topics>\n" + "  <brokers> is a list of one or more Kafka brokers\n" + "  <topics> is a list of one or more kafka topics to consume from\n\n");
            System.exit(1);
        }


        String brokers = args[0];
        String topics = args[1];

        // Create context with a 2 seconds batch interval
        SparkConf sparkConf = new SparkConf().setMaster("local[1]").setAppName("JavaDirectKafkaWordCount");
        final JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, Durations.seconds(60));

        HashSet<String> topicsSet = new HashSet<String>(Arrays.asList(topics.split(",")));
        HashMap<String, String> kafkaParams = new HashMap<String, String>();
        kafkaParams.put("metadata.broker.list", brokers);
        kafkaParams.put("auto.offset.reset", "smallest");

        // Create direct kafka stream with brokers and topics
        JavaPairInputDStream<String, String> messages = KafkaUtils.createDirectStream(jssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParams, topicsSet);

        messages.print(3);

        // Get the lines, split them into words, count the words and print
        JavaDStream<String> lines = messages.map(new Function<Tuple2<String, String>, String>() {
            @Override
            public String call(Tuple2<String, String> tuple2) {
                return tuple2._2();
            }
        });

        final JavaDStream<String> words = lines.flatMap(new FlatMapFunction<String, String>() {
            @Override
            public Iterable<String> call(String x) {
                return Lists.newArrayList(SPACE.split(x));
            }
        });


        final JavaPairDStream<String, Integer> wordCounts = words.mapToPair(new PairFunction<String, String, Integer>() {
            @Override
            public Tuple2<String, Integer> call(String s) {
                return new Tuple2<String, Integer>(s, 1);
            }
        }).reduceByKey(new Function2<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer i1, Integer i2) {
                return i1 + i2;
            }
        });

        System.out.println("Printing line");
        //lines.print();

        System.out.println("Printing count");
        //lines.print(0);

        System.out.println("Starting json read");

        lines.foreachRDD(
                new Function2<JavaRDD<String>, Time, Void>() {
                    @Override
                    public Void call(JavaRDD<String> rdd, Time time) {

                        // Get the singleton instance of SQLContext
                        SQLContext sqlContext = SQLContext.getOrCreate(rdd.context());

                        DataFrame tweet = sqlContext.read().json(rdd);
                        tweet.select("id","user.location").show();
                        return null;
                    }
                }
                        );


        System.out.println("Printing json schema");
        // The inferred schema can be visualized using the printSchema() method


        System.out.println("Printing word");
        //words.print();


        System.out.println("Printing word count");
        wordCounts.print();
        // Start the computation


        jssc.start();
        jssc.awaitTermination();
    }
}
