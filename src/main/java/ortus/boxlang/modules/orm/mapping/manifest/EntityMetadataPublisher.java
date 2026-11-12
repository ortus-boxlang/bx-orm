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
package ortus.boxlang.modules.orm.mapping.manifest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ThreadBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Publishes the normalized entity metadata of a trust-mode manifest onto the live entity classes (BOX-178).
 * <p>
 * Discovery normalizes each entity's annotations in place: it adds {@code entityName} and {@code table}, defaults
 * {@code fieldtype} and {@code ormType}, normalizes association types, and so on. Those structs are the class's own, so
 * after an {@code off} or {@code auto} boot, {@code getClassMetadata()} and {@code getMetadata()} return the normalized
 * values, and code relies on them (ColdBox's {@code ObjectPopulator} reads {@code entityName} to resolve relationships).
 * <p>
 * A {@code trust} boot skips discovery, so without this the classes would keep their raw annotations. The manifest holds the
 * same normalized metadata discovery produced (it is written from it), so this loads each entity class and writes the
 * manifest's entity and property annotations, recursively through {@code extends}, onto the class metadata. The application
 * then sees the same metadata in every manifest mode.
 */
public final class EntityMetadataPublisher {

	private static final BoxLangLogger	logger						= BoxRuntime.getInstance().getLoggingService().getLogger( "orm" );

	/** Above this many entities the classes load in parallel (with {@code enableThreadedMapping}), as in discovery. */
	private static final int			MAX_SYNCHRONOUS_ENTITIES	= 20;

	private EntityMetadataPublisher() {
	}

	/**
	 * Load every entity class of the manifest and publish its normalized metadata onto the class.
	 *
	 * @param context  The context of the application booting, so the entity classes resolve through its mappings.
	 * @param manifest The trusted manifest.
	 * @param config   The ORM configuration, for {@code ignoreParseErrors}.
	 *
	 * @return The number of entity classes published.
	 */
	public static int publish( IBoxContext context, OrmManifest manifest, ORMConfig config ) {
		long						start		= System.currentTimeMillis();
		List<OrmManifest.Entity>	entities	= manifest.getEntities();
		// Loading (and, on a cold class cache, compiling) the classes is the cost, so it runs in parallel like discovery
		// does. The merge stays sequential: a subclass's merge also writes its parent class's metadata.
		boolean						parallel	= config.enableThreadedMapping && entities.size() > MAX_SYNCHRONOUS_ENTITIES;
		List<Object>				loaded		= parallel
		    ? entities.parallelStream().map( entity -> ThreadBoxContext.runInContext( context, ctx -> loadMeta( ctx, entity ) ) ).toList()
		    : entities.stream().map( entity -> loadMeta( context, entity ) ).toList();

		int							published	= 0;
		for ( int i = 0; i < entities.size(); i++ ) {
			OrmManifest.Entity	entity	= entities.get( i );
			Object				live	= loaded.get( i );
			if ( live instanceof IStruct liveMeta ) {
				merge( liveMeta, entity.metadata() );
				published++;
			} else if ( live instanceof Throwable error ) {
				if ( !config.ignoreParseErrors ) {
					throw new BoxRuntimeException( "ORM manifest [trust] mode: failed to load entity class [" + entity.classFQN()
					    + "] to publish its metadata. Regenerate the manifest if the class moved.", error );
				}
				logger.warn( "ORM manifest [trust] mode: failed to load entity class [{}] to publish its metadata: {}", entity.classFQN(),
				    error.getMessage() );
			}
		}
		if ( logger.isDebugEnabled() ) {
			logger.debug( "ORM startup metric - trust metadata publish: {}ms ({} classes, {})", System.currentTimeMillis() - start, published,
			    parallel ? "parallel" : "sequential" );
		}
		return published;
	}

	/**
	 * Load an entity class and return its live class metadata, or the error that prevented it (so a parallel load never
	 * loses which entity failed).
	 */
	private static Object loadMeta( IBoxContext context, OrmManifest.Entity entity ) {
		try {
			DynamicObject classObj = BoxRuntime.getInstance().getClassLocator()
			    .load( context, entity.classFQN(), ClassLocator.BX_PREFIX, true, context.getCurrentImports() );
			return classObj.invokeStatic( context, "getMetaStatic" );
		} catch ( Exception e ) {
			return e;
		}
	}

	/**
	 * Publish normalized class metadata onto live class metadata: the class annotations, each property's annotations
	 * (matched by name), and the same for the parent class.
	 *
	 * @param live       The live class metadata (the class's own struct).
	 * @param normalized The normalized metadata from the manifest.
	 */
	static void merge( IStruct live, IStruct normalized ) {
		if ( live == null || normalized == null ) {
			return;
		}
		mergeAnnotations( live, normalized );

		Map<String, IStruct> liveProperties = byName( live.get( Key.properties ) );
		for ( IStruct property : structs( normalized.get( Key.properties ) ) ) {
			IStruct liveProperty = liveProperties.get( nameOf( property ) );
			if ( liveProperty != null ) {
				mergeAnnotations( liveProperty, property );
			}
		}

		if ( live.get( Key._EXTENDS ) instanceof IStruct liveParent && normalized.get( Key._EXTENDS ) instanceof IStruct parent ) {
			merge( liveParent, parent );
		}
	}

	/**
	 * Write each normalized annotation the live annotations lack or hold with another value.
	 */
	private static void mergeAnnotations( IStruct live, IStruct normalized ) {
		if ( ! ( normalized.get( Key.annotations ) instanceof IStruct annotations ) || annotations.isEmpty() ) {
			return;
		}
		IStruct liveAnnotations = live.get( Key.annotations ) instanceof IStruct existing ? existing : null;
		if ( liveAnnotations == null ) {
			return;
		}
		annotations.forEach( ( key, value ) -> {
			if ( !liveAnnotations.containsKey( key ) || !Objects.equals( liveAnnotations.get( key ), value ) ) {
				liveAnnotations.put( key, value );
			}
		} );
	}

	/**
	 * Index a properties array by property name (case-insensitive).
	 */
	private static Map<String, IStruct> byName( Object properties ) {
		Map<String, IStruct> index = new HashMap<>();
		for ( IStruct property : structs( properties ) ) {
			index.putIfAbsent( nameOf( property ), property );
		}
		return index;
	}

	private static List<IStruct> structs( Object value ) {
		if ( ! ( value instanceof List<?> list ) ) {
			return List.of();
		}
		return list.stream().filter( IStruct.class::isInstance ).map( IStruct.class::cast ).toList();
	}

	private static String nameOf( IStruct property ) {
		Object name = property.get( Key._NAME );
		return name == null ? "" : String.valueOf( name ).toLowerCase();
	}
}
