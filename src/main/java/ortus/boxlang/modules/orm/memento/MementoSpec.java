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
package ortus.boxlang.modules.orm.memento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.modules.orm.errors.ORMErrorType;
import ortus.boxlang.modules.orm.errors.ORMException;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * What one struct should contain: the caller's options ({@code includes}, {@code excludes}, {@code mappers},
 * {@code defaults}, {@code ignoreDefaults}, {@code profile}) merged with the entity's mementifier-style
 * {@code this.memento} ({@code defaultIncludes}, {@code defaultExcludes}, {@code neverInclude}, {@code defaults},
 * {@code mappers}, {@code profiles}).
 * <p>
 * Includes and excludes are property names, getter names (without {@code get}) or dotted paths into associations
 * ({@code "role.name"}); an include may be aliased ({@code "lastLoginTime:lastLogin"}). {@code "*"} means every plain
 * property. An entity with no {@code defaultIncludes} defaults to the id and its plain properties, and the caller's
 * includes add to them.
 */
public final class MementoSpec {

	/** {@code includes}. */
	static final Key	INCLUDES			= Key.of( "includes" );
	/** {@code excludes}. */
	static final Key	EXCLUDES			= Key.of( "excludes" );
	/** {@code mappers}. */
	static final Key	MAPPERS				= Key.of( "mappers" );
	/** {@code defaults}. */
	static final Key	DEFAULTS			= Key.of( "defaults" );
	/** {@code ignoreDefaults}. */
	static final Key	IGNORE_DEFAULTS		= Key.of( "ignoreDefaults" );
	/** {@code profile}. */
	static final Key	PROFILE				= Key.of( "profile" );
	/** {@code memento}, the entity's this.memento. */
	static final Key	MEMENTO				= Key.of( "memento" );
	/** {@code defaultIncludes}. */
	static final Key	DEFAULT_INCLUDES	= Key.of( "defaultIncludes" );
	/** {@code defaultExcludes}. */
	static final Key	DEFAULT_EXCLUDES	= Key.of( "defaultExcludes" );
	/** {@code neverInclude}. */
	static final Key	NEVER_INCLUDE		= Key.of( "neverInclude" );
	/** {@code profiles}. */
	static final Key	PROFILES			= Key.of( "profiles" );

	/**
	 * One top-level include: a property, getter or association, with the includes below it.
	 *
	 * @param name     The name as written (before any alias).
	 * @param alias    The output key, or null to use the name.
	 * @param children The includes below it ({@code "name"} for {@code "role.name"}); empty for the association's own
	 *                 defaults.
	 */
	public record Include( String name, String alias, List<String> children ) {

		/**
		 * The key this include is written under.
		 *
		 * @param canonical The property's declared name, or null for a getter.
		 *
		 * @return The output key name.
		 */
		public String outputName( String canonical ) {
			return alias != null ? alias : canonical != null ? canonical : name;
		}
	}

	/** The caller's includes. */
	private final List<String>	includes;
	/** The caller's excludes. */
	private final List<String>	excludes;
	/** The caller's mappers. */
	private final IStruct		mappers;
	/** The caller's defaults. */
	private final IStruct		defaults;
	/** Ignore the entity's default includes and excludes. */
	private final boolean		ignoreDefaults;
	/** The mementifier profile, or empty. */
	private final String		profile;

	/**
	 * Create a spec.
	 *
	 * @param includes       The includes.
	 * @param excludes       The excludes.
	 * @param mappers        The mappers.
	 * @param defaults       The defaults for null values.
	 * @param ignoreDefaults Ignore the entity's default includes and excludes.
	 * @param profile        The profile, or empty.
	 */
	public MementoSpec( List<String> includes, List<String> excludes, IStruct mappers, IStruct defaults, boolean ignoreDefaults, String profile ) {
		this.includes		= includes;
		this.excludes		= excludes;
		this.mappers		= mappers;
		this.defaults		= defaults;
		this.ignoreDefaults	= ignoreDefaults;
		this.profile		= profile == null ? "" : profile.trim();
	}

	/**
	 * Read the caller's options.
	 *
	 * @param options {@code includes}, {@code excludes} (lists or arrays), {@code mappers}, {@code defaults} (structs),
	 *                {@code ignoreDefaults}, {@code profile}; may be null.
	 * @param bif     The BIF, for errors.
	 *
	 * @return The spec.
	 */
	public static MementoSpec fromOptions( IStruct options, String bif ) {
		if ( options == null ) {
			return new MementoSpec( List.of(), List.of(), new Struct(), new Struct(), false, "" );
		}
		return new MementoSpec( names( options.get( INCLUDES ), bif, "includes" ), names( options.get( EXCLUDES ), bif, "excludes" ),
		    struct( options.get( MAPPERS ), bif, "mappers" ), struct( options.get( DEFAULTS ), bif, "defaults" ),
		    BooleanCaster.cast( options.getOrDefault( IGNORE_DEFAULTS, false ) ),
		    options.get( PROFILE ) == null ? "" : options.get( PROFILE ).toString() );
	}

	/**
	 * The spec for an association below this one: only the nested includes when some were given, else the target's own
	 * defaults; the nested excludes; the same profile.
	 *
	 * @param include        The association's include.
	 * @param nestedExcludes The excludes below the association.
	 *
	 * @return The child spec.
	 */
	public MementoSpec child( Include include, List<String> nestedExcludes ) {
		return new MementoSpec( include.children(), nestedExcludes, new Struct(), new Struct(), !include.children().isEmpty(), profile );
	}

	/**
	 * The caller's includes.
	 *
	 * @return The includes.
	 */
	public List<String> includes() {
		return includes;
	}

	/**
	 * The profile.
	 *
	 * @return The profile, or empty.
	 */
	public String profile() {
		return profile;
	}

	/**
	 * The effective settings for one entity: the caller's options merged with its {@code this.memento} (and profile).
	 *
	 * @param entity The entity (null when only the entity type is known).
	 *
	 * @return The effective settings.
	 */
	public Effective resolve( IClassRunnable entity ) {
		IStruct			memento		= memento( entity );
		List<String>	allIncludes	= new ArrayList<>();
		List<String>	allExcludes	= new ArrayList<>();
		if ( !ignoreDefaults ) {
			List<String> defaultIncludes = names( memento.get( DEFAULT_INCLUDES ), "this.memento", "defaultIncludes" );
			// No defaultIncludes: the defaults are the plain properties (mementifier's ORM auto-includes), and the caller's
			// includes add to them.
			allIncludes.addAll( defaultIncludes.isEmpty() ? List.of( "*" ) : defaultIncludes );
			allExcludes.addAll( names( memento.get( DEFAULT_EXCLUDES ), "this.memento", "defaultExcludes" ) );
		}
		allIncludes.addAll( includes );
		allExcludes.addAll( excludes );
		allExcludes.addAll( names( memento.get( NEVER_INCLUDE ), "this.memento", "neverInclude" ) );
		IStruct allMappers = new Struct( IStruct.TYPES.LINKED );
		allMappers.putAll( struct( memento.get( MAPPERS ), "this.memento", "mappers" ) );
		allMappers.putAll( mappers );
		IStruct allDefaults = new Struct();
		allDefaults.putAll( struct( memento.get( DEFAULTS ), "this.memento", "defaults" ) );
		allDefaults.putAll( defaults );
		return new Effective( parseIncludes( allIncludes ), allExcludes, allMappers, allDefaults );
	}

	/**
	 * The effective settings for one entity.
	 *
	 * @param includes The top-level includes, by lower-cased name (a {@code "*"} entry stands for every plain property).
	 * @param excludes The excludes (top-level and dotted).
	 * @param mappers  The mappers, by key.
	 * @param defaults The defaults for null values, by key.
	 */
	public record Effective( Map<String, Include> includes, List<String> excludes, IStruct mappers, IStruct defaults ) {

		/**
		 * Whether a top-level name is excluded.
		 *
		 * @param name The name (any casing).
		 *
		 * @return True when excluded.
		 */
		public boolean excludes( String name ) {
			return excludes.stream().anyMatch( e -> e.trim().equalsIgnoreCase( name ) );
		}

		/**
		 * The excludes below an association: {@code "role.id"} gives {@code "id"} for {@code role}.
		 *
		 * @param name The association name (any casing).
		 *
		 * @return The nested excludes.
		 */
		public List<String> nestedExcludes( String name ) {
			List<String> result = new ArrayList<>();
			for ( String e : excludes ) {
				int dot = e.indexOf( '.' );
				if ( dot > 0 && e.substring( 0, dot ).trim().equalsIgnoreCase( name ) ) {
					result.add( e.substring( dot + 1 ).trim() );
				}
			}
			return result;
		}
	}

	/**
	 * The entity's {@code this.memento}, with the profile's settings replacing the base ones when the profile exists.
	 *
	 * @param entity The entity, or null.
	 *
	 * @return The memento settings (empty when the entity declares none).
	 */
	private IStruct memento( IClassRunnable entity ) {
		IStruct result = new Struct();
		if ( entity == null || ! ( entity.getThisScope().get( MEMENTO ) instanceof IStruct memento ) ) {
			return result;
		}
		result.putAll( memento );
		if ( !profile.isEmpty() && memento.get( PROFILES ) instanceof IStruct profiles && profiles.get( Key.of( profile ) ) instanceof IStruct chosen ) {
			result.putAll( chosen );
		}
		return result;
	}

	/**
	 * Group includes by their first segment.
	 *
	 * @param entries The includes.
	 *
	 * @return The top-level includes by lower-cased name, in order.
	 */
	private static Map<String, Include> parseIncludes( List<String> entries ) {
		Map<String, Include> result = new LinkedHashMap<>();
		for ( String raw : entries ) {
			String entry = raw.trim();
			if ( entry.isEmpty() ) {
				continue;
			}
			int		dot		= entry.indexOf( '.' );
			String	head	= dot > 0 ? entry.substring( 0, dot ).trim() : entry;
			String	rest	= dot > 0 ? entry.substring( dot + 1 ).trim() : null;
			String	alias	= null;
			if ( rest == null && head.contains( ":" ) ) {
				alias	= head.substring( head.indexOf( ':' ) + 1 ).trim();
				head	= head.substring( 0, head.indexOf( ':' ) ).trim();
			}
			Include existing = result.get( head.toLowerCase() );
			if ( existing == null ) {
				existing = new Include( head, alias, new ArrayList<>() );
				result.put( head.toLowerCase(), existing );
			} else if ( alias != null && existing.alias() == null ) {
				existing = new Include( existing.name(), alias, existing.children() );
				result.put( head.toLowerCase(), existing );
			}
			if ( rest != null && !rest.isEmpty() ) {
				existing.children().add( rest );
			}
		}
		return result;
	}

	/**
	 * A list of names from a comma list or an array.
	 *
	 * @param value The value (null for none).
	 * @param owner The BIF or {@code this.memento}, for errors.
	 * @param what  The option name, for errors.
	 *
	 * @return The names, trimmed, without blanks.
	 */
	static List<String> names( Object value, String owner, String what ) {
		Set<String> result = new LinkedHashSet<>();
		if ( value == null ) {
			return new ArrayList<>();
		}
		if ( value instanceof Array array ) {
			for ( Object item : array ) {
				if ( item != null && !item.toString().isBlank() ) {
					result.add( item.toString().trim() );
				}
			}
		} else if ( value instanceof String text ) {
			for ( String item : text.split( "," ) ) {
				if ( !item.isBlank() ) {
					result.add( item.trim() );
				}
			}
		} else {
			throw new ORMException( ORMErrorType.ARGUMENT,
			    owner + " " + what + " must be a list or an array of names, but is a " + value.getClass().getSimpleName() + ".",
			    "Pass e.g. \"id,name,role.name\" or [ \"id\", \"name\", \"role.name\" ]." );
		}
		return new ArrayList<>( result );
	}

	/**
	 * A struct option.
	 *
	 * @param value The value (null for none).
	 * @param owner The BIF or {@code this.memento}, for errors.
	 * @param what  The option name, for errors.
	 *
	 * @return The struct (empty for null).
	 */
	private static IStruct struct( Object value, String owner, String what ) {
		if ( value == null ) {
			return new Struct();
		}
		if ( value instanceof IStruct struct ) {
			return struct;
		}
		throw new ORMException( ORMErrorType.ARGUMENT, owner + " " + what + " must be a struct.", "Pass a struct of key to value." );
	}
}
