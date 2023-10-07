package com.fradantim.graphql2jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Table(name = "quote")
@Entity
public class ExtendedQuote {
	@Id
	private Integer id;
	private Integer bookId;
	private String text;
	private String nonExistingColumnA;
	private String nonExistingColumnB;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getBookId() {
		return bookId;
	}

	public void setBookId(Integer bookId) {
		this.bookId = bookId;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
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
}