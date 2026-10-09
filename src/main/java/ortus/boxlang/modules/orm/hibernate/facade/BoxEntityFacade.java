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
package ortus.boxlang.modules.orm.hibernate.facade;

/**
 * Implemented by every generated entity facade class. Hibernate manages the facade as a normal POJO; bx-orm unwraps it
 * to the backing BoxLang instance at the BIF boundary. This interface exposes the facade's backing {@link BoxEntityState}
 * so the property-accessor interceptor (and bx-orm's wrap/unwrap layer) can reach it.
 */
public interface BoxEntityFacade {

	/**
	 * @return The backing state this facade delegates its mapped properties to.
	 */
	BoxEntityState boxState();
}
