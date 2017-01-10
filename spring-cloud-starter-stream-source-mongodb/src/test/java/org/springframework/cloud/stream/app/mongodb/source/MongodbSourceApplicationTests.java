/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.mongodb.source;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * @author Adam Zwickey
 * @author ARtem Bilan
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(
		classes = MongodbSourceApplicationTests.MongoSourceApplication.class,
		properties = {
				"spring.data.mongodb.port=0",
				"mongodb.collection=testing"
		})
@DirtiesContext
public abstract class MongodbSourceApplicationTests {

	@Autowired
	private MongoClient mongo;

	@Autowired
	protected Source source;

	@Autowired
	protected MessageCollector messageCollector;

	@Before
	public void setUp() {
		MongoDatabase database = this.mongo.getDatabase("test");
		database.createCollection("testing");
		MongoCollection<Document> collection = database.getCollection("testing");
		collection.insertOne(new Document("greeting", "hello"));
		collection.insertOne(new Document("greeting", "hola"));
	}


	@TestPropertySource(properties = "trigger.fixedDelay=1")
	public static class DefaultTests extends MongodbSourceApplicationTests {

		@Test
		public void test() throws InterruptedException {
			Message<?> received =
					this.messageCollector
							.forChannel(this.source.output())
							.poll(2, TimeUnit.SECONDS);
			assertThat(received, notNullValue());
			assertThat(received.getPayload(), instanceOf(String.class));
		}

	}

	@TestPropertySource(properties = {
			"mongodb.query={ 'greeting': 'hola' }",
			"trigger.fixedDelay=1" })
	public static class ValidQueryTests extends MongodbSourceApplicationTests {

		@Test
		public void test() throws InterruptedException {
			Message<?> received =
					this.messageCollector
							.forChannel(this.source.output())
							.poll(2, TimeUnit.SECONDS);
			assertThat(received, notNullValue());
			assertThat((String) received.getPayload(), containsString("hola"));
		}

	}

	@TestPropertySource(properties = {
			"mongodb.query={ 'greeting': 'bogus' }",
			"trigger.fixedDelay=1" })
	public static class InvalidQueryTests extends MongodbSourceApplicationTests {

		@Test
		public void test() throws InterruptedException {
			Message<?> received =
					this.messageCollector
							.forChannel(this.source.output())
							.poll(2, TimeUnit.SECONDS);
			assertThat(received, nullValue());
		}

	}

	@TestPropertySource(properties = {
			"trigger.fixedDelay=1",
			"mongodb.split=false" })
	public static class NoSplitTests extends MongodbSourceApplicationTests {

		@Test
		public void test() throws InterruptedException {
			Message<?> received =
					this.messageCollector
							.forChannel(this.source.output())
							.poll(2, TimeUnit.SECONDS);
			assertThat(received, notNullValue());
			assertThat(received.getPayload(), instanceOf(List.class));
			assertThat(received.getPayload().toString(), containsString("hola"));
			assertThat(received.getPayload().toString(), containsString("hello"));
		}

	}

	@TestPropertySource(properties = "trigger.fixedDelay=100")
	public static class MongoTriggerTests extends MongodbSourceApplicationTests {

		@Test
		public void test() throws InterruptedException {
			Message<?> received =
					this.messageCollector
							.forChannel(this.source.output())
							.poll(1, TimeUnit.SECONDS);
			assertThat(received, nullValue());
		}

	}

	@TestPropertySource(properties = {
			"mongodb.query-expression=new BasicQuery('{ }')" +
					".limit(1)" +
					".with(new org.springframework.data.domain.Sort('greeting'))",
			"trigger.fixedDelay=1",
			"mongodb.split=false" })
	public static class QueryDslTests extends MongodbSourceApplicationTests {

		@Test
		public void test() throws InterruptedException {
			Message<?> received =
					this.messageCollector
							.forChannel(this.source.output())
							.poll(2, TimeUnit.SECONDS);
			assertThat(received, notNullValue());
			assertThat(received.getPayload(), instanceOf(List.class));
			assertThat(received.getPayload().toString(), containsString("hello"));
			assertThat(received.getPayload().toString(), not(containsString("hola")));
		}

	}


	@SpringBootApplication
	public static class MongoSourceApplication {

	}

}
