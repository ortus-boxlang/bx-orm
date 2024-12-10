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
package ortus.boxlang.modules.orm.mapping;

import java.nio.file.Path;

import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class EntityRecord {

	private String		entityName;
	private String		classFQN;
	private String		className;
	private DataSource	datasource;
	private IStruct		metadata;
	private Path		xmlFilePath;
	private IEntityMeta	entityMeta;
	private String		resolverPrefix;

	public EntityRecord( String entityName, String classFQN ) {
		this( entityName, classFQN, null );
	}

	public EntityRecord( String entityName, String classFQN, IStruct metadata ) {
		this( entityName, classFQN, metadata, null );
	}

	public EntityRecord( String entityName, String classFQN, IStruct metadata, DataSource onDatasource ) {
		this.entityName		= entityName;
		this.classFQN		= classFQN;
		this.datasource		= onDatasource;
		this.metadata		= metadata == null ? Struct.EMPTY : metadata;
		this.resolverPrefix	= parseResolverPrefix( ( String ) this.metadata.getOrDefault( Key.path, ClassLocator.BX_PREFIX ) );

		String[] fqn = this.classFQN.split( "\\." );
		this.className = fqn[ fqn.length - 1 ];
	}

	public EntityRecord setXmlFilePath( Path xmlFilePath ) {
		this.xmlFilePath = xmlFilePath;
		return this;
	}

	public EntityRecord setMetadata( IStruct metadata ) {
		this.metadata = metadata;
		return this;
	}

	public EntityRecord setEntityMeta( IEntityMeta entityMeta ) {
		this.entityMeta = entityMeta;
		return this;
	}

	public IStruct getMetadata() {
		return metadata;
	}

	public String getEntityName() {
		return entityName;
	}

	public IEntityMeta getEntityMeta() {
		return entityMeta;
	}

	public String getClassName() {
		return className;
	}

	public String getClassFQN() {
		return classFQN;
	}

	public DataSource getDatasource() {
		return datasource;
	}

	public Path getXmlFilePath() {
		return xmlFilePath;
	}

	public String getResolverPrefix() {
		return resolverPrefix;
	}

	private String parseResolverPrefix( String filePath ) {
		return filePath.endsWith( "cfc" ) ? "cfc" : ClassLocator.BX_PREFIX;
	}
}
