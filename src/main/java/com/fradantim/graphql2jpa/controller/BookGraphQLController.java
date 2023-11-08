package com.fradantim.graphql2jpa.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;

import com.fradantim.graphql2jpa.annotation.ReturnType;
import com.fradantim.graphql2jpa.dao.GraphQLDAO;
import com.fradantim.graphql2jpa.entity.Book;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

@Controller
public class BookGraphQLController extends DataFetcherExceptionResolverAdapter {
	@Autowired
	private GraphQLDAO graphQLDao;

	@QueryMapping
	@ReturnType(Book.class)
	public Object findBookById(DataFetchingEnvironment env, @Argument Integer id) {
		return graphQLDao.find(Book.class, id, env.getSelectionSet(), true).orElseThrow(() -> new BookNotFound(id));
	}

	@QueryMapping
	@ReturnType(Book.class)
	public List<Object> findBookByIds(DataFetchingEnvironment env, @Argument List<Integer> ids) {
		return graphQLDao.find(Book.class, ids, env.getSelectionSet(), isThreadLocalContextAware());
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
