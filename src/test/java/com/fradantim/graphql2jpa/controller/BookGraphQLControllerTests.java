package com.fradantim.graphql2jpa.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.fradantim.graphql2jpa.model.Cover;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BookGraphQLControllerTests {

	private static final Logger logger = LoggerFactory.getLogger(BookGraphQLControllerTests.class);

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
	
	@Test
	void queryBookByPojoTest() {
		String queryValue = """
				{
				  findBookByPojo(pojo:{id:1}) {
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

		Book book = getGraphQLQueryResult(response, "findBookByPojo", Book.class);

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
	void incrementConnections() {
		String queryPrefix = "{ findBookById(id: 1) { ";
		String querySuffix = " } }";

		List<List<String>> bookAttributes = combinations(List.of("id", "isbn", "name"));
		List<List<String>> personAttributes = combinations(List.of("id", "name"));
		List<List<String>> quoteAttributes = combinations(List.of("id", "text", "bookId"));

		List<List<String>> rootAttributes = combinations(List.of("self", "author", "reviewers", "quotes"));

		rootAttributes.forEach(
				ra -> bookAttributes.forEach(ba -> personAttributes.forEach(pa -> quoteAttributes.forEach(qa -> {
					if (ra.isEmpty() || (ba.isEmpty() && pa.isEmpty() && qa.isEmpty()))
						return;

					String body = "";
					if (ra.contains("self") && !ba.isEmpty()) {
						body += ba.stream().collect(Collectors.joining(" "));
					}
					if (ra.contains("author") && !pa.isEmpty()) {
						body += " author { " + pa.stream().collect(Collectors.joining(" ")) + " }";
					}
					if (ra.contains("reviewers") && !pa.isEmpty()) {
						body += " reviewers { " + pa.stream().collect(Collectors.joining(" ")) + " }";
					}
					if (ra.contains("quotes") && !qa.isEmpty()) {
						body += " quotes { " + qa.stream().collect(Collectors.joining(" ")) + " }";
					}

					if (!body.isBlank()) {
						String query = queryPrefix + body + querySuffix;
						logger.info("Query: {}", query);

						Map<String, Object> requestBody = Map.of("query", query);
						RequestEntity<Map<String, Object>> request = RequestEntity.post(localUrl + "/graphql")
								.body(requestBody);

						ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request,
								new ParameterizedTypeReference<>() {
								});
						assertThat(response.getBody()).doesNotContainKey("errors");
						assertThat(response.getBody()).containsKey("data").extracting("data").isInstanceOf(Map.class)
								.hasFieldOrProperty("findBookById").extracting("findBookById").isNotNull()
								.isInstanceOf(Map.class);
					}
				}))));
	}
	
	@Test
	void enumTest() {
		String queryValue = """
				{
				  echoCover(cover: HARD)
				}
				""";

		Map<String, Object> requestBody = Map.of("query", queryValue);
		RequestEntity<Map<String, Object>> request = RequestEntity.post(localUrl + "/graphql").body(requestBody);

		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(request,
				new ParameterizedTypeReference<>() {
				});

		Cover cover = getGraphQLQueryResult(response, "echoCover", Cover.class);
		assertThat(cover).isEqualTo(Cover.HARD);
	}

	private static <T> List<List<T>> combinations(List<T> iterable, int r) {
		List<T> pool = new ArrayList<>(iterable);
		int n = pool.size();
		if (r > n)
			return new ArrayList<>(); // empty list if r > n

		List<List<T>> result = new ArrayList<>();
		int[] indices = new int[r];
		for (int i = 0; i < r; i++)
			indices[i] = i;

		while (true) {
			List<T> combination = new ArrayList<>();
			for (int i : indices)
				combination.add(pool.get(i));
			result.add(combination);

			int i;
			for (i = r - 1; i >= 0; i--)
				if (indices[i] != i + n - r)
					break;

			if (i < 0)
				break;

			indices[i]++;
			for (int j = i + 1; j < r; j++)
				indices[j] = indices[j - 1] + 1;
		}

		return result;
	}

	/** From input [A,B,C] returns [[],[A],[B],[C],[A,B],[A,C],[B,C],[A,B,C]] */
	private static <T> List<List<T>> combinations(List<T> ogList) {
		List<List<T>> allCombinations = new ArrayList<>();

		IntStream.range(0, ogList.size() + 1).forEach(i -> {
			allCombinations.addAll(combinations(ogList, i));
		});

		return allCombinations;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private <T> T getGraphQLQueryResult(ResponseEntity<Map<String, Object>> response, String query, Class<T> type) {
		assertThat(response).isNotNull().extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull().containsKey("data").extracting(m -> m.get("data"))
				.isInstanceOf(Map.class);
		Map<String, Object> data = (Map) response.getBody().get("data");
		assertThat(data).containsKey(query).extracting(m -> m.get(query)).isNotNull();
		return objectMapper.convertValue(data.get(query), type);
	}
}
