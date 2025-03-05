package ortus.boxlang.modules.orm.hibernate;

import java.io.Serializable;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.proxy.AbstractLazyInitializer;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMRequestContext;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.runnables.IBoxRunnable;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class BoxLazyInitializer extends AbstractLazyInitializer implements Serializable {

	private final Serializable			id;
	private final String				entityName;
	private final PersistentClass		mappingInfo;
	private static final ClassLocator	CLASS_LOCATOR	= BoxRuntime.getInstance().getClassLocator();

	public BoxLazyInitializer( String entityName, Serializable id, SharedSessionContractImplementor session, PersistentClass mappingInfo ) {
		super( entityName, id, session );
		this.id				= id;
		this.entityName		= entityName;
		this.mappingInfo	= mappingInfo;
	}

	public IBoxRunnable getEntity() {
		RequestBoxContext context = RequestBoxContext.getCurrent();
		if ( context == null ) {
			throw new BoxRuntimeException( "No request box context could be found" );
		}
		ORMApp			ormApp			= ORMRequestContext.getForContext( context ).getORMApp();
		EntityRecord	entityRecord	= ormApp.lookupEntity( entityName, true );
		DynamicObject	entity			= CLASS_LOCATOR.load(
		    context,
		    entityRecord.getClassName(),
		    entityRecord.getResolverPrefix(),
		    true,
		    context.getCurrentImports()
		);
		IClassRunnable	classRunnable	= ( IClassRunnable ) entity.unWrapBoxLangClass();
		// now we do BoxClassInstantiator to generate association methods, etc.
		return classRunnable;
	}

	@Override
	public Class getPersistentClass() {
		throw new UnsupportedOperationException( "dynamic-map entity representation" );
	}

}
