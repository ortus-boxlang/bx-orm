package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

@BoxBIF
public class GetHibernateVersion extends BIF {

	/**
	 * ORM Service, responsible for managing ORM applications.
	 */
	private ORMService ormService;

	/**
	 * Retrieve the installed Hibernate version.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return org.hibernate.Version.getVersionString();
	}

}
