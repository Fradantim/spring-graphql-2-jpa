package com.fradantim.graphql2jpa.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.fradantim.graphql2jpa.dao.GraphQLDAO;
import com.fradantim.graphql2jpa.entity.ExtendedBook;

import graphql.schema.DataFetchingEnvironment;

@Controller
public class ExtendedBookGraphQLController {
	@Autowired
	private GraphQLDAO graphQLDao;

	@QueryMapping
	public Object findExtendedBookById(DataFetchingEnvironment env, @Argument Long id) {
		return graphQLDao.find(ExtendedBook.class, id, env.getSelectionSet(), true);
	}
}
