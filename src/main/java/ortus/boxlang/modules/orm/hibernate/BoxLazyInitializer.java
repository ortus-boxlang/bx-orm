package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.proxy.AbstractLazyInitializer;
import org.hibernate.tuple.entity.EntityMetamodel;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.runnables.IBoxRunnable;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.Struct;

public class BoxLazyInitializer extends AbstractLazyInitializer implements Serializable {

	@SuppressWarnings( "unused" ) // needed for compilation
	private final Serializable			id;
	@SuppressWarnings( "unused" ) // needed for compilation
	private final String				entityName;
	private final PersistentClass		mappingInfo;
	private final EntityMetamodel		entityMetamodel;
	private final ORMApp				ormApp;
	private RequestBoxContext			context;
	private final EntityRecord			entityRecord;
	private final BoxClassInstantiator	boxClassInstantiator;

	public BoxLazyInitializer( String entityName, Serializable id, SharedSessionContractImplementor session, PersistentClass mappingInfo ) {
		super( entityName, id, session );
		System.out.println( "Contract implementor: " + session.getClass().getName() );
		this.id				= id;
		this.entityName		= entityName;
		this.mappingInfo	= mappingInfo;

		this.context		= RequestBoxContext.getCurrent();
		if ( context == null ) {
			context = new ScriptingRequestBoxContext( BoxRuntime.getInstance().getRuntimeContext() );
		}
		this.ormApp			= ORMRequestContext.getForContext( context ).getORMApp();
		this.entityRecord	= ormApp.lookupEntity( entityName, true );

		SessionFactoryImplementor sessionFactoryImpl = ( SessionFactoryImplementor ) ormApp.getSessionFactoryOrThrow( this.entityRecord.getDatasource() );
		this.entityMetamodel		= sessionFactoryImpl.getMetamodel()
		    .entityPersister( this.entityRecord.getEntityName() )
		    .getEntityMetamodel();
		this.boxClassInstantiator	= new BoxClassInstantiator( entityMetamodel, this.mappingInfo );
	}

	public IBoxRunnable getEntity() {
		return boxClassInstantiator.instantiate( context, entityRecord, Struct.EMPTY );
	}

	@SuppressWarnings( "rawtypes" )
	@Override
	public Class getPersistentClass() {
		throw new UnsupportedOperationException( "dynamic-map entity representation" );
	}

	/**
	 * Convenience method to get the actual instantiated target
	 *
	 * @return
	 */
	public IClassRunnable getInstantiatedEntity() {
		return ( IClassRunnable ) getImplementation();
	}

}
