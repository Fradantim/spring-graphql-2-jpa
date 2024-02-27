package com.fradantim.graphql2jpa.utils;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;

public class GraphQLEntityFetchTranslator {

	private static final Logger logger = LoggerFactory.getLogger(GraphQLEntityFetchTranslator.class);

	private GraphQLEntityFetchTranslator() {
	}

	public static String buildFetch(String parentAlias, DataFetchingFieldSelectionSet dataSelectionSet) {
		StringBuilder fetchContent = new StringBuilder();
		buildFetch(fetchContent, parentAlias, dataSelectionSet, new HashMap<>());
		logger.debug("jpql fetch: {}", fetchContent);
		return fetchContent.toString();
	}

	private static void buildFetch(StringBuilder fetch, String parentAlias,
			DataFetchingFieldSelectionSet dataSelectionSet, Map<String, Integer> aliasCounter) {
		dataSelectionSet.getImmediateFields().stream().filter(GraphQLEntityFetchTranslator::isComplexObject)
				.forEach(field -> {
					String fieldAlias = getNextAlias(field.getName().substring(0, 1).toLowerCase(), aliasCounter);
					fetch.append(" left join fetch " + parentAlias + "." + field.getName() + " " + fieldAlias);
					buildFetch(fetch, fieldAlias, field.getSelectionSet(), aliasCounter);
				});
	}

	private static boolean isComplexObject(SelectedField field) {
		return !field.getSelectionSet().getImmediateFields().isEmpty();
	}

	private static String getNextAlias(String fieldAlias, Map<String, Integer> aliasCounter) {
		return fieldAlias + aliasCounter.compute(fieldAlias, (k, v) -> v == null ? 0 : v + 1);
	}
}
