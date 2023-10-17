package com.fradantim.graphql2jpa.dao.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.hibernate.jpa.HibernatePersistenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.stereotype.Service;

import com.fradantim.graphql2jpa.dao.GraphQLDAO;

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.spi.ClassTransformer;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

@Service
public class GraphQLDAOImpl implements GraphQLDAO, AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(GraphQLDAOImpl.class);
	private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private static final AtomicLong entityCounter = new AtomicLong(); // this is a nice metric to expose
	private static final AtomicLong connectionCounter = new AtomicLong(); // this is a nice metric to expose

	private final Map<String, Class<?>> entityDefinitionCache = new ConcurrentHashMap<>();
	private final MutablePersistenceUnitInfo persistenceUnitInfo;
	private EntityManagerFactory emf = null;
	private EntityManager entityManager = null;

	@Value("${graphql-dao.connection.close-delay:PT4S}")
	private Duration connectionCloseDelay;

	public GraphQLDAOImpl(EntityManagerFactory otherEMF) {
		persistenceUnitInfo = new MutablePersistenceUnitInfo() { // @ @formatter:off
			@Override public ClassLoader getNewTempClassLoader() {return null;}
			@Override public void addTransformer(ClassTransformer classTransformer) { /* no-op */ }
		}; // @formatter:on

		otherEMF.getProperties().entrySet().stream().filter(e -> e.getKey().startsWith("hibernate."))
				.filter(e -> !"hibernate.transaction.coordinator_class".equals(e.getKey()))
				.forEach(e -> persistenceUnitInfo.getProperties().put(e.getKey(), e.getValue()));

		persistenceUnitInfo.setPersistenceUnitName("DynamicPersistencUnitInfo");
	}

	@Override
	public Optional<Object> find(Class<?> modelClass, Object primaryKey, DataFetchingFieldSelectionSet dataSelectionSet,
			boolean evict) {
		Class<?> minClass = getMinimalClass(modelClass, dataSelectionSet, true);
		EntityManager em = getEntityManager();
		Object res = em.find(minClass, primaryKey);
		if (evict && res != null)
			em.detach(res);
		return Optional.ofNullable(res);
	}

	@Override
	public void close() throws Exception {
		if (emf != null)
			emf.close();
	}

	private static String buildSelectionIdentifier(Class<?> modelClass,
			DataFetchingFieldSelectionSet dataSelectionSet) {
		return modelClass.getName() + buildSelectionIdentifier(dataSelectionSet);
	}

	private static String buildSelectionIdentifier(DataFetchingFieldSelectionSet dataSelectionSet) {
		String fields = dataSelectionSet.getImmediateFields().stream().map(f -> {
			if (f.getSelectionSet().getFields().isEmpty())
				return f.getResultKey();

			return f.getResultKey() + buildSelectionIdentifier(f.getSelectionSet());
		}).sorted().collect(Collectors.joining(","));

		return "[" + fields + "]";
	}

	private static boolean isMandatory(Field field) {
		return field.getAnnotation(Id.class) != null;
	}

	private static String buildDeepSelectionIdentifier(Class<?> modelClass,
			DataFetchingFieldSelectionSet dataSelectionSet) {
		return modelClass.getName() + buildDeepSelectionIdentifierRec(modelClass, dataSelectionSet);
	}

	/** (recursive method) includes mandatory but not-selected fields */
	private static String buildDeepSelectionIdentifierRec(Class<?> modelClass,
			DataFetchingFieldSelectionSet dataSelectionSet) {
		List<String> fields = new ArrayList<>();

		for (Field field : modelClass.getDeclaredFields()) {
			String fieldName = field.getName();
			Optional<SelectedField> selectedField = dataSelectionSet.getImmediateFields().stream()
					.filter(dsf -> dsf.getName().equals(fieldName)).findFirst();
			if (selectedField.isPresent()) {
				// selected field
				if (selectedField.get().getSelectionSet().getFields().isEmpty())
					fields.add(fieldName);
				else
					fields.add(fieldName + buildDeepSelectionIdentifierRec(getRealFieldType(field),
							selectedField.get().getSelectionSet()));
			} else if (isMandatory(field)) {
				// mandatory but not-selected fields
				fields.add(field.getName());
			}
		}

		Collections.sort(fields);
		return "[" + fields.stream().collect(Collectors.joining(",")) + "]";
	}

	private Class<?> getMinimalClass(Class<?> modelClass, DataFetchingFieldSelectionSet dataSelectionSet,
			boolean firstEntry) {
		OffsetDateTime start = OffsetDateTime.now();
		String identifier = buildSelectionIdentifier(modelClass, dataSelectionSet);

		Class<?> entityDefinition = entityDefinitionCache.get(identifier);
		if (entityDefinition != null)
			return entityDefinition;

		String fullIdentifier = buildDeepSelectionIdentifier(modelClass, dataSelectionSet);
		if (!fullIdentifier.equals(identifier)) {
			entityDefinition = entityDefinitionCache.get(fullIdentifier);
			if (entityDefinition != null) {
				// new alias for same class
				entityDefinitionCache.put(identifier, entityDefinition);
				return entityDefinition;
			}
		}

		synchronized (fullIdentifier) {
			entityDefinition = entityDefinitionCache.get(fullIdentifier);
			if (entityDefinition != null) {
				if (logger.isDebugEnabled())
					logger.debug("Entity retrieved in {}", Duration.between(start, OffsetDateTime.now()));
				return entityDefinition;
			}

			logger.info("Creating minimal class for {}", fullIdentifier);
			entityDefinition = buildMinimalClass(modelClass, dataSelectionSet);
			logger.info("Created a minimal class as {}", entityDefinition.getName());

			entityDefinitionCache.put(fullIdentifier, entityDefinition);
			if (!fullIdentifier.equals(identifier))
				entityDefinitionCache.put(identifier, entityDefinition);
			synchronized (this) {
				persistenceUnitInfo.addManagedClassName(entityDefinition.getName());
			}
			if (firstEntry)
				refreshEntityManager(start);
		}
		return entityDefinition;
	}

	private Class<?> buildMinimalClass(Class<?> modelClass, DataFetchingFieldSelectionSet dataSelectionSet) {
		String className = modelClass.getName() + "$copy" + entityCounter.getAndIncrement();
		DynamicType.Builder<?> builder = new ByteBuddy().subclass(Object.class).name(className)
				.annotateType(modelClass.getDeclaredAnnotations());

		for (Field field : modelClass.getDeclaredFields()) {
			Optional<SelectedField> selectedField = dataSelectionSet.getImmediateFields().stream()
					.filter(dsf -> dsf.getName().equals(field.getName())).findFirst();
			if (selectedField.isPresent()) {
				// selected field
				builder = appendField(builder, field, selectedField);
			} else if (isMandatory(field)) {
				// mandatory but not-selected fields
				builder = appendField(builder, field, Optional.empty());
			}
		}

		return builder.make().load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION).getLoaded();
	}

	private DynamicType.Builder<?> appendField(DynamicType.Builder<?> builder, Field field,
			Optional<SelectedField> selectableField) {
		try {
			if (selectableField.isEmpty() || selectableField.get().getSelectionSet().getImmediateFields().isEmpty()) {
				// simple field
				return builder.defineField(field.getName(), field.getType(), Modifier.PUBLIC)
						.annotateField(field.getAnnotations());
			} else {
				// entity
				Type type = field.getGenericType();
				if (type instanceof ParameterizedType) {
					// generic
					Class<?> entityModelClass = getRealFieldType(field);
					Class<?> entityMinClass = getMinimalClass(entityModelClass, selectableField.get().getSelectionSet(),
							false);
					Generic generic = TypeDescription.Generic.Builder.parameterizedType(field.getType(), entityMinClass)
							.build();
					return builder.defineField(field.getName(), generic, Modifier.PUBLIC)
							.annotateField(field.getAnnotations());
				} else {
					// simple
					Class<?> entityMinClass = getMinimalClass(field.getType(), selectableField.get().getSelectionSet(),
							false);
					return builder.defineField(field.getName(), entityMinClass, Modifier.PUBLIC)
							.annotateField(field.getAnnotations());
				}
			}
		} catch (SecurityException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private static Class<?> getRealFieldType(Field field) {
		if (field.getGenericType() instanceof ParameterizedType ptype) {
			try {
				return Class.forName(ptype.getActualTypeArguments()[0].getTypeName());
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException(e);
			}
		}

		return field.getType();
	}

	private synchronized void refreshEntityManager(OffsetDateTime start) {
		EntityManagerFactory oldemf = emf;
		if (oldemf != null) {
			Long previousConnection = connectionCounter.get();
			Runnable closeConnnectionRunnable = () -> {
				oldemf.close();
				logger.info("EntityManager and Factory #{} closed", previousConnection);
			};
			executorService.schedule(closeConnnectionRunnable, connectionCloseDelay.toMillis(), TimeUnit.MILLISECONDS);
		}

		logger.debug("Refreshing EntityManager");
		emf = new HibernatePersistenceProvider().createContainerEntityManagerFactory(persistenceUnitInfo,
				Collections.emptyMap());
		entityManager = emf.createEntityManager();
		// this duration may include entity creation time
		logger.info("EntityManager and Factory #{} opened in {}", connectionCounter.incrementAndGet(),
				Duration.between(start, OffsetDateTime.now()));
	}

	private EntityManager getEntityManager() {
		if (entityManager != null && entityManager.isOpen())
			return entityManager;

		OffsetDateTime start = OffsetDateTime.now();
		synchronized (this) {
			if (entityManager != null && entityManager.isOpen()) {
				if (logger.isDebugEnabled())
					logger.debug("EntityManager retrieved in {}", Duration.between(start, OffsetDateTime.now()));
				return entityManager;
			}

			refreshEntityManager(start);
		}

		return entityManager;
	}
}
