package ortus.boxlang.orm.mapping;

import org.w3c.dom.Document;

public interface IPersistenceWriter {

	public Document generateXML( ORMAnnotationInspector inspector );
}
