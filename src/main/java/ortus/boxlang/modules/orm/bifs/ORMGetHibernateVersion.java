/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

@BoxBIF
public class ORMGetHibernateVersion extends BaseORMBIF {

	/**
	 * Retrieve the installed Hibernate version.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String version = org.hibernate.Version.getVersionString();
		if ( !version.trim().toUpperCase().equals( "[WORKING]" ) ) {
			return version;
		}
		ModuleRecord ormModule = context.getRuntime().getModuleService().getModuleRecord( ORMKeys.moduleName );
		return StringCaster.cast(
		    ormModule.settings.getOrDefault( ORMKeys.hibernateVersion, "UNKNOWN" )
		);
	}

}
