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
package ortus.boxlang.modules.orm.hibernate;

import org.hibernate.EntityNameResolver;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Determine entity names for a given entity/boxlang class.
 */
public class BoxEntityNameResolver implements EntityNameResolver {

	@Override
	public String resolveEntityName( Object entity ) {
		if ( entity instanceof IClassRunnable boxClass ) {
			IStruct	annotations	= boxClass.getAnnotations();
			String	result		= null;
			if ( annotations.containsKey( ORMKeys.entity ) ) {
				result = StringCaster.cast( annotations.get( ORMKeys.entity ) );
			} else if ( annotations.containsKey( ORMKeys.entityName ) ) {
				result = StringCaster.cast( annotations.get( ORMKeys.entityName ) );
			}
			if ( result == null || result.isBlank() ) {
				result = boxClass.getClass().getSimpleName().replace( ORMService.BX_CLASS_SUFFIX, "" ).replace( ORMService.CFC_CLASS_SUFFIX, "" );
			}
			return result.trim();
		}
		return null;
	}

}
