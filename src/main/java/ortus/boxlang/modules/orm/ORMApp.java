package ortus.boxlang.modules.orm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.interceptors.SessionLifecycle;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.MappingGenerator;
import ortus.boxlang.runtime.context.ApplicationBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.IJDBCCapableContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.jdbc.ConnectionManager;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class ORMApp {

	/**
	 * The logger for the ORM application.
	 */
	private static final Logger				logger	= LoggerFactory.getLogger( ORMApp.class );

	/**
	 * A map of session factories, keyed by name.
	 */
	private Map<Key, SessionFactory>		sessionFactories;

	/**
	 * The boxlang context used to create this ORM application.
	 */
	private IBoxContext						context;

	/**
	 * The ORM configuration.
	 */
	private ORMConfig						config;

	/**
	 * A unique name for this ORM application.
	 */
	private Key								name;

	/**
	 * The default session factory for this ORM application.
	 * <p>
	 * In other words, the session factory for the default datasource.
	 */
	private SessionFactory					defaultSessionFactory;

	/**
	 * The default datasource for this ORM application - created from the datasource named in the ORM configuration.
	 */
	private DataSource						defaultDatasource;

	/**
	 * A map of entities discovered for this ORM application, keyed by datasource name.
	 */
	private Map<String, List<EntityRecord>>	entityMap;

	/**
	 * Get a unique name for this context's ORM Application.
	 * 
	 * Used to ensure we can tell the various ORM apps apart.
	 * 
	 * @return a unique key for the given context's application.
	 */
	public static Key getUniqueAppName( IBoxContext context ) {
		ApplicationBoxContext appContext = ( ApplicationBoxContext ) context.getParentOfType( ApplicationBoxContext.class );
		return Key.of( appContext.getApplication().getName() + "_" + context.getConfig().hashCode() );
	}

	public ORMApp( RequestBoxContext context, ORMConfig config ) {
		// @TODO: Consider only storing the ApplicationBoxContext, as that's the parent, and the RequestBoxContext will obviously age out pretty quickly.
		this.context			= context;
		this.config				= config;
		this.sessionFactories	= new ConcurrentHashMap<>();
		this.name				= ORMApp.getUniqueAppName( context );
		this.defaultDatasource	= context.getConnectionManager().getDatasourceOrThrow( Key.of( config.datasource ) );

		if ( context.getParentOfType( ApplicationBoxContext.class ) == null ) {
			logger.error( "ORMApp created with a context that is not inside an application context; aborting." );
			return;
		}

		logger.info( "ORMApp started" );

		this.entityMap = MappingGenerator.discoverEntities( context, config );
		logger.debug( "Discovered entities on {} datasources:", this.entityMap.size() );

		this.entityMap.forEach( ( datasourceName, entities ) -> {
			logger.debug( "Creating session factory for datasource: {}", datasourceName );
			DataSource				datasource	= context.getConnectionManager().getDatasourceOrThrow( Key.of( datasourceName ) );

			SessionFactoryBuilder	builder		= new SessionFactoryBuilder( context, datasource, config, entities );
			SessionFactory			factory		= builder.build();
			logger.info( "Registering new Hibernate session factory for name: {}", builder.getUniqueName() );
			this.sessionFactories.put( datasource.getUniqueName(), factory );

			if ( datasource.equals( this.defaultDatasource ) ) {
				logger.debug( "Setting the default datasource", datasource );
				this.defaultSessionFactory = factory;
			}
		} );

		// Register our ORM Session Manager
		context.getApplicationListener().getInterceptorPool().register( new SessionLifecycle( config ) );
	}

	/**
	 * Get a unique name for this context's ORM Application.
	 * 
	 * Used to ensure we can tell the various ORM apps apart.
	 * 
	 * @return a unique key for the given context's application.
	 */
	public Key getUniqueName() {
		return this.name;
	}

	/**
	 * Get ALL discovered entities/entity meta info for this ORM application.
	 */
	public List<EntityRecord> getEntityRecords() {
		return this.entityMap.values().stream().flatMap( List::stream ).toList();
	}

	/**
	 * 
	 * Get ALL discovered entities/entity meta for this ORM application which are associated with the given datasource.
	 * 
	 * @param datasourceName The datasource for which to get entities. Will filter the result to entities with a `datasource="myDatasourceName"`
	 *                       annotation.
	 */
	public List<EntityRecord> getEntityRecords( String datasourceName ) {
		if ( !this.entityMap.containsKey( datasourceName ) ) {
			throw new BoxRuntimeException( "No entities found for datasource: " + datasourceName );
		}
		return this.entityMap.get( datasourceName );
	}

	/**
	 * Get the entity map for this ORM application, where the key is the configured datasource name and the value is a list of EntityRecords.
	 */
	public Map<String, List<EntityRecord>> getEntityMap() {
		return this.entityMap;
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 * 
	 * @param datasourceName Datasource name to look up the session factory for.
	 */
	public SessionFactory getSessionFactoryOrThrow( Key datasourceName ) {
		return getSessionFactoryOrThrow( ( ( IJDBCCapableContext ) context ).getConnectionManager().getDatasourceOrThrow( datasourceName ) );
	}

	/**
	 * Get the SessionFactory instantiated for this particular datasource.
	 * 
	 * @param datasource The datasource for which to get the session factory.
	 */
	public SessionFactory getSessionFactoryOrThrow( DataSource datasource ) {
		if ( !this.sessionFactories.containsKey( datasource.getUniqueName() ) ) {
			throw new BoxRuntimeException( "No session factory found for datasource: " + datasource.getUniqueName() );
		}

		return this.sessionFactories.get( datasource.getUniqueName() );
	}

	/**
	 * Get the default session factory for this ORM application.
	 */
	public SessionFactory getDefaultSessionFactoryOrThrow() {
		if ( this.defaultSessionFactory == null ) {
			throw new BoxRuntimeException( "No default session factory set for ORM application" );
		}
		return this.defaultSessionFactory;
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context The context for which to get a session.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession( IBoxContext context ) {
		return getSession( context, null );
	}

	/**
	 * Get a Hibernate session for a given Boxlang context. Will open a new session if one does not already exist.
	 *
	 * @param context    The context for which to get a session.
	 * @param datasource The datasource to get the session for.
	 *
	 * @return The Hibernate session.
	 */
	public Session getSession( IBoxContext context, Key datasource ) {
		IBoxContext	jdbcContext		= ( IBoxContext ) context.getParentOfType( IJDBCCapableContext.class );
		DataSource	theDatasource	= getDatasourceForNameOrDefault( context, datasource );
		Key			sessionKey		= Key.of( "session_" + theDatasource.getUniqueName().getName() );

		if ( jdbcContext.hasAttachment( sessionKey ) ) {
			return jdbcContext.getAttachment( sessionKey );
		}

		SessionFactory sessionFactory = getSessionFactoryOrThrow( theDatasource );
		jdbcContext.putAttachment( sessionKey, sessionFactory.openSession() );
		return jdbcContext.getAttachment( sessionKey );
	}

	private DataSource getDatasourceForNameOrDefault( IBoxContext context, Key datasourceName ) {
		ConnectionManager connectionManager = context.getParentOfType( IJDBCCapableContext.class ).getConnectionManager();
		return datasourceName != null ? connectionManager.getDatasourceOrThrow( datasourceName )
		    : connectionManager.getDefaultDatasourceOrThrow();
	}

	/**
	 * Shut down the ORM application, including shutting down all Hibernate resources - session factories, open sessions and connections, etc.
	 */
	public void onShutdown() {
		logger.info( "ORMApp shutdown" );
		// @TODO: "It is the responsibility of the application to ensure that there are
		// no open sessions before calling this method as the impact on those
		// sessions is indeterminate."
		for ( SessionFactory sessionFactory : sessionFactories.values() ) {
			sessionFactory.close();
		}
		sessionFactories.clear();
	}
}
