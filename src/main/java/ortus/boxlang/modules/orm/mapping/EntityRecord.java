package ortus.boxlang.modules.orm.mapping;

import java.nio.file.Path;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

public class EntityRecord {

	private String	entityName;
	private String	classFQN;
	private String	datasource;
	private IStruct	metadata;
	private Path	xmlFilePath;

	public EntityRecord( String entityName, String classFQN ) {
		this( entityName, classFQN, null );
	}

	public EntityRecord( String entityName, String classFQN, IStruct metadata ) {
		this.entityName	= entityName;
		this.classFQN	= classFQN;
		this.metadata	= metadata;
		if ( metadata != null && metadata.containsKey( Key.datasource ) ) {
			this.datasource = metadata.getAsString( Key.datasource );
		}
	}

	public void setXmlFilePath( Path xmlFilePath ) {
		this.xmlFilePath = xmlFilePath;
	}

	public EntityRecord setMetadata( IStruct metadata ) {
		this.metadata = metadata;
		return this;
	}

	public IStruct getMetadata() {
		return metadata;
	}

	public String getEntityName() {
		return entityName;
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
