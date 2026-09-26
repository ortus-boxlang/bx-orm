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

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage 1 of the POJO-facade representation: proves the ByteBuddy {@link EntityFacadeFactory} generates a real Java
 * class that Hibernate maps as a normal POJO, whose accessors delegate to a {@link BoxEntityState}, and that
 * {@code uuid} id generation works on it (impossible for class-less dynamic entities).
 */
public class EntityFacadeFactoryTest {

	/** A trivial map-backed state, standing in for the BoxLang instance (which, in production, is an IClassRunnable). */
	static class MapState implements BoxEntityState {

		final Map<String, Object> backing;

		MapState( Map<String, Object> backing ) {
			this.backing = backing;
		}

		@Override
		public Object get( String property ) {
			return backing.get( property );
		}

		@Override
		public void set( String property, Object value ) {
			backing.put( property, value );
		}
	}

	private static final String MAPPING_XML = """
	                                          <?xml version="1.0"?>
	                                          <!DOCTYPE hibernate-mapping PUBLIC "-//Hibernate/Hibernate Mapping DTD 3.0//EN"
	                                          	"http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd">
	                                          <hibernate-mapping>
	                                          	<class name="ortus.boxlang.modules.orm.hibernate.facade.generated.WidgetFacade" table="widget_facade">
	                                          		<id name="id" type="string" length="36">
	                                          			<generator class="uuid"/>
	                                          		</id>
	                                          		<property name="name" type="string"/>
	                                          	</class>
	                                          </hibernate-mapping>
	                                          """;

	@DisplayName( "It generates a real facade class Hibernate maps as a POJO, with working uuid id generation" )
	@Test
	public void testGeneratedFacadeWithUuid() throws Exception {
		Class<?> facadeClass = EntityFacadeFactory.generate(
		    "ortus.boxlang.modules.orm.hibernate.facade.generated.WidgetFacade",
		    new EntityFacadeFactory.PropertySpec( "id", String.class ),
		    List.of( new EntityFacadeFactory.PropertySpec( "name", Object.class ) ),
		    getClass().getClassLoader()
		);

		// The facade implements BoxEntityFacade and delegates to whatever BoxEntityState it wraps.
		assertThat( BoxEntityFacade.class.isAssignableFrom( facadeClass ) ).isTrue();

		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
		    .applySetting( AvailableSettings.URL, "jdbc:derby:memory:facadeFactorySpike;create=true" )
		    .applySetting( AvailableSettings.DIALECT, "org.hibernate.community.dialect.DerbyDialect" )
		    .applySetting( AvailableSettings.HBM2DDL_AUTO, "create-drop" )
		    .build();

		try {
			Metadata metadata = new MetadataSources( registry )
			    .addInputStream( new ByteArrayInputStream( MAPPING_XML.getBytes( StandardCharsets.UTF_8 ) ) )
			    .buildMetadata();

			try ( SessionFactory sf = metadata.buildSessionFactory() ) {
				Map<String, Object>	backing	= new HashMap<>();
				Object				facade	= facadeClass.getConstructor( BoxEntityState.class ).newInstance( new MapState( backing ) );

				// Exercise the generated setter (delegates into the backing state), then persist without an id.
				facadeClass.getMethod( "setName", Object.class ).invoke( facade, "Widget" );
				sf.inTransaction( session -> session.persist( facade ) );

				// The uuid generator wrote a real id back through the facade into the backing state.
				Object generatedId = backing.get( "id" );
				assertThat( generatedId ).isInstanceOf( String.class );
				assertThat( ( String ) generatedId ).isNotEmpty();
				assertThat( facadeClass.getMethod( "getId" ).invoke( facade ) ).isEqualTo( generatedId );

				// Confirm the row actually persisted with the delegated name (read back independently of facade instantiation).
				sf.inTransaction( session -> {
					Object name = session
					    .createNativeQuery( "select name from widget_facade where id = :id", String.class )
					    .setParameter( "id", generatedId )
					    .getSingleResult();
					assertThat( name ).isEqualTo( "Widget" );
				} );
			}
		} finally {
			StandardServiceRegistryBuilder.destroy( registry );
		}
	}
}
