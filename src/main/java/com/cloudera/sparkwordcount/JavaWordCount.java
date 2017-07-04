/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.cloudera.sparkwordcount;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.sql.SQLContext;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.apache.spark.sql.*;

import static java.lang.System.exit;

public class JavaWordCount {
  public static void main(String[] args) {

      System.out.println("Starting spark session");
    JavaSparkContext sc = new JavaSparkContext(new SparkConf().setMaster("local").setAppName("Spark Count"));

      SQLContext sqlContext = new org.apache.spark.sql.SQLContext(sc);

      System.out.println("Starting json read");
      DataFrame people = sqlContext.read().json("hdfs://quickstart.cloudera:8020/tmp/kafka/channel/FlumeTweet.1498965473481.json");

      System.out.println("Printing json schema");
// The inferred schema can be visualized using the printSchema() method
      people.printSchema();

    final int threshold = Integer.parseInt(args[1]);
    
    // split each document into words
    JavaRDD<String> tokenized = sc.textFile(args[0]).flatMap(
      new FlatMapFunction<String, String>() {
        @Override
        public Iterable<String> call(String s) {
          return Arrays.asList(s.split(" "));
        }
      }
    );
    
    // count the occurrence of each word
    JavaPairRDD<String, Integer> counts = tokenized.mapToPair(
      new PairFunction<String, String, Integer>() {
        @Override
        public Tuple2<String, Integer> call(String s) {
          return new Tuple2<String, Integer>(s, 1);
        }
      }
    ).reduceByKey(
      new Function2<Integer, Integer, Integer>() {
        @Override
        public Integer call(Integer i1, Integer i2) {
          return i1 + i2;
        }
      }
    );
    
    // filter out words with less than threshold occurrences
    JavaPairRDD<String, Integer> filtered = counts.filter(
      new Function<Tuple2<String, Integer>, Boolean>() {
        @Override
        public Boolean call(Tuple2<String, Integer> tup) {
          return tup._2() >= threshold;
        }
      }
    );
    
    // count characters
    JavaPairRDD<Character, Integer> charCounts = filtered.flatMap(
      new FlatMapFunction<Tuple2<String, Integer>, Character>() {
        @Override
        public Iterable<Character> call(Tuple2<String, Integer> s) {
          Collection<Character> chars = new ArrayList<Character>(s._1().length());
          for (char c : s._1().toCharArray()) {
            chars.add(c);
          }
          return chars;
        }
      }
    ).mapToPair(
      new PairFunction<Character, Character, Integer>() {
        @Override
        public Tuple2<Character, Integer> call(Character c) {
          return new Tuple2<Character, Integer>(c, 1);
        }
      }
    ).reduceByKey(
      new Function2<Integer, Integer, Integer>() {
        @Override
        public Integer call(Integer i1, Integer i2) {
          return i1 + i2;
        }
      }
    );
    
    System.out.println(charCounts.collect());
  }
}
