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

	@ManyToOne(fetch = FetchType.EAGER)
	private Person author;

	@JoinColumn(name = "bookId")
	@OneToMany(fetch = FetchType.EAGER)
	private Set<Quote> quotes;

	@JoinTable(name = "book_reviewer", joinColumns = @JoinColumn(name = "author_id"), inverseJoinColumns = @JoinColumn(name = "book_id"))
	@ManyToMany(fetch = FetchType.EAGER)
	private Set<Person> reviewers;

	public Book() {
	}

	public Book(Integer id, String name, String isbn, Person author) {
		super();
		this.id = id;
		this.name = name;
		this.isbn = isbn;
		this.author = author;
	}

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
}