package ortus.boxlang.modules.orm.mapping;

import java.nio.file.Path;

import ortus.boxlang.modules.orm.mapping.inspectors.IEntityMeta;
import ortus.boxlang.runtime.types.IStruct;

public class EntityRecord {

	private String		entityName;
	private String		classFQN;
	private String		className;
	private String		datasource;
	private IStruct		metadata;
	private Path		xmlFilePath;
	private IEntityMeta	entityMeta;

	public EntityRecord( String entityName, String classFQN ) {
		this( entityName, classFQN, null );
	}

	public EntityRecord( String entityName, String classFQN, IStruct metadata ) {
		this( entityName, classFQN, metadata, null );
	}

	public EntityRecord( String entityName, String classFQN, IStruct metadata, String onDatasource ) {
		this.entityName	= entityName;
		this.classFQN	= classFQN;
		this.metadata	= metadata;
		this.datasource	= onDatasource;

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

	public String getDatasource() {
		return datasource;
	}

	public Path getXmlFilePath() {
		return xmlFilePath;
	}
}
