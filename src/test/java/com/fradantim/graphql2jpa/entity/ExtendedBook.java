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
public class ExtendedBook {
	@Id
	private Integer id;
	private String name;
	private String isbn;
	private String nonExistingColumnA;
	private String nonExistingColumnB;

	@ManyToOne(fetch = FetchType.EAGER)
	private ExtendedPerson author;

	@JoinColumn(name = "bookId")
	@OneToMany(fetch = FetchType.EAGER)
	private Set<ExtendedQuote> quotes;

	@JoinTable(name = "book_reviewer", joinColumns = @JoinColumn(name = "person_id"), inverseJoinColumns = @JoinColumn(name = "book_id"))
	@ManyToMany(fetch = FetchType.EAGER)
	private Set<ExtendedPerson> reviewers;

	@JoinColumn(name = "nonExistingBookId")
	@OneToMany(fetch = FetchType.EAGER)
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

	public String getNonExistingColumnA() {
		return nonExistingColumnA;
	}

	public void setNonExistingColumnA(String nonExistingColumnA) {
		this.nonExistingColumnA = nonExistingColumnA;
	}

	public String getNonExistingColumnB() {
		return nonExistingColumnB;
	}

	public void setNonExistingColumnB(String nonExistingColumnB) {
		this.nonExistingColumnB = nonExistingColumnB;
	}

	public ExtendedPerson getAuthor() {
		return author;
	}

	public void setAuthor(ExtendedPerson author) {
		this.author = author;
	}

	public Set<ExtendedQuote> getQuotes() {
		return quotes;
	}

	public void setQuotes(Set<ExtendedQuote> quotes) {
		this.quotes = quotes;
	}

	public Set<ExtendedPerson> getReviewers() {
		return reviewers;
	}

	public void setReviewers(Set<ExtendedPerson> reviewers) {
		this.reviewers = reviewers;
	}

	public Set<NonExistingEntity> getMissingOneToMany() {
		return missingOneToMany;
	}

	public void setMissingOneToMany(Set<NonExistingEntity> missingOneToMany) {
		this.missingOneToMany = missingOneToMany;
	}
}