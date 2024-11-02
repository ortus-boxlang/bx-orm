component entityName="MappingFromAnotherMother" datasource="dsn2"  persistent="true" {

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