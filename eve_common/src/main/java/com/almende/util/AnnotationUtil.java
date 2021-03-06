/**
 * @file AnnotationUtil.java
 *       AnnotationUtil is a utility to get all annotations of a class, its
 *       methods,
 *       and the method parameters. Returned annotations include all annotations
 *       of
 *       the classes interfaces and super classes.
 *       Requested classes are cached, so requesting a classes annotations
 *       repeatedly
 *       is fast.
 *       Example usage:
 *       AnnotatedClass annotatedClass = AnnotationUtil.get(MyClass.class);
 *       List<AnnotatedMethod> methods = annotatedClass.getMethods();
 *       for (AnnotatedMethod method : methods) {
 *       System.out.println("Method: " + method.getName());
 *       List<Annotation> annotations = method.getAnnotations();
 *       for (Annotation annotation : annotations) {
 *       System.out.println("    Annotation: " + annotation.toString());
 *       }
 *       }
 * @brief
 *        AnnotationUtil is a utility to retrieve merged annotations from a
 *        class
 *        including all its superclasses and interfaces.
 * @license
 *          Licensed under the Apache License, Version 2.0 (the "License"); you
 *          may not
 *          use this file except in compliance with the License. You may obtain
 *          a copy
 *          of the License at
 *          http://www.apache.org/licenses/LICENSE-2.0
 *          Unless required by applicable law or agreed to in writing, software
 *          distributed under the License is distributed on an "AS IS" BASIS,
 *          WITHOUT
 *          WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *          the
 *          License for the specific language governing permissions and
 *          limitations under
 *          the License.
 *          Copyright (c) 2013 Almende B.V.
 * @author Jos de Jong, <jos@almende.org>
 * @date 2013-01-21
 */
package com.almende.util;

import java.lang.annotation.Annotation;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Class AnnotationUtil.
 */
public final class AnnotationUtil {
	private static final Logger					LOG		= Logger.getLogger(AnnotationUtil.class
																.getName());
	private static Map<String, AnnotatedClass>	cache	= new ConcurrentHashMap<String, AnnotatedClass>();

	private AnnotationUtil() {}

	/**
	 * Get all annotations of a class, methods, and parameters.
	 * Returned annotations include all annotations of the classes interfaces
	 * and super classes (excluding java.lang.Object).
	 * 
	 * @param clazz
	 *            the clazz
	 * @return annotatedClazz
	 */
	public static AnnotatedClass get(final Class<?> clazz) {
		AnnotatedClass annotatedClazz = cache.get(clazz.getName());
		if (annotatedClazz == null) {
			annotatedClazz = new AnnotatedClass(clazz);
			cache.put(clazz.getName(), annotatedClazz);
		}
		return annotatedClazz;
	}

	/**
	 * The Class CachedAnnotation.
	 */
	public static class CachedAnnotation {
		private Annotation	annotation	= null;
		private Object		value		= null;

		/**
		 * Instantiates a new cached annotation.
		 *
		 * @param annotation
		 *            the annotation
		 */
		public CachedAnnotation(Annotation annotation) {
			this.annotation = annotation;
			try {
				Method method = annotation.getClass().getMethod("value",
						new Class<?>[0]);
				if (method != null) {
					value = method.invoke(annotation, new Object[0]);
				}
			} catch (NoSuchMethodException | SecurityException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {}
		}

		/**
		 * Value.
		 *
		 * @return the object
		 */
		public Object value() {
			return value;
		}

		/**
		 * Gets the annotation.
		 *
		 * @return the annotation
		 */
		public Annotation getAnnotation() {
			return annotation;
		}
	}

	/**
	 * AnnotatedClass describes a class, its annotations, and its methods.
	 */
	public static class AnnotatedClass extends AnnotatedThing {

		/** The clazz. */
		private Class<?>									clazz		= null;
		private final List<AnnotatedMethod>					methodList	= new ArrayList<AnnotatedMethod>(
																				5);
		private final Map<Class<?>, List<AnnotatedMethod>>	methods		= new HashMap<Class<?>, List<AnnotatedMethod>>(
																				5);
		private final Map<String, List<AnnotatedMethod>>	methodNames	= new HashMap<String, List<AnnotatedMethod>>(
																				5);
		private final List<AnnotatedField>					fieldList	= new ArrayList<AnnotatedField>(
																				1);
		private final Map<Class<?>, List<AnnotatedField>>	fields		= new HashMap<Class<?>, List<AnnotatedField>>(
																				1);

		/**
		 * Create a new AnnotatedClass.
		 *
		 * @param clazz
		 *            the clazz
		 */
		public AnnotatedClass(final Class<?> clazz) {
			this.clazz = clazz;
			merge(clazz);
			AnnotationUtil.convertMethods(methods, methodList);
			AnnotationUtil.convertMethodNames(methodNames, methodList);
			AnnotationUtil.convertFields(fields, fieldList);
		}

		/**
		 * Recursively merge a class into this AnnotatedClass.
		 * The method loops over all the classess interfaces and superclasses
		 * Methods with will be merged.
		 * 
		 * @param clazz
		 *            the clazz
		 * @param includeObject
		 *            if true, superclass java.lang.Object will
		 *            be included too.
		 */
		private void merge(final Class<?> clazz) {
			Class<?> c = clazz;
			while (c != null && c != Object.class) {
				// merge the annotations
				super.merge(c.getDeclaredAnnotations());

				// merge the methods
				AnnotationUtil.merge(methodList, c.getDeclaredMethods());
				AnnotationUtil.merge(fieldList, c.getDeclaredFields());

				// merge all interfaces and the superclasses of the interfaces
				for (final Class<?> i : c.getInterfaces()) {
					merge(i);
				}

				// ok now again for the superclass
				c = c.getSuperclass();
			}
		}

		/**
		 * Get the actual Java class described by this AnnotatedClass.
		 * 
		 * @return clazz
		 */
		public Class<?> getActualClass() {
			return clazz;
		}

		/**
		 * Get all methods including methods declared in superclasses.
		 * 
		 * @return methods
		 */
		public List<AnnotatedMethod> getMethods() {
			return methodList;
		}

		/**
		 * Get all methods, grouped by name allowing detection of overloading.
		 *
		 * @return the method names
		 */
		public Map<String, List<AnnotatedMethod>> getMethodNames() {
			return methodNames;
		}
		
		/**
		 * Get all methods including methods declared in superclasses, filtered
		 * by name.
		 * 
		 * @param name
		 *            the name
		 * @return filteredMethods
		 */
		public List<AnnotatedMethod> getMethods(final String name) {
			if (methodNames.containsKey(name)) {
				return methodNames.get(name);
			} else {
				return Collections.emptyList();
			}
		}

		/**
		 * Get all methods including methods declared in superclasses, filtered
		 * by annotation.
		 * 
		 * @param <T>
		 *            the generic type
		 * @param annotation
		 *            the annotation
		 * @return filteredMethods
		 */
		public <T> List<AnnotatedMethod> getAnnotatedMethods(
				final Class<T> annotation) {
			if (methods.containsKey(annotation)) {
				return methods.get(annotation);
			} else {
				return Collections.emptyList();
			}
		}

		/**
		 * Get all fields including fields declared in superclasses, filtered
		 * by annotation.
		 * 
		 * @param <T>
		 *            the generic type
		 * @param annotation
		 *            the annotation
		 * @return filteredMethods
		 */
		public <T> List<AnnotatedField> getAnnotatedFields(
				final Class<T> annotation) {
			if (fields.containsKey(annotation)) {
				return fields.get(annotation);
			} else {
				return Collections.emptyList();
			}
		}
	}

	/**
	 * The Class AnnotatedField.
	 */
	public static class AnnotatedField extends AnnotatedThing {

		/** The field. */
		private Field	field	= null;

		/** The name. */
		private String	name	= null;

		/** The type. */
		private Type	type	= null;

		/**
		 * Instantiates a new annotated field.
		 * 
		 * @param field
		 *            the field
		 */
		public AnnotatedField(final Field field) {
			super();
			this.field = field;
			name = field.getName();
			type = field.getType();

			merge(field);
		}

		/**
		 * Merge a java method into this Annotated method.
		 * Annotations and parameter annotations will be merged.
		 * 
		 * @param field
		 *            the field
		 */
		private void merge(final Field field) {
			super.merge(field.getDeclaredAnnotations());
		}

		/**
		 * Gets the field.
		 * 
		 * @return the field
		 */
		public Field getField() {
			return field;
		}

		/**
		 * Gets the name.
		 * 
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * Gets the type.
		 * 
		 * @return the type
		 */
		public Type getType() {
			return type;
		}

	}

	/**
	 * AnnotatedMethod describes a method and its parameters.
	 */
	public static class AnnotatedMethod extends AnnotatedThing {

		/** The method. */
		private final Method				method;

		/** The name. */
		private String						name				= null;

		/** The return type. */
		private Class<?>					returnType			= null;

		/** The generic return type. */
		private Type						genericReturnType	= null;

		/** The parameters. */
		private final List<AnnotatedParam>	parameters			= new ArrayList<AnnotatedParam>(
																		5);

		private boolean						isVoid				= false;
		private MethodHandle				methodHandle;

		/**
		 * Instantiates a new annotated method.
		 * 
		 * @param method
		 *            the method
		 * @throws IllegalAccessException
		 *             the illegal access exception
		 */
		public AnnotatedMethod(final Method method)
				throws IllegalAccessException {
			super();

			this.method = method;
			method.setAccessible(true);
			name = method.getName();
			returnType = method.getReturnType();
			genericReturnType = method.getGenericReturnType();
			isVoid = (returnType == void.class || returnType == Void.class);
			merge(method);

			if (Defines.HASMETHODHANDLES) {
				MethodType newType;

				int i = 0;
				if (!Modifier.isStatic(method.getModifiers())) {
					i = 1;
				}
				Class<?>[] parameterArray = new Class<?>[parameters.size() + i];
				if (i == 1) {
					parameterArray[0] = method.getDeclaringClass();
				}
				for (AnnotatedParam parm : parameters) {
					parameterArray[i++] = parm.getType();
				}
				if (isVoid) {
					newType = MethodType.methodType(void.class, parameterArray);
				} else {
					newType = MethodType.methodType(Object.class,
							parameterArray);
				}

				try {
					methodHandle = new ConstantCallSite(MethodHandles
							.lookup()
							.unreflect(method)
							.asType(newType)
							.asSpreader(Object[].class,
									newType.parameterCount())).getTarget();
				} catch (WrongMethodTypeException e) {
					final IllegalAccessException res = new IllegalAccessException();
					res.initCause(e);
					throw res;
				}
			}
		}

		/**
		 * Merge a java method into this Annotated method.
		 * Annotations and parameter annotations will be merged.
		 * 
		 * @param method
		 *            the method
		 */
		private void merge(final Method method) {
			// merge the annotations
			super.merge(method.getDeclaredAnnotations());

			// merge the params
			final Annotation[][] params = method.getParameterAnnotations();
			final Class<?>[] types = method.getParameterTypes();
			final Type[] genericTypes = method.getGenericParameterTypes();
			for (int i = 0; i < params.length; i++) {
				if (i > parameters.size() - 1) {
					parameters.add(new AnnotatedParam(params[i], types[i],
							genericTypes[i]));
				} else {
					parameters.get(i).merge(params[i]);
				}
			}
		}

		/**
		 * Checks if is void.
		 *
		 * @return true, if is void
		 */
		public boolean isVoid() {
			return isVoid;
		}

		/**
		 * Get the actual method as MethodHandler.
		 * 
		 * @return methodHandle
		 */
		public MethodHandle getMethodHandle() {
			return methodHandle;
		}

		/**
		 * Get the actual Java method described by this AnnotatedMethod.
		 * 
		 * @return method
		 */
		public Method getActualMethod() {
			return method;
		}

		/**
		 * Get the method name.
		 * 
		 * @return name
		 */
		public String getName() {
			return name;
		}

		/**
		 * Get the return type of the method.
		 * 
		 * @return returnType
		 */
		public Class<?> getReturnType() {
			return returnType;
		}

		/**
		 * Get the generic return type of the method.
		 * 
		 * @return genericType
		 */
		public Type getGenericReturnType() {
			return genericReturnType;
		}

		/**
		 * Get all parameter annotations of this method, defined in all
		 * implementations and interfaces of the methods declaring class.
		 * 
		 * @return params
		 */
		public List<AnnotatedParam> getParams() {
			return parameters;
		}
	}

	/**
	 * AnnotatedParam describes all annotations of a parameter.
	 */
	public static class AnnotatedParam extends AnnotatedThing {

		/** The annotations. */

		/** The type. */
		private Class<?>	type		= null;

		/** The generic type. */
		private Type		genericType	= null;

		/**
		 * Instantiates a new annotated param.
		 */
		public AnnotatedParam() {
			super();
		}

		/**
		 * Instantiates a new annotated param.
		 * 
		 * @param annotations
		 *            the annotations
		 * @param type
		 *            the type
		 * @param genericType
		 *            the generic type
		 */
		public AnnotatedParam(final Annotation[] annotations,
				final Class<?> type, final Type genericType) {
			super();
			this.type = type;
			this.genericType = genericType;

			merge(annotations);
		}

		/**
		 * Get the type of the parameter.
		 * 
		 * @return type
		 */
		public Class<?> getType() {
			return type;
		}

		/**
		 * Get the generic type of the parameter.
		 * 
		 * @return genericType
		 */
		public Type getGenericType() {
			return genericType;
		}
	}

	/**
	 * The Class AnnotatedThing.
	 */
	public static class AnnotatedThing {

		private final List<CachedAnnotation>				annotationList	= new ArrayList<CachedAnnotation>();

		private final Map<Class<?>, List<CachedAnnotation>>	annotations		= new HashMap<Class<?>, List<CachedAnnotation>>(
																					1);

		/**
		 * Instantiates a new annotated thing.
		 */
		public AnnotatedThing() {}

		/**
		 * Merge.
		 * 
		 * @param annotations
		 *            the annotations
		 */
		public void merge(final Annotation[] annotations) {
			// merge the annotations
			AnnotationUtil.merge(this.annotationList, annotations);
			AnnotationUtil.convertAnnotations(this.annotations,
					this.annotationList);
		}

		/**
		 * Get all annotations of this parameter, defined in all implementations
		 * and interfaces of the class.
		 * 
		 * @return annotations
		 */
		public List<CachedAnnotation> getAnnotations() {
			if (annotations.size() == 1) {
				return annotations.values().iterator().next();
			}
			List<CachedAnnotation> res = new ArrayList<CachedAnnotation>();
			for (List<CachedAnnotation> sublist : annotations.values()) {
				res.addAll(sublist);
			}
			return res;
		}

		/**
		 * Get an annotation of this parameter by type.
		 * Returns null if not available.
		 * 
		 * @param <T>
		 *            the generic type
		 * @param type
		 *            the type
		 * @return annotation
		 */
		public <T extends Annotation> CachedAnnotation getAnnotation(
				final Class<T> type) {
			List<CachedAnnotation> res = annotations.get(type);
			if (res != null && !res.isEmpty()) {
				return res.get(0);
			}
			return null;
		}
	}

	/**
	 * Merge an array with annotations (listB) into a list with
	 * annotations (listA).
	 * 
	 * @param listA
	 *            the list a
	 * @param listB
	 *            the list b
	 */
	private static void merge(final List<CachedAnnotation> listA,
			final Annotation[] listB) {
		for (final Annotation b : listB) {
			boolean found = false;
			for (final CachedAnnotation a : listA) {
				if (a.getAnnotation().getClass() == b.getClass()) {
					found = true;
					break;
				}
			}
			if (!found) {
				listA.add(new CachedAnnotation(b));
			}
		}
	}

	private static void convertFields(
			final Map<Class<?>, List<AnnotatedField>> fields,
			final List<AnnotatedField> fieldList) {
		for (AnnotatedField field : fieldList) {
			for (CachedAnnotation annotation : field.getAnnotations()) {
				List<AnnotatedField> list = fields.get(annotation
						.getAnnotation().annotationType());
				if (list == null) {
					list = new ArrayList<AnnotatedField>(1);
					fields.put(annotation.getAnnotation().annotationType(),
							list);
				}
				list.add(field);
			}
		}
	}

	private static void convertMethodNames(
			final Map<String, List<AnnotatedMethod>> methodNames,
			final List<AnnotatedMethod> methodList) {
		for (AnnotatedMethod method : methodList) {
			List<AnnotatedMethod> list = methodNames.get(method.name);
			if (list == null) {
				list = new ArrayList<AnnotatedMethod>(1);
				methodNames.put(method.name, list);
			}
			list.add(method);
		}
	}

	private static void convertMethods(
			final Map<Class<?>, List<AnnotatedMethod>> methods,
			final List<AnnotatedMethod> methodList) {
		for (AnnotatedMethod method : methodList) {
			for (CachedAnnotation annotation : method.getAnnotations()) {
				List<AnnotatedMethod> list = methods.get(annotation
						.getAnnotation().annotationType());
				if (list == null) {
					list = new ArrayList<AnnotatedMethod>(1);
					methods.put(annotation.getAnnotation().annotationType(),
							list);
				}
				list.add(method);
			}
		}
	}

	private static void convertAnnotations(
			final Map<Class<?>, List<CachedAnnotation>> annotations,
			final List<CachedAnnotation> annotationList) {
		for (CachedAnnotation annotation : annotationList) {
			List<CachedAnnotation> list = annotations.get(annotation
					.getAnnotation().annotationType());
			if (list == null) {
				list = new ArrayList<CachedAnnotation>(1);
				annotations.put(annotation.getAnnotation().annotationType(),
						list);
			}
			list.add(annotation);
		}
	}

	/**
	 * Merge an array of methods (listB) into a list with method
	 * annotations (listA).
	 * 
	 * @param listA
	 *            the list a
	 * @param listB
	 *            the list b
	 * @throws IllegalAccessException
	 */
	private static void merge(final List<AnnotatedMethod> listA,
			final Method[] listB) {
		for (final Method b : listB) {
			AnnotatedMethod methodAnnotations = null;
			for (final AnnotatedMethod a : listA) {
				if (areEqual(a.method, b)) {
					methodAnnotations = a;
					break;
				}
			}

			if (methodAnnotations != null) {
				methodAnnotations.merge(b);
			} else {
				try {
					listA.add(new AnnotatedMethod(b));
				} catch (IllegalAccessException e) {
					LOG.log(Level.SEVERE, "Failed to obtain AnnotatedMethod:"
							+ b.getName(), e);
				}
			}
		}
	}

	/**
	 * Merge an array with annotations (listB) into a list with
	 * annotations (listA).
	 * 
	 * @param listA
	 *            the list a
	 * @param listB
	 *            the list b
	 */
	private static void merge(final List<AnnotatedField> listA,
			final Field[] listB) {
		for (final Field b : listB) {
			AnnotatedField fieldAnnotations = null;
			for (final AnnotatedField a : listA) {
				if (areEqual(a.field, b)) {
					fieldAnnotations = a;
					break;
				}
			}

			if (fieldAnnotations != null) {
				fieldAnnotations.merge(b);
			} else {
				listA.add(new AnnotatedField(b));
			}
		}
	}

	/**
	 * Test if two methods have equal names, return type, param count,
	 * and param types.
	 * 
	 * @param a
	 *            the a
	 * @param b
	 *            the b
	 * @return true, if successful
	 */
	private static boolean areEqual(final Method a, final Method b) {
		// http://stackoverflow.com/q/10062957/1262753
		if (!a.getName().equals(b.getName())) {
			return false;
		}
		if (a.getReturnType() != b.getReturnType()) {
			return false;
		}

		final Class<?>[] paramsa = a.getParameterTypes();
		final Class<?>[] paramsb = b.getParameterTypes();
		if (paramsa.length != paramsb.length) {
			return false;
		}
		for (int i = 0; i < paramsa.length; i++) {
			if (paramsa[i] != paramsb[i]) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Test if two fields have equal names and types.
	 * 
	 * @param a
	 *            the a
	 * @param b
	 *            the b
	 * @return true, if successful
	 */
	private static boolean areEqual(final Field a, final Field b) {
		// http://stackoverflow.com/q/10062957/1262753
		if (!a.getName().equals(b.getName())) {
			return false;
		}
		if (a.getType() != b.getType()) {
			return false;
		}
		return true;
	}
}
