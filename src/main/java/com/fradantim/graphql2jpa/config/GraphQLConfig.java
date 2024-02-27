package com.fradantim.graphql2jpa.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.stereotype.Component;

import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.SchemaPrinter;
import jakarta.annotation.Nullable;
import jakarta.persistence.Id;

@Configuration
public class GraphQLConfig {

	private static final Logger logger = LoggerFactory.getLogger(GraphQLConfig.class);

	@Bean
	public GraphQlSourceBuilderCustomizer buildCustomizer(Resource graphQLSchema) {
		return (GraphQlSource.SchemaResourceBuilder builder) -> builder.schemaResources(graphQLSchema);
	}

	@Bean
	public Resource graphQLSchema() {
		String printedSchema = new SchemaPrinter().print(generateSchema());
		logger.debug("Auto-generated graphql schema:\n{}", printedSchema);
		return new ByteArrayResource(printedSchema.getBytes());
	}

	public GraphQLSchema generateSchema() {
		ComplexTypes complexTypes = new ComplexTypes();
		List<GraphQLFieldDefinition> queryFields = new ArrayList<>();

		String pkgName = GraphQLConfig.class.getPackageName().substring(0,
				GraphQLConfig.class.getPackageName().lastIndexOf("."));

		getPackageClasses(pkgName).filter(this::classIsComponentAnotated)
				.flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods()))
				.filter(m -> m.isAnnotationPresent(QueryMapping.class)).forEach(method -> {
					logger.debug("Including {} : {}", method.getDeclaringClass().getName(), method.getName());
					String queryName = method.getAnnotation(QueryMapping.class).name();
					if (queryName.isBlank())
						queryName = method.getName();

					GraphQLOutputType type;
					Class<?> returnType = method.getReturnType();
					if (Collection.class.isAssignableFrom(returnType)) {
						returnType = (Class<?>) ((ParameterizedType) method.getGenericReturnType())
								.getActualTypeArguments()[0];
						type = GraphQLList.list(getOrBuildOutputType(complexTypes, returnType, null));
					} else {
						type = getOrBuildOutputType(complexTypes, returnType, null);
					}

					List<GraphQLArgument> arguments = getArguments(complexTypes, method);

					GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition().name(queryName)
							.type(type);
					arguments.forEach(builder::argument);

					queryFields.add(builder.build());
				});

		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name("Query");
		queryFields.forEach(builder::field);
		return GraphQLSchema.newSchema().query(builder).build();
	}

	private List<GraphQLArgument> getArguments(ComplexTypes complexTypes, Method method) {
		return Arrays.stream(method.getParameters()).filter(p -> p.getAnnotation(Argument.class) != null).map(p -> {
			String name = p.getAnnotation(Argument.class).name();
			if (name.isBlank())
				name = p.getName();

			GraphQLInputType inputType;
			if (Collection.class.isAssignableFrom(p.getType())) {
				Class<?> nonGenericType = (Class<?>) ((ParameterizedType) p.getParameterizedType())
						.getActualTypeArguments()[0];

				inputType = GraphQLList.list(getOrBuildInputType(complexTypes, nonGenericType, Attribute.of(p)));
			} else {
				inputType = getOrBuildInputType(complexTypes, p.getType(), Attribute.of(p));
			}

			return GraphQLArgument.newArgument().name(name).type(inputType).build();
		}).toList();
	}

	private Map<Class<?>, GraphQLScalarType> typetranslation = Map
			.of( // @formatter:off
				Long.class, Scalars.GraphQLInt,
				Integer.class, Scalars.GraphQLInt,
				Double.class, Scalars.GraphQLFloat,
				Float.class, Scalars.GraphQLFloat,
				Boolean.class, Scalars.GraphQLBoolean,
				String.class, Scalars.GraphQLString,
				Temporal.class, Scalars.GraphQLString
			);// @formatter:on

	private Optional<GraphQLScalarType> getBestScalar(Class<?> type) {
		return typetranslation.entrySet().stream().map(e -> e.getKey().isAssignableFrom(type) ? e.getValue() : null)
				.filter(Objects::nonNull).findFirst();
	}

	private Optional<GraphQLScalarType> getBestScalar(Class<?> type, @Nullable Attribute attribute) {
		return getBestScalar(type).map(scalar -> {
			if (attribute != null && ("id".equals(attribute.getName()) || "ids".equals(attribute.getName())
					|| attribute.getName().endsWith("Id") || attribute.getName().endsWith("Ids")
					|| attribute.getAnnotation(Id.class) != null))
				return Scalars.GraphQLID;
			return scalar;
		});
	}

	@SuppressWarnings("rawtypes")
	private GraphQLEnumType getOrBuildEnumType(ComplexTypes complexTypes, Class<?> type) {
		return complexTypes.getEnumType(type).orElseGet(() -> {
			String name = complexTypes.nextAvailableName(type.getSimpleName());
			GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum().name(name);
			for (Object obj : type.getEnumConstants())
				builder.value(((Enum) obj).name());

			GraphQLEnumType enumType = builder.build();
			complexTypes.addEnumType(type, enumType);

			return enumType;
		});
	}

	private GraphQLOutputType getOrBuildOutputType(ComplexTypes complexTypes, Class<?> type,
			@Nullable Attribute attribute) {
		Optional<GraphQLOutputType> complexTypeOpt = complexTypes.getOutputType(type);
		if (complexTypeOpt.isPresent())
			return complexTypeOpt.get();

		if (type.isEnum())
			return getOrBuildEnumType(complexTypes, type);

		Optional<GraphQLScalarType> scalar = getBestScalar(type, attribute);
		if (scalar.isPresent())
			return scalar.get();

		List<GraphQLFieldDefinition> fieldsDefinitions = Arrays.stream(type.getDeclaredFields()).map(f -> {
			GraphQLOutputType fieldType;
			if (Collection.class.isAssignableFrom(f.getType())) {
				Class<?> nonGenericType = (Class<?>) ((ParameterizedType) f.getGenericType())
						.getActualTypeArguments()[0];
				fieldType = GraphQLList.list(getOrBuildOutputType(complexTypes, nonGenericType, Attribute.of(f)));
			} else {
				fieldType = getOrBuildOutputType(complexTypes, f.getType(), Attribute.of(f));
			}
			return GraphQLFieldDefinition.newFieldDefinition().name(f.getName()).type(fieldType).build();
		}).toList();

		String name = complexTypes.nextAvailableName(type.getSimpleName());
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name(name);
		fieldsDefinitions.forEach(builder::field);
		GraphQLObjectType complexType = builder.build();
		complexTypes.addOutputType(type, complexType);
		return complexType;
	}

	private GraphQLInputType getOrBuildInputType(ComplexTypes complexTypes, Class<?> type,
			@Nullable Attribute attribute) {
		Optional<GraphQLInputType> complexTypeOpt = complexTypes.getInputType(type);
		if (complexTypeOpt.isPresent())
			return complexTypeOpt.get();

		if (type.isEnum())
			return getOrBuildEnumType(complexTypes, type);

		Optional<GraphQLScalarType> scalar = getBestScalar(type, attribute);
		if (scalar.isPresent()) {
			return scalar.get();
		}

		List<GraphQLInputObjectField> fieldsDefinitions = Arrays.stream(type.getDeclaredFields()).map(f -> {
			GraphQLInputType fieldType;

			if (Collection.class.isAssignableFrom(f.getType())) {
				Class<?> nonGenericType = (Class<?>) ((ParameterizedType) f.getGenericType())
						.getActualTypeArguments()[0];
				fieldType = GraphQLList.list(getOrBuildInputType(complexTypes, nonGenericType, Attribute.of(f)));
			} else {
				fieldType = getOrBuildInputType(complexTypes, f.getType(), Attribute.of(f));
			}

			return GraphQLInputObjectField.newInputObjectField().name(f.getName()).type(fieldType).build();
		}).toList();

		String name = complexTypes.nextAvailableName(type.getSimpleName());
		GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject().name(name);
		fieldsDefinitions.forEach(builder::field);
		GraphQLInputType complexType = builder.build();
		complexTypes.addInputType(type, complexType);
		return complexType;
	}

	private static Stream<Class<?>> getPackageClasses(String thePackage) {
		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

		return provider.findCandidateComponents(thePackage).stream().flatMap(beanDefinition -> {
			try {
				return Optional.of(Class.forName(beanDefinition.getBeanClassName())).stream();
			} catch (ClassNotFoundException e) {
				return Stream.empty();
			}
		});
	}

	private boolean classIsComponentAnotated(Class<?> clazz) {
		return clazz.isAnnotationPresent(Component.class) || Arrays.stream(clazz.getAnnotations())
				.map(Annotation::annotationType).filter(c -> c.getName().startsWith("org.springframework.stereotype"))
				.anyMatch(this::classIsComponentAnotated);
	}
}

class ComplexTypes {
	private Map<Class<?>, GraphQLEnumType> enumTypes = new HashMap<>();
	private Map<Class<?>, GraphQLInputType> inputTypes = new HashMap<>();
	private Map<Class<?>, GraphQLOutputType> outputTypes = new HashMap<>();

	private long getCount(String typeNamePrefix) {
		return Stream.of(enumTypes, inputTypes, outputTypes).flatMap(m -> m.values().stream())
				.filter(t -> hasNamePrefix(t, typeNamePrefix)).count();
	}

	private boolean hasNamePrefix(GraphQLType type, String prefix) {
		if (type instanceof GraphQLNamedSchemaElement o) {
			String typeName = o.getName();
			return typeName.equals(prefix) || typeName.startsWith(prefix + "_");
		}
		return false;
	}

	public Optional<GraphQLEnumType> getEnumType(Class<?> clazz) {
		return Optional.ofNullable(enumTypes.get(clazz));
	}

	public Optional<GraphQLInputType> getInputType(Class<?> clazz) {
		return Optional.ofNullable(inputTypes.get(clazz));
	}

	public Optional<GraphQLOutputType> getOutputType(Class<?> clazz) {
		return Optional.ofNullable(outputTypes.get(clazz));
	}

	public String nextAvailableName(String typeName) {
		long count = getCount(typeName);
		return count == 0 ? typeName : typeName + "_" + (count + 1);
	}

	public void addEnumType(Class<?> clazz, GraphQLEnumType type) {
		enumTypes.put(clazz, type);
	}

	public void addInputType(Class<?> clazz, GraphQLInputType type) {
		inputTypes.put(clazz, type);
	}

	public void addOutputType(Class<?> clazz, GraphQLOutputType type) {
		outputTypes.put(clazz, type);
	}
}

interface Attribute {
	public String getName();

	public <T extends Annotation> T getAnnotation(Class<T> annotationClass);

	public static Attribute of(Field field) { // @formatter:off
 		return new Attribute() {
			@Override public String getName() { return field.getName(); }
			@Override public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
				return field.getAnnotation(annotationClass);
			}
		};
	}// @formatter:on

	public static Attribute of(Parameter parameter) { // @formatter:off
 		return new Attribute() {
			@Override public String getName() { return parameter.getName(); }
			@Override public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
				return parameter.getAnnotation(annotationClass);
			}
		};
	}// @formatter:on
}