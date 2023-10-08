package com.fradantim.graphql2jpa;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class GraphQL2JPAApplication implements CommandLineRunner {
	private static final Logger logger = LoggerFactory.getLogger(GraphQL2JPAApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(GraphQL2JPAApplication.class, args);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Override
	public void run(String... args) throws Exception {
		logger.info("Filling database...");
		List.of("create table person (id int not null, name varchar(31), primary key (id))",
				"create table book (author_id bigint, id int not null, isbn varchar(31), name varchar(31), primary key (id))",
				"create table book_reviewer (person_id int not null, book_id int not null, primary key (person_id, book_id))",
				"create table quote (book_id bigint, id int not null, text varchar(127), primary key (id))",
				"insert into person (id, name) values (1, 'Stephen King')",
				"insert into person (id, name) values (2, 'Not Fradantim, he does not read')",
				"insert into person (id, name) values (3, 'George Orwell')",
				"insert into person (id, name) values (4, 'Also not Fradantim')",
				"insert into book (id, name, isbn, author_id) values (1, 'IT', '9783453435773', 1)",
				"insert into book (id, name, isbn, author_id) values (2, 'The Shinning', '9783785746042', 1)",
				"insert into book (id, name, isbn, author_id) values (3, 'Carrie', '9780307348074', 1)",
				"insert into book (id, name, isbn, author_id) values (4, '1984', '9789510459959', 3)",
				"insert into book (id, name, isbn, author_id) values (5, 'Animal Farm', '9783257691955', 3)",
				"insert into quote (id, book_id, text) values (1, 1, 'Your hair is winter fire, January embers, My heart burns there, too.')",
				"insert into quote (id, book_id, text) values (2, 1, 'We all float down here!')",
				"insert into quote (id, book_id, text) values (3, 1, 'What can be done when you’re eleven can often never be done again.')",
				"insert into quote (id, book_id, text) values (4, 2, 'The tears that heal are also the tears that scald and scourge.')",
				"insert into quote (id, book_id, text) values (5, 2, 'Are you sure self-pity is a luxury you can afford, Jack?')",
				"insert into quote (id, book_id, text) values (6, 2, 'Living by your wits is always knowing where the wasps are.')",
				"insert into quote (id, book_id, text) values (7, 3, 'But sorry is the Kool-Aid of human emotions. [...] True sorrow is as rare as true love.')",
				"insert into quote (id, book_id, text) values (8, 3, 'True sorrow is as rare as true love.')",
				"insert into quote (id, book_id, text) values (9, 3, 'the late afternoon sunlight, warm as oil, sweet as childhood ...')",
				"insert into quote (id, book_id, text) values (10, 4, 'Perhaps one did not want to be loved so much as to be understood.')",
				"insert into quote (id, book_id, text) values (11, 4, 'The best books... are those that tell you what you know already.')",
				"insert into quote (id, book_id, text) values (12, 4, 'If you want to keep a secret, you must also hide it from yourself.')",
				"insert into quote (id, book_id, text) values (13, 5, 'All animals are equal, but some animals are more equal than others.')",
				"insert into quote (id, book_id, text) values (14, 5, 'Four legs good, two legs bad.')",
				"insert into quote (id, book_id, text) values (15, 5, 'All men are enemies. All devs are comrades.')",
				"insert into book_reviewer (book_id, person_id) values (1,2)",
				"insert into book_reviewer (book_id, person_id) values (1,4)",
				"insert into book_reviewer (book_id, person_id) values (2,4)",
				"insert into book_reviewer (book_id, person_id) values (3,2)",
				"insert into book_reviewer (book_id, person_id) values (3,4)",
				"insert into book_reviewer (book_id, person_id) values (5,2)").forEach(jdbcTemplate::update);
		logger.info("Done.");
	}
}