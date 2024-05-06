package ortus.boxlang.modules.orm.hibernate;

import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

public class BoxProxy implements HibernateProxy {

	@Override
	public Object writeReplace() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'writeReplace'" );
	}

	@Override
	public LazyInitializer getHibernateLazyInitializer() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'getHibernateLazyInitializer'" );
	}

}
