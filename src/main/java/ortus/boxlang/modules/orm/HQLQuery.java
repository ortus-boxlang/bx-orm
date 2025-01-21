package ortus.boxlang.modules.orm;

import java.util.List;

import org.hibernate.Session;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.jdbc.QueryParameter;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

public class HQLQuery {

	private static BoxRuntime		runtime		= BoxRuntime.getInstance();
	private static ORMService		ormService	= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );
	private Key						datasource;
	private Session					session;
	private ORMApp					ormApp;
	private IBoxContext				context;
	private ORMRequestContext		ormRequestContext;

	private List<QueryParameter>	params;
	private IStruct					options;
	private String					hql;

	public HQLQuery( IBoxContext context, String hql, List<QueryParameter> params, IStruct options ) {
		this.params				= params;
		this.options			= options;
		this.context			= context;
		this.hql				= hql;
		this.ormApp				= ormService.getORMAppByContext( context.getRequestContext() );
		this.ormRequestContext	= ORMRequestContext.getForContext( context.getRequestContext() );

		this.datasource			= options.containsKey( Key.datasource ) ? Key.of( options.getAsString( Key.datasource ) ) : null;
		this.session			= ormRequestContext.getSession( datasource );
	}

	public List<?> execute() {
		org.hibernate.query.Query<?> hqlQuery = session.createQuery( this.hql );

		if ( this.options.containsKey( Key.offset ) ) {
			hqlQuery.getQueryOptions().setFirstRow( this.options.getAsInteger( Key.offset ) );
		}
		if ( this.options.containsKey( ORMKeys.maxResults ) ) {
			hqlQuery.getQueryOptions().setMaxRows( this.options.getAsInteger( ORMKeys.maxResults ) );
		}
		if ( this.options.containsKey( ORMKeys.readOnly ) ) {
			hqlQuery.setReadOnly( BooleanCaster.cast( this.options.get( ORMKeys.readOnly ) ) );
		}

		List<?> result = hqlQuery.list();
		return result;
	}
}
