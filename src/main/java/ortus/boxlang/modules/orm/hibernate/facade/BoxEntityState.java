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
 * The backing state a generated entity facade delegates to.
 * <p>
 * In production this is backed by a BoxLang entity instance ({@code IClassRunnable}), writing property values into its
 * {@code this} and {@code variables} scopes so BoxLang code and Hibernate see one shared state. It is an interface so
 * the generated facade codegen stays decoupled from the BoxLang runtime (and is unit-testable with a plain map).
 */
public interface BoxEntityState {

	/**
	 * Read a mapped property value from the backing BoxLang instance.
	 *
	 * @param property The property name.
	 *
	 * @return The current value, or {@code null}.
	 */
	Object get( String property );

	/**
	 * Write a mapped property value onto the backing BoxLang instance.
	 *
	 * @param property The property name.
	 * @param value    The value to set.
	 */
	void set( String property, Object value );
}
