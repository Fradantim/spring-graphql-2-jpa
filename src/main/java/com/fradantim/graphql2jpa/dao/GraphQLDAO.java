package com.fradantim.graphql2jpa.dao;

import graphql.schema.DataFetchingFieldSelectionSet;

public interface GraphQLDAO {

	/**
	 * @param modelClass       class to use as a template
	 * @param dataSelectionSet fields to retrieve
	 * @param evict            if the entity should be removed from the persistence
	 *                         context
	 * @return An entity instance from a reduced class based on <b>modelClass</b>
	 */
	public Object find(Class<?> modelClass, Object primaryKey, DataFetchingFieldSelectionSet dataSelectionSet,
			boolean evict);
}
