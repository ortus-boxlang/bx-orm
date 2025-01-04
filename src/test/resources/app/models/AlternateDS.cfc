component entityName="AlternateDS" table="alternate_ds" datasource="dsn2"  persistent="true" {

    property 
        name="id" 
        type="string" 
        fieldtype="id" 
        ormtype="string"
        length="40"
        generator="assigned";

    property 
        name="name" 
        type="string";
}