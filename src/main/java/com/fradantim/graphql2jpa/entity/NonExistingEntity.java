package com.fradantim.graphql2jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Table(name = "missing_table")
@Entity
public class NonExistingEntity {
	@Id
	private Integer id;
	private String nonExistingColumnA;
	private String nonExistingColumnB;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
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