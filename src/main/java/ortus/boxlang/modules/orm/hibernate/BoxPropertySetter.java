package ortus.boxlang.modules.orm.hibernate;

import java.lang.reflect.Method;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Setter;

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.VariablesScope;

public class BoxPropertySetter implements Setter {

	private Property		mappedProperty;
	private PersistentClass	mappedEntity;
	private IBoxContext		context;

	public BoxPropertySetter( IBoxContext context, Property mappedProperty, PersistentClass mappedEntity ) {
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
		this.context		= context;
	}

	@Override
	public void set( Object target, Object value, SessionFactoryImplementor factory ) {
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
