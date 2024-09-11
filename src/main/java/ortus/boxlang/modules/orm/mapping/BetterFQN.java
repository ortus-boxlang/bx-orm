package ortus.boxlang.modules.orm.mapping;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BetterFQN {

	/**
	 * These words cannot appear in a package name.
	 */
	static final Set<String>	RESERVED_WORDS	= new HashSet<>( Arrays.asList( "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
	    "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements",
	    "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
	    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while" ) );

	private ArrayList<String>	fqnParts;

	public BetterFQN( Path file ) {
		this.fqnParts = new ArrayList<>();
		this.fqnParts.add( parsePackageFromFile( file ) );
		this.fqnParts.add( getClassName( file ) );
	}

	public BetterFQN( Path root, Path file ) {
		this.fqnParts = new ArrayList<>();
		this.fqnParts.add( parsePackageFromFile( root.relativize( file ) ) );
		this.fqnParts.add( getClassName( file ) );
	}

	private String getClassName( Path file ) {
		String name = file.toFile().getName();
		if ( name.endsWith( ".bx" ) ) {
			name = name.substring( 0, name.length() - 3 );
		} else if ( name.endsWith( ".cfc" ) ) {
			name = name.substring( 0, name.length() - 4 );
		}
		return name;
	}

	/**
	 * Parse the package from a file path.
	 * 
	 * @param file The file to parse the package from.
	 * 
	 * @return The package name.
	 */
	private String parsePackageFromFile( Path file ) {
		String packg = file.toFile().toString().replace( File.separatorChar + file.toFile().getName(), "" );
		if ( packg.startsWith( "/" ) || packg.startsWith( "\\" ) ) {
			packg = packg.substring( 1 );
		}
		// trim trailing \ or /
		if ( packg.endsWith( "\\" ) || packg.endsWith( "/" ) ) {
			packg = packg.substring( 0, packg.length() - 1 );
		}

		// Take out periods in folder names
		packg	= packg.replaceAll( "\\.", "" );
		// Replace / with .
		packg	= packg.replaceAll( "/", "." );
		// Remove any : from Windows drives
		packg	= packg.replaceAll( ":", "" );
		// Replace \ with .
		packg	= packg.replaceAll( "\\\\", "." );

		return packg;

	}

	public ArrayList<String> getParts() {
		return fqnParts;
	}

	public String toString() {
		return String.join( ".", fqnParts ).replace( "..", "." );
	}
}
