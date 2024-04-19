package ortus.boxlang.orm.mapping;

import java.nio.file.Path;

import ortus.boxlang.runtime.runnables.IClassRunnable;

public record EntityRecord( String entityName, Class<IClassRunnable> entityClass, Path mappingFile ) {

}
