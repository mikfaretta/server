/*
 * Copyright (C) 2012-2017 Thales Services SAS.
 *
 * This file is part of AuthzForce CE.
 *
 * AuthzForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthzForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthzForce CE.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice, following Software Freedom Law Center's recommendations <http://www.softwarefreedom.org/resources/2007/gpl-non-gpl-collaboration.html>:  
 *  
 *    Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with Apache CXF work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.  
 * 
 * FIXME: to avoid this patched version of CXF {@link JSONProvider}, this code should be merged into CXF source ultimately (e.g. by submitting a patch to CXF developers). This patched version affects private parts of the original class, therefore there is no way to achieve the same result with simple class derivation.
 */
package org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json;

import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.NameChecker;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.Nullable;
import org.apache.cxf.jaxrs.provider.AbstractJAXBProvider;
import org.apache.cxf.jaxrs.utils.*;
import org.apache.cxf.staxutils.DepthRestrictingStreamReader;
import org.apache.cxf.staxutils.DocumentDepthProperties;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.staxutils.W3CDOMStreamWriter;
import org.apache.cxf.staxutils.transform.TransformUtils;
import org.codehaus.jettison.AbstractXMLInputFactory;
import org.codehaus.jettison.JSONSequenceTooLargeException;
import org.codehaus.jettison.mapped.SimpleConverter;
import org.codehaus.jettison.mapped.TypeConverter;
import org.codehaus.jettison.util.StringIndenter;
import org.ow2.authzforce.webapp.HardenedMappedXMLInputFactory;
import org.ow2.authzforce.webapp.SetPropertyAllowingMappedXMLInputFactory;
import org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.utils.JSONUtils;
import org.ow2.authzforce.webapp.org.codehaus.jettison.mapped.Configuration;
import org.w3c.dom.Document;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Fix for CXF issue: {@link org.apache.cxf.jaxrs.provider.json.JSONProvider} does not enforce JAX-RS (JSON in this case) depth control properties org.apache.cxf.stax.* (only innerElementCountThreshold is enforced and only affects
 * JSONObject key-value pairs, not JSONArray elements!), as of CXF 3.1.8. This fix is adapted from protected method {@code getStreamReader()} of
 * {@link org.apache.cxf.jaxrs.provider.JAXBElementProvider}
 * <p>
 * FIXME: report this issue
 * <p>
 * This implementation, like {@link JSONProvider}, is not as efficient as it could be, because JSON parsing is done after the whole JSON payload has been parsed into a String by
 * {@link AbstractXMLInputFactory}#readAll() methods
 * 
 * @param <T>
 *            Java type supported by the provider
 */
@Produces({ "application/json", "application/*+json" })
@Consumes({ "application/json", "application/*+json" })
@Provider
public class JSONProvider<T> extends AbstractJAXBProvider<T>
{
	private static final String MAPPED_CONVENTION = "mapped";
	private static final String BADGER_FISH_CONVENTION = "badgerfish";
	private static final String DROP_ROOT_CONTEXT_PROPERTY = "drop.json.root.element";
	private static final String ARRAY_KEYS_PROPERTY = "json.array.keys";
	private static final String ROOT_IS_ARRAY_PROPERTY = "json.root.is.array";
	private static final String DROP_ELEMENT_IN_XML_PROPERTY = "drop.xml.elements";
	private static final String IGNORE_EMPTY_JSON_ARRAY_VALUES_PROPERTY = "ignore.empty.json.array.values";

	private final ConcurrentHashMap<String, String> namespaceMap = new ConcurrentHashMap<>();
	private boolean serializeAsArray;
	private List<String> arrayKeys;
	private List<String> primitiveArrayKeys;
	private boolean unwrapped;
	private String wrapperName;
	private String namespaceSeparator;
	private Map<String, String> wrapperMap;
	private boolean dropRootElement;
	private boolean dropElementsInXmlStream = true;
	private boolean dropCollectionWrapperElement;
	private boolean ignoreMixedContent;
	private boolean ignoreEmptyArrayValues;
	private boolean writeXsiType = true;
	private boolean readXsiType = true;
	private boolean ignoreNamespaces;
	// private String convention = MAPPED_CONVENTION;
	private TypeConverter typeConverter;
	private boolean attributesToElements;
	private boolean writeNullAsString = true;
	private boolean escapeForwardSlashesAlways;

	/*
	 * BEGIN CHANGE to {@link org.apache.cxf.jaxrs.provider.json.JSONProvider}
	 */

	/**
	 * XMLStreamReader factory based on specified JSON-to-XML convention (attribute convention)
	 */
	private interface JsonToXmlStreamReaderFactory
	{
		/**
		 * Adapted from {@link JSONUtils#createStreamReader(InputStream, boolean, java.util.concurrent.ConcurrentHashMap, String, List, DocumentDepthProperties, String)} to support extra property
		 * maxStringLength
		 */
		XMLStreamReader createReader(InputStream is, boolean readXsiType, ConcurrentMap<String, String> namespaceMap, String namespaceSeparator, List<String> elementsToAttributes,
				List<String> primitiveArrayKeys, DocumentDepthProperties depthProps, int maxStringLength, String enc) throws Exception;

	}

	private interface XmlToJsonStreamWriterFactory
	{
		XMLStreamWriter createWriter(final Object actualObject, final Class<?> actualClass, final Type genericType, final String enc, final OutputStream os, final boolean isCollection)
				throws Exception;
	}

	private static final JsonToXmlStreamReaderFactory MAPPED_CONVENTION_BASED_JSON_TO_XML_STREAM_READER_FACTORY = (is, readXsiType, namespaceMap, namespaceSeparator, elementsToAttributes, primitiveArrayKeys, depthProps, maxStringLength, enc) ->
	{
		// BEGIN CHANGE to JSONProvider (CXF 3.1.8)
		// reader = JSONUtils.createStreamReader(is, readXsiType, namespaceMap, namespaceSeparator, primitiveArrayKeys, depthProps, enc);
		if (readXsiType)
		{
			/*
			 * XSI namespace added to namespaceMap in setNamespaceMap()
			 */
			namespaceMap.putIfAbsent(JSONUtils.XSI_URI, JSONUtils.XSI_PREFIX);
		}
		// END CHANGE
		final Configuration conf = new Configuration(namespaceMap);
		if (namespaceSeparator != null)
		{
			conf.setJsonNamespaceSeparator(namespaceSeparator);
		}

		// BEGIN CHANGE
		if (elementsToAttributes != null)
		{
			conf.setElementsAsAttributes(new HashSet<>(elementsToAttributes));
		}
		// END CHANGE

		if (primitiveArrayKeys != null)
		{
			conf.setPrimitiveArrayKeys(new HashSet<>(primitiveArrayKeys));
		}

		// BEGIN CHANGE to JSONUtils#createStreamReader() (CXF 3.1.8)
		// XMLInputFactory factory = depthProps != null
		// ? new JettisonMappedReaderFactory(conf, depthProps)
		// : new MappedXMLInputFactory(conf);
		// depthProps already handled by createDepthReaderIfNeeded() called in createStreamReader() method
		final XMLInputFactory factory = maxStringLength > 0 ? new HardenedMappedXMLInputFactory(conf, /* depthProps, */maxStringLength) : new SetPropertyAllowingMappedXMLInputFactory(conf /*
																																														 * depthProps,
																																														 */);
		/*
		 * Mitigation of XML External Entity attacks: (More info: https://find-sec-bugs.github.io/bugs.htm)
		 */
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		// END CHANGE
		return new JSONUtils.JettisonReader(namespaceMap, factory.createXMLStreamReader(is, enc));
	};

	private static final JsonToXmlStreamReaderFactory BADGERFISH_CONVENTION_BASED_JSON_TO_XML_STREAM_READER_FACTORY = new JsonToXmlStreamReaderFactory()
	{

		@Override
		public XMLStreamReader createReader(final InputStream is, final boolean readXsiType, final ConcurrentMap<String, String> namespaceMap, final String namespaceSeparator,
				final List<String> elementsToAttributes, final List<String> primitiveArrayKeys, final DocumentDepthProperties depthProps, final int maxStringLength, final String enc) throws Exception
		{
			// BEGIN CHANGE to JSONProvider (CXF 3.1.8)
			if (depthProps != null)
			{
				throw UNSUPPORTED_DEPTH_PROPERTIES_WITH_BADGERFISH_EXCEPTION;
			}
			// END CHANGE
			return JSONUtils.createBadgerFishReader(is, enc);
		}

	};

	private List<String> elementsToAttributes;
	private Map<String, String> outAttributesMap;
	private Map<String, String> inAttributesMap;

	private final class BadgerfishConventionBasedXmlToJsonStreamWriterFactory implements XmlToJsonStreamWriterFactory
	{

		@Override
		public XMLStreamWriter createWriter(final Object actualObject, final Class<?> actualClass, final Type genericType, final String enc, final OutputStream os, final boolean isCollection)
				throws Exception
		{
			final boolean dropElementsInXmlStreamProp = getBooleanJsonProperty(DROP_ELEMENT_IN_XML_PROPERTY, dropElementsInXmlStream);
			final XMLStreamWriter writer = JSONUtils.createBadgerFishWriter(os, enc);
			return TransformUtils.createTransformWriterIfNeeded(writer, os, outElementsMap, dropElementsInXmlStreamProp ? outDropElements : null, outAppendMap, outAttributesMap, attributesToElements,
					null, StandardCharsets.UTF_8.name());
		}

	}

	/**
	 * Factory for XML-to-JSON writer based on "mapped" convention
	 */
	protected final class MappedConventionBasedXmlToJsonStreamWriterFactory implements XmlToJsonStreamWriterFactory
	{
		/**
		 * Default constructor
		 */
		protected MappedConventionBasedXmlToJsonStreamWriterFactory() {}

		/**
		 * Adapted from original CXF JSONProvider's method createWriter()
		 */
		@Override
		public XMLStreamWriter createWriter(final Object actualObject, final Class<?> actualClass, final Type genericType, final String enc, final OutputStream os, final boolean isCollection)
				throws Exception
		{
			final boolean dropElementsInXmlStreamProp = getBooleanJsonProperty(DROP_ELEMENT_IN_XML_PROPERTY, dropElementsInXmlStream);

			final boolean dropRootNeeded = getBooleanJsonProperty(DROP_ROOT_CONTEXT_PROPERTY, dropRootElement);
			final boolean dropRootInXmlNeeded = dropRootNeeded && dropElementsInXmlStreamProp;

			QName qname = actualClass == Document.class ? org.apache.cxf.helpers.DOMUtils.getElementQName(((Document) actualObject).getDocumentElement()) : getQName(actualClass, genericType,
					actualObject);
			if (qname != null && ignoreNamespaces && (isCollection || dropRootInXmlNeeded))
			{
				qname = new QName(qname.getLocalPart());
			}

			final Configuration config = JSONUtils.createConfiguration(namespaceMap, writeXsiType && !ignoreNamespaces, attributesToElements, typeConverter);
			if (namespaceSeparator != null)
			{
				config.setJsonNamespaceSeparator(namespaceSeparator);
			}
			if (!dropElementsInXmlStreamProp && JSONProvider.super.outDropElements != null)
			{
				config.setIgnoredElements(outDropElements);
			}
			if (!writeNullAsString)
			{
				config.setWriteNullAsString(false);
			}
			final boolean ignoreEmpty = getBooleanJsonProperty(IGNORE_EMPTY_JSON_ARRAY_VALUES_PROPERTY, ignoreEmptyArrayValues);
			if (ignoreEmpty)
			{
				config.setIgnoreEmptyArrayValues(true);
			}

			if (escapeForwardSlashesAlways)
			{
				config.setEscapeForwardSlashAlways(true);
			}

			final boolean dropRootInJsonStream = dropRootNeeded && !dropElementsInXmlStreamProp;
			if (dropRootInJsonStream)
			{
				config.setDropRootElement(true);
			}

			List<String> theArrayKeys = getArrayKeys();
			final boolean rootIsArray = isRootArray(theArrayKeys);

			if (ignoreNamespaces && rootIsArray && (theArrayKeys == null || dropRootInJsonStream))
			{
				if (theArrayKeys == null)
				{
					theArrayKeys = new LinkedList<>();
				}
				// else theArrayKeys != null -> dropRootInJsonStream == true considering the if condition above
				else
				{
					theArrayKeys = new LinkedList<>(theArrayKeys);
				}
				if (qname != null)
				{
					theArrayKeys.add(qname.getLocalPart());
				}
			}

			XMLStreamWriter writer = JSONUtils.createStreamWriter(os, qname, writeXsiType && !ignoreNamespaces, config, rootIsArray, theArrayKeys, isCollection || dropRootInXmlNeeded, enc);
			writer = JSONUtils.createIgnoreMixedContentWriterIfNeeded(writer, ignoreMixedContent);
			writer = JSONUtils.createIgnoreNsWriterIfNeeded(writer, ignoreNamespaces, !writeXsiType);
			// BEGIN CHANGE
			// return createTransformWriterIfNeeded(writer, os, dropElementsInXmlStreamProp);
			return TransformUtils.createTransformWriterIfNeeded(writer, os, outElementsMap, dropElementsInXmlStreamProp ? outDropElements : null, outAppendMap, outAttributesMap, attributesToElements,
					null, StandardCharsets.UTF_8.name());
			// END CHANGE
		}
	}

	/**
	 * JSON-to-XML stream reader factory following the "mapped" convention
	 */
	protected JsonToXmlStreamReaderFactory jsonToXmlStreamReaderFactory = MAPPED_CONVENTION_BASED_JSON_TO_XML_STREAM_READER_FACTORY;

	/**
	 * JSON-to-XML stream writer factory following the "mapped" convention
	 */
	protected XmlToJsonStreamWriterFactory xmlToJsonStreamWriterFactory = new MappedConventionBasedXmlToJsonStreamWriterFactory();

	/**
	 * Default constructor
	 */
	public JSONProvider() {}

	/**
	 * END CHANGE to {@link JSONProvider}
	 */

	@Override
	public void setAttributesToElements(final boolean value)
	{
		this.attributesToElements = value;
	}

	/*
	 * BEGIN CHANGE to transform input elements (JSON keys without '@') to attributes, i.e. the opposite of attributesToElements, and to transform the attributes themselves
	 */

	/**
	 * Set the Jettison elementsToAttributes property
	 * @param keys property value
	 */
	public void setElementsToAttributes(final List<String> keys)
	{
		this.elementsToAttributes = keys;
	}

	/**
	 * Set the Jettison outTransformAttributes property
	 * @param attributesMap property value
	 */
	public void setOutTransformAttributes(final Map<String, String> attributesMap)
	{
		this.outAttributesMap = attributesMap;
	}

	/**
	 * Set the Jettison inTransformAttributes property
	 * @param attributesMap property value
	 */
	public void setInTransformAttributes(final Map<String, String> attributesMap)
	{
		this.inAttributesMap = attributesMap;
	}

	/*
	 * END CHANGE
	 */

	/*
	 * BEGIN CHANGE to pre-instantiate the proper stream reader/writer factory depending on convention
	 */

	/**
	 * Set the Jettison convention
	 * @param value convention
	 */
	public void setConvention(final String value)
	{
		if (BADGER_FISH_CONVENTION.equals(value))
		{
			this.jsonToXmlStreamReaderFactory = BADGERFISH_CONVENTION_BASED_JSON_TO_XML_STREAM_READER_FACTORY;
			this.xmlToJsonStreamWriterFactory = new BadgerfishConventionBasedXmlToJsonStreamWriterFactory();
		}
		else if (MAPPED_CONVENTION.equals(value))
		{
			this.jsonToXmlStreamReaderFactory = MAPPED_CONVENTION_BASED_JSON_TO_XML_STREAM_READER_FACTORY;
			this.xmlToJsonStreamWriterFactory = new MappedConventionBasedXmlToJsonStreamWriterFactory();
		}
		else
		{
			throw new IllegalArgumentException("Unsupported convention '" + value + "'");
		}
	}

	/*
	 * END CHANGE
	 */

	/**
	 * Set the convert-types-to-strings property
	 * @param convert property value
	 */
	public void setConvertTypesToStrings(final boolean convert)
	{
		if (convert)
		{
			this.setTypeConverter(new SimpleConverter());
		}
	}

	/**
	 * Set the type converter
	 * @param converter converter
	 */
	public void setTypeConverter(final TypeConverter converter)
	{
		this.typeConverter = converter;
	}

	/**
	 * Set ignore-namespaces property
	 * @param ignoreNamespaces property value
	 */
	public void setIgnoreNamespaces(final boolean ignoreNamespaces)
	{
		this.ignoreNamespaces = ignoreNamespaces;
	}

	/**
	 * Set the message context
	 * @param mc context
	 */
	@Context
	public void setMessageContext(final MessageContext mc)
	{
		super.setContext(mc);
	}

	/**
	 * Set the Jettison drop-root-element property
	 * @param drop property value
	 */
	public void setDropRootElement(final boolean drop)
	{
		this.dropRootElement = drop;
	}

	/**
	 * Set the Jettison dropCollectionWrapperElement property
	 * @param drop property value
	 */
	public void setDropCollectionWrapperElement(final boolean drop)
	{
		this.dropCollectionWrapperElement = drop;
	}

	/**
	 * Set the Jettison ignoreMixedContent property
	 * @param ignore property value
	 */
	public void setIgnoreMixedContent(final boolean ignore)
	{
		this.ignoreMixedContent = ignore;
	}

	/**
	 * Set the Jettison supportUnwrapped property
	 * @param unwrap property value
	 */
	public void setSupportUnwrapped(final boolean unwrap)
	{
		this.unwrapped = unwrap;
	}

	/**
	 * Set the Jettison wrapperName property
	 * @param wName property value
	 */
	public void setWrapperName(final String wName)
	{
		wrapperName = wName;
	}

	/**
	 * Set the Jettison wrapperMap property
	 * @param map property value
	 */
	public void setWrapperMap(final Map<String, String> map)
	{
		wrapperMap = map;
	}

	/**
	 * Set the Jettison serializAsArray property
	 * @param asArray property value
	 */
	public void setSerializeAsArray(final boolean asArray)
	{
		this.serializeAsArray = asArray;
	}

	/**
	 * Set the Jettison arrayKeys property
	 * @param keys property value
	 */
	public void setArrayKeys(final List<String> keys)
	{
		this.arrayKeys = keys;
	}

	/*
	 * BEGIN CHANGE to validate namespace URI and prefixes in namespaceMap property, and to support xsi:type (i.e.g XMLSchema-instance namespace for inheritance) equivalent for JSON
	 */

	/**
	 * Set the XML-namespace-to-JSON-prefix mappings
	 * @param namespaceMap mappings
	 */
	public void setNamespaceMap(final Map<String, String> namespaceMap)
	{
		if (namespaceMap != null)
		{
			for (final Entry<String, String> nsToPrefix : namespaceMap.entrySet())
			{
				final String nsURI = nsToPrefix.getKey();
				if (!StandardURIChecker.getInstance().isValidURI(nsURI))
				{
					throw new IllegalArgumentException("Invalid namespace URI in one of namespaceMap keys: " + nsURI);
				}

				final String nsPrefix = nsToPrefix.getValue();
				/*
				 * Empty string is special value to say this is default namespace (no prefix)
				 */
				if (!nsPrefix.isEmpty() && !NameChecker.isValidNCName(nsPrefix))
				{
					throw new IllegalArgumentException("Invalid namespace prefix in one of namespaceMap values: " + nsPrefix);
				}
			}

			this.namespaceMap.putAll(namespaceMap);
			/*
			 * This should not be necessary as this is added in createReader() method if readxsiType is enabled
			 */
			// this.namespaceMap.putIfAbsent(JSONUtils.XSI_URI, JSONUtils.XSI_PREFIX);
		}
	}

	/*
	 * END CHANGE
	 */

	@Override
	public boolean isReadable(final Class<?> type, final Type genericType, final Annotation[] anns, final MediaType mt)
	{
		return super.isReadable(type, genericType, anns, mt) || Document.class.isAssignableFrom(type);
	}

	/*
	 * BEGIN CHANGE
	 */
	@Override
	public T readFrom(final Class<T> type, final Type genericType, final Annotation[] anns, final MediaType mt, final MultivaluedMap<String, String> headers, final InputStream is) throws IOException
	{

		if (isPayloadEmpty(headers))
		{
			if (AnnotationUtils.getAnnotation(anns, Nullable.class) != null)
			{
				return null;
			}

			reportEmptyContentLength();
		}

		XMLStreamReader reader = null;
		final String enc = HttpUtils.getEncoding(mt, StandardCharsets.UTF_8.name());
		Unmarshaller unmarshaller = null;
		try
		{
			final boolean isCollection;
			final XMLStreamReader xsr;
			final Class<?> theType;
			final Class<?> theGenericType;
			try (InputStream realStream = getInputStream(type, genericType, is))
			{
				if (Document.class.isAssignableFrom(type))
				{
					final W3CDOMStreamWriter writer = new W3CDOMStreamWriter();
					reader = createReader(/* type, */realStream, false, enc);
					copyReaderToWriter(reader, writer);
					return type.cast(writer.getDocument());
				}
				isCollection = InjectionUtils.isSupportedCollectionOrArray(type);
				theGenericType = isCollection ? InjectionUtils.getActualType(genericType) : type;
				theType = getActualType(theGenericType, genericType, anns);

				unmarshaller = createUnmarshaller(theType, genericType, isCollection);
				xsr = createReader(/* type, */realStream, isCollection, enc);
			}

			Object response;
			if (JAXBElement.class.isAssignableFrom(type) || !isCollection && (unmarshalAsJaxbElement || jaxbElementClassMap != null && jaxbElementClassMap.containsKey(theType.getName())))
			{
				response = unmarshaller.unmarshal(xsr, theType);
			}
			else
			{
				response = unmarshaller.unmarshal(xsr);
			}
			if (response instanceof JAXBElement && !JAXBElement.class.isAssignableFrom(type))
			{
				response = ((JAXBElement<?>) response).getValue();
			}
			if (isCollection)
			{
				response = ((CollectionWrapper) response).getCollectionOrArray(unmarshaller, theType, type, genericType, JAXBUtils.getAdapter(theGenericType, anns));
			}
			else
			{
				response = checkAdapter(response, type, anns, false);
			}
			return type.cast(response);

		}
		catch (final JAXBException e)
		{
			handleJAXBException(e, true);
		}
		catch (final XMLStreamException e)
		{
			if (e.getCause() instanceof JSONSequenceTooLargeException)
			{
				throw new WebApplicationException(413);
			}

			handleXMLStreamException(e, true);
		}
		catch (final WebApplicationException e)
		{
			throw e;
		}
		catch (final Exception e)
		{
			throw ExceptionUtils.toBadRequestException(e, null);
		}
		finally
		{
			try
			{
				StaxUtils.close(reader);
			}
			catch (final XMLStreamException e)
			{
				throw ExceptionUtils.toBadRequestException(e, null);
			}
			JAXBUtils.closeUnmarshaller(unmarshaller);
		}
		// unreachable
		return null;
	}

	/**
	 * Create XML reader
	 * @param is inputstream
	 * @param isCollection is the input a collection?
	 * @param enc encoding
	 * @return the reader
	 * @throws Exception error
	 */
	protected XMLStreamReader createReader(/* final Class<?> type, */final InputStream is, final boolean isCollection, final String enc) throws Exception
	{
		final XMLStreamReader reader = createReader(/* type, */is, enc);
		return isCollection ? new JAXBCollectionWrapperReader(reader) : reader;
	}

	/**
	 * Reason for changing original org.apache.cxf.jaxrs.provider.json.JSONProvider#createReader(Class,InputStream,String):
	 * <ol>
	 * <li>Prevent use of depthProperties with badgerfish convention (not supported). More info: <a href="http://cxf.547215.n5.nabble.com/No-JSON-depth-control-with-Badgerfish-td5776211.html">No JSON depth control with Badgerfish</a></li>
	 * <li>Support innerElementLevelThreshold (only innerElementCount is supported by CXF JSONProvider as shown by method JSONUtils$JettisonMappedReaderFactory#createNewJSONTokener() ) and max string
	 * length (similar to org.apache.cxf.stax.maxAttributeSize/maxTextLength for XML).</li>
	 * </ol>
	 * @param is input stream
	 * @param enc encoding
	 * @return XML reader
	 * @throws Exception error
	 */
	protected XMLStreamReader createReader(/* final Class<?> type, */final InputStream is, final String enc) throws Exception
	{
		XMLStreamReader reader = jsonToXmlStreamReaderFactory.createReader(is, readXsiType, namespaceMap, namespaceSeparator, elementsToAttributes, primitiveArrayKeys, getDepthProperties(),
				maxStringLength, enc);
		// BEGIN CHANGE
		// reader = createTransformReaderIfNeeded(reader, is);
		reader = TransformUtils.createTransformReaderIfNeeded(reader, is, inDropElements, inElementsMap, inAppendMap, inAttributesMap, true);
		// END CHANGE
		// BEGIN CHANGE to JSONProvider (same as JAXBElementProvider) (CXF
		// 3.1.8)
		/*
		 * This enforces innerElementLevelThreshold and innerElementCount (and total elementCount but we don't use it). Else only innerElementCount is supported.
		 */
		reader = createDepthReaderIfNeeded(reader, is);
		// END CHANGE

		return reader;
	}

	/**
	 * Wrap an inputstream in another
	 * @param cls JAXB class
	 * @param type generic type
	 * @param is original inputstream
	 * @return wrapped inputstream
	 * @throws Exception error
	 */
	protected InputStream getInputStream(final Class<T> cls, final Type type, final InputStream is) throws Exception
	{
		if (unwrapped)
		{
			final String rootName = getRootName(cls, type);
			final InputStream isBefore = new ByteArrayInputStream(rootName.getBytes());
			final String after = "}";
			final InputStream isAfter = new ByteArrayInputStream(after.getBytes());
			final InputStream[] streams = new InputStream[] { isBefore, is, isAfter };

			final Enumeration<InputStream> list = new Enumeration<>()
			{
				private int index;

				@Override
				public boolean hasMoreElements()
				{
					return index < streams.length;
				}

				@Override
				public InputStream nextElement()
				{
					return streams[index++];
				}

			};
			return new SequenceInputStream(list);
		}

		return is;

	}

	/**
	 * Get the root JSON property name corresponding to a JAXB object
	 * @param cls JAXB class
	 * @param type parameter type
	 * @return root name
	 * @throws Exception error
	 */
	protected String getRootName(final Class<T> cls, final Type type) throws Exception
	{
		String name = null;
		if (wrapperName != null)
		{
			name = wrapperName;
		}
		else if (wrapperMap != null)
		{
			name = wrapperMap.get(cls.getName());
		}
		if (name == null)
		{
			final QName qname = getQName(cls, type, null);
			if (qname != null)
			{
				name = qname.getLocalPart();
				final String prefix = qname.getPrefix();
				if (prefix.length() > 0)
				{
					name = prefix + "." + name;
				}
			}
		}

		if (name == null)
		{
			throw ExceptionUtils.toInternalServerErrorException(null, null);
		}

		return "{\"" + name + "\":";
	}

	@Override
	public boolean isWriteable(final Class<?> type, final Type genericType, final Annotation[] anns, final MediaType mt)
	{

		return super.isWriteable(type, genericType, anns, mt) || Document.class.isAssignableFrom(type);
	}

	/*
	 * BEGIN CHANGE to use xmlToJsonStreamWriterFactory
	 */
	@Override
	public void writeTo(final T obj, final Class<?> cls, final Type genericType, final Annotation[] anns, final MediaType m, final MultivaluedMap<String, Object> headers, final OutputStream os)
			throws IOException
	{
		if (os == null)
		{
			final StringBuilder sb = new StringBuilder();
			sb.append("Jettison needs initialized OutputStream");
			if (getContext() != null && getContext().getContent(XMLStreamWriter.class) == null)
			{
				sb.append("; if you need to customize Jettison output with the custom XMLStreamWriter" + " then extend JSONProvider or when possible configure it directly.");
			}
			throw new IOException(sb.toString());
		}
		XMLStreamWriter writer = null;
		try
		{

			final String enc = HttpUtils.getSetEncoding(m, headers, StandardCharsets.UTF_8.name());
			if (Document.class.isAssignableFrom(cls))
			{
				writer = xmlToJsonStreamWriterFactory.createWriter(obj, cls, genericType, enc, os, false);
				copyReaderToWriter(StaxUtils.createXMLStreamReader((Document) obj), writer);
				return;
			}
			if (InjectionUtils.isSupportedCollectionOrArray(cls))
			{
				marshalCollection(cls, obj, genericType, enc, os, /* m, */anns);
			}
			else
			{
				final Object actualObject = checkAdapter(obj, cls, anns, true);
				final Class<?> actualClass = obj != actualObject || cls.isInterface() ? actualObject.getClass() : cls;
				final Type actualGenericType;
				if (cls == genericType)
				{
					actualGenericType = actualClass;
				}
				else
				{
					actualGenericType = genericType;
				}

				marshal(actualObject, actualClass, actualGenericType, enc, os);
			}

		}
		catch (final JAXBException e)
		{
			handleJAXBException(e, false);
		}
		catch (final XMLStreamException e)
		{
			handleXMLStreamException(e, false);
		}
		catch (final Exception e)
		{
			throw ExceptionUtils.toInternalServerErrorException(e, null);
		}
		finally
		{
			StaxUtils.close(writer);
		}
	}

	/*
	 * END CHANGE
	 */

	/**
	 * Copy reader input to writer
	 * @param reader XML reader
	 * @param writer XML writer
	 * @throws Exception error
	 */
	protected static void copyReaderToWriter(final XMLStreamReader reader, final XMLStreamWriter writer) throws Exception
	{
		writer.writeStartDocument();
		StaxUtils.copy(reader, writer);
		writer.writeEndDocument();
	}

	/**
	 * Marshal a collection
	 * @param originalCls collection type
	 * @param collection collection
	 * @param genericType collection item type
	 * @param encoding encoding
	 * @param os output stream
	 * @param anns annotations
	 * @throws Exception error
	 */
	protected void marshalCollection(final Class<?> originalCls, final Object collection, final Type genericType, final String encoding, final OutputStream os, /* final MediaType m, */
			final Annotation[] anns) throws Exception
	{

		Class<?> actualClass = InjectionUtils.getActualType(genericType);
		actualClass = getActualType(actualClass, genericType, anns);

		final Collection<?> c = originalCls.isArray() ? Arrays.asList((Object[]) collection) : (Collection<?>) collection;

		final Iterator<?> it = c.iterator();

		final Object firstObj = it.hasNext() ? it.next() : null;

		final String startTag;
		final String endTag;
		if (!dropCollectionWrapperElement)
		{
			final QName qname;
			if (firstObj instanceof JAXBElement)
			{
				final JAXBElement<?> el = (JAXBElement<?>) firstObj;
				qname = el.getName();
				actualClass = el.getDeclaredType();
			}
			else
			{
				qname = getCollectionWrapperQName(actualClass, genericType, firstObj, false);
			}
			String prefix = "";
			if (!ignoreNamespaces)
			{
				prefix = namespaceMap.get(qname.getNamespaceURI());
				if (prefix != null)
				{
					if (prefix.length() > 0)
					{
						prefix += ".";
					}
				}
				else if (qname.getNamespaceURI().length() > 0)
				{
					prefix = "ns1.";
				}
			}
			prefix = (prefix == null) ? "" : prefix;
			startTag = "{\"" + prefix + qname.getLocalPart() + "\":[";
			endTag = "]}";
		}
		else if (serializeAsArray)
		{
			startTag = "[";
			endTag = "]";
		}
		else
		{
			startTag = "{";
			endTag = "}";
		}

		os.write(startTag.getBytes());
		if (firstObj != null)
		{
			final XmlJavaTypeAdapter adapter = JAXBUtils.getAdapter(firstObj.getClass(), anns);
			marshalCollectionMember(JAXBUtils.useAdapter(firstObj, adapter, true), actualClass, genericType, encoding, os);
			while (it.hasNext())
			{
				os.write(",".getBytes());
				marshalCollectionMember(JAXBUtils.useAdapter(it.next(), adapter, true), actualClass, genericType, encoding, os);
			}
		}
		os.write(endTag.getBytes());
	}

	/**
	 * Marshal a collection member
	 * @param cls collection type
	 * @param obj collection member
	 * @param genericType collection item type
	 * @param enc encoding
	 * @param os output stream
	 * @throws Exception error
	 */
	protected void marshalCollectionMember(final Object obj, final Class<?> cls, final Type genericType, final String enc, final OutputStream os) throws Exception
	{
		final Object actualObject;
		if (obj instanceof JAXBElement)
		{
			actualObject = ((JAXBElement<?>) obj).getValue();
		}
		else
		{
			actualObject = convertToJaxbElementIfNeeded(obj, cls, genericType);
		}

		final Class<?> actualClass;
		if (actualObject instanceof JAXBElement && cls != JAXBElement.class)
		{
			actualClass = JAXBElement.class;
		}
		else
		{
			actualClass = cls;
		}

		final Marshaller ms = createMarshaller(actualObject, actualClass, genericType, enc);
		marshal(ms, actualObject, actualClass, genericType, enc, os, true);

	}

	/*
	 * BEGIN CHANGE - refactored original marshal() method for using xmlToJsonStreamWriterFactory, try-with-resource style and adding sub-method marshalRaw
	 */
	private void marshalRaw(final Marshaller ms, final Object actualObject, final Class<?> actualClass, final Type genericType, final String enc, final OutputStream os, final boolean isCollection)
			throws Exception
	{
		final XMLStreamWriter writer = xmlToJsonStreamWriterFactory.createWriter(actualObject, actualClass, genericType, enc, os, isCollection);
		if (namespaceMap.size() > 1 || namespaceMap.size() == 1 && !namespaceMap.containsKey(JSONUtils.XSI_URI))
		{
			setNamespaceMapper(ms, namespaceMap);
		}
		ms.marshal(actualObject, writer);
		writer.close();
	}

	/**
	 * Marshal a JAXB element
	 * @param actualObject JAXB object
	 * @param actualClass JAXB class
	 * @param genericType collection item type if it is a collection
	 * @param ms marshaller
	 * @param enc encoding
	 * @param os output stream
	 * @param isCollection true iff the marshalled object is a collection
	 * @throws Exception error
	 */
	protected void marshal(final Marshaller ms, final Object actualObject, final Class<?> actualClass, final Type genericType, final String enc, final OutputStream os, final boolean isCollection)
			throws Exception
	{
		final MessageContext mc = getContext();
		if (mc != null && PropertyUtils.isTrue(mc.get(Marshaller.JAXB_FORMATTED_OUTPUT)))
		{
			final StringIndenter formatter;
			try (CachedOutputStream actualOs = new CachedOutputStream())
			{
				marshalRaw(ms, actualObject, actualClass, genericType, enc, actualOs, isCollection);
				formatter = new StringIndenter(IOUtils.newStringFromBytes(actualOs.getBytes()));
			}
			try (Writer outWriter = new OutputStreamWriter(os, enc))
			{
				IOUtils.copy(new StringReader(formatter.result()), outWriter, 2048);
				// outWriter.close() automatically called when exiting the try-with-resources block
			}
		}
		else
		{
			marshalRaw(ms, actualObject, actualClass, genericType, enc, os, isCollection);
		}
	}

	/*
	 * END CHANGE
	 */

	/*
	 * BEGIN CHANGE createWriter() method removed here from original JSONProvider class, moved to new XmlToJsonStreamWriterFactory#createWriter() so to speak END CHANGE
	 */

	/**
	 * Get the arrayKeys property from the message context
	 * @return property value
	 */
	protected List<String> getArrayKeys()
	{
		final MessageContext mc = getContext();
		if (mc != null)
		{
			final Object prop = mc.get(ARRAY_KEYS_PROPERTY);
			if (prop instanceof List)
			{
				return CastUtils.cast((List<?>) prop);
			}
		}
		return arrayKeys;
	}

	/**
	 * Get the "json.root.is.array" property from the message context
	 * @param theArrayKeys elements mapped to JSON arrays
	 * @return property value
	 */
	protected boolean isRootArray(final List<String> theArrayKeys)
	{
		return theArrayKeys != null || getBooleanJsonProperty(ROOT_IS_ARRAY_PROPERTY, serializeAsArray);
	}

	/**
	 * Get a boolean property from the message context
	 * @param name property name
	 * @param defaultValue default property value
	 * @return the final property value
	 */
	protected boolean getBooleanJsonProperty(final String name, final boolean defaultValue)
	{
		final MessageContext mc = getContext();
		if (mc != null)
		{
			final Object prop = mc.get(name);
			if (prop != null)
			{
				return PropertyUtils.isTrue(prop);
			}
		}
		return defaultValue;
	}

	/**
	 * Marshall a JAXB object to JSON
	 * @param actualObject JAXB object
	 * @param actualClass JAXB class
	 * @param genericType type parameter if the JAXB class is generic
	 * @param enc encoding
	 * @param os output stream
	 * @throws Exception error
	 */
	protected void marshal(final Object actualObject, final Class<?> actualClass, final Type genericType, final String enc, final OutputStream os) throws Exception
	{

		final Object newActualObject = convertToJaxbElementIfNeeded(actualObject, actualClass, genericType);
		final Class<?> newActualClass;
		if (newActualObject instanceof JAXBElement && actualClass != JAXBElement.class)
		{
			newActualClass = JAXBElement.class;
		}
		else
		{
			newActualClass = actualClass;
		}

		final Marshaller ms = createMarshaller(newActualObject, newActualClass, genericType, enc);
		marshal(ms, newActualObject, newActualClass, genericType, enc, os, false);
	}

	private QName getQName(final Class<?> cls, final Type type, final Object object) throws Exception
	{
		final QName qname = getJaxbQName(cls, type, object, false);
		if (qname != null)
		{
			final String prefix = getPrefix(qname.getNamespaceURI());
			return new QName(qname.getNamespaceURI(), qname.getLocalPart(), prefix);
		}
		return null;
	}

	private String getPrefix(final String namespace)
	{
		final String prefix = namespaceMap.get(namespace);
		return prefix == null ? "" : prefix;
	}

	/**
	 * Write the XSI type
	 * @param writeXsiType true to enable
	 */
	public void setWriteXsiType(final boolean writeXsiType)
	{
		this.writeXsiType = writeXsiType;
	}

	/**
	 * Read the XSI type
	 * @param readXsiType true to enable
	 */
	public void setReadXsiType(final boolean readXsiType)
	{
		this.readXsiType = readXsiType;
	}

	/**
	 * Set the Jettison primitiveArrayKeys property
	 * @param primitiveArrayKeys property value
	 */
	public void setPrimitiveArrayKeys(final List<String> primitiveArrayKeys)
	{
		this.primitiveArrayKeys = primitiveArrayKeys;
	}

	/**
	 * Enable dropping elements in the XML stream
	 * @param drop true to enable
	 */
	public void setDropElementsInXmlStream(final boolean drop)
	{
		this.dropElementsInXmlStream = drop;
	}

	/**
	 * Set the Jettison writeNullAsString property
	 * @param writeNullAsString property value
	 */
	public void setWriteNullAsString(final boolean writeNullAsString)
	{
		this.writeNullAsString = writeNullAsString;
	}

	/**
	 * Set the Jettison ignoreEmptyArrayElements property
	 * @param ignoreEmptyArrayElements property value
	 */
	public void setIgnoreEmptyArrayValues(final boolean ignoreEmptyArrayElements)
	{
		this.ignoreEmptyArrayValues = ignoreEmptyArrayElements;
	}

	@Override
	protected DocumentDepthProperties getDepthProperties()
	{
		final DocumentDepthProperties depthProperties = super.getDepthProperties();
		if (depthProperties != null)
		{
			return depthProperties;
		}
		if (getContext() != null)
		{
			final String totalElementCountStr = (String) getContext().getContextualProperty(DocumentDepthProperties.TOTAL_ELEMENT_COUNT);
			final String innerElementCountStr = (String) getContext().getContextualProperty(DocumentDepthProperties.INNER_ELEMENT_COUNT);
			final String elementLevelStr = (String) getContext().getContextualProperty(DocumentDepthProperties.INNER_ELEMENT_LEVEL);
			if (totalElementCountStr != null || innerElementCountStr != null || elementLevelStr != null)
			{
				try
				{
					final int totalElementCount = totalElementCountStr != null ? Integer.parseInt(totalElementCountStr) : -1;
					final int elementLevel = elementLevelStr != null ? Integer.parseInt(elementLevelStr) : -1;
					final int innerElementCount = innerElementCountStr != null ? Integer.parseInt(innerElementCountStr) : -1;
					return new DocumentDepthProperties(totalElementCount, elementLevel, innerElementCount);
				}
				catch (final Exception ex)
				{
					throw ExceptionUtils.toInternalServerErrorException(ex, null);
				}
			}
		}
		return null;
	}

	/**
	 * Set the escapeForwardSlashesAlways property
	 * @param escape true to enable
	 */
	public void setEscapeForwardSlashesAlways(final boolean escape)
	{
		this.escapeForwardSlashesAlways = escape;
	}

	/**
	 * Set the namespace separator
	 * @param namespaceSeparator separator
	 */
	public void setNamespaceSeparator(final String namespaceSeparator)
	{
		this.namespaceSeparator = namespaceSeparator;
	}

	/*
	 * END OF JSONProvider's changes. BEGINNING of new features.
	 */

	private static final UnsupportedOperationException UNSUPPORTED_DEPTH_PROPERTIES_WITH_BADGERFISH_EXCEPTION = new UnsupportedOperationException(
			"'depthProperties' property not supported if convention = 'badgerfish'");

	private int maxStringLength = -1;

	/**
	 * Set maximum string length in JSON payload;
	 * 
	 * @param value
	 *            maximum, ignored iff negative (or zero)
	 */
	public void setMaxStringLength(final int value)
	{
		this.maxStringLength = value;
	}

	/**
	 * Reason for override: super.createDepthReaderIfNeeded() fails if getDepthProperties() != null because the reader (XMLStreamReader) is not compatible with internal call to
	 * StaxUtils.configureReader(reader, message): it is not expected type org.codehaus.stax2.XMLStreamReader2 (therefore ClassCastException);
	 */
	@Override
	protected XMLStreamReader createDepthReaderIfNeeded(final XMLStreamReader reader, final InputStream is)
	{
		final DocumentDepthProperties props = getDepthProperties();
		if (props != null && props.isEffective())
		{
			return new DepthRestrictingStreamReader(TransformUtils.createNewReaderIfNeeded(reader, is), props);
		}
		// BEGIN CHANGE to AbstractJAXBProvider (CXF 3.1.8)
		/*
		 * WARNING: configureReaderRestrictions() fails if getDepthProperties() != null because the reader (XMLStreamReader) is not compatible with internal StaxUtils.configureReader(reader, message),
		 * it is not expected type org.codehaus.stax2.XMLStreamReader2 (therefore ClassCastException);
		 */
		// else if (reader != null) {
		// reader = configureReaderRestrictions(reader);
		// }
		// END CHANGE
		return reader;
	}

}
