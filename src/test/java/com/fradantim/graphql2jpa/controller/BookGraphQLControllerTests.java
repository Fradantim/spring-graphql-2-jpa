package com.fradantim.graphql2jpa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fradantim.graphql2jpa.entity.Book;
import com.fradantim.graphql2jpa.entity.Person;
import com.fradantim.graphql2jpa.entity.Quote;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BookGraphQLControllerTests {

	@Value("http://localhost:${local.server.port}")
	private String localUrl;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void queryBookAllRealFieldsTest() {
		String queryValue = """
				{
					findBookById(id:1) {
						id name isbn
						author {id name}
						quotes {id text}
						reviewers {id name}
					}
				}
				""";

		Map<String, Object> requestBody = Map.of("query", queryValue);
		RequestEntity<Map<String, Object>> request = RequestEntity.post(localUrl + "/graphql").body(requestBody);

		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request,
				new ParameterizedTypeReference<>() {
				});

		Book book = getGraphQLQueryResult(response, "findBookById", Book.class);

		assertThat(book).hasAllNullFieldsOrPropertiesExcept("id", "name", "isbn", "author", "quotes", "reviewers");

		Person author = book.getAuthor();
		assertThat(author).hasAllNullFieldsOrPropertiesExcept("id", "name");

		assertThat(book.getQuotes()).hasSize(3);
		for (Quote quote : book.getQuotes()) {
			assertThat(quote).hasAllNullFieldsOrPropertiesExcept("id", "text");
		}

		assertThat(book.getReviewers()).hasSize(2);
		for (Person reviewer : book.getReviewers()) {
			assertThat(reviewer).hasAllNullFieldsOrPropertiesExcept("id", "name");
		}
	}

	@Test
	void queryBookOnlyIdsTest() {
		String queryValue = """
				{
					findBookById(id:1) {
						id
						author {id}
						quotes {id}
						reviewers {id}
					}
				}
				""";

		Map<String, Object> requestBody = Map.of("query", queryValue);
		RequestEntity<Map<String, Object>> request = RequestEntity.post(localUrl + "/graphql").body(requestBody);

		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request,
				new ParameterizedTypeReference<>() {
				});

		Book book = getGraphQLQueryResult(response, "findBookById", Book.class);

		assertThat(book).hasAllNullFieldsOrPropertiesExcept("id", "author", "quotes", "reviewers");

		Person author = book.getAuthor();
		assertThat(author).hasAllNullFieldsOrPropertiesExcept("id");

		assertThat(book.getQuotes()).hasSize(3);
		for (Quote quote : book.getQuotes()) {
			assertThat(quote).hasAllNullFieldsOrPropertiesExcept("id");
		}

		assertThat(book.getReviewers()).hasSize(2);
		for (Person reviewer : book.getReviewers()) {
			assertThat(reviewer).hasAllNullFieldsOrPropertiesExcept("id");
		}
	}
	
	@Test
	void queryBookIdsAreIncludedTest() {
		String queryValue = """
				{
					findBookById(id:1) {
						name
						author {name}
						quotes {text}
						reviewers {name}
					}
				}
				""";

		Map<String, Object> requestBody = Map.of("query", queryValue);
		RequestEntity<Map<String, Object>> request = RequestEntity.post(localUrl + "/graphql").body(requestBody);

		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request,
				new ParameterizedTypeReference<>() {
				});

		Book book = getGraphQLQueryResult(response, "findBookById", Book.class);

		assertThat(book).hasAllNullFieldsOrPropertiesExcept("id", "name", "author", "quotes", "reviewers");

		Person author = book.getAuthor();
		assertThat(author).hasAllNullFieldsOrPropertiesExcept("id", "name");

		assertThat(book.getQuotes()).hasSize(3);
		for (Quote quote : book.getQuotes()) {
			assertThat(quote).hasAllNullFieldsOrPropertiesExcept("id", "text");
		}

		assertThat(book.getReviewers()).hasSize(2);
		for (Person reviewer : book.getReviewers()) {
			assertThat(reviewer).hasAllNullFieldsOrPropertiesExcept("id", "name");
		}
	}

	@Test
	void queryBookWithMissingColumnsTest() {
		String queryValue = """
				{
					findBookById(id:1) {
						id nonExistingColumnA
						author {id}
						quotes {id}
						reviewers {id}
					}
				}
				""";

		Map<String, Object> requestBody = Map.of("query", queryValue);
		RequestEntity<Map<String, Object>> request = RequestEntity.post(localUrl + "/graphql").body(requestBody);

		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request,
				new ParameterizedTypeReference<>() {
				});

		assertThat(response).isNotNull().extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull().containsKey("errors").extracting(m -> m.get("errors"))
				.isInstanceOf(Collection.class);

		@SuppressWarnings({ "rawtypes", "unchecked" })
		Collection<Map<String, Object>> errors = (Collection) response.getBody().get("errors");
		assertThat(errors).isNotEmpty();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private <T> T getGraphQLQueryResult(ResponseEntity<Map<String, Object>> response, String query, Class<T> type) {
		assertThat(response).isNotNull().extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull().containsKey("data").extracting(m -> m.get("data"))
				.isInstanceOf(Map.class);
		Map<String, Object> data = (Map) response.getBody().get("data");
		assertThat(data).containsKey(query).extracting(m -> m.get(query)).isInstanceOf(Map.class);
		Map<String, Object> findBookById = (Map) data.get(query);
		assertThat(findBookById).isNotNull().isNotEmpty();

		return objectMapper.convertValue(findBookById, type);
	}
}
