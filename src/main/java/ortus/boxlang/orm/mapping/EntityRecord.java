package ortus.boxlang.orm.mapping;

import java.nio.file.Path;

public record EntityRecord( String entityName, String classFQN, Path mappingFile ) {

}
