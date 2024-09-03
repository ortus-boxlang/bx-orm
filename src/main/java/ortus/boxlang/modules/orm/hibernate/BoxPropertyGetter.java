package ortus.boxlang.modules.orm.hibernate;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortus.boxlang.runtime.context.IBoxContext;

public class BoxPropertyGetter implements Getter {

	private Property			mappedProperty;
	private PersistentClass		mappedEntity;
	private IBoxContext			context;

	private static final Logger	log	= LoggerFactory.getLogger( BoxPropertyGetter.class );

	public BoxPropertyGetter( IBoxContext context, Property mappedProperty, PersistentClass mappedEntity ) {
		this.mappedProperty	= mappedProperty;
		this.mappedEntity	= mappedEntity;
		this.context		= context;
	}

	@Override
	public Object get( Object owner ) {
		log.debug( "getting property {} on entity {}", mappedProperty.getName(), mappedEntity.getEntityName() );
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'get'" );
	}

	@Override
	public Object getForInsert( Object owner, Map mergeMap, SharedSessionContractImplementor session ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getForInsert'" );
	}

	@Override
	public Class getReturnType() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getReturnType'" );
	}

	@Override
	public Member getMember() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getMember'" );
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
