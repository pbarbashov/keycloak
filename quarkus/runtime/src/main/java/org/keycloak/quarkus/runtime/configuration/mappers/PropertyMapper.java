/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.quarkus.runtime.configuration.mappers;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiFunction;

import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider;

public class PropertyMapper {

    static PropertyMapper IDENTITY = new PropertyMapper(null, null, null, null, null,
            false,null, null, false,Collections.emptyList(),null, true) {
        @Override
        public ConfigValue getOrDefault(String name, ConfigSourceInterceptorContext context, ConfigValue current) {
            return current;
        }
    };

    private final String to;
    private final String from;
    private final String defaultValue;
    private final BiFunction<String, ConfigSourceInterceptorContext, String> mapper;
    private final String mapFrom;
    private final boolean buildTime;
    private final String description;
    private final boolean mask;
    private final Iterable<String> expectedValues;
    private final ConfigCategory category;
    private final String paramLabel;
    private final boolean hidden;

    PropertyMapper(String from, String to, String defaultValue, BiFunction<String, ConfigSourceInterceptorContext, String> mapper,
            String mapFrom, boolean buildTime, String description, String paramLabel, boolean mask, Iterable<String> expectedValues,
            ConfigCategory category, boolean hidden) {
        this.from = MicroProfileConfigProvider.NS_KEYCLOAK_PREFIX + from;
        this.to = to == null ? this.from : to;
        this.defaultValue = defaultValue;
        this.mapper = mapper == null ? PropertyMapper::defaultTransformer : mapper;
        this.mapFrom = mapFrom;
        this.buildTime = buildTime;
        this.description = description;
        this.paramLabel = paramLabel;
        this.mask = mask;
        this.expectedValues = expectedValues == null ? Collections.emptyList() : expectedValues;
        this.category = category != null ? category : ConfigCategory.GENERAL;
        this.hidden = hidden;
    }

    public static PropertyMapper.Builder builder(String fromProp, String toProp) {
        return new PropertyMapper.Builder(fromProp, toProp);
    }

    public static PropertyMapper.Builder builder(ConfigCategory category) {
        return new PropertyMapper.Builder(category);
    }

    private static String defaultTransformer(String value, ConfigSourceInterceptorContext context) {
        return value;
    }

    ConfigValue getOrDefault(ConfigSourceInterceptorContext context, ConfigValue current) {
        return getOrDefault(null, context, current);        
    }

    ConfigValue getOrDefault(String name, ConfigSourceInterceptorContext context, ConfigValue current) {
        String from = this.from;

        if (to != null && to.endsWith(".")) {
            // in case mapping is based on prefixes instead of full property names
            from = name.replace(to.substring(0, to.lastIndexOf('.')), from.substring(0, from.lastIndexOf('.')));
        }

        // try to obtain the value for the property we want to map
        ConfigValue config = context.proceed(from);

        if (config == null) {
            if (mapFrom != null) {
                // if the property we want to map depends on another one, we use the value from the other property to call the mapper
                String parentKey = MicroProfileConfigProvider.NS_KEYCLOAK + "." + mapFrom;
                ConfigValue parentValue = context.proceed(parentKey);

                if (parentValue != null) {
                    ConfigValue value = transformValue(parentValue.getValue(), context);

                    if (value != null) {
                        return value;
                    }
                }
            }

            // if not defined, return the current value from the property as a default if the property is not explicitly set
            if (defaultValue == null
                    || (current != null && !current.getConfigSourceName().equalsIgnoreCase("default values"))) {
                if (defaultValue == null && mapper != null) {
                    String value = current == null ? null : current.getValue();
                    return ConfigValue.builder().withName(to).withValue(mapper.apply(value, context)).build();
                }
                return current;
            }

            if (mapper != null) {
                return transformValue(defaultValue, context);
            }
            
            return ConfigValue.builder().withName(to).withValue(defaultValue).build();
        }

        if (mapFrom != null) {
            return config;
        }

        if (config.getName().equals(name)) {
            return config;
        }

        ConfigValue value = transformValue(config.getValue(), context);

        // we always fallback to the current value from the property we are mapping
        if (value == null) {
            return current;
        }

        return value;
    }

    public String getFrom() {
        return from;
    }

    public String getDescription() {
        return description;
    }

    public Iterable<String> getExpectedValues() {
        return expectedValues;
    }

    public String getDefaultValue() {return defaultValue; }

    public ConfigCategory getCategory() {
        return category;
    }

    public boolean isHidden() {
        return hidden;
    }

    private ConfigValue transformValue(String value, ConfigSourceInterceptorContext context) {
        if (value == null) {
            return null;
        }

        if (mapper == null) {
            return ConfigValue.builder().withName(to).withValue(value).build();
        }

        String mappedValue = mapper.apply(value, context);

        if (mappedValue != null) {
            return ConfigValue.builder().withName(to).withValue(mappedValue).build();
        }

        return null;
    }

    public boolean isBuildTime() {
        return buildTime;
    }

    boolean isMask() {
        return mask;
    }

    public String getTo() {
        return to;
    }

    public String getParamLabel() {
        return paramLabel;
    }

    public static class Builder {

        private String from;
        private String to;
        private String defaultValue;
        private BiFunction<String, ConfigSourceInterceptorContext, String> mapper;
        private String description;
        private String mapFrom = null;
        private Iterable<String> expectedValues = Collections.emptyList();
        private boolean isBuildTimeProperty = false;
        private boolean isMasked = false;
        private ConfigCategory category = ConfigCategory.GENERAL;
        private String paramLabel;
        private boolean hidden;

        public Builder(ConfigCategory category) {
            this.category = category;
        }

        public Builder(String fromProp, String toProp) {
            this.from = fromProp;
            this.to = toProp;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }


        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder transformer(BiFunction<String, ConfigSourceInterceptorContext, String> mapper) {
            this.mapper = mapper;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder paramLabel(String label) {
            this.paramLabel = label;
            return this;
        }

        public Builder mapFrom(String mapFrom) {
            this.mapFrom = mapFrom;
            return this;
        }

        public Builder expectedValues(Iterable<String> expectedValues) {
            this.expectedValues = expectedValues;
            return this;
        }

        public Builder expectedValues(String... expectedValues) {
            this.expectedValues = Arrays.asList(expectedValues);
            return this;
        }

        public Builder isBuildTimeProperty(boolean isBuildTime) {
            this.isBuildTimeProperty = isBuildTime;
            return this;
        }

        public Builder isMasked(boolean isMasked) {
            this.isMasked = isMasked;
            return this;
        }

        public Builder category(ConfigCategory category) {
            this.category = category;
            return this;
        }

        public Builder type(Class<Boolean> type) {
            if (Boolean.class.equals(type)) {
                expectedValues(Boolean.TRUE.toString(), Boolean.FALSE.toString());
                paramLabel(defaultValue == null ? "true|false" : defaultValue);
                defaultValue(defaultValue == null ? Boolean.FALSE.toString() : defaultValue);
            }
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public PropertyMapper build() {
            return new PropertyMapper(from, to, defaultValue, mapper, mapFrom, isBuildTimeProperty, description, paramLabel,
                    isMasked, expectedValues, category, hidden);
        }
    }
}
