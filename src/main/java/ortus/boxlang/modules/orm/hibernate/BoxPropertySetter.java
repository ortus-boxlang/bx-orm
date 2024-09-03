package ortus.boxlang.modules.orm.hibernate;

import java.lang.reflect.Method;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.VariablesScope;

public class BoxPropertySetter implements Setter {

	private Property			mappedProperty;
	private PersistentClass		mappedEntity;
	private IBoxContext			context;

	private final static Logger	log	= LoggerFactory.getLogger( BoxPropertySetter.class );

	public BoxPropertySetter( IBoxContext context, Property mappedProperty, PersistentClass mappedEntity ) {
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
		this.context		= context;
	}

	@Override
	public void set( Object target, Object value, SessionFactoryImplementor factory ) {
		log.debug( "Setting property {} on entity {} to value {}", mappedProperty.getName(), mappedEntity.getEntityName(), value );
		if ( target instanceof IClassRunnable instance ) {
			VariablesScope variables = instance.getVariablesScope();
			variables.put( mappedProperty.getName(), value );
		}
	}

	@Override
	public String getMethodName() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMethodName'" );
	}

	@Override
	public Method getMethod() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMethod'" );
	}

}
