# spring-graphql-2-jpa

Simple proof of concept on using the attribute projection of graphql to reduce the entities fetched by jpa.

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
  int country_id
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

country {
  int id
  string name
}

book ||--o{ book_reviewer : "reviewers"
person ||--o{ book_reviewer : ""
person ||--o{ book : "author"
country ||--o{ person : ""
book ||--o{ quote : ""
```

### Data query

#### Selecting all fields
GraphQL Query:
```
{
  findBookById(id:1) {
    id name isbn
    author {
      id name
      country {id name}
      }
    quotes { id text }
    reviewers {
      id name
      country {id name}
    }
  }
}
```

Response:
``` json
{
  "data": {
    "findBookById": {
      "id": "1",
      "name": "IT",
      "isbn": "9783453435773",
      "author": {
        "id": "1",
        "name": "Stephen King",
        "country": { "id": "1", "name": "U.S." }
      },
      "quotes": [
        { "id": "2", "text": "We all float down here!" },
        { "id": "3", "text": "What can be done when you’re eleven can often never be done again." },
        { "id": "1", "text": "Your hair is winter fire, January embers, My heart burns there, too." }
      ],
      "reviewers": [
        {
          "id": "2",
          "name": "Not Fradantim, he does not read", 
          "country": { "id": "3", "name": "Argentina" }
        },
        {
          "id": "4",
          "name": "Also not Fradantim",
          "country": { "id": "3", "name": "Argentina" }
        }
      ]
    }
  }
}
```

Background query:
``` sql
select b1_0.id,b1_0.isbn,b1_0.name,
  a1_0.id,a1_0.name,
  c1_0.id,c1_0.name,
  c2_0.id,c2_0.name,
  q1_0.book_id,q1_0.id,q1_0.text,
  r1_0.book_id,r1_1.id,r1_1.name
from book b1_0 
  left join person a1_0 on a1_0.id=b1_0.author_id 
  left join country c1_0 on c1_0.id=a1_0.country_id 
  left join quote q1_0 on b1_0.id=q1_0.book_id 
  left join book_reviewer r1_0 on b1_0.id=r1_0.book_id 
  left join person r1_1 on r1_1.id=r1_0.person_id 
  left join country c2_0 on c2_0.id=r1_1.country_id 
where b1_0.id=?
```

#### Selecting some fields
GraphQL Query:
```
{
  findBookById(id:1) {
    name
    quotes { id }
    reviewers { name }
  }
}
```

Response:
``` json
{
  "data": {
    "findBookById": {
      "name": "IT",
      "quotes": [
        { "id": "2" },
        { "id": "3" },
        { "id": "1" }
      ],
      "reviewers": [
        { "name": "Also not Fradantim" },
        { "name": "Not Fradantim, he does not read" }
      ]
    }
  }
}
```

Background query:
``` sql
select b1_0.id,b1_0.author_id,b1_0.isbn,b1_0.name,
  q1_0.book_id,q1_0.id,q1_0.text,
  r1_0.book_id,r1_1.id,r1_1.country_id,r1_1.name
from book b1_0 
  left join quote q1_0 on b1_0.id=q1_0.book_id 
  left join book_reviewer r1_0 on b1_0.id=r1_0.book_id 
  left join person r1_1 on r1_1.id=r1_0.person_id 
where b1_0.id=?
```

As you can see the relationships `book -> author` or `reviewer -> country` where not included in the graphql query and thus where not included in the background query

## How?

The backend will use the graphql projection to indicate fetchs:
GraphQL Query:
```
{
  findBookById(id:1) {
    id name isbn
    author {
      id name
      country {id name}
      }
    quotes { id text }
    reviewers {
      id name
      country {id name}
    }
  }
}
```

JPQL:
``` sql
select b from Book b
  left join fetch b.author a0
  left join fetch a0.country c0
  left join fetch b.quotes q0
  left join fetch b.reviewers r0
  left join fetch r0.country c1
where b1.id = :id
```

## Previous attempt

There's [a previous attempt](/../../tree/best-projection-worst-conn-mgmt) which also manages to select fewer columns in the database query, but need to create new connections to the database at runtime.

-F