/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
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
package ortus.boxlang.modules.orm.hibernate.cache;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Reads an EHCache <code>ehcache.xml</code> (version 2 or 3) into {@link CacheRegionSettings}, so the files Adobe ColdFusion
 * and Lucee applications used keep working with bx-orm's BoxCache-backed second-level cache. EHCache itself is not used.
 * <p>
 * EHCache 2 (<code>&lt;ehcache&gt;</code>): <code>&lt;defaultCache&gt;</code> becomes the shared properties and each
 * <code>&lt;cache name="..."&gt;</code> a region. Attributes map to BoxCache properties:
 * <ul>
 * <li><code>maxEntriesLocalHeap</code> or <code>maxElementsInMemory</code> to <code>maxObjects</code></li>
 * <li><code>timeToLiveSeconds</code> to <code>defaultTimeout</code></li>
 * <li><code>timeToIdleSeconds</code> to <code>defaultLastAccessTimeout</code></li>
 * <li><code>eternal="true"</code> to both timeouts <code>0</code> (never expire)</li>
 * <li><code>memoryStoreEvictionPolicy</code> to <code>evictionPolicy</code> (<code>CLOCK</code> becomes <code>LRU</code>)</li>
 * </ul>
 * EHCache 3 (<code>&lt;config&gt;</code>): each <code>&lt;cache alias="..."&gt;</code> is a region, with
 * <code>&lt;cache-template&gt;</code> values applied through <code>uses-template</code>. <code>&lt;heap unit="entries"&gt;</code>
 * maps to <code>maxObjects</code>, <code>&lt;expiry&gt;&lt;ttl&gt;</code> to <code>defaultTimeout</code> and
 * <code>&lt;tti&gt;</code> to <code>defaultLastAccessTimeout</code> (converted to seconds), and <code>&lt;none/&gt;</code> to
 * no expiry. Anything else in the file (disk stores, listeners, clustering) is ignored.
 */
public final class EhcacheXmlReader {

	static final Key	MAX_OBJECTS			= Key.of( "maxObjects" );
	static final Key	DEFAULT_TIMEOUT		= Key.of( "defaultTimeout" );
	static final Key	LAST_ACCESS_TIMEOUT	= Key.of( "defaultLastAccessTimeout" );
	static final Key	EVICTION_POLICY		= Key.of( "evictionPolicy" );

	private EhcacheXmlReader() {
	}

	/**
	 * Read an <code>ehcache.xml</code> file.
	 *
	 * @param file The file.
	 *
	 * @return The region settings it describes.
	 */
	public static CacheRegionSettings read( Path file ) {
		try ( InputStream in = Files.newInputStream( file ) ) {
			return read( parse( in ) );
		} catch ( ORMException e ) {
			throw e;
		} catch ( Exception e ) {
			throw new ORMException( ORMErrorType.CONFIG, "The cacheConfig file [" + file + "] could not be read as an ehcache.xml: "
			    + e.getMessage(), "Check that the file is a valid EHCache 2 or 3 configuration.", null, e );
		}
	}

	/**
	 * Read a parsed <code>ehcache.xml</code> document.
	 *
	 * @param doc The document.
	 *
	 * @return The region settings it describes.
	 */
	static CacheRegionSettings read( Document doc ) {
		Element root = doc.getDocumentElement();
		return switch ( localName( root ) ) {
			case "ehcache" -> readVersion2( root );
			case "config" -> readVersion3( root );
			default -> throw new ORMException( ORMErrorType.CONFIG,
			    "The cacheConfig XML file has a <" + localName( root ) + "> root; expected an EHCache 2 <ehcache> or EHCache 3 <config>.",
			    "Point cacheConfig at an ehcache.xml, a .json file, or set it to a struct." );
		};
	}

	/**
	 * Read an EHCache 2 configuration.
	 *
	 * @param root The <code>&lt;ehcache&gt;</code> element.
	 *
	 * @return The settings.
	 */
	private static CacheRegionSettings readVersion2( Element root ) {
		IStruct					shared	= new Struct();
		Map<String, IStruct>	regions	= new LinkedHashMap<>();
		for ( Element child : children( root ) ) {
			switch ( localName( child ) ) {
				case "defaultCache" -> shared.putAll( version2Properties( child ) );
				case "cache" -> {
					String name = child.getAttribute( "name" );
					if ( !name.isBlank() ) {
						regions.put( name.trim(), version2Properties( child ) );
					}
				}
				default -> {
					// disk stores, listeners and the like have no BoxCache counterpart
				}
			}
		}
		return new CacheRegionSettings( shared, regions );
	}

	/**
	 * The BoxCache properties of an EHCache 2 <code>&lt;cache&gt;</code> or <code>&lt;defaultCache&gt;</code>.
	 *
	 * @param cache The element.
	 *
	 * @return The properties it sets.
	 */
	static IStruct version2Properties( Element cache ) {
		IStruct	properties	= new Struct();
		String	max			= firstAttribute( cache, "maxEntriesLocalHeap", "maxElementsInMemory" );
		if ( max != null ) {
			properties.put( MAX_OBJECTS, Integer.parseInt( max ) );
		}
		String ttl = firstAttribute( cache, "timeToLiveSeconds" );
		if ( ttl != null ) {
			properties.put( DEFAULT_TIMEOUT, Long.parseLong( ttl ) );
		}
		String tti = firstAttribute( cache, "timeToIdleSeconds" );
		if ( tti != null ) {
			properties.put( LAST_ACCESS_TIMEOUT, Long.parseLong( tti ) );
		}
		if ( "true".equalsIgnoreCase( cache.getAttribute( "eternal" ).trim() ) ) {
			properties.put( DEFAULT_TIMEOUT, 0L );
			properties.put( LAST_ACCESS_TIMEOUT, 0L );
		}
		String policy = firstAttribute( cache, "memoryStoreEvictionPolicy" );
		if ( policy != null ) {
			String upper = policy.toUpperCase( Locale.ROOT );
			properties.put( EVICTION_POLICY, "CLOCK".equals( upper ) ? "LRU" : upper );
		}
		return properties;
	}

	/**
	 * Read an EHCache 3 configuration.
	 *
	 * @param root The <code>&lt;config&gt;</code> element.
	 *
	 * @return The settings.
	 */
	private static CacheRegionSettings readVersion3( Element root ) {
		Map<String, IStruct> templates = new HashMap<>();
		for ( Element child : children( root ) ) {
			if ( "cache-template".equals( localName( child ) ) && !child.getAttribute( "name" ).isBlank() ) {
				templates.put( child.getAttribute( "name" ).trim(), version3Properties( child ) );
			}
		}
		Map<String, IStruct> regions = new LinkedHashMap<>();
		for ( Element child : children( root ) ) {
			if ( !"cache".equals( localName( child ) ) || child.getAttribute( "alias" ).isBlank() ) {
				continue;
			}
			IStruct	properties	= new Struct();
			IStruct	template	= templates.get( child.getAttribute( "uses-template" ).trim() );
			if ( template != null ) {
				properties.putAll( template );
			}
			properties.putAll( version3Properties( child ) );
			regions.put( child.getAttribute( "alias" ).trim(), properties );
		}
		return new CacheRegionSettings( new Struct(), regions );
	}

	/**
	 * The BoxCache properties of an EHCache 3 <code>&lt;cache&gt;</code> or <code>&lt;cache-template&gt;</code>.
	 *
	 * @param cache The element.
	 *
	 * @return The properties it sets.
	 */
	static IStruct version3Properties( Element cache ) {
		IStruct properties = new Struct();
		for ( Element child : children( cache ) ) {
			switch ( localName( child ) ) {
				case "heap" -> putHeap( properties, child );
				case "resources" -> {
					for ( Element resource : children( child ) ) {
						if ( "heap".equals( localName( resource ) ) ) {
							putHeap( properties, resource );
						}
					}
				}
				case "expiry" -> {
					for ( Element expiry : children( child ) ) {
						switch ( localName( expiry ) ) {
							case "ttl" -> properties.put( DEFAULT_TIMEOUT, toSeconds( expiry ) );
							case "tti" -> properties.put( LAST_ACCESS_TIMEOUT, toSeconds( expiry ) );
							case "none" -> {
								properties.put( DEFAULT_TIMEOUT, 0L );
								properties.put( LAST_ACCESS_TIMEOUT, 0L );
							}
							default -> {
								// a custom expiry class has no BoxCache counterpart
							}
						}
					}
				}
				default -> {
					// key/value types, listeners, loaders and the like have no BoxCache counterpart
				}
			}
		}
		return properties;
	}

	/**
	 * Map an EHCache 3 <code>&lt;heap&gt;</code> counted in entries (the default unit) to <code>maxObjects</code>. A heap sized
	 * in bytes has no BoxCache counterpart and is skipped.
	 *
	 * @param properties The properties to fill.
	 * @param heap       The <code>&lt;heap&gt;</code> element.
	 */
	private static void putHeap( IStruct properties, Element heap ) {
		String unit = heap.getAttribute( "unit" ).trim();
		if ( unit.isEmpty() || "entries".equalsIgnoreCase( unit ) ) {
			properties.put( MAX_OBJECTS, Integer.parseInt( heap.getTextContent().trim() ) );
		}
	}

	/**
	 * Convert an EHCache 3 duration (<code>&lt;ttl unit="minutes"&gt;10&lt;/ttl&gt;</code>) to seconds. The default unit is
	 * seconds; durations under a second round up to one second.
	 *
	 * @param duration The duration element.
	 *
	 * @return The duration in seconds.
	 */
	static long toSeconds( Element duration ) {
		long	value	= Long.parseLong( duration.getTextContent().trim() );
		String	unit	= duration.getAttribute( "unit" ).trim().toLowerCase( Locale.ROOT );
		return switch ( unit ) {
			case "", "seconds" -> value;
			case "minutes" -> value * 60;
			case "hours" -> value * 3600;
			case "days" -> value * 86400;
			case "millis" -> Math.max( 1, value / 1000 );
			case "micros" -> Math.max( 1, value / 1_000_000 );
			case "nanos" -> Math.max( 1, value / 1_000_000_000 );
			default -> throw new ORMException( ORMErrorType.CONFIG, "Unknown EHCache duration unit [" + unit + "] in the cacheConfig file.",
			    "Use seconds, minutes, hours, days, millis, micros or nanos." );
		};
	}

	/**
	 * Parse XML with DTDs and external entities turned off.
	 *
	 * @param in The XML.
	 *
	 * @return The document.
	 *
	 * @throws Exception When the XML cannot be parsed.
	 */
	static Document parse( InputStream in ) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware( true );
		factory.setFeature( "http://apache.org/xml/features/disallow-doctype-decl", true );
		factory.setFeature( "http://xml.org/sax/features/external-general-entities", false );
		factory.setFeature( "http://xml.org/sax/features/external-parameter-entities", false );
		factory.setAttribute( XMLConstants.ACCESS_EXTERNAL_DTD, "" );
		factory.setAttribute( XMLConstants.ACCESS_EXTERNAL_SCHEMA, "" );
		factory.setXIncludeAware( false );
		factory.setExpandEntityReferences( false );
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse( in );
	}

	/**
	 * The child elements of an element.
	 *
	 * @param parent The element.
	 *
	 * @return Its element children, in document order.
	 */
	private static java.util.List<Element> children( Element parent ) {
		java.util.List<Element>	elements	= new java.util.ArrayList<>();
		NodeList				nodes		= parent.getChildNodes();
		for ( int i = 0; i < nodes.getLength(); i++ ) {
			if ( nodes.item( i ).getNodeType() == Node.ELEMENT_NODE ) {
				elements.add( ( Element ) nodes.item( i ) );
			}
		}
		return elements;
	}

	/**
	 * An element's name without its namespace prefix.
	 *
	 * @param element The element.
	 *
	 * @return The local name.
	 */
	private static String localName( Element element ) {
		return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
	}

	/**
	 * The first of several attributes that is present and not blank.
	 *
	 * @param element The element.
	 * @param names   Attribute names, in order of preference.
	 *
	 * @return The trimmed value, or null when none is set.
	 */
	private static String firstAttribute( Element element, String... names ) {
		for ( String name : names ) {
			String value = element.getAttribute( name );
			if ( value != null && !value.isBlank() ) {
				return value.trim();
			}
		}
		return null;
	}
}
