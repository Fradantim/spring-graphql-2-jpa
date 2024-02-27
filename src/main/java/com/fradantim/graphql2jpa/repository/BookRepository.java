package com.fradantim.graphql2jpa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fradantim.graphql2jpa.entity.Book;
import com.fradantim.graphql2jpa.utils.GraphQLEntityFetchTranslator;

import graphql.schema.DataFetchingFieldSelectionSet;
import jakarta.persistence.EntityManager;

public interface BookRepository extends JpaRepository<Book, Integer>, CustomizedBookRepository {
}

interface CustomizedBookRepository {
	Optional<Book> findById(Integer id, DataFetchingFieldSelectionSet dataSelectionSet);

	List<Book> findByIdIn(List<Integer> ids, DataFetchingFieldSelectionSet dataSelectionSet);
}

@Repository
class CustomizedBookRepositoryImpl implements CustomizedBookRepository {

	private final EntityManager entityManager;

	public CustomizedBookRepositoryImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<Book> findById(Integer id, DataFetchingFieldSelectionSet dataSelectionSet) {
		String fetch = GraphQLEntityFetchTranslator.buildFetch("b", dataSelectionSet);
		Book book = entityManager.createQuery("Select b from Book b " + fetch + " where b.id = :id", Book.class)
				.setParameter("id", id).getSingleResult();

		return Optional.ofNullable(book);
	}

	@Override
	public List<Book> findByIdIn(List<Integer> ids, DataFetchingFieldSelectionSet dataSelectionSet) {
		String fetch = GraphQLEntityFetchTranslator.buildFetch("b", dataSelectionSet);
		return entityManager.createQuery("Select b from Book b " + fetch + " where b.id in :ids", Book.class)
				.setParameter("ids", ids).getResultList();
	}
}