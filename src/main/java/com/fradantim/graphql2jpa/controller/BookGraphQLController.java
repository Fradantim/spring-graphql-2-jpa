package com.fradantim.graphql2jpa.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.fradantim.graphql2jpa.annotation.ReturnType;
import com.fradantim.graphql2jpa.dao.GraphQLDAO;
import com.fradantim.graphql2jpa.entity.Book;

import graphql.schema.DataFetchingEnvironment;

@Controller
public class BookGraphQLController {
	@Autowired
	private GraphQLDAO graphQLDao;

	@QueryMapping
	@ReturnType(Book.class)
	public Object findBookById(DataFetchingEnvironment env, @Argument Long id) {
		return graphQLDao.find(Book.class, id, env.getSelectionSet(), true);
	}
}
