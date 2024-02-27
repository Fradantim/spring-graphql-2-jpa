package com.fradantim.graphql2jpa.controller;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;

import com.fradantim.graphql2jpa.entity.Book;
import com.fradantim.graphql2jpa.model.Cover;
import com.fradantim.graphql2jpa.repository.BookRepository;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

@Controller
public class BookGraphQLController extends DataFetcherExceptionResolverAdapter {

	private final BookRepository bookRepository;

	public BookGraphQLController(BookRepository bookRepository) {
		this.bookRepository = bookRepository;
	}

	@QueryMapping
	public Book findBookById(DataFetchingEnvironment env, @Argument Integer id) {
		return bookRepository.findById(id, env.getSelectionSet()).orElseThrow(() -> new BookNotFound(id));
	}

	@QueryMapping
	public List<Book> findBookByIds(DataFetchingEnvironment env, @Argument List<Integer> ids) {
		return bookRepository.findByIdIn(ids, env.getSelectionSet());
	}

	@QueryMapping
	public Book findBookByPojo(DataFetchingEnvironment env, @Argument Book pojo) {
		return findBookById(env, pojo.getId());
	}

	@QueryMapping
	public Cover echoCover(@Argument Cover cover) {
		return cover;
	}

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		if (ex instanceof BookNotFound bnf) {
			return GraphqlErrorBuilder.newError().errorType(ErrorType.NOT_FOUND)
					.message("No book found for id " + bnf.id).path(env.getExecutionStepInfo().getPath())
					.location(env.getField().getSourceLocation()).build();
		}
		return GraphqlErrorBuilder.newError().errorType(ErrorType.INTERNAL_ERROR).message(ex.getMessage())
				.path(env.getExecutionStepInfo().getPath()).location(env.getField().getSourceLocation()).build();
	}
}

class BookNotFound extends RuntimeException {

	private static final long serialVersionUID = -5222064011257511675L;

	public final Integer id;

	public BookNotFound(Integer id) {
		this.id = id;
	}
}
