package com.fradantim.graphql2jpa.config;

import java.lang.annotation.Annotation;
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

import com.fradantim.graphql2jpa.annotation.ReturnType;

import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
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
		Map<Class<?>, GraphQLObjectType> complexTypes = new HashMap<>();
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

					Class<?> returnType;
					ReturnType rt = method.getAnnotation(ReturnType.class);
					if (rt != null)
						returnType = rt.value();
					else
						returnType = method.getReturnType();

					GraphQLOutputType type;
					if (Collection.class.isAssignableFrom(method.getReturnType())) {
						type = buildOrGetReturnType(complexTypes, returnType);
						type = GraphQLList.list(type);
					} else {
						type = buildOrGetReturnType(complexTypes, returnType);
					}

					List<GraphQLArgument> arguments = getArguments(method);

					GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition().name(queryName)
							.type(type);
					arguments.forEach(builder::argument);

					queryFields.add(builder.build());
				});

		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name("Query");
		queryFields.forEach(builder::field);
		return GraphQLSchema.newSchema().query(builder).build();
	}

	private List<GraphQLArgument> getArguments(Method method) {
		return Arrays.stream(method.getParameters()).filter(p -> p.getAnnotation(Argument.class) != null).map(p -> {
			String name = p.getAnnotation(Argument.class).name();
			if (name.isBlank())
				name = p.getName();

			String finalName = name;
			return getParameterType(p, name)
					.map(type -> GraphQLArgument.newArgument().name(finalName).type(type).build());
		}).filter(Optional::isPresent).map(Optional::get).toList();
	}

	@SuppressWarnings("unchecked")
	private Optional<GraphQLInputType> getParameterType(Parameter parameter, String name) {
		if ("id".equals(name))
			return Optional.of(Scalars.GraphQLID);

		if ("ids".equals(name))
			return Optional.of(GraphQLList.list(Scalars.GraphQLID));

		Optional<GraphQLInputType> type;
		if (Collection.class.isAssignableFrom(parameter.getType())) {
			Class<?> nonGenericType = (Class<?>) ((ParameterizedType) parameter.getParameterizedType())
					.getActualTypeArguments()[0];

			type = ((Optional<GraphQLInputType>) (Object) getBestScalar(nonGenericType));
			type = type.map(GraphQLList::list);
		} else {
			type = (Optional<GraphQLInputType>) (Object) getBestScalar(parameter.getType());
		}

		if (type.isEmpty())
			logger.warn("No matching graphql type for parameter {} with type {}", name, type);
		return type;
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

	private GraphQLOutputType buildOrGetReturnType(Map<Class<?>, GraphQLObjectType> complexTypes, Class<?> type) {
		if (complexTypes.containsKey(type))
			return complexTypes.get(type);

		Optional<GraphQLScalarType> scalar = getBestScalar(type);
		if (scalar.isPresent())
			return scalar.get();

		List<GraphQLFieldDefinition> fieldsDefinitions = Arrays.stream(type.getDeclaredFields()).map(f -> {
			GraphQLOutputType fieldType;
			if ("id".equals(f.getName()) || f.getAnnotation(Id.class) != null)
				fieldType = Scalars.GraphQLID;
			else {
				if (Collection.class.isAssignableFrom(f.getType())) {
					Class<?> nonGenericType = (Class<?>) ((ParameterizedType) f.getGenericType())
							.getActualTypeArguments()[0];
					fieldType = buildOrGetReturnType(complexTypes, nonGenericType);
					fieldType = GraphQLList.list(fieldType);
				} else {
					fieldType = buildOrGetReturnType(complexTypes, f.getType());
				}
			}

			return GraphQLFieldDefinition.newFieldDefinition().name(f.getName()).type(fieldType).build();
		}).toList();

		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject().name(type.getSimpleName());
		fieldsDefinitions.forEach(builder::field);
		GraphQLObjectType complexType = builder.build();
		complexTypes.put(type, complexType);
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
