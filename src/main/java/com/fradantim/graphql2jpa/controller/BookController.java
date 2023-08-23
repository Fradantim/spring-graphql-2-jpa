package com.fradantim.graphql2jpa.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

import com.fradantim.graphql2jpa.dao.GraphQLDAO;
import com.fradantim.graphql2jpa.entity.Book;

import graphql.schema.DataFetchingEnvironment;

@Controller
public class BookController {
	@Autowired
	private GraphQLDAO graphQLDao;

	@QueryMapping
	public Object findBookById(DataFetchingEnvironment env, @Argument @PathVariable Long id) {
		return graphQLDao.find(Book.class, id, env.getSelectionSet(), true);
	}
}
