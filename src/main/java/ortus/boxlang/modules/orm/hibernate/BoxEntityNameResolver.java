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
import ortus.boxlang.modules.orm.hibernate.facade.BoxEntityFacade;

/**
 * Determine entity names for a given entity/boxlang class.
 *
 * @since 1.0.0
 */
public class BoxEntityNameResolver implements EntityNameResolver {

	@Override
	public String resolveEntityName( Object entity ) {
		// Facade (POJO) mode: Hibernate registers the entity under the generated facade's fully-qualified class name
		// (the modern mapping.xml `class` attribute binds via the annotation path, so the entity-name IS the FQN). The
		// facade is a real Java class, so its class name is exactly that registered entity-name. Hibernate's
		// assertInstanceOfEntityType calls this resolver on the facade instance and must get the FQN back, not the
		// BoxLang short name, or it rejects the instance as "not an entity class".
		if ( entity instanceof BoxEntityFacade ) {
			return entity.getClass().getName();
		}
		return ORMService.getEntityName( entity );
	}

}
