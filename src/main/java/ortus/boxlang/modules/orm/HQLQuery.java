package ortus.boxlang.modules.orm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.ArrayCaster;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.dynamic.casters.CastAttempt;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.jdbc.QueryParameter;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.DatabaseException;

public class HQLQuery {

	private static BoxRuntime		runtime				= BoxRuntime.getInstance();
	private static ORMService		ormService			= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );
	private Key						datasource;
	private Session					session;
	private ORMApp					ormApp;
	private IBoxContext				context;
	private ORMRequestContext		ormRequestContext;

	private List<QueryParameter>	parameters;
	private int						parameterCount;
	private IStruct					options;
	private String					hql;
	private List<String>			HQLWithParamTokens	= new ArrayList<>();

	private static final String		UPDATE_PREFIX		= "UPDATE";
	private static final String		DELETE_PREFIX		= "DELETE";

	public HQLQuery( IBoxContext context, String hql, Object bindings, IStruct options ) {
		this.options			= options;
		this.context			= context;
		this.hql				= hql;

		this.ormApp				= ormService.getORMAppByContext( context.getRequestContext() );
		this.ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );
		this.datasource			= options.containsKey( Key.datasource ) ? Key.of( options.getAsString( Key.datasource ) ) : null;
		this.session			= ormRequestContext.getSession( datasource );

		this.parameterCount		= 0;
		this.parameters			= processBindings( bindings );
	}

	private List<QueryParameter> processBindings( Object bindings ) {
		if ( bindings == null ) {
			return new ArrayList<>();
		}
		CastAttempt<Array> castAsArray = ArrayCaster.attempt( bindings );
		if ( castAsArray.wasSuccessful() ) {
			Array castedArray = castAsArray.get();
			// Short circuit for empty arrays to simplify our checks below
			if ( castedArray.isEmpty() ) {
				return new ArrayList<>();
			}
			// An array could be an array of structs where a name is specified in each struct, which we massage back into a struct
			int		foundNames		= 0;
			IStruct	possibleStruct	= Struct.of();
			for ( Object item : castedArray ) {
				CastAttempt<IStruct> itemStructAttempt = StructCaster.attempt( item );
				if ( itemStructAttempt.wasSuccessful() ) {
					IStruct itemStruct = itemStructAttempt.get();
					if ( itemStruct.containsKey( Key._NAME ) ) {
						foundNames++;
						possibleStruct.put( Key.of( itemStruct.get( Key._NAME ) ), itemStruct );
					}
				}
			}
			// All items in the array are structs with names
			if ( foundNames == castedArray.size() ) {
				return buildParameterList( null, possibleStruct );
			} else if ( foundNames > 0 ) {
				throw new DatabaseException( "Invalid query params passed as array of structs. Some structs have a name, some do not." );
			}
			// No structs with names were found, or possbly no structs were found at all!
			return buildParameterList( castedArray, null );
		}
		CastAttempt<IStruct> castAsStruct = StructCaster.attempt( bindings );
		if ( castAsStruct.wasSuccessful() ) {
			return buildParameterList( null, castAsStruct.get() );
		}

		// We always have bindings, since we exit early if there are none
		String className = bindings.getClass().getName();
		throw new DatabaseException( "Invalid type for query params. Expected array or struct. Received: " + className );
	}

	/**
	 * Process a struct of named query bindings into a list of
	 * {@link QueryParameter} instances.
	 * <p>
	 * Also performs SQL string replacement to convert named parameters to
	 * positional placeholders.
	 *
	 * @param sql        The SQL string to execute
	 * @param parameters An `IStruct` of `String` `name` to either an `Object`
	 *                   `value` or a `queryparam` `IStruct`.
	 */
	@SuppressWarnings( { "null", "unchecked" } )
	private List<QueryParameter> buildParameterList( Array positionalParameters, IStruct namedParameters ) {
		List<QueryParameter> params = new ArrayList<>();
		// Short circuit for no parameters
		if ( positionalParameters == null && namedParameters == null ) {
			return params;
		} else if ( positionalParameters != null && positionalParameters.isEmpty() ) {
			return params;
		} else if ( namedParameters != null && namedParameters.isEmpty() ) {
			return params;
		}

		boolean			isPositional		= positionalParameters != null;
		String			HQL					= this.hql;
		// This is the HQL string with the named parameters replaced with positional placeholders
		StringBuilder	newHQL				= new StringBuilder();
		// This is the name of the current named parameter being processed
		StringBuilder	paramName			= new StringBuilder();
		// This is the name (index) of the current positional parameter being processed, like ?1 or ?23
		StringBuilder	positionName		= new StringBuilder();
		// This is the current HQL token being processed. We'll save these for later when we apply the parameters.
		// We could techincally finalize this string now, but we'd end up casting all the values twice which seems inefficient.
		StringBuilder	HQLWithParamToken	= new StringBuilder();
		// Track the named params we encounter for validation below
		Set<Key>		foundNamedParams	= new HashSet<>();

		// 0 = Default state, processing HQL
		// 1 = Inside a string literal
		// 2 = Inside a single line comment
		// 3 = Inside a multi-line comment
		// 4 = Inside a named parameter
		// 5 = Inside a positional parameter, like ? or ?1 or ?23
		int				state				= 0;

		// This should always match params.size(), but is a little easier to use
		final int[]		paramsEncountered	= { 0 };
		// Pop this into a lambda so we can re-use it for the last named parameter
		Runnable		processNamed		= () -> {
												HQLWithParamTokens.add( HQLWithParamToken.toString() );
												HQLWithParamToken.setLength( 0 );
												Key finalParamName = Key.of( paramName.toString() );
												if ( isPositional ) {
													throw new DatabaseException(
													    "Named parameter [:" + finalParamName.getName() + "] found in query with positional parameters." );
												} else {
													if ( namedParameters.containsKey( finalParamName ) ) {
														QueryParameter newParam = QueryParameter.fromAny( namedParameters.get( finalParamName ) );
														foundNamedParams.add( finalParamName );
														params.add( newParam );
														// List params add ?1, ?2, ?3 etc. to the HQL string
														if ( newParam.isListParam() ) {
															List<Object> values = ( List<Object> ) newParam.getValue();
															newHQL.append(
															    values.stream()
															        .map( v -> "?" + ( ++this.parameterCount ) )
															        .collect( Collectors.joining( ", " ) )
															);
														} else {
															newHQL.append( "?" + ( ++this.parameterCount ) );
														}
													} else {
														throw new DatabaseException(
														    "Named parameter [:" + finalParamName.getName() + "] not provided to query." );
													}
												}
											};
		// Pop this into a lambda so we can re-use it for the last positional parameter
		Runnable		processPositional	= () -> {
												if ( paramsEncountered[ 0 ] > positionalParameters.size() ) {
													throw new DatabaseException( "Too few positional parameters [" + positionalParameters.size()
													    + "] provided for query having at least [" + paramsEncountered[ 0 ] + "] '?' char(s)." );
												}
												HQLWithParamTokens.add( HQLWithParamToken.toString() );
												HQLWithParamToken.setLength( 0 );
												var newParam = QueryParameter.fromAny( positionalParameters.get( paramsEncountered[ 0 ] - 1 ) );
												params.add( newParam );
												// List params add ?1, ?2, ?3 etc. to the HQL string
												if ( newParam.isListParam() ) {
													List<Object> values = ( List<Object> ) newParam.getValue();
													newHQL.append(
													    values.stream()
													        .map( v -> "?" + ( ++this.parameterCount ) )
													        .collect( Collectors.joining( ", " ) )
													);
												} else {
													newHQL.append( "?" + ( ++this.parameterCount ) );
												}
											};

		for ( int i = 0; i < HQL.length(); i++ ) {
			char c = HQL.charAt( i );

			switch ( state ) {
				// Default state, processing HQL
				case 0 : {
					if ( c == '\'' ) {
						// If we've reached a ' then we're inside a string literal
						state = 1;
					} else if ( c == '-' && i < HQL.length() - 1 && HQL.charAt( i + 1 ) == '-' ) {
						// If we've reached a -- then we're inside a single line comment
						state = 2;
					} else if ( c == '/' && i < HQL.length() - 1 && HQL.charAt( i + 1 ) == '*' ) {
						// If we've reached a /* then we're inside a multi-line comment
						state = 3;
					} else if ( c == '?' ) {
						// We've encountered a positional parameter
						paramsEncountered[ 0 ]++;
						if ( !isPositional ) {
							throw new DatabaseException( "Positional parameter [?] found in query with named parameters." );
						}

						state = 5;
						// Do not append anything
						break;
					} else if ( c == ':' ) {
						// We've encountered a named parameter
						state = 4;
						// Do not append anything
						break;
					}
					newHQL.append( c );
					HQLWithParamToken.append( c );
					break;
				}
				// Inside a string literal
				case 1 : {
					// If we've reached the ending ' and it wasn't escaped as \' then we're done
					if ( c == '\'' && ( i == HQL.length() - 1 || HQL.charAt( i + 1 ) != '\'' ) ) {
						state = 0;
						// if we reached ' but the next char is also ' then this is just an escaped ''
						// Append them both and move on
					} else if ( c == '\'' && i < HQL.length() - 1 && HQL.charAt( i + 1 ) == '\'' ) {
						newHQL.append( c ); // Append the first single quote
						HQLWithParamToken.append( c );
						c = HQL.charAt( ++i ); // Skip the next single quote
					}
					newHQL.append( c );
					HQLWithParamToken.append( c );
					break;
				}
				// Inside a single line comment
				case 2 : {
					if ( c == '\n' || c == '\r' ) {
						state = 0;
					}
					newHQL.append( c );
					HQLWithParamToken.append( c );
					break;
				}
				// Inside a multi-line comment
				case 3 : {
					if ( c == '*' && i < HQL.length() - 1 && HQL.charAt( i + 1 ) == '/' ) {
						state = 0;
						newHQL.append( c );
						HQLWithParamToken.append( c );
						c = HQL.charAt( ++i );
					}
					newHQL.append( c );
					HQLWithParamToken.append( c );
					break;
				}
				// Inside a named parameter
				case 4 : {
					if ( ! ( Character.isLetterOrDigit( c ) || c == '_' ) ) {
						processNamed.run();
						paramName.setLength( 0 );
						// reset the state and backup to re-precess the next char again
						state = 0;
						i--;
						break;
					}
					paramName.append( c );
					break;
				}
				// Inside a numbered positional parameter, like ?1 or ?23
				case 5 : {
					if ( ! ( Character.isDigit( c ) ) ) {
						processPositional.run();
						positionName.setLength( 0 );
						// reset the state and backup to re-precess the next char again
						state = 0;
						i--;
						break;
					}
					positionName.append( c );
					break;
				}
			}
		}

		// If named param is the last thing in the query
		if ( state == 4 ) {
			processNamed.run();
		}
		// If positional param is the last thing in the query
		if ( state == 5 ) {
			processPositional.run();
		}

		HQLWithParamTokens.add( HQLWithParamToken.toString() );
		this.hql = newHQL.toString();
		return params;
	}

	public Object execute() {
		boolean							isUpdate	= this.hql.trim().toUpperCase().startsWith( UPDATE_PREFIX )
		    || this.hql.trim().toUpperCase().startsWith( DELETE_PREFIX );

		org.hibernate.query.Query<?>	hqlQuery	= session.createQuery( this.hql );

		if ( this.options.containsKey( Key.offset ) ) {
			hqlQuery.getQueryOptions().setFirstRow( this.options.getAsInteger( Key.offset ) );
		}
		if ( this.options.containsKey( ORMKeys.maxResults ) ) {
			hqlQuery.getQueryOptions().setMaxRows( this.options.getAsInteger( ORMKeys.maxResults ) );
		}
		if ( this.options.containsKey( ORMKeys.readOnly ) ) {
			hqlQuery.setReadOnly( BooleanCaster.cast( this.options.get( ORMKeys.readOnly ) ) );
		}

		if ( this.parameters != null ) {
			int parameterIndex = 1;
			for ( QueryParameter param : this.parameters ) {
				if ( param.isListParam() ) {
					Array list = ( Array ) param.getValue();
					for ( Object value : list ) {
						hqlQuery.setParameter( parameterIndex++, value );
					}
				} else {
					hqlQuery.setParameter( parameterIndex++, param.getValue() );
				}
			}
		}
		if ( isUpdate ) {
			return hqlQuery.executeUpdate();
		} else {
			return hqlQuery.list();
		}

	}
}
