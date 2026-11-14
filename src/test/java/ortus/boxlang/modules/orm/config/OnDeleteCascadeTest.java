package ortus.boxlang.modules.orm.config;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;

/**
 * Live (embedded Derby) tests for deleting a parent whose children are still loaded in the session, when the parent has no
 * collection and no ORM cascade and the database cascades the delete. Uses {@code src/test/resources/onDeleteApp}.
 */
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class OnDeleteCascadeTest {

	private static final Key	APP_NAME	= Key.of( "BXORMOnDeleteTest" );
	private static final Path	APP_ROOT	= Path.of( "src/test/resources/onDeleteApp" ).toAbsolutePath();

	private BoxRuntime			instance;

	@BeforeAll
	public void setUp() {
		instance = BoxRuntime.getInstance( false );
		if ( !instance.getModuleService().hasModule( ORMKeys.moduleName ) ) {
			ModuleRecord ormModuleRecord = new ModuleRecord( Path.of( "./build/module" ).toAbsolutePath().toString() );
			instance.getModuleService().getRegistry().put( ORMKeys.moduleName, ormModuleRecord );
			ormModuleRecord
			    .loadDescriptor( instance.getRuntimeContext() )
			    .register( instance.getRuntimeContext() )
			    .activate( instance.getRuntimeContext() );
		}
	}

	@AfterAll
	public void teardown() {
		instance.getApplicationService().shutdownApplication( APP_NAME );
		RequestBoxContext.removeCurrent();
	}

	@DisplayName( "onDelete=\"cascade\": deleting the parent while its children are loaded flushes and the rows are gone" )
	@Test
	public void testOnDeleteCascadeWithLoadedChildren() {
		IScope vars = runRequest( """
		                          parent = entityNew( "OdParent", { name : "cascade-parent" } );
		                          entitySave( parent );
		                          entitySave( entityNew( "OdCascadeChild", { label : "c1", parent : parent } ) );
		                          entitySave( entityNew( "OdCascadeChild", { label : "c2", parent : parent } ) );
		                          ormFlush();
		                          pid = parent.getId();
		                          entityDelete( parent );
		                          ormFlush();
		                          children = queryExecute( "SELECT id FROM od_cascade_children WHERE parentId = :id", { id : pid } ).recordCount;
		                          parents = queryExecute( "SELECT id FROM od_parents WHERE id = :id", { id : pid } ).recordCount;
		                          """ );
		assertThat( vars.get( Key.of( "children" ) ) ).isEqualTo( 0 );
		assertThat( vars.get( Key.of( "parents" ) ) ).isEqualTo( 0 );
	}

	@DisplayName( "A child that points at a parent that was never saved still fails with orm.transient" )
	@Test
	public void testGenuineTransientStillFails() {
		IScope vars = runRequest( """
		                          err = {};
		                          try {
		                              entitySave( entityNew( "OdChild", { label : "orphan", parent : entityNew( "OdParent", { name : "never saved" } ) } ) );
		                              ormFlush();
		                              err = { type : "NO ERROR" };
		                          } catch ( any e ) {
		                              err = { type : e.type, message : e.message };
		                          }
		                          ormClearSession();
		                          """ );
		var		result	= ( ortus.boxlang.runtime.types.IStruct ) vars.get( Key.of( "err" ) );
		assertThat( result.getAsString( Key.type ) ).isEqualTo( "orm.transient" );
		assertThat( result.getAsString( Key.message ) ).contains( "never saved" );
	}

	private IScope runRequest( String source ) {
		ScriptingRequestBoxContext context = new ScriptingRequestBoxContext( instance.getRuntimeContext(), false );
		RequestBoxContext.setCurrent( context );
		try {
			context.loadApplicationDescriptor( APP_ROOT.resolve( "index.bxs" ).toUri() );
			context.getApplicationListener().onRequestStart( context, null );
			instance.executeSource( source, context );
			return context.getScopeNearby( VariablesScope.name );
		} finally {
			try {
				context.getApplicationListener().onRequestEnd( context, null );
			} catch ( Exception ignored ) {
				// best-effort
			}
			RequestBoxContext.removeCurrent();
			context.shutdown();
		}
	}
}
