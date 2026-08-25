/*
 * Copyright 2006 Envoi Solutions LLC
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ow2.authzforce.webapp.org.codehaus.jettison.mapped;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jettison.mapped.DefaultConverter;
import org.codehaus.jettison.mapped.TypeConverter;

/**
 * Required by {@link MappedNamespaceConvention} in the same package
 */
public class Configuration
{
	/* Were there a constants class, this key would live there. */
	private static final String JETTISON_TYPE_CONVERTER_CLASS_KEY = "jettison.mapped.typeconverter.class";

	/* Mostly exists to wrap exception handling for reflective invocations. */
	private static class ConverterFactory
	{
		TypeConverter newDefaultConverterInstance()
		{
			return new DefaultConverter();
		}
	}

	private static final ConverterFactory converterFactory;

	static
	{
		ConverterFactory cf = null;
		final String userSpecifiedClass = System.getProperty(JETTISON_TYPE_CONVERTER_CLASS_KEY);
		if (userSpecifiedClass != null && userSpecifiedClass.length() > 0)
		{
			try
			{
				final Class<? extends TypeConverter> tc = Class.forName(userSpecifiedClass).asSubclass(TypeConverter.class);
				final TypeConverter  typeConverter = tc.getDeclaredConstructor().newInstance(); /* Blow up as soon as possible. */
				cf = new ConverterFactory()
				{
					@Override
					public TypeConverter newDefaultConverterInstance()
					{
						try
						{
							return typeConverter;
						}
						catch (final Exception e)
						{
							// Implementer of custom class would have to try pretty hard to make this happen,
							// since we already created one instance.
							throw new ExceptionInInitializerError(e);
						}
					}
				};
			}
			catch (final Exception e)
			{
				throw new ExceptionInInitializerError(e);
			}
		}
		if (cf == null)
		{
			cf = new ConverterFactory();
		}
		converterFactory = cf;
	}

	private Map<String, String> xmlToJsonNamespaces;
	private List<String> attributesAsElements;
	/*
	 * BEGIN CHANGE to transform specific JSON keys - not prefixed with attributeKey - to XML attributes (opposite of attributesAsElements)
	 */
	private Set<String> elementsAsAttributes = Collections.emptySet();
	/*
	 * END
	 */
	private List<String> ignoredElements;
	private boolean suppressAtAttributes;
	private String attributeKey = "@";
	private boolean ignoreNamespaces;
	private boolean dropRootElement;
	private Set<String> primitiveArrayKeys = Collections.emptySet();
	private boolean writeNullAsString = true;
	private boolean readNullAsString;
	private boolean ignoreEmptyArrayValues;
	private boolean escapeForwardSlashAlways;
	private String jsonNamespaceSeparator;

	private TypeConverter typeConverter = converterFactory.newDefaultConverterInstance();

	/**
	 * Default constructor
	 */
	public Configuration()
	{
		super();
		this.xmlToJsonNamespaces = new HashMap<>();
	}

	/**
	 * Constructor from XML-namespace-to-JSON-prefix mappings
	 * @param xmlToJsonNamespaces the mappings
	 */
	public Configuration(final Map<String, String> xmlToJsonNamespaces)
	{
		super();
		this.xmlToJsonNamespaces = xmlToJsonNamespaces;
	}

	/**
	 * Constructor
	 * @param xmlToJsonNamespaces XML-namespace-to-JSON-prefix mappings
	 * @param attributesAsElements XML attributes to be converted like elements (without '@' prefix in JSON)
	 * @param ignoredElements XML elements ignored in XML-to-JSON conversion
	 */
	public Configuration(final Map<String, String> xmlToJsonNamespaces, final List<String> attributesAsElements, final List<String> ignoredElements)
	{
		super();
		this.xmlToJsonNamespaces = xmlToJsonNamespaces;
		this.attributesAsElements = attributesAsElements;
		this.ignoredElements = ignoredElements;
	}

	/**
	 * Are namespaces ignored?
	 * @return true iff they are
	 */
	public boolean isIgnoreNamespaces()
	{
		return ignoreNamespaces;
	}

	/**
	 * Ignore namespaces
	 * @param ignoreNamespaces true to ignore
	 */
	public void setIgnoreNamespaces(final boolean ignoreNamespaces)
	{
		this.ignoreNamespaces = ignoreNamespaces;
	}

	/**
	 * Get the attributesAsElements property
	 * @return the property value
	 */
	public List<String> getAttributesAsElements()
	{
		return attributesAsElements;
	}

	/**
	 * Set the attributesAsElements property
	 * @param attributesAsElements property value
	 */
	public void setAttributesAsElements(final List<String> attributesAsElements)
	{
		this.attributesAsElements = attributesAsElements;
	}

	/**
	 * Get the ignored XML elements
	 * @return the ignored elements
	 */
	public List<String> getIgnoredElements()
	{
		return ignoredElements;
	}

	/**
	 * Set the ignored XML elements
	 * @param ignoredElements ignored elements
	 */
	public void setIgnoredElements(final List<String> ignoredElements)
	{
		this.ignoredElements = ignoredElements;
	}

	/**
	 * Get the XML-namespace-to-JSON-prefix mappings
	 * @return the mappings
	 */
	public Map<String, String> getXmlToJsonNamespaces()
	{
		return xmlToJsonNamespaces;
	}

	/**
	 * Set the ML-namespace-to-JSON-prefix mappings
	 * @param xmlToJsonNamespaces mappings
	 */
	public void setXmlToJsonNamespaces(final Map<String, String> xmlToJsonNamespaces)
	{
		this.xmlToJsonNamespaces = xmlToJsonNamespaces;
	}

	/**
	 * Get the type converter
	 * @return type converter
	 */
	public TypeConverter getTypeConverter()
	{
		return typeConverter;
	}

	/**
	 * Set the type converter
	 * @param typeConverter type converter
	 */
	public void setTypeConverter(final TypeConverter typeConverter)
	{
		this.typeConverter = typeConverter;
	}

	/**
	 * Is the '@' prefix suppressed from XML attributes converted to JSON?
	 * @return true iff it is
	 */
	public boolean isSupressAtAttributes()
	{
		return this.suppressAtAttributes;
	}

	/**
	 * Enable/Disable adding the '@' prefix before the XML attribute name in the JSON output
	 * @param supressAtAttributes true iff disabled
	 */
	public void setSupressAtAttributes(final boolean supressAtAttributes)
	{
		this.suppressAtAttributes = supressAtAttributes;
	}

	/**
	 * Get the prefix for XML attributes mapped to JSON. Default: at sign ('@').
	 * @return the prefix
	 */
	public String getAttributeKey()
	{
		return this.attributeKey;
	}

	/**
	 * Set the prefix for XML attributes mapped to JSON. Default: at sign ('@').
	 * @param attributeKey the prefix
	 */
	public void setAttributeKey(final String attributeKey)
	{
		this.attributeKey = attributeKey;
	}

	/**
	 * Create default type converter
	 * @return converter instance
	 */
	static TypeConverter newDefaultConverterInstance()
	{
		return converterFactory.newDefaultConverterInstance();
	}

	/**
	 * Get Jettison primitiveArrayKeys property
	 * @return the property value
	 */
	public Set<String> getPrimitiveArrayKeys()
	{
		return primitiveArrayKeys;
	}

	/**
	 * Set the Jettison primitiveArrayKeys property
	 * @param primitiveArrayKeys the property value
	 */
	public void setPrimitiveArrayKeys(final Set<String> primitiveArrayKeys)
	{
		this.primitiveArrayKeys = primitiveArrayKeys;
	}

	/**
	 * Is the root XML element dropped?
	 * @return true iff dropped
	 */
	public boolean isDropRootElement()
	{
		return dropRootElement;
	}

	/**
	 * Enable/disable dropping the root XML element
	 * @param dropRootElement true iff enabled
	 */
	public void setDropRootElement(final boolean dropRootElement)
	{
		this.dropRootElement = dropRootElement;
	}

	/**
	 * Is the Jettison writeNullAsString property true?
	 * @return the property value
	 */
	public boolean isWriteNullAsString()
	{
		return writeNullAsString;
	}

	/**
	 * Set the Jettison writeNullAsString property
	 * @param writeNullAsString  the property value
	 */
	public void setWriteNullAsString(final boolean writeNullAsString)
	{
		this.writeNullAsString = writeNullAsString;
	}

	/**
	 * Get the Jettison readNullAsString property
	 * @return  the property value
	 */
	public boolean isReadNullAsString()
	{
		return readNullAsString;
	}

	/**
	 * Set the Jettison readNullAsString property
	 * @param readNullString  the property value
	 */
	public void setReadNullAsString(final boolean readNullString)
	{
		this.readNullAsString = readNullString;
	}

	/**
	 * Get the Jettison ignoreEmptyArrayValues property
	 * @return  the property value
	 */
	public boolean isIgnoreEmptyArrayValues()
	{
		return ignoreEmptyArrayValues;
	}

	/**
	 * Set the Jettison ignoreEmptyArrayValues property
	 * @param ignoreEmptyArrayValues  the property value
	 */
	public void setIgnoreEmptyArrayValues(final boolean ignoreEmptyArrayValues)
	{
		this.ignoreEmptyArrayValues = ignoreEmptyArrayValues;
	}

	/**
	 * Set the jettison readNullAsEmptyString property
	 * @param read property value
	 */
	@Deprecated
	public void setReadNullAsEmptyString(final boolean read)
	{
	}

	/**
	 * Get the Jettison escapeForwardSlashAlways property
	 * @return  the property value
	 */
	public boolean isEscapeForwardSlashAlways()
	{
		return escapeForwardSlashAlways;
	}

	/**
	 * Set the Jettison escapeForwardSlashAlways property
	 * @param escapeForwardSlash  the property value
	 */
	public void setEscapeForwardSlashAlways(final boolean escapeForwardSlash)
	{
		this.escapeForwardSlashAlways = escapeForwardSlash;
	}

	/**
	 * Get the Jettison jsonNamespaceSeparator property
	 * @return  the property value
	 */
	public String getJsonNamespaceSeparator()
	{
		return jsonNamespaceSeparator;
	}

	/**
	 * Set the Jettison jsonNamespaceSeparator property
	 * @param jsonNamespaceSeparator  the property value
	 */
	public void setJsonNamespaceSeparator(final String jsonNamespaceSeparator)
	{
		this.jsonNamespaceSeparator = jsonNamespaceSeparator;
	}

	/*
	 * BEGIN CHANGE to transform specific JSON keys - not starting with $attributeKey - to attributes (opposite of attributesAsElements)
	 */
	/**
	 * Get the Jettison elementsAsAttributes property
	 * @return  the property value
	 */
	public Set<String> getElementsAsAttributes()
	{
		return this.elementsAsAttributes;
	}

	/**
	 * Set the Jettison elementsAsAttributes property
	 * @param elementsAsAttributes  the property value
	 */
	public void setElementsAsAttributes(final Set<String> elementsAsAttributes)
	{
		this.elementsAsAttributes = elementsAsAttributes;
	}
	/*
	 * END
	 */
}
