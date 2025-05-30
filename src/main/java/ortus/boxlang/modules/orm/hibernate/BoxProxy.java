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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

import ortus.boxlang.compiler.parser.BoxSourceType;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.loader.ImportDefinition;
import ortus.boxlang.runtime.runnables.BoxClassSupport;
import ortus.boxlang.runtime.runnables.BoxInterface;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.StaticScope;
import ortus.boxlang.runtime.scopes.ThisScope;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.AbstractFunction;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Property;
import ortus.boxlang.runtime.types.meta.BoxMeta;
import ortus.boxlang.runtime.util.ResolvedFilePath;

/**
 * Boxlang class proxy.
 * 
 * @since 1.0.0
 */
public class BoxProxy implements IClassRunnable, HibernateProxy {

	private BoxLazyInitializer	lazyInitializer;

	private IClassRunnable		runnable;

	/**
	 * Constructor.
	 *
	 * @param entityName
	 * @param id
	 * @param session
	 */
	public BoxProxy( String entityName, Serializable id, SharedSessionContractImplementor session, PersistentClass mappingInfo ) {
		this.lazyInitializer = new BoxLazyInitializer( entityName, id, session, mappingInfo );
	}

	/**
	 * Private method to get the instantiated targer from the initializer.
	 *
	 * @return
	 */
	public IClassRunnable getRunnable() {
		if ( runnable == null ) {
			runnable = lazyInitializer.getInstantiatedEntity();
		}
		return runnable;
	}

	/****** Hibernate Proxy Implementation Methods ***************/

	/**
	 * Perform serialization-time write-replacement of this proxy.
	 *
	 * @return The serializable proxy replacement.
	 */
	@Override
	public Object writeReplace() {
		return this;
	}

	/**
	 * Get the underlying lazy initialization handler.
	 *
	 * @return The lazy initializer.
	 */
	@Override
	public LazyInitializer getHibernateLazyInitializer() {
		return this.lazyInitializer;
	}

	/**
	 * Assign a value to a key
	 *
	 * @param key   The key to assign
	 * @param value The value to assign
	 */
	@Override
	public Object assign( IBoxContext context, Key key, Object value ) {
		return BoxClassSupport.assign( getRunnable(), context, key, value );
	}

	/**
	 * Dereference this object by a key and return the value, or throw exception
	 *
	 * @param key  The key to dereference
	 * @param safe Whether to throw an exception if the key is not found
	 *
	 * @return The requested object
	 */
	@Override
	public Object dereference( IBoxContext context, Key key, Boolean safe ) {
		return BoxClassSupport.dereference( getRunnable(), context, key, safe );
	}

	/**
	 * Dereference this object by a key and invoke the result as an invokable (UDF, java method) using positional arguments
	 *
	 * @param name                The key to dereference
	 * @param positionalArguments The positional arguments to pass to the invokable
	 * @param safe                Whether to throw an exception if the key is not found
	 *
	 * @return The requested object
	 */
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Object[] positionalArguments, Boolean safe ) {
		return BoxClassSupport.dereferenceAndInvoke( getRunnable(), context, name, positionalArguments, safe );
	}

	/**
	 * Dereference this object by a key and invoke the result as an invokable (UDF, java method)
	 *
	 * @param name           The name of the key to dereference, which becomes the method name
	 * @param namedArguments The arguments to pass to the invokable
	 * @param safe           If true, return null if the method is not found, otherwise throw an exception
	 *
	 * @return The requested return value or null
	 */
	public Object dereferenceAndInvoke( IBoxContext context, Key name, Map<Key, Object> namedArguments, Boolean safe ) {
		return BoxClassSupport.dereferenceAndInvoke( getRunnable(), context, name, namedArguments, safe );
	}

	/****** IClassRunnable Implementation Methods ***************/

	@Override
	public List<ImportDefinition> getImports() {
		return getRunnable().getImports();
	}

	@Override
	public Object getRunnableAST() {
		return getRunnable().getRunnableAST();
	}

	@Override
	public long getRunnableCompileVersion() {
		return getRunnable().getRunnableCompileVersion();
	}

	@Override
	public LocalDateTime getRunnableCompiledOn() {
		return getRunnable().getRunnableCompiledOn();
	}

	@Override
	public ResolvedFilePath getRunnablePath() {
		return getRunnable().getRunnablePath();
	}

	@Override
	public BoxSourceType getSourceType() {
		return getRunnable().getSourceType();
	}

	@Override
	public String asString() {
		return getRunnable().asString();
	}

	@Override
	public BoxMeta _getbx() {
		return getRunnable()._getbx();
	}

	@Override
	public void _pseudoConstructor( IBoxContext arg0 ) {
		getRunnable()._pseudoConstructor( arg0 );
	}

	@Override
	public void _setSuper( IClassRunnable arg0 ) {
		getRunnable().setSuper( arg0 );
	}

	@Override
	public void _setbx( BoxMeta arg0 ) {
		getRunnable()._setbx( arg0 );
	}

	@Override
	public Key bxGetName() {
		return getRunnable().bxGetName();
	}

	@Override
	public Boolean canInvokeImplicitAccessor( IBoxContext arg0 ) {
		return getRunnable().canInvokeImplicitAccessor( arg0 );
	}

	@Override
	public Boolean canOutput() {
		return getRunnable().canOutput();
	}

	@Override
	public Map<Key, AbstractFunction> getAbstractMethods() {
		return getRunnable().getAbstractMethods();
	}

	@Override
	public Map<Key, AbstractFunction> getAllAbstractMethods() {
		return getRunnable().getAllAbstractMethods();
	}

	@Override
	public IStruct getAnnotations() {
		return getRunnable().getAnnotations();
	}

	@Override
	public IClassRunnable getBottomClass() {
		return getRunnable().getBottomClass();
	}

	@Override
	public BoxMeta getBoxMeta() {
		return getRunnable().getBoxMeta();
	}

	@Override
	public Boolean getCanInvokeImplicitAccessor() {
		return getRunnable().getCanInvokeImplicitAccessor();
	}

	@Override
	public Boolean getCanOutput() {
		return getRunnable().getCanOutput();
	}

	@Override
	public IClassRunnable getChild() {
		return getRunnable().getChild();
	}

	@Override
	public Set<Key> getCompileTimeMethodNames() {
		return getRunnable().getCompileTimeMethodNames();
	}

	@Override
	public IStruct getDocumentation() {
		return getRunnable().getDocumentation();
	}

	@Override
	public Map<Key, Property> getGetterLookup() {
		return getRunnable().getGetterLookup();
	}

	@Override
	public List<BoxInterface> getInterfaces() {
		return getRunnable().getInterfaces();
	}

	@Override
	public IStruct getMetaData() {
		return getRunnable().getMetaData();
	}

	@Override
	public Map<Key, Property> getProperties() {
		return getRunnable().getProperties();
	}

	@Override
	public Map<Key, Property> getSetterLookup() {
		return getRunnable().getSetterLookup();
	}

	@Override
	public StaticScope getStaticScope() {
		return getRunnable().getStaticScope();
	}

	@Override
	public IClassRunnable getSuper() {
		return getRunnable().getSuper();
	}

	@Override
	public ThisScope getThisScope() {
		return getRunnable().getThisScope();
	}

	@Override
	public VariablesScope getVariablesScope() {
		return getRunnable().getVariablesScope();
	}

	@Override
	public boolean isJavaExtends() {
		return getRunnable().isJavaExtends();
	}

	@Override
	public MethodHandle lookupPrivateField( Field arg0 ) {
		return getRunnable().lookupPrivateField( arg0 );
	}

	@Override
	public MethodHandle lookupPrivateMethod( Method arg0 ) {
		return getRunnable().lookupPrivateMethod( arg0 );
	}

	@Override
	public void pseudoConstructor( IBoxContext arg0 ) {
		getRunnable()._pseudoConstructor( arg0 );
	}

	@Override
	public void registerInterface( BoxInterface arg0 ) {
		getRunnable().registerInterface( arg0 );
	}

	@Override
	public void setCanInvokeImplicitAccessor( Boolean arg0 ) {
		getRunnable().setCanInvokeImplicitAccessor( arg0 );
	}

	@Override
	public void setCanOutput( Boolean arg0 ) {
		getRunnable().setCanOutput( arg0 );
	}

	@Override
	public void setChild( IClassRunnable arg0 ) {
		getRunnable().setChild( arg0 );
	}

	@Override
	public void setSuper( IClassRunnable arg0 ) {
		getRunnable().setSuper( arg0 );
	}

}
