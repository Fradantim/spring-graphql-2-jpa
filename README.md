# spring-graphql-2-jpa

Simple proof of concept on using the attribute projection of graphql to reduce the columns selected by jpa.

## An Example

### The model
``` mermaid
erDiagram
book {
  int id
  string name
  string isbn
  int author_id
}

person {
  int id
  string name
}

quote {
  int id
  int book_id
  string text
}

book_reviewer {
  book_id
  person_id
}

book ||--o{ book_reviewer : "reviewers"
person ||--o{ book_reviewer : "reviews"
person ||--o{ book : "author"
book ||--o{ quote : ""
```

### Data query

#### Selecting all fields
Query:
```
{
  findBookById(id:1) {
    id name isbn
    author { id name }
    quotes { id text }
    reviewers { id name }
  }
}
```

JPQL:
``` sql
select
	c1_0.id,c1_0.isbn,c1_0.name,
	a1_0.id,a1_0.name,
	q1_0.book_id,q1_0.id,q1_0.text,
	r1_0.author_id,r1_1.id,r1_1.name
	from book c1_0
	left join person a1_0 on a1_0.id=c1_0.author_id
	left join quote q1_0 on c1_0.id=q1_0.book_id 
	left join (book_reviewer r1_0 join person r1_1 on r1_1.id=r1_0.book_id) on c1_0.id=r1_0.author_id 
	where c1_0.id=?
```

Response:
``` json
{
  "data": {
    "findBookById": {
      "id": "1",
      "name": "IT",
      "isbn": "9783453435773",
      "author": { "id": "1", "name": "Stephen King" },
      "quotes": [
        { "id": "3", "text": "What can be done when you’re eleven can often never be done again." },
        { "id": "1", "text": "Your hair is winter fire, January embers, My heart burns there, too." },
        { "id": "2", "text": "We all float down here!" }
      ],
      "reviewers": [
        { "id": "2", "name": "Not Fradantim, he does not read" },
        { "id": "4", "name": "Also not Fradantim" }
      ]
    }
  }
}
```

#### Selecting some fields
Query:
```
{
  findBookById(id:1) {
    name
    quotes { id }
    reviewers { name }
  }
}
```

JPQL:
``` sql
select 
	c1_0.id,c1_0.name,
	q1_0.book_id,q1_0.id,
	r1_0.author_id,r1_1.id,r1_1.name
	from book c1_0 
	left join quote q1_0 on c1_0.id=q1_0.book_id 
	left join (book_reviewer r1_0 join person r1_1 on r1_1.id=r1_0.book_id) on c1_0.id=r1_0.author_id
	where c1_0.id=?
```

Response:
``` json
{
  "data": {
    "findBookById": {
      "name": "IT",
      "quotes": [ { "id": "1" }, { "id": "3" }, { "id": "2" } ],
      "reviewers": [ { "name": "Not Fradantim, he does not read" }, { "name": "Also not Fradantim" } ]
    }
  }
}
```

Hey, who asked for Book.id and Reviewer.id? There's the catch!

## How?

The backend will create a brand new class based on the selected fields, for example, if we have the class:
``` java
@Entity @Table("MyTable") public class MyEntity {
	@Id private Long id;
	private String firstName;
	private String lastName;
	// constructors, getters ands setters
}
```
and we only ask for lastName a new class will be created:
``` java
@Entity @Table("MyTable") public class MyEntity$copy0 {
	@Id public Long id;
	public String lastName;
	// nothing else
}
```

Why is the `@Id` annotated field kept? -> Because it's a mandatory field for an entity.

This class reduction is also done on transitive classes (`@OneToOne` / `@OneToMany` / `@ManyToOne` / `@ManyToMany`), if they are selected on the request.

This way the `EntityManager` can reduce the selected columns when performing a query.

## The bad thing

Each time a new class is created some computation time is lost performing reflection on the original class, and previous `EntityManager` and `EntityManagerFactory` need to be rebuilt, which can mean **creating a new jdbc connection**. Some measures are taken, like caching the classes, or reduce how often the `EntityManager` and `EntityManagerFactory` are rebuilt. Still this solution is not production ready, and may not work for every use case, but hey, that's a poc for you.

-F