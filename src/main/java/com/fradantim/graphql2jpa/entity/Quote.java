package com.fradantim.graphql2jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Table(name = "quote")
@Entity
public class Quote {
	@Id
	private Integer id;
	private Integer bookId;
	private String text;

	public Quote() {
	}

	public Quote(Integer id, Integer bookId, String text) {
		this.id = id;
		this.bookId = bookId;
		this.text = text;
	}

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
}