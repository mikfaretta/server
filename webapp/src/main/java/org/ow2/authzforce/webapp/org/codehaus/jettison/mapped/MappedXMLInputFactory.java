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

import java.util.Map;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.jettison.AbstractXMLInputFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONTokener;

/**
 * Factory for XML input to be converted to JSON according to "mapped" convention
 */
public class MappedXMLInputFactory extends AbstractXMLInputFactory
{

	private final MappedNamespaceConvention convention;

	/**
	 * Constructor
	 * @param nstojns XML-namespace-to-JSON-prefix mappings
	 */
	public MappedXMLInputFactory(final Map<String, String> nstojns)
	{
		this(new Configuration(nstojns));
	}

	/**
	 * Constructor
	 * @param config configuration
	 */
	public MappedXMLInputFactory(final Configuration config)
	{
		this.convention = new MappedNamespaceConvention(config);
	}

	@Override
	public XMLStreamReader createXMLStreamReader(final JSONTokener tokener) throws XMLStreamException
	{
		try
		{
			final JSONObject root = createJSONObject(tokener);
			return new MappedXMLStreamReader(root, convention);
		}
		catch (final JSONException e)
		{
			final int column = e.getColumn();
			if (column == -1)
			{
				throw new XMLStreamException(e);
			}
			else
			{
				throw new XMLStreamException(e.getMessage(), new ErrorLocation(e.getLine(), e.getColumn()), e);
			}
		}
	}

	/**
	 * Create JSON object from tokener
	 * @param tokener JSON tokener
	 * @return JSON object
	 * @throws JSONException error
	 */
	protected JSONObject createJSONObject(final JSONTokener tokener) throws JSONException
	{
		return new JSONObject(tokener);
	}

	private static class ErrorLocation implements Location
	{

		private final int line;
		private final int column;

		public ErrorLocation(final int line, final int column)
		{
			this.line = line;
			this.column = column;
		}

		@Override
		public int getCharacterOffset()
		{
			return 0;
		}

		@Override
		public int getColumnNumber()
		{
			return column;
		}

		@Override
		public int getLineNumber()
		{
			return line;
		}

		@Override
		public String getPublicId()
		{
			return null;
		}

		@Override
		public String getSystemId()
		{
			return null;
		}

	}
}
