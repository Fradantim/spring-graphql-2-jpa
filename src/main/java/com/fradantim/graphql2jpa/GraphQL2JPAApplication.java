package com.fradantim.graphql2jpa;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fradantim.graphql2jpa.entity.Book;
import com.fradantim.graphql2jpa.entity.Person;
import com.fradantim.graphql2jpa.entity.Quote;

@SpringBootApplication
public class GraphQL2JPAApplication implements CommandLineRunner {
	private static final Logger logger = LoggerFactory.getLogger(GraphQL2JPAApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(GraphQL2JPAApplication.class, args);
	}

	@Autowired
	private QuoteRepository quoteRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Override
	public void run(String... args) throws Exception {
		logger.info("Creating tables...");
		List.of("create table person (id int not null, name varchar(31), primary key (id))",
				"create table book (author_id bigint, id int not null, isbn varchar(31), name varchar(31), primary key (id))",
				"create table book_reviewer (author_id int not null, book_id int not null, primary key (author_id, book_id))",
				"create table quote (book_id bigint, id int not null, text varchar(127), primary key (id))")
				.forEach(jdbcTemplate::update);

		logger.info("Inserting data...");
		int idPerson = 1;
		Person person1 = authorRepository.save(new Person(idPerson++, "Stephen Kink"));
		Person person2 = authorRepository.save(new Person(idPerson++, "Not Fradantim, he does not read"));
		Person person3 = authorRepository.save(new Person(idPerson++, "Geprge Orwell"));
		Person person4 = authorRepository.save(new Person(idPerson++, "Also not Fradantim"));

		int idBook = 1;
		Book book1 = bookRepository.save(new Book(idBook++, "IT", "9783453435773", person1));
		Book book2 = bookRepository.save(new Book(idBook++, "The Shinning", "9783785746042", person1));
		Book book3 = bookRepository.save(new Book(idBook++, "Carrie", "9780307348074", person1));

		Book book4 = bookRepository.save(new Book(idBook++, "1984", "9789510459959", person3));
		Book book5 = bookRepository.save(new Book(idBook++, "Animal Farm", "9783257691955", person3));

		AtomicInteger idQuote = new AtomicInteger(1);
		List.of("Your hair is winter fire, January embers, My heart burns there, too.", "We all float down here!",
				"What can be done when you’re eleven can often never be done again.").forEach(t -> {
					quoteRepository.save(new Quote(idQuote.getAndIncrement(), book1.getId(), t));
				});

		List.of("The tears that heal are also the tears that scald and scourge.",
				"Are you sure self-pity is a luxury you can afford, Jack?",
				"Living by your wits is always knowing where the wasps are.").forEach(t -> {
					quoteRepository.save(new Quote(idQuote.getAndIncrement(), book2.getId(), t));
				});

		List.of("But sorry is the Kool-Aid of human emotions. [...] True sorrow is as rare as true love.",
				"True sorrow is as rare as true love.",
				"the late afternoon sunlight, warm as oil, sweet as childhood ...").forEach(t -> {
					quoteRepository.save(new Quote(idQuote.getAndIncrement(), book3.getId(), t));
				});

		List.of("Perhaps one did not want to be loved so much as to be understood.",
				"The best books... are those that tell you what you know already.",
				"If you want to keep a secret, you must also hide it from yourself.").forEach(t -> {
					quoteRepository.save(new Quote(idQuote.getAndIncrement(), book4.getId(), t));
				});

		List.of("All animals are equal, but some animals are more equal than others.", "Four legs good, two legs bad.",
				"All men are enemies. All devs are comrades").forEach(t -> {
					quoteRepository.save(new Quote(idQuote.getAndIncrement(), book5.getId(), t));
				});

		Book book1b = bookRepository.findById(1).get();
		book1b.setReviewers(new HashSet<Person>());
		book1b.getReviewers().add(person2);
		book1b.getReviewers().add(person4);

		Book book2b = bookRepository.findById(2).get();
		book2b.setReviewers(new HashSet<Person>());
		book2b.getReviewers().add(person4);

		Book book3b = bookRepository.findById(3).get();
		book3b.setReviewers(new HashSet<Person>());
		book3b.getReviewers().add(person2);
		book3b.getReviewers().add(person4);

		Book book5b = bookRepository.findById(5).get();
		book5b.setReviewers(new HashSet<Person>());
		book5b.getReviewers().add(person2);

		bookRepository.saveAll(List.of(book1b, book2b, book3b, book4, book5b));
		logger.info("Done.");
	}
}

interface BookRepository extends JpaRepository<Book, Integer> {
}

interface AuthorRepository extends JpaRepository<Person, Integer> {
}

interface QuoteRepository extends JpaRepository<Quote, Integer> {
}