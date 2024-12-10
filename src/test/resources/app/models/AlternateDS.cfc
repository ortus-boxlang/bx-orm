component entityName="MappingFromAnotherMother" table="alternate_ds" datasource="dsn2"  persistent="true" {

    property 
        name="id" 
        type="string" 
        fieldtype="id" 
        ormtype="string" 
        generator="assigned";

    property 
        name="name" 
        type="string";
}