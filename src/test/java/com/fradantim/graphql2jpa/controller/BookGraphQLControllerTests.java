package com.fradantim.graphql2jpa.controller;

import static org.assertj.core.api.Assertions.assertThat;

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

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	void queryBookTest() {
		String queryValue = """
				{
					findBookById(id:1) {
						id name isbn
						author {id name}
						reviewers {id name}
						quotes {id text}
					}
				}
				""";

		Map<String, Object> requestBody = Map.of("query", queryValue);
		RequestEntity<Map<String, Object>> request = RequestEntity.post(localUrl + "/graphql").body(requestBody);

		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request,
				new ParameterizedTypeReference<>() {
				});

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull().containsKey("data");
		assertThat(response.getBody().get("data")).isInstanceOf(Map.class);
		assertThat((Map) response.getBody().get("data")).containsKey("findBookById");
		assertThat((Map) ((Map) response.getBody().get("data")).get("findBookById")).isNotNull().isInstanceOf(Map.class)
				.isNotEmpty();

		Map<String, Object> result = (Map) ((Map) response.getBody().get("data")).get("findBookById");

		Book book = objectMapper.convertValue(result, Book.class);

		assertThat(book.getId()).isNotNull();
		assertThat(book.getName()).isNotNull();
		assertThat(book.getIsbn()).isNotNull();
		assertThat(book.getNonExistingColumnA()).isNull();
		assertThat(book.getNonExistingColumnB()).isNull();
		assertThat(book.getMissingOneToMany()).isNull();

		assertThat(book.getAuthor()).isNotNull();
		Person author = book.getAuthor();
		assertThat(author.getId()).isNotNull();
		assertThat(author.getName()).isNotNull();
		assertThat(author.getNonExistingColumnA()).isNull();
		assertThat(author.getNonExistingColumnB()).isNull();

		assertThat(book.getQuotes()).isNotNull().isNotEmpty();
		for (Quote quote : book.getQuotes()) {
			assertThat(quote.getId()).isNotNull();
			assertThat(quote.getText()).isNotNull();
			assertThat(quote.getNonExistingColumnA()).isNull();
			assertThat(quote.getNonExistingColumnB()).isNull();
		}

		assertThat(book.getReviewers()).isNotNull().isNotEmpty();
		for (Person reviewer : book.getReviewers()) {
			assertThat(reviewer.getId()).isNotNull();
			assertThat(reviewer.getName()).isNotNull();
			assertThat(reviewer.getNonExistingColumnA()).isNull();
			assertThat(reviewer.getNonExistingColumnB()).isNull();
		}
	}
}
