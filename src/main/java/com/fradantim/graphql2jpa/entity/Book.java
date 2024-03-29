package com.fradantim.graphql2jpa.entity;

import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Table(name = "Book")
@Entity
public class Book {
	@Id
	private Integer id;
	private String name;
	private String isbn;

	@ManyToOne(fetch = FetchType.LAZY)
	private Person author;

	@JoinColumn(name = "bookId")
	@OneToMany(fetch = FetchType.LAZY)
	private Set<Quote> quotes;

	@JoinTable(name = "book_reviewer", joinColumns = @JoinColumn(name = "book_id"), inverseJoinColumns = @JoinColumn(name = "person_id"))
	@ManyToMany(fetch = FetchType.LAZY)
	private Set<Person> reviewers;

	@JoinColumn(name = "nonExistingBookId")
	@OneToMany(fetch = FetchType.LAZY)
	private Set<NonExistingEntity> missingOneToMany;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIsbn() {
		return isbn;
	}

	public void setIsbn(String isbn) {
		this.isbn = isbn;
	}

	public Person getAuthor() {
		return author;
	}

	public void setAuthor(Person author) {
		this.author = author;
	}

	public Set<Quote> getQuotes() {
		return quotes;
	}

	public void setQuotes(Set<Quote> quotes) {
		this.quotes = quotes;
	}

	public Set<Person> getReviewers() {
		return reviewers;
	}

	public void setReviewers(Set<Person> reviewers) {
		this.reviewers = reviewers;
	}

	public Set<NonExistingEntity> getMissingOneToMany() {
		return missingOneToMany;
	}

	public void setMissingOneToMany(Set<NonExistingEntity> missingOneToMany) {
		this.missingOneToMany = missingOneToMany;
	}
}