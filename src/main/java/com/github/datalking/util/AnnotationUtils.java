
package com.github.datalking.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public abstract class AnnotationUtils {

    public static final String VALUE = "value";

    private static final String REPEATABLE_CLASS_NAME = "java.lang.annotation.Repeatable";

    private static final Map<AnnotationCacheKey, Annotation> findAnnotationCache =
            new ConcurrentHashMap<AnnotationCacheKey, Annotation>(256);

    private static final Map<AnnotationCacheKey, Boolean> metaPresentCache =
            new ConcurrentHashMap<AnnotationCacheKey, Boolean>(256);

    private static final Map<Class<?>, Boolean> annotatedInterfaceCache =
            new ConcurrentHashMap<Class<?>, Boolean>(256);

    private static final Map<Class<? extends Annotation>, Boolean> synthesizableCache =
            new ConcurrentHashMap<Class<? extends Annotation>, Boolean>(256);

    private static final Map<Class<? extends Annotation>, Map<String, List<String>>> attributeAliasesCache =
            new ConcurrentHashMap<Class<? extends Annotation>, Map<String, List<String>>>(256);

    private static final Map<Class<? extends Annotation>, List<Method>> attributeMethodsCache =
            new ConcurrentHashMap<Class<? extends Annotation>, List<Method>>(256);


    public static <A extends Annotation> A findAnnotation(Method method, Class<A> annotationType) {
        Assert.notNull(method, "Method must not be null");
        if (annotationType == null) {
            return null;
        }

        AnnotationCacheKey cacheKey = new AnnotationCacheKey(method, annotationType);
        A result = (A) findAnnotationCache.get(cacheKey);

        if (result == null) {
//            Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
            result = findAnnotation((AnnotatedElement) method, annotationType);

            if (result == null) {
                result = searchOnInterfaces(method, annotationType, method.getDeclaringClass().getInterfaces());
            }

            Class<?> clazz = method.getDeclaringClass();
            while (result == null) {
                clazz = clazz.getSuperclass();
                if (clazz == null || Object.class == clazz) {
                    break;
                }
                try {
                    Method equivalentMethod = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
//                    Method resolvedEquivalentMethod = BridgeMethodResolver.findBridgedMethod(equivalentMethod);
                    result = findAnnotation((AnnotatedElement) equivalentMethod, annotationType);
                } catch (NoSuchMethodException ex) {
                    // No equivalent method found
                }
                if (result == null) {
                    result = searchOnInterfaces(method, annotationType, clazz.getInterfaces());
                }
            }

            if (result != null) {
                findAnnotationCache.put(cacheKey, result);
            }
        }

        return result;
    }

    public static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
        Assert.notNull(annotatedElement, "AnnotatedElement must not be null");
        if (annotationType == null) {
            return null;
        }

        A ann = findAnnotation(annotatedElement, annotationType, new HashSet<>());
        return ann;
    }

    private static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType, Set<Annotation> visited) {
        try {
            Annotation[] anns = annotatedElement.getDeclaredAnnotations();
            for (Annotation ann : anns) {
                if (ann.annotationType() == annotationType) {
                    return (A) ann;
                }
            }
            for (Annotation ann : anns) {
                if (!isInJavaLangAnnotationPackage(ann) && visited.add(ann)) {
                    A annotation = findAnnotation((AnnotatedElement) ann.annotationType(), annotationType, visited);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static boolean isInJavaLangAnnotationPackage(Annotation annotation) {
        return (annotation != null && isInJavaLangAnnotationPackage(annotation.annotationType()));
    }

    static boolean isInJavaLangAnnotationPackage(Class<? extends Annotation> annotationType) {
        return (annotationType != null && isInJavaLangAnnotationPackage(annotationType.getName()));
    }

    public static boolean isInJavaLangAnnotationPackage(String annotationType) {
        return (annotationType != null && annotationType.startsWith("java.lang.annotation"));
    }


    private static <A extends Annotation> A searchOnInterfaces(Method method, Class<A> annotationType, Class<?>... ifcs) {
        A annotation = null;
        for (Class<?> iface : ifcs) {
            if (isInterfaceWithAnnotatedMethods(iface)) {
                try {
                    Method equivalentMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                    annotation = getAnnotation(equivalentMethod, annotationType);
                } catch (NoSuchMethodException ex) {
                    // Skip this interface - it doesn't have the method...
                }
                if (annotation != null) {
                    break;
                }
            }
        }
        return annotation;
    }

    static boolean isInterfaceWithAnnotatedMethods(Class<?> iface) {
        Boolean found = annotatedInterfaceCache.get(iface);
        if (found != null) {
            return found;
        }
        found = Boolean.FALSE;
        for (Method ifcMethod : iface.getMethods()) {
            try {
                if (ifcMethod.getAnnotations().length > 0) {
                    found = Boolean.TRUE;
                    break;
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
        annotatedInterfaceCache.put(iface, found);
        return found;
    }


    public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
//        Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
        return getAnnotation((AnnotatedElement) method, annotationType);
    }

    public static <A extends Annotation> A getAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
        try {
            A annotation = annotatedElement.getAnnotation(annotationType);
            if (annotation == null) {
                for (Annotation metaAnn : annotatedElement.getAnnotations()) {
                    annotation = metaAnn.annotationType().getAnnotation(annotationType);
                    if (annotation != null) {
                        break;
                    }
                }
            }
            return annotation;
        } catch (Throwable ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static final class AnnotationCacheKey implements Comparable<AnnotationCacheKey> {

        private final AnnotatedElement element;

        private final Class<? extends Annotation> annotationType;

        public AnnotationCacheKey(AnnotatedElement element, Class<? extends Annotation> annotationType) {
            this.element = element;
            this.annotationType = annotationType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AnnotationCacheKey)) {
                return false;
            }
            AnnotationCacheKey otherKey = (AnnotationCacheKey) other;
            return (this.element.equals(otherKey.element) && this.annotationType.equals(otherKey.annotationType));
        }

        @Override
        public int hashCode() {
            return (this.element.hashCode() * 29 + this.annotationType.hashCode());
        }

        @Override
        public String toString() {
            return "@" + this.annotationType + " on " + this.element;
        }

        @Override
        public int compareTo(AnnotationCacheKey other) {
            int result = this.element.toString().compareTo(other.element.toString());
            if (result == 0) {
                result = this.annotationType.getName().compareTo(other.annotationType.getName());
            }
            return result;
        }
    }

}
