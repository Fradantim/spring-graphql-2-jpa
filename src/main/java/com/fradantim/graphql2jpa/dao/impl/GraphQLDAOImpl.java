package com.fradantim.graphql2jpa.dao.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.hibernate.jpa.HibernatePersistenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private final AtomicLong counter = new AtomicLong();
	private final Map<String, Class<?>> cache = new ConcurrentHashMap<>();
	private final MutablePersistenceUnitInfo persistenceUnitInfo;
	private EntityManagerFactory emf = null;
	private EntityManager entityManager = null;

	public GraphQLDAOImpl(EntityManagerFactory otherEMF) {
		persistenceUnitInfo = buildPersistenceUnitInfo(otherEMF.getProperties());
	}

	private MutablePersistenceUnitInfo buildPersistenceUnitInfo(Map<String, Object> otherEMFProperties) {
		MutablePersistenceUnitInfo persistenceUnitInfo = new MutablePersistenceUnitInfo() { // @ @formatter:off
			@Override public ClassLoader getNewTempClassLoader() {return null;}
			@Override public void addTransformer(ClassTransformer classTransformer) { /* no-op */ }
		}; // @formatter:on

		otherEMFProperties.entrySet().stream().filter(e -> e.getKey().startsWith("hibernate."))
				.filter(e -> !"hibernate.transaction.coordinator_class".equals(e.getKey())).forEach(e -> {
					persistenceUnitInfo.getProperties().put(e.getKey(), e.getValue());
				});

		persistenceUnitInfo.setPersistenceUnitName("DynamicPersistencUnitInfo");
		return persistenceUnitInfo;
	}

	@Override
	public Object find(Class<?> modelClass, Object primaryKey, DataFetchingFieldSelectionSet dataSelectionSet,
			boolean evict) {
		Class<?> minClass = getMinimalClass(modelClass, dataSelectionSet, true);
		EntityManager em = getEntityManager();
		Object res = em.find(minClass, primaryKey);
		if (evict)
			em.detach(res);
		return res;
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
			Boolean firstEntry) {
		String identifier = buildSelectionIdentifier(modelClass, dataSelectionSet);

		Class<?> result = cache.get(identifier);
		if (result != null)
			return result;

		String fullIdentifier = buildDeepSelectionIdentifier(modelClass, dataSelectionSet);
		if (!fullIdentifier.equals(identifier)) {
			result = cache.get(fullIdentifier);
			if (result != null) {
				// new alias for same class
				cache.put(identifier, result);
				return result;
			}
		}

		synchronized (fullIdentifier) {
			result = cache.get(fullIdentifier);
			if (result != null)
				return result;

			logger.info("Creating minimal class for {}", fullIdentifier);
			result = buildMinimalClass(modelClass, dataSelectionSet);
			logger.info("Created a minimal class as {}", result.getName());

			cache.put(fullIdentifier, result);
			if (!fullIdentifier.equals(identifier))
				cache.put(identifier, result);
			synchronized (this) {
				persistenceUnitInfo.addManagedClassName(result.getName());
			}
			if (firstEntry)
				refreshEntityManager();
		}
		return result;
	}

	private Class<?> buildMinimalClass(Class<?> modelClass, DataFetchingFieldSelectionSet dataSelectionSet) {
		String className = modelClass.getName() + "$copy" + counter.getAndIncrement();
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
				if (type instanceof ParameterizedType ptype) {
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
			throw new RuntimeException(e);
		}
	}

	private static Class<?> getRealFieldType(Field field) {
		if (field.getGenericType() instanceof ParameterizedType ptype) {
			try {
				return Class.forName(ptype.getActualTypeArguments()[0].getTypeName());
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		return field.getType();
	}

	private synchronized void refreshEntityManager() {
		EntityManagerFactory oldemf = emf;
		if (oldemf != null) {
			new Thread(() -> {
				// kill previous emf after some time so it can complete current queries
				try {
					Thread.sleep(4000);
				} catch (InterruptedException e) {
					e.printStackTrace(); // no-op
				}
				logger.info("Closing previous EntityManagerFactory");
				emf.close();
			}).start();
		}

		logger.info("Refreshing EntityManager");
		emf = new HibernatePersistenceProvider().createContainerEntityManagerFactory(persistenceUnitInfo,
				Collections.emptyMap());
		entityManager = emf.createEntityManager();
	}

	private EntityManager getEntityManager() {
		if (entityManager != null && entityManager.isOpen())
			return entityManager;

		synchronized (this) {
			if (entityManager != null && entityManager.isOpen())
				return entityManager;
			refreshEntityManager();
		}

		return entityManager;
	}
}
