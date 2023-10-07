package com.fradantim.graphql2jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Table(name = "person")
@Entity
public class ExtendedPerson {
	@Id
	private Integer id;
	private String name;
	private String nonExistingColumnA;
	private String nonExistingColumnB;

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